package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import java.math.BigDecimal

const val VERIFY_CONFIRM_TAG = "verify_confirm"
const val CONFLICT_ASSIST_TAG = "conflict_assist"

/** The frozen photograph behind a proposal, for the instrumented lifecycle test. */
const val VERIFY_PHOTO_TAG = "verify_photo"

/** The enlarged crop of the row the figure was read from. */
const val VERIFY_ZOOM_TAG = "verify_zoom"

/**
 * Which question this screen is asking, for copy only — never for what a confirmation tap means to
 * the rest of the app.
 *
 * ## Why this exists rather than a `verified: Boolean`
 *
 * Neither branch this screen serves is independently OCR-verified in the sense
 * [AutomaticVerification] uses the word: an [OcrProposal] may be a single uncorroborated recognition
 * run exactly as before, and a [ScaleUnresolved] reading is, by construction, one
 * [ReadingEligibility] already refused for insufficient decimal-scale evidence. A boolean named
 * `verified` would misstate the first case as true and would invite a future caller to read the
 * second as "confirmed, therefore verified" — which is precisely the conflation the task that added
 * this mode was written to prevent. Neither case ever sets or implies
 * [app.justthecarbs.domain.BasisProvenance.DECLARED] became verified OCR output; only the user's own
 * tap on [onConfirm] is ever recorded, and it is recorded as a **user confirmation**, never as an OCR
 * verification status change.
 */
enum class VerificationScreenMode {
    /** An [EvidenceResolver.Outcome.NeedsVerification] proposal: one pass read this, unconfirmed. */
    OcrProposal,

    /**
     * [ConfirmationEligibility] admitted this reading despite [ReadingEligibility] refusing it for
     * insufficient/ambiguous decimal-scale evidence. Every other exclusion still applied identically.
     */
    ScaleUnresolved,
}

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
 *
 * ## What the eighth session added, and why
 *
 * This screen now serves **every** unverified proposal, not only the ones the resolver labelled
 * [EvidenceResolver.Outcome.NeedsVerification]. A `Resolved` reading that no independent route
 * corroborated used to go to the live-preview `ProposalCard` instead, with the capture already
 * recycled — and on `20260902-213005-691` that card read `12 g / 100 g` for a package printing
 * `7,2 g`, drawn over a camera view of an empty table after the user had moved the package away.
 * The two states ask the same question and now look the same.
 *
 * Two additions make the question answerable rather than merely fair:
 *
 * - **An enlarged crop of the row the figure came from.** A 1684x3648 capture fitted into a phone
 *   viewport renders an 80 px-tall nutrition row at a few pixels; a user cannot check a decimal
 *   point in that. The crop is taken from the candidate's own geometry, so it shows exactly the
 *   pixels the value was read from.
 * - **The row's recognised text**, so the proposal states *where on the label* it looked. On the
 *   red Lidl label that reads `Hidratos de carbono ... 12g`, which is itself the tell.
 *
 * Both are derived from the candidate; nothing is re-recognised and no figure is altered.
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
    VerificationScreen(
        bitmap = bitmap,
        value = candidate.value,
        basis = candidate.basis ?: NutritionBasis.PER_100_G,
        rowText = candidate.sourceLine,
        // See the primitive overload's own KDoc for why this translation must happen exactly once,
        // here, rather than at each call site.
        rowInSourceSpace = proposal.winningEvidence?.sourceSpaceGeometry ?: candidate.geometry,
        mode = VerificationScreenMode.OcrProposal,
        onConfirm = onConfirm,
        onReject = onReject,
        onRetake = onRetake,
    )
}

/**
 * The primitive form of [VerificationScreen], taking the figure's parts directly rather than an
 * [EvidenceResolver.Outcome.NeedsVerification] — what a [ScanPresentationDecision.Action
 * .CONFIRM_UNVERIFIED] reading needs, since it is never wrapped in that outcome type (it is a
 * [ConfirmationEligibility] admission, and [ReadingEligibility] already refused it once).
 *
 * [rowInSourceSpace] must already be translated into the bitmap's own coordinate space — see
 * [RecognitionEvidence.sourceSpaceGeometry] — since this overload has no evidence object of its own
 * to translate from and must not silently assume the caller passed source-space geometry by
 * forgetting to translate it.
 */
