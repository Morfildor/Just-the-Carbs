package app.justthecarbs.domain

/** Appearance choice (§43). */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * The whole of the app's settings (§43). Intentionally four values: anything that does not make
 * scan → portion → carbs faster, safer or clearer does not belong here (§74).
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.LIGHT,
    val resultStyle: ResultStyle = ResultStyle.DECIMAL_DOMINANT,
    val hapticsEnabled: Boolean = true,
    /**
     * True once the welcome carousel has been through — finished or skipped.
     *
     * Gates the start destination and nothing else. It says the user has been introduced to the
     * app, not that they have been shown where its controls are; [hasSeenTutorial] answers that
     * separate question. Keeping them apart is what lets a first-run user get the carousel *and*
     * then be offered the coach marks, rather than one standing in for the other.
     */
    val hasSeenOnboarding: Boolean = false,
    /**
     * True once the user is done with the coach-mark tutorial — finished it, skipped it, or
     * dismissed the reminder card on Home. All three mean "I am done with this".
     *
     * Read only by [TutorialReminder.shouldShow]. Deliberately separate from [hasSeenOnboarding];
     * see that field and `TutorialReminder` for why one flag cannot serve both.
     */
    val hasSeenTutorial: Boolean = false,
    /**
     * Launches recorded so far, including the current one.
     *
     * Not a usage statistic and never shown to anyone: its only reader is
     * [TutorialReminder.shouldShow], and it stops being incremented as soon as the reminder can no
     * longer appear. Zero means "not yet counted", which that rule treats as a first launch.
     */
    val launchCount: Int = 0,
)
