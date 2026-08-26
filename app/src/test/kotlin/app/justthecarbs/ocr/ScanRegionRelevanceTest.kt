package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The relevance filter's SAFETY CONTRACT, not merely its happy path.
 *
 * This class can only narrow a reading the parser already produced. The negative cases below are the
 * load-bearing ones: they pin that framing can never conjure a value out of a refusal, never edit a
 * confident answer, and never invent a basis. Without them the filter would be a second, weaker
 * interpretation path — exactly what the geometry-first rewrite removed from this codebase.
 */
class ScanRegionRelevanceTest {

    private val image = 1000 to 2000

    /** Frames the middle band of the image, leaving generous room top and bottom. */
    private val centreRegion = NormalizedRegion(left = 0.1, top = 0.4, right = 0.9, bottom = 0.6)

    private fun candidate(value: String, box: OcrBox, basis: NutritionBasis? = NutritionBasis.PER_100_G) =
        CarbCandidate(
            sourceLine = "row $value",
            label = "Koolhydraten",
            value = BigDecimal(value),
            basis = basis,
            score = 100,
            geometry = box,
            evidence = emptyList(),
        )

    private fun report(reading: LabelReading) = NutritionParseReport(reading, emptyList())

    private fun apply(reading: LabelReading, region: NormalizedRegion? = centreRegion) =
        ScanRegionRelevance.apply(report(reading), region, image.first, image.second)

    // ---- The safety contract --------------------------------------------------------------------

    @Test
    fun `a refusal is never turned into a reading`() {
        val result = apply(LabelReading.NotFound)
        assertEquals(LabelReading.NotFound, result.reading)
    }

    @Test
    fun `a confident reading is never altered even when it lies outside the frame`() {
        // Far below the framed band: if framing could veto a confident answer, this is where it would.
        val far = candidate("61.9", OcrBox(0, 1900, 500, 1950))
        val original = LabelReading.Confident(far)
        val result = apply(original)
        assertEquals(original, result.reading)
    }

    @Test
    fun `no candidate is ever introduced that the parser did not produce`() {
        val inside = candidate("5.0", OcrBox(100, 850, 900, 950))
        val outside = candidate("7.5", OcrBox(100, 100, 900, 200))
        val result = apply(LabelReading.Ambiguous(listOf(inside, outside)))
        val produced = when (val r = result.reading) {
            is LabelReading.Confident -> listOf(r.candidate)
            is LabelReading.Ambiguous -> r.candidates
            LabelReading.NotFound -> emptyList()
        }
        assertTrue("every surviving candidate must be one of the originals", produced.all { it === inside || it === outside })
    }

    @Test
    fun `a basis is never supplied or changed`() {
        val noBasis = candidate("5.0", OcrBox(100, 850, 900, 950), basis = null)
        val other = candidate("7.5", OcrBox(100, 100, 900, 200), basis = null)
        val result = apply(LabelReading.Ambiguous(listOf(noBasis, other)))
        // A single survivor with a null basis must NOT be promoted to Confident, whose invariant
        // requires a basis — the filter must leave such a reading ambiguous rather than invent one.
        val produced = (result.reading as? LabelReading.Ambiguous)?.candidates
            ?: listOf((result.reading as LabelReading.Confident).candidate)
        assertTrue("no basis may be invented", produced.all { it.basis == null })
    }

    // ---- Doing its actual job -------------------------------------------------------------------

    @Test
    fun `an ambiguity is resolved when exactly one candidate is where the user pointed`() {
        val inside = candidate("5.0", OcrBox(100, 850, 900, 950))
        val outside = candidate("7.5", OcrBox(100, 60, 900, 160))
        val result = apply(LabelReading.Ambiguous(listOf(inside, outside)))

        val confident = result.reading as? LabelReading.Confident
        assertEquals("the framed candidate should win", BigDecimal("5.0"), confident?.candidate?.value)
    }

    @Test
    fun `an ambiguity where every candidate is framed is left untouched`() {
        val a = candidate("5.0", OcrBox(100, 850, 900, 950))
        val b = candidate("7.5", OcrBox(100, 1000, 900, 1100))
        val original = LabelReading.Ambiguous(listOf(a, b))
        val result = apply(original)
        assertEquals("no discrimination achieved; leave the parser's answer alone", original, result.reading)
    }

    @Test
    fun `an ambiguity where no candidate is framed is left untouched rather than refused`() {
        // The overlay is a soft guide. If the user's aim and the parser's evidence disagree entirely,
        // the evidence is the harder signal — a mis-drawn frame must not suppress a real reading.
        val a = candidate("5.0", OcrBox(100, 60, 900, 160))
        val b = candidate("7.5", OcrBox(100, 1900, 900, 1990))
        val original = LabelReading.Ambiguous(listOf(a, b))
        val result = apply(original)
        assertEquals(original, result.reading)
    }

    @Test
    fun `a null region leaves the report exactly as it was`() {
        val original = report(LabelReading.Ambiguous(listOf(candidate("5.0", OcrBox(0, 0, 10, 10)))))
        val result = ScanRegionRelevance.apply(original, null, image.first, image.second)
        assertSame("a pre-layout capture must be a no-op", original, result)
    }

    @Test
    fun `a degenerate image size leaves the report exactly as it was`() {
        val original = report(LabelReading.Ambiguous(listOf(candidate("5.0", OcrBox(0, 0, 10, 10)))))
        assertSame(original, ScanRegionRelevance.apply(original, centreRegion, 0, 100))
        assertSame(original, ScanRegionRelevance.apply(original, centreRegion, 100, 0))
    }

    @Test
    fun `diagnostics and serving candidate survive the filter`() {
        val inside = candidate("5.0", OcrBox(100, 850, 900, 950))
        val outside = candidate("7.5", OcrBox(100, 60, 900, 160))
        val original = NutritionParseReport(
            reading = LabelReading.Ambiguous(listOf(inside, outside)),
            diagnostics = listOf(OcrDiagnostic("stage", "message")),
        )
        val result = ScanRegionRelevance.apply(original, centreRegion, image.first, image.second)
        assertEquals(original.diagnostics, result.diagnostics)
        assertEquals(original.servingCandidate, result.servingCandidate)
    }
}
