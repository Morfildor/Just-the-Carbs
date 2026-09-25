package app.justthecarbs.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space

/**
 * Home's `Show protein` control: a 48dp chip with switch semantics (design spec 2026-09-24,
 * section 2). It reads and writes the one persisted protein setting, the same one as the Settings
 * row, so the two controls cannot disagree.
 *
 * Why not `JtcFilterChip`: that is a Material `FilterChip`, whose role is a checkbox and which has
 * no icon slot; this control is an on/off mode, so it is a switch. It borrows that chip's colours.
 *
 * Why the hairline: the quiet fill measures about 1.05:1 against the page, which is fine beside a
 * selected sibling (the calculator's mode chips) but reads as disabled on a control that stands
 * alone. Light uses the recent cards' `outlineVariant` edge while off; Dark keys on the page, like
 * `JtcValueButton`, and keeps its `outline` edge in both states, since the on fill alone sits at
 * about 1.5:1 against a dark page.
 *
 * The glyph is `EggAlt`, the egg with its yolk, not the plain `Egg`: the design gate of spec
 * section 3 was a screenshot at 20dp, and there the plain egg read as a water drop, filled or
 * outlined (emulator, 2026-09-25). The yolk makes it unambiguous.
 *
 * The state is carried by the glyph (outlined egg off, filled on), reinforced by the container and
 * label colours, the tile subtitle and the spoken switch state. Nothing is colour-only. The label
 * never changes with the state. No haptic: the app's haptics mean "a scan produced something, read
 * the screen", and a buzz for changing what the dock shows would dilute that.
 */
@Composable
fun ProteinToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val darkPage = colors.background.luminance() < 0.5f
    val spec = tween<Color>(Motion.STANDARD_MS, easing = EaseOutQuart)
    val container by animateColorAsState(
        if (checked) colors.secondaryContainer else colors.surfaceContainerLow,
        spec,
        label = "proteinToggleContainer",
    )
    val labelColor by animateColorAsState(
        if (checked) colors.onSecondaryContainer else colors.onSurface,
        spec,
        label = "proteinToggleLabel",
    )
    val glyphColor by animateColorAsState(
        if (checked) colors.onSecondaryContainer else colors.onSurfaceVariant,
        spec,
        label = "proteinToggleGlyph",
    )
    val edge by animateColorAsState(
        when {
            darkPage -> colors.outline
            checked -> Color.Transparent
            else -> colors.outlineVariant
        },
        spec,
        label = "proteinToggleEdge",
    )
    val shape = RoundedCornerShape(Space.chipRadius)

    Row(
        modifier = modifier
            .heightIn(min = Space.minTouchTarget)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, edge, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = GLYPH_INSET, end = Space.m, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // One beat with the colours, like the favourite star's outlined/filled swap.
        Crossfade(
            targetState = checked,
            animationSpec = tween(Motion.STANDARD_MS, easing = EaseOutQuart),
            label = "proteinToggleGlyphShape",
        ) { on ->
            Icon(
                imageVector = if (on) Icons.Filled.EggAlt else Icons.Outlined.EggAlt,
                // The label names the control; the glyph is its state cue, not a second name.
                contentDescription = null,
                tint = glyphColor,
                // Load-bearing: the fill is the state signal, and the egg's inner highlight is the
                // first detail to go at a smaller size.
                modifier = Modifier.size(GLYPH_SIZE),
            )
        }
        Text(
            text = stringResource(R.string.protein_toggle),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
        )
    }
}

private val GLYPH_SIZE = 20.dp
private val GLYPH_INSET = 12.dp
