package app.justthecarbs.ui.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialStyleTest {
    @Test
    fun `blue chapters have distinct quiet fields with a broad start`() {
        val start = TutorialAccent.PRIMARY.lightField()
        val barcode = TutorialAccent.BARCODE.lightField()
        val search = TutorialAccent.SEARCH.lightField()
        assertEquals(3, setOf(start, barcode, search).size)
        assertTrue(start.bloomSpread > search.bloomSpread)
        assertTrue(search.bloomSpread > barcode.bloomSpread)
        assertTrue(search.bloomAlpha < barcode.bloomAlpha)
        val finale = TutorialAccent.RESULT.lightField()
        assertTrue(finale.bloomAlpha < start.bloomAlpha)
    }

    @Test
    fun `strong focus adds definition without making a larger box`() {
        val standard = TutorialFocusEmphasis.STANDARD.treatment()
        val strong = TutorialFocusEmphasis.STRONG.treatment()

        assertTrue(strong.edgeAlpha > standard.edgeAlpha)
        assertTrue(strong.edgeWidth > standard.edgeWidth)
        assertTrue(strong.bloomAlphaMultiplier > standard.bloomAlphaMultiplier)
        assertTrue(strong.bloomSpreadMultiplier <= 1.05f)
        assertTrue(strong.arrivalBoost > standard.arrivalBoost)
    }

    @Test
    fun `focus arrival peaks once and settles exactly`() {
        val boost = TutorialFocusEmphasis.STRONG.treatment().arrivalBoost

        assertTrue(tutorialFocusArrival(0f, boost) < 1f)
        assertTrue(tutorialFocusArrival(0.48f, boost) > 1f)
        assertEquals(1f, tutorialFocusArrival(1f, boost), 0.0001f)
        assertTrue(tutorialFocusArrivalSpread(0.48f, boost) > 1f)
        assertEquals(1f, tutorialFocusArrivalSpread(1f, boost), 0.0001f)
    }

    @Test
    fun `completed rail keeps own color while future rail stays neutral`() {
        val own = Color.Blue
        val active = Color.Red
        val track = Color.Gray.copy(alpha = 0.18f)
        assertEquals(own.copy(alpha = 0.30f), tutorialRailColor(0, 3, own, active, track))
        assertEquals(active, tutorialRailColor(3, 3, own, active, track))
        assertEquals(track, tutorialRailColor(4, 3, own, active, track))
    }

    @Test
    fun `scrim preserves substantially more context than it removes`() {
        val light = tutorialScrimTone(backgroundLuminance = 0.9f)
        val dark = tutorialScrimTone(backgroundLuminance = 0.1f)

        assertTrue(light.baseAlpha + light.edgeAlpha + light.localAlpha < 0.32f)
        assertTrue(dark.baseAlpha + dark.edgeAlpha < light.baseAlpha + light.edgeAlpha)
        assertTrue(dark.localAlpha < light.localAlpha)
    }

    @Test
    fun `nearby overlapping geometry retargets directly`() {
        val from = Rect(80f, 120f, 360f, 220f)
        val to = Rect(92f, 126f, 372f, 226f)

        assertEquals(TutorialSpotlightTransition.DIRECT, tutorialSpotlightTransition(from, to))
    }

    @Test
    fun `distant or incompatible geometry releases before acquiring`() {
        assertEquals(
            TutorialSpotlightTransition.RELEASE_ACQUIRE,
            tutorialSpotlightTransition(
                Rect(40f, 80f, 360f, 180f),
                Rect(40f, 620f, 360f, 940f),
            ),
        )
        assertEquals(
            TutorialSpotlightTransition.RELEASE_ACQUIRE,
            tutorialSpotlightTransition(Rect.Zero, Rect(40f, 80f, 360f, 180f)),
        )
    }
}
