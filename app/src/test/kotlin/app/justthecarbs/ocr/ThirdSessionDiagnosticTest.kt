package app.justthecarbs.ocr

import org.junit.Test

/**
 * Prints what the real interpreter does with each of the nine 2026-09-01 third-session captures.
 *
 * Asserts almost nothing on purpose. It exists so a change to the parser can be measured against the
 * device's own recognitions before and after, in the one form that is comparable to the evidence
 * bundles: the columns, their anchors, the row types and the outcome. The assertions that pin
 * behaviour live in `ThirdSessionRegressionTest`; putting them here too would mean a fixture whose
 * output moves for a good reason fails in two places.
 */
class ThirdSessionDiagnosticTest {

    private val fixtures = listOf(
        "225530-249 drink wide framing" to ThirdSessionFixtures.drinkWideFraming(),
        "225617-066 drink automatic" to ThirdSessionFixtures.drinkAutomaticConfident(),
        "225632-622 drink fused pipe" to ThirdSessionFixtures.drinkFusedHeaderPipe(),
        "225654-501 drink truncated unit" to ThirdSessionFixtures.drinkTruncatedUnitHeader(),
        "225720-700 cracker punctuated" to ThirdSessionFixtures.crackerPunctuatedUnit(),
        "225738-513 cracker clean" to ThirdSessionFixtures.crackerCleanUnit(),
        "225752-375 korean sauce" to ThirdSessionFixtures.koreanSauceLinearPanel(),
        "225813-635 korean sauce 2" to ThirdSessionFixtures.koreanSauceSecondCapture(),
        "225829-154 baltic table" to ThirdSessionFixtures.balticTableSeparateAnchors(),
    )

    @Test
    fun `the third session captures through the real interpreter`() {
        fixtures.forEach { (name, document) ->
            val report = NutritionTableInterpreter.interpret(document)
            println("=".repeat(78))
            println(name)

            val rows = LogicalRowBuilder.build(document)
            ColumnClassifier.classify(rows, document.width).forEach {
                println("  column ${it.kind.name.padEnd(18)} x=${it.centerX} '${it.headerText}'")
            }
            rows.map { it to RowClassifier.classify(it) }
                .filter { it.second != NutritionRowKind.OTHER }
                .forEach { (row, kind) -> println("  ${kind.name.padEnd(20)} '${row.text}'") }

            println("  OUTCOME: ${describe(report.reading)}")
            report.servingCandidate?.let {
                println("  SERVING: ${it.carbsPerServing.toPlainString()} descriptor=${it.descriptor}")
            }
            report.diagnostics
                .filter { it.stage in INTERESTING }
                .forEach { println("    [${it.stage}] ${it.message}") }
        }
    }

    private fun describe(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value.toPlainString()} ${reading.candidate.basis}"
        is LabelReading.Ambiguous -> "Ambiguous " +
            reading.candidates.joinToString { "${it.value.toPlainString()}/${it.basis}" }
        LabelReading.NotFound -> "NotFound"
    }

    private companion object {
        val INTERESTING = setOf(
            "rejected", "result", "prose", "damaged-label", "merged-row", "cross-column",
            "unit-accompaniment", "row-segment", "serving-weight", "ambiguous", "selected",
        )
    }
}
