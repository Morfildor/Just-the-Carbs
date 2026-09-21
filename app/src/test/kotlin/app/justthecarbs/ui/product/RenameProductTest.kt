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

        // Preserves the device-owned columns, as the real `@Transaction saveProductFacts` does.
        // A fake that simply forwarded to `save` would make the lost-update tests pass while the
        // defect sat in production — the trap `LocalAliasTest` already records for the alias write.
        override suspend fun saveProductFacts(product: Product) {
            val current = stored[product.barcode]
            save(
                if (current == null) {
                    product
                } else {
                    product.copy(localAlias = current.localAlias, favorite = current.favorite)
                },
            )
        }

        val stored = mutableMapOf(seed.barcode to seed)
        val aliasWrites = mutableListOf<Pair<String, String?>>()
        val fullSaves = mutableListOf<Product>()

        // Column-accurate, like the real `UPDATE`s in `ProductDao` (1.0.8 lost-update hardening).
        // Implementing these as whole-row copies would make every preservation test in this repo
        // pass while the defect they exist to catch sat in production.
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
                lastSelectedPortionUnitId = if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) {
                    null
                } else {
                    lastSelectedPortionUnitId ?: existing.lastSelectedPortionUnitId
                },
                lastCount = if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) {
                    null
                } else {
                    lastCount ?: existing.lastCount
                },
            )
        }

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            fullSaves += product
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())

        /**
         * Fails the next [failAliasWrites] alias writes, then behaves normally.
         *
         * A counter rather than a boolean so a test can fail the *first* of two renames and let the
         * second succeed — which is the only way to reach the stale-failure rule, where a late
         * failure must not roll back a newer success.
         */
        var failAliasWrites = 0

        /** Completed by the test to release a parked alias write, for ordering two renames. */
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun setLocalAlias(barcode: String, alias: String?) {
            gate?.await()
            aliasWrites += barcode to alias
            if (failAliasWrites > 0) {
                failAliasWrites--
                throw java.io.IOException("the write failed")
            }
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

    // ---- when the write fails (1.0.8 P2) ----------------------------------------------------------

    @Test
    fun `a successful save leaves no failure message`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // The optimistic path stays quiet. A confirmation for something the user can already see on
        // the title would be noise.
        assertFalse(rig.viewModel.state.value.renameFailed)
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.localAlias)
    }

    @Test
    fun `a failed save rolls the name back and says so`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.failAliasWrites = 1

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        val state = rig.viewModel.state.value
        // Showing a name storage does not hold would be the app stating something untrue,
        // indefinitely, with nothing on screen to correct it.
        assertNull("the name must go back to what is stored", state.product?.localAlias)
        assertTrue("and the failure must be reported", state.renameFailed)
        assertEquals("AH Volkoren Tarwebrood 800g", state.product?.displayName)
    }

    @Test
    fun `a failed removal restores the alias it was removing`() = runTest(dispatcher) {
        val rig = rig(alias = "Breakfast bread")
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.failAliasWrites = 1

        rig.viewModel.setLocalAlias(null)
        advanceUntilIdle()

        val state = rig.viewModel.state.value
        // Removal has identical failure semantics: the screen must not claim the custom name is
        // gone while the store still has it.
        assertEquals("Breakfast bread", state.product?.localAlias)
        assertTrue(state.renameFailed)
    }

    @Test
    fun `a late failure never rolls back a newer successful rename`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()

        // Rename A is parked mid-write and will fail. Rename B then runs to completion.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        rig.local.gate = gate
        rig.local.failAliasWrites = 1
        rig.viewModel.setLocalAlias("First name")

        rig.local.gate = null
        rig.viewModel.setLocalAlias("Second name")
        advanceUntilIdle()

        // A's failure lands last, and must be ignored: the user has already replaced that attempt,
        // and rolling back would revert a name they can see to one two renames old.
        gate.complete(Unit)
        advanceUntilIdle()

        val state = rig.viewModel.state.value
        assertEquals("the newer rename owns the name", "Second name", state.product?.localAlias)
        assertFalse("and a superseded failure says nothing", state.renameFailed)
    }

    @Test
    fun `a new attempt clears the previous failure`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.failAliasWrites = 1
        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()
        assertTrue(rig.viewModel.state.value.renameFailed)

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // A message about a failure that has since been superseded would describe something no
        // longer true.
        assertFalse(rig.viewModel.state.value.renameFailed)
        assertEquals("Breakfast bread", rig.viewModel.state.value.product?.localAlias)
    }

    @Test
    fun `the editor is still usable after a failure`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.failAliasWrites = 1
        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // Reopening is the retry surface, and it clears the stale message on the way in.
        rig.viewModel.showRenameForm(true)

        val state = rig.viewModel.state.value
        assertTrue("the editor must reopen", state.showRenameForm)
        assertFalse("without carrying the old message into a fresh attempt", state.renameFailed)
    }

    @Test
    fun `the failure can be dismissed`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.local.failAliasWrites = 1
        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        rig.viewModel.dismissRenameFailure()

        assertFalse(rig.viewModel.state.value.renameFailed)
    }

    @Test
    fun `a failure never blocks the calculation`() = runTest(dispatcher) {
        val rig = rig()
        rig.viewModel.load(barcode)
        advanceUntilIdle()
        rig.viewModel.onPortionChanged("65")
        advanceUntilIdle()
        val before = rig.viewModel.state.value.result
        rig.local.failAliasWrites = 1

        rig.viewModel.setLocalAlias("Breakfast bread")
        advanceUntilIdle()

        // A name is presentation. Failing to store one must not disturb the number the user is here
        // for, which is the same rule a successful rename already obeys.
        assertEquals(before?.exact, rig.viewModel.state.value.result?.exact)
        assertEquals("65", rig.viewModel.state.value.portionText)
    }
}
