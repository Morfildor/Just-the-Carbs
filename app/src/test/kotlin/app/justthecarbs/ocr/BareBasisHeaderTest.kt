package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirms — rather than assumes — the task's own claim that bare `100g`/`100 g`/`100ml`/`100 ml`
 * headers already parse, per its explicit instruction: "Retain and test existing bare '100g'/'100ml'
 * support; adding another keyword regex alone is insufficient."
 *
 * [ColumnClassifier.PER_100] already uses `\s*` (zero-or-more whitespace) between the quantity and
 * the unit spelling, and [LogicalRow.text] joins separate elements with exactly one space — so a
 * fused single-element token (`100g`) and a two-element spaced token (`100`, `g`) both already match
 * the same regex. [RunTogetherHeaderTest] pins the *run-together-with-a-second-header* variant of
 * this; this file pins the plain single-header form directly, so the claim has a positive JVM test
 * rather than resting on regex inspection alone.
 */
class BareBasisHeaderTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    private fun classifiesAsPerHundred(text: String, expected: NutritionColumnKind) {
        val header = row(element(text, 0, 0, 200, 60))
        assertEquals(
            "'$text' must classify as a HEADER row",
            NutritionRowKind.HEADER,
            RowClassifier.classify(header),
        )
        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1000)
        assertTrue(
            "'$text' must resolve a $expected column, got ${columns.map { it.kind }}",
            columns.any { it.kind == expected },
        )
    }

    @Test
    fun `a fused 100g header resolves a per-100-gram column`() =
        classifiesAsPerHundred("100g", NutritionColumnKind.PER_100_G)

    @Test
    fun `a spaced 100 g header resolves a per-100-gram column`() =
        classifiesAsPerHundred("100 g", NutritionColumnKind.PER_100_G)

    @Test
    fun `a fused 100ml header resolves a per-100-millilitre column`() =
        classifiesAsPerHundred("100ml", NutritionColumnKind.PER_100_ML)

    @Test
    fun `a spaced 100 ml header resolves a per-100-millilitre column`() =
        classifiesAsPerHundred("100 ml", NutritionColumnKind.PER_100_ML)

    /**
     * The same quantity split across two separate OCR elements — genuinely different geometry, not a
     * single fused token — because [LogicalRow.text] joins elements with exactly one space, which is
     * exactly what the fused/spaced regex already tolerates.
     */
    @Test
    fun `100 and g as two separate elements still resolve a per-100-gram column`() {
        val header = row(
            element("100", 0, 0, 60, 60),
            element("g", 70, 5, 100, 55),
        )
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(header))
        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 1000)
        assertTrue(columns.any { it.kind == NutritionColumnKind.PER_100_G })
    }
}
