package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/** Which of the two legitimate meal-item shapes a row holds. Always explicit, never inferred. */
enum class MealItemKind { WEIGHT_BASED, DIRECT_CARBS }

/**
 * One line of the temporary meal (brief §7-§9).
 *
 * An **immutable snapshot** of a calculation the user already made and accepted. Every figure
 * needed to re-display and re-total the line is held here, rather than being looked up from the
 * product again — so an item added as `48.2 g/100 g x 72 g = 34.704 g` still reads that way after
 * the product is reformulated, corrected, re-verified, or deleted.
 *
 * The kind-specific fields are nullable in exactly two disciplined shapes, gated by [kind]: a
 * `WEIGHT_BASED` item has [resolvedAmount]/[basis]/[carbsPer100] and no count; a `DIRECT_CARBS` item
 * has [count]/[carbsPerUnit] and **no grams at all**. A direct-carb item must never carry a
 * fabricated [resolvedAmount] — the app does not know what four slices weigh, and writing a number
 * there would make it indistinguishable from a weighed portion. Use [weightBased]/[directCarbs]
 * rather than the constructor so an invalid mixture is not constructible by accident.
 */
data class MealItem(
    val id: Long = 0,
    /** Null for a quick calculation, which never had a barcode. */
    val productBarcode: String?,
    val displayName: String,
    /** What the user chose, in their own terms: "2 slices", "½ pack", "200 ml" (§10). */
    val portionDescription: String,
    val kind: MealItemKind,
    /** The resolved base-unit amount actually calculated with. WEIGHT_BASED only. */
    val resolvedAmount: BigDecimal?,
    /** WEIGHT_BASED only. */
    val basis: NutritionBasis?,
    /** WEIGHT_BASED only. */
    val carbsPer100: BigDecimal?,
    /** How many units. DIRECT_CARBS only. */
    val count: BigDecimal?,
    /** Carbohydrate in one unit. DIRECT_CARBS only. */
    val carbsPerUnit: BigDecimal?,
    /** The unrounded result. Summed as-is; formatting happens only after summation (§9). */
    val exactCarbs: BigDecimal,
    val addedAt: Instant,
) {
    companion object {
        fun weightBased(
            id: Long = 0,
            productBarcode: String?,
            displayName: String,
            portionDescription: String,
            resolvedAmount: BigDecimal,
            basis: NutritionBasis,
            carbsPer100: BigDecimal,
            exactCarbs: BigDecimal,
            addedAt: Instant,
        ): MealItem = MealItem(
            id = id,
            productBarcode = productBarcode,
            displayName = displayName,
            portionDescription = portionDescription,
            kind = MealItemKind.WEIGHT_BASED,
            resolvedAmount = resolvedAmount,
            basis = basis,
            carbsPer100 = carbsPer100,
            count = null,
            carbsPerUnit = null,
            exactCarbs = exactCarbs,
            addedAt = addedAt,
        )

        fun directCarbs(
            id: Long = 0,
            productBarcode: String?,
            displayName: String,
            portionDescription: String,
            count: BigDecimal,
            carbsPerUnit: BigDecimal,
            exactCarbs: BigDecimal,
            addedAt: Instant,
        ): MealItem = MealItem(
            id = id,
            productBarcode = productBarcode,
            displayName = displayName,
            portionDescription = portionDescription,
            kind = MealItemKind.DIRECT_CARBS,
            resolvedAmount = null,
            basis = null,
            carbsPer100 = null,
            count = count,
            carbsPerUnit = carbsPerUnit,
            exactCarbs = exactCarbs,
            addedAt = addedAt,
        )
    }
}

/**
 * The running total of a temporary meal (§9).
 *
 * This is **not a second carbohydrate formula**. Each [MealItem.exactCarbs] was produced by
 * [CarbCalculator] or [DirectCarbCalculator]; adding results the app has already computed is
 * addition, not a parallel calculation path. The app still has one formula per portion shape.
 *
 * The total is `sum(exactCarbs)` over unrounded values, so it can never be the sum of rounded
 * display strings — `18.65 + 21.65` is `40.30`, not the `40` or `40.4` that pre-rounding would
 * produce.
 */
object MealTotal {

    /** Exact sum of every item. Zero for an empty meal — never null, so callers need no branch. */
    fun exact(items: List<MealItem>): BigDecimal =
        items.fold(BigDecimal.ZERO) { running, item -> running.add(item.exactCarbs) }

    /**
     * The same total as a [CarbResult], so the meal screen can reuse [ResultFormatter] and display
     * the decimal and whole-gram figures exactly as the calculator does.
     *
     * [CarbResult.basis] is taken from the first item that has one, purely as a label. It is never a
     * conversion factor (§17), and mixed-basis meals are summed as plain carbohydrate grams — the
     * carbohydrate in 200 ml of milk and in 72 g of bread are both grams of carbohydrate. A meal of
     * only direct-carb items has no basis anywhere, so it falls back to the same default an empty
     * meal uses; the figure is unaffected either way.
     */
    fun asResult(items: List<MealItem>): CarbResult = CarbResult(
        exact = exact(items),
        basis = items.firstNotNullOfOrNull { it.basis } ?: NutritionBasis.PER_100_G,
    )
}
