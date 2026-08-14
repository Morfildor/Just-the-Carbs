package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

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

    /**
     * The existing row for one exact variant, or null.
     *
     * `portionUnitId IS :unitId` rather than `= :unitId`: in SQL, `NULL = NULL` is NULL, not true,
     * so a grams-mode variant (unitId null) would never match itself with `=` and every use would
     * insert a duplicate row instead of incrementing.
     */
    @Query(
        """
        SELECT * FROM portion_usage
        WHERE productBarcode = :barcode
          AND inputMode = :inputMode
          AND portionUnitId IS :unitId
          AND amount = :amount
        LIMIT 1
        """,
    )
    suspend fun findVariant(
        barcode: String,
        inputMode: String,
        unitId: Long?,
        amount: String,
    ): PortionUsageEntity?

    @Insert
    suspend fun insert(usage: PortionUsageEntity): Long

    @Update
    suspend fun update(usage: PortionUsageEntity)

    @Delete
    suspend fun delete(usage: PortionUsageEntity)

    /** Portion history is per-product; deleting a product should not leave its usage behind. */
    @Query("DELETE FROM portion_usage WHERE productBarcode = :barcode")
    suspend fun deleteForProduct(barcode: String)
}
