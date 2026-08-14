package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * One line of the temporary meal (brief §7-§9).
 *
 * An **immutable snapshot** of a calculation the user already made and accepted. Every figure
 * needed to re-display and re-total the line is held here, rather than being looked up from the
 * product again — so an item added as `48.2 g/100 g × 72 g = 34.704 g` still reads that way after
 * the product is reformulated, corrected, re-verified, or deleted.
 */
data class MealItem(
    val id: Long = 0,
    /** Null for a quick calculation, which never had a barcode. */
    val productBarcode: String?,
    val displayName: String,
    /** What the user chose, in their own terms: "2 slices", "½ pack", "200 ml" (§10). */
    val portionDescription: String,
    /** The resolved base-unit amount actually calculated with. */
    val resolvedAmount: BigDecimal,
    val basis: NutritionBasis,
    val carbsPer100: BigDecimal,
    /** The unrounded result. Summed as-is; formatting happens only after summation (§9). */
    val exactCarbs: BigDecimal,
    val addedAt: Instant,
)

/**
 * The running total of a temporary meal (§9).
 *
 * This is **not a second carbohydrate formula**. Each [MealItem.exactCarbs] was produced by
 * [CarbCalculator]; adding results the app has already computed is addition, not a parallel
 * calculation path. The app still has exactly one formula.
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
     * [basis] is taken from the first item purely as a label. It is never a conversion factor
     * (§17), and mixed-basis meals are summed as plain carbohydrate grams — the carbohydrate in
     * 200 ml of milk and in 72 g of bread are both grams of carbohydrate.
     */
    fun asResult(items: List<MealItem>): CarbResult = CarbResult(
        exact = exact(items),
        basis = items.firstOrNull()?.basis ?: NutritionBasis.PER_100_G,
    )
}
