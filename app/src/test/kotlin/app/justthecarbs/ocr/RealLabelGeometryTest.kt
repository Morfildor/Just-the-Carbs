package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: the two real packages that failed on a physical device, at realistic camera tilt.
// Invariant: a legible, conventional nutrition table photographed by hand must read its TOTAL
// carbohydrate — and must never substitute a child nutrient, a reference percentage, or a
// per-serving figure for it. See RealLabelFixtures for why slope is a first-class parameter.
class RealLabelGeometryTest {

    private fun readingOf(document: OcrDocument) = NutritionTableParser.parse(document)

    private fun confidentValue(document: OcrDocument): Pair<BigDecimal, NutritionBasis?> {
        val reading = readingOf(document)
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        return candidate.value.stripTrailingZeros() to candidate.basis
    }

    // ---- Sondey ------------------------------------------------------------------------------

    @Test
    fun `sondey reads total carbohydrate when the table is axis-aligned`() {
        val (value, basis) = confidentValue(RealLabelFixtures.sondey(slopePercent = 0.0))
        assertEquals(BigDecimal("61.9"), value)
        assertEquals(NutritionBasis.PER_100_G, basis)
    }

    @Test
    fun `sondey reads total carbohydrate through ordinary hand-held tilt`() {
        // ~2.9 degrees. The value that broke on the real package.
        val (value, basis) = confidentValue(RealLabelFixtures.sondey(slopePercent = 5.0))
        assertEquals(BigDecimal("61.9"), value)
        assertEquals(NutritionBasis.PER_100_G, basis)
    }

    @Test
    fun `sondey never reports its sugars figure as total carbohydrate`() {
        listOf(0.0, 2.0, 5.0, 8.0).forEach { slope ->
            val reading = readingOf(RealLabelFixtures.sondey(slope))
            val values = when (reading) {
                is LabelReading.Confident -> listOf(reading.candidate.value)
                is LabelReading.Ambiguous -> reading.candidates.map { it.value }
                LabelReading.NotFound -> emptyList()
            }
            assertTrue(
                "sugars 47.6 offered as total carbohydrate at $slope% slope",
                values.none { it.compareTo(BigDecimal("47.6")) == 0 },
            )
        }
    }

    // ---- Kinder ------------------------------------------------------------------------------

    @Test
    fun `kinder reads the per-100 g column through ordinary hand-held tilt`() {
        val (value, basis) = confidentValue(RealLabelFixtures.kinder(slopePercent = 5.0))
        assertEquals(BigDecimal("53.5"), value)
        assertEquals(NutritionBasis.PER_100_G, basis)
    }

    @Test
    fun `kinder never offers the reference percentage or a child nutrient as total carbohydrate`() {
        listOf(0.0, 2.0, 5.0, 8.0).forEach { slope ->
            val reading = readingOf(RealLabelFixtures.kinder(slope))
            val values = when (reading) {
                is LabelReading.Confident -> listOf(reading.candidate.value)
                is LabelReading.Ambiguous -> reading.candidates.map { it.value }
                LabelReading.NotFound -> emptyList()
            }
            listOf("3", "7", "53.3").forEach { forbidden ->
                assertTrue(
                    "$forbidden offered as total carbohydrate at $slope% slope",
                    values.none { it.compareTo(BigDecimal(forbidden)) == 0 },
                )
            }
        }
    }

    @Test
    fun `kinder reads the per-piece carbohydrate figure from the serving column`() {
        val report = NutritionTableParser.parseWithDiagnostics(RealLabelFixtures.kinder(slopePercent = 5.0))
        val serving = report.servingCandidate
        assertNotNull("no serving candidate", serving)
        assertEquals(BigDecimal("6.7"), serving!!.carbsPerServing.stripTrailingZeros())
        assertEquals(PortionUnitKind.PIECE, serving.descriptor?.kind)
        assertEquals(BigDecimal.ONE, serving.descriptor?.count?.stripTrailingZeros())
    }

    @Test
    fun `kinder associates the printed piece weight with the serving column`() {
        val report = NutritionTableParser.parseWithDiagnostics(RealLabelFixtures.kinder(slopePercent = 5.0))
        val weight = report.servingCandidate?.descriptor?.weightOrVolume
        assertNotNull("piece weight 12.5 g was not associated", weight)
        assertEquals(BigDecimal("12.5"), weight!!.amount.stripTrailingZeros())
        assertEquals(NutritionBasis.PER_100_G, weight.basis)
    }

