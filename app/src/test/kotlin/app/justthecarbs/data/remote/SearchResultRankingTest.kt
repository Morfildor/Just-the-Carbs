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
        otherNames: List<String> = emptyList(),
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
        otherNames = otherNames,
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

    /**
     * The 2026-09-16 rule dropped a full match without a figure whenever one with a figure existed,
     * so the top of the list always showed a number. Such matches are now kept, after the ones with
     * a figure, so that top stays as it was.
     */
    @Test
    fun `among full matches a figure comes before the device country`() {
        assertEquals(
            listOf("Nutella & go", "Nutella jar"),
            rank(
                "nutella",
                nl,
                candidate("Nutella jar", countries = listOf(nl), calculable = false),
                candidate("Nutella & go", countries = listOf("en:united-states")),
            ),
        )
    }

    /**
     * Measured on the Turkish benchmark: `kek` (cake) matches inside German *Keks* (biscuit), and a
     * Keks with a figure then led the list above every real kek without one.
     */
    @Test
    fun `among full matches a whole word comes before one inside a longer word`() {
        assertEquals(
            listOf("Kek", "Hafer Keks"),
            rank(
                "kek",
                "en:turkey",
                candidate("Hafer Keks", countries = listOf("en:turkey"), scans = 900),
                candidate("Kek", calculable = false),
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

    // ---- strength before everything else ------------------------------------------------------

    /**
     * The live "krokante pizza Albert heijn" page (2026-09-17), reduced. The service padded its answer
     * with Albert Heijn products that share nothing but the brand, and the old rule kept every one
     * that carried a figure. Albert Heijn's own pizzas often lack a printed quantity, so they carried
     * none.
     */
    private val albertHeijnPage = arrayOf(
        candidate("Albert Heijn Heldere Bloemenhoning", brand = "Albert Heijn"),
        candidate("Pizza krokante", brand = "Albert Heijn", calculable = false),
        candidate("Kandijkoek", brand = "Albert Heijn"),
        candidate("Pizza krokant Hawaï", brand = "Albert Heijn"),
        candidate("Albert heijn Carrots"),
        candidate("Krokante muesli chocolade", brand = "Gwoon"),
    )

    @Test
    fun `a full match without a figure ranks above a brand-only match with one`() {
        val ranked = rank("krokante pizza Albert heijn", nl, *albertHeijnPage)
        assertEquals(listOf("Pizza krokante", "Pizza krokant Hawaï"), ranked.take(2))
    }

    @Test
    fun `a strong partial match ranks by how much of the product it names, before its figure`() {
        assertEquals(
            listOf("Pizza krokant Hawaï", "Krokante muesli", "Pizza salami"),
            rank(
                "krokante pizza Albert heijn",
                nl,
                candidate("Krokante muesli", brand = "Albert Heijn"),
                candidate("Pizza salami", brand = "Albert Heijn"),
                candidate("Pizza krokant Hawaï", brand = "Albert Heijn", calculable = false),
            ),
        )
    }

    @Test
    fun `among equally strong partial matches a figure comes first`() {
        assertEquals(
            listOf("Pizza salami", "Pizza tonno"),
            rank(
                "krokante pizza Albert heijn",
                nl,
                candidate("Pizza tonno", brand = "Albert Heijn", calculable = false),
                candidate("Pizza salami", brand = "Albert Heijn"),
            ),
        )
    }

    @Test
    fun `the device country does not reorder partial matches`() {
        assertEquals(
            listOf("Pizza tonno", "Pizza salami"),
            rank(
                "krokante pizza Albert heijn",
                nl,
                candidate("Pizza tonno", brand = "Albert Heijn"),
                candidate("Pizza salami", brand = "Albert Heijn", countries = listOf(nl)),
            ),
        )
    }

    @Test
    fun `a result's other names count toward its match`() {
        assertEquals(
            listOf("Nutella", "Kraker"),
            rank(
                "fındık kreması",
                null,
                candidate("Kraker"),
                candidate("Nutella", otherNames = listOf("Fındık Kreması")),
            ),
        )
    }

    @Test
    fun `a plain spelling finds the Turkish one`() {
        assertEquals(
            listOf("Pınar Süt", "Sut"),
            rank("Pinar sut", null, candidate("Sut", brand = "Migros"), candidate("Pınar Süt", brand = "Pınar")),
        )
    }

    // ---- select: rank, then stop once the matches run out -------------------------------------

    private fun select(query: String, limit: Int, vararg candidates: SearchResultRanking.Candidate) =
        SearchResultRanking.select(query, candidates.toList(), nl, limit).map { it.name }

    @Test
    fun `brand-only and unrelated results are not shown beside real matches`() {
        assertEquals(
            listOf("Pizza krokante", "Pizza krokant Hawaï"),
            select("krokante pizza Albert heijn", 20, *albertHeijnPage),
        )
    }

    /**
     * Replaces a rule measured on 2026-09-16 that kept only calculable results once one of them
     * matched every word. It also kept calculable results that matched nothing, and dropped exact
     * matches without a figure — the 2026-09-17 on-device report.
     */
    @Test
    fun `a full match without a figure is kept, after the full matches with one`() {
        assertEquals(
            listOf("Hagelslag puur", "Hagelslag melk", "Hagelslag wit"),
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

    @Test
    fun `a result sharing one of two words is not shown beside a full match`() {
        assertEquals(
            listOf("Conimex Nasi Goreng"),
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
    fun `strong partial matches follow the full matches`() {
        assertEquals(
            listOf("Pizza krokante", "Pizza krokant Hawaï", "Pizza salami"),
            select(
                "krokante pizza Albert heijn",
                20,
                candidate("Pizza salami", brand = "Albert Heijn"),
                candidate("Pizza krokant Hawaï", brand = "Albert Heijn"),
                candidate("Pizza krokante", brand = "Albert Heijn", calculable = false),
            ),
        )
    }

    /** Measured live: "honig macaroni" has no full match; the service's order is all there is. */
    @Test
    fun `weak matches are shown, in the provider's order, when nothing better exists`() {
        assertEquals(
            listOf("Miel", "Macaroni", "Brood"),
            select(
                "honig macaroni",
                20,
                candidate("Miel", scans = 1, calculable = false),
                candidate("Macaroni", scans = 900),
                candidate("Brood", countries = listOf(nl), scans = 5000),
            ),
        )
    }

    @Test
    fun `when nothing is calculable every full match is kept`() {
        assertEquals(
            listOf("Ontbijtkoek naturel", "Ontbijtkoek volkoren"),
            select(
                "ontbijtkoek",
                20,
                candidate("Ontbijtkoek naturel", calculable = false),
                candidate("Ontbijtkoek volkoren", calculable = false),
            ),
        )
    }

    @Test
    fun `the selection is capped after filtering`() {
        val many = (1..30).map { candidate("Melk $it") }.toTypedArray()
        assertEquals((1..20).map { "Melk $it" }, select("melk", 20, *many))
    }
}
