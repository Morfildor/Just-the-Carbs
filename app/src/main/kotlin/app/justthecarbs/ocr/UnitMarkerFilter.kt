package app.justthecarbs.ocr

/**
 * Excludes numbers that occupy a **unit-marker position** rather than a value position.
 *
 * ### The failure this exists for
 *
 * ML Kit read the printed unit marker `(g)` on the Kinder package as `(9)`. That token is a
 * well-formed single-digit carbohydrate quantity: it sits on the correct `TOTAL_CARBOHYDRATE` row, it
 * is introduced by the carbohydrate term so [CarbohydrateTermAnchor] cannot touch it, it clears the
 * per-100 validator (9 g/100 g is entirely ordinary), and it is standalone in parentheses so
 * `isStandaloneNumber` accepts it. Every existing guard passed it. It then beat the real `53,5` and
 * became a **confident-wrong** — the worst output this app can produce.
 *
 * This is not one label's problem and not one recognizer's. `g` -> `9` is the same class of
 * letter-to-digit confusion as the already-guarded `O` -> `0`, and printed nutrition tables annotate
 * their rows with units constantly: `Carbohydrate (g)`, `Koolhydraten g`, `Fett (g)`, `Energie (kJ)`.
 * Any of those markers can be misrecognised as a digit on any package.
 *
 * ### The rule, and why it is structural rather than a blacklist
 *
 * Blacklisting the literal `(9)` would fix one token and leave `(6)` (from `(g)` in another font),
 * bare `9` (from an unbracketed `g`), and every other marker untouched. Instead this asks a question
 * about **where the token sits in the table**, which is what actually distinguishes a marker from a
 * value:
 *
 * **A number that sits between the nutrient name and the row's first real value is not a value.**
 * Printed nutrition text states a nutrient, optionally annotates its unit, and then gives the figure.
 * Nothing is printed between the name and the figure except that annotation. So the *first* numeric
 * token after the nutrient name, when it is bracketed, is the unit marker.
 *
 * Bracketing is required and is what makes this safe. An unbracketed leading number on a nutrient row
 * is genuinely ambiguous — it may be the value on a table whose columns did not resolve — and
 * excluding it would cost correct readings. `(9)` is not ambiguous: values are not parenthesised in
 * the value column of a nutrition table.
 *
 * ### The rule that was tried and is WRONG — do not reinstate it
 *
 * An earlier version also treated "shares an x position with unit markers on two or more other rows"
 * as a marker column. It regressed the **sondey canary** to `NotFound`, and the reason is structural
 * rather than a tolerance being too loose: real labels overwhelmingly print the unit to the **right**
 * of the value (`61,9` `g`), not to the left. Those trailing `g` elements form a perfectly good
 * cluster, sitting right next to the value column, so "the column where units repeat" identified the
 * value column and deleted the answer. Unit repetition says nothing about which side of the value the
 * unit is on, so it cannot locate a marker column. Reading order can.
 *
 * ### What it must not do
 *
 * A legitimate carbohydrate value can be a single digit — `9 g` per 100 g is real, and diet drinks
 * and vegetables print values below 10 constantly. So this never keys on the *value*. `9` in a value
 * column is a value and stays one; `(9)` between the nutrient name and that column is a marker and
 * goes. The discriminator is position and bracketing, never magnitude.
 *
 * Like [CarbohydrateTermAnchor], it can only ever **remove** candidates. It never promotes, never
 * supplies a basis, and never introduces a number the parser did not already produce.
 */
internal object UnitMarkerFilter {

    /**
     * The indices of elements on [row] that occupy a unit-marker position and must not become values.
     *
     * [allRows] and [medianHeight] are accepted so the caller's contract does not change if a later,
     * measured cross-row signal is added. Nothing here uses them today — see the class documentation
     * for the cross-row rule that was tried, measured against the canaries, and rejected.
     */
    @Suppress("UNUSED_PARAMETER")
    fun markerElementIndices(
        row: LogicalRow,
        allRows: List<LogicalRow>,
        medianHeight: Int,
    ): Set<Int> = buildSet {
        val anchors = CarbohydrateTermAnchor.nutrientAnchors(row)

        row.elements.forEachIndexed { index, element ->
            if (!isBracketed(element.text)) return@forEachIndexed
            if (!isNumericLooking(element.text)) return@forEachIndexed
            // Only the FIRST number after a nutrient name. Past that the row is in value territory,
            // where a parenthesised figure is something this filter has no business judging.
            if ((0 until index).any { isNumericLooking(row.elements[it].text) }) return@forEachIndexed
            // Something on this row must actually name a nutrient to its left, or there is no
            // annotation relationship to appeal to.
            if (anchors.none { it.left < element.box.left }) return@forEachIndexed
            add(index)
        }
    }

    /** A token whose whole content is a number, optionally bracketed: `9`, `(9)`, `2,5`. */
    private fun isNumericLooking(text: String): Boolean =
        stripBrackets(text).matches(NUMERIC_TOKEN)

    private fun isBracketed(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length >= 3 && trimmed.first() in "([{" && trimmed.last() in ")]}"
    }

    private fun stripBrackets(text: String): String =
        text.trim().trim('(', ')', '[', ']', '{', '}', ':', '.', ',').trim()

    private val NUMERIC_TOKEN = Regex("""\d{1,4}(?:[.,]\d{1,3})?""")
}
