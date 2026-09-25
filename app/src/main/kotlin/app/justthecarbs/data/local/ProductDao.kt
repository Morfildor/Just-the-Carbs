package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One product's usage exactly as it was stored, read inside the transaction that erased it.
 *
 * Storage-shaped on purpose: epoch millis and `TEXT` decimals, the columns' own types, so the
 * snapshot restores byte-for-byte what was read without a decimal round-trip in between.
 * [app.justthecarbs.data.local.RoomProductDataSource] maps it to the domain
 * [app.justthecarbs.domain.RecentUseSnapshot] at the same seam that already maps everything else.
 */
data class RecentUseSnapshotRow(
    val barcode: String,
    val lastUsedAt: Long?,
    val lastPortion: String?,
    val lastInputMode: String?,
    val lastSelectedPortionUnitId: Long?,
    val lastCount: String?,
    val portionUsage: List<PortionUsageEntity>,
)

/** The columns of one stored product that a search row needs — see [ProductDao.observeSearchable]. */
data class SearchableProductRow(
    val barcode: String,
    val name: String,
    val brand: String?,
    val carbsPer100: String,
    val basis: String,
    val imageUrl: String?,
    val packageAmount: String?,
)

/**
 * Product reads and writes, plus the two destructive Settings actions (§43).
 *
 * The clear actions issue statements against `portion_usage` and `portion_units` as well as
 * `products`, which is a deliberate exception to "one DAO per table". Both are single atomic user
 * actions whose whole correctness claim is that nothing survives them; splitting them across DAOs
 * would mean either two transactions (so a crash between them leaves the user told their history was
 * cleared when half of it was not) or a `withTransaction` block whose participants nothing forces
 * anyone to keep in step. One transaction, one place, one list of tables.
 */
@Dao
abstract class ProductDao {

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    abstract suspend fun findByBarcode(barcode: String): ProductEntity?

    @Upsert
    abstract suspend fun upsert(product: ProductEntity)

    /** Inserts only when the barcode has no row; returns -1 when one already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(product: ProductEntity): Long

    /**
     * Recents for the home screen (§21), with favourites floated to the top (§22) rather than given
     * a tab of their own.
     *
     * `favorite DESC` works because SQLite stores the flag as 0/1. Favourites are included even
     * before they have been used, so starring a product never makes it disappear from home.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE lastUsedAt IS NOT NULL OR favorite = 1
        ORDER BY favorite DESC, lastUsedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun observeRecents(limit: Int): Flow<List<ProductEntity>>

    /**
     * Every stored product, as the few columns a search row shows, for matching typed names on the
     * device (Home's search and the search screen).
     *
     * Every row, not only Recents: a product entered by hand has no barcode to scan again, and once
     * it falls out of the 25 Recents this was the only way left to find it. The order is the one a
     * match should keep — favourites, then most recently used, then everything else by name.
     */
    @Query(
        """
        SELECT barcode, name, brand, carbsPer100, basis, imageUrl, packageAmount FROM products
        ORDER BY favorite DESC, lastUsedAt IS NULL, lastUsedAt DESC, name COLLATE NOCASE
        """,
    )
    abstract fun observeSearchable(): Flow<List<SearchableProductRow>>

    /**
     * Every column on `products` that records *that the user ate the thing*, cleared for every row.
     *
     * Two things here were wrong before the 2026-08-26 release pass, and both made the action a
     * weaker promise than its label:
     *
     * - It cleared only `lastUsedAt` and `lastPortion`, leaving `lastInputMode`,
     *   `lastSelectedPortionUnitId` and `lastCount` behind. Those three *are* a remembered portion —
     *   "2 slices" survived in full, so a cleared product could still pre-fill the count the user
     *   last ate.
     * - It carried `WHERE favorite = 0`, so a favourite kept its entire usage history. Starring a
     *   product is a statement about wanting it near the top of Home, not consent to keep a record
     *   of eating it, and "clear recent history" that silently exempts rows is the kind of
     *   half-promise a privacy control must not make.
     *
     * What it must never touch is the product itself: name, carbohydrate value, verification status,
     * provenance and the favourite flag all survive, because erasing a value the user verified
     * against a package as a side effect of clearing history would destroy exactly the data §23
     * calls the reliability feature.
     */
    @Query(
        """
        UPDATE products SET
            lastUsedAt = NULL,
            lastPortion = NULL,
            lastInputMode = NULL,
            lastSelectedPortionUnitId = NULL,
            lastCount = NULL
        """,
    )
    abstract suspend fun clearProductUsageColumns()

