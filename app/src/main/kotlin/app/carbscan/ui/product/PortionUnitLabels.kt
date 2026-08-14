package app.carbscan.ui.product

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import app.carbscan.R
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitKind

/** Every non-[PortionUnitKind.CUSTOM] kind has an English plural label; custom units use their own text. */
private fun PortionUnitKind.pluralsRes(): Int = when (this) {
    PortionUnitKind.SLICE -> R.plurals.portion_kind_slice
    PortionUnitKind.PIECE -> R.plurals.portion_kind_piece
    PortionUnitKind.BISCUIT -> R.plurals.portion_kind_biscuit
    PortionUnitKind.COOKIE -> R.plurals.portion_kind_cookie
    PortionUnitKind.BAR -> R.plurals.portion_kind_bar
    PortionUnitKind.ROLL -> R.plurals.portion_kind_roll
    PortionUnitKind.SCOOP -> R.plurals.portion_kind_scoop
    PortionUnitKind.SACHET -> R.plurals.portion_kind_sachet
    PortionUnitKind.SERVING -> R.plurals.portion_kind_serving
    PortionUnitKind.CUSTOM -> error("CUSTOM has no built-in label; read PortionUnit.customLabel instead")
}

/** e.g. "slice" for count=1, "slices" for count=2; a custom unit's own name for any count. */
@Composable
fun PortionUnit.unitLabel(count: Int): String =
    if (kind == PortionUnitKind.CUSTOM) {
        customLabel.orEmpty()
    } else {
        pluralStringResource(kind.pluralsRes(), count)
    }

/** The mode-chip label: Title Case, matching the "Grams" chip it sits beside. */
@Composable
fun PortionUnit.chipLabel(): String = unitLabel(count = 2).replaceFirstChar { it.uppercase() }
