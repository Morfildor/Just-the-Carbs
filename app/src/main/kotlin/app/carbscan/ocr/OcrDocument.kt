package app.carbscan.ocr

/** Android-free OCR geometry. Coordinates use the source image's pixel space. */
data class OcrBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be greater than or equal to left" }
        require(bottom >= top) { "bottom must be greater than or equal to top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    fun union(other: OcrBox): OcrBox = OcrBox(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    fun verticalOverlapRatio(other: OcrBox): Double {
        val overlap = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0)
        val reference = minOf(height, other.height).coerceAtLeast(1)
        return overlap.toDouble() / reference
    }
}

/** One ML Kit word-like element after crossing into the pure Kotlin parser boundary. */
data class OcrElement(
    val text: String,
    val box: OcrBox,
    val blockId: Int,
    val lineId: Int,
)

/** The complete input required by the spatial parser. */
data class OcrDocument(
    val width: Int,
    val height: Int,
    val elements: List<OcrElement>,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}
