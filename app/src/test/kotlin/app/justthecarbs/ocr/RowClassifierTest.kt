package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

// Suite: row classification
// Invariant: a row naming any child nutrient can NEVER be TOTAL_CARBOHYDRATE, whatever else it says.
// This is a type-level exclusion, not a scoring penalty — the old scoring model could be outvoted
// by geometry and pick "of which sugars" as the total.
class RowClassifierTest {

    private fun row(vararg words: String): LogicalRow {
        val elements = words.mapIndexed { index, word ->
            OcrElement(word, OcrBox(40 + index * 100, 200, 130 + index * 100, 230), blockId = 0, lineId = 0)
        }
        return LogicalRow(
            elements = elements,
            box = elements.drop(1).fold(elements.first().box) { box, element -> box.union(element.box) },
            sourceLines = setOf(LineKey(0, 0)),
        )
    }

    @Test
    fun `a plain carbohydrate row is the total`() {
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(row("Carbohydrate", "45", "g")))
    }

    @Test
    fun `an of-which-sugars row is a child even though it says carbohydrate`() {
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row("Carbohydrate", "of", "which", "sugars", "8", "g")),
        )
    }

    @Test
    fun `a dextrose row is a child`() {
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, RowClassifier.classify(row("Dextrose", "3.1", "g")))
    }

    @Test
    fun `a polyols row is a child`() {
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, RowClassifier.classify(row("Polyols", "12", "g")))
    }

    @Test
    fun `a dutch koolhydraten row is the total`() {
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(row("Koolhydraten", "45", "g")))
    }

    @Test
    fun `a dutch waarvan suikers row is a child`() {
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row("waarvan", "suikers", "8", "g")),
        )
    }

    @Test
    fun `a per-100g header row is a header`() {
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(row("per", "100", "g")))
    }

    @Test
    fun `a per-serving header row is a header`() {
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(row("per", "serving")))
    }

    @Test
    fun `a protein row is other`() {
        assertEquals(NutritionRowKind.OTHER, RowClassifier.classify(row("Protein", "7.2", "g")))
    }

    @Test
    fun `a header row that also names carbohydrate is still the total row`() {
        // "Carbohydrate per 100 g" on one printed row: the nutrient wins, because this row carries
        // the value. Header detection only applies to rows that name no nutrient at all.
        assertEquals(
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            RowClassifier.classify(row("Carbohydrate", "per", "100", "g", "45")),
        )
    }
}
