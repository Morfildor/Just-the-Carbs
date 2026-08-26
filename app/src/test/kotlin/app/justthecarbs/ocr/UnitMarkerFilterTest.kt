package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * A unit marker misrecognised as a digit must not become a carbohydrate value.
 *
 * The measured event: ML Kit read the Kinder package's printed `(g)` as `(9)`, and `9` then became a
 * **confident** carbohydrate reading, beating the real `53,5`. Every existing guard passed it — right
 * row, right nutrient anchor, standalone token, and 9 g/100 g is a legitimate figure.
 *
 * The negatives are the load-bearing half of this class. A single-digit carbohydrate value is
 * completely ordinary, so a filter that keys on "small number" would destroy correct readings on
 * every low-carbohydrate product. These tests pin that the discriminator is **position**, never
 * magnitude.
 */
class UnitMarkerFilterTest {

    private fun element(text: String, left: Int, top: Int, width: Int = 60, height: Int = 20) =
        OcrElement(text, OcrBox(left, top, left + width, top + height), blockId = 0, lineId = 0)

    private fun row(y: Int, vararg cells: Pair<String, Int>): LogicalRow {
        val elements = cells.map { (text, x) -> element(text, x, y) }.sortedBy { it.box.left }
        return LogicalRow(
            elements = elements,
            box = elements.drop(1).fold(elements.first().box) { box, e -> box.union(e.box) },
            sourceLines = emptySet(),
        )
    }

    private fun markers(target: LogicalRow, all: List<LogicalRow>): Set<Int> =
        UnitMarkerFilter.markerElementIndices(target, all, medianHeight = 20)

    private fun textsExcluded(target: LogicalRow, all: List<LogicalRow>): List<String> =
        markers(target, all).sorted().map { target.elements[it].text }

    // ---------------------------------------------------------------- the measured failure

    @Test
    fun `a bracketed g misread as 9 between name and value is not a value`() {
        // The exact shape from the Kinder recognition: the printed "(g)" annotation between the
        // nutrient name and the value column came back as "(9)".
        val energy = row(100, "Energie" to 100, "(kJ)" to 400, "2247" to 700)
        val fat = row(140, "Fett" to 100, "(g)" to 400, "29,5" to 700)
        val carbs = row(180, "Kohlenhydrate" to 100, "(9)" to 400, "53,5" to 700)
        val protein = row(220, "Eiweiss" to 100, "(g)" to 400, "8,7" to 700)
        val all = listOf(energy, fat, carbs, protein)

        assertEquals(
            "the (9) annotation must be excluded",
            listOf("(9)"),
            textsExcluded(carbs, all),
        )
    }

    @Test
    fun `an UNBRACKETED corrupted marker is deliberately NOT excluded`() {
        // Documents a deliberate limit, not an oversight. An unbracketed leading number on a nutrient
        // row is genuinely ambiguous: on a table whose columns did not resolve, that token may be the
        // real value. Excluding it would cost correct readings on labels this app currently reads, so
        // the bracket is required as the evidence that the token is an annotation.
        val energy = row(100, "Energie" to 100, "kJ" to 400, "2247" to 700)
        val fat = row(140, "Vetten" to 100, "g" to 400, "29,5" to 700)
        val carbs = row(180, "Koolhydraten" to 100, "9" to 400, "46,0" to 700)
        val all = listOf(energy, fat, carbs)

        assertTrue(textsExcluded(carbs, all).isEmpty())
    }

    @Test
    fun `a corrupted ml marker is excluded on the same structural grounds`() {
        // "(ml)" misrecognised leaves a bracketed numeric token in the annotation position. Nothing
        // about the rule is specific to the letter g.
        val energy = row(100, "Energie" to 100, "(kJ)" to 400, "180" to 700)
        val fat = row(140, "Vetten" to 100, "(ml)" to 400, "1,2" to 700)
        val carbs = row(180, "Koolhydraten" to 100, "(1)" to 400, "9,4" to 700)
        val all = listOf(energy, fat, carbs)

        assertEquals(listOf("(1)"), textsExcluded(carbs, all))
    }

    // ---------------------------------------------------------------- legitimate values survive

