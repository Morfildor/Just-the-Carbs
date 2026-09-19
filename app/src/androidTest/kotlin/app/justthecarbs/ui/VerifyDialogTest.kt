package app.justthecarbs.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.ui.product.VerifyDialog
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VerifyDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(basis: NutritionBasis = NutritionBasis.PER_100_G) = Product(
        barcode = "8712100849060",
        name = "Hagelslag puur",
        carbsPer100 = BigDecimal("48.2"),
        basis = basis,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
    )

    private fun showDialog(
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        onConfirm: (BigDecimal, NutritionBasis, String) -> Unit = { _, _, _ -> },
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                VerifyDialog(
                    product = product(basis),
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun gramBasisNamesTheUnitWithoutAnUnresolvedFormatToken() {
        showDialog(NutritionBasis.PER_100_G)

        compose.onNodeWithText("Carbs per 100 g").assertIsDisplayed()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun millilitreBasisNamesTheUnitWithoutAnUnresolvedFormatToken() {
        showDialog(NutritionBasis.PER_100_ML)

        compose.onNodeWithText("Carbs per 100 ml").assertIsDisplayed()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun malformedCarbohydratesShowTextAndDisableConfirm() {
        showDialog()

        compose.onNodeWithText("48.2").performTextReplacement("not a number")

        compose.onNodeWithText("Enter carbohydrates per 100 as a number").assertIsDisplayed()
        compose.onNodeWithText("Save as verified").assertIsNotEnabled()
    }

    @Test
    fun negativeCarbohydratesShowTextAndDisableConfirm() {
        showDialog()

        compose.onNodeWithText("48.2").performTextReplacement("-1")

        compose.onNodeWithText("Enter 0 or more").assertIsDisplayed()
        compose.onNodeWithText("Save as verified").assertIsNotEnabled()
    }

    @Test
    fun outOfRangeCarbohydratesShowTextAndDisableConfirm() {
        showDialog()

        compose.onNodeWithText("48.2").performTextReplacement("101")

        compose.onNodeWithText("That value is outside the possible range").assertIsDisplayed()
        compose.onNodeWithText("Save as verified").assertIsNotEnabled()
    }

    @Test
    fun validCarbohydratesRemainConfirmableAndAreReturnedUnchanged() {
        var confirmed: Triple<BigDecimal, NutritionBasis, String>? = null
        showDialog { carbs, basis, name -> confirmed = Triple(carbs, basis, name) }

        compose.onNodeWithText("Save as verified").assertIsEnabled().performClick()

        val result = checkNotNull(confirmed)
        assertEquals(BigDecimal("48.2"), result.first)
        assertEquals(NutritionBasis.PER_100_G, result.second)
        assertEquals("Hagelslag puur", result.third)
    }
}
