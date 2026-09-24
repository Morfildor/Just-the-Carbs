package app.justthecarbs.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which of the two Data actions has just finished, for the screen's one-line confirmation. */
enum class ClearedData { RECENT_HISTORY, SAVED_PRODUCTS }

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val products: RoomProductDataSource,
) : ViewModel() {

    private val _cleared = MutableStateFlow<ClearedData?>(null)

    /**
     * Set only after a clear has **finished**, so the screen never confirms a deletion that has not
     * happened; consumed by [onClearedShown] once the confirmation has been shown.
     */
    val cleared: StateFlow<ClearedData?> = _cleared.asStateFlow()

    fun onClearedShown() {
        _cleared.value = null
    }

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
    fun clearRecents() = viewModelScope.launch {
        products.clearRecentHistory()
        _cleared.value = ClearedData.RECENT_HISTORY
    }.let {}

    /**
     * Deletes every saved product and everything derived from one: portion units and usual-portion
     * records included, so re-scanning a cleared barcode cannot resurrect its history.
     *
     * Settings and the in-progress meal are deliberately untouched — neither is saved product data.
     * `docs/privacy-policy.md` describes exactly this scope, and the two must be changed together.
     */
    fun clearProducts() = viewModelScope.launch {
        products.deleteAllProducts()
        _cleared.value = ClearedData.SAVED_PRODUCTS
    }.let {}
}
