package app.justthecarbs.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.data.RefreshOutcome
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.CarbResult
import app.justthecarbs.domain.DirectCarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LabelComparison
import app.justthecarbs.domain.LabelVerdict
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionResolver
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.UnusableReason
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

/** What the calculator screen is showing. Immutable, one object, driven by a StateFlow (§65). */
data class ProductUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val portionText: String = "",
    val result: CarbResult? = null,
    /**
     * The total for a direct-carb countable portion (spec §14).
     *
     * Separate from [result] rather than folded into it: [CarbResult] carries a non-null
     * [NutritionBasis] meaning "per 100 g/ml", which is a claim this path cannot make — it never
     * knew a weight. At most one of the two is non-null at any time.
     */
    val directCarbResult: BigDecimal? = null,
    val failure: Failure? = null,
    val barcode: String = "",
    /** True for a quick calculation that has not been saved as a product (§28). */
    val unsaved: Boolean = false,
    /** The *Save product* form is open (1.0.3 P1). Only ever reachable while [unsaved]. */
    val showSaveQuickCalculationForm: Boolean = false,
    /**
     * A save is in flight.
     *
     * The write is asynchronous, so without this the button stays enabled across the round trip and a
     * second tap starts a second write under a second synthetic key — two rows for one product.
     */
    val savingQuickCalculation: Boolean = false,
    /** The user tried to save without a name — the one field saving genuinely requires. */
    val quickSaveNameError: Boolean = false,
    /**
     * The save failed and nothing was written.
     *
     * Said out loud rather than swallowed: the calculation is still on screen and still correct, so
     * silence here reads as success and the user would leave believing the product was kept.
     */
    val quickSaveFailed: Boolean = false,
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
    val newerRemotePortionUnit: PortionConversion? = null,
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

    /**
     * The carbohydrate figure to display, whichever path produced it (correction pass §1).
     *
     * The screen needs one exact [BigDecimal], not a [CarbResult]: a direct-carb portion has a real
     * answer but no per-100 basis, so it can never become a [CarbResult] without inventing the
     * weight the whole path exists to avoid. Rendering from the exact value instead lets both paths
     * share the result panel, its *Copy* action and *Add to meal* — before this, a valid direct-carb
     * calculation showed the equation while the result stayed "pending" and its actions never
     * appeared.
     *
     * At most one of the two is ever non-null, so the order here resolves nothing in practice; it is
     * fixed only so the property is total.
     */
    val exactCarbs: BigDecimal? get() = result?.exact ?: directCarbResult

    /** Running total of the meal, or null when the meal is empty and the bar should not show. */
    val mealTotal: CarbResult? get() = if (mealItems.isEmpty()) null else MealTotal.asResult(mealItems)
}

/** Every way the screen can fail to show a number, each with its own recovery (§13, §26, §36). */
sealed interface Failure {
    /** No such barcode anywhere. Offers scan-label / manual entry (§26). */
    data object NotFound : Failure

    /** A record exists but its carbohydrate value cannot be trusted (§13). */
    data object NoUsableValue : Failure

    /**
     * A record exists with a carbohydrate value, but nothing said whether it is per 100 g or per
     * 100 ml (§17).
     *
     * Separate from [NoUsableValue] because the recovery differs in kind. There, the number itself
     * is missing or corrupt and the user has to read one off the package. Here the number is fine
     * and only its unit is open, so the user answers one question — which is why this state points
     * at manual entry, where the basis is a chip they can see and change.
     */
    data object UnknownBasis : Failure

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

    /**
     * The load currently in flight, so one barcode never causes two network requests.
     *
     * The already-loaded guard below cannot do this job: it tests `product != null`, which is
     * precisely what a lookup that has *started but not finished* has not set yet. Two calls close
     * together — a recomposition re-running the load effect, a user retrying a slow lookup — both
     * saw a null product and both went to the network. Open Food Facts allows 15 reads per minute
     * per IP, so duplicate requests are not merely wasteful; they spend a budget the whole app
     * shares, and the second answer would overwrite the first for no benefit.
     *
     * This job covers the **whole** load, not just the fetch: [onProductLoaded] suspends inside it
     * rather than launching separately. Otherwise the delivery phase ran outside the job, leaving a
     * window in which neither guard below could see that a load was still underway.
     */
    private var lookupJob: Job? = null

