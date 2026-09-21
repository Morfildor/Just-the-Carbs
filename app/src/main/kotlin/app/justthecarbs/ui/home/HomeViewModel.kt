package app.justthecarbs.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.RecentUseSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A recent product together with the countable unit it was last used with, if any
 * (countable-portions brief §12) — Recents reflects how the user actually thinks about the
 * product, not always its raw gram amount.
 */
data class RecentEntry(val product: Product, val lastUnit: PortionUnit?)

/**
 * A just-forgotten product, held for the life of one Undo Snackbar.
 *
 * Carries the product's name alongside the snapshot purely so the Snackbar can name what it
 * removed; [RecentUseSnapshot] itself deliberately holds only usage facts, and the product row it
 * belongs to is untouched and still has the authoritative name.
 */
data class ForgottenRecent(val name: String, val snapshot: RecentUseSnapshot)

/** Where one product's Quick Add is. Absent from the map means idle and tappable. */
enum class QuickAddStatus {
    /** The meal write has been issued and has not returned. */
    IN_FLIGHT,

    /** Landed; the card is showing its confirmation. Still not tappable — see [HomeViewModel.quickAdd]. */
    ADDED,
}

/** One-shot outcomes the screen reacts to once: a haptic for success, a message for failure. */
sealed interface QuickAddEvent {
    data class Added(val barcode: String) : QuickAddEvent
    data class Failed(val name: String) : QuickAddEvent
}

class HomeViewModel(private val repository: ProductRepository) : ViewModel() {

