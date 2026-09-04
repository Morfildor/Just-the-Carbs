package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The ninth session's defect, and the controls that must survive fixing it.
 *
 * ## The defect
 *
 * A **correct** reading reached the resolver, was correctly classified `NeedsVerification`, and was
 * then discarded by the scanner's automatic veto before any branch could present it. The veto asked
 * `mayAdvance`, which answers `false` for that outcome — the right answer to "may this skip the
 * confirmation" and the wrong answer to "may this be shown at all".
 *
 * Measured on three captures. Each bundle records a correct printed value under
 * `SELECTED_REGION_OCR` and `final UI action : RECOVERY` in the same file:
 *
 * | bundle | printed | Strategy B read | shown |
 * |---|---|---|---|
 * | `085019-213` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | nothing |
 * | `085032-269` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | nothing |
 * | `084951-833` | `0,5 g / 100 ml` | `Confident 0.5/PER_100_ML` | nothing |
 *
 * ## The controls
 *
 * Widening what may be *proposed* must not widen what may be *accepted without asking*, and must not
 * let the eighth session's red-label `12` through. Both are asserted below.
 */
class NinthSessionRegressionTest {

    /**
     * One capture's evidence, so every view shares its [PhysicalObservationId].
     *
     * That is the physical truth of these fixtures — Pass A and Strategy B are both recognitions of
     * the one photograph this session recorded — and since 2026-09-04 it is also what stops them
     * corroborating each other for automatic advancement.
     */
    private fun evidence(source: EvidenceSource, document: OcrDocument) = RecognitionEvidence(
        source = source,
        report = NutritionTableInterpreter.interpret(document),
        document = document,
        physicalObservation = ONE_CAPTURE,
    )

    /** Pass A twice (one recognition, two views) plus a distinct Strategy B run — the device shape. */
    private fun sessionEvidence(passA: OcrDocument, strategyB: OcrDocument) = listOf(
        evidence(EvidenceSource.FULL_FRAME_PASS_A, passA),
        evidence(EvidenceSource.FILTERED_PASS_A, passA),
        evidence(EvidenceSource.SELECTED_REGION_OCR, strategyB),
    )

    // ---------------------------------------------------------------- preconditions

    /**
     * Without this, every assertion below could pass for the wrong reason.
     *
     * The derived Strategy B documents must read as the device's Strategy B actually read, and the
     * Pass A documents must still fail as the device's Pass A actually failed.
     */
    @Test
    fun `precondition - the fixtures reproduce both halves of the device's disagreement`() {
        listOf(
            NinthSessionFixtures.whiteTableFirst(),
            NinthSessionFixtures.whiteTableSecond(),
        ).forEach {
            assertTrue(
                "pass A must still refuse, or the defect is not modelled",
                NutritionTableInterpreter.interpret(it).reading is LabelReading.NotFound,
            )
        }

        listOf(
            NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            NinthSessionStrategyBDocuments.whiteTableSecondUnitRecognised(),
        ).forEach {
            val confident = NutritionTableInterpreter.interpret(it).reading as LabelReading.Confident
            assertEquals(0, confident.candidate.value.compareTo(BigDecimal("2.8")))
            assertEquals(NutritionBasis.PER_100_G, confident.candidate.basis)
        }
    }

    // ---------------------------------------------------------------- the fix

    /**
     * The resolver's verdict was never the problem — it correctly said "please check this".
     *
     * Pinned so a future change cannot "fix" the presentation by making the resolver claim more than
     * it knows.
     */
    @Test
    fun `a lone strategy B reading resolves to NeedsVerification, not Resolved`() {
        val outcome = EvidenceResolver.resolve(
            sessionEvidence(
                NinthSessionFixtures.whiteTableFirst(),
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            ),
        )
        assertTrue(
            "expected NeedsVerification, got $outcome",
            outcome is EvidenceResolver.Outcome.NeedsVerification,
        )
    }

