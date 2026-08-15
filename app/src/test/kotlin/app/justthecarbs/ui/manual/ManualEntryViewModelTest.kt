package app.justthecarbs.ui.manual

import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Manual entry carrying an OCR-captured countable portion (correction pass §2).
 *
 * The workflow these pin is the one the whole pass exists to make work: a barcode that Open Food
 * Facts does not know, a nutrition label photographed instead, a "2 slices" portion accepted from
 * it — and then a product that has to be created before that portion can legally be stored, because
 * `portion_units.productBarcode` is a foreign key to `products.barcode`.
 *
 * The invariant under test is ordering plus honesty: the product is written first, the portion
 * second, and a portion that fails to persist is never reported as captured.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC)
    private val barcode = "8712100849060"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Records the order writes happen in, which is the property this suite is really about. */
    private class Journal {
        val entries = mutableListOf<String>()
    }

    private open class FakeLocal(private val journal: Journal) : LocalProductDataSource {
        val stored = mutableMapOf<String, Product>()

        override suspend fun fetch(barcode: String): ProductFetchResult =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            journal.entries += "product:${product.barcode}"
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
    }

    private open class FakeUnits(private val journal: Journal) : PortionUnitStore {
        val stored = mutableMapOf<Long, PortionUnit>()
        private var nextId = 1L

        override suspend fun findByBarcode(barcode: String): List<PortionUnit> =
            stored.values.filter { it.productBarcode == barcode }

        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> =
            flowOf(stored.values.filter { it.productBarcode == barcode })

        override suspend fun findById(id: Long): PortionUnit? = stored[id]

        override suspend fun save(unit: PortionUnit): PortionUnit {
            journal.entries += "portion:${unit.productBarcode}"
            val saved = if (unit.id == 0L) unit.copy(id = nextId++) else unit
            stored[saved.id] = saved
            return saved
        }

        override suspend fun delete(unit: PortionUnit) {
            stored.remove(unit.id)
        }
    }

    private class NoRemote : ProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private class NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private class NoUsage : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null

        override suspend fun save(usage: PortionUsage): PortionUsage = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private fun repositoryOf(local: LocalProductDataSource, units: PortionUnitStore) =
        ProductRepository(
            local = local,
            remote = NoRemote(),
            portionUnits = units,
            meal = NoMeal(),
            portionUsage = NoUsage(),
            searchSource = NoSearch(),
            clock = clock,
        )

    private fun ManualEntryViewModel.fillIn() {
        onNameChanged("Volkoren brood")
        onCarbsChanged("41.2")
    }

    private val slicePortion = PendingPortionUnit(
        kind = PortionUnitKind.SLICE,
        conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
    )

    @Test
    fun `a pending portion is carried into the entry state`() = runTest(dispatcher) {
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        viewModel.start(barcode, pendingPortionUnit = slicePortion)

        assertEquals(slicePortion, viewModel.state.value.pendingPortionUnit)
    }

    @Test
    fun `tapping save twice before it lands writes the portion only once`() = runTest(dispatcher) {
        // Release-freeze audit §8. The write is asynchronous and the screen only navigates away once
        // it completes, so both taps land while the button is still enabled. A portion unit is
        // inserted with an autogenerated id, so without an in-flight guard the second tap creates a
        // second identical "1 slice" row that the user never asked for and cannot tell apart.
        val journal = Journal()
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("product:$barcode", "portion:$barcode"), journal.entries)
        assertEquals(1, units.stored.size)
    }

    @Test
    fun `the save button is re-enabled when the product write fails`() = runTest(dispatcher) {
        // A failed write must not strand the screen: previously the exception escaped viewModelScope
        // entirely. Now it is caught, and `saving` is cleared so the user can retry.
        val journal = Journal()
        val failing = object : FakeLocal(journal) {
            override suspend fun save(product: Product) = throw IllegalStateException("disk full")
        }
        val viewModel = ManualEntryViewModel(repositoryOf(failing, FakeUnits(journal)))
        viewModel.start(barcode)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertNull(viewModel.state.value.savedBarcode)
        assertTrue("save must be available again after a failure", viewModel.state.value.canSave)
    }

    @Test
    fun `the product is persisted before its portion`() = runTest(dispatcher) {
        // The ordering the foreign key requires. Asserted on the write journal rather than on the
        // end state, because both rows existing afterwards would not prove which was written first.
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("product:$barcode", "portion:$barcode"), journal.entries)
    }

    @Test
    fun `a successful save persists both the product and the portion`() = runTest(dispatcher) {
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(local.stored[barcode])
        assertEquals(1, units.stored.size)
        val saved = units.stored.values.single()
        assertEquals(PortionUnitKind.SLICE, saved.kind)
        assertEquals(
            0,
            BigDecimal("14.2").compareTo(
                (saved.conversion as PortionConversion.DirectCarbs).carbsPerUnit,
            ),
        )
        assertEquals(barcode, viewModel.state.value.savedBarcode)
        assertFalse(viewModel.state.value.portionSaveFailed)
    }

    @Test
    fun `an OCR portion keeps OCR provenance and verified status`() = runTest(dispatcher) {
        val journal = Journal()
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        val saved = units.stored.values.single()
        assertEquals(ProductDataOrigin.OCR, saved.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, saved.verificationStatus)
    }

    @Test
    fun `a failed portion write is reported rather than shown as captured`() = runTest(dispatcher) {
        // The false-success case. The product genuinely saved, so the user still reaches the
        // calculator — but the portion did not, and the state says so instead of staying silent.
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = object : FakeUnits(journal) {
            override suspend fun save(unit: PortionUnit): PortionUnit =
                throw IllegalStateException("constraint failed")
        }
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertTrue("the portion failure must be visible", viewModel.state.value.portionSaveFailed)
        assertTrue("no portion may be left behind", units.stored.isEmpty())
        assertEquals(barcode, viewModel.state.value.savedBarcode)
    }

    @Test
    fun `a failed product write attempts no portion at all`() = runTest(dispatcher) {
        val journal = Journal()
        val local = object : FakeLocal(journal) {
            override suspend fun save(product: Product) = throw IllegalStateException("disk full")
        }
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertTrue("no orphaned portion may exist", units.stored.isEmpty())
        assertTrue(
            "the portion must never be attempted without its product",
            journal.entries.none { it.startsWith("portion:") },
        )
    }

    @Test
    fun `entry with no pending portion saves only the product`() = runTest(dispatcher) {
        // The unchanged path: ordinary manual entry must not grow a portion it was never given.
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(local.stored[barcode])
        assertTrue(units.stored.isEmpty())
        assertFalse(viewModel.state.value.portionSaveFailed)
    }

    @Test
    fun `an invalid entry saves neither the product nor the portion`() = runTest(dispatcher) {
        // Validation still gates everything. A blank name must not become a product row that a
        // portion then attaches itself to.
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.onCarbsChanged("41.2")

        viewModel.save()
        advanceUntilIdle()

        assertTrue(local.stored.isEmpty())
        assertTrue(units.stored.isEmpty())
        assertNull(viewModel.state.value.savedBarcode)
    }

    @Test
    fun `a weight-based pending portion is stored as weight-based`() = runTest(dispatcher) {
        // A printed weight is preferred wherever the label gave one, and it must not be flattened
        // into a carbohydrate figure on the way through.
        val journal = Journal()
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), units))
        viewModel.start(
            barcode,
            pendingPortionUnit = PendingPortionUnit(
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.WeightBased(
                    BigDecimal("35"),
                    app.justthecarbs.domain.NutritionBasis.PER_100_G,
                ),
            ),
        )
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        val conversion = units.stored.values.single().conversion
        assertTrue("a printed weight must stay weight-based", conversion is PortionConversion.WeightBased)
        assertEquals(
            0,
            BigDecimal("35").compareTo((conversion as PortionConversion.WeightBased).amountPerUnit),
        )
    }
}
