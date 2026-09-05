package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ScanDecisionEngineTest {

    // Verified fixture helpers, reused verbatim from EvidenceResolverTest.kt (this repo's own
    // existing pattern for constructing CarbCandidate / OcrDocument / RecognitionEvidence for
    // resolver-level tests) -- do not invent a different construction style.

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(100, 100, 200, 130),
        evidence = emptyList(),
    )

    private fun documentWithConfidence(confidence: Float?) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement(
                text = "53,5",
                box = OcrBox(100, 100, 200, 130),
                blockId = 0,
                lineId = 0,
                confidence = confidence,
            ),
        ),
    )

    private fun evidence(
        source: EvidenceSource,
        value: String?,
        basis: NutritionBasis? = NutritionBasis.PER_100_G,
        confidence: Float? = 0.9f,
        observation: PhysicalObservationId = PhysicalObservationId.UNKNOWN,
    ): RecognitionEvidence {
        val reading = if (value != null) LabelReading.Confident(candidate(value, basis)) else LabelReading.NotFound
        return RecognitionEvidence(
            source = source,
            report = NutritionParseReport(reading, emptyList()),
            document = documentWithConfidence(confidence),
            physicalObservation = observation,
        )
    }

    @Test fun `two independent recognition runs agreeing on an Established-scale value becomes AutoAccept`() {
        // Two separate photographs of the same label, each read independently. That is the only way
        // to satisfy DISTINCT_OCR_AGREEMENT -- FULL_FRAME_PASS_A and SELECTED_REGION_OCR are two
        // different recognitionRuns but both from the same capture. Here we use separate physical
        // observations to trigger DISTINCT_OCR_AGREEMENT verification.
        val observation1 = PhysicalObservationId("obs1")
        val observation2 = PhysicalObservationId("obs2")
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5", observation = observation1),
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5", observation = observation2),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected AutoAccept, got $decision", decision is ScanDecision.AutoAccept)
    }

    @Test fun `a value only one pass found is not verified -- ScanDecision is Confirm, not AutoAccept`() {
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "2.3"),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected Confirm or FocusedEntry, got $decision", decision is ScanDecision.Confirm || decision is ScanDecision.FocusedEntry)
        assertTrue("must never silently AutoAccept an uncorroborated single-pass reading", decision !is ScanDecision.AutoAccept)
    }

    @Test fun `no evidence at all resolves to Crop, never AutoAccept`() {
        val decision = ScanDecisionEngine.decide(emptyList(), automatic = true)

        assertTrue(decision is ScanDecision.Crop || decision is ScanDecision.FocusedEntry)
        assertTrue(decision !is ScanDecision.AutoAccept)
    }

    @Test fun `passes disagreeing on the value become ScanDecision Conflict, never AutoAccept`() {
        // The exact fixture from EvidenceResolverTest's "passes disagreeing on the value are
        // conflicted and never pick one" -- 53.5 vs 9, from two different recognition runs, with no
        // structural corroboration for either.
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "9"),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected Conflict, got $decision", decision is ScanDecision.Conflict)
    }
}
