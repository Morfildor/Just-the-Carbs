package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The tenth session: the green drink's discarded `0.5`, and the red label's `12` leaking through a
 * tap.
 *
 * Evidence: `scan-evidence (10).zip`, SHA-256
 * `3391e49c41d387090b4638aee3010240f0a3d0fe6026aed46c0e41f54f382c43`, nine captures
 * `20260903-084935-802` .. `20260903-085128-913` from a Samsung SM-S928B.
 *
 * ## Issue 1 — why the green drink differed from the white table
 *
 * Both bundles record `final UI action : RECOVERY` over a correct Strategy B reading, but their
 * resolver verdicts differ (`Nothing` vs `NeedsVerification`) and the reason is **not** the one the
 * status text suggests. Measured against the real classifier:
 *
 * | capture | header as recognised | columns | statedBasis | pass A resolver |
 * |---|---|---|---|---|
 * | green `084951-833` | `PER: 100 m \| 25d6` — the `l` lost | **0** | null | `Nothing` |
 * | white `085019-213` | `… per 100g` | 1 (`PER_100_G`) | `PER_100_G` | `NeedsVerification` |
 *
 * The green drink's *value* was never the problem — `0.5g` carries its unit and its separator, so
 * accompaniment never declined it. Its *column* was: `m` is not a unit spelling, so nothing placed
 * the cell and no pass was confident, which is `EvidenceResolver`'s `confident.isEmpty()` branch.
 *
 * Strategy B, recognising the tighter crop, read the header cleanly and established all four facts —
 * value, unit, carbohydrate row and the `/100 ml` basis. So the outcome is deterministic and it is
 * the *presented* one: `NeedsVerification` and `CONFIRM_ON_CAPTURE`.
 *
 * ## Issue 2 — a tap is not evidence about decimal scale
 *
 * Recovery offered `12 g / 100 g` for a package printing `7,2 g`. "The user tapped it" establishes
 * which row they meant and nothing about whether the recognizer read the digits correctly. See
 * [ReadingEligibility] for the distinction that separates that from the Korean sauce's legitimate
 * `6 g / 18 g serving`, which both surfaces must keep offering.
 */
class TenthSessionRegressionTest {

    private fun evidence(source: EvidenceSource, document: OcrDocument) = RecognitionEvidence(
        source = source,
        report = NutritionTableInterpreter.interpret(document),
        document = document,
    )

    /** Pass A twice (one recognition, two views) plus a distinct Strategy B run — the device shape. */
    private fun sessionEvidence(passA: OcrDocument, strategyB: OcrDocument) = listOf(
        evidence(EvidenceSource.FULL_FRAME_PASS_A, passA),
        evidence(EvidenceSource.FILTERED_PASS_A, passA),
        evidence(EvidenceSource.SELECTED_REGION_OCR, strategyB),
    )

    private val unverified = AutomaticVerification.Verdict(
        route = AutomaticVerification.Route.NONE,
        rejectionReason = "only one recognition run (SELECTED_REGION)",
    )

    // ================================================================ issue 1: the green drink

    /**
     * The measured cause, pinned so the explanation cannot drift from the code.
     *
     * Without this, the assertions below could pass while the fixture had quietly stopped modelling
     * the failure — the trap this repo has hit with the Dutch header fixture, the soft-keyboard
     * geometry test and the fifth session's serving declaration.
     */
    @Test
    fun `precondition - the green drink resolves no column because the header lost its l`() {
        val passA = NinthSessionFixtures.greenDrinkStrategyB()
        val rows = LogicalRowBuilder.build(passA)

        assertEquals(
            "pass A must resolve no column at all; that is the whole defect",
            0,
            ColumnClassifier.classify(rows, passA.width).size,
        )
        assertTrue(
            "the header element must still be the truncated 'm'",
            passA.elements.any { it.box.left == 1208 && it.box.top == 1347 && it.text == "m" },
        )
        assertTrue(
            "the carbohydrate row's own value is clean — the value was never the problem",
            rows.any { it.text.contains("Koolhydraten") && it.text.contains("0.5g") },
        )
        assertTrue("pass A is NotFound", NutritionTableInterpreter.interpret(passA).reading is LabelReading.NotFound)
    }

