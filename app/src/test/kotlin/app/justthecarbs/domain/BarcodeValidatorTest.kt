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
}
