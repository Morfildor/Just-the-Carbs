package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/Scan evidence 06-09/20260906-123544-918` — a Sempio Korean sauce, US-style Nutrition Facts
 * panel with no per-100 column at all.
 *
 * ## The gap this closes
 *
 * The main pipeline correctly reports [CarbFailureReason.BASIS_MISSING] — no per-100 column exists
 * for a linear panel to resolve, which is a true and structurally-correct statement. Separately,
 * [RecoveryCandidates.of] — consulting [ServingDeclaration] the same way it always has — already
 * resolves `6 g / 18 g serving` (normalizing to `33.3 g/100g`), because the panel prints
 * `Serv. size: 1Tbsp (18 g)` and `Total Carb. 6 g` in the ordinary shape that object recognises.
 *
 * Neither fact was wrong. The defect was in [ScanPresentationDecision]: its veto fallback checked
 * only [FocusedAmountEntry.of] (per-100-column shapes) before giving up to
 * [ScanPresentationDecision.Action.CROP_FALLBACK] — which opens the crop-confirmation screen, not
 * the recovery screen where [RecoveryCandidates]'s already-resolved answer would actually render.
 * A rectangle cannot fix a basis question a rectangle never answered, so the crop screen is the
 * wrong fallback whenever recovery already has something to offer.
 */
class NineteenthSessionServingBasisRoutingTest {

    @Test
    fun `the Korean sauce US panel routes to RECOVERY, not CROP_FALLBACK`() {
        val document = NineteenthSessionFixtures.koreanSauce123544()
        val report = NutritionTableInterpreter.interpret(document)

        // Precondition: the main pipeline must still report no usable per-100 reading, or this test
        // is measuring a different rule.
        assertEquals(
            "precondition: the main pipeline must find no total-carbohydrate value at all",
            LabelReading.NotFound,
            report.reading,
        )
        assertEquals(
            "precondition: FocusedAmountEntry must not resolve a per-100 target on this panel",
            null,
            FocusedAmountEntry.of(document),
        )

        // Precondition: recovery must already have a basis-complete answer to offer.
        val candidates = RecoveryCandidates.of(document)
        assertTrue(
            "precondition: RecoveryCandidates must already resolve the declared-serving reading, " +
                "was $candidates",
            candidates.any { it.reading.amount.compareTo(java.math.BigDecimal("6")) == 0 },
        )

        val evidence = listOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = report,
                document = document,
                physicalObservation = PhysicalObservationId.forStill("capture.jpg"),
            ),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        val decision = ScanPresentationDecision.decide(
            outcome = outcome,
            verification = verification,
            document = document,
            automatic = true,
        )

        assertEquals(
            "a panel with no per-100 column but a resolvable declared-serving candidate must route " +
                "to RECOVERY, where that candidate is shown, not to CROP_FALLBACK",
            ScanPresentationDecision.Action.RECOVERY,
            decision,
        )
    }

    /**
     * Negative control: a document with genuinely nothing to offer (no per-100 column, no resolvable
     * recovery candidate at all) must still fall to [ScanPresentationDecision.Action.CROP_FALLBACK].
     * This is what proves the fix is a narrowing, not a blanket "always prefer RECOVERY".
     */
    @Test
    fun `a document with no recovery candidates at all still falls to CROP_FALLBACK`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("some unrelated text", OcrBox(0, 0, 200, 40), 0, 0),
            ),
        )
        val report = NutritionTableInterpreter.interpret(document)
        assertTrue(
            "precondition: RecoveryCandidates must resolve nothing on this document",
            RecoveryCandidates.of(document).isEmpty(),
        )

        val evidence = listOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = report,
                document = document,
                physicalObservation = PhysicalObservationId.forStill("capture.jpg"),
            ),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        val decision = ScanPresentationDecision.decide(
            outcome = outcome,
            verification = verification,
            document = document,
            automatic = true,
        )

        assertEquals(
            ScanPresentationDecision.Action.CROP_FALLBACK,
            decision,
        )
    }
}
