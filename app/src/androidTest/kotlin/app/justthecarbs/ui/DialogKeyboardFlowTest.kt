package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.ui.product.SaveQuickCalculationDialog
import app.justthecarbs.ui.product.VerifyDialog
import app.justthecarbs.ui.scan.ManualBarcodeDialog
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The keyboard's own action key in the three small entry dialogs (UX polish, wave 2).
 *
 * Done submits through the same path, and under the same condition, as the dialog's visible confirm
 * button: a key must never do what the disabled button refuses.
 */
class DialogKeyboardFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nameLabel get() = context.getString(R.string.manual_name)
    private val barcodeLabel get() = context.getString(R.string.manual_barcode)
    private fun carbsLabel(basis: NutritionBasis) = context.getString(R.string.manual_carbs, basis.unitLabel)

    // --- Save quick calculation ---

    private fun showSaveQuick(saving: Boolean = false, saved: MutableList<String>) {
        compose.setContent {
            JustTheCarbsTheme {
                SaveQuickCalculationDialog(
                    nameError = false,
                    saving = saving,
                    onSave = { saved += it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun doneOnANamedQuickCalculationSavesIt() {
        val saved = mutableListOf<String>()
        showSaveQuick(saved = saved)

        compose.onNodeWithText(nameLabel).performTextInput("Hagelslag")
        compose.onNode(hasSetTextAction() and hasText("Hagelslag")).performImeAction()

        compose.runOnIdle { assertEquals(listOf("Hagelslag"), saved) }
    }

    @Test
    fun doneOnABlankNameSavesNothing() {
        val saved = mutableListOf<String>()
        showSaveQuick(saved = saved)

        compose.onNodeWithText(nameLabel).performClick()
        compose.onNodeWithText(nameLabel).performImeAction()

        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
    }

    @Test
    fun doneWhileASaveIsInFlightSavesNothingMore() {
        val saved = mutableListOf<String>()
        showSaveQuick(saving = true, saved = saved)

        compose.onNodeWithText(nameLabel).performTextInput("Hagelslag")
        compose.onNode(hasSetTextAction() and hasText("Hagelslag")).performImeAction()

        compose.runOnIdle { assertEquals(emptyList<String>(), saved) }
    }

    // --- Verify ---

    private val product = Product(
        barcode = "8712100849060",
        name = "Hagelslag",
        carbsPer100 = BigDecimal("62"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
    )

    private fun showVerify(confirmed: MutableList<BigDecimal>) {
        compose.setContent {
            JustTheCarbsTheme {
                VerifyDialog(
                    product = product,
                    onConfirm = { carbs, _, _ -> confirmed += carbs },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun nextOnTheVerifyNameMovesToTheCarbsField() {
        showVerify(mutableListOf())

        compose.onNodeWithText(nameLabel).performClick()
        compose.onNodeWithText(nameLabel).performImeAction()

        compose.onNodeWithText(carbsLabel(NutritionBasis.PER_100_G)).assertIsFocused()
    }

    @Test
    fun doneOnAValidVerifiedFigureConfirmsIt() {
        val confirmed = mutableListOf<BigDecimal>()
        showVerify(confirmed)

        val carbs = compose.onNodeWithText(carbsLabel(NutritionBasis.PER_100_G))
        carbs.performTextReplacement("61.5")
        carbs.performImeAction()

        compose.runOnIdle { assertEquals(listOf(BigDecimal("61.5")), confirmed) }
    }

    @Test
    fun doneOnAnImpossibleVerifiedFigureConfirmsNothing() {
        val confirmed = mutableListOf<BigDecimal>()
        showVerify(confirmed)

        val carbs = compose.onNodeWithText(carbsLabel(NutritionBasis.PER_100_G))
        carbs.performTextReplacement("615")
        carbs.performImeAction()

        compose.runOnIdle { assertEquals(emptyList<BigDecimal>(), confirmed) }
    }

    // --- Manual barcode ---

    private fun showBarcode(confirmed: MutableList<String>) {
        compose.setContent {
            JustTheCarbsTheme {
                ManualBarcodeDialog(onConfirm = { confirmed += it }, onDismiss = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theBarcodeDialogOpensReadyToType() {
        showBarcode(mutableListOf())

        compose.onNodeWithText(barcodeLabel).assertIsFocused()
    }

    @Test
    fun doneOnAValidBarcodeConfirmsIt() {
        val confirmed = mutableListOf<String>()
        showBarcode(confirmed)

        compose.onNodeWithText(barcodeLabel).performTextInput("4006381333931")
        compose.onNode(hasSetTextAction() and hasText("4006381333931")).performImeAction()

        compose.runOnIdle { assertEquals(listOf("4006381333931"), confirmed) }
    }

    @Test
    fun doneOnAnInvalidBarcodeConfirmsNothing() {
        val confirmed = mutableListOf<String>()
        showBarcode(confirmed)

        compose.onNodeWithText(barcodeLabel).performTextInput("4006381333932")
        compose.onNode(hasSetTextAction() and hasText("4006381333932")).performImeAction()

        compose.runOnIdle { assertEquals(emptyList<String>(), confirmed) }
    }
}
