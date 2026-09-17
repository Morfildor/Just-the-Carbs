package app.justthecarbs.data.remote

import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.SearchQueryMatcher
import app.justthecarbs.domain.SearchQueryMatcher.Strength
import java.util.Locale

/**
 * Orders one page of Search-a-licious results and leaves out the ones that do not match what was
 * typed. It never adds or alters a result; [select] may leave some out.
 *
 * The service ranks by text relevance alone, which is blind to two things that decide whether a
 * result is the one someone wants: whether the product is **sold where they are**, and whether this
 * app can **show a carbohydrate figure** for it. Measured live on 2026-09-16 over 56 queries a Dutch
 * shopper would type, the top result was sold in the Netherlands or Belgium for only 35, and was both
 * relevant and calculable for only 28. `nutella` led with *Nutella & go!*, a US product scanned once.
 *
 * Those signals are only ever used between results that match the query equally well. The
 * 2026-09-16 version let a figure outweigh the text: once one exact match could show a figure, every
 * result that could show one was kept, relevant or not, and exact matches without one were dropped.
 * On device, "krokante pizza Albert heijn" then listed honey, waffles and oyster sauce around the two
 * real pizzas (2026-09-17). Measured on the captured page, and on the benchmarks in
 * `SearchBenchmarkTest`.
 *
 * Order of precedence, each only breaking ties left by the one before:
 *
 * 1. **How well the text matches** ([SearchQueryMatcher.Strength]): results containing every query
 *    word, then strong partial matches, then the rest.
 * 2. For strong partial matches, **how much of the product they name**, then how many query words
 *    they contain. For full matches, **how many query words they name as whole words**: measured on
 *    the Turkish benchmark, `kek` (cake) matched inside German *Keks* (biscuit), and without this a
 *    Keks with a figure led the list above every real kek.
 * 3. **Calculable**: the result carries a validated figure and basis, so its card shows a number.
 *    Never applied to weak matches, so a figure cannot lift an unrelated product. Ahead of the
 *    country because the 2026-09-16 rule dropped full matches without a figure whenever one with a
 *    figure existed; keeping them after those ones leaves the top of every list as it was.
 * 4. For full matches only, **sold in the device's country**, when the device has one. Re-sorting
 *    partial matches by country was measured to be wrong: "honig macaroni" matches German *Honig*
 *    (honey), and country-sorting those matches put an unrelated Spanish bread first.
 * 5. For full matches only, **more widely scanned**: Open Food Facts' count of distinct scanners, a
 *    proxy for "the one on the shelf". Sorting the whole list by it was measured and rejected — it
 *    halved top-1 relevance, because a popular product that matches one word outranks the right one.
 * 6. The service's order.
 *
 * The device country is only compared here, on the device. It is never sent to any service.
 */
internal object SearchResultRanking {

    data class Candidate(
        val hit: ProductSearchHit,
        /** Open Food Facts country tags, e.g. `en:netherlands`. */
        val countries: List<String>,
        val uniqueScans: Int?,
        /** The record's names in other languages, compared as well as the one shown. */
        val otherNames: List<String> = emptyList(),
    )

    fun rank(query: String, candidates: List<Candidate>, deviceCountryTag: String?): List<ProductSearchHit> =
        ranked(query, candidates, deviceCountryTag).map { it.first }

    /**
     * [rank], then stop where the matches run out, then take [limit].
     *
     * Full and strong matches are kept, with or without a figure: an exact match that cannot show
     * one is still the product the user named, and tapping it runs the full product lookup, which
     * resolves the unit for some records this index cannot (5 of a sample of 12, 2026-09-16).
     * Weak matches are shown only when there is nothing better, because a list padded with them
     * reads as though they were answers.
     */
    fun select(
        query: String,
        candidates: List<Candidate>,
        deviceCountryTag: String?,
        limit: Int,
    ): List<ProductSearchHit> {
        val ranked = ranked(query, candidates, deviceCountryTag)
        val matching = ranked.filter { it.second != Strength.WEAK }
        return matching.ifEmpty { ranked }.take(limit).map { it.first }
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

    private fun ranked(
        query: String,
        candidates: List<Candidate>,
        deviceCountryTag: String?,
    ): List<Pair<ProductSearchHit, Strength>> {
        val subjects = candidates.map { SearchQueryMatcher.Subject(listOf(it.hit.name) + it.otherNames, it.hit.brand) }
        val matcher = SearchQueryMatcher(query, subjects)
        val matches = subjects.map(matcher::match)

        return candidates.indices
            .sortedWith(
                compareBy<Int> { matches[it].strength }
                    .thenByDescending { if (matches[it].strength == Strength.STRONG) matches[it].productWords else 0 }
                    .thenByDescending { if (matches[it].strength == Strength.STRONG) matches[it].matchedWords else 0 }
                    .thenByDescending { if (matches[it].strength == Strength.FULL) matches[it].wholeWords else 0 }
                    // Weak matches keep the service's order and nothing else: see rules 3 and 4.
                    .thenBy { if (matches[it].strength == Strength.WEAK) it else 0 }
                    .thenBy { if (candidates[it].hit.isCalculable()) 0 else 1 }
                    .thenBy {
                        val home = deviceCountryTag != null && deviceCountryTag in candidates[it].countries
                        if (matches[it].strength == Strength.FULL && !home) 1 else 0
                    }
                    .thenByDescending {
                        if (matches[it].strength == Strength.FULL) candidates[it].uniqueScans ?: 0 else 0
                    }
                    .thenBy { it },
            )
            .map { candidates[it].hit to matches[it].strength }
    }

    private fun ProductSearchHit.isCalculable() = carbsPer100 != null && basis != null
}
