package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A real [PhysicalObservationId] is required at every camera-derived evidence site.
 *
 * ## What this closes
 *
 * [PhysicalObservationId.UNKNOWN] was the only value anything in production ever constructed, which
 * "lets production compile while the intended provenance route is entirely unwired" (2026-09-04
 * audit). Two recognitions of the exact same photograph -- the whole frame, the filtered/cropped
 * re-parse, and a Strategy B re-recognition of the same still -- could therefore look like two
 * independent physical observations to [EvidenceSource.recognitionRun]'s consumers, even though they
 * all read the same ink.
 *
 * This pins that [SelectedTableResolution.resolve]'s two construction sites (full-frame Pass A,
 * filtered Pass A) both stamp the caller-supplied still id, so they can never accidentally satisfy
 * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] against each other. See
 * [PhysicalObservationProvenanceTest] for the policy-level cases (same photograph cannot
 * self-corroborate; independent observations may) — this file is about the wiring that feeds it.
 */
class StillObservationWiringTest {

    @Test fun `forStill and forLiveSnapshot produce distinct, non-UNKNOWN ids`() {
        val still = PhysicalObservationId.forStill("capture-123")
        val live = PhysicalObservationId.forLiveSnapshot(aimEpoch = 7L, snapshotId = "abc")

        assertNotEquals(PhysicalObservationId.UNKNOWN, still)
        assertNotEquals(PhysicalObservationId.UNKNOWN, live)
        assertNotEquals(still, live)
    }

    @Test fun `two forStill calls with the same captureId produce the same id`() {
        assertEquals(PhysicalObservationId.forStill("capture-123"), PhysicalObservationId.forStill("capture-123"))
    }

    /** A clean carbohydrate row, just enough for Pass A to resolve a confident reading. */
    private fun cleanTable() = OcrDocument(
        width = 1000,
        height = 500,
        elements = listOf(
            OcrElement("per 100 g", OcrBox(400, 60, 640, 100), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
            OcrElement("53,5 g", OcrBox(430, 160, 560, 200), 0, 1),
        ),
    )

    private fun fakeRegion() = NormalizedRegion(0.0, 0.0, 1.0, 1.0)

    private fun fakePassAResult(): PassAResult {
        val document = cleanTable()
        return PassAResult(
            sessionId = 1L,
            document = document,
            report = NutritionTableInterpreter.interpret(document),
            bitmap = null,
            evidence = null,
            recognitionMs = 0L,
        )
    }

    @Test fun `full-frame and filtered Pass A evidence from one resolve call share the still id`() {
        val stillId = PhysicalObservationId.forStill("capture-1")
        val passA = fakePassAResult()
        val result = SelectedTableResolution.resolve(
            passA = passA,
            region = fakeRegion(),
            bitmap = null,
            stillObservationId = stillId,
            // Strategy B is irrelevant here and is disabled, so this measures Pass A's two views alone.
            recogniseRegion = { _, _ -> null },
        )
        // Every RecognitionEvidence this call produced that isn't LIVE_STABLE_FRAME must carry stillId.
        result.evidence
            .filter { it.source != EvidenceSource.LIVE_STABLE_FRAME }
            .forEach { assertEquals("source ${it.source} must carry the still id", stillId, it.physicalObservation) }
    }
}
