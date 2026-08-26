package app.justthecarbs.ocr

import org.junit.Test

/**
 * Measurement harness for Dutch nutrition tables. **Prints a report; asserts almost nothing.**
 *
 * It exists because this repo has learned twice that a green suite is not evidence that the scanner
 * reads packaging: every synthetic fixture was written by the same person who wrote the parser, so
 * it reproduces the label forms that were already thought about. The forms below were written from
 * Dutch and Belgian packaging conventions *first*, then run, and whatever failed became a real
 * regression test in `DutchNutritionTableTest`.
 *
 * Keep it. When the next Dutch label fails in someone's hand, adding its printed form here and
 * reading the outcome is the fastest route from "it does not work" to "this stage ran out of
 * evidence".
 */
class DutchLabelDiagnosticTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    /**
     * Lays a printed table out the way one is actually printed, which the geometry stages care about
     * far more than the words do.
     *
     * **A header phrase is centred over the column it describes.** The first version of this harness
     * laid every header word left-to-right from a fixed origin, so a long header like
     * `Voedingswaarde per 100 g` pushed its own `per 100 g` hundreds of pixels to the right of the
     * values it heads — and every long Dutch header "failed". That was the harness mis-drawing the
     * label, not the parser mis-reading it, and acting on it would have meant tuning a parser against
     * a picture no package resembles. This repo has made that mistake once already, with a geometric
     * regression test that measured the soft keyboard.
     *
     * A leading descriptive noun (`Voedingswaarde`, `Gemiddelde voedingswaarde`) belongs at the left,
     * in the label column, because that is where a package prints it — above `Koolhydraten`, not
     * above the numbers.
     */
    private fun table(header: List<String>, rows: List<Pair<String, List<String>>>): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        val columnCentres = listOf(380, 900)
        var line = 0

        // Everything up to and including the last connective-or-"100"-or-unit token heads a column;
        // anything before a leading noun stays on the left. Split on the first token that starts the
        // measurement phrase itself.
        val phraseStart = header.indexOfFirst { it.trim('(', ')').lowercase() in PHRASE_STARTERS }
        val leading = if (phraseStart > 0) header.take(phraseStart) else emptyList()
        val phrase = if (phraseStart > 0) header.drop(phraseStart) else header

        var leadX = 40
        leading.forEach { word ->
            elements += e(word, leadX, 100, leadX + 18 * word.length, 130, line = line)
            leadX += 18 * word.length + 10
        }

        val phraseWidth = phrase.sumOf { 22 * it.length + 12 }
        var x = columnCentres.first() - phraseWidth / 2
        phrase.forEach { word ->
            elements += e(word, x, 100, x + 22 * word.length, 130, line = line)
            x += 22 * word.length + 12
        }
        line++

        rows.forEachIndexed { index, (label, values) ->
            val top = 200 + index * 60
            var labelX = 40
            label.split(' ').forEach { word ->
                elements += e(word, labelX, top, labelX + 14 * word.length, top + 30, line = line)
                labelX += 14 * word.length + 10
            }
            values.forEachIndexed { column, value ->
                val width = 22 * value.length
                val vx = columnCentres[column] - width / 2
                elements += e(value, vx, top, vx + width, top + 30, line = line, block = 4)
            }
            line++
        }
        return OcrDocument(width = 1200, height = 200 + rows.size * 60 + 60, elements = elements)
    }

    /** Tokens that begin a column-heading measurement phrase rather than describing the table. */
    private val PHRASE_STARTERS = setOf(
        "per", "pro", "par", "100", "100g", "100ml",
    )

    /**
     * Same as [table] but with a second header phrase centred over the second value column, which is
     * how a Dutch supermarket label prints `per 100 g` beside `per portie`.
     */
    private fun twoColumnTable(
        first: List<String>,
        second: List<String>,
        rows: List<Pair<String, List<String>>>,
    ): OcrDocument {
        val base = table(first, rows)
        val secondWidth = second.sumOf { 22 * it.length + 12 }
        var x = 900 - secondWidth / 2
        val extra = second.map { word ->
            val element = e(word, x, 100, x + 22 * word.length, 130, line = 0)
            x += 22 * word.length + 12
            element
        }
        return base.copy(elements = base.elements + extra)
    }

    private fun outcome(document: OcrDocument): String {
        val report = NutritionTableInterpreter.interpret(document)
        return when (val reading = report.reading) {
            is LabelReading.Confident ->
                "Confident ${reading.candidate.value} ${reading.candidate.basis}"
            is LabelReading.Ambiguous ->
                "Ambiguous(${reading.candidates.size}) ${reading.candidates.map { it.value }}"
            is LabelReading.NotFound ->
                "NotFound" + report.diagnostics.take(2).joinToString("") { "  [$it]" }
        }
    }

    private fun report(title: String, cases: List<Pair<String, OcrDocument>>) {
        println("\n=== $title ===")
        cases.forEach { (name, document) ->
            println(String.format("  %-46s %s", name, outcome(document)))
        }
    }

    /** The word for "carbohydrates" as Dutch packaging actually prints and OCRs it. */
    @Test
    fun `how the Dutch carbohydrate term is recognised`() {
        val spellings = listOf(
            "Koolhydraten",
            "koolhydraten",
            "KOOLHYDRATEN",
            "Koolhydraten:",
            "Totaal koolhydraten",
            "Koolhydraat",
            "Koolhydr.",
            "Koolhydraten (g)",
            "Kool hydraten",
            "Koolhydraten / Glucides",
            "Glucides / Koolhydraten",
            "Koolhydraten waarvan suikers",
        )
        report(
            "Dutch carbohydrate spellings, per-100-g table",
            spellings.map { spelling ->
                spelling to table(
                    header = listOf("per", "100", "g"),
                    rows = listOf(
                        "Vetten" to listOf("12"),
                        spelling to listOf("62"),
                        "waarvan suikers" to listOf("35"),
                        "Eiwitten" to listOf("6,2"),
                    ),
                )
            },
        )
    }

    /** The basis header, which decides whether a found value can be placed at all. */
    @Test
    fun `how Dutch basis headers are recognised`() {
        val headers = listOf(
            listOf("per", "100", "g"),
            listOf("per", "100", "gram"),
            listOf("per", "100g"),
            listOf("Voedingswaarde", "per", "100", "g"),
            listOf("Voedingswaarden", "per", "100", "g"),
            listOf("Gemiddelde", "voedingswaarde", "per", "100", "g"),
            listOf("Voedingswaarde", "100", "g"),
            listOf("Voedingswaarde", "(100", "g)"),
            listOf("per", "100", "ml"),
            listOf("per", "100", "milliliter"),
            listOf("Per", "100", "ML"),
        )
        report(
            "Dutch basis headers",
            headers.map { header ->
                header.joinToString(" ") to table(
                    header = header,
                    rows = listOf(
                        "Vetten" to listOf("12"),
                        "Koolhydraten" to listOf("62"),
                        "waarvan suikers" to listOf("35"),
                    ),
                )
            },
        )
    }

    /** Child-nutrient rows: every one of these must refuse to become the total. */
    @Test
    fun `how Dutch child nutrient rows are classified`() {
        val children = listOf(
            "waarvan suikers",
            "Waarvan suikers",
            "w.v. suikers",
            "wv suikers",
            "waarvan toegevoegde suikers",
            "waarvan verzadigde vetzuren",
            "Vezels",
            "Voedingsvezels",
            "Voedingsvezel",
            "Vezelstoffen",
            "Zetmeel",
            "Polyolen",
            "Suikeralcoholen",
            "waarvan sacharose",
            "waarvan melksuiker",
            "waarvan druivensuiker",
            "waarvan vruchtensuiker",
            "waarvan meervoudige alcoholen",
        )
        // The stage verdict alone proves nothing about the outcome. A row typed TOTAL_CARBOHYDRATE
        // can still be safe, because CarbohydrateTermAnchor binds each number to the nearest nutrient
        // name on its left and refuses one introduced by a non-carbohydrate term. What matters is the
        // value the pipeline hands back, so both the stage and the end-to-end result are printed and
        // only the second one decides "unsafe".
        //
        // The total is 62 and every child value is 35. Returning 35 is the confident-wrong this whole
        // architecture exists to prevent; NotFound is a pass.
        println("\n=== Dutch child nutrients: separate row, then merged onto the total row ===")
        println(String.format("    %-34s %-20s %-16s %s", "child term", "RowClassifier", "separate row", "merged row"))
        children.forEach { child ->
            val row = LogicalRow(
                elements = listOf(
                    e("Koolhydraten", 40, 200, 240, 230, line = 0),
                    e(child, 260, 200, 500, 230, line = 0),
                ),
                box = OcrBox(40, 200, 500, 230),
                sourceLines = setOf(LineKey(0, 0)),
            )
            val kind = RowClassifier.classify(row)

            val separate = outcome(
                table(
                    header = listOf("per", "100", "g"),
                    rows = listOf(
                        "Koolhydraten" to listOf("62"),
                        child to listOf("35"),
                    ),
                ),
            )
            val merged = outcome(mergedRow(child))

            val unsafe = listOf(separate, merged).any { it.startsWith("Confident 35") || it.contains("35.0,") || it.contains(", 35.0") }
            println(
                String.format(
                    "  %-34s %-20s %-16s %-16s %s",
                    child, kind, separate.take(15), merged.take(15),
                    if (unsafe) "*** RETURNS THE CHILD VALUE ***" else "",
                ),
            )
        }
    }

    /**
     * The row-merge case, modelled the way it actually happens on a photograph.
     *
     * Two *printed* rows collapse into one reconstructed row, so the child keeps its own place in the
     * **label column** and its value keeps its place in the **same value column** as the total — the
     * boxes simply overlap vertically enough (25 of 40 px here) that `LogicalRowBuilder` cannot
     * separate them. That is the geometry the 2026-08-16 tilt bug produced.
     *
     * An earlier version of this helper laid the child out to the *right* of the total's value, in no
     * column at all. Every case then "passed" — but only because the child's number was unplaceable,
     * which is the geometry saving the parser rather than the vocabulary doing its job. Modelling the
     * safe version of a hazard proves nothing about the hazard.
     */
    private fun mergedRow(child: String): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += e("per", 300, 100, 366, 130, line = 0)
        elements += e("100", 378, 100, 444, 130, line = 0)
        elements += e("g", 456, 100, 478, 130, line = 0)

        elements += e("Koolhydraten", 40, 200, 208, 240, line = 1)
        elements += e("62", 380, 200, 424, 240, line = 1, block = 4)

        var x = 60
        child.split(' ').forEach { word ->
            elements += e(word, x, 215, x + 14 * word.length, 255, line = 1)
            x += 14 * word.length + 10
        }
        elements += e("35", 380, 215, 424, 255, line = 1, block = 4)

        elements += e("Eiwitten", 40, 320, 152, 360, line = 2)
        elements += e("6,2", 380, 320, 446, 360, line = 2, block = 4)
        return OcrDocument(width = 1200, height = 500, elements = elements)
    }

    /** A per-portion column beside per-100, the arrangement most Dutch supermarket labels use. */
    @Test
    fun `how Dutch serving columns are recognised`() {
        val servingHeaders = listOf(
            "per portie",
            "per portie (30 g)",
            "per stuk",
            "per plak",
            "per plakje",
            "per sneetje",
            "per zakje",
            "per beker",
            "per glas",
            "per eetlepel",
            "gemiddelde portie",
        )
        report(
            "per 100 g + a Dutch serving column",
            servingHeaders.map { serving ->
                serving to twoColumnTable(
                    first = listOf("per", "100", "g"),
                    second = serving.split(' '),
                    rows = listOf(
                        "Koolhydraten" to listOf("62", "18,6"),
                        "waarvan suikers" to listOf("35", "10,5"),
                    ),
                )
            },
        )
    }
}