    @Test
    fun `a genuine single digit carbohydrate value in the value column survives`() {
        // 9 g per 100 g is an entirely ordinary figure. This is the case a magnitude-based rule would
        // destroy, and it is why the filter keys on position instead.
        val energy = row(100, "Energie" to 100, "(kJ)" to 400, "180" to 700)
        val fat = row(140, "Vetten" to 100, "(g)" to 400, "1,2" to 700)
        val carbs = row(180, "Koolhydraten" to 100, "(g)" to 400, "9" to 700)
        val all = listOf(energy, fat, carbs)

        assertFalse(
            "a value in the value column is not a marker whatever its magnitude",
            textsExcluded(carbs, all).contains("9"),
        )
    }

    @Test
    fun `a real 9 g carbohydrate row with a fused unit survives`() {
        val energy = row(100, "Energie" to 100, "(kJ)" to 400, "180" to 700)
        val fat = row(140, "Vetten" to 100, "(g)" to 400, "1,2" to 700)
        val carbs = row(180, "Koolhydraten" to 100, "(g)" to 400, "9 g" to 700)
        val all = listOf(energy, fat, carbs)

        assertTrue("nothing on this row is in a marker position", textsExcluded(carbs, all).isEmpty())
    }

    @Test
    fun `a table that prints no unit markers at all is untouched`() {
        // The common label shape: units fused to values, nothing bracketed anywhere.
        val energy = row(100, "Energie" to 100, "180 kJ" to 700)
        val fat = row(140, "Vetten" to 100, "1,2 g" to 700)
        val carbs = row(180, "Koolhydraten" to 100, "9,4 g" to 700)
        val all = listOf(energy, fat, carbs)

        assertTrue(textsExcluded(carbs, all).isEmpty())
    }

    @Test
    fun `a trailing unit printed to the RIGHT of the value never excludes the value`() {
        // The sondey canary's shape, and the regression that killed the first design of this filter.
        // Real labels overwhelmingly print "61,9" then "g", so any rule keyed on where units repeat
        // identifies the VALUE column. This pins that the value survives.
        val fat = row(140, "Vetten" to 100, "24,0" to 400, "g" to 480)
        val carbs = row(180, "Koolhydraten" to 100, "61,9" to 400, "g" to 480)
        val sugars = row(220, "waarvan suikers" to 100, "47,6" to 400, "g" to 480)
        val all = listOf(fat, carbs, sugars)

        assertTrue(
            "the value must survive a table that prints its units to the right",
            textsExcluded(carbs, all).isEmpty(),
        )
    }

    // ---------------------------------------------------------------- bracketing signal

    @Test
    fun `a bracketed number directly after the nutrient name is a marker`() {
        // No other row prints a marker at all. The positional argument stands on its own: a
        // bracketed token between the nutrient name and the first value is an annotation.
        val carbs = row(180, "Koolhydraten" to 100, "(9)" to 400, "53,5" to 700)
        val all = listOf(carbs)

        assertEquals(listOf("(9)"), textsExcluded(carbs, all))
    }

    @Test
    fun `a bracketed number appearing after a real value is left alone`() {
        // Past the first value the row is in value territory. A parenthesised figure there is
        // something else — a per-serving column, a reference intake — and this filter has no business
        // deciding about it.
        val carbs = row(180, "Koolhydraten" to 100, "53,5" to 400, "(6,7)" to 700)
        val all = listOf(carbs)

        assertTrue(textsExcluded(carbs, all).isEmpty())
    }

    @Test
    fun `a bracketed number with no nutrient name to its left is left alone`() {
        // Nothing on the row claims it as an annotation, so the positional argument does not hold.
        val orphan = row(180, "(9)" to 100, "53,5" to 400)
        val all = listOf(orphan)

        assertTrue(textsExcluded(orphan, all).isEmpty())
    }

    @Test
    fun `parenthesised text that is not numeric is irrelevant`() {
        val carbs = row(180, "Koolhydraten" to 100, "(waarvan)" to 400, "53,5" to 700)
        val all = listOf(carbs)

        assertTrue(textsExcluded(carbs, all).isEmpty())
    }

