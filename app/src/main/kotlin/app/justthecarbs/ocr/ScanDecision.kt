package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The scanner's one final answer: what to show the user, given a bundle of recognition evidence.
 *
 * Every existing policy object ([EvidenceResolver], [AutomaticVerification], [ScaleAmbiguity],
 * [ReadingEligibility], [AutomaticScanAdvance], [ScanPresentationDecision]) already makes its own
 * correct judgement. This type does not re-decide any of them -- it is the terminal shape their
 * combined judgement is expressed as, so a caller (the UI) executes an exhaustive `when` over five
 * variants instead of asking six separately-named objects six separately-named questions.
 *
 * [AutoAccept] is the only variant that may navigate without a user tap. Its [VerifiedReading] is
 * only ever constructed by [ScanDecisionEngine] from a bundle that already passed every existing
 * automatic-advance gate -- see [ScanDecisionEngine.decide].
 */
sealed interface ScanDecision {

    /** A reading verified strongly enough to skip both confirmations entirely. */
    data class AutoAccept(val reading: VerifiedReading) : ScanDecision

    /** A confident, scale-established reading, not yet independently verified -- one tap confirms it. */
    data class Confirm(val value: BigDecimal, val basis: NutritionBasis, val rowText: String) : ScanDecision

    /** The row and basis are known; the digits are not safe to prefill. Amount starts blank. */
    data class FocusedEntry(val basis: NutritionBasis, val rowText: String) : ScanDecision

    /** Confident evidence disagreed and nothing resolves it. No candidate is preselected. */
    data class Conflict(val values: List<String>) : ScanDecision

    /** Nothing could be read or placed; the crop rectangle is the user's remaining lever. */
    data object Crop : ScanDecision
}

/**
 * A reading that has been verified strongly enough to reach the user with no confirmation step.
 *
 * The constructor is private: the only way to obtain one is [ScanDecisionEngine.decide] deciding
 * every gate already held. This is what makes "AutoAccept requires verification" a type-level fact
 * rather than a rule a caller must remember to check.
 */
data class VerifiedReading private constructor(
    val value: BigDecimal,
    val basis: NutritionBasis,
    val rowText: String,
) {
    companion object {
        internal fun of(value: BigDecimal, basis: NutritionBasis, rowText: String) =
            VerifiedReading(value, basis, rowText)
    }
}
