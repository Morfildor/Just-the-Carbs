package app.justthecarbs.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Fades the bottom edge of a scrolling zone while there is more below it.
 *
 * A zone that runs out of room cuts its last line through the middle of the glyphs, which reads as
 * a rendering fault rather than as more content — the calculator's portion zone was found doing
 * exactly that from a 1.3x font scale, and the meal-line editor does it at 2x. The fade says "there
 * is more" in the one place the user is looking.
 *
 * Drawn as an alpha ramp through `DstIn` rather than as a solid-to-transparent gradient painted over
 * the content: painting over needs to know the background colour, and would smear the wrong one in
 * one of the two themes. It draws **nothing** when the zone is not scrollable, so a screen that
 * fits is untouched.
 *
 * **Order matters.** Apply this *before* `verticalScroll`, where it decorates the viewport. After
 * it, it decorates the scrolling content — whose height is the whole scrollable extent — so the
 * fade lands far below the screen and nothing appears at the visible edge. That mistake compiles,
 * runs, and changes nothing visible; only a device screenshot shows it.
 */
fun Modifier.fadeOutWhenMoreBelow(scroll: ScrollState): Modifier = this
    // Forces a layer so DstIn composites against this zone alone, not the whole window.
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        if (!scroll.canScrollForward) return@drawWithContent
        val fade = FADE_HEIGHT.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }

/** How far the bottom of a scrolling zone fades out. Shorter than a line, so nothing is hidden. */
private val FADE_HEIGHT = 20.dp
