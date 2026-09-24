package app.justthecarbs.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * The figure a scanner candidate shows beside Confirm.
 *
 * It follows the device's decimal separator, like every other figure on screen, so a Dutch phone
 * reading a label that prints `7,2` shows `7,2` rather than `7.2`. It keeps every digit the reading
 * has: this is the figure the user checks against the package, and Confirm carries exactly it.
 */
class CandidateFigureTest {

    private val dutch = Locale.forLanguageTag("nl-NL")

    @Test
    fun `a Dutch device shows the decimal comma the label printed`() {
        assertEquals("7,2", candidateFigure(BigDecimal("7.2"), dutch))
    }

    @Test
    fun `an English device keeps the decimal point`() {
        assertEquals("7.2", candidateFigure(BigDecimal("7.2"), Locale.UK))
    }

    @Test
    fun `every digit of the reading is shown, never rounded away`() {
        assertEquals("2,09", candidateFigure(BigDecimal("2.09"), dutch))
    }

    @Test
    fun `trailing zeros and grouping are not shown`() {
        assertEquals("1500", candidateFigure(BigDecimal("1500.0"), dutch))
        assertEquals("72", candidateFigure(BigDecimal("72.0"), dutch))
    }
}