    /**
     * The `portion_usage` aggregates behind *Usual* (§13).
     *
     * These are the other half of "history", and clearing recents used to leave them untouched — so
     * the usual-portion shortcuts a user had built up survived an action that told them their
     * history was gone, and reappeared on the next scan of the same barcode.
     */
    @Query("DELETE FROM portion_usage")
    abstract suspend fun deleteAllPortionUsage()

    /** *Clear recent history* (§43): usage facts only, atomically, for every product. */
    @Transaction
    open suspend fun clearRecentHistory() {
        clearProductUsageColumns()
        deleteAllPortionUsage()
    }

    // ---- one product's usage (Remove from Recent) ----------------------------------------------

    /**
     * The same five columns [clearProductUsageColumns] clears, for one barcode.
     *
     * Written as its own statement rather than by adding a `WHERE` to that one: the global action's
     * whole claim is that it exempts no row, and a shared statement with an optional predicate is
     * one careless call site away from a global clear that quietly skipped something.
     */
    @Query(
        """
        UPDATE products SET
            lastUsedAt = NULL,
            lastPortion = NULL,
            lastInputMode = NULL,
            lastSelectedPortionUnitId = NULL,
            lastCount = NULL
        WHERE barcode = :barcode
        """,
    )
    abstract suspend fun clearProductUsageColumnsFor(barcode: String)

    @Query("DELETE FROM portion_usage WHERE productBarcode = :barcode")
    abstract suspend fun deletePortionUsageFor(barcode: String)

    @Query("SELECT * FROM portion_usage WHERE productBarcode = :barcode")
    abstract suspend fun findPortionUsageFor(barcode: String): List<PortionUsageEntity>

    /**
     * Restores one usage variant. `IGNORE`, and the direction matters.
     *
     * `portion_usage` is uniquely indexed on (barcode, mode, unit, amount). If the user re-used the
     * product during the Undo window, a fresh row for the same variant already exists and is the
     * *newer* truth; ignoring the restore keeps it. `REPLACE` would overwrite a real count the user
     * has just earned with a stale one, which is the one outcome an Undo must never produce.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPortionUsage(usage: PortionUsageEntity)

    @Query(
        """
        UPDATE products SET
            lastUsedAt = :lastUsedAt,
            lastPortion = :lastPortion,
            lastInputMode = :lastInputMode,
            lastSelectedPortionUnitId = :lastSelectedPortionUnitId,
            lastCount = :lastCount
        WHERE barcode = :barcode
        """,
    )
    abstract suspend fun restoreProductUsageColumns(
        barcode: String,
        lastUsedAt: Long?,
        lastPortion: String?,
        lastInputMode: String?,
        lastSelectedPortionUnitId: Long?,
        lastCount: String?,
    )

    /**
     * *Remove from Recent* for one product (§43 semantics, one barcode).
     *
     * Reads the snapshot and clears in **one** transaction, so the returned snapshot describes
     * exactly what was erased — a read outside the transaction could be overtaken by a concurrent
     * use and offer the user an Undo that restores a state that never existed.
     *
     * Everything the global action preserves is preserved here: the product row, its name,
     * carbohydrate value and basis, provenance, verification, its `portion_units` definitions and
     * the favourite flag. A favourite therefore stays in Favourites and simply stops remembering how
     * it was last eaten; an ordinary product leaves Recents, because `observeRecents` selects on
     * `lastUsedAt IS NOT NULL OR favorite = 1`.
     *
     * Returns null for an unknown barcode — nothing was erased, so there is nothing to offer Undo
     * for, and the caller must not show one.
     */
    @Transaction
    open suspend fun forgetRecentUse(barcode: String): RecentUseSnapshotRow? {
        val product = findByBarcode(barcode) ?: return null
        val usage = findPortionUsageFor(barcode)
        clearProductUsageColumnsFor(barcode)
        deletePortionUsageFor(barcode)
        return RecentUseSnapshotRow(
            barcode = product.barcode,
            lastUsedAt = product.lastUsedAt,
            lastPortion = product.lastPortion,
            lastInputMode = product.lastInputMode,
            lastSelectedPortionUnitId = product.lastSelectedPortionUnitId,
            lastCount = product.lastCount,
            portionUsage = usage,
        )
    }

