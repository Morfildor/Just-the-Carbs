package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * How one countable unit becomes a carbohydrate figure (spec §8).
 *
 * Two legitimate shapes, made unrepresentable in any other combination:
 *
 * - [WeightBased] — "1 slice = 35 g", the app's original and preferred form. Resolves to grams via
 *   [PortionResolver] and then to carbs via [CarbCalculator], keeping one formula.
 * - [DirectCarbs] — "1 slice = 14.2 g of carbohydrate", used when the source gives per-serving carbs
 *   but no weight. There is no gram figure at all on this path and none is invented; asking the user
 *   to weigh bread the label already describes was the failure this exists to fix.
 *
 * A sealed interface rather than a nullable-field data class so that "weight-based with a null
 * weight" cannot be constructed. Room stores the discriminant plus value/basis columns, which is the
 * one place the two shapes are flattened — see `PortionUnitEntity`.
 */
sealed interface PortionConversion {

    data class WeightBased(val amountPerUnit: BigDecimal, val basis: NutritionBasis) : PortionConversion {
        init {
            require(amountPerUnit.signum() > 0) { "amountPerUnit must be positive" }
        }
    }

    data class DirectCarbs(val carbsPerUnit: BigDecimal) : PortionConversion {
        init {
            require(carbsPerUnit.signum() >= 0) { "carbsPerUnit must not be negative" }
        }
    }
}
