package app.justthecarbs.ocr

/**
 * Geometry for synthetic Dutch nutrition tables, shared by [DutchLabelDiagnosticTest] (which prints)
 * and `DutchNutritionTableTest` (which asserts).
 *
 * Shared deliberately. The diagnostic is what a future session runs when a Dutch label fails in
 * someone's hand, and the regression test is what stops today's fixes eroding; if they drew their
 * tables differently, a finding in one would not be reproducible in the other.
 *
 * ## The layout rule that matters
 *
 * **A header phrase is centred over the column it describes**, and a leading descriptive noun
 * (`Voedingswaarde`) sits at the left in the label column, which is where a package prints it.
 *
 * The first version of this harness laid every header word left-to-right from one origin, so a long
 * header like `Voedingswaarde per 100 g` pushed its own `per 100 g` hundreds of pixels right of the
 * values it heads. Six Dutch headers "failed" that were never broken. Acting on that would have
 * meant tuning the parser against a picture no package resembles — the same class of mistake as the
 * geometric regression test in this repo's history that turned out to be measuring the soft keyboard.
 */
internal object DutchLabelFixtures {

    /** Value-column centres. Far apart so two header phrases cannot overlap and interleave. */
    private val COLUMN_CENTRES = listOf(380, 900)

    /** Tokens that begin a measurement phrase rather than describing the table as a whole. */
    private val PHRASE_STARTERS = setOf("per", "pro", "par", "100", "100g", "100ml")

    fun element(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    /** A one- or two-column table: header phrase over column 1, one row per nutrient. */
    fun table(header: List<String>, rows: List<Pair<String, List<String>>>): OcrDocument {
        val elements = mutableListOf<OcrElement>()

        val phraseStart = header.indexOfFirst { it.trim('(', ')').lowercase() in PHRASE_STARTERS }
        val leading = if (phraseStart > 0) header.take(phraseStart) else emptyList()
        val phrase = if (phraseStart > 0) header.drop(phraseStart) else header

        var leadX = 40
        leading.forEach { word ->
            elements += element(word, leadX, 100, leadX + 18 * word.length, 130, line = 0)
            leadX += 18 * word.length + 10
        }
        elements += phraseElements(phrase, COLUMN_CENTRES.first())

        rows.forEachIndexed { index, (label, values) ->
            val top = 200 + index * 60
            var labelX = 40
            label.split(' ').forEach { word ->
                elements += element(word, labelX, top, labelX + 14 * word.length, top + 30, line = index + 1)
                labelX += 14 * word.length + 10
            }
            values.forEachIndexed { column, value ->
                val width = 22 * value.length
                val vx = COLUMN_CENTRES[column] - width / 2
                elements += element(value, vx, top, vx + width, top + 30, line = index + 1, block = 4)
            }
        }
        return OcrDocument(width = 1200, height = 200 + rows.size * 60 + 60, elements = elements)
    }

    /** [table] plus a second header phrase centred over the second value column. */
    fun twoColumnTable(
        first: List<String>,
        second: List<String>,
        rows: List<Pair<String, List<String>>>,
    ): OcrDocument {
        val base = table(first, rows)
        return base.copy(elements = base.elements + phraseElements(second, COLUMN_CENTRES[1]))
    }

    private fun phraseElements(phrase: List<String>, centre: Int): List<OcrElement> {
        var x = centre - phrase.sumOf { 22 * it.length + 12 } / 2
        return phrase.map { word ->
            val e = element(word, x, 100, x + 22 * word.length, 130, line = 0)
            x += 22 * word.length + 12
            e
        }
    }

    /**
     * Two printed rows collapsed into one reconstructed row — the tilt/row-merge case.
     *
     * The child keeps its place in the **label column** and its value keeps the **same value column**
     * as the total; the boxes simply overlap vertically (25 of 40 px) by more than
     * `LogicalRowThresholds` can separate. That is the geometry the 2026-08-16 chaining bug produced,
     * and it is the only arrangement in which the child's number competes for the total's cell.
     *
     * Laying the child out to the *right* instead — as an earlier draft did — puts its number in no
     * column at all, so every case "passes" because the value was unplaceable. Modelling the safe
     * version of a hazard proves nothing about the hazard.
     */
    fun mergedTotalAndChildRow(child: String, total: String = "62", childValue: String = "35"): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += element("per", 300, 100, 366, 130, line = 0)
        elements += element("100", 378, 100, 444, 130, line = 0)
        elements += element("g", 456, 100, 478, 130, line = 0)

        elements += element("Koolhydraten", 40, 200, 208, 240, line = 1)
        elements += element(total, 380, 200, 380 + 22 * total.length, 240, line = 1, block = 4)

        var x = 60
        child.split(' ').forEach { word ->
            elements += element(word, x, 215, x + 14 * word.length, 255, line = 1)
            x += 14 * word.length + 10
        }
        elements += element(childValue, 380, 215, 380 + 22 * childValue.length, 255, line = 1, block = 4)

        elements += element("Eiwitten", 40, 320, 152, 360, line = 2)
        elements += element("6,2", 380, 320, 446, 360, line = 2, block = 4)
        return OcrDocument(width = 1200, height = 500, elements = elements)
    }

    /** Human-readable outcome, used by both the diagnostic's report and the assertions' messages. */
    fun outcome(document: OcrDocument): String {
        val report = NutritionTableInterpreter.interpret(document)
        return when (val reading = report.reading) {
            is LabelReading.Confident -> "Confident ${reading.candidate.value} ${reading.candidate.basis}"
            is LabelReading.Ambiguous -> "Ambiguous(${reading.candidates.size}) ${reading.candidates.map { it.value }}"
            is LabelReading.NotFound -> "NotFound"
        }
    }

    /** Every value the reading offers the user, however it is framed. */
    fun offeredValues(document: OcrDocument): List<String> {
        return when (val reading = NutritionTableInterpreter.interpret(document).reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value.stripTrailingZeros().toPlainString())
            is LabelReading.Ambiguous ->
                reading.candidates.map { it.value.stripTrailingZeros().toPlainString() }
            is LabelReading.NotFound -> emptyList()
        }
    }

    /** The Dutch carbohydrate-child terms this pass added, plus the ones that already worked. */
    val CHILD_TERMS = listOf(
        "waarvan suikers", "Waarvan suikers", "w.v. suikers", "wv suikers",
        "waarvan toegevoegde suikers",
        "waarvan sacharose", "waarvan melksuiker", "waarvan druivensuiker", "waarvan vruchtensuiker",
        "Vezels", "Voedingsvezels", "Voedingsvezel", "Vezelstoffen",
        "Zetmeel", "Polyolen", "Suikeralcoholen", "waarvan meervoudige alcoholen",
    )

    /** Ways Dutch packaging prints and OCRs the word for "carbohydrates". */
    val CARBOHYDRATE_SPELLINGS = listOf(
        "Koolhydraten", "koolhydraten", "KOOLHYDRATEN", "Koolhydraten:", "Totaal koolhydraten",
        "Koolhydraat", "Koolhydr.", "Koolhydraten (g)", "Kool hydraten",
        "Koolhydraten / Glucides", "Glucides / Koolhydraten",
    )
}
