package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.StaleMeal
import app.justthecarbs.ui.meal.MEAL_EDIT_FIELD_TAG
import app.justthecarbs.ui.meal.MEAL_EDIT_SAVE_TAG
import app.justthecarbs.ui.meal.MEAL_ITEM_ROW_TAG
import app.justthecarbs.ui.meal.MEAL_STALE_ADD_TO_IT_TAG
import app.justthecarbs.ui.meal.MEAL_STALE_DIALOG_TAG
import app.justthecarbs.ui.meal.MEAL_STALE_START_NEW_TAG
import app.justthecarbs.ui.meal.MealScreen
import app.justthecarbs.ui.meal.MealUiState
import app.justthecarbs.ui.meal.StaleMealDialog
import app.justthecarbs.ui.search.SEARCH_ONLINE_ERROR_TAG
import app.justthecarbs.ui.search.SEARCH_ONLINE_SECTION_TAG
import app.justthecarbs.ui.search.SEARCH_SAVED_SECTION_TAG
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * The three surfaces of the 2026-09-23 meal/search patch, rendered: changing a meal line's portion,
 * the stale-meal question, and stored products listed above online results.
 *
 * **Instrumented: needs a device or emulator.**
 */
class MealSessionAndSavedSearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val bread = MealItem.weightBased(
        id = 1,
        productBarcode = "111",
        displayName = "Volkoren brood",
        portionDescription = "350 g",
        resolvedAmount = BigDecimal("350"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("40"),
        exactCarbs = BigDecimal("140.00"),
        addedAt = Instant.parse("2026-09-23T08:00:00Z"),
    )

    private val crackers = MealItem.directCarbs(
        id = 2,
        productBarcode = "222",
        displayName = "Crackers",
        portionDescription = "2 crackers",
        count = BigDecimal("2"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("28.4"),
        addedAt = Instant.parse("2026-09-23T08:01:00Z"),
    )

    /** Mirrors MealViewModel's edit state; records what Save hands over. */
    private fun showMeal(items: List<MealItem>, saved: MutableList<Pair<BigDecimal, String>>) {
        compose.setContent {
            var editing by remember { mutableStateOf<MealItem?>(null) }
            JustTheCarbsTheme {
                MealScreen(
                    state = MealUiState(items = items, editing = editing),
                    settings = AppSettings(),
                    onBack = {},
                    onRemoveItem = {},
                    onClear = {},
                    onShowClearConfirmation = {},
                    onEditItem = { editing = it },
                    onSaveEdit = { amount, description ->
                        saved += amount to description
                        editing = null
                    },
                    onCancelEdit = { editing = null },
                )
            }
        }
    }

    @Test
    fun tappingAMealLineOpensItsPortionAndSaveHandsOverTheCorrection() {
        val saved = mutableListOf<Pair<BigDecimal, String>>()
        showMeal(listOf(bread), saved)

        compose.onNodeWithTag(MEAL_ITEM_ROW_TAG).performClick()
        compose.onNodeWithTag(MEAL_EDIT_FIELD_TAG).assertTextContains("350")
        compose.onNodeWithTag(MEAL_EDIT_FIELD_TAG).performTextReplacement("35")

        // The preview is the recalculation Save will write, from the line's own per-100 figure.
        val expected = CarbCalculator.calculate(BigDecimal("40"), BigDecimal("35"), NutritionBasis.PER_100_G).exact
        compose.onNodeWithText(context.getString(R.string.meal_edit_result, ResultFormatter.decimal(expected)))
            .assertIsDisplayed()
        compose.onNodeWithTag(MEAL_EDIT_SAVE_TAG).performClick()

        assertEquals(listOf(BigDecimal("35") to "35 g"), saved)
    }

    @Test
    fun aCountedLineIsChangedByCountAndSaysWhatItIsMadeOf() {
        val saved = mutableListOf<Pair<BigDecimal, String>>()
        showMeal(listOf(crackers), saved)

        compose.onNodeWithTag(MEAL_ITEM_ROW_TAG).performClick()
        compose.onNodeWithTag(MEAL_EDIT_FIELD_TAG).performTextReplacement("3")
        compose.onNodeWithTag(MEAL_EDIT_SAVE_TAG).performClick()

        val description = context.getString(R.string.meal_edit_count_description, "3", ResultFormatter.quantity(BigDecimal("14.2")))
        assertEquals(listOf(BigDecimal("3") to description), saved)
    }

    @Test
    fun zeroCannotBeSavedAsAPortion() {
        showMeal(listOf(bread), mutableListOf())

        compose.onNodeWithTag(MEAL_ITEM_ROW_TAG).performClick()
        compose.onNodeWithTag(MEAL_EDIT_FIELD_TAG).performTextReplacement("0")

        compose.onNodeWithTag(MEAL_EDIT_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun theRemoveButtonStillRemovesRatherThanOpeningTheEditor() {
        var removed: MealItem? = null
        compose.setContent {
            JustTheCarbsTheme {
                MealScreen(
                    state = MealUiState(items = listOf(bread)),
                    settings = AppSettings(),
                    onBack = {},
                    onRemoveItem = { removed = it },
                    onClear = {},
                    onShowClearConfirmation = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.meal_remove_item, bread.displayName)).performClick()

        assertEquals(bread, removed)
        compose.onNodeWithTag(MEAL_EDIT_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun theStaleMealQuestionStatesTheMealAndOffersBothAnswers() {
        var answer: Boolean? = null
        compose.setContent {
            JustTheCarbsTheme {
                StaleMealDialog(
                    staleMeal = StaleMeal(itemCount = 3, exactCarbs = BigDecimal("85.04"), sinceLastAdded = Duration.ofMinutes(7 * 60 + 40)),
                    onStartNewMeal = { answer = true },
                    onAddToMeal = { answer = false },
                    onDismiss = {},
                )
            }
        }

        val items = context.resources.getQuantityString(R.plurals.meal_item_count, 3, 3)
        val hours = context.resources.getQuantityString(R.plurals.meal_stale_hours, 7, 7)
        compose.onNodeWithTag(MEAL_STALE_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.meal_stale_body, items, ResultFormatter.decimal(BigDecimal("85.04")), hours))
            .assertIsDisplayed()

        compose.onNodeWithTag(MEAL_STALE_ADD_TO_IT_TAG).performClick()
        assertEquals(false, answer)
        compose.onNodeWithTag(MEAL_STALE_START_NEW_TAG).performClick()
        assertEquals(true, answer)
    }

    // ---- stored products in search -----------------------------------------------------------

    private fun hit(barcode: String, name: String) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = null,
        packageQuantity = null,
        carbsPer100 = BigDecimal("40"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private fun showSearch(state: SearchUiState, onSelect: (ProductSearchHit) -> Unit = {}) {
        compose.setContent {
            JustTheCarbsTheme {
                SearchScreen(
                    state = state,
                    onQueryChanged = {},
                    onSearchSubmit = {},
                    onSelect = onSelect,
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun storedProductsAreListedFirstUnderTheirOwnLabelAndNotRepeated() {
        val mine = hit("local:1", "Homemade brood")
        val online = hit("555", "Brood tarwe")
        var selected: ProductSearchHit? = null
        showSearch(
            SearchUiState(query = "brood", savedHits = listOf(mine), hits = listOf(mine, online)),
            onSelect = { selected = it },
        )

        compose.onNodeWithTag(SEARCH_SAVED_SECTION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_ONLINE_SECTION_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("Homemade brood").onFirst().assertIsDisplayed()
        assertEquals("listed once", 1, compose.onAllNodesWithText("Homemade brood").fetchSemanticsNodes().size)
        compose.onNodeWithText("Homemade brood").performClick()
        assertEquals(mine, selected)
    }

    @Test
    fun offlineWithStoredMatchesKeepsTheListAndSaysWhatIsMissing() {
        showSearch(
            SearchUiState(
                query = "homemade",
                savedHits = listOf(hit("local:1", "Homemade brood")),
                error = LookupError.OFFLINE,
            ),
        )

        compose.onNodeWithText("Homemade brood").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_ONLINE_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_offline_title)).assertDoesNotExist()
    }

    @Test
    fun withNoStoredMatchTheOnlineListIsUnlabelledAsBefore() {
        showSearch(SearchUiState(query = "brood", hits = listOf(hit("555", "Brood tarwe"))))

        compose.onNodeWithText("Brood tarwe").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_SAVED_SECTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SEARCH_ONLINE_SECTION_TAG).assertDoesNotExist()
    }
}
