package app.carbscan.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.ProductRepository
import app.carbscan.domain.CarbResult
import app.carbscan.domain.MealItem
import app.carbscan.domain.MealTotal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealUiState(
    val items: List<MealItem> = emptyList(),
    val showClearConfirmation: Boolean = false,
) {
    /** Null while empty, so the screen shows its empty state rather than a `0.0 g` total. */
    val total: CarbResult? get() = if (items.isEmpty()) null else MealTotal.asResult(items)
}

/**
 * The meal-total screen (development-pass brief §10).
 *
 * Holds no calculation of its own. Every figure on this screen was computed by `CarbCalculator`
 * when its item was added, and the total is [MealTotal]'s sum of those unrounded values — so the
 * app still has exactly one carbohydrate formula, in one place.
 */
class MealViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _state = MutableStateFlow(MealUiState())
    val state: StateFlow<MealUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMealItems().collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
    }

    fun removeItem(item: MealItem) {
        viewModelScope.launch { repository.removeMealItem(item) }
    }

    fun showClearConfirmation(show: Boolean) = _state.update { it.copy(showClearConfirmation = show) }

    /**
     * Clearing is confirmed rather than immediate, and the confirmation says the items are not
     * saved anywhere — a user who assumes a meal is recoverable has misunderstood what this app
     * keeps, and the moment they might lose work is the honest place to say so.
     */
    fun clear() {
        viewModelScope.launch {
            repository.clearMeal()
            _state.update { it.copy(showClearConfirmation = false) }
        }
    }
}
