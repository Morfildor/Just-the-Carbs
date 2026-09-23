package app.justthecarbs.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.ui.components.ResultValue
import app.justthecarbs.ui.home.HomeActionCard
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/**
 * Deterministic, non-interactive app scenes behind the tutorial.
 *
 * The preview fills the screen. It reserves the measured teaching footprint rather than ending at
 * it, so the user can still recognize the screen above and below the copy. Every figure is a
 * constant; no ViewModel, repository, camera, network, Room database, or real meal participates.
 */
@Composable
fun TutorialBackdropContent(
    backdrop: TutorialBackdrop,
    anchors: TutorialAnchors,
    modifier: Modifier = Modifier,
    teachingTop: Dp = 0.dp,
    teachingHeight: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val compact = density.fontScale >= ACCESSIBILITY_PREVIEW_FONT_SCALE
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.25f)),
        ) {
            when (backdrop) {
                TutorialBackdrop.HOME -> HomePreview(anchors, teachingTop, teachingHeight, compact)
                TutorialBackdrop.PRODUCT -> ProductPreview(anchors, teachingTop, teachingHeight, compact)
                TutorialBackdrop.MEAL -> MealPreview(anchors, teachingTop, teachingHeight, compact)
            }
        }
    }
}

@Composable
private fun HomePreview(anchors: TutorialAnchors, teachingTop: Dp, teachingHeight: Dp, compact: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(teachingTop)
                .statusBarsPadding()
                .padding(horizontal = Space.screenEdge),
        ) {
            Spacer(Modifier.height(Space.minTouchTarget))
            Text(
                text = BuildConfig.APP_NAME,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(if (compact) Space.xs else Space.s))
            Column(Modifier.fillMaxWidth().tutorialAnchor(anchors, TutorialAnchor.FIND_ACTIONS)) {
                PreviewSearchField(anchors, compact)
                Spacer(Modifier.height(if (compact) Space.xs else Space.s))
                // The REAL cards, with a no-op click (P0-4).
                //
                // These were two locally-declared gradient tiles -- cobalt-to-indigo and
                // teal-to-green -- with circular icon plates, which is a Home screen the app has
                // never had and, since the visual pass, is not even close to. A tutorial that
                // teaches a different design than the one behind it is worse than no tutorial: the
                // user learns to look for a green card that is not there.
                HomeActionCard(
                    icon = Icons.Filled.QrCodeScanner,
                    title = stringResource(R.string.home_scan_button),
                    subtitle = stringResource(R.string.home_action_barcode_subtitle),
                    accent = MaterialTheme.colorScheme.primary,
                    filled = true,
                    onClick = {},
                    modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_BARCODE),
                )
                Spacer(Modifier.height(if (compact) Space.xs else Space.s))
                HomeActionCard(
                    icon = Icons.Filled.DocumentScanner,
                    title = stringResource(R.string.home_empty_scan_label),
                    subtitle = stringResource(R.string.home_action_label_subtitle),
                    accent = MaterialTheme.extendedColors.accents.teal,
                    onClick = {},
                    modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_LABEL),
                )
            }
            // The rhythm strip is the first thing to go when the region is tight.
            //
            // The teaching region has a FIXED height (`teachingTop`), and the real action cards are
            // taller than the gradient tiles they replaced -- so on this step `weight(1f)` resolves
            // to nothing and the strip's roundels ride up against the card above. Measured on
            // device at 411x914; a minimum-height spacer was tried first and changed nothing,
            // because there is no slack for it to claim.
            //
            // It is dropped rather than squeezed. CLAUDE.md records this strip as visible "only as
            // context" on START, and the step's own teaching copy already names all three routes
            // in words ("Scan a barcode, search by name, or scan the nutrition label"). The cards
            // it was colliding with ARE the subject of this step; the decoration is not.
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(teachingHeight))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.s))
            // A borderless label, matching Home's own `TextButton`. The outlined box drawn here
            // before was a third bordered rectangle stacked under two bordered cards, and Home has
            // never rendered this control that way.
            Text(
                text = stringResource(R.string.home_manual_button),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = Space.minTouchTarget).wrapContentHeight(),
            )
            Spacer(Modifier.height(Space.m))
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PreviewSearchField(anchors: TutorialAnchors, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 56.dp)
            .clip(RoundedCornerShape(Space.buttonRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Space.buttonRadius))
            .tutorialAnchor(anchors, TutorialAnchor.SEARCH)
            .padding(horizontal = Space.m, vertical = if (compact) Space.xs else Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp),
        )
        Spacer(Modifier.width(Space.s))
        Text(
            text = stringResource(R.string.home_search_label),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PREVIEW_ACCENT_SUPPORTING_ALPHA = 0.96f

@Composable
private fun ProductPreview(anchors: TutorialAnchors, teachingTop: Dp, teachingHeight: Dp, compact: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(teachingTop)
                .statusBarsPadding()
                .padding(horizontal = Space.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.minTouchTarget))
            Text(
                text = stringResource(R.string.tutorial_example_product),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.tutorial_example_per_hundred, EXAMPLE_CARBS),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compact) Space.xs else Space.s))
            // The portion group as the calculator now states it: a left-aligned group label, not
            // the centred question `product_portion_question` -- which the visual pass removed
            // from the real screen, and which a user following this tutorial would then look for
            // and not find.
            // The same eyebrow the real screen now pairs with its `CARBS` label (2026-09-23).
            Text(
                text = stringResource(R.string.product_portion_group_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xs))
            // A quiet fill with a hairline `outline` edge and a left-aligned value, matching the
            // calculator's own number-entry frame at rest (2026-09-23): the hairline is what says
            // "field" next to a headline number, and the value sits at the start like a form value.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 52.dp else 64.dp)
                    .clip(RoundedCornerShape(Space.buttonRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Space.buttonRadius))
                    .padding(horizontal = Space.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "$EXAMPLE_PORTION g",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(if (compact) Space.xs else Space.s))
            Text(
                text = stringResource(R.string.product_result_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            // The REAL result component, so the tutorial inherits the baseline fix (P0-2) rather
            // than re-drawing the numeral and hanging its own unit off the bottom of it.
            ResultValue(
                dominant = EXAMPLE_RESULT_VALUE,
                unit = EXAMPLE_RESULT_UNIT,
                accessibleLabel = EXAMPLE_RESULT,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Space.primaryButtonHeight)
                    .clip(RoundedCornerShape(Space.buttonRadius))
                    .background(MaterialTheme.colorScheme.primary)
                    .tutorialAnchor(anchors, TutorialAnchor.ADD_TO_MEAL),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.meal_add),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }
        Spacer(Modifier.height(teachingHeight))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.meal_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = Space.m),
            )
            Text(
                text = "2 items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = Space.m),
            )
        }
    }
}