@Composable
fun VerificationScreen(
    bitmap: Bitmap,
    value: BigDecimal,
    basis: NutritionBasis,
    rowText: String,
    rowInSourceSpace: OcrBox,
    mode: VerificationScreenMode,
    onConfirm: (BigDecimal, NutritionBasis) -> Unit,
    onReject: () -> Unit,
    onRetake: () -> Unit,
    /**
     * The label's OWN printed pair — amount and basis label exactly as printed ("6" / "18 g
     * serving") — for a declared-serving reading whose [value]/[basis] here are already normalized
     * to per-100 for storage.
     *
     * Null for every ordinary case, including an ordinary per-100 scale-unresolved reading, where
     * [value]/[basis] already state what is printed and there is nothing separate to show. Non-null
     * only when the reading came from [app.justthecarbs.domain.CarbBasis.PerQuantity] or
     * [app.justthecarbs.domain.CarbBasis.PerUnknownServing] — see
     * [app.justthecarbs.domain.CarbReading.derivedFrom]'s own KDoc, which states exactly this need:
     * showing a computed figure as though the package had printed it is the confusion this exists to
     * avoid. When present, this pair becomes the PRIMARY confirmation line — what the user can
     * actually compare against the package — and [value]/[basis] are shown only as secondary,
     * clearly-labelled context. The exact [BigDecimal] arithmetic behind [value] is unchanged either
     * way; this parameter affects only which numbers this screen puts first.
     */
    printedAmount: BigDecimal? = null,
    printedBasisLabel: String? = null,
) {
    val scannerColors = MaterialTheme.extendedColors.scanner
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(
                    when (mode) {
                        VerificationScreenMode.OcrProposal -> R.string.verify_found_title
                        VerificationScreenMode.ScaleUnresolved -> R.string.verify_scale_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    when (mode) {
                        VerificationScreenMode.OcrProposal -> R.string.verify_found_body
                        VerificationScreenMode.ScaleUnresolved -> R.string.verify_scale_body
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The row the figure was read from, enlarged. This is the part a user can actually check a
        // decimal point in; the full photograph below it is for locating that row on the package.
        val closeUpDescription = stringResource(R.string.verify_found_zoom_description)
        RowCloseUp(
            bitmap = bitmap,
            row = rowInSourceSpace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m)
                .height(CLOSE_UP_HEIGHT)
                .background(Color.Black)
                // A Canvas carries no contentDescription of its own, so the label is applied through
                // semantics — without it TalkBack announces nothing for the one element that shows
                // the user what the app actually read.
                .semantics { contentDescription = closeUpDescription }
                .testTag(VERIFY_ZOOM_TAG),
        )

        // The photograph stays: it is the only place the proposal can actually be checked.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PHOTO_HEIGHT)
                .padding(top = Space.s)
                .background(Color.Black)
                .testTag(VERIFY_PHOTO_TAG),
        ) {
            var viewSize by remember { mutableStateOf(IntSize.Zero) }
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.verify_found_photo_description),
                modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it },
                contentScale = ContentScale.Fit,
            )
            // The same tested mapping the crop and assisted screens use, so the outline cannot drift
            // into a different coordinate space than the one the candidate was measured in.
            val displayed = CropSelectionGeometry.displayedImageBounds(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
            )
            if (displayed.width > 0f && displayed.height > 0f) {
                val scale = displayed.width / bitmap.width
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val topLeft = Offset(
                        displayed.left + rowInSourceSpace.left * scale,
                        displayed.top + rowInSourceSpace.top * scale,
                    )
                    val size = Size(
                        rowInSourceSpace.width * scale,
                        rowInSourceSpace.height * scale,
                    )
                    drawRect(scannerColors.darkEdge, topLeft, size, style = Stroke(width = 8f))
                    drawRect(scannerColors.selection, topLeft, size, style = Stroke(width = 4f))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            // A declared-serving reading: the PRINTED pair is what the user can compare against the
            // package, so it is the primary line. The stored/calculated value is per-100 and appears
            // only as clearly-labelled secondary context beneath it — never presented as though the
            // package itself printed a per-100 figure. See [printedAmount]'s own KDoc.
            if (printedAmount != null && printedBasisLabel != null) {
                Text(
                    text = stringResource(
                        R.string.verify_scale_printed_value,
                        printedAmount.stripTrailingZeros().toPlainString(),
                        printedBasisLabel,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.verify_scale_normalized_value,
                        value.stripTrailingZeros().toPlainString(),
                        basis.unitLabel,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.verify_found_value,
                        value.stripTrailingZeros().toPlainString(),
                        basis.unitLabel,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Where on the label this came from. States the app's claim in the label's own words.
            Text(
                text = stringResource(R.string.verify_found_row, rowText.trim()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onConfirm(value, basis) },
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight).testTag(VERIFY_CONFIRM_TAG),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            VerificationScreenMode.OcrProposal -> R.string.verify_found_confirm
                            VerificationScreenMode.ScaleUnresolved -> R.string.verify_scale_confirm
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onReject,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget).testTag(VERIFY_REJECT_TAG),
            ) { Text(stringResource(R.string.verify_found_reject)) }
            TextButton(
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth().testTag(VERIFY_RETAKE_TAG),
            ) {
                Text(stringResource(R.string.crop_retake))
            }
        }
    }
}

/** The reject action, for the instrumented lifecycle test. */
const val VERIFY_REJECT_TAG = "verify_reject"

/** The retake action, for the instrumented lifecycle test. */
const val VERIFY_RETAKE_TAG = "verify_retake"

private val CLOSE_UP_HEIGHT = 140.dp
private val PHOTO_HEIGHT = 320.dp

/**
 * The candidate's own row, scaled to fill [modifier]'s width.
 *
 * Drawn rather than cropped into a new bitmap: allocating a second bitmap here would double the
 * memory held while the user reads the screen, on top of an 8 MP capture, and would need its own
 * recycling rules. `drawImage` with a source rectangle costs nothing and cannot leak.
 *
 * The source rectangle is padded vertically so the row is not clipped to its exact ink bounds — a
 * value sitting flush against the crop edge is harder to read, and the neighbouring rows are useful
 * context for deciding whether the app looked at the right line.
 */
@Composable
private fun RowCloseUp(bitmap: Bitmap, row: OcrBox, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier = modifier) {
        val padding = (row.height * CLOSE_UP_VERTICAL_PADDING).toInt()
        val top = (row.top - padding).coerceAtLeast(0)
        val bottom = (row.bottom + padding).coerceAtMost(bitmap.height)
        val left = (row.left - padding).coerceAtLeast(0)
        val right = (row.right + padding).coerceAtMost(bitmap.width)
        if (right <= left || bottom <= top) return@Canvas
        drawImage(
            image = image,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(right - left, bottom - top),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

private const val CLOSE_UP_VERTICAL_PADDING = 0.35f

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
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(R.string.conflict_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.conflict_body,
                    conflict.values.joinToString(", ") { it.substringBefore('/') },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(Space.m),
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
