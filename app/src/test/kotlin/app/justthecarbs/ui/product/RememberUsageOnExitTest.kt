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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * P0 §3: back navigation must not silently cancel the "remember this portion" write.
 *
 * The previous NavHost `onBack` did `viewModel.rememberUsage(); navController.popBackStack()` —
 * `rememberUsage()` launches into `viewModelScope` and returns immediately, so the pop right after
 * it could destroy this `NavBackStackEntry`-scoped ViewModel and cancel the write before Room ever
 * ran. `rememberUsageAndAwait()` is the fix: it suspends until the write actually lands, so a
 * caller that awaits it before navigating cannot race it.
 */
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

    private class RecordingLocal(seed: List<Product> = emptyList()) : LocalProductDataSource {
        val stored = seed.associateBy { it.barcode }.toMutableMap()

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
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

    /** A usage store whose `save` suspends, standing in for a slow Room write on the way out. */
    private class DelayedUsage(private val delayMs: Long) : PortionUsageStore {
        val saved = mutableListOf<PortionUsage>()

        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null

        override suspend fun save(usage: PortionUsage): PortionUsage {
            delay(delayMs)
            saved += usage
            return usage
        }

        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModelWith(usage: PortionUsageStore) = ProductViewModel(
        repository = ProductRepository(
            local = RecordingLocal(),
            remote = ImmediateRemote(product()),
            portionUnits = NoUnits(),
            meal = NoMeal(),
            portionUsage = usage,
            searchSource = NoSearch(),
            clock = clock,
        ),
        savedState = SavedStateHandle(),
    )

    /**
     * The core P0 §3 guarantee: `rememberUsageAndAwait()` does not return until the write has
     * actually landed. A caller sequencing `awaitRememberUsage(); popBackStack()` — exactly what the
     * NavHost's fixed `onBack` does — therefore cannot navigate before persistence completes.
     */
    @Test
    fun `rememberUsageAndAwait does not return before the write completes`() = runTest(dispatcher) {
        val usage = DelayedUsage(delayMs = 500L)
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        // Deliberately short of the 600ms debounce in `init`'s typing-settle collector — settling
        // that collector here would consume `DelayedUsage`'s single delay itself and confound the
        // explicit `rememberUsageAndAwait()` call below with a write neither call site intended.
        advanceTimeBy(50L)
        usage.saved.clear()

        var awaitReturned = false
        val job = CoroutineScope(dispatcher).launch {
            viewModel.rememberUsageAndAwait()
            awaitReturned = true
        }

        advanceTimeBy(200L)
        assert(!awaitReturned) { "awaitRememberUsage must not return before the write completes" }
        assert(usage.saved.isEmpty()) { "precondition: the write must still be in flight at 200ms of a 500ms delay" }

        // Exact time, not `advanceUntilIdle()`: the latter would also drain the still-pending
        // 600ms typing-settle debounce from `init` (only 250ms elapsed so far) and let it fire a
        // second, unrelated write — a confound of the fixture, not evidence about the code under
        // test. 300ms lands exactly on the explicit call's 500ms delay without reaching the
        // debounce's remaining ~350ms.
        advanceTimeBy(300L)
        job.join()
        assert(awaitReturned) { "awaitRememberUsage must return once the write has landed" }
        assertEquals(1, usage.saved.size)
    }

    /** All fields, including `selectedPortionUnitId`, are snapshotted before the write — never re-read live. */
    @Test
    fun `the snapshot is fixed at call time, not re-read from state during the write`() = runTest(dispatcher) {
        val usage = DelayedUsage(delayMs = 500L)
        val viewModel = viewModelWith(usage)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")
        // Same reason as above: stay under the 600ms debounce so its own write cannot land during
        // this test and be mistaken for the explicitly-awaited one.
        advanceTimeBy(50L)
        usage.saved.clear()

        val job = CoroutineScope(dispatcher).launch {
            viewModel.rememberUsageAndAwait()
        }

        // Mutate the portion while the write is in flight — still under 600ms total, so the
        // debounce collector cannot fire from this edit either.
        advanceTimeBy(200L)
        viewModel.onPortionChanged("999")

        advanceTimeBy(300L)
        job.join()

        assertEquals(1, usage.saved.size)
        assertEquals(
            "the recorded amount must be the value at call time (65), not the later edit (999)",
            0,
            BigDecimal("65").compareTo(usage.saved.single().amount),
        )
    }

    /** A quick calculation (`unsaved`) records no usage — the same rule `rememberUsage()` already follows. */
    @Test
    fun `an unsaved quick calculation awaits nothing and records no usage`() = runTest(dispatcher) {
        val usage = DelayedUsage(delayMs = 500L)
        val viewModel = ProductViewModel(
            repository = ProductRepository(
                local = RecordingLocal(),
                remote = ImmediateRemote(product()),
                portionUnits = NoUnits(),
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

        assertEquals("an unsaved calculation must never record usage", 0, usage.saved.size)
    }
}
