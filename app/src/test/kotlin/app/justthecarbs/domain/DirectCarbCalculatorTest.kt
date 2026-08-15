package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

// Suite: direct-carb calculation
// Invariant: count x carbsPerUnit, exact, with no intermediate rounding and no grams anywhere.
// This is the only place that multiplication happens, mirroring PortionResolver's role for weights.
class DirectCarbCalculatorTest {

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `four slices at 14 point 2 g carbs each`() {
        assertDecimal("56.8", DirectCarbCalculator.exactCarbs(BigDecimal("4"), BigDecimal("14.2")))
    }

    @Test
    fun `a single unit returns the per-unit value unchanged`() {
        assertDecimal("12.6", DirectCarbCalculator.exactCarbs(BigDecimal.ONE, BigDecimal("12.6")))
    }

    @Test
    fun `a fractional count is supported`() {
        assertDecimal("7.1", DirectCarbCalculator.exactCarbs(BigDecimal("0.5"), BigDecimal("14.2")))
    }

    @Test
    fun `zero count is zero carbs`() {
        assertDecimal("0", DirectCarbCalculator.exactCarbs(BigDecimal.ZERO, BigDecimal("14.2")))
    }

    @Test
    fun `the result is exact and not pre-rounded`() {
        // 3 x 4.567 = 13.701. Rounding here would make a meal total disagree with its own lines.
        assertDecimal("13.701", DirectCarbCalculator.exactCarbs(BigDecimal("3"), BigDecimal("4.567")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative count is rejected`() {
        DirectCarbCalculator.exactCarbs(BigDecimal("-1"), BigDecimal("14.2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative per-unit value is rejected`() {
        DirectCarbCalculator.exactCarbs(BigDecimal.ONE, BigDecimal("-14.2"))
    }
}
