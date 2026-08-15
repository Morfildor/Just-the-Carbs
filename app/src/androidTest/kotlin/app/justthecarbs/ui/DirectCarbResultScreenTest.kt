package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.DirectCarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.meal.MEAL_ADD_TAG
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * A direct-carb portion must produce a real, usable result on screen (correction pass §1).
 *
 * This is the blocker these tests exist for. [ProductUiState.directCarbResult] was computed
 * correctly and the equation rendered, but the result panel read only `state.result` — which is null
 * on this path, because a direct-carb portion has no per-100 basis and therefore no
 * [app.justthecarbs.domain.CarbResult]. The user saw "4 slices × 14.2 g carbs" above a panel still
 * saying *Enter a portion*, with neither *Copy* nor *Add to meal* available.
 *
 * The suite asserts the whole visible outcome rather than just the number: a result the user cannot
 * copy or add to a meal is not a finished result, and those actions live inside the same `if` the
 * bug was in.
 *
 * **Instrumented: needs a device or emulator.**
 */
class DirectCarbResultScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-08-15T10:00:00Z")
    private val barcode = "5449000000996"

    private fun product() = Product(
        barcode = barcode,
        name = "Sliced Bread",
        carbsPer100 = BigDecimal("41.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /** "1 slice = 14.2 g carbs" — a label that printed per-serving carbs but no weight. */
    private fun directCarbSlice(carbsPerUnit: String = "14.2") = PortionUnit(
        id = 1,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.DirectCarbs(BigDecimal(carbsPerUnit)),
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = PortionConversion.DirectCarbs(BigDecimal(carbsPerUnit)),
        latestRemoteConversion = PortionConversion.DirectCarbs(BigDecimal(carbsPerUnit)),
        rawRemoteServingText = "1 slice",
        createdAt = now,
        updatedAt = now,
    )

    /**
     * Renders the calculator mid-direct-carb-calculation.
     *
     * The carbohydrate figure comes from the real [DirectCarbCalculator], not a literal, so the test
     * cannot pass while the domain calculation is wrong.
     */
    private fun showDirectCarbResult(count: String = "4", carbsPerUnit: String = "14.2") {
        val unit = directCarbSlice(carbsPerUnit)
        val conversion = unit.conversion as PortionConversion.DirectCarbs
        val exact = DirectCarbCalculator.exactCarbs(BigDecimal(count), conversion.carbsPerUnit)

        compose.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product(),
                        // Deliberately empty: no gram figure exists on this path and none is
                        // invented. The result must appear without one.
                        portionText = "",
                        countText = count,
                        inputMode = InputMode.PORTION_UNIT,
                        selectedPortionUnitId = unit.id,
                        portionUnits = listOf(unit),
                        result = null,
                        directCarbResult = exact,
                        barcode = barcode,
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
                )
            }
        }
    }

    @Test
    fun fourDirectCarbSlicesShowTheCalculatedTotal() {
        // 4 × 14.2 = 56.8. The exact figure the workflow this pass exists to fix must produce.
        showDirectCarbResult(count = "4", carbsPerUnit = "14.2")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertTextEquals("56.8 g")
    }

    @Test
    fun aDirectCarbResultIsNotLeftPending() {
        // The defect's signature: the panel's placeholder still showing while a valid result exists.
        showDirectCarbResult()

        compose.onNodeWithText("Enter a portion").assertDoesNotExist()
    }

    @Test
    fun aDirectCarbResultOffersCopy() {
        // Addressed by content description, not by text: *Copy* is an icon button whose only
        // accessible label is its contentDescription — which is also what a TalkBack user hears.
        showDirectCarbResult()

        compose.onNodeWithContentDescription("Copy value").assertIsDisplayed()
    }

    @Test
    fun aDirectCarbResultOffersAddToMeal() {
        showDirectCarbResult()

        compose.onNodeWithTag(MEAL_ADD_TAG).assertIsDisplayed()
    }

    @Test
    fun theEquationShowsCarbsPerUnitAndNeverAGramWeight() {
        // The other half of the guarantee: the app must show where the number came from without
        // inventing the weight it never knew.
        showDirectCarbResult(count = "4", carbsPerUnit = "14.2")

        compose.onNodeWithText("4 slices × 14.2 g carbs", substring = true).assertIsDisplayed()
    }

    @Test
    fun aWholeNumberOfCarbsStillRenders() {
        // 2 × 15 = 30. Guards the formatting path for a value with no fractional part.
        showDirectCarbResult(count = "2", carbsPerUnit = "15")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertTextEquals("30.0 g")
    }
}
