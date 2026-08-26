package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locating the nutrition table inside a recognized full-package capture.
 *
 * The negatives matter more than the positives here. This class decides what a second recognition
 * pass reads, so a wrong region is not a missed opportunity — it is a crop that may contain a number
 * belonging to something else, which is the mechanism a confident-wrong answer comes from.
 */
class NutritionTableLocatorTest {

    /** One element, sized so text height and row pitch come out realistic. */
    private fun element(text: String, left: Int, top: Int, width: Int = 90, height: Int = 20) =
        OcrElement(text, OcrBox(left, top, left + width, top + height), blockId = 0, lineId = 0)

    /** A row of elements laid out left to right at a given y. */
    private fun row(y: Int, vararg cells: Pair<String, Int>): List<OcrElement> =
        cells.map { (text, x) -> element(text, x, y) }

    /** A realistic table: header, several nutrient rows, values in a column. */
    private fun tableElements(originX: Int = 100, originY: Int = 400): List<OcrElement> {
        val pitch = 40
        return buildList {
            addAll(row(originY, "Voedingswaarde" to originX, "per 100 g" to originX + 300))
            addAll(row(originY + pitch, "Energie" to originX, "1500 kJ" to originX + 300))
            addAll(row(originY + pitch * 2, "Vetten" to originX, "12,0 g" to originX + 300))
            addAll(row(originY + pitch * 3, "Koolhydraten" to originX, "53,5 g" to originX + 300))
            addAll(row(originY + pitch * 4, "waarvan suikers" to originX, "21,0 g" to originX + 300))
            addAll(row(originY + pitch * 5, "Eiwitten" to originX, "6,0 g" to originX + 300))
            addAll(row(originY + pitch * 6, "Zout" to originX, "0,5 g" to originX + 300))
        }
    }

    /** Ingredient prose far above the table, the interference this class exists to exclude. */
    private fun ingredientProse(): List<OcrElement> = buildList {
        addAll(row(40, "INGREDIENTEN" to 100, "tarwebloem" to 400, "suiker" to 700))
        addAll(row(80, "plantaardige" to 100, "olie" to 400, "cacaopoeder" to 700))
        addAll(row(120, "magere" to 100, "melkpoeder" to 400, "glucosestroop" to 700))
    }

    private fun document(elements: List<OcrElement>, width: Int = 1200, height: Int = 1600) =
        OcrDocument(width, height, elements)

    @Test
    fun `locates a table inside a full package capture`() {
        val located = NutritionTableLocator.locate(document(ingredientProse() + tableElements()))

        assertNotNull("a table with a carbohydrate row should be located", located)
        located!!
        assertTrue("the carbohydrate anchor is the required signal", located.hasCarbohydrateAnchor)
    }

    @Test
    fun `the located region excludes ingredient prose printed above the table`() {
        val located = NutritionTableLocator.locate(document(ingredientProse() + tableElements()))!!

        // The prose sits at y=40..140; the table starts at y=400. The region must not reach it.
        assertTrue(
            "region top ${located.region.top} should sit below the ingredient prose at y<=140",
            located.region.top > 200,
        )
    }

    @Test
    fun `the located region includes the basis header above the nutrient rows`() {
        val located = NutritionTableLocator.locate(document(ingredientProse() + tableElements()))!!

        // The header row sits at y=400; the carbohydrate row at y=520. Cutting the header is the
        // exact defect the pre-Pass-A crop caused, so it is asserted directly rather than implied.
        assertTrue(
            "region top ${located.region.top} must cover the header row at y=400",
            located.region.top <= 400,
        )
        assertTrue("a header was found and included", located.hasHeader)
    }

    @Test
    fun `the located region covers the value column to the right of the nutrient names`() {
        val located = NutritionTableLocator.locate(document(ingredientProse() + tableElements()))!!

        // Values are at x=400..490. Clipping the value column makes the table unreadable, which would
        // be a worse outcome than not isolating at all.
        assertTrue(
            "region right ${located.region.right} must cover the value column ending at x=490",
            located.region.right >= 490,
        )
    }

    @Test
    fun `refuses when no carbohydrate row is present`() {
        // Prose only. Nothing here is a nutrition table, and isolating a region of it would hand a
        // second recognition pass a crop of an ingredient list.
        assertNull(NutritionTableLocator.locate(document(ingredientProse())))
    }

    @Test
    fun `refuses an empty document`() {
        assertNull(NutritionTableLocator.locate(document(emptyList())))
    }

    @Test
    fun `refuses when the table already fills the frame`() {
        // A pre-cropped panel — every committed fixture. Re-recognising it would cost a full pass to
        // obtain exactly the interference Pass A already has, i.e. none.
        val tight = tableElements(originX = 20, originY = 20)
        val located = NutritionTableLocator.locate(document(tight, width = 520, height = 320))

        assertNull("a table filling the frame should not be isolated", located)
    }

    @Test
    fun `does not merge a second printed block separated by a large gap`() {
        // A reference-intake summary far below the table. Its rows are aligned and numeric, so a
        // clustering rule would absorb them; reachability by row pitch must not.
        val second = buildList {
            addAll(row(1300, "Referentie-inname" to 100, "8400 kJ" to 400))
            addAll(row(1340, "van een gemiddelde" to 100, "2000 kcal" to 400))
        }
        val located = NutritionTableLocator.locate(document(tableElements() + second))!!

        assertTrue(
            "region bottom ${located.region.bottom} should not reach the separate block at y=1300",
            located.region.bottom < 1300,
        )
    }

    @Test
    fun `the region is expressed in the source image coordinate space`() {
        val located = NutritionTableLocator.locate(document(ingredientProse() + tableElements()))!!

        assertTrue("left within image", located.region.left >= 0)
        assertTrue("top within image", located.region.top >= 0)
        assertTrue("right within image", located.region.right <= 1200)
        assertTrue("bottom within image", located.region.bottom <= 1600)
    }

    @Test
    fun `scales with text size rather than assuming a resolution`() {
        // The same layout at 3x. Expansion is expressed in text heights, so the located region should
        // scale with it rather than being eaten or dwarfed by a fixed pixel margin.
        val scaled = (ingredientProse() + tableElements()).map { element ->
            element.copy(
                box = OcrBox(
                    element.box.left * 3,
                    element.box.top * 3,
                    element.box.right * 3,
                    element.box.bottom * 3,
                ),
            )
        }
        val located = NutritionTableLocator.locate(document(scaled, width = 3600, height = 4800))

        assertNotNull("the same layout at 3x should still be located", located)
        assertTrue("the header at y=1200 is still covered", located!!.region.top <= 1200)
    }

    @Test
    fun `a table with no header is still located but reports the header missing`() {
        val headerless = tableElements().filterNot { it.text == "Voedingswaarde" || it.text == "per 100 g" }
        val located = NutritionTableLocator.locate(document(ingredientProse() + headerless))

        // Locating without a header is allowed — the parse that follows will refuse an unplaceable
        // value on its own terms. What must not happen is claiming a header was included.
        assertNotNull(located)
        assertEquals(false, located!!.hasHeader)
    }
}
