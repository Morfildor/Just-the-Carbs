package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The decorative circle bleeding off the top-right corner.
 *
 * Extracted because it was on exactly two of eleven screens — Home in blue and Meal in orange — and
 * nowhere else, which read as an unfinished rollout rather than as a deliberate accent. Every
 * non-camera screen now gets one in its own destination colour.
 *
 * Purely decorative: it sits behind all content and never intercepts touches. The alpha is low
 * enough that text drawn over it keeps the contrast `ContrastTest` asserts against the flat
 * background — do not raise it without re-checking that, because the assertions are computed
 * against the *ground colour*, not against this.
 *
 * Camera screens deliberately do not use it. They are black by design and a coloured wash over a
 * live preview is noise on the one screen where the user is trying to see through the glass.
 */
@Composable
fun AccentBackdrop(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 80.dp, y = (-90).dp)
            .size(220.dp)
            .background(accent.copy(alpha = 0.14f), CircleShape),
    )
}
