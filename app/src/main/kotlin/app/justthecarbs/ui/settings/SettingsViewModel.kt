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

    /**
     * Forgets usage history — for every product, favourites included — and keeps the products
     * themselves, their verified values and the favourite flag (§43, §23).
     *
     * The exact list of what "usage" means is in [app.justthecarbs.data.local.ProductDao], and it is
     * five columns plus the whole `portion_usage` table, not the two columns this used to clear.
     */
    fun clearRecents() = viewModelScope.launch { products.clearRecentHistory() }.let {}

    /**
     * Deletes every saved product and everything derived from one: portion units and usual-portion
     * records included, so re-scanning a cleared barcode cannot resurrect its history.
     *
     * Settings and the in-progress meal are deliberately untouched — neither is saved product data.
     * `docs/privacy-policy.md` describes exactly this scope, and the two must be changed together.
     */
    fun clearProducts() = viewModelScope.launch { products.deleteAllProducts() }.let {}
}