    /** Load a stored or remote product by barcode. */
    fun load(barcode: String) {
        if (_state.value.barcode == barcode && _state.value.product != null) return
        // A lookup already running for this same barcode is the answer this call wants; joining it
        // costs nothing and starting a second one costs a request.
        if (lookupJob?.isActive == true && _state.value.barcode == barcode) return

        // A lookup for a *different* barcode is stale the moment this one is asked for. Cancelling
        // rather than letting it finish is what stops a slow previous scan delivering its product
        // over the new one — the state writes below are unconditional, so whichever job completed
        // last would otherwise win regardless of which the user actually asked for.
        lookupJob?.cancel()
        _state.update { it.copy(loading = true, barcode = barcode, failure = null) }

        lookupJob = viewModelScope.launch {
            when (val result = repository.lookup(barcode)) {
                is ProductFetchResult.Found -> onProductLoaded(result.product)
                is ProductFetchResult.NotFound ->
                    _state.update { it.copy(loading = false, failure = Failure.NotFound) }
                is ProductFetchResult.Unusable -> {
                    val failure = when (result.reason) {
                        UnusableReason.NO_CARB_VALUE -> Failure.NoUsableValue
                        UnusableReason.UNKNOWN_BASIS -> Failure.UnknownBasis
                    }
                    _state.update { it.copy(loading = false, failure = failure) }
                }
                is ProductFetchResult.Failed ->
                    _state.update { it.copy(loading = false, failure = Failure.Lookup(result.error)) }
            }
        }
    }

    /**
     * Start a calculation from a carbohydrate basis that is not stored anywhere (§28, 1.0.3 P1).
     *
     * **There is no name parameter, and that absence is the feature.** A nutrition label states a
     * carbohydrate figure and its basis; it does not state what the product is called. Requiring a
     * name here is what previously sent every OCR reading through the *Enter product* form, which
     * would not proceed without one and wrote a Room row before it would navigate — so reading one
     * number off one photograph cost a named, saved record nobody asked for. The name is asked for
     * exactly once, in [saveQuickCalculation], at the only moment it is genuinely required.
     *
     * The scratch [Product] is held in state and never handed to the repository, so no row exists
     * for the portion to be remembered against — which is why [rememberUsage] and [toggleFavorite]
     * return early on an empty barcode rather than needing a flag to consult.
     *
     * [origin] carries where the figure came from. It is not [VerificationStatus.USER_VERIFIED]:
     * the user confirmed a number the *parser* proposed rather than transcribing the package
     * themselves, and provenance and verification are separate facts that must stay separate.
     */
    fun startQuickCalculation(
        carbsPer100: BigDecimal,
        basis: NutritionBasis,
        origin: ProductDataOrigin = ProductDataOrigin.OCR,
    ) {
        val scratch = Product(
            barcode = "",
            name = "",
            carbsPer100 = carbsPer100,
            basis = basis,
            dataSource = origin,
            verificationStatus = VerificationStatus.UNVERIFIED,
        )
        _state.update {
            it.copy(
                loading = false,
                product = scratch,
                unsaved = true,
                failure = null,
                // A second reading replaces the first outright. Leaving a stale verdict or notice
                // standing would attach it to a figure it was never about.
                labelVerdict = null,
                newerRemoteCarbs = null,
            )
        }
        recalculate()
    }

    /**
     * Open or close the *Save product* form. Closing changes nothing else — the calculation stands.
     *
     * Clears [ProductUiState.quickSaveFailed] as well as the name error, because that message
     * describes the attempt the user just made and not the one they are about to make. Left
     * standing, a failure from a previous attempt would still be on screen behind a freshly opened
     * form, and would remain there after a *successful* save right up until the screen changed.
     */
    fun showSaveQuickCalculation(show: Boolean) =
        _state.update {
            it.copy(showSaveQuickCalculationForm = show, quickSaveNameError = false, quickSaveFailed = false)
        }

