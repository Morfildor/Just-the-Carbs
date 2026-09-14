package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.extendedColors

/**
 * A quiet barcode-like registration mark in the top-right corner.
 *
 * Extracted because it was on exactly two of eleven screens — Home in blue and Meal in orange — and
 * nowhere else, which read as an unfinished rollout rather than as a deliberate accent. Every
 * non-camera screen now gets one in its own destination colour.
 *
 * The previous oversized circle was a generic decoration with no relationship to the product. The
 * uneven rules borrow from both a barcode and a nutrition label, giving every destination a small
 * piece of recognisable visual grammar without adding an illustration or another card. Purely
 * decorative: it sits behind all content and never intercepts touches.
 *
 * Camera screens deliberately do not use it. They are black by design and a coloured wash over a
 * live preview is noise on the one screen where the user is trying to see through the glass.
 */
@Composable
fun AccentBackdrop(accent: Color, modifier: Modifier = Modifier) {
    val alpha = MaterialTheme.extendedColors.accentBackdropAlpha
    Row(
        modifier = modifier
            .offset(x = 24.dp, y = (-8).dp)
            .width(92.dp)
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(40.dp, 72.dp, 56.dp, 88.dp, 64.dp).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(height)
                    .background(
                        accent.copy(alpha = alpha * if (index % 2 == 0) 0.72f else 1f),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}
