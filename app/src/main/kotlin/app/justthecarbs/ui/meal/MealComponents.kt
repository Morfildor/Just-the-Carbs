package app.justthecarbs.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.StaleMeal
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.components.jtcOutlinedButtonBorder
import app.justthecarbs.ui.components.rememberSuccessPulse
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** Stable handles for instrumented tests. */
const val MEAL_ADD_TAG = "meal_add"
const val MEAL_ADD_AND_SCAN_TAG = "meal_add_and_scan"
const val MEAL_BAR_TAG = "meal_bar"
const val MEAL_STALE_DIALOG_TAG = "meal_stale_dialog"
const val MEAL_STALE_START_NEW_TAG = "meal_stale_start_new"
const val MEAL_STALE_ADD_TO_IT_TAG = "meal_stale_add_to_it"

/**
 * *Add to meal* and *Add & scan next* (development-pass brief §9, §11).
 *
 * Both are **secondary** to the result itself. The app's single purpose is answering "how many
 * carbohydrates is this portion?", and the meal is a convenience on top of that answer — so these
 * sit below the number rather than competing with it, and neither is a filled primary button that
 * would read as "the thing you came here to do".
 *
 * They are only shown once there is a result to add. An *Add to meal* button next to an empty
 * result would either add nothing or add a zero, and both are worse than not offering it.
 */
@Composable
fun MealActions(
    onAdd: () -> Unit,
    onAddAndScanNext: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * False while a previous tap's write is still in flight.
     *
     * The write is asynchronous, so without this a second tap before the first completes starts a
     * second insert — two rows in the meal for one user action.
     */
    enabled: Boolean = true,
    /**
     * A distinct value each time a meal-add write has just succeeded (see
     * `ProductUiState.lastMealAddSucceeded`). Null means no recent success to show. Drives a brief
     * "✓ Added" label on *Add to meal* via `rememberSuccessPulse` — the same confirmation grammar
     * as the result's copy button, generalized rather than reinvented.
     */
    justAdded: Any? = null,
) {
    val showAdded = rememberSuccessPulse(justAdded)
    // One height for the pair (2026-09-23 calculator refinement). *Add to meal* was 48dp and *Add &
    // scan next* 56dp, top-aligned, so the two labels sat on different baselines; at 1.3x text the
    // filled one wrapped and grew while the outlined one did not. Both now have the primary floor,
    // and `IntrinsicSize.Min` plus `fillMaxHeight` keep them the same height if either wraps -- the
    // arrangement `PackShortcuts` already uses. The narrower content padding is what lets both
    // labels stay on one line at 1.3x on a 411dp phone.
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // Outlined with a PRIMARY label and border, not Material's default.
        //
        // The default takes its content colour from `onSurfaceVariant` -- the same grey this app
        // uses for supporting text -- so `Add to meal` read as a disabled control sitting beside a
        // filled blue `Add & scan next`. It is a real alternative, and now looks like one. Built
        // out of the same pieces as `JtcOutlinedButton` rather than calling it, because this
        // button's label is a slot: it swaps to a check icon plus `Added` on success.
        OutlinedButton(
            onClick = onAdd,
            enabled = enabled,
            shape = RoundedCornerShape(Space.buttonRadius),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = jtcOutlinedButtonBorder(enabled),
            contentPadding = MEAL_ACTION_PADDING,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Space.primaryButtonHeight)
                .fillMaxHeight()
                .testTag(MEAL_ADD_TAG),
        ) {
            if (showAdded) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.xs))
            }
            Text(
                if (showAdded) stringResource(R.string.meal_added) else stringResource(R.string.meal_add),
                textAlign = TextAlign.Center,
            )
        }
        // Filled, because in a multi-item meal this is the button the user presses repeatedly: it
        // is the loop. One tap records the item and reopens the scanner, which is the difference
        // between adding six things and giving up after two (§11).
        //
        // Filled with `primaryTile`, not `primary` (2026-09-23). In Light the two are the same
        // cobalt. In Dark `primary` is the pale #82A2FF, and spread over a 56dp button directly under
        // the answer it was the brightest object on the screen -- brighter than the coral figure the
        // dock exists to show. `primaryTile` is the deep cobalt Home's scan tile already uses for
        // exactly this reason (see ExtendedColors.primaryTile).
        Button(
            onClick = onAddAndScanNext,
            enabled = enabled,
            shape = RoundedCornerShape(Space.buttonRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.extendedColors.primaryTile,
                contentColor = MaterialTheme.extendedColors.onPrimaryTile,
            ),
            contentPadding = MEAL_ACTION_PADDING,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Space.primaryButtonHeight)
                .fillMaxHeight()
                .testTag(MEAL_ADD_AND_SCAN_TAG),
        ) {
            Text(stringResource(R.string.meal_add_and_scan), textAlign = TextAlign.Center)
        }
    }
}

