package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: numeric parsing on European labels
// Invariant: "61,9" is sixty-one point nine. Not 619, and not a choice between 61 and 9.
//
// The failure this guards is specific: both halves of a split decimal clear the per-100 validator on
// their own, so a fragmented recognition does not fail loudly — it produces an ambiguity between the
// right answer and a meaningless one, and asks the user to pick.
class DecimalCommaTest {

    private fun label(vararg carbCells: Pair<String, IntRange>): OcrDocument =
        SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(
                300,
                "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350,
                block = 1, line = 0,
            )
            row(300, *carbCells, block = 2, line = 0)
        }.document()

    private fun confidentValue(document: OcrDocument): BigDecimal {
        val reading = NutritionTableParser.parse(document)
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate.value.stripTrailingZeros()
    }

    @Test
    fun `a decimal comma is one value`() {
        assertEquals(BigDecimal("61.9"), confidentValue(label("61,9" to 405..470, "g" to 478..495)))
    }

    @Test
    fun `a decimal point is one value`() {
        assertEquals(BigDecimal("53.5"), confidentValue(label("53.5" to 405..470, "g" to 478..495)))
    }

    @Test
    fun `a comma is never dropped to make an integer`() {
        val value = confidentValue(label("61,9" to 405..470, "g" to 478..495))
        assertTrue("61,9 was read as 619", value.compareTo(BigDecimal("619")) != 0)
        // 619 would also have been refused by the per-100 validator, so check the real risk too:
        // that the fractional digits were simply dropped.
        assertTrue("the fraction was discarded", value.compareTo(BigDecimal("61")) != 0)
    }

    @Test
    fun `a trailing zero after the comma is preserved as a value`() {
        assertEquals(BigDecimal("0.5"), confidentValue(label("0,50" to 405..470, "g" to 478..495)))
    }

    @Test
    fun `a number split across the separator is rejoined rather than offered as two answers`() {
        // "61," + "9" — the fragmentation a thin, low comma produces.
        val value = confidentValue(label("61," to 405..455, "9" to 458..472, "g" to 480..497))
        assertEquals(BigDecimal("61.9"), value)
    }

    @Test
    fun `two numbers in different columns are never joined into one`() {
        // A wide gap means these are separate cells, whatever their text looks like. Joining them
        // would invent a value that appears nowhere on the package.
        val document = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(200, "per" to 600..645, "stuk" to 653..720, block = 1, line = 0)
            row(300, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 2, line = 0)
            row(300, "61," to 405..455, block = 3, line = 0)
            row(300, "9" to 640..660, block = 4, line = 0)
        }.document()

        val reading = NutritionTableParser.parse(document)
        val values = when (reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        assertTrue(
            "cells a column apart were fused into 61.9: $values",
            values.none { it.compareTo(BigDecimal("61.9")) == 0 },
        )
    }

    @Test
    fun `a value fused with its unit still parses`() {
        assertEquals(BigDecimal("61.9"), confidentValue(label("61,9g" to 405..495)))
    }

    @Test
    fun `the basis is still resolved alongside a comma decimal`() {
        val reading = NutritionTableParser.parse(label("61,9" to 405..470, "g" to 478..495))
        assertEquals(
            NutritionBasis.PER_100_G,
            (reading as LabelReading.Confident).candidate.basis,
        )
    }
}
