package app.carbscan.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.carbscan.BuildConfig
import app.carbscan.R
import app.carbscan.domain.AppSettings
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.ThemeChoice
import app.carbscan.ui.components.SectionLabel
import app.carbscan.ui.theme.Space

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.product_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = Space.s).semantics { heading() },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenEdge)
                .navigationBarsPadding(),
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
            Text(
                text = stringResource(R.string.settings_safety_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.settings_safety_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
