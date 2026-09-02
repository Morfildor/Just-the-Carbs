package app.justthecarbs.ocr

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins where a column's x-anchor comes from (P1-2).
 *
 * ### The defect
 *
 * [ColumnClassifier] anchored a column on the union of the **whole matched header span**. On the
 * Sondey biscuit pack (`docs/Scan evidence 31-08-26/20260831-135943-434`) the per-100 header is
 * printed as a long multilingual phrase, so the span ran from `Voedingswaarde/Valeur` at x=172 to
 * `100g` at x=1181 and the anchor landed at their midpoint, **676.5**.
 *
 * The basis token itself sits at `[1070,1370,1181,1412]`, centre **1125.5**, and the value it heads
 * — `72,0` at `[1066,1902,1150,1944]` — has centre **1108.0**. So the correct anchor is 17.5 px from
 * its value while the one actually used was 432 px away, and the interpreter reported
 * `Total-carbohydrate row found but no usable per-100 cell` on a clean, well-lit, square-on table.
 *
 * ### The rule
 *
 * A column is anchored on the **basis token** — the `100g` / `100 ml` / `portie` that made the span
 * match — not on the bounding box of every word around it. A leading noun sits in the label column
 * and says nothing about where the values are; the basis token is printed over them.
 */
class ColumnAnchorTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = top / 50)

    /** Same shape as [ColumnClassifierTest]'s helper, so both suites describe a row identically. */
    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    /** The Sondey header row, verbatim from `recognized.txt`, in printed order. */
    private fun sondeyHeaderRow() = row(
        element("Voedingswaarde/Valeur", 172, 1363, 684, 1416),
        element("nutritionnelle/", 696, 1363, 998, 1416),
        element("100g", 1070, 1370, 1181, 1412),
        element("portie/", 1353, 1375, 1486, 1416),
    )

    private fun columnsOf(row: LogicalRow) = ColumnClassifier.classify(listOf(row), documentWidth = 1684)

    // ---------------------------------------------------------------------------------------

    /**
     * The load-bearing assertion. 1125.5 is the `100g` token's own centre; 676.5 is the span
     * midpoint the defect produced.
     */
    @Test
    fun `a per-100 column is anchored on its basis token, not on the whole header span`() {
        val perHundred = columnsOf(sondeyHeaderRow())
            .single { it.kind == NutritionColumnKind.PER_100_G }

        assertEquals(1125.5, perHundred.centerX, 0.5)
    }

    /**
     * States the consequence directly, so a future change that moves the anchor without breaking the
     * literal above still fails: the anchor must sit nearer the value it heads than to anything else.
     */
    @Test
    fun `the per-100 anchor lands on the value column it heads`() {
        val perHundred = columnsOf(sondeyHeaderRow())
            .single { it.kind == NutritionColumnKind.PER_100_G }

        // '72,0' [1066,1902,1150,1944] -> centre 1108.0
        val valueCentre = (1066 + 1150) / 2.0
        assertTrue(
            "anchor ${perHundred.centerX} should be within one cell width of the value at $valueCentre",
            abs(perHundred.centerX - valueCentre) < 60.0,
        )
    }

    /**
     * The serving column is anchored on `portie/`, which is the only real per-serving header on the
     * row. The defect additionally emitted a **second** PER_SERVING column at x=741.0, invented from
     * a merged span that had swept up the neighbouring label text — a junk anchor sitting in the
     * middle of the label column, where no value is printed at all.
     */
    @Test
    fun `the serving column is anchored on its own header token and is not duplicated`() {
        val serving = columnsOf(sondeyHeaderRow())
            .filter { it.kind == NutritionColumnKind.PER_SERVING }

        assertEquals("exactly one serving column", 1, serving.size)
        // 'portie/' [1353,1375,1486,1416] -> centre 1419.5
        assertEquals(1419.5, serving.single().centerX, 0.5)
    }

    /** '22,5' [1386,1899,1465,1941] -> centre 1425.5, which the 1419.5 anchor sits 6 px from. */
    @Test
    fun `the serving anchor lands on the serving value column`() {
        val serving = columnsOf(sondeyHeaderRow())
            .single { it.kind == NutritionColumnKind.PER_SERVING }

        val valueCentre = (1386 + 1465) / 2.0
        assertTrue(
            "anchor ${serving.centerX} should be within one cell width of $valueCentre",
            abs(serving.centerX - valueCentre) < 60.0,
        )
    }

    /** Both columns are found, and they are distinct positions — not one merged span. */
    @Test
    fun `the sondey header yields exactly one per-100 and one serving column`() {
        val kinds = columnsOf(sondeyHeaderRow()).map { it.kind }

        assertEquals(1, kinds.count { it == NutritionColumnKind.PER_100_G })
        assertEquals(1, kinds.count { it == NutritionColumnKind.PER_SERVING })
    }

    // ---------------------------------------------------------------------------------------
    // Shapes that must keep working.
    // ---------------------------------------------------------------------------------------

    /**
     * A two-word basis phrase anchors on the phrase, not on its last word: `100` and `ml` are one
     * printed quantity and the column sits over both. Fanta's header, from
     * `20260831-140132-710`: `'100' [891,1452,963,1517]` + `'ml' [982,1458,1022,1521]`.
     */
    @Test
    fun `a multi-element basis phrase anchors on the whole phrase`() {
        val row = row(
                element("100", 891, 1452, 963, 1517),
                element("ml", 982, 1458, 1022, 1521),
        )

        val column = columnsOf(row).single { it.kind == NutritionColumnKind.PER_100_ML }

        // Union of the two tokens: (891 + 1022) / 2 = 956.5
        assertEquals(956.5, column.centerX, 0.5)
    }

    /**
     * `per 100 g` keeps anchoring across the whole basis phrase rather than collapsing onto `g`.
     * The connective is part of how the basis is printed and sits over the same column.
     */
    @Test
    fun `a connective-led basis phrase anchors across the phrase`() {
        val row = row(
                element("per", 400, 100, 460, 140),
                element("100", 470, 100, 540, 140),
                element("g", 550, 100, 575, 140),
        )

        val column = columnsOf(row).single { it.kind == NutritionColumnKind.PER_100_G }

        assertEquals((400 + 575) / 2.0, column.centerX, 0.5)
    }

    /**
     * Two per-100 columns on one row stay two columns at their own positions — the case the greedy
     * span walk already handled, which must not regress.
     */
    @Test
    fun `two per-100 headers on one row stay two separate columns`() {
        val row = row(
                element("per", 300, 100, 360, 140),
                element("100", 370, 100, 440, 140),
                element("g", 450, 100, 475, 140),
                element("per", 800, 100, 860, 140),
                element("100", 870, 100, 940, 140),
                element("ml", 950, 100, 1000, 140),
        )

        val columns = columnsOf(row)

        assertEquals(1, columns.count { it.kind == NutritionColumnKind.PER_100_G })
        assertEquals(1, columns.count { it == columns.first { c -> c.kind == NutritionColumnKind.PER_100_ML } })
        val grams = columns.single { it.kind == NutritionColumnKind.PER_100_G }
        val millilitres = columns.single { it.kind == NutritionColumnKind.PER_100_ML }
        assertTrue("the two columns must not share an anchor", abs(grams.centerX - millilitres.centerX) > 300)
    }

    /** A bare `%` keeps its own column at its own position — the Kinder regression already pinned. */
    @Test
    fun `a bare percent header keeps its own anchor`() {
        val row = row(
                element("per", 300, 100, 360, 140),
                element("stuk", 370, 100, 450, 140),
                element("%", 900, 100, 930, 140),
        )

        val columns = columnsOf(row)

        val percent = columns.single { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
        assertEquals(915.0, percent.centerX, 0.5)
        assertEquals(1, columns.count { it.kind == NutritionColumnKind.PER_SERVING })
    }
}
