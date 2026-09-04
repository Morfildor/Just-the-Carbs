package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A filtered report must be paired with the document it was filtered from.
 *
 * ## The defect
 *
 * [SelectedTableResolution] built its Strategy A evidence as
 *
 * ```
 * RecognitionEvidence(FILTERED_PASS_A, report = filtered.report, document = passA.document)
 * ```
 *
 * — the **filtered** parse beside the **unfiltered** document. [SelectedTableReader.Result] did not
 * expose the filtered document at all, so there was nothing else to pass.
 *
 * That breaks the claim the source name makes. `FILTERED_PASS_A` means *Pass A's elements restricted
 * to the user's rectangle*, and every consumer that reads `evidence.document` — the resolver's
 * richest-document tie-break, [AutomaticVerification]'s structural route,
 * [RecognitionEvidence.valueConfidence], [ScaleAmbiguity] through the scanner — was handed elements
 * the user's rectangle had **excluded**.
 *
 * The concrete consequence is the one this class asserts: evidence from outside the selected
 * rectangle could support or contradict the selected candidate. A row the user cropped away is not
 * evidence about the row they cropped to.
 *
 * ## Scope
 *
 * This is a pairing fix, not a rule change. No threshold moves, no parse is re-run, and the
 * *reading* is untouched — `filtered.report` was already the filtered parse and still is. What
 * changes is which elements the stages behind it are allowed to see.
 */
class FilteredEvidenceDocumentTest {

    private fun report(document: OcrDocument) = NutritionTableInterpreter.interpret(document)

    /**
     * A table at the top of the frame, with a **second, contradicting** nutrition panel below it.
     *
     * The rectangle selects only the upper table. The lower panel is what must not reach any stage
     * judging the upper table's candidate — it is a different product's figures, which is exactly
     * the interference the crop exists to remove.
     */
    private fun twoPanelFrame() = OcrDocument(
        width = 1000,
        height = 2000,
        elements = listOf(
            // Upper table — the one the user cropped to.
            OcrElement("per 100 ml", OcrBox(400, 60, 640, 100), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
            OcrElement("0,5 g", OcrBox(430, 160, 560, 200), 0, 1),
            OcrElement("waarvan suikers", OcrBox(80, 240, 340, 280), 0, 2),
            OcrElement("0,5 g", OcrBox(430, 240, 560, 280), 0, 2),
            // Lower panel — a different product entirely, well outside the rectangle.
            OcrElement("per 100 g", OcrBox(400, 1500, 640, 1540), 1, 0),
            OcrElement("Koolhydraten", OcrBox(60, 1600, 300, 1640), 1, 1),
            OcrElement("62 g", OcrBox(430, 1600, 560, 1640), 1, 1),
            OcrElement("waarvan suikers", OcrBox(80, 1680, 340, 1720), 1, 2),
            OcrElement("35 g", OcrBox(430, 1680, 560, 1720), 1, 2),
        ),
    )

    /** The rectangle over the upper table only. */
    private val upperTable = NormalizedRegion(0.0, 0.0, 1.0, 0.25)

    private fun resolve(passADocument: OcrDocument): SelectedTableResolution.Result =
        SelectedTableResolution.resolve(
            passA = PassAResult(
                sessionId = 1L,
                document = passADocument,
                report = report(passADocument),
                bitmap = null,
                evidence = null,
                recognitionMs = 0L,
            ),
            region = upperTable,
            bitmap = null,
            // Strategy B is irrelevant here and is disabled, so this measures Strategy A alone.
            recogniseRegion = { _, _ -> null },
        )

    /**
     * **The contract.** A filtered report is paired with a filtered document.
     *
     * Fails before the fix with the full 10-element frame, because `passA.document` was passed
     * through unchanged.
     */
    @Test
    fun `filtered evidence carries the filtered document`() {
        val frame = twoPanelFrame()
        val result = resolve(frame)

        val filteredEvidence = result.evidence.firstOrNull { it.source == EvidenceSource.FILTERED_PASS_A }
        assertNotNull("precondition: Strategy A must have produced evidence", filteredEvidence)

        val document = filteredEvidence!!.document
        assertNotNull(document)
        assertTrue(
            "the filtered document must be smaller than the frame it was filtered from",
            document!!.elements.size < frame.elements.size,
        )
        assertEquals(
            "it must hold exactly the elements the rectangle retained",
            ElementRegionFilter.filter(frame, upperTable)!!.elements.size,
            document.elements.size,
        )
    }

    /**
     * **The consequence.** Nothing the rectangle excluded reaches the filtered evidence.
     *
     * Stated on the values rather than on the count, because a count can coincide while the wrong
     * elements survive. The lower panel's `62 g` and `35 g` are the interference; neither may be
     * visible to a stage judging the upper table's `0,5 g`.
     */
    @Test
    fun `evidence outside the rectangle cannot support or contradict the selected candidate`() {
        val result = resolve(twoPanelFrame())
        val filteredEvidence = result.evidence.first { it.source == EvidenceSource.FILTERED_PASS_A }
        val texts = filteredEvidence.document!!.elements.map { it.text }

        assertFalse("the other panel's total must not be visible", texts.contains("62 g"))
        assertFalse("nor its sugars figure", texts.contains("35 g"))
        assertTrue("the selected table's own value must still be there", texts.contains("0,5 g"))
    }

    /**
     * Pass A's own evidence is untouched: it is the whole frame and must stay so.
     *
     * Without this the fix could be "filter everything", which would delete the baseline the resolver
     * compares against and make the crop an oracle rather than evidence.
     */
    @Test
    fun `the whole-frame evidence still carries the whole frame`() {
        val frame = twoPanelFrame()
        val result = resolve(frame)
        val wholeFrame = result.evidence.first { it.source == EvidenceSource.FULL_FRAME_PASS_A }

        assertEquals(
            "Pass A's evidence is the uncropped recognition, by definition",
            frame.elements.size,
            wholeFrame.document!!.elements.size,
        )
    }

    /**
     * A whole-frame selection removes nothing, so its "filtered" document *is* the frame.
     *
     * [ElementRegionFilter.filter] returns the document unchanged above its no-op threshold, so this
     * is not a case the pairing fix may narrow: the two documents are legitimately equal here, and a
     * fix that shrank this one would be discarding elements the user never excluded.
     *
     * Asserted as the boundary of the change rather than as a property of the defect.
     */
    @Test
    fun `a whole-frame selection legitimately keeps every element`() {
        val frame = twoPanelFrame()
        val result = SelectedTableResolution.resolve(
            passA = PassAResult(
                sessionId = 1L,
                document = frame,
                report = report(frame),
                bitmap = null,
                evidence = null,
                recognitionMs = 0L,
            ),
            region = NormalizedRegion(0.0, 0.0, 1.0, 1.0),
            bitmap = null,
            recogniseRegion = { _, _ -> null },
        )

        val filtered = result.evidence.firstOrNull { it.source == EvidenceSource.FILTERED_PASS_A }
        if (filtered != null) {
            assertEquals(
                "nothing was excluded, so nothing may be dropped from the filtered view",
                frame.elements.size,
                filtered.document!!.elements.size,
            )
        }
    }
}