    @Test
    fun `the white table's correct value is presented instead of being discarded`() {
        listOf(
            NinthSessionFixtures.whiteTableFirst() to
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            NinthSessionFixtures.whiteTableSecond() to
                NinthSessionStrategyBDocuments.whiteTableSecondUnitRecognised(),
        ).forEach { (passA, strategyB) ->
            val evidence = sessionEvidence(passA, strategyB)
            val outcome = EvidenceResolver.resolve(evidence)
            val verification = AutomaticVerification.verify(evidence)

            assertTrue(
                "the automatic attempt must not fall back to the crop screen",
                AutomaticScanAdvance.mayPresentAutomatically(outcome, verification, passA),
            )
            assertEquals(
                "the value is offered for confirmation on the photograph",
                AutomaticScanAdvance.Presentation.ConfirmOnCapture,
                AutomaticScanAdvance.presentation(outcome, verification, strategyB, automatic = true),
            )
            val reading = AutomaticScanAdvance.confidentReading(outcome)
            assertNotNull(reading)
            assertEquals(0, reading!!.candidate.value.compareTo(BigDecimal("2.8")))
            assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
        }
    }

    /**
     * The scale is established by the candidate's own decimal separator, not by anything inferred.
     *
     * This is the property that separates `2,8` from the red label's `12`, and it is why confirming
     * this reading is a fair question: the digits on the card are the digits on the package.
     */
    @Test
    fun `the white table's scale is established by its own printed separator`() {
        val strategyB = NinthSessionStrategyBDocuments.whiteTableUnitRecognised()
        val candidate = (NutritionTableInterpreter.interpret(strategyB).reading as LabelReading.Confident).candidate
        val verdict = ScaleAmbiguity.check(strategyB, candidate)
        assertTrue("expected Established, got $verdict", verdict is ScaleAmbiguity.Verdict.Established)
    }

    // ---------------------------------------------------------------- controls that must not move