    /**
     * Recents come straight from the database, so home draws without waiting for anything (§7).
     * `WhileSubscribed` keeps the query alive briefly across a rotation instead of re-running it.
     *
     * One batch [ProductRepository.findPortionUnits] call per emission, not one [ProductRepository.findPortionUnit]
     * call per product (P1 §13) — the loop form issued one Room query per recent product on every
     * list change, up to [RECENTS_LIMIT] queries for a single screen draw.
     */
    val recents: StateFlow<List<RecentEntry>> = repository.observeRecents(RECENTS_LIMIT)
        .map { products ->
            val unitIds = products.mapNotNull { product ->
                product.lastSelectedPortionUnitId
                    ?.takeIf { product.lastInputMode == InputMode.PORTION_UNIT }
            }
            val unitsById = repository.findPortionUnits(unitIds).associateBy { it.id }
            products.map { product ->
                val lastUnit = product.lastSelectedPortionUnitId
                    ?.takeIf { product.lastInputMode == InputMode.PORTION_UNIT }
                    ?.let { unitsById[it] }
                RecentEntry(product, lastUnit)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The meal in progress, for Home's running-total bar (§10). Empty most of the time. */
    val mealItems: StateFlow<List<MealItem>> = repository.observeMealItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * The product whose usage was most recently forgotten, held only long enough to offer Undo.
     *
     * Exactly the shape [app.justthecarbs.ui.meal.MealUiState.lastRemoved] uses, and for the same
     * reasons: one deep, never persisted, dropped when the Snackbar goes. A list here would be an
     * undo *history*, and a history of removed products is a record of what the user has eaten —
     * the one thing this app does not keep (§22).
     */
    private val _lastForgotten = MutableStateFlow<ForgottenRecent?>(null)
    val lastForgotten: StateFlow<ForgottenRecent?> = _lastForgotten.asStateFlow()

    /**
     * Quick Add state per barcode. A map rather than one flag so a write in flight for one card
     * never locks the rest of Home — the user can add a second product while the first confirms.
     */
    private val _quickAdd = MutableStateFlow<Map<String, QuickAddStatus>>(emptyMap())
    val quickAdd: StateFlow<Map<String, QuickAddStatus>> = _quickAdd.asStateFlow()

    private val _quickAddEvents = Channel<QuickAddEvent>(Channel.BUFFERED)
    val quickAddEvents: Flow<QuickAddEvent> = _quickAddEvents.receiveAsFlow()

    /**
     * Add this product's remembered portion to the meal, exactly as the calculator would have added
     * it (§9), and record the use exactly as the calculator would have recorded it (§13, §20).
     *
     * [portionDescription] comes from the screen for the same reason it does on the Product screen:
     * unit words and plurals live in resources. It is the very string the card is showing, so the
     * meal line reads the way the thing the user tapped did.
     *
     * **Single execution.** A tap is refused while the product has *any* status, not only while the
     * write is in flight. The in-flight window alone protects nothing in practice: a Room insert
     * returns in milliseconds, well inside the gap between the two taps of an accidental double tap,
     * so a guard that released on completion would let the second tap add the item again. The card
     * therefore stays non-repeatable for its short confirmation ([CONFIRMATION_MS]) — which is also
     * exactly how long it says "Added" — and then becomes tappable again for a deliberate second
     * portion. The check-and-set happens synchronously on the main thread before anything suspends,
     * so two taps cannot both pass it.
     *
     * Ineligible products are refused here as well as hidden on screen: the plan is recomputed from
     * [entry] rather than trusted from the caller.
     */
    fun quickAdd(entry: RecentEntry, portionDescription: String) {
        val plan = quickAddPlan(entry.product, entry.lastUnit) ?: return
        val barcode = plan.barcode
        if (barcode in _quickAdd.value) return
        _quickAdd.update { it + (barcode to QuickAddStatus.IN_FLIGHT) }

        viewModelScope.launch {
            try {
                when (plan) {
                    is QuickAddPlan.DirectCarbs -> repository.addDirectCarbMealItem(
                        productBarcode = barcode,
                        displayName = plan.displayName,
                        portionDescription = portionDescription,
                        count = plan.count,
                        carbsPerUnit = plan.carbsPerUnit,
                        exactCarbs = plan.exactCarbs,
                    )

                    is QuickAddPlan.Weighed -> repository.addMealItem(
                        productBarcode = barcode,
                        displayName = plan.displayName,
                        portionDescription = portionDescription,
                        resolvedAmount = plan.resolvedAmount,
                        basis = plan.basis,
                        carbsPer100 = plan.carbsPer100,
                        exactCarbs = plan.exactCarbs,
                    )
                }
            } catch (e: CancellationException) {
                _quickAdd.update { it - barcode }
                throw e
            } catch (_: Exception) {
                _quickAdd.update { it - barcode }
                _quickAddEvents.trySend(QuickAddEvent.Failed(plan.displayName))
                return@launch
            }

            _quickAdd.update { it + (barcode to QuickAddStatus.ADDED) }
            _quickAddEvents.trySend(QuickAddEvent.Added(barcode))

            // The same usage write the calculator's *Add to meal* makes, so Recents order and the
            // *Usual* shortcuts behave identically whichever way the portion was added. Like there,
            // a history failure must not turn a committed meal line into a reported failure.
            try {
                repository.recordUse(
                    barcode = barcode,
                    // Null on the direct-carb path, which preserves any earlier weight (see
                    // `ProductRepository.recordUse`) — never the count.
                    portion = (plan as? QuickAddPlan.Weighed)?.resolvedAmount,
                    mode = plan.inputMode,
                    portionUnitId = plan.portionUnitId,
                    count = plan.count,
                    expectedBasis = plan.basis,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the meal line is what the user asked for, and it exists.
            }

            delay(CONFIRMATION_MS)
            _quickAdd.update { it - barcode }
        }
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch { repository.setFavorite(product.barcode, !product.favorite) }
    }

    /**
     * Forget one product's remembered use, keeping the product itself (§43, one barcode).
     *
     * Immediate and reversible rather than confirmed, which is the rule the meal's own removal
     * already follows: a modal costs the common case (the user meant it) more than it saves the rare
     * one (they did not), and an Undo covers the rare one better than a dialog does.
     *
     * No Snackbar when nothing was erased — see [RecentUseSnapshot.erasedAnything]. Offering "Undo"
     * for a no-op would be a claim that something happened.
     */
    fun forgetRecentUse(product: Product) {
        viewModelScope.launch {
            val snapshot = repository.forgetRecentUse(product.barcode) ?: return@launch
            if (!snapshot.erasedAnything) return@launch
            _lastForgotten.value = ForgottenRecent(product.displayName, snapshot)
        }
    }

    /** Put the last forgotten product's usage back, exactly as it was. */
    fun undoForgetRecentUse() {
        // Cleared first, so a second Undo tap cannot restore twice — the Snackbar can still be on
        // screen for a moment after its action fires. Same guard, same reason, as the meal's.
        val forgotten = _lastForgotten.getAndUpdate { null } ?: return
        viewModelScope.launch { repository.restoreRecentUse(forgotten.snapshot) }
    }

    /** The Snackbar has gone (dismissed, replaced or timed out); the removal is now final. */
    fun clearForgetUndo() {
        _lastForgotten.value = null
    }

    companion object {
        /** Enough to cover a normal shop without turning home into a scrolling history (§21). */
        private const val RECENTS_LIMIT = 25

        /**
         * How long a card says "Added" and refuses another tap. Long enough to be seen after the
         * thumb lifts and to absorb a slow accidental double tap; short enough that a deliberate
         * second portion of the same food is barely delayed.
         */
        const val CONFIRMATION_MS = 1_400L
    }
}
