package app.carbscan.ui.product

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import app.carbscan.ui.components.ProductHeroImage
import app.carbscan.ui.components.ProductGalleryDialog
import app.carbscan.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.carbscan.R
import app.carbscan.domain.AppSettings
import app.carbscan.domain.InputMode
import app.carbscan.domain.LookupError
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionParser
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.Product
import app.carbscan.domain.ProductImageSelector
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ResultFormatter
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.VerificationStatus
import app.carbscan.ui.components.FavoriteButton
import app.carbscan.ui.components.PrimaryAction
import app.carbscan.ui.components.RecoveryPanel
import app.carbscan.ui.components.SecondaryAction
import app.carbscan.ui.components.SourceBadge
import app.carbscan.domain.PortionUsage
import app.carbscan.ui.meal.MealActions
import app.carbscan.ui.meal.MealBarIfPresent
import app.carbscan.ui.theme.NumberType
import app.carbscan.ui.theme.Space
import app.carbscan.ui.theme.extendedColors
import java.math.BigDecimal
import java.math.RoundingMode

/** Stable handle for the inline portion-unit correction field, used by instrumented tests. */
const val PORTION_CORRECTION_FIELD_TAG = "portion_unit_correction_amount"

/** Stable handle for the "add portion unit" form's amount field, used by instrumented tests. */
const val ADD_PORTION_UNIT_FIELD_TAG = "add_portion_unit_amount"

/** Stable handle for the dominant carbohydrate result, used by instrumented tests. */
const val PRODUCT_RESULT_TAG = "product_result"

/**
 * The calculator — the screen §14 says deserves the majority of the UI attention.
 *
 * Layout is built around one constraint from §40: the user is standing in a kitchen holding food
 * in their other hand. The result is pinned to the bottom of the screen, above the keyboard, so it
 * stays readable while the portion is being typed, and every control they need sits in the lower
 * half within thumb reach.
 */
