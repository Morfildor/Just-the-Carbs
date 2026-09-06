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
     * Translates every element of a crop-local [document] into source-image coordinates, and resizes
     * the document to the source bitmap's own dimensions.
     *
     * ## Why this exists
     *
     * A screen that shows the **full source photograph** — [app.justthecarbs.ui.scan
     * .AssistedReadingScreen] and [app.justthecarbs.ui.scan.VerificationScreen] both always do —
     * needs the document it hit-tests and draws highlights against to share that same coordinate
     * space. Handing it a crop-local document (Strategy B's or a targeted reread's own recognition)
     * unchanged would make every tap and every highlight wrong by exactly the crop's own offset: a
     * tap the user placed on the printed carbohydrate row, translated to *crop-local* pixel
     * coordinates by the screen's own full-bitmap-relative geometry math, would compare against
     * *source-space* element boxes and miss.
     *
     * [toSourceSpace] already solves this for one candidate's box, at the one place a proposal is
     * drawn. This is the same operation applied to every element of a whole document, for the case
     * where the user is handed the document itself to tap and search within — recovery and focused
     * entry, neither of which has a single candidate box to translate because the whole point of
     * those screens is that no single candidate was safely produced yet.
     *
     * [sourceWidth]/[sourceHeight] are the full source bitmap's own pixel dimensions — the resulting
     * document describes that whole bitmap, not merely the translated crop, because a screen showing
     * the full photograph needs `document.width`/`document.height` to agree with the bitmap it is
     * drawing (the same contract [OcrDocument]'s own KDoc states: "coordinates use the source image's
     * pixel space").
     *
     * Returns [document] unchanged when [crop] is null, so a Pass A document — never cropped — is
     * untouched.
     */
    fun documentToSourceSpace(
        document: OcrDocument,
        crop: PixelRect?,
        sourceWidth: Int,
        sourceHeight: Int,
    ): OcrDocument {
        if (crop == null) return document
        return OcrDocument(
            width = sourceWidth,
            height = sourceHeight,
            elements = document.elements.map { it.copy(box = toSourceSpace(it.box, crop)) },
        )
    }

    /**
     * Composes a [NormalizedRegion] expressed as fractions of [crop] (a document already cropped from
     * the source bitmap) into a [NormalizedRegion] expressed as fractions of the full source bitmap —
     * the inverse of [toPixels] followed by normalizing against the crop's own dimensions.
     *
     * ## Why this exists
     *
     * [app.justthecarbs.ocr.TargetedRereadRegion.of] computes a rereading region from *whichever*
     * document produced the evidence being retargeted — see
     * [app.justthecarbs.ocr.TargetedRereadTrigger]. When that document is [EvidenceSource
     * .SELECTED_REGION_OCR]'s own crop-local recognition, the resulting region is normalized against
     * the **crop's** width and height, not the source bitmap's. Passing that region straight to
     * [SelectedRegionRecognizer.recognise] — which crops from the **full source bitmap** — would crop
     * the wrong rectangle: a region meant to span the crop's own top 20%, say, would instead span the
     * source bitmap's top 20%, landing on whatever happens to be there rather than on the row the
     * trigger actually located.
     *
     * [sourceWidth]/[sourceHeight] are the full bitmap's own dimensions, needed because [crop] is
     * expressed in pixels of that same bitmap and a [NormalizedRegion] is always relative to *some*
     * frame — composing the two requires knowing what the outer frame is.
     */
    fun composeWithSourceCrop(
        cropLocalRegion: NormalizedRegion,
        crop: PixelRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): NormalizedRegion? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val left = (crop.left + cropLocalRegion.left * crop.width) / sourceWidth
        val top = (crop.top + cropLocalRegion.top * crop.height) / sourceHeight
        val right = (crop.left + cropLocalRegion.right * crop.width) / sourceWidth
        val bottom = (crop.top + cropLocalRegion.bottom * crop.height) / sourceHeight
        if (right <= left || bottom <= top) return null
        return NormalizedRegion(
            left = left.coerceIn(0.0, 1.0),
            top = top.coerceIn(0.0, 1.0),
            right = right.coerceIn(0.0, 1.0),
            bottom = bottom.coerceIn(0.0, 1.0),
        )
    }

    /**
     * A selection at or above this fraction of both dimensions is treated as the whole frame.
     *
     * Not the same constant as [ElementRegionFilter]'s no-op threshold, though they are numerically
     * close: that one asks "would filtering remove anything", this one asks "would recognising this
     * differ from Pass A". They answer different questions and must be free to move independently.
     */
    private const val WHOLE_FRAME_THRESHOLD = 0.97
}
