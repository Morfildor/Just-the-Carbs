package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction

/**
 * Portion-usage aggregates, the input to *Usual* (brief §13, §22).
 *
 * Reads are per-product only. There is deliberately no "all usage across all products" query: the
 * feature never needs one, and not having it means the app cannot accidentally grow a screen that
 * lists everything the user has eaten.
 */
@Dao
interface PortionUsageDao {

    @Query("SELECT * FROM portion_usage WHERE productBarcode = :barcode")
    suspend fun findByBarcode(barcode: String): List<PortionUsageEntity>

    /** The existing row for one exact variant, or null. Read-only — never part of a write decision. */
    @Query(
        """
        SELECT * FROM portion_usage
        WHERE productBarcode = :barcode
          AND inputMode = :inputMode
          AND portionUnitId = :unitId
          AND amount = :amount
        LIMIT 1
        """,
    )
    suspend fun findVariant(
        barcode: String,
        inputMode: String,
        unitId: Long,
        amount: String,
    ): PortionUsageEntity?

    /** The transaction preserves atomic increments on platform SQLite back to API 26. */
    @Transaction
    suspend fun recordUse(barcode: String, inputMode: String, unitId: Long, amount: String, now: Long) {
        val inserted = insertVariant(PortionUsageEntity(
            productBarcode = barcode, inputMode = inputMode, portionUnitId = unitId,
            amount = amount, usageCount = 1, lastUsedAt = now,
        ))
        if (inserted == -1L) incrementVariant(barcode, inputMode, unitId, amount, now)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVariant(usage: PortionUsageEntity): Long

    @Query("""
        UPDATE portion_usage SET usageCount = usageCount + 1, lastUsedAt = :now
        WHERE productBarcode = :barcode AND inputMode = :inputMode
          AND portionUnitId = :unitId AND amount = :amount
    """)
    suspend fun incrementVariant(barcode: String, inputMode: String, unitId: Long, amount: String, now: Long)

    /**
     * Plain upsert by identity — [PortionUsageStore.save]'s implementation, used where a caller
     * already has the exact row it wants written (an explicit correction, a test) rather than
     * "increment whatever variant this describes". Not used by the increment path; see [recordUse].
     */
    @Upsert
    suspend fun upsert(usage: PortionUsageEntity): Long

    @Delete
    suspend fun delete(usage: PortionUsageEntity)

}
