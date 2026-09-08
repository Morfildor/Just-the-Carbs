package app.justthecarbs.ui.onboarding

import androidx.lifecycle.ViewModel
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Why the tutorial is on screen.
 *
 * The two modes share one screen and one step sequence and differ in exactly two ways: what leaving
 * it does, and whether finishing writes anything. Keeping that as a mode rather than a second screen
 * is what stops the app growing a parallel onboarding flow — see [OnboardingViewModel.finish].
 */
enum class TutorialMode {
    /** Taken from Home's reminder card. Leaving marks the tutorial seen and returns to Home. */
    FIRST_RUN,

    /** Replayed from Settings. Leaving writes nothing and returns to Settings. */
    REPLAY,
}

/**
 * The first-launch tutorial: which of [TUTORIAL_STEPS] is showing, and marking onboarding seen.
 *
 * `hasSeenTutorial` is the one flag this class writes, and a replay writes nothing at all — there is
 * nothing to remember about having watched the tutorial twice.
 *
 * It never touches `hasSeenOnboarding`, which belongs to the welcome carousel. The two record
 * different events: the carousel has been read, and the coach marks are done with. Collapsing them
 * would make finishing either one silence the other.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    val mode: TutorialMode = TutorialMode.FIRST_RUN,
) : ViewModel() {

    sealed interface CompletionState {
        data object Idle : CompletionState
        data object Saving : CompletionState
        data class Failed(val message: String) : CompletionState

        /**
         * The tutorial is finished and the screen may leave.
         *
         * In [TutorialMode.FIRST_RUN] this means the DataStore write landed. In
         * [TutorialMode.REPLAY] there is no write, so it means only "the user is done" — the state
         * exists in both modes so the screen has one exit signal to observe rather than two.
         */
        data object Saved : CompletionState
    }

    private val _stepIndex = MutableStateFlow(0)
    val stepIndex: StateFlow<Int> = _stepIndex.asStateFlow()

    private val _completionState = MutableStateFlow<CompletionState>(CompletionState.Idle)
    val completionState: StateFlow<CompletionState> = _completionState.asStateFlow()

    private val completeMutex = Mutex()

    fun next() {
        _stepIndex.value = (_stepIndex.value + 1).coerceAtMost(TUTORIAL_LAST_STEP)
    }

    /**
     * Jump to a step directly. Coerced rather than trusted, so an out-of-range index from a caller
     * cannot become state and crash the screen when it indexes [TUTORIAL_STEPS].
     */
    fun showStep(index: Int) {
        _stepIndex.value = index.coerceIn(0, TUTORIAL_LAST_STEP)
    }

    /**
     * Leave the tutorial: Skip, system Back, or the final action. All three end it the same way.
     *
     * Skip is not a lesser exit than finishing — someone who skips has decided they are done, and
     * showing them the tutorial again on the next launch would be the app disagreeing with them. So
     * every exit from [TutorialMode.FIRST_RUN] persists `hasSeenTutorial`, and the screen only
     * leaves once that write has actually landed.
     *
     * A [Mutex] guards against two callers racing (a rapid double tap on the final action) starting
     * two DataStore edits. A repository failure sets [CompletionState.Failed] rather than leaving
     * the caller stuck: the screen re-enables its action and stays put, so a failed write never
     * traps the user in the tutorial and never silently drops them onto Home as though it had
     * worked. Retrying after a failure is a normal second attempt — the mutex remembers only
     * whether a write is in flight or has already durably succeeded, not that one previously failed.
     */
    suspend fun finish() {
        // A replay must not touch the flag. It is not merely unnecessary: re-writing `true` over an
        // existing `true` is a pointless write, and writing it at all in a mode reachable *before*
        // the reminder has been answered would let watching the tutorial from Settings stand in for
        // having taken it up. Returning Saved directly also means a replay cannot fail to close.
        if (mode == TutorialMode.REPLAY) {
            _completionState.value = CompletionState.Saved
            return
        }

        completeMutex.withLock {
            if (_completionState.value is CompletionState.Saved) return
            _completionState.value = CompletionState.Saving
            try {
                settingsRepository.setHasSeenTutorial(true)
                _completionState.value = CompletionState.Saved
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _completionState.value = CompletionState.Failed(e.message ?: "Could not save")
            }
        }
    }
}
