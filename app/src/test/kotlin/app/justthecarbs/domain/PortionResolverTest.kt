package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * Anchor cases from the countable-portions brief §20. [PortionResolver] is a pure conversion layer
 * in front of [CarbCalculator] — it must never itself compute a carbohydrate value.
 */
class PortionResolverTest {

    private fun assertSameValue(expected: String, actual: BigDecimal) {
        assertEquals(
            "expected $expected but was $actual",
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    @Test
    fun `2 slices at 36 g each resolves to 72 g`() {
        val resolved = PortionResolver.resolve(count = BigDecimal("2"), amountPerUnit = BigDecimal("36"))

        assertSameValue("72", resolved)
    }

    @Test
    fun `half a bar at 40 g resolves to 20 g`() {
        val resolved = PortionResolver.resolve(count = BigDecimal("0.5"), amountPerUnit = BigDecimal("40"))

        assertSameValue("20", resolved)
    }

    @Test
    fun `2_5 biscuits at 12_5 g each resolves to 31_25 g`() {
        val resolved = PortionResolver.resolve(count = BigDecimal("2.5"), amountPerUnit = BigDecimal("12.5"))

        assertSameValue("31.25", resolved)
    }

    @Test
    fun `zero count resolves to zero, not an error`() {
        val resolved = PortionResolver.resolve(count = BigDecimal.ZERO, amountPerUnit = BigDecimal("36"))

        assertSameValue("0", resolved)
    }

    @Test
    fun `a large count does not lose precision`() {
        val resolved = PortionResolver.resolve(count = BigDecimal("250"), amountPerUnit = BigDecimal("36"))

        assertSameValue("9000", resolved)
    }

    @Test
    fun `a negative count is rejected rather than resolved`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortionResolver.resolve(count = BigDecimal("-1"), amountPerUnit = BigDecimal("36"))
        }
    }

    @Test
    fun `a negative amount per unit is rejected rather than resolved`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortionResolver.resolve(count = BigDecimal("2"), amountPerUnit = BigDecimal("-36"))
        }
    }
}
