package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The screen a scanned nutrition label lands on (1.0.3 P1).
 *
 * These drive the calculator the way a person would — type a portion, read the total — against a
 * product that has **no name and no barcode**, which is the state a label reading produces. Nothing
 * here reaches into a ViewModel: the point is that the screen itself works with nothing behind it.
 *
 * **Instrumented: needs a device or emulator.**
 */
class QuickCalculationScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** Exactly what `startQuickCalculation` builds: no name, no barcode, OCR provenance. */
    private fun scratch(
        carbs: String = "48",
        basis: NutritionBasis = NutritionBasis.PER_100_G,
    ) = Product(
        barcode = "",
        name = "",
        carbsPer100 = BigDecimal(carbs),
        basis = basis,
        dataSource = ProductDataOrigin.OCR,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * Types into the portion field and puts the keyboard away before anything is read.
     *
     * The dismissal is not politeness. Gboard is a real ~641 px window over the bottom of the screen,
     * and `assertIsDisplayed` tests visibility against the window — so a result pinned to the bottom
     * edge is genuinely not displayed while the IME is up, and the assertion is reporting the truth.
     * Leaving it open is what made an arbitrary test fail per run in the 2026-08-28 suite.
     */
    private fun typePortion(text: String) {
        // Matched by content description, not by text: the field has no visible label and its `0`
        // placeholder is deliberately cleared from semantics, so there is nothing textual to match.
        compose.onNodeWithContentDescription("Portion in g").performTextInput(text)
        compose.onNodeWithContentDescription("Portion in g").performImeAction()
        compose.waitForIdle()
    }

    private fun showQuick(
        product: Product = scratch(),
        unsaved: Boolean = true,
        showSaveForm: Boolean = false,
        onShowSave: (Boolean) -> Unit = {},
        onSave: (String) -> Unit = {},
    ) {
        compose.setContent {
            var portion by remember { mutableStateOf("") }
            val parsed = PortionParser.parse(portion)

            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        portionText = portion,
                        result = parsed?.let {
                            CarbCalculator.calculate(product.carbsPer100, it, product.basis)
                        },
                        barcode = product.barcode,
                        unsaved = unsaved,
                        showSaveQuickCalculationForm = showSaveForm,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = { portion = it },
                    onAdjust = {},
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
                    onShowSaveQuickCalculation = onShowSave,
                    onSaveQuickCalculation = onSave,
                )
            }
        }
    }

    /**
     * The headline claim of the patch: a portion typed against a nameless reading produces a total.
     *
     * Scoped to [PRODUCT_RESULT_TAG] rather than searching the screen for "16.8 g" — the portion
     * field also holds text that such a search matches once it has a value and a unit suffix, so an
     * unscoped assertion can pass by finding the input instead of the answer.
     */
    @Test
    fun aNamelessReadingCalculatesATotal() {
        showQuick()
        typePortion("35")

        compose.onNode(hasTestTag(PRODUCT_RESULT_TAG)).assertIsDisplayed()
        compose.onNode(hasTestTag(PRODUCT_RESULT_TAG) and hasText("16.8", substring = true))
            .assertIsDisplayed()
    }

    /**
     * The detected figure **and its basis** are on screen before any portion is typed (P2).
     *
     * Asserted on the full "48 g carbs / 100 g" line rather than on the bare number. The basis is
     * the half that cannot be recovered by looking at the package again in a hurry, and a figure
     * shown without it is the one presentation this app must never produce. The bare number matches
     * two nodes anyway — this line and the pending-result slot, which deliberately previews the
     * per-100 figure the result will be scaled from.
     */
    @Test
    fun theDetectedValueAndBasisAreShown() {
        showQuick()

        compose.onNodeWithText("48 g carbs / 100 g").assertIsDisplayed()
    }

    /** A millilitre reading must say millilitres — the unit the portion will be measured in. */
    @Test
    fun aMillilitreReadingShowsAMillilitreBasis() {
        showQuick(product = scratch(carbs = "9.4", basis = NutritionBasis.PER_100_ML))

        compose.onNodeWithText("9.4 g carbs / 100 ml").assertIsDisplayed()
    }

    /** Provenance is stated: the number came off a label, and it says so (P2). */
    @Test
    fun theReadingIsLabelledAsComingFromTheLabel() {
        showQuick()

        compose.onNodeWithText("Read from label by you").assertIsDisplayed()
    }

    /**
     * The screen does not present itself as an incomplete product record.
     *
     * A nameless product would otherwise render an empty monogram plate under an empty title bar,
     * which reads as a record that failed to load rather than as the reading just taken.
     */
    @Test
    fun anUnnamedCalculationIsTitledAsAQuickCalculation() {
        showQuick()

        compose.onNodeWithText("Quick calculation").assertIsDisplayed()
    }

    /**
     * The portion field is ready to type into on arrival (1.0.3 ease pass).
     *
     * A quick calculation has exactly one input and the user has just confirmed the figure, so the
     * only remaining act is saying how much they are eating. Requiring a tap on the single tappable
     * thing on the screen is a step with no decision in it. Measured on the emulator as
     * `mInputShown=false` before this and `true` after; asserted here as focus, which is what the
     * keyboard follows from and what a test can see.
     */
    @Test
    fun thePortionFieldIsReadyToTypeIntoOnArrival() {
        showQuick()

        compose.onNodeWithContentDescription("Portion in g").assertIsFocused()
    }

    /**
     * A saved product keeps its hands off the keyboard, and the reason is not symmetry.
     *
     * It arrives pre-filled with the remembered portion, and its *Usual* shortcuts and pack buttons
     * are alternatives to typing at all — opening the keyboard would cover the very controls that
     * make a repeat visit fast, in order to offer an edit the user may not want.
     */
    @Test
    fun aSavedProductDoesNotGrabTheKeyboard() {
        showQuick(
            product = scratch().copy(barcode = "local:abc", name = "Hagelslag"),
            unsaved = false,
        )

        compose.onNodeWithContentDescription("Portion in g").assertIsNotFocused()
    }

    /**
     * Focus is claimed on arrival, not re-claimed afterwards.
     *
     * The request is keyed on `Unit`, so it fires once for the life of the screen. Were it to run on
     * recomposition, every keystroke, arriving result and meal-bar appearance would drag focus back
     * to this field — which is worse than the tap it saves, because it would fight the user.
     */
    @Test
    fun focusIsNotStolenBackAfterTyping() {
        showQuick()

        compose.onNodeWithContentDescription("Portion in g").performTextInput("35")
        compose.onNodeWithContentDescription("Portion in g").performImeAction()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Portion in g").assertIsNotFocused()
    }

    /** Saving is offered, and it is not what the user has to do to get their number. */
    @Test
    fun savingIsOfferedAsASecondaryAction() {
        showQuick()

        compose.onNodeWithText("Save product").performScrollTo().assertIsDisplayed()
    }

    /** A saved product must not offer to be saved again — there is nothing left to save. */
    @Test
    fun aSavedProductDoesNotOfferToBeSaved() {
        showQuick(
            product = scratch().copy(barcode = "local:abc", name = "Hagelslag"),
            unsaved = false,
        )

        val matches = compose.onAllNodes(hasText("Save product")).fetchSemanticsNodes().size
        assertEquals("a saved product still offered to be saved", 0, matches)
    }

    /** Tapping *Save product* asks for the form rather than writing anything on the spot. */
    @Test
    fun tappingSaveOpensTheForm() {
        var requested: Boolean? = null
        showQuick(onShowSave = { requested = it })

        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(true, requested)
    }

    /**
     * The name reaches the save, and the calculation stays visible behind the dialog.
     *
     * The second half matters as much as the first: the user is saving *because* the number was
     * worth keeping, so losing sight of it at that moment would be the worst possible time.
     */
    @Test
    fun theFormCollectsANameAndSavesIt() {
        var savedName: String? = null
        showQuick(showSaveForm = true, onSave = { savedName = it })

        compose.onNodeWithText("Product name").performTextInput("Hagelslag")
        compose.waitForIdle()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        assertEquals("Hagelslag", savedName)
    }

    /** An empty name cannot be submitted — the button is the guard, not an error after the fact. */
    @Test
    fun theFormCannotBeSubmittedWithoutAName() {
        var savedName: String? = null
        showQuick(showSaveForm = true, onSave = { savedName = it })

        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        assertTrue("a blank name must not reach the save", savedName == null)
    }


    /** Cancelling closes the form and asks for nothing else. */
    @Test
    fun cancellingTheFormRequestsItBeClosed() {
        var requested: Boolean? = null
        showQuick(showSaveForm = true, onShowSave = { requested = it })

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        assertEquals(false, requested)
    }
}
