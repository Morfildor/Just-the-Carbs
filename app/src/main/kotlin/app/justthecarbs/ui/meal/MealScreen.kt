package app.justthecarbs.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** Stable handles for instrumented tests. */
const val MEAL_TOTAL_TAG = "meal_total"
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
) {
    val snackbarHostState = remember { SnackbarHostState() }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-90).dp)
                .size(200.dp)
                .background(MaterialTheme.extendedColors.orangeSoft.copy(alpha = 0.9f), CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.product_back),
                    )
                }
                Text(
                    text = stringResource(R.string.meal_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Space.s)
                        .semantics { heading() },
                )
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

            if (state.items.isEmpty()) {
                EmptyMeal(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Space.screenEdge),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        MealItemRow(item = item, onRemove = { onRemoveItem(item) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            MealTotalPanel(state = state, settings = settings, onScanNext = onScanNext)
        }

        // Anchored to the *top* of the screen, not the bottom.
        //
        // A bottom-anchored Snackbar sat directly over the meal total — the one number this screen
        // exists to show — for its whole four-second life, so removing an item hid the figure the
        // user was removing it to correct. Only visible by looking at the screen; every assertion
        // still passed. Raising it above the panel instead would work on this device and fail on a
        // shorter one, because the panel's height depends on the total's font scale.
        //
        // The Undo action's colour comes from `inversePrimary`, which `Theme.kt` now sets — left to
        // Material's default it rendered as a lavender that appears nowhere else in the app.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Space.screenEdge)
                .testTag(MEAL_SNACKBAR_TAG),
        ) { data ->
            Snackbar(snackbarData = data, shape = RoundedCornerShape(Space.buttonRadius))
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
 */
@Composable
private fun MealItemRow(item: MealItem, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.meal_remove_item, item.displayName)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                text = stringResource(
                    R.string.meal_item_summary,
                    item.portionDescription,
                    ResultFormatter.decimal(item.exactCarbs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
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
private fun MealTotalPanel(state: MealUiState, settings: AppSettings, onScanNext: () -> Unit) {
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

        // Both figures derive from the exact sum independently, exactly as the calculator does —
        // the whole-gram line is never rounded from the decimal one (§17).
        val dominant = when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(total?.exact ?: java.math.BigDecimal.ZERO)} g"
            ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(total?.wholeGrams ?: 0)} g"
        }

        Text(
            text = dominant,
            style = NumberType.result,
            color = MaterialTheme.extendedColors.result,
            maxLines = 1,
            // Same reason as the calculator's result, and more pressing here: a meal total is the
            // sum of several portions, so it reaches three and four digits sooner than any single
            // product's result does.
            autoSize = NumberType.resultAutoSize,
            modifier = Modifier.testTag(MEAL_TOTAL_TAG),
        )

        Text(
            text = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> stringResource(
                    R.string.product_result_whole,
                    ResultFormatter.whole(total?.wholeGrams ?: 0),
                )
                ResultStyle.WHOLE_DOMINANT -> stringResource(
                    R.string.product_result_calculated,
                    ResultFormatter.decimal(total?.exact ?: java.math.BigDecimal.ZERO),
                )
            },
            style = NumberType.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
