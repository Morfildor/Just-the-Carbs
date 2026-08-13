package app.carbscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Portion text -> number. Brief §16 (fast decimal entry), §42 (locale-safe parsing), §59
 * (locale-safe input, malformed values).
 *
 * The parser deliberately accepts BOTH `.` and `,` as the decimal separator regardless of the
 * device locale, rather than using a locale NumberFormat. A Dutch user on an English phone still
 * types `6,5`, and a locale-bound parser would silently read that as 65 — a ten-fold portion error.
 * Accepting both and rejecting anything ambiguous is the safer reading of §42's "unambiguous
 * internal decimal handling".
 */
class PortionParserTest {

    private fun assertParses(input: String, expected: String) {
        val parsed = PortionParser.parse(input)
        assertEquals("input '$input'", 0, BigDecimal(expected).compareTo(parsed))
    }

    @Test
    fun `parses a plain whole number`() {
        assertParses("65", "65")
    }

    @Test
    fun `parses a point as the decimal separator`() {
        assertParses("6.5", "6.5")
    }

    @Test
    fun `parses a comma as the decimal separator`() {
        assertParses("6,5", "6.5")
    }

    @Test
    fun `parses zero`() {
        assertParses("0", "0")
    }

    @Test
    fun `parses a large portion`() {
        assertParses("2500", "2500")
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertParses("  65  ", "65")
    }

    @Test
    fun `treats a leading separator as a leading zero`() {
        assertParses(".5", "0.5")
    }

    /** Mid-typing state: the user has typed `5.` on the way to `5.5`. The result must not vanish. */
    @Test
    fun `treats a trailing separator as the whole number typed so far`() {
        assertParses("5.", "5")
        assertParses("5,", "5")
    }

    @Test
    fun `rejects empty and blank input`() {
        assertNull(PortionParser.parse(""))
        assertNull(PortionParser.parse("   "))
    }

    @Test
    fun `rejects a lone separator`() {
        assertNull(PortionParser.parse("."))
        assertNull(PortionParser.parse(","))
    }

    @Test
    fun `rejects more than one separator`() {
        assertNull(PortionParser.parse("6.5.5"))
        assertNull(PortionParser.parse("6,5,5"))
        assertNull(PortionParser.parse("6.5,5"))
    }

    @Test
    fun `rejects non-numeric text`() {
        assertNull(PortionParser.parse("abc"))
        assertNull(PortionParser.parse("65 g"))
    }

    @Test
    fun `rejects a negative portion`() {
        assertNull(PortionParser.parse("-5"))
    }

    /**
     * BigDecimal would happily accept `1e3` as 1000. A user cannot type that on a numeric keypad,
     * so its only route in is a paste or a bug — and reading it as 1000 g would be a silent
     * thousand-fold error.
     */
    @Test
    fun `rejects scientific notation`() {
        assertNull(PortionParser.parse("1e3"))
        assertNull(PortionParser.parse("1E3"))
    }
}
