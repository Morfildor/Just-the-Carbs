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

    // ---- v5 -> v6: PortionConversion and MealItemKind (spec §11) -------------------------------

    @Test
    fun migratingFromV5PreservesPortionUnitsAsWeightConversions() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('333', 'Sliced Bread', '48.2', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        // An unverified remote unit, a user-verified one whose remote figure has since moved, and a
        // manual one — the three provenance/verification combinations that must all survive.
        db.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, amountPerUnit, basis, dataSource,
             verificationStatus, verifiedAt, originalRemoteAmountPerUnit, latestRemoteAmountPerUnit,
             rawRemoteServingText, createdAt, updatedAt)
            VALUES
            (1, '333', 'SLICE', NULL, '35', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', NULL,
             '35', NULL, '1 slice (35 g)', 5000, 5000),
            (2, '333', 'PIECE', NULL, '40', 'PER_100_G', 'OPEN_FOOD_FACTS', 'USER_VERIFIED', 9000,
             '38', '42', '1 piece (38 g)', 6000, 7000),
            (3, '333', 'CUSTOM', 'heel', '25', 'PER_100_G', 'MANUAL', 'USER_VERIFIED', 9500,
             NULL, NULL, NULL, 8000, 8000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.query(
            "SELECT conversionKind, conversionValue, conversionBasis, latestRemoteConversionKind, " +
                "latestRemoteConversionValue, verificationStatus, customLabel FROM portion_units ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEIGHT", cursor.getString(0))
            assertEquals("35", cursor.getString(1))
            assertEquals("PER_100_G", cursor.getString(2))
            assertTrue("no latest remote figure was recorded", cursor.isNull(3))

            assertTrue(cursor.moveToNext())
            assertEquals("WEIGHT", cursor.getString(0))
            assertEquals("40", cursor.getString(1))
            assertEquals("WEIGHT", cursor.getString(3))
            assertEquals("42", cursor.getString(4))
            assertEquals("the user's verification survives", "USER_VERIFIED", cursor.getString(5))

            assertTrue(cursor.moveToNext())
            assertEquals("25", cursor.getString(1))
            assertEquals("heel", cursor.getString(6))
        }
    }

    @Test
    fun migratingFromV5PreservesPortionUsageForeignKeysByKeepingIds() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('444', 'Bread', '48.2', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, amountPerUnit, basis, dataSource,
             verificationStatus, verifiedAt, originalRemoteAmountPerUnit, latestRemoteAmountPerUnit,
             rawRemoteServingText, createdAt, updatedAt)
            VALUES (42, '444', 'SLICE', NULL, '35', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED',
                    NULL, NULL, NULL, NULL, 5000, 5000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO portion_usage
            (id, productBarcode, inputMode, portionUnitId, amount, usageCount, lastUsedAt)
            VALUES (1, '444', 'PORTION_UNIT', 42, '2', 3, 9000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        // The rebuild must not renumber: a usage row pointing at unit 42 must still find unit 42.
        // This is the assertion that would catch a copy written as a plain INSERT without ids.
        migrated.query(
            "SELECT u.id FROM portion_units u JOIN portion_usage p ON p.portionUnitId = u.id",
        ).use { cursor ->
            assertTrue("the usage row still resolves to its unit", cursor.moveToFirst())
            assertEquals(42, cursor.getInt(0))
        }
    }

    @Test
    fun migratingFromV5LabelsExistingMealItemsWeightBased() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO current_meal_items
            (id, productBarcode, displayName, portionDescription, resolvedAmount, basis,
             carbsPer100, exactCarbs, addedAt)
            VALUES (1, '555', 'Bread', '72 g', '72', 'PER_100_G', '48.2', '34.704', 9000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.query(
            "SELECT itemKind, resolvedAmount, basis, carbsPer100, count, carbsPerUnit, exactCarbs " +
                "FROM current_meal_items WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEIGHT_BASED", cursor.getString(0))
            assertEquals("72", cursor.getString(1))
            assertEquals("PER_100_G", cursor.getString(2))
            assertEquals("48.2", cursor.getString(3))
            assertTrue("a legacy row has no count", cursor.isNull(4))
            assertTrue("a legacy row has no per-unit carbs", cursor.isNull(5))
            assertEquals("34.704", cursor.getString(6))
        }
    }

    @Test
    fun aDirectCarbMealItemCanBeStoredAfterMigration() {
        helper.createDatabase(TEST_DB, 5).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        // The whole point of the rebuild: these three columns must now accept NULL.
        migrated.execSQL(
            """
            INSERT INTO current_meal_items
            (id, productBarcode, displayName, portionDescription, itemKind, resolvedAmount, basis,
             carbsPer100, count, carbsPerUnit, exactCarbs, addedAt)
            VALUES (2, '666', 'Crackers', '4 slices', 'DIRECT_CARBS', NULL, NULL, NULL,
                    '4', '14.2', '56.8', 9100)
            """.trimIndent(),
        )

        migrated.query(
            "SELECT resolvedAmount, basis, carbsPer100, count, carbsPerUnit FROM current_meal_items WHERE id = 2",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("no fake grams", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertEquals("4", cursor.getString(3))
            assertEquals("14.2", cursor.getString(4))
        }
    }

    @Test
    fun aDirectCarbPortionUnitCanBeStoredAfterMigration() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('777', 'Crackers', '62', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, conversionKind, conversionValue, conversionBasis,
             dataSource, verificationStatus, verifiedAt, originalRemoteConversionKind,
             originalRemoteConversionValue, originalRemoteConversionBasis, latestRemoteConversionKind,
             latestRemoteConversionValue, latestRemoteConversionBasis, rawRemoteServingText,
             createdAt, updatedAt)
            VALUES (1, '777', 'SLICE', NULL, 'DIRECT_CARBS', '14.2', NULL, 'OPEN_FOOD_FACTS',
                    'UNVERIFIED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2 slices', 5000, 5000)
            """.trimIndent(),
        )

        migrated.query("SELECT conversionKind, conversionValue, conversionBasis FROM portion_units WHERE id = 1")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DIRECT_CARBS", cursor.getString(0))
                assertEquals("14.2", cursor.getString(1))
                assertTrue("carbs per item has no g/ml basis", cursor.isNull(2))
            }
    }

    // A unique name per test instance (JUnit creates a fresh instance per @Test method): reusing a
    // fixed name let one test's already-migrated v3 file leak into the next test's "fresh" v2
    // database, since MigrationTestHelper.createDatabase() does not itself guarantee a clean file.
    private val TEST_DB = "migration-test-${java.util.UUID.randomUUID()}"
}
