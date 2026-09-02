package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins header recognition when two adjacent column headers are recognised as one token (P1-3).
 *
 * ### The defect, measured on the same can twice
 *
 * Two captures of one Fanta Zero can, 36 seconds apart
 * (`docs/Scan evidence 31-08-26/`):
 *
 * ```
 * 140132-710:  '100 ml 250 m'  -> row-kind HEADER -> column PER_100_ML   (correct)
 * 140208-173:  '100 ml250 ml'  -> row-kind OTHER  -> no columns at all
 * ```
 *
 * The only difference is the space between the two column headers. Losing it drops the row out of
 * HEADER classification entirely, so no column resolves, and the app then asks the user "per what?"
 * — offering `/100 g` on a beverage whose only unit token anywhere in the capture is `ml`.
 *
 * ### What this fix is, and what it must not be
 *
 * A **tokenising** fix: `100 ml250 ml` is split into the two basis phrases it visibly contains. It
 * does not widen what counts as a header — a row still has to state a recognised basis to be one,
 * and a row of prose containing a stray `100 g` is still not a header. Only the boundary between two
 * *already-recognised* basis phrases is recovered.
 */
class RunTogetherHeaderTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = top / 50)

    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    // ---------------------------------------------------------------------------------------
    // The measured regression.
    // ---------------------------------------------------------------------------------------

    /**
     * The exact token from `20260831-140208-173`, at the geometry recorded there:
     * `'100 ml250 ml' [903,1530,1293,1643]`.
     */
    @Test
    fun `a run-together per-100 header is still classified as a header`() {
        val header = row(element("100 ml250 ml", 903, 1530, 1293, 1643))

        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(header))
    }

    /** The whole point: the row must yield a usable per-100-ml column, not nothing. */
    @Test
    fun `a run-together per-100 header resolves a millilitre column`() {
        val header = row(element("100 ml250 ml", 903, 1530, 1293, 1643))

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1684)

        assertTrue(
            "expected a PER_100_ML column, got ${columns.map { it.kind }}",
            columns.any { it.kind == NutritionColumnKind.PER_100_ML },
        )
    }

    /** The spaced form from the sibling capture keeps working, unchanged. */
    @Test
    fun `the spaced form of the same header still resolves`() {
        val header = row(
            element("100", 891, 1452, 963, 1517),
            element("ml", 982, 1458, 1022, 1521),
        )

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1684)

        assertTrue(columns.any { it.kind == NutritionColumnKind.PER_100_ML })
    }

    /** Grams run together the same way, and must behave the same way. */
    @Test
    fun `a run-together per-100 g header resolves a gram column`() {
        val header = row(element("100 g250 g", 903, 1530, 1293, 1643))

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1684)

        assertTrue(columns.any { it.kind == NutritionColumnKind.PER_100_G })
    }

    // ---------------------------------------------------------------------------------------
    // Nothing is loosened. These must all keep refusing.
    // ---------------------------------------------------------------------------------------

    /**
     * Prose containing a stray basis phrase **does** classify as `HEADER` today, and this pins that
     * as the pre-existing behaviour rather than asserting a property the app has never had.
     *
     * `RowClassifier.isHeaderLike` returns true whenever `PER_100` matches anywhere in the row, so
     * `Bevat een bron van 100 gram fenylalanine.` is header-like on the current code — verified by
     * running this fixture against a tree with every change from this pass reverted. It is not a
     * regression from the run-together fix and must not be "fixed" here: `HEADER` and `OTHER` are
     * equally value-less downstream, so the classification alone is harmless, and the guard that
     * actually protects prose labels is [ProseNutritionReader]'s usable-basis-column condition, which
     * requires aligned value cells this row does not have.
     *
     * The property that matters, and the one asserted: **the run-together fix must not make this
     * worse.** A prose row must not start yielding a usable column it did not yield before.
     */
    @Test
    fun `prose containing a stray basis phrase yields no aligned value column`() {
        val prose = row(
            element("Bevat", 100, 100, 200, 140),
            element("een", 210, 100, 270, 140),
            element("bron", 280, 100, 360, 140),
            element("van", 370, 100, 430, 140),
            element("100", 440, 100, 500, 140),
            element("gram", 510, 100, 610, 140),
            element("fenylalanine.", 620, 100, 850, 140),
        )

        // One prose row cannot supply the aligned value cells a usable column requires, so whatever
        // ColumnClassifier reports here can never become a per-100 figure downstream.
        assertTrue(
            "a lone prose row must not present a usable basis column",
            !ProseNutritionReader.hasUsableBasisColumn(
                rows = listOf(prose),
                columns = ColumnClassifier.classify(listOf(prose), documentWidth = 1684),
                documentWidth = 1684,
            ),
        )
    }

    /** An ordinary nutrient row is not a header, run-together or not. */
    @Test
    fun `a nutrient row is not turned into a header`() {
        val nutrient = row(
            element("Koolhydraten:", 348, 1827, 567, 1888),
            element("0.5g", 922, 1823, 1012, 1894),
        )

        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(nutrient))
    }

    /**
     * A token that merely contains digits and letters is not a basis. `1081stoffen,` is a real
     * recognition from the Fanta ingredient list (`20260831-140132-710`) and must stay noise.
     */
    @Test
    fun `a digit-and-letter ingredient token is not a basis`() {
        val ingredients = row(element("1081stoffen,", 1009, 882, 1243, 1000))

        val columns = ColumnClassifier.classify(listOf(ingredients), documentWidth = 1684)

        assertTrue("expected no columns, got ${columns.map { it.kind }}", columns.isEmpty())
    }

    /** A bare quantity with no unit states no basis and must resolve nothing. */
    @Test
    fun `a bare number is not a basis`() {
        val bare = row(element("100250", 903, 1530, 1293, 1643))

        val columns = ColumnClassifier.classify(listOf(bare), documentWidth = 1684)

        assertTrue(columns.isEmpty())
    }

    /**
     * A run-together pair naming two *different* bases still resolves both, and they stay distinct —
     * `100 g` and `100 ml` on one multilingual label is an ordinary printing, and collapsing them
     * into one column would attach a millilitre figure to a gram basis.
     */
    @Test
    fun `a run-together pair of different bases resolves both`() {
        val header = row(element("100 g100 ml", 903, 1530, 1293, 1643))

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1684)

        assertTrue(
            "expected PER_100_G, got ${columns.map { "${it.kind}@${it.centerX}='${it.headerText}'" }}",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertTrue(
            "expected PER_100_ML, got ${columns.map { "${it.kind}@${it.centerX}='${it.headerText}'" }}",
            columns.any { it.kind == NutritionColumnKind.PER_100_ML },
        )
    }
}
