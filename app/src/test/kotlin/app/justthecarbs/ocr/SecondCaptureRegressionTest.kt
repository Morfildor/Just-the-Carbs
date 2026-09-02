package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Test

/**
 * The two captures from the 2026-09-01 22:22 phone session
 * (`docs/Scan Evidence 01-09-26 2nd test/`), asserted at the level the user experiences.
 *
 * Both returned `NotFound` on the device after 20195 ms and 11189 ms. The parse regression is
 * measured separately in [ParserStageProfileTest]; what is asserted here is what the parser *says*,
 * and in particular the two properties that must never break:
 *
 * - the 250 ml figure never reaches the user as a per-100-ml reading;
 * - a capture whose carbohydrate label is damaged past recovery refuses rather than guessing.
 */
class SecondCaptureRegressionTest {

    private fun read(document: OcrDocument) = NutritionTableInterpreter.interpret(document)

    private fun confidentValue(reading: LabelReading): Pair<Double, NutritionBasis?>? =
        (reading as? LabelReading.Confident)?.let { it.candidate.value.toDouble() to it.candidate.basis }

    // ------------------------------------------------------------------ P0-3: the fused header

    /**
     * The header states two columns and must produce two, at their own positions.
     *
     * On the device this row read `100 ml 250 m ml (79` and produced a **single**
     * `PER_100_ML @ x=836`, whose span text was `'100 ml 250 m'` — the per-100 match having consumed
     * the following quantity. Both value cells then bound to it.
     */
    @Test
    fun `the split-unit header yields a per-100 column and a separate off-basis position`() {
        val document = HardwareLabelFixtures.greenDrinkClippedLabel()
        val columns = ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

        val perHundred = columns.filter { it.kind == NutritionColumnKind.PER_100_ML }
        check(perHundred.size == 1) { "expected one per-100-ml column, got $columns" }

        // The per-100 span must not reach into the 250 ml header.
        check(!perHundred.single().headerText.contains("250")) {
            "the per-100 span consumed the 250 ml quantity: '${perHundred.single().headerText}'"
        }

        // And the 250 ml position must exist, with its meaning unestablished.
        val offBasis = columns.filter { it.kind == NutritionColumnKind.UNKNOWN }
        check(offBasis.any { it.headerText.contains("250") }) {
            "the 250 ml header produced no column of its own: $columns"
        }
    }

    /** The same, for the capture where ML Kit emitted `100` `ml` `250` `ml` cleanly. */
    @Test
    fun `the cleanly split header also yields two positions`() {
        val document = HardwareLabelFixtures.greenDrinkSecondCapture()
        val columns = ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

        check(columns.count { it.kind == NutritionColumnKind.PER_100_ML } == 1) {
            "expected exactly one per-100-ml column, got $columns"
        }
        check(columns.any { it.kind == NutritionColumnKind.UNKNOWN && it.headerText.contains("250") }) {
            "the 250 ml header produced no column of its own: $columns"
        }
    }

    /**
     * A single-column header is untouched.
     *
     * The negative control for the split above: a table stating only `per 100 g` must still produce
     * exactly one column, with no spurious unknown position beside it.
     */
    @Test
    fun `a single-basis header still produces one column`() {
        val document = HardwareLabelFixtures.crackerBag()
        val columns = ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

        check(columns.count { it.kind == NutritionColumnKind.PER_100_G } == 1) {
            "expected one per-100-g column, got $columns"
        }
    }

    // ------------------------------------------------------- the value must never inherit the basis

    /**
     * **The forbidden outcome.** `1.3` is the 250 ml figure; it may never be reported per 100 ml.
     */
    @Test
    fun `neither capture ever reports the 250 ml figure as a per-100-ml reading`() {
        listOf(
            "222212-563" to HardwareLabelFixtures.greenDrinkSecondCapture(),
            "222300-297" to HardwareLabelFixtures.greenDrinkClippedLabel(),
        ).forEach { (name, document) ->
            val reading = read(document).reading

            val values = when (reading) {
                is LabelReading.Confident -> listOf(reading.candidate)
                is LabelReading.Ambiguous -> reading.candidates
                else -> emptyList()
            }
            values.forEach { candidate ->
                check(candidate.value.toDouble() != 1.3) {
                    "$name reported the 250 ml figure 1.3 as ${candidate.basis}"
                }
                check(candidate.value.toDouble() != 13.0) {
                    "$name reported the 250 ml figure 13 as ${candidate.basis}"
                }
            }
        }
    }

