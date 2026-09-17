package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * [ResultFormatter.editable] — a number the app writes into a field the user may edit.
 *
 * Added 2026-09-17: on a device whose locale writes a decimal comma, the calculator showed `62,5`
 * as a result beside a portion field the app had filled with `62.5`. Both parse, but one screen
 * should not use two decimal separators.
 */
class EditableNumberFormattingTest {

    private val turkish = Locale.forLanguageTag("tr-TR")

    @Test
    fun `every digit is kept`() {
        assertEquals("12.25", ResultFormatter.editable(BigDecimal("12.25"), Locale.UK))
        assertEquals("3.125", ResultFormatter.editable(BigDecimal("3.125"), Locale.UK))
        assertEquals("65", ResultFormatter.editable(BigDecimal("65.0"), Locale.UK))
        assertEquals("0", ResultFormatter.editable(BigDecimal("0.00"), Locale.UK))
    }

    @Test
    fun `a Turkish field uses the decimal comma`() {
        assertEquals("12,5", ResultFormatter.editable(BigDecimal("12.5"), turkish))
    }

    /** A grouping separator in a field would read back as a decimal one. */
    @Test
    fun `no grouping separator is ever written`() {
        assertEquals("1500", ResultFormatter.editable(BigDecimal("1500"), turkish))
        assertEquals("1500,5", ResultFormatter.editable(BigDecimal("1500.5"), turkish))
        assertEquals("1500.5", ResultFormatter.editable(BigDecimal("1500.5"), Locale.US))
    }

    @Test
    fun `what is written parses back to the same value`() {
        val locales = listOf(turkish, Locale.forLanguageTag("nl-NL"), Locale.US, Locale.GERMANY)
        val values = listOf("0.125", "12.5", "62.25", "1500", "3.125").map(::BigDecimal)
        for (locale in locales) {
            for (value in values) {
                val parsed = PortionParser.parse(ResultFormatter.editable(value, locale))
                assertEquals("$value in $locale", 0, value.compareTo(parsed))
            }
        }
    }

    @Test
    fun `Turkish input parses the same whatever the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(turkish)
            assertEquals(0, BigDecimal("12.5").compareTo(PortionParser.parse("12,5")))
            assertEquals(0, BigDecimal("12.5").compareTo(PortionParser.parse("12.5")))
            assertEquals(0, BigDecimal("0.5").compareTo(PortionParser.parse(",5")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** Refused outright, never read as something else. */
    @Test
    fun `malformed Turkish-style input is refused`() {
        listOf("1.250,5", "12,5 g", "12,,5", "₺12", "12,5,0", "-12,5", "1e3").forEach { text ->
            assertNull(text, PortionParser.parse(text))
        }
    }
}
