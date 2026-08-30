package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: the scan guide's safety margin
// `expand` is all that remains of this object: it produces the rectangle the automatic fast path
// reads and the crop screen opens on. The `toPixels`/`PixelRegion` pre-recognition cropper it used
// to carry was removed in 1.0.3 — see ScanRegionMapper's KDoc for why cropping before OCR cost both
// canaries, and why the region is now applied after recognition instead.
class ScanRegionMapperTest {

    private val overlay = NormalizedRegion(left = 0.08, top = 0.24, right = 0.92, bottom = 0.76)

    @Test
    fun `the crop is grown beyond the drawn frame`() {
        val expanded = ScanRegionMapper.expand(overlay)
        assertTrue("left must move outward", expanded.left < overlay.left)
        assertTrue("top must move outward", expanded.top < overlay.top)
        assertTrue("right must move outward", expanded.right > overlay.right)
        assertTrue("bottom must move outward", expanded.bottom > overlay.bottom)
    }

    @Test
    fun `expansion never escapes the image`() {
        val edgeToEdge = NormalizedRegion(left = 0.01, top = 0.01, right = 0.99, bottom = 0.99)
        val expanded = ScanRegionMapper.expand(edgeToEdge)
        assertTrue(expanded.left >= 0.0)
        assertTrue(expanded.top >= 0.0)
        assertTrue(expanded.right <= 1.0)
        assertTrue(expanded.bottom <= 1.0)
    }

    /**
     * The 1.0.3 P3 measurement: what the automatic pass actually reads, versus the drawn guide.
     *
     * The scan guide is `fillMaxWidth().padding(Space.l).aspectRatio(0.8f)`, which on an ordinary
     * 1080x2400 phone lands at roughly the fractions below — already near full width, because the
     * only horizontal inset is one padding step.
     *
     * Expanding it by [ScanRegionMapper.SAFETY_MARGIN] then **saturates horizontally**: the margin
     * is 12% of the guide's own width, which is far more than the padding, so both sides clamp to
     * the frame edge. The region the automatic pass reads therefore spans the **entire width of the
     * photograph** — every column of package beside the table included.
     *
     * That is not a coordinate defect and no mapping is wrong here; it is the documented margin
     * behaving as specified on a guide that is already nearly full-width. It is recorded as a test
     * because it is the measured explanation for the device observation that wide framing declines
     * while close framing succeeds, and because anyone changing the guide's aspect ratio or padding
     * needs to see this relationship rather than rediscover it.
     */
    @Test
    fun `the expanded scan guide spans the full frame width on a typical phone`() {
        // Guide fractions for a 1080x2400 preview with one padding step each side.
        val guide = NormalizedRegion(left = 0.061, top = 0.253, right = 0.939, bottom = 0.747)
        val expanded = ScanRegionMapper.expand(guide)

        assertEquals("the horizontal margin clamps to the frame edge", 0.0, expanded.left, 1e-9)
        assertEquals("the horizontal margin clamps to the frame edge", 1.0, expanded.right, 1e-9)

        // Vertically there is room, so the margin applies without clamping and the region stays
        // short of the full frame. The asymmetry is the point: horizontal context cannot be
        // excluded by aiming, only by moving the phone closer.
        assertTrue("the vertical margin does not clamp", expanded.top > 0.0)
        assertTrue("the vertical margin does not clamp", expanded.bottom < 1.0)

        val guideArea = (guide.right - guide.left) * (guide.bottom - guide.top)
        val readArea = expanded.width * expanded.height
        assertTrue(
            "the region read is materially larger than the guide drawn (was ${readArea / guideArea})",
            readArea > guideArea * 1.3,
        )
    }
}
