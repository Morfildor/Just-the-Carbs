package app.justthecarbs.ocr

/**
 * Decides whether [CarbUnitAccompaniment]'s question can be asked of a given table at all.
 *
 * ## Why the filter needs a policy in front of it
 *
 * [CarbUnitAccompaniment] answers one question about one token: *is a recognised unit printed with
 * this value?* Its answer is correct and its tests pin it against real corrupted tokens, including
 * the deliberate assertion that a bare `72,0` is **not** accompanied. That strictness is the point —
 * at token scope, the absence of a unit is the only observable difference between a printed `0,5 g`
 * whose `g` became a `9` and a genuine `0.59`.
 *
 * Applied to every candidate on every label, though, it also declines a whole legitimate layout: a
 * table that prints its unit **only in the column header** and bare numbers down the column. That
 * layout is not corrupted; it is how a large minority of real tables are typeset, and it is what
 * most of this repo's synthetic fixtures describe. Declining it is a coverage regression to
 * `NotFound` — a UX cost, not an accuracy cost — but taken across every such label it is a large one,
 * and it is avoidable without weakening anything.
 *
 * ## The distinction this makes, and why it is not the rejected sibling rule
 *
 * The owner rejected a *sibling-conditional* form of the accompaniment rule: "decline when
 * neighbouring cells carry units and this one does not". It fails because the Boursin declaration
 * prints well-formed `19g` and `5,5g.` in the same table as the corrupted `3q.`, so a sibling check
 * finds units nearby and lets the corruption through.
 *
 * That objection is about a token whose unit glyph became **another letter** — `3q.`, `2.5c`. Such a
 * token is declined by [CarbUnitAccompaniment] on its own text, before any question of neighbours
 * arises, and this policy never reaches it: `mayDeclineBareValues` gates only the **bare-number**
 * branch. A corrupted-suffix token is refused on every label, unconditionally, exactly as before.
 *
 * What this asks is a different and weaker question, about the *label* rather than about a value's
 * neighbours:
 *
 * > Does this table demonstrate that it prints units on its value cells?
 *
 * When it does, a value cell with no unit is anomalous against the label's own typesetting, and the
 * accompaniment rule applies. When no value cell anywhere carries a unit, the label states its units
 * in the header and a bare number is ordinary rather than suspicious — so the rule is not asked, and
 * behaviour is exactly what it was before the filter was wired in.
 *
 * ## Measured on the label the rule exists for
 *
 * The green drink (`docs/Scan Evidence 01-09-26/20260901-211417-935`) prints
 * `0g`, `13g`, `13g`, `0g` — four unit-bearing value cells — alongside the corrupted `0.59`. So the
 * convention is demonstrated, the rule applies, and `0.59` is declined. The confident-wrong closes.
 *
 * The threshold is deliberately **two** cells, not one: a single `0g` could itself be a
 * misrecognition, and one observation is not a convention. Two independent cells agreeing that this
 * label prints units is the same "one observation is not evidence" standard the percent-column
 * fallback already uses.
 */
internal object UnitAccompanimentPolicy {

    /**
     * How many unit-bearing value cells a document must show before a bare number is treated as
     * anomalous.
     *
     * Two, for the same reason [ColumnClassifier]'s percent fallback requires two cells: one cell is
     * a stray, two are a convention. Set higher, a short table (a drink with four nutrient rows)
     * would stop qualifying and the confident-wrong this closes would reopen.
     */
    private const val MIN_UNIT_BEARING_CELLS = 2

    /**
     * How many **distinct rows** must show a corrupted unit glyph before that alone is a convention.
     *
     * Three, and the two halves of that are separately load-bearing.
     *
     * *Distinct rows*, because several corrupted cells on one reconstructed row can be one bad
     * recognition of one printed row — the fat row's value and its neighbour's, merged. Independent
     * nutrient rows are independent witnesses to how the label is typeset, which is the same
     * reasoning that makes this policy count over the document rather than over the carbohydrate row.
     *
     * *Three* rather than [MIN_UNIT_BEARING_CELLS]'s two, because this evidence is strictly weaker: a
     * clean `2.1g` states what was printed, whereas `0.59` only states that *something* followed the
     * number. Requiring a clear majority of a table's nutrient rows to show the same damage is what
     * separates thorough glyph corruption from a stray misread on a label that genuinely prints bare
     * values.
     */
    private const val MIN_CORRUPTED_UNIT_ROWS = 3

