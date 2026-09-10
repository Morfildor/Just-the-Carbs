package app.justthecarbs.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
 * **The aperture remains exact.** The moderate veil is removed over the target. A separate broad
 * elliptical colour field guides the eye, while a restrained tonal hairline defines same-hue
 * targets without reading as another rounded control.
 *
 * Drawn as one layer with [BlendMode.DstOut] punching the hole, rather than as four rectangles around
 * the target: four rectangles leave hairline seams at their joins on fractional pixel boundaries,
 * which reads as a cross through the dim. The `saveLayer` is required — an erasing blend needs its own
 * layer or it erases everything beneath it, including the preview.
 *
 * A null [spotlight] dims the whole screen evenly, which is the target-unavailable fallback.
 */
@Composable
fun TutorialScrim(
    spotlight: Rect?,
    cornerRadius: Dp,
    scrimColor: Color,
    modifier: Modifier = Modifier,
    edgeAlpha: Float = 0.055f,
    localAlpha: Float = 0f,
    localRelief: Float = 0f,
    apertureVisibility: Float = 1f,
    clearAperture: Boolean = true,
) {
    Canvas(
        // The scrim is pure decoration: narration carries the words, and announcing a dimming
        // layer would put a meaningless stop between the title and the action.
        modifier = modifier.clearAndSetSemantics { },
    ) {
        val radiusPx = cornerRadius.toPx()
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
                        0.32f to Color.Transparent,
                        0.72f to scrimColor.copy(alpha = edgeAlpha * 0.55f),
                        1f to scrimColor.copy(alpha = edgeAlpha),
                        center = spotlight.center,
                        radius = furthest.coerceAtLeast(1f),
                    ),
                )

                if (localAlpha > 0f) {
                    val localSpread = 72.dp.toPx()
                    val horizontalRadius = spotlight.width / 2f + localSpread * 1.25f
                    val verticalRadius = spotlight.height / 2f + localSpread
                    val radius = maxOf(horizontalRadius, verticalRadius).coerceAtLeast(1f)
                    scale(horizontalRadius / radius, verticalRadius / radius, spotlight.center) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to scrimColor.copy(alpha = localAlpha),
                                0.58f to scrimColor.copy(alpha = localAlpha * 0.62f),
                                1f to Color.Transparent,
                                center = spotlight.center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = spotlight.center,
                        )
                    }
                }

                if (localRelief > 0f) {
                    val reliefCenter = spotlight.center.copy(y = spotlight.center.y - 20.dp.toPx())
                    val horizontalRadius = spotlight.width / 2f + 36.dp.toPx()
                    val verticalRadius = spotlight.height / 2f + 40.dp.toPx()
                    val radius = maxOf(horizontalRadius, verticalRadius).coerceAtLeast(1f)
                    scale(horizontalRadius / radius, verticalRadius / radius, reliefCenter) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color.Black.copy(alpha = localRelief),
                                0.52f to Color.Black.copy(alpha = localRelief * 0.48f),
                                1f to Color.Transparent,
                                center = reliefCenter,
                                radius = radius,
                            ),
                            radius = radius,
                            center = reliefCenter,
                            // DstOut respects the radial alpha; Clear would erase the scrim at
                            // full strength anywhere the brush is non-transparent.
                            blendMode = BlendMode.DstOut,
                        )
                    }
                }

                if (clearAperture) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = apertureVisibility.coerceIn(0f, 1f)),
                        topLeft = spotlight.topLeft,
                        size = spotlight.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                        // Acquisition is intentionally partial until visibility reaches one.
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
            canvas.restore()
        }
    }
}

/**
 * A single broad elliptical atmosphere around the aperture. Its shape deliberately does not trace
 * the target's rounded rectangle, and the target is cleared from the layer so the real control
 * stays crisp. The finite acquisition settles once; geometry remains owned by the same animated
 * spotlight Rect as the scrim.
 */
