package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the second recognition runs, and when it is skipped.
 *
 * ## Why this matters for latency and for safety at once
 *
 * Strategy B is a fresh ML Kit pass over the cropped pixels, measured at **~400 ms** on the emulator
 * and more on a phone. Running it when it cannot change the answer is time the user waits for
 * nothing; *not* running it when it could contradict a wrong reading is the grated-cheese failure
 * this repo has already measured and pinned.
 *
 * So both directions are asserted here, on the real nine-photograph corpus's own recognitions where
 * possible, and the safety direction is the one with the negative control.
 */
class StrategyBInvocationTest {

    private val region = NormalizedRegion(0.05, 0.05, 0.95, 0.95)

    /** Records whether the second recognition was asked for, and answers with [answer]. */
    private class Recorder(private val answer: RecognitionEvidence? = null) {
        var calls = 0
            private set

        fun asLambda(): (android.graphics.Bitmap?, NormalizedRegion) -> RecognitionEvidence? =
            { _, _ ->
                calls++
                answer
            }
    }

    private fun passA(document: OcrDocument): PassAResult {
        val report = NutritionTableInterpreter.interpret(document)
        return PassAResult(
            sessionId = 1L,
            document = document,
            report = report,
            bitmap = null,
            evidence = null,
            recognitionMs = 0L,
        )
    }

    private fun resolve(
        document: OcrDocument,
        recorder: Recorder,
    ): SelectedTableResolution.Result = SelectedTableResolution.resolve(
        passA = passA(document),
        region = region,
        bitmap = null,
        stillObservationId = PhysicalObservationId("test-fixture"),
        liveEvidence = null,
        recogniseRegion = recorder.asLambda(),
    )

    // ---------------------------------------------------------------- the skip

    /**
     * A clean table **whose own other rows corroborate the reading** needs no second opinion.
     *
     * ## Why this fixture changed, and what the change records
     *
     * This test previously used `crackerCleanUnit` (`225738-513`), on the condition that both views
     * of Pass A read confidently and agreed. That condition is now insufficient and the reason is
     * measured: it was equally true of `085542-213`, whose reading is `12` where the package prints
     * `72`. A clean parse of a misread character is exactly as clean as a clean parse of a correct
     * one, so skipping on cleanliness removed the last chance to contradict the misread.
     *
     * The skip now requires [CrossColumnRatioCheck] — corroboration from rows the same OCR error
     * cannot have produced consistently. `crackerPunctuatedUnit` satisfies it (four supporting rows,
     * candidate ratio 0.3125 against a table median of 0.309); the old fixture does not, because its
     * tighter framing left only two nutrient rows carrying both columns.
     *
     * **That is not a regression.** `225738-513` now runs Strategy B, which is the correct and
     * honest cost of a label that cannot corroborate itself — see the companion test below, which
     * pins exactly that.
     */
    @Test
    fun `a cross-column verified pass A skips the second recognition`() {
        val recorder = Recorder()
        val result = resolve(ThirdSessionFixtures.crackerPunctuatedUnit(), recorder)

        assertEquals("Strategy B ran on a reading the table itself corroborates", 0, recorder.calls)
        assertTrue(
            "expected a skip status, got ${result.strategyB}",
            result.strategyB == SelectedTableResolution.StrategyBStatus.SKIPPED_CROSS_COLUMN_VERIFIED ||
                result.strategyB == SelectedTableResolution.StrategyBStatus.SKIPPED_RUNS_ALREADY_AGREE,
        )
    }

    /** And the reading it skipped on is the printed one, not merely *a* reading. */
    @Test
    fun `the skipped-on reading is the value the package prints`() {
        val result = resolve(ThirdSessionFixtures.crackerPunctuatedUnit(), Recorder())

        val outcome = result.outcome
        assertTrue("expected a resolved outcome, got $outcome", outcome is EvidenceResolver.Outcome.Resolved)
        val reading = (outcome as EvidenceResolver.Outcome.Resolved).reading
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        assertEquals(
            0,
            (reading as LabelReading.Confident).candidate.value.compareTo(java.math.BigDecimal("72")),
        )
    }

