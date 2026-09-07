package app.justthecarbs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.justthecarbs.AppContainer
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionUnitKind
import java.math.BigDecimal
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.home.HomeViewModel
import app.justthecarbs.ui.manual.ManualEntryScreen
import app.justthecarbs.ui.manual.ManualEntryViewModel
import app.justthecarbs.ui.manual.PendingPortionUnit
import app.justthecarbs.ui.meal.MealScreen
import app.justthecarbs.ui.meal.MealViewModel
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.OnboardingViewModel
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchViewModel
import app.justthecarbs.ui.product.ProductNavigationEvent
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductViewModel
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.ui.scan.LabelScannerScreen
import app.justthecarbs.ui.scan.ScannerScreen
import app.justthecarbs.ui.settings.SettingsScreen
import app.justthecarbs.ui.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Keys for handing a label reading back from the scanner to the calculator (§12).
 *
 * Passed through the previous entry's `SavedStateHandle` rather than as route arguments: the
 * calculator is being *returned to*, not navigated to afresh, and re-navigating would rebuild it
 * and lose the portion the user had already typed.
 */
private const val KEY_DETECTED_CARBS = "detected_carbs"
private const val KEY_DETECTED_BASIS = "detected_basis"

/**
 * Parses a label reading carried through the saved-state handoff (§12), or null on any malformed
 * input.
 *
 * Both halves must parse. The basis in particular is never allowed to default to grams (§5,
 * startup-hardening pass): `onUseValue` on the writing side always supplies a real [NutritionBasis],
 * so a missing, blank or unrecognised [basisText] here means the round trip corrupted it — a
 * malformed restore, not a normal path — and this is the same "never guess the denominator" rule the
 * OCR basis-unknown card enforces on the reading side. `entries.firstOrNull` rather than `.valueOf`,
 * which would crash the whole screen on exactly the corrupted input this guards against.
 *
 * Extracted as a pure top-level function (rather than left inline in the `LaunchedEffect`) so the
 * parsing rule is JVM-testable without a Compose harness.
 */
internal fun parseDetectedLabelReading(carbsText: String?, basisText: String?): Pair<BigDecimal, NutritionBasis>? {
    val carbs = carbsText?.toBigDecimalOrNull() ?: return null
    val basis = basisText?.let { text -> NutritionBasis.entries.firstOrNull { it.name == text } } ?: return null
    return carbs to basis
}

/**
 * Rebuilds a pending portion from its navigation arguments (correction pass §2).
 *
 * Returns null unless the arguments describe one complete, valid conversion. A half-parsed portion
 * is discarded rather than repaired: the two shapes are kept apart on the wire precisely so a weight
 * can never be read back as a carbohydrate figure, and guessing here would undo that.
 */
private fun pendingPortionUnitFrom(
    kindName: String,
    carbsPerUnit: String,
    weightPerUnit: String,
    weightBasis: String,
): PendingPortionUnit? {
    val kind = PortionUnitKind.entries.firstOrNull { it.name == kindName } ?: return null

    val weight = PortionParser.parse(weightPerUnit)
    val basis = NutritionBasis.entries.firstOrNull { it.name == weightBasis }
    if (weight != null && basis != null && weight.signum() > 0) {
        return PendingPortionUnit(kind, PortionConversion.WeightBased(weight, basis))
    }

    val carbs = PortionParser.parse(carbsPerUnit) ?: return null
    if (carbs.signum() < 0) return null
    return PendingPortionUnit(kind, PortionConversion.DirectCarbs(carbs))
}

/**
 * The manual-entry route for the scanner's *Edit* action.
 *
 * Extracted from the navigation lambda so it can be asserted without an emulator, because the
 * defect it fixes is invisible to every screen test: *Edit* used to navigate to
 * `Routes.manual(barcode)` and drop the basis entirely, so a label whose `per 100 ml` column had
 * been read correctly opened manual entry with **`100 g`** selected — `ManualEntryUiState`'s
 * default, with nothing having overridden it. A user typing the correct figure there stores it
 * against the wrong denominator, and no later stage can detect that.
 *
 * [basis] is null only when nothing established one, in which case the manual screen keeps its own
 * default — the pre-existing behaviour for an entry reached from Home.
 *
 * The amount is deliberately absent. *Edit* is reached when the app's number was wrong or was
 * withheld, so pre-filling it would re-propose the very figure the user has just rejected; carrying
 * a value is *Correct*'s job, which is a different question and keeps its own route.
 */
