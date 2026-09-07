package app.justthecarbs.data.remote

import app.justthecarbs.domain.BarcodeValidator
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.PackageBasisResolver
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.SearchProviderLog
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Search-a-licious as a [ProductSearchSource] — the app's primary text-search provider.
 *
 * ## What it is allowed to decide
 *
 * Nothing that reaches a calculation. A [ProductSearchHit] is not a `Product` and cannot become one:
 * it has no provenance, no verification status and no id, and selecting one runs an ordinary barcode
 * lookup through [OpenFoodFactsDataSource.fetch]. So a schema change, an outage or an outright wrong
 * result here costs the user a bad *list to choose from* — never a wrong carbohydrate value, a
 * corrupted stored product, or anything at all in the scanner or OCR paths.
 *
 * ## The basis, and the one honest regression
 *
 * This index does not carry `product_quantity_unit` — measured 0 of 140 hits across seven queries on
 * 2026-08-28, and requesting it by name returns nothing. That field is
 * [PackageBasisResolver]'s primary evidence, so on this path the basis is resolved from the printed
 * `quantity` text alone and resolves less often: on `pasta`, 3 of 20 hits show a number where the
 * legacy endpoint showed 18.
 *
 * **This is a display regression, not a nutrition one, and the resolver is not weakened to hide it.**
 * A hit with an unresolved basis shows its name, brand, package text and photo and no figure — the
 * existing rule (§13), unchanged. Printing a number under an assumed unit is the exact defect
 * [PackageBasisResolver] exists to prevent, and it would be a worse trade than a quieter card.
 * The number the user actually doses from comes from the canonical lookup after they tap.
 *
 * ## Failure classification
 *
 * Every failure is mapped to the app's existing small [LookupError] vocabulary — no provider-specific
 * error type leaves this class. Which of those failures may spend a fallback request is decided by
 * [app.justthecarbs.domain.FallbackProductSearch], not here: this class reports what happened, and
 * the chain decides what that is worth.
 */
