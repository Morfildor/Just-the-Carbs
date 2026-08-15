package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.ui.home.HOME_SEARCH_FIELD_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.home.RecentEntry
import app.justthecarbs.ui.meal.MEAL_BAR_TAG
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Home (§7 product-development brief, redesigned empty state 2026-08-15).
 *
 * No dedicated HomeScreen coverage existed before this pass, despite Home having the same
 * conditional-state complexity as Product/Meal/Search (empty vs recents vs search, plus the meal
 * bar priority rule). Pins the rules that matter: the empty state communicates Scan → Portion →
 * Carbs without becoming a dashboard, the primary actions never disappear, and the meal bar/search
 * results still take priority over the decorative starter content.
 *
 * **Instrumented: needs a device or emulator.**
 */
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(barcode: String = "8712100849060", name: String = "Hagelslag puur") = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        lastPortion = BigDecimal("65"),
    )

    private fun mealItem() = MealItem.weightBased(
        productBarcode = "8712100849060",
        displayName = "Hagelslag puur",
        portionDescription = "65 g",
        resolvedAmount = BigDecimal("65"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("31.3"),
        addedAt = Instant.EPOCH,
    )

    private fun show(
        recents: List<RecentEntry> = emptyList(),
        mealItems: List<MealItem> = emptyList(),
        searchState: SearchUiState = SearchUiState(),
        onSearchSubmit: () -> Unit = {},
        density: Density? = null,
    ) {
        compose.setContent {
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    HomeScreen(
                        recents = recents,
                        settings = AppSettings(),
                        onScan = {},
                        onManualEntry = {},
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        mealItems = mealItems,
                        mealTotal = if (mealItems.isEmpty()) null else {
                            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("65"), NutritionBasis.PER_100_G)
                        },
                        searchState = searchState,
                        onSearchSubmit = onSearchSubmit,
                    )
                }
            }
            if (density != null) {
                CompositionLocalProvider(LocalDensity provides density, content = content)
            } else {
                content()
            }
        }
    }

    @Test
    fun emptyStateIsDisplayedWithNoRecents() {
        show(recents = emptyList())

        compose.onNodeWithText("Scan. Portion. Carbs.").assertIsDisplayed()
    }

    @Test
    fun scanBarcodeCtaRemainsVisibleInTheEmptyState() {
        show(recents = emptyList())

        compose.onNodeWithText("Scan barcode").assertIsDisplayed()
    }

    @Test
    fun manualEntryRemainsAvailableInTheEmptyState() {
        show(recents = emptyList())

        compose.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun theStarterVisualDoesNotAppearOnceRecentsExist() {
        show(recents = listOf(RecentEntry(product(), lastUnit = null)))

        compose.onNodeWithText("Scan. Portion. Carbs.").assertDoesNotExist()
        compose.onNodeWithText("Hagelslag puur").assertIsDisplayed()
        // The primary actions must still be present with recents shown.
        compose.onNodeWithText("Scan barcode").assertIsDisplayed()
    }

    @Test
    fun theMealBarStillTakesPriorityOverTheEmptyStarterContent() {
        show(recents = emptyList(), mealItems = listOf(mealItem()))

        compose.onNodeWithTag(MEAL_BAR_TAG).assertIsDisplayed()
        // The starter content still furnishes the remaining space beneath the meal bar.
        compose.onNodeWithText("Scan. Portion. Carbs.").assertIsDisplayed()
    }

    @Test
    fun typingInSearchReplacesTheEmptyStateWithSearchResults() {
        show(recents = emptyList(), searchState = SearchUiState(query = "hagel"))

        // A non-blank query takes over the space the empty state would otherwise occupy.
        compose.onNodeWithText("Scan. Portion. Carbs.").assertDoesNotExist()
    }

    @Test
    fun typingIntoTheSearchFieldIsPossibleFromTheEmptyState() {
        show(recents = emptyList())

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("hagel")
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsDisplayed()
    }

    /**
     * Typing must never itself trigger a network search (Open Food Facts' search endpoint is
     * rate-limited and not meant for as-you-type traffic) — only the explicit IME action does.
     */
    @Test
    fun typingAloneDoesNotSubmitASearch() {
        var submitCount = 0
        show(recents = emptyList(), onSearchSubmit = { submitCount++ })

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("hagel")

        assertEquals("typing must not submit a search", 0, submitCount)
    }

    @Test
    fun theImeSearchActionSubmitsExactlyOnce() {
        var submitCount = 0
        show(recents = emptyList(), searchState = SearchUiState(query = "hagel"), onSearchSubmit = { submitCount++ })

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performImeAction()

        assertEquals(1, submitCount)
    }

    /**
     * 1.8x is the scale the app is documented as supporting (see ProductScreenTest's equivalent
     * pin). The primary Scan/Enter-manually actions sit at the bottom of a Column with the
     * decorative starter content given `Modifier.weight(1f)` above them, so they must never be
     * pushed off-screen regardless of how much vertical space the starter content wants.
     */
    @Test
    fun largeFontDoesNotHidePrimaryActions() {
        show(
            recents = emptyList(),
            density = Density(density = 2.75f, fontScale = 1.8f),
        )

        compose.onNodeWithText("Scan barcode").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertIsDisplayed()
    }
}
