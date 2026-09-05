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

    @Test fun `Unsupported scale corroborated by a genuinely distinct physical observation IS eligible`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "41", reason = "no separator present"),
            basis = CarbBasis.PerHundred(NutritionBasis.PER_100_ML),
            corroborated = true,
            corroborationSettlesScale = true, // a second photograph, or cross-column structure
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Eligible)
    }
}
