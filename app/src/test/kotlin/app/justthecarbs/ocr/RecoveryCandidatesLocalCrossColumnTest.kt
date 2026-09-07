package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins the gap [RecoveryCandidates.ofIncludingScaleRefusals] closes: the localized-panel
 * cross-column check inside [RecoveryCandidates.of] could permanently exclude a candidate before
 * [ConfirmationEligibility]'s own — deliberately more authoritative — full-document re-check ever
 * had the chance to run.
 *
 * ## The fixture
 *
 * A table with three "supporting" nutrient rows corrupted so their serving-to-per-100 ratio reads
 * 0.50 (double the label's true 0.25), plus four further rows correctly stating the true 0.25 ratio.
 * The carbohydrate candidate itself states the true 0.25 ratio.
 *
 * With only the first three corrupted rows visible — a narrower panel scope, exactly like the
 * Hellmann's mayonnaise measurement [RecoveryCandidates.ofIncludingScaleRefusals]'s own KDoc cites
 * — the median is 0.50 and the candidate's true 0.25 falls outside [CrossColumnRatioCheck]'s ±25%
 * tolerance: `Conflicting`. With the full seven rows visible, the four correct rows pull the median
 * back to 0.25 and the same candidate is `Consistent`.
 *
 * Both verdicts are computed directly against [CrossColumnRatioCheck.check] first, so the fixture's
 * own arithmetic is proven correct independently of [RecoveryCandidates] before anything built on
 * top of it is trusted.
 */
class RecoveryCandidatesLocalCrossColumnTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    /**
     * The corrupted-only rows: three nutrients stating a 0.50 serving/per-100 ratio (perHundred 20,
     * perServing 10), plus the header and the carbohydrate declaration itself (perHundred 20,
     * perServing 5 — the true 0.25 ratio).
     */
    private val corruptedRows = listOf(
        element("per 100 g", 360, 10, 500, 40),
        element("per serving", 690, 10, 830, 40),
        element("Energie", 20, 60, 200, 90),
        element("20", 390, 60, 460, 90),
        element("10", 710, 60, 780, 90),
        element("Vetten", 20, 110, 200, 140),
        element("20", 390, 110, 460, 140),
        element("10", 710, 110, 780, 140),
        element("Zout", 20, 160, 200, 190),
        element("20", 390, 160, 460, 190),
        element("10", 710, 160, 780, 190),
        element("Koolhydraten", 20, 210, 260, 240),
        element("20", 390, 210, 460, 240),
        element("5", 710, 210, 780, 240),
    )

    /** Four further rows at the true 0.25 ratio, present only in the full document. */
    private val correctingRows = listOf(
        element("Vezels", 20, 260, 200, 290),
        element("20", 390, 260, 460, 290),
        element("5", 710, 260, 780, 290),
        element("Eiwitten", 20, 310, 200, 340),
        element("20", 390, 310, 460, 340),
        element("5", 710, 310, 780, 340),
        element("Suikers", 20, 360, 200, 390),
        element("20", 390, 360, 460, 390),
        element("5", 710, 360, 780, 390),
        element("Natrium", 20, 410, 200, 440),
        element("20", 390, 410, 460, 440),
        element("5", 710, 410, 780, 440),
    )

    private fun document(elements: List<OcrElement>) =
        OcrDocument(width = 1000, height = 500, elements = elements)

    private fun carbCandidate(document: OcrDocument): CarbCandidate {
        val reading = NutritionTableParser.parseWithDiagnostics(document).reading
        assertTrue("fixture must produce a candidate, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate
    }

    // ------------------------------------------------------------- the fixture's own arithmetic

    @Test
    fun `the narrow panel alone reports the candidate as conflicting`() {
        val narrow = document(corruptedRows)
        val candidate = carbCandidate(narrow)

        val verdict = CrossColumnRatioCheck.check(narrow, candidate)

        assertTrue(
            "expected the narrow panel to conflict with the candidate, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.Conflicting,
        )
    }

    @Test
    fun `the full document with the correcting rows reports the candidate as consistent`() {
        val full = document(corruptedRows + correctingRows)
        val candidate = carbCandidate(full)

        val verdict = CrossColumnRatioCheck.check(full, candidate)

        assertTrue(
            "expected the full document to corroborate the candidate, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.Consistent,
        )
        assertTrue((verdict as CrossColumnRatioCheck.Verdict.Consistent).supportingRows >= 3)
    }

    // ------------------------------------------------------------- RecoveryCandidates.of / ofIncludingScaleRefusals

    /**
     * [RecoveryCandidates.of] scopes its cross-column check to `document.copy(elements =
     * panel.elements)` — [NutritionDocumentModel]'s own panel-locator geometry decides what that
     * scope actually contains on a real device (the Hellmann's mayonnaise measurement its own KDoc
     * cites), which this fixture does not attempt to reproduce. What this test isolates instead is
     * the mechanism [RecoveryCandidates] itself is responsible for: given a document that genuinely
     * conflicts with the candidate — narrower than another document that does not — `of` must
     * suppress it, which the two tests above already establish for these two documents.
     */
    @Test
    fun `of suppresses the candidate the narrow document's own cross-column check refutes`() {
        val narrow = document(corruptedRows)

        val candidates = RecoveryCandidates.of(narrow)

        assertTrue(
            "expected no carbohydrate candidate to survive the narrow document's own contradiction",
            candidates.none { it.reading.amount.compareTo(BigDecimal("20")) == 0 },
        )
    }

    /**
     * The gap this test file exists to close: [ofIncludingScaleRefusals] must retain the candidate
     * [of] suppresses for a cross-column reason, so [ConfirmationEligibility] can ask its own
     * full-document question about it. Retaining it here is not itself a safety weakening — nothing
     * downstream of this function accepts a value without [ConfirmationEligibility]'s own re-check.
     */
    @Test
    fun `ofIncludingScaleRefusals retains the candidate of suppresses for a cross-column reason`() {
        val narrow = document(corruptedRows)

        val candidates = RecoveryCandidates.ofIncludingScaleRefusals(narrow)

        val retained = candidates.singleOrNull { it.reading.amount.compareTo(BigDecimal("20")) == 0 }
        assertTrue(
            "expected ofIncludingScaleRefusals to retain the cross-column-refuted candidate",
            retained != null,
        )
        assertEquals(CarbBasis.PerHundred::class, retained!!.reading.basis::class)
    }

    // ------------------------------------------------------------- ConfirmationEligibility, end to end

    /**
     * The end-to-end proof: a candidate the narrow document's own cross-column check refutes, but
     * that the FULL document (with the correcting rows) does not, must reach
     * [ConfirmationEligibility.Verdict.Eligible] when evaluated against the full document — never
     * against the narrow one, and never unconditionally.
     */
    @Test
    fun `ConfirmationEligibility admits a candidate the narrow panel refused once the full document corroborates it`() {
        val full = document(corruptedRows + correctingRows)
        val candidate = carbCandidate(full)

        val verdict = ConfirmationEligibility.evaluate(
            document = full,
            reading = LabelReading.Confident(candidate),
            scale = ScaleAmbiguity.Verdict.Unsupported(
                candidateText = "20",
                reason = "test fixture: scale deliberately unresolved",
            ),
            disputed = DisputedCandidates.NONE,
        )

        assertTrue(
            "expected the full document to admit the candidate for confirmation, got $verdict",
            verdict is ConfirmationEligibility.Verdict.Eligible,
        )
    }

    /**
     * The negative control: evaluated against the NARROW document alone (where the candidate is a
     * genuine cross-column outlier and there is no richer evidence to correct it), the same shaped
     * candidate must still be refused. This is what proves the fix does not simply always admit a
     * cross-column-flagged candidate — it re-asks the question against whatever document it is
     * actually given, and a document that really does contradict the candidate still refuses it.
     */
    @Test
    fun `ConfirmationEligibility still refuses when only the narrow document is available`() {
        val narrow = document(corruptedRows)
        val candidate = carbCandidate(narrow)

        val verdict = ConfirmationEligibility.evaluate(
            document = narrow,
            reading = LabelReading.Confident(candidate),
            scale = ScaleAmbiguity.Verdict.Unsupported(
                candidateText = "20",
                reason = "test fixture: scale deliberately unresolved",
            ),
            disputed = DisputedCandidates.NONE,
        )

        assertNull(
            "expected the narrow document, with no correcting rows, to still refuse the candidate",
            (verdict as? ConfirmationEligibility.Verdict.Eligible),
        )
    }
}