@Composable
fun TutorialSpotlightDecoration(
    spotlight: Rect?,
    accent: Color,
    edgeColor: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    acquisition: Float = 1f,
    intensity: Float = 1f,
    spreadScale: Float = 1f,
    bloomAlpha: Float = 0.26f,
    edgeAlpha: Float = 0.48f,
    edgeWidth: Dp = 1.dp,
    arrivalBoost: Float = 0f,
    visibility: Float = 1f,
    drawEdge: Boolean = true,
    clearCenter: Boolean = true,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (spotlight == null || spotlight.width <= 0f || spotlight.height <= 0f) return@Canvas
        val settled = acquisition.coerceIn(0f, 1f)
        val visible = visibility.coerceIn(0f, 1f)
        val arrival = tutorialFocusArrival(settled, arrivalBoost)
        val spreadArrival = tutorialFocusArrivalSpread(settled, arrivalBoost)
        val spread = 54.dp.toPx() * spreadScale * spreadArrival
        val horizontalRadius = spotlight.width / 2f + spread * 1.25f
        val verticalRadius = spotlight.height / 2f + spread
        val radius = maxOf(horizontalRadius, verticalRadius).coerceAtLeast(1f)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            scale(horizontalRadius / radius, verticalRadius / radius, spotlight.center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to accent.copy(alpha = (bloomAlpha * intensity * arrival * visible).coerceAtMost(1f)),
                        0.38f to accent.copy(
                            alpha = (bloomAlpha * intensity * arrival * visible * 0.50f).coerceAtMost(1f),
                        ),
                        0.72f to accent.copy(alpha = bloomAlpha * intensity * visible * 0.12f),
                        1f to Color.Transparent,
                        center = spotlight.center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = spotlight.center,
                )
            }
            if (clearCenter) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = spotlight.topLeft,
                    size = spotlight.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                    ),
                    blendMode = BlendMode.Clear,
                )
            }
            canvas.restore()
        }
        if (drawEdge) {
            drawRoundRect(
                color = edgeColor.copy(
                    alpha = (edgeAlpha * arrival * visible).coerceIn(0f, 1f),
                ),
                topLeft = spotlight.topLeft,
                size = spotlight.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    cornerRadius.toPx(),
                    cornerRadius.toPx(),
                ),
                style = Stroke(width = edgeWidth.toPx()),
            )
        }
    }
}

/** Decorative, finite editorial annotation. Geometry has already passed collision checks. */
@Composable
internal fun TutorialPointerDecoration(
    geometry: TutorialPointerGeometry?,
    accent: Color,
    casingColor: Color,
    acquisition: Float,
    visibility: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val pointer = geometry ?: return@Canvas
        val reveal = ((acquisition - 0.08f) / 0.74f).coerceIn(0f, 1f)
        val visible = visibility.coerceIn(0f, 1f)
        if (reveal <= 0f) return@Canvas

        val path = Path().apply {
            moveTo(pointer.start.x, pointer.start.y)
            val stepCount = (POINTER_DRAW_SEGMENTS * reveal).toInt().coerceAtLeast(1)
            repeat(stepCount) { index ->
                val fraction = minOf(reveal, (index + 1f) / POINTER_DRAW_SEGMENTS)
                val point = cubicPoint(pointer, fraction)
                lineTo(point.x, point.y)
            }
        }
        val opacity = (0.90f + 0.06f * acquisition.coerceIn(0f, 1f)) * visible
        val tip = cubicPoint(pointer, reveal)
        drawPath(
            path = path,
            color = casingColor.copy(alpha = POINTER_CASING_ALPHA * visible),
            style = Stroke(width = 3.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.78f * visible),
                    accent.copy(alpha = opacity),
                ),
                start = pointer.start,
                end = tip,
            ),
            style = Stroke(width = 2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )

        val tangent = cubicTangent(pointer, reveal)
        val tangentLength = kotlin.math.hypot(tangent.x, tangent.y).coerceAtLeast(0.001f)
        val unitX = tangent.x / tangentLength
        val unitY = tangent.y / tangentLength
        val headLength = 8.5.dp.toPx()
        val headWidth = 3.dp.toPx()
        val backX = -unitX * headLength
        val backY = -unitY * headLength
        val sideX = -unitY * headWidth
        val sideY = unitX * headWidth
        val headProgress = ((reveal - 0.74f) / 0.26f).coerceIn(0f, 1f)
        if (headProgress <= 0f) return@Canvas
        val head = Path().apply {
            moveTo(
                tip.x + (backX + sideX) * headProgress,
                tip.y + (backY + sideY) * headProgress,
            )
            lineTo(tip.x, tip.y)
            lineTo(
                tip.x + (backX - sideX) * headProgress,
                tip.y + (backY - sideY) * headProgress,
            )
        }
        drawPath(
            path = head,
            color = casingColor.copy(alpha = POINTER_CASING_ALPHA * visible),
            style = Stroke(
                width = 3.6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawPath(
            path = head,
            color = accent.copy(alpha = opacity),
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private const val POINTER_DRAW_SEGMENTS = 40
private const val POINTER_CASING_ALPHA = 0.42f

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
