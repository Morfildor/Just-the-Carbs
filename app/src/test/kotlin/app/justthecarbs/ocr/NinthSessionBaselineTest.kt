package app.justthecarbs.ocr

import org.junit.Test

/**
 * Measures what the nine captures of the ninth phone session do **before** anything is changed.
 *
 * Prints; asserts nothing. This exists so the root cause is established from the shipped code's own
 * behaviour rather than from reading it, and so the before/after in the final report is a
 * measurement anyone can reproduce in one command.
 *
 * [NinthSessionOutcomeTest] is the assertion half.
 *
 * ## What this probe can and cannot see
 *
 * These fixtures are the **Pass A / full-frame** documents, which is what `diagnostics.txt` records.
 * The three correct readings this session lost came from **Strategy B** — a fresh recognition of the
 * user's crop — and the bundle records only its *verdict*, never its document. So this probe
 * reproduces Pass A's `NotFound` exactly, and cannot reproduce Strategy B's `Confident 2.8`.
 *
 * That is a real limit and it is why the fix in this pass is proven by
 * [NinthSessionRegressionTest] driving the **resolver and the gate** with the evidence shapes the
 * bundles record, rather than by re-deriving a value from a document the device never saved.
 */
class NinthSessionBaselineTest {

    private fun probe(name: String, document: OcrDocument) {
        println("########## $name")
        val report = NutritionTableInterpreter.interpret(document)
        println("  pass A reading     : ${report.reading}")

        val confident = report.reading as? LabelReading.Confident
        if (confident != null) {
            val candidate = confident.candidate
            println("  candidate          : ${candidate.value} / ${candidate.basis} col=${candidate.column}")
            println("  scale verdict      : ${ScaleAmbiguity.check(document, candidate)}")
        }

        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)
        rows.forEachIndexed { index, row ->
            if (kinds[index] == NutritionRowKind.TOTAL_CARBOHYDRATE ||
                kinds[index] == NutritionRowKind.CARBOHYDRATE_CHILD
            ) {
                println("  ${kinds[index]}: '${row.text}'")
            }
        }
        val columns = ColumnClassifier.classify(rows, document.width)
        println("  columns            : " + columns.joinToString { "${it.kind}@${it.centerX}" })
        println("  stated basis       : ${StatedBasis.of(document)}")
    }

    @Test
    fun `the ninth session as the device produced it`() {
        probe("084935-802 green drink, nothing read", NinthSessionFixtures.greenDrinkNoReading())
        probe("084951-833 green drink, B read 0.5 (LOST)", NinthSessionFixtures.greenDrinkStrategyB())
        probe("085008-894 cracker 72 (CONTROL: must keep working)", NinthSessionFixtures.crackerAutoAdvance())
        probe("085019-213 white table, B read 2.8 (LOST)", NinthSessionFixtures.whiteTableFirst())
        probe("085032-269 white table, B read 2.8 (LOST)", NinthSessionFixtures.whiteTableSecond())
        probe("085046-180 blue tub 3.2 (CONTROL: must keep working)", NinthSessionFixtures.blueTubAutoAdvance())
        probe("085100-491 ingredient underside", NinthSessionFixtures.ingredientUnderside())
        probe("085117-289 small blue table, sparse", NinthSessionFixtures.smallBlueTableSparse())
        probe("085128-913 red label, B read 12 (CONTROL: must stay refused)", NinthSessionFixtures.redLabelTwelve())
    }
}
