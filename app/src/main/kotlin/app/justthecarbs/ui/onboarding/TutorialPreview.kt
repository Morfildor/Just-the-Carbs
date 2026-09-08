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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/**
 * The still life behind the tutorial's scrim.
 *
 * Every figure here is a constant. These composables render *like* Home, the calculator and the meal
 * screen — same vocabulary, same hierarchy, same tokens — without being them: no ViewModel, no
 * repository, no camera, no network, and nothing that can write to Room or to the user's real meal.
 * That is the whole safety argument for showing a working-looking app during onboarding, and it is
 * structural rather than a rule someone has to remember: there is no state to mutate here.
 *
 * They are also entirely non-interactive. Nothing in this file is clickable and nothing carries
 * semantics of its own — the overlay above marks the whole backdrop as decorative for TalkBack, so
 * the preview cannot be reached or operated while the tutorial is running.
 */

/** The example product, named once so the product and meal previews cannot disagree about it. */
private const val EXAMPLE_CARBS = "48"
private const val EXAMPLE_PORTION = "35"
private const val EXAMPLE_RESULT = "16.8 g"

@Composable
fun TutorialBackdropContent(
    backdrop: TutorialBackdrop,
    anchors: TutorialAnchors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (backdrop) {
            TutorialBackdrop.HOME -> HomePreview(anchors)
            TutorialBackdrop.PRODUCT -> ProductPreview(anchors)
            TutorialBackdrop.MEAL -> MealPreview(anchors)
        }
    }
}

@Composable
private fun HomePreview(anchors: TutorialAnchors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Space.screenEdge),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            text = BuildConfig.APP_NAME,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.m))

        // The real Home search field's label and placeholder, drawn as a static field.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(Space.buttonRadius))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(Space.buttonRadius),
                )
                .tutorialAnchor(anchors, TutorialAnchor.SEARCH)
                .padding(horizontal = Space.m),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    text = stringResource(R.string.home_search_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.m))
        PreviewActionCard(
            icon = Icons.Filled.QrCodeScanner,
            title = stringResource(R.string.home_scan_button),
            subtitle = stringResource(R.string.home_action_barcode_subtitle),
            gradient = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.extendedColors.accents.indigo,
            ),
            modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_BARCODE),
        )
        Spacer(Modifier.height(Space.s))
        PreviewActionCard(
            icon = Icons.Filled.DocumentScanner,
            title = stringResource(R.string.home_empty_scan_label),
            subtitle = stringResource(R.string.home_action_label_subtitle),
            gradient = listOf(
                MaterialTheme.extendedColors.accents.teal,
                MaterialTheme.extendedColors.accents.green,
            ),
            modifier = Modifier.tutorialAnchor(anchors, TutorialAnchor.SCAN_LABEL),
        )

        Spacer(Modifier.height(Space.l))
        // The find → portion → carbs rhythm the first step describes, drawn with the same three
        // icons Home's own empty state uses so the tutorial and the app agree visually.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val steps = listOf(
                Triple(
                    Icons.Filled.QrCodeScanner,
                    R.string.tutorial_rhythm_find,
                    MaterialTheme.colorScheme.primary,
                ),
                Triple(
                    Icons.Filled.Scale,
                    R.string.tutorial_rhythm_portion,
                    MaterialTheme.colorScheme.tertiary,
                ),
                Triple(
                    Icons.Filled.Calculate,
                    R.string.tutorial_rhythm_carbs,
                    MaterialTheme.extendedColors.result,
                ),
            )
            steps.forEachIndexed { index, (icon, labelRes, tint) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(tint.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Space.s)
                            .padding(bottom = Space.m)
                            .width(16.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(Brush.linearGradient(gradient))
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.padding(horizontal = Space.m)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun ProductPreview(anchors: TutorialAnchors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Space.screenEdge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.tutorial_example_product),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(R.string.tutorial_example_per_hundred, EXAMPLE_CARBS),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.l))
        Text(
            text = stringResource(R.string.product_portion_question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.s))
        // The portion field, holding a value — the step's copy says "choose a portion", and an
        // empty field would illustrate the sentence before it rather than the one it is next to.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(Space.buttonRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Space.buttonRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$EXAMPLE_PORTION g",
                style = NumberType.portion,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(Space.l))
        Text(
            text = stringResource(R.string.product_result_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = EXAMPLE_RESULT,
            style = NumberType.result,
            color = MaterialTheme.extendedColors.result,
            maxLines = 1,
            autoSize = NumberType.resultAutoSize,
        )

        Spacer(Modifier.height(Space.l))
        // The real *Add to meal* label — the control the step tells the user to tap.
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
    }
}

@Composable
private fun MealPreview(anchors: TutorialAnchors) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(
                text = stringResource(R.string.meal_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.m))
            PreviewMealRow(
                name = stringResource(R.string.tutorial_example_product),
                portion = "$EXAMPLE_PORTION g",
                carbs = EXAMPLE_RESULT,
            )
            Spacer(Modifier.height(Space.s))
            PreviewMealRow(
                name = stringResource(R.string.tutorial_example_second_product),
                portion = "120 g",
                carbs = "9.6 g",
            )
        }

        // The pinned MEAL TOTAL panel, with the same shape and lift the real one has.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .tutorialAnchor(anchors, TutorialAnchor.MEAL_TOTAL)
                .padding(horizontal = Space.screenEdge, vertical = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.meal_total_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "26.4 g",
                style = NumberType.result,
                color = MaterialTheme.extendedColors.result,
                maxLines = 1,
                autoSize = NumberType.resultAutoSize,
            )
        }
    }
}

@Composable
private fun PreviewMealRow(name: String, portion: String, carbs: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Space.cardRadius))
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = portion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = carbs,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.extendedColors.result,
        )
    }
}
