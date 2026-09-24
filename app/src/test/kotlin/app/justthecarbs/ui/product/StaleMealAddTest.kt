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
import app.justthecarbs.domain.RecentUseSnapshot
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The calculator's *Add to meal* onto a meal that has gone quiet (see
 * [app.justthecarbs.domain.MealStaleness]): it asks before writing, writes exactly the item the
 * user tapped for once answered, and a dismissed question changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleMealAddTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-09-23T08:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val barcode = "8712100849060"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- fakes -------------------------------------------------------------------------------

    private class StoredMeal(seed: List<MealItem>, private val failAdd: Boolean = false) : MealStore {
        val items = MutableStateFlow(seed)
        /** Every write in order, so clear-before-add is asserted rather than assumed. */
        val log = mutableListOf<String>()
        override fun observeItems(): Flow<List<MealItem>> = items
        override suspend fun findItems(): List<MealItem> = items.value
        override suspend fun add(item: MealItem): MealItem {
            log += "add"
            if (failAdd) error("disk full")
            items.value = items.value + item
            return item
        }
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() {
            log += "clear"
            items.value = emptyList()
        }
    }

    private class StoringLocal : LocalProductDataSource {
        val stored = mutableMapOf<String, Product>()
        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound
        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = Unit
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class RecordingUsage : PortionUsageStore {
        val saved = mutableListOf<PortionUsage>()
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage {
            saved += usage
            return usage
        }
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private val product = Product(
        barcode = barcode,
        name = "Hagelslag",
        carbsPer100 = BigDecimal("46.0"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    private fun dinnerLine(hoursAgo: Long) = MealItem.weightBased(
        id = 1,
        productBarcode = "999",
        displayName = "Pasta",
        portionDescription = "250 g",
        resolvedAmount = BigDecimal("250"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("30"),
        exactCarbs = BigDecimal("75"),
        addedAt = now.minus(Duration.ofHours(hoursAgo)),
    )

    private class Rig(val vm: ProductViewModel, val meal: StoredMeal, val local: StoringLocal)

    private suspend fun kotlinx.coroutines.test.TestScope.loaded(meal: StoredMeal): Rig {
        val local = StoringLocal()
        val vm = ProductViewModel(
            repository = ProductRepository(
                local = local,
                remote = object : ProductDataSource {
                    override suspend fun fetch(barcode: String) = ProductFetchResult.Found(product)
                },
                portionUnits = NoUnits(),
                meal = meal,
                portionUsage = RecordingUsage(),
                searchSource = object : ProductSearchSource {
                    override suspend fun search(terms: String) = ProductSearchResult.NoMatches
                },
                clock = clock,
            ),
            savedState = SavedStateHandle(),
        )
        vm.load(barcode)
        advanceUntilIdle()
        vm.onPortionChanged("65")
        return Rig(vm, meal, local)
    }

    // ---- tests -------------------------------------------------------------------------------

    @Test
    fun `adding onto a meal quiet for two hours asks first and writes nothing`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 2))))
        val events = mutableListOf<ProductNavigationEvent>()
        val collector = CoroutineScope(dispatcher).launch { rig.vm.navigationEvents.collect { events += it } }

        rig.vm.addCurrentToMeal("65 g", scanNext = true)
        advanceUntilIdle()

        val question = rig.vm.state.value.staleMeal
        assertNotNull(question)
        assertEquals(1, question!!.itemCount)
        assertTrue("nothing is written while the question is open", rig.meal.log.isEmpty())
        assertFalse(rig.vm.state.value.addingToMeal)
        assertFalse(rig.vm.state.value.mealAddFailed)
        assertTrue("scan-next waits for the answer", events.isEmpty())
        assertNull("no usage is recorded for an add that has not happened", rig.local.stored[barcode]?.lastUsedAt)
        collector.cancel()
    }

    @Test
    fun `starting a new meal clears first, then writes the item as it was when tapped`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 9))))
        val events = mutableListOf<ProductNavigationEvent>()
        val collector = CoroutineScope(dispatcher).launch { rig.vm.navigationEvents.collect { events += it } }

        rig.vm.addCurrentToMeal("65 g", scanNext = true)
        advanceUntilIdle()
        // Typing while the question is open cannot change what the answer writes.
        rig.vm.onPortionChanged("500")
        rig.vm.resolveStaleMeal(startNewMeal = true)
        advanceUntilIdle()

        assertEquals(listOf("clear", "add"), rig.meal.log)
        val line = rig.meal.items.value.single()
        assertEquals(barcode, line.productBarcode)
        assertEquals(0, BigDecimal("65").compareTo(line.resolvedAmount))
        assertEquals("65 g", line.portionDescription)
        assertNull(rig.vm.state.value.staleMeal)
        assertEquals(listOf(ProductNavigationEvent.ScanNext), events)
        assertEquals("the add is a use, as always", now, rig.local.stored.getValue(barcode).lastUsedAt)
        collector.cancel()
    }

    @Test
    fun `adding to the quiet meal keeps its lines`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 3))))

        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()
        rig.vm.resolveStaleMeal(startNewMeal = false)
        advanceUntilIdle()

        assertEquals(listOf("add"), rig.meal.log)
        assertEquals(listOf("999", barcode), rig.meal.items.value.map { it.productBarcode })
    }

    @Test
    fun `a dismissed question adds nothing and clears nothing`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 9))))

        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()
        rig.vm.dismissStaleMeal()
        // A late answer after dismissal has nothing left to write.
        rig.vm.resolveStaleMeal(startNewMeal = true)
        advanceUntilIdle()

        assertTrue(rig.meal.log.isEmpty())
        assertNull(rig.vm.state.value.staleMeal)
        assertFalse(rig.vm.state.value.addingToMeal)
    }

    @Test
    fun `a second Add while the question is open does nothing`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 9))))

        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()
        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()
        rig.vm.resolveStaleMeal(startNewMeal = false)
        advanceUntilIdle()

        assertEquals(listOf("add"), rig.meal.log)
    }

    @Test
    fun `a meal added to under two hours ago is not questioned`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 1))))

        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()

        assertNull(rig.vm.state.value.staleMeal)
        assertEquals(listOf("999", barcode), rig.meal.items.value.map { it.productBarcode })
    }

    @Test
    fun `a failed add after starting a new meal leaves it empty and says so`() = runTest(dispatcher) {
        val rig = loaded(StoredMeal(listOf(dinnerLine(hoursAgo = 9)), failAdd = true))

        rig.vm.addCurrentToMeal("65 g")
        advanceUntilIdle()
        rig.vm.resolveStaleMeal(startNewMeal = true)
        advanceUntilIdle()

        assertEquals(listOf("clear", "add"), rig.meal.log)
        assertTrue("never the old lines plus a missing new one", rig.meal.items.value.isEmpty())
        assertTrue(rig.vm.state.value.mealAddFailed)
        assertFalse(rig.vm.state.value.addingToMeal)
    }
}
