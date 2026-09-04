package app.justthecarbs.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    fun `complete persists hasSeenOnboarding before returning`() = runTest {
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.complete()

        assertEquals(true, repository.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `a second call to complete after the first returns writes to the store only once`() = runTest {
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
        val viewModel = OnboardingViewModel(repository)

        viewModel.complete()
        viewModel.complete()
        viewModel.complete()

        assertEquals(1, writeCount.get())
        assertEquals(true, repository.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `two concurrent calls to complete both return only after the write lands`() = runTest {
        val repository = fakeRepository()
        val viewModel = OnboardingViewModel(repository)

        val firstCall = async { viewModel.complete() }
        val secondCall = async { viewModel.complete() }
        firstCall.await()
        secondCall.await()

        assertTrue(repository.settings.first().hasSeenOnboarding)
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
