package app.justthecarbs.ocr

import java.math.BigDecimal

/**
 * Combines several recognition passes into one outcome, favouring agreement and refusing conflict.
 *
 * ## The problem this solves
 *
 * With one recognition there was nothing to resolve. This pass adds a fresh recognition of the
 * user-selected region and retains stable pre-shutter live frames, so the app can now hold several
 * answers to the same question. The measurements taken while designing this contain a real case:
 *
 * ```
 * full frame  -> Confident 2.09      (known recognition-originated error)
 * 5% crop     -> Confident 2         (the value actually printed on the package)
 * overlay crop-> Confident 2.04      (a third, also wrong)
 * ```
 *
 * There is no honest way to pick from that set. `2` is correct, but it is only knowable as correct by
 * reading the package — not by any property available to the app. **So the resolver refuses.**
 *
 * ## The rules, in order
 *
 * 1. **Agreement is the only route to a confident answer.** Two independent passes reporting the same
 *    value *and* the same basis is strong evidence, because the passes see different pixels and
 *    tokenise independently.
 * 2. **Disagreement between confident passes is always a refusal**, never a vote. Two passes claiming
 *    different values means at least one is wrong and the app cannot tell which.
 * 3. **A lone confident pass keeps exactly the standing it had before this pass existed.** Pass A
 *    alone still answers, because that is the shipped behaviour and it is not made worse by the
 *    presence of a second opinion that found nothing.
 * 4. **A value only the crop found is never automatically confident.** It is offered for explicit
 *    verification instead. This is the §8 "selected-region-only" case: re-recognition demonstrably
 *    recovers correct values *and* demonstrably invents wrong ones, so it may propose but not decide.
 *
 * ## What this deliberately does not do
 *
 * It does not score, weight or rank. It has no notion of a "better" source, and it never uses
 * numeric plausibility to prefer one value over another — `2` does not beat `2.09` for looking
 * rounder. Confidence metadata can only *withhold* a promotion, never create one.
 */
object EvidenceResolver {

    /**
     * How sure the recognizer must be about a value that **only one** pass found before it is worth
     * putting in front of the user for verification.
     *
     * Calibrated against measured data rather than chosen: on the real corpus, clean numeric tokens
     * score 0.75–0.93 while the mangled nutrient token that caused a known misread scores 0.45. Set
     * below the clean band so a legitimate recovery is not withheld, and above the mangled sample so
     * obvious garbage is not proposed. A value below this is dropped silently — proposing it would
     * spend the user's attention on something the recognizer itself doubts.
     */
    const val MIN_PROPOSAL_CONFIDENCE = 0.60f

    /** What the scanner should do, once every available pass has reported. */
    sealed interface Outcome {

        /**
         * The pass whose reading this outcome carries, when it carries one.
         *
         * ## Why the outcome has to say this
         *
         * The reading and the report were carried; **the recognition they came out of was not**. The
         * scanner therefore evaluated and drew every outcome against `captured.document` — Pass A's
         * whole-frame recognition — whichever pass had actually won.
         *
         * That is wrong in two ways whenever Strategy B wins, and both are measurable rather than
         * theoretical:
         *
         * * **The parser question is asked of the wrong document.** [ScaleAmbiguity] looks for the
         *   candidate's row and its sibling cells. Strategy B's candidate geometry is in *crop-local*
         *   coordinates, so it matches no row in Pass A's full-frame document and the scale question
         *   silently degrades to "no row to pair against".
         * * **The geometry is drawn in the wrong space.** [app.justthecarbs.ui.scan.VerificationScreen]
         *   scales `candidate.geometry` against the full capture bitmap, so a crop-local box lands
         *   short by the crop's own origin — the highlight and the close-up point at the wrong part of
         *   the photograph, which is precisely the evidence the user is being asked to check against.
         *
         * Null for [Conflicted] and [Nothing], which carry no reading to attribute.
         */
        val winningEvidence: RecognitionEvidence? get() = null

