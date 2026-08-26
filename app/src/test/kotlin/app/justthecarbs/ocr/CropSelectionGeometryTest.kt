package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping the on-screen selection rectangle onto the captured bitmap.
 *
 * ## Why this is the highest-risk arithmetic in the feature
 *
 * The crop box is drawn in view pixels over an image displayed with `ContentScale.Fit`, which
 * letterboxes: the displayed image rarely fills the view, and the offset differs per device and
 * orientation. A box that looks perfectly placed on screen but maps a few percent off in the bitmap
 * would crop the basis header away — which is precisely the failure that cost both canaries when the
 * scan overlay was used as a pre-recognition crop. That failure was invisible on screen, and it is
 * invisible in an emulator screenshot too. It is only catchable here.
 *
 * The mapping therefore takes the **displayed image rectangle**, not the view size, and every case
 * below fixes an independently checkable expectation rather than asserting self-consistency.
 */
class CropSelectionGeometryTest {

    // ------------------------------------------------------------------ letterboxing

    @Test
    fun `a portrait image in a wider view is pillarboxed and the offset is removed`() {
        // 900x1600 image in a 1080x1920 view. Fit scales by min(1080/900, 1920/1600) = 1.2,
        // giving a 1080x1920 displayed image with no bars at all.
        val displayed = CropSelectionGeometry.displayedImageBounds(
            imageWidth = 900,
            imageHeight = 1600,
            viewWidth = 1080f,
            viewHeight = 1920f,
        )

        assertEquals(0f, displayed.left, 0.01f)
        assertEquals(0f, displayed.top, 0.01f)
        assertEquals(1080f, displayed.width, 0.01f)
        assertEquals(1920f, displayed.height, 0.01f)
    }

    @Test
    fun `a 4 by 3 image in a tall view gets horizontal bars above and below`() {
        // 3264x2448 (4:3) in a 1080x1920 view. Fit scale = min(1080/3264, 1920/2448) = 0.3309.
        // Displayed height = 2448 * 0.3309 = 810, so bars of (1920-810)/2 = 555 top and bottom.
        val displayed = CropSelectionGeometry.displayedImageBounds(
            imageWidth = 3264,
            imageHeight = 2448,
            viewWidth = 1080f,
            viewHeight = 1920f,
        )

        assertEquals(0f, displayed.left, 0.5f)
        assertEquals(555f, displayed.top, 0.5f)
        assertEquals(1080f, displayed.width, 0.5f)
        assertEquals(810f, displayed.height, 0.5f)
    }

    @Test
    fun `a landscape image in a portrait view maps a centred box back to the image centre`() {
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        // The middle half of the DISPLAYED image, expressed in view coordinates.
        val selection = ViewRect(
            left = displayed.left + displayed.width * 0.25f,
            top = displayed.top + displayed.height * 0.25f,
            right = displayed.left + displayed.width * 0.75f,
            bottom = displayed.top + displayed.height * 0.75f,
        )

        val region = CropSelectionGeometry.toNormalizedRegion(selection, displayed)!!

        assertEquals(0.25, region.left, 0.001)
        assertEquals(0.25, region.top, 0.001)
        assertEquals(0.75, region.right, 0.001)
        assertEquals(0.75, region.bottom, 0.001)
    }

    @Test
    fun `a box placed over a letterbox bar is clamped to the image, never negative`() {
        // The user can drag the handle into the black bar. That must clamp to the image edge rather
        // than produce a negative fraction, which NormalizedRegion would reject at construction.
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val selection = ViewRect(left = 0f, top = 0f, right = 1080f, bottom = 1920f)

        val region = CropSelectionGeometry.toNormalizedRegion(selection, displayed)!!

        assertEquals(0.0, region.left, 0.001)
        assertEquals(0.0, region.top, 0.001)
        assertEquals(1.0, region.right, 0.001)
        assertEquals(1.0, region.bottom, 0.001)
    }

    // ------------------------------------------------------------------ orientation

