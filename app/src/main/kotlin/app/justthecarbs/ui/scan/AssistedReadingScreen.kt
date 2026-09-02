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
import androidx.compose.runtime.mutableIntStateOf
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
import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.DisputedCandidates
import app.justthecarbs.ocr.FocusedAmountEntry
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.RecoveryCandidates
import app.justthecarbs.ocr.StatedBasis
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

/** Test hooks; the tappable overlay and the inline field carry no text of their own. */
const val ASSIST_OVERLAY_TAG = "assist_overlay"
const val ASSIST_MANUAL_FIELD_TAG = "assist_manual_field"
const val ASSIST_FOCUSED_FIELD_TAG = "assist_focused_field"

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
    /**
     * Candidates a distinct recognition run contradicted, which recovery must not re-offer.
     *
     * Carried from the resolver rather than recomputed here: the disagreement is a fact about the
     * *evidence set*, and this screen only ever holds one document, so it could not detect one on
     * its own. That gap is what let `20260902-131511-970` refuse `89` as `Conflicted` and then offer
     * `89 g / 100 ml` as its first recovery choice.
     */
    val disputed: DisputedCandidates = DisputedCandidates.NONE,
    /**
     * True when the reading was withheld because its absolute decimal scale is not established.
     *
     * Distinct from every other reason this screen appears: the app *did* read a number and *did*
     * place it under a basis, and is declining to show it because a uniform decimal collapse is
     * equally consistent with the same pixels. The user is asked for the digits, not for the basis.
     */
    val scaleAmbiguous: Boolean = false,
)

/** Which step of the assisted flow the user is on. */
private sealed interface AssistStep {
    /** Choosing how to proceed. */
    data object Choosing : AssistStep

    /**
     * Choosing between the label's own basis-complete readings.
     *
     * Replaces the old "pick a number, then pick a basis" pair. Every choice here already knows what
     * it is measured per, which is what removes the step at which a bare value could acquire a
     * fabricated basis.
     */
    data object PickingLabelled : AssistStep

    /** Tapping the carbohydrate row (§18). */
    data object PickingRow : AssistStep

    /** Typing the value in, with the table still visible (§19). */
    data object TypingValue : AssistStep

    /**
     * Typing **only the amount**, for a label whose row and basis are already established (P1-1).
     *
     * Distinct from [TypingValue] in exactly one way, and it is the whole point: the basis is fixed
     * and cannot be changed here. This step is reachable only when the label itself stated the
     * basis and the classifier read it — so offering a `100 g or 100 ml?` picker would invite the
     * user to overwrite a fact the app got right with a guess, which is the composition that
     * produced `1.3 g / 100 ml` on a previous device recording.
     *
     * Someone who genuinely wants to supply both halves uses [TypingValue], which is still offered.
     */
    data object TypingFocusedAmount : AssistStep
}

/**
 * The accept actions for a value **the user typed** — the one place both 1.0.3 safety rules apply.
 *
 * ## Reachable only from the typing step, deliberately
 *
 * This is the last place in the app that asks *"per 100 g or per 100 ml?"*, and it is now reachable
 * only when the user has typed the figure themselves. That is the distinction the third phone
 * session made necessary: when the user reads a number off the package and types it, they are the
 * source of the data and are entitled to state its basis. When the app read the number, the app must
 * already know the basis — and if it does not, it may not ask, because the answer would attach to
 * whichever cell the user happened to tap rather than to the column that cell sits in.
 *
 * A device recording showed exactly that failure: the user tapped a `1.3` printed under *per 250 ml*
 * and this screen offered `/100 ml`, because [StatedBasis] had correctly established that the label
 * states per 100 ml somewhere. The fix is not a better question here; it is that OCR-derived choices
 * now arrive already carrying their basis. See
 * [app.justthecarbs.ocr.RecoveryCandidates].
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
 * The label's own readings, each stating what it is measured per.
 *
 * ## Every choice is basis-complete
 *
 * A choice reads `0.5 g / 100 ml`, `1.3 g / 250 ml`, `6 g / 18 g serving` — never a bare number.
 * That is the whole safety property: there is no moment at which a value exists without a basis, so
 * there is nothing for a later screen to supply one to. See
 * [app.justthecarbs.ocr.RecoveryCandidates] for how the list is built and what it refuses to
 * include.
 *
 * ## A converted figure says so
 *
 * When the label prints against something other than 100 g or 100 ml, the choice shows the printed
 * reading and, beneath it, what it becomes. `6 g / 18 g serving` becomes `33.3 g / 100 g`, and both
 * are on screen — the user is never shown a computed number as though the package had printed it.
 *
 * ## A figure that cannot be converted is shown, not offered
 *
 * A per-serving figure whose serving size the label never stated has no per-100 form, and this app
 * stores nothing else. Rather than dropping it silently — which would look like the app failing to
 * see a number that is plainly on the photograph — it is rendered as a disabled row with the reason.
 * That is the one place a non-action is better than an action, because the alternative is asking the
 * user to invent a serving size.
 */
