package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {

    @Test
    fun `accepts real EAN-13 codes`() {
        assertTrue(BarcodeValidator.isValid("8712100849060"))
        assertTrue(BarcodeValidator.isValid("5000159484695"))
    }

    @Test
    fun `accepts a real EAN-8 code`() {
        assertTrue(BarcodeValidator.isValid("96385074"))
    }

    @Test
    fun `accepts a real UPC-A code`() {
        assertTrue(BarcodeValidator.isValid("036000291452"))
    }

    /** A single mistyped digit must not pass, or the app looks up an unrelated product. */
    @Test
    fun `rejects a code with a wrong check digit`() {
        assertFalse(BarcodeValidator.isValid("8712100849061"))
        assertFalse(BarcodeValidator.isValid("5000159484696"))
    }

    @Test
    fun `rejects the wrong number of digits`() {
        assertFalse(BarcodeValidator.isValid("123"))
        assertFalse(BarcodeValidator.isValid("871210084906"))
    }

    @Test
    fun `rejects non-digits`() {
        assertFalse(BarcodeValidator.isValid("87121008490AB"))
        assertFalse(BarcodeValidator.isValid(""))
    }

    @Test
    fun `tolerates surrounding whitespace from a paste`() {
        assertTrue(BarcodeValidator.isValid("  8712100849060 "))
    }

    /** One physical product must not be able to occupy two rows with two verified values. */
    @Test
    fun `normalises UPC-A to its 13-digit GTIN`() {
        assertEquals("0036000291452", BarcodeValidator.normalize("036000291452"))
    }

    @Test
    fun `leaves an EAN-13 unchanged`() {
        assertEquals("8712100849060", BarcodeValidator.normalize("8712100849060"))
    }

    @Test
    fun `normalising an invalid code yields nothing`() {
        assertNull(BarcodeValidator.normalize("8712100849061"))
    }

    // --- Format-aware validate() ---------------------------------------------------------------
    //
    // UPC-E is a zero-suppressed compression of a 12-digit UPC-A, not "an 8-digit code" in the same
    // sense EAN-8 is. These pin that an 8-digit UPC-E raw value is never accidentally validated with
    // EAN-8's plain length/check-digit rule, and that the standard UPC-E<->UPC-A expansion table
    // (GS1 General Specifications; cross-checked against the worked example on Wikipedia's Universal
    // Product Code article, "654321" -> "065100004327"/"165100004324") is implemented correctly.

    @Test
    fun `validates a real EAN-13 with its format`() {
        assertEquals("8712100849060", BarcodeValidator.validate("8712100849060", BarcodeFormat.EAN_13))
    }

    @Test
    fun `validates a real EAN-8 with its format`() {
        // EAN-8 has no 13-digit GTIN-14-style renormalisation rule of its own; normalize() leaves an
        // 8-digit code as-is (it is not 12 or 14 digits), so the database key stays 8 digits.
        assertEquals("96385074", BarcodeValidator.validate("96385074", BarcodeFormat.EAN_8))
    }

    @Test
    fun `rejects an EAN-8 with a wrong check digit`() {
        assertNull(BarcodeValidator.validate("96385075", BarcodeFormat.EAN_8))
    }

    @Test
    fun `validates a real UPC-A with its format, normalised to 13 digits`() {
        assertEquals("0036000291452", BarcodeValidator.validate("036000291452", BarcodeFormat.UPC_A))
    }

    @Test
    fun `rejects a UPC-A of the wrong length for its format`() {
        assertNull(BarcodeValidator.validate("8712100849060", BarcodeFormat.UPC_A))
    }

    /**
     * The worked example from GS1's zero-suppression table, reproduced on Wikipedia's Universal
     * Product Code article: UPC-E data digits "654321" expand to UPC-A "065100004327" (number
     * system 0) or "165100004324" (number system 1). Verified independently by hand (mod-10, 3x/1x
     * weighting from the right) before being used as a fixture — see [mod10CheckDigit] below.
     */
    @Test
    fun `expands a known UPC-E example to its documented UPC-A equivalent (number system 0)`() {
        assertEquals("0065100004327", BarcodeValidator.validate("06543217", BarcodeFormat.UPC_E))
    }

    @Test
    fun `same UPC-E example with number system 1`() {
        assertEquals("0165100004324", BarcodeValidator.validate("16543214", BarcodeFormat.UPC_E))
    }

    /** A second independently-verified real example (widely cited "Marlboro Lights" pair). */
    @Test
    fun `expands the Marlboro Lights UPC-E example to its known UPC-A`() {
        assertEquals("0042100005264", BarcodeValidator.validate("04252614", BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 0 (the 0-1-2 branch)`() {
        // Digits 1,2,3,4,5,0 -> NS D1 D2 D6 0000 D3 D4 D5 = 0 1 2 0 0000 3 4 5 -> body 01200000345
        val body = "01200000345"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "0" + checkDigit // NS + D1..D5 + D6=0 + check
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 2 (top of the 0-1-2 branch)`() {
        val body = "01220000345"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "2" + checkDigit
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 3 (its own branch)`() {
        // NS D1 D2 D3 00000 D4 D5 = 0 1 2 3 00000 4 5 -> body 01230000045
        val body = "01230000045"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "3" + checkDigit
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 4 (its own branch)`() {
        // NS D1 D2 D3 D4 00000 D5 = 0 1 2 3 4 00000 5 -> body 01234000005
        val body = "01234000005"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "4" + checkDigit
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 5 (bottom of the 5-9 branch)`() {
        // NS D1 D2 D3 D4 D5 0000 D6 = 0 1 2 3 4 5 0000 5 -> body 01234500005
        val body = "01234500005"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "5" + checkDigit
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `expands a UPC-E with last digit 9 (top of the 5-9 branch)`() {
        val body = "01234500009"
        val checkDigit = mod10CheckDigit(body)
        val upce = "0" + "12345" + "9" + checkDigit
        assertEquals("0" + body + checkDigit, BarcodeValidator.validate(upce, BarcodeFormat.UPC_E))
    }

    @Test
    fun `rejects a UPC-E with a wrong check digit`() {
        val body = "04210000526"
        val wrongCheck = (mod10CheckDigit(body) + 1) % 10
        assertNull(BarcodeValidator.validate("0425261$wrongCheck", BarcodeFormat.UPC_E))
    }

    @Test
    fun `rejects a UPC-E with an invalid number system`() {
        // Only 0 and 1 are valid number-system digits for standard UPC-E.
        assertNull(BarcodeValidator.validate("24252614", BarcodeFormat.UPC_E))
        assertNull(BarcodeValidator.validate("94252614", BarcodeFormat.UPC_E))
    }

    @Test
    fun `rejects a UPC-E of the wrong length`() {
        assertNull(BarcodeValidator.validate("0425261", BarcodeFormat.UPC_E))
        assertNull(BarcodeValidator.validate("042526145", BarcodeFormat.UPC_E))
    }

    @Test
    fun `rejects non-digit UPC-E input`() {
        assertNull(BarcodeValidator.validate("0425261A", BarcodeFormat.UPC_E))
    }

    /**
     * The exact regression this pass fixes, using a real collision: "00000062" is simultaneously a
     * valid plain EAN-8 (its own mod-10 check digit happens to pass) *and* a valid UPC-E (number
     * system 0, expanding to a materially different GTIN). Before this pass the scanner ignored ML
     * Kit's detected format and validated every 8-digit value as EAN-8, so a UPC-E symbol like this
     * one would have resolved to the wrong product — its raw digits unchanged, instead of expanded.
     */
    @Test
    fun `an 8-digit UPC-E value is not treated as EAN-8`() {
        val raw = "00000062"

        // As plain EAN-8, this happens to be valid on its own terms — proving the two formats are
        // not merely "invalid vs valid" but can validate to two different products.
        assertEquals(raw, BarcodeValidator.validate(raw, BarcodeFormat.EAN_8))

        // As UPC-E (its actual detected format), it must expand to the GTIN encoded by compression,
        // not be left as the raw 8 digits.
        assertEquals("0000000000062", BarcodeValidator.validate(raw, BarcodeFormat.UPC_E))
    }

    /** Mirrors [BarcodeValidator]'s private mod-10 routine so fixtures can be derived, not guessed. */
    private fun mod10CheckDigit(body: String): Int {
        val sum = body.reversed()
            .mapIndexed { index, char -> char.digitToInt() * if (index % 2 == 0) 3 else 1 }
            .sum()
        return (10 - sum % 10) % 10
    }
}
