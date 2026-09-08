package app.justthecarbs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.domain.TutorialReminder
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.OnboardingViewModel
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PRIMARY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tutorial routing: how it is entered, what each mode does on exit, and what each writes.
 *
 * The graph here is a small stand-in rather than the whole [JustTheCarbsNavHost]: building the real
 * one requires an `AppContainer`, which opens Room and constructs the network stack — precisely the
 * things this tutorial must never touch, and a poor thing to instantiate in a test about navigation.
 * What is under test is the routing *rule*, wired exactly as production wires it: same ViewModel,
 * same modes, same `Saved`-gated exit, same pop-on-leave.
 *
 * The repository is a real temp-file DataStore, so the persistence assertions are about genuine
 * writes rather than an in-memory flag that cannot fail.
 *
 * **Instrumented: needs a device or emulator.**
 */
class TutorialNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private fun freshRepository(): SettingsRepository {
        val dir = File.createTempFile("tutorial-nav-test", "").apply { delete(); mkdirs() }
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsRepository.forTesting(store)
    }

    private lateinit var nav: NavHostController

    private fun route(): String? = nav.currentBackStackEntry?.destination?.route

    /**
     * Home, Settings and the tutorial, wired the way production wires them.
     *
     * Home renders the reminder from the same [TutorialReminder] rule the real screen uses, so the
     * "offered, not imposed" behaviour is exercised rather than assumed.
     */
    @Composable
    private fun TestGraph(repository: SettingsRepository) {
        nav = rememberNavController()
        val settings by repository.settings.collectAsState(initial = app.justthecarbs.domain.AppSettings())
        val scope = rememberCoroutineScope()

        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                Column {
                    Text("HOME")
                    if (TutorialReminder.shouldShow(settings.hasSeenOnboarding, settings.launchCount)) {
                        Text(
                            text = "Show me",
                            modifier = Modifier.clickable { nav.navigate("onboarding?replay=false") },
                        )
                        Text(
                            text = "No thanks",
                            modifier = Modifier.clickable {
                                scope.launch { repository.setHasSeenOnboarding(true) }
                            },
                        )
                    }
                }
            }
            composable("settings") {
                Column {
                    Text("SETTINGS")
                    Text(
                        text = "Replay tutorial",
                        modifier = Modifier.clickable { nav.navigate("onboarding?replay=true") },
                    )
                }
            }
            composable(
                route = "onboarding?replay={replay}",
                arguments = listOf(
                    navArgument("replay") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val replay = entry.arguments?.getBoolean("replay") ?: false
                val mode = if (replay) TutorialMode.REPLAY else TutorialMode.FIRST_RUN
                val viewModel = remember(mode) { OnboardingViewModel(repository, mode) }
                val stepIndex by viewModel.stepIndex.collectAsState()
                val completion by viewModel.completionState.collectAsState()
                val exitScope = rememberCoroutineScope()

                LaunchedEffect(completion) {
                    if (completion is OnboardingViewModel.CompletionState.Saved) nav.popBackStack()
                }

                OnboardingScreen(
                    stepIndex = stepIndex,
                    mode = mode,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onExit = { exitScope.launch { viewModel.finish() } },
                    busy = completion is OnboardingViewModel.CompletionState.Saving,
                )
            }
        }
    }

    private fun start(repository: SettingsRepository) {
        compose.setContent { JustTheCarbsTheme { TestGraph(repository) } }
        compose.waitForIdle()
    }

    // ---- the tutorial is offered, never imposed -----------------------------------------------

    @Test
    fun aFirstLaunchOpensHomeAndNotTheTutorial() {
        // The whole point of the owner's change: an experienced user reinstalling the app is never
        // held up by a walkthrough. Home first, always.
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)

        assertEquals("home", route())
        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun aFirstLaunchStillOffersTheTutorialOnHome() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)

        compose.onNodeWithText("Show me").assertIsDisplayed()
    }

    @Test
    fun theReminderOpensTheTutorialWhenTaken() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)

        compose.onNodeWithText("Show me").performClick()
        compose.waitForIdle()

        assertTrue("was ${route()}", route()?.startsWith("onboarding") == true)
        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
    }

    @Test
    fun dismissingTheReminderRetiresItPermanentlyWithoutOpeningTheTutorial() {
        // The reinstalling-expert path: one tap on Home and the app never asks again.
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)

        compose.onNodeWithText("No thanks").performClick()
        compose.waitForIdle()

        assertEquals("home", route())
        compose.onNodeWithText("Show me").assertDoesNotExist()
        assertTrue(runBlocking { repository.settings.first().hasSeenOnboarding })
    }

    @Test
    fun theReminderIsGoneOnceOnboardingHasBeenSeen() {
        val repository = freshRepository()
        runBlocking {
            repository.recordLaunch()
            repository.setHasSeenOnboarding(true)
        }
        start(repository)

        compose.onNodeWithText("Show me").assertDoesNotExist()
    }

    @Test
    fun theReminderStopsAfterItsWindow() {
        val repository = freshRepository()
        // One more launch than the window allows.
        runBlocking { repeat(TutorialReminder.REMINDER_LAUNCHES + 2) { repository.recordLaunch() } }
        start(repository)

        compose.onNodeWithText("Show me").assertDoesNotExist()
        // And it retired by running out of launches, not by pretending onboarding was completed.
        assertFalse(runBlocking { repository.settings.first().hasSeenOnboarding })
    }

    // ---- exits ---------------------------------------------------------------------------------

    @Test
    fun skippingTheTutorialPersistsOnboardingAndReturnsToHome() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)
        compose.onNodeWithText("Show me").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals("home", route())
        // Skip is a real exit, not a deferral: the reminder must not come back.
        assertTrue(runBlocking { repository.settings.first().hasSeenOnboarding })
        compose.onNodeWithText("Show me").assertDoesNotExist()
    }

    @Test
    fun finishingTheTutorialPersistsOnboardingAndReturnsToHome() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)
        compose.onNodeWithText("Show me").performClick()
        compose.waitForIdle()

        repeat(TUTORIAL_LAST_STEP + 1) {
            compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
            compose.waitForIdle()
        }

        assertEquals("home", route())
        assertTrue(runBlocking { repository.settings.first().hasSeenOnboarding })
    }

    @Test
    fun replayExitsBackToSettingsAndNotHome() {
        // The single most important replay behaviour: it returns the user where they started it.
        val repository = freshRepository()
        runBlocking { repository.setHasSeenOnboarding(true) }
        start(repository)

        compose.runOnUiThread { nav.navigate("settings") }
        compose.waitForIdle()
        compose.onNodeWithText("Replay tutorial").performClick()
        compose.waitForIdle()
        assertTrue("was ${route()}", route()?.startsWith("onboarding") == true)

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals("settings", route())
    }

    @Test
    fun replayDoesNotAlterOnboardingState() {
        val repository = freshRepository()
        runBlocking { repository.setHasSeenOnboarding(true) }
        start(repository)

        compose.runOnUiThread { nav.navigate("settings") }
        compose.waitForIdle()
        compose.onNodeWithText("Replay tutorial").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        // Still true, and never cleared: a replay has no business editing the flag in either
        // direction.
        assertTrue(runBlocking { repository.settings.first().hasSeenOnboarding })
    }
}
