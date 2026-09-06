package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ui.scan.ASSIST_CORRECTION_FIELD_TAG
import app.justthecarbs.ui.scan.ASSIST_CORRECTION_SUBMIT_TAG
import app.justthecarbs.ui.scan.AssistState
import app.justthecarbs.ui.scan.AssistedReadingScreen
import app.justthecarbs.ui.scan.CorrectionTarget
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Direct correction of a rejected OCR proposal — reached from rejecting an ordinary
 * [app.justthecarbs.ui.scan.VerificationScreen] proposal or a
 * [app.justthecarbs.ui.scan.VerificationScreenMode.ScaleUnresolved] reading (UX-reduction pass).
 *
 * The property under test throughout: the app already knows the row, the basis and the rejected
 * value, so this screen must ask for none of that again — no "tap the carbohydrate row", no basis
 * picker, no generic Choosing menu. Only the digits are asked for.
 */
class CorrectingKnownAmountScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)

    private fun target(rejected: String = "40") = CorrectionTarget(
        rejectedValue = BigDecimal(rejected),
        basis = NutritionBasis.PER_100_G,
        rowText = "Koolhydraten 40 g",
        rowInSourceSpace = OcrBox(60, 180, 220, 205),
    )

    // ---- routing: reject lands directly here, never the generic menu -----------------------------

    @Test
    fun aCorrectionTargetOpensDirectlyOnTheCorrectionStepNeverTheGenericMenu() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        // The correction field is present immediately — no navigation, no menu tap needed.
        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).assertIsDisplayed()
        // None of the generic-menu affordances a fresh automatic-attempt failure would show.
        rule.onNodeWithText("Tap the carbohydrate row").assertDoesNotExist()
        rule.onNodeWithText("Choose the carbohydrate figure").assertDoesNotExist()
        rule.onNodeWithText("Type it in").assertDoesNotExist()
    }

    @Test
    fun theKnownRowIsEchoedBackAndNoBasisPickerIsOffered() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("From: Koolhydraten 40 g").assertIsDisplayed()
        // No basis chip/picker anywhere — the basis is fixed to what the app already established.
        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
    }

    // ---- focus (task item 6) ----------------------------------------------------------------------

    @Test
    fun theCorrectionFieldReceivesFocusAutomatically() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).assertIsFocused()
    }

    // ---- pre-fill + must-change-to-submit (task item 6, 9) -----------------------------------------

    @Test
    fun theRejectedValueIsPrefilledButCannotBeResubmittedUnchanged() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target("40")),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        // The field shows the rejected value...
        rule.onNodeWithText("40").assertIsDisplayed()
        // ...but the submit action is disabled until the user actually edits it.
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).performClick()
        assertNull("an unedited rejection must not be resubmittable", used)

        // Retyping the SAME rejected value (as if selected and retyped identically) must still not
        // enable submission.
        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performTextReplacement("40")
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun editingTheValueEnablesSubmitAndTheVisibleButtonWorks() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target("40")),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performTextReplacement("4.0")
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).assertIsEnabled()
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).performClick()

        assertEquals(0, used!!.first.compareTo(BigDecimal("4.0")))
        assertEquals(NutritionBasis.PER_100_G, used!!.second)
    }

    // ---- IME Done (task items 7, 8) ----------------------------------------------------------------

    @Test
    fun imeDoneSubmitsAValidChangedCorrection() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target("40")),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performTextReplacement("4.0")
        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performImeAction()

        assertEquals(0, used!!.first.compareTo(BigDecimal("4.0")))
        assertEquals(NutritionBasis.PER_100_G, used!!.second)
    }

    @Test
    fun imeDoneDoesNothingForAnUnchangedValue() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target("40")),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performImeAction()
        assertNull(used)
    }

    @Test
    fun imeDoneDoesNothingForAnImplausibleChangedValue() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = null, correctionTarget = target("40")),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        // 790 g / 100 g is not plausible; it differs from the rejected 40 but must still not submit.
        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performTextReplacement("790")
        rule.onNodeWithTag(ASSIST_CORRECTION_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ASSIST_CORRECTION_FIELD_TAG).performImeAction()
        assertNull(used)
    }
}
