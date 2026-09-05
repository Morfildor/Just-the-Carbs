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

    sealed interface CompletionState {
        data object Idle : CompletionState
        data object Saving : CompletionState
        data class Failed(val message: String) : CompletionState
        data object Saved : CompletionState
    }

    private val _slideIndex = MutableStateFlow(0)
    val slideIndex: StateFlow<Int> = _slideIndex.asStateFlow()

    private val _completionState = MutableStateFlow<CompletionState>(CompletionState.Idle)
    val completionState: StateFlow<CompletionState> = _completionState.asStateFlow()

    private val completeMutex = Mutex()

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
     * Persists `hasSeenOnboarding = true` and updates [completionState] to reflect the outcome.
     *
     * A [Mutex] guards against two callers racing (a rapid double tap on *Get started*) starting
     * two DataStore edits. Unlike the previous design, a repository failure now sets
     * [CompletionState.Failed] rather than leaving the caller's own local "in progress" flag stuck
     * true forever -- the caller observes this state and re-enables its own UI on Failed. A retry
     * (calling [complete] again after a Failed state) is a normal, supported second attempt: the
     * mutex does not remember the previous failure, only whether a write is currently in flight or
     * has already durably succeeded.
     */
    suspend fun complete() {
        completeMutex.withLock {
            if (_completionState.value is CompletionState.Saved) return
            _completionState.value = CompletionState.Saving
            try {
                settingsRepository.setHasSeenOnboarding(true)
                _completionState.value = CompletionState.Saved
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _completionState.value = CompletionState.Failed(e.message ?: "Could not save")
            }
        }
    }

    private companion object {
        const val LAST_SLIDE = 2
    }
}
