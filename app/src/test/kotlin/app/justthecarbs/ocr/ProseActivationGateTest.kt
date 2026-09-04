package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProseActivationGateTest {

    /** A prose label the table path cannot read now returns a confident value. */
    @Test
    fun proseIsReadWhenTheTablePathFindsNothing() {
        val report = NutritionTableParser.parseWithDiagnostics(prose())
        val reading = report.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(BigDecimal("46"), (reading as LabelReading.Confident).candidate.value.stripTrailingZeros())
        assertTrue(report.provenance is CandidateProvenance.FromProseSpan)
    }

    /**
     * The gate's other direction: a TABLE that the parser reads confidently must never reach the
     * prose stage. Its provenance must be a row, which is only possible via the tabular path.
     */
    @Test
    fun aReadableTableNeverReachesTheProseStage() {
        val report = NutritionTableParser.parseWithDiagnostics(table())
        assertTrue(report.reading is LabelReading.Confident)
        assertTrue(
            "a table's provenance must be a row, not a prose span",
            report.provenance is CandidateProvenance.FromDeclaration,
        )
    }

    /**
     * End-to-end guard for the same case `ProseEligibilityTest.aMergedTableRowIsNotProse` pins in
     * isolation: a table whose rows chained produces a `NotFound` the prose reader must NOT rescue.
     * A confident answer here would mean the fallback had converted a safe refusal into a guess about
     * a label it has no business reading — the exact failure the activation gate exists to prevent.
     */
    @Test
    fun aFailedTableIsNotRescuedByTheProseStage() {
        val report = NutritionTableParser.parseWithDiagnostics(mergedTable())
        assertEquals(LabelReading.NotFound, report.reading)
    }

    /**
     * The merged-table fixture is only meaningful if its two printed rows really do reconstruct into
     * one. Asserted rather than assumed: if a later geometry change un-merged them, the case above
     * would keep passing for an entirely different and uninteresting reason.
     */
    @Test
    fun theMergedTableFixtureReallyReconstructsIntoOneRow() {
        assertEquals(1, LogicalRowBuilder.build(mergedTable()).size)
    }

    private fun prose(): OcrDocument {
        val words = ("Voedingswaarde per 100 g: koolhydraten 46 g, waarvan suikers 1,0 g")
            .split(' ')
        var x = 20
        val elements = words.map { word ->
            val width = word.length * 18
            val box = OcrBox(x, 40, x + width, 80)
            x += width + 12
            OcrElement(word, box, blockId = 0, lineId = 0)
        }
        return OcrDocument(x + 40, 200, elements)
    }

    private fun table(): OcrDocument = OcrDocument(
        width = 1000,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(320, 20, 380, 60), 0, 0),
            OcrElement("100", OcrBox(390, 20, 450, 60), 0, 0),
            OcrElement("g", OcrBox(460, 20, 490, 60), 0, 0),
            OcrElement("Koolhydraten", OcrBox(40, 120, 300, 160), 0, 1),
            OcrElement("2,3", OcrBox(400, 120, 470, 160), 0, 1),
            OcrElement("g", OcrBox(480, 120, 510, 160), 0, 1),
            OcrElement("waarvan", OcrBox(40, 220, 200, 260), 0, 2),
            OcrElement("suikers", OcrBox(210, 220, 340, 260), 0, 2),
            OcrElement("2,3", OcrBox(400, 220, 470, 260), 0, 2),
            OcrElement("g", OcrBox(480, 220, 510, 260), 0, 2),
        ),
    )

    /** The same table with its two nutrient rows overlapping enough to reconstruct as one row. */
    private fun mergedTable(): OcrDocument = OcrDocument(
        width = 1000,
        height = 300,
        elements = listOf(
            OcrElement("Koolhydraten", OcrBox(40, 130, 300, 170), 0, 1),
            OcrElement("2,3", OcrBox(400, 128, 470, 168), 0, 1),
            OcrElement("waarvan", OcrBox(40, 150, 200, 190), 0, 2),
            OcrElement("suikers", OcrBox(210, 152, 340, 192), 0, 2),
            OcrElement("2,3", OcrBox(400, 154, 470, 194), 0, 2),
        ),
    )
}
