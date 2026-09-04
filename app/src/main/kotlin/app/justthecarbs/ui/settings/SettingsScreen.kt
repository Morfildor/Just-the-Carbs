package app.justthecarbs.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import app.justthecarbs.ui.components.AccentBackdrop
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.components.SectionLabel
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent
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
    var feedbackLinkFailed by remember { mutableStateOf(false) }
    var rateLinkFailed by remember { mutableStateOf(false) }
    var evidenceExportFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        AccentBackdrop(
            accent = Destination.SETTINGS.accent(),
            modifier = Modifier.align(Alignment.TopEnd),
        )

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

                // The one item in this section that is marketing rather than utility, so it leads
                // rather than sitting between Feedback and the safety box as one more plain text row.
                RateUsCard(
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

/**
 * The Play Store rating ask (§43 amendment): our own marketing, so it reads as an invitation rather
 * than one more configuration row. Amber rather than the primary blue — blue is already spent on
 * every ordinary action/link in this screen, and amber is the accent already associated with a
 * "gold star" reading elsewhere in the palette (see [app.justthecarbs.ui.theme.AccentPalette]).
 *
 * The card tint stays at the same low, text-safe opacity the safety card uses for `orangeSoft`
 * rather than introducing a second saturated fill: the accent-recession rule that keeps this app's
 * accents out of the result figure's way applies just as much to a card that competes for attention
 * on this screen with the actual settings controls. The button itself is the one deliberate
 * exception — `colorScheme.tertiary` (the brighter, saturated `Orange`/`OrangeDark` token, already
 * used for the meal accent) rather than the text-safe amber, because a call-to-action is meant to
 * stand out and amber measured as too close in hue to the safety card immediately below it to read
 * as a distinct, inviting action. White text on that fill fails contrast badly (measured 1.9:1 in
 * light, 1.7:1 in dark — nowhere near the 4.5:1 floor every other label in this app clears), so the
 * button label uses the theme's own dark ink/chalk text instead, which clears it by a wide margin
 * (9.15:1 / 10.8:1).
 */
@Composable
private fun RateUsCard(failed: Boolean, onClick: () -> Unit) {
    val amber = MaterialTheme.extendedColors.accents.amber
    val gold = MaterialTheme.colorScheme.tertiary
    val onGold = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(amber.copy(alpha = 0.12f))
            .border(1.dp, amber.copy(alpha = 0.35f), RoundedCornerShape(Space.cardRadius))
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            repeat(5) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = amber,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_rate_headline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_rate_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .padding(top = Space.xs)
                .heightIn(min = Space.minTouchTarget)
                .clip(RoundedCornerShape(Space.buttonRadius))
                .background(gold)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                text = stringResource(R.string.settings_rate),
                style = MaterialTheme.typography.labelLarge,
                color = onGold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = onGold,
                modifier = Modifier.size(18.dp),
            )
        }
        if (failed) {
            Text(
                text = stringResource(R.string.settings_rate_link_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            .semantics { role = Role.Button }
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
