package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ConflictAdjudicationTest {

    // Reused verbatim from EvidenceResolverTest.kt's verified fixture pattern (see Task 6's
    // ScanDecisionEngineTest for the same helpers) -- CarbCandidate/OcrDocument/RecognitionEvidence
    // construction must match this repo's existing style exactly.

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(200, 150, 250, 180),
        evidence = emptyList(),
    )

    /**
     * A document whose OTHER rows (energie/vet/eiwit at a fixed ratio between a per-100 g column and
     * a per-serving column) make CrossColumnRatioCheck.check(document, candidate) return Consistent
     * for a candidate whose row matches that ratio, and Conflicting for one that doesn't -- mirroring
     * the real four-row fixture documented in CLAUDE.md's "Verified automatic advancement" section
     * (the cracker canary: energie 135/432=0.313, fat 3.4/11=0.309, etc., median ~0.31).
     *
     * Two header phrases -- "Per 100 g" (left, x=150-270) and "Per serving" (right, x=400-500) --
     * are required for [ColumnClassifier] to resolve any columns at all; a table with no header row
     * resolves zero columns and every check falls back to NotEnoughEvidence regardless of the value
     * cells beneath it. The two headers are kept far enough apart that the header span walk (which
     * tries up to 4 elements, longest first) cannot merge them into one span, which is what happens
     * if a bare "100" or "serving" token sits close enough to be swept into the other phrase's match.
     *
     * `rowValue` is the accepted per-100 g candidate (under the left column, matching [candidate]'s
     * fixed geometry); `otherColumnValue` is the same row's per-serving figure (under the right
     * column). The other three rows' per-100 figures are deliberately printed LARGER than their
     * per-serving figures (432/135, 11/3.4, 2.8/0.9 -> ratio ~0.3125) so the table's own established
     * ratio is serving/per-100 ~= 0.31 -- matching a candidate of 72/100g against a ~22.5 g serving
     * (ratio 0.3125, Consistent) while a candidate of 12/100g against a ~37.5 g serving (ratio 3.125)
     * is wildly inconsistent with it (Conflicting).
     */
    private fun tableWithRatio(rowValue: String, otherColumnValue: String) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("Per", OcrBox(150, 0, 190, 20), blockId = -1, lineId = 0),
            OcrElement("100", OcrBox(200, 0, 250, 20), blockId = -1, lineId = 0),
            OcrElement("g", OcrBox(255, 0, 270, 20), blockId = -1, lineId = 0),
            OcrElement("Per", OcrBox(400, 0, 440, 20), blockId = -1, lineId = 1),
            OcrElement("serving", OcrBox(445, 0, 500, 20), blockId = -1, lineId = 1),
            OcrElement("Energie", OcrBox(0, 30, 100, 60), blockId = 0, lineId = 0),
            OcrElement("432", OcrBox(200, 30, 250, 60), blockId = 0, lineId = 0),
            OcrElement("135", OcrBox(450, 30, 500, 60), blockId = 0, lineId = 0),
            OcrElement("Vet", OcrBox(0, 70, 100, 100), blockId = 1, lineId = 0),
            OcrElement("11", OcrBox(200, 70, 250, 100), blockId = 1, lineId = 0),
            OcrElement("3.4", OcrBox(450, 70, 500, 100), blockId = 1, lineId = 0),
            OcrElement("Eiwit", OcrBox(0, 110, 100, 140), blockId = 2, lineId = 0),
            OcrElement("2.8", OcrBox(200, 110, 250, 140), blockId = 2, lineId = 0),
            OcrElement("0.9", OcrBox(450, 110, 500, 140), blockId = 2, lineId = 0),
            OcrElement("Koolhydraten", OcrBox(0, 150, 100, 180), blockId = 3, lineId = 0),
            OcrElement(rowValue, OcrBox(200, 150, 250, 180), blockId = 3, lineId = 0),
            OcrElement(otherColumnValue, OcrBox(450, 150, 500, 180), blockId = 3, lineId = 0),
        ),
    )

    private fun evidenceGroup(value: String, document: OcrDocument): List<RecognitionEvidence> = listOf(
        RecognitionEvidence(
            source = EvidenceSource.FULL_FRAME_PASS_A,
            report = NutritionParseReport(LabelReading.Confident(candidate(value, NutritionBasis.PER_100_G)), emptyList()),
            document = document,
        ),
    )

    @Test fun `a group explicitly contradicted by its own table loses to a group the same table supports`() {
        // 12/100g against a serving column of ~37.5 (ratio 3.1, wildly inconsistent with the
        // table's other rows at ~0.31) vs 72/100g against ~22.5 (ratio 0.3125, consistent).
        val contradicted = evidenceGroup("12", tableWithRatio(rowValue = "12", otherColumnValue = "37.5"))
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val groups = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue("expected SingleSupported, got $result", result is ConflictAdjudication.AdjudicationResult.SingleSupported)
    }

    @Test fun `two groups both uncheckable (not enough table rows) remains a conflict`() {
        val sparseDocument = OcrDocument(
            width = 100, height = 100,
            elements = listOf(OcrElement("Koolhydraten", OcrBox(0, 0, 50, 20), blockId = 0, lineId = 0)),
        )
        val groupA = evidenceGroup("12", sparseDocument)
        val groupB = evidenceGroup("72", sparseDocument)
        val groups = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to groupA,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to groupB,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue(result is ConflictAdjudication.AdjudicationResult.StillConflicted)
    }

    @Test fun `a supported group versus an uncheckable group remains a conflict -- uncheckable is not the same as contradicted`() {
        val sparseDocument = OcrDocument(
            width = 100, height = 100,
            elements = listOf(OcrElement("Koolhydraten", OcrBox(0, 0, 50, 20), blockId = 0, lineId = 0)),
        )
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val uncheckable = evidenceGroup("12", sparseDocument)
        val groups = mapOf(
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to uncheckable,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue(
            "a supported candidate does not automatically defeat one that simply cannot be checked",
            result is ConflictAdjudication.AdjudicationResult.StillConflicted,
        )
    }

    @Test fun `adjudication is order-independent -- swapping map insertion order gives the same result`() {
        val contradicted = evidenceGroup("12", tableWithRatio(rowValue = "12", otherColumnValue = "37.5"))
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val forward = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
        )
        val reversed = mapOf(
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
        )

        val resultForward = ConflictAdjudication.adjudicate(forward) as ConflictAdjudication.AdjudicationResult.SingleSupported
        val resultReversed = ConflictAdjudication.adjudicate(reversed) as ConflictAdjudication.AdjudicationResult.SingleSupported

        assertEquals(resultForward.evidence, resultReversed.evidence)
    }
}
