package app.carbscan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.settings.SettingsRepository
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
