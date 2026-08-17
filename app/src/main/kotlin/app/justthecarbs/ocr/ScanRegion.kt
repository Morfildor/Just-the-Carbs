package app.justthecarbs.ocr

/**
 * The part of the frame the user framed the nutrition table in, as fractions of the visible preview.
 *
 * Fractions rather than pixels because the three things that have to agree — the overlay drawn in
 * Compose, the preview surface, and the captured JPEG — are all different sizes on every device and
 * in every capture mode. A fraction is the only expression of "this rectangle" that survives all
 * three (§10, §2).
 */
data class NormalizedRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(right > left) { "right must exceed left" }
        require(bottom > top) { "bottom must exceed top" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/** A crop rectangle in a bitmap's own pixels. */
data class PixelRegion(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Turns the on-screen scan region into a crop rectangle on the captured image.
 *
 * **Why crop at all.** The label scanner previously ran OCR on the whole frame while drawing a
 * region overlay that did nothing — the overlay's own KDoc said cropping "has no demonstrated
 * recognition benefit". On a real package the frame also contains the ingredient list, marketing
 * copy, a barcode, a best-before date and recycling marks. Every one of those adds rows the table
 * reconstruction has to survive, and words like "suikers" or a stray "100 g" appear in prose as
 * readily as in a table. The user already told the app which part of the package to read by putting
 * it inside the frame; ignoring that discards the single most reliable signal available.
 *
 * The mapping is trivial *because* the camera is bound through a `ViewPort` matched to the preview:
 * that makes the captured image cover exactly the field of view the preview showed, so a fraction of
 * the preview is the same fraction of the capture. Without the viewport this would need the
 * preview's crop, the two streams' aspect ratios and the rotation, and would be wrong on some device
 * in a way nobody could see. Correctness lives in the binding, not in arithmetic here.
 */
object ScanRegionMapper {

    /**
     * How far the crop is grown beyond the drawn frame, per side, as a fraction of the frame.
     *
     * The overlay is a guide, not a boundary the user is expected to hit exactly, and a table whose
     * last row sits a few millimetres outside it must still be read. Generous enough to forgive
     * ordinary aim; far short of "the whole frame", which is what it replaces.
     */
    const val SAFETY_MARGIN = 0.12

    fun expand(region: NormalizedRegion, marginFraction: Double = SAFETY_MARGIN): NormalizedRegion {
        val dx = region.width * marginFraction
        val dy = region.height * marginFraction
        return NormalizedRegion(
            left = (region.left - dx).coerceIn(0.0, 1.0),
            top = (region.top - dy).coerceIn(0.0, 1.0),
            right = (region.right + dx).coerceIn(0.0, 1.0),
            bottom = (region.bottom + dy).coerceIn(0.0, 1.0),
        )
    }

    /**
     * [region] as pixels of an [imageWidth] x [imageHeight] bitmap, or null when the result would not
     * be a usable crop.
     *
     * Null rather than a clamped best effort: the caller's fallback is to OCR the whole image, which
     * is the old behaviour and always safe. A degenerate or near-total crop is not worth the risk of
     * cutting the table in half.
     */
    fun toPixels(region: NormalizedRegion, imageWidth: Int, imageHeight: Int): PixelRegion? {
        if (imageWidth <= 0 || imageHeight <= 0) return null

        val left = (region.left * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val top = (region.top * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val right = (region.right * imageWidth).toInt().coerceIn(left + 1, imageWidth)
        val bottom = (region.bottom * imageHeight).toInt().coerceIn(top + 1, imageHeight)

        val width = right - left
        val height = bottom - top
        if (width < MIN_CROP_PIXELS || height < MIN_CROP_PIXELS) return null
        // Cropping away less than a tenth of the image is not worth a second full-size bitmap.
        if (width >= imageWidth * NO_OP_THRESHOLD && height >= imageHeight * NO_OP_THRESHOLD) return null

        return PixelRegion(left = left, top = top, width = width, height = height)
    }

    /** Below this there is not enough text left for recognition to mean anything. */
    private const val MIN_CROP_PIXELS = 64

    /** A crop this close to the full image saves nothing and costs an allocation. */
    private const val NO_OP_THRESHOLD = 0.98
}
