package app.carbscan.ui.product

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import app.carbscan.ui.components.ProductThumbnail
import app.carbscan.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
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
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ResultFormatter
import app.carbscan.domain.ResultStyle
import app.carbscan.domain.VerificationStatus
import app.carbscan.ui.components.FavoriteButton
import app.carbscan.ui.components.RecoveryPanel
import app.carbscan.ui.components.SourceBadge
import app.carbscan.ui.theme.NumberType
import app.carbscan.ui.theme.Space
import java.math.BigDecimal
import java.math.RoundingMode

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
    onDismissVerify: () -> Unit,
    onConfirmVerification: (BigDecimal, app.carbscan.domain.NutritionBasis, String) -> Unit,
    onResetOnline: () -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
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
) {
    if (state.showVerifyDialog && state.product != null) {
        VerifyDialog(
            product = state.product,
            onConfirm = onConfirmVerification,
            onDismiss = onDismissVerify,
        )
    }

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
            )
        }
    }
}

@Composable
private fun ProductTopBar(
    product: Product?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVerify: () -> Unit,
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_verify)) },
                        onClick = { menuOpen = false; onVerify() },
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
            PrimaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
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
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Pinned directly under the title, where the user expects the product's own facts. Leaving
        // it inside the centred block left it floating in the middle of the screen, detached from
        // the name it describes.
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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenEdge),
            // Anchored to the bottom, immediately above the result. §40 assumes the user is
            // standing in a kitchen holding food in the other hand, so the controls belong within
            // thumb reach and directly adjacent to the number they change. Centring instead left a
            // gap both above and below the block, which read as unfinished rather than as calm.
            verticalArrangement = Arrangement.Bottom,
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

            Spacer(Modifier.height(Space.m))
            val selectedUnit = state.selectedPortionUnit
            if (state.inputMode == InputMode.PORTION_UNIT && selectedUnit != null) {
                CountField(value = state.countText, unit = selectedUnit, onValueChange = onCountChanged)
                Spacer(Modifier.height(Space.s))
                PortionEquationText(count = state.countText, unit = selectedUnit, resolvedGrams = state.portionText)
                Spacer(Modifier.height(Space.s))
                PortionUnitStatusRow(unit = selectedUnit, onVerify = onVerifyPortionUnit)
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

            Spacer(Modifier.height(Space.l))
        }

        ResultPanel(state = state, settings = settings)
    }
}

/**
 * The product header (§14): thumbnail, the per-100 figure, and where that figure came from.
 *
 * Grouped into one card so the screen reads as *product* then *portion* then *result*, rather than
 * as a stack of unrelated lines.
 */
@Composable
private fun ProductSummary(product: Product, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(product = product, size = 56.dp)

        Column(modifier = Modifier.weight(1f).padding(start = Space.m)) {
            Text(
                text = stringResource(
                    R.string.product_per_100,
                    product.carbsPer100.stripTrailingZeros().toPlainString(),
                    product.portionUnit,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s))
            SourceBadge(product)
        }
    }
}

@Composable
private fun PortionField(value: String, unit: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = NumberType.portion,
        singleLine = true,
        // Decimal keypad, because portions have decimals and a full keyboard would be noise (§16).
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

@Composable
private fun PackShortcuts(pack: BigDecimal, onSetPortion: (BigDecimal) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // Same large-font constraint as the adjust row, and worse here: the Dutch "Hele
        // verpakking" is twice the length of "Full pack".
        OutlinedButton(
            onClick = { onSetPortion(pack.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)) },
            shape = RoundedCornerShape(Space.buttonRadius),
            contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.s),
            modifier = Modifier.weight(1f).heightIn(min = Space.minTouchTarget),
        ) { Text(stringResource(R.string.product_half_pack), textAlign = TextAlign.Center) }

        OutlinedButton(
            onClick = { onSetPortion(pack) },
            shape = RoundedCornerShape(Space.buttonRadius),
            contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.s),
            modifier = Modifier.weight(1f).heightIn(min = Space.minTouchTarget),
        ) { Text(stringResource(R.string.product_full_pack), textAlign = TextAlign.Center) }
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
            shape = RoundedCornerShape(Space.buttonRadius),
        )
        units.forEach { unit ->
            FilterChip(
                selected = !isGramsSelected && unit.id == selectedUnitId,
                onClick = { onSelectUnit(unit.id) },
                label = { Text(unit.chipLabel()) },
                shape = RoundedCornerShape(Space.buttonRadius),
            )
        }
    }
}

@Composable
private fun CountField(value: String, unit: PortionUnit, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = NumberType.portion,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text(text = unit.unitLabel(count = 2), style = MaterialTheme.typography.titleMedium) },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "" },
    )
}

/**
 * "2 slices × 36 g = 72 g" — the derived gram amount shown as supporting information, never as the
 * dominant figure (brief §9, §11). Lets the user see where the calculation came from and quickly
 * spot a wrong per-unit weight without doing the multiplication themselves.
 */
@Composable
private fun PortionEquationText(count: String, unit: PortionUnit, resolvedGrams: String) {
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
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** Provenance/verification badge for the selected countable unit, mirroring [SourceBadge]. */
@Composable
private fun PortionUnitStatusRow(unit: PortionUnit, onVerify: () -> Unit) {
    val isVerified = unit.verificationStatus == VerificationStatus.USER_VERIFIED
    val isFromOff = unit.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS

    if (!isFromOff) return // user-defined units need no provenance badge — they are simply the user's own.

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (isVerified) {
            Text(
                text = stringResource(R.string.product_verified_portion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onVerify) {
                Text(stringResource(R.string.product_online_portion), style = MaterialTheme.typography.bodySmall)
            }
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
            modifier = Modifier.fillMaxWidth(),
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
private fun ResultPanel(state: ProductUiState, settings: AppSettings) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val result = state.result

    // Resolved during composition, not inside the click lambda: reading resources off
    // LocalContext at click time is not configuration-aware and can return a stale string.
    val copiedValue = result?.let { ResultFormatter.clipboardValue(it, settings.resultStyle) }
    val copiedMessage = copiedValue?.let { stringResource(R.string.product_copied, it) }

    val panelShape = RoundedCornerShape(topStart = Space.xl, topEnd = Space.xl)

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
            .padding(horizontal = Space.screenEdge, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        // Announced as a live region so TalkBack reads the new result as the
                        // portion changes, instead of leaving a blind user to hunt for it (§39).
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text) }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
    ) { Text(text) }
}
