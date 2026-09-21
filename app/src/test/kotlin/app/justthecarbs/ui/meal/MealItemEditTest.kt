package app.justthecarbs.ui.meal

import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemCorrection
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionAdjustment
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
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.home.HomeViewModel
import app.justthecarbs.ui.home.RecentEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Correcting a meal line from the meal screen, above storage (1.0.8, *Edit current meal item*).
 *
 * The meal store here keeps a [CommittingMeal.history] of every state it committed, so "the old and
 * the new line never count together" is asserted on the writes themselves rather than on a
 * conflated StateFlow that could hide an add-then-remove between two frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealItemEditTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- fakes -------------------------------------------------------------------------------

    /**
     * An in-memory meal with the DAO's ordering. Counts each kind of write and records every list it
     * committed. [gate] holds an update open until completed; [failUpdates] makes the disk refuse.
     */
    private class CommittingMeal(seed: List<MealItem>) : MealStore {
        private var nextId = 1_000L
        private val flow = MutableStateFlow(sorted(seed))
        val history = mutableListOf(flow.value)
        var adds = 0
        var updates = 0
        var removes = 0
        var gate: CompletableDeferred<Unit>? = null
        var failUpdates = false

        val items: List<MealItem> get() = flow.value
        val writes: Int get() = adds + updates + removes

        override fun observeItems(): Flow<List<MealItem>> = flow.asStateFlow()
        override suspend fun findItems(): List<MealItem> = flow.value

        override suspend fun add(item: MealItem): MealItem {
            adds++
            val saved = item.copy(id = nextId++)
            commit(flow.value + saved)
            return saved
        }

        override suspend fun update(item: MealItem) {
            gate?.await()
            if (failUpdates) error("disk full")
            updates++
            commit(flow.value.map { if (it.id == item.id) item else it })
        }

        override suspend fun remove(item: MealItem) {
            removes++
            commit(flow.value.filterNot { it.id == item.id })
        }

        override suspend fun clear() = commit(emptyList())

        private fun commit(next: List<MealItem>) {
            flow.value = sorted(next)
            history += flow.value
        }

        private companion object {
            fun sorted(items: List<MealItem>) = items.sortedWith(compareBy({ it.addedAt }, { it.id }))
        }
    }

    private class StoringLocal(seed: List<Product>) : LocalProductDataSource {
        val products = seed.associateBy { it.barcode }.toMutableMap()
        override suspend fun fetch(barcode: String): ProductFetchResult =
            products[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound
        override suspend fun save(product: Product) {
            products[product.barcode] = product
        }
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(products.values.toList())

        // Column-accurate, like the real `UPDATE`s and `@Transaction` in `ProductDao` (1.0.8
        // lost-update hardening). Implementing these as whole-row copies would make every
        // preservation test in this repo pass while the defect they exist to catch sat in
        // production — the trap `LocalAliasTest` already records for the alias write.
        override suspend fun setFavorite(barcode: String, favorite: Boolean) {
            products[barcode] = products[barcode]?.copy(favorite = favorite) ?: return
        }

        override suspend fun recordUsageColumns(
            barcode: String,
            lastPortion: java.math.BigDecimal?,
            lastUsedAt: java.time.Instant,
            lastInputMode: app.justthecarbs.domain.InputMode?,
            lastSelectedPortionUnitId: Long?,
            lastCount: java.math.BigDecimal?,
        ) {
            val existing = products[barcode] ?: return
            products[barcode] = existing.copy(
                lastPortion = lastPortion ?: existing.lastPortion,
                lastUsedAt = lastUsedAt,
                lastInputMode = lastInputMode ?: existing.lastInputMode,
                lastSelectedPortionUnitId =
                    if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) null
                    else lastSelectedPortionUnitId ?: existing.lastSelectedPortionUnitId,
                lastCount =
                    if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) null
                    else lastCount ?: existing.lastCount,
            )
        }

        override suspend fun saveProductFacts(product: Product) {
            val current = products[product.barcode]
            save(
                if (current == null) product
                else product.copy(localAlias = current.localAlias, favorite = current.favorite),
            )
        }
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) {}
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
            rows.removeAll {
                it.productBarcode == usage.productBarcode && it.inputMode == usage.inputMode &&
                    it.portionUnitId == usage.portionUnitId && it.amount.compareTo(usage.amount) == 0
            }
            rows += usage
            return usage
        }
        override suspend fun delete(usage: PortionUsage) {
            rows.remove(usage)
        }
    }

    private object NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String) = emptyList<PortionUnit>()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit) = unit
        override suspend fun delete(unit: PortionUnit) {}
    }

    /** Fails the test if anything reaches for the network: a correction has no reason to. */
    private val noRemote = object : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult =
            error("a meal-line correction must never look a product up")
    }
    private val noSearch = object : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private class Rig(
        val vm: MealViewModel,
        val repository: ProductRepository,
        val meal: CommittingMeal,
        val local: StoringLocal,
        val usage: RecordingUsage,
    ) {
        val editor: MealItemEditor get() = checkNotNull(vm.state.value.editor) { "no editor open" }
    }

    private fun TestScope.rig(items: List<MealItem>, products: List<Product> = emptyList()): Rig {
        val meal = CommittingMeal(items)
        val local = StoringLocal(products)
        val usage = RecordingUsage()
        val repository = ProductRepository(
            local = local,
            remote = noRemote,
            portionUnits = NoUnits,
            meal = meal,
            portionUsage = usage,
            searchSource = noSearch,
        )
        val vm = MealViewModel(repository)
        dispatcher.scheduler.runCurrent()
        return Rig(vm, repository, meal, local, usage)
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private val addedAt = Instant.parse("2026-09-18T12:00:00Z")

    private val bread = MealItem.weightBased(
        id = 42,
        productBarcode = "111",
        displayName = "Wholegrain Bread",
        portionDescription = "2 slices",
        resolvedAmount = BigDecimal("70"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("33.740"),
        addedAt = addedAt,
    )

    private val juice = MealItem.weightBased(
        id = 43,
        productBarcode = "222",
        displayName = "Orange Juice",
        portionDescription = "200 ml",
        resolvedAmount = BigDecimal("200"),
        basis = NutritionBasis.PER_100_ML,
        carbsPer100 = BigDecimal("9.4"),
        exactCarbs = BigDecimal("18.800"),
        addedAt = addedAt.plusSeconds(60),
    )

    private val crispbread = MealItem.directCarbs(
        id = 44,
        productBarcode = "333",
        displayName = "Crispbread",
        portionDescription = "4 slices",
        count = BigDecimal("4"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("56.8"),
        addedAt = addedAt.plusSeconds(120),
    )

    /** The bread's product as it is *now*: reformulated since the line was added. */
    private val breadProductToday = Product(
        barcode = "111",
        name = "Wholegrain Bread",
        carbsPer100 = BigDecimal("60.0"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        lastPortion = BigDecimal("70"),
        lastInputMode = InputMode.GRAMS,
        lastUsedAt = Instant.EPOCH,
    )

    private fun assertSameAmount(expected: String, actual: BigDecimal?) {
        assertNotNull("expected $expected", actual)
        assertEquals("expected $expected, was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    // ---- opening -----------------------------------------------------------------------------

    @Test
    fun `a weighed line opens at the amount it resolved to`() = runTest {
        val rig = rig(listOf(bread))

        rig.vm.editItem(bread)

        assertEquals(bread, rig.editor.item)
        assertEquals("70", rig.editor.amountText)
        assertFalse("nothing has changed yet", rig.editor.canSave)
    }

    @Test
    fun `a direct-carb line opens at its count`() = runTest {
        val rig = rig(listOf(crispbread))

        rig.vm.editItem(crispbread)

        assertEquals("4", rig.editor.amountText)
    }

    // ---- live recalculation ------------------------------------------------------------------

    @Test
    fun `typing recalculates from the line's own snapshot, not the product as it is now`() = runTest {
        val rig = rig(listOf(bread), products = listOf(breadProductToday))

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")

        // 48.2 × 85 / 100 = 40.97 — the snapshot's figure. Today's 60.0 would give 51.
        assertSameAmount("40.97", rig.editor.exactCarbs)
        assertTrue(rig.editor.canSave)
    }

    @Test
    fun `a direct-carb count recalculates by its carbs per unit`() = runTest {
        val rig = rig(listOf(crispbread))

        rig.vm.editItem(crispbread)
        rig.vm.onEditAmountChange("3")

        assertSameAmount("42.6", rig.editor.exactCarbs)
    }

    // ---- saving ------------------------------------------------------------------------------

    @Test
    fun `saving replaces the line in place`() = runTest {
        val rig = rig(listOf(bread))

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        val saved = rig.meal.items.single()
        assertEquals(42L, saved.id)
        assertEquals("111", saved.productBarcode)
        assertEquals("Wholegrain Bread", saved.displayName)
        assertEquals(addedAt, saved.addedAt)
        assertEquals(MealItemKind.WEIGHT_BASED, saved.kind)
        assertEquals("85 g", saved.portionDescription)
        assertEquals(BigDecimal("85"), saved.resolvedAmount)
        assertEquals(NutritionBasis.PER_100_G, saved.basis)
        assertEquals(BigDecimal("48.2"), saved.carbsPer100)
        assertSameAmount("40.97", saved.exactCarbs)

        assertEquals("one update, nothing added or removed", 1, rig.meal.updates)
        assertEquals(0, rig.meal.adds)
        assertEquals(0, rig.meal.removes)
        assertEquals(MealEditStatus.SAVED, rig.editor.status)
        assertEquals(listOf(saved), rig.vm.state.value.items)
    }

    @Test
    fun `the total moves once, straight from the old figure to the new`() = runTest {
        val rig = rig(listOf(bread, juice))
        val totals = mutableListOf<BigDecimal?>()
        backgroundScope.launch { rig.vm.state.collect { totals += it.total?.exact } }
        dispatcher.scheduler.runCurrent()

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        // Every state the store ever committed has exactly the two lines: the old bread was replaced,
        // never added to (three lines) or removed first (one line).
        assertEquals(2, rig.meal.history.size)
        assertTrue(rig.meal.history.all { it.size == 2 })

        // 33.74 + 18.8 = 52.54 before; 40.97 + 18.8 = 59.77 after, with nothing in between.
        val distinct = totals.filterNotNull().fold(mutableListOf<BigDecimal>()) { seen, total ->
            if (seen.lastOrNull()?.compareTo(total) != 0) seen += total
            seen
        }
        assertEquals(2, distinct.size)
        assertSameAmount("52.54", distinct[0])
        assertSameAmount("59.77", distinct[1])
    }

    @Test
    fun `a millilitre line is saved per 100 ml`() = runTest {
        val rig = rig(listOf(juice))

        rig.vm.editItem(juice)
        rig.vm.onEditAmountChange("250")
        rig.vm.saveEdit(BigDecimal("250"), "250 ml")
        dispatcher.scheduler.runCurrent()

        val saved = rig.meal.items.single()
        assertEquals(NutritionBasis.PER_100_ML, saved.basis)
        assertEquals(BigDecimal("250"), saved.resolvedAmount)
        assertEquals("250 ml", saved.portionDescription)
        assertSameAmount("23.5", saved.exactCarbs)
    }

    @Test
    fun `a direct-carb line is saved with its new count and still no weight`() = runTest {
        val rig = rig(listOf(crispbread))

        rig.vm.editItem(crispbread)
        rig.vm.onEditAmountChange("3")
        rig.vm.saveEdit(BigDecimal("3"), "3 × 14.2 g carbs")
        dispatcher.scheduler.runCurrent()

        val saved = rig.meal.items.single()
        assertEquals(44L, saved.id)
        assertEquals(MealItemKind.DIRECT_CARBS, saved.kind)
        assertEquals(BigDecimal("3"), saved.count)
        assertEquals(BigDecimal("14.2"), saved.carbsPerUnit)
        assertSameAmount("42.6", saved.exactCarbs)
        assertEquals("3 × 14.2 g carbs", saved.portionDescription)
        assertNull(saved.resolvedAmount)
        assertNull(saved.basis)
    }

    // ---- what never writes -------------------------------------------------------------------

    @Test
    fun `cancelling leaves the line exactly as it was`() = runTest {
        val rig = rig(listOf(bread))

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        assertTrue("a savable correction was typed, then abandoned", rig.editor.canSave)
        rig.vm.closeEditor()
        dispatcher.scheduler.runCurrent()

        assertNull(rig.vm.state.value.editor)
        assertEquals(listOf(bread), rig.meal.items)
        assertEquals(0, rig.meal.writes)
    }

    @Test
    fun `blank, zero, negative and malformed amounts cannot be saved`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)

        for (text in listOf("", "   ", "0", "0.0", "0,00", "-5", "1.2.3", "abc", ".", "5g")) {
            rig.vm.onEditAmountChange(text)
            assertFalse("'$text' must not be savable", rig.editor.canSave)
            assertNull("'$text' has no result", rig.editor.exactCarbs)
        }

        // The ViewModel refuses on its own, whatever a caller passes.
        rig.vm.saveEdit(BigDecimal.ZERO, "0 g")
        rig.vm.saveEdit(BigDecimal("-5"), "-5 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(0, rig.meal.writes)
        assertEquals(listOf(bread), rig.meal.items)
    }

    @Test
    fun `only typed text that is not a usable amount is flagged, never an empty field`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)

        rig.vm.onEditAmountChange("")
        assertFalse("an empty field is unfinished, not wrong", rig.editor.invalid)

        rig.vm.onEditAmountChange("0")
        assertTrue(rig.editor.invalid)

        rig.vm.onEditAmountChange("1.2.3")
        assertTrue(rig.editor.invalid)

        rig.vm.onEditAmountChange("85")
        assertFalse(rig.editor.invalid)
    }

    @Test
    fun `an unchanged amount is not written`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)

        rig.vm.onEditAmountChange("70")
        assertFalse(rig.editor.canSave)
        rig.vm.onEditAmountChange("70.0")
        assertFalse(rig.editor.canSave)

        rig.vm.saveEdit(BigDecimal("70"), "70 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(0, rig.meal.writes)
        assertEquals(MealEditStatus.EDITING, rig.editor.status)
    }

    @Test
    fun `correcting a line records no use of the product`() = runTest {
        val rig = rig(listOf(bread), products = listOf(breadProductToday))

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        assertEquals("the line was corrected", BigDecimal("85"), rig.meal.items.single().resolvedAmount)
        assertEquals("no Usual shortcut is recorded", emptyList<PortionUsage>(), rig.usage.rows)
        assertSame("the product row is not touched", breadProductToday, rig.local.products["111"])
    }

    // ---- failure and repeated taps ------------------------------------------------------------

    @Test
    fun `a failed write keeps the editor open with what was typed, and a retry can succeed`() = runTest {
        val rig = rig(listOf(bread))
        rig.meal.failUpdates = true

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(MealEditStatus.FAILED, rig.editor.status)
        assertEquals("85", rig.editor.amountText)
        assertTrue("the user can try again", rig.editor.canSave)
        assertEquals(listOf(bread), rig.meal.items)

        rig.meal.failUpdates = false
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(MealEditStatus.SAVED, rig.editor.status)
        assertSameAmount("40.97", rig.meal.items.single().exactCarbs)
    }

    @Test
    fun `typing after a failure clears it`() = runTest {
        val rig = rig(listOf(bread))
        rig.meal.failUpdates = true
        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        rig.vm.onEditAmountChange("86")

        assertEquals(MealEditStatus.EDITING, rig.editor.status)
    }

    @Test
    fun `a second save while the first is still writing writes once`() = runTest {
        val rig = rig(listOf(bread))
        val gate = CompletableDeferred<Unit>()
        rig.meal.gate = gate

        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()
        assertEquals(MealEditStatus.SAVING, rig.editor.status)
        assertFalse(rig.editor.canSave)

        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, rig.meal.updates)
    }

    // ---- the other meal actions, alongside editing ---------------------------------------------

    @Test
    fun `a line added by Quick Add can be corrected like any other`() = runTest {
        val rig = rig(emptyList(), products = listOf(breadProductToday.copy(carbsPer100 = BigDecimal("48.2"))))
        val home = HomeViewModel(rig.repository)
        val product = rig.local.products.getValue("111")

        home.quickAdd(RecentEntry(product, lastUnit = null), "70 g")
        dispatcher.scheduler.runCurrent()
        val quickAdded = rig.vm.state.value.items.single()
        val usageAfterQuickAdd = rig.usage.rows.toList()

        rig.vm.editItem(quickAdded)
        assertEquals("70", rig.editor.amountText)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        val saved = rig.meal.items.single()
        assertEquals(quickAdded.id, saved.id)
        assertEquals(quickAdded.addedAt, saved.addedAt)
        assertEquals("85 g", saved.portionDescription)
        assertSameAmount("40.97", saved.exactCarbs)
        assertEquals("the correction adds no usage of its own", usageAfterQuickAdd, rig.usage.rows)
    }

    @Test
    fun `removing and undoing still work`() = runTest {
        val rig = rig(listOf(bread, juice))

        rig.vm.removeItem(bread)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(juice), rig.vm.state.value.items)
        assertEquals(bread, rig.vm.state.value.lastRemoved)

        rig.vm.undoRemove()
        dispatcher.scheduler.runCurrent()
        val restored = rig.vm.state.value.items.first()
        assertEquals("Wholegrain Bread", restored.displayName)
        assertEquals(addedAt, restored.addedAt)
        assertSameAmount("33.74", restored.exactCarbs)
        assertNull(rig.vm.state.value.lastRemoved)
    }

    @Test
    fun `a restored line can be corrected`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.removeItem(bread)
        dispatcher.scheduler.runCurrent()
        rig.vm.undoRemove()
        dispatcher.scheduler.runCurrent()
        val restored = rig.vm.state.value.items.single()

        rig.vm.editItem(restored)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        assertEquals(restored.id, rig.meal.items.single().id)
        assertSameAmount("40.97", rig.meal.items.single().exactCarbs)
    }

    // ---- the quick-adjust rail (1.0.8) --------------------------------------------------------

    @Test
    fun `the rail adjusts a weighed line and the carbohydrate follows`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Step(BigDecimal(10)))
        assertEquals("80", rig.editor.amountText)
        // 80 g of 48.2 g/100 g. The rail feeds the ordinary calculator, so the figure the sheet
        // shows is the same one typing 80 would have produced.
        assertSameAmount("38.56", checkNotNull(rig.editor.exactCarbs))

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Halve)
        assertEquals("40", rig.editor.amountText)
        assertSameAmount("19.28", checkNotNull(rig.editor.exactCarbs))

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Double)
        assertEquals("80", rig.editor.amountText)
    }

    @Test
    fun `the rail adjusts a counted line by whole and half units`() = runTest {
        val rig = rig(listOf(crispbread))
        rig.vm.editItem(crispbread)

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Step(BigDecimal.ONE.negate()))
        assertEquals("3", rig.editor.amountText)
        assertSameAmount("42.6", checkNotNull(rig.editor.exactCarbs))

        // Half a slice is a real thing to eat and the direct-carb calculator has always been able
        // to express it — nothing rounds the count to a whole unit.
        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Halve)
        assertEquals("1.5", rig.editor.amountText)
        assertSameAmount("21.3", checkNotNull(rig.editor.exactCarbs))
    }

    @Test
    fun `the rail and the product calculator apply the same operations`() = runTest {
        // Requirement: Product and the meal editor must not disagree about what halving does. They
        // cannot, because both call PortionAdjustment — asserted here by applying the operation
        // directly and comparing with what the editor ended up holding.
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)
        val start = MealItemCorrection.amountOf(bread)

        listOf(
            PortionAdjustment.Operation.Halve,
            PortionAdjustment.Operation.Double,
            PortionAdjustment.Operation.Step(BigDecimal(10)),
        ).forEach { operation ->
            rig.vm.editItem(bread)
            rig.vm.adjustEditAmount(operation)
            assertEquals(
                "the editor must apply $operation exactly as the domain does",
                ResultFormatter.editable(PortionAdjustment.apply(start, operation)),
                rig.editor.amountText,
            )
        }
    }

    @Test
    fun `the rail cannot drive a line below zero and zero cannot be saved`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("5")

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Step(BigDecimal(-10)))

        assertEquals("0", rig.editor.amountText)
        // Zero is reachable — it is an ordinary state on the way to another number — but it is not
        // a correction, so Save stays closed and no write can happen.
        assertFalse(rig.editor.canSave)
        assertEquals(0, rig.meal.updates)
    }

    @Test
    fun `typing still works after the rail has been used`() = runTest {
        val rig = rig(listOf(bread))
        rig.vm.editItem(bread)
        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Double)
        assertEquals("140", rig.editor.amountText)

        // The rail writes through the same handler a keystroke does, so the field is not left in
        // some adjusted mode that swallows the next edit.
        rig.vm.onEditAmountChange("85")
        assertEquals("85", rig.editor.amountText)
        assertTrue(rig.editor.canSave)
    }

    @Test
    fun `the rail is ignored once a write has started`() = runTest {
        // Same guard typing has: the amount must not drift away from the one being written.
        val rig = rig(listOf(bread))
        rig.meal.gate = CompletableDeferred()
        rig.vm.editItem(bread)
        rig.vm.onEditAmountChange("85")
        rig.vm.saveEdit(BigDecimal("85"), "85 g")
        dispatcher.scheduler.runCurrent()

        rig.vm.adjustEditAmount(PortionAdjustment.Operation.Double)
        assertEquals("85", rig.editor.amountText)

        rig.meal.gate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertSameAmount("40.97", rig.meal.items.single().exactCarbs)
    }
}
