package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

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
}
