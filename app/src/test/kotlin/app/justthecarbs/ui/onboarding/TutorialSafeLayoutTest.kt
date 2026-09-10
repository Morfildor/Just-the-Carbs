package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the fixed narration contract independently of any emulator viewport. */
class TutorialSafeLayoutTest {

    @Test
    fun `teaching stage centers at sixty percent of usable viewport`() {
        assertEquals(1_120, tutorialStageTop(viewportHeight = 2_400, narrationHeight = 640))
    }

    @Test
    fun `larger narration expands equally around the same visual center`() {
        val ordinary = tutorialStageTop(viewportHeight = 2_400, narrationHeight = 640)
        val largeText = tutorialStageTop(viewportHeight = 2_400, narrationHeight = 960)

        assertEquals(160, ordinary - largeText)
    }

    @Test
    fun `narration can consume the viewport without producing negative preview geometry`() {
        assertEquals(0, tutorialStageTop(viewportHeight = 800, narrationHeight = 900))
    }
}