        /**
         * Corroborated by at least two independent passes, or produced by Pass A alone exactly as
         * before. Safe to present as the scanner's answer — still subject to the user tapping it.
         */
        data class Resolved(
            val reading: LabelReading,
            val report: NutritionParseReport,
            val agreeingSources: List<EvidenceSource>,
            /** The pass [report] came from. See [Outcome.winningEvidence]. */
            override val winningEvidence: RecognitionEvidence? = null,
        ) : Outcome

        /**
         * A single pass found something credible that nothing else corroborates.
         *
         * Presented as an explicit "check this against the label" proposal, never as a settled
         * result. This is what turns the measured witte-kaas recovery (`NotFound` -> `2.3`) into
         * something the user can use, without pretending an uncorroborated re-recognition is
         * authoritative.
         */
        data class NeedsVerification(
            val reading: LabelReading.Confident,
            val report: NutritionParseReport,
            val source: EvidenceSource,
            /** The pass [report] came from. See [Outcome.winningEvidence]. */
            override val winningEvidence: RecognitionEvidence? = null,
        ) : Outcome

        /**
         * Passes disagreed about the value or the basis. The app must not choose.
         *
         * [values] is for diagnostics and for telling the user plainly that the readings conflicted,
         * which is more actionable than a bare "not found".
         */
        data class Conflicted(
            val values: List<String>,
            val sources: List<EvidenceSource>,
        ) : Outcome

        /**
         * Several passes, or one pass, produced competing readings that nothing resolves.
         *
         * Distinct from [Conflicted], which means two *confident* passes disagreed. This is the
         * weaker and commoner case: no pass reached a confident value at all, and the richest thing
         * available is an ambiguity — typically one recognition offering two candidates.
         *
         * ### Why this is not `Resolved`
         *
         * It used to be. `resolve` wrapped an ambiguous reading in [Resolved] "so an Ambiguous set
         * from Pass A still reaches the user rather than being flattened to NotFound", which is a
         * real requirement — but naming that state *resolved* is a lie the diagnostics then repeat.
         * Measured on `docs/Scan Evidence 01-09-26/20260901-211417-935`, the evidence bundle records
         *
         * ```
         * reading         : Ambiguous (Strategy A re-parse)
         * strategy B      : RAN_NO_READING
         * resolver.verdict: Resolved   <- what AutomaticScanAdvance reads
         * ```
         *
         * on the scan that went on to put a 2.6x-wrong value in front of the user. Nothing was
         * resolved: one pass could not decide and the other returned nothing.
         *
         * [app.justthecarbs.ocr.AutomaticScanAdvance] already refused to advance on it, because it
         * additionally requires a `Confident` reading — so this renaming fixes an honesty defect
         * rather than a live auto-advance hole. It matters because a future caller reading
         * `Resolved` and trusting the name would reopen exactly that hole, and because the bundles
         * a person reads while debugging a bad scan said the opposite of what happened.
         */
        data class Unresolved(
            val reading: LabelReading,
            val report: NutritionParseReport,
            val source: EvidenceSource,
            /** The pass [report] came from. See [Outcome.winningEvidence]. */
            override val winningEvidence: RecognitionEvidence? = null,
        ) : Outcome

        /** Nothing usable from any pass. */
        data object Nothing : Outcome
    }

