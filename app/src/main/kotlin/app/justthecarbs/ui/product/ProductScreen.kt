package app.justthecarbs.ui.product

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import app.justthecarbs.ui.components.JtcFilterChip
import app.justthecarbs.ui.components.JtcValueButton
import app.justthecarbs.ui.components.ProductIdentityRow
import app.justthecarbs.ui.components.ProductGalleryDialog
import app.justthecarbs.ui.components.CopyResultButton
import app.justthecarbs.ui.components.ResultValue
import app.justthecarbs.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LookupError
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductImageSelector
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.components.FavoriteButton
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.PrimaryAction
import app.justthecarbs.ui.components.RecoveryPanel
import app.justthecarbs.ui.components.SecondaryAction
import app.justthecarbs.ui.components.SourceBadge
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.components.WrappingRow
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.ui.meal.MealActions
import app.justthecarbs.ui.meal.MealBarIfPresent
import app.justthecarbs.ui.meal.StaleMealDialog
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/** Stable handle for the inline portion-unit correction field, used by instrumented tests. */
const val PORTION_CORRECTION_FIELD_TAG = "portion_unit_correction_amount"

/** Stable handle for the "add portion unit" form's amount field, used by instrumented tests. */
const val ADD_PORTION_UNIT_FIELD_TAG = "add_portion_unit_amount"

/** Stable handle for the dominant carbohydrate result, used by instrumented tests. */
const val PRODUCT_RESULT_TAG = "product_result"

/** Stable handle for the inline Verify action beside the per-100 figure, used by instrumented tests. */
const val PRODUCT_VERIFY_INLINE_TAG = "product_verify_inline"

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
    onSetPortion: (BigDecimal) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onVerifyByTyping: () -> Unit = {},
    onDismissVerify: () -> Unit,
    onConfirmVerification: (BigDecimal, app.justthecarbs.domain.NutritionBasis, String) -> Unit,
    onResetOnline: () -> Unit,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit = {},
    /** Straight back to the barcode camera from *Product not found*, without passing through Home. */
    onScanAgain: () -> Unit = {},
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
    onSwitchToGrams: () -> Unit = {},
    onSwitchToPortionUnit: (Long) -> Unit = {},
    onCountChanged: (String) -> Unit = {},
    onShowAddPortionUnitForm: (Boolean) -> Unit = {},
    onAddPortionUnit: (PortionUnitKind, PortionConversion, String?) -> Unit = { _, _, _ -> },
    onVerifyPortionUnit: () -> Unit = {},
    onApplyNewerRemotePortionUnit: () -> Unit = {},
    onDismissNewerRemotePortionUnit: () -> Unit = {},
    onCorrectPortionUnit: (PortionConversion) -> Unit = {},
    onCancelPortionUnitCorrection: () -> Unit = {},
    /**
     * Takes the portion in the user's own words ("2 slices"), which only a composable can build, and
     * a fallback display name for a calculation that has no product name of its own.
     */
    onAddToMeal: (String, String) -> Unit = { _, _ -> },
    onAddToMealAndScanNext: (String, String) -> Unit = { _, _ -> },
    /**
     * Opens the scanner without adding anything: *Add & scan next* while the item it would add has
     * just been added (see `MealActions`).
     */
    onScanNext: () -> Unit = {},
    onOpenMeal: () -> Unit = {},
    onConfirmLabelMatch: () -> Unit = {},
    onUseDetectedLabelValue: (BigDecimal) -> Unit = {},
    onEditDetectedLabelValue: (BigDecimal) -> Unit = {},
    onDismissLabelVerdict: () -> Unit = {},
    onDismissLabelHandoffFailure: () -> Unit = {},
    onSelectUsualPortion: (PortionUsage) -> Unit = {},
    /** Open or close the *Save product* form. Only reachable on an unsaved quick calculation. */
    onShowSaveQuickCalculation: (Boolean) -> Unit = {},
    /** Persist the calculation on screen under this name (1.0.3 P1). */
    onSaveQuickCalculation: (String) -> Unit = {},
    /** The answer to [ProductUiState.staleMeal]: true starts a new meal, false adds to the stored one. */
    onResolveStaleMeal: (Boolean) -> Unit = {},
    onDismissStaleMeal: () -> Unit = {},
) {
    var galleryOpen by remember(state.product?.barcode) { mutableStateOf(false) }
    val galleryImages = remember(state.product?.images) {
        state.product?.let(ProductImageSelector::galleryImages).orEmpty()
    }

    // The system back gesture and the toolbar back button must persist the same way (P0 §3). Without
    // this, only the toolbar's `IconButton` called `onBack` — the system gesture went straight to
    // Compose Navigation's default handling, so `rememberUsageAndAwait()` never ran on a gesture
    // exit, which is the far more common way to leave a screen on a modern device.
    //
    // Deliberately not migrated to PredictiveBackHandler in the 2026-09-14 interaction pass — on the
    // barcode-product route, `onBack` awaits `viewModel.rememberUsageAndAwait()` (a suspend Room
    // write) before popping, precisely so the pop cannot destroy the ViewModel and cancel that write
    // mid-flight. That is a real cleanup ordering dependency between an async write and navigation;
    // see docs/superpowers/specs/2026-09-14-interaction-polish-design.md.
    BackHandler(onBack = onBack)

    if (galleryOpen && state.product != null && galleryImages.isNotEmpty()) {
        ProductGalleryDialog(
            productName = state.product.name,
            images = galleryImages,
            onDismiss = { galleryOpen = false },
        )
    }

    state.staleMeal?.let { staleMeal ->
        StaleMealDialog(
            staleMeal = staleMeal,
            onStartNewMeal = { onResolveStaleMeal(true) },
            onAddToMeal = { onResolveStaleMeal(false) },
            onDismiss = onDismissStaleMeal,
        )
    }

    if (state.showVerifyDialog && state.product != null) {
        VerifyDialog(
            product = state.product,
            onConfirm = onConfirmVerification,
            onDismiss = onDismissVerify,
        )
    }

    // Saving a quick calculation asks for the one thing it genuinely needs, at the one moment it
    // needs it (1.0.3 P1). A dialog rather than a screen, so the result stays visible behind it and
    // cancelling returns to a calculation that never went anywhere.
    if (state.showSaveQuickCalculationForm && state.product != null) {
        SaveQuickCalculationDialog(
            nameError = state.quickSaveNameError,
            saving = state.savingQuickCalculation,
            onSave = onSaveQuickCalculation,
            onDismiss = { onShowSaveQuickCalculation(false) },
        )
    }

    // A label reading was handed back with an unresolved basis or an unparsable value (§5) and was
    // discarded rather than guessed. Said out loud rather than silently swallowed — nothing else on
    // screen changes, so without this the tap that started the comparison would look like it did
    // nothing at all.
    if (state.labelHandoffFailed) {
        AlertDialog(
            onDismissRequest = onDismissLabelHandoffFailure,
            modifier = Modifier.jtcDialogOutline(),
            shape = JtcDialogDefaults.shape,
            containerColor = JtcDialogDefaults.containerColor,
            iconContentColor = JtcDialogDefaults.iconContentColor,
            titleContentColor = JtcDialogDefaults.titleContentColor,
            textContentColor = JtcDialogDefaults.textContentColor,
            tonalElevation = JtcDialogDefaults.tonalElevation,
            confirmButton = {
                TextButton(onClick = onDismissLabelHandoffFailure) {
                    Text(stringResource(R.string.ocr_confirm))
                }
            },
            text = { Text(stringResource(R.string.label_handoff_failed)) },
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // No backdrop motif here. It lives on Home only (2026-09-22 visual pass): on this screen
        // it sat behind the top bar's trailing controls, and decoration may not share a level with
        // a control. The product's name in the top bar is what identifies the screen.

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                state.loading -> LoadingBody(
                    barcode = state.barcode,
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                )
                state.failure != null -> FailureBody(
                    failure = state.failure,
                    barcode = state.barcode,
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                    onRetry = onRetry,
                    onSearch = onSearch,
                    onScanAgain = onScanAgain,
                )
                state.product != null -> CalculatorBody(
                    product = state.product,
                    state = state,
                    settings = settings,
                    onPortionChanged = onPortionChanged,
                    onSetPortion = onSetPortion,
                    onApplyNewerRemote = onApplyNewerRemote,
                    onDismissNewerRemote = onDismissNewerRemote,
                    onSwitchToGrams = onSwitchToGrams,
                    onSwitchToPortionUnit = onSwitchToPortionUnit,
                    onCountChanged = onCountChanged,
                    onShowAddPortionUnitForm = onShowAddPortionUnitForm,
                    onAddPortionUnit = onAddPortionUnit,
                    onVerifyPortionUnit = onVerifyPortionUnit,
                    onVerify = onVerify,
                    onVerifyByTyping = onVerifyByTyping,
                    onApplyNewerRemotePortionUnit = onApplyNewerRemotePortionUnit,
                    onDismissNewerRemotePortionUnit = onDismissNewerRemotePortionUnit,
                    onCorrectPortionUnit = onCorrectPortionUnit,
                    onCancelPortionUnitCorrection = onCancelPortionUnitCorrection,
                    onAddToMeal = onAddToMeal,
                    onAddToMealAndScanNext = onAddToMealAndScanNext,
                    onScanNext = onScanNext,
                    onOpenMeal = onOpenMeal,
                    onSelectUsualPortion = onSelectUsualPortion,
                    onShowSaveQuickCalculation = onShowSaveQuickCalculation,
                    onOpenGallery = { galleryOpen = true }.takeIf { galleryImages.isNotEmpty() },
                )
            }
        }
    }
}

