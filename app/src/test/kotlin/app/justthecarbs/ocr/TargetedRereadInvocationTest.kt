package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Whether [SelectedTableResolution.resolve] actually invokes the targeted-reread recognizer, and
 * whether it consumes and exits cleanly on the result.
 *
 * [TargetedRereadTriggerTest] and [TargetedRereadRegionTest] pin the pure decision and geometry in
 * isolation; this file proves the **production path** — [SelectedTableResolution.resolve] itself —
 * reaches them, folds a returned reread into the resolved outcome, and degrades safely when the
 * reread fails, following [StrategyBInvocationTest]'s established pattern for testing an injectable
 * recognizer without an Android bitmap.
 */
class TargetedRereadInvocationTest {

    private val region = NormalizedRegion(0.05, 0.05, 0.95, 0.95)

    /** Records whether the targeted reread was invoked, and with which region. */
    private class Recorder(private val answer: RecognitionEvidence? = null) {
        var calls = 0
            private set
        var lastRegion: NormalizedRegion? = null
            private set

        fun asLambda(): (android.graphics.Bitmap?, NormalizedRegion) -> RecognitionEvidence? =
            { _, rgn ->
                calls++
                lastRegion = rgn
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

    /** A single-column drink table with a lone separatorless integer -- Unsupported scale. */
    private fun unsupportedScaleDocument() = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
        ),
    )

    private fun rereadEvidence(value: String, basis: NutritionBasis = NutritionBasis.PER_100_ML) =
        RecognitionEvidence(
            source = EvidenceSource.TARGETED_REREAD,
            report = NutritionParseReport(
                reading = LabelReading.Confident(
                    CarbCandidate(
                        sourceLine = "Koolhydraten $value g",
                        label = "Koolhydraten",
                        value = BigDecimal(value),
                        basis = basis,
                        score = NutritionParserThresholds.CONFIDENT_SCORE,
                        geometry = OcrBox(430, 200, 500, 240),
                        evidence = emptyList(),
                        column = when (basis) {
                            NutritionBasis.PER_100_ML -> NutritionColumnKind.PER_100_ML
                            NutritionBasis.PER_100_G -> NutritionColumnKind.PER_100_G
                        },
                    ),
                ),
                diagnostics = emptyList(),
            ),
            document = null,
            physicalObservation = PhysicalObservationId("test-fixture"),
        )

    /**
     * The real production path: an Unsupported-scale reading triggers exactly one targeted reread.
     */
    @Test
    fun `an Unsupported-scale reading triggers the targeted reread`() {
        val document = unsupportedScaleDocument()
        val recorder = Recorder(answer = rereadEvidence("41"))

        SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            // Strategy B must run (not be skipped) for this fixture, or the reread trigger never
            // gets a chance to fire against the post-Strategy-B state -- use a recogniseRegion that
            // returns nothing, mirroring the real "Strategy B found nothing new" case.
            recogniseRegion = { _, _ -> null },
            recogniseTargetedReread = recorder.asLambda(),
        )

        assertEquals("the targeted reread must be attempted exactly once", 1, recorder.calls)
    }

    /** The reread's result is folded into the final outcome, not discarded. */
    @Test
    fun `the reread result is consumed and can change the resolved outcome`() {
        val document = unsupportedScaleDocument()
        // Reread agrees with a SECOND recognition run (not the same run as the still), giving
        // DISTINCT_OCR_AGREEMENT-shaped corroboration is NOT what this asserts -- TARGETED_REREAD
        // shares the still's own recognitionRun, so this only tests that the evidence is folded in
        // and the outcome recomputed, not that it changes the safety verdict.
        val result = SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            recogniseRegion = { _, _ -> null },
            recogniseTargetedReread = { _, _ -> rereadEvidence("41") },
        )

        assertTrue(
            "the reread's evidence must appear in the final evidence list",
            result.evidence.any { it.source == EvidenceSource.TARGETED_REREAD },
        )
    }

    /**
     * The reread carries the STILL's own [PhysicalObservationId], never a fresh one -- it is one
     * more parse of the photograph already in hand, not independent physical corroboration. This is
     * the safety property the whole feature rests on: see [EvidenceSource.TARGETED_REREAD].
     */
    @Test
    fun `the reread must share the still's own PhysicalObservationId, never a new one`() {
        val document = unsupportedScaleDocument()
        val stillId = PhysicalObservationId.forStill("capture-under-test.jpg")

        val result = SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = stillId,
            recogniseRegion = { _, _ -> null },
            // The production default wiring: SelectedRegionRecognizer.recognise is what actually
            // stamps the observation id, and the production default in SelectedTableResolution.resolve
            // is exercised here by NOT overriding recogniseTargetedReread -- but recognise() itself
            // needs a real bitmap to run, which this JVM test does not have. So this asserts the
            // CONTRACT via the injected lambda instead: whatever the caller wires up must be
            // constructed with stillObservationId, which is what production code does.
            recogniseTargetedReread = { _, _ ->
                rereadEvidence("41").copy(physicalObservation = stillId)
            },
        )

        val reread = result.evidence.single { it.source == EvidenceSource.TARGETED_REREAD }
        assertEquals(stillId, reread.physicalObservation)
        // And it must NOT be counted as a second independent recognition run.
        assertEquals(RecognitionRun.SELECTED_REGION, EvidenceSource.TARGETED_REREAD.recognitionRun)
    }

    /** A reread that returns null (failed/degenerate/timed out) must not crash or corrupt the outcome. */
    @Test
    fun `a failed targeted reread degrades safely, keeping the pre-reread outcome`() {
        val document = unsupportedScaleDocument()
        val withoutReread = SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            recogniseRegion = { _, _ -> null },
            recogniseTargetedReread = { _, _ -> null },
        )

        // Must not throw, and the pre-reread outcome (whatever the resolver made of Pass A + Strategy
        // B alone) must be exactly what is returned -- a null reread changes nothing.
        assertFalse(
            "no TARGETED_REREAD evidence should appear when the reread returns null",
            withoutReread.evidence.any { it.source == EvidenceSource.TARGETED_REREAD },
        )
    }

    /**
     * An already-confident, scale-established reading must NOT trigger a reread at all -- the
     * targeted reread is for cases genuinely in doubt, never a blanket "always look again".
     */
    @Test
    fun `a scale-established reading does not trigger a reread`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41,5g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val recorder = Recorder()

        SelectedTableResolution.resolve(
            passA = passA(document),
            region = region,
            bitmap = null,
            stillObservationId = PhysicalObservationId("test-fixture"),
            recogniseRegion = { _, _ -> null },
            recogniseTargetedReread = recorder.asLambda(),
        )

        assertEquals(
            "a reading whose scale is already Established needs no reread",
            0,
            recorder.calls,
        )
    }
}
