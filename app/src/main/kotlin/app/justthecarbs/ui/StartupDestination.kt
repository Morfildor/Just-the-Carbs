package app.justthecarbs.ui

/**
 * Which screen the app should open on, given the launch intent and whether onboarding has been seen.
 *
 * Pure and Android-free so the rules below are JVM-testable: the launcher hands an action string to
 * `MainActivity`, and deciding what that means is exactly the kind of branch that is easy to get
 * subtly wrong and impossible to check on a device without reinstalling the app between every case.
 */
enum class StartupDestination {
    /** Ordinary launch — whatever the nav graph's own start destination resolves to. */
    DEFAULT,

    /** The barcode scanner, from the launcher's *Barcode* shortcut. */
    SCAN_BARCODE,

    /** The nutrition-label scanner, from the launcher's *Label* shortcut. */
    SCAN_LABEL,

    /**
     * Home with its search field focused, from the launcher's *Search* shortcut.
     *
     * The odd one out, and deliberately so: the other two name a screen to push, while this one
     * names a *state of the start destination*. Search is not a screen of its own in this app —
     * Home owns the field and the live results — so the shortcut asks Home to take focus rather
     * than navigating anywhere. See the nav host, where that difference is what stops this
     * shortcut pushing a destination onto the stack.
     */
    SEARCH,
    ;

    companion object {

        /** Intent actions published by `res/xml/shortcuts.xml`. */
        const val ACTION_SCAN_BARCODE = "app.justthecarbs.action.SCAN_BARCODE"
        const val ACTION_SCAN_LABEL = "app.justthecarbs.action.SCAN_LABEL"
        const val ACTION_SEARCH = "app.justthecarbs.action.SEARCH"

        /**
         * Resolves a launch [action] against whether the user has completed onboarding.
         *
         * **A shortcut never skips the welcome carousel**, and that is the whole reason this is a
         * function rather than a `when` at the call site. The carousel is a gate: it is the only
         * place `hasSeenOnboarding` is written, and the nav graph decides its start destination from
         * that flag. A shortcut that navigated straight to a camera on a first launch would put a
         * live preview in front of someone who has not yet been told what the app does, and would
         * leave the carousel to appear *later*, on some subsequent ordinary launch, which is worse
         * than never showing it. So before onboarding is complete every shortcut resolves to
         * [DEFAULT] and the app opens exactly as it would have anyway.
         *
         * An unrecognised or absent action is [DEFAULT] rather than an error: the launcher is not
         * the only thing that can start this activity, and an ordinary launch has no action at all.
         */
        fun from(action: String?, hasSeenOnboarding: Boolean): StartupDestination {
            if (!hasSeenOnboarding) return DEFAULT
            return when (action) {
                ACTION_SCAN_BARCODE -> SCAN_BARCODE
                ACTION_SCAN_LABEL -> SCAN_LABEL
                ACTION_SEARCH -> SEARCH
                else -> DEFAULT
            }
        }
    }
}

/**
 * One shortcut delivery: where to go, plus which delivery it was.
 *
 * The [id] exists because the destination alone is not enough to drive a `LaunchedEffect`. Tapping
 * the *same* shortcut twice produces the same [destination] both times, so an effect keyed on the
 * destination would not re-run and the second tap would appear to do nothing — measured on device,
 * where a second *Barcode* tap left the app sitting on Home. Each delivery gets a fresh [id], so
 * every tap is a distinct event even when it asks for the screen already asked for.
 *
 * [NONE] is the resting value and carries id 0, so an ordinary launch never navigates.
 */
data class StartupRequest(
    val destination: StartupDestination = StartupDestination.DEFAULT,
    val id: Long = 0L,
) {
    companion object {
        /** No shortcut: an ordinary launch. */
        val NONE = StartupRequest()
    }
}
