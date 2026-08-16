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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.BuildConfig
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
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
import java.math.BigDecimal

/** Stable handles for instrumented tests. */
const val HOME_SEARCH_FIELD_TAG = "home_search_field"
const val HOME_SEARCH_RESULTS_TAG = "home_search_results"
const val HOME_SCAN_BARCODE_TAG = "home_scan_barcode"
const val HOME_SCAN_LABEL_TAG = "home_scan_label"
const val HOME_FAVORITES_HEADING_TAG = "home_favorites_heading"

/**
 * Home (§6, §7).
 *
 * One primary surface, no bottom navigation. Favourites get their own labelled section directly
 * above Recent rather than a tab of their own, because a second tab would add a decision to a
 * workflow whose whole value is not having to make one (§22, §74). They were previously only sorted
 * to the top of Recent, which made them first without making them findable.
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
            //
            // Deliberately NOT hidden while searching. It used to be, on the reasoning that search
            // results are the more immediate task — but searching is precisely *how* the user finds
            // the next item to add, so the bar vanished during the one activity that most implies a
            // meal is underway. Building a three-item meal meant watching the running total
            // disappear and reappear three times, and it made the meal feel lost rather than
            // waiting.
            MealBarIfPresent(
                itemCount = mealItems.size,
                total = mealTotal,
                onClick = onOpenMeal,
                modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.xs),
            )

            // The two camera entry points, then history beneath them. Both live in the scrolling
            // region rather than pinned at the bottom, which is a deliberate change: the app's three
            // ways in must all be visible before any history (§7 of the entry-points brief), and
            // they can only read as one matched system if they sit together. At the top of the
            // scroll region they land roughly 220–300dp down — inside thumb reach and above the fold
            // on a short display — whereas pinning both would park ~180dp of permanent chrome over
            // the recents list and make Home feel like a dashboard (§2, §22).
            //
            // A non-blank query takes over this whole region with live results — Home never shows
            // both at once.
            if (searchState.query.isNotBlank()) {
                HomeSearchResults(
                    state = searchState,
                    onSelect = onSearchSelect,
                    onScanLabel = onSearchScanLabel,
                    onEnterManually = onSearchEnterManually,
                    onRetry = onSearchRetry,
                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                )
            } else {
                HomeBody(
                    recents = recents,
                    settings = settings,
                    onScan = onScan,
                    onScanLabel = onScanLabel,
                    onManualEntry = onManualEntry,
                    onOpenProduct = onOpenProduct,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                )
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The launcher mark used to be rendered here and it did not survive the move: it is a 108 dp
        // adaptive-icon vector whose two paths are white shapes designed to read against the
        // launcher's own coloured background, drawn inside a 72 dp safe zone. Tinted dark and placed
        // on a light tile, the figure and ground invert and the mark reads as an indistinct blob —
        // visible only by looking at the rendered screen. An app icon is not a general-purpose
        // illustration, and the step strip below already carries this composition's identity, so the
        // headline now leads and the strip does the visual work.
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

    // Scrolls rather than clips: the strip is a fixed-width row of roundels, and at 1.8x font scale
    // on a narrow display it otherwise runs past the screen edge with the third step cut in half.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
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
        // Labelled "Search products", not just hinted: the placeholder alone ("Product or brand
        // name") never says the word *search*, which left the magnifier glyph carrying the entire
        // discovery burden for one of the app's three ways in.
        label = { Text(stringResource(R.string.home_search_label)) },
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

/**
 * Home's resting state: the two camera entry points, then either recent products or the branded
 * starter composition.
 *
 * One `LazyColumn` rather than a fixed header plus a list, so the actions scroll with the content
 * instead of stealing a permanent band of the screen. The actions are `item`s, so on a screen with
 * many recents they scroll away exactly as a header should — they are the first thing seen, not a
 * fixture.
 */