@Composable
private fun MealPreview(anchors: TutorialAnchors, teachingTop: Dp, teachingHeight: Dp, compact: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(teachingTop)
                .statusBarsPadding()
                .padding(horizontal = Space.screenEdge),
        ) {
            Spacer(Modifier.height(Space.minTouchTarget))
            Text(
                text = stringResource(R.string.meal_title),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s))
            PreviewMealRow(stringResource(R.string.tutorial_example_product), "$EXAMPLE_PORTION g", EXAMPLE_RESULT, compact)
            Spacer(Modifier.height(Space.s))
            PreviewMealRow(stringResource(R.string.tutorial_example_second_product), "120 g", "9.6 g", compact)
        }
        Spacer(Modifier.height(teachingHeight))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .tutorialAnchor(anchors, TutorialAnchor.MEAL_TOTAL)
                    .navigationBarsPadding()
                    .padding(horizontal = Space.screenEdge, vertical = if (compact) Space.s else Space.m),
                // Left-aligned, matching `MealTotalPanel` since the visual pass. Centring it here
                // put the total on a different axis from the rows it totals.
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.meal_total_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultValue(
                    dominant = EXAMPLE_TOTAL_VALUE,
                    unit = EXAMPLE_RESULT_UNIT,
                    accessibleLabel = "$EXAMPLE_TOTAL_VALUE $EXAMPLE_RESULT_UNIT",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PreviewMealRow(name: String, portion: String, carbs: String, compact: Boolean) {
    Row(
        // Borderless, like the real `MealItemRow`. A box around every row turned a two-item list
        // into two cards, which is the "box inside a box" the visual direction removes.
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) Space.s else Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = portion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = carbs,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.extendedColors.result,
        )
    }
}

private const val ACCESSIBILITY_PREVIEW_FONT_SCALE = 1.5f
private const val EXAMPLE_CARBS = "48"
private const val EXAMPLE_PORTION = "35"
private const val EXAMPLE_RESULT = "16.8 g"

/**
 * The same figure as [EXAMPLE_RESULT], split the way `ResultValue` takes it.
 *
 * Held as two constants rather than split at the space, because the numeral and its unit are two
 * different typographic roles -- a substring is a guess about a display string that happens to be
 * right today.
 */
private const val EXAMPLE_RESULT_VALUE = "16.8"
private const val EXAMPLE_RESULT_UNIT = "g"

/** The two example portions added together, as the meal total. */
private const val EXAMPLE_TOTAL_VALUE = "26.4"
