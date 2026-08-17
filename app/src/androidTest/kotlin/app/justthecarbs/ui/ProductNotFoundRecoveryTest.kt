package app.justthecarbs.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.product.Failure
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Recovery from *Product not found* (§5, §20).
 *
 * This screen is where a mis-scan lands, and it offered no way back to the camera at all: the user
 * had to press back to Home and start the scan again. That was a real defect reported from a
 * physical device, and it was invisible to the suite because nothing asserted which recovery actions
 * exist here.
 *
 * **Instrumented: needs a device or emulator.**
 */
class ProductNotFoundRecoveryTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderNotFound(
        onScanAgain: () -> Unit = {},
        onScanLabel: () -> Unit = {},
        onEnterManually: () -> Unit = {},
        onSearch: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = null,
                        failure = Failure.NotFound,
                        barcode = "8712100849060",
                    ),
                    settings = AppSettings(),
                    onPortionChanged = {},
                    onAdjust = {},
                    onSetPortion = {},
                    onToggleFavorite = {},
                    onBack = {},
                    onVerify = {},
                    onDismissVerify = {},
                    onConfirmVerification = { _, _, _ -> },
                    onResetOnline = {},
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                    onRetry = {},
                    onSearch = onSearch,
                    onScanAgain = onScanAgain,
                )
            }
        }
    }

    private fun string(id: Int, vararg args: Any): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(id, *args)

    @Test
    fun productNotFoundOffersScanBarcodeAgain() {
        renderNotFound()
        compose.onNodeWithText(string(R.string.notfound_scan_again))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun scanBarcodeAgainIsTheActionInvoked() {
        var scanAgain = 0
        var scanLabel = 0
        renderNotFound(onScanAgain = { scanAgain++ }, onScanLabel = { scanLabel++ })

        compose.onNodeWithText(string(R.string.notfound_scan_again)).performScrollTo().performClick()

        assertEquals("scan again must route to the barcode camera", 1, scanAgain)
        assertEquals("it must not open the label scanner instead", 0, scanLabel)
    }

    @Test
    fun everyRecoveryPathIsStillReachable() {
        // Adding the primary action must not have pushed an existing recovery off the screen —
        // the failure mode that hid "Scan nutrition label" from every returning user once before.
        //
        // performScrollTo is the point of this test, not boilerplate: it fails outright if the panel
        // has no scroll container, which is what it had before this pass. A fourth action on a
        // fixed-height Column is clipped and unreachable at a large font scale, and nothing on
        // screen would say so.
        renderNotFound()
        listOf(
            R.string.notfound_scan_again,
            R.string.product_scan_label,
            R.string.search_action,
            R.string.permission_manual,
        ).forEach { id ->
            compose.onNodeWithText(string(id)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyRecoveryPathSurvivesALargeFontScale() {
        // 1.8x, the accessibility scale this repo has already been bitten by twice.
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = androidx.compose.ui.platform.LocalDensity.current.density,
                    fontScale = 1.8f,
                ),
            ) {
                JustTheCarbsTheme {
                    ProductScreen(
                        state = ProductUiState(
                            loading = false,
                            product = null,
                            failure = Failure.NotFound,
                            barcode = "8712100849060",
                        ),
                        settings = AppSettings(),
                        onPortionChanged = {},
                        onAdjust = {},
                        onSetPortion = {},
                        onToggleFavorite = {},
                        onBack = {},
                        onVerify = {},
                        onDismissVerify = {},
                        onConfirmVerification = { _, _, _ -> },
                        onResetOnline = {},
                        onScanLabel = {},
                        onEnterManually = {},
                        onRetry = {},
                        onSearch = {},
                        onScanAgain = {},
                    )
                }
            }
        }

        listOf(
            R.string.notfound_scan_again,
            R.string.product_scan_label,
            R.string.search_action,
            R.string.permission_manual,
        ).forEach { id ->
            compose.onNodeWithText(string(id)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theUnknownBarcodeIsStillShown() {
        renderNotFound()
        compose.onNodeWithText(string(R.string.notfound_barcode, "8712100849060"))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
