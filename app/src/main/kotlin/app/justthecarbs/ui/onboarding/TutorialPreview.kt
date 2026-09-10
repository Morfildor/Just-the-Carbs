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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.ui.theme.NumberType
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
                PreviewActionCard(
                    icon = Icons.Filled.QrCodeScanner,
                    title = stringResource(R.string.home_scan_button),
                    subtitle = stringResource(R.string.home_action_barcode_subtitle),
                    gradient = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.extendedColors.accents.indigo,
                    ),
                    compact = compact,
                    modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_BARCODE),
                )
                Spacer(Modifier.height(if (compact) Space.xs else Space.s))
                PreviewActionCard(
                    icon = Icons.Filled.DocumentScanner,
                    title = stringResource(R.string.home_empty_scan_label),
                    subtitle = stringResource(R.string.home_action_label_subtitle),
                    gradient = listOf(
                        MaterialTheme.extendedColors.accents.teal,
                        MaterialTheme.extendedColors.accents.green,
                    ),
                    compact = compact,
                    modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_LABEL),
                )
            }
            Spacer(Modifier.weight(1f))
            PreviewRhythm(compact)
            Spacer(Modifier.height(Space.s))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.minTouchTarget)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Space.buttonRadius))
                    .padding(horizontal = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_manual_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

@Composable
private fun PreviewActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.extendedColors.onAccent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(Brush.linearGradient(gradient))
            .padding(horizontal = Space.m, vertical = if (compact) Space.xs else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (compact) 32.dp else 40.dp)
                .background(contentColor.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
        }
        Column(Modifier.padding(start = Space.m)) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = contentColor,
            )
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = PREVIEW_ACCENT_SUPPORTING_ALPHA),
                    maxLines = 1,
                )
            }
        }
    }
}

private const val PREVIEW_ACCENT_SUPPORTING_ALPHA = 0.96f

@Composable
private fun PreviewRhythm(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        listOf(
            Triple(Icons.Filled.QrCodeScanner, R.string.tutorial_rhythm_find, MaterialTheme.colorScheme.primary),
            Triple(Icons.Filled.Scale, R.string.tutorial_rhythm_portion, MaterialTheme.colorScheme.tertiary),
            Triple(Icons.Filled.Calculate, R.string.tutorial_rhythm_carbs, MaterialTheme.extendedColors.result),
        ).forEach { (icon, label, tint) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (compact) 32.dp else 40.dp)
                        .background(tint.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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
            Text(
                text = stringResource(R.string.product_portion_question),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 52.dp else 64.dp)
                    .clip(RoundedCornerShape(Space.buttonRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Space.buttonRadius)),
                contentAlignment = Alignment.Center,
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
            )
            Text(
                text = EXAMPLE_RESULT,
                style = if (compact) MaterialTheme.typography.headlineMedium else NumberType.result,
                color = MaterialTheme.extendedColors.result,
                maxLines = 1,
                autoSize = if (compact) null else NumberType.resultAutoSize,
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.meal_total_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "26.4 g",
                    style = if (compact) MaterialTheme.typography.headlineMedium else NumberType.result,
                    color = MaterialTheme.extendedColors.result,
                    maxLines = 1,
                    autoSize = if (compact) null else NumberType.resultAutoSize,
                )
            }
        }
    }
}

@Composable
private fun PreviewMealRow(name: String, portion: String, carbs: String, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Space.cardRadius))
            .padding(horizontal = Space.m, vertical = if (compact) Space.s else Space.m),
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
