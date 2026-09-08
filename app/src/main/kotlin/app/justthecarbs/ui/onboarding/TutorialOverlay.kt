package app.justthecarbs.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * The ring drawn around the spotlight.
 *
 * Restrained by design: one static rounded outline, nothing else. No pulse, no connector, no
 * arrowhead — the tutorial is read once and should not be the loudest thing the user ever sees the
 * app do, and a tap-anywhere overlay does not need an arrow telling the user where to press, because
 * pressing anywhere works.
 *
 * The border is not the only cue that a control is the target: the spotlight is a hole in an
 * otherwise uniform dim, and the callout names the control in words. So the design does not rely on
 * colour alone.
 */
@Composable
fun TutorialSpotlightDecoration(
    spotlight: Rect?,
    accent: Color,
    cornerRadius: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (spotlight == null) return@Canvas

        val radiusPx = cornerRadius.toPx()
        val strokePx = strokeWidth.toPx()

        drawRoundRect(
            color = accent,
            topLeft = spotlight.topLeft,
            size = spotlight.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
        )
    }
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
