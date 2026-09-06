package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two P0 guards from the sixth phone session, stated as properties of the shipped rules.
 *
 * Both captures displayed **`89 g / 100 ml`** for a package printing `8,9 g / 100 ml`. The two
 * reached the screen by different doors and each needs its own guard:
 *
 * | capture  | resolver     | why it reached the user            | guard |
 * |----------|--------------|------------------------------------|-------|
 * | `131511` | `Conflicted` | recovery re-offered the losing value | [DisputedCandidates] |
 * | `131545` | `Resolved`   | one unverified run got a confirmation | [ScaleAmbiguity] |
 *
 * Neither guard corrects anything. `89` never becomes `8.9` anywhere in this pass - the decimal
 * point is what OCR is least reliable about, so repositioning it guesses at exactly the wrong
 * thing, and unlike a refusal a wrong repair is invisible to the person reading the result.
 */
class SixthSessionRegressionTest {

    private fun report(document: OcrDocument): NutritionParseReport =
        NutritionTableInterpreter.interpret(document)

    /**
     * Evidence whose originating photograph is stated by the caller.
     *
     * Several cases here assert that a scale rule holds **even when the reading is corroborated**, so
     * their preconditions need corroboration that actually exists. Since 2026-09-04 that means two
     * distinct [PhysicalObservationId]s — two views of one JPEG no longer corroborate anything — so
     * those cases pass separate ids, and the ones modelling a single capture share one.
     */
    private fun confidentEvidence(
        source: EvidenceSource,
        document: OcrDocument,
        observation: PhysicalObservationId = PhysicalObservationId("ONE_CAPTURE"),
    ): RecognitionEvidence = RecognitionEvidence(
        source = source,
        report = report(document),
        document = document,
        physicalObservation = observation,
    )

