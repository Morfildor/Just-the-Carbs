package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The eighth session's safety guarantees.
 *
 * Every case here fails on the code as shipped at `c57aee0` and passes after the change — the
 * negative controls are recorded in the pass notes rather than left as an assertion nobody re-ran.
 *
 * The subject is **what the app does with a reading**, never whether the parser produces it:
 * [EighthSessionOutcomeTest] pins that the parser still reads `12` confidently, and it must keep
 * doing so. A parser that refused `12 g / 100 g` would refuse a legitimate label.
 */
class EighthSessionRegressionTest {

    private fun candidateOf(document: OcrDocument): CarbCandidate =
        (NutritionTableInterpreter.interpret(document).reading as LabelReading.Confident).candidate

    private fun resolvedOutcome(document: OcrDocument): EvidenceResolver.Outcome {
        val report = NutritionTableInterpreter.interpret(document)
        // One source, deliberately: this models the device's own situation on `213005-691`, where
        // only Pass A produced a reading and Strategy B returned none.
        return EvidenceResolver.Outcome.Resolved(
            reading = report.reading,
            report = report,
            agreeingSources = listOf(EvidenceSource.FULL_FRAME_PASS_A),
        )
    }

    /** Nothing outside the single recognition run supported the reading. */
    private val unverified = AutomaticVerification.Verdict(
        route = AutomaticVerification.Route.NONE,
        rejectionReason = "only one recognition run (PASS_A)",
    )

    /** A genuinely distinct recognition run read the same amount and basis. */
    private val verifiedByDistinctRun =
        AutomaticVerification.Verdict(route = AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT)

    // ------------------------------------------------------------------ P0-B: the hole

