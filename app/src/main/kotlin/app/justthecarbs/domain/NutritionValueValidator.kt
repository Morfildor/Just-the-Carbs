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

    /**
     * A serving's total carbohydrate, which has no fixed size to bound it (spec §7).
     *
     * [MAX_PER_100_G]'s reasoning — "100 g of anything cannot hold more than 100 g of carbohydrate"
     * — is arithmetic about a fixed 100 g and does not transfer: a 500 g ready meal can legitimately
     * carry well over 100 g. So this only rejects what is clearly corrupt rather than merely large,
     * because a false rejection here silently costs the user the countable-portion path.
     */
    fun validateCarbsPerServing(raw: Double?): BigDecimal? {
        if (raw == null) return null
        if (raw.isNaN() || raw.isInfinite()) return null
        if (raw < 0.0) return null
        if (raw > MAX_PER_SERVING) return null

        return BigDecimal.valueOf(raw)
    }

    /**
     * No edible serving holds this much carbohydrate; a figure above it is a unit error or corrupt
     * data. Deliberately far above any real serving so genuine large portions are never refused.
     */
    private const val MAX_PER_SERVING = 1_000.0
}
