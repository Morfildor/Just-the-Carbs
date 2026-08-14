package app.carbscan.ocr

import app.carbscan.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: spatial nutrition-table parsing
// Invariant: only a total-carbohydrate value aligned to an established per-100 column is confident.
// Boundary IN: pure OCR geometry, terminology, scoring, ambiguity, and validation.
// Boundary OUT: ML Kit conversion and camera lifecycle (LabelAnalyzer and instrumented scanner tests).
class NutritionLabelParserTest {

    @Test
    fun `Dutch table selects total carbohydrate and excludes sugars`() {
        val candidate = confident(
            table(
                header = "per 100 g",
                carbLabel = "Koolhydraten",
                carbValue = "46,3 g",
                excludedLabel = "waarvan suikers",
                excludedValue = "7,8 g",
            ),
        )

        assertDecimal("46.3", candidate.value)
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `English multi-column table selects per-100 rather than serving`() {
        val candidate = confident(twoColumnTable("Carbohydrate", "52.4 g", "13.1 g", "of which sugars"))

        assertDecimal("52.4", candidate.value)
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `German spatial table reads separate OCR elements`() {
        assertDecimal("41.7", confident(table("pro 100 g", "Kohlenhydrate", "41,7", "davon Zucker", "6,2")).value)
    }

    @Test
    fun `French spatial table reads decimal comma`() {
        assertDecimal("38.9", confident(table("pour 100 g", "Glucides", "38,9 g", "dont sucres", "4,1 g")).value)
    }

    @Test
    fun `Spanish spatial table reads total above sugars`() {
        assertDecimal(
            "27.5",
            confident(table("por 100 g", "Hidratos de carbono", "27,5 g", "de los cuales azucares", "3,2 g")).value,
        )
    }

    @Test
    fun `Italian spatial table reads total above sugars`() {
        assertDecimal("33.4", confident(table("per 100 g", "Carboidrati", "33,4 g", "di cui zuccheri", "5,0 g")).value)
    }

    @Test
    fun `supports a per-100-ml column`() {
        val candidate = confident(table("per 100 ml", "Carbohydrate", "9.4 g", "of which sugars", "8.1 g"))

        assertEquals(NutritionBasis.PER_100_ML, candidate.basis)
        assertDecimal("9.4", candidate.value)
    }

    @Test
    fun `supports per-100 column on the left`() {
        val document = document(
            e("per 100 g", 90, 20, 190, 45, line = 1),
            e("per serving", 340, 20, 470, 45, line = 1),
            e("Carbohydrate", 520, 100, 720, 128, line = 2),
            e("48.2 g", 100, 102, 175, 130, line = 2),
            e("12.1 g", 360, 101, 430, 129, line = 2),
        )

        assertDecimal("48.2", confident(document).value)
    }

    @Test
    fun `supports per-100 column on the right`() {
        val document = document(
            e("per serving", 300, 20, 430, 45, line = 1),
            e("per 100 g", 570, 20, 680, 45, line = 1),
            e("Carbohydrate", 30, 100, 230, 128, line = 2),
            e("12.1 g", 330, 101, 400, 129, line = 2),
            e("48.2 g", 585, 102, 655, 130, line = 2),
        )

        assertDecimal("48.2", confident(document).value)
    }

    @Test
    fun `supports a same-element label and value`() {
        val document = document(
            e("per 100 g", 350, 20, 470, 45, line = 1),
            e("Carbohydrate 52.4 g", 30, 100, 445, 132, line = 2),
        )

        assertDecimal("52.4", confident(document).value)
    }

    @Test
    fun `accepts a slightly vertically misaligned value`() {
        val document = document(
            e("per 100 g", 350, 20, 470, 45, line = 1),
            e("Carbohydrate", 30, 100, 240, 128, line = 2),
            e("48.2", 370, 112, 425, 140, line = 3),
            e("g", 430, 112, 445, 140, line = 3),
        )

        assertDecimal("48.2", confident(document).value)
    }

    @Test
    fun `two equally plausible values remain ambiguous`() {
        val reading = NutritionTableParser.parse(
            document(
                e("per 100 g", 350, 20, 470, 45, line = 1),
                e("Carbohydrate", 30, 100, 230, 128, line = 2),
                e("48.2 g", 330, 100, 390, 128, line = 2),
                e("51.0 g", 430, 100, 490, 128, line = 2),
            ),
        )

        assertTrue("expected Ambiguous but was $reading", reading is LabelReading.Ambiguous)
        assertEquals(2, (reading as LabelReading.Ambiguous).candidates.size)
    }

    @Test
    fun `two plausible per-100 bases require explicit selection`() {
        val reading = NutritionTableParser.parse(
            document(
                e("per 100 g", 300, 20, 410, 45, line = 1),
                e("per 100 ml", 500, 20, 630, 45, line = 1),
                e("Carbohydrate", 30, 100, 230, 128, line = 2),
                e("48.2 g", 320, 100, 390, 128, line = 2),
                e("49.0 g", 525, 100, 595, 128, line = 2),
            ),
        )

        assertTrue("expected Ambiguous but was $reading", reading is LabelReading.Ambiguous)
        val candidates = (reading as LabelReading.Ambiguous).candidates
        assertEquals(setOf(NutritionBasis.PER_100_G, NutritionBasis.PER_100_ML), candidates.map { it.basis }.toSet())
    }

    @Test
    fun `carbohydrate row without basis evidence requires explicit basis selection`() {
        val reading = NutritionTableParser.parse(
            document(e("Carbohydrate 48.2 g", 30, 100, 450, 130, line = 1)),
        )

        assertTrue("expected Ambiguous but was $reading", reading is LabelReading.Ambiguous)
        val candidate = (reading as LabelReading.Ambiguous).candidates.single()
        assertDecimal("48.2", candidate.value)
        assertEquals(null, candidate.basis)
    }

    @Test
    fun `carbohydrate anchor without a row-aligned number is not found`() {
        val reading = NutritionTableParser.parse(
            document(
                e("per 100 g", 350, 20, 470, 45, line = 1),
                e("Carbohydrate", 30, 100, 230, 128, line = 2),
                e("48.2 g", 370, 260, 430, 288, line = 8),
            ),
        )

        assertEquals(LabelReading.NotFound, reading)
    }

    @Test
    fun `impossible carbohydrate value is rejected`() {
        assertEquals(
            LabelReading.NotFound,
            NutritionTableParser.parse(table("per 100 g", "Carbohydrate", "473 g", "of which sugars", "8 g")),
        )
    }

    @Test
    fun `fibre starch and polyol rows never outrank total carbohydrate`() {
        val document = document(
            e("per 100 g", 350, 20, 470, 45, line = 1),
            e("Carbohydrate", 30, 90, 230, 118, line = 2),
            e("48.2 g", 370, 90, 430, 118, line = 2),
            e("of which polyols", 55, 125, 250, 153, line = 3),
            e("12.0 g", 370, 125, 430, 153, line = 3),
            e("of which starch", 55, 160, 250, 188, line = 4),
            e("20.0 g", 370, 160, 430, 188, line = 4),
            e("Fibre", 30, 195, 130, 223, line = 5),
            e("5.0 g", 370, 195, 430, 223, line = 5),
        )

        assertDecimal("48.2", confident(document).value)
    }

    @Test
    fun `additional supported languages recognize total carbohydrate and exclude sugars`() {
        val cases = listOf(
            "Portuguese" to ("Hidratos de carbono" to "dos quais acucares"),
            "Turkish" to ("Karbonhidrat" to "sekerler"),
            "Polish" to ("Weglowodany" to "w tym cukry"),
            "Danish" to ("Kulhydrat" to "heraf sukkerarter"),
            "Swedish" to ("Kolhydrat" to "varav sockerarter"),
            "Norwegian" to ("Karbohydrat" to "hvorav sukkerarter"),
            "Finnish" to ("Hiilihydraatti" to "josta sokereita"),
            "Czech" to ("Sacharidy" to "z toho cukry"),
            "Romanian" to ("Glucide" to "din care zaharuri"),
        )

        cases.forEach { (language, labels) ->
            val candidate = confident(table("per 100 g", labels.first, "44,6 g", labels.second, "9,1 g"))
            assertEquals(language, 0, BigDecimal("44.6").compareTo(candidate.value))
        }
    }

    @Test
    fun `diagnostics explain selected and rejected candidates`() {
        val report = NutritionTableParser.parseWithDiagnostics(
            twoColumnTable("Carbohydrate", "52.4 g", "13.1 g", "of which sugars"),
        )

        assertTrue(report.diagnostics.any { it.stage == "anchor" && "Carbohydrate" in it.message })
        assertTrue(report.diagnostics.any { it.stage == "header" && "PER_100_G" in it.message })
        assertTrue(report.diagnostics.any { it.stage == "selected" && "52.4" in it.message })
        assertTrue(report.diagnostics.any { it.stage == "rejected" && "13.1" in it.message })
    }

    private fun confident(document: OcrDocument): CarbCandidate {
        val reading = NutritionTableParser.parse(document)
        assertTrue("expected Confident but was $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate
    }

    private fun table(
        header: String,
        carbLabel: String,
        carbValue: String,
        excludedLabel: String,
        excludedValue: String,
    ): OcrDocument = document(
        e(header, 350, 20, 470, 45, line = 1),
        e(carbLabel, 30, 100, 260, 128, line = 2),
        e(carbValue, 365, 100, 440, 128, line = 2),
        e(excludedLabel, 55, 138, 270, 166, line = 3),
        e(excludedValue, 365, 138, 440, 166, line = 3),
    )

    private fun twoColumnTable(
        carbLabel: String,
        per100Value: String,
        servingValue: String,
        sugarLabel: String,
    ): OcrDocument = document(
        e("per 100 g", 330, 20, 450, 45, line = 1),
        e("per serving", 575, 20, 715, 45, line = 1),
        e(carbLabel, 30, 100, 250, 128, line = 2),
        e(per100Value, 350, 100, 425, 128, line = 2),
        e(servingValue, 600, 100, 675, 128, line = 2),
        e(sugarLabel, 55, 138, 270, 166, line = 3),
        e("9.2 g", 350, 138, 425, 166, line = 3),
        e("2.3 g", 600, 138, 675, 166, line = 3),
    )

    private fun document(vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = 800, height = 500, elements = elements.toList())

    private fun e(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        line: Int,
        block: Int = 0,
    ) = OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