    @Test
    fun `kinder never takes the sugars row's per-piece figure as the serving candidate source`() {
        // 6,7 appears on both rows. The value is the same, so only its provenance distinguishes a
        // correct read from a lucky one: the row it came from must be the total, never the child.
        val report = NutritionTableParser.parseWithDiagnostics(RealLabelFixtures.kinder(slopePercent = 5.0))
        val rowKinds = LogicalRowBuilder.build(RealLabelFixtures.kinder(slopePercent = 5.0))
            .map { RowClassifier.classify(it) }
        assertEquals(
            "the sugars row must survive as its own CARBOHYDRATE_CHILD row",
            1,
            rowKinds.count { it == NutritionRowKind.CARBOHYDRATE_CHILD },
        )
        assertEquals(
            "exactly one total-carbohydrate row",
            1,
            rowKinds.count { it == NutritionRowKind.TOTAL_CARBOHYDRATE },
        )
        assertNotNull(report.servingCandidate)
    }

    // ---- Row reconstruction, stated directly -------------------------------------------------

    @Test
    fun `a tilted table does not chain its carbohydrate and sugars rows together`() {
        val rows = LogicalRowBuilder.build(RealLabelFixtures.kinder(slopePercent = 5.0))
        val carbRow = rows.single { it.text.contains("Koolhydraten") }
        assertTrue(
            "the sugars row was absorbed into the carbohydrate row: '${carbRow.text}'",
            !carbRow.text.contains("suikers"),
        )
        assertTrue(carbRow.text.contains("53,5"))
        assertTrue("the per-piece cell was lost from the total row", carbRow.text.contains("6,7"))
    }

    @Test
    fun `the sugars row keeps its own identity at every tested tilt`() {
        listOf(0.0, 2.0, 5.0, 8.0).forEach { slope ->
            val rows = LogicalRowBuilder.build(RealLabelFixtures.kinder(slope))
            val sugarRows = rows.filter { it.text.contains("suikers") }
            assertEquals("at $slope% slope", 1, sugarRows.size)
            assertTrue(
                "at $slope% slope the sugars row swallowed the total: '${sugarRows.single().text}'",
                !sugarRows.single().text.contains("Koolhydraten"),
            )
        }
    }

    // ---- Columns -----------------------------------------------------------------------------

    @Test
    fun `kinder resolves all three of its columns`() {
        val rows = LogicalRowBuilder.build(RealLabelFixtures.kinder(slopePercent = 5.0))
        val kinds = ColumnClassifier.classify(rows, documentWidth = 800).map { it.kind }
        assertTrue("per-100 g column missing: $kinds", NutritionColumnKind.PER_100_G in kinds)
        assertTrue("per-serving column missing: $kinds", NutritionColumnKind.PER_SERVING in kinds)
        assertTrue("percent column missing: $kinds", NutritionColumnKind.REFERENCE_PERCENT in kinds)
    }

    @Test
    fun `a split percent token still forms a reference-percent column`() {
        // ML Kit emits "3%" as one element on some frames and "3" + "%" on others. The header-derived
        // column must not depend on which one this photograph produced.
        val rows = LogicalRowBuilder.build(RealLabelFixtures.kinder(slopePercent = 5.0))
        val percent = ColumnClassifier.classify(rows, documentWidth = 800)
            .filter { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
        assertEquals(1, percent.size)
        // Its centre must sit on the percentage cells, not be pulled left into the serving column.
        assertTrue("percent column centre at ${percent.single().centerX}", percent.single().centerX > 700)
    }

    @Test
    fun `the serving column's centre is not dragged into the percent column`() {
        val rows = LogicalRowBuilder.build(RealLabelFixtures.kinder(slopePercent = 5.0))
        val serving = ColumnClassifier.classify(rows, documentWidth = 800)
            .single { it.kind == NutritionColumnKind.PER_SERVING }
        assertTrue("serving column centre at ${serving.centerX}", serving.centerX < 700)
    }

    // ---- Decimal comma -----------------------------------------------------------------------

    @Test
    fun `a decimal comma is one value, never an integer or two numbers`() {
        val (value, _) = confidentValue(RealLabelFixtures.sondey(slopePercent = 3.0))
        assertEquals(BigDecimal("61.9"), value)
        assertTrue("61,9 was read as 619", value.compareTo(BigDecimal("619")) != 0)
        assertNull(
            "61,9 was split into separate 61 and 9 candidates",
            (readingOf(RealLabelFixtures.sondey(3.0)) as? LabelReading.Ambiguous)?.candidates,
        )
    }
}
