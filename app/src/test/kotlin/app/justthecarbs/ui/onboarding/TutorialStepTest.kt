package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tutorial's shape, asserted as data rather than as rendered pixels.
 *
 * These pin the properties the brief actually constrains — the number of moments, the order they
 * teach in, and which control each one points at — without an emulator. A step added or reordered
 * changes an assertion here before anyone has to notice it on a screen.
 */
class TutorialStepTest {

    @Test
    fun `the tutorial is six moments, matching the brief's ceiling`() {
        // "No more than six instructional moments." A seventh would make the sequence longer than
        // the 30-45s it is designed to take, which is the point of the cap.
        assertEquals(6, TUTORIAL_STEPS.size)
        assertEquals(5, TUTORIAL_LAST_STEP)
    }

    @Test
    fun `the steps teach in the app's own order`() {
        // Orientation, then the three ways in, then portion-and-meal, then the running total. This
        // is the rhythm the first step describes, so the sequence must actually follow it.
        assertEquals(
            listOf(
                TutorialAnchor.NONE,
                TutorialAnchor.SCAN_BARCODE,
                TutorialAnchor.SEARCH,
                TutorialAnchor.SCAN_LABEL,
                TutorialAnchor.ADD_TO_MEAL,
                TutorialAnchor.MEAL_TOTAL,
            ),
            TUTORIAL_STEPS.map { it.anchor },
        )
    }

    @Test
    fun `each backdrop shows the screen its step is teaching`() {
        // Pointing at *Add to meal* over a drawing of Home would put an arrow on a control that is
        // not there. The backdrop and the anchor have to agree.
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[0].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[1].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[2].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[3].backdrop)
        assertEquals(TutorialBackdrop.PRODUCT, TUTORIAL_STEPS[4].backdrop)
        assertEquals(TutorialBackdrop.MEAL, TUTORIAL_STEPS[5].backdrop)
    }

    @Test
    fun `only the orientation step has no target`() {
        // A teaching step with no anchor would render a centred card and teach nothing about where
        // to tap, which is the one thing a coach mark exists to do.
        val anchorless = TUTORIAL_STEPS.filter { it.anchor == TutorialAnchor.NONE }
        assertEquals(1, anchorless.size)
        assertEquals(TUTORIAL_STEPS.first(), anchorless.single())
    }

    @Test
    fun `every step carries distinct copy`() {
        // A duplicated title or body means a step was copied and not edited — which reads to the
        // user as the tutorial repeating itself.
        assertEquals(TUTORIAL_STEPS.size, TUTORIAL_STEPS.map { it.titleRes }.toSet().size)
        assertEquals(TUTORIAL_STEPS.size, TUTORIAL_STEPS.map { it.bodyRes }.toSet().size)
        TUTORIAL_STEPS.forEach { assertNotEquals(0, it.titleRes) }
        TUTORIAL_STEPS.forEach { assertNotEquals(0, it.bodyRes) }
    }

    @Test
    fun `no anchor is taught twice`() {
        // Two steps pointing at the same control would spend two of the six moments on one lesson.
        val targeted = TUTORIAL_STEPS.map { it.anchor }.filter { it != TutorialAnchor.NONE }
        assertEquals(targeted.size, targeted.toSet().size)
    }

    @Test
    fun `the four teaching anchors the brief names are all covered`() {
        val anchors = TUTORIAL_STEPS.map { it.anchor }.toSet()
        listOf(
            TutorialAnchor.SCAN_BARCODE,
            TutorialAnchor.SEARCH,
            TutorialAnchor.SCAN_LABEL,
            TutorialAnchor.ADD_TO_MEAL,
            TutorialAnchor.MEAL_TOTAL,
        ).forEach { assertTrue("missing $it", it in anchors) }
    }
}
