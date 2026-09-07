package app.justthecarbs.ui.manual

import app.justthecarbs.data.ProductRepository
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
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.CancellationException
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
    fun `a failed product write says so instead of failing silently`() = runTest(dispatcher) {
        // Re-enabling the button is necessary but not sufficient. With only that, the tap looks like
        // it missed: the screen is unchanged, nothing says the product was not saved, and the user's
        // only available reading is that the button did not register. The failure must be stated.
        val journal = Journal()
        val failing = object : FakeLocal(journal) {
            override suspend fun save(product: Product) = throw IllegalStateException("disk full")
        }
        val viewModel = ManualEntryViewModel(repositoryOf(failing, FakeUnits(journal)))
        viewModel.start(barcode)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertTrue("a failed save must be reported to the user", viewModel.state.value.saveFailed)
        assertNull(viewModel.state.value.savedBarcode)
    }

    @Test
    fun `editing after a failed save clears the failure notice`() = runTest(dispatcher) {
        // The notice describes one attempt, not a permanent property of the screen. Leaving it up
        // while the user edits would make a subsequent successful save look like it also failed.
        val journal = Journal()
        val failing = object : FakeLocal(journal) {
            override suspend fun save(product: Product) = throw IllegalStateException("disk full")
        }
        val viewModel = ManualEntryViewModel(repositoryOf(failing, FakeUnits(journal)))
        viewModel.start(barcode)
        viewModel.fillIn()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saveFailed)

        viewModel.onNameChanged("Volkorenbrood")

        assertFalse(viewModel.state.value.saveFailed)
    }

    @Test
    fun `a retried save that succeeds clears the earlier failure`() = runTest(dispatcher) {
        // The failure must not survive the write that fixes it, or a successful save reads as a
        // failed one.
        val journal = Journal()
        var failNext = true
        val flaky = object : FakeLocal(journal) {
            override suspend fun save(product: Product) {
                if (failNext) throw IllegalStateException("disk full")
                super.save(product)
            }
        }
        val viewModel = ManualEntryViewModel(repositoryOf(flaky, FakeUnits(journal)))
        viewModel.start(barcode)
        viewModel.fillIn()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saveFailed)

        failNext = false
        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.saveFailed)
        assertEquals(barcode, viewModel.state.value.savedBarcode)
    }

    @Test
    fun `a product write failure over an existing local row is classified as a product failure`() =
        runTest(dispatcher) {
            // The case the previous "ask the store afterwards" heuristic got wrong. It inferred which
            // write failed from whether a product row existed once the exception had been caught —
            // but a row already present for this barcode makes that question answer "yes" for a
            // reason that has nothing to do with this save. The product write failed, so the *stale*
            // row was read as proof the product had landed, and the user was told the portion alone
            // failed and sent on to a calculator showing the old record.
            val journal = Journal()
            val local = object : FakeLocal(journal) {
                override suspend fun save(product: Product) = throw IllegalStateException("disk full")
            }
            // The barcode is already known locally, from an earlier scan or entry.
            local.stored[barcode] = Product(
                barcode = barcode,
                name = "Stale record",
                carbsPer100 = BigDecimal("1"),
                basis = NutritionBasis.PER_100_G,
                dataSource = ProductDataOrigin.MANUAL,
            )
            val units = FakeUnits(journal)
            val viewModel = ManualEntryViewModel(repositoryOf(local, units))
            viewModel.start(barcode, pendingPortionUnit = slicePortion)
            viewModel.fillIn()

            viewModel.save()
            advanceUntilIdle()

            assertTrue("the product write is what failed", viewModel.state.value.saveFailed)
            assertFalse(
                "the portion was never attempted, so it did not fail",
                viewModel.state.value.portionSaveFailed,
            )
            assertNull("nothing was saved, so nothing to navigate to", viewModel.state.value.savedBarcode)
            assertTrue("no orphaned portion may exist", units.stored.isEmpty())
        }

    @Test
    fun `a portion write failure after a successful product write is classified as a portion failure`() =
        runTest(dispatcher) {
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

            assertTrue("the portion is what failed", viewModel.state.value.portionSaveFailed)
            assertFalse("the product saved fine", viewModel.state.value.saveFailed)
            // The product genuinely exists, so the user still reaches the calculator and can add the
            // portion by hand. This is the pre-existing behaviour and must not change.
            assertEquals(barcode, viewModel.state.value.savedBarcode)
        }

    @Test
    fun `both writes succeeding still navigates`() = runTest(dispatcher) {
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, pendingPortionUnit = slicePortion)
        viewModel.fillIn()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(barcode, viewModel.state.value.savedBarcode)
        assertFalse(viewModel.state.value.saveFailed)
        assertFalse(viewModel.state.value.portionSaveFailed)
        assertFalse(viewModel.state.value.saving)
        assertEquals(listOf("product:$barcode", "portion:$barcode"), journal.entries)
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

    // --- §5, startup-hardening pass: the basis must never silently default to grams when a
    // scanned figure carries no established denominator. ---

    @Test
    fun `an unresolved OCR basis blocks save until a chip is chosen`() = runTest(dispatcher) {
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        // ocrCarbs is non-blank (a figure genuinely arrived) but ocrBasis fails to parse — exactly
        // what CandidateChoice's unknown-basis branch and the QUICK route's malformed-basis fallback
        // both produce.
        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "")

        assertNull("basis must stay unresolved, never default to grams", viewModel.state.value.basis)
        viewModel.onNameChanged("Hagelslag")

        assertFalse("Save must stay disabled with no basis chosen", viewModel.state.value.canSave)
    }

    @Test
    fun `selecting a basis chip after an unresolved OCR reading enables save`() = runTest(dispatcher) {
        val journal = Journal()
        val local = FakeLocal(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, FakeUnits(journal)))
        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "")
        viewModel.onNameChanged("Hagelslag")
        assertFalse(viewModel.state.value.canSave)

        viewModel.onBasisChanged(NutritionBasis.PER_100_G)

        assertTrue("choosing a basis is what unblocks save", viewModel.state.value.canSave)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(NutritionBasis.PER_100_G, local.stored[barcode]?.basis)
    }

    @Test
    fun `a known OCR basis preselects the matching chip and allows save immediately`() =
        runTest(dispatcher) {
            val journal = Journal()
            val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

            viewModel.start(barcode, ocrCarbs = "48", ocrBasis = NutritionBasis.PER_100_ML.name)

            assertEquals(NutritionBasis.PER_100_ML, viewModel.state.value.basis)
            viewModel.onNameChanged("Coconut milk")
            assertTrue("a resolved OCR basis needs no further choice", viewModel.state.value.canSave)
        }

    @Test
    fun `ordinary Home entry with no OCR context still starts on grams`() = runTest(dispatcher) {
        // The unchanged path: plain manual entry (nothing carried in) must keep defaulting to
        // grams, exactly as it always has — only the OCR-unresolved case must refuse to default.
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        viewModel.start(barcode)

        assertEquals(NutritionBasis.PER_100_G, viewModel.state.value.basis)
    }

    @Test
    fun `a blank saved-state basis with no carried carbs still defaults to grams`() = runTest(dispatcher) {
        // editManuallyRoute can carry a blank basis too (an unresolved reading's *Edit* path), but
        // with no carbs figure alongside it there is nothing an OCR value could be silently
        // mislabeled as — this is the ordinary "start typing from scratch" case, not the hazard §5
        // closes.
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        viewModel.start(barcode, ocrCarbs = "", ocrBasis = "")

        assertEquals(NutritionBasis.PER_100_G, viewModel.state.value.basis)
    }

    @Test
    fun `an invalid saved-state basis with carried carbs never becomes grams`() = runTest(dispatcher) {
        // A malformed or unrecognised basis string must be treated identically to a blank one —
        // never parsed loosely, never defaulted.
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "not-a-real-basis")

        assertNull(viewModel.state.value.basis)
    }

    @Test
    fun `an unresolved basis is preserved across a simulated process recreation`() = runTest(dispatcher) {
        // start() is idempotent once name or carbs are non-empty (it is the recreation guard), so
        // calling it again with the same arguments — as LaunchedEffect(barcode, carbs, basis, ...)
        // does after a configuration change — must not resurrect a default basis the first call
        // correctly refused to set.
        val journal = Journal()
        val viewModel = ManualEntryViewModel(repositoryOf(FakeLocal(journal), FakeUnits(journal)))

        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "")
        assertNull(viewModel.state.value.basis)

        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "")

        assertNull("re-entering start() must not resurrect a default", viewModel.state.value.basis)
    }

    @Test
    fun `save is a no-op when the basis is unresolved even if called directly`() = runTest(dispatcher) {
        // canSave is the UI's guard, but save() itself must not trust the button stayed disabled —
        // it is reachable directly from a test or from a future caller. Nothing may be written.
        val journal = Journal()
        val local = FakeLocal(journal)
        val units = FakeUnits(journal)
        val viewModel = ManualEntryViewModel(repositoryOf(local, units))
        viewModel.start(barcode, ocrCarbs = "48", ocrBasis = "")
        viewModel.onNameChanged("Hagelslag")

        viewModel.save()
        advanceUntilIdle()

        assertTrue("no product may be written with an unresolved basis", local.stored.isEmpty())
        assertTrue(units.stored.isEmpty())
        assertNull(viewModel.state.value.savedBarcode)
    }

    // ---- P1 §14: a cancelled save must propagate, never report as a failed save --------------

    @Test
    fun `a cancellation during the no-portion product write propagates instead of setting saveFailed`() =
        runTest(dispatcher) {
            // A plain runCatching around this write would also catch CancellationException — e.g.
            // the screen being navigated away from mid-save — and report it exactly like a genuine
            // disk error. It must instead propagate out of viewModelScope.launch, leaving saveFailed
            // untouched rather than flipped to true.
            val journal = Journal()
            val cancelling = object : FakeLocal(journal) {
                override suspend fun save(product: Product): Unit = throw CancellationException("torn down")
            }
            val viewModel = ManualEntryViewModel(repositoryOf(cancelling, FakeUnits(journal)))
            viewModel.start(barcode)
            viewModel.fillIn()

            viewModel.save()
            advanceUntilIdle()

            assertFalse(
                "a cancellation is not a save failure",
                viewModel.state.value.saveFailed,
            )
        }

    @Test
    fun `a cancellation during the product write of a pending-portion save propagates instead of setting saveFailed`() =
        runTest(dispatcher) {
            val journal = Journal()
            val cancelling = object : FakeLocal(journal) {
                override suspend fun save(product: Product): Unit = throw CancellationException("torn down")
            }
            val units = FakeUnits(journal)
            val viewModel = ManualEntryViewModel(repositoryOf(cancelling, units))
            viewModel.start(barcode, pendingPortionUnit = slicePortion)
            viewModel.fillIn()

            viewModel.save()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.saveFailed)
            assertTrue("the portion was never attempted", units.stored.isEmpty())
        }

    @Test
    fun `a cancellation during the portion write propagates instead of setting portionSaveFailed`() =
        runTest(dispatcher) {
            val journal = Journal()
            val local = FakeLocal(journal)
            val units = object : FakeUnits(journal) {
                override suspend fun save(unit: PortionUnit): PortionUnit =
                    throw CancellationException("torn down")
            }
            val viewModel = ManualEntryViewModel(repositoryOf(local, units))
            viewModel.start(barcode, pendingPortionUnit = slicePortion)
            viewModel.fillIn()

            viewModel.save()
            advanceUntilIdle()

            assertFalse(
                "a cancellation is not a portion save failure",
                viewModel.state.value.portionSaveFailed,
            )
            // The product write itself completed before the cancellation, so it is still on disk —
            // unlike a genuine portion-write failure, cancellation must not be misreported either way.
            assertEquals(1, journal.entries.count { it == "product:$barcode" })
        }
}
