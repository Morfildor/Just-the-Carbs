package app.justthecarbs.ui.scan

import androidx.compose.ui.geometry.Offset
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ocr.ViewRect

/** Which part of the rectangle a drag grabbed. */
internal enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY }

/**
 * The crop rectangle's gesture state, owned by the gesture rather than by composition.
 *
 * ## Why this exists as a class instead of living in the pointer lambda
 *
 * `detectDragGestures` suspends inside one `pointerInput` block for the whole gesture, and that
 * block captures the composition values in scope when it started. Reading the *selection* from that
 * capture makes every drag event compute `selectionAsItWasWhenTheGestureBegan + thisEventsDelta` —
 * and because `dragAmount` is an increment since the previous event, not a total, the increments do
 * not add up. A 100 px drag delivered as ten 10 px events moves the rectangle 10 px, and the last
 * event silently wins over the nine before it.
 *
 * Recomposition does not rescue this. The lambda's captured `selection` is only refreshed when the
 * `pointerInput` block restarts, which happens on a key change — and the key is the displayed image
 * bounds, which by definition do not change while the user is dragging inside them.
 *
 * So the gesture keeps its own current rectangle and mutates it event by event. That is what makes
 * accumulation correct by construction rather than dependent on a recomposition landing between two
 * pointer events.
 *
 * ## Kept pure
 *
 * No Compose state, no snapshot reads, no Android types. The whole point is that the transition
 * sequence — grab, accumulate, release, grab again — is testable without a device, an emulator or a
 * composition, which is where the accumulation defect actually lives.
 */
internal class CropGestureState(initial: NormalizedRegion) {

    /** The rectangle as it stands right now, including any in-progress drag. */
    var selection: NormalizedRegion = initial
        private set

    private var activeHandle: CropHandle? = null

    /**
     * Adopt a rectangle that changed outside this gesture.
     *
     * Called when the caller's selection moves for a reason the gesture did not cause — a new
     * capture, a restored state, a fresh proposal. Ignored while a drag is in flight so that a
     * recomposition mid-gesture cannot yank the rectangle out from under the user's thumb.
     */
    fun syncFromCaller(region: NormalizedRegion) {
        if (activeHandle == null) selection = region
    }

    /** Begin a drag at [point]; decides whether this gesture resizes a corner or moves the body. */
    fun onDragStart(point: Offset, displayed: ViewRect, handleRadius: Float) {
        activeHandle = nearestHandle(point, CropSelectionGeometry.toViewRect(selection, displayed), handleRadius)
    }

    /**
     * Apply one incremental drag delta, returning the updated rectangle, or null if the drag would
     * leave the rectangle unrepresentable (which leaves the current one untouched).
     *
     * Reads and writes [selection], so consecutive events compose: the second event operates on the
     * result of the first, which is precisely what the captured-composition-value form could not do.
     */
    fun onDrag(dragAmount: Offset, displayed: ViewRect): NormalizedRegion? {
        val current = CropSelectionGeometry.toViewRect(selection, displayed)
        val moved = applyDrag(current, activeHandle, dragAmount, displayed)
        val next = CropSelectionGeometry.toNormalizedRegion(moved, displayed) ?: return null
        selection = next
        return next
    }

    /**
     * End the gesture, however it ended.
     *
     * Clearing the handle here is what stops the next gesture inheriting this one's grab. The
     * previous implementation held the handle in a process-global `var`, shared by every crop screen
     * in the process; `detectDragGestures` runs `onDragEnd`/`onDragCancel` only while its pointer
     * input is alive, so a drag interrupted by Retake could leave that global set.
     *
     * **What follows from that is reachability, not an observed defect.** Whether a stale global
     * handle ever actually changed a subsequent gesture was never reproduced on a device, and it is
     * not claimed here. Per-instance state is state isolation: a new screen starts from a known
     * handle regardless, which is a stronger guarantee than reasoning about when the global gets
     * cleared. The proven crop defect is the accumulation one described in this class's header.
     */
    fun onDragFinished() {
        activeHandle = null
    }
}

private fun nearestHandle(point: Offset, rect: ViewRect, radius: Float): CropHandle {
    val corners = mapOf(
        CropHandle.TOP_LEFT to Offset(rect.left, rect.top),
        CropHandle.TOP_RIGHT to Offset(rect.right, rect.top),
        CropHandle.BOTTOM_LEFT to Offset(rect.left, rect.bottom),
        CropHandle.BOTTOM_RIGHT to Offset(rect.right, rect.bottom),
    )
    val closest = corners.minByOrNull { (_, corner) -> (corner - point).getDistance() }
    // Falling back to BODY rather than the nearest corner matters: a drag starting in the middle of
    // a large selection is a reposition, and snapping it to a distant corner would resize instead.
    return closest?.takeIf { (it.value - point).getDistance() <= radius }?.key ?: CropHandle.BODY
}

/** Applies a drag to the rectangle, keeping it inside the displayed image and above a minimum size. */
private fun applyDrag(
    rect: ViewRect,
    handle: CropHandle?,
    drag: Offset,
    displayed: ViewRect,
): ViewRect {
    val minSide = CROP_MIN_SIDE_PX
    return when (handle) {
        CropHandle.TOP_LEFT -> rect.copyRect(
            left = (rect.left + drag.x).coerceIn(displayed.left, rect.right - minSide),
            top = (rect.top + drag.y).coerceIn(displayed.top, rect.bottom - minSide),
        )
        CropHandle.TOP_RIGHT -> rect.copyRect(
            right = (rect.right + drag.x).coerceIn(rect.left + minSide, displayed.right),
            top = (rect.top + drag.y).coerceIn(displayed.top, rect.bottom - minSide),
        )
        CropHandle.BOTTOM_LEFT -> rect.copyRect(
            left = (rect.left + drag.x).coerceIn(displayed.left, rect.right - minSide),
            bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSide, displayed.bottom),
        )
        CropHandle.BOTTOM_RIGHT -> rect.copyRect(
            right = (rect.right + drag.x).coerceIn(rect.left + minSide, displayed.right),
            bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSide, displayed.bottom),
        )
        // Translate, clamped so the whole rectangle stays over the image rather than sliding half
        // of it into a letterbox bar where it would select nothing.
        CropHandle.BODY, null -> {
            val dx = drag.x.coerceIn(displayed.left - rect.left, displayed.right - rect.right)
            val dy = drag.y.coerceIn(displayed.top - rect.top, displayed.bottom - rect.bottom)
            ViewRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
        }
    }
}

private fun ViewRect.copyRect(
    left: Float = this.left,
    top: Float = this.top,
    right: Float = this.right,
    bottom: Float = this.bottom,
) = ViewRect(left, top, right, bottom)

/** Generous enough for a thumb, small enough that two corners are separately grabbable. */
internal const val CROP_HANDLE_TOUCH_DP = 40
internal const val CROP_MIN_SIDE_PX = 80f
