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
 *
 * ## Superseded by the twentieth session: routes to CONFIRM_UNVERIFIED, not RECOVERY
 *
 * This capture is exactly the shape task §5 names by name ("already resolved declared-serving
 * readings, such as `6 g / 18 g serving`, should open confirmation directly"): `RecoveryCandidates`
 * has already fully resolved ONE basis-complete, `ReadingEligibility`-eligible reading, so there is
 * nothing left to choose between — showing a menu ("tap the row" / "type it in" / this button) for a
 * question the app has already answered is exactly the friction §2's explicit-confirmation state
 * exists to remove. [ScanPresentationDecision.Action.RECOVERY] is still the fallback whenever
 * recovery has more than one candidate, or a candidate that does not normalize.
 */
class NineteenthSessionServingBasisRoutingTest {

    @Test
    fun `the Korean sauce US panel routes to CONFIRM_UNVERIFIED, not CROP_FALLBACK`() {
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
            "a panel with no per-100 column but exactly one resolvable declared-serving candidate " +
                "must open explicit confirmation directly, not CROP_FALLBACK and not the RECOVERY menu",
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
            decision,
        )

        // The candidate the confirmation screen would actually render, normalizing to the printed
        // 33.3 g/100g figure — proving `confirmationCandidateFor` and `decide` agree about what
        // qualifies, which is what a caller relies on never to diverge (see its own KDoc).
        val candidate = ScanPresentationDecision.confirmationCandidateFor(outcome, document)
        assertTrue("confirmationCandidateFor must resolve the same candidate decide() found", candidate != null)
        val normalized = candidate!!.reading.normalizedToPerHundred()
        assertTrue("the declared-serving reading must normalize to per-100", normalized != null)
        assertEquals(0, normalized!!.amount.setScale(1, java.math.RoundingMode.HALF_UP).compareTo(java.math.BigDecimal("33.3")))
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
