package app.justthecarbs.ocr

/**
 * The tactile vocabulary of the nutrition-label scanner, as a pure decision.
 *
 * ## Why this is a value and not three `performHapticFeedback` calls in the composable
 *
 * The same reason [ScanPresentationDecision] is a value. A rule that lives as a bare call inside
 * `LabelScannerScreen` — a composable that binds a real camera — cannot be reached from the JVM, so
 * it can be silently changed or reverted with a green suite. This file's own pass notes record that
 * shape three times in that one file: an anonymous `else` (eighth session), a local `val` (ninth),
 * and a policy restated imperatively beside the pure decision it was supposed to obey (eleventh).
 *
 * A haptic is a safety-adjacent output on this screen — it is the app making a claim, without words,
 * about a carbohydrate figure someone may dose insulin from. That claim belongs in a testable
 * function.
 *
 * ## THE SAFETY RULE: a cue reports ATTENTION, never CORRECTNESS
 *
 * This is the whole design and every effect choice below follows from it.
 *
 * The app cannot know whether a scanned figure is right. Every screen in this pipeline is built on
 * that: [ScanPresentationDecision] refuses to advance an uncorroborated reading, [ScaleAmbiguity]
 * withholds digits whose decimal scale nothing established, and [ConfirmationEligibility] makes a
 * one-tap confirmation unconstructible for a reading that has not earned it. A haptic that meant
 * "got it — that's your number" would assert, through a channel with no words and no nuance, exactly
 * the confidence the parser spent all of that architecture refusing to assert.
 *
 * So a cue answers one question only: **what does the user now have to do?**
 *
 * - Is the app asking you something? → [NeedsDecision]
 * - Did the app carry you onward? → [Advanced]
 * - Is the app handing the job back? → [HandedBack]
 * - Is nothing different yet? → no cue at all.
 *
 * ### The ordering that makes this structural rather than a promise
 *
 * The strongest effect is deliberately **not** on the most confident outcome. [Action.AUTO_ADVANCE]
 * — the one path where the app is most sure, having required independent corroboration *and* an
 * established decimal scale — gets [Advanced], the lightest cue in the vocabulary. The outcomes that
 * need the user to *look at the printed package* get the firm one.
 *
 * That inversion is the point. If the effect scaled with the app's confidence, a user would learn
 * over a few dozen scans that a strong buzz means a trustworthy number, and would eventually stop
 * checking the one they should check hardest. Here the strength tracks how much of the user's
 * attention is being asked for, so the habit it builds is "firm buzz means read the screen" — which
 * is true, and is what this app wants someone to do.
 *
 * ## Why not "one when the photo is taken, one when it reads, one when the calculation is ready"
 *
 * Two of those three are the same event and the third is usually invisible.
 *
 * On the [Action.AUTO_ADVANCE] path the OCR completing **is** the calculation arriving — the scanner
 * calls `onUseValue` in the same statement that releases the capture, and the calculator is the next
 * screen. Firing at "read" and again at "calculation ready" would be two buzzes for one transition,
 * which teaches the hand nothing and costs the vocabulary its meaning. On every other path there is
 * no calculation at all yet: the app is still asking a question, and a "ready" cue would fire for a
 * number the user has not accepted and may be about to reject.
 *
 * The third moment therefore does not exist as a distinct thing to report, so it gets no cue. See
 * [None] for the moments deliberately left silent, which are most of them.
 */
internal enum class ScanHapticCue {
    /**
     * The app has finished looking and is putting a question on the frozen photograph.
     *
     * `HapticFeedbackType.Confirm` at the binding site — and the name is about the *interaction*
     * completing, not about a value being correct. Android documents it as "the confirmation or
     * successful completion of a user interaction"; the interaction that completed here is *the
     * scan*, i.e. the app has stopped working and it is the user's turn. It fires identically
     * whether the question is "is this number right?" or "the scale is unresolved, check the row" —
     * because from the hand's point of view those are the same instruction: stop waiting, look.
     *
     * Deliberately the same cue for [ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE] and
     * [ScanPresentationDecision.Action.CONFIRM_UNVERIFIED], which are two *different* evidence gaps.
     * A tactile channel cannot carry that distinction — attempting to encode "and this one is
     * shakier" in a vibration would be a claim the user cannot decode and might guess at wrongly.
     * The screen says which question is open, in words, next to the printed row. The buzz only says
     * that there is one.
     */
    NeedsDecision,

