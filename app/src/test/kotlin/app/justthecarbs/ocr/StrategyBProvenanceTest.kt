package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strategy B's coordinate space, carried explicitly instead of assumed away.
 *
 * ## The defect
 *
 * [SelectedRegionRecognizer] crops the source bitmap and calls
 * `MlKitOcrMapper.toDocument(text, crop.width, crop.height)`. Every box in the document it returns
 * is therefore measured **from the crop's own origin**, and the document's `width`/`height` are the
 * crop's, not the photograph's.
 *
 * Nothing carried that fact. [RecognitionEvidence] held the document and no crop, so:
 *
 * * the scanner asked [ScaleAmbiguity] and [ReadingEligibility] about a Strategy B candidate using
 *   **Pass A's** document, where that candidate's geometry matches no row at all — the scale
 *   question silently degraded to `Unsupported("no row to pair against")` on every Strategy B
 *   reading, whatever the label actually printed; and
 * * [app.justthecarbs.ui.scan.VerificationScreen] drew `candidate.geometry` scaled against the full
 *   capture bitmap, so the highlight and the row close-up were short by the crop origin — pointing
 *   at the wrong part of the photograph the user is being asked to check the figure against.
 *
 * [SelectedRegionCrop.toSourceSpace] already existed, with tests, and its KDoc already said
 * "evidence and assisted-mode tapping" need it. It had **zero production callers**.
 *
 * ## What these cases exercise
 *
 * The production orchestration — [SelectedTableResolution.resolve] with a real `recogniseRegion`
 * lambda standing in for ML Kit — rather than a hand-assembled evidence list. A corrected full-frame
 * Strategy B document built by hand in a test would assert the fix onto a fixture instead of onto
 * the code, and is exactly what the brief forbids.
 */
class StrategyBProvenanceTest {

    // ------------------------------------------------------------------ fixtures

    /**
     * A drink table, laid out at ([originX], [originY]) within a 2000x2000 photograph.
     *
     * The same builder produces the full-frame document (at the real origin) and the crop-local one
     * (at the origin, minus itself, i.e. 0,0) — which is precisely the relationship a real crop has
     * to its source, so the two are honestly related rather than independently invented.
     */
    private fun drinkTable(originX: Int, originY: Int, width: Int, height: Int) = OcrDocument(
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

    /** The photograph as Pass A sees it, with the table sitting at ([x], [y]). */
    private fun passAWithTableAt(x: Int, y: Int) = drinkTable(x, y, width = 2000, height = 2000)

    /** The same table as Strategy B sees it: crop-local, origin at 0,0. */
    private fun cropLocalTable() = drinkTable(0, 0, width = 700, height = 400)

    private fun report(document: OcrDocument) = NutritionTableInterpreter.interpret(document)

    /**
     * A photograph Pass A could make nothing of — which is the only situation in which Strategy B
     * *wins*, and therefore the only situation in which its coordinate space reaches the user.
     *
     * Necessary rather than incidental: with a readable Pass A the resolver corroborates the two and
     * carries the richer document (Pass A's), so a test using a readable Pass A would silently be
     * measuring Pass A's geometry and would pass however broken the translation was.
     */
    private fun blindPassA() = OcrDocument(
        width = 2000,
        height = 2000,
        elements = listOf(OcrElement("Voedingswaarde", OcrBox(20, 20, 400, 60), 0, 0)),
    )

    /**
     * Runs the production resolution with a Strategy B that returns a crop-local document.
     *
     * [crop] is what a real [SelectedRegionRecognizer] would have carried; supplying it here is the
     * whole point — a run that forgets it is exactly the pre-fix behaviour.
     */
    private fun resolveWithStrategyB(
        passA: OcrDocument,
        strategyBDocument: OcrDocument,
        crop: SelectedRegionCrop.PixelRect?,
    ): SelectedTableResolution.Result {
        val passAResult = PassAResult(
            sessionId = 1L,
            document = passA,
            report = report(passA),
            bitmap = null,
            evidence = null,
            recognitionMs = 0L,
        )
        return SelectedTableResolution.resolve(
            passA = passAResult,
            region = NormalizedRegion(0.1, 0.1, 0.9, 0.9),
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            recogniseRegion = { _, _ ->
                RecognitionEvidence(
                    source = EvidenceSource.SELECTED_REGION_OCR,
                    report = report(strategyBDocument),
                    document = strategyBDocument,
                    crop = crop,
                    physicalObservation = PhysicalObservationId("test-fixture"),
                )
            },
        )
    }

    // ------------------------------------------------------------------ the provenance is carried

    /**
     * The winning evidence, its native document and its crop origin all survive resolution.
     *
     * Without this the outcome names a source and carries a report, and the coordinate space those
     * were measured in is unrecoverable — which is how a crop-local box ended up drawn as if it were
     * full-frame.
     */
    @Test
    fun `a Strategy B win carries its own document and crop origin`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        val result = resolveWithStrategyB(blindPassA(), cropLocalTable(), crop)

        val winner = result.outcome.winningEvidence
        assertNotNull("the outcome must name the pass its reading came from", winner)
        assertEquals(EvidenceSource.SELECTED_REGION_OCR, winner!!.source)
        assertEquals("the native document must be the crop's, not Pass A's", 700, winner.document!!.width)
        assertEquals(400, winner.document!!.height)
        assertEquals("the crop origin must survive", crop, winner.crop)
    }

