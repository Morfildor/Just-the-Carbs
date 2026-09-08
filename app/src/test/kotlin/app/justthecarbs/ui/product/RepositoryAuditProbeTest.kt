package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.Instant

/** Regressions reproduced during the September repository review. */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryAuditProbeTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private val product = Product("8712100849060", "Example", BigDecimal("40"),
        NutritionBasis.PER_100_G, ProductDataOrigin.OPEN_FOOD_FACTS,
        remoteUpdatedAt = Instant.now())

    private class Local(var product: Product) : LocalProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.Found(product)
        override suspend fun save(product: Product) { this.product = product }
        override fun observeRecents(limit: Int) = flowOf(listOf(product))
    }
    private class Units : PortionUnitStore {
        val units = mutableListOf<PortionUnit>()
        override suspend fun findByBarcode(barcode: String) = units.toList()
        override fun observeByBarcode(barcode: String) = flowOf(units.toList())
        override suspend fun findById(id: Long) = units.find { it.id == id }
        override suspend fun save(unit: PortionUnit): PortionUnit {
            val saved = unit.copy(id = unit.id.takeIf { it != 0L } ?: (units.size + 1L))
            units.removeAll { it.id == saved.id }; units.add(saved); return saved
        }
        override suspend fun delete(unit: PortionUnit) { units.remove(unit) }
    }
    private class Meal : MealStore {
        val items = mutableListOf<MealItem>()
        override fun observeItems() = flowOf(emptyList<MealItem>())
        override suspend fun findItems() = items.toList()
        override suspend fun add(item: MealItem): MealItem { items.add(item); return item }
        override suspend fun update(item: MealItem) {}
        override suspend fun remove(item: MealItem) { items.remove(item) }
        override suspend fun clear() { items.clear() }
    }
    private class Usage : PortionUsageStore {
        var fail = false
        override suspend fun findByBarcode(barcode: String) = emptyList<PortionUsage>()
        override suspend fun findVariant(barcode: String, inputMode: InputMode,
            portionUnitId: Long?, amount: BigDecimal): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage {
            if (fail) throw java.io.IOException("usage write failed")
            return usage
        }
        override suspend fun delete(usage: PortionUsage) {}
    }
    private val local = Local(product)
    private val units = Units()
    private val meal = Meal()
    private val usage = Usage()
    private val repository = ProductRepository(local,
        object : ProductDataSource {
            override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
        }, units, meal, usage,
        object : ProductSearchSource {
            override suspend fun search(terms: String) = ProductSearchResult.NoMatches
        })
    private fun vm() = ProductViewModel(repository, SavedStateHandle())

    @Test fun directPortionSurvivesProductVerification() = runTest(dispatcher) {
        val unit = repository.saveUserPortionUnit(product.barcode, PortionUnitKind.SLICE,
            PortionConversion.DirectCarbs(BigDecimal("14")))
        val vm = vm(); vm.load(product.barcode); advanceUntilIdle()
        vm.onPortionChanged("50"); advanceUntilIdle()
        vm.switchToPortionUnit(unit.id); vm.onCountChanged("2")
        assertEquals(0, BigDecimal("28").compareTo(vm.state.value.exactCarbs))
        vm.confirmVerification(BigDecimal("50"), NutritionBasis.PER_100_G, "Example")
        advanceUntilIdle()
        assertEquals("2 slices remain 28 g carbs after per-100 verification", 0,
            BigDecimal("28").compareTo(vm.state.value.exactCarbs))
    }

    @Test fun weightPortionCannotBeUsedAfterBasisChanges() = runTest(dispatcher) {
        val unit = repository.saveUserPortionUnit(product.barcode, PortionUnitKind.SLICE,
            PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))
        val vm = vm(); vm.load(product.barcode); advanceUntilIdle()
        vm.switchToPortionUnit(unit.id); vm.onCountChanged("2"); advanceUntilIdle()
        vm.confirmVerification(BigDecimal("10"), NutritionBasis.PER_100_ML, "Example")
        advanceUntilIdle()
        assertNull("70 g cannot be calculated against a per-100 ml value without density",
            vm.state.value.exactCarbs)
    }

    @Test fun successfulMealInsertMustNotBeReportedAsFailedWhenHistoryFails() = runTest(dispatcher) {
        val vm = vm(); vm.load(product.barcode); advanceUntilIdle()
        vm.onPortionChanged("50"); advanceUntilIdle()
        usage.fail = true
        vm.addCurrentToMeal("50 g", scanNext = true); advanceUntilIdle()
        assertEquals(1, meal.items.size)
        assertEquals(ProductNavigationEvent.ScanNext, vm.navigationEvents.first())
        assertTrue(vm.state.value.usageSaveFailed)
        assertFalse("meal is already committed; retrying would insert it twice", vm.state.value.mealAddFailed)
    }

    @Test fun resetOnlineRestoresTheOriginalBasisTogetherWithTheValue() = runTest(dispatcher) {
        repository.saveVerification(product.barcode, BigDecimal("10"), NutritionBasis.PER_100_ML)
        repository.resetToOnlineValue(product.barcode)
        assertEquals(NutritionBasis.PER_100_G, local.product.basis)
    }
    @Test fun legacyRemoteValuesWithoutBasesCannotBeAppliedOrReset() = runTest(dispatcher) {
        local.product = product.copy(originalRemoteCarbs = BigDecimal("60"), latestRemoteCarbs = BigDecimal("70"))
        assertFalse(local.product.canResetToOnlineValue)
        assertFalse(local.product.remoteValueDiffers)
        repository.resetToOnlineValue(product.barcode)
        repository.applyLatestRemoteValue(product.barcode)
        assertEquals(BigDecimal("40"), local.product.carbsPer100)
    }

    @Test fun applyRemoteValueRestoresItsBasisAndClearsOldQuantities() = runTest(dispatcher) {
        local.product = product.copy(latestRemoteCarbs = BigDecimal("10"),
            latestRemoteBasis = NutritionBasis.PER_100_ML, lastPortion = BigDecimal("50"),
            packageAmount = BigDecimal("500"))
        repository.applyLatestRemoteValue(product.barcode)
        assertEquals(NutritionBasis.PER_100_ML, local.product.basis)
        assertNull(local.product.lastPortion)
        assertNull(local.product.packageAmount)
        repository.resetToOnlineValue(product.barcode)
        assertEquals(NutritionBasis.PER_100_G, local.product.basis)
        assertEquals(BigDecimal("40"), local.product.carbsPer100)
    }

    @Test fun pendingUsageFromTheOldBasisIsDiscarded() = runTest(dispatcher) {
        repository.saveVerification(product.barcode, BigDecimal("10"), NutritionBasis.PER_100_ML)
        repository.recordUse(product.barcode, BigDecimal("50"), expectedBasis = NutritionBasis.PER_100_G)
        assertNull(local.product.lastPortion)
    }

}
