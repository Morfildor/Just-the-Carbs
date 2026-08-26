package app.justthecarbs.ocr

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
         * Corroborated by at least two independent passes, or produced by Pass A alone exactly as
         * before. Safe to present as the scanner's answer — still subject to the user tapping it.
         */
        data class Resolved(
            val reading: LabelReading,
            val report: NutritionParseReport,
            val agreeingSources: List<EvidenceSource>,
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
            // NotFound — the pre-existing behaviour, preserved.
            val ambiguous = evidence.firstOrNull { it.reading is LabelReading.Ambiguous }
                ?: return Outcome.Nothing
            return Outcome.Resolved(
                reading = ambiguous.reading,
                report = ambiguous.report,
                agreeingSources = listOf(ambiguous.source),
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
            // RULE 2. Two passes claim different things. At least one is wrong; nothing here can tell
            // which, and picking either is precisely how a confident-wrong reaches the user.
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
            )
        }

        // RULE 4. Only a re-recognition or a live frame found this. Propose, never decide.
        val confidence = lone.valueConfidence
        if (confidence != null && confidence < MIN_PROPOSAL_CONFIDENCE) return Outcome.Nothing

        return Outcome.NeedsVerification(
            reading = lone.reading as LabelReading.Confident,
            report = lone.report,
            source = lone.source,
        )
    }
}
