package app.justthecarbs.domain

/** Appearance choice (§43). */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * The whole of the app's settings (§43). Intentionally four values: anything that does not make
 * scan → portion → carbs faster, safer or clearer does not belong here (§74).
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val resultStyle: ResultStyle = ResultStyle.DECIMAL_DOMINANT,
    val hapticsEnabled: Boolean = true,
    val hasSeenOnboarding: Boolean = false,
)