    /** Two genuinely separate photographs of one label, for the "even when corroborated" cases. */
    private fun independentEvidence(document: OcrDocument) = listOf(
        confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document, PhysicalObservationId("FRAME_A")),
        confidentEvidence(
            EvidenceSource.SECOND_OBSERVATION_PASS, document, PhysicalObservationId("FRAME_B"),
        ),
    )

    /**
     * The `131511` evidence set, as the bundle recorded it: two views of Pass A reading `89`, and a
     * genuinely distinct selected-region run reading `8`.
     *
     * The `8` document is the same recognition with the carbohydrate cell replaced by what the
     * second run actually returned. Only that one token differs, which is what makes this a
     * *disagreement about one candidate* rather than two unrelated documents.
     */
    private fun conflictedEvidence(): List<RecognitionEvidence> {
        val passA = SixthSessionFixtures.sauceConflictedRuns()
        val selected = OcrDocument(
            width = passA.width,
            height = passA.height,
            elements = passA.elements.map {
                if (it.text == "89g,") it.copy(text = "8g,") else it
            },
        )
        return listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, passA),
            confidentEvidence(EvidenceSource.FILTERED_PASS_A, passA),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, selected),
        )
    }

    @Test fun `an agreeing crop must not erase a dispute between the full and filtered readings`() {
        val full = confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, separatedDrink("8,9 g"))
        val filtered = confidentEvidence(EvidenceSource.FILTERED_PASS_A, separatedDrink("6,2 g"))
        val crop = confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, separatedDrink("6,2 g"))
        for (items in listOf(listOf(full, filtered, crop), listOf(crop, filtered, full))) {
            val disputes = DisputedCandidates.of(items)
            assertTrue(disputes.disputes(BigDecimal("8.9"), NutritionBasis.PER_100_ML))
            assertTrue(disputes.disputes(BigDecimal("6.2"), NutritionBasis.PER_100_ML))
            assertTrue(RecoveryCandidates.of(separatedDrink("8,9 g"), disputes).none {
                it.reading.amount.compareTo(BigDecimal("8.9")) == 0
            })
        }
    }

    @Test fun `same-run disagreement alone remains outside the cross-run dispute rule`() {
        val disputes = DisputedCandidates.of(listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, separatedDrink("8,9 g")),
            confidentEvidence(EvidenceSource.FILTERED_PASS_A, separatedDrink("6,2 g")),
        ))
        assertTrue(disputes.isEmpty)
    }

    // ---------------------------------------------------------------- 131511: the conflict

    @Test
    fun `the conflicted capture still resolves as a conflict`() {
        val outcome = EvidenceResolver.resolve(conflictedEvidence())
        assertTrue("expected Conflicted, was $outcome", outcome is EvidenceResolver.Outcome.Conflicted)
    }

    @Test
    fun `a candidate another recognition run disputes is not offered by recovery`() {
        val evidence = conflictedEvidence()
        val disputed = DisputedCandidates.of(evidence)
        val document = SixthSessionFixtures.sauceConflictedRuns()

        val offered = RecoveryCandidates.of(document, disputed)
        val amounts = offered.map { it.reading.amount.stripTrailingZeros().toPlainString() }

        assertFalse("89 must not be offered: another run read 8 there", amounts.contains("89"))
        assertFalse("8 must not be offered either: it is the other side of the same dispute", amounts.contains("8"))
    }

    /**
     * The dispute suppresses **on its own**, with no help from the scale rule.
     *
     * On the real `131511` document both guards independently remove `89`, so a test using that
     * document passes even with cross-run propagation disabled — the negative control proved
     * exactly that, and it means such a test pins nothing about the dispute.
     *
     * This fixture keeps the same disagreement and removes the *other* reason: both cells carry a
     * surviving decimal separator, so [ScaleAmbiguity] reports `Established` and the only thing that
     * can withhold `8.9` is the fact that a second recognition run read it differently.
     */
    @Test
    fun `the dispute alone suppresses a candidate whose scale is established`() {
        val passA = separatedDrink("8,9 g")
        val selected = separatedDrink("6,2 g")
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, passA),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, selected),
        )

        // Precondition: without a dispute this document offers 8.9, and its scale is established.
        val undisputed = RecoveryCandidates.of(passA, DisputedCandidates.NONE)
            .map { it.reading.amount.stripTrailingZeros().toPlainString() }
        assertTrue("precondition: 8.9 must be offered when nothing disputes it", undisputed.contains("8.9"))

        val disputed = DisputedCandidates.of(evidence)
        assertTrue("precondition: the runs must actually disagree", !disputed.isEmpty)

        val offered = RecoveryCandidates.of(passA, disputed)
            .map { it.reading.amount.stripTrailingZeros().toPlainString() }
        assertFalse("a disputed value must not be offered, even with a clean decimal", offered.contains("8.9"))
    }

    /** A two-column drink table whose values keep their separators. */
    private fun separatedDrink(carbCell: String) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
            OcrElement("per portie", OcrBox(700, 100, 900, 140), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement(carbCell, OcrBox(430, 200, 560, 240), 0, 1),
            OcrElement("1,3 g", OcrBox(730, 200, 830, 240), 0, 1),
        ),
    )

    @Test
    fun `the dispute names which run contradicted which candidate`() {
        val disputed = DisputedCandidates.of(conflictedEvidence())

        assertTrue("expected the dispute to cover 89", disputed.disputes(BigDecimal("89"), NutritionBasis.PER_100_ML))
        assertTrue("expected the dispute to cover 8", disputed.disputes(BigDecimal("8"), NutritionBasis.PER_100_ML))

        val described = disputed.describe()
        assertTrue("expected the runs to be named, was: $described", described.contains("PASS_A"))
        assertTrue("expected the runs to be named, was: $described", described.contains("SELECTED_REGION"))
    }

    /**
     * A conflict removes the disputed values and nothing else.
     *
     * Without this the guard could pass by emptying the recovery list, which would replace a wrong
     * answer with a dead end on every conflicted capture.
     */
    @Test
    fun `an undisputed value on a conflicted label is still offered`() {
        val document = SixthSessionFixtures.sauceConflictedRuns()
        val everything = RecoveryCandidates.of(document, DisputedCandidates.NONE)
        val disputedOnly = RecoveryCandidates.of(document, DisputedCandidates.of(conflictedEvidence()))

        assertEquals(
            "exactly the disputed candidates should have been removed",
            everything.count { it.reading.amount.compareTo(BigDecimal("89")) != 0 },
            disputedOnly.size,
        )
    }

    // ---------------------------------------------------------------- 131545: the shared scale

    @Test
    fun `the shared-scale capture is not verified by any route`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            confidentEvidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val verdict = AutomaticVerification.verify(evidence)
        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
    }

    /**
     * The ratio agrees at both scales, which is the whole reason a ratio cannot settle this.
     *
     * `13/89 = 0.146` and `1.3/8.9 = 0.146`; the declared serving is `15 ml` of `100 ml`, i.e.
     * `0.15`. Multiplying both columns by ten leaves every ratio untouched.
     */
    @Test
    fun `the collapsed pair and the printed pair have the same column ratio`() {
        val collapsed = BigDecimal("13").toDouble() / BigDecimal("89").toDouble()
        val printed = BigDecimal("1.3").toDouble() / BigDecimal("8.9").toDouble()
        assertEquals(printed, collapsed, 1e-9)
    }

    @Test
    fun `an unverified integer pair sharing a scale is reported as scale-ambiguous`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val candidate = (report(document).reading as LabelReading.Confident).candidate

        val verdict = ScaleAmbiguity.check(document, candidate)
        assertTrue(
            "expected Ambiguous, was $verdict",
            verdict is ScaleAmbiguity.Verdict.Ambiguous,
        )
    }

    @Test
    fun `a scale-ambiguous reading is not proposed for confirmation`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            confidentEvidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val outcome = EvidenceResolver.resolve(evidence)

        assertFalse(
            "an unverified shared-scale reading must not reach a normal confirmation",
            AutomaticScanAdvance.mayConfirm(
                outcome,
                AutomaticVerification.verify(evidence),
                document,
            ),
        )
    }

    @Test
    fun `the scale-ambiguous value is never offered by recovery either`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val offered = RecoveryCandidates.of(document, DisputedCandidates.NONE)
        val amounts = offered.map { it.reading.amount.stripTrailingZeros().toPlainString() }

        assertFalse("89 is scale-ambiguous and must not be offered", amounts.contains("89"))
    }

    @Test
    fun `no divided value is ever manufactured`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val offered = RecoveryCandidates.of(document, DisputedCandidates.NONE)
        val amounts = offered.map { it.reading.amount.stripTrailingZeros().toPlainString() }

        listOf("8.9", "0.89", "1.3", "0.13").forEach {
            assertFalse("the app must never invent $it", amounts.contains(it))
        }
    }

    /**
     * The basis survives into focused entry, so the user types only the number.
     *
     * The label stated `per 100 ml` and the classifier read it. That fact is not in doubt and must
     * not be thrown away because the *digits* were.
     */
    @Test
    fun `the stated basis is preserved for focused entry`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val basis = StatedBasis.of(document)
        assertNotNull("the label states its basis; focused entry must keep it", basis)
        assertEquals(NutritionBasis.PER_100_ML, basis)
    }

    // ---------------------------------------------------------------- the rule stays general

    /**
     * A verified integer reading is untouched — on a label where the integer is *lone*.
     *
     * ## The fixture changed, and the assertion was inverted (tenth pass)
     *
     * This case used to drive [SixthSessionFixtures.sauceSharedScaleCollapse] — the truffle document
     * whose printed `8,9`/`1,3` was recognised as `89`/`13` — and assert that two distinct runs
     * agreeing made **that** reading confirmable. It therefore asserted the safety of the exact
     * value this whole class exists to keep off the screen, on the reasoning that agreement
     * "resolves the scale question".
     *
     * It does not. Both corroboration routes are scale-invariant, which `ScaleInvarianceTest`
     * measures rather than argues: a uniform separator loss leaves every cross-column ratio
     * unchanged, and two runs of one recognizer over the same pixels repeat it. So the truffle is
     * now refused **however** it was verified, and that is asserted below.
     *
     * The original intent — a verified integer must not become collateral damage — is retained on
     * `20260902-131357-353`'s actual shape: `41g`, integer-like, **lone**, with no sibling on its row
     * to share a rescale with. That verdict is `Unsupported`, not `Ambiguous`, so corroboration still
     * admits it and the good capture still behaves as it did.
     */
    @Test
    fun `a verified lone integer is never made ambiguous`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val evidence = independentEvidence(document)
        val verdict = AutomaticVerification.verify(evidence)
        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
        assertTrue(
            "precondition: a lone integer is Unsupported, never Ambiguous",
            ScaleAmbiguity.check(document, confidentCandidate(document))
                is ScaleAmbiguity.Verdict.Unsupported,
        )
        assertTrue(
            "a verified lone integer must still be confirmable",
            AutomaticScanAdvance.mayConfirm(EvidenceResolver.resolve(evidence), verdict, document),
        )
    }

    /**
     * `41g` is withheld from one-tap confirmation when only *views of one photograph* agree — reached
     * one step later, through focused entry, with the photograph and basis preserved.
     *
     * The companion to the case above, added 2026-09-04 and **reversed 2026-09-05**. Same-frame
     * agreement cannot see a missing decimal separator any more than a single run can, because both
     * inherit the same pixels — the arithmetic [ScaleInvarianceTest] measures. This class's own
     * introduction records that the Hellmann's `13` case (`docs/Scan Evidence new structure/
     * 20260904-113950-065`) reaches the user through exactly this door: same-photograph agreement
     * across recognition runs, `route = NONE`. The 2026-09-05 evidence-reliability plan states the
     * rule directly: "Unsupported decimal scale from a single physical observation must never
     * prefill a value for one-tap acceptance."
     *
     * `41` is not lost — [AutomaticScanAdvance.presentation] still routes an ineligible confident
     * reading with an established basis to `Presentation.Recover`, which keeps the photograph and
     * opens focused entry rather than confirming a digit that same-frame agreement cannot vouch for.
     */
    @Test
    fun `a lone integer agreed only by views of one photograph is withheld from confirmation`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val oneFrame = PhysicalObservationId("FRAME_A")
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document, oneFrame),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, document, oneFrame),
        )
        val verdict = AutomaticVerification.verify(evidence)

        assertEquals(
            "one photograph cannot verify itself for advancement",
            AutomaticVerification.Route.NONE,
            verdict.route,
        )
        assertFalse(
            "same-photograph agreement alone must not make an Unsupported-scale value confirmable",
            AutomaticScanAdvance.mayConfirm(EvidenceResolver.resolve(evidence), verdict, document),
        )
    }

    /**
     * **The correction itself.** The truffle's separatorless *pair* is refused even when verified.
     *
     * This is the assertion that replaces the old endorsement above, on the same document, so the
     * scenario the original case covered is still exercised — with the answer the evidence supports.
     */
    @Test
    fun `the truffle collapse is refused even when two distinct runs agree`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        // Two separate photographs, so the corroboration the precondition needs genuinely exists —
        // and the point stands all the stronger: a separatorless *pair* is refused even by the
        // strongest corroboration the app has, because agreement is scale-invariant.
        val evidence = independentEvidence(document)
        val verdict = AutomaticVerification.verify(evidence)
        assertTrue(
            "precondition: something must corroborate it, or this proves nothing",
            verdict.mayAdvanceAutomatically,
        )
        val outcome = EvidenceResolver.resolve(evidence)
        assertFalse(
            "a separatorless pair is not rescued by scale-invariant corroboration",
            AutomaticScanAdvance.mayConfirm(outcome, verdict, document),
        )
        assertFalse(
            "and it must never advance",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, verdict, document),
        )
    }

    private fun confidentCandidate(document: OcrDocument): CarbCandidate {
        val candidate = (report(document).reading as? LabelReading.Confident)?.candidate
        assertNotNull("precondition: the fixture must parse confidently", candidate)
        return candidate!!
    }

    /**
     * A lone value with no sibling column cell is not scale-ambiguous.
     *
     * The check needs a *pair* that moves together. A single-column label states one number and
     * there is no second value whose shared scale could be in question - so those labels behave
     * exactly as they did, which is what keeps this from becoming a blanket "integers are
     * suspicious" rule.
     */
    @Test
    fun `a single-column integer reading is not scale-ambiguous`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val candidate = (report(document).reading as? LabelReading.Confident)?.candidate
        assertNotNull("precondition: the fixture must parse confidently", candidate)

        val verdict = ScaleAmbiguity.check(document, candidate!!)
        assertFalse(
            "a lone value has no shared scale to be ambiguous about, was $verdict",
            verdict is ScaleAmbiguity.Verdict.Ambiguous,
        )
    }

    /**
     * A pair carrying a surviving decimal separator is not ambiguous.
     *
     * If the recognizer kept the separator, the scale is *stated*. This is the signal that makes
     * the rule general rather than package-specific: it keys on the punctuation evidence that
     * actually survived, not on the magnitude of the number.
     */
    @Test
    fun `a pair whose decimal separator survived is not scale-ambiguous`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("per portie", OcrBox(700, 100, 900, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("8,9 g", OcrBox(430, 200, 520, 240), 0, 1),
                OcrElement("1,3 g", OcrBox(730, 200, 820, 240), 0, 1),
            ),
        )
        val candidate = (report(document).reading as? LabelReading.Confident)?.candidate
        assertNotNull("precondition: the fixture must parse confidently", candidate)

        val verdict = ScaleAmbiguity.check(document, candidate!!)
        assertFalse(
            "the separator survived, so the scale is stated, was $verdict",
            verdict is ScaleAmbiguity.Verdict.Ambiguous,
        )
    }
}
