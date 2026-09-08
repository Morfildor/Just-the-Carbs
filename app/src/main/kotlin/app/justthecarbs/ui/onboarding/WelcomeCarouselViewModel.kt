package app.justthecarbs.ui.onboarding

import androidx.lifecycle.ViewModel
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The welcome carousel: which of the three slides is showing, and marking the carousel seen on exit.
 *
 * Writes `hasSeenOnboarding` and nothing else. It deliberately does **not** touch `hasSeenTutorial`:
 * having read three slides is not the same as having been shown where the controls are, and setting
 * both here would retire Home's tutorial offer before it had ever appeared — which would mean a
 * first-run user got this screen *or* the coach marks, never both.
 */
class WelcomeCarouselViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

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

    /**
     * Skip to the final slide.
     *
     * Skip does not leave the carousel — it jumps to the end, where *Get started* is the one action
     * that both persists the flag and moves on. One exit, one write, one place a failure can be
     * reported; a Skip that left directly would be a second exit path needing its own copy of all
     * three.
     */
    fun skip() {
        _slideIndex.value = LAST_SLIDE
    }

    /**
     * Persists `hasSeenOnboarding = true` and updates [completionState] to reflect the outcome.
     *
     * A [Mutex] guards against two callers racing (a rapid double tap on *Get started*) starting two
     * DataStore edits. A repository failure sets [CompletionState.Failed] rather than leaving the
     * caller stuck: the screen re-enables its action and stays put, so a failed write never traps
     * the user in the carousel and never silently drops them onto Home as though it had worked.
     * Retrying after a failure is a normal second attempt — the mutex remembers only whether a write
     * is in flight or has already durably succeeded, not that one previously failed.
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
