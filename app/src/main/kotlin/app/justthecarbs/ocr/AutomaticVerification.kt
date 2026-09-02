package app.justthecarbs.ocr

/**
 * Whether a reading has been **independently verified**, as distinct from being parser-plausible.
 *
 * ## The distinction this type exists to make
 *
 * `LabelReading.Confident` means the parser found exactly one placeable candidate and every
 * structural rule accepted it. That is a statement about *structure*, and it was being read as a
 * statement about *digits*. It is not one, and the difference is measurable: on
 * `docs/Scan Evidence 02-09/20260902-085542-213` the printed `72,0 g` came back as `12,0.g`, which
 * is confident by every test the parser has and wrong by a factor of six.
 *
 * So there are now three separate questions, and they are answered by three separate things:
 *
 * | question                        | answered by            | vocabulary                    |
 * |---------------------------------|------------------------|-------------------------------|
 * | is it structurally plausible?   | the parser             | `Confident` / `Ambiguous` / … |
 * | is it independently verified?   | **this**               | [Route] / not verified        |
 * | what does the UI do?            | the scanner            | advance / confirm / recover   |
 *
 * A plausible-but-unverified reading is not an error and is not discarded. It becomes a
 * **one-tap confirmation**, which is what the app did before the automatic fast path existed. Only a
 * verified reading skips that tap.
 *
 * ## Why "two views of one recognition" is not a route
 *
 * [EvidenceSource.FULL_FRAME_PASS_A] and [EvidenceSource.FILTERED_PASS_A] are two parses of one ML
 * Kit run over the same characters; the second is literally a subset of the first's elements. Their
 * agreeing proves the filter kept the winning row and nothing else. This repo has already recorded
 * one confident-wrong caused by counting them as corroboration (grated cheese, `2.09`), and
 * `SKIPPED_PASS_A_STRONG` reintroduced the same mistake by a different door: it did not *claim*
 * corroboration, but it let a single run's reading advance with no second opinion of any kind.
 *
 * Verification therefore has exactly two routes, and both require evidence from outside the run
 * being checked.
 */
internal object AutomaticVerification {

    /** How a reading earned automatic advancement. */
    enum class Route {
        /**
         * The label's own other rows corroborate it — see [CrossColumnRatioCheck].
         *
         * Structural rather than optical: it does not re-read the pixels, it asks whether this row
         * behaves like the rest of the table. Free, deterministic, and available on any label
         * printing two value columns.
         */
        CROSS_COLUMN,

        /**
         * A genuinely separate recognition run read the same amount and the same basis.
         *
         * "Separate" is counted over [RecognitionRun], never over [EvidenceSource].
         */
        DISTINCT_OCR_AGREEMENT,

        /** Nothing verified it. The reading may still be offered — for the user to confirm. */
        NONE,
    }

    /**
     * The verdict, with enough detail for the evidence bundle to say *why*.
     *
     * [rejectionReason] is populated whenever [route] is [Route.NONE], so a bundle distinguishes
     * "the table contradicted it" from "the table could not answer" — which are very different
     * things to read while debugging a scan, and only the first indicates a misread.
     */
    data class Verdict(
        val route: Route,
        val supportingRows: Int = 0,
        val medianRatio: Double? = null,
        val candidateRatio: Double? = null,
        val rejectionReason: String? = null,
    ) {
        val mayAdvanceAutomatically: Boolean get() = route != Route.NONE
    }

