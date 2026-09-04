package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Which elements [RecognitionEvidence.valueConfidence] averages over.
 *
 * ## The defect this pins
 *
 * `valueConfidence` selected its elements with `verticalOverlapRatio(box) > 0.5` and no horizontal
 * bound at all, so on a **merged row** it averaged the candidate's clause together with the child
 * nutrient's. The seventh session measured exactly that shape on a real package, where ML Kit put
 * the whole declaration on one reconstructed row:
 *
 * ```
 * Koolhydraten/Glucides 8,9 g   waarvan suikers/dont sucres 1,3 g
 * ^^^^^^ the candidate's clause ^^^^^^   ^^^^^^^^ the child clause ^^^^^^^^
 * ```
 *
 * The number this produces gates [EvidenceResolver.MIN_PROPOSAL_CONFIDENCE], which decides whether a
 * lone re-recognition is proposed to the user or dropped in silence. Averaging in tokens from a
 * different printed clause means that gate is answered partly by how well ML Kit read *the sugars
 * row*.
 *
 * ## Why the blast radius is small and the fix still matters
 *
 * The rule can only ever **withhold** a proposal, never create one, so a wrong average cannot
 * manufacture a value — it can only lose a good one, or keep a bad one that other rules then have to
 * catch. That is why this was a weakness rather than a release blocker.
 *
 * It matters because [NutrientRowSegments] is the clause bound that [ScaleAmbiguity],
 * [RecoveryCandidates] and the automatic path all already use, and those three "cannot disagree
 * about where the clause ends". This stage was the one place reasoning about a row that did not ask.
 *
 * ## What is deliberately unchanged
 *
 * An ordinary single-nutrient row has no clause boundary — [NutrientRowSegments.totalCarbohydrateSegment]
 * returns null there — and the whole row is the clause. Those fixtures must produce the identical
 * average they did before, which is what `an ordinary row is unaffected` asserts.
 */
class ValueConfidenceClauseBoundTest {

    private fun element(
        text: String,
        left: Int,
        right: Int,
        confidence: Float?,
        top: Int = 100,
        bottom: Int = 140,
    ) = OcrElement(
        text = text,
        box = OcrBox(left, top, right, bottom),
        blockId = 0,
        lineId = 0,
        confidence = confidence,
    )

    /**
     * The seventh session's merged shape: one reconstructed row carrying both printed clauses.
     *
     * The candidate's own clause is cleanly recognised (0.90); the child clause is damaged (0.10).
     * A bound average sees only the first, an unbounded one is dragged down by the second.
     *
     * ## Where the boundary actually falls, measured rather than assumed
     *
     * [NutrientRowSegments] locates clauses with the greedy span walk, which consumes the connective
     * `waarvan` into the **total's** clause — the segment runs `startX=100, endX=880`, so the child
     * clause begins at `suikers`. That is correct: `waarvan` ("of which") introduces the child but is
     * printed as part of the total's sentence, and CLAUDE.md already records that this walk answers
     * "which term names which nutrient" rather than "where does the printed clause end".
     *
     * So the clause holds **four** tokens here, one of which the recognizer read badly, and the
     * bounded average is `(0.90 + 0.90 + 0.90 + 0.10) / 4 = 0.70`. An earlier revision of this test
     * expected 0.90 by counting three tokens; that was a wrong expectation about the fixture, not a
     * defect in the bound, and it is corrected rather than worked around. The value the assertion
     * pins is measured from the real segmenter.
     */
    private fun mergedRow(): Pair<OcrDocument, CarbCandidate> {
        val elements = listOf(
            element("Koolhydraten", left = 100, right = 400, confidence = 0.90f),
            element("8,9", left = 420, right = 500, confidence = 0.90f),
            element("g", left = 510, right = 540, confidence = 0.90f),
            // The child clause. Same reconstructed row, different printed clause.
            element("waarvan", left = 700, right = 860, confidence = 0.10f),
            element("suikers", left = 880, right = 1040, confidence = 0.10f),
            element("1,3", left = 1060, right = 1140, confidence = 0.10f),
            element("g", left = 1150, right = 1180, confidence = 0.10f),
        )
        val candidate = CarbCandidate(
            sourceLine = elements.joinToString(" ") { it.text },
            label = "Koolhydraten",
            value = BigDecimal("8.9"),
            basis = NutritionBasis.PER_100_ML,
            score = 0,
            geometry = OcrBox(420, 100, 500, 140),
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_ML,
        )
        return OcrDocument(1400, 800, elements) to candidate
    }

