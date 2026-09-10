package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_NARRATION_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PROGRESS_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SPOTLIGHT_TAG
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
 * [app.justthecarbs.ui.onboarding.OnboardingScreen]'s narration semantics for why, and for how TalkBack
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
    fun searchChapterUsesTheApprovedConciseCopy() {
        showTutorial(startStep = 2)
        compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[2].bodyRes))
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals("Search by name")
    }

    @Test
    fun tappingTheNarrationBackgroundAlsoAdvances() {
        showTutorial()

        val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click(narration.center) }
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
    fun advancingIntoTheFinalStepDoesNotDropTheFinishTap() {
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP - 1, onExit = { exits++ })

        tapTapSurface()
        tapTapSurface()

        assertEquals(1, exits)
    }

    @Test
    fun finalAffordanceAndProgressSwitchAtomicallyWithTotalCopy() {
        compose.mainClock.autoAdvance = false
        lateinit var requestStep: (Int) -> Unit
        compose.setContent {
            var step by remember { mutableStateOf(TUTORIAL_LAST_STEP - 1) }
            requestStep = { step = it }
            JustTheCarbsTheme {
                OnboardingScreen(
                    stepIndex = step,
                    mode = TutorialMode.FIRST_RUN,
                    onNext = { step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                    onExit = {},
                )
            }
        }

        compose.runOnIdle { requestStep(TUTORIAL_LAST_STEP) }
        compose.mainClock.advanceTimeBy(65)
        compose.waitForIdle()

        assertMealOrTotalPresentationIsInternallyConsistent()

        compose.mainClock.advanceTimeBy(20)
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[TUTORIAL_LAST_STEP].titleRes))
        compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true)
            .assertContentDescriptionEquals("Step 6 of 6")
        assertEquals(
            string(app.justthecarbs.R.string.tutorial_finish),
            compose.onNodeWithTag(TUTORIAL_NARRATION_TAG)
                .fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
    }

    @Test
    fun rapidRequestedStepsConvergeOnTheLatestAtomicPresentation() {
        compose.mainClock.autoAdvance = false
        lateinit var requestStep: (Int) -> Unit
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            requestStep = { step = it }
            JustTheCarbsTheme {
                OnboardingScreen(step, TutorialMode.FIRST_RUN, onNext = {}, onExit = {})
            }
        }
        compose.mainClock.advanceTimeBy(310)
        compose.waitForIdle()

        for (requested in 1..TUTORIAL_LAST_STEP) {
            compose.runOnIdle { requestStep(requested) }
            compose.mainClock.advanceTimeBy(25)
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[TUTORIAL_LAST_STEP].titleRes))
        compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true)
            .assertContentDescriptionEquals("Step 6 of 6")
        assertEquals(
            string(app.justthecarbs.R.string.tutorial_finish),
            compose.onNodeWithTag(TUTORIAL_NARRATION_TAG)
                .fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
    }

    private fun assertMealOrTotalPresentationIsInternallyConsistent() {
        val title = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val progress = compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        val action = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG)
            .fetchSemanticsNode().config[SemanticsActions.OnClick].label
        when (title) {
            string(TUTORIAL_STEPS[TUTORIAL_LAST_STEP - 1].titleRes) -> {
                assertEquals("Step 5 of 6", progress)
                assertEquals(string(app.justthecarbs.R.string.tutorial_next), action)
            }
            string(TUTORIAL_STEPS[TUTORIAL_LAST_STEP].titleRes) -> {
                assertEquals("Step 6 of 6", progress)
                assertEquals(string(app.justthecarbs.R.string.tutorial_finish), action)
            }
            else -> throw AssertionError("Unexpected transition title: $title")
        }
    }

    @Test
    fun theNarrationsAccessibilityActionAlsoFinishesOnTheFinalStep() {
        // TalkBack's path: the narration carries a real onClick accessibility action -- labelled
        // from the same tutorial_finish string the old visible button used -- so explore-by-touch and
        // the double-tap gesture still reach Finish, with no visible button anywhere on screen.
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_NARRATION_TAG)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun theNarrationsAccessibilityActionAdvancesOnAnOrdinaryStep() {
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

        compose.onNodeWithTag(TUTORIAL_NARRATION_TAG)
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
        showTutorial(startStep = 0)
        compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true)
            .assertContentDescriptionEquals("Step 1 of 6")
    }

    @Test
    fun narrationKeepsOneCentralReadingZoneAcrossAllSteps() {
        showTutorial()

        val first = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).fetchSemanticsNode().boundsInRoot
        // The stage is centered at 60% of the usable app height. Root bounds also include the
        // emulator's system-bar area, so its root-relative center lands slightly above 60%.
        assertTrue("teaching stage must live near visual center: $first", first.center.y / viewport.height in 0.55f..0.61f)
        TUTORIAL_STEPS.indices.forEach { index ->
            val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals("left edge moved at step $index", first.left, narration.left, 1f)
            assertEquals("right edge moved at step $index", first.right, narration.right, 1f)
            assertEquals("bottom edge moved at step $index", first.bottom, narration.bottom, 1f)
            assertEquals("reading zone moved at step $index", first.top, narration.top, 1f)
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(TUTORIAL_TAP_AFFORDANCE_TAG, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(TUTORIAL_PROGRESS_TAG, useUnmergedTree = true)
                .assertContentDescriptionEquals("Step ${index + 1} of ${TUTORIAL_STEPS.size}")
            if (index != TUTORIAL_LAST_STEP) tapTapSurface()
        }
    }

    @Test
    fun everyStepHasARealSpotlightOutsideTheNarration() {
        showTutorial()

        TUTORIAL_STEPS.indices.forEach { index ->
            compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, useUnmergedTree = true).assertIsDisplayed()
            val target = compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("step $index target must have width: $target", target.width > 0f)
            assertTrue("step $index target must have height: $target", target.height > 0f)
            assertTrue("step $index target must not begin at Rect.Zero: $target", target.left != 0f || target.top != 0f)
            assertTrue(
                "step $index target $target must not overlap narration $narration",
                target.bottom <= narration.top || target.top >= narration.bottom,
            )
            if (index != TUTORIAL_LAST_STEP) tapTapSurface()
        }
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
    fun theNarrationFitsOnANarrowViewport() {
        // A representative narrow phone width (320dp, the historical Android minimum) rather than
        // the emulator's own default -- narration must still render fully and remain usable.
        compose.setContent {
            var step by remember { mutableStateOf(2) } // SEARCH: a longer body string
            JustTheCarbsTheme {
                Box(
                    Modifier
                        .fillMaxHeight()
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
        val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode()
        assertTrue(
            "narration bounds ${narration.boundsInRoot} must stay within the overlay ${overlay.boundsInRoot}",
            narration.boundsInRoot.left >= overlay.boundsInRoot.left &&
                narration.boundsInRoot.right <= overlay.boundsInRoot.right,
        )
    }

    @Test
    fun theNarrationAndTargetStaySeparatedAtALargeFontScale() {
        showTutorial(startStep = 1, fontScale = 2.0f)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TAP_AFFORDANCE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()

        val root = compose.onRoot().fetchSemanticsNode()
        val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode()
        val target = compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, useUnmergedTree = true).fetchSemanticsNode()

        assertTrue(
            "narration top ${narration.boundsInRoot.top} must not be above the screen",
            narration.boundsInRoot.top >= root.boundsInRoot.top,
        )
        assertTrue(
            "narration bottom ${narration.boundsInRoot.bottom} must not exceed the screen",
            narration.boundsInRoot.bottom <= root.boundsInRoot.bottom,
        )
        assertTrue(
            "target $target must not overlap narration $narration",
            target.boundsInRoot.bottom <= narration.boundsInRoot.top ||
                target.boundsInRoot.top >= narration.boundsInRoot.bottom,
        )
    }

    @Test
    fun everyLargeFontStepKeepsItsTargetOutsideTheNarration() {
        showTutorial(fontScale = 2.0f)

        TUTORIAL_STEPS.indices.forEach { index ->
            val target = compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("large-font step $index target must have area: $target", target.width > 0f && target.height > 0f)
            assertTrue(
                "large-font step $index target $target must remain outside narration $narration",
                target.bottom <= narration.top || target.top >= narration.bottom,
            )
            if (index != TUTORIAL_LAST_STEP) tapTapSurface()
        }
    }
    @Test
    fun conciseCopyAndChapterContract() {
        val titles = listOf("Three ways to get started", "Scan the barcode", "Search by name",
            "Scan the nutrition label", "Add it to your meal", "See your meal total")
        val bodies = listOf("Scan a barcode, search by name, or scan the nutrition label.",
            "Fastest when the package has one.", "Can't scan? Type the product name and choose a match.",
            "Can't find the product? We'll look for the carb value on the package.",
            "Set the portion, then add it to the running total.", "Everything you add is combined here.")
        val chapters = listOf("START", "BARCODE", "SEARCH", "LABEL", "MEAL", "TOTAL")
        assertEquals(6, TUTORIAL_STEPS.size)
        TUTORIAL_STEPS.forEachIndexed { index, step ->
            assertEquals(titles[index], string(step.titleRes))
            assertEquals(bodies[index], string(step.bodyRes))
            assertEquals(chapters[index], string(step.chapterRes))
            assertTrue(string(step.titleRes).length in 1..40)
            assertTrue(string(step.bodyRes).length in 1..75)
        }
    }

    @Test
    fun copyStaysCenteredAt320dpWithNormalAndLargeFonts() {
        var index by mutableStateOf(0)
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            JustTheCarbsTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.fillMaxHeight().width(320.dp)) {
                        OnboardingScreen(index, TutorialMode.REPLAY, onNext = {}, onExit = {})
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale = scale; index = 0 }
            var firstCenter: Float? = null
            var firstTop: Float? = null
            for (step in TUTORIAL_STEPS.indices) {
                compose.runOnIdle { index = step }
                val title = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val body = compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
                val center = (title.top + body.bottom) / 2f
                if (firstCenter == null) { firstCenter = center; firstTop = narration.top }
                assertEquals("copy center moved at $scale / $step", firstCenter, center, 2f)
                assertEquals("narration moved at $scale / $step", firstTop!!, narration.top, 1f)
                compose.onNodeWithTag(TUTORIAL_TAP_AFFORDANCE_TAG, useUnmergedTree = true).assertIsDisplayed()
            }
        }
    }

}
