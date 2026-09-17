package app.justthecarbs.ocr

import app.justthecarbs.ocr.DutchLabelFixtures.offeredValues
import app.justthecarbs.ocr.DutchLabelFixtures.outcome
import app.justthecarbs.ocr.EuropeanLabelFixtures.LANGUAGES
import org.junit.Test

/**
 * Measurement harness for nutrition tables in European languages and Turkish. **Prints a report;
 * asserts nothing.** `EuropeanNutritionTableTest` asserts what this found.
 *
 * Same purpose as [DutchLabelDiagnosticTest] and [TurkishLabelDiagnosticTest]: a printed form is
 * run before anything is changed, and only a form shown to fail becomes a fix. When a label in one of
 * these languages fails in someone's hand, add its printed form to [EuropeanLabelFixtures] and read
 * the outcome here.
 *
 * Per language: a one-column table; three columns (per 100, per portion, percentage) with and without
 * `%` on the percentage cells; other spellings of the carbohydrate name; a drink; a merged total+child
 * row for each child name (must never offer the child's `35`); and the declaration as running text.
 */
class EuropeanLabelDiagnosticTest {

    @Test
    fun report() {
        val lines = mutableListOf<String>()
        LANGUAGES.forEach { language ->
            lines += "== ${language.code}"
            lines += "  1 column          -> ${outcome(EuropeanLabelFixtures.table(language))}"
            listOf(true, false).forEach { signs ->
                val doc = EuropeanLabelFixtures.table(language, columns = 3, percentSigns = signs)
                val columns = ColumnClassifier.classify(LogicalRowBuilder.build(doc), doc.width)
                lines += "  3 columns, %=${if (signs) "cell" else "head"} -> ${outcome(doc)} | " +
                    columns.joinToString { "${it.kind}@${it.centerX.toInt()}" }
            }
            language.carbohydrateSpellings.forEach { spelling ->
                lines += "  '$spelling' -> ${outcome(EuropeanLabelFixtures.table(language, carbohydrate = spelling))}"
            }
            val lost = EuropeanLabelFixtures.tableWithLostCarbohydrateCell(language)
            val lostColumns = ColumnClassifier.classify(LogicalRowBuilder.build(lost), lost.width)
            lines += "  lost carb cell    -> ${outcome(lost)} | " +
                lostColumns.joinToString { "${it.kind}@${it.centerX.toInt()}" }
            lines += "  drink             -> ${outcome(EuropeanLabelFixtures.drink(language))}"
            language.childTerms.forEach { child ->
                val doc = EuropeanLabelFixtures.mergedRow(language, child)
                val kinds = RowClassifier.classifyAll(LogicalRowBuilder.build(doc))
                lines += "  merged '$child' -> offers ${offeredValues(doc)} | rows=$kinds"
            }
            val inline = EuropeanLabelFixtures.inlineBasisRows(language)
            lines += "  inline basis      -> ${outcome(inline)} offers ${offeredValues(inline)}"
            val prose = EuropeanLabelFixtures.prose(language)
            val report = NutritionTableInterpreter.interpret(prose)
            lines += "  prose             -> ${outcome(prose)} | ${report.provenance?.javaClass?.simpleName}"
        }
        println(lines.joinToString("\n"))
    }
}
