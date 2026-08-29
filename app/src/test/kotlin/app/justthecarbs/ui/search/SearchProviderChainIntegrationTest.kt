package app.justthecarbs.ui.search

import app.justthecarbs.domain.CachedProductSearch
import app.justthecarbs.domain.FallbackProductSearch
import app.justthecarbs.domain.GovernedProductSearch
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * The ViewModel driving the **real provider chain** (2026-08-28 migration).
 *
 * The unit tests either side of this one each prove half the story: `SearchViewModelTest` pins
 * scheduling against a single fake source, and `FallbackProductSearchTest` pins the chain against
 * direct calls. Neither can see what this one measures — what a *user typing* actually costs each
 * provider, and whether staleness protection still holds when a query's answer arrives from the
 * fallback rather than the primary.
 *
 * Everything runs on virtual time; there is no real delay and no network anywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchProviderChainIntegrationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class RecordingSource(
        private val answer: (String) -> ProductSearchResult,
    ) : ProductSearchSource {
        val calls = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            calls += terms
            return answer(terms)
        }
    }

    private fun found(barcode: String) = ProductSearchResult.Found(
        listOf(
            ProductSearchHit(
                barcode = barcode,
                name = "Product $barcode",
                brand = null,
                packageQuantity = null,
                carbsPer100 = BigDecimal("10"),
                basis = NutritionBasis.PER_100_G,
                imageUrl = null,
            ),
        ),
    )

    /**
     * The production wiring, on the test's virtual clock.
     *
     * [cached] mirrors `AppContainer`: the cache wraps the **primary only**, inside the chain. Left
     * at its default the primary is uncached, which is how the pre-existing cases below were written
     * and how they must stay — several of them repeat a query deliberately to count requests, and a
     * cache would silently answer the second one and make those assertions measure nothing.
     */
    private fun chainOf(
        primary: ProductSearchSource,
        legacy: ProductSearchSource,
        cached: Boolean = false,
    ): ProductSearchSource {
        val clock = { dispatcher.scheduler.currentTime }
        return FallbackProductSearch(
            primary = if (cached) CachedProductSearch(primary, clock) else primary,
            fallback = GovernedProductSearch(legacy, RemoteSearchGovernor(nowMs = clock), clock),
        )
    }

    private fun viewModelFor(source: ProductSearchSource) = SearchViewModel(
        source,
        RemoteSearchGovernor(
            RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS,
        ) { dispatcher.scheduler.currentTime },
    ) { dispatcher.scheduler.currentTime }

    /** Past the settle wait and the (short) primary interval, so a scheduled search has gone out. */
    private fun past() =
        SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS + 1

    // ---- §24 A: rapid typing costs one primary request -----------------------------------------

    @Test
    fun `typing a word rapidly costs exactly one primary request and no legacy request`() = runTest {
        val primary = RecordingSource { found("1") }
        val legacy = RecordingSource { found("2") }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        listOf("c", "ch", "cho", "choc", "choco", "chocolate").forEach {
            viewModel.onQueryChanged(it)
            dispatcher.scheduler.advanceTimeBy(80)
        }
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(listOf("chocolate"), primary.calls)
        assertTrue("a successful primary must never spend a legacy request", legacy.calls.isEmpty())
    }

    /**
     * The latency win, expressed as behaviour: a settled query is searched without waiting out the
     * legacy provider's 7 s interval.
     */
    @Test
    fun `a settled query is searched without the legacy seven second wait`() = runTest {
        val primary = RecordingSource { found("1") }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("2") }))

        viewModel.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(
            SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS + 1,
        )

        assertEquals(listOf("gouda"), primary.calls)
        assertTrue(
            "the primary must answer well inside the legacy interval",
            SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.PRIMARY_MIN_INTERVAL_MS <
                RemoteSearchGovernor.MIN_INTERVAL_MS,
        )
    }

    // ---- §24 B/C: explicit search and duplicates -------------------------------------------------

    @Test
    fun `Enter during the settle wait produces one request, not two`() = runTest {
        val primary = RecordingSource { found("1") }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("2") }))

        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.search()
        dispatcher.scheduler.advanceTimeBy(past() * 2)

        assertEquals(listOf("hagelslag"), primary.calls)
    }

    @Test
    fun `re-typing the same normalized query does not search twice`() = runTest {
        val primary = RecordingSource { found("1") }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("2") }))

        viewModel.onQueryChanged("milk")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.onQueryChanged("milk ")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(listOf("milk"), primary.calls)
    }

    // ---- §23 C/D: fallback reaches the screen ---------------------------------------------------

    /**
     * The user-visible point of the whole migration: when the primary fails, results still arrive,
     * and nothing on screen mentions that anything went wrong.
     */
    @Test
    fun `a primary failure is invisible when the fallback answers`() = runTest {
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val legacy = RecordingSource { found("rescued") }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(past())

        val state = viewModel.state.value
        assertEquals(listOf("rescued"), state.hits.map { it.barcode })
        // One search operation, one outcome — no intermediate error is ever committed.
        assertEquals(null, state.error)
        assertFalse(state.refreshFailed)
        assertFalse(state.noMatches)
        assertFalse(state.searching)
        assertEquals(1, legacy.calls.size)
    }

    @Test
    fun `both providers failing surfaces one ordinary error`() = runTest {
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.OFFLINE) }
        val legacy = RecordingSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals(LookupError.OFFLINE, viewModel.state.value.error)
        assertTrue(viewModel.state.value.hits.isEmpty())
    }

    @Test
    fun `a legitimate no-results answer never reaches the legacy provider`() = runTest {
        val primary = RecordingSource { ProductSearchResult.NoMatches }
        val legacy = RecordingSource { found("should-not-appear") }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("zzzqqxx")
        dispatcher.scheduler.advanceTimeBy(past())

        assertTrue(viewModel.state.value.noMatches)
        assertTrue(viewModel.state.value.hits.isEmpty())
        assertTrue(legacy.calls.isEmpty())
    }

    // ---- §18/§23 G,H: staleness across the fallback ---------------------------------------------

    /**
     * **The rule that cannot be traded away**, now exercised across a provider boundary.
     *
     * Query A fails on the primary and goes to the fallback. The user types B while A's fallback is
     * still running. A's late answer must not replace B, must not clear B's progress, and must not
     * put A's hits on screen.
     *
     * ## Why the fallback here ignores cancellation
     *
     * The first version of this test used an ordinary fake and **passed with the ViewModel's
     * generation guard deleted** — proving it measured `flatMapLatest` cancellation rather than the
     * staleness invariant it claims to pin. That is the same vacuity trap this repo has already hit
     * twice (the Dutch header fixture, the soft-keyboard geometry test): modelling only the safe
     * version of a hazard proves nothing.
     *
     * [UncancellableLegacySource] is the fake that reproduces it. Its answer for the superseded
     * query genuinely arrives after B has completed, so only the generation check at the
     * state-commit boundary can stop it — cancellation cannot.
     */
    @Test
    fun `a fallback answer for a superseded query cannot land on the newer one`() = runTest {
        val primary = RecordingSource { terms ->
            if (terms == "aaa") ProductSearchResult.Failed(LookupError.SERVER) else found("b-hit")
        }
        val legacy = UncancellableLegacySource(found("a-late-hit"))
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("aaa")
        // Far enough for A's primary to fail and its fallback to be in flight, but not to finish.
        dispatcher.scheduler.advanceTimeBy(past())

        viewModel.onQueryChanged("bbb")
        dispatcher.scheduler.advanceTimeBy(past())

        // Only now does A's abandoned fallback complete, on top of a screen showing B.
        legacy.release()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("bbb", state.query)
        assertEquals(
            "the newer query's own result must be what shows",
            listOf("b-hit"),
            state.hits.map { it.barcode },
        )
        assertFalse(state.hits.any { it.barcode == "a-late-hit" })
        assertEquals(null, state.error)
        assertFalse(state.searching)
    }

    /**
     * A legacy fallback whose call does **not** unwind when its coroutine is cancelled.
     *
     * Reproduces a transport that has already handed its request to the network: cancellation is
     * requested, the call completes anyway, and the response arrives late. Without this, a stale
     * result never returns at all and the generation check is never exercised.
     */
    private class UncancellableLegacySource(
        private val answer: ProductSearchResult,
    ) : ProductSearchSource {
        val calls = mutableListOf<String>()
        private val gate = kotlinx.coroutines.CompletableDeferred<Unit>()

        fun release() = gate.complete(Unit)

        override suspend fun search(terms: String): ProductSearchResult =
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                calls += terms
                gate.await()
                answer
            }
    }

    /**
     * §19: an obsolete query must not spend a fallback request.
     *
     * The edit arrives while the primary's failure is still being handled, so the chain's own
     * obsolete-query check is what stops the legacy request — the guarantee is about request
     * economy, not only about what reaches the screen.
     */
    @Test
    fun `superseding a query before its fallback starts spends no legacy request`() = runTest {
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val legacy = RecordingSource { found("wasted") }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("aaa")
        // Mid-settle: the request has been scheduled but has not been issued.
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS / 2)
        viewModel.onQueryChanged("bbb")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals("only the surviving query is searched", listOf("bbb"), primary.calls)
        assertEquals(1, legacy.calls.size)
        assertEquals("bbb", legacy.calls.single())
    }

    // ---- §25: Home and Search share one stack ---------------------------------------------------

    /**
     * Home's inline search and the search screen are separate ViewModels over **one** chain and one
     * legacy governor, exactly as `AppContainer` wires them.
     *
     * The property that matters: the legacy budget is shared, so the two screens cannot spend it
     * twice — while the primary, which has its own pacing, still serves both.
     */
    @Test
    fun `Home and Search share one provider stack and one legacy budget`() = runTest {
        val clock = { dispatcher.scheduler.currentTime }
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val legacy = RecordingSource { found("1") }
        val sharedChain = FallbackProductSearch(
            primary = primary,
            fallback = GovernedProductSearch(legacy, RemoteSearchGovernor(nowMs = clock), clock),
        )

        val home = viewModelFor(sharedChain)
        val screen = viewModelFor(sharedChain)

        home.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(past())
        screen.onQueryChanged("kaas")
        dispatcher.scheduler.advanceTimeBy(past())

        // Both screens reached the primary — it is not the constrained resource.
        assertEquals(listOf("gouda", "kaas"), primary.calls)
        // Only one of them spent the shared legacy allowance.
        assertEquals(
            "the second screen must not get its own legacy allowance",
            listOf("gouda"),
            legacy.calls,
        )
    }

    @Test
    fun `an obsolete Home request cannot affect the Search screen`() = runTest {
        val chain = chainOf(RecordingSource { found("1") }, RecordingSource { found("2") })
        val home = viewModelFor(chain)
        val screen = viewModelFor(chain)

        home.onQueryChanged("gouda")
        screen.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceTimeBy(past())

        assertEquals("gouda", home.state.value.query)
        assertEquals("hagelslag", screen.state.value.query)
    }

    // ---- §24 F: retry ---------------------------------------------------------------------------

    @Test
    fun `repeated retries do not stack duplicate primary requests`() = runTest {
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.RATE_LIMITED) }
        val legacy = RecordingSource { found("2") }
        val viewModel = viewModelFor(chainOf(primary, legacy))

        viewModel.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(past())
        val afterFirst = primary.calls.size

        repeat(5) { viewModel.retry() }
        dispatcher.scheduler.advanceTimeBy(past())

        assertTrue(
            "five taps produced ${primary.calls.size - afterFirst} extra requests",
            primary.calls.size - afterFirst <= 1,
        )
        // A rate limit is never fallback-eligible, so no amount of retrying reaches the legacy host.
        assertTrue(legacy.calls.isEmpty())
    }

    // ---- §29: request counts with the cache in the production position ------------------------

    /**
     * Types a query the way a user does, then waits out the settle and the primary interval.
     *
     * A shared helper rather than repeated inline, because the scenarios below are only meaningful
     * if each query is driven identically — a difference in how one of them is entered would show up
     * as a difference in request count and be read as a cache result.
     */
    private fun SearchViewModel.enter(terms: String) {
        onQueryChanged(terms)
        dispatcher.scheduler.advanceTimeBy(past())
    }

    @Test
    fun `chocolate then gouda then chocolate costs two primary requests, not three`() = runTest {
        val primary = RecordingSource { terms -> found(terms) }
        val legacy = RecordingSource { found("legacy") }
        val viewModel = viewModelFor(chainOf(primary, legacy, cached = true))

        viewModel.enter("chocolate")
        viewModel.enter("gouda")
        viewModel.enter("chocolate")

        // The scenario the cache exists for, measured end to end through the real ViewModel, the
        // real chain and the real generation guard — not against the cache in isolation.
        assertEquals(listOf("chocolate", "gouda"), primary.calls)
        assertTrue("a cache hit must not reach the legacy provider", legacy.calls.isEmpty())
    }

    @Test
    fun `a cache hit puts the right results on screen`() = runTest {
        val primary = RecordingSource { terms -> found(terms) }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("legacy") }, cached = true))

        viewModel.enter("chocolate")
        viewModel.enter("gouda")
        viewModel.enter("chocolate")

        // Counting saved requests is not enough: a cache that returned the wrong list would save
        // exactly as many. The hits on screen must be chocolate's, and the search must read as
        // finished rather than leaving a progress line up for a request that never happened.
        val state = viewModel.state.value
        assertEquals(listOf("chocolate"), state.hits.map { it.barcode })
        assertFalse("a hit is a completed search, not a pending one", state.searching)
        assertFalse(state.noMatches)
        assertEquals(null, state.error)
    }

    @Test
    fun `an expired entry resumes making requests`() = runTest {
        val primary = RecordingSource { terms -> found(terms) }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("legacy") }, cached = true))

        viewModel.enter("chocolate")
        viewModel.enter("gouda")
        dispatcher.scheduler.advanceTimeBy(CachedProductSearch.TTL_MS)
        viewModel.enter("chocolate")

        assertEquals(listOf("chocolate", "gouda", "chocolate"), primary.calls)
    }

    @Test
    fun `a provider failure is not remembered and the next attempt retries`() = runTest {
        var failNext = true
        val primary = RecordingSource { terms ->
            if (failNext) ProductSearchResult.Failed(LookupError.OFFLINE) else found(terms)
        }
        // The legacy source must fail too, or the chain answers from it and the primary's retry is
        // never exercised. OFFLINE is fallback-eligible, so both are asked on the first attempt.
        val legacy = RecordingSource { ProductSearchResult.Failed(LookupError.OFFLINE) }
        val viewModel = viewModelFor(chainOf(primary, legacy, cached = true))

        viewModel.enter("chocolate")
        assertEquals(listOf("chocolate"), primary.calls)

        failNext = false
        viewModel.enter("gouda")
        viewModel.enter("chocolate")

        // The failure left no entry, so the recovered service is reached rather than the outage
        // being replayed from memory for five minutes.
        assertEquals(listOf("chocolate", "gouda", "chocolate"), primary.calls)
        assertEquals(listOf("chocolate"), viewModel.state.value.hits.map { it.barcode })
    }

    @Test
    fun `a stale cache hit cannot land on a newer query`() = runTest {
        val primary = RecordingSource { terms -> found(terms) }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("legacy") }, cached = true))

        // Prime the cache so "chocolate" is answerable without a request at all — the fastest
        // possible completion, which is exactly what makes it the hardest case for staleness.
        viewModel.enter("chocolate")

        // Ask for it again and immediately type something else before the pipeline can settle.
        viewModel.onQueryChanged("chocolate")
        viewModel.onQueryChanged("gouda")
        dispatcher.scheduler.advanceTimeBy(past())

        // The generation guard is what holds here, not cancellation: a cache hit may complete
        // without ever suspending, so it can arrive after a newer query began. It must not commit.
        val state = viewModel.state.value
        assertEquals("gouda", state.query)
        assertEquals(listOf("gouda"), state.hits.map { it.barcode })
    }

    @Test
    fun `clearing the query leaves the cache usable`() = runTest {
        val primary = RecordingSource { terms -> found(terms) }
        val viewModel = viewModelFor(chainOf(primary, RecordingSource { found("legacy") }, cached = true))

        viewModel.enter("chocolate")
        viewModel.onQueryChanged("")
        dispatcher.scheduler.advanceTimeBy(past())
        viewModel.enter("chocolate")

        // An empty field must neither store an entry nor evict one. Clearing and retyping is
        // ordinary use, and it should cost nothing.
        assertEquals(listOf("chocolate"), primary.calls)
    }

    @Test
    fun `a fallback answer is not remembered as a primary result`() = runTest {
        val primary = RecordingSource { ProductSearchResult.Failed(LookupError.SERVER) }
        val legacy = RecordingSource { found("legacy") }
        val viewModel = viewModelFor(chainOf(primary, legacy, cached = true))

        // The legacy provider keeps its own 7 s budget, and it applies between every one of these.
        // Waiting it out before each query is what makes this measure caching rather than the
        // governor refusing — without it the legacy list is short for a reason that has nothing to
        // do with the cache, which is exactly how this assertion first failed.
        viewModel.enter("chocolate")
        dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS)
        viewModel.enter("gouda")
        dispatcher.scheduler.advanceTimeBy(RemoteSearchGovernor.MIN_INTERVAL_MS)
        viewModel.enter("chocolate")

        // Every attempt reaches both providers. The primary failed, so nothing was stored — and the
        // legacy answer is not filed under the primary's cache, which is what keeps a cached
        // result's provenance answerable.
        assertEquals(listOf("chocolate", "gouda", "chocolate"), primary.calls)
        assertEquals(listOf("chocolate", "gouda", "chocolate"), legacy.calls)
    }
}
