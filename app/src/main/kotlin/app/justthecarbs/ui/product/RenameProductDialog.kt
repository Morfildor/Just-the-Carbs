package app.justthecarbs.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.justthecarbs.R
import app.justthecarbs.domain.Product
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Space

const val RENAME_FIELD_TAG = "product_rename_field"
const val RENAME_SAVE_TAG = "product_rename_save"
const val RENAME_REMOVE_TAG = "product_rename_remove"

/**
 * *Rename on this device* — a personal name for a saved product (1.0.8).
 *
 * ## What this is not
 *
 * It is not product editing. The field here writes [Product.localAlias] and nothing else: the
 * canonical [Product.name] stays on the row underneath, the carbohydrate figure is not on this
 * screen at all, and naming something is not a statement that its value has been checked against
 * the package. A form that also offered the figure would be *Enter product* again under a
 * friendlier title, and would put a settled number back in play at the one moment the user is
 * thinking about something else entirely.
 *
 * ## Why a dialog
 *
 * The same reason `SaveQuickCalculationDialog` is one: the product — its photo, its figure, the
 * portion being calculated — stays visible behind it, so the thing being renamed is in view while
 * the name is chosen, and cancelling returns to a screen that never moved.
 *
 * ## No success message
 *
 * Saving produces no Snackbar and no toast. The product title behind the dialog changes to the new
 * name the moment the write lands, which is a better confirmation than a sentence about it — the
 * user sees the actual result rather than a claim about the result.
 */
@Composable
fun RenameProductDialog(
    /** The name currently shown for this product — its alias if it has one, else its own name. */
    currentDisplayName: String,
    /** The existing alias, so the field opens on what the user last chose. Null for none. */
    existingAlias: String?,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Seeded from the existing alias, so reopening the editor shows what is in force rather than an
    // empty box the user has to retype. Keyed on the alias: if it changes underneath (a rename from
    // elsewhere, a refresh) the field re-seeds rather than holding a stale draft.
    var text by remember(existingAlias) { mutableStateOf(existingAlias.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    val trimmed = text.trim()
    // Blank cannot create an alias — *Remove custom name* is the way to have no alias, and it says
    // so. Saving an unchanged name is also refused, so the confirm button cannot look like it will
    // do something when it would do nothing.
    val canSave = trimmed.isNotEmpty() && trimmed != existingAlias

    // Keyed on `Unit`, like the portion field's: requested once for the life of the dialog, so a
    // recomposition on every keystroke cannot yank the cursor back to the start of the field.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline(),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(stringResource(R.string.product_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    text = stringResource(R.string.product_rename_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // What is being renamed, in the user's sight while they choose. Without it the
                // dialog is a bare text box over a title the dialog itself is covering.
                Text(
                    text = stringResource(R.string.product_rename_current, currentDisplayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )

                OutlinedTextField(
                    value = text,
                    // Capped at the same limit the repository enforces on write, so the field
                    // cannot accept a name that storage would silently shorten. Newlines are
                    // dropped rather than allowed to grow the box: this is a name, on one line.
                    onValueChange = { new ->
                        text = new.replace("\n", "").take(Product.MAX_LOCAL_ALIAS_LENGTH)
                    },
                    label = { Text(stringResource(R.string.product_rename_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // Done commits, matching the confirm button exactly — including its refusal to
                    // save a blank or unchanged name, so the two controls cannot disagree about
                    // what is valid. A Done that saved what the button would not is the kind of
                    // divergence nobody finds until it has stored something odd.
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(trimmed) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(RENAME_FIELD_TAG),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = canSave,
                modifier = Modifier.testTag(RENAME_SAVE_TAG),
            ) {
                Text(stringResource(R.string.product_rename_save))
            }
        },
        dismissButton = {
            // Removing is offered only when there is something to remove, in the dismiss slot
            // rather than as a third button: a dialog with Save, Cancel and Remove makes the user
            // read three options to do the one thing they opened it for. Cancel remains reachable
            // by tapping outside or by the back gesture.
            if (existingAlias != null) {
                TextButton(onClick = onRemove, modifier = Modifier.testTag(RENAME_REMOVE_TAG)) {
                    Text(stringResource(R.string.product_rename_remove))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.verify_cancel)) }
            }
        },
    )
}
