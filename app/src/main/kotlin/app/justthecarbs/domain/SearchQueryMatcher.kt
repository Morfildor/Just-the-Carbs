package app.justthecarbs.domain

import java.text.Normalizer
import java.util.Locale

/**
 * How closely a search result matches what was typed, decided by counting the query's words.
 *
 * Shared by the ranking of a fresh result page and by the decision, while a refined query is still
 * loading, about which rows already on screen may stay there. The two must agree on what "matches"
 * means, or a row could vanish while loading and come straight back in the answer.
 *
 * ## The three strengths
 *
 * - [Strength.FULL]: every query word appears in one of the result's names or its brand.
 * - [Strength.STRONG]: at least one query word that describes the product rather than naming its
 *   maker ([brandWords]) appears, and either every such word does or most query words do. Inflected
 *   forms count ([nearlyContains]). "Eti Popkek" is strong for a Popkek record entered without its
 *   brand; "krokante pizza Albert heijn" is strong for "Pizza krokant Hawaï".
 * - [Strength.WEAK]: anything else, including a result that only shares the brand.
 *
 * Measured on 2026-09-17 with "krokante pizza Albert heijn": the service answered with dozens of
 * Albert Heijn products that match no product word — honey, waffles, oyster sauce — and the old
 * ordering showed them among the two real pizzas because they carried a carbohydrate figure. Here
 * they are [Strength.WEAK], and a weak result is not shown when a better one exists.
 *
 * ## Folding
 *
 * Case, accents and the Turkish dotless `ı` are folded away ([fold]), so `Pinar sut` matches
 * `Pınar Süt`. Folding happens only here, for comparing. The text sent to the service stays exactly
 * as typed: the live service does not fold Turkish letters, so `Pinar sut` and `Pınar süt` return
 * different pages, and folding the request would lose the better one.
 */
class SearchQueryMatcher(query: String, page: List<Subject>) {

    /** One result as it is compared: every name it carries, and its brand. */
    class Subject(names: List<String>, brand: String?) {
        internal val nameText = fold(names.joinToString(" "))
        internal val brandText = fold(brand.orEmpty())
        internal val text = "$nameText $brandText"
        internal val words: List<String> = wordsIn(text)
        internal val wordSet: Set<String> = words.toSet()
        internal val brandTokens: Set<String> = wordsIn(brandText).toSet()
    }

    /** In ranking order: a full match comes first. */
    enum class Strength { FULL, STRONG, WEAK }

    class Match(
        val strength: Strength,
        /** Query words found, exactly or as an inflected form. */
        val matchedWords: Int,
        /** Of [matchedWords], those that describe the product rather than name its brand. */
        val productWords: Int,
        /**
         * Query words the result names as a whole word, not only inside a longer one: `kek` in
         * "Kek" but not in German "Keks", `süt` in "Süt" but not in "Sütlü".
         */
        val wholeWords: Int,
    )

    /** The query's words, folded, in order, each once. A lone letter is not a word. */
    val words: List<String> = wordsIn(fold(query)).filter { it.length >= MIN_WORD_LENGTH }.distinct()

    /**
     * Query words that [page] shows as a brand, such as `albert` and `heijn`.
     *
     * Decided from the page, because a record's own brand field is often empty: "Albert heijn
     * Carrots" carries the brand only in its name. A word is a brand word when at least
     * [MIN_BRAND_MENTIONS] results have it as a whole word of their brand, and no more results use
     * it in a name without that brand. The second rule keeps `pizza` a product word on a page that
     * also lists two Pizza Hut products.
     */
    val brandWords: Set<String> = words.filterTo(mutableSetOf()) { word ->
        val asBrand = page.count { word in it.brandTokens }
        val inNameOnly = page.count { word !in it.brandTokens && word in it.nameText }
        asBrand >= MIN_BRAND_MENTIONS && asBrand >= inNameOnly
    }

    fun match(subject: Subject): Match {
        if (words.isEmpty()) return Match(Strength.FULL, 0, 0, 0)
        var exact = 0
        var matched = 0
        var product = 0
        var whole = 0
        for (word in words) {
            if (word in subject.wordSet) whole++
            val found = when {
                word in subject.text -> {
                    exact++
                    true
                }
                else -> nearlyContains(subject.words, word)
            }
            if (!found) continue
            matched++
            if (word !in brandWords) product++
        }
        val everyProductWord = product == words.size - brandWords.size
        val strength = when {
            exact == words.size -> Strength.FULL
            product > 0 && (everyProductWord || matched * 2 > words.size) -> Strength.STRONG
            else -> Strength.WEAK
        }
        return Match(strength, matched, product, whole)
    }

    companion object {
        /**
         * The comparison form of [text]: decomposed, accents dropped, lower-cased without regard to
         * the device locale, and the Turkish dotless `ı` read as `i`.
         *
         * `ı` needs its own step because it has no decomposition: without it `Pınar` and `Pinar`
         * never meet. Its capital `I` and the dotted `İ` already fold correctly — the first through
         * the locale-independent lower-casing, the second by decomposing into `I` and a mark.
         */
        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(Locale.ROOT)
                .replace('ı', 'i')

        private fun wordsIn(folded: String): List<String> = WORD.findAll(folded).map { it.value }.toList()

        /**
         * Whether a result word begins with [word] less one or two final letters: `krokant` for
         * `krokante`, `fıstık` for `fıstıklı`, `pizza` for `pizzas`.
         *
         * Counted toward [Strength.STRONG] only, never [Strength.FULL]. Short words get no such
         * reading, so `pasta` never reaches `pastırma`.
         */
        private fun nearlyContains(resultWords: List<String>, word: String): Boolean =
            (1..MAX_TRIMMED_LETTERS).any { trim ->
                val stem = word.dropLast(trim)
                stem.length >= MIN_STEM_LENGTH && resultWords.any { it.startsWith(stem) }
            }

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val WORD = Regex("[\\p{L}\\p{N}]+")

        /** A lone letter — the `s` of `jerry's` — says nothing about which product is meant. */
        private const val MIN_WORD_LENGTH = 2

        private const val MIN_BRAND_MENTIONS = 2
        private const val MAX_TRIMMED_LETTERS = 2
        private const val MIN_STEM_LENGTH = 5
    }
}
