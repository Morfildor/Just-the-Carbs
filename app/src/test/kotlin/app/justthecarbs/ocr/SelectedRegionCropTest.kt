package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Coordinate mapping for the native-resolution re-recognition (spec §3, §25 NativeRegionRecognition).
 *
 * This is pinned in JVM tests for the same reason [CropSelectionGeometry] is: a rectangle that looks
 * right on screen but maps to the wrong bitmap pixels would crop the wrong part of the label, and the
 * symptom — a failed read — is indistinguishable from ordinary recognition failure. Arithmetic errors
 * here are invisible on a device.
 */
class SelectedRegionCropTest {

    @Test
    fun `maps a normalized region onto source pixels`() {
        val rect = SelectedRegionCrop.toPixels(
            NormalizedRegion(0.25, 0.5, 0.75, 1.0),
            sourceWidth = 1000,
            sourceHeight = 800,
        )

        assertNotNull(rect)
        assertEquals(250, rect!!.left)
        assertEquals(400, rect.top)
        assertEquals(500, rect.width)
        assertEquals(400, rect.height)
    }

    /** The crop must never address a pixel outside the bitmap; createBitmap throws if it does. */
    @Test
    fun `a full-bleed region stays inside the bitmap bounds`() {
        val rect = SelectedRegionCrop.toPixels(
            NormalizedRegion(0.0, 0.0, 1.0, 0.5),
            sourceWidth = 640,
            sourceHeight = 480,
        )

        assertNotNull(rect)
        assertEquals(0, rect!!.left)
        assertEquals(640, rect.right)
        assertEquals(240, rect.bottom)
    }

    @Test
    fun `a null region attempts no second pass`() {
        assertNull(SelectedRegionCrop.toPixels(null, 1000, 1000))
    }

    /**
     * Re-recognising the whole frame would duplicate Pass A exactly.
     *
     * Two identical passes are not independent evidence, so allowing this would let the resolver
     * "corroborate" a value against itself — turning a single opinion into a false consensus.
     */
    @Test
    fun `a whole-frame selection is refused so it cannot corroborate itself`() {
        assertNull(SelectedRegionCrop.toPixels(NormalizedRegion(0.0, 0.0, 1.0, 1.0), 1000, 1000))
        assertNull(SelectedRegionCrop.toPixels(NormalizedRegion(0.005, 0.005, 0.995, 0.995), 1000, 1000))
    }

    @Test
    fun `a region smaller than the minimum side is refused`() {
        // 2% of 1000px = 20px, below MIN_SIDE_PX.
        assertNull(SelectedRegionCrop.toPixels(NormalizedRegion(0.4, 0.4, 0.42, 0.42), 1000, 1000))
    }

    /**
     * An inverted rectangle cannot reach this code at all — [NormalizedRegion] refuses to exist.
     *
     * Asserted rather than assumed, and asserted at the constructor rather than at [SelectedRegionCrop]:
     * the guarantee is a type invariant, so it holds for every caller, present and future. (An earlier
     * draft of this test tried to *pass* an inverted region in and discovered the value could not be
     * built — which is the stronger property, so it is what gets pinned.)
     */
    @Test
    fun `an inverted region is unconstructible so it can never be cropped`() {
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedRegion(0.8, 0.2, 0.2, 0.8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedRegion(0.5, 0.5, 0.5, 0.5)
        }
    }

    @Test
    fun `a zero-sized source is refused`() {
        assertNull(SelectedRegionCrop.toPixels(NormalizedRegion(0.1, 0.1, 0.9, 0.9), 0, 0))
    }

    /** Crop-space geometry must come back to source space or every downstream overlay is offset. */
    @Test
    fun `boxes translate from crop space back to source space`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 350, width = 400, height = 300)
        val inCropSpace = OcrBox(left = 10, top = 20, right = 60, bottom = 45)

        val inSourceSpace = SelectedRegionCrop.toSourceSpace(inCropSpace, crop)

        assertEquals(210, inSourceSpace.left)
        assertEquals(370, inSourceSpace.top)
        assertEquals(260, inSourceSpace.right)
        assertEquals(395, inSourceSpace.bottom)
    }

    /** Round-tripping must be exact: translating in and back out changes nothing. */
    @Test
    fun `source-space translation preserves box dimensions exactly`() {
        val crop = SelectedRegionCrop.PixelRect(left = 137, top = 449, width = 500, height = 500)
        val box = OcrBox(left = 3, top = 7, right = 103, bottom = 57)

        val moved = SelectedRegionCrop.toSourceSpace(box, crop)

        assertEquals(box.width, moved.width)
        assertEquals(box.height, moved.height)
    }
}
