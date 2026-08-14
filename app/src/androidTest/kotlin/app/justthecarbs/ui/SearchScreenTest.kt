package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.ui.search.SEARCH_RESULTS_TAG
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * Free-text product search (spec §9).
 *
 * The rules worth pinning: a result is never auto-selected, a card never implies a carbohydrate
 * value it does not have, and a search failure never masquerades as "no such product".
 *
 * **Instrumented: needs a device or emulator.**
 */
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hit(
        barcode: String = "8710496979125",
        name: String = "Chocoladehagel puur",
        brand: String? = "De Ruijter",
        quantity: String? = "390 gram",
        carbs: String? = "67",
    ) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = quantity,
        carbsPer100 = carbs?.let(::BigDecimal),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private fun show(
        state: SearchUiState,
        onSelect: (ProductSearchHit) -> Unit = {},
        onScanLabel: () -> Unit = {},
        onEnterManually: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                SearchScreen(
                    state = state,
                    onQueryChanged = {},
                    onSelect = onSelect,
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                    onRetry = onRetry,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun anUntouchedSearchExplainsWhatToDo() {
        show(SearchUiState())

        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertIsDisplayed()
    }

    @Test
    fun aResultCardShowsWhatIsNeededToRecogniseThePackage() {
        show(SearchUiState(query = "hagelslag", hits = listOf(hit())))

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithText("De Ruijter · 390 gram").assertIsDisplayed()
        compose.onNodeWithText("67 g carbs / 100 g").assertIsDisplayed()
    }

    /**
     * The central rule of §15/§16. Even a single result requires a tap: "only one match" is not the
     * same as "the right match", and the app has no way to tell those apart.
     */
    @Test
    fun aSingleResultIsNotAutoSelected() {
        var selected: ProductSearchHit? = null
        show(SearchUiState(query = "hagelslag", hits = listOf(hit())), onSelect = { selected = it })

        assertNull("a lone result must not select itself", selected)

        compose.onNodeWithText("Chocoladehagel puur").performClick()
        assertEquals("8710496979125", selected!!.barcode)
    }

    /**
     * A missing value is said in words. A "0 g carbs" card would be a confident wrong answer about
     * food, which is the exact failure this app exists to avoid.
     */
    @Test
    fun aResultWithNoCarbohydrateValueSaysSoRatherThanShowingZero() {
        show(SearchUiState(query = "x", hits = listOf(hit(carbs = null))))

        compose.onNodeWithText("No carbohydrate value — check the package").assertIsDisplayed()
        compose.onNodeWithText("0 g carbs / 100 g").assertDoesNotExist()
    }

    @Test
    fun noMatchesOffersTheWaysToGetANumberWithoutTheDatabase() {
        show(SearchUiState(query = "zzzzz", noMatches = true))

        compose.onNodeWithText("No products found", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Scan nutrition label").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    /**
     * The search endpoint answers 503 while otherwise healthy often enough that this distinction is
     * load-bearing: telling someone their product does not exist because a host was busy would send
     * them off to transcribe a label the database already has.
     */
    @Test
    fun aServerFailureIsShownAsRetryableRatherThanAsNoSuchProduct() {
        show(SearchUiState(query = "hagelslag", error = LookupError.SERVER))

        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("No products found", substring = true).assertDoesNotExist()
    }

    @Test
    fun beingOfflineIsNamedRatherThanReportedAsAServerProblem() {
        show(SearchUiState(query = "hagelslag", error = LookupError.OFFLINE))

        compose.onNodeWithText("No internet connection").assertIsDisplayed()
    }

    @Test
    fun rateLimitingTellsTheUserToWaitRatherThanToRetypeTheirQuery() {
        show(SearchUiState(query = "hagelslag", error = LookupError.RATE_LIMITED))

        compose.onNodeWithText("Too many lookups", substring = true).assertIsDisplayed()
    }

    @Test
    fun resultsAreListed() {
        show(
            SearchUiState(
                query = "hagelslag",
                hits = listOf(hit(), hit(barcode = "8718906716223", name = "Puur Hagelslag", brand = "Albert Heijn")),
            ),
        )

        compose.onNodeWithTag(SEARCH_RESULTS_TAG).assertIsDisplayed()
        compose.onNodeWithText("Puur Hagelslag").assertIsDisplayed()
    }
}
