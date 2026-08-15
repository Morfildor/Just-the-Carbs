package app.justthecarbs.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.MealItem
import app.justthecarbs.ui.components.FavoriteButton
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.ProductThumbnail
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.components.SearchResultRow
import app.justthecarbs.ui.components.SecondaryAction
import app.justthecarbs.ui.meal.MealBarIfPresent
import app.justthecarbs.ui.product.unitLabel
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** Stable handles for instrumented tests. */
const val HOME_SEARCH_FIELD_TAG = "home_search_field"
const val HOME_SEARCH_RESULTS_TAG = "home_search_results"

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
    onScanLabel: () -> Unit = {},
    mealItems: List<MealItem> = emptyList(),
    mealTotal: CarbResult? = null,
    onOpenMeal: () -> Unit = {},
    searchState: SearchUiState = SearchUiState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    onSearchSelect: (ProductSearchHit) -> Unit = {},
    onSearchScanLabel: () -> Unit = {},
    onSearchEnterManually: () -> Unit = {},
    onSearchRetry: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Doc's decorative blue-soft circle, bleeding off the top-right corner (result.html).
        // Purely decorative — sits behind all content, never intercepts touches.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-90).dp)
                .size(220.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
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

            // A deliberate, always-available way in: not a fallback offered only after a failure
            // (that is what SearchScreen still is for the recovery paths), but a first-class entry
            // point someone reaches for on purpose because they already know what they want.
            HomeSearchField(
                query = searchState.query,
                onQueryChanged = onSearchQueryChanged,
                onSearchSubmit = onSearchSubmit,
                modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.xs),
            )

            // The meal in progress, if there is one (§10). Above recents rather than below, because a
            // half-built meal is the thing the user is in the middle of; and absent entirely when the
            // meal is empty, so Home's resting state is unchanged from before this feature existed.
            // Hidden while a search is active — the meal bar and search results both want the space
            // right below the header, and a search in progress is the more immediate task.
            if (searchState.query.isBlank()) {
                MealBarIfPresent(
                    itemCount = mealItems.size,
                    total = mealTotal,
                    onClick = onOpenMeal,
                    modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.xs),
                )
            }

            // Recents take the scrollable middle; the primary action sits at the bottom where a thumb
            // actually reaches it (§40). A non-blank query takes over the same space with live
            // results instead — Home never shows both at once.
            if (searchState.query.isNotBlank()) {
                HomeSearchResults(
                    state = searchState,
                    onSelect = onSearchSelect,
                    onScanLabel = onSearchScanLabel,
                    onEnterManually = onSearchEnterManually,
                    onRetry = onSearchRetry,
                    modifier = Modifier.weight(1f),
                )
            } else if (recents.isEmpty()) {
                EmptyState(onScanLabel = onScanLabel, modifier = Modifier.weight(1f))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(Space.buttonRadius),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                        ),
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
}

/**
 * The empty state (§7 product-development brief, redesigned 2026-08-15).
 *
 * Previously two lines of text floating in a large void, which read as unfinished rather than as
 * calm. It now furnishes the space with the app's own mark, the "Scan. Portion. Carbs." headline,
 * and a compact graphical 3-step strip echoing the onboarding identity — still a utility screen,
 * not a dashboard: no stats, no fake recents, no tips.
 */
@Composable
private fun EmptyState(onScanLabel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Inset inside the tile so the mark doesn't collide with the tile's rounded corners.
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp),
            )
        }

        Spacer(Modifier.height(Space.l))
        Text(
            text = stringResource(R.string.home_empty_headline),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )

        Spacer(Modifier.height(Space.l))
        EmptyStateStepStrip()

        Spacer(Modifier.height(Space.m))
        TextButton(onClick = onScanLabel) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Space.xs))
            Text(stringResource(R.string.home_empty_scan_label))
        }
    }
}

/**
 * Compact graphical Scan → Portion → Carbs strip. Three small icon roundels joined by connector
 * lines — deliberately not three large cards, which would read as feature tiles rather than a
 * single at-a-glance sequence.
 */
@Composable
private fun EmptyStateStepStrip(modifier: Modifier = Modifier) {
    val steps = listOf(
        Triple(Icons.Filled.QrCodeScanner, R.string.home_empty_step_scan, MaterialTheme.colorScheme.primary),
        Triple(Icons.Filled.Scale, R.string.home_empty_step_portion, MaterialTheme.colorScheme.tertiary),
        Triple(Icons.Filled.Calculate, R.string.home_empty_step_carbs, MaterialTheme.extendedColors.result),
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, (icon, labelRes, tint) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(tint.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
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
                        .padding(horizontal = Space.xs)
                        .padding(bottom = Space.l)
                        .width(20.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/**
 * Home's deliberate search entry (§9, owner request 2026-08-14): always visible, never only
 * offered after a failure. Field only — [HomeSearchResults] below owns the live results, so typing
 * here behaves exactly like typing on [app.justthecarbs.ui.search.SearchScreen].
 */
@Composable
private fun HomeSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val clearLabel = stringResource(R.string.search_clear)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        // Tapping the leading icon also submits: it sits where a "search" affordance is expected,
        // in addition to the IME action, without adding a second visible button to this compact field.
        leadingIcon = {
            IconButton(onClick = { onSearchSubmit(); focusManager.clearFocus() }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_submit))
            }
        },
        // Search is explicit: typing alone never triggers a request (Open Food Facts' search
        // endpoint is rate-limited and not meant for as-you-type traffic).
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchSubmit()
                focusManager.clearFocus()
            },
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.semantics { contentDescription = clearLabel },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = modifier.fillMaxWidth().testTag(HOME_SEARCH_FIELD_TAG),
    )
}

/** Live results for Home's inline search — the same states [app.justthecarbs.ui.search.SearchScreen] renders. */
@Composable
private fun HomeSearchResults(
    state: SearchUiState,
    onSelect: (ProductSearchHit) -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.error != null -> {
            val title = when (state.error) {
                LookupError.OFFLINE -> stringResource(R.string.error_offline_title)
                LookupError.TIMEOUT -> stringResource(R.string.error_timeout_title)
                LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_title)
                LookupError.SERVER -> stringResource(R.string.error_server_title)
                LookupError.MALFORMED -> stringResource(R.string.error_malformed_title)
            }
            val body = when (state.error) {
                LookupError.OFFLINE -> stringResource(R.string.error_offline_body)
                LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_body)
                else -> stringResource(R.string.error_generic_body)
            }
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecoveryPanel(title = title, body = body) {
                    PrimaryAction(text = stringResource(R.string.error_retry), onClick = onRetry)
                    SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
                    SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
                }
            }
        }

        state.searching && state.hits.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }

        state.noMatches -> Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RecoveryPanel(
                title = stringResource(R.string.notfound_title),
                body = stringResource(R.string.search_no_matches, state.query),
            ) {
                PrimaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
                SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
            }
        }

        // A query too short to search yet (SearchViewModel.MIN_QUERY_LENGTH) — not an error, not a
        // miss, just not enough to go on.
        state.hits.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth().padding(Space.screenEdge),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.search_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screenEdge)
                .testTag(HOME_SEARCH_RESULTS_TAG),
        ) {
            items(state.hits, key = { it.barcode }) { hit ->
                SearchResultRow(hit = hit, onClick = { onSelect(hit) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
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
