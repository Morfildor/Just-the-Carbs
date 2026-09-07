package app.justthecarbs.ui.search

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Search runs **as you type**, debounced by [SearchViewModel.REMOTE_SEARCH_SETTLE_MS].
 *
 * Open Food Facts' search endpoint allows 10 reads/min/IP, so the rules these tests pin are as much
 * about request economy as about correctness: a typed word must cost one request rather than one per
 * keystroke, an explicit submission must skip the wait without duplicating the automatic call, and —
 * the one that cannot be traded away — a response for an older query must never land on top of a
 * newer one, whatever order the network answers in.
 *
 * Everything runs on virtual time ([StandardTestDispatcher]); there is no real delay anywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    /**
     * Just past the settle wait **and** the shared request interval, so a scheduled search has
     * actually gone out.
     *
     * Covers both waits deliberately. Advancing only past the settle delay leaves the request
     * parked in the governor, so a test written against the settle time alone would report zero
     * calls and read as a scheduling bug — which is exactly what the pacing is supposed to do, and
     * exactly what the request-accounting tests below assert on purpose.
     */
    private fun past(rounds: Int = 1) =
        (SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.MIN_INTERVAL_MS) * rounds + 1

    private val dispatcher = StandardTestDispatcher()

    /**
     * The governor under test, driven by the **scheduler's** virtual clock.
     *
     * This is what keeps the suite fast and deterministic: a real-clock governor would impose
     * genuine 7-second cooldowns that `advanceTimeBy` cannot move, so every pacing test would
     * either sleep or be untestable. `currentTime` is the same clock `delay` runs on, so the
     * governor and the pipeline agree about what time it is.
     */
    private val governor = RemoteSearchGovernor { dispatcher.scheduler.currentTime }

    /** A ViewModel wired to the shared virtual clock. */
    private fun viewModelFor(source: ProductSearchSource) =
        SearchViewModel(source, governor) { dispatcher.scheduler.currentTime }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun hit(barcode: String = "8710496979125") = ProductSearchHit(
        barcode = barcode,
        name = "Chocoladehagel puur",
        brand = "De Ruijter",
        packageQuantity = "390 gram",
        carbsPer100 = BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    /**
     * Counts calls and lets a test control exactly when each call's response resolves.
     *
     * **Honours cancellation**, like a well-behaved suspending transport: a search whose coroutine
     * is cancelled never returns. That makes it the right fake for testing that requests are
     * *cancelled*, and the wrong one for testing what happens when a cancelled request **completes
     * anyway** — see [UncancellableSearchSource].
     */
    private class FakeSearchSource : ProductSearchSource {
        var callCount = 0
            private set
        val callsInOrder = mutableListOf<String>()
        private val pending = mutableMapOf<String, CompletableDeferred<ProductSearchResult>>()

        override suspend fun search(terms: String): ProductSearchResult {
            callCount++
            callsInOrder.add(terms)
            val deferred = CompletableDeferred<ProductSearchResult>()
            pending[terms] = deferred
            return deferred.await()
        }

        fun resolve(terms: String, result: ProductSearchResult) {
            pending.remove(terms)?.complete(result)
        }
    }

    /**
     * A transport that does **not** honour cancellation: every started search runs to completion and
     * returns, however long ago its coroutine was cancelled.
     *
     * This is the fake the stale-result guarantee has to be proven against. With a cancellation-
     * honouring fake, `flatMapLatest`/`collectLatest` alone make the test pass — the old response
     * simply never arrives — so the test would be measuring the cancellation and claiming to measure
     * the staleness guard. Modelling only the safe version of a hazard proves nothing about the
     * hazard.
     *
     * `withContext(NonCancellable)` is what makes the completion reach the caller. Real code can be
     * in this position for ordinary reasons — a response already buffered in memory, a callback
     * adapter that ignores the cancellation signal, or work that finished between the cancellation
     * and the next suspension point.
     */
    private class UncancellableSearchSource : ProductSearchSource {
        val callsInOrder = mutableListOf<String>()
        private val pending = mutableMapOf<String, CompletableDeferred<ProductSearchResult>>()

        override suspend fun search(terms: String): ProductSearchResult {
            callsInOrder.add(terms)
            val deferred = CompletableDeferred<ProductSearchResult>()
            pending[terms] = deferred
            return withContext(NonCancellable) { deferred.await() }
        }

        fun resolve(terms: String, result: ProductSearchResult) {
            pending.remove(terms)?.complete(result)
        }
    }

    // ---- A. Debounce -------------------------------------------------------------------------

    @Test
    fun `a valid query does not search immediately`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        // Everything runnable has run; only the debounce delay has not elapsed.
        dispatcher.scheduler.runCurrent()

        assertEquals("the debounce must not be skipped", 0, source.callCount)
    }

    @Test
    fun `a valid query searches once the debounce elapses`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertEquals(listOf(hit()), viewModel.state.value.hits)
    }

    // ---- B. Rapid typing ---------------------------------------------------------------------

    @Test
    fun `typing a whole word costs one request, not one per keystroke`() = runTest {
        // The headline efficiency claim. Nine edits, each inside the debounce window, must collapse
        // to a single call for the final text — this is what keeps a normal search well inside the
        // endpoint's 10 reads/min/IP budget.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        listOf("c", "ch", "cho", "choc", "choco", "chocol", "chocola", "chocolat", "chocolate")
            .forEach { text ->
                viewModel.onQueryChanged(text)
                dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 4)
            }
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(1, source.callCount)
        assertEquals(listOf("chocolate"), source.callsInOrder)
    }

    @Test
    fun `typing again before the debounce expires cancels the pending search`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())

        // Not merely "the newer one also ran": the older one must never have been sent at all.
        assertEquals(listOf("hagelslag puur"), source.callsInOrder)
    }

    // ---- C. Minimum length -------------------------------------------------------------------

    @Test
    fun `typing below the minimum length never reaches the network`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("h")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("ha")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(0, source.callCount)
        assertEquals("ha", viewModel.state.value.query)
    }

    @Test
    fun `crossing the minimum length starts a live search`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("ha")
        dispatcher.scheduler.advanceTimeBy(past())
        assertEquals(0, source.callCount)

        viewModel.onQueryChanged("hag")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(listOf("hag"), source.callsInOrder)
    }

    @Test
    fun `dropping back below the minimum length cancels the pending search`() = runTest {
        // Backspacing through the threshold must not leave a queued request for text that no longer
        // qualifies — the delayed call would arrive for a query the user has already shortened.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.onQueryChanged("ha")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertFalse(viewModel.state.value.searching)
    }

    // ---- D. Clearing -------------------------------------------------------------------------

    @Test
    fun `clearing the query cancels the pending search and fires nothing later`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.onQueryChanged("")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    @Test
    fun `clearing the query restores the initial state`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.hits.size)

        viewModel.onQueryChanged("")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchUiState(), viewModel.state.value)
    }

    // ---- E. Duplicate queries ----------------------------------------------------------------

    @Test
    fun `text that normalizes to the same query does not search twice`() = runTest {
        // "milk" -> "milk " -> " milk " is one search. Whitespace edits are the ordinary consequence
        // of typing and of autocorrect, and each one costing a request would spend the rate budget
        // on nothing.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("milk")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("milk ")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged(" milk ")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(1, source.callCount)
        // The editor still shows exactly what was typed — normalization decides what is *searched*,
        // never what the field displays.
        assertEquals(" milk ", viewModel.state.value.query)
    }

    @Test
    fun `a whitespace-only edit does not disturb results already showing`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("milk")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("milk", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("milk ")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    // ---- F. Explicit search ------------------------------------------------------------------

    @Test
    fun `explicit search triggers exactly one call`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertEquals(listOf(hit()), viewModel.state.value.hits)
    }

    @Test
    fun `an explicit search bypasses the settle wait`() = runTest {
        // Enter skips the settle delay — that is what "immediate" means and all it has ever meant.
        // It does not skip the request budget; see `an explicit search cannot bypass the request
        // budget` below, which is the half that protects the endpoint.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        // No time advanced past the settle wait — only the already-runnable coroutines. The budget
        // permits this one because it is the first request of the session.
        dispatcher.scheduler.runCurrent()

        assertEquals("pressing Search must not wait out the settle delay", 1, source.callCount)
    }

    @Test
    fun `pressing Search does not also fire the debounced request behind it`() = runTest {
        // The duplicate this pipeline exists to prevent: the user types, then presses Search before
        // the debounce elapses. Two independent paths would send the identical query twice — once
        // now, once when the queued delay expired.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.search()
        dispatcher.scheduler.advanceTimeBy(past(rounds = 2))

        assertEquals(1, source.callCount)
    }

    @Test
    fun `pressing Search while its own live request is in flight does not resend it`() = runTest {
        // The debounce has already elapsed and the call has gone out. Pressing Search cannot make
        // the answer arrive sooner, and re-issuing would cancel the running call and pay for a
        // second one.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        assertEquals(1, source.callCount)

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `blank query does not search`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("   ")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    @Test
    fun `a too-short query does not search and shows no results`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("ha")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `a too-short submitted query says why nothing happened`() = runTest {
        // Refusing the request is right — two characters match thousands of products — but refusing
        // it *silently* is a dead end: the button is tapped, no request is made, no spinner appears,
        // and the screen still shows the same "type a product name" prompt it showed before. The
        // only reading available to the user is that the tap did not register.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("ha")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertTrue("a refused submission must say why", viewModel.state.value.queryTooShort)
    }

    @Test
    fun `editing the query clears the too-short notice`() = runTest {
        // The notice belongs to one submission, not to the field. Left up, it would still be on
        // screen next to a query that is now long enough and has not been submitted.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("ha")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.queryTooShort)

        viewModel.onQueryChanged("hagelslag")

        assertFalse(viewModel.state.value.queryTooShort)
    }

    @Test
    fun `a long enough query submitted after a short one clears the notice and searches`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("ha")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.queryTooShort)
        assertEquals(1, source.callCount)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `a blank submission is not reported as too short`() = runTest {
        // An empty field is not a refused search — the user has not asked for anything yet, and the
        // screen's own prompt already says what to do. Telling them "3 characters" there would be
        // an error message about a mistake nobody made.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("   ")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.queryTooShort)
    }

    @Test
    fun `submitting the same query twice in a row does not duplicate the request`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        // A second submission before the first resolves (e.g. a stray recomposition re-invoking the
        // same callback) must not fire a second network call for the identical in-flight query.
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
    }

    @Test
    fun `submitting the same query again after it completes does not duplicate the request`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
    }

    @Test
    fun `a newer search cannot be overwritten by a slower older request`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("ha")
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("puur")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        // The older ("hagelslag") request resolves after the newer ("puur") one.
        source.resolve("puur", ProductSearchResult.Found(listOf(hit(barcode = "puur-barcode"))))
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "hagelslag-barcode"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.hits.size)
        assertEquals("puur-barcode", viewModel.state.value.hits.first().barcode)
    }

    @Test
    fun `a failure is not shown as no matches`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LookupError.SERVER, viewModel.state.value.error)
        assertFalse(viewModel.state.value.noMatches)
    }

    @Test
    fun `no matches is distinct from not yet searched`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("zzzzz")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("zzzzz", ProductSearchResult.NoMatches)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.noMatches)
    }

    @Test
    fun `clearing the query drops stale hits without searching`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("")

        assertEquals(0, viewModel.state.value.hits.size)
        assertEquals(1, source.callCount)
    }

    @Test
    fun `editing the query after submitting discards the in-flight response`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("bread")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        // The user keeps typing. The "bread" request is still in flight, and the new text is long
        // enough that its own live search is now running too.
        viewModel.onQueryChanged("bread wholegrain")
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("bread", ProductSearchResult.Found(listOf(hit(barcode = "stale-barcode"))))
        dispatcher.scheduler.advanceUntilIdle()

        // The abandoned query's hits never appear, whatever order the responses arrive in.
        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
        assertEquals("bread wholegrain", viewModel.state.value.query)
    }

    // ---- Result transitions (§7): what survives an edit, and what must not ---------------------

    @Test
    fun `editing keeps the previous results visible while a replacement is on its way`() = runTest {
        // Changed behaviour, deliberately. Blanking the list on every keystroke made the screen
        // flash empty-then-spinner-then-results for each character typed. The old list stays,
        // flagged by `searching`, and is replaced atomically when the newer result lands.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag puur")

        assertEquals(1, viewModel.state.value.hits.size)
        assertTrue("the stale list must be labelled as refreshing", viewModel.state.value.searching)
        // Verdicts about the previous query go immediately even so: "no products found for X" and a
        // network error are statements about a search that finished, and attributing them to text
        // that has not been searched would be a false answer rather than a stale one.
        assertFalse(viewModel.state.value.noMatches)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `editing below the minimum length drops the previous results at once`() = runTest {
        // Nothing is coming to replace them, so keeping them would present the old list as the
        // answer to text that will never be searched.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("ha")

        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `a newer result replaces the old list in a single state write`() = runTest {
        // No intermediate empty list between the two result sets: an observer must never see the
        // screen pass through "no results" on the way from one query's hits to another's.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "old"))))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        // Still the old list, while the new query is in flight.
        assertEquals("old", viewModel.state.value.hits.single().barcode)

        source.resolve("hagelslag puur", ProductSearchResult.Found(listOf(hit(barcode = "new"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("new", viewModel.state.value.hits.single().barcode)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `editing away and back resubmits rather than being deduped`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        // A -> B -> A. The results for A were cleared during the edit to B, so retyping A and
        // submitting must genuinely search again rather than being swallowed as a duplicate.
        viewModel.onQueryChanged("puur")
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, source.callCount)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `editing to a query that cannot be searched ends the spinner`() = runTest {
        // A running search whose query the user has backspaced away must not leave a spinner up for
        // a request nobody is waiting on. (When the new text *is* searchable the spinner stays, by
        // design — it now belongs to the new search rather than the abandoned one.)
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("bread")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.searching)

        viewModel.onQueryChanged("br")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `typing back to a previous query abandons the one in between`() = runTest {
        // A -> B -> A. B's search is running when the user backspaces to A. B must be abandoned:
        // its answer can never appear, and A is searched again — A's results were dropped when B
        // was typed, so there is nothing on screen to reuse and re-asking is the honest thing.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        assertEquals(listOf("hagelslag", "hagelslag puur"), source.callsInOrder)

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A2"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("hagelslag", "hagelslag puur", "hagelslag"), source.callsInOrder)
        assertEquals("A2", viewModel.state.value.hits.single().barcode)
        assertFalse(viewModel.state.value.searching)

        // B answers after everything settled. It must not surface.
        source.resolve("hagelslag puur", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("A2", viewModel.state.value.hits.single().barcode)
    }

    // ---- G/H. Stale-result and stale-error protection ------------------------------------------

    @Test
    fun `an older response cannot replace a newer one that already landed`() = runTest {
        // The mandatory case. A -> B, B completes, A completes later. This is the ordinary shape of
        // live search: each keystroke may leave a request in flight, and the network is free to
        // answer them in any order it likes.
        //
        // Run against the UNCANCELLABLE source deliberately. With a cancellation-honouring fake this
        // test passes on flatMapLatest alone and proves nothing about the staleness guard — verified
        // by negative control, where deleting the generation check left this case green.
        val source = UncancellableSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("chocolate")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("chocolate bar")
        dispatcher.scheduler.advanceTimeBy(past())
        assertEquals(listOf("chocolate", "chocolate bar"), source.callsInOrder)

        source.resolve("chocolate bar", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("B", viewModel.state.value.hits.single().barcode)

        // A answers last, and must be discarded rather than displayed.
        source.resolve("chocolate", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("B", viewModel.state.value.hits.single().barcode)
        assertEquals("chocolate bar", viewModel.state.value.query)
    }

    @Test
    fun `an older failure cannot overwrite a newer query's results`() = runTest {
        // The same hazard wearing a different hat, and the more damaging one: an error replaces the
        // whole list with a recovery panel, so a late failure for an abandoned query would wipe
        // good results off the screen and invite a retry of the wrong thing.
        val source = UncancellableSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("coffee")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("coffee beans")
        dispatcher.scheduler.advanceTimeBy(past())

        source.resolve("coffee beans", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("coffee", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("a stale failure must not surface", viewModel.state.value.error)
        assertEquals("B", viewModel.state.value.hits.single().barcode)
    }

    @Test
    fun `an older empty result cannot overwrite a newer query's results`() = runTest {
        val source = UncancellableSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("zzzzz")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())

        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("zzzzz", ProductSearchResult.NoMatches)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse("a stale miss must not surface", viewModel.state.value.noMatches)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `a response for a query the user has cleared never lands`() = runTest {
        val source = UncancellableSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())

        viewModel.onQueryChanged("")
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchUiState(), viewModel.state.value)
    }

    // ---- I. Error recovery ---------------------------------------------------------------------

    @Test
    fun `a failed query does not poison the next one`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("coffee")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("coffee", ProductSearchResult.Failed(LookupError.OFFLINE))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LookupError.OFFLINE, viewModel.state.value.error)

        viewModel.onQueryChanged("coffee beans")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("coffee beans", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `editing after a failure clears the error before the new result arrives`() = runTest {
        // The error belongs to the query that produced it. Leaving the recovery panel up while the
        // user types their next query would offer a retry of something they have moved on from.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("coffee")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("coffee", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("coffee beans")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `retry re-runs the same query even though it already ran`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        // The retry is SCHEDULED, not sent: it is as bound by the shared request interval as
        // typing is, which is what stops repeated taps hammering a failing endpoint.
        dispatcher.scheduler.runCurrent()
        assertEquals("a retry must not bypass the request budget", 1, source.callCount)

        dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, source.callCount)
        assertNull(viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `retry runs the current query, not the one that failed`() = runTest {
        // Retry goes through the same pipeline as everything else, so it re-reads the field rather
        // than replaying a remembered request. If the user edited the query while the error was up,
        // retrying the old one would search something they can no longer see.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("coffee")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("coffee", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("coffee beans")
        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("coffee", "coffee beans"), source.callsInOrder)
    }

    // ---- J. Empty results ----------------------------------------------------------------------

    @Test
    fun `no matches is not reported while the search is still running`() = runTest {
        // "No products found" must mean a completed search matched nothing — never "the debounce is
        // running" or "the request is out". Reporting it early tells the user their product does not
        // exist while the app is still looking for it.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("zzzzz")
        dispatcher.scheduler.runCurrent()
        assertFalse("not during the debounce", viewModel.state.value.noMatches)

        dispatcher.scheduler.advanceTimeBy(past())
        assertFalse("not while the request is in flight", viewModel.state.value.noMatches)
        assertTrue(viewModel.state.value.searching)

        source.resolve("zzzzz", ProductSearchResult.NoMatches)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("only once the search completed", viewModel.state.value.noMatches)
        assertFalse(viewModel.state.value.searching)
    }

    // ---- K. Short-query feedback ----------------------------------------------------------------

    @Test
    fun `passive typing below the minimum length is not reported as a refusal`() = runTest {
        // The distinction this pass had to preserve: typing "ha" on the way to "hagelslag" is not a
        // mistake, and flagging it would put an error beside every query mid-word — and, as a live
        // region, announce it to a TalkBack user on every keystroke.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("h")
        dispatcher.scheduler.advanceTimeBy(past())
        assertFalse(viewModel.state.value.queryTooShort)

        viewModel.onQueryChanged("ha")
        dispatcher.scheduler.advanceTimeBy(past())
        assertFalse(viewModel.state.value.queryTooShort)
    }

    @Test
    fun `a manual submission below the minimum length still says why`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("ha")
        dispatcher.scheduler.advanceTimeBy(past())
        assertFalse(viewModel.state.value.queryTooShort)

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.queryTooShort)
        assertEquals(0, source.callCount)
    }

    // ---- N. Request economy at the chosen debounce ---------------------------------------------

    @Test
    fun `an ordinary typing pace still costs one request per word`() = runTest {
        // The constraint that decides the debounce value. Open Food Facts' search endpoint allows
        // 10 reads/min/IP, shared by everyone behind one address, so the debounce must outlast the
        // gap between keystrokes at a realistic pace — 180ms is roughly 65 words per minute, which
        // is faster than most people type a product name into a phone.
        //
        // Pinned as a *count*, not as a comparison against the constant, so lowering the constant
        // past the point where a typed word leaks requests fails here rather than silently
        // doubling the app's search traffic.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        "hagelslag".foldIndexed("") { index, acc, ch ->
            val next = acc + ch
            viewModel.onQueryChanged(next)
            if (index < "hagelslag".lastIndex) dispatcher.scheduler.advanceTimeBy(180)
            next
        }
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(1, source.callCount)
        assertEquals(listOf("hagelslag"), source.callsInOrder)
    }

    @Test
    fun `a two-word query typed with a thinking pause costs two requests at most`() = runTest {
        // The realistic worst case for a live search: someone types a word, pauses to think, then
        // adds a second. Two requests for a two-word query is the honest cost of live search and
        // stays well inside the budget; anything more means the debounce is too short.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)

        listOf("h", "ha", "hag", "hage", "hagel").forEach {
            viewModel.onQueryChanged(it)
            dispatcher.scheduler.advanceTimeBy(180)
        }
        // The pause that makes the first request go out.
        dispatcher.scheduler.advanceTimeBy(past())
        listOf("hagel ", "hagel p", "hagel pu", "hagel puu", "hagel puur").forEach {
            viewModel.onQueryChanged(it)
            dispatcher.scheduler.advanceTimeBy(180)
        }
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(2, source.callCount)
        assertEquals(listOf("hagel", "hagel puur"), source.callsInOrder)
    }

    // ---- M. Refresh failure over usable results ------------------------------------------------

    @Test
    fun `a refresh that fails keeps the results it was replacing`() = runTest {
        // The defect this pass exists to fix. A live refresh is a *background* operation over a list
        // the user is already reading and can already tap. Discarding that list because one request
        // came back 503 costs them everything they had for a failure they did not ask for — and on
        // an endpoint that answers 503 while healthy, it happens mid-word.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("A", viewModel.state.value.hits.single().barcode)
        assertEquals(LookupError.SERVER, viewModel.state.value.error)
        assertTrue(
            "the failure must be marked as a refresh so the screen shows a banner, not a panel",
            viewModel.state.value.refreshFailed,
        )
        assertFalse("the progress line must stop", viewModel.state.value.searching)
    }

    @Test
    fun `a first search that fails still gets the full error state`() = runTest {
        // The other half of the same rule. With nothing on screen to preserve there is no reason to
        // soften the failure into a banner over an empty list — the recovery panel and its two ways
        // out are the whole content of the screen at that point.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Failed(LookupError.OFFLINE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LookupError.OFFLINE, viewModel.state.value.error)
        assertFalse(
            "with no results to keep, this is a full failure",
            viewModel.state.value.refreshFailed,
        )
        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
    }

    @Test
    fun `a refresh that succeeds after one failed clears the banner atomically`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.refreshFailed)

        viewModel.retry()
        // Scheduled, then released by the shared interval — a retry cannot jump the budget.
        dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
        source.resolve("hagelslag puur", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("B", viewModel.state.value.hits.single().barcode)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `a refresh that matches nothing becomes a true no-results state`() = runTest {
        // The kept list is kept only while an answer is still coming. An answer that says "nothing
        // matches" IS the answer, so the old hits go — leaving them would present another query's
        // products as the result for text that genuinely has none.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag zzzz")
        dispatcher.scheduler.advanceTimeBy(past())
        // Still the old list while the answer is in flight — no "no results" flash on the way.
        assertEquals("A", viewModel.state.value.hits.single().barcode)
        assertFalse(viewModel.state.value.noMatches)

        source.resolve("hagelslag zzzz", ProductSearchResult.NoMatches)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.noMatches)
        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
    }

    @Test
    fun `retrying a failed refresh keeps the results visible and sends one request`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        // Enough virtual time has passed in the setup above that the budget already permits another
        // request, so this retry goes out at once — which is correct: the governor delays a retry
        // only when the budget is actually spent. The pacing case is
        // `repeated Retry taps cannot exceed the request budget` below.
        viewModel.retry()
        dispatcher.scheduler.runCurrent()

        // Exactly ONE extra request, and the list it is refreshing is not blanked.
        assertEquals(listOf("hagelslag", "hagelslag puur", "hagelslag puur"), source.callsInOrder)
        assertEquals("A", viewModel.state.value.hits.single().barcode)
        assertTrue(viewModel.state.value.searching)
        assertNull("the banner goes while the retry runs", viewModel.state.value.error)
    }

    @Test
    fun `editing after a failed refresh drops the banner and keeps the list`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hagelslag puurr")

        // The failure belonged to the previous query, so it goes at once. The list it was covering
        // is still the newest thing anyone has seen, and a replacement is on its way, so it stays.
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshFailed)
        assertEquals("A", viewModel.state.value.hits.single().barcode)
        assertTrue(viewModel.state.value.searching)
    }

    @Test
    fun `shortening the query after a failed refresh clears results and banner together`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit(barcode = "A"))))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("ha")

        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshFailed)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `a stale failure cannot raise a banner over a newer query's results`() = runTest {
        // The staleness guard has to cover the new flag too: a late failure for an abandoned query
        // must not put a "couldn't refresh" banner over results that refreshed perfectly well.
        val source = UncancellableSearchSource()
        val viewModel = viewModelFor(source)

        viewModel.onQueryChanged("coffee")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("coffee beans")
        dispatcher.scheduler.advanceTimeBy(past())

        source.resolve("coffee beans", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("coffee", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshFailed)
        assertEquals("B", viewModel.state.value.hits.single().barcode)
    }

    @Test
    fun `clearing the query after a failed refresh restores the initial state`() = runTest {
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onQueryChanged("hagelslag puur")
        dispatcher.scheduler.advanceTimeBy(past())
        source.resolve("hagelslag puur", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchUiState(), viewModel.state.value)
    }

    // ---- O. Request accounting: what Open Food Facts actually sees -----------------------------

    /** Resolves everything at once, so a test can measure request COUNTS without gating anything. */
    private class AlwaysFoundSource(
        private val result: ProductSearchResult = ProductSearchResult.Found(emptyList()),
    ) : ProductSearchSource {
        val callsInOrder = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            callsInOrder.add(terms)
            return result
        }
    }

    @Test
    fun `sustained typing for a minute cannot exceed the shared request budget`() {
        // THE headline claim of this pass, measured rather than argued. A user typing continuously
        // for a minute — the worst case any UI can produce — must not be able to push this client
        // past Open Food Facts' documented search budget of 10/min/IP.
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)

            // A keystroke every 120 ms for 60 s: ~500 edits, every one a distinct valid query.
            var i = 0
            val start = dispatcher.scheduler.currentTime
            while (dispatcher.scheduler.currentTime - start < 60_000) {
                viewModel.onQueryChanged("chocolate" + "x".repeat(i % 40))
                dispatcher.scheduler.advanceTimeBy(120)
                i++
            }
            dispatcher.scheduler.advanceUntilIdle()

            // ~500 edits must still collapse to a handful of requests. The bound is the SETTLE
            // wait, not a budget: continuous typing never settles, so almost nothing is sent.
            //
            // The old assertion here was "< 10", against Open Food Facts' 10/min search budget.
            // That budget no longer applies to this path (2026-08-28): the ViewModel now paces the
            // PRIMARY provider, which documents no such limit, and the legacy 10/min ceiling is
            // enforced one layer down in GovernedProductSearch — which is where it is now asserted.
            // Keeping the old number here would have been asserting one provider's limit against a
            // different provider's traffic.
            assertTrue(
                "typing for a minute produced ${source.callsInOrder.size} requests",
                source.callsInOrder.size <= MAX_REQUESTS_PER_MINUTE_OF_CONTINUOUS_TYPING,
            )
        }
    }

    @Test
    fun `each deliberate pause costs at most one request`() {
        // The shape that broke the original design: a pause longer than the settle delay meant
        // "send another request", so a thoughtful typist produced one per pause plus duplicates.
        //
        // Renamed and re-aimed on 2026-08-28. It used to assert OFF's 9-per-minute ceiling, which
        // this path no longer speaks to — the ViewModel paces the PRIMARY provider, and twenty
        // genuinely distinct queries typed over a minute SHOULD each be searched: that is the
        // feature. What must still hold is that a pause never costs more than one request, which is
        // what rules out the duplicate-per-pause defect. The legacy 10/min budget is asserted
        // against the legacy provider in GovernedProductSearchTest.
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)

            val pauses = 20
            repeat(pauses) { n ->
                viewModel.onQueryChanged("chocolate bar number $n")
                // Well past the settle wait every time — 20 deliberate pauses in ~60 s.
                dispatcher.scheduler.advanceTimeBy(3_000)
            }
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                "$pauses pauses produced ${source.callsInOrder.size} requests",
                source.callsInOrder.size <= pauses,
            )
            // Distinct queries only — a pause must never re-send one already answered.
            assertEquals(source.callsInOrder.size, source.callsInOrder.distinct().size)
        }
    }

    @Test
    fun `only the newest query is sent when the budget frees up`() {
        // Coalescing, asserted as the absence of a queue. Four queries are typed while the budget
        // is spent; exactly one request follows, for the LAST of them. cho/choc/choco must never
        // appear — a queue that drained in order would spend the whole budget on stale prefixes.
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            assertEquals(listOf("chocolate"), source.callsInOrder)

            // The budget is now spent. Four more queries during the cooldown.
            listOf("chocolate a", "chocolate ab", "chocolate abc", "chocolate abcd").forEach {
                viewModel.onQueryChanged(it)
                dispatcher.scheduler.advanceTimeBy(200)
            }
            dispatcher.scheduler.advanceTimeBy(past())

            assertEquals(
                "only the newest query may follow a cooldown",
                listOf("chocolate", "chocolate abcd"),
                source.callsInOrder,
            )
        }
    }

    @Test
    fun `pressing Enter during a cooldown queues rather than sending`() {
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            // Past the settle wait only, so the first request has gone out and its cooldown is
            // still running. `past()` would advance beyond the interval too and there would be no
            // cooldown left to test against.
            dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 1)
            assertEquals(listOf("chocolate"), source.callsInOrder)

            viewModel.onQueryChanged("chocolate bar")
            viewModel.search()
            dispatcher.scheduler.runCurrent()

            assertEquals("Enter must not jump the budget", listOf("chocolate"), source.callsInOrder)
            assertTrue(viewModel.state.value.awaitingRemotePermit)

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals(listOf("chocolate", "chocolate bar"), source.callsInOrder)
        }
    }

    @Test
    fun `repeated Enter cannot exceed the request budget`() {
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            viewModel.onQueryChanged("chocolate bar")

            // Twenty taps in four seconds — a frustrated user on a slow connection.
            repeat(20) {
                viewModel.search()
                dispatcher.scheduler.advanceTimeBy(200)
            }
            dispatcher.scheduler.advanceTimeBy(past())

            assertEquals(
                "twenty Enter taps must cost one request",
                listOf("chocolate", "chocolate bar"),
                source.callsInOrder,
            )
        }
    }

    @Test
    fun `repeated Retry taps cannot exceed the request budget`() {
        runTest {
            val source = AlwaysFoundSource(ProductSearchResult.Failed(LookupError.SERVER))
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            // Settle only — the cooldown from that first request must still be running for the
            // taps below to be testing anything.
            dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 1)
            assertEquals(1, source.callsInOrder.size)

            // Twenty taps on Try again, spread over four seconds — inside the 7 s interval.
            repeat(20) {
                viewModel.retry()
                dispatcher.scheduler.advanceTimeBy(200)
            }

            assertEquals(
                "twenty Retry taps during a cooldown must add no requests",
                1,
                source.callsInOrder.size,
            )

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals("and exactly one when the budget allows", 2, source.callsInOrder.size)
        }
    }

    @Test
    fun `two screens share one budget`() {
        // Home's inline search and the search screen are separate ViewModels. A per-ViewModel
        // cooldown would let the two most-likely-consecutive screens spend the budget twice over,
        // which is precisely the traffic the endpoint was refusing.
        runTest {
            val home = AlwaysFoundSource()
            val screen = AlwaysFoundSource()
            val homeViewModel = viewModelFor(home)
            val screenViewModel = viewModelFor(screen)

            homeViewModel.onQueryChanged("chocolate")
            // Settle only, so Home's request has gone out and its cooldown is still in force —
            // which is the state the search screen must be held by.
            dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 1)
            assertEquals(1, home.callsInOrder.size)

            // The user opens the search screen and types immediately.
            screenViewModel.onQueryChanged("gouda")
            screenViewModel.search()
            dispatcher.scheduler.runCurrent()

            assertEquals(
                "the second screen must be held by the first screen's request",
                0,
                screen.callsInOrder.size,
            )

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals(listOf("gouda"), screen.callsInOrder)
        }
    }

    // ---- P. Rate limiting ----------------------------------------------------------------------

    @Test
    fun `a rate limit is not shown as a database outage`() {
        // The user-visible half of the fix. A 429 is a wait this app is already handling, not a
        // dead end, so it must not raise the full recovery panel — whose actions answer a question
        // ("the database has nothing for you") that was not asked.
        runTest {
            val source = AlwaysFoundSource(
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 30_000),
            )
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())

            assertNull("a rate limit is never a full-screen failure", viewModel.state.value.error)
            assertTrue(viewModel.state.value.rateLimited)
            assertTrue("the queued query is still coming", viewModel.state.value.searching)
        }
    }

    @Test
    fun `a rate limit backs off for the server's stated retry-after`() {
        runTest {
            val source = AlwaysFoundSource(
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 30_000),
            )
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            assertEquals(1, source.callsInOrder.size)

            // Long past the ordinary interval, still held: the server asked for longer.
            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals("the server's backoff outlasts our own interval", 1, source.callsInOrder.size)

            dispatcher.scheduler.advanceTimeBy(30_000)
            assertEquals("and the queued query resumes on its own", 2, source.callsInOrder.size)
        }
    }

    @Test
    fun `a rate limit with no retry-after uses the fallback backoff`() {
        runTest {
            val source = AlwaysFoundSource(
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = null),
            )
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            assertEquals(1, source.callsInOrder.size)

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals(1, source.callsInOrder.size)

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.RATE_LIMIT_FALLBACK_BACKOFF_MS)
            assertEquals(2, source.callsInOrder.size)
        }
    }

    @Test
    fun `a sustained rate limit stops rather than retrying forever`() {
        // The bound that stops the automatic resume becoming a retry loop against the endpoint
        // least able to absorb one. One automatic retry; a second refusal is reported as an
        // actionable failure instead of an endless progress line.
        runTest {
            val source = AlwaysFoundSource(
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 10_000),
            )
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            dispatcher.scheduler.advanceTimeBy(300_000)

            assertEquals(
                "one automatic resume, then stop — never an unbounded loop",
                2,
                source.callsInOrder.size,
            )
            assertEquals(LookupError.RATE_LIMITED, viewModel.state.value.error)
            assertFalse(viewModel.state.value.searching)
        }
    }

    @Test
    fun `typing during a server backoff replaces the queued query`() {
        runTest {
            val source = AlwaysFoundSource(
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 30_000),
            )
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())

            viewModel.onQueryChanged("chocolate bar")
            dispatcher.scheduler.advanceTimeBy(1_000)
            viewModel.onQueryChanged("gouda cheese")
            dispatcher.scheduler.advanceTimeBy(30_000 + past())

            assertEquals(
                "only the newest query survives a backoff",
                listOf("chocolate", "gouda cheese"),
                source.callsInOrder,
            )
        }
    }

    @Test
    fun `a stale rate limit still records the backoff but cannot touch the screen`() {
        // Two separate facts about one late 429, and they must be handled differently: the server's
        // refusal is a fact about the SHARED BUDGET and is always honoured, while its verdict on an
        // abandoned query is stale and must never reach state.
        runTest {
            val source = UncancellableSearchSource()
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            viewModel.onQueryChanged("chocolate bar")
            dispatcher.scheduler.advanceTimeBy(past())

            source.resolve("chocolate bar", ProductSearchResult.Found(listOf(hit(barcode = "B"))))
            dispatcher.scheduler.advanceUntilIdle()
            source.resolve(
                "chocolate",
                ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 30_000),
            )
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("B", viewModel.state.value.hits.single().barcode)
            assertNull("a stale 429 must not surface", viewModel.state.value.error)
            assertFalse(viewModel.state.value.rateLimited)
            // But the budget was genuinely refused, so the block is in force regardless.
            assertFalse(governor.permitsRequestAt(dispatcher.scheduler.currentTime))
        }
    }

    // ---- P2. The governor must never fail open (P1 §6) ------------------------------------------

    /**
     * The previous wait loop capped its re-checks at `MAX_PERMIT_WAIT_ROUNDS` (8) and, once that
     * cap was reached, fell through to `emit(request)` regardless of whether the governor still
     * reported a positive wait. A rate limiter that eventually sends anyway is not a rate limiter.
     *
     * This drives the governor's block being externally extended well past what 8 rounds of the
     * old loop would have covered, and asserts zero network calls happen until the block has
     * genuinely, naturally lifted — never as a side effect of having waited "long enough" in round
     * terms.
     */
    @Test
    fun `the governor block is honoured however many times it is extended, never overridden by a round count`() {
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)

            // Block the governor for longer than the settle wait, so it is still active at the exact
            // moment the request first reaches it (right after the settle delay elapses) — a shorter
            // block would already have expired by then, and there would be nothing here for the
            // extensions below to hold back.
            governor.recordRateLimited(
                dispatcher.scheduler.currentTime,
                retryAfterMs = SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 200,
            )

            viewModel.onQueryChanged("chocolate")
            // Past the settle wait — the request now reaches the governor for the first time, which
            // must still be blocked (the backoff above outlasts this by 200ms).
            dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 1)
            assertEquals("precondition: nothing has been sent yet", 0, source.callsInOrder.size)

            // Re-extend the block every 50ms, each extension comfortably longer (200ms) than the gap
            // to the next one, so the pipeline's wait loop keeps re-checking and finding a fresh
            // positive wait rather than ever seeing zero. A round only advances once its own
            // `delay(wait)` elapses — with `wait` staying around 150-200ms and 40 extensions spanning
            // 2000ms of virtual time, that is comfortably more re-check rounds than the old 8-round
            // cap allowed, so a capped loop would have fallen through and sent the request well
            // before this finishes.
            repeat(40) {
                governor.recordRateLimited(dispatcher.scheduler.currentTime, retryAfterMs = 200)
                dispatcher.scheduler.advanceTimeBy(50)
            }

            assertEquals(
                "continuously re-extending the block for far longer than 8 re-check rounds would " +
                    "cover must still hold the request — a round-count cap would have let it " +
                    "through long before this point",
                0,
                source.callsInOrder.size,
            )

            // Let the block actually expire, with nothing further extending it.
            dispatcher.scheduler.advanceTimeBy(201)
            assertEquals("the request goes out once the block genuinely lifts", 1, source.callsInOrder.size)
        }
    }

    // ---- P3. The 429 retry budget is per-query, not per-ViewModel (P1 §7) ------------------------

    /**
     * A fake whose every call returns 429, so the ViewModel's own one-automatic-resume rule is what
     * eventually stops the retries — this file's fixture for exercising that rule directly, distinct
     * from [AlwaysFoundSource] used with a single canned rate-limit result.
     */
    private class AlwaysRateLimitedSource : ProductSearchSource {
        val callsInOrder = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            callsInOrder.add(terms)
            return ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 5_000)
        }
    }

    /**
     * The exact scenario the old `resumedAfterRateLimit` flag got wrong: query A consumes its one
     * automatic resume, the user moves on to an unrelated query B before A's story is even over, and
     * B's own first 429 must still get its own legitimate resume — because the flag belonged to the
     * ViewModel, not to A, a stale `true` left over from A silently denied B a resume it was entitled
     * to.
     */
    @Test
    fun `switching to a new query after a rate limit gives the new query its own automatic resume`() {
        runTest {
            val source = AlwaysRateLimitedSource()
            val viewModel = viewModelFor(source)

            // Query A: first request, then its one automatic resume — both 429. That consumes A's
            // (previously: the ViewModel's) retry allowance.
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            dispatcher.scheduler.advanceTimeBy(5_001) // A's own backoff, its automatic resume fires
            assertEquals(
                "precondition: A's request and its one automatic resume have both gone out",
                listOf("chocolate", "chocolate"),
                source.callsInOrder,
            )
            assertEquals(
                "precondition: A's retry was refused again and reported as a finished failure",
                LookupError.RATE_LIMITED,
                viewModel.state.value.error,
            )

            // The user moves on. B is an entirely different query.
            viewModel.onQueryChanged("gouda")
            dispatcher.scheduler.advanceTimeBy(past())
            assertEquals(
                "B's first request must go out",
                listOf("chocolate", "chocolate", "gouda"),
                source.callsInOrder,
            )

            // B's first 429. It must get its OWN automatic resume — not be denied one because A
            // already spent the (old, ViewModel-scoped) allowance.
            dispatcher.scheduler.advanceTimeBy(5_001)
            assertEquals(
                "B must receive its own legitimate automatic resume, not inherit A's exhausted one",
                listOf("chocolate", "chocolate", "gouda", "gouda"),
                source.callsInOrder,
            )
        }
    }

    /** B's retry budget is still bounded to one — the per-query fix must not become unbounded. */
    @Test
    fun `a sustained rate limit on the new query still stops after its own one resume`() {
        runTest {
            val source = AlwaysRateLimitedSource()
            val viewModel = viewModelFor(source)

            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            dispatcher.scheduler.advanceTimeBy(5_001)

            viewModel.onQueryChanged("gouda")
            dispatcher.scheduler.advanceTimeBy(past())
            dispatcher.scheduler.advanceTimeBy(5_001) // B's own automatic resume
            dispatcher.scheduler.advanceTimeBy(5_001) // if this fired a THIRD B request, the fix over-corrected

            assertEquals(
                "B gets exactly one automatic resume, never an unbounded retry loop",
                listOf("chocolate", "chocolate", "gouda", "gouda"),
                source.callsInOrder,
            )
            assertEquals(LookupError.RATE_LIMITED, viewModel.state.value.error)
        }
    }

    /** Explicit Search is subject to the same governor as everything else — no bypass. */
    @Test
    fun `an explicit search submission still cannot bypass the governor`() {
        runTest {
            val source = AlwaysFoundSource()
            val viewModel = viewModelFor(source)

            // The first query needs only the settle wait — the governor has nothing to enforce yet.
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS + 1)
            assertEquals("precondition: chocolate's request has gone out", 1, source.callsInOrder.size)

            // Immediately submit a second, different query via the explicit action, well inside the
            // interval chocolate's own send just started.
            viewModel.onQueryChanged("gouda")
            viewModel.search()
            dispatcher.scheduler.runCurrent()

            assertEquals(
                "an explicit submission must still wait for the shared interval",
                1,
                source.callsInOrder.size,
            )

            dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS + 1)
            assertEquals(listOf("chocolate", "gouda"), source.callsInOrder)
        }
    }

    // ---- Q. Local narrowing ---------------------------------------------------------------------

    private fun namedHit(barcode: String, name: String) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = "De Ruijter",
        packageQuantity = "390 gram",
        carbsPer100 = BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    @Test
    fun `extending a query narrows the existing results instantly`() {
        // The responsiveness half of the pass. The remote refresh is a second away at best, so
        // typing has to be answered from what is already on screen or the app feels frozen.
        runTest {
            val source = FakeSearchSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("choc")
            dispatcher.scheduler.advanceTimeBy(past())
            source.resolve(
                "choc",
                ProductSearchResult.Found(
                    listOf(
                        namedHit("1", "Chocolate bar"),
                        namedHit("2", "Chocolade hagelslag"),
                    ),
                ),
            )
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("chocolate")
            // No time advanced at all: this must be instant and must not involve the network.
            dispatcher.scheduler.runCurrent()

            assertEquals(listOf("1"), viewModel.state.value.hits.map { it.barcode })
            assertTrue(viewModel.state.value.narrowedLocally)
            assertEquals("narrowing must not touch the network", 1, source.callCount)
        }
    }

    @Test
    fun `a divergent query does not present the previous results as its own`() {
        // The rule that keeps narrowing honest. "gouda" did not grow out of "chocolate", so the
        // chocolate hits are not a partial answer to it — they are a wrong one.
        runTest {
            val source = FakeSearchSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            source.resolve(
                "chocolate",
                ProductSearchResult.Found(listOf(namedHit("1", "Chocolate bar"))),
            )
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("gouda")
            dispatcher.scheduler.runCurrent()

            assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
            // Neutral pending, not a verdict: nothing has been searched for "gouda" yet.
            assertFalse(viewModel.state.value.noMatches)
            assertNull(viewModel.state.value.error)
            assertTrue(viewModel.state.value.searching)
        }
    }

    @Test
    fun `shortening a query does not present the narrower results as complete`() {
        // "chocolate" results are a SUBSET of what "choc" matches, so showing them for "choc" would
        // present a narrow answer as a complete one.
        runTest {
            val source = FakeSearchSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            source.resolve(
                "chocolate",
                ProductSearchResult.Found(listOf(namedHit("1", "Chocolate bar"))),
            )
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("choc")
            dispatcher.scheduler.runCurrent()

            assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
            assertFalse(viewModel.state.value.noMatches)
        }
    }

    @Test
    fun `a narrowing that matches nothing locally keeps the wider list rather than blanking`() {
        // Local matching is a substring test; the remote search is not. "hagelslag" ->
        // "hagelslag puur" is an ordinary refinement no product name contains literally, so an
        // empty local result says nothing about the remote answer — and blanking would reintroduce
        // the empty-then-results flicker, and read as "no results" for an unsearched query.
        runTest {
            val source = FakeSearchSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("hagelslag")
            dispatcher.scheduler.advanceTimeBy(past())
            source.resolve(
                "hagelslag",
                ProductSearchResult.Found(listOf(namedHit("1", "Chocoladehagel puur"))),
            )
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("hagelslag zzzz")
            dispatcher.scheduler.runCurrent()

            assertEquals(listOf("1"), viewModel.state.value.hits.map { it.barcode })
            assertFalse(viewModel.state.value.noMatches)
            assertTrue(viewModel.state.value.searching)
        }
    }

    @Test
    fun `dropping below the minimum length clears the narrowed list at once`() {
        runTest {
            val source = FakeSearchSource()
            val viewModel = viewModelFor(source)
            viewModel.onQueryChanged("chocolate")
            dispatcher.scheduler.advanceTimeBy(past())
            source.resolve(
                "chocolate",
                ProductSearchResult.Found(listOf(namedHit("1", "Chocolate bar"))),
            )
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onQueryChanged("ch")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
            assertFalse(viewModel.state.value.searching)
            assertEquals("nothing may be queued below the minimum", 1, source.callCount)
        }
    }

    // ---- L. Lifecycle --------------------------------------------------------------------------

    @Test
    fun `a pending debounce does not fire after the ViewModel is cleared`() = runTest {
        // Leaving the screen mid-debounce must not send the request afterwards, nor write into a
        // state nobody is collecting. The pipeline lives on viewModelScope, so onCleared ends it.
        val source = FakeSearchSource()
        val viewModel = viewModelFor(source)
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)

        viewModel.viewModelScope.coroutineContext.cancelChildren()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    private companion object {
        /**
         * What a minute of **continuous** typing may cost the primary provider.
         *
         * Not a rate limit. Continuous typing never settles, so the settle wait alone keeps this
         * near zero; the allowance exists only so the test states a bound rather than an exact
         * number that would break on any timing tweak. The primary documents no request budget and
         * was measured serving twelve back-to-back requests without throttling, so the number here
         * is a sanity ceiling, not a contract with a server.
         *
         * The legacy provider's real 10/min budget is asserted where it is now enforced —
         * `GovernedProductSearchTest`.
         */
        const val MAX_REQUESTS_PER_MINUTE_OF_CONTINUOUS_TYPING = 12
    }
}
