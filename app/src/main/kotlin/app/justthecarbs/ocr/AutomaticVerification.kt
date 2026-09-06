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
         * A **separate photograph** read the same amount and the same basis.
         *
         * "Separate" is counted over [PhysicalObservationId], never over [EvidenceSource] and — since
         * 2026-09-04 — no longer over [RecognitionRun] either. A crop, rotation, upscale or contrast
         * variant of one capture is the same observation and cannot reach this route, because it
         * inherits the optical defect that corrupted the glyph in the first place.
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
        /**
         * Whether two or more recognition views agreed, **including views of one photograph**.
         *
         * Proposal-grade evidence, not advancement-grade — see [agreesAcrossViews]. Carried on the
         * verdict rather than passed separately so the callers of
         * [AutomaticScanAdvance.eligibility] keep their signatures and cannot accidentally supply the
         * advancement answer to the proposal question, which is the confusion that let `0.59`
         * advance in the first place.
         */
        val viewsAgree: Boolean = false,
    ) {
        val mayAdvanceAutomatically: Boolean get() = route != Route.NONE

        /**
         * Evidence sufficient to **offer** the figure for confirmation.
         *
         * Deliberately weaker than [mayAdvanceAutomatically]: an independent observation or the
         * label's own structure removes the tap, while agreement between two views of one frame only
         * earns the right to show the number behind one.
         */
        val mayBeProposed: Boolean get() = mayAdvanceAutomatically || viewsAgree
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
        val viewsAgree = agreesAcrossViews(evidence)
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
                viewsAgree = false,
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
        var structuralSupport: Verdict? = null
        for (candidate in confident.sortedByDescending { it.document?.elements?.size ?: 0 }) {
            val document = candidate.document ?: continue
            val structural = verify(document, candidate.report)
            if (structural.mayAdvanceAutomatically) {
                if (structuralSupport == null) structuralSupport = structural
                continue
            }
            // A *contradiction* is final: a second recognition agreeing with a reading the label
            // itself refutes does not rescue it, it means both runs made the same mistake. Only
            // the "could not answer" case falls through to the optical route.
            //
            // `viewsAgree` is deliberately NOT carried here: when the table refutes the row, the
            // fact that two views read it the same way is what a repeated misread looks like, so
            // it must not become a reason to propose the figure anyway.
            if (structural.candidateRatio != null) return structural
            if (structuralGap == null) structuralGap = structural
        }
        // Every document must get a chance to veto; the richest document is not necessarily right.
        structuralSupport?.let { return it.copy(viewsAgree = viewsAgree) }

        // Independence is a property of the photograph, not of the recognizer invocation.
        //
        // This counted `recognitionRun` until 2026-09-04, and `20260904-113653-044` is what that
        // cost: Pass A and the selected-region pass are two runs, they are also two views of one
        // JPEG, and both read the Fanta's printed `0,5 g` as `0.59` because the `g` glyph was
        // corrupted in the pixels they share. Two correlated observations agreeing is one observation
        // counted twice, and the app advanced with no confirmation on a tenfold error.
        //
        // See [PhysicalObservationId]. Everything derived from one capture — crop, rotation, upscale,
        // contrast — shares its id, so only a genuinely separate photograph reaches this route.
        val distinctObservations = confident.map { it.physicalObservation }
            .filter { it != PhysicalObservationId.UNKNOWN }.distinct()
        if (distinctObservations.size >= 2) {
            return Verdict(Route.DISTINCT_OCR_AGREEMENT, viewsAgree = viewsAgree)
        }

        // Both reasons, not just the second one.
        //
        // `structuralGap` holds why the *structural* route could not answer — "only 1 coherent row
        // pair; 3 needed" — and it was computed and then discarded, so every bundle in the twelfth
        // session recorded the optical route's reason alone: *"only one recognition run (PASS_A)"*.
        // That is true and it is not the whole answer, and on this evidence it is the misleading
        // half: it reads as "the app never looked at the table", when the app did look and found the
        // label could not corroborate itself. Which of the two is missing decides whether a capture
        // is fixed by a better photograph or by a label that prints a second column at all.
        val structural = structuralGap?.rejectionReason
        val runs = confident.map { it.source.recognitionRun }.distinct()
        return Verdict(
            route = Route.NONE,
            supportingRows = structuralGap?.supportingRows ?: 0,
            viewsAgree = viewsAgree,
            rejectionReason = buildString {
                // Say which of the two reasons applies, because they call for different actions.
                //
                // "One run" means the app looked once and a second look might help. "One physical
                // observation" means it looked twice at the same photograph, which cannot settle an
                // optical corruption however many times it is repeated — that one needs a second
                // photograph, and a bundle saying "only one recognition run" would be plainly false
                // on a capture where two runs demonstrably happened.
                if (runs.size >= 2) {
                    append(
                        "${runs.size} recognition runs establish fewer than two known physical observations " +
                            "(${distinctObservations.joinToString { it.value }.ifEmpty { "UNKNOWN" }}); views of the same photograph " +
                            "share its optical defects and cannot corroborate each other",
                    )
                } else {
                    append(
                        "only one recognition run (${runs.joinToString()}); " +
                            "two parses of one run cannot corroborate each other",
                    )
                }
                structural?.let { append(" — and the table could not corroborate it either: $it") }
            },
        )
    }

    /**
     * Whether two or more recognition **views** read the same amount and basis, same frame or not.
     *
     * ## A deliberately weaker question than [verify]
     *
     * This asks *may this figure be shown for confirmation*, where [verify] asks *may this figure skip
     * the confirmation tap*. Same-frame views can answer the first and not the second, and collapsing
     * the two is what made the `0.59` advance possible.
     *
     * Two views of one photograph share its pixels, so they cannot settle an **optical** corruption or
     * the **absolute decimal scale** — both inherit whatever the glyph actually looked like. They can
     * still disagree about tokenisation, row association and column ownership, which is the far more
     * common way a single digit goes wrong, so their agreement is real evidence against *that*.
     *
     * ## Why this is not a loophole back to the defect
     *
     * Nothing here reaches [Route.DISTINCT_OCR_AGREEMENT]; the only consumer is
     * [ReadingEligibility]'s `corroborated` parameter, which governs whether a value is **offered**.
     * Every scale rule still applies on top: [ReadingEligibility] refuses a demonstrated
     * [ScaleAmbiguity.Verdict.Ambiguous] *before* consulting corroboration at all, precisely because
     * agreement is scale-invariant.
     *
     * Measured: without this, `20260904-113818-873` (`57 g per 100 gram`) and `20260904-114311-968`
     * (`koolhydraten 35 g`) fall from `CONFIRM_ON_CAPTURE` to `RECOVERY` — the app holds the correct
     * value and shows the user nothing, which is a regression in exactly the direction this app has
     * repeatedly had to fix.
     */
    fun agreesAcrossViews(evidence: List<RecognitionEvidence>): Boolean {
        val confident = evidence.filter { it.isConfident }
        val primary = confident.firstOrNull() ?: return false

        // Two *parses of one recognition* are not two views, and this predates the physical-observation
        // work rather than being softened by it: `FILTERED_PASS_A` is literally a subset of
        // `FULL_FRAME_PASS_A`'s elements, carrying the same characters, so their agreeing proves only
        // that the filter kept the winning row. Grated cheese resolved to the known-wrong `2.09`
        // exactly this way.
        //
        // Without this bound the red label's `12` — confident, scale-unsupported, and read by the two
        // Pass A views alone — would become proposable, which is the eighth session's release blocker
        // reopening through the proposal door.
        if (confident.map { it.source.recognitionRun }.distinct().size < 2) return false

        return confident.size >= 2 && confident.all { it.fullyAgreesWith(primary) }
    }

    private fun format(ratio: Double): String = String.format("%.3f", ratio)
}
