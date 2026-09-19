package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.scan.MANUAL_BARCODE_CONTINUE_TAG
import app.justthecarbs.ui.scan.MANUAL_BARCODE_ERROR_TAG
import app.justthecarbs.ui.scan.MANUAL_BARCODE_FIELD_TAG
import app.justthecarbs.ui.scan.MANUAL_BARCODE_SHEET_TAG
import app.justthecarbs.ui.scan.ManualBarcodeSheet
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Manual and pasted barcode entry as a rendered sheet (1.0.8).
 *
 * The input rules are covered in the JVM suite. What only a rendered sheet can answer is whether
 * the number can actually be got *out* of it: whether Continue gates on validity, whether the
 * keyboard's Done key does the same thing the button does, and whether an invalid code is refused
 * by both routes rather than by only the one that was remembered.
 */
class ManualBarcodeSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /** A real EAN-13 (Coca-Cola 330ml), so acceptance is never accidental. */
    private val valid = "5449000000996"

    private val confirmed = mutableListOf<String>()
    private var dismissed = 0

    private fun showSheet() {
        confirmed.clear()
        dismissed = 0
        compose.setContent {
            JustTheCarbsTheme {
                ManualBarcodeSheet(
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun type(code: String) {
        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performTextReplacement(code)
        compose.waitForIdle()
    }

    @Test
    fun aValidCodeCanBeSubmittedWithTheButton() {
        showSheet()
        type(valid)

        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(valid), confirmed)
    }

    @Test
    fun theKeyboardsDoneKeySubmitsTheSameCode() {
        // Someone typing thirteen digits on a numeric keypad finishes at the keypad, not at a
        // button — so Done must do what Continue does rather than merely dismissing the keyboard.
        showSheet()
        type(valid)

        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performImeAction()
        compose.waitForIdle()

        assertEquals(listOf(valid), confirmed)
    }

    @Test
    fun anInvalidCodeCannotProceedByEitherRoute() {
        // The last digit is wrong by one. Both routes must refuse it: a check digit is the only
        // thing standing between a mistyped code and a confident lookup for an unrelated product.
        showSheet()
        type("5449000000997")

        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performImeAction()
        compose.waitForIdle()

        assertTrue("an invalid code must not be submitted", confirmed.isEmpty())
    }

    @Test
    fun anInvalidCodeIsExplainedInPlainWords() {
        showSheet()
        type("12345678")

        compose.onNodeWithTag(MANUAL_BARCODE_ERROR_TAG).assertIsDisplayed()
        // The message names the problem in the user's terms. No checksum, no symbology, no parser
        // vocabulary — none of which the person holding the packet can act on.
        compose.onNodeWithText("That barcode doesn't look valid").assertIsDisplayed()
    }

    @Test
    fun nothingIsFlaggedWhileTheCodeIsStillBeingTyped() {
        // Every valid code passes through being too short, so complaining early would flag a
        // correct entry mid-typing.
        showSheet()
        type("5449")

        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsNotEnabled()
        compose.onNodeWithText("That barcode doesn't look valid").assertDoesNotExist()
    }

    @Test
    fun aUpcATypedOffAPackageBecomesTheSameKeyAScanProduces() {
        // Twelve digits printed under a US barcode. What leaves this sheet is the normalised
        // 13-digit GTIN — the same database key the camera path resolves to — which is what makes
        // "one barcode flow" true rather than aspirational.
        showSheet()
        type("036000291452")

        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf("0036000291452"), confirmed)
    }

    @Test
    fun nonDigitsAreRefusedRatherThanRepaired() {
        showSheet()
        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performTextClearance()
        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).performTextInput("54a49b000000996")
        compose.waitForIdle()

        // The letters never enter the field, and the digits keep their order — a filter, not a
        // repair. The result is the valid code, so Continue opens.
        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(valid), confirmed)
    }

    @Test
    fun everyControlStaysReachableAtALargeFontScale() {
        // A sheet with a fixed-height error slot and a Cancel/Continue row is exactly the shape
        // that clips when the text grows. Driven through the real controls rather than captured as
        // a screenshot: `ModalBottomSheet` renders in its own subcomposition, so a LocalDensity
        // override wrapped around the sheet's caller never reaches it (measured — a "2x" capture
        // came back byte-identical to the 1x one), which makes a pixel capture here say nothing.
        confirmed.clear()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                JustTheCarbsTheme {
                    ManualBarcodeSheet(onConfirm = { confirmed += it }, onDismiss = {})
                }
            }
        }
        type(valid)

        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).assertIsDisplayed()
        // The primary action must still be on screen and still meet the touch-target floor: a
        // Continue button pushed off the bottom is a sheet with no way out but Back.
        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        assertEquals(listOf(valid), confirmed)
    }

    @Test
    fun theSheetIsPresentWithItsFieldAndPrimaryAction() {
        showSheet()
        compose.onNodeWithTag(MANUAL_BARCODE_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MANUAL_BARCODE_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MANUAL_BARCODE_CONTINUE_TAG).assertIsDisplayed()
        // The help line says where to find the number, which is the one thing someone holding a
        // damaged package needs told.
        compose.onNodeWithText("Type the number printed under the barcode.").assertIsDisplayed()
    }
}
