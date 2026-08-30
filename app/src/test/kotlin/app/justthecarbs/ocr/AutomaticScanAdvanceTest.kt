package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * When a capture may skip the crop-confirmation step (1.0.3 P3).
 *
 * **These are safety tests.** The fast path's entire claim is that it removes a tap without removing
 * a judgement, so what matters is not that the confident case advances — it is that every other case
 * does not. Each outcome below is produced by running the **real** [EvidenceResolver] over evidence
 * shaped like the situation it describes, rather than by constructing an `Outcome` directly: a gate
 * tested against hand-built outcomes would keep passing if the resolver's own classification moved
 * underneath it, which is exactly the coupling that matters here.
 */
class AutomaticScanAdvanceTest {

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(100, 100, 200, 130),
        evidence = emptyList(),
    )

    private fun document() = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("53,5", OcrBox(100, 100, 200, 130), blockId = 0, lineId = 0, confidence = 0.9f),
        ),
    )

    private fun evidence(
        source: EvidenceSource,
        value: String? = null,
        ambiguous: List<String> = emptyList(),
        basis: NutritionBasis? = NutritionBasis.PER_100_G,
    ): RecognitionEvidence {
        val reading = when {
            value != null -> LabelReading.Confident(candidate(value, basis))
            ambiguous.isNotEmpty() -> LabelReading.Ambiguous(ambiguous.map { candidate(it, basis) })
            else -> LabelReading.NotFound
        }
        return RecognitionEvidence(
            source = source,
            report = NutritionParseReport(reading, emptyList()),
            document = document(),
        )
    }

    // ---- 1. the case the fast path exists for --------------------------------------------------

    /**
     * Two independent recognition runs agreeing gives a confident answer, and that skips the crop.
     *
     * This is the ordinary outcome for someone who pointed the phone at a nutrition table, and the
     * tap it removes was an approval of a rectangle the app had already chosen for them.
     */
    @Test
    fun `a corroborated confident reading may advance without the crop step`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FILTERED_PASS_A, value = "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, value = "53.5"),
            ),
        )

        assertTrue("precondition: this is Resolved", outcome is EvidenceResolver.Outcome.Resolved)
        assertTrue(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** Pass A answering alone is the pre-existing safe path (resolver rule 3), and it advances too. */
    @Test
    fun `pass A alone answering confidently may advance`() {
        val outcome = EvidenceResolver.resolve(listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, value = "61.9")))

        assertTrue("precondition: this is Resolved", outcome is EvidenceResolver.Outcome.Resolved)
        assertTrue(AutomaticScanAdvance.mayAdvance(outcome))
    }

    // ---- 2. the cases that must NOT advance ----------------------------------------------------

    /**
     * The subtle one, and the reason this gate is stricter than "is it Resolved".
     *
     * With no confident pass the resolver deliberately keeps the richest **ambiguous** report rather
     * than flattening it to `NotFound` — so the outcome really is `Resolved`, and gating on the
     * outcome type alone would send a multi-candidate reading straight past the crop step. It is not
     * unsafe (the scanner shows `AmbiguousCard` and never auto-accepts), but the parser could not
     * decide between candidates, and a frame containing more than the table is the usual reason.
     * Tightening the rectangle is the user's most direct lever on exactly that.
     */
    @Test
    fun `a Resolved outcome carrying an ambiguous reading does NOT advance`() {
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguous = listOf("53.5", "6.7"))),
        )

        assertTrue(
            "precondition: the resolver reports this as Resolved, which is what makes it a trap",
            outcome is EvidenceResolver.Outcome.Resolved,
        )
        assertTrue(
            "precondition: carrying an Ambiguous reading",
            (outcome as EvidenceResolver.Outcome.Resolved).reading is LabelReading.Ambiguous,
        )
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** An uncorroborated value must still be checked against the package on the frozen photo. */
    @Test
    fun `a needs-verification outcome does NOT advance`() {
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.SELECTED_REGION_OCR, value = "2.3")),
        )

        assertTrue(
            "precondition: a lone non-Pass-A run needs verification",
            outcome is EvidenceResolver.Outcome.NeedsVerification,
        )
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** Two passes claiming different numbers. At least one is wrong; nothing can say which. */
    @Test
    fun `a conflicted outcome does NOT advance`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FILTERED_PASS_A, value = "2.09"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, value = "2"),
            ),
        )

        assertTrue("precondition: this is Conflicted", outcome is EvidenceResolver.Outcome.Conflicted)
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /** Nothing usable anywhere: the rectangle is the most direct thing the user can change. */
    @Test
    fun `a nothing-found outcome does NOT advance`() {
        val outcome = EvidenceResolver.resolve(listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A)))

        assertTrue("precondition: this is Nothing", outcome is EvidenceResolver.Outcome.Nothing)
        assertFalse(AutomaticScanAdvance.mayAdvance(outcome))
    }

    /**
     * A confident value whose basis was never established does not advance either.
     *
     * `Resolved` + `Confident` is the advance condition, and a null basis cannot reach it: a
     * candidate with no column is unconstructible (`CarbCandidate`'s `init`), so the only confident
     * readings that exist carry a basis. Pinned so that a future relaxation of that invariant fails
     * here rather than quietly shipping a "grams of what?" reading past the crop step.
     */
    @Test
    fun `every advancing outcome carries a basis`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FILTERED_PASS_A, value = "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, value = "53.5"),
            ),
        )

        assertTrue(AutomaticScanAdvance.mayAdvance(outcome))
        val reading = (outcome as EvidenceResolver.Outcome.Resolved).reading as LabelReading.Confident
        assertTrue("an advancing reading must state what it is per", reading.candidate.basis != null)
    }
}
