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
        /**
         * What happened to Strategy B, for the evidence bundle.
         *
         * Recorded because the outcome was previously unrecoverable: `recogniseRegion(...)?.let`
         * discards a null silently, and `SelectedRegionRecognizer.recognise` returns null for a
         * recycled bitmap, a degenerate or whole-frame crop, or any caught exception. A device
         * bundle therefore could not answer "did a second recognition run", while separately
         * printing a hardcoded line claiming it had not — so a wrong answer looked like a measured
         * one.
         */
        val strategyB: StrategyBStatus,
    ) {
        /** True when the rectangle removed essentially nothing (§11). */
        val selectionWasIneffective: Boolean get() = filtered.isIneffective
    }

    /** Whether the independent second recognition ran, and what it produced. */
    enum class StrategyBStatus {
        /** Skipped: two independent runs already agreed, so a second opinion adds nothing (§28). */
        SKIPPED_RUNS_ALREADY_AGREE,

        /**
         * Skipped: the label's own other rows corroborate the reading — see [CrossColumnRatioCheck].
         *
         * Distinct from [SKIPPED_RUNS_ALREADY_AGREE], and the distinction matters when reading an
         * evidence bundle: that one means *two separate recognitions corroborated each other*, this
         * one means *the table's other nutrient rows agree with this one*. Both are corroboration by
         * something the run being checked could not control; they differ in what supplied it, and
         * the log says which.
         *
         * **Renamed from `SKIPPED_PASS_A_STRONG`, and the rename records a real change.** That value
         * meant "one recognition parsed cleanly", which is not corroboration at all — it was true of
         * the misread `12` on `085542-213`, and skipping on it removed the last chance to catch a
         * confident-wrong. A bundle printing the old name means a build that could skip unverified.
         */
        SKIPPED_CROSS_COLUMN_VERIFIED,

        /**
         * Attempted and returned nothing — a recycled bitmap, a degenerate or whole-frame crop, or a
         * caught exception. Distinct from [RAN_NO_READING]: nothing was recognised at all.
         */
        ATTEMPTED_RETURNED_NULL,

        /** Ran a real recognition, which produced no confident reading. */
        RAN_NO_READING,

        /** Ran a real recognition and produced a confident reading, for the resolver to weigh. */
        RAN_CONFIDENT,
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
                // **The filtered document, not Pass A's.** This used to pass `passA.document` — the
                // filtered *parse* beside the unfiltered *document* — so every stage reading
                // `evidence.document` was handed elements the user's rectangle had excluded. A row
                // the user cropped away is not evidence about the row they cropped to.
                document = filtered.document ?: passA.document,
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

        // Strategy B is also skipped when the label has **structurally verified itself** — its own
        // other rows corroborate the reading, so a second look at the same pixels adds nothing that
        // is not already known (§7 strong-path latency).
        //
        // ### This replaces `SKIPPED_PASS_A_STRONG`, which was an unverified bypass
        //
        // The previous condition asked whether the two views of Pass A were confident, agreed, and
        // stated a basis. Every part of that was true of `085542-213` — and its reading was `12`
        // where the package prints `72`. The condition described a *clean parse*, and a clean parse
        // of a misread character is exactly as clean as a clean parse of a correct one. It skipped
        // the only remaining opportunity to disagree with the misread, and the app then advanced.
        //
        // So the skip is now conditional on evidence from outside the run being skipped for.
        // [CrossColumnRatioCheck] is such evidence: it compares the reading against the *other rows
        // of the same table*, which the same OCR error cannot have produced consistently. On the
        // misread cracker it reports 1.875 against a table ratio of 0.309 and the skip does not
        // happen; on all three correct captures it reports 0.3125 against 0.309 and it does.
        //
        // A label that cannot verify itself — a single-value-column drink, say — now runs Strategy
        // B, which is the honest cost of not having a second opinion for free.
        val passAViews = evidence.filter { it.source.recognitionRun == wholeFrame.source.recognitionRun }
        val passAIsStrong = passAViews.size >= 2 &&
            passAViews.all { it.isConfident } &&
            passAViews.all { it.fullyAgreesWith(passAViews.first()) } &&
            passAViews.first().statesABasis &&
            evidence.filter { it.isConfident }.all { it.fullyAgreesWith(passAViews.first()) } &&
            AutomaticVerification.verify(evidence).route == AutomaticVerification.Route.CROSS_COLUMN

        // The status is captured rather than inferred. A null here is a real, distinct event — see
        // StrategyBStatus.ATTEMPTED_RETURNED_NULL — and losing it is what made a device bundle
        // unable to say whether a second recognition had run at all.
        val strategyB = if (independentRunsAgree) {
            StrategyBStatus.SKIPPED_RUNS_ALREADY_AGREE
        } else if (passAIsStrong) {
            StrategyBStatus.SKIPPED_CROSS_COLUMN_VERIFIED
        } else {
            val second = recogniseRegion(bitmap, region)
            if (second == null) {
                StrategyBStatus.ATTEMPTED_RETURNED_NULL
            } else {
                evidence += second
                if (second.isConfident) StrategyBStatus.RAN_CONFIDENT else StrategyBStatus.RAN_NO_READING
            }
        }

        return Result(
            outcome = EvidenceResolver.resolve(evidence),
            evidence = evidence,
            filtered = filtered,
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
            strategyB = strategyB,
        )
    }
}
