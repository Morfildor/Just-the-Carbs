package app.carbscan.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