    /**
     * Requirement 1. The capture that produced the wrong proposal on the device.
     *
     * Fails at `c57aee0` with `mayConfirm == true`, which is what put `12 g / 100 g` on an ordinary
     * confirmation card over a live camera preview.
     */
    @Test
    fun `the 12 reading is never offered as an ordinary one-tap confirmation`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val outcome = resolvedOutcome(document)
        assertFalse(
            "an unverified separatorless reading must not reach a confirmation card",
            AutomaticScanAdvance.mayConfirm(outcome, unverified, document),
        )
    }

    @Test
    fun `the 12 reading is never advanced automatically`() {
        val outcome = resolvedOutcome(EighthSessionFixtures.redLabelTwelve())
        assertFalse(
            "an unverified reading must never skip confirmation entirely",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, unverified, EighthSessionFixtures.redLabelTwelve()),
        )
    }

    @Test
    fun `the absence of a paired value is reported as unsupported, not as established`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val verdict = ScaleAmbiguity.check(document, candidateOf(document))
        assertTrue("got $verdict", verdict is ScaleAmbiguity.Verdict.Unsupported)
    }

    /**
     * Requirement 10 — the rule must not become "reject every integer".
     *
     * ## The fixture changed, and why (tenth pass)
     *
     * This case used to drive [EighthSessionFixtures.redLabelTwelve] — the red label whose printed
     * `7,2 g` was recognised as `12` — and assert that a distinct run agreeing made it confirmable
     * *and* advanceable. That asserted the safety of the exact reading the session was convened to
     * keep away from the user, on a corroboration route later measured to be scale-invariant. The
     * red label now has its own controls (`the 12 reading is never …` above, and the tenth pass's
     * recovery guard); it must not simultaneously be the fixture proving integers are fine.
     *
     * The intent is retained on a **genuinely safe** integer: a single-column drink printing a whole
     * number with no sibling to rescale against. Its scale verdict is `Unsupported` — the same shape
     * the red label produces — so corroboration is still what admits it, and the "reject every
     * integer" regression this case exists to catch is still caught.
     */
    @Test
    fun `a separatorless reading that a distinct run agrees with is still usable`() {
        val document = singleColumnIntegerDrink()
        val outcome = resolvedOutcome(document)
        assertTrue(
            "precondition: the scale must be unsupported, or this measures a different rule",
            ScaleAmbiguity.check(document, candidateOf(document)) is ScaleAmbiguity.Verdict.Unsupported,
        )
        assertTrue(
            "independent verification must still be sufficient to SHOW it",
            AutomaticScanAdvance.mayConfirm(outcome, verifiedByDistinctRun, document),
        )
        // ## And no longer sufficient to skip the confirmation (thirteenth pass)
        //
        // This line asserted `mayAdvanceVerified` until the thirteenth session measured
        // `20260904-081421-421` advancing automatically with `scale evidence: UNSUPPORTED` in its
        // own bundle. Corroboration is scale-invariant — both routes report identical verdicts on a
        // label and its ×10 twin — so it is strong evidence against a single misread digit and no
        // evidence at all that `41` is not a collapsed `4,1`.
        //
        // Requirement 10 is unchanged and still enforced by the assertion above: the rule has **not**
        // become "reject every integer". The figure is shown immediately, on the frozen photograph,
        // one tap from the calculator. What it may no longer do is bypass that tap.
        assertFalse(
            "a scale-invariant route may not authorise skipping the confirmation",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, verifiedByDistinctRun, document),
        )
    }

    /**
     * A single-column drink table stating a whole number.
     *
     * No second value column, so nothing on the carbohydrate row can share a scale with the
     * candidate — which is what makes its verdict `Unsupported` rather than `Ambiguous`, and what
     * makes it the honest stand-in for a legitimate integer label.
     */
    private fun singleColumnIntegerDrink() = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
        ),
    )

    // ------------------------------------------------------------------ P0-B: the controls

    /** Requirement 7. The correct drink capture must keep working, unverified as it is. */
    @Test
    fun `the correct drink reading is still offered for confirmation on its own separator`() {
        val document = EighthSessionFixtures.drinkConfirmed()
        val outcome = resolvedOutcome(document)
        val candidate = candidateOf(document)
        assertEquals(BigDecimal("0.5"), candidate.value)
        assertEquals(NutritionBasis.PER_100_ML, candidate.basis)
        assertTrue(
            "a token carrying its own decimal separator states its scale and must still confirm",
            AutomaticScanAdvance.mayConfirm(outcome, unverified, document),
        )
    }

    /** Requirement 8. The cracker must not be collateral damage. */
    @Test
    fun `the cracker reading is still offered on its own separator, unverified`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        val outcome = resolvedOutcome(document)
        assertEquals(BigDecimal("72.0"), candidateOf(document).value)
        assertTrue(
            "72,0 carries its separator and must remain usable",
            AutomaticScanAdvance.mayConfirm(outcome, unverified, document),
        )
    }

    @Test
    fun `the cracker still advances automatically once verified`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        val outcome = resolvedOutcome(document)
        assertTrue(AutomaticScanAdvance.mayAdvanceVerified(outcome, verifiedByDistinctRun, document))
    }

    // ------------------------------------------------------------------ what may never be offered

    /**
     * Requirements 2 and 4. The child nutrients and damaged tokens the red label prints.
     *
     * `61`/`610`/`619` are the sugars `6,1 g`; `0.8`/`0.89` the fibre; `18`/`180` the protein;
     * `0,25` the salt; `724` and `2` are damaged forms of the total itself. Several are
     * integer-shaped and every one sits on a row of its own.
     *
     * **`12` is deliberately not in this set, and the reason is measured rather than assumed.**
     * Recovery is the screen where the user taps a number they are looking at on the frozen
     * photograph, so it suppresses only values something *contradicts* — a cross-run dispute, or a
     * demonstrated `Ambiguous` scale. Extending it to [ScaleAmbiguity.Verdict.Unsupported] was
     * measured across every committed session fixture and would delete the Korean sauce's
     * `6 g / 18 g serving` (third, fourth **and** fifth sessions), which is a control that must keep
     * working. What requirement 2 protects against is the app *proposing* `12` — that is
     * `the 12 reading is never offered as an ordinary one-tap confirmation` above, plus the
     * lifecycle test that keeps any unverified proposal on its own photograph.
     */
    @Test
    fun `no red-label capture offers a child nutrient or a damaged token as the total`() {
        val forbidden = setOf("724", "2", "61", "610", "619", "6.1", "0.8", "0.89", "1.8", "18", "180", "0.25")
        listOf(
            "212952-487" to EighthSessionFixtures.redLabelFusedValue(),
            "213005-691" to EighthSessionFixtures.redLabelTwelve(),
            "213014-298" to EighthSessionFixtures.redLabelSevenTwoFour(),
            "213026-546" to EighthSessionFixtures.redLabelFusedValueSecond(),
        ).forEach { (name, document) ->
            val offered = RecoveryCandidates.of(document, DisputedCandidates.NONE)
            offered.forEach { candidate ->
                val shown = candidate.reading.amount.stripTrailingZeros().toPlainString()
                assertFalse(
                    "$name offered '$shown' (raw '${candidate.rawText}') as a carbohydrate value",
                    shown in forbidden,
                )
            }
        }
    }

    /**
     * The measurement behind the exclusion of `12` from the set above, kept as a test so the
     * trade-off cannot be silently reversed.
     *
     * Suppressing `Unsupported` in recovery would empty the recovery screen on every linear US panel
     * — the Korean sauce prints its total as a bare `6` with no second column to pair against.
     */
    @Test
    fun `the Korean sauce's bare 6 stays selectable, which is why recovery keeps Unsupported values`() {
        val offered = RecoveryCandidates.of(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            DisputedCandidates.NONE,
        )
        assertTrue(
            "the linear panel's own total must remain tappable, got $offered",
            offered.any { it.reading.amount.compareTo(BigDecimal("6")) == 0 },
        )
    }

    /** Requirement 3. Nothing may invent the printed value the recognizer never read. */
    @Test
    fun `no red-label capture ever produces 7 point 2`() {
        listOf(
            EighthSessionFixtures.redLabelFusedValue(),
            EighthSessionFixtures.redLabelTwelve(),
            EighthSessionFixtures.redLabelSevenTwoFour(),
            EighthSessionFixtures.redLabelFusedValueSecond(),
        ).forEach { document ->
            val reading = NutritionTableInterpreter.interpret(document).reading
            if (reading is LabelReading.Confident) {
                assertFalse(
                    "the parser manufactured 7.2, which no recognition contains",
                    reading.candidate.value.compareTo(BigDecimal("7.2")) == 0,
                )
            }
            RecoveryCandidates.of(document, DisputedCandidates.NONE).forEach {
                assertFalse(
                    "recovery manufactured 7.2, which no recognition contains",
                    it.reading.amount.compareTo(BigDecimal("7.2")) == 0,
                )
            }
        }
    }

    // ------------------------------------------------------------------ recovery is unaffected

    /**
     * Requirement 11's other half, and the reason [RecoveryCandidates] keeps checking only
     * `Ambiguous`: the tapped-number screen must not be emptied by the new verdict.
     */
    @Test
    fun `recovery still offers the numbers a user can point at`() {
        val cracker = RecoveryCandidates.of(
            EighthSessionFixtures.crackerAutoAdvance(),
            DisputedCandidates.NONE,
        )
        assertTrue(
            "the cracker's own figures must remain selectable, got $cracker",
            cracker.any { it.reading.amount.compareTo(BigDecimal("72.0")) == 0 },
        )

        val drink = RecoveryCandidates.of(
            EighthSessionFixtures.drinkConfirmed(),
            DisputedCandidates.NONE,
        )
        assertTrue(
            "the drink's 0.5 must remain selectable, got $drink",
            drink.any { it.reading.amount.compareTo(BigDecimal("0.5")) == 0 },
        )
    }

    /** Requirement 5. The conflicted capture must stay refused on both sides of the dispute. */
    @Test
    fun `neither side of the drink dispute leaks through recovery`() {
        val document = EighthSessionFixtures.drinkConflicted()
        // The device's own dispute, built the way the app builds it: Pass A read `0.5g`, the
        // selected-region run read `5g`. Derived through `DisputedCandidates.of` rather than
        // hand-constructed, so the test exercises the real cross-run detection.
        val selected = OcrDocument(
            width = document.width,
            height = document.height,
            elements = document.elements.map {
                if (it.text == "0.5g") it.copy(text = "5g") else it
            },
        )
        fun evidenceOf(source: EvidenceSource, doc: OcrDocument) = RecognitionEvidence(
            source = source,
            report = NutritionTableInterpreter.interpret(doc),
            document = doc,
        )
        val disputed = DisputedCandidates.of(
            listOf(
                evidenceOf(EvidenceSource.FULL_FRAME_PASS_A, document),
                evidenceOf(EvidenceSource.FILTERED_PASS_A, document),
                evidenceOf(EvidenceSource.SELECTED_REGION_OCR, selected),
            ),
        )
        assertFalse("the fixture must actually produce a dispute", disputed.isEmpty)
        RecoveryCandidates.of(document, disputed).forEach {
            val amount = it.reading.amount
            assertFalse(
                "a disputed value was offered: $amount",
                amount.compareTo(BigDecimal("0.5")) == 0 || amount.compareTo(BigDecimal("5")) == 0,
            )
        }
    }

    /** Requirement 6. Unit accompaniment still removes the drink's damaged tokens. */
    @Test
    fun `the recovery drink capture still suppresses its separatorless tokens`() {
        val offered = RecoveryCandidates.of(
            EighthSessionFixtures.drinkRecovery(),
            DisputedCandidates.NONE,
        )
        offered.forEach {
            val raw = it.rawText
            assertFalse("'$raw' should have been declined for stating no unit", raw == "0.59" || raw == "1.3")
        }
    }
}
