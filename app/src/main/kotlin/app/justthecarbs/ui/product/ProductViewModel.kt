package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.data.RefreshOutcome
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LabelComparison
import app.justthecarbs.domain.LabelVerdict
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionResolver
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.VerificationStatus
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
    /**
     * A newer online value seen during this session (corrections #5, #10).
     *
     * Held as a *notice*, not applied. The product being calculated with is a fixed snapshot for
     * the life of the session.
     */
    val newerRemoteCarbs: BigDecimal? = null,
    // ---- countable portions (brief §2, §9-§12) ----------------------------------------------
    /** Frozen for the session once loaded — a background refresh never replaces this list. */
    val portionUnits: List<PortionUnit> = emptyList(),
    val inputMode: InputMode = InputMode.GRAMS,
    val selectedPortionUnitId: Long? = null,
    val countText: String = "",
    val showAddPortionUnitForm: Boolean = false,
    /** Same immutability rule as [newerRemoteCarbs], scoped to the unit currently in use (§9). */
    val newerRemotePortionUnitAmount: BigDecimal? = null,
    /** The inline "1 slice = [36] g" correction form is open (development-pass brief §3.3). */
    val correctingPortionUnit: Boolean = false,
    // ---- temporary meal (development-pass brief §7-§10) --------------------------------------
    /** The current meal, live. Adding from this screen is what puts items here (§8). */
    val mealItems: List<MealItem> = emptyList(),
    /** Set for one collection after *Add & scan next*, so the screen knows to move on (§11). */
    val addedToMeal: Boolean = false,
    /**
     * A label reading waiting to be compared against the current value (§12).
     *
     * Held as a *verdict*, never applied. Same rule as [newerRemoteCarbs]: the calculator's figure
     * does not move until the user says so — and unlike a background refresh, that holds here even
     * when the two figures agree, because agreement is still a claim only the user can make.
     */
    val labelVerdict: LabelVerdict? = null,
    /**
     * Portions this product is usually eaten in (§13). Empty until a pattern exists, which is most
     * of the time — a shortcut offered after one use would turn "I once weighed 63 g" into a
     * standing recommendation.
     */
    val usualPortions: List<PortionUsage> = emptyList(),
) {
    val canCalculate: Boolean get() = product != null
    val selectedPortionUnit: PortionUnit? get() = portionUnits.firstOrNull { it.id == selectedPortionUnitId }

    /** Running total of the meal, or null when the meal is empty and the bar should not show. */
    val mealTotal: CarbResult? get() = if (mealItems.isEmpty()) null else MealTotal.asResult(mealItems)
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

        // The meal is shared state, not session state: another screen can clear it while this one
        // is open, and the bar must reflect that. Unlike the product's carbs, nothing here feeds a
        // calculation, so observing it live cannot violate session immutability (§9).
        viewModelScope.launch {
            repository.observeMealItems().collect { items ->
                _state.update { it.copy(mealItems = items) }
            }
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
        val restoredPortion = savedState.get<String>(KEY_PORTION)
        val restoredCount = savedState.get<String>(KEY_COUNT)
        val restoredMode = savedState.get<String>(KEY_MODE)?.let(InputMode::valueOf)
        val restoredSelectedId = savedState.get<Long>(KEY_SELECTED_UNIT)

        viewModelScope.launch {
            // Portion units are fetched ONCE here, never re-subscribed to during the session — the
            // same immutability discipline as the product's own carbs (§9). A background refresh
            // below can only produce a notice, never replace this list.
            val units = repository.findPortionUnits(product.barcode)

            val mode = restoredMode ?: product.lastInputMode ?: InputMode.GRAMS
            val candidateSelectedId = restoredSelectedId ?: product.lastSelectedPortionUnitId
            val resolvedMode = if (mode == InputMode.PORTION_UNIT && units.none { it.id == candidateSelectedId }) {
                InputMode.GRAMS
            } else {
                mode
            }
            val resolvedSelectedId = if (resolvedMode == InputMode.PORTION_UNIT) candidateSelectedId else null
            val countText = restoredCount
                ?: product.lastCount?.takeIf { resolvedMode == InputMode.PORTION_UNIT }?.stripTrailingZeros()?.toPlainString()
                ?: ""

            val portionText = if (resolvedMode == InputMode.PORTION_UNIT && resolvedSelectedId != null) {
                val unit = units.first { it.id == resolvedSelectedId }
                val count = PortionParser.parse(countText) ?: BigDecimal.ONE
                PortionResolver.resolve(count, unit.amountPerUnit).stripTrailingZeros().toPlainString()
            } else {
                restoredPortion
                    ?: product.lastPortion?.stripTrailingZeros()?.toPlainString()
                    ?: ""
            }

            _state.update {
                it.copy(
                    loading = false,
                    product = product,
                    portionText = portionText,
                    failure = null,
                    portionUnits = units,
                    inputMode = resolvedMode,
                    selectedPortionUnitId = resolvedSelectedId,
                    countText = countText,
                    usualPortions = repository.usualPortions(product.barcode),
                )
            }
            recalculate()

            // Background refresh only, never on the path to a result: the value is already on screen
            // by now (§10.2).
            //
            // CALCULATION-SESSION IMMUTABILITY (correction #5). The refresh must never replace the
            // product this session is calculating with. Otherwise: the screen opens on 48.2, the user
            // types 65, a refresh returns 51.0, and the answer changes under their hand while they are
            // reading it. The newer figure is offered as a notice the user can accept. Portion units
            // follow the exact same rule (§9).
            when (val outcome = repository.refreshFromRemote(product.barcode)) {
                is RefreshOutcome.RemoteDiffers ->
                    _state.update { it.copy(newerRemoteCarbs = outcome.latestRemoteCarbs) }
                RefreshOutcome.Unchanged -> Unit
            }

            val frozenSelected = units.firstOrNull { it.id == resolvedSelectedId }
            if (frozenSelected != null) {
                val refreshed = repository.findPortionUnits(product.barcode)
                    .firstOrNull { it.id == frozenSelected.id }
                val latest = refreshed?.latestRemoteAmountPerUnit
                if (latest != null && latest.compareTo(frozenSelected.amountPerUnit) != 0) {
                    _state.update { it.copy(newerRemotePortionUnitAmount = latest) }
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

    // ---- countable portions (brief §9-§12) -----------------------------------------------------

    /** Immediate, per §12: the portion field keeps whatever grams it last resolved to. */
    fun switchToGrams() {
        savedState[KEY_MODE] = InputMode.GRAMS.name
        savedState.remove<Long>(KEY_SELECTED_UNIT)
        _state.update { it.copy(inputMode = InputMode.GRAMS, selectedPortionUnitId = null) }
    }

    fun switchToPortionUnit(unitId: Long) {
        val unit = _state.value.portionUnits.firstOrNull { it.id == unitId } ?: return
        val countText = _state.value.countText.ifBlank { "1" }
        savedState[KEY_MODE] = InputMode.PORTION_UNIT.name
        savedState[KEY_SELECTED_UNIT] = unitId
        savedState[KEY_COUNT] = countText
        _state.update {
            it.copy(inputMode = InputMode.PORTION_UNIT, selectedPortionUnitId = unitId, countText = countText)
        }
        recalculateFromCount(unit, countText)
    }

    fun onCountChanged(text: String) {
        savedState[KEY_COUNT] = text
        _state.update { it.copy(countText = text) }
        val unit = _state.value.selectedPortionUnit ?: return
        recalculateFromCount(unit, text)
    }

    private fun recalculateFromCount(unit: PortionUnit, countText: String) {
        val count = PortionParser.parse(countText)
        if (count == null) {
            _state.update { it.copy(result = null) }
            return
        }
        val resolved = PortionResolver.resolve(count, unit.amountPerUnit)
        savedState[KEY_PORTION] = resolved.stripTrailingZeros().toPlainString()
        _state.update { it.copy(portionText = resolved.stripTrailingZeros().toPlainString()) }
        recalculate()
    }

    fun showAddPortionUnitForm(show: Boolean) = _state.update { it.copy(showAddPortionUnitForm = show) }

    /** A unit the user defines themselves (§6). Always saved as verified — they read their own scale. */
    fun addPortionUnit(kind: PortionUnitKind, amountPerUnit: BigDecimal, customLabel: String?) {
        val product = _state.value.product ?: return
        if (product.barcode.isEmpty() || amountPerUnit.signum() <= 0) return
        viewModelScope.launch {
            val saved = repository.saveUserPortionUnit(
                barcode = product.barcode,
                kind = kind,
                amountPerUnit = amountPerUnit,
                basis = product.basis,
                customLabel = customLabel,
            )
            _state.update { it.copy(portionUnits = it.portionUnits + saved, showAddPortionUnitForm = false) }
            switchToPortionUnit(saved.id)
        }
    }

    /**
     * The user tapped *Online portion* / *Edit* — open the inline correction form (§3.3).
     *
     * Opening the form is deliberately NOT the same as verifying. Previously this tap verified the
     * remote weight as-is, which quietly asserted "the package agrees" on behalf of a user who had
     * not yet looked. Now the tap asks, and [correctSelectedPortionUnit] answers.
     */
    fun verifySelectedPortionUnit() {
        if (_state.value.selectedPortionUnit == null) return
        _state.update { it.copy(correctingPortionUnit = true) }
    }

    fun cancelPortionUnitCorrection() = _state.update { it.copy(correctingPortionUnit = false) }

    /**
     * The user confirmed the per-unit weight against the package, possibly correcting it (§3.3).
     *
     * Routes to `verifyPortionUnit(unitId, confirmedAmountPerUnit)`, so a corrected weight keeps the
     * unit's Open Food Facts provenance and its `originalRemoteAmountPerUnit` — it does not become a
     * second, competing user-defined unit.
     */
    fun correctSelectedPortionUnit(confirmedAmountPerUnit: BigDecimal) {
        val unit = _state.value.selectedPortionUnit ?: return
        if (confirmedAmountPerUnit.signum() <= 0) return
        viewModelScope.launch {
            val verified = repository.verifyPortionUnit(unit.id, confirmedAmountPerUnit)
            _state.update { st ->
                st.copy(
                    portionUnits = st.portionUnits.map { if (it.id == verified.id) verified else it },
                    correctingPortionUnit = false,
                )
            }
            // The corrected weight is a deliberate user action, so unlike a background refresh it
            // *should* move the open session's result (§9 protects against surprise, not intent).
            if (_state.value.selectedPortionUnitId == verified.id) {
                recalculateFromCount(verified, _state.value.countText)
            }
        }
    }

    /** Deliberate acceptance of a newer remote weight; only then does the session change (§9). */
    fun applyNewerRemotePortionUnit() {
        val unit = _state.value.selectedPortionUnit ?: return
        viewModelScope.launch {
            val applied = repository.applyLatestRemotePortionUnit(unit.id)
            _state.update { st ->
                st.copy(
                    portionUnits = st.portionUnits.map { if (it.id == applied.id) applied else it },
                    newerRemotePortionUnitAmount = null,
                )
            }
            if (_state.value.selectedPortionUnitId == applied.id) {
                recalculateFromCount(applied, _state.value.countText)
            }
        }
    }

    fun dismissNewerRemotePortionUnit() = _state.update { it.copy(newerRemotePortionUnitAmount = null) }

    // ---- temporary meal (development-pass brief §7-§11) ----------------------------------------

    /**
     * Add the calculation currently on screen to the meal (§9).
     *
     * [portionDescription] comes from the UI in the user's own words — "2 slices", "½ pack", "200
     * ml" — because pluralised unit names live in resources and only a composable can read them.
     * The ViewModel supplies the numbers; the screen supplies the wording.
     *
     * The stored carbohydrate figure is [ProductUiState.result]'s exact value: the number the user
     * is looking at as they tap. Nothing is recomputed here, so the meal cannot disagree with the
     * screen it was added from.
     */
    fun addCurrentToMeal(portionDescription: String) {
        val product = _state.value.product ?: return
        val result = _state.value.result ?: return
        val resolved = PortionParser.parse(_state.value.portionText) ?: return
        viewModelScope.launch {
            repository.addMealItem(
                productBarcode = product.barcode.takeIf { !_state.value.unsaved },
                displayName = product.name,
                portionDescription = portionDescription,
                resolvedAmount = resolved,
                basis = product.basis,
                carbsPer100 = product.carbsPer100,
                exactCarbs = result.exact,
            )
            // Adding to a meal is the strongest possible signal that this portion is real — stronger
            // than the debounced typing signal — so it counts towards *Usual* too (§13).
            rememberUsage()
            _state.update { it.copy(addedToMeal = true) }
        }
    }

    /** Consumed by the screen once it has acted on [ProductUiState.addedToMeal]. */
    fun consumeAddedToMeal() = _state.update { it.copy(addedToMeal = false) }

    fun removeMealItem(item: MealItem) {
        viewModelScope.launch { repository.removeMealItem(item) }
    }

    fun clearMeal() {
        viewModelScope.launch { repository.clearMeal() }
    }

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

    /** Remember the portion once the user has actually acted on the result (§20, §21, §11-§12). */
    fun rememberUsage() {
        val product = _state.value.product ?: return
        val portion = PortionParser.parse(_state.value.portionText) ?: return
        if (_state.value.unsaved || product.barcode.isEmpty()) return
        val mode = _state.value.inputMode
        val count = if (mode == InputMode.PORTION_UNIT) PortionParser.parse(_state.value.countText) else null
        viewModelScope.launch {
            repository.recordUse(
                product.barcode,
                portion,
                mode = mode,
                portionUnitId = _state.value.selectedPortionUnitId,
                count = count,
            )
        }
    }

    fun toggleFavorite() {
        val product = _state.value.product ?: return
        if (product.barcode.isEmpty()) return
        val next = !product.favorite
        _state.update { it.copy(product = product.copy(favorite = next)) }
        viewModelScope.launch { repository.setFavorite(product.barcode, next) }
    }

    /** The user explicitly accepts the newer online value; only then does the session change. */
    fun applyNewerRemoteValue() {
        val product = _state.value.product ?: return
        viewModelScope.launch {
            repository.applyLatestRemoteValue(product.barcode)
            (repository.lookup(product.barcode) as? ProductFetchResult.Found)?.let { updated ->
                _state.update { it.copy(product = updated.product, newerRemoteCarbs = null) }
                recalculate()
            }
        }
    }

    /** Dismissing keeps the session exactly as it is; the newer value stays recorded locally. */
    fun dismissNewerRemoteValue() = _state.update { it.copy(newerRemoteCarbs = null) }

    // ---- usual portions (development-pass brief §13) --------------------------------------------

    /**
     * The user tapped a *Usual* shortcut (§13).
     *
     * Only ever reached from a deliberate tap. Nothing here runs on load, so a suggestion is never
     * pre-applied — the app offers, the user chooses, and until they do the portion field is
     * whatever it was.
     *
     * A countable variant restores both the unit and the count, so tapping "2 slices" puts the
     * calculator back in countable mode rather than filling 72 g into a grams field and losing what
     * the number meant.
     */
    fun applyUsualPortion(usage: PortionUsage) {
        if (usage.inputMode == InputMode.PORTION_UNIT && usage.portionUnitId != null) {
            val unit = _state.value.portionUnits.firstOrNull { it.id == usage.portionUnitId }
            if (unit != null) {
                savedState[KEY_MODE] = InputMode.PORTION_UNIT.name
                savedState[KEY_SELECTED_UNIT] = unit.id
                val countText = usage.amount.stripTrailingZeros().toPlainString()
                savedState[KEY_COUNT] = countText
                _state.update {
                    it.copy(
                        inputMode = InputMode.PORTION_UNIT,
                        selectedPortionUnitId = unit.id,
                        countText = countText,
                    )
                }
                recalculateFromCount(unit, countText)
                return
            }
            // The unit was deleted since the usage was recorded. Falling back to grams here would
            // silently reinterpret a count as a weight, so the tap does nothing instead.
            return
        }
        switchToGrams()
        setPortion(usage.amount)
    }

    // ---- integrated label verification (development-pass brief §12) -----------------------------

    /**
     * A nutrition label was read by the camera. Compare it; do not apply it.
     *
     * The verdict goes into state and the calculator's figure stays exactly as it was. Even
     * [LabelVerdict.Match] takes this path — there is deliberately no branch here that writes a
     * value, so no future edit can accidentally make a camera frame self-accepting.
     */
    fun onLabelDetected(detected: BigDecimal, detectedBasis: NutritionBasis) {
        val product = _state.value.product ?: return
        _state.update {
            it.copy(
                labelVerdict = LabelComparison.compare(
                    current = product.carbsPer100,
                    currentBasis = product.basis,
                    detected = detected,
                    detectedBasis = detectedBasis,
                ),
            )
        }
    }

    /**
     * The user confirmed the package agrees with the value already in use (§12).
     *
     * This writes no new number — the figures are equal — but it *is* a verification: the user has
     * now read the package, which is precisely what [VerificationStatus.USER_VERIFIED] records.
     * Provenance is untouched, as always.
     */
    fun confirmLabelMatch() {
        val product = _state.value.product ?: return
        val verdict = _state.value.labelVerdict as? LabelVerdict.Match ?: return
        viewModelScope.launch {
            repository.saveVerification(
                barcode = product.barcode,
                verifiedCarbsPer100 = verdict.current,
                basis = product.basis,
            )
            reloadAfterVerification()
        }
    }

    /** The user chose the package's figure over the one on screen (§12). Their tap, their call. */
    fun useDetectedLabelValue(detected: BigDecimal) {
        val product = _state.value.product ?: return
        viewModelScope.launch {
            repository.saveVerification(
                barcode = product.barcode,
                verifiedCarbsPer100 = detected,
                basis = product.basis,
            )
            reloadAfterVerification()
        }
    }

    /** Dismissing changes nothing: not the value, not the verification status, not the session. */
    fun dismissLabelVerdict() = _state.update { it.copy(labelVerdict = null) }

    private suspend fun reloadAfterVerification() {
        val product = _state.value.product ?: return
        (repository.lookup(product.barcode) as? ProductFetchResult.Found)?.let { updated ->
            _state.update { it.copy(product = updated.product, labelVerdict = null) }
            recalculate()
        }
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
        const val KEY_COUNT = "count_text"
        const val KEY_MODE = "input_mode"
        const val KEY_SELECTED_UNIT = "selected_portion_unit_id"

        /** Long enough to cover typing a three-digit portion, short enough to beat a fast exit. */
        const val PORTION_SETTLE_MS = 600L
    }
}
