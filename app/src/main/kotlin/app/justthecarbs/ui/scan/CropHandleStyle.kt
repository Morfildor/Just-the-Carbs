package app.justthecarbs.ui.scan

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * How big the crop rectangle's drawing is, in density-independent units.
 *
 * These were raw pixels, tuned on the 2.625x reference emulator; on a 3.5x phone the same pixels
 * drew handles a quarter smaller while the touch target around them ([CROP_HANDLE_TOUCH_DP]) was
 * already in dp. Each value here is the old pixel figure divided by 2.625, so the reference screen
 * looks as it did and every other screen now matches it.
 */
internal object CropHandleStyle {
    private val HANDLE_RADIUS = 7.dp
    private val HANDLE_RING = 1.dp

    /** Long enough to read as a handle, short enough not to imply the edge continues past it. */
    private val ARM_LENGTH = 13.dp
    private val ARM_OUTER_STROKE = 4.dp
    private val ARM_INNER_STROKE = 2.25.dp
    private val SELECTION_OUTER_STROKE = 2.5.dp
    private val SELECTION_INNER_STROKE = 1.dp

    data class Px(
        val handleRadius: Float,
        val handleRing: Float,
        val armLength: Float,
        val armOuterStroke: Float,
        val armInnerStroke: Float,
        val selectionOuterStroke: Float,
        val selectionInnerStroke: Float,
    )

    fun inPx(density: Density): Px = with(density) {
        Px(
            handleRadius = HANDLE_RADIUS.toPx(),
            handleRing = HANDLE_RING.toPx(),
            armLength = ARM_LENGTH.toPx(),
            armOuterStroke = ARM_OUTER_STROKE.toPx(),
            armInnerStroke = ARM_INNER_STROKE.toPx(),
            selectionOuterStroke = SELECTION_OUTER_STROKE.toPx(),
            selectionInnerStroke = SELECTION_INNER_STROKE.toPx(),
        )
    }
}
