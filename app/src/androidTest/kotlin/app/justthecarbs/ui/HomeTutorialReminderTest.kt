package app.justthecarbs.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.home.HOME_SCAN_BARCODE_TAG
import app.justthecarbs.ui.home.HOME_TUTORIAL_DISMISS_TAG
import app.justthecarbs.ui.home.HOME_TUTORIAL_REMINDER_TAG
import app.justthecarbs.ui.home.HOME_TUTORIAL_START_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Home's tutorial invitation.
 *
 * The rule deciding *when* it appears is pure and covered by
 * [app.justthecarbs.domain.TutorialReminderTest]; this covers what only a composition can answer —
 * that the card renders when told to, that both of its actions work, and that it does not push the
 * app's real entry points off the screen.
 *
 * **Instrumented: needs a device or emulator.**
 */
class HomeTutorialReminderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun showHome(
        show: Boolean,
        theme: ThemeChoice = ThemeChoice.LIGHT,
        onStart: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme(themeChoice = theme) {
                HomeScreen(
                    recents = emptyList(),
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    showTutorialReminder = show,
                    onStartTutorial = onStart,
                    onDismissTutorialReminder = onDismiss,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theReminderIsShownWhenTheRuleSaysSo() {
        showHome(show = true)

        compose.onNodeWithTag(HOME_TUTORIAL_REMINDER_TAG).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.home_tutorial_reminder_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.home_tutorial_reminder_body)).assertIsDisplayed()
    }

    @Test
    fun theReminderIsAbsentWhenTheRuleSaysNot() {
        showHome(show = false)

        compose.onNodeWithTag(HOME_TUTORIAL_REMINDER_TAG).assertDoesNotExist()
    }

    @Test
    fun takingTheReminderStartsTheTutorial() {
        var started = 0
        showHome(show = true, onStart = { started++ })

        compose.onNodeWithTag(HOME_TUTORIAL_START_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, started)
    }

    @Test
    fun dismissingTheReminderReportsItAndDoesNotStartTheTutorial() {
        var started = 0
        var dismissed = 0
        showHome(show = true, onStart = { started++ }, onDismiss = { dismissed++ })

        compose.onNodeWithTag(HOME_TUTORIAL_DISMISS_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals("dismissing must not open the tutorial", 0, started)
    }

    @Test
    fun theRealEntryPointsAreStillPresentAlongsideTheReminder() {
        // The card sits above the scan actions, so the risk it introduces is pushing the app's
        // actual way in off the first screen a new user sees.
        showHome(show = true)

        compose.onNodeWithTag(HOME_TUTORIAL_REMINDER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_SCAN_BARCODE_TAG).assertIsDisplayed()
    }

    @Test
    fun bothReminderActionsMeetTheTouchTargetFloor() {
        showHome(show = true)

        listOf(HOME_TUTORIAL_START_TAG, HOME_TUTORIAL_DISMISS_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun theReminderRendersInDarkThemeToo() {
        showHome(show = true, theme = ThemeChoice.DARK)

        compose.onNodeWithTag(HOME_TUTORIAL_REMINDER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_TUTORIAL_START_TAG).assertIsDisplayed()
    }
}
