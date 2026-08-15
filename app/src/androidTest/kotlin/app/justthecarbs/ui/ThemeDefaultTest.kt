package app.justthecarbs.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The fresh-install default is Light **regardless of the Android system theme**, while all three
 * user-selectable choices keep their existing behaviour.
 *
 * These assertions are about the theme actually applied, not about which enum was passed in: every
 * case reads `MaterialTheme.colorScheme.background` from inside the composition. That is the seam
 * where a wrong default would be visible to a user, and it is why these tests are instrumented —
 * `isSystemInDarkTheme()` resolves the real [Configuration], which needs a device.
 *
 * **Instrumented: needs a device or emulator.**
 */
class ThemeDefaultTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Every background this class needs, captured in one composition.
     *
     * The rule's `setContent` may only be called once per test, so each case renders all of its
     * variants together rather than measuring them one call at a time. Overriding
     * [LocalConfiguration] is what makes "regardless of the system theme" testable without touching
     * device settings: `isSystemInDarkTheme()` reads through it, so both system states are exercised
     * in a single pass on a single emulator.
     */
    private class Backgrounds {
        var defaultUnderLightSystem = Color.Unspecified
        var defaultUnderDarkSystem = Color.Unspecified
        var appSettingsDefaultUnderDarkSystem = Color.Unspecified
        var light = Color.Unspecified
        var dark = Color.Unspecified
        var systemChoiceUnderLightSystem = Color.Unspecified
        var systemChoiceUnderDarkSystem = Color.Unspecified
        var lightChoiceUnderDarkSystem = Color.Unspecified
        var darkChoiceUnderLightSystem = Color.Unspecified
        var isSystemInDarkThemeUnderForcedDark = false
        var isSystemInDarkThemeUnderForcedLight = true
    }

    /** Forces the system ui-mode for everything composed inside [content]. */
    @Composable
    private fun WithSystemDark(systemDark: Boolean, content: @Composable () -> Unit) {
        val base = LocalConfiguration.current
        val forced = Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides forced, content = content)
    }

    private fun measure(): Backgrounds {
        val out = Backgrounds()
        compose.setContent {
            WithSystemDark(systemDark = false) {
                out.isSystemInDarkThemeUnderForcedLight = isSystemInDarkTheme()
                // No themeChoice argument: exactly what a caller relying on the default gets.
                JustTheCarbsTheme { out.defaultUnderLightSystem = MaterialTheme.colorScheme.background }
                JustTheCarbsTheme(themeChoice = ThemeChoice.LIGHT) { out.light = MaterialTheme.colorScheme.background }
                JustTheCarbsTheme(themeChoice = ThemeChoice.DARK) { out.dark = MaterialTheme.colorScheme.background }
                JustTheCarbsTheme(themeChoice = ThemeChoice.SYSTEM) {
                    out.systemChoiceUnderLightSystem = MaterialTheme.colorScheme.background
                }
                JustTheCarbsTheme(themeChoice = ThemeChoice.DARK) {
                    out.darkChoiceUnderLightSystem = MaterialTheme.colorScheme.background
                }
            }
            WithSystemDark(systemDark = true) {
                out.isSystemInDarkThemeUnderForcedDark = isSystemInDarkTheme()
                JustTheCarbsTheme { out.defaultUnderDarkSystem = MaterialTheme.colorScheme.background }
                JustTheCarbsTheme(themeChoice = AppSettings().theme) {
                    out.appSettingsDefaultUnderDarkSystem = MaterialTheme.colorScheme.background
                }
                JustTheCarbsTheme(themeChoice = ThemeChoice.SYSTEM) {
                    out.systemChoiceUnderDarkSystem = MaterialTheme.colorScheme.background
                }
                JustTheCarbsTheme(themeChoice = ThemeChoice.LIGHT) {
                    out.lightChoiceUnderDarkSystem = MaterialTheme.colorScheme.background
                }
            }
        }
        compose.waitForIdle()
        return out
    }

    /**
     * Guards every other test in this class: if light and dark shared a background, or if forcing
     * the configuration did not actually reach `isSystemInDarkTheme()`, the assertions below would
     * pass without proving anything.
     */
    @Test
    fun theHarnessCanTellLightFromDarkAndDrivesTheSystemTheme() {
        val bg = measure()
        assertNotEquals(bg.light, bg.dark)
        assertTrue(bg.light != Color.Unspecified && bg.dark != Color.Unspecified)
        assertEquals(true, bg.isSystemInDarkThemeUnderForcedDark)
        assertEquals(false, bg.isSystemInDarkThemeUnderForcedLight)
    }

    @Test
    fun defaultThemeIsLightWhenTheSystemIsLight() {
        val bg = measure()
        assertEquals(bg.light, bg.defaultUnderLightSystem)
    }

    /** The requirement, stated directly: system dark must not change the fresh-install default. */
    @Test
    fun defaultThemeIsStillLightWhenTheSystemIsDark() {
        val bg = measure()
        assertEquals(bg.light, bg.defaultUnderDarkSystem)
        assertNotEquals(bg.dark, bg.defaultUnderDarkSystem)
    }

    /** The same must hold when the default arrives as data rather than as a defaulted argument. */
    @Test
    fun defaultAppSettingsRenderLightUnderSystemDark() {
        val bg = measure()
        assertEquals(bg.light, bg.appSettingsDefaultUnderDarkSystem)
    }

    /** An explicit SYSTEM choice must still follow the system — in both directions. */
    @Test
    fun explicitSystemChoiceStillFollowsTheSystem() {
        val bg = measure()
        assertEquals(bg.dark, bg.systemChoiceUnderDarkSystem)
        assertEquals(bg.light, bg.systemChoiceUnderLightSystem)
    }

    /** An explicit DARK choice stays dark even when the system is light. */
    @Test
    fun explicitDarkChoiceIgnoresALightSystem() {
        val bg = measure()
        assertEquals(bg.dark, bg.darkChoiceUnderLightSystem)
    }

    /** An explicit LIGHT choice stays light even when the system is dark. */
    @Test
    fun explicitLightChoiceIgnoresADarkSystem() {
        val bg = measure()
        assertEquals(bg.light, bg.lightChoiceUnderDarkSystem)
    }
}
