package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.fixtures.GroundTruthManifest
import org.junit.Assert.assertEquals
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
     * An ambiguous reading never advances past the crop step.
     *
     * With no confident pass the resolver deliberately keeps the richest **ambiguous** report rather
     * than flattening it to `NotFound`, so the user still sees the candidates. That state is now
     * named [EvidenceResolver.Outcome.Unresolved] rather than `Resolved` — see that type's KDoc for
     * the device bundle that recorded an ambiguity as "Resolved" beside a second pass that had found
     * nothing.
     *
     * The safety assertion is unchanged and is the point of the test: the parser could not decide
     * between candidates, a frame containing more than the table is the usual reason, and tightening
     * the rectangle is the user's most direct lever on exactly that.
     */
    @Test
    fun `an ambiguous reading does NOT advance`() {
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguous = listOf("53.5", "6.7"))),
        )

        assertTrue(
            "precondition: an ambiguity with nothing to corroborate it is Unresolved",
            outcome is EvidenceResolver.Outcome.Unresolved,
        )
        assertTrue(
            "precondition: carrying an Ambiguous reading",
            (outcome as EvidenceResolver.Outcome.Unresolved).reading is LabelReading.Ambiguous,
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

    // ---- 3. same-photograph agreement must not settle an Unsupported scale (Hellmann's 1.3 -> 13) --

    /**
     * Reproduces the documented Hellmann's case, `20260904-113950-065`: a bottle prints
     * `1,3 g / 100 ml`; every view of the one photograph reads `13g`, with no separator and nothing
     * on its row to pair against, so [ScaleAmbiguity.check] returns `Unsupported`.
     *
     * `AutomaticVerification.Verdict(route = NONE, viewsAgree = true)` is exactly what
     * `agreesAcrossViews`-style same-photograph agreement produces — two recognition *runs* of one
     * capture agreeing, never a distinct [PhysicalObservationId] and never [CrossColumnRatioCheck].
     * That is real evidence against a single misread digit and no evidence at all about absolute
     * decimal scale, because every view inherits the same ink.
     *
     * [AutomaticScanAdvance.eligibility] must ask [ReadingEligibility] the right question about this:
     * `corroborationSettlesScale` must be `false` here, so the figure is withheld from confirmation
     * and instead reaches the user one step later, through focused entry.
     */
    @Test fun `Unsupported-scale confident reading corroborated only by same-photograph agreement is NOT eligible for confirmation`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("Koolhydraten", OcrBox(0, 0, 100, 30), blockId = 0, lineId = 0),
                OcrElement("13g", OcrBox(200, 0, 250, 30), blockId = 0, lineId = 0, confidence = 0.9f),
            ),
        )
        val candidate = CarbCandidate(
            sourceLine = "Koolhydraten 13g",
            label = "Koolhydraten",
            value = BigDecimal("13"),
            basis = NutritionBasis.PER_100_ML,
            score = 120,
            geometry = OcrBox(200, 0, 250, 30),
            evidence = emptyList(),
        )
        // SELECTED_REGION_OCR, not FULL_FRAME_PASS_A: a lone Pass A recognition resolves outright
        // (EvidenceResolver rule 3, "Pass A alone answers"), so it is a re-recognition -- exactly the
        // shape the Hellmann's capture's Strategy B pass has -- that produces NeedsVerification.
        val outcome = EvidenceResolver.resolve(
            listOf(
                RecognitionEvidence(
                    source = EvidenceSource.SELECTED_REGION_OCR,
                    report = NutritionParseReport(LabelReading.Confident(candidate), emptyList()),
                    document = document,
                ),
            ),
        ) as? EvidenceResolver.Outcome.NeedsVerification
            ?: throw AssertionError("fixture must produce NeedsVerification — a single uncorroborated pass")

        // agreesAcrossViews-style corroboration: viewsAgree = true, route = NONE -- two recognition
        // RUNS of one photograph agreeing, never a structural or distinct-observation verification.
        val verification = AutomaticVerification.Verdict(route = AutomaticVerification.Route.NONE, viewsAgree = true)

        val isEligible = AutomaticScanAdvance.mayConfirm(outcome, verification, document)

        assertEquals(
            "same-photograph view agreement must not make an Unsupported-scale value confirmable",
            false,
            isEligible,
        )
    }

    // ---- 4. the ground-truth manifest: the Hellmann's case, end to end through the real fixture ----

    /**
     * [GroundTruthManifest]'s Hellmann's case (`20260904-113950-065`), replayed through the real
     * production fixture rather than a hand-built reproduction — the same three-view evidence
     * (`FULL_FRAME_PASS_A`, `FILTERED_PASS_A`, `SELECTED_REGION_OCR`) [FifteenthSessionReplay] builds
     * from [FifteenthSessionCorpus]'s actual recognized documents for this physical capture, all
     * sharing one [PhysicalObservationId] because they are one photograph.
     *
     * The corpus's own record (`FifteenthSessionCorpus.kt:161-166`) is what this fix changes: the
     * *device* action was `CONFIRM_ON_CAPTURE` with `13.0` prefilled — the wrong-prefill bug this task
     * closes. The manifest's `allowedFinalActions` (`RECOVERY`, `FOCUSED_AMOUNT_ENTRY`) and
     * `forbiddenDisplayedValues` (`13`, `13.0`) state what the fixed behaviour must be.
     */
    @Test fun `the ground-truth Hellmann's case never reaches CONFIRM_ON_CAPTURE with 13 prefilled`() {
        val case = GroundTruthManifest.CASES.first { it.captureId == "20260904-113950-065" }
        val capture = FifteenthSessionCorpus.captures.single { it.bundle == case.captureId }
        val result = FifteenthSessionReplay.replay(capture)

        assertEquals(
            false,
            result.action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
        )
        assertTrue(
            "action ${result.action} must be one of ${case.allowedFinalActions}",
            case.allowedFinalActions.contains(result.action.name),
        )
        case.forbiddenDisplayedValues.forEach { forbidden ->
            assertFalse(
                "the forbidden value $forbidden must never be offered",
                result.offeredValue?.compareTo(forbidden) == 0,
            )
        }
    }
}
