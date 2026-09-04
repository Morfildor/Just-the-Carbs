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
 * The fifth phone session (`docs/Scan Evidence 02-09 2nd test/`), asserted.
 *
 * ## The failure this file was written for
 *
 * `20260902-103936-423`. The packet prints `72,0 g / 100 g` and `22,5 g / portion`. ML Kit read the
 * per-100 header as **`1009`**, so [ColumnClassifier] resolved no per-100 column at all — only the
 * serving column at x=1411 and a reference-percent column at x=1584.
 *
 * The device bundle then records two separate defects:
 *
 * ```
 * reading         : NotFound (Strategy A re-parse)
 * strategy B      : RAN_CONFIDENT
 * automatic-verification: CROSS_COLUMN (support=5, median=0.309, candidate=0.313)
 * final UI action : RECOVERY
 * ```
 *
 * and recovery displayed `72 g / serving` beside `22.5 g / serving`.
 *
 * 1. **A fabricated basis.** `72,0` sits at x≈1082. The serving column's centre is x=1411, and
 *    [RecoveryCandidates] binds with `LOOSE_COLUMN_FRACTION` = 0.22 of the document width — 370 px
 *    on this 1684-wide capture. 1411 − 1082 = 329 < 370, so the per-100 value bound to the serving
 *    column and was labelled with a basis it never had. A damaged header must never cause a number
 *    to inherit a neighbouring column's meaning.
 * 2. **A verified reading that was thrown away.** `SELECTED_REGION_OCR` read `72.0/PER_100_G` and
 *    the label's own other rows corroborated it, yet the app showed generic recovery.
 *
 * Both are asserted here against the real recognition, not a reconstruction of it.
 */
class FifthSessionRegressionTest {

    // ---------------------------------------------------------------- the release blocker

    @Test
    fun `the damaged per-100 header resolves no per-100 column`() {
        // The precondition the whole failure rests on. Asserted rather than assumed, so that if a
        // future change makes this header resolve after all, the tests below stop measuring the
        // hazard they were written for and say so here first.
        val document = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        assertTrue(
            "expected the `1009` header to leave no per-100 column; got " +
                columns.joinToString { "${it.kind}@${it.centerX}" },
            columns.none {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            },
        )
        assertTrue(
            "expected a serving column to survive, which is what the value could wrongly bind to",
            columns.any { it.kind == NutritionColumnKind.PER_SERVING },
        )
    }

    @Test
    fun `the per-hundred value is never offered as a serving figure`() {
        val document = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val candidates = RecoveryCandidates.of(document)

        val fabricated = candidates.filter {
            it.reading.amount.compareTo(BigDecimal("72.0")) == 0 &&
                it.reading.basis !is CarbBasis.PerHundred
        }
        assertTrue(
            "72 was offered under a basis it never had: " +
                fabricated.joinToString { it.label },
            fabricated.isEmpty(),
        )
        assertTrue(
            "no candidate may read `72 g / serving`: " + candidates.joinToString { it.label },
            candidates.none { it.label.contains("72") && it.label.contains("serving") },
        )
    }

    @Test
    fun `the serving figure keeps its own serving basis`() {
        // The other half of rule 3: suppressing the fabricated basis must not suppress the genuine
        // one. `22,5` sits at x≈1431, twenty pixels from the serving column's centre — it really is
        // in that column, and its own provenance supports the label it carries.
        val document = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val candidates = RecoveryCandidates.of(document)

        val serving = candidates.singleOrNull {
            it.reading.amount.compareTo(BigDecimal("22.5")) == 0
        }
        assertNotNull(
            "the serving value should still be offered: " + candidates.joinToString { it.label },
            serving,
        )
        assertTrue(
            "22.5 should carry a serving basis, got ${serving!!.label}",
            serving.reading.basis is CarbBasis.PerUnknownServing ||
                serving.reading.basis is CarbBasis.PerQuantity,
        )
    }

