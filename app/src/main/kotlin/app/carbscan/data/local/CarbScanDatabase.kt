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
    entities = [ProductEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CarbScanDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

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

        fun build(context: Context): CarbScanDatabase =
            Room.databaseBuilder(context.applicationContext, CarbScanDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
