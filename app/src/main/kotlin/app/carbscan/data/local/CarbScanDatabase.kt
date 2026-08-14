package app.carbscan.data.local

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
    entities = [ProductEntity::class, PortionUnitEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CarbScanDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun portionUnitDao(): PortionUnitDao

    companion object {
        private const val NAME = "carbscan.db"

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
        // Internal, not private: CarbScanDatabaseMigrationTest exercises it directly with
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
        }

        fun build(context: Context): CarbScanDatabase =
            Room.databaseBuilder(context.applicationContext, CarbScanDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
