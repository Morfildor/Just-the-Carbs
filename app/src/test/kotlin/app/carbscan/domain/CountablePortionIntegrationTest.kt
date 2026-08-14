package app.carbscan.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * The countable-portions brief's anchor case (§1, §20): there is exactly one carbohydrate formula.
 * [PortionResolver] only ever produces the `portion` value [CarbCalculator] already accepts.
 */
class CountablePortionIntegrationTest {

    @Test
    fun `2 slices at 36 g each, 42 g carbs per 100 g, resolves to 72 g and 30_2 g carbs`() {
        val resolvedGrams = PortionResolver.resolve(count = BigDecimal("2"), amountPerUnit = BigDecimal("36"))
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("42"),
            portion = resolvedGrams,
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(0, BigDecimal("72").compareTo(resolvedGrams))
        assertEquals(0, BigDecimal("30.24").compareTo(result.exact))
        assertEquals(BigDecimal("30.2"), result.decimal)
        assertEquals(30, result.wholeGrams)
    }

    @Test
    fun `the basis carried by the resolved portion is untouched by the resolver`() {
        val resolvedMl = PortionResolver.resolve(count = BigDecimal("1.5"), amountPerUnit = BigDecimal("200"))
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("9.4"),
            portion = resolvedMl,
            basis = NutritionBasis.PER_100_ML,
        )

        assertEquals(NutritionBasis.PER_100_ML, result.basis)
    }
}
