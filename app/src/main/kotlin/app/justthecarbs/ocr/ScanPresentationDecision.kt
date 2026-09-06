package app.justthecarbs.ocr

/**
 * The scanner's final presentation decision, lifted out of the composable so it can be tested.
 *
 * ## The blind spot this closes
 *
 * The ninth session's fix — widening the automatic veto from `mayAdvance` to
 * [AutomaticScanAdvance.mayPresentAutomatically] — was correct, and reverting it failed **zero** JVM
 * tests. The reason is structural rather than an oversight in the test suite: the veto was a local
 * `val` inside `LabelScannerScreen`, a composable that binds a real camera, so nothing in the JVM
 * could reach it. That is the same shape as the eighth session's P0, which was an anonymous `else`
 * in the same file.
 *
 * A rule no test can reach is a rule that can be silently reverted. So the decision is a value here,
 * computed by a pure function of the evidence, and the composable's job is reduced to acting on it.
 *
 * ## What this does and does not decide
 *
 * It decides **which screen the user sees**, from an outcome every rule has already judged. It
 * applies no confidence rule of its own: every judgement is [EvidenceResolver]'s,
 * [AutomaticVerification]'s, [ScaleAmbiguity]'s and [ReadingEligibility]'s, already made. Adding a
 * threshold here would be a second, drifting copy of a safety rule — the failure mode this codebase
 * has recorded twice.
 */
internal object ScanPresentationDecision {

    /** What the scanner does with a resolved capture. Mirrors the `uiAction` an evidence bundle records. */
    enum class Action {
        /** Verified and terminal: skip both confirmations and release the photograph. */
        AUTO_ADVANCE,

        /** Confident, uncorroborated, and its scale is established — proposed on the frozen capture. */
        CONFIRM_ON_CAPTURE,

        /** Reached through a confirmed crop, where the ordinary proposal card applies. */
        CONFIRM,

        /**
         * A confident reading whose digits are withheld, or a resolved capture with nothing to
         * propose.
         *
         * Routes to the assisted path **on the photograph** — focused entry with the stated basis
         * preserved. The capture is retained: the user is being asked to read digits off it.
         */
        RECOVERY,

        /**
         * An automatic attempt found nothing worth presenting, so the crop screen takes over.
         *
         * ## Why this is a distinct action rather than a flavour of [RECOVERY]
         *
         * Both keep the photograph, so [releasesCapture] cannot tell them apart — but they are
         * different screens, and the difference is the *reason*. `RECOVERY` means the app read
         * something and will not show the digits; this means the app could not read anything, and
         * the rectangle is the one lever the user holds over an ambiguity, a conflict or a failed
         * read.
         *
         * It was previously computed in the composable, as `automatic && !mayPresentAutomatically`,
         * beside — and independently of — the decision that was supposed to be the authority. Two
         * expressions of one policy is the shape this object exists to remove, so the distinction
         * lives here.
         */
        CROP_FALLBACK,

        /**
         * The carbohydrate row and its basis were both established; only the digits failed.
         *
         * ## Why this is not [CROP_FALLBACK] (thirteenth session)
         *
         * `20260904-081307-240` is a large, flat Lidl carton printing `Koolhydraten 6,2 g`. The
         * pipeline found the row, resolved the `Ø/100 ml` column, and then declined the value cell
         * because the printed `g` had been recognised as a `0` (`6,20`). Everything except the
         * number was known — and the app sent the user to drag a rectangle.
         *
         * Cropping cannot help that capture. The rectangle was already correct, the row was already
         * found, and re-recognising the same pixels reproduces the same damaged glyph. The screen
         * the user needs is the one that says *"carbohydrate, per 100 ml — type the number"*, which
         * [FocusedAmountEntry] has described since the fourth session and which was reachable only
         * *after* a tap on the assisted screen.
         *
         * ## What it claims, and what it must not
         *
         * It claims only that the row and basis are established, and the claim is
         * [FocusedAmountEntry.of]'s own — reused rather than restated, so the routing and the screen
         * cannot disagree about whether a target exists. It reads no value, promotes no row and
         * relaxes no rule: a refused figure stays refused, and the user supplies the digits.
         *
         * The photograph is kept, because that is what the digits are read from.
         */
        FOCUSED_AMOUNT_ENTRY,
    }