    /**
     * Puts one [forgetRecentUse] back, atomically.
     *
     * **Only the five usage columns are written.** The product row is updated in place rather than
     * re-inserted from the snapshot, so an edit made during the Undo window — a value verified
     * against the package, a favourite toggled — survives the Undo instead of being silently
     * reverted to the moment of removal.
     *
     * **Nothing is resurrected.** If the product was deleted while the Snackbar was up, the `UPDATE`
     * matches no row and the usage rows are deliberately not written either: re-inserting them would
     * leave `portion_usage` orphaned against a barcode with no product, which is exactly the defect
     * that let a deleted product's portions reappear on the next scan (see [deleteAllProducts]).
     *
     * The rows are inserted with a fresh surrogate `id`. `portion_usage.id` is an
     * `autoGenerate` ROWID that nothing outside this table references, and SQLite may reuse a freed
     * ROWID — so forcing the old id back risks colliding with a row inserted for a *different*
     * product during the Undo window, and losing the restore to the conflict strategy. The variant
     * identity (barcode, mode, unit, amount) and both aggregate fields round-trip exactly, which is
     * what *Usual* actually reads.
     */
    @Transaction
    open suspend fun restoreRecentUse(snapshot: RecentUseSnapshotRow) {
        if (findByBarcode(snapshot.barcode) == null) return
        restoreProductUsageColumns(
            barcode = snapshot.barcode,
            lastUsedAt = snapshot.lastUsedAt,
            lastPortion = snapshot.lastPortion,
            lastInputMode = snapshot.lastInputMode,
            lastSelectedPortionUnitId = snapshot.lastSelectedPortionUnitId,
            lastCount = snapshot.lastCount,
        )
        snapshot.portionUsage.forEach { insertPortionUsage(it.copy(id = 0)) }
    }

    @Query("DELETE FROM portion_units")
    abstract suspend fun deleteAllPortionUnits()

    @Query("DELETE FROM products")
    abstract suspend fun deleteProductRows()

    /**
     * *Clear locally saved products* (§43). Confirmation is the caller's job.
     *
     * `portion_units` cascades from `products` and would go anyway; `portion_usage` does **not** —
     * it carries a bare `productBarcode` string with no foreign key, precisely so that per-product
     * usage is not coupled to the product row's lifetime. The consequence was that deleting every
     * product left every usage aggregate behind, and re-scanning the same barcode resurrected the
     * usual portions of a product the user had deleted. The units are deleted explicitly too rather
     * than left to the cascade, so this method does not depend on `PRAGMA foreign_keys` being on for
     * whichever connection happens to be open.
     *
     * `current_meal_items` is deliberately **not** cleared. A meal item is an immutable calculation
     * snapshot that is designed to outlive its product (see `MIGRATION_3_4` — it has no foreign key
     * for exactly this reason), and the meal is the plate the user is assembling right now, not
     * saved product data. Deleting it here would discard in-progress work the action never mentions.
     */
    @Transaction
    open suspend fun deleteAllProducts() {
        deleteAllPortionUsage()
        deleteAllPortionUnits()
        deleteProductRows()
    }
}
