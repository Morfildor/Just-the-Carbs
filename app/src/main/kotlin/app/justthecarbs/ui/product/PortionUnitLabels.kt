package app.justthecarbs.ui.product

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.justthecarbs.R
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind

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

/**
 * The kind's own word, for the places that have a [PortionUnitKind] but no [PortionUnit] yet — the
 * "Add portion unit" type picker and the OCR "save as a … portion" action.
 *
 * Those three sites previously rendered `kind.name`, so the user was shown the raw enum constant:
 * `SLICE`, `BISCUIT`, `SACHET`, and — worst — `CUSTOM`, which is not a word for anything a person
 * eats. Routing them through the same plurals the rest of the app uses is what keeps one vocabulary
 * (§15) and keeps implementation names out of the UI (§24).
 *
 * [CUSTOM] has no printable word of its own here (its label is user-supplied text that does not
 * exist yet at type-selection time), so it borrows the "Custom" wording the name field already uses.
 */
@Composable
fun PortionUnitKind.kindLabel(count: Int = 1): String =
    if (this == PortionUnitKind.CUSTOM) {
        stringResource(R.string.product_portion_unit_custom)
    } else {
        pluralStringResource(pluralsRes(), count)
    }

/** The mode-chip label: Title Case, matching the "Grams" chip it sits beside. */
@Composable
fun PortionUnit.chipLabel(): String = unitLabel(count = 2).replaceFirstChar { it.uppercase() }
