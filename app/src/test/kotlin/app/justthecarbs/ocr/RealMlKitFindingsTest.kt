package app.justthecarbs.ocr

import app.justthecarbs.domain.PortionUnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: defects found by running the two real packages through actual ML Kit
//
// Each case below reproduces something the synthetic fixtures could not, because each depends on
// how ML Kit really recognises a photograph rather than on text a human typed into a fixture. They
// are kept as fast JVM tests so they run without a device; RealImageOcrTest (androidTest) is what
// re-checks them against the photographs themselves.
class RealMlKitFindingsTest {

    private fun rowOf(vararg words: String): LogicalRow {
        val elements = words.mapIndexed { index, word ->
            OcrElement(word, OcrBox(60 + index * 90, 200, 60 + index * 90 + 80, 224), 0, 0)
        }
        return LogicalRow(
            elements = elements,
            box = elements.drop(1).fold(elements.first().box) { acc, e -> acc.union(e.box) },
            sourceLines = setOf(LineKey(0, 0)),
        )
    }

    // ---- A letter misread as a digit is not a value ------------------------------------------

    @Test
    fun `a digit welded inside a word is never a value`() {
        // ML Kit read Slovenian "Ogljikovi" as "0gjikovi" on the real Kinder photograph. That "0"
        // is a well-formed number and 0 g of carbohydrate is a legitimate figure, so it passed the
        // validator, became a second interpretation, and turned a correct confident 53.5 into an
        // ambiguity between 53.5 and 0 — asking the user to choose between the right answer and a
        // misread letter.
        val document = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(300, "0gjikovi" to 60..220, "hidrati" to 228..350, block = 1, line = 0)
            row(350, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 2, line = 0)
            row(350, "53,5" to 405..470, "g" to 478..495, block = 3, line = 0)
        }.document()

        val reading = NutritionTableParser.parse(document)
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            BigDecimal("53.5"),
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
    }

    @Test
    fun `letter-digit confusion in either direction is refused`() {
        listOf("0gjikovi", "4082LLAES", "x45", "1e", "Sl5vak").forEach { junk ->
            val document = SlopedLabel(0.0).apply {
                row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
                row(300, "Koolhydraten" to 60..220, junk to 228..380, block = 1, line = 0)
                row(300, "53,5" to 405..470, "g" to 478..495, block = 2, line = 0)
            }.document()

            val reading = NutritionTableParser.parse(document)
            assertTrue("'$junk' produced $reading", reading is LabelReading.Confident)
            assertEquals(
                "'$junk' contributed a value",
                BigDecimal("53.5"),
                (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
            )
        }
    }

    @Test
    fun `a figure fused to its unit is still a value`() {
        // The distinction that makes the rule above safe: "53,5g" is a cell, "0gjikovi" is a word.
        // Testing only that the suffix *starts* with "g" would accept both and fix nothing.
        val document = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(300, "Koolhydraten" to 60..220, block = 1, line = 0)
            row(300, "53,5g" to 405..495, block = 2, line = 0)
        }.document()

        assertEquals(
            BigDecimal("53.5"),
            (NutritionTableParser.parse(document) as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
    }

    // ---- A "per <unit>" line is a header ------------------------------------------------------

    @Test
    fun `a row naming only a countable serving is a header row`() {
        // On the real Kinder package the per-piece header spans several printed lines of eight
        // languages, and the line carrying "Par pièce" names no per-100 basis, no generic serving
        // word and no reference intake. It was typed OTHER, so ColumnClassifier — which only looks
        // at HEADER rows — never applied the countable-unit vocabulary it already had, and the
        // printed per-piece figure was discarded with "no column".
        assertEquals(
            NutritionRowKind.HEADER,
            RowClassifier.classify(rowOf("Hranive", "vrednost", "/", "Per", "/", "Par", "pièce")),
        )
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(rowOf("Per", "stuk")))
    }

    @Test
    fun `naming a nutrient still beats the header shape`() {
        // The ordering that keeps the change safe: a value row mentioning a serving unit is still a
        // value row, never demoted to a header that carries no figure.
        assertEquals(
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            RowClassifier.classify(rowOf("Koolhydraten", "per", "stuk", "6,7", "g")),
        )
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(rowOf("waarvan", "suikers", "per", "stuk", "6,7", "g")),
        )
    }

    // ---- The serving header as ML Kit actually delivers it ------------------------------------

    @Test
    fun `a serving header survives punctuation, a non-English connective and an accent`() {
        // The real matched header span is "/ Par pièce": a slash from the language separator, a
        // connective that is not the English "per", and an accent ServingSizeParser's unit table
        // does not carry. Each alone defeated the parse and silently dropped the per-piece portion.
        val document = SlopedLabel(0.0).apply {
            row(200, "ø/100" to 380..470, "g" to 478..495, block = 0, line = 0)
            row(200, "/" to 560..575, "Par" to 583..640, "pièce" to 648..730, block = 1, line = 0)
            row(300, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 2, line = 0)
            row(300, "53,5" to 405..470, "g" to 478..495, block = 3, line = 0)
            row(300, "6,7" to 590..645, "g" to 653..670, block = 4, line = 0)
        }.document()

        val report = NutritionTableParser.parseWithDiagnostics(document)
        assertEquals(BigDecimal("6.7"), report.servingCandidate?.carbsPerServing?.stripTrailingZeros())
        assertEquals(PortionUnitKind.PIECE, report.servingCandidate?.descriptor?.kind)
        assertEquals(BigDecimal.ONE, report.servingCandidate?.descriptor?.count?.stripTrailingZeros())
    }

    @Test
    fun `the average symbol does not stop a per-100 basis being recognised`() {
        // Sondey prints its basis as "ø/100 g", not "per 100 g".
        val document = SlopedLabel(0.0).apply {
            row(200, "ø/100" to 380..470, "g" to 478..495, block = 0, line = 0)
            row(300, "Koolhydraten/Glucides/Kohlenhydrate" to 60..350, block = 1, line = 0)
            row(300, "61,9" to 405..470, "g" to 478..495, block = 2, line = 0)
        }.document()

        val reading = NutritionTableParser.parse(document)
        assertTrue("got $reading", reading is LabelReading.Confident)
        assertEquals(
            app.justthecarbs.domain.NutritionBasis.PER_100_G,
            (reading as LabelReading.Confident).candidate.basis,
        )
    }

    // ---- Terminology that earned its place ----------------------------------------------------

    @Test
    fun `a merged Croatian sugars and German carbohydrate row is a child row`() {
        // ML Kit merged "od kojih šećeri" with "Kohlenhydrate" onto one recognized row on the real
        // photograph. Without the Croatian exclusion that row types as TOTAL on the strength of the
        // German word — a sugars-row-as-total waiting for a frame where it carries numbers.
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(rowOf("od", "kojih", "šećeri", "/od", "Kohlenhydrate", "tega", "siadkorji")),
        )
    }

    @Test
    fun `the languages printed on the real package all classify`() {
        listOf("Ugljikohidrati", "Ogljikovi hidrati", "Ugljeni hidrati", "Јаглехидрати", "Karbohidrate")
            .forEach { term ->
                assertEquals(
                    term,
                    NutritionRowKind.TOTAL_CARBOHYDRATE,
                    RowClassifier.classify(rowOf(*term.split(" ").toTypedArray(), "53,5", "g")),
                )
            }
        listOf("šećeri", "sladkorji", "шеќери", "sheqerna").forEach { term ->
            assertEquals(
                term,
                NutritionRowKind.CARBOHYDRATE_CHILD,
                RowClassifier.classify(rowOf(term, "53,3", "g")),
            )
        }
    }
}
