package app.justthecarbs.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.justthecarbs.data.settings.SettingsRepository
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

    @Test
    fun `a swipe can move to any slide in either direction`() {
        // Unlike `next`, which only advances by one. The pager reports the slide the user landed
        // on, so backwards movement has to be expressible.
        val viewModel = OnboardingViewModel(fakeRepository())

        viewModel.showSlide(2)
        assertEquals(2, viewModel.slideIndex.value)

        viewModel.showSlide(0)
        assertEquals(0, viewModel.slideIndex.value)
    }

    @Test
    fun `a swipe cannot put an out-of-range slide into state`() {
        // The pager is the source of this value, so it is coerced rather than trusted — an index
        // past the end would crash the screen when it indexed SLIDES.
        val viewModel = OnboardingViewModel(fakeRepository())

        viewModel.showSlide(99)
        assertEquals(2, viewModel.slideIndex.value)

        viewModel.showSlide(-3)
        assertEquals(0, viewModel.slideIndex.value)
    }

    @Test
    fun `next still works after a swipe moved the slide`() {
        // The button and the gesture drive the same state, so they must compose: swiping back and
        // then tapping Next should advance from where the swipe left off, not from where the last
        // tap did.
        val viewModel = OnboardingViewModel(fakeRepository())

        viewModel.showSlide(2)
        viewModel.showSlide(0)
        viewModel.next()

        assertEquals(1, viewModel.slideIndex.value)
    }
}
