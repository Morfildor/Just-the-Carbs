package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal

class CaptureEvidenceCoordinatorTest {

    private fun confidentCandidate(value: BigDecimal, basis: NutritionBasis): CarbCandidate = CarbCandidate(
        sourceLine = "test",
        label = "test",
        value = value,
        basis = basis,
        score = 100,
        geometry = OcrBox(0, 0, 10, 10),
        evidence = emptyList(),
        column = null,
    )

    @Test fun `beginNewWork bumps workGeneration but not aimEpoch`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aimBefore = coordinator.aimEpoch
        val genBefore = coordinator.workGeneration

        coordinator.beginNewWork()

        assertEquals("aimEpoch must not change on a work-generation bump", aimBefore, coordinator.aimEpoch)
        assertNotEquals("workGeneration must change", genBefore, coordinator.workGeneration)
    }

    @Test fun `beginNewAim bumps aimEpoch but not workGeneration`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aimBefore = coordinator.aimEpoch
        val genBefore = coordinator.workGeneration

        coordinator.beginNewAim()

        assertNotEquals("aimEpoch must change", aimBefore, coordinator.aimEpoch)
        assertEquals("workGeneration must not change on an aim-epoch bump", genBefore, coordinator.workGeneration)
    }

    @Test fun `isCurrentWork is false for a stale generation after beginNewWork`() {
        val coordinator = CaptureEvidenceCoordinator()
        val staleGeneration = coordinator.workGeneration

        coordinator.beginNewWork()

        assertEquals(false, coordinator.isCurrentWork(staleGeneration))
        assertEquals(true, coordinator.isCurrentWork(coordinator.workGeneration))
    }

    @Test fun `repeated captures under one aim each get a fresh work generation without moving the aim epoch`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aim = coordinator.aimEpoch

        val gen1 = coordinator.beginNewWork()
        val gen2 = coordinator.beginNewWork()

        assertNotEquals(gen1, gen2)
        assertEquals("two shutter presses under the same aim must not move the aim epoch", aim, coordinator.aimEpoch)
    }

    @Test fun `freezeAtShutter uses the same clock value passed to it, not a fresh wall-clock read`() {
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        val coordinator = CaptureEvidenceCoordinator()
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1000L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1100L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1200L, aimEpoch = coordinator.aimEpoch)

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 1250L)

        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
        assertEquals(3, snapshot.observationCount)
    }

    @Test fun `freezeAtShutter reports a rejection reason when nothing is available`() {
        val buffer = LiveEvidenceBuffer()
        val coordinator = CaptureEvidenceCoordinator()

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 1000L)

        assertEquals(null, snapshot.candidate)
        assertEquals("no observations recorded for this aim epoch", snapshot.rejectionReason)
    }
}
