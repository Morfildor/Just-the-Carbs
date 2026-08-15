package app.justthecarbs.data.remote

import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductImageType
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
 * Exercises the real Retrofit stack against a local socket, so the JSON really is parsed and the
 * HTTP status codes really are interpreted. Brief §59: malformed API response, missing
 * carbohydrate value, unknown product.
 */
class OpenFoodFactsDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenFoodFactsDataSource

    private val barcode = "8712100849060"

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

        dataSource = OpenFoodFactsDataSource(api, preferredLanguage = { "nl" })
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

    private fun found(result: ProductFetchResult) =
        (result as? ProductFetchResult.Found)?.product
            ?: error("expected Found but was $result")

    @Test
    fun `maps a complete product`() = runTest {
        respond(
            """
            {"code":"$barcode","product":{
              "product_name":"Hagelslag puur",
              "brands":"De Ruijter",
              "quantity":"380 g",
              "nutriments":{"carbohydrates_100g":48.2},
              "image_front_small_url":"https://images.example/front.jpg"
            }}
            """.trimIndent(),
        )

        val product = found(dataSource.fetch(barcode))

        assertEquals("Hagelslag puur", product.name)
        assertEquals("De Ruijter", product.brand)
        assertEquals(0, BigDecimal("48.2").compareTo(product.carbsPer100))
        assertEquals(NutritionBasis.PER_100_G, product.basis)
        assertEquals(0, BigDecimal("380").compareTo(product.packageAmount!!))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, product.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, product.verificationStatus)
    }

    /**
     * Full product lookup (rebrand hardening pass §6): unlike search, a single-product read still
     * needs gallery and serving metadata — the calculator's gallery and countable-portion parsing
     * both depend on it.
     */
    @Test
    fun `product lookup requests PRODUCT_FIELDS, including gallery and serving metadata`() = runTest {
        respond("""{"code":"$barcode","product":{"product_name":"Hagelslag puur"}}""")

        dataSource.fetch(barcode)

        val fields = server.takeRequest().requestUrl!!.queryParameter("fields")!!
        assertEquals(OpenFoodFactsApi.PRODUCT_FIELDS, fields)
        assertTrue(fields.contains("selected_images"))
        assertTrue(fields.contains("serving_size"))
    }

    @Test
    fun `selected images prefer device then product then English and keep one safe image per type`() = runTest {
        respond(
            """
            {"product":{
              "product_name":"Chocolate",
              "lang":"de",
              "nutriments":{"carbohydrates_100g":48.2},
              "selected_images":{
                "front":{"display":{
                  "en":"https://images.openfoodfacts.org/front-en.400.jpg",
                  "nl":"https://images.openfoodfacts.org/front-nl.400.jpg"}},
                "nutrition":{"display":{
                  "en":"https://images.openfoodfacts.org/nutrition-en.400.jpg",
                  "de":"https://images.openfoodfacts.org/nutrition-de.400.jpg"}},
                "ingredients":{"display":{
                  "nl":"https://evil.example.com/ingredients-nl.400.jpg",
                  "en":"https://images.openfoodfacts.org/ingredients-en.400.jpg"}},
                "packaging":{"display":{
                  "nl":"https://images.openfoodfacts.org/front-nl.400.jpg"}}
              }
            }}
            """.trimIndent(),
        )

        val images = found(dataSource.fetch(barcode)).images

        assertEquals(
            listOf(ProductImageType.FRONT, ProductImageType.NUTRITION, ProductImageType.INGREDIENTS),
            images.map { it.type },
        )
        assertEquals(listOf("nl", "de", "en"), images.map { it.language })
        assertEquals(
            listOf(
                "https://images.openfoodfacts.org/front-nl.400.jpg",
                "https://images.openfoodfacts.org/nutrition-de.400.jpg",
                "https://images.openfoodfacts.org/ingredients-en.400.jpg",
            ),
            images.map { it.displayUrl },
        )
    }

    @Test
    fun `selected image fallback is deterministic when preferred languages are absent`() = runTest {
        respond(
            """{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":1.0},
               "selected_images":{"front":{"display":{
                 "sv":"https://images.openfoodfacts.org/front-sv.400.jpg",
                 "fr":"https://images.openfoodfacts.org/front-fr.400.jpg"}}}}}
            """
                .trimIndent(),
        )

        val image = found(dataSource.fetch(barcode)).images.single()

        assertEquals("fr", image.language)
        assertEquals("https://images.openfoodfacts.org/front-fr.400.jpg", image.displayUrl)
    }

    @Test
    fun `sends the identifying User-Agent that Open Food Facts requires`() = runTest {
        respond("""{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":1.0}}}""")

        dataSource.fetch(barcode)

        val sent = server.takeRequest().getHeader("User-Agent")
        assertTrue("User-Agent was '$sent'", !sent.isNullOrBlank())
    }

    @Test
    fun `requests only the fields the app actually uses`() = runTest {
        respond("""{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":1.0}}}""")

        dataSource.fetch(barcode)

        val path = server.takeRequest().path.orEmpty()
        assertTrue("path was $path", path.contains("/api/v3/product/$barcode"))
        assertTrue("path was $path", path.contains("fields="))
        assertTrue("path was $path", path.contains("selected_images"))
        assertTrue("path was $path", path.contains("lang"))
    }

    @Test
    fun `prefers the localized Dutch product name`() = runTest {
        respond(
            """{"product":{"product_name":"Chocolate sprinkles","product_name_nl":"Hagelslag puur",
               "nutriments":{"carbohydrates_100g":48.2}}}""",
        )

        assertEquals("Hagelslag puur", found(dataSource.fetch(barcode)).name)
    }

    @Test
    fun `detects a millilitre basis from the declared quantity`() = runTest {
        respond("""{"product":{"product_name":"Sinaasappelsap","quantity":"1 l",
                   "nutriments":{"carbohydrates_100g":9.4}}}""")

        val product = found(dataSource.fetch(barcode))

        assertEquals(NutritionBasis.PER_100_ML, product.basis)
        assertEquals("ml", product.portionUnit)
    }

    @Test
    fun `an empty response body is not found`() = runTest {
        respond("""{"code":"$barcode"}""")

        assertEquals(ProductFetchResult.NotFound, dataSource.fetch(barcode))
    }

    @Test
    fun `a 404 is not found`() = runTest {
        respond("{}", code = 404)

        assertEquals(ProductFetchResult.NotFound, dataSource.fetch(barcode))
    }

    @Test
    fun `a product with no name routes to manual entry rather than showing a nameless row`() = runTest {
        respond("""{"product":{"nutriments":{"carbohydrates_100g":48.2}}}""")

        assertEquals(ProductFetchResult.NotFound, dataSource.fetch(barcode))
    }

    // ---- §13: the value must be trustworthy or absent -------------------------------------------

    @Test
    fun `a missing carbohydrate value is unusable, not zero`() = runTest {
        respond("""{"product":{"product_name":"Hagelslag","nutriments":{}}}""")

        assertEquals(ProductFetchResult.Unusable(barcode), dataSource.fetch(barcode))
    }

    @Test
    fun `sugars are never substituted for a missing total carbohydrate`() = runTest {
        respond("""{"product":{"product_name":"Hagelslag","nutriments":{"sugars_100g":42.0}}}""")

        assertEquals(ProductFetchResult.Unusable(barcode), dataSource.fetch(barcode))
    }

    @Test
    fun `a negative carbohydrate value is rejected`() = runTest {
        respond("""{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":-5.0}}}""")

        assertEquals(ProductFetchResult.Unusable(barcode), dataSource.fetch(barcode))
    }

    @Test
    fun `an impossible carbohydrate value is rejected`() = runTest {
        respond("""{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":4820.0}}}""")

        assertEquals(ProductFetchResult.Unusable(barcode), dataSource.fetch(barcode))
    }

    // ---- countable-portions brief §7: serving_size --------------------------------------------

    @Test
    fun `a well-formed serving size becomes a portion unit candidate`() = runTest {
        respond(
            """{"product":{"product_name":"Bread","nutriments":{"carbohydrates_100g":42.0},
               "serving_size":"1 slice (36 g)"}}""",
        )

        val result = dataSource.fetch(barcode) as ProductFetchResult.Found

        val candidate = requireNotNull(result.portionUnitCandidate)
        assertEquals(PortionUnitKind.SLICE, candidate.kind)
        assertEquals(0, BigDecimal("36").compareTo(candidate.amountPerUnit))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
        assertEquals("1 slice (36 g)", candidate.rawServingText)
    }

    @Test
    fun `an ambiguous serving size yields no candidate, not a guess`() = runTest {
        respond(
            """{"product":{"product_name":"Bread","nutriments":{"carbohydrates_100g":42.0},
               "serving_size":"approx. 35 g"}}""",
        )

        val result = dataSource.fetch(barcode) as ProductFetchResult.Found

        assertNull(result.portionUnitCandidate)
    }

    @Test
    fun `a serving size in the wrong basis is dropped rather than mixed with the product basis`() = runTest {
        respond(
            """{"product":{"product_name":"Juice","quantity":"1 l","nutriments":{"carbohydrates_100g":9.4},
               "serving_size":"1 scoop (30 g)"}}""",
        )

        val result = dataSource.fetch(barcode) as ProductFetchResult.Found

        assertEquals(NutritionBasis.PER_100_ML, result.product.basis)
        assertNull("a gram serving on an ml product must not be mixed in", result.portionUnitCandidate)
    }

    @Test
    fun `no serving_size field yields no candidate`() = runTest {
        respond("""{"product":{"product_name":"X","nutriments":{"carbohydrates_100g":1.0}}}""")

        val result = dataSource.fetch(barcode) as ProductFetchResult.Found

        assertNull(result.portionUnitCandidate)
    }

    @Test
    fun `an unreadable package quantity still yields a usable product`() = runTest {
        respond("""{"product":{"product_name":"X","quantity":"family pack",
                   "nutriments":{"carbohydrates_100g":48.2}}}""")

        val product = found(dataSource.fetch(barcode))

        assertNull(product.packageAmount)
        assertEquals(NutritionBasis.PER_100_G, product.basis)
    }

    // ---- §36: every failure mode gets its own answer --------------------------------------------

    @Test
    fun `malformed JSON is reported as malformed, not as a crash`() = runTest {
        respond("""{"product":{"product_name":""")

        val result = dataSource.fetch(barcode)

        assertEquals(LookupError.MALFORMED, (result as ProductFetchResult.Failed).error)
    }

    @Test
    fun `a rate limit response is distinguished from other server errors`() = runTest {
        respond("{}", code = 429)

        val result = dataSource.fetch(barcode)

        assertEquals(LookupError.RATE_LIMITED, (result as ProductFetchResult.Failed).error)
    }

    @Test
    fun `a server error is reported as a server error`() = runTest {
        respond("{}", code = 500)

        val result = dataSource.fetch(barcode)

        assertEquals(LookupError.SERVER, (result as ProductFetchResult.Failed).error)
    }

    @Test
    fun `a dropped connection is reported rather than thrown`() = runTest {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })

        val result = dataSource.fetch(barcode)

        assertTrue("was $result", result is ProductFetchResult.Failed)
    }
}
