package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Records why the corroborating table must be the confident pass's own, and what was measured when
 * that was widened.
 *
 * ## The idea, and why it looked necessary
 *
 * `docs/Scan Evidence 3rd testr/20260904-134233-470` photographs a Lidl yoghurt printing `Ø/100 g`
 * beside `Ø/125 g`, and every printed row really does state the ratio 1.25. Only the
 * selected-region pass read the value confidently, and that pass recognises a **crop** whose top
 * edge cut the header band — so its own document resolves one column and can form no pair, while
 * the full-frame recognition of the same photograph resolves both. It reads like corroborating
 * evidence sitting unused in the bundle.
 *
 * ## What was measured
 *
 * The widening was implemented — translating the crop-local candidate into source space through
 * [RecognitionEvidence.sourceSpaceGeometry] and asking the full-frame table. The translation is
 * exact: the candidate lands on the full frame's `TOTAL_CARBOHYDRATE` row at **1.000** vertical
 * overlap. The table still cannot answer, and the reason is in the cells rather than the plumbing:
 *
 * ```
 * Energie      503 | 152    <- pairs with the neighbouring kcal, not with 629
 * Vetten      10,0 | -      <- reconstruction put 12,5 on a different row
 * Koolhydraten 3,2 | 4,0    = 1.25, the only coherent pair
 * Sugars       3,2 | 5,8    <- a genuine misread
 * Protein     4,69 | 0,13   <- picks up the salt value
 * ```
 *
 * One pair against three. So the widening bought **nothing on any capture in this corpus**, and it
 * was removed rather than kept as machinery that might pay off later. This class keeps the
 * measurement executable so the idea is not rebuilt on the strength of how plausible it sounds.
 *
 * The general point, which is the transferable one: *a document holding the right columns is not
 * the same as a document holding the right pairs.* Column resolution and row reconstruction fail
 * independently, and this capture fails at the second.
 */
class CorroboratingViewTest {

    private fun capture(suffix: String) =
        SeventeenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) }

    /**
     * The asymmetry that made the idea look promising: the confident pass lacks a column the other
     * view of the same photograph resolved.
     */
    @Test
    fun `the confident pass lacks the second column that another view of the frame resolved`() {
        val capture = capture("134233-470")

        val cropColumns = ColumnClassifier.classify(
            LogicalRowBuilder.build(capture.strategyB()),
            capture.strategyB().width,
        )
        val frameColumns = ColumnClassifier.classify(
            LogicalRowBuilder.build(capture.passA()),
            capture.passA().width,
        )

        assertTrue(
            "the crop must hold no off-basis column, or there is nothing to recover",
            cropColumns.none { it.kind == NutritionColumnKind.UNKNOWN },
        )
        assertTrue(
            "the full frame must hold the 125 g column",
            frameColumns.any { it.kind == NutritionColumnKind.UNKNOWN },
        )
    }

    /**
     * And the reason widening does not help: with the candidate correctly translated onto the
     * richer table, that table still yields one coherent pair.
     *
     * This is the assertion that falsifies the idea, so it deliberately performs the translation
     * itself rather than trusting prose.
     */
    @Test
    fun `the richer view still cannot supply three coherent pairs`() {
        val capture = capture("134233-470")
        val crop = SelectedRegionCrop.PixelRect(0, 671, 1684, 2305)
        val cropReading = NutritionTableParser.parseWithDiagnostics(capture.strategyB()).reading
        val candidate = (cropReading as LabelReading.Confident).candidate
        val translated = candidate.copy(
            geometry = SelectedRegionCrop.toSourceSpace(candidate.geometry, crop),
        )
        val frame = capture.passA()

        // The translation itself is exact — the candidate lands on the right row.
        val matched = LogicalRowBuilder.build(frame)
            .maxByOrNull { it.box.verticalOverlapRatio(translated.geometry) }!!
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(matched))

        val verdict = CrossColumnRatioCheck.check(frame, translated)
        assertTrue(
            "expected the richer table to fall short of three pairs, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.NotEnoughEvidence,
        )
    }

    /**
     * A contradiction is still final, whichever view produces it.
     *
     * `134539-493` photographs two different products' tables and its confident passes disagree, so
     * nothing may verify it.
     */
    @Test
    fun `a capture whose passes disagree is never verified`() {
        val result = SeventeenthSessionReplay.replay(capture("134539-493"))

        assertEquals(AutomaticVerification.Route.NONE, result.verification.route)
        assertTrue(
            "a disagreement must not advance",
            result.action != ScanPresentationDecision.Action.AUTO_ADVANCE,
        )
    }

    /**
     * Verification never invents a reading: every advance still takes its value from a confident
     * pass of its own.
     */
    @Test
    fun `no capture advances without a confident pass of its own`() {
        SeventeenthSessionReplay.replayAll()
            .filter { it.action == ScanPresentationDecision.Action.AUTO_ADVANCE }
            .forEach { result ->
                assertTrue(
                    "${result.capture.bundle} advanced with no confident reading",
                    AutomaticScanAdvance.confidentReading(result.outcome) != null,
                )
            }
    }
}