    /**
     * Restoring the one lost glyph reproduces the device's Strategy B verdict exactly.
     *
     * This is what makes the derived document evidence rather than invention: it is asserted against
     * the **real** interpreter, so it cannot pass for the wrong reason.
     */
    @Test
    fun `precondition - the green drink's strategy B reads 0 point 5 per 100 ml`() {
        val strategyB = NinthSessionStrategyBDocuments.greenDrinkUnitRecognised()
        val columns = ColumnClassifier.classify(LogicalRowBuilder.build(strategyB), strategyB.width)

        assertEquals("exactly one column resolves once the unit is readable", 1, columns.size)
        assertEquals(NutritionColumnKind.PER_100_ML, columns.single().kind)

        val confident = NutritionTableInterpreter.interpret(strategyB).reading as LabelReading.Confident
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("0.5")))
        assertEquals(NutritionBasis.PER_100_ML, confident.candidate.basis)
    }

    /**
     * **Issue 1's required outcome, exactly.** One resolver verdict, one UI action, no disjunction.
     *
     * Strategy B established the value, its unit, the carbohydrate row and the `/100 ml` basis, so
     * the reading is proposed on the retained photograph rather than discarded.
     */
    @Test
    fun `the green drink resolves to NeedsVerification and is confirmed on the capture`() {
        val passA = NinthSessionFixtures.greenDrinkStrategyB()
        val strategyB = NinthSessionStrategyBDocuments.greenDrinkUnitRecognised()
        val evidence = sessionEvidence(passA, strategyB)

        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertTrue(
            "expected NeedsVerification, got ${outcome::class.simpleName}",
            outcome is EvidenceResolver.Outcome.NeedsVerification,
        )
        assertTrue(
            "the automatic attempt must not fall back to the crop screen",
            AutomaticScanAdvance.mayPresentAutomatically(outcome, verification, strategyB),
        )
        assertEquals(
            AutomaticScanAdvance.Presentation.ConfirmOnCapture,
            AutomaticScanAdvance.presentation(outcome, verification, strategyB, automatic = true),
        )

        val reading = AutomaticScanAdvance.confidentReading(outcome)
        assertNotNull(reading)
        assertEquals(0, reading!!.candidate.value.compareTo(BigDecimal("0.5")))
        assertEquals(NutritionBasis.PER_100_ML, reading.candidate.basis)
    }

    /** And it may never skip the confirmation: one run read it, nothing corroborated it. */
    @Test
    fun `the green drink is never advanced automatically`() {
        val evidence = sessionEvidence(
            NinthSessionFixtures.greenDrinkStrategyB(),
            NinthSessionStrategyBDocuments.greenDrinkUnitRecognised(),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
        assertFalse(
            AutomaticScanAdvance.mayAdvanceVerified(
                outcome,
                AutomaticVerification.verify(evidence),
                NinthSessionStrategyBDocuments.greenDrinkUnitRecognised(),
            ),
        )
    }

    /**
     * The green drink's scale is established by its own printed separator.
     *
     * This is the property that separates it from the red label, and it is why proposing it is a
     * fair question: the digits on the card are the digits on the package.
     */
    @Test
    fun `the green drink's scale is established by its own token`() {
        val strategyB = NinthSessionStrategyBDocuments.greenDrinkUnitRecognised()
        val candidate =
            (NutritionTableInterpreter.interpret(strategyB).reading as LabelReading.Confident).candidate
        assertTrue(
            "expected Established",
            ScaleAmbiguity.check(strategyB, candidate) is ScaleAmbiguity.Verdict.Established,
        )
    }

    // ================================================================ issue 2: the red label

    /**
     * **Requirement 1.** Tapping *every* element of the red capture offers neither `12` nor `72`.
     *
     * Driven across both documents that capture produced — Pass A, which read `72g` and could not
     * place it, and the Strategy B shape that read `12` under a resolved per-100 column. Neither may
     * put a figure in front of the user, by any route.
     */
    @Test
    fun `no tap anywhere on the red capture offers 12 or 72`() {
        listOf(
            "ninth pass A" to NinthSessionFixtures.redLabelTwelve(),
            "eighth strategy B shape" to EighthSessionFixtures.redLabelTwelve(),
        ).forEach { (name, document) ->
            assertTrue(
                "$name: the opening recovery list must be empty, was " +
                    RecoveryCandidates.of(document).map { it.label },
                RecoveryCandidates.of(document).isEmpty(),
            )

            document.elements.forEach { element ->
                val offered = RecoveryCandidates.onRowAt(
                    document,
                    element.box.centerY.toInt(),
                    element.box.centerX.toInt(),
                )
                offered.forEach { candidate ->
                    val shown = candidate.reading.amount.stripTrailingZeros().toPlainString()
                    assertFalse(
                        "$name: tapping '${element.text}' offered '$shown' (${candidate.label})",
                        shown == "12" || shown == "72",
                    )
                }
            }
        }
    }

    /** Nothing anywhere may manufacture the printed `7.2`, which no recognition of it contains. */
    @Test
    fun `the red capture never manufactures 7 point 2`() {
        val document = NinthSessionFixtures.redLabelTwelve()
        RecoveryCandidates.of(document).forEach {
            assertFalse(
                "recovery manufactured 7.2",
                it.reading.amount.compareTo(BigDecimal("7.2")) == 0,
            )
        }
        assertTrue(
            "no element states it either",
            document.elements.none { it.text.replace(',', '.').contains("7.2") },
        )
    }

    /**
     * **Requirement 2.** Focused entry stays reachable, with the basis preserved.
     *
     * The value is withheld; the *question* is not. Refusing the digits must not also destroy the
     * basis the label stated, or the refusal becomes the dead end the seventh session removed.
     */
    @Test
    fun `focused entry remains reachable on the red strategy B shape with its basis preserved`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
                evidence(EvidenceSource.FILTERED_PASS_A, document),
            ),
        )

        assertEquals(
            "the digits are withheld, so the user is asked for them",
            AutomaticScanAdvance.Presentation.Recover,
            AutomaticScanAdvance.presentation(outcome, unverified, document, automatic = true),
        )
        assertEquals(
            "the basis the label stated must survive the refusal",
            NutritionBasis.PER_100_G,
            StatedBasis.of(document),
        )
        assertNotNull(
            "focused entry needs a target row",
            FocusedAmountEntry.of(document),
        )
    }

    // ================================================================ the controls

    /**
     * **Requirement 3.** The Korean sauce's `6 g / 18 g serving` stays recoverable.
     *
     * This is the measurement that rules out the naive fix. Its `6` is `Unsupported` exactly as the
     * red label's `12` is — bare, separatorless, nothing on its row to pair against — so a rule
     * keyed on the scale verdict alone deletes it. What keeps it is that its basis was **declared**
     * by a serving sentence the package prints, not inferred from a column the app resolved.
     */
    @Test
    fun `the Korean sauce still offers 6 g per 18 g serving`() {
        listOf(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.koreanSauceSecondCapture(),
            FourthSessionFixtures.sauceLinearPanel(),
            FourthSessionFixtures.sauceLinearPanelSecond(),
            FifthSessionFixtures.sauceLinearPanelFirst(),
            FifthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { document ->
            val offered = RecoveryCandidates.of(document)
            assertTrue(
                "the linear panel's own total must stay tappable, got $offered",
                offered.any { it.reading.amount.compareTo(BigDecimal("6")) == 0 },
            )
        }
    }

    /** The property that admits it, stated directly so the reason cannot silently change. */
    @Test
    fun `the sauce is admitted by its declared serving, not by its scale`() {
        val document = ThirdSessionFixtures.koreanSauceLinearPanel()
        val six = RecoveryCandidates.of(document)
            .single { it.reading.amount.compareTo(BigDecimal("6")) == 0 }

        assertTrue(
            "precondition: its scale is Unsupported, exactly like the red label's 12",
            ScaleAmbiguity.check(
                document,
                CarbCandidate(
                    sourceLine = six.rowText, label = six.rowText, value = six.reading.amount,
                    basis = null, score = 0, geometry = six.box, evidence = emptyList(), column = null,
                ),
            ) is ScaleAmbiguity.Verdict.Unsupported,
        )
        assertTrue(
            "what admits it is a serving the label declared",
            six.reading.basis is CarbBasis.PerQuantity,
        )
    }

    /** The truffle's `89` and any invented `8.9` stay out, by both routes. */
    @Test
    fun `the truffle never offers 89 or 8 point 9`() {
        listOf(
            SeventhSessionFixtures.truffleSeparatorlessPair(),
            SeventhSessionFixtures.truffleDamagedUnitGlyph(),
            SixthSessionFixtures.sauceSharedScaleCollapse(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach {
                val shown = it.reading.amount.stripTrailingZeros().toPlainString()
                assertFalse("offered '$shown'", shown == "89" || shown == "8.9")
            }
        }
    }

    /** The corroborated cracker still advances; nothing here touches the verified path. */
    @Test
    fun `the cracker still advances automatically`() {
        val document = NinthSessionFixtures.crackerAutoAdvance()
        val evidence = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertEquals(AutomaticVerification.Route.CROSS_COLUMN, verification.route)
        assertEquals(
            AutomaticScanAdvance.Presentation.Advance,
            AutomaticScanAdvance.presentation(outcome, verification, document, automatic = true),
        )
        assertEquals(
            0,
            AutomaticScanAdvance.confidentReading(outcome)!!.candidate.value.compareTo(BigDecimal("72.0")),
        )
    }

    /** The white table reaches confirmation and never advancement — issue 1's sibling. */
    @Test
    fun `the white table is confirmed on the capture and never advanced`() {
        listOf(
            NinthSessionFixtures.whiteTableFirst() to
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            NinthSessionFixtures.whiteTableSecond() to
                NinthSessionStrategyBDocuments.whiteTableSecondUnitRecognised(),
        ).forEach { (passA, strategyB) ->
            val evidence = sessionEvidence(passA, strategyB)
            val outcome = EvidenceResolver.resolve(evidence)
            val verification = AutomaticVerification.verify(evidence)

            assertEquals(
                AutomaticScanAdvance.Presentation.ConfirmOnCapture,
                AutomaticScanAdvance.presentation(outcome, verification, strategyB, automatic = true),
            )
            assertFalse(
                "one run read it; it may be proposed but never accepted without asking",
                AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, strategyB),
            )
            assertEquals(
                0,
                AutomaticScanAdvance.confidentReading(outcome)!!.candidate.value
                    .compareTo(BigDecimal("2.8")),
            )
        }
    }

    // ================================================================ the centralization itself

    /**
     * **Requirement 6.** Neither surface may decide eligibility on its own.
     *
     * Asserted by behaviour rather than by inspection: for every candidate on every committed
     * fixture, what recovery offers and what [ReadingEligibility] permits must be the same set. A
     * branch that bypassed the central decision would show up here as a candidate offered while the
     * central rule refuses it.
     */
    @Test
    fun `recovery offers exactly what the central eligibility rule permits`() {
        allFixtures().forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertTrue(
                    "$name offered '${candidate.label}' which ReadingEligibility refuses: " +
                        ReadingEligibility.evaluate(document, candidate).reason,
                    ReadingEligibility.evaluate(document, candidate).isEligible,
                )
            }
        }
    }

    /**
     * And the automatic path asks the same object, so the two cannot diverge.
     *
     * For every fixture that produces a confident reading, `mayConfirm` must agree with
     * [ReadingEligibility] evaluated on the same inputs.
     */
    @Test
    fun `the automatic path's confirmation gate is the central rule`() {
        allFixtures().forEach { (name, document) ->
            val outcome = EvidenceResolver.resolve(
                listOf(
                    evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
                    evidence(EvidenceSource.FILTERED_PASS_A, document),
                ),
            )
            if (AutomaticScanAdvance.confidentReading(outcome) == null) return@forEach

            val verdict = AutomaticScanAdvance.eligibility(outcome, unverified, document)
            assertNotNull("$name: a confident reading must produce a verdict", verdict)
            assertEquals(
                "$name: mayConfirm and the central rule disagree",
                verdict!!.isEligible,
                AutomaticScanAdvance.mayConfirm(outcome, unverified, document),
            )
        }
    }

    /** Every verdict carries a sentence, so an evidence bundle can always say why. */
    @Test
    fun `every eligibility verdict states its reason`() {
        allFixtures().forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertTrue(
                    "$name: an empty reason tells a bundle nothing",
                    ReadingEligibility.evaluate(document, candidate).reason.isNotBlank(),
                )
            }
        }
    }

    private fun allFixtures(): List<Pair<String, OcrDocument>> = listOf(
        "3 koreanSauce" to ThirdSessionFixtures.koreanSauceLinearPanel(),
        "3 koreanSauce2" to ThirdSessionFixtures.koreanSauceSecondCapture(),
        "3 drinkAutomatic" to ThirdSessionFixtures.drinkAutomaticConfident(),
        "3 crackerClean" to ThirdSessionFixtures.crackerCleanUnit(),
        "3 baltic" to ThirdSessionFixtures.balticTableSeparateAnchors(),
        "4 sauceLinear" to FourthSessionFixtures.sauceLinearPanel(),
        "4 crackerCorrect" to FourthSessionFixtures.crackerCorrectFirst(),
        "4 crackerMisread" to FourthSessionFixtures.crackerMisreadTotal(),
        "5 sauceFirst" to FifthSessionFixtures.sauceLinearPanelFirst(),
        "5 crackerVerified" to FifthSessionFixtures.crackerCrossColumnVerified(),
        "5 crackerDamaged" to FifthSessionFixtures.crackerDamagedPerHundredHeader(),
        "6 sauceConflicted" to SixthSessionFixtures.sauceConflictedRuns(),
        "6 sauceSharedScale" to SixthSessionFixtures.sauceSharedScaleCollapse(),
        "7 trufflePair" to SeventhSessionFixtures.truffleSeparatorlessPair(),
        "7 truffleDamaged" to SeventhSessionFixtures.truffleDamagedUnitGlyph(),
        "8 redTwelve" to EighthSessionFixtures.redLabelTwelve(),
        "8 drinkConfirmed" to EighthSessionFixtures.drinkConfirmed(),
        "8 crackerAdvance" to EighthSessionFixtures.crackerAutoAdvance(),
        "9 green" to NinthSessionFixtures.greenDrinkStrategyB(),
        "9 greenStrategyB" to NinthSessionStrategyBDocuments.greenDrinkUnitRecognised(),
        "9 whiteStrategyB" to NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
        "9 cracker" to NinthSessionFixtures.crackerAutoAdvance(),
        "9 blueTub" to NinthSessionFixtures.blueTubAutoAdvance(),
        "9 redTwelve" to NinthSessionFixtures.redLabelTwelve(),
    )
}
