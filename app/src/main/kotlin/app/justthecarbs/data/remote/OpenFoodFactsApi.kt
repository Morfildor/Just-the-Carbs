package app.justthecarbs.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    /**
     * Single-product read (§12).
     *
     * v3, not v2 (countable-portions brief §14, verified 2026-08-14): v2 is documented as
     * deprecated-but-supported, v3 is current, and the fields this app reads are unchanged between
     * the two, so migrating now — while the DTO is already being extended for `serving_size` — is
     * low-risk. Only the response envelope differs (`status` int vs string, a `result` wrapper), and
     * this app never reads that envelope; see [OffProductResponse].
     *
     * `fields` is always sent. Requesting only what the app uses keeps responses small on a mobile
     * connection and is what OFF asks clients to do — a full product document is very large and
     * most of it is irrelevant to a carbohydrate calculation.
     */
    @GET("api/v3/product/{barcode}")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = REQUESTED_FIELDS,
    ): Response<OffProductResponse>

    /**
     * Free-text product search — the fallback when a barcode does not resolve (spec §9).
     *
     * `cgi/search.pl`, not `api/v2/search`: verified live on 2026-08-14, v2's search endpoint is a
     * facet/filter API that does not accept `search_terms` and answers with an HTML error page. The
     * CGI endpoint returns code, name, brands, quantity, the 400 px image and nutriments in a
     * single call, so a result card can show enough for the user to recognise their package before
     * relying on the number.
     *
     * [pageSize] is deliberately small. This is a disambiguation list the user reads, not a catalogue
     * to browse, and OFF's read budget is 15 requests/min/IP.
     */
    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") terms: String,
        @Query("fields") fields: String = REQUESTED_FIELDS,
        @Query("page_size") pageSize: Int = SEARCH_PAGE_SIZE,
        @Query("json") json: Int = 1,
    ): Response<OffSearchResponse>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        /** Enough to disambiguate a product, few enough to read without scrolling far (§15). */
        const val SEARCH_PAGE_SIZE = 20

        val REQUESTED_FIELDS = listOf(
            "code",
            "lang",
            "product_name",
            "product_name_nl",
            "brands",
            "quantity",
            "nutriments",
            "image_front_small_url",
            // The 400 px display variant, for the calculator's hero image (§5). Adding one URL
            // string to the response is negligible next to the identification value of an image
            // the user can actually recognise their package in.
            "image_front_url",
            "selected_images",
            "serving_size",
        ).joinToString(",")
    }
}
