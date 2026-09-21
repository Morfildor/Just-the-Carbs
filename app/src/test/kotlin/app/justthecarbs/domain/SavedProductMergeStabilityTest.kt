package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * A local match keeps the strength it was judged at, all the way through the merge (1.0.8).
 *
 * ## The defect this file exists for
 *
 * [SavedProductSearch.search] matches a saved product against **both** of its names — the canonical
 * one the package prints and the alias the user gave it on this device — which is what makes a
 * renamed product findable by either. It then returned a `List<ProductSearchHit>`, whose `name` is
 * the **displayed** name: the alias, when there is one.
 *
 * The merge's leading/trailing split asks one question of each local hit — *was this FULL?* — and
 * the old implementation answered it by re-matching `hit.name`. So for a product renamed away from
 * its canonical name, the merge saw only the alias and could not reproduce the judgement `search`
 * had already made:
 *
 * ```
 *   canonical : AH Volkoren Tarwebrood 800g
 *   alias     : Breakfast bread
 *   query     : volkoren tarwebrood      -> search: FULL (canonical)   merge: WEAK (alias only)
 * ```
 *
 * The user's experience was the list **moving under them**: the product they typed the name of led
 * the local answer, then dropped below the remote block when the network caught up. Nothing was
 * wrong with the product, the query or the remote page — the information needed to place it had
 * been thrown away and guessed at again.
 *
 * ## What the fix is, structurally
 *
 * Strength is *carried*, not recomputed: [SavedProductSearch.searchLocal] returns
 * [SavedProductSearch.LocalMatch], which pairs the hit with the strength already established over
 * every searchable name. The merge reads that field.
 *
 * Note what is deliberately **not** done: [ProductSearchHit] gains no `strength`, no `isLocal` and
 * no source field. That type is the shared shape of a result from any provider, and its whole point
 * is that the screen cannot tell where a row came from. The carrier is internal to local search.
 */
class SavedProductMergeStabilityTest {

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

    private fun remoteHit(barcode: String, name: String = "Remote $barcode") = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = null,
        packageQuantity = null,
        carbsPer100 = BigDecimal("12"),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    // ---- the four leading cases ------------------------------------------------------------------

    @Test
    fun `an alias full match leads the merged list`() {
        val local = SavedProductSearch.searchLocal(
            "breakfast bread",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        val merged = SavedProductSearch.merge(local, listOf(remoteHit("3333333333338")), limit)

        assertEquals("Breakfast bread", merged.first().name)
    }

    @Test
    fun `a canonical-name full match of a renamed product still leads the merged list`() {
        // THE REGRESSION. The product is renamed to something sharing no word with the query, and
        // the query is every word of the canonical name. `search` judges this FULL against the
        // canonical name; a merge re-deriving strength from the row's displayed text sees only
        // "Breakfast bread" against "volkoren tarwebrood" and demotes it.
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        assertEquals(
            "precondition: search must judge this FULL on the canonical name",
            SearchQueryMatcher.Strength.FULL,
            local.single().strength,
        )

        val merged = SavedProductSearch.merge(
            local,
            listOf(remoteHit("3333333333338")),
            limit,
        )

        assertEquals(
            "the product whose canonical name was typed must lead, under the user's own name",
            "Breakfast bread",
            merged.first().name,
        )
        assertEquals("8710496979125", merged.first().barcode)
    }

    @Test
    fun `a folded canonical match of a renamed product still leads`() {
        // The same defect reached through folding rather than through the alias: `Pınar` is matched
        // by a plain `pinar`, and that judgement must survive the merge too. A merge re-matching
        // the displayed name would be asking about "Morning milk" instead.
        val local = SavedProductSearch.searchLocal(
            "pinar sut",
            listOf(saved(barcode = "1111111111116", name = "Pınar Süt", brand = null, localAlias = "Morning milk")),
            limit,
        )
        assertEquals(
            "precondition: folding must make this a FULL canonical match",
            SearchQueryMatcher.Strength.FULL,
            local.single().strength,
        )

        val merged = SavedProductSearch.merge(local, listOf(remoteHit("3333333333338")), limit)

        assertEquals("Morning milk", merged.first().name)
    }

    @Test
    fun `an ordinary product with no alias is unaffected`() {
        // The control. Nothing about a product that was never renamed may change — this is every
        // install that has not used the rename feature, i.e. almost all of them.
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood",
            listOf(saved()),
            limit,
        )
        val merged = SavedProductSearch.merge(local, listOf(remoteHit("3333333333338")), limit)

        assertEquals("AH Volkoren Tarwebrood 800g", merged.first().name)
        assertEquals(listOf("8710496979125", "3333333333338"), merged.map { it.barcode })
    }

