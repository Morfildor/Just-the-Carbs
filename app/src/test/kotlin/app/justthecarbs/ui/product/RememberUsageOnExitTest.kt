package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Event semantics and awaited persistence for remembered portions. */
@OptIn(ExperimentalCoroutinesApi::class)
class RememberUsageOnExitTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC)
    private val barcode = "8712100849060"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun product() = Product(
        barcode = barcode,
        name = "Hagelslag",
        carbsPer100 = BigDecimal("46.0"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    private class ImmediateRemote(private val product: Product) : ProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.Found(product)
    }

    private class RecordingLocal(seed: List<Product>) : LocalProductDataSource {
        val stored = seed.associateBy { it.barcode }.toMutableMap()

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
            error("this fake does not implement forgetRecentUse")
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
            error("this fake does not implement restoreRecentUse")
    }

    private class FixedUnits(private val units: List<PortionUnit> = emptyList()) : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> =
            units.filter { it.productBarcode == barcode }

        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> =
            flowOf(units.filter { it.productBarcode == barcode })

        override suspend fun findById(id: Long): PortionUnit? = units.firstOrNull { it.id == id }
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

    /** Production-like aggregate: duplicate variants increment one row's usageCount. */
    private class AggregatingUsage(
        private val delayMs: Long = 0L,
        private var failuresRemaining: Int = 0,
    ) : PortionUsageStore {
        private data class Key(
            val barcode: String,
            val mode: InputMode,
            val portionUnitId: Long?,
            val amount: BigDecimal,
        )

        private val rows = linkedMapOf<Key, PortionUsage>()
        var saveAttempts = 0
            private set

        val saved: List<PortionUsage> get() = rows.values.toList()

        override suspend fun findByBarcode(barcode: String): List<PortionUsage> =
            rows.values.filter { it.productBarcode == barcode }

        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = rows[key(barcode, inputMode, portionUnitId, amount)]

        override suspend fun save(usage: PortionUsage): PortionUsage {
            if (delayMs > 0) delay(delayMs)
            saveAttempts += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                error("simulated usage-store failure")
            }
            val normalized = usage.copy(amount = usage.amount.stripTrailingZeros())
            rows[key(normalized)] = normalized
            return normalized
        }

        override suspend fun delete(usage: PortionUsage) {
            rows.remove(key(usage))
        }

        fun count(
            amount: String,
            mode: InputMode = InputMode.GRAMS,
            portionUnitId: Long? = null,
        ): Int = rows.values.firstOrNull {
            it.inputMode == mode &&
                it.portionUnitId == portionUnitId &&
                it.amount.compareTo(BigDecimal(amount)) == 0
        }?.usageCount ?: 0

        private fun key(usage: PortionUsage) =
            key(usage.productBarcode, usage.inputMode, usage.portionUnitId, usage.amount)

        private fun key(
            barcode: String,
            mode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ) = Key(barcode, mode, portionUnitId, amount.stripTrailingZeros())
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModelWith(
        usage: PortionUsageStore,
        loadedProduct: Product = product(),
        units: PortionUnitStore = FixedUnits(),
    ) = ProductViewModel(
        repository = ProductRepository(
            local = RecordingLocal(listOf(loadedProduct)),
            remote = ImmediateRemote(loadedProduct),
            portionUnits = units,
            meal = NoMeal(),
            portionUsage = usage,
            searchSource = NoSearch(),
            clock = clock,
        ),
        savedState = SavedStateHandle(),
    )

    @Test
    fun `rememberUsageAndAwait does not return before the write completes`() = runTest(dispatcher) {
        val usage = AggregatingUsage(delayMs = 500L)
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        advanceTimeBy(50L)

        var awaitReturned = false
        val job = CoroutineScope(dispatcher).launch {
            viewModel.rememberUsageAndAwait()
            awaitReturned = true
        }

        advanceTimeBy(200L)
        assert(!awaitReturned)
        assertTrue(usage.saved.isEmpty())

        advanceTimeBy(300L)
        job.join()
        assert(awaitReturned)
        assertEquals(1, usage.saved.size)
    }

    @Test
    fun `the snapshot is fixed at call time, not re-read from state during the write`() = runTest(dispatcher) {
        val usage = AggregatingUsage(delayMs = 500L)
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        advanceTimeBy(50L)

        val job = CoroutineScope(dispatcher).launch { viewModel.rememberUsageAndAwait() }
        advanceTimeBy(200L)
        viewModel.onPortionChanged("999")
        advanceTimeBy(300L)
        job.join()

        assertEquals(1, usage.saved.size)
        assertEquals(0, BigDecimal("65").compareTo(usage.saved.single().amount))
    }

    @Test
    fun `an unsaved quick calculation awaits nothing and records no usage`() = runTest(dispatcher) {
        val usage = AggregatingUsage(delayMs = 500L)
        val viewModel = ProductViewModel(
            repository = ProductRepository(
                local = RecordingLocal(emptyList()),
                remote = ImmediateRemote(product()),
                portionUnits = FixedUnits(),
                meal = NoMeal(),
                portionUsage = usage,
                searchSource = NoSearch(),
                clock = clock,
            ),
            savedState = SavedStateHandle(),
        )
        viewModel.startQuickCalculation(BigDecimal("46.0"), NutritionBasis.PER_100_G)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        advanceUntilIdle()

        viewModel.rememberUsageAndAwait()

        assertEquals(0, usage.saved.size)
    }

    @Test
    fun `typing intermediate and final values records no usage until an explicit event`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()

        viewModel.onPortionChanged("6")
        advanceTimeBy(601L)
        viewModel.onPortionChanged("65")
        advanceTimeBy(601L)

        assertEquals(emptyList<PortionUsage>(), usage.saved)
    }

    @Test
    fun `settled typing followed by awaited exit records the final portion exactly once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        advanceTimeBy(601L)

        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("65"))
        assertEquals(1, usage.saved.size)
    }

    @Test
    fun `a remembered prefill does not become a new use by waiting`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = viewModelWith(usage, loadedProduct = product().copy(lastPortion = BigDecimal("65.00")))

        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals("65", viewModel.state.value.portionText)
        assertEquals(emptyList<PortionUsage>(), usage.saved)
    }

    @Test
    fun `successful Add then unchanged awaited exit counts one use`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = loadedWithPortion(usage, "65")

        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("65"))
    }

    @Test
    fun `two separately completed Adds of the same portion count twice`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = loadedWithPortion(usage, "65")

        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()
        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()

        assertEquals(2, usage.count("65"))
    }

    @Test
    fun `Add then edit then exit records each semantic portion once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = loadedWithPortion(usage, "65")
        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()

        viewModel.onPortionChanged("80")
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("65"))
        assertEquals(1, usage.count("80"))
    }

    @Test
    fun `equivalent decimal spellings deduplicate Add from unchanged exit`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = loadedWithPortion(usage, "65")
        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()

        viewModel.onPortionChanged("65.00")
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("65"))
    }

    @Test
    fun `repeated awaited exit of one unchanged snapshot records at most once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val viewModel = loadedWithPortion(usage, "65")

        viewModel.rememberUsageAndAwait()
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("65"))
    }

    @Test
    fun `failed usage write is retried by awaited exit`() = runTest(dispatcher) {
        val usage = AggregatingUsage(failuresRemaining = 1)
        val viewModel = loadedWithPortion(usage, "65")

        viewModel.addCurrentToMeal("65 g")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.usageSaveFailed)
        viewModel.rememberUsageAndAwait()

        assertEquals(2, usage.saveAttempts)
        assertEquals(1, usage.count("65"))
        assertEquals(false, viewModel.state.value.usageSaveFailed)
    }

    @Test
    fun `direct-carb count typing is passive and Add plus unchanged exit counts once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val unit = directCarbUnit()
        val viewModel = viewModelWith(usage, units = FixedUnits(listOf(unit)))
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.switchToPortionUnit(unit.id)

        viewModel.onCountChanged("1")
        advanceTimeBy(601L)
        viewModel.onCountChanged("2")
        advanceTimeBy(601L)
        assertEquals(emptyList<PortionUsage>(), usage.saved)

        viewModel.addCurrentToMeal("2 slices")
        advanceUntilIdle()
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("2", InputMode.PORTION_UNIT, unit.id))
    }

    @Test
    fun `direct-carb Add then count edit then exit records both snapshots once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val unit = directCarbUnit()
        val viewModel = viewModelWith(usage, units = FixedUnits(listOf(unit)))
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.switchToPortionUnit(unit.id)
        viewModel.onCountChanged("2")
        viewModel.addCurrentToMeal("2 slices")
        advanceUntilIdle()

        viewModel.onCountChanged("3.0")
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("2", InputMode.PORTION_UNIT, unit.id))
        assertEquals(1, usage.count("3", InputMode.PORTION_UNIT, unit.id))
    }

    @Test
    fun `weight-based count typing is passive and Add plus unchanged exit counts once`() = runTest(dispatcher) {
        val usage = AggregatingUsage()
        val unit = weightBasedUnit()
        val viewModel = viewModelWith(usage, units = FixedUnits(listOf(unit)))
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.switchToPortionUnit(unit.id)

        viewModel.onCountChanged("1")
        advanceTimeBy(601L)
        viewModel.onCountChanged("2")
        advanceTimeBy(601L)
        assertEquals(emptyList<PortionUsage>(), usage.saved)

        viewModel.addCurrentToMeal("2 slices")
        advanceUntilIdle()
        viewModel.rememberUsageAndAwait()

        assertEquals(1, usage.count("2", InputMode.PORTION_UNIT, unit.id))
    }

    private suspend fun TestScope.loadedWithPortion(
        usage: PortionUsageStore,
        portion: String,
    ): ProductViewModel {
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged(portion)
        return viewModel
    }

    private fun directCarbUnit() = PortionUnit(
        id = 7L,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        verifiedAt = clock.instant(),
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
    )

    private fun weightBasedUnit() = directCarbUnit().copy(
        id = 8L,
        conversion = PortionConversion.WeightBased(
            amountPerUnit = BigDecimal("36"),
            basis = NutritionBasis.PER_100_G,
        ),
    )
}