internal fun editManuallyRoute(barcode: String, basis: NutritionBasis?): String =
    Routes.manual(barcode, "", basis?.name.orEmpty())

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCAN = "scan"
    const val PRODUCT = "product/{barcode}"
    const val MANUAL =
        "manual?barcode={barcode}&carbs={carbs}&basis={basis}" +
            "&unitKind={unitKind}&unitCarbs={unitCarbs}&unitWeight={unitWeight}&unitBasis={unitBasis}"
    const val LABEL_SCAN = "labelscan?barcode={barcode}&compare={compare}"
    const val SETTINGS = "settings"
    const val MEAL = "meal"
    const val SEARCH = "search"

    /**
     * A calculation with no product behind it (1.0.3 P1).
     *
     * Separate from [PRODUCT] rather than a special barcode value, because the two differ in what
     * they *do* on arrival: `product/{barcode}` begins with a database and possibly a network
     * lookup, and this begins with nothing at all. Folding them together would mean teaching the
     * lookup path to recognise a sentinel barcode and skip itself, which is how a sentinel ends up
     * being written to disk.
     *
     * The figures travel as route arguments so the calculation survives process death — the same
     * reason a detected value going to manual entry does. There is nowhere else to recover them
     * from: by design, nothing about this calculation is stored.
     */
    const val QUICK = "quick?carbs={carbs}&basis={basis}"

    /**
     * Defense in depth for a dynamic path segment (P1 §10), on top of — not instead of — validating
     * a barcode at the boundary it enters this app (the scanner, manual entry, and now
     * [app.justthecarbs.domain.BarcodeValidator]-checked search hits). Every barcode reaching this
     * function today is already a validated, digits-only GTIN and needs no encoding at all — but a
     * route builder should not depend on every future caller remembering that. URL-encoding here
     * means a malformed value (one containing `/`, `?`, `#`, `%` or whitespace, however it got past
     * an upstream check) becomes a single opaque path segment rather than corrupting the route —
     * extra segments, a broken match, or a navigation Compose otherwise cannot recover from —
     * instead of crashing navigation or, worse, silently landing on an unintended destination.
     */
    fun product(barcode: String) = "product/${java.net.URLEncoder.encode(barcode, "UTF-8")}"

    fun quick(carbs: String, basis: String) = "quick?carbs=$carbs&basis=$basis"

    fun manual(barcode: String? = null, carbs: String = "", basis: String = "") =
        "manual?barcode=${barcode.orEmpty()}&carbs=$carbs&basis=$basis" +
            "&unitKind=&unitCarbs=&unitWeight=&unitBasis="

    /**
     * Manual entry carrying a portion accepted from OCR, for a product that does not exist yet
     * (correction pass §2).
     *
     * The portion travels as arguments rather than in a `savedStateHandle`, for the same reason the
     * detected carbohydrate figure does: it must survive process death, and the route the user is
     * being sent to is precisely where the product it depends on gets created.
     *
     * The two conversion shapes are kept distinct in the arguments — carbs-per-unit and
     * weight-per-unit are never collapsed into one field, so a weight can never be read back as a
     * carbohydrate figure.
     */
    fun manualWithPendingPortion(
        barcode: String?,
        carbs: String,
        basis: String,
        unitKind: String,
        unitCarbs: String = "",
        unitWeight: String = "",
        unitBasis: String = "",
    ) = "manual?barcode=${barcode.orEmpty()}&carbs=$carbs&basis=$basis" +
        "&unitKind=$unitKind&unitCarbs=$unitCarbs&unitWeight=$unitWeight&unitBasis=$unitBasis"

    /**
     * [compare]: whether a product is already loaded on the screen underneath, so a reading comes
     * back as a comparison rather than a new product. Decided by the caller, not derived from
     * whether [barcode] is blank — a not-found product screen has a real barcode but no loaded
     * product, and must still take the "create" path (§12).
     */
    fun labelScan(barcode: String? = null, compare: Boolean = false) =
        "labelscan?barcode=${barcode.orEmpty()}&compare=$compare"
}

/**
 * Navigation (§6): one primary surface with a small number of destinations reached from it.
 *
 * The scanner is popped off the back stack as soon as a barcode resolves, so pressing back from a
 * product returns to Home rather than re-opening the camera on a code that has already been read.
 */
