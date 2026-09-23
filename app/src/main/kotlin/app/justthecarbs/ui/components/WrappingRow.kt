package app.justthecarbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * A row that wraps its items onto further lines, like `FlowRow`, and whose intrinsic height is the
 * height it measures to.
 *
 * `FlowRow` estimates its intrinsic height by laying its items out at their minimum intrinsic
 * widths, which for text is its longest word, so it assumes items share lines that, measured, they
 * do not. Beside the calculator's thumbnail at 1.8x text, the per-100 figure, the provenance badge
 * and Verify were estimated as one line and measured as three. The calculator reserves the product
 * header's height from that estimate (see ProductIdentityRow), and a header measured taller than
 * its reserve is centred on it by Compose: the photo rose 12px over the meal bar (2026-09-23).
 *
 * Here the estimate repeats the measurement: each item at its natural width (its max intrinsic
 * width, capped at the line), wrapped at the same points, each line as tall as its tallest item at
 * that width. Items are measured against the whole line, so none is squeezed, and centred
 * vertically on their line.
 */
@Composable
internal fun WrappingRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val measurePolicy = remember(horizontalSpacing, verticalSpacing) {
        WrappingRowMeasurePolicy(horizontalSpacing, verticalSpacing)
    }
    Layout(content = content, modifier = modifier, measurePolicy = measurePolicy)
}

private class WrappingRowMeasurePolicy(
    private val horizontalSpacing: Dp,
    private val verticalSpacing: Dp,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val gap = horizontalSpacing.roundToPx()
        val lines = lines(placeables.map { it.width }, constraints.maxWidth, gap)
        val lineHeights = lines.map { line -> line.maxOf { placeables[it].height } }
        val height = lineHeights.sum() + verticalSpacing.roundToPx() * (lines.size - 1).coerceAtLeast(0)
        val width = lines.maxOfOrNull { line -> line.sumOf { placeables[it].width } + gap * (line.size - 1) } ?: 0
        return layout(
            constraints.constrainWidth(width),
            constraints.constrainHeight(height),
        ) {
            var y = 0
            lines.forEachIndexed { index, line ->
                var x = 0
                line.forEach { item ->
                    val placeable = placeables[item]
                    placeable.place(x, y + (lineHeights[index] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += lineHeights[index] + verticalSpacing.roundToPx()
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        estimatedHeight(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        estimatedHeight(measurables, width)

    /** One item per line: the narrowest the row can be. */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    /** Every item on one line. */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.maxIntrinsicWidth(height) } +
            horizontalSpacing.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

    private fun IntrinsicMeasureScope.estimatedHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val widths = measurables.map { minOf(it.maxIntrinsicWidth(Constraints.Infinity), width) }
        val heights = measurables.mapIndexed { index, item -> item.minIntrinsicHeight(widths[index]) }
        val lines = lines(widths, width, horizontalSpacing.roundToPx())
        return lines.sumOf { line -> line.maxOf { heights[it] } } +
            verticalSpacing.roundToPx() * (lines.size - 1).coerceAtLeast(0)
    }

    /** Item indices per line: an item starts a new line when it does not fit after the last. */
    private fun lines(widths: List<Int>, available: Int, gap: Int): List<List<Int>> {
        val lines = mutableListOf<MutableList<Int>>()
        var used = 0
        widths.forEachIndexed { index, itemWidth ->
            val current = lines.lastOrNull()
            if (current == null || used + gap + itemWidth > available) {
                lines += mutableListOf(index)
                used = itemWidth
            } else {
                current += index
                used += gap + itemWidth
            }
        }
        return lines
    }
}
