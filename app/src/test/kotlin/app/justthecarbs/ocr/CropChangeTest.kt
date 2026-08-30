package app.justthecarbs.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding whether a confirmed crop is worth recognising again (1.0.3 P2).
 *
 * ## The waste this removes
 *
 * The automatic post-capture attempt reads `ScanRegionMapper.expand(scanRegion)`, and when it
 * declines, the crop screen opens on **that same rectangle** — it is assigned to `cropSelection`
 * on the line above the automatic call. So a user who looks at the proposed box, decides it is
 * already right and taps *Read table* runs `SelectedTableResolution.resolve` over an identical
 * region: the same Strategy A re-parse of the same retained elements, and the same ~400 ms
 * Strategy B ML Kit pass over the same pixels.
 *
 * Recognition is deterministic over identical input, so the outcome is the one that already
 * declined. The user waits again to be told the same thing.
 *
 * ## Why a tolerance rather than equality
 *
 * The rectangle survives a round trip through drag gestures and float arithmetic, so a user who
 * touches a corner and puts it back does not necessarily produce the bit-identical double they
 * started with. Comparing for equality would call that a change and rerun the pass, which is the
 * defect this exists to remove. The tolerance is a fraction of the frame, not an absolute, because
 * the region is expressed in fractions of the preview.
 *
 * ## What it must never do
 *
 * Skip a pass the user actually asked for. A crop the user genuinely tightened is new information
 * and must be recognised — that is the entire point of the crop screen, and P2 is a latency fix,
 * not a licence to ignore the user.
 */
class CropChangeTest {

    private fun region(l: Double, t: Double, r: Double, b: Double) = NormalizedRegion(l, t, r, b)

    private val original = region(0.10, 0.20, 0.90, 0.80)

    // ---- 1. the case that wastes a recognition -------------------------------------------------

    @Test
    fun `the identical region is not a material change`() {
        assertFalse(CropChange.isMaterial(original, original))
    }

    /**
     * Coordinate noise from a touch-and-return is not a user change.
     *
     * A drag that lands back where it started arrives as a slightly different double; treating that
     * as an edit reintroduces exactly the duplicate pass being removed.
     */
    @Test
    fun `sub-tolerance coordinate noise is not a material change`() {
        assertFalse(CropChange.isMaterial(original, region(0.1001, 0.2001, 0.8999, 0.7999)))
    }

    // ---- 2. the cases that must still be recognised --------------------------------------------

    /** The ordinary reason someone opens the crop screen: tightening around the table. */
    @Test
    fun `tightening the box is a material change`() {
        assertTrue(CropChange.isMaterial(original, region(0.30, 0.35, 0.70, 0.65)))
    }

    @Test
    fun `widening the box is a material change`() {
        assertTrue(CropChange.isMaterial(original, region(0.02, 0.05, 0.98, 0.95)))
    }

    /** Same size, moved — a translation to a different part of the label is new information. */
    @Test
    fun `moving the box without resizing it is a material change`() {
        assertTrue(CropChange.isMaterial(original, region(0.20, 0.30, 1.00, 0.90)))
    }

    /** One edge dragged well past the tolerance still counts, even with three edges unmoved. */
    @Test
    fun `moving a single edge past the tolerance is a material change`() {
        assertTrue(CropChange.isMaterial(original, region(0.10, 0.20, 0.90, 0.55)))
    }

    /**
     * The tolerance boundary, asserted from both sides so it is a real threshold and not an
     * accident of the values the other cases happen to use.
     */
    @Test
    fun `the tolerance has a boundary on both sides`() {
        val justUnder = CropChange.TOLERANCE * 0.5
        val wellOver = CropChange.TOLERANCE * 4.0
        assertFalse(
            CropChange.isMaterial(original, region(0.10 + justUnder, 0.20, 0.90, 0.80)),
        )
        assertTrue(
            CropChange.isMaterial(original, region(0.10 + wellOver, 0.20, 0.90, 0.80)),
        )
    }

    // ---- 3. the safety cases -------------------------------------------------------------------

    /**
     * With no previous region there is nothing to compare against, so the pass must run.
     *
     * This is what makes a fresh capture, or a *Read table* that follows no automatic attempt,
     * behave exactly as it always did. Defaulting the other way would silently skip the first real
     * recognition of a capture.
     */
    @Test
    fun `a null previous region is always material`() {
        assertTrue(CropChange.isMaterial(null, original))
    }

    /**
     * The tolerance must be small enough that it cannot mask a deliberate adjustment.
     *
     * Stated as a property rather than trusted from the constant's value: 1% of the frame is a few
     * pixels on any phone, far below what a finger can place, so no intentional drag lands inside
     * it.
     */
    @Test
    fun `the tolerance is far below any deliberate drag`() {
        assertTrue("tolerance must be at most 1% of the frame", CropChange.TOLERANCE <= 0.01)
        assertTrue("tolerance must be positive to absorb float noise", CropChange.TOLERANCE > 0.0)
    }
}
