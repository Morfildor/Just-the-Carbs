package app.justthecarbs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The on-device product store. Everything the user owns — verified values, manual products,
 * favourites, last portions — lives here and nowhere else (§34).
 *
 * ## Migration policy (§33)
 *
 * `exportSchema = true` writes `app/schemas/<version>.json`, and **those files are committed**:
 * without the previous schema on disk, a future migration cannot be written or tested.
 *
 * `fallbackToDestructiveMigration()` is deliberately absent and must stay absent. It would trade a
 * crash for silently wiping every carbohydrate value the user personally verified against a
 * package. Every version bump gets a real [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        ProductEntity::class,
        PortionUnitEntity::class,
        MealItemEntity::class,
        PortionUsageEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class JustTheCarbsDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun portionUnitDao(): PortionUnitDao
    abstract fun mealItemDao(): MealItemDao
    abstract fun portionUsageDao(): PortionUsageDao

    companion object {
        private const val NAME = "justthecarbs.db"

        /**
         * v1 → v2: records the latest remote carbohydrate value alongside the local one.
         *
         * A real migration rather than a destructive fallback, because installs of v1 already
         * exist and may hold values the user verified against a package. Nullable with no default:
         * "we have not asked the provider since the upgrade" is genuinely different from "the
         * provider agrees with the stored value", and the two must not be conflated.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE products ADD COLUMN latestRemoteCarbs TEXT")
            }
        }

        /**
         * v2 → v3: adds countable portions (brief §13). A new `portion_units` table, plus three
         * columns on `products` remembering which amount field/unit/count the user last used
         * (§11) — `lastPortion` already covers "last base-unit amount", so only the new mode/unit/
         * count facts are added.
         *
         * SQL matches exactly what Room generates from [PortionUnitEntity]'s annotations (verified
         * against the exported `3.json` schema), since Room validates the post-migration schema
         * against the compiled entities and refuses to open on a mismatch.
         */
        // Internal, not private: JustTheCarbsDatabaseMigrationTest exercises it directly with
        // MigrationTestHelper, which needs the same Migration instance the app itself registers.
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portion_units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `customLabel` TEXT,
                        `amountPerUnit` TEXT NOT NULL,
                        `basis` TEXT NOT NULL,
                        `dataSource` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `verifiedAt` INTEGER,
                        `originalRemoteAmountPerUnit` TEXT,
                        `latestRemoteAmountPerUnit` TEXT,
                        `rawRemoteServingText` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`productBarcode`) REFERENCES `products`(`barcode`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_portion_units_productBarcode` ON `portion_units` (`productBarcode`)",
                )
                // SQLite's ALTER TABLE has no "ADD COLUMN IF NOT EXISTS" — guarded by hand instead.
                // Defensive rather than purely theoretical: Room's own MigrationTestHelper was
                // observed re-invoking this migration against an already-migrated connection during
                // its post-migration validation pass, which made a non-idempotent ALTER fail with
                // "duplicate column name" purely as a testing artefact, not a real schema problem.
                if (!connection.hasColumn("products", "lastInputMode")) {
                    connection.execSQL("ALTER TABLE products ADD COLUMN lastInputMode TEXT")
                }
                if (!connection.hasColumn("products", "lastSelectedPortionUnitId")) {
                    connection.execSQL("ALTER TABLE products ADD COLUMN lastSelectedPortionUnitId INTEGER")
                }
                if (!connection.hasColumn("products", "lastCount")) {
                    connection.execSQL("ALTER TABLE products ADD COLUMN lastCount TEXT")
                }
            }
        }

        /**
         * v3 → v4: the temporary meal, usual portions, and the calculator's hero image.
         *
         * Purely additive — two new tables and one new nullable column. No existing column is
         * altered, dropped or retyped, so every v1/v2/v3 record (verified products, favourites,
         * recents, portion units, remembered input mode, remote-change metadata) survives by
         * construction rather than by careful copying.
         *
         * `current_meal_items` deliberately has **no foreign key to `products`**. A meal item is an
         * immutable calculation snapshot (§9): it must survive its product being reformulated,
         * re-verified, or deleted. A cascading FK would delete the item; a restricting FK would
         * block the product delete. Copying the few facts needed to re-display and re-total is the
         * only shape that satisfies "an existing meal item must NOT silently change".
         *
         * Same `PRAGMA table_info` guard as [MIGRATION_2_3] and for the same reason — Room's
         * MigrationTestHelper re-invokes migrations during validation, and a naive ALTER then fails
         * with "duplicate column" as a pure testing artefact.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                if (!connection.hasColumn("products", "largeImageUrl")) {
                    connection.execSQL("ALTER TABLE products ADD COLUMN largeImageUrl TEXT")
                }

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `current_meal_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT,
                        `displayName` TEXT NOT NULL,
                        `portionDescription` TEXT NOT NULL,
                        `resolvedAmount` TEXT NOT NULL,
                        `basis` TEXT NOT NULL,
                        `carbsPer100` TEXT NOT NULL,
                        `exactCarbs` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portion_usage` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT NOT NULL,
                        `inputMode` TEXT NOT NULL,
                        `portionUnitId` INTEGER,
                        `amount` TEXT NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_portion_usage_productBarcode_inputMode_portionUnitId_amount` " +
                        "ON `portion_usage` (`productBarcode`, `inputMode`, `portionUnitId`, `amount`)",
                )
            }
        }

        /** v4 → v5: optional OFF gallery metadata; all existing product/session data is retained. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                if (!connection.hasColumn("products", "galleryImagesJson")) {
                    connection.execSQL("ALTER TABLE products ADD COLUMN galleryImagesJson TEXT")
                }
            }
        }

        /**
         * v5 → v6: portion units carry a [app.justthecarbs.domain.PortionConversion] instead of a
         * mandatory weight, and meal items carry a kind instead of mandatory grams (spec §11).
         *
         * Both tables are **rebuilt and copied** rather than altered, because SQLite cannot drop a
         * NOT NULL constraint in place and both changes make previously-mandatory columns optional.
         * Row ids are preserved by the copy, so `portion_usage.portionUnitId` still resolves to the
         * same unit and nothing referencing a meal item by id breaks.
         *
         * Every pre-existing row is explicitly labelled — portion units as `'WEIGHT'`, meal items as
         * `'WEIGHT_BASED'` — rather than left NULL for a mapper to interpret. "Absent means legacy"
         * is the kind of implicit rule that quietly rots; the discriminant is always present and
         * always authoritative.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portion_units_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `customLabel` TEXT,
                        `conversionKind` TEXT NOT NULL,
                        `conversionValue` TEXT NOT NULL,
                        `conversionBasis` TEXT,
                        `dataSource` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `verifiedAt` INTEGER,
                        `originalRemoteConversionKind` TEXT,
                        `originalRemoteConversionValue` TEXT,
                        `originalRemoteConversionBasis` TEXT,
                        `latestRemoteConversionKind` TEXT,
                        `latestRemoteConversionValue` TEXT,
                        `latestRemoteConversionBasis` TEXT,
                        `rawRemoteServingText` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`productBarcode`) REFERENCES `products`(`barcode`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `portion_units_new`
                        (id, productBarcode, kind, customLabel, conversionKind, conversionValue,
                         conversionBasis, dataSource, verificationStatus, verifiedAt,
                         originalRemoteConversionKind, originalRemoteConversionValue,
                         originalRemoteConversionBasis, latestRemoteConversionKind,
                         latestRemoteConversionValue, latestRemoteConversionBasis,
                         rawRemoteServingText, createdAt, updatedAt)
                    SELECT id, productBarcode, kind, customLabel, 'WEIGHT', amountPerUnit,
                           basis, dataSource, verificationStatus, verifiedAt,
                           CASE WHEN originalRemoteAmountPerUnit IS NOT NULL THEN 'WEIGHT' END,
                           originalRemoteAmountPerUnit,
                           CASE WHEN originalRemoteAmountPerUnit IS NOT NULL THEN basis END,
                           CASE WHEN latestRemoteAmountPerUnit IS NOT NULL THEN 'WEIGHT' END,
                           latestRemoteAmountPerUnit,
                           CASE WHEN latestRemoteAmountPerUnit IS NOT NULL THEN basis END,
                           rawRemoteServingText, createdAt, updatedAt
                    FROM `portion_units`
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `portion_units`")
                connection.execSQL("ALTER TABLE `portion_units_new` RENAME TO `portion_units`")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_portion_units_productBarcode` ON `portion_units` (`productBarcode`)",
                )

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `current_meal_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT,
                        `displayName` TEXT NOT NULL,
                        `portionDescription` TEXT NOT NULL,
                        `itemKind` TEXT NOT NULL,
                        `resolvedAmount` TEXT,
                        `basis` TEXT,
                        `carbsPer100` TEXT,
                        `count` TEXT,
                        `carbsPerUnit` TEXT,
                        `exactCarbs` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `current_meal_items_new`
                        (id, productBarcode, displayName, portionDescription, itemKind,
                         resolvedAmount, basis, carbsPer100, count, carbsPerUnit, exactCarbs, addedAt)
                    SELECT id, productBarcode, displayName, portionDescription, 'WEIGHT_BASED',
                           resolvedAmount, basis, carbsPer100, NULL, NULL, exactCarbs, addedAt
                    FROM `current_meal_items`
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `current_meal_items`")
                connection.execSQL("ALTER TABLE `current_meal_items_new` RENAME TO `current_meal_items`")
            }
        }

        /**
         * v6 → v7: `portion_usage.portionUnitId` becomes `NOT NULL`, storing
         * [PortionUsageEntity.NO_UNIT_SENTINEL] (`0`) for a GRAMS-mode row instead of SQL `NULL`
         * (P0 §5).
         *
         * SQLite's ordinary unique-index semantics treat every `NULL` as distinct from every other
         * `NULL` — including from itself — so the pre-existing
         * `UNIQUE (productBarcode, inputMode, portionUnitId, amount)` index never actually
         * constrained a grams-mode row (`portionUnitId` always `NULL` there): any number of
         * "duplicate" grams-mode variants for the same product and amount could coexist despite the
         * index's own name. Only countable-portion rows (`portionUnitId` a real id) were ever
         * protected. `0` is a safe, permanent sentinel: `PortionUnit.id` is
         * `@PrimaryKey(autoGenerate = true)`, and SQLite `AUTOINCREMENT` never assigns `0` to a real
         * saved row.
         *
         * Rebuild-and-copy rather than an in-place `ALTER`, because SQLite cannot add a `NOT NULL`
         * constraint to an existing nullable column. The copy also **merges** any pre-existing
         * grams-mode duplicates it finds — rows this exact bug could have produced, since the old
         * `NULL`-holed index never stopped them accumulating — by summing their `usageCount` and
         * keeping the most recent `lastUsedAt`, grouped by the natural key the new unique index will
         * enforce going forward. A naive `INSERT ... SELECT` that only rewrote `NULL` to `0` would
         * itself violate that same new unique index the moment it reached a second duplicate row,
         * failing the migration outright on exactly the data it exists to repair. One surviving
         * row's `id` is kept (arbitrarily, via `MIN(id)`) so any portion-usage id referenced
         * elsewhere still resolves to *a* row for that variant, consistent with `MIGRATION_5_6`'s
         * "row ids are preserved" note — this is the one migration in this file where more than one
         * source row can fold into a single destination row, because merging duplicates is the
         * point.
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portion_usage_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT NOT NULL,
                        `inputMode` TEXT NOT NULL,
                        `portionUnitId` INTEGER NOT NULL,
                        `amount` TEXT NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `portion_usage_new`
                        (id, productBarcode, inputMode, portionUnitId, amount, usageCount, lastUsedAt)
                    SELECT MIN(id), productBarcode, inputMode, COALESCE(portionUnitId, 0), amount,
                           SUM(usageCount), MAX(lastUsedAt)
                    FROM `portion_usage`
                    GROUP BY productBarcode, inputMode, COALESCE(portionUnitId, 0), amount
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `portion_usage`")
                connection.execSQL("ALTER TABLE `portion_usage_new` RENAME TO `portion_usage`")
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_portion_usage_productBarcode_inputMode_portionUnitId_amount` " +
                        "ON `portion_usage` (`productBarcode`, `inputMode`, `portionUnitId`, `amount`)",
                )
            }
        }

        /** Shared by the guarded migrations above. SQLite has no "ADD COLUMN IF NOT EXISTS". */
        private fun SQLiteConnection.hasColumn(table: String, column: String): Boolean {
            val statement = prepare("PRAGMA table_info(`$table`)")
            return statement.use {
                var found = false
                while (it.step()) {
                    if (it.getText(1) == column) {
                        found = true
                        break
                    }
                }
                found
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE products ADD COLUMN originalRemoteBasis TEXT")
                connection.execSQL("ALTER TABLE products ADD COLUMN latestRemoteBasis TEXT")
                // A verified row may have changed basis. Leave historical pairs unknown.
            }
        }

        fun build(context: Context): JustTheCarbsDatabase =
            Room.databaseBuilder(context.applicationContext, JustTheCarbsDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()
    }
}
