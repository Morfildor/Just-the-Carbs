package app.justthecarbs.data.remote

import app.justthecarbs.domain.ProductSearchHit
import java.text.Normalizer
import java.util.Locale

/**
 * Orders one page of Search-a-licious results and keeps the ones that can show a carbohydrate figure.
 * It never adds or alters a result; [select] may leave some out.
 *
 * The service ranks by text relevance alone, which is blind to two things that decide whether a
 * result is the one someone wants: whether the product is **sold where they are**, and whether this
 * app can **show a carbohydrate figure** for it. Measured live on 2026-09-16 over 56 queries a Dutch
 * shopper would type, the top result was sold in the Netherlands or Belgium for only 35, and was both
 * relevant and calculable for only 28. `nutella` led with *Nutella & go!*, a US product scanned once.
 * With [select] over a 50-result page (and German and French names in the query) that became
 * 53/56 — and 37/40 on a separate set of 40 queries not used while designing it (baseline 18/40),
 * with 95% of the top five showing a figure (baseline 39%).
 *
 * Order of precedence, each only breaking ties left by the one before:
 *
 * 1. **Every query word appears** in the name or brand. Results missing a word keep the service's
 *    own order below the full matches. Re-sorting those as well was measured to be wrong: "honig
 *    macaroni" matches German *Honig* (honey), nothing matches both words, and country-sorting the
 *    partial matches put an unrelated Spanish bread first.
 * 2. **Sold in the device's country**, when the device has one.
 * 3. **Calculable**: the result carries a validated figure and basis, so its card shows a number.
 * 4. **More widely scanned**: Open Food Facts' count of distinct scanners, a proxy for "the one on
 *    the shelf". Sorting the whole list by it instead was measured and rejected — it halved top-1
 *    relevance, because a popular product that matches one word outranks the right one.
 * 5. The service's order.
 *
 * The device country is only compared here, on the device. It is never sent to any service.
 */
internal object SearchResultRanking {

    data class Candidate(
        val hit: ProductSearchHit,
        /** Open Food Facts country tags, e.g. `en:netherlands`. */
        val countries: List<String>,
        val uniqueScans: Int?,
    )

    fun rank(query: String, candidates: List<Candidate>, deviceCountryTag: String?): List<ProductSearchHit> {
        val words = wordsOf(query)

        return candidates
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Candidate>> { if (matchesEveryWord(it.value.hit, words)) 0 else 1 }
                    .thenBy { (index, candidate) ->
                        // Partial matches are ordered by the service alone; see rule 1.
                        if (!matchesEveryWord(candidate.hit, words)) index else 0
                    }
                    .thenBy { (_, candidate) ->
                        if (deviceCountryTag != null && deviceCountryTag in candidate.countries) 0 else 1
                    }
                    .thenBy { (_, candidate) ->
                        if (candidate.hit.isCalculable()) 0 else 1
                    }
                    .thenByDescending { (_, candidate) -> candidate.uniqueScans ?: 0 }
                    .thenBy { it.index },
            )
            .map { it.value.hit }
    }

    /**
     * [rank], then leave out results whose card could not show a figure, then take [limit].
     *
     * - When a calculable result matches every query word, only calculable results are kept.
     * - When none does, full matches are kept even without a figure. Measured on held-out queries:
     *   dropping them removed the only results that were actually "Conimex Nasi" or "krentenbollen",
     *   and a sambal took the top slot. Tapping one still runs the full product lookup, which
     *   resolves the unit for some records this index cannot (5 of a sample of 12).
     * - When nothing is calculable, nothing is left out; the cards say so themselves.
     */
    fun select(
        query: String,
        candidates: List<Candidate>,
        deviceCountryTag: String?,
        limit: Int,
    ): List<ProductSearchHit> {
        val words = wordsOf(query)
        val ranked = rank(query, candidates, deviceCountryTag)
        val calculable = ranked.filter { it.isCalculable() }
        val kept = when {
            calculable.isEmpty() -> ranked
            calculable.any { matchesEveryWord(it, words) } -> calculable
            else -> ranked.filter { it.isCalculable() || matchesEveryWord(it, words) }
        }
        return kept.take(limit)
    }

    /**
     * The Open Food Facts country tag for a locale's region: the region's English name, lower-cased
     * and hyphenated (`GB` -> `en:united-kingdom`). A few regions are named differently by Open Food
     * Facts; for those this simply matches nothing and country stops being a signal.
     */
    fun countryTagOf(locale: Locale): String? {
        if (locale.country.isBlank()) return null
        val name = Locale("", locale.country).getDisplayCountry(Locale.ENGLISH)
        if (name.isBlank()) return null
        return "en:" + name.lowercase(Locale.ROOT).replace(' ', '-')
    }

    private fun wordsOf(query: String): List<String> =
        WORD.findAll(fold(query)).map { it.value }.filter { it.length >= MIN_WORD_LENGTH }.toList()

    private fun ProductSearchHit.isCalculable() = carbsPer100 != null && basis != null

    private fun matchesEveryWord(hit: ProductSearchHit, words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val text = fold(hit.name + " " + hit.brand.orEmpty())
        return words.all { it in text }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)

    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")

    /** A lone letter — the `s` of `jerry's` — says nothing about which product is meant. */
    private const val MIN_WORD_LENGTH = 2
}
