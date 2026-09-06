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
            // A group whose OWN views disagree (one supports it, another contradicts it) is neither
            // "supported" nor cleanly "contradicted" -- it is unresolvable on its own evidence, and
            // must not be settled by what a *different* group's table says. Kept apart from a group
            // that is purely Conflicting, which CAN be cleanly eliminated by a competing Consistent
            // group: those are different claims (one is "my own views disagree", the other is "no
            // view here supports me").
            val internallyDisputed: Boolean,
        )

        val judged = groups.map { (key, groupEvidence) ->
            val verdicts = groupEvidence.map { evidence ->
                val document = evidence.document
                val candidate = (evidence.reading as? LabelReading.Confident)?.candidate
                if (document != null && candidate != null) CrossColumnRatioCheck.check(document, candidate)
                else null
            }
            val support = verdicts.filterIsInstance<CrossColumnRatioCheck.Verdict.Consistent>()
            val contradiction = verdicts.filterIsInstance<CrossColumnRatioCheck.Verdict.Conflicting>()
            // An uncheckable view (null) cannot manufacture support on its own, but it also cannot be
            // dismissed by a contradiction it took no part in -- that is why "all null" falls through
            // to uncheckable rather than being folded into either verdict.
            val verdict = when {
                support.isNotEmpty() && contradiction.isEmpty() -> support.first()
                contradiction.isNotEmpty() && support.isEmpty() -> contradiction.first()
                contradiction.isNotEmpty() -> contradiction.first()
                else -> null
            }
            Judged(
                key = key,
                evidence = groupEvidence,
                verdict = verdict,
                internallyDisputed = support.isNotEmpty() && contradiction.isNotEmpty(),
            )
        }

        // An internally disputed group can never be cleanly eliminated (its own contradiction is not
        // evidence a competing group's table produced) and can never win either (its own support does
        // not settle the question its own contradiction raised) -- so its mere presence keeps the
        // whole adjudication conflicted, regardless of what any other group's verdict is.
        if (judged.any { it.internallyDisputed }) return AdjudicationResult.StillConflicted(groups)

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
