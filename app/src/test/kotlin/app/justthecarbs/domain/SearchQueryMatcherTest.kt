package app.justthecarbs.domain

import app.justthecarbs.domain.SearchQueryMatcher.Strength.FULL
import app.justthecarbs.domain.SearchQueryMatcher.Strength.STRONG
import app.justthecarbs.domain.SearchQueryMatcher.Strength.WEAK
import app.justthecarbs.domain.SearchQueryMatcher.Subject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SearchQueryMatcherTest {

    private fun subject(name: String, brand: String? = null, vararg otherNames: String) =
        Subject(listOf(name, *otherNames), brand)

    private fun strength(query: String, subject: Subject, page: List<Subject> = listOf(subject)) =
        SearchQueryMatcher(query, page).match(subject).strength

    // ---- folding -------------------------------------------------------------------------------

    private val turkishPairs = listOf(
        "Pınar süt" to "Pinar sut",
        "İçim yoğurt" to "Icim yogurt",
        "Ülker çikolata" to "Ulker cikolata",
        "Şölen" to "Solen",
        "Torku fıstıklı" to "Torku fistikli",
        "Eti Burçak" to "Eti Burcak",
    )

    @Test
    fun `Turkish letters fold to the spelling typed without them`() {
        for ((turkish, ascii) in turkishPairs) {
            assertEquals(turkish, SearchQueryMatcher.fold(ascii), SearchQueryMatcher.fold(turkish))
        }
    }

    @Test
    fun `a Turkish spelling and its plain spelling match each other both ways`() {
        for ((turkish, ascii) in turkishPairs) {
            assertEquals("$ascii -> $turkish", FULL, strength(ascii, subject(turkish)))
            assertEquals("$turkish -> $ascii", FULL, strength(turkish, subject(ascii)))
        }
    }

    /**
     * Under a Turkish default locale, `"I".lowercase(Locale.getDefault())` is the dotless `ı`. Folding
     * must not depend on the device locale, or a Turkish phone would stop matching `PINAR`.
     */
    @Test
    fun `folding does not depend on the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("pinar icim kirmizi", SearchQueryMatcher.fold("PINAR İÇİM KIRMIZI"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `folding ignores case and accents`() {
        assertEquals("calve pindakaas", SearchQueryMatcher.fold("CALVÉ Pindakaas"))
    }

    // ---- query words ---------------------------------------------------------------------------

    @Test
    fun `punctuation and lone letters are not query words`() {
        assertEquals(listOf("ben", "jerry"), SearchQueryMatcher("Ben & Jerry's", emptyList()).words)
    }

    @Test
    fun `a repeated word is counted once`() {
        assertEquals(listOf("cola", "zero"), SearchQueryMatcher("cola cola zero", emptyList()).words)
    }

    @Test
    fun `a query with no words matches everything fully`() {
        assertEquals(FULL, strength("&", subject("Anything")))
    }

    // ---- full matches --------------------------------------------------------------------------

    @Test
    fun `every query word present is a full match`() {
        assertEquals(FULL, strength("krokante pizza", subject("Pizza krokante", "Albert Heijn")))
    }

    @Test
    fun `a query word may be found in the brand`() {
        assertEquals(FULL, strength("calve pindakaas", subject("Pindakaas", "Calvé")))
    }

    @Test
    fun `a query word may be found in any of the result's names`() {
        assertEquals(
            FULL,
            strength("fındık kreması", subject("Hazelnut spread", null, "Fındık Kreması")),
        )
    }

    @Test
    fun `the match counts the query words a result names as whole words`() {
        val matcher = SearchQueryMatcher("kek süt", emptyList())
        assertEquals(2, matcher.match(subject("Sütlü kek")).matchedWords)
        assertEquals(1, matcher.match(subject("Sütlü kek")).wholeWords)
        assertEquals(2, matcher.match(subject("Kek", "Süt")).wholeWords)
        assertEquals(0, matcher.match(subject("Keks Sütlü")).wholeWords)
    }

    // ---- brand words ---------------------------------------------------------------------------

    /** The live "krokante pizza Albert heijn" page, reduced to what decides the question. */
    private val albertHeijnPage = listOf(
        subject("Albert Heijn Heldere Bloemenhoning", "Albert Heijn"),
        subject("Kandijkoek", "Albert Heijn"),
        subject("Pizza krokante", "Albert Heijn"),
        subject("Albert heijn Carrots"),
        subject("Pizza krokant Hawaï", "Albert Heijn"),
        subject("Krokante muesli chocolade", "Gwoon"),
    )

    @Test
    fun `a word the page shows as a brand is a brand word`() {
        assertEquals(
            setOf("albert", "heijn"),
            SearchQueryMatcher("krokante pizza Albert heijn", albertHeijnPage).brandWords,
        )
    }

    @Test
    fun `brand words alone never make a match strong, even without a brand on the record`() {
        val query = "krokante pizza Albert heijn"
        assertEquals(WEAK, strength(query, albertHeijnPage[0], albertHeijnPage))
        assertEquals(WEAK, strength(query, albertHeijnPage[3], albertHeijnPage))
    }

    @Test
    fun `a word the page mostly uses in product names is not a brand word`() {
        val page = listOf(
            subject("Pizza Margherita", "Pizza Hut"),
            subject("Pizza Pepperoni", "Pizza Hut"),
            subject("Pizza funghi", "Dr. Oetker"),
            subject("Pizza salami", "Wagner"),
            subject("Pizza tonno", "Ristorante"),
        )
        assertEquals(setOf("hut"), SearchQueryMatcher("pizza hut", page).brandWords)
    }

    @Test
    fun `a brand word is a whole word of a brand, not part of one`() {
        val page = listOf(
            subject("Alpine milk chocolate", "Milka"),
            subject("Oreo", "Milka"),
            subject("Hazelnut", "Milka"),
        )
        assertEquals(emptySet<String>(), SearchQueryMatcher("milk chocolate bar", page).brandWords)
    }

    @Test
    fun `one brand mention on a page is not enough to call a word a brand`() {
        val page = listOf(subject("Nasi goreng", "Conimex"), subject("Nasi kruiden"))
        assertEquals(emptySet<String>(), SearchQueryMatcher("conimex nasi", page).brandWords)
    }

    // ---- strong and weak partial matches -------------------------------------------------------

    @Test
    fun `most of the words, including a product word, is a strong match`() {
        assertEquals(
            STRONG,
            strength("krokante pizza Albert heijn", albertHeijnPage[4], albertHeijnPage),
        )
    }

    /**
     * Live "Eti Popkek" page (2026-09-17), reduced: three of the Popkek records were entered with no
     * brand at all, and the page is full of other Eti products.
     */
    private val popkekPage = listOf(
        subject("Popkek"),
        subject("popkek portakal", "Eti"),
        subject("Eti Cin", "Eti"),
        subject("Eti Browni Gold", "Eti"),
        subject("Gong original", "Eti"),
    )

    @Test
    fun `every product word present is a strong match even without the brand`() {
        assertEquals(setOf("eti"), SearchQueryMatcher("Eti Popkek", popkekPage).brandWords)
        assertEquals(STRONG, strength("Eti Popkek", popkekPage[0], popkekPage))
        assertEquals(FULL, strength("Eti Popkek", popkekPage[1], popkekPage))
        assertEquals(WEAK, strength("Eti Popkek", popkekPage[3], popkekPage))
    }

    @Test
    fun `a query made only of brand words matches that brand fully`() {
        val page = listOf(subject("Kandijkoek", "Albert Heijn"), subject("Hummus", "Albert Heijn"))
        assertEquals(setOf("albert", "heijn"), SearchQueryMatcher("albert heijn", page).brandWords)
        assertEquals(FULL, strength("albert heijn", page[0], page))
    }

    @Test
    fun `a match on half the words is weak`() {
        assertEquals(
            WEAK,
            strength("krokante pizza Albert heijn", albertHeijnPage[5], albertHeijnPage),
        )
        assertEquals(WEAK, strength("hagelslag puur", subject("Melk hagelslag")))
    }

    @Test
    fun `nothing in common is weak`() {
        assertEquals(WEAK, strength("hagelslag", subject("Chocolade melk")))
    }

    /** Dutch declines `krokant` to `krokante`; Turkish adds `-lı` to `fıstık`. */
    @Test
    fun `an inflected form counts toward a strong match`() {
        assertEquals(STRONG, strength("krokante pizza", subject("Pizza krokant Hawaï")))
        assertEquals(STRONG, strength("fıstıklı çikolata", subject("Fıstık çikolata")))
    }

    @Test
    fun `an inflected form never makes a full match`() {
        assertEquals(STRONG, strength("krokante pizza", subject("Krokant pizza")))
    }

    @Test
    fun `a short word has no inflected form`() {
        // `pasta` would otherwise reach `pastırma`, a cured meat.
        assertEquals(WEAK, strength("pasta sos", subject("Pastırma", "Sos")))
    }

    @Test
    fun `the match reports how many words it found and how many describe the product`() {
        val match = SearchQueryMatcher("krokante pizza Albert heijn", albertHeijnPage)
            .match(albertHeijnPage[4])
        assertEquals(4, match.matchedWords)
        assertEquals(2, match.productWords)
    }
}
