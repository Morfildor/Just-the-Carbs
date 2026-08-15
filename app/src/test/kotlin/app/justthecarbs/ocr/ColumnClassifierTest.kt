package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: column classification
// Invariant: a percent column is never mistaken for a grams column, even when its header is
// unreadable — the fallback inspects the cells' own shape. PER_100_G vs PER_SERVING is never
// guessed from shape; without a header those stay UNKNOWN and are refused downstream.
class ColumnClassifierTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = top / 50)

    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    @Test
    fun `per 100 g and per serving headers become two classified columns`() {
        val header = row(
            e("per", 300, 100, 340, 130),
            e("100", 345, 100, 385, 130),
            e("g", 390, 100, 405, 130),
            e("per", 550, 100, 590, 130),
            e("serving", 595, 100, 680, 130),
        )

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertEquals(
            listOf(NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_SERVING),
            columns.map { it.kind }.sortedBy { it.ordinal },
        )
    }

    @Test
    fun `a per 100 ml header classifies as PER_100_ML`() {
        val header = row(e("per", 300, 100, 340, 130), e("100", 345, 100, 385, 130), e("ml", 390, 100, 415, 130))

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertEquals(listOf(NutritionColumnKind.PER_100_ML), columns.map { it.kind })
    }

    @Test
    fun `a percent reference header classifies as REFERENCE_PERCENT`() {
        val header = row(
            e("per", 300, 100, 340, 130),
            e("100", 345, 100, 385, 130),
            e("g", 390, 100, 405, 130),
            e("%RI", 620, 100, 670, 130),
        )

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertTrue(columns.any { it.kind == NutritionColumnKind.REFERENCE_PERCENT })
        assertTrue(columns.any { it.kind == NutritionColumnKind.PER_100_G })
    }

    @Test
    fun `a headerless percent column is recognised from its own cells`() {
        // OCR dropped the "%RI" header entirely. The column's cells all carry a percent sign, which
        // is enough to refuse them as carbohydrate grams — the only claim this fallback makes.
        val header = row(e("per", 300, 100, 340, 130), e("100", 345, 100, 385, 130), e("g", 390, 100, 405, 130))
        val carbs = row(e("Carbohydrate", 40, 200, 240, 230), e("45", 350, 200, 390, 230), e("17%", 620, 200, 680, 230))
        val fat = row(e("Fat", 40, 250, 100, 280), e("1.5", 350, 250, 390, 280), e("2%", 620, 250, 680, 280))

        val columns = ColumnClassifier.classify(listOf(header, carbs, fat), documentWidth = 800)

        val percent = columns.firstOrNull { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
        assertTrue("a headerless percent column must still be classified", percent != null)
        assertTrue("it sits at the percent cells' x position", percent!!.centerX > 600)
    }

    @Test
    fun `a value column with no header at all is not guessed`() {
        // No header row anywhere, no percent shape. Refusing to guess is the point: a wrong
        // PER_100_G guess here would produce a confident wrong carbohydrate number.
        val carbs = row(e("Carbohydrate", 40, 200, 240, 230), e("45", 350, 200, 390, 230))

        val columns = ColumnClassifier.classify(listOf(carbs), documentWidth = 800)

        assertTrue(
            "no column may be classified as a grams basis without a header",
            columns.none { it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML },
        )
    }

    @Test
    fun `no rows yields no columns`() {
        assertTrue(ColumnClassifier.classify(emptyList(), documentWidth = 800).isEmpty())
    }
}
