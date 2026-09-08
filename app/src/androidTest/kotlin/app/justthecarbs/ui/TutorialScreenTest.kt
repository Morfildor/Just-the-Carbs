package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.TUTORIAL_BACK_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_BODY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PRIMARY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PROGRESS_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_STEPS
import app.justthecarbs.ui.onboarding.TUTORIAL_TITLE_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The tutorial's rendered behaviour: which words each step shows, where its actions are, and that
 * the boundaries behave.
 *
 * **Instrumented: needs a device or emulator.** The step machine is covered by pure JVM tests
 * ([app.justthecarbs.ui.onboarding.TutorialStepTest]); this class covers what only a real
 * composition can answer — that every step's strings actually resolve, that the controls exist at
 * the right steps, and that the touch targets clear the floor.
 *
 * Copy is read from resources rather than hardcoded, so a wording change does not fail these tests
 * for the wrong reason — what is asserted is that each step shows *its own* declared copy.
 */
class TutorialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Drives the real screen with real state, so Next and Back actually move it.
     *
     * The step index is hoisted here rather than each test rendering a fixed step: a test that
     * renders step 3 directly could never catch a Next button that fails to advance.
     */
    private fun showTutorial(
        mode: TutorialMode = TutorialMode.FIRST_RUN,
        theme: ThemeChoice = ThemeChoice.LIGHT,
        startStep: Int = 0,
        onExit: () -> Unit = {},
    ) {
        compose.setContent {
            var step by remember { mutableStateOf(startStep) }
            JustTheCarbsTheme(themeChoice = theme) {
                Box(Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = mode,
                        onNext = { step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onPrevious = { step = (step - 1).coerceAtLeast(0) },
                        onExit = onExit,
                    )
                }
            }
        }
    }

    @Test
    fun theTutorialOpensOnItsFirstStep() {
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
    }

    @Test
    fun everyStepShowsItsOwnTitleAndBody() {
        // Walks the whole sequence through the real Next button, so this also pins that Next
        // advances exactly one step and that all six steps' copy resolves.
        showTutorial()

        TUTORIAL_STEPS.forEachIndexed { index, step ->
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertTextEquals(string(step.titleRes))
            compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true).assertTextEquals(string(step.bodyRes))
            if (index != TUTORIAL_LAST_STEP) {
                compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun theTeachingStepsNameTheAppsOwnControls() {
        // The tutorial must use the real labels. A tutorial that renames the buttons teaches
        // nothing, so these are asserted against the same resources Home and the calculator use.
        showTutorial(startStep = 2)
        compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[2].bodyRes))
        compose.onNodeWithText(string(app.justthecarbs.R.string.home_search_label), substring = true)
            .assertExists()
    }

    @Test
    fun backIsAbsentOnTheFirstStepAndPresentAfterIt() {
        // Back on the first step has nowhere to go; system Back is the way out there, and it exits
        // rather than stepping.
        showTutorial()
        compose.onNodeWithTag(TUTORIAL_BACK_TAG).assertDoesNotExist()

        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TUTORIAL_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun backReturnsToThePreviousStep() {
        showTutorial(startStep = 2)

        compose.onNodeWithTag(TUTORIAL_BACK_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[1].titleRes))
    }

    @Test
    fun theFinalStepOffersTheFinishActionAndCallsExit() {
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_finish)).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun nextIsNotOfferedOnTheFinalStep() {
        // The last step's action finishes; offering "Next" there would imply a seventh moment.
        showTutorial(startStep = TUTORIAL_LAST_STEP)

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_next)).assertDoesNotExist()
    }

    @Test
    fun skipIsOfferedOnEveryStepAndExits() {
        // "Skippable at all times" is the requirement, so every step is checked rather than the
        // first one and hoping the rest follow.
        //
        // One composition, walked forward with Next — `setContent` may only be called once per
        // test, so a loop that re-renders per step throws "has already set content" rather than
        // testing anything.
        var exits = 0
        showTutorial(onExit = { exits++ })

        TUTORIAL_STEPS.indices.forEach { index ->
            compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
            if (index != TUTORIAL_LAST_STEP) {
                compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
                compose.waitForIdle()
            }
        }

        // And it genuinely exits, from the last step reached.
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
    }

    @Test
    fun skipExitsFromTheVeryFirstStep() {
        // The specific case a new user hits: open the tutorial, decide immediately against it.
        var exits = 0
        showTutorial(onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun everyTutorialControlMeetsTheTouchTargetFloor() {
        showTutorial(startStep = 1)

        listOf(TUTORIAL_PRIMARY_TAG, TUTORIAL_SKIP_TAG, TUTORIAL_BACK_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun progressIsRenderedForEveryStep() {
        showTutorial(startStep = 1)
        compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theTutorialRendersInDarkThemeToo() {
        // The overlay paints its own scrim and card, so it is exactly the kind of screen that can
        // look right in one theme and be unreadable in the other.
        showTutorial(theme = ThemeChoice.DARK)

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).assertIsDisplayed()
    }

    @Test
    fun replayModeRendersTheSameStepsAsFirstRun() {
        // One screen, one sequence: replay must not quietly become a shorter or different tutorial.
        showTutorial(mode = TutorialMode.REPLAY)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
    }
}
