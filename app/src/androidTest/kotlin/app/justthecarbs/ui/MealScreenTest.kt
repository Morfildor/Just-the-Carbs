package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.meal.MEAL_ADD_AND_SCAN_TAG
import app.justthecarbs.ui.meal.MEAL_ADD_TAG
import app.justthecarbs.ui.meal.MEAL_BAR_TAG
import app.justthecarbs.ui.meal.MEAL_CLEAR_TAG
import app.justthecarbs.ui.meal.MEAL_TOTAL_TAG
import app.justthecarbs.ui.meal.MealScreen
import app.justthecarbs.ui.meal.MealUiState
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The temporary meal, driven the way a user drives it (development-pass brief §7-§11).
 *
 * These assert on what is on screen, never on ViewModel internals, and the totals are produced by
 * the same [MealTotal] and [CarbCalculator] production uses rather than by hard-coded numbers — so
 * a test passing means the arithmetic the user sees is right, not that a fixture matches itself.
 *
 * **Instrumented: needs a device or emulator.**
 */
class MealScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(name: String = "Hagelslag puur", carbs: String = "48.2") = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    private fun item(
        id: Long,
        name: String,
        description: String,
        carbs: String,
        exact: String,
    ) = MealItem.weightBased(
        id = id,
        productBarcode = "8712100849060",
        displayName = name,
        portionDescription = description,
        resolvedAmount = BigDecimal("50"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal(carbs),
        exactCarbs = BigDecimal(exact),
        addedAt = Instant.parse("2026-08-14T10:00:00Z"),
    )

    // ---- the meal screen itself ----------------------------------------------------------------

    private fun showMeal(initial: List<MealItem>) {
        compose.setContent {
            var items by remember { mutableStateOf(initial) }
            var confirming by remember { mutableStateOf(false) }

            JustTheCarbsTheme {
                MealScreen(
                    state = MealUiState(items = items, showClearConfirmation = confirming),
                    settings = AppSettings(),
                    onBack = {},
                    onRemoveItem = { removed -> items = items.filterNot { it.id == removed.id } },
                    onClear = { items = emptyList(); confirming = false },
                    onShowClearConfirmation = { confirming = it },
                )
            }
        }
    }

    @Test
    fun anEmptyMealSaysSoRatherThanShowingAZeroTotal() {
        showMeal(emptyList())

        compose.onNodeWithText("No items yet").assertIsDisplayed()
    }

    @Test
    fun eachItemShowsThePortionInTheWordsTheUserChose() {
        showMeal(listOf(item(1, "Bread", "2 slices", "48.2", "34.704")))

        compose.onNodeWithText("2 slices · 34.7 g").assertIsDisplayed()
    }

    /**
     * §9's central guarantee, asserted through the UI.
     *
     * 18.65 and 21.65 display as 18.7 and 21.7, which sum to 40.4; as whole grams they are 19 and
     * 22, which sum to 41. Only summing the unrounded values gives 40.3. A total of 40.4 or 41 on
     * this screen means the app rounded before it added.
     */
    @Test
    fun theTotalSumsUnroundedValuesRatherThanTheDisplayedOnes() {
        showMeal(
            listOf(
                item(1, "A", "50 g", "37.3", "18.65"),
                item(2, "B", "50 g", "43.3", "21.65"),
            ),
        )

        compose.onNodeWithTag(MEAL_TOTAL_TAG).assertIsDisplayed()
        compose.onNodeWithText("40.3 g").assertIsDisplayed()
    }

    @Test
    fun removingAnItemUpdatesTheTotal() {
        showMeal(
            listOf(
                item(1, "A", "50 g", "37.3", "18.65"),
                item(2, "B", "50 g", "43.3", "21.65"),
            ),
        )

        compose.onNodeWithContentDescription("Remove B").performClick()

        compose.onNodeWithText("18.7 g").assertIsDisplayed()
    }

    /**
     * Clearing is destructive and unrecoverable — there is no meal history to restore from — so it
     * asks first, and the confirmation says plainly that nothing is saved.
     */
    @Test
    fun clearingAsksFirstAndSaysTheItemsAreNotSavedAnywhere() {
        showMeal(listOf(item(1, "A", "50 g", "37.3", "18.65")))

        compose.onNodeWithTag(MEAL_CLEAR_TAG).performClick()

        compose.onNodeWithText("Clear this meal? The items are not saved anywhere.")
            .assertIsDisplayed()
    }

    @Test
    fun confirmingTheClearEmptiesTheMeal() {
        showMeal(listOf(item(1, "A", "50 g", "37.3", "18.65")))

        compose.onNodeWithTag(MEAL_CLEAR_TAG).performClick()
        // Addressed through the dialog rather than by index: once the dialog is open, "Clear meal"
        // matches its title, its confirm button AND the top-bar action behind it, and an index into
        // that set silently targets a different node the moment the wording or order changes.
        compose.onNode(hasAnyAncestor(isDialog()) and hasText("Clear meal") and hasClickAction())
            .performClick()

        compose.onNodeWithText("No items yet").assertIsDisplayed()
    }

    /** The clear action is absent on an empty meal, not merely disabled. */
    @Test
    fun anEmptyMealOffersNoClearAction() {
        showMeal(emptyList())

        compose.onNodeWithTag(MEAL_CLEAR_TAG).assertDoesNotExist()
    }

    // ---- adding from the calculator -------------------------------------------------------------

    /**
     * The calculator with a live meal, wired end to end: adding really does run the portion
     * description and the calculated result into a [MealItem], and the bar really does total them.
     *
     * Note there is deliberately no `performScrollTo()` before tapping the meal actions. They live
     * inside the pinned result surface, which is not a scrollable container (§3.2), so asking to
     * scroll to them fails with "no parent layout with a Scroll SemanticsAction". Asserting they
     * are clickable where they already are is the stronger claim anyway: it is exactly the property
     * the pinned layout exists to provide.
     */
    private fun showCalculatorWithMeal(onScanNext: () -> Unit = {}) {
        compose.setContent {
            var portion by remember { mutableStateOf("") }
            var items by remember { mutableStateOf(emptyList<MealItem>()) }
            val p = product()
            val parsed = PortionParser.parse(portion)
            val result = parsed?.let { CarbCalculator.calculate(p.carbsPer100, it, p.basis) }

            fun add(description: String) {
                val exact = result?.exact ?: return
                items = items + MealItem.weightBased(
                    id = items.size + 1L,
                    productBarcode = p.barcode,
                    displayName = p.name,
                    portionDescription = description,
                    resolvedAmount = parsed ?: BigDecimal.ZERO,
                    basis = p.basis,
                    carbsPer100 = p.carbsPer100,
                    exactCarbs = exact,
                    addedAt = Instant.parse("2026-08-14T10:00:00Z"),
                )
            }

            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = p,
                        portionText = portion,
                        result = result,
                        barcode = p.barcode,
                        mealItems = items,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = { portion = it },
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
                    onAddToMeal = { add(it) },
                    onAddToMealAndScanNext = { add(it); onScanNext() },
                )
            }
        }
    }

    /**
     * The bar is absent, not zeroed, before anything is added — an always-present "0 items" strip
     * would make a calculator look like a tracker with a permanent dashboard (§10, §28).
     */
    @Test
    fun theMealBarIsAbsentUntilSomethingIsAdded() {
        showCalculatorWithMeal()

        compose.onNodeWithTag(MEAL_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun addingAPortionShowsItInTheRunningTotalBar() {
        showCalculatorWithMeal()

        compose.onNode(hasSetTextAction()).performTextInput("50")
        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()

        // 48.2 g/100 g × 50 g = 24.1 g, via the production calculator.
        compose.onNodeWithTag(MEAL_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithText("Meal · 1 item · 24.1 g").assertIsDisplayed()
    }

    @Test
    fun addingTwoPortionsTotalsThemInTheBar() {
        showCalculatorWithMeal()

        compose.onNode(hasSetTextAction()).performTextInput("50")
        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()

        compose.onNodeWithText("Meal · 2 items · 48.2 g").assertIsDisplayed()
    }

    /** §11: one tap both records the item and moves on, or the loop is not worth using. */
    @Test
    fun addAndScanNextRecordsTheItemAndLeavesForTheScanner() {
        var scannedNext = false
        showCalculatorWithMeal(onScanNext = { scannedNext = true })

        compose.onNode(hasSetTextAction()).performTextInput("50")
        compose.onNodeWithTag(MEAL_ADD_AND_SCAN_TAG).performClick()

        compose.onNodeWithText("Meal · 1 item · 24.1 g").assertIsDisplayed()
        assertEquals("Add & scan next must also open the scanner", true, scannedNext)
    }

    /** No result, nothing to add — an Add button beside an empty result could only add a zero. */
    @Test
    fun theMealActionsAreAbsentUntilAPortionProducesAResult() {
        showCalculatorWithMeal()

        compose.onNodeWithTag(MEAL_ADD_TAG).assertDoesNotExist()
    }

    /**
     * Adding to the meal must not cost the user the controls they are still using.
     *
     * A regression from a defect no assertion caught and only running the app revealed. The bar was
     * first placed in the fixed-height header, so its ~56 dp came straight out of the scrolling
     * portion zone: "How much are you eating?" ended up clipped *behind* the bar and
     * "+ Add portion unit" was pushed off the bottom of the screen. Moving it into the scrolling
     * zone then hid the bar itself whenever the keyboard was open — a running total you cannot see
     * is not a running total. It now shares the pinned result surface, which is the only region
     * guaranteed to be visible.
     *
     * The assertions are on *displayed*, not on existence: a clipped node is still in the tree, so
     * `assertExists` would have passed in every one of those broken arrangements. The portion field
     * and the result are what must survive, since those are what the user is working with; the
     * question label above them is ordinary scrollable content and may legitimately scroll away.
     */
    @Test
    fun addingToTheMealKeepsThePortionFieldAndResultVisible() {
        showCalculatorWithMeal()

        compose.onNode(hasSetTextAction()).performTextInput("50")
        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()

        compose.onNodeWithTag(MEAL_BAR_TAG).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("24.1 g").assertIsDisplayed()
        compose.onNodeWithTag(MEAL_ADD_TAG).assertIsDisplayed()

        // The real defect was geometric, not existential: the field stayed "displayed" while the
        // result panel covered its lower half, so only comparing edges catches it.
        //
        // Measured against the panel rather than against a before/after snapshot of this same
        // screen: typing opens the IME and tapping dismisses it, so a before/after comparison here
        // is really a comparison of two different layouts and reports ~268 dp of "movement" that is
        // entirely the keyboard. The panel's top edge versus the field's bottom edge is a fact
        // about one layout at one moment, and it is exactly the relationship that broke.
        val fieldBottom = compose.onNode(hasSetTextAction())
            .fetchSemanticsNode().boundsInRoot.bottom
        val panelTop = compose.onNodeWithText("CARBOHYDRATES")
            .fetchSemanticsNode().boundsInRoot.top

        assert(panelTop >= fieldBottom) {
            with(compose.density) {
                "the result panel overlaps the portion field by ${(fieldBottom - panelTop).toDp()}"
            }
        }
    }
}
