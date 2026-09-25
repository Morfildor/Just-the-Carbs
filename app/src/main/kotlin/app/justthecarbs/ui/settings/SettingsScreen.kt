package app.justthecarbs.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ocr.ScanEvidenceExport
import app.justthecarbs.ocr.ScanEvidenceRecorder
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.components.SectionLabel
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent
import app.justthecarbs.ui.theme.extendedColors

/** Stable handle for instrumented tests. */
const val SETTINGS_REPLAY_TUTORIAL_TAG = "settings_replay_tutorial"

/** The `Show protein` row under Results. */
const val SETTINGS_PROTEIN_TAG = "settings_protein"

/**
 * Settings (§43). Four sections, deliberately small.
 *
 * The About section carries the Open Food Facts attribution required by ODbL (§57) and a plain
 * statement of what the app does and does not do (§45). Neither is decorative: the first is a
 * licence obligation, the second is the safety boundary.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeChanged: (ThemeChoice) -> Unit,
    onResultStyleChanged: (ResultStyle) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onClearRecents: () -> Unit,
    onClearProducts: () -> Unit,
    onReplayTutorial: () -> Unit = {},
    /** Writes the one persisted protein setting, the same one Home's chip writes. */
    onProteinChanged: (Boolean) -> Unit = {},
    /** A clear that has just finished, to confirm in a Snackbar; null when there is none. */
    cleared: ClearedData? = null,
    onClearedShown: () -> Unit = {},
    onBack: () -> Unit,
) {
    var confirmClearRecents by remember { mutableStateOf(false) }
    var confirmClearProducts by remember { mutableStateOf(false) }
    var privacyPolicyLinkFailed by remember { mutableStateOf(false) }
    var feedbackLinkFailed by remember { mutableStateOf(false) }
    var rateLinkFailed by remember { mutableStateOf(false) }
    val evidenceScope = rememberCoroutineScope()
    var evidenceBusy by remember { mutableStateOf(false) }
    var evidenceExportFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Confirms a clear once it has actually run (the ViewModel reports it only after the database
    // has answered), then consumes the report. Keyed on it, so a second, different clear replaces
    // the first message rather than queueing behind it.
    val snackbarHostState = remember { SnackbarHostState() }
    val clearedMessage = when (cleared) {
        ClearedData.RECENT_HISTORY -> stringResource(R.string.settings_cleared_recents)
        ClearedData.SAVED_PRODUCTS -> stringResource(R.string.settings_cleared_products)
        null -> null
    }
    LaunchedEffect(cleared) {
        if (clearedMessage == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(clearedMessage)
        onClearedShown()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // No backdrop motif here. It lives on Home only (2026-09-22 visual pass): on this screen
        // it sat behind the top bar's trailing controls, and decoration may not share a level with
        // a control. The destination is identified by JtcTopBar's DestinationMarker instead.

        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            JtcTopBar(
                title = stringResource(R.string.settings_title),
                destination = Destination.SETTINGS,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    // `navigationBarsPadding()` BEFORE `verticalScroll`, and the order is the whole
                    // thing. Applied after, it pads the scrolling *content*, so the reserved space sits
                    // at the far end of the scrollable extent and travels with the content instead of
                    // holding the viewport's bottom clear — the last row then comes to rest under the
                    // navigation bar. Applied here it pads the viewport, which is the edge the user is
                    // actually looking at.
                    //
                    // Same trap this codebase already recorded for the portion zone's fade modifier.
                    // Both were invisible until someone looked at the screen: the padding exists in
                    // both orderings, so nothing crashes and no assertion fails.
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screenEdge),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                SectionLabel(stringResource(R.string.settings_appearance))
                SettingsChoiceSegment(
                    labels = ThemeChoice.entries.map { choice ->
                        stringResource(
                            when (choice) {
                                ThemeChoice.SYSTEM -> R.string.settings_theme_system
                                ThemeChoice.LIGHT -> R.string.settings_theme_light
                                ThemeChoice.DARK -> R.string.settings_theme_dark
                            },
                        )
                    },
                    selectedIndex = ThemeChoice.entries.indexOf(settings.theme),
                    onSelected = { onThemeChanged(ThemeChoice.entries[it]) },
                )

                HorizontalDivider()

                SectionLabel(stringResource(R.string.settings_results))
                SettingsChoiceSegment(
                    labels = ResultStyle.entries.map { style ->
                        stringResource(
                            when (style) {
                                ResultStyle.DECIMAL_DOMINANT -> R.string.settings_results_decimal_first
                                ResultStyle.WHOLE_DOMINANT -> R.string.settings_results_whole_first
                            },
                        )
                    },
                    selectedIndex = ResultStyle.entries.indexOf(settings.resultStyle),
                    onSelected = { onResultStyleChanged(ResultStyle.entries[it]) },
                )

                // The optional protein reading, under Results because it changes what the result
                // shows. The haptics row's anatomy plus a supporting line, since the label alone
                // does not say what changes. One node: the whole row toggles and the switch's own
                // callback is null, so TalkBack meets a single switch named `Show protein`. The
                // same persisted value as Home's chip.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.minTouchTarget)
                        .toggleable(
                            value = settings.proteinEnabled,
                            role = Role.Switch,
                            onValueChange = onProteinChanged,
                        )
                        .padding(vertical = Space.s)
                        .testTag(SETTINGS_PROTEIN_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.protein_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_protein_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Space.m))
                    Switch(checked = settings.proteinEnabled, onCheckedChange = null)
                }

                HorizontalDivider()

                SectionLabel(stringResource(R.string.settings_interaction))
                // One control, not a label beside a switch: the whole row toggles, so tapping the
                // words works, and TalkBack meets a single switch that carries its own name.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.minTouchTarget)
                        .toggleable(
                            value = settings.hapticsEnabled,
                            role = Role.Switch,
                            onValueChange = onHapticsChanged,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_haptics),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = settings.hapticsEnabled, onCheckedChange = null)
                }

                // Replaying the tutorial belongs with Interaction rather than in a section of its
                // own: it is a small, occasional action, and giving it a heading would make it the
                // most prominent thing on a screen where it is the least important. It carries a
                // supporting line because, unlike the rows above it, its label does not say what
                // tapping it will show.
                SettingsAction(
                    text = stringResource(R.string.settings_replay_tutorial),
                    supporting = stringResource(R.string.settings_replay_tutorial_body),
                    onClick = onReplayTutorial,
                    modifier = Modifier.testTag(SETTINGS_REPLAY_TUTORIAL_TAG),
                )

                HorizontalDivider()

                SectionLabel(stringResource(R.string.settings_data))
                SettingsAction(
                    text = stringResource(R.string.settings_clear_recents),
                    destructive = true,
                    onClick = { confirmClearRecents = true },
                )
                SettingsAction(
                    text = stringResource(R.string.settings_clear_products),
                    destructive = true,
                    onClick = { confirmClearProducts = true },
                )

                HorizontalDivider()

                SectionLabel(stringResource(R.string.settings_about))
                // A plain row with a leading amber star, not a bordered card with a five-star
                // graphic, a headline, a body paragraph and a gold button.
                //
                // That treatment was the loudest object on a screen of quiet rows, and it was the
                // only place in the app spending a saturated fill on something that is not an
                // action the user came to take. A row named `Rate on Google Play` beside one
                // amber star says the same thing and lets the list read as a list.
                RateRow(
                    failed = rateLinkFailed,
                    onClick = {
                        try {
                            uriHandler.openUri("market://details?id=${BuildConfig.APPLICATION_ID}")
                        } catch (_: Exception) {
                            try {
                                // Falls back to the web listing when the Play Store app itself cannot
                                // handle the market:// scheme (e.g. not installed).
                                uriHandler.openUri(
                                    "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
                                )
                            } catch (_: Exception) {
                                rateLinkFailed = true
                            }
                        }
                    },
                )

                SettingsAction(
                    text = stringResource(R.string.settings_privacy_policy),
                    onClick = {
                        try {
                            uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL)
                        } catch (_: Exception) {
                            // No browser or other app can handle the link. Manual entry always remains
                            // available elsewhere in the app (§9, §36); here the fallback is simply
                            // showing the URL as text the user can read and copy themselves.
                            privacyPolicyLinkFailed = true
                        }
                    },
                )
                if (privacyPolicyLinkFailed) {
                    Text(
                        text = stringResource(R.string.settings_privacy_policy_link_failed, BuildConfig.PRIVACY_POLICY_URL),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val feedbackSubject = stringResource(R.string.settings_feedback_subject)
                val feedbackBodyPrompt = stringResource(R.string.settings_feedback_body_prompt)
                SettingsAction(
                    text = stringResource(R.string.settings_feedback),
                    onClick = {
                        val body = buildString {
                            appendLine("App version: ${BuildConfig.VERSION_NAME}")
                            appendLine("Android version: API ${android.os.Build.VERSION.SDK_INT}")
                            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                            appendLine()
                            append(feedbackBodyPrompt)
                        }
                        val mailUri = "mailto:${BuildConfig.CONTACT_EMAIL}" +
                            "?subject=${Uri.encode(feedbackSubject)}" +
                            "&body=${Uri.encode(body)}"
                        try {
                            uriHandler.openUri(mailUri)
                        } catch (_: Exception) {
                            // No email app can handle the intent. The address is still shown so the
                            // user can reach it another way, same fallback shape as Privacy Policy above.
                            feedbackLinkFailed = true
                        }
                    },
                )
                if (feedbackLinkFailed) {
                    Text(
                        text = stringResource(R.string.settings_feedback_link_failed, BuildConfig.CONTACT_EMAIL),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Space.cardRadius))
                        .background(MaterialTheme.extendedColors.orangeSoft)
                        .padding(Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text(
                        text = stringResource(R.string.settings_safety_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.extendedColors.onOrangeSoft,
                    )
                    Text(
                        text = stringResource(R.string.settings_safety_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.onOrangeSoft,
                    )
                }
                // Data and photographs carry separate licences (ODbL/DbCL and CC BY-SA 3.0), so each
                // gets its own credit line. The app displays OFF photos on every product screen, so the
                // image credit is not conditional.
                Text(
                    text = stringResource(R.string.settings_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_attribution_images),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )

                // The version, last.
                //
                // It used to be the FIRST thing under `About`, in bodyMedium, above the rating
                // card and the privacy and feedback rows -- so the section opened with the one
                // line in it nobody navigates to. It is a colophon: useful when reporting a bug,
                // and otherwise the quietest thing on the screen.
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.s),
                )

                // Debug builds only, and gated on the recorder's own flag rather than a second copy of
                // the condition — in a release build `enabled` is a compile-time false, so this whole
                // block is removed along with the recorder itself. It is intentionally the last thing on
                // the screen: it is a developer tool, not a feature.
                if (ScanEvidenceRecorder.enabled) {
                    HorizontalDivider(modifier = Modifier.padding(top = Space.m))
                    SectionLabel("Scan diagnostics (debug)")
                    Text(
                        text = "Exports the last few nutrition-label captures: the photo, the image " +
                            "handed to OCR, the recognized text and the parser trace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (evidenceExportFailed) {
                        Text(
                            text = "Could not export evidence. Capture a label first, then try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsAction(
                        text = if (evidenceBusy) "Preparing evidence…" else "Export scan evidence",
                        enabled = !evidenceBusy,
                        onClick = {
                            evidenceBusy = true
                            evidenceScope.launch {
                                try {
                                    val intent = withContext(Dispatchers.IO) { ScanEvidenceExport.share(context) }
                                    evidenceExportFailed = intent == null
                                    if (intent != null) {
                                        evidenceExportFailed = runCatching { context.startActivity(intent) }.isFailure
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    evidenceExportFailed = true
                                } finally { evidenceBusy = false }
                            }
                        },
                    )
                    SettingsAction(
                        text = "Clear recorded captures",
                        enabled = !evidenceBusy,
                        onClick = {
                            evidenceBusy = true
                            evidenceScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        check(ScanEvidenceRecorder.drain())
                                        ScanEvidenceRecorder.clear(context)
                                    }
                                    evidenceExportFailed = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    evidenceExportFailed = true
                                } finally { evidenceBusy = false }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Space.l))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Space.screenEdge, vertical = Space.s),
        ) { data ->
            Snackbar(snackbarData = data, shape = RoundedCornerShape(Space.buttonRadius))
        }
    }

    // Both destructive actions confirm first (§43). The product wording spells out that verified
    // values go too, because that is the part the user would regret.
    if (confirmClearRecents) {
        ConfirmDialog(
            // The title names the row that opened it, so the modal is unambiguous about which of
            // the two destructive actions is about to run.
            title = stringResource(R.string.settings_clear_recents),
            message = stringResource(R.string.settings_clear_recents_confirm),
            onConfirm = { confirmClearRecents = false; onClearRecents() },
            onDismiss = { confirmClearRecents = false },
        )
    }
    if (confirmClearProducts) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_products),
            message = stringResource(R.string.settings_clear_products_confirm),
            onConfirm = { confirmClearProducts = false; onClearProducts() },
            onDismiss = { confirmClearProducts = false },
        )
    }
}

