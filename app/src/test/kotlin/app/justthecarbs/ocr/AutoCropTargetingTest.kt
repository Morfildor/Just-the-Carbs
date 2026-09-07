package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCropTargetingTest {

    /**
     * An ordinary single-column table: a per-100 header well above the total-carbohydrate row, with
     * generous prose margin on every side -- the shape a real photograph has and a tight fixture
     * does not, so a real narrowing has real margin to prove itself against.
     */
    private fun ordinaryTableDocument() = OcrDocument(
        width = 2000,
        height = 3000,
        elements = listOf(
            OcrElement("Ingredients: wheat flour, sugar, cocoa", OcrBox(80, 80, 1600, 150), 0, 0),
            OcrElement("per 100 g", OcrBox(900, 800, 1180, 860), 0, 1),
            OcrElement("Koolhydraten", OcrBox(200, 1400, 700, 1460), 0, 2),
            OcrElement("53,5g", OcrBox(900, 1400, 1080, 1460), 0, 2),
            OcrElement("Best before 2027-01-01, lot A1234", OcrBox(80, 2800, 1200, 2860), 0, 3),
        ),
    )

    @Test
    fun `a declaration with a resolved per-100 header targets a region spanning header through value`() {
        val document = ordinaryTableDocument()
        val report = NutritionTableInterpreter.interpret(document)
        assertTrue(
            "precondition: this document must read confidently, was ${report.reading}",
            report.reading is LabelReading.Confident,
        )

        val region = AutoCropTargeting.regionFor(document)
        requireNotNull(region) { "a resolved declaration and header must produce a region" }

        // The header (top 800) through the value row (bottom 1460) must both be inside the region,
        // with the padding this file's own KDoc promises applied on top.
        val topPixels = region.top * document.height
        val bottomPixels = region.bottom * document.height
        assertTrue("top ($topPixels) must be at or above the header's own top (800)", topPixels <= 800.0)
        assertTrue("bottom ($bottomPixels) must be at or below the value row's bottom (1460)", bottomPixels >= 1460.0)

        // The whole point of this feature: materially narrower than the frame, and it must exclude
        // both the ingredients prose far above and the best-before line far below.
        assertTrue(
            "region height fraction (${region.height}) must be materially narrower than the full frame",
            region.height < 0.5,
        )
        assertTrue("the ingredients row (ends at y=150) must be excluded", topPixels > 150.0)
        assertTrue("the best-before row (starts at y=2800) must be excluded", bottomPixels < 2800.0)
    }

    @Test
    fun `the targeted region is horizontally narrower than the full frame width too`() {
        val document = ordinaryTableDocument()
        val region = AutoCropTargeting.regionFor(document)
        requireNotNull(region)
        assertTrue(
            "region width fraction (${region.width}) must be narrower than the full frame",
            region.width < 0.9,
        )
    }

    @Test
    fun `padding is applied beyond the declaration and header bounds, not equal to them`() {
        val document = ordinaryTableDocument()
        val region = AutoCropTargeting.regionFor(document)
        requireNotNull(region)

        val topPixels = region.top * document.height
        val bottomPixels = region.bottom * document.height

        // Strictly outside the raw header/value union (800..1460) on both edges -- proves the
        // padding fraction is doing work, not that clamping alone happened to match the bounds.
        assertTrue("top ($topPixels) must be strictly above the raw header top (800)", topPixels < 800.0)
        assertTrue("bottom ($bottomPixels) must be strictly below the raw value bottom (1460)", bottomPixels > 1460.0)
    }

    @Test
    fun `padding is clamped to the source image bounds and never produces a negative or out-of-range fraction`() {
        // The declaration sits hard against the top-left corner, so the padded box would overshoot
        // above and to the left of the image if it were not clamped.
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(10, 5, 260, 55), 0, 0),
                OcrElement("Koolhydraten", OcrBox(10, 90, 260, 140), 0, 1),
                OcrElement("41,5g", OcrBox(10, 90, 100, 140), 0, 1),
            ),
        )
        val report = NutritionTableInterpreter.interpret(document)
        assertTrue(
            "precondition: this document must read confidently, was ${report.reading}",
            report.reading is LabelReading.Confident,
        )

        val region = AutoCropTargeting.regionFor(document)
        requireNotNull(region)

        assertTrue("left must clamp to 0.0, was ${region.left}", region.left >= 0.0)
        assertTrue("top must clamp to 0.0, was ${region.top}", region.top >= 0.0)
        assertTrue("right must not exceed 1.0, was ${region.right}", region.right <= 1.0)
        assertTrue("bottom must not exceed 1.0, was ${region.bottom}", region.bottom <= 1.0)
    }

    @Test
    fun `two distinct valued total declarations in one panel fall back to the owning panel's own bounds`() {
        // A structurally-located panel (a total-carbohydrate anchor, a per-100 header, several
        // nutrient anchors and a numeric column -- NutritionPanelLocator2D's own bar) whose
        // reconstructed rows disagree about the carbohydrate figure: two distinct total-carbohydrate
        // rows, each carrying its OWN value. declarationRegion's own guard -- mirroring
        // FocusedAmountEntry/TargetedRereadTrigger -- refuses to pick between two VALUED
        // declarations, so this panel has no single declaration to target and must fall back to its
        // own bounds rather than the whole document.
        val document = OcrDocument(
            width = 2000,
            height = 3000,
            elements = listOf(
                OcrElement("Ingredients: wheat flour, sugar", OcrBox(80, 80, 1600, 150), 0, 0),
                OcrElement("per 100 g", OcrBox(900, 800, 1180, 860), 0, 1),
                OcrElement("Vetten", OcrBox(200, 1100, 700, 1160), 0, 2),
                OcrElement("3,4g", OcrBox(900, 1100, 1080, 1160), 0, 2),
                OcrElement("Koolhydraten", OcrBox(200, 1400, 700, 1460), 0, 3),
                OcrElement("53,5g", OcrBox(900, 1400, 1080, 1460), 0, 3),
                OcrElement("Glucides", OcrBox(200, 1600, 700, 1660), 0, 4),
                OcrElement("46g", OcrBox(900, 1600, 1080, 1660), 0, 4),
                OcrElement("Best before 2027-01-01", OcrBox(80, 2800, 1200, 2860), 0, 5),
            ),
        )
        val panels = NutritionDocumentModel.build(document).panels
        org.junit.Assume.assumeTrue(
            "precondition: this document must localize at least one panel",
            panels.isNotEmpty(),
        )
        val totals = panels.flatMap { it.declarations }
            .filter { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE && it.valueCells.isNotEmpty() }
        org.junit.Assume.assumeTrue(
            "precondition: this fixture must reconstruct at least two distinctly-valued total " +
                "declarations, found ${totals.size}: ${totals.map { it.text }}",
            totals.size >= 2,
        )

        val region = AutoCropTargeting.regionFor(document)
        requireNotNull(region) { "a located panel with no single declaration to target must still produce a region" }

        // The region must still be narrower than the full frame -- it is bounded by the panel, not
        // the whole document -- and must exclude the prose far above and below the panel.
        val topPixels = region.top * document.height
        val bottomPixels = region.bottom * document.height
        assertTrue("region height fraction (${region.height}) must be narrower than the full frame", region.height < 0.7)
        assertTrue("the ingredients row (ends at y=150) must be excluded", topPixels > 150.0)
        assertTrue("the best-before row (starts at y=2800) must be excluded", bottomPixels < 2800.0)
    }

    @Test
    fun `a document with no recognisable nutrition structure at all returns null so the caller keeps its existing default`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("unrelated marketing text", OcrBox(0, 0, 200, 40), 0, 0),
            ),
        )
        assertNull(
            "no declaration and no panel means the caller must keep its own fallback rectangle",
            AutoCropTargeting.regionFor(document),
        )
    }

    @Test
    fun `an empty document returns null`() {
        val document = OcrDocument(width = 1000, height = 1000, elements = emptyList())
        assertNull(AutoCropTargeting.regionFor(document))
    }

    @Test
    fun `a null document returns null`() {
        assertNull(AutoCropTargeting.regionFor(null))
    }

    /**
     * A negative control on the whole-frame guard: a declaration and header that together already
     * span nearly the entire image must not be reported as a narrowing.
     */
    @Test
    fun `a declaration spanning nearly the whole frame is not reported as a targeted region`() {
        // Same shape as ordinaryTableDocument, but the header sits near the very top of the frame
        // and the value row near the very bottom, so their union already covers almost all of it --
        // no prose margin left for a padded crop to be a genuine narrowing of.
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(450, 5, 730, 65), 0, 0),
                OcrElement("Koolhydraten", OcrBox(100, 940, 600, 995), 0, 1),
                OcrElement("53,5g", OcrBox(450, 940, 630, 995), 0, 1),
            ),
        )
        val report = NutritionTableInterpreter.interpret(document)
        assertTrue(
            "precondition: this document must read confidently, was ${report.reading}",
            report.reading is LabelReading.Confident,
        )
        assertNull(
            "a declaration already spanning nearly the whole frame is not a narrowing and must return null",
            AutoCropTargeting.regionFor(document),
        )
    }
}
