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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * *Rename on this device*, driven through the calculator's own ViewModel (1.0.8).
 *
 * `LocalAliasTest` proves what the repository stores. This proves what the screen does with it: that
 * the title changes at once rather than after a round trip, that the editor opens on what is already
 * in force, and that the action reaches the narrow one-column write rather than the whole-row save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RenameProductTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC)
    private val barcode = "8712100849060"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun product(alias: String? = null) = Product(
        barcode = barcode,
        name = "AH Volkoren Tarwebrood 800g",
        carbsPer100 = BigDecimal("41.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        localAlias = alias,
        // Recently synced, so `refreshFromRemote` skips the network and the fixture is about the
        // rename rather than about a refresh.
        remoteUpdatedAt = Instant.parse("2026-09-20T09:59:50Z"),
    )

    /** Column-accurate, like `LocalAliasTest`'s: `setLocalAlias` writes one field and reads none. */
    private class RecordingLocal(seed: Product) : LocalProductDataSource {
        val stored = mutableMapOf(seed.barcode to seed)
        val aliasWrites = mutableListOf<Pair<String, String?>>()
        val fullSaves = mutableListOf<Product>()

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            fullSaves += product
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())

        override suspend fun setLocalAlias(barcode: String, alias: String?) {
            aliasWrites += barcode to alias
            stored[barcode] = stored[barcode]?.copy(localAlias = alias) ?: return
        }

        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = Unit
    }

    private class NoRemote : ProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit) = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem) = item
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
        override suspend fun save(usage: PortionUsage) = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private class Rig(val viewModel: ProductViewModel, val local: RecordingLocal)

    private fun rig(alias: String? = null): Rig {
        val local = RecordingLocal(product(alias))
        return Rig(
            ProductViewModel(
                repository = ProductRepository(
                    local = local,
                    remote = NoRemote(),
                    portionUnits = NoUnits(),
                    meal = NoMeal(),
                    portionUsage = NoUsage(),
                    searchSource = NoSearch(),
                    clock = clock,
                ),
                savedState = SavedStateHandle(),
            ),
            local,
        )
    }

    // ---- the editor ------------------------------------------------------------------------------

    @Test
    fun `the editor opens and closes`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        assertFalse(rig.viewModel.state.value.showRenameForm)
        rig.viewModel.showRenameForm(true)
        assertTrue(rig.viewModel.state.value.showRenameForm)
        rig.viewModel.showRenameForm(false)
        assertFalse(rig.viewModel.state.value.showRenameForm)
    }

    @Test
    fun `the editor is seeded from the alias already in force`() = runTest(dispatcher) {
        val rig = rig(alias = "Breakfast bread")
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        // The dialog reads `product.localAlias` directly, so "pre-filled" is a property of the state
        // the screen is given rather than a second copy of the alias the editor has to keep in step.
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.localAlias)
    }

    // ---- saving ----------------------------------------------------------------------------------

    @Test
    fun `the displayed name changes immediately, before the write lands`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        rig.viewModel.setLocalAlias("Breakfast bread")

        // Read *before* the dispatcher runs the launched write. The title behind the dialog is the
        // confirmation this feature ships instead of a Snackbar, so it has to be there at once —
        // making the user watch a round trip would turn a personalisation into a transaction.
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.displayName)
        assertTrue(rig.local.aliasWrites.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(barcode to "Breakfast bread"), rig.local.aliasWrites)
    }

    @Test
    fun `saving closes the editor`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        rig.viewModel.showRenameForm(true)
        rig.viewModel.setLocalAlias("Breakfast bread")

        assertFalse(rig.viewModel.state.value.showRenameForm)
    }

    @Test
    fun `saving writes only the alias`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.fullSaves.clear()

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // The narrow write. A rename must not travel through the whole-row save path, which would
        // re-write every column from a snapshot this screen may have been holding for minutes.
        assertEquals(1, rig.local.aliasWrites.size)
        assertTrue(rig.local.fullSaves.isEmpty())
    }

    @Test
    fun `whitespace is trimmed before the name is shown or stored`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        rig.viewModel.setLocalAlias("  Breakfast bread  ")
        advanceUntilIdle()

        // Trimmed in the state too, not only on the way to storage — otherwise the title would show
        // the padded string until the screen was next loaded from disk.
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.localAlias)
        assertEquals(listOf(barcode to "Breakfast bread"), rig.local.aliasWrites)
    }

    @Test
    fun `a blank name does not become an alias`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        rig.viewModel.setLocalAlias("   ")
        advanceUntilIdle()

        assertNull(rig.viewModel.state.value.product?.localAlias)
        assertEquals("AH Volkoren Tarwebrood 800g", rig.viewModel.state.value.product?.displayName)
    }

    // ---- removing --------------------------------------------------------------------------------

    @Test
    fun `removing a custom name restores the canonical one`() = runTest(dispatcher) {
        val rig = rig(alias = "Breakfast bread")
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.displayName)

        rig.viewModel.setLocalAlias(null)
        advanceUntilIdle()

        assertNull(rig.viewModel.state.value.product?.localAlias)
        assertEquals("AH Volkoren Tarwebrood 800g", rig.viewModel.state.value.product?.displayName)
        assertEquals(listOf(barcode to null), rig.local.aliasWrites)
        assertNull(rig.local.stored.getValue(barcode).localAlias)
    }

    // ---- what renaming must not do ----------------------------------------------------------------

    @Test
    fun `renaming leaves the rest of the product alone`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        val before = rig.viewModel.state.value.product!!

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()
        val after = rig.viewModel.state.value.product!!

        // Whole-object comparison with the alias put back, so a field added to Product later is
        // covered here without anyone remembering to add an assertion.
        assertEquals(before, after.copy(localAlias = null))
        assertEquals("AH Volkoren Tarwebrood 800g", after.name)
        assertEquals(VerificationStatus.UNVERIFIED, after.verificationStatus)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, after.dataSource)
    }

    @Test
    fun `renaming does not disturb the calculation on screen`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.viewModel.onPortionChanged("65")
        advanceUntilIdle()
        val before = rig.viewModel.state.value.result

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // The number the user came for does not move because they named the thing it belongs to.
        assertEquals("65", rig.viewModel.state.value.portionText)
        assertEquals(before?.exact, rig.viewModel.state.value.result?.exact)
    }

    @Test
    fun `a quick calculation cannot be renamed`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.startQuickCalculation(BigDecimal("48.2"), NutritionBasis.PER_100_G)
        advanceUntilIdle()

        rig.viewModel.showRenameForm(true)
        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // There is no stored row to attach a name to. Guarded in the ViewModel as well as by the
        // menu being hidden, so the rule does not depend on a composable staying the way it is.
        assertFalse(rig.viewModel.state.value.showRenameForm)
        assertTrue(rig.local.aliasWrites.isEmpty())
    }
}
