package app.carbscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Brief §13: never trust a remote value blindly. Reject negatives, NaN, infinity, malformed
 * numbers and impossible unit combinations. Do not invent missing data.
 *
 * Every rejection here ends the same way in the UI — "Carbohydrate value unavailable" plus
 * *Scan label* / *Enter manually* — so the validator returns null rather than a reason code.
 */
class NutritionValueValidatorTest {

    private fun assertAccepts(raw: Double, basis: NutritionBasis, expected: String) {
        val accepted = NutritionValueValidator.validateCarbsPer100(raw, basis)
        assertEquals("raw $raw", 0, BigDecimal(expected).compareTo(accepted))
    }

    @Test
    fun `accepts a normal declared value`() {
        assertAccepts(48.2, NutritionBasis.PER_100_G, "48.2")
    }

    @Test
    fun `accepts zero carbohydrates`() {
        assertAccepts(0.0, NutritionBasis.PER_100_G, "0")
    }

    @Test
    fun `accepts pure sugar at 100 g per 100 g`() {
        assertAccepts(100.0, NutritionBasis.PER_100_G, "100")
    }

    @Test
    fun `rejects a missing value rather than defaulting it to zero`() {
        assertNull(NutritionValueValidator.validateCarbsPer100(null, NutritionBasis.PER_100_G))
    }

    @Test
    fun `rejects a negative value`() {
        assertNull(NutritionValueValidator.validateCarbsPer100(-0.1, NutritionBasis.PER_100_G))
    }

    @Test
    fun `rejects NaN`() {
        assertNull(NutritionValueValidator.validateCarbsPer100(Double.NaN, NutritionBasis.PER_100_G))
    }

    @Test
    fun `rejects infinity`() {
        assertNull(
            NutritionValueValidator.validateCarbsPer100(
                Double.POSITIVE_INFINITY,
                NutritionBasis.PER_100_G,
            ),
        )
        assertNull(
            NutritionValueValidator.validateCarbsPer100(
                Double.NEGATIVE_INFINITY,
                NutritionBasis.PER_100_G,
            ),
        )
    }

    /** 100 g of food cannot contain more than 100 g of carbohydrate. This is arithmetic, not taste. */
    @Test
    fun `rejects more than 100 g of carbohydrate per 100 g`() {
        assertNull(NutritionValueValidator.validateCarbsPer100(100.1, NutritionBasis.PER_100_G))
        assertNull(NutritionValueValidator.validateCarbsPer100(4820.0, NutritionBasis.PER_100_G))
    }

    /**
     * Per 100 *ml* the ceiling is higher, because a dense syrup weighs well over 100 g per 100 ml.
     * The bound is physical (no edible liquid approaches 2 g/ml), not nutritional.
     */
    @Test
    fun `accepts a dense syrup above 100 g per 100 ml`() {
        assertAccepts(117.0, NutritionBasis.PER_100_ML, "117")
    }

    @Test
    fun `rejects a physically impossible value per 100 ml`() {
        assertNull(NutritionValueValidator.validateCarbsPer100(201.0, NutritionBasis.PER_100_ML))
    }
}
