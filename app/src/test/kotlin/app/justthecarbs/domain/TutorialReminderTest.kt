package app.justthecarbs.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Home offers the tutorial.
 *
 * The rule is small and entirely about boundaries, which is exactly the shape that gets an
 * off-by-one wrong: "the first launch and the five after it" is six launches, not five, and the
 * difference is invisible until someone counts on a real device.
 */
class TutorialReminderTest {

    @Test
    fun `a first launch is invited`() {
        assertTrue(TutorialReminder.shouldShow(hasSeenOnboarding = false, launchCount = 1))
    }

    @Test
    fun `the reminder covers the first launch and the five after it`() {
        // Launches 1..6 inclusive: the first, plus REMINDER_LAUNCHES more.
        (1..TutorialReminder.REMINDER_LAUNCHES + 1).forEach { count ->
            assertTrue("launch $count should still invite", TutorialReminder.shouldShow(false, count))
        }
    }

    @Test
    fun `the reminder stops after its window`() {
        assertFalse(TutorialReminder.shouldShow(false, TutorialReminder.REMINDER_LAUNCHES + 2))
        assertFalse(TutorialReminder.shouldShow(false, 50))
    }

    @Test
    fun `having seen onboarding retires the reminder immediately at any launch count`() {
        // Finishing, skipping and dismissing all set this flag, and all three mean "I am done with
        // this" — so none of them may leave the card on screen for the rest of the window.
        (0..10).forEach { count ->
            assertFalse("launch $count", TutorialReminder.shouldShow(hasSeenOnboarding = true, launchCount = count))
        }
    }

    @Test
    fun `an uncounted or corrupt launch count invites rather than suppresses`() {
        // Zero means "not yet counted". A missing or damaged preference should show a new user the
        // only pointer to the tutorial, never silently hide it.
        assertTrue(TutorialReminder.shouldShow(false, 0))
        assertTrue(TutorialReminder.shouldShow(false, -7))
    }

    @Test
    fun `launches stop being counted once the reminder can no longer appear`() {
        // A counter with nothing left to decide is a number about the user's habits that this app
        // has no use for, so it stops rather than climbing forever.
        assertTrue(TutorialReminder.shouldCountLaunch(false, 1))
        assertTrue(TutorialReminder.shouldCountLaunch(false, TutorialReminder.REMINDER_LAUNCHES + 1))
        assertFalse(TutorialReminder.shouldCountLaunch(false, TutorialReminder.REMINDER_LAUNCHES + 2))
        assertFalse(TutorialReminder.shouldCountLaunch(hasSeenOnboarding = true, launchCount = 1))
    }

    @Test
    fun `counting and showing agree on the boundary`() {
        // The two rules are read in different places (the repository increments, Home renders), so a
        // disagreement would show a card on a launch that was never counted, or vice versa.
        (0..12).forEach { count ->
            val shows = TutorialReminder.shouldShow(false, count)
            val counts = TutorialReminder.shouldCountLaunch(false, count)
            assertTrue(
                "disagreement at launch $count: shows=$shows counts=$counts",
                shows == counts || count == 0,
            )
        }
    }
}
