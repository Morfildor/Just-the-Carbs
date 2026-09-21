package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Searching saved products by the name their owner gave them (1.0.8).
 *
 * The feature's claim is that **both** names find the product: the one the user chose and the one
 * the package prints. Only one of those would be a trap — a rename that hid a product from its own
 * name, or one that left it findable only by a name the user has stopped using.
 *
 * Nothing here touches remote search. An alias exists on this device only, so it is a *local*
 * matching signal; the remote provider never sees it and its page is never re-ranked because of it.
 * The tests at the end of this file pin that boundary rather than leaving it as an intention.
 */
class SavedProductAliasSearchTest {

    private val limit = 20

    private fun saved(
        barcode: String = "8710496979125",
        name: String = "AH Volkoren Tarwebrood 800g",
        localAlias: String? = null,
        brand: String? = "Albert Heijn",
        favorite: Boolean = false,
        lastUsedAt: Long? = null,
    ) = SavedProduct(
        barcode = barcode,
        name = name,
        localAlias = localAlias,
        brand = brand,
        carbsPer100 = BigDecimal("41.5"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
        favorite = favorite,
        lastUsedAt = lastUsedAt,
    )

    private fun namesFor(query: String, vararg products: SavedProduct) =
        SavedProductSearch.search(query, products.toList(), limit).map { it.name }

    // ---- matching on either name ------------------------------------------------------------------

    @Test
    fun `an aliased product is found by the alias`() {
        val hits = namesFor("breakfast bread", saved(localAlias = "Breakfast bread"))
        assertEquals(listOf("Breakfast bread"), hits)
    }

    @Test
    fun `an aliased product is still found by its canonical name`() {
        // The half most easily lost. A rename is a nickname, not a replacement, so the name printed
        // on the package must keep working — not least because that is what the user will type when
        // they are standing in a shop holding the thing.
        val hits = namesFor("volkoren tarwebrood", saved(localAlias = "Breakfast bread"))
        assertEquals(listOf("Breakfast bread"), hits)
    }

    @Test
    fun `an aliased product is still found by its brand`() {
        val hits = namesFor("albert heijn", saved(localAlias = "Breakfast bread"))
        assertEquals(listOf("Breakfast bread"), hits)
    }

    @Test
    fun `an aliased product is still found by its exact barcode`() {
        val hits = namesFor("8710496979125", saved(localAlias = "Breakfast bread"))
        assertEquals(listOf("Breakfast bread"), hits)
    }

    @Test
    fun `a product with no alias matches exactly as it did before`() {
        assertEquals(listOf("AH Volkoren Tarwebrood 800g"), namesFor("volkoren", saved()))
        assertEquals(listOf("AH Volkoren Tarwebrood 800g"), namesFor("albert", saved()))
        assertEquals(emptyList<String>(), namesFor("breakfast", saved()))
    }

    @Test
    fun `an alias does not make an unrelated query match`() {
        // Matching gained a name, not a licence. A query matching neither name, the brand nor the
        // barcode still finds nothing.
        assertEquals(emptyList<String>(), namesFor("yoghurt", saved(localAlias = "Breakfast bread")))
    }

    @Test
    fun `a blank alias is ignored for matching`() {
        val product = saved(localAlias = "   ")
        assertEquals(listOf("AH Volkoren Tarwebrood 800g"), namesFor("volkoren", product))
    }

    @Test
    fun `alias matching folds like every other name`() {
        // The same folding the rest of search uses — NFD plus the Turkish dotless i — applies to an
        // alias because it goes through the same matcher, not because anything here restates it.
        val hits = namesFor("pinar sut", saved(name = "Something else", localAlias = "Pınar süt"))
        assertEquals(listOf("Pınar süt"), hits)
    }

    // ---- rendering ---------------------------------------------------------------------------------

    @Test
    fun `a local hit renders the alias as its name`() {
        val hits = SavedProductSearch.search("volkoren", listOf(saved(localAlias = "Breakfast bread")), limit)
        // Found by the canonical name, shown under the user's — the row simply says what this
        // person calls this product. No badge, nothing marking it local.
        assertEquals("Breakfast bread", hits.single().name)
    }

    @Test
    fun `a local hit keeps its brand, figure and identity`() {
        val hits = SavedProductSearch.search("breakfast", listOf(saved(localAlias = "Breakfast bread")), limit)
        val hit = hits.single()
        // Only the displayed name moves. Everything the row is built from and everything tapping it
        // resolves by is untouched by a rename.
        assertEquals("Albert Heijn", hit.brand)
        assertEquals(BigDecimal("41.5"), hit.carbsPer100)
        assertEquals(NutritionBasis.PER_100_G, hit.basis)
        assertEquals("8710496979125", hit.barcode)
    }

    // ---- ranking -----------------------------------------------------------------------------------

    @Test
    fun `an alias equal to the canonical name does not double-count for ranking`() {
        // Two identical names in the subject would inflate the matched-word counts that order one
        // saved product against another, so a rename to a product's own name could quietly move it
        // up the list for a reason no user could see.
        val plain = saved(barcode = "1111111111116", name = "Volkoren brood")
        val self = saved(barcode = "2222222222227", name = "Volkoren brood", localAlias = "Volkoren brood")

        val order = SavedProductSearch.search("volkoren brood", listOf(plain, self), limit).map { it.barcode }
        // A total order keyed on displayName then barcode: identical names leave barcode deciding,
        // and the aliased copy must not have jumped it.
        assertEquals(listOf("1111111111116", "2222222222227"), order)
    }

    @Test
    fun `a better textual match beats a favourite, alias or not`() {
        // The existing rule, restated against an alias: relevance is settled in full before the star
        // is consulted, so renaming something cannot smuggle it above a genuinely better match —
        // nor be smuggled below one.
        //
        // The two names must differ in a signal the matcher actually exposes, which is the trap this
        // codebase has hit before. "Bread" and "Bread rolls white" both contain the query as a whole
        // word, so every relevance signal ties and the star is correctly the first thing with
        // anything to say. Here the starred product has the query only *inside* a longer word
        // ("Breadsticks"), so `wholeWords` genuinely separates them and relevance decides first.
        val starred = saved(barcode = "1111111111116", name = "Breadsticks", brand = null, favorite = true)
        val named = saved(barcode = "2222222222227", name = "Something else", brand = null, localAlias = "Bread")

        val hits = namesFor("bread", starred, named)
        assertEquals(listOf("Bread", "Breadsticks"), hits)
    }

    @Test
    fun `a favourite wins only once relevance has genuinely tied`() {
        // The other side of the same boundary. Both names carry the query as a whole word, so the
        // relevance signals tie honestly and the star is the correct tie-break — an alias is not a
        // relevance bonus, and does not become one.
        val starred = saved(barcode = "1111111111116", name = "Bread rolls white", brand = null, favorite = true)
        val named = saved(barcode = "2222222222227", name = "Something else", brand = null, localAlias = "Bread")

        assertEquals(listOf("Bread rolls white", "Bread"), namesFor("bread", starred, named))
    }

    // ---- the local/remote boundary -------------------------------------------------------------------

    @Test
    fun `merging keeps the remote block in the order it arrived`() {
        val local = SavedProductSearch.searchLocal("breakfast", listOf(saved(localAlias = "Breakfast bread")), limit)
        val remote = listOf(remoteHit("3333333333338"), remoteHit("4444444444449"))

        val merged = SavedProductSearch.merge(local, remote, limit)

        // An alias is evidence about this device, and re-ordering someone else's ranked page on the
        // strength of it would be re-ranking on strictly less evidence than produced it.
        assertEquals(
            listOf("3333333333338", "4444444444449"),
            merged.filter { it.barcode != "8710496979125" }.map { it.barcode },
        )
    }

    @Test
    fun `a saved product appearing remotely is shown once, under the user's name`() {
        val local = SavedProductSearch.searchLocal("breakfast", listOf(saved(localAlias = "Breakfast bread")), limit)
        val remote = listOf(remoteHit("8710496979125", name = "AH Volkoren Tarwebrood 800g"))

        val merged = SavedProductSearch.merge(local, remote, limit)

        assertEquals(1, merged.count { it.barcode == "8710496979125" })
        // The local payload wins the duplicate — as it did before aliases existed — so the name on
        // the row is the one the product screen will show after the tap.
        assertEquals("Breakfast bread", merged.single { it.barcode == "8710496979125" }.name)
    }

    @Test
    fun `a full alias match leads the merged list`() {
        val local = SavedProductSearch.searchLocal("breakfast bread", listOf(saved(localAlias = "Breakfast bread")), limit)
        val remote = listOf(remoteHit("3333333333338"))

        val merged = SavedProductSearch.merge(local, remote, limit)

        // A local hit matching every word was already on top before the network answered; leaving it
        // there is the arrangement in which the list moves least.
        assertEquals("Breakfast bread", merged.first().name)
        assertTrue(merged.map { it.barcode }.contains("3333333333338"))
    }

    private fun remoteHit(barcode: String, name: String = "Remote $barcode") = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = null,
        packageQuantity = null,
        carbsPer100 = BigDecimal("12"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )
}
