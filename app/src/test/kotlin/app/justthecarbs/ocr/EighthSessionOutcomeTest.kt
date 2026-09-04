package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins what the eighth-session captures **actually did on the phone**, so the fixtures are proven to
 * carry the failure this pass repairs.
 *
 * This class asserts the device outcome, not the desired one. Without it a later test asserting that
 * `12` never reaches a confirmation card could pass because the fixture never produced `12` at all —
 * the fixture trap this repo has recorded four times (the Dutch header, the soft-keyboard geometry
 * test, the fifth session's synthetic `per portion` header, and the sixth session's merged row).
 *
 * **The parse itself is unchanged by this pass and must stay unchanged.** `12` is a well-formed
 * number on a correctly classified `TOTAL_CARBOHYDRATE` row under a correctly resolved `PER_100_G`
 * column, and every content-based guard is right to accept it — a parser that refused `12 g / 100 g`
 * would refuse a legitimate label. What this pass changes is what the app *does* with an unverified
 * reading whose scale nothing established, never whether the parser produces it.
 */
class EighthSessionOutcomeTest {

    private fun reading(document: OcrDocument): LabelReading =
        NutritionTableInterpreter.interpret(document).reading

    private fun confident(document: OcrDocument): CarbCandidate {
        val reading = reading(document)
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate
    }

    // ---------------------------------------------------------------- the P0

    @Test
    fun `the red label parses 12 confidently under a resolved per-100 g column`() {
        val candidate = confident(EighthSessionFixtures.redLabelTwelve())
        assertEquals(BigDecimal("12.0"), candidate.value)
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertEquals(NutritionColumnKind.PER_100_G, candidate.column)
    }

    @Test
    fun `the 12 candidate's row carries exactly one value cell, so nothing pairs with it`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val candidate = confident(document)
        // The structural fact behind the P0: the row states the figure once, under one column, so
        // there is no sibling to share a scale with. The old rule reported that as `Established`;
        // it is now `Unsupported`, which is the same observation with an honest name.
        val verdict = ScaleAmbiguity.check(document, candidate)
        assertTrue(
            "expected no pair to be found, got $verdict",
            verdict is ScaleAmbiguity.Verdict.Unsupported,
        )
        val row = LogicalRowBuilder.build(document).first { it.text.contains("12g") }
        val valueCells = row.elements.count { Regex("^\\d").containsMatchIn(it.text.trim()) }
        assertEquals("the row must carry exactly one value cell", 1, valueCells)
    }

    @Test
    fun `the 12 token carries no decimal separator`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val row = LogicalRowBuilder.build(document).first { it.text.contains("12g") }
        val token = row.elements.first { it.text.trim() == "12g" }
        assertTrue(
            "the P0 depends on the separator being absent; got '${token.text}'",
            !Regex("\\d[.,]\\d").containsMatchIn(token.text),
        )
    }

    @Test
    fun `no red-label capture recognises the printed 7 point 2`() {
        // The whole P1 question rests on this: the printed value survives in none of the four
        // recognitions, so no parser rule can honestly derive it and none may invent it.
        listOf(
            "212952-487" to EighthSessionFixtures.redLabelFusedValue(),
            "213005-691" to EighthSessionFixtures.redLabelTwelve(),
            "213014-298" to EighthSessionFixtures.redLabelSevenTwoFour(),
            "213026-546" to EighthSessionFixtures.redLabelFusedValueSecond(),
        ).forEach { (name, document) ->
            val carbRows = LogicalRowBuilder.build(document)
                .filterIndexed { index, _ ->
                    RowClassifier.classifyAll(LogicalRowBuilder.build(document))[index] ==
                        NutritionRowKind.TOTAL_CARBOHYDRATE
                }
            carbRows.forEach { row ->
                assertTrue(
                    "$name: a total-carbohydrate row unexpectedly contains 7,2 — '${row.text}'",
                    !row.text.contains("7,2") && !row.text.contains("7.2"),
                )
            }
        }
    }

    // ------------------------------------------------- the three other red captures

    @Test
    fun `the two fused captures read nothing at all`() {
        assertEquals(LabelReading.NotFound, reading(EighthSessionFixtures.redLabelFusedValue()))
        assertEquals(LabelReading.NotFound, reading(EighthSessionFixtures.redLabelFusedValueSecond()))
    }

    @Test
    fun `the 724 capture reads nothing — unit accompaniment declines a bare number`() {
        assertEquals(LabelReading.NotFound, reading(EighthSessionFixtures.redLabelSevenTwoFour()))
    }

    // ---------------------------------------------------------------- the controls

    @Test
    fun `the correct drink capture keeps its decimal separator, which is real scale evidence`() {
        val document = EighthSessionFixtures.drinkConfirmed()
        val candidate = confident(document)
        assertEquals(BigDecimal("0.5"), candidate.value)
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_ML, candidate.basis)
        val verdict = ScaleAmbiguity.check(document, candidate)
        assertTrue("expected established-by-separator, got $verdict", verdict is ScaleAmbiguity.Verdict.Established)
        assertTrue(
            "the reason must be the separator, not the absence of a pair: $verdict",
            (verdict as ScaleAmbiguity.Verdict.Established).reason.contains("decimal separator"),
        )
    }

    @Test
    fun `the cracker parses 72 with its separator intact`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        val candidate = confident(document)
        assertEquals(BigDecimal("72.0"), candidate.value)
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        // Its own token carries the separator, so its scale is stated by the recognizer. That is
        // what must keep it advancing, independently of the cross-column route.
        val verdict = ScaleAmbiguity.check(document, candidate)
        assertTrue(
            "the cracker's scale must stay established by its own separator, got $verdict",
            verdict is ScaleAmbiguity.Verdict.Established &&
                verdict.reason.contains("decimal separator"),
        )
    }

    /**
     * The device verified this capture through [CrossColumnRatioCheck] and logged
     * `CROSS_COLUMN (support=3, median=0.316, candidate=0.313)`.
     *
     * **This fixture does not reproduce that**, and the reason is recorded rather than asserted
     * around: the device ran the check against the *filtered* document produced by the confirmed
     * rectangle, while this fixture is the raw full-frame Pass A recognition. In the full frame
     * ML Kit splits the declaration across two reconstructed rows — `Koolhydraten, waarvan/Glucides,
     * 9%` and `dont/Carbohydrate, of which: 72,0 g 22,5 g` — and the candidate's geometry matches the
     * first, whose only number is the reference percentage. So no pair exists for the candidate's own
     * row and the check reports `NotEnoughEvidence`.
     *
     * Asserted as measured, so nobody later reads the absence of `CROSS_COLUMN` here as a regression
     * in the ratio check. The cracker's protection in this pass comes from its decimal separator, and
     * that is what the test above pins.
     */
    @Test
    fun `the full-frame cracker document cannot run the cross-column route, and that is the fixture not the check`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        val candidate = confident(document)
        val check = CrossColumnRatioCheck.check(document, candidate)
        assertTrue(
            "expected NotEnoughEvidence on the unfiltered document, got $check",
            check is CrossColumnRatioCheck.Verdict.NotEnoughEvidence,
        )
    }

    @Test
    fun `the recovery drink capture reads nothing from pass A`() {
        assertEquals(LabelReading.NotFound, reading(EighthSessionFixtures.drinkRecovery()))
    }

    @Test
    fun `the conflicted drink capture parses 0 point 5 from pass A`() {
        val candidate = confident(EighthSessionFixtures.drinkConflicted())
        assertEquals(BigDecimal("0.5"), candidate.value)
    }
}
