package app.justthecarbs.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRepositoryTest {

    private fun tempDataStore(): DataStore<Preferences> {
        val dir = File.createTempFile("settings-test", "").apply { delete(); mkdirs() }
        return PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
    }

    /**
     * A fresh install must open in Light, whatever the system theme is doing. The default lives in
     * two places that have to agree — [AppSettings]'s own default (what MainActivity renders for the
     * frame or two before DataStore has answered) and this fallback (what it renders afterwards) —
     * so both are asserted. If they disagreed the app would visibly flip theme on launch.
     */
    @Test
    fun `theme defaults to LIGHT when nothing is stored`() = runTest {
        val repo = SettingsRepository.forTesting(tempDataStore())
        assertEquals(ThemeChoice.LIGHT, repo.settings.first().theme)
    }

    @Test
    fun `AppSettings default theme is LIGHT`() {
        assertEquals(ThemeChoice.LIGHT, AppSettings().theme)
    }

    /**
     * An unrecognised stored value is not evidence the user wants the system theme — it is evidence
     * of a corrupt or downgraded preference file, which falls back to the fresh-install default.
     */
    @Test
    fun `an unknown stored theme falls back to LIGHT`() = runTest {
        val store = tempDataStore()
        store.edit { it[stringPreferencesKey("theme")] = "SEPIA" }
        assertEquals(ThemeChoice.LIGHT, SettingsRepository.forTesting(store).settings.first().theme)
    }

    /**
     * The point of the whole change: only the *default* moved. All three explicit choices must still
     * round-trip, or the theme selector has quietly become a two-option control.
     */
    @Test
    fun `every explicitly chosen theme round-trips`() = runTest {
        for (choice in ThemeChoice.entries) {
            val repo = SettingsRepository.forTesting(tempDataStore())
            repo.setTheme(choice)
            assertEquals(choice, repo.settings.first().theme)
        }
    }

    @Test
    fun `hasSeenOnboarding defaults to false`() = runTest {
        val store = tempDataStore()
        val repo = SettingsRepository.forTesting(store)
        assertEquals(false, repo.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `setHasSeenOnboarding persists true`() = runTest {
        val store = tempDataStore()
        val repo = SettingsRepository.forTesting(store)
        repo.setHasSeenOnboarding(true)
        assertTrue(repo.settings.first().hasSeenOnboarding)
    }
}
