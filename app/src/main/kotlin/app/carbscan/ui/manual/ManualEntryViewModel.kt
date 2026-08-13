package app.carbscan.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.ProductRepository
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.NutritionValueValidator
import app.carbscan.domain.PortionParser
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class CarbsError { MALFORMED, OUT_OF_RANGE }

data class ManualEntryUiState(
    val barcode: String = "",
    val name: String = "",
    val carbsPer100: String = "",
    val basis: NutritionBasis = NutritionBasis.PER_100_G,
    val packageAmount: String = "",
    val nameError: Boolean = false,
    val carbsError: CarbsError? = null,
    val savedBarcode: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && PortionParser.parse(carbsPer100) != null
}

/**
 * Manual entry (§27) and quick calculation (§28).
 *
 * Validation runs through the same [NutritionValueValidator] that guards remote data, so a typo
 * that produces an impossible value is caught wherever it came from.
 */
class ManualEntryViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManualEntryUiState())
    val state: StateFlow<ManualEntryUiState> = _state.asStateFlow()

    /**
     * Pre-fill from wherever the user came here from.
     *
     * The barcode arrives from an unknown scan, so any product created here becomes associated with
     * it and the next scan resolves locally (§26). [ocrCarbs] and [ocrBasis] arrive from a label
     * reading the user has already confirmed (§29) — they are a starting point, still editable,
     * never a value that has been accepted on the user's behalf.
     */
    fun start(barcode: String?, ocrCarbs: String = "", ocrBasis: String = "") {
        if (_state.value.name.isNotEmpty() || _state.value.carbsPer100.isNotEmpty()) return
        _state.update {
            it.copy(
                barcode = barcode.orEmpty(),
                carbsPer100 = ocrCarbs,
                basis = NutritionBasis.entries.firstOrNull { basis -> basis.name == ocrBasis }
                    ?: it.basis,
            )
        }
    }

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, nameError = false) }

    fun onCarbsChanged(value: String) =
        _state.update { it.copy(carbsPer100 = value, carbsError = null) }

    fun onBasisChanged(basis: NutritionBasis) = _state.update { it.copy(basis = basis) }

    fun onPackageChanged(value: String) = _state.update { it.copy(packageAmount = value) }

    fun save() {
        val current = _state.value

        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }

        val carbs = PortionParser.parse(current.carbsPer100)
        if (carbs == null) {
            _state.update { it.copy(carbsError = CarbsError.MALFORMED) }
            return
        }
        // The same ceiling remote data has to clear. A hand-typed 482 is a slipped decimal point,
        // and accepting it would produce a tenfold-wrong result with total confidence (§13).
        if (NutritionValueValidator.validateCarbsPer100(carbs.toDouble(), current.basis) == null) {
            _state.update { it.copy(carbsError = CarbsError.OUT_OF_RANGE) }
            return
        }

        // A product without a barcode still needs a stable key so it can live in Recents. The
        // synthetic id is namespaced so it can never collide with a real GTIN (§28).
        val key = current.barcode.ifBlank { "local:${UUID.randomUUID()}" }

        viewModelScope.launch {
            repository.saveUserAuthoredProduct(
                Product(
                    barcode = key,
                    name = current.name.trim(),
                    carbsPer100 = carbs,
                    basis = current.basis,
                    dataSource = ProductDataOrigin.MANUAL,
                    packageAmount = PortionParser.parse(current.packageAmount),
                ),
                origin = ProductDataOrigin.MANUAL,
            )
            _state.update { it.copy(savedBarcode = key) }
        }
    }

    fun onNavigated() = _state.update { it.copy(savedBarcode = null) }
}
