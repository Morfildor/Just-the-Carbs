package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The nineteenth session's 23 captures (`docs/Scan evidence 06-09/`, a Samsung device), replayed
 * through the real production pipeline exactly as recorded in each bundle's `selection.txt`.
 *
 * ## Purpose
 *
 * This file pins the pipeline's behaviour on the two most important captures of the session, using
 * each bundle's own recorded evidence (`passes contributing evidence`, `scale evidence`,
 * `automatic-verification`, `final UI action`). It exists so the fix can be shown to change exactly
 * the case it targets and nothing else.
 *
 * The headline defect, now fixed: `redLidl123352` showed `LIVE_STABLE_FRAME` and
 * `SELECTED_REGION_OCR` — two genuinely different [PhysicalObservationId]s — both reading
 * `12.0/PER_100_G` where the package prints `7,2 g/100g`. [AutomaticVerification] correctly reports
 * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT], and that route used to let
 * [ReadingEligibility] treat the figure as eligible for one-tap confirmation even though
 * [ScaleAmbiguity] independently reports the same candidate as
 * [ScaleAmbiguity.Verdict.Unsupported] (a lone separatorless value). Two distinct physical
 * observations agreeing is evidence against a single-run tokenisation slip; it is not evidence
 * against a *systematic* misread that the same optical conditions reproduce in every observation —
 * which is exactly what happened here. [AutomaticScanAdvance.eligibility] now requires
 * [AutomaticVerification.Route.CROSS_COLUMN] specifically to settle [ScaleAmbiguity
 * .Verdict.Unsupported]; `DISTINCT_OCR_AGREEMENT` alone no longer does.
 */
class NineteenthSessionBaselineTest {

