package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * Correcting one line of the temporary meal to a different amount of the same thing (1.0.8).
 *
 * A [MealItem] is an immutable calculation snapshot, and a correction keeps it one: the replacement
 * is calculated from the line's **own** figures — its per-100 value and basis, or its carbohydrate
 * per unit — and never from the product as it is now. Correcting "72 g" to "85 g" of a product that
 * has since been reformulated still multiplies the figure the user accepted when they added it,
 * which is the same guarantee the line gave before it was touched (§9).
 *
 * The editable quantity is the one the snapshot actually stores: the resolved g/ml amount of a
 * weighed line, the count of a direct-carb line. A weighed line added as "2 slices" is corrected as
 * the 70 g it resolved to, because the snapshot does not record which unit produced it — and its
 * wording is never parsed back into a count to pretend otherwise.
 *
 * Not a third formula: the two calculators each shape already uses do the arithmetic.
 */
object MealItemCorrection {

    /** What a correction starts from: the resolved amount of a weighed line, or a direct-carb count. */
    fun amountOf(item: MealItem): BigDecimal = when (item.kind) {
        MealItemKind.WEIGHT_BASED -> requireNotNull(item.resolvedAmount) { "a weighed line has an amount" }
        MealItemKind.DIRECT_CARBS -> requireNotNull(item.count) { "a direct-carb line has a count" }
    }

    /** The unrounded carbohydrate [amount] of this line holds, by the calculator its shape uses. */
    fun exactCarbs(item: MealItem, amount: BigDecimal): BigDecimal = when (item.kind) {
        MealItemKind.WEIGHT_BASED -> CarbCalculator.calculate(
            carbsPer100 = requireNotNull(item.carbsPer100) { "a weighed line has a per-100 figure" },
            portion = amount,
            basis = requireNotNull(item.basis) { "a weighed line has a basis" },
        ).exact
        MealItemKind.DIRECT_CARBS -> DirectCarbCalculator.exactCarbs(
            count = amount,
            carbsPerUnit = requireNotNull(item.carbsPerUnit) { "a direct-carb line has carbs per unit" },
        )
    }

    /**
     * Whether [amount] may replace this line's: a positive number that differs from the one it has.
     *
     * Numeric comparison, so `70.0` is the same `70` — a re-typed but identical amount is not a
     * correction and must not rewrite the line.
     */
    fun isCorrection(item: MealItem, amount: BigDecimal?): Boolean =
        amount != null && amount.signum() > 0 && amount.compareTo(amountOf(item)) != 0

    /**
     * The same line with [amount] in place of its own: same id, product, name, position and shape,
     * with the carbohydrate recalculated and the wording replaced.
     *
     * [portionDescription] is supplied by the screen, because words live in resources; it must
     * describe [amount] — the old wording is never carried over, since "2 slices" beside 105 g would
     * be a false statement about the line.
     */
    fun corrected(item: MealItem, amount: BigDecimal, portionDescription: String): MealItem {
        require(amount.signum() > 0) { "a meal line needs a positive amount" }
        val exact = exactCarbs(item, amount)
        return when (item.kind) {
            MealItemKind.WEIGHT_BASED -> item.copy(
                portionDescription = portionDescription,
                resolvedAmount = amount,
                exactCarbs = exact,
            )
            MealItemKind.DIRECT_CARBS -> item.copy(
                portionDescription = portionDescription,
                count = amount,
                exactCarbs = exact,
            )
        }
    }
}
