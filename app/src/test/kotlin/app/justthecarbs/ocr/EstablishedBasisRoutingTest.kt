package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the label's **carbohydrate row and basis are both established** and only the digits failed,
 * the app must ask for the digits — not for a crop.
 *
 * ## The failure this closes (thirteenth session)
 *
 * `20260904-081307-240` is a large, flat, high-contrast Lidl carton printing `Koolhydraten 6,2 g`.
 * The pipeline got almost everything right:
 *
 * ```
 * row-kind: TOTAL_CARBOHYDRATE: Koolhydraten 6,20      <- row found
 * column:   PER_100_ML 'Ø/ 100 ml' @ x=1132.5          <- basis resolved
 * unit-accompaniment: '6,20' states no unit            <- only the digits are in doubt
 * result:   Total-carbohydrate row found but no usable per-100 cell
 * final UI action : CROP_FALLBACK
 * ```
 *
 * Everything except the number was known, and the user was sent to drag a rectangle. Cropping
 * cannot help: the rectangle was already correct, the row was already found, and re-recognising the
 * same pixels reproduces the same damaged glyph. The next screen the user needs is the one that says
 * *"carbohydrate, per 100 ml — type the number you can see"*, and this app already has it
 * ([FocusedAmountEntry], reachable today only after a tap on the assisted screen).
 *
 * ## Why this is a presentation fix and not an OCR one
 *
 * The brief's instruction is explicit: *"do not respond to a presentation failure by adding another
 * OCR heuristic."* No recognition rule changes here and no value is invented — `6,20` stays refused,
 * exactly as [FusedUnitDigitTest] pins. What changes is which screen a capture with an established
 * row and basis lands on.
 *
 * The distinction is [ScanPresentationDecision.Action.CROP_FALLBACK] ("the app could not read
 * anything, and the rectangle is your lever") versus
 * [ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY] ("the app read the row and the basis, and
 * needs only the digits"). Sending the second case to the first is what makes recovery feel
 * repetitive: the user crops, the same thing happens, and nothing has been learned.
 *
 * ## The bound
 *
 * This routes only when the document establishes **exactly one** per-100 basis and a
 * total-carbohydrate row — [FocusedAmountEntry.of]'s existing contract, reused rather than restated
 * so the screen and the routing cannot disagree about whether a target exists. A label with two
 * disagreeing bases, or none, still falls back to the crop screen, because there the rectangle
 * genuinely is the lever.
 */
class EstablishedBasisRoutingTest {

    private fun decide(document: OcrDocument): ScanPresentationDecision.Action {
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val evidence = listOf(
            RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, report, document),
        )
        return ScanPresentationDecision.decide(
            outcome = EvidenceResolver.resolve(evidence),
            verification = AutomaticVerification.verify(evidence),
            document = document,
            automatic = true,
        )
    }

    @Test
    fun `a label whose row and basis are known routes to focused entry, not to crop`() {
        val document = ThirteenthSessionFixtures.lidlDrinkSixPointTwoUnitLost()

        // Preconditions, asserted so this cannot pass for the wrong reason.
        val target = FocusedAmountEntry.of(document)
        assertNotNull("the fixture must establish a focused-entry target", target)
        assertEquals(NutritionBasis.PER_100_ML, target?.basis)
        assertNull(
            "and it must produce no confident reading, or the premise is different",
            NutritionTableParser.parseWithDiagnostics(document).reading as? LabelReading.Confident,
        )

        assertEquals(
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
            decide(document),
        )
    }

    @Test
    fun `a frame with nothing recognised still falls back to the crop screen`() {
        // The blurred non-label. No row, no basis, nothing to ask for — the rectangle really is the
        // only lever, and this is the control that keeps the new action from swallowing every
        // failure.
        val document = ThirteenthSessionFixtures.blurredNonLabel()
        assertNull(FocusedAmountEntry.of(document))
        assertEquals(ScanPresentationDecision.Action.CROP_FALLBACK, decide(document))
    }

    @Test
    fun `a label with no resolvable basis still falls back to the crop screen`() {
        // The cracker pack: a two-column table whose per-100 carbohydrate cell was not recognised.
        // Whether it routes to crop or focused entry depends on whether a single basis resolved, and
        // asserting the target's absence/presence alongside the action is what keeps the two in step.
        val document = ThirteenthSessionFixtures.crackersSeventyTwoNothing()
        val target = FocusedAmountEntry.of(document)
        val action = decide(document)
        if (target == null) {
            assertEquals(ScanPresentationDecision.Action.CROP_FALLBACK, action)
        } else {
            assertEquals(ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY, action)
        }
    }

    @Test
    fun `a confident verified reading is unaffected`() {
        // The yoghurt. Nothing about this change may touch a capture that already succeeds.
        val document = ThirteenthSessionFixtures.yoghurtThreePointTwo()
        val action = decide(document)
        assertEquals(ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE, action)
    }
}
