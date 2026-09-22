package app.justthecarbs.ui.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.components.AccentBackdrop
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent
import app.justthecarbs.ui.theme.extendedColors

/**
 * Manual product entry (§27) and the quick calculator (§28).
 *
 * Four fields, three of them required. Nothing about fat, protein, sugar or energy is asked for:
 * the app cannot use those values, and asking would imply it is keeping a food diary (§2, §27).
 */
@Composable
fun ManualEntryScreen(
    state: ManualEntryUiState,
    onNameChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onBasisChanged: (NutritionBasis) -> Unit,
    onPackageChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccentBackdrop(
            accent = Destination.MANUAL.accent(),
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            JtcTopBar(
                title = stringResource(R.string.manual_title),
                destination = Destination.MANUAL,
                onBack = onBack,
            )

            // Save travels with the form instead of being pinned to the screen's bottom edge.
            //
            // The old arrangement gave this column `weight(1f)` and put the action in a sibling
            // pinned beneath it, so on a 1080x2400 device the last field ended at y≈1084 and Save
            // sat at y≈2132 — roughly 1050px of empty cream between a four-field form and its own
            // submit button. The button read as belonging to the screen rather than to the form,
            // which is the "stranded at the opposite end" complaint.
            //
            // Both are now inside one scrolling column, so Save sits directly under the last field
            // at a normal rhythm step. `weight(1f, fill = false)` is what makes this safe at the
            // other extreme: the column takes only the height it needs when the content is short,
            // and stops growing — becoming scrollable — once the content plus the button exceed the
            // viewport, which is what happens at a large font scale or with both error lines
            // showing. So nothing is ever pushed off-screen and nothing is ever unreachable.
            //
            // Pinning it to the bottom edge instead was considered and rejected on this screen:
            // the form is short and the collision it would create with the IME is precisely the
            // failure mode the brief warns against. `imePadding()` on the outer column already
            // lifts the whole form clear of the keyboard.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screenEdge),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                // Shown, not editable: it came from the scan that got the user here, and retyping it
                // would only be a chance to get it wrong (§26).
                if (state.barcode.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.notfound_barcode, state.barcode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.manual_name)) },
                    singleLine = true,
                    isError = state.nameError,
                    supportingText = if (state.nameError) {
                        { Text(stringResource(R.string.manual_error_name)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                CarbsField(
                    value = state.carbsPer100,
                    onValueChange = onCarbsChanged,
                    // Names the unit the value is measured in, tracking the basis chips below — the
                    // same number means different things per 100 g and per 100 ml, and this is the
                    // one field where that ambiguity has a numeric consequence. A null basis (§5,
                    // startup-hardening pass) has no unit to name yet, so the label asks plainly
                    // rather than guessing one to print.
                    label = {
                        Text(
                            state.basis?.let { basis -> stringResource(R.string.manual_carbs, basis.unitLabel) }
                                ?: stringResource(R.string.manual_carbs_unresolved),
                        )
                    },
                    isError = state.carbsError != null,
                    supportingText = state.carbsError?.let { error ->
                        {
                            Text(
                                stringResource(
                                    when (error) {
                                        CarbsError.MALFORMED -> R.string.manual_error_carbs
                                        CarbsError.OUT_OF_RANGE -> R.string.manual_error_carbs_range
                                    },
                                ),
                            )
                        }
                    },
                )

                Column {
                    Text(
                        text = stringResource(R.string.manual_basis),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        // The basis is an explicit choice, never inferred from the number typed. It
                        // decides the unit the portion is locked to, and the app never converts (§17).
                        // Neither chip is selected when `state.basis` is null — that null is a real
                        // state (§5, startup-hardening pass), not a loading gap, so showing one chip
                        // pre-selected here would be exactly the silent default this screen exists to
                        // refuse.
                        FilterChip(
                            selected = state.basis == NutritionBasis.PER_100_G,
                            onClick = { onBasisChanged(NutritionBasis.PER_100_G) },
                            label = { Text(stringResource(R.string.manual_basis_g)) },
                            modifier = Modifier.heightIn(min = Space.minTouchTarget),
                        )
                        FilterChip(
                            selected = state.basis == NutritionBasis.PER_100_ML,
                            onClick = { onBasisChanged(NutritionBasis.PER_100_ML) },
                            label = { Text(stringResource(R.string.manual_basis_ml)) },
                            modifier = Modifier.heightIn(min = Space.minTouchTarget),
                        )
                    }
                    if (state.basis == null) {
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = stringResource(R.string.manual_basis_unresolved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.packageAmount,
                    onValueChange = onPackageChanged,
                    label = { Text(stringResource(R.string.manual_package)) },
                    singleLine = true,
                    isError = state.packageError,
                    supportingText = if (state.packageError) {
                        { Text(stringResource(R.string.manual_error_package)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                // A step clear of the last field, so Save reads as the end of the form rather than
                // as a fifth row of it.
                Spacer(Modifier.height(Space.s))

                // Directly above the button that failed, so the explanation is where the user is
                // already looking. Without it the tap re-enabled the button and changed nothing
                // else, which reads as a missed tap rather than a failed save — and the response to
                // a missed tap is to tap again and fail again.
                if (state.saveFailed) {
                    Text(
                        text = stringResource(R.string.manual_error_save),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        // Paired disabled foreground, not onPrimary (a filled-button colour that
                        // read as a washed-out active button rather than a genuinely disabled one).
                        disabledContainerColor = MaterialTheme.extendedColors.disabledButton,
                        disabledContentColor = MaterialTheme.extendedColors.onDisabledButton,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
                ) { Text(stringResource(R.string.manual_save)) }

                // Trailing room below the action, plus the navigation-bar inset as real height.
                //
                // `windowInsetsBottomHeight` rather than a `navigationBarsPadding()` modifier: the
                // inset now has to be part of what *scrolls*, and a padding modifier applied after
                // `verticalScroll` pads the scrolling content rather than the viewport — the exact
                // ordering trap this repository has recorded twice before, most recently as the
                // Settings row that came to rest under the navigation bar.
                Spacer(Modifier.height(Space.m))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * The carbohydrate amount field, with the [PortionField][app.justthecarbs.ui.product.PortionField]/
 * [CountField][app.justthecarbs.ui.product.CountField] auto-focus-and-select-all treatment applied
 * when this screen was reached with a value already carried in from a scan (task §3/§5: "editing an
 * eligible proposal should preserve and select its amount, focus the keyboard").
 *
 * ## Why this is gated on the value being non-blank at FIRST composition, not on every recomposition
 *
 * `remember { value.isNotBlank() }` captures whether a figure arrived pre-filled, once, the moment
 * this composable enters the tree. Re-evaluating `value.isNotBlank()` on every recomposition would
 * re-arm the effect (and re-select the text) on every keystroke once the user starts typing over a
 * carried-in value that happened to become blank and non-blank again — this field only ever wants to
 * claim focus and select **the value the screen opened with**, exactly once.
 *
 * Ordinary manual entry (no scan behind it) opens with a blank field, so `startedWithValue` is false
 * and neither the focus request nor the select-all fires — this field's behaviour there is unchanged.
 */
@Composable
private fun CarbsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val startedWithValue = remember { value.isNotBlank() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    var hasFocus by remember { mutableStateOf(false) }

    if (startedWithValue) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        isError = isError,
        supportingText = supportingText,
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                // Only on the transition into focus, and only for a value that arrived pre-filled —
                // ordinary manual entry starts blank, where select-all is meaningless. Re-selecting on
                // every focused recomposition would fight the user's own caret placement mid-edit.
                if (startedWithValue && focus.isFocused && !hasFocus) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
                hasFocus = focus.isFocused
            },
    )
}