@Composable
fun JustTheCarbsNavHost(
    container: AppContainer,
    settings: AppSettings,
    navController: NavHostController = rememberNavController(),
) {
    // Evaluated once, at NavHost's first composition. By the time this composable exists at all,
    // `settings` is guaranteed to be the real first DataStore value — MainActivity holds the splash
    // screen and renders nothing but a neutral background (StartupState.Loading) until then, so a
    // returning user's start destination is never decided from AppSettings()'s synthetic default.
    // startDestination not re-evaluating on a later `settings` change is fine because completing
    // onboarding navigates explicitly rather than relying on a recomposition.
    val startDestination = if (settings.hasSeenOnboarding) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(
                factory = factory { OnboardingViewModel(container.settingsRepository) },
            )
            val slideIndex by viewModel.slideIndex.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            val completionState by viewModel.completionState.collectAsStateWithLifecycle()

            LaunchedEffect(completionState) {
                if (completionState is OnboardingViewModel.CompletionState.Saved) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            }

            OnboardingScreen(
                slideIndex = slideIndex,
                onNext = viewModel::next,
                onSkip = viewModel::skip,
                onSlideChanged = viewModel::showSlide,
                // Disabled only while a write is genuinely in flight -- Idle and Failed both allow a
                // tap (Failed is a retry, not a re-disable), so a DataStore failure can no longer
                // leave this button permanently unusable.
                onGetStarted = {
                    if (completionState !is OnboardingViewModel.CompletionState.Saving) {
                        coroutineScope.launch { viewModel.complete() }
                    }
                },
                completionError = (completionState as? OnboardingViewModel.CompletionState.Failed)?.message,
            )
        }

        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(container.productRepository) })
            val recents by viewModel.recents.collectAsStateWithLifecycle()
            val mealItems by viewModel.mealItems.collectAsStateWithLifecycle()

            // Home's own search instance (§9, owner request 2026-08-14): a deliberate, always-on
            // entry point, not the SearchScreen fallback reached only from a failure. Same
            // ViewModel class, same behaviour — a separate instance because it lives and dies with
            // Home rather than with a route someone navigated to.
            val searchViewModel: SearchViewModel = viewModel(
                // Both the provider chain and the pacing come from the container, not from the
                // ViewModel: Home's inline search and this screen are separate instances, and a
                // per-instance budget would let them spend the same shared budget twice over.
                //
                // `container.searchSource` is the primary/fallback chain, exposed as a plain
                // ProductSearchSource — this screen does not know there is more than one provider.
                // The governor here paces the PRIMARY; the legacy fallback carries its own stricter
                // budget inside GovernedProductSearch.
                factory = factory {
                    SearchViewModel(container.searchSource, container.primarySearchGovernor)
                },
            )
            val searchState by searchViewModel.state.collectAsStateWithLifecycle()

            HomeScreen(
                recents = recents,
                settings = settings,
                onScan = { navController.navigate(Routes.SCAN) },
                onManualEntry = { navController.navigate(Routes.manual()) },
                onOpenProduct = { navController.navigate(Routes.product(it)) },
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onScanLabel = { navController.navigate(Routes.labelScan()) },
                mealItems = mealItems,
                mealTotal = if (mealItems.isEmpty()) null else MealTotal.asResult(mealItems),
                onOpenMeal = { navController.navigate(Routes.MEAL) },
                searchState = searchState,
                onSearchQueryChanged = searchViewModel::onQueryChanged,
                onSearchSubmit = searchViewModel::search,
                onSearchSelect = { hit -> navController.navigate(Routes.product(hit.barcode)) },
                onSearchScanLabel = { navController.navigate(Routes.labelScan()) },
                onSearchEnterManually = { navController.navigate(Routes.manual()) },
                onSearchRetry = searchViewModel::retry,
            )
        }

        composable(Routes.SCAN) {
            ScannerScreen(
                hapticsEnabled = settings.hapticsEnabled,
                onBarcode = { barcode ->
                    navController.navigate(Routes.product(barcode)) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
                onManualBarcode = { barcode ->
                    navController.navigate(Routes.product(barcode)) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() },
                onEnterManually = {
                    navController.navigate(Routes.manual()) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.PRODUCT,
            arguments = listOf(navArgument("barcode") { type = NavType.StringType }),
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()
            val viewModel: ProductViewModel = viewModel(
                factory = factory { ProductViewModel(container.productRepository, createSavedStateHandle()) },
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(barcode) { viewModel.load(barcode) }

            // Navigate to the scanner only once *Add & scan next*'s write has actually landed
            // (P0 §1). `addCurrentToMeal` sends this event after persistence succeeds, never before
            // — so collecting it and navigating here cannot pop this route (destroying `viewModel`
            // and cancelling its coroutine) while the insert is still in flight. `LaunchedEffect(Unit)`
            // rather than keying on `state`: the event is one-shot by construction (a `Channel`, not
            // a replaying flow), so there is nothing to re-key the collector on.
            LaunchedEffect(Unit) {
                viewModel.navigationEvents.collect { event ->
                    when (event) {
                        ProductNavigationEvent.ScanNext -> navController.navigate(Routes.SCAN) {
                            popUpTo(Routes.HOME)
                        }
                    }
                }
            }

            // A label reading handed back by the scanner (§12). Read once and cleared, so returning
            // to this screen later does not re-open a comparison the user already resolved.
            val savedState = entry.savedStateHandle
            val detectedCarbs by savedState.getStateFlow<String?>(KEY_DETECTED_CARBS, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(detectedCarbs) {
                if (detectedCarbs == null) return@LaunchedEffect
                val basisText = savedState.get<String>(KEY_DETECTED_BASIS)
                savedState.remove<String>(KEY_DETECTED_CARBS)
                savedState.remove<String>(KEY_DETECTED_BASIS)

                val reading = parseDetectedLabelReading(detectedCarbs, basisText)
                if (reading != null) {
                    viewModel.onLabelDetected(reading.first, reading.second)
                } else {
                    viewModel.reportLabelHandoffFailure()
                }
            }

            ProductScreen(
                state = state,
                settings = settings,
                onPortionChanged = viewModel::onPortionChanged,
                onAdjust = viewModel::adjustPortion,
                onSetPortion = viewModel::setPortion,
                onToggleFavorite = viewModel::toggleFavorite,
                onBack = {
                    // The portion is remembered on the way out, not on every keystroke, so a
                    // half-typed number never becomes the pre-fill for next time (§20).
                    //
                    // Awaited before popping (P0 §3): `rememberUsage()` alone launches into
                    // `viewModelScope` and returns immediately, so popping right after it could
                    // destroy `viewModel` and cancel that write before Room ever runs. This
                    // composable's own `coroutineScope` — not the ViewModel's — outlives the pop,
                    // so the write finishes before `popBackStack()` runs.
                    coroutineScope.launch {
                        viewModel.rememberUsageAndAwait()
                        navController.popBackStack()
                    }
                },
                // *Verify label* opens the camera straight into nutrition-label OCR (spec §7).
                // It previously opened a dialog asking the user to retype the figure — which is
                // verification only in the sense that they had to read the package to do it, and
                // is precisely the transcription step the app exists to remove. The typed path is
                // still reachable from the comparison's *Edit detected value*.
                onVerify = { navController.navigate(Routes.labelScan(barcode, compare = true)) },
                onVerifyByTyping = { viewModel.showVerifyDialog(true) },
                onDismissVerify = { viewModel.showVerifyDialog(false) },
                onConfirmVerification = viewModel::confirmVerification,
                onResetOnline = viewModel::resetToOnlineValue,
                // Shared by two very different situations: the calculator's "rescan" (a product is
                // loaded, so the reading is a comparison) and the not-found screen's recovery action
                // (no product loaded yet, so the reading should create one). Only `state.product`
                // tells them apart — the barcode is non-empty in both cases (§12).
                onScanLabel = {
                    navController.navigate(Routes.labelScan(barcode, compare = state.product != null))
                },
                onEnterManually = { navController.navigate(Routes.manual(barcode)) },
                onRetry = { viewModel.load(barcode) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                // One tap from *Product not found* back to the camera (§5). The not-found product is
                // popped rather than left underneath: it is a dead end the user is leaving, and
                // keeping it would put a stale failure between the next result and Home. `SCAN` is a
                // fresh entry, so its analyzer — and with it BarcodeStabilityTracker's held-barcode
                // count and one-shot latch — is rebuilt from scratch.
                onScanAgain = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.PRODUCT) { inclusive = true }
                    }
                },
                onApplyNewerRemote = viewModel::applyNewerRemoteValue,
                onDismissNewerRemote = viewModel::dismissNewerRemoteValue,
                onSwitchToGrams = viewModel::switchToGrams,
                onSwitchToPortionUnit = viewModel::switchToPortionUnit,
                onCountChanged = viewModel::onCountChanged,
                onShowAddPortionUnitForm = viewModel::showAddPortionUnitForm,
                onAddPortionUnit = viewModel::addPortionUnit,
                onVerifyPortionUnit = viewModel::verifySelectedPortionUnit,
                onApplyNewerRemotePortionUnit = viewModel::applyNewerRemotePortionUnit,
                onDismissNewerRemotePortionUnit = viewModel::dismissNewerRemotePortionUnit,
                onCorrectPortionUnit = viewModel::correctSelectedPortionUnit,
                onCancelPortionUnitCorrection = viewModel::cancelPortionUnitCorrection,
                onAddToMeal = { description, fallbackName ->
                    viewModel.addCurrentToMeal(description, fallbackName, scanNext = false)
                },
                // Straight back to the camera, with this product popped off the stack, once the
                // write has landed: after adding a fourth item the user wants the scanner, not a
                // four-deep back stack of products they have already finished with (§11). The
                // navigation itself happens in the `navigationEvents` collector above, only after
                // `addCurrentToMeal` confirms persistence succeeded (P0 §1) — this call only starts
                // the write and cannot itself trigger navigation.
                onAddToMealAndScanNext = { description, fallbackName ->
                    viewModel.addCurrentToMeal(description, fallbackName, scanNext = true)
                },
                onOpenMeal = { navController.navigate(Routes.MEAL) },
                onConfirmLabelMatch = viewModel::confirmLabelMatch,
                onDismissLabelHandoffFailure = viewModel::dismissLabelHandoffFailure,
                onUseDetectedLabelValue = viewModel::useDetectedLabelValue,
                onEditDetectedLabelValue = { detected ->
                    // "Edit detected value" hands the reading to manual entry pre-filled, so the
                    // user corrects the OCR rather than retyping the whole label from scratch.
                    viewModel.dismissLabelVerdict()
                    navController.navigate(
                        Routes.manual(
                            barcode,
                            detected.toPlainString(),
                            state.product?.basis?.name.orEmpty(),
                        ),
                    )
                },
                onDismissLabelVerdict = viewModel::dismissLabelVerdict,
                onSelectUsualPortion = viewModel::applyUsualPortion,
            )
        }

        /**
         * A calculation with no product behind it (1.0.3 P1).
         *
         * The same [ProductScreen] and the same [ProductViewModel] as a barcode product — the whole
         * point is that once a carbohydrate figure and its basis are known, how they were obtained
         * stops mattering to the calculation. Only the *entry* differs:
         * [ProductViewModel.startQuickCalculation] puts the figures straight into state instead of
         * `load` fetching them, so no lookup happens and no row is read or written.
         *
         * Actions that need a product row are deliberately not wired: there is nothing to favourite,
         * verify against, refresh from or attach a portion unit to. `ProductScreen` already hides
         * each of those on an empty barcode, so they are absent from the screen rather than present
         * and inert.
         */
        composable(
            route = Routes.QUICK,
            arguments = listOf(
                navArgument("carbs") { type = NavType.StringType; defaultValue = "" },
                navArgument("basis") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val carbsArg = entry.arguments?.getString("carbs").orEmpty()
            val basisArg = entry.arguments?.getString("basis").orEmpty()
            val viewModel: ProductViewModel = viewModel(
                factory = factory { ProductViewModel(container.productRepository, createSavedStateHandle()) },
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Keyed on the arguments, so a corrected value arriving as a new destination starts a new
            // calculation while a recomposition does not restart the one in progress.
            LaunchedEffect(carbsArg, basisArg) {
                val carbs = PortionParser.parse(carbsArg)
                val basis = NutritionBasis.entries.firstOrNull { it.name == basisArg }
                // Both halves are required. A figure whose basis was lost in transit is exactly the
                // "grams of what?" question this app must never answer on the user's behalf, so the
                // route refuses rather than defaulting — manual entry is where an open basis belongs.
                if (carbs != null && basis != null) {
                    viewModel.startQuickCalculation(carbs, basis)
                } else {
                    navController.navigate(Routes.manual(null, carbsArg, basisArg)) {
                        popUpTo(Routes.QUICK) { inclusive = true }
                    }
                }
            }

            // Saving lands on the ordinary product calculator for the row that now exists, so the
            // saved product behaves exactly like any other from that point on. The quick screen is
            // popped: it was a way through, not somewhere to return to with a duplicate of a product
            // that is now real.
            LaunchedEffect(state.unsaved, state.barcode) {
                if (!state.unsaved && state.barcode.isNotEmpty()) {
                    navController.navigate(Routes.product(state.barcode)) {
                        popUpTo(Routes.QUICK) { inclusive = true }
                    }
                }
            }

            // Same P0 fix as the barcode-product route above: navigate to the scanner only once
            // *Add & scan next*'s write has actually landed, never before.
            LaunchedEffect(Unit) {
                viewModel.navigationEvents.collect { event ->
                    when (event) {
                        ProductNavigationEvent.ScanNext -> navController.navigate(Routes.SCAN) {
                            popUpTo(Routes.HOME)
                        }
                    }
                }
            }

            ProductScreen(
                state = state,
                settings = settings,
                onPortionChanged = viewModel::onPortionChanged,
                onAdjust = viewModel::adjustPortion,
                onSetPortion = viewModel::setPortion,
                onToggleFavorite = {},
                onBack = { navController.popBackStack() },
                onVerify = {},
                onDismissVerify = {},
                onConfirmVerification = { _, _, _ -> },
                onResetOnline = {},
                // Re-scanning replaces this calculation with the next reading, rather than layering a
                // second quick screen on the stack.
                onScanLabel = {
                    navController.navigate(Routes.labelScan()) {
                        popUpTo(Routes.QUICK) { inclusive = true }
                    }
                },
                onEnterManually = { navController.navigate(Routes.manual()) },
                onRetry = {},
                onAddToMeal = { description, fallbackName ->
                    viewModel.addCurrentToMeal(description, fallbackName, scanNext = false)
                },
                // Navigation happens in the `navigationEvents` collector above, only after the write
                // succeeds (P0 §1) — same as the barcode-product route.
                onAddToMealAndScanNext = { description, fallbackName ->
                    viewModel.addCurrentToMeal(description, fallbackName, scanNext = true)
                },
                onOpenMeal = { navController.navigate(Routes.MEAL) },
                onShowSaveQuickCalculation = viewModel::showSaveQuickCalculation,
                onSaveQuickCalculation = viewModel::saveQuickCalculation,
            )
        }

        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(
                // Both the provider chain and the pacing come from the container, not from the
                // ViewModel: Home's inline search and this screen are separate instances, and a
                // per-instance budget would let them spend the same shared budget twice over.
                //
                // `container.searchSource` is the primary/fallback chain, exposed as a plain
                // ProductSearchSource — this screen does not know there is more than one provider.
                // The governor here paces the PRIMARY; the legacy fallback carries its own stricter
                // budget inside GovernedProductSearch.
                factory = factory {
                    SearchViewModel(container.searchSource, container.primarySearchGovernor)
                },
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            SearchScreen(
                state = state,
                onQueryChanged = viewModel::onQueryChanged,
                onSearchSubmit = viewModel::search,
                onSelect = { hit ->
                    // Selecting a result runs an ordinary barcode lookup, so a searched product is
                    // cached, validated and given provenance by exactly the same path as a scanned
                    // one. The search screen is popped: it was a way through, not a place to return
                    // to with a stale query.
                    navController.navigate(Routes.product(hit.barcode)) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                },
                onScanLabel = {
                    navController.navigate(Routes.labelScan()) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                },
                onEnterManually = {
                    navController.navigate(Routes.manual()) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                },
                onRetry = viewModel::retry,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MEAL) {
            val viewModel: MealViewModel = viewModel(
                factory = factory { MealViewModel(container.productRepository) },
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            MealScreen(
                state = state,
                settings = settings,
                onBack = { navController.popBackStack() },
                onRemoveItem = viewModel::removeItem,
                onClear = viewModel::clear,
                onShowClearConfirmation = viewModel::showClearConfirmation,
                // An ordinary forward navigation, so the scanner's own "back" returns to the meal
                // the user is still building rather than skipping past it to Home.
                onScanNext = { navController.navigate(Routes.SCAN) },
                onUndoRemove = viewModel::undoRemove,
                onUndoExpired = viewModel::clearUndo,
            )
        }

        composable(
            route = Routes.MANUAL,
            arguments = listOf(
                navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                navArgument("carbs") { type = NavType.StringType; defaultValue = "" },
                navArgument("basis") { type = NavType.StringType; defaultValue = "" },
                navArgument("unitKind") { type = NavType.StringType; defaultValue = "" },
                navArgument("unitCarbs") { type = NavType.StringType; defaultValue = "" },
                navArgument("unitWeight") { type = NavType.StringType; defaultValue = "" },
                navArgument("unitBasis") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()
            val carbs = entry.arguments?.getString("carbs").orEmpty()
            val basis = entry.arguments?.getString("basis").orEmpty()
            val unitKind = entry.arguments?.getString("unitKind").orEmpty()
            val unitCarbs = entry.arguments?.getString("unitCarbs").orEmpty()
            val unitWeight = entry.arguments?.getString("unitWeight").orEmpty()
            val unitBasis = entry.arguments?.getString("unitBasis").orEmpty()
            val viewModel: ManualEntryViewModel =
                viewModel(factory = factory { ManualEntryViewModel(container.productRepository) })
            val state by viewModel.state.collectAsStateWithLifecycle()

            // A portion accepted from OCR for a product that does not exist yet (correction §2).
            // Rebuilt here rather than being persisted at the scanner, because the row it depends
            // on is created by this very screen.
            val pendingPortion = remember(unitKind, unitCarbs, unitWeight, unitBasis) {
                pendingPortionUnitFrom(unitKind, unitCarbs, unitWeight, unitBasis)
            }

            // A confirmed OCR reading arrives pre-filled; the user still supplies the name (§29).
            LaunchedEffect(barcode, carbs, basis, pendingPortion) {
                viewModel.start(barcode, carbs, basis, pendingPortion)
            }

            // Saving lands the user straight on the calculator: the point of entering a product is
            // to get a number, not to admire a saved record (§70).
            LaunchedEffect(state.savedBarcode) {
                state.savedBarcode?.let { saved ->
                    viewModel.onNavigated()
                    navController.navigate(Routes.product(saved)) {
                        popUpTo(Routes.MANUAL) { inclusive = true }
                    }
                }
            }

            ManualEntryScreen(
                state = state,
                onNameChanged = viewModel::onNameChanged,
                onCarbsChanged = viewModel::onCarbsChanged,
                onBasisChanged = viewModel::onBasisChanged,
                onPackageChanged = viewModel::onPackageChanged,
                onSave = viewModel::save,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.LABEL_SCAN,
            arguments = listOf(
                navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                navArgument("compare") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()
            val compare = entry.arguments?.getBoolean("compare") ?: false

            // Whether a `products` row actually exists for this barcode — the fact the portion's
            // foreign key depends on (correction pass §2). A non-empty barcode is NOT the same
            // question: the not-found screen has a real barcode and no row, and saving a portion
            // there failed while the UI reported success. Null while the check is in flight, which
            // keeps both save paths closed rather than guessing one.
            var productExistsCheck by remember(barcode) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(barcode) {
                productExistsCheck = barcode.isNotEmpty() &&
                    container.productRepository.findLocal(barcode) != null
            }
            val productExists = productExistsCheck == true

            /**
             * Open manual entry pre-filled with a detected figure, replacing the scanner.
             *
             * Shared by *Use* (when there is no product to compare against) and *Correct*, which
             * navigate identically. Written once rather than twice partly for the obvious reason and
             * partly for a mundane one: two copies of this expression nested inside sibling lambdas
             * reproducibly crashed `lintAnalyzeDebug` with an internal UAST resolver failure
             * (`resolveSyntheticJavaPropertyAccessorCall`, on the enum's synthetic `name` accessor).
             * The behaviour is identical either way; only lint could tell the two apart.
             */
            fun openManualEntryWith(carbs: BigDecimal, basis: NutritionBasis?) {
                val route = Routes.manual(barcode, carbs.toPlainString(), basis?.name.orEmpty())
                navController.navigate(route) {
                    popUpTo(Routes.LABEL_SCAN) { inclusive = true }
                }
            }

            LabelScannerScreen(
                onUseValue = { carbs, basis ->
                    // For a product already on the calculator, a label reading comes back as a
                    // *comparison* rather than as a new product (§12): the user scanned to check
                    // the value they were looking at, so send them back to it with both figures.
                    // Routing to manual entry instead — as this did — quietly reframed "check this"
                    // as "create this", and lost the value being checked against.
                    //
                    // `compare` is decided by the caller from whether a product was actually loaded,
                    // not from whether `barcode` is blank: a not-found product screen has a real
                    // barcode but no loaded product, and must still take the "create" path below —
                    // otherwise the reading is silently dropped (ProductViewModel.onLabelDetected
                    // no-ops with no product to compare against) and the screen just bounces back.
                    if (compare) {
                        navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                            handle[KEY_DETECTED_CARBS] = carbs.toPlainString()
                            handle[KEY_DETECTED_BASIS] = basis.name
                        }
                        navController.popBackStack()
                    } else {
                        // Straight to the calculator (1.0.3 P1). This used to open manual entry,
                        // which would not proceed without a product name and wrote a Room row before
                        // it would navigate — so reading one number off one photograph cost a named,
                        // saved record the user never asked for. A label states a carbohydrate figure
                        // and its basis, which is everything the calculation needs; what the product
                        // is called is a question only *saving* has to ask, and saving is now
                        // optional and offered from the result.
                        navController.navigate(Routes.quick(carbs.toPlainString(), basis.name)) {
                            popUpTo(Routes.LABEL_SCAN) { inclusive = true }
                        }
                    }
                },
                // *Edit* keeps the basis the label stated and leaves the amount blank.
                //
                // The basis used to be dropped here, and a device recording showed what that costs:
                // a coconut-milk label whose `per 100 ml` column the classifier had read correctly
                // opened manual entry with **`100 g`** selected, because that is
                // `ManualEntryUiState`'s default and nothing had overridden it. A user typing the
                // right figure there stores it against the wrong denominator, and no later stage can
                // detect that. The amount stays empty deliberately — this action is reached when the
                // app's number was wrong or withheld, so re-proposing it would undo the rejection.
                onEditManually = { basis ->
                    navController.navigate(editManuallyRoute(barcode, basis)) {
                        popUpTo(Routes.LABEL_SCAN) { inclusive = true }
                    }
                },
                // *Correct* carries the detected figure into the same field the user would
                // otherwise have to fill from scratch. It uses the create route even when
                // `compare` is true: correcting a reading means the user has decided the OCR value
                // is wrong, and a comparison of a value they have already rejected is not what they
                // asked for — they asked to type the right one.
                onCorrectValue = ::openManualEntryWith,
                onClose = { navController.popBackStack() },
                // Two genuinely different situations, decided by whether the product row actually
                // exists rather than by whether the barcode string is non-empty (correction §2).
                // A not-found product screen has a real barcode and no product row, and that is
                // exactly the case where capturing a countable portion matters most.
                //
                // The scan is the user reading their own package, so the unit is stored as verified
                // with OCR provenance — the same provenance/verification split products already use.
                onSavePortionUnit = if (!productExists) {
                    null
                } else {
                    { kind, conversion ->
                        // Suspends until the write completes and reports what happened, so the
                        // scanner can only show "saved" once the row is genuinely on disk.
                        //
                        // A plain runCatching would also catch CancellationException — if this
                        // screen is torn down mid-write, that would report as an ordinary failed
                        // save rather than letting the cancellation propagate (P1 §14).
                        try {
                            container.productRepository.saveUserPortionUnit(
                                barcode = barcode,
                                kind = kind,
                                conversion = conversion,
                                origin = ProductDataOrigin.OCR,
                            )
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                    }
                },
                // No product row yet: carry the accepted portion into creation instead of writing
                // it against a foreign key with nothing to point at.
                onCarryPendingPortionUnit = if (productExists) {
                    null
                } else {
                    { kind, conversion ->
                        val weight = conversion as? PortionConversion.WeightBased
                        val direct = conversion as? PortionConversion.DirectCarbs
                        navController.navigate(
                            Routes.manualWithPendingPortion(
                                barcode = barcode,
                                carbs = "",
                                basis = "",
                                unitKind = kind.name,
                                unitCarbs = direct?.carbsPerUnit?.toPlainString().orEmpty(),
                                unitWeight = weight?.amountPerUnit?.toPlainString().orEmpty(),
                                unitBasis = weight?.basis?.name.orEmpty(),
                            ),
                        ) { popUpTo(Routes.LABEL_SCAN) { inclusive = true } }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = factory {
                    SettingsViewModel(container.settingsRepository, container.localProducts)
                },
            )
            SettingsScreen(
                settings = settings,
                onThemeChanged = viewModel::setTheme,
                onResultStyleChanged = viewModel::setResultStyle,
                onHapticsChanged = viewModel::setHaptics,
                onClearRecents = viewModel::clearRecents,
                onClearProducts = viewModel::clearProducts,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Small helper so each destination can build its ViewModel from the container. */
private inline fun <reified VM : ViewModel> factory(
    crossinline create: CreationExtras.() -> VM,
) = viewModelFactory { initializer { create() } }
