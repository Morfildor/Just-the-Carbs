package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two structural reasons [CrossColumnRatioCheck] could not reach a verdict on real labels,
 * and the boundaries of the fixes for them.
 *
 * ## The measurement this class exists for
 *
 * Across the 29 captures of `docs/Scan Evidence 3rd testr`, `automatic-verification` was `NONE` on
 * every one, and the cross-column half reported **`only 0 coherent row pairs; 3 needed`** on 19 of
 * the 20 captures that reached it. Zero pairs, not two — the check was not narrowly missing a
 * threshold, it was never obtaining two usable value columns to form a ratio from.
 *
 * Both causes are artifacts of how headers are typeset, not evidence that a table is unreadable.
 */
class CrossColumnEvidenceReachTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    private fun capture(suffix: String) =
        SeventeenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) }

    private fun columnsOf(document: OcrDocument): List<NutritionColumn> =
        ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

    /**
     * One printed column, read once per language, must not defeat the check.
     *
     * `20260904-134428-088` photographs a Turkish rice-flour box whose single per-100 g column is
     * headed in six languages. `ColumnClassifier` correctly emits one column per recognised header
     * phrase, and they land within 91 px of each other on a 1684 px frame — 5.4% of the width.
     */
    @Test
    fun `a multilingual header emits several per-hundred columns for one printed column`() {
        val document = capture("134428-088").passA()
        val perHundred = columnsOf(document).filter {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }

        assertTrue(
            "expected several per-100 columns, found ${perHundred.size}",
            perHundred.size > 1,
        )
        val centres = perHundred.map { it.centerX }
        assertTrue(
            "the six headers must sit close enough to be one printed column, spanned " +
                "${centres.max() - centres.min()} px",
            centres.max() - centres.min() <= document.width * 0.06,
        )
    }

    /**
     * The control, and the reason the collapse must be bounded by distance rather than by kind.
     *
     * `20260904-134552-198` photographs an Indomie packet printing **two separate tables** — one for
     * the noodles (27 g) and one for the bouillon (2,7 g). Both are genuinely per 100 g, and they
     * are different products. Merging them would invent a table that is not printed, and the
     * carbohydrate figure the user doses from would become whichever cell happened to bind first.
     */
    @Test
    fun `two different products' per-hundred columns stay far apart`() {
        val document = capture("134552-198").passA()
        val perHundred = columnsOf(document).filter {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }

        assertTrue(perHundred.size > 1)
        val centres = perHundred.map { it.centerX }
        assertTrue(
            "two products' tables must remain distinguishable, spanned " +
                "${centres.max() - centres.min()} px",
            centres.max() - centres.min() > document.width * 0.06,
        )
    }

    /**
     * A second value column whose *meaning* is unresolved still states a consistent ratio.
     *
     * `20260904-134233-470` prints `Ø/100 g` and `Ø/125 g`. There is no
     * [app.justthecarbs.domain.NutritionBasis] member meaning "per 125 g", so the second column is
     * emitted [NutritionColumnKind.UNKNOWN] — which is correct, and is the rule that closed the
     * 2.6x error. It is nonetheless a real printed column of real figures.
     */
    @Test
    fun `an off-basis serving column is emitted as a position with no meaning`() {
        val document = capture("134233-470").passA()
        val columns = columnsOf(document)

        assertEquals(
            1,
            columns.count { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertTrue(
            "the 125 g column must be present as UNKNOWN",
            columns.any { it.kind == NutritionColumnKind.UNKNOWN },
        )
    }

    /**
     * The ratio a table states is the same whichever column carries it.
     *
     * This is the whole justification for letting an `UNKNOWN` column act as the denominator: the
     * quantity is dimensionless and never leaves [CrossColumnRatioCheck]. Measured on the Baltic
     * crispbread, whose second column is headed `Ø/9 g` and resolves `UNKNOWN`, four rows agree at
     * the printed 9 g serving fraction and the carbohydrate row is one of them.
     *
     * Before the off-basis denominator existed this label reached `NotEnoughEvidence(0)` — it has
     * no `PER_SERVING` column at all, so there was nothing to divide by.
     */
    @Test
    fun `a table with an off-basis second column corroborates its own carbohydrate row`() {
        val document = capture("134917-744").passA()
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        val verdict = CrossColumnRatioCheck.check(document, candidate)

        assertTrue(
            "expected the table to corroborate its own row, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.Consistent,
        )
        assertTrue((verdict as CrossColumnRatioCheck.Verdict.Consistent).supportingRows >= 3)
    }

    /**
     * One printed per-100 column read once per language must not defeat the check.
     *
     * The corpora contain several such labels — the Hellmann's mayonnaise emits three `PER_100_ML`
     * columns spanning 1% of the frame, the Fanta three spanning under 1% — so the collapse is
     * reachable in practice. Those particular captures fail for unrelated reasons (a lost decimal
     * separator), which is why this is pinned directly rather than through a replay.
     *
     * Without the collapse this table reaches `NotEnoughEvidence(0)`: `singleOrNull` finds three
     * per-100 columns and refuses before a single pair is formed.
     */
    @Test
    fun `a table headed in three languages still corroborates its carbohydrate row`() {
        val document = OcrDocument(
            width = 1000,
            height = 500,
            elements = listOf(
                // One printed column, three recognised header phrases within 2% of the width.
                element("per 100 g", 360, 10, 500, 40),
                element("pour 100 g", 366, 45, 506, 75),
                element("pro 100 g", 372, 80, 512, 110),
                element("per portie", 690, 10, 830, 40),
                element("Energie", 20, 140, 200, 170),
                element("400", 390, 140, 470, 170),
                element("200", 710, 140, 790, 170),
                element("Vetten", 20, 190, 200, 220),
                element("10", 390, 190, 460, 220),
                element("5", 710, 190, 770, 220),
                element("Koolhydraten", 20, 240, 260, 270),
                element("20", 390, 240, 460, 270),
                element("10", 710, 240, 780, 270),
                element("Eiwitten", 20, 290, 200, 320),
                element("8", 390, 290, 450, 320),
                element("4", 710, 290, 770, 320),
            ),
        )
        val reading = NutritionTableParser.parseWithDiagnostics(document).reading
        assertTrue("fixture must produce a candidate, got $reading", reading is LabelReading.Confident)

        val perHundred = columnsOf(document).filter {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }
        assertTrue(
            "the fixture must emit several per-100 columns, or it pins nothing",
            perHundred.size > 1,
        )

        val verdict = CrossColumnRatioCheck.check(
            document,
            (reading as LabelReading.Confident).candidate,
        )

        assertTrue(
            "expected the collapsed column to let the table answer, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.Consistent,
        )
    }

    /**
     * A percentage column is never a second measurement of the nutrient.
     *
     * `%RI` cells are a fraction of a reference intake, not the nutrient restated, so admitting one
     * as the ratio denominator would compare a mass against a percentage and move the median every
     * other judgement rests on.
     */
    @Test
    fun `a reference percent column is never used as the second value column`() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per 100 g", 380, 10, 520, 40),
                element("%RI", 700, 10, 780, 40),
                element("Energie", 20, 60, 200, 90),
                element("200", 400, 60, 480, 90),
                element("10%", 700, 60, 780, 90),
                element("Vetten", 20, 110, 200, 140),
                element("10", 400, 110, 480, 140),
                element("14%", 700, 110, 780, 140),
                element("Koolhydraten", 20, 160, 260, 190),
                element("20", 400, 160, 480, 190),
                element("8%", 700, 160, 780, 190),
                element("Eiwitten", 20, 210, 200, 240),
                element("5", 400, 210, 480, 240),
                element("10%", 700, 210, 780, 240),
            ),
        )
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val reading = report.reading
        assertTrue("fixture must produce a candidate, got $reading", reading is LabelReading.Confident)

        val verdict = CrossColumnRatioCheck.check(
            document,
            (reading as LabelReading.Confident).candidate,
        )

        assertTrue(
            "a percent column must not supply the ratio, got $verdict",
            verdict is CrossColumnRatioCheck.Verdict.NotEnoughEvidence,
        )
    }
}