    private fun evidenceFor(document: OcrDocument, candidate: CarbCandidate) = RecognitionEvidence(
        source = EvidenceSource.SELECTED_REGION_OCR,
        report = NutritionParseReport(
            reading = LabelReading.Confident(candidate),
            diagnostics = emptyList(),
        ),
        document = document,
    )

    /** Precondition: the fixture really is a merged row, or every case below passes vacuously. */
    @Test
    fun `the fixture is a genuine merged row with two clauses`() {
        val (document, _) = mergedRow()
        val row = LogicalRowBuilder.build(document).single()
        val clause = NutrientRowSegments.totalCarbohydrateSegment(row)
        assertNotNull(
            "the fixture must produce a bounded carbohydrate clause, or this test proves nothing",
            clause,
        )
        assertTrue(
            "the child clause must fall outside the carbohydrate clause",
            row.elements.filter { it.text == "suikers" }.none { clause!!.contains(it.box) },
        )
    }

    /** The defect: the child clause's damaged tokens must not drag the candidate's average down. */
    @Test
    fun `a merged row averages only the candidate's own clause`() {
        val (document, candidate) = mergedRow()
        val confidence = evidenceFor(document, candidate).valueConfidence
        assertNotNull(confidence)
        // Four tokens inside the clause (see the fixture KDoc for why `waarvan` is one of them):
        // (0.90 + 0.90 + 0.90 + 0.10) / 4. Unbounded this is 0.443, averaging all seven.
        assertEquals(
            "only the carbohydrate clause's own tokens may contribute",
            0.70,
            confidence!!.toDouble(),
            0.001,
        )
    }

    /** The child clause's *value* tokens are the ones that must never contribute. */
    @Test
    fun `the child clause's own figure never contributes`() {
        val (document, candidate) = mergedRow()
        val row = LogicalRowBuilder.build(document).single()
        val clause = NutrientRowSegments.totalCarbohydrateSegment(row)!!
        listOf("suikers", "1,3").forEach { text ->
            assertTrue(
                "'$text' belongs to the child clause and must fall outside the total's",
                row.elements.filter { it.text == text }.none { clause.contains(it.box) },
            )
        }
    }

    /**
     * The consequence, stated in the terms the resolver actually uses.
     *
     * Unbounded, this row averages to 0.44 — below [EvidenceResolver.MIN_PROPOSAL_CONFIDENCE] — so a
     * correct lone re-recognition of a cleanly printed carbohydrate clause was dropped in silence
     * because the *sugars* clause beside it recognised badly.
     */
    @Test
    fun `a clean clause beside a damaged one still clears the proposal threshold`() {
        val (document, candidate) = mergedRow()
        val confidence = evidenceFor(document, candidate).valueConfidence!!
        assertTrue(
            "a cleanly recognised clause must be proposable regardless of its neighbour",
            confidence >= EvidenceResolver.MIN_PROPOSAL_CONFIDENCE,
        )
    }

    /** An ordinary table row has no clause boundary, so nothing about it may change. */
    @Test
    fun `an ordinary row is unaffected`() {
        val elements = listOf(
            element("Koolhydraten", left = 100, right = 400, confidence = 0.80f),
            element("61,9", left = 420, right = 520, confidence = 0.60f),
            element("g", left = 530, right = 560, confidence = 0.40f),
        )
        val document = OcrDocument(1400, 800, elements)
        val candidate = CarbCandidate(
            sourceLine = elements.joinToString(" ") { it.text },
            label = "Koolhydraten",
            value = BigDecimal("61.9"),
            basis = NutritionBasis.PER_100_G,
            score = 0,
            geometry = OcrBox(420, 100, 520, 140),
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_G,
        )
        // The whole row is the clause, so all three tokens contribute exactly as before.
        assertEquals(0.60, evidenceFor(document, candidate).valueConfidence!!.toDouble(), 0.001)
    }

    /** No confidence reported anywhere is still "unknown", never a fabricated zero. */
    @Test
    fun `a document with no confidences reports null`() {
        val elements = listOf(
            element("Koolhydraten", left = 100, right = 400, confidence = null),
            element("61,9", left = 420, right = 520, confidence = null),
        )
        val document = OcrDocument(1400, 800, elements)
        val candidate = CarbCandidate(
            sourceLine = "Koolhydraten 61,9",
            label = "Koolhydraten",
            value = BigDecimal("61.9"),
            basis = NutritionBasis.PER_100_G,
            score = 0,
            geometry = OcrBox(420, 100, 520, 140),
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_G,
        )
        assertNull(evidenceFor(document, candidate).valueConfidence)
    }
}
