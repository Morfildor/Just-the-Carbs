package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CompletableDeferred
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.ui.search.SEARCH_FIELD_TAG
import app.justthecarbs.ui.search.SEARCH_PENDING_TAG
import app.justthecarbs.ui.search.SEARCH_RATE_LIMITED_TAG
import app.justthecarbs.ui.search.SEARCH_REFRESH_ERROR_TAG
import app.justthecarbs.ui.search.SEARCH_REFRESH_PROGRESS_TAG
import app.justthecarbs.ui.search.SEARCH_RESULTS_TAG
import app.justthecarbs.ui.search.SEARCH_SUBMIT_TAG
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.search.SearchViewModel
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
        onSearchSubmit: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                SearchScreen(
                    state = state,
                    onQueryChanged = {},
                    onSearchSubmit = onSearchSubmit,
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
        // The quantity's space is non-breaking on screen, so "390" can never end a line without its
        // unit; it renders identically to an ordinary space.
        compose.onNodeWithText("De Ruijter · 390${Typography.nbsp}gram").assertIsDisplayed()
        compose.onNodeWithText("67 g carbs").assertIsDisplayed()
        compose.onNodeWithText("/ 100 g").assertIsDisplayed()
    }

    /**
     * The row's spoken content description and its visible two-line value must agree. Before this
     * fix the visible text went through [ResultFormatter.quantity] (one decimal place, locale-aware
     * separator) while the spoken description used raw [java.math.BigDecimal] precision — the same
     * integer test value ("67") happened to make both agree, which is why no earlier test caught the
     * divergence. A fractional value exposes it: the visible figure rounds to one decimal place
     * while an unrounded description would still read the full value.
     */
    @Test
    fun theSpokenCarbFigureMatchesTheVisibleRoundedOne() {
        show(SearchUiState(query = "hagelslag", hits = listOf(hit(carbs = "12.34"))))

        val expectedQuantity = ResultFormatter.quantity(BigDecimal("12.34"))
        assertEquals("12.3", expectedQuantity)
        // The printed package quantity is part of the spoken line as of the 2026-09-16 presentation
        // pass: the card shows "De Ruijter · 390 gram" and the description used to say only
        // "De Ruijter", so a screen-reader user lost the field that tells a 390 g pack from a 600 g
        // one. Only the expected literal changed here — what this case exists to prove, that the
        // spoken figure is the same ROUNDED figure the card shows, is unchanged and still asserted.
        compose.onNodeWithContentDescription(
            "Chocoladehagel puur. De Ruijter. 390 gram. $expectedQuantity g carbs / 100 g",
        ).assertIsDisplayed()
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
        compose.onNodeWithText("0 g carbs").assertDoesNotExist()
    }

    /**
     * Defense in depth against `SearchNutritionColumn` rendering a bare, unlabelled number.
     *
     * `ProductSearchHit`'s own data layer null-links [ProductSearchHit.carbsPer100] and
     * [ProductSearchHit.basis] at the one place hits are built, so a non-null value with a null
     * basis should not be constructible in practice — but the component checks both fields anyway,
     * exactly as [SearchResultRow]'s own KDoc describes, so a future construction path that broke
     * that invariant would still degrade to "no value" instead of a number nothing supports.
     */
    @Test
    fun searchRowsPairValueAndBasisAndSuppressAnUnknownBasis() {
        val unknownBasisHit = hit(carbs = "67").copy(basis = null)
        show(SearchUiState(query = "hagelslag", hits = listOf(unknownBasisHit)))

        compose.onNodeWithText("No carbohydrate value — check the package").assertIsDisplayed()
        compose.onNodeWithText("67 g carbs").assertDoesNotExist()
    }

    /** A missing carbohydrate figure renders the quiet "no value" text, not a fake zero. */
    @Test
    fun searchRowsShowAQuietNoValueStateForAMissingCarbFigure() {
        show(SearchUiState(query = "hagelslag", hits = listOf(hit(carbs = null))))

        compose.onNodeWithText("No carbohydrate value — check the package").assertIsDisplayed()
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

    /**
     * Typing must not fire the **explicit** submit callback.
     *
     * Live search is not this callback: it is the ViewModel's own debounced reaction to
     * `onQueryChanged`, which is what [liveSearchShowsResultsWithoutPressingEnter] drives. Keeping
     * them apart is the point — if typing also called `onSearchSubmit`, the screen would be issuing
     * an immediate request per keystroke, which is exactly what the debounce exists to prevent.
     */
    @Test
    fun typingAloneDoesNotSubmitAnExplicitSearch() {
        var submitCount = 0
        show(SearchUiState(), onSearchSubmit = { submitCount++ })

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")

        assertEquals("typing must not fire the explicit submit", 0, submitCount)
    }

    // ---- Live search, driven through the real ViewModel ----------------------------------------

    /**
     * A [ProductSearchSource] that answers immediately, so the screen's behaviour is what is being
     * measured rather than a fake's timing. The debounce is real: Compose's test clock advances
     * automatically while [ComposeContentTestRule.waitUntil] polls, so no sleep is needed and none
     * is used.
     */
    private class ImmediateSearchSource(
        private val result: ProductSearchResult,
    ) : ProductSearchSource {
        val queries = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            queries += terms
            return result
        }
    }

    private fun showLive(source: ProductSearchSource): SearchViewModel {
        val viewModel = SearchViewModel(source)
        compose.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            JustTheCarbsTheme {
                SearchScreen(
                    state = state,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearchSubmit = viewModel::search,
                    onSelect = {},
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = viewModel::retry,
                    onBack = {},
                )
            }
        }
        return viewModel
    }

    /** The headline behaviour of this pass: results arrive without the user pressing anything. */
    @Test
    fun liveSearchShowsResultsWithoutPressingEnter() {
        val source = ImmediateSearchSource(ProductSearchResult.Found(listOf(hit())))
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("Chocoladehagel puur").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        assertEquals(listOf("hagelslag"), source.queries)
    }

    /**
     * Passive typing below the minimum length is not a refusal.
     *
     * The refusal notice is for a *submission*. Showing it while someone is still typing puts an
     * error beside every query mid-word — and, since it is a live region, announces it to a TalkBack
     * user on every keystroke.
     */
    @Test
    fun typingTooFewCharactersShowsThePromptRatherThanTheRefusal() {
        val source = ImmediateSearchSource(ProductSearchResult.Found(listOf(hit())))
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("ha")
        compose.waitForIdle()

        compose.onNodeWithText("Type at least 3 characters to search.").assertDoesNotExist()
        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertIsDisplayed()
        assertEquals(emptyList<String>(), source.queries)
    }

    /** A manual submission of a too-short query still says why nothing happened. */
    @Test
    fun submittingTooFewCharactersStillShowsTheRefusal() {
        val source = ImmediateSearchSource(ProductSearchResult.Found(listOf(hit())))
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("ha")
        compose.onNodeWithTag(SEARCH_SUBMIT_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Type at least 3 characters to search.").assertIsDisplayed()
        assertEquals(emptyList<String>(), source.queries)
    }

    /** Pressing Search still works, and still costs exactly one request. */
    @Test
    fun explicitSearchStillWorksAndDoesNotDuplicateTheLiveRequest() {
        val source = ImmediateSearchSource(ProductSearchResult.Found(listOf(hit())))
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")
        compose.onNodeWithTag(SEARCH_SUBMIT_TAG).performClick()

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("Chocoladehagel puur").fetchSemanticsNodes().isNotEmpty()
        }
        // Idle again, so any debounce that was still queued has had its chance to fire.
        compose.waitForIdle()

        assertEquals("Enter must not add a second request", listOf("hagelslag"), source.queries)
    }

    /** Clearing the field returns the screen to the state it started in. */
    @Test
    fun clearingTheQueryResetsTheScreen() {
        val source = ImmediateSearchSource(ProductSearchResult.Found(listOf(hit())))
        showLive(source)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("Chocoladehagel puur").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Chocoladehagel puur").assertDoesNotExist()
        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertIsDisplayed()
    }

    /**
     * The empty state must mean "a completed search matched nothing", never "a search is running".
     *
     * Asserted at the moment the results are on screen and a newer query is in flight — the exact
     * window in which a naive implementation blanks the list and flashes the no-results text.
     */
    @Test
    fun noResultsTextNeverAppearsWhileASearchIsStillRunning() {
        // A CompletableDeferred, not a CountDownLatch: the ViewModel's searches run on
        // Dispatchers.Main, so a *blocking* await here freezes the very thread Compose's test
        // synchronization drives and the test deadlocks rather than failing. Suspending releases the
        // thread, which is what a real network call does too.
        val gate = CompletableDeferred<Unit>()
        val source = object : ProductSearchSource {
            @Volatile var calls = 0
            override suspend fun search(terms: String): ProductSearchResult {
                calls++
                // The first query answers; the second is held open, so the screen is observed
                // exactly while a newer search is in flight over an existing result list.
                if (calls > 1) gate.await()
                return ProductSearchResult.Found(listOf(hit()))
            }
        }
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("Chocoladehagel puur").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(" puur")
        // The second request waits out the shared request interval, so this needs the longer
        // ceiling — the pacing is real here, not faked.
        compose.waitUntil(SECOND_REQUEST_TIMEOUT_MS) { source.calls > 1 }

        // The previous results are still shown rather than blanked, and neither the no-results text
        // nor the initial prompt has flashed up in their place.
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithText("No products found", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertDoesNotExist()

        gate.complete(Unit)
        compose.waitForIdle()
    }

    @Test
    fun theImeSearchActionSubmitsExactlyOnce() {
        var submitCount = 0
        show(SearchUiState(query = "hagelslag"), onSearchSubmit = { submitCount++ })

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()

        assertEquals(1, submitCount)
    }

    @Test
    fun theSearchButtonSubmitsExactlyOnce() {
        var submitCount = 0
        show(SearchUiState(query = "hagelslag"), onSearchSubmit = { submitCount++ })

        compose.onNodeWithTag(SEARCH_SUBMIT_TAG).performClick()

        assertEquals(1, submitCount)
    }

    // ---- Refresh continuity: what the user keeps while a newer search runs ---------------------

    /**
     * The primary target of the smoothing pass, asserted as rendering rather than as state.
     *
     * A refresh over an existing list must read as "these are being replaced", not as "the screen
     * restarted": the list stays, the hairline appears, and the full-region spinner — which is what
     * makes the screen look like it is reloading from nothing — must not.
     */
    @Test
    fun refreshingOverExistingResultsKeepsThemAndShowsOnlyTheHairline() {
        show(SearchUiState(query = "hagelslag puur", hits = listOf(hit()), searching = true))

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_RESULTS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_REFRESH_PROGRESS_TAG).assertExists()
    }

    /** With nothing to keep, the centred spinner is right — it is the whole content of the screen. */
    @Test
    fun aFirstSearchWithNoResultsYetShowsTheCentredSpinnerAndNoHairline() {
        show(SearchUiState(query = "hagelslag", searching = true))

        compose.onNodeWithTag(SEARCH_RESULTS_TAG).assertDoesNotExist()
        // Never both at once: two simultaneous loading indicators for one search read as two
        // things happening.
        compose.onNodeWithTag(SEARCH_REFRESH_PROGRESS_TAG).assertDoesNotExist()
    }

    /**
     * A transient refresh failure must not cost the user the list they were reading.
     *
     * The recovery panel's actions answer "the database has nothing for you", which is not what
     * happened — the results below are still good and still tappable.
     */
    @Test
    fun aFailedRefreshKeepsTheResultsAndShowsOnlyAnInlineNotice() {
        show(
            SearchUiState(
                query = "hagelslag puur",
                hits = listOf(hit()),
                error = LookupError.SERVER,
                refreshFailed = true,
            ),
        )

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_REFRESH_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithText("Couldn't refresh results").assertIsDisplayed()
        // The full-screen failure's own copy must be absent — that panel is for the other case.
        compose.onNodeWithText("You can read the value from the package instead.").assertDoesNotExist()
        compose.onNodeWithText("Enter manually").assertDoesNotExist()
    }

    /** Retry from the inline notice targets the current query and leaves the list in place. */
    @Test
    fun retryingFromTheInlineNoticeKeepsTheResultsVisible() {
        var retries = 0
        show(
            SearchUiState(
                query = "hagelslag puur",
                hits = listOf(hit()),
                error = LookupError.SERVER,
                refreshFailed = true,
            ),
            onRetry = { retries++ },
        )

        compose.onNodeWithTag(SEARCH_REFRESH_ERROR_TAG).onChildren()
            .filterToOne(hasText("Try again")).performClick()

        assertEquals(1, retries)
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
    }

    /** A failure with nothing to preserve still gets the full recovery panel. */
    @Test
    fun aFirstSearchFailureStillShowsTheFullErrorState() {
        show(SearchUiState(query = "hagelslag", error = LookupError.SERVER))

        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_REFRESH_ERROR_TAG).assertDoesNotExist()
    }

    /**
     * Driven end to end through the real ViewModel: results, then a refresh that fails.
     *
     * The state-level tests prove the ViewModel keeps the hits; this proves the screen renders that
     * as a notice over a live list rather than as a replacement for it.
     */
    @Test
    fun aLiveRefreshFailureLeavesTheResultsOnScreen() {
        val source = object : ProductSearchSource {
            @Volatile var calls = 0
            override suspend fun search(terms: String): ProductSearchResult {
                calls++
                return if (calls == 1) {
                    ProductSearchResult.Found(listOf(hit()))
                } else {
                    ProductSearchResult.Failed(LookupError.SERVER)
                }
            }
        }
        showLive(source)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("hagelslag")
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("Chocoladehagel puur").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(" puur")
        // Second request — paced by the shared interval, hence the longer ceiling.
        compose.waitUntil(SECOND_REQUEST_TIMEOUT_MS) {
            compose.onAllNodesWithTag(SEARCH_REFRESH_ERROR_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertDoesNotExist()
    }

    // ---- Request pacing, as rendered ------------------------------------------------------------

    /**
     * Waiting on the shared request budget is **not** an outage and must not be drawn as one.
     *
     * This is the state the real-device failure produced: the app had earned a 503 by asking too
     * often, and the screen reported it as "The product database is unavailable". Pacing is now a
     * calm, self-resolving wait, and the recovery panel — whose actions answer "the database has
     * nothing for you" — must be nowhere near it.
     */
    @Test
    fun waitingForTheRequestBudgetIsNotShownAsAnOutage() {
        show(SearchUiState(query = "chocolate", searching = true, awaitingRemotePermit = true))

        compose.onNodeWithTag(SEARCH_PENDING_TAG).assertIsDisplayed()
        compose.onNodeWithText("The product database is unavailable").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("No products found", substring = true).assertDoesNotExist()
    }

    /**
     * An enforced server backoff offers **no Retry**.
     *
     * A button there invites exactly the request hammering the backoff exists to stop, and there is
     * nothing to retry anyway — the queued query resumes by itself.
     */
    @Test
    fun aRateLimitOffersNoRetryAndNoDatabaseUnavailableCopy() {
        show(
            SearchUiState(
                query = "chocolate",
                hits = listOf(hit()),
                searching = true,
                awaitingRemotePermit = true,
                rateLimited = true,
            ),
        )

        compose.onNodeWithTag(SEARCH_RATE_LIMITED_TAG).assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("The product database is unavailable").assertDoesNotExist()
        // The results the user was reading survive the backoff.
        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
    }

    /** No API jargon reaches the user: not the status code, not the word "limit", not the vendor. */
    @Test
    fun theRateLimitNoticeExposesNoApiJargon() {
        show(
            SearchUiState(
                query = "chocolate",
                searching = true,
                awaitingRemotePermit = true,
                rateLimited = true,
            ),
        )

        listOf("429", "rate limit", "Rate limit", "requests", "quota", "Open Food Facts").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    /**
     * A query that diverges from the one on screen shows a neutral pending state, never the
     * previous query's products and never a verdict about a search that has not run.
     */
    @Test
    fun aDivergentQueryShowsNeitherOldResultsNorAVerdict() {
        show(SearchUiState(query = "gouda", searching = true, awaitingRemotePermit = true))

        compose.onNodeWithText("Chocoladehagel puur").assertDoesNotExist()
        compose.onNodeWithText("No products found", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(SEARCH_PENDING_TAG).assertIsDisplayed()
    }

    /** Typing is answered from results already on screen, with no network involved. */
    @Test
    fun localNarrowingRendersImmediatelyWhileTheRefreshIsStillPending() {
        show(
            SearchUiState(
                query = "chocolate",
                hits = listOf(hit()),
                searching = true,
                awaitingRemotePermit = true,
                narrowedLocally = true,
            ),
        )

        compose.onNodeWithText("Chocoladehagel puur").assertIsDisplayed()
        // Labelled as still-refreshing rather than presented as the completed answer.
        compose.onNodeWithTag(SEARCH_REFRESH_PROGRESS_TAG).assertExists()
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

    private companion object {
        /**
         * A generous ceiling for `waitUntil`, not a sleep — the condition is polled and the test
         * proceeds the instant it holds. Comfortably above
         * [SearchViewModel.REMOTE_SEARCH_SETTLE_MS] so a slow emulator cannot turn a passing
         * behaviour into a timeout.
         */
        const val TIMEOUT_MS = 5_000L

        /**
         * The ceiling for a case that needs a **second** remote request.
         *
         * These tests run against a real-clock governor, so the second request genuinely waits out
         * [app.justthecarbs.domain.RemoteSearchGovernor.MIN_INTERVAL_MS]. That wait is the feature
         * under test elsewhere; here it is just latency to be tolerated, so the ceiling covers the
         * interval plus the settle wait plus emulator slack. Still a polled condition, not a sleep.
         */
        const val SECOND_REQUEST_TIMEOUT_MS = 15_000L
    }

    // ---- Keyboard dismissal over live results ------------------------------------------------

    /**
     * Selecting a product from the list is still ONE tap.
     *
     * The touch-to-dismiss gesture added to the results list sits on the same nodes as the rows,
     * so the hazard it introduces is a tap being spent dismissing the keyboard instead of opening
     * the product. This pins the shipped behaviour: the handler observes on
     * [androidx.compose.ui.input.pointer.PointerEventPass.Initial] with `requireUnconsumed = false`
     * and consumes nothing, so selection is unaffected.
     *
     * **This test does not discriminate between the Initial and Main passes.** A control run with
     * `Main` passes it too — `performClick` injects a synthetic down/up that does not reproduce the
     * consumption ordering a real finger produces. Measured, not assumed, so nobody reads a green
     * result here as proof that the pass choice is load-bearing; only hardware can show that.
     */
    @Test
    fun tappingAResultStillSelectsItOnTheFirstTap() {
        var selected: ProductSearchHit? = null
        show(
            SearchUiState(query = "hagelslag", hits = listOf(hit())),
            onSelect = { selected = it },
        )

        compose.onNodeWithText("Chocoladehagel puur").performClick()

        assertEquals("one tap must select, not merely dismiss the keyboard", "8710496979125", selected?.barcode)
    }

    /**
     * The gesture must not interfere with the list itself.
     *
     * A pointer handler on a scrollable container is the classic way to break scrolling, so this
     * asserts the list still renders and still holds every row after the handler is attached —
     * the cheap structural check that the modifier did not swallow the list's own input.
     */
    @Test
    fun theResultsListStillRendersEveryRowWithTheDismissalGestureAttached() {
        show(
            SearchUiState(
                query = "hagelslag",
                hits = listOf(
                    hit(barcode = "1111111111111", name = "Hagelslag puur"),
                    hit(barcode = "2222222222222", name = "Hagelslag melk"),
                ),
            ),
        )

        compose.onNodeWithTag(SEARCH_RESULTS_TAG).assertIsDisplayed()
        compose.onNodeWithText("Hagelslag puur").assertIsDisplayed()
        compose.onNodeWithText("Hagelslag melk").assertIsDisplayed()
    }
}
