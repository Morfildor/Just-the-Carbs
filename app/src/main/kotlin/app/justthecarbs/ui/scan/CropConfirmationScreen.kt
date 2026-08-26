package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ocr.ViewRect
import app.justthecarbs.ui.theme.Space

/** Test hooks. The crop rectangle has no text of its own, so it cannot be found any other way. */
const val CROP_SELECTION_TAG = "crop_selection"
const val CROP_READ_TAG = "crop_read"

/**
 * The confirm-the-table step between capture and result.
 *
 * ## Why this interaction exists
 *
 * Automatic table localisation was attempted three times and measured to fail: vertical banding
 * dropped the basis header, connected-component clustering had no threshold that worked across the
 * corpus, and re-recognising an isolated crop manufactured a confident-wrong. Meanwhile the dominant
 * real-device failure is surrounding package text — ingredient lists and marketing copy merging into
 * table rows during reconstruction. A person can separate those in a second, and no algorithm in this
 * repo has managed it.
 *
 * So this asks. Once. With the rectangle already positioned, so the common case is a glance and a tap.
 *
 * ## Deliberately minimal
 *
 * No rotate, no filters, no brightness, no zoom, no aspect-ratio lock. Every one of those would be a
 * photo editor, and the user is standing in a supermarket holding a chocolate bar. The only gesture
 * that changes the outcome is moving the rectangle, so it is the only gesture offered.
 *
 * ## The instruction is load-bearing, not decoration
 *
 * "Include the per 100 g heading" is on screen because a crop that excludes the basis header produces
 * a refusal the user cannot diagnose: the value is right there, they can read it, and the app says it
 * found nothing. The app will not guess the basis — see the safety tests — so the only fix is telling
 * the user what the rectangle must contain, before they draw it.
 */
@Composable
fun CropConfirmationScreen(
    bitmap: Bitmap,
    /** Where the rectangle starts. Only a proposal; the user is free to move it. */
    initialSelection: NormalizedRegion,
    /** True while the table is being re-parsed, which disables the primary action. */
    reading: Boolean,
    onReadTable: (NormalizedRegion) -> Unit,
    onRetake: () -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Held in image fractions, not view pixels: a rotation or a window resize changes the displayed
    // rectangle, and a selection stored in pixels would silently point somewhere else afterwards.
    var selection by remember { mutableStateOf(initialSelection) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(R.string.crop_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.crop_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewSize = it },
        ) {
            val displayed = CropSelectionGeometry.displayedImageBounds(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
            )

            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )

            if (displayed.width > 0f && displayed.height > 0f) {
                CropOverlay(
                    selection = selection,
                    displayed = displayed,
                    onSelectionChange = { selection = it },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (reading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.crop_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Button(
                onClick = { onReadTable(selection) },
                enabled = !reading,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Space.primaryButtonHeight)
                    .testTag(CROP_READ_TAG),
            ) { Text(stringResource(R.string.crop_read)) }
            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
            ) { Text(stringResource(R.string.crop_retake)) }
        }
    }
}

/**
 * The draggable rectangle: a scrim with the selection punched out, plus four corner handles.
 *
 * Corners rather than edges or a free-draw gesture. A nutrition table is an axis-aligned rectangle,
 * so two opposite corners fully determine it, and corner handles are the only affordance that lets
 * one thumb make both a coarse and a fine adjustment without a mode switch.
 */
@Composable
private fun CropOverlay(
    selection: NormalizedRegion,
    displayed: ViewRect,
    onSelectionChange: (NormalizedRegion) -> Unit,
) {
    val rect = CropSelectionGeometry.toViewRect(selection, displayed)
    val handleRadius = with(androidx.compose.ui.platform.LocalDensity.current) { HANDLE_TOUCH_DP.dp.toPx() }
    val selectionLabel = stringResource(R.string.crop_selection)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CROP_SELECTION_TAG)
            .semantics { contentDescription = selectionLabel }
            .pointerInput(displayed) {
                detectDragGestures(
                    onDragStart = { start -> activeHandle = nearestHandle(start, rectOf(selection, displayed), handleRadius) },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                ) { change, dragAmount ->
                    change.consume()
                    val current = rectOf(selection, displayed)
                    val moved = applyDrag(current, activeHandle, dragAmount, displayed)
                    CropSelectionGeometry.toNormalizedRegion(moved, displayed)?.let(onSelectionChange)
                }
            },
    ) {
        // Scrim everything except the selection, so the eye goes straight to what will be read.
        //
        // Deliberately heavy (§10). At 0.55 the excluded ingredient list stayed comfortably readable,
        // so nothing on screen suggested it was being excluded — and the device recording shows users
        // accepting a rectangle covering most of the package because it looked acceptable. Text
        // outside the selection should be visibly *dismissed*, not merely tinted.
        val scrim = Color.Black.copy(alpha = 0.78f)
        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, rect.top.coerceAtLeast(0f)))
        drawRect(
            scrim,
            topLeft = Offset(0f, rect.bottom),
            size = Size(size.width, (size.height - rect.bottom).coerceAtLeast(0f)),
        )
        drawRect(
            scrim,
            topLeft = Offset(0f, rect.top),
            size = Size(rect.left.coerceAtLeast(0f), rect.height),
        )
        drawRect(
            scrim,
            topLeft = Offset(rect.right, rect.top),
            size = Size((size.width - rect.right).coerceAtLeast(0f), rect.height),
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 3f),
        )

        // The handles are the only control that changes the outcome, so they are drawn to look
        // grabbable (§10). Previously they were flat white dots that read as decoration on a
        // rectangle that already looked final — which is why the recording shows the initial
        // rectangle being accepted unchanged. An accent ring plus L-shaped corner brackets say
        // "drag me" without adding a mode, a gesture, or any new control.
        listOf(
            Offset(rect.left, rect.top) to Pair(1, 1),
            Offset(rect.right, rect.top) to Pair(-1, 1),
            Offset(rect.left, rect.bottom) to Pair(1, -1),
            Offset(rect.right, rect.bottom) to Pair(-1, -1),
        ).forEach { (corner, direction) ->
            val (dx, dy) = direction
            val arm = HANDLE_ARM_PX
            listOf(Color.Black.copy(alpha = 0.45f) to 9f, HANDLE_COLOR to 5f).forEach { (color, width) ->
                drawLine(
                    color = color,
                    start = corner,
                    end = Offset(corner.x + arm * dx, corner.y),
                    strokeWidth = width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = corner,
                    end = Offset(corner.x, corner.y + arm * dy),
                    strokeWidth = width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            drawCircle(HANDLE_COLOR, radius = HANDLE_DRAW_PX, center = corner)
            drawCircle(Color.White, radius = HANDLE_DRAW_PX * 0.45f, center = corner)
        }
    }
}

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY }

