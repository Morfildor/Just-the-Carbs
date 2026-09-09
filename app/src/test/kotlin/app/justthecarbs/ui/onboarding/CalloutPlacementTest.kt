package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Callout placement, tested at the sizes an emulator does not happen to have.
 *
 * Bottom is the deterministic default. The only decision this function makes is whether bottom
 * placement, given the card's REAL measured height (not an estimate), would overlap the spotlight
 * or fail to fit in the usable safe area below it — in which case it falls back to top. There is no
 * "roomier side" comparison: a side is chosen once by this one rule, not by comparing two candidate
 * zones' available space.
 */
class CalloutPlacementTest {

    private val screen = 2400f
    private val card = 600f

    @Test
    fun `an ordinary target near the top uses the bottom default`() {
        val side = calloutSideFor(
            spotlightTop = 200f,
            spotlightBottom = 400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `an ordinary target near vertical centre still uses the bottom default`() {
        // The old "roomier side" rule would have picked ABOVE here (1400 above vs 1000 below).
        // The new rule has no such comparison: bottom fits (1000 >= 600), so bottom is used.
        val side = calloutSideFor(
            spotlightTop = 1400f,
            spotlightBottom = 1400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `a target low enough to make the bottom zone too small falls back to top`() {
        // Only 300px below the spotlight, card needs 600 - bottom would overlap. Top has 2000px.
        val side = calloutSideFor(
            spotlightTop = 2000f,
            spotlightBottom = 2100f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `a target flush against the bottom edge falls back to top`() {
        val side = calloutSideFor(
            spotlightTop = 2100f,
            spotlightBottom = 2400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `a target flush against the top edge still uses the bottom default`() {
        val side = calloutSideFor(
            spotlightTop = 0f,
            spotlightBottom = 300f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `the bottom safe-area inset is subtracted from the available gap`() {
        // 700px between spotlight and screen bottom, card needs 600 - fits with no inset.
        // But a 150px system-bar inset shrinks the usable gap to 550, which no longer fits.
        val withoutInset = calloutSideFor(
            spotlightTop = 1600f,
            spotlightBottom = 1700f,
            screenHeight = screen,
            cardHeight = card,
            safeAreaBottomInset = 0f,
        )
        assertEquals(CalloutSide.BELOW, withoutInset)

        val withInset = calloutSideFor(
            spotlightTop = 1600f,
            spotlightBottom = 1700f,
            screenHeight = screen,
            cardHeight = card,
            safeAreaBottomInset = 150f,
        )
        assertEquals(CalloutSide.ABOVE, withInset)
    }

    @Test
    fun `repeated calls with identical inputs return the identical side`() {
        // Determinism is the whole point of removing the room-comparison: the same step must always
        // render in the same place, never flip between runs on the same geometry.
        val first = calloutSideFor(1000f, 1200f, screen, card)
        val second = calloutSideFor(1000f, 1200f, screen, card)
        val third = calloutSideFor(1000f, 1200f, screen, card)
        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `bottom disqualified but top has room still resolves to top`() {
        // Bottom does not fit (1100px available, card needs 1150); top has 1600px and the card
        // fits there -- the ordinary two-tier fallback, unchanged by the emergency case below.
        val side = calloutSideFor(
            spotlightTop = 1600f,
            spotlightBottom = 1300f,
            screenHeight = screen,
            cardHeight = 1150f,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `a card that fits neither zone returns the emergency CLAMPED case, never a third comparison`() {
        // Bottom disqualified (1100px available, needs 1150) AND top disqualified (also 1100px
        // available, needs 1150) -- both real zones were measured and rejected, which is the one
        // situation CLAMPED exists for. This is not "pick whichever is less bad": the function
        // makes no comparison between the two rejected zones at all, it simply reports that
        // neither held, and leaves placement to the caller (see OnboardingScreen).
        val side = calloutSideFor(
            spotlightTop = 1100f,
            spotlightBottom = 1300f,
            screenHeight = screen,
            cardHeight = 1150f,
        )
        assertEquals(CalloutSide.CLAMPED, side)
    }

    @Test
    fun `CLAMPED is reserved for a genuine double miss, not a close call on one side`() {
        // Bottom exactly fits (1100px available, card needs exactly 1100) -- BELOW wins outright,
        // the ordinary deterministic default, with no need to even consider CLAMPED.
        val side = calloutSideFor(
            spotlightTop = 1300f,
            spotlightBottom = 1300f,
            screenHeight = screen,
            cardHeight = 1100f,
        )
        assertEquals(CalloutSide.BELOW, side)
    }
}
