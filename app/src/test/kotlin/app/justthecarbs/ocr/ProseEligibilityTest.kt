package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProseEligibilityTest {

    /**
     * Prose: one row reads as a sentence — nutrient, its value, child, its value, in that order,
     * with no usable basis column anywhere in the document.
     */
    @Test
    fun aSentenceCarryingNutrientValueChildValueIsProse() {
        val document = OcrDocument(
            width = 1400,
            height = 200,
            elements = listOf(
                element("Voedingswaarde", 20, 40, 240, 80),
                element("per", 250, 40, 300, 80),
                element("100", 310, 40, 370, 80),
                element("g:", 380, 40, 420, 80),
                element("koolhydraten", 430, 40, 650, 80),
                element("46", 660, 40, 710, 80),
                element("g,", 720, 40, 760, 80),
                element("waarvan", 770, 40, 900, 80),
                element("suikers", 910, 40, 1020, 80),
                element("1,0", 1030, 40, 1090, 80),
                element("g", 1100, 40, 1130, 80),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertTrue(
            "a one-row sentence in nutrient-value-child-value order is prose",
            ProseNutritionReader.isProseLabel(
                rows,
                ColumnClassifier.classify(rows, document.width),
                document.width,
            ),
        )
    }

    /**
     * A table is NOT prose, even a table the parser could not read. The predicate must not be
     * satisfied merely by the table stage having failed.
     */
    @Test
    fun aTableWithNutrientsOnSeparateRowsIsNotProse() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per", 380, 20, 440, 60),
                element("100", 450, 20, 510, 60),
                element("g", 520, 20, 550, 60),
                element("Koolhydraten", 40, 120, 300, 160),
                element("2,3", 400, 120, 470, 160),
                element("g", 480, 120, 510, 160),
                element("waarvan", 40, 220, 200, 260),
                element("suikers", 210, 220, 340, 260),
                element("2,3", 400, 220, 470, 260),
                element("g", 480, 220, 510, 260),
                element("Vezels", 40, 320, 190, 360),
                element("0,0", 400, 320, 470, 360),
                element("g", 480, 320, 510, 360),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertFalse(
            "nutrients on their own rows are a table",
            ProseNutritionReader.isProseLabel(
                rows,
                ColumnClassifier.classify(rows, document.width),
                document.width,
            ),
        )
    }

    /**
     * THE CASE THAT MATTERS. A genuine two-column table whose rows chained during reconstruction, so
     * the carbohydrate row and the sugars row merged into one — exactly the 2026-08-16 tilt bug. The
     * merged row now contains a total term AND a child term, which is why bare co-occurrence is not
     * an admissible predicate. Two things must save it: the values sit in column order (value, value)
     * rather than sentence order (nutrient, value, child, value), and the document has a basis column
     * whose x-position real values are printed under. Either alone is sufficient to refuse; both hold.
     *
     * The fibre row is here so the merged carbohydrate/sugars pair is not the table's only evidence
     * of a column — a real table prints more than two nutrients, and asserting on a two-row table
     * would make the guard depend on a threshold rather than on the structure being present.
     */
    @Test
    fun aMergedTableRowIsNotProse() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per", 380, 20, 440, 60),
                element("100", 450, 20, 510, 60),
                element("g", 520, 20, 550, 60),
                // One reconstructed row: the table's two printed rows chained together.
                element("Koolhydraten", 40, 130, 300, 170),
                element("2,3", 400, 128, 470, 168),
                element("waarvan", 40, 150, 200, 190),
                element("suikers", 210, 152, 340, 192),
                element("2,3", 400, 154, 470, 194),
                // A third nutrient row that did NOT chain, printed under the same value column.
                element("Vezels", 40, 280, 190, 320),
                element("0,0", 400, 280, 470, 320),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        // Assert the REASON, not only the verdict. This table's basis column has real values printed
        // under it, so it is USABLE, so condition 2 refuses — which is precisely the property the
        // 2026-08-17 usability amendment had to preserve. If a future change makes this column
        // "unusable", the merged-table guard has silently eroded and this assertion catches it.
        assertTrue(
            "precondition: a basis column must be resolved at all",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertTrue(
            "precondition: the merged table's basis column must be usable structure",
            ProseNutritionReader.hasUsableBasisColumn(rows, columns, document.width),
        )
        assertFalse(
            "a merged table row must never be mistaken for a sentence",
            ProseNutritionReader.isProseLabel(rows, columns, document.width),
        )
    }

    /**
     * RESOLVED-BUT-UNUSABLE. The basis phrase is inside a running sentence, so `ColumnClassifier`
     * resolves a PER_100_G column for it — but the nutrient values printed elsewhere in the paragraph
     * are not aligned to its x-position. That column is text, not structure, and must not defeat prose
     * eligibility. Every real prose fixture in the corpus has this shape; the un-amended predicate
     * could never fire because of it.
     *
     * The layout is the one the real fixtures produce: a wrapped paragraph whose first line carries
     * the basis phrase and names no nutrient (so it classifies as a HEADER and a column resolves from
     * it), with the nutrients on a later line. Written as a single un-wrapped line the basis phrase
     * shares a row with "waarvan suikers", the row classifies as CARBOHYDRATE_CHILD, and
     * `ColumnClassifier` — which only inspects HEADER rows — resolves no column at all, which would
     * make the test pass without ever exercising the usability rule.
     */
    @Test
    fun aBasisPhraseInRunningTextDoesNotMakeAUsableColumn() {
        val document = OcrDocument(
            width = 1400,
            height = 400,
            elements = listOf(
                element("Voedingswaarde", 20, 40, 240, 80),
                element("per", 250, 40, 300, 80),
                element("100", 310, 40, 370, 80),
                element("g:", 380, 40, 420, 80),
                element("energie", 430, 40, 550, 80),
                element("1312", 560, 40, 640, 80),
                element("kJ,", 650, 40, 700, 80),
                element("koolhydraten", 20, 140, 240, 180),
                element("46", 250, 140, 300, 180),
                element("g,", 310, 140, 350, 180),
                element("waarvan", 360, 140, 490, 180),
                element("suikers", 500, 140, 610, 180),
                element("1,0", 620, 140, 680, 180),
                element("g", 690, 140, 720, 180),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        // The point of the test is that a column IS resolved and eligibility still holds.
        assertTrue(
            "precondition: this document resolves a basis column",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertFalse(
            "precondition: that column must NOT be usable structure",
            ProseNutritionReader.hasUsableBasisColumn(rows, columns, document.width),
        )
        assertTrue(
            "a basis phrase in running text is not usable column structure",
            ProseNutritionReader.isProseLabel(rows, columns, document.width),
        )
    }

    /**
     * RESOLVED-BUT-UNUSABLE, on ingredient prose specifically. "waarvan toegevoegde suikers 0 g per
     * 100 g" is a legal claim about an ingredient, not a nutrition table header, and must never
     * establish a usable basis column.
     */
    @Test
    fun ingredientProseDoesNotEstablishAUsableBasisColumn() {
        val document = OcrDocument(
            width = 1400,
            height = 500,
            elements = listOf(
                element("Ingredienten:", 20, 40, 240, 80),
                element("bloem,", 250, 40, 360, 80),
                element("waarvan", 370, 40, 500, 80),
                element("toegevoegde", 510, 40, 700, 80),
                element("suikers", 710, 40, 820, 80),
                element("0", 830, 40, 860, 80),
                element("g", 870, 40, 900, 80),
                element("per", 910, 40, 960, 80),
                element("100", 970, 40, 1030, 80),
                element("g.", 1040, 40, 1080, 80),
                element("Voedingswaarde", 20, 140, 240, 180),
                element("per", 250, 140, 300, 180),
                element("100", 310, 140, 370, 180),
                element("g:", 380, 140, 420, 180),
                element("energie", 430, 140, 550, 180),
                element("1312", 560, 140, 640, 180),
                element("kJ,", 650, 140, 700, 180),
                element("koolhydraten", 20, 240, 240, 280),
                element("46", 250, 240, 300, 280),
                element("g,", 310, 240, 350, 280),
                element("waarvan", 360, 240, 490, 280),
                element("suikers", 500, 240, 610, 280),
                element("1,0", 620, 240, 680, 280),
                element("g", 690, 240, 720, 280),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        assertTrue(
            "precondition: this document resolves a basis column",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertFalse(
            "precondition: no resolved column may be usable structure here",
            ProseNutritionReader.hasUsableBasisColumn(rows, columns, document.width),
        )
        assertTrue(
            "ingredient prose must not block prose eligibility",
            ProseNutritionReader.isProseLabel(rows, columns, document.width),
        )
    }

    /**
     * THE CONSTRAINT ON THE 2026-08-17 WIDENING. Condition 1 now spans reconstructed rows, because ML
     * Kit wraps a printed sentence wherever the line ends. This is the failure mode that widening
     * could have introduced and must not: all four tokens are present, in correct document order, and
     * would satisfy the sequence if the window were the whole document — but the total clause and the
     * child clause belong to **different declarations**, each opened by its own basis phrase.
     *
     * Two separately-declared per-100 sentences do not become one sentence because they are printed
     * near each other. The declaration boundary is what refuses it, which is exactly why the predicate
     * reuses `read`'s own assembly instead of flattening the document.
     *
     * The precondition assertions are load-bearing: without them this test would still pass if the
     * fixture accidentally produced ONE declaration and failed the sequence for some unrelated reason,
     * and it would then be pinning nothing.
     */
    @Test
    fun aSequenceCompletedAcrossTwoDeclarationsIsNotProse() {
        val document = OcrDocument(
            width = 1400,
            height = 500,
            elements = listOf(
                // Declaration A: opens a basis and states the total, but its child clause never comes.
                element("Voedingswaarde", 20, 40, 240, 80),
                element("per", 250, 40, 300, 80),
                element("100", 310, 40, 370, 80),
                element("g:", 380, 40, 420, 80),
                element("koolhydraten", 430, 40, 650, 80),
                element("46", 660, 40, 710, 80),
                element("g,", 720, 40, 760, 80),
                // Declaration B: a second, independent basis phrase opens here, and the child clause
                // that would have completed A's sequence sits inside it.
                element("Bereid", 20, 140, 140, 180),
                element("per", 150, 140, 200, 180),
                element("100", 210, 140, 270, 180),
                element("g:", 280, 140, 320, 180),
                element("waarvan", 330, 140, 460, 180),
                element("suikers", 470, 140, 580, 180),
                element("1,0", 590, 140, 650, 180),
                element("g", 660, 140, 690, 180),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        assertEquals(
            "precondition: the fixture must really produce TWO declarations, or it tests nothing",
            2,
            ProseNutritionReader.declarationCountForTest(rows),
        )
        assertFalse(
            "precondition: no usable basis column may be doing the refusing here",
            ProseNutritionReader.hasUsableBasisColumn(rows, columns, document.width),
        )
        assertTrue(
            "precondition: all four tokens must be present in correct document order document-wide",
            ProseNutritionReader.documentWideSequenceForTest(rows),
        )
        assertFalse(
            "a sequence completed across two declarations is not a sentence",
            ProseNutritionReader.isProseLabel(rows, columns, document.width),
        )
    }

    @Test
    fun anEmptyDocumentIsNotProse() {
        assertFalse(ProseNutritionReader.isProseLabel(emptyList(), emptyList(), 1000))
    }

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)
}
