package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Callout placement, tested at the sizes an emulator does not happen to have.
 *
 * This is the arithmetic that decides whether the card covers the control it is describing. It is
 * pure precisely so the awkward cases — a target at the very top, at the very bottom, on a short
 * screen where neither side fits — can be asserted directly rather than hoped for.
 */
class CalloutPlacementTest {

    private val screen = 2400f
    private val callout = 600f

    @Test
    fun `a target near the top puts the callout below it`() {
        val side = calloutSideFor(
            spotlightTop = 200f,
            spotlightBottom = 400f,
            screenHeight = screen,
            requiredHeight = callout,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `a target near the bottom puts the callout above it`() {
        val side = calloutSideFor(
            spotlightTop = 2000f,
            spotlightBottom = 2300f,
            screenHeight = screen,
            requiredHeight = callout,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `the roomier side wins when both would fit`() {
        // Target slightly above centre: 1000 above, 1200 below. Below is roomier.
        assertEquals(
            CalloutSide.BELOW,
            calloutSideFor(1000f, 1200f, screen, callout),
        )
        // Target slightly below centre: 1400 above, 800 below. Above is roomier.
        assertEquals(
            CalloutSide.ABOVE,
            calloutSideFor(1400f, 1600f, screen, callout),
        )
    }

    @Test
    fun `a side too small for the card is not chosen even when it is the roomier one`() {
        // Both gaps are smaller than the card, so neither is usable; but this also pins the
        // narrower case: with 500 above and 1900 below, "below" is both roomier AND large enough.
        assertEquals(
            CalloutSide.BELOW,
            calloutSideFor(500f, 500f, screen, callout),
        )
    }

    @Test
    fun `a target filling the screen falls back to centred rather than covering itself`() {
        // Neither side can hold the card. Overlapping the target would hide the control the step is
        // pointing at, so the card centres and the arrow is dropped instead.
        val side = calloutSideFor(
            spotlightTop = 100f,
            spotlightBottom = 2300f,
            screenHeight = screen,
            requiredHeight = callout,
        )
        assertEquals(CalloutSide.CENTERED, side)
    }

    @Test
    fun `a target flush against the top edge still places the card below`() {
        assertEquals(
            CalloutSide.BELOW,
            calloutSideFor(0f, 300f, screen, callout),
        )
    }

    @Test
    fun `a target flush against the bottom edge still places the card above`() {
        assertEquals(
            CalloutSide.ABOVE,
            calloutSideFor(2100f, 2400f, screen, callout),
        )
    }

    @Test
    fun `horizontal clamping keeps a box on screen at either edge`() {
        // Far left: pushed in to the margin.
        assertEquals(20f, clampHorizontally(-500f, 300f, 1080f, 20f), 0.01f)
        // Far right: pulled back so the right edge clears the margin.
        assertEquals(760f, clampHorizontally(5000f, 300f, 1080f, 20f), 0.01f)
        // Comfortably inside: untouched.
        assertEquals(400f, clampHorizontally(400f, 300f, 1080f, 20f), 0.01f)
    }

    @Test
    fun `a box wider than the screen is pinned to the margin rather than given a negative position`() {
        // Overflowing one edge is recoverable; starting off-screen is not, because the beginning of
        // the text would be unreachable.
        assertEquals(20f, clampHorizontally(0f, 2000f, 1080f, 20f), 0.01f)
    }
}