    /**
     * Persist the calculation currently on screen as a product (1.0.3 P1).
     *
     * The only write this path ever makes, and only from a deliberate tap. The calculation is already
     * complete and visible before this is called, so nothing about the number changes here — the
     * product simply acquires a name, a key and a row.
     *
     * The synthetic key is namespaced exactly as [app.justthecarbs.ui.manual.ManualEntryViewModel]
     * does, so it can never collide with a real GTIN, and it is what lets the saved product appear in
     * Recents at all.
     *
     * Origin is preserved rather than flattened to MANUAL: the figure was read by the camera and
     * saving it does not change where it came from.
     *
     * `saveUserAuthoredProduct` also stamps [VerificationStatus.USER_VERIFIED], which is deliberate
     * and is the same treatment a manually-entered product gets. It is a **stronger** claim than the
     * one [startQuickCalculation] makes, and the difference is the user's own act: confirming a
     * parser's proposal to get a number is not vouching for it, whereas choosing to keep this
     * product for future meals is. Note this is the repository's existing rule for user-authored
     * products rather than something decided here — if it is ever revisited, it must move for manual
     * entry and this path together, since neither has a better claim than the other.
     */
    fun saveQuickCalculation(name: String) {
        val state = _state.value
        val product = state.product ?: return
        // Not a quick calculation, or already saved. A second tap has nothing left to write, and
        // writing anyway would create a duplicate row under a fresh synthetic key.
        if (!state.unsaved || state.savingQuickCalculation) return

        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(quickSaveNameError = true) }
            return
        }

        val key = "local:${UUID.randomUUID()}"
        val saved = product.copy(barcode = key, name = trimmed)
        _state.update { it.copy(savingQuickCalculation = true, quickSaveNameError = false) }

        viewModelScope.launch {
            runCatching {
                repository.saveUserAuthoredProduct(
                    saved,
                    origin = if (product.dataSource.isUserAuthored) product.dataSource else ProductDataOrigin.MANUAL,
                )
            }.onSuccess {
                // The screen keeps every number it was showing; only its identity changes. The
                // portion is recorded now that there is a row to record it against.
                _state.update {
                    it.copy(
                        product = saved,
                        barcode = key,
                        unsaved = false,
                        savingQuickCalculation = false,
                        showSaveQuickCalculationForm = false,
                        quickSaveFailed = false,
                    )
                }
                rememberUsage()
            }.onFailure {
                // Reported rather than merely survived: clearing the flag alone would re-enable the
                // button and change nothing else, making a failed save look like a missed tap.
                //
                // The form is closed as part of reporting, and that is not cosmetic. The failure
                // message renders on the *Save product* action, which sits on the screen behind
                // this dialog — so leaving the dialog open would put the only account of what went
                // wrong underneath the scrim, and the user would see a dialog that simply did
                // nothing when they tapped Save. Closing it reveals the message and leaves the
                // action right there to retry.
                _state.update {
                    it.copy(
                        savingQuickCalculation = false,
                        quickSaveFailed = true,
                        showSaveQuickCalculationForm = false,
                    )
                }
            }
        }
    }

    /**
     * Suspends rather than launching, so delivery is part of [lookupJob] (see its KDoc).
     *
     * It used to `viewModelScope.launch` a coroutine of its own. That detached the whole delivery
     * phase — the portion-unit read, the recalculation and the background refresh — from the job
     * `load` tracks, which left a window where `lookupJob.isActive` was already false and
     * `state.product` was still null. Both of `load`'s guards read exactly those two things, so a
     * second `load` in that window went back to Open Food Facts for a barcode already fetched, and
     * a superseded lookup's delivery could no longer be cancelled by the newer one.
     *
     * Running inline keeps that work inside the job's lifetime, so both guarantees hold for the
     * whole operation instead of only its network half.
     */
    private suspend fun onProductLoaded(product: Product) {
        // Pre-fill the portion the user chose last time, so a repeat product needs no typing at
        // all (§20) — but only if they have not already started typing in this session.
        val restoredPortion = savedState.get<String>(KEY_PORTION)
        val restoredCount = savedState.get<String>(KEY_COUNT)
        val restoredMode = savedState.get<String>(KEY_MODE)?.let(InputMode::valueOf)
        val restoredSelectedId = savedState.get<Long>(KEY_SELECTED_UNIT)

        run {
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
                when (val conversion = units.first { it.id == resolvedSelectedId }.conversion) {
                    is PortionConversion.WeightBased -> {
                        val count = PortionParser.parse(countText) ?: BigDecimal.ONE
                        PortionResolver.resolve(count, conversion.amountPerUnit)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                    // A restored direct-carb selection has no grams to pre-fill. The count alone
                    // reproduces the calculation.
                    is PortionConversion.DirectCarbs -> ""
                }
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
            // A restored countable selection must recalculate through its own conversion path;
            // recalculate() alone only covers the grams field.
            val restoredUnit = units.firstOrNull { it.id == resolvedSelectedId }
            if (restoredUnit != null && resolvedMode == InputMode.PORTION_UNIT) {
                recalculateFromCount(restoredUnit, countText)
            } else {
                recalculate()
            }

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
                // Asks the unit itself rather than comparing here, so this notice uses the same
                // numeric comparison as everywhere else and a trailing zero cannot trigger it.
                val changed = refreshed?.takeIf { it.remoteConversionDiffers }?.latestRemoteConversion
                if (changed != null) {
                    _state.update { it.copy(newerRemotePortionUnit = changed) }
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
        _state.update {
            it.copy(inputMode = InputMode.GRAMS, selectedPortionUnitId = null, directCarbResult = null)
        }
        recalculate()
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
            _state.update { it.copy(result = null, directCarbResult = null) }
            return
        }

        when (val conversion = unit.conversion) {
            is PortionConversion.WeightBased -> {
                val resolved = PortionResolver.resolve(count, conversion.amountPerUnit)
                val portionText = resolved.stripTrailingZeros().toPlainString()
                savedState[KEY_PORTION] = portionText
                _state.update { it.copy(portionText = portionText, directCarbResult = null) }
                recalculate()
            }
            is PortionConversion.DirectCarbs -> {
                // No grams exist on this path, so none are written into portionText. Filling it with
                // a derived figure would put a weight the app never knew in front of the user.
                _state.update {
                    it.copy(
                        result = null,
                        directCarbResult = DirectCarbCalculator.exactCarbs(count, conversion.carbsPerUnit),
                    )
                }
            }
        }
    }

    fun showAddPortionUnitForm(show: Boolean) = _state.update { it.copy(showAddPortionUnitForm = show) }

    /** A unit the user defines themselves (§6). Always saved as verified — they read their own scale. */
    fun addPortionUnit(kind: PortionUnitKind, conversion: PortionConversion, customLabel: String?) {
        val product = _state.value.product ?: return
        if (product.barcode.isEmpty()) return
        viewModelScope.launch {
            val saved = repository.saveUserPortionUnit(
                barcode = product.barcode,
                kind = kind,
                conversion = conversion,
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
    fun correctSelectedPortionUnit(confirmedConversion: PortionConversion) {
        val unit = _state.value.selectedPortionUnit ?: return
        viewModelScope.launch {
            val verified = repository.verifyPortionUnit(unit.id, confirmedConversion)
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
                    newerRemotePortionUnit = null,
                )
            }
            if (_state.value.selectedPortionUnitId == applied.id) {
                recalculateFromCount(applied, _state.value.countText)
            }
        }
    }

    fun dismissNewerRemotePortionUnit() = _state.update { it.copy(newerRemotePortionUnit = null) }

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
    fun addCurrentToMeal(portionDescription: String, fallbackName: String = "") {
        val product = _state.value.product ?: return
        val barcode = product.barcode.takeIf { !_state.value.unsaved }
        val directCarbs = _state.value.directCarbResult
        val conversion = _state.value.selectedPortionUnit?.conversion
        // A quick calculation has no name by design, and `MealScreen` renders this straight into the
        // line and into the "Remove …" label a screen reader announces — so an empty one is a blank
        // row in the one list whose whole job is saying what is on the plate. The wording comes from
        // the screen for the same reason [portionDescription] does: it lives in resources.
        val displayName = product.name.ifBlank { fallbackName }

        viewModelScope.launch {
            if (directCarbs != null && conversion is PortionConversion.DirectCarbs) {
                val count = PortionParser.parse(_state.value.countText) ?: return@launch
                repository.addDirectCarbMealItem(
                    productBarcode = barcode,
                    displayName = displayName,
                    portionDescription = portionDescription,
                    count = count,
                    carbsPerUnit = conversion.carbsPerUnit,
                    exactCarbs = directCarbs,
                )
            } else {
                val result = _state.value.result ?: return@launch
                val resolved = PortionParser.parse(_state.value.portionText) ?: return@launch
                repository.addMealItem(
                    productBarcode = barcode,
                    displayName = displayName,
                    portionDescription = portionDescription,
                    resolvedAmount = resolved,
                    basis = product.basis,
                    carbsPer100 = product.carbsPer100,
                    exactCarbs = result.exact,
                )
            }
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

    /**
     * Remember the portion once the user has actually acted on the result (§20, §21, §11-§12).
     *
     * Branches on the selected unit's [PortionConversion] rather than falling back through nullable
     * values (correction pass §4). The old form was `parse(portionText) ?: count`, which for a
     * direct-carb portion wrote the *count* into `lastPortion`: using 4 slices recorded "4" as the
     * product's remembered gram amount, so the next visit in grams mode pre-filled 4 g of bread.
     *
     * Reading `portionText` at all is unsafe on the direct-carb path for a second reason — it can
     * still hold grams left over from an earlier weight-based selection this session, which would be
     * recorded as if the user had just chosen it. The typed branch below never reads it there.
     */
    fun rememberUsage() {
        val product = _state.value.product ?: return
        if (_state.value.unsaved || product.barcode.isEmpty()) return
        val mode = _state.value.inputMode
        val conversion = _state.value.selectedPortionUnit?.conversion

        val count = if (mode == InputMode.PORTION_UNIT) {
            PortionParser.parse(_state.value.countText)
        } else {
            null
        }

        val resolvedPortion = when {
            // No weight exists on this path and none may be invented — not even from the count.
            mode == InputMode.PORTION_UNIT && conversion is PortionConversion.DirectCarbs -> null
            else -> PortionParser.parse(_state.value.portionText)
        }

        // Nothing usable to record at all: no weight and, in countable mode, no count either.
        if (resolvedPortion == null && count == null) return

        viewModelScope.launch {
            repository.recordUse(
                product.barcode,
                resolvedPortion,
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
