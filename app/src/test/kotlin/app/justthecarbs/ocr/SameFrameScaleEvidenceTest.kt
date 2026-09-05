package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same-frame agreement is evidence about **digits**, never about **decimal scale**.
 *
 * ## The capture that draws the line
 *
 * `docs/Scan Evidence new structure/20260904-113950-065` — a Hellmann's bottle printing
 * `Koolhydraten / Glucides 1,3 g per 100 ml`. Every view of that one photograph read `13g`: the
 * decimal separator is absent from the *pixels*, so looking again at those pixels reproduces its
 * absence. The device offered `13.0 / 100 ml` for confirmation — ten times the printed figure.
 *
 * [ScaleAmbiguity] returned [ScaleAmbiguity.Verdict.Unsupported] rather than
 * [ScaleAmbiguity.Verdict.Ambiguous], because the paired portion cell was recognised as `<l5q` and is
 * not numeric — so there was no pair to demonstrate a common rescaling with, and the rule correctly
 * reported that it had nothing to say.
 *
 * ## Why agreement must not fill that gap
 *
 * `Unsupported` is documented as "not a refusal by itself: a caller holding other evidence is
 * entitled to proceed". The question this test settles is *which* other evidence qualifies.
 *
 * Agreement between views of one photograph does not, and the reason is the same arithmetic that
 * removed [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] from same-frame views: **evidence
 * unchanged by multiplying every recognised value by ten cannot establish absolute decimal scale.**
 * When a separator is missing from the ink, every view agrees on the wrong scale perfectly.
 *
 * ## The mechanism exists, and the automatic path now DOES use it (reversed 2026-09-05)
 *
 * [ReadingEligibility.evaluate] takes `corroborationSettlesScale` so a caller *can* say its
 * corroboration is scale-blind, and the cases below pin that behaviour. **Corrected 2026-09-05:**
 * this KDoc previously stated that the automatic path passes `true` unconditionally, as a measured
 * decision. `AutomaticScanAdvance.eligibility` now computes it from the real verification route
 * (`verification.route != AutomaticVerification.Route.NONE`), so same-observation agreement alone
 * (`route = NONE`) yields `false` here exactly as the case below models — the caller finally asks
 * the question this file's own tests were written to answer.
 *
 * All three separatorless integers in the 21-capture corpus are [ScaleAmbiguity.Verdict.Unsupported]
 * for the identical reason — no paired value inside the carbohydrate clause — so nothing distinguishes
 * them:
 *
 * | capture | printed | read | correct |
 * |---|---|---|---|
 * | `113818-873` | `57 g`  | `57`   | yes |
 * | `114311-968` | `35 g`  | `35g.` | yes |
 * | `113950-065` | `1,3 g` | `13g`  | no  |
 *
 * **The trade below was previously accepted and is now rejected.** Passing `false` hides `57` and
 * `35` from the one-tap confirmation card — two correct readings pushed one screen further, into
 * recovery/focused entry — to close the one wrong proposal that no content-based rule can repair:
 * `1,3` appears in **no** recognition of that capture, so the correct value is absent from the
 * evidence entirely and only a different photograph (or the label's own structure) can supply it.
 * The 2026-09-05 evidence-reliability plan's global constraint states why the trade reverses:
 * "Unsupported decimal scale from a single physical observation must never prefill a value for
 * one-tap acceptance" — a rule permissive enough to keep `57`/`35` on the card is, by the identical
 * evidence shape, permissive enough to put `13` there too. `57` and `35` are not lost, only slowed:
 * see `FifteenthSessionReplayTest`/`SeventeenthSessionReplayTest`'s named same-observation
 * exceptions.
 *
 * The route that actually restores the one-tap card for these two is a second physical observation
 * (`Route.DISTINCT_OCR_AGREEMENT`) or the label's own structure (`Route.CROSS_COLUMN`), which is why
 * the parameter is kept and pinned rather than deleted.
 *
 * ## What this deliberately does not do
 *
 * It does not turn `13` into `1.3`. No digit is manufactured and no separator is inserted.
 */
class SameFrameScaleEvidenceTest {

    private val perHundred = CarbBasis.PerHundred(NutritionBasis.PER_100_ML)

    /**
     * The `13 g` case.
     *
     * A lone separatorless value that only same-frame views agree on is not proposable: the agreement
     * cannot see the missing separator.
     */
    @Test
    fun `same-frame agreement does not establish the scale of a separatorless value`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(
                "13g",
                "no paired value in this clause to share a scale with",
            ),
            basis = perHundred,
            corroborated = true,
            corroborationSettlesScale = false,
        )

        assertTrue(
            "a separatorless value agreed only by views of one photograph must not be offered",
            verdict is ReadingEligibility.Verdict.Refused,
        )
    }

    /**
     * An independent observation *may* settle it.
     *
     * A second photograph has its own focus and its own blur, so its agreeing on `13` is genuine
     * evidence that `13` is what is printed — the separator's absence is no longer shared.
     */
    @Test
    fun `an independent observation does establish the scale of a separatorless value`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("13g", "no paired value"),
            basis = perHundred,
            corroborated = true,
            corroborationSettlesScale = true,
        )

        assertTrue(
            "a second photograph agreeing is scale evidence",
            verdict is ReadingEligibility.Verdict.Eligible,
        )
    }

    /**
     * A value whose own token carries a separator is unaffected.
     *
     * This is the common case and the one that must not be damaged: `6,2g` states its own scale, so
     * no corroboration of any kind is needed and the figure is offered exactly as before.
     */
    @Test
    fun `a value carrying its own separator is unaffected`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Established("the candidate's own token carries a separator"),
            basis = perHundred,
            corroborated = false,
            corroborationSettlesScale = false,
        )

        assertTrue(verdict is ReadingEligibility.Verdict.Eligible)
    }

    /**
     * Same-frame agreement still supports a reading whose scale is established.
     *
     * The demotion is confined to the scale question; agreement keeps every other job it had, which
     * is what stops this becoming a blanket refusal.
     */
    @Test
    fun `same-frame agreement still supports an established-scale reading`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Established("separator present"),
            basis = perHundred,
            corroborated = true,
            corroborationSettlesScale = false,
        )

        assertTrue(verdict is ReadingEligibility.Verdict.Eligible)
    }

    /**
     * A declared serving basis is still its own escape hatch.
     *
     * The Korean sauce's `6 g / 18 g serving` is offerable because the *label* printed the pairing,
     * which is evidence from the package rather than from a recognition. Unchanged here, and pinned
     * so the scale demotion cannot quietly delete it.
     */
    @Test
    fun `a declared serving basis is still eligible without scale corroboration`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("6", "no paired value"),
            basis = CarbBasis.PerUnknownServing("portie"),
            corroborated = false,
            corroborationSettlesScale = false,
        )

        assertTrue(verdict is ReadingEligibility.Verdict.Eligible)
    }
}
