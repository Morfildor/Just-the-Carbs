package app.justthecarbs.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a JSON value that may be either a number or a string, as its plain text.
 *
 * Open Food Facts is crowd-maintained through several import paths, and its numeric fields are not
 * consistently typed: the same key is `500` in one record and `"500"` in the next. A strictly typed
 * property throws `SerializationException` on whichever form it was not declared for, which this app
 * surfaces to the user as *malformed response* — a whole failed lookup caused by a field's quoting.
 *
 * Scoped to the one field that needs it rather than switching the parser to `isLenient`, which would
 * relax quoting rules for every field in every response, including the carbohydrate values.
 */
internal object LooseNumericText : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.justthecarbs.LooseNumericText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

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
    /** Language tag used for the product's primary localized content. */
    val lang: String? = null,
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
    /**
     * OFF's own normalized unit for [quantity] — `"g"`, `"ml"`, occasionally `"l"` or `"cl"`.
     *
     * This is the primary basis signal (see [app.justthecarbs.domain.PackageBasisResolver]). OFF
     * parses the free text upstream, which is why this field survives spellings this app deliberately
     * does not teach its own parser ("390 gram", "1,5 liter") and multipack notation ("6 x 33 cl").
     *
     * Typed as a String, not an enum: it is crowd-derived and an unrecognised value must degrade to
     * "basis not established", never to a deserialization failure that the app would report as a
     * broken response.
     */
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    /**
     * OFF's normalized numeric quantity, in [productQuantityUnit].
     *
     * Requested and modelled so the basis question and the pack-size question can be answered from
     * the same response, but **deliberately not used as the package size**. For a multipack OFF
     * reports a total, and "is the package one bottle or the crate?" is the ambiguity
     * [app.justthecarbs.domain.PackageQuantityParser] refuses to resolve — adopting this number as
     * the pack size would resolve it silently, in the one place the ½-pack shortcut then acts on it.
     *
     * Read through [LooseNumericText] because OFF types it inconsistently — `500` in some records
     * and `"500"` in others. Strict deserialization throws on whichever of the two the declared type
     * is not, and this app reports a `SerializationException` as a malformed response, so declaring
     * it plainly would turn a field it does not even use into a lookup failure.
     */
    @SerialName("product_quantity")
    @Serializable(with = LooseNumericText::class)
    val productQuantity: String? = null,
    val nutriments: OffNutriments? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    /**
     * The 400 px front image — OFF's own display variant, confirmed live on 2026-08-14
     * (`front_en.879.400.jpg`). Feeds the calculator's hero image (§5). Deliberately not the
     * multi-megabyte original: 400 px covers a 120–170 dp hero at 3× density.
     */
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("selected_images") val selectedImages: OffSelectedImages? = null,
    /**
     * Free text, e.g. "1 slice (36 g)". Feeds [app.justthecarbs.domain.ServingSizeParser] only —
     * `serving_quantity`/`serving_quantity_unit` are deliberately not modelled here: OFF documents
     * `serving_quantity` as its own normalized extraction from this same text, not an independently
     * trustworthy "grams per countable unit" (countable-portions brief §7), so there is nothing this
     * app would do with them that parsing this field directly does not already cover.
     */
    @SerialName("serving_size") val servingSize: String? = null,
)

@Serializable
data class OffSelectedImages(
    val front: OffSelectedImage? = null,
    val nutrition: OffSelectedImage? = null,
    val ingredients: OffSelectedImage? = null,
    val packaging: OffSelectedImage? = null,
)

@Serializable
data class OffSelectedImage(
    /** OFF language tag to its 400 px display URL. Other sizes are intentionally not downloaded. */
    val display: Map<String, String> = emptyMap(),
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
    /**
     * TOTAL carbohydrate in one serving, as OFF reports it (spec §7).
     *
     * Read only to build a countable portion when `serving_size` names a unit but prints no weight —
     * it is never a substitute for [carbohydrates100g] and never feeds the per-100 calculation.
     */
    @SerialName("carbohydrates_serving") val carbohydratesServing: Double? = null,
)