/**
 * The meal actions' inner padding: Material's default less half its horizontal inset, which is what
 * keeps "Add & scan next" on one line at 1.3x text on a 411dp phone.
 */
private val MEAL_ACTION_PADDING = PaddingValues(horizontal = Space.s + Space.xs, vertical = Space.s)

/**
 * The compact running-total bar, e.g. `Meal · 2 items · 38.0 g` (§10).
 *
 * Present on every screen that can contribute to a meal, and **absent entirely when the meal is
 * empty** — an app whose default state includes a "Meal · 0 items · 0 g" strip has quietly become
 * a tracker with a permanent dashboard. Here the bar exists only while the user is mid-meal, and
 * disappears the moment they clear it.
 *
 * It shows the decimal figure, not the whole-gram one: this is a running subtotal that will be
 * added to, and rounding at each step would compound (§9).
 */
@Composable
fun MealBar(
    itemCount: Int,
    total: CarbResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Tighter, unfilled variant for the calculator's pinned result surface.
     *
     * The full-height bar earns its space on Home, where it is the only thing saying a meal is in
     * progress. Inside the result panel it does not: the panel is already a distinct surface, and
     * the bar's own padding pushed the panel tall enough to clip the portion field the user was
     * still typing into — found by running the app, not by reading it. Compact keeps the same
     * information and the same tap target while giving the height back.
     */
    compact: Boolean = false,
) {
    val itemsLabel = pluralStringResource(R.plurals.meal_item_count, itemCount, itemCount)
    val summary = stringResource(
        R.string.meal_bar_summary,
        itemsLabel,
        ResultFormatter.decimal(total.exact),
    )
    val openLabel = stringResource(R.string.meal_open)

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The compact variant's padding left the row 40dp tall, under the app's minimum. It is
            // full-width, so it was never hard to hit horizontally — but it is the only way back to
            // a meal in progress, and it costs nothing to make it a full-height target. `heightIn`
            // rather than `height` so the row still grows with the text at a large font scale.
            .heightIn(min = Space.minTouchTarget)
            .clip(RoundedCornerShape(Space.cardRadius))
            .then(
                if (compact) Modifier
                else Modifier.background(MaterialTheme.colorScheme.secondaryContainer),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) Space.s else Space.m,
                vertical = if (compact) Space.xs else Space.s,
            )
            .testTag(MEAL_BAR_TAG)
            // One tap target announcing one thing, rather than a row of separately-focusable text
            // fragments a screen-reader user has to reassemble.
            .semantics { contentDescription = "$summary. $openLabel" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = if (compact) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = if (compact) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.s))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (compact) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

/** Reserves nothing when the meal is empty — see [MealBar]'s comment on why that matters. */
@Composable
fun MealBarIfPresent(
    itemCount: Int,
    total: CarbResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (total == null || itemCount == 0) return
    MealBar(
        itemCount = itemCount,
        total = total,
        onClick = onClick,
        modifier = modifier,
        compact = compact,
    )
    Spacer(Modifier.height(if (compact) Space.xs else Space.s))
}

/**
 * Asked when an Add would land on a meal that has gone quiet (see
 * [app.justthecarbs.domain.MealStaleness]).
 *
 * Both answers add the item the user tapped for; they differ only in what happens to the stored
 * meal. *Start new meal* is the confirm action because it is the likely intent after hours of
 * silence, and it states that the old items go. Dismissing adds nothing and clears nothing.
 */
@Composable
fun StaleMealDialog(
    staleMeal: StaleMeal,
    onStartNewMeal: () -> Unit,
    onAddToMeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = pluralStringResource(R.plurals.meal_item_count, staleMeal.itemCount, staleMeal.itemCount)
    val hours = staleMeal.sinceLastAdded.toHours().toInt()
    val age = pluralStringResource(R.plurals.meal_stale_hours, hours, hours)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline().testTag(MEAL_STALE_DIALOG_TAG),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(stringResource(R.string.meal_stale_title)) },
        text = {
            Text(
                stringResource(
                    R.string.meal_stale_body,
                    items,
                    ResultFormatter.decimal(staleMeal.exactCarbs),
                    age,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onStartNewMeal, modifier = Modifier.testTag(MEAL_STALE_START_NEW_TAG)) {
                Text(stringResource(R.string.meal_stale_start_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onAddToMeal, modifier = Modifier.testTag(MEAL_STALE_ADD_TO_IT_TAG)) {
                Text(stringResource(R.string.meal_stale_add_to_it))
            }
        },
    )
}
