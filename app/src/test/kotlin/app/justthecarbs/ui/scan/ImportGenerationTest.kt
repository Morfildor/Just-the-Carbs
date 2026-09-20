package app.justthecarbs.ui.scan

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The newest-wins rule, pinned where a test can reach it.
 *
 * This is the guard that stops a slow first photograph navigating after the user has chosen a
 * second one — a stale result reaching `onBarcode` is a confident lookup for a product the user has
 * already moved on from. It lives in [ImportGeneration] rather than as an `AtomicLong` inside the
 * scanner composable for the reason this repo has recorded twice: a rule held inside a composable
 * that binds a camera is unreachable from the JVM, and reverting it fails nothing.
 */
class ImportGenerationTest {

    @Test
    fun `a fresh import is current`() {
        val generation = ImportGeneration()

        val first = generation.begin()

        assertTrue(generation.isCurrent(first))
    }

    @Test
    fun `a newer import supersedes an older one`() {
        val generation = ImportGeneration()

        val older = generation.begin()
        val newer = generation.begin()

        assertFalse("the older import must no longer be allowed to land", generation.isCurrent(older))
        assertTrue(generation.isCurrent(newer))
    }

    @Test
    fun `invalidate leaves nothing current`() {
        // What leaving the screen, dismissing a choice or navigating away does: there is no
        // successor to attribute the abandoned work to, and it must still not land.
        val generation = ImportGeneration()
        val work = generation.begin()

        generation.invalidate()

        assertFalse(generation.isCurrent(work))
    }

    @Test
    fun `a generation never becomes current again`() {
        // The counter only ever advances, so an abandoned import cannot be resurrected by a later
        // one happening to take the same number — which a wrapping or resetting counter would allow.
        val generation = ImportGeneration()
        val first = generation.begin()

        repeat(5) { generation.begin() }

        assertFalse(generation.isCurrent(first))
    }

    @Test
    fun `concurrent imports leave exactly one current`() {
        // Staging runs on Dispatchers.IO while the picker callback runs on the main thread, so two
        // imports genuinely race. Exactly one may survive that race: two "current" generations
        // would be two results each believing it may navigate.
        val generation = ImportGeneration()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val taken = java.util.Collections.synchronizedList(mutableListOf<Long>())

        repeat(threads) {
            pool.execute {
                start.await()
                taken += generation.begin()
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals("every import must take a distinct generation", threads, taken.toSet().size)

        val stillCurrent = AtomicInteger(0)
        taken.forEach { if (generation.isCurrent(it)) stillCurrent.incrementAndGet() }
        assertEquals("exactly one import may remain current", 1, stillCurrent.get())
    }
}
