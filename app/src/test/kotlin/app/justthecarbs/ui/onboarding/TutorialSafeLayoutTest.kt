package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the fixed narration contract independently of any emulator viewport. */
class TutorialSafeLayoutTest {

    @Test
    fun `preview ends exactly where narration begins`() {
        assertEquals(1_760, tutorialPreviewHeight(viewportHeight = 2_400, narrationHeight = 640))
    }

    @Test
    fun `larger narration grows upward by reducing only the preview viewport`() {
        val ordinary = tutorialPreviewHeight(viewportHeight = 2_400, narrationHeight = 640)
        val largeText = tutorialPreviewHeight(viewportHeight = 2_400, narrationHeight = 960)

        assertEquals(320, ordinary - largeText)
    }

    @Test
    fun `narration can consume the viewport without producing negative preview geometry`() {
        assertEquals(0, tutorialPreviewHeight(viewportHeight = 800, narrationHeight = 900))
    }
}
