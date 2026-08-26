package app.justthecarbs.ocr

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

/**
 * One ML Kit word-like element after crossing into the pure Kotlin parser boundary.
 *
 * ## Recognition metadata, and the line it must not cross
 *
 * [confidence] and [recognizedLanguage] are what the recognizer reported about *these characters*.
 * They were previously discarded at the mapper. They are retained because recognition-stage failures
 * are now known to be a dominant cause of real-device failure, and because they are measurably
 * diagnostic: on the Kinder fixture the mangled nutrient token `Uokohidiat/0gjikovi` carries 0.452
 * while clean numeric tokens carry 0.88+.
 *
 * **Confidence answers "how sure was OCR about these characters". It must never answer "what does
 * this number mean".** Which nutrient a value belongs to is decided by geometry and reading order —
 * [CarbohydrateTermAnchor], the child-nutrient exclusion, column association — and a high-confidence
 * sugars figure is still a sugars figure. Nothing in the parser may promote a candidate because it
 * was confidently recognised.
 *
 * Both default to "unknown" so the several hundred synthetic parser fixtures, which are about
 * geometry, need no arbitrary confidence values.
 */
data class OcrElement(
    val text: String,
    val box: OcrBox,
    val blockId: Int,
    val lineId: Int,
    /**
     * Recognizer confidence in `0.0..1.0`, or null when unknown (synthetic fixtures, or an engine
     * that does not report it). Measured as populated with zero NaN on all nine real fixtures.
     */
    val confidence: Float? = null,
    /** BCP-47-ish tag the recognizer attributed, or null. Diagnostic only. */
    val recognizedLanguage: String? = null,
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
