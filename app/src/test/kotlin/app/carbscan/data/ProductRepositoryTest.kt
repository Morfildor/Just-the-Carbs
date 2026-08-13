package app.carbscan.data

import app.carbscan.domain.LocalProductDataSource
import app.carbscan.domain.LookupError
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataSource
import app.carbscan.domain.ProductFetchResult
import app.carbscan.domain.ProductSource
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

    private fun product(
        source: ProductSource,
        carbs: String,
        name: String = "Hagelslag puur",
    ) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        source = source,
    )

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

    // ---- priority 1: user-verified local ------------------------------------------------------

    @Test
    fun `a user-verified product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.USER_VERIFIED, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "99.9")))
        val repository = ProductRepository(local, remote, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, BigDecimal("48.2").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    @Test
    fun `a manually created product is used without touching the network`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "99.9")))
        val repository = ProductRepository(local, remote, clock)

        val result = repository.lookup(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo((result as ProductFetchResult.Found).product.carbsPer100))
        assertEquals(0, remote.calls)
    }

    // ---- priority 2: cached remote ------------------------------------------------------------

    @Test
    fun `a cached remote product is shown immediately without a network call`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.REMOTE, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = ProductRepository(local, remote, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(0, remote.calls)
    }

    // ---- priority 3: remote --------------------------------------------------------------------

    @Test
    fun `an uncached barcode is fetched from the remote source and cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "48.2")))
        val repository = ProductRepository(local, remote, clock)

        val result = repository.lookup(barcode)

        assertTrue(result is ProductFetchResult.Found)
        assertEquals(1, remote.calls)
        assertNotNull("the fetched product must be cached for offline reuse", local.stored[barcode])
    }

    @Test
    fun `an unknown barcode reports not found rather than inventing a product`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = ProductRepository(local, remote, clock)

        assertEquals(ProductFetchResult.NotFound, repository.lookup(barcode))
        assertNull(local.stored[barcode])
    }

    @Test
    fun `a product whose carbohydrate value fails validation is never cached`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Unusable(barcode))
        val repository = ProductRepository(local, remote, clock)

        assertEquals(ProductFetchResult.Unusable(barcode), repository.lookup(barcode))
        assertNull("an unusable value must not enter the cache", local.stored[barcode])
    }

    @Test
    fun `a network failure with nothing cached surfaces the error`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(ProductFetchResult.Failed(LookupError.OFFLINE))
        val repository = ProductRepository(local, remote, clock)

        val result = repository.lookup(barcode)

        assertEquals(LookupError.OFFLINE, (result as ProductFetchResult.Failed).error)
    }

    // ---- §23: remote must never overwrite user data --------------------------------------------

    @Test
    fun `a background refresh never overwrites a user-verified product`() = runTest {
        val verified = product(ProductSource.USER_VERIFIED, "48.2", name = "Verified by owner")
        val local = FakeLocal(listOf(verified))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "99.9", "Remote name")))
        val repository = ProductRepository(local, remote, clock)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertEquals("Verified by owner", stored.name)
        assertEquals(ProductSource.USER_VERIFIED, stored.source)
    }

    @Test
    fun `a background refresh never overwrites a manually created product`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.MANUAL, "12.0")))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "99.9")))
        val repository = ProductRepository(local, remote, clock)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("12.0").compareTo(local.stored.getValue(barcode).carbsPer100))
    }

    @Test
    fun `a background refresh does update a cached remote product`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.REMOTE, "48.2")))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "50.1")))
        val repository = ProductRepository(local, remote, clock)

        repository.refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("50.1").compareTo(local.stored.getValue(barcode).carbsPer100))
    }

    @Test
    fun `a background refresh preserves the favourite flag and last portion`() = runTest {
        val cached = product(ProductSource.REMOTE, "48.2").copy(
            favorite = true,
            lastPortion = BigDecimal("65"),
            lastUsedAt = now,
        )
        val local = FakeLocal(listOf(cached))
        val remote = FakeRemote(ProductFetchResult.Found(product(ProductSource.REMOTE, "50.1")))
        val repository = ProductRepository(local, remote, clock)

        repository.refreshFromRemote(barcode)

        val stored = local.stored.getValue(barcode)
        assertTrue("refresh must not clear local usage state", stored.favorite)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
    }

    @Test
    fun `verifying a product keeps the original online value and stamps the time`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.REMOTE, "48.2")))
        val remote = FakeRemote(ProductFetchResult.NotFound)
        val repository = ProductRepository(local, remote, clock)

        repository.saveVerification(barcode, verifiedCarbsPer100 = BigDecimal("47.3"), basis = NutritionBasis.PER_100_G)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductSource.USER_VERIFIED, stored.source)
        assertEquals(0, BigDecimal("47.3").compareTo(stored.carbsPer100))
        assertEquals(0, BigDecimal("48.2").compareTo(stored.originalRemoteCarbs!!))
        assertEquals(now, stored.verifiedAt)
    }

    @Test
    fun `resetting to the online value restores the remote figure and clears verification`() = runTest {
        val local = FakeLocal(
            listOf(
                product(ProductSource.USER_VERIFIED, "47.3").copy(
                    originalRemoteCarbs = BigDecimal("48.2"),
                    verifiedAt = now,
                ),
            ),
        )
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), clock)

        repository.resetToOnlineValue(barcode)

        val stored = local.stored.getValue(barcode)
        assertEquals(ProductSource.REMOTE, stored.source)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
        assertNull(stored.verifiedAt)
    }

    // ---- §20/§21: usage memory -----------------------------------------------------------------

    @Test
    fun `recording a portion stores it against the product with the current time`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.REMOTE, "48.2")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), clock)

        repository.recordUse(barcode, BigDecimal("65"))

        val stored = local.stored.getValue(barcode)
        assertEquals(0, BigDecimal("65").compareTo(stored.lastPortion!!))
        assertEquals(now, stored.lastUsedAt)
    }

    @Test
    fun `toggling a favourite does not disturb the carbohydrate value`() = runTest {
        val local = FakeLocal(listOf(product(ProductSource.USER_VERIFIED, "48.2")))
        val repository = ProductRepository(local, FakeRemote(ProductFetchResult.NotFound), clock)

        repository.setFavorite(barcode, true)

        val stored = local.stored.getValue(barcode)
        assertTrue(stored.favorite)
        assertEquals(ProductSource.USER_VERIFIED, stored.source)
        assertEquals(0, BigDecimal("48.2").compareTo(stored.carbsPer100))
    }
}
