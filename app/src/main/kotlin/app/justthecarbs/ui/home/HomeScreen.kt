package app.justthecarbs.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import app.justthecarbs.ui.components.ProteinToggle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.justthecarbs.domain.StaleMeal
import app.justthecarbs.ui.components.AccentBackdrop
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.ProductThumbnail
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.components.SearchResultRow
import app.justthecarbs.ui.components.dismissKeyboardOnTouch
import app.justthecarbs.ui.components.SecondaryAction
import app.justthecarbs.ui.meal.MealBarIfPresent
import app.justthecarbs.ui.product.unitLabel
import app.justthecarbs.ui.search.SearchInformationalState
import app.justthecarbs.ui.search.SearchResultsNotice
import app.justthecarbs.ui.search.rememberSearchResultsListState
import app.justthecarbs.ui.search.shownResultKeys
import app.justthecarbs.ui.search.SearchProgressLine
import app.justthecarbs.ui.search.SearchingLine
import app.justthecarbs.ui.search.SearchViewModel
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.components.jtcTextFieldColors
import app.justthecarbs.ui.meal.StaleMealDialog
import app.justthecarbs.ui.search.searchResultItems
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Stable handles for instrumented tests. */
const val HOME_BACKDROP_TAG = "home_backdrop"
const val HOME_SEARCH_FIELD_TAG = "home_search_field"
const val HOME_SEARCH_SUBMIT_TAG = "home_search_submit"
const val HOME_MANUAL_TAG = "home_manual_entry"

