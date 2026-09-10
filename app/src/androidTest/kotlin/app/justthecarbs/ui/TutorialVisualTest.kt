package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.TUTORIAL_BODY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_NARRATION_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SPOTLIGHT_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_STEPS
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_SURFACE_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_TITLE_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Layout invariants across every chapter. Optional captures support human visual review, not goldens. */
class TutorialVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun light() = walk("light", ThemeChoice.LIGHT, 1f, 400)
    @Test fun dark() = walk("dark", ThemeChoice.DARK, 1f, 400)
    @Test fun largeLight() = walk("large-light", ThemeChoice.LIGHT, 2f, 400)
    @Test fun largeDark() = walk("large-dark", ThemeChoice.DARK, 2f, 400)
    @Test fun narrow() = walk("narrow", ThemeChoice.LIGHT, 1f, 320)
    @Test fun narrowLarge() = walk("narrow-large", ThemeChoice.DARK, 2f, 320)

    @Test
    fun transitionFrames() {
        compose.mainClock.autoAdvance = false
        lateinit var requestStep: (Int) -> Unit
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            requestStep = { step = it }
            JustTheCarbsTheme(themeChoice = ThemeChoice.LIGHT) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    OnboardingScreen(step, TutorialMode.REPLAY, onNext = {}, onExit = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(310)
        compose.waitForIdle()

        for (destination in 1..TUTORIAL_LAST_STEP) {
            compose.runOnIdle { requestStep(destination) }
            var elapsed = 0L
            listOf(35L, 75L, 150L, 230L, 310L).forEach { checkpoint ->
                compose.mainClock.advanceTimeBy(checkpoint - elapsed)
                compose.waitForIdle()
                compose.onNodeWithTag(TUTORIAL_TITLE_TAG, true).fetchSemanticsNode()
                compose.onNodeWithTag(TUTORIAL_BODY_TAG, true).fetchSemanticsNode()
                compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, true).fetchSemanticsNode()
                captureIfRequested("transition-$destination-${checkpoint}ms", captureComposeRoot = true)
                elapsed = checkpoint
            }
        }
    }

    private fun walk(name: String, theme: ThemeChoice, fontScale: Float, width: Int) {
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustTheCarbsTheme(themeChoice = theme) {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        OnboardingScreen(step, TutorialMode.REPLAY,
                            onNext = { step++ }, onExit = {})
                    }
                }
            }
        }
        val first = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
        val firstTitle = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, true).fetchSemanticsNode().boundsInRoot
        val firstBody = compose.onNodeWithTag(TUTORIAL_BODY_TAG, true).fetchSemanticsNode().boundsInRoot
        val firstCopyCenter = (firstTitle.top + firstBody.bottom) / 2f
        TUTORIAL_STEPS.indices.forEach { index ->
            val narration = compose.onNodeWithTag(TUTORIAL_NARRATION_TAG).fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithTag(TUTORIAL_TITLE_TAG, true).fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag(TUTORIAL_BODY_TAG, true).fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("$name step $index copy must fit on screen",
                title.top >= root.top && body.bottom <= root.bottom)
            val target = compose.onNodeWithTag(TUTORIAL_SPOTLIGHT_TAG, true).fetchSemanticsNode().boundsInRoot
            assertEquals("$name step $index narration moved", first.top, narration.top, 1f)
            assertEquals("$name step $index teaching copy center moved", firstCopyCenter,
                (title.top + body.bottom) / 2f, 2f)
            assertTrue("$name step $index target must not overlap narration: $target / $narration",
                target.height > 0f && (target.bottom <= narration.top || target.top >= narration.bottom))
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, true).assertIsDisplayed()
            compose.onNodeWithTag(TUTORIAL_BODY_TAG, true).assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("tutorialScreenshots") == "true") {
                Thread.sleep(1200)
                captureIfRequested("$name-${index + 1}")
            }
            if (index != TUTORIAL_LAST_STEP) {
                compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG, true).performTouchInput { click(center) }
            }
        }
    }

    private fun captureIfRequested(name: String, captureComposeRoot: Boolean = false) {
        if (InstrumentationRegistry.getArguments().getString("tutorialScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "tutorial-v2")
        output.mkdirs()
        File(output, "$name.png").outputStream().use {
            val bitmap = if (captureComposeRoot) {
                compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).captureToImage().asAndroidBitmap()
            } else {
                instrumentation.uiAutomation.takeScreenshot()
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
