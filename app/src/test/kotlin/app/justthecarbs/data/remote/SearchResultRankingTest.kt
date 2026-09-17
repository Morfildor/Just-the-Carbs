package app.justthecarbs.data.remote

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class SearchResultRankingTest {

    private fun candidate(
        name: String,
        brand: String? = null,
        countries: List<String> = emptyList(),
        scans: Int? = null,
        calculable: Boolean = true,
    ) = SearchResultRanking.Candidate(
        hit = ProductSearchHit(
            barcode = name,
            name = name,
            brand = brand,
            packageQuantity = if (calculable) "400 g" else null,
            carbsPer100 = if (calculable) BigDecimal("50") else null,
            basis = if (calculable) NutritionBasis.PER_100_G else null,
            imageUrl = null,
        ),
        countries = countries,
        uniqueScans = scans,
    )

    private fun rank(query: String, country: String?, vararg candidates: SearchResultRanking.Candidate) =
        SearchResultRanking.rank(query, candidates.toList(), country).map { it.name }

    private val nl = "en:netherlands"

    @Test
    fun `a result containing every query word ranks above one that does not`() {
        assertEquals(
            listOf("Calve Pindakaas", "Pindakaas"),
            rank("calve pindakaas", nl, candidate("Pindakaas"), candidate("Calve Pindakaas")),
        )
    }

    @Test
    fun `a query word may be found in the brand`() {
        assertEquals(
            listOf("Pindakaas", "Pindasaus"),
            rank(
                "calve pindakaas",
                null,
                candidate("Pindasaus", brand = "Calvé"),
                candidate("Pindakaas", brand = "Calvé"),
            ),
        )
    }

    /**
     * Measured live: with German in the query languages, "honig macaroni" matches German *Honig*
     * (honey). Nothing matched both words, and re-sorting those partial matches by country put an
     * unrelated Spanish bread first. Partial matches keep the service's own order.
     */
    @Test
    fun `results that miss a query word keep the provider's order`() {
        assertEquals(
            listOf("Miel", "Macaroni", "Brood"),
            rank(
                "honig macaroni",
                nl,
                candidate("Miel", scans = 1, calculable = false),
                candidate("Macaroni", scans = 900),
                candidate("Brood", countries = listOf(nl), scans = 5000),
            ),
        )
    }

    @Test
    fun `among full matches a product sold in the device country comes first`() {
        assertEquals(
            listOf("Nutella jar", "Nutella & go"),
            rank(
                "nutella",
                nl,
                candidate("Nutella & go", countries = listOf("en:united-states"), scans = 50),
                candidate("Nutella jar", countries = listOf(nl, "en:belgium"), scans = 3),
            ),
        )
    }

    @Test
    fun `with no known device country, country is not a signal`() {
        assertEquals(
            listOf("Nutella & go", "Nutella jar"),
            rank(
                "nutella",
                null,
                candidate("Nutella & go", countries = listOf("en:united-states"), scans = 50),
                candidate("Nutella jar", countries = listOf(nl), scans = 3),
            ),
        )
    }

    @Test
    fun `then a product the app can calculate with comes before one it cannot`() {
        assertEquals(
            listOf("Hagelslag puur", "Hagelslag melk"),
            rank(
                "hagelslag",
                nl,
                candidate("Hagelslag melk", countries = listOf(nl), scans = 90, calculable = false),
                candidate("Hagelslag puur", countries = listOf(nl), scans = 2),
            ),
        )
    }

    @Test
    fun `then the more widely scanned product comes first`() {
        assertEquals(
            listOf("Fanta orange", "Fanta lemon"),
            rank(
                "fanta",
                nl,
                candidate("Fanta lemon", countries = listOf(nl), scans = 4),
                candidate("Fanta orange", countries = listOf(nl), scans = 400),
            ),
        )
    }

    @Test
    fun `equal candidates keep the provider's order`() {
        assertEquals(
            listOf("Melk A", "Melk B", "Melk C"),
            rank("melk", nl, candidate("Melk A"), candidate("Melk B"), candidate("Melk C")),
        )
    }

    @Test
    fun `matching ignores case and accents`() {
        assertEquals(
            listOf("CALVÉ Pindakaas", "Pindakaas"),
            rank("calve PINDAKAAS", null, candidate("Pindakaas"), candidate("CALVÉ Pindakaas")),
        )
    }

    @Test
    fun `punctuation in the query is not a word`() {
        assertEquals(
            listOf("Ben & Jerry's Cookie Dough", "Ben's rice"),
            rank(
                "ben & jerry's",
                null,
                candidate("Ben's rice"),
                candidate("Ben & Jerry's Cookie Dough"),
            ),
        )
    }

    @Test
    fun `ranking never adds, drops or alters a result`() {
        val input = listOf(
            candidate("A", countries = listOf(nl)),
            candidate("B hagel", scans = 3),
            candidate("C hagel", calculable = false),
            candidate("D"),
        )
        val ranked = SearchResultRanking.rank("hagel", input, nl)
        assertEquals(input.map { it.hit }.toSet(), ranked.toSet())
        assertEquals(input.size, ranked.size)
    }

    @Test
    fun `the device country maps to Open Food Facts' country tag`() {
        assertEquals("en:netherlands", SearchResultRanking.countryTagOf(Locale("nl", "NL")))
        assertEquals("en:netherlands", SearchResultRanking.countryTagOf(Locale("en", "NL")))
        assertEquals("en:united-kingdom", SearchResultRanking.countryTagOf(Locale("en", "GB")))
        assertEquals("en:germany", SearchResultRanking.countryTagOf(Locale.GERMANY))
    }

    @Test
    fun `a locale without a country has no country tag`() {
        assertNull(SearchResultRanking.countryTagOf(Locale("nl")))
    }

    // ---- select: rank, then keep what can show a carbohydrate figure --------------------------

    private fun select(query: String, limit: Int, vararg candidates: SearchResultRanking.Candidate) =
        SearchResultRanking.select(query, candidates.toList(), nl, limit).map { it.name }

    @Test
    fun `results that cannot show a figure are dropped when a calculable full match exists`() {
        assertEquals(
            listOf("Hagelslag puur", "Chocolade melk"),
            select(
                "hagelslag",
                20,
                candidate("Hagelslag melk", calculable = false),
                candidate("Hagelslag puur"),
                candidate("Chocolade melk"),
                candidate("Hagelslag wit", calculable = false),
            ),
        )
    }

    /**
     * Measured on held-out queries: dropping every non-calculable result removed the only products
     * that were actually "Conimex Nasi" or "krentenbollen", and a sambal took the top slot. When no
     * calculable result matches the query, the real product stays — above the calculable guesses.
     */
    @Test
    fun `when no calculable result matches every word, the full matches stay first`() {
        assertEquals(
            listOf("Conimex Nasi Goreng", "Sambal"),
            select(
                "conimex nasi",
                20,
                candidate("Sambal", brand = "Conimex"),
                candidate("Conimex Nasi Goreng", calculable = false),
                candidate("Nasi kruiden", calculable = false),
            ),
        )
    }

    @Test
    fun `when nothing is calculable every result is kept`() {
        assertEquals(
            listOf("Ontbijtkoek", "Koek"),
            select(
                "ontbijtkoek",
                20,
                candidate("Ontbijtkoek", calculable = false),
                candidate("Koek", calculable = false),
            ),
        )
    }

    @Test
    fun `the selection is capped after filtering`() {
        val many = (1..30).map { candidate("Melk $it") }.toTypedArray()
        assertEquals((1..20).map { "Melk $it" }, select("melk", 20, *many))
    }
}
