package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProvenanceTest {

    /**
     * A label whose total and its child print the SAME value. Asserting the number alone cannot
     * distinguish a correct read from a sugars read, which is the case five of the seven new real
     * photographs present. Provenance is what makes such a test mean anything.
     */
    @Test
    fun tabularResultReportsTheRowItsValueCameFrom() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("Voedingswaarde", 40, 20, 300, 60),
                element("per", 320, 20, 380, 60),
                element("100", 390, 20, 450, 60),
                element("g", 460, 20, 490, 60),
                element("Koolhydraten", 40, 120, 300, 160),
                element("2,3", 400, 120, 470, 160),
                element("g", 480, 120, 510, 160),
                element("waarvan", 40, 200, 200, 240),
                element("suikers", 210, 200, 340, 240),
                element("2,3", 400, 200, 470, 240),
                element("g", 480, 200, 510, 240),
            ),
        )

        val report = NutritionTableParser.parseWithDiagnostics(document)

        val reading = report.reading
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_G, (reading as LabelReading.Confident).candidate.basis)

        val provenance = report.provenance
        assertTrue(
            "expected declaration provenance, got $provenance",
            provenance is CandidateProvenance.FromDeclaration,
        )
        val rowText = (provenance as CandidateProvenance.FromDeclaration).rowTexts
            .joinToString(" ").lowercase()
        assertTrue("value must come from the Koolhydraten row, not the sugars row: $rowText", "koolhydraten" in rowText)
        assertTrue("value must NOT come from the sugars row: $rowText", "suikers" !in rowText)
    }

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)
}
