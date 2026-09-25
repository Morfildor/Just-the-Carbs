package app.justthecarbs.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** Why a lookup could not produce a value. Each maps to a distinct, actionable message (§36). */
enum class LookupError {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    SERVER,
    /** The response arrived but could not be parsed. */
    MALFORMED,
}

/**
 * A countable unit a remote source suggested, not yet a stored [PortionUnit] (countable-portions
 * brief §7). The repository decides whether to create or update a [PortionUnit] from this — it
 * carries no id, provenance timestamps, or verification state, because those are storage concerns.
 */
data class PortionUnitCandidate(
    val kind: PortionUnitKind,
    val conversion: PortionConversion,
    val rawServingText: String,
)

/**
 * The outcome of asking any source for a product.
 *
 * [NotFound] and [Unusable] are deliberately different: "we have no record of this barcode" and
 * "we have a record but its carbohydrate value cannot be trusted" lead the user to the same two
 * escape hatches, but conflating them would let a bad value masquerade as a missing one (§13, §26).
 */
sealed interface ProductFetchResult {
    data class Found(val product: Product, val portionUnitCandidate: PortionUnitCandidate? = null) :
        ProductFetchResult
    data object NotFound : ProductFetchResult
    data class Unusable(
        val barcode: String,
        val reason: UnusableReason = UnusableReason.NO_CARB_VALUE,
    ) : ProductFetchResult
    data class Failed(val error: LookupError) : ProductFetchResult
}

/**
 * Why a record exists but cannot be turned into a [Product].
 *
 * The two are kept apart because they are different facts about the package in the user's hand, and
 * telling them apart is the difference between usable advice and a confusing dead end. Saying "no
 * carbohydrate value" to someone holding a bottle that plainly prints one sends them looking for a
 * problem that is not there.
 */
enum class UnusableReason {
    /** The carbohydrate figure is missing, negative, non-finite, or beyond a physical ceiling. */
    NO_CARB_VALUE,

    /**
     * A carbohydrate figure exists, but nothing established whether it is per 100 **g** or per
     * 100 **ml** (§17, see [PackageBasisResolver]).
     *
     * Refused rather than defaulted. The figure itself is not in doubt, so the recovery is cheap —
     * the user states the unit once, in manual entry, where it is a visible chip rather than an
     * assumption. That is the same treatment an OCR reading with an unplaced column already gets.
     */
    UNKNOWN_BASIS,
}

/**
 * One place the app can ask about a barcode (brief §11).
 *
 * The UI never talks to Open Food Facts directly. Adding GS1, a national product database or a
 * retailer feed later means adding an implementation, not editing screens.
 */
interface ProductDataSource {
    suspend fun fetch(barcode: String): ProductFetchResult
}

/**
 * The on-device store. Reads like any other source, and additionally accepts writes, because it is
 * the only source the user can author (§23, §27).
 */
interface LocalProductDataSource : ProductDataSource {
    suspend fun save(product: Product)

    /**
     * Stores [product] only if no row exists for its barcode, and says whether it did.
     *
     * For a lookup's first save (2026-09-25 review): the network call can outlast the screen that
     * started it, and the user may have saved the same barcode by hand in the meantime. That row is
     * theirs and must not be replaced by the online record they were working around. The default is
     * check-then-save, good enough for in-memory fakes; the Room store overrides it with one
     * conflict-ignoring insert, so nothing can land between the check and the write.
     */
    suspend fun saveIfAbsent(product: Product): Boolean {
        if (fetch(product.barcode) is ProductFetchResult.Found) return false
        save(product)
        return true
    }

    /** Favourites first, then most recently used (§21, §22). */
    fun observeRecents(limit: Int): Flow<List<Product>>

    /**
     * Forgets that one product was ever used, and returns what was forgotten (§43, one barcode).
     *
     * Clears the five remembered-use columns and that barcode's `portion_usage` rows — the same
     * definition of "usage" the global *Clear recent history* action uses — while preserving the
     * product itself, its carbohydrate value and basis, provenance, verification, its portion-unit
     * definitions and the favourite flag.
     *
     * Declared here, on the local store, rather than assembled in [ProductRepository] from a product
     * save plus a `PortionUsageStore` delete: the two writes must land together or not at all. A
     * crash between them leaves the product looking forgotten while its *Usual* shortcuts survive to
     * reappear on the next visit, which is the precise defect the global action was fixed for. The
     * store that owns both tables is the only layer that can make them one transaction.
     *
     * Returns null for an unknown barcode: nothing was erased, so there is nothing to undo.
     */
    suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot?

    /**
     * Puts one [forgetRecentUse] back, atomically and only if the product still exists.
     *
     * Writes only the usage fields, so an edit made during the Undo window survives it.
     */
    suspend fun restoreRecentUse(snapshot: RecentUseSnapshot)
}
