package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The candidate values that **another recognition run contradicted**, carried from the resolver to
 * the recovery screen.
 *
 * ## The defect this closes
 *
 * Measured on `docs/Scan Evidence 02-09 sixth/20260902-131511-970`. Three passes reported:
 *
 * ```
 * FULL_FRAME_PASS_A    [run=PASS_A]          Confident 89.0/PER_100_ML
 * FILTERED_PASS_A      [run=PASS_A]          Confident 89.0/PER_100_ML
 * SELECTED_REGION_OCR  [run=SELECTED_REGION] Confident  8.0/PER_100_ML
 * resolver.verdict: Conflicted
 * final UI action : RECOVERY
 * ```
 *
 * [EvidenceResolver] refused correctly: two genuinely independent runs disagreed and nothing can say
 * which is right. But the scanner then built the recovery screen from the winning **document**
 * alone, and [RecoveryCandidates] rebuilt its choices from that document with no knowledge that a
 * dispute had ever happened. Its first offer was `89 g / 100 ml` — the disputed value, presented
 * exactly like an ordinary proposal, one tap from the calculator.
 *
 * So the app refused a reading and then offered it anyway. The refusal was real and the recovery
 * screen simply could not see it.
 *
 * ## The rule
 *
 * > A candidate that a **distinct recognition run** read differently is not offered as an ordinary
 * > recovery proposal.
 *
 * **Both sides are suppressed**, not just the loser. `89` and `8` are the two halves of one
 * disagreement, and there is no property available to the app that says which is the printed one —
 * that is the same reasoning [EvidenceResolver] rule 2 already applies, extended to the surface that
 * was bypassing it. Offering the "other" one would be picking a winner by a different name.
 *
 * ## What still works after a dispute
 *
 * Everything except the disputed numbers. Undisputed values on the label are still offered with
 * their own bases, row tapping still works, and focused or full manual entry is still reachable —
 * so a conflicted capture degrades to *the user reads the package*, which is what the recovery
 * screen is for, rather than to a dead end.
 *
 * ## Counted over runs, never over sources
 *
 * [EvidenceSource.FULL_FRAME_PASS_A] and [EvidenceSource.FILTERED_PASS_A] are two parses of one
 * recognition over the same characters, so they cannot dispute each other any more than they can
 * corroborate each other. This is the same invariant [EvidenceSource.recognitionRun] exists to
 * enforce, applied in the opposite direction.
 */
data class DisputedCandidates(
    /** Each disputed value with the runs that read it, in the order the passes reported. */
    private val entries: List<Entry> = emptyList(),
) {
    /** One side of a disagreement: a value, its basis, and which run asserted it. */
    data class Entry(
        val value: BigDecimal,
        val basis: NutritionBasis?,
        val run: RecognitionRun,
    )

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Whether [value] under [basis] is one of the contested readings.
     *
     * `compareTo`, never `equals`: `BigDecimal.equals` compares scale, so `89` and `89.0` would read
     * as different candidates and the suppression would silently miss whichever spelling recovery
     * happened to build. This repo has hit that trap more than once.
     */
    fun disputes(value: BigDecimal, basis: NutritionBasis?): Boolean = entries.any {
        it.value.compareTo(value) == 0 && (it.basis == null || basis == null || it.basis == basis)
    }

    /** For the evidence bundle: which run contradicted which candidate. */
    fun describe(): String =
        if (entries.isEmpty()) {
            "none"
        } else {
            entries.joinToString("; ") {
                "${it.value.stripTrailingZeros().toPlainString()}/${it.basis?.name ?: "no-basis"} " +
                    "read by ${it.run.name}"
            }
        }

    companion object {
        /** No dispute — the ordinary case, and the identity for every non-conflicted capture. */
        val NONE = DisputedCandidates()

        /**
         * The disputed candidates in [evidence], or [NONE] when the runs did not disagree.
         *
         * A dispute needs two **distinct runs** claiming different things. One run seen through two
         * parses is one opinion, and a run that found nothing contradicts nothing — a `NotFound` is
         * an absence of evidence, not evidence of absence, and treating it as a dispute would
         * suppress a good reading on every label where the second pass simply failed.
         */
        fun of(evidence: List<RecognitionEvidence>): DisputedCandidates {
            val confident = evidence.filter { it.isConfident }
            if (confident.isEmpty()) return NONE
            if (confident.all { it.fullyAgreesWith(confident.first()) }) return NONE
            if (confident.map { it.source.recognitionRun }.distinct().size < 2) return NONE

            // Retain each run's claim: deduplicating values first can erase the second run.
            val claims = mutableListOf<Entry>()
            confident.forEach { candidate ->
                val value = candidate.value ?: return@forEach
                val already = claims.any {
                    it.value.compareTo(value) == 0 && it.basis == candidate.basis &&
                        it.run == candidate.source.recognitionRun
                }
                if (!already) {
                    claims += Entry(value, candidate.basis, candidate.source.recognitionRun)
                }
            }

            return DisputedCandidates(claims)
        }
    }
}
