package app.justthecarbs.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Copies one carbohydrate figure to the clipboard, confirming on its own control.
 *
 * ## Why this is shared rather than written twice
 *
 * The product context says the user transcribes the figure into something else — that is the last
 * step of the job, not an extra. The calculator has had this button since 1.0.2; the **meal total**
 * did not, although a meal total is the figure a multi-item user is most likely to be transcribing
 * (they built the meal precisely to get one number for several foods). Adding a second hand-written
 * copy button is how two confirmations drift apart, which is the same failure `JtcTopBar` and
 * `rememberSuccessPulse` were each extracted to stop.
 *
 * ## What it copies
 *
 * [value] only — the bare number, never "31 g carbs" (§19). The caller resolves it through
 * `ResultFormatter.clipboardValue` so the clipboard agrees with the user's configured result style,
 * and so this component never decides how a figure is formatted.
 *
 * ## How it confirms
 *
 * The icon swaps to a check for the shared hold (see [rememberSuccessPulse]), because a Toast is
 * transient, easy to miss one-handed, and gone by the time the user looks back from the app they are
 * pasting into. The Toast is kept alongside it: it is what announces the copy to TalkBack, which the
 * icon swap alone does not do. The hold is keyed on the copied value, so copying a *different*
 * number after changing the portion restarts the confirmation instead of reusing a running timer.
 */
@Composable
fun CopyResultButton(
    value: String,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // `LocalClipboard`, not the deprecated `LocalClipboardManager`.
    //
    // The replacement's `setClipEntry` is a **suspend** function — the platform clipboard is not
    // guaranteed to answer synchronously — so the write is launched on a scope tied to this
    // composable rather than performed inline in the click lambda. That is the whole difference in
    // shape between the two APIs; what is copied, when the check mark appears, the Toast and the
    // semantics are all deliberately unchanged.
    //
    // The confirmation is still set synchronously on click rather than after the write resumes.
    // Awaiting it would make the check mark's timing depend on the platform's, which on a slow
    // frame reads as a dropped tap on the one control whose entire job is saying "that worked".
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Resolved during composition, not inside the click lambda: reading resources off LocalContext
    // at click time is not configuration-aware and can return a stale string.
    val copyLabel = stringResource(R.string.product_copy)
    val copiedMessage = stringResource(R.string.product_copied, value)

    var copiedAt by remember { mutableStateOf<String?>(null) }
    val showCopied = rememberSuccessPulse(copiedAt) && copiedAt == value

    IconButton(
        onClick = {
            // ClipData.newPlainText's first argument is the clip *label* — what the system may show
            // when describing the clipboard's contents — and is not part of what gets pasted. The
            // pasted text is `value` alone, exactly as before: the bare number, never "31 g carbs".
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(copyLabel, value)),
                )
            }
            copiedAt = value
            if (hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
            .size(Space.minTouchTarget)
            .semantics { contentDescription = copyLabel },
    ) {
        Icon(
            imageVector = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = null,
            tint = if (showCopied) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }
}
