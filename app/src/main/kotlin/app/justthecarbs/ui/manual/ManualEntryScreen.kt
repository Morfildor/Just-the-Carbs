package app.justthecarbs.ui.manual

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.theme.Space
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
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-90).dp)
                .size(200.dp)
                .background(MaterialTheme.extendedColors.orangeSoft.copy(alpha = 0.9f), CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.product_back),
                    )
                }
                Text(
                    text = stringResource(R.string.manual_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Space.s).semantics { heading() },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
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

                OutlinedTextField(
                    value = state.carbsPer100,
                    onValueChange = onCarbsChanged,
                    // Names the unit the value is measured in, tracking the basis chips below — the
                    // same number means different things per 100 g and per 100 ml, and this is the
                    // one field where that ambiguity has a numeric consequence.
                    label = { Text(stringResource(R.string.manual_carbs, state.basis.unitLabel)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
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
                        FilterChip(
                            selected = state.basis == NutritionBasis.PER_100_G,
                            onClick = { onBasisChanged(NutritionBasis.PER_100_G) },
                            label = { Text(stringResource(R.string.manual_basis_g)) },
                            modifier = Modifier.height(Space.minTouchTarget),
                        )
                        FilterChip(
                            selected = state.basis == NutritionBasis.PER_100_ML,
                            onClick = { onBasisChanged(NutritionBasis.PER_100_ML) },
                            label = { Text(stringResource(R.string.manual_basis_ml)) },
                            modifier = Modifier.height(Space.minTouchTarget),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.packageAmount,
                    onValueChange = onPackageChanged,
                    label = { Text(stringResource(R.string.manual_package)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Space.s))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenEdge)
                    .padding(bottom = Space.m)
                    .navigationBarsPadding(),
            ) {
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.extendedColors.disabledButton,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth().height(Space.primaryButtonHeight),
                ) { Text(stringResource(R.string.manual_save)) }
            }
        }
    }
}
