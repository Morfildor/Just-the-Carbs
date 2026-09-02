package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may and may not count as corroboration, pinned against the device evidence that motivated it.
 *
 * The measured failure is `docs/Scan Evidence 01-09-26/20260901-211417-935`, whose `selection.txt`
 * records:
 *
 * ```
 * reading         : Ambiguous (Strategy A re-parse)
 * strategy B      : RAN_NO_READING
 * resolver.verdict: Resolved   <- what AutomaticScanAdvance reads
 * passes contributing evidence: FULL_FRAME_PASS_A, FILTERED_PASS_A, SELECTED_REGION_OCR
 * ```
 *
 * Three things in that block are misleading and each is pinned below: an ambiguity was called
 * *resolved*; a pass that produced no reading was listed as contributing; and two parses of one
 * recognition were listed beside an independent run as though all three were opinions.
 */
class EvidenceIndependenceTest {

    private fun candidate(value: String, basis: NutritionBasis) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = NutritionParserThresholds.CONFIDENT_SCORE,
        geometry = OcrBox(0, 0, 10, 10),
        evidence = emptyList(),
        column = if (basis == NutritionBasis.PER_100_G) {
            NutritionColumnKind.PER_100_G
        } else {
            NutritionColumnKind.PER_100_ML
        },
    )

    private fun report(reading: LabelReading) = NutritionParseReport(reading, emptyList(), null)

    private fun evidence(
        source: EvidenceSource,
        reading: LabelReading,
    ) = RecognitionEvidence(source = source, report = report(reading), document = null)

    private fun confident(source: EvidenceSource, value: String, basis: NutritionBasis) =
        evidence(source, LabelReading.Confident(candidate(value, basis)))

    private fun ambiguousDrinkReading() = LabelReading.Ambiguous(
        listOf(
            candidate("0.5", NutritionBasis.PER_100_ML),
            candidate("1.3", NutritionBasis.PER_100_ML),
        ),
    )

    // ------------------------------------------------------------------ the recorded failure

    /**
     * **The regression this file exists for.** An ambiguous Strategy A reading, with Strategy B
     * having run and produced nothing, must not resolve.
     *
     * `RAN_NO_READING` contributes zero support: a recognition that answered nothing has not
     * corroborated anything. Previously this returned `Outcome.Resolved`, which is what the device
     * bundle recorded.
     */
    @Test
    fun `an ambiguous reading with a second pass that found nothing does not resolve`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguousDrinkReading()),
                evidence(EvidenceSource.FILTERED_PASS_A, ambiguousDrinkReading()),
                evidence(EvidenceSource.SELECTED_REGION_OCR, LabelReading.NotFound),
            ),
        )

        assertTrue(
            "an ambiguity with no corroboration must be Unresolved, got $outcome",
            outcome is EvidenceResolver.Outcome.Unresolved,
        )
    }

    /** And it must not advance past the crop step. */
    @Test
    fun `an unresolved ambiguity never advances automatically`() {
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguousDrinkReading())),
        )

        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /**
     * Repeating the same ambiguity across two *independent* runs still does not resolve it.
     *
     * Two passes that both say "either 0.5 or 1.3" have narrowed nothing. Treating repetition as
     * agreement would be the worst available form of the count-one-opinion-twice error, because the
     * app would then present one of two competing numbers as settled.
     */
    @Test
    fun `the same ambiguity seen by two independent runs is still unresolved`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguousDrinkReading()),
                evidence(EvidenceSource.SELECTED_REGION_OCR, ambiguousDrinkReading()),
            ),
        )

        assertTrue(
            "repetition is not resolution, got $outcome",
            outcome is EvidenceResolver.Outcome.Unresolved,
        )
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    // ------------------------------------------------------------------ evidence families

    /**
     * The two Pass A views are one evidence family and cannot corroborate each other. This is the
     * pre-existing guarantee; it is restated here because the family question is what this file is
     * about, and because the drink bundle listed all three passes as though they were peers.
     */
    @Test
    fun `the two pass A views share a recognition run`() {
        assertEquals(
            EvidenceSource.FULL_FRAME_PASS_A.recognitionRun,
            EvidenceSource.FILTERED_PASS_A.recognitionRun,
        )
        assertEquals(RecognitionRun.SELECTED_REGION, EvidenceSource.SELECTED_REGION_OCR.recognitionRun)
    }

    /**
     * A value only the two Pass A views agree on is Pass A's own reading — resolved exactly as it
     * was before evidence existed, and explicitly NOT reported as corroborated by two sources.
     */
    @Test
    fun `two views of one recognition resolve as a lone pass A reading`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                confident(EvidenceSource.FULL_FRAME_PASS_A, "72.0", NutritionBasis.PER_100_G),
                confident(EvidenceSource.FILTERED_PASS_A, "72.0", NutritionBasis.PER_100_G),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.Resolved)
        assertEquals(
            "both sources come from one recognition run",
            1,
            (outcome as EvidenceResolver.Outcome.Resolved)
                .agreeingSources.map { it.recognitionRun }.distinct().size,
        )
        assertTrue("a lone confident pass A still advances", AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** A genuinely independent run agreeing with Pass A is corroboration, and advances. */
    @Test
    fun `an independent run agreeing with pass A corroborates it`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                confident(EvidenceSource.FULL_FRAME_PASS_A, "59.2", NutritionBasis.PER_100_G),
                confident(EvidenceSource.SELECTED_REGION_OCR, "59.2", NutritionBasis.PER_100_G),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.Resolved)
        assertEquals(
            2,
            (outcome as EvidenceResolver.Outcome.Resolved)
                .agreeingSources.map { it.recognitionRun }.distinct().size,
        )
        assertTrue(AutomaticScanAdvance.mayAdvance(outcome))
    }

    // ------------------------------------------------------------------ value AND basis

    /**
     * Agreement is on the value **and** the basis. The same number under two different bases is a
     * conflict, not a confirmation — this is the drink's exact hazard shape, where `1.3` is correct
     * per 250 ml and catastrophic per 100 ml.
     */
    @Test
    fun `the same value under different bases is a conflict, never agreement`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                confident(EvidenceSource.FULL_FRAME_PASS_A, "1.3", NutritionBasis.PER_100_ML),
                confident(EvidenceSource.SELECTED_REGION_OCR, "1.3", NutritionBasis.PER_100_G),
            ),
        )

        assertTrue(
            "differing bases must conflict, got $outcome",
            outcome is EvidenceResolver.Outcome.Conflicted,
        )
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** Scale differences are not disagreement: 53.5 and 53.50 are one value. */
    @Test
    fun `agreement ignores decimal scale`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                confident(EvidenceSource.FULL_FRAME_PASS_A, "53.5", NutritionBasis.PER_100_G),
                confident(EvidenceSource.SELECTED_REGION_OCR, "53.50", NutritionBasis.PER_100_G),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.Resolved)
    }

    /** Two independent runs claiming different values is always a refusal, never a vote. */
    @Test
    fun `two independent runs disagreeing on the value conflict`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                confident(EvidenceSource.FULL_FRAME_PASS_A, "2.09", NutritionBasis.PER_100_G),
                confident(EvidenceSource.SELECTED_REGION_OCR, "2", NutritionBasis.PER_100_G),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.Conflicted)
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    // ------------------------------------------------------------------ nothing at all

    /** No reading anywhere is Nothing, and never advances. */
    @Test
    fun `no reading from any pass is nothing`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, LabelReading.NotFound),
                evidence(EvidenceSource.SELECTED_REGION_OCR, LabelReading.NotFound),
            ),
        )

        assertEquals(EvidenceResolver.Outcome.Nothing, outcome)
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** An empty evidence list cannot advance either. */
    @Test
    fun `no evidence at all is nothing`() {
        assertEquals(EvidenceResolver.Outcome.Nothing, EvidenceResolver.resolve(emptyList()))
    }
}