/**
 * Product's own top bar — deliberately not [app.justthecarbs.ui.components.JtcTopBar].
 *
 * Sharing the *visual system* is not the same as sharing the *component*, and forcing this screen
 * onto `JtcTopBar` as written costs the two things that were fixed here on purpose: a product name
 * can run to two lines (`maxLines = 2`, see the comment below) where `JtcTopBar` is hard-locked to
 * one, and this bar's height is intrinsic to its content rather than a fixed 64dp — a two-line name
 * at a large font scale needs to grow the bar, not clip inside it. Rendering both forms at 2x font
 * scale on a 320dp width showed the shared 64dp bar clipping a two-line title mid-glyph; the
 * intrinsic-height bar simply grew.
 *
 * What *is* shared, so the screen still reads as one system: the back icon's ordinary-ink tint and
 * the horizontal spacing around the title.
 *
 * **No destination marker (2026-09-23 calculator refinement).** The three coloured bars that sat
 * between the back arrow and the name read as a small bar chart -- a "stats" signal on a screen
 * whose only job is one answer -- and they were a second accent colour on the calculator for no
 * information the name does not already give. The name says where you are.
 */
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
            .padding(vertical = Space.xs)
            .padding(start = Space.s, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.product_back),
                // Ordinary foreground ink, matching JtcTopBar's back-arrow rule exactly.
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            // A scanned label states a carbohydrate figure, not a product name — so an unnamed
            // product is the ordinary state of a quick calculation, not a missing field. The title
            // says what the screen *is* rather than leaving a blank where a name would go, which
            // reads as a record that failed to load.
            text = product?.name?.ifEmpty { stringResource(R.string.quick_title) }.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            // Home already ellipsised its product names and this did not, so the same long name was
            // cut mid-character here and cleanly on the previous screen. An ellipsis also tells the
            // user the name continues, which a hard clip leaves them to infer.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.xs)
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
private fun LoadingBody(barcode: String, onScanLabel: () -> Unit, onEnterManually: () -> Unit) {
    // A lookup still running after a few seconds is a slow connection or a stalled one, and a
    // spinner alone left the user nothing to do but wait (2026-09-24 UX review). The line then says
    // so and the failure screen's own recoveries appear under it. The lookup keeps running: if it
    // lands, the calculator replaces this screen as before. Keyed on the barcode so a new lookup
    // starts its own wait.
    var stalled by remember(barcode) { mutableStateOf(false) }
    LaunchedEffect(barcode) {
        delay(STALLED_LOOKUP_MS)
        stalled = true
    }
    // §37: a brief, quiet loading state. Never a full-screen blocking spinner.
    //
    // The spinner now says what it is waiting for. A bare indeterminate circle is the same picture
    // whether the app is reading its own database in 20ms or waiting on a slow mobile connection to
    // Open Food Facts, and this screen is reached with the phone still pointed at a shelf — "is it
    // working, or did my scan fail?" is exactly the question that makes people re-scan. Naming the
    // work also names the recovery: if it stalls, the barcode underneath is what they can act on.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Text(
                text = stringResource(if (stalled) R.string.product_still_looking else R.string.product_finding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (barcode.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.notfound_barcode, barcode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Secondary, not primary: the lookup may still answer, and these are ways round it
            // rather than the thing this screen is for.
            if (stalled) {
                SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
                SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
            }
        }
    }
}

/** How long a lookup runs before the loading screen offers a way round it. */
private const val STALLED_LOOKUP_MS = 4_000L