@Composable
fun ProductScreen(
    state: ProductUiState,
    settings: AppSettings,
    onPortionChanged: (String) -> Unit,
    onAdjust: (Int) -> Unit,
    onSetPortion: (BigDecimal) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onVerifyByTyping: () -> Unit = {},
    onDismissVerify: () -> Unit,
    onConfirmVerification: (BigDecimal, app.carbscan.domain.NutritionBasis, String) -> Unit,
    onResetOnline: () -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit = {},
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
    onSwitchToGrams: () -> Unit = {},
    onSwitchToPortionUnit: (Long) -> Unit = {},
    onCountChanged: (String) -> Unit = {},
    onShowAddPortionUnitForm: (Boolean) -> Unit = {},
    onAddPortionUnit: (PortionUnitKind, BigDecimal, String?) -> Unit = { _, _, _ -> },
    onVerifyPortionUnit: () -> Unit = {},
    onApplyNewerRemotePortionUnit: () -> Unit = {},
    onDismissNewerRemotePortionUnit: () -> Unit = {},
    onCorrectPortionUnit: (BigDecimal) -> Unit = {},
    onCancelPortionUnitCorrection: () -> Unit = {},
    /** Takes the portion in the user's own words ("2 slices"), which only a composable can build. */
    onAddToMeal: (String) -> Unit = {},
    onAddToMealAndScanNext: (String) -> Unit = {},
    onOpenMeal: () -> Unit = {},
    onConfirmLabelMatch: () -> Unit = {},
    onUseDetectedLabelValue: (BigDecimal) -> Unit = {},
    onEditDetectedLabelValue: (BigDecimal) -> Unit = {},
    onDismissLabelVerdict: () -> Unit = {},
    onSelectUsualPortion: (PortionUsage) -> Unit = {},
) {
    var galleryOpen by remember(state.product?.barcode) { mutableStateOf(false) }
    val galleryImages = remember(state.product?.images) {
        state.product?.let(ProductImageSelector::galleryImages).orEmpty()
    }

    if (galleryOpen && state.product != null && galleryImages.isNotEmpty()) {
        ProductGalleryDialog(
            productName = state.product.name,
            images = galleryImages,
            onDismiss = { galleryOpen = false },
        )
    }

    if (state.showVerifyDialog && state.product != null) {
        VerifyDialog(
            product = state.product,
            onConfirm = onConfirmVerification,
            onDismiss = onDismissVerify,
        )
    }

    // A label reading is presented for comparison, never applied (§12). Shown over the calculator
    // so the value being compared against stays visible behind it.
    if (state.labelVerdict != null && state.product != null) {
        LabelVerificationDialog(
            verdict = state.labelVerdict,
            productBasis = state.product.basis,
            currentIsUserAuthored = state.product.dataSource.isUserAuthored,
            onConfirmMatch = onConfirmLabelMatch,
            onUsePackageValue = onUseDetectedLabelValue,
            onEditDetected = onEditDetectedLabelValue,
            onRescan = onScanLabel,
            onDismiss = onDismissLabelVerdict,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Doc's decorative blue-soft circle, bleeding off the top-right corner (result.html).
        // Purely decorative — sits behind all content, never intercepts touches.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-90).dp)
                .size(220.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            ProductTopBar(
                product = state.product,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onVerify = onVerify,
                onVerifyByTyping = onVerifyByTyping,
                onResetOnline = onResetOnline,
            )

            when {
                state.loading -> LoadingBody()
                state.failure != null -> FailureBody(
                    failure = state.failure,
                    barcode = state.barcode,
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                    onRetry = onRetry,
                    onSearch = onSearch,
                )
                state.product != null -> CalculatorBody(
                    product = state.product,
                    state = state,
                    settings = settings,
                    onPortionChanged = onPortionChanged,
                    onAdjust = onAdjust,
                    onSetPortion = onSetPortion,
                    onApplyNewerRemote = onApplyNewerRemote,
                    onDismissNewerRemote = onDismissNewerRemote,
                    onSwitchToGrams = onSwitchToGrams,
                    onSwitchToPortionUnit = onSwitchToPortionUnit,
                    onCountChanged = onCountChanged,
                    onShowAddPortionUnitForm = onShowAddPortionUnitForm,
                    onAddPortionUnit = onAddPortionUnit,
                    onVerifyPortionUnit = onVerifyPortionUnit,
                    onApplyNewerRemotePortionUnit = onApplyNewerRemotePortionUnit,
                    onDismissNewerRemotePortionUnit = onDismissNewerRemotePortionUnit,
                    onCorrectPortionUnit = onCorrectPortionUnit,
                    onCancelPortionUnitCorrection = onCancelPortionUnitCorrection,
                    onAddToMeal = onAddToMeal,
                    onAddToMealAndScanNext = onAddToMealAndScanNext,
                    onOpenMeal = onOpenMeal,
                    onSelectUsualPortion = onSelectUsualPortion,
                    onOpenGallery = { galleryOpen = true }.takeIf { galleryImages.isNotEmpty() },
                )
            }
        }
    }
}

@Composable
private fun ProductTopBar(
    product: Product?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVerify: () -> Unit,
    onVerifyByTyping: () -> Unit,
    onResetOnline: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.product_back),
            )
        }

        Text(
            text = product?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.s)
                .semantics { heading() },
        )

        if (product != null && product.barcode.isNotEmpty()) {
            FavoriteButton(favorite = product.favorite, onToggle = onToggleFavorite)

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(Space.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.product_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Scanning first, typing second. Both remain available: OCR fails on curved,
                    // glossy and worn packaging often enough that removing the typed path would
                    // strand the user exactly when the camera lets them down (§12).
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_verify)) },
                        onClick = { menuOpen = false; onVerify() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_verify_typed)) },
                        onClick = { menuOpen = false; onVerifyByTyping() },
                    )
                    // Only offered when there is genuinely an online value to go back to (§23).
                    if (product.canResetToOnlineValue) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.product_reset_online)) },
                            onClick = { menuOpen = false; onResetOnline() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    // §37: a brief, quiet loading state. Never a full-screen blocking spinner.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun FailureBody(
    failure: Failure,
    barcode: String,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit = {},
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
) {
    val title = when (failure) {
        Failure.NotFound -> stringResource(R.string.notfound_title)
        Failure.NoUsableValue -> stringResource(R.string.unusable_title)
        is Failure.Lookup -> when (failure.error) {
            LookupError.OFFLINE -> stringResource(R.string.error_offline_title)
            LookupError.TIMEOUT -> stringResource(R.string.error_timeout_title)
            LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_title)
            LookupError.SERVER -> stringResource(R.string.error_server_title)
            LookupError.MALFORMED -> stringResource(R.string.error_malformed_title)
        }
    }
    val body = when (failure) {
        Failure.NotFound -> stringResource(R.string.notfound_body)
        Failure.NoUsableValue -> stringResource(R.string.unusable_body)
        is Failure.Lookup -> when (failure.error) {
            LookupError.OFFLINE -> stringResource(R.string.error_offline_body)
            LookupError.RATE_LIMITED -> stringResource(R.string.error_rate_limited_body)
            else -> stringResource(R.string.error_generic_body)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RecoveryPanel(title = title, body = body) {
            // Every failure offers a way to get a number anyway. The user never hits a dead end
            // (§10, §26, §32).
            //
            // Search sits first in the recovery order for a *missing* barcode specifically: the
            // product may well be in the database under a different code, and finding it there
            // beats retyping a label the database already has. For a network failure it is
            // pointless — the same host is down — so it is not offered there (spec §9).
            if (failure is Failure.NotFound) {
                PrimaryAction(text = stringResource(R.string.search_action), onClick = onSearch)
                SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
            } else {
                PrimaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
            }
            SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
            if (failure is Failure.Lookup) {
                SecondaryAction(text = stringResource(R.string.error_retry), onClick = onRetry)
            }
            if (barcode.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.notfound_barcode, barcode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        }
    }
}

@Composable
private fun CalculatorBody(
    product: Product,
    state: ProductUiState,
    settings: AppSettings,
    onPortionChanged: (String) -> Unit,
    onAdjust: (Int) -> Unit,
    onSetPortion: (BigDecimal) -> Unit,
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
    onSwitchToGrams: () -> Unit = {},
    onSwitchToPortionUnit: (Long) -> Unit = {},
    onCountChanged: (String) -> Unit = {},
    onShowAddPortionUnitForm: (Boolean) -> Unit = {},
    onAddPortionUnit: (PortionUnitKind, BigDecimal, String?) -> Unit = { _, _, _ -> },
    onVerifyPortionUnit: () -> Unit = {},
    onApplyNewerRemotePortionUnit: () -> Unit = {},
    onDismissNewerRemotePortionUnit: () -> Unit = {},
    onCorrectPortionUnit: (BigDecimal) -> Unit = {},
    onCancelPortionUnitCorrection: () -> Unit = {},
    onAddToMeal: (String) -> Unit = {},
    onAddToMealAndScanNext: (String) -> Unit = {},
    onOpenMeal: () -> Unit = {},
    onSelectUsualPortion: (PortionUsage) -> Unit = {},
    /** Null when the product has no safe gallery image, which is what removes the hero's tap. */
    onOpenGallery: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ZONE 1 — product identity (development-pass brief §4, §19).
        //
        // The hero image is the first thing on the screen because the first question the user has,
        // before they trust any number, is "is this the package in my hand?". It compacts while the
        // keyboard is open: identification matters before typing, the portion and result matter
        // during it.
        // Read from the IME inset's height rather than the experimental `isImeVisible`, which is a
        // stable API giving the same fact. Non-zero means the keyboard is taking screen space.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        ProductHeroImage(
            product = product,
            compact = imeVisible,
            onClick = onOpenGallery,
            modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.s),
        )

        // The per-100 figure and its provenance, directly under the image they describe.
        ProductSummary(
            product = product,
            modifier = Modifier.padding(horizontal = Space.screenEdge),
        )

        // Correction #5/#10: a newer online figure is offered, never imposed. The calculation the
        // user is looking at does not move unless they say so.
        state.newerRemoteCarbs?.let { newer ->
            RemoteChangedNotice(
                newerCarbs = newer,
                unit = product.portionUnit,
                onApply = onApplyNewerRemote,
                onDismiss = onDismissNewerRemote,
                modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.s),
            )
        }

        val selectedUnit = state.selectedPortionUnit
        val countableActive = state.inputMode == InputMode.PORTION_UNIT && selectedUnit != null

        // ZONE 2 — portion controls. Scrollable, and deliberately holds only what the user can
        // afford to scroll for: the mode row, the input field itself, and the secondary shortcuts.
        // The equation and the result live outside it, in the pinned surface below (§3.2).
        val portionScroll = rememberScrollState()
        // Zone 2 takes the remaining height and lays its controls out from the TOP, directly under
        // the product they belong to.
        //
        // It was previously bottom-anchored, on the reasoning that controls belong within thumb
        // reach (§40). Once the hero image shortened zone 1, that left a measured 163 dp of dead
        // space between the per-100 figure and "How much are you eating?" — a quarter of the
        // screen of nothing, which reads as a broken layout rather than a calm one. The controls
        // still sit comfortably in the lower half because the hero above them is 150 dp tall; they
        // simply no longer float away from it. Pinned by
        // `thePortionControlsFollowTheProductHeaderWithoutALargeDeadBand`.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(portionScroll)
                .padding(horizontal = Space.screenEdge),
        ) {
            Text(
                text = stringResource(R.string.product_portion_question),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            // Only rendered when countable units genuinely exist (§11 of the countable-portions
            // brief) — a product with none keeps today's exact single-field layout, unchanged.
            if (state.portionUnits.isNotEmpty()) {
                Spacer(Modifier.height(Space.m))
                PortionModeRow(
                    units = state.portionUnits,
                    selectedUnitId = state.selectedPortionUnitId,
                    isGramsSelected = state.inputMode == InputMode.GRAMS,
                    onSelectGrams = onSwitchToGrams,
                    onSelectUnit = onSwitchToPortionUnit,
                )
            }

            // Usual portions, above the input rather than below it: they are an alternative to
            // typing, so they have to be seen before the user starts (§13). Absent entirely until a
            // portion has been used twice, which is most of the time.
            if (state.usualPortions.isNotEmpty()) {
                Spacer(Modifier.height(Space.m))
                UsualPortionRow(
                    usages = state.usualPortions,
                    units = state.portionUnits,
                    basisUnit = product.portionUnit,
                    onSelect = onSelectUsualPortion,
                )
            }

            Spacer(Modifier.height(Space.m))
            if (countableActive) {
                CountField(value = state.countText, unit = selectedUnit!!, onValueChange = onCountChanged)
                Spacer(Modifier.height(Space.s))
                PortionUnitStatusRow(
                    unit = selectedUnit,
                    onVerify = onVerifyPortionUnit,
                    onCorrect = onCorrectPortionUnit,
                    correcting = state.correctingPortionUnit,
                    onCancelCorrection = onCancelPortionUnitCorrection,
                )
                state.newerRemotePortionUnitAmount?.let { newer ->
                    Spacer(Modifier.height(Space.s))
                    PortionUnitChangedNotice(
                        newerAmount = newer,
                        unit = selectedUnit,
                        onApply = onApplyNewerRemotePortionUnit,
                        onDismiss = onDismissNewerRemotePortionUnit,
                    )
                }
            } else {
                PortionField(
                    value = state.portionText,
                    unit = product.portionUnit,
                    onValueChange = onPortionChanged,
                )

                Spacer(Modifier.height(Space.m))
                QuickAdjustRow(onAdjust = onAdjust)

                // Only offered when the package size was read confidently. A guessed pack size
                // would be a wrong portion presented as a shortcut (§14, §13).
                product.packageAmount?.let { pack ->
                    Spacer(Modifier.height(Space.s))
                    PackShortcuts(pack = pack, onSetPortion = onSetPortion)
                }
            }

            if (product.barcode.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                AddPortionUnitAction(
                    expanded = state.showAddPortionUnitForm,
                    basisUnit = product.portionUnit,
                    onExpand = { onShowAddPortionUnitForm(true) },
                    onCancel = { onShowAddPortionUnitForm(false) },
                    onSave = { kind, amount, label -> onAddPortionUnit(kind, amount, label) },
                )
            }

            // Enough trailing room that the last control can be scrolled clear of the pinned
            // result panel below. At 16 dp the final row came to rest half-underneath it — legible
            // enough to read but cut through mid-glyph, which looks like a rendering fault rather
            // than like more content below. Found by looking at the screen once the Usual row had
            // made this zone taller; the panel's shadow needs clearing too, not just its edge.
            Spacer(Modifier.height(Space.xl))
        }

        // ZONE 3 — the equation and the result, in one pinned surface (brief §3.2).
        //
        // The portion description is built here rather than in the ViewModel because pluralised
        // unit names ("slice"/"slices") live in resources and only a composable can read them.
        // The ViewModel supplies the numbers; the screen supplies the wording.
        val portionDescription = portionDescription(
            state = state,
            unit = selectedUnit.takeIf { countableActive },
            basisUnit = product.portionUnit,
        )

        ResultPanel(
            state = state,
            settings = settings,
            equationUnit = selectedUnit.takeIf { countableActive },
            onAddToMeal = { onAddToMeal(portionDescription) },
            onAddToMealAndScanNext = { onAddToMealAndScanNext(portionDescription) },
            onOpenMeal = onOpenMeal,
        )
    }
}

