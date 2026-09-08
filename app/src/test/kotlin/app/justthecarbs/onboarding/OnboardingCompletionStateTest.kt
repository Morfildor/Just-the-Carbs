package app.justthecarbs.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class OnboardingCompletionStateTest {

    private fun tempStore(): DataStore<Preferences> {
        val dir = File.createTempFile("onboarding-completion-test", "").apply { delete(); mkdirs() }
        return PreferenceDataStoreFactory.create(produceFile = { File(dir, "settings.preferences_pb") })
    }

    private fun succeedingRepository(): SettingsRepository = SettingsRepository.forTesting(tempStore())

    /** Fails every write. Mirrors OnboardingViewModelTest's countingStore decorator, but throws. */
    private fun alwaysThrowingRepository(): SettingsRepository {
        val real = tempStore()
        val throwing = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("simulated write failure")
            }
        }
        return SettingsRepository.forTesting(throwing)
    }

    /** Fails the first write, succeeds on every write after that. */
    private fun failOnceThenSucceedRepository(): SettingsRepository {
        val real = tempStore()
        var attempts = 0
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                attempts++
                if (attempts == 1) throw IOException("simulated write failure")
                return real.updateData(transform)
            }
        }
        return SettingsRepository.forTesting(flaky)
    }

    @Test fun `finish moves to Saved on a successful write`() = runTest {
        val viewModel = OnboardingViewModel(succeedingRepository())

        assertEquals(OnboardingViewModel.CompletionState.Idle, viewModel.completionState.value)
        viewModel.finish()
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test fun `a repository failure sets Failed and never Saved -- the button must be re-enabled`() = runTest {
        val viewModel = OnboardingViewModel(alwaysThrowingRepository())

        viewModel.finish()

        assertTrue(viewModel.completionState.value is OnboardingViewModel.CompletionState.Failed)
    }

    @Test fun `retrying after a failure and succeeding reaches Saved`() = runTest {
        val viewModel = OnboardingViewModel(failOnceThenSucceedRepository())

        viewModel.finish() // fails
        assertTrue(viewModel.completionState.value is OnboardingViewModel.CompletionState.Failed)

        viewModel.finish() // retries, succeeds
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test fun `calling finish again after Saved does not re-run the write`() = runTest {
        val real = tempStore()
        var writeCount = 0
        val counting = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                writeCount++
                return real.updateData(transform)
            }
        }
        val viewModel = OnboardingViewModel(SettingsRepository.forTesting(counting))

        viewModel.finish()
        viewModel.finish()

        assertEquals(1, writeCount)
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }
}
