package app.justthecarbs.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.ui.search.SEARCH_REFRESH_ERROR_TAG
import app.justthecarbs.ui.search.SEARCH_REFRESH_PROGRESS_TAG
import app.justthecarbs.ui.search.SEARCH_RESULTS_TAG
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * How a locally saved product looks on the search screen.
 *
 * The claim is a negative one and it is the whole UX requirement: **there is nothing to see**. A
 * saved product renders through the same row, in the same list, with no badge, no section header
 * and no separate style, so the user perceives "search became faster" rather than "search now has
 * two databases". These tests therefore mostly assert that things are *absent*.
 *
 * The screen takes a plain [SearchUiState] and cannot tell where a hit came from — which is the
 * structural version of the same guarantee. What is exercised here is the states the merge can
 * produce.
 *
 * **Instrumented: needs a device or emulator.**
 */
class SavedProductSearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hit(
        barcode: String = "8710496979125",
        name: String = "Chocoladehagel puur",
        brand: String? = "De Ruijter",
        quantity: String? = null,
    ) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = quantity,
        carbsPer100 = BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private fun show(state: SearchUiState, onSelect: (ProductSearchHit) -> Unit = {}) {
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
    fun aSavedProductRendersThroughTheOrdinaryResultRow() {
        // No printed package quantity — the store holds a parsed number, not the text on the pack —
        // and the row must still show the name, the brand and the figure.
        show(SearchUiState(query = "hagelslag", hits = listOf(hit())))

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithText("De Ruijter").assertIsDisplayed()
        compose.onNodeWithText("67 g", substring = true).assertIsDisplayed()
    }

    @Test
    fun thereIsNoLocalOrOnlineBadgeSectionOrHeader() {
        show(
            SearchUiState(
                query = "hagelslag",
                hits = listOf(hit(), hit(barcode = "r1", name = "Hagelslag melk", brand = null)),
            ),
        )

        // The vocabulary a sectioned list would have to use. None of it may appear.
        for (word in listOf("Saved", "Local", "Online", "On this device", "From the database")) {
            compose.onNodeWithText(word, substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test
    fun localAndRemoteResultsAreOneUndifferentiatedList() {
        show(
            SearchUiState(
                query = "hagelslag",
                hits = listOf(hit(), hit(barcode = "r1", name = "Hagelslag melk", brand = null)),
            ),
        )

        // One list node, both rows inside it — not two lists, and no divider row between them.
        compose.onAllNodesWithTag(SEARCH_RESULTS_TAG).assertCountEquals(1)
        compose.onNodeWithTag(SEARCH_RESULTS_TAG).onChildren().assertCountEquals(2)
    }

    @Test
    fun progressCoexistsWithLocalHitsWhileTheRemoteSearchRuns() {
        // The state the merge produces between a saved answer and the network's: results on screen,
        // hairline still running. Blanking the list here is what the whole design avoids.
        show(SearchUiState(query = "hagelslag", hits = listOf(hit()), searching = true, narrowedLocally = true))

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_REFRESH_PROGRESS_TAG).assertExists()
    }

    @Test
    fun anOfflineFailureDoesNotReplaceTheLocalHits() {
        // The offline case, which is the point of the feature: the saved products stay, and the
        // failure is a compact line beside them rather than a full-screen recovery panel.
        show(
            SearchUiState(
                query = "hagelslag",
                hits = listOf(hit()),
                error = LookupError.OFFLINE,
                refreshFailed = true,
            ),
        )

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_REFRESH_ERROR_TAG).assertIsDisplayed()
        // The recovery panel's actions belong to a dead end, and this is not one.
        compose.onNodeWithText("Enter manually").assertDoesNotExist()
    }

    @Test
    fun selectingASavedProductUsesTheOrdinaryProductNavigation() {
        // Including a synthetic `local:` identity, which is a valid navigation key: the screen hands
        // back the hit and the caller runs the same `Routes.product(barcode)` it runs for any other.
        var selected: ProductSearchHit? = null
        val saved = hit(barcode = "local:11112222", name = "Zelfgemaakte muesli", brand = null)
        show(SearchUiState(query = "muesli", hits = listOf(saved)), onSelect = { selected = it })

        compose.onNodeWithText("Zelfgemaakte muesli").performClick()

        assertEquals("local:11112222", selected?.barcode)
    }
}
