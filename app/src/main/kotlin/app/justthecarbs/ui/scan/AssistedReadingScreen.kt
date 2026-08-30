package app.justthecarbs.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.AssistedSelection
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.StatedBasis
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

/** Test hooks; the tappable overlay and the inline field carry no text of their own. */
const val ASSIST_OVERLAY_TAG = "assist_overlay"
const val ASSIST_MANUAL_FIELD_TAG = "assist_manual_field"

/**
 * What the assisted fallback is working with.
 *
 * [document] is Pass A's recognition — already in memory, so the assisted path costs no recognition
 * and appears instantly at the moment automatic reading gives up.
 */
data class AssistState(
    val document: OcrDocument?,
    /** True when the confirmed rectangle removed essentially nothing (§11), which is worth saying. */
    val ineffectiveSelection: Boolean = false,
    /**
     * True when the user confirmed a rectangle that had already been recognised (1.0.3 P2).
     *
     * A *separate* flag from [ineffectiveSelection] rather than a reuse of it, because the two say
     * different things and only one of them is true here. "Your box kept nearly the whole photo" is
     * a claim about the box's *size*; this is a claim about it not having *moved*, and the box may
     * be perfectly tight. Telling a user their tight crop was too wide would send them to fix
     * something that is not wrong.
     */
    val cropUnchanged: Boolean = false,
)

/** Which step of the assisted flow the user is on. */
private sealed interface AssistStep {
    /** Choosing how to proceed. */
    data object Choosing : AssistStep

    /** Tapping the carbohydrate row (§18). */
    data object PickingRow : AssistStep

    /** Tapping the printed number directly (§17). */
    data object PickingValue : AssistStep

    /** A number is chosen; the basis still has to be established. */
    data class ConfirmingBasis(val value: BigDecimal, val rowText: String?) : AssistStep

    /** Typing the value in, with the table still visible (§19). */
    data object TypingValue : AssistStep
}

/**
 * The accept actions for a chosen value — the one place both 1.0.3 safety rules apply.
 *
 * ## P0: an impossible figure gets no ordinary accept action
 *
 * A physical-device recording showed a label printing about `7,9 g` producing `790` and `794`
 * through this screen, offered by the same two full-emphasis buttons an ordinary value gets. There
 * is no unit under which 790 g of carbohydrate per 100 g or 100 ml exists, so there is no action to
 * offer — the figure is shown, said to be wrong, and the field stays editable.
 *
 * Deliberately **not** a disabled button: a control that does nothing and says nothing is the dead
 * end this whole screen was built to remove. And deliberately **not** a repair — `790` is not
 * offered as `79.0`, because the decimal point is what OCR is least reliable about and a wrong
 * repair is invisible where a refusal is not.
 *
 * ## P1: a basis the label stated is not asked for again
 *
 * When [statedBasis] is non-null the label said what it was measured per and the classifier read
 * it; only the value needed help. One action is offered, naming that basis. When it is null —
 * nothing stated, or two bases stated — both actions stand, which is the pre-existing behaviour.
 *
 * The two rules compose: a preserved basis does not exempt a value from the plausibility barrier,
 * so an impossible value under a known basis still leaves no accept action at all.
 */
