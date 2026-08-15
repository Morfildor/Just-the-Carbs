package app.justthecarbs.data.remote

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchResult
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
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
 * Free-text product search against Open Food Facts (spec §9).
 *
 * The fixtures below use the response shape captured from the **live** endpoint on 2026-08-14
 * (`cgi/search.pl?search_terms=hagelslag`), not an invented one — including the detail that
 * `quantity` arrives as free text like `"390 gram"` rather than a number.
 */
class OpenFoodFactsSearchTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenFoodFactsDataSource

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
            .create(OpenFoodFactsApi::class.java)

        dataSource = OpenFoodFactsDataSource(api)
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
        (result as? ProductSearchResult.Found)?.hits
            ?: error("expected Found but was $result")

    /** Verbatim shape of a live hit, trimmed to the requested fields. */
    private val liveShapedResponse = """
        {"count":243,"page":1,"page_size":2,"products":[
          {"code":"8710496979125","product_name":"Chocoladehagel puur","brands":"De Ruijter",
           "quantity":"390 gram","nutriments":{"carbohydrates_100g":67},
           "image_front_url":"https://images.openfoodfacts.org/images/products/front.400.jpg",
           "serving_size":"20 gram"},
          {"code":"8718906716223","product_name":"Puur Hagelslag","brands":"Albert Heijn",
           "quantity":"600 g","nutriments":{"carbohydrates_100g":67},
           "image_front_url":"https://images.openfoodfacts.org/images/products/front2.400.jpg"}
        ]}
    """.trimIndent()

    @Test
    fun `maps live-shaped search hits`() = runTest {
        respond(liveShapedResponse)

        val results = hits(dataSource.search("hagelslag"))

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("8710496979125", first.barcode)
        assertEquals("Chocoladehagel puur", first.name)
        assertEquals("De Ruijter", first.brand)
        assertEquals("390 gram", first.packageQuantity)
        assertEquals(0, BigDecimal("67").compareTo(first.carbsPer100!!))
        assertEquals(NutritionBasis.PER_100_G, first.basis)
    }

    @Test
    fun `sends the search terms as a query parameter`() = runTest {
        respond(liveShapedResponse)

        dataSource.search("hagelslag")

        val request = server.takeRequest()
        assertTrue(
            "must call cgi/search.pl with search_terms — api/v2/search does not accept it",
            request.path!!.contains("cgi/search.pl") && request.path!!.contains("search_terms=hagelslag"),
        )
    }

    /**
     * A record with no carbohydrate value is still shown. The user may recognise the package, and
     * can verify it from the label afterwards — dropping it would hide a product that exists.
     * The card is responsible for saying the value is missing rather than implying one.
     */
    @Test
    fun `a hit without a carbohydrate value is kept, with a null value`() = runTest {
        respond(
            """
            {"count":1,"products":[
              {"code":"123","product_name":"Onbekend","quantity":"200 g","nutriments":{}}
            ]}
            """.trimIndent(),
        )

        val hit = hits(dataSource.search("onbekend")).single()

        assertEquals("Onbekend", hit.name)
        assertNull("no value must stay null rather than becoming zero", hit.carbsPer100)
    }

    /** An out-of-range figure is shown as no value, never as a number the calculator would refuse. */
    @Test
    fun `an impossible carbohydrate value is not offered as a figure`() = runTest {
        respond(
            """
            {"count":1,"products":[
              {"code":"123","product_name":"Broken","quantity":"200 g",
               "nutriments":{"carbohydrates_100g":250}}
            ]}
            """.trimIndent(),
        )

        assertNull(hits(dataSource.search("broken")).single().carbsPer100)
    }

    /** Neither can be selected usefully; a blank row is worse than a shorter list. */
    @Test
    fun `hits without a barcode or a name are dropped`() = runTest {
        respond(
            """
            {"count":3,"products":[
              {"product_name":"No barcode","quantity":"200 g"},
              {"code":"456","quantity":"200 g"},
              {"code":"789","product_name":"Usable","quantity":"200 g"}
            ]}
            """.trimIndent(),
        )

        val results = hits(dataSource.search("mixed"))

        assertEquals(1, results.size)
        assertEquals("789", results.single().barcode)
    }

    @Test
    fun `an empty result set is reported as no matches rather than as a failure`() = runTest {
        respond("""{"count":0,"products":[]}""")

        assertEquals(ProductSearchResult.NoMatches, dataSource.search("zzzzzz"))
    }

    /** A blank query never reaches the network — there is nothing to ask, and reads are budgeted. */
    @Test
    fun `a blank query does not hit the network`() = runTest {
        assertEquals(ProductSearchResult.NoMatches, dataSource.search("   "))
        assertEquals(0, server.requestCount)
    }

    /**
     * Observed live on 2026-08-14: this endpoint intermittently answers 503 with an HTML page while
     * the product-read endpoint is healthy. It must read as a retryable server problem — reporting
     * "no matches" would tell the user their product does not exist because a host was busy.
     */
    @Test
    fun `a 503 is a retryable server failure, not an empty result`() = runTest {
        respond("<html>Page temporarily unavailable</html>", code = 503)

        val result = dataSource.search("hagelslag")

        assertEquals(LookupError.SERVER, (result as ProductSearchResult.Failed).error)
    }

    @Test
    fun `rate limiting is reported distinctly so the user can be told to wait`() = runTest {
        respond("{}", code = 429)

        val result = dataSource.search("hagelslag")

        assertEquals(LookupError.RATE_LIMITED, (result as ProductSearchResult.Failed).error)
    }

    /**
     * Lean search response (rebrand hardening pass §6): up to 20 hits don't need gallery/serving
     * metadata a result card never shows, so the request must not ask OFF for it.
     */
    @Test
    fun `search requests the lean SEARCH_FIELDS, not gallery or serving metadata`() = runTest {
        respond(liveShapedResponse)

        dataSource.search("hagelslag")

        val fields = server.takeRequest().requestUrl!!.queryParameter("fields")!!
        assertEquals(OpenFoodFactsApi.SEARCH_FIELDS, fields)
        assertTrue(
            "search fields must not request gallery/serving metadata",
            !fields.contains("selected_images") && !fields.contains("serving_size"),
        )
    }
}
