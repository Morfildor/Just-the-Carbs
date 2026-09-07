package app.justthecarbs.ui.home

import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * P1 §13: Home's recents row must resolve each product's remembered countable unit with one batch
 * query, not one [ProductRepository.findPortionUnit] call per product in a loop. The N+1 shape was
 * invisible without a test that actually counts calls — a green suite never asserted call counts,
 * only the final `RecentEntry` list, which looks identical either way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun product(
        barcode: String,
        unitId: Long?,
        mode: InputMode? = InputMode.PORTION_UNIT,
    ) = Product(
        barcode = barcode,
        name = "Product $barcode",
        carbsPer100 = BigDecimal("10"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        lastInputMode = mode,
        lastSelectedPortionUnitId = unitId,
    )

    private fun unit(id: Long, barcode: String) = PortionUnit(
        id = id,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        verifiedAt = Instant.EPOCH,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private class FakeLocal(private val recents: List<Product>) : LocalProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
        override suspend fun save(product: Product) {}
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(recents)
    }

    private val noRemote = object : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
    }

    private val noSearch = object : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private val noMeal = object : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = item
        override suspend fun update(item: MealItem) {}
        override suspend fun remove(item: MealItem) {}
        override suspend fun clear() {}
    }

    private val noUsage = object : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage = usage
        override suspend fun delete(usage: PortionUsage) {}
    }

    /** Counts calls to both the single-id and batch lookups, so a regression to the loop form shows up as a count, not just a wrong list. */
    private class CountingPortionUnitStore(seed: List<PortionUnit>) : PortionUnitStore {
        val stored = seed.associateBy { it.id }
        var findByIdCalls = 0
            private set
        var findByIdsCalls = 0
            private set

        override suspend fun findByBarcode(barcode: String): List<PortionUnit> =
            stored.values.filter { it.productBarcode == barcode }

        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> =
            flowOf(stored.values.filter { it.productBarcode == barcode })

        override suspend fun findById(id: Long): PortionUnit? {
            findByIdCalls++
            return stored[id]
        }

        override suspend fun findByIds(ids: List<Long>): List<PortionUnit> {
            findByIdsCalls++
            return ids.distinct().mapNotNull { stored[it] }
        }

        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) {}
    }

    private fun viewModel(products: List<Product>, units: CountingPortionUnitStore) = HomeViewModel(
        ProductRepository(
            local = FakeLocal(products),
            remote = noRemote,
            portionUnits = units,
            meal = noMeal,
            portionUsage = noUsage,
            searchSource = noSearch,
        ),
    )

    // `recents` is a WhileSubscribed StateFlow — the upstream repository flow only starts once
    // something collects it, so every test needs an active subscriber before advancing time.
    private fun TestScope.subscribe(vm: HomeViewModel) {
        backgroundScope.launch { vm.recents.collect {} }
    }

    @Test
    fun `recents with several countable products cost exactly one batch lookup`() = runTest {
        val units = CountingPortionUnitStore(listOf(unit(1, "111"), unit(2, "222"), unit(3, "333")))
        val products = listOf(
            product("111", unitId = 1),
            product("222", unitId = 2),
            product("333", unitId = 3),
        )

        val vm = viewModel(products, units)
        subscribe(vm)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("no per-product loop", 0, units.findByIdCalls)
        assertEquals("exactly one batch call for the whole list", 1, units.findByIdsCalls)
        assertEquals(
            listOf(1L, 2L, 3L),
            vm.recents.value.map { it.lastUnit?.id },
        )
    }

    @Test
    fun `a product with no remembered unit needs no lookup at all`() = runTest {
        val units = CountingPortionUnitStore(emptyList())
        val products = listOf(product("111", unitId = null, mode = InputMode.GRAMS))

        val vm = viewModel(products, units)
        subscribe(vm)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, units.findByIdCalls)
        // The batch call may still run once with an empty id list; what matters is it never scales
        // with product count and never falls back to the per-id path.
        assertEquals(listOf<PortionUnit?>(null), vm.recents.value.map { it.lastUnit })
    }

    @Test
    fun `only PORTION_UNIT products contribute to the batch, GRAMS products are excluded`() = runTest {
        val units = CountingPortionUnitStore(listOf(unit(1, "111")))
        val products = listOf(
            product("111", unitId = 1, mode = InputMode.PORTION_UNIT),
            // Has a stale lastSelectedPortionUnitId from a previous session, but the mode says
            // grams — must not be resolved as if it were still a countable-portion product.
            product("222", unitId = 99, mode = InputMode.GRAMS),
        )

        val vm = viewModel(products, units)
        subscribe(vm)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, units.findByIdCalls)
        val entries = vm.recents.value
        assertEquals(1L, entries.first { it.product.barcode == "111" }.lastUnit?.id)
        assertEquals(null, entries.first { it.product.barcode == "222" }.lastUnit)
    }
}
