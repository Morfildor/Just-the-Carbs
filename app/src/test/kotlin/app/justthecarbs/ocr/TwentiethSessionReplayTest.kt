package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Recorded-evidence replay of `docs/Scan evidence 2/` (twentieth session, 2026-09-06) against the
 * task's new three-outcome policy: automatic completion, explicit visual confirmation
 * ([ScanPresentationDecision.Action.CONFIRM_UNVERIFIED]), and targeted correction.
 *
 * ## What this proves, and what it does not
 *
 * Every evidence item here reproduces exactly what the device's own `selection.txt` recorded for
 * that bundle — the same passes, the same reported values, the same verification route. This proves
 * that GIVEN what ML Kit actually returned on that capture, the current decision logic (with this
 * session's `ConfirmationEligibility`/`ScanPresentationDecision` changes) routes it correctly. It
 * does NOT prove ML Kit will read better text from a fresh photograph — that needs a physical device,
 * which this pass does not have. See CLAUDE.md's standing distinction between recorded-text replay
 * (decision correctness) and real-image/device recognition (optical accuracy).
 *
 * Where a bundle's own Pass-A `OcrDocument` was recovered from its `diagnostics.txt` (via
 * `tools/derive-session-fixtures.py`, in [TwentiethSessionFixtures]), it is used directly and
 * `NutritionTableInterpreter.interpret` is the real production parser — nothing about the row/column
 * classification is asserted by hand. Where a pass produced no document (a live frame, or a
 * Strategy-B/targeted-reread pass whose own crop-local document was not captured in the bundle), the
 * evidence is constructed directly from the bundle's own recorded value/basis, which is what every
 * downstream decision object actually consumes.
 */
class TwentiethSessionReplayTest {

    private fun passAEvidence(document: OcrDocument, stillId: PhysicalObservationId) = listOf(
        RecognitionEvidence(
            source = EvidenceSource.FULL_FRAME_PASS_A,
            report = NutritionTableInterpreter.interpret(document),
            document = document,
            physicalObservation = stillId,
        ),
        RecognitionEvidence(
            source = EvidenceSource.FILTERED_PASS_A,
            report = NutritionTableInterpreter.interpret(document),
            document = document,
            physicalObservation = stillId,
        ),
    )

    private fun confidentEvidence(
        source: EvidenceSource,
        value: String,
        basis: NutritionBasis,
        physicalObservation: PhysicalObservationId,
        document: OcrDocument? = null,
        geometry: OcrBox = OcrBox(0, 0, 100, 40),
    ) = RecognitionEvidence(
        source = source,
        report = NutritionParseReport(
            reading = LabelReading.Confident(
                CarbCandidate(
                    sourceLine = "replay",
                    label = "replay",
                    value = BigDecimal(value),
                    basis = basis,
                    score = NutritionParserThresholds.CONFIDENT_SCORE,
                    geometry = geometry,
                    evidence = emptyList(),
                    column = when (basis) {
                        NutritionBasis.PER_100_G -> NutritionColumnKind.PER_100_G
                        NutritionBasis.PER_100_ML -> NutritionColumnKind.PER_100_ML
                    },
                ),
            ),
            diagnostics = emptyList(),
        ),
        document = document,
        physicalObservation = physicalObservation,
    )

    private fun decide(evidence: List<RecognitionEvidence>): ScanPresentationDecision.Action {
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val document = outcome.winningEvidence?.document
        return ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
    }

    // ------------------------------------------------------------------------------------------
    // Protein bar 40/100 g, 10/25 g -- CROSS_COLUMN-verified but scale-Ambiguous (40 paired with
    // 10). Task §6: "correct 40/100 g, 10/25 g; avoid forced typing solely because both are
    // integers." This is the case ConfirmationEligibility exists to open.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `protein bar 40 g CROSS_COLUMN-verified but scale-ambiguous reaches explicit confirmation, not blank typing`() {
        val document = TwentiethSessionFixtures.proteinBar123650()
        val stillId = PhysicalObservationId.forStill("123650-430")
        val evidence = passAEvidence(document, stillId)

        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        assertEquals(
            "precondition: must reproduce the recorded CROSS_COLUMN verification",
            AutomaticVerification.Route.CROSS_COLUMN,
            verification.route,
        )
        val document2 = outcome.winningEvidence?.document
        val scale = AutomaticScanAdvance.scaleVerdict(outcome, document2)
        assertTrue(
            "precondition: must reproduce the recorded scale ambiguity (40 paired with 10)",
            scale is ScaleAmbiguity.Verdict.Ambiguous,
        )

        val decision = ScanPresentationDecision.decide(outcome, verification, document2, automatic = true)
        assertEquals(
            "a CROSS_COLUMN-verified, structurally sound integer reading must reach EXPLICIT " +
                "confirmation rather than blank RECOVERY typing",
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
            decision,
        )

        val candidate = ScanPresentationDecision.confirmationCandidateFor(outcome, document2)
        assertNotNull("the confirmation screen must have a candidate to render", candidate)
        assertEquals(0, candidate!!.reading.amount.compareTo(BigDecimal("40")))

