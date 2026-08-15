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
    /** Fraction of vertical overlap against the running row box required to join it. */
    const val MIN_ROW_OVERLAP = 0.5

    /** Narrow tiebreaker when overlap is present but inconclusive, in median element heights. */
    const val MAX_CENTER_DISTANCE_IN_HEIGHT = 0.6
}

object LogicalRowBuilder {

    fun build(document: OcrDocument): List<LogicalRow> {
        if (document.elements.isEmpty()) return emptyList()

        val medianHeight = medianHeight(document.elements)
        val ordered = document.elements.sortedWith(compareBy({ it.box.centerY }, { it.box.left }))

        val rows = mutableListOf<MutableList<OcrElement>>()
        var current = mutableListOf(ordered.first())
        var currentBox = ordered.first().box

        ordered.drop(1).forEach { element ->
            if (belongsToRow(element, currentBox, medianHeight)) {
                current += element
                currentBox = currentBox.union(element.box)
            } else {
                rows += current
                current = mutableListOf(element)
                currentBox = element.box
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

    /**
     * Overlap first, centre distance only as a narrow tiebreaker.
     *
     * Overlap is measured against the row's *running* union box rather than against the previous
     * element, so a tall value beside short label text does not start a spurious row.
     */
    private fun belongsToRow(element: OcrElement, rowBox: OcrBox, medianHeight: Int): Boolean {
        val overlap = rowBox.verticalOverlapRatio(element.box)
        if (overlap >= LogicalRowThresholds.MIN_ROW_OVERLAP) return true
        if (overlap <= 0.0) return false
        val allowed = medianHeight.coerceAtLeast(1) * LogicalRowThresholds.MAX_CENTER_DISTANCE_IN_HEIGHT
        return abs(rowBox.centerY - element.box.centerY) <= allowed
    }

    private fun medianHeight(elements: List<OcrElement>): Int {
        val heights = elements.map { it.box.height }.sorted()
        return heights[heights.size / 2]
    }
}
