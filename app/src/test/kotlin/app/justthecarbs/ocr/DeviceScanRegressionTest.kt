package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The confident-wrong readings produced by real scans on a physical device (Samsung SM-S928B,
 * 2026-08-25), reconstructed from the recorded evidence in `docs/scan evidence/`.
 *
 * Every fixture here is built from an actual recorded recognition — the element texts and their box
 * coordinates are transcribed from the `diagnostics.txt` of the scan named in each test, scaled down
 * only where the arithmetic is unaffected. These are not invented adversarial inputs: each one is a
 * photograph a user actually took, of a product they actually wanted to eat.
 *
 * All three failures shared a shape worth stating plainly, because it is the shape any future
 * regression will take: **no stage was individually wrong**. The row classifier, the validator, the
 * column classifier and the prose reader each behaved exactly as documented, and the wrong number
 * came out of how their outcomes composed. Tests that exercise one stage in isolation cannot catch
 * that, which is why these run the whole parser.
 */
class DeviceScanRegressionTest {

    private fun value(report: NutritionParseReport): BigDecimal? =
        (report.reading as? LabelReading.Confident)?.candidate?.value

    private fun confidentValue(report: NutritionParseReport): BigDecimal =
        value(report) ?: throw AssertionError("expected a confident reading, got ${report.reading}")

    // ---------------------------------------------------------------- A: malformed per-100 value

    /**
     * Scan `20260825-123828-500`. The package prints `86 g` per 100 g and `22 g` per 25 g portion.
     *
     * ML Kit read the printed `86g` as **`869`** — a `g` recognised as a `9`. The validator refused
     * 869 as impossible per 100 g, which is correct and must not change. What followed was the
     * defect: with no usable per-100 cell the prose fallback ran, bound the term `Kohlenhydrate` to
     * the **per-portion** cell in the next column, and reported `CONFIDENT 22.0 PER_100_G`.
     *
     * 22 g is a real number printed on the package, which is what makes it dangerous — it is
     * plausible, it is nearly a quarter of the true figure, and nothing about the result reveals that
     * it describes a 25 g portion rather than 100 g.
     */
    private fun malformedPerHundredLabel(): OcrDocument {
        val label = SlopedLabel(slopePercent = -3.0, glyphHeight = 60)
        // Header: the two value columns, per 100 g and per portie 25 g.
        label.row(1100, "per" to 841..899, "100" to 914..975, "g" to 985..1004, block = 8, line = 0)
        label.row(1160, "per" to 1159..1208, "portie" to 1216..1324, "25g" to 1330..1400, block = 16, line = 0)
        // Nutrient rows, each with its per-100 cell and its per-portion cell.
        label.row(1380, "Vetten/Matieres" to 204..511, block = 2, line = 0)
        label.row(1390, "13g" to 820..925, block = 10, line = 0)
        label.row(1580, "davon" to 209..321, "gesattigte" to 335..535, block = 3, line = 2)
        label.row(1588, "12g" to 818..911, block = 11, line = 0)
        // The carbohydrate row: term on the left, the CORRUPTED per-100 cell, the per-portion cell.
        label.row(1730, "Kohlenhydrate:" to 214..511, block = 4, line = 1)
        label.row(1735, "869" to 853..917, block = 12, line = 0)
        label.row(1740, "22" to 1150..1210, "g" to 1220..1245, block = 17, line = 0)
        label.row(1877, "davon" to 216..335, "Zucker:" to 346..489, block = 5, line = 1)
        label.row(1877, "54g" to 851..915, block = 13, line = 0)
        label.row(1959, "Eiwitten/Proteines:" to 222..714, block = 6, line = 0)
        label.row(1954, "0,99" to 852..926, block = 14, line = 0)
        return label.document(width = 1684, height = 3648)
    }

    @Test
    fun `a per-100 cell corrupted into an implausible value never resolves to the serving column`() {
        val report = NutritionTableParser.parseWithDiagnostics(malformedPerHundredLabel())

        assertNotEquals(
            "the per-portion figure was promoted to a per-100 reading",
            BigDecimal("22"),
            value(report)?.stripTrailingZeros(),
        )
        // 869 itself must never surface either — it is not a possible per-100 carbohydrate figure.
        assertNotEquals(BigDecimal("869"), value(report)?.stripTrailingZeros())
    }

    @Test
    fun `the rejected per-100 cell blocks the prose fallback rather than inviting it`() {
        val report = NutritionTableParser.parseWithDiagnostics(malformedPerHundredLabel())

        assertTrue(
            "the table's own answer was rejected, so no fallback may substitute a neighbour: " +
                report.diagnostics.joinToString { "${it.stage}=${it.message}" },
            report.diagnostics.any {
                it.stage == "prose" && it.message.contains("rejected as implausible")
            },
        )
        assertNull("no serving value may be promoted into the reading", value(report))
    }

