package app.justthecarbs.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.justthecarbs.R
import app.justthecarbs.domain.LabelVerdict
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

/** Stable handles for instrumented tests. */
const val VERIFY_CONFIRM_TAG = "verify_label_confirm"
const val VERIFY_USE_PACKAGE_TAG = "verify_label_use_package"

/**
 * The package-versus-app comparison after a label scan (development-pass brief §12, spec §7).
 *
 * Two rules, both of which are about what this dialog *cannot* do:
 *
 * - **A match still requires a tap.** There is no auto-accept path, not even when the figures are
 *   identical. The app read a camera frame; only the user can say the package agrees, and skipping
 *   the tap would put that claim in the app's mouth. It also keeps the interaction identical in
 *   both cases, so a user cannot learn to expect a silent success and stop reading.
 * - **A mismatch is never resolved by the app.** Both figures are shown plainly with equal weight
 *   and neither is preselected — *Use package value* and *Edit detected value* are offered, and
 *   dismissing changes nothing at all. Picking a winner here would mean either overwriting a
 *   verified value with a camera guess, or discarding what the user is holding in their hand.
 *
 * Until the user chooses, the calculator behind this dialog is untouched (session immutability).
 */
@Composable
fun LabelVerificationDialog(
    verdict: LabelVerdict,
    productBasis: NutritionBasis,
    /** True when the current figure is the user's own, so the label reads CURRENT rather than ONLINE. */
    currentIsUserAuthored: Boolean,
    onConfirmMatch: () -> Unit,
    onUsePackageValue: (BigDecimal) -> Unit,
    onEditDetected: (BigDecimal) -> Unit,
    onRescan: () -> Unit,
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
        title = { Text(stringResource(R.string.verify_label_title)) },
        text = {
            Column {
                when (verdict) {
                    is LabelVerdict.Match -> {
                        ComparisonRows(
                            current = verdict.current,
                            detected = verdict.detected,
                            currentBasis = productBasis,
                            detectedBasis = productBasis,
                            currentIsUserAuthored = currentIsUserAuthored,
                        )
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = stringResource(R.string.verify_label_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is LabelVerdict.Mismatch -> {
                        ComparisonRows(
                            current = verdict.current,
                            detected = verdict.detected,
                            currentBasis = productBasis,
                            detectedBasis = productBasis,
                            currentIsUserAuthored = currentIsUserAuthored,
                        )
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = stringResource(R.string.verify_label_mismatch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is LabelVerdict.BasisMismatch -> {
                        ComparisonRows(
                            current = verdict.current,
                            detected = verdict.detected,
                            currentBasis = verdict.currentBasis,
                            detectedBasis = verdict.detectedBasis,
                            currentIsUserAuthored = currentIsUserAuthored,
                        )
                        Spacer(Modifier.height(Space.s))
                        // No "use package value" is offered here at all: the figures measure
                        // different things, so applying one to the other would be wrong rather
                        // than merely unverified (§17).
                        Text(
                            text = stringResource(
                                R.string.verify_label_basis_mismatch,
                                verdict.detectedBasis.unitLabel,
                                verdict.currentBasis.unitLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (verdict) {
                is LabelVerdict.Match -> Button(
                    onClick = onConfirmMatch,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.testTag(VERIFY_CONFIRM_TAG),
                ) { Text(stringResource(R.string.verify_label_confirm)) }

                is LabelVerdict.Mismatch -> Button(
                    onClick = { onUsePackageValue(verdict.detected) },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.testTag(VERIFY_USE_PACKAGE_TAG),
                ) { Text(stringResource(R.string.verify_label_use_package)) }

                is LabelVerdict.BasisMismatch -> TextButton(onClick = onRescan) {
                    Text(stringResource(R.string.verify_label_rescan))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (verdict is LabelVerdict.Mismatch) {
                    TextButton(onClick = { onEditDetected(verdict.detected) }) {
                        Text(stringResource(R.string.verify_label_edit))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * The two figures, stacked and equally weighted.
 *
 * Deliberately the same type size and colour for both. Emphasising either one would be the app
 * nudging the user toward an answer in exactly the situation where it must not (§12).
 */
@Composable
private fun ComparisonRows(
    current: BigDecimal,
    detected: BigDecimal,
    currentBasis: NutritionBasis,
    detectedBasis: NutritionBasis,
    currentIsUserAuthored: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        ComparisonRow(
            label = stringResource(
                if (currentIsUserAuthored) {
                    R.string.verify_label_current_yours
                } else {
                    R.string.verify_label_current
                },
            ),
            value = current,
            basis = currentBasis,
        )
        ComparisonRow(
            label = stringResource(R.string.verify_label_detected),
            value = detected,
            basis = detectedBasis,
        )
    }
}

@Composable
private fun ComparisonRow(label: String, value: BigDecimal, basis: NutritionBasis) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.verify_label_value,
                value.stripTrailingZeros().toPlainString(),
                basis.unitLabel,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
