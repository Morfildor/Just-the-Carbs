package app.justthecarbs.ui.meal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.PortionAdjustment
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.ui.components.PortionAdjustRail
import app.justthecarbs.ui.components.ResultValue
import app.justthecarbs.ui.components.fadeOutWhenMoreBelow
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Stable handles for instrumented tests. */
const val MEAL_EDITOR_TAG = "meal_editor"
const val MEAL_EDITOR_FIELD_TAG = "meal_editor_field"
const val MEAL_EDITOR_RESULT_TAG = "meal_editor_result"
const val MEAL_EDITOR_SAVE_TAG = "meal_editor_save"
const val MEAL_EDITOR_CANCEL_TAG = "meal_editor_cancel"

/**
 * Correcting one meal line: a compact sheet over the meal, never a trip back to the product (1.0.8).
 *
 * Reads top to bottom as the correction itself — which line, the amount being changed, the carbs
 * it now holds, Save — with the carbohydrate figure the largest thing on it, as it is on every
 * screen. Everything shown is taken from the line's own snapshot: the per-100 figure or per-unit
 * carbs the line was calculated with, not the product as it is today.
 *
 * Every way out animates the sheet away before the meal is told: Cancel and a successful save
 * here, and a swipe, a scrim tap or Back through [ModalBottomSheet] itself. A save is confirmed by
 * touch and by the corrected row and total, never by a Snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealItemEditorSheet(
    editor: MealItemEditor,
    settings: AppSettings,
    onAmountChange: (String) -> Unit,
    /** One of the quick-adjust rail's accelerators, applied to the amount being corrected (1.0.8). */
    onAdjust: (PortionAdjustment.Operation) -> Unit,
    onSave: (BigDecimal, String) -> Unit,
    /** The sheet has gone without saving. */
    onDismiss: () -> Unit,
    /** The sheet has gone after its correction was written. */
    onSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val cancel: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        EditorContent(
            editor = editor,
            settings = settings,
            sheetState = sheetState,
            onAmountChange = onAmountChange,
            onAdjust = onAdjust,
            onSave = onSave,
            onCancel = cancel,
            onSaved = onSaved,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    editor: MealItemEditor,
    settings: AppSettings,
    sheetState: SheetState,
    onAmountChange: (String) -> Unit,
    onAdjust: (PortionAdjustment.Operation) -> Unit,
    onSave: (BigDecimal, String) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val item = editor.item
    val direct = item.kind == MealItemKind.DIRECT_CARBS
    val amount = editor.amount
    // Built from the new amount alone, in the same composition that shows it, and handed to Save
    // with that amount — so the words written can never describe a different number.
    val description = amount?.let { correctedPortionDescription(item, it) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    // Saved: the write has landed. Confirm by touch, drop the keyboard and the sheet together, and
    // let the meal show the corrected line. Read inside the sheet's own composition, which is where
    // its focused field and keyboard live.
    val saved = editor.status == MealEditStatus.SAVED
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(saved) {
        if (!saved) return@LaunchedEffect
        if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        focusManager.clearFocus()
        sheetState.hide()
        currentOnSaved()
    }

    val save = {
        if (editor.canSave && amount != null && description != null) onSave(amount, description)
    }

    // Measured against the room the sheet actually has. On a small screen with the keyboard up that
    // is a few hundred dp, and the compact arrangement gives back spacing and the secondary
    // whole-gram line rather than letting something that matters fall off the bottom.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxHeight < COMPACT_SHEET_HEIGHT
        val blockGap = if (compact) Space.s else Space.l

        // Whether the accelerator rail is worth its height on this device.
        //
        // Keyed on the **window**, not on this sheet and not on the keyboard. Two measurements
        // rule the obvious alternatives out:
        //
        //  - The sheet's own `maxHeight` settles at about 437dp here whatever the phone, because a
        //    bottom sheet is content-sized. Gating on it (via `compact`) hid the rail on every
        //    device, always — a defect the suite could not see, since nothing asserted the rail
        //    was present.
        //  - The keyboard is *always* up on this sheet: the field takes focus on arrival, which is
        //    the whole point of a sheet that exists to take one number. So "is the keyboard up"
        //    is a constant here and cannot decide anything either.
        //
        // What actually varies is how much screen the phone has. On a short window the figure
        // being corrected must keep its room and the rail gives way; the keyboard is right there
        // and typing costs one extra tap. On an ordinary phone both fit.
        // `LocalWindowInfo.containerSize` rather than `Configuration.screenHeightDp`: it reports
        // the window this composition is actually in (correct in split screen and in a freeform
        // window, where the configuration's screen height is not), and it is what lint's
        // ConfigurationScreenWidthHeight rule asks for.
        val windowHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        val showRail = windowHeight >= RAIL_MIN_WINDOW_HEIGHT

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MEAL_EDITOR_TAG)
                .padding(start = Space.screenEdge, end = Space.screenEdge, bottom = Space.m),
        ) {
            // **Only what names the line scrolls.** The amount, the carbohydrate figure it produces and
            // the actions are pinned below, because those three are the correction: a short screen may
            // take the product name out of view, never the number being read. Found the other way round
            // on a 320x640dp screen with the keyboard up, where the figure was the part that went.
            //
            // The fade goes before `verticalScroll` so it decorates the viewport rather than the
            // scrolling content — see its own KDoc for why that ordering is the whole trick.
            val contentScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fadeOutWhenMoreBelow(contentScroll)
                    .verticalScroll(contentScroll),
            ) {
                Text(
                    text = item.displayName,
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.xs))
                // The line as it stands — the "before" — so the figure below reads as its replacement.
                // Its wording is shown as stored and never parsed: it is what the line says, not data.
                Text(
                    text = stringResource(
                        R.string.meal_edit_current,
                        item.portionDescription,
                        ResultFormatter.decimal(item.exactCarbs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(blockGap))

            AmountField(
                value = editor.amountText,
                // A weighed line is corrected in its own unit, g or ml, exactly as it was calculated.
                // A count has no unit word to show: the line does not record whether it was slices.
                unit = if (direct) null else item.basis?.unitLabel,
                accessibleLabel = if (direct) {
                    stringResource(R.string.meal_edit_count_field, perUnitCarbs(item))
                } else {
                    stringResource(R.string.product_portion_label, item.basis?.unitLabel.orEmpty())
                },
                isError = editor.invalid,
                compact = compact,
                onValueChange = onAmountChange,
                onDone = { if (editor.canSave) save() else focusManager.clearFocus() },
            )
            // The same accelerators the calculator offers, stepping by the unit this line is in:
            // ten for a weighed line, one for a count. The step is fixed rather than package-scaled
            // here because a meal line carries no package size — it is a snapshot of an amount, not
            // of the product — and inventing one would be the guessed shortcut §14 rules out.
            //
            // **Dropped only when the keyboard is up on a genuinely short sheet**, which is the
            // one place the rail must give way: there the figure being corrected must keep its
            // room, and an accelerator that saves a keystroke is not worth the number it would
            // push off the bottom — the keyboard is right there anyway.
            //
            // Keyed on `showRail` rather than on `!compact`, and that distinction was measured
            // rather than reasoned about. `BoxWithConstraints` inside a `ModalBottomSheet` reports
            // the *sheet's own* height, which is content-sized and settles at about 437dp for this
            // sheet — under the 460dp `compact` threshold, which was chosen for the keyboard-open
            // case. Gating the rail on `!compact` therefore hid it **always**, on every screen
            // size, with the suite green because nothing asserted the rail was there. Logged the
            // constraint to find it: 596 → 635 → 620 → 519 → 467 → 437dp as the sheet settled.
            if (showRail) {
                Spacer(Modifier.height(Space.s))
                PortionAdjustRail(
                    step = if (direct) BigDecimal.ONE else BigDecimal(WEIGHT_STEP),
                    onAdjust = onAdjust,
                    hapticsEnabled = settings.hapticsEnabled,
                )
            }

            Spacer(Modifier.height(Space.xs))
            // What each gram or each counted unit contributes — the snapshot's own figure, so a
            // product corrected since this line was added cannot quietly change the arithmetic.
            Text(
                text = if (direct) {
                    stringResource(R.string.meal_edit_per_unit, perUnitCarbs(item))
                } else {
                    stringResource(
                        R.string.product_per_100,
                        ResultFormatter.quantity(requireNotNull(item.carbsPer100)),
                        item.basis?.unitLabel.orEmpty(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(blockGap))

            EditorResult(editor = editor, settings = settings, direct = direct, compact = compact)

            if (editor.status == MealEditStatus.FAILED) {
                Spacer(Modifier.height(Space.s))
                // Said beside the action that failed, and politely announced: a failed write that
                // changed nothing on screen reads as a missed tap, and the answer to that is another tap
                // that fails the same way.
                Text(
                    text = stringResource(R.string.meal_edit_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(Modifier.height(blockGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier
                        .heightIn(min = Space.primaryButtonHeight)
                        .testTag(MEAL_EDITOR_CANCEL_TAG),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                // Enabled only for a real correction: a valid amount that differs from the line's. An
                // unchanged amount has nothing to save, and an invalid one must never reach the meal.
                Button(
                    onClick = save,
                    enabled = editor.canSave,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.extendedColors.disabledButton,
                        disabledContentColor = MaterialTheme.extendedColors.onDisabledButton,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Space.primaryButtonHeight)
                        .testTag(MEAL_EDITOR_SAVE_TAG),
                ) {
                    Text(stringResource(R.string.product_save))
                }
            }
        }
    }
}

/**
 * The amount being corrected, in the calculator's own type.
 *
 * Opens focused with the whole value selected, so the first keystroke replaces it: correcting 72 to
 * 85 is typing "85", not deleting two digits first. Held as a [TextFieldValue] for that selection
 * only — the text itself belongs to the caller.
 */
@Composable
private fun AmountField(
    value: String,
    unit: String?,
    accessibleLabel: String,
    isError: Boolean,
    /** A short sheet: the amount is set smaller so the figure it produces keeps its room. */
    compact: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val amountStyle = if (compact) NumberType.portion.copy(fontSize = 36.sp, lineHeight = 40.sp) else NumberType.portion
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    var hadFocus by remember { mutableStateOf(false) }

    // Once, on arrival: the sheet exists to take this one number.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        textStyle = amountStyle,
        singleLine = true,
        isError = isError,
        // Decimal keypad: amounts and counts both take decimals, and PortionParser reads `.` and `,`
        // alike, whatever the device's locale writes. Done saves when there is something to save.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        placeholder = {
            Text(
                text = "0",
                style = amountStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
            )
        },
        suffix = unit?.let { { Text(text = it, style = MaterialTheme.typography.titleMedium) } },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                // Only on the way into focus, so later edits keep the caret where the user put it.
                if (focus.isFocused && !hadFocus) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
                hadFocus = focus.isFocused
            }
            .semantics { contentDescription = accessibleLabel }
            .testTag(MEAL_EDITOR_FIELD_TAG),
    )
}

/** What the result slot holds: a figure, a request for one, or why there cannot be one. */
private sealed interface Slot {
    data class Figure(val dominant: String) : Slot
    data object Waiting : Slot
    data object Invalid : Slot
}

/**
 * The carbohydrate the typed amount holds — the largest thing on the sheet, in the result's colour
 * and type, exactly as the calculator shows it.
 *
 * The slot keeps one height whatever it holds, so the sheet does not move while the user types
 * through an empty or unusable value on the way to a real one.
 */
@Composable
private fun EditorResult(
    editor: MealItemEditor,
    settings: AppSettings,
    direct: Boolean,
    compact: Boolean,
) {
    val exact = editor.exactCarbs
    val slot = when {
        exact != null -> Slot.Figure(
            when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> ResultFormatter.decimal(exact)
                ResultStyle.WHOLE_DOMINANT -> ResultFormatter.whole(ResultFormatter.wholeGrams(exact))
            },
        )
        editor.invalid -> Slot.Invalid
        else -> Slot.Waiting
    }
    val resultUnit = stringResource(R.string.result_unit_grams)

    Text(
        text = stringResource(R.string.product_result_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Space.xs))
    // Top-aligned, not centred: the figure fills the slot, but a one-line message does not, and
    // centring left it floating halfway down with a gap under the label it belongs to.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) COMPACT_RESULT_SLOT_HEIGHT else RESULT_SLOT_HEIGHT),
        contentAlignment = Alignment.TopStart,
    ) {
        AnimatedContent(
            targetState = slot,
            transitionSpec = { fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)) },
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.fillMaxWidth(),
            label = "mealEditResult",
        ) { shown ->
            when (shown) {
                is Slot.Figure -> ResultValue(
                    dominant = shown.dominant,
                    unit = resultUnit,
                    accessibleLabel = stringResource(R.string.result_accessible_grams, shown.dominant),
                    testTag = MEAL_EDITOR_RESULT_TAG,
                    modifier = Modifier.fillMaxWidth(),
                )
                Slot.Invalid -> Text(
                    text = stringResource(R.string.meal_edit_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Slot.Waiting -> Text(
                    text = stringResource(
                        if (direct) R.string.product_result_pending_count else R.string.product_result_pending,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // Dropped on a short sheet: it restates the dominant figure in other words, so it is the first
    // thing that can go when the room it takes is needed by the figure itself.
    if (compact) return
    // Always one line tall, so the sheet does not shorten when there is no figure to support.
    Text(
        text = when {
            exact == null -> ""
            settings.resultStyle == ResultStyle.DECIMAL_DOMINANT -> stringResource(
                R.string.product_result_whole,
                ResultFormatter.whole(ResultFormatter.wholeGrams(exact)),
            )
            else -> stringResource(R.string.product_result_calculated, ResultFormatter.decimal(exact))
        },
        style = NumberType.supporting,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = 1,
    )
}

/** The per-unit carbohydrate of a direct-carb line, every digit, as the calculator writes it. */
private fun perUnitCarbs(item: MealItem): String =
    ResultFormatter.editable(requireNotNull(item.carbsPerUnit) { "a direct-carb line has carbs per unit" })

/**
 * A corrected line's wording, built from its new amount and nothing else: `85 g`, `240 ml`,
 * `3 × 14.2 g carbs`.
 *
 * The old wording is never kept or parsed. A weighed line added as "2 slices" is now "105 g",
 * because the snapshot records the grams and not the slice; a direct-carb line says what it
 * structurally is — a count of units of known carbohydrate — without claiming a unit word it does
 * not have.
 */
@Composable
private fun correctedPortionDescription(item: MealItem, amount: BigDecimal): String = when (item.kind) {
    MealItemKind.WEIGHT_BASED ->
        "${ResultFormatter.editable(amount)} ${requireNotNull(item.basis) { "a weighed line has a basis" }.unitLabel}"
    MealItemKind.DIRECT_CARBS ->
        stringResource(R.string.meal_item_direct_portion, ResultFormatter.editable(amount), perUnitCarbs(item))
}

/**
 * The window height at which this sheet has room to **spare** for the accelerator rail.
 *
 * Deliberately well above the height at which the rail merely fits. Measured on a 731dp window (a
 * 1080x1920 device at 420dpi): the rail renders, but the scrolling product name and the
 * "Currently …" line are then intermittently clipped — they are the only give in a sheet whose
 * pinned content already costs about 380dp (a 96dp result slot, the large amount field, a 56dp
 * action row and the gaps) of its ~437dp. Both failure modes were seen while trying to place it:
 * pinning the "Currently" line simply moved the clipping onto the product name.
 *
 * A control that sometimes costs you the line you are correcting *against* is worse than one that
 * is not offered, so the bar sits where the sheet is comfortably taller than its content.
 *
 * The consequence is stated rather than hidden: **on an ordinary phone this sheet has no rail**,
 * and a meal line is corrected by typing, as before 1.0.8. The calculator — a whole screen rather
 * than a sheet — is where the accelerators live. This is the measured limit of "reuse the pattern
 * where technically appropriate", not an oversight.
 */
private val RAIL_MIN_WINDOW_HEIGHT = 900.dp

/**
 * The rail's step for a weighed meal line, in the line's own unit (g or ml).
 *
 * Ten, matching the calculator's default step for a product whose package size was never read —
 * which is the same situation this sheet is always in, since a meal line stores an amount and not
 * a package. Deliberately not derived from the amount itself: a step that changed as the user
 * adjusted would move the ground under repeated taps.
 */
private const val WEIGHT_STEP = 10

/** The result's reserved height — the calculator's own, so the figure sits in the same frame. */
private val RESULT_SLOT_HEIGHT = 96.dp

/** The same slot with less room to give: still far larger than anything else on the sheet. */
private val COMPACT_RESULT_SLOT_HEIGHT = 72.dp

/**
 * Below this much room, the sheet uses its compact arrangement.
 *
 * Measured rather than guessed: a 320x640dp screen with the keyboard up leaves the sheet about
 * 370dp, and the full arrangement wants about 490dp of it.
 */
private val COMPACT_SHEET_HEIGHT = 460.dp
