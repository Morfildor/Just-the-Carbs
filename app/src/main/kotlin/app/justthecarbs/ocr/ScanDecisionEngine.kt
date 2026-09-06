package app.justthecarbs.ocr

/**
 * Wraps the existing, already-correct decision chain
 * ([EvidenceResolver] -> [AutomaticVerification] -> [ScanPresentationDecision]) into one
 * exhaustive [ScanDecision]. Adds no new policy: every threshold and rule this delegates to is
 * unchanged. What it adds is a type-level guarantee that only a bundle [ScanPresentationDecision]
 * itself resolved to [ScanPresentationDecision.Action.AUTO_ADVANCE] can ever produce a
 * [ScanDecision.AutoAccept] carrying a [VerifiedReading] -- constructible only inside this file.
 */
object ScanDecisionEngine {

    fun decide(evidence: List<RecognitionEvidence>, automatic: Boolean): ScanDecision {
        val outcome = EvidenceResolver.resolve(evidence)
        // The winning pass's own document, and no other. A live-only winner has no retained
        // document, and an unrelated evidence item's document is a different recognition's
        // coordinate space -- substituting it would let ScaleAmbiguity/FocusedAmountEntry reason
        // about a candidate's geometry against a document that never produced it. Falling through
        // to null degrades safely: every downstream consumer null-checks and refuses (Crop), it
        // never invents a scale or column association from borrowed coordinates.
        val document = outcome.winningEvidence?.document
        val verification = AutomaticVerification.verify(evidence)

        // A conflict is terminal and must never be dressed up as a proposal or confirmation.
        if (outcome is EvidenceResolver.Outcome.Conflicted) {
            return ScanDecision.Conflict(outcome.values)
        }

        val action = ScanPresentationDecision.decide(outcome, verification, document, automatic)

        return when (action) {
            ScanPresentationDecision.Action.AUTO_ADVANCE -> {
                val confident = AutomaticScanAdvance.confidentReading(outcome)
                val basis = confident?.candidate?.basis
                if (confident == null || basis == null) {
                    // Defensive: ScanPresentationDecision only returns AUTO_ADVANCE when
                    // AutomaticScanAdvance.mayAdvanceVerified held, which itself requires a
                    // non-null basis. Reaching here would mean the two disagree -- refuse rather
                    // than silently downgrade to a confirmation on a value we cannot describe.
                    ScanDecision.Conflict(values = listOfNotNull(confident?.candidate?.value?.toPlainString()))
                } else {
                    ScanDecision.AutoAccept(
                        VerifiedReading.of(confident.candidate.value, basis, confident.candidate.sourceLine),
                    )
                }
            }
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE, ScanPresentationDecision.Action.CONFIRM -> {
                val confident = AutomaticScanAdvance.confidentReading(outcome)
                val basis = confident?.candidate?.basis
                if (confident == null || basis == null) {
                    ScanDecision.Crop
                } else {
                    ScanDecision.Confirm(confident.candidate.value, basis, confident.candidate.sourceLine)
                }
            }
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED -> {
                val candidate = ScanPresentationDecision.confirmationCandidateFor(
                    outcome,
                    document,
                    DisputedCandidates.of(evidence),
                )
                val basisEnum = candidate?.reading?.basis?.let { ConfirmationEligibility.perHundredBasis(it) }
                if (candidate == null || basisEnum == null) {
                    ScanDecision.Crop
                } else {
                    ScanDecision.ConfirmUnverified(candidate.reading.amount, basisEnum, candidate.rowText)
                }
            }
            ScanPresentationDecision.Action.RECOVERY -> {
                val target = FocusedAmountEntry.of(document)
                if (target != null) {
                    ScanDecision.FocusedEntry(target.basis, target.rowText)
                } else {
                    ScanDecision.Crop
                }
            }
            ScanPresentationDecision.Action.CROP_FALLBACK -> ScanDecision.Crop
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY -> {
                val target = FocusedAmountEntry.of(document)
                if (target != null) {
                    ScanDecision.FocusedEntry(target.basis, target.rowText)
                } else {
                    ScanDecision.Crop
                }
            }
        }
    }
}
