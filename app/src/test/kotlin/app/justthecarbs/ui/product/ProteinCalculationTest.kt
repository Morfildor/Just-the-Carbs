package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbCalculator
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The protein figure is computed from the same portion as the carbs, by the same formula, in the
 * same state update, and is null on every path that resolves no gram portion (design spec
 * 2026-09-24, sections 5 and 9).
 */
class ProteinCalculationTest {

    private val dispatcher = StandardTestDispatcher()
    private val barcode = "8000500310427"
    private val now = Instant.parse("2026-09-25T10:00:00Z")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun nutella(protein: String? = "6.3") = Product(
        barcode = barcode,
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        // Recently synced, so the background refresh after the load does not fetch.
        remoteUpdatedAt = now,
        proteinPer100 = protein?.let(::BigDecimal),
        proteinOrigin = protein?.let { ProductDataOrigin.OPEN_FOOD_FACTS },
    )

    private val directCarbSlice = PortionUnit(
        id = 7,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = now,
        updatedAt = now,
    )

    private val weightSlice = directCarbSlice.copy(
        id = 8,
        conversion = PortionConversion.WeightBased(BigDecimal("15"), NutritionBasis.PER_100_G),
    )

    private class Local(private val product: Product) : LocalProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.Found(product)
        override suspend fun save(product: Product) = Unit
        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = Unit
    }

    private object Offline : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
    }

    private class Units(private val units: List<PortionUnit>) : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String) = units
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(units)
        override suspend fun findById(id: Long) = units.firstOrNull { it.id == id }
        override suspend fun save(unit: PortionUnit) = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private object NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem) = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
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

    private object NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModel(product: Product, units: List<PortionUnit> = emptyList()) = ProductViewModel(
        ProductRepository(
            local = Local(product),
            remote = Offline,
            portionUnits = Units(units),
            meal = NoMeal,
            portionUsage = NoUsage,
            searchSource = NoSearch,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        ),
        SavedStateHandle(),
    )

    @Test
    fun `protein is the same formula applied to the same portion`() = runTest(dispatcher) {
        val vm = viewModel(nutella())
        vm.load(barcode)
        advanceUntilIdle()

        vm.onPortionChanged("65")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(0, BigDecimal("37.375").compareTo(state.result!!.exact))
        assertEquals(0, BigDecimal("4.095").compareTo(state.exactProtein))
        assertEquals(
            CarbCalculator.calculate(BigDecimal("6.3"), BigDecimal("65"), NutritionBasis.PER_100_G).exact,
            state.exactProtein,
        )
    }

    @Test
    fun `the protein follows every portion change`() = runTest(dispatcher) {
        val vm = viewModel(nutella())
        vm.load(barcode)
        advanceUntilIdle()

        vm.onPortionChanged("65")
        vm.onPortionChanged("100")
        advanceUntilIdle()

        assertEquals(0, BigDecimal("6.3").compareTo(vm.state.value.exactProtein))
    }

    @Test
    fun `clearing the portion clears both figures`() = runTest(dispatcher) {
        val vm = viewModel(nutella())
        vm.load(barcode)
        advanceUntilIdle()
        vm.onPortionChanged("65")
        advanceUntilIdle()

        vm.onPortionChanged("")
        advanceUntilIdle()

        assertNull(vm.state.value.result)
        assertNull(vm.state.value.exactProtein)
    }

    @Test
    fun `a record without protein gives carbs and no protein figure`() = runTest(dispatcher) {
        val vm = viewModel(nutella(protein = null))
        vm.load(barcode)
        advanceUntilIdle()

        vm.onPortionChanged("65")
        advanceUntilIdle()

        assertNotNull(vm.state.value.result)
        assertNull(vm.state.value.exactProtein)
    }

    @Test
    fun `a counted portion with a weight scales the protein too`() = runTest(dispatcher) {
        val vm = viewModel(nutella(), listOf(weightSlice))
        vm.load(barcode)
        advanceUntilIdle()

        vm.switchToPortionUnit(weightSlice.id)
        vm.onCountChanged("2")
        advanceUntilIdle()

        // 2 x 15 g = 30 g; 30 x 6.3 / 100 = 1.89.
        assertEquals(0, BigDecimal("1.89").compareTo(vm.state.value.exactProtein))
    }

    @Test
    fun `a direct-carb portion has no protein figure, because it has no weight`() = runTest(dispatcher) {
        val vm = viewModel(nutella(), listOf(directCarbSlice))
        vm.load(barcode)
        advanceUntilIdle()
        vm.onPortionChanged("65")
        advanceUntilIdle()

        vm.switchToPortionUnit(directCarbSlice.id)
        vm.onCountChanged("2")
        advanceUntilIdle()

        assertNotNull(vm.state.value.directCarbResult)
        assertNull(vm.state.value.exactProtein)
    }

    @Test
    fun `a quick calculation carries no protein`() = runTest(dispatcher) {
        val vm = viewModel(nutella())
        vm.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        advanceUntilIdle()

        vm.onPortionChanged("65")
        advanceUntilIdle()

        assertNull(vm.state.value.product!!.proteinPer100)
        assertNull(vm.state.value.exactProtein)
    }
}
