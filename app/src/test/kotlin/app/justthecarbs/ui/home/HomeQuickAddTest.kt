package app.justthecarbs.ui.home

import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.DirectCarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemKind
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * *Quick Add* from Home, above storage: what reaches the meal, what reaches usage history, and that
 * one tap can only ever write one line.
 *
 * The meal line is compared field by field with what the calculator's own primitives produce, so
 * "the same semantic MealItem the Product flow would create" is asserted rather than assumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeQuickAddTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- fakes -------------------------------------------------------------------------------

    /** A local store that actually stores, because `recordUse` reads the product back. */
    private class StoringLocal(seed: List<Product>) : LocalProductDataSource {
        val products = seed.associateBy { it.barcode }.toMutableMap()
        override suspend fun fetch(barcode: String): ProductFetchResult =
            products[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound
        override suspend fun save(product: Product) {
            products[product.barcode] = product
        }
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(products.values.toList())
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) {}
    }

    /**
     * Records every insert. [gate], when set, holds each insert open until completed — the only way
     * to put a second tap genuinely *inside* the in-flight window rather than after it.
     */
    private class RecordingMeal(var gate: CompletableDeferred<Unit>? = null, var fail: Boolean = false) : MealStore {
        val added = mutableListOf<MealItem>()
        private val items = MutableStateFlow<List<MealItem>>(emptyList())
        override fun observeItems(): Flow<List<MealItem>> = items
        override suspend fun findItems(): List<MealItem> = items.value
        override suspend fun add(item: MealItem): MealItem {
            gate?.await()
            if (fail) error("disk full")
            added += item
            items.value = items.value + item
            return item
        }
        override suspend fun update(item: MealItem) {}
        override suspend fun remove(item: MealItem) {}
        override suspend fun clear() {}
    }

    private class RecordingUsage : PortionUsageStore {
        val rows = mutableListOf<PortionUsage>()
        override suspend fun findByBarcode(barcode: String) = rows.filter { it.productBarcode == barcode }
        override suspend fun findVariant(barcode: String, inputMode: InputMode, portionUnitId: Long?, amount: BigDecimal) =
            rows.firstOrNull {
                it.productBarcode == barcode && it.inputMode == inputMode &&
                    it.portionUnitId == portionUnitId && it.amount.compareTo(amount) == 0
            }
        override suspend fun save(usage: PortionUsage): PortionUsage {
            rows.removeAll { it.productBarcode == usage.productBarcode && it.inputMode == usage.inputMode &&
                it.portionUnitId == usage.portionUnitId && it.amount.compareTo(usage.amount) == 0 }
            rows += usage
            return usage
        }
        override suspend fun delete(usage: PortionUsage) {
            rows.remove(usage)
        }
    }

    private class Units(private val units: List<PortionUnit>) : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String) = units.filter { it.productBarcode == barcode }
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(findByBarcodeNow(barcode))
        private fun findByBarcodeNow(barcode: String) = units.filter { it.productBarcode == barcode }
        override suspend fun findById(id: Long) = units.firstOrNull { it.id == id }
        override suspend fun findByIds(ids: List<Long>) = units.filter { it.id in ids }
        override suspend fun save(unit: PortionUnit) = unit
        override suspend fun delete(unit: PortionUnit) {}
    }

    private val noRemote = object : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
    }
    private val noSearch = object : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private class Rig(
        val vm: HomeViewModel,
        val local: StoringLocal,
        val meal: RecordingMeal,
        val usage: RecordingUsage,
    )

    private fun rig(products: List<Product>, units: List<PortionUnit> = emptyList(), meal: RecordingMeal = RecordingMeal()): Rig {
        val local = StoringLocal(products)
        val usage = RecordingUsage()
        val vm = HomeViewModel(
            ProductRepository(
                local = local,
                remote = noRemote,
                portionUnits = Units(units),
                meal = meal,
                portionUsage = usage,
                searchSource = noSearch,
            ),
        )
        return Rig(vm, local, meal, usage)
    }

    private fun TestScope.events(vm: HomeViewModel): MutableList<QuickAddEvent> {
        val seen = mutableListOf<QuickAddEvent>()
        backgroundScope.launch { vm.quickAddEvents.collect { seen += it } }
        return seen
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private val bread = Product(
        barcode = "111",
        name = "Wholegrain Bread",
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        lastPortion = BigDecimal("72"),
        lastInputMode = InputMode.GRAMS,
        lastUsedAt = Instant.EPOCH,
    )

    private fun unit(conversion: PortionConversion, id: Long = 7, barcode: String = "111") = PortionUnit(
        id = id,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        verifiedAt = Instant.EPOCH,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // ---- the meal line -----------------------------------------------------------------------

    @Test
    fun `a remembered weight adds the calculator's weighed line`() = runTest {
        val rig = rig(listOf(bread))

        rig.vm.quickAdd(RecentEntry(bread, lastUnit = null), "72 g")
        dispatcher.scheduler.runCurrent()

        val item = rig.meal.added.single()
        assertEquals(MealItemKind.WEIGHT_BASED, item.kind)
        assertEquals("111", item.productBarcode)
        assertEquals("Wholegrain Bread", item.displayName)
        assertEquals("72 g", item.portionDescription)
        assertEquals(BigDecimal("72"), item.resolvedAmount)
        assertEquals(NutritionBasis.PER_100_G, item.basis)
        assertEquals(BigDecimal("48.2"), item.carbsPer100)
        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("72"), NutritionBasis.PER_100_G).exact,
            item.exactCarbs,
        )
        assertNull(item.count)
        assertNull(item.carbsPerUnit)
    }

    @Test
    fun `a remembered count against a weight-based unit adds the resolved weighed line`() = runTest {
        val slice = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))
        val p = bread.copy(lastInputMode = InputMode.PORTION_UNIT, lastSelectedPortionUnitId = 7, lastCount = BigDecimal("2"))
        val rig = rig(listOf(p), listOf(slice))

        rig.vm.quickAdd(RecentEntry(p, slice), "2 slices")
        dispatcher.scheduler.runCurrent()

        val item = rig.meal.added.single()
        assertEquals(MealItemKind.WEIGHT_BASED, item.kind)
        assertEquals("2 slices", item.portionDescription)
        assertEquals(BigDecimal("70"), item.resolvedAmount)
        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("70"), NutritionBasis.PER_100_G).exact,
            item.exactCarbs,
        )
    }

    @Test
    fun `a remembered direct-carb count adds a line with no weight`() = runTest {
        val slice = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))
        val p = bread.copy(lastInputMode = InputMode.PORTION_UNIT, lastSelectedPortionUnitId = 7, lastCount = BigDecimal("4"))
        val rig = rig(listOf(p), listOf(slice))

        rig.vm.quickAdd(RecentEntry(p, slice), "4 slices")
        dispatcher.scheduler.runCurrent()

        val item = rig.meal.added.single()
        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertEquals("4 slices", item.portionDescription)
        assertEquals(BigDecimal("4"), item.count)
        assertEquals(BigDecimal("14.2"), item.carbsPerUnit)
        assertEquals(DirectCarbCalculator.exactCarbs(BigDecimal("4"), BigDecimal("14.2")), item.exactCarbs)
        assertNull("no weight is invented on this path", item.resolvedAmount)
        assertNull(item.basis)
        assertNull(item.carbsPer100)
    }

    // ---- usage: the same semantics as the calculator's Add-to-meal --------------------------

    @Test
    fun `quick add records the use exactly as the calculator would`() = runTest {
        val rig = rig(listOf(bread))

        rig.vm.quickAdd(RecentEntry(bread, lastUnit = null), "72 g")
        dispatcher.scheduler.runCurrent()

        val stored = rig.local.products.getValue("111")
        assertTrue("floats up Recents", stored.lastUsedAt!!.isAfter(Instant.EPOCH))
        assertEquals(BigDecimal("72"), stored.lastPortion)
        assertEquals(InputMode.GRAMS, stored.lastInputMode)
        // Feeds *Usual* with the variant the user chose.
        val usage = rig.usage.rows.single()
        assertEquals(InputMode.GRAMS, usage.inputMode)
        assertEquals(0, BigDecimal("72").compareTo(usage.amount))
    }

    /**
     * The direct-carb use must not overwrite the product's remembered weight with the count —
     * `recordUse` is given a null portion, exactly as the calculator gives it.
     */
    @Test
    fun `a direct-carb quick add preserves the earlier weight and records the count`() = runTest {
        val slice = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))
        val p = bread.copy(
            lastPortion = BigDecimal("65"),
            lastInputMode = InputMode.PORTION_UNIT,
            lastSelectedPortionUnitId = 7,
            lastCount = BigDecimal("4"),
        )
        val rig = rig(listOf(p), listOf(slice))

        rig.vm.quickAdd(RecentEntry(p, slice), "4 slices")
        dispatcher.scheduler.runCurrent()

        val stored = rig.local.products.getValue("111")
        assertEquals(BigDecimal("65"), stored.lastPortion)
        assertEquals(BigDecimal("4"), stored.lastCount)
        assertEquals(7L, stored.lastSelectedPortionUnitId)
        val usage = rig.usage.rows.single()
        assertEquals(InputMode.PORTION_UNIT, usage.inputMode)
        assertEquals(7L, usage.portionUnitId)
        assertEquals(0, BigDecimal("4").compareTo(usage.amount))
    }

    // ---- refusals ----------------------------------------------------------------------------

    @Test
    fun `a favourite with no remembered portion adds nothing`() = runTest {
        val never = bread.copy(favorite = true, lastPortion = null, lastInputMode = null, lastUsedAt = null)
        val rig = rig(listOf(never))

        rig.vm.quickAdd(RecentEntry(never, lastUnit = null), "whatever the screen said")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(rig.meal.added.isEmpty())
        assertTrue(rig.vm.quickAdd.value.isEmpty())
    }

    @Test
    fun `a count whose unit cannot be resolved adds nothing`() = runTest {
        val p = bread.copy(lastInputMode = InputMode.PORTION_UNIT, lastSelectedPortionUnitId = 7, lastCount = BigDecimal("2"))
        val rig = rig(listOf(p))

        rig.vm.quickAdd(RecentEntry(p, lastUnit = null), "72 g")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(rig.meal.added.isEmpty())
    }

    // ---- single execution --------------------------------------------------------------------

    /** Two taps while the insert is genuinely still open: one line. */
    @Test
    fun `a second tap while the write is in flight adds nothing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = rig(listOf(bread), meal = RecordingMeal(gate = gate))
        val entry = RecentEntry(bread, lastUnit = null)

        rig.vm.quickAdd(entry, "72 g")
        dispatcher.scheduler.runCurrent()
        assertEquals(QuickAddStatus.IN_FLIGHT, rig.vm.quickAdd.value["111"])
        rig.vm.quickAdd(entry, "72 g")
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, rig.meal.added.size)
    }

    /**
     * The realistic double tap: the first insert has already *finished* when the second tap lands,
     * because Room returns in milliseconds. A guard that only covered the in-flight window would let
     * this one through — the reason the confirmation window is non-repeatable too.
     */
    @Test
    fun `a second tap just after the write landed still adds nothing`() = runTest {
        val rig = rig(listOf(bread))
        val entry = RecentEntry(bread, lastUnit = null)

        rig.vm.quickAdd(entry, "72 g")
        dispatcher.scheduler.runCurrent()
        assertEquals(QuickAddStatus.ADDED, rig.vm.quickAdd.value["111"])
        dispatcher.scheduler.advanceTimeBy(150)
        rig.vm.quickAdd(entry, "72 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(1, rig.meal.added.size)
    }

    @Test
    fun `after the confirmation a deliberate second portion can be added`() = runTest {
        val rig = rig(listOf(bread))
        val entry = RecentEntry(bread, lastUnit = null)

        rig.vm.quickAdd(entry, "72 g")
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(HomeViewModel.CONFIRMATION_MS + 1)
        dispatcher.scheduler.runCurrent()
        assertNull("idle again", rig.vm.quickAdd.value["111"])

        rig.vm.quickAdd(entry, "72 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(2, rig.meal.added.size)
    }

    /** Per product, not a lock on Home. */
    @Test
    fun `another product can be added while the first is still confirming`() = runTest {
        val yoghurt = bread.copy(barcode = "222", name = "Yoghurt", lastPortion = BigDecimal("150"))
        val rig = rig(listOf(bread, yoghurt))

        rig.vm.quickAdd(RecentEntry(bread, null), "72 g")
        dispatcher.scheduler.runCurrent()
        rig.vm.quickAdd(RecentEntry(yoghurt, null), "150 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("111", "222"), rig.meal.added.map { it.productBarcode })
    }

    // ---- outcomes ----------------------------------------------------------------------------

    @Test
    fun `success emits one added event for the haptic`() = runTest {
        val rig = rig(listOf(bread))
        val seen = events(rig.vm)

        rig.vm.quickAdd(RecentEntry(bread, null), "72 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf<QuickAddEvent>(QuickAddEvent.Added("111")), seen)
    }

    @Test
    fun `a failed write reports itself and leaves the card tappable`() = runTest {
        val rig = rig(listOf(bread), meal = RecordingMeal(fail = true))
        val seen = events(rig.vm)

        rig.vm.quickAdd(RecentEntry(bread, null), "72 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf<QuickAddEvent>(QuickAddEvent.Failed("Wholegrain Bread")), seen)
        assertNull(rig.vm.quickAdd.value["111"])
        // And nothing was recorded as used: the portion never reached the meal.
        assertNotNull(rig.local.products["111"])
        assertEquals(Instant.EPOCH, rig.local.products.getValue("111").lastUsedAt)
    }
}
