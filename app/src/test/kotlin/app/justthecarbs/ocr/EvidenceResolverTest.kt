package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The consensus rules (spec §8). These are safety tests, not convenience tests.
 *
 * Every case below corresponds to something that was actually measured while designing this pass, or
 * to a hazard the repo has already been bitten by. In particular the conflict cases are real: on the
 * grated-cheese fixture the full frame reports `2.09`, a 5% native-resolution crop reports `2` (the
 * printed value) and the production-overlay crop reports `2.04`. No property available to the app
 * distinguishes the correct one, so the resolver must refuse rather than vote.
 */
class EvidenceResolverTest {

    // ---- fixture plumbing ----------------------------------------------------------------------

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(100, 100, 200, 130),
        evidence = emptyList(),
    )

    /** A document whose elements overlap the candidate geometry, carrying a stated confidence. */
    private fun documentWithConfidence(confidence: Float?) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement(
                text = "53,5",
                box = OcrBox(100, 100, 200, 130),
                blockId = 0,
                lineId = 0,
                confidence = confidence,
            ),
        ),
    )

    private fun evidence(
        source: EvidenceSource,
        value: String?,
        basis: NutritionBasis? = NutritionBasis.PER_100_G,
        confidence: Float? = 0.9f,
        ambiguous: List<String> = emptyList(),
    ): RecognitionEvidence {
        val reading = when {
            value != null -> LabelReading.Confident(candidate(value, basis))
            ambiguous.isNotEmpty() -> LabelReading.Ambiguous(ambiguous.map { candidate(it, basis) })
            else -> LabelReading.NotFound
        }
        return RecognitionEvidence(
            source = source,
            report = NutritionParseReport(reading, emptyList()),
            document = documentWithConfidence(confidence),
        )
    }

    // ---- Rule 1: agreement -----------------------------------------------------------------------

    @Test
    fun `two passes agreeing on value and basis resolve confidently`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FILTERED_PASS_A, "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "53.5"),
            ),
        )

        val resolved = outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got $outcome")
        assertEquals(2, resolved.agreeingSources.size)
        val confident = resolved.reading as LabelReading.Confident
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("53.5")))
    }

    /**
     * THE trap this resolver was measured falling into.
     *
     * `FULL_FRAME_PASS_A` and `FILTERED_PASS_A` are two parses of ONE recognition — the filtered
     * document is a subset of the same elements, with identical characters. If their agreement counts
     * as corroboration, a single wrong reading promotes itself to "confirmed by two sources".
     *
     * This is not hypothetical: on the grated-cheese fixture both Pass A views agreed on the
     * known-wrong `2.09`, which resolved confidently *and* suppressed the independent recognition
     * that would have disagreed. Corroboration must therefore be counted over recognition runs.
     */
    @Test
    fun `the two pass A views cannot corroborate each other`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "2.09"),
                evidence(EvidenceSource.FILTERED_PASS_A, "2.09"),
            ),
        )

        val resolved = outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got $outcome")
        assertEquals(
            "one recognition must not present itself as two agreeing sources",
            1,
            resolved.agreeingSources.map { it.recognitionRun }.distinct().size,
        )
    }

    /** A genuinely independent second run does corroborate. */
    @Test
    fun `a separate recognition run does corroborate pass A`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
                evidence(EvidenceSource.FILTERED_PASS_A, "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "53.5"),
            ),
        )

        val resolved = outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got $outcome")
        assertTrue(
            "two distinct recognition runs agreed",
            resolved.agreeingSources.map { it.recognitionRun }.distinct().size >= 2,
        )
    }

    /**
     * Scale must not fake a conflict.
     *
     * `BigDecimal("53.5") != BigDecimal("53.50")` under `equals`, and this repo has already been
     * bitten twice by scale-sensitive comparison. If this regressed, every agreement involving a
     * trailing zero would be reported to the user as a conflict.
     */
    @Test
    fun `agreement is numeric so differing scale is not a conflict`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "53.50"),
            ),
        )

        assertTrue("53.5 and 53.50 are the same reading", outcome is EvidenceResolver.Outcome.Resolved)
    }

    // ---- Rule 2: conflict --------------------------------------------------------------------------

    /** THE measured case. Pass A says 53.5, a re-recognition says 9. Nothing may choose. */
    @Test
    fun `passes disagreeing on the value are conflicted and never pick one`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "9"),
            ),
        )

        val conflict = outcome as? EvidenceResolver.Outcome.Conflicted
            ?: throw AssertionError("expected Conflicted, got $outcome")
        assertEquals(2, conflict.values.size)
    }

    /**
     * The real grated-cheese disagreement, with the trap that makes it dangerous.
     *
     * `2` is the printed value and `2.09` is the known recognition error — but the app has no way to
     * know that. A resolver that preferred the "rounder" number would be applying numeric
     * plausibility as digit correction, which §26 forbids outright.
     */
    @Test
    fun `a rounder-looking value never wins a conflict`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "2.09"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "2"),
            ),
        )

        assertTrue(
            "2 vs 2.09 must refuse, not silently prefer the round number",
            outcome is EvidenceResolver.Outcome.Conflicted,
        )
    }

    /** Same number, different basis, is a conflict: per 100 g and per 100 ml are not interchangeable. */
    @Test
    fun `the same value under different bases is a conflict`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "5", NutritionBasis.PER_100_G),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "5", NutritionBasis.PER_100_ML),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.Conflicted)
    }

    // ---- Rule 3: lone Pass A keeps its existing standing ---------------------------------------

    @Test
    fun `pass A alone still answers when other passes find nothing`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "61.9"),
                evidence(EvidenceSource.SELECTED_REGION_OCR, null),
                evidence(EvidenceSource.LIVE_STABLE_FRAME, null),
            ),
        )

        val resolved = outcome as? EvidenceResolver.Outcome.Resolved
            ?: throw AssertionError("expected Resolved, got $outcome")
        assertEquals(listOf(EvidenceSource.FULL_FRAME_PASS_A), resolved.agreeingSources)
    }

    // ---- Rule 4: uncorroborated re-recognition proposes, never decides --------------------------

    /**
     * The measured witte-kaas recovery: full frame `NotFound`, native crop `Confident 2.3` (correct).
     * It must reach the user, and it must not arrive disguised as a settled answer.
     */
    @Test
    fun `a value only the crop found needs verification rather than being accepted`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "2.3"),
            ),
        )

        val proposal = outcome as? EvidenceResolver.Outcome.NeedsVerification
            ?: throw AssertionError("expected NeedsVerification, got $outcome")
        assertEquals(EvidenceSource.SELECTED_REGION_OCR, proposal.source)
        assertEquals(0, proposal.reading.candidate.value.compareTo(BigDecimal("2.3")))
    }

    @Test
    fun `a lone live frame is only ever a proposal`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
                evidence(EvidenceSource.LIVE_STABLE_FRAME, "61.9"),
            ),
        )

        assertTrue(
            "a live frame must never resolve the scan by itself",
            outcome is EvidenceResolver.Outcome.NeedsVerification,
        )
    }

    /** Low recognizer confidence withholds a proposal — it can subtract trust, never add it. */
    @Test
    fun `a poorly recognised lone value is not even proposed`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "9", confidence = 0.20f),
            ),
        )

        assertEquals(EvidenceResolver.Outcome.Nothing, outcome)
    }

    /**
     * Confidence must not be *required*, only respected when present.
     *
     * An engine that reports nothing (or a synthetic document) must still be able to propose,
     * otherwise swapping recognizer would silently disable the recovery path.
     */
    @Test
    fun `missing confidence does not block a proposal`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "2.3", confidence = null),
            ),
        )

        assertTrue(outcome is EvidenceResolver.Outcome.NeedsVerification)
    }

    /**
     * Confidence cannot override contradictory evidence.
     *
     * A very confidently recognised `9` against a less confidently recognised `53.5` is still a
     * conflict. Confidence describes characters, not meaning (§7).
     */
    @Test
    fun `high confidence does not win a conflict`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5", confidence = 0.55f),
                evidence(EvidenceSource.SELECTED_REGION_OCR, "9", confidence = 0.99f),
            ),
        )

        assertTrue(
            "confidence must not decide which value is the carbohydrate figure",
            outcome is EvidenceResolver.Outcome.Conflicted,
        )
    }

    // ---- degenerate inputs ----------------------------------------------------------------------

    @Test
    fun `no evidence at all resolves to nothing`() {
        assertEquals(EvidenceResolver.Outcome.Nothing, EvidenceResolver.resolve(emptyList()))
    }

    @Test
    fun `all passes failing resolves to nothing`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
                evidence(EvidenceSource.SELECTED_REGION_OCR, null),
            ),
        )

        assertEquals(EvidenceResolver.Outcome.Nothing, outcome)
    }

    /**
     * An ambiguity from Pass A must still reach the user rather than being flattened to NotFound —
     * carried by [EvidenceResolver.Outcome.Unresolved], which says what it is.
     *
     * The survival requirement is the assertion; the outcome's name changed on 2026-09-01 because
     * calling an undecided ambiguity "Resolved" is what a device bundle recorded on the scan that
     * went on to show a 2.6x-wrong value. See that type's KDoc.
     */
    @Test
    fun `an ambiguous pass A survives when nothing is confident`() {
        val outcome = EvidenceResolver.resolve(
            listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, null, ambiguous = listOf("53.5", "6.7")),
                evidence(EvidenceSource.SELECTED_REGION_OCR, null),
            ),
        )

        val unresolved = outcome as? EvidenceResolver.Outcome.Unresolved
            ?: throw AssertionError("expected Unresolved, got $outcome")
        assertTrue(unresolved.reading is LabelReading.Ambiguous)
    }

    /** Order must not change the outcome; the rules are symmetric. */
    @Test
    fun `resolution does not depend on evidence order`() {
        val a = evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5")
        val b = evidence(EvidenceSource.SELECTED_REGION_OCR, "9")

        val forward = EvidenceResolver.resolve(listOf(a, b))
        val backward = EvidenceResolver.resolve(listOf(b, a))

        assertTrue(forward is EvidenceResolver.Outcome.Conflicted)
        assertTrue(backward is EvidenceResolver.Outcome.Conflicted)
    }
}
