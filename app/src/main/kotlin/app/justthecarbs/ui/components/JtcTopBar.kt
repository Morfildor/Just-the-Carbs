package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * ## The accent spine
 *
 * The 4dp coloured edge is how a destination gets identity now that titles are one size. It is
 * deliberately the *same* device as the accent spine on Home's recent cards, so the app reads as
 * one system rather than as a screen that happens to have a stripe.
 *
 * It never carries meaning on its own — the title says where you are, and the spine only
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
            .height(64.dp)
            .padding(bottom = Space.s)
            // Left inset is the spine's own margin — at Space.s (8dp) the spine sat almost against
            // the screen edge and read as a clipped rendering artefact rather than as a deliberate
            // accent. Only visible by looking at the device; every assertion passed either way.
            .padding(start = Space.m, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                // A short centred bar sized against the title's cap height, rather than one
                // filling the row. Filling it made the spine's ends land at whatever the row's
                // padding happened to leave, which looked arbitrary next to the text; 22dp reads
                // as deliberately paired with the title.
                .height(22.dp)
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.product_back),
                    // Ordinary foreground ink, NOT the accent. Tinting it accent-coloured makes the
                    // one control on the bar inherit whatever hue the destination happens to carry
                    // — on Settings that is a muted neutral, which rendered the back arrow as the
                    // faintest thing on a screen where it is the only way out. The spine carries
                    // the destination's colour; the control carries contrast.
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Box(Modifier.width(Space.s))
        }

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
