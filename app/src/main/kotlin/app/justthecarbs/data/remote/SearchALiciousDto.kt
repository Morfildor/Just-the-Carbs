package app.justthecarbs.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a field that arrives as either a JSON array of strings or a single string.
 *
 * `brands` is the field that forced this, and the difference is not cosmetic: the legacy
 * `cgi/search.pl` endpoint sends a comma-joined **string** (`"De Ruijter,Van Houten"`) while
 * Search-a-licious sends an **array** (`["De Ruijter"]`) — verified live on 2026-08-28, 137 of 140
 * hits across seven queries. Declaring it as either type alone throws `SerializationException` on
 * the other, which this app reports to the user as *malformed response*: an entire failed search
 * caused by a field's container type.
 *
 * Both forms collapse to the same thing — the first non-blank entry — because the UI shows one
 * brand. Scoped to this one field rather than relaxing the parser globally, for the same reason
 * [LooseNumericText] is scoped: the carbohydrate values must keep their strict typing.
 */
internal object FirstOfStringOrArray : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.justthecarbs.FirstOfStringOrArray", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = json.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
            is JsonArray -> element
                .filterIsInstance<JsonPrimitive>()
                .firstOrNull { it !is JsonNull && it.content.isNotBlank() }
                ?.content
            // An object here is a shape nobody has seen. Degrading to "no brand" keeps a usable hit
            // rather than failing the whole search over a decorative field.
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/**
 * Search-a-licious response envelope (`POST /search`).
 *
 * Captured from the live service on 2026-08-28. The envelope carries far more than this
 * (`aggregations`, `facets`, `charts`, `debug`, `page_count`, `took`); only what the app acts on is
 * modelled, and `ignoreUnknownKeys` absorbs the rest.
 *
 * [count] is the total number of upstream matches, not the number returned. It is modelled because
 * it is the one field that distinguishes a **legitimate empty answer** from a broken one: the
 * service answers `{"count":0,"hits":[]}` for a query that genuinely matches nothing, which must
 * become [app.justthecarbs.domain.ProductSearchResult.NoMatches] and must never trigger a fallback
 * to the legacy provider — while `{"count":94,"hits":[]}` is the same empty array meaning the
 * opposite thing, and must fall back. See [SearchALiciousDataSource].
 */
@Serializable
data class SearchALiciousResponse(
    val hits: List<SearchALiciousHit>? = null,
    val count: Int? = null,
    /**
     * True when the upstream search gave up early. Modelled so a truncated answer is not reported
     * as a complete one — a timed-out search that returns three hits is not the statement "there are
     * three matching products".
     */
    @SerialName("timed_out") val timedOut: Boolean? = null,
)

/**
 * One product in a Search-a-licious result page.
 *
 * Every field is nullable with a default, for the same reason the Open Food Facts DTO is: this
 * indexes a crowd-sourced database, any field can be missing on any record, and a strict schema
 * would turn an incomplete product into a parse failure the app reports as a broken response
 * rather than as the missing data it actually is.
 *
 * **`product_quantity_unit` is deliberately absent from this DTO.** It is not in the
 * Search-a-licious index at all — measured 0 of 140 hits across seven queries on 2026-08-28, and
 * requesting it explicitly by name returns nothing rather than an error. Declaring a field that can
 * never arrive would suggest the primary basis signal is available here when it is not; the basis
 * is resolved from [quantity]'s free text alone on this path. See [SearchALiciousDataSource].
 */
@Serializable
data class SearchALiciousHit(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    /**
     * Localized name, preferred where present exactly as on the legacy path.
     *
     * Only populated when the request sends `langs=nl,en` — verified live: without it the field is
     * absent from every hit. See [SearchALiciousApi.SEARCH_LANGS].
     */
    @SerialName("product_name_nl") val productNameNl: String? = null,
    @Serializable(with = FirstOfStringOrArray::class)
    val brands: String? = null,
    /** Free text as printed, e.g. `"390 gram"`. The only basis evidence this endpoint supplies. */
    val quantity: String? = null,
    val nutriments: OffNutriments? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
)