@Composable
private fun BasisActions(
    value: BigDecimal,
    statedBasis: NutritionBasis?,
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
) {
    // Asked per basis, not once: 150 is impossible per 100 g and legitimate per 100 ml, so a single
    // verdict would either block a correct reading or admit an impossible one.
    val offered = (statedBasis?.let(::listOf) ?: NutritionBasis.entries)
        .filter { CarbPlausibility.isPlausiblePer100(value, it) }

    if (offered.isEmpty()) {
        Text(
            text = stringResource(R.string.assist_value_implausible),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    if (statedBasis != null) {
        Text(
            text = stringResource(
                when (statedBasis) {
                    NutritionBasis.PER_100_G -> R.string.assist_basis_from_label_g
                    NutritionBasis.PER_100_ML -> R.string.assist_basis_from_label_ml
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        offered.forEach { basis ->
            Button(
                onClick = { onUseValue(value, basis) },
                modifier = Modifier.weight(1f).height(Space.minTouchTarget),
                shape = RoundedCornerShape(Space.buttonRadius),
            ) {
                Text(
                    stringResource(
                        when (basis) {
                            NutritionBasis.PER_100_G -> R.string.ocr_use_per_100_g
                            NutritionBasis.PER_100_ML -> R.string.ocr_use_per_100_ml
                        },
                    ),
                )
            }
        }
    }
}

/**
 * The end of the dead end (spec §17, §18, §19).
 *
 * ## Why this exists
 *
 * Even with several recognition strategies there will be packaging no automatic pipeline reads. The
 * release-blocking behaviour is not "OCR sometimes fails" — it is that a failure **ejected the user
 * from the task** while the number they wanted was plainly visible on screen. Here the frozen capture
 * stays up and the user finishes in a tap or two.
 *
 * ## Why this does not weaken any safety rule
 *
 * Every refusal in the parser exists because the app could not determine *which nutrient a number
 * belongs to*. When the user taps the carbohydrate row, that association is supplied by a human
 * reading the printed package — a better source than any geometric rule here. The app does not then
 * re-guess: candidates are restricted to the tapped row, so the sugars figure on the next row is not
 * reachable, and the chosen value is always confirmed explicitly before use.
 *
 * The basis is asked for rather than assumed. A value with the wrong basis is a wrong carbohydrate
 * figure, and this screen has no more evidence about per-100-g versus per-100-ml than the parser did.
 */
@Composable
fun AssistedReadingScreen(
    bitmap: Bitmap,
    state: AssistState,
    /** Confirmed value and basis, ready to use. */
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    var step by remember { mutableStateOf<AssistStep>(AssistStep.Choosing) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var typed by remember { mutableStateOf("") }
    var rowCandidates by remember { mutableStateOf<List<AssistedSelection.NumericCandidate>>(emptyList()) }
    var tappedRowText by remember { mutableStateOf<String?>(null) }

    val allNumbers = remember(state.document) { AssistedSelection.numericCandidates(state.document) }

    /**
     * The basis the label itself stated, when it stated exactly one (1.0.3 P1).
     *
     * Derived from the document already on screen, so it costs no recognition and adds no state to
     * carry through the pipeline. Null whenever the label was silent or said two different things,
     * in which case the user is asked exactly as before.
     */
    val statedBasis = remember(state.document) { StatedBasis.of(state.document) }

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
                text = stringResource(
                    when (step) {
                        AssistStep.PickingRow -> R.string.assist_tap_row_title
                        AssistStep.PickingValue -> R.string.assist_tap_value_title
                        is AssistStep.ConfirmingBasis -> R.string.assist_basis_title
                        AssistStep.TypingValue -> R.string.assist_type_title
                        AssistStep.Choosing -> R.string.assist_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    when (step) {
                        AssistStep.PickingRow -> R.string.assist_tap_row_body
                        AssistStep.PickingValue -> R.string.assist_tap_value_body
                        is AssistStep.ConfirmingBasis -> R.string.assist_basis_body
                        AssistStep.TypingValue -> R.string.assist_type_body
                        AssistStep.Choosing -> when {
                            // Ordered most specific first. An unchanged crop is a precise statement
                            // about what the user just did and beats the generic advice.
                            state.cropUnchanged -> R.string.assist_body_unchanged
                            state.ineffectiveSelection -> R.string.assist_body_tighten
                            else -> R.string.assist_body
                        }
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewSize = it },
        ) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            val displayed = CropSelectionGeometry.displayedImageBounds(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
            )

            val tappable = step == AssistStep.PickingRow || step == AssistStep.PickingValue
            if (displayed.width > 0f && displayed.height > 0f && tappable) {
                val highlighted = if (step == AssistStep.PickingValue) allNumbers else rowCandidates
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ASSIST_OVERLAY_TAG)
                        .pointerInput(step, displayed, state.document) {
                            detectTapGestures { offset ->
                                // Screen -> source-image pixels. Without this the tap would be
                                // compared against boxes in a different coordinate space and would
                                // select whatever happened to be nearby, which is precisely the kind
                                // of invisible geometric error the crop mapping is tested against.
                                val scale = displayed.width / bitmap.width
                                if (scale <= 0f) return@detectTapGestures
                                val imageX = ((offset.x - displayed.left) / scale).toInt()
                                val imageY = ((offset.y - displayed.top) / scale).toInt()

                                when (step) {
                                    AssistStep.PickingRow -> {
                                        rowCandidates = AssistedSelection
                                            .candidatesOnRowAt(state.document, imageY)
                                        tappedRowText = AssistedSelection.rowTextAt(state.document, imageY)
                                        // One number on the tapped row is unambiguous, so skip a step.
                                        rowCandidates.singleOrNull()?.let {
                                            step = AssistStep.ConfirmingBasis(it.value, tappedRowText)
                                        }
                                    }
                                    AssistStep.PickingValue -> {
                                        allNumbers
                                            .filter { c ->
                                                imageX >= c.box.left && imageX <= c.box.right &&
                                                    imageY >= c.box.top && imageY <= c.box.bottom
                                            }
                                            .minByOrNull { it.box.width * it.box.height }
                                            ?.let { step = AssistStep.ConfirmingBasis(it.value, null) }
                                    }
                                    else -> Unit
                                }
                            }
                        },
                ) {
                    val scale = displayed.width / bitmap.width
                    highlighted.forEach { candidate ->
                        drawRect(
                            color = Color(0xFF4CAF50).copy(alpha = 0.85f),
                            topLeft = Offset(
                                displayed.left + candidate.box.left * scale,
                                displayed.top + candidate.box.top * scale,
                            ),
                            size = Size(candidate.box.width * scale, candidate.box.height * scale),
                            style = Stroke(width = 4f),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            when (val current = step) {
                AssistStep.Choosing -> {
                    Button(
                        onClick = { step = AssistStep.PickingRow },
                        shape = RoundedCornerShape(Space.buttonRadius),
                        modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                    ) { Text(stringResource(R.string.assist_pick_row)) }
                    OutlinedButton(
                        onClick = { step = AssistStep.PickingValue },
                        shape = RoundedCornerShape(Space.buttonRadius),
                        modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
                    ) { Text(stringResource(R.string.assist_pick_value)) }
                    OutlinedButton(
                        onClick = { step = AssistStep.TypingValue },
                        shape = RoundedCornerShape(Space.buttonRadius),
                        modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
                    ) { Text(stringResource(R.string.assist_type_it)) }
                    TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.crop_retake))
                    }
                }

                AssistStep.PickingRow -> {
                    // Several numbers on the tapped row: the user disambiguates. Restricted to that
                    // row, so an adjacent nutrient's figure is not in this list at all.
                    if (rowCandidates.size > 1) {
                        tappedRowText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            rowCandidates.take(4).forEach { candidate ->
                                OutlinedButton(
                                    onClick = {
                                        step = AssistStep.ConfirmingBasis(candidate.value, tappedRowText)
                                    },
                                    shape = RoundedCornerShape(Space.buttonRadius),
                                    modifier = Modifier.weight(1f).height(Space.minTouchTarget),
                                ) { Text(candidate.text) }
                            }
                        }
                    }
                    TextButton(onClick = { step = AssistStep.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_back))
                    }
                }

                AssistStep.PickingValue ->
                    TextButton(onClick = { step = AssistStep.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_back))
                    }

                is AssistStep.ConfirmingBasis -> {
                    Text(
                        text = stringResource(
                            // "per what?" is the wrong question when the label already said. The
                            // heading follows the basis, so a preserved one is stated rather than
                            // re-asked (1.0.3 P1).
                            if (statedBasis != null) R.string.assist_confirm_value_known
                            else R.string.assist_confirm_value,
                            current.value.stripTrailingZeros().toPlainString(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    BasisActions(
                        value = current.value,
                        statedBasis = statedBasis,
                        onUseValue = onUseValue,
                    )
                    TextButton(onClick = { step = AssistStep.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_back))
                    }
                }

                AssistStep.TypingValue -> {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { input ->
                            // Both separators, because the app is used where the comma is decimal.
                            if (input.length <= 6 && input.all { it.isDigit() || it == '.' || it == ',' }) {
                                typed = input
                            }
                        },
                        label = { Text(stringResource(R.string.assist_type_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(ASSIST_MANUAL_FIELD_TAG),
                    )
                    val parsed = typed.replace(',', '.').toBigDecimalOrNull()
                    if (parsed != null) {
                        BasisActions(
                            value = parsed,
                            statedBasis = statedBasis,
                            onUseValue = onUseValue,
                        )
                    }
                    TextButton(onClick = { step = AssistStep.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            }
        }
    }
}
