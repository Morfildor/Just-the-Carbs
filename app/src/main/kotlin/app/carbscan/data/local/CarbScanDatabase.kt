package app.carbscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = true,
)
abstract class CarbScanDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    companion object {
        private const val NAME = "carbscan.db"

        fun build(context: Context): CarbScanDatabase =
            Room.databaseBuilder(context.applicationContext, CarbScanDatabase::class.java, NAME)
                .build()
    }
}
