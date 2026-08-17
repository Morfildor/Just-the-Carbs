package app.justthecarbs.ocr

import kotlin.math.abs

/** An ML Kit block/line pair. Diagnostics only — never a row-membership signal. */
data class LineKey(val block: Int, val line: Int)

/**
 * One printed table row, reconstructed from geometry.
 *
 * ML Kit's own line grouping is not the printed layout: it both splits a single printed row across
 * several lines (a label and its value recognized as separate blocks) and merges two printed rows
 * into one line (a total and the "of which sugars" beneath it). The old parser trusted that grouping
 * and could therefore attribute a child nutrient's value to the total-carbohydrate label. Row
 * identity is now decided by box geometry alone; [sourceLines] is kept only so diagnostics can show
 * where the text came from.
 */
data class LogicalRow(
    /** Left-to-right. */
    val elements: List<OcrElement>,
    /** Union of every element box. */
    val box: OcrBox,
    val sourceLines: Set<LineKey>,
) {
    val text: String = elements.joinToString(" ") { it.text }
}

/**
 * Row-membership geometry.
 *
 * Deliberately stricter than [NutritionParserThresholds]'s row constants and deliberately separate
 * from them. Those were tuned as one signal among many inside a scoring model, where being slightly
 * too generous only nudged a score. Here a row boundary is a hard structural claim that later stages
 * treat as authoritative, so it is tuned tight and reviewed on its own terms.
 */
object LogicalRowThresholds {
    /**
     * How far a de-skewed element centre may sit from its row's centre, in text heights.
     *
     * Compared against the row's **median** de-skewed centre, not against a growing union box and not
     * against the previously added element. Both of those are single-linkage rules: each new member
     * moves the thing the next candidate is measured against, so on a photograph a row walks steadily
     * down the table and absorbs the row beneath it. A median over the members already accepted does
     * not move under one outlier, which is what makes the boundary hold.
     *
     * 0.7 leaves better than 2x margin at an ordinary row pitch of ~1.5 text heights while still
     * absorbing the few pixels of residual skew the slope estimate leaves behind.
     */
    const val MAX_CENTER_DISTANCE_IN_HEIGHT = 0.7
}

object LogicalRowBuilder {

    fun build(document: OcrDocument): List<LogicalRow> {
        if (document.elements.isEmpty()) return emptyList()

        val medianHeight = medianHeight(document.elements)

        // One global skew angle, measured from the image itself. Rows are then compared along the
        // baseline the package was actually photographed at rather than along the image's own
        // horizontal — which is the difference between reading a hand-held photo and only ever
        // reading a rendered mock. See RowSlopeEstimator for why this is a scalar and not a grouping.
        val slope = RowSlopeEstimator.estimate(document.elements)
        fun deskewedCenter(element: OcrElement): Double =
            element.box.centerY - slope * element.box.centerX

        val ordered = document.elements.sortedWith(compareBy({ deskewedCenter(it) }, { it.box.left }))

        val rows = mutableListOf<MutableList<OcrElement>>()
        var current = mutableListOf(ordered.first())
        // Kept sorted so the median is a lookup rather than a re-sort per element.
        var currentCenters = mutableListOf(deskewedCenter(ordered.first()))

        ordered.drop(1).forEach { element ->
            val center = deskewedCenter(element)
            val rowCenter = currentCenters[currentCenters.size / 2]
            val rowHeight = maxOf(medianHeight, element.box.height).coerceAtLeast(1)
            if (abs(center - rowCenter) <= rowHeight * LogicalRowThresholds.MAX_CENTER_DISTANCE_IN_HEIGHT) {
                current += element
                // The list is built in ascending centre order, so appending keeps it sorted.
                currentCenters += center
            } else {
                rows += current
                current = mutableListOf(element)
                currentCenters = mutableListOf(center)
            }
        }
        rows += current

        return rows.map { elements ->
            val sorted = elements.sortedBy { it.box.left }
            LogicalRow(
                elements = sorted,
                box = sorted.drop(1).fold(sorted.first().box) { box, element -> box.union(element.box) },
                sourceLines = sorted.map { LineKey(it.blockId, it.lineId) }.toSet(),
            )
        }
    }

    private fun medianHeight(elements: List<OcrElement>): Int {
        val heights = elements.map { it.box.height }.sorted()
        return heights[heights.size / 2]
    }
}
