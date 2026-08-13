package app.carbscan.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.carbscan.domain.LookupError
import app.carbscan.domain.Product
import app.carbscan.domain.ResultFormatter
import app.carbscan.domain.ResultStyle
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
) {
    Column(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenEdge),
        ) {
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

            Spacer(Modifier.height(Space.xl))

            Text(
                text = stringResource(R.string.product_portion_question),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Space.s))
            PortionField(
                value = state.portionText,
                unit = product.portionUnit,
                onValueChange = onPortionChanged,
            )

            Spacer(Modifier.height(Space.m))
            QuickAdjustRow(onAdjust = onAdjust)

            // Only offered when the package size was read confidently. A guessed pack size would
            // be a wrong portion presented as a shortcut (§14, §13).
            product.packageAmount?.let { pack ->
                Spacer(Modifier.height(Space.m))
                PackShortcuts(pack = pack, onSetPortion = onSetPortion)
            }

            Spacer(Modifier.height(Space.l))
        }

        ResultPanel(state = state, settings = settings)
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
                modifier = Modifier
                    .weight(1f)
                    .height(Space.minTouchTarget)
                    .semantics { contentDescription = description },
            ) {
                Text(text = if (delta > 0) "+$delta" else "$delta")
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
        OutlinedButton(
            onClick = { onSetPortion(pack.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)) },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.product_half_pack)) }

        OutlinedButton(
            onClick = { onSetPortion(pack) },
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).height(Space.minTouchTarget),
        ) { Text(stringResource(R.string.product_full_pack)) }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = Space.cardRadius, topEnd = Space.cardRadius),
            )
            .navigationBarsPadding()
            .padding(horizontal = Space.screenEdge, vertical = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.product_result_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.xs))

        if (result == null) {
            Text(
                text = stringResource(R.string.product_result_pending),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(80.dp).padding(top = Space.l),
            )
        } else {
            val dominant = when (settings.resultStyle) {
                ResultStyle.WHOLE_WITH_DECIMAL -> "${ResultFormatter.whole(result.wholeGrams)} g"
                ResultStyle.DECIMAL_ONLY -> "${ResultFormatter.decimal(result.exact)} g"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dominant,
                    style = NumberType.result,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    // Announced as a live region so TalkBack reads the new result as the portion
                    // changes, instead of leaving a blind user to go looking for it (§39).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )

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

            // The decimal is rendered legibly, not as a whisper (design decision 3.2).
            if (settings.resultStyle == ResultStyle.WHOLE_WITH_DECIMAL) {
                Text(
                    text = stringResource(
                        R.string.product_result_calculated,
                        ResultFormatter.decimal(result.exact),
                    ),
                    style = NumberType.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
