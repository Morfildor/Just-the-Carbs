package app.justthecarbs.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.PortionAdjustment.Operation
import app.justthecarbs.ui.theme.Space
import java.math.BigDecimal

/** Stable handles for instrumented tests. */
const val PORTION_RAIL_TAG = "portion_rail"
const val PORTION_RAIL_HALVE_TAG = "portion_rail_halve"
const val PORTION_RAIL_DOUBLE_TAG = "portion_rail_double"
const val PORTION_RAIL_MINUS_TAG = "portion_rail_minus"
const val PORTION_RAIL_PLUS_TAG = "portion_rail_plus"

/**
 * The portion accelerator rail: `½ · ×2 · −step · +step` (1.0.8).
 *
 * ## What it is, and what it is subordinate to
 *
 * Four accelerators for the amount above it. The screen's hierarchy is *amount field, then
 * carbohydrate result, then this* — a portion is still typed, and these only save the typing when
 * the change is one of the four common ones. So it is drawn as one segmented pill: a single
 * low-contrast surface divided into four, rather than four separate buttons each with its own
 * outline. Four outlined buttons is what the screen had before, and at a glance they read as a row
 * of peers competing with the field for the eye; one divided rail reads as a toolbar attached to
 * the field, which is what it is.
 *
 * Height is [Space.minTouchTarget] and no more. It never grows to a primary-action height, because
 * the only primary action on these screens is the number itself.
 *
 * ## Why the step size is a parameter
 *
 * A weight rail's step is scaled to the package (`quickAdjustStep`: ±5 on a biscuit, ±50 on a
 * pasta pack — one absolute step cannot serve both), while a count's is always one. The arithmetic
 * for all four controls is [app.justthecarbs.domain.PortionAdjustment], which this only renders.
 *
 * ## Large fonts
 *
 * Labels are short by construction — `½`, `×2`, `−10`, `+10` — and `heightIn` lets the rail grow
 * rather than clip. The labels do not scale with the body text: they are set at a fixed size, so a
 * 2× font scale makes the rail taller and its touch targets larger without pushing `−50` into
 * truncation, which is the failure mode the previous row was explicitly shaped around (a control
 * reading "+1" when it means "+10" is a control that lies).
 */
@Composable
fun PortionAdjustRail(
    /** The signed step the ± segments move by: package-scaled for a weight, 1 for a count. */
    step: BigDecimal,
    onAdjust: (Operation) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.buttonRadius)

    // One surface, divided — see the KDoc. The border is the rail's own outline; the segments
    // inside it are separated by hairlines rather than by gaps, so it cannot read as four buttons.
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `IntrinsicSize.Min` so the dividers' `fillMaxHeight` resolves to the tallest segment
            // rather than to an unbounded constraint. **This is load-bearing**: in a parent
            // offering unbounded height, a `fillMaxHeight` child takes all of it and drags the row
            // with it — measured elsewhere in this app as a callout card that filled the screen
            // top to bottom, with a green test suite throughout, because nothing asserts how tall
            // a control is. Remove this and the rail stops being a rail.
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .testTag(PORTION_RAIL_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Order runs ½ · ×2 · − · + : the two proportional operations first, then the two linear
        // ones, so the pair that changes the amount by feel sits apart from the pair that nudges
        // it. Within each pair the order is the one a number line implies.
        val plain = stepLabel(step)

        Segment(
            label = "½",
            description = stringResource(R.string.adjust_halve),
            testTag = PORTION_RAIL_HALVE_TAG,
            hapticsEnabled = hapticsEnabled,
            onClick = { onAdjust(Operation.Halve) },
        )
        Divider()
        Segment(
            label = "×2",
            description = stringResource(R.string.adjust_double),
            testTag = PORTION_RAIL_DOUBLE_TAG,
            hapticsEnabled = hapticsEnabled,
            onClick = { onAdjust(Operation.Double) },
        )
        Divider()
        // The minus sign is U+2212, not a hyphen: at these weights a hyphen reads as a dash beside
        // a digit rather than as an operator, and it is the character the previous row used too.
        Segment(
            label = "−$plain",
            description = stringResource(R.string.adjust_minus, plain),
            testTag = PORTION_RAIL_MINUS_TAG,
            hapticsEnabled = hapticsEnabled,
            onClick = { onAdjust(Operation.Step(step.negate())) },
        )
        Divider()
        Segment(
            label = "+$plain",
            description = stringResource(R.string.adjust_plus, plain),
            testTag = PORTION_RAIL_PLUS_TAG,
            hapticsEnabled = hapticsEnabled,
            onClick = { onAdjust(Operation.Step(step)) },
        )
    }
}

/**
 * One segment of the rail.
 *
 * Built from `clickable` on a Box rather than from a Material button, and that is the whole reason
 * this component exists: a `TextButton` or `OutlinedButton` brings its own minimum width, its own
 * content padding and its own container, none of which can be reconciled with four equal segments
 * inside one shared outline. What it must keep from a Material button is the parts that matter —
 * a ripple, a visible pressed state, a 48dp target and a spoken label — and each is supplied here
 * explicitly.
 */
@Composable
private fun RowScope.Segment(
    label: String,
    description: String,
    testTag: String,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = Space.minTouchTarget)
            // Pressed state carried by the surface as well as the ripple. A ripple alone is
            // easy to miss on a rail this quiet, and these are controls someone taps repeatedly
            // and quickly — the tint is what confirms the fourth tap landed as well as the first.
            .background(
                if (pressed) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = {
                    // A selection tick, not the shutter's LongPress: this is a small adjustment
                    // being acknowledged, and it is tapped in quick succession. Gated on the app's
                    // single existing haptics setting — no new preference.
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            )
            .padding(horizontal = Space.xs)
            // Spoken as "Halve the amount" / "Plus 10" rather than as the glyph, which TalkBack
            // reads as a detached mathematical operator.
            //
            // `role = Button` because this is a Box with a `clickable`, not a Material button:
            // without it TalkBack announces the label and the click action but never says *what
            // kind of thing* it is, so a screen-reader user is told "Plus 10, double tap to
            // activate" with no indication that the rail is a row of buttons.
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The hairline between two segments — what makes the rail one control rather than four.
 *
 * Inset vertically so it stops short of the rail's own rounded ends: a divider running the full
 * height would meet the outline at the corners and read as a crop mark. Its width is a fixed
 * hairline rather than a fraction, so it stays one pixel at every font scale.
 */
@Composable
private fun RowScope.Divider() {
    Box(
        modifier = Modifier
            // Matches the row's height rather than taking a fixed one, so at a large font scale —
            // where the segments grow — the divider grows with them instead of leaving a stub in
            // the middle of a taller rail.
            .fillMaxHeight()
            .padding(vertical = Space.s)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * The step as it is printed on the rail and spoken by TalkBack.
 *
 * `toPlainString` after stripping, so a whole step reads `10` rather than `10.0` or `1E+1`, and a
 * fractional one (which no current caller produces, but the type permits) reads as itself.
 */
private fun stepLabel(step: BigDecimal): String =
    step.abs().stripTrailingZeros().toPlainString()

private val DIVIDER_HEIGHT = 24.dp