    @Test
    fun `the verified selected-region reading becomes the resolved result`() {
        // The state contradiction. Strategy A finds nothing because the header is damaged; the
        // independent recognition reads the printed value and the table's own rows corroborate it.
        // That combination must resolve, not fall through to generic recovery.
        val passA = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val evidence = listOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = NutritionTableInterpreter.interpret(passA),
                document = passA,
            ),
            selectedRegionEvidence(),
        )

        val outcome = EvidenceResolver.resolve(evidence)
        val verdict = AutomaticVerification.verify(evidence)

        assertEquals(
            "cross-column verification should support the selected-region reading",
            AutomaticVerification.Route.CROSS_COLUMN,
            verdict.route,
        )
        assertTrue(
            "a verified single-run reading must resolve, got ${outcome::class.simpleName}",
            outcome is EvidenceResolver.Outcome.Resolved,
        )

        val reading = (outcome as EvidenceResolver.Outcome.Resolved).reading
        assertTrue("expected a confident reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("72.0"), candidate.value)
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `the verified selected-region reading advances automatically`() {
        val passA = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val evidence = listOf(
            RecognitionEvidence(
                source = EvidenceSource.FULL_FRAME_PASS_A,
                report = NutritionTableInterpreter.interpret(passA),
                document = passA,
            ),
            selectedRegionEvidence(),
        )

        val outcome = EvidenceResolver.resolve(evidence)
        val verdict = AutomaticVerification.verify(evidence)

        assertTrue(
            "the device showed RECOVERY for a cross-column-verified 72/PER_100_G",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, verdict, selectedRegionEvidence().document),
        )
    }

    /**
     * The selected-region recognition, as the device performed it.
     *
     * ## Why this uses a different document from Pass A's
     *
     * Strategy B is a **fresh ML Kit pass over the cropped pixels**, not a re-parse of Pass A's
     * elements — that is the whole difference between it and Strategy A, and it is why it counts as
     * a distinct [RecognitionRun]. On the device it read the `per 100 g` header that Pass A had
     * mangled into `1009`, which is how the bundle records `CROSS_COLUMN (support=5)` for a capture
     * whose Pass A document resolves no per-100 column at all.
     *
     * So its document is [FifthSessionFixtures.crackerCrossColumnVerified] — a real, undamaged
     * recognition of the same printed table from the same session, forty seconds earlier. Reusing
     * the damaged document here would model a Strategy B that cannot see anything Pass A missed,
     * which is the safe version of the hazard and would prove nothing.
     *
     * The reading is taken from the interpreter's own output on that document, so the value, the
     * basis and the geometry all come from a real recognition rather than from literals.
     */
    private fun selectedRegionEvidence(): RecognitionEvidence {
        val document = FifthSessionFixtures.crackerCrossColumnVerified()
        val report = NutritionTableInterpreter.interpret(document)

        val candidate = (report.reading as LabelReading.Confident).candidate
        assertEquals("precondition: Strategy B reads the printed value", BigDecimal("72.0"), candidate.value)
        assertEquals("precondition: and its printed basis", NutritionBasis.PER_100_G, candidate.basis)

        return RecognitionEvidence(
            source = EvidenceSource.SELECTED_REGION_OCR,
            report = report,
            document = document,
        )
    }

    // ---------------------------------------------------------------- routes that must not regress

    @Test
    fun `the cross-column verified cracker still skips the second recognition`() {
        // `20260902-103926-226` reached the calculator with no tap and no second ML Kit pass. That
        // is the fast path working; this pins that the fix does not cost it.
        val document = FifthSessionFixtures.crackerCrossColumnVerified()
        val report = NutritionTableInterpreter.interpret(document)
        val verdict = AutomaticVerification.verify(document, report)

        assertEquals(AutomaticVerification.Route.CROSS_COLUMN, verdict.route)
        assertTrue("expected at least 3 supporting rows", verdict.supportingRows >= 3)

        val candidate = (report.reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("72.0"), candidate.value)
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `both clean drinks read half a gram per hundred millilitres`() {
        listOf(
            "103854-549" to FifthSessionFixtures.drinkConfirmFirst(),
            "103906-452" to FifthSessionFixtures.drinkConfirmSecond(),
        ).forEach { (name, document) ->
            val report = NutritionTableInterpreter.interpret(document)
            val reading = report.reading
            assertTrue("$name should read confidently, got $reading", reading is LabelReading.Confident)
            val candidate = (reading as LabelReading.Confident).candidate
            assertEquals(name, BigDecimal("0.5"), candidate.value)
            assertEquals(name, NutritionBasis.PER_100_ML, candidate.basis)
        }
    }

    @Test
    fun `the drink is never asked for its basis again`() {
        // Invariant 9. The drink states `per 100 ml` and the parser reads it, so nothing downstream
        // may re-open the question — every candidate it offers must already carry that basis, and a
        // recovery choice labelled `/100 g` or with no basis at all would be the 2.6x error the
        // basis-complete rework removed.
        listOf(
            "103854-549" to FifthSessionFixtures.drinkConfirmFirst(),
            "103906-452" to FifthSessionFixtures.drinkConfirmSecond(),
        ).forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach { candidate ->
                val basis = candidate.reading.basis
                assertTrue(
                    "$name offered '${candidate.label}', which does not state per 100 ml",
                    basis is CarbBasis.PerHundred && basis.basis == NutritionBasis.PER_100_ML,
                )
            }
        }
    }

    @Test
    fun `both sauce captures still offer six grams per eighteen gram serving`() {
        listOf(
            "103949-880" to FifthSessionFixtures.sauceLinearPanelFirst(),
            "104006-838" to FifthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { (name, document) ->
            val candidates = RecoveryCandidates.of(document)
            val six = candidates.singleOrNull { it.reading.amount.compareTo(BigDecimal("6")) == 0 }
            assertNotNull(
                "$name should offer the 6 g clause: " + candidates.joinToString { it.label },
                six,
            )
            val basis = six!!.reading.basis
            assertTrue("$name expected a PerQuantity basis, got $basis", basis is CarbBasis.PerQuantity)
            assertEquals(name, BigDecimal("18"), (basis as CarbBasis.PerQuantity).quantity)
            assertEquals(name, NutritionBasis.PER_100_G, basis.unit)
        }
    }

    @Test
    fun `the sauce never offers a reference intake figure as carbohydrate`() {
        listOf(
            FifthSessionFixtures.sauceLinearPanelFirst(),
            FifthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { document ->
            val offered = RecoveryCandidates.of(document).map { it.reading.amount }
            listOf("1", "2", "4", "22").forEach { forbidden ->
                assertTrue(
                    "$forbidden must never be offered as a carbohydrate value: " +
                        offered.joinToString { it.toPlainString() },
                    offered.none { it.compareTo(BigDecimal(forbidden)) == 0 },
                )
            }
        }
    }

    @Test
    fun `the sauce panel manufactures no phantom percent columns`() {
        listOf(
            FifthSessionFixtures.sauceLinearPanelFirst(),
            FifthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { document ->
            val rows = LogicalRowBuilder.build(document)
            val percent = ColumnClassifier.classify(rows, document.width)
                .filter { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
            // One is legitimate — the cell-shape fallback recovering a genuinely aligned column of
            // percentages. Eight was the inline-`% DV` defect, one header manufactured per clause.
            assertTrue(
                "expected at most one reference-percent column, got " +
                    percent.joinToString { "@${it.centerX}" },
                percent.size <= 1,
            )
        }
    }

    @Test
    fun `text after the ingredients boundary is not a nutrient row`() {
        val document = FifthSessionFixtures.sauceLinearPanelFirst()
        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)

        val brownSugar = rows.indices.filter {
            rows[it].text.contains("brown sugar", ignoreCase = true)
        }
        assertTrue("fixture should contain the brown-sugar ingredient row", brownSugar.isNotEmpty())
        brownSugar.forEach {
            assertEquals(
                "the ingredient list must not classify as a nutrient row: '${rows[it].text}'",
                NutritionRowKind.OTHER,
                kinds[it],
            )
        }
    }

    // ---------------------------------------------------------------- the contradicted misread

    @Test
    fun `the contradicted misread is never offered by recovery`() {
        // Fourth-session capture, checked here because recovery is the surface the fifth session
        // changed. `12` is contradicted by the table's own rows; it must not reappear as a tap-away
        // choice on the screen the user reaches after the automatic path refuses.
        val document = FourthSessionFixtures.crackerMisreadTotal()
        val report = NutritionTableInterpreter.interpret(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        assertEquals("precondition: the fixture still reproduces the misread", BigDecimal("12.0"), candidate.value)
        assertTrue(
            "precondition: the table must contradict it",
            CrossColumnRatioCheck.check(document, candidate) is CrossColumnRatioCheck.Verdict.Conflicting,
        )

        val offered = RecoveryCandidates.of(document)
        assertTrue(
            "a contradicted value must not be offered as a recovery choice: " +
                offered.joinToString { it.label },
            offered.none { it.reading.amount.compareTo(BigDecimal("12.0")) == 0 },
        )
    }

    @Test
    fun `a later run agreeing with a contradicted reading does not rescue it`() {
        val document = FourthSessionFixtures.crackerMisreadTotal()
        val report = NutritionTableInterpreter.interpret(document)

        val evidence = listOf(
            RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, report, document),
            RecognitionEvidence(EvidenceSource.SELECTED_REGION_OCR, report, document),
        )

        val verdict = AutomaticVerification.verify(evidence)
        assertEquals(
            "two runs making the same mistake is not corroboration",
            AutomaticVerification.Route.NONE,
            verdict.route,
        )
        assertTrue(
            "the rejection should name the contradiction, got ${verdict.rejectionReason}",
            verdict.rejectionReason.orEmpty().contains("contradict"),
        )
        assertFalse(
            AutomaticScanAdvance.mayAdvanceVerified(EvidenceResolver.resolve(evidence), verdict, evidence.first().document),
        )
    }
}
