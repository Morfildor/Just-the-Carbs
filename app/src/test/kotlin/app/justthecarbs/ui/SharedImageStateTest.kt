package app.justthecarbs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share lifecycle rules: newest wins, no replay, held across onboarding, consumed once.
 *
 * These are the requirements this feature is actually judged on, and every one of them is a rule
 * about a *transition* — invisible to a screen test, and a one-line change away from being wrong.
 */
class SharedImageStateTest {

    private val T = SharedImageTransitions

    @Test
    fun `an ordinary launch holds nothing`() {
        assertEquals(0L, T.currentId(SharedImageState.None))
        assertNull(T.stagedPath(SharedImageState.None))
    }

    @Test
    fun `a staged share becomes a question when onboarding is done`() {
        val arrived = T.arrive(1L)
        val next = T.staged(arrived, 1L, "/cache/a.jpg", hasSeenOnboarding = true)
        assertEquals(SharedImageState.Ready(1L, "/cache/a.jpg"), next)
    }

    @Test
    fun `a share staged before onboarding is held, not shown and not lost`() {
        val next = T.staged(T.arrive(1L), 1L, "/cache/a.jpg", hasSeenOnboarding = false)
        assertEquals(SharedImageState.HeldForOnboarding(1L, "/cache/a.jpg"), next)
        // Held means the file is retained: losing it would lose the only copy of what the user sent.
        assertEquals("/cache/a.jpg", T.stagedPath(next!!))
    }

    @Test
    fun `finishing onboarding releases a held share`() {
        val held = SharedImageState.HeldForOnboarding(1L, "/cache/a.jpg")
        assertEquals(SharedImageState.Ready(1L, "/cache/a.jpg"), T.onboardingCompleted(held))
    }

    @Test
    fun `finishing onboarding with no share conjures nothing`() {
        // The ordinary first launch. A carousel completion must not produce a chooser.
        assertEquals(SharedImageState.None, T.onboardingCompleted(SharedImageState.None))
    }

    @Test
    fun `finishing onboarding leaves an already-ready share alone`() {
        val ready = SharedImageState.Ready(3L, "/cache/a.jpg")
        assertEquals(ready, T.onboardingCompleted(ready))
    }

    @Test
    fun `the gate is re-checked at completion, not fixed at arrival`() {
        // A share arriving in the last seconds of onboarding must not be pinned behind a gate that
        // has since opened.
        val next = T.staged(T.arrive(1L), 1L, "/cache/a.jpg", hasSeenOnboarding = true)
        assertTrue(next is SharedImageState.Ready)
    }

    @Test
    fun `a newer share supersedes one still copying`() {
        val first = T.arrive(1L)
        val second = T.arrive(2L)
        assertFalse(T.isCurrent(second, 1L))
        assertTrue(T.isCurrent(second, 2L))
        // The abandoned copy's completion is refused rather than landing on top of the new one.
        assertNull(T.staged(second, 1L, "/cache/old.jpg", hasSeenOnboarding = true))
        assertNull(T.failed(second, 1L))
    }

    @Test
    fun `a newer share supersedes one already waiting at the chooser`() {
        val waiting = SharedImageState.Ready(1L, "/cache/first.jpg")
        assertEquals("/cache/first.jpg", T.stagedPath(waiting))
        val second = T.arrive(2L)
        assertFalse(T.isCurrent(second, 1L))
    }

    @Test
    fun `a newer share supersedes a failure the user has not dismissed`() {
        val failed = SharedImageState.Failed(1L)
        val second = T.arrive(2L)
        assertFalse(T.isCurrent(second, 1L))
        assertTrue(T.isCurrent(second, 2L))
    }

    @Test
    fun `a stale completion cannot overwrite a newer held share`() {
        val held = SharedImageState.HeldForOnboarding(2L, "/cache/new.jpg")
        assertNull(T.staged(held, 1L, "/cache/old.jpg", hasSeenOnboarding = true))
    }

    @Test
    fun `consuming clears the share so nothing can replay it`() {
        // Rotation, returning from another app, or a later launch must find nothing to act on.
        assertEquals(SharedImageState.None, T.consume())
        assertEquals(0L, T.currentId(T.consume()))
        assertNull(T.stagedPath(T.consume()))
    }

    @Test
    fun `a consumed share is no longer current, so a late result cannot land`() {
        val consumed = T.consume()
        assertFalse(T.isCurrent(consumed, 1L))
        assertNull(T.staged(consumed, 1L, "/cache/a.jpg", hasSeenOnboarding = true))
        assertNull(T.failed(consumed, 1L))
    }

    @Test
    fun `a failure is reported rather than silently swallowed`() {
        assertEquals(SharedImageState.Failed(1L), T.failed(T.arrive(1L), 1L))
    }

    @Test
    fun `a copy in flight reports no file to delete`() {
        // The copy owns its own partial file and deletes it itself; a second delete here would
        // race a coroutine still writing.
        assertNull(T.stagedPath(SharedImageState.Staging(1L)))
    }

    @Test
    fun `a failure holds no file to delete`() {
        assertNull(T.stagedPath(SharedImageState.Failed(1L)))
    }

    @Test
    fun `each state reports its own delivery id`() {
        assertEquals(1L, T.currentId(SharedImageState.Staging(1L)))
        assertEquals(2L, T.currentId(SharedImageState.HeldForOnboarding(2L, "/a")))
        assertEquals(3L, T.currentId(SharedImageState.Ready(3L, "/a")))
        assertEquals(4L, T.currentId(SharedImageState.Failed(4L)))
    }
}
