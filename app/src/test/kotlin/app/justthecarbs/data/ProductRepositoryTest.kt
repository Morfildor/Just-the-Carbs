package app.justthecarbs.data

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitCandidate
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.PortionUnitStore
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.PortionUsageStore
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductImage
import app.justthecarbs.domain.ProductImageType
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.data.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The lookup priority of brief §10 is a correctness requirement, not an optimisation:
 *
 *   1. user-verified local  — used immediately, never silently overwritten (§23)
 *   2. cached remote local  — shown immediately, refresh must not block (§10.2)
 *   3. Open Food Facts      — network
 *   4. fallback             — the user never hits a dead end
 *
 * Open Food Facts allows 15 reads/min/IP, so "did the repository avoid the network?" is itself a
 * behaviour worth asserting, not an implementation detail.
 */
class ProductRepositoryTest {

    private val now: Instant = Instant.parse("2026-08-13T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val barcode = "8712100849060"
    private val portionUnits = FakePortionUnitStore()

    /**
     * Provenance and verification are two independent facts, so the fixtures name the *combination*
     * under test rather than a single "source".
     */
    private data class Provenance(
        val origin: ProductDataOrigin,
        val status: VerificationStatus,
    )

    private val PLAIN_OFF =
        Provenance(ProductDataOrigin.OPEN_FOOD_FACTS, VerificationStatus.UNVERIFIED)
    private val VERIFIED_OFF =
        Provenance(ProductDataOrigin.OPEN_FOOD_FACTS, VerificationStatus.USER_VERIFIED)
    private val MANUAL =
        Provenance(ProductDataOrigin.MANUAL, VerificationStatus.USER_VERIFIED)
    private val UNVERIFIED_MANUAL =
        Provenance(ProductDataOrigin.MANUAL, VerificationStatus.UNVERIFIED)

    private fun product(
        source: Provenance,
        carbs: String,
        name: String = "Hagelslag puur",
    ) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = source.origin,
        verificationStatus = source.status,
    )

    private fun Product.provenance() = Provenance(dataSource, verificationStatus)

    private open class FakeLocal(seed: List<Product> = emptyList()) : LocalProductDataSource {
        val stored = seed.associateBy { it.barcode }.toMutableMap()