        // Never AUTO_ADVANCE and never a one-tap CONFIRM_ON_CAPTURE: the scale question stays open
        // and must be answered by looking at the photograph, not skipped.
        assertTrue(
            decision != ScanPresentationDecision.Action.AUTO_ADVANCE &&
                decision != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Hellmann's: printed 1.3 (misread 13); automatic acceptance remains blocked. Unsupported
    // scale, NO corroboration (135616-088's own recorded verification is NONE -- the table's
    // columns actively CONTRADICT this row, ratio 0.069 vs table 0.150). This must stay excluded.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `Hellmann's 13g contradicted by the table's own columns stays excluded from every surface`() {
        val document = TwentiethSessionFixtures.hellmanns135616()
        val stillId = PhysicalObservationId.forStill("135616-088")
        val report = NutritionTableInterpreter.interpret(document)

        assertTrue(
            "precondition: the fixture must reproduce a confident 13/PER_100_ML reading",
            (report.reading as? LabelReading.Confident)?.candidate?.value?.compareTo(BigDecimal("13")) == 0,
        )

        val evidence = listOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = report,
                document = document,
                physicalObservation = stillId,
            ),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        assertEquals(
            "precondition: the table's own columns must contradict this row exactly as recorded",
            AutomaticVerification.Route.NONE,
            verification.route,
        )

        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
        assertTrue(
            "a contradicted misread must never reach AUTO_ADVANCE, CONFIRM_ON_CAPTURE or an explicit " +
                "confirmation screen -- was $decision",
            decision == ScanPresentationDecision.Action.RECOVERY ||
                decision == ScanPresentationDecision.Action.CROP_FALLBACK ||
                decision == ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
        )