@Composable
private fun FailureBody(
    failure: Failure,
    barcode: String,
    onScanLabel: () -> Unit,
    onEnterManually: () -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit = {},
    onScanAgain: () -> Unit = {},
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
) {
    val title = when (failure) {
        Failure.NotFound -> stringResource(R.string.notfound_title)
        Failure.NoUsableValue -> stringResource(R.string.unusable_title)
        Failure.UnknownBasis -> stringResource(R.string.unknown_basis_title)
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
        Failure.UnknownBasis -> stringResource(R.string.unknown_basis_body)
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
            // For a *missing* barcode the order is: scan again, read the label, search by name. The
            // most likely reason to be here is now the cheapest to undo — the scanner read a code
            // the user had not aimed at, and one tap returns to the camera. Search stays offered
            // because the product may be in the database under a different code, but it is a
            // slower recovery than simply scanning the right thing. For a network failure search is
            // pointless — the same host is down — so it is not offered there (spec §9).
            if (failure is Failure.NotFound) {
                // *Scan barcode again* is the primary recovery, and it is here because of a real
                // device failure: the scanner could accept a barcode the user had not aimed at, and
                // this screen — the place that mistake lands — offered no way back to the camera at
                // all. Recovering from a mis-scan meant navigating out to Home first. The scanner is
                // now gated (see BarcodeStabilityTracker), but the dead end was its own defect.
                PrimaryAction(text = stringResource(R.string.notfound_scan_again), onClick = onScanAgain)
                SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
                SecondaryAction(text = stringResource(R.string.search_action), onClick = onSearch)
            } else if (failure is Failure.UnknownBasis) {
                // Manual entry leads here, not the label scanner. The database already supplied a
                // carbohydrate figure; the single missing fact is whether it is per 100 g or per
                // 100 ml, and manual entry is the one screen that asks that as a visible chip.
                // Sending the user to photograph a nutrition table would make them re-read a number
                // that was never in doubt.
                PrimaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
                SecondaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
            } else {
                PrimaryAction(text = stringResource(R.string.product_scan_label), onClick = onScanLabel)
            }
            if (failure !is Failure.UnknownBasis) {
                SecondaryAction(text = stringResource(R.string.permission_manual), onClick = onEnterManually)
            }
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
    onSetPortion: (BigDecimal) -> Unit,
    onApplyNewerRemote: () -> Unit = {},
    onDismissNewerRemote: () -> Unit = {},
    onSwitchToGrams: () -> Unit = {},
    onSwitchToPortionUnit: (Long) -> Unit = {},
    onCountChanged: (String) -> Unit = {},
    onShowAddPortionUnitForm: (Boolean) -> Unit = {},
    onAddPortionUnit: (PortionUnitKind, PortionConversion, String?) -> Unit = { _, _, _ -> },
    onVerifyPortionUnit: () -> Unit = {},
    onVerify: () -> Unit = {},
    onVerifyByTyping: () -> Unit = {},
    onApplyNewerRemotePortionUnit: () -> Unit = {},
    onDismissNewerRemotePortionUnit: () -> Unit = {},
    onCorrectPortionUnit: (PortionConversion) -> Unit = {},
    onCancelPortionUnitCorrection: () -> Unit = {},
    onAddToMeal: (String, String) -> Unit = { _, _ -> },
    onAddToMealAndScanNext: (String, String) -> Unit = { _, _ -> },
    onScanNext: () -> Unit = {},
    onOpenMeal: () -> Unit = {},
    onSelectUsualPortion: (PortionUsage) -> Unit = {},
    /** Opens the *Save product* form on an unsaved quick calculation (1.0.3 P1). */
    onShowSaveQuickCalculation: (Boolean) -> Unit = {},
    /** Null when the product has no safe gallery image, which is what removes the hero's tap. */
    onOpenGallery: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    // A tap on anything that does nothing puts the keyboard away (2026-09-24 UX review). The meal
    // actions step aside while typing, so without this the only way to reach them was the
    // keyboard's Done. Buttons, chips, the field and the gallery handle their own taps and consume
    // them, so this sees only taps that landed on nothing; a drag cancels it, so scrolling the
    // portion zone never clears focus.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(focusManager) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
    ) {

        // Read from the IME inset's height rather than the experimental `isImeVisible`, which is a
        // stable API giving the same fact. Non-zero means the keyboard is taking screen space.
        //
        // Through `derivedStateOf`, so only the shown/hidden change recomposes this body. Read
        // directly, the inset's height is state that changes on every frame of the keyboard's
        // slide, and the whole calculator recomposed with it for a boolean that flips once.
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val imeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }

        val selectedUnit = state.selectedPortionUnit
        val countableActive = state.inputMode == InputMode.PORTION_UNIT && selectedUnit != null
        val portionScroll = rememberScrollState()

        // A short window with the keyboard up (2026-09-23 calculator refinement).
        //
        // Measured at 360x600dp: the keyboard, the top bar, the meal bar and the dock with its meal
        // actions left the portion zone ZERO pixels tall, so the user typed into a field they could
        // not see, and "Add & scan next" was clipped mid-word. On such a window the meal bar steps
        // aside while the keyboard is open; it is for after the typing, and returns the moment the
        // keyboard closes. (The dock's meal actions step aside while typing on every window since
        // the hero redesign; see `ResultPanel`.)
        //
        // The height is divided by the font scale, i.e. measured in lines of text rather than dp,
        // because large text squeezes the zone exactly as a short window does: at 1.8x on the
        // 411x914 phone the two-line meal bar and two-line meal buttons left the count field a
        // sliver under the dock while typing. At 1.0x the rule is unchanged.
        val shortWindow =
            LocalConfiguration.current.screenHeightDp / LocalDensity.current.fontScale <= SHORT_WINDOW_HEIGHT_DP
        val keyboardSqueeze = imeVisible && shortWindow

        // Keeps the input itself in view whenever the zone is too short for the whole group.
        //
        // The zone is bottom-anchored, but once its content is taller than the zone there is no
        // slack to anchor and it scrolls from the top -- which put the label and chips on screen and
        // the field under the dock: on arrival at 1.8x text, and on a short window with a
        // remembered portion. After the keyboard closes the dock grows back by its actions row and
        // the same thing happened to a field the user had just typed into. So on arrival and each
        // time the keyboard goes away, the field is scrolled into view if (and only if) it is not
        // already. Where everything fits there is nothing to scroll and this does nothing.
        val inputInView = remember { BringIntoViewRequester() }
        LaunchedEffect(imeVisible, countableActive) {
            if (imeVisible) return@LaunchedEffect
            // One frame, so the layout this change caused (identity row back, dock actions back)
            // has been measured before deciding whether the field needs moving.
            withFrameNanos { }
            if (portionScroll.maxValue > 0) inputInView.bringIntoView()
        }

        // The reading order is identity -> portion -> result, and it is now a fixed frame rather
        // than a stack whose first element could grow without limit.
        //
        // What changed in the 2026-09-22 visual pass, and why: the identity block used to open
        // with a photo sized at 28% of the screen height (150-280dp), which together with a
        // three-line provenance block and a dock carrying both a meal bar and a provenance
        // sentence pushed the portion field underneath the dock on the reference phone, and off
        // the screen entirely at 360x720dp or 1.3x text. Identity is now one compact row. See
        // [app.justthecarbs.ui.components.ProductIdentityRow].
        //
        // The meal strip moved OUT of the dock and up here, directly under the top bar. It is
        // status -- "there is a meal in progress" -- not part of the answer, and inside the dock
        // it competed with the result for the one elevated surface on the screen while taking
        // height from the controls above it. It sits below the bar rather than in it, so a
        // two-line product title cannot collide with it.
        if (!keyboardSqueeze) {
            MealBarIfPresent(
                itemCount = state.mealItems.size,
                total = state.mealTotal,
                onClick = onOpenMeal,
                modifier = Modifier.padding(horizontal = Space.screenEdge),
            )
        }

        // Identity: the product image with the per-100 figure, its provenance badge and the
        // Verify link, as a hero or a row (see ProductIdentityRow). A quick calculation has no
        // name, so ProductIdentityRow omits the image rather than rendering a monogram plate
        // derived from an empty string -- which read as a product record that had failed to load.
        //
        // OUTSIDE the scrolling zone, pinned under the top bar. While the keyboard is open it is
        // the smallest row when that fits and nothing when it does not.
        //
        // Giving way is the last height available on the tightest configuration the report names:
        // 360x720dp at 1.3x text with the IME up leaves about 390dp for the whole screen once the
        // keyboard has taken its third. With the identity row pinned, the portion field was still
        // cut through its lower edge by the dock -- measured, after the dock and the field had
        // already given back everything they could. CalculatorFrame now measures that rather than
        // assuming it: the portion zone takes its height first, and the identity only what is left.
        //
        // It is the right thing to drop, and this screen already applies the same rule to the
        // badge hint, the provenance line and the save action. Identity answers "is this the right
        // product?", which is a question the user has already answered by the time they are typing
        // a portion into it; the name stays in the top bar throughout, so nothing that identifies
        // the product actually leaves the screen. It returns the instant the keyboard closes.
        //
        // It was briefly inside it, and centring the zone's contents then floated the whole
        // identity block 200px down the page, detached from the title it belongs to -- a worse
        // defect than the trailing gap the centring was fixing. Identity is the answer to "is this
        // the right product?", which is a question about the top of the screen; the portion group
        // is what may float. It is short and fixed-height, so keeping it out of the scroll costs
        // nothing even at a large font scale.
        CalculatorFrame(
            reserveIdentity = !imeVisible,
            modifier = Modifier.weight(1f),
            identity = {
                ProductIdentityRow(
                    product = product,
                    modifier = Modifier.padding(
                        start = Space.screenEdge,
                        end = Space.screenEdge,
                        top = Space.s,
                    ),
                    onOpenGallery = onOpenGallery,
                    // The spare height of a tall screen goes to the product photo, in fixed steps,
                    // once the portion controls below have taken what they need. See
                    // ProductIdentityRow and CalculatorFrame.
                    allowHero = true,
                    // While typing: the smallest row when it fits, and nothing when it does not.
                    // It used to be hidden outright, which on a tall phone left ~140dp of empty
                    // page over the field; a short window or a large font scale still has no room
                    // for it and hides it exactly as before.
                    keyboardOpen = imeVisible,
                ) {
                    ProductSummary(
                        product = product,
                        compact = imeVisible,
                        onVerify = onVerify,
                        onVerifyByTyping = onVerifyByTyping,
                    )
                }
            },
        ) {
        Column(
            modifier = Modifier
                .fadeOutWhenMoreBelow(portionScroll)
                .verticalScroll(portionScroll),
            // The zone is only as tall as its contents when they fit, and CalculatorFrame rests it
            // on the dock, so there is no slack inside it to arrange. When the contents are taller
            // than the room, it is the room's height and scrolls from the top.
            verticalArrangement = Arrangement.Bottom,
        ) {

        // Correction #5/#10: a newer online figure is offered, never imposed. The calculation the
        // user is looking at does not move unless they say so.
        state.newerRemoteCarbs?.let { newer ->
            RemoteChangedNotice(
                newerCarbs = newer,
                unit = (state.newerRemoteBasis ?: product.basis).unitLabel,
                onApply = onApplyNewerRemote,
                onDismiss = onDismissNewerRemote,
                modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.s),
            )
        }

        // The product identity and controls share this scrolling space. The equation and result
        // stay in the pinned panel below; scrolling a long product never steals their height.
        Column(modifier = Modifier.padding(horizontal = Space.screenEdge)) {
            Spacer(Modifier.height(Space.m))

            // The portion group has a label, and the mode chips share its line.
            //
            // This replaces a centred `How much are you eating?` sentence, which did a field
            // label's job in a full line of centred bodyLarge -- the only centred body text on the
            // screen. The label plus the field's own greyed `0` placeholder say the same thing in
            // a quarter of the height, and reclaiming that height is part of what makes the
            // portion control a first-class object again rather than an afterthought under a photo.
            //
            // The SAME eyebrow as the dock's `CARBS` label -- `labelSmall`, muted, upper case from
            // the string -- and that sameness is the point (2026-09-23). As `labelLarge` in ink it
            // read as a section heading while `CARBS` read as a caption, so the two numbers on this
            // screen did not present as a labelled pair; matched eyebrows make `PORTION 65 g` and
            // `CARBS 37.4 g` read as question and answer, the way Home's Recent card already pairs
            // them.
            //
            // A `FlowRow`, not a `Row` (2026-09-23 calculator refinement). The chips used to share a
            // `Row` with the label after a weighted spacer, and a `Row` never wraps: at 1.8x text a
            // custom unit's chip was squeezed until "Generous tablespoon heaped" broke mid-word
            // ("tablespoo" / "n heaped") and `PORTION` was pressed against the first chip. Now the
            // chips stay beside the label while they fit and move, as one group, to the line under
            // it when they do not. `SpaceBetween` keeps them at the end of the label's line in the
            // ordinary case, which is exactly where they were.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.product_portion_group_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Space.s),
                )
                // Only rendered when countable units genuinely exist (§11 of the
                // countable-portions brief) -- a product with none keeps a single field.
                if (state.portionUnits.isNotEmpty()) {
                    PortionModeRow(
                        units = state.portionUnits,
                        selectedUnitId = state.selectedPortionUnitId,
                        isGramsSelected = state.inputMode == InputMode.GRAMS,
                        basis = product.basis,
                        onSelectGrams = onSwitchToGrams,
                        onSelectUnit = onSwitchToPortionUnit,
                    )
                }
            }

            Spacer(Modifier.height(Space.s))
            if (countableActive) {
                // The countable branch has no adjust or pack rows to group the shortcuts with, so
                // Usual stays above the field here -- it is the only alternative to typing on this
                // path, and below the field it would sit under the status row instead of beside
                // the input it fills.
                if (state.usualPortions.isNotEmpty()) {
                    UsualPortionRow(
                        usages = state.usualPortions,
                        units = state.portionUnits,
                        basisUnit = product.portionUnit,
                        entered = PortionParser.parse(state.countText),
                        enteredUnitId = selectedUnit?.id,
                        onSelect = onSelectUsualPortion,
                    )
                    Spacer(Modifier.height(Space.s))
                }
                Box(Modifier.bringIntoViewRequester(inputInView)) {
                    CountField(value = state.countText, unit = selectedUnit, onValueChange = onCountChanged)
                }
                Spacer(Modifier.height(Space.s))
                PortionUnitStatusRow(
                    unit = selectedUnit,
                    onVerify = onVerifyPortionUnit,
                    onCorrect = onCorrectPortionUnit,
                    correcting = state.correctingPortionUnit,
                    onCancelCorrection = onCancelPortionUnitCorrection,
                )
                state.newerRemotePortionUnit?.let { newer ->
                    Spacer(Modifier.height(Space.s))
                    PortionUnitChangedNotice(
                        newerConversion = newer,
                        unit = selectedUnit,
                        onApply = onApplyNewerRemotePortionUnit,
                        onDismiss = onDismissNewerRemotePortionUnit,
                    )
                }
            } else {
                Box(Modifier.bringIntoViewRequester(inputInView)) {
                    PortionField(
                        value = state.portionText,
                        unit = product.portionUnit,
                        onValueChange = onPortionChanged,
                        // Opens the keyboard on arrival for a quick calculation, and only then.
                        //
                        // That screen exists to answer one question and has exactly one input: the user
                        // has just photographed a label, confirmed the figure, and the single remaining
                        // act is typing how much they are eating. Making them tap a field that is the
                        // only thing on the screen to tap is a step with no decision in it.
                        //
                        // Deliberately **not** applied to a saved product. There the field usually
                        // arrives pre-filled with the remembered portion, and the *Usual* shortcuts and
                        // pack buttons are alternatives to typing at all — opening the keyboard would
                        // cover the very shortcuts that make a repeat visit fast, to offer an edit the
                        // user may not want. `state.portionText.isEmpty()` guards the case where a quick
                        // calculation is revisited with a portion already typed (a rotation, or coming
                        // back from the meal), so focus is claimed once on arrival and never stolen back
                        // mid-session.
                        autoFocus = state.unsaved && state.portionText.isEmpty(),
                        compact = imeVisible,
                    )
                }
                MoreThanThePackHint(
                    portionText = state.portionText,
                    pack = product.packageAmount,
                    unit = product.portionUnit,
                )

                // Usual portions, immediately under the field they fill.
                //
                // They used to sit ABOVE the input, on the stated ground that an alternative to
                // typing must be seen before the user starts. That reasoning held when the input
                // was preceded by a photo and a centred question; it does not survive the new
                // order, where the label already announces the group and the shortcuts read as
                // "or one of these" beside the pack row they are typographically identical to.
                // Grouping the shortcut rows together is what removes the interleaving of labels,
                // fields and buttons that made this zone feel like a form.
                //
                // Still absent entirely until a portion has been used twice, and still never
                // pre-selected -- a tap sets the portion, and without a tap the field is untouched.
                if (state.usualPortions.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    UsualPortionRow(
                        usages = state.usualPortions,
                        units = state.portionUnits,
                        basisUnit = product.portionUnit,
                        entered = PortionParser.parse(state.portionText),
                        enteredUnitId = null,
                        onSelect = onSelectUsualPortion,
                    )
                }

                // The generic +/- adjust row is gone (2026-09-22 refinement pass).
                //
                // Four arithmetic buttons sat between the field and the pack shortcuts, and they
                // were the densest thing in a zone whose whole job is "type a number". They said
                // nothing about *this* product -- a scaled step is still an arbitrary numeric
                // template -- while the two rows that remain both name something real: a portion
                // the user has actually eaten, and a fraction of the package in their hand. With
                // the row removed the field, its shortcuts and the result sit within one screen of
                // each other again.
                //
                // `ProductViewModel.adjustPortion` is deliberately left in place: it is portion
                // arithmetic with its own JVM coverage, not presentation, and this pass does not
                // touch calculation APIs.

                // Only offered when the package size was read confidently. A guessed pack size
                // would be a wrong portion presented as a shortcut (§14, §13).
                product.packageAmount?.let { pack ->
                    Spacer(Modifier.height(Space.s))
                    PackShortcuts(
                        pack = pack,
                        onSetPortion = onSetPortion,
                        entered = PortionParser.parse(state.portionText),
                    )
                }
            }

            if (product.barcode.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                AddPortionUnitAction(
                    expanded = state.showAddPortionUnitForm,
                    basisUnit = product.portionUnit,
                    productBasis = product.basis,
                    onExpand = { onShowAddPortionUnitForm(true) },
                    onCancel = { onShowAddPortionUnitForm(false) },
                    onSave = { kind, conversion, label -> onAddPortionUnit(kind, conversion, label) },
                )
            }

            // Saving is optional and secondary, and its placement says so (1.0.3 P1).
            //
            // In the scrolling zone rather than in the pinned result panel, and that is deliberate
            // rather than incidental: that panel is welded to the bottom edge, so everything it
            // renders is height taken from the controls above it. Four separate defects in this
            // area have come from growing it — the meal bar three times and the provenance line
            // once, the last of which put the quick-adjust row physically underneath the panel so
            // "+10" silently did nothing. A save affordance is exactly the kind of thing that
            // "obviously belongs next to the result", and it does not.
            //
            // Hidden while the keyboard is open, for the same reason the provenance line is: the
            // user is typing a portion to get a number, and an unrelated action competing for that
            // moment is noise. It reappears the instant they stop.
            if (state.unsaved && !imeVisible) {
                Spacer(Modifier.height(Space.s))
                SaveQuickCalculationAction(
                    saving = state.savingQuickCalculation,
                    failed = state.quickSaveFailed,
                    onClick = { onShowSaveQuickCalculation(true) },
                )
            }

            // Trailing room between the last control and the dock. With the group anchored to the
            // bottom of the zone this is the visible gap between the input column and the answer
            // surface at every size where the content fits, so it is one spacing step, not the
            // 32dp the top-anchored layout needed to scroll the last row clear of the dock's
            // shadow. When the content overflows, the fade still says "more below".
            Spacer(Modifier.height(Space.m))
        }
        }
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

        // The meal line's name, for a calculation that has none. Supplied here rather than in the
        // ViewModel for the same reason the portion wording is: it lives in resources. A saved
        // product ignores it — its own name always wins.
        val mealFallbackName = stringResource(R.string.quick_title)

        ResultPanel(
            state = state,
            settings = settings,
            equationUnit = selectedUnit.takeIf { countableActive },
            imeVisible = imeVisible,
            onAddToMeal = { onAddToMeal(portionDescription, mealFallbackName) },
            onAddToMealAndScanNext = { onAddToMealAndScanNext(portionDescription, mealFallbackName) },
            onScanNext = onScanNext,
            onOpenMeal = onOpenMeal,
        )
    }
}

