package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The gate that separates *structurally plausible* from *independently verified*.
 *
 * Every case here is about one question: can a single OCR run, however cleanly it parsed, clear a
 * value for automatic use? It must not, and the reason is `085542-213` — a misread `72,0 g` that
 * satisfied every structural rule the app has.
 */
class AutomaticVerificationTest {

    private fun evidence(
        source: EvidenceSource,
        value: String?,
        basis: NutritionBasis? = NutritionBasis.PER_100_G,
        document: OcrDocument? = null,
    ): RecognitionEvidence {
        val reading = if (value == null || basis == null) {
            LabelReading.NotFound
        } else {
            LabelReading.Confident(
                CarbCandidate(
                    sourceLine = "Carbohydrate $value g",
                    label = "Carbohydrate",
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
            document = document,
        )
    }

    // ================================================== same-run representations cannot self-confirm

    /**
     * THE structural property of this pass.
     *
     * `FULL_FRAME_PASS_A` and `FILTERED_PASS_A` are two parses of one ML Kit run over the same
     * characters — the second is literally a subset of the first's elements. Their agreeing proves
     * the filter kept the winning row and nothing more.
     */
    @Test
    fun `two views of one recognition run cannot verify each other`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.FILTERED_PASS_A, "72"),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
        assertFalse(verdict.mayAdvanceAutomatically)
        assertNotNull("a refusal must say why", verdict.rejectionReason)
    }

    /** Adding more views of the same run does not accumulate into corroboration. */
    @Test
    fun `three views of one recognition run still cannot verify each other`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.FILTERED_PASS_A, "72"),
                evidence(EvidenceSource.FILTERED_PASS_A, "72"),
            ),
        )

        assertFalse(verdict.mayAdvanceAutomatically)
    }

    // ================================================================ distinct runs

    /** Two genuinely separate recognitions agreeing on amount and basis is verification. */
    @Test
    fun `two distinct recognition runs agreeing on amount and basis verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "72"),
            ),
        )

        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
        assertTrue(verdict.mayAdvanceAutomatically)
    }

    /**
     * `72`, `72.0` and `72,0` are the same amount.
     *
     * `BigDecimal.equals` compares scale, so a scale-sensitive comparison would report a genuine
     * agreement as a conflict. This repo has hit that trap twice.
     */
    @Test
    fun `distinct runs agree across differing scales of the same amount`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "72.0"),
            ),
        )

        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
    }

    /** Different amounts are a conflict, whoever read them. */
    @Test
    fun `distinct runs disagreeing on the amount do not verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "12"),
            ),
        )

        assertFalse(verdict.mayAdvanceAutomatically)
    }

    /** Same amount under a different basis is a conflict, not agreement. */
    @Test
    fun `distinct runs agreeing on the amount but not the basis do not verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72", NutritionBasis.PER_100_G),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "72", NutritionBasis.PER_100_ML),
            ),
        )

        assertFalse(verdict.mayAdvanceAutomatically)
    }

    /** A second run that found nothing is not a second opinion. */
    @Test
    fun `a second run returning nothing does not verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "72"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, null),
            ),
        )

        assertFalse(verdict.mayAdvanceAutomatically)
    }

    @Test
    fun `no confident evidence at all does not verify`() {
        val verdict = AutomaticVerification.verify(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, null)),
        )

        assertFalse(verdict.mayAdvanceAutomatically)
        assertNotNull(verdict.rejectionReason)
    }

    // ================================================================ the cross-column route

    /** The real misread, refused by the label's own arithmetic. */
    @Test
    fun `the misread cracker is vetoed by cross-column consistency`() {
        val document = FourthSessionFixtures.crackerMisreadTotal()
        val verdict = AutomaticVerification.verify(document, NutritionTableInterpreter.interpret(document))

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
        assertNotNull(verdict.candidateRatio)
        assertNotNull(verdict.medianRatio)
        assertTrue(
            "expected a contradiction, got ${verdict.rejectionReason}",
            verdict.rejectionReason!!.contains("contradict"),
        )
    }

    /**
     * A contradicted reading cannot be rescued by a second recognition agreeing with it.
     *
     * Two runs making the same mistake is not corroboration — and on a misread character it is the
     * *likely* outcome, since both runs read the same pixels. The label's own arithmetic is the
     * stronger evidence and it is final.
     */
    @Test
    fun `a cross-column contradiction is not overridden by distinct-run agreement`() {
        val document = FourthSessionFixtures.crackerMisreadTotal()
        // The real report, so the candidate carries the geometry of the row it was actually read
        // from. A synthetic candidate with placeholder geometry matches no row, and the check then
        // reports "cannot answer" rather than "contradicts" — which is a different verdict and
        // would make this test pass for the wrong reason.
        val report = NutritionTableInterpreter.interpret(document)
        val passA = RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, report, document)
        val secondRun = RecognitionEvidence(EvidenceSource.SELECTED_REGION_OCR, report, document)

        assertTrue(
            "precondition: the label must contradict this reading, or the test proves nothing",
            AutomaticVerification.verify(document, report).rejectionReason!!.contains("contradict"),
        )

        assertFalse(
            "a second run agreeing with a contradicted reading must not verify it",
            AutomaticVerification.verify(listOf(passA, secondRun)).mayAdvanceAutomatically,
        )
    }

    /**
     * A table that cannot answer falls through to the optical route rather than refusing outright.
     *
     * The distinction matters: `NotEnoughEvidence` means "this label prints one value column", which
     * is most labels. Treating it as a contradiction would refuse almost every correct scan.
     */
    @Test
    fun `a table with too little evidence falls through to distinct-run agreement`() {
        val document = FourthSessionFixtures.drinkCleanAutomatic()
        val report = NutritionTableInterpreter.interpret(document)

        assertTrue(
            "precondition: this label must be unable to corroborate itself",
            AutomaticVerification.verify(document, report).rejectionReason!!.contains("coherent row"),
        )

        val verdict = AutomaticVerification.verify(
            listOf(
                RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, report, document),
                RecognitionEvidence(EvidenceSource.SELECTED_REGION_OCR, report, document),
            ),
        )

        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
    }

    // ================================================================ the gate itself

    @Test
    fun `an unverified reading never clears the automatic gate`() {
        // A real document rather than a synthetic candidate, because the gate now also asks
        // [ReadingEligibility] about the reading's decimal scale, and that question is only
        // answerable against the document the value was read from. The cracker's `72,0` carries its
        // own separator, so its scale is `Established` and this test isolates the *verification*
        // half of the gate — which is what it is about.
        val document = FourthSessionFixtures.crackerCorrectFirst()
        val report = NutritionTableInterpreter.interpret(document)
        val resolved = EvidenceResolver.Outcome.Resolved(
            reading = report.reading,
            report = report,
            agreeingSources = listOf(EvidenceSource.FULL_FRAME_PASS_A),
        )

        assertTrue(
            "the structural half must still pass, or this test proves nothing",
            AutomaticScanAdvance.mayAdvance(resolved),
        )
        assertTrue(
            "precondition: the scale must be established, or this measures the wrong refusal",
            AutomaticScanAdvance.scaleVerdict(resolved, document) is ScaleAmbiguity.Verdict.Established,
        )
        assertFalse(
            AutomaticScanAdvance.mayAdvanceVerified(
                resolved,
                AutomaticVerification.Verdict(AutomaticVerification.Route.NONE),
                document,
            ),
        )
        assertTrue(
            AutomaticScanAdvance.mayAdvanceVerified(
                resolved,
                AutomaticVerification.Verdict(AutomaticVerification.Route.CROSS_COLUMN),
                document,
            ),
        )
    }
}
