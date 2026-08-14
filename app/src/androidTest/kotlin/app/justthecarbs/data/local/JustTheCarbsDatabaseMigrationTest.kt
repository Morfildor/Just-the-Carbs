package app.justthecarbs.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v2 -> v3 (countable-portions brief §13) must be non-destructive: everything a v2 install already
 * has on disk — verified carbs, favourites, recent history, latest-remote figures — must survive.
 *
 * NOTE: this is an **instrumented** test (Room's `MigrationTestHelper` requires
 * `android.app.Instrumentation`, so unlike the domain/data-mapping tests it cannot run as a plain
 * JVM unit test — matches the project's standing "no Robolectric, DAO tests stay instrumented"
 * decision).
 */
class JustTheCarbsDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JustTheCarbsDatabase::class.java,
    )

    @Test
    fun migratingFromV4PreservesProductDataAndAddsEmptyGalleryMetadata() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('v4-product', 'Preserved', '42.5', 'PER_100_G', 'OPEN_FOOD_FACTS', 'USER_VERIFIED', 1)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, JustTheCarbsDatabase.MIGRATION_4_5)
        migrated.query(
            "SELECT carbsPer100, verificationStatus, favorite, galleryImagesJson FROM products WHERE barcode = 'v4-product'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("42.5", cursor.getString(0))
            assertEquals("USER_VERIFIED", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertTrue("old rows start without gallery metadata", cursor.isNull(3))
        }
    }

    @Test
    fun migratingFromV2PreservesAnExistingVerifiedFavouriteProduct() {
        val db = helper.createDatabase(TEST_DB, 2)
        db.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, brand, packageAmount,
             servingAmount, imageUrl, originalRemoteCarbs, latestRemoteCarbs, verifiedAt,
             remoteUpdatedAt, lastUsedAt, lastPortion, favorite)
            VALUES
            ('5449000000996', 'Test Bread', '42.0', 'PER_100_G', 'OPEN_FOOD_FACTS', 'USER_VERIFIED',
             'TestBrand', '500', NULL, NULL, '40.0', '43.0', 1000, 2000, 3000, '65', 1)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, JustTheCarbsDatabase.MIGRATION_2_3)
        migrated.query("SELECT * FROM products WHERE barcode = '5449000000996'").use { cursor ->
            assertTrue("row survives the migration", cursor.moveToFirst())
            assertEquals("42.0", cursor.getString(cursor.getColumnIndexOrThrow("carbsPer100")))
            assertEquals("USER_VERIFIED", cursor.getString(cursor.getColumnIndexOrThrow("verificationStatus")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("favorite")))
            assertEquals("43.0", cursor.getString(cursor.getColumnIndexOrThrow("latestRemoteCarbs")))
        }
    }

    @Test
    fun migratingFromV2CreatesAnEmptyPortionUnitsTableReadyForUse() {
        helper.createDatabase(TEST_DB, 2).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, JustTheCarbsDatabase.MIGRATION_2_3)
        migrated.query("SELECT COUNT(*) FROM portion_units").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migratingFromV2LeavesNewProductColumnsNull() {
        val db = helper.createDatabase(TEST_DB, 2)
        db.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, brand, packageAmount,
             servingAmount, imageUrl, originalRemoteCarbs, latestRemoteCarbs, verifiedAt,
             remoteUpdatedAt, lastUsedAt, lastPortion, favorite)
            VALUES
            ('111', 'Plain Product', '10', 'PER_100_G', 'MANUAL', 'UNVERIFIED', NULL, NULL,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, JustTheCarbsDatabase.MIGRATION_2_3)
        migrated.query(
            "SELECT lastInputMode, lastSelectedPortionUnitId, lastCount FROM products WHERE barcode = '111'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue("lastInputMode starts null", cursor.isNull(0))
            assertTrue("lastSelectedPortionUnitId starts null", cursor.isNull(1))
            assertTrue("lastCount starts null", cursor.isNull(2))
        }
    }

    @Test
    fun aNewPortionUnitCanBeInsertedAndReadBackAfterMigration() {
        helper.createDatabase(TEST_DB, 2).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, JustTheCarbsDatabase.MIGRATION_2_3)

        migrated.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, brand, packageAmount,
             servingAmount, imageUrl, originalRemoteCarbs, latestRemoteCarbs, verifiedAt,
             remoteUpdatedAt, lastUsedAt, lastPortion, favorite, lastInputMode,
             lastSelectedPortionUnitId, lastCount)
            VALUES
            ('222', 'Sliced Bread', '42', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', NULL, NULL,
             NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 'PORTION_UNIT', 1, '2')
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, amountPerUnit, basis, dataSource,
             verificationStatus, verifiedAt, originalRemoteAmountPerUnit, latestRemoteAmountPerUnit,
             rawRemoteServingText, createdAt, updatedAt)
            VALUES
            (1, '222', 'SLICE', NULL, '36', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', NULL, '36',
             NULL, '1 slice (36 g)', 5000, 5000)
            """.trimIndent(),
        )

        migrated.query("SELECT amountPerUnit FROM portion_units WHERE productBarcode = '222'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("36", cursor.getString(0))
        }
        // Cascade-delete behaviour is intentionally NOT asserted here — see PortionUnitDaoTest,
        // which checks it against a normally-opened JustTheCarbsDatabase (the real production path)
        // rather than this migration-test connection, after that assertion here proved to depend
        // on MigrationTestHelper's own connection setup rather than the app's actual behaviour.
    }

    // A unique name per test instance (JUnit creates a fresh instance per @Test method): reusing a
    // fixed name let one test's already-migrated v3 file leak into the next test's "fresh" v2
    // database, since MigrationTestHelper.createDatabase() does not itself guarantee a clean file.
    private val TEST_DB = "migration-test-${java.util.UUID.randomUUID()}"
}
