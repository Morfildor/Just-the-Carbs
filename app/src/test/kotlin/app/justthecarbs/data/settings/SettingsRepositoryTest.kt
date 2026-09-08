package app.justthecarbs.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SettingsRepositoryTest {

    /**
     * A DataStore over a fresh temp directory, safe to write to more than once.
     *
     * The scope is the load-bearing part, and it was missing until a test needed a *second* write.
     * Without an explicit scope the factory uses one backed by the test's own dispatcher, and under
     * `runTest` that interacts badly with DataStore's atomic `.tmp` -> file rename on Windows: the
     * second write finds the rename refused and DataStore reports it as "multiple instances of
     * DataStore for this file", which reads like a fixture bug and is really a file handle the
     * previous write has not finished releasing.
     *
     * Every test here wrote at most once, so nothing exercised it. Kept as a real scope on
     * [Dispatchers.IO] rather than worked around per-test, since "a store you may write to twice" is
     * what a settings fixture should be.
     */
    private fun tempDataStore(): DataStore<Preferences> {
        val dir = createTempDirectory("settings-test").toFile()
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
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

    @Test
    fun `hasSeenTutorial defaults to false`() = runTest {
        val repo = SettingsRepository.forTesting(tempDataStore())
        assertEquals(false, repo.settings.first().hasSeenTutorial)
    }

    @Test
    fun `setHasSeenTutorial persists true`() = runTest {
        val repo = SettingsRepository.forTesting(tempDataStore())
        repo.setHasSeenTutorial(true)
        assertTrue(repo.settings.first().hasSeenTutorial)
    }

    @Test
    fun `setting the carousel flag leaves the tutorial flag alone`() = runTest {
        // They are separate keys, not two readings of one. Setting either must leave the other
        // alone, which is what lets a first-run user see the carousel and then still be offered the
        // tutorial -- and what stops a future "simplification" collapsing them without a test
        // noticing.
        val repo = SettingsRepository.forTesting(tempDataStore())
        repo.setHasSeenOnboarding(true)

        val settings = repo.settings.first()
        assertTrue(settings.hasSeenOnboarding)
        assertEquals(false, settings.hasSeenTutorial)
    }

    @Test
    fun `setting the tutorial flag leaves the carousel flag alone`() = runTest {
        val repo = SettingsRepository.forTesting(tempDataStore())
        repo.setHasSeenTutorial(true)

        val settings = repo.settings.first()
        assertTrue(settings.hasSeenTutorial)
        assertEquals(false, settings.hasSeenOnboarding)
    }

    /**
     * A store whose backing file already holds [seed], written by a DataStore instance that is then
     * discarded.
     *
     * Seeding through a *separate* instance is what keeps each test to one write against the store
     * under test. Two writes to one DataStore inside a single `runTest` fail on Windows: the atomic
     * `.tmp` -> file rename is refused while the previous write still holds the handle, and
     * DataStore reports it as a misleading "multiple instances of DataStore for this file". That is
     * a fixture/platform limitation and not app behaviour — `recordLaunch` is a single `edit`, and
     * the app calls it once per launch.
     */
    private fun storeSeededWith(seed: (MutablePreferences) -> Unit): DataStore<Preferences> {
        val dir = createTempDirectory("settings-seeded").toFile()
        val file = { File(dir, "settings.preferences_pb") }
        val seedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        runBlocking {
            PreferenceDataStoreFactory.create(scope = seedScope, produceFile = file)
                .edit { seed(it) }
        }
        seedScope.cancel()
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = file,
        )
    }

    /**
     * The counter keys on the tutorial flag, not the carousel's.
     *
     * Only the *stopping* half is asserted here. The complementary case — that a set
     * `has_seen_onboarding` still lets the count rise — cannot be written against this fixture:
     * `recordLaunch` would have to actually write, and a write following the seed's write to the
     * same backing file is the Windows rename failure described above. That half is covered where
     * the rule lives, in `TutorialReminderTest.shouldCountLaunch`, which takes the flag as a
     * parameter and never touches a file.
     */
    @Test
    fun `the tutorial flag is what stops the launch count`() = runTest {
        val repo = SettingsRepository.forTesting(
            storeSeededWith {
                it[booleanPreferencesKey("has_seen_tutorial")] = true
                it[intPreferencesKey("launch_count")] = 3
            },
        )

        repo.recordLaunch()

        assertEquals("a retired reminder has nothing left to count", 3, repo.settings.first().launchCount)
    }
}
