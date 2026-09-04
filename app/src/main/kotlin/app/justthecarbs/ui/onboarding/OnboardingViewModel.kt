package app.justthecarbs.ui.onboarding

import androidx.lifecycle.ViewModel
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** First-launch carousel state: which of the 3 slides is showing, and marking it seen on exit. */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _slideIndex = MutableStateFlow(0)
    val slideIndex: StateFlow<Int> = _slideIndex.asStateFlow()

    private val completeMutex = Mutex()
    private var completed = false

    fun next() {
        _slideIndex.value = (_slideIndex.value + 1).coerceAtMost(LAST_SLIDE)
    }

    /**
     * Follow a slide the user reached by swiping.
     *
     * Separate from [next] because a swipe can move in either direction and can land on any slide,
     * where [next] only ever advances by one. Coerced rather than trusted: the pager is the source
     * of the value and this keeps an out-of-range index from becoming state.
     */
    fun showSlide(index: Int) {
        _slideIndex.value = index.coerceIn(0, LAST_SLIDE)
    }

    fun skip() {
        _slideIndex.value = LAST_SLIDE
    }

    /**
     * Persists `hasSeenOnboarding = true` and returns once the write has landed, so a caller can
     * navigate only after completion is durable.
     *
     * A [Mutex] rather than a bare boolean check: two callers racing (a rapid double tap on *Get
     * started*) must not both start a DataStore edit, and the second caller must still get back a
     * `return` that means "the write has happened" rather than "someone else started it". The
     * mutex makes the second caller wait for the first's edit to finish rather than short-circuit
     * past it.
     */
    suspend fun complete() {
        completeMutex.withLock {
            if (completed) return
            settingsRepository.setHasSeenOnboarding(true)
            completed = true
        }
    }

    private companion object {
        const val LAST_SLIDE = 2
    }
}