        // And the confirmation-candidate lookup must not silently find one anyway.
        val candidate = ScanPresentationDecision.confirmationCandidateFor(outcome, document)
        assertEquals(null, candidate)
    }

    /**
     * 135628-243: `SELECTED_REGION_OCR` read `13`, `TARGETED_REREAD` read `139` -- two DISTINCT
     * confident readings from within the same recognition run family that nonetheless disagree
     * with each other on value. Recorded outcome: `Conflicted` / `FOCUSED_AMOUNT_ENTRY`. Neither
     * number may reach the user as a proposal.
     */
    @Test
    fun `Hellmann's 13 vs 139 disagreement between passes stays a conflict, offers neither value`() {
        val document = TwentiethSessionFixtures.hellmanns135628()
        val stillId = PhysicalObservationId.forStill("135628-243")
        val evidence = passAEvidence(document, stillId) +
            listOf(
                confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, "13", NutritionBasis.PER_100_ML, stillId),
                confidentEvidence(EvidenceSource.TARGETED_REREAD, "139", NutritionBasis.PER_100_ML, stillId),
            )
        val outcome = EvidenceResolver.resolve(evidence)
        assertTrue(
            "precondition: SELECTED_REGION's 13 and TARGETED_REREAD's 139 must conflict",
            outcome is EvidenceResolver.Outcome.Conflicted,
        )
        val verification = AutomaticVerification.verify(evidence)
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
        assertEquals(ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY, decision)

        val candidate = ScanPresentationDecision.confirmationCandidateFor(outcome, document)
        assertEquals(null, candidate)
    }

    // ------------------------------------------------------------------------------------------
    // Lidl low-calorie item: Pass A misread 12, the reread recovered the printed 7.2 -- a genuine
    // disagreement between recognitions, correctly refused (135407-920). And the sibling capture
    // where Pass A read 72 and the reread read 12 (135418-923) -- also a genuine disagreement.
    // Neither wrong value may reach the user; both correctly conflict rather than picking a side.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `Lidl 12 vs the reread's recovered 7-point-2 conflicts rather than picking either`() {
        val document = TwentiethSessionFixtures.lidlLowCal135407()
        val stillId = PhysicalObservationId.forStill("135407-920")
        val evidence = passAEvidence(document, stillId) +
            listOf(
                confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, "7.2", NutritionBasis.PER_100_G, stillId),
                confidentEvidence(EvidenceSource.TARGETED_REREAD, "7.2", NutritionBasis.PER_100_G, stillId),
            )
        val outcome = EvidenceResolver.resolve(evidence)
        assertTrue(
            "precondition: PASS_A's 12 and SELECTED_REGION's corroborated 7.2 must conflict",
            outcome is EvidenceResolver.Outcome.Conflicted,
        )
        val verification = AutomaticVerification.verify(evidence)
        // Conflicted carries no winningEvidence (see EvidenceResolver.Outcome's own KDoc), so the
        // caller falls back to Pass A's own document -- exactly what LabelScannerScreen's
        // `evaluationDocument` does in production (`if (winner != null) winner.document else
        // captured.document`).
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
        assertEquals(
            "neither the misread 12 nor the recovered 7.2 may be offered without the user reading it " +
                "off the package themselves",
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
            decision,
        )
    }

    @Test
    fun `Lidl 72 vs the reread's 12 also conflicts rather than picking either`() {
        val document = TwentiethSessionFixtures.lidlLowCal135418()
        val stillId = PhysicalObservationId.forStill("135418-923")
        val evidence = passAEvidence(document, stillId) +
            confidentEvidence(EvidenceSource.TARGETED_REREAD, "12", NutritionBasis.PER_100_G, stillId)
        val outcome = EvidenceResolver.resolve(evidence)
        assertTrue(outcome is EvidenceResolver.Outcome.Conflicted)
        val verification = AutomaticVerification.verify(evidence)
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
        assertEquals(ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY, decision)
    }

    // ------------------------------------------------------------------------------------------
    // Korean-sauce-shaped declared serving (123544-918): no per-100 column at all, but
    // RecoveryCandidates already resolves ONE basis-complete reading from the printed serving
    // declaration. Task §5: "already resolved declared-serving readings ... should open
    // confirmation directly."
    // ------------------------------------------------------------------------------------------

    @Test
    fun `Korean-sauce-shaped declared serving opens confirmation directly, not the crop screen`() {
        val document = TwentiethSessionFixtures.koreanSauce123544()
        val report = NutritionTableInterpreter.interpret(document)
        assertEquals(
            "precondition: the main pipeline must find no per-100 reading on this linear panel",
            LabelReading.NotFound,
            report.reading,
        )
        val candidates = RecoveryCandidates.of(document)
        assertTrue(
            "precondition: RecoveryCandidates must already resolve a declared-serving reading",
            candidates.isNotEmpty(),
        )

        val stillId = PhysicalObservationId.forStill("123544-918")
        val evidence = passAEvidence(document, stillId)
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)

        assertTrue(
            "a single resolved declared-serving candidate must open explicit confirmation directly " +
                "(CONFIRM_UNVERIFIED), never CROP_FALLBACK -- was $decision",
            decision == ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Crackers: correct 72/100 g; the merged header-fragment `ger 100 g.` glued onto the
    // carbohydrate row produced a garbage `100` candidate that must never be confused with a
    // genuine `12` misread -- both are safely withheld, but for different, correctly-attributed
    // reasons (task §6: "distinguish correct integer recognition from wrong 12 or header 100").
    // ------------------------------------------------------------------------------------------

    @Test
    fun `the crackers header-fragment 100 is withheld, never confused with a genuine value`() {
        val document = TwentiethSessionFixtures.crackers123324()
        val report = NutritionTableInterpreter.interpret(document)
        val confident = report.reading as? LabelReading.Confident
        assertNotNull("precondition: the header fragment must still parse as a confident 100", confident)
        assertEquals(0, confident!!.candidate.value.compareTo(BigDecimal("100")))

        val stillId = PhysicalObservationId.forStill("123324-685")
        val evidence = passAEvidence(document, stillId)
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)

        // Never advanced and never a one-tap confirmation -- an uncorroborated, unpaired
        // separatorless value with nothing to check it against.
        assertTrue(
            decision != ScanPresentationDecision.Action.AUTO_ADVANCE &&
                decision != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Pickles: printed 5.4, corrupted unit/token on other captures of the same product. This
    // capture's SELECTED_REGION_OCR read a clean, separator-carrying 5.4 with nothing to
    // corroborate it -- CONFIRM_ON_CAPTURE is correct and unaffected by this pass's changes.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `pickle 5-point-4 with an established decimal separator reaches ordinary one-tap confirmation`() {
        // A minimal document carrying the candidate's own row, so ScaleAmbiguity can locate the
        // token and see the decimal separator the recorded bundle itself credits ("established --
        // the candidate's own token carries a decimal separator").
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("5,4g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val stillId = PhysicalObservationId.forStill("135604-916")
        val evidence = listOf(
            confidentEvidence(
                EvidenceSource.SELECTED_REGION_OCR,
                "5.4",
                NutritionBasis.PER_100_G,
                stillId,
                document,
                geometry = OcrBox(430, 200, 500, 240),
            ),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val decision = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)
        assertEquals(
            "a lone recognition with its own decimal separator is still an ordinary one-tap proposal",
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            decision,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Dairy: printed 2.0, corroborated by a genuinely distinct live frame, decimal-established --
    // correctly reaches AUTO_ADVANCE and must continue to. This is the automatic-completion
    // control this pass must not regress.
    // ------------------------------------------------------------------------------------------

    @Test
    fun `dairy 2-point-0 corroborated and scale-established still auto-advances`() {
        val document = TwentiethSessionFixtures.dairyYogurt123457()
        val stillId = PhysicalObservationId.forStill("123457-722")
        val evidence = passAEvidence(document, stillId) +
            confidentEvidence(
                EvidenceSource.LIVE_STABLE_FRAME,
                "2.0",
                NutritionBasis.PER_100_G,
                PhysicalObservationId.forLiveSnapshot(0L, "s"),
            )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val document2 = outcome.winningEvidence?.document
        val decision = ScanPresentationDecision.decide(outcome, verification, document2, automatic = true)
        assertEquals(ScanPresentationDecision.Action.AUTO_ADVANCE, decision)
    }
}
