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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.ocr.CorrectionFieldState
import app.justthecarbs.ocr.CropSelectionGeometry
import app.justthecarbs.ocr.DisputedCandidates
import app.justthecarbs.ocr.FocusedAmountEntry
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.RecoveryCandidates
import app.justthecarbs.ocr.StatedBasis
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import java.math.BigDecimal

/** Test hooks; the tappable overlay and the inline field carry no text of their own. */
const val ASSIST_OVERLAY_TAG = "assist_overlay"
const val ASSIST_MANUAL_FIELD_TAG = "assist_manual_field"
const val ASSIST_FOCUSED_FIELD_TAG = "assist_focused_field"
const val ASSIST_CORRECTION_FIELD_TAG = "assist_correction_field"
const val ASSIST_CORRECTION_SUBMIT_TAG = "assist_correction_submit"
const val ASSIST_CORRECTION_HIGHLIGHT_TAG = "assist_correction_highlight"

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
    /**
     * Open directly on focused amount entry, because the row and basis are already established.
     *
     * ## The capture this exists for (thirteenth session)
     *
     * `20260904-081307-240` printed `Koolhydraten 6,2 g` in large, flat type. The parser found the
     * total-carbohydrate row, resolved the `Ø/100 ml` column, and declined only the value cell — the
     * printed `g` had been recognised as a `0`, giving `6,20`. Everything except the digits was
     * known, and the app opened the **crop** screen: a rectangle the user could not usefully change,
     * over a row the app had already located.
     *
     * When [ScanPresentationDecision] routes a capture here it has established, through
     * [FocusedAmountEntry.of], that exactly one per-100 basis and one carbohydrate row exist. So the
     * screen skips the *"tap the row / choose / type it in"* menu and asks the one question that
     * remains. There is no basis picker on that step, deliberately: the label stated the basis and
     * the app read it, so offering a choice would invite a guess to overwrite a fact.
     *
     * It changes no rule and reads no value — it selects the first screen.
     */
    val startOnFocusedEntry: Boolean = false,
    /**
     * Open directly on correcting a **known** row/basis whose displayed figure the user just
     * rejected — never the generic "tap the row / choose / type it in" menu.
     *
     * ## Why this exists (UX-reduction pass)
     *
     * Before a rejection landed here, the app already knew this is the total-carbohydrate reading,
     * its basis, its row geometry, the displayed (wrong) value and the frozen photograph — it read
     * all of that to build the very proposal the user just declined. Falling back to generic
     * [AssistState] threw all of it away and made the user re-identify the row from scratch, tap
     * their way through a menu, or worse, land in full manual product entry with a blank basis
     * picker for a basis the app already knew.
     *
     * Set only from a rejection of [VerificationScreen] (either [VerificationScreenMode.OcrProposal]
     * or [VerificationScreenMode.ScaleUnresolved]) — never from a fresh automatic-attempt failure,
     * where no specific value was ever shown to reject.
     */
    val correctionTarget: CorrectionTarget? = null,
)

/**
 * Everything [AssistStep.CorrectingKnownAmount] needs to let the user fix a rejected figure without
 * re-establishing the row or basis it was already read from.
 *
 * [rejectedValue]/[basis] are the figure and basis the user just declined — [basis] is fixed for the
 * whole step (task §6: "no basis picker"), and [rejectedValue] is what [CorrectionFieldState] compares
 * a typed correction against so an unedited resubmission cannot be mistaken for a correction.
 */
