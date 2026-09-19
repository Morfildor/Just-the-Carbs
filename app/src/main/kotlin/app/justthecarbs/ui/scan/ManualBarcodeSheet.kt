package app.justthecarbs.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.BarcodeValidator
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import kotlinx.coroutines.launch

/** Stable handles for instrumented tests. */
const val MANUAL_BARCODE_SHEET_TAG = "manual_barcode_sheet"
const val MANUAL_BARCODE_FIELD_TAG = "manual_barcode_field"
const val MANUAL_BARCODE_CONTINUE_TAG = "manual_barcode_continue"
const val MANUAL_BARCODE_PASTE_TAG = "manual_barcode_paste"
const val MANUAL_BARCODE_ERROR_TAG = "manual_barcode_error"

/**
 * Manual and pasted barcode entry (§8; rebuilt as a sheet in 1.0.8).
 *
 * The escape hatch for a barcode the camera cannot read — torn, creased, wet, behind a curved
 * shrink-wrap, or on packaging already half-opened — and the route in for a code that was never on
 * a package at all: copied from a webpage, a message, or an order confirmation.
 *
 * ## One downstream flow
 *
 * A code accepted here goes through [BarcodeValidator] and then through **the caller's ordinary
 * barcode navigation** — the same `Routes.product(barcode)` a camera detection takes. There is no
 * manual lookup path: this screen produces a validated barcode and nothing else, so everything
 * after it (the §10 lookup priority, the cache, the refresh, the not-found recovery) is code that
 * was already there and is exercised identically whichever way the number arrived.
 *
 * ## What it must never do
 *
 * The number is never repaired. Non-digits are refused at the keystroke rather than stripped out of
 * the middle of a code, no check digit is ever recalculated, and a code that fails validation is
 * reported as invalid rather than turned into a nearby valid one. A silently corrected barcode is a
 * confident lookup for an unrelated product (§36) — the one outcome worse than refusing.
 *
 * Surrounding whitespace *is* tolerated, because [BarcodeValidator] already trims it: a code pasted
 * from a message routinely arrives with a leading space or a trailing newline, and that is a
 * property of the clipboard rather than of the number.
 *
 * ## Why a sheet rather than the dialog it replaces
 *
 * This is a *typing* task with a keyboard open for its whole life. A centred dialog is placed
 * relative to the screen and then shoved by the IME; a bottom sheet is anchored to the same edge
 * the keyboard rises from, so the field sits directly above the keys and the layout does not jump
 * when they appear. It also matches the meal-line editor, which is the app's other "correct one
 * value and leave" surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBarcodeSheet(
    /** A validated, normalised 13-digit barcode — the same form a scan produces. */
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SheetContent(
            onConfirm = { code ->
                // Animate away before the caller navigates, so the sheet does not vanish in one
                // frame under the destination replacing it.
                scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(code) }
            },
            onCancel = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

