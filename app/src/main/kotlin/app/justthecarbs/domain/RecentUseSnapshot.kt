package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Everything "the user has used this product" means, for one product, captured at the instant it
 * was forgotten so that a single Undo can put it back.
 *
 * The five product columns plus the whole of that barcode's `portion_usage` — the same definition
 * of "usage" that the global *Clear recent history* action uses (see
 * [app.justthecarbs.data.local.ProductDao.clearRecentHistory]), scoped to one barcode. Keeping the
 * two definitions identical is deliberate: a per-product forget that cleared less than the global
 * action would be the same half-promise the 2026-08-26 release pass fixed, and one that cleared
 * more would destroy product facts the global action is careful to preserve.
 *
 * **Not a history.** Exactly one of these is ever held, in memory, by the screen that offered the
 * Undo, and it is dropped the moment the Snackbar goes. Nothing persists it, and there is no
 * accessor anywhere that returns more than one — the app remains structurally incapable of listing
 * what the user has eaten (§22), which is the boundary this feature had to be built inside rather
 * than around.
 *
 * `portionUsage` carries the rows as they were, including their `usageCount` and `lastUsedAt`, so
 * restoring re-establishes the *Usual* shortcuts exactly as the user had built them up. The row
 * `id`s are deliberately **not** part of the restore contract — see
 * [app.justthecarbs.data.local.ProductDao.restoreRecentUse] for why a surrogate key is reassigned
 * rather than forced back.
 */
data class RecentUseSnapshot(
    val barcode: String,
    val lastUsedAt: Instant?,
    val lastPortion: BigDecimal?,
    val lastInputMode: InputMode?,
    val lastSelectedPortionUnitId: Long?,
    val lastCount: BigDecimal?,
    val portionUsage: List<PortionUsage>,
) {
    /**
     * Whether forgetting this product actually erased anything.
     *
     * False for a favourite that has never been used — it is in Recents because it is starred, not
     * because it was eaten, so there is nothing to forget and nothing to offer an Undo for. It is
     * also false on a second removal of the same product, which is what stops a repeated action
     * replacing a good snapshot with an empty one and leaving Undo with nothing to restore.
     */
    val erasedAnything: Boolean
        get() = lastUsedAt != null ||
            lastPortion != null ||
            lastInputMode != null ||
            lastSelectedPortionUnitId != null ||
            lastCount != null ||
            portionUsage.isNotEmpty()
}
