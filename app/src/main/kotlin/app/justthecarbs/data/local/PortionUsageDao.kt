package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

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

    /**
     * Atomically insert a first use, or increment an existing variant's count (P0 §5).
     *
     * The previous pattern — `findVariant` to decide, then a separate `insert` or `update` — has a
     * window between the read and the write that a concurrent call for the *same* variant could in
     * principle land in: both read the same `usageCount`, both compute `+1` from it, and the second
     * write clobbers the first instead of compounding it (a lost update). **Measured, not
     * assumed:** driving that old pattern with 20 concurrent coroutines against a real
     * `Room.inMemoryDatabaseBuilder` connection (`PortionUsageDaoTest`, including with an added
     * artificial delay between the read and the write) never actually lost an update in this app —
     * Room dispatches every suspend DAO call through its own internal single-threaded write
     * executor before it reaches SQLite, which serializes two same-process calls regardless of
     * which coroutine dispatcher scheduled them. The hazard the old pattern carries is real
     * (nothing in the DAO/repository layer *requires* that serialization, it happens to be true of
     * today's `Room.databaseBuilder` defaults, and would not hold across multiple database
     * instances or processes sharing the file), but it is a latent architectural risk closed
     * pre-emptively here, not a bug this pass reproduced on a device.
     *
     * The `NOT NULL portionUnitId` half of the fix is the one with a **measured** defect behind
     * it: before that change, SQLite's own uniqueness treats every `NULL` in an indexed column as
     * distinct from every other `NULL`, so the `UNIQUE` index never actually constrained a
     * grams-mode row (`portionUnitId` always `NULL` there) at all — regardless of concurrency,
     * regardless of Room's serialization. That gap is closed by `MIGRATION_6_7` and this query
     * together, independent of the argument above.
     *
     * A single `INSERT ... ON CONFLICT ... DO UPDATE` is still the correct design regardless: it
     * moves "one row per variant" from an invariant the *calling code* has to uphold to one SQLite
     * itself enforces as a single atomic statement, which is strictly more robust than relying on
     * an implementation detail of Room's current executor — and it is what the *database*, not this
     * DAO's calling convention, now guarantees `usageCount = usageCount + 1` compounds correctly.
     */
    @Query(
        """
        INSERT INTO portion_usage (productBarcode, inputMode, portionUnitId, amount, usageCount, lastUsedAt)
        VALUES (:barcode, :inputMode, :unitId, :amount, 1, :now)
        ON CONFLICT(productBarcode, inputMode, portionUnitId, amount)
        DO UPDATE SET usageCount = usageCount + 1, lastUsedAt = :now
        """,
    )
    suspend fun recordUse(barcode: String, inputMode: String, unitId: Long, amount: String, now: Long)

    /**
     * Plain upsert by identity — [PortionUsageStore.save]'s implementation, used where a caller
     * already has the exact row it wants written (an explicit correction, a test) rather than
     * "increment whatever variant this describes". Not used by the increment path; see [recordUse].
     */
    @Upsert
    suspend fun upsert(usage: PortionUsageEntity): Long

    @Delete
    suspend fun delete(usage: PortionUsageEntity)

    /** Portion history is per-product; deleting a product should not leave its usage behind. */
    @Query("DELETE FROM portion_usage WHERE productBarcode = :barcode")
    suspend fun deleteForProduct(barcode: String)
}
