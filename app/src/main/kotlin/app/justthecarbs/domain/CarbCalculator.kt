package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The result of one carbohydrate calculation.
 *
 * [exact] is never rounded. Rounding happens only at the display step (brief §17), so both
 * [decimal] and [wholeGrams] are derived from [exact] independently — [wholeGrams] is NOT rounded
 * from [decimal], which would round twice and can shift the whole gram by one.
 */
data class CarbResult(
    val exact: BigDecimal,
    val basis: NutritionBasis,
) {
    /** Supporting value, e.g. `31.3 g calculated` (brief §18). */
    val decimal: BigDecimal get() = exact.setScale(1, RoundingMode.HALF_UP)

    /** Dominant value, e.g. `31 g`. Mathematically correct nearest-whole rounding (brief §18). */
    val wholeGrams: Int get() = exact.setScale(0, RoundingMode.HALF_UP).toInt()
}

/**
 * The safety-critical calculation, isolated from all UI (brief §17).
 *
 * `carbohydrates = carbsPer100 × portion / 100`, in the product's own basis unit.
 */
object CarbCalculator {

    fun calculate(
        carbsPer100: BigDecimal,
        portion: BigDecimal,
        basis: NutritionBasis,
    ): CarbResult {
        // A negative input is never a real product or a real portion; it means bad remote data or a
        // parsing bug upstream. Fail loudly rather than showing a plausible-looking wrong number
        // (brief §13: correct failure beats incorrect calculation). Callers validate first — see
        // NutritionValueValidator and PortionParser — so this is the last line of defence.
        require(carbsPer100.signum() >= 0) { "carbsPer100 must not be negative" }
        require(portion.signum() >= 0) { "portion must not be negative" }

        // movePointLeft(2) is the exact "/ 100": it is a scale shift, so unlike divide() it can
        // never round, never throw on a non-terminating quotient, and never loses a digit.
        val exact = carbsPer100.multiply(portion).movePointLeft(2)
        return CarbResult(exact = exact, basis = basis)
    }
}
