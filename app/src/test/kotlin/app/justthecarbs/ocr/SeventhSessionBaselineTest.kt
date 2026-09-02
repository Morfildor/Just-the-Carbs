package app.justthecarbs.ocr

import org.junit.Test

/**
 * Measures what the two truffle captures do **before** anything is changed.
 *
 * Prints; asserts nothing. This exists so the root cause is established from the shipped code's own
 * behaviour rather than from reading it, and so the before/after in the final report is a
 * measurement anyone can reproduce in one command.
 */
class SeventhSessionBaselineTest {

    private fun probe(name: String, document: OcrDocument) {
        println("########## $name")
        val report = NutritionTableInterpreter.interpret(document)
        println("  reading            : ${report.reading}")

        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)
        val columns = ColumnClassifier.classify(rows, document.width)
        println("  columns            : " + columns.joinToString { "${it.kind}@${it.centerX}" })

        // The carbohydrate row, found the way a human reads the bundle: the row naming the word.
        rows.forEachIndexed { index, row ->
            val normalized = NutritionTerminology.normalize(row.text)
            val namesCarb = NutritionTerminology.carbohydrateTerms.any {
                NutritionTerminology.containsTerm(normalized, it)
            } || DamagedCarbohydrateLabel.statesADamagedCarbohydrateWord(normalized)
            if (!namesCarb) return@forEachIndexed

            println("  --- row $index kind=${kinds[index]}")
            println("      text     : ${row.text.take(140)}")
            println("      box      : ${row.box}")
            val segment = NutrientRowSegments.totalCarbohydrateSegment(row)
            println("      segment  : " + (segment?.let { "term='${it.term}' x=${it.startX}..${it.endX}" } ?: "none"))
            println("      classify : ${RowClassifier.classify(row)}")

            row.elements.forEach { element ->
                println(
                    "      el '${element.text}' @[${element.box.left},${element.box.top}," +
                        "${element.box.right},${element.box.bottom}]" +
                        (segment?.let { " inSegment=${it.contains(element.box)}" } ?: ""),
                )
            }

            // What a tap on each element would do.
            row.elements.forEach { element ->
                val y = element.box.centerY.toInt()
                val x = element.box.centerX.toInt()
                val child = RecoveryCandidates.isChildRowAt(document, y, x)
                val offered = RecoveryCandidates.onRowAt(document, y, x)
                println(
                    "      TAP '${element.text}' -> isChildRow=$child offers=" +
                        offered.joinToString { it.label }.ifEmpty { "—" },
                )
            }
        }

        println("  recovery of()      : " + RecoveryCandidates.of(document).joinToString { it.label }.ifEmpty { "—" })
        println("  explain()          :")
        RecoveryCandidates.explain(document).forEach { println("      $it") }
        println("  stated basis       : " + StatedBasis.of(document))
        println("  focused entry      : " + (FocusedAmountEntry.of(document)?.let {
            "basis=${it.basis} row='${it.rowText.take(60)}'"
        } ?: "NULL — no focused entry offered"))
        println()
    }

    @Test
    fun `the two truffle captures, as shipped`() {
        probe("141642-529", SeventhSessionFixtures.truffleSeparatorlessPair())
        probe("141703-456", SeventhSessionFixtures.truffleDamagedUnitGlyph())
    }
}
