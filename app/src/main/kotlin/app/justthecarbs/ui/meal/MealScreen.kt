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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** Stable handles for instrumented tests. */
const val MEAL_TOTAL_TAG = "meal_total"
const val MEAL_CLEAR_TAG = "meal_clear"

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
) {
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

            MealTotalPanel(state = state, settings = settings)
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
private fun MealTotalPanel(state: MealUiState, settings: AppSettings) {
    val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)
    val total = state.total

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}
