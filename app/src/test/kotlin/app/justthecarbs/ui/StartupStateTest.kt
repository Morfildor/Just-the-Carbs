package app.justthecarbs.ui

import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [asStartupState] is what stops a returning user's start destination being decided from
 * [AppSettings]'s synthetic default: [StartupState.Loading] only ever comes from the caller's own
 * `collectAsStateWithLifecycle(initialValue = ...)`, never from this mapping itself, so the first
 * value this Flow actually emits must always be [StartupState.Ready] wrapping a real settings value.
 */
class StartupStateTest {

    @Test
    fun `the first emission is always Ready, never a synthetic default`() = runTest {
        val source = flowOf(AppSettings(hasSeenOnboarding = true))

        val first = source.asStartupState().toList().first()

        assertTrue(first is StartupState.Ready)
        assertEquals(true, (first as StartupState.Ready).settings.hasSeenOnboarding)
    }

    @Test
    fun `Ready carries the settings value unchanged`() = runTest {
        val settings = AppSettings(theme = ThemeChoice.DARK, hasSeenOnboarding = false)
        val source = flowOf(settings)

        val result = source.asStartupState().toList().first() as StartupState.Ready

        assertEquals(settings, result.settings)
    }
}
