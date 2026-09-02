package app.justthecarbs.ocr

import org.junit.Test

/**
 * Prints what the parser currently does with each 2026-09-02 capture. Asserts almost nothing.
 *
 * This is the measurement harness for the fourth phone session, and it exists for the same reason
 * [ThirdSessionDiagnosticTest] does: a change to a parser stage is only defensible against a
 * before/after taken on the device's own recognition. Its output is the negative control for every
 * fix in this pass — the "before" column of the table in `CLAUDE.md` is this test's own output, run
 * on the tree as it stood before anything was changed.
 *
 * It deliberately makes no assertions about outcomes. [FourthSessionRegressionTest] does that.
 */
class FourthSessionDiagnosticTest {

    private fun report(name: String, document: OcrDocument) {
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        val report = NutritionTableInterpreter.interpret(document)

        println("=== $name (${document.elements.size} elements) ===")
        println("  reading  : ${report.reading}")
        println("  provenance: ${report.provenance}")
        columns.forEach {
            println("  column   : ${it.kind} '${it.headerText}' @ x=${it.centerX}")
        }
        rows.forEachIndexed { index, row ->
            val kind = RowClassifier.classify(row)
            if (kind != NutritionRowKind.OTHER) {
                println("  row[$index] : $kind: ${row.elements.joinToString(" ") { it.text }}")
            }
        }
        report.diagnostics.forEach { println("  diag     : ${it.stage}: ${it.message}") }
        println()
    }

    @Test
    fun `what the fourth session's nine captures currently produce`() {
        report("085442-819 drink clean", FourthSessionFixtures.drinkCleanAutomatic())
        report("085453-023 drink dead end", FourthSessionFixtures.drinkRecoveryDeadEnd())
        report("085513-478 drink dead end 2", FourthSessionFixtures.drinkRecoveryDeadEndSecond())
        report("085534-551 cracker correct", FourthSessionFixtures.crackerCorrectFirst())
        report("085542-213 cracker MISREAD", FourthSessionFixtures.crackerMisreadTotal())
        report("085554-517 cracker correct 2", FourthSessionFixtures.crackerCorrectSecond())
        report("085602-075 cracker correct 3", FourthSessionFixtures.crackerCorrectThird())
        report("085611-201 sauce linear", FourthSessionFixtures.sauceLinearPanel())
        report("085631-444 sauce linear 2", FourthSessionFixtures.sauceLinearPanelSecond())
    }
}
