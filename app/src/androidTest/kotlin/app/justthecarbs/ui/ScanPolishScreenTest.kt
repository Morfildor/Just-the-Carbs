package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.Failure
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * Behaviour added by the pre-release UI/UX polish pass.
 *
 * These assert *states the user can be left in*, which is where this repo's UI defects have
 * historically lived — a green suite has twice coexisted with a control that could not be reached
 * and an action that was never composed. Each case here failed before the change it covers.
 */
class ScanPolishScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun product() = Product(
        barcode = "1234567890128",
        name = "Test biscuits",
        brand = null,
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * The lookup wait names the work rather than showing a bare spinner.
     *
     * The scanner hands off to this screen with the phone still pointed at a shelf. An unlabelled
     * indeterminate circle is identical whether the app is reading its own database in 20ms or
     * stalled on a slow connection, and "did my scan even register?" is what makes people re-scan.
     */
    @Test
    fun theLoadingStateNamesWhatItIsWaitingFor() {
        rule.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(loading = true, barcode = "1234567890128"),
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
                )
            }
        }

        rule.onNodeWithText("Finding product…").assertIsDisplayed()
        // The barcode stays visible while waiting: if the lookup stalls, this is the one fact the
        // user can act on (retype it, or recognise that the scanner read the wrong package).
        rule.onNodeWithText("1234567890128", substring = true).assertIsDisplayed()
    }

    /**
     * A failed lookup never leaves the user without a way forward, and the recovery is reachable
     * without going back to Home.
     *
     * Pinned because the app has shipped this exact dead end before: *Product not found* offered no
     * route back to the camera at all, so recovering from a mis-scan meant navigating out first.
     */
    @Test
    fun productNotFoundOffersRecoveryWithoutLeavingTheScreen() {
        var scannedAgain = false
        rule.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        failure = Failure.NotFound,
                        barcode = "1234567890128",
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
                    onScanAgain = { scannedAgain = true },
                )
            }
        }

        rule.onNodeWithText("Scan barcode again").performClick()
        assert(scannedAgain) { "the primary recovery action did not fire" }
    }
}
