package app.justthecarbs.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.editableAmount
import app.justthecarbs.domain.withPortion
import java.math.BigDecimal
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
    /** The line whose portion is being changed, while its dialog is open. */
    val editing: MealItem? = null,
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

    fun startEdit(item: MealItem) = _state.update { it.copy(editing = item) }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    /**
     * Resize the line being edited to [amount], described as [portionDescription] (built by the
     * screen, because unit words live in resources).
     *
     * The figure is recalculated from the line's own stored values by [withPortion], never from the
     * product, and nothing but the meal line is written: no usage history, no *Usual* aggregate and
     * no remembered portion, since this is a correction to a portion already counted once. An
     * unchanged amount writes nothing, so reopening and saving a counted line cannot replace its
     * "2 slices" wording. The dialog is closed before the write starts, so a second tap on Save has
     * nothing left to save.
     */
    fun saveEdit(amount: BigDecimal, portionDescription: String) {
        val editing = _state.value.editing ?: return
        val unchanged = editing.editableAmount?.let { it.compareTo(amount) == 0 } == true
        val updated = if (unchanged) null else editing.withPortion(amount, portionDescription)
        if (!unchanged && updated == null) return
        _state.update { it.copy(editing = null) }
        if (updated != null) viewModelScope.launch { repository.updateMealItem(updated) }
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