    // ---- what must not have moved ------------------------------------------------------------------

    @Test
    fun `the remote block keeps its exact relative order`() {
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        val remote = listOf(
            remoteHit("r1", name = "Zwaluw"),
            remoteHit("r2", name = "Appel"),
            remoteHit("r3", name = "Mango"),
        )

        val merged = SavedProductSearch.merge(local, remote, limit)

        assertEquals(
            listOf("r1", "r2", "r3"),
            merged.filter { it.barcode.startsWith("r") }.map { it.barcode },
        )
    }

    @Test
    fun `a duplicate barcode appears once and keeps the local payload`() {
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        val remote = listOf(remoteHit("8710496979125", name = "AH Volkoren Tarwebrood 800g"))

        val merged = SavedProductSearch.merge(local, remote, limit)

        assertEquals(1, merged.count { it.barcode == "8710496979125" })
        // The saved copy, because `ProductRepository.lookup` is local-first: showing the remote
        // figure would show a number the next screen contradicts.
        val shown = merged.single { it.barcode == "8710496979125" }
        assertEquals("Breakfast bread", shown.name)
        assertEquals(BigDecimal("41.5"), shown.carbsPer100)
    }

    @Test
    fun `a strong but partial local match is still appended rather than leading`() {
        // The trailing half of the split must be unchanged: carrying strength forward must not
        // promote anything that was not already FULL.
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood rogge",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        assertNotEquals(SearchQueryMatcher.Strength.FULL, local.single().strength)

        val merged = SavedProductSearch.merge(
            local,
            listOf(remoteHit("3333333333338")),
            limit,
        )

        assertEquals("3333333333338", merged.first().barcode)
        assertTrue(merged.map { it.barcode }.contains("8710496979125"))
    }

    @Test
    fun `the limit still bounds the merged list`() {
        val local = SavedProductSearch.searchLocal(
            "volkoren tarwebrood",
            listOf(saved(localAlias = "Breakfast bread")),
            limit,
        )
        val remote = (1..30).map { remoteHit("r$it") }

        assertEquals(5, SavedProductSearch.merge(local, remote, limit = 5).size)
    }

    // ---- the negative control, stated as a test --------------------------------------------------

    @Test
    fun `reconstructing strength from the displayed name alone would lose the canonical judgement`() {
        // This is the old implementation, written out: strength re-derived from the hit's own name.
        // It is kept as an executable statement of *why* the carrier exists — if this ever stops
        // disagreeing with `searchLocal`, the two names have stopped differing and the guard above
        // would be passing for a reason that has nothing to do with the fix.
        val product = saved(localAlias = "Breakfast bread")
        val local = SavedProductSearch.searchLocal("volkoren tarwebrood", listOf(product), limit)

        val carried = local.single().strength
        val reconstructed = run {
            val hit = local.single().hit
            val subjects = listOf(SearchQueryMatcher.Subject(listOf(hit.name), hit.brand))
            SearchQueryMatcher("volkoren tarwebrood", subjects).match(subjects.single()).strength
        }

        assertEquals(SearchQueryMatcher.Strength.FULL, carried)
        assertNotEquals(
            "the displayed name alone cannot reproduce the canonical match — which is the whole defect",
            carried,
            reconstructed,
        )
    }
}
