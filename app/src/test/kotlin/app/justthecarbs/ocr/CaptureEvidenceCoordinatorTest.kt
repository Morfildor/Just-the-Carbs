package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureEvidenceCoordinatorTest {

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
}
