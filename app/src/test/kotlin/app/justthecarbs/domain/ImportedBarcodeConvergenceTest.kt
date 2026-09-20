package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The convergence claim, asserted rather than argued: **a barcode from a photograph and a barcode
 * from the camera are validated and normalised by one boundary, not two that agree.**
 *
 * The camera path is `ML Kit → BarcodeFrameReader.read → NormalizedBarcode →
 * BarcodeStabilityTracker → onBarcode`; the photo path is `ML Kit → BarcodeFrameReader.read →
 * NormalizedBarcode → ImportedBarcodeSelection → onBarcode`. The shared segment is the whole of
 * validation and normalisation, which is what makes "one downstream lookup" a property of the code.
 *
 * These cases drive [BarcodeFrameReader] with the arguments each caller passes, so a future change
 * that gave the photo path its own validation would have to delete a test rather than merely drift.
 */
class ImportedBarcodeConvergenceTest {

    /** What the live analyzer passes: a rotation-corrected frame size. */
    private fun asCameraFrame(raw: String, format: BarcodeFormat) = BarcodeFrameReader.read(
        rawValue = raw,
        format = format,
        boxLeft = 300,
        boxTop = 800,
        boxRight = 780,
        boxBottom = 1000,
        uprightWidth = 1080,
        uprightHeight = 1920,
        timestampNanos = 1_000L,
    )

    /** What the imported-photo reader passes: the InputImage's own already-upright size. */
    private fun asImportedPhoto(raw: String, format: BarcodeFormat?) = BarcodeFrameReader.read(
        rawValue = raw,
        format = format,
        // A deliberately different position and a much larger image: an imported photograph is
        // whatever resolution the user's gallery holds, and the barcode may be anywhere in it.
        boxLeft = 40,
        boxTop = 60,
        boxRight = 190,
        boxBottom = 130,
        uprightWidth = 4032,
        uprightHeight = 3024,
        timestampNanos = 2_000L,
    )

    @Test
    fun `a photo and a camera frame produce the same value for the same code`() {
        val codes = listOf(
            "8712100849060" to BarcodeFormat.EAN_13,
            "96385074" to BarcodeFormat.EAN_8,
            "036000291452" to BarcodeFormat.UPC_A,
        )

        codes.forEach { (raw, format) ->
            val camera = asCameraFrame(raw, format)
            val photo = asImportedPhoto(raw, format)

            assertNotNull("the camera path must read $raw", camera)
            assertNotNull("the photo path must read $raw", photo)
            assertEquals(
                "$raw must normalise identically whichever way it arrived",
                camera!!.value,
                photo!!.value,
            )
        }
    }

    @Test
    fun `a UPC-A photographed reaches the same 13-digit key the camera produces`() {
        // The normalisation that makes a barcode a database key — not merely a passthrough — and
        // the clearest evidence the photo path is not doing its own thing.
        val photo = asImportedPhoto("036000291452", BarcodeFormat.UPC_A)

        assertEquals("0036000291452", photo?.value)
    }

    @Test
    fun `a failed check digit is refused on the photo path exactly as on the camera path`() {
        // One digit changed from a real code: well-formed, right length, wrong checksum. It must
        // not reach selection at all, which is what keeps an invalid candidate out of a choice list.
        val broken = "8712100849061"

        assertNull(asCameraFrame(broken, BarcodeFormat.EAN_13))
        assertNull(asImportedPhoto(broken, BarcodeFormat.EAN_13))
    }

    @Test
    fun `an unsupported symbology is refused on the photo path`() {
        // `format = null` is what an ML Kit constant outside the configured four maps to. A QR code
        // in the same photograph must contribute nothing rather than being guessed at as a product.
        assertNull(asImportedPhoto("HTTPS://EXAMPLE.TEST", null))
    }

    @Test
    fun `invalid detections are excluded before selection sees them`() {
        // The end-to-end shape of the exclusion: recognition produces a mix, the reader maps each
        // through BarcodeFrameReader, and only survivors reach the selection rule — so a photograph
        // holding one valid code and two misreads continues immediately rather than asking.
        val recognised = listOf(
            "8712100849060" to BarcodeFormat.EAN_13,
            "8712100849061" to BarcodeFormat.EAN_13,
            "12345678" to BarcodeFormat.EAN_8,
        )

        val survivors = recognised.mapNotNull { (raw, format) -> asImportedPhoto(raw, format) }

        assertEquals("only the valid code may survive", 1, survivors.size)
        assertEquals(
            ImportedBarcodeSelection.Outcome.Single("8712100849060"),
            ImportedBarcodeSelection.of(survivors),
        )
    }

    @Test
    fun `a photo holding only invalid codes produces no barcode rather than a guess`() {
        val recognised = listOf(
            "8712100849061" to BarcodeFormat.EAN_13,
            "5000159484696" to BarcodeFormat.EAN_13,
        )

        val survivors = recognised.mapNotNull { (raw, format) -> asImportedPhoto(raw, format) }

        assertTrue(survivors.isEmpty())
        assertEquals(ImportedBarcodeSelection.Outcome.None, ImportedBarcodeSelection.of(survivors))
    }

    @Test
    fun `a barcode small and off-centre in a photo is still read`() {
        // The case the live path's geometry gate would refuse and the photo path must not: a code
        // occupying a fraction of a screenshot, nowhere near its centre. BarcodeStabilityTracker's
        // MIN_LONGEST_SIDE is 0.20 and its region is inset by 0.15, both of which this fails.
        val corner = BarcodeFrameReader.read(
            rawValue = "8712100849060",
            format = BarcodeFormat.EAN_13,
            boxLeft = 20,
            boxTop = 30,
            boxRight = 120,
            boxBottom = 70,
            uprightWidth = 2000,
            uprightHeight = 1500,
            timestampNanos = 0L,
        )

        assertNotNull("an imported photo is not subject to the live aim gates", corner)
        assertEquals(
            ImportedBarcodeSelection.Outcome.Single("8712100849060"),
            ImportedBarcodeSelection.of(listOfNotNull(corner)),
        )

        // And the control: the same detection genuinely would be refused by the live tracker, so
        // the case above is a real difference between the paths rather than a vacuous assertion.
        val tracker = BarcodeStabilityTracker()
        repeat(BarcodeAcceptanceThresholds.REQUIRED_FRAMES + 1) { frame ->
            val acceptance = tracker.onFrame(
                listOfNotNull(corner),
                frame * BarcodeAcceptanceThresholds.STABLE_DURATION_NANOS,
            )
            assertTrue(
                "the live path must never accept a code this small and this far off-centre",
                acceptance !is BarcodeAcceptance.Accepted,
            )
        }
    }
}
