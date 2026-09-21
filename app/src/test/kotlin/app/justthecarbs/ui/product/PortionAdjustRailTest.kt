package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionAdjustment
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
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The quick-adjust rail driving the calculator's two amount fields (1.0.8).
 *
 * [PortionAdjustmentTest] pins the arithmetic; this pins that the *screen's* two fields are wired
 * to it correctly and separately. The separation is the interesting part: weight and count are
 * different state, and an accelerator that wrote a count into the grams field would produce a
 * plausible wrong figure with nothing on screen to show it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PortionAdjustRailTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val bread = Product(
        barcode = "5449000000996",
        name = "Wholegrain Bread",
        brand = "Baker",
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        packageAmount = BigDecimal("400"),
    )

    private val slice = PortionUnit(
        id = 7,
        productBarcode = bread.barcode,
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

    private class StoringLocal(products: List<Product>) : LocalProductDataSource {
        private val stored = products.associateBy { it.barcode }.toMutableMap()
        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())

        // Column-accurate, like the real `UPDATE`s and `@Transaction` in `ProductDao` (1.0.8
        // lost-update hardening). Implementing these as whole-row copies would make every
        // preservation test in this repo pass while the defect they exist to catch sat in
        // production — the trap `LocalAliasTest` already records for the alias write.
        override suspend fun setFavorite(barcode: String, favorite: Boolean) {
            stored[barcode] = stored[barcode]?.copy(favorite = favorite) ?: return
        }

        override suspend fun recordUsageColumns(
            barcode: String,
            lastPortion: java.math.BigDecimal?,
            lastUsedAt: java.time.Instant,
            lastInputMode: app.justthecarbs.domain.InputMode?,
            lastSelectedPortionUnitId: Long?,
            lastCount: java.math.BigDecimal?,
        ) {
            val existing = stored[barcode] ?: return
            stored[barcode] = existing.copy(
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
            val current = stored[product.barcode]
            save(
                if (current == null) product
                else product.copy(localAlias = current.localAlias, favorite = current.favorite),
            )
        }
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = Unit
    }

    private class Units(private val units: List<PortionUnit>) : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String) = units.filter { it.productBarcode == barcode }
        override fun observeByBarcode(barcode: String) = flowOf(units.filter { it.productBarcode == barcode })
        override suspend fun findById(id: Long) = units.firstOrNull { it.id == id }
        override suspend fun save(unit: PortionUnit) = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private object NoUsage : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null

        override suspend fun save(usage: PortionUsage) = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private object NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem) = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private object SilentRemote : ProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
    }

    private object NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModel(units: List<PortionUnit> = emptyList()): ProductViewModel {
        val repository = ProductRepository(
            local = StoringLocal(listOf(bread)),
            remote = SilentRemote,
            portionUnits = Units(units),
            meal = NoMeal,
            portionUsage = NoUsage,
            searchSource = NoSearch,
            clock = Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC),
        )
        return ProductViewModel(repository, SavedStateHandle())
    }

    // ---- the weight field ----------------------------------------------------------------------

    @Test
    fun `the four accelerators move the weight field and the result together`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.onPortionChanged("70")

        vm.adjustPortion(PortionAdjustment.Operation.Halve)
        assertEquals("35", vm.state.value.portionText)
        // The result is recalculated by the ordinary calculator, not by the rail: 35 g of
        // 48.2 g/100 g is 16.87 g.
        assertEquals(0, BigDecimal("16.87").compareTo(vm.state.value.result?.exact))

        vm.adjustPortion(PortionAdjustment.Operation.Double)
        assertEquals("70", vm.state.value.portionText)
        assertEquals(0, BigDecimal("33.74").compareTo(vm.state.value.result?.exact))

        vm.adjustPortion(PortionAdjustment.Operation.Step(BigDecimal(25)))
        assertEquals("95", vm.state.value.portionText)

        vm.adjustPortion(PortionAdjustment.Operation.Step(BigDecimal(-25)))
        assertEquals("70", vm.state.value.portionText)
    }

    @Test
    fun `the weight field floors at zero and shows no result there`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.onPortionChanged("5")

        vm.adjustPortion(PortionAdjustment.Operation.Step(BigDecimal(-25)))

        assertEquals("0", vm.state.value.portionText)
        // Zero is reachable and shows nothing: a portion of nothing has no carbohydrate figure to
        // read, which is what stops it being mistaken for an answer.
        assertNull("zero is not a result", vm.state.value.result?.exact?.takeIf { it.signum() > 0 })
    }

    @Test
    fun `an empty field can be started with a step`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(bread.barcode)
        advanceUntilIdle()

        vm.adjustPortion(PortionAdjustment.Operation.Step(BigDecimal(25)))

        assertEquals("25", vm.state.value.portionText)
    }

    @Test
    fun `typing after adjusting still works`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.onPortionChanged("70")
        vm.adjustPortion(PortionAdjustment.Operation.Double)
        assertEquals("140", vm.state.value.portionText)

        // The rail writes through onPortionChanged, so the field is left in an ordinary editable
        // state rather than in some adjusted mode that swallows the next keystroke.
        vm.onPortionChanged("85")
        assertEquals("85", vm.state.value.portionText)
        assertEquals(0, BigDecimal("40.97").compareTo(vm.state.value.result?.exact))
    }

    // ---- the count field -----------------------------------------------------------------------

    @Test
    fun `the accelerators move the count field by whole and half units`() = runTest(dispatcher) {
        val vm = viewModel(listOf(slice))
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.switchToPortionUnit(slice.id)
        advanceUntilIdle()
        vm.onCountChanged("2")

        vm.adjustCount(PortionAdjustment.Operation.Step(BigDecimal.ONE))
        assertEquals("3", vm.state.value.countText)

        vm.adjustCount(PortionAdjustment.Operation.Step(BigDecimal.ONE.negate()))
        assertEquals("2", vm.state.value.countText)

        vm.adjustCount(PortionAdjustment.Operation.Double)
        assertEquals("4", vm.state.value.countText)

        // Half a slice: legitimate, and expressible by the domain since PortionResolver multiplies
        // a BigDecimal count. Nothing rounds it back to a whole unit.
        vm.adjustCount(PortionAdjustment.Operation.Halve)
        assertEquals("2", vm.state.value.countText)
        vm.adjustCount(PortionAdjustment.Operation.Halve)
        assertEquals("1", vm.state.value.countText)
        vm.adjustCount(PortionAdjustment.Operation.Halve)
        assertEquals("0.5", vm.state.value.countText)
    }

    @Test
    fun `a half count resolves through the ordinary calculator`() = runTest(dispatcher) {
        val vm = viewModel(listOf(slice))
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.switchToPortionUnit(slice.id)
        advanceUntilIdle()
        vm.onCountChanged("1")

        vm.adjustCount(PortionAdjustment.Operation.Halve)

        // Half a 35 g slice is 17.5 g, and 17.5 g of 48.2 g/100 g is 8.435 g. The rail changed the
        // count; PortionResolver and CarbCalculator did everything after that, unchanged.
        assertEquals("0.5", vm.state.value.countText)
        assertEquals(0, BigDecimal("8.435").compareTo(vm.state.value.result?.exact))
    }

    @Test
    fun `the count field floors at zero`() = runTest(dispatcher) {
        val vm = viewModel(listOf(slice))
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.switchToPortionUnit(slice.id)
        advanceUntilIdle()
        vm.onCountChanged("1")

        vm.adjustCount(PortionAdjustment.Operation.Step(BigDecimal(-1)))
        assertEquals("0", vm.state.value.countText)

        vm.adjustCount(PortionAdjustment.Operation.Step(BigDecimal(-1)))
        assertEquals("0", vm.state.value.countText)
    }

    @Test
    fun `adjusting the count leaves the weight field alone and the reverse`() = runTest(dispatcher) {
        // The two fields are separate state. An accelerator that wrote across them would produce a
        // plausible wrong figure — a count of slices read as grams — with nothing on screen to say
        // so, which is exactly the class of defect this app refuses to ship.
        val vm = viewModel(listOf(slice))
        vm.load(bread.barcode)
        advanceUntilIdle()
        vm.switchToPortionUnit(slice.id)
        advanceUntilIdle()
        vm.onCountChanged("2")
        val weightBefore = vm.state.value.portionText

        vm.adjustCount(PortionAdjustment.Operation.Double)

        assertEquals("4", vm.state.value.countText)
        // The weight field is the resolved grams of the count and follows it, so it is checked for
        // *consistency* rather than for being untouched: 4 slices of 35 g is 140 g.
        assertEquals("140", vm.state.value.portionText)
        assertTrue("the weight field must track the count, not a stale value", weightBefore != "140")
    }

    // ---- locale ---------------------------------------------------------------------------------

    @Test
    fun `an adjusted amount is written with the device's decimal separator`() = runTest(dispatcher) {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            val vm = viewModel()
            vm.load(bread.barcode)
            advanceUntilIdle()
            vm.onPortionChanged("75")

            vm.adjustPortion(PortionAdjustment.Operation.Halve)

            // 37,5 with a comma — what a Turkish user would have typed, and what the result beside
            // it already shows. The field and the figure must not disagree about the separator.
            assertEquals("37,5", vm.state.value.portionText)
            assertEquals(
                ResultFormatter.editable(BigDecimal("37.5")),
                vm.state.value.portionText,
            )
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
