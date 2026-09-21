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
import app.justthecarbs.domain.RecentUseSnapshot
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

        // This fake stores nothing at all — its `save` is empty and `observeRecents` returns a
        // fixed list — so these are no-ops rather than column-accurate writes. That is faithful:
        // nothing here can observe a stored column, and pretending otherwise would invent state the
        // tests in this file do not read.
        override suspend fun setFavorite(barcode: String, favorite: Boolean) {
            Unit
        }

        override suspend fun recordUsageColumns(
            barcode: String,
            lastPortion: java.math.BigDecimal?,
            lastUsedAt: java.time.Instant,
            lastInputMode: app.justthecarbs.domain.InputMode?,
            lastSelectedPortionUnitId: Long?,
            lastCount: java.math.BigDecimal?,
        ) {
            Unit
        }

        override suspend fun saveProductFacts(product: Product) {
            save(product)
        }

        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
            error("this fake does not implement forgetRecentUse")

        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
            error("this fake does not implement restoreRecentUse")
    }

    /**
     * A local store that records the forget/restore round trip.
     *
     * The storage semantics themselves are pinned on a real database in
     * `ProductDaoTest` — what this fake is for is the part of the feature that lives above storage:
     * that exactly one snapshot is held, that it is dropped before the restore is issued, and that a
     * second Undo therefore reaches the repository zero times.
     */
    private class ForgetfulLocal(
        private val recents: List<Product> = emptyList(),
        private val snapshotFor: (String) -> RecentUseSnapshot? = { defaultSnapshot(it) },
    ) : LocalProductDataSource {
        val forgotten = mutableListOf<String>()
        val restored = mutableListOf<RecentUseSnapshot>()

        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
        override suspend fun save(product: Product) {}
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(recents)

        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? {
            forgotten += barcode
            return snapshotFor(barcode)
        }

        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) {
            restored += snapshot
        }

        companion object {
            fun defaultSnapshot(barcode: String) = RecentUseSnapshot(
                barcode = barcode,
                lastUsedAt = Instant.EPOCH,
                lastPortion = BigDecimal("65"),
                lastInputMode = InputMode.GRAMS,
                lastSelectedPortionUnitId = null,
                lastCount = null,
                portionUsage = emptyList(),
            )

            /** A favourite that was starred but never eaten: nothing to forget, nothing to undo. */
            fun emptySnapshot(barcode: String) = RecentUseSnapshot(
                barcode = barcode,
                lastUsedAt = null,
                lastPortion = null,
                lastInputMode = null,
                lastSelectedPortionUnitId = null,
                lastCount = null,
                portionUsage = emptyList(),
            )
        }
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

    // ---- Remove from Recent: the Undo window ---------------------------------------------------

    private fun forgetfulViewModel(local: ForgetfulLocal) = HomeViewModel(
        ProductRepository(
            local = local,
            remote = noRemote,
            portionUnits = CountingPortionUnitStore(emptyList()),
            meal = noMeal,
            portionUsage = noUsage,
            searchSource = noSearch,
        ),
    )

    private fun aProduct(barcode: String = "111") = product(barcode, unitId = null, mode = InputMode.GRAMS)
        .copy(name = "Hagelslag")

    @Test
    fun `forgetting a product offers an undo naming it`() = runTest {
        val local = ForgetfulLocal()
        val vm = forgetfulViewModel(local)

        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("111"), local.forgotten)
        assertEquals("Hagelslag", vm.lastForgotten.value?.name)
        assertEquals("111", vm.lastForgotten.value?.snapshot?.barcode)
    }

    @Test
    fun `undo restores the snapshot that was taken`() = runTest {
        val local = ForgetfulLocal()
        val vm = forgetfulViewModel(local)
        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()
        val offered = vm.lastForgotten.value!!.snapshot

        vm.undoForgetRecentUse()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(offered), local.restored)
    }

    /**
     * The Snackbar can still be on screen for a moment after its action fires, so a second tap is
     * reachable — and a second restore of the same snapshot would re-insert the usual portions the
     * first one already put back.
     *
     * The guard is that the held snapshot is cleared *before* the restore is launched, not that the
     * restore is idempotent; asserted by call count, because a state check alone would still pass if
     * the clear happened after the launch.
     */
    @Test
    fun `undo cannot run twice`() = runTest {
        val local = ForgetfulLocal()
        val vm = forgetfulViewModel(local)
        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()

        vm.undoForgetRecentUse()
        vm.undoForgetRecentUse()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("exactly one restore", 1, local.restored.size)
        assertEquals("and nothing left to undo", null, vm.lastForgotten.value)
    }

    @Test
    fun `undo after the snackbar has gone restores nothing`() = runTest {
        val local = ForgetfulLocal()
        val vm = forgetfulViewModel(local)
        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()

        vm.clearForgetUndo()
        vm.undoForgetRecentUse()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<RecentUseSnapshot>(), local.restored)
    }

    /**
     * A favourite starred but never eaten has nothing to forget. Offering "Removed X — Undo" there
     * would claim something happened, and the Undo would restore a state of all nulls.
     *
     * This is also what stops a repeated removal of the same favourite — which stays on screen and
     * so can be long-pressed again — replacing a good snapshot with an empty one.
     */
    @Test
    fun `forgetting a product that was never used offers no undo`() = runTest {
        val local = ForgetfulLocal(snapshotFor = { ForgetfulLocal.emptySnapshot(it) })
        val vm = forgetfulViewModel(local)

        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("the clear still ran", listOf("111"), local.forgotten)
        assertEquals("but nothing is offered", null, vm.lastForgotten.value)
    }

    @Test
    fun `forgetting an unknown product offers no undo`() = runTest {
        val local = ForgetfulLocal(snapshotFor = { null })
        val vm = forgetfulViewModel(local)

        vm.forgetRecentUse(aProduct())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.lastForgotten.value)
    }

    /**
     * One deep, never a list. A second removal replaces the first, matching the Snackbar the screen
     * shows — a queue would let the user tap Undo and restore a product forgotten two actions ago.
     */
    @Test
    fun `a second removal replaces the first rather than queueing behind it`() = runTest {
        val local = ForgetfulLocal()
        val vm = forgetfulViewModel(local)

        vm.forgetRecentUse(aProduct("111"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.forgetRecentUse(aProduct("222"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.undoForgetRecentUse()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("only the most recent is restorable", listOf("222"), local.restored.map { it.barcode })
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
