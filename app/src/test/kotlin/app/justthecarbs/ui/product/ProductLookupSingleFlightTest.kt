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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * One accepted barcode must cost at most one remote lookup, and a superseded one must not answer.
 *
 * Open Food Facts allows **15 reads per minute per IP**, which makes a duplicate request more than
 * waste: it spends a budget shared by every user behind the same address, and the app's whole
 * offline story depends on staying inside it. The pre-existing guard in [ProductViewModel.load]
 * tested `product != null`, which is exactly the field a lookup that has *started but not finished*
 * has not written yet — so two calls close together both saw null and both went to the network.
 *
 * The second test is the more serious one. Both branches of `load` write state unconditionally, so
 * without cancellation the **last** lookup to complete wins regardless of which barcode the user
 * actually asked for. A slow first scan finishing after a fast second one would put the previous
 * product on screen under the new scan's barcode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductLookupSingleFlightTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun product(barcode: String, name: String) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal("46.0"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * Counts fetches, with a **per-barcode** delay.
     *
     * Per-barcode rather than one shared delay, because a shared one makes the overwrite test
     * vacuous: two lookups started microseconds apart and delayed equally complete in the order they
     * started, so the second one's product wins on its own and the test would pass with no
     * cancellation whatever. Making the abandoned lookup the *slower* of the two is what forces it to
     * land after the current one and actually attempt the overwrite.
     */
    private class CountingRemote(
        private val delaysMs: Map<String, Long>,
        private val products: Map<String, Product>,
    ) : ProductDataSource {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(barcode: String): ProductFetchResult {
            fetched += barcode
            delay(delaysMs[barcode] ?: 100L)
            return products[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound
        }
    }

    /**
     * A local cache that actually **persists**, because production's does.
     *
     * The previous fake's `save` was a no-op, and that alone hid a real duplicate fetch: on a cache
     * miss `ProductRepository.lookup` saves the fetched product, and `refreshFromRemote` then reads
     * the cache to decide whether there is anything to refresh. With a discarding `save` the refresh
     * found nothing and returned before reaching the network, so the second request never appeared
     * in the fetch list this file asserts on. A fake that cannot store is not a cache, and a
     * single-flight test built on one measures half the path.
     */
    private class RecordingLocal(seed: List<Product> = emptyList()) : LocalProductDataSource {
        val stored = seed.associateBy { it.barcode }.toMutableMap()

        override suspend fun fetch(barcode: String) =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
    }

    /**
     * Portion units that take time to load, which is what holds the window this file's third test
     * covers open long enough to act in.
     *
     * That test needs the load to be *between* the fetch returning and the product reaching the
     * screen. Delivery does the portion-unit read first, so delaying this store parks the load
     * exactly there. **A zero-delay store closes the window inside a single dispatch and the test
     * would pass vacuously** — which is why the delay is the fixture rather than an incidental
     * detail.
     */
    private class SlowUnits(private val delayMs: Long) : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> {
            delay(delayMs)
            return emptyList()
        }

        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
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

    private class NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun viewModelWith(
        remote: ProductDataSource,
        portionUnits: PortionUnitStore = NoUnits(),
        local: LocalProductDataSource = RecordingLocal(),
    ) = ProductViewModel(
        repository = ProductRepository(
            local = local,
            remote = remote,
            portionUnits = portionUnits,
            meal = NoMeal(),
            portionUsage = NoUsage(),
            searchSource = NoSearch(),
            clock = clock,
        ),
        savedState = SavedStateHandle(),
    )

    @Test
    fun `two loads of the same barcode while one is in flight cost one remote fetch`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        val remote = CountingRemote(
            delaysMs = mapOf(barcode to 500L),
            products = mapOf(barcode to product(barcode, "Hagelslag")),
        )
        val viewModel = viewModelWith(remote)

        viewModel.load(barcode)
        // Deliberately before the first has completed — the state still holds a null product, which
        // is precisely the situation the old guard could not detect.
        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals("the same barcode was fetched twice", listOf(barcode), remote.fetched)
        assertEquals("Hagelslag", viewModel.state.value.product?.name)
    }

    /**
     * A superseded lookup must not deliver. Asserted on the *product on screen*, not on the fetch
     * count: cancelling after the request has left is still correct behaviour, and a fetch-count
     * assertion would forbid it while missing the failure that actually matters.
     */
    @Test
    fun `a slow first lookup cannot overwrite the product of a later scan`() = runTest(dispatcher) {
        val first = "8712100849060"
        val second = "5000159484695"
        val remote = CountingRemote(
            // The abandoned lookup is the SLOW one, so without cancellation it lands last and its
            // unconditional state write wins. With a shared delay this test cannot fail.
            delaysMs = mapOf(first to 800L, second to 100L),
            products = mapOf(first to product(first, "Hagelslag"), second to product(second, "Snickers")),
        )
        val viewModel = viewModelWith(remote)

        viewModel.load(first)
        viewModel.load(second)
        advanceUntilIdle()

        assertEquals(
            "the abandoned scan's product was delivered over the current one",
            "Snickers",
            viewModel.state.value.product?.name,
        )
        assertEquals(second, viewModel.state.value.barcode)
    }

    /**
     * The gap the first test could not see: the fetch has **completed** but the product is not on
     * screen yet.
     *
     * `onProductLoaded` used to launch its own coroutine instead of running inside `lookupJob`, and
     * that coroutine does the portion-unit read, the recalculation and the background refresh
     * before it ever writes `product`. For that whole stretch `lookupJob?.isActive` was false while
     * `state.product` was still null — so both of `load`'s guards said "nothing is happening here"
     * and a second call went back to Open Food Facts for a barcode already fetched.
     *
     * Reaching it in the app takes no unusual behaviour: `LaunchedEffect` re-running the load, a
     * configuration change, or the user tapping the same recent product twice.
     *
     * It passes now because `onProductLoaded` is a `suspend fun` awaited inside `lookupJob`, so the
     * job spans the whole load. **Verified non-vacuous by negative control:** restoring the
     * `viewModelScope.launch` in `onProductLoaded` fails this with `[barcode, barcode]`.
     *
     * Asserted on the fetch list rather than on a flag, because spending a second request against a
     * 15/min shared budget is the actual defect.
     */
    @Test
    fun `a second load after the fetch resolves but before the product lands costs no extra fetch`() =
        runTest(dispatcher) {
            val barcode = "8712100849060"
            val remote = CountingRemote(
                delaysMs = mapOf(barcode to 100L),
                products = mapOf(barcode to product(barcode, "Hagelslag")),
            )
            // Longer than the fetch, so the second load below lands squarely inside the window where
            // the job is finished and the product has not been written.
            val viewModel = viewModelWith(remote, portionUnits = SlowUnits(delayMs = 500L))

            viewModel.load(barcode)
            advanceTimeBy(200L) // fetch done; onProductLoaded still waiting on the portion units

            // Preconditions — without these the test could pass for the wrong reason, by running
            // entirely before or entirely after the window it exists to cover.
            assertNull(
                "precondition: the product must not be on screen yet",
                viewModel.state.value.product,
            )
            assertEquals(
                "precondition: the first fetch must already have happened",
                listOf(barcode),
                remote.fetched,
            )

            viewModel.load(barcode)
            advanceUntilIdle()

            assertEquals(
                "the same barcode was fetched twice across the post-lookup gap",
                listOf(barcode),
                remote.fetched,
            )
            assertEquals("Hagelslag", viewModel.state.value.product?.name)
        }

    /**
     * The single-flight guards must not swallow a retry.
     *
     * `Failure.Lookup` renders a *Try again* action wired straight to `load(barcode)` with the same
     * barcode. That is the one case where re-fetching an already-requested barcode is exactly what
     * the user asked for, so it has to survive both guards — a guard that keyed only on "this
     * barcode was requested before" would leave the button dead and the user stuck on an error
     * screen with no way forward.
     *
     * It passes because a failed load completes its job without setting a product, which is the
     * state both guards read.
     */
    @Test
    fun `retrying after a failed lookup fetches again`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        // No product for this barcode, so the first lookup resolves to NotFound.
        val remote = CountingRemote(delaysMs = mapOf(barcode to 50L), products = emptyMap())
        val viewModel = viewModelWith(remote)

        viewModel.load(barcode)
        advanceUntilIdle()
        assertEquals(listOf(barcode), remote.fetched)

        // Exactly what the "Try again" action does.
        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals(
            "the retry action did not reach the network",
            listOf(barcode, barcode),
            remote.fetched,
        )
    }

    // ---- the fresh-lookup double fetch --------------------------------------------------------

    /**
     * A product never seen before must cost **one** Open Food Facts request, not two.
     *
     * `lookup` on a cache miss fetches remotely and saves the result. `onProductLoaded` then calls
     * `refreshFromRemote`, which reads that just-written row, sees a product to refresh, and goes
     * straight back to the network for the same barcode — a second request against a 15 reads/min/IP
     * budget, issued microseconds after the first, to re-download bytes the app is still holding.
     *
     * The old fake's discarding `save` made this invisible: with nothing in the cache the refresh
     * returned `Unchanged` before its own fetch. It is asserted on the fetch **list** rather than a
     * count so the failure message names the barcode that was requested twice.
     */
    @Test
    fun `a first-time lookup costs exactly one remote fetch`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        val remote = CountingRemote(
            delaysMs = mapOf(barcode to 50L),
            products = mapOf(barcode to product(barcode, "Hagelslag")),
        )
        val viewModel = viewModelWith(remote)

        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals(
            "a cache-miss lookup fetched the same barcode twice",
            listOf(barcode),
            remote.fetched,
        )
        assertEquals("Hagelslag", viewModel.state.value.product?.name)
    }

    /**
     * The refresh must still happen for a product that was **already** cached.
     *
     * This is the other half of the fix and the reason it cannot be "just stop calling
     * `refreshFromRemote`". A cached product is shown without any network call at all, so the
     * background refresh is the only thing that can ever notice a reformulation (corrections #5,
     * #10). Suppressing it wholesale would trade a duplicate request for a permanently stale value.
     *
     * The seeded product carries no `remoteUpdatedAt`, which is what a record cached before this
     * change looks like — it has never been refreshed, so it is due one.
     */
    @Test
    fun `a lookup served from cache still refreshes from remote`() = runTest(dispatcher) {
        val barcode = "8712100849060"
        val cached = product(barcode, "Hagelslag")
        val remote = CountingRemote(
            delaysMs = mapOf(barcode to 50L),
            products = mapOf(barcode to product(barcode, "Hagelslag")),
        )
        val viewModel = viewModelWith(remote, local = RecordingLocal(listOf(cached)))

        viewModel.load(barcode)
        advanceUntilIdle()

        assertEquals(
            "a cached product was never refreshed, so a reformulation could never be noticed",
            listOf(barcode),
            remote.fetched,
        )
    }

    /**
     * Cancelling a superseded lookup must not reopen the duplicate-fetch gap.
     *
     * The abandoned lookup is allowed to have reached the network or not — cancelling after a
     * request has left is still correct — so this asserts on what must hold either way: **no barcode
     * is requested more than once**. A cancellation that dropped the surviving lookup back into the
     * un-stamped state would show up here as the survivor being fetched twice, which is exactly the
     * regression the freshness guard could plausibly reintroduce.
     *
     * The abandoned barcode is deliberately the slow one, so it is cancelled mid-fetch rather than
     * before starting — the state in which its product was never saved and never stamped.
     */
    @Test
    fun `cancelling a superseded lookup leaves the surviving one at a single fetch`() =
        runTest(dispatcher) {
            val first = "8712100849060"
            val second = "5000159484695"
            val remote = CountingRemote(
                delaysMs = mapOf(first to 800L, second to 100L),
                products = mapOf(
                    first to product(first, "Hagelslag"),
                    second to product(second, "Snickers"),
                ),
            )
            val viewModel = viewModelWith(remote)

            viewModel.load(first)
            // Let the first lookup's request actually leave before superseding it, so this covers
            // cancellation *during* a fetch rather than before one has begun.
            advanceTimeBy(100L)
            viewModel.load(second)
            advanceUntilIdle()

            assertEquals(
                "a barcode was requested more than once across the cancellation",
                remote.fetched.distinct(),
                remote.fetched,
            )
            assertEquals(
                "the abandoned lookup's fetch never happened, so the case was not exercised",
                listOf(first, second),
                remote.fetched,
            )
            assertEquals("Snickers", viewModel.state.value.product?.name)
        }
}
