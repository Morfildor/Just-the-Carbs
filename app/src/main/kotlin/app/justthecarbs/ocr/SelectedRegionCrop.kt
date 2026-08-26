package app.justthecarbs.ocr

/**
 * Maps a normalized user selection onto source-bitmap pixels (spec §3), and nothing else.
 *
 * Pure arithmetic, deliberately separated from the Android bitmap work in [SelectedRegionRecognizer]
 * so the coordinate mapping — the part that can be wrong invisibly — is JVM-testable. A visually
 * correct rectangle that maps to the wrong pixels would crop the wrong part of the label and be
 * indistinguishable from a recognition failure, which is precisely the bug class
 * [CropSelectionGeometry] already exists to prevent on the display side.
 */
object SelectedRegionCrop {

    /**
     * The smallest crop worth recognising, per side, in pixels.
     *
     * Below this there is not enough of a nutrition table to read, and ML Kit on a sliver of an image
     * is a good way to manufacture tokens from partial glyphs — the `(g)` -> `(9)` hazard's raw
     * material. Refusing is safe because the caller keeps the whole-frame reading.
     */
    const val MIN_SIDE_PX = 64

    /** A rectangle in source-bitmap pixel space, guaranteed to lie inside the bitmap. */
    data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /**
     * [region] as pixels of a [sourceWidth] x [sourceHeight] bitmap, or null when it is not worth
     * cropping.
     *
     * Returns null — rather than clamping to something arbitrary — when the selection resolves to
     * fewer than [MIN_SIDE_PX] on a side, or covers essentially the whole frame. Null is a distinct,
     * honest outcome meaning "do not run a second recognition", which the caller handles by keeping
     * Pass A's answer.
     *
     * Inverted and degenerate rectangles need no handling here: [NormalizedRegion] refuses to
     * construct them, so they cannot reach this function. The ordering check below is retained only
     * as a cheap guard against a future change to that invariant.
     *
     * A selection covering essentially the whole frame also returns null: re-recognising the whole
     * image reproduces Pass A exactly, so it would spend seconds and memory to produce a duplicate
     * opinion that cannot corroborate anything (two identical passes are not independent evidence).
     */
    fun toPixels(
        region: NormalizedRegion?,
        sourceWidth: Int,
        sourceHeight: Int,
    ): PixelRect? {
        if (region == null) return null
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        if (region.right <= region.left || region.bottom <= region.top) return null

        // Whole-frame selections produce no new information; see KDoc above.
        if (region.width >= WHOLE_FRAME_THRESHOLD && region.height >= WHOLE_FRAME_THRESHOLD) return null

        val left = (region.left * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val top = (region.top * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val right = (region.right * sourceWidth).toInt().coerceIn(left + 1, sourceWidth)
        val bottom = (region.bottom * sourceHeight).toInt().coerceIn(top + 1, sourceHeight)

        val width = right - left
        val height = bottom - top
        if (width < MIN_SIDE_PX || height < MIN_SIDE_PX) return null

        return PixelRect(left = left, top = top, width = width, height = height)
    }

    /**
     * Translates a box recognised in crop space back into source-image coordinates.
     *
     * Without this the second pass's geometry would be expressed relative to the crop's own origin,
     * so any comparison against Pass A's boxes — and the evidence bundle's overlays — would be offset
     * by the crop position, silently. Note the parser itself does not need this (it re-derives rows
     * and columns within whatever space it is given), but evidence and assisted-mode tapping do.
     */
    fun toSourceSpace(box: OcrBox, crop: PixelRect): OcrBox = OcrBox(
        left = box.left + crop.left,
        top = box.top + crop.top,
        right = box.right + crop.left,
        bottom = box.bottom + crop.top,
    )

    /**
     * A selection at or above this fraction of both dimensions is treated as the whole frame.
     *
     * Not the same constant as [ElementRegionFilter]'s no-op threshold, though they are numerically
     * close: that one asks "would filtering remove anything", this one asks "would recognising this
     * differ from Pass A". They answer different questions and must be free to move independently.
     */
    private const val WHOLE_FRAME_THRESHOLD = 0.97
}