/**
 * The portion in the user's own words, for a meal line (development-pass brief §10).
 *
 * "2 slices" rather than "72 g": a meal list of resolved gram figures would be unrecognisable as
 * the food the user just scanned. Falls back to the raw amount plus its basis unit when no
 * countable unit is in use, which is then genuinely how the user expressed it.
 */
@Composable
private fun portionDescription(
    state: ProductUiState,
    unit: PortionUnit?,
    basisUnit: String,
): String = if (unit != null) {
    val count = state.countText.ifBlank { "0" }
    // Plural agreement follows the typed count, so "1 slice" and "2 slices" both read correctly.
    val quantity = PortionParser.parse(count)?.toInt() ?: 0
    "$count ${unit.unitLabel(count = quantity)}"
} else {
    "${state.portionText} $basisUnit"
}

/**
 * The per-100 figure and where it came from (§14, §19).
 *
 * The thumbnail that used to sit here is gone: [ProductHeroImage] directly above now carries the
 * product's identity, and repeating the same photo at 56 dp underneath it was redundant. Dropping
 * it also lets this row become a single quiet line of facts rather than a card competing with the
 * image above and the result below.
 */
@Composable
private fun ProductSummary(product: Product, modifier: Modifier = Modifier) {
    // Stacked rather than side by side. The badge is itself a two-part block (a pill plus, for
    // unverified online data, a "Check package if needed" line), so putting it beside the per-100
    // figure produced a ragged two-line arrangement where neither element had a clean baseline —
    // visible only once a real product was on screen.
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Text(
            text = stringResource(
                R.string.product_per_100,
                product.carbsPer100.stripTrailingZeros().toPlainString(),
                product.portionUnit,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        SourceBadge(product)
    }
}

@Composable
private fun PortionField(value: String, unit: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = NumberType.portion,
        singleLine = true,
        // Decimal keypad, because portions have decimals and a full keyboard would be noise (§16).
        // Done, for the same reason as the count field: a decimal keypad has no Enter key, so
        // without it there is no in-app way to put the keyboard away.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        suffix = {
            Text(text = unit, style = MaterialTheme.typography.titleMedium)
        },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "" },
    )
}

