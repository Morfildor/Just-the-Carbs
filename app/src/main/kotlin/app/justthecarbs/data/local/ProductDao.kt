package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
