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
import app.carbscan.ui.home.HomeScreen
import app.carbscan.ui.home.HomeViewModel
import app.carbscan.ui.manual.ManualEntryScreen
import app.carbscan.ui.manual.ManualEntryViewModel
import app.carbscan.ui.product.ProductScreen
import app.carbscan.ui.product.ProductViewModel
import app.carbscan.ui.scan.LabelScannerScreen
import app.carbscan.ui.scan.ScannerScreen
import app.carbscan.ui.settings.SettingsScreen
import app.carbscan.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val PRODUCT = "product/{barcode}"
    const val MANUAL = "manual?barcode={barcode}&carbs={carbs}&basis={basis}"
    const val LABEL_SCAN = "labelscan?barcode={barcode}"
    const val SETTINGS = "settings"

    fun product(barcode: String) = "product/$barcode"

    fun manual(barcode: String? = null, carbs: String = "", basis: String = "") =
        "manual?barcode=${barcode.orEmpty()}&carbs=$carbs&basis=$basis"

    fun labelScan(barcode: String? = null) = "labelscan?barcode=${barcode.orEmpty()}"
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
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(container.productRepository) })
            val recents by viewModel.recents.collectAsStateWithLifecycle()

            HomeScreen(
                recents = recents,
                onScan = { navController.navigate(Routes.SCAN) },
                onManualEntry = { navController.navigate(Routes.manual()) },
                onOpenProduct = { navController.navigate(Routes.product(it)) },
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                onVerify = { viewModel.showVerifyDialog(true) },
                onDismissVerify = { viewModel.showVerifyDialog(false) },
                onConfirmVerification = viewModel::confirmVerification,
                onResetOnline = viewModel::resetToOnlineValue,
                onScanLabel = { navController.navigate(Routes.labelScan(barcode)) },
                onEnterManually = { navController.navigate(Routes.manual(barcode)) },
                onRetry = { viewModel.load(barcode) },
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
            ),
        ) { entry ->
            val barcode = entry.arguments?.getString("barcode").orEmpty()
            LabelScannerScreen(
                onUseValue = { carbs, basis ->
                    navController.navigate(
                        Routes.manual(barcode, carbs.toPlainString(), basis.name),
                    ) { popUpTo(Routes.LABEL_SCAN) { inclusive = true } }
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
