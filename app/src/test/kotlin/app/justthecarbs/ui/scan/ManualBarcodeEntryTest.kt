package app.justthecarbs.ui.scan

import app.justthecarbs.domain.BarcodeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the manual-barcode field accepts, and — more importantly — what it refuses to do to a number
 * (1.0.8).
 *
 * The rules are extracted from the sheet so they are checkable without a Compose harness: "what
 * exactly happens to a pasted code" is a safety question, and a silently repaired barcode produces
 * a confident lookup for an unrelated product with nothing on screen to show it went wrong.
 */
class ManualBarcodeEntryTest {

    /** A real EAN-13 (Coca-Cola 330ml), used throughout so validity is not accidental. */
    private val valid13 = "5449000000996"

    // ---- what the field accepts -------------------------------------------------------------

    @Test
    fun `digits are kept as typed`() {
        assertEquals(valid13, manualBarcodeInput(valid13))
    }

    @Test
    fun `a pasted code keeps its digits and drops the packaging around them`() {
        // What the clipboard actually holds when a code is copied from a message or an order page.
        assertEquals(valid13, manualBarcodeInput("  $valid13\n"))
        assertEquals(valid13, manualBarcodeInput("EAN: $valid13"))
        assertEquals(valid13, manualBarcodeInput("5449 0000 00996"))
    }

    @Test
    fun `a paste with no digits yields nothing to enter`() {
        // The sheet leaves the field untouched in this case rather than clearing what was typed.
        assertEquals("", manualBarcodeInput("no barcode here"))
        assertEquals("", manualBarcodeInput(""))
    }

    @Test
    fun `input is capped at the longest supported GTIN`() {
        assertEquals(14, manualBarcodeInput("1".repeat(40)).length)
    }

    // ---- what it must never do ----------------------------------------------------------------

    @Test
    fun `filtering never alters the digits themselves`() {
        // The safety rule. Removing a non-digit is a refusal to accept a character; changing a
        // digit would be a repair, and a repaired barcode is a confident lookup for the wrong
        // product. Asserted as "the digits out are the digits in, in order".
        val messy = "5a4b4c9 0-0/0.0[0]0996"
        assertEquals(messy.filter(Char::isDigit), manualBarcodeInput(messy))
    }

    @Test
    fun `a code with a broken check digit stays broken`() {
        // The last digit of this code is wrong by one. Nothing recomputes it, so it is still
        // invalid after passing through the field — which is what puts the error on screen
        // instead of quietly looking up a different product.
        val broken = "5449000000997"
        assertEquals(broken, manualBarcodeInput(broken))
        assertNull(BarcodeValidator.normalize(manualBarcodeInput(broken)))
        assertTrue(manualBarcodeShowsError(broken))
    }

    @Test
    fun `a code of an unsupported length is not padded to a supported one`() {
        val nine = "544900000"
        assertEquals(nine, manualBarcodeInput(nine))
        assertNull(BarcodeValidator.normalize(nine))
    }

    // ---- when the error appears ---------------------------------------------------------------

    @Test
    fun `nothing is flagged while the code is still being typed`() {
        // Every valid code passes through being too short. Complaining at digit 1 is technically
        // true and practically hostile, so the message waits until there is enough to judge.
        assertFalse(manualBarcodeShowsError(""))
        assertFalse(manualBarcodeShowsError("5"))
        assertFalse(manualBarcodeShowsError("54490"))
        assertFalse(manualBarcodeShowsError("5449000"))
    }

    @Test
    fun `an invalid code of judgeable length is flagged`() {
        assertTrue(manualBarcodeShowsError("12345678"))
    }

    @Test
    fun `a valid code is never flagged`() {
        assertFalse(manualBarcodeShowsError(valid13))
        // EAN-8 and UPC-A, the other two printed forms someone might transcribe.
        assertFalse(manualBarcodeShowsError("96385074"))
        assertFalse(manualBarcodeShowsError("036000291452"))
    }

    // ---- the one downstream flow ----------------------------------------------------------------

    @Test
    fun `an accepted code is the same normalised form a scan produces`() {
        // What makes "one barcode flow, not two" true rather than aspirational: this screen emits
        // `BarcodeValidator.normalize`'s output, which is the database key a camera detection also
        // resolves to. A UPC-A typed off a package becomes the same 13-digit GTIN either way.
        val typed = BarcodeValidator.normalize(manualBarcodeInput("036000291452"))
        val scanned = BarcodeValidator.validate("036000291452", app.justthecarbs.domain.BarcodeFormat.UPC_A)
        assertNotNull(typed)
        assertEquals(scanned, typed)
        assertEquals("0036000291452", typed)
    }

    @Test
    fun `every supported printed form can be entered by hand`() {
        // The formats a user can actually read off a package. UPC-E is deliberately absent: its
        // 8 printed digits are a compression that only means anything with the symbology the
        // scanner reports, and manual entry has no such signal — so an 8-digit manual entry is
        // judged as EAN-8, which is what the length-based rules here do.
        listOf(valid13, "96385074", "036000291452").forEach { code ->
            assertNotNull("$code should be enterable", BarcodeValidator.normalize(manualBarcodeInput(code)))
        }
    }
}
