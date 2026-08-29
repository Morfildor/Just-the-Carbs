package app.justthecarbs.domain

/**
 * Remembers recent successful searches so a repeated query costs no request.
 *
 * ```
 *  search("chocolate") ─▶ miss ─▶ delegate ─▶ Found ─▶ stored ─▶ returned
 *  search("gouda")     ─▶ miss ─▶ delegate ─▶ Found ─▶ stored ─▶ returned
 *  search("chocolate") ─▶ HIT  ────────────────────────────────▶ returned   (no request)
 * ```
 *
 * That sequence is the whole feature: three searches, two requests. Re-checking a term the user
 * looked at ten seconds ago is one of the most common things anyone does with a search box —
 * comparing two products, or coming back from a result they did not want — and it currently spends a
 * request and a round trip to redisplay a list the app was holding moments earlier.
 *
 * ## Why it is a [ProductSearchSource] and not a field on the ViewModel
 *
 * Because the two screens are two ViewModels. Home's inline search and the search screen each build
 * their own `SearchViewModel`, so a cache owned by one would be invisible to the other — and moving
 * between those two screens is precisely when the same query gets typed twice. As a decorator it
 * sits in `AppContainer` beside the single provider chain, so both screens share one instance for
 * the same reason they already share one governor.
 *
 * It also means nothing above it changes. `SearchViewModel` has no idea this exists: a cache hit is
 * an ordinary fast [ProductSearchResult.Found], so the generation guard, the settle wait, local
 * narrowing and every state transition behave exactly as they do for a fast network answer. A cache
 * that needed the ViewModel's cooperation would be a cache that could disagree with it.
 *
 * ## What is cached, and the rule that matters most
 *
 * **Successful answers only.** [ProductSearchResult.Found] is stored;
 * [ProductSearchResult.NoMatches] and every [ProductSearchResult.Failed] are returned untouched and
 * never written. A transient failure must never become a remembered one: caching a 503 would keep an
 * outage on screen for five minutes after the service recovered, and caching an offline error would
 * survive the user reconnecting. Those are the states where retrying is the *correct* behaviour, so
 * they stay uncached and stay retryable.
 *
 * `NoMatches` is deliberately excluded too, though it is an answer rather than a failure. It is the
 * one result a user is most likely to react to by editing the query and coming back — and the cost
 * of getting it wrong (telling someone a product does not exist because it did not exist five
 * minutes ago, in a database strangers are editing continuously) is worse than one saved request.
 *
 * ## Staleness cannot reach a calculation
 *
 * A cached entry is a list of [ProductSearchHit]s — names, brands, package text and photos to
 * recognise. It is not a `Product` and cannot become one: tapping a result runs the ordinary barcode
 * lookup through the canonical path, which is untouched by this class and has its own freshness
 * rules. So the worst a stale entry can do is show a slightly old *list to choose from*; the
 * carbohydrate figure the user acts on never comes from here.
 *
 * ## Bounds
 *
 * [TTL_MS] and [MAX_ENTRIES] are both small on purpose — this is a short-term memory for one sitting
 * with the app, not a store. Eviction is least-recently-used, so the entries that survive are the
 * ones being compared back and forth, which is the access pattern the cache exists for.
 */
class CachedProductSearch(
    private val delegate: ProductSearchSource,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ProductSearchSource {

    private class Entry(val storedAtMs: Long, val hits: List<ProductSearchHit>)

    /**
     * Access-ordered so `get` counts as a use and eviction removes the least recently *read*, not
     * merely the oldest written. The `true` argument is the entire difference between an LRU and an
     * insertion-order cache, and LRU is the right one here: a query being flipped back and forth is
     * exactly what should survive.
     *
     * Guarded by [lock] rather than relying on a concurrent map, because two operations have to be
     * atomic together (evict-if-full then put), and because `LinkedHashMap`'s access-order
     * reordering mutates on read — so even lookups need the lock.
     */
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    private val lock = Any()

    override suspend fun search(terms: String): ProductSearchResult {
        val key = key(terms)
        // A blank query is not a search and must not occupy an entry; the delegate already answers
        // it without a request.
        if (key.isEmpty()) return delegate.search(terms)

        cached(key)?.let { return ProductSearchResult.Found(it) }

        val result = delegate.search(terms)
        // Only a success is remembered — see the class KDoc. Note this is reached only when the
        // delegate actually returned: a cancelled search throws, so an abandoned query cannot write
        // an entry on its way out.
        if (result is ProductSearchResult.Found) {
            synchronized(lock) { entries[key] = Entry(nowMs(), result.hits) }
        }
        return result
    }

    /**
     * The stored hits for [key] if there are any and they are still fresh.
     *
     * An expired entry is **removed** rather than merely ignored. Leaving it would let a stale entry
     * occupy one of the [MAX_ENTRIES] slots indefinitely, evicting live ones in its place.
     *
     * A future [storedAtMs] is treated as expired, not as fresh. Wall-clock time can move backwards
     * — a timezone or NTP correction — and the alternative reading (`now - stored` negative, so
     * "younger than the TTL") would pin an entry until real time caught up. Same rule, and the same
     * reasoning, as the product-refresh freshness window.
     */
    private fun cached(key: String): List<ProductSearchHit>? = synchronized(lock) {
        val entry = entries[key] ?: return null
        val age = nowMs() - entry.storedAtMs
        if (age in 0 until TTL_MS) return entry.hits
        entries.remove(key)
        null
    }

    /**
     * The one definition of "the same search", and it must agree with the delegate's.
     *
     * Trimming only, matching what the data sources themselves do (`terms.trim()`) and the
     * ViewModel's `normalize` — so `" chocolate "` and `"chocolate"` share an entry because they
     * produce the same request. Case is **not** folded: the providers below are free to treat case
     * as meaningful, and a cache that merged two queries the network would answer differently would
     * be inventing results rather than remembering them. The cheap win is not worth that.
     */
    private fun key(terms: String): String = terms.trim()

    companion object {
        /**
         * How long an answer stays usable.
         *
         * Long enough to cover a sitting — comparing two products, backing out of one and returning
         * — and short enough that a product edited upstream shows up on any later visit. Five
         * minutes is well inside the window in which Open Food Facts search results are stable in
         * practice, and nothing safety-relevant depends on it: see the class KDoc.
         */
        const val TTL_MS = 5 * 60 * 1000L

        /**
         * How many queries are remembered.
         *
         * Sized for the pattern this exists for — a handful of terms compared against each other —
         * not for coverage. Twenty entries of at most 20 hits each is a few hundred kilobytes at
         * worst, bounded and process-lifetime only.
         */
        const val MAX_ENTRIES = 20
    }
}
