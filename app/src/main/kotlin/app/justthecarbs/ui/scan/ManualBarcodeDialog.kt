package app.justthecarbs.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.justthecarbs.R
import app.justthecarbs.domain.BarcodeValidator
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Space

/**
 * Manual barcode entry (§8).
 *
 * The escape hatch for a barcode the camera cannot read — torn, creased, wet, or on packaging
 * already half-opened. Without it the only fallback was retyping the whole product by hand, which
 * throws away a perfectly good database lookup because a few printed bars are damaged.
 *
 * The typed code goes through the same [BarcodeValidator] as a scanned one, so a mistyped digit is
 * caught here rather than becoming a confident lookup for an unrelated product (§36).
 */
@Composable
fun ManualBarcodeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    val normalised = BarcodeValidator.normalize(text)
    // Only complain once there is enough typed to be judged. Flagging "invalid" after the first
    // digit would be technically true and practically hostile.
    val showError = text.length >= MIN_JUDGEABLE_LENGTH && normalised == null
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline(),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(stringResource(R.string.scanner_enter_manually)) },
        text = {
            // The dialog exists to take one typed number, so it opens with the keyboard ready for it.
            // Inside the dialog's own content, which is composed in the dialog's window: an effect in
            // the caller's composition can run before the field it names exists.
            LaunchedEffect(Unit) { fieldFocus.requestFocus() }
            Column {
                OutlinedTextField(
                    value = text,
                    // Digits only: every supported symbology is numeric, so filtering here means
                    // the user cannot produce input the validator will reject on a technicality.
                    onValueChange = { entered -> text = entered.filter(Char::isDigit).take(MAX_GTIN) },
                    label = { Text(stringResource(R.string.manual_barcode)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    // The Done button's path and condition: a code the validator refuses is never sent.
                    keyboardActions = KeyboardActions(
                        onDone = { normalised?.let(onConfirm) ?: focusManager.clearFocus() },
                    ),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.error_invalid_barcode)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                )
                Text(
                    text = stringResource(R.string.scanner_enter_barcode_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { normalised?.let(onConfirm) },
                enabled = normalised != null,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** EAN-8 is the shortest supported symbology, so nothing below 8 digits can be judged yet. */
private const val MIN_JUDGEABLE_LENGTH = 8

/** GTIN-14 is the longest form the validator accepts. */
private const val MAX_GTIN = 14
