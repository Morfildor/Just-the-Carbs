package app.justthecarbs.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * The calculator's spoken result follows typing only once it settles (2026-09-25 review): a
 * polite live region re-announced "N grams of carbs" on every keystroke while the keyboard was up.
 */
class SettledAnnouncementTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun whileSettlingTheTextFollowsOnlyAfterItStopsChanging() {
        var text by mutableStateOf("6 grams")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Text("SAID ${rememberSettledText(text, settling = true, settleMs = 600L)}")
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("SAID 6 grams").assertExists()

        composeRule.runOnIdle { text = "65 grams" }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.runOnIdle { text = "650 grams" }
        composeRule.mainClock.advanceTimeBy(400L)
        // 700 ms of typing, but never 600 ms without a change: the old figure stands.
        composeRule.onNodeWithText("SAID 6 grams").assertExists()

        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText("SAID 650 grams").assertExists()
    }

    @Test
    fun notSettlingTheTextFollowsAtOnce() {
        var text by mutableStateOf("6 grams")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Text("SAID ${rememberSettledText(text, settling = false, settleMs = 600L)}")
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { text = "65 grams" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("SAID 65 grams").assertExists()
    }

    @Test
    fun whenSettlingEndsTheLatestTextFollowsAtOnce() {
        var text by mutableStateOf("6 grams")
        var settling by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Text("SAID ${rememberSettledText(text, settling = settling, settleMs = 600L)}")
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { text = "65 grams" }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithText("SAID 6 grams").assertExists()

        // The keyboard closes (Done): the figure the user is reading is spoken without waiting.
        composeRule.runOnIdle { settling = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("SAID 65 grams").assertExists()
    }
}
