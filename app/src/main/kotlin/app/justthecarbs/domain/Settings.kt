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
     * The one first-run flag. True once the user has finished the tutorial, skipped it, or
     * dismissed the reminder on Home — all three mean "I am done with this".
     */
    val hasSeenOnboarding: Boolean = false,
    /**
     * Launches recorded so far, including the current one.
     *
     * Not a usage statistic and never shown to anyone: its only reader is
     * [TutorialReminder.shouldShow], and it stops being incremented as soon as the reminder can no
     * longer appear. Zero means "not yet counted", which that rule treats as a first launch.
     */
    val launchCount: Int = 0,
)