    // ---------------------------------------------------------------- end to end through the parser

    @Test
    fun `the interpreter never returns a corrupted unit marker as the carbohydrate value`() {
        // The measured confident-wrong, reproduced at the boundary that produced it.
        //
        // The geometry matters and is not incidental: the resolved basis column sits over the MARKER
        // column, not over the value column. That is what happened on the real capture — the column
        // structure had been damaged — and it is the only arrangement in which `(9)` is placeable at
        // all. With the basis column over the value column instead, `(9)` is refused as "no column"
        // and this fixture would pass with or without the filter, proving nothing.
        //
        // NEGATIVE CONTROL, verified by disabling UnitMarkerFilter and re-running: this exact
        // document yields `Confident 9.0 PER_100_G` — the printed value is 53,5. With the filter it
        // is NotFound. If this test ever passes with the filter removed, it has stopped pinning
        // anything.
        val elements = buildList {
            add(element("Nahrwerte", 100, 10, width = 200))
            add(element("pro 100 g", 390, 50, width = 90))

            add(element("Energie", 100, 100, width = 200))
            add(element("(kJ)", 400, 100))
            add(element("2247", 700, 100))

            add(element("Fett", 100, 140, width = 200))
            add(element("(g)", 400, 140))
            add(element("29,5", 700, 140))

            add(element("Kohlenhydrate", 100, 180, width = 200))
            add(element("(9)", 400, 180))
            add(element("53,5", 700, 180))

            add(element("Eiweiss", 100, 220, width = 200))
            add(element("(g)", 400, 220))
            add(element("8,7", 700, 220))
        }

        val reading = NutritionTableParser.parseWithDiagnostics(OcrDocument(1000, 400, elements)).reading

        // NotFound, not 53.5: the real value genuinely has no column to be read from once the marker
        // column is the only one resolved. Refusing is the correct outcome — the alternative on offer
        // was a confident 9.
        assertEquals(
            "a misread unit marker must never become a confident value",
            LabelReading.NotFound,
            reading,
        )
    }

    @Test
    fun `a corrupted marker does not stop a well formed table from reading correctly`() {
        // The same corrupted `(9)`, but with the basis column where a well-formed table puts it. The
        // marker is excluded and the real value is still read confidently, so the filter costs
        // nothing on a table whose structure survived.
        val elements = buildList {
            add(element("Nahrwerte", 100, 10, width = 200))
            add(element("pro 100 g", 690, 50, width = 90))

            add(element("Energie", 100, 100, width = 200))
            add(element("(kJ)", 400, 100))
            add(element("2247", 700, 100))

            add(element("Kohlenhydrate", 100, 180, width = 200))
            add(element("(9)", 400, 180))
            add(element("53,5", 700, 180))
        }

        val reading = NutritionTableParser.parseWithDiagnostics(OcrDocument(1000, 400, elements)).reading

        assertTrue("expected the printed 53.5, got $reading", reading is LabelReading.Confident)
        reading as LabelReading.Confident
        assertEquals(0, reading.candidate.value.compareTo(BigDecimal("53.5")))
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    @Test
    fun `a genuine low carbohydrate table still reads confidently end to end`() {
        // The regression this filter must not cause: a real value below 10 in a table that also
        // prints unit markers.
        val elements = buildList {
            add(element("Nahrwerte", 100, 10, width = 200))
            add(element("pro 100 g", 690, 50, width = 90))

            add(element("Energie", 100, 100, width = 200))
            add(element("(kJ)", 400, 100))
            add(element("180", 700, 100))

            add(element("Fett", 100, 140, width = 200))
            add(element("(g)", 400, 140))
            add(element("1,2", 700, 140))

            add(element("Kohlenhydrate", 100, 180, width = 200))
            add(element("(g)", 400, 180))
            add(element("9", 700, 180))
        }

        val report = NutritionTableParser.parseWithDiagnostics(OcrDocument(1000, 400, elements))
        val reading = report.reading

        assertTrue("expected a confident 9, got $reading", reading is LabelReading.Confident)
        reading as LabelReading.Confident
        assertEquals(0, reading.candidate.value.compareTo(BigDecimal("9")))
    }
}
