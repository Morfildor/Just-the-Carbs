package app.justthecarbs.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SuccessPulseTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aNonNullTriggerShowsSuccessAndHoldsForTheGivenWindow() {
        var trigger by mutableStateOf<String?>(null)
        composeRule.setContent {
            val showing = rememberSuccessPulse(trigger, holdMs = 500L)
            Text(if (showing) "SHOWING" else "IDLE")
        }

        composeRule.onNodeWithText("IDLE").assertExists()
        composeRule.runOnIdle { trigger = "value-a" }
        composeRule.onNodeWithText("SHOWING").assertExists()

        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.onNodeWithText("IDLE").assertExists()
    }

    @Test
    fun aNewTriggerWhileShowingRestartsTheHoldFromTheLatestValue() {
        var trigger by mutableStateOf<String?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val showing = rememberSuccessPulse(trigger, holdMs = 500L)
            Text(if (showing) "SHOWING" else "IDLE")
        }

        composeRule.runOnIdle { trigger = "value-a" }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithText("SHOWING").assertExists()

        // Retrigger with a different value before the first hold expires.
        composeRule.runOnIdle { trigger = "value-b" }
        composeRule.mainClock.advanceTimeBy(400L)
        // 400ms after the SECOND trigger — still well inside its own 500ms hold.
        composeRule.onNodeWithText("SHOWING").assertExists()

        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.onNodeWithText("IDLE").assertExists()
    }

    // ---- rememberSuccessPulseSince: the hold is measured from the success itself --------------

    /** Composed well after the success: nothing to confirm (the meal buttons' re-entry case). */
    @Test
    fun aSuccessOlderThanTheHoldShowsNothingWhenComposedLater() {
        composeRule.setContent {
            val showing = rememberSuccessPulseSince(at = 1_000L, holdMs = 500L, now = { 10_000L })
            Text(if (showing) "SHOWING" else "IDLE")
        }
        composeRule.onNodeWithText("IDLE").assertExists()
    }

    /** Composed partway through the hold: shows only for what is left of it. */
    @Test
    fun aSuccessInsideTheHoldShowsOnlyForTheRemainder() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val showing = rememberSuccessPulseSince(at = 1_000L, holdMs = 500L, now = { 1_300L })
            Text(if (showing) "SHOWING" else "IDLE")
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("SHOWING").assertExists()

        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.onNodeWithText("IDLE").assertExists()
    }

    /** A time in the future (the clock moved back) holds nothing rather than holding forever. */
    @Test
    fun aSuccessInTheFutureShowsNothing() {
        composeRule.setContent {
            val showing = rememberSuccessPulseSince(at = 5_000L, holdMs = 500L, now = { 1_000L })
            Text(if (showing) "SHOWING" else "IDLE")
        }
        composeRule.onNodeWithText("IDLE").assertExists()
    }
}
