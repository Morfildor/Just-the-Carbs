package app.carbscan.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.ProductRepository
import app.carbscan.domain.CarbCalculator
import app.carbscan.domain.CarbResult
import app.carbscan.domain.LookupError
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionParser
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ProductFetchResult
import app.carbscan.domain.VerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** What the calculator screen is showing. Immutable, one object, driven by a StateFlow (§65). */
data class ProductUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val portionText: String = "",
    val result: CarbResult? = null,
    val failure: Failure? = null,
    val barcode: String = "",
    /** True for a quick calculation that has not been saved as a product (§28). */
    val unsaved: Boolean = false,
    val showVerifyDialog: Boolean = false,
) {
    val canCalculate: Boolean get() = product != null
}

/** Every way the screen can fail to show a number, each with its own recovery (§13, §26, §36). */
sealed interface Failure {
    /** No such barcode anywhere. Offers scan-label / manual entry (§26). */
    data object NotFound : Failure

    /** A record exists but its carbohydrate value cannot be trusted (§13). */
    data object NoUsableValue : Failure

    /** Network or server problem. Retryable (§36). */
    data class Lookup(val error: LookupError) : Failure
}

/**
 * The calculator (§14, §16).
 *
 * The result recomputes on every keystroke — there is no Calculate button, and there is nothing to
 * save before a number appears (§16). Portion text is held in [SavedStateHandle] so a mid-edit
 * portion survives process death (§63).
 */
class ProductViewModel(
    private val repository: ProductRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductUiState())
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    init {
        // Record the portion from the state itself rather than from a "back was pressed" callback.
        //
        // The user leaves this screen in more ways than the back button: the system back gesture,
        // Home, the recents switcher, or the process being killed outright. A callback on one
        // button catches exactly one of those, so §20's "remember the last portion" quietly failed
        // for the most common gesture on the device.
        //
        // Debounced so that typing "250" records once, not once per digit.
        viewModelScope.launch {
            _state
                .map { it.portionText }
                .distinctUntilChanged()
                .debounce(PORTION_SETTLE_MS)
                .collect { rememberUsage() }
        }
    }

    /** Load a stored or remote product by barcode. */
    fun load(barcode: String) {
        if (_state.value.barcode == barcode && _state.value.product != null) return
        _state.update { it.copy(loading = true, barcode = barcode, failure = null) }

        viewModelScope.launch {
            when (val result = repository.lookup(barcode)) {
                is ProductFetchResult.Found -> onProductLoaded(result.product)
                is ProductFetchResult.NotFound ->
                    _state.update { it.copy(loading = false, failure = Failure.NotFound) }
                is ProductFetchResult.Unusable ->
                    _state.update { it.copy(loading = false, failure = Failure.NoUsableValue) }
                is ProductFetchResult.Failed ->
                    _state.update { it.copy(loading = false, failure = Failure.Lookup(result.error)) }
            }
        }
    }

    /** Start a quick calculation that is not stored anywhere yet (§28). */
    fun startQuickCalculation(name: String, carbsPer100: BigDecimal, basis: NutritionBasis) {
        val scratch = Product(
            barcode = "",
            name = name,
            carbsPer100 = carbsPer100,
            basis = basis,
            dataSource = ProductDataOrigin.MANUAL,
            verificationStatus = VerificationStatus.USER_VERIFIED,
        )
        _state.update { it.copy(loading = false, product = scratch, unsaved = true, failure = null) }
        recalculate()
    }

    private fun onProductLoaded(product: Product) {
        // Pre-fill the portion the user chose last time, so a repeat product needs no typing at
        // all (§20) — but only if they have not already started typing in this session.
        val restored = savedState.get<String>(KEY_PORTION)
        val prefilled = restored
            ?: product.lastPortion?.stripTrailingZeros()?.toPlainString()
            ?: ""

        _state.update {
            it.copy(loading = false, product = product, portionText = prefilled, failure = null)
        }
        recalculate()

        // Only ever a background refresh, never on the path to a result: the value is already on
        // screen by now (§10.2). A verified or user-authored product returns immediately.
        viewModelScope.launch {
            if (repository.refreshFromRemote(product.barcode)) {
                (repository.lookup(product.barcode) as? ProductFetchResult.Found)?.let { fresh ->
                    _state.update { it.copy(product = fresh.product) }
                    recalculate()
                }
            }
        }
    }

    fun onPortionChanged(text: String) {
        savedState[KEY_PORTION] = text
        _state.update { it.copy(portionText = text) }
        recalculate()
    }

    /** −10 / −5 / +5 / +10 (§16). Never goes below zero, which is not a portion. */
    fun adjustPortion(delta: Int) {
        val current = PortionParser.parse(_state.value.portionText) ?: BigDecimal.ZERO
        val adjusted = current.add(BigDecimal(delta)).max(BigDecimal.ZERO)
        onPortionChanged(adjusted.stripTrailingZeros().toPlainString())
    }

    fun setPortion(amount: BigDecimal) =
        onPortionChanged(amount.stripTrailingZeros().toPlainString())

    private fun recalculate() {
        val product = _state.value.product
        val portion = PortionParser.parse(_state.value.portionText)

        // No portion, no result. Showing a stale or zero number while the field is empty would be
        // showing a value the user did not ask for (§13).
        if (product == null || portion == null) {
            _state.update { it.copy(result = null) }
            return
        }

        _state.update {
            it.copy(
                result = CarbCalculator.calculate(
                    carbsPer100 = product.carbsPer100,
                    portion = portion,
                    basis = product.basis,
                ),
            )
        }
    }

    /** Remember the portion once the user has actually acted on the result (§20, §21). */
    fun rememberUsage() {
        val product = _state.value.product ?: return
        val portion = PortionParser.parse(_state.value.portionText) ?: return
        if (_state.value.unsaved || product.barcode.isEmpty()) return
        viewModelScope.launch { repository.recordUse(product.barcode, portion) }
    }

    fun toggleFavorite() {
        val product = _state.value.product ?: return
        if (product.barcode.isEmpty()) return
        val next = !product.favorite
        _state.update { it.copy(product = product.copy(favorite = next)) }
        viewModelScope.launch { repository.setFavorite(product.barcode, next) }
    }

    fun showVerifyDialog(show: Boolean) = _state.update { it.copy(showVerifyDialog = show) }

    fun confirmVerification(carbsPer100: BigDecimal, basis: NutritionBasis, name: String) {
        val product = _state.value.product ?: return
        viewModelScope.launch {
            repository.saveVerification(
                barcode = product.barcode,
                verifiedCarbsPer100 = carbsPer100,
                basis = basis,
                name = name.ifBlank { null },
            )
            (repository.lookup(product.barcode) as? ProductFetchResult.Found)?.let { updated ->
                _state.update { it.copy(product = updated.product, showVerifyDialog = false) }
                recalculate()
            }
        }
    }

    fun resetToOnlineValue() {
        val product = _state.value.product ?: return
        viewModelScope.launch {
            repository.resetToOnlineValue(product.barcode)
            (repository.lookup(product.barcode) as? ProductFetchResult.Found)?.let { updated ->
                _state.update { it.copy(product = updated.product) }
                recalculate()
            }
        }
    }

    private companion object {
        const val KEY_PORTION = "portion_text"

        /** Long enough to cover typing a three-digit portion, short enough to beat a fast exit. */
        const val PORTION_SETTLE_MS = 600L
    }
}
