package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Duration
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

/** The amount this line was calculated from, in the terms its portion field takes: grams/ml or a count. */
val MealItem.editableAmount: BigDecimal?
    get() = when (kind) {
        MealItemKind.WEIGHT_BASED -> resolvedAmount
        MealItemKind.DIRECT_CARBS -> count
    }

/**
 * This line with a different amount, recalculated from the line's **own** stored figures.
 *
 * Never from the product: the line is a snapshot, and a correction made to the product since it was
 * added must not leak into a line the user is only resizing (§9). The carbohydrate figure comes
 * from the same calculator that produced it originally, so the app still has one formula per portion
 * shape. Identity, name, basis and [MealItem.addedAt] are kept, so the line keeps its place.
 *
 * Null for an amount that is not positive (a zero line is a removal, which has its own action) and
 * for a row missing the figures its [MealItem.kind] requires, which cannot be recalculated honestly.
 */
fun MealItem.withPortion(amount: BigDecimal, portionDescription: String): MealItem? {
    if (amount.signum() <= 0) return null
    return when (kind) {
        MealItemKind.WEIGHT_BASED -> {
            val per100 = carbsPer100 ?: return null
            val itemBasis = basis ?: return null
            copy(
                portionDescription = portionDescription,
                resolvedAmount = amount,
                exactCarbs = CarbCalculator.calculate(per100, amount, itemBasis).exact,
            )
        }
        MealItemKind.DIRECT_CARBS -> {
            val perUnit = carbsPerUnit ?: return null
            copy(
                portionDescription = portionDescription,
                count = amount,
                exactCarbs = DirectCarbCalculator.exactCarbs(amount, perUnit),
            )
        }
    }
}

/** A meal in progress that has gone quiet — see [MealStaleness]. */
data class StaleMeal(
    val itemCount: Int,
    val exactCarbs: BigDecimal,
    /** Time between the most recent addition and now, whichever way the clock moved. */
    val sinceLastAdded: Duration,
)

/**
 * Whether the next item added probably belongs to a **different** eating session than the meal
 * already stored.
 *
 * The meal survives restarts on purpose, so an app switch or a process death cannot cost a
 * half-built plate. The same persistence means a meal nobody cleared is still there hours later,
 * and the next Add would quietly put breakfast on top of last night's dinner: a total that is too
 * high, read off a screen designed to be copied into another calculator.
 *
 * The signal is the time since the **most recent** addition, not the age of the oldest item. A long
 * dinner that gets a dessert added counts as active, and once the user has answered "add to this
 * meal" the new line is itself recent, so the question is asked once per session, never on every
 * add that follows.
 *
 * [AFTER] is two hours: longer than the gaps inside one meal (the items of a plate, then a dessert),
 * and no longer than the usual gap between one eating occasion and the next. Being wrong in either
 * direction costs little: a false alarm is one extra tap, and nothing is ever cleared without the
 * user choosing it.
 *
 * The distance is taken in either direction: a last addition two hours in the *future* means the
 * clock was moved, and a timestamp that far from now says nothing about the current session. Small
 * corrections (network time adjusting by seconds) stay well inside the window.
 */
object MealStaleness {

    val AFTER: Duration = Duration.ofHours(2)

    /** Null for an empty meal or one added to within [AFTER] of [now]. */
    fun check(items: List<MealItem>, now: Instant): StaleMeal? {
        val lastAdded = items.maxOfOrNull { it.addedAt } ?: return null
        val since = Duration.between(lastAdded, now).abs()
        if (since < AFTER) return null
        return StaleMeal(itemCount = items.size, exactCarbs = MealTotal.exact(items), sinceLastAdded = since)
    }
}
