package app.justthecarbs.ui.scan

import androidx.compose.ui.geometry.Offset
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ocr.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop rectangle's gesture transitions, tested without a composition.
 *
 * These cover the defect that a Compose UI test would struggle to see and that reading the code
 * makes easy to argue about either way: `detectDragGestures` delivers **incremental** deltas, so a
 * handler that recomputes from a rectangle captured when the gesture began throws away every delta
 * but the last. The failure is not a crash or a wrong-looking frame — it is a drag that moves a
 * tenth as far as the thumb did.
 */
class CropGestureStateTest {

    /** A 1000x1000 image displayed with no letterboxing, so view pixels and image pixels align. */
    private val displayed = ViewRect(0f, 0f, 1000f, 1000f)

    private fun region(left: Float, top: Float, right: Float, bottom: Float) =
        CropSelectionGeometry.toNormalizedRegion(ViewRect(left, top, right, bottom), displayed)!!

    private fun CropGestureState.viewRect() = CropSelectionGeometry.toViewRect(selection, displayed)

    /**
     * Ten 10 px events must move the rectangle 100 px, not 10.
     *
     * This is the accumulation defect stated as arithmetic. Against a handler that reads a captured
     * selection, every event computes `originalRect + 10`, so the rectangle ends where a single
     * event would have put it and the drag visibly lags the thumb.
     */
    @Test
    fun `consecutive drag deltas accumulate when moving the body`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))
        // Centre of the rectangle: far from every corner, so this is a reposition.
        state.onDragStart(Offset(400f, 400f), displayed, handleRadius = 40f)

        repeat(10) { state.onDrag(Offset(10f, 5f), displayed) }
        state.onDragFinished()

        val rect = state.viewRect()
        assertEquals("x should have accumulated 10 x 10px", 300f, rect.left, 0.5f)
        assertEquals("y should have accumulated 10 x 5px", 250f, rect.top, 0.5f)
        assertEquals(700f, rect.right, 0.5f)
        assertEquals(650f, rect.bottom, 0.5f)
    }

    /** The same accumulation guarantee for every corner, since each has its own clamping branch. */
    @Test
    fun `consecutive drag deltas accumulate when resizing each corner`() {
        data class Case(val name: String, val grab: Offset, val expect: (ViewRect) -> Unit)

        val cases = listOf(
            Case("top-left", Offset(200f, 200f)) { r ->
                assertEquals("top-left x", 250f, r.left, 0.5f)
                assertEquals("top-left y", 250f, r.top, 0.5f)
            },
            Case("top-right", Offset(600f, 200f)) { r ->
                assertEquals("top-right x", 650f, r.right, 0.5f)
                assertEquals("top-right y", 250f, r.top, 0.5f)
            },
            Case("bottom-left", Offset(200f, 600f)) { r ->
                assertEquals("bottom-left x", 250f, r.left, 0.5f)
                assertEquals("bottom-left y", 650f, r.bottom, 0.5f)
            },
            Case("bottom-right", Offset(600f, 600f)) { r ->
                assertEquals("bottom-right x", 650f, r.right, 0.5f)
                assertEquals("bottom-right y", 650f, r.bottom, 0.5f)
            },
        )

        cases.forEach { case ->
            val state = CropGestureState(region(200f, 200f, 600f, 600f))
            state.onDragStart(case.grab, displayed, handleRadius = 40f)
            repeat(5) { state.onDrag(Offset(10f, 10f), displayed) }
            state.onDragFinished()
            case.expect(state.viewRect())
        }
    }

    /**
     * A second gesture must start from where the first one left the rectangle.
     *
     * Without per-gesture current state the second drag restarts from the original rectangle, so the
     * first drag's work is silently discarded the moment the user lifts and drags again — which is
     * the ordinary way anyone adjusts a crop.
     */
    @Test
    fun `a second gesture begins from the rectangle the first one produced`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))

        state.onDragStart(Offset(400f, 400f), displayed, handleRadius = 40f)
        repeat(5) { state.onDrag(Offset(20f, 0f), displayed) }
        state.onDragFinished()
        val afterFirst = state.viewRect()
        assertEquals(300f, afterFirst.left, 0.5f)

        state.onDragStart(Offset(500f, 400f), displayed, handleRadius = 40f)
        repeat(5) { state.onDrag(Offset(20f, 0f), displayed) }
        state.onDragFinished()

        assertEquals("the second gesture restarted from the original rectangle", 400f, state.viewRect().left, 0.5f)
    }

    /**
     * A gesture abandoned without `onDragEnd` must not leave its handle grabbed.
     *
     * This is the state the removed process-global `var` was able to persist — a reachable state,
     * never observed changing a real gesture, which is why this is a property test rather than a
     * regression test for a reproduced defect. It is reproduced here by
     * simply never calling [CropGestureState.onDragFinished] — exactly what happens when the
     * composable leaves composition mid-drag — and then starting a fresh gesture in the body of the
     * rectangle. If the corner grab survived, that gesture would resize instead of translate.
     */
    @Test
    fun `an interrupted drag does not leave a corner grabbed for the next gesture`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))

        // Grab a corner and abandon the gesture without ever ending it.
        state.onDragStart(Offset(200f, 200f), displayed, handleRadius = 40f)
        state.onDrag(Offset(10f, 10f), displayed)

        // A new gesture, starting in the middle: unambiguously a reposition.
        state.onDragStart(Offset(400f, 400f), displayed, handleRadius = 40f)
        val before = state.viewRect()
        state.onDrag(Offset(30f, 30f), displayed)
        val after = state.viewRect()

        assertEquals("width changed, so the drag resized instead of moving", before.width, after.width, 0.5f)
        assertEquals("height changed, so the drag resized instead of moving", before.height, after.height, 0.5f)
        assertEquals(before.left + 30f, after.left, 0.5f)
    }

    /**
     * A fresh capture must not inherit the previous screen's rectangle.
     *
     * Per-instance state makes this structural — a new [CropGestureState] cannot see an older one —
     * so this asserts the property rather than the mechanism, and would catch a future change that
     * reintroduced shared state behind the same API.
     */
    @Test
    fun `a new instance starts from its own initial selection`() {
        val first = CropGestureState(region(200f, 200f, 600f, 600f))
        first.onDragStart(Offset(400f, 400f), displayed, handleRadius = 40f)
        repeat(5) { first.onDrag(Offset(40f, 40f), displayed) }
        // Deliberately not finished: the interrupted-navigation case.

        val second = CropGestureState(region(100f, 100f, 500f, 500f))

        val rect = second.viewRect()
        assertEquals(100f, rect.left, 0.5f)
        assertEquals(100f, rect.top, 0.5f)
        assertNotEquals("the new screen inherited the old rectangle", first.selection, second.selection)
    }

    /** Bounds and minimum-size rules are unchanged by the refactor — pinned so they stay that way. */
    @Test
    fun `dragging the body cannot push the rectangle off the image`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))
        state.onDragStart(Offset(400f, 400f), displayed, handleRadius = 40f)
        repeat(20) { state.onDrag(Offset(100f, 100f), displayed) }
        state.onDragFinished()

        val rect = state.viewRect()
        assertTrue("right edge left the image", rect.right <= displayed.right + 0.5f)
        assertTrue("bottom edge left the image", rect.bottom <= displayed.bottom + 0.5f)
        assertEquals("the rectangle must keep its size while translating", 400f, rect.width, 0.5f)
    }

    @Test
    fun `resizing cannot collapse the rectangle below the minimum side`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))
        state.onDragStart(Offset(200f, 200f), displayed, handleRadius = 40f)
        repeat(20) { state.onDrag(Offset(50f, 50f), displayed) }
        state.onDragFinished()

        val rect = state.viewRect()
        assertTrue("width fell below the minimum", rect.width >= CROP_MIN_SIDE_PX - 0.5f)
        assertTrue("height fell below the minimum", rect.height >= CROP_MIN_SIDE_PX - 0.5f)
    }

    /**
     * An external selection change is adopted between gestures but ignored during one.
     *
     * Both halves matter: without the first a caller-driven reset would never reach the gesture;
     * without the second a recomposition landing mid-drag would jump the rectangle under the thumb.
     */
    @Test
    fun `an external selection change is adopted only between gestures`() {
        val state = CropGestureState(region(200f, 200f, 600f, 600f))

        state.syncFromCaller(region(0f, 0f, 300f, 300f))
        assertEquals(0f, state.viewRect().left, 0.5f)

        state.onDragStart(Offset(150f, 150f), displayed, handleRadius = 40f)
        state.onDrag(Offset(10f, 10f), displayed)
        val duringDrag = state.viewRect()
        state.syncFromCaller(region(500f, 500f, 900f, 900f))

        assertEquals(
            "a mid-drag external change moved the rectangle under the user's thumb",
            duringDrag.left,
            state.viewRect().left,
            0.5f,
        )
    }
}
