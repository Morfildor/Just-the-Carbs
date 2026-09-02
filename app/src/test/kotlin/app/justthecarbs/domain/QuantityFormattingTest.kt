package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * [ResultFormatter.quantity] — the one place a stored or derived quantity becomes text.
 *
 * The defect it exists for: the recovery screen showed `33.3 g / 100 g` and the calculator that
 * followed showed `33.33333333`, because each site rounded (or did not round) for itself.
 */
class QuantityFormattingTest {

    private fun format(value: String) =
        ResultFormatter.quantity(BigDecimal(value), Locale.UK)

    /** The measured defect: `6 g` per an `18 g` serving normalizes to a non-terminating decimal. */
    @Test
    fun `a derived per-100 figure prints at one decimal place`() {
        assertEquals("33.3", format("33.33333333"))
    }

    /** A whole number stays whole — no gratuitous `.0`. */
    @Test
    fun `a whole quantity keeps no trailing decimal`() {
        assertEquals("72", format("72.0"))
        assertEquals("72", format("72"))
        assertEquals("6", format("6"))
    }

    /** A value the user typed is unchanged. */
    @Test
    fun `a one-decimal quantity is unchanged`() {
        assertEquals("0.5", format("0.5"))
        assertEquals("4.5", format("4.5"))
        assertEquals("59.2", format("59.2"))
    }

    /** HALF_UP, the app's rule — not `DecimalFormat`'s HALF_EVEN default. */
    @Test
    fun `rounding is half up`() {
        assertEquals("0.3", format("0.25"))
        assertEquals("2.5", format("2.45"))
        // HALF_EVEN would give 2.2 here; the app rounds away from zero.
        assertEquals("2.3", format("2.25"))
    }

    @Test
    fun `zero prints as zero`() {
        assertEquals("0", format("0"))
        assertEquals("0", format("0.00"))
    }

    /**
     * Locale-aware, like every other function in [ResultFormatter].
     *
     * A Dutch reader sees `33,3`. This is the one place a decimal separator may be ambiguous —
     * everything upstream stays in [BigDecimal].
     */
    @Test
    fun `the decimal separator follows the locale`() {
        assertEquals("33,3", ResultFormatter.quantity(BigDecimal("33.33333333"), Locale("nl", "NL")))
    }

    /**
     * The precision matches [ResultFormatter.decimal], which formats the result itself.
     *
     * If these disagreed, a per-100 figure and the result derived from it would claim different
     * amounts of precision on the same screen.
     */
    @Test
    fun `quantity and decimal agree on how much precision the app claims`() {
        val value = BigDecimal("33.33333333")
        assertEquals(
            ResultFormatter.decimal(value, Locale.UK),
            // decimal always prints one place; quantity trims a trailing zero. On a value with a
            // non-zero first decimal they must produce the same string.
            ResultFormatter.quantity(value, Locale.UK),
        )
    }
}
