package app.justthecarbs.ui.scan

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The crop handles are drawn in density-independent sizes, so they look the same on every screen.
 *
 * They used to be raw pixels: 18 px is a comfortable handle on the 2.625x reference emulator and a
 * speck on a 3.5x phone, while the touch target around it was already in dp.
 */
class CropHandleStyleTest {

    @Test
    fun `the handle scales with the screen density`() {
        val onReference = CropHandleStyle.inPx(Density(2.625f))
        val onDenser = CropHandleStyle.inPx(Density(3.5f))

        assertEquals(onReference.handleRadius * 3.5f / 2.625f, onDenser.handleRadius, 0.001f)
        assertEquals(onReference.armLength * 3.5f / 2.625f, onDenser.armLength, 0.001f)
        assertEquals(onReference.selectionOuterStroke * 3.5f / 2.625f, onDenser.selectionOuterStroke, 0.001f)
    }

    /** The reference emulator, where the pixel sizes were tuned, still draws about what it did. */
    @Test
    fun `the reference density keeps the tuned sizes`() {
        val px = CropHandleStyle.inPx(Density(2.625f))

        assertEquals(18f, px.handleRadius, 1.5f)
        assertEquals(34f, px.armLength, 1.5f)
        assertEquals(11f, px.armOuterStroke, 1.5f)
        assertEquals(6f, px.armInnerStroke, 1.5f)
        assertEquals(7f, px.selectionOuterStroke, 1.5f)
        assertEquals(3f, px.selectionInnerStroke, 1.5f)
        assertEquals(3f, px.handleRing, 1.5f)
    }
}
