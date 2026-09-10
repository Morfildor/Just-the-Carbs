package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.extendedColors

/**
 * The decorative circle bleeding off the top-right corner.
 *
 * Extracted because it was on exactly two of eleven screens — Home in blue and Meal in orange — and
 * nowhere else, which read as an unfinished rollout rather than as a deliberate accent. Every
 * non-camera screen now gets one in its own destination colour.
 *
 * Purely decorative: it sits behind all content and never intercepts touches. The theme owns the
 * alpha because the same 14% wash that reads clearly on cream nearly disappears into the warm
 * near-black ground. Both values stay restrained enough that the circle remains atmosphere, not a
 * competing glow.
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
            .background(
                accent.copy(alpha = MaterialTheme.extendedColors.accentBackdropAlpha),
                CircleShape,
            ),
    )
}
