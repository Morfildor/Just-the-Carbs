package app.justthecarbs.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** First-launch carousel state: which of the 3 slides is showing, and marking it seen on exit. */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _slideIndex = MutableStateFlow(0)
    val slideIndex: StateFlow<Int> = _slideIndex.asStateFlow()

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

    fun complete() {
        viewModelScope.launch { settingsRepository.setHasSeenOnboarding(true) }
    }

    private companion object {
        const val LAST_SLIDE = 2
    }
}
