package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.product.PackShortcuts
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The pack shortcuts must stay readable when the text grows.
 *
 * The defect this pins is real and was reported from the device: at a large font scale on a narrow
 * screen, three equal `weight(1f)` buttons give each label a third of the width, and `Full pack`
 * -- the only multi-word label of the three -- rendered as `Full`. A shortcut that silently loses
 * half its name is worse than one that takes a second line, because `Full` and `½ pack` read as
 * the same kind of thing and the user cannot tell which they tapped.
 *
 * Asserted through the Text's own layout rather than by eye: `hasVisualOverflow` is what reports
 * a clipped or ellipsised line, and a bounds comparison cannot see it (a constrained Text reports
 * its constrained size, so it can never disagree with itself).
 *
 * **Deliberately a component test.** Driving this through the whole ProductScreen at a 2x font
 * scale hits the non-idle composition documented in
 * `ProductScreenTest.theResultIsNotClippedAtTheLargestFontScale`, where the same semantics action
 * hangs indefinitely. This is the smallest real production composable that can prove the property.
 *
 * **Instrumented: needs a device or emulator.**
 */
class PackShortcutsResponsiveTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showAt(widthDp: Int, fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(
                // Keep the device's own density and raise only the font scale: imposing a density
                // invents a window width that device cannot have.
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                JustTheCarbsTheme {
                    Box(Modifier.width(widthDp.dp)) {
                        PackShortcuts(pack = BigDecimal("500"), onSetPortion = {})
                    }
                }
            }
        }
    }

    /**
     * Every label renders all of its own characters.
     *
     * The property asserted is that **the last character of the label is actually laid out** --
     * `getLineEnd(lastLine)` reaching the end of the string. That is precisely what clipping and
     * ellipsis destroy, and it is what the reported defect looked like: `Full pack` rendering as
     * `Full`.
     *
     * Deliberately NOT `hasVisualOverflow`. That was the first assertion written here and it is
     * too strict to be useful: a label given exactly the width it asks for reports overflow on a
     * sub-pixel rounding boundary (measured: 127.5px of text in a 128px box, every character
     * present), so it failed the ORDINARY 411dp/1.0x case -- identically at baseline, before any
     * change, which is how it was caught. An assertion that fails on correct rendering would have
     * been "fixed" by loosening the layout rather than by fixing the defect.
     *
     * The line count is still bounded, so a label cannot pass by wrapping without limit.
     */
    private fun assertNoLabelIsTruncated(vararg labels: String) {
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action
                ?.invoke(layouts)
            val layout = layouts.single()

            val lastLine = layout.lineCount - 1
            val charactersRendered = layout.getLineEnd(lastLine, visibleEnd = true)
            assertTrue(
                "\"$label\" is truncated: only $charactersRendered of ${label.length} characters " +
                    "are rendered, across ${layout.lineCount} line(s), laid out at " +
                    "${layout.size.width}x${layout.size.height}px (longest line " +
                    "${layout.multiParagraph.maxIntrinsicWidth}px)",
                charactersRendered == label.length,
            )
            assertTrue(
                "\"$label\" wrapped to ${layout.lineCount} lines; the button allows at most 2",
                layout.lineCount <= 2,
            )
        }
    }

    /**
     * THE REGRESSION. 320dp is the narrowest window the app supports and 1.8x is the font scale it
     * documents; this is the exact configuration in which `Full pack` became `Full`.
     */
    @Test
    fun noPackShortcutLabelIsTruncatedOnANarrowScreenAtALargeFontScale() {
        showAt(widthDp = 320, fontScale = 1.8f)
        assertNoLabelIsTruncated("¼ pack", "½ pack", "Full pack")
    }

    /** 2.0x is Android's own accessibility maximum -- above what the app promises, still readable. */
    @Test
    fun noPackShortcutLabelIsTruncatedAtTheAndroidMaximumFontScale() {
        showAt(widthDp = 320, fontScale = 2.0f)
        assertNoLabelIsTruncated("¼ pack", "½ pack", "Full pack")
    }

    /** The ordinary case must be untouched by whatever makes the large case fit. */
    @Test
    fun theOrdinaryCaseIsStillUntruncated() {
        showAt(widthDp = 411, fontScale = 1.0f)
        assertNoLabelIsTruncated("¼ pack", "½ pack", "Full pack")
    }

    /**
     * When a label wraps, the whole row grows with it -- one control group, one height.
     *
     * Pinned because the first version of the wrap fix left `Full pack` 92px taller than `¼ pack`
     * and `½ pack` in the same row (226px against 134px at 1.8x on a 411dp screen), seen in a
     * device screenshot and in no assertion. Three shortcuts of two different heights no longer
     * read as one row of shortcuts.
     */
    @Test
    fun theThreeButtonsShareOneHeightWhenALabelWraps() {
        showAt(widthDp = 320, fontScale = 1.8f)
        val heights = listOf("¼ pack", "½ pack", "Full pack").associateWith {
            compose.onNodeWithText(it).fetchSemanticsNode().size.height
        }
        assertTrue("buttons differ in height: $heights", heights.values.distinct().size == 1)
    }

    @Test
    fun theThreeButtonsShareOneHeightAtTheOrdinarySize() {
        showAt(widthDp = 411, fontScale = 1.0f)
        val heights = listOf("¼ pack", "½ pack", "Full pack").associateWith {
            compose.onNodeWithText(it).fetchSemanticsNode().size.height
        }
        assertTrue("buttons differ in height: $heights", heights.values.distinct().size == 1)
    }

    /** All three shortcuts survive whatever the layout does -- none is dropped to make room. */
    @Test
    fun allThreeShortcutsStillExistAtALargeFontScale() {
        showAt(widthDp = 320, fontScale = 1.8f)
        compose.onNodeWithText("¼ pack").assertExists()
        compose.onNodeWithText("½ pack").assertExists()
        compose.onNodeWithText("Full pack").assertExists()
    }
}
