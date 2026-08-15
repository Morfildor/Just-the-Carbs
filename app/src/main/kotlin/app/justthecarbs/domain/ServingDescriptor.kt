package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** A quantity together with the unit it is measured in. Never converted between g and ml. */
data class AmountWithBasis(val amount: BigDecimal, val basis: NutritionBasis)

/**
 * What a serving-size string *says*, separated from what it lets the app *calculate*.
 *
 * Splitting these apart is what makes direct-carb portions possible: "2 slices" is a perfectly good
 * descriptor even though it carries no weight, and a per-serving carbohydrate figure can then supply
 * the missing relationship. The old parser fused the two and returned null for any string without a
 * weight, which is why a user with a countable product and no printed weight was made to fetch a
 * kitchen scale.
 */
data class ServingDescriptor(
    val kind: PortionUnitKind,
    /** How many units the serving covers. 1 for a bare "1 slice"/"slice". Never inferred above 1. */
    val count: BigDecimal,
    /** Null when no weight/volume was printed. Never a sentinel, never estimated. */
    val weightOrVolume: AmountWithBasis?,
    val rawText: String,
) {
    /**
     * The weight of a single unit, or null when no weight was printed.
     *
     * "2 slices (70 g)" describes 35 g per slice — the app never stores "1 slice = 70 g".
     */
    val amountPerUnit: AmountWithBasis?
        get() = weightOrVolume?.let {
            AmountWithBasis(
                amount = it.amount.divide(count, SCALE, RoundingMode.HALF_UP).stripTrailingZeros(),
                basis = it.basis,
            )
        }

    private companion object {
        /** Matches the scale the previous per-unit division used. */
        const val SCALE = 4
    }
}