    private fun buildEvidence(
        stillDocument: OcrDocument,
        selectedRegionDocument: OcrDocument? = null,
        cropOrigin: SelectedRegionCrop.PixelRect? = null,
        liveValue: BigDecimal? = null,
        liveBasis: NutritionBasis? = null,
    ): List<RecognitionEvidence> {
        val stillId = PhysicalObservationId.forStill("capture.jpg")
        val fullFrameReport = NutritionTableInterpreter.interpret(stillDocument)
        val evidence = mutableListOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = fullFrameReport,
                document = stillDocument,
                physicalObservation = stillId,
            ),
        )
        if (selectedRegionDocument != null && cropOrigin != null) {
            val report = NutritionTableInterpreter.interpret(selectedRegionDocument)
            evidence += RecognitionEvidence(
                source = EvidenceSource.SELECTED_REGION_OCR,
                report = report,
                document = selectedRegionDocument,
                crop = cropOrigin,
                physicalObservation = PhysicalObservationId.forStill("capture.jpg"),
            )
        }
        if (liveValue != null && liveBasis != null) {
            evidence += RecognitionEvidence(
                source = EvidenceSource.LIVE_STABLE_FRAME,
                report = NutritionParseReport(
                    LabelReading.Confident(
                        CarbCandidate(
                            sourceLine = "live",
                            label = "live",
                            value = liveValue,
                            basis = liveBasis,
                            score = 0,
                            geometry = OcrBox(0, 0, 1, 1),
                            evidence = emptyList(),
                            column = null,
                        ),
                    ),
                    emptyList(),
                ),
                document = null,
                physicalObservation = PhysicalObservationId.forLiveSnapshot(0L, "capture.jpg"),
            )
        }
        return evidence
    }

    /**
     * `20260906-123352-975` — the red Lidl label, replayed with the bundle's own evidence set.
     *
     * Precondition asserts the fixture still reproduces the misread `12.0` and its
     * `DISTINCT_OCR_AGREEMENT` verification exactly as recorded — if that ever stops being true the
     * fixture has drifted and the final assertion is meaningless. The fix: this now routes to
     * `RECOVERY` (via `Presentation.Recover`), never `CONFIRM_ON_CAPTURE`.
     */
    @Test
    fun `fixed - red Lidl 123352 no longer reaches CONFIRM_ON_CAPTURE with the wrong value`() {
        val stillDocument = NineteenthSessionFixtures.redLidl123352()
        val selectedRegion = NineteenthSessionStrategyBFixtures.redLidl123352StrategyB()
        val crop = SelectedRegionCrop.PixelRect(left = 0, top = 671, width = 1684, height = 2305)

        val evidence = buildEvidence(
            stillDocument = stillDocument,
            selectedRegionDocument = selectedRegion,
            cropOrigin = crop,
            liveValue = BigDecimal("12.0"),
            liveBasis = NutritionBasis.PER_100_G,
        )

        val outcome = EvidenceResolver.resolve(evidence)
        assertTrue(
            "precondition: must resolve to a confident 12.0 reading, was $outcome",
            outcome is EvidenceResolver.Outcome.Resolved &&
                (outcome.reading as? LabelReading.Confident)?.candidate?.value?.compareTo(
                    BigDecimal("12.0"),
                ) == 0,
        )

        val verification = AutomaticVerification.verify(evidence)
        assertEquals(
            "precondition: must verify via DISTINCT_OCR_AGREEMENT as recorded in the bundle",
            AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT,
            verification.route,
        )

        val document = outcome.winningEvidence?.document
        val decision = ScanPresentationDecision.decide(
            outcome = outcome,
            verification = verification,
            document = document,
            automatic = true,
        )

        // THE FIX: the wrong value no longer reaches a one-tap confirmation. It still reaches the
        // user, via RECOVERY (Presentation.Recover) -- the photograph, the highlighted row and the
        // basis are preserved, and the user must type the digits rather than confirm a misread one.
        assertEquals(
            "distinct-run agreement alone must no longer promote an Unsupported-scale reading to " +
                "a one-tap confirmation",
            ScanPresentationDecision.Action.RECOVERY,
            decision,
        )
    }

    /**
     * `20260906-123650-430` / `-123657-089` — the protein bar, correctly read as `40.0/PER_100_G`
     * (ground truth, confirmed against the photograph) and independently verified by
     * [AutomaticVerification.Route.CROSS_COLUMN] with 9 supporting rows, but withheld to RECOVERY
     * because [ScaleAmbiguity] demonstrates the `40`/`10` pair could be a uniform rescaling.
     *
     * This is the correct and conservative behaviour per the architecture's own stated rule — a
     * cross-column ratio is scale-invariant and cannot rule out a uniform ×10 error — and this test
     * pins it as a baseline **cost**, not a bug to silently "fix" by trusting the ratio.
     */
    @Test
    fun `baseline - protein bar 123650 is correctly withheld despite strong corroboration`() {
        val stillDocument = NineteenthSessionFixtures.proteinBar123650()
        val evidence = buildEvidence(
            stillDocument = stillDocument,
            liveValue = BigDecimal("40.0"),
            liveBasis = NutritionBasis.PER_100_G,
        )

        val outcome = EvidenceResolver.resolve(evidence)
        assertTrue(
            "precondition: must resolve to a confident 40.0 reading, was $outcome",
            outcome is EvidenceResolver.Outcome.Resolved &&
                (outcome.reading as? LabelReading.Confident)?.candidate?.value?.compareTo(
                    BigDecimal("40.0"),
                ) == 0,
        )

        val verification = AutomaticVerification.verify(evidence)
        assertEquals(
            "precondition: must verify via CROSS_COLUMN as recorded in the bundle",
            AutomaticVerification.Route.CROSS_COLUMN,
            verification.route,
        )
        assertTrue(
            "precondition: must have strong support as recorded (support=9)",
            verification.supportingRows >= 9,
        )

        val document = outcome.winningEvidence?.document
        val scaleVerdict = AutomaticScanAdvance.scaleVerdict(outcome, document)
        assertTrue(
            "precondition: scale must be demonstrated Ambiguous, was $scaleVerdict",
            scaleVerdict is ScaleAmbiguity.Verdict.Ambiguous,
        )

        val decision = ScanPresentationDecision.decide(
            outcome = outcome,
            verification = verification,
            document = document,
            automatic = true,
        )

        assertEquals(
            "a demonstrated pair ambiguity is withheld even under strong cross-column support",
            ScanPresentationDecision.Action.RECOVERY,
            decision,
        )
    }
}
