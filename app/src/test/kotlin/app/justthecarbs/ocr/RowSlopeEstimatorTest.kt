package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: global skew estimation
// Invariant: ML Kit's line grouping may contribute a single ANGLE and nothing else. It must never
// decide which cells share a row — the architecture's core claim — so every test here checks a
// scalar, and the refusals are what keep a bad line from moving it.
class RowSlopeEstimatorTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    @Test
    fun `an axis-aligned document has no slope`() {
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("Carbohydrate", 40, 200, 240, 220, line = 0),
                e("of", 250, 200, 300, 220, line = 0),
                e("which", 310, 200, 400, 220, line = 0),
            ),
        )
        assertEquals(0.0, slope, 0.001)
    }

    @Test
    fun `a tilted line reports its angle`() {
        // Drops 30 px across 300 px of centre-to-centre travel: 10%.
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("Koolhydraten", 40, 200, 240, 220, line = 0),
                e("Glucides", 340, 230, 540, 250, line = 0),
            ),
        )
        assertEquals(0.1, slope, 0.01)
    }

    @Test
    fun `a line covering two printed rows is refused rather than averaged in`() {
        // The documented ML Kit failure: a total and its child merged onto one recognized line. Its
        // apparent angle is enormous and entirely fictional, so it must not contribute at all.
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("Carbohydrate", 40, 200, 240, 220, line = 0),
                e("8", 500, 300, 540, 320, line = 0),
            ),
        )
        assertEquals(0.0, slope, 0.001)
    }

    @Test
    fun `two adjacent words are too short to measure an angle from`() {
        // 50 px of centre-to-centre travel with 20 px glyphs. The 6 px of box jitter between these
        // two boxes would read as a 12% slope — pure noise, and exactly what the width floor is for.
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("of", 40, 200, 80, 220, line = 0),
                e("which", 88, 206, 128, 226, line = 0),
            ),
        )
        assertEquals(0.0, slope, 0.001)
    }

    @Test
    fun `the median ignores a single wild line`() {
        val elements = buildList {
            // Three honest lines at 5%.
            repeat(3) { index ->
                val top = 200 + index * 40
                add(e("left", 40, top, 140, top + 20, line = index))
                add(e("right", 440, top + 20, 540, top + 40, line = index))
            }
            // One line whose right-hand element was recognized from a different row entirely, but
            // which still fits inside the spread guard.
            add(e("left", 40, 400, 140, 420, line = 9))
            add(e("right", 440, 428, 540, 448, line = 9))
        }
        val slope = RowSlopeEstimator.estimate(elements)
        assertTrue("slope $slope should stay near the honest 5%", slope in 0.04..0.08)
    }

    @Test
    fun `an implausible angle is refused outright`() {
        // 60% is 31 degrees. Text that steep is not a mildly tilted table, and de-skewing by it
        // would be this stage inventing a layout rather than measuring one.
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("left", 40, 200, 140, 220, line = 0),
                e("right", 440, 440, 540, 460, line = 0),
            ),
        )
        assertEquals(0.0, slope, 0.001)
    }

    @Test
    fun `a document with nothing to measure yields no slope`() {
        assertEquals(0.0, RowSlopeEstimator.estimate(emptyList()), 0.001)
        assertEquals(
            0.0,
            RowSlopeEstimator.estimate(listOf(e("Carbohydrate", 40, 200, 240, 220, line = 0))),
            0.001,
        )
    }

    @Test
    fun `single-element lines contribute nothing and leave the document un-skewed`() {
        // A table whose every cell became its own recognized line — common, and the case where this
        // stage must fall back to 0.0 rather than guess.
        val slope = RowSlopeEstimator.estimate(
            listOf(
                e("Carbohydrate", 40, 200, 240, 220, line = 0, block = 0),
                e("45", 500, 210, 540, 230, line = 0, block = 1),
                e("of which sugars", 40, 240, 240, 260, line = 0, block = 2),
            ),
        )
        assertEquals(0.0, slope, 0.001)
    }
}
