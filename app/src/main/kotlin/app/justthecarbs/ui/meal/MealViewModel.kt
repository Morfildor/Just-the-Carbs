package app.justthecarbs.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemCorrection
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.PortionAdjustment
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.ResultFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class MealUiState(
    val items: List<MealItem> = emptyList(),
    val showClearConfirmation: Boolean = false,
    /**
     * The item most recently removed, held only long enough to offer Undo. Not a history: exactly
     * one item deep, cleared when the Snackbar goes, and never persisted.
     */
    val lastRemoved: MealItem? = null,
    /** The line being corrected while its editor is open; null the rest of the time. */
    val editor: MealItemEditor? = null,
) {
    /** Null while empty, so the screen shows its empty state rather than a `0.0 g` total. */
    val total: CarbResult? get() = if (items.isEmpty()) null else MealTotal.asResult(items)
}

/** Where a correction is. Only [SAVED] ends it; [FAILED] keeps the editor open for another try. */
enum class MealEditStatus { EDITING, SAVING, FAILED, SAVED }

/**
 * One meal line being corrected (1.0.8).
 *
 * [item] is the line exactly as it was when the editor opened: the snapshot every live figure is
 * recalculated from, and what closing without saving leaves in place. [amountText] is the field.
 */
data class MealItemEditor(
    val item: MealItem,
    val amountText: String,
    val status: MealEditStatus = MealEditStatus.EDITING,
) {
    /** The typed amount, or null while it is empty, malformed, zero or negative. */
    val amount: BigDecimal? get() = PortionParser.parse(amountText)?.takeIf { it.signum() > 0 }

    /** The carbohydrate [amount] of this line holds, from the line's own snapshot. */
    val exactCarbs: BigDecimal? get() = amount?.let { MealItemCorrection.exactCarbs(item, it) }

    /**
     * Typed text that is not a usable amount. An empty field is unfinished rather than wrong, so it
     * earns no message — the result slot simply asks for an amount.
     */
    val invalid: Boolean get() = amountText.isNotBlank() && amount == null

    /** Offered only for a real correction, and never while its write is in flight or done. */
    val canSave: Boolean
        get() = status != MealEditStatus.SAVING && status != MealEditStatus.SAVED &&
            MealItemCorrection.isCorrection(item, amount)
}

/**
 * The meal-total screen (development-pass brief §10).
 *
 * Holds no calculation of its own. Every figure on this screen was computed by `CarbCalculator`
 * when its item was added, and the total is [MealTotal]'s sum of those unrounded values — so the
 * app still has exactly one carbohydrate formula, in one place. A correction recalculates through
 * [MealItemCorrection], which hands the arithmetic to those same calculators.
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

    /**
     * Open the editor on one line, at the quantity the line actually stores (see
     * [MealItemCorrection.amountOf]). Written with every digit and the device's decimal separator,
     * so the field holds exactly the amount the line was calculated with.
     */
    fun editItem(item: MealItem) {
        val text = ResultFormatter.editable(MealItemCorrection.amountOf(item))
        _state.update { it.copy(editor = MealItemEditor(item = item, amountText = text)) }
    }

    /** A keystroke. Ignored once a write has started, so the field cannot drift from what is saved. */
    fun onEditAmountChange(text: String) = _state.update { state ->
        val editor = state.editor
        if (editor == null || editor.status == MealEditStatus.SAVING || editor.status == MealEditStatus.SAVED) {
            state
        } else {
            // Typing again after a failed write is a new attempt, so the failure stops being shown.
            state.copy(editor = editor.copy(amountText = text, status = MealEditStatus.EDITING))
        }
    }

    /**
     * One of the quick-adjust rail's accelerators, applied to the amount being corrected (1.0.8).
     *
     * Routed through [onEditAmountChange] rather than writing the state itself, so a rail tap is
     * governed by exactly the rules a keystroke is: ignored once a write has started, and clearing
     * a previous failure because it is a new attempt. The arithmetic is [PortionAdjustment]'s, the
     * same object the product calculator's two fields use — the two surfaces cannot disagree about
     * what halving does, because there is only one implementation of it.
     */
    fun adjustEditAmount(operation: PortionAdjustment.Operation) {
        val editor = _state.value.editor ?: return
        val current = PortionParser.parse(editor.amountText)
        onEditAmountChange(ResultFormatter.editable(PortionAdjustment.apply(current, operation)))
    }

    /**
     * Replace the line with the same line at [amount] (1.0.8).
     *
     * [amount] and [portionDescription] come from the screen together, from the one state it drew,
     * so the words written can never describe a different number from the one saved. The wording is
     * the screen's because it lives in resources.
     *
     * **One write, in place.** [ProductRepository.updateMealItem] rewrites the row under its own id,
     * so the old and new lines never count together — not even for a frame, as an add-then-remove
     * would. Nothing else is written: this corrects the meal the user is building, it is not another
     * use of the product, so Recents and the *Usual* shortcuts are left exactly as they were.
     *
     * Refused without writing when the amount is not a correction (zero, negative, unchanged) or a
     * write is already in flight — the status is set before anything suspends, so a double tap cannot
     * start two.
     */
    fun saveEdit(amount: BigDecimal, portionDescription: String) {
        val editor = _state.value.editor ?: return
        if (editor.status == MealEditStatus.SAVING || editor.status == MealEditStatus.SAVED) return
        if (!MealItemCorrection.isCorrection(editor.item, amount)) return

        val replacement = MealItemCorrection.corrected(editor.item, amount, portionDescription)
        _state.update { it.copy(editor = editor.copy(status = MealEditStatus.SAVING)) }

        viewModelScope.launch {
            val outcome = try {
                repository.updateMealItem(replacement)
                MealEditStatus.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The line is untouched and the editor stays open with what was typed, so a failed
                // write costs one more tap rather than the correction.
                MealEditStatus.FAILED
            }
            _state.update { state ->
                val current = state.editor
                // Only the editor that started this write hears how it ended. One closed meanwhile
                // is gone; one reopened meanwhile is a new edit that never pressed Save.
                if (current?.item?.id == editor.item.id && current.status == MealEditStatus.SAVING) {
                    state.copy(editor = current.copy(status = outcome))
                } else {
                    state
                }
            }
        }
    }

    /** Close the editor. Before a save this changes nothing; after one, the line is already written. */
    fun closeEditor() = _state.update { it.copy(editor = null) }
}
