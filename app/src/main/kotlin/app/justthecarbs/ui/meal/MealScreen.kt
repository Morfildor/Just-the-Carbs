package app.justthecarbs.ui.meal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.PortionAdjustment
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.components.AccentBackdrop
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.CopyResultButton
import app.justthecarbs.ui.components.ResultValue
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.accent
import app.justthecarbs.ui.theme.Space
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Stable handles for instrumented tests. */
const val MEAL_TOTAL_TAG = "meal_total"

/** The meal total's copy-to-clipboard action. */
const val MEAL_COPY_TAG = "meal_copy"
const val MEAL_CLEAR_TAG = "meal_clear"
const val MEAL_SCAN_NEXT_TAG = "meal_scan_next"
const val MEAL_SNACKBAR_TAG = "meal_snackbar"

/**
 * The meal total (development-pass brief §10).
 *
 * A working scratchpad, not a log. Everything on this screen is deliberately undated and unnamed:
 * there is no "save", no meal name, no time, and no history to return to — clearing it is the only
 * way it ends, and then it is gone. That is what keeps a carbohydrate calculator from becoming the
 * food diary §28 rules out.
 *
 * Each line shows the portion in the words the user chose ("2 slices", "½ pack"), because a list of
 * resolved gram figures would be unrecognisable as the food they just scanned.
 */
