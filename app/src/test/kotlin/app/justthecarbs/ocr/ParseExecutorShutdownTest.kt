package app.justthecarbs.ocr

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The label scanner's teardown guarantee:
 *
 * > Leaving the nutrition-label scanner while recognition or cancellation work is in flight must not
 * > crash, and must not dispatch that work onto an executor that has already been shut down.
 *
 * ## The defect this pins
 *
 * `LabelAnalyzer.close()` closes the ML Kit recognizer — which *cancels* any in-flight task — and
 * then shuts down the executor its completion listeners run on. Play Services' `Task` implementation
 * delivers that cancellation by calling `executor.execute(...)` from its own machinery, on the main
 * thread, after `close()` has returned. With a default-configured executor that call throws
 * `RejectedExecutionException` from inside GMS code, where none of this app's `runCatching` guards
 * at the *submission* sites can reach it, and the app dies.
 *
 * Reproduced on the emulator and proven pre-existing against clean `main` before the fix; since
 * confirmed fixed on physical hardware by the owner.
 *
 * ## Why the executor and not the composable
 *
 * The invariant is a property of the executor's saturation policy, and that is the whole fix.
 * `LabelAnalyzer` builds a `TextRecognition` client in its constructor, so it cannot be instantiated
 * off-device at all — which is precisely why this defect shipped without coverage. Driving a real
 * camera session through a disposal race would need a faked CameraX *and* a faked Play Services
 * `Task` whose cancellation dispatch could be timed, which is a large testing abstraction for one
 * regression and would ultimately assert this same property through several layers of stand-in.
 *
 * These tests instead exercise the real executor the app ships — `newParseExecutor()` is the only
 * construction site, and `LabelAnalyzer` calls it — so a change to its policy fails here. They are
 * deterministic: every wait is on a latch or on `awaitTermination`, and nothing sleeps.
 *
 * What remains uncovered, stated plainly: that `close()` continues to call `shutdown()` on this
 * executor, and that GMS still dispatches the way it does. The first is one line in `close()`; the
 * second is a third party's behaviour no local test can pin. This class covers the part that was
 * actually wrong.
 */
class ParseExecutorShutdownTest {

    @Test
    fun `a task dispatched after shutdown is discarded rather than thrown`() {
        val executor = newParseExecutor()
        executor.shutdown()
        assertTrue("precondition: the executor must really be shut down", executor.isShutdown)

        // The exact shape of the crash: something outside this app's frames calls execute() on a
        // terminated pool. With the default AbortPolicy this line throws
        // RejectedExecutionException; the shipped DiscardPolicy drops it silently.
        executor.execute { error("a task rejected after shutdown must never run") }
    }

    @Test
    fun `a task dispatched after shutdown does not run`() {
        // Discarding means discarding. If a rejected task were somehow executed instead, a callback
        // belonging to a torn-down camera session would touch state the session has already
        // released -- which is a worse outcome than the crash this replaced.
        val executor = newParseExecutor()
        val ran = java.util.concurrent.atomic.AtomicBoolean(false)

        executor.shutdown()
        executor.execute { ran.set(true) }

        assertTrue(
            "the pool should terminate promptly with nothing queued",
            executor.awaitTermination(5, TimeUnit.SECONDS),
        )
        assertFalse("a discarded task must not run", ran.get())
    }

    @Test
    fun `work already accepted before shutdown still completes`() {
        // The other half of the contract, and the reason close() uses shutdown() rather than
        // shutdownNow(): a parse already dispatched is allowed to finish. Discarding late arrivals
        // must not be confused with abandoning work in progress.
        val executor = newParseExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)

        executor.execute {
            started.countDown()
            release.await()
            finished.countDown()
        }
        assertTrue("the task should start", started.await(5, TimeUnit.SECONDS))

        executor.shutdown()
        release.countDown()

        assertTrue("an accepted task must still finish", finished.await(5, TimeUnit.SECONDS))
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a late dispatch during an in-flight parse is discarded without disturbing it`() {
        // The real race, in the order it actually happens on the device: a parse is running when the
        // screen goes away, close() shuts the executor down, and GMS then delivers a cancellation
        // onto it. The in-flight parse must finish and the late arrival must be dropped.
        val executor = newParseExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val inFlightFinished = CountDownLatch(1)
        val lateRan = java.util.concurrent.atomic.AtomicBoolean(false)

        executor.execute {
            started.countDown()
            release.await()
            inFlightFinished.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        executor.shutdown()
        executor.execute { lateRan.set(true) }

        release.countDown()
        assertTrue(
            "the parse that was already running must complete",
            inFlightFinished.await(5, TimeUnit.SECONDS),
        )
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse("the late cancellation must be discarded", lateRan.get())
    }

    @Test
    fun `the parse executor is serial and runs off the calling thread on a daemon thread`() {
        // Three properties the analyzer's KDoc relies on, asserted rather than assumed:
        //
        //  - serial, so two captures cannot parse concurrently and contend;
        //  - not the caller's thread, which is what keeps a 26-198ms parse off the main thread;
        //  - daemon, so a stray task can never hold the process alive after teardown.
        val executor = newParseExecutor()
        val threads = java.util.Collections.synchronizedList(mutableListOf<Thread>())
        val done = CountDownLatch(3)

        repeat(3) {
            executor.execute {
                threads.add(Thread.currentThread())
                done.countDown()
            }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))

        assertEquals("all parses must share one thread", 1, threads.distinct().size)
        val worker = threads.first()
        assertTrue("the parse thread must be a daemon", worker.isDaemon)
        assertFalse(
            "the parse must not run on the caller's thread",
            worker == Thread.currentThread(),
        )

        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun `the shipped executor uses a discarding saturation policy`() {
        // Stated directly as well as behaviourally. The two tests above would also pass against a
        // CallerRunsPolicy -- which does not throw either, but would run a dead session's callback
        // on the main thread instead. Naming the policy makes that substitution fail here rather
        // than on a phone.
        val executor = newParseExecutor() as ThreadPoolExecutor
        assertTrue(
            "expected a DiscardPolicy, was ${executor.rejectedExecutionHandler::class.java.simpleName}",
            executor.rejectedExecutionHandler is ThreadPoolExecutor.DiscardPolicy,
        )
        executor.shutdown()
    }
}
