package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The re-parse step: what happens when the user's rectangle meets the retained Pass A document.
 *
 * The cases that matter here are the degenerate ones. A crop that works is the easy path; the
 * dangerous paths are a crop that encloses nothing, a recognition that failed outright, and any route
 * by which the whole-frame answer could be silently lost or silently preferred.
 */
class SelectedTableReaderTest {

    private fun document() = SlopedLabel(slopePercent = 0.0).apply {
        row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
        row(190, "Koolhydraten" to 60..220, block = 1, line = 0)
        row(190, "53,5" to 380..450, "g" to 458..475, block = 2, line = 0)
        row(260, "Ingrediënten" to 900..1100, block = 3, line = 0)
    }.document(width = 1200, height = 400)

    private fun whole(document: OcrDocument) = NutritionTableParser.parseWithDiagnostics(document)

    @Test
    fun `a usable selection re-parses the filtered elements`() {
        val document = document()
        val result = SelectedTableReader.read(
            document,
            whole(document),
            NormalizedRegion(0.0, 0.0, 0.5, 1.0),
        )

        assertEquals(SelectedTableReader.Outcome.FILTERED, result.outcome)
        assertTrue("the filter must have removed something", result.elementsAfter < result.elementsBefore)
        val confident = result.report.reading as? LabelReading.Confident
            ?: throw AssertionError("expected Confident, got ${result.report.reading}")
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("53.5")))
    }

    @Test
    fun `a selection enclosing nothing keeps the whole-frame reading verbatim`() {
        // The user's gesture landed on blank packaging. That says the crop went wrong, not that the
        // label has no carbohydrate row — so a good whole-frame answer must survive it. Asserting
        // identity, not equality: the report is passed through, never recomputed into something that
        // merely looks the same.
        val document = document()
        val wholeFrame = whole(document)

        val result = SelectedTableReader.read(
            document,
            wholeFrame,
            NormalizedRegion(0.80, 0.80, 0.99, 0.99),
        )

        assertEquals(SelectedTableReader.Outcome.RETAINED_WHOLE_FRAME, result.outcome)
        assertSame(wholeFrame, result.report)
    }

    @Test
    fun `a failed recognition reports NO_DOCUMENT and keeps the whole-frame report`() {
        val empty = NutritionParseReport(LabelReading.NotFound, emptyList())

        val result = SelectedTableReader.read(null, empty, NormalizedRegion(0.1, 0.1, 0.9, 0.9))

        assertEquals(SelectedTableReader.Outcome.NO_DOCUMENT, result.outcome)
        assertSame(empty, result.report)
    }

    @Test
    fun `a null selection parses the whole document rather than refusing`() {
        val document = document()

        val result = SelectedTableReader.read(document, whole(document), region = null)

        assertEquals(SelectedTableReader.Outcome.FILTERED, result.outcome)
        assertEquals(result.elementsBefore, result.elementsAfter)
    }

    @Test
    fun `the re-parse can lose a reading the whole frame had, and does not paper over it`() {
        // A deliberately bad crop that cuts the header off. The result is a refusal, and this pins
        // that the reader reports that refusal rather than quietly falling back to the whole-frame
        // answer — silently substituting a different reading is how a user ends up acting on a number
        // that does not correspond to what they selected.
        val document = document()
        val wholeFrame = whole(document)
        assertTrue(
            "PRECONDITION: the whole frame must read confidently for this case to mean anything",
            wholeFrame.reading is LabelReading.Confident,
        )

        val result = SelectedTableReader.read(
            document,
            wholeFrame,
            // Below the header band, enclosing only the value row.
            NormalizedRegion(0.0, 0.35, 0.5, 1.0),
        )

        assertEquals(SelectedTableReader.Outcome.FILTERED, result.outcome)
        assertTrue(
            "a headerless crop must refuse, not inherit the whole-frame reading; got ${result.report.reading}",
            result.report.reading !is LabelReading.Confident,
        )
    }
}
