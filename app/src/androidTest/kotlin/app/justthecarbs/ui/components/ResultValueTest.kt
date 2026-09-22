package app.justthecarbs.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResultValueTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theNumeralAndUnitAreExposedAsOneCoherentAccessibleResult() {
        composeRule.setContent {
            JustTheCarbsTheme {
                ResultValue(
                    dominant = "31.2",
                    unit = "g",
                    accessibleLabel = "31.2 grams",
                    testTag = "result_under_test",
                )
            }
        }

        // The merged node carries the full spoken label...
        composeRule.onNodeWithTag("result_under_test")
            .assertContentDescriptionEquals("31.2 grams")
        // ...and the two constituent strings are still findable as text (rendering, not hidden).
        composeRule.onNodeWithText("31.2").assertExists()
        composeRule.onNodeWithText("g").assertExists()
    }

    @Test
    fun aLongValueDoesNotClipAndTheUnitStaysBesideIt() {
        composeRule.setContent {
            // Preserve the emulator's physical density: overriding it with 3.0 on CI's 320px-wide,
            // 160dpi device turns a 200dp box into a viewport that cannot exist on that device.
            // A 280dp result on its 320dp window leaves room for margins while still requiring
            // auto-size to fit this unusually long value at a 2x font scale.
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 2.0f),
            ) {
                JustTheCarbsTheme {
                    Box(modifier = Modifier.width(280.dp)) {
                        ResultValue(
                            dominant = "1234.5",
                            unit = "g",
                            accessibleLabel = "1234.5 grams",
                            testTag = "long_result",
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("long_result").assertExists()
        composeRule.onNodeWithText("g").assertExists()

        // Ask the Text itself whether it overflowed, via GetTextLayoutResult — a plain bounds
        // comparison does not work here (a constrained Text reports its already-constrained size,
        // so it can never disagree with itself even when digits are visibly cut off); see
        // ProductScreenTest.theResultIsNotClippedAtTheLargestFontScale for the same technique and
        // the fuller explanation of why it is necessary.
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("1234.5")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.single()

        assertTrue(
            "The numeral overflows its box: laid out at ${layout.size.width}x" +
                "${layout.size.height}px, longest line ${layout.multiParagraph.maxIntrinsicWidth}px",
            !layout.hasVisualOverflow,
        )
        // A truncated result would still satisfy a bounds/overflow check by simply being a shorter
        // string, so the value itself is asserted too, via the merged accessible description — the
        // same defense ProductScreenTest applies for the identical reason.
        composeRule.onNodeWithTag("long_result").assertContentDescriptionEquals("1234.5 grams")
    }

    /**
     * The widest result the calculator can actually be asked to render, in the box the calculator
     * actually gives it, at the largest font scale the app documents as supported.
     *
     * **This case carries the invariant that `ProductScreenTest` used to assert against the whole
     * screen.** That test hung indefinitely and was not flaky: invoking the
     * `GetTextLayoutResult` semantics action against an `autoSize` Text nested in ProductScreen's
     * scrolling zone leaves the composition permanently non-idle, so the NEXT call that waits for
     * idle never returns -- `waitForIdle`, `performScrollTo`, or an assertion, whichever came
     * first. Measured, not guessed: a thread dump showed the main thread spinning in
     * `ComposeIdlingResource.checkLayoutBusy`, while the very same action on this component
     * settles in milliseconds, and ProductScreen itself settles fine until the action is invoked.
     *
     * So the invariant moved to the smallest production component that can prove it -- this one,
     * which is the real `ResultValue` the real screen composes, not a stand-in. The geometry is
     * the screen's own: 320dp is the narrowest window the app supports and 80dp is the height of
     * ProductScreen's result slot. `ProductScreenTest` keeps the parts it can prove without the
     * hanging action: that the full value is present and nothing is truncated.
     *
     * 125.3 g is 260 g of a 48.2 g/100 g product -- three digits plus a decimal.
     */
    @Test
    fun theWidestRealResultFitsTheScreensOwnSlotAtTheLargestFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 2.0f),
            ) {
                JustTheCarbsTheme {
                    // ProductScreen's own geometry: its narrowest supported window, and the fixed
                    // height of its result slot. A result that fits here fits on the screen.
                    Box(modifier = Modifier.width(320.dp)) {
                        Box(modifier = Modifier.height(80.dp)) {
                            ResultValue(
                                dominant = "125.3",
                                unit = "g",
                                accessibleLabel = "125.3 grams",
                                testTag = "widest_real_result",
                            )
                        }
                    }
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("125.3")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.single()

        assertTrue(
            "The result overflows the screen's own slot: laid out at ${layout.size.width}x" +
                "${layout.size.height}px, longest line ${layout.multiParagraph.maxIntrinsicWidth}px, " +
                "width overflow ${layout.didOverflowWidth}, height overflow ${layout.didOverflowHeight}, " +
                "line count ${layout.lineCount}",
            !layout.hasVisualOverflow,
        )
        // A clipped result would still pass an overflow check by simply being a shorter string, so
        // the value itself is asserted too -- and the unit must still be beside it, since a numeral
        // that fits only because its unit was pushed off is not a result anyone can read.
        composeRule.onNodeWithTag("widest_real_result")
            .assertContentDescriptionEquals("125.3 grams")
        composeRule.onNodeWithText("g").assertExists()
    }
}
