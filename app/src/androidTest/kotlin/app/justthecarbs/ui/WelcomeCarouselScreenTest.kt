package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.CAROUSEL_PRIMARY_TAG
import app.justthecarbs.ui.onboarding.CAROUSEL_ROOT_TAG
import app.justthecarbs.ui.onboarding.CAROUSEL_SKIP_TAG
import app.justthecarbs.ui.onboarding.CAROUSEL_TITLE_TAG
import app.justthecarbs.ui.onboarding.WelcomeCarouselScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The welcome carousel's rendered behaviour.
 *
 * **Instrumented: needs a device or emulator.** The slide machine is covered by pure JVM tests
 * ([app.justthecarbs.ui.onboarding.WelcomeCarouselViewModelTest]); this class covers what only a
 * real composition can answer — that each slide's strings resolve, that the action changes on the
 * last slide, and that the touch targets clear the floor.
 *
 * Copy is read from resources rather than hardcoded, so a wording change cannot fail these tests for
 * the wrong reason.
 */
class WelcomeCarouselScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private var getStartedCount = 0

    /** Drives the real screen with real state, so Next and Skip actually move it. */
    private fun show(initialSlide: Int = 0, themeChoice: ThemeChoice = ThemeChoice.LIGHT) {
        getStartedCount = 0
        compose.setContent {
            var slide by remember { mutableIntStateOf(initialSlide) }
            JustTheCarbsTheme(themeChoice = themeChoice) {
                WelcomeCarouselScreen(
                    slideIndex = slide,
                    onNext = { slide = (slide + 1).coerceAtMost(2) },
                    onSkip = { slide = 2 },
                    onGetStarted = { getStartedCount++ },
                    onSlideChanged = { slide = it },
                )
            }
        }
    }

    @Test
    fun theFirstSlideShowsItsOwnCopy() {
        show()
        compose.onNodeWithTag(CAROUSEL_ROOT_TAG).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.onboarding_title_1)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.onboarding_body_1)).assertIsDisplayed()
    }

    @Test
    fun nextWalksForwardThroughAllThreeSlides() {
        show()
        compose.onNodeWithText(string(R.string.onboarding_title_1)).assertIsDisplayed()

        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_2)).assertIsDisplayed()

        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_3)).assertIsDisplayed()
    }

    @Test
    fun theActionBecomesGetStartedOnTheLastSlideOnly() {
        // Walked forward rather than rendered twice: `setContent` may only be called once per test,
        // and driving the real screen is a better check anyway -- it proves the label changes as a
        // consequence of reaching the last slide, not merely that it differs when handed a
        // different starting index.
        show()
        compose.onNodeWithText(string(R.string.onboarding_next)).assertIsDisplayed()

        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_next)).assertIsDisplayed()

        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_get_started)).assertIsDisplayed()
    }

    @Test
    fun skipJumpsToTheLastSlideRatherThanLeaving() {
        // Skip is a jump, not an exit -- the final slide's action is the one place the flag is
        // written. A Skip that left directly would need its own copy of that write.
        show()
        compose.onNodeWithTag(CAROUSEL_SKIP_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(string(R.string.onboarding_title_3)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.onboarding_get_started)).assertIsDisplayed()
        assertEquals("Skip must not complete the carousel by itself", 0, getStartedCount)
    }

    @Test
    fun skipIsNotOfferedOnTheLastSlide() {
        // There is nothing left to skip, and leaving it there would sit a second, quieter exit
        // beside the real one.
        show(initialSlide = 2)
        compose.onNodeWithTag(CAROUSEL_SKIP_TAG).assertDoesNotExist()
    }

    @Test
    fun getStartedFiresOnceFromTheLastSlide() {
        show(initialSlide = 2)
        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, getStartedCount)
    }

    @Test
    fun theActionsClearTheTouchTargetFloor() {
        show()
        compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(CAROUSEL_SKIP_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun everySlideRendersATitle() {
        // Guards the tag itself as much as the copy: a slide whose title stopped rendering would
        // otherwise only be caught by whichever test happened to name that slide's words.
        //
        // Two things this test had to learn about the real screen, both of which made an earlier
        // version fail for reasons that were not defects:
        //
        //  - `setContent` may only be called once per test, so the slides are walked rather than
        //    re-rendered per index.
        //  - `HorizontalPager` keeps the neighbouring page composed, so more than one title node
        //    legitimately exists while a slide change settles. Asserting "exactly one" measured the
        //    pager's offscreen buffer, not whether the title renders. `onAllNodesWithTag` with a
        //    non-empty check asks the question that was actually meant.
        show()
        assertTitleRendered()

        repeat(2) {
            compose.onNodeWithTag(CAROUSEL_PRIMARY_TAG).performClick()
            compose.waitForIdle()
            assertTitleRendered()
        }
    }

    @Test
    fun pagerGesturesRemainBidirectionalInLightTheme() {
        assertBidirectionalPagerMotion(ThemeChoice.LIGHT)
    }

    @Test
    fun pagerGesturesRemainBidirectionalInDarkTheme() {
        assertBidirectionalPagerMotion(ThemeChoice.DARK)
    }

    private fun assertBidirectionalPagerMotion(themeChoice: ThemeChoice) {
        show(themeChoice = themeChoice)
        val root = compose.onNodeWithTag(CAROUSEL_ROOT_TAG)

        root.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_2)).assertIsDisplayed()

        root.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_3)).assertIsDisplayed()

        root.performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_2)).assertIsDisplayed()

        root.performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.onboarding_title_1)).assertIsDisplayed()
    }

    private fun assertTitleRendered() {
        val titles = compose.onAllNodesWithTag(CAROUSEL_TITLE_TAG).fetchSemanticsNodes()
        assertTrue("no slide title was rendered", titles.isNotEmpty())
    }
}
