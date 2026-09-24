package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.OcrElement
import app.justthecarbs.ui.scan.ASSIST_FOCUSED_FIELD_TAG
import app.justthecarbs.ui.scan.ASSIST_FOCUSED_SUBMIT_TAG
import app.justthecarbs.ui.scan.ASSIST_MANUAL_FIELD_TAG
import app.justthecarbs.ui.scan.AssistState
import app.justthecarbs.ui.scan.AssistedReadingScreen
import app.justthecarbs.ui.scan.CorrectionTarget
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * System Back on the frozen photograph, and the two typing steps' keyboard behaviour.
 *
 * The scanner's frozen branch owns one Back handler that retakes, exactly like the Retake button.
 * Each test stands in for it with an outer [BackHandler] that only counts, so what is pinned here is
 * the split: a sub-step of the assisted screen steps back to its choices itself, and everything
 * else falls through to the scanner.
 */
class AssistedTypingAndBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var scannerBacks = 0
    private var used: Pair<BigDecimal, NutritionBasis>? = null

    private fun bitmap(): Bitmap = Bitmap.createBitmap(421, 912, Bitmap.Config.ARGB_8888)

    /** No basis printed: typing leaves both bases open. */
    private fun noBasisDocument() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 0, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 0, 0),
            OcrElement("waarvan", OcrBox(80, 250, 170, 275), 1, 0),
            OcrElement("suikers", OcrBox(178, 250, 250, 275), 1, 0),
            OcrElement("47,6", OcrBox(380, 250, 450, 275), 1, 0),
        ),
    )

    /** A `per 100 ml` header centred over the value column: the label states the basis. */
    private fun millilitreDocument() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("ml", OcrBox(448, 90, 490, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("2,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("waarvan", OcrBox(80, 250, 170, 275), 2, 0),
            OcrElement("suikers", OcrBox(178, 250, 250, 275), 2, 0),
            OcrElement("1,9", OcrBox(380, 250, 450, 275), 2, 0),
        ),
    )

    /** The white Dutch table (`20260903-085019-213`): row and `per 100g` basis established. */
    private fun whiteTableDocument() = OcrDocument(
        width = 421,
        height = 912,
        elements = listOf(
            OcrElement("Gemiddelde", OcrBox(82, 296, 163, 315), 0, 0),
            OcrElement("voedingswaarde", OcrBox(169, 297, 278, 315), 0, 0),
            OcrElement("per", OcrBox(283, 298, 303, 315), 0, 0),
            OcrElement("100g", OcrBox(309, 299, 338, 316), 0, 0),
            OcrElement("Koolhydraten,", OcrBox(85, 380, 179, 397), 1, 0),
            OcrElement("waarvan", OcrBox(184, 380, 236, 397), 1, 0),
            OcrElement("2.8", OcrBox(326, 377, 345, 392), 1, 0),
            OcrElement("g", OcrBox(350, 379, 357, 393), 1, 0),
        ),
    )

    private fun show(state: AssistState) {
        rule.setContent {
            JustTheCarbsTheme {
                // Stands in for the scanner's frozen-branch handler, which retakes.
                BackHandler { scannerBacks++ }
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = state,
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }
    }

    private fun pressBack() {
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    // ---- Back ----------------------------------------------------------------------------------

    @Test
    fun backFromTypingReturnsToTheChoicesAndKeepsThePhoto() {
        show(AssistState(document = noBasisDocument()))
        rule.onNodeWithText("Type it in").performClick()

        pressBack()

        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).assertDoesNotExist()
        rule.onNodeWithText("Type it in").assertIsDisplayed()
        assertEquals(0, scannerBacks)
    }

    @Test
    fun backFromTappingTheRowReturnsToTheChoices() {
        show(AssistState(document = noBasisDocument()))
        rule.onNodeWithText("Tap the carbohydrate row").performClick()

        pressBack()

        rule.onNodeWithText("Type it in").assertIsDisplayed()
        assertEquals(0, scannerBacks)
    }

    @Test
    fun backFromTheChoicesIsLeftToTheScannerToRetake() {
        show(AssistState(document = noBasisDocument()))

        pressBack()

        assertEquals(1, scannerBacks)
    }

    /** A correction has no choices screen to step back to; its only way out is Retake. */
    @Test
    fun backFromACorrectionIsLeftToTheScannerToRetake() {
        show(
            AssistState(
                document = whiteTableDocument(),
                correctionTarget = CorrectionTarget(
                    rejectedValue = BigDecimal("28"),
                    basis = NutritionBasis.PER_100_G,
                    rowText = "Koolhydraten, waarvan 28 g",
                    rowInSourceSpace = OcrBox(85, 377, 357, 397),
                ),
            ),
        )

        pressBack()

        assertEquals(1, scannerBacks)
    }

    // ---- typing --------------------------------------------------------------------------------

    @Test
    fun theTypedValueFieldIsFocusedOnArrival() {
        show(AssistState(document = noBasisDocument()))
        rule.onNodeWithText("Type it in").performClick()

        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun theFocusedAmountFieldIsFocusedOnArrival() {
        show(AssistState(document = whiteTableDocument(), startOnFocusedEntry = true))

        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).assertIsFocused()
    }

    /** Present from the start, so the first keystroke does not move the layout, but unusable. */
    @Test
    fun theFocusedAmountActionIsPresentButDisabledUntilAValueIsTyped() {
        show(AssistState(document = whiteTableDocument(), startOnFocusedEntry = true))

        rule.onNodeWithTag(ASSIST_FOCUSED_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).performTextInput("2,8")
        rule.onNodeWithTag(ASSIST_FOCUSED_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun doneSubmitsAFocusedAmount() {
        show(AssistState(document = whiteTableDocument(), startOnFocusedEntry = true))

        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).performTextInput("2,8")
        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).performImeAction()

        assertEquals(0, used!!.first.compareTo(BigDecimal("2.8")))
        assertEquals(NutritionBasis.PER_100_G, used!!.second)
    }

    @Test
    fun doneNeverSubmitsAnImpossibleFocusedAmount() {
        show(AssistState(document = whiteTableDocument(), startOnFocusedEntry = true))

        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).performTextInput("790")
        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG).performImeAction()

        assertNull(used)
    }

    @Test
    fun doneSubmitsATypedValueUnderTheBasisTheLabelStated() {
        show(AssistState(document = millilitreDocument()))
        rule.onNodeWithText("Type it in").performClick()

        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("2,5")
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performImeAction()

        assertEquals(0, used!!.first.compareTo(BigDecimal("2.5")))
        assertEquals(NutritionBasis.PER_100_ML, used!!.second)
    }

    /** With both bases open the user must say which; Done must not pick one for them. */
    @Test
    fun doneNeverChoosesBetweenTwoBases() {
        show(AssistState(document = noBasisDocument()))
        rule.onNodeWithText("Type it in").performClick()

        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53,5")
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performImeAction()

        assertNull(used)
        rule.onNodeWithText("Use / 100 g").assertIsEnabled()
        rule.onNodeWithText("Use / 100 ml").assertIsEnabled()
    }
}
