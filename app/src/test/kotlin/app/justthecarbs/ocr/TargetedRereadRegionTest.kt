package app.justthecarbs.ocr

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetedRereadRegionTest {

    private fun row(box: OcrBox) = LogicalRow(
        elements = listOf(OcrElement("x", box, 0, 0)),
        box = box,
        sourceLines = emptySet(),
    )

    /**
     * The sondey/kinder lesson, pinned directly: the region must always reach back to the header
     * band, never start at the value row's own top. A region that started at the row would exclude
     * exactly the header [ColumnClassifier] needs to place the value.
     */
    @Test
    fun `the region always includes the header band, never starting at the row alone`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val headerBox = OcrBox(left = 100, top = 200, right = 900, bottom = 260)
        val targetRow = row(OcrBox(left = 100, top = 1400, right = 900, bottom = 1460))

        val region = TargetedRereadRegion.of(document, targetRow, headerBox, panelTop = 150)
        assertNotNullOrFail(region)

        val topPixels = region!!.top * document.height
        assertTrue(
            "region top ($topPixels) must be at or above the header's top (${headerBox.top}), " +
                "not the row's own top (${targetRow.box.top})",
            topPixels <= headerBox.top,
        )
    }

    @Test
    fun `with no header, the region falls back to the panel's own top edge, not the row's`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val targetRow = row(OcrBox(left = 100, top = 1400, right = 900, bottom = 1460))

        val region = TargetedRereadRegion.of(document, targetRow, headerBox = null, panelTop = 300)
        assertNotNullOrFail(region)

        val topPixels = region!!.top * document.height
        assertTrue(
            "with no header, the region must still reach back to the panel top (300), was $topPixels",
            topPixels <= 300,
        )
    }

    @Test
    fun `the region includes the target row's full vertical span plus a margin`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val targetRow = row(OcrBox(left = 100, top = 1400, right = 900, bottom = 1460))

        val region = TargetedRereadRegion.of(document, targetRow, headerBox = null, panelTop = 1350)
        assertNotNullOrFail(region)

        val bottomPixels = region!!.bottom * document.height
        assertTrue(
            "region bottom ($bottomPixels) must be at or below the row's own bottom (${targetRow.box.bottom})",
            bottomPixels >= targetRow.box.bottom,
        )
    }

    @Test
    fun `a region covering essentially the whole document is refused as redundant`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val targetRow = row(OcrBox(left = 10, top = 1900, right = 990, bottom = 1990))

        // Header spans almost the whole document height, so the computed region would too.
        val headerBox = OcrBox(left = 10, top = 20, right = 990, bottom = 60)
        val region = TargetedRereadRegion.of(document, targetRow, headerBox, panelTop = 20)

        assertNull(
            "a region spanning nearly the whole document adds nothing over the wider pass",
            region,
        )
    }

    /**
     * A degenerate row (zero height, at the very top edge with no room for a margin above it) must
     * not crash. [OcrDocument] itself forbids width/height <= 0, so that guard is defence in depth
     * rather than reachable from a real document; this exercises the actual edge case that can occur
     * in practice.
     */
    @Test
    fun `a degenerate zero-height row at the document edge does not crash`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val targetRow = row(OcrBox(left = 0, top = 0, right = 10, bottom = 0))

        // Must not throw. Either a valid (possibly minimal) region or null is acceptable.
        TargetedRereadRegion.of(document, targetRow, headerBox = null, panelTop = 0)
    }

    @Test
    fun `the region includes horizontal margin around the widest of header and row`() {
        val document = OcrDocument(width = 1000, height = 2000, elements = emptyList())
        val headerBox = OcrBox(left = 50, top = 200, right = 950, bottom = 260)
        val targetRow = row(OcrBox(left = 200, top = 1400, right = 800, bottom = 1460))

        val region = TargetedRereadRegion.of(document, targetRow, headerBox, panelTop = 150)
        assertNotNullOrFail(region)

        val leftPixels = region!!.left * document.width
        val rightPixels = region.right * document.width
        assertTrue("left must not exceed the header's own left (50)", leftPixels <= 50)
        assertTrue("right must not be less than the header's own right (950)", rightPixels >= 950)
    }

    private fun assertNotNullOrFail(region: NormalizedRegion?) {
        org.junit.Assert.assertNotNull("expected a computed region, got null", region)
    }
}
