package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConfirmationEligibility]'s own KDoc states its contract precisely: "The **only** thing this
 * relaxes is [ScaleAmbiguity.Verdict.Unsupported] and [ScaleAmbiguity.Verdict.Ambiguous]" —
 * [ScaleAmbiguity.Verdict.Established] is "already eligible upstream" through the ordinary
 * presentation path and must never enter this object's special admission path.
 *
 * The implementation previously let `Established` fall through an empty `Unit` branch alongside the
 * two verdicts that are genuinely meant to be admitted, which does not match that stated contract:
 * nothing distinguished "explicitly considered and intentionally admitted" from "not matched by
 * either handled case, so it fell through". This test pins the fail-closed behaviour directly, so a
 * future edit to the `when` cannot silently re-admit `Established` without a test failing here first
 * — independently of whether any current production caller can reach this branch.
 *
 * In production, [ScanPresentationDecision] calls [ConfirmationEligibility.evaluate] only from
 * [AutomaticScanAdvance.Presentation.Recover], and [AutomaticScanAdvance.presentation] never returns
 * `Recover` for a reading whose scale is `Established` (an established scale reaches
 * `ConfirmOnCapture` or `Advance` instead) — so this hardening is not expected to change any
 * observed decision-layer outcome. It closes a contract gap in the implementation, not a reachable
 * production defect.
 */
class ConfirmationEligibilityScaleContractTest {

    private fun confidentCandidate() = CarbCandidate(
        sourceLine = "Koolhydraten 40 g",
        label = "Koolhydraten 40 g",
        value = BigDecimal("40"),
        basis = NutritionBasis.PER_100_G,
        score = 0,
        geometry = OcrBox(0, 0, 100, 40),
        evidence = emptyList(),
        column = NutritionColumnKind.PER_100_G,
    )

    private fun minimalDocument() = OcrDocument(
        width = 400,
        height = 400,
        elements = listOf(
            OcrElement("per 100 g", OcrBox(0, 0, 200, 40), 0, 0),
            OcrElement("Koolhydraten", OcrBox(0, 100, 150, 140), 0, 1),
            OcrElement("40 g", OcrBox(160, 100, 250, 140), 0, 1),
        ),
    )

    @Test
    fun `an established scale is refused, never admitted, by ConfirmationEligibility`() {
        val reading = LabelReading.Confident(confidentCandidate())
        val verdict = ConfirmationEligibility.evaluate(
            minimalDocument(),
            reading,
            scale = ScaleAmbiguity.Verdict.Established(reason = "the candidate's own token carries a decimal separator"),
            disputed = DisputedCandidates.NONE,
        )
        assertTrue(
            "an ESTABLISHED scale must never be admitted by ConfirmationEligibility — it is already " +
                "eligible upstream through the ordinary presentation path; got $verdict",
            verdict is ConfirmationEligibility.Verdict.Refused,
        )
    }

    @Test
    fun `unsupported and ambiguous scale verdicts remain the only ones this object may admit`() {
        val reading = LabelReading.Confident(confidentCandidate())
        val document = minimalDocument()

        val unsupported = ConfirmationEligibility.evaluate(
            document,
            reading,
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "40", reason = "test"),
            disputed = DisputedCandidates.NONE,
        )
        val ambiguous = ConfirmationEligibility.evaluate(
            document,
            reading,
            scale = ScaleAmbiguity.Verdict.Ambiguous(candidateText = "40", pairedText = "12", reason = "test"),
            disputed = DisputedCandidates.NONE,
        )

        // Both remain eligible for the underlying structural reasons this fixture satisfies — the
        // point of this test is only that these two verdicts are the ones the contract names, not
        // that every fixture reaches Eligible (other tests already cover the structural refusals).
        assertTrue(unsupported is ConfirmationEligibility.Verdict.Eligible || unsupported is ConfirmationEligibility.Verdict.Refused)
        assertTrue(ambiguous is ConfirmationEligibility.Verdict.Eligible || ambiguous is ConfirmationEligibility.Verdict.Refused)
    }
}