    // ------------------------------------------------------------------- B: merged total + child

    /**
     * Scan `20260825-123841-535`, and the same package again in `20260825-124321-395`.
     *
     * Row reconstruction welded the printed `Kohlenhydrate … 86 g` line to the `waarvan suikers …`
     * line beneath it. [RowClassifier] then typed the merged row `CARBOHYDRATE_CHILD` by its
     * unconditional child-exclusion rule — correctly, on the text it was given — and the row was
     * discarded, taking the correct `86` with it.
     *
     * The explicit total anchor `Kohlenhydrate:` precedes `86g`, so reading order says plainly which
     * clause owns that number. [MergedTotalRowRecovery] reads it.
     */
    private fun mergedTotalAndChildLabel(): OcrDocument {
        val label = SlopedLabel(slopePercent = -3.0, glyphHeight = 60)
        label.row(1100, "per" to 839..899, "100g" to 912..1006, block = 10, line = 0)
        label.row(1380, "Vetten/Matieres" to 173..494, block = 4, line = 0)
        label.row(1381, "1,3" to 835..879, "g" to 891..910, block = 13, line = 0)
        // One reconstructed row carrying BOTH nutrient names and BOTH values.
        label.row(
            1720,
            "Kohlenhydrate:" to 175..484,
            "86g" to 831..906,
            "waarvan" to 1000..1180,
            "suikers/dont" to 1190..1420,
            block = 6,
            line = 1,
        )
        label.row(1881, "davon" to 175..297, "ZucRer:" to 311..456, block = 7, line = 1)
        label.row(1881, "54g" to 834..901, block = 16, line = 0)
        return label.document(width = 1684, height = 3648)
    }

    @Test
    fun `a total declaration printed before a child term on the same row is still read`() {
        val report = NutritionTableParser.parseWithDiagnostics(mergedTotalAndChildLabel())

        assertEquals(
            "the total printed ahead of the child term was lost",
            BigDecimal("86"),
            confidentValue(report).stripTrailingZeros(),
        )
        assertEquals(NutritionBasis.PER_100_G, (report.reading as LabelReading.Confident).candidate.basis)
    }

    @Test
    fun `recovering a merged row cannot reach the child value that follows it`() {
        val recovered = MergedTotalRowRecovery.recover(
            LogicalRowBuilder.build(mergedTotalAndChildLabel())
                .single { it.text.contains("Kohlenhydrate") },
        ) ?: throw AssertionError("expected the leading total span to be recovered")

        assertTrue("the child term must not survive into the span", !recovered.text.contains("waarvan"))
        assertTrue("the child term must not survive into the span", !recovered.text.contains("suikers"))
        assertTrue("the total term is the span's own anchor", recovered.text.contains("Kohlenhydrate"))
    }

