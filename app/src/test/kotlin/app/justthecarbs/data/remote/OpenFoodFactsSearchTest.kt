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
           "quantity":"390 gram","product_quantity":390,"product_quantity_unit":"g",
           "nutriments":{"carbohydrates_100g":67},
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
        // "390 gram" is a spelling this app's own parser deliberately does not teach. OFF normalises
        // it upstream, so the structured unit — which a real response carries and this fixture now
        // reproduces — is what establishes the basis.
        assertEquals(NutritionBasis.PER_100_G, first.basis)
    }

    // ---- release pass §4: search uses the same basis rule as a barcode lookup -------------------
    //
    // Search and lookup share `PackageBasisResolver`, and this block pins that they share its
    // *outcome* too. The consequence differs by path — a lookup refuses the product, a hit simply
    // shows no number — but neither may print a figure under a unit nothing established.

    private suspend fun singleHit(product: String) =
        hits(dataSource.search("x")).also { assertEquals(1, it.size) }.first()

    @Test
    fun `a search hit with a structured millilitre unit carries a millilitre basis`() = runTest {
        respond(
            """{"count":1,"products":[
               {"code":"1111111111116","product_name":"Sinaasappelsap","quantity":"1,5 liter",
                "product_quantity":1500,"product_quantity_unit":"ml",
                "nutriments":{"carbohydrates_100g":9.4}}]}""",
        )

        val hit = singleHit("")

        assertEquals(NutritionBasis.PER_100_ML, hit.basis)
        assertEquals(0, BigDecimal("9.4").compareTo(hit.carbsPer100!!))
    }

    @Test
    fun `a search hit for a multipack carries the multipack's basis`() = runTest {
        respond(
            """{"count":1,"products":[
               {"code":"1111111111116","product_name":"Cola","quantity":"6 x 33 cl",
                "nutriments":{"carbohydrates_100g":10.6}}]}""",
        )

        val hit = singleHit("")

        assertEquals(NutritionBasis.PER_100_ML, hit.basis)
        assertEquals(0, BigDecimal("10.6").compareTo(hit.carbsPer100!!))
    }

    /**
     * The search half of the §3 rule.
     *
     * A hit is not refused — the user may well recognise the package, and dropping it would hide a
     * product that exists. What it must not do is print "67 g / 100 g" for a record that never said
     * grams. The number goes with the basis, so the card falls back to its existing "no value" copy
     * and selecting the hit runs a normal lookup, which asks the user properly.
     */
    @Test
    fun `a search hit with no establishable basis shows no carbohydrate figure`() = runTest {
        respond(
            """{"count":1,"products":[
               {"code":"1111111111116","product_name":"Onbekend","quantity":"family pack",
                "nutriments":{"carbohydrates_100g":67}}]}""",
        )

        val hit = singleHit("")

        assertNull("the basis was never established", hit.basis)
        assertNull("so the figure must not be shown under an assumed unit", hit.carbsPer100)
        assertEquals("but the hit is still selectable", "Onbekend", hit.name)
        assertEquals("family pack", hit.packageQuantity)
    }

    @Test
    fun `a search hit with an unsupported structured unit shows no carbohydrate figure`() = runTest {
        respond(
            """{"count":1,"products":[
               {"code":"1111111111116","product_name":"Imported","quantity":"16 oz",
                "product_quantity":16,"product_quantity_unit":"oz",
                "nutriments":{"carbohydrates_100g":67}}]}""",
        )

        val hit = singleHit("")

        assertNull(hit.basis)
        assertNull(hit.carbsPer100)
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
              {"code":"1111111111116","product_name":"Onbekend","quantity":"200 g","nutriments":{}}
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
              {"code":"1111111111116","product_name":"Broken","quantity":"200 g",
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
              {"code":"2222222222222","quantity":"200 g"},
              {"code":"1111111111116","product_name":"Usable","quantity":"200 g"}
            ]}
            """.trimIndent(),
        )

        val results = hits(dataSource.search("mixed"))

        assertEquals(1, results.size)
        assertEquals("1111111111116", results.single().barcode)
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

    // ---- P1 §10: a malformed remote `code` must not reach a search hit ---------------------------
    //
    // `hit.barcode` becomes a `product/{barcode}` navigation route verbatim. Before this fix, the
    // only check on `code` was "non-blank" — so a value containing a path separator, a URI
    // delimiter, the wrong length, or non-digit characters would reach that route with no validation
    // at all, the one thing every other barcode this app handles (scanned, manually typed) already
    // gets. A hit that fails validation is silently absent from the result list rather than reaching
    // the screen with a barcode this app cannot safely act on — the same "malformed data means the
    // record is missing, not corrupted" rule `NutritionValueValidator` already applies to a hit's
    // carbohydrate figure.

    @Test
    fun `a search hit whose code contains a path separator is dropped rather than passed through`() = runTest {
        respond(
            """
            {"count":1,"page":1,"page_size":1,"products":[
              {"code":"123/456","product_name":"Suspicious","nutriments":{"carbohydrates_100g":10}}
            ]}
            """.trimIndent(),
        )

        // The whole page had exactly one hit and it was dropped, so the result collapses to
        // NoMatches — the same rule an empty products array already produces, not a `Found`
        // carrying an empty list.
        assertEquals(
            "a code that could corrupt the product/{barcode} route must not become a hit",
            ProductSearchResult.NoMatches,
            dataSource.search("suspicious"),
        )
    }

    @Test
    fun `a search hit whose code has an invalid check digit is dropped`() = runTest {
        respond(
            """
            {"count":1,"page":1,"page_size":1,"products":[
              {"code":"8710496979129","product_name":"Wrong Check Digit","nutriments":{"carbohydrates_100g":10}}
            ]}
            """.trimIndent(),
        )

        assertEquals(ProductSearchResult.NoMatches, dataSource.search("query"))
    }

    @Test
    fun `a search hit whose code contains non-digit characters is dropped`() = runTest {
        respond(
            """
            {"count":1,"page":1,"page_size":1,"products":[
              {"code":"87104969791?5","product_name":"Malformed","nutriments":{"carbohydrates_100g":10}}
            ]}
            """.trimIndent(),
        )

        assertEquals(ProductSearchResult.NoMatches, dataSource.search("query"))
    }

    /** A malformed hit does not poison the rest of the page — the other, valid hits still come through. */
    @Test
    fun `one malformed code does not discard the other valid hits on the same page`() = runTest {
        respond(
            """
            {"count":2,"page":1,"page_size":2,"products":[
              {"code":"123/456","product_name":"Bad", "nutriments":{"carbohydrates_100g":10}},
              {"code":"8710496979125","product_name":"Chocoladehagel puur","brands":"De Ruijter",
               "quantity":"390 gram","product_quantity":390,"product_quantity_unit":"g",
               "nutriments":{"carbohydrates_100g":67}}
            ]}
            """.trimIndent(),
        )

        val result = hits(dataSource.search("mixed"))

        assertEquals(1, result.size)
        assertEquals("8710496979125", result.single().barcode)
    }

    /**
     * A valid 12-digit UPC-A must be normalised to the 13-digit form this app keys products by —
     * the same normalisation a scanned or manually-typed barcode already receives, so a search
     * result and a scanned result for the identical physical product resolve to the same row rather
     * than two.
     */
    @Test
    fun `a valid 12-digit code is normalised to the 13-digit form used as the database key`() = runTest {
        respond(
            """
            {"count":1,"page":1,"page_size":1,"products":[
              {"code":"036000291452","product_name":"UPC-A Product","nutriments":{"carbohydrates_100g":10}}
            ]}
            """.trimIndent(),
        )

        val result = hits(dataSource.search("upc"))

        assertEquals(1, result.size)
        assertEquals("0036000291452", result.single().barcode)
    }
}
