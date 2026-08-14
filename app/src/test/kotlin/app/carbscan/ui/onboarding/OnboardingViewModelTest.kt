package app.carbscan.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.carbscan.data.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [SettingsRepository] is constructed from a real temp-file DataStore rather than a mock: this
 * project has no mockito-kotlin dependency, and `next()`/`skip()` never touch the repository
 * anyway, so a fake is both sufficient and avoids adding a new test dependency for two tests
 * (mirrors the pattern in `SettingsRepositoryTest`).
 */
class OnboardingViewModelTest {

    private fun fakeRepository(): SettingsRepository {
        val dir = File.createTempFile("onboarding-vm-test", "").apply { delete(); mkdirs() }
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsRepository.forTesting(store)
    }

    @Test
    fun `next advances slide index up to the last slide`() {
        val viewModel = OnboardingViewModel(fakeRepository())
        assertEquals(0, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(1, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(2, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(2, viewModel.slideIndex.value)
    }

    @Test
    fun `skip jumps straight to the last slide`() {
        val viewModel = OnboardingViewModel(fakeRepository())
        viewModel.skip()
        assertEquals(2, viewModel.slideIndex.value)
    }
}
