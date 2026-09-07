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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * "Add & scan next" must not lose the meal insert to navigation (P0 §1), meal additions must use
 * one coherent state snapshot rather than mixed-moment reads (P0 §2), and a rapid double tap must
 * not duplicate the write (P0 §1/§2 "single-flight").
 *
 * The previous [ProductViewModel.addCurrentToMeal] launched a fire-and-forget coroutine and read
 * `_state.value` piecemeal *inside* it, and the NavHost's *Add & scan next* callback called it and
 * then immediately navigated — which can pop the Product route, destroying this
 * `NavBackStackEntry`-scoped ViewModel and cancelling `viewModelScope` before Room ever runs. This
 * file drives that exact shape with a deliberately delayed [MealStore] fake, standing in for the
 * NavHost's navigation by asserting on [ProductViewModel.navigationEvents] instead — the two are
 * equivalent because the fix is "do not signal readiness to navigate until the write lands", and
 * that signal is exactly what the NavHost's `LaunchedEffect` collects to decide when to navigate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToMealTransactionTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC)
    private val barcode = "8712100849060"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun product(carbs: String = "46.0") = Product(
        barcode = barcode,
        name = "Hagelslag",
        carbsPer100 = BigDecimal(carbs),
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

    /**
     * The fixture this whole file depends on: a meal store whose [add] suspends for [delayMs]
     * before returning, standing in for a slow Room insert. Every added item is recorded so the
     * test can assert exactly one row was written.
     */
    private class DelayedMeal(private val delayMs: Long) : MealStore {
        val added = mutableListOf<MealItem>()

        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()

        override suspend fun add(item: MealItem): MealItem {
            delay(delayMs)
            added += item
            return item
        }

        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private class RecordingUsage : PortionUsageStore {
        val recorded = mutableListOf<PortionUsage>()

        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null

        override suspend fun save(usage: PortionUsage): PortionUsage {
            recorded += usage
            return usage
        }

        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModelWith(meal: MealStore, local: LocalProductDataSource = RecordingLocal()) =
        ProductViewModel(
            repository = ProductRepository(
                local = local,
                remote = ImmediateRemote(product()),
                portionUnits = NoUnits(),
                meal = meal,
                portionUsage = RecordingUsage(),
                searchSource = NoSearch(),
                clock = clock,
            ),
            savedState = SavedStateHandle(),
        )

    // ---- "Add & scan next" cannot lose the insert to navigation --------------------------------

    /**
     * The core P0 §1 scenario: tap *Add & scan next*, navigation must not happen yet, complete the
     * insert, assert exactly one row exists and navigation fires exactly once.
     */
    @Test
    fun `add and scan next does not signal navigation until the insert completes`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 500L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        val events = mutableListOf<ProductNavigationEvent>()
        val collector = CoroutineScope(dispatcher).launch {
            viewModel.navigationEvents.collect { events += it }
        }

        viewModel.addCurrentToMeal("65 g", scanNext = true)

        // Deliberately before the delayed insert completes.
        advanceTimeBy(200L)
        assertTrue(
            "navigation must not happen before the insert completes",
            events.isEmpty(),
        )
        assertTrue(
            "precondition: the insert must still be in flight",
            meal.added.isEmpty(),
        )

        advanceUntilIdle()

        assertEquals("exactly one row must exist", 1, meal.added.size)
        assertEquals(
            "navigation must fire exactly once",
            listOf(ProductNavigationEvent.ScanNext),
            events,
        )
        collector.cancel()
    }

    /** Ordinary *Add to meal* (no scan-next) must never emit a navigation event at all. */
    @Test
    fun `ordinary add to meal never emits a navigation event`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 100L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        val events = mutableListOf<ProductNavigationEvent>()
        val collector = CoroutineScope(dispatcher).launch {
            viewModel.navigationEvents.collect { events += it }
        }

        viewModel.addCurrentToMeal("65 g", scanNext = false)
        advanceUntilIdle()

        assertEquals("exactly one row must exist", 1, meal.added.size)
        assertTrue("ordinary Add to meal must never navigate", events.isEmpty())
        collector.cancel()
    }

    /**
     * Negative control for the fixture itself: without the delay, both assertions above would pass
     * trivially even with the pre-fix fire-and-forget code, because the write would already have
     * completed by the time anything checks. The delay is what makes this test able to fail.
     */
    @Test
    fun `precondition - the delayed meal store actually suspends`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 500L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        viewModel.addCurrentToMeal("65 g", scanNext = true)
        advanceTimeBy(200L)

        assertTrue("the fixture must still be mid-write at 200ms of a 500ms delay", meal.added.isEmpty())
    }

    // ---- immutable snapshot: a fast edit after tapping must not change what is written ----------

    /**
     * P0 §2: the previous implementation read `_state.value.result`/`portionText`/`countText`
     * *inside* the launched coroutine, so an edit that lands after suspension begins but before the
     * repository call runs could blend into the write. This drives exactly that timing with a
     * delayed meal store standing in for a slow Room insert, edits the portion between the tap and
     * completion, and asserts the persisted item is entirely the value on screen at tap time.
     */
    @Test
    fun `a fast edit after tapping Add does not change what gets persisted`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 500L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()

        // Calculation A: 65 g of a 46.0 g/100g product.
        viewModel.onPortionChanged("65")
        advanceUntilIdle()
        val exactA = viewModel.state.value.result?.exact
        checkNotNull(exactA) { "precondition: calculation A must have a result" }

        viewModel.addCurrentToMeal("65 g", scanNext = false)

        // Immediately mutate state to calculation B, while the write is still in flight.
        viewModel.onPortionChanged("130")
        advanceUntilIdle()
        val exactB = viewModel.state.value.result?.exact
        checkNotNull(exactB) { "precondition: calculation B must have a result" }
        assertFalse("precondition: A and B must actually differ", exactA == exactB)

        assertEquals("exactly one row must have been written", 1, meal.added.size)
        val persisted = meal.added.single()
        assertEquals(
            "the persisted item must carry calculation A's exact figure, not B's",
            exactA,
            persisted.exactCarbs,
        )
        assertEquals(
            "the persisted item's description must be A's, not a value mixed with B's",
            "65 g",
            persisted.portionDescription,
        )
    }

    // ---- single-flight: a rapid double tap must not duplicate the write --------------------------

    /** Two calls to `addCurrentToMeal` before the first's write completes must insert only once. */
    @Test
    fun `a rapid double tap on Add to meal inserts only once`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 500L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        viewModel.addCurrentToMeal("65 g", scanNext = false)
        // Before the first write has completed.
        viewModel.addCurrentToMeal("65 g", scanNext = false)
        advanceUntilIdle()

        assertEquals(
            "a rapid double tap must not insert twice",
            1,
            meal.added.size,
        )
    }

    /** After a successful add, [ProductUiState.addingToMeal] is released so a later tap can proceed. */
    @Test
    fun `the guard releases after a successful add, so a later tap can add again`() = runTest(dispatcher) {
        val meal = DelayedMeal(delayMs = 100L)
        val viewModel = viewModelWith(meal)
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        viewModel.addCurrentToMeal("65 g", scanNext = false)
        advanceUntilIdle()
        assertFalse("the guard must release once the write completes", viewModel.state.value.addingToMeal)

        viewModel.addCurrentToMeal("65 g", scanNext = false)
        advanceUntilIdle()

        assertEquals("a later, separate tap must add a second row", 2, meal.added.size)
    }

    // ---- failure path: persistence failing must not pretend success ------------------------------

    private class FailingMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = error("simulated persistence failure")
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    @Test
    fun `a failed add releases the guard, reports failure, and never navigates`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FailingMeal())
        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("65")

        val events = mutableListOf<ProductNavigationEvent>()
        val collector = CoroutineScope(dispatcher).launch {
            viewModel.navigationEvents.collect { events += it }
        }

        viewModel.addCurrentToMeal("65 g", scanNext = true)
        advanceUntilIdle()

        assertFalse("the guard must release on failure", viewModel.state.value.addingToMeal)
        assertTrue("the failure must be reported", viewModel.state.value.mealAddFailed)
        assertTrue("a failed write must never navigate", events.isEmpty())
        collector.cancel()
    }

}
