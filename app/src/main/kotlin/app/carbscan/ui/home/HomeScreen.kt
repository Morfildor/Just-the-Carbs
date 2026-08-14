package app.carbscan.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carbscan.BuildConfig
import app.carbscan.R
import app.carbscan.domain.AppSettings
import app.carbscan.domain.InputMode
import app.carbscan.domain.Product
import app.carbscan.domain.ResultFormatter
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.CarbCalculator
import app.carbscan.domain.CarbResult
import app.carbscan.domain.MealItem
import app.carbscan.ui.components.ProductThumbnail
import app.carbscan.ui.components.FavoriteButton
import app.carbscan.ui.meal.MealBarIfPresent
import app.carbscan.ui.product.unitLabel
import app.carbscan.ui.theme.Space

/**
 * Home (§6, §7).
 *
 * One primary surface, no bottom navigation. Favourites float to the top of Recent rather than
 * occupying a tab of their own, because a second tab would add a decision to a workflow whose whole
 * value is not having to make one (§22, §74).
 *
 * Nothing here waits on the network: recents are local, and the list renders before any lookup
 * could possibly return (§7).
 */
@Composable
fun HomeScreen(
    recents: List<RecentEntry>,
    settings: AppSettings,
    onScan: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onOpenSettings: () -> Unit,
    mealItems: List<MealItem> = emptyList(),
    mealTotal: CarbResult? = null,
    onOpenMeal: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.screenEdge, end = Space.s, top = Space.s, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = BuildConfig.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            val settingsLabel = stringResource(R.string.home_settings)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(Space.minTouchTarget)
                    .semantics { contentDescription = settingsLabel },
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
            }
        }

        // The meal in progress, if there is one (§10). Above recents rather than below, because a
        // half-built meal is the thing the user is in the middle of; and absent entirely when the
        // meal is empty, so Home's resting state is unchanged from before this feature existed.
        MealBarIfPresent(
            itemCount = mealItems.size,
            total = mealTotal,
            onClick = onOpenMeal,
            modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.xs),
        )

        // Recents take the scrollable middle; the primary action sits at the bottom where a thumb
        // actually reaches it (§40).
        if (recents.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            RecentList(
                recents = recents,
                settings = settings,
                onOpenProduct = onOpenProduct,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge)
                .padding(bottom = Space.m)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onScan,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(Space.s))
                Text(
                    text = stringResource(R.string.home_scan_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            TextButton(
                onClick = onManualEntry,
                modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
            ) {
                Text(stringResource(R.string.home_manual_button))
            }
        }
    }
}

/**
 * The empty state (§3).
 *
 * Previously two lines of text floating in a large void, which read as unfinished rather than as
 * calm. It now carries the app's own mark — the package-and-scan-beam from the launcher icon — at a
 * size and opacity that furnishes the space without competing with the primary action below it.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(32.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Inset inside the tile: the mark's scan beam runs the full width of its viewport, so
            // at tile size it collides with the rounded corners.
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(92.dp),
            )
        }

        Spacer(Modifier.height(Space.l))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecentList(
    recents: List<RecentEntry>,
    settings: AppSettings,
    onOpenProduct: (String) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Space.screenEdge,
            end = Space.screenEdge,
            bottom = Space.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.s).semantics { heading() },
            )
        }
        items(items = recents, key = { it.product.barcode }) { entry ->
            RecentCard(
                entry = entry,
                settings = settings,
                onClick = { onOpenProduct(entry.product.barcode) },
                onToggleFavorite = { onToggleFavorite(entry.product) },
            )
        }
    }
}

/**
 * A recent product (§7): name, last portion, last result, favourite. Nothing else.
 *
 * No calories, no macros, no "eaten today" — none of which would make the next scan faster, and
 * all of which would make this look like the diet tracker the app must not be (§2).
 */
@Composable
private fun RecentCard(
    entry: RecentEntry,
    settings: AppSettings,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val product = entry.product
    // Recompute rather than store a display string, so a corrected carbs value is reflected in the
    // summary immediately instead of showing a figure derived from the old one.
    val summary = product.lastPortion?.let { portion ->
        val result = CarbCalculator.calculate(product.carbsPer100, portion, product.basis)
        // Countable-portions brief §12: when the product was last used as "2 slices", Recents says
        // so — not the gram amount the user never actually thought in.
        val amountLabel = product.lastCount
            ?.takeIf { entry.lastUnit != null && product.lastInputMode == InputMode.PORTION_UNIT }
            ?.let { count -> "${count.stripTrailingZeros().toPlainString()} ${entry.lastUnit!!.unitLabel(count = 2)}" }
            ?: "${portion.stripTrailingZeros().toPlainString()} ${product.portionUnit}"
        // Follows the user's configured result style (§18). Recents previously always showed the
        // whole gram while the calculator led with the decimal, so the same portion of the same
        // product read as "30 g" here and "30.2 g" one tap away — the app appearing to disagree
        // with itself about a number the user is about to rely on.
        val carbsLabel = when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(result.exact)} g"
            ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(result.wholeGrams)} g"
        }
        stringResource(R.string.recent_summary, amountLabel, carbsLabel)
    } ?: stringResource(R.string.recent_never_used)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(Space.cardRadius),
            )
            .clickable(onClick = onClick)
            .padding(start = Space.s + Space.xs, top = Space.s, bottom = Space.s, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(product = product)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.s + Space.xs, end = Space.xs),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FavoriteButton(favorite = product.favorite, onToggle = onToggleFavorite)
    }
}
