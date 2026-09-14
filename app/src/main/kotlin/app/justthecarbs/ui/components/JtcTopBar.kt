package app.justthecarbs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent

/**
 * The one top bar.
 *
 * ## What this replaces
 *
 * Eleven screens each built their own title `Row`, and they had drifted: measured on 2026-09-03 the
 * title was `titleLarge` on Manual Entry and Settings, `titleMedium` on Meal and Search, and
 * `headlineMedium` on Home. One structural element, three sizes, with nothing holding them
 * together. This is the same failure `Space.primaryButtonHeight` was extracted to stop, where
 * `56.dp` had been typed by hand at sixteen call sites.
 *
 * ## The destination marker
 *
 * Three compact bars borrowed from a nutrition label identify the destination without resembling
 * another control. The same motif appears, at a larger scale, in the page backdrop.
 *
 * It never carries meaning on its own — the title says where you are, and the marker only
 * reinforces it. That is this app's existing accessibility rule, and it binds the new palette
 * exactly as it bound the old one.
 *
 * ## Home is not a caller
 *
 * Home keeps `headlineMedium` and its own row. Its title is a brand wordmark, not a navigation
 * label, and it is the one screen with no back affordance — folding it in here would mean a
 * `destination == HOME` special case inside a component whose whole purpose is that there are none.
 */
@Composable
fun JtcTopBar(
    title: String,
    destination: Destination,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val accent = destination.accent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // 56dp of bar plus 8dp of breathing room beneath it, owned here rather than left to
            // each screen to remember. Without it every adopting screen's first row began
            // immediately under the title — measured on Settings, where "Appearance" touched the
            // bar. Putting it in the component is the whole reason the component exists: four
            // screens each adding their own top padding is four chances to pick a different value.
            .heightIn(min = 64.dp)
            .padding(bottom = Space.s)
            // The outer inset belongs to the bar, while the marker has its own gap from the title.
            .padding(start = Space.s, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.product_back),
                    // Ordinary foreground ink, NOT the accent. Tinting it accent-coloured makes the
                    // one control on the bar inherit whatever hue the destination happens to carry
                    // — on Settings that is a muted neutral, which rendered the back arrow as the
                    // faintest thing on a screen where it is the only way out. The marker carries
                    // the destination's colour; the control carries contrast.
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Box(Modifier.width(Space.s))
        }

        DestinationMarker(accent = accent, modifier = Modifier.padding(end = Space.s))

        Text(
            text = title,
            // One size, everywhere. This is the whole point of the component.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.xs)
                .semantics { heading() },
        )

        trailing()
    }
}

/** Three compact rules borrowed from a nutrition label and echoed by [AccentBackdrop]. */
@Composable
fun DestinationMarker(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(16.dp).height(24.dp)) {
        val ruleWidth = size.width / 5f
        drawRect(
            color = accent,
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.36f),
            size = androidx.compose.ui.geometry.Size(ruleWidth, size.height * 0.64f),
        )
        drawRect(
            color = accent.copy(alpha = 0.72f),
            topLeft = androidx.compose.ui.geometry.Offset(ruleWidth * 2f, 0f),
            size = androidx.compose.ui.geometry.Size(ruleWidth, size.height),
        )
        drawRect(
            color = accent,
            topLeft = androidx.compose.ui.geometry.Offset(ruleWidth * 4f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(ruleWidth, size.height * 0.82f),
        )
    }
}
