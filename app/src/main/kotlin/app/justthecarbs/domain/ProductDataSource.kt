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

    /** Favourites first, then most recently used (§21, §22). */
    fun observeRecents(limit: Int): Flow<List<Product>>
}
