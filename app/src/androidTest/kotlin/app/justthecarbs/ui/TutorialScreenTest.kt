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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.TUTORIAL_BODY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PRIMARY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_STEPS
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_SURFACE_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_TITLE_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The tutorial's rendered behaviour under the tap-anywhere contract: a tap almost anywhere advances
 * one step; Skip is the one deliberate exception and always wins hit-testing on its own bounds; the
 * final step's tap finishes instead of advancing.
 *
 * **Instrumented: needs a device or emulator.** The step machine is covered by pure JVM tests
 * ([app.justthecarbs.ui.onboarding.TutorialStepTest]); this class covers what only a real
 * composition can answer.
 *
 * Copy is read from resources rather than hardcoded, so a wording change does not fail these tests
 * for the wrong reason.
 */
class TutorialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Drives the real screen with real state, so a tap actually moves it.
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
                        onExit = onExit,
                    )
                }
            }
        }
    }

    private fun tapTapSurface() {
        compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click() }
        compose.waitForIdle()
    }

    @Test
    fun theTutorialOpensOnItsFirstStep() {
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
    }

    @Test
    fun tappingTheFullScreenSurfaceAdvancesExactlyOneStep() {
        // The core contract: a tap on open scrim -- not on any specific control -- advances.
        showTutorial()

        tapTapSurface()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[1].titleRes))
    }

    @Test
    fun repeatedTapsWalkTheWholeSequenceAndAllSixStepsCopyResolves() {
        showTutorial()

        TUTORIAL_STEPS.forEachIndexed { index, step ->
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertTextEquals(string(step.titleRes))
            compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true).assertTextEquals(string(step.bodyRes))
            if (index != TUTORIAL_LAST_STEP) {
                tapTapSurface()
            }
        }
    }

    @Test
    fun theTeachingStepsNameTheAppsOwnControls() {
        showTutorial(startStep = 2)
        compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[2].bodyRes))
        compose.onNodeWithText(string(app.justthecarbs.R.string.home_search_label), substring = true)
            .assertExists()
    }

    @Test
    fun tappingTheCalloutCardBackgroundAlsoAdvances() {
        // The card has no clickable of its own on its background, so a tap there falls through to
        // the tap surface beneath it exactly like a tap on open scrim -- pinned here because it is
        // the specific case the spec calls out: the card must not need to reimplement "tap advances".
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[1].titleRes))
    }

    @Test
    fun tappingSkipExitsWithoutAdvancingAndNeverBothFire() {
        // The load-bearing guarantee from the design: a tap landing on Skip's own bounds must exit,
        // and must NOT also advance the step -- the two must never both fire from one tap.
        var exits = 0
        var nextCalls = 0
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            JustTheCarbsTheme {
                Box(Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = TutorialMode.FIRST_RUN,
                        onNext = { nextCalls++; step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onExit = { exits++ },
                    )
                }
            }
        }

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
        assertEquals(0, nextCalls)
    }

    @Test
    fun theFinalStepsTapFinishesInsteadOfAdvancing() {
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_finish)).assertIsDisplayed()
        tapTapSurface()

        assertEquals(1, exits)
    }

    @Test
    fun theFinalStepsPrimaryButtonAlsoFinishes() {
        // The button remains a working, labelled alternative to the tap-anywhere surface -- the
        // path a TalkBack user relies on, since the full-screen surface is decorative to them.
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun nextIsNotOfferedOnTheFinalStep() {
        showTutorial(startStep = TUTORIAL_LAST_STEP)

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_next)).assertDoesNotExist()
    }

    @Test
    fun skipIsOfferedOnEveryStepAndExits() {
        var exits = 0
        showTutorial(onExit = { exits++ })

        TUTORIAL_STEPS.indices.forEach { index ->
            compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
            if (index != TUTORIAL_LAST_STEP) {
                tapTapSurface()
            }
        }

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
    }

    @Test
    fun skipExitsFromTheVeryFirstStep() {
        var exits = 0
        showTutorial(onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun everyTutorialControlMeetsTheTouchTargetFloor() {
        showTutorial(startStep = 1)

        listOf(TUTORIAL_PRIMARY_TAG, TUTORIAL_SKIP_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun backButtonNoLongerExists() {
        // Forward-only tour: there is no Back control anywhere in the tutorial now. TUTORIAL_BACK_TAG
        // and its string resource (tutorial_back) were both deleted along with the composable, so the
        // absence is asserted against the literal tag the old control used to carry.
        showTutorial(startStep = 2)
        compose.onNodeWithTag("tutorial_back").assertDoesNotExist()
    }

    @Test
    fun theStepCountTextIsExactlyStepOneOfSix() {
        // "Step 1 of 6" is the one remaining progress indicator, asserted as the literal rendered
        // string rather than merely "a text node exists" -- tutorial_progress is "Step %1$d of %2$d"
        // and TUTORIAL_STEPS.size is 6 (both confirmed in strings.xml / TutorialStep.kt), so this
        // proves the words-only replacement for the dot row actually renders, not just that some
        // node is present.
        showTutorial(startStep = 0)
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
    }

    @Test
    fun theTutorialRendersInDarkThemeToo() {
        showTutorial(theme = ThemeChoice.DARK)

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).assertIsDisplayed()
    }

    @Test
    fun replayModeRendersTheSameStepsAsFirstRun() {
        showTutorial(mode = TutorialMode.REPLAY)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
    }
}
