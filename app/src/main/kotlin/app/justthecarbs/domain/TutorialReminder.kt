package app.justthecarbs.domain

/**
 * Whether Home should offer the tutorial reminder.
 *
 * The app does not open the tutorial by itself. A first launch lands on Home like any other launch,
 * and the tutorial is offered as a card the user can take or dismiss. That way an experienced user
 * reinstalling the app is never held up by a walkthrough they already know — they dismiss it once
 * and it is gone — while someone new still gets an obvious, repeated invitation rather than one
 * chance they might miss.
 *
 * The reminder is shown for at most [REMINDER_LAUNCHES] launches *after* the first. It stops early
 * and permanently as soon as `hasSeenOnboarding` is true, which is set by finishing the tutorial, by
 * skipping it, or by dismissing the reminder on Home — all three are the user saying they are done
 * with it, and the app must not keep asking after that.
 *
 * Pure and settings-shaped rather than a flag someone toggles, so the decision is one testable rule
 * instead of a condition spread across Home, the ViewModel and the repository.
 */
object TutorialReminder {

    /**
     * How many launches after the first still show the reminder.
     *
     * Five, per the owner's instruction. The count is of launches, not of days or sessions: the
     * question the reminder answers is "have you had a chance to notice this yet", and a launch is
     * the occasion on which someone could.
     */
    const val REMINDER_LAUNCHES = 5

    /**
     * True when Home should render the reminder.
     *
     * [launchCount] is the number of launches recorded so far, counting the current one, so it is 1
     * on a genuine first run. The reminder appears on that first launch and on the next five, i.e.
     * launches 1..6 inclusive — "the next 5 starts after the 1st".
     *
     * A [launchCount] at or below zero is treated as a first launch rather than as "past the
     * window": the counter is persisted, and a missing or corrupt value should invite a new user in,
     * not silently hide the only pointer to the tutorial.
     */
    fun shouldShow(hasSeenOnboarding: Boolean, launchCount: Int): Boolean {
        if (hasSeenOnboarding) return false
        if (launchCount <= 0) return true
        return launchCount <= REMINDER_LAUNCHES + 1
    }

    /**
     * Whether the launch counter is still worth incrementing.
     *
     * Once the reminder can never appear again there is nothing to count, and a counter that climbs
     * forever is a number about the user's habits that this app has no use for (§2). It stops rather
     * than saturating.
     */
    fun shouldCountLaunch(hasSeenOnboarding: Boolean, launchCount: Int): Boolean =
        !hasSeenOnboarding && launchCount <= REMINDER_LAUNCHES + 1
}
