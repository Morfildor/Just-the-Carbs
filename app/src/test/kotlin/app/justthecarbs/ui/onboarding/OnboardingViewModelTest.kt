package app.justthecarbs.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * [SettingsRepository] is constructed from a real temp-file DataStore rather than a mock: this
 * project has no mockito-kotlin dependency, and the step functions never touch the repository
 * anyway, so a fake is both sufficient and avoids adding a new test dependency (mirrors the pattern
 * in `SettingsRepositoryTest`).
 */
class OnboardingViewModelTest {

    private fun fakeRepository(): SettingsRepository {
        val dir = File.createTempFile("onboarding-vm-test", "").apply { delete(); mkdirs() }
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsRepository.forTesting(store)
    }

    /**
     * A DataStore whose writes always fail.
     *
     * The whole `Failed` branch is unreachable with an in-memory fake that cannot fail, so without
     * this the retry path would be unexecuted code that merely compiles — the same trap this
     * codebase already recorded for the quick-calculation save path.
     */
    private fun failingRepository(): SettingsRepository {
        val dir = File.createTempFile("onboarding-vm-fail", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        val failing = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = throw java.io.IOException("disk full")
        }
        return SettingsRepository.forTesting(failing)
    }

    // ---- step machine -------------------------------------------------------------------------

    @Test
    fun `next advances up to the last step and stops`() {
        val viewModel = OnboardingViewModel(fakeRepository())
        assertEquals(0, viewModel.stepIndex.value)
        repeat(TUTORIAL_LAST_STEP) { viewModel.next() }
        assertEquals(TUTORIAL_LAST_STEP, viewModel.stepIndex.value)

        viewModel.next()
        assertEquals("next past the end must not run off the list", TUTORIAL_LAST_STEP, viewModel.stepIndex.value)
    }

    @Test
    fun `an out-of-range step cannot become state`() {
        val viewModel = OnboardingViewModel(fakeRepository())

        viewModel.showStep(99)
        assertEquals(TUTORIAL_LAST_STEP, viewModel.stepIndex.value)

        viewModel.showStep(-3)
        assertEquals(0, viewModel.stepIndex.value)
    }

    // ---- first-run persistence ----------------------------------------------------------------

    @Test
    fun `finishing the first run persists hasSeenTutorial before returning`() = runTest {
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        viewModel.finish()

        assertEquals(true, repository.settings.first().hasSeenTutorial)
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test
    fun `skipping persists exactly as finishing does`() = runTest {
        // Skip is not a lesser exit: someone who skips has decided they are done, and showing them
        // the tutorial again next launch would be the app overruling them. Both routes call
        // finish(), so this asserts the shared contract rather than a second code path.
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        viewModel.finish() // Skip on step 1, without advancing.

        assertEquals(0, viewModel.stepIndex.value)
        assertEquals(true, repository.settings.first().hasSeenTutorial)
    }

    @Test
    fun `repeated finish calls write to the store only once`() = runTest {
        val writeCount = AtomicInteger(0)
        val dir = File.createTempFile("onboarding-vm-test-2", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        val countingStore = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                writeCount.incrementAndGet()
                return real.updateData(transform)
            }
        }
        val repository = SettingsRepository.forTesting(countingStore)
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        viewModel.finish()
        viewModel.finish()
        viewModel.finish()

        assertEquals(1, writeCount.get())
        assertEquals(true, repository.settings.first().hasSeenTutorial)
    }

    @Test
    fun `two concurrent finish calls both return only after the write lands`() = runTest {
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        val first = async { viewModel.finish() }
        val second = async { viewModel.finish() }
        first.await()
        second.await()

        assertTrue(repository.settings.first().hasSeenTutorial)
    }

    // ---- failure handling ---------------------------------------------------------------------

    @Test
    fun `a failed write reports Failed and does not claim the tutorial is done`() = runTest {
        val viewModel = OnboardingViewModel(failingRepository(), TutorialMode.FIRST_RUN)

        viewModel.finish()

        val state = viewModel.completionState.value
        assertTrue("expected Failed, was $state", state is OnboardingViewModel.CompletionState.Failed)
        // The screen navigates only on Saved, so this is what keeps a failed write from dropping
        // the user onto Home as though onboarding had completed.
        assertFalse(state is OnboardingViewModel.CompletionState.Saved)
    }

    @Test
    fun `a retry after a failure is allowed and can succeed`() = runTest {
        // The mutex must not remember a previous failure -- only whether a write is in flight or
        // has already durably succeeded. A tutorial nobody can leave is worse than one that failed
        // to save.
        val dir = File.createTempFile("onboarding-vm-retry", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        var failNext = true
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                if (failNext) {
                    failNext = false
                    throw java.io.IOException("transient")
                }
                return real.updateData(transform)
            }
        }
        val repository = SettingsRepository.forTesting(flaky)
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        viewModel.finish()
        assertTrue(viewModel.completionState.value is OnboardingViewModel.CompletionState.Failed)

        viewModel.finish()

        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
        assertEquals(true, repository.settings.first().hasSeenTutorial)
    }

    // ---- replay mode --------------------------------------------------------------------------

    @Test
    fun `replay writes nothing at all`() = runTest {
        val writeCount = AtomicInteger(0)
        val dir = File.createTempFile("onboarding-vm-replay", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        val countingStore = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                writeCount.incrementAndGet()
                return real.updateData(transform)
            }
        }
        val repository = SettingsRepository.forTesting(countingStore)
        val viewModel = OnboardingViewModel(repository, TutorialMode.REPLAY)

        viewModel.finish()

        assertEquals("replay must not touch the onboarding flag", 0, writeCount.get())
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test
    fun `replay does not clear a tutorial flag that is already set`() = runTest {
        val repository = fakeRepository()
        repository.setHasSeenTutorial(true)

        val viewModel = OnboardingViewModel(repository, TutorialMode.REPLAY)
        viewModel.finish()

        assertEquals(true, repository.settings.first().hasSeenTutorial)
    }

    @Test
    fun `finishing the tutorial never marks the welcome carousel seen`() = runTest {
        // The two flags record different events and are set by different screens. If the tutorial
        // wrote the carousel's flag as well, a user who reached the coach marks by some route other
        // than the carousel would silently lose the carousel -- and, more importantly, the direction
        // this guards is the one that is easy to "tidy up" into a single flag later.
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository, TutorialMode.FIRST_RUN)

        viewModel.finish()

        val settings = repository.settings.first()
        assertEquals(true, settings.hasSeenTutorial)
        assertEquals(false, settings.hasSeenOnboarding)
    }

    @Test
    fun `replay closes even when the store is broken`() = runTest {
        // A replay has nothing to persist, so a failing DataStore must not be able to trap the user
        // in a tutorial they opened voluntarily from Settings.
        val viewModel = OnboardingViewModel(failingRepository(), TutorialMode.REPLAY)

        viewModel.finish()

        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test
    fun `mode defaults to first run`() {
        // The safe direction: a caller that forgets to pass a mode persists the flag rather than
        // silently skipping the write and re-showing the tutorial on every launch.
        assertEquals(TutorialMode.FIRST_RUN, OnboardingViewModel(fakeRepository()).mode)
    }
}
