package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitStore
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.PortionUsageStore
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * One accepted barcode must cost at most one remote lookup, and a superseded one must not answer.
 *
 * Open Food Facts allows **15 reads per minute per IP**, which makes a duplicate request more than
 * waste: it spends a budget shared by every user behind the same address, and the app's whole
 * offline story depends on staying inside it. The pre-existing guard in [ProductViewModel.load]
 * tested `product != null`, which is exactly the field a lookup that has *started but not finished*
 * has not written yet — so two calls close together both saw null and both went to the network.
 *
 * The second test is the more serious one. Both branches of `load` write state unconditionally, so
 * without cancellation the **last** lookup to complete wins regardless of which barcode the user
 * actually asked for. A slow first scan finishing after a fast second one would put the previous
 * product on screen under the new scan's barcode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductLookupSingleFlightTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun product(barcode: String, name: String) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal("46.0"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * Counts fetches, with a **per-barcode** delay.
     *
     * Per-barcode rather than one shared delay, because a shared one makes the overwrite test
     * vacuous: two lookups started microseconds apart and delayed equally complete in the order they
     * started, so the second one's product wins on its own and the test would pass with no
     * cancellation whatever. Making the abandoned lookup the *slower* of the two is what forces it to
     * land after the current one and actually attempt the overwrite.
     */
    private class CountingRemote(
        private val delaysMs: Map<String, Long>,
        private val products: Map<String, Product>,
    ) : ProductDataSource {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(barcode: String): ProductFetchResult {
            fetched += barcode
            delay(delaysMs[barcode] ?: 100L)
            return products[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound
        }
    }

    private class EmptyLocal : LocalProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
        override suspend fun save(product: Product) = Unit
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(emptyList())
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private class NoUsage : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null

        override suspend fun save(usage: PortionUsage): PortionUsage = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModelWith(remote: ProductDataSource) = ProductViewModel(
        repository = ProductRepository(
            local = EmptyLocal(),
            remote = remote,
            portionUnits = NoUnits(),
            meal = NoMeal(),
            portionUsage = NoUsage(),
            searchSource = NoSearch(),
            clock = clock,
        ),
        savedState = SavedStateHandle(),
    )

    @Test
    fun `two loads of the same barcode while one is in flight cost one remote fetch`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        val remote = CountingRemote(
            delaysMs = mapOf(barcode to 500L),
            products = mapOf(barcode to product(barcode, "Hagelslag")),
        )
        val viewModel = viewModelWith(remote)

        viewModel.load(barcode)
        // Deliberately before the first has completed — the state still holds a null product, which
        // is precisely the situation the old guard could not detect.
        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals("the same barcode was fetched twice", listOf(barcode), remote.fetched)
        assertEquals("Hagelslag", viewModel.state.value.product?.name)
    }

    /**
     * A superseded lookup must not deliver. Asserted on the *product on screen*, not on the fetch
     * count: cancelling after the request has left is still correct behaviour, and a fetch-count
     * assertion would forbid it while missing the failure that actually matters.
     */
    @Test
    fun `a slow first lookup cannot overwrite the product of a later scan`() = runTest(dispatcher) {
        val first = "8712100849060"
        val second = "5000159484695"
        val remote = CountingRemote(
            // The abandoned lookup is the SLOW one, so without cancellation it lands last and its
            // unconditional state write wins. With a shared delay this test cannot fail.
            delaysMs = mapOf(first to 800L, second to 100L),
            products = mapOf(first to product(first, "Hagelslag"), second to product(second, "Snickers")),
        )
        val viewModel = viewModelWith(remote)

        viewModel.load(first)
        viewModel.load(second)
        advanceUntilIdle()

        assertEquals(
            "the abandoned scan's product was delivered over the current one",
            "Snickers",
            viewModel.state.value.product?.name,
        )
        assertEquals(second, viewModel.state.value.barcode)
    }
}
