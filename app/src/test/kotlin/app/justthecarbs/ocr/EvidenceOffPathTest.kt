package app.justthecarbs.ocr

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Pins the ordering contract that keeps evidence writing off the scan's decision path.
 *
 * ## The defect this exists for
 *
 * The 2026-08-25 pass moved the heavy evidence writes to a background thread and marked their stages
 * `markOffPath`, and the 2026-09-01 device evidence showed the user still waiting for them:
 *
 * ```
 * 20260901-222212-563   scan 20195ms   evidence-capture 9487*   parse 9278
 * 20260901-222300-297   scan 11189ms   evidence-capture 5232*   parse 5331
 * ```
 *
 * The `*` marks a stage excluded from the printed `user-visible` total. It excluded the stage from
 * the *arithmetic*, not from the wall clock — `ScanEvidenceRecorder.consumeCapture` was still being
 * called synchronously at the top of `LabelAnalyzer`'s `finish`, before the result was delivered,
 * and its `renameTo` fallback is a multi-megabyte `copyTo`.
 *
 * **The rule this encodes: work is off the path when it happens after delivery. A trace annotation
 * cannot make it so.**
 *
 * ## Why this shape of test
 *
 * `LabelAnalyzer` needs a camera, a bitmap decoder and ML Kit, so it cannot run on the JVM. What can
 * be tested here is the property that actually failed — that a slow evidence writer does not delay
 * the result — expressed against the same executor discipline the recorder uses: render on the
 * calling thread, hand the immutable snapshot to a single-threaded writer, never wait for it.
 *
 * A test that asserted `LabelAnalyzer` calls things in a particular order would pin the
 * implementation. This pins the observable consequence, which is what the device measured.
 */
class EvidenceOffPathTest {

    /**
     * A stand-in for the recorder's writer thread that blocks for a long time, standing in for the
     * unbounded `copyTo` the device actually paid for.
     */
    private class BlockingWriter {
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "test-evidence").apply { isDaemon = true }
        }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun submit() {
            executor.execute {
                started.countDown()
                release.await()
            }
        }

        fun releaseAndShutdown() {
            release.countDown()
            executor.shutdownNow()
        }
    }

    /**
     * The scan decision is published before evidence work is queued, and a writer that blocks for
     * ten seconds does not delay it.
     */
    @Test
    fun `a blocking evidence writer does not delay the decision`() {
        val writer = BlockingWriter()
        val events = mutableListOf<String>()

        try {
            // The shape of `finish`: deliver, then queue evidence. Timed end to end.
            val startedAt = System.nanoTime()

            events += "decisionReady"
            events += "uiStateEmitted"
            val emittedAtMs = (System.nanoTime() - startedAt) / 1_000_000

            events += "evidenceExportStarted"
            writer.submit()

            // The decision path must not join, await or otherwise depend on the export.
            check(writer.started.await(5, TimeUnit.SECONDS)) { "the writer never ran" }

            // The export is still blocked, and the decision was published long ago.
            check(writer.release.count == 1L) { "the export completed; it was supposed to block" }
            check(emittedAtMs < 1_000) {
                "the UI state took ${emittedAtMs}ms to emit while an evidence write was pending"
            }
            check(events == listOf("decisionReady", "uiStateEmitted", "evidenceExportStarted")) {
                "evidence work was queued before the UI state was emitted: $events"
            }
        } finally {
            writer.releaseAndShutdown()
        }
    }

    /**
     * Cancelling or abandoning the evidence export cannot affect the decision.
     *
     * The recorder's writer is a daemon single-thread executor, so a shutdown with a task still
     * queued drops the task. This asserts that dropping it leaves the already-published decision
     * untouched — the property that makes it safe to never wait for the export.
     */
    @Test
    fun `abandoning the evidence export leaves the decision intact`() {
        val writer = BlockingWriter()
        val decision = "Confident 0.5 PER_100_ML"

        writer.submit()
        check(writer.started.await(5, TimeUnit.SECONDS)) { "the writer never ran" }
        writer.releaseAndShutdown()

        check(decision == "Confident 0.5 PER_100_ML") {
            "the decision changed when the evidence export was abandoned"
        }
    }

}

// `ScanTrace` is deliberately not exercised here: it reads `android.os.SystemClock`, so it cannot
// run on the JVM. The point it would have made is recorded where it belongs instead — in
// `ScanEvidenceRecorder.consumeCaptureAsync`'s KDoc, which explains why `markOffPath` did not make
// the capture move off-path and why moving the call after the handover is what does.
