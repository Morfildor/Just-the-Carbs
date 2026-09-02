package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The nine captures of the 2026-09-01 third phone session, with the outcome each one must produce.
 *
 * Every fixture is the device's own recognition, complete and geometry-preserving — see
 * [ThirdSessionFixtures]. The required outcomes come from the evidence bundles and the screen
 * recording taken alongside them.
 *
 * | fixture      | required                                                    |
 * |--------------|-------------------------------------------------------------|
 * | `225530-249` | `0.5/100 ml` or safe labelled recovery; never `1.3/100 ml`   |
 * | `225617-066` | automatic `0.5/100 ml`                                       |
 * | `225632-622` | correct, or safe basis-aware recovery                        |
 * | `225654-501` | never bind `13g` to per 100 ml; prefer `0.5/100 ml`          |
 * | `225720-700` | automatic `72/100 g`; no `9%` candidate                      |
 * | `225738-513` | preserve automatic `72/100 g`                                |
 * | `225752-375` | `6 g/18 g serving`, safely normalized or retained            |
 * | `225813-635` | as above; no fibre or %DV candidate                          |
 * | `225829-154` | automatic `59.2/100 g` with a separate 9 g anchor            |
 */
class ThirdSessionRegressionTest {

    private fun read(document: OcrDocument) = NutritionTableInterpreter.interpret(document).reading

    private fun confident(reading: LabelReading): CarbCandidate {
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate
    }

    private fun columnsOf(document: OcrDocument) =
        ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

    // =============================================================== the green drink, four framings

    /**
     * THE safety property of this whole pass, asserted across every drink capture.
     *
     * The package prints `0,5 g per 100 ml` and `1,3 g per 250 ml`. A device recording produced
     * `1.3 g carbs / 100 ml` in Quick Calculation — through the *recovery* path, after the automatic
     * path had been fixed. Neither route may reach it.
     */
    @Test
    fun `no drink capture ever reports the 250 ml figure as a per-100-ml reading`() {
        val forbidden = listOf(BigDecimal("1.3"), BigDecimal("13"))

        listOf(
            "225530-249" to ThirdSessionFixtures.drinkWideFraming(),
            "225617-066" to ThirdSessionFixtures.drinkAutomaticConfident(),
            "225632-622" to ThirdSessionFixtures.drinkFusedHeaderPipe(),
            "225654-501" to ThirdSessionFixtures.drinkTruncatedUnitHeader(),
        ).forEach { (name, document) ->
            // The automatic path.
            when (val reading = read(document)) {
                is LabelReading.Confident -> assertFalse(
                    "$name read ${reading.candidate.value} ${reading.candidate.basis}",
                    forbidden.any { it.compareTo(reading.candidate.value) == 0 },
                )
                is LabelReading.Ambiguous -> reading.candidates.forEach { candidate ->
                    assertFalse(
                        "$name offered ${candidate.value} ${candidate.basis} as an interpretation",
                        forbidden.any { it.compareTo(candidate.value) == 0 },
                    )
                }
                LabelReading.NotFound -> Unit
            }

            // The recovery path — the one the recording actually went down.
            RecoveryCandidates.of(document).forEach { candidate ->
                val perHundred = candidate.reading.normalizedToPerHundred()
                if (perHundred != null) {
                    assertFalse(
                        "$name's recovery offers '${candidate.label}', which becomes " +
                            "${perHundred.amount.toPlainString()} per 100 ml",
                        forbidden.any { it.compareTo(perHundred.amount) == 0 },
                    )
                }
            }
        }
    }

    @Test
    fun `every drink capture keeps the 250 ml column separate from the per-100 column`() {
        listOf(
            "225530-249" to ThirdSessionFixtures.drinkWideFraming(),
            "225617-066" to ThirdSessionFixtures.drinkAutomaticConfident(),
            "225632-622" to ThirdSessionFixtures.drinkFusedHeaderPipe(),
            "225654-501" to ThirdSessionFixtures.drinkTruncatedUnitHeader(),
        ).forEach { (name, document) ->
            val columns = columnsOf(document)
            val perHundred = columns.filter { it.kind == NutritionColumnKind.PER_100_ML }
            val offBasis = columns.filter { it.kind == NutritionColumnKind.UNKNOWN }

            assertEquals("$name should resolve exactly one per-100-ml column", 1, perHundred.size)
            assertTrue(
                "$name lost its 250 ml column; header was '${perHundred.first().headerText}'",
                offBasis.isNotEmpty(),
            )
            assertTrue(
                "$name's per-100 anchor at ${perHundred.first().centerX} is not left of its " +
                    "250 ml anchor at ${offBasis.first().centerX}",
                perHundred.first().centerX < offBasis.first().centerX,
            )
            assertFalse(
                "$name's per-100 header '${perHundred.first().headerText}' still contains the 250",
                perHundred.first().headerText.contains("250"),
            )
        }
    }

