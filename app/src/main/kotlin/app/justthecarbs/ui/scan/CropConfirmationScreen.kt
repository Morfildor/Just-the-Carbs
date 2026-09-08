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
    /**
     * True when the app already tried this rectangle automatically and could not read it safely
     * (1.0.3 P4).
     *
     * Changes only the wording. Without it this screen is identical whether it is the first thing
     * after a capture or the fallback from an automatic attempt the user just waited through — and
     * in the second case "Tighten the box around the table" reads as though nothing had happened, or
     * as though the scan had restarted. Saying that an attempt was made, and that this is the way to
     * help it, is the difference between a failure and a hand-off.
     */
    afterAutomaticAttempt: Boolean = false,
    onReadTable: (NormalizedRegion) -> Unit,
    onRetake: () -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Held in image fractions, not view pixels: a rotation or a window resize changes the displayed
    // rectangle, and a selection stored in pixels would silently point somewhere else afterwards.
    var selection by remember(bitmap, initialSelection) { mutableStateOf(initialSelection) }

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
            // Three states, and the ordering matters (1.0.3 P4).
            //
            // `reading` is checked FIRST because while a pass is running the user is not being asked
            // for anything — telling them to drag the corners while the app is already reading the
            // table asks for work that is about to be thrown away, and on the automatic path that
            // instruction would appear before they had done anything at all. It becomes a statement
            // of what is happening instead.
            //
            // Then the post-attempt wording, then the ordinary first-time wording.
            val titleRes = when {
                reading -> R.string.crop_reading
                afterAutomaticAttempt -> R.string.crop_title_after_attempt
                else -> R.string.crop_title
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    if (afterAutomaticAttempt) R.string.crop_body_after_attempt else R.string.crop_body,
                ),
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
    val handleRadius = with(androidx.compose.ui.platform.LocalDensity.current) { CROP_HANDLE_TOUCH_DP.dp.toPx() }
    val selectionLabel = stringResource(R.string.crop_selection)

    /**
     * The gesture's own current rectangle and grabbed handle.
     *
     * Per-instance, replacing a process-global `var` that every scan in the process shared. That is
     * state isolation rather than a fixed defect: a drag interrupted before `onDragEnd`/`onDragCancel`
     * could run — Retake mid-drag, the app backgrounding — could leave the global handle set, but
     * whether that ever changed a subsequent gesture was never reproduced and is not claimed. The
     * proven defect is the accumulation one described below.
     *
     * It also holds the *rectangle*, which is what fixes accumulation. `detectDragGestures` suspends
     * inside one `pointerInput` block for the whole gesture, so the lambda below reads whatever
     * `selection` was captured when that block last started — and the block's key is `displayed`,
     * which cannot change while the user drags inside the image. Since `dragAmount` is an increment
     * since the previous event rather than a total, recomputing from that captured value made every
     * event overwrite the one before it: a 100 px drag delivered as ten 10 px events moved 10 px.
     * Keeping the current rectangle in the gesture makes each event compose onto the last.
     *
     * Not `mutableStateOf`: nothing in composition reads it, so snapshot state would add a
     * recomposition per pointer event for no observable benefit.
     */
    val gesture = remember { CropGestureState(selection) }
    // Adopt a rectangle that changed for a reason the gesture did not cause (a new capture, a
    // restored proposal). Ignored mid-drag by the holder itself, so this cannot jump the rectangle
    // under the user's thumb.
    gesture.syncFromCaller(selection)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CROP_SELECTION_TAG)
            .semantics { contentDescription = selectionLabel }
            .pointerInput(displayed) {
                detectDragGestures(
                    onDragStart = { start -> gesture.onDragStart(start, displayed, handleRadius) },
                    onDragEnd = { gesture.onDragFinished() },
                    onDragCancel = { gesture.onDragFinished() },
                ) { change, dragAmount ->
                    change.consume()
                    gesture.onDrag(dragAmount, displayed)?.let(onSelectionChange)
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

private const val HANDLE_DRAW_PX = 18f

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
