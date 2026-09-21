package app.justthecarbs.ui.search

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import app.justthecarbs.domain.SavedProduct
import app.justthecarbs.domain.SavedProductSearchSource
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Local-first search in the ViewModel: saved products answer a keystroke, the network joins later.
 *
 * Two properties carry the feature and both are asserted against virtual time rather than argued:
 *
 * - **Local hits are on screen before the remote request exists.** Not merely before the response —
 *   before the settle wait has elapsed and before a single call has been made, which is the whole
 *   claim ("search became faster") stated as a measurement.
 * - **The remote pipeline is untouched.** Every request-count and timing assertion in
 *   [SearchViewModelTest] still describes this ViewModel exactly; the ones here re-measure it with
 *   a local source attached, so a local read cannot have changed what the network sees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedProductSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val governor = RemoteSearchGovernor { dispatcher.scheduler.currentTime }

    /** Past the settle wait *and* the shared interval, so a scheduled search has actually gone out. */
    private fun past() = SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.MIN_INTERVAL_MS + 1

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModelFor(
        source: ProductSearchSource,
        saved: SavedProductSearchSource?,
    ) = SearchViewModel(source, governor, saved) { dispatcher.scheduler.currentTime }

    private fun savedProduct(
        barcode: String = "8710496979125",
        name: String = "Chocoladehagel puur",
        brand: String? = "De Ruijter",
        favorite: Boolean = false,
        lastUsedAt: Long? = null,
        /** The user's personal name for this product, if they have given it one (1.0.8). */
        localAlias: String? = null,
    ) = SavedProduct(
        barcode = barcode,
        name = name,
        localAlias = localAlias,
        brand = brand,
        carbsPer100 = BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
        favorite = favorite,
        lastUsedAt = lastUsedAt,
    )

    private fun remoteHit(barcode: String, name: String = "Remote $barcode") = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = null,
        packageQuantity = null,
        carbsPer100 = BigDecimal("12"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

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

    /** Answers instantly, and counts how often the store was read. */
    private class FakeSavedSource(private val products: List<SavedProduct>) : SavedProductSearchSource {
        var reads = 0
            private set

        override suspend fun allProducts(): List<SavedProduct> {
            reads++
            return products
        }
    }

    /**
     * A saved-product source that does **not** honour cancellation, and whose reads complete only
     * when the test says so.
     *
     * This is the fake the local staleness guarantee has to be proven against, for exactly the
     * reason [SearchViewModelTest]'s `UncancellableSearchSource` exists: with a well-behaved fake,
     * cancelling the coroutine is enough and the test would be measuring the cancellation while
     * claiming to measure the generation check. A source in this position is not exotic — a Room
     * query already past its last suspension point behaves this way.
     */
    private class UncancellableSavedSource : SavedProductSearchSource {
        private val pending = mutableListOf<CompletableDeferred<List<SavedProduct>>>()

        override suspend fun allProducts(): List<SavedProduct> {
            val deferred = CompletableDeferred<List<SavedProduct>>()
            pending += deferred
            return withContext(NonCancellable) { deferred.await() }
        }

        /** Completes the read that started [index]-th, oldest first. */
        fun resolve(index: Int, products: List<SavedProduct>) {
            pending[index].complete(products)
        }
    }

    // ---- A. Local hits arrive before the remote request ------------------------------------------

    @Test
    fun `saved products are on screen before the settle delay has elapsed`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        // Only far enough to let the local coroutine run — deliberately short of the settle wait.
        advanceTimeBy(1)

        assertEquals(listOf("Chocoladehagel puur"), viewModel.state.value.hits.map { it.name })
        assertEquals("no request may have been made yet", 0, remote.callCount)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `the progress affordance stays up while the remote search is still running`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(1)

        val state = viewModel.state.value
        assertTrue("local hits are shown", state.hits.isNotEmpty())
        assertTrue("and the screen still says more is coming", state.searching)
        assertTrue(state.narrowedLocally)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a local answer does not make the app search sooner or more often`() = runTest {
        // The remote pipeline is measured with a local source attached, against exactly the figures
        // SearchViewModelTest pins without one.
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        "hagelslag".forEachIndexed { index, _ -> viewModel.onQueryChanged("hagelslag".take(index + 1)) }
        advanceTimeBy(SearchViewModel.REMOTE_SEARCH_SETTLE_MS - 1)
        assertEquals("still settling", 0, remote.callCount)

        advanceTimeBy(past())
        assertEquals("one request for the word, not one per keystroke", 1, remote.callCount)
        assertEquals(listOf("hagelslag"), remote.callsInOrder)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `no local source leaves the remote result exactly as it was`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, saved = null)

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        val hits = listOf(remoteHit("r1"), remoteHit("r2"))
        remote.resolve("hagel", ProductSearchResult.Found(hits))
        advanceUntilIdle()

        assertEquals(hits, viewModel.state.value.hits)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    // ---- B. Merge --------------------------------------------------------------------------------

    @Test
    fun `a remote Found merges with the saved hits already on screen`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct(name = "Chocoladehagel puur"))))

        viewModel.onQueryChanged("chocoladehagel puur")
        advanceTimeBy(past())
        remote.resolve(
            "chocoladehagel puur",
            ProductSearchResult.Found(listOf(remoteHit("r1"), remoteHit("r2"))),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("8710496979125", "r1", "r2"), state.hits.map { it.barcode })
        assertFalse("the merged list is the complete answer", state.searching)
        assertFalse(state.narrowedLocally)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a product returned by both sources appears once, with the saved payload`() = runTest {
        val remote = FakeSearchSource()
        val saved = savedProduct(barcode = "shared", name = "Chocoladehagel puur")
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(saved)))

        viewModel.onQueryChanged("chocoladehagel puur")
        advanceTimeBy(past())
        remote.resolve(
            "chocoladehagel puur",
            ProductSearchResult.Found(listOf(remoteHit("shared", name = "Something else"))),
        )
        advanceUntilIdle()

        val hits = viewModel.state.value.hits
        assertEquals(1, hits.count { it.barcode == "shared" })
        assertEquals("Chocoladehagel puur", hits.single { it.barcode == "shared" }.name)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a saved hit arriving after the remote answer still merges`() = runTest {
        // The local read is slower than the network — the unusual order, and it must produce the
        // same screen as the usual one rather than replacing the remote list.
        val remote = FakeSearchSource()
        val saved = UncancellableSavedSource()
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("chocoladehagel puur")
        advanceTimeBy(past())
        remote.resolve("chocoladehagel puur", ProductSearchResult.Found(listOf(remoteHit("r1"))))
        advanceUntilIdle()
        assertEquals(listOf("r1"), viewModel.state.value.hits.map { it.barcode })

        saved.resolve(0, listOf(savedProduct(name = "Chocoladehagel puur")))
        advanceUntilIdle()

        assertEquals(listOf("8710496979125", "r1"), viewModel.state.value.hits.map { it.barcode })
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    // ---- C. The remote outcomes that must not erase local hits ------------------------------------

    @Test
    fun `NoMatches keeps the saved hits and does not claim the product does not exist`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.NoMatches)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Chocoladehagel puur"), state.hits.map { it.name })
        assertFalse("a saved product IS a result", state.noMatches)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `NoMatches with nothing saved still reports no matches`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(emptyList()))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.NoMatches)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.noMatches)
        assertTrue(viewModel.state.value.hits.isEmpty())
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `being offline never erases the saved hits`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.Failed(LookupError.OFFLINE))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Chocoladehagel puur"), state.hits.map { it.name })
        // The established contract for a failure over usable results: the error is carried so it can
        // be reported, and `refreshFailed` is what tells the screen to report it *beside* the list
        // rather than in place of it. The screen's own `when` tests `hits.isNotEmpty()` first, so a
        // failure never takes results away — see `SearchScreen`'s branch order.
        assertTrue("reported as a refresh failure, not a dead end", state.refreshFailed)
        assertEquals(LookupError.OFFLINE, state.error)
        assertFalse("the progress line stops", state.searching)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a failure arriving before the saved hits still ends with them on screen`() = runTest {
        // The other order, so the outcome does not depend on which finished first.
        val remote = FakeSearchSource()
        val saved = UncancellableSavedSource()
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.Failed(LookupError.OFFLINE))
        advanceUntilIdle()
        assertEquals(LookupError.OFFLINE, viewModel.state.value.error)

        saved.resolve(0, listOf(savedProduct()))
        advanceUntilIdle()

        // The same screen the usual order produces: results present, failure demoted from a
        // dead end to a refresh failure reported beside them.
        val state = viewModel.state.value
        assertEquals(listOf("Chocoladehagel puur"), state.hits.map { it.name })
        assertTrue(state.refreshFailed)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a server failure with nothing saved still reaches the recovery panel`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(emptyList()))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.Failed(LookupError.SERVER))
        advanceUntilIdle()

        assertEquals(LookupError.SERVER, viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshFailed)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a rate limit keeps the saved hits while the backoff runs`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.Failed(LookupError.RATE_LIMITED, retryAfterMs = 5_000))
        advanceTimeBy(1)

        val state = viewModel.state.value
        assertEquals(listOf("Chocoladehagel puur"), state.hits.map { it.name })
        assertTrue("the queued retry is still coming", state.rateLimited)
        assertNull("a handled wait is never a dead end", state.error)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    // ---- D. Cancellation and staleness -----------------------------------------------------------

    @Test
    fun `a refinement cannot land the previous query's saved hits`() = runTest {
        val remote = FakeSearchSource()
        val saved = UncancellableSavedSource()
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("gouda")
        advanceTimeBy(1)
        viewModel.onQueryChanged("hagelslag")
        advanceTimeBy(1)

        // The first read completes late, with products that answer the query the user has left.
        saved.resolve(0, listOf(savedProduct(name = "Gouda jong", brand = null)))
        advanceUntilIdle()

        assertTrue(
            "the abandoned query's products must not appear",
            viewModel.state.value.hits.none { it.name == "Gouda jong" },
        )
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `even a cancellation-ignoring source cannot commit a stale local result`() = runTest {
        // The generation check is the invariant; cancellation is only the optimisation. This source
        // ignores cancellation entirely, so the check is the only thing left standing.
        val remote = FakeSearchSource()
        val saved = UncancellableSavedSource()
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("gouda")
        advanceTimeBy(1)
        viewModel.onQueryChanged("hagel")
        advanceTimeBy(1)

        // Newest first, oldest second: the stale answer arrives last and must still be refused.
        saved.resolve(1, listOf(savedProduct(name = "Chocoladehagel puur")))
        advanceUntilIdle()
        saved.resolve(0, listOf(savedProduct(barcode = "2", name = "Gouda jong", brand = null)))
        advanceUntilIdle()

        assertEquals(listOf("Chocoladehagel puur"), viewModel.state.value.hits.map { it.name })
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `clearing the field drops the saved hits`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(1)
        assertTrue(viewModel.state.value.hits.isNotEmpty())

        viewModel.onQueryChanged("")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hits.isEmpty())
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a query below the minimum length reads nothing at all`() = runTest {
        val remote = FakeSearchSource()
        val saved = FakeSavedSource(listOf(savedProduct()))
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("ha")
        advanceUntilIdle()

        assertEquals("the store is not scanned for a two-letter query", 0, saved.reads)
        assertTrue(viewModel.state.value.hits.isEmpty())
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `a submission refused for being too short clears the saved hits`() = runTest {
        val remote = FakeSearchSource()
        val viewModel = viewModelFor(remote, FakeSavedSource(listOf(savedProduct())))

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(1)
        assertTrue(viewModel.state.value.hits.isNotEmpty())

        viewModel.onQueryChanged("ha")
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.queryTooShort)
        assertTrue(viewModel.state.value.hits.isEmpty())
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `typing on does not re-read the store for a query already answered`() = runTest {
        val remote = FakeSearchSource()
        val saved = FakeSavedSource(listOf(savedProduct()))
        val viewModel = viewModelFor(remote, saved)

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(1)
        // Same terms after normalization — a trailing space is not a new query.
        viewModel.onQueryChanged("hagel ")
        advanceUntilIdle()

        assertEquals(1, saved.reads)
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }

    // ---- E. The store is not reachable ------------------------------------------------------------

    @Test
    fun `a failing store costs the saved hits and nothing else`() = runTest {
        val remote = FakeSearchSource()
        val broken = object : SavedProductSearchSource {
            override suspend fun allProducts(): List<SavedProduct> = error("store unavailable")
        }
        val viewModel = viewModelFor(remote, broken)

        viewModel.onQueryChanged("hagel")
        advanceTimeBy(past())
        remote.resolve("hagel", ProductSearchResult.Found(listOf(remoteHit("r1"))))
        advanceUntilIdle()

        assertEquals(listOf("r1"), viewModel.state.value.hits.map { it.barcode })
        viewModel.viewModelScope.coroutineContext.cancelChildren()
    }
}
