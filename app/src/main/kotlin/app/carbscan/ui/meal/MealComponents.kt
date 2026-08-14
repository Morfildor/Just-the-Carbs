package app.carbscan.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.carbscan.R
import app.carbscan.domain.CarbResult
import app.carbscan.domain.ResultFormatter
import app.carbscan.ui.theme.Space

/** Stable handles for instrumented tests. */
const val MEAL_ADD_TAG = "meal_add"
const val MEAL_ADD_AND_SCAN_TAG = "meal_add_and_scan"
const val MEAL_BAR_TAG = "meal_bar"

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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        OutlinedButton(
            onClick = onAdd,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).testTag(MEAL_ADD_TAG),
        ) {
            Text(stringResource(R.string.meal_add))
        }
        // Filled, because in a multi-item meal this is the button the user presses repeatedly: it
        // is the loop. One tap records the item and reopens the scanner, which is the difference
        // between adding six things and giving up after two (§11).
        Button(
            onClick = onAddAndScanNext,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).testTag(MEAL_ADD_AND_SCAN_TAG),
        ) {
            Text(stringResource(R.string.meal_add_and_scan))
        }
    }
}

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
