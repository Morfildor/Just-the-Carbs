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
 * A scanned nutrition label must reach a carbohydrate total without creating a product (1.0.3 P1).
 *
 * The flow this pins is the whole point of the patch: scan a label, type a portion, read the number,
 * leave. Before it, the only route into the calculator was `product/{barcode}`, which begins with a
 * database lookup — so an OCR reading had to be routed through the *Enter product* form, which will
 * not proceed without a product name and writes a Room row before it navigates. Getting one figure
 * out of one photograph therefore cost a named, saved record the user never asked for.
 *
 * **The persistence assertions are the load-bearing ones.** Nothing on screen distinguishes a quick
 * calculation from a saved product's calculator, so a regression that quietly started writing would
 * be invisible to a human driving the app and invisible to a test that only checked the number. Both
 * stores here record every write and the tests assert they stayed empty, which is why
 * [RecordingLocal.saved] and [RecordingUsage.saved] exist as lists rather than as maps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickCalculationTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-29T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records every product write, so "no product was created" is a measurement, not an assumption. */
    private class RecordingLocal : LocalProductDataSource {
        val saved = mutableListOf<Product>()
        private val stored = mutableMapOf<String, Product>()

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            saved += product
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
    }

    /**
     * Records every usage write.
     *
     * Recents are `products.lastUsedAt`, which `recordUse` stamps — so a quick calculation leaking
     * into Recents would show up here as a write, even though no *product* row was created by it.
     * The two are separate failures and are asserted separately.
     */
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

    /**
     * Fails the test if it is ever asked for anything: a quick calculation must not hit the network.
     *
     * Only usable on the quick-calculation fixture. A *barcode* load legitimately reaches the network
     * twice — once to fetch and once for the background refresh — so the control test at the bottom
     * of this file uses [SilentRemote] instead. Making that one throw would assert the opposite of
     * what the app is supposed to do.
     */
    private class ForbiddenRemote : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult =
            throw AssertionError("a quick calculation must not perform a remote lookup (barcode=$barcode)")
    }

    /** Answers nothing, quietly — for the barcode control, where a refresh attempt is correct. */
    private class SilentRemote : ProductDataSource {
        override suspend fun fetch(barcode: String) = ProductFetchResult.NotFound
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class RecordingMeal : MealStore {
        val added = mutableListOf<MealItem>()
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem {
            added += item
            return item
        }

        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private class Fixture {
        val local = RecordingLocal()
        val usage = RecordingUsage()
        val meal = RecordingMeal()

        val repository = ProductRepository(
            local = local,
            remote = ForbiddenRemote(),
            portionUnits = NoUnits(),
            meal = meal,
            portionUsage = usage,
            searchSource = NoSearch(),
            clock = Clock.fixed(Instant.parse("2026-08-29T10:00:00Z"), ZoneOffset.UTC),
        )

        val viewModel = ProductViewModel(repository, SavedStateHandle())
    }

    // ---- 1. a reading reaches a calculator with no name and no product -------------------------

    @Test
    fun `an OCR reading starts a calculation with no product name`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertFalse("the calculator must not sit in a loading state", state.loading)
        assertNull("a quick calculation is not a failure state", state.failure)
        assertTrue("the calculation must be marked unsaved", state.unsaved)
        assertTrue("no name was supplied and none may be invented", state.product?.name.isNullOrEmpty())
        assertEquals("", state.product?.barcode)
        assertTrue("the calculator must be usable", state.canCalculate)
    }

    // ---- 2 & 3. no persistence as a side effect ------------------------------------------------

    /**
     * The debounced usage recorder is the specific hazard here.
     *
     * `ProductViewModel.init` collects `portionText` and calls `rememberUsage()` after it settles, so
     * a quick calculation would write Recents purely by the user typing a portion — no deliberate
     * action at all. `advanceUntilIdle` runs past that debounce, so this test genuinely exercises it
     * rather than finishing before it fires.
     */
    @Test
    fun `typing a portion into a quick calculation persists nothing`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        advanceUntilIdle()

        assertEquals("a quick calculation created a product row", emptyList<Product>(), fixture.local.saved)
        assertEquals("a quick calculation created a usage record", emptyList<PortionUsage>(), fixture.usage.saved)
    }

    /** The explicit exit path — leaving the screen must not be what triggers a write either. */
    @Test
    fun `leaving a quick calculation persists nothing`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        fixture.viewModel.rememberUsage()
        advanceUntilIdle()

        assertEquals(emptyList<Product>(), fixture.local.saved)
        assertEquals(emptyList<PortionUsage>(), fixture.usage.saved)
    }

    // ---- 4, 5, 6. the basis reaches the calculator and the arithmetic is the production one -----

    @Test
    fun `the detected basis reaches the calculator and produces the expected total`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertEquals(NutritionBasis.PER_100_G, state.product?.basis)
        // 48 x 35 / 100 = 16.8, exactly — the same movePointLeft(2) path a barcode product uses.
        assertEquals(0, BigDecimal("16.8").compareTo(state.exactCarbs))
        assertEquals("the whole-gram figure is derived from exact, never re-rounded", 17, state.result?.wholeGrams)
    }

    /**
     * A millilitre basis must survive, and must not be silently turned into grams.
     *
     * This is the one place a lost basis would be invisible: the arithmetic is identical either way,
     * so only the unit the portion field asks for would be wrong — which is exactly the failure
     * `PackageBasisResolver` exists to prevent on the Open Food Facts path.
     */
    @Test
    fun `a millilitre reading keeps its basis`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("9.4"), NutritionBasis.PER_100_ML)
        fixture.viewModel.onPortionChanged("250")
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertEquals(NutritionBasis.PER_100_ML, state.product?.basis)
        assertEquals(0, BigDecimal("23.500").compareTo(state.exactCarbs))
    }

    /** Rounding is `ResultFormatter`'s HALF_UP, and a quick calculation must not acquire its own. */
    @Test
    fun `existing rounding behaviour is preserved`() = runTest(dispatcher) {
        val fixture = Fixture()

        // 31.0 x 50 / 100 = 15.5 exactly — the value that exposed DecimalFormat's HALF_EVEN default.
        fixture.viewModel.startQuickCalculation(BigDecimal("31.0"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("50")
        advanceUntilIdle()

        assertEquals(16, fixture.viewModel.state.value.result?.wholeGrams)
    }

    // ---- 7 & 8. the user's correction is what gets used -----------------------------------------

    /**
     * A corrected figure must replace the detected one outright.
     *
     * Correction is not a new concept here: *Correct* on the scanner card routes to manual entry
     * pre-filled, and that path already existed. What this pins is that starting a quick calculation
     * from the corrected value calculates from that value and not from anything remembered about the
     * reading it came from.
     */
    @Test
    fun `a user-corrected value is what the calculator uses`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.startQuickCalculation(BigDecimal("62"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("50")
        advanceUntilIdle()

        assertEquals(0, BigDecimal("62").compareTo(fixture.viewModel.state.value.product?.carbsPer100))
        assertEquals(0, BigDecimal("31.0").compareTo(fixture.viewModel.state.value.exactCarbs))
    }

    @Test
    fun `a user-corrected basis is what the calculator uses`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("9.4"), NutritionBasis.PER_100_G)
        fixture.viewModel.startQuickCalculation(BigDecimal("9.4"), NutritionBasis.PER_100_ML)
        advanceUntilIdle()

        assertEquals(NutritionBasis.PER_100_ML, fixture.viewModel.state.value.product?.basis)
    }

    // ---- provenance -----------------------------------------------------------------------------

    /**
     * A camera reading is OCR-provenanced, and it is not verified.
     *
     * The two facts are separate and must stay separate (owner correction, 2026-08-13). The user
     * confirmed a number the *parser* proposed — they did not transcribe the package themselves — so
     * `USER_VERIFIED` would be a claim nobody made. Recording it as `MANUAL` would be a second,
     * quieter version of the same lie: it would say a human typed it.
     */
    @Test
    fun `a quick calculation from OCR keeps OCR provenance and is not marked verified`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        advanceUntilIdle()

        val product = fixture.viewModel.state.value.product
        assertEquals(ProductDataOrigin.OCR, product?.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, product?.verificationStatus)
    }

    // ---- 9, 10, 11. optional saving --------------------------------------------------------------

    /**
     * Saving is a deliberate act, and the name is required only at that point.
     *
     * The calculation is complete and on screen before this is ever called — which is the entire
     * behavioural claim of the patch, and the reason the name lives here rather than upstream.
     */
    @Test
    fun `saving a quick calculation writes exactly one product carrying the calculated figures`() =
        runTest(dispatcher) {
            val fixture = Fixture()

            fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
            fixture.viewModel.onPortionChanged("35")
            advanceUntilIdle()
            assertEquals("nothing may be written before the save", emptyList<Product>(), fixture.local.saved)

            fixture.viewModel.saveQuickCalculation("Hagelslag")
            advanceUntilIdle()

            // Counted as distinct keys, not as writes. Saving legitimately touches the row twice —
            // once to create it and once for `recordUse` to stamp `lastUsedAt`, which is what puts
            // it in Recents at all — and a write count would forbid the second.
            assertEquals("exactly one product row", 1, fixture.local.saved.map { it.barcode }.distinct().size)
            val saved = fixture.local.saved.last()
            assertEquals("Hagelslag", saved.name)
            assertEquals(0, BigDecimal("48").compareTo(saved.carbsPer100))
            assertEquals(NutritionBasis.PER_100_G, saved.basis)
            assertTrue("a saved product needs a stable key for Recents", saved.barcode.isNotEmpty())
            assertEquals(
                "the saved product must reach Recents with its portion remembered",
                0,
                BigDecimal("35").compareTo(saved.lastPortion),
            )
        }

    /**
     * The calculation survives the save, and the screen stops being "unsaved".
     *
     * Losing the number at the moment of saving would be the worst possible time to lose it: the user
     * saves *because* the result was worth keeping.
     */
    @Test
    fun `saving keeps the calculation on screen`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        advanceUntilIdle()
        fixture.viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertEquals(0, BigDecimal("16.8").compareTo(state.exactCarbs))
        assertEquals("35", state.portionText)
        assertFalse("the product now exists, so it is no longer unsaved", state.unsaved)
        assertEquals("Hagelslag", state.product?.name)
    }

    /** A blank name is refused, and refusing must not write a half-made record. */
    @Test
    fun `saving without a name is refused and writes nothing`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        fixture.viewModel.saveQuickCalculation("   ")
        advanceUntilIdle()

        assertEquals(emptyList<Product>(), fixture.local.saved)
        assertTrue("the calculation is untouched", fixture.viewModel.state.value.unsaved)
        assertEquals(0, BigDecimal("16.8").compareTo(fixture.viewModel.state.value.exactCarbs))
    }

    /**
     * Cancelling the save leaves the calculation exactly as it was.
     *
     * Asserted on the result rather than on a dialog flag, because "the calculation is intact" is the
     * promise; which control was open at the time is not.
     */
    @Test
    fun `cancelling the save leaves the calculation intact`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        fixture.viewModel.showSaveQuickCalculation(true)
        fixture.viewModel.showSaveQuickCalculation(false)
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertFalse(state.showSaveQuickCalculationForm)
        assertTrue(state.unsaved)
        assertEquals(0, BigDecimal("16.8").compareTo(state.exactCarbs))
        assertEquals(emptyList<Product>(), fixture.local.saved)
    }

    /** Saving twice must not produce two rows — the second tap has nothing left to save. */
    @Test
    fun `saving twice writes one product`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.saveQuickCalculation("Hagelslag")
        fixture.viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()

        // Distinct keys again: two saves would mint two synthetic barcodes, which is precisely the
        // duplicate this guards against, whereas one row written twice is ordinary.
        assertEquals(1, fixture.local.saved.map { it.barcode }.distinct().size)
    }

    // ---- the meal, which a quick calculation may still feed ---------------------------------------

    /**
     * An unsaved calculation can still be added to the meal, and does so with no product barcode.
     *
     * A meal item is an immutable snapshot designed to outlive its product — that is why
     * `current_meal_items` has no foreign key — so this is the one write a quick calculation is
     * *allowed* to make. It still creates no product row.
     */
    @Test
    fun `an unsaved calculation can join the meal without creating a product`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        advanceUntilIdle()
        fixture.viewModel.addCurrentToMeal("35 g")
        advanceUntilIdle()

        assertEquals(1, fixture.meal.added.size)
        assertNull("an unsaved item must not claim a product row", fixture.meal.added.single().productBarcode)
        assertEquals("still no product was created", emptyList<Product>(), fixture.local.saved)
        assertEquals("still no usage was recorded", emptyList<PortionUsage>(), fixture.usage.saved)
    }

    /**
     * A nameless calculation still names itself in the meal.
     *
     * `MealScreen` renders `displayName` straight into the line and into the *Remove …* label an
     * accessibility service reads out, so an empty one is a blank row in a list whose entire job is
     * telling the user which items are on the plate — and "Remove" with nothing after it. The
     * product has no name by design here, so the fallback has to come from the item's own creation.
     */
    @Test
    fun `an unsaved calculation joins the meal under a readable name`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.onPortionChanged("35")
        advanceUntilIdle()
        fixture.viewModel.addCurrentToMeal("35 g", "Quick calculation")
        advanceUntilIdle()

        assertEquals(
            "the screen's fallback wording is what names the line",
            "Quick calculation",
            fixture.meal.added.single().displayName,
        )
        assertTrue(
            "a meal line must never be blank",
            fixture.meal.added.single().displayName.isNotBlank(),
        )
    }

    // ---- a save that fails -------------------------------------------------------------------------

    /**
     * Writes nothing and fails on demand, so the failure path is measured rather than assumed.
     *
     * The success path says nothing about this one: every store here is an in-memory map that
     * cannot fail, so without a fake that does, the entire `onFailure` branch is unexecuted code
     * that happens to compile.
     */
    private class FailingLocal : LocalProductDataSource {
        val saved = mutableListOf<Product>()
        private val stored = mutableMapOf<String, Product>()
        var failNextSave = false

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            if (failNextSave) {
                failNextSave = false
                throw RuntimeException("the write failed")
            }
            saved += product
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
    }

    private fun failingFixture(local: FailingLocal, usage: RecordingUsage) = ProductViewModel(
        ProductRepository(
            local = local,
            remote = ForbiddenRemote(),
            portionUnits = NoUnits(),
            meal = RecordingMeal(),
            portionUsage = usage,
            searchSource = NoSearch(),
            clock = clock,
        ),
        SavedStateHandle(),
    )

    /**
     * A failed save closes the form, because that is the only way its message can be seen.
     *
     * The failure is reported on the *Save product* action, which sits on the screen **behind** the
     * dialog. Leaving the dialog open therefore put the sole account of what went wrong underneath
     * the scrim: the user tapped *Save*, the dialog stayed exactly as it was, and nothing anywhere
     * said the product had not been kept. Indistinguishable from a tap that missed.
     */
    @Test
    fun `a failed save closes the form so its message is visible`() = runTest(dispatcher) {
        val local = FailingLocal()
        val viewModel = failingFixture(local, RecordingUsage())

        viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        viewModel.showSaveQuickCalculation(true)
        local.failNextSave = true
        viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("the failure must be recorded", state.quickSaveFailed)
        assertFalse("the form must close, or its message is hidden behind it", state.showSaveQuickCalculationForm)
        assertTrue("nothing was written, so it is still unsaved", state.unsaved)
        assertEquals("a failed save writes no product", emptyList<Product>(), local.saved)
    }

    /** The failure is about the attempt just made, so reopening the form must not still show it. */
    @Test
    fun `reopening the form clears a previous failure`() = runTest(dispatcher) {
        val local = FailingLocal()
        val viewModel = failingFixture(local, RecordingUsage())

        viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        local.failNextSave = true
        viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()
        assertTrue("precondition: the first attempt failed", viewModel.state.value.quickSaveFailed)

        viewModel.showSaveQuickCalculation(true)

        assertFalse("a stale failure must not survive into the next attempt", viewModel.state.value.quickSaveFailed)
    }

    /** Retrying after a failure saves once, under one key — not a second synthetic barcode. */
    @Test
    fun `retrying after a failed save writes exactly one product`() = runTest(dispatcher) {
        val local = FailingLocal()
        val usage = RecordingUsage()
        val viewModel = failingFixture(local, usage)

        viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        viewModel.onPortionChanged("35")
        advanceUntilIdle()

        local.failNextSave = true
        viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()

        viewModel.saveQuickCalculation("Hagelslag")
        advanceUntilIdle()

        assertEquals("one product, one key", 1, local.saved.map { it.barcode }.distinct().size)
        assertFalse("the retry succeeded", viewModel.state.value.unsaved)
        assertFalse("and the error is gone", viewModel.state.value.quickSaveFailed)
    }

    /**
     * A blank name writes nothing.
     *
     * Currently unreachable from the dialog, whose confirm button is disabled while the field is
     * blank — but the ViewModel is the layer that owns the rule, and a guard that depends on a
     * button staying disabled is one refactor away from not existing.
     */
    @Test
    fun `a blank name is refused and writes nothing`() = runTest(dispatcher) {
        val fixture = Fixture()

        fixture.viewModel.startQuickCalculation(BigDecimal("48"), NutritionBasis.PER_100_G)
        fixture.viewModel.saveQuickCalculation("   ")
        advanceUntilIdle()

        assertTrue("the user is told why", fixture.viewModel.state.value.quickSaveNameError)
        assertEquals("nothing was written", emptyList<Product>(), fixture.local.saved)
        assertTrue("still a quick calculation", fixture.viewModel.state.value.unsaved)
    }

    // ---- 12 & 13. the existing flows are unchanged -------------------------------------------------

    /**
     * A barcode product is untouched by any of this: it persists usage exactly as before.
     *
     * The negative control for the persistence assertions above. Without it, deleting `recordUse`
     * outright would make every "persists nothing" test in this file pass.
     */
    @Test
    fun `a saved barcode product still records usage`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        val stored = Product(
            barcode = barcode,
            name = "Hagelslag",
            carbsPer100 = BigDecimal("48"),
            basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
        )
        val local = RecordingLocal()
        local.save(stored)
        val usage = RecordingUsage()
        val repository = ProductRepository(
            local = local,
            remote = SilentRemote(),
            portionUnits = NoUnits(),
            meal = RecordingMeal(),
            portionUsage = usage,
            searchSource = NoSearch(),
            clock = clock,
        )
        val viewModel = ProductViewModel(repository, SavedStateHandle())

        viewModel.load(barcode)
        advanceUntilIdle()
        viewModel.onPortionChanged("35")
        advanceUntilIdle()

        assertFalse("a looked-up product is not a quick calculation", viewModel.state.value.unsaved)
        assertNotNull(viewModel.state.value.product)
        assertTrue("a saved product must still record its usage", usage.saved.isNotEmpty())
    }
}
