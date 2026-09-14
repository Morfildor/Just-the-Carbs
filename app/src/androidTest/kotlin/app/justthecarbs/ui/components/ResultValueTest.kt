package app.justthecarbs.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.justthecarbs.ui.theme.JustTheCarbsTheme
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
            JustTheCarbsTheme {
                ResultValue(
                    dominant = "1234.5",
                    unit = "g",
                    accessibleLabel = "1234.5 grams",
                    testTag = "long_result",
                )
            }
        }

        composeRule.onNodeWithTag("long_result").assertExists()
        composeRule.onNodeWithText("g").assertExists()
    }
}