/**
 * The room between the meal bar and the dock: the product identity at the top, the portion zone
 * resting on the dock, and the portion zone measured first.
 *
 * The portion zone is the calculator, so it takes its height before anything else: its whole
 * contents when they fit, the room less [reserveIdentity]'s floor (the identity row) when they do
 * not, in which case it scrolls. The identity then gets everything left, which is how a tall
 * screen's spare height becomes a larger product photo instead of empty page (2026-09-23 hero
 * redesign; the identity picks one of its fixed photo sizes from that height). A photo can
 * therefore never push the portion field under the dock: it is sized from what the field left.
 *
 * Replaces a weighted scrolling `Column` with `Arrangement.Bottom` and the identity pinned above
 * it, which put the same slack under the identity row as empty page -- about 250dp on a 412dp
 * phone before a portion was typed. What that arrangement got right is kept: the portion group
 * rests on the dock, so the input sits over its answer as one column in thumb reach, and a
 * shorter group leaves slack above it, never between the field and the answer. Two alternatives
 * measured on the device before it, and still wrong: `weight(1f, fill = false)` on the zone or a
 * weighted sibling spacer both unpin the dock from the bottom edge.
 */
@Composable
private fun CalculatorFrame(
    reserveIdentity: Boolean,
    identity: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    zone: @Composable () -> Unit,
) {
    Layout(contents = listOf(identity, zone), modifier = modifier) { (identityMeasurables, zoneMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val identityMeasurable = identityMeasurables.firstOrNull()
        val reserved = if (reserveIdentity && identityMeasurable != null) {
            identityMeasurable.minIntrinsicHeight(width).coerceAtMost(height)
        } else {
            0
        }
        val zonePlaceable = zoneMeasurables.single().measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = (height - reserved).coerceAtLeast(0)),
        )
        val identityPlaceable = identityMeasurable?.measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = (height - zonePlaceable.height).coerceAtLeast(0)),
        )
        layout(width, height) {
            identityPlaceable?.place(0, 0)
            zonePlaceable.place(0, height - zonePlaceable.height)
        }
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
private fun ProductSummary(
    product: Product,
    /** True while the IME is open — drops the badge's advisory line to give the room back. */
    compact: Boolean = false,
    onVerify: () -> Unit = {},
    onVerifyByTyping: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // One line that wraps: the figure, the badge and Verify (2026-09-23 hero redesign). Under a
    // hero photo the full width holds all three, so the caption costs one line; beside the row's
    // thumbnail the column is narrower and the badge wraps under the figure as it always sat. It
    // was a stacked `Column` while the badge was a two-part block (a pill plus a "Check package if
    // needed" line) that sat raggedly beside the figure; that second line is gone (see below).
    //
    // A wrapping row measures each item against the whole line and moves one that does not fit to
    // the next line, so no item is squeezed. On CI's 320dp emulator at 1.8x text a `Row` measured
    // Verify at 13dp wide, its label broken one letter per line into a 218dp-tall sliver
    // (`TouchTargetSizeTest`: "Verify = 13x218dp"), because a `Row` hands its second child
    // whatever width the first left over and never wraps.
    //
    // `WrappingRow`, not `FlowRow`: the calculator reserves the header's height from this block's
    // intrinsic height, and a `FlowRow` estimates that as if the three items shared one line.
    // See WrappingRow.
    WrappingRow(
        modifier = modifier.fillMaxWidth(),
        horizontalSpacing = Space.s,
        verticalSpacing = Space.xs,
    ) {
        // The same weight as the product name in the top bar, one step below the size it had.
        //
        // This is the figure every result on the screen derives from, and it is the one an
        // experienced user sanity-checks first — they know roughly what bread and pasta should be,
        // so a wrong database entry is usually obvious at a glance. It must stay legible and
        // SemiBold. It must not, as `titleLarge` at 24sp, be the second-heaviest text on the
        // screen: measured beside the 48sp portion and the 72sp result it was a third
        // number-with-unit competing for the eye, heading a row whose job is identity, on a screen
        // that already struggled to separate two figures (2026-09-23). At `titleMedium` it reads
        // as the row's supporting fact: photo, then figure, then provenance.
        Text(
            text = stringResource(
                R.string.product_per_100,
                // ResultFormatter.quantity, not toPlainString: a per-100 figure derived from a
                // serving declaration (6 g per 18 g -> 33.33333333) would otherwise print every
                // digit of the division. See that function.
                ResultFormatter.quantity(product.carbsPer100),
                product.portionUnit,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
        // `showHint = false`, always. The badge's second line ("Check package if needed") is
        // deleted from this screen rather than merely hidden while the keyboard is open: the
        // result dock now carries one provenance sentence at the number itself, which is where the
        // user is actually deciding whether to act on the figure. Two statements of the same fact,
        // one of them 250dp above the other, is what made this block three lines tall.
        SourceBadge(product, showHint = false)
        // Discoverable verification, not just buried in the overflow menu. Only when it is
        // actually relevant: a value the app itself never checked against the package, and not
        // user-authored (isRemoteRefreshable is exactly "not user-authored AND unverified" --
        // the same condition the app already uses to decide whether a background refresh may
        // touch this product, so this reuses an existing fact rather than inventing a new one).
        //
        // Deliberately worded and styled as a neutral action, not a warning: SourceBadge's own
        // orange-soft badge already carries the "not verified" signal, so this must not repeat
        // or escalate it.
        //
        // Still dropped while the keyboard is open. It is not height this time -- the row is
        // the badge's own height either way -- but a tap target that navigates away, sitting
        // beside a field the user is mid-keystroke in.
        if (!compact && product.isRemoteRefreshable) {
            TextButton(
                onClick = onVerify,
                contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
                modifier = Modifier
                    .heightIn(min = Space.minTouchTarget)
                    .testTag(PRODUCT_VERIFY_INLINE_TAG),
            ) {
                Text(
                    text = stringResource(R.string.product_verify_inline),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * A quiet line under the portion field when the typed amount is more than the whole package
 * (2026-09-24 review). On the emulator a typo produced 6580 g of a 400 g jar with nothing on
 * screen to say so.
 *
 * It changes no number and blocks nothing: a portion can legitimately span two packs. It only
 * names a fact the app already holds -- the package size read confidently, the same value the
 * pack shortcuts are built from -- so an unknown size never produces it. Ordinary supporting ink,
 * not the result colour and not an error colour: it is a prompt to glance, not a warning. Polite
 * live region, so TalkBack users hear it when it appears.
 */
@Composable
private fun MoreThanThePackHint(portionText: String, pack: BigDecimal?, unit: String) {
    val portion = PortionParser.parse(portionText)
    if (pack == null || portion == null || portion <= pack) return
    Text(
        text = stringResource(R.string.product_more_than_pack, ResultFormatter.quantity(pack), unit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Space.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun PortionField(
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    /** Claim focus and open the keyboard once, on arrival. See the call site for when and why. */
    autoFocus: Boolean = false,
    /**
     * True while the soft keyboard is open, which drops the field's resting height.
     *
     * The field only needs to be a large, obvious target while the user is looking for something
     * to tap. Once they are typing into it they have already found it, and the height is better
     * spent keeping the field clear of the pinned dock -- measured at 360x720dp with 1.3x text,
     * where the full-height field was cut through mid-glyph by the dock's top edge.
     *
     * The typed numeral does not change size, only the box around it.
     */
    compact: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val portionLabel = stringResource(R.string.product_portion_label, unit)
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // The same select-on-focus as the count field (2026-09-24). A returning product arrives with
    // its remembered portion, and a thumb that taps the field and types a new amount expects to
    // replace it: with the caret after `65`, typing `80` read 6580 g (measured on the emulator as
    // 3783.5 g of carbs). The composable owns the selection; the caller still owns the text.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    var hasFocus by remember { mutableStateOf(false) }

    // Requested once per screen, not once per recomposition: `Unit` as the key means a later
    // recomposition — a keystroke, a result arriving, the meal bar appearing — cannot pull focus
    // back to this field while the user is somewhere else. If `autoFocus` is false there is no
    // effect at all, so a saved product's focus behaviour is byte-for-byte what it was.
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        textStyle = NumberType.portion.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        // Decimal keypad, because portions have decimals and a full keyboard would be noise (§16).
        // Done, for the same reason as the count field: a decimal keypad has no Enter key, so
        // without it there is no in-app way to put the keyboard away.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            NumberEntryFrame(
                focused = focused,
                compact = compact,
                unit = unit,
                showPlaceholder = value.isEmpty(),
                innerTextField = innerTextField,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                // Only on the transition into focus, as the count field does: re-selecting on
                // every focused recomposition would fight the user's own caret placement.
                if (focus.isFocused && !hasFocus) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
                hasFocus = focus.isFocused
            }
            // A real label, not an empty one. This field has no visible `label`, so
            // `contentDescription = ""` left TalkBack announcing an unnamed edit box on the screen's
            // primary input — the question above it is a separate node and is not read with it.
            // The unit is included because it is the one thing about this field that changes
            // between products and it is what stops a millilitre product being typed in grams.
            .semantics { contentDescription = portionLabel },
    )
}

/**
 * The frame every large numeric entry on this screen shares: the portion field and the count
 * field (2026-09-23 hierarchy pass).
 *
 * This replaces Material's `OutlinedTextField` with a transparent resting border and a centred
 * numeral, and each of the three differences is the point:
 *
 *  - **A hairline at rest.** The fill alone was 1.05:1 against the page in Light and 1.07:1 in
 *    Dark (measured from the tokens), so a remembered `65` sat in a box nobody could see and read
 *    as a readout. `outline` is 3.3:1 in Light and 5.3:1 in Dark, the same token the value buttons
 *    already take for their edge in Dark. This is a deliberate exception to DESIGN.md's
 *    field-at-rest rule, recorded there: that rule was written for 17sp fields, and this is the one
 *    control on the screen whose value is displayed at headline size next to another headline
 *    number. Focus still promotes the edge to a 2dp `primary` stroke, so "this one is live" is
 *    unchanged.
 *  - **The unit sits beside the number,** on its baseline, the way a form value reads -- not 350px
 *    away at the far edge of the box as a `suffix`. `65 g` is then one object, which is what the
 *    dock's `37.4 g` already is.
 *  - **Left-aligned,** like every other line on the screen, so the input and the answer share one
 *    column and differ by kind (a box with a hairline versus an elevated surface), by weight
 *    (SemiBold versus Bold), by scale (48sp versus 72sp) and by hue -- not by one of them alone.
 *
 * Purely a decoration box: the text field's own focus, IME action and semantics are unchanged,
 * and a tap anywhere inside the frame focuses the field because the frame is laid out inside the
 * field's node.
 */
@Composable
private fun NumberEntryFrame(
    focused: Boolean,
    compact: Boolean,
    unit: String,
    showPlaceholder: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Space.buttonRadius)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A floor, not a fixed height: 80dp holds the 48sp numeral comfortably at the default
            // scale, and `heightIn` lets the frame grow rather than clip the digits at 1.3x/1.8x.
            .heightIn(min = if (compact) PORTION_FIELD_HEIGHT_COMPACT else PORTION_FIELD_HEIGHT)
            .background(
                color = if (focused) colors.surfaceContainerLowest else colors.surfaceContainerLow,
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.primary else colors.outline,
                shape = shape,
            )
            .padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.CenterStart,
    ) {
        NumberBesideUnit(
            number = {
                // `IntrinsicSize.Min`: the inner text field otherwise takes every pixel it is
                // offered, which put the unit back at the far edge of the frame -- measured on the
                // first build, `g` at x=923 of a 1080px window beside a two-digit portion.
                Box(modifier = Modifier.width(IntrinsicSize.Min)) {
                    // An empty 48sp field with a lone unit is a large blank box that does not say
                    // what goes in it. A greyed `0` in the field's own type shows the shape of the
                    // expected input without being a value: it is a placeholder, so it never becomes
                    // part of the portion and there is no pre-filled zero to delete before typing.
                    //
                    // Cleared from semantics: the field already announces itself, and leaving the
                    // placeholder readable made the *field* match text searches for values like
                    // "0.0 g", so assertions looking for the result found the input box instead.
                    if (showPlaceholder) {
                        Text(
                            text = "0",
                            style = NumberType.portion,
                            color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    innerTextField()
                }
            },
            unit = {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    // Two lines, then an ellipsis: a custom unit's name can be long ("generous
                    // tablespoon heaped"), and it wraps beside the number rather than pushing it out.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

/**
 * The number and its unit on one baseline, with the number measured first.
 *
 * A plain `Row` (what this was until 2026-09-23) measures the unit's `Text` before the weighted
 * number, and a `Text` takes all the width it wants. With a short unit that never mattered; with a
 * countable unit named "generous tablespoon heaped" at 1.8x text the unit took the whole row and
 * the typed count was laid out **zero pixels wide** -- the user could not see the number they were
 * typing, and the unit was clipped at the frame's edge besides. Measured on the emulator.
 *
 * So the order is reversed: the number takes the width it needs up to [NUMBER_WIDTH_SHARE] of the
 * frame, and the unit gets whatever is left, wrapping to a second line if it must. Both sit on the
 * number's baseline, the same rule [app.justthecarbs.ui.components.ResultValue] follows for the
 * answer. With the ordinary units ("g", "ml", "slices") this places both exactly where the `Row`
 * did.
 */
@Composable
private fun NumberBesideUnit(
    number: @Composable () -> Unit,
    unit: @Composable () -> Unit,
) {
    val gap = Space.s
    Layout(
        content = {
            number()
            unit()
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val numberPlaceable = measurables[0].measure(
            loose.copy(maxWidth = (constraints.maxWidth * NUMBER_WIDTH_SHARE).roundToInt()),
        )
        val unitPlaceable = measurables[1].measure(
            loose.copy(maxWidth = (constraints.maxWidth - numberPlaceable.width - gapPx).coerceAtLeast(0)),
        )
        // First baselines, falling back to the bottom edge when a child reports none (intrinsic
        // measurement passes do not carry alignment lines).
        fun Placeable.baseline(): Int =
            this[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: height
        val baseline = maxOf(numberPlaceable.baseline(), unitPlaceable.baseline())
        val numberY = baseline - numberPlaceable.baseline()
        val unitY = baseline - unitPlaceable.baseline()
        val height = maxOf(numberY + numberPlaceable.height, unitY + unitPlaceable.height)
        layout(numberPlaceable.width + gapPx + unitPlaceable.width, height) {
            numberPlaceable.placeRelative(0, numberY)
            unitPlaceable.placeRelative(numberPlaceable.width + gapPx, unitY)
        }
    }
}

/**
 * The most of the frame's width the number may take before it scrolls inside its field.
 *
 * Wide enough for any portion a person types ("1250.5" at 48sp is about 45% of a 360dp frame), and
 * leaves the unit at least two fifths of the row, where even a long custom name fits in two lines.
 */
private const val NUMBER_WIDTH_SHARE = 0.6f

/**
 * The portion field's resting height.
 *
 * Tall enough to be the screen's obvious input and to hold `NumberType.portion` (48sp) without
 * crowding it, and deliberately shorter than the result dock's own numeral slot -- the answer
 * outranks the input, and that ordering should hold in height as well as in type size.
 */
private val PORTION_FIELD_HEIGHT = 80.dp

/** What the field shrinks to while the keyboard is open. See [PortionField]'s `compact`. */
private val PORTION_FIELD_HEIGHT_COMPACT = 64.dp

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
internal fun PackShortcuts(
    pack: BigDecimal,
    onSetPortion: (BigDecimal) -> Unit,
    /**
     * The portion the field holds, if it parses: the shortcut equal to it is marked selected. By
     * value (`compareTo`), since the field holds `125` where the quarter is `125.00`.
     */
    entered: BigDecimal? = null,
) {
    // Scale 2 with HALF_UP: a 355 ml can quartered is 88.75 ml, and truncating to a whole number
    // would silently change the portion the user asked for.
    val fractions = listOf(
        R.string.product_quarter_pack to pack.divide(BigDecimal(4), 2, RoundingMode.HALF_UP),
        R.string.product_half_pack to pack.divide(BigDecimal(2), 2, RoundingMode.HALF_UP),
        R.string.product_full_pack to pack,
    )

    // THE LABELS WRAP RATHER THAN TRUNCATE, AND THE ROW ITSELF IS UNCHANGED.
    //
    // The three equal `weight(1f)` buttons stay: at every ordinary size they are the compact
    // 3-across row they have always been, and the arrangement is byte-for-byte what it was. What
    // changed is one line in `JtcValueButton` -- `maxLines` 1 -> 2 -- so a label that no longer
    // fits its third of the width takes a second line instead of being clipped.
    //
    // The defect this fixes was measured, not reported by eye: at 1.8x on a 320dp window -- the
    // narrowest the app supports, at an ordinary accessibility setting -- `Full pack` rendered as
    // `Full`, and `¼ pack` overflowed too (238.5px of text into 202px of button). A shortcut that
    // silently loses half its name is worse than one that takes a second line, because `Full` and
    // `½ pack` then read as the same kind of thing.
    //
    // A `FlowRow` that wraps the BUTTONS to a 2+1 arrangement was built and measured first, and it
    // is NOT what ships: with `weight(1f)` every item still shares one line, so it never wrapped
    // and added an experimental API for nothing; and sizing the buttons to `IntrinsicSize.Max`
    // instead gave each Text exactly its own intrinsic width with no slack, which still reported
    // overflow (239px of text in a 239px box). Wrapping the text is the smaller and more robust
    // fix, and it needs no breakpoint, no device width and no font-scale threshold.
    //
    // Nothing is dropped, renamed or shrunk below the design system's own type, and the touch
    // target only ever grows -- `JtcValueButton` sets a minimum height, not a fixed one.
    //
    // `height(IntrinsicSize.Min)` on the row plus `fillMaxHeight()` on each button is what keeps
    // the three the SAME height once one of them wraps. Without it the first screenshot of the
    // wrapped state showed `Full pack` standing 92px taller than its two neighbours in the same
    // row (226px against 134px at 1.8x) -- three shortcuts that no longer read as one control
    // group. The row now measures to its tallest child and the other two stretch to match. At
    // every size where nothing wraps all three already agree, so this changes nothing there.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        fractions.forEach { (label, amount) ->
            // Same treatment as the adjust row: these set a portion, they are not actions.
            JtcValueButton(
                text = stringResource(label),
                onClick = { onSetPortion(amount) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                selected = entered != null && amount.compareTo(entered) == 0,
            )
        }
    }
}

/**
 * Fades the bottom edge of a scrolling area while there is more content below it.
 *
 * The portion zone ends at the pinned result panel, so an element that happens to straddle that
 * boundary is drawn cut in half — legible and severed, which reads as a rendering fault rather than
 * as a hint to scroll. At the default font scale nothing overflows and this is inert; from 1.3x it
 * is what tells the user there is more.
 *
 * `DstIn` with an alpha ramp, so the content's own pixels fade to transparent and whatever the
 * screen's background happens to be shows through — the alternative, painting a solid-to-transparent
 * gradient over the top, needs to know the background colour and would smear a wrong one across the
 * content in the other theme.
 *
 * [FADE_HEIGHT] is deliberately shorter than a line of text: enough to make the cut read as a fade,
 * not so much that a control resting at the boundary becomes unreadable.
 */
private fun Modifier.fadeOutWhenMoreBelow(scroll: ScrollState): Modifier = this
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        val fade = FADE_HEIGHT.toPx().coerceAtMost(size.height)
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
        // The same fade at the top edge. Now that the group rests on the dock, a focused field
        // that overflows the zone is scrolled into view from BELOW the zone's top -- with the
        // keyboard open on a 411dp phone the mode chips were cut mid-glyph under the meal bar.
        // Inert while there is nothing above, exactly as the bottom fade is inert at the default
        // scale.
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = fade,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** How far the bottom of a scrolling zone fades out. Shorter than a line, so nothing is hidden. */
private val FADE_HEIGHT = 20.dp

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
    /** The amount the active field holds (grams, or a count), for marking the matching shortcut. */
    entered: BigDecimal?,
    /** The unit that count is of, or null while the field is in grams. */
    enteredUnitId: Long?,
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
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            // One third of the row per shortcut, however many there are (2026-09-23 calculator
            // refinement). With a single usual portion the one button used to stretch across the
            // whole width, where it read as a second field or a wide primary button rather than as
            // one of the pack row's siblings directly beneath it. Empty slots are plain weighted
            // space, so one, two and three shortcuts all share the pack row's column grid.
            val slots = maxOf(usages.size, USUAL_SLOTS)
            usages.forEach { usage ->
                val unit = usage.portionUnitId?.let { id -> units.firstOrNull { it.id == id } }
                val amount = ResultFormatter.editable(usage.amount)
                val label = if (unit != null) {
                    val quantity = usage.amount.toInt()
                    stringResource(R.string.product_usual_count, amount, unit.unitLabel(quantity))
                } else {
                    stringResource(R.string.product_usual_grams, amount, basisUnit)
                }

                JtcValueButton(
                    text = label,
                    onClick = { onSelect(usage) },
                    modifier = Modifier.weight(1f),
                    // The same amount of the same unit: "2 slices" is not 2 g. By value, so a typed
                    // `65.0` is the usual 65 g.
                    selected = usage.portionUnitId == enteredUnitId &&
                        entered != null && usage.amount.compareTo(entered) == 0,
                )
            }
            repeat(slots - usages.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The pack row's column count, which the usual row shares so the two read as one grid. */
private const val USUAL_SLOTS = 3

/**
 * Grams | <one chip per countable unit> (countable-portions brief §9, §11). Only rendered when
 * the product has at least one countable unit — a plain product keeps today's single field.
 */
@Composable
private fun PortionModeRow(
    units: List<PortionUnit>,
    selectedUnitId: Long?,
    isGramsSelected: Boolean,
    /** Names the basis chip: a millilitre product's chip said "Grams" beside a field asking for ml. */
    basis: NutritionBasis,
    onSelectGrams: () -> Unit,
    onSelectUnit: (Long) -> Unit,
) {
    // Every chip carries the app's minimum touch height, as Settings' and manual entry's already
    // did. Material's FilterChip defaults to 32dp, and these are the control that decides whether
    // the number on screen means grams or slices -- the one mis-tap here changes what the result is
    // *of*, not merely its size. Measured at 84px on a 420dpi device before this.
    //
    // No longer `fillMaxWidth`: the row shares the portion group's label line and sizes to its
    // chips, so a product with one countable unit does not stretch two chips across the screen.
    //
    // A `FlowRow` for the same reason as the line it sits on: several long unit names at a large
    // font scale wrap onto a second line instead of being squeezed into each other.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        JtcFilterChip(
            selected = isGramsSelected,
            onClick = onSelectGrams,
            label = stringResource(
                when (basis) {
                    NutritionBasis.PER_100_G -> R.string.product_mode_grams
                    NutritionBasis.PER_100_ML -> R.string.product_mode_millilitres
                },
            ),
            modifier = Modifier.heightIn(min = Space.minTouchTarget),
        )
        units.forEach { unit ->
            JtcFilterChip(
                selected = !isGramsSelected && unit.id == selectedUnitId,
                onClick = { onSelectUnit(unit.id) },
                label = unit.chipLabel(),
                modifier = Modifier.heightIn(min = Space.minTouchTarget),
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
    val countLabel = stringResource(R.string.product_count_field_label, unit.unitLabel(count = 2))
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    var hasFocus by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        textStyle = NumberType.portion.copy(color = MaterialTheme.colorScheme.onSurface),
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
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        // The same frame as the portion field, with the unit's word agreeing with the count typed
        // (2026-09-23): "1 slice", "2 slices". It was always the plural, so a pre-filled `1` read
        // "1 slices". Exactly one takes the singular and anything else the plural, the rule Recents
        // already follows, so a fractional count reads "1.5 slices".
        decorationBox = { innerTextField ->
            val typed = PortionParser.parse(fieldValue.text)
            NumberEntryFrame(
                focused = focused,
                compact = false,
                unit = unit.unitLabel(
                    count = if (typed != null && typed.compareTo(BigDecimal.ONE) == 0) 1 else 2,
                ),
                showPlaceholder = fieldValue.text.isEmpty(),
                innerTextField = innerTextField,
            )
        },
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
            // Named for the same reason as the portion field, and with the unit's own word so the
            // announcement is "Number of slices" rather than an unlabelled box.
            .semantics { contentDescription = countLabel },
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
    // English pluralization: only exactly 1 is singular ("1 slice"); 0, 1.5, 2... are all plural
    // ("0 slices", "1.5 slices", "2 slices") — the equation is read as a sentence, so getting this
    // wrong reads as a typo, unlike the mode chip's fixed representative plural.
    val pluralQuantity = if (PortionParser.parse(count)?.compareTo(BigDecimal.ONE) == 0) 1 else 2
    val equation = when (val conversion = unit.conversion) {
        is PortionConversion.WeightBased -> {
            if (resolvedGrams.isBlank()) return
            stringResource(
                R.string.product_count_equation,
                count.ifBlank { "0" },
                unit.unitLabel(count = pluralQuantity),
                ResultFormatter.editable(conversion.amountPerUnit),
                conversion.basis.unitLabel,
                resolvedGrams,
            )
        }
        // No grams anywhere on this path — the user was never asked for a weight and must not be
        // shown one, so this reads "4 slices × 14.2 g carbs" and stops there.
        is PortionConversion.DirectCarbs -> stringResource(
            R.string.product_direct_carb_equation,
            count.ifBlank { "0" },
            unit.unitLabel(count = pluralQuantity),
            ResultFormatter.editable(conversion.carbsPerUnit),
        )
    }
    // Left, like every other line in the dock. It was the one centred line on a left-aligned
    // surface, which made a portion fact float over the answer instead of reading as its context.
    Text(
        text = equation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
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
    onCorrect: (PortionConversion) -> Unit = {},
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
    onSave: (PortionConversion) -> Unit,
    onCancel: () -> Unit,
) {
    val conversion = unit.conversion
    var amountText by remember(unit.id) {
        mutableStateOf(
            ResultFormatter.editable(
                when (conversion) {
                    is PortionConversion.WeightBased -> conversion.amountPerUnit
                    is PortionConversion.DirectCarbs -> conversion.carbsPerUnit
                },
            ),
        )
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
                text = when (conversion) {
                    is PortionConversion.WeightBased ->
                        stringResource(R.string.product_one_unit_equals, unit.unitLabel(count = 1))
                    is PortionConversion.DirectCarbs ->
                        stringResource(R.string.product_one_unit_contains, unit.unitLabel(count = 1))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.s))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = {
                    Text(
                        when (conversion) {
                            is PortionConversion.WeightBased -> conversion.basis.unitLabel
                            is PortionConversion.DirectCarbs ->
                                stringResource(R.string.product_unit_carbs_suffix)
                        },
                    )
                },
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
                    // A weight of zero is not a portion; zero carbs per unit legitimately is.
                    if (amount.signum() < 0) return@TextButton
                    onSave(
                        when (conversion) {
                            is PortionConversion.WeightBased -> {
                                if (amount.signum() <= 0) return@TextButton
                                PortionConversion.WeightBased(amount, conversion.basis)
                            }
                            is PortionConversion.DirectCarbs -> PortionConversion.DirectCarbs(amount)
                        },
                    )
                },
            ) { Text(stringResource(R.string.product_save_verified)) }
        }
    }
}

/** Same immutability pattern as [RemoteChangedNotice], scoped to the countable unit in use (§9). */
@Composable
private fun PortionUnitChangedNotice(
    newerConversion: PortionConversion,
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
            text = when (newerConversion) {
                is PortionConversion.WeightBased -> stringResource(
                    R.string.portion_changed_body,
                    ResultFormatter.editable(newerConversion.amountPerUnit),
                    newerConversion.basis.unitLabel,
                    unit.unitLabel(count = 1),
                )
                is PortionConversion.DirectCarbs -> stringResource(
                    R.string.portion_changed_carbs_body,
                    ResultFormatter.editable(newerConversion.carbsPerUnit),
                    unit.unitLabel(count = 1),
                )
            },
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
    productBasis: NutritionBasis,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    onSave: (PortionUnitKind, PortionConversion, String?) -> Unit,
) {
    if (!expanded) {
        TextButton(
            onClick = onExpand,
            // No horizontal inset: Material's 12dp put this label alone off the column edge that
            // the field, the shortcut rows and the dock's label all share.
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = Space.s),
            modifier = Modifier.heightIn(min = Space.minTouchTarget),
        ) {
            Text(stringResource(R.string.product_add_portion_unit), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var kind by remember { mutableStateOf(PortionUnitKind.SLICE) }
    var kindMenuOpen by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    // Which fact the user has. Only one field is ever shown, so "fill in whichever you know" needs
    // no cross-field validation — there is no second field to leave empty.
    var weightMode by remember { mutableStateOf(true) }

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
                // The kind's own word, not `kind.name` — that rendered the raw enum constant, so the
                // picker read SLICE / BISCUIT / SACHET / CUSTOM in screaming caps (§24).
                Text(kind.kindLabel())
            }
            DropdownMenu(expanded = kindMenuOpen, onDismissRequest = { kindMenuOpen = false }) {
                PortionUnitKind.entries.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.kindLabel()) },
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
        // Two ways to describe one unit: what it weighs, or what it contains. A user who knows
        // neither is not helped by a weight field they would have to guess at.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            FilterChip(
                selected = weightMode,
                onClick = { weightMode = true },
                label = { Text(stringResource(R.string.product_unit_mode_weight)) },
                modifier = Modifier.heightIn(min = Space.minTouchTarget),
            )
            FilterChip(
                selected = !weightMode,
                onClick = { weightMode = false },
                label = { Text(stringResource(R.string.product_unit_mode_carbs)) },
                modifier = Modifier.heightIn(min = Space.minTouchTarget),
            )
        }

        Spacer(Modifier.height(Space.s))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = {
                Text(
                    if (weightMode) {
                        stringResource(R.string.product_portion_unit_weighs, kind.kindLabel())
                    } else {
                        stringResource(R.string.product_portion_unit_contains, kind.kindLabel())
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = {
                Text(if (weightMode) basisUnit else stringResource(R.string.product_unit_carbs_suffix))
            },
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
                    // A zero-gram unit is not a portion; a zero-carb unit genuinely exists.
                    val conversion = if (weightMode) {
                        if (amount.signum() <= 0) return@TextButton
                        PortionConversion.WeightBased(amount, productBasis)
                    } else {
                        if (amount.signum() < 0) return@TextButton
                        PortionConversion.DirectCarbs(amount)
                    }
                    onSave(kind, conversion, customName.ifBlank { null }.takeIf { kind == PortionUnitKind.CUSTOM })
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
                ResultFormatter.editable(newerCarbs),
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
    /**
     * True while the soft keyboard is taking screen space.
     *
     * This panel is welded to the bottom edge, so anything it renders conditionally is height taken
     * from the scrolling controls above. See the provenance line below for what happens when that
     * budget is exceeded.
     */
    imeVisible: Boolean = false,
    onAddToMeal: () -> Unit = {},
    onAddToMealAndScanNext: () -> Unit = {},
    onScanNext: () -> Unit = {},
    onOpenMeal: () -> Unit = {},
) {
    // The exact figure, whichever path produced it. Reading `state.result` alone left a valid
    // direct-carb calculation showing "pending" with no Copy and no Add to meal (correction §1).
    val exact = state.exactCarbs

    // The clipboard text follows the user's configured result style, so what is pasted agrees with
    // what is on screen. `CopyResultButton` owns the copying and its confirmation; this stays here
    // because formatting a result is the screen's decision, not the button's.
    val copiedValue = exact?.let { ResultFormatter.clipboardValue(it, settings.resultStyle) }

    val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)

    // The numeral slot gives height back while the keyboard is open.
    //
    // Measured at 360x720dp with 1.3x text and the IME up: the full-height dock left the portion
    // field cut through mid-glyph by the dock's top edge -- the original P0-1 defect, in the
    // hardest configuration the report names. This surface is welded to the bottom, so every dp it
    // holds is taken from the field above it.
    //
    // Safe for the same reason the other IME-time reductions on this screen are: `resultAutoSize`
    // shrinks the numeral to fit rather than clipping it, so the figure stays whole and legible --
    // just smaller, and only while the user is producing it rather than reading it. It returns to
    // full size the instant the keyboard closes.
    //
    // Both branches read this one value, so the pending slot and the result slot cannot disagree
    // and the dock still does not change height when the first digit is typed.
    val resultSlotHeight = if (imeVisible) RESULT_SLOT_HEIGHT_COMPACT else RESULT_SLOT_HEIGHT

    // The numeral slot is only reserved once it can be needed (2026-09-23 hero redesign): with an
    // answer, or while the keyboard is open and one is being typed. At rest with nothing typed the
    // dock is the label and "Enter a portion", about 60dp shorter: the slot there held the per-100
    // figure in grey, which the identity header above already states, and the spare height goes
    // to the product image instead. The fixed slot still does its job, because the dock only has to
    // keep one height while the user types into the field resting on it, and it does: the slot is
    // there for the whole time the keyboard is up, pending or not. It appears at a moment the page
    // is moving anyway (the keyboard opening, or a shortcut tapped), and the dock's size change is
    // animated, so nothing jumps.
    val showsSlot = exact != null || imeVisible

    // Whether the provenance line renders, decided once.
    //
    // Both the padding above and the Text below read this single value: computing the condition
    // twice is how the panel's height budget and its contents drift apart, and a panel that pads
    // for a line it does not draw (or vice versa) is the bug this whole block exists to prevent.
    //
    // Only for Open Food Facts data — a MANUAL or OCR value was by definition read off the package
    // by the user, so "checked against the package" is not an open question there. Only with a
    // result, since there is nothing to qualify otherwise. Not while the IME is open, where the
    // user is typing a portion rather than deciding whether to trust the figure — the same reason
    // `SourceBadge` drops its own hint there.
    val showsProvenanceLine = exact != null &&
        !imeVisible &&
        state.product?.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Shadow + a distinct container tone. This surface was previously pure white on an
            // off-white page — a ~1% difference — so the most important element on the screen had
            // no edge at all and read as part of the background. The tone is the theme's
            // `resultDock`, which is Lowest in Light and High in Dark: one token, so the dock is
            // raised away from the page in both themes rather than sinking below it in Dark.
            .shadow(elevation = Space.resultElevation, shape = panelShape, clip = false)
            .clip(panelShape)
            .background(MaterialTheme.extendedColors.resultDock)
            .navigationBarsPadding()
            .padding(
                start = Space.screenEdge,
                end = Space.screenEdge,
                top = Space.m,
                // The one remaining give-back. The provenance line is a single sentence now, so
                // this pays for it out of the bottom padding rather than letting the surface grow.
                //
                // Four separate layout defects in this app have come from this panel growing --
                // the meal bar three times and the provenance block once, the last of which put
                // the quick-adjust row physically underneath the panel so "+10" silently did
                // nothing. The panel is welded to the bottom edge, so every dp it gains is taken
                // from the controls above it. Treat its height as a budget, never as a starting
                // point.
                bottom = if (showsProvenanceLine) Space.m else Space.l,
            )
            // Innermost, so the surface, its shadow and its padding follow the content's height
            // frame by frame: the dock grows into the answer and the meal actions, and shrinks back
            // to the compact prompt, over the standard 220ms with no overshoot.
            .animateContentSize(tween(Motion.STANDARD_MS, easing = EaseOutQuart)),
        horizontalAlignment = Alignment.Start,
    ) {
        // The meal bar is deliberately NOT here any more -- it moved to the top of the screen,
        // under the top bar. See `CalculatorBody`. It is status, not part of the answer, and this
        // is the one elevated surface on the screen.

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

        // Both figures come from `exact`, independently. Neither is derived from the other, so
        // swapping which one dominates cannot introduce a double rounding (§17).
        val dominantNumeral = exact?.let { value ->
            when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> ResultFormatter.decimal(value)
                ResultStyle.WHOLE_DOMINANT -> ResultFormatter.whole(ResultFormatter.wholeGrams(value))
            }
        }
        val resultUnit = stringResource(R.string.result_unit_grams)

        // One slot at one fixed height for both states, so the dock does not jump when the first
        // digit is typed (§16: the result area must not move under the user). `height`, not
        // `heightIn`: the numeral shrinks to fit via `resultAutoSize` rather than pushing the row
        // taller, which is what keeps the portion field from moving under the user mid-keystroke.
        //
        // Pending, the slot shows the per-100 figure the result is about to be scaled from, rather
        // than an instruction: the basis figure is the number the user is working from, and seeing
        // it here, in the result's own position at a size that reads as supporting rather than
        // final, is what makes the relationship between the two legible. Centred in the slot, so
        // with the label above and the hint below the pending dock reads as three evenly spaced
        // lines rather than a label, a hole and two lines (bottom-aligned was tried first and
        // looked like exactly that). It is deliberately NOT drawn in the result's colour or `NumberType.result`:
        // this is not a result, it is the input to one, and a per-100 figure that looked like an
        // answer would be the worst possible confusion on this screen.
        //
        // The whole slot is one `AnimatedContent` over the displayed numeral (null while pending),
        // keyed only on whether an answer exists: the answer fades in when it first exists, over
        // 120ms, and after that its digits change in place. Keyed on the numeral itself, typing
        // `125` faded through ghosts of `1` and `12` (2026-09-24). `sizeTransform = null`: both
        // states are the slot's fixed height, and a size animation would only ever be a bug made
        // visible.
        //
        // TalkBack hears the result from the wrapping Box, not from anything inside the
        // `AnimatedContent`. A polite live region speaks when a property of an existing node
        // changes, and the content inside is rebuilt on the first answer (and the slot itself only
        // exists once there is one or the keyboard is up), so a live region in there was not
        // reliably announced. The Box exists in every state; its description appears with the
        // first answer and changes with each one after. Its merged text still carries the numeral
        // and the pending preview; the copy button stays its own node.
        val accessibleResult = dominantNumeral?.let {
            stringResource(R.string.result_accessible_grams, it)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PRODUCT_RESULT_TAG)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    accessibleResult?.let { contentDescription = it }
                },
        ) {
            if (showsSlot) {
                AnimatedContent(
                    targetState = dominantNumeral,
                    contentKey = { it != null },
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(Motion.QUICK_MS)),
                            initialContentExit = fadeOut(tween(Motion.QUICK_MS)),
                            sizeTransform = null,
                        )
                    },
                    label = "result",
                    modifier = Modifier.fillMaxWidth().height(resultSlotHeight),
                ) { numeral ->
                    if (numeral == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            state.product?.let { product ->
                                Text(
                                    text = stringResource(
                                        R.string.product_result_basis_preview,
                                        // See the note on the same call in ProductSummary.
                                        ResultFormatter.quantity(product.carbsPer100),
                                        product.portionUnit,
                                    ),
                                    style = NumberType.supporting,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ResultValue(
                                dominant = numeral,
                                unit = resultUnit,
                                // Announced by the wrapping Box; see above.
                                accessibleLabel = null,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(Modifier.width(Space.s))

                            // Shared with the meal total's own copy button (see `CopyResultButton`), so
                            // the two most important numbers in the app are transferred and confirmed
                            // identically rather than by two hand-written copies of the same logic.
                            CopyResultButton(
                                value = copiedValue.orEmpty(),
                                hapticsEnabled = settings.hapticsEnabled,
                            )
                        }
                    }
                }
            }
        }

        // One supporting line in both states, at the same height: the whole-gram figure under a
        // result (legible, not a whisper -- design decision 3.2), or the hint while pending. Same
        // style for both so the dock's silhouette does not change on the first keystroke. The
        // countable path asks for a count, not a weight; naming the wrong input is a small thing
        // that makes the app look like it is not watching.
        Text(
            text = when {
                exact == null && equationUnit != null -> stringResource(R.string.product_result_pending_count)
                exact == null -> stringResource(R.string.product_result_pending)
                settings.resultStyle == ResultStyle.DECIMAL_DOMINANT -> stringResource(
                    R.string.product_result_whole,
                    ResultFormatter.whole(ResultFormatter.wholeGrams(exact)),
                )
                else -> stringResource(
                    R.string.product_result_calculated,
                    ResultFormatter.decimal(exact),
                )
            },
            style = NumberType.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Where this number came from, at the number itself.
        //
        // `SourceBadge` already states provenance, but it lives at the top of the screen and
        // drops its advisory line entirely while the keyboard is open — so at the moment the
        // user reads the result and decides whether to act on it, nothing on that half of the
        // screen says whether the underlying figure was ever checked against the package.
        //
        // Only shown for Open Food Facts data, because it is the only provenance where the
        // question is open: a manually-entered or OCR-read value was, by definition, read off
        // the package by the user. Deliberately plain text in the ordinary supporting colour —
        // no icon, no alarm hue, and nothing about doses or consequences (§25, §45).
        //
        // **Hidden while the keyboard is open, and that is load-bearing rather than tidy.**
        // This panel is pinned to the bottom edge, so every dp it gains is taken from the
        // scrolling controls above it. Adding this line unconditionally grew the panel by
        // ~40px and put its top edge at y=997 while the quick-adjust row still ended at
        // y=1102 — the row was physically underneath the panel, and a tap on "+10" hit the
        // panel instead and silently did nothing. That is the fourth time a change to this
        // surface has swallowed a control above it (see the meal-bar history), and the only
        // reason it was caught is that an instrumented test clicked the button and checked the
        // result actually moved.
        //
        // The IME-open case is also when the line is least needed: the user is typing a
        // portion, not deciding whether to trust the figure, and `SourceBadge` above already
        // drops its own hint for exactly the same reason.
        if (showsProvenanceLine) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(
                    if (state.product.isUserVerified) {
                        R.string.product_result_verified
                    } else {
                        R.string.product_result_unverified
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }

        when {
            // The meal actions step aside while the keyboard is open, on every window (2026-09-23
            // hero redesign). They are for after the typing and return the moment it closes, so
            // adding to the meal is Done and then a tap. Until then the dock reserved their ~80dp
            // row, empty and invisible, whenever the keyboard was up without a result -- so the
            // first keystroke could not grow the dock under the finger -- which read as a hole in
            // the dock. Leaving them out while typing gives the dock one height for the whole time
            // the keyboard is up, with or without a result, which is the same guarantee without the
            // hole. A short window already worked this way.
            imeVisible -> Unit
            exact != null -> {
                // Only once there is a number worth adding. Offered under the result, never in
                // place of it: the app answers a carbohydrate question first and builds a meal
                // second (§9).
                Spacer(Modifier.height(Space.m))

                MealActions(
                    onAdd = onAddToMeal,
                    onAddAndScanNext = onAddToMealAndScanNext,
                    onScanNext = onScanNext,
                    enabled = !state.addingToMeal,
                    justAdded = state.lastMealAddSucceeded,
                )
                // Reported rather than merely survived, same rule as `quickSaveFailed`: the result
                // is still on screen and still correct, so silence here reads as success and the
                // user would leave believing the item was added. `MealActions` re-enables itself
                // the moment this shows, since the failed write already released the guard — this
                // is the retry surface.
                if (state.usageSaveFailed) {
                    Text(stringResource(R.string.usage_save_failed), color = MaterialTheme.colorScheme.error)
                }
                if (state.mealAddFailed) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = stringResource(R.string.meal_add_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * At or below this window height, divided by the font scale, the calculator counts as short: the
 * keyboard plus the pinned dock would otherwise leave the portion zone no height at all (measured at
 * 600dp; 640dp is CI's emulator; and 914dp at 1.8x text). A 720dp phone at the default text size
 * keeps the ordinary behaviour.
 */
private const val SHORT_WINDOW_HEIGHT_DP = 700

/**
 * The height reserved for the result numeral, whether or not a result exists yet.
 *
 * One constant for both the pending and the calculated state, because the whole point is that the
 * dock does not change height when the first digit is typed -- the field above it must not move
 * under the user. 80dp holds the 72sp numeral with its trimmed leading; the old 96dp was sized for
 * an untrimmed line box and left a visible gap under the number.
 */
private val RESULT_SLOT_HEIGHT = 80.dp

/**
 * The numeral slot while the soft keyboard is open. See `ResultPanel` for the measurement.
 *
 * Still far larger than anything else on the screen, so the result keeps its place in the
 * hierarchy even in the worst case -- the same argument `NumberType.resultAutoSize`'s own floor
 * rests on.
 */
private val RESULT_SLOT_HEIGHT_COMPACT = 60.dp

// PrimaryAction / SecondaryAction moved to ui.components alongside RecoveryPanel, which they are
// only ever used inside -- the search screen needs the same pair for the same panel.
