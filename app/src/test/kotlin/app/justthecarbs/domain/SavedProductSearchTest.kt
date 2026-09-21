package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Matching and ranking over the products already on the device.
 *
 * The rule these tests exist to pin is the one that is not a tie-break: **relevance beats
 * favourite**. Every other signal here — the star, recency, the final name/barcode key — only
 * decides between results the text could not separate.
 */
class SavedProductSearchTest {

    private val limit = 20

    private fun saved(
        barcode: String = "8710496979125",
        name: String = "Chocoladehagel puur",
        brand: String? = "De Ruijter",
        favorite: Boolean = false,
        lastUsedAt: Long? = null,
        /** The user's personal name for this product, if they have given it one (1.0.8). */
        localAlias: String? = null,
    ) = SavedProduct(
        barcode = barcode,
        name = name,
        localAlias = localAlias,
        brand = brand,
        carbsPer100 = BigDecimal("67"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
        favorite = favorite,
        lastUsedAt = lastUsedAt,
    )

    private fun namesFor(query: String, vararg products: SavedProduct) =
        SavedProductSearch.search(query, products.toList(), limit).map { it.name }

    // ---- matching -------------------------------------------------------------------------------

    @Test
    fun `a product is found by its stored name`() {
        val hits = SavedProductSearch.search("hagel", listOf(saved()), limit)
        assertEquals(listOf("Chocoladehagel puur"), hits.map { it.name })
    }

    @Test
    fun `a product is found by its brand`() {
        val hits = SavedProductSearch.search("ruijter", listOf(saved()), limit)
        assertEquals(1, hits.size)
        assertEquals("De Ruijter", hits.single().brand)
    }

    @Test
    fun `an unrelated query matches nothing rather than everything`() {
        assertEquals(emptyList<String>(), namesFor("courgette", saved(), saved(barcode = "2", name = "Gouda")))
    }

    @Test
    fun `accents are folded away so a plain query finds an accented name`() {
        val hits = namesFor("cote dor", saved(name = "Côte d'Or", brand = null))
        assertEquals(listOf("Côte d'Or"), hits)
    }

    @Test
    fun `the Turkish dotless i is folded so Pinar finds Pınar`() {
        // The matcher's own rule (`ı` -> `i`), exercised through saved search so the two cannot
        // drift: a saved product must be findable by exactly the text that finds a remote one.
        val hits = namesFor("pinar sut", saved(name = "Pınar Süt", brand = null))
        assertEquals(listOf("Pınar Süt"), hits)
    }

    @Test
    fun `a Turkish query matches its own accented spelling too`() {
        val hits = namesFor("fıstıklı", saved(name = "Torku Fıstıklı Çikolata", brand = null))
        assertEquals(listOf("Torku Fıstıklı Çikolata"), hits)
    }

    @Test
    fun `case is folded in both directions`() {
        assertEquals(listOf("GOUDA JONG"), namesFor("gouda", saved(name = "GOUDA JONG", brand = null)))
    }

    // ---- barcode --------------------------------------------------------------------------------

    @Test
    fun `an exact real barcode identifies its product`() {
        val hits = SavedProductSearch.search(
            "8710496979125",
            listOf(saved(barcode = "2", name = "Gouda"), saved()),
            limit,
        )
        assertEquals("8710496979125", hits.first().barcode)
    }

    @Test
    fun `an exact barcode outranks a better textual match`() {
        // The typed number names one product exactly. Nothing a name can do competes with that.
        val byNumber = saved(barcode = "8710496979125", name = "Zoutjes")
        val byName = saved(barcode = "9999999999999", name = "8710496979125 Special Edition")
        val hits = SavedProductSearch.search("8710496979125", listOf(byName, byNumber), limit)
        assertEquals("Zoutjes", hits.first().name)
    }

    @Test
    fun `a synthetic local key is never matched as barcode text`() {
        // `local:<uuid>` is an identity this app minted, not something a user could have typed. A
        // digit query must not "exactly match" it, whatever those digits are.
        val synthetic = saved(barcode = "${SavedProduct.LOCAL_KEY_PREFIX}123456", name = "Zelfgemaakt")
        assertEquals(emptyList<String>(), namesFor("123456", synthetic))
    }

    @Test
    fun `a synthetic local product is still findable by its name`() {
        // Excluded from barcode matching only. It is a real saved product and a real navigation key.
        val synthetic = saved(barcode = "${SavedProduct.LOCAL_KEY_PREFIX}abc", name = "Zelfgemaakte muesli")
        val hits = SavedProductSearch.search("muesli", listOf(synthetic), limit)
        assertEquals("${SavedProduct.LOCAL_KEY_PREFIX}abc", hits.single().barcode)
    }

    // ---- ordering -------------------------------------------------------------------------------

    @Test
    fun `relevance beats favourite`() {
        // The load-bearing rule. Typing a product's name and being shown a different one because
        // that one is starred is the failure this ordering exists to prevent.
        //
        // The two differ on the *whole-word* count, which is the signal that separates two full
        // matches: "Süt" names `süt` as a whole word, "Sütlü" only contains it. The starred one is
        // the weaker read, and the star must not rescue it.
        val starred = saved(barcode = "1", name = "Pınar Sütlü Çikolata", brand = null, favorite = true)
        val better = saved(barcode = "2", name = "Pınar Süt", brand = null, favorite = false)
        assertEquals(
            listOf("Pınar Süt", "Pınar Sütlü Çikolata"),
            namesFor("pinar sut", starred, better),
        )
    }

    @Test
    fun `a favourite wins only once relevance has genuinely tied`() {
        // The companion to the rule above, stated so the boundary is explicit rather than implied:
        // both names contain both query words as whole words, so nothing about the text separates
        // them and the star is the first signal with anything to say.
        val starred = saved(barcode = "1", name = "Gouda jong belegen extra", brand = null, favorite = true)
        val plain = saved(barcode = "2", name = "Gouda jong", brand = null, favorite = false)
        assertEquals(
            listOf("Gouda jong belegen extra", "Gouda jong"),
            namesFor("gouda jong", starred, plain),
        )
    }

    @Test
    fun `a starred product does not outrank a stronger textual match`() {
        // The same rule at the strength boundary rather than within one strength: a star must not
        // lift a weak match above a strong one, which is where it would do the most damage.
        val starredBrandOnly = saved(barcode = "1", name = "Gouda jong", brand = "De Ruijter", favorite = true)
        val realMatch = saved(barcode = "2", name = "Chocoladehagel puur", brand = "De Ruijter", favorite = false)
        assertEquals(
            listOf("Chocoladehagel puur"),
            namesFor("de ruijter chocoladehagel", starredBrandOnly, realMatch),
        )
    }

    @Test
    fun `favourite breaks a tie between equally relevant products`() {
        val plain = saved(barcode = "1", name = "Gouda jong", brand = null, favorite = false)
        val starred = saved(barcode = "2", name = "Gouda jong", brand = null, favorite = true)
        assertEquals("2", SavedProductSearch.search("gouda jong", listOf(plain, starred), limit).first().barcode)
    }

    @Test
    fun `recency breaks a tie between equally relevant unstarred products`() {
        val older = saved(barcode = "1", name = "Gouda jong", brand = null, lastUsedAt = 1_000)
        val newer = saved(barcode = "2", name = "Gouda jong", brand = null, lastUsedAt = 9_000)
        assertEquals("2", SavedProductSearch.search("gouda jong", listOf(older, newer), limit).first().barcode)
    }

    @Test
    fun `a never-used product sorts below one that has been used`() {
        // A null timestamp is "no evidence", not "used at the epoch".
        val never = saved(barcode = "1", name = "Gouda jong", brand = null, lastUsedAt = null)
        val used = saved(barcode = "2", name = "Gouda jong", brand = null, lastUsedAt = 1)
        assertEquals(
            listOf("2", "1"),
            SavedProductSearch.search("gouda jong", listOf(never, used), limit).map { it.barcode },
        )
    }

    @Test
    fun `ordering is deterministic when every other signal ties`() {
        // Without a final total key the order could differ between the initial local answer and the
        // merge, which reads on screen as the list reshuffling for no reason.
        val a = saved(barcode = "3", name = "Gouda jong", brand = null)
        val b = saved(barcode = "1", name = "Gouda jong", brand = null)
        val c = saved(barcode = "2", name = "Gouda jong", brand = null)
        val once = SavedProductSearch.search("gouda jong", listOf(a, b, c), limit).map { it.barcode }
        val again = SavedProductSearch.search("gouda jong", listOf(c, a, b), limit).map { it.barcode }
        assertEquals(listOf("1", "2", "3"), once)
        assertEquals(once, again)
    }

    @Test
    fun `a weak match is suppressed when a stronger one exists`() {
        // "De Ruijter Gouda" shares only the brand word with a query about hagelslag.
        val brandOnly = saved(barcode = "1", name = "Gouda jong", brand = "De Ruijter")
        val real = saved(barcode = "2", name = "Chocoladehagel puur", brand = "De Ruijter")
        assertEquals(listOf("Chocoladehagel puur"), namesFor("de ruijter chocoladehagel", brandOnly, real))
    }

    @Test
    fun `a weak match is kept when nothing better exists`() {
        val brandOnly = saved(barcode = "1", name = "Gouda jong", brand = "De Ruijter")
        val other = saved(barcode = "2", name = "Hagelslag melk", brand = "De Ruijter")
        // Both match only the brand word pair, so neither is better and both are worth offering.
        assertTrue(namesFor("de ruijter", brandOnly, other).isNotEmpty())
    }

    @Test
    fun `the result limit is respected`() {
        val many = (1..50).map { saved(barcode = "$it", name = "Gouda jong $it", brand = null) }
        assertEquals(5, SavedProductSearch.search("gouda jong", many, limit = 5).size)
    }

    @Test
    fun `a blank query matches nothing`() {
        assertEquals(emptyList<String>(), namesFor("   ", saved()))
    }

    @Test
    fun `an empty store returns nothing`() {
        assertEquals(emptyList<ProductSearchHit>(), SavedProductSearch.search("gouda", emptyList(), limit))
    }

    // ---- the hit's payload ----------------------------------------------------------------------

    @Test
    fun `a saved hit carries its stored figure, basis and identity`() {
        val hit = SavedProductSearch.search("hagel", listOf(saved()), limit).single()
        assertEquals("8710496979125", hit.barcode)
        assertEquals(BigDecimal("67"), hit.carbsPer100)
        assertEquals(NutritionBasis.PER_100_G, hit.basis)
    }

    @Test
    fun `a saved hit states no printed package quantity`() {
        // The store holds a parsed number with its unit discarded, not the text the package printed.
        // Rendering "390" on the card would put a string there the package never showed.
        assertEquals(null, SavedProductSearch.search("hagel", listOf(saved()), limit).single().packageQuantity)
    }
}
