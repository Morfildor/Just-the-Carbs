package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal

/**
 * Local hits and a remote page, as one list.
 *
 * The claim these tests pin is that **the remote block is never re-ranked**. Search-a-licious ranks
 * with information this app never receives — localized name matching, per-record popularity, its own
 * relevance score — and none of it survives into [ProductSearchHit]. Anything this merge did to that
 * order would be re-ordering on strictly less evidence than produced it.
 */
class SavedProductMergeTest {

    private val limit = 20

    /**
     * These cases build [ProductSearchHit]s directly rather than going through
     * [SavedProductSearch.searchLocal], because what they pin is the merge's *arrangement* rules —
     * order, dedupe, leading/trailing, the limit — over inputs chosen to make each rule visible.
     *
     * So the strength each hit is offered at is derived here, from the hit's own name, which is
     * exactly what these tests meant before strength became a carried value. The renamed-product
     * case that carrying exists for lives in `SavedProductMergeStabilityTest`, where the two names
     * genuinely differ; here they cannot, so this helper is faithful rather than a weakening.
     */
    private fun asMatches(query: String, hits: List<ProductSearchHit>): List<SavedProductSearch.LocalMatch> {
        if (hits.isEmpty()) return emptyList()
        val subjects = hits.map { SearchQueryMatcher.Subject(listOf(it.name), it.brand) }
        val matcher = SearchQueryMatcher(query.trim(), subjects)
        return hits.mapIndexed { i, hit -> SavedProductSearch.LocalMatch(hit, matcher.match(subjects[i]).strength) }
    }

    private fun hit(
        barcode: String,
        name: String = "Product $barcode",
        brand: String? = null,
        carbs: String = "10",
    ) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = null,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    // ---- the empty cases ------------------------------------------------------------------------

    @Test
    fun `no local hits leaves the remote result and its order exactly unchanged`() {
        // The most important regression guard in this file: with nothing saved — every install's
        // first search, and the state every existing search test runs in — the merge must be the
        // identity function on the remote page.
        val remote = listOf(hit("1"), hit("2"), hit("3"))
        assertEquals(remote, SavedProductSearch.merge(asMatches("gouda", emptyList()), remote, limit))
    }

    @Test
    fun `no remote hits leaves the local list as it is`() {
        val local = listOf(hit("1"), hit("2"))
        assertEquals(local, SavedProductSearch.merge(asMatches("gouda", local), emptyList(), limit))
    }

    @Test
    fun `both empty produces nothing`() {
        assertEquals(emptyList<ProductSearchHit>(), SavedProductSearch.merge(asMatches("gouda", emptyList()), emptyList(), limit))
    }

    // ---- order ----------------------------------------------------------------------------------

    @Test
    fun `the remote block keeps its exact relative order`() {
        // Deliberately in an order no local rule would produce: reverse-alphabetical names with the
        // lowest-carb record last. Anything sorting this block would disturb it.
        val remote = listOf(
            hit("r1", name = "Zwaluw", carbs = "90"),
            hit("r2", name = "Appel", carbs = "5"),
            hit("r3", name = "Mango", carbs = "40"),
        )
        val local = listOf(hit("l1", name = "Gouda jong"))
        val merged = SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit)
        assertEquals(listOf("r1", "r2", "r3"), merged.filter { it.barcode.startsWith("r") }.map { it.barcode })
    }

    @Test
    fun `a full local match leads the remote block`() {
        val local = listOf(hit("l1", name = "Gouda jong"))
        val remote = listOf(hit("r1", name = "Gouda belegen"))
        assertEquals(listOf("l1", "r1"), SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit).map { it.barcode })
    }

    @Test
    fun `a partial local match is appended after the remote block`() {
        // "Gouda jong" does not contain "belegen", so it is strong-but-partial and has no claim to
        // outrank a remote result that may well be the better answer.
        val local = listOf(hit("l1", name = "Gouda jong"))
        val remote = listOf(hit("r1", name = "Gouda belegen extra"))
        assertEquals(
            listOf("r1", "l1"),
            SavedProductSearch.merge(asMatches("gouda belegen", local), remote, limit).map { it.barcode },
        )
    }

    @Test
    fun `a weak local match never leads clearly better remote results`() {
        val local = listOf(hit("l1", name = "Hagelslag melk", brand = "De Ruijter"))
        val remote = listOf(hit("r1", name = "Gouda jong belegen"))
        assertEquals(
            listOf("r1", "l1"),
            SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit).map { it.barcode },
        )
    }

    // ---- duplicates -----------------------------------------------------------------------------

    @Test
    fun `a product appears once when both sources return it`() {
        val local = listOf(hit("shared", name = "Gouda jong"))
        val remote = listOf(hit("shared", name = "Gouda jong"), hit("r2"))
        val merged = SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit)
        assertEquals(1, merged.count { it.barcode == "shared" })
    }

    @Test
    fun `a duplicate uses the local payload`() {
        // Tapping the row runs `ProductRepository.lookup`, which is local-first — so showing the
        // remote figure here would show a number the very next screen contradicts.
        val localCopy = hit("shared", name = "Gouda jong", carbs = "2")
        val remoteCopy = hit("shared", name = "Gouda Young Cheese", carbs = "999")
        val merged = SavedProductSearch.merge(asMatches("gouda jong", listOf(localCopy)), listOf(remoteCopy), limit)
        assertSame(localCopy, merged.single { it.barcode == "shared" })
    }

    @Test
    fun `a duplicated partial local match takes the remote position rather than the tail`() {
        // It is in the remote block, so the provider's own placement for it stands; the local
        // payload replaces only the contents.
        val local = listOf(hit("shared", name = "Gouda jong", carbs = "2"))
        val remote = listOf(hit("r1"), hit("shared", name = "Gouda Young", carbs = "9"), hit("r3"))
        val merged = SavedProductSearch.merge(asMatches("gouda belegen", local), remote, limit)
        assertEquals(listOf("r1", "shared", "r3"), merged.map { it.barcode })
        assertEquals(BigDecimal("2"), merged[1].carbsPer100)
    }

    @Test
    fun `a leading local match is not repeated inside the remote block`() {
        val local = listOf(hit("shared", name = "Gouda jong", carbs = "2"))
        val remote = listOf(hit("shared", name = "Gouda jong", carbs = "9"), hit("r2"))
        assertEquals(
            listOf("shared", "r2"),
            SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit).map { it.barcode },
        )
    }

    // ---- survival and bounds --------------------------------------------------------------------

    @Test
    fun `remaining useful local hits survive the merge`() {
        val local = listOf(hit("l1", name = "Gouda jong"), hit("l2", name = "Gouda jong extra"))
        val remote = listOf(hit("r1"), hit("r2"))
        val merged = SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit).map { it.barcode }
        assertEquals(setOf("l1", "l2", "r1", "r2"), merged.toSet())
    }

    @Test
    fun `the result limit is respected`() {
        val local = (1..5).map { hit("l$it", name = "Gouda jong $it") }
        val remote = (1..30).map { hit("r$it") }
        assertEquals(10, SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit = 10).size)
    }

    @Test
    fun `a leading local match survives a limit that truncates the remote block`() {
        // The limit trims from the end, so what the user typed the name of is what survives.
        val local = listOf(hit("l1", name = "Gouda jong"))
        val remote = (1..30).map { hit("r$it") }
        val merged = SavedProductSearch.merge(asMatches("gouda jong", local), remote, limit = 3)
        assertEquals(listOf("l1", "r1", "r2"), merged.map { it.barcode })
    }
}
