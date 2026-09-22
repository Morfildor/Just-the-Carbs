package app.justthecarbs.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space

/**
 * One chrome for both scanners.
 *
 * The two camera screens had independently grown their own. One put its close button in an opaque
 * black band with a title beside it; the other floated two buttons over the preview. One drew a
 * black 66% dock; the other a light themed card. Both declared a near-identical private scrim
 * button (`ScrimIconButton` / `LabelScrimIconButton`) differing only in a hundredth of alpha. The
 * result was that moving between the barcode and label scanners felt like moving between two apps.
 *
 * Everything here is presentation. Nothing on the scanning path -- `ScanRegionOverlay` geometry,
 * `captureLabel`, focus and metering, the analyzers, the haptics or any OCR stage -- is touched by
 * it. These composables take state the screens already held and render it consistently.
 */

/** The dock's own black: dark enough to carry white text over any preview, light enough to see through. */
private val DOCK_SCRIM = Color.Black.copy(alpha = 0.66f)

/** The scrim buttons' black. One value; the two screens used 0.45 and 0.55 for no stated reason. */
private val BUTTON_SCRIM = Color.Black.copy(alpha = 0.45f)

/**
 * A circular control floating over the camera preview.
 *
 * `CircleShape` rather than `RoundedCornerShape(50)` -- the same figure, but the token says what it
 * means, and these buttons are now the only fully round things in the app (report section 5).
 */
@Composable
internal fun ScannerScrimButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(Space.minTouchTarget)
            .background(BUTTON_SCRIM, CircleShape)
            .semantics { contentDescription = description },
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}

/**
 * The top row over the preview: close at the start, an optional trailing control at the end.
 *
 * No title band. The barcode scanner used to paint an opaque strip across the top of the frame
 * carrying the words "Scan barcode" -- a heading over a live camera, occluding the very thing the
 * user is aiming, restating what they had just tapped to get here. The dock below names the task,
 * where it does not cover the preview.
 *
 * The trailing slot falls back to an empty box of the same width, so the close button keeps its
 * position whether or not the device has a torch: `SpaceBetween` with a single child would centre
 * it.
 */
@Composable
internal fun ScannerTopBar(
    onClose: () -> Unit,
    closeDescription: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(Space.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScannerScrimButton(
            onClick = onClose,
            icon = Icons.Filled.Close,
            description = closeDescription,
        )
        if (trailing != null) trailing() else Spacer(Modifier.width(Space.minTouchTarget))
    }
}

/**
 * The bottom dock: one dark panel, inset from the edges, holding the task and its actions.
 *
 * Inset rather than edge-to-edge so the preview reads as something the panel sits on, and so the
 * dock's own corner radius is visible -- an edge-to-edge panel with two rounded top corners reads as
 * a sheet the screen is turning into, which is not what this is.
 *
 * The caller supplies the inset and the navigation-bar padding through [modifier]. Both scanners
 * already position this region themselves (the label scanner's bottom slot also holds the review
 * cards, which need the same placement), so applying them here as well would double them.
 */
@Composable
internal fun ScannerDock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // White is the dock's content colour, provided rather than passed.
    //
    // The ground here is a fixed black scrim, not a theme surface, so a child reaching for
    // `onSurface` or `onSurfaceVariant` gets a colour chosen to read against the *page* -- which in
    // Light is near-black ink on a near-black panel. Supplying `LocalContentColor` means every
    // `Text` and `Icon` in every card inherits the right default without each one being told, and a
    // card added later cannot get it wrong by omission.
    //
    // `onSurfaceVariant`'s role -- secondary text -- is expressed here as white at 80%, the same
    // value `ScannerDockCopy` uses for the guidance line, so the two agree.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(DOCK_SCRIM, RoundedCornerShape(Space.cardRadius))
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            content = content,
        )
    }
}

/** Secondary text inside the dock: the dark-chrome equivalent of `onSurfaceVariant`. */
internal val scannerDockSecondary: Color = Color.White.copy(alpha = 0.80f)

/**
 * The dock's two lines of words: what to do, then how it is going.
 *
 * The guidance line cross-fades between states rather than cutting, and carries the polite live
 * region -- so a TalkBack user hears "Hold steady" or "Reading captured label" as it changes,
 * instead of the screen going silent, exactly as both scanners already did separately.
 *
 * No spinner is drawn here. The spinner belongs inside whichever action is doing the work (the
 * label scanner's `CaptureButton` renders one); a second one beside the guidance text made a single
 * operation look like two, which is the duplication P0-5 names.
 */
@Composable
internal fun ScannerDockCopy(title: String, guidance: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
    )
    AnimatedContent(
        targetState = guidance,
        transitionSpec = { fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)) },
        label = "scannerGuidance",
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.80f),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * A quiet action inside the dark dock.
 *
 * White 32% border over a white 8% fill: legible against any preview without becoming a second
 * primary action beside the one the dock exists for. This is the dark-chrome sibling of
 * `JtcOutlinedButton` and deliberately not a call to it -- that one draws its border from
 * `primary`, a theme colour chosen to read against a *page*, not against whatever the camera
 * happens to be pointed at.
 */
@Composable
internal fun ScannerGhostButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(Space.buttonRadius)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier
            .heightIn(min = Space.secondaryButtonHeight)
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.32f else 0.16f), shape)
            .background(Color.White.copy(alpha = 0.08f), shape),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}
