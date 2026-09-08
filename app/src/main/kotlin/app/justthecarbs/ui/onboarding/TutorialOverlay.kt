package app.justthecarbs.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The dim over the app, with the current target left clear.
 *
 * Two things about how this is drawn are deliberate, and both are about not making the app look
 * switched off while the tutorial is up.
 *
 * **The dim is graded, not flat.** A single uniform wash at the strength needed to make the far
 * corners recede also flattens everything near the target, so the screen reads as blacked out with a
 * hole punched in it rather than as the app with one part brought forward. Instead the base wash is
 * light and a soft radial gradient adds the rest of the weight towards the edges, fading to nothing
 * as it approaches the spotlight. The user can still see the app they are being taught.
 *
 * **The hole is feathered, not cut.** The clear-blended hole is followed by a soft ring that eases
 * the dim back in over [FEATHER] rather than stopping at a hard edge, which is what stops the
 * spotlight reading as a rectangle stuck on top of the screen.
 *
 * Drawn as one layer with [BlendMode.Clear] punching the hole, rather than as four rectangles around
 * the target: four rectangles leave hairline seams at their joins on fractional pixel boundaries,
 * which reads as a cross through the dim. The `saveLayer` is required — a clear blend needs its own
 * layer or it erases everything beneath it, including the preview.
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
        val featherPx = FEATHER.toPx()

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())

            drawRect(color = scrimColor)

            if (spotlight != null) {
                // Weight towards the edges of the screen, easing off near the target. Centred on the
                // spotlight and sized to reach the far corner, so the falloff is about the distance
                // from what the user is meant to be looking at rather than from the screen's middle.
                val furthest = maxOf(
                    spotlight.center.getDistance(),
                    Offset(size.width - spotlight.center.x, spotlight.center.y).getDistance(),
                    Offset(spotlight.center.x, size.height - spotlight.center.y).getDistance(),
                    Offset(size.width - spotlight.center.x, size.height - spotlight.center.y)
                        .getDistance(),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        1f to scrimColor.copy(alpha = scrimColor.alpha * EDGE_WEIGHT),
                        center = spotlight.center,
                        radius = furthest.coerceAtLeast(1f),
                    ),
                )

                drawRoundRect(
                    color = Color.Black,
                    topLeft = spotlight.topLeft,
                    size = spotlight.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                    blendMode = BlendMode.Clear,
                )

                // Ease the dim back in around the hole, so there is no line where clear meets dim.
                //
                // Built from many thin strokes of increasing alpha rather than one thick one: a
                // single stroke is a band of uniform strength, which on the device read as a pale
                // ring drawn around the spotlight — a second edge, exactly the boxiness the feather
                // exists to remove. Stepping the alpha approximates the gradient a blur would give.
                val steps = FEATHER_STEPS
                val bandWidth = featherPx / steps
                repeat(steps) { i ->
                    // Nearest the hole clears the most dim, tailing to nothing at the outer edge.
                    val strength = (1f - i / steps.toFloat())
                    val inset = bandWidth * (i + 0.5f)
                    drawRoundRect(
                        color = scrimColor.copy(alpha = scrimColor.alpha * strength),
                        topLeft = Offset(spotlight.left - inset, spotlight.top - inset),
                        size = androidx.compose.ui.geometry.Size(
                            spotlight.width + inset * 2f,
                            spotlight.height + inset * 2f,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            radiusPx + inset,
                            radiusPx + inset,
                        ),
                        style = Stroke(width = bandWidth + 1f),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
            canvas.restore()
        }
    }
}

/** How far the dim takes to come back in around the spotlight. */
private val FEATHER = 28.dp

/**
 * How many bands the feather is drawn in.
 *
 * Enough that the steps are not individually visible at this width; more would cost overdraw for no
 * difference anyone can see.
 */
private const val FEATHER_STEPS = 12

/**
 * How much extra dim the edges of the screen carry over the base wash.
 *
 * The base wash is deliberately light. This is what makes the far corners recede without flattening
 * the area around the target — see [TutorialScrim].
 */
private const val EDGE_WEIGHT = 0.85f

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
    /**
     * Breathing scale for the outer halo, 1f at rest.
     *
     * Only the halo moves. The ring itself stays exactly on the control's bounds, because a border
     * that grows and shrinks around a button reads as the button changing size — and on this screen
     * the ring is a claim about *which* control the words are describing.
     */
    pulse: Float = 1f,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (spotlight == null) return@Canvas

        val radiusPx = cornerRadius.toPx()
        val strokePx = strokeWidth.toPx()

        // Three concentric strokes of decreasing alpha standing in for a blur: a hard 2dp outline
        // reads as a box drawn on the screen, where a halo reads as light coming off the control.
        // Cheap enough to redraw every frame of the pulse, unlike a real blur.
        repeat(HALO_LAYERS) { layer ->
            val spread = strokePx * (layer + 1) * HALO_STEP * pulse
            drawRoundRect(
                color = accent.copy(alpha = accent.alpha * HALO_ALPHA / (layer + 1)),
                topLeft = Offset(spotlight.left - spread, spotlight.top - spread),
                size = androidx.compose.ui.geometry.Size(
                    spotlight.width + spread * 2f,
                    spotlight.height + spread * 2f,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    radiusPx + spread,
                    radiusPx + spread,
                ),
                style = Stroke(width = strokePx * 1.5f),
            )
        }

        drawRoundRect(
            color = accent,
            topLeft = spotlight.topLeft,
            size = spotlight.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
        )

        if (arrowStart != null) {
            drawConnector(
                start = arrowStart,
                target = spotlight,
                color = accent,
                strokeWidth = strokePx,
            )
        }
    }
}

private const val HALO_LAYERS = 3
private const val HALO_STEP = 2.2f
private const val HALO_ALPHA = 0.28f

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
