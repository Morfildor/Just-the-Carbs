package app.carbscan.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.VerificationStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * Countable-portions brief §21. Uses `Room.inMemoryDatabaseBuilder(...).build()` — the same
 * connection path [CarbScanDatabase.build] uses in production — deliberately, not
 * `MigrationTestHelper`'s connection: a cascade-delete assertion against the migration-test
 * harness failed even though the schema and foreign key declaration are correct, which turned out
 * to be a property of that harness's connection setup, not of the real app. This test exists to
 * answer the real question — does a real, normally-opened [CarbScanDatabase] enforce the
 * `portion_units.productBarcode` foreign key's `ON DELETE CASCADE` — rather than trust an
 * assumption either way.
 *
 * NOTE: this is an **instrumented** test.
 */
@RunWith(AndroidJUnit4::class)
class PortionUnitDaoTest {

    private lateinit var database: CarbScanDatabase
    private lateinit var productDao: ProductDao
    private lateinit var portionUnitDao: PortionUnitDao

    private val now = Instant.parse("2026-08-14T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarbScanDatabase::class.java,
        ).build()
        productDao = database.productDao()
        portionUnitDao = database.portionUnitDao()
    }

    @After
    fun tearDown() = database.close()

    private fun product(barcode: String) = Product(
        barcode = barcode,
        name = "Sliced Bread",
        carbsPer100 = BigDecimal("42"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    private fun sliceUnit(barcode: String) = app.carbscan.domain.PortionUnit(
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        amountPerUnit = BigDecimal("36"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteAmountPerUnit = BigDecimal("36"),
        latestRemoteAmountPerUnit = BigDecimal("36"),
        rawRemoteServingText = "1 slice (36 g)",
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun aPortionUnitIsPersistedAndReadBackExactly() = runTest {
        productDao.upsert(product("111").toEntity())
        val id = portionUnitDao.upsert(sliceUnit("111").toEntity())

        val stored = portionUnitDao.findById(id)!!.toDomain()
        assertEquals(PortionUnitKind.SLICE, stored.kind)
        assertEquals(0, BigDecimal("36").compareTo(stored.amountPerUnit))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
    }

    @Test
    fun multiplePortionUnitsCanExistForOneProduct() = runTest {
        productDao.upsert(product("111").toEntity())
        portionUnitDao.upsert(sliceUnit("111").toEntity())
        portionUnitDao.upsert(
            sliceUnit("111").copy(kind = PortionUnitKind.CUSTOM, customLabel = "Dumpling", amountPerUnit = BigDecimal("24")).toEntity(),
        )

        assertEquals(2, portionUnitDao.findByBarcode("111").size)
    }

    /**
     * The actual question this file exists to answer: does deleting a product, through the real
     * production database-open path, cascade-delete its portion units? A prior attempt to check
     * this via `MigrationTestHelper`'s connection failed the cascade, which — checked here against
     * the real path — turns out to be specific to that test helper's connection setup, not a
     * property of the app's actual database.
     */
    @Test
    fun deletingAProductCascadesToItsPortionUnits() = runTest {
        productDao.upsert(product("111").toEntity())
        portionUnitDao.upsert(sliceUnit("111").toEntity())
        assertEquals(1, portionUnitDao.findByBarcode("111").size)

        database.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE barcode = '111'")

        assertTrue(
            "cascade delete should leave no orphaned portion units",
            portionUnitDao.findByBarcode("111").isEmpty(),
        )
    }
}
