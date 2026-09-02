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

    private fun confidentEvidence(
        source: EvidenceSource,
        document: OcrDocument,
    ): RecognitionEvidence = RecognitionEvidence(
        source = source,
        report = report(document),
        document = document,
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
     * A verified integer reading is untouched.
     *
     * `20260902-131357-353` reads `41g` - integer-like, no decimal separator, exactly the shape the
     * ambiguity rule keys on - and it is **correct**, verified by three distinct recognition runs.
     * The rule must not touch it, or the fix costs a good capture. This is why the ambiguity
     * question is asked only of *unverified* readings.
     */
    @Test
    fun `a verified reading is never made ambiguous`() {
        val document = SixthSessionFixtures.sauceSharedScaleCollapse()
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, document),
        )
        // Two distinct runs agreeing is DISTINCT_OCR_AGREEMENT, which resolves the scale question.
        val verdict = AutomaticVerification.verify(evidence)
        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
        assertTrue(
            "a verified reading must still be confirmable",
            AutomaticScanAdvance.mayConfirm(EvidenceResolver.resolve(evidence), verdict, document),
        )
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
