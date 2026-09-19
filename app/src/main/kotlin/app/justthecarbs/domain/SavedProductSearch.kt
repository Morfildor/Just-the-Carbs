package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * One locally stored product, in the narrow shape saved-product search needs.
 *
 * Deliberately **not** a [Product] and deliberately not a Room entity. It carries the four things
 * matching reads — identity, name, brand, and whether the identity is a real barcode — plus the
 * three things ranking breaks ties with. Everything else a product has (provenance, verification,
 * portion units, gallery metadata, the remembered portion) is irrelevant here, and reading it would
 * make an in-memory scan over every saved row cost far more than it needs to.
 *
 * [carbsPer100] and [basis] are non-null because the columns they come from are: a stored product
 * always has a figure and a denominator, which is why a saved hit always renders a number. That is
 * a fact about the store, not an assumption — see `products.carbsPer100` / `products.basis`.
 */
data class SavedProduct(
    /**
     * The product's identity and navigation key — a real GTIN, or a synthetic `local:<uuid>` for a
     * product the user authored with no barcode.
     *
     * Both are valid identities and both navigate correctly (`Routes.product` takes either, and
     * `ProductRepository.lookup` resolves a synthetic key from the local store and never asks the
     * network for it). Only one of them is *text a user could type*, which is why
     * [isRealBarcode] exists rather than this being matched directly.
     */
    val barcode: String,
    val name: String,
    val brand: String?,
    val carbsPer100: BigDecimal,
    val basis: NutritionBasis,
    val imageUrl: String?,
    val favorite: Boolean,
    /** Epoch millis of the last use, or null if never used. Ordering only. */
    val lastUsedAt: Long?,
) {
    /**
     * Whether [barcode] is a scannable GTIN rather than a synthetic local key.
     *
     * A `local:` key is a UUID this app minted. Matching it as barcode text would mean a query of
     * digits could "exactly match" a product whose identity the user has never seen and could not
     * type, so the one thing it must never be is a barcode candidate — while remaining a perfectly
     * good identity for dedupe and navigation.
     */
    val isRealBarcode: Boolean get() = !barcode.startsWith(LOCAL_KEY_PREFIX)

    companion object {
        /** The prefix `ManualEntryViewModel` and `ProductViewModel` mint synthetic keys with. */
        const val LOCAL_KEY_PREFIX = "local:"
    }
}

/**
 * Saved products, read for search.
 *
 * A separate interface from [ProductSearchSource] rather than another implementation of it, and
 * that separation is the design. [ProductSearchSource] is the *remote provider chain*: its results
 * are candidates from a database of strangers' records, subject to a request budget, a cache, a
 * fallback host and a governor. Saved products are none of those things — they are already on the
 * device, free to read, and always available. Making this a `ProductSearchSource` would mean either
 * a composite source (so the ViewModel could no longer tell a local hit from a remote one, and the
 * merge rules below would have nowhere to live) or a fourth link in a chain built to answer
 * "which host do we ask next?", a question this never asks.
 */
interface SavedProductSearchSource {
    /** Every locally stored product, in no particular order. */
    suspend fun allProducts(): List<SavedProduct>
}

/**
 * Matching, ranking and merging for locally saved products.
 *
 * ## Why this is a scan and not SQL
 *
 * There is no `LIKE`, no FTS table, no new index and no migration. The store holds the products one
 * person has scanned, and matching in Kotlin buys the two things SQL here cannot: the **same**
 * [SearchQueryMatcher] that ranks the remote page — so a row cannot be judged one way locally and
 * the other way remotely — and its folding, which `LIKE` has no equivalent for (`Pınar` and `Pinar`
 * are different strings to SQLite, and `LIKE` is only case-insensitive for ASCII).
 *
 * ## The ordering, and the one rule that is not a tie-break
 *
 * **Relevance beats favourite.** Starring a product says the user wants it near the top of *Home*;
 * it says nothing about which product they just typed the name of. A favourite ranked above a
 * better textual match would mean typing a product's exact name and being shown a different one, so
 * [Strength] and the matched-word counts are settled in full before [SavedProduct.favorite] is
 * consulted at all. The star is a tie-break between results that match the text equally well, which
 * is the only question it can honestly answer.
 */
