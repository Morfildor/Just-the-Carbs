package app.carbscan.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open Food Facts read API **v3** response (`api/v3/product/{barcode}`).
 *
 * This said "v2" until 2026-08-14 while the endpoint in [OpenFoodFactsApi] had already moved to v3 —
 * corrected against the actual request path, not from memory. The fields this app reads are
 * unchanged between the two versions; v2 remains deprecated-but-supported upstream.
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

/**
 * `cgi/search.pl?json=1` response (spec §9).
 *
 * The products carry the same shape as a single-product read — verified live on 2026-08-14 against
 * `search_terms=hagelslag`, which returned code, name, brands, quantity, `image_front_url` and
 * `nutriments.carbohydrates_100g` for each hit — so [OffProduct] is reused rather than duplicated.
 *
 * `count` is the total number of matches upstream, not the number returned; the app requests a
 * small page and never paginates, because this is a disambiguation list, not a catalogue.
 */
@Serializable
data class OffSearchResponse(
    val count: Int? = null,
    val products: List<OffProduct> = emptyList(),
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
    /**
     * The 400 px front image — OFF's own display variant, confirmed live on 2026-08-14
     * (`front_en.879.400.jpg`). Feeds the calculator's hero image (§5). Deliberately not the
     * multi-megabyte original: 400 px covers a 120–170 dp hero at 3× density.
     */
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    /**
     * Free text, e.g. "1 slice (36 g)". Feeds [app.carbscan.domain.ServingSizeParser] only —
     * `serving_quantity`/`serving_quantity_unit` are deliberately not modelled here: OFF documents
     * `serving_quantity` as its own normalized extraction from this same text, not an independently
     * trustworthy "grams per countable unit" (countable-portions brief §7), so there is nothing this
     * app would do with them that parsing this field directly does not already cover.
     */
    @SerialName("serving_size") val servingSize: String? = null,
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
