package app.justthecarbs.ocr

import org.junit.Test

/**
 * Measures what the eight captures of the eighth phone session do **before** anything is changed.
 *
 * Prints; asserts nothing. This exists so the root cause is established from the shipped code's own
 * behaviour rather than from reading it, and so the before/after in the final report is a
 * measurement anyone can reproduce in one command.
 *
 * [EighthSessionOutcomeTest] is the assertion half — it pins the device outcomes so a later test
 * claiming `12` is suppressed cannot pass because the fixture never produced `12` in the first
 * place. That is the fixture trap this repo has now hit four times.
 */
class EighthSessionBaselineTest {

    private fun probe(name: String, document: OcrDocument) {
        println("########## $name")
        val report = NutritionTableInterpreter.interpret(document)
        println("  reading            : ${report.reading}")

        val confident = report.reading as? LabelReading.Confident
        if (confident != null) {
            val candidate = confident.candidate
            println("  candidate          : ${candidate.value} / ${candidate.basis} col=${candidate.column}")
            println("  geometry           : ${candidate.geometry}")
            println("  scale verdict      : ${ScaleAmbiguity.check(document, candidate)}")
        }

        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)
        rows.forEachIndexed { index, row ->
            if (kinds[index] == NutritionRowKind.TOTAL_CARBOHYDRATE || kinds[index] == NutritionRowKind.CARBOHYDRATE_CHILD) {
                println("  ${kinds[index]}: '${row.text}'")
            }
        }
        val columns = ColumnClassifier.classify(rows, document.width)
        println("  columns            : " + columns.joinToString { "${it.kind}@${it.centerX}" })
    }

    @Test
    fun `the eighth session as the device produced it`() {
        probe("212902-571 drink recovery", EighthSessionFixtures.drinkRecovery())
        probe("212915-131 drink confirmed (CONTROL: must keep working)", EighthSessionFixtures.drinkConfirmed())
        probe("212926-150 drink conflicted", EighthSessionFixtures.drinkConflicted())
        probe("212939-872 cracker auto-advance (CONTROL)", EighthSessionFixtures.crackerAutoAdvance())
        probe("212952-487 red label, value fused", EighthSessionFixtures.redLabelFusedValue())
        probe("213005-691 red label -> 12 (THE P0)", EighthSessionFixtures.redLabelTwelve())
        probe("213014-298 red label -> 724", EighthSessionFixtures.redLabelSevenTwoFour())
        probe("213026-546 red label, value fused again", EighthSessionFixtures.redLabelFusedValueSecond())
    }
}