/**
 * The Appearance / Result-style segmented control.
 *
 * Kept exactly as it was -- it is on the report's preserve list. (The KDoc that used to sit here
 * described `RateUsCard`, which lived further down the file; that card is gone and its
 * documentation with it.)
 */
@Composable
private fun SettingsChoiceSegment(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val outerShape = RoundedCornerShape(Space.buttonRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One group of mutually exclusive options, announced as such.
            .selectableGroup()
            .clip(outerShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, outerShape)
            .padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.primaryButtonHeight)
                    .clip(RoundedCornerShape(Space.buttonRadius - Space.xs))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(index) },
                    )
                    .padding(horizontal = Space.s, vertical = Space.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * `Rate on Google Play`, as one settings row.
 *
 * Replaces a bordered, amber-tinted card carrying a five-star graphic, a headline, a body
 * paragraph and a saturated gold button. All of that was doing marketing work on the one screen
 * where the user came to change a setting, and it was the loudest object on it -- a saturated fill
 * spent on something that is not the task at hand, directly above the orange informational box
 * that IS meant to be the screen's one coloured surface.
 *
 * What survives is the amber, at 20dp, as a single leading star. The row is then structurally the
 * same as every other row in this section, so the list reads as a list.
 */
@Composable
private fun RateRow(failed: Boolean, onClick: () -> Unit) {
    val amber = MaterialTheme.extendedColors.accents.amber
    val label = stringResource(R.string.settings_rate)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.minTouchTarget)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = amber,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Space.s + Space.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (failed) {
            Text(
                text = stringResource(R.string.settings_rate_link_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s),
            )
        }
    }
}

