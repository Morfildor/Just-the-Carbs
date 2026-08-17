package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: which number on a total-carbohydrate row may be that row's value, and which columns may
// claim a cell at all.
//
// Both rules below exist because a table-shaped assumption was applied to a label that is not a
// table. Neither is a proximity score and neither loosens a geometric tolerance: they narrow what
// is eligible before any distance is measured.
//
// Every fixture here is synthetic and represents a *structural condition* observed on real
// packaging, never a particular photograph. No value, brand or coordinate is copied from a fixture.
class ProseRowValueBindingTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun document(width: Int = 1200, height: Int = 900, vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = width, height = height, elements = elements.toList())

    private fun valuesOf(reading: LabelReading): List<BigDecimal> = when (reading) {
        is LabelReading.Confident -> listOf(reading.candidate.value)
        is LabelReading.Ambiguous -> reading.candidates.map { it.value }
        LabelReading.NotFound -> emptyList()
    }

    // ---- a number another nutrient already claimed is not the carbohydrate's ---------------------
    //
    // The class of label: a running nutrition *sentence*, where one printed line ends mid-clause.
    // The line then carries the tail of the previous nutrient's clause ("...saturates: 19 g,") and
    // the head of the carbohydrate clause ("Carbohydrate: 3 g."). Geometry reconstructs that as one
    // logical row, and the row types TOTAL_CARBOHYDRATE correctly — it does name carbohydrate.
    //
    // Printed nutrition text names a nutrient and then states its value, so a number belongs to the
    // nearest nutrient name to its left. This is a structural claim about reading order, not a
    // distance, so a value sitting exactly under the basis column's x-centre cannot defeat it.

    @Test
    fun `a value another nutrient already claimed is never that row's carbohydrate figure`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 900,
                // A standalone basis declaration, as a prose label prints it: its own sentence.
                elements = arrayOf(
                    e("Per", 100, 100, 160, 140, line = 0),
                    e("100", 170, 100, 230, 140, line = 0),
                    e("g:", 240, 100, 270, 140, line = 0),
                    e("Energy", 285, 100, 400, 140, line = 0),
                    // The wrapped line: the saturates clause ends here, the carbohydrate clause
                    // starts here. "19" sits directly under the basis column's x-centre.
                    e("saturates:", 100, 200, 230, 240, line = 1),
                    e("19", 240, 200, 280, 240, line = 1),
                    e("g,", 285, 200, 315, 240, line = 1),
                    e("Carbohydrate:", 340, 200, 560, 240, line = 1),
                    e("3", 575, 200, 595, 240, line = 1),
                    e("g.", 600, 200, 630, 240, line = 1),
                ),
            ),
        ).reading

        assertFalse(
            "the preceding nutrient's figure was returned as total carbohydrate: $reading",
            valuesOf(reading).any { it.compareTo(BigDecimal("19")) == 0 },
        )
    }

    @Test
    fun `the ordinary table shape is unaffected — the label precedes its value`() {
        // The canary for the rule above: every real table prints the nutrient name and then its
        // cells, so the rule must never fire on one.
        val reading = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 900,
                elements = arrayOf(
                    e("per", 500, 100, 560, 140, line = 0),
                    e("100", 570, 100, 630, 140, line = 0),
                    e("g", 640, 100, 665, 140, line = 0),
                    e("Carbohydrate", 100, 200, 340, 240, line = 1),
                    e("45", 560, 200, 620, 240, line = 1),
                    e("g", 630, 200, 655, 240, line = 1),
                ),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(0, BigDecimal("45").compareTo((reading as LabelReading.Confident).candidate.value))
    }

    @Test
    fun `a table printing its label column to the RIGHT of its values still reads`() {
        // The canary that decided the rule's shape. An earlier draft excluded every number left of
        // the carbohydrate term, which reads this layout as prose and loses it. Anchoring on the
        // nearest nutrient name to the left instead means a number is only excluded when something
        // else on the row actually claimed it — here nothing does.
        val reading = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 900,
                elements = arrayOf(
                    e("per", 90, 20, 140, 60, line = 0),
                    e("100", 150, 20, 210, 60, line = 0),
                    e("g", 220, 20, 245, 60, line = 0),
                    e("48.2", 100, 150, 175, 190, line = 1),
                    e("g", 185, 150, 210, 190, line = 1),
                    e("Carbohydrate", 520, 150, 760, 190, line = 1),
                ),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(0, BigDecimal("48.2").compareTo((reading as LabelReading.Confident).candidate.value))
    }

    @Test
    fun `a multilingual row naming carbohydrate twice still reads the value after the first term`() {
        // European packaging prints the same nutrient in several languages before its cell. The
        // rule anchors on the LEFTMOST carbohydrate term so the whole language run stays to the
        // left of the value, exactly as printed.
        val reading = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 900,
                elements = arrayOf(
                    e("per", 500, 100, 560, 140, line = 0),
                    e("100", 570, 100, 630, 140, line = 0),
                    e("g", 640, 100, 665, 140, line = 0),
                    e("Koolhydraten", 100, 200, 300, 240, line = 1),
                    e("/", 310, 200, 320, 240, line = 1),
                    e("Glucides", 330, 200, 450, 240, line = 1),
                    e("/", 460, 200, 470, 240, line = 1),
                    e("Kohlenhydrate", 480, 200, 490, 240, line = 1),
                    e("61,9", 560, 200, 620, 240, line = 1),
                    e("g", 630, 200, 655, 240, line = 1),
                ),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(0, BigDecimal("61.9").compareTo((reading as LabelReading.Confident).candidate.value))
    }

    // ---- a column recovered from cell shape has a vertical extent ------------------------------
    //
    // The class of label: a crop that necessarily retains a second block of printed matter — a
    // reference-intake summary panel, a marketing paragraph, a second package in frame. Percentage
    // figures in that other block cluster on an x position of their own, and the header-less
    // percent-column fallback recovers a column from them.
    //
    // A column recovered from cells is only evidence about the band of rows those cells occupy. It
    // is not a claim about a table printed hundreds of pixels away that it never touched. Applying
    // it there vetoes the real per-100 cell and the reading is lost.
    //
    // Note this narrows what a recovered column may CLAIM. It does not prefer whichever column is
    // nearer the values, and it does not touch a column read from a header, which is a stated fact
    // about the table rather than an inference from cell shape.

    @Test
    fun `a percent column recovered from a distant block does not veto the real per-100 cell`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 1400,
                elements = arrayOf(
                    // The real table, near the top.
                    e("Voedingswaarde", 400, 100, 660, 140, line = 0),
                    e("per", 670, 100, 730, 140, line = 0),
                    e("100", 740, 100, 800, 140, line = 0),
                    e("g", 810, 100, 835, 140, line = 0),
                    e("koolhydraten", 400, 200, 640, 240, line = 1),
                    // The value cell sits to the right of its column header's centre, which is
                    // ordinary: a header phrase is wider than the figure beneath it.
                    e("5,0", 800, 200, 860, 240, line = 1),
                    e("g", 870, 200, 895, 240, line = 1),
                    // A separate reference-intake panel far below, with its own percent column.
                    e("Referentie", 100, 1000, 300, 1040, line = 2),
                    e("49%", 780, 1000, 860, 1040, line = 2),
                    e("Volwassene", 100, 1100, 320, 1140, line = 3),
                    e("3,0%", 780, 1100, 860, 1140, line = 3),
                ),
            ),
        )

        val reading = report.reading
        assertTrue("expected Confident, got $reading; ${report.diagnostics}", reading is LabelReading.Confident)
        assertEquals(0, BigDecimal("5.0").compareTo((reading as LabelReading.Confident).candidate.value))
    }

    // ---- the anchor itself ----------------------------------------------------------------------

    @Test
    fun `an unclaimed number defaults to the carbohydrate`() {
        // The rule never invents an owner. With no nutrient name to a number's left, the number
        // stays eligible — which is what keeps the label-column-on-the-right table above working
        // and what makes the rule strictly a narrowing of existing behaviour.
        assertTrue(CarbohydrateTermAnchor.isCarbohydrateValue(anchors = emptyList(), right = 500))
    }

    @Test
    fun `the nearest name to the left wins, not the first or the last on the row`() {
        val anchors = listOf(
            CarbohydrateTermAnchor.Anchor(left = 100, isCarbohydrate = false),
            CarbohydrateTermAnchor.Anchor(left = 400, isCarbohydrate = true),
            CarbohydrateTermAnchor.Anchor(left = 900, isCarbohydrate = false),
        )

        assertFalse("claimed by the name at 100", CarbohydrateTermAnchor.isCarbohydrateValue(anchors, right = 300))
        assertTrue("claimed by the carbohydrate at 400", CarbohydrateTermAnchor.isCarbohydrateValue(anchors, right = 700))
        assertFalse("claimed by the name at 900", CarbohydrateTermAnchor.isCarbohydrateValue(anchors, right = 1000))
    }

    @Test
    fun `a percent column recovered from the table's own rows still vetoes its cells`() {
        // The canary for the rule above. When the percent cells come from the table itself, the
        // recovered column overlaps the total row's band and must keep protecting it — this is the
        // job the fallback was added for and it must survive the narrowing.
        val reading = NutritionTableInterpreter.interpret(
            document(
                width = 1200,
                height = 900,
                elements = arrayOf(
                    e("per", 500, 100, 560, 140, line = 0),
                    e("100", 570, 100, 630, 140, line = 0),
                    e("g", 640, 100, 665, 140, line = 0),
                    e("Carbohydrate", 100, 200, 340, 240, line = 1),
                    e("45", 560, 200, 620, 240, line = 1),
                    e("17", 900, 200, 950, 240, line = 1),
                    e("%", 955, 200, 975, 240, line = 1),
                    e("Fat", 100, 300, 180, 340, line = 2),
                    e("1,5", 560, 300, 620, 340, line = 2),
                    e("2", 900, 300, 940, 340, line = 2),
                    e("%", 945, 300, 965, 340, line = 2),
                ),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(0, BigDecimal("45").compareTo((reading as LabelReading.Confident).candidate.value))
    }
}