    /**
     * The bread label from `RealImageOcrTest` (fixture 6), whose declaration is one running sentence:
     * `…onverzadigde vetzuren 6,4 g, koolhydraten 46g,waarvan suikers 1,0 g`.
     *
     * Two versions of [MergedTotalRowRecovery] failed on this row and both are pinned here, because
     * each produced a different wrong answer on real recognition:
     *
     * 1. Bounding the span only on the right kept the **fat** clause's `6,4` and dropped the
     *    carbohydrate's own `46` — in running text the value *follows* its term — and the parser
     *    confidently reported 6.4 g.
     * 2. Bounding both sides then yielded a span containing the word `koolhydraten` and no number at
     *    all, because ML Kit fuses the value to the next clause as `46g,waarvan`. That empty span
     *    still presented as a total row, so the interpreter stopped and the prose stage that reads
     *    this label correctly was never reached.
     *
     * The rule that satisfies both: recover only a span that is bounded on both sides **and actually
     * contains a value**. Here that means recovering nothing and letting the prose reader do its job.
     */
    @Test
    fun `a fused prose value yields no recovered span, leaving the row to the prose reader`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 30)
        label.row(
            400,
            "ren" to 100..160,
            "6,4" to 170..230,
            "g," to 240..270,
            "koolhydraten" to 280..520,
            // As the recognizer actually returns it: the value welded to the child clause.
            "46g,waarvan" to 530..760,
            "suikers" to 770..880,
            "1,0" to 890..940,
            "g." to 950..980,
        )
        val row = LogicalRowBuilder.build(label.document(width = 1125, height = 1320)).single()

        assertNull(
            "a value-less span must not be presented as a recovered total row",
            MergedTotalRowRecovery.recover(row),
        )
    }

    /** The same rule's positive half: a genuine table row keeps only its own clause. */
    @Test
    fun `a recovered span excludes the preceding nutrient's value as well as the child's`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 30)
        label.row(
            400,
            "vetzuren" to 100..260,
            "6,4" to 270..330,
            "koolhydraten" to 340..580,
            "46" to 590..650,
            "waarvan" to 660..800,
            "suikers" to 810..920,
            "1,0" to 930..980,
        )
        val row = LogicalRowBuilder.build(label.document(width = 1125, height = 1320)).single()

        val recovered = MergedTotalRowRecovery.recover(row)
            ?: throw AssertionError("expected the carbohydrate clause to be recovered")

        assertTrue("the preceding nutrient's value must not survive", !recovered.text.contains("6,4"))
        assertTrue("the child's value must not survive", !recovered.text.contains("1,0"))
        assertTrue("the total's own value must survive", recovered.text.contains("46"))
    }

    @Test
    fun `a child row with no leading total declaration stays excluded`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 40)
        // Sugars alone — the row RowClassifier exists to refuse. Nothing may recover it.
        label.row(200, "waarvan" to 100..300, "suikers:" to 310..520, "54g" to 800..900)
        val row = LogicalRowBuilder.build(label.document(width = 1200, height = 600)).single()

        assertNull(
            "a row naming only a child nutrient must never yield a total span",
            MergedTotalRowRecovery.recover(row),
        )
    }

    // --------------------------------------------------------------------------- C: unsafe zero

    /**
     * Scan `20260825-124043-922`. The package prints roughly `5,00 g` of carbohydrate; ML Kit
     * returned the token **`b,00`**, the leading `5` recognised as a `b`.
     *
     * `NUMBER` matched the `00` after the separator and the guard that rejects digits-inside-a-word
     * only inspected the character immediately before the match — a comma — so the parser reported
     * **`CONFIDENT 0.0 g`**. A false zero is the worst available outcome: it is exactly what a
     * carbohydrate-free food legitimately reads, so nothing downstream can doubt it.
     */
    /**
     * The value cells sit under the `per 100 g` header, as they do on a printed table — the parser
     * will not bind a cell to a column it is not aligned with, and a fixture that ignored that would
     * be testing nothing.
     */
    private fun corruptedZeroLabel(): OcrDocument {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 60)
        label.row(1200, "per" to 900..980, "100" to 990..1060, "g" to 1070..1100, block = 8, line = 0)
        label.row(1400, "Vetten" to 200..500, block = 1, line = 0)
        label.row(1400, "3,2" to 960..1040, block = 9, line = 0)
        label.row(1706, "koolhydraten," to 200..700, block = 2, line = 0)
        label.row(1706, "b,00" to 956..1070, block = 10, line = 0)
        label.row(1900, "Eiwitten" to 200..520, block = 3, line = 0)
        label.row(1900, "1,1" to 960..1040, block = 11, line = 0)
        return label.document(width = 1684, height = 3648)
    }

    @Test
    fun `a carbohydrate token whose leading digit was misread as a letter is not a zero`() {
        val report = NutritionTableParser.parseWithDiagnostics(corruptedZeroLabel())

        assertNotEquals(
            "a corrupted token was reported as a confident zero",
            BigDecimal.ZERO,
            value(report)?.stripTrailingZeros(),
        )
    }

    @Test
    fun `a genuine zero carbohydrate declaration is still accepted`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 60)
        // The basis phrase is its own recognized row above the value column, as on a printed table.
        label.row(1200, "per" to 900..980, "100" to 990..1060, "g" to 1070..1100, block = 8, line = 0)
        label.row(1400, "Vetten" to 200..500, block = 1, line = 0)
        label.row(1400, "0" to 960..1000, "g" to 1010..1040, block = 9, line = 0)
        // A clean, uncorrupted zero — sparkling water, a diet drink, an oil.
        label.row(1706, "koolhydraten" to 200..700, block = 2, line = 0)
        label.row(1706, "0" to 960..1000, "g" to 1010..1040, block = 10, line = 0)
        label.row(1900, "Eiwitten" to 200..520, block = 3, line = 0)
        label.row(1900, "0" to 960..1000, "g" to 1010..1040, block = 11, line = 0)

        val report = NutritionTableParser.parseWithDiagnostics(label.document(width = 1684, height = 3648))

        assertEquals(
            "a legitimate zero-carbohydrate product must still read",
            BigDecimal.ZERO,
            confidentValue(report).stripTrailingZeros(),
        )
    }
}