    /**
     * **The eighth session's control.** The red label prints `7,2 g`; Strategy B read `12`.
     *
     * Widening what may be proposed must not let this through. It is unverified, its token carries no
     * decimal separator, and its row holds nothing to pair with — so the scale is `Unsupported` and
     * no confirmation may be offered.
     */
    @Test
    fun `the red label's 12 is never offered for confirmation`() {
        val passA = NinthSessionFixtures.redLabelTwelve()
        val evidence = sessionEvidence(passA, passA)
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertFalse(
            "an unsupported-scale reading must never be confirmable",
            AutomaticScanAdvance.mayConfirm(outcome, verification, passA),
        )
        assertTrue(
            "and it must never advance",
            !AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, passA),
        )
    }

    /**
     * A `NeedsVerification` outcome may be *proposed*; it may never *advance*.
     *
     * This is the invariant that keeps the widening safe, and it holds by construction because
     * `mayAdvanceVerified` delegates to `mayAdvance`. Pinned so a future edit cannot quietly
     * generalise the wrong one of the two.
     */
    @Test
    fun `an uncorroborated reading may be proposed but never advanced`() {
        val outcome = EvidenceResolver.resolve(
            sessionEvidence(
                NinthSessionFixtures.whiteTableFirst(),
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            ),
        )
        val verification = AutomaticVerification.Verdict(
            route = AutomaticVerification.Route.NONE,
            rejectionReason = "test: nothing corroborated it",
        )
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
        assertFalse(
            AutomaticScanAdvance.mayAdvanceVerified(
                outcome,
                verification,
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            ),
        )
        assertEquals(
            AutomaticScanAdvance.Presentation.ConfirmOnCapture,
            AutomaticScanAdvance.presentation(
                outcome,
                verification,
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
                automatic = true,
            ),
        )
    }

    /**
     * The two captures that already worked must be untouched by this change.
     *
     * Each is modelled with the evidence its own bundle records, which differ:
     * the cracker skipped Strategy B entirely (`SKIPPED_CROSS_COLUMN_VERIFIED` — the label's own
     * other rows corroborated it), while the blue tub ran Strategy B and agreed with it
     * (`DISTINCT_OCR_AGREEMENT`). Giving both the same evidence would prove neither.
     */
    @Test
    fun `the cracker still advances on cross-column verification alone`() {
        val document = NinthSessionFixtures.crackerAutoAdvance()
        val evidence = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        assertEquals(
            "the bundle records CROSS_COLUMN for this capture",
            AutomaticVerification.Route.CROSS_COLUMN,
            verification.route,
        )
        assertEquals(
            AutomaticScanAdvance.Presentation.Advance,
            AutomaticScanAdvance.presentation(outcome, verification, document, automatic = true),
        )
        assertEquals(
            0,
            AutomaticScanAdvance.confidentReading(outcome)!!.candidate.value.compareTo(BigDecimal("72.0")),
        )
    }

    /**
     * The blue tub is still **proposed**, and no longer advances. Updated 2026-09-04.
     *
     * All three sources read `Confident 3.2/PER_100_G`, and this test previously asserted that the
     * third — Strategy B — was "a genuinely separate recognition run, which is what makes it
     * corroboration". That premise is false: Strategy B recognises a *crop of the same JPEG*, so it
     * inherits that photograph's focus, blur and glyph damage.
     *
     * `20260904-113653-044` is what the premise cost. A Fanta bottle printing `0,5 g / 100 ml` was
     * read `0.59` by every view of one capture, the agreement satisfied
     * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT], and the app advanced with no confirmation
     * on a figure ten times the printed one.
     *
     * **The reading is not lost** — `3.2` is still correct, still resolved and still put in front of
     * the user. The value's survival is asserted below so a future change cannot turn a demotion
     * into a disappearance.
     *
     * ## Amended 2026-09-04 (seventeenth session): it advances again, by a different route
     *
     * The optical route is still refused, and that is the property this test was written to hold —
     * it is asserted directly below rather than inferred from the final action. What changed is
     * that the **table** now answers: this is a Lidl yoghurt printing `Ø/100 g` beside `Ø/125 g`,
     * and the off-basis second column makes seven of its rows form pairs at the printed 1.25
     * serving ratio, the carbohydrate row among them.
     *
     * That is [AutomaticVerification.Route.CROSS_COLUMN] — structural evidence from the label's own
     * other nutrients, which is independent of the photograph in the way a second view of the same
     * pixels is not. So the `0.59` failure that motivated the physical-observation rule cannot
     * return through it: a tenfold misread of one cell does not also misread four other rows
     * consistently in the same direction, which is exactly what the ratio check tests for.
     */
    @Test
    fun `the blue tub is not corroborated by same-frame agreement`() {
        val document = NinthSessionFixtures.blueTubAutoAdvance()
        val evidence = sessionEvidence(document, document)
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertNotEquals(
            "views of one photograph cannot corroborate each other",
            AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT,
            verification.route,
        )
        assertEquals(
            "the label's own other rows are what vouch for it",
            AutomaticVerification.Route.CROSS_COLUMN,
            verification.route,
        )
        assertEquals(
            0,
            AutomaticScanAdvance.confidentReading(outcome)!!.candidate.value.compareTo(BigDecimal("3.2")),
        )
    }

    /**
     * A capture with nothing to read still declines to the crop screen.
     *
     * The veto's original purpose is preserved: where the parser produced no reading, the rectangle
     * really is the user's most direct lever and the crop step earns its interaction.
     */
    @Test
    fun `captures with no reading still fall back to the crop screen`() {
        listOf(
            NinthSessionFixtures.ingredientUnderside(),
            NinthSessionFixtures.smallBlueTableSparse(),
            NinthSessionFixtures.greenDrinkNoReading(),
        ).forEach { document ->
            val evidence = listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
                evidence(EvidenceSource.FILTERED_PASS_A, document),
            )
            val outcome = EvidenceResolver.resolve(evidence)
            val verification = AutomaticVerification.verify(evidence)
            assertFalse(
                "nothing was read; the crop screen is the right fallback",
                AutomaticScanAdvance.mayPresentAutomatically(outcome, verification, document),
            )
        }
    }

    /** An ambiguity is still not presentable automatically — repeating it does not resolve it. */
    @Test
    fun `an unresolved ambiguity still falls back to the crop screen`() {
        val document = NinthSessionFixtures.greenDrinkNoReading()
        val outcome = EvidenceResolver.Outcome.Unresolved(
            reading = LabelReading.Ambiguous(emptyList()),
            report = NutritionTableInterpreter.interpret(document),
            source = EvidenceSource.FULL_FRAME_PASS_A,
        )
        val verification = AutomaticVerification.Verdict(
            route = AutomaticVerification.Route.NONE,
            rejectionReason = "test",
        )
        assertFalse(AutomaticScanAdvance.mayPresentAutomatically(outcome, verification, document))
        assertEquals(
            AutomaticScanAdvance.Presentation.NotApplicable,
            AutomaticScanAdvance.presentation(outcome, verification, document, automatic = true),
        )
    }

    private companion object {
        /** Every fixture in this session came from one photograph per capture. */
        val ONE_CAPTURE = PhysicalObservationId("NINTH_SESSION_CAPTURE")
    }
}