/** Home's `Show protein` chip, beside *Enter manually*. */
const val HOME_PROTEIN_TAG = "home_protein_toggle"
const val HOME_BODY_TAG = "home_body"
const val HOME_SEARCH_RESULTS_TAG = "home_search_results"
const val HOME_SEARCH_REFRESH_ERROR_TAG = "home_search_refresh_error"
const val HOME_SEARCH_ONLINE_ERROR_TAG = "home_search_online_error"
const val HOME_SEARCH_RATE_LIMITED_TAG = "home_search_rate_limited"
const val HOME_SEARCH_PENDING_TAG = "home_search_pending"
const val HOME_SEARCH_PROGRESS_TAG = "home_search_progress"
const val HOME_SEARCH_SEARCHING_TAG = "home_search_searching"
const val HOME_SCAN_BARCODE_TAG = "home_scan_barcode"
const val HOME_SCAN_LABEL_TAG = "home_scan_label"
const val HOME_FAVORITES_HEADING_TAG = "home_favorites_heading"
const val HOME_RECENT_FORGET_TAG = "home_recent_forget"
const val HOME_SNACKBAR_TAG = "home_snackbar"
const val HOME_QUICK_ADD_TAG = "home_quick_add"
const val HOME_TUTORIAL_REMINDER_TAG = "home_tutorial_reminder"
const val HOME_TUTORIAL_START_TAG = "home_tutorial_start"
const val HOME_TUTORIAL_DISMISS_TAG = "home_tutorial_dismiss"

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
    onForgetRecent: (Product) -> Unit = {},
    forgotten: ForgottenRecent? = null,
    onUndoForgetRecent: () -> Unit = {},
    onForgetUndoExpired: () -> Unit = {},
    quickAddStatus: Map<String, QuickAddStatus> = emptyMap(),
    onQuickAdd: (RecentEntry, String) -> Unit = { _, _ -> },
    quickAddEvents: Flow<QuickAddEvent> = emptyFlow(),
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
    showTutorialReminder: Boolean = false,
    onStartTutorial: () -> Unit = {},
    onDismissTutorialReminder: () -> Unit = {},
    /** A Quick Add waiting on the stale-meal question; null when none is. */
    staleMeal: StaleMeal? = null,
    onResolveStaleMeal: (Boolean) -> Unit = {},
    onDismissStaleMeal: () -> Unit = {},
    /** Writes the one persisted protein setting, the same one the Settings row writes. */
    onProteinChanged: (Boolean) -> Unit = {},
) {
    staleMeal?.let {
        StaleMealDialog(
            staleMeal = it,
            onStartNewMeal = { onResolveStaleMeal(true) },
            onAddToMeal = { onResolveStaleMeal(false) },
            onDismiss = onDismissStaleMeal,
        )
    }

    // Back peels one layer at a time: keyboard, then search, then the app.
    //
    // **This replaces a handler keyed on the query alone, which destroyed the search it was meant
    // to protect.** It read `enabled = query.isNotBlank()` and cleared the query outright, so the
    // universal Android gesture for "put the keyboard away" — the first Back after typing — threw
    // away the query, the results and the in-flight request together. The user had asked for less
    // keyboard and lost the whole search; the only route back was to type it again, and a
    // re-typed query costs a fresh Open Food Facts request against a 10/min budget.
    //
    // The keyboard's visibility is read from the real IME inset, never inferred from the query.
    // Those two facts are independent — a query is non-blank for as long as results are on screen,
    // which is mostly with the keyboard down — and conflating them is precisely the defect above.
    //
    // Dismissing the IME touches nothing else: no `onSearchQueryChanged("")`, so the query, the
    // hits, and any searching/pending state are all exactly as they were. Only the second press,
    // once the keyboard is genuinely gone, clears the search. With no query at all neither branch
    // is enabled and Back falls through to the platform default (Home is the start destination, so
    // that is closing the app), unchanged.
    //
    // Deliberately not migrated to PredictiveBackHandler in the 2026-09-14 interaction pass —
    // neither branch navigates anywhere (Home is the start destination); they dismiss a keyboard
    // and clear a field. A predictive-back gesture would show the system's close/home preview
    // mid-drag for an action that, on release, keeps the app open — a misleading preview rather
    // than a cleanup-ordering hazard, but still a reason to leave these as ordinary handlers; see
    // docs/superpowers/specs/2026-09-14-interaction-polish-design.md.
    val searchFocusManager = LocalFocusManager.current
    // Derived, so only the keyboard's arrival and departure recompose Home, not every frame of its
    // slide (the inset changes each frame while the IME animates).
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }

    // **There is deliberately NO handler for the keyboard-up case, and that is the fix.** The
    // platform already dismisses the IME on Back before the press reaches any app handler, so the
    // only thing this screen has to do is stay out of the way while that happens: `!imeVisible`
    // disables the clear, and the press does its ordinary job.
    //
    // Measured on the emulator rather than assumed, because the obvious implementation is wrong in
    // a way that looks right. With no handler registered, Back moves the IME inset 883px -> 0. With
    // an IME-gated handler registered -- one that calls `focusManager.clearFocus()`, which is what
    // this pass first shipped -- the handler fires and the inset stays at 883: the app has
    // swallowed the very press the system needed, and `clearFocus()` does not close a keyboard. The
    // first Back would then appear to do nothing at all, which is worse than the defect being
    // fixed.
    BackHandler(enabled = !imeVisible && searchState.query.isNotBlank()) {
        onSearchQueryChanged("")
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // One Snackbar per removal, keyed on the barcode so a second removal replaces the first rather
    // than queueing behind it — a queue would let the user tap Undo and restore a product they
    // forgot two actions ago. Exactly the meal screen's rule, for exactly the same reason.
    val forgottenMessage = forgotten?.let { stringResource(R.string.recent_forgotten, it.name) }
    val undoAction = stringResource(R.string.action_undo)
    LaunchedEffect(forgotten?.snapshot?.barcode) {
        if (forgotten == null || forgottenMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = forgottenMessage,
            actionLabel = undoAction,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndoForgetRecent()
            // Timed out or replaced: the removal stands, and the held snapshot is dropped so a stale
            // action cannot restore it later.
            SnackbarResult.Dismissed -> onForgetUndoExpired()
        }
    }

    // Quick Add's outcomes. Success is confirmed by touch and by the card itself — the pill turns
    // to "Added" and the meal bar's count and total change — and deliberately not by a Snackbar:
    // adding a remembered portion is the routine path, and a message after every routine success
    // is noise that also covers the list the user is still working through. Only a failure speaks,
    // because a write that did not happen must never look like one that did.
    val haptics = LocalHapticFeedback.current
    val currentHapticsEnabled by rememberUpdatedState(settings.hapticsEnabled)
    val resources = LocalResources.current
    // The meal bar's acknowledgement: a brief glow and lift on the bar the portion just joined, so
    // the eye travels from the card to the meal. Keyed on Quick Add's own success event and nothing
    // else — deriving it from the item count would also fire when Home first loads its meal from
    // storage, which is not something the user did.
    val mealBarPulse = remember { Animatable(0f) }
    val pulseScope = rememberCoroutineScope()
    LaunchedEffect(quickAddEvents) {
        quickAddEvents.collect { event ->
            when (event) {
                is QuickAddEvent.Added -> {
                    if (currentHapticsEnabled) {
                        // Fired when the write has landed, not on touch-down: the buzz is a statement
                        // that the meal changed. Insertion takes milliseconds, so it still reads as
                        // immediate.
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    pulseScope.launch {
                        mealBarPulse.snapTo(1f)
                        mealBarPulse.animateTo(0f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
                    }
                }
                is QuickAddEvent.Failed -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.recent_quick_add_failed, event.name),
                    withDismissAction = false,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The bars sit behind the wordmark, so they fold away with it while a query is typed: left
        // behind, their stubs poked out between the search field and the meal bar (seen on the
        // emulator) and read as a rendering fault. A fade only: they are decoration and take no
        // layout room.
        AnimatedVisibility(
            visible = searchState.query.isBlank(),
            enter = fadeIn(tween(Motion.STANDARD_MS)),
            exit = fadeOut(tween(Motion.QUICK_MS)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            AccentBackdrop(
                accent = MaterialTheme.colorScheme.primary,
                // Keep the decorative nutrition bars clear of the Settings touch target.
                modifier = Modifier
                    .padding(end = Space.xxl + Space.l)
                    .testTag(HOME_BACKDROP_TAG),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // The wordmark gives its room to results while a query is typed: with it, a 1080x2400
            // phone showed about two and a half results above the keyboard. Clearing the search
            // brings it back. Settings goes with it and is one Back away.
            AnimatedVisibility(
                visible = searchState.query.isBlank(),
                enter = expandVertically(tween(Motion.STANDARD_MS)) + fadeIn(tween(Motion.STANDARD_MS)),
                exit = shrinkVertically(tween(Motion.STANDARD_MS)) + fadeOut(tween(Motion.QUICK_MS)),
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
            //
            // Its arrival is animated because Quick Add can now start a meal from this very screen:
            // the first item makes the bar appear *above* the list, and appearing in one frame
            // shoved the card the user had just tapped 60dp down from under their thumb. Expanding
            // moves the list at the same pace the bar grows, so the eye can follow it. On
            // entering Home with a meal already underway it is simply there — AnimatedVisibility
            // does not animate its initial state.
            AnimatedVisibility(
                visible = mealItems.isNotEmpty() && mealTotal != null,
                enter = expandVertically(tween(Motion.STANDARD_MS)) + fadeIn(tween(Motion.STANDARD_MS)),
                exit = shrinkVertically(tween(Motion.STANDARD_MS)) + fadeOut(tween(Motion.QUICK_MS)),
            ) {
                val glow = MaterialTheme.colorScheme.primary
                MealBarIfPresent(
                    itemCount = mealItems.size,
                    total = mealTotal,
                    onClick = onOpenMeal,
                    modifier = Modifier
                        .padding(horizontal = Space.screenEdge, vertical = Space.xs)
                        .graphicsLayer {
                            val lift = 1f + 0.025f * mealBarPulse.value
                            scaleX = lift
                            scaleY = lift
                        }
                        .drawWithContent {
                            drawContent()
                            val p = mealBarPulse.value
                            if (p > 0f) {
                                val radius = CornerRadius(Space.cardRadius.toPx())
                                drawRoundRect(color = glow.copy(alpha = 0.14f * p), cornerRadius = radius)
                                drawRoundRect(
                                    color = glow.copy(alpha = 0.7f * p),
                                    cornerRadius = radius,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        },
                )
            }

            // Held here, above the switch between search and body, so clearing a search returns
            // to Recents where they were rather than to the top: the body leaves composition while
            // results are shown, and a list state remembered inside it would go with it.
            val bodyListState = rememberLazyListState()

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
                    onScan = onScan,
                    onScanLabel = onSearchScanLabel,
                    onEnterManually = onSearchEnterManually,
                    onRetry = onSearchRetry,
                    onListTouched = { searchFocusManager.clearFocus() },
                    // Stops at the keyboard. Edge-to-edge means `adjustResize` no longer shrinks the
                    // window, so without this the region ran on underneath the IME and the centred
                    // recovery panels put their actions behind it (measured: "Enter manually" at
                    // y=1568, keyboard top at y=1517). Search is the one Home state typed into.
                    modifier = Modifier.weight(1f).navigationBarsPadding().imePadding(),
                )
            } else {
                HomeBody(
                    listState = bodyListState,
                    recents = recents,
                    settings = settings,
                    onScan = onScan,
                    onScanLabel = onScanLabel,
                    onManualEntry = onManualEntry,
                    onOpenProduct = onOpenProduct,
                    onToggleFavorite = onToggleFavorite,
                    onForgetRecent = onForgetRecent,
                    quickAddStatus = quickAddStatus,
                    onQuickAdd = onQuickAdd,
                    showTutorialReminder = showTutorialReminder,
                    onStartTutorial = onStartTutorial,
                    onDismissTutorialReminder = onDismissTutorialReminder,
                    mealInProgress = mealItems.isNotEmpty(),
                    onProteinChanged = onProteinChanged,
                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                )
            }
        }

        // Bottom-anchored, unlike the meal screen's — and the divergence is deliberate rather than
        // an inconsistency. The meal's Snackbar is pinned to the top because the bottom of that
        // screen is its total panel, the one number it exists to show. Home has no pinned bottom
        // content, and the Undo here is time-limited: at the bottom its action sits in thumb reach
        // one-handed, which is how this app is used, instead of at the far top of a tall phone.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.screenEdge, vertical = Space.s)
                .testTag(HOME_SNACKBAR_TAG),
        ) { data ->
            // Custom content purely to cap the message at two lines. Product names run long —
            // "Griekse yoghurt met honing en walnoten, 0% vet" is an ordinary Open Food Facts name —
            // and the default Snackbar grows to fit, which measured four lines at a 1.8x font scale.
            // The name is what gets clipped, and that is the right thing to lose: the sentence opens
            // with the fact (something was removed) and the Undo beside it stays put either way.
            Snackbar(
                shape = RoundedCornerShape(Space.buttonRadius),
                action = data.visuals.actionLabel?.let { label ->
                    {
                        TextButton(
                            onClick = { data.performAction() },
                            colors = ButtonDefaults.textButtonColors(
                                // The colour Snackbar would have used itself; `Theme.kt` sets
                                // `inversePrimary` behind it, so Undo keeps the app's own blue
                                // rather than Material's default lavender.
                                contentColor = SnackbarDefaults.actionContentColor,
                            ),
                        ) {
                            Text(label)
                        }
                    }
                },
            ) {
                Text(
                    text = data.visuals.message,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.m))
        // The launcher mark used to be rendered here and it did not survive the move: it is a 108 dp
        // adaptive-icon vector whose two paths are white shapes designed to read against the
        // launcher's own coloured background, drawn inside a 72 dp safe zone. Tinted dark and placed
        // on a light tile, the figure and ground invert and the mark reads as an indistinct blob —
        // visible only by looking at the rendered screen. An app icon is not a general-purpose
        // illustration, and the step strip below already carries this composition's identity, so the
        // headline now leads and the strip does the visual work.
        Text(
            text = stringResource(R.string.home_empty_headline),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 360.dp),
        )

        Spacer(Modifier.height(Space.m))
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

    // Replaces the previous horizontalScroll fallback, which the code's own prior comment admitted
    // ran the third step past the screen edge at 1.8x font scale on a narrow display. BoxWithConstraints
    // measures the actual available width and switches to a compact vertical arrangement below the
    // threshold, so all three steps are always fully readable without scrolling to see them.
    BoxWithConstraints(modifier = modifier) {
        // 320dp is this repo's own documented historical minimum Android width (see
        // CLAUDE.md's tap-anywhere-tutorial completion pass, which uses the same figure for its
        // own narrow-viewport test) — a plain, already-established threshold rather than a new
        // guess.
        val compact = maxWidth < 320.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                steps.forEach { (icon, labelRes, tint) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepRoundel(icon, tint)
                        Spacer(Modifier.width(Space.s))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.forEachIndexed { index, (icon, labelRes, tint) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepRoundel(icon, tint)
                        Spacer(Modifier.width(Space.s))
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
                                .width(16.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRoundel(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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
        // The placeholder now says "Search products" and there is no floating `label`.
        //
        // The label was added because the old placeholder ("Product or brand name") never used the
        // word *search*, leaving the magnifier glyph to carry the whole affordance. That problem is
        // real and this keeps the fix -- it just moves the words into the placeholder rather than
        // showing both. A floating label on a search bar animates up on focus and then sits in the
        // field's top border, which is one more moving edge on the screen the pass is de-cluttering,
        // and search bars across the platform are placeholder-only.
        placeholder = { Text(stringResource(R.string.home_search_label)) },
        // Tapping the leading icon also submits: it sits where a "search" affordance is expected,
        // in addition to the IME action, without adding a second visible button to this compact field.
        //
        // Sized explicitly, like every other IconButton in the app. A text field's decoration slots
        // constrain their content, so an unsized IconButton here measured 40dp rather than the
        // Material default 48 — measured at 105px on a 420dpi device. Undersized targets are hardest
        // to hit exactly where this app is used: one-handed, in a shop, often in a hurry.
        leadingIcon = {
            IconButton(
                onClick = { onSearchSubmit(); focusManager.clearFocus() },
                modifier = Modifier.size(Space.minTouchTarget).testTag(HOME_SEARCH_SUBMIT_TAG),
            ) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_submit))
            }
        },
        // Typing searches by itself, debounced in the ViewModel so a typed word costs one request
        // rather than one per keystroke (Open Food Facts' search endpoint allows 10 reads/min/IP).
        // The IME action still submits, skipping the wait for anyone who has finished typing; both
        // go through the same request pipeline so neither duplicates the other's call.
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
                    modifier = Modifier
                        .size(Space.minTouchTarget)
                        .semantics { contentDescription = clearLabel },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        shape = RoundedCornerShape(Space.buttonRadius),
        // The shared field colours: a quiet fill with no border at rest, primary only on focus.
        // The permanent `outlineVariant` border this replaces was the topmost of the stacked edges
        // that made Home read as a column of boxes.
        colors = jtcTextFieldColors(),
        modifier = modifier.fillMaxWidth().testTag(HOME_SEARCH_FIELD_TAG),
    )
}

/** Live results for Home's inline search — the same states [app.justthecarbs.ui.search.SearchScreen] renders. */
@Composable
private fun HomeSearchResults(
    state: SearchUiState,
    onSelect: (ProductSearchHit) -> Unit,
    onScan: () -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onListTouched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Above the state `when`, so it outlives the searching line a refinement passes through.
    val resultsListState = rememberSearchResultsListState(state.shownResultKeys)

    Column(modifier = modifier.fillMaxWidth()) {
        // The same hairline as the search screen, in the same place, for a first search as well as
        // a refresh. Home had no refresh indication at all until the smoothing pass, and its first
        // search showed a centred spinner until 2026-09-17.
        SearchProgressLine(searching = state.searching, tag = HOME_SEARCH_PROGRESS_TAG)

        val region = Modifier.weight(1f)
        when {
            // Results first, for the same reason as SearchScreen: a refresh failure carries an error
            // but costs the user nothing, so it must not take the region away from a usable list.
            state.hasResults -> Column(modifier = region.fillMaxWidth()) {
                // No Retry while rate limited — the queued query resumes by itself, and a button there
                // would invite the hammering the backoff exists to stop. Same rule, and the same
                // composable, as SearchScreen.
                SearchResultsNotice(
                    state = state,
                    onRetry = onRetry,
                    rateLimitedTag = HOME_SEARCH_RATE_LIMITED_TAG,
                    refreshErrorTag = HOME_SEARCH_REFRESH_ERROR_TAG,
                    onlineErrorTag = HOME_SEARCH_ONLINE_ERROR_TAG,
                )
                LazyColumn(
                    state = resultsListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Space.screenEdge)
                        // Parity with the search screen: reaching for the list puts the keyboard
                        // away without spending the touch. Home's inline results had no such
                        // handler at all, so the only way to see more than half a list here was
                        // the Back button -- which, before this pass, deleted the search.
                        .dismissKeyboardOnTouch(onListTouched)
                        .testTag(HOME_SEARCH_RESULTS_TAG),
                ) {
                    searchResultItems(state, onSelect)
                }
            }

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
                Box(modifier = region.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RecoveryPanel(title = title, body = body) {
                        PrimaryAction(text = stringResource(R.string.error_retry), onClick = onRetry)
                        // The barcode is the other way to the same product, and Home (unlike the
                        // search screen, which a failed barcode lookup opens) has not tried it yet.
                        SecondaryAction(text = stringResource(R.string.home_scan_button), onClick = onScan)
                        SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
                        SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
                    }
                }
            }

            // Waiting on the shared budget with nothing to show. A word, not a spinner: a spinner held
            // for several seconds promises a request that has not been sent and reads as a hang.
            //
            // Placed at the top of the region rather than centred in it — see SearchInformationalState
            // for the measured reason, which bites hardest here because Home's keyboard is usually open.
            state.awaitingRemotePermit -> SearchInformationalState(modifier = region) {
                Text(
                    text = stringResource(
                        if (state.rateLimited) R.string.search_rate_limited else R.string.search_updating,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(HOME_SEARCH_PENDING_TAG),
                )
            }

            state.searching -> SearchingLine(tag = HOME_SEARCH_SEARCHING_TAG, modifier = region)

            state.noMatches -> Box(modifier = region.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecoveryPanel(
                    title = stringResource(R.string.search_no_matches_title),
                    body = stringResource(R.string.search_no_matches, state.query),
                ) {
                    PrimaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
                    // Same reason as the failure panel above: a name that matched nothing says
                    // nothing about the barcode, which Home has not tried.
                    SecondaryAction(text = stringResource(R.string.home_scan_button), onClick = onScan)
                    SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
                }
            }

            // A query too short to search yet (SearchViewModel.MIN_QUERY_LENGTH) — not an error, not a
            // miss, just not enough to go on.
            //
            // A *submission* refused for being too short says so, exactly as SearchScreen does. Home
            // rendered only the generic prompt here, so tapping the magnifier with "ha" typed changed
            // nothing on screen and read as the button having missed rather than as the app declining.
            // Typing alone still never reaches this: `queryTooShort` is set only by an explicit search.
            else -> SearchInformationalState(modifier = region) {
                Text(
                    text = if (state.queryTooShort) {
                        pluralStringResource(
                            R.plurals.search_too_short,
                            SearchViewModel.MIN_QUERY_LENGTH,
                            SearchViewModel.MIN_QUERY_LENGTH,
                        )
                    } else {
                        stringResource(R.string.search_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // Announced on change so a TalkBack user hears the refusal; the plain prompt is
                    // deliberately not a live region, or it would announce over the user's own typing.
                    modifier = if (state.queryTooShort) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                )
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
    listState: LazyListState,
    recents: List<RecentEntry>,
    settings: AppSettings,
    onScan: () -> Unit,
    onScanLabel: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onForgetRecent: (Product) -> Unit,
    quickAddStatus: Map<String, QuickAddStatus>,
    onQuickAdd: (RecentEntry, String) -> Unit,
    showTutorialReminder: Boolean,
    onStartTutorial: () -> Unit,
    onDismissTutorialReminder: () -> Unit,
    mealInProgress: Boolean,
    modifier: Modifier = Modifier,
    onProteinChanged: (Boolean) -> Unit = {},
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag(HOME_BODY_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Space.screenEdge,
            end = Space.screenEdge,
            top = Space.xs,
            bottom = Space.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // The tutorial, offered rather than imposed (owner instruction, 2026-09-08). First in the
        // column so a new user meets it before the actions it explains, and gone for good the
        // moment it is taken or dismissed — see `TutorialReminder` for the window.
        if (showTutorialReminder) {
            item(key = "tutorial_reminder") {
                TutorialReminderCard(
                    onStart = onStartTutorial,
                    onDismiss = onDismissTutorialReminder,
                )
            }
        }

        item(key = "action_barcode") {
            HomeActionCard(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(
                    if (mealInProgress) R.string.home_scan_next_button else R.string.home_scan_button,
                ),
                // With the protein reading on, the primary way in says what it will now read. Mid-meal
                // the meal copy wins and the chip alone states the mode. The label tile never
                // changes: label scanning does not read protein in this version.
                subtitle = stringResource(
                    when {
                        mealInProgress -> R.string.home_action_barcode_subtitle_mid_meal
                        settings.proteinEnabled -> R.string.home_action_barcode_subtitle_protein
                        else -> R.string.home_action_barcode_subtitle
                    },
                ),
                accent = MaterialTheme.colorScheme.primary,
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
                accent = MaterialTheme.extendedColors.accents.teal,
                onClick = onScanLabel,
                modifier = Modifier.testTag(HOME_SCAN_LABEL_TAG),
            )
        }

        // Manual entry sits directly under the three real ways in, and *above* the starter hero.
        // Ordering it after the hero put it beyond the composed window at 1.8x font scale, where a
        // LazyColumn simply never composes it — the action was not merely below the fold, it did not
        // exist. The hero is reassurance; this is a function, and functions come first.
        //
        // The same row closes the cluster with the `Show protein` chip at its trailing edge: the one
        // modifier of what these ways in will read (design spec 2026-09-24, section 2). The item
        // keeps its key, so tests that scroll to it by key still find it. A wrapping row, because
        // at large text the two do not fit on one line; the chip then drops under *Enter
        // manually*, start-aligned, and the footer grows by one chip row whether protein is on or
        // off. At ordinary text they share one line down to a 320dp window (2026-09-25 review).
        item(key = "manual") {
            // Modifier order is load-bearing here, and getting it wrong is invisible. `.height()`
            // before `.padding()` applies the padding *inside* the 48dp box, so the button measured
            // 44dp — the explicit minimum was being silently eaten by the very line meant to space
            // it. Padding first (now on the row), then a minimum height on the button itself;
            // `heightIn` rather than `height` so the row still grows with the text at a large font
            // scale. No `fillMaxWidth` on the button: in a wrapping row a full-width child takes the
            // whole first line and would push the chip under it every time.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onManualEntry,
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(HOME_MANUAL_TAG),
                ) {
                    Text(stringResource(R.string.home_manual_button))
                }
                ProteinToggle(
                    checked = settings.proteinEnabled,
                    onCheckedChange = onProteinChanged,
                    modifier = Modifier.testTag(HOME_PROTEIN_TAG),
                )
            }
        }

        if (recents.isEmpty()) {
            item(key = "starter") {
                // A LazyColumn item already follows the entry points. Giving this final item a
                // whole viewport inserts a large blank band before its headline and pushes it
                // beyond the initial screen on short devices.
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
                        modifier = Modifier.animateItem().testTag(HOME_FAVORITES_HEADING_TAG),
                    )
                }
                // The bare barcode, the same key the card has under Recent. The two sections are
                // `filter`/`filterNot` of one list, so a product is in exactly one of them and the
                // key is still unique; sharing it is what lets `animateItem` move a starred card up
                // into Favourites instead of deleting it in one place and inserting it in another.
                items(items = favorites, key = { it.product.barcode }) { entry ->
                    RecentCard(
                        entry = entry,
                        settings = settings,
                        onClick = { onOpenProduct(entry.product.barcode) },
                        onToggleFavorite = { onToggleFavorite(entry.product) },
                        onForget = { onForgetRecent(entry.product) },
                        quickAddStatus = quickAddStatus[entry.product.barcode],
                        onQuickAdd = { description -> onQuickAdd(entry, description) },
                        // Forgetting a product's usage removes it from this list unless it is
                        // starred, and a row that simply stops existing between two frames reads as
                        // a glitch. `animateItem` fades and closes the gap instead, and it also
                        // covers the item *arriving* when Undo puts it back — the same motion in
                        // reverse, which is what makes the pair feel like one reversible action.
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (others.isNotEmpty()) {
                item(key = "recent_heading") {
                    SectionHeading(
                        text = stringResource(R.string.home_recent_title),
                        modifier = Modifier.animateItem(),
                    )
                }
                items(items = others, key = { it.product.barcode }) { entry ->
                    RecentCard(
                        entry = entry,
                        settings = settings,
                        onClick = { onOpenProduct(entry.product.barcode) },
                        onToggleFavorite = { onToggleFavorite(entry.product) },
                        onForget = { onForgetRecent(entry.product) },
                        quickAddStatus = quickAddStatus[entry.product.barcode],
                        onQuickAdd = { description -> onQuickAdd(entry, description) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * One way into the app: an icon, what it does, and why you'd pick it over the other one.
 *
 * The barcode path is the fastest common case, so it owns Home's one filled action. Label scan is
 * equally discoverable through size and placement but quieter through a paper surface and accent
 * edge. Hierarchy now comes from role instead of two unrelated gradients competing at full volume.
 *
 * Semantics are merged into a single button node: without that, TalkBack announces the icon, the
 * title and the subtitle as three separate stops inside one tappable thing.
 */
@Composable
/**
 * `internal` so `TutorialPreview` can draw the real card rather than an imitation of it.
 *
 * Safe to share because it is purely presentational: every parameter is appearance plus one
 * `onClick`, there is no ViewModel, no navigation and no state of its own. The tutorial passes a
 * no-op click, and its preview root already carries `clearAndSetSemantics {}`, so the card's own
 * `Role.Button` cannot be reached by TalkBack inside the overlay.
 */
internal fun HomeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    filled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val description = stringResource(R.string.home_action_description, title, subtitle)
    // `primaryTile`, not the passed `accent`, for the filled variant.
    //
    // In Light the two are the same cobalt. In Dark `colorScheme.primary` is the pale `#82A2FF`,
    // which spread over an 88dp tile made the tile the brightest object on the screen -- brighter
    // than the coral carbohydrate result it must never outrank. See ExtendedColors.primaryTile.
    val containerColor = if (filled) {
        MaterialTheme.extendedColors.primaryTile
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (filled) {
        MaterialTheme.extendedColors.onPrimaryTile
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (filled) {
        MaterialTheme.extendedColors.onPrimaryTile.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            // Flat, and outlined with the ordinary card hairline rather than a tinted accent border.
            //
            // Two changes, both about edges. The filled tile had a 5dp accent-tinted shadow: the
            // result dock is meant to be the only elevated surface in the app, and a second one on
            // Home diluted that to "elevation is decoration". The outlined tile had a 1dp border at
            // 52% of its destination accent -- the teal ring the report singles out -- which made
            // the quiet sibling of a pair read as a differently-coloured object rather than as the
            // same object, quieter. The teal now appears only inside the icon plate, which is the
            // one place a destination accent is allowed at this size.
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
            .padding(horizontal = Space.m, vertical = Space.m + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon plate is where a destination accent is allowed to appear, and the only place
        // on this card: a 40dp square at `plateRadius`, 10-16% tint, accent-tinted icon.
        Box(
            modifier = Modifier
                .size(Space.minTouchTarget)
                .background(
                    if (filled) contentColor.copy(alpha = 0.16f) else accent.copy(alpha = 0.12f),
                    RoundedCornerShape(Space.plateRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                // The accent on the quiet tile, so the pair is one loud and one quiet version of
                // the same anatomy rather than two different cards.
                tint = if (filled) contentColor else accent,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = Space.m)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (filled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The tutorial invitation (owner instruction, 2026-09-08).
 *
 * Deliberately a quiet card rather than a dialog or a full-screen takeover. The welcome carousel is
 * the app's one gate and it is already behind the user by the time this appears; someone who knows
 * the app must be able to ignore or dismiss this in one tap rather than be walked through a second
 * one. It is outlined in the app's own card idiom — not the filled gradient the two scan actions use
 * — so it reads as an offer sitting above the real work, never as a fourth way in.
 *
 * *Not now* is a real, permanent answer, not a snooze: it sets the same `hasSeenTutorial` flag that
 * finishing and skipping set, so the reminder never returns. Replaying stays available in Settings,
 * which is what makes a permanent dismissal safe to offer.
 */
@Composable
private fun TutorialReminderCard(onStart: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val accent = MaterialTheme.extendedColors.accents.violet

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            // The ordinary card hairline. A violet-tinted border made this the third differently
            // outlined object in Home's first screenful, alongside the search field and the teal
            // label tile -- the violet now lives only in the icon plate.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(Space.m)
            .testTag(HOME_TUTORIAL_REMINDER_TAG),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    // A square at `plateRadius`, like every other icon plate in the app. The
                    // circle here was the odd one out: four plate shapes existed across the app
                    // (48/12, 36/10, 36 circle, 40 circle) for one job.
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(Space.plateRadius)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Space.s))
            Text(
                text = stringResource(R.string.home_tutorial_reminder_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.home_tutorial_reminder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = Space.minTouchTarget)
                    .testTag(HOME_TUTORIAL_DISMISS_TAG),
            ) {
                Text(stringResource(R.string.home_tutorial_reminder_dismiss))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier
                    .heightIn(min = Space.minTouchTarget)
                    .testTag(HOME_TUTORIAL_START_TAG),
            ) {
                Text(stringResource(R.string.home_tutorial_reminder_start))
            }
        }
    }
}

/** One list-section label. Extracted so Favourites and Recent cannot drift apart visually. */
@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
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
    onForget: () -> Unit,
    quickAddStatus: QuickAddStatus?,
    onQuickAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = entry.product
    // Recompute rather than store a display string, so a corrected carbs value is reflected in the
    // summary immediately instead of showing a figure derived from the old one.
    //
    // The label and the figure both come from one `rememberedCarbs` decision. Reading `lastPortion`
    // to compute while reading `lastCount` to label was the P0 defect: `lastPortion` legitimately
    // survives a direct-carb use, so a product last eaten as "4 slices" could print that count over
    // a number scaled from a stale gram amount left by an earlier weight-based use.
    val remembered = rememberedCarbs(product, entry.lastUnit)

    val portionLabel = remembered?.let {
        // Countable-portions brief §12: when the product was last used as "2 slices", Recents says
        // so — not the gram amount the user never actually thought in.
        when (it) {
            is RememberedCarbs.Countable ->
                // Plural agreement follows the count, so a remembered single portion reads "1
                // slice" rather than the "1 slices" the previous hardcoded `count = 2` produced.
                // Anything that is not exactly one takes the plural, which keeps a fractional
                // count ("1.5 slices") correct rather than truncating it to the singular.
                //
                // `ResultFormatter.editable` — the formatting the calculator pre-fills and writes
                // into a meal line — because this exact string now also *becomes* the meal line
                // when Quick Add is tapped, and it must read the same as one added from the
                // calculator ("1,5 slices" on a comma-decimal device, not "1.5 slices").
                "${ResultFormatter.editable(it.count)} " +
                    entry.lastUnit!!.unitLabel(
                        count = if (it.count.compareTo(BigDecimal.ONE) == 0) 1 else 2,
                    )
            is RememberedCarbs.Weight ->
                "${ResultFormatter.editable(it.portion)} ${product.portionUnit}"
        }
    } ?: stringResource(R.string.recent_never_used)

    // Offered only when the remembered use can be rebuilt without guessing — see `quickAddPlan`.
    // Computed from the same `rememberedCarbs` decision as the figure on this card, so what the
    // pill adds is always the number the card shows.
    val quickAddable = remember(product, entry.lastUnit) { quickAddPlan(product, entry.lastUnit) != null }

    // Unchanged rule: follows the user's configured result style, so Recents and the calculator
    // cannot print different numbers for the same portion of the same product.
    val carbsLabel = remembered?.let {
        when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(it.exactCarbs)} g"
            ResultStyle.WHOLE_DOMINANT ->
                "${ResultFormatter.whole(ResultFormatter.wholeGrams(it.exactCarbs))} g"
        }
    }

    // No spine. It was a 4dp accent bar down the card's leading edge, blue on an ordinary recent
    // and violet on a favourite -- a second, quieter statement of a fact the filled star already
    // makes unambiguously, spending the interaction colour on a decoration attached to every row.
    //
    // The accessibility rule it was written under is unaffected and still holds: the favourite
    // state was NEVER carried by the spine's colour alone, which is exactly why removing it costs
    // nothing. The star is filled or not, and `FavoriteButton` announces "Remove favourite" or
    // "Add favourite" either way.

    // The card's own options, opened by long press and by nothing else on screen.
    //
    // Deliberately *not* a permanent overflow button beside the star. This row already carries a
    // spine, a thumbnail, a two-line name, a labelled carbohydrate figure and a 48dp favourite
    // button; a second 48dp button takes its width from the one column that needs it most, and it
    // would charge every user on every card, forever, for an action taken rarely. Home's job is to
    // be quiet and fast, and removing a recent product is maintenance, not the fast path.
    //
    // The gesture is reachable without the gesture: `onLongClickLabel` becomes a TalkBack action, so
    // a screen-reader user finds it under "Actions available" rather than needing to press and hold.
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val optionsLabel = stringResource(R.string.recent_options)

    Box(modifier = modifier.fillMaxWidth()) {
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
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = optionsLabel,
                    onLongClick = {
                        // Compose does not haptically acknowledge a long press by itself, and Android
                        // users expect one — without it a press-and-hold that opens a menu feels like a
                        // mis-tap that happened to work. Gated on the app's single existing haptics
                        // preference; no new setting.
                        if (settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        menuOpen = true
                    },
                )
                // A plain 12dp inset now the spine is gone. It used to be 12dp to the spine plus
                // 12dp from the spine to the thumbnail, which with the spine removed would have
                // left the thumbnail floating 24dp in from a card edge nothing else respects.
                .padding(
                    start = Space.s + Space.xs,
                    top = Space.s + 2.dp,
                    bottom = Space.s + 2.dp,
                    end = Space.s + Space.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(product = product)

            // Two rows that line up across the card: identity with its answer, then the remembered
            // portion with the card's actions. Every element keeps a fixed place — the actions sit at
            // the trailing edge of the portion row whatever the name or portion says, so a thumb
            // learns where "+" and the star are once and finds them on every card.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.s + Space.xs),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // The answer, as an answer.
                    //
                    // The SAME red as the calculator, because it is the same fact — not a new hue. A
                    // second red would make the app appear to have two kinds of carbohydrate figure.
                    // It shares the name's row and type size, so the card reads "what — how much" in
                    // one line, and the quiet CARBS caption rides on its baseline.
                    //
                    // Animated out rather than simply absent, for the one case where it disappears
                    // while the card stays: forgetting a *favourite*'s usage keeps the card on screen
                    // and leaves it with nothing to show here. `AnimatedContent`, not
                    // `AnimatedVisibility`, and the difference is load-bearing: the outgoing content
                    // keeps the label it was composed with, so the figure fades out showing the value
                    // that was removed. `AnimatedVisibility` recomposes its child from current state
                    // while the exit runs, which here is null — it would animate out a blank column.
                    AnimatedContent(
                        targetState = carbsLabel,
                        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                        label = "recent_carbs",
                    ) { label ->
                        if (label == null) {
                            Spacer(Modifier)
                        } else {
                            // Inline ("CARBS 28.9 g") at ordinary text sizes, stacked at large ones:
                            // measured at 1.8x, the inline pair took so much of the row that the
                            // product name broke mid-word ("Hagelsl / ag puur"). Stacked, the figure
                            // is only as wide as itself and the name keeps its room.
                            if (LocalDensity.current.fontScale >= LARGE_TEXT_SCALE) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(start = Space.s, end = Space.xs),
                                ) {
                                    RecentCarbFigure(label)
                                    RecentCarbCaption()
                                }
                            } else {
                                Row(modifier = Modifier.padding(start = Space.s, end = Space.xs)) {
                                    RecentCarbCaption(Modifier.alignByBaseline())
                                    Spacer(Modifier.width(Space.xs + 2.dp))
                                    RecentCarbFigure(label, Modifier.alignByBaseline())
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.xs))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = portionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.s))
                    RecentCardActions(
                        favorite = product.favorite,
                        onToggleFavorite = onToggleFavorite,
                        quickAdd = if (quickAddable) {
                            QuickAddAction(
                                status = quickAddStatus,
                                description = stringResource(
                                    R.string.recent_quick_add_description,
                                    portionLabel,
                                    product.name,
                                ),
                                onClick = { onQuickAdd(portionLabel) },
                            )
                        } else {
                            null
                        },
                    )
                }
            }
        }

        RecentOptionsMenu(
            expanded = menuOpen,
            productName = product.name,
            onDismiss = { menuOpen = false },
            onForget = { menuOpen = false; onForget() },
        )
    }
}

/** From here up, the recent card's carb caption stacks under its figure instead of beside it. */
private const val LARGE_TEXT_SCALE = 1.3f

@Composable
private fun RecentCarbFigure(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.extendedColors.result,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun RecentCarbCaption(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.recent_carbs_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** What the card's "+" needs to render and act; null on a card with nothing it could add. */
private class QuickAddAction(
    val status: QuickAddStatus?,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * The card's two actions — *Quick Add* and *Favourite* — as one quiet capsule: `( + │ ☆ )`.
 *
 * **Why one capsule.** They were separate controls in separate places, and the "+" had to ride
 * along after the portion text, so it moved with every portion's length. Paired at the trailing
 * edge of the portion row they have fixed homes: the star is always the outermost segment (where it
 * has always been), the "+" always directly inside it. A card that cannot Quick Add shows the star
 * alone in the same spot, so no star ever moves between cards. Folding the star in also returns the
 * width it used to take from the product name.
 *
 * **Weight.** A hairline capsule on the card's own surface, icons only (owner direction: minimal,
 * no text). The red carb figure above stays the heaviest thing on the card; the "+" is a blue glyph
 * on the capsule's own surface, the star stays neutral until set, and the only fill the capsule ever
 * shows is the confirmation.
 *
 * **Touch.** Each segment is drawn 44x36dp; Compose extends any pointer node smaller than the 48dp
 * minimum to that minimum, verified by an instrumented test that taps outside the drawn segment.
 */
@Composable
private fun RecentCardActions(
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    quickAdd: QuickAddAction?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.chipRadius)
    val added = quickAdd?.status == QuickAddStatus.ADDED

    // Success: the capsule's outline ripples outward once and fades. Drawn behind and outside the
    // capsule's own clip, so it can travel past the edge.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(added) {
        if (!added) return@LaunchedEffect
        ring.snapTo(0f)
        ring.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val ringColor = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .drawBehind {
                val p = ring.value
                if (p > 0f && p < 1f) {
                    val grow = 6.dp.toPx() * p
                    drawRoundRect(
                        color = ringColor.copy(alpha = 0.5f * (1f - p)),
                        topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(size.height / 2f + grow),
                        style = Stroke(width = 2.dp.toPx() * (1f - 0.5f * p)),
                    )
                }
            }
            .clip(shape)
            .border(1.dp, outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (quickAdd != null) {
            QuickAddSegment(action = quickAdd)
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(outline),
            )
        }
        FavoriteSegment(favorite = favorite, onToggle = onToggleFavorite)
    }
}

/**
 * The capsule's "+": adds this card's remembered portion to the current meal.
 *
 * **It always consumes the tap, including while it refuses one.** It sits inside a card whose own tap
 * opens the product; a disabled `clickable` would let the second tap of a double tap fall through to
 * the card and navigate away from Home mid-confirmation. So it stays clickable in every state and
 * does nothing unless idle — the ripple is withheld then, so a refused tap does not look accepted.
 *
 * **Confirmation.** The segment fills with the interaction blue, the plus turns a quarter and becomes
 * a check with a small spring, and the capsule outline ripples once (see [RecentCardActions]); at the
 * same moment the meal bar above the list glows (see `HomeScreen`). The check holds while the
 * ViewModel keeps the card non-repeatable. TalkBack hears the product and the portion, never a bare
 * "Add", and the added state as a state description.
 */
@Composable
private fun QuickAddSegment(action: QuickAddAction) {
    val idle = action.status == null
    val added = action.status == QuickAddStatus.ADDED
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && idle) 0.82f else 1f,
        animationSpec = tween(Motion.QUICK_MS),
        label = "quick_add_press",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(added) {
        if (!added) return@LaunchedEffect
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
    }
    val container by animateColorAsState(
        // Unfilled at rest, so the capsule reads as one quiet surface rather than a two-state
        // toggle; the fill arrives only as the confirmation.
        targetValue = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(Motion.STANDARD_MS),
        label = "quick_add_container",
    )
    val content by animateColorAsState(
        targetValue = if (added) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(Motion.STANDARD_MS),
        label = "quick_add_content",
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (added) 1f else 0f,
        animationSpec = tween(Motion.STANDARD_MS),
        label = "quick_add_icon",
    )
    val addedState = stringResource(R.string.recent_quick_added_state)
    val iconSize = with(LocalDensity.current) { 20.sp.toDp() }

    Box(
        modifier = Modifier
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = if (idle) ripple(color = content) else null,
                role = Role.Button,
                onClick = { if (idle) action.onClick() },
            )
            .semantics {
                contentDescription = action.description
                if (added) stateDescription = addedState
                if (!idle) disabled()
            }
            .testTag(HOME_QUICK_ADD_TAG)
            .size(width = 44.dp, height = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = pressScale * pop.value
                scaleY = pressScale * pop.value
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { rotationZ = 90f * checkProgress }
                    .alpha(1f - checkProgress),
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(iconSize).alpha(checkProgress),
            )
        }
    }
}

/** The capsule's star. Same strings, icons and colours as the app's `FavoriteButton`. */
@Composable
private fun FavoriteSegment(favorite: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    val iconSize = with(LocalDensity.current) { 20.sp.toDp() }
    Box(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = description }
            .size(width = 44.dp, height = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = null,
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The one contextual action a recent card offers.
 *
 * A menu rather than an immediate removal on long press: a press-and-hold is easy to trigger by
 * resting a thumb while scrolling, and this app's own precedent for an instant destructive action
 * (the meal's row removal) is a deliberate *tap* on a visible control. The menu is the "I meant
 * this" step, at a fraction of a dialog's weight.
 *
 * Its label is neutral, not `colorScheme.error`. The action is reversible and keeps the product, so
 * alarm red would overstate it — and this app reserves red for carbohydrate figures, so a second red
 * a few pixels from the card's own carb value would read as a number rather than a warning.
 *
 * The supporting line exists because "Remove from Recent" is genuinely ambiguous next to Settings'
 * "Clear locally saved products". Someone who has verified a value against a package needs to know,
 * before tapping, that it survives.
 */
@Composable
private fun RecentOptionsMenu(
    expanded: Boolean,
    productName: String,
    onDismiss: () -> Unit,
    onForget: () -> Unit,
) {
    val shape = RoundedCornerShape(Space.buttonRadius)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            // Keeps a long product name from stretching the menu to the screen edge.
            .widthIn(max = 288.dp)
            // The same hairline every card on this screen carries, and it is not decoration: in
            // dark mode `surfaceContainerLowest` is within a few percent of the page behind it, so
            // without a border the menu rendered as text floating over the list with no edge at
            // all. Only a dark-mode screenshot showed it — the shadow that carries the surface in
            // light mode does almost nothing against a near-black background.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        // Names what the menu is about. Without it a bare "Remove from Recent" floating over a list
        // leaves the user checking which card they actually pressed.
        Text(
            text = productName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // No leading icon, deliberately. A trash can would claim the opposite of what this does,
        // and the history glyphs that do not (a clock face) read as "timer" rather than "forget".
        // Beyond the ambiguity it also sat wrong: Material centres a leading icon against the whole
        // item, so next to a title with a supporting line beneath it the icon lined up with the
        // supporting text instead of the title, which looked like a mistake. Without it the header,
        // the action and its explanation share one left edge.
        DropdownMenuItem(
            onClick = onForget,
            modifier = Modifier.testTag(HOME_RECENT_FORGET_TAG),
            text = {
                Column(modifier = Modifier.padding(vertical = Space.xs)) {
                    Text(
                        text = stringResource(R.string.recent_forget),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.recent_forget_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
