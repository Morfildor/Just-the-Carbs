package app.justthecarbs.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.Space

/**
 * The app's four action treatments, its field colours, its chip and its menu — in one place so
 * eleven screens cannot each invent their own.
 *
 * The vocabulary was already written down in `DESIGN.md` and largely *not applied*: two of
 * seventeen text fields used the app's own field colours, five of eight chips were Material-default
 * rectangles, and every `OutlinedButton` inherited Material's grey `onSurfaceVariant` label — so
 * `Add to meal`, `Retake` and `Type it in` read as **disabled** beside a blue text button on the
 * same screen. That is the specific defect [JtcOutlinedButton] exists to close.
 *
 * The four treatments, and what each one means:
 *
 * | | height | container | label | meaning |
 * |---|---|---|---|---|
 * | [PrimaryAction] | 56dp | `primary` | `onPrimary` | the one thing to do next |
 * | [JtcOutlinedButton] | 48dp | none, `primary` border | `primary` | a real alternative |
 * | `TextButton` | 48dp | none | `primary` | an aside |
 * | [JtcValueButton] | 40dp | `surfaceContainerLow` | `onSurface` | a numeric shortcut, not an action |
 *
 * [JtcValueButton] is deliberately *not* accented. A row of four blue-bordered `-25 / -5 / +5 /
 * +25` buttons spends the interaction colour on arithmetic and competes with the primary action;
 * these are conveniences for filling a field, so they are quiet fills with ink labels.
 */

/**
 * A secondary action: a genuine alternative to the primary one, not a lesser version of it.
 *
 * Fixes Material's default outlined-button colouring, which takes its label from
 * `onSurfaceVariant` — the same grey this app uses for supporting text. The border is `primary` at
 * 45% so the control reads as an outline rather than a second filled button competing with the
 * real one.
 */
@Composable
fun JtcOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Space.buttonRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = jtcOutlinedButtonBorder(enabled),
        // heightIn, not height: the floor keeps the three control heights honest while still
        // letting the label grow at a large font scale instead of being clipped.
        modifier = modifier.heightIn(min = Space.secondaryButtonHeight),
    ) { Text(text) }
}

/** [JtcOutlinedButton]'s border, exposed for the few call sites that need their own slot content. */
@Composable
fun jtcOutlinedButtonBorder(enabled: Boolean = true) =
    BorderStroke(
        width = 1.dp,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )

/**
 * A compact numeric shortcut — `-25`, `½ pack`, a usual portion.
 *
 * A quiet fill with no border, because in rows of four an outline per button turns the portion
 * area into a grid of boxes. 40dp tall visually; the caller's row padding keeps the touch target
 * at or above 48dp, which is asserted by the existing
 * `ProductScreenTest.everyTutorialControlMeetsTheTouchTargetFloor`-style checks.
 *
 * Not a `Button` with custom colours: Material's button carries its own minimum height (36dp) and
 * content padding that fight a 40dp box, and its ripple defaults to the content colour. A plain
 * clickable `Box` is the honest primitive for "a tappable chip of text".
 */
@Composable
fun JtcValueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = Space.valueButtonHeight)
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(Space.buttonRadius),
            )
            // Clip before clickable so the ripple follows the rounded corner instead of
            // painting a square over it.
            .clip(RoundedCornerShape(Space.buttonRadius))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.s + Space.xs, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The app's text-field colours.
 *
 * A quiet fill at rest with **no border**, and a `primary` border only while focused. The
 * previous default gave every field a permanent grey outline, which is what put an edge around
 * every level of every screen — the field, the card holding it and the section holding that.
 *
 * The focused container lifts to `surfaceContainerLowest` rather than changing hue, so focus reads
 * as "this one is live" without a colour change competing with the result.
 */
@Composable
fun jtcTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    // Transparent, not `outlineVariant`: at rest the fill alone says "field". Material still
    // reserves the stroke's width, so focusing does not shift the content by a pixel.
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/**
 * The app's filter chip: pill-shaped, selected state carried by a tinted container rather than a
 * border.
 *
 * Material's default `FilterChip` is a rounded *rectangle* with a visible outline in both states,
 * so the five unstyled sites (manual entry's g/ml, the verify dialog's, the add-unit form's) read
 * as small boxes while the three styled ones read as chips. The affordance of a chip is its pill;
 * diluting it was the inconsistency.
 */
@Composable
fun JtcFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        shape = RoundedCornerShape(Space.chipRadius),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            disabledSelectedBorderColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/**
 * The app's dropdown menu surface: the card treatment, so an overflow menu is the same object as
 * a recent card rather than a Material-default elevated rectangle.
 */
@Composable
fun JtcDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(Space.buttonRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
        content = content,
    )
}

/**
 * A row of [JtcValueButton]s that share the row's width equally.
 *
 * Exists because the adjust row, the pack row and the usual-portions row all want the same thing
 * and previously each spelled it out with their own weights and spacing.
 */
@Composable
fun RowScope.ValueButtonSlot(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) = JtcValueButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f))
