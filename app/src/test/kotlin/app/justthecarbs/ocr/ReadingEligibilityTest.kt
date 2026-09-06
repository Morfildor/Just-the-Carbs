package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [ReadingEligibility.evaluate]'s own `corroborationSettlesScale` distinction directly, ahead
 * of the caller fix in [AutomaticScanAdvanceTest].
 *
 * `evaluate` already implements this correctly — see its own KDoc at lines 130-218. These two cases
 * exist so a future change to the caller ([AutomaticScanAdvance.eligibility]) cannot silently drift
 * away from what `evaluate` was actually asked, by pinning the object's own behaviour independently
 * of any one caller.
 */
class ReadingEligibilityTest {

    @Test fun `missing scale evidence cannot be settled by same-image agreement`() {
        val verdict = ReadingEligibility.evaluate(
            scale = null,
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(app.justthecarbs.domain.NutritionBasis.PER_100_G),
            corroborated = true,
            corroborationSettlesScale = false,
        )
        org.junit.Assert.assertFalse(verdict.isEligible)
    }

    @Test fun `Unsupported scale corroborated only by same-photograph view agreement is refused, not shown`() {
        // Reproduces the documented Hellmann's case: 1.3 g/100ml printed, every view of one
        // photograph reads 13g. Same-photograph agreement (agreesAcrossViews) must NOT be
        // sufficient to make this Eligible -- only a genuinely distinct physical observation
        // (a second photograph) or the label's own cross-column structure may.
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "13", reason = "no separator present"),
            basis = CarbBasis.PerHundred(NutritionBasis.PER_100_ML),
            corroborated = true,
            corroborationSettlesScale = false, // same-photograph agreement, not a distinct observation
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Refused)
    }

    @Test fun `Unsupported scale corroborated by cross-column table structure IS eligible`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "41", reason = "no separator present"),
            basis = CarbBasis.PerHundred(NutritionBasis.PER_100_ML),
            corroborated = true,
            corroborationSettlesScale = true, // cross-column table structure
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Eligible)
    }

    /**
     * The nineteenth session's correction: agreement between two distinct *physical observations*
     * (a live frame and a still, or two stills) is real evidence against a single-run tokenisation
     * slip, but it is NOT evidence against a *systematic* misread — one the same optical conditions
     * (a damaged glyph, a fused decimal comma) reproduce identically in every observation.
     *
     * `docs/Scan evidence 06-09/20260906-123352-975` is the measured case: `LIVE_STABLE_FRAME` and
     * `SELECTED_REGION_OCR` are two genuinely different [PhysicalObservationId]s, both reading
     * `12.0/PER_100_G` where the package prints `7,2 g/100g`. Both observations recognised the same
     * fused glyph the same way, because the *ink itself* — not one recognition run's noise — is what
     * produced the ambiguity. So [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] must NOT be
     * treated as `corroborationSettlesScale = true`; only [AutomaticVerification.Route.CROSS_COLUMN]
     * — evidence from the label's *other* rows, never a re-observation of the same clause — may.
     *
     * The earlier version of this test asserted the opposite and is corrected here. See
     * [NineteenthSessionBaselineTest] for the full replay of the capture this pins.
     */
    @Test fun `Unsupported scale corroborated only by a second physical observation is still refused`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "12", reason = "no separator present"),
            basis = CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            corroborated = true,
            // A second physical observation agreeing is not cross-column structural evidence, and
            // must not settle scale on its own -- see AutomaticScanAdvance.eligibility, which now
            // keys corroborationSettlesScale on the verification ROUTE (CROSS_COLUMN only), never on
            // whether the observations were merely distinct.
            corroborationSettlesScale = false,
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Refused)
    }
}