    /** `225654-501`: the capture whose fused header let `13g` inherit the per-100 basis. */
    @Test
    fun `the truncated-unit capture reads its printed per-100-ml value`() {
        val candidate = confident(read(ThirdSessionFixtures.drinkTruncatedUnitHeader()))

        assertEquals(0, candidate.value.compareTo(BigDecimal("0.5")))
        assertEquals(NutritionBasis.PER_100_ML, candidate.basis)
    }

    /** `225617-066`: already correct on the device, and must stay so. */
    @Test
    fun `the automatic drink capture keeps its confident reading`() {
        val candidate = confident(read(ThirdSessionFixtures.drinkAutomaticConfident()))

        assertEquals(0, candidate.value.compareTo(BigDecimal("0.5")))
        assertEquals(NutritionBasis.PER_100_ML, candidate.basis)
    }

    /**
     * `225632-622`: both value cells lost their unit glyph to a digit (`0,5 g` → `0.59`).
     *
     * A refusal is the required outcome, and it must reach recovery rather than offering the
     * corrupted token: `0.59` is a perfectly ordinary carbohydrate quantity, so nothing downstream
     * could doubt it.
     */
    @Test
    fun `the corrupted-unit capture refuses rather than offering the damaged token`() {
        val document = ThirdSessionFixtures.drinkFusedHeaderPipe()

        assertEquals(LabelReading.NotFound, read(document))
        RecoveryCandidates.of(document).forEach {
            assertFalse(
                "the corrupted token 0.59 was offered as '${it.label}'",
                it.reading.amount.compareTo(BigDecimal("0.59")) == 0,
            )
        }
    }

    /**
     * `225530-249`: the wide framing. Its carbohydrate word came back as `oolhvdraten` — the `y`
     * read as a `v`, so no carbohydrate suffix survives — and the tall `EEEEEEE` artefact chained
     * four printed rows into one.
     *
     * `NotFound` is the correct outcome and the brief accepts it. What must hold is that nothing
     * wrong is offered instead.
     */
    @Test
    fun `the wide-framing capture refuses rather than guessing`() {
        val document = ThirdSessionFixtures.drinkWideFraming()

        assertEquals(LabelReading.NotFound, read(document))
        assertTrue(
            "a capture with no recoverable carbohydrate row must offer no labelled reading",
            RecoveryCandidates.of(document).isEmpty(),
        )
    }

    // =============================================================== the cracker, two captures

