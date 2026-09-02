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
     * Whether a value with no accompanying unit may be declined on this document.
     *
     * True when at least [MIN_UNIT_BEARING_CELLS] elements are a number fused to, or immediately
     * followed by, a recognised unit — i.e. the label demonstrably prints units on its values.
     *
     * Counted over the **whole document** rather than the carbohydrate row, deliberately: the
     * carbohydrate row is exactly the row whose unit may have been corrupted, so asking it about its
     * own convention is circular. The fat and salt rows are independent witnesses to how this label
     * is typeset.
     */
    fun mayDeclineBareValues(document: OcrDocument, rows: List<LogicalRow>): Boolean {
        var count = 0
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

            row.elements.forEachIndexed { index, element ->
                if (!looksNumeric(element.text)) return@forEachIndexed
                // A number that is part of an inline basis phrase ("per 100 g" printed inside a
                // value row) is a header token living on a value row; same reasoning as above.
                if (index in basisIndices) return@forEachIndexed
                if (CarbUnitAccompaniment.isAccompanied(element, row.elements)) {
                    count++
                    if (count >= MIN_UNIT_BEARING_CELLS) return true
                }
            }
        }
        return false
    }

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