    /**
     * Whether a value with no accompanying unit may be declined on this document.
     *
     * True when at least [MIN_UNIT_BEARING_CELLS] elements are a number fused to, or immediately
     * followed by, a recognised unit — i.e. the label demonstrably prints units on its values.
     *
     * Counted over the **whole document** rather than the carbohydrate row, deliberately: the
     * carbohydrate row is exactly the row whose unit may have been corrupted, so asking it about its
     * own convention is circular. The fat and salt rows are independent witnesses to how this label
     * is typeset.
     *
     * ## When no clean unit survives at all
     *
     * A clean unit is normally required — see [looksLikeCorruptedUnit]. But on
     * `docs/Scan Evidence 04-09 2nd test/20260904-124935-320` ML Kit fused the `g` onto **every** cell
     * of a Lidl drink printing `6,2 g / 100 ml`, so there was no clean survivor to require, the label
     * read as one stating units in its headers only, and the corrupted `6,29` was offered for
     * confirmation. The controlled comparison is in the same session: `20260904-124924-679` is the
     * same package seconds earlier with its `g` glyphs intact, and there the convention is seen and
     * `6.2` is read.
     *
     * So corruption spread across [MIN_CORRUPTED_UNIT_ROWS] separate nutrient rows establishes the
     * convention on its own. This still cannot make a corrupted value *acceptable* —
     * [CarbUnitAccompaniment] refuses those tokens outright either way. It only stops thorough
     * damage from being read as proof that the label prints bare numbers.
     */
    fun mayDeclineBareValues(document: OcrDocument, rows: List<LogicalRow>): Boolean {
        var count = 0
        var cleanUnitSeen = false
        var corruptedRows = 0
        rows.forEach { row ->
            // Header rows are excluded, and this is load-bearing rather than tidiness. A header
            // states the basis — `per 100 g`, `100 ml` — so its own tokens are a number beside a
            // unit and would satisfy the accompaniment test. Counting them would make **every**
            // table with a unit-bearing header look like a table that prints units on its values,
            // which is the opposite of what this policy is asking, and would decline every bare
            // value on exactly the labels the header exists to serve.
            if (RowClassifier.classify(row) == NutritionRowKind.HEADER) return@forEach

            // Computed once per row. It was previously evaluated inside the element loop below,
            // which re-ran the whole inline-basis span walk for every element on the row — one of
            // the three nested-loop multipliers behind the 2026-09-01 parse regression.
            val basisIndices = basisIndices(row)
            var rowShowedCorruption = false

            row.elements.forEachIndexed { index, element ->
                if (!looksNumeric(element.text) && !looksLikeCorruptedUnit(element.text)) {
                    return@forEachIndexed
                }
                // A number that is part of an inline basis phrase ("per 100 g" printed inside a
                // value row) is a header token living on a value row; same reasoning as above.
                if (index in basisIndices) return@forEachIndexed
                if (looksNumeric(element.text) &&
                    CarbUnitAccompaniment.isAccompanied(element, row.elements)
                ) {
                    cleanUnitSeen = true
                    count++
                } else if (looksLikeCorruptedUnit(element.text)) {
                    // Evidence that *something* was printed after the number, not that it was a unit.
                    count++
                    rowShowedCorruption = true
                }
                if (count >= MIN_UNIT_BEARING_CELLS && cleanUnitSeen) return true
            }

            // Counted per row, not per cell: two corrupted cells on one reconstructed row may be one
            // damaged printed row rather than two witnesses to the label's typesetting.
            if (rowShowedCorruption) {
                corruptedRows++
                if (corruptedRows >= MIN_CORRUPTED_UNIT_ROWS) return true
            }
        }
        return false
    }

