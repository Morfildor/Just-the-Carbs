package app.justthecarbs.ui.search

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Search is explicit, not as-you-type (this hardening pass): Open Food Facts' search endpoint is
 * rate-limited and not meant to be hit on every keystroke. These tests pin the rules that matter —
 * typing alone must never call the network, an explicit [SearchViewModel.search] call must, stale
 * responses must never overwrite a newer search's results, and duplicate submissions of the same
 * query must not fire a second request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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

    /** Counts calls and lets a test control exactly when each call's response resolves. */
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

    @Test
    fun `typing alone never calls the search source`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)

        viewModel.onQueryChanged("h")
        viewModel.onQueryChanged("ha")
        viewModel.onQueryChanged("hagelslag")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertEquals("hagelslag", viewModel.state.value.query)
    }

    @Test
    fun `explicit search triggers exactly one call`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("hagelslag")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertEquals(listOf(hit()), viewModel.state.value.hits)
    }

    @Test
    fun `blank query does not search`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("   ")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    @Test
    fun `a too-short query does not search and shows no results`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("ha")

        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `submitting the same query twice in a row does not duplicate the request`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
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
        val viewModel = SearchViewModel(source)
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
        val viewModel = SearchViewModel(source)

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
        val viewModel = SearchViewModel(source)
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
        val viewModel = SearchViewModel(source)
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
        val viewModel = SearchViewModel(source)
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
    fun `retry re-runs the same query even though it already ran`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Failed(LookupError.SERVER))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, source.callCount)
        assertNull(viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.hits.size)
    }
}
