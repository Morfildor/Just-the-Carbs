package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtering recognised elements to a user-confirmed rectangle, before row reconstruction.
 *
 * ## Why this stage exists at all
 *
 * The measured failure is not that the parser misreads a nutrition table. It is that on a real
 * package the recognised document contains the table *and* the ingredient list, marketing copy, a
 * best-before date and often a second package's panel — and row reconstruction, which is geometric,
 * merges a table row with whatever prose happens to sit at the same height. Once a total-carbohydrate
 * row has swallowed a sentence, no downstream rule can unpick it.
 *
 * This filter removes that interference using the one signal no algorithm in this repo has been able
 * to derive from the image: a human pointing at the table. Three automatic localisation attempts were
 * measured and rejected (vertical banding dropped the basis header, connected-component clustering
 * had no cross-fixture threshold, re-recognising a crop manufactured a confident-wrong).
 *
 * ## What this class must never become
 *
 * It filters **elements**, never candidates, and it runs **before** reconstruction. It has no notion
 * of a nutrient, a value, or an answer — it cannot prefer a number, because it cannot see one. That
 * is what keeps it outside the safety architecture instead of part of it: the parser still has to
 * find the header, the total row and the column in what survives, under exactly the rules it always
 * applied.
 */
class ElementRegionFilterTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    /** A 1000x1000 document, so a region fraction reads directly as a percentage of the image. */
    private fun document(vararg elements: OcrElement) =
        OcrDocument(width = 1000, height = 1000, elements = elements.toList())

    /**
     * Filters and asserts a usable document came back.
     *
     * The null case is a genuine outcome of [ElementRegionFilter.filter] with its own tests below;
     * these cases are about *what survives*, so a null here is a test failure with a clear message
     * rather than a `!!` throwing an unexplained NPE.
     */
    private fun filtered(document: OcrDocument, region: NormalizedRegion?): OcrDocument =
        ElementRegionFilter.filter(document, region)
            ?: throw AssertionError("expected a filtered document, but the selection retained nothing")

    private val leftHalf = NormalizedRegion(left = 0.0, top = 0.0, right = 0.5, bottom = 1.0)

    // ------------------------------------------------------------------ inclusion and exclusion

    @Test
    fun `an element fully inside the selection is retained`() {
        val inside = element("Koolhydraten", 100, 100, 300, 130)
        val filtered = filtered(document(inside), leftHalf)

        assertEquals(listOf("Koolhydraten"), filtered.elements.map { it.text })
    }

    @Test
    fun `an element fully outside the selection is removed`() {
        val inside = element("Koolhydraten", 100, 100, 300, 130)
        val outside = element("Ingredienten", 700, 100, 900, 130)
        val filtered = filtered(document(inside, outside), leftHalf)

        assertEquals(listOf("Koolhydraten"), filtered.elements.map { it.text })
    }

    @Test
    fun `the document keeps its original dimensions so geometry stays comparable`() {
        // Coordinates are NOT rebased to the selection. Every downstream stage — row reconstruction,
        // slope estimation, column classification — reasons in source-image pixels, and rebasing
        // would silently change the meaning of every threshold expressed in text heights.
        val filtered = filtered(
            document(element("Koolhydraten", 100, 100, 300, 130)),
            leftHalf,
        )

        assertEquals(1000, filtered.width)
        assertEquals(1000, filtered.height)
        assertEquals(OcrBox(100, 100, 300, 130), filtered.elements.single().box)
    }

    // ------------------------------------------------------------------ boundary behaviour

    @Test
    fun `an element straddling the boundary is retained when most of it is inside`() {
        // The selection edge is a human gesture on a preview, not a measured boundary. A word whose
        // tail crosses it is still part of the table the user enclosed.
        val straddling = element("Koolhydraten", 400, 100, 600, 130)
        val filtered = filtered(document(straddling), leftHalf)

        assertEquals(listOf("Koolhydraten"), filtered.elements.map { it.text })
    }

    @Test
    fun `an element only grazing the selection is removed`() {
        // A word from the neighbouring panel that happens to touch the edge must not be admitted;
        // admitting it is how a prose sentence re-enters a table row.
        val grazing = element("Ingredienten", 480, 100, 900, 130)

        // Null rather than an empty document: the grazing element is the only thing recognised, so
        // discarding it leaves the selection retaining nothing at all — the refusal case below.
        assertNull(ElementRegionFilter.filter(document(grazing), leftHalf))
    }

    @Test
    fun `inclusion is decided by the element's own overlap fraction, not absolute overlap area`() {
        // A long prose line and a short table word can overlap the selection by the same number of
        // pixels while meaning completely different things. Normalising by the element's own area is
        // what makes the rule independent of how ML Kit happened to tokenise the text.
        val shortInside = element("46,0", 440, 100, 480, 130)
        val longOutside = element("Ingredienten: tarwebloem, suiker", 460, 200, 990, 230)
        val filtered = filtered(document(shortInside, longOutside), leftHalf)

        assertEquals(listOf("46,0"), filtered.elements.map { it.text })
    }

    // ------------------------------------------------------------------ the real interference shape

    @Test
    fun `a prose panel horizontally adjacent to the table is excluded`() {
        // The measured Kinder shape: a second package's ingredient panel sits beside the table, so
        // reconstructed rows span both panels before any downstream stage sees them.
        val tableRow = element("Koolhydraten", 100, 300, 350, 330)
        val tableValue = element("53,5", 380, 300, 460, 330)
        val adjacentProse = element("suikers", 620, 305, 780, 335)
        val filtered = filtered(
            document(tableRow, tableValue, adjacentProse),
            leftHalf,
        )

        assertEquals(listOf("Koolhydraten", "53,5"), filtered.elements.map { it.text })
    }

    @Test
    fun `a multi-row table inside the selection is retained whole`() {
        // The header band is the part every failed automatic approach lost. Nothing here may treat a
        // header differently from a value row — it survives because the user enclosed it.
        val header = element("per 100 g", 100, 100, 350, 130)
        val fat = element("Vetten", 100, 200, 350, 230)
        val carbs = element("Koolhydraten", 100, 300, 350, 330)
        val sugars = element("waarvan suikers", 100, 400, 350, 430)
        val filtered = filtered(document(header, fat, carbs, sugars), leftHalf)

        assertEquals(
            listOf("per 100 g", "Vetten", "Koolhydraten", "waarvan suikers"),
            filtered.elements.map { it.text },
        )
    }

    // ------------------------------------------------------------------ refusals

    @Test
    fun `a selection retaining nothing yields null rather than an empty document`() {
        // An empty OcrDocument is a valid object that would parse to NotFound, which reads as "the
        // label had no carbohydrate row". That is a different statement from "the selection was
        // unusable", and the caller must be able to tell them apart to keep the Pass A reading.
        val filtered = ElementRegionFilter.filter(
            document(element("Ingredienten", 700, 100, 900, 130)),
            leftHalf,
        )

        assertNull(filtered)
    }

    @Test
    fun `a null selection is a no-op that returns the document unchanged`() {
        val original = document(element("Koolhydraten", 100, 100, 300, 130))

        assertEquals(original, ElementRegionFilter.filter(original, region = null))
    }

    @Test
    fun `a selection covering the whole image returns the document unchanged`() {
        val original = document(
            element("Koolhydraten", 100, 100, 300, 130),
            element("Ingredienten", 700, 100, 900, 130),
        )
        val whole = NormalizedRegion(0.0, 0.0, 1.0, 1.0)

        assertEquals(original, ElementRegionFilter.filter(original, whole))
    }

    // ------------------------------------------------------------------ it only ever removes

    @Test
    fun `filtering never introduces an element that was not recognised`() {
        // The property that keeps this stage incapable of manufacturing a value. Re-recognising a
        // crop broke exactly this: it produced a token, `(9)`, that Pass A never saw.
        val elements = listOf(
            element("per 100 g", 100, 100, 350, 130),
            element("Koolhydraten", 100, 300, 350, 330),
            element("53,5", 380, 300, 460, 330),
            element("Ingredienten", 700, 100, 990, 130),
        )
        val original = OcrDocument(1000, 1000, elements)
        val filtered = ElementRegionFilter.filter(original, leftHalf)

        assertNotNull(filtered)
        assertTrue(
            "every surviving element must be one Pass A produced",
            filtered!!.elements.all { it in original.elements },
        )
    }
}
