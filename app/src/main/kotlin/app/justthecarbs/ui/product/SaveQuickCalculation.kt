package app.justthecarbs.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.justthecarbs.R
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Space

/**
 * *Save product* on an unsaved calculation (1.0.3 P1).
 *
 * Deliberately an [OutlinedButton] rather than a filled one. The primary decision on this screen is
 * the carbohydrate total — the user came here to read a number, and most of the time they will read
 * it and leave. Saving is the minority path, and giving it filled-button weight would restate the
 * problem this patch exists to remove: an app that treats "record this product" as the point and the
 * answer as a step on the way there.
 */
@Composable
fun SaveQuickCalculationAction(
    saving: Boolean,
    failed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onClick,
            // Disabled only while a write is actually in flight. A second tap in that window would
            // otherwise mint a second synthetic barcode and store the same product twice.
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
            shape = RoundedCornerShape(Space.buttonRadius),
        ) {
            Text(stringResource(R.string.quick_save))
        }

        // A failed save is said out loud. The calculation is still on screen and still correct, so
        // silence here reads as success and the user would leave believing the product was kept.
        if (failed) {
            Text(
                text = stringResource(R.string.quick_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Asks for the one thing saving genuinely requires (1.0.3 P1).
 *
 * A name and nothing else. The carbohydrate figure and its basis are already established — they are
 * what the user has been calculating with — so re-presenting them here as editable fields would
 * reopen a settled question at the worst moment, and would be the *Enter product* form again under a
 * different title. Correcting the figure is still available upstream, on the scanner card and
 * through manual entry.
 *
 * A dialog rather than a screen, so the result stays visible behind it and cancelling returns to a
 * calculation that never went anywhere.
 */
@Composable
fun SaveQuickCalculationDialog(
    nameError: Boolean,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline(),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(stringResource(R.string.quick_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    text = stringResource(R.string.quick_save_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.manual_name)) },
                    singleLine = true,
                    isError = nameError,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (nameError) {
                    Text(
                        text = stringResource(R.string.manual_error_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            // Deliberately not the same word as the action that opened this dialog. Two "Save
            // product" controls on one screen is ambiguous to a person and genuinely unresolvable
            // for an accessibility service or a test, which cannot tell which one was meant.
            TextButton(onClick = { onSave(name) }, enabled = !saving && name.isNotBlank()) {
                Text(stringResource(R.string.quick_save_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.verify_cancel)) }
        },
    )
}