    /**
     * Verifies [report]'s reading against [document] alone, with no second recognition available.
     *
     * This is the cheap route and the one that runs first: it costs a re-parse of rows already
     * built, so a label that can corroborate itself never pays for a second ML Kit pass.
     */
    fun verify(document: OcrDocument, report: NutritionParseReport): Verdict {
        val candidate = (report.reading as? LabelReading.Confident)?.candidate
            ?: return Verdict(Route.NONE, rejectionReason = "no confident reading to verify")

        return when (val verdict = CrossColumnRatioCheck.check(document, candidate)) {
            is CrossColumnRatioCheck.Verdict.Consistent -> Verdict(
                route = Route.CROSS_COLUMN,
                supportingRows = verdict.supportingRows,
                medianRatio = verdict.medianRatio,
                candidateRatio = verdict.candidateRatio,
            )

            is CrossColumnRatioCheck.Verdict.Conflicting -> Verdict(
                route = Route.NONE,
                supportingRows = verdict.supportingRows,
                medianRatio = verdict.medianRatio,
                candidateRatio = verdict.candidateRatio,
                rejectionReason = "the table's own columns contradict this row " +
                    "(row ${format(verdict.candidateRatio)} vs table ${format(verdict.medianRatio)})",
            )

            is CrossColumnRatioCheck.Verdict.NotEnoughEvidence -> Verdict(
                route = Route.NONE,
                supportingRows = verdict.coherentRows,
                rejectionReason = "only ${verdict.coherentRows} coherent row pairs; " +
                    "${CrossColumnRatioCheck.MIN_SUPPORTING_ROWS} needed",
            )
        }
    }

    /**
     * Verifies across every recognition that ran, given the whole evidence set.
     *
     * Tries [Route.CROSS_COLUMN] first because it is free, then falls back to asking whether two
     * **distinct runs** agreed on both amount and basis.
     *
     * The agreement test is [RecognitionEvidence.fullyAgreesWith], which compares values with
     * `compareTo` — so `72`, `72.0` and `72,0` (which reaches here already parsed) count as the same
     * amount, while a basis difference is a genuine conflict rather than a rounding artefact.
     */
    fun verify(evidence: List<RecognitionEvidence>): Verdict {
        val confident = evidence.filter { it.isConfident }
        val primary = confident.firstOrNull()
            ?: return Verdict(Route.NONE, rejectionReason = "no confident reading to verify")

        // Every confident pass must be asserting the same thing before any of them is verified.
        //
        // Without this the verdict is about `confident.first()` — an artefact of the order the
        // evidence list happens to be built in — while the scanner promotes whatever
        // [EvidenceResolver] resolved. Those are the same reading today only because a disagreement
        // makes the resolver return `Conflicted`, which cannot advance. That is a guarantee held in
        // a different file, and the brief's invariant is that verification belongs to the exact
        // candidate being promoted, so it is asserted here rather than inherited.
        if (confident.any { !it.fullyAgreesWith(primary) }) {
            return Verdict(
                route = Route.NONE,
                rejectionReason = "confident passes disagree; there is no single candidate to verify",
            )
        }

        // Ask the structural route against every confident pass's own document, richest first.
        //
        // They all assert the same amount and basis (checked above), so this chooses which *table*
        // answers the question, never which value is verified. Richest first because a document with
        // more elements has more rows to supply supporting pairs — and on `103936` the pass that can
        // answer is not the one that happens to be first in the list.
        //
        // A contradiction from any of them is final and returns immediately: a table refuting the
        // reading is not overruled by another view of the same table failing to reach three pairs.
        var structuralGap: Verdict? = null
        confident
            .sortedByDescending { it.document?.elements?.size ?: 0 }
            .forEach { candidate ->
                val document = candidate.document ?: return@forEach
                val structural = verify(document, candidate.report)
                if (structural.mayAdvanceAutomatically) return structural
                // A *contradiction* is final: a second recognition agreeing with a reading the label
                // itself refutes does not rescue it, it means both runs made the same mistake. Only
                // the "could not answer" case falls through to the optical route.
                if (structural.candidateRatio != null) return structural
                if (structuralGap == null) structuralGap = structural
            }

        val distinctRuns = confident.map { it.source.recognitionRun }.distinct()
        return if (distinctRuns.size >= 2) {
            Verdict(Route.DISTINCT_OCR_AGREEMENT)
        } else {
            Verdict(
                route = Route.NONE,
                rejectionReason = if (distinctRuns.size < 2) {
                    "only one recognition run (${distinctRuns.joinToString()}); " +
                        "two parses of one run cannot corroborate each other"
                } else {
                    "recognition runs disagree on amount or basis"
                },
            )
        }
    }

    private fun format(ratio: Double): String = String.format("%.3f", ratio)
}
