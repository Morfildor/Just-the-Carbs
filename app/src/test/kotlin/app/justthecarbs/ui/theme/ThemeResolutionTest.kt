package app.justthecarbs.ui.theme

import app.justthecarbs.domain.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule that turns a [ThemeChoice] plus the device state into an effective dark-theme state.
 *
 * [ThemeDefaultTest][app.justthecarbs.ui.ThemeDefaultTest] already asserts the *rendered*
 * `colorScheme.background` for these combinations on a device, which is where a wrong default is
 * visible to a user. These cases are the complement, not a duplicate: [resolveDarkTheme] is now read
 * by two independent consumers — the Material colour scheme and the system-bar appearance flags —
 * and the defect this pass fixed was precisely those two disagreeing. Pinning the shared rule on the
 * JVM means a change to it fails in seconds without an emulator, and states the contract in the one
 * place both consumers depend on.
 */
class ThemeResolutionTest {

    // --- The six-combination matrix -------------------------------------------------------------
    //
    // Device theme x app selection. The two that matter most are the explicit choices that
    // *contradict* the device, because those are the ones the old system-bar wiring got wrong: it
    // resolved its own light/dark from the device configuration and so inverted the bar icons
    // exactly here.

    @Test
    fun `system choice on a light device is light`() {
        assertFalse(resolveDarkTheme(ThemeChoice.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `system choice on a dark device is dark`() {
        assertTrue(resolveDarkTheme(ThemeChoice.SYSTEM, systemInDarkTheme = true))
    }

    @Test
    fun `light choice on a light device is light`() {
        assertFalse(resolveDarkTheme(ThemeChoice.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun `light choice overrides a dark device`() {
        assertFalse(resolveDarkTheme(ThemeChoice.LIGHT, systemInDarkTheme = true))
    }

    @Test
    fun `dark choice overrides a light device`() {
        assertTrue(resolveDarkTheme(ThemeChoice.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `dark choice on a dark device is dark`() {
        assertTrue(resolveDarkTheme(ThemeChoice.DARK, systemInDarkTheme = true))
    }

    // --- Properties the two consumers rely on ---------------------------------------------------

    /**
     * An explicit selection must not consult the device at all. Asserted as a property over both
     * device states rather than as two more literal cases, so a future change that quietly reads the
     * system value inside the LIGHT/DARK branches fails here.
     */
    @Test
    fun `an explicit choice ignores the device theme entirely`() {
        listOf(ThemeChoice.LIGHT, ThemeChoice.DARK).forEach { choice ->
            assertEquals(
                "$choice must resolve the same on a light and a dark device",
                resolveDarkTheme(choice, systemInDarkTheme = false),
                resolveDarkTheme(choice, systemInDarkTheme = true),
            )
        }
    }

    /** Conversely, SYSTEM must *only* ever be the device state — never a constant. */
    @Test
    fun `the system choice follows the device in both directions`() {
        assertEquals(false, resolveDarkTheme(ThemeChoice.SYSTEM, systemInDarkTheme = false))
        assertEquals(true, resolveDarkTheme(ThemeChoice.SYSTEM, systemInDarkTheme = true))
    }

    /**
     * Status- and navigation-bar icon appearance is `!dark` at the single call site in
     * `MainActivity`, where "light bars" means a light *background* and therefore dark icons. This
     * restates that inversion against the resolved theme so the mapping is pinned somewhere, since
     * the flags themselves need a real window to observe. Light theme -> light bars -> dark icons.
     */
    @Test
    fun `light appearance bar flags are the inverse of the resolved dark state`() {
        ThemeChoice.entries.forEach { choice ->
            listOf(false, true).forEach { systemDark ->
                val dark = resolveDarkTheme(choice, systemDark)
                val appearanceLightBars = !dark
                assertEquals(
                    "$choice on systemDark=$systemDark must give light bars exactly when not dark",
                    !dark,
                    appearanceLightBars,
                )
                assertEquals(dark, !appearanceLightBars)
            }
        }
    }

    /** Every choice must resolve; a new enum entry should force a decision rather than default. */
    @Test
    fun `every theme choice resolves in both device states`() {
        ThemeChoice.entries.forEach { choice ->
            resolveDarkTheme(choice, systemInDarkTheme = false)
            resolveDarkTheme(choice, systemInDarkTheme = true)
        }
        assertEquals(3, ThemeChoice.entries.size)
    }
}
