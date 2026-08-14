package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * Anchor cases from brief §17. These three are quoted verbatim in the requirements and are the
 * contract the rest of the app is built on.
 */
class CarbCalculatorTest {

    /**
     * [CarbResult.exact] is compared by numeric value, not by `equals`, because BigDecimal's
     * `equals` is scale-sensitive (`15.45` != `15.450`) and the scale of the unrounded value is an
     * implementation artifact. The *displayed* values are asserted with `assertEquals` on purpose —
     * there, scale is the behaviour (`12.0` must not collapse to `12`).
     */
    private fun assertSameValue(expected: String, actual: BigDecimal) {
        assertEquals(
            "expected $expected but was $actual",
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    @Test
    fun `48_2 g per 100 g over a 65 g portion is 31_33 exact, 31_3 shown, 31 g rounded`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("48.2"),
            portion = BigDecimal("65"),
            basis = NutritionBasis.PER_100_G,
        )

        assertSameValue("31.33", result.exact)
        assertEquals(BigDecimal("31.3"), result.decimal)
        assertEquals(31, result.wholeGrams)
    }

    @Test
    fun `52 g per 100 g over a 30 g portion is 15_6 shown and 16 g rounded`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("52"),
            portion = BigDecimal("30"),
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(BigDecimal("15.6"), result.decimal)
        assertEquals(16, result.wholeGrams)
    }

    @Test
    fun `4_8 g per 100 ml over a 250 ml portion is 12_0 shown and 12 g rounded`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("4.8"),
            portion = BigDecimal("250"),
            basis = NutritionBasis.PER_100_ML,
        )

        assertEquals(BigDecimal("12.0"), result.decimal)
        assertEquals(12, result.wholeGrams)
    }

    @Test
    fun `a zero portion is zero carbohydrates, not an error`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("48.2"),
            portion = BigDecimal.ZERO,
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(0, result.wholeGrams)
        assertEquals(0, result.exact.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `a zero-carb product yields zero for any portion`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal.ZERO,
            portion = BigDecimal("500"),
            basis = NutritionBasis.PER_100_ML,
        )

        assertEquals(0, result.wholeGrams)
    }

    @Test
    fun `a large portion does not lose precision`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("48.2"),
            portion = BigDecimal("2500"),
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(BigDecimal("1205.0"), result.decimal)
        assertEquals(1205, result.wholeGrams)
    }

    /**
     * The double-rounding guard (brief §17 "never round internally before completion").
     * Exact is 15.45: the whole gram must come from 15.45 (→ 15), NOT from the already-rounded
     * decimal 15.5 (→ 16). Rounding twice would hand the user a gram that is simply wrong.
     */
    @Test
    fun `the whole gram is rounded from the exact value, never from the shown decimal`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("51.5"),
            portion = BigDecimal("30"),
            basis = NutritionBasis.PER_100_G,
        )

        assertSameValue("15.45", result.exact)
        assertEquals(BigDecimal("15.5"), result.decimal)
        assertEquals(15, result.wholeGrams)
    }

    @Test
    fun `an exact half rounds up`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("50"),
            portion = BigDecimal("31"),
            basis = NutritionBasis.PER_100_G,
        )

        assertSameValue("15.50", result.exact)
        assertEquals(16, result.wholeGrams)
    }

    /**
     * Design decision 3.1 / brief §17: `1 ml = 1 g` must NEVER be assumed. The basis is a label,
     * not a conversion factor — identical numbers must produce an identical result on both bases.
     * If anyone ever introduces a density fudge, this test fails.
     */
    @Test
    fun `the basis never applies a density conversion`() {
        val grams = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("4.8"),
            portion = BigDecimal("250"),
            basis = NutritionBasis.PER_100_G,
        )
        val millilitres = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("4.8"),
            portion = BigDecimal("250"),
            basis = NutritionBasis.PER_100_ML,
        )

        assertEquals(grams.exact, millilitres.exact)
        assertEquals(NutritionBasis.PER_100_ML, millilitres.basis)
    }

    /**
     * Regression: the formatter and the calculator must round identically.
     *
     * `DecimalFormat` defaults to HALF_EVEN, so 15.45 formatted naively becomes "15.4" while
     * `CarbResult.decimal` (HALF_UP) is 15.5 — the app would show a different number from the one
     * it calculated. Caught by a UI test; pinned here where it is cheap to run.
     */
    @Test
    fun `the displayed decimal uses the same rounding as the calculation`() {
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("51.5"),
            portion = BigDecimal("30"),
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(BigDecimal("15.5"), result.decimal)
        assertEquals("15.5", ResultFormatter.decimal(result.exact, java.util.Locale.ROOT))
        assertEquals(15, result.wholeGrams)
        assertEquals("15", ResultFormatter.whole(result.wholeGrams, java.util.Locale.ROOT))
    }

    @Test
    fun `a negative carbohydrate value is rejected rather than calculated`() {
        assertThrows(IllegalArgumentException::class.java) {
            CarbCalculator.calculate(
                carbsPer100 = BigDecimal("-1"),
                portion = BigDecimal("65"),
                basis = NutritionBasis.PER_100_G,
            )
        }
    }

    @Test
    fun `a negative portion is rejected rather than calculated`() {
        assertThrows(IllegalArgumentException::class.java) {
            CarbCalculator.calculate(
                carbsPer100 = BigDecimal("48.2"),
                portion = BigDecimal("-65"),
                basis = NutritionBasis.PER_100_G,
            )
        }
    }
}
