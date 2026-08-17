package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ServingColumnWeightTest {

    /** The Lidl grated-cheese shape: 2,0 per 100 g and 1,0 per 50 g portion. 2,0 x 50 / 100 = 1,0. */
    @Test
    fun adoptsAServingWeightPrintedInTheColumnHeaderWhenArithmeticAgrees() {
        val report = NutritionTableParser.parseWithDiagnostics(gratedCheese(servingCarbs = "1,0"))

        val serving = report.servingCandidate
        assertEquals(BigDecimal("1"), serving?.carbsPerServing?.stripTrailingZeros())
        val weight = serving?.descriptor?.weightOrVolume?.amount
        // compareTo, not assertEquals: BigDecimal("50").stripTrailingZeros() is 5E+1 (a negative
        // scale), which is numerically 50 but never .equals() a scale-0 BigDecimal("50") — a JDK
        // BigDecimal quirk this codebase already works around elsewhere (e.g.
        // NutritionTableInterpreterTest, DecimalCommaTest) for exactly this reason.
        assertEquals("a serving weight was adopted", true, weight != null)
        assertEquals(0, BigDecimal("50").compareTo(weight))
    }

    /**
     * The same label with a per-serving figure the header's own weight cannot explain:
     * 2,0 x 50 / 100 = 1,0, not 9,9. The header and the column contradict each other, so there is no
     * trustworthy serving claim left to keep — the WHOLE candidate is dropped, not merely its weight.
     * Keeping carbsPerServing here would surface a figure read out of a column the parser has just
     * concluded it cannot read. The canonical per-100 reading is unaffected.
     */
    @Test
    fun dropsTheWholeServingCandidateWhenHeaderArithmeticDisagrees() {
        val report = NutritionTableParser.parseWithDiagnostics(gratedCheese(servingCarbs = "9,9"))

        assertEquals(
            BigDecimal("2"),
            (report.reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
        assertNull(
            "a header weight the table's arithmetic refuses invalidates the whole serving candidate",
            report.servingCandidate,
        )
    }

    private fun gratedCheese(servingCarbs: String) = OcrDocument(
        width = 1200,
        height = 500,
        elements = listOf(
            OcrElement("per", OcrBox(300, 20, 360, 60), 0, 0),
            OcrElement("100", OcrBox(370, 20, 440, 60), 0, 0),
            OcrElement("g", OcrBox(450, 20, 480, 60), 0, 0),
            OcrElement("per", OcrBox(650, 20, 710, 60), 0, 0),
            OcrElement("portie", OcrBox(720, 20, 830, 60), 0, 0),
            OcrElement("50", OcrBox(840, 20, 890, 60), 0, 0),
            OcrElement("g", OcrBox(900, 20, 930, 60), 0, 0),
            OcrElement("Koolhydraten", OcrBox(40, 140, 290, 180), 0, 1),
            OcrElement("2,0", OcrBox(380, 140, 450, 180), 0, 1),
            OcrElement("g", OcrBox(460, 140, 490, 180), 0, 1),
            OcrElement(servingCarbs, OcrBox(790, 140, 860, 180), 0, 1),
            OcrElement("g", OcrBox(870, 140, 900, 180), 0, 1),
            OcrElement("waarvan", OcrBox(40, 240, 190, 280), 0, 2),
            OcrElement("suikers", OcrBox(200, 240, 330, 280), 0, 2),
            OcrElement("0,5", OcrBox(380, 240, 450, 280), 0, 2),
            OcrElement("g", OcrBox(460, 240, 490, 280), 0, 2),
        ),
    )
}
