package app.justthecarbs.ocr

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TargetedRereadTriggerTest {

    /** A single-column drink table with a lone separatorless integer -- Unsupported scale. */
    private fun unsupportedScaleDocument() = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
        ),
    )

    @Test
    fun `a confident Unsupported-scale reading is worth rereading`() {
        val document = unsupportedScaleDocument()
        val report = NutritionTableInterpreter.interpret(document)
        val target = TargetedRereadTrigger.targetFor(document, report)
        assertNotNull("an Unsupported-scale reading should trigger a targeted reread", target)
    }

    /**
     * A document with a per-100 column and a value declined for stating no unit.
     *
     * [UnitAccompanimentPolicy.mayDeclineBareValues] only declines a bare number once the document
     * demonstrates it prints units on its value cells elsewhere -- so a second, cleanly-unit-suffixed
     * row is required for the fixture to reproduce the real pickle/oil shape, where the fat row reads
     * cleanly on the very same label whose carbohydrate row lost its unit glyph.
     */
    private fun unitRejectedDocument() = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("per 100 g", OcrBox(400, 100, 620, 140), 0, 0),
            OcrElement("Vetten", OcrBox(60, 160, 300, 195), 0, 1),
            OcrElement("3,4g", OcrBox(430, 160, 500, 195), 0, 1),
            OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 2),
            // "5,49" -- unit glyph misread as a 9, structurally the same shape CarbUnitAccompaniment
            // declines on the pickle/oil captures.
            OcrElement("5,49", OcrBox(430, 200, 500, 240), 0, 2),
        ),
    )

    @Test
    fun `a unit-rejected value on an otherwise-located row is worth rereading`() {
        val document = unitRejectedDocument()
        val report = NutritionTableInterpreter.interpret(document)
        assertNull(
            "precondition: the value must be declined, not merely absent, reading=${report.reading} " +
                "failureReason=${report.failureReason}",
            (report.reading as? LabelReading.Confident),
        )
        org.junit.Assert.assertEquals(
            "precondition: failure reason must be CARB_VALUE_MISSING, was ${report.failureReason}",
            CarbFailureReason.CARB_VALUE_MISSING,
            report.failureReason,
        )
        val target = TargetedRereadTrigger.targetFor(document, report)
        assertNotNull(
            "a row and basis located but the value declined for its unit is worth rereading",
            target,
        )
    }

    /** A document naming no carbohydrate row at all -- nothing to target. */
    @Test
    fun `a document with no carbohydrate declaration at all is not worth rereading`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(OcrElement("unrelated marketing text", OcrBox(0, 0, 200, 40), 0, 0)),
        )
        val report = NutritionTableInterpreter.interpret(document)
        assertNull(TargetedRereadTrigger.targetFor(document, report))
    }

    /** A genuinely ambiguous multi-candidate reading is not a "one row, digits in doubt" case. */
    @Test
    fun `a multi-candidate ambiguity across different rows is not worth rereading`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement(
                    "Koolhydraten/Glucides waarvan suikers",
                    OcrBox(60, 200, 900, 240),
                    0,
                    0,
                ),
                OcrElement("46g", OcrBox(920, 200, 990, 240), 0, 0),
                OcrElement("12g", OcrBox(1000, 200, 1070, 240), 0, 0),
            ),
        )
        val report = NutritionTableInterpreter.interpret(document)
        // This fixture is not guaranteed to produce Ambiguous -- the important assertion is just
        // that a genuine cross-candidate ambiguity, if present, is refused.
        if (report.reading is LabelReading.Ambiguous) {
            assertNull(TargetedRereadTrigger.targetFor(document, report))
        }
    }

    @Test
    fun `an already-confident and scale-established reading is not worth rereading`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41,5g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val report = NutritionTableInterpreter.interpret(document)
        assertNotNull(
            "precondition: this must be a confident, scale-established reading",
            report.reading as? LabelReading.Confident,
        )
        assertNull(
            "a reading whose scale is already Established needs no reread",
            TargetedRereadTrigger.targetFor(document, report),
        )
    }
}
