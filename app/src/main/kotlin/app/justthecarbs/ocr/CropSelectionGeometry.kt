package app.justthecarbs.ocr

/** A rectangle in view (screen) pixels. Pure Kotlin so the mapping stays JVM-testable. */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Maps between the crop rectangle the user drags on screen and fractions of the captured bitmap.
 *
 * ## Why this is the riskiest arithmetic in the crop feature
 *
 * The frozen capture is displayed scaled-to-fit, so the image almost never fills the view: there are
 * letterbox bars whose size depends on the capture's aspect ratio and the device's screen. A crop box
 * that looks perfectly placed on screen but maps a few percent off in the bitmap would slice the
 * basis header away — and that is not a hypothetical failure mode. It is exactly what happened when
 * the scan overlay was used as a pre-recognition crop: sondey reported
 * `rejected: 61.9: REFERENCE_PERCENT column`, the right value on the right row, made unplaceable
 * because its `per 100 g` header had been cropped off. The user could not see it, and neither could a
 * screenshot.
 *
 * So the mapping is expressed against the **displayed image rectangle** rather than the view size,
 * and it is pinned by tests that fix independently-checkable numbers instead of asserting
 * self-consistency.
 *
 * ## Rotation
 *
 * There is deliberately no rotation arithmetic here. The bitmap shown to the user is the one
 * [StillImageLoader] has already made upright, and the same upright bitmap is what recognition ran
 * on, so both sides of this mapping share one coordinate space. Callers must pass the **upright**
 * dimensions; passing the pre-EXIF ones would be wrong by a transpose, which is why there is a test
 * for it.
 */
object CropSelectionGeometry {

    /**
     * The smallest selection worth reading, as a fraction of each axis.
     *
     * A stray tap must not become a sliver crop that reads as `NotFound` and looks to the user like
     * the parser failing on a table they can plainly see.
     */
    private const val MIN_SIDE_FRACTION = 0.02

    /**
     * Where a scale-to-fit image actually lands inside a view.
     *
     * Returns a degenerate (zero-size) rectangle for a degenerate input rather than throwing: this is
     * called from layout, which legitimately runs before measurement with a zero-size view.
     */
    fun displayedImageBounds(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
    ): ViewRect {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return ViewRect(0f, 0f, 0f, 0f)
        }

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val left = (viewWidth - displayedWidth) / 2f
        val top = (viewHeight - displayedHeight) / 2f

        return ViewRect(left, top, left + displayedWidth, top + displayedHeight)
    }

    /**
     * [selection] in view pixels as fractions of the image, or null when it is not a usable crop.
     *
     * Clamps to the image before validating, so dragging a handle into a letterbox bar pins to the
     * edge instead of producing a negative fraction that [NormalizedRegion] would reject at
     * construction. A selection lying entirely in a bar encloses no image and is refused.
     */
    fun toNormalizedRegion(selection: ViewRect, displayed: ViewRect): NormalizedRegion? {
        if (displayed.width <= 0f || displayed.height <= 0f) return null
        if (selection.width <= 0f || selection.height <= 0f) return null

        val left = ((selection.left - displayed.left) / displayed.width).coerceIn(0f, 1f)
        val top = ((selection.top - displayed.top) / displayed.height).coerceIn(0f, 1f)
        val right = ((selection.right - displayed.left) / displayed.width).coerceIn(0f, 1f)
        val bottom = ((selection.bottom - displayed.top) / displayed.height).coerceIn(0f, 1f)

        if (right - left < MIN_SIDE_FRACTION || bottom - top < MIN_SIDE_FRACTION) return null

        return NormalizedRegion(
            left = left.toDouble(),
            top = top.toDouble(),
            right = right.toDouble(),
            bottom = bottom.toDouble(),
        )
    }

    /** The inverse, for drawing an initial or restored selection. */
    fun toViewRect(region: NormalizedRegion, displayed: ViewRect): ViewRect = ViewRect(
        left = displayed.left + (region.left * displayed.width).toFloat(),
        top = displayed.top + (region.top * displayed.height).toFloat(),
        right = displayed.left + (region.right * displayed.width).toFloat(),
        bottom = displayed.top + (region.bottom * displayed.height).toFloat(),
    )
}
