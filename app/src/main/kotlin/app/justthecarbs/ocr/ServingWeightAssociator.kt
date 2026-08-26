package app.justthecarbs.ocr

import app.justthecarbs.domain.AmountWithBasis
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Finds the serving weight a table prints on its **own line** beneath a per-serving column header,
 * the "(12.5 g)" under "per piece" shape (spec §17).
 *
 * The header phrase itself carries no weight — "per stuk" names a unit and stops — so
 * [app.justthecarbs.domain.ServingSizeParser] correctly returns a descriptor with
 * `weightOrVolume == null`, and the app then has a per-serving carbohydrate figure it cannot convert
 * to the weight-based portion the rest of the app prefers. The weight is printed; it is just on the
 * next line.
 *
 * Association is refused unless **all** of these hold, because a wrong serving weight silently
 * rescales every portion computed from it:
 *
 * - the row says nothing but a weight, so a nutrient row that happens to contain "12,5 g" is not a
 *   candidate;
 * - it sits horizontally over the serving column, not merely somewhere on the label;
 * - it sits *below* that column's header and within [MAX_VERTICAL_GAP_IN_HEIGHTS] text heights, so a
 *   pack weight printed elsewhere cannot be adopted;
 * - and the table's own arithmetic agrees — see [agreesWithTable].
 *
 * The last condition is what makes this evidence rather than proximity. It is the caller's job
 * because only the caller knows the per-100 figure.
 */
internal object ServingWeightAssociator {

    /** Nothing but a weight, optionally bracketed: "(12,5 g)", "12.5 g", "[30 ml]". */
    private val WEIGHT_ONLY =
        Regex(
            """^[(\[]?\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*(${NutritionTerminology.basisUnitAlternation})\s*[)\]]?$""",
            RegexOption.IGNORE_CASE,
        )

    /** How far below the header the weight line may sit, in text heights. */
    private const val MAX_VERTICAL_GAP_IN_HEIGHTS = 3.0

    /** How far the weight line's centre may sit from the column's, as a fraction of image width. */
    private const val MAX_HORIZONTAL_OFFSET_FRACTION = 0.10

    fun find(
        rows: List<LogicalRow>,
        column: NutritionColumn,
        documentWidth: Int,
        medianTextHeight: Int,
    ): AmountWithBasis? {
        val headerBox = column.headerBox ?: return null
        val maxGap = medianTextHeight.coerceAtLeast(1) * MAX_VERTICAL_GAP_IN_HEIGHTS
        val maxOffset = documentWidth * MAX_HORIZONTAL_OFFSET_FRACTION

        return rows
            .asSequence()
            // Below the header, never level with it and never above: a figure over the header
            // belongs to whatever is above the table, not to this column.
            .filter { it.box.top >= headerBox.bottom - medianTextHeight / 2 }
            .filter { it.box.top - headerBox.bottom <= maxGap }
            .filter { abs(it.box.centerX - column.centerX) <= maxOffset }
            .mapNotNull { row -> parseWeightOnly(row.text)?.let { row to it } }
            .minByOrNull { (row, _) -> row.box.top }
            ?.second
    }

    private fun parseWeightOnly(text: String): AmountWithBasis? {
        // ML Kit splits "(12,5 g)" into "(12,5" and "g)", which this stage receives space-joined.
        // Collapsing the spaces makes both tokenizations one string; the anchors still require the
        // whole row to be nothing but a weight, so a nutrient line can never match.
        val match = WEIGHT_ONLY.find(text.replace(" ", "")) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        if (amount.signum() <= 0) return null
        val basis = if (match.groupValues[2].lowercase() in NutritionTerminology.millilitreUnits) {
            NutritionBasis.PER_100_ML
        } else {
            NutritionBasis.PER_100_G
        }
        return AmountWithBasis(amount = amount.stripTrailingZeros(), basis = basis)
    }

    /**
     * Whether "per 100" x weight / 100 reproduces the printed per-serving figure (spec §17).
     *
     * 53.5 g/100 g x 12.5 g = 6.6875, printed as 6.7. Exact equality is the wrong test — every figure
     * on a package is already rounded, and demanding it would reject correct labels. What a
     * disagreement *does* catch is a mis-association: a 25 g weight would predict 13.4 against a
     * printed 6.7, which no rounding explains.
     *
     * The tolerance covers the rounding of both printed figures plus a small margin for labels that
     * round to whole grams.
     */
    fun agreesWithTable(
        carbsPer100: BigDecimal,
        servingWeight: BigDecimal,
        printedCarbsPerServing: BigDecimal,
    ): Boolean {
        val expected = carbsPer100.toDouble() * servingWeight.toDouble() / 100.0
        val printed = printedCarbsPerServing.toDouble()
        val tolerance = maxOf(ABSOLUTE_TOLERANCE, printed * RELATIVE_TOLERANCE)
        return abs(expected - printed) <= tolerance
    }

    /** Covers a label that rounds its per-serving figure to a whole gram. */
    private const val ABSOLUTE_TOLERANCE = 0.6

    /** Covers proportional rounding on larger figures. */
    private const val RELATIVE_TOLERANCE = 0.05
}
