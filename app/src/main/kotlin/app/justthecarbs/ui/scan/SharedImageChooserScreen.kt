package app.justthecarbs.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

const val SHARE_CHOOSER_BARCODE_TAG = "share_chooser_barcode"
const val SHARE_CHOOSER_LABEL_TAG = "share_chooser_label"
const val SHARE_CHOOSER_CANCEL_TAG = "share_chooser_cancel"
const val SHARE_CHOOSER_FAILED_TAG = "share_chooser_failed"

/**
 * "What do you want to scan?" - the one question a shared image has to answer.
 *
 * ## Why the app asks instead of deciding
 *
 * A screenshot of a barcode and a photo of a nutrition table are the same MIME type and, to any
 * cheap heuristic, the same kind of picture. The app could run both recognizers and take whichever
 * answered - and that is precisely the guess this codebase refuses everywhere else it appears. A
 * barcode found in the corner of a photo of a packet would silently win over the nutrition table
 * the user actually shared, and the result is the failure this project keeps naming: a confident
 * product page, indistinguishable afterwards from having scanned the right thing.
 *
 * Neither recognizer runs before the user answers. That is not merely how this is written; it is
 * structural, because the recognizers are reached only through the two screens this chooser
 * navigates to, and it navigates to neither until a card is tapped.
 *
 * ## Why a full route rather than a bottom sheet
 *
 * A share can arrive cold, into an app with nothing behind it. A sheet needs a screen to sit on,
 * which on a cold share would mean composing Home purely as a backdrop and then navigating away
 * from it - and on the onboarding path, a sheet over a carousel the user has not finished. A route
 * is the same thing whether the app was cold, warm, or just past its welcome, which is the property
 * the rest of the startup architecture already has.
 *
 * It also makes dismissal ordinary: Back, the system gesture and the explicit action all reach the
 * same [onCancel], rather than a sheet dismissal being a separate path from the system back.
 */
@Composable
fun SharedImageChooserScreen(
    onChooseBarcode: () -> Unit,
    onChooseLabel: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Insets before the scroll, not after: applied afterwards they pad the scrolling
            // *content* rather than the viewport, and the last row comes to rest under the
            // navigation bar. The same ordering trap this project already hit on Settings.
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.m),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(Space.l))

        Text(
            text = stringResource(R.string.share_chooser_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.share_chooser_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.l))

        // Barcode leads, and filled, matching Home: the same two ways in, in the same order, with
        // the same weighting - so someone arriving here from another app recognises the choice
        // rather than meeting a new vocabulary.
        ShareChoiceCard(
            icon = Icons.Filled.QrCodeScanner,
            title = stringResource(R.string.share_chooser_barcode),
            subtitle = stringResource(R.string.share_chooser_barcode_subtitle),
            accent = MaterialTheme.colorScheme.primary,
            filled = true,
            onClick = onChooseBarcode,
            modifier = Modifier.testTag(SHARE_CHOOSER_BARCODE_TAG),
        )

        Spacer(Modifier.height(Space.s))

        ShareChoiceCard(
            icon = Icons.Filled.DocumentScanner,
            title = stringResource(R.string.share_chooser_label),
            subtitle = stringResource(R.string.share_chooser_label_subtitle),
            accent = MaterialTheme.extendedColors.accents.teal,
            onClick = onChooseLabel,
            modifier = Modifier.testTag(SHARE_CHOOSER_LABEL_TAG),
        )

        Spacer(Modifier.height(Space.m))

        // Quick to dismiss, and deliberately not a bare glyph in a corner: this screen may be the
        // first thing a new user ever sees of this app, arriving from a share they half meant.
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(SHARE_CHOOSER_CANCEL_TAG),
        ) {
            Text(stringResource(R.string.share_chooser_cancel))
        }

        Spacer(Modifier.height(Space.l))
    }
}

/**
 * What the app says when a shared image cannot be read.
 *
 * Stated rather than swallowed: a share that silently does nothing is indistinguishable from the
 * app ignoring the user. The wording names no exception, no size in bytes and no storage
 * vocabulary - every staging failure (unreadable, empty, too large, truncated) is one statement
 * here, because the user acts on all four identically. The same rule the barcode import already
 * follows for its own failures.
 */
@Composable
fun SharedImageFailedScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Space.m)
            .testTag(SHARE_CHOOSER_FAILED_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.share_failed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                text = stringResource(R.string.share_failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.m))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.share_failed_home))
            }
        }
    }
}

/**
 * One choice, built to the same specification as Home's action cards.
 *
 * A separate composable rather than a shared component, and the reason is scope: lifting Home's
 * private card into a shared one would edit Home - a screen this feature has no business changing
 * - to serve a screen that renders two cards. The two are kept identical by the same tokens; if a
 * third caller ever appears, that is the point at which to extract one.
 *
 * The whole row is one merged semantics node with a Button role, so TalkBack announces a single
 * actionable choice rather than an icon, a title, a subtitle and a chevron in sequence. The icon
 * plate is [Space.minTouchTarget], which sets the row height floor above 48dp on its own.
 */
@Composable
private fun ShareChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val description = stringResource(R.string.home_action_description, title, subtitle)
    val containerColor = if (filled) accent else MaterialTheme.colorScheme.surfaceContainerLowest
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val supportingColor = if (filled) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (filled) 5.dp else 0.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.18f),
                spotColor = accent.copy(alpha = 0.18f),
            )
            .clip(shape)
            .background(containerColor)
            .then(if (filled) Modifier else Modifier.border(1.dp, accent.copy(alpha = 0.52f), shape))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = Space.m, vertical = Space.m + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Space.minTouchTarget)
                .background(
                    if (filled) contentColor.copy(alpha = 0.16f) else accent.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = Space.m)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = supportingColor)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor,
        )
    }
}
