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
                TutorialAnchor.FIND_ACTIONS,
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
        // Highlighting *Add to meal* over a drawing of Home would focus a control that is
        // not there. The backdrop and the anchor have to agree.
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[0].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[1].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[2].backdrop)
        assertEquals(TutorialBackdrop.HOME, TUTORIAL_STEPS[3].backdrop)
        assertEquals(TutorialBackdrop.PRODUCT, TUTORIAL_STEPS[4].backdrop)
        assertEquals(TutorialBackdrop.MEAL, TUTORIAL_STEPS[5].backdrop)
    }

    @Test
    fun `every normal tutorial step has a measured target`() {
        val anchorless = TUTORIAL_STEPS.filter { it.anchor == TutorialAnchor.NONE }
        assertEquals(0, anchorless.size)
        assertEquals(TutorialAnchor.FIND_ACTIONS, TUTORIAL_STEPS.first().anchor)
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
        val targeted = TUTORIAL_STEPS.map { it.anchor }
        assertEquals(targeted.size, targeted.toSet().size)
    }

    @Test
    fun `the four teaching anchors the brief names are all covered`() {
        val anchors = TUTORIAL_STEPS.map { it.anchor }.toSet()
        listOf(
            TutorialAnchor.FIND_ACTIONS,
            TutorialAnchor.SCAN_BARCODE,
            TutorialAnchor.SEARCH,
            TutorialAnchor.SCAN_LABEL,
            TutorialAnchor.ADD_TO_MEAL,
            TutorialAnchor.MEAL_TOTAL,
        ).forEach { assertTrue("missing $it", it in anchors) }
    }
    @Test
    fun `semantic accents follow features and reserve red for the total`() {
        assertEquals(TutorialAccent.entries, TUTORIAL_STEPS.map { it.accent })
        assertEquals(6, TUTORIAL_STEPS.map { it.chapterRes }.toSet().size)
        TUTORIAL_STEPS.forEach { assertNotEquals(0, it.chapterRes) }
        assertEquals(TutorialFocusStyle.CONTROL, TUTORIAL_STEPS[2].focusStyle)
        assertEquals(TutorialFocusStyle.RESULT, TUTORIAL_STEPS.last().focusStyle)
    }

    @Test
    fun `four instructional features use pointers while orientation and total stay calm`() {
        assertEquals(
            listOf(
                TutorialPointer.NONE,
                TutorialPointer.FEATURE,
                TutorialPointer.FEATURE,
                TutorialPointer.FEATURE,
                TutorialPointer.FEATURE,
                TutorialPointer.NONE,
            ),
            TUTORIAL_STEPS.map { it.pointer },
        )
    }

    @Test
    fun `orientation search label and meal receive their required focus treatments`() {
        assertEquals(
            listOf(
                TutorialFocusEmphasis.STRONG,
                TutorialFocusEmphasis.STANDARD,
                TutorialFocusEmphasis.STRONG,
                TutorialFocusEmphasis.STRONG,
                TutorialFocusEmphasis.STRONG,
                TutorialFocusEmphasis.STANDARD,
            ),
            TUTORIAL_STEPS.map { it.focusEmphasis },
        )
    }

    @Test
    fun `visible presentation derives finish state from the same displayed step`() {
        val meal = tutorialPresentation(TUTORIAL_LAST_STEP - 1)
        val total = tutorialPresentation(TUTORIAL_LAST_STEP)

        assertEquals(TUTORIAL_STEPS[TUTORIAL_LAST_STEP - 1], meal.step)
        assertEquals(false, meal.isLast)
        assertEquals(TUTORIAL_STEPS[TUTORIAL_LAST_STEP], total.step)
        assertEquals(true, total.isLast)
    }

}
