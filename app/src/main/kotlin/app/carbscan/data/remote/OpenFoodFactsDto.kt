package app.carbscan.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open Food Facts read API v2 response.
 *
 * Every field is nullable with a default. OFF is a crowd-sourced database: any field can be
 * missing, and a strict schema would turn an incomplete record into a parse failure — which the app
 * would then report as a broken response rather than as the missing data it actually is (§13, §36).
 *
 * The `status` field is deliberately not modelled. It has changed representation between API
 * versions (integer in v0, string in v2), and "is there a product object with a usable
 * carbohydrate value?" is a question the app can answer for itself without depending on that.
 */
@Serializable
data class OffProductResponse(
    val code: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    /** Localized name, preferred where present (§12). */
    @SerialName("product_name_nl") val productNameNl: String? = null,
    val brands: String? = null,
    /**
     * Free text, e.g. "500 ml". Parsed by `PackageQuantityParser` for both the package size and
     * the g/ml basis. Kept as a string because OFF's numeric quantity fields are inconsistently
     * typed (sometimes a number, sometimes a string), which crashes strict deserialization.
     */
    val quantity: String? = null,
    val nutriments: OffNutriments? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
)

@Serializable
data class OffNutriments(
    /**
     * TOTAL carbohydrate per 100 g/ml. This is the only nutrient the app reads.
     *
     * It is never substituted with sugars, fibre, net carbs, energy or protein (§12) — those are
     * different quantities, and quietly standing in for a missing total would produce a confident
     * wrong number at the exact moment the user needs a right one.
     */
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
)