@Composable
private fun SheetContent(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    // `LocalClipboard`, not the deprecated `LocalClipboardManager` — the same migration
    // CopyResultButton already made. Its `getClipEntry` is a **suspend** function, because the
    // platform clipboard is not guaranteed to answer synchronously, so the read is launched on a
    // scope tied to this composable rather than performed inline in the click lambda.
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val normalised = BarcodeValidator.normalize(text)
    // Only complain once there is enough typed to be judged. Flagging "invalid" after the first
    // digit would be technically true and practically hostile — every valid code passes through
    // being too short on its way to being complete.
    val showError = manualBarcodeShowsError(text)

    // The sheet exists to take one number, and the keyboard is where that number comes from.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = { normalised?.let(onConfirm) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The sheet sits above the keyboard rather than behind it, and clears the navigation
            // bar when there is no keyboard — so the Continue button is reachable in both states
            // and under gesture or three-button navigation alike.
            .imePadding()
            .navigationBarsPadding()
            .padding(start = Space.screenEdge, end = Space.screenEdge, bottom = Space.m)
            .testTag(MANUAL_BARCODE_SHEET_TAG),
    ) {
        Text(
            text = stringResource(R.string.scanner_enter_manually),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(R.string.scanner_enter_barcode_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.l))

        OutlinedTextField(
            value = text,
            // Digits only, filtered at the keystroke: every supported symbology is numeric, so this
            // stops the user producing input the validator would reject on a technicality. It is
            // **not** a repair — it refuses a character rather than altering the number — and it is
            // what makes an ordinary system paste work, since a pasted code carrying a stray space
            // or newline arrives clean instead of being rejected for punctuation.
            onValueChange = { entered -> text = manualBarcodeInput(entered) },
            label = { Text(stringResource(R.string.manual_barcode)) },
            singleLine = true,
            // A numeric keypad with a Done action: this field takes digits and there is exactly one
            // thing to do with them. Done submits only when the code is valid — an invalid one
            // leaves the keyboard up with the error showing, which is the state the user can act on.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = showError,
            // The paste affordance lives in the field, not as a second button below it: the
            // clipboard is an *alternative way to fill this one input*, so it belongs where the
            // input is. Icon-only for the same reason — a full-width "Paste from clipboard" button
            // would out-weigh Continue, which is the action that actually does something.
            trailingIcon = {
                val pasteLabel = stringResource(R.string.manual_barcode_paste)
                TextButton(
                    onClick = {
                        clipboardScope.launch {
                            val pasted = clipboard.getClipEntry()
                                ?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                            // Same filter as typing, so the two routes cannot disagree about what
                            // is acceptable. A clipboard holding no digits leaves the field
                            // untouched rather than clearing what the user has already typed.
                            val digits = manualBarcodeInput(pasted)
                            if (digits.isNotEmpty()) text = digits
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(MANUAL_BARCODE_PASTE_TAG),
                ) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = pasteLabel,
                        // `size`, not `height`: a height-only constraint leaves the glyph's width
                        // to its intrinsic value, so it is not guaranteed square.
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag(MANUAL_BARCODE_FIELD_TAG),
        )

        // Held in a fixed-height slot so the sheet does not jump as the message comes and goes
        // while the user types through the too-short states on the way to a complete code.
        Spacer(Modifier.height(Space.xs))
        Text(
            text = if (showError) stringResource(R.string.error_invalid_barcode) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            minLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                // Announced when it appears, so a TalkBack user learns the code was refused rather
                // than finding Continue mysteriously inert.
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(MANUAL_BARCODE_ERROR_TAG),
        )

        Spacer(Modifier.height(Space.m))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.heightIn(min = Space.primaryButtonHeight),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            // *Continue*, not *Done*: what happens next is a product lookup, and the label should
            // say the number is being taken somewhere rather than that the task is finished.
            Button(
                onClick = { submit() },
                enabled = normalised != null,
                shape = RoundedCornerShape(Space.buttonRadius),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.extendedColors.disabledButton,
                    disabledContentColor = MaterialTheme.extendedColors.onDisabledButton,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.primaryButtonHeight)
                    .testTag(MANUAL_BARCODE_CONTINUE_TAG),
            ) {
                Text(stringResource(R.string.manual_barcode_continue))
            }
        }
    }
}

/**
 * What the field accepts from a keystroke or a paste.
 *
 * Digits only, at most [MAX_GTIN] of them. **This is a filter, not a repair**, and the distinction
 * is the safety rule of the whole screen: it refuses characters that cannot be part of any
 * supported symbology, and it never alters the digits themselves — no reordering, no padding, no
 * recomputed check digit. `1234x5678` becomes `12345678`, which the validator then judges on its
 * merits; it does not become some nearby valid code.
 *
 * Internal rather than private so the rule is JVM-testable. Its behaviour is what makes an ordinary
 * system paste work — a code copied from a message arrives with spaces, newlines or a `UPC:` prefix
 * — and "what exactly does a paste do to the number" is not a question to answer by reading a
 * composable.
 */
internal fun manualBarcodeInput(raw: String): String = raw.filter(Char::isDigit).take(MAX_GTIN)

/**
 * Whether [text] is far enough along to be judged invalid.
 *
 * Every valid code passes through being too short on its way to being complete, so complaining
 * before [MIN_JUDGEABLE_LENGTH] digits would flag a correct entry mid-typing. Technically true,
 * practically hostile.
 */
internal fun manualBarcodeShowsError(text: String): Boolean =
    text.length >= MIN_JUDGEABLE_LENGTH && BarcodeValidator.normalize(text) == null

/** EAN-8 is the shortest supported symbology, so nothing below 8 digits can be judged yet. */
private const val MIN_JUDGEABLE_LENGTH = 8

/** GTIN-14 is the longest form the validator accepts. */
private const val MAX_GTIN = 14
