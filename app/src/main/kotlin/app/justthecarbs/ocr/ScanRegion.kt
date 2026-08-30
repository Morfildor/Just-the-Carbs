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

/**
 * The scan guide as a rectangle later stages can use, grown by a forgiving margin.
 *
 * **This no longer maps the region to pixels, and cropping before recognition must not return.** An
 * earlier version turned the guide into a `PixelRegion` that `StillImageLoader` cut the capture down
 * to *before* OCR ran, on the argument that surrounding package text — ingredients, marketing copy, a
 * barcode, a best-before date — adds rows the table reconstruction has to survive.
 *
 * That argument is real and the remedy was wrong, which the 2026-08-17 capture-first pass measured:
 * the overlay's own height varies against the label's, so on a tall package the crop removed the
 * basis header band. `ColumnClassifier` then reclassified the per-100 column as `REFERENCE_PERCENT`
 * and the interpreter correctly refused an unplaceable value — sondey and kinder both went
 * `Confident` → `NotFound`, on a header the app had itself cropped off. No parser rule was at fault.
 * A wider margin was rejected for the same reason: the header's offset varies per package, so any
 * fixed margin is a guess that is wrong on some label with nothing on screen to show it. Its position
 * is *observable after recognition* and only guessable before it.
 *
 * So the rectangle is now **relevance, not a boundary**: recognition sees the whole frame, and the
 * region narrows the result afterwards (`ScanRegionRelevance`, which may only ever narrow an existing
 * reading) or is confirmed by the user against a frozen photograph (`SelectedTableReader`). The
 * `toPixels`/`PixelRegion` pair went unused at that point and was deleted in 1.0.3.
 *
 * [expand] survives and is load-bearing: it is the rectangle the automatic fast path reads and the
 * one the crop screen opens on.
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

}
