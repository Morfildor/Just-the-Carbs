package app.justthecarbs.ocr

import app.justthecarbs.ocr.DutchLabelFixtures.element
import app.justthecarbs.ocr.DutchLabelFixtures.outcome
import app.justthecarbs.ocr.DutchLabelFixtures.table
import app.justthecarbs.ocr.DutchLabelFixtures.twoColumnTable
import org.junit.Test

/**
 * Measurement harness for Turkish nutrition tables. **Prints a report; asserts nothing.**
 *
 * Same purpose and geometry as [DutchLabelDiagnosticTest]: the printed forms below were written from
 * Turkish packaging conventions (Türk Gıda Kodeksi labelling: `Enerji`, `Yağ`, `Karbonhidrat`,
 * `Şekerler`, `Lif`, `Protein`, `Tuz`, per `100 g` or `100 ml`) before they were run, and a form
 * that fails here becomes a regression test only once it is shown to fail. No terminology is added
 * on a guess.
 *
 * When a Turkish label fails in someone's hand, add its printed form here and read the outcome.
 */
class TurkishLabelDiagnosticTest {

    private val rows = listOf(
        "Enerji" to listOf("1650"),
        "Yağ" to listOf("12,0"),
        "Karbonhidrat" to listOf("62,5"),
        "Şekerler" to listOf("24,0"),
        "Protein" to listOf("6,2"),
        "Tuz" to listOf("0,4"),
    )

    private val headers = listOf(
        listOf("100", "g"),
        listOf("100", "g'da"),
        listOf("100", "gr'da"),
        listOf("100", "G'DA"),
        listOf("100g'da"),
        listOf("100", "g", "için"),
        listOf("100", "g'daki"),
        listOf("100", "gramda"),
        listOf("Besin", "Değerleri", "100", "g'da"),
        listOf("Ortalama", "Besin", "Değerleri", "100", "g"),
        listOf("BESİN", "DEĞERLERİ", "100", "G"),
    )

    private val millilitreHeaders = listOf(
        listOf("100", "ml"),
        listOf("100", "ml'de"),
        listOf("100", "ML'DE"),
        listOf("100", "ml", "için"),
    )

    private val carbohydrateSpellings = listOf(
        "Karbonhidrat", "KARBONHİDRAT", "KARBONHIDRAT", "karbonhidrat", "Karbonhidrat (g)",
        "Karbonhidrat, g", "Karbonhidratlar", "Toplam karbonhidrat", "Karbonhidrat:",
    )

    /** Turkish child rows that a merged row can put beside the total. */
    private val childTerms = listOf(
        "Şekerler", "- Şekerler", "ŞEKERLER", "Şeker", "Şekerleri", "bunun şekerleri",
        "Lif", "Diyet lifi", "Lifler", "Posa", "Nişasta", "Polioller",
        "Sakaroz", "Laktoz", "Glikoz", "Fruktoz", "Maltoz", "Dekstroz", "Maltodekstrin", "Glikoz şurubu",
    )

    @Test
    fun report() {
        val lines = mutableListOf<String>()

        lines += "== per-100 headers (g), Karbonhidrat 62,5"
        headers.forEach { header -> lines += "${header.joinToString(" ").padEnd(34)} -> ${outcome(table(header, rows))}" }

        lines += "== per-100 headers (ml), Karbonhidrat 4,5"
        millilitreHeaders.forEach { header ->
            val drink = listOf("Enerji" to listOf("78"), "Karbonhidrat" to listOf("4,5"), "Şekerler" to listOf("4,5"))
            lines += "${header.joinToString(" ").padEnd(34)} -> ${outcome(table(header, drink))}"
        }

        lines += "== carbohydrate spellings under '100 g'"
        carbohydrateSpellings.forEach { spelling ->
            val doc = table(listOf("100", "g"), rows.map { if (it.first == "Karbonhidrat") spelling to it.second else it })
            lines += "${spelling.padEnd(34)} -> ${outcome(doc)}"
        }

        lines += "== two columns: 100 g + portion"
        listOf(
            listOf("Porsiyon", "başına", "(30", "g)"),
            listOf("PORSİYON", "BAŞINA", "(30", "G)"),
            listOf("1", "porsiyon", "(30", "g)"),
            listOf("Porsiyonda", "(30", "g)"),
            listOf("30", "g'da"),
        ).forEach { second ->
            val doc = twoColumnTable(
                listOf("100", "g'da"),
                second,
                rows.map { (label, values) -> label to (values + values.first()) },
            )
            val report = NutritionTableInterpreter.interpret(doc)
            val columns = ColumnClassifier.classify(LogicalRowBuilder.build(doc), doc.width)
            lines += "${second.joinToString(" ").padEnd(34)} -> ${outcome(doc)} | columns=" +
                columns.joinToString { "${it.kind}@${it.centerX.toInt()}" } +
                " | serving=${report.servingCandidate?.descriptor}"
        }

        lines += "== merged Karbonhidrat 62 + child 35 on one reconstructed row (must never offer 35 alone)"
        childTerms.forEach { child ->
            val doc = mergedRow(child)
            val kinds = LogicalRowBuilder.build(doc).map { RowClassifier.classify(it) }
            lines += "${child.padEnd(34)} -> ${outcome(doc)} | rows=$kinds"
        }

        println(lines.joinToString("\n"))
    }

    /** [DutchLabelFixtures.mergedTotalAndChildRow], with Turkish words. */
    private fun mergedRow(child: String): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += element("100", 378, 100, 444, 130, line = 0)
        elements += element("g", 456, 100, 478, 130, line = 0)

        elements += element("Karbonhidrat", 40, 200, 208, 240, line = 1)
        elements += element("62", 380, 200, 424, 240, line = 1, block = 4)

        var x = 60
        child.split(' ').forEach { word ->
            elements += element(word, x, 215, x + 14 * word.length, 255, line = 1)
            x += 14 * word.length + 10
        }
        elements += element("35", 380, 215, 424, 255, line = 1, block = 4)

        elements += element("Protein", 40, 320, 138, 360, line = 2)
        elements += element("6,2", 380, 320, 446, 360, line = 2, block = 4)
        return OcrDocument(width = 1200, height = 500, elements = elements)
    }
}