/**
 * One tappable Settings row.
 *
 * [supporting] adds a second, quieter line for an action whose label alone does not say what it
 * does. When present the row grows rather than being fixed at [Space.minTouchTarget] — `heightIn`
 * rather than `height`, so the two lines are never squeezed into one row's worth of space at a large
 * font scale — and the whole row merges into a single semantics node, so TalkBack announces one
 * button with its explanation rather than a button followed by an orphaned line of text.
 */
@Composable
private fun SettingsAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    // "Clear recent history" and "Clear saved products" rendered identically to "Replay tutorial" —
    // same primary-blue label, same row shape — despite one group deleting data and the other only
    // navigating. DESIGN.md's own rule ("destructive/non-destructive semantics are clear") wasn't
    // met: colour was the only signal available on this row shape, and every action used it the same
    // way. Both destructive rows already gate behind a confirmation dialog; this is a legibility
    // addition on top of that safety net, not a replacement for it.
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    // Every settings row's label is ink, including the destructive ones.
    //
    // This reverses the earlier decision to colour `Clear recent history` and `Clear saved
    // products` in `error` red. The reasoning then was that colour was the only signal available
    // on this row shape -- true at the time, and the fix chosen made a settings list read as a
    // warning screen with two alarms permanently lit, which is how a real warning stops being
    // noticed. The red moves to where the decision actually is: the confirm button inside the
    // dialog, which is also where the row's verb now appears (`Clear`).
    //
    // The safety net is unchanged and was always the real one: both rows still gate behind a
    // confirmation dialog, and that dialog now states its title, so the destructive nature is
    // carried in words rather than by hue alone -- which is this app's standing accessibility rule.
    val labelColor = MaterialTheme.colorScheme.onSurface
    if (supporting == null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = Space.minTouchTarget)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { role = Role.Button }
                // `heightIn(48)` above plus centring, rather than 12dp of padding on top of an
                // already-48dp floor, which made every plain row 24dp taller than it needed to be
                // and stretched a four-row section down the page.
                .wrapContentHeight(Alignment.CenterVertically),
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Space.minTouchTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A destructive confirmation.
 *
 * Titled, and the confirm button says the row's own verb.
 *
 * It used to be untitled with a generic confirm label, so a modal that permanently deletes stored
 * products opened with a paragraph of body text and two equally-weighted words. The title names
 * what is about to happen and the verb matches the row that opened it, which is what lets the
 * destructive rows above drop their red -- the decision point carries the weight, not the list.
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline(),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_confirm_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
