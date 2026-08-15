package app.justthecarbs.ocr

import kotlin.math.abs

/**
 * Decides which numeric elements on a row are percentage-valued (correction pass §5).
 *
 * ML Kit tokenizes "17%" either as one element or as "17" followed by a separate "%", and which one
 * it picks varies with font, spacing and image quality on the same package. Testing only the
 * element's own text therefore catches one shape and misses the other, leaving a %RI figure eligible
 * as a carbohydrate quantity — which is how a reference-intake percentage is offered to the user as
 * grams, and, under a serving column, saved as a countable portion.
 *
 * The rule is deliberately narrow so it cannot swallow a legitimate grams cell: the "%" must be on
 * the same logical row, immediately to the right of the number with no other element between them,
 * within a small horizontal gap, and vertically aligned. A "%" belonging to a distant column does
 * not disqualify anything.
 */
object PercentAssociation {

    /**
     * Horizontal gap allowed between a number and its "%", as a fraction of the document width.
     *
     * Sized for "17 %" printed with a thin space, not for two different columns. At 800 px this is
     * 24 px — around one character — so a percent sign in the next column over is never associated.
     */
    private const val MAX_GAP_FRACTION = 0.03

    /** Minimum vertical overlap between the number's box and the "%" box. */
    private const val MIN_VERTICAL_OVERLAP = 0.5

    /** An element that is nothing but a percent sign, which is the split-token case. */
    private val BARE_PERCENT = Regex("^\\s*%\\s*$")

    /** An element carrying a digit and a percent sign together, which is the fused case. */
    private val FUSED_PERCENT = Regex("\\d\\s*%")

    /**
     * The indices of [row]'s elements that carry a percentage value, in either tokenization.
     *
     * Bare "%" elements are included too: they are not numbers, but marking them keeps callers from
     * having to re-derive which elements were consumed by an association.
     */
    fun percentElementIndices(row: LogicalRow, documentWidth: Int): Set<Int> {
        val indices = mutableSetOf<Int>()
        val maxGap = documentWidth * MAX_GAP_FRACTION

        row.elements.forEachIndexed { index, element ->
            if (FUSED_PERCENT.containsMatchIn(element.text)) {
                indices += index
                return@forEachIndexed
            }
            if (!BARE_PERCENT.matches(element.text)) return@forEachIndexed

            indices += index
            // Only the immediately preceding element is considered. Reaching further back would
            // let "30 g 17 %" mark the 30, which is the grams figure the user actually wants.
            val previous = row.elements.getOrNull(index - 1) ?: return@forEachIndexed
            if (!previous.text.any { it.isDigit() }) return@forEachIndexed
            val gap = element.box.left - previous.box.right
            if (gap > maxGap || gap < -previous.box.width) return@forEachIndexed
            if (previous.box.verticalOverlapRatio(element.box) < MIN_VERTICAL_OVERLAP) return@forEachIndexed
            indices += index - 1
        }

        return indices
    }

    /** True when a "%" sits close enough to the right of [element] on [row] to belong to it. */
    fun hasAssociatedPercent(row: LogicalRow, element: OcrElement, documentWidth: Int): Boolean {
        val index = row.elements.indexOfFirst { it === element }
        if (index < 0) return false
        return index in percentElementIndices(row, documentWidth)
    }

    /** Distance helper kept beside the rule it serves, so the two are reviewed together. */
    internal fun horizontalGap(left: OcrBox, right: OcrBox): Int = abs(right.left - left.right)
}