    /**
     * Resolves [evidence] into one outcome.
     *
     * Order of the list is irrelevant to the result; the rules are symmetric.
     */
    fun resolve(evidence: List<RecognitionEvidence>): Outcome {
        val confident = evidence.filter { it.isConfident }

        if (confident.isEmpty()) {
            // No pass produced an accepted value. Keep the richest non-confident report so an
            // Ambiguous set from Pass A still reaches the user rather than being flattened to
            // NotFound — the pre-existing behaviour, preserved — but report it as UNRESOLVED.
            //
            // Repeating the same ambiguity across several passes does not resolve it: two passes
            // that both say "it is either 0.5 or 1.3" have not narrowed anything, and counting them
            // as agreement would be the count-one-opinion-twice error [EvidenceSource.recognitionRun]
            // exists to prevent, in its most dangerous form — the app would present one of two
            // competing numbers as settled.
            val ambiguous = evidence.firstOrNull { it.reading is LabelReading.Ambiguous }
                ?: return Outcome.Nothing
            return Outcome.Unresolved(
                reading = ambiguous.reading,
                report = ambiguous.report,
                source = ambiguous.source,
                winningEvidence = ambiguous,
            )
        }

        // Group by the value AND basis actually claimed. compareTo, never equals: BigDecimal equality
        // is scale-sensitive and would split 53.5 from 53.50 into a false conflict.
        val groups = mutableListOf<MutableList<RecognitionEvidence>>()
        confident.forEach { candidate ->
            val existing = groups.firstOrNull { group -> group.first().fullyAgreesWith(candidate) }
            if (existing != null) existing += candidate else groups += mutableListOf(candidate)
        }

        if (groups.size > 1) {
            // RULE 2, with an exception carved out by [ConflictAdjudication]. Two passes claiming
            // different things is normally a refusal outright — nothing here can tell which is
            // wrong, and picking either is precisely how a confident-wrong reaches the user.
            //
            // But "nothing can tell which" is not always true. Each group's OWN nutrition table can
            // structurally contradict it — see [CrossColumnRatioCheck] — and when one group's table
            // explicitly contradicts it while a competing group's table explicitly supports it, that
            // is evidence available to the app, not a guess about plausibility, confidence, magnitude
            // or source order. [ConflictAdjudication] is the only place that evidence is consulted;
            // everything else about RULE 2 is unchanged, including refusing outright when neither
            // group's table can be checked at all, or when a supported group merely faces an
            // uncheckable one rather than a contradicted one.
            val byValueAndBasis = groups.associate { group ->
                (group.first().value ?: BigDecimal.ZERO) to group.first().basis to group
            }
            val adjudication = ConflictAdjudication.adjudicate(byValueAndBasis)
            if (adjudication is ConflictAdjudication.AdjudicationResult.SingleSupported) {
                val surviving = adjudication.evidence

                // Same corroboration test RULE 1 applies to an ordinary agreeing group: two DISTINCT
                // recognition runs is real independent evidence and resolves confidently. A group
                // that survived adjudication by table structure alone, with no second run agreeing,
                // has not been corroborated in that sense — it was defended, not confirmed by a
                // second opinion — so it is conservatively proposed for verification rather than
                // resolved outright. This mirrors RULE 4's stance on a lone re-recognition: being
                // right about a decimal scale contradiction is not the same claim as two independent
                // passes reading the same characters.
                if (surviving.map { it.source.recognitionRun }.distinct().size >= 2) {
                    val best = surviving.maxByOrNull { it.document?.elements?.size ?: 0 } ?: surviving.first()
                    return Outcome.Resolved(
                        reading = best.reading,
                        report = best.report,
                        agreeingSources = surviving.map { it.source },
                        winningEvidence = best,
                    )
                }

                val lone = surviving.maxByOrNull { it.document?.elements?.size ?: 0 } ?: surviving.first()
                return Outcome.NeedsVerification(
                    reading = lone.reading as LabelReading.Confident,
                    report = lone.report,
                    source = lone.source,
                    winningEvidence = lone,
                )
            }

            return Outcome.Conflicted(
                values = groups.map { group ->
                    val v = group.first().value?.stripTrailingZeros()?.toPlainString() ?: "?"
                    val b = group.first().basis?.name ?: "no-basis"
                    "$v/$b"
                },
                sources = confident.map { it.source },
            )
        }

        val agreed = groups.single()

        // RULE 1. Independent corroboration — counted over recognition RUNS, not over sources.
        //
        // The whole-frame parse and the filtered parse come from ONE recognition, so their agreeing
        // is not two opinions; the filter simply kept the row the parser had already chosen. Counting
        // them as corroboration is how a single wrong reading gets promoted to "confirmed", which was
        // measured happening on grated cheese (`2.09`, agreed by both Pass A views).
        if (agreed.map { it.source.recognitionRun }.distinct().size >= 2) {
            // Report the one whose document is richest, so downstream consumers (serving candidate,
            // provenance, diagnostics) get the fullest picture. This chooses a *report to carry*,
            // not a value — every member of this group asserts the identical value and basis.
            val best = agreed.maxByOrNull { it.document?.elements?.size ?: 0 } ?: agreed.first()
            return Outcome.Resolved(
                reading = best.reading,
                report = best.report,
                agreeingSources = agreed.map { it.source },
                winningEvidence = best,
            )
        }

        // One recognition run, possibly seen through more than one parse (whole frame and filtered).
        // Carry the richest report; it is the same claimed value either way.
        val lone = agreed.maxByOrNull { it.document?.elements?.size ?: 0 } ?: agreed.first()

        // RULE 3. Pass A alone answers, exactly as it did before this pass existed. Adding evidence
        // sources must not make the app worse at labels it already read.
        //
        // Note `agreeingSources` reports every source in the group but they share one recognition
        // run, so a caller counting sources must not read this as corroboration — that is exactly
        // why the check above counts runs.
        if (lone.source.recognitionRun == RecognitionRun.PASS_A) {
            return Outcome.Resolved(
                reading = lone.reading,
                report = lone.report,
                agreeingSources = agreed.map { it.source },
                winningEvidence = lone,
            )
        }

        // RULE 4. Only a re-recognition or a live frame found this. Propose, never decide.
        val confidence = lone.valueConfidence
        if (confidence != null && confidence < MIN_PROPOSAL_CONFIDENCE) return Outcome.Nothing

        // RULE 5. Unless the label itself corroborates it.
        //
        // ## The contradiction this removes
        //
        // Rule 4 exists because a re-recognition "may propose but not decide" — it demonstrably
        // recovers correct values and demonstrably invents wrong ones, and *nothing else had seen
        // it*. That reasoning is about the absence of corroboration, not about which pass produced
        // the reading. When corroboration exists, the premise is gone.
        //
        // Measured on `docs/Scan Evidence 02-09 2nd test/20260902-103936-423`. ML Kit read the
        // printed `per 100 g` header as `1009`, so Pass A resolved no per-100 column and returned
        // NotFound. `SELECTED_REGION_OCR` read `Confident 72.0/PER_100_G` — the printed value — and
        // [CrossColumnRatioCheck] supported it with **five** coherent rows (median 0.309, candidate
        // 0.313). The bundle records all of that and then `final UI action : RECOVERY`, whose first
        // offer was `72 g / serving`. The app held a verified correct reading and showed the user a
        // fabricated one.
        //
        // ## Why this does not weaken rule 4
        //
        // The corroboration required here is [CrossColumnRatioCheck]: the *other nutrient rows of
        // the same table*, whose serving-to-per-100 ratio is a property of the serving size and is
        // therefore the same on every row. A re-recognition that misread a digit cannot also have
        // misread four other rows consistently in the same direction — that is exactly what the
        // check measures, and it is what refuses the misread `12` (ratio 1.875 against a table
        // median of 0.309) while accepting `72` (0.3125).
        //
        // So the rule is: **a lone re-recognition still may not decide on its own authority; it may
        // decide when the label agrees with it.** A reading the table cannot speak to
        // (`NotEnoughEvidence` — any single-value-column label) keeps rule 4 unchanged and is still
        // proposed for the user to confirm, and a reading the table *contradicts* is not rescued by
        // this at all.
        val document = lone.document
        if (document != null &&
            AutomaticVerification.verify(document, lone.report).route == AutomaticVerification.Route.CROSS_COLUMN
        ) {
            return Outcome.Resolved(
                reading = lone.reading,
                report = lone.report,
                agreeingSources = agreed.map { it.source },
                winningEvidence = lone,
            )
        }

        return Outcome.NeedsVerification(
            reading = lone.reading as LabelReading.Confident,
            report = lone.report,
            source = lone.source,
            winningEvidence = lone,
        )
    }
}
