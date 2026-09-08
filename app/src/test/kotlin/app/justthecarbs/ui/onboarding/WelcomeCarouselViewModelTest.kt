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
 * The welcome carousel's slide machine and its single write.
 *
 * Same fixture approach as [OnboardingViewModelTest]: a real temp-file DataStore rather than a mock,
 * because this project has no mocking dependency and the slide functions never touch the repository
 * anyway.
 */
class WelcomeCarouselViewModelTest {

    private fun fakeRepository(): SettingsRepository {
        val dir = File.createTempFile("welcome-vm-test", "").apply { delete(); mkdirs() }
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsRepository.forTesting(store)
    }

    /** A DataStore whose writes always fail, so the `Failed` branch is actually executed. */
    private fun failingRepository(): SettingsRepository {
        val dir = File.createTempFile("welcome-vm-fail", "").apply { delete(); mkdirs() }
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

    // ---- slide machine ------------------------------------------------------------------------

    @Test
    fun `next advances to the last slide and stops`() {
        val viewModel = WelcomeCarouselViewModel(fakeRepository())
        assertEquals(0, viewModel.slideIndex.value)
        repeat(10) { viewModel.next() }
        assertEquals("must not run past the last slide", 2, viewModel.slideIndex.value)
    }

    @Test
    fun `skip jumps to the last slide rather than leaving`() {
        // Skip is a jump, not an exit: the final slide's action is the one place the flag is
        // written, so a Skip that left directly would need its own copy of the write, the failure
        // reporting and the navigation.
        val viewModel = WelcomeCarouselViewModel(fakeRepository())
        viewModel.skip()
        assertEquals(2, viewModel.slideIndex.value)
        assertEquals(
            WelcomeCarouselViewModel.CompletionState.Idle,
            viewModel.completionState.value,
        )
    }

    @Test
    fun `an out of range slide from the pager is coerced rather than becoming state`() {
        val viewModel = WelcomeCarouselViewModel(fakeRepository())
        viewModel.showSlide(99)
        assertEquals(2, viewModel.slideIndex.value)
        viewModel.showSlide(-4)
        assertEquals(0, viewModel.slideIndex.value)
    }

    // ---- completion ---------------------------------------------------------------------------

    @Test
    fun `completing persists hasSeenOnboarding before returning`() = runTest {
        val repository = fakeRepository()
        val viewModel = WelcomeCarouselViewModel(repository)

        viewModel.complete()

        assertEquals(WelcomeCarouselViewModel.CompletionState.Saved, viewModel.completionState.value)
        assertTrue(repository.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `completing the carousel never marks the tutorial seen`() = runTest {
        // This is the invariant the whole two-flag model rests on. If the carousel also wrote
        // `hasSeenTutorial`, Home's reminder card would be retired before it had ever been shown and
        // a first-run user would get the carousel *or* the coach marks, never both.
        val repository = fakeRepository()

        WelcomeCarouselViewModel(repository).complete()

        assertEquals(false, repository.settings.first().hasSeenTutorial)
    }

    @Test
    fun `completing twice writes once`() = runTest {
        val dir = File.createTempFile("welcome-vm-count", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        val writes = AtomicInteger(0)
        val countingStore = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                writes.incrementAndGet()
                return real.updateData(transform)
            }
        }
        val viewModel = WelcomeCarouselViewModel(SettingsRepository.forTesting(countingStore))

        viewModel.complete()
        viewModel.complete()
        viewModel.complete()

        assertEquals("a settled completion must not rewrite the flag", 1, writes.get())
    }

    @Test
    fun `two concurrent completions both return only after the write lands`() = runTest {
        val repository = fakeRepository()
        val viewModel = WelcomeCarouselViewModel(repository)

        val first = async { viewModel.complete() }
        val second = async { viewModel.complete() }
        first.await()
        second.await()

        assertEquals(WelcomeCarouselViewModel.CompletionState.Saved, viewModel.completionState.value)
        assertTrue(repository.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `a failed write reports Failed rather than Saved`() = runTest {
        // A failure must leave the user on the carousel with a retryable action, never drop them
        // onto Home as though the flag had been written -- which would show the carousel again next
        // launch with no explanation.
        val viewModel = WelcomeCarouselViewModel(failingRepository())

        viewModel.complete()

        assertTrue(
            "expected Failed, was ${viewModel.completionState.value}",
            viewModel.completionState.value is WelcomeCarouselViewModel.CompletionState.Failed,
        )
    }

    @Test
    fun `retrying after a failure can still succeed`() = runTest {
        // The mutex remembers only whether a write is in flight or has durably succeeded, not that
        // one previously failed -- so a second tap after a transient disk error is a normal attempt.
        val dir = File.createTempFile("welcome-vm-flaky", "").apply { delete(); mkdirs() }
        val real: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        val attempts = AtomicInteger(0)
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                if (attempts.getAndIncrement() == 0) throw java.io.IOException("transient")
                return real.updateData(transform)
            }
        }
        val repository = SettingsRepository.forTesting(flaky)
        val viewModel = WelcomeCarouselViewModel(repository)

        viewModel.complete()
        assertTrue(viewModel.completionState.value is WelcomeCarouselViewModel.CompletionState.Failed)

        viewModel.complete()
        assertEquals(WelcomeCarouselViewModel.CompletionState.Saved, viewModel.completionState.value)
        assertTrue(repository.settings.first().hasSeenOnboarding)
    }
}