        override suspend fun fetch(barcode: String): ProductFetchResult =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())
    }

    private class FakeRemote(private val result: ProductFetchResult) : ProductDataSource {
        var calls = 0
            private set

        override suspend fun fetch(barcode: String): ProductFetchResult {
            calls++
            return result
        }
    }

    private open class FakePortionUnitStore(seed: List<PortionUnit> = emptyList()) : PortionUnitStore {
        val stored = seed.associateBy { it.id }.toMutableMap()
        private var nextId = (seed.maxOfOrNull { it.id } ?: 0) + 1

        override suspend fun findByBarcode(barcode: String): List<PortionUnit> =
            stored.values.filter { it.productBarcode == barcode }

        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> =
            flowOf(stored.values.filter { it.productBarcode == barcode })

        override suspend fun findById(id: Long): PortionUnit? = stored[id]

        override suspend fun save(unit: PortionUnit): PortionUnit {
            val saved = if (unit.id == 0L) unit.copy(id = nextId++) else unit
            stored[saved.id] = saved
            return saved
        }

        override suspend fun delete(unit: PortionUnit) {
            stored.remove(unit.id)
        }
    }

    private class FakeMealStore : MealStore {
        val stored = mutableListOf<MealItem>()
        private var nextId = 1L

        override fun observeItems(): Flow<List<MealItem>> = flowOf(stored.toList())

        override suspend fun findItems(): List<MealItem> = stored.toList()

        override suspend fun add(item: MealItem): MealItem {
            val saved = item.copy(id = nextId++)
            stored += saved
            return saved
        }

        override suspend fun update(item: MealItem) {
            val index = stored.indexOfFirst { it.id == item.id }
            if (index >= 0) stored[index] = item
        }

        override suspend fun remove(item: MealItem) {
            stored.removeAll { it.id == item.id }
        }

        override suspend fun clear() = stored.clear()
    }

    private class FakePortionUsageStore : PortionUsageStore {
        val stored = mutableListOf<PortionUsage>()
        private var nextId = 1L

        override suspend fun findByBarcode(barcode: String): List<PortionUsage> =
            stored.filter { it.productBarcode == barcode }

        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = stored.firstOrNull {
            it.productBarcode == barcode &&
                it.inputMode == inputMode &&
                it.portionUnitId == portionUnitId &&
                it.amount.compareTo(amount) == 0
        }

        override suspend fun save(usage: PortionUsage): PortionUsage {
            if (usage.id != 0L) {
                val index = stored.indexOfFirst { it.id == usage.id }
                if (index >= 0) stored[index] = usage
                return usage
            }
            val saved = usage.copy(id = nextId++)
            stored += saved
            return saved
        }

        override suspend fun delete(usage: PortionUsage) {
            stored.removeAll { it.id == usage.id }
        }
    }

    private val meal = FakeMealStore()
    private val usage = FakePortionUsageStore()

    /** Search is a separate capability; these tests are about the lookup priority, not about it. */
    private val noSearch = object : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    /**
     * One construction point for the repository under test, so adding a collaborator does not mean
     * editing every test — and so no test can silently depend on positional argument order.
     */
    private fun repositoryOf(
        local: LocalProductDataSource,
        remote: ProductDataSource,
        units: PortionUnitStore = portionUnits,
        mealStore: MealStore = meal,
        usageStore: PortionUsageStore = usage,
    ) = ProductRepository(
        local = local,
        remote = remote,
        portionUnits = units,
        meal = mealStore,
        portionUsage = usageStore,
        searchSource = noSearch,
        clock = clock,
    )

    // ---- priority 1: user-verified local ------------------------------------------------------

    @Test
    fun `a user-verified product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = repositoryOf(local, remote)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, BigDecimal("48.2").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    @Test
    fun `a manually created product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = repositoryOf(local, remote)

        val result = repository.lookup(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    // ---- priority 2: cached remote ------------------------------------------------------------

    @Test
    fun `a cached remote product is shown immediately without a network call`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = repositoryOf(local, remote)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, remote.calls)
    }

    // ---- priority 3: remote --------------------------------------------------------------------

    @Test
    fun `an uncached barcode is fetched from the remote source and cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, remote)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(1, remote.calls)
        assertNotNull("the fetched product must be cached for offline reuse", local.stored[barcode])
    }

    @Test
    fun `an unknown barcode reports not found rather than inventing a product`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = repositoryOf(local, remote)

        assertEquals(ProductFetchResult.NotFound, repository.lookup(barcode))
        assertNull(local.stored[barcode])
    }

    @Test
    fun `a product whose carbohydrate value fails validation is never cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Unusable(barcode))
        val repository = repositoryOf(local, remote)

        assertEquals(ProductFetchResult.Unusable(barcode), repository.lookup(barcode))
        assertNull("an unusable value must not enter the cache", local.stored[barcode])
    }

    @Test
    fun `a network failure with nothing cached surfaces the error`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = repositoryOf(local, remote)

        val result = repository.lookup(barcode)

        assertEquals(LookupError.OFFLINE, (result as ProductFetchResult.Failed).error)
    }

    // ---- §23: remote must never overwrite user data --------------------------------------------

    @Test
    fun `a background refresh never overwrites a user-verified product`() = runTest {
        val verified = product(VERIFIED_OFF, "48.2", name = "Verified by owner")
        val local = FakeLocal(listOf(verified))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9", "Remote name")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("Verified by owner", stored.name)
        assertEquals(VERIFIED_OFF, stored.provenance())
    }

    @Test
    fun `a refresh can add display images without changing a verified carbohydrate value`() = runTest {
        val verified = product(VERIFIED_OFF, "48.2", name = "Verified by owner")
        val image = ProductImage(
            ProductImageType.NUTRITION,
            "en",
            "https://images.openfoodfacts.org/nutrition-en.400.jpg",
        )
        val fetched = product(PLAIN_OFF, "99.9", name = "Remote name").copy(images = listOf(image))
        val local = FakeLocal(listOf(verified))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.Found(fetched)))

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("Verified by owner", stored.name)
        assertEquals(listOf(image), stored.images)
    }

    @Test
    fun `a background refresh never overwrites a manually created product`() = runTest {
        val local = FakeLocal(listOf(product(MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo(local.stored.getValue(barcode).carbsPer100))
    }

    @Test
    fun `a background refresh does update a cached remote product`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("50.1").compareTo(local.stored.getValue(barcode).carbsPer100))
    }

    @Test
    fun `a remote refresh that omits selected images preserves the cached gallery`() = runTest {
        val cachedImage = ProductImage(
            ProductImageType.FRONT,
            "en",
            "https://images.openfoodfacts.org/front-en.400.jpg",
        )
        val cached = product(PLAIN_OFF, "48.2").copy(images = listOf(cachedImage))
        val local = FakeLocal(listOf(cached))
        val remoteWithoutImages = product(PLAIN_OFF, "50.1").copy(images = emptyList())
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.Found(remoteWithoutImages)))

        repository.refreshFromRemote(barcode)

        assertEquals(listOf(cachedImage), local.stored.getValue(barcode).images)
    }

    @Test
    fun `a background refresh preserves the favourite flag and last portion`() = runTest {
        val cached = product(PLAIN_OFF, "48.2").copy(
            favorite = true,
            lastPortion = BigDecimal("65"),
            lastUsedAt = now,
        )
        val local = FakeLocal(listOf(cached))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertTrue("refresh must not clear local usage state", stored.favorite)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
    }

    /**
     * P0 §4: the three countable-portion fields (§11) are exactly as device-owned as `favorite` and
     * `lastPortion`, and were silently dropped by the previous `fetched.copy(...)` — `fetched` is a
     * value straight off the wire, carrying `Product`'s defaults (null) for all three. Together
     * these fields *are* a remembered portion ("2 slices"), so an ordinary background refresh could
     * erase it on any unverified Open Food Facts product with no error, no notice and no way to
     * detect the loss short of noticing the next visit pre-filled nothing.
     */
    @Test
    fun `a background refresh preserves the remembered countable-portion mode`() = runTest {
        val cached = product(PLAIN_OFF, "48.2").copy(
            lastInputMode = InputMode.PORTION_UNIT,
            lastSelectedPortionUnitId = 7L,
            lastCount = BigDecimal("2"),
        )
        val local = FakeLocal(listOf(cached))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(
            "refresh must not clear the remembered countable-portion mode",
            InputMode.PORTION_UNIT,
            stored.lastInputMode,
        )
        assertEquals(7L, stored.lastSelectedPortionUnitId)
        assertEquals(0, BigDecimal("2").compareTo(stored.lastCount!!))
    }

    /**
     * A [ProductDataSource] whose [fetch] suspends for [delayMs] before returning, and whose caller
     * can mutate [local] during that suspension — standing in for the user acting on the product
     * (favouriting it, changing its remembered portion) while the network request is still in
     * flight. This is what makes the lost-update race in P0 §4 reachable in a test at all: without
     * an actual suspension between reading `existing` and writing the merged result, there is no
     * window for a concurrent local write to land in.
     */
    private class SuspendingRemote(
        private val result: ProductFetchResult,
        private val delayMs: Long,
        private val onSuspended: suspend () -> Unit,
    ) : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult {
            onSuspended()
            kotlinx.coroutines.delay(delayMs)
            return result
        }
    }

    /**
     * The classic lost-update shape: read, suspend, a concurrent write lands, then a merge based on
     * the pre-suspension read overwrites it. `refreshFromRemote` used to build its merged product
     * from `existing` — read *before* `remote.fetch` — so a favourite toggled while the network call
     * was in flight was silently rolled back the instant the refresh's own write landed afterwards.
     */
    @Test
    fun `a favourite toggled while a refresh is in flight survives the refresh`() = runTest {
        val cached = product(PLAIN_OFF, "48.2").copy(favorite = false)
        val local = FakeLocal(listOf(cached))
        val remote = SuspendingRemote(
            result = ProductFetchResult.Found(product(PLAIN_OFF, "50.1")),
            delayMs = 100L,
            onSuspended = {
                // The user favourites the product while the network request the refresh started is
                // still in flight — exactly the window a stale pre-fetch snapshot cannot see.
                local.save(local.stored.getValue(barcode).copy(favorite = true))
            },
        )
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertTrue(
            "a favourite set while the refresh was in flight must survive the refresh's own write",
            local.stored.getValue(barcode).favorite,
        )
    }

    /** Same race, for the remembered portion rather than the favourite flag. */
    @Test
    fun `a new portion recorded while a refresh is in flight survives the refresh`() = runTest {
        val cached = product(PLAIN_OFF, "48.2").copy(lastPortion = BigDecimal("50"))
        val local = FakeLocal(listOf(cached))
        val remote = SuspendingRemote(
            result = ProductFetchResult.Found(product(PLAIN_OFF, "50.1")),
            delayMs = 100L,
            onSuspended = {
                local.save(local.stored.getValue(barcode).copy(lastPortion = BigDecimal("90")))
            },
        )
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertEquals(
            "a portion recorded while the refresh was in flight must survive the refresh's own write",
            0,
            BigDecimal("90").compareTo(local.stored.getValue(barcode).lastPortion!!),
        )
    }

    /** The fix must not turn into "never update anything" — remote-owned fields still refresh. */
    @Test
    fun `remote-owned fields still update across the same race window`() = runTest {
        val cached = product(PLAIN_OFF, "48.2").copy(favorite = false)
        val local = FakeLocal(listOf(cached))
        val remote = SuspendingRemote(
            result = ProductFetchResult.Found(product(PLAIN_OFF, "55.5")),
            delayMs = 100L,
            onSuspended = {
                local.save(local.stored.getValue(barcode).copy(favorite = true))
            },
        )
        val repository = repositoryOf(local, remote)

        val outcome = repository.refreshFromRemote(barcode)

        assertTrue(outcome is RefreshOutcome.RemoteDiffers)
        assertEquals(
            "the fix for the race must not come at the cost of the remote figure being recorded",
            0,
            BigDecimal("55.5").compareTo((outcome as RefreshOutcome.RemoteDiffers).latestRemoteCarbs),
        )
        assertEquals(now, local.stored.getValue(barcode).remoteUpdatedAt)
    }

    @Test
    fun `verifying a product keeps the original online value and stamps the time`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = repositoryOf(local, remote)

        repository.saveVerification(barcode, verifiedCarbsPer100 = BigDecimal("47.3"), basis = NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(VERIFIED_OFF, stored.provenance())
        assertEquals(0, BigDecimal("47.3").compareTo(stored.carbsPer100))
        assertEquals(0, BigDecimal("48.2").compareTo(stored.originalRemoteCarbs!!))
        assertEquals(now, stored.verifiedAt)
    }

    @Test
    fun `resetting to the online value restores the remote figure and clears verification`() = runTest {
        val local = FakeLocal(
            listOf(
                product(VERIFIED_OFF, "47.3").copy(
                    originalRemoteCarbs = BigDecimal("48.2"), originalRemoteBasis = NutritionBasis.PER_100_G,
                    verifiedAt = now,
                ),
            ),
        )
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.resetToOnlineValue(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(PLAIN_OFF, stored.provenance())
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertNull(stored.verifiedAt)
    }

    // ---- provenance and verification are independent facts -------------------------------------

    /**
     * The regression this whole section exists for. A product can be *both* Open Food Facts data
     * and user-verified. An earlier model collapsed the two into a single field, which silently
     * rewrote the provenance to "verified" and lost the fact that it ever came from OFF — making a
     * downloaded-then-checked product indistinguishable from one the user typed themselves.
     */
    @Test
    fun `verifying an Open Food Facts product keeps its Open Food Facts provenance`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.saveVerification(barcode, BigDecimal("47.3"), NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
        assertEquals(now, stored.verifiedAt)
    }

    @Test
    fun `verifying a manual product keeps its manual provenance`() = runTest {
        val local = FakeLocal(listOf(product(UNVERIFIED_MANUAL, "12.0")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.saveVerification(barcode, BigDecimal("12.5"), NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.MANUAL, stored.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
    }

    /** A user-typed product has no "online value", so offering to reset to one would be a lie. */
    @Test
    fun `verifying a manual product records no original online value`() = runTest {
        val local = FakeLocal(listOf(product(UNVERIFIED_MANUAL, "12.0")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.saveVerification(barcode, BigDecimal("12.5"), NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertNull(stored.originalRemoteCarbs)
        assertEquals(false, stored.canResetToOnlineValue)
    }

    @Test
    fun `resetting a verified Open Food Facts product leaves it refreshable again`() = runTest {
        val local = FakeLocal(
            listOf(
                product(VERIFIED_OFF, "47.3")
                    .copy(originalRemoteCarbs = BigDecimal("48.2"), originalRemoteBasis = NutritionBasis.PER_100_G, verifiedAt = now),
            ),
        )
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = repositoryOf(local, remote)

        repository.resetToOnlineValue(barcode)
        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
        assertEquals(0, BigDecimal("50.1").compareTo(stored.carbsPer100))
    }

    /**
     * Provenance alone protects user-authored data. This product is NOT verified, so a rule keyed
     * only on verification status would happily overwrite a number the user typed by hand.
     */
    @Test
    fun `an unverified manual product is still never overwritten by remote data`() = runTest {
        val local = FakeLocal(listOf(product(UNVERIFIED_MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = repositoryOf(local, remote)

        val outcome = repository.refreshFromRemote(barcode)

        // The difference is REPORTED (correction #10) but never applied: a hand-typed value is the
        // user's, and a sync may not correct it.
        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("99.9"), NutritionBasis.PER_100_G), outcome)
        assertEquals(0, BigDecimal("12.0").compareTo(local.stored.getValue(barcode).carbsPer100))
        assertEquals(UNVERIFIED_MANUAL, local.stored.getValue(barcode).provenance())
        // The remote IS consulted now, so a reformulation can be detected (#10) — but the value
        // in use is untouched, which is what the assertion above proves.
    }

    @Test
    fun `an OCR product is stored as OCR provenance and counts as verified`() = runTest {
        val local = FakeLocal()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.saveUserAuthoredProduct(
            product(UNVERIFIED_MANUAL, "47.3"),
            origin = ProductDataOrigin.OCR,
        )

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.OCR, stored.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
        assertEquals(now, stored.verifiedAt)
    }

    /**
     * Regression: a product the user just created must be reachable.
     *
     * Recents are keyed on `lastUsedAt`, so a newly authored product with a null timestamp is saved
     * to the database and then invisible — and since there is no browse-all-products screen, it is
     * unreachable forever. Authoring a product IS using it.
     */
    @Test
    fun `a user-authored product is immediately visible in recents`() = runTest {
        val local = FakeLocal()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.saveUserAuthoredProduct(product(UNVERIFIED_MANUAL, "12.0"))

        assertEquals(now, local.stored.getValue(barcode).lastUsedAt)
    }

    // ---- corrections #5 / #10: reformulation is reported, never silently applied ---------------

    /**
     * The unsafe sequence this rule exists to prevent: the screen opens on 48.2, the user types a
     * portion, a background refresh returns 51.0, and the answer changes under their hand.
     */
    @Test
    fun `a refresh reports a changed online value without applying it to a verified product`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        val outcome = repository.refreshFromRemote(barcode)

        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("51.0"), NutritionBasis.PER_100_G), outcome)
        val stored = local.stored.getValue(barcode)
        assertEquals("the value in use must not move", 0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("but the newer figure is recorded", 0, BigDecimal("51.0").compareTo(stored.latestRemoteCarbs!!))
        assertEquals(VERIFIED_OFF, stored.provenance())
    }

    @Test
    fun `a verified product whose online value moved is flagged as differing`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = repositoryOf(
            local,
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0"))),
        )

        repository.refreshFromRemote(barcode)

        assertTrue(local.stored.getValue(barcode).remoteValueDiffers)
    }

    // ---- refresh freshness: one scan must not cost two requests -------------------------------

    /**
     * A product downloaded moments ago is not refreshed again.
     *
     * The calculator calls `refreshFromRemote` immediately after `lookup`, which on a cache miss has
     * just downloaded and saved the product — so without a freshness rule every first-time scan spent
     * **two** of the 15 reads/min/IP budget to fetch the same bytes twice. Seeded here at the same
     * instant as the fixed clock, which is exactly the state `lookup` leaves behind.
     */
    @Test
    fun `a product synced moments ago is not fetched again by a refresh`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2").copy(remoteUpdatedAt = now)))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        val outcome = repository.refreshFromRemote(barcode)

        assertEquals("a freshly synced product was re-fetched", 0, remote.calls)
        assertEquals(RefreshOutcome.Unchanged, outcome)
    }

    /**
     * The freshness rule must not become a cache policy — a stale product still refreshes.
     *
     * Without this the fix above would silently disable reformulation detection (§24, correction
     * #10), which is the entire reason the background refresh exists.
     */
    @Test
    fun `a product synced long ago is still refreshed`() = runTest {
        val stale = product(PLAIN_OFF, "48.2")
            .copy(remoteUpdatedAt = now.minusSeconds(3600))
        val local = FakeLocal(listOf(stale))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        val outcome = repository.refreshFromRemote(barcode)

        assertEquals("a stale product was not refreshed", 1, remote.calls)
        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("51.0"), NutritionBasis.PER_100_G), outcome)
    }

    /**
     * A product that has never recorded a sync is refreshed, not treated as fresh.
     *
     * Null means "never refreshed" — a row cached before this stamp existed, or a user-authored one
     * — and reading null as fresh would permanently freeze exactly those records. This is the case
     * that decides whether the guard is safe by default.
     */
    @Test
    fun `a product that has never been synced is refreshed`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertEquals("a never-synced product must still be refreshed", 1, remote.calls)
    }

    /**
     * A sync stamp in the future is a clock change, not freshness.
     *
     * A device whose clock jumps backwards (a timezone fix, an NTP correction) would otherwise
     * suppress every refresh until real time caught up.
     */
    @Test
    fun `a sync timestamp in the future does not suppress a refresh`() = runTest {
        val local = FakeLocal(
            listOf(product(PLAIN_OFF, "48.2").copy(remoteUpdatedAt = now.plusSeconds(86_400))),
        )
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        repository.refreshFromRemote(barcode)

        assertEquals("a future timestamp was read as freshness", 1, remote.calls)
    }

    /** The stamp is what makes the whole rule work, so it is asserted directly. */
    @Test
    fun `a freshly fetched product records when it was synced`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, remote)

        repository.lookup(barcode)

        assertEquals(now, local.stored.getValue(barcode).remoteUpdatedAt)
    }

    /**
     * The product handed back is the product cached, stamp included.
     *
     * `lookup` saved a stamped copy and returned the unstamped one it came off the wire with, so a
     * caller inspecting the returned product saw a null `remoteUpdatedAt` — "never synced" — about a
     * row the same call had just marked as synced. Nothing in the app read that field off the
     * returned value, so this was a latent inconsistency rather than an observed defect; the point of
     * the assertion is that the two records cannot drift apart again.
     */
    @Test
    fun `a fresh lookup returns the same product it cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, remote)

        val returned = repository.lookup(barcode) as ProductFetchResult.Found
        val cached = local.stored.getValue(barcode)

        assertEquals("returned product must carry the sync stamp", now, returned.product.remoteUpdatedAt)
        assertEquals("returned and cached product must be the same record", cached, returned.product)
    }

    /**
     * A cache hit returns the cached record untouched — including a null stamp.
     *
     * The stamp is applied only where a remote fetch actually happened. Stamping a cache hit would
     * make every read look freshly synced and silently suppress the background refresh.
     */
    @Test
    fun `a cached lookup returns the cached product unstamped`() = runTest {
        val cachedProduct = product(PLAIN_OFF, "48.2")
        val local = FakeLocal(listOf(cachedProduct))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0")))
        val repository = repositoryOf(local, remote)

        val returned = repository.lookup(barcode) as ProductFetchResult.Found

        assertEquals("a cache hit must not reach the network", 0, remote.calls)
        assertEquals(cachedProduct, returned.product)
        assertEquals(null, returned.product.remoteUpdatedAt)
    }

    @Test
    fun `an unchanged online value raises no notice`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = repositoryOf(
            local,
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2"))),
        )

        assertEquals(RefreshOutcome.Unchanged, repository.refreshFromRemote(barcode))
        assertEquals(false, local.stored.getValue(barcode).remoteValueDiffers)
    }

    /** Applying is a deliberate act, and it is reversible. */
    @Test
    fun `applying the newer online value keeps the previous figure recoverable`() = runTest {
        val local = FakeLocal(
            listOf(product(VERIFIED_OFF, "48.2").copy(latestRemoteCarbs = BigDecimal("51.0"), latestRemoteBasis = NutritionBasis.PER_100_G)),
        )
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.applyLatestRemoteValue(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("51.0").compareTo(stored.carbsPer100))
        assertEquals(0, BigDecimal("48.2").compareTo(stored.originalRemoteCarbs!!))
        // The user has not checked THIS number against a package, so it is no longer verified.
        assertEquals(VerificationStatus.UNVERIFIED, stored.verificationStatus)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
    }

    @Test
    fun `applying does nothing when there is no newer value`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.applyLatestRemoteValue(barcode)

        assertEquals(0, BigDecimal("48.2").compareTo(local.stored.getValue(barcode).carbsPer100))
        assertEquals(VERIFIED_OFF, local.stored.getValue(barcode).provenance())
    }

    @Test
    fun `only an unverified Open Food Facts product is remote-refreshable`() {
        assertTrue(product(PLAIN_OFF, "1").isRemoteRefreshable)
        assertEquals(false, product(VERIFIED_OFF, "1").isRemoteRefreshable)
        assertEquals(false, product(MANUAL, "1").isRemoteRefreshable)
        assertEquals(false, product(UNVERIFIED_MANUAL, "1").isRemoteRefreshable)
    }

    // ---- §20/§21: usage memory -----------------------------------------------------------------

    @Test
    fun `recording a portion stores it against the product with the current time`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(barcode, BigDecimal("65"))

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
        assertEquals(now, stored.lastUsedAt)
    }

    @Test
    fun `toggling a favourite does not disturb the carbohydrate value`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.setFavorite(barcode, true)

        val stored = local.stored.getValue(barcode)
        assertTrue(stored.favorite)
        assertEquals(VERIFIED_OFF, stored.provenance())
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
    }

    // ---- countable portions: persistence, verification, remote-refresh immutability (§21) ------

    @Test
    fun `a remote portion unit candidate is persisted on first lookup`() = runTest {
        val local = FakeLocal()
        val candidate = PortionUnitCandidate(PortionUnitKind.SLICE, PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G), "1 slice (36 g)")
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), candidate))
        val repository = repositoryOf(local, remote)

        repository.lookup(barcode)

        val saved = portionUnits.stored.values.single { it.productBarcode == barcode }
        assertEquals(PortionUnitKind.SLICE, saved.kind)
        assertEquals(0, BigDecimal("36").compareTo(saved.conversion.weight()))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, saved.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, saved.verificationStatus)
        assertEquals("1 slice (36 g)", saved.rawRemoteServingText)
    }

    @Test
    fun `a user-defined portion unit is persisted as manual and verified`() = runTest {
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
        )

        val saved = repository.saveUserPortionUnit(
            barcode = barcode,
            kind = PortionUnitKind.CUSTOM,
            conversion = PortionConversion.WeightBased(BigDecimal("24"), NutritionBasis.PER_100_G),
            customLabel = "Dumpling",
        )

        assertEquals(ProductDataOrigin.MANUAL, saved.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, saved.verificationStatus)
        assertEquals("Dumpling", saved.customLabel)
        assertEquals(now, saved.verifiedAt)
        assertTrue(saved.id > 0)
    }

    @Test
    fun `a remote unit and a user verification of it coexist as provenance and status`() = runTest {
        val existing = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(existing))
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
            units = seededUnits,
        )

        val verified = repository.verifyPortionUnit(1, confirmedConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))

        assertEquals("checking against the package does not change where the data came from", ProductDataOrigin.OPEN_FOOD_FACTS, verified.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, verified.verificationStatus)
        assertEquals(0, BigDecimal("35").compareTo(verified.conversion.weight()))
    }

    @Test
    fun `a background refresh cannot overwrite a user-verified portion unit`() = runTest {
        val verifiedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = now, originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(verifiedUnit))
        val newCandidate = PortionUnitCandidate(PortionUnitKind.SLICE, PortionConversion.WeightBased(BigDecimal("38"), NutritionBasis.PER_100_G), "1 slice (38 g)")
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), newCandidate)),
            units = seededUnits,
        )

        repository.refreshFromRemote(barcode)

        val stored = seededUnits.stored.getValue(1)
        assertEquals("the effective amount must not move", 0, BigDecimal("35").compareTo(stored.conversion.weight()))
        assertEquals("but the newer figure is retained for a notice", 0, BigDecimal("38").compareTo(stored.latestRemoteConversion!!.weight()))
        assertTrue(stored.remoteConversionDiffers)
    }

    @Test
    fun `a background refresh does update an unverified remote portion unit`() = runTest {
        val unverifiedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(unverifiedUnit))
        val newCandidate = PortionUnitCandidate(PortionUnitKind.SLICE, PortionConversion.WeightBased(BigDecimal("38"), NutritionBasis.PER_100_G), "1 slice (38 g)")
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), newCandidate)),
            units = seededUnits,
        )

        repository.refreshFromRemote(barcode)

        val stored = seededUnits.stored.getValue(1)
        assertEquals(0, BigDecimal("38").compareTo(stored.conversion.weight()))
        // The very first remote value ever seen stays put, even though the effective amount moved.
        assertEquals(0, BigDecimal("36").compareTo(stored.originalRemoteConversion!!.weight()))
    }

    // ---- direct-carb portion units (spec §9, §12) ----------------------------------------------

    @Test
    fun `a remote direct-carb candidate becomes a stored direct-carb unit`() = runTest {
        val local = FakeLocal()
        val candidate = PortionUnitCandidate(
            PortionUnitKind.SLICE,
            PortionConversion.DirectCarbs(BigDecimal("12.6")),
            "2 slices",
        )
        val repository = repositoryOf(
            local,
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), candidate)),
        )

        repository.lookup(barcode)

        val saved = portionUnits.stored.values.single { it.productBarcode == barcode }
        assertEquals(PortionConversion.DirectCarbs(BigDecimal("12.6")), saved.conversion)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, saved.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, saved.verificationStatus)
    }

    @Test
    fun `a refresh cannot overwrite a user-verified direct-carb unit`() = runTest {
        val verifiedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = now, originalRemoteConversion = null, latestRemoteConversion = null,
            rawRemoteServingText = "2 slices", createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(verifiedUnit))
        val newCandidate = PortionUnitCandidate(
            PortionUnitKind.SLICE,
            PortionConversion.DirectCarbs(BigDecimal("20.0")),
            "1 slice",
        )
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), newCandidate)),
            units = seededUnits,
        )

        repository.refreshFromRemote(barcode)

        val stored = seededUnits.stored.getValue(1)
        assertEquals(
            "the user's own figure stands, exactly as it would for a weight",
            PortionConversion.DirectCarbs(BigDecimal("14.2")),
            stored.conversion,
        )
        assertEquals(PortionConversion.DirectCarbs(BigDecimal("20.0")), stored.latestRemoteConversion)
    }

    @Test
    fun `a refresh does update an unverified direct-carb unit`() = runTest {
        val unverified = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            originalRemoteConversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
            latestRemoteConversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
            rawRemoteServingText = "2 slices", createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(unverified))
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(
                ProductFetchResult.Found(
                    product(PLAIN_OFF, "42"),
                    PortionUnitCandidate(
                        PortionUnitKind.SLICE,
                        PortionConversion.DirectCarbs(BigDecimal("13.1")),
                        "2 slices",
                    ),
                ),
            ),
            units = seededUnits,
        )

        repository.refreshFromRemote(barcode)

        assertEquals(
            PortionConversion.DirectCarbs(BigDecimal("13.1")),
            seededUnits.stored.getValue(1).conversion,
        )
    }

    @Test
    fun `a user can define a direct-carb unit without knowing any weight`() = runTest {
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
        )

        val saved = repository.saveUserPortionUnit(
            barcode = barcode,
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        )

        assertEquals(PortionConversion.DirectCarbs(BigDecimal("14.2")), saved.conversion)
        assertEquals(ProductDataOrigin.MANUAL, saved.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, saved.verificationStatus)
    }

    @Test
    fun `a direct-carb meal item records no resolved grams`() = runTest {
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
        )

        val item = repository.addDirectCarbMealItem(
            productBarcode = barcode,
            displayName = "Crackers",
            portionDescription = "4 slices",
            count = BigDecimal("4"),
            carbsPerUnit = BigDecimal("14.2"),
            exactCarbs = BigDecimal("56.8"),
        )

        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertNull("a counted portion must never carry a weight the app never knew", item.resolvedAmount)
        assertEquals(0, BigDecimal("56.8").compareTo(repository.findMealItems().single().exactCarbs))
    }

    @Test
    fun `a product can carry more than one portion unit`() = runTest {
        val repository = repositoryOf(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
        )

        repository.saveUserPortionUnit(barcode, PortionUnitKind.SLICE, PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G))
        repository.saveUserPortionUnit(barcode, PortionUnitKind.CUSTOM, PortionConversion.WeightBased(BigDecimal("24"), NutritionBasis.PER_100_G), "Dumpling")

        val units = repository.findPortionUnits(barcode)
        assertEquals(2, units.size)
    }

    @Test
    fun `countable portion units work fully offline once cached`() = runTest {
        val cachedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            conversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(cachedUnit))
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = repositoryOf(local, remote, units = seededUnits)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, remote.calls)
        assertEquals(1, repository.findPortionUnits(barcode).size)
    }

    @Test
    fun `the last input mode, portion unit and count are remembered`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(
            barcode,
            portion = BigDecimal("72"),
            mode = InputMode.PORTION_UNIT,
            portionUnitId = 7,
            count = BigDecimal("2"),
        )

        val stored = local.stored.getValue(barcode)
        assertEquals(InputMode.PORTION_UNIT, stored.lastInputMode)
        assertEquals(7L, stored.lastSelectedPortionUnitId)
        assertEquals(0, BigDecimal("2").compareTo(stored.lastCount!!))
    }

    @Test
    fun `switching back to grams clears the remembered portion unit selection`() = runTest {
        val local = FakeLocal(
            listOf(
                product(PLAIN_OFF, "42").copy(
                    lastInputMode = InputMode.PORTION_UNIT,
                    lastSelectedPortionUnitId = 7,
                    lastCount = BigDecimal("2"),
                ),
            ),
        )
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(barcode, portion = BigDecimal("65"), mode = InputMode.GRAMS)

        val stored = local.stored.getValue(barcode)
        assertEquals(InputMode.GRAMS, stored.lastInputMode)
        assertNull(stored.lastSelectedPortionUnitId)
        assertNull(stored.lastCount)
    }

    // ---- direct-carb usage must not corrupt lastPortion (correction pass §4) --------------------
    //
    // `lastPortion` is strictly a resolved mass/volume in the product's own basis unit — it
    // pre-fills the grams field. A direct-carb portion resolves no weight at all, so there is
    // nothing legitimate to write there. Writing the *count* instead (the old `parse(portionText)
    // ?: count` fallback) silently reinterprets "4 slices" as "4 grams", and the next visit in
    // grams mode pre-fills 4 g of bread.

    @Test
    fun `using a direct-carb portion leaves the remembered gram portion untouched`() = runTest {
        val local = FakeLocal(
            listOf(product(PLAIN_OFF, "42").copy(lastPortion = BigDecimal("65"))),
        )
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(
            barcode,
            portion = null,
            mode = InputMode.PORTION_UNIT,
            portionUnitId = 7,
            count = BigDecimal("4"),
        )

        val stored = local.stored.getValue(barcode)
        assertEquals(
            "the previously remembered weight must survive a direct-carb use",
            0,
            BigDecimal("65").compareTo(stored.lastPortion!!),
        )
    }

    @Test
    fun `a direct-carb use still remembers the count and the unit`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(
            barcode,
            portion = null,
            mode = InputMode.PORTION_UNIT,
            portionUnitId = 7,
            count = BigDecimal("4"),
        )

        val stored = local.stored.getValue(barcode)
        assertEquals(InputMode.PORTION_UNIT, stored.lastInputMode)
        assertEquals(7L, stored.lastSelectedPortionUnitId)
        assertEquals(0, BigDecimal("4").compareTo(stored.lastCount!!))
    }

    @Test
    fun `a direct-carb use with no prior portion leaves lastPortion null`() = runTest {
        // The count must not become the product's first remembered weight either — "4" arriving in
        // an empty field is exactly as wrong as "4" overwriting 65.
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(
            barcode,
            portion = null,
            mode = InputMode.PORTION_UNIT,
            portionUnitId = 7,
            count = BigDecimal("4"),
        )

        assertNull(local.stored.getValue(barcode).lastPortion)
    }

    @Test
    fun `a direct-carb use still records a usual portion keyed on the count`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repeat(2) {
            repository.recordUse(
                barcode,
                portion = null,
                mode = InputMode.PORTION_UNIT,
                portionUnitId = 7,
                count = BigDecimal("4"),
            )
        }

        val usual = repository.usualPortions(barcode)
        assertEquals(1, usual.size)
        assertEquals(InputMode.PORTION_UNIT, usual.first().inputMode)
        assertEquals(0, BigDecimal("4").compareTo(usual.first().amount))
    }

    @Test
    fun `a weight-based countable use still updates lastPortion`() = runTest {
        // The other half of the branch: a weight-based unit does resolve real grams, and those
        // grams remain the right thing to remember.
        val local = FakeLocal(
            listOf(product(PLAIN_OFF, "42").copy(lastPortion = BigDecimal("65"))),
        )
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(
            barcode,
            portion = BigDecimal("72"),
            mode = InputMode.PORTION_UNIT,
            portionUnitId = 7,
            count = BigDecimal("2"),
        )

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("72").compareTo(stored.lastPortion!!))
        assertEquals(0, BigDecimal("2").compareTo(stored.lastCount!!))
    }

    // ---- OCR portion capture for an unknown product (correction pass §2) ------------------------
    //
    // `portion_units.productBarcode` is a foreign key to `products.barcode`, so a portion cannot be
    // stored before its product exists. The OCR save action was reachable whenever a barcode string
    // existed — including the not-found screen, which has a real barcode and no product row — so the
    // insert failed while the UI had already said "saved".
    //
    // `saveProductWithPortionUnit` is the one ordered boundary: product first, portion second, and
    // the portion is only attempted if the product actually landed.

    @Test
    fun `creating a product with a pending portion persists the product before the portion`() = runTest {
        val local = FakeLocal()
        val units = FakePortionUnitStore()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound), units = units)

        val saved = repository.saveProductWithPortionUnit(
            product = product(MANUAL, "42").copy(barcode = barcode),
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
            origin = ProductDataOrigin.OCR,
        )

        assertTrue("the product must exist", local.stored.containsKey(barcode))
        assertEquals(1, units.stored.size)
        assertEquals(barcode, saved.productBarcode)
        assertEquals(PortionUnitKind.SLICE, saved.kind)
        assertEquals(
            0,
            BigDecimal("14.2").compareTo(
                (saved.conversion as PortionConversion.DirectCarbs).carbsPerUnit,
            ),
        )
    }

    @Test
    fun `an OCR-captured portion keeps OCR provenance and verified status`() = runTest {
        // The user was reading the physical package, which is exactly what verification means here —
        // but the provenance stays OCR, per the owner's provenance-vs-verification correction.
        val local = FakeLocal()
        val units = FakePortionUnitStore()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound), units = units)

        val saved = repository.saveProductWithPortionUnit(
            product = product(MANUAL, "42").copy(barcode = barcode),
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
            origin = ProductDataOrigin.OCR,
        )

        assertEquals(ProductDataOrigin.OCR, saved.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, saved.verificationStatus)
    }

    @Test
    fun `a failed product write means no portion is attempted and nothing is left behind`() = runTest {
        // The false-success case the UI must never render as saved. If the product write throws,
        // the portion insert must not run at all — an orphaned unit would violate the FK anyway,
        // and a "saved" message would be a plain lie.
        val local = object : FakeLocal() {
            override suspend fun save(product: Product) {
                throw IllegalStateException("disk full")
            }
        }
        val units = FakePortionUnitStore()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound), units = units)

        val failure = runCatching {
            repository.saveProductWithPortionUnit(
                product = product(MANUAL, "42").copy(barcode = barcode),
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
                origin = ProductDataOrigin.OCR,
            )
        }

        assertTrue("the failure must surface to the caller", failure.isFailure)
        assertTrue("no portion may be written without its product", units.stored.isEmpty())
    }

    @Test
    fun `a failed portion write surfaces rather than reporting success`() = runTest {
        val local = FakeLocal()
        val units = object : FakePortionUnitStore() {
            override suspend fun save(unit: PortionUnit): PortionUnit {
                throw IllegalStateException("constraint failed")
            }
        }
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound), units = units)

        val failure = runCatching {
            repository.saveProductWithPortionUnit(
                product = product(MANUAL, "42").copy(barcode = barcode),
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
                origin = ProductDataOrigin.OCR,
            )
        }

        assertTrue("the caller must see the failure, not a success", failure.isFailure)
        assertTrue("no portion was stored", units.stored.isEmpty())
    }

    @Test
    fun `saving a portion for an existing product does not need the product creation path`() = runTest {
        // The other branch of §2: the product row already exists, so the portion is saved directly
        // and the result is awaited before anything is reported to the user.
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val units = FakePortionUnitStore()
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound), units = units)

        val saved = repository.saveUserPortionUnit(
            barcode = barcode,
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
            origin = ProductDataOrigin.OCR,
        )

        assertEquals(1, units.stored.size)
        assertEquals(barcode, saved.productBarcode)
        assertEquals(ProductDataOrigin.OCR, saved.dataSource)
    }

    // ---- temporary meal (development-pass brief §7-§10) ----------------------------------------

    private suspend fun ProductRepository.addItem(
        name: String,
        description: String,
        resolved: String,
        carbs: String,
        exact: String,
    ) = addMealItem(
        productBarcode = barcode,
        displayName = name,
        portionDescription = description,
        resolvedAmount = BigDecimal(resolved),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal(carbs),
        exactCarbs = BigDecimal(exact),
    )

    @Test
    fun `a meal item keeps the portion in the words the user chose`() = runTest {
        val repository = repositoryOf(FakeLocal(), FakeRemote(ProductFetchResult.NotFound))

        val item = repository.addItem("Bread", "2 slices", "72", "48.2", "34.704")

        assertEquals("2 slices", item.portionDescription)
        assertEquals(0, BigDecimal("72").compareTo(item.resolvedAmount))
    }

    /**
     * §9's whole point. The total must come from the unrounded figures, so it cannot be the sum of
     * what the screen displayed — 18.65 and 21.65 display as 18.7 and 21.7, whose sum is 40.4, and
     * as whole grams 19 + 22 = 41. Only the exact sum gives 40.30.
     */
    @Test
    fun `the meal total sums unrounded values rather than displayed ones`() = runTest {
        val repository = repositoryOf(FakeLocal(), FakeRemote(ProductFetchResult.NotFound))
        repository.addItem("A", "50 g", "50", "37.3", "18.65")
        repository.addItem("B", "50 g", "50", "43.3", "21.65")

        val total = app.justthecarbs.domain.MealTotal.exact(repository.findMealItems())

        assertEquals(0, BigDecimal("40.30").compareTo(total))
    }

    /**
     * A meal line is a snapshot, not a live view. Re-verifying the product at a different value must
     * leave an already-added line showing the number the user accepted at the time (§9).
     */
    @Test
    fun `correcting a product afterwards does not change an item already in the meal`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))
        repository.addItem("Bread", "2 slices", "72", "48.2", "34.704")

        repository.saveVerification(barcode, BigDecimal("51.0"), NutritionBasis.PER_100_G)

        val item = repository.findMealItems().single()
        assertEquals(0, BigDecimal("48.2").compareTo(item.carbsPer100))
        assertEquals(0, BigDecimal("34.704").compareTo(item.exactCarbs))
    }

    @Test
    fun `clearing the meal leaves nothing behind`() = runTest {
        val repository = repositoryOf(FakeLocal(), FakeRemote(ProductFetchResult.NotFound))
        repository.addItem("A", "50 g", "50", "37.3", "18.65")
        repository.addItem("B", "50 g", "50", "43.3", "21.65")

        repository.clearMeal()

        assertTrue(repository.findMealItems().isEmpty())
    }

    // ---- usual portions (brief §13, §22) -------------------------------------------------------

    @Test
    fun `one use of a portion is not yet a usual portion`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(barcode, portion = BigDecimal("65"), mode = InputMode.GRAMS)

        assertTrue(repository.usualPortions(barcode).isEmpty())
    }

    @Test
    fun `a portion used twice becomes a usual portion`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repeat(2) { repository.recordUse(barcode, portion = BigDecimal("65"), mode = InputMode.GRAMS) }

        val usual = repository.usualPortions(barcode).single()
        assertEquals(2, usual.usageCount)
        assertEquals(0, BigDecimal("65").compareTo(usual.amount))
    }

    /**
     * The column is TEXT, so without normalisation "65" and "65.0" would be two rows and neither
     * would ever reach the two-use threshold — the feature would silently never trigger.
     */
    @Test
    fun `the same portion typed with and without a trailing zero counts as one variant`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repository.recordUse(barcode, portion = BigDecimal("65"), mode = InputMode.GRAMS)
        repository.recordUse(barcode, portion = BigDecimal("65.0"), mode = InputMode.GRAMS)

        assertEquals(2, repository.usualPortions(barcode).single().usageCount)
    }

    /**
     * "2 slices" and "2 g" are different portions that happen to share a number. Recording the
     * resolved grams instead of the count would merge them and offer the user a nonsense shortcut.
     */
    @Test
    fun `a count against a unit is recorded as the count, not the resolved grams`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repeat(2) {
            repository.recordUse(
                barcode,
                portion = BigDecimal("72"),
                mode = InputMode.PORTION_UNIT,
                portionUnitId = 7,
                count = BigDecimal("2"),
            )
        }

        val usual = repository.usualPortions(barcode).single()
        assertEquals(InputMode.PORTION_UNIT, usual.inputMode)
        assertEquals(7L, usual.portionUnitId)
        assertEquals(0, BigDecimal("2").compareTo(usual.amount))
    }

    @Test
    fun `a gram portion and an identical count against a unit stay separate variants`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        repeat(2) { repository.recordUse(barcode, portion = BigDecimal("2"), mode = InputMode.GRAMS) }
        repeat(2) {
            repository.recordUse(
                barcode,
                portion = BigDecimal("72"),
                mode = InputMode.PORTION_UNIT,
                portionUnitId = 7,
                count = BigDecimal("2"),
            )
        }

        assertEquals(2, repository.usualPortions(barcode).size)
    }

    /** At most three, per §13 — a row of shortcuts the user has to read is not a shortcut. */
    @Test
    fun `no more than three usual portions are ever offered`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = repositoryOf(local, FakeRemote(ProductFetchResult.NotFound))

        listOf("30", "45", "60", "75").forEach { amount ->
            repeat(2) { repository.recordUse(barcode, portion = BigDecimal(amount), mode = InputMode.GRAMS) }
        }

        assertEquals(3, repository.usualPortions(barcode).size)
    }

    /**
     * The gram weight of a weight-based conversion. Fails loudly on a direct-carb one rather than
     * returning a default, so a test asserting about grams cannot silently pass on a unit that has
     * none.
     */
    private fun PortionConversion.weight(): BigDecimal = when (this) {
        is PortionConversion.WeightBased -> amountPerUnit
        is PortionConversion.DirectCarbs -> error("expected a weight-based conversion, got $this")
    }
}