    /**
     * A value cell whose trailing glyph is a digit where a unit belongs.
     *
     * ## Why this counts, and what would go wrong without it
     *
     * The policy's evidence is destroyed by the very corruption it exists to catch. On
     * `docs/Scan Evidence new structure/20260904-113653-044` the Fanta prints `g` on **all twelve** of
     * its value cells, and ML Kit returned `0g`, `0.59`, `0.59`, `09`, `0` down the 100 ml column: one
     * clean unit survived, against [MIN_UNIT_BEARING_CELLS] of two. So the label was judged to state
     * "units in its headers only", the accompaniment rule was never asked, and the corrupted `0.59`
     * was accepted as an ordinary bare value.
     *
     * That is backwards: **the more thoroughly the unit glyphs are damaged, the more ordinary a bare
     * value looks**, so the threshold becomes unreachable precisely on the labels that need it.
     *
     * A trailing digit run on an otherwise well-formed value (`09`, `0.59`, `14.59`, `145`) is
     * therefore counted as a witness that this label prints *something* after its numbers. It is not
     * counted as a unit — [CarbUnitAccompaniment] still refuses these tokens outright, so nothing
     * here can make a corrupted value acceptable. It only stops the corruption from being read as
     * proof of a bare-value convention.
     *
     * ## Why a clean unit is usually required, and what happens when none survives
     *
     * The mixed case — some glyphs damaged, some intact — requires at least one *clean* unit-bearing
     * cell, so the evidence stays positive: a real unit is observed somewhere and the damaged cells
     * corroborate the convention rather than establishing it alone. Two witnesses suffice there.
     *
     * Thorough corruption leaves no clean survivor to require, and demanding one would make the
     * threshold unreachable on exactly the labels that need it — which is how `6,29` was offered on a
     * package printing `6,2 g`. So corruption seen on [MIN_CORRUPTED_UNIT_ROWS] separate nutrient
     * rows establishes the convention by itself.
     *
     * The legitimate header-only layout is protected by this pattern rather than by that clean-unit
     * requirement, and the distinction is measurable: a label printing bare `2.1`, `4.8`, `3.6` — or
     * bare integers `21`, `36`, `47`, `72` — matches **nothing** here, because a corrupted cell must
     * show a trailing digit where a unit belongs. Both of those layouts are pinned by
     * [UnitConventionSemanticsTest].
     */
    private fun looksLikeCorruptedUnit(text: String): Boolean =
        CORRUPTED_UNIT_CELL.matches(text.trim())

    /**
     * A number with a trailing digit where a unit glyph belongs.
     *
     * Requires a decimal separator (`0.59`, `14.59`) or a leading zero (`09`) so an ordinary integer
     * value like `47` or `72` is not mistaken for a corrupted cell — those are legitimate readings
     * this rule must stay silent about, and [ScaleAmbiguity] is what governs them.
     */
    private val CORRUPTED_UNIT_CELL = Regex("^(?:\\d{1,3}[.,]\\d{1,3}9|0\\d)[.,;:]?$")

    /** Element indices belonging to a basis declaration printed inside [row]. */
    private fun basisIndices(row: LogicalRow): Set<Int> =
        InlineBasisSpans.find(row).flatMap { it.elementIndices }.toSet()

    /**
     * A token that is a number, possibly with a unit or trailing punctuation fused to it.
     *
     * Used only to decide what to *count* as a value cell. A nutrient name, a language fragment or a
     * bare unit element is not evidence about how values are typeset.
     */
    private fun looksNumeric(text: String): Boolean = NUMERIC_CELL.matches(text.trim())

    private val NUMERIC_CELL = Regex(
        "^\\d{1,4}(?:[.,]\\d{1,3})?\\s*(?:g|gr|gram|grammes?|ml)?[.,;:]?$",
        RegexOption.IGNORE_CASE,
    )
}
