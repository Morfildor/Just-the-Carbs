package app.justthecarbs.data.remote

import app.justthecarbs.domain.FallbackProductSearch
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal

/**
 * Search-a-licious mapping (2026-08-28 migration).
 *
 * The fixtures reproduce the response shape captured from the **live** service on 2026-08-28
 * (`https://search.openfoodfacts.org/search?q=…`), not an invented one. Two details of that shape
 * are load-bearing and would each have broken the app if assumed rather than measured:
 *
 * - `brands` arrives as a JSON **array** (`["Nutella"]`), where the legacy endpoint sends a
 *   comma-joined string. Measured on 137 of 140 hits across seven queries.
 * - `product_quantity_unit` is **absent from the index entirely** — 0 of 140 hits, and requesting it
 *   by name returns nothing rather than erroring. It is the primary basis signal on the legacy path,
 *   so its absence is what makes the basis resolve less often here.
 */
class SearchALiciousDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: SearchALiciousDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SearchALiciousApi::class.java)

        dataSource = SearchALiciousDataSource(api)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun hits(result: ProductSearchResult) =
        (result as? ProductSearchResult.Found)?.hits ?: error("expected Found but was $result")

    /** Verbatim shape of a live hit, trimmed to the requested fields. */
    private val liveShapedResponse = """
        {"count":631,"page":1,"page_size":2,"timed_out":false,"hits":[
          {"code":"8710496979125","product_name":"Chocoladehagel puur",
           "product_name_nl":"Chocoladehagel puur","brands":["De Ruijter"],
           "quantity":"390 g","lang":"nl",
           "nutriments":{"carbohydrates_100g":67.0},
           "image_front_url":"https://images.openfoodfacts.org/images/products/front_nl.4.400.jpg",
           "image_front_small_url":"https://images.openfoodfacts.org/images/products/front_nl.4.200.jpg"},
          {"code":"0009800800049","product_name":"Nutella & go!","brands":["Nutella"],
           "quantity":"500 ml","nutriments":{"carbohydrates_100g":63.46}}
        ]}
    """.trimIndent()

    @Test
    fun `a live-shaped response maps to hits`() = runTest {
        respond(liveShapedResponse)

        val hits = hits(dataSource.search("hagelslag"))

        assertEquals(2, hits.size)
        val first = hits[0]
        assertEquals("8710496979125", first.barcode)
        assertEquals("Chocoladehagel puur", first.name)
        assertEquals("De Ruijter", first.brand)
        assertEquals("390 g", first.packageQuantity)
        assertEquals(0, BigDecimal("67").compareTo(first.carbsPer100))
        assertEquals(NutritionBasis.PER_100_G, first.basis)
        assertTrue(first.imageUrl!!.endsWith("front_nl.4.400.jpg"))
        // ml resolves as readily as g — the basis follows the printed unit, not a default.
        assertEquals(NutritionBasis.PER_100_ML, hits[1].basis)
    }

    /**
     * The container-type difference between the two providers, pinned.
     *
     * A string here is not hypothetical politeness: OFF is fed by several import paths and the app
     * must not fail an entire search over one field's container type.
     */
    @Test
    fun `brands is read from either an array or a plain string`() = runTest {
        respond(
            """
            {"count":3,"hits":[
              {"code":"1","product_name":"A","brands":["First","Second"]},
              {"code":"2","product_name":"B","brands":"Legacy Style,Other"},
              {"code":"3","product_name":"C","brands":[]}
            ]}
            """.trimIndent(),
        )

        val hits = hits(dataSource.search("x"))

        assertEquals("First", hits[0].brand)
        // The comma split matches the legacy path's behaviour exactly — one brand is shown.
        assertEquals("Legacy Style", hits[1].brand)
        // An empty array is "no brand", not a crash and not a blank string.
        assertNull(hits[2].brand)
    }

    @Test
    fun `a hit with no brand, image, quantity or carbs is still returned`() = runTest {
        respond("""{"count":1,"hits":[{"code":"5","product_name":"Bare record"}]}""")

        val hit = hits(dataSource.search("bare")).single()

        assertEquals("5", hit.barcode)
        assertEquals("Bare record", hit.name)
        assertNull(hit.brand)
        assertNull(hit.imageUrl)
        assertNull(hit.packageQuantity)
        // No quantity text means no basis, and no basis means no number — never an assumed unit.
        assertNull(hit.basis)
        assertNull(hit.carbsPer100)
    }

    @Test
    fun `hits with a missing or blank barcode or name are skipped`() = runTest {
        respond(
            """
            {"count":5,"hits":[
              {"product_name":"No barcode"},
              {"code":"","product_name":"Blank barcode"},
              {"code":"7"},
              {"code":"8","product_name":"   "},
              {"code":"9","product_name":"Keeps this one"}
            ]}
            """.trimIndent(),
        )

        val hits = hits(dataSource.search("x"))

        assertEquals(listOf("9"), hits.map { it.barcode })
    }

    /**
     * Deduplication is by **barcode**, never by name.
     *
     * The fixture makes the distinction observable: two different products genuinely named "Gouda"
     * must both survive, while one product repeated must appear once.
     */
    @Test
    fun `duplicate barcodes are collapsed but same-named distinct products are kept`() = runTest {
        respond(
            """
            {"count":4,"hits":[
              {"code":"100","product_name":"Gouda","brands":["First seen"]},
              {"code":"200","product_name":"Gouda","brands":["A different product"]},
              {"code":"100","product_name":"Gouda","brands":["Duplicate, dropped"]}
            ]}
            """.trimIndent(),
        )

        val hits = hits(dataSource.search("gouda"))

        assertEquals(listOf("100", "200"), hits.map { it.barcode })
        // distinctBy keeps the FIRST occurrence, which is what preserves relevance ordering.
        assertEquals("First seen", hits[0].brand)
    }

    @Test
    fun `server relevance order is preserved`() = runTest {
        respond(
            """
            {"count":4,"hits":[
              {"code":"1","product_name":"Most relevant"},
              {"code":"2","product_name":"Second"},
              {"code":"3","product_name":"Third"},
              {"code":"4","product_name":"Least"}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("1", "2", "3", "4"), hits(dataSource.search("x")).map { it.barcode })
    }

    /**
     * The rule the whole fallback design rests on: an empty answer is an **answer**.
     *
     * If this returned Failed, every deliberate search for something genuinely absent would spend a
     * second request against the legacy endpoint.
     */
    @Test
    fun `a zero-result response is NoMatches, not a failure`() = runTest {
        respond("""{"count":0,"hits":[],"timed_out":false}""")

        assertEquals(ProductSearchResult.NoMatches, dataSource.search("zzzqqxx"))
    }

    /**
     * The distinction this whole group exists for, and the one that was previously wrong.
     *
     * A response carrying matches whose every record fails to map is **not** the service saying
     * "nothing matches". Reporting it as [ProductSearchResult.NoMatches] would suppress the legacy
     * fallback — which never runs on `NoMatches`, by design — and tell the user their product does
     * not exist, when in fact the app failed to read a reply that did contain it. That is invisible
     * from the screen: an empty result list looks the same either way.
     *
     * So it is [LookupError.MALFORMED], which *is* fallback-eligible.
     */
    @Test
    fun `a response whose every hit is unusable is MALFORMED, not NoMatches`() = runTest {
        respond("""{"count":2,"hits":[{"product_name":"no code"},{"code":"1"}]}""")

        val result = dataSource.search("x")

        assertEquals(LookupError.MALFORMED, (result as ProductSearchResult.Failed).error)
    }

    /**
     * `count` claims matches, the array is empty. The array alone is indistinguishable from a
     * genuine zero-result answer, so `count` is what separates them.
     */
    @Test
    fun `a positive count with an empty hits array is MALFORMED`() = runTest {
        respond("""{"count":94,"hits":[],"timed_out":false}""")

        val result = dataSource.search("x")

        assertEquals(LookupError.MALFORMED, (result as ProductSearchResult.Failed).error)
    }

    /**
     * No `count` at all, but records arrived and none mapped.
     *
     * The array being non-empty is itself the claim that matches exist, so a missing `count` must
     * not downgrade this back to an answer. Pinned because the obvious implementation — trusting
     * `count` alone — would report `NoMatches` here and silently suppress the fallback.
     */
    @Test
    fun `unusable hits with no count at all are MALFORMED`() = runTest {
        respond("""{"hits":[{"product_name":"no code"},{"code":"  ","product_name":"x"}]}""")

        val result = dataSource.search("x")

        assertEquals(LookupError.MALFORMED, (result as ProductSearchResult.Failed).error)
    }

    /**
     * One bad record must never discard the good ones.
     *
     * Missing fields are an ordinary state of a crowd-sourced database, not a broken response. The
     * escalation above applies only when *nothing* survived.
     */
    @Test
    fun `a mix of malformed and valid hits returns only the valid ones`() = runTest {
        respond(
            """{"count":3,"hits":[
                 {"product_name":"no code"},
                 {"code":"111","product_name":"Real product"},
                 {"code":"222"}
               ]}""",
        )

        val hits = hits(dataSource.search("x"))

        assertEquals(1, hits.size)
        assertEquals("111", hits[0].barcode)
    }

    /**
     * A body with **no `hits` key** is a shape this app does not recognise — a proxy page, a
     * redesigned envelope — and must be fallback-eligible, unlike an empty list.
     */
    @Test
    fun `a body with no hits array is MALFORMED, not NoMatches`() = runTest {
        respond("""{"count":0,"detail":"something else entirely"}""")

        val result = dataSource.search("x")

        assertEquals(LookupError.MALFORMED, (result as ProductSearchResult.Failed).error)
    }

    @Test
    fun `unparseable JSON is a controlled failure, never a crash`() = runTest {
        respond("""{"hits":[{"code":}]}""")

        val result = dataSource.search("x")

        assertTrue(result is ProductSearchResult.Failed)
    }

    @Test
    fun `a self-reported timeout is a failure rather than a truncated answer`() = runTest {
        respond("""{"count":9000,"timed_out":true,"hits":[{"code":"1","product_name":"Partial"}]}""")

        val result = dataSource.search("x")

        assertEquals(LookupError.TIMEOUT, (result as ProductSearchResult.Failed).error)
    }

    @Test
    fun `a 5xx is a SERVER failure`() = runTest {
        respond("""{"detail":"boom"}""", code = 503)

        assertEquals(
            LookupError.SERVER,
            (dataSource.search("x") as ProductSearchResult.Failed).error,
        )
    }

    @Test
    fun `a 400 validation error is a SERVER failure, not a crash`() = runTest {
        respond("""{"detail":"1 validation error for SearchParameters"}""", code = 400)

        assertEquals(
            LookupError.SERVER,
            (dataSource.search("x") as ProductSearchResult.Failed).error,
        )
    }

    @Test
    fun `a 429 carries the server's own Retry-After through`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("Retry-After", "42")
                .setBody("{}"),
        )

        val result = dataSource.search("x") as ProductSearchResult.Failed

        assertEquals(LookupError.RATE_LIMITED, result.error)
        assertEquals(42_000L, result.retryAfterMs)
    }

    @Test
    fun `a blank query never reaches the network`() = runTest {
        assertEquals(ProductSearchResult.NoMatches, dataSource.search("   "))

        assertEquals(0, server.requestCount)
    }

    /**
     * The request itself, pinned — because two of these parameters were established by measurement
     * and would silently degrade the feature if dropped.
     *
     * Sent as a **POST body**, so `langs` and `fields` are JSON arrays here where the GET form took
     * comma-joined strings. Same values, different container — the POST schema types them as arrays.
     */
    @Test
    fun `the request asks for the measured fields and languages`() = runTest {
        respond("""{"count":0,"hits":[]}""")

        dataSource.search("hagelslag")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("hagelslag", body["q"]!!.jsonPrimitive.content)
        assertEquals(20, body["page_size"]!!.jsonPrimitive.int)
        // Without langs, product_name_nl is absent from every hit and Dutch recall collapses
        // (hagelslag: 449 matches with it, 26 without).
        assertEquals(listOf("nl", "en"), body["langs"]!!.jsonArray.map { it.jsonPrimitive.content })
        val fields = body["fields"]!!.jsonArray.map { it.jsonPrimitive.content }
        // The EXACT list, not a set of `contains` checks. Those cannot see a field being *added*,
        // which is the direction this regresses in: every unused field is paid for on every request
        // and the response still looks perfectly correct. `lang` was requested and read nowhere
        // until 2026-08-28, and a `contains`-based assertion is exactly why nothing noticed.
        assertEquals(
            listOf(
                "code",
                "product_name",
                "product_name_nl",
                "brands",
                "quantity",
                "nutriments",
                "image_front_small_url",
                "image_front_url",
            ),
            fields,
        )
        // Not requested because it is not in this index — see the class KDoc.
        assertTrue(!fields.contains("product_quantity_unit"))
    }

    /**
     * Every requested field is one a result card actually renders.
     *
     * Stated as a property rather than only as a list, so the reason the list is what it is survives
     * next to it: `SearchResultRow` draws the name (preferring `product_name_nl`), the brand, the
     * printed quantity, the carbohydrate figure and the photo, and a hit is identified by its
     * barcode. Nothing else is read anywhere in the app.
     */
    @Test
    fun `every requested field feeds something the result card shows`() {
        val rendered = mapOf(
            "code" to "the barcode — a hit's identity and what selecting it looks up",
            "product_name" to "the card's title",
            "product_name_nl" to "the preferred localized title",
            "brands" to "the card's subtitle",
            "quantity" to "the subtitle's package size, and the only basis evidence here",
            "nutriments" to "the carbohydrate figure",
            "image_front_small_url" to "the thumbnail",
            "image_front_url" to "the thumbnail, preferred",
        )
        assertEquals(rendered.keys.toList(), SearchALiciousApi.SEARCH_FIELDS)
    }

    /**
     * The transport itself: POST, with nothing the user typed in the URL.
     *
     * The privacy claim of the POST migration is exactly this — a URL is the part of a request that
     * proxies, gateways and server access logs retain in plain text as a matter of course, and a
     * search term in this app is a food someone is about to eat. Asserted as *absence from the whole
     * URL* rather than absence of a `q` parameter, so reintroducing the term under any parameter
     * name, or in the path, fails this test.
     */
    @Test
    fun `the search uses POST and the query never appears in the URL`() = runTest {
        respond("""{"count":0,"hits":[]}""")

        dataSource.search("hagelslag")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(
            "query text leaked into the URL: ${request.path}",
            !request.path!!.contains("hagelslag"),
        )
        assertEquals("hagelslag", Json.parseToJsonElement(request.body.readUtf8())
            .jsonObject["q"]!!.jsonPrimitive.content)
    }

    /**
     * Punctuation a user types must not leave the app as a Lucene operator.
     *
     * `(` is measured: `Kinder Bueno (White)` returned **zero hits** unescaped against the live
     * service and the correct product once escaped. The full evidence, including the `milk
     * -chocolate` case where the unescaped form returned a *wrong* result set rather than an empty
     * one, is in [SearchALiciousQuery].
     */
    @Test
    fun `reserved characters are escaped in the request body`() = runTest {
        respond("""{"count":0,"hits":[]}""")

        dataSource.search("Kinder Bueno (White)")

        val q = Json.parseToJsonElement(server.takeRequest().body.readUtf8())
            .jsonObject["q"]!!.jsonPrimitive.content
        assertEquals("""Kinder Bueno \(White\)""", q)
    }

    /**
     * An out-of-range figure shows as *no value*, never as a number (§13).
     *
     * The same rule the legacy path applies; a card must not display something the calculator would
     * refuse.
     */
    @Test
    fun `an impossible carbohydrate value is dropped rather than shown`() = runTest {
        respond(
            """{"count":1,"hits":[{"code":"1","product_name":"Corrupt",
               "quantity":"100 g","nutriments":{"carbohydrates_100g":900}}]}""",
        )

        val hit = hits(dataSource.search("x")).single()

        assertNull(hit.carbsPer100)
        assertEquals(NutritionBasis.PER_100_G, hit.basis)
    }

    /**
     * The measured consequence of the missing structured unit, stated as behaviour rather than left
     * implicit: an unparseable quantity yields no basis and therefore no number — it does **not**
     * fall back to grams.
     */
    @Test
    fun `an unreadable quantity yields no basis and no number`() = runTest {
        respond(
            """{"count":1,"hits":[{"code":"1","product_name":"Mystery pack",
               "quantity":"family size","nutriments":{"carbohydrates_100g":20}}]}""",
        )

        val hit = hits(dataSource.search("x")).single()

        assertNull(hit.basis)
        assertNull(hit.carbsPer100)
        // Everything the user needs to recognise the package still shows.
        assertEquals("Mystery pack", hit.name)
        assertEquals("family size", hit.packageQuantity)
    }

    @Test
    fun `the localized name is preferred over the default one`() = runTest {
        respond(
            """{"count":1,"hits":[{"code":"1","product_name":"Chocolate sprinkles",
               "product_name_nl":"Chocoladehagelslag"}]}""",
        )

        assertEquals("Chocoladehagelslag", hits(dataSource.search("x")).single().name)
    }

    // ------------------------------------------------------- the two halves, wired together

    /**
     * The end-to-end claim of the unusable-hits fix, through the **real** chain.
     *
     * The classification tests above assert what the data source returns; the chain tests in
     * `FallbackProductSearchTest` assert what the chain does with a `MALFORMED`. Neither proves the
     * two connect — and connecting them is the entire point, because the original bug was precisely
     * that a real unusable response produced `NoMatches` and the chain then (correctly) declined to
     * fall back. So this drives an actual HTTP response through an actual `FallbackProductSearch`.
     */
    @Test
    fun `a real unusable-hits response reaches the legacy fallback`() = runTest {
        respond("""{"count":2,"hits":[{"product_name":"no code"},{"code":"1"}]}""")
        val fallbackCalls = mutableListOf<String>()
        val chain = FallbackProductSearch(
            primary = dataSource,
            fallback = object : ProductSearchSource {
                override suspend fun search(terms: String): ProductSearchResult {
                    fallbackCalls += terms
                    return ProductSearchResult.Found(
                        listOf(ProductSearchHit("999", "From legacy", null, null, null, null, null)),
                    )
                }
            },
        )

        val result = chain.search("hagelslag")

        assertEquals(listOf("hagelslag"), fallbackCalls)
        assertEquals("999", (result as ProductSearchResult.Found).hits.single().barcode)
        // And the fallback received the user's text verbatim — the Lucene escaping is scoped to the
        // Search-a-licious request body and must not follow the query to a provider with no query
        // language, where literal backslashes would match nothing.
        assertTrue(!fallbackCalls.single().contains('\\'))
    }

    /**
     * The other side of the same fix, and the rule it must not break: a genuine zero-result answer
     * is still an answer, and still costs no legacy request.
     */
    @Test
    fun `a real zero-result response does not reach the legacy fallback`() = runTest {
        respond("""{"count":0,"hits":[],"timed_out":false}""")
        var fallbackCalled = false
        val chain = FallbackProductSearch(
            primary = dataSource,
            fallback = object : ProductSearchSource {
                override suspend fun search(terms: String): ProductSearchResult {
                    fallbackCalled = true
                    return ProductSearchResult.NoMatches
                }
            },
        )

        assertEquals(ProductSearchResult.NoMatches, chain.search("zzzqqxx"))
        assertTrue("a genuine no-match must not spend a legacy request", !fallbackCalled)
    }
}
