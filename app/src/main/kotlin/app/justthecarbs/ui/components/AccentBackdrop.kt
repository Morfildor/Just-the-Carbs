package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
            // Every caller aligns this TopEnd inside a root Box and pads only its *content* with
            // statusBarsPadding(), so the motif alone was laid out from y=0 — underneath the opaque
            // status bar this app paints. Measured on device: on all six screens each bar began at
            // exactly y=128 (the status-bar boundary) with its rounded top corners sliced off flat,
            // which reads as a rendering fault rather than as a mark bled off the edge.
            //
            // The inset belongs here rather than at the six call sites: it is a property of drawing
            // a decoration hard against the top edge, it was already omitted identically six times,
            // and a seventh screen would omit it again.
            .statusBarsPadding()
            // Aligned TopEnd by every caller, so the row's right edge already sits flush with the
            // screen edge — any positive x-offset from there pushes bars off-screen. Measured on
            // device: the rightmost 1-2 bars were clipped on every one of the six screens that call
            // this.
            //
            // There is deliberately no negative y-offset. The previous -8dp existed to let the
            // tallest bar bleed above the row's own bounds, which was harmless only while the bars
            // were transparent; against the opaque bar it subtracted 8dp straight back out of the
            // inset above and re-clipped what that inset exists to protect.
            //
            // The five 12dp bars and four 8dp gaps total exactly 92dp, so the original 92dp-wide row
            // fitted its content with zero tolerance and, aligned flush to the screen edge, had
            // nowhere to round into: measured on a 1080px screen the first four bars rendered 32px
            // wide and the fifth only 30px, cut by the screen boundary. Removing the earlier
            // x-offset fixed an *offset* overflow but not this one, which is the content being
            // exactly as wide as its container.
            //
            // So the explicit width is gone: an unconstrained Row measures to its own content, and
            // the end padding then holds the whole mark clear of the screen edge. Exact-fit
            // arithmetic is what made a sub-pixel rounding difference visible as a clipped bar, and
            // a width derived from the bars themselves cannot drift from them the way a typed 92dp
            // did.
            .height(96.dp)
            .padding(end = MOTIF_EDGE_INSET),
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

/**
 * How far the motif's right edge sits inside the screen edge.
 *
 * Small enough that the mark still reads as anchored to the corner rather than floating, and large
 * enough that the last bar is drawn in full. The row has no declared width — it measures to its own
 * bars — so this padding is the whole margin rather than one half of a pair that has to agree.
 */
private val MOTIF_EDGE_INSET = 6.dp
