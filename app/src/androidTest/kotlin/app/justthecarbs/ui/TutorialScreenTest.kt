package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.TUTORIAL_BODY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_CALLOUT_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_STEPS
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_AFFORDANCE_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_SURFACE_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_TITLE_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The tutorial's rendered behaviour under the tap-anywhere contract: a tap almost anywhere advances
 * one step, including a tap directly over the highlighted control; Skip is the one deliberate
 * exception and always wins hit-testing on its own bounds; the final step's tap finishes instead of
 * advancing. There is no visible Next/Finish button anywhere on screen — see
 * [app.justthecarbs.ui.onboarding.OnboardingScreen]'s `CalloutCard` for why, and for how TalkBack
 * still gets a real advance action through semantics rather than a pointer-input control.
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
        fontScale: Float? = null,
        onExit: () -> Unit = {},
    ) {
        compose.setContent {
            var step by remember { mutableStateOf(startStep) }
            JustTheCarbsTheme(themeChoice = theme) {
                val content = @Composable {
                    Box(Modifier.fillMaxSize()) {
                        OnboardingScreen(
                            stepIndex = step,
                            mode = mode,
                            onNext = { step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                            onExit = onExit,
                        )
                    }
                }
                if (fontScale != null) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(base.density, fontScale),
                    ) { content() }
                } else {
                    content()
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
    fun tappingDirectlyOverTheHighlightedControlStillAdvances() {
        // The spotlight is visual context, never a hole cut out of the tap-anywhere surface. A tap
        // landing at the on-screen coordinates of the control the step is pointing at -- e.g. right
        // on top of the drawn "Scan barcode" card in the backdrop -- must behave exactly like any
        // other tap on open scrim, because the backdrop is entirely non-interactive.
        showTutorial(startStep = 1) // SCAN_BARCODE anchor

        val surface = compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).fetchSemanticsNode()
        val bounds = surface.boundsInRoot
        val overTheHighlight = Offset(bounds.left + bounds.width * 0.3f, bounds.top + bounds.height * 0.3f)

        compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click(overTheHighlight) }
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[2].titleRes))
    }

    @Test
    fun tappingNearTheScreenEdgesStillAdvances() {
        // The tap-anywhere surface fills the screen; a tap close to any edge -- where a thumb
        // reaching around a large phone is likely to land -- must not miss it.
        showTutorial(startStep = 1)

        val surface = compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).fetchSemanticsNode()
        val bounds = surface.boundsInRoot
        val margin = 4f
        val corners = listOf(
            Offset(bounds.left + margin, bounds.top + margin),
            Offset(bounds.right - margin, bounds.top + margin),
            Offset(bounds.left + margin, bounds.bottom - margin),
        )

        corners.forEachIndexed { index, corner ->
            compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click(corner) }
            compose.waitForIdle()
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
                .assertTextEquals(string(TUTORIAL_STEPS[2 + index].titleRes))
        }
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
        // The affordance line is decorative (clearAndSetSemantics { testTag = ... }), so its own
        // text is not queryable by hasText at all -- only the tag survives the semantics wipe (see
        // OnboardingScreen), and useUnmergedTree = true is still required since a tag on a cleared
        // node does not merge upward either. Its exact wording is pinned separately by
        // TutorialStepTest / strings.xml; this test's job is the tap-anywhere-finishes behaviour.
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_TAP_AFFORDANCE_TAG, useUnmergedTree = true).assertIsDisplayed()
        tapTapSurface()

        assertEquals(1, exits)
    }

    @Test
    fun theCardsAccessibilityActionAlsoFinishesOnTheFinalStep() {
        // TalkBack's path: the callout card carries a real onClick accessibility action -- labelled
        // from the same tutorial_finish string the old visible button used -- so explore-by-touch and
        // the double-tap gesture still reach Finish, with no visible button anywhere on screen.
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_CALLOUT_TAG)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun theCardsAccessibilityActionAdvancesOnAnOrdinaryStep() {
        var nextCalls = 0
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            JustTheCarbsTheme {
                Box(Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = TutorialMode.FIRST_RUN,
                        onNext = { nextCalls++; step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onExit = {},
                    )
                }
            }
        }

        compose.onNodeWithTag(TUTORIAL_CALLOUT_TAG)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()

        assertEquals(1, nextCalls)
    }

    @Test
    fun noVisiblePrimaryButtonExistsAnywhereInTheTutorial() {
        // The implementation-miss this pass closes: tap-anywhere is the sighted-user progression
        // model, so there must be no Next/Finish button a sighted user can see and tap as a second,
        // competing way to advance. Asserted on every step, not just the last.
        showTutorial()

        TUTORIAL_STEPS.indices.forEach { index ->
            compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_next)).assertDoesNotExist()
            compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_finish)).assertDoesNotExist()
            if (index != TUTORIAL_LAST_STEP) tapTapSurface()
        }
    }

    @Test
    fun theTapToContinueAffordanceIsPurelyVisualAndUnannounced() {
        // The restrained "Tap anywhere to continue" line is decoration restating the card's own
        // accessibility action. It must render for a sighted user -- found and displayed by its own
        // tag, set INSIDE clearAndSetSemantics {} in OnboardingScreen so it survives the wipe -- but
        // it must carry no Text semantics of its own, so TalkBack's word-based navigation and any
        // hasText query can never find "Tap anywhere to continue" as spoken content. That absence is
        // asserted directly by text, in both trees: clearAndSetSemantics wipes the Text property
        // itself, not merely how it merges upward, so the affordance's own words are unreachable by
        // hasText even in the unmerged tree -- confirming the wording is announced nowhere, not just
        // that it fails to merge into an ancestor.
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_TAP_AFFORDANCE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_tap_to_continue), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_tap_to_continue)).assertDoesNotExist()
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

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
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
    }

    @Test
    fun replayModeRendersTheSameStepsAsFirstRun() {
        showTutorial(mode = TutorialMode.REPLAY)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
    }

    @Test
    fun theCalloutFitsOnANarrowViewport() {
        // A representative narrow phone width (320dp, the historical Android minimum) rather than
        // the emulator's own default -- the callout must still render fully and remain tappable.
        compose.setContent {
            var step by remember { mutableStateOf(2) } // SEARCH: a longer body string
            JustTheCarbsTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .width(320.dp),
                ) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = TutorialMode.FIRST_RUN,
                        onNext = { step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onExit = {},
                    )
                }
            }
        }

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
        val overlay = compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).fetchSemanticsNode()
        val card = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "card bounds ${card.boundsInRoot} must stay within the overlay ${overlay.boundsInRoot}",
            card.boundsInRoot.left >= overlay.boundsInRoot.left &&
                card.boundsInRoot.right <= overlay.boundsInRoot.right,
        )
    }

    @Test
    fun theCalloutStaysFullyOnScreenAtALargeFontScale() {
        // The emergency placement case (CalloutSide.CLAMPED): a large accessibility font scale can
        // measure the real card taller than either the bottom or the top clear zone. It must still
        // render fully on screen -- not clipped, not off the top or bottom edge -- using the actual
        // measured card rather than an estimate.
        showTutorial(startStep = 1, fontScale = 2.0f)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()

        val root = compose.onRoot().fetchSemanticsNode()
        val cardTitle = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNode()

        assertTrue(
            "card top ${cardTitle.boundsInRoot.top} must not be above the screen (root top ${root.boundsInRoot.top})",
            cardTitle.boundsInRoot.top >= root.boundsInRoot.top,
        )
        assertTrue(
            "card bottom ${cardTitle.boundsInRoot.bottom} must not exceed the screen " +
                "(root bottom ${root.boundsInRoot.bottom})",
            cardTitle.boundsInRoot.bottom <= root.boundsInRoot.bottom,
        )
    }
}
