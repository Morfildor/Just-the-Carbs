package app.justthecarbs.ui.search

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RemoteSearchGovernor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Products stored on the phone are found by name, listed before online results, and answered on
 * the device without touching the remote pipeline: its debounce, request count and staleness
 * guard behave exactly as they do with nothing stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedProductSearchTest {

    private val dispatcher = StandardTestDispatcher()
    private val governor = RemoteSearchGovernor { dispatcher.scheduler.currentTime }

    private fun past() = SearchViewModel.REMOTE_SEARCH_SETTLE_MS + RemoteSearchGovernor.MIN_INTERVAL_MS + 1

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Every call recorded; each answered when the test says, and completed even if cancelled. */
    private class Source : ProductSearchSource {
        val calls = mutableListOf<String>()
        private val pending = mutableMapOf<String, CompletableDeferred<ProductSearchResult>>()
        override suspend fun search(terms: String): ProductSearchResult {
            calls += terms
            val answer = CompletableDeferred<ProductSearchResult>()
            pending[terms] = answer
            return withContext(NonCancellable) { answer.await() }
        }
        fun answer(terms: String, result: ProductSearchResult) {
            pending.remove(terms)?.complete(result)
        }
    }

    private fun stored(barcode: String, name: String, brand: String? = null) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = null,
        carbsPer100 = BigDecimal("40"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private val homemade = stored("local:1", "Homemade spelt bread")
    private val cafe = stored("222", "Café noir")
    private val pinar = stored("333", "Süt", brand = "Pınar")
    private val volkoren = stored("444", "Volkoren brood", brand = "Jumbo")

    private fun viewModel(source: ProductSearchSource, saved: MutableStateFlow<List<ProductSearchHit>>) =
        SearchViewModel(source, governor, saved) { dispatcher.scheduler.currentTime }

    @Test
    fun `a stored product is listed as soon as it is typed, before any request`() = runTest(dispatcher) {
        val source = Source()
        val vm = viewModel(source, MutableStateFlow(listOf(homemade, volkoren)))
        runCurrent()

        vm.onQueryChanged("spelt")
        runCurrent()

        assertEquals(listOf(homemade), vm.state.value.savedHits)
        assertTrue(vm.state.value.hasResults)
        assertTrue("nothing has been sent yet", source.calls.isEmpty())
    }

    @Test
    fun `matching folds case, accents and the Turkish dotless i`() = runTest(dispatcher) {
        val vm = viewModel(Source(), MutableStateFlow(listOf(cafe, pinar)))
        runCurrent()

        vm.onQueryChanged("CAFE")
        assertEquals(listOf(cafe), vm.state.value.savedHits)
        vm.onQueryChanged("pinar sut")
        assertEquals(listOf(pinar), vm.state.value.savedHits)
    }

    @Test
    fun `every word must match the name or brand`() = runTest(dispatcher) {
        val vm = viewModel(Source(), MutableStateFlow(listOf(volkoren, homemade)))
        runCurrent()

        vm.onQueryChanged("brood jumbo")
        assertEquals(listOf(volkoren), vm.state.value.savedHits)
        vm.onQueryChanged("brood kaas")
        assertTrue(vm.state.value.savedHits.isEmpty())
        // Two of three words is a strong match for the online ranking, but a stored product is
        // listed above the online results only when it has every word the user typed.
        vm.onQueryChanged("volkoren brood wit")
        assertTrue(vm.state.value.savedHits.isEmpty())
    }

    @Test
    fun `stored matches keep the stored order`() = runTest(dispatcher) {
        val first = stored("1", "Brood wit")
        val second = stored("2", "Brood bruin")
        val vm = viewModel(Source(), MutableStateFlow(listOf(first, second)))
        runCurrent()

        vm.onQueryChanged("brood")

        assertEquals(listOf(first, second), vm.state.value.savedHits)
    }

    @Test
    fun `short or letter-only queries list nothing stored`() = runTest(dispatcher) {
        val vm = viewModel(Source(), MutableStateFlow(listOf(stored("1", "Ab"), stored("2", "A b c"))))
        runCurrent()

        vm.onQueryChanged("ab")
        assertTrue(vm.state.value.savedHits.isEmpty())
        vm.onQueryChanged("a b")
        assertTrue("one-letter words match nothing, not everything", vm.state.value.savedHits.isEmpty())
    }

    @Test
    fun `at most ten stored products are listed`() = runTest(dispatcher) {
        val many = (1..15).map { stored("$it", "Brood $it") }
        val vm = viewModel(Source(), MutableStateFlow(many))
        runCurrent()

        vm.onQueryChanged("brood")

        assertEquals(many.take(SearchViewModel.MAX_SAVED_HITS), vm.state.value.savedHits)
    }

    @Test
    fun `the remote search still costs exactly one request and its results are unchanged`() = runTest(dispatcher) {
        val source = Source()
        val vm = viewModel(source, MutableStateFlow(listOf(volkoren)))
        runCurrent()

        vm.onQueryChanged("b")
        vm.onQueryChanged("br")
        vm.onQueryChanged("bro")
        vm.onQueryChanged("brood")
        advanceTimeBy(past())
        runCurrent()

        assertEquals(listOf("brood"), source.calls)
        val online = stored("555", "Brood tarwe")
        source.answer("brood", ProductSearchResult.Found(listOf(volkoren, online)))
        runCurrent()

        assertEquals("remote hits are exactly what the service returned", listOf(volkoren, online), vm.state.value.hits)
        assertEquals(listOf(volkoren), vm.state.value.savedHits)
        assertEquals("a stored product is not listed twice", listOf(online), vm.state.value.onlineHits)
    }

    @Test
    fun `an offline search keeps the stored matches and reports the online failure`() = runTest(dispatcher) {
        val source = Source()
        val vm = viewModel(source, MutableStateFlow(listOf(homemade)))
        runCurrent()

        vm.onQueryChanged("homemade")
        advanceTimeBy(past())
        runCurrent()
        source.answer("homemade", ProductSearchResult.Failed(LookupError.OFFLINE))
        runCurrent()

        assertEquals(LookupError.OFFLINE, vm.state.value.error)
        assertFalse("not a refresh failure: no online list was kept", vm.state.value.refreshFailed)
        assertEquals(listOf(homemade), vm.state.value.savedHits)
        assertTrue(vm.state.value.hasResults)
    }

    @Test
    fun `a late answer for an older query leaves the newer stored matches alone`() = runTest(dispatcher) {
        val source = Source()
        val vm = viewModel(source, MutableStateFlow(listOf(homemade, volkoren)))
        runCurrent()

        vm.onQueryChanged("homemade")
        advanceTimeBy(past())
        runCurrent()
        vm.onQueryChanged("volkoren")
        source.answer("homemade", ProductSearchResult.Found(listOf(homemade)))
        runCurrent()

        assertEquals(listOf(volkoren), vm.state.value.savedHits)
        assertTrue("the stale answer never landed", vm.state.value.hits.isEmpty())
    }

    @Test
    fun `a list that arrives or changes after typing is matched against the current text`() = runTest(dispatcher) {
        val saved = MutableStateFlow<List<ProductSearchHit>>(emptyList())
        val vm = viewModel(Source(), saved)
        runCurrent()

        vm.onQueryChanged("spelt")
        assertTrue(vm.state.value.savedHits.isEmpty())
        saved.value = listOf(homemade)
        runCurrent()

        assertEquals(listOf(homemade), vm.state.value.savedHits)
    }

    @Test
    fun `clearing the field clears the stored matches`() = runTest(dispatcher) {
        val vm = viewModel(Source(), MutableStateFlow(listOf(homemade)))
        runCurrent()

        vm.onQueryChanged("spelt")
        vm.onQueryChanged("")

        assertTrue(vm.state.value.savedHits.isEmpty())
        assertFalse(vm.state.value.hasResults)
    }
}