object SavedProductSearch {

    /**
     * The saved products matching [query], best first.
     *
     * Ordering, each rule only breaking ties the one before it left:
     *
     * 1. **An exact real-barcode match**, which is not a ranking signal but an identification: the
     *    user typed this product's number, so there is nothing to rank against it.
     * 2. **Textual relevance** — [SearchQueryMatcher.Strength], then, within a strength, the same
     *    counts `SearchResultRanking` uses (product words and matched words for a strong match,
     *    whole words for a full one). This is the whole of the relevance judgement and it is
     *    finished before rule 3 begins.
     * 3. **Favourite**, as a modest tie-break.
     * 4. **Most recently used**, as a further one. Never used sorts last rather than first — an
     *    absent timestamp is "no evidence", not "eaten at the epoch".
     * 5. **Name, then barcode**, so the order is total and two runs over the same store cannot
     *    differ. Without a final deterministic key the list could reshuffle between an initial
     *    local answer and the merge, which reads as the list thrashing for no reason.
     *
     * Weak matches are dropped whenever anything better exists, exactly as
     * `SearchResultRanking.select` does for the remote page — a list padded with results that share
     * only a brand word reads as though they were answers.
     */
    fun search(query: String, products: List<SavedProduct>, limit: Int): List<ProductSearchHit> {
        if (products.isEmpty()) return emptyList()

        val terms = query.trim()
        if (terms.isEmpty()) return emptyList()

        // A barcode is compared as digits against digits, so "8710496979125" typed with a stray
        // space still identifies its product. Synthetic `local:` keys are excluded at the source:
        // they are not text anyone could have typed.
        val digits = terms.filter(Char::isDigit)
        val barcodeMatch = if (digits.length == terms.length && digits.isNotEmpty()) {
            products.firstOrNull { it.isRealBarcode && it.barcode == digits }
        } else {
            null
        }

        val subjects = products.map { SearchQueryMatcher.Subject(listOf(it.name), it.brand) }
        val matcher = SearchQueryMatcher(terms, subjects)
        val matches = subjects.map(matcher::match)

        val ordered = products.indices
            .sortedWith(
                // Rule 1. An exact barcode is an identification, so it precedes every relevance
                // comparison rather than being folded in as another signal.
                compareBy<Int> { if (products[it] === barcodeMatch) 0 else 1 }
                    // Rule 2 — relevance, settled in full before the star is consulted.
                    .thenBy { matches[it].strength }
                    .thenByDescending {
                        if (matches[it].strength == SearchQueryMatcher.Strength.STRONG) {
                            matches[it].productWords
                        } else {
                            0
                        }
                    }
                    .thenByDescending {
                        if (matches[it].strength == SearchQueryMatcher.Strength.STRONG) {
                            matches[it].matchedWords
                        } else {
                            0
                        }
                    }
                    .thenByDescending {
                        if (matches[it].strength == SearchQueryMatcher.Strength.FULL) {
                            matches[it].wholeWords
                        } else {
                            0
                        }
                    }
                    // Rule 3 — the star, and only now.
                    .thenBy { if (products[it].favorite) 0 else 1 }
                    // Rule 4 — recency. Null last: never used is not "used long ago".
                    .thenByDescending { products[it].lastUsedAt ?: Long.MIN_VALUE }
                    // Rule 5 — a total order, so repeated runs cannot differ.
                    .thenBy { products[it].name }
                    .thenBy { products[it].barcode },
            )

        val useful = ordered.filter {
            products[it] === barcodeMatch || matches[it].strength != SearchQueryMatcher.Strength.WEAK
        }
        // Weak matches are shown only when nothing better matched at all, mirroring
        // `SearchResultRanking.select`. A query matching nothing locally — the ordinary case for a
        // product the user has never saved — must still return nothing rather than every saved row,
        // so the fallback keeps only rows that matched at least one query word.
        val kept = useful.ifEmpty { ordered.filter { matches[it].matchedWords > 0 } }

        return kept.take(limit).map { products[it].toHit() }
    }