    /**
     * The scan resolved and the app has already moved on to the calculator.
     *
     * The lightest cue in the vocabulary, and that is the safety argument stated above: this is the
     * one outcome the app is most confident about, so it gets the *least* emphatic acknowledgement.
     * It exists to answer "did my capture do anything?" for a user who has taken the phone away from
     * the package and is not looking at the screen — nothing more.
     *
     * `HapticFeedbackType.SegmentTick` at the binding site: Android describes it as the user moving
     * between discrete choices, which is a movement cue rather than a success cue, and it is
     * documented as soft enough to be unobtrusive.
     */
    Advanced,

    /**
     * The app could not get there alone and is handing the work back.
     *
     * Covers the crop fallback, the recovery/assisted path and focused amount entry. All three keep
     * the photograph and ask the user to supply something — a tighter rectangle, the row, or the
     * digits.
     *
     * `HapticFeedbackType.Reject` at the binding site. **This is not an error buzz and must not be
     * read as scolding**: nothing has gone wrong, and this app treats a refusal as the correct
     * outcome whenever the evidence does not support a figure — a safe non-result is better than a
     * confidently wrong carbohydrate value. The cue is distinct from [NeedsDecision] because the
     * required action genuinely differs: [NeedsDecision] means *check this*, this means *supply
     * this*. A user who learns the difference knows whether to reach for the package or the keyboard
     * before looking.
     */
    HandedBack,
    ;

    companion object {
        /**
         * The cue for a completed OCR pass, or null for silence.
         *
         * Keyed on [ScanPresentationDecision.Action] rather than on the outcome, the reading, or any
         * confidence value — so this can never become a second, drifting copy of a safety rule. It
         * reads a decision every rule has already made and says only how to announce it. It cannot
         * promote a reading, suppress one, or change which screen appears.
         *
         * [automatic] is the scanner's own gate, threaded through unchanged. A capture the user
         * reached by confirming a crop is **silent on every outcome**, and that is a deliberate
         * restraint rather than an oversight — see [None] and the parameter's own note below.
         *
         * @param automatic true for the post-capture pass against the app's own rectangle; false when
         *   the user pressed *Read table* on a crop they confirmed. In the second case the user's
         *   finger is already on the screen and their eyes are already on the result they asked for,
         *   so a vibration reports something they are actively watching happen. A cue that tells you
         *   what you can already see is noise, and noise is what makes people turn the setting off —
         *   taking the two cues that *do* carry information with it.
         */
        fun forCompletedPass(
            action: ScanPresentationDecision.Action,
            automatic: Boolean,
        ): ScanHapticCue? {
            // The user is watching this happen because they asked for it. Nothing to announce.
            if (!automatic) return null

            // Exhaustive, deliberately, and with no `else`: adding a
            // [ScanPresentationDecision.Action] must be a compile error here rather than silently
            // inheriting whichever cue a default branch happened to name. That is the same reason
            // [ScanPresentationDecision.releasesCapture] is exhaustive, and it matters more here
            // than it looks — a new "we advanced" action defaulting into [NeedsDecision] would put
            // the app's firmest attention cue on a screen the user never has to check.
            return when (action) {
                // Terminal and corroborated. The lightest cue: see the class KDoc's inversion note.
                ScanPresentationDecision.Action.AUTO_ADVANCE -> Advanced

                // A question on the frozen photograph. Both of these ask the user to compare a
                // figure against the printed row; they differ in which evidence gap is open, which
                // is a distinction for the screen's words and not for the hand.
                ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE -> NeedsDecision
                ScanPresentationDecision.Action.CONFIRM_UNVERIFIED -> NeedsDecision

                // The app is handing the job back and needs something supplied.
                ScanPresentationDecision.Action.CROP_FALLBACK -> HandedBack
                ScanPresentationDecision.Action.RECOVERY -> HandedBack
                ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY -> HandedBack

                // Unreachable from the automatic path — [ScanPresentationDecision.decide] returns
                // CONFIRM only via the `!automatic` branch of its final `when`, which this function
                // has already returned null for above. Named rather than defaulted so the
                // exhaustiveness guarantee holds, and silent because if it ever did become
                // reachable, the honest cue for "the ordinary proposal card" is the one a confirmed
                // crop already gets: none.
                ScanPresentationDecision.Action.CONFIRM -> null
            }
        }
    }
}
