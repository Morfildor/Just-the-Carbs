package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * A candidate's basis must come from that candidate's own evidence — never from a neighbour's.
 *
 * ## The failure these pin
 *
 * `docs/Scan Evidence 02-09 2nd test/20260902-103936-423`. ML Kit read the printed `per 100 g`
 * header as `1009`, so no per-100 column resolved. The per-100 value `72,0` then bound to the one
 * column that survived — the serving column, 329 px away, inside the deliberately generous
 * `LOOSE_COLUMN_FRACTION` tolerance — and recovery offered **`72 g / serving`**.
 *
 * No stage was individually wrong. The tolerance is generous on purpose so photographic skew does
 * not detach a cell from its own column, and the serving column really was the nearest one. The
 * defect is that *nearest surviving column* was treated as *this cell's column*, which are the same
 * thing only while every column is intact.
 *
 * The rule added is structural and names nothing product-specific: **a column may claim a cell only
 * if no other value cell on the same row is closer to that column's centre.** These tests are built
 * from geometry rather than from the device fixture, so they state the rule rather than the case.
 */
class CandidateBasisProvenanceTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    /**
     * A two-column table whose per-100 header is [perHundredHeader].
     *
     * Passing a damaged spelling is how the hazard is reproduced: the printed layout is identical
     * and only the header text differs, so anything that changes between the two runs is caused by
     * the header being unreadable and nothing else.
     */
    private fun table(perHundredHeader: String) = OcrDocument(
        width = 1000,
        height = 1200,
        elements = listOf(
            element(perHundredHeader, 380, 100, 520, 140),
            // "Nutritional value portion" is the device's own header text. It matters that this
            // states a serving *column* and not a serving *declaration*: the damaged capture prints
            // no "Serv. size: … (18 g)" sentence, so `ServingDeclaration.of` returns null there and
            // the document-level fallback in `basisFor` never runs. Writing a header this fixture
            // could read as a declaration would model a different failure from the measured one.
            element("Nutritional value portion", 660, 100, 900, 140),
            element("Carbohydrate", 40, 300, 300, 340),
            element("72,0 g", 400, 300, 500, 340),
            element("22,5 g", 730, 300, 830, 340),
            element("of which sugars", 60, 380, 340, 420),
            element("2,3 g", 400, 380, 500, 420),
            element("0,7 g", 730, 380, 830, 420),
        ),
    )

    @Test
    fun `an intact table gives every cell its own column's basis`() {
        // The control. Both cells keep their own basis, so the rule below cannot be "suppress
        // whenever two cells are on one row".
        val candidates = RecoveryCandidates.of(table("per 100 g"))

        val perHundred = candidates.single { it.reading.amount.compareTo(BigDecimal("72.0")) == 0 }
        assertEquals(
            CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            perHundred.reading.basis,
        )

        val serving = candidates.single { it.reading.amount.compareTo(BigDecimal("22.5")) == 0 }
        assertTrue(
            "the serving cell keeps its serving basis, got ${serving.reading.basis}",
            serving.reading.basis is CarbBasis.PerUnknownServing ||
                serving.reading.basis is CarbBasis.PerQuantity,
        )
    }

    @Test
    fun `a damaged per-100 header does not relabel its value as per-serving`() {
        // The same table with the header OCR damaged. `1009` is what the device actually returned
        // for `100 g`; the point is that it resolves no column, not that it is that exact string.
        val document = table("1009")
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        // Preconditions — without these the test could pass because the fixture never reproduced
        // the hazard. The per-100 column must be gone, a serving column must survive for the value
        // to wrongly bind to, and no document-level serving declaration may exist (that is a
        // different, legitimate path and would mask this one).
        assertTrue(
            "precondition: no per-100 column survives; got " + columns.joinToString { "${it.kind}" },
            columns.none {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            },
        )
        assertTrue(
            "precondition: a serving column survives",
            columns.any { it.kind == NutritionColumnKind.PER_SERVING },
        )
        assertNull(
            "precondition: this label states no serving declaration",
            ServingDeclaration.of(rows),
        )

        val candidates = RecoveryCandidates.of(document)

        assertTrue(
            "72 must not inherit the serving column: " + candidates.joinToString { it.label },
            candidates.none {
                it.reading.amount.compareTo(BigDecimal("72.0")) == 0 &&
                    it.reading.basis !is CarbBasis.PerHundred
            },
        )
    }

    @Test
    fun `the cell whose column it really is keeps that column`() {
        // The other half: suppressing the borrowed basis must not suppress the genuine one, or the
        // rule would be "offer nothing when a header is damaged", which loses a real choice.
        val candidates = RecoveryCandidates.of(table("1009"))

        val serving = candidates.singleOrNull { it.reading.amount.compareTo(BigDecimal("22.5")) == 0 }
        assertNotNull(
            "the serving value is genuinely in the serving column: " +
                candidates.joinToString { it.label },
            serving,
        )
    }

    @Test
    fun `a lone cell may still claim a distant column`() {
        // The rule is about *competition*, not distance. A row printing one value under a column
        // whose header sits slightly off-centre — ordinary on a photographed label — must still
        // bind, or legitimate readings disappear whenever the framing is imperfect.
        val document = OcrDocument(
            width = 1000,
            height = 1200,
            elements = listOf(
                element("per 100 g", 380, 100, 520, 140),
                element("Carbohydrate", 40, 300, 300, 340),
                element("72,0 g", 520, 300, 620, 340),
            ),
        )
        val candidate = RecoveryCandidates.of(document)
            .singleOrNull { it.reading.amount.compareTo(BigDecimal("72.0")) == 0 }

        assertNotNull("a single uncontested cell must keep its column", candidate)
        assertEquals(
            CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            candidate!!.reading.basis,
        )
    }

    @Test
    fun `a value in an unknown column states no basis at all`() {
        // The pre-existing rule, re-pinned here because the competition rule must not accidentally
        // create a basis where the column kind already refuses one.
        val document = OcrDocument(
            width = 1000,
            height = 1200,
            elements = listOf(
                element("per 250 ml", 700, 100, 880, 140),
                element("Carbohydrate", 40, 300, 300, 340),
                element("1,3 g", 730, 300, 830, 340),
            ),
        )
        assertTrue(
            "an UNKNOWN column supplies no basis: " +
                RecoveryCandidates.of(document).joinToString { it.label },
            RecoveryCandidates.of(document).isEmpty(),
        )
    }

    @Test
    fun `verification belongs to the candidate being promoted`() {
        // Invariant 4. Two confident passes claiming different values must not produce a verdict
        // about whichever happened to be first in the evidence list.
        val document = FifthSessionFixtures.crackerCrossColumnVerified()
        val correct = NutritionTableInterpreter.interpret(document)
        val misreadDocument = FourthSessionFixtures.crackerMisreadTotal()
        val misread = NutritionTableInterpreter.interpret(misreadDocument)

        assertEquals(
            "precondition: the two reports assert different values",
            BigDecimal("72.0"),
            (correct.reading as LabelReading.Confident).candidate.value,
        )
        assertEquals(
            BigDecimal("12.0"),
            (misread.reading as LabelReading.Confident).candidate.value,
        )

        val verdict = AutomaticVerification.verify(
            listOf(
                RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, misread, misreadDocument),
                RecognitionEvidence(EvidenceSource.SELECTED_REGION_OCR, correct, document),
            ),
        )
        assertEquals(
            "disagreeing passes leave no single candidate to verify",
            AutomaticVerification.Route.NONE,
            verdict.route,
        )
        assertNull("and no ratio should be reported for a candidate nobody agreed on", verdict.medianRatio)
    }

    @Test
    fun `the order of the evidence list does not change the verdict`() {
        val document = FifthSessionFixtures.crackerCrossColumnVerified()
        val report = NutritionTableInterpreter.interpret(document)
        val passA = RecognitionEvidence(EvidenceSource.FULL_FRAME_PASS_A, report, document)
        val strategyB = RecognitionEvidence(EvidenceSource.SELECTED_REGION_OCR, report, document)

        assertEquals(
            AutomaticVerification.verify(listOf(passA, strategyB)).route,
            AutomaticVerification.verify(listOf(strategyB, passA)).route,
        )
    }
}