    /**
     * The action for [outcome], given what verified it and the document it was read from.
     *
     * [automatic] mirrors the scanner's own gate: a capture the user already confirmed a crop for
     * keeps its confirmation step, because there they have been asked a question and an answer
     * appearing without acknowledgement reads as the app having ignored them.
     *
     * ## The veto, stated once
     *
     * An automatic attempt that [AutomaticScanAdvance.mayPresentAutomatically] declines falls back
     * to the crop screen — and that predicate is deliberately *wider* than `mayAdvance`. Asking
     * `mayAdvance` here is the ninth session's defect: it answers `false` for
     * [EvidenceResolver.Outcome.NeedsVerification], which is the right answer to "may this skip the
     * confirmation" and the wrong answer to "may this be shown at all". Three captures of that
     * session held a correct printed value and showed the user nothing.
     */
    fun decide(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument?,
        automatic: Boolean,
    ): Action {
        if (automatic &&
            !AutomaticScanAdvance.mayPresentAutomatically(outcome, verification, document)
        ) {
            // Nothing presentable — but "nothing to propose" is not the same as "nothing was
            // learned". When the label's own structure established the carbohydrate row and exactly
            // one per-100 basis, the app is missing only the digits, and asking for a crop invites
            // the user to fix something that is not wrong. See [Action.FOCUSED_AMOUNT_ENTRY].
            //
            // Asked *after* the veto rather than before it, deliberately: a capture that has
            // something to propose proposes it, and this is the fallback's fallback. Asked of the
            // same `document` the veto just judged, so the two cannot be looking at different
            // recognitions.
            if (FocusedAmountEntry.of(document) != null) return Action.FOCUSED_AMOUNT_ENTRY

            // ## The basis-complete recovery gap (nineteenth session, 2026-09-06)
            //
            // `FocusedAmountEntry.of` requires a *resolved per-100 column* — it has nothing to say
            // about a US-style linear panel, which has no columns at all and states its basis as a
            // printed serving sentence instead (`ServingDeclaration`). `RecoveryCandidates.of`
            // already resolves that shape (it is what a tap on the recovery screen has always
            // offered), but nothing consulted it here: a label whose only basis is a declared
            // serving fell straight to [Action.CROP_FALLBACK], and the crop screen — not the
            // recovery screen — is what the scanner opens. `RecoveryCandidates`'s already-correct
            // answer was never shown.
            //
            // Measured on `docs/Scan evidence 06-09/20260906-123544-918`, a Sempio Korean sauce US
            // Nutrition Facts panel: the main pipeline reports `BASIS_MISSING` (no per-100 column
            // exists to resolve), while `RecoveryCandidates.of` — consulting the same
            // `ServingDeclaration` — already resolves `6 g / 18 g serving`, normalizing to
            // `33.3 g/100g`. Routing to `Action.RECOVERY` here is what lets the assisted screen show
            // that already-resolved candidate instead of sending the user to drag a crop rectangle
            // that cannot fix a basis question a rectangle never answered.
            //
            // Reads no new value and relaxes no rule: [RecoveryCandidates] applies every suppression
            // it always has (unit accompaniment, cross-column contradiction, scale eligibility,
            // disputed-candidate exclusion) before a candidate reaches this list, so an empty list
            // here still falls through to [Action.CROP_FALLBACK] exactly as before.
            if (RecoveryCandidates.of(document).isNotEmpty()) return Action.RECOVERY

            return Action.CROP_FALLBACK
        }

        return when (AutomaticScanAdvance.presentation(outcome, verification, document, automatic)) {
            AutomaticScanAdvance.Presentation.Advance -> Action.AUTO_ADVANCE
            AutomaticScanAdvance.Presentation.ConfirmOnCapture -> Action.CONFIRM_ON_CAPTURE
            // The digits themselves are not trustworthy enough to propose. The photograph is kept
            // and the user is asked for the number, with the basis the label stated preserved.
            AutomaticScanAdvance.Presentation.Recover -> Action.RECOVERY
            // Not a confident reading: an ambiguity, a conflict, or nothing at all. Through a
            // confirmed crop these keep their existing screens, which the caller selects from the
            // outcome type; none of them is a proposal.
            AutomaticScanAdvance.Presentation.NotApplicable -> when (outcome) {
                is EvidenceResolver.Outcome.Nothing -> Action.RECOVERY
                else -> Action.CONFIRM
            }
        }
    }

    /**
     * Whether this action ends the scan, and so may release the frozen photograph.
     *
     * The invariant the eighth session's P0 violated: everything that still has a question for the
     * user keeps the photograph that question is about.
     *
     * Exhaustive rather than an equality test, so adding an [Action] is a compile error here instead
     * of silently defaulting a new screen to "the photograph may be recycled" — which is exactly the
     * mistake that drew a confirmation card over a live camera preview.
     */
    fun releasesCapture(action: Action): Boolean = when (action) {
        // Terminal: the calculator takes it from here.
        Action.AUTO_ADVANCE -> true
        // All of these still have a question for the user, and every one of those questions is
        // about the photograph.
        Action.CONFIRM_ON_CAPTURE -> false
        Action.CONFIRM -> false
        Action.RECOVERY -> false
        Action.CROP_FALLBACK -> false
        // The digits are read off the photograph, so it is exactly what must stay on screen.
        Action.FOCUSED_AMOUNT_ENTRY -> false
    }
}
