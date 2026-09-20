package app.justthecarbs.ocr

/**
 * Decides what to do with a photo-picker result — as a value, not as a branch inside a composable.
 *
 * ## Why this is its own unit
 *
 * This repo has twice shipped a safety rule as a local `val` or an anonymous `else` inside
 * `LabelScannerScreen`, a composable that binds a real camera and is unreachable from the JVM. Both
 * times the rule could be reverted without failing a single test (the eighth session's P0, and the
 * ninth session's veto). The note this file exists to honour is the one already written there:
 * **a rule no test can reach is a rule that can be silently reverted.**
 *
 * The three questions below are rules of exactly that kind. Each one, got wrong, produces a wrong
 * carbohydrate figure or a stale one on screen, and none of them is observable from a camera test.
 *
 * ## The three rules
 *
 * 1. **A consumed result is never replayed.** `rememberLauncherForActivityResult` re-delivers its
 *    last result to a recreated composition (rotation, process death, returning from Settings). A
 *    picker result is an *event*, not state: replaying it would silently re-import a photograph the
 *    user already acted on — over the top of whatever they are looking at now.
 * 2. **Only the newest selection may land.** Staging plus recognition is seconds of work, so a user
 *    who picks a second photo before the first finishes has two in flight. The second is the one
 *    they mean. This mirrors the guard `takePictureNow` already applies to captures, and uses the
 *    same identity — the coordinator's work generation — so a capture and an import cannot both
 *    believe they are current.
 * 3. **Cancelling changes nothing.** The picker returning no URI is not a failure and must not
 *    disturb the live camera, the current reading, or anything already on screen.
 */
internal object ImportedPhotoIntake {

    /** What the screen should do with a result the launcher just delivered. */
    sealed interface Decision {
        /**
         * Stage and recognise it under [workGeneration], which is this import's own identity for
         * every downstream staleness check — exactly as a capture's is.
         */
        data class Import(val workGeneration: Long) : Decision

        /** Do nothing at all, and say why. Never touches state the user is looking at. */
        data class Ignore(val reason: Reason) : Decision
    }

    enum class Reason {
        /** The user backed out of the picker. Not an error; nothing to report. */
        CANCELLED,

        /**
         * This exact result has already been acted on and the launcher is re-delivering it after a
         * configuration change or process recreation.
         */
        ALREADY_CONSUMED,
    }

    /**
     * @param hasSelection whether the launcher produced a URI at all. False is a cancellation.
     * @param resultToken identifies the delivered result. Any stable per-result value works; the
     *   screen uses the URI's own string, which is what the launcher re-delivers unchanged on a
     *   recreation — so a replay is recognisable by value rather than by guessing from timing.
     * @param lastConsumedToken the token of the result already acted on, or null if none.
     * @param beginWork starts a new work generation. Called **only** on the accepted path, so a
     *   cancellation or a replay cannot invalidate in-flight work — a user who opens the picker,
     *   changes their mind and backs out must find the screen exactly as they left it, including a
     *   recognition still running from a capture they made beforehand.
     */
    fun decide(
        hasSelection: Boolean,
        resultToken: String?,
        lastConsumedToken: String?,
        beginWork: () -> Long,
    ): Decision = when {
        !hasSelection || resultToken == null -> Decision.Ignore(Reason.CANCELLED)
        resultToken == lastConsumedToken -> Decision.Ignore(Reason.ALREADY_CONSUMED)
        else -> Decision.Import(beginWork())
    }
}
