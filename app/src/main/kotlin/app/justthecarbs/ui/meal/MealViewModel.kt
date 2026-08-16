package app.justthecarbs.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealTotal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealUiState(
    val items: List<MealItem> = emptyList(),
    val showClearConfirmation: Boolean = false,
    /**
     * The item most recently removed, held only long enough to offer Undo. Not a history: exactly
     * one item deep, cleared when the Snackbar goes, and never persisted.
     */
    val lastRemoved: MealItem? = null,
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

    /**
     * Remove immediately and keep the snapshot so it can be put back (§5.3).
     *
     * No confirmation dialog: removal is instant and reversible, which is faster than a modal for
     * the common case (the user meant it) and safer for the rare one (they did not). The snapshot
     * held here is the removed item exactly as it was — restoring it never recomputes carbs.
     */
    fun removeItem(item: MealItem) {
        viewModelScope.launch {
            repository.removeMealItem(item)
            _state.update { it.copy(lastRemoved = item) }
        }
    }

    /** Put the last removed item back, exactly as it was. */
    fun undoRemove() {
        val removed = _state.value.lastRemoved ?: return
        // Cleared first so a second Undo tap cannot insert the same line twice — the Snackbar can
        // still be on screen for a moment after the action fires.
        _state.update { it.copy(lastRemoved = null) }
        viewModelScope.launch { repository.restoreMealItem(removed) }
    }

    /** The Snackbar has gone (dismissed or timed out); the removal is now final. */
    fun clearUndo() = _state.update { it.copy(lastRemoved = null) }

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