class SearchALiciousDataSource(
    private val api: SearchALiciousApi,
    /**
     * Notified when a response arrives with matches that all proved unusable.
     *
     * That state is invisible from the screen — the user sees the fallback's results, or an ordinary
     * error — so without a diagnostic it would take a schema change to notice a schema change. Debug
     * builds only; carries counts, never query text. See [SearchProviderLog].
     */
    private val log: SearchProviderLog = SearchProviderLog.None,
) : ProductSearchSource {

    override suspend fun search(terms: String): ProductSearchResult {
        val query = terms.trim()
        if (query.isEmpty()) return ProductSearchResult.NoMatches

        return try {
            val response = api.search(
                SearchALiciousRequest(
                    // Escaped here, at the transport boundary, and nowhere else. The user's text is
                    // untouched everywhere above this line — the field shows what they typed and the
                    // legacy fallback receives it verbatim. See [SearchALiciousQuery].
                    q = SearchALiciousQuery.escape(query),
                ),
            )
            when {
                response.code() == HTTP_TOO_MANY_REQUESTS -> ProductSearchResult.Failed(
                    LookupError.RATE_LIMITED,
                    retryAfterMs = RetryAfterHeader.parseMs(
                        response.headers()[HEADER_RETRY_AFTER],
                        System.currentTimeMillis(),
                    ),
                )
                !response.isSuccessful -> ProductSearchResult.Failed(LookupError.SERVER)
                else -> toSearchResult(response.body())
            }
        } catch (_: UnknownHostException) {
            ProductSearchResult.Failed(LookupError.OFFLINE)
        } catch (_: SocketTimeoutException) {
            ProductSearchResult.Failed(LookupError.TIMEOUT)
        } catch (_: SerializationException) {
            ProductSearchResult.Failed(LookupError.MALFORMED)
        } catch (_: IOException) {
            ProductSearchResult.Failed(LookupError.OFFLINE)
        }
    }

    /**
     * Classifies a 2xx body into the three genuinely different states it can represent.
     *
     * ## "Nothing matched" and "nothing was usable" are not the same statement
     *
     * This distinction is the whole point of the function, and getting it wrong is silent. Because
     * [app.justthecarbs.domain.FallbackProductSearch] deliberately does **not** fall back on
     * [ProductSearchResult.NoMatches] — a zero-result answer is an answer — reporting an unusable
     * response as `NoMatches` would suppress the legacy provider precisely when it was most needed,
     * and tell the user their product does not exist when the app had simply failed to read the
     * reply. The user has no way to tell those apart from the screen.
     *
     * The three cases:
     *
     * 1. **The service found nothing** — `hits` is empty *and* it does not claim otherwise.
     *    [ProductSearchResult.NoMatches]. A real answer; no fallback, by design.
     * 2. **At least one hit mapped** — [ProductSearchResult.Found]. One malformed record among good
     *    ones is an ordinary state of a crowd-sourced database and must never discard the good ones.
     * 3. **The service says it has matches, but none survived mapping** — [LookupError.MALFORMED],
     *    which is fallback-eligible. Includes `count > 0` with an empty `hits` array, and any
     *    non-empty `hits` array from which nothing usable was extracted.
     *
     * `count` is the tie-breaker, and it is used only in the direction that is safe. A positive
     * `count` with nothing usable is *evidence of a broken payload*, so it escalates to a failure.
     * A missing or zero `count` never *downgrades* a non-empty-but-unusable `hits` array back to
     * `NoMatches` — the array itself is already the claim that matches exist.
     *
     * A body with **no `hits` key at all** is malformed for the same reason: a shape this app does
     * not recognise — a proxy error page, a redesigned envelope — where the legacy provider may well
     * answer.
     *
     * A response that reports itself as `timed_out` is a partial answer, and presenting a truncated
     * list as the complete result set would be a wrong answer rather than a missing one.
     */
    private fun toSearchResult(body: SearchALiciousResponse?): ProductSearchResult {
        val rawHits = body?.hits ?: return ProductSearchResult.Failed(LookupError.MALFORMED)
        if (body.timedOut == true) return ProductSearchResult.Failed(LookupError.TIMEOUT)

        val hits = rawHits.mapNotNull { it.toHit() }
            // Deduplicated by barcode, never by display name: two genuinely different products
            // routinely share a name ("Gouda"), and collapsing those would hide one of them. The
            // barcode is the product's identity, so `distinctBy` on it is the only safe key.
            //
            // distinctBy keeps the FIRST occurrence, which preserves the service's relevance
            // ordering — the ranking is the main thing this provider is being adopted for.
            .distinctBy { it.barcode }

        if (hits.isNotEmpty()) return ProductSearchResult.Found(hits)

        // Nothing usable came back. Whether that is an answer or a broken payload is decided by
        // whether the service claimed to have anything — never by the mapped list being empty,
        // which is true in both cases and is what made the original bug invisible.
        val claimsMatches = rawHits.isNotEmpty() || (body.count ?: 0) > 0
        return if (claimsMatches) {
            log.onPrimaryHitsUnusable(rawHits.size, body.count)
            ProductSearchResult.Failed(LookupError.MALFORMED)
        } else {
            ProductSearchResult.NoMatches
        }
    }

    /**
     * Maps one indexed product to a hit, or drops it.
     *
     * Dropped when it carries no barcode (it could not be selected — selection *is* a barcode
     * lookup) or no name (a blank row in a disambiguation list is worse than a shorter list). Every
     * other absence is tolerated: no brand, no quantity, no image and no carbohydrate value are all
     * ordinary states of a crowd-sourced record, and a user may still recognise the package.
     */
    private fun SearchALiciousHit.toHit(): ProductSearchHit? {
        // Validated and normalised (P1 §10), not merely non-blank — see
        // OpenFoodFactsDataSource.toHit's identical fix for the full rationale. `code` here is
        // equally untrusted remote text, and this hit ends up in the same `product/{barcode}`
        // navigation route.
        val barcode = BarcodeValidator.normalize(code.orEmpty()) ?: return null
        val displayName = listOfNotNull(productNameNl, productName)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        val resolution = PackageBasisResolver.resolve(
            // This index carries no structured unit — see the class KDoc. Passing null is the honest
            // input, and it routes the resolver to its free-text rule rather than to a guess.
            structuredUnit = null,
            freeTextQuantity = quantity,
        )
        val basis = (resolution as? PackageBasisResolver.Resolution.Resolved)?.basis

        return ProductSearchHit(
            barcode = barcode,
            name = displayName,
            brand = brands?.takeIf { it.isNotBlank() }?.substringBefore(',')?.trim(),
            packageQuantity = quantity?.takeIf { it.isNotBlank() }?.trim(),
            // Validated exactly as on the legacy path: an out-of-range figure shows as "no value"
            // rather than as a number, so a card can never display something the calculator would
            // refuse (§13).
            carbsPer100 = basis?.let {
                NutritionValueValidator.validateCarbsPer100(
                    raw = nutriments?.carbohydrates100g,
                    basis = it,
                )
            },
            basis = basis,
            imageUrl = imageFrontUrl?.takeIf { it.isNotBlank() }
                ?: imageFrontSmallUrl?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HEADER_RETRY_AFTER = "Retry-After"
    }
}