    @Test
    fun `the same fractional box maps identically whatever the view aspect ratio`() {
        // The mapping must depend only on the displayed image rectangle. If a device's screen shape
        // could change the resulting fractions, the crop would be device-dependent — the exact class
        // of bug the ViewPort binding exists to prevent elsewhere.
        val shapes = listOf(1080f to 1920f, 1440f to 3120f, 1600f to 2560f, 2000f to 1200f)

        val regions = shapes.map { (w, h) ->
            val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, w, h)
            val selection = ViewRect(
                left = displayed.left + displayed.width * 0.2f,
                top = displayed.top + displayed.height * 0.3f,
                right = displayed.left + displayed.width * 0.8f,
                bottom = displayed.top + displayed.height * 0.6f,
            )
            CropSelectionGeometry.toNormalizedRegion(selection, displayed)!!
        }

        regions.forEach { region ->
            assertEquals(0.2, region.left, 0.002)
            assertEquals(0.3, region.top, 0.002)
            assertEquals(0.8, region.right, 0.002)
            assertEquals(0.6, region.bottom, 0.002)
        }
    }

    @Test
    fun `an EXIF-rotated capture is mapped against the upright bitmap it is displayed from`() {
        // The image shown to the user is the already-rotated bitmap, so the mapping needs no rotation
        // arithmetic of its own — but only if the dimensions handed in are the upright ones. Pinning
        // it here because passing the pre-rotation dimensions would look plausible and be wrong by a
        // 90-degree transpose.
        val upright = CropSelectionGeometry.displayedImageBounds(2448, 3264, 1080f, 1920f)

        // 2448x3264 in 1080x1920: scale = min(0.441, 0.588) = 0.441, displayed 1080x1440,
        // bars of (1920-1440)/2 = 240.
        assertEquals(240f, upright.top, 0.5f)
        assertEquals(1440f, upright.height, 0.5f)
    }

    // ------------------------------------------------------------------ degenerate selections

    @Test
    fun `a zero-width selection is refused rather than constructing an invalid region`() {
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val selection = ViewRect(left = 500f, top = 700f, right = 500f, bottom = 900f)

        assertNull(CropSelectionGeometry.toNormalizedRegion(selection, displayed))
    }

    @Test
    fun `an inverted selection is refused`() {
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val selection = ViewRect(left = 800f, top = 900f, right = 400f, bottom = 700f)

        assertNull(CropSelectionGeometry.toNormalizedRegion(selection, displayed))
    }

    @Test
    fun `a selection entirely inside a letterbox bar is refused`() {
        // Wholly in the top bar: it encloses no image at all, so there is nothing to read.
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val selection = ViewRect(left = 100f, top = 10f, right = 900f, bottom = 200f)

        assertNull(CropSelectionGeometry.toNormalizedRegion(selection, displayed))
    }

    @Test
    fun `a selection smaller than the minimum useful fraction is refused`() {
        // A stray tap must not become a 3-pixel crop that reads as NotFound and looks like a parser
        // failure. Refusing lets the UI keep the previous selection instead.
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val selection = ViewRect(
            left = displayed.left + 10f,
            top = displayed.top + 10f,
            right = displayed.left + 14f,
            bottom = displayed.top + 14f,
        )

        assertNull(CropSelectionGeometry.toNormalizedRegion(selection, displayed))
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun `a region converted to view coordinates and back is unchanged`() {
        // The UI needs both directions: fractions to draw the initial box, view pixels to record the
        // dragged one. A round-trip drift would slowly move the box every time state was restored.
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 1080f, 1920f)
        val original = NormalizedRegion(0.18, 0.32, 0.86, 0.71)

        val asView = CropSelectionGeometry.toViewRect(original, displayed)
        val roundTripped = CropSelectionGeometry.toNormalizedRegion(asView, displayed)!!

        assertEquals(original.left, roundTripped.left, 0.001)
        assertEquals(original.top, roundTripped.top, 0.001)
        assertEquals(original.right, roundTripped.right, 0.001)
        assertEquals(original.bottom, roundTripped.bottom, 0.001)
    }

    @Test
    fun `a degenerate view produces an empty displayed rectangle rather than dividing by zero`() {
        val displayed = CropSelectionGeometry.displayedImageBounds(3264, 2448, 0f, 0f)

        assertTrue(displayed.width <= 0f || displayed.height <= 0f)
        assertNull(
            CropSelectionGeometry.toNormalizedRegion(ViewRect(0f, 0f, 10f, 10f), displayed),
        )
    }
}