    /**
     * A clean reading the table **cannot** corroborate gets a second recognition.
     *
     * The deliberate cost of the change above, pinned so it cannot be quietly undone. `225738-513`
     * reads `72` correctly and its two Pass A views agree — under the old rule it skipped. It now
     * pays for Strategy B, because two parses of one recognition are one opinion and this label
     * offers nothing else.
     */
    @Test
    fun `a clean reading with too few corroborating rows still gets a second recognition`() {
        val recorder = Recorder()
        val result = resolve(ThirdSessionFixtures.crackerCleanUnit(), recorder)

        assertEquals(
            "an uncorroborated reading must get a second opinion, however cleanly it parsed",
            1,
            recorder.calls,
        )
        assertTrue(
            "expected Strategy B to have run, got ${result.strategyB}",
            result.strategyB != SelectedTableResolution.StrategyBStatus.SKIPPED_CROSS_COLUMN_VERIFIED,
        )
    }

    // ---------------------------------------------------------------- the run

    /**
     * **The safety direction.** A capture Pass A could not read must still get its second opinion —
     * that is the entire reason Strategy B exists, and skipping it here would turn a recoverable
     * scan into a dead end.
     */
    @Test
    fun `a pass A that read nothing still runs the second recognition`() {
        val recorder = Recorder()
        resolve(ThirdSessionFixtures.drinkWideFraming(), recorder)

        assertEquals("a NotFound pass A must get a second opinion", 1, recorder.calls)
    }

    @Test
    fun `a pass A whose value cells were declined still runs the second recognition`() {
        // `225632-622`: both carbohydrate cells lost their unit glyph to a digit, so the parser
        // declined them. A second recognition of the same pixels may read them correctly.
        val recorder = Recorder()
        resolve(ThirdSessionFixtures.drinkFusedHeaderPipe(), recorder)

        assertEquals(1, recorder.calls)
    }

    /**
     * A confident Pass A whose basis was never established does **not** take the strong path.
     *
     * A value the parser found and could not place on the label is exactly the case where another
     * look is worth the wait, and it is the case the app must never advance on regardless.
     */
    @Test
    fun `a confident reading with no basis is not treated as strong`() {
        val document = OcrDocument(
            width = 800,
            height = 400,
            elements = listOf(
                // A carbohydrate row with a value and no basis header anywhere on the label.
                OcrElement("Koolhydraten", OcrBox(60, 100, 220, 140), blockId = 0, lineId = 0),
                OcrElement("53,5", OcrBox(380, 100, 460, 140), blockId = 0, lineId = 0),
                OcrElement("g", OcrBox(468, 100, 490, 140), blockId = 0, lineId = 0),
            ),
        )
        val recorder = Recorder()
        val result = resolve(document, recorder)

        assertEquals(
            "a reading the parser could not place must get a second opinion",
            1,
            recorder.calls,
        )
        assertTrue(
            result.strategyB != SelectedTableResolution.StrategyBStatus.SKIPPED_CROSS_COLUMN_VERIFIED,
        )
    }

    // ---------------------------------------------------------------- the skip cannot suppress a conflict

    /**
     * The strong path never fires while any confident evidence disagrees.
     *
     * This is the property that separates it from the corroboration rule it sits beside. If a
     * conflicting reading is already present, the resolver must see the conflict, and the skip must
     * not remove the evidence that produces it.
     */
    @Test
    fun `contradicting live evidence prevents the strong-path skip`() {
        val document = ThirdSessionFixtures.crackerCleanUnit()
        val disagreeing = RecognitionEvidence(
            source = EvidenceSource.LIVE_STABLE_FRAME,
            report = NutritionParseReport(
                reading = LabelReading.Confident(
                    CarbCandidate(
                        sourceLine = "Koolhydraten 61,9 g",
                        label = "Koolhydraten",
                        value = java.math.BigDecimal("61.9"),
                        basis = app.justthecarbs.domain.NutritionBasis.PER_100_G,
                        score = NutritionParserThresholds.CONFIDENT_SCORE,
                        geometry = OcrBox(0, 0, 10, 10),
                        evidence = emptyList(),
                        column = NutritionColumnKind.PER_100_G,
                    ),
                ),
                diagnostics = emptyList(),
                servingCandidate = null,
            ),
            document = null,
        )

        val recorder = Recorder()
        SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            liveEvidence = disagreeing,
            recogniseRegion = recorder.asLambda(),
        )

        assertEquals(
            "a disagreement on the table must not be skipped past",
            1,
            recorder.calls,
        )
    }
}
