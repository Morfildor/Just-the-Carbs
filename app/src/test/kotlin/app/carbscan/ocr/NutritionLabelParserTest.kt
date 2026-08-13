package app.carbscan.ocr

import app.carbscan.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Brief §59: Dutch, English, 100 g, 100 ml, ambiguity, and parsing failure. */
class NutritionLabelParserTest {

    private fun single(text: String): CarbCandidate {
        val reading = NutritionLabelParser.parse(text)
        assertTrue("expected Single but was $reading", reading is LabelReading.Single)
        return (reading as LabelReading.Single).candidate
    }

    @Test
    fun `reads a Dutch label`() {
        val candidate = single(
            """
            Voedingswaarde per 100 g
            Energie 2100 kJ
            Vetten 31 g
            Koolhydraten 47,3 g
            Eiwitten 7,5 g
            """.trimIndent(),
        )

        assertEquals(0, BigDecimal("47.3").compareTo(candidate.value))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    @Test
    fun `reads an English label`() {
        val candidate = single(
            """
            Nutrition per 100 g
            Fat 31 g
            Carbohydrate 47.3 g
            Protein 7.5 g
            """.trimIndent(),
        )

        assertEquals(0, BigDecimal("47.3").compareTo(candidate.value))
    }

    @Test
    fun `reads a per-100-ml label`() {
        val candidate = single(
            """
            Voedingswaarde per 100 ml
            Koolhydraten 9,4 g
            """.trimIndent(),
        )

        assertEquals(NutritionBasis.PER_100_ML, candidate.basis)
        assertEquals(0, BigDecimal("9.4").compareTo(candidate.value))
    }

    /**
     * The single most dangerous misread available to this parser. "waarvan suikers" is a sub-line
     * of the total, and reporting 22,1 instead of 47,3 would understate the dose by half.
     */
    @Test
    fun `never mistakes the sugars sub-line for the total`() {
        val candidate = single(
            """
            per 100 g
            Koolhydraten 47,3 g
            waarvan suikers 22,1 g
            """.trimIndent(),
        )

        assertEquals(0, BigDecimal("47.3").compareTo(candidate.value))
    }

    @Test
    fun `never mistakes of-which-sugars for the total`() {
        val candidate = single(
            """
            per 100 g
            Carbohydrate 47.3 g
            of which sugars 22.1 g
            """.trimIndent(),
        )

        assertEquals(0, BigDecimal("47.3").compareTo(candidate.value))
    }

    @Test
    fun `ignores fibre and starch sub-lines`() {
        val candidate = single(
            """
            per 100 g
            Koolhydraten 47,3 g
            waarvan zetmeel 25,2 g
            Vezels 4,1 g
            """.trimIndent(),
        )

        assertEquals(0, BigDecimal("47.3").compareTo(candidate.value))
    }

    /** A per-100-g and a per-serving column: the app must ask, not choose (§29). */
    @Test
    fun `reports ambiguity when two different carbohydrate values appear`() {
        val reading = NutritionLabelParser.parse(
            """
            per 100 g
            Koolhydraten 47,3 g
            Koolhydraten 15,6 g
            """.trimIndent(),
        )

        assertTrue("was $reading", reading is LabelReading.Ambiguous)
        assertEquals(2, (reading as LabelReading.Ambiguous).candidates.size)
    }

    /** The same value twice is one finding, not a question. */
    @Test
    fun `does not treat a repeated identical value as ambiguous`() {
        val reading = NutritionLabelParser.parse(
            """
            per 100 g
            Koolhydraten 47,3 g
            Koolhydraten 47,3 g
            """.trimIndent(),
        )

        assertTrue("was $reading", reading is LabelReading.Single)
    }

    @Test
    fun `finds nothing in text with no carbohydrate row`() {
        val reading = NutritionLabelParser.parse(
            """
            Ingredienten: tarwebloem, suiker, zout
            Ten minste houdbaar tot 2027
            """.trimIndent(),
        )

        assertEquals(LabelReading.NotFound, reading)
    }

    @Test
    fun `finds nothing when the row has no readable number`() {
        assertEquals(
            LabelReading.NotFound,
            NutritionLabelParser.parse("Koolhydraten ...... g"),
        )
    }

    @Test
    fun `finds nothing in empty text`() {
        assertEquals(LabelReading.NotFound, NutritionLabelParser.parse(""))
    }

    /** An OCR misread of 47,3 as 473 is impossible per 100 g and must be dropped, not offered. */
    @Test
    fun `discards an impossible value rather than offering it for confirmation`() {
        assertEquals(
            LabelReading.NotFound,
            NutritionLabelParser.parse("per 100 g\nKoolhydraten 473 g"),
        )
    }

    @Test
    fun `defaults to grams when the label does not state a basis`() {
        assertEquals(NutritionBasis.PER_100_G, single("Koolhydraten 47,3 g").basis)
    }

    @Test
    fun `keeps the source line so the user can check the reading`() {
        val candidate = single("per 100 g\nKoolhydraten 47,3 g")

        assertTrue(candidate.sourceLine.contains("Koolhydraten"))
    }
}
