package app.justthecarbs.ui.components

import androidx.compose.foundation.layout.Box
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
}
