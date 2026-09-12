package app.justthecarbs.ui.product

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import app.justthecarbs.ui.components.ProductHeroImage
import app.justthecarbs.ui.components.ProductGalleryDialog
import app.justthecarbs.ui.theme.Destination
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.ui.meal.MealActions
import app.justthecarbs.ui.meal.MealBarIfPresent
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent
import app.justthecarbs.ui.theme.extendedColors
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
) {
    var galleryOpen by remember(state.product?.barcode) { mutableStateOf(false) }
    val galleryImages = remember(state.product?.images) {
        state.product?.let(ProductImageSelector::galleryImages).orEmpty()
    }

    // The system back gesture and the toolbar back button must persist the same way (P0 §3). Without
    // this, only the toolbar's `IconButton` called `onBack` — the system gesture went straight to
    // Compose Navigation's default handling, so `rememberUsageAndAwait()` (or the old fire-and-forget
    // `rememberUsage()`) never ran at all on a gesture exit, which is the far more common way to
    // leave a screen on a modern device.
    BackHandler(onBack = onBack)

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
                state.loading -> LoadingBody(state.barcode)
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
 * What *is* shared, so the screen still reads as one system: the destination spine (the same 4dp
 * device Home's recent cards and every `JtcTopBar` screen use, here in [Destination.PRODUCT]'s
 * blue), the back icon's ordinary-ink tint (never the accent — same reasoning as `JtcTopBar`), and
 * the horizontal spacing around the spine and title.
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
    val accent = Destination.PRODUCT.accent()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .padding(start = Space.m, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(22.dp)
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.product_back),
                // Ordinary foreground ink, not the accent — the spine already carries the
                // destination's colour, matching JtcTopBar's back-arrow rule exactly.
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
private fun LoadingBody(barcode: String) {
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
                text = stringResource(R.string.product_finding),
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
        }
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
    onAdjust: (Int) -> Unit,
    onSetPortion: (BigDecimal) -> Unit,
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
    onAddToMeal: (String, String) -> Unit = { _, _ -> },
    onAddToMealAndScanNext: (String, String) -> Unit = { _, _ -> },
    onOpenMeal: () -> Unit = {},
    onSelectUsualPortion: (PortionUsage) -> Unit = {},
    /** Opens the *Save product* form on an unsaved quick calculation (1.0.3 P1). */
    onShowSaveQuickCalculation: (Boolean) -> Unit = {},
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

        // A quick calculation has no photo and no name, so the hero would identify nothing: the
        // monogram is derived from the name printed above it, and with no name it renders as an
        // empty coloured plate under an empty title — which reads as a product record that failed to
        // load rather than as the reading the user just took. A *saved* product with no photo still
        // gets its monogram, unchanged.
        //
        // Suppressed rather than shrunk. The height it frees is taken up by centring the portion
        // zone below — see the `verticalArrangement` there. Do not instead "fix" the resulting
        // space by unpinning the result panel (`weight(1f, fill = false)` on that zone): that was
        // tried and it leaves a strip of page beneath the panel, which is worse than the gap.
        if (product.name.isNotEmpty()) {
            ProductHeroImage(
                product = product,
                compact = imeVisible,
                onClick = onOpenGallery,
                modifier = Modifier.padding(horizontal = Space.screenEdge, vertical = Space.s),
            )
        }

        // The per-100 figure and its provenance, directly under the image they describe.
        //
        // `compact` drops the badge's advisory second line while the keyboard is open, for the same
        // reason the hero shrinks there: "Check package if needed" is guidance to read *before*
        // committing to a number, and during typing the portion field and the result need the room.
        // Nothing is hidden that the user has not already had on screen.
        ProductSummary(
            product = product,
            compact = imeVisible,
            modifier = Modifier.padding(horizontal = Space.screenEdge),
        )

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
        // Still `weight(1f)`: the zone takes the remaining height so the result panel stays welded to
        // the bottom edge. Letting this zone shrink instead (`fill = false`) does remove the gap, but
        // it unpins the panel — it then floats with a strip of page below it, which is worse than the
        // gap it fixed. Verified on the emulator, both ways.
        //
        // The dead space is removed at its source instead: the trailing spacer below now absorbs it,
        // and the no-photo hero above no longer reserves 150 dp for two letters.
        Column(
            modifier = Modifier
                .weight(1f)
                // Fades the last few dp of content into the page when, and only when, there is more
                // of it below (1.0.3 ease pass).
                //
                // **Before `verticalScroll`, and that ordering is the whole thing.** A draw modifier
                // placed after it is applied to the scrolling *content*, whose height is the full
                // scrollable extent — so the fade lands at the bottom of everything, far below the
                // screen, and nothing appears at the visible edge. Placed here it decorates the
                // viewport, which is the edge the user is actually looking at. Written the wrong way
                // round first and caught by screenshotting the device, not by reading the code.
                //
                // This zone fits without scrolling at the default font scale and overflows from
                // **1.3x** — an ordinary accessibility setting, not an extreme one. Measured there:
                // *+ Add portion unit* came to rest sliced horizontally through the middle of its
                // glyphs at the pinned panel's edge, and at 1.8x the whole quick-adjust row did.
                // Legible, and cut — which is the exact "reads as a rendering fault rather than as
                // more content below" failure the trailing spacer below already names. That spacer
                // fixes the *scrolled-to-the-bottom* case; nothing was addressing the *unscrolled*
                // one, which is what every large-font user sees first.
                //
                // A fade rather than moving anything: the panel stays welded to the bottom edge, no
                // control changes size or position, and at the default scale — where nothing
                // overflows — `canScrollForward` is false and this draws nothing at all.
                .fadeOutWhenMoreBelow(portionScroll)
                .verticalScroll(portionScroll)
                .padding(horizontal = Space.screenEdge),
            // A quick calculation has no hero, no *Usual* row, no portion units and no
            // *Add portion unit* action, so its contents fill far less of this zone than a saved
            // product's do — measured at 883 px of empty page between the last control and the
            // result panel, which is the same "reads unfinished" band the 2026-08-16 Home work
            // treated as a defect rather than tolerated.
            //
            // Centring the short content is the one fix available here that cannot make things
            // worse: `weight(1f, fill = false)` on this zone removes the gap but unpins the panel
            // from the bottom edge (tried, rejected, recorded), and a `weight` spacer *inside* a
            // `verticalScroll` Column is meaningless because the scroll gives it an infinite height
            // constraint. Arrangement is a property of the parent and does nothing once the content
            // is taller than the zone, so a saved product's layout is untouched.
            verticalArrangement = if (state.unsaved) Arrangement.Center else Arrangement.Top,
        ) {
            // Dropped while the keyboard is open, for the same reason `SourceBadge` drops its
            // advisory line: it is a prompt to start, and once the user is typing into a focused
            // field it has been answered. Keeping it cost real legibility rather than height alone
            // — with the IME up, the pinned result panel cut the line through the middle of its
            // glyphs, and a half-rendered sentence reads as a broken screen. Verified on the
            // emulator at 65 g with the keyboard open.
            if (!imeVisible) {
                Text(
                    text = stringResource(R.string.product_portion_question),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

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
                )

                Spacer(Modifier.height(Space.m))
                QuickAdjustRow(onAdjust = onAdjust, packageAmount = product.packageAmount)

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
private fun ProductSummary(
    product: Product,
    /** True while the IME is open — drops the badge's advisory line to give the room back. */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Stacked rather than side by side. The badge is itself a two-part block (a pill plus, for
    // unverified online data, a "Check package if needed" line), so putting it beside the per-100
    // figure produced a ragged two-line arrangement where neither element had a clean baseline —
    // visible only once a real product was on screen.
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        // Weighted deliberately heavier than the product name in the top bar above it.
        //
        // This is the figure every result on the screen derives from, and it is the one an
        // experienced user sanity-checks first — they know roughly what bread and pasta should be,
        // so a wrong database entry is usually obvious at a glance. The name is already carried by
        // the photo and the top bar; leaving this in plain weight made the screen's most
        // consequential input read as secondary to a label the user does not need.
        Text(
            text = stringResource(
                R.string.product_per_100,
                // ResultFormatter.quantity, not toPlainString: a per-100 figure derived from a
                // serving declaration (6 g per 18 g -> 33.33333333) would otherwise print every
                // digit of the division. See that function.
                ResultFormatter.quantity(product.carbsPer100),
                product.portionUnit,
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        SourceBadge(product, showHint = !compact)
    }
}

@Composable
private fun PortionField(
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    /** Claim focus and open the keyboard once, on arrival. See the call site for when and why. */
    autoFocus: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val portionLabel = stringResource(R.string.product_portion_label, unit)
    val focusRequester = remember { FocusRequester() }

    // Requested once per screen, not once per recomposition: `Unit` as the key means a later
    // recomposition — a keystroke, a result arriving, the meal bar appearing — cannot pull focus
    // back to this field while the user is somewhere else. If `autoFocus` is false there is no
    // effect at all, so a saved product's focus behaviour is byte-for-byte what it was.
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

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
        // An empty 48sp field with a lone unit suffix is a large blank box that does not say what
        // goes in it. A greyed `0` in the field's own type shows the shape of the expected input
        // without being a value: it is a placeholder, so it never becomes part of the portion and
        // there is no pre-filled zero to delete before typing.
        //
        // Cleared from semantics: the field already announces itself, and leaving the placeholder
        // readable made the *field* match text searches for values like "0.0 g", so assertions
        // looking for the result found the input box instead. It is decoration for the eye only.
        placeholder = {
            Text(
                text = "0",
                style = NumberType.portion,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
                textAlign = TextAlign.Center,
            )
        },
        suffix = {
            Text(text = unit, style = MaterialTheme.typography.titleMedium)
        },
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            // A real label, not an empty one. This field has no visible `label`, so
            // `contentDescription = ""` left TalkBack announcing an unnamed edit box on the screen's
            // primary input — the question above it is a separate node and is not read with it.
            // The unit is included because it is the one thing about this field that changes
            // between products and it is what stops a millilitre product being typed in grams.
            .semantics { contentDescription = portionLabel },
    )
}

/**
 * The step size the ± buttons move by, chosen from the package size (§16).
 *
 * A fixed ±5 g is wrong at both ends of the range this app serves: on a 400 g loaf or a 500 g pasta
 * pack it is roughly one-hundredth of the package and takes twenty taps to do anything, while on a
 * 20 g biscuit it is a quarter of the item. One absolute step cannot serve both.
 *
 * The package size is the signal already trusted elsewhere on this screen — [PackShortcuts] renders
 * only when [app.justthecarbs.domain.PackageQuantityParser] read one confidently — so scaling to it
 * introduces no new guess. When no package size was read, the original ±5/±10 stands: it is the
 * safe default for an unknown product, and inventing a step from a size the app does not have would
 * be exactly the guessed-shortcut problem §14 rules out.
 *
 * Steps stay round numbers. A "+37" button is arithmetically defensible and useless to someone
 * adjusting a portion by feel.
 */
internal fun quickAdjustStep(packageAmount: BigDecimal?): Int {
    val pack = packageAmount?.toInt() ?: return 5
    return when {
        pack >= 750 -> 50
        pack >= 300 -> 25
        pack >= 120 -> 10
        else -> 5
    }
}

@Composable
private fun QuickAdjustRow(onAdjust: (Int) -> Unit, packageAmount: BigDecimal? = null) {
    // Order runs negative → positive so the row reads like a number line (§16).
    //
    // Two steps per direction, the second twice the first, so the row spans a useful range without
    // a fourth button — the constraint that keeps every label inside its button at large font
    // scales, the same one that keeps ¾ out of PackShortcuts.
    val small = quickAdjustStep(packageAmount)
    val large = small * 2
    val steps = listOf(-large, -small, small, large)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        steps.forEach { delta ->
            // Spoken as "Minus 25" / "Plus 25" rather than the glyph, which TalkBack would
            // otherwise read as a mathematical operator detached from its amount.
            val description = stringResource(
                if (delta > 0) R.string.adjust_plus else R.string.adjust_minus,
                kotlin.math.abs(delta),
            )
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
        if (!scroll.canScrollForward) return@drawWithContent
        val fade = FADE_HEIGHT.toPx().coerceAtMost(size.height)
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
    // Every chip carries the app's minimum touch height, as Settings' and manual entry's already
    // did. Material's FilterChip defaults to 32dp, and these are the control that decides whether
    // the number on screen means grams or slices — the one mis-tap here changes what the result is
    // *of*, not merely its size. Measured at 84px on a 420dpi device before this.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        FilterChip(
            selected = isGramsSelected,
            onClick = onSelectGrams,
            label = { Text(stringResource(R.string.product_mode_grams)) },
            shape = RoundedCornerShape(Space.chipRadius),
            modifier = Modifier.heightIn(min = Space.minTouchTarget),
        )
        units.forEach { unit ->
            FilterChip(
                selected = !isGramsSelected && unit.id == selectedUnitId,
                onClick = { onSelectUnit(unit.id) },
                label = { Text(unit.chipLabel()) },
                shape = RoundedCornerShape(Space.chipRadius),
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
                conversion.amountPerUnit.stripTrailingZeros().toPlainString(),
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
            conversion.carbsPerUnit.stripTrailingZeros().toPlainString(),
        )
    }
    Text(
        text = equation,
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
            when (conversion) {
                is PortionConversion.WeightBased -> conversion.amountPerUnit
                is PortionConversion.DirectCarbs -> conversion.carbsPerUnit
            }.stripTrailingZeros().toPlainString(),
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
                    newerConversion.amountPerUnit.stripTrailingZeros().toPlainString(),
                    newerConversion.basis.unitLabel,
                    unit.unitLabel(count = 1),
                )
                is PortionConversion.DirectCarbs -> stringResource(
                    R.string.portion_changed_carbs_body,
                    newerConversion.carbsPerUnit.stripTrailingZeros().toPlainString(),
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
    onOpenMeal: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // The exact figure, whichever path produced it. Reading `state.result` alone left a valid
    // direct-carb calculation showing "pending" with no Copy and no Add to meal (correction §1).
    val exact = state.exactCarbs

    // Resolved during composition, not inside the click lambda: reading resources off
    // LocalContext at click time is not configuration-aware and can return a stale string.
    val copiedValue = exact?.let { ResultFormatter.clipboardValue(it, settings.resultStyle) }
    val copiedMessage = copiedValue?.let { stringResource(R.string.product_copied, it) }

    val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)

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
                //
                // The provenance line below follows the same rule: when it is present the padding
                // shrinks to pay for it, so this surface's height stays a fixed budget rather than
                // a starting point that each new element adds to.
                top = if (state.mealItems.isEmpty() && !showsProvenanceLine) Space.l else Space.s,
                bottom = if (showsProvenanceLine) Space.m else Space.l,
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

        if (exact == null) {
            // Reserves the same height the result will occupy, so the panel does not jump when the
            // first digit is typed (§16: the result area must not move under the user).
            //
            // The slot shows the per-100 figure the result is about to be scaled from, rather than
            // an instruction. "Enter a portion" spent the screen's largest and best-placed surface
            // telling the user to do something the focused, labelled field above already asks for;
            // the basis figure is the number they are working from, and seeing it here — in the
            // result's own position, at a size that reads as supporting rather than final — is what
            // makes the relationship between the two legible.
            //
            // It is deliberately NOT drawn in the result's colour or `NumberType.result`: this is
            // not a result, it is the input to one, and a per-100 figure that looked like an answer
            // would be the worst possible confusion on this screen.
            Box(
                modifier = Modifier.height(96.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    state.product?.let { product ->
                        Text(
                            text = stringResource(
                                R.string.product_result_basis_preview,
                                // See the note on the same call above.
                                ResultFormatter.quantity(product.carbsPer100),
                                product.portionUnit,
                            ),
                            style = NumberType.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.xs))
                    }
                    Text(
                        // The countable path asks for a count, not a weight — naming the wrong
                        // input is a small thing that makes the app look like it is not watching.
                        text = if (equationUnit != null) {
                            stringResource(R.string.product_result_pending_count)
                        } else {
                            stringResource(R.string.product_result_pending)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Both figures come from `exact`, independently. Neither is derived from the other,
            // so swapping which one dominates cannot introduce a double rounding (§17).
            val wholeGrams = ResultFormatter.wholeGrams(exact)
            val dominant = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(exact)} g"
                ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(wholeGrams)} g"
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
                // The button holds a visible "copied" state for a few seconds after the tap.
                //
                // The Toast alone was the only confirmation, and a Toast is transient, easy to miss
                // one-handed, and gone by the time the user looks back — while the value they are
                // about to paste is going into something that doses insulin. The icon swapping to a
                // checkmark survives being glanced away from, which is exactly what a Toast cannot
                // do. The Toast stays: it is what announces the copy to TalkBack.
                //
                // Keyed on the copied value so copying a *different* number after changing the
                // portion restarts the confirmation rather than silently reusing the running timer.
                var copiedAt by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(copiedAt) {
                    if (copiedAt != null) {
                        kotlinx.coroutines.delay(Motion.COPIED_STATE_MS)
                        copiedAt = null
                    }
                }
                val showCopied = copiedAt != null && copiedAt == copiedValue

                IconButton(
                    onClick = {
                        // Only the number reaches the clipboard — never "31 g carbs" (§19).
                        clipboard.setText(AnnotatedString(copiedValue.orEmpty()))
                        copiedAt = copiedValue
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
                    Icon(
                        imageVector = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = if (showCopied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.LocalContentColor.current
                        },
                    )
                }
            }

            // The supporting figure is rendered legibly, not as a whisper (design decision 3.2).
            Text(
                text = when (settings.resultStyle) {
                    ResultStyle.DECIMAL_DOMINANT -> stringResource(
                        R.string.product_result_whole,
                        ResultFormatter.whole(wholeGrams),
                    )
                    ResultStyle.WHOLE_DOMINANT -> stringResource(
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
                    textAlign = TextAlign.Center,
                )
            }

            // Only once there is a number worth adding. Offered under the result, never in place
            // of it: the app answers a carbohydrate question first and builds a meal second (§9).
            Spacer(Modifier.height(Space.m))
            MealActions(
                onAdd = onAddToMeal,
                onAddAndScanNext = onAddToMealAndScanNext,
                enabled = !state.addingToMeal,
            )
            // Reported rather than merely survived, same rule as `quickSaveFailed`: the result is
            // still on screen and still correct, so silence here reads as success and the user would
            // leave believing the item was added. `MealActions` re-enables itself the moment this
            // shows, since the failed write already released the guard — this is the retry surface.
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

// PrimaryAction / SecondaryAction moved to ui.components alongside RecoveryPanel, which they are
// only ever used inside — the search screen needs the same pair for the same panel.
