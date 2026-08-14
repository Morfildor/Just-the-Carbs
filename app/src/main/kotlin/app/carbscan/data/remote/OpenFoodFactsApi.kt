package app.carbscan.data.remote

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

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        val REQUESTED_FIELDS = listOf(
            "code",
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
            "serving_size",
        ).joinToString(",")
    }
}