    /**
     * The strength [search] judged a hit at, for the merge's leading/trailing split.
     *
     * Recomputed rather than carried on [ProductSearchHit]: that type is the shared shape of a
     * search result from any provider, and adding a local-only field to it would put "where did
     * this come from" into the one type whose whole point is that the screen cannot tell.
     */
    fun strengthOf(query: String, hits: List<ProductSearchHit>): List<SearchQueryMatcher.Strength> {
        if (hits.isEmpty()) return emptyList()
        val subjects = hits.map { SearchQueryMatcher.Subject(listOf(it.name), it.brand) }
        val matcher = SearchQueryMatcher(query.trim(), subjects)
        return subjects.map { matcher.match(it).strength }
    }

    /**
     * Local hits and a remote page, as one list.
     *
     * ```
     *   [ local FULL matches ]  [ remote, in the order it arrived ]  [ remaining local ]
     * ```
     *
     * Four rules, and each exists because of what the alternative does:
     *
     * - **The remote block keeps its exact relative order.** Search-a-licious ranks with information
     *   this app never receives — localized name matching, per-record popularity, the provider's own
     *   relevance score — and [ProductSearchHit] retains none of it. Re-ranking the block here would
     *   be re-ranking on strictly less evidence than produced it.
     * - **A duplicate keeps the local payload and the remote position.** The payload, because the
     *   saved row is what tapping the result resolves to anyway: `ProductRepository.lookup` is
     *   local-first, so showing the remote figure and then opening the local one would be showing a
     *   number the next screen contradicts. The position, because the remote block's order is not
     *   ours to change — unless the local copy leads, in which case it has already been placed.
     * - **Only FULL local matches lead.** A local hit that matched every word was already on screen
     *   and on top before the remote answer arrived, so leaving it there is the arrangement in which
     *   the list moves least. A strong-but-partial local match has no claim to outrank a remote
     *   result that may well be better, so it is appended instead.
     * - **Weak local matches never lead**, which is the [search] fallback's one exception: locally
     *   they are better than nothing, but "better than nothing" stops being true the moment real
     *   results exist.
     */
    fun merge(
        query: String,
        localHits: List<ProductSearchHit>,
        remoteHits: List<ProductSearchHit>,
        limit: Int,
    ): List<ProductSearchHit> {
        if (localHits.isEmpty()) return remoteHits.take(limit)
        if (remoteHits.isEmpty()) return localHits.take(limit)

        val strengths = strengthOf(query, localHits)
        val leading = mutableListOf<ProductSearchHit>()
        val trailing = mutableListOf<ProductSearchHit>()
        localHits.forEachIndexed { index, hit ->
            if (strengths[index] == SearchQueryMatcher.Strength.FULL) leading += hit else trailing += hit
        }

        val localByBarcode = localHits.associateBy { it.barcode }
        val leadingBarcodes = leading.mapTo(mutableSetOf()) { it.barcode }

        val merged = mutableListOf<ProductSearchHit>()
        merged += leading
        // A remote result the user has saved is replaced in place by the saved copy, keeping the
        // provider's position. A remote result whose local copy is already leading is dropped rather
        // than shown twice.
        remoteHits.forEach { remote ->
            if (remote.barcode in leadingBarcodes) return@forEach
            merged += localByBarcode[remote.barcode] ?: remote
        }
        val shown = merged.mapTo(mutableSetOf()) { it.barcode }
        trailing.forEach { if (it.barcode !in shown) merged += it }

        return merged.take(limit)
    }

    private fun SavedProduct.toHit() = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        // Deliberately null. `ProductSearchHit.packageQuantity` is free text *as printed on the
        // package* ("390 gram"), and what the store holds is `packageAmount`, a parsed number with
        // its unit already discarded. Rendering "390" or inventing "390 g" would put a string on the
        // card that the package never showed, to fill a field whose entire job is helping someone
        // recognise the pack in front of them. The row renders correctly without it.
        packageQuantity = null,
        carbsPer100 = carbsPer100,
        basis = basis,
        imageUrl = imageUrl,
    )
}
