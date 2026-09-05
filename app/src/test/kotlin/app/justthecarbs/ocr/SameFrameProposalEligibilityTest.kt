package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Same-frame agreement is demoted, not deleted.
 *
 * ## The distinction one boolean was hiding
 *
 * [ReadingEligibility.evaluate]'s `corroborated` parameter answers *may this figure be **shown***.
 * [AutomaticVerification.Verdict.mayAdvanceAutomatically] answers *may this figure **skip the user's
 * tap***. `AutomaticScanAdvance` passed the second as the first, so the two questions shared one
 * flag.
 *
 * That was invisible until same-frame agreement stopped counting for advancement. On the first run of
 * the 21-capture replay, `20260904-113818-873` (a chocolate spread printing `57 g per 100 gram`) and
 * `20260904-114311-968` (a snack pack printing `koolhydraten 35 g`) both fell from
 * `CONFIRM_ON_CAPTURE` to `RECOVERY` — the app still held the correct value and stopped showing it.
 * Removing a wrong automatic route had silently removed a correct *proposal*.
 *
 * ## Why the two answers legitimately differ
 *
 * Two views of one photograph cannot settle **scale** or an **optical** corruption, because both
 * inherit the same pixels — that is the `0.59` case and it is why advancement now requires a second
 * observation. But they can still disagree about a *single misread digit* arising from tokenisation
 * or row association, and their agreeing is real evidence against that far commoner failure.
 *
 * So the ordering was: same-frame agreement is enough to put a number on screen behind a confirmation
 * tap; only an independent observation, or the label's own structure, is enough to remove the tap.
 *
 * **Corrected 2026-09-05.** That ordering is what let the Hellmann's `1,3 -> 13` misread reach a
 * one-tap `CONFIRM_ON_CAPTURE` card: `route = NONE` with `viewsAgree = true` is indistinguishable
 * from the `57`/`35` cases this file models, so a rule permissive enough to keep those on the card
 * admits `13` identically. `AutomaticScanAdvance.eligibility` now asks
 * `ReadingEligibility.evaluate(..., corroborationSettlesScale = verification.route !=
 * AutomaticVerification.Route.NONE)`, so same-frame agreement alone no longer earns the one-tap
 * card for an [ScaleAmbiguity.Verdict.Unsupported] reading — see `ScaleInvarianceTest`'s `41` case
 * and `SixthSessionRegressionTest`'s "withheld from confirmation" case for the caller-level pinning.
 * The tests below still hold at the [ReadingEligibility.evaluate] level: `corroborated = true` with
 * the default `corroborationSettlesScale = true` still reports `Eligible`, which is what makes
 * `corroborationSettlesScale` worth having as its own parameter rather than folding `corroborated`
 * into one boolean. What changed is which value the automatic path's one caller now passes for it.
 */
class SameFrameProposalEligibilityTest {

    private fun evidence(
        source: EvidenceSource,
        value: String,
        observation: PhysicalObservationId,
    ): RecognitionEvidence = RecognitionEvidence(
        source = source,
        report = NutritionParseReport(
            reading = LabelReading.Confident(
                CarbCandidate(
                    sourceLine = "Koolhydraten $value g",
                    label = "Koolhydraten",
                    value = BigDecimal(value),
                    basis = NutritionBasis.PER_100_G,
                    score = 100,
                    geometry = OcrBox(0, 0, 10, 10),
                    evidence = emptyList(),
                    column = NutritionColumnKind.PER_100_G,
                ),
            ),
            diagnostics = emptyList(),
        ),
        document = null,
        physicalObservation = observation,
    )

    private val frameA = PhysicalObservationId("FRAME_A")
    private val frameB = PhysicalObservationId("FRAME_B")

    /**
     * The regression this test exists to prevent.
     *
     * A bare integer whose scale is unsupported, agreed by two views of one frame, is eligible to be
     * **proposed**. The user sees `57` and confirms it; they are not sent to recovery.
     */
    @Test
    fun `same-frame agreement still makes an unsupported-scale reading proposable`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("57", "no paired value in this clause"),
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            corroborated = true,
        )

        assertTrue(
            "a value two views of one frame agree on may still be offered for confirmation",
            verdict is ReadingEligibility.Verdict.Eligible,
        )
    }

    /** But it may never remove the confirmation tap. */
    @Test
    fun `same-frame agreement never satisfies automatic verification`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "57", frameA),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "57", frameA),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
    }

    /**
     * The two questions are answered by two different inputs.
     *
     * Stated as its own case so a future refactor cannot quietly re-merge them: if
     * `proposalCorroboration` ever becomes an alias for `mayAdvanceAutomatically`, this fails.
     */
    @Test
    fun `proposal corroboration and advancement corroboration are distinct`() {
        val sameFrame = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "57", frameA),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "57", frameA),
        )

        assertTrue(
            "agreement across views is proposal-grade evidence",
            AutomaticVerification.agreesAcrossViews(sameFrame),
        )
        assertEquals(
            "…but it is not advancement-grade",
            AutomaticVerification.Route.NONE,
            AutomaticVerification.verify(sameFrame).route,
        )
    }

    /** An independent observation answers both questions at once. */
    @Test
    fun `an independent observation satisfies both questions`() {
        val independent = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "57", frameA),
            evidence(EvidenceSource.SECOND_OBSERVATION_PASS, "57", frameB),
        )

        assertTrue(AutomaticVerification.agreesAcrossViews(independent))
        assertEquals(
            AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT,
            AutomaticVerification.verify(independent).route,
        )
    }

    /** Disagreement is not agreement, whichever question is being asked. */
    @Test
    fun `views that disagree corroborate nothing`() {
        val disagreeing = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "57", frameA),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "5.7", frameA),
        )

        assertEquals(false, AutomaticVerification.agreesAcrossViews(disagreeing))
    }

    /** A lone reading has nothing to agree with it. */
    @Test
    fun `a single view corroborates nothing`() {
        val lone = listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, "57", frameA))

        assertEquals(false, AutomaticVerification.agreesAcrossViews(lone))
    }

    /**
     * Two parses of **one recognition** are not two views, for proposals either.
     *
     * `FILTERED_PASS_A` is a subset of `FULL_FRAME_PASS_A`'s own elements, carrying the same
     * characters, so their agreeing says only that the filter kept the winning row. This rule predates
     * the physical-observation work — grated cheese resolved to the known-wrong `2.09` exactly this
     * way — and the proposal path must not become the softer door it comes back through.
     *
     * Concretely: without this the eighth session's red-label `12` (confident, scale unsupported, read
     * by the two Pass A views alone) would become proposable again.
     */
    @Test
    fun `two parses of one recognition run corroborate nothing`() {
        val onePassTwoViews = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "12", frameA),
            evidence(EvidenceSource.FILTERED_PASS_A, "12", frameA),
        )

        assertEquals(false, AutomaticVerification.agreesAcrossViews(onePassTwoViews))
    }
}
