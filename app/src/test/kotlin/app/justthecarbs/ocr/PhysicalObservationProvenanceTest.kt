package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Independence is a property of the **photograph**, not of the recognizer invocation.
 *
 * ## The capture that forced this type to exist
 *
 * `docs/Scan Evidence new structure/20260904-113653-044` — a Fanta bottle whose 100 ml column prints
 * `0,5 g`. On a Samsung SM-S928B the app produced:
 *
 * ```
 * FULL_FRAME_PASS_A   [run=PASS_A]          Confident 0.59/PER_100_ML
 * FILTERED_PASS_A     [run=PASS_A]          Confident 0.59/PER_100_ML
 * SELECTED_REGION_OCR [run=SELECTED_REGION] Confident 0.59/PER_100_ML
 * automatic-verification: DISTINCT_OCR_AGREEMENT
 * final UI action : AUTO_ADVANCE
 * ```
 *
 * `0.59` is the printed `0,5 g` with the `g` glyph recognised as a `9`. The app advanced with no
 * confirmation step on a value **ten times** the printed one, on a dosing input.
 *
 * [RecognitionRun] was the wrong unit of independence. It distinguishes `PASS_A` from
 * `SELECTED_REGION`, and those genuinely are separate ML Kit invocations — but both read *the same
 * JPEG*. `SELECTED_REGION_OCR` crops that one capture and recognises it again, so it sees the same
 * physical ink, the same focus, the same motion blur and the same specular highlight. When the optics
 * are what corrupted the glyph, a second look at the same pixels reproduces the corruption. Two
 * correlated observations agreeing is one observation counted twice.
 *
 * ## What this changes and what it deliberately does not
 *
 * Same-observation views keep every other job they had: they recover labels the full frame cannot
 * read, they generate candidates, they support confirmation and focused entry, and their disagreement
 * is still a conflict. The single thing they may no longer do is satisfy
 * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT], because that route is the one that skips the
 * user's confirmation tap.
 */
class PhysicalObservationProvenanceTest {

    private fun evidence(
        source: EvidenceSource,
        value: String?,
        observation: PhysicalObservationId,
        basis: NutritionBasis? = NutritionBasis.PER_100_ML,
    ): RecognitionEvidence {
        val reading = if (value == null || basis == null) {
            LabelReading.NotFound
        } else {
            LabelReading.Confident(
                CarbCandidate(
                    sourceLine = "Koolhydraten: $value",
                    label = "Koolhydraten",
                    value = BigDecimal(value),
                    basis = basis,
                    score = 100,
                    geometry = OcrBox(0, 0, 10, 10),
                    evidence = emptyList(),
                    column = if (basis == NutritionBasis.PER_100_G) {
                        NutritionColumnKind.PER_100_G
                    } else {
                        NutritionColumnKind.PER_100_ML
                    },
                ),
            )
        }
        return RecognitionEvidence(
            source = source,
            report = NutritionParseReport(reading = reading, diagnostics = emptyList()),
            document = null,
            physicalObservation = observation,
        )
    }

    private val frameA = PhysicalObservationId("FRAME_A")
    private val frameB = PhysicalObservationId("FRAME_B")

    // ============================================ same physical observation cannot self-corroborate

    /**
     * The exact `113653-044` shape, and the hard gate of this pass.
     *
     * Both runs read `0.59` because both read the same photograph of the same corrupted glyph.
     */
    @Test
    fun `a crop of the same frame cannot verify the frame it was cropped from`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "0.59", frameA),
                evidence(EvidenceSource.FILTERED_PASS_A, "0.59", frameA),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "0.59", frameA),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
        assertFalse(verdict.mayAdvanceAutomatically)
    }

    /**
     * The rejection has to *say* it was the same photograph.
     *
     * A bundle reading "only one recognition run" would be false here — there were two runs. The
     * reason a person debugging a scan needs is that both looked at one frame.
     */
    @Test
    fun `the rejection names the physical observation, not the run count`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "0.59", frameA),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "0.59", frameA),
            ),
        )

        val reason = verdict.rejectionReason.orEmpty()
        assertTrue(
            "expected the reason to cite one physical observation, was: $reason",
            reason.contains("physical observation", ignoreCase = true) ||
                reason.contains("same photograph", ignoreCase = true),
        )
    }

    /** Every derived view is correlated, whatever transform produced it. */
    @Test
    fun `every view derived from one capture shares its physical observation`() {
        val sources = listOf(
            EvidenceSource.FULL_FRAME_PASS_A,
            EvidenceSource.FILTERED_PASS_A,
            EvidenceSource.SELECTED_REGION_OCR,
        )
        val evidence = sources.map { evidence(it, "0.59", frameA) }

        assertEquals(1, evidence.map { it.physicalObservation }.distinct().size)
        assertFalse(AutomaticVerification.verify(evidence).mayAdvanceAutomatically)
    }

    // ==================================================== independent observations may corroborate

    /**
     * The route this pass preserves rather than removes.
     *
     * Two genuinely different photographs agreeing is the evidence
     * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] always claimed to be.
     */
    @Test
    fun `two independent physical observations may corroborate a reading`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "0.5", frameA),
                evidence(EvidenceSource.SECOND_OBSERVATION_PASS, "0.5", frameB),
            ),
        )

        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
        assertTrue(verdict.mayAdvanceAutomatically)
    }

    /** Disagreement across genuinely independent frames is a conflict, never a verification. */
    @Test
    fun `independent observations that disagree do not verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "0.5", frameA),
                evidence(EvidenceSource.SECOND_OBSERVATION_PASS, "0.59", frameB),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
        assertFalse(verdict.mayAdvanceAutomatically)
    }

    /**
     * A second frame agreeing on the amount but not on what it is measured per is not agreement.
     *
     * Same rule [RecognitionEvidence.fullyAgreesWith] already applies within a frame; stated across
     * frames so the new route cannot become the looser of the two.
     */
    @Test
    fun `independent observations must agree on the basis too`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "0.5", frameA, NutritionBasis.PER_100_ML),
                evidence(
                    EvidenceSource.SECOND_OBSERVATION_PASS, "0.5", frameB, NutritionBasis.PER_100_G,
                ),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
    }

    // ================================================================================ the type itself

    /** Distinct captures get distinct identities; that is the whole content of the type. */
    @Test
    fun `physical observation identity distinguishes captures`() {
        assertNotEquals(frameA, frameB)
        assertEquals(frameA, PhysicalObservationId("FRAME_A"))
    }

    /**
     * A default-constructed evidence object must not accidentally look independent.
     *
     * Two pieces of evidence with no stated observation are not thereby two photographs — and the
     * safe reading of "unknown" is "possibly the same frame".
     */
    @Test
    fun `evidence with no stated observation does not corroborate`() {
        val reading = LabelReading.Confident(
            CarbCandidate(
                sourceLine = "Koolhydraten: 0.59",
                label = "Koolhydraten",
                value = BigDecimal("0.59"),
                basis = NutritionBasis.PER_100_ML,
                score = 100,
                geometry = OcrBox(0, 0, 10, 10),
                evidence = emptyList(),
                column = NutritionColumnKind.PER_100_ML,
            ),
        )
        val bare = { source: EvidenceSource ->
            RecognitionEvidence(
                source = source,
                report = NutritionParseReport(reading = reading, diagnostics = emptyList()),
                document = null,
            )
        }

        val verdict = AutomaticVerification.verify(
            listOf(bare(EvidenceSource.FULL_FRAME_PASS_A), bare(EvidenceSource.SELECTED_REGION_OCR)),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
    }
}
