package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

// Suite: multilingual nutrient terminology on one printed row
// Invariant: a row naming ANY child nutrient in ANY covered language is a child row. The exclusion
// is by row type and is checked before the carbohydrate term, so no amount of geometric convenience
// or repeated total-carbohydrate wording can promote it. A slash-separated trilingual row names the
// child three times and the parent three times; it is still a child row.
class MultilingualRowTest {

    private fun rowOf(text: String): LogicalRow {
        val elements = text.split(" ").mapIndexed { index, word ->
            OcrElement(
                text = word,
                box = OcrBox(60 + index * 90, 200, 60 + index * 90 + 80, 224),
                blockId = 0,
                lineId = 0,
            )
        }
        return LogicalRow(
            elements = elements,
            box = elements.drop(1).fold(elements.first().box) { acc, e -> acc.union(e.box) },
            sourceLines = setOf(LineKey(0, 0)),
        )
    }

    private fun kindOf(text: String) = RowClassifier.classify(rowOf(text))

    @Test
    fun `slash-separated total-carbohydrate rows are recognised in every covered language`() {
        listOf(
            "Koolhydraten / Glucides / Kohlenhydrate 61,9 g",
            "Carbohydrate / Glucides / Koolhydraten / Kohlenhydrate 53,5 g",
            "Carbohydrates 45 g",
            "Ugljikohidrati 53,5 g",
            "Ogljikovi hidrati 53,5 g",
            "Hidratos de carbono 20 g",
            "Carboidrati 20 g",
            "Sacharidy 20 g",
            "Węglowodany 20 g",
            "Hiilihydraatit 20 g",
        ).forEach { text ->
            assertEquals(text, NutritionRowKind.TOTAL_CARBOHYDRATE, kindOf(text))
        }
    }

    @Test
    fun `slash-separated sugar rows are child rows in every covered language`() {
        listOf(
            "waarvan suikers / dont sucres / davon Zucker 47,6 g",
            "of which sugars 30 g",
            "od čega šećeri 47,6 g",
            "od tega sladkorji 47,6 g",
            "de los cuales azúcares 30 g",
            "di cui zuccheri 30 g",
            "z toho cukry 30 g",
            "josta sokereita 30 g",
        ).forEach { text ->
            assertEquals(text, NutritionRowKind.CARBOHYDRATE_CHILD, kindOf(text))
        }
    }

    @Test
    fun `a row naming the total AND a child is a child row`() {
        // "Carbohydrate, of which sugars" printed as one line. The child name is decisive: the
        // number on this row is a sugars figure whatever else the row says.
        listOf(
            "Koolhydraten waarvan suikers 47,6 g",
            "Carbohydrate of which sugars 30 g",
            "Kohlenhydrate davon Zucker 30 g",
            "Glucides dont sucres 30 g",
            "Ugljikohidrati od čega šećeri 30 g",
        ).forEach { text ->
            assertEquals(text, NutritionRowKind.CARBOHYDRATE_CHILD, kindOf(text))
        }
    }

    @Test
    fun `diacritics are matched regardless of how OCR renders them`() {
        // Normalization strips combining marks, so a recognizer that loses the caron still matches.
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, kindOf("od cega seceri 47,6 g"))
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, kindOf("davon Zucker 47,6 g"))
    }

    @Test
    fun `a multilingual total row survives being read end to end`() {
        val document = SlopedLabel(0.0).apply {
            row(200, "per" to 470..515, "100" to 523..580, "g" to 588..605, block = 0, line = 0)
            row(
                300,
                "Ugljikohidrati" to 60..230, "/" to 238..248, "Ogljikovi" to 256..390,
                "hidrati" to 398..480,
                block = 1, line = 0,
            )
            row(300, "53,5" to 500..565, "g" to 573..590, block = 2, line = 0)
            row(350, "od" to 80..120, "čega" to 128..200, "šećeri" to 208..300, block = 3, line = 0)
            row(350, "53,3" to 500..565, "g" to 573..590, block = 4, line = 0)
        }.document()

        val reading = NutritionTableParser.parse(document)
        assertEquals(
            java.math.BigDecimal("53.5"),
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
    }
}