    /**
     * **Non-zero X and Y.** The translated box equals the full-frame box the same table occupies.
     *
     * This is the assertion that would have caught the defect: pre-fix, `sourceSpaceGeometry` did not
     * exist and the raw crop-local box was used, which is short by exactly (200, 300) here.
     */
    @Test
    fun `crop-local geometry translates to the full-frame position at a non-zero origin`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        val result = resolveWithStrategyB(blindPassA(), cropLocalTable(), crop)
        val winner = result.outcome.winningEvidence!!

        val translated = winner.sourceSpaceGeometry
        assertNotNull("a confident reading must expose source-space geometry", translated)

        // The same table read whole-frame by Pass A, for comparison. Its candidate box is where the
        // row genuinely is in the photograph, which is what the translated box must equal.
        val fullFrame = passAWithTableAt(200, 300)
        val expected = (report(fullFrame).reading as LabelReading.Confident).candidate.geometry
        assertEquals("translated geometry must land on the row's real position", expected, translated)

        // And the raw box must genuinely differ, or the test proves nothing.
        val raw = (winner.reading as LabelReading.Confident).candidate.geometry
        assertTrue("precondition: the crop-local box must differ from the full-frame one", raw != expected)
        assertEquals("the offset is exactly the crop origin", expected.left - raw.left, 200)
        assertEquals(expected.top - raw.top, 300)
    }

    /** **The zero-origin control.** A crop at (0,0) translates to itself, so nothing is over-applied. */
    @Test
    fun `a crop at the origin leaves geometry unchanged`() {
        val crop = SelectedRegionCrop.PixelRect(left = 0, top = 0, width = 700, height = 400)
        val result = resolveWithStrategyB(blindPassA(), cropLocalTable(), crop)
        val winner = result.outcome.winningEvidence!!

        val raw = (winner.reading as LabelReading.Confident).candidate.geometry
        assertEquals("a zero origin must be a no-op", raw, winner.sourceSpaceGeometry)
    }

    /** **Multiple crop sizes and positions.** The translation is the origin, never a scale factor. */
    @Test
    fun `the translation is the crop origin at every size and position`() {
        listOf(
            SelectedRegionCrop.PixelRect(left = 17, top = 991, width = 700, height = 400),
            SelectedRegionCrop.PixelRect(left = 640, top = 64, width = 1200, height = 900),
            SelectedRegionCrop.PixelRect(left = 1000, top = 1000, width = 900, height = 900),
        ).forEach { crop ->
            val result = resolveWithStrategyB(blindPassA(), cropLocalTable(), crop)
            val winner = result.outcome.winningEvidence!!
            val raw = (winner.reading as LabelReading.Confident).candidate.geometry
            val translated = winner.sourceSpaceGeometry!!

            assertEquals("$crop: left", raw.left + crop.left, translated.left)
            assertEquals("$crop: top", raw.top + crop.top, translated.top)
            assertEquals("$crop: width is never rescaled", raw.width, translated.width)
            assertEquals("$crop: height is never rescaled", raw.height, translated.height)
        }
    }

    /** **Pass A is untouched.** No crop, so its geometry is already source-space and does not move. */
    @Test
    fun `Pass A geometry is unchanged by the translation boundary`() {
        val passA = passAWithTableAt(200, 300)
        val evidence = RecognitionEvidence(
            source = EvidenceSource.FULL_FRAME_PASS_A,
            report = report(passA),
            document = passA,
        )
        assertNull("Pass A carries no crop", evidence.crop)
        val raw = (evidence.reading as LabelReading.Confident).candidate.geometry
        assertEquals("Pass A geometry must pass through untouched", raw, evidence.sourceSpaceGeometry)
    }

    // ------------------------------------------------------------------ evaluation uses the winner

    /**
     * The scale question is asked of the winning pass's own document.
     *
     * Pre-fix the scanner passed `captured.document` — Pass A — so a Strategy B candidate's row was
     * never found and the verdict was `Unsupported("no row to pair against")` regardless of what the
     * label printed. Here the printed `0,5 g` carries its own separator, so the correct verdict is
     * `Established`, and asking the wrong document gives the wrong answer.
     */
    @Test
    fun `the scale question is asked of the winning document, not Pass A's`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        val blindPassA = blindPassA()
        val result = resolveWithStrategyB(blindPassA, cropLocalTable(), crop)
        val winner = result.outcome.winningEvidence!!

        val againstPassA = AutomaticScanAdvance.scaleVerdict(result.outcome, blindPassA)
        val againstWinner = AutomaticScanAdvance.scaleVerdict(result.outcome, winner.document)

        assertTrue(
            "precondition: Pass A's document cannot place this candidate, was $againstPassA",
            againstPassA is ScaleAmbiguity.Verdict.Unsupported,
        )
        assertTrue(
            "the winning document states the scale — the separator survived, was $againstWinner",
            againstWinner is ScaleAmbiguity.Verdict.Established,
        )
    }

    /**
     * The green drink's `0.5` reaches the user as a proposal on the frozen photograph.
     *
     * The ninth session held this value twice and showed nothing. It is confident, uncorroborated,
     * and its scale is stated by its own separator — so `CONFIRM_ON_CAPTURE`, evaluated against the
     * winning document.
     */
    @Test
    fun `the green drink's Strategy B reading is proposed on the capture`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        val result = resolveWithStrategyB(blindPassA(), cropLocalTable(), crop)
        val winner = result.outcome.winningEvidence!!
        val verification = AutomaticVerification.verify(result.evidence)

        assertEquals(
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            ScanPresentationDecision.decide(
                result.outcome,
                verification,
                winner.document,
                automatic = true,
            ),
        )
        assertTrue(
            "a proposal keeps the photograph it is about",
            !ScanPresentationDecision.releasesCapture(ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE),
        )
    }

    /**
     * **The red label's `12` stays withheld through the Strategy B route too.**
     *
     * The crop origin must not become a way in: a reading whose scale nothing establishes is refused
     * whichever pass produced it and whatever space it was measured in.
     */
    @Test
    fun `a Strategy B reading with no scale evidence is still withheld`() {
        val crop = SelectedRegionCrop.PixelRect(left = 200, top = 300, width = 700, height = 400)
        // The red label's shape: a lone separatorless integer under an inferred per-100 column.
        val redCropLocal = OcrDocument(
            width = 700,
            height = 400,
            elements = listOf(
                OcrElement("por 100 g", OcrBox(300, 40, 540, 80), 0, 0),
                OcrElement("Hidratos de carbono", OcrBox(20, 140, 280, 180), 0, 1),
                OcrElement("12g", OcrBox(320, 140, 400, 180), 0, 1),
            ),
        )
        val result = resolveWithStrategyB(blindPassA(), redCropLocal, crop)
        val winner = result.outcome.winningEvidence!!
        val verification = AutomaticVerification.verify(result.evidence)

        assertTrue(
            "precondition: the parser must read 12 confidently, or this measures nothing",
            winner.reading is LabelReading.Confident,
        )
        val action = ScanPresentationDecision.decide(
            result.outcome,
            verification,
            winner.document,
            automatic = true,
        )
        // Twentieth session: an unsupported-scale reading with an otherwise sound row/clause/unit/
        // column now reaches an EXPLICIT visual-confirmation screen (CONFIRM_UNVERIFIED), never a
        // one-tap shortcut -- whichever pass read it. See ConfirmationEligibility.
        assertEquals(
            "an unsupported scale routes to explicit confirmation, whichever pass read it",
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
            action,
        )
        assertTrue(
            "must never be a one-tap shortcut past the user's own comparison",
            action != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE &&
                action != ScanPresentationDecision.Action.AUTO_ADVANCE,
        )
    }
}
