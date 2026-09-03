package app.justthecarbs.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JtcTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theTitleAndBackActionAreShown() {
        var backs = 0
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Meal", destination = Destination.MEAL, onBack = { backs++ })
            }
        }
        rule.onNodeWithText("Meal").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aBarWithNoBackActionRendersNoBackButton() {
        // Home has no back affordance. Passing null must remove the control, not disable it — a
        // dead button is worse than an absent one.
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Just the Carbs", destination = Destination.HOME)
            }
        }
        rule.onNodeWithText("Just the Carbs").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }

    @Test
    fun theTrailingSlotRenders() {
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Meal", destination = Destination.MEAL) {
                    Text("Clear")
                }
            }
        }
        rule.onNodeWithText("Clear").assertIsDisplayed()
    }
}
