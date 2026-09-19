package app.justthecarbs.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.justthecarbs.R
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.ResultFormatter
import app.justthecarbs.domain.Product
import app.justthecarbs.ui.components.JtcDialogDefaults
import app.justthecarbs.ui.components.jtcDialogOutline
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

/**
 * Verification (§23) — the app's main reliability feature.
 *
 * The user is standing in front of the package, comparing it to what the database claims. So the
 * fields are pre-filled with the current values and the user corrects what is wrong, rather than
 * being made to retype what is already right.
 *
 * The basis is editable here too so an incorrect database value can be corrected against the
 * package. The app never converts between grams and millilitres.
 */
@Composable
fun VerifyDialog(
    product: Product,
    onConfirm: (carbsPer100: BigDecimal, basis: NutritionBasis, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(product.name) }
    var carbs by remember {
        mutableStateOf(ResultFormatter.editable(product.carbsPer100))
    }
    var basis by remember { mutableStateOf(product.basis) }

    val parsedCarbs = PortionParser.parse(carbs)
    // The same validator the remote data must satisfy. A verified value is trusted more than a
    // downloaded one, so it has to clear at least the same bar (§13).
    val validCarbs = parsedCarbs?.let {
        NutritionValueValidator.validateCarbsPer100(it.toDouble(), basis)
    }
    val isNegativeNumber = carbs.trim().let { value ->
        value.startsWith("-") && PortionParser.parse(value.removePrefix("-")) != null
    }
    val carbsError = when {
        carbs.isBlank() -> null
        isNegativeNumber -> R.string.verify_error_carbs_negative
        parsedCarbs == null -> R.string.manual_error_carbs
        validCarbs == null -> R.string.manual_error_carbs_range
        else -> null
    }
    val canConfirm = name.isNotBlank() && validCarbs != null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.jtcDialogOutline(),
        shape = JtcDialogDefaults.shape,
        containerColor = JtcDialogDefaults.containerColor,
        iconContentColor = JtcDialogDefaults.iconContentColor,
        titleContentColor = JtcDialogDefaults.titleContentColor,
        textContentColor = JtcDialogDefaults.textContentColor,
        tonalElevation = JtcDialogDefaults.tonalElevation,
        title = { Text(stringResource(R.string.verify_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    text = stringResource(R.string.verify_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.manual_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text(stringResource(R.string.manual_carbs, basis.unitLabel)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = carbsError != null,
                    supportingText = carbsError?.let { error ->
                        { Text(stringResource(error)) }
                    },
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(
                        selected = basis == NutritionBasis.PER_100_G,
                        onClick = { basis = NutritionBasis.PER_100_G },
                        label = { Text(stringResource(R.string.manual_basis_g)) },
                        modifier = Modifier.heightIn(min = Space.minTouchTarget),
                    )
                    FilterChip(
                        selected = basis == NutritionBasis.PER_100_ML,
                        onClick = { basis = NutritionBasis.PER_100_ML },
                        label = { Text(stringResource(R.string.manual_basis_ml)) },
                        modifier = Modifier.heightIn(min = Space.minTouchTarget),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validCarbs?.let { onConfirm(it, basis, name.trim()) } },
                enabled = canConfirm,
            ) { Text(stringResource(R.string.verify_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.verify_cancel)) }
        },
    )
}