@Composable
fun MealScreen(
    state: MealUiState,
    settings: AppSettings,
    onBack: () -> Unit,
    onRemoveItem: (MealItem) -> Unit,
    onClear: () -> Unit,
    onShowClearConfirmation: (Boolean) -> Unit,
    onScanNext: () -> Unit = {},
    onUndoRemove: () -> Unit = {},
    onUndoExpired: () -> Unit = {},
    onEditItem: (MealItem) -> Unit = {},
    onEditAmountChange: (String) -> Unit = {},
    /** A quick-adjust accelerator applied to the amount being corrected (1.0.8). */
    onAdjustEditAmount: (PortionAdjustment.Operation) -> Unit = {},
    onSaveEdit: (BigDecimal, String) -> Unit = { _, _ -> },
    onCloseEditor: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // A saved correction's acknowledgement, played once the sheet has gone: a brief glow on the line
    // that changed and a small lift of the total it changed, driven by one value so the eye travels
    // from one to the other. Home's meal bar confirms a Quick Add the same way.
    val editPulse = remember { Animatable(0f) }
    var glowingLine by remember { mutableStateOf<Long?>(null) }
    val pulseScope = rememberCoroutineScope()

    // One Snackbar per removal, keyed on the removed item so a second removal replaces the first
    // rather than queueing behind it — a queue would let the user tap Undo and restore an item they
    // removed two actions ago.
    val removed = state.lastRemoved
    val removedLabel = removed?.displayName
    val undoMessage = removedLabel?.let { stringResource(R.string.meal_item_removed, it) }
    val undoAction = stringResource(R.string.action_undo)
    LaunchedEffect(removed?.id) {
        if (removed == null || undoMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undoMessage,
            actionLabel = undoAction,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndoRemove()
            // Timed out or was replaced: the removal stands, and the held snapshot is dropped so it
            // cannot be restored later by a stale action.
            SnackbarResult.Dismissed -> onUndoExpired()
        }
    }
    if (state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { onShowClearConfirmation(false) },
            modifier = Modifier.jtcDialogOutline(),
            shape = JtcDialogDefaults.shape,
            containerColor = JtcDialogDefaults.containerColor,
            iconContentColor = JtcDialogDefaults.iconContentColor,
            titleContentColor = JtcDialogDefaults.titleContentColor,
            textContentColor = JtcDialogDefaults.textContentColor,
            tonalElevation = JtcDialogDefaults.tonalElevation,
            title = { Text(stringResource(R.string.meal_clear)) },
            text = { Text(stringResource(R.string.meal_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = onClear) { Text(stringResource(R.string.meal_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { onShowClearConfirmation(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.editor?.let { editor ->
        MealItemEditorSheet(
            editor = editor,
            settings = settings,
            onAmountChange = onEditAmountChange,
            onAdjust = onAdjustEditAmount,
            onSave = onSaveEdit,
            onDismiss = onCloseEditor,
            onSaved = {
                onCloseEditor()
                glowingLine = editor.item.id
                pulseScope.launch {
                    editPulse.snapTo(1f)
                    editPulse.animateTo(0f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccentBackdrop(
            accent = Destination.MEAL.accent(),
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            JtcTopBar(
                title = stringResource(R.string.meal_title),
                destination = Destination.MEAL,
                onBack = onBack,
            ) {
                // Only offered when there is something to clear — a permanently-present destructive
                // action on an empty screen is noise the user has to learn to ignore.
                if (state.items.isNotEmpty()) {
                    TextButton(
                        onClick = { onShowClearConfirmation(true) },
                        modifier = Modifier.testTag(MEAL_CLEAR_TAG),
                    ) {
                        Text(stringResource(R.string.meal_clear))
                    }
                }
            }

            // A sibling Box (not the outer one) so the Snackbar below is TopCenter-aligned to the
            // space starting right after JtcTopBar, rather than to the whole screen.
            //
            // A bottom-anchored Snackbar sat directly over the meal total — the one number this
            // screen exists to show — for its whole four-second life, so removing an item hid the
            // figure the user was removing it to correct. Aligning it to the *screen's* top instead
            // fixed that but created the same class of bug one layer up: it landed on top of
            // JtcTopBar, because `statusBarsPadding()` clears the status bar but knows nothing about
            // the bar's own height — which is itself not fixed (it grows past its 64dp minimum at a
            // large font scale). Nesting the Snackbar in this Box, a sibling of JtcTopBar rather than
            // of the whole screen, gets the correct offset from the layout system directly instead of
            // a guessed dp figure that would work on this device and fail on a shorter or
            // larger-font one — the exact trap the original bottom-anchor fix already named.
            Box(modifier = Modifier.weight(1f)) {
                if (state.items.isEmpty()) {
                    EmptyMeal(modifier = Modifier.fillMaxSize())
                } else {
                    // Full width, with the screen edge applied inside each row, so a row's press
                    // feedback spans the whole line the way a list's does; the dividers keep the
                    // edge inset.
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { item ->
                            MealItemRow(
                                item = item,
                                glow = if (item.id == glowingLine) editPulse::value else null,
                                onEdit = { onEditItem(item) },
                                onRemove = { onRemoveItem(item) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = Space.screenEdge),
                            )
                        }
                    }
                }

                // The Undo action's colour comes from `inversePrimary`, which `Theme.kt` now sets —
                // left to Material's default it rendered as a lavender that appears nowhere else in
                // the app.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Space.s, start = Space.screenEdge, end = Space.screenEdge)
                        .testTag(MEAL_SNACKBAR_TAG),
                ) { data ->
                    Snackbar(snackbarData = data, shape = RoundedCornerShape(Space.buttonRadius))
                }
            }

            MealTotalPanel(
                state = state,
                settings = settings,
                onScanNext = onScanNext,
                pulse = editPulse::value,
            )
        }
    }
}

@Composable
private fun EmptyMeal(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Space.screenEdge),
        ) {
            Text(
                text = stringResource(R.string.meal_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.meal_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One line: what it was, how much of it, and the carbohydrates that produced.
 *
 * The figures come straight from the stored [MealItem] and are never recomputed from the product —
 * a line added before the product was corrected must keep showing what the user accepted (§9).
 *
 * The line is its own edit control (1.0.8): tapping it opens the editor, so there is no per-row Edit
 * button to crowd the list. What says so is quiet and in the app's own grammar — the amount is set
 * in the interaction blue, the line answers a press across its full width, and TalkBack hears the
 * action by name. Remove stays a separate, explicit target at the end of the line, so neither can
 * be hit when the other was meant.
 */
@Composable
private fun MealItemRow(
    item: MealItem,
    /** The post-save glow for the line that was just corrected; null on every other line. */
    glow: (() -> Float)?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.meal_remove_item, item.displayName)
    val carbs = ResultFormatter.decimal(item.exactCarbs)
    val summary = stringResource(R.string.meal_item_summary, item.portionDescription, carbs)
    val spoken = stringResource(R.string.meal_item_spoken, item.displayName, item.portionDescription, carbs)
    val editLabel = stringResource(R.string.meal_edit_item)
    val amountColor = MaterialTheme.colorScheme.primary
    val glowColor = MaterialTheme.colorScheme.primary
    // Only the portion carries the blue: it is the part of the line a tap changes.
    val styledSummary = remember(summary, item.portionDescription, amountColor) {
        buildAnnotatedString {
            append(summary)
            val start = summary.indexOf(item.portionDescription)
            if (start >= 0 && item.portionDescription.isNotEmpty()) {
                addStyle(
                    SpanStyle(color = amountColor, fontWeight = FontWeight.SemiBold),
                    start,
                    start + item.portionDescription.length,
                )
            }
        }
    }

    // The whole line is the edit target, so its press feedback spans the full width; Remove sits
    // inside it as its own control and takes its own taps. They stay two separate accessibility
    // nodes: the line, announced as one phrase with its amount and carbs in words, and Remove.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.minTouchTarget)
            .drawBehind {
                val strength = glow?.invoke() ?: 0f
                if (strength > 0f) drawRect(glowColor.copy(alpha = 0.12f * strength))
            }
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .semantics { contentDescription = spoken }
            .padding(start = Space.screenEdge, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Space.s),
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = styledSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                // Where it always was: the button's edge on the screen-edge inset.
                .padding(end = Space.screenEdge)
                .size(Space.minTouchTarget)
                .semantics { contentDescription = removeLabel },
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
        }
    }
}

/**
 * The total, in the same pinned surface the calculator uses for its result — the same kind of
 * number in the same place on screen, so it reads as one app rather than two.
 */
@Composable
private fun MealTotalPanel(
    state: MealUiState,
    settings: AppSettings,
    onScanNext: () -> Unit,
    /** 0 at rest; briefly 1 → 0 after a correction is saved, lifting the total it changed. */
    pulse: () -> Float,
) {
    val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)
    val total = state.total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Same lift as the calculator's result panel, and for the same reason recorded there:
            // this surface is `surfaceContainerLowest` on a cream page, a ~1% difference, so without
            // a shadow the screen's most important number has no edge and reads as part of the
            // background. The two panels are the same element in the same place and must not differ.
            .shadow(elevation = Space.resultElevation, shape = panelShape, clip = false)
            .clip(panelShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(horizontal = Space.screenEdge, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.meal_total_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))

        if (total == null) {
            // No calculation has happened — this must not look like one. A dominant tomato-red
            // "0.0 g" here previously claimed a result the app had not computed; an em dash makes
            // no such claim. The supporting line below the dominant figure is part of that same
            // claim, so it must stay silent here too — a "≈ 0 g whole grams" line beneath the em
            // dash would restate the exact defect the em dash exists to avoid.
            Text(
                text = stringResource(R.string.meal_total_empty_placeholder),
                style = NumberType.result,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(MEAL_TOTAL_TAG),
            )
        } else {
            // Both figures derive from the exact sum independently, exactly as the calculator does —
            // the whole-gram line is never rounded from the decimal one (§17).
            val dominantNumeral = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> ResultFormatter.decimal(total.exact)
                ResultStyle.WHOLE_DOMINANT -> ResultFormatter.whole(total.wholeGrams)
            }
            val resultUnit = stringResource(R.string.result_unit_grams)

            // The total and its copy button, on one row.
            //
            // A meal total is the figure a multi-item user is *most* likely to be transcribing —
            // building the meal is how they turn several foods into one number — and until now it
            // was the one result in the app that could not be copied, so that user had to read and
            // retype it while the calculator's single-product figure was one tap away. Same
            // component, same confirmation, same clipboard rule as the calculator.
            //
            // The button is placed in the spacer that balances it on the left, so the total itself
            // stays optically centred in the panel rather than being pushed off-centre by the
            // button's width. Both spacers are the same minimum touch target the button occupies.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.width(Space.minTouchTarget))

                // The digits cross-fade when the total changes, as the calculator's result does, and
                // lift slightly when a correction has just changed them. Read in the layer phase, so
                // the pulse redraws the figure without recomposing it.
                AnimatedContent(
                    targetState = dominantNumeral,
                    transitionSpec = {
                        (fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.Center,
                    label = "mealTotal",
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .graphicsLayer {
                            val lift = 1f + 0.03f * pulse()
                            scaleX = lift
                            scaleY = lift
                        },
                ) { numeral ->
                    ResultValue(
                        dominant = numeral,
                        unit = resultUnit,
                        accessibleLabel = stringResource(R.string.result_accessible_grams, numeral),
                        testTag = MEAL_TOTAL_TAG,
                    )
                }

                CopyResultButton(
                    value = ResultFormatter.clipboardValue(total, settings.resultStyle),
                    hapticsEnabled = settings.hapticsEnabled,
                    modifier = Modifier.testTag(MEAL_COPY_TAG),
                )
            }

            Text(
                text = when (settings.resultStyle) {
                    ResultStyle.DECIMAL_DOMINANT -> stringResource(
                        R.string.product_result_whole,
                        ResultFormatter.whole(total.wholeGrams),
                    )
                    ResultStyle.WHOLE_DOMINANT -> stringResource(
                        R.string.product_result_calculated,
                        ResultFormatter.decimal(total.exact),
                    )
                },
                style = NumberType.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The meal was previously a dead end: the only ways on were the system back gesture or the
        // top-left arrow, both of which read as "leave" rather than "continue". A meal is usually
        // several products, so the screen that shows the running total is exactly where the user
        // decides to add another — barcode, because that is the app's fastest way to a product.
        //
        // Offered only once the meal has something in it. On an empty meal the primary action is
        // still to calculate a portion, which the empty state already says.
        if (state.items.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            Box(modifier = Modifier.testTag(MEAL_SCAN_NEXT_TAG)) {
                PrimaryAction(
                    text = stringResource(R.string.meal_scan_next),
                    onClick = onScanNext,
                )
            }
        }
    }
}
