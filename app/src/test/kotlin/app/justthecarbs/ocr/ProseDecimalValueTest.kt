package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how the prose gate handles decimal values, and why the Boursin lid is refused (P1-4).
 *
 * ### A wrong hypothesis, corrected by measurement — read this before "fixing" NUMBER_WORD
 *
 * The P1-4 brief item, and this session's first diagnosis, held that the gate could never accept a
 * decimal value because its number test is:
 *
 * ```
 * NUMBER_WORD = Regex("^\\d{1,4}[a-z]{0,4}$")   // no decimal separator at all
 * ```
 *
 * Tested against raw tokens that is true — `2,5g`, `0.59` and `72,0` all fail it. **The gate never
 * sees raw tokens.** `wordsOf` feeds it `NutritionTerminology.normalize`d text, and `normalize`
 * replaces non-word characters with spaces, so by the time the walk runs:
 *
 * ```
 * '2,5g'  -> "2 5g"  -> words [2, 5g]   both match
 * '3q.'   -> "3q"    -> words [3q]      matches
 * '2,50'  -> "2 50"  -> words [2, 50]   both match
 * ```
 *
 * So decimals were never blocked, and widening `NUMBER_WORD` would change nothing. The tests below
 * measure that directly, so the claim cannot quietly rot back into the brief's version.
 *
 * ### What actually refuses the Boursin lid
 *
 * A declaration must open at a **connective** (`per`, `pour`, `pro`) — `basisPhraseAt` returns null
 * otherwise. The lid prints `PourPerlPro 100g:`, which ML Kit returns as one fused token
 * (`docs/Scan evidence 31-08-26/20260831-140033-054`, element `'PourPerlPro' [171,2001,313,2047]`).
 * That token is not a connective, so **no declaration opens at all** and condition 1 is never
 * reached.
 *
 * This is the limitation `CLAUDE.md` already records as deliberately deferred for real fixture 4,
 * where widening basis phrases to bare nouns is explicitly **not authorized** — fixture 3 is the
 * label on which a wrongly-bound term would be undetectable by value. Nothing here widens it.
 */
class ProseDecimalValueTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = top / 50)

    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    /**
     * One prose declaration in the shape a lid prints, opened by a **connective** so the declaration
     * actually opens. The carbohydrate value is supplied by the caller so one fixture can exercise
     * every number form.
     */
    private fun proseDeclaration(totalValue: String, childValue: String): List<LogicalRow> {
        var x = 100
        fun next(text: String, width: Int): OcrElement {
            val e = element(text, x, 2000, x + width, 2050)
            x += width + 12
            return e
        }
        return listOf(
            row(
                next("per", 90),
                next("100", 60),
                next("g", 30),
                next("Glucides", 170),
                next("/", 20),
                next("Koolhydraten:", 240),
                next(totalValue, 80),
                next("dont", 90),
                next("sucres", 120),
                next("/", 20),
                next("waarvan", 150),
                next("suikers:", 140),
                next(childValue, 80),
            ),
        )
    }

    private fun isProse(rows: List<LogicalRow>): Boolean =
        ProseNutritionReader.isProseLabel(
            rows = rows,
            columns = ColumnClassifier.classify(rows, documentWidth = 1684),
            documentWidth = 1684,
        )

    // ---------------------------------------------------------------------------------------
    // Decimals were never the blocker. These pass on unmodified code and pin that.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a declaration with whole-number values is prose`() {
        assertTrue(isProse(proseDeclaration(totalValue = "3g", childValue = "2g")))
    }

    /** The case the brief predicted would fail. It does not, for the reason in the class KDoc. */
    @Test
    fun `a declaration with comma-decimal values is prose`() {
        assertTrue(isProse(proseDeclaration(totalValue = "2,5g", childValue = "1,6g")))
    }

    @Test
    fun `a declaration with point-decimal values is prose`() {
        assertTrue(isProse(proseDeclaration(totalValue = "2.5g", childValue = "1.6g")))
    }

    @Test
    fun `trailing sentence punctuation does not hide a value`() {
        assertTrue(isProse(proseDeclaration(totalValue = "2,5g.", childValue = "1,6g,")))
    }

    @Test
    fun `a bare decimal value is recognised by the gate`() {
        assertTrue(isProse(proseDeclaration(totalValue = "2,5", childValue = "1,6")))
    }

    /**
     * Directly measures the mechanism, so the class KDoc's explanation cannot drift from behaviour:
     * normalization splits a decimal into two whole-number fragments before the walk ever runs.
     */
    @Test
    fun `normalization splits a decimal value into whole-number fragments`() {
        assertEquals("2 5g", NutritionTerminology.normalize("2,5g"))
        assertEquals("2 5g", NutritionTerminology.normalize("2.5g"))
        assertEquals("3q", NutritionTerminology.normalize("3q."))
        assertEquals("0 59", NutritionTerminology.normalize("0.59"))
    }

    // ---------------------------------------------------------------------------------------
    // What actually refuses the Boursin lid — and it is deliberately left refused.
    // ---------------------------------------------------------------------------------------

    /**
     * The measured blocker, verbatim from the device bundle. A fused `PourPerlPro` opens no
     * declaration, so the label is refused before any number is considered.
     *
     * Left refused on purpose: opening a declaration at a bare noun is the change `CLAUDE.md` records
     * as unauthorized, because it is undetectable-by-value on a label whose total and sugars figures
     * are identical.
     */
    @Test
    fun `a fused basis token opens no declaration, so the label is not prose`() {
        var x = 100
        fun next(text: String, width: Int): OcrElement {
            val e = element(text, x, 2000, x + width, 2050)
            x += width + 12
            return e
        }
        val rows = listOf(
            row(
                next("PourPerlPro", 142),
                next("100g:", 110),
                next("Glucides", 170),
                next("Koolhydraten:", 240),
                next("3q.", 80),
                next("dont", 90),
                next("sucres", 120),
                next("waarvan", 150),
                next("suikers:", 140),
                next("2,50", 80),
            ),
        )

        assertEquals("no declaration may open", 0, ProseNutritionReader.declarationCountForTest(rows))
        assertFalse(isProse(rows))
    }

    /** Replacing only the fused token with a connective is enough — proving the blocker is that token. */
    @Test
    fun `the same declaration opens once its connective is separated`() {
        assertEquals(
            1,
            ProseNutritionReader.declarationCountForTest(
                proseDeclaration(totalValue = "3q.", childValue = "2,50"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Nothing is loosened.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a declaration with no values is not prose`() {
        val rows = listOf(
            row(
                element("per", 100, 2000, 190, 2050),
                element("100", 202, 2000, 262, 2050),
                element("g", 274, 2000, 304, 2050),
                element("Glucides", 316, 2000, 486, 2050),
                element("dont", 498, 2000, 588, 2050),
                element("sucres", 600, 2000, 720, 2050),
            ),
        )

        assertFalse(isProse(rows))
    }

    /**
     * The child clause reaching a value before the total's is the merged-table-row shape, and is
     * still refused whatever the number format.
     */
    @Test
    fun `a child term before the total's value is not prose`() {
        val rows = listOf(
            row(
                element("per", 100, 2000, 190, 2050),
                element("100", 202, 2000, 262, 2050),
                element("g", 274, 2000, 304, 2050),
                element("Koolhydraten:", 316, 2000, 556, 2050),
                element("waarvan", 568, 2000, 718, 2050),
                element("suikers:", 730, 2000, 870, 2050),
                element("2,5g", 882, 2000, 962, 2050),
            ),
        )

        assertFalse(isProse(rows))
    }

    // ---------------------------------------------------------------------------------------
    // The two rules compose.
    // ---------------------------------------------------------------------------------------

    /**
     * Even where the gate does accept a declaration, a corrupted value is still refused — the gate
     * decides whether a label is *read as prose*, [CarbUnitAccompaniment] decides whether a figure is
     * *usable*. The Boursin total would be `3q.` either way.
     */
    @Test
    fun `a prose declaration does not make a corrupted value usable`() {
        assertTrue(isProse(proseDeclaration(totalValue = "3q.", childValue = "2,50")))

        val corrupted = element("3q.", 1445, 2111, 1478, 2154)
        assertFalse(
            "the corrupted value must still be refused",
            CarbUnitAccompaniment.isAccompanied(corrupted, listOf(corrupted)),
        )
    }
}
