package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Reproduces LabelScannerScreen's actual capture lifecycle against CaptureEvidenceCoordinator and
 * LiveEvidenceBuffer, without needing Android/Compose. This is the test the review asked for: it
 * must fail against the OLD single-AtomicLong design (which this test does not use -- it uses the
 * NEW coordinator, so a regression back to one shared counter would fail this suite, not merely
 * fail to compile).
 */
class CaptureLifecycleIntegrationTest {

    private fun confidentCandidate(value: BigDecimal) = CarbCandidate(
        sourceLine = "test", label = "test", value = value,
        basis = app.justthecarbs.domain.NutritionBasis.PER_100_G,
        // OcrBox's coordinates are Int (source-image pixel space), not Float -- the brief's
        // sketch of this test wrote `0f`, which does not compile against the real type.
        score = 100, geometry = OcrBox(0, 0, 10, 10), evidence = emptyList(), column = null,
    )

    @Test fun `three frames recorded while aiming survive a shutter press and a slow still pipeline`() {
        val coordinator = CaptureEvidenceCoordinator()
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"))

        // User aims the camera; three agreeing live frames land, exactly as the analyzer callback
        // does in production -- recorded against the CURRENT aim epoch (which does not move yet).
        var clock = 0L
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 100
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 100
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 50 // user taps the shutter shortly after the third frame

        // Shutter handler: freeze BEFORE bumping work generation (this ordering is the fix).
        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = clock)
        val workGeneration = coordinator.beginNewWork()

        // Simulate a slow still pipeline -- 2500ms, well past LiveEvidenceBuffer's 1500ms window.
        clock += 2500

        // The still pipeline is done; it queries the buffer the way readSelectedTable does today.
        // A query issued AFTER 2500ms would find nothing (window has passed) if it re-queried the
        // buffer live -- which is exactly why the frozen snapshot, not a live re-query, must be
        // what the resolver receives.
        val liveQueryNow = buffer.asEvidence(nowMs = clock, aimEpoch = coordinator.aimEpoch)

        assertEquals("a live re-query after the window has passed must find nothing", null, liveQueryNow)
        assertNotNull("but the frozen snapshot taken AT shutter time must still carry the candidate", snapshot.candidate)
        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
        assertEquals(true, coordinator.isCurrentWork(workGeneration))
    }

    @Test fun `an aim epoch from a previous attempt cannot corroborate the current one`() {
        val coordinator = CaptureEvidenceCoordinator()
        val buffer = LiveEvidenceBuffer()
        val staleCandidate = confidentCandidate(BigDecimal("99"))

        // Frames recorded under a previous aim (before a retake).
        buffer.record(LabelReading.Confident(staleCandidate), 0L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(staleCandidate), 100L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(staleCandidate), 200L, aimEpoch = coordinator.aimEpoch)

        // User retakes -- a genuinely new aim begins.
        coordinator.beginNewAim()

        // New frames under the new aim disagree with the stale ones (a different package, say).
        val freshCandidate = confidentCandidate(BigDecimal("46"))
        buffer.record(LabelReading.Confident(freshCandidate), 300L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(freshCandidate), 400L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(freshCandidate), 500L, aimEpoch = coordinator.aimEpoch)

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 550L)

        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
    }

    @Test fun `retake cancels stale work -- a late result from before the retake is not current`() {
        val coordinator = CaptureEvidenceCoordinator()
        val staleGeneration = coordinator.beginNewWork() // first capture attempt

        coordinator.beginNewAim() // user retakes
        coordinator.beginNewWork() // the retake's own capture attempt

        // A result from the FIRST attempt finally arrives, late.
        assertEquals(false, coordinator.isCurrentWork(staleGeneration))
    }

    @Test fun `two captures under one aim epoch (no retake between them) each get their own work generation`() {
        val coordinator = CaptureEvidenceCoordinator()
        val gen1 = coordinator.beginNewWork()
        val gen2 = coordinator.beginNewWork()

        assertEquals(false, coordinator.isCurrentWork(gen1))
        assertEquals(true, coordinator.isCurrentWork(gen2))
    }
}
