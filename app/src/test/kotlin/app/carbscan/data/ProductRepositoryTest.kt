package app.carbscan.data

import app.carbscan.domain.InputMode
import app.carbscan.domain.LocalProductDataSource
import app.carbscan.domain.LookupError
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitCandidate
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.PortionUnitStore
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataSource
import app.carbscan.domain.ProductFetchResult
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.VerificationStatus
import app.carbscan.data.RefreshOutcome
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

    private class FakeLocal(seed: List<Product> = emptyList()) : LocalProductDataSource {
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

    private class FakePortionUnitStore(seed: List<PortionUnit> = emptyList()) : PortionUnitStore {
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

    // ---- priority 1: user-verified local ------------------------------------------------------

    @Test
    fun `a user-verified product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, BigDecimal("48.2").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    @Test
    fun `a manually created product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val result = repository.lookup(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    // ---- priority 2: cached remote ------------------------------------------------------------

    @Test
    fun `a cached remote product is shown immediately without a network call`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, remote.calls)
    }

    // ---- priority 3: remote --------------------------------------------------------------------

    @Test
    fun `an uncached barcode is fetched from the remote source and cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(1, remote.calls)
        assertNotNull("the fetched product must be cached for offline reuse", local.stored[barcode])
    }

    @Test
    fun `an unknown barcode reports not found rather than inventing a product`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = ProductRepository(local, remote, portionUnits, clock)

        assertEquals(ProductFetchResult.NotFound, repository.lookup(barcode))
        assertNull(local.stored[barcode])
    }

    @Test
    fun `a product whose carbohydrate value fails validation is never cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Unusable(barcode))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        assertEquals(ProductFetchResult.Unusable(barcode), repository.lookup(barcode))
        assertNull("an unusable value must not enter the cache", local.stored[barcode])
    }

    @Test
    fun `a network failure with nothing cached surfaces the error`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val result = repository.lookup(barcode)

        assertEquals(LookupError.OFFLINE, (result as ProductFetchResult.Failed).error)
    }

    // ---- §23: remote must never overwrite user data --------------------------------------------

    @Test
    fun `a background refresh never overwrites a user-verified product`() = runTest {
        val verified = product(VERIFIED_OFF, "48.2", name = "Verified by owner")
        val local = FakeLocal(listOf(verified))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9", "Remote name")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("Verified by owner", stored.name)
        assertEquals(VERIFIED_OFF, stored.provenance())
    }

    @Test
    fun `a background refresh never overwrites a manually created product`() = runTest {
        val local = FakeLocal(listOf(product(MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "99.9")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo(local.stored.getValue(barcode).carbsPer100))
    }

    @Test
    fun `a background refresh does update a cached remote product`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("50.1").compareTo(local.stored.getValue(barcode).carbsPer100))
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
        val repository = ProductRepository(local, remote, portionUnits, clock)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertTrue("refresh must not clear local usage state", stored.favorite)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
    }

    @Test
    fun `verifying a product keeps the original online value and stamps the time`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "48.2")))
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = ProductRepository(local, remote, portionUnits, clock)

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
                    originalRemoteCarbs = BigDecimal("48.2"),
                    verifiedAt = now,
                ),
            ),
        )
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

        repository.saveVerification(barcode, BigDecimal("47.3"), NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
        assertEquals(now, stored.verifiedAt)
    }

    @Test
    fun `verifying a manual product keeps its manual provenance`() = runTest {
        val local = FakeLocal(listOf(product(UNVERIFIED_MANUAL, "12.0")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

        repository.saveVerification(barcode, BigDecimal("12.5"), NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductDataOrigin.MANUAL, stored.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
    }

    /** A user-typed product has no "online value", so offering to reset to one would be a lie. */
    @Test
    fun `verifying a manual product records no original online value`() = runTest {
        val local = FakeLocal(listOf(product(UNVERIFIED_MANUAL, "12.0")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
                    .copy(originalRemoteCarbs = BigDecimal("48.2"), verifiedAt = now),
            ),
        )
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "50.1")))
        val repository = ProductRepository(local, remote, portionUnits, clock)

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
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val outcome = repository.refreshFromRemote(barcode)

        // The difference is REPORTED (correction #10) but never applied: a hand-typed value is the
        // user's, and a sync may not correct it.
        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("99.9")), outcome)
        assertEquals(0, BigDecimal("12.0").compareTo(local.stored.getValue(barcode).carbsPer100))
        assertEquals(UNVERIFIED_MANUAL, local.stored.getValue(barcode).provenance())
        // The remote IS consulted now, so a reformulation can be detected (#10) — but the value
        // in use is untouched, which is what the assertion above proves.
    }

    @Test
    fun `an OCR product is stored as OCR provenance and counts as verified`() = runTest {
        val local = FakeLocal()
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, remote, portionUnits, clock)

        val outcome = repository.refreshFromRemote(barcode)

        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("51.0")), outcome)
        val stored = local.stored.getValue(barcode)
        assertEquals("the value in use must not move", 0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("but the newer figure is recorded", 0, BigDecimal("51.0").compareTo(stored.latestRemoteCarbs!!))
        assertEquals(VERIFIED_OFF, stored.provenance())
    }

    @Test
    fun `a verified product whose online value moved is flagged as differing`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = ProductRepository(
            local,
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "51.0"))),
            portionUnits,
            clock,
        )

        repository.refreshFromRemote(barcode)

        assertTrue(local.stored.getValue(barcode).remoteValueDiffers)
    }

    @Test
    fun `an unchanged online value raises no notice`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = ProductRepository(
            local,
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "48.2"))),
            portionUnits,
            clock,
        )

        assertEquals(RefreshOutcome.Unchanged, repository.refreshFromRemote(barcode))
        assertEquals(false, local.stored.getValue(barcode).remoteValueDiffers)
    }

    /** Applying is a deliberate act, and it is reversible. */
    @Test
    fun `applying the newer online value keeps the previous figure recoverable`() = runTest {
        val local = FakeLocal(
            listOf(product(VERIFIED_OFF, "48.2").copy(latestRemoteCarbs = BigDecimal("51.0"))),
        )
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

        repository.recordUse(barcode, BigDecimal("65"))

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
        assertEquals(now, stored.lastUsedAt)
    }

    @Test
    fun `toggling a favourite does not disturb the carbohydrate value`() = runTest {
        val local = FakeLocal(listOf(product(VERIFIED_OFF, "48.2")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val candidate = PortionUnitCandidate(PortionUnitKind.SLICE, BigDecimal("36"), NutritionBasis.PER_100_G, "1 slice (36 g)")
        val remote = FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), candidate))
        val repository = ProductRepository(local, remote, portionUnits, clock)

        repository.lookup(barcode)

        val saved = portionUnits.stored.values.single { it.productBarcode == barcode }
        assertEquals(PortionUnitKind.SLICE, saved.kind)
        assertEquals(0, BigDecimal("36").compareTo(saved.amountPerUnit))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, saved.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, saved.verificationStatus)
        assertEquals("1 slice (36 g)", saved.rawRemoteServingText)
    }

    @Test
    fun `a user-defined portion unit is persisted as manual and verified`() = runTest {
        val repository = ProductRepository(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
            portionUnits,
            clock,
        )

        val saved = repository.saveUserPortionUnit(
            barcode = barcode,
            kind = PortionUnitKind.CUSTOM,
            amountPerUnit = BigDecimal("24"),
            basis = NutritionBasis.PER_100_G,
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
            amountPerUnit = BigDecimal("36"), basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteAmountPerUnit = BigDecimal("36"),
            latestRemoteAmountPerUnit = BigDecimal("36"), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(existing))
        val repository = ProductRepository(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
            seededUnits,
            clock,
        )

        val verified = repository.verifyPortionUnit(1, confirmedAmountPerUnit = BigDecimal("35"))

        assertEquals("checking against the package does not change where the data came from", ProductDataOrigin.OPEN_FOOD_FACTS, verified.dataSource)
        assertEquals(VerificationStatus.USER_VERIFIED, verified.verificationStatus)
        assertEquals(0, BigDecimal("35").compareTo(verified.amountPerUnit))
    }

    @Test
    fun `a background refresh cannot overwrite a user-verified portion unit`() = runTest {
        val verifiedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            amountPerUnit = BigDecimal("35"), basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = now, originalRemoteAmountPerUnit = BigDecimal("36"),
            latestRemoteAmountPerUnit = BigDecimal("36"), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(verifiedUnit))
        val newCandidate = PortionUnitCandidate(PortionUnitKind.SLICE, BigDecimal("38"), NutritionBasis.PER_100_G, "1 slice (38 g)")
        val repository = ProductRepository(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), newCandidate)),
            seededUnits,
            clock,
        )

        repository.refreshFromRemote(barcode)

        val stored = seededUnits.stored.getValue(1)
        assertEquals("the effective amount must not move", 0, BigDecimal("35").compareTo(stored.amountPerUnit))
        assertEquals("but the newer figure is retained for a notice", 0, BigDecimal("38").compareTo(stored.latestRemoteAmountPerUnit!!))
        assertTrue(stored.remoteAmountDiffers)
    }

    @Test
    fun `a background refresh does update an unverified remote portion unit`() = runTest {
        val unverifiedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            amountPerUnit = BigDecimal("36"), basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteAmountPerUnit = BigDecimal("36"),
            latestRemoteAmountPerUnit = BigDecimal("36"), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(unverifiedUnit))
        val newCandidate = PortionUnitCandidate(PortionUnitKind.SLICE, BigDecimal("38"), NutritionBasis.PER_100_G, "1 slice (38 g)")
        val repository = ProductRepository(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.Found(product(PLAIN_OFF, "42"), newCandidate)),
            seededUnits,
            clock,
        )

        repository.refreshFromRemote(barcode)

        val stored = seededUnits.stored.getValue(1)
        assertEquals(0, BigDecimal("38").compareTo(stored.amountPerUnit))
        // The very first remote value ever seen stays put, even though the effective amount moved.
        assertEquals(0, BigDecimal("36").compareTo(stored.originalRemoteAmountPerUnit!!))
    }

    @Test
    fun `a product can carry more than one portion unit`() = runTest {
        val repository = ProductRepository(
            FakeLocal(listOf(product(PLAIN_OFF, "42"))),
            FakeRemote(ProductFetchResult.NotFound),
            portionUnits,
            clock,
        )

        repository.saveUserPortionUnit(barcode, PortionUnitKind.SLICE, BigDecimal("36"), NutritionBasis.PER_100_G)
        repository.saveUserPortionUnit(barcode, PortionUnitKind.CUSTOM, BigDecimal("24"), NutritionBasis.PER_100_G, "Dumpling")

        val units = repository.findPortionUnits(barcode)
        assertEquals(2, units.size)
    }

    @Test
    fun `countable portion units work fully offline once cached`() = runTest {
        val cachedUnit = PortionUnit(
            id = 1, productBarcode = barcode, kind = PortionUnitKind.SLICE, customLabel = null,
            amountPerUnit = BigDecimal("36"), basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null, originalRemoteAmountPerUnit = BigDecimal("36"),
            latestRemoteAmountPerUnit = BigDecimal("36"), rawRemoteServingText = "1 slice (36 g)",
            createdAt = now, updatedAt = now,
        )
        val seededUnits = FakePortionUnitStore(listOf(cachedUnit))
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = ProductRepository(local, remote, seededUnits, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, remote.calls)
        assertEquals(1, repository.findPortionUnits(barcode).size)
    }

    @Test
    fun `the last input mode, portion unit and count are remembered`() = runTest {
        val local = FakeLocal(listOf(product(PLAIN_OFF, "42")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

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
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), portionUnits, clock)

        repository.recordUse(barcode, portion = BigDecimal("65"), mode = InputMode.GRAMS)

        val stored = local.stored.getValue(barcode)
        assertEquals(InputMode.GRAMS, stored.lastInputMode)
        assertNull(stored.lastSelectedPortionUnitId)
        assertNull(stored.lastCount)
    }
}
