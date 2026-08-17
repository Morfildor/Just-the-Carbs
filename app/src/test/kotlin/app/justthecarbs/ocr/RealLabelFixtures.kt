package app.justthecarbs.ocr

/**
 * Reconstructions of the two real packages that failed on a physical device.
 *
 * Every pre-existing OCR fixture in this suite places each printed row's elements at an **identical**
 * top/bottom — a perfectly axis-aligned table that only exists when the image is a rendered mock. A
 * hand-held photograph of a package is never axis-aligned: the same printed row drifts steadily down
 * (or up) across the table's width, so the label at the left and the value at the right sit at
 * measurably different y. That drift is the variable the old fixtures held at zero, and it is the one
 * that broke row reconstruction in the field, so it is a first-class parameter here.
 *
 * [slopePercent] is vertical drift as a percentage of horizontal distance. 5% across a 700 px table
 * is 35 px — about one row pitch, and only ~2.9° of camera tilt. That is an ordinary photograph, not
 * a bad one.
 *
 * These are **not** the supplied photographs and cannot stand in for them. They are geometry
 * regressions: they pin the row/column/terminology behaviour that the real labels demand, at
 * realistic skew. Running the actual images through ML Kit is
 * `RealImageOcrTest` (androidTest), which is skipped until the photographs are dropped in.
 */
internal class SlopedLabel(
    private val slopePercent: Double,
    private val glyphHeight: Int = 20,
    private val originX: Int = 60,
) {
    private val elements = mutableListOf<OcrElement>()
    private var nextLine = 0

    /**
     * One printed row. [block] and [line] default to a fresh line per call, but callers pass explicit
     * values to reproduce ML Kit's real segmentation: a table's left-hand nutrient name and its
     * right-hand value cells almost never share a recognized line.
     */
    fun row(
        top: Int,
        vararg words: Pair<String, IntRange>,
        block: Int = 0,
        line: Int = nextLine++,
        height: Int = glyphHeight,
    ) {
        words.forEach { (text, xs) ->
            val drift = ((xs.first - originX) * slopePercent / 100.0).toInt()
            elements += OcrElement(
                text = text,
                box = OcrBox(xs.first, top + drift, xs.last, top + drift + height),
                blockId = block,
                lineId = line,
            )
        }
    }

    fun document(width: Int = 800, height: Int = 700) =
        OcrDocument(width = width, height = height, elements = elements.toList())
}

internal object RealLabelFixtures {

    /**
     * Sondey / Lidl biscuits: a trilingual NL/FR/DE table with a decimal comma.
     *
     * The nutrient name wraps across printed lines while its value stays on the first, and the total
     * and its "waarvan suikers" child are adjacent — the arrangement in which a merged row silently
     * becomes a child row and the whole reading is lost.
     */
    fun sondey(slopePercent: Double): OcrDocument = SlopedLabel(slopePercent).apply {
        row(
            100,
            "Gemiddelde" to 60..190, "voedingswaarde/Valeurs" to 198..430,
            block = 0, line = 0,
        )
        row(134, "nutritionnelles" to 60..220, "moyennes/Nährwerte" to 228..440, block = 0, line = 1)
        // The real basis header is "ø/100 g" — the average symbol is part of the printed cell, and
        // it must not stop the per-100 basis being recognised.
        row(168, "ø/100" to 380..470, "g" to 478..495, block = 1, line = 0)

        row(230, "Energie/Énergie/Energie" to 60..300, block = 2, line = 0)
        row(230, "2036" to 330..400, "kJ/486" to 408..490, "kcal" to 498..545, block = 3, line = 0)

        row(264, "Vetten/Matières" to 60..250, "grasses/Fett" to 258..390, block = 4, line = 0)
        row(264, "24,0" to 400..465, "g" to 473..490, block = 5, line = 0)

        row(298, "waarvan" to 60..165, "verzadigde" to 173..300, "vetzuren/dont" to 308..460, block = 6, line = 0)
        row(332, "davon" to 60..140, "gesättigte" to 148..270, "Fettsäuren" to 278..400, block = 6, line = 1)
        row(332, "14,3" to 400..465, "g" to 473..490, block = 7, line = 0)

        row(366, "Koolhydraten/Glucides/Kohlenhydrate" to 60..420, block = 8, line = 0)
        row(366, "61,9" to 430..495, "g" to 503..520, block = 9, line = 0)

        row(400, "waarvan" to 60..165, "suikers/dont" to 173..320, "sucres/davon" to 328..470, block = 10, line = 0)
        row(400, "47,6" to 480..545, "g" to 553..570, block = 11, line = 0)
        row(434, "Zucker" to 60..150, block = 10, line = 1)

        row(468, "Vezels/Fibres" to 60..230, "alimentaires/Ballaststoffe" to 238..470, block = 12, line = 0)
        row(468, "1,5" to 480..530, "g" to 538..555, block = 13, line = 0)
        row(502, "Zout/Sel/Salz" to 60..220, block = 14, line = 0)
        row(502, "0,50" to 430..495, "g" to 503..520, block = 15, line = 0)
    }.document()

    /**
     * Kinder / Ferrero chocolate: three value columns, one of them a reference percentage, and a
     * per-piece column whose serving weight is printed on a second header line.
     *
     * The sugars row repeats the carbohydrate row's per-piece figure (6,7) exactly, so a parser that
     * lets geometry outvote row type has a convenient wrong answer available on every column.
     */
    fun kinder(slopePercent: Double): OcrDocument = SlopedLabel(slopePercent).apply {
        // Row pitch 34 px against 20 px glyphs — 1.7x, the ordinary density of a printed
        // nutrition table. The earlier draft of this fixture used a 50 px pitch, which is
        // unrealistically airy and hid the row-chaining failure at every tilt worth testing.
        row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
        row(200, "per" to 560..605, "stuk" to 613..680, block = 1, line = 0)
        row(200, "%" to 730..755, block = 2, line = 0)
        // The serving weight is a second header line under "per stuk", not part of that phrase.
        row(234, "(12,5" to 560..640, "g)" to 648..690, block = 1, line = 1)

        row(288, "Energie" to 60..170, block = 3, line = 0)
        row(288, "2270" to 405..470, "kJ" to 478..515, block = 4, line = 0)

        row(322, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 5, line = 0)
        row(322, "53,5" to 405..470, "g" to 478..495, block = 6, line = 0)
        row(322, "6,7" to 580..635, "g" to 643..660, block = 7, line = 0)
        row(322, "3" to 725..740, "%" to 748..765, block = 8, line = 0)

        row(356, "waarvan" to 80..185, "suikers" to 193..290, "/" to 298..308, "sucres" to 316..400, block = 9, line = 0)
        row(356, "53,3" to 405..470, "g" to 478..495, block = 10, line = 0)
        row(356, "6,7" to 580..635, "g" to 643..660, block = 11, line = 0)
        row(356, "7" to 725..740, "%" to 748..765, block = 12, line = 0)
    }.document()
}
