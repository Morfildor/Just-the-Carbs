package app.justthecarbs.ocr

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What an evidence bundle actually contains, asserted rather than assumed.
 *
 * ## Why these cases exist
 *
 * `CLAUDE.md` records `strategyB.txt` as implemented in the ninth-session pass, and the ninth
 * session's own archives do not contain the file — because those bundles were produced by an older
 * APK, not because the source is defective. Those two facts are consistent and neither settles the
 * question, so the brief's instruction is to *verify* rather than reimplement. That is what this
 * does: it drives the real [ScanEvidenceRecorder] against a temporary folder and reads the files
 * back.
 *
 * The finding is that the writer was already correct and one thing was genuinely missing — the
 * **coordinate space** of the document it dumps. See `strategy B coordinate space` below.
 *
 * ## The trap these are shaped to avoid
 *
 * A test that executes zero cases and reports success is the failure mode this repo has already hit
 * (the `assumeTrue`-skipped OCR corpus, which was green while the scanner did not work). Every case
 * here asserts the file exists *before* asserting anything about its content, so a recorder that
 * silently wrote nothing fails rather than passes vacuously.
 */
class ScanEvidenceDiagnosticsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun report(document: OcrDocument) = NutritionTableInterpreter.interpret(document)

    /** A small readable drink table, used as both the Pass A and the Strategy B document. */
    private fun table(originX: Int = 0, originY: Int = 0, width: Int = 1000, height: Int = 1000) =
        OcrDocument(
            width = width,
            height = height,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(originX + 300, originY + 40, originX + 540, originY + 80), 0, 0),
                OcrElement("Koolhydraten", OcrBox(originX + 20, originY + 140, originX + 260, originY + 180), 0, 1),
                OcrElement("0,5 g", OcrBox(originX + 320, originY + 140, originX + 440, originY + 180), 0, 1),
                OcrElement("waarvan suikers", OcrBox(originX + 40, originY + 220, originX + 300, originY + 260), 0, 2),
                OcrElement("0,5 g", OcrBox(originX + 320, originY + 220, originX + 440, originY + 260), 0, 2),
            ),
        )

    private fun record(
        into: File,
        strategyBDocument: OcrDocument?,
        strategyBCrop: SelectedRegionCrop.PixelRect? = null,
        strategyBStatus: String = SelectedTableResolution.StrategyBStatus.RAN_CONFIDENT.name,
        uiAction: String = ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE.name,
    ) {
        val passA = table()
        ScanEvidenceRecorder.recordSelection(
            folder = into,
            region = NormalizedRegion(0.1, 0.1, 0.9, 0.9),
            document = passA,
            outcome = SelectedTableReader.Outcome.FILTERED.name,
            elementsBefore = passA.elements.size,
            elementsAfter = passA.elements.size,
            report = report(passA),
            resolverVerdict = "Resolved",
            strategyBStatus = strategyBStatus,
            uiAction = uiAction,
            strategyBDocument = strategyBDocument,
            strategyBCrop = strategyBCrop,
        )
    }

    /**
     * Precondition for every case below: the recorder must be switched on in this build variant.
     *
     * `recordSelection` returns immediately when `enabled` is false, so without this a broken writer
     * and a release build would be indistinguishable — every case would pass by writing nothing.
     */
    @Test
    fun `the recorder is enabled in the debug unit-test variant`() {
        assertTrue(
            "these cases measure nothing unless the recorder actually runs",
            ScanEvidenceRecorder.enabled,
        )
    }

    // ------------------------------------------------------------------ strategyB.txt

    /** Strategy B ran, so its element dump is written. */
    @Test
    fun `a Strategy B run writes its own element dump`() {
        val out = folder.newFolder("ran")
        val strategyB = table(width = 700, height = 400)
        record(out, strategyBDocument = strategyB)

        val file = File(out, "strategyB.txt")
        assertTrue("strategyB.txt must exist when Strategy B produced a document", file.exists())
        val text = file.readText()
        assertTrue("the dump must carry the document's elements", text.contains("Koolhydraten"))
        assertTrue(text.contains("0,5 g"))
    }

    /**
     * The printed element count matches the document's.
     *
     * Self-checking: a dump that silently truncated, or that rendered a *different* document, would
     * disagree with its own header.
     */
    @Test
    fun `the dump's element count matches the document it rendered`() {
        val out = folder.newFolder("count")
        val strategyB = table(width = 700, height = 400)
        record(out, strategyBDocument = strategyB)

        val text = File(out, "strategyB.txt").readText()
        val printed = Regex("elements\\s*[=:]\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toInt()
        assertEquals(
            "the header must report the document's real element count",
            strategyB.elements.size,
            printed,
        )
    }

    /** Strategy B was skipped, so there is no dump to write and none is written. */
    @Test
    fun `a skipped Strategy B writes no dump`() {
        val out = folder.newFolder("skipped")
        record(
            out,
            strategyBDocument = null,
            strategyBStatus = SelectedTableResolution.StrategyBStatus.SKIPPED_RUNS_ALREADY_AGREE.name,
        )

        assertFalse(
            "no second recognition ran, so there is nothing to dump",
            File(out, "strategyB.txt").exists(),
        )
        // …and the fact that it was skipped is still recorded, in the file a human reads first.
        assertTrue(
            File(out, "selection.txt").readText()
                .contains(SelectedTableResolution.StrategyBStatus.SKIPPED_RUNS_ALREADY_AGREE.name),
        )
    }

    /**
     * **The coordinate space is stated.**
     *
     * This is the one thing that was genuinely missing. Strategy B recognises a *crop*, so its boxes
     * are measured from the crop's origin and its width and height are the crop's — while
     * `diagnostics.txt` records Pass A in capture coordinates. A reader comparing the two without
     * knowing that concludes the passes disagree about where the row is, when they agree exactly.
     * That is the same confusion that let crop-local geometry be drawn as if it were full-frame.
     */
    @Test
    fun `the dump states its native size and crop origin`() {
        val out = folder.newFolder("space")
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        record(out, strategyBDocument = table(width = 700, height = 400), strategyBCrop = crop)

        val text = File(out, "strategyB.txt").readText()
        assertTrue("the native size must be stated", text.contains("700x400"))
        assertTrue("the crop origin must be stated", text.contains("(200, 300)"))
        assertTrue(
            "and it must say which direction the offset goes",
            text.contains("capture coordinates"),
        )
    }

    /** A run whose crop origin was not supplied says so, rather than implying (0,0). */
    @Test
    fun `an unrecorded crop origin is reported as unrecorded`() {
        val out = folder.newFolder("nocrop")
        record(out, strategyBDocument = table(width = 700, height = 400), strategyBCrop = null)

        assertTrue(
            "silence about the origin must not read as an origin of zero",
            File(out, "strategyB.txt").readText().contains("unrecorded"),
        )
    }

    // ------------------------------------------------------------------ the executed action

    /**
     * `final UI action` is the action that ran.
     *
     * The scanner now takes this string from [ScanPresentationDecision]'s own `Action`, so the line
     * cannot drift from the branch that executed — which is what it used to do, because the branch
     * set `uiAction` by hand while the decision was computed separately.
     */
    @Test
    fun `the bundle records the action that was executed`() {
        ScanPresentationDecision.Action.entries.forEach { action ->
            val out = folder.newFolder("action-${action.name}")
            record(out, strategyBDocument = null, uiAction = action.name)
            val line = File(out, "selection.txt").readText()
                .lineSequence()
                .first { it.startsWith("final UI action") }
            assertTrue("expected ${action.name} in: $line", line.contains(action.name))
        }
    }
}
