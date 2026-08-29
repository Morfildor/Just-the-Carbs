package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * One candidate from a free-text search (spec §9).
 *
 * Deliberately **not** a [Product]. A search hit is something the user is being asked to recognise,
 * not something the app has decided to use: it carries no provenance, no verification status and no
 * id, and it becomes a Product only when the user picks it and the normal barcode lookup runs. That
 * separation is what makes "no fuzzy match is ever auto-selected" structural rather than a rule
 * someone has to remember.
 *
 * [carbsPer100] is nullable because OFF records frequently lack it. A hit with no carbohydrate value
 * is still worth showing — the user may recognise the package and can then verify it from the label
 * — but the card must say so rather than imply a number it does not have.
 */
data class ProductSearchHit(
    val barcode: String,
    val name: String,
    val brand: String?,
    /** Free text as printed, e.g. "390 gram". Shown to help tell 390 g from 600 g on the shelf. */
    val packageQuantity: String?,
    val carbsPer100: BigDecimal?,
    /**
     * Grams or millilitres, or **null** when the record established neither (see
     * [PackageBasisResolver]).
     *
     * A null basis forces [carbsPer100] to null as well, at the one place hits are built: a figure
     * whose denominator is unknown is not a figure the user can act on, and showing it beside a
     * package size would read as "per 100 g" to anyone who has seen the rest of the app.
     */
    val basis: NutritionBasis?,
    val imageUrl: String?,
)

/** The outcome of a search. Mirrors [ProductFetchResult]'s shape so failures stay actionable. */
sealed interface ProductSearchResult {
    data class Found(val hits: List<ProductSearchHit>) : ProductSearchResult

    /** The query ran and matched nothing. Distinct from a failure — retrying will not help. */
    data object NoMatches : ProductSearchResult

    /**
     * The search could not be answered.
     *
     * [retryAfterMs] carries the server's own `Retry-After` when it sent one with a 429, so the
     * client can wait exactly as long as it was asked to instead of guessing. Null means either
     * "not a rate limit" or "rate limited without a stated duration" — the two are distinguished by
     * [error], and the governor applies its fallback backoff for the latter.
     *
     * Only search carries this. The product-read path has its own budget and its own error type,
     * and widening [LookupError] itself would put a search-shaped field on every lookup failure in
     * the app.
     */
    data class Failed(
        val error: LookupError,
        val retryAfterMs: Long? = null,
    ) : ProductSearchResult
}

/**
 * Free-text product search (spec §9).
 *
 * A separate interface from [ProductDataSource] rather than another method on it: searching is a
 * fallback capability, and a local cache or a future GS1 source can implement one without the
 * other. Keeping them apart means no implementation is forced to stub a method it cannot honour.
 */
interface ProductSearchSource {
    suspend fun search(terms: String): ProductSearchResult
}
