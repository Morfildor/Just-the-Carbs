package app.justthecarbs.ocr

/**
 * Estimates the printed text's global skew — one scalar, dy/dx — so row reconstruction can compare
 * elements along the baseline the package was actually photographed at.
 *
 * **Why this exists.** [LogicalRowBuilder] previously compared an element's vertical extent against
 * the running row's union box. On an axis-aligned image that is exact. On a photograph it is a
 * runaway: the same printed row drifts down across the table's width, so the union box grows far
 * taller than the text, and any element of the *next* row that falls inside that inflated box scores
 * a full overlap ratio and joins. Measured on a reconstruction of the Kinder table at an ordinary
 * row pitch, the carbohydrate and sugars rows merged from 4% slope — about 2.3 degrees of hand tilt —
 * and the merged row was then typed `CARBOHYDRATE_CHILD`, losing the reading entirely.
 *
 * **Why ML Kit's line grouping is used here and nowhere else.** Row *membership* stays geometry-only,
 * for the reasons [LogicalRow] documents: ML Kit both splits one printed row across lines and merges
 * two printed rows into one, so it cannot be trusted to say which cells belong together. But a
 * recognized line's own elements are contiguous printed text, which makes them a sound sample of the
 * baseline's *angle* — a single number, not a grouping decision. Nothing downstream can tell which
 * lines contributed it.
 *
 * Robustness comes from three deliberate refusals rather than from tuning:
 *
 * - a line whose elements span more than [MAX_LINE_SPREAD_IN_HEIGHTS] text heights is discarded, so
 *   the documented "two printed rows merged into one line" case cannot contribute a bogus angle;
 * - a line too short horizontally is discarded, because two adjacent words measure jitter, not slope;
 * - the **median** is taken, never the mean, so a surviving outlier cannot move the result.
 *
 * With no usable evidence the answer is 0.0 — the un-skewed reading — never a guess.
 */
internal object RowSlopeEstimator {

    /** Beyond this the image is not mildly tilted, and de-skewing it would be its own guess. */
    private const val MAX_SLOPE = 0.25

    /**
     * A line must span at least this many text heights before its angle means anything.
     *
     * At 20 px text this is 80 px — several words. Two adjacent short words span barely 50 px, where
     * the few pixels of box jitter every OCR engine produces read as a 12% slope. The multilingual
     * nutrient names these tables are full of ("Koolhydraten / Glucides") clear it comfortably.
     */
    private const val MIN_LINE_WIDTH_IN_HEIGHTS = 4.0

    /** A "line" taller than this covers more than one printed row and is not a baseline sample. */
    private const val MAX_LINE_SPREAD_IN_HEIGHTS = 2.5

    fun estimate(elements: List<OcrElement>): Double {
        if (elements.size < 2) return 0.0
        val medianHeight = medianOf(elements.map { it.box.height.toDouble() }).coerceAtLeast(1.0)

        val slopes = elements
            .groupBy { LineKey(it.blockId, it.lineId) }
            .values
            .mapNotNull { line -> slopeOf(line, medianHeight) }

        if (slopes.isEmpty()) return 0.0
        return medianOf(slopes)
    }

    private fun slopeOf(line: List<OcrElement>, medianHeight: Double): Double? {
        if (line.size < 2) return null

        val spread = line.maxOf { it.box.bottom } - line.minOf { it.box.top }
        if (spread > medianHeight * MAX_LINE_SPREAD_IN_HEIGHTS) return null

        val left = line.minBy { it.box.centerX }
        val right = line.maxBy { it.box.centerX }
        val dx = right.box.centerX - left.box.centerX
        if (dx < medianHeight * MIN_LINE_WIDTH_IN_HEIGHTS) return null

        val slope = (right.box.centerY - left.box.centerY) / dx
        return slope.takeIf { kotlin.math.abs(it) <= MAX_SLOPE }
    }

    /** Lower median, matching [LogicalRowBuilder]'s own convention for an even count. */
    private fun medianOf(values: List<Double>): Double = values.sorted()[values.size / 2]
}
