package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: geometry-first logical rows
// Invariant: row membership is decided by box geometry alone. ML Kit's blockId/lineId are carried
// for diagnostics and never consulted, because ML Kit both splits one printed row across lines and
// merges two printed rows into one — the two failures that made the old parser pick sugars.
class LogicalRowBuilderTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun document(vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = 800, height = 500, elements = elements.toList())

    @Test
    fun `elements on the same printed row merge despite different ML Kit line ids`() {
        // Same baseline, but ML Kit split the label and its value into separate lines/blocks.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0, block = 0),
                e("45", 400, 202, 440, 232, line = 7, block = 3),
            ),
        )

        assertEquals(1, rows.size)
        assertEquals(listOf("Carbohydrate", "45"), rows.single().elements.map { it.text })
    }

    @Test
    fun `elements on different printed rows split despite a shared ML Kit line id`() {
        // ML Kit merged two printed rows onto one line id. Geometry must still separate them.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 4),
                e("45", 400, 200, 440, 230, line = 4),
                e("of which sugars", 60, 250, 260, 280, line = 4),
                e("8", 400, 250, 440, 280, line = 4),
            ),
        )

        assertEquals(2, rows.size)
        assertEquals(listOf("Carbohydrate", "45"), rows[0].elements.map { it.text })
        assertEquals(listOf("of which sugars", "8"), rows[1].elements.map { it.text })
    }

    @Test
    fun `rows are ordered top to bottom and elements left to right`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("8", 400, 250, 440, 280, line = 1),
                e("of which sugars", 60, 250, 260, 280, line = 1),
                e("45", 400, 200, 440, 230, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
            ),
        )

        assertEquals(listOf("Carbohydrate", "45"), rows[0].elements.map { it.text })
        assertEquals(listOf("of which sugars", "8"), rows[1].elements.map { it.text })
    }

    @Test
    fun `the row box is the union of its elements`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 232, line = 0),
                e("45", 400, 202, 440, 230, line = 1),
            ),
        )

        val box = rows.single().box
        assertEquals(40, box.left)
        assertEquals(440, box.right)
        assertEquals(200, box.top)
        assertEquals(232, box.bottom)
    }

    @Test
    fun `source line keys are retained for diagnostics`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0, block = 0),
                e("45", 400, 202, 440, 232, line = 7, block = 3),
            ),
        )

        assertEquals(setOf(LineKey(0, 0), LineKey(3, 7)), rows.single().sourceLines)
    }

    @Test
    fun `a slightly offset element still joins the row when overlap is high`() {
        // Real OCR boxes jitter by a few pixels on the same printed baseline.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("45", 400, 204, 440, 234, line = 1),
            ),
        )

        assertEquals(1, rows.size)
    }

    @Test
    fun `an empty document yields no rows`() {
        assertTrue(LogicalRowBuilder.build(OcrDocument(width = 800, height = 500, elements = emptyList())).isEmpty())
    }

    @Test
    fun `tall and short elements on one baseline still merge`() {
        // A large "45" beside small "Carbohydrate" text — overlap ratio is measured against the
        // running row box, so a big element does not orphan its own row.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 210, 240, 232, line = 0),
                e("45", 400, 196, 460, 240, line = 1),
            ),
        )

        assertEquals(1, rows.size)
    }
}
