package app.justthecarbs.data.remote

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 * `proteins_100g` is read tolerantly on all three parsers that share `OffNutriments` (design spec
 * 2026-09-24, section 9): a product lookup and a search page must never fail because of protein.
 *
 * Open Food Facts types numeric fields inconsistently, so the fixtures send the field as a number,
 * a string, an object, garbage and null, always beside a valid carbohydrate figure. A strict
 * `Double?` field would turn the object case into a malformed reply for the whole response, which is
 * what the object cases pin.
 */
class ProteinParsingTest {

    private lateinit var server: MockWebServer
    private lateinit var offApi: OpenFoodFactsApi
    private lateinit var searchApi: SearchALiciousApi

    private val barcode = "8712100849060"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // The shared app configuration (NetworkModule): unknown keys ignored, nulls coerced.
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        offApi = retrofit.create(OpenFoodFactsApi::class.java)
        searchApi = retrofit.create(SearchALiciousApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun respond(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private suspend fun lookUpWithProtein(proteinJson: String?, quantity: String = "400 g"): ProductFetchResult {
        val protein = proteinJson?.let { ""","proteins_100g":$it""" }.orEmpty()
        respond(
            """
            {"code":"$barcode","product":{
              "product_name":"Hazelnootpasta","quantity":"$quantity",
              "nutriments":{"carbohydrates_100g":57.5$protein}
            }}
            """.trimIndent(),
        )
        return OpenFoodFactsDataSource(offApi, preferredLanguage = { "nl" }).fetch(barcode)
    }

    private fun found(result: ProductFetchResult) =
        (result as? ProductFetchResult.Found)?.product ?: error("expected Found but was $result")

    // ---- product lookup --------------------------------------------------------------------------

    @Test
    fun `a numeric protein value is stored with its source`() = runTest {
        val product = found(lookUpWithProtein("6.3"))

        assertEquals(0, BigDecimal("6.3").compareTo(product.proteinPer100))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, product.proteinOrigin)
        assertEquals(0, BigDecimal("57.5").compareTo(product.carbsPer100))
    }

    @Test
    fun `a protein value sent as a string is read`() = runTest {
        val product = found(lookUpWithProtein("\"6.3\""))

        assertEquals(0, BigDecimal("6.3").compareTo(product.proteinPer100))
    }

    @Test
    fun `a protein value of zero is a value, not a missing one`() = runTest {
        val product = found(lookUpWithProtein("0"))

        assertEquals(0, BigDecimal.ZERO.compareTo(product.proteinPer100))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, product.proteinOrigin)
    }

    @Test
    fun `an object in the protein field leaves the lookup usable`() = runTest {
        val product = found(lookUpWithProtein("""{"value":6.3}"""))

        assertNull(product.proteinPer100)
        assertNull(product.proteinOrigin)
        assertEquals(0, BigDecimal("57.5").compareTo(product.carbsPer100))
    }

    @Test
    fun `an array in the protein field leaves the lookup usable`() = runTest {
        val product = found(lookUpWithProtein("[6.3]"))

        assertNull(product.proteinPer100)
    }

    @Test
    fun `garbage text in the protein field leaves the lookup usable`() = runTest {
        val product = found(lookUpWithProtein("\"n/a\""))

        assertNull(product.proteinPer100)
        assertNull(product.proteinOrigin)
    }

    @Test
    fun `a null protein value is no protein`() = runTest {
        val product = found(lookUpWithProtein("null"))

        assertNull(product.proteinPer100)
    }

    @Test
    fun `a record without the field has no protein`() = runTest {
        val product = found(lookUpWithProtein(null))

        assertNull(product.proteinPer100)
        assertNull(product.proteinOrigin)
    }

    @Test
    fun `an impossible protein value is refused and the carbs still work`() = runTest {
        // 150 g of protein in 100 g of food cannot exist.
        val product = found(lookUpWithProtein("150"))

        assertNull(product.proteinPer100)
        assertEquals(0, BigDecimal("57.5").compareTo(product.carbsPer100))
    }

    @Test
    fun `a negative protein value is refused`() = runTest {
        assertNull(found(lookUpWithProtein("-2")).proteinPer100)
    }

    @Test
    fun `the millilitre ceiling applies under a millilitre basis`() = runTest {
        // Above the per-100 g ceiling, below the per-100 ml density bound: the same rule as carbs.
        val product = found(lookUpWithProtein("120", quantity = "500 ml"))

        assertEquals(NutritionBasis.PER_100_ML, product.basis)
        assertEquals(0, BigDecimal("120").compareTo(product.proteinPer100))
    }

    // ---- search pages ----------------------------------------------------------------------------

    @Test
    fun `an object in the protein field does not fail a Search-a-licious page`() = runTest {
        respond(
            """
            {"count":2,"page":1,"page_size":2,"hits":[
              {"code":"8710496979125","product_name":"Chocoladehagel puur","brands":["De Ruijter"],
               "quantity":"390 g","nutriments":{"carbohydrates_100g":67.0,"proteins_100g":{"x":1}}},
              {"code":"0009800800049","product_name":"Nutella","brands":["Nutella"],
               "quantity":"400 g","nutriments":{"carbohydrates_100g":57.5,"proteins_100g":"6.3"}}
            ]}
            """.trimIndent(),
        )

        val result = SearchALiciousDataSource(searchApi, preferredLanguage = { "en-NL" }).search("hagelslag")

        assertTrue("expected Found but was $result", result is ProductSearchResult.Found)
        val hits = (result as ProductSearchResult.Found).hits
        assertEquals(2, hits.size)
        assertEquals(0, BigDecimal("67").compareTo(hits.first { it.barcode == "8710496979125" }.carbsPer100))
    }

    @Test
    fun `an object in the protein field does not fail a legacy search page`() = runTest {
        respond(
            """
            {"count":1,"page":1,"page_size":1,"products":[
              {"code":"8710496979125","product_name":"Chocoladehagel puur","brands":"De Ruijter",
               "quantity":"390 gram","product_quantity":390,"product_quantity_unit":"g",
               "nutriments":{"carbohydrates_100g":67,"proteins_100g":{"x":1}}}
            ]}
            """.trimIndent(),
        )

        val result = OpenFoodFactsDataSource(offApi, preferredLanguage = { "en-NL" }).search("hagelslag")

        assertTrue("expected Found but was $result", result is ProductSearchResult.Found)
        assertEquals(1, (result as ProductSearchResult.Found).hits.size)
    }
}
