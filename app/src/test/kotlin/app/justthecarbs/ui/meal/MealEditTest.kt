package app.justthecarbs.ui.meal

import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.DirectCarbCalculator
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
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RecentUseSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Changing a meal line's portion from the meal screen: the line is recalculated from its own
 * snapshot, written through the repository's existing update, and nothing else is written — no
 * product, no usage history, no *Usual* aggregate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealEditTest {

    private val dispatcher = StandardTestDispatcher()
    private val addedAt = Instant.parse("2026-09-23T08:00:00Z")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Meal(seed: List<MealItem>) : MealStore {
        val items = MutableStateFlow(seed)
        val updates = mutableListOf<MealItem>()
        val adds = mutableListOf<MealItem>()
        override fun observeItems(): Flow<List<MealItem>> = items
        override suspend fun findItems(): List<MealItem> = items.value
        override suspend fun add(item: MealItem): MealItem {
            adds += item
            return item
        }
        override suspend fun update(item: MealItem) {
            updates += item
            items.value = items.value.map { if (it.id == item.id) item else it }
        }
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    /** Fails the test on any product write: a resize is not a use. */
    private class ReadOnlyLocal : LocalProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
        override suspend fun save(product: Product) = error("an edit must not write a product")
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = error("unexpected")
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = error("unexpected")
    }

    /** Fails the test on any usage write: the portion was counted once, when it was added. */
    private class NoUsageWrites : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage = error("an edit must not record usage")
        override suspend fun delete(usage: PortionUsage) = error("an edit must not record usage")
        override suspend fun recordUse(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
            now: Instant,
        ): PortionUsage = error("an edit must not record usage")
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private val weighed = MealItem.weightBased(
        id = 1,
        productBarcode = "111",
        displayName = "Bread",
        portionDescription = "350 g",
        resolvedAmount = BigDecimal("350"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("168.700"),
        addedAt = addedAt,
    )

    private val counted = MealItem.directCarbs(
        id = 2,
        productBarcode = "222",
        displayName = "Crackers",
        portionDescription = "2 crackers",
        count = BigDecimal("2"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("28.4"),
        addedAt = addedAt.plusSeconds(60),
    )

    private fun viewModel(meal: Meal) = MealViewModel(
        ProductRepository(
            local = ReadOnlyLocal(),
            remote = object : ProductDataSource {
                override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
            },
            portionUnits = NoUnits(),
            meal = meal,
            portionUsage = NoUsageWrites(),
            searchSource = object : ProductSearchSource {
                override suspend fun search(terms: String) = ProductSearchResult.NoMatches
            },
        ),
    )

    @Test
    fun `correcting a mistyped weight updates that line only, from its own figures`() = runTest(dispatcher) {
        val meal = Meal(listOf(weighed, counted))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(weighed)
        vm.saveEdit(BigDecimal("35"), "35 g")
        advanceUntilIdle()

        val updated = meal.updates.single()
        assertEquals(weighed.id, updated.id)
        assertEquals(weighed.addedAt, updated.addedAt)
        assertEquals("35 g", updated.portionDescription)
        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("35"), NutritionBasis.PER_100_G).exact,
            updated.exactCarbs,
        )
        assertEquals("the other line is untouched", counted, vm.state.value.items[1])
        assertTrue("an edit never inserts a second line", meal.adds.isEmpty())
        assertNull(vm.state.value.editing)
        assertEquals(
            0,
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("35"), NutritionBasis.PER_100_G).exact
                .add(counted.exactCarbs)
                .compareTo(vm.state.value.total!!.exact),
        )
    }

    @Test
    fun `a counted line is resized by count`() = runTest(dispatcher) {
        val meal = Meal(listOf(counted))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(counted)
        vm.saveEdit(BigDecimal("3"), "3 × 14.2 g carbs")
        advanceUntilIdle()

        val updated = meal.updates.single()
        assertEquals(BigDecimal("3"), updated.count)
        assertEquals(DirectCarbCalculator.exactCarbs(BigDecimal("3"), BigDecimal("14.2")), updated.exactCarbs)
        assertNull(updated.resolvedAmount)
    }

    @Test
    fun `saving the same amount writes nothing and keeps the original wording`() = runTest(dispatcher) {
        val meal = Meal(listOf(counted))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(counted)
        vm.saveEdit(BigDecimal("2.0"), "2.0 × 14.2 g carbs")
        advanceUntilIdle()

        assertTrue(meal.updates.isEmpty())
        assertEquals("2 crackers", meal.items.value.single().portionDescription)
        assertNull("the dialog still closes", vm.state.value.editing)
    }

    @Test
    fun `zero is refused and the dialog stays open`() = runTest(dispatcher) {
        val meal = Meal(listOf(weighed))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(weighed)
        vm.saveEdit(BigDecimal.ZERO, "0 g")
        advanceUntilIdle()

        assertTrue(meal.updates.isEmpty())
        assertEquals(weighed, vm.state.value.editing)
    }

    @Test
    fun `a second Save after the first has nothing left to save`() = runTest(dispatcher) {
        val meal = Meal(listOf(weighed))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(weighed)
        vm.saveEdit(BigDecimal("35"), "35 g")
        vm.saveEdit(BigDecimal("40"), "40 g")
        advanceUntilIdle()

        assertEquals(listOf("35 g"), meal.updates.map { it.portionDescription })
    }

    @Test
    fun `cancelling writes nothing`() = runTest(dispatcher) {
        val meal = Meal(listOf(weighed))
        val vm = viewModel(meal)
        advanceUntilIdle()

        vm.startEdit(weighed)
        vm.cancelEdit()
        vm.saveEdit(BigDecimal("35"), "35 g")
        advanceUntilIdle()

        assertTrue(meal.updates.isEmpty())
        assertNull(vm.state.value.editing)
    }
}
