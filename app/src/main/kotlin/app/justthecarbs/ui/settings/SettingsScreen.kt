package app.justthecarbs.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ocr.ScanEvidenceExport
import app.justthecarbs.ocr.ScanEvidenceRecorder
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.components.SectionLabel
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

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
    onBack: () -> Unit,
) {
    var confirmClearRecents by remember { mutableStateOf(false) }
    var confirmClearProducts by remember { mutableStateOf(false) }
    var privacyPolicyLinkFailed by remember { mutableStateOf(false) }
    var evidenceExportFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.theme == choice,
                        onClick = { onThemeChanged(choice) },
                        label = {
                            Text(
                                stringResource(
                                    when (choice) {
                                        ThemeChoice.SYSTEM -> R.string.settings_theme_system
                                        ThemeChoice.LIGHT -> R.string.settings_theme_light
                                        ThemeChoice.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.height(Space.minTouchTarget),
                    )
                }
            }

            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_results))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ResultStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.resultStyle == style,
                        onClick = { onResultStyleChanged(style) },
                        label = {
                            Text(
                                stringResource(
                                    when (style) {
                                        ResultStyle.DECIMAL_DOMINANT -> R.string.settings_results_decimal_first
                                        ResultStyle.WHOLE_DOMINANT -> R.string.settings_results_whole_first
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.height(Space.minTouchTarget),
                    )
                }
            }

            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_interaction))
            Row(
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_haptics),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.hapticsEnabled, onCheckedChange = onHapticsChanged)
            }

            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_data))
            SettingsAction(
                text = stringResource(R.string.settings_clear_recents),
                onClick = { confirmClearRecents = true },
            )
            SettingsAction(
                text = stringResource(R.string.settings_clear_products),
                onClick = { confirmClearProducts = true },
            )

            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = "Nothing recorded yet — capture a nutrition label first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsAction(
                    text = "Export scan evidence",
                    onClick = {
                        val intent = ScanEvidenceExport.share(context)
                        if (intent == null) {
                            evidenceExportFailed = true
                        } else {
                            evidenceExportFailed = false
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
                SettingsAction(
                    text = "Clear recorded captures",
                    onClick = {
                        ScanEvidenceRecorder.clear(context)
                        evidenceExportFailed = false
                    },
                )
            }
            Spacer(Modifier.height(Space.l))
        }
    }

    // Both destructive actions confirm first (§43). The product wording spells out that verified
    // values go too, because that is the part the user would regret.
    if (confirmClearRecents) {
        ConfirmDialog(
            message = stringResource(R.string.settings_clear_recents_confirm),
            onConfirm = { confirmClearRecents = false; onClearRecents() },
            onDismiss = { confirmClearRecents = false },
        )
    }
    if (confirmClearProducts) {
        ConfirmDialog(
            message = stringResource(R.string.settings_clear_products_confirm),
            onConfirm = { confirmClearProducts = false; onClearProducts() },
            onDismiss = { confirmClearProducts = false },
        )
    }
}

@Composable
private fun SettingsAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(Space.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Space.cardRadius),
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
