package app.justthecarbs.ocr

import org.junit.Test

/**
 * Prints what each hardware fixture currently does, and asserts almost nothing.
 *
 * This is the before/after instrument for the 2026-09-01 device session, in the same spirit as
 * `DutchLabelDiagnosticTest`: a harness whose output is read by a person comparing two runs, rather
 * than a gate that fails the build. Turning these observations into assertions belongs in
 * [HardwareScanRegressionTest], where each one states a rule rather than a measurement.
 *
 * Run with `--info` (or read the standard output in the HTML report) to see the tables.
 */
class HardwareScanDiagnosticTest {

    private fun describe(name: String, document: OcrDocument) {
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        println("=========== $name")
        println("reading  : ${describeReading(report.reading)}")
        println("serving  : ${report.servingCandidate}")
        println("columns  :")
        columns.forEach { println("    ${it.kind.name.padEnd(18)} x=${"%.1f".format(it.centerX)}  '${it.headerText}'") }
        println("rows     :")
        rows.forEach { row ->
            val kind = RowClassifier.classify(row)
            if (kind != NutritionRowKind.OTHER) {
                println("    ${kind.name.padEnd(20)} ${row.text.take(90)}")
            }
        }
        println("rejections:")
        report.diagnostics.filter { it.stage in REPORTED_STAGES }
            .forEach { println("    ${it.stage.padEnd(14)} ${it.message.take(110)}") }
        println()
    }

    private fun describeReading(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value.toPlainString()} ${reading.candidate.basis?.name ?: "no-basis"}"
        is LabelReading.Ambiguous ->
            "Ambiguous " + reading.candidates.joinToString(", ") {
                "${it.value.toPlainString()}/${it.basis?.name ?: "no-basis"}"
            }
        LabelReading.NotFound -> "NotFound"
    }

    private val REPORTED_STAGES = setOf(
        "rejected", "result", "ambiguous", "selected", "unit-marker",
        "unit-accompaniment", "row-segment", "cross-column", "serving-weight", "prose",
    )

    @Test
    fun `what the four hardware labels currently do`() {
        describe("A green drink   (prints 0,5 g/100 ml and 1,3 g/250 ml)", HardwareLabelFixtures.greenDrink())
        describe("B cracker bag   (prints 72,0 g/100 g and 22,5 g/portion)", HardwareLabelFixtures.crackerBag())
        describe("C korean sauce  (prints 6 g per 18 g serving)", HardwareLabelFixtures.koreanSauce())
        describe("D multilingual  (prints 59,2 g/100 g and 5,4 g/9 g)", HardwareLabelFixtures.multilingualTable())
    }
}
