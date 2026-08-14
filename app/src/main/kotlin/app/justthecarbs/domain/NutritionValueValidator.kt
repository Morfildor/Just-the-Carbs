package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * Gatekeeper for carbohydrate values arriving from anywhere the app does not control — Open Food
 * Facts today, other providers later, OCR after user confirmation (brief §13).
 *
 * A rejected value is `null`, never a substituted or defaulted number. Missing data stays missing:
 * the UI shows *"Carbohydrate value unavailable"* with *Scan label* / *Enter manually*, because a
 * correct failure beats an incorrect calculation.
 */
object NutritionValueValidator {

    /** 100 g of anything cannot hold more than 100 g of carbohydrate. Arithmetic, not nutrition. */
    private const val MAX_PER_100_G = 100.0

    /**
     * Per 100 *ml* the ceiling is a density bound, not a mass bound: a heavy syrup weighs far more
     * than 100 g per 100 ml, so a figure above 100 is legitimate. No edible liquid approaches
     * 2 g/ml, which makes 200 a safe "physically impossible above this" line.
     */
    private const val MAX_PER_100_ML = 200.0

    fun validateCarbsPer100(raw: Double?, basis: NutritionBasis): BigDecimal? {
        if (raw == null) return null
        if (raw.isNaN() || raw.isInfinite()) return null
        if (raw < 0.0) return null

        val max = when (basis) {
            NutritionBasis.PER_100_G -> MAX_PER_100_G
            NutritionBasis.PER_100_ML -> MAX_PER_100_ML
        }
        if (raw > max) return null

        // valueOf() goes via Double.toString, so 48.2 stays "48.2" instead of becoming the
        // binary-expansion 48.2000000000000028421709430404007434844970703125 that BigDecimal(double)
        // would produce.
        return BigDecimal.valueOf(raw)
    }
}