/**
 * Which handle a drag grabbed.
 *
 * Deliberately module-level mutable state rather than remembered composable state: it is set in
 * `onDragStart` and read in the drag callback of the *same* gesture, so it never outlives one
 * interaction and never participates in recomposition.
 */
private var activeHandle: Handle? = null

private fun rectOf(selection: NormalizedRegion, displayed: ViewRect): ViewRect =
    CropSelectionGeometry.toViewRect(selection, displayed)

private fun nearestHandle(point: Offset, rect: ViewRect, radius: Float): Handle {
    val corners = mapOf(
        Handle.TOP_LEFT to Offset(rect.left, rect.top),
        Handle.TOP_RIGHT to Offset(rect.right, rect.top),
        Handle.BOTTOM_LEFT to Offset(rect.left, rect.bottom),
        Handle.BOTTOM_RIGHT to Offset(rect.right, rect.bottom),
    )
    val closest = corners.minByOrNull { (_, corner) -> (corner - point).getDistance() }
    // Falling back to BODY rather than the nearest corner matters: a drag starting in the middle of
    // a large selection is a reposition, and snapping it to a distant corner would resize instead.
    return closest?.takeIf { (it.value - point).getDistance() <= radius }?.key ?: Handle.BODY
}

/** Applies a drag to the rectangle, keeping it inside the displayed image and above a minimum size. */
private fun applyDrag(
    rect: ViewRect,
    handle: Handle?,
    drag: Offset,
    displayed: ViewRect,
): ViewRect {
    val minSide = MIN_SIDE_PX
    return when (handle) {
        Handle.TOP_LEFT -> rect.copy(
            left = (rect.left + drag.x).coerceIn(displayed.left, rect.right - minSide),
            top = (rect.top + drag.y).coerceIn(displayed.top, rect.bottom - minSide),
        )
        Handle.TOP_RIGHT -> rect.copy(
            right = (rect.right + drag.x).coerceIn(rect.left + minSide, displayed.right),
            top = (rect.top + drag.y).coerceIn(displayed.top, rect.bottom - minSide),
        )
        Handle.BOTTOM_LEFT -> rect.copy(
            left = (rect.left + drag.x).coerceIn(displayed.left, rect.right - minSide),
            bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSide, displayed.bottom),
        )
        Handle.BOTTOM_RIGHT -> rect.copy(
            right = (rect.right + drag.x).coerceIn(rect.left + minSide, displayed.right),
            bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSide, displayed.bottom),
        )
        // Translate, clamped so the whole rectangle stays over the image rather than sliding half
        // of it into a letterbox bar where it would select nothing.
        Handle.BODY, null -> {
            val dx = drag.x.coerceIn(displayed.left - rect.left, displayed.right - rect.right)
            val dy = drag.y.coerceIn(displayed.top - rect.top, displayed.bottom - rect.bottom)
            ViewRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
        }
    }
}

private fun ViewRect.copy(
    left: Float = this.left,
    top: Float = this.top,
    right: Float = this.right,
    bottom: Float = this.bottom,
) = ViewRect(left, top, right, bottom)

/** Generous enough for a thumb, small enough that two corners are separately grabbable. */
private const val HANDLE_TOUCH_DP = 40
private const val HANDLE_DRAW_PX = 18f
private const val MIN_SIDE_PX = 80f

/** Length of each corner bracket arm, in px. Long enough to read as a handle, short enough not to
 * imply the selection edge continues past the rectangle. */
private const val HANDLE_ARM_PX = 34f

/**
 * The handle accent.
 *
 * A warm colour rather than white: the selection outline is white, and a white handle on a white
 * outline is exactly why the handles previously read as part of the frame rather than as controls.
 * Not taken from MaterialTheme because this screen renders over an arbitrary photograph, where a
 * theme surface colour carries no guarantee of contrast.
 */
private val HANDLE_COLOR = Color(0xFFFFC107)
