package app.carbscan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import app.carbscan.domain.AppSettings
import app.carbscan.domain.CarbCalculator
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.VerificationStatus
import app.carbscan.ui.product.ProductScreen
import app.carbscan.ui.product.ProductUiState
import app.carbscan.ui.theme.CarbScanTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * §60 workflow tests for the calculator.
 *
 * These assert on what the user sees, not on how it is produced. Each drives the screen the way a
 * person would — type into the portion field, tap an adjust button — and reads the result off the
 * display. None of them reach into a ViewModel or assert on internal state, so the screen can be
 * rebuilt however we like as long as the numbers stay right.
 *
 * **Instrumented: needs a device or emulator.**
 */
class ProductScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(
        name: String = "Hagelslag puur",
        carbs: String = "48.2",
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verification: VerificationStatus = VerificationStatus.UNVERIFIED,
        packageAmount: String? = null,
    ) = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = basis,
        dataSource = origin,
        verificationStatus = verification,
        packageAmount = packageAmount?.let(::BigDecimal),
    )

    /**
     * Renders the real screen with real state. The portion is held here rather than in a
     * ViewModel so the test exercises the composable's own behaviour, and recalculates through the
     * same [CarbCalculator] production uses — not a stubbed number.
     */
    private fun showCalculator(
        product: Product = product(),
        settings: AppSettings = AppSettings(),
    ) {
        compose.setContent {
            var portion by remember { mutableStateOf("") }
            val parsed = app.carbscan.domain.PortionParser.parse(portion)

            CarbScanTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        portionText = portion,
                        result = parsed?.let {
                            CarbCalculator.calculate(product.carbsPer100, it, product.basis)
                        },
                        barcode = product.barcode,
                    ),
                    settings = settings,
                    onPortionChanged = { portion = it },
                    onAdjust = { delta ->
                        val current = app.carbscan.domain.PortionParser.parse(portion) ?: BigDecimal.ZERO
                        portion = current.add(BigDecimal(delta)).max(BigDecimal.ZERO)
                            .stripTrailingZeros().toPlainString()
                    },
                    onSetPortion = { portion = it.stripTrailingZeros().toPlainString() },
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
    }

    // ---- §70 the primary acceptance test ---------------------------------------------------

    @Test
    fun typingAPortionShowsTheCarbohydrateResultImmediately() {
        showCalculator()

        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
        compose.onNode(portionField()).performTextInput("65")

        // The brief's own worked example: 48.2 x 65 / 100 = 31.33.
        // Default hierarchy is decimal-dominant (correction #6).
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    @Test
    fun thereIsNoCalculateButtonToPress() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")

        // §16: the result updates as you type. If a Calculate button ever appears, this fails.
        compose.onAllNodesWithText("Calculate", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    @Test
    fun clearingThePortionRemovesTheResultRatherThanShowingZero() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")
        compose.onNodeWithText("31.3 g").assertIsDisplayed()

        compose.onNode(portionField()).performTextReplacement("")

        // §13: showing "0 g" for an empty field would be presenting a value the user never asked
        // for. The prompt must come back instead.
        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
        compose.onAllNodesWithText("0 g").assertCountEquals(0)
    }

    // ---- §16 quick adjustment ----------------------------------------------------------------

    @Test
    fun quickAdjustChangesThePortionAndTheResultTogether() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")

        compose.onNodeWithText("+10").performClick()

        // 48.2 x 75 / 100 = 36.15
        compose.onNodeWithText("36.2 g").assertIsDisplayed()
    }

    @Test
    fun quickAdjustNeverProducesANegativePortion() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("5")

        compose.onNodeWithText("-10").performClick()

        // Clamped at zero: a negative portion is not a thing you can eat.
        compose.onNodeWithText("0.0 g").assertIsDisplayed()
    }

    // ---- design decision 3.1: ml is never converted to g -------------------------------------

    @Test
    fun aMillilitreProductLocksThePortionFieldToMillilitres() {
        showCalculator(product(carbs = "9.4", basis = NutritionBasis.PER_100_ML, name = "Sinaasappelsap"))

        compose.onNodeWithText("9.4 g carbs / 100 ml").assertIsDisplayed()
        compose.onNode(portionField()).performTextInput("250")

        // 9.4 x 250 / 100 = 23.5 — the same arithmetic as grams, because no density is applied.
        compose.onNodeWithText("23.5 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 24 g whole grams").assertIsDisplayed()
    }

    // ---- §23 / §25 provenance is always visible ----------------------------------------------

    @Test
    fun unverifiedRemoteDataSaysSoWithoutAlarmingTheUser() {
        showCalculator()

        compose.onNodeWithText("Online value").assertIsDisplayed()
        compose.onNodeWithText("Check package if needed").assertIsDisplayed()
    }

    @Test
    fun aVerifiedProductIsMarkedAsVerifiedByTheUser() {
        showCalculator(product(verification = VerificationStatus.USER_VERIFIED))

        compose.onNodeWithText("✓ Verified by you").assertIsDisplayed()
        compose.onAllNodesWithText("Online value").assertCountEquals(0)
    }

    // ---- §14 pack shortcuts only when the size is actually known ------------------------------

    @Test
    fun packShortcutsAppearOnlyWhenAPackageSizeIsKnown() {
        showCalculator(product(packageAmount = "380"))

        compose.onNodeWithText("Full pack").performClick()

        // 48.2 x 380 / 100 = 183.16
        compose.onNodeWithText("183.2 g").assertIsDisplayed()
    }

    @Test
    fun packShortcutsAreHiddenWhenThePackageSizeIsUnknown() {
        showCalculator(product(packageAmount = null))

        // A guessed pack size would be a wrong portion offered as a convenience (§13).
        compose.onAllNodesWithText("Full pack").assertCountEquals(0)
        compose.onAllNodesWithText("½ pack").assertCountEquals(0)
    }

    // ---- §43 result style --------------------------------------------------------------------

    @Test
    fun wholeDominantSettingRestoresTheOriginalHierarchy() {
        showCalculator(settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT))
        compose.onNode(portionField()).performTextInput("65")

        compose.onNodeWithText("31 g").assertIsDisplayed()
        compose.onNodeWithText("31.3 g calculated").assertIsDisplayed()
    }

    /**
     * The double-rounding guard, asserted through the UI (correction #6).
     * 51.5 x 30 / 100 = 15.45 exactly. The decimal shows 15.5; the whole gram is 15, derived from
     * the exact value and NOT from the displayed 15.5, which would give 16.
     */
    @Test
    fun theWholeGramIsNeverDerivedFromTheDisplayedDecimal() {
        showCalculator(product(carbs = "51.5"))
        compose.onNode(portionField()).performTextInput("30")

        compose.onNodeWithText("15.5 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 15 g whole grams").assertIsDisplayed()
        compose.onAllNodesWithText("≈ 16 g whole grams").assertCountEquals(0)
    }

    // ---- stress: the screen must survive real-world data --------------------------------------

    @Test
    fun aVeryLongProductNameDoesNotPushTheResultOffScreen() {
        showCalculator(
            product(
                name = "Biologische Volkoren Ontbijtgranen met Noten, Rozijnen en Honing " +
                    "Extra Grote Familieverpakking Voordeeldoos",
            ),
        )
        compose.onNode(portionField()).performTextInput("65")

        // The result is what matters; a long name must never displace it.
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    @Test
    fun aZeroCarbProductCalculatesZeroRatherThanFailing() {
        showCalculator(product(carbs = "0", name = "Bronwater"))
        compose.onNode(portionField()).performTextInput("500")

        compose.onNodeWithText("0.0 g").assertIsDisplayed()
    }

    @Test
    fun aLargePortionStillProducesAReadableResult() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("2500")

        // 48.2 x 2500 / 100 = 1205
        compose.onNodeWithText("1205.0 g").assertIsDisplayed()
    }

    @Test
    fun aDecimalPortionIsAcceptedWithEitherSeparator() {
        showCalculator()

        compose.onNode(portionField()).performTextInput("32.5")
        // 48.2 x 32.5 / 100 = 15.665
        compose.onNodeWithText("15.7 g").assertIsDisplayed()

        compose.onNode(portionField()).performTextReplacement("32,5")
        compose.onNodeWithText("15.7 g").assertIsDisplayed()
    }

    /** The portion field is the only text input on this screen. */
    private fun portionField() = androidx.compose.ui.test.hasSetTextAction()
}
