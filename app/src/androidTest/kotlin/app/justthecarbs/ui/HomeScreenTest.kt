package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.ui.home.HOME_SCAN_BARCODE_TAG
import app.justthecarbs.ui.home.HOME_SCAN_LABEL_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_FIELD_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_PENDING_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_RATE_LIMITED_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_REFRESH_ERROR_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_RESULTS_TAG
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
 * Home (§7 product-development brief; entry-point pass 2026-08-15).
 *
 * The rule this class exists to pin: **all three ways into the app are present at all times.** The
 * defect it was written against was structural rather than cosmetic — "Scan nutrition label" lived
 * inside the empty state, so it vanished permanently the moment the user's first product landed in
 * Recents, and the app's third entry point became unreachable from Home for every returning user.
 *
 * Also pins the explicit-search contract (typing must never reach Open Food Facts) and the meal
 * bar's priority over starter content.
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
        onScan: () -> Unit = {},
        onScanLabel: () -> Unit = {},
        onManualEntry: () -> Unit = {},
        onSearchSubmit: () -> Unit = {},
        density: Density? = null,
    ) {
        compose.setContent {
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    HomeScreen(
                        recents = recents,
                        settings = AppSettings(),
                        onScan = onScan,
                        onManualEntry = onManualEntry,
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onScanLabel = onScanLabel,
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

    // ---- The three core entry points -------------------------------------------------------

    @Test
    fun allThreeEntryPointsAreVisibleInTheEmptyState() {
        show(recents = emptyList())

        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsDisplayed()
    }

    /**
     * The regression this pass exists to prevent. Before it, the nutrition-label action was rendered
     * only by the empty state, so a single recent product removed it from Home entirely.
     */
    @Test
    fun allThreeEntryPointsSurviveTheArrivalOfRecentProducts() {
        show(recents = listOf(RecentEntry(product(), lastUnit = null)))

        compose.onNodeWithText("Hagelslag puur").assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsDisplayed()
    }

    /** Core actions precede history: the app's job must not sit below a list of past products. */
    @Test
    fun theCoreActionsRenderAboveTheRecentList() {
        show(recents = listOf(RecentEntry(product(), lastUnit = null)))

        val barcodeTop = compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).fetchSemanticsNode()
            .positionInRoot.y
        val labelTop = compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).fetchSemanticsNode()
            .positionInRoot.y
        val recentTop = compose.onNodeWithText("Hagelslag puur").fetchSemanticsNode()
            .positionInRoot.y

        assert(barcodeTop < labelTop) { "barcode card must precede the label card" }
        assert(labelTop < recentTop) { "core actions must precede recent products" }
    }

    @Test
    fun manualEntryRemainsAvailableButBelowTheCoreActions() {
        show(recents = emptyList())

        compose.onNodeWithText("Enter manually").performScrollTo().assertIsDisplayed()

        val labelTop = compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).fetchSemanticsNode()
            .positionInRoot.y
        val manualTop = compose.onNodeWithText("Enter manually").fetchSemanticsNode()
            .positionInRoot.y
        assert(manualTop > labelTop) { "manual entry must stay below the three core entry points" }
    }

    // ---- Navigation wiring ------------------------------------------------------------------

    @Test
    fun theBarcodeCardTriggersTheScannerExactlyOnce() {
        var scans = 0
        show(onScan = { scans++ })

        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).performClick()

        assertEquals(1, scans)
    }

    @Test
    fun theNutritionLabelCardTriggersTheLabelScannerExactlyOnce() {
        var labelScans = 0
        show(onScanLabel = { labelScans++ })

        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).performClick()

        assertEquals(1, labelScans)
    }

    /**
     * The label scanner must be reachable with recents present — the same click, on the state a
     * returning user actually sees.
     */
    @Test
    fun theNutritionLabelCardStillNavigatesWhenRecentsExist() {
        var labelScans = 0
        show(recents = listOf(RecentEntry(product(), lastUnit = null)), onScanLabel = { labelScans++ })

        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).performScrollTo().performClick()

        assertEquals(1, labelScans)
    }

    @Test
    fun manualEntryTriggersItsRouteExactlyOnce() {
        var manual = 0
        show(onManualEntry = { manual++ })

        compose.onNodeWithText("Enter manually").performScrollTo().performClick()

        assertEquals(1, manual)
    }

    // ---- Explicit search --------------------------------------------------------------------

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

    @Test
    fun aNonBlankQueryReplacesTheHomeBodyWithResults() {
        show(recents = emptyList(), searchState = SearchUiState(query = "hagel"))

        // Search owns the whole middle region, so the starter content and the action cards yield.
        compose.onNodeWithText("Scan. Portion. Carbs.").assertDoesNotExist()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertDoesNotExist()
    }

    // ---- Home's inline search shares SearchScreen's refresh rules ----------------------------

    private fun searchHit() = ProductSearchHit(
        barcode = "8710496979125",
        name = "Chocoladehagel puur",
        brand = "De Ruijter",
        packageQuantity = "390 gram",
        carbsPer100 = java.math.BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    /**
     * Home renders the same three cases as [app.justthecarbs.ui.search.SearchScreen] and must not
     * drift from it. Home is the more exposed of the two — it is where someone searches on purpose
     * rather than after a failure — and before this pass it had no refresh indication at all: its
     * only loading state was the centred spinner for an empty list, so a refresh over results was
     * completely silent.
     */
    @Test
    fun refreshingHomesResultsKeepsThemRatherThanShowingTheCentredSpinner() {
        show(
            recents = emptyList(),
            searchState = SearchUiState(
                query = "hagelslag puur",
                hits = listOf(searchHit()),
                searching = true,
            ),
        )

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(HOME_SEARCH_RESULTS_TAG).assertIsDisplayed()
    }

    @Test
    fun aFailedRefreshOnHomeKeepsTheResultsAndShowsOnlyAnInlineNotice() {
        show(
            recents = emptyList(),
            searchState = SearchUiState(
                query = "hagelslag puur",
                hits = listOf(searchHit()),
                error = LookupError.SERVER,
                refreshFailed = true,
            ),
        )

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(HOME_SEARCH_REFRESH_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithText("Couldn't refresh results").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertDoesNotExist()
    }

    /** Home follows the same pacing rules — a governor wait is never an outage there either. */
    @Test
    fun waitingForTheRequestBudgetOnHomeIsNotShownAsAnOutage() {
        show(
            recents = emptyList(),
            searchState = SearchUiState(
                query = "chocolate",
                searching = true,
                awaitingRemotePermit = true,
            ),
        )

        compose.onNodeWithTag(HOME_SEARCH_PENDING_TAG).assertIsDisplayed()
        compose.onNodeWithText("The product database is unavailable").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun aRateLimitOnHomeOffersNoRetryAndKeepsTheResults() {
        show(
            recents = emptyList(),
            searchState = SearchUiState(
                query = "chocolate",
                hits = listOf(searchHit()),
                searching = true,
                awaitingRemotePermit = true,
                rateLimited = true,
            ),
        )

        compose.onNodeWithTag(HOME_SEARCH_RATE_LIMITED_TAG).assertIsDisplayed()
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun aFirstSearchFailureOnHomeStillShowsTheFullErrorState() {
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "hagelslag", error = LookupError.SERVER),
        )

        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithTag(HOME_SEARCH_REFRESH_ERROR_TAG).assertDoesNotExist()
    }

    // ---- Starter content and the meal bar ---------------------------------------------------

    @Test
    fun theStarterHeroIsShownOnlyWhileThereAreNoRecents() {
        show(recents = emptyList())
        compose.onNodeWithText("Scan. Portion. Carbs.").assertIsDisplayed()
    }

    @Test
    fun theStarterHeroDisappearsOnceRecentsExist() {
        show(recents = listOf(RecentEntry(product(), lastUnit = null)))
        compose.onNodeWithText("Scan. Portion. Carbs.").assertDoesNotExist()
    }

    @Test
    fun theMealBarStillTakesPriorityOverTheEmptyStarterContent() {
        show(recents = emptyList(), mealItems = listOf(mealItem()))

        compose.onNodeWithTag(MEAL_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertIsDisplayed()
    }

    // ---- Accessibility ----------------------------------------------------------------------

    /**
     * Each action card is one button node carrying one description, rather than an icon, a title and
     * a subtitle announced as three separate stops inside a single tappable surface.
     */
    @Test
    fun theActionCardsExposeButtonSemanticsWithASingleDescription() {
        show(recents = emptyList())

        compose.onNodeWithContentDescription("Scan barcode. Fastest way to find a packaged product")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithContentDescription("Scan nutrition label. Read carbs straight from the package")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // ---- Scaling ----------------------------------------------------------------------------

    /**
     * 1.8x is the scale the app is documented as supporting (see ProductScreenTest's equivalent
     * pin). Every entry point must survive it — the cards scroll, so they are reached with
     * performScrollTo rather than asserted to be on screen unaided.
     */
    @Test
    fun largeFontKeepsEveryEntryPointReachable() {
        show(
            recents = emptyList(),
            density = Density(density = 2.75f, fontScale = 1.8f),
        )

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enter manually").performScrollTo().assertIsDisplayed()
    }
}
