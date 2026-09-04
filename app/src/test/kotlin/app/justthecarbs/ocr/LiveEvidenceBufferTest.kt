package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pre-shutter live evidence retention (spec §5, §9).
 *
 * The physical regression these pin: live reaches a usable interpretation, the user captures, the
 * still path returns NotFound, and the live evidence must still exist. Equally important is what they
 * forbid — a single lucky frame must never become authoritative.
 */
class LiveEvidenceBufferTest {

    private fun candidate(value: String, basis: NutritionBasis? = NutritionBasis.PER_100_G) =
        CarbCandidate(
            sourceLine = "Koolhydraten $value g",
            label = "Koolhydraten",
            value = BigDecimal(value),
            basis = basis,
            score = 120,
            geometry = OcrBox(10, 10, 100, 40),
            evidence = emptyList(),
        )

    private fun confident(value: String, basis: NutritionBasis? = NutritionBasis.PER_100_G) =
        LabelReading.Confident(candidate(value, basis))

    @Test
    fun `three agreeing frames make a consensus`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        val consensus = buffer.stableConsensus(nowMs = 1250)

        assertNotNull(consensus)
        assertEquals(0, consensus!!.value.compareTo(BigDecimal("61.9")))
    }

    /** THE core safety rule: one frame is never enough, however good it looked. */
    @Test
    fun `a single frame is never a consensus`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)

        assertNull(buffer.stableConsensus(nowMs = 1010))
    }

    @Test
    fun `two frames are still not enough`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1050)

        assertNull(buffer.stableConsensus(nowMs = 1060))
    }

    /** Disagreement means the camera was unstable. Staying quiet beats picking a side. */
    @Test
    fun `frames disagreeing on the value produce no consensus`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("47.6"), 1100)
        buffer.record(confident("61.9"), 1200)

        assertNull(buffer.stableConsensus(nowMs = 1250))
    }

    @Test
    fun `frames disagreeing on the basis produce no consensus`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("5", NutritionBasis.PER_100_G), 1000)
        buffer.record(confident("5", NutritionBasis.PER_100_ML), 1100)
        buffer.record(confident("5", NutritionBasis.PER_100_G), 1200)

        assertNull(buffer.stableConsensus(nowMs = 1250))
    }

    /** Scale must not fake disagreement — the BigDecimal trap this repo has hit twice. */
    @Test
    fun `differing scale still agrees`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("5"), 1000)
        buffer.record(confident("5.0"), 1100)
        buffer.record(confident("5.00"), 1200)

        assertNotNull(buffer.stableConsensus(nowMs = 1250))
    }

    /** Stale frames are from a different aim and must not corroborate the current one. */
    @Test
    fun `observations outside the window do not count`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        assertNull("frames 10s old must have aged out", buffer.stableConsensus(nowMs = 11_000))
    }

    @Test
    fun `ambiguous and not-found frames never produce a consensus`() {
        val buffer = LiveEvidenceBuffer()
        repeat(5) { i ->
            buffer.record(LabelReading.Ambiguous(listOf(candidate("53.5"), candidate("6.7"))), 1000L + i * 50)
            buffer.record(LabelReading.NotFound, 1000L + i * 50)
        }

        assertNull(buffer.stableConsensus(nowMs = 1300))
    }

    /** Retake and screen exit must not let evidence leak across capture sessions. */
    @Test
    fun `clear forgets everything`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        buffer.clear()

        assertNull(buffer.stableConsensus(nowMs = 1250))
        assertEquals(emptyList<LiveEvidenceBuffer.Observation>(), buffer.snapshot())
    }

    /** Bounded memory: aiming for a minute must not accumulate a minute of readings. */
    @Test
    fun `the buffer is bounded by capacity`() {
        val buffer = LiveEvidenceBuffer(capacity = 4)
        repeat(50) { i -> buffer.record(confident("61.9"), 1000L + i) }

        assertEquals(4, buffer.snapshot().size)
    }

    /** Evidence handed to the resolver is tagged as live, which is what stops it resolving alone. */
    @Test
    fun `consensus surfaces as live-tagged evidence`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        val evidence = buffer.asEvidence(nowMs = 1250)

        assertNotNull(evidence)
        assertEquals(EvidenceSource.LIVE_STABLE_FRAME, evidence!!.source)
    }

    /**
     * End-to-end guard for the video's failure, expressed through the resolver.
     *
     * Live consensus 61.9 + still NotFound must reach the user as a verification proposal, never as
     * a settled answer and never as nothing at all.
     */
    @Test
    fun `live consensus with a failed still becomes a verification proposal`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        val outcome = EvidenceResolver.resolve(
            listOfNotNull(
                RecognitionEvidence(
                    source = EvidenceSource.FULL_FRAME_PASS_A,
                    report = NutritionParseReport(LabelReading.NotFound, emptyList()),
                    document = null,
                ),
                buffer.asEvidence(nowMs = 1250),
            ),
        )

        val proposal = outcome as? EvidenceResolver.Outcome.NeedsVerification
            ?: throw AssertionError("expected NeedsVerification, got $outcome")
        assertEquals(EvidenceSource.LIVE_STABLE_FRAME, proposal.source)
    }

    /** And when the still path agrees, the two corroborate into a resolved answer. */
    @Test
    fun `live consensus agreeing with the still path resolves`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000)
        buffer.record(confident("61.9"), 1100)
        buffer.record(confident("61.9"), 1200)

        val outcome = EvidenceResolver.resolve(
            listOfNotNull(
                RecognitionEvidence(
                    source = EvidenceSource.FULL_FRAME_PASS_A,
                    report = NutritionParseReport(confident("61.9"), emptyList()),
                    document = null,
                ),
                buffer.asEvidence(nowMs = 1250),
            ),
        )

        val resolved = outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got $outcome")
        assertEquals(2, resolved.agreeingSources.size)
    }

    // --- §9, startup-hardening pass: session binding and concurrency. ---

    @Test
    fun `frames from a different session never corroborate the session being asked about`() {
        // Exactly the hazard §9 exists to close: a package the user swept the camera past (or a
        // Retake's abandoned in-flight recognition) recorded three agreeing frames under session 1.
        // Session 2 is the capture actually being evaluated and must see none of them.
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000, sessionId = 1L)
        buffer.record(confident("61.9"), 1100, sessionId = 1L)
        buffer.record(confident("61.9"), 1200, sessionId = 1L)

        assertNull(
            "session 1's frames must not corroborate session 2",
            buffer.stableConsensus(nowMs = 1250, sessionId = 2L),
        )
    }

    @Test
    fun `frames recorded under the requested session still reach consensus`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000, sessionId = 7L)
        buffer.record(confident("61.9"), 1100, sessionId = 7L)
        buffer.record(confident("61.9"), 1200, sessionId = 7L)

        val consensus = buffer.stableConsensus(nowMs = 1250, sessionId = 7L)

        assertNotNull(consensus)
        assertEquals(0, consensus!!.value.compareTo(BigDecimal("61.9")))
    }

    @Test
    fun `two agreeing frames from the current session plus one from a stale session is not consensus`() {
        // MIN_AGREEING_FRAMES is 3. Session scoping must reduce the pool available to the current
        // session rather than merely relabel it — a stale-session frame cannot pad out the count.
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 900, sessionId = 1L)
        buffer.record(confident("61.9"), 1000, sessionId = 2L)
        buffer.record(confident("61.9"), 1100, sessionId = 2L)

        assertNull(buffer.stableConsensus(nowMs = 1150, sessionId = 2L))
    }

    @Test
    fun `asEvidence is also session-scoped`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000, sessionId = 1L)
        buffer.record(confident("61.9"), 1100, sessionId = 1L)
        buffer.record(confident("61.9"), 1200, sessionId = 1L)

        assertNull(
            "asEvidence must apply the same session filter as stableConsensus",
            buffer.asEvidence(nowMs = 1250, sessionId = 2L),
        )
        assertNotNull(buffer.asEvidence(nowMs = 1250, sessionId = 1L))
    }

    @Test
    fun `clearing does not need a session id and forgets every session`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000, sessionId = 1L)
        buffer.record(confident("61.9"), 1100, sessionId = 2L)

        buffer.clear()

        assertNull(buffer.stableConsensus(nowMs = 1150, sessionId = 1L))
        assertNull(buffer.stableConsensus(nowMs = 1150, sessionId = 2L))
        assertTrue(buffer.snapshot().isEmpty())
    }

    /**
     * The concurrency hazard itself: [record] from many threads (standing in for the analyzer's
     * frame callback) racing [stableConsensus] reads from another (standing in for the
     * still-recognition coroutine's `Dispatchers.IO` read) must never throw and must never observe a
     * torn/inconsistent deque. Before synchronization was added, a plain `ArrayDeque` read via
     * `.filter{}`/`.mapNotNull{}` while another thread called `addLast`/`removeFirst` was a real
     * `ConcurrentModificationException` hazard — ArrayDeque is explicitly documented as not
     * thread-safe. This does not prove the absence of a race (no finite test can), but it drives
     * enough concurrent traffic through the exact call shape the app uses that the old unsynchronized
     * version reliably threw within a handful of iterations when this test was run against it.
     */
    @Test
    fun `concurrent record and consensus reads never throw`() {
        val buffer = LiveEvidenceBuffer(capacity = 12)
        val writerCount = 4
        val iterationsPerWriter = 500
        val failure = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(writerCount + 1)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(writerCount + 1)

        repeat(writerCount) { writerIndex ->
            pool.submit {
                try {
                    startLatch.await()
                    repeat(iterationsPerWriter) { i ->
                        buffer.record(
                            confident("61.9"),
                            timestampMs = (writerIndex * iterationsPerWriter + i).toLong(),
                            sessionId = writerIndex.toLong(),
                        )
                    }
                } catch (t: Throwable) {
                    failure.set(true)
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        // One reader thread, standing in for the still-recognition coroutine's single read per
        // capture — but hammered repeatedly here to maximise the chance of catching a race.
        pool.submit {
            try {
                startLatch.await()
                repeat(iterationsPerWriter) {
                    buffer.stableConsensus(nowMs = Long.MAX_VALUE, sessionId = 0L)
                    buffer.snapshot()
                }
            } catch (t: Throwable) {
                failure.set(true)
            } finally {
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        pool.shutdown()

        assertTrue("threads did not finish in time", finished)
        assertFalse("record/consensus/snapshot must never throw under concurrent access", failure.get())
    }

    @Test
    fun `concurrent record and clear never throw`() {
        val buffer = LiveEvidenceBuffer(capacity = 12)
        val failure = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val iterations = 1000

        pool.submit {
            try {
                startLatch.await()
                repeat(iterations) { i -> buffer.record(confident("61.9"), i.toLong()) }
            } catch (t: Throwable) {
                failure.set(true)
            } finally {
                doneLatch.countDown()
            }
        }
        pool.submit {
            try {
                startLatch.await()
                repeat(iterations) { buffer.clear() }
            } catch (t: Throwable) {
                failure.set(true)
            } finally {
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        pool.shutdown()

        assertTrue("threads did not finish in time", finished)
        assertFalse("record/clear must never throw under concurrent access", failure.get())
    }

    /**
     * A queued analyser callback landing after a snapshot/consensus read was taken must be reflected
     * only in the *next* read, never corrupt the one already in progress — the shape of "a queued
     * callback arriving after snapshot creation" the spec calls out explicitly. `synchronized` makes
     * each individual call atomic, so a read either happens entirely before or entirely after a given
     * `record`; there is no interleaved half-state to observe.
     */
    @Test
    fun `a record queued immediately after a snapshot is read does not corrupt that snapshot`() {
        val buffer = LiveEvidenceBuffer()
        buffer.record(confident("61.9"), 1000, sessionId = 1L)
        buffer.record(confident("61.9"), 1100, sessionId = 1L)
        buffer.record(confident("61.9"), 1200, sessionId = 1L)

        val snapshotBefore = buffer.snapshot()
        assertEquals(3, snapshotBefore.size)

        // Simulates a frame callback landing right after the snapshot was taken.
        buffer.record(confident("61.9"), 1300, sessionId = 1L)

        // The earlier snapshot is an immutable List — it must be unaffected by the later write.
        assertEquals("the snapshot already taken must not grow", 3, snapshotBefore.size)
        assertEquals(4, buffer.snapshot().size)
    }
}
