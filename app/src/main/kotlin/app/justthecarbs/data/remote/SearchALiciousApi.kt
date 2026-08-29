package app.justthecarbs.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Open Food Facts' dedicated full-text search service (Search-a-licious).
 *
 * A separate interface from [OpenFoodFactsApi] because it is a separate host with a separate
 * response envelope and a separate budget. Sharing one interface would mean one base URL.
 *
 * ## Why this exists alongside the legacy endpoint
 *
 * Measured on 2026-08-28, at a polite 7 s spacing well inside its own documented budget, the legacy
 * `cgi/search.pl` answered **503 on five of seven** representative queries. This service answered
 * twelve back-to-back requests with no spacing at all, every one HTTP 200 in 136–202 ms. That gap —
 * not a schema preference — is the reason search moved.
 *
 * The legacy endpoint remains implemented and reachable as a fallback; see
 * [app.justthecarbs.domain.FallbackProductSearch].
 */
interface SearchALiciousApi {

    /**
     * Free-text product search, over the documented **POST** form.
     *
     * ## Why POST rather than GET
     *
     * Both verbs exist on this endpoint with identical `q` semantics (verified against the service's
     * own OpenAPI document, 2026-08-28), so this is purely a transport choice — and it is the
     * privacy-preserving one. On GET the user's search text is the URL, which is the part of a
     * request that gets written to proxy logs, server access logs and TLS-terminating middlebox
     * records as a matter of course. Moving it into the body does not encrypt anything that was not
     * already encrypted in transit, but it keeps what someone typed out of the one field that is
     * routinely retained in plain text by machines nobody here operates.
     *
     * That matters more for this app than for most: a search term is a food someone is about to eat.
     */
    @POST("search")
    suspend fun search(
        @Body request: SearchALiciousRequest,
    ): Response<SearchALiciousResponse>

    companion object {
        const val BASE_URL = "https://search.openfoodfacts.org/"

        /** Matches the legacy page size, so neither provider produces a longer list than the other. */
        const val SEARCH_PAGE_SIZE = 20

        /**
         * Languages whose localized names and text are searched and returned.
         *
         * Dutch first, English second — the same asymmetry the rest of the app already has: the UI
         * is English-only (owner decision 10) while the *data* the owner scans is legitimately
         * Dutch. This is input recognition, not localization, and does not reopen that decision.
         *
         * Load-bearing rather than a nicety, for two measured reasons: it is what makes
         * `product_name_nl` appear at all (absent from every hit without it), preserving the app's
         * existing preference for the localized name, and it widens recall substantially on Dutch
         * terms — `hagelslag` returned 26 matches without it and 449 with it.
         *
         * A **list**, not a comma-joined string: the POST schema types `langs` as an array where the
         * GET query parameter took a string. Same values, different container.
         */
        val SEARCH_LANGS = listOf("nl", "en")

        /**
         * Exactly what a result card renders, and nothing else.
         *
         * The full hit carries ~40 keys including `ecoscore_data`, `nutriscore_data`,
         * `ingredients_tags` and an `images` map — measured at ~13 KB per hit, none of which a
         * result card reads. Requesting only the nine fields the card uses is what keeps live search
         * light enough to run on every settled keystroke.
         *
         * `product_quantity_unit` is deliberately **not** requested: it is not in this index, and
         * asking for it returns nothing rather than failing, so listing it would read as though the
         * app had a basis signal it does not have. See [SearchALiciousHit].
         *
         * `lang` was requested until 2026-08-28 and read nowhere. It was dropped on measurement, not
         * on principle: 240 bytes per response (12 bytes × 20 hits, 2.3% of the payload) across
         * `chocolate`, `pasta`, `hagelslag`, `milk` and `nutella`, with the **mapped products
         * identical** for every one of the five. Note the app's language handling is unaffected —
         * that lives in [SEARCH_LANGS] on the request, which is what makes `product_name_nl` arrive;
         * the per-hit `lang` echo was never part of it.
         *
         * A **list** for the same reason as [SEARCH_LANGS] — the POST schema types `fields` as an
         * array.
         */
        val SEARCH_FIELDS = listOf(
            "code",
            "product_name",
            "product_name_nl",
            "brands",
            "quantity",
            "nutriments",
            "image_front_small_url",
            "image_front_url",
        )
    }
}

/**
 * The POST `/search` request body, matching the service's documented `SearchParameters` schema.
 *
 * Only the four parameters the app actually uses are modelled. The schema also accepts `page`,
 * `sort_by`, `facets`, `charts`, `sort_params` and `index_id`; every one of them has a server-side
 * default that is already what this app wants, and sending a field merely to restate its default is
 * how a request body silently acquires behaviour nobody chose. In particular `sort_by` is left
 * unsent so results keep the service's own relevance ranking — the thing the migration was for.
 *
 * ## `@EncodeDefault` is load-bearing, not decoration
 *
 * kotlinx.serialization omits a property equal to its default unless told otherwise, and the shared
 * [NetworkModule] `Json` does not set `encodeDefaults`. Without these annotations every request
 * would serialise to `{"q":"…"}` alone and the **server's** defaults would silently apply:
 * `page_size` 10 instead of 20, `langs` `["en"]` instead of `["nl","en"]` — which is what makes
 * `product_name_nl` appear at all, so Dutch recall would have collapsed — and no `fields` filter,
 * pulling ~13 KB per hit of `ecoscore_data`/`nutriscore_data`/`images` the app never reads.
 *
 * That failure is entirely invisible: every request still succeeds and still returns products. It
 * was caught by `the request asks for the measured fields and languages`, which asserts the body
 * rather than the outcome. Do not remove these annotations, and do not "simplify" them by switching
 * the shared `Json` to `encodeDefaults = true` — that would change how every other DTO in the app
 * serialises, to fix one request body.
 */
@Serializable
data class SearchALiciousRequest(
    /**
     * The search text.
     *
     * **Already escaped** by [SearchALiciousQuery.escape] before it reaches here. This field is
     * interpreted as a Lucene query by the service, so raw user text is not safe to place in it —
     * see that function for the measurements.
     */
    val q: String,
    @EncodeDefault
    val langs: List<String> = SearchALiciousApi.SEARCH_LANGS,
    @EncodeDefault
    @SerialName("page_size")
    val pageSize: Int = SearchALiciousApi.SEARCH_PAGE_SIZE,
    @EncodeDefault
    val fields: List<String> = SearchALiciousApi.SEARCH_FIELDS,
)
