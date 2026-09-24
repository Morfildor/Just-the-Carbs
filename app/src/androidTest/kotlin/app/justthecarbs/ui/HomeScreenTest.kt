package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import app.justthecarbs.ui.home.HOME_MANUAL_TAG
import app.justthecarbs.ui.home.HOME_BODY_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_PENDING_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_PROGRESS_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_SEARCHING_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_RATE_LIMITED_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_REFRESH_ERROR_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_RESULTS_TAG
import app.justthecarbs.R
import app.justthecarbs.domain.RecentUseSnapshot
import app.justthecarbs.ui.home.ForgottenRecent
import app.justthecarbs.ui.home.HOME_RECENT_FORGET_TAG
import app.justthecarbs.ui.home.HOME_SNACKBAR_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.home.RecentEntry
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.ui.meal.MEAL_BAR_TAG
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        onSearchQueryChanged: (String) -> Unit = {},
        onSearchSelect: (ProductSearchHit) -> Unit = {},
        onOpenProduct: (String) -> Unit = {},
        onForgetRecent: (Product) -> Unit = {},
        forgotten: ForgottenRecent? = null,
        onUndoForgetRecent: () -> Unit = {},
        density: Density? = null,
        /**
         * Reports the live IME inset in px on every composition.
         *
         * The keyboard-vs-Back cases are about a window inset the system owns, and there is no
         * Compose assertion for it — so the screen's own view of it is sampled from inside the
         * composition, which is exactly the value the shipped `BackHandler` is gated on.
         */
        probeImeBottom: (Int) -> Unit = {},
    ) {
        compose.setContent {
            probeImeBottom(WindowInsets.ime.getBottom(LocalDensity.current))
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    HomeScreen(
                        recents = recents,
                        settings = AppSettings(),
                        onScan = onScan,
                        onManualEntry = onManualEntry,
                        onOpenProduct = onOpenProduct,
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onForgetRecent = onForgetRecent,
                        forgotten = forgotten,
                        onUndoForgetRecent = onUndoForgetRecent,
                        onScanLabel = onScanLabel,
                        mealItems = mealItems,
                        mealTotal = if (mealItems.isEmpty()) null else {
                            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("65"), NutritionBasis.PER_100_G)
                        },
                        searchState = searchState,
                        onSearchSubmit = onSearchSubmit,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onSearchSelect = onSearchSelect,
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

    /**
     * Waits for the soft keyboard to finish appearing or disappearing.
     *
     * The IME animates, and `waitForIdle` does not wait for a window the app does not own, so a
     * bare read straight after `pressBack` catches the inset mid-slide. Polls the sampled value
     * instead of sleeping a fixed time, so a slow emulator lengthens the wait rather than failing.
     */
    private fun awaitIme(present: Boolean, timeoutMs: Long = 5_000, read: () -> Int) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if ((read() > 0) == present) return
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            Thread.sleep(50)
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
        compose.onNodeWithText(stringOf(R.string.home_empty_headline)).assertDoesNotExist()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertDoesNotExist()
    }

    /**
     * Home is the app's start destination, so it sits at the bottom of the back stack — the system
     * back button has nothing to pop and would otherwise close the app while the user is mid-search.
     * With the keyboard already down, back must clear the query instead, the same action the
     * field's own X button performs, rather than exiting.
     *
     * **Revised 2026-09-22.** This case previously drove back with no regard for keyboard state and
     * asserted the query was cleared — which is exactly what the defect did, so the test protected
     * it. It now states the narrower rule it was always meant to: clearing is the *second* step,
     * reached once the IME is gone. `theFirstBackDismissesTheKeyboardAndKeepsTheSearch` below owns
     * the first step.
     */
    @Test
    fun systemBackClearsAnActiveSearchOnceTheKeyboardIsAlreadyDown() {
        var cleared: String? = null
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "haribo"),
            onSearchQueryChanged = { cleared = it },
        )
        // Nothing has focused the field, so no IME is up: this is the keyboard-down state.
        compose.waitForIdle()

        androidx.test.espresso.Espresso.pressBack()

        assertEquals("", cleared)
    }

    /**
     * The first back after typing puts the keyboard away and leaves the search completely alone.
     *
     * **This is the defect the 2026-09-22 pass fixed, and it was a data-loss bug, not a nicety.**
     * The handler was keyed on `query.isNotBlank()` and cleared the query outright, so the
     * universal Android gesture for "dismiss the keyboard" destroyed the query, the results and
     * the in-flight request together. The only recovery was retyping, which costs a fresh Open
     * Food Facts request against a 10/min budget.
     *
     * Asserted on the IME inset itself plus `onSearchQueryChanged` never firing. Those two together
     * are the whole contract: the keyboard is gone, and the screen never asked for the query (which
     * the ViewModel owns) to be changed.
     *
     * **The keyboard is dismissed by the platform, not by this screen, and the test is written that
     * way on purpose.** Measured on the emulator: Back with no handler registered moves the inset
     * 883px -> 0, while an IME-gated `BackHandler` calling `clearFocus()` fires and leaves the
     * inset at 883 — an app handler here swallows the dismissal instead of performing it. So what
     * Home must do while the keyboard is up is nothing, and this case fails if a future change
     * adds an interception.
     */
    @Test
    fun theFirstBackDismissesTheKeyboardAndKeepsTheSearch() {
        val changes = mutableListOf<String>()
        var imeBottom = -1
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "haribo", hits = listOf(searchHit())),
            onSearchQueryChanged = { changes += it },
            probeImeBottom = { imeBottom = it },
        )

        // Focus the field, which is what brings the IME up.
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()
        awaitIme(present = true) { imeBottom }
        assertTrue("precondition: the keyboard must be up", imeBottom > 0)

        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        awaitIme(present = false) { imeBottom }

        assertEquals("back must not change the query while the keyboard is up", emptyList<String>(), changes)
        assertEquals("the first back must put the keyboard away", 0, imeBottom)
        // The results the user was about to read are still there.
        compose.onNodeWithTag(HOME_SEARCH_RESULTS_TAG).assertIsDisplayed()
    }

    /**
     * The two presses together, in order, and the query is cleared exactly once.
     *
     * Runs both steps in one test because the contract is about the *sequence*: a handler that
     * cleared on both presses would pass each single-step case above on its own.
     */
    @Test
    fun theSecondBackClearsTheSearchExactlyOnce() {
        val changes = mutableListOf<String>()
        var imeBottom = -1
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "haribo", hits = listOf(searchHit())),
            onSearchQueryChanged = { changes += it },
            probeImeBottom = { imeBottom = it },
        )

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()
        awaitIme(present = true) { imeBottom }

        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        awaitIme(present = false) { imeBottom }
        assertEquals("the first back is the keyboard only", emptyList<String>(), changes)

        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()

        assertEquals("the second back clears, and only the second", listOf(""), changes)
    }

    /**
     * Reaching for the results puts the keyboard away without touching the search — Home's inline
     * list now behaves exactly like the search screen's.
     *
     * Before this pass Home had no such handler at all, so with live results arriving under an open
     * keyboard the only way to see more than half a list was the back button, which deleted the
     * search.
     */
    @Test
    fun touchingTheResultsDismissesTheKeyboardWithoutClearingTheSearch() {
        val changes = mutableListOf<String>()
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "haribo", hits = listOf(searchHit())),
            onSearchQueryChanged = { changes += it },
        )

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(HOME_SEARCH_RESULTS_TAG).performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertEquals("scrolling must not change the query", emptyList<String>(), changes)
        // Focus left the field, which is what takes the keyboard with it. Asserted on focus rather
        // than the inset because this dismissal IS the app's own doing (unlike Back's), so focus is
        // the thing the screen actually controls.
        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsNotFocused()
        compose.onNodeWithTag(HOME_SEARCH_RESULTS_TAG).assertIsDisplayed()
    }

    /**
     * The dismissal observes the gesture rather than consuming it, so a tap on a row still selects
     * that row on the *first* press — it is not spent putting the keyboard away.
     *
     * Note what this does and does not establish: Compose's synthetic `performClick` does not model
     * the pointer-consumption ordering a real finger produces, so this passes against both an
     * `Initial`/non-consuming handler and a `Main` one. It pins the property on the shipped code;
     * hardware is the discriminating check (see `dismissKeyboardOnTouch`).
     */
    @Test
    fun tappingAResultStillSelectsItOnTheFirstTap() {
        var selected: ProductSearchHit? = null
        show(
            recents = emptyList(),
            searchState = SearchUiState(query = "haribo", hits = listOf(searchHit())),
            onSearchSelect = { selected = it },
        )

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Chocoladehagel puur").performClick()

        assertEquals(searchHit().barcode, selected?.barcode)
    }

    /**
     * With no query to leave, the interception must be OFF so back falls through to its default
     * behaviour (closing the app, since Home has no back-stack entry) rather than being swallowed.
     * Asserted by actually pressing back with a blank query: Espresso surfaces a fall-through exit
     * as [androidx.test.espresso.NoActivityResumedException] rather than a normal return, which is
     * itself the proof the app was not intercepted — a caught exception here would mean the handler
     * wrongly stayed enabled and silently ate the press instead of letting the activity finish.
     */
    @Test
    fun systemBackWithNoActiveSearchClosesTheAppRatherThanBeingSwallowed() {
        show(recents = emptyList())

        try {
            androidx.test.espresso.Espresso.pressBack()
            org.junit.Assert.fail("expected back to fall through and finish the activity")
        } catch (expected: androidx.test.espresso.NoActivityResumedException) {
            // The handler declined to intercept, so the press reached the activity and closed it —
            // exactly the pre-existing (and here, still correct) behaviour for an empty query.
        }
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
        compose.onNodeWithTag(HOME_SEARCH_PROGRESS_TAG).assertExists()
    }

    /** Same quiet first-search treatment as the search screen (2026-09-17). */
    @Test
    fun aFirstSearchOnHomeShowsTheHairlineAndASearchingLineRatherThanASpinner() {
        show(recents = emptyList(), searchState = SearchUiState(query = "hagelslag", searching = true))

        compose.onNodeWithTag(HOME_SEARCH_PROGRESS_TAG).assertExists()
        compose.onNodeWithTag(HOME_SEARCH_SEARCHING_TAG).assertIsDisplayed()
        compose.onAllNodes(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and
                !hasTestTag(HOME_SEARCH_PROGRESS_TAG),
        ).assertCountEquals(0)
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
        compose.onNodeWithText(stringOf(R.string.error_server_title)).assertDoesNotExist()
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
        compose.onNodeWithText(stringOf(R.string.home_empty_headline)).assertIsDisplayed()
    }

    @Test
    fun theStarterFollowsTheEntryPointsWithoutAViewportSizedBlankGap() {
        show(recents = emptyList())

        val manualBottom = compose.onNodeWithText("Enter manually")
            .fetchSemanticsNode().boundsInRoot.bottom
        val starterTop = compose.onNodeWithText(stringOf(R.string.home_empty_headline))
            .fetchSemanticsNode().boundsInRoot.top
        with(compose.density) {
            assertTrue(
                "starter begins ${(starterTop - manualBottom).toDp()} after manual entry",
                (starterTop - manualBottom).toDp() < 80.dp,
            )
        }
    }

    @Test
    fun theStarterHeroDisappearsOnceRecentsExist() {
        show(recents = listOf(RecentEntry(product(), lastUnit = null)))
        compose.onNodeWithText(stringOf(R.string.home_empty_headline)).assertDoesNotExist()
    }

    @Test
    fun theMealBarStillTakesPriorityOverTheEmptyStarterContent() {
        show(recents = emptyList(), mealItems = listOf(mealItem()))

        compose.onNodeWithTag(MEAL_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertIsDisplayed()
    }

    /**
     * The barcode action's copy is context-aware (2026-09-14 interaction pass, item 7): with a meal
     * already in progress, the same tile offers to scan the *next* item rather than repeating the
     * ordinary first-scan invitation.
     */
    @Test
    fun theBarcodeActionOffersToScanNextWhenAMealIsInProgress() {
        show(recents = emptyList(), mealItems = listOf(mealItem()))

        compose.onNodeWithText("Scan next item").assertIsDisplayed()
        compose.onNodeWithText("Scan barcode").assertDoesNotExist()
    }

    @Test
    fun theBarcodeActionShowsOrdinaryScanCopyWithNoMealInProgress() {
        show(recents = emptyList(), mealItems = emptyList())

        compose.onNodeWithText("Scan barcode").assertIsDisplayed()
        compose.onNodeWithText("Scan next item").assertDoesNotExist()
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
        var manualClicks = 0
        show(
            recents = emptyList(),
            onManualEntry = { manualClicks++ },
            // Keep the emulator's actual px-to-dp ratio. Replacing density with 2.75 on CI's
            // 320px/160dpi device turns a 320dp screen into an impossible 116dp screen.
            density = Density(density = compose.density.density, fontScale = 1.8f),
        )

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_LABEL_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HOME_BODY_TAG).performScrollToIndex(2)
        compose.onNodeWithTag(HOME_MANUAL_TAG).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("manual entry must remain tappable at large fonts", 1, manualClicks)
    }

    /**
     * The empty-state step strip's previous `horizontalScroll` fallback ran the third step ("Carbs")
     * off past the strip's own right edge at a narrow width, reachable only by scrolling the strip
     * itself *sideways* — a gesture nothing on screen hinted at, and a second, independent scroll
     * axis nested inside the page's own vertical one. 320dp is this repo's own documented historical
     * minimum Android width (see the tap-anywhere-tutorial completion pass in CLAUDE.md, and
     * `ThemeRefinementVisualTest`/`TutorialScreenTest`'s existing `Box(Modifier.width(320.dp)...)`
     * convention for narrow-viewport tests) — this test uses 300dp, just below the strip's own
     * compact threshold.
     *
     * At that width the strip switches to a vertical arrangement (see `EmptyStateStepStrip`), which
     * is itself taller than the horizontal row and can push "Carbs" below the harness's own
     * (necessarily finite) viewport — an ordinary page-scroll, reached the same way every other item
     * in `HomeBody`'s `LazyColumn` is reached elsewhere in this file (`performScrollTo`, see e.g.
     * `largeFontKeepsEveryEntryPointReachable` above). What this test actually pins is that scrolling
     * the *page* to the last step is enough on its own: no second, horizontal scroll inside the strip
     * is needed to bring "Scan" and "Portion" onto screen at the same time as "Carbs" once there —
     * exactly the guarantee the removed `horizontalScroll` fallback could not make.
     */
    @Test
    fun theEmptyStateStepStripHasNoHorizontalScrollAtANarrowWidth() {
        compose.setContent {
            JustTheCarbsTheme {
                Box(
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                ) {
                    HomeScreen(
                        recents = emptyList(),
                        settings = AppSettings(),
                        onScan = {},
                        onManualEntry = {},
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Carbs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan").assertIsDisplayed()
        compose.onNodeWithText("Portion").assertIsDisplayed()
    }

    // ---- The carbohydrate figure on a recent card --------------------------------------------

    @Test
    fun aRecentProductShowsItsCarbFigureAsAFigure() {
        // The figure was always computed by `rememberedCarbs`; it was rendered as grey supporting
        // text inside "150 g → 5.6 g". This asserts it is present as its own labelled value, which
        // is what makes Home answer the question a returning user actually has.
        val product = Product(
            barcode = "1",
            name = "Griekse yoghurt",
            carbsPer100 = BigDecimal("3.7"),
            basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            lastPortion = BigDecimal("150"),
        )
        compose.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = listOf(RecentEntry(product = product, lastUnit = null)),
                    settings = AppSettings(),
                    onScan = {}, onManualEntry = {}, onOpenProduct = {},
                    onToggleFavorite = {}, onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithText("5.6 g").assertIsDisplayed()
        compose.onNodeWithText("CARBS").assertIsDisplayed()
    }

    @Test
    fun theRecentCarbFigureFollowsTheConfiguredResultStyle() {
        // Recents and the calculator disagreeing about a number is a defect this repo has already
        // fixed once — the whole-gram/decimal split. Promoting the figure to a prominent position
        // makes any future disagreement more visible, not less, so it is pinned here.
        val product = Product(
            barcode = "1",
            name = "Griekse yoghurt",
            carbsPer100 = BigDecimal("3.7"),
            basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            lastPortion = BigDecimal("150"),
        )
        compose.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = listOf(RecentEntry(product = product, lastUnit = null)),
                    settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT),
                    onScan = {}, onManualEntry = {}, onOpenProduct = {},
                    onToggleFavorite = {}, onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithText("6 g").assertIsDisplayed()
    }

    // ---- Remove from Recent ---------------------------------------------------------------

    private fun recentEntry(barcode: String = "1", name: String = "Hagelslag puur") =
        RecentEntry(product = product(barcode = barcode, name = name), lastUnit = null)

    /**
     * The contract that decides the whole interaction: Home stays quiet.
     *
     * A permanent per-card remove button would take its width from the product name, on every card,
     * forever, to serve an action taken rarely — so the action is deliberately behind a long press.
     * Asserted as an absence, because that is the only way this can regress: someone adding a
     * visible affordance "for discoverability" would break nothing else in this file.
     */
    @Test
    fun noVisibleRemoveControlExistsOnARecentCard() {
        show(recents = listOf(recentEntry()))

        compose.onNodeWithText(stringOf(R.string.recent_forget)).assertDoesNotExist()
        compose.onNodeWithContentDescription(stringOf(R.string.recent_options)).assertDoesNotExist()
    }

    @Test
    fun anOrdinaryTapOnARecentCardStillOpensTheProductRatherThanAMenu() {
        var opened: String? = null
        show(recents = listOf(recentEntry(barcode = "1")), onOpenProduct = { opened = it })

        // `useUnmergedTree` puts the gesture on the product name. The merged node is the whole card,
        // and its geometric centre now falls on the card's Quick Add pill — a tap there is an add,
        // by design, not an open. The name is where a person taps to open a product.
        compose.onNodeWithText("Hagelslag puur", useUnmergedTree = true).performClick()

        assertEquals("1", opened)
        compose.onNodeWithText(stringOf(R.string.recent_forget)).assertDoesNotExist()
    }

    @Test
    fun longPressingARecentCardOpensItsOptions() {
        show(recents = listOf(recentEntry()))

        // `useUnmergedTree` puts the gesture on the product name. The merged node is the whole card,
        // and its geometric centre now falls on the card's Quick Add pill — a tap there is an add,
        // by design, not an open. The name is where a person taps to open a product.
        compose.onNodeWithText("Hagelslag puur", useUnmergedTree = true).performTouchInput { longClick() }

        compose.onNodeWithTag(HOME_RECENT_FORGET_TAG).assertIsDisplayed()
        // The menu names the card it belongs to, and says what survives — the action is otherwise
        // easy to read as "delete this product".
        compose.onNodeWithText(stringOf(R.string.recent_forget_explainer)).assertIsDisplayed()
    }

    @Test
    fun choosingRemoveFromRecentReportsTheProductExactlyOnce() {
        val removed = mutableListOf<String>()
        show(
            recents = listOf(recentEntry(barcode = "1"), recentEntry(barcode = "2", name = "Melk")),
            onForgetRecent = { removed += it.barcode },
        )

        // Bring the second card fully into view first. On CI's profile-less 320x640 @160dpi
        // emulator the second card is composed only at the bottom edge, so the long press on "Melk"
        // never reached it and the options menu never opened (`home_recent_forget` not found); on a
        // shorter screen the "Melk" node does not exist at all. `performScrollToNode` on the owning
        // list, not `performScrollTo` on the target: the latter needs the node to exist already.
        // Reproduce with `wm size 320x640` / `wm density 160`.
        compose.onNodeWithTag(HOME_BODY_TAG).performScrollToNode(hasText("Melk"))

        // `useUnmergedTree` puts the gesture on the product name. The merged node is the whole card,
        // and its geometric centre now falls on the card's Quick Add pill — a tap there is an add,
        // by design, not an open. The name is where a person taps to open a product.
        compose.onNodeWithText("Melk", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag(HOME_RECENT_FORGET_TAG).performClick()

        assertEquals(listOf("2"), removed)
    }

    /**
     * The gesture must be reachable without performing the gesture.
     *
     * `onLongClickLabel` is what puts the action in TalkBack's "Actions available" list; without it
     * the long press is still there and a screen-reader user has no way to discover or trigger it,
     * which would make this feature sighted-only.
     */
    @Test
    fun theRecentCardExposesItsOptionsAsALabelledAccessibilityAction() {
        show(recents = listOf(recentEntry()))

        // `useUnmergedTree`, because the card does not merge its descendants: the long-click action
        // lives on the card's own clickable node, which the merged tree folds into the text beneath
        // it. Asserted by count so the label cannot be satisfied by some other node acquiring one.
        compose.onAllNodes(hasLongClickLabelOf(stringOf(R.string.recent_options)), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun theUndoSnackbarNamesTheRemovedProductAndRestoresIt() {
        var undone = 0
        show(
            recents = emptyList(),
            forgotten = ForgottenRecent(
                name = "Hagelslag puur",
                snapshot = RecentUseSnapshot(
                    barcode = "1",
                    lastUsedAt = Instant.EPOCH,
                    lastPortion = BigDecimal("65"),
                    lastInputMode = null,
                    lastSelectedPortionUnitId = null,
                    lastCount = null,
                    portionUsage = emptyList(),
                ),
            ),
            onUndoForgetRecent = { undone++ },
        )

        compose.onNodeWithTag(HOME_SNACKBAR_TAG).assertIsDisplayed()
        compose.onNodeWithText(stringOf(R.string.recent_forgotten, "Hagelslag puur")).assertIsDisplayed()
        compose.onNodeWithText(stringOf(R.string.action_undo)).performClick()

        assertEquals(1, undone)
    }

    private fun stringOf(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)


    private fun hasLongClickLabelOf(label: String) = SemanticsMatcher("long-click label is '$label'") {
        it.config.getOrNull(SemanticsActions.OnLongClick)?.label == label
    }

    // ---- Search panels and list position ----------------------------------------------------

    private fun numberedHit(i: Int) = searchHit().copy(barcode = "10000$i", name = "Product $i")

    /** Search state a test can change after the screen is up, the way the ViewModel does. */
    private var liveSearch by mutableStateOf(SearchUiState())

    private fun showWithLiveSearch(recents: List<RecentEntry> = emptyList()) {
        compose.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = recents,
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    searchState = liveSearch,
                )
            }
        }
    }

    @Test
    fun aSearchThatMatchesNothingOffersToScanTheBarcode() {
        var scans = 0
        show(searchState = SearchUiState(query = "zzqxvkw", noMatches = true), onScan = { scans++ })

        compose.onNodeWithText(stringOf(R.string.home_scan_button)).performScrollTo().performClick()

        assertEquals(1, scans)
    }

    @Test
    fun aFailedFirstSearchOffersToScanTheBarcode() {
        var scans = 0
        show(searchState = SearchUiState(query = "hagelslag", error = LookupError.OFFLINE), onScan = { scans++ })

        compose.onNodeWithText(stringOf(R.string.home_scan_button)).performScrollTo().performClick()

        assertEquals(1, scans)
    }

    /**
     * A refined query replaces the list; the new list starts at its top rather than wherever the old
     * one had been scrolled to (the lazy list otherwise keeps the first visible row by key).
     */
    @Test
    fun aRefinedQueryShowsItsResultsFromTheTop() {
        val hits = (0 until 30).map(::numberedHit)
        liveSearch = SearchUiState(query = "prod", hits = hits)
        showWithLiveSearch()
        compose.onNodeWithTag(HOME_SEARCH_RESULTS_TAG).performScrollToIndex(hits.lastIndex)
        compose.onNodeWithText("Product 2").assertDoesNotExist()

        compose.runOnIdle { liveSearch = SearchUiState(query = "produ", hits = hits.drop(2)) }

        compose.onNodeWithText("Product 2").assertIsDisplayed()
    }

    /** Clearing a search returns to Recents where they were, not to the top of Home. */
    @Test
    fun clearingASearchReturnsToWhereRecentsWereScrolled() {
        val recents = (1..15).map { recentEntry(barcode = "$it", name = "Recent $it") }
        showWithLiveSearch(recents = recents)
        compose.onNodeWithTag(HOME_BODY_TAG).performScrollToNode(hasText("Recent 15"))
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertDoesNotExist()

        compose.runOnIdle { liveSearch = SearchUiState(query = "choc", hits = listOf(searchHit())) }
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.runOnIdle { liveSearch = SearchUiState() }

        compose.onNodeWithText("Recent 15").assertIsDisplayed()
    }
}
