package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProseNutritionReaderTest {

    /** The real AH stokbrood relationship: total, then sugars, then fibre, all on one line. */
    @Test
    fun bindsTheTotalAndLetsChildTermsClaimTheirOwnValues() {
        val result = ProseNutritionReader.read(rowsOf(STOKBROOD))

        val reading = result.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("46"), candidate.value.stripTrailingZeros())
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)

        val provenance = result.provenance
        assertEquals("koolhydraten", provenance?.nutrientTerm)
    }

    /** 1,0 is sugars and 4,7 is fibre. Neither may ever surface as the total. */
    @Test
    fun neverOffersAChildNutrientsValue() {
        val reading = ProseNutritionReader.read(rowsOf(STOKBROOD)).reading
        val offered = when (reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        listOf("1.0", "4.7", "12").forEach { forbidden ->
            assertTrue(
                "$forbidden is a child nutrient or unrelated value, never the total",
                offered.none { it.compareTo(BigDecimal(forbidden)) == 0 },
            )
        }
    }

    /** No basis printed anywhere: the value is readable but unplaceable. Refuse. */
    @Test
    fun refusesWhenNoBasisGovernsTheDeclaration() {
        val reading = ProseNutritionReader.read(rowsOf("koolhydraten 46 g, waarvan suikers 1,0 g")).reading
        assertEquals(LabelReading.NotFound, reading)
    }

    /**
     * Two bases in immediate succession. The second opens a new declaration, so the carbohydrate span
     * is governed by `per 100 ml` alone — but nothing is governed by `per 100 g`, and no reading may
     * be attributed to it. The point of the assertion is that the reader does not silently attach the
     * value to the FIRST basis it saw.
     */
    @Test
    fun aSecondBasisPhraseOpensANewDeclaration() {
        val text = "per 100 g per 100 ml koolhydraten 9,0 g, waarvan suikers 9,0 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
    }

    /** A value with no gram unit is not a nutrient quantity. */
    @Test
    fun requiresAGramUnitOnTheValue() {
        val text = "Voedingswaarde per 100 g: koolhydraten 46, waarvan suikers 1,0 g"
        assertEquals(LabelReading.NotFound, ProseNutritionReader.read(rowsOf(text)).reading)
    }

    /** Per 100 ml prose, so the basis is carried through rather than assumed to be grams. */
    @Test
    fun carriesAMillilitreBasisThrough() {
        val text = "gemiddeld per 100 ml: koolhydraten 9,0 g, waarvan suikers 9,0 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue(reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
        assertEquals(BigDecimal("9"), reading.candidate.value.stripTrailingZeros())
    }

    /**
     * Two independent declarations disagreeing is ambiguity, not a pick — and specifically not the
     * first one. This is the test that fails if `read` ever regresses to returning on first success.
     */
    @Test
    fun conflictingTotalsAcrossDeclarationsAreAmbiguous() {
        val text = "per 100 g: koolhydraten 46 g. per 100 g: koolhydraten 12 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val values = (reading as LabelReading.Ambiguous).candidates.map { it.value.stripTrailingZeros() }
        assertTrue("both readings must be offered, got $values", values.size == 2)
    }

    /**
     * THE CASE THE 0-OF-3 BASELINE MEASUREMENT DEMANDS. The basis phrase is on its own reconstructed
     * row, three rows above the carbohydrate value — the shape of every real prose fixture in the
     * corpus. A row-scoped reader returns NotFound here and would never fire on a real label.
     */
    @Test
    fun aDeclarationGovernsNutrientSpansOnLaterRows() {
        val rows = multiRow(
            "Pour Per Pro 100g:",
            "energie 1312 kJ",
            "vetten 19 g waarvan verzadigde 13 g",
            "koolhydraten 3 g waarvan suikers 2,5 g",
        )

        val reading = ProseNutritionReader.read(rows).reading
        assertTrue("expected Confident across rows, got $reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("3"), candidate.value.stripTrailingZeros())
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    /**
     * A multilingual label printing the same figure twice is ONE interpretation, not an ambiguity.
     * Distinctness is on (value, basis) exactly as the tabular path deduplicates.
     */
    @Test
    fun theSameFigureRepeatedInTwoDeclarationsIsOneReading() {
        val text = "per 100 g: koolhydraten 46 g. per 100 g: carbohydrate 46 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(BigDecimal("46"), (reading as LabelReading.Confident).candidate.value.stripTrailingZeros())
    }

    private companion object {
        const val STOKBROOD =
            "Voedingswaarde per 100 g: energie 1312 kJ, vetten 7,8 g, " +
                "koolhydraten 46 g, waarvan suikers 1,0 g, vezels 4,7 g, eiwitten 12 g"
    }

    /** One row, tokens laid out left to right at a uniform pitch. */
    private fun rowsOf(text: String): List<LogicalRow> = multiRow(text)

    /**
     * One reconstructed row per line, at a 100 px pitch that is comfortably clear of the 40 px text
     * height so no two lines can chain. This is the layout every real prose fixture actually has.
     */
    private fun multiRow(vararg lines: String): List<LogicalRow> {
        var maxX = 0
        val elements = lines.flatMapIndexed { line, text ->
            val top = 40 + line * 100
            var x = 20
            text.split(' ').filter { it.isNotBlank() }.map { word ->
                val width = word.length * 18
                val box = OcrBox(x, top, x + width, top + 40)
                x += width + 12
                maxX = maxOf(maxX, x)
                OcrElement(word, box, blockId = line, lineId = line)
            }
        }
        return LogicalRowBuilder.build(OcrDocument(maxX + 40, 40 + lines.size * 100 + 60, elements))
    }
}
