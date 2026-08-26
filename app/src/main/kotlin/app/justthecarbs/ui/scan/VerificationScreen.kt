package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

const val VERIFY_CONFIRM_TAG = "verify_confirm"
const val CONFLICT_ASSIST_TAG = "conflict_assist"

/**
 * A value one recognition pass found that nothing else corroborated (spec §8).
 *
 * ## Why this state exists rather than "Confident" or "NotFound"
 *
 * Re-recognising the user's selected region is measured to do both good and harm on the real corpus:
 * it recovered a fixture the full frame could not read (`NotFound` -> the correct `2.3`) and corrected
 * a known-wrong reading (`2.09` -> the printed `2`) — and at other crop tightnesses it destroyed
 * canaries outright. An uncorroborated reading from such a pass is genuinely worth something and is
 * genuinely not proof.
 *
 * Presenting it as confident would launder a single opinion into an answer. Discarding it would throw
 * away the recovery this whole pass exists to enable. So it is shown **next to the photograph it came
 * from**, stated as unverified, and the user — who is holding the package — settles it.
 *
 * The wording is a fact about the reading ("read once, not confirmed"), never about a consequence.
 * This app does not tell people what a number means for their dose.
 */
@Composable
fun VerificationScreen(
    bitmap: Bitmap,
    proposal: EvidenceResolver.Outcome.NeedsVerification,
    onConfirm: (BigDecimal, NutritionBasis) -> Unit,
    onReject: () -> Unit,
    onRetake: () -> Unit,
) {
    val candidate = proposal.reading.candidate
    val basis = candidate.basis ?: NutritionBasis.PER_100_G

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(R.string.verify_found_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.verify_found_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        // The photograph stays: it is the only place the proposal can actually be checked.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                text = stringResource(
                    R.string.verify_found_value,
                    candidate.value.stripTrailingZeros().toPlainString(),
                    basis.unitLabel,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Button(
                onClick = { onConfirm(candidate.value, basis) },
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight).testTag(VERIFY_CONFIRM_TAG),
            ) { Text(stringResource(R.string.verify_found_confirm)) }
            OutlinedButton(
                onClick = onReject,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
            ) { Text(stringResource(R.string.verify_found_reject)) }
            TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.crop_retake))
            }
        }
    }
}

/**
 * Two recognition passes disagreed (spec §8 conflict case).
 *
 * The app says so plainly and offers no value at all. This is the case that would otherwise become a
 * confident-wrong: on the real corpus one fixture yields `2.09`, `2.04` and `2` from three different
 * recognitions of the same photograph, and no property available to the app identifies the printed
 * one. Choosing would be guessing with extra steps.
 *
 * Stating the disagreement is more useful than a bare "not found", because it tells the user why the
 * app stopped and hands them straight to the interaction that resolves it.
 */
@Composable
fun ConflictScreen(
    bitmap: Bitmap,
    conflict: EvidenceResolver.Outcome.Conflicted,
    onAssist: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(R.string.conflict_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    R.string.conflict_body,
                    conflict.values.joinToString(", ") { it.substringBefore('/') },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Button(
                onClick = onAssist,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight).testTag(CONFLICT_ASSIST_TAG),
            ) { Text(stringResource(R.string.conflict_assist)) }
            TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.crop_retake))
            }
        }
    }
}
