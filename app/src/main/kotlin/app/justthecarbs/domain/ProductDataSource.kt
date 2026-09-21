package app.justthecarbs.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

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

    /** Favourites first, then most recently used (§21, §22). */
    fun observeRecents(limit: Int): Flow<List<Product>>

    /**
     * Sets or clears one product's personal on-device name ([Product.localAlias]).
     *
     * Declared as its own narrow operation rather than left to [save], because the two differ in
     * what they are allowed to touch. [save] writes a whole [Product], which is correct when the
     * caller *is* the authority on that product; a rename is not — the screen offering it holds a
     * snapshot that may be minutes old, and writing it back would roll back any refresh, favourite
     * or recorded use that landed in between. This writes the one column and reads none.
     *
     * [alias] is already trimmed and non-blank, or null to remove. A barcode with no stored product
     * is a no-op: an alias is metadata *about* a saved product, never a reason to create one.
     *
     * The default **throws**, deliberately, and is not a silent no-op. It exists so the many test
     * fakes that have no interest in aliases need not restate an operation they never reach — the
     * same accommodation [forgetRecentUse] and [restoreRecentUse] already get, and by the same
     * mechanism those fakes use for them. A default that quietly discarded the write would be far
     * worse than a compile error: a test asserting a rename had been stored would pass while
     * nothing was stored, which is the vacuous-green outcome this codebase keeps having to dig out.
     * Failing loudly means any fake that *is* reached by an alias write says so on the first run.
     */
    suspend fun setLocalAlias(barcode: String, alias: String?): Unit =
        error("this data source does not implement setLocalAlias")

    /**
     * Sets or clears one product's favourite flag ([Product.favorite]).
     *
     * Narrow for [setLocalAlias]'s reason, applied to the other independently owned piece of user
     * metadata. The previous implementation was `save(existing.copy(favorite = …))`, a whole-row
     * write from a snapshot the caller held — so starring a product from a screen that had been
     * open a while rolled back a rename, a recorded use or a refreshed figure that had landed in
     * between. The star and the name answer different questions asked at different moments, and
     * neither may carry the other backwards.
     *
     * An unknown barcode is a no-op: a favourite is metadata *about* a saved product, never a
     * reason to create one.
     *
     * The default throws for [setLocalAlias]'s reason: a silent no-op would let a test assert that
     * a star had been stored while nothing was.
     */
    suspend fun setFavorite(barcode: String, favorite: Boolean): Unit =
        error("this data source does not implement setFavorite")

    /**
     * Writes a product's facts, preserving the columns the device owns, in one transaction.
     *
     * For the three operations that legitimately replace a coherent set of product facts —
     * verifying a figure against the package, accepting a newer online figure, resetting to the
     * online one. Those columns move together and must not be seen half-applied, so this is a
     * whole-row write by nature.
     *
     * What it must not do is carry [Product.localAlias] or [Product.favorite] backwards. Each of
     * those is owned by a different action at a different moment and is no part of what a figure
     * change replaces. Preserving them **inside the transaction** rather than re-reading just
     * beforehand is what makes that unreachable rather than merely unlikely: a read and a write in
     * Kotlin are two statements with a gap between them, and the gap is exactly where the lost
     * update lived.
     *
     * The remembered-portion columns are deliberately not preserved: they are cleared on purpose
     * when the basis changes, and a portion in grams is meaningless once a product is measured per
     * 100 ml.
     *
     * The default throws for [setLocalAlias]'s reason.
     */
    suspend fun saveProductFacts(product: Product): Unit =
        error("this data source does not implement saveProductFacts")

    /**
     * Records that the product was used, writing only the five remembered-use columns.
     *
     * Those five are a coherent group — together they say "this is how it was last eaten" — so they
     * move together, in one statement. That is what makes a single write right here and wrong for
     * the alias or the star, which are independent facts.
     *
     * The coalescing rules belong to the implementation rather than the caller, deliberately:
     * applying them in Kotlin means reading the row first, and that read is the stale snapshot this
     * whole change exists to remove.
     *
     * - [lastPortion] null **preserves** the stored value rather than erasing it. It is strictly a
     *   resolved mass or volume in the product's own basis unit, and a direct-carb portion resolves
     *   none; writing the count there would make "4 slices" reappear as "4 g".
     * - [lastInputMode] null likewise preserves.
     * - [lastSelectedPortionUnitId] and [lastCount] are **cleared** when the mode is
     *   [InputMode.GRAMS] — a positive statement that the user has stopped counting items — and
     *   otherwise preserved when null.
     *
     * An unknown barcode is a no-op, for the reason above.
     */
    suspend fun recordUsageColumns(
        barcode: String,
        lastPortion: BigDecimal?,
        lastUsedAt: Instant,
        lastInputMode: InputMode?,
        lastSelectedPortionUnitId: Long?,
        lastCount: BigDecimal?,
    ): Unit = error("this data source does not implement recordUsageColumns")

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
