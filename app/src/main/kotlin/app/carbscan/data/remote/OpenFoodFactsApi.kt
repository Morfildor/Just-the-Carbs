package app.carbscan.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    /**
     * Single-product read (§12).
     *
     * `fields` is always sent. Requesting only what the app uses keeps responses small on a mobile
     * connection and is what OFF asks clients to do — a full product document is very large and
     * most of it is irrelevant to a carbohydrate calculation.
     */
    @GET("api/v2/product/{barcode}")
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
        ).joinToString(",")
    }
}