    // ----------------------------------------------------------- P0-4: the damaged label recovery

    /**
     * The preferred outcome for `222212-563`: the printed `0,5 g/100 ml`.
     *
     * Everything on this capture was recognised correctly except the word `Koolhydraten:`, which came
     * back `laolhydraten:`. The row keeps its values and precedes the sugars row, so
     * [DamagedCarbohydrateLabel]'s structural conditions hold and the table reads.
     */
    @Test
    fun `the damaged-label capture reads its printed per-100-ml value`() {
        val reading = read(HardwareLabelFixtures.greenDrinkSecondCapture()).reading
        val value = confidentValue(reading)

        check(value != null) { "expected a confident reading, got $reading" }
        check(value.first == 0.5) { "expected 0.5, got ${value.first}" }
        check(value.second == NutritionBasis.PER_100_ML) {
            "expected per 100 ml, got ${value.second}"
        }
    }

    /**
     * The clipped-label capture refuses, and that is the required outcome rather than a shortfall.
     *
     * `bydraten.` has lost `koolhy` entirely, so no carbohydrate suffix survives and the row is not
     * recoverable evidence of anything. The brief's acceptable outcome here is a fast safe recovery,
     * not a value.
     */
    @Test
    fun `the clipped-label capture refuses rather than guessing`() {
        val reading = read(HardwareLabelFixtures.greenDrinkClippedLabel()).reading
        check(reading is LabelReading.NotFound) {
            "expected NotFound for an unrecoverable label, got $reading"
        }
    }

    /**
     * The recovery never substitutes the sugars row.
     *
     * The single most important negative control on [DamagedCarbohydrateLabel]: the row it recovers
     * must be the carbohydrate row, never the child row printed beneath it. `0.59` and `1.3` are the
     * sugars figures on this capture.
     */
    @Test
    fun `the recovered row is the carbohydrate row, never the sugars row`() {
        val reading = read(HardwareLabelFixtures.greenDrinkSecondCapture()).reading
        val candidate = (reading as LabelReading.Confident).candidate

        check(candidate.value.toDouble() != 0.59) { "the sugars figure was reported as the total" }
        check(candidate.value.toDouble() != 1.3) { "the sugars figure was reported as the total" }
        check(!NutritionTerminology.exclusionTerms.any {
            NutritionTerminology.containsTerm(
                NutritionTerminology.normalize(candidate.sourceLine),
                it,
            )
        }) {
            "the recovered row names a child nutrient: '${candidate.sourceLine}'"
        }
    }

    /**
     * The recovery cannot fire on a table that already reads.
     *
     * Asserted on the two fixtures with undamaged labels: their readings must be exactly what they
     * were before the recovery existed.
     */
    @Test
    fun `an undamaged table is unaffected by the damaged-label recovery`() {
        val cracker = confidentValue(read(HardwareLabelFixtures.crackerBag()).reading)
        check(cracker == 72.0 to NutritionBasis.PER_100_G) { "cracker changed: $cracker" }

        val multilingual = confidentValue(read(HardwareLabelFixtures.multilingualTable()).reading)
        check(multilingual == 59.2 to NutritionBasis.PER_100_G) {
            "multilingual changed: $multilingual"
        }
    }

    /**
     * The recovery cannot fire without a resolved basis column.
     *
     * Condition 1 of [DamagedCarbohydrateLabel]: a document that resolved no per-100 column is not a
     * confirmed nutrition table, so a damaged word in it is just a damaged word. Built by taking the
     * damaged capture and removing its header row, which is the one thing that makes it a table.
     */
    @Test
    fun `the recovery requires a confirmed nutrition table`() {
        val original = HardwareLabelFixtures.greenDrinkSecondCapture()
        val withoutHeader = original.copy(
            elements = original.elements.filterNot { it.text == "100" || it.text == "ml" },
        )

        val rows = LogicalRowBuilder.build(withoutHeader)
        val columns = ColumnClassifier.classify(rows, withoutHeader.width)
        val kinds = rows.map { RowClassifier.classify(it) }

        val recovered = DamagedCarbohydrateLabel.recoverTotalRowIndex(rows, kinds, columns)
        check(recovered == null) {
            "recovery fired on a document with no per-100 column (columns=$columns)"
        }
    }
}