@Composable
private fun QuickAdjustRow(onAdjust: (Int) -> Unit) {
    // Order runs negative → positive so the row reads like a number line (§16).
    val steps = listOf(-10 to R.string.adjust_minus_ten, -5 to R.string.adjust_minus_five,
        5 to R.string.adjust_plus_five, 10 to R.string.adjust_plus_ten)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        steps.forEach { (delta, label) ->
            val description = stringResource(label)
            OutlinedButton(
                onClick = { onAdjust(delta) },
                shape = RoundedCornerShape(Space.buttonRadius),
                // A button's default 24dp side padding leaves too little room for "+10" at a large
                // font scale, where it truncates to "+1" — a control that lies about what it does.
                // heightIn rather than height so the row grows instead of clipping (§39).
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.minTouchTarget)
                    .semantics { contentDescription = description },
            ) {
                Text(
                    text = if (delta > 0) "+$delta" else "$delta",
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * ¼ · ½ · Full pack (development-pass brief §14).
 *
 * ¼ was added because it is genuinely common for the large packages this shortcut applies to — a
 * 400 g loaf, a 1 L carton — and it removes an arithmetic step the user would otherwise do in their
 * head while holding the food.
 *
 * **¾ is deliberately absent.** §14 requires demonstrated value for it, and a fourth button pushes
 * the labels into truncation at large font scales, where a control that says "Fu…" is worse than a
 * control that does not exist.
 *
 * The reliability gate is unchanged: this row only renders when [PackageQuantityParser] read a
 * package size confidently, so a guessed pack size can never be presented as a shortcut, and
 * multipacks are still never inferred (§14, §13). Every fraction resolves through the same
 * base-unit path as a typed portion — no separate calculation.
 */
@Composable
private fun PackShortcuts(pack: BigDecimal, onSetPortion: (BigDecimal) -> Unit) {
    // Scale 2 with HALF_UP: a 355 ml can quartered is 88.75 ml, and truncating to a whole number
    // would silently change the portion the user asked for.
    val fractions = listOf(
        R.string.product_quarter_pack to pack.divide(BigDecimal(4), 2, RoundingMode.HALF_UP),
        R.string.product_half_pack to pack.divide(BigDecimal(2), 2, RoundingMode.HALF_UP),
        R.string.product_full_pack to pack,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        fractions.forEach { (label, amount) ->
            OutlinedButton(
                onClick = { onSetPortion(amount) },
                shape = RoundedCornerShape(Space.buttonRadius),
                // Tighter horizontal padding than the default so three labels fit one row at a
                // large font scale — the constraint that keeps ¾ out.
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.s),
                modifier = Modifier.weight(1f).heightIn(min = Space.minTouchTarget),
            ) {
                Text(
                    text = stringResource(label),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Stable handle for the usual-portions row, used by instrumented tests. */
const val USUAL_PORTION_ROW_TAG = "usual_portion_row"

/**
 * *Usual* — the portions this product has actually been eaten in (development-pass brief §13).
 *
 * A report, not a recommendation. The app is saying "you have used this twice", not "you should
 * eat this", which is why the label is *Usual* rather than *Suggested* or *For you*, and why
 * nothing here is ever pre-selected: a tap sets the portion, and without a tap the field is
 * untouched. An app that pre-filled its own guess would be making a dietary suggestion, which §28
 * rules out entirely.
 *
 * Countable variants keep their words — "2 slices", not the 72 g behind it — so the shortcut is
 * recognisable as the thing the user did last time.
 */
@Composable
private fun UsualPortionRow(
    usages: List<PortionUsage>,
    units: List<PortionUnit>,
    basisUnit: String,
    onSelect: (PortionUsage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(USUAL_PORTION_ROW_TAG)) {
        Text(
            text = stringResource(R.string.product_usual_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            usages.forEach { usage ->
                val unit = usage.portionUnitId?.let { id -> units.firstOrNull { it.id == id } }
                val amount = usage.amount.stripTrailingZeros().toPlainString()
                val label = if (unit != null) {
                    val quantity = usage.amount.toInt()
                    stringResource(R.string.product_usual_count, amount, unit.unitLabel(quantity))
                } else {
                    stringResource(R.string.product_usual_grams, amount, basisUnit)
                }

                OutlinedButton(
                    onClick = { onSelect(usage) },
                    shape = RoundedCornerShape(Space.chipRadius),
                    contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
                    modifier = Modifier.weight(1f).heightIn(min = Space.minTouchTarget),
                ) {
                    Text(text = label, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Grams | <one chip per countable unit> (countable-portions brief §9, §11). Only rendered when
 * the product has at least one countable unit — a plain product keeps today's single field.
 */
@Composable
private fun PortionModeRow(
    units: List<PortionUnit>,
    selectedUnitId: Long?,
    isGramsSelected: Boolean,
    onSelectGrams: () -> Unit,
    onSelectUnit: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        FilterChip(
            selected = isGramsSelected,
            onClick = onSelectGrams,
            label = { Text(stringResource(R.string.product_mode_grams)) },
            shape = RoundedCornerShape(Space.chipRadius),
        )
        units.forEach { unit ->
            FilterChip(
                selected = !isGramsSelected && unit.id == selectedUnitId,
                onClick = { onSelectUnit(unit.id) },
                label = { Text(unit.chipLabel()) },
                shape = RoundedCornerShape(Space.chipRadius),
            )
        }
    }
}

/**
 * The count field, e.g. `2` slices.
 *
 * Held as a [TextFieldValue] rather than a plain String purely so the selection can be controlled:
 * the field pre-fills with `1`, and **the whole value is selected the first time the field takes
 * focus**, so a user who taps in and types `2` gets `2` rather than `12` (brief §3.1). Android text
 * fields do not select-all on focus by default; without this the pre-filled `1` is a live value the
 * next keystroke appends to.
 *
 * Selection is applied once per focus gain, not on every recomposition — otherwise the caret would
 * jump back to a full selection while the user was still editing, which breaks ordinary cursor
 * editing (the fix must not trade one input bug for another).
 */
@Composable
private fun CountField(value: String, unit: PortionUnit, onValueChange: (String) -> Unit) {
    // The composable owns the selection; the caller still owns the text. Whenever the incoming
    // value differs from what we last emitted (mode switch, unit change, restored state), the
    // field's text is resynchronised while leaving the caret at the end.
    val focusManager = LocalFocusManager.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    var hasFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        textStyle = NumberType.portion,
        singleLine = true,
        // A Done action, not the platform default. The decimal keypad has no Enter key, so without
        // this the only way to dismiss the keyboard is the system back gesture — leaving the
        // controls underneath it (verify, add unit, pack shortcuts) covered with no obvious way to
        // reach them.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        suffix = { Text(text = unit.unitLabel(count = 2), style = MaterialTheme.typography.titleMedium) },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                // Only on the transition into focus. Re-selecting on every focused recomposition
                // would fight the user's own caret placement mid-edit.
                if (focus.isFocused && !hasFocus) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
                hasFocus = focus.isFocused
            }
            .semantics { contentDescription = "" },
    )
}

/**
 * "2 slices × 36 g = 72 g" — the derived gram amount shown as supporting information, never as the
 * dominant figure (brief §9, §11). Lets the user see where the calculation came from and quickly
 * spot a wrong per-unit weight without doing the multiplication themselves.
 */
@Composable
private fun PortionEquationText(
    count: String,
    unit: PortionUnit,
    resolvedGrams: String,
    modifier: Modifier = Modifier,
) {
    if (resolvedGrams.isBlank()) return
    // English pluralization: only exactly 1 is singular ("1 slice"); 0, 1.5, 2... are all plural
    // ("0 slices", "1.5 slices", "2 slices") — the equation is read as a sentence, so getting this
    // wrong reads as a typo, unlike the mode chip's fixed representative plural.
    val pluralQuantity = if (PortionParser.parse(count)?.compareTo(BigDecimal.ONE) == 0) 1 else 2
    Text(
        text = stringResource(
            R.string.product_count_equation,
            count.ifBlank { "0" },
            unit.unitLabel(count = pluralQuantity),
            unit.amountPerUnit.stripTrailingZeros().toPlainString(),
            unit.basis.unitLabel,
            resolvedGrams,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/**
 * Provenance/verification badge for the selected countable unit, mirroring [SourceBadge] — and the
 * entry point for correcting a wrong remote weight in place (development-pass brief §3.3).
 *
 * Before this, a user whose loaf really had 38 g slices while Open Food Facts said 36 g had no way
 * to *correct* the online unit: they could only add a second, competing custom unit. The repository
 * already supported the correction (`verifyPortionUnit(unitId, confirmedAmountPerUnit)`); this is
 * the UI path that finally calls it with a value.
 *
 * Verifying and correcting are the same gesture on purpose. The user is looking at the package
 * either way; whether the number matches is what they discover while looking.
 */
@Composable
private fun PortionUnitStatusRow(
    unit: PortionUnit,
    onVerify: () -> Unit,
    onCorrect: (BigDecimal) -> Unit = {},
    correcting: Boolean = false,
    onCancelCorrection: () -> Unit = {},
) {
    val isVerified = unit.verificationStatus == VerificationStatus.USER_VERIFIED
    val isFromOff = unit.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS

    if (!isFromOff) return // user-defined units need no provenance badge — they are simply the user's own.

    if (correcting) {
        PortionUnitCorrectionForm(unit = unit, onSave = onCorrect, onCancel = onCancelCorrection)
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isVerified) {
            Text(
                text = stringResource(R.string.product_verified_portion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A verified weight can still be wrong — the user may have verified against a
            // different loaf. Editing stays reachable rather than being a one-way door.
            TextButton(onClick = onVerify) {
                Text(stringResource(R.string.product_edit_portion), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            TextButton(onClick = onVerify) {
                Text(stringResource(R.string.product_online_portion), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * `1 slice = [36] g` → **Save as verified** (brief §3.3).
 *
 * Pre-filled with the current amount, so confirming an already-correct weight is one tap and
 * correcting a wrong one is a single edit. Saving routes to
 * `ProductRepository.verifyPortionUnit(unitId, confirmedAmountPerUnit)`, which preserves the remote
 * provenance and the original remote amount while recording the corrected effective value.
 */
@Composable
private fun PortionUnitCorrectionForm(
    unit: PortionUnit,
    onSave: (BigDecimal) -> Unit,
    onCancel: () -> Unit,
) {
    var amountText by remember(unit.id) {
        mutableStateOf(unit.amountPerUnit.stripTrailingZeros().toPlainString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.buttonRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.m),
    ) {
        Text(
            text = stringResource(R.string.product_correct_portion_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.product_one_unit_equals, unit.unitLabel(count = 1)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.s))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(unit.basis.unitLabel) },
                shape = RoundedCornerShape(Space.buttonRadius),
                // Tagged so UI tests can address this field directly. The alternative — indexing
                // into "every text field on screen" — silently targets the wrong field as soon as
                // the screen gains another one, which is exactly how a test starts failing for a
                // reason unrelated to what it checks.
                modifier = Modifier.weight(1f).testTag(PORTION_CORRECTION_FIELD_TAG),
            )
        }

        Spacer(Modifier.height(Space.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TextButton(
                onClick = {
                    // A blank or unparseable amount is not a correction. Silently doing nothing is
                    // right here: the field is still on screen showing what the user typed.
                    val amount = PortionParser.parse(amountText) ?: return@TextButton
                    if (amount.signum() <= 0) return@TextButton
                    onSave(amount)
                },
            ) { Text(stringResource(R.string.product_save_verified)) }
        }
    }
}

/** Same immutability pattern as [RemoteChangedNotice], scoped to the countable unit in use (§9). */
@Composable
private fun PortionUnitChangedNotice(
    newerAmount: BigDecimal,
    unit: PortionUnit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.buttonRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.m),
    ) {
        Text(
            text = stringResource(R.string.portion_changed_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(
                R.string.portion_changed_body,
                newerAmount.stripTrailingZeros().toPlainString(),
                unit.basis.unitLabel,
                unit.unitLabel(count = 1),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            TextButton(onClick = onApply) { Text(stringResource(R.string.portion_changed_apply)) }
        }
    }
}

/**
 * "+ Add portion unit" (brief §6). A subtle text action that expands into a small inline form —
 * deliberately not a new screen/route, so adding a unit never interrupts the calculator.
 */
@Composable
private fun AddPortionUnitAction(
    expanded: Boolean,
    basisUnit: String,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    onSave: (PortionUnitKind, BigDecimal, String?) -> Unit,
) {
    if (!expanded) {
        TextButton(onClick = onExpand) {
            Text(stringResource(R.string.product_add_portion_unit), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var kind by remember { mutableStateOf(PortionUnitKind.SLICE) }
    var kindMenuOpen by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.buttonRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.m),
    ) {
        Text(
            text = stringResource(R.string.product_add_portion_unit_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(Space.s))

        Text(
            text = stringResource(R.string.product_portion_unit_type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { kindMenuOpen = true }, shape = RoundedCornerShape(Space.buttonRadius)) {
                Text(if (kind == PortionUnitKind.CUSTOM) stringResource(R.string.product_portion_unit_custom_name) else kind.name)
            }
            DropdownMenu(expanded = kindMenuOpen, onDismissRequest = { kindMenuOpen = false }) {
                PortionUnitKind.entries.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.name) },
                        onClick = { kind = candidate; kindMenuOpen = false },
                    )
                }
            }
        }

        if (kind == PortionUnitKind.CUSTOM) {
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text(stringResource(R.string.product_portion_unit_custom_name)) },
                singleLine = true,
                shape = RoundedCornerShape(Space.buttonRadius),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.s))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(stringResource(R.string.product_portion_unit_weighs, kind.name.lowercase())) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(basisUnit) },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.fillMaxWidth().testTag(ADD_PORTION_UNIT_FIELD_TAG),
        )

        Spacer(Modifier.height(Space.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            TextButton(
                onClick = {
                    val amount = PortionParser.parse(amountText) ?: return@TextButton
                    if (kind == PortionUnitKind.CUSTOM && customName.isBlank()) return@TextButton
                    onSave(kind, amount, customName.ifBlank { null }.takeIf { kind == PortionUnitKind.CUSTOM })
                },
            ) { Text(stringResource(R.string.product_save)) }
        }
    }
}

/**
 * An unobtrusive notice that the online figure has moved (§24, corrections #5 and #10).
 *
 * Deliberately not a dialog and not an error: the user may be holding the older packaging, and a
 * changed database entry is information rather than a fault. It never blocks the calculation.
 */
@Composable
private fun RemoteChangedNotice(
    newerCarbs: BigDecimal,
    unit: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.buttonRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.m),
    ) {
        Text(
            text = stringResource(R.string.remote_changed_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(
                R.string.remote_changed_body,
                newerCarbs.stripTrailingZeros().toPlainString(),
                unit,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            TextButton(onClick = onApply) { Text(stringResource(R.string.remote_changed_apply)) }
        }
    }
}

/**
 * The persistent result (§14). Visually dominant, pinned above the keyboard, and always present —
 * it does not appear and disappear as the portion field is edited, because a result area that
 * moves is a result area the user has to hunt for.
 */
@Composable
private fun ResultPanel(
    state: ProductUiState,
    settings: AppSettings,
    /** Non-null when a countable unit is in use — draws the equation inside this same surface. */
    equationUnit: PortionUnit? = null,
    onAddToMeal: () -> Unit = {},
    onAddToMealAndScanNext: () -> Unit = {},
    onOpenMeal: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val result = state.result

    // Resolved during composition, not inside the click lambda: reading resources off
    // LocalContext at click time is not configuration-aware and can return a stale string.
    val copiedValue = result?.let { ResultFormatter.clipboardValue(it, settings.resultStyle) }
    val copiedMessage = copiedValue?.let { stringResource(R.string.product_copied, it) }

    val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Shadow + a distinct container tone. This surface was previously pure white on an
            // off-white page — a ~1% difference — so the most important element on the screen had
            // no edge at all and read as part of the background.
            .shadow(elevation = Space.resultElevation, shape = panelShape, clip = false)
            .clip(panelShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(
                start = Space.screenEdge,
                end = Space.screenEdge,
                // The panel gives back the height the meal bar takes, rather than growing by it.
                // Growing pushed the whole panel up over the portion field, so the user could no
                // longer read the number they were typing — the third layout defect in this area
                // that only running the app revealed.
                top = if (state.mealItems.isEmpty()) Space.l else Space.s,
                bottom = Space.l,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The running meal total, present only while a meal is actually in progress (§10). An
        // always-visible "0 items" strip would make the app look like a tracker with a permanent
        // dashboard, which is precisely what it is not.
        //
        // It belongs in this pinned surface, and finding that out took running the app three times.
        // Placed in the fixed header above, its ~56 dp came straight out of the portion controls:
        // "How much are you eating?" was clipped behind it and "+ Add portion unit" was pushed off
        // the bottom of the screen. Moved into the scrolling zone, it was simply not on screen once
        // the keyboard was open — a running total you cannot see is not a running total. Here it
        // shares the one surface that is always visible, for the same reason the equation does,
        // and in its compact form so the panel does not grow over the field above it.
        MealBarIfPresent(
            itemCount = state.mealItems.size,
            total = state.mealTotal,
            onClick = onOpenMeal,
            compact = true,
        )

        // The conversion equation lives INSIDE the result surface (brief §3.2).
        //
        // It is the user's sanity check — the one piece of UI answering "why is the answer that
        // number?" — and it used to sit in the scrollable region above, where an open keyboard
        // could push it out of view at exactly the moment it was wanted. Sharing the result's own
        // pinned surface makes "visible whenever the result is visible" a structural guarantee
        // rather than a property of how tall the screen happens to be.
        if (equationUnit != null) {
            PortionEquationText(
                count = state.countText,
                unit = equationUnit,
                resolvedGrams = state.portionText,
                modifier = Modifier.padding(bottom = Space.s),
            )
        }

        Text(
            text = stringResource(R.string.product_result_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.xs))

        if (result == null) {
            // Reserves the same height the result will occupy, so the panel does not jump when the
            // first digit is typed (§16: the result area must not move under the user).
            Box(
                modifier = Modifier.height(96.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.product_result_pending),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Both figures come from `exact`, independently. Neither is derived from the other,
            // so swapping which one dominates cannot introduce a double rounding (§17).
            val dominant = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(result.exact)} g"
                ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(result.wholeGrams)} g"
            }

            Row(
                modifier = Modifier.height(96.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Animated only on the digits changing, not on every recomposition, and only for
                // 120ms — long enough to notice the number moved, short enough that nobody waits.
                AnimatedContent(
                    targetState = dominant,
                    transitionSpec = {
                        (fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)))
                    },
                    label = "result",
                ) { value ->
                    Text(
                        text = value,
                        style = NumberType.result,
                        color = MaterialTheme.extendedColors.result,
                        maxLines = 1,
                        // Shrinks rather than clips. See [NumberType.resultAutoSize] — a result
                        // that loses digits still looks like a finished number.
                        autoSize = NumberType.resultAutoSize,
                        // Announced as a live region so TalkBack reads the new result as the
                        // portion changes, instead of leaving a blind user to hunt for it (§39).
                        modifier = Modifier
                            .testTag(PRODUCT_RESULT_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                Spacer(Modifier.width(Space.s))

                val copyLabel = stringResource(R.string.product_copy)
                IconButton(
                    onClick = {
                        // Only the number reaches the clipboard — never "31 g carbs" (§19).
                        clipboard.setText(AnnotatedString(copiedValue.orEmpty()))
                        if (settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        android.widget.Toast.makeText(
                            context,
                            copiedMessage.orEmpty(),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier
                        .size(Space.minTouchTarget)
                        .semantics { contentDescription = copyLabel },
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                }
            }

            // The supporting figure is rendered legibly, not as a whisper (design decision 3.2).
            Text(
                text = when (settings.resultStyle) {
                    ResultStyle.DECIMAL_DOMINANT -> stringResource(
                        R.string.product_result_whole,
                        ResultFormatter.whole(result.wholeGrams),
                    )
                    ResultStyle.WHOLE_DOMINANT -> stringResource(
                        R.string.product_result_calculated,
                        ResultFormatter.decimal(result.exact),
                    )
                },
                style = NumberType.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Only once there is a number worth adding. Offered under the result, never in place
            // of it: the app answers a carbohydrate question first and builds a meal second (§9).
            Spacer(Modifier.height(Space.m))
            MealActions(onAdd = onAddToMeal, onAddAndScanNext = onAddToMealAndScanNext)
        }
    }
}

// PrimaryAction / SecondaryAction moved to ui.components alongside RecoveryPanel, which they are
// only ever used inside — the search screen needs the same pair for the same panel.
