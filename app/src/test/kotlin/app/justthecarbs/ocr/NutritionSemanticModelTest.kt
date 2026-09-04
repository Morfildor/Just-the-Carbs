package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.CarbBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NutritionSemanticModelTest {
    private fun e(text: String, x: Int, y: Int, width: Int = 120, height: Int = 20) =
        OcrElement(text, OcrBox(x, y, x + width, y + height), blockId = y, lineId = 0)

    private fun document(vararg rows: List<OcrElement>, width: Int = 900, height: Int = 500) =
        OcrDocument(width, height, rows.flatMap { it })

    private fun row(y: Int, vararg cells: Pair<String, Int>) = cells.map { (text, x) -> e(text, x, y) }

    private fun baseRows() = arrayOf(
        row(20, "Nutrition" to 20, "per 100 g" to 390),
        row(60, "Fat" to 20, "10 g" to 400),
        row(100, "Protein" to 20, "6 g" to 400),
    )

    private fun confidentValue(document: OcrDocument): BigDecimal? =
        (NutritionTableParser.parse(document) as? LabelReading.Confident)?.candidate?.value

    @Test
    fun `one total declaration spans a label row and aligned value row`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20),
            row(180, "47 g" to 400),
            row(220, "of which sugars" to 40, "3 g" to 400),
        )

        val panel = NutritionDocumentModel.build(document).panels.single()
        val total = panel.declarations.single { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }

        assertEquals(2, total.sourceRows.size)
        assertEquals(listOf("Carbohydrate", "47 g"), total.sourceRows.map { it.text })
        assertEquals(0, confidentValue(document)!!.compareTo(BigDecimal("47")))
    }

    @Test
    fun `multilingual label continuation can span three physical rows`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrates /" to 20),
            row(180, "Koolhydraten" to 30),
            row(220, "7.2 g" to 400),
            row(260, "Sugars" to 50, "3.2 g" to 400),
        )

        val total = NutritionDocumentModel.build(document).panels.single().declarations
            .single { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }

        assertEquals(3, total.sourceRows.size)
        assertEquals(0, confidentValue(document)!!.compareTo(BigDecimal("7.2")))
    }

    @Test
    fun `a continuation row can own per hundred and serving cells`() {
        val document = document(
            row(20, "Nutrition" to 20, "per 100 g" to 390, "25 g serving" to 590),
            row(60, "Fat" to 20, "10 g" to 400, "2.5 g" to 600),
            row(100, "Protein" to 20, "6 g" to 400, "1.5 g" to 600),
            row(140, "Carbohydrate" to 20),
            row(180, "72 g" to 400, "18 g" to 600),
            row(220, "Sugars" to 50, "3 g" to 400, "0.8 g" to 600),
            width = 1000,
        )

        val report = NutritionTableParser.parseWithDiagnostics(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        assertEquals(0, candidate.value.compareTo(BigDecimal("72")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
        assertEquals(0, report.servingCandidate!!.carbsPerServing.compareTo(BigDecimal("18")))
    }

    @Test
    fun `a child beginning on the continuation row cannot supply the total`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20),
            row(180, "of which sugars" to 40, "3 g" to 400),
        )

        assertTrue(NutritionTableParser.parse(document) is LabelReading.NotFound)
        val declarations = NutritionDocumentModel.build(document).panels.single().declarations
        assertTrue(declarations.any { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE })
        assertTrue(declarations.any { it.kind == NutritionRowKind.CARBOHYDRATE_CHILD })
        assertFalse(
            declarations.single { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }
                .sourceRows.any { it.text.contains("3 g") },
        )
    }

    @Test
    fun `a child after a valid total remains separate`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20, "47 g" to 400),
            row(180, "of which sugars" to 40, "3 g" to 400),
        )

        assertEquals(0, confidentValue(document)!!.compareTo(BigDecimal("47")))
        val declarations = NutritionDocumentModel.build(document).panels.single().declarations
        assertEquals(
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            declarations.single { it.kind == NutritionRowKind.CARBOHYDRATE_CHILD }.parentKind,
        )
        assertTrue(declarations.all { it.panelId == 0 })
    }

    @Test
    fun `an unrelated numeric continuation is not attached`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20),
            row(180, "Best before" to 20, "2027" to 400),
        )

        assertTrue(NutritionTableParser.parse(document) is LabelReading.NotFound)
    }

    @Test
    fun `raw element panel isolation excludes horizontally adjacent prose`() {
        val table = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20, "47 g" to 400),
            row(180, "Sugars" to 40, "3 g" to 400),
            row(220, "Salt" to 20, "1 g" to 400),
            width = 1200,
        )
        val adjacent = listOf(
            e("ingredients sugar", 720, 60, 220),
            e("best before 2027", 720, 100, 220),
            e("carbohydrate syrup", 720, 140, 220),
            e("250 g package", 720, 180, 220),
        )
        val combined = table.copy(elements = table.elements + adjacent)

        val model = NutritionDocumentModel.build(combined)
        val panel = model.panels.single { it.localized }

        assertTrue(panel.elements.none { it.box.left >= 720 })
        assertEquals(0, confidentValue(combined)!!.compareTo(BigDecimal("47")))
    }

    @Test
    fun `a serving cell cannot steal a missing per hundred column`() {
        val document = document(
            row(20, "Nutrition" to 20, "per 100 g" to 390, "25 g serving" to 590),
            row(60, "Fat" to 20, "10 g" to 400, "2.5 g" to 600),
            row(100, "Protein" to 20, "6 g" to 400, "1.5 g" to 600),
            row(140, "Carbohydrate" to 20),
            row(180, "18 g" to 600),
            width = 1000,
        )

        assertTrue(NutritionTableParser.parse(document) is LabelReading.NotFound)
    }

    @Test
    fun `manual tap on a continuation value resolves through its total declaration`() {
        val document = document(
            *baseRows(),
            row(140, "Carbohydrate" to 20),
            row(180, "47.0 g" to 400),
            row(220, "Sugars" to 40, "3 g" to 400),
        )

        val candidates = RecoveryCandidates.onRowAt(document, tappedY = 190, tappedX = 430)

        assertEquals(1, candidates.size)
        assertEquals(0, candidates.single().reading.amount.compareTo(BigDecimal("47")))
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_G), candidates.single().reading.basis)
    }

    @Test
    fun `a weak panel guess preserves the full document fallback`() {
        val document = document(
            row(20, "per 100 g" to 390),
            row(100, "Carbohydrate" to 20, "4.7 g" to 400),
        )

        val panel = NutritionDocumentModel.build(document).panels.single()

        assertFalse(panel.localized)
        assertEquals(document.elements, panel.elements)
        assertEquals(0, confidentValue(document)!!.compareTo(BigDecimal("4.7")))
    }

    @Test
    fun `one physical row can retain total and child declarations`() {
        val document = document(
            row(20, "per 100 g" to 390),
            row(100, "Carbohydrate" to 20, "4.7 g" to 400, "of which sugars" to 520, "3 g" to 760),
            width = 1000,
        )

        val declarations = NutritionDocumentModel.build(document).panels.single().declarations

        assertTrue(declarations.any { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE })
        assertTrue(declarations.any { it.kind == NutritionRowKind.CARBOHYDRATE_CHILD })
        assertEquals(0, confidentValue(document)!!.compareTo(BigDecimal("4.7")))
    }

    @Test
    fun `two strongly separated panels remain independent`() {
        fun panel(offset: Int) = listOf(
            row(20, "Nutrition" to (offset + 20), "per 100 g" to (offset + 350)),
            row(60, "Fat" to (offset + 20), "10 g" to (offset + 360)),
            row(100, "Protein" to (offset + 20), "6 g" to (offset + 360)),
            row(140, "Carbohydrate" to (offset + 20), "4.7 g" to (offset + 360)),
            row(180, "Sugars" to (offset + 40), "3 g" to (offset + 360)),
        ).flatten()
        val document = OcrDocument(1800, 400, panel(0) + panel(900))

        val panels = NutritionDocumentModel.build(document).panels

        assertEquals(2, panels.size)
        assertTrue(panels.all { it.localized })
        assertTrue(panels.all { panel ->
            panel.elements.maxOf { it.box.right } - panel.elements.minOf { it.box.left } < 700
        })
    }
}
