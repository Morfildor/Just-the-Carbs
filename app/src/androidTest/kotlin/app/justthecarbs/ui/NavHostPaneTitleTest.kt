package app.justthecarbs.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.JustTheCarbsApplication
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test

/**
 * The real [JustTheCarbsNavHost]: each destination names itself for TalkBack, and Home reports the
 * launch fully drawn once its Recents have loaded.
 *
 * Uses the application's own container, as the app does. Only destinations that neither look a
 * product up nor open a camera are visited, and none of them writes: the settings are passed in, and
 * Home, Settings, Meal, Search and manual entry only read until the user acts.
 *
 * **Instrumented: needs a device or emulator.**
 */
class NavHostPaneTitleTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as JustTheCarbsApplication).container

    private lateinit var nav: NavHostController

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    private fun showNavHost() {
        rule.setContent {
            nav = rememberNavController()
            JustTheCarbsTheme {
                JustTheCarbsNavHost(
                    container = container,
                    // Past both introductions, so the start destination is Home and no card competes.
                    settings = AppSettings(hasSeenOnboarding = true, hasSeenTutorial = true),
                    navController = nav,
                )
            }
        }
    }

    private fun goTo(route: String) {
        rule.runOnUiThread { nav.navigate(route) }
        rule.waitForIdle()
    }

    private fun assertOnlyPane(title: String, previous: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasPaneTitle(title)).fetchSemanticsNodes().size == 1 }
        // The screen left behind stops announcing itself once the transition has finished.
        rule.waitUntil(5_000) { rule.onAllNodes(hasPaneTitle(previous)).fetchSemanticsNodes().isEmpty() }
        rule.onAllNodes(hasPaneTitle(previous)).assertCountEquals(0)
    }

    @Test
    fun eachDestinationAnnouncesItsScreenTitle() {
        showNavHost()
        val home = context.getString(R.string.app_name)
        rule.waitUntil(5_000) { rule.onAllNodes(hasPaneTitle(home)).fetchSemanticsNodes().size == 1 }

        goTo("settings")
        assertOnlyPane(context.getString(R.string.settings_title), previous = home)

        goTo("meal")
        assertOnlyPane(context.getString(R.string.meal_title), previous = context.getString(R.string.settings_title))

        goTo("search")
        assertOnlyPane(context.getString(R.string.search_title), previous = context.getString(R.string.meal_title))

        goTo("manual")
        assertOnlyPane(context.getString(R.string.manual_title), previous = context.getString(R.string.search_title))
    }

    @Test
    fun homeReportsFullyDrawnOnceRecentsHaveLoaded() {
        showNavHost()

        rule.waitUntil(10_000) { rule.activity.fullyDrawnReporter.isFullyDrawnReported }
    }
}