@Composable
private fun HomeBody(
    recents: List<RecentEntry>,
    settings: AppSettings,
    onScan: () -> Unit,
    onScanLabel: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Space.screenEdge,
            end = Space.screenEdge,
            top = Space.xs,
            bottom = Space.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        item(key = "action_barcode") {
            HomeActionCard(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(R.string.home_scan_button),
                subtitle = stringResource(R.string.home_action_barcode_subtitle),
                filled = true,
                onClick = onScan,
                modifier = Modifier.testTag(HOME_SCAN_BARCODE_TAG),
            )
        }
        item(key = "action_label") {
            HomeActionCard(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.home_empty_scan_label),
                subtitle = stringResource(R.string.home_action_label_subtitle),
                filled = false,
                onClick = onScanLabel,
                modifier = Modifier.testTag(HOME_SCAN_LABEL_TAG),
            )
        }

        // Manual entry sits directly under the three real ways in, and *above* the starter hero.
        // Ordering it after the hero put it beyond the composed window at 1.8x font scale, where a
        // LazyColumn simply never composes it — the action was not merely below the fold, it did not
        // exist. The hero is reassurance; this is a function, and functions come first.
        item(key = "manual") {
            TextButton(
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Space.minTouchTarget)
                    .padding(top = Space.xs),
            ) {
                Text(stringResource(R.string.home_manual_button))
            }
        }

        if (recents.isEmpty()) {
            item(key = "starter") {
                EmptyState(modifier = Modifier.padding(top = Space.m, bottom = Space.m))
            }
        } else {
            // Favourites are the repeat-use path: a product the user has already told the app they
            // come back to. They were previously only *sorted* to the top of Recents (`ORDER BY
            // favorite DESC`), which makes them first but not findable — nothing on screen said the
            // list had two halves, so a favourite five items down looked like ordinary history.
            //
            // Split here rather than in the query so the DAO keeps returning one ordered list and
            // the section is purely presentational. An empty favourites list renders nothing at all
            // — no heading, no empty card — because a section explaining its own emptiness costs the
            // user a scroll on every launch to say nothing.
            val favorites = recents.filter { it.product.favorite }
            val others = recents.filterNot { it.product.favorite }

            if (favorites.isNotEmpty()) {
                item(key = "favorites_heading") {
                    SectionHeading(
                        text = stringResource(R.string.home_favorites_title),
                        modifier = Modifier.testTag(HOME_FAVORITES_HEADING_TAG),
                    )
                }
                items(items = favorites, key = { "fav_${it.product.barcode}" }) { entry ->
                    RecentCard(
                        entry = entry,
                        settings = settings,
                        onClick = { onOpenProduct(entry.product.barcode) },
                        onToggleFavorite = { onToggleFavorite(entry.product) },
                    )
                }
            }

            if (others.isNotEmpty()) {
                item(key = "recent_heading") {
                    SectionHeading(text = stringResource(R.string.home_recent_title))
                }
                items(items = others, key = { it.product.barcode }) { entry ->
                    RecentCard(
                        entry = entry,
                        settings = settings,
                        onClick = { onOpenProduct(entry.product.barcode) },
                        onToggleFavorite = { onToggleFavorite(entry.product) },
                    )
                }
            }
        }
    }
}

/**
 * One way into the app: an icon, what it does, and why you'd pick it over the other one.
 *
 * The two camera actions share this shape so they read as two options of one kind, and differ only
 * in weight — [filled] carries the app's primary blue and its lifted shadow, the outlined variant
 * borrows [RecentCard]'s exact surface and border so the whole column is visibly one system. The
 * icon roundel is the only place the outlined card spends colour, using the existing orange, which
 * is what keeps the two scanners distinguishable at a glance without adding a hue (§5).
 *
 * Semantics are merged into a single button node: without that, TalkBack announces the icon, the
 * title and the subtitle as three separate stops inside one tappable thing.
 */
@Composable
private fun HomeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val container = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val titleColor = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // On the filled card the subtitle must stay legible against the accent, so it is the same ink at
    // reduced opacity rather than onSurfaceVariant, which is tuned for the page background.
    val subtitleColor = if (filled) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.home_action_description, title, subtitle)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (filled) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = shape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(container)
            .then(
                if (filled) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                },
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (filled) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = Space.m)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (filled) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** One list-section label. Extracted so Favourites and Recent cannot drift apart visually. */
@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = Space.m, bottom = Space.xs)
            .semantics { heading() },
    )
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
    //
    // The label and the figure both come from one `rememberedCarbs` decision. Reading `lastPortion`
    // to compute while reading `lastCount` to label was the P0 defect: `lastPortion` legitimately
    // survives a direct-carb use, so a product last eaten as "4 slices" could print that count over
    // a number scaled from a stale gram amount left by an earlier weight-based use.
    val summary = rememberedCarbs(product, entry.lastUnit)?.let { remembered ->
        // Countable-portions brief §12: when the product was last used as "2 slices", Recents says
        // so — not the gram amount the user never actually thought in.
        val amountLabel = when (remembered) {
            is RememberedCarbs.Countable ->
                // Plural agreement follows the count, so a remembered single portion reads "1
                // slice" rather than the "1 slices" the previous hardcoded `count = 2` produced.
                // Anything that is not exactly one takes the plural, which keeps a fractional
                // count ("1.5 slices") correct rather than truncating it to the singular.
                "${remembered.count.stripTrailingZeros().toPlainString()} " +
                    entry.lastUnit!!.unitLabel(
                        count = if (remembered.count.compareTo(BigDecimal.ONE) == 0) 1 else 2,
                    )

            is RememberedCarbs.Weight ->
                "${remembered.portion.stripTrailingZeros().toPlainString()} ${product.portionUnit}"
        }
        // Follows the user's configured result style (§18). Recents previously always showed the
        // whole gram while the calculator led with the decimal, so the same portion of the same
        // product read as "30 g" here and "30.2 g" one tap away — the app appearing to disagree
        // with itself about a number the user is about to rely on.
        val carbsLabel = when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(remembered.exactCarbs)} g"
            ResultStyle.WHOLE_DOMINANT ->
                "${ResultFormatter.whole(ResultFormatter.wholeGrams(remembered.exactCarbs))} g"
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
