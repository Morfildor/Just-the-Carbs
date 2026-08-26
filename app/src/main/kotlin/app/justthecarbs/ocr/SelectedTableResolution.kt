package app.justthecarbs.ocr

import android.graphics.Bitmap

/**
 * Runs every available recognition strategy for a confirmed rectangle and resolves them (§2, §4, §8).
 *
 * ## What changed and why
 *
 * Previously "Read table" ran Strategy A alone and its report *became* the answer, replacing the
 * whole-frame reading. That is safe only while there is exactly one recognition. Now there are up to
 * four opinions — whole frame, filtered whole frame, a fresh native-resolution recognition of the
 * selection, and stable pre-shutter live frames — and the one thing that must never happen is one of
 * them silently overwriting another.
 *
 * So this gathers them all as [RecognitionEvidence] and hands the decision to [EvidenceResolver],
 * which can corroborate, propose, or refuse, but never vote.
 *
 * ## Staged execution (§28)
 *
 * Strategy A is a re-parse of elements already in memory: microseconds, no recognition. Strategy B is
 * a real ML Kit pass costing on the order of a second. So A runs first and B is skipped entirely when
 * A already produced a reading corroborated by the whole frame — there is nothing for a second
 * opinion to add when two existing opinions already agree, and the user should not wait for it.
 */
internal object SelectedTableResolution {

    data class Result(
        val outcome: EvidenceResolver.Outcome,
        /** Every pass that ran, for diagnostics and the evidence bundle. */
        val evidence: List<RecognitionEvidence>,
        /** Strategy A's own result, retained for the ineffective-selection hint (§11). */
        val filtered: SelectedTableReader.Result,
        val elapsedMs: Long,
    ) {
        /** True when the rectangle removed essentially nothing (§11). */
        val selectionWasIneffective: Boolean get() = filtered.isIneffective
    }

    /**
     * Resolves [region] against a completed capture.
     *
     * [bitmap] is the retained upright full-resolution capture; passing null simply disables
     * Strategy B, which degrades this to the previous behaviour rather than failing.
     */
    fun resolve(
        passA: PassAResult,
        region: NormalizedRegion,
        bitmap: Bitmap?,
        liveEvidence: RecognitionEvidence? = null,
        recogniseRegion: (Bitmap?, NormalizedRegion) -> RecognitionEvidence? = { bmp, rgn ->
            SelectedRegionRecognizer.recognise(bmp, rgn)
        },
    ): Result {
        val started = System.nanoTime()
        val evidence = mutableListOf<RecognitionEvidence>()

        // The whole-frame reading, always available and always the baseline.
        val wholeFrame = RecognitionEvidence(
            source = EvidenceSource.FULL_FRAME_PASS_A,
            report = passA.report,
            document = passA.document,
        )
        evidence += wholeFrame

        // Strategy A: filter Pass A's elements. Cheap, and structurally incapable of inventing a
        // character that Pass A did not read.
        val filtered = SelectedTableReader.read(passA.document, passA.report, region)
        if (filtered.outcome == SelectedTableReader.Outcome.FILTERED) {
            evidence += RecognitionEvidence(
                source = EvidenceSource.FILTERED_PASS_A,
                report = filtered.report,
                document = passA.document,
            )
        }

        liveEvidence?.let { evidence += it }

        // Strategy B is skipped only when INDEPENDENT runs already agree (§28 staged execution).
        //
        // "Independent" is the load-bearing word, and getting it wrong was measured: an earlier
        // version skipped whenever any two confident passes agreed, which included the whole-frame
        // and filtered views of the SAME recognition. On grated cheese those two agreed on the
        // known-wrong `2.09`, so the independent recognition that would have contradicted them was
        // never run, and a wrong value resolved confidently. Two parses of one recognition are one
        // opinion; only a separate run can corroborate or contradict it.
        val independentRunsAgree = evidence
            .filter { it.isConfident }
            .let { confident ->
                confident.map { it.source.recognitionRun }.distinct().size >= 2 &&
                    confident.all { it.fullyAgreesWith(confident.first()) }
            }

        if (!independentRunsAgree) {
            recogniseRegion(bitmap, region)?.let { evidence += it }
        }

        return Result(
            outcome = EvidenceResolver.resolve(evidence),
            evidence = evidence,
            filtered = filtered,
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
        )
    }
}
