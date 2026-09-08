package app.justthecarbs.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * The dimmed scrim with a rounded hole cut out around the current target.
 *
 * Drawn as one layer with [BlendMode.Clear] punching the hole, rather than as four rectangles
 * around the target: four rectangles leave hairline seams at their joins on fractional pixel
 * boundaries, which reads as a cross through the dim. `drawWithLayer` is required — a clear blend
 * needs its own layer or it erases everything beneath it, including the preview.
 *
 * A null [spotlight] dims the whole screen evenly, which is the orientation step and the
 * target-unavailable fallback.
 */
@Composable
fun TutorialScrim(
    spotlight: Rect?,
    cornerRadius: Dp,
    scrimColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        // The scrim is pure decoration: the callout carries the words, and announcing a dimming
        // layer would put a meaningless stop between the title and the action.
        modifier = modifier.clearAndSetSemantics { },
    ) {
        val radiusPx = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawRect(color = scrimColor)
            if (spotlight != null) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = spotlight.topLeft,
                    size = spotlight.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                    blendMode = BlendMode.Clear,
                )
            }
            canvas.restore()
        }
    }
}

/**
 * The ring drawn around the spotlight, and the connector from the callout to it.
 *
 * Restrained by design: a solid rounded outline and one gently curved line ending in a small
 * arrowhead. No bouncing, no flashing, no dashes marching around the target — the tutorial is read
 * once and should not be the loudest thing the user ever sees the app do.
 *
 * The border is not the only cue that a control is the target: the spotlight is a hole in an
 * otherwise uniform dim, and the callout names the control in words. So the design does not rely on
 * colour alone.
 */
@Composable
fun TutorialSpotlightDecoration(
    spotlight: Rect?,
    arrowStart: Offset?,
    accent: Color,
    cornerRadius: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (spotlight == null) return@Canvas

        val radiusPx = cornerRadius.toPx()
        drawRoundRect(
            color = accent,
            topLeft = spotlight.topLeft,
            size = spotlight.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokeWidth.toPx()),
        )

        if (arrowStart != null) {
            drawConnector(
                start = arrowStart,
                target = spotlight,
                color = accent,
                strokeWidth = strokeWidth.toPx(),
            )
        }
    }
}

/**
 * A curved connector from [start] to the nearest edge of [target], with an arrowhead.
 *
 * The line stops at the target's edge rather than its centre, so it never overlaps the control it
 * is pointing at. The curve is a single quadratic whose control point is offset perpendicular to
 * the run, which is what gives it a gentle bow rather than a right-angled elbow.
 */
private fun DrawScope.drawConnector(
    start: Offset,
    target: Rect,
    color: Color,
    strokeWidth: Float,
) {
    val center = target.center
    // Aim at the point on the target's edge closest to the callout, so the arrow lands on the side
    // the user is reading from.
    val end = Offset(
        x = center.x.coerceIn(target.left, target.right),
        y = if (start.y < center.y) target.top - strokeWidth else target.bottom + strokeWidth,
    )

    val dx = end.x - start.x
    val dy = end.y - start.y
    if (abs(dx) < 0.5f && abs(dy) < 0.5f) return

    // Bow the line sideways by a fraction of its vertical run. Proportional rather than a fixed
    // number of pixels, so a short connector stays nearly straight and a long one curves gently
    // instead of swinging wide.
    val bow = dy * 0.28f
    val control = Offset(start.x + dx * 0.5f - bow * 0.35f, start.y + dy * 0.5f)

    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.cornerPathEffect(strokeWidth)),
    )

    drawArrowHead(tip = end, from = control, color = color, strokeWidth = strokeWidth)
}

/** A small solid arrowhead at [tip], oriented along the direction from [from]. */
private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color, strokeWidth: Float) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 0.5f) return

    val ux = dx / length
    val uy = dy / length
    val size = strokeWidth * 3f
    // Perpendicular to the direction of travel, giving the head its two back corners.
    val px = -uy
    val py = ux

    val baseX = tip.x - ux * size
    val baseY = tip.y - uy * size
    val half = size * 0.45f

    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + px * half, baseY + py * half)
        lineTo(baseX - px * half, baseY - py * half)
        close()
    }
    drawPath(path = head, color = color)
}

/**
 * Grow [rect] by [padding] on every side, clamped to a [width] x [height] screen.
 *
 * The spotlight is deliberately larger than the control it surrounds — a hole exactly the size of a
 * button reads as a rendering artefact rather than as emphasis. Clamping keeps a target near an
 * edge from producing a hole that hangs off the screen.
 */
fun inflateWithin(rect: Rect, padding: Float, width: Float, height: Float): Rect = Rect(
    left = (rect.left - padding).coerceAtLeast(0f),
    top = (rect.top - padding).coerceAtLeast(0f),
    right = (rect.right + padding).coerceAtMost(width),
    bottom = (rect.bottom + padding).coerceAtMost(height),
)
