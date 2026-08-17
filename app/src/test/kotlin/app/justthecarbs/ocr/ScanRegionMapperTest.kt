package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: scan-region cropping
// Invariant: every refusal falls back to reading the whole image, which is the behaviour that
// shipped before cropping existed. A wrong crop cuts the nutrition table in half; a missing crop
// only costs the surrounding clutter. The asymmetry is why null means "don't crop" everywhere here.
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

    @Test
    fun `a region maps to the same fractions of any capture size`() {
        // The point of normalized coordinates: the overlay is measured on a 1080-wide preview and
        // applied to an 8 MP capture, and neither has to know about the other.
        val small = ScanRegionMapper.toPixels(overlay, 1080, 1920)!!
        val large = ScanRegionMapper.toPixels(overlay, 3264, 2448)!!

        assertEquals(0.08, small.left.toDouble() / 1080, 0.005)
        assertEquals(0.08, large.left.toDouble() / 3264, 0.005)
        assertEquals(0.84, small.width.toDouble() / 1080, 0.005)
        assertEquals(0.84, large.width.toDouble() / 3264, 0.005)
    }

    @Test
    fun `a crop that would keep almost the whole image is refused as pointless`() {
        val nearlyEverything = NormalizedRegion(left = 0.005, top = 0.005, right = 0.995, bottom = 0.995)
        assertNull(
            "a 99% crop costs a second full-size bitmap and saves nothing",
            ScanRegionMapper.toPixels(nearlyEverything, 3264, 2448),
        )
    }

    @Test
    fun `a crop too small to recognise anything in is refused`() {
        val sliver = NormalizedRegion(left = 0.50, top = 0.50, right = 0.505, bottom = 0.505)
        assertNull(ScanRegionMapper.toPixels(sliver, 1080, 1920))
    }

    @Test
    fun `a degenerate image size is refused rather than producing a crop`() {
        assertNull(ScanRegionMapper.toPixels(overlay, 0, 1920))
        assertNull(ScanRegionMapper.toPixels(overlay, 1080, 0))
    }

    @Test
    fun `the crop always lies inside the image`() {
        val region = ScanRegionMapper.expand(NormalizedRegion(0.0, 0.0, 1.0, 0.5))
        val pixels = ScanRegionMapper.toPixels(region, 1080, 1920)
        assertNotNull(pixels)
        assertTrue(pixels!!.left >= 0)
        assertTrue(pixels.top >= 0)
        assertTrue("right edge escaped the bitmap", pixels.left + pixels.width <= 1080)
        assertTrue("bottom edge escaped the bitmap", pixels.top + pixels.height <= 1920)
    }

    @Test
    fun `the default overlay keeps a usable share of an 8 MP capture`() {
        // Sanity on the real numbers: cropping must leave enough pixels for small print to survive.
        // 0.84 x 0.52 of 3264x2448 is ~2740x1270 — more resolution than the entire capture had
        // before this pass, on a fraction of the scene.
        val pixels = ScanRegionMapper.toPixels(ScanRegionMapper.expand(overlay), 3264, 2448)!!
        assertTrue("crop width ${pixels.width}", pixels.width > 2000)
        assertTrue("crop height ${pixels.height}", pixels.height > 1000)
    }
}
