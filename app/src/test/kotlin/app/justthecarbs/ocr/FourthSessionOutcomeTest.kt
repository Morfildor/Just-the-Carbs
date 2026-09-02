package app.justthecarbs.ocr

import org.junit.Test

/**
 * The whole fourth session in one table: reading, verification route, and what recovery offers.
 *
 * Prints rather than asserts — [FourthSessionRegressionTest] carries the assertions. This exists so
 * the before/after in `CLAUDE.md` is a measurement anyone can reproduce in one command, and so a
 * future change that shifts a capture from automatic to confirmation is visible rather than merely
 * still-green.
 */
class FourthSessionOutcomeTest {

    private fun row(name: String, document: OcrDocument) {
        val report = NutritionTableInterpreter.interpret(document)
        val verdict = AutomaticVerification.verify(document, report)
        val value = (report.reading as? LabelReading.Confident)?.candidate
        val reading = value?.let { "${it.value.toPlainString()}/${it.basis?.name}" }
            ?: report.reading::class.simpleName!!

        val action = when {
            value != null && verdict.mayAdvanceAutomatically -> "AUTO_ADVANCE"
            value != null -> "CONFIRM"
            else -> "RECOVERY"
        }

        val offers = RecoveryCandidates.of(document).joinToString(" | ") { it.label }

        println(
            "%-14s %-22s %-24s %-13s %s".format(
                name,
                reading,
                verdict.route.name +
                    if (verdict.supportingRows > 0) "(${verdict.supportingRows})" else "",
                action,
                offers.ifEmpty { "—" },
            ),
        )
    }

    @Test
    fun `the fourth session's nine captures, end to end`() {
        println("%-14s %-22s %-24s %-13s %s".format("capture", "reading", "verification", "action", "recovery offers"))
        row("085442-819", FourthSessionFixtures.drinkCleanAutomatic())
        row("085453-023", FourthSessionFixtures.drinkRecoveryDeadEnd())
        row("085513-478", FourthSessionFixtures.drinkRecoveryDeadEndSecond())
        row("085534-551", FourthSessionFixtures.crackerCorrectFirst())
        row("085542-213", FourthSessionFixtures.crackerMisreadTotal())
        row("085554-517", FourthSessionFixtures.crackerCorrectSecond())
        row("085602-075", FourthSessionFixtures.crackerCorrectThird())
        row("085611-201", FourthSessionFixtures.sauceLinearPanel())
        row("085631-444", FourthSessionFixtures.sauceLinearPanelSecond())
    }
}
