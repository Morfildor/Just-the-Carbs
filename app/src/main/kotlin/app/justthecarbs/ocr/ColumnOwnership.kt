package app.justthecarbs.ocr

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Which cell on a row a column is entitled to claim.
 *
 * ## The rule
 *
 * > A cell **outside** a column's strict tolerance may not claim that column when another
 * > value-shaped cell on the same row sits **inside** it.
 *
 * The two tolerances already in [NutritionParserThresholds] mean different things, and this is the
 * rule that keeps them meaning different things.
 * [NutritionParserThresholds.STRICT_COLUMN_FRACTION] is *this cell is printed in that column*.
 * [NutritionParserThresholds.LOOSE_COLUMN_FRACTION] is a skew allowance — it exists so a
 * photographed table's drift cannot detach a cell from **its own** column. It was never meant to be
 * a licence to reach across a table into a column that already has a cell printed squarely inside
 * it, and that is the only thing this object forbids.
 *
 * ## What it deliberately does not do
 *
 * It does not rank. Two cells both inside the strict tolerance are both still claims, and the
 * reading stays [LabelReading.Ambiguous] exactly as before — pinned by
 * `NutritionTableInterpreterTest.a genuinely unresolvable layout is ambiguous rather than guessed`,
 * where `45` and `51` sit 2 px and 47 px from a column whose strict tolerance is 84 px. Resolving
 * that pair by nearness would be a proximity score, which is the mechanism the geometry-first
 * rewrite removed and which this must not reintroduce.
 *
 * So this only ever **removes** a distant claim in favour of a present one. It cannot introduce a
 * reading, cannot move one between columns, and cannot turn an ambiguity into a confident answer.
 *
 * ## Why this object exists rather than the rule living at each call site
 *
 * The rule was written on 2026-09-02 for [RecoveryCandidates] alone, to stop a per-100 figure whose
 * own column OCR had destroyed being relabelled with the surviving serving column's basis. It was
 * never applied to the **automatic** path, and the twelfth phone session shows what that costs.
 *
 * On the pickle capture `20260903-212700-478` the label prints
 *
 * ```
 * koolhydraten     5,4 g / 100 g     1,6 g / 30 g part
 * ```
 *
 * ML Kit read the per-100 cell's unit as a digit (`5,4` `9`), so [CarbUnitAccompaniment] declined
 * `5,4` as a carbohydrate value — correctly, and that refusal is unchanged. `1,6` still carried a
 * clean `g`, and with the per-100 column's own cell out of the running it bound to that column
 * 243 px away and the app reported **`Confident 1.6/PER_100_G`** — the 30 g serving figure wearing
 * the per-100 basis, a third of the printed value, offered as a reading.
 *
 * The decisive detail is that `5,4` sits **17 px** from the per-100 column's centre and `1,6` sits
 * **243 px** from it, against a strict tolerance of 202 px. `5,4` lost its eligibility as a *value*;
 * it never stopped **occupying** the column. Those are different questions, and conflating them is
 * what let a distant cell inherit a column that was plainly already spoken for.
 *
 * So ownership is decided over every value-shaped cell on the row, **before and independently of**
 * whether a cell is admissible as a carbohydrate quantity. A cell the parser refuses still blocks
 * the column it sits in. That is the whole safety claim of this object, and
 * `TwelfthSessionRegressionTest` pins it with the pickle's own geometry.
 *
 * ## What it is not
 *
 * It is not a proximity score and does not rank columns — [NutritionTableInterpreter.columnFor] and
 * [RecoveryCandidates] still choose the nearest column within the existing tolerance, unchanged.
 * This only ever **removes** a claim, so it cannot introduce a reading and cannot move one from one
 * column to another. On a healthy table each cell's own column is nearest to it, nothing is
 * contested, and every cell keeps the basis it had.
 */
internal object ColumnOwnership {

    /**
     * The value-shaped elements on [row] that compete for a column.
     *
     * Deliberately permissive, and deliberately *not* the parser's candidate list. A cell declined
     * for stating no unit, a cell a different nutrient's anchor claimed, a percentage — each is
     * still a number printed in a column, and each still occupies the position it was printed at.
     * Narrowing this to eligible candidates would reintroduce the defect: on the pickle the only
     * surviving *candidate* on the row is the serving figure, so a candidate-based contest would
     * find nothing to compete with and hand it the per-100 column exactly as before.
     */
    fun competingCells(row: LogicalRow): List<OcrElement> = competingCells(row.elements)

    /** As [competingCells], for a caller that holds the row's elements rather than the row. */
    fun competingCells(elements: List<OcrElement>): List<OcrElement> =
        elements.filter { valueIn(it.text) != null }

    /**
     * Whether [column] may claim the cell at [cellBox], given [competing] from the same row.
     *
     * [competing] is matched by box rather than by identity so a caller holding a derived cell — the
     * interpreter's `NumberCell`, which carries a box and a parsed value but not the element — can
     * ask the same question about the same printed position.
     *
     * [documentWidth] sizes the strict tolerance, exactly as every other column-distance test in the
     * parser sizes it, so this cannot drift from what "in that column" means elsewhere.
     */
    fun claims(
        column: NutritionColumn,
        cellBox: OcrBox,
        competing: List<OcrElement>,
        documentWidth: Int,
    ): Boolean {
        val strict = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.STRICT_COLUMN_FRACTION,
        )
        // A cell printed inside the column keeps its claim whatever else is on the row: the rule is
        // about reaching *into* a column from outside it, never about ranking two cells within one.
        if (abs(column.centerX - cellBox.centerX) <= strict) return true
        return competing.none { other ->
            other.box != cellBox && abs(column.centerX - other.box.centerX) <= strict
        }
    }

    /**
     * A number, optionally with a fused unit. Anchored, so a word containing digits is not a value.
     *
     * The same shape [RecoveryCandidates] reads a candidate value with — one definition, so the two
     * surfaces cannot disagree about which printed tokens are cells competing for a column.
     */
    private val VALUE_TOKEN = Regex(
        "^(\\d{1,3}(?:[.,]\\d{1,3})?)\\s*[.,]?\\s*(?:g|gr|gram|grammes?|ml)?[.,;:]?$",
        RegexOption.IGNORE_CASE,
    )

    fun valueIn(text: String): BigDecimal? {
        val match = VALUE_TOKEN.find(text.trim()) ?: return null
        return match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
    }
}
