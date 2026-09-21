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

/**
 * One saved product in the narrow shape saved-product search reads.
 *
 * A projection rather than a `SELECT *`: search scans every saved row on every keystroke, and the
 * full entity carries a JSON gallery blob, six remote-variant columns and the whole remembered
 * portion — none of which matching or ranking looks at. Room maps the seven named columns and reads
 * nothing else.
 *
 * Storage-shaped, like [RecentUseSnapshotRow]: `TEXT` decimals and epoch millis, converted at the
 * `RoomSavedProductSearchSource` seam that already converts everything else.
 */
data class SavedProductSearchRow(
    val barcode: String,
    val name: String,
    /** The user's personal name for this product, or null. Matched and rendered like [name]. */
    val localAlias: String?,
    val brand: String?,
    val carbsPer100: String,
    val basis: String,
    val imageUrl: String?,
    val favorite: Boolean,
    val lastUsedAt: Long?,
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

    /**
     * Writes a product's facts while preserving the columns the **device** owns, atomically.
     *
     * ## Why this exists rather than a plain [upsert]
     *
     * Three operations legitimately replace a coherent set of product facts: verifying a figure
     * against the package, accepting a newer online figure, and resetting to the online one. Each is
     * a whole-row write by nature — the value, the basis, the verification status and the audit
     * columns move together and must not be seen half-applied.
     *
     * But each is also built from a snapshot read moments earlier, and between that read and this
     * write the repository suspends (clearing usage rows for a basis change). A rename or a
     * favourite landing in that window was rolled back by the write — not overwritten with a newer
     * value, but reverted to one that was already stale when it was read.
     *
     * Re-reading in Kotlin just before calling [upsert] narrows that window without closing it: the
     * gap between the read and the write is still two statements. Here the read and the write are
     * **one transaction**, so there is no window at all. That is the difference between making the
     * race unlikely and making it unreachable, and this codebase has recorded before that the first
     * is not good enough.
     *
     * ## What counts as device-owned
     *
     * [ProductEntity.localAlias] and [ProductEntity.favorite]: the name this user gave the product
     * and whether they starred it. Neither is a claim about what the product *is*, so neither may be
     * carried backwards by a change to the figure. The remembered-portion columns are deliberately
     * **not** in this list — they are cleared on purpose when the basis changes, because a portion
     * in grams is meaningless once a product is measured per 100 ml, and preserving them here would
     * silently undo that.
     *
     * A product that does not exist yet is simply inserted: there is nothing to preserve.
     */
    @Transaction
    open suspend fun saveProductFacts(product: ProductEntity) {
        val current = findByBarcode(product.barcode)
        upsert(
            if (current == null) {
                product
            } else {
                product.copy(localAlias = current.localAlias, favorite = current.favorite)
            },
        )
    }

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
     * Every saved product, for local-first search.
     *
     * **Deliberately not [observeRecents].** That query answers "what belongs on Home", so it
     * carries `WHERE lastUsedAt IS NOT NULL OR favorite = 1` and a `LIMIT` — which between them hide
     * exactly the products this feature exists to find: one saved but never since used, or one
     * pushed past the recents limit. A product the user has stored is searchable whether or not it
     * is recent, starred, or recently enough used to be on a list of twelve.
     *
     * No `WHERE`, no `ORDER BY` and no `LIKE`: matching and ranking are
     * [app.justthecarbs.domain.SavedProductSearch]'s, in Kotlin, using the same matcher and the same
     * folding the remote page is ranked with. SQLite has no equivalent of that folding — `LIKE` is
     * case-insensitive for ASCII only, so it would never match `Pınar` against `Pinar` — and pushing
     * the predicate down would mean two different definitions of "matches".
     *
     * `suspend`, not a `Flow`: this is read once per search, not observed. A flow here would
     * re-emit the whole table on every unrelated product write.
     */
    @Query(
        """
        SELECT barcode, name, localAlias, brand, carbsPer100, basis, imageUrl, favorite, lastUsedAt
        FROM products
        """,
    )
    abstract suspend fun findAllForSearch(): List<SavedProductSearchRow>

    // ---- local alias (Rename on this device) ----------------------------------------------------

    /**
     * Sets or clears one product's personal name, touching **only** that column.
     *
     * A targeted `UPDATE` rather than an upsert of a `Product` the caller is holding, and the
     * difference is the whole correctness claim. The screen that offers the rename has had a product
     * snapshot in memory for as long as the user has been on it; during that time a background
     * refresh may have landed a newer figure, a favourite may have been toggled, a use recorded.
     * Writing the whole row back from that snapshot would silently roll every one of those changes
     * back to whatever they were when the screen opened — a lost update whose symptom is a
     * *favourite* disappearing because someone renamed something.
     *
     * Only rows that exist are affected: renaming a barcode that is not saved matches nothing and
     * writes nothing, rather than creating a phantom product that has a nickname and no data.
     */
    @Query("UPDATE products SET localAlias = :alias WHERE barcode = :barcode")
    abstract suspend fun setLocalAlias(barcode: String, alias: String?)

    /**
     * Sets or clears one product's favourite flag, writing that column and reading none.
     *
     * The same rule [setLocalAlias] states, applied to the other independently owned piece of user
     * metadata. `setFavorite` was `local.save(existing.copy(favorite = …))` — a whole-row write
     * from a snapshot the caller was holding — so starring a product from a screen that had been
     * open for a while rolled back anything that had landed in the meantime: a rename, a recorded
     * use, a refreshed figure.
     *
     * The star and the name are owned by different actions and belong to different moments; neither
     * is evidence about the other, and neither may carry the other backwards.
     *
     * An unknown barcode matches nothing and writes nothing. A favourite is metadata *about* a
     * saved product, never a reason to create one.
     */
    @Query("UPDATE products SET favorite = :favorite WHERE barcode = :barcode")
    abstract suspend fun setFavorite(barcode: String, favorite: Boolean)

    /**
     * Writes the five remembered-use columns as one statement.
     *
     * These five are a **coherent group** rather than five independent facts: together they say
     * "this is how the product was last eaten", and a reader that saw a new `lastCount` beside an
     * old `lastInputMode` would pre-fill a portion the user never entered. So they move together —
     * which is what makes one statement right here and wrong for, say, the alias.
     *
     * The coalescing rules are expressed in SQL rather than in Kotlin, and that is the point: doing
     * them in Kotlin means reading the row first, and *that read* is the snapshot a concurrent
     * rename or favourite used to be rolled back from.
     *
     * - `lastPortion` advances only on a real resolved amount. A direct-carb portion ("4 slices ×
     *   14.2 g carbs") resolves no weight at all, so null preserves the previous value instead of
     *   erasing it — writing the *count* there would make "4 slices" reappear as "4 g".
     * - `lastInputMode` likewise only advances when the caller states one.
     * - The unit and the count are **cleared** in grams mode and otherwise coalesced. Grams mode is
     *   a positive statement that the user is no longer counting items, so leaving a stale unit
     *   behind would re-offer a countable portion they have just stopped using.
     *
     * [gramsMode] is passed separately rather than compared against [lastInputMode] inside the
     * statement because "no mode stated" and "grams" are different answers, and only the second one
     * clears.
     */
    @Query(
        """
        UPDATE products SET
            lastPortion = COALESCE(:lastPortion, lastPortion),
            lastUsedAt = :lastUsedAt,
            lastInputMode = COALESCE(:lastInputMode, lastInputMode),
            lastSelectedPortionUnitId = CASE
                WHEN :gramsMode THEN NULL
                ELSE COALESCE(:lastSelectedPortionUnitId, lastSelectedPortionUnitId)
            END,
            lastCount = CASE
                WHEN :gramsMode THEN NULL
                ELSE COALESCE(:lastCount, lastCount)
            END
        WHERE barcode = :barcode
        """,
    )
    abstract suspend fun recordUsageColumns(
        barcode: String,
        lastPortion: String?,
        lastUsedAt: Long,
        lastInputMode: String?,
        lastSelectedPortionUnitId: Long?,
        lastCount: String?,
        gramsMode: Boolean,
    )

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
