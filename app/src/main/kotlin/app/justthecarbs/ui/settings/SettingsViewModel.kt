package app.justthecarbs.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val products: RoomProductDataSource,
) : ViewModel() {

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { settings.setTheme(choice) }.let {}

    fun setResultStyle(style: ResultStyle) =
        viewModelScope.launch { settings.setResultStyle(style) }.let {}

    fun setHaptics(enabled: Boolean) =
        viewModelScope.launch { settings.setHapticsEnabled(enabled) }.let {}

    /** Forgets usage history but keeps verified products and favourites (§43, §23). */
    fun clearRecents() = viewModelScope.launch { products.clearRecentHistory() }.let {}

    fun clearProducts() = viewModelScope.launch { products.deleteAllProducts() }.let {}
}
