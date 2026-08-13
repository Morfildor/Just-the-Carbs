package app.carbscan.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.Product
import app.carbscan.domain.ProductSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * Covers the parts of §59 "product history" that live in SQL rather than in Kotlin: ordering,
 * the favourites-float-to-top rule, and the exactness of the TEXT decimal round-trip.
 *
 * NOTE: this is an **instrumented** test. It needs a device or emulator, and none is available in
 * the current development environment, so it has been compiled but never executed. Treat it as
 * unverified until it has been run on hardware.
 */
@RunWith(AndroidJUnit4::class)
class ProductDaoTest {

    private lateinit var database: CarbScanDatabase
    private lateinit var dao: ProductDao

    private val epoch = Instant.parse("2026-08-13T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarbScanDatabase::class.java,
        ).build()
        dao = database.productDao()
    }

    @After
    fun tearDown() = database.close()

    private fun product(
        barcode: String,
        carbs: String = "48.2",
        usedSecondsAfterEpoch: Long? = null,
        favorite: Boolean = false,
        source: ProductSource = ProductSource.REMOTE,
    ) = Product(
        barcode = barcode,
        name = "Product $barcode",
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        source = source,
        lastUsedAt = usedSecondsAfterEpoch?.let(epoch::plusSeconds),
        lastPortion = usedSecondsAfterEpoch?.let { BigDecimal("65") },
        favorite = favorite,
    )

    @Test
    fun recentsAreOrderedByMostRecentlyUsed() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())
        dao.upsert(product("222", usedSecondsAfterEpoch = 30).toEntity())
        dao.upsert(product("333", usedSecondsAfterEpoch = 20).toEntity())

        val recents = dao.observeRecents(limit = 10).first()

        assertEquals(listOf("222", "333", "111"), recents.map { it.barcode })
    }

    @Test
    fun favouritesFloatAboveMoreRecentlyUsedProducts() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10, favorite = true).toEntity())
        dao.upsert(product("222", usedSecondsAfterEpoch = 99).toEntity())

        val recents = dao.observeRecents(limit = 10).first()

        assertEquals(listOf("111", "222"), recents.map { it.barcode })
    }

    @Test
    fun aStarredProductAppearsBeforeItHasEverBeenUsed() = runTest {
        dao.upsert(product("111", favorite = true).toEntity())

        assertEquals(1, dao.observeRecents(limit = 10).first().size)
    }

    @Test
    fun anUnusedUnstarredProductIsNotARecent() = runTest {
        dao.upsert(product("111").toEntity())

        assertEquals(0, dao.observeRecents(limit = 10).first().size)
    }

    @Test
    fun recentsRespectTheLimit() = runTest {
        repeat(5) { dao.upsert(product("bc$it", usedSecondsAfterEpoch = it.toLong()).toEntity()) }

        assertEquals(3, dao.observeRecents(limit = 3).first().size)
    }

    /** The reason carbohydrate columns are TEXT: a REAL column would not survive this. */
    @Test
    fun carbohydrateValuesRoundTripExactly() = runTest {
        dao.upsert(product("111", carbs = "48.2").toEntity())

        val stored = dao.findByBarcode("111")!!.toDomain()

        assertEquals(BigDecimal("48.2"), stored.carbsPer100)
    }

    @Test
    fun verificationTimestampRoundTrips() = runTest {
        val verified = product("111", source = ProductSource.USER_VERIFIED)
            .copy(verifiedAt = epoch, originalRemoteCarbs = BigDecimal("50.0"))
        dao.upsert(verified.toEntity())

        val stored = dao.findByBarcode("111")!!.toDomain()

        assertEquals(epoch, stored.verifiedAt)
        assertEquals(ProductSource.USER_VERIFIED, stored.source)
        assertEquals(BigDecimal("50.0"), stored.originalRemoteCarbs)
    }

    @Test
    fun upsertReplacesTheExistingRowRatherThanAddingOne() = runTest {
        dao.upsert(product("111", carbs = "48.2", usedSecondsAfterEpoch = 10).toEntity())
        dao.upsert(product("111", carbs = "50.1", usedSecondsAfterEpoch = 20).toEntity())

        assertEquals(1, dao.observeRecents(limit = 10).first().size)
        assertEquals(BigDecimal("50.1"), dao.findByBarcode("111")!!.toDomain().carbsPer100)
    }

    /** §43 clearing history must not destroy what §23 calls the reliability feature. */
    @Test
    fun clearingRecentHistoryKeepsVerifiedProductsAndFavourites() = runTest {
        dao.upsert(
            product("111", usedSecondsAfterEpoch = 10, source = ProductSource.USER_VERIFIED)
                .copy(verifiedAt = epoch)
                .toEntity(),
        )
        dao.upsert(product("222", usedSecondsAfterEpoch = 20, favorite = true).toEntity())

        dao.clearRecentHistory()

        val verified = dao.findByBarcode("111")!!.toDomain()
        assertNotNull("the verified product itself must survive", verified)
        assertEquals(ProductSource.USER_VERIFIED, verified.source)
        assertNull("but it no longer shows as recent", verified.lastUsedAt)

        val favourite = dao.findByBarcode("222")!!.toDomain()
        assertNotNull("a favourite keeps its portion memory", favourite.lastUsedAt)
    }

    @Test
    fun deletingAllProductsEmptiesTheStore() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())

        dao.deleteAllProducts()

        assertNull(dao.findByBarcode("111"))
    }
}
