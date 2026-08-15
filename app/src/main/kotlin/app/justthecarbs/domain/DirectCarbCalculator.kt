package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * The carbohydrate total for a direct-carb countable portion: `count x carbsPerUnit` (spec §13).
 *
 * The direct-carb counterpart to [PortionResolver], and the only place this multiplication happens.
 * Unlike [PortionResolver] — which converts to grams and hands off to [CarbCalculator] — this path
 * has no per-100 basis to go through, so it produces the carbohydrate figure itself. That does not
 * make it a second formula for the same thing: no weight exists here for [CarbCalculator] to work
 * from, which is precisely why this path exists.
 *
 * Returns a plain [BigDecimal] rather than a [CarbResult] because [CarbResult.basis] is non-null and
 * means "per 100 g/ml" — a claim a direct-carb result cannot make. The UI formats this value with
 * [ResultFormatter.decimal]/[ResultFormatter.whole], which take raw values.
 *
 * Exact, never pre-rounded: display rounding belongs to [ResultFormatter] alone, and rounding here
 * would let a meal total disagree with the lines it is the sum of.
 */
object DirectCarbCalculator {

    fun exactCarbs(count: BigDecimal, carbsPerUnit: BigDecimal): BigDecimal {
        require(count.signum() >= 0) { "count must not be negative" }
        require(carbsPerUnit.signum() >= 0) { "carbsPerUnit must not be negative" }

        return count.multiply(carbsPerUnit)
    }
}
