package app.justthecarbs.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Space
import kotlinx.coroutines.launch

/** Stable handles for instrumented tests. */
const val IMPORTED_BARCODE_SHEET_TAG = "imported_barcode_sheet"
const val IMPORTED_BARCODE_OPTION_TAG = "imported_barcode_option"

/**
 * Asks which barcode the user meant, when a photograph contained more than one (1.0.8).
 *
 * ## Why this exists rather than a rule that picks
 *
 * A shelf photo, a multipack whose outer and inner codes both show, a packet lying on a receipt —
 * each is an ordinary photograph with two valid GTINs in it. Choosing either automatically produces
 * a confident lookup for a product the user did not photograph, and that failure is invisible
 * afterwards: they see a product page and have no reason to doubt it. The live scanner resolves the
 * same question from *time* — which code the camera was held on — and a still photograph has no
 * such evidence, so asking is the only honest answer. See
 * [app.justthecarbs.domain.ImportedBarcodeSelection], where the rule lives.
 *
 * ## The presentation
 *
 * A bottom sheet over the frozen chosen photograph, matching [ManualBarcodeSheet]'s treatment so
 * the scanner's two "answer one question and leave" surfaces are visibly one system. The photograph
 * stays behind it, which is what makes the choice answerable: the codes on screen are the codes in
 * the picture the user is looking at.
 *
 * Dismissing is a real answer — *neither of these* — and returns to the live camera with nothing
 * changed, exactly as cancelling the picker does.
 *
 * ## Accessibility
 *
 * Each row is its own button carrying the digits as its label, so selection is never communicated
 * by highlight or position alone: a TalkBack user hears the number they are about to look up, and
 * the digits are read as an ordinary string rather than as a quantity. Nothing here depends on
 * colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedBarcodeSheet(
    /** Distinct, already-validated 13-digit barcodes, in the order they were detected. */
    barcodes: List<String>,
    onSelect: (String) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Space.screenEdge, end = Space.screenEdge, bottom = Space.m)
                .testTag(IMPORTED_BARCODE_SHEET_TAG),
        ) {
            Text(
                text = stringResource(R.string.scanner_photo_multiple_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.scanner_photo_multiple_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.l))

            barcodes.forEach { barcode ->
                val label = stringResource(R.string.scanner_photo_use_barcode, barcode)
                OutlinedButton(
                    onClick = {
                        // Animate away before the caller navigates, so the sheet does not vanish in
                        // one frame under the destination replacing it — the same ordering
                        // ManualBarcodeSheet uses for the same reason.
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(barcode) }
                    },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.primaryButtonHeight)
                        // One node, one label: the row states the whole action ("Use barcode
                        // 5000112637922") rather than leaving a screen reader to infer from a bare
                        // number what tapping it does.
                        .semantics { contentDescription = label }
                        .testTag(IMPORTED_BARCODE_OPTION_TAG),
                ) {
                    Text(
                        text = barcode,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(Space.s))
            }

            TextButton(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