    /** `225720-700`: `72,0 g` came back as `72,0.g` and was declined for stating no unit. */
    @Test
    fun `the punctuated-unit cracker reads its printed per-100 value automatically`() {
        val candidate = confident(read(ThirdSessionFixtures.crackerPunctuatedUnit()))

        assertEquals(0, candidate.value.compareTo(BigDecimal("72")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    /** `225738-513`: already correct, and the corrupted `22,59` must stay rejected. */
    @Test
    fun `the clean cracker keeps its reading and still rejects the corrupted portion value`() {
        val document = ThirdSessionFixtures.crackerCleanUnit()
        val candidate = confident(read(document))

        assertEquals(0, candidate.value.compareTo(BigDecimal("72")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
        RecoveryCandidates.of(document).forEach {
            assertFalse(
                "the corrupted portion value 22.59 was offered as '${it.label}'",
                it.reading.amount.compareTo(BigDecimal("22.59")) == 0,
            )
        }
    }

    @Test
    fun `the cracker recovery never offers the nine percent reference figure`() {
        RecoveryCandidates.of(ThirdSessionFixtures.crackerPunctuatedUnit()).forEach {
            assertFalse("'${it.label}' is a percentage", it.rawText.contains('%'))
            assertFalse(
                "the 9% reference figure was offered as a value",
                it.reading.amount.compareTo(BigDecimal("9")) == 0,
            )
        }
    }

    // =============================================================== the Korean sauce, two captures

    /**
     * The sauce prints `Serv. size: 1 Tbsp (18 g)` and `Total Carb. 6 g`. There is no per-100 column
     * anywhere on the panel, so the automatic path correctly refuses — and recovery must carry the
     * reading with its real basis rather than asking "per 100 g or per 100 ml?".
     */
    @Test
    fun `both sauce captures offer six grams per eighteen gram serving`() {
        listOf(
            "225752-375" to ThirdSessionFixtures.koreanSauceLinearPanel(),
            "225813-635" to ThirdSessionFixtures.koreanSauceSecondCapture(),
        ).forEach { (name, document) ->
            val candidates = RecoveryCandidates.of(document)
            val six = candidates.firstOrNull { it.reading.amount.compareTo(BigDecimal("6")) == 0 }

            assertNotNull("$name did not offer the printed 6 g at all", six)
            assertEquals("$name", "6 g / 18 g serving", six!!.label)
            assertEquals(
                CarbBasis.PerQuantity(BigDecimal("18"), NutritionBasis.PER_100_G, "serving"),
                six.reading.basis,
            )
        }
    }

    @Test
    fun `the sauce's serving reading normalizes to thirty three grams per hundred`() {
        val six = RecoveryCandidates.of(ThirdSessionFixtures.koreanSauceLinearPanel())
            .first { it.reading.amount.compareTo(BigDecimal("6")) == 0 }
        val derived = six.reading.normalizedToPerHundred()

        assertNotNull(derived)
        assertEquals(0, derived!!.amount.compareTo(BigDecimal("33.33333333")))
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_G), derived.basis)
        // The printed reading survives the derivation, so the screen can show both.
        assertEquals(0, derived.derivedFrom!!.amount.compareTo(BigDecimal("6")))
        assertEquals(six.reading.basis, derived.derivedFrom.basis)
    }

    @Test
    fun `neither sauce capture offers the fibre figure or a percent daily value`() {
        listOf(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.koreanSauceSecondCapture(),
        ).forEach { document ->
            val values = RecoveryCandidates.of(document).map { it.reading.amount }
            // 1 is the fibre figure on the same recognised row as the carbohydrate clause;
            // 2, 4 and 22 are %DV figures printed across the panel.
            listOf("1", "2", "4", "22").forEach { forbidden ->
                assertFalse(
                    "$forbidden was offered as a carbohydrate value",
                    values.any { it.compareTo(BigDecimal(forbidden)) == 0 },
                )
            }
        }
    }

    @Test
    fun `the sauce never offers a per-100 basis it did not print`() {
        listOf(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.koreanSauceSecondCapture(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach {
                assertFalse(
                    "'${it.label}' claims a per-100 basis on a panel that states none",
                    it.reading.basis is CarbBasis.PerHundred,
                )
            }
        }
    }

    // =============================================================== the Baltic multilingual table

    /**
     * `225829-154` read `59.2 g/100 g` correctly on the device — **by luck**. Its header resolved as
     * one column at x=1377.5 spanning both the per-100 g and the 9 g portion positions, and `59,2g`
     * at x=1308.5 simply happened to be nearer that midpoint than `5,4g` at x=1485.5.
     */
    @Test
    fun `the baltic table anchors its per-100 and portion columns separately`() {
        val columns = columnsOf(ThirdSessionFixtures.balticTableSeparateAnchors())

        val perHundred = columns.single { it.kind == NutritionColumnKind.PER_100_G }
        val portion = columns.filter { it.kind == NutritionColumnKind.UNKNOWN }
        val percent = columns.filter { it.kind == NutritionColumnKind.REFERENCE_PERCENT }

        // The printed cells sit at 1308.5 (59,2g), 1485.5 (5,4g) and 1595 (2%). Each anchor must be
        // nearer its own cell than to either of the others.
        assertTrue(
            "the per-100 anchor at ${perHundred.centerX} is not over the 59,2 g cell at 1308.5",
            kotlin.math.abs(perHundred.centerX - 1308.5) < kotlin.math.abs(perHundred.centerX - 1485.5),
        )
        assertTrue("the 9 g portion column was not separated", portion.isNotEmpty())
        assertTrue(
            "the portion anchor at ${portion.first().centerX} is not over the 5,4 g cell at 1485.5",
            kotlin.math.abs(portion.first().centerX - 1485.5) <
                kotlin.math.abs(portion.first().centerX - 1308.5),
        )
        assertTrue("the %RI column was lost", percent.any { it.centerX > 1500 })
    }

    @Test
    fun `the baltic table keeps its confident per-100 reading`() {
        val candidate = confident(read(ThirdSessionFixtures.balticTableSeparateAnchors()))

        assertEquals(0, candidate.value.compareTo(BigDecimal("59.2")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `the baltic table never reports the nine gram portion figure as per 100 g`() {
        val document = ThirdSessionFixtures.balticTableSeparateAnchors()

        val reading = read(document)
        if (reading is LabelReading.Confident) {
            assertFalse(
                "5.4 is the 9 g portion figure, not a per-100 value",
                reading.candidate.value.compareTo(BigDecimal("5.4")) == 0,
            )
        }
        RecoveryCandidates.of(document).forEach {
            if (it.reading.basis is CarbBasis.PerHundred) {
                assertFalse(
                    "'${it.label}' reports the portion figure under a per-100 basis",
                    it.reading.amount.compareTo(BigDecimal("5.4")) == 0,
                )
            }
            // 54 is the decimal-lost misread of 5,4 recorded on an earlier capture of this table.
            assertFalse(
                "'${it.label}' offers the decimal-lost 54",
                it.reading.amount.compareTo(BigDecimal("54")) == 0,
            )
        }
    }

    // =============================================================== across all nine

    @Test
    fun `no capture anywhere offers a percentage as a carbohydrate value`() {
        allNine().forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach {
                assertFalse("$name offered the percentage '${it.rawText}'", it.rawText.contains('%'))
                assertFalse("$name offered '${it.rawText}'", it.rawText.trim().startsWith('<'))
            }
        }
    }

    @Test
    fun `every candidate anywhere states what it is measured per`() {
        allNine().forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach {
                assertTrue(
                    "$name offered '${it.label}', which does not name a basis",
                    it.label.contains(" / ") && it.label.substringAfter(" / ").any { c -> c.isLetter() },
                )
            }
        }
    }

    /**
     * No choice anywhere is drawn from a child nutrient row.
     *
     * Asserted on the **rows the candidates came from**, not by tapping at each child row's centre.
     * A tap tests [LogicalRowBuilder]'s row membership, and on `225530-249` a tall `EEEEEEE` OCR
     * artefact chains four printed rows into one that vertically contains the sugars row — so a tap
     * there lands on the chained row, and the test would be measuring row reconstruction rather than
     * the rule it is named for. Asking each candidate which row it came from tests the rule directly
     * and is true regardless of how the rows reconstructed.
     */
    @Test
    fun `no candidate anywhere comes from a child nutrient row`() {
        allNine().forEach { (name, document) ->
            val childRows = LogicalRowBuilder.build(document)
                .filter { RowClassifier.classify(it) == NutritionRowKind.CARBOHYDRATE_CHILD }
                .map { it.text }
                .toSet()

            RecoveryCandidates.of(document).forEach { candidate ->
                assertFalse(
                    "$name offered '${candidate.label}' from the child row '${candidate.rowText}'",
                    candidate.rowText in childRows,
                )
            }
        }
    }

    /** And tapping a child row directly offers nothing, on every fixture where one is reachable. */
    @Test
    fun `tapping a child row offers nothing wherever one can be tapped`() {
        allNine().forEach { (name, document) ->
            LogicalRowBuilder.build(document)
                .filter { RowClassifier.classify(it) == NutritionRowKind.CARBOHYDRATE_CHILD }
                .forEach { child ->
                    val y = child.box.centerY.toInt()
                    // Only meaningful where the tap actually reaches this row. Where OCR chained
                    // rows together the tap lands on the chained row, which is a row-reconstruction
                    // fact rather than a child-row one; the test above covers those.
                    if (RecoveryCandidates.rowTextAt(document, y) != child.text) return@forEach
                    assertEquals(
                        "$name offered values from the child row '${child.text}'",
                        emptyList<RecoveryCandidates.Candidate>(),
                        RecoveryCandidates.onRowAt(document, y),
                    )
                    assertTrue(
                        "$name did not report '${child.text}' as a child row",
                        RecoveryCandidates.isChildRowAt(document, y),
                    )
                }
        }
    }

    /**
     * A candidate in a column whose meaning was never established is not offered.
     *
     * This is what makes the 2.6x error unreachable rather than merely unlikely: the 250 ml cells
     * resolve to [NutritionColumnKind.UNKNOWN], and an unknown position has no honest label, so
     * there is nothing for the screen to show and nothing for the user to pick.
     */
    @Test
    fun `no candidate anywhere sits in a column whose meaning was not established`() {
        allNine().forEach { (name, document) ->
            val unknown = columnsOf(document).filter { it.kind == NutritionColumnKind.UNKNOWN }
            RecoveryCandidates.of(document).forEach { candidate ->
                unknown.forEach { column ->
                    val nearest = columnsOf(document)
                        .minByOrNull { kotlin.math.abs(it.centerX - candidate.box.centerX) }
                    assertFalse(
                        "$name offered '${candidate.label}' from an UNKNOWN column at ${column.centerX}",
                        nearest?.kind == NutritionColumnKind.UNKNOWN,
                    )
                }
            }
        }
    }

    private fun allNine() = listOf(
        "225530-249" to ThirdSessionFixtures.drinkWideFraming(),
        "225617-066" to ThirdSessionFixtures.drinkAutomaticConfident(),
        "225632-622" to ThirdSessionFixtures.drinkFusedHeaderPipe(),
        "225654-501" to ThirdSessionFixtures.drinkTruncatedUnitHeader(),
        "225720-700" to ThirdSessionFixtures.crackerPunctuatedUnit(),
        "225738-513" to ThirdSessionFixtures.crackerCleanUnit(),
        "225752-375" to ThirdSessionFixtures.koreanSauceLinearPanel(),
        "225813-635" to ThirdSessionFixtures.koreanSauceSecondCapture(),
        "225829-154" to ThirdSessionFixtures.balticTableSeparateAnchors(),
    )
}