data class CorrectionTarget(
    val rejectedValue: BigDecimal,
    val basis: NutritionBasis,
    val rowText: String,
    val rowInSourceSpace: OcrBox,
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

    /**
     * Correcting a figure the user just rejected on [VerificationScreen] — the row, basis, row
     * geometry and rejected value are all already known (task §5/§6).
     *
     * Distinct from [TypingFocusedAmount], which is reached when the app never had a value to
     * propose in the first place (it read the row and basis but the *digits* were unreadable). Here
     * the app DID propose digits and the user said they were wrong, so the initial field content is
     * the rejected value itself — selected for immediate replacement, never resubmittable unchanged
     * (see [app.justthecarbs.ocr.CorrectionFieldState]).
     */
    data object CorrectingKnownAmount : AssistStep
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        offered.forEach { basis ->
            Button(
                onClick = { onUseValue(value, basis) },
                modifier = Modifier.weight(1f).heightIn(min = Space.minTouchTarget),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.assist_unknown_serving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@forEach
        }

        val converted = derived.derivedFrom != null
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Button(
                onClick = { onAccept(candidate) },
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    // The first screen. [AssistState.correctionTarget] takes priority over
    // [AssistState.startOnFocusedEntry]: both mean the row and basis are already known, but a
    // correction target additionally carries a specific rejected VALUE, which is the stronger claim
    // — the app proposed digits and the user said they were wrong, so there is even less to ask than
    // in the focused-entry case, where the app never had digits to propose at all.
    // `remember` with no key: this selects the *initial* step, and the user's own navigation within
    // the screen must not be undone by a recomposition.
    var step by remember {
        mutableStateOf<AssistStep>(
            when {
                state.correctionTarget != null -> AssistStep.CorrectingKnownAmount
                state.startOnFocusedEntry -> AssistStep.TypingFocusedAmount
                else -> AssistStep.Choosing
            },
        )
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var typed by remember { mutableStateOf("") }
    // Pre-filled with the rejected value so the user sees exactly what they are correcting, but
    // tracked separately from whether it has actually been EDITED — see CorrectionFieldState, which
    // is what stops an unedited resubmission being treated as though the rejection never happened.
    var correctionTyped by remember(state.correctionTarget) {
        mutableStateOf(state.correctionTarget?.rejectedValue?.let { ResultFormatter.quantity(it) } ?: "")
    }
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

    val scannerColors = MaterialTheme.extendedColors.scanner

    // ## Black is the photo's ground, not the screen's (thirteenth pass)
    //
    // This whole screen used to be `.background(Color.Black)` with `Color.White` text scattered
    // through it and Material controls — `OutlinedTextField`, `Button`, helper and error text —
    // left on their theme defaults. In **Light** theme those defaults are dark-on-light, so the
    // field's label, outline, cursor and typed digits rendered dark grey on black. That is the
    // low-contrast focused-entry state the owner reported, and it is worst on precisely the screen
    // that exists for the user to type a number they can read.
    //
    // The fix is structural rather than a repaint: black is kept where it belongs — behind the
    // photograph, where it is the correct ground for a label image — and the interactive half
    // becomes an ordinary Material [Surface]. Every control inside it then sits on
    // `colorScheme.surface` with `onSurface` content, which is what their defaults already assume,
    // in both themes and with no hardcoded colour at all.
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
                text = stringResource(
                    when (step) {
                        AssistStep.PickingRow -> R.string.assist_tap_row_title
                        AssistStep.PickingLabelled -> R.string.assist_choose_title
                        AssistStep.TypingValue -> R.string.assist_type_title
                        AssistStep.TypingFocusedAmount -> R.string.assist_focused_title
                        AssistStep.CorrectingKnownAmount -> R.string.assist_correct_title
                        AssistStep.Choosing -> R.string.assist_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    when (step) {
                        AssistStep.PickingRow -> R.string.assist_tap_row_body
                        AssistStep.PickingLabelled -> R.string.assist_choose_body
                        AssistStep.TypingValue -> R.string.assist_type_body
                        AssistStep.TypingFocusedAmount -> R.string.assist_focused_body
                        AssistStep.CorrectingKnownAmount -> R.string.assist_correct_body
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The photograph's own ground. `ContentScale.Fit` letterboxes a 1684x3648 capture in
                // almost any viewport, and black is the right neutral behind a label image in both
                // themes — a cream letterbox would compete with the package.
                .background(Color.Black)
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
                        val topLeft = Offset(
                            displayed.left + candidate.box.left * scale,
                            displayed.top + candidate.box.top * scale,
                        )
                        val size = Size(candidate.box.width * scale, candidate.box.height * scale)
                        // Shape plus a dark/light double edge remains identifiable on white tables,
                        // black packages and saturated photography. Blue means selection, not
                        // success; the rectangle itself carries the state without colour alone.
                        drawRect(scannerColors.darkEdge, topLeft, size, style = Stroke(width = 8f))
                        drawRect(scannerColors.selection, topLeft, size, style = Stroke(width = 4f))
                    }
                }
            }

            // A static highlight of the already-known row, for the correction step. Never tappable
            // — there is nothing left to tap for; the row is established and only the digits need
            // fixing — so this is a plain overlay, not a `pointerInput` surface.
            val correctionTarget = state.correctionTarget
            if (displayed.width > 0f && displayed.height > 0f &&
                step == AssistStep.CorrectingKnownAmount && correctionTarget != null
            ) {
                val scale = displayed.width / bitmap.width
                Canvas(modifier = Modifier.fillMaxSize().testTag(ASSIST_CORRECTION_HIGHLIGHT_TAG)) {
                    val topLeft = Offset(
                        displayed.left + correctionTarget.rowInSourceSpace.left * scale,
                        displayed.top + correctionTarget.rowInSourceSpace.top * scale,
                    )
                    val size = Size(
                        correctionTarget.rowInSourceSpace.width * scale,
                        correctionTarget.rowInSourceSpace.height * scale,
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
                .padding(Space.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            when (step) {
                AssistStep.Choosing -> {
                    // ## A withheld reading leads with focused entry (eleventh session)
                    //
                    // `20260903-142926-419`: the Lidl cracker prints `72,0 g / 100 g`, ML Kit
                    // landed on its Spanish row and returned `72g` with the separator gone, and
                    // [ReadingEligibility] refused it — correctly, and by the same rule that keeps
                    // the red label's `12` out.
                    //
                    // What was wrong is only what came next. A withheld value is by definition
                    // absent from `labelled`, so this screen offered *Tap the carbohydrate row* and
                    // *Type it in*, and focused entry — the one screen that already knew the row
                    // **and** the basis — was reachable only after a tap had been made and found
                    // fruitless. The app established both facts before the screen was drawn;
                    // making the user discover that by failing is the dead-end shape this whole
                    // screen exists to remove.
                    //
                    // **Gated on `scaleAmbiguous`, not offered universally.** On an ordinary failed
                    // read nothing was established, so an offer to type "the value printed under
                    // 100 g" would name a basis the app never read — and there the tap genuinely is
                    // the user's lever, so it stays first. `focusedTarget` is required as well
                    // because the flag says a value was withheld, not that a target exists.
                    //
                    // No digits travel with it: the withheld number is exactly what the app
                    // declined to stand behind, so prefilling it would re-propose it with the app's
                    // authority attached.
                    if (state.scaleAmbiguous && focusedTarget != null) {
                        Text(
                            text = stringResource(R.string.assist_focused_offer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = { step = AssistStep.TypingFocusedAmount },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                        ) {
                            Text(
                                stringResource(
                                    R.string.assist_focused_action,
                                    focusedTarget.basis.unitLabel,
                                ),
                            )
                        }
                    }
                    // The labelled choices lead when the label established a basis anywhere. They
                    // are the only route that reaches Quick Calculation without the user having to
                    // state a basis themselves, so putting anything above them would send people
                    // down a longer path for no reason.
                    if (labelled.isNotEmpty()) {
                        LabelledChoices(labelled, ::accept)
                        OutlinedButton(
                            onClick = { step = AssistStep.PickingLabelled },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
                        ) { Text(stringResource(R.string.assist_pick_labelled)) }
                    }
                    // Tapping a row stays available and stays *primary* in the ordinary case. It is
                    // demoted only when focused entry is offered above, so the screen has one
                    // primary action rather than two competing ones — and tapping is genuinely the
                    // weaker route there, since the row it would identify is already known.
                    val focusedEntryLeads = state.scaleAmbiguous && focusedTarget != null
                    if (focusedEntryLeads) {
                        OutlinedButton(
                            onClick = { step = AssistStep.PickingRow },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
                        ) { Text(stringResource(R.string.assist_pick_row)) }
                    } else {
                        Button(
                            onClick = { step = AssistStep.PickingRow },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                        ) { Text(stringResource(R.string.assist_pick_row)) }
                    }
                    OutlinedButton(
                        onClick = { step = AssistStep.TypingValue },
                        shape = RoundedCornerShape(Space.buttonRadius),
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget),
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = { step = AssistStep.TypingFocusedAmount },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                        ) {
                            Text(
                                stringResource(
                                    R.string.assist_focused_action,
                                    focusedTarget.basis.unitLabel,
                                ),
                            )
                        }
                    } else if (fruitlessTap) {
                        // A fruitless tap with no `focusedTarget` means the row is not established
                        // through a resolved per-100 column (a declared-serving or linear-panel
                        // shape, say) -- so there is no fixed basis to preserve, and the previous
                        // behaviour left the user with nothing but "Back". Full manual entry is the
                        // one screen that still applies here: the user supplies both the amount and
                        // the basis, exactly as the ordinary "Type it in" path already does.
                        Text(
                            text = stringResource(R.string.assist_row_unclear),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = { step = AssistStep.TypingValue },
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                        ) { Text(stringResource(R.string.assist_type_it)) }
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                                modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
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

                AssistStep.CorrectingKnownAmount -> {
                    val target = state.correctionTarget
                    // Unreachable without a target — the only path here is a rejection carrying one.
                    // Same defensive shape as TypingFocusedAmount above, for the same reason: a
                    // future caller adding another route must not be able to reach a screen that
                    // claims a row/basis it does not have.
                    if (target == null) {
                        step = AssistStep.Choosing
                    } else {
                        val focusManager = LocalFocusManager.current
                        val focusRequester = remember { FocusRequester() }
                        var fieldValue by remember {
                            mutableStateOf(
                                TextFieldValue(
                                    text = correctionTyped,
                                    selection = TextRange(0, correctionTyped.length),
                                ),
                            )
                        }
                        // Auto-focus and select-all fire once, on entering this step — not on every
                        // recomposition, which would fight the user's own caret placement mid-edit.
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }

                        fun trySubmit() {
                            CorrectionFieldState.submittableAmount(
                                typed = fieldValue.text,
                                rejectedValue = target.rejectedValue,
                                basis = target.basis,
                            )?.let { amount ->
                                focusManager.clearFocus()
                                onUseValue(amount, target.basis)
                            }
                        }

                        Text(
                            text = stringResource(R.string.assist_correct_row, target.rowText.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val submittable = CorrectionFieldState.submittableAmount(
                            typed = fieldValue.text,
                            rejectedValue = target.rejectedValue,
                            basis = target.basis,
                        )
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { input ->
                                if (input.text.length <= 6 &&
                                    input.text.all { it.isDigit() || it == '.' || it == ',' }
                                ) {
                                    fieldValue = input
                                    correctionTyped = input.text
                                }
                            },
                            label = { Text(stringResource(R.string.assist_correct_label, target.basis.unitLabel)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            // The same submit function the visible button calls below — task §6's
                            // explicit requirement that keyboard and button paths never carry two
                            // copies of validation.
                            keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                            singleLine = true,
                            isError = fieldValue.text.isNotBlank() && submittable == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag(ASSIST_CORRECTION_FIELD_TAG),
                        )
                        if (fieldValue.text.isNotBlank() && submittable == null) {
                            Text(
                                text = stringResource(R.string.assist_value_implausible),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Kept as a visible, always-present action for accessibility and
                        // discoverability (task §6) even though IME Done already submits — disabled
                        // rather than hidden while nothing submittable exists, so TalkBack and a
                        // pointer user both see the same control in the same place regardless of
                        // input method.
                        Button(
                            onClick = ::trySubmit,
                            enabled = submittable != null,
                            shape = RoundedCornerShape(Space.buttonRadius),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Space.primaryButtonHeight)
                                .testTag(ASSIST_CORRECTION_SUBMIT_TAG),
                        ) {
                            Text(
                                if (submittable != null) {
                                    stringResource(
                                        R.string.assist_focused_confirm,
                                        ResultFormatter.quantity(submittable),
                                        target.basis.unitLabel,
                                    )
                                } else {
                                    stringResource(R.string.assist_focused_title)
                                },
                            )
                        }
                        TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.crop_retake))
                        }
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
