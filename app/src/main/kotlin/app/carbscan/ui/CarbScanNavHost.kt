package app.carbscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.carbscan.AppContainer
import app.carbscan.domain.AppSettings
import app.carbscan.domain.MealTotal
import app.carbscan.domain.NutritionBasis
import java.math.BigDecimal
import app.carbscan.ui.home.HomeScreen
import app.carbscan.ui.home.HomeViewModel
import app.carbscan.ui.manual.ManualEntryScreen
import app.carbscan.ui.manual.ManualEntryViewModel
import app.carbscan.ui.meal.MealScreen
import app.carbscan.ui.meal.MealViewModel
import app.carbscan.ui.onboarding.OnboardingScreen
import app.carbscan.ui.onboarding.OnboardingViewModel
import app.carbscan.ui.search.SearchScreen
import app.carbscan.ui.search.SearchViewModel
import app.carbscan.ui.product.ProductScreen
import app.carbscan.ui.product.ProductViewModel
import app.carbscan.ui.scan.LabelScannerScreen
import app.carbscan.ui.scan.ScannerScreen
import app.carbscan.ui.settings.SettingsScreen
import app.carbscan.ui.settings.SettingsViewModel
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

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCAN = "scan"
    const val PRODUCT = "product/{barcode}"
    const val MANUAL = "manual?barcode={barcode}&carbs={carbs}&basis={basis}"
    const val LABEL_SCAN = "labelscan?barcode={barcode}&compare={compare}"
    const val SETTINGS = "settings"
    const val MEAL = "meal"
    const val SEARCH = "search"

    fun product(barcode: String) = "product/$barcode"

    fun manual(barcode: String? = null, carbs: String = "", basis: String = "") =
        "manual?barcode=${barcode.orEmpty()}&carbs=$carbs&basis=$basis"

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
fun CarbScanNavHost(
    container: AppContainer,
    settings: AppSettings,
    navController: NavHostController = rememberNavController(),
) {
    // Evaluated once, at NavHost's first composition. `settings` starts at AppSettings()'s default
    // (hasSeenOnboarding = false) until the real DataStore value arrives via
    // collectAsStateWithLifecycle in MainActivity, so a returning user can in principle see one
    // frame of Onboarding before the true value loads. Not worth fixing here — DataStore reads are
    // near-instant, and startDestination not re-evaluating on a later `settings` change is fine
    // because completing onboarding navigates explicitly rather than relying on a recomposition.
    val startDestination = if (settings.hasSeenOnboarding) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(
                factory = factory { OnboardingViewModel(container.settingsRepository) },
            )
            val slideIndex by viewModel.slideIndex.collectAsStateWithLifecycle()

            OnboardingScreen(
                slideIndex = slideIndex,
                onNext = viewModel::next,
                onSkip = viewModel::skip,
                onGetStarted = {
                    viewModel.complete()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
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
                factory = factory { SearchViewModel(container.productRepository) },
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
                mealItems = mealItems,
                mealTotal = if (mealItems.isEmpty()) null else MealTotal.asResult(mealItems),
                onOpenMeal = { navController.navigate(Routes.MEAL) },
                searchState = searchState,
                onSearchQueryChanged = searchViewModel::onQueryChanged,
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

            LaunchedEffect(barcode) { viewModel.load(barcode) }

            // A label reading handed back by the scanner (§12). Read once and cleared, so returning
            // to this screen later does not re-open a comparison the user already resolved.
            val savedState = entry.savedStateHandle
            val detectedCarbs by savedState.getStateFlow<String?>(KEY_DETECTED_CARBS, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(detectedCarbs) {
                val carbs = detectedCarbs ?: return@LaunchedEffect
                val basis = savedState.get<String>(KEY_DETECTED_BASIS)
                    ?.let(NutritionBasis::valueOf)
                    ?: NutritionBasis.PER_100_G
                savedState.remove<String>(KEY_DETECTED_CARBS)
                savedState.remove<String>(KEY_DETECTED_BASIS)
                viewModel.onLabelDetected(BigDecimal(carbs), basis)
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
                    viewModel.rememberUsage()
                    navController.popBackStack()
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
                onAddToMeal = viewModel::addCurrentToMeal,
                onAddToMealAndScanNext = { description ->
                    viewModel.addCurrentToMeal(description)
                    // Straight back to the camera, with this product popped off the stack: after
                    // adding a fourth item the user wants the scanner, not a four-deep back stack
                    // of products they have already finished with (§11).
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.HOME)
                    }
                },
                onOpenMeal = { navController.navigate(Routes.MEAL) },
                onConfirmLabelMatch = viewModel::confirmLabelMatch,
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

        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(
                factory = factory { SearchViewModel(container.productRepository) },
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            SearchScreen(
                state = state,
                onQueryChanged = viewModel::onQueryChanged,
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
            )
        }

        composable(
            route = Routes.MANUAL,
            arguments = listOf(
                navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                navArgument("carbs") { type = NavType.StringType; defaultValue = "" },
                navArgument("basis") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()
            val carbs = entry.arguments?.getString("carbs").orEmpty()
            val basis = entry.arguments?.getString("basis").orEmpty()
            val viewModel: ManualEntryViewModel =
                viewModel(factory = factory { ManualEntryViewModel(container.productRepository) })
            val state by viewModel.state.collectAsStateWithLifecycle()

            // A confirmed OCR reading arrives pre-filled; the user still supplies the name (§29).
            LaunchedEffect(barcode, carbs, basis) { viewModel.start(barcode, carbs, basis) }

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
                        navController.navigate(
                            Routes.manual(barcode, carbs.toPlainString(), basis.name),
                        ) { popUpTo(Routes.LABEL_SCAN) { inclusive = true } }
                    }
                },
                onEditManually = {
                    navController.navigate(Routes.manual(barcode)) {
                        popUpTo(Routes.LABEL_SCAN) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() },
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
