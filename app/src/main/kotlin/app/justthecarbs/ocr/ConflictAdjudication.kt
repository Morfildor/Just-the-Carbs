package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * Adjudicates disagreement between distinct (value, basis) groups of confident evidence using each
 * group's OWN document structure -- never by confidence, magnitude, frequency, source order, or
 * plausibility.
 *
 * A group is eliminated only when [CrossColumnRatioCheck] explicitly returns [CrossColumnRatioCheck.Verdict.Conflicting]
 * for it AND a competing group gets [CrossColumnRatioCheck.Verdict.Consistent] from the same check.
 * A group whose check returns [CrossColumnRatioCheck.Verdict.NotEnoughEvidence] is never eliminated
 * merely because a competing group happens to be [CrossColumnRatioCheck.Verdict.Consistent] --
 * "supported vs uncheckable" stays a conflict, because absence of contradiction is not the same
 * claim as presence of support.
 */
object ConflictAdjudication {

    sealed interface AdjudicationResult {
        data class SingleSupported(val evidence: List<RecognitionEvidence>) : AdjudicationResult
        data class StillConflicted(val groups: Map<Pair<BigDecimal, NutritionBasis?>, List<RecognitionEvidence>>) : AdjudicationResult
    }

    fun adjudicate(
        groups: Map<Pair<BigDecimal, NutritionBasis?>, List<RecognitionEvidence>>,
    ): AdjudicationResult {
        data class Judged(
            val key: Pair<BigDecimal, NutritionBasis?>,
            val evidence: List<RecognitionEvidence>,
            val verdict: CrossColumnRatioCheck.Verdict?,
        )

        val judged = groups.map { (key, groupEvidence) ->
            val primary = groupEvidence.first()
            val document = primary.document
            val candidate = (primary.reading as? LabelReading.Confident)?.candidate
            val verdict = if (document != null && candidate != null) {
                CrossColumnRatioCheck.check(document, candidate)
            } else {
                null
            }
            Judged(key = key, evidence = groupEvidence, verdict = verdict)
        }

        val anySupported = judged.any { it.verdict is CrossColumnRatioCheck.Verdict.Consistent }
        val remaining = judged.filterNot { it.verdict is CrossColumnRatioCheck.Verdict.Conflicting && anySupported }
        val supportedRemaining = remaining.filter { it.verdict is CrossColumnRatioCheck.Verdict.Consistent }

        return if (supportedRemaining.size == 1 && remaining.size == 1) {
            AdjudicationResult.SingleSupported(supportedRemaining.single().evidence)
        } else {
            AdjudicationResult.StillConflicted(groups)
        }
    }
}
