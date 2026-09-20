package app.justthecarbs.ui

/**
 * The lifecycle of one shared image, from arrival to consumption, as a value.
 *
 * ## What this exists to make testable
 *
 * The requirements this feature is held to are almost all *lifecycle* rules — "newest wins", "no
 * replay after rotation", "no replay after the user dismissed it", "held across onboarding,
 * consumed exactly once". Every one of them is a rule about a transition, and left as scattered
 * `var`s in an Activity none of them is reachable from a JVM test. This codebase has recorded
 * that failure mode twice already, and [SharedImageRequest] states the rule: **a rule no test can
 * reach is a rule that can be silently reverted.**
 *
 * So the transitions live here, pure, and the Activity holds one of these and calls the
 * transitions by name.
 *
 * ## The staged file, and why it is a plain path
 *
 * The bytes are copied at arrival ([app.justthecarbs.ui.scan.SharedImageIntake]), so from this
 * object's point of view a live share is a path plus a delivery id. A path rather than a `File`
 * keeps this Android-free and plain-JVM testable, matching every other decision object here.
 */
sealed interface SharedImageState {

    /** Nothing shared, or the share has been dealt with. The resting state. */
    data object None : SharedImageState

    /**
     * Arrived and being copied out of the sender's URI.
     *
     * A state of its own rather than an implicit gap, because a second share can land during it
     * and must supersede the first — which is decidable only if "a copy is running, under this
     * id" is something the app can say.
     */
    data class Staging(val id: Long) : SharedImageState

    /**
     * Copied, and waiting for onboarding to finish before the user is asked anything.
     *
     * The state that makes "a share never skips onboarding" survivable rather than destructive.
     * A shortcut arriving early is dropped because it can be tapped again; a share cannot be
     * re-sent without leaving the app, so it waits here.
     */
    data class HeldForOnboarding(val id: Long, val path: String) : SharedImageState

    /** Copied and ready: the chooser should be asking what the image contains. */
    data class Ready(val id: Long, val path: String) : SharedImageState

    /**
     * The copy failed, or the share was unusable. The user is told and offered a way Home.
     *
     * Carried as state rather than reported and forgotten, because the alternative is a share
     * that silently does nothing — indistinguishable, to the user, from the app ignoring them.
     */
    data class Failed(val id: Long) : SharedImageState
}

/**
 * The transitions, each enforcing one of the lifecycle rules.
 *
 * Deliberately a set of pure functions over [SharedImageState] rather than methods that mutate:
 * the Activity's field is the only mutable thing, and every rule about *when* a value may replace
 * it is checked here where a test can see it.
 */
object SharedImageTransitions {

    /**
     * Whether a result produced under [id] may still become state.
     *
     * The single staleness guarantee, and the same one [app.justthecarbs.ui.scan.ImportGeneration]
     * enforces for picked photos: a result may land only if its delivery is still the current one.
     * Cancellation is the optimisation — a copy already in flight completes regardless — so this
     * check, applied at the point a result becomes visible, is what actually stops a slow first
     * share overtaking a fast second one.
     */
    fun isCurrent(state: SharedImageState, id: Long): Boolean = currentId(state) == id

    /** The delivery id the app is currently working on, or 0 when at rest. */
    fun currentId(state: SharedImageState): Long = when (state) {
        SharedImageState.None -> 0L
        is SharedImageState.Staging -> state.id
        is SharedImageState.HeldForOnboarding -> state.id
        is SharedImageState.Ready -> state.id
        is SharedImageState.Failed -> state.id
    }

    /**
     * The staged file a state is holding, if any — what must be deleted when it is superseded.
     *
     * [SharedImageState.Staging] holds none: the copy owns its own partial file and deletes it
     * itself on failure, so reporting one here would invite a second delete of a file another
     * coroutine is still writing.
     */
    fun stagedPath(state: SharedImageState): String? = when (state) {
        is SharedImageState.HeldForOnboarding -> state.path
        is SharedImageState.Ready -> state.path
        SharedImageState.None, is SharedImageState.Staging, is SharedImageState.Failed -> null
    }

    /**
     * A new share arrives, superseding whatever was in flight.
     *
     * **Newest wins**, unconditionally: a user who shares a second image has changed their mind
     * about the first, whether the first was still copying, waiting at the chooser, or reporting
     * a failure. The caller deletes [stagedPath] of the old state — returned rather than deleted
     * here so this stays Android-free.
     */
    fun arrive(id: Long): SharedImageState = SharedImageState.Staging(id)

    /**
     * A copy finished under [id].
     *
     * Ignored unless [id] is still current, which is what stops an abandoned copy landing on top
     * of a newer share. [hasSeenOnboarding] decides whether the user is asked now or after the
     * carousel — checked at *completion* rather than at arrival, deliberately: a share that
     * arrives during the last seconds of onboarding should not be pinned behind a gate that has
     * since opened.
     */
    fun staged(
        state: SharedImageState,
        id: Long,
        path: String,
        hasSeenOnboarding: Boolean,
    ): SharedImageState? {
        if (!isCurrent(state, id)) return null
        return if (hasSeenOnboarding) {
            SharedImageState.Ready(id, path)
        } else {
            SharedImageState.HeldForOnboarding(id, path)
        }
    }

    /** A copy failed under [id]. Ignored unless still current, for [staged]'s reason. */
    fun failed(state: SharedImageState, id: Long): SharedImageState? {
        if (!isCurrent(state, id)) return null
        return SharedImageState.Failed(id)
    }

    /**
     * Onboarding completed: a held share becomes the question it was always going to be.
     *
     * Every other state is returned unchanged — in particular [SharedImageState.None], so
     * finishing the carousel on an ordinary first launch cannot conjure a chooser out of nothing.
     */
    fun onboardingCompleted(state: SharedImageState): SharedImageState = when (state) {
        is SharedImageState.HeldForOnboarding -> SharedImageState.Ready(state.id, state.path)
        else -> state
    }

    /**
     * The share has been acted on or dismissed: back to rest.
     *
     * This is the **no replay** rule. Consuming clears the state outright rather than marking it
     * handled, so a rotation, a return from another app or a later launch finds nothing to act
     * on. The caller deletes [stagedPath] first unless it has handed the file downstream.
     */
    fun consume(): SharedImageState = SharedImageState.None
}
