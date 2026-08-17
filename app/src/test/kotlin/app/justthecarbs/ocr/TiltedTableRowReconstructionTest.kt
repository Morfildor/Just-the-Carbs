package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: row reconstruction across the camera tilt and table density a hand-held photo actually
// produces.
//
// Invariant: reading a nutrition table must not depend on holding the phone square to the package.
//
// This matrix is the regression for the defect that made the nutrition scanner fail on real
// packaging while 459 tests stayed green. The old row builder measured an element's overlap against
// the row's *running union box*. That box grows vertically as members are added, so on a tilted
// photograph it inflated past the text height, and any element of the next row falling inside it
// scored a full overlap and joined — single-linkage chaining. The carbohydrate and sugars rows
// merged, the merged row was typed CARBOHYDRATE_CHILD by the (correct, unconditional) exclusion
// rule, and the whole reading was lost.
//
// Measured on this matrix before the fix:
//
//     pitch=30  slope=2%  ->  Confident
//     pitch=30  slope=4%  ->  NotFound   (rows merged)
//     pitch=40  slope=5%  ->  NotFound   (rows merged)
//     pitch=50  slope=8%  ->  NotFound   (row fragmented, all values on the child row)
//
// 4% slope is about 2.3 degrees of tilt. Every pre-existing fixture in this suite places a printed
// row's elements at identical y — slope 0 — which is why none of them could see it.
class TiltedTableRowReconstructionTest {

    /** Slopes in percent of horizontal distance. 10% is ~5.7 degrees: a careless but legible photo. */
    private val slopes = listOf(0.0, 2.0, 4.0, 5.0, 6.0, 8.0, 10.0)

    /** Row pitch in pixels against a 20 px glyph: 1.5x to 2.5x, spanning dense to airy labels. */
    private val pitches = listOf(30, 34, 40, 50)

    private fun kinderAt(slopePercent: Double, pitch: Int): OcrDocument =
        SlopedLabel(slopePercent, glyphHeight = 20).apply {
            var y = 200
            row(y, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(y, "per" to 560..605, "stuk" to 613..680, block = 1, line = 0)
            row(y, "%" to 730..755, block = 2, line = 0)
            y += pitch
            row(y, "(12,5" to 560..640, "g)" to 648..690, block = 1, line = 1)
            y += pitch * 2
            row(y, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 5, line = 0)
            row(y, "53,5" to 405..470, "g" to 478..495, block = 6, line = 0)
            row(y, "6,7" to 580..635, "g" to 643..660, block = 7, line = 0)
            row(y, "3" to 725..740, "%" to 748..765, block = 8, line = 0)
            y += pitch
            row(y, "waarvan" to 80..185, "suikers" to 193..290, "/" to 298..308, "sucres" to 316..400, block = 9, line = 0)
            row(y, "53,3" to 405..470, "g" to 478..495, block = 10, line = 0)
            row(y, "6,7" to 580..635, "g" to 643..660, block = 11, line = 0)
            row(y, "7" to 725..740, "%" to 748..765, block = 12, line = 0)
        }.document()

    @Test
    fun `the total and its sugars child stay separate rows at every tilt and density`() {
        pitches.forEach { pitch ->
            slopes.forEach { slope ->
                val rows = LogicalRowBuilder.build(kinderAt(slope, pitch))
                val merged = rows.filter { it.text.contains("Koolhydraten") && it.text.contains("suikers") }
                assertTrue(
                    "pitch=$pitch slope=$slope% merged the total into the child row: " +
                        merged.joinToString { it.text },
                    merged.isEmpty(),
                )
            }
        }
    }

    @Test
    fun `the total carbohydrate reads confidently at every tilt and density`() {
        pitches.forEach { pitch ->
            slopes.forEach { slope ->
                val reading = NutritionTableParser.parse(kinderAt(slope, pitch))
                assertTrue(
                    "pitch=$pitch slope=$slope% gave $reading",
                    reading is LabelReading.Confident,
                )
                assertEquals(
                    "pitch=$pitch slope=$slope%",
                    java.math.BigDecimal("53.5"),
                    (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
                )
            }
        }
    }

    @Test
    fun `no tilt or density ever substitutes sugars or a percentage for the total`() {
        pitches.forEach { pitch ->
            slopes.forEach { slope ->
                val reading = NutritionTableParser.parse(kinderAt(slope, pitch))
                val values = when (reading) {
                    is LabelReading.Confident -> listOf(reading.candidate.value)
                    is LabelReading.Ambiguous -> reading.candidates.map { it.value }
                    LabelReading.NotFound -> emptyList()
                }
                listOf("53.3", "3", "7", "6.7").forEach { forbidden ->
                    assertTrue(
                        "pitch=$pitch slope=$slope% offered $forbidden as the total",
                        values.none { it.compareTo(java.math.BigDecimal(forbidden)) == 0 },
                    )
                }
            }
        }
    }
}