@Composable
private fun LabelledChoices(
    candidates: List<RecoveryCandidates.Candidate>,
    onAccept: (RecoveryCandidates.Candidate) -> Unit,
) {
    candidates.forEach { candidate ->
        val derived = candidate.reading.normalizedToPerHundred()
        if (derived == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = stringResource(R.string.assist_unknown_serving),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            return@forEach
        }

        val converted = derived.derivedFrom != null
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Button(
                onClick = { onAccept(candidate) },
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
            ) {
                Text(
                    if (converted) {
                        // One formatter, not a local setScale. This line and the calculator that
                        // follows it were rounding the same derived figure two different ways —
                        // `33.3` here and `33.33333333` there — because each site decided for
                        // itself. See ResultFormatter.quantity.
                        "${ResultFormatter.quantity(derived.amount)} g / ${derived.basis.label}"
                    } else {
                        candidate.label
                    },
                )
            }
            if (converted) {
                Text(
                    text = stringResource(
                        R.string.assist_derived_from,
                        ResultFormatter.quantity(candidate.reading.amount),
                        candidate.reading.basis.label,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
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
 * reachable.
 *
 * ## The basis travels with the value, and is never asked for separately (third phone session)
 *
 * This screen used to work in two steps: pick a number, then pick a basis. A device recording showed
 * the cost. On a drink printing `0,5 g / 100 ml` beside `1,3 g / 250 ml`, the user tapped the `1.3`
 * and was offered `/100 ml` — because the *label* states per 100 ml, which is true and is not a fact
 * about the cell they tapped. Quick Calculation then showed `1.3 g carbs / 100 ml`, the original
 * 2.6x error arriving through the manual path after the automatic path had been fixed.
 *
 * Now every OCR-derived choice is built by [app.justthecarbs.ocr.RecoveryCandidates] already
 * carrying the basis of the column it sits in, and reads as `1.3 g / 250 ml`. Choosing it yields
 * `0.52 g / 100 ml` by conversion, never by relabelling. A cell in a column whose meaning was not
 * established is not offered at all, because there is no honest label for it.
 *
 * The one remaining place that asks about a basis is [BasisActions], reachable only after the user
 * has **typed** the figure — where they are the source of the data and are entitled to say what it
 * is measured per.
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
    var rowCandidates by remember { mutableStateOf<List<RecoveryCandidates.Candidate>>(emptyList()) }
    var tappedRowText by remember { mutableStateOf<String?>(null) }
    var tappedChildRow by remember { mutableStateOf(false) }

    /**
     * Set once a tap on a non-child row yielded no usable reading.
     *
     * Drives the focused-entry offer below, which is what stops the user repeating a tap that cannot
     * succeed. One is enough: the second identical tap teaches them nothing the first did not.
     */
    var fruitlessTap by remember { mutableStateOf(false) }

    /**
     * How many taps have landed on a child clause and been refused.
     *
     * The first is ordinary — the screen answers it with the "this appears to be sugars" message and
     * another tap is the right next action. A second one means the interaction is not working for
     * this label, so focused entry is offered rather than inviting a third identical attempt.
     */
    var childRowTaps by remember { mutableIntStateOf(0) }

    /**
     * What the label established even though its printed value was unreadable — the row and the
     * basis. Null when neither is safely known, in which case only retake and manual entry are
     * honest offers.
     */
    val focusedTarget = remember(state.document) { FocusedAmountEntry.of(state.document) }

    /**
     * Every basis-complete reading the label offers, built once from the document already on screen.
     *
     * Empty when the label established no basis anywhere — in which case there is nothing honest to
     * offer as a labelled choice and the screen falls back to tapping a row or typing the figure.
     */
    val labelled = remember(state.document, state.disputed) {
        RecoveryCandidates.of(state.document, state.disputed)
    }

    /**
     * The basis the label itself stated, when it stated exactly one (1.0.3 P1).
     *
     * Used **only** by the typing step now. It is a fact about the label, not about any particular
     * cell, which is precisely why it may no longer be attached to a number the user pointed at.
     */
    val statedBasis = remember(state.document) { StatedBasis.of(state.document) }

    /** Hands a chosen reading on, converting to per-100 when the label printed another quantity. */
    fun accept(candidate: RecoveryCandidates.Candidate) {
        val perHundred = candidate.reading.normalizedToPerHundred() ?: return
        val basis = (perHundred.basis as? CarbBasis.PerHundred)?.basis ?: return
        onUseValue(perHundred.amount, basis)
    }

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
                        AssistStep.PickingLabelled -> R.string.assist_choose_title
                        AssistStep.TypingValue -> R.string.assist_type_title
                        AssistStep.TypingFocusedAmount -> R.string.assist_focused_title
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
                        AssistStep.PickingLabelled -> R.string.assist_choose_body
                        AssistStep.TypingValue -> R.string.assist_type_body
                        AssistStep.TypingFocusedAmount -> R.string.assist_focused_body
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

            val tappable = step == AssistStep.PickingRow || step == AssistStep.PickingLabelled
            if (displayed.width > 0f && displayed.height > 0f && tappable) {
                val highlighted = if (step == AssistStep.PickingLabelled) labelled else rowCandidates
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
                                        // Reported before the candidates, so a child row can say why
                                        // it offers nothing rather than looking like a missed tap.
                                        // Both coordinates: on overlapping rows the horizontal
                                        // position is what says which word the finger was on, and
                                        // the label column does not overlap the value columns.
                                        tappedChildRow =
                                            RecoveryCandidates.isChildRowAt(state.document, imageY, imageX)
                                        // The dispute applies to the tap too. A value suppressed from
                                        // the labelled list must not come back because the user
                                        // pointed at the row it sits on — the reason it is withheld
                                        // is that a second recognition read it differently, and
                                        // where the finger landed says nothing about that.
                                        rowCandidates = RecoveryCandidates.onRowAt(
                                            state.document,
                                            imageY,
                                            imageX,
                                            state.disputed,
                                        )
                                        tappedRowText =
                                            RecoveryCandidates.rowTextAt(state.document, imageY, imageX)
                                        // One reading on the tapped row is unambiguous — it already
                                        // carries its basis, so there is no second question to ask.
                                        rowCandidates.singleOrNull()?.let(::accept)

                                        // A tap that produced nothing must change the screen.
                                        //
                                        // The recording shows the loop this closes: on the green
                                        // drink the carbohydrate row is found and its per-100-ml
                                        // column is established, but the printed value came back as
                                        // `0.59` (the unit glyph read as a digit) and is correctly
                                        // refused. The old screen offered no candidates, said
                                        // nothing new, and asked for the same tap again — which is
                                        // indistinguishable from a missed tap, so the user repeats
                                        // it.
                                        //
                                        // A child-clause tap is not itself a failure — it *did*
                                        // change the screen, which now says "this appears to be
                                        // sugars", and the right next action is another tap.
                                        //
                                        // But a second one is. The seventh session's recording shows
                                        // the user tapping the carbohydrate value, being told it
                                        // looked like sugars, going back, and finding *Type it in*
                                        // disabled — because this flag was the only thing that
                                        // offered focused entry and a child row could never set it.
                                        // Two unsuccessful taps means the row-tapping interaction is
                                        // not working for this label, whatever the reason, and the
                                        // user must be given a way forward rather than a third
                                        // identical attempt.
                                        if (rowCandidates.isEmpty()) {
                                            if (!tappedChildRow || childRowTaps > 0) fruitlessTap = true
                                            if (tappedChildRow) childRowTaps++
                                        }
                                    }
                                    AssistStep.PickingLabelled -> {
                                        labelled
                                            .filter { c ->
                                                imageX >= c.box.left && imageX <= c.box.right &&
                                                    imageY >= c.box.top && imageY <= c.box.bottom
                                            }
                                            .minByOrNull { it.box.width * it.box.height }
                                            ?.let(::accept)
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
            when (step) {
                AssistStep.Choosing -> {
                    // The labelled choices lead when the label established a basis anywhere. They
                    // are the only route that reaches Quick Calculation without the user having to
                    // state a basis themselves, so putting anything above them would send people
                    // down a longer path for no reason.
                    if (labelled.isNotEmpty()) {
                        LabelledChoices(labelled, ::accept)
                        OutlinedButton(
                            onClick = { step = AssistStep.PickingLabelled },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
                        ) { Text(stringResource(R.string.assist_pick_labelled)) }
                    }
                    Button(
                        onClick = { step = AssistStep.PickingRow },
                        shape = RoundedCornerShape(Space.buttonRadius),
                        modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                    ) { Text(stringResource(R.string.assist_pick_row)) }
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
                    // A child row supplies nothing, and says so. Without this the empty list below
                    // would read as a missed tap and the user would keep tapping the same row.
                    if (tappedChildRow) {
                        Text(
                            text = stringResource(R.string.assist_child_row),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // Several readings on the tapped row: the user disambiguates between them, each
                    // already carrying its own basis. Restricted to that row, so an adjacent
                    // nutrient's figure is not in this list at all.
                    if (rowCandidates.size > 1) {
                        tappedRowText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                        LabelledChoices(rowCandidates, ::accept)
                    }
                    // The escape from the loop (P1-1).
                    //
                    // Offered after a tap that found the row and could not read its number — which
                    // is the drink's exact state: the carbohydrate row is there, the per-100-ml
                    // column is established, and the printed value came back as `0.59`. Asking for
                    // that one number is the only thing left that can succeed, and repeating the tap
                    // is the one thing that cannot.
                    if (fruitlessTap && focusedTarget != null) {
                        Text(
                            text = stringResource(R.string.assist_focused_offer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Button(
                            onClick = { step = AssistStep.TypingFocusedAmount },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                        ) {
                            Text(
                                stringResource(
                                    R.string.assist_focused_action,
                                    focusedTarget.basis.unitLabel,
                                ),
                            )
                        }
                    }
                    TextButton(onClick = { step = AssistStep.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_back))
                    }
                }

                AssistStep.TypingFocusedAmount -> {
                    // Unreachable without a target — the only path here is the offer above, which is
                    // rendered only when one exists. Stated as a guard rather than a `!!` because a
                    // future caller adding another route must not be able to reach a screen that
                    // claims a basis it does not have.
                    if (focusedTarget == null) {
                        step = AssistStep.Choosing
                    } else {
                        Text(
                            text = stringResource(
                                R.string.assist_focused_prompt,
                                focusedTarget.basis.unitLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { input ->
                                if (input.length <= 6 &&
                                    input.all { it.isDigit() || it == '.' || it == ',' }
                                ) {
                                    typed = input
                                }
                            },
                            label = { Text(stringResource(R.string.assist_type_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(ASSIST_FOCUSED_FIELD_TAG),
                        )
                        val parsed = typed.replace(',', '.').toBigDecimalOrNull()
                        // The plausibility barrier still applies. A basis the label stated does not
                        // exempt a figure from being impossible under it.
                        if (parsed != null && CarbPlausibility.isPlausiblePer100(parsed, focusedTarget.basis)) {
                            Button(
                                onClick = { onUseValue(parsed, focusedTarget.basis) },
                                shape = RoundedCornerShape(Space.buttonRadius),
                                modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.assist_focused_confirm,
                                        ResultFormatter.quantity(parsed),
                                        focusedTarget.basis.unitLabel,
                                    ),
                                )
                            }
                        } else if (parsed != null) {
                            Text(
                                text = stringResource(R.string.assist_value_implausible),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = { step = AssistStep.Choosing },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_back)) }
                    }
                }

                AssistStep.PickingLabelled -> {
                    LabelledChoices(labelled, ::accept)
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
