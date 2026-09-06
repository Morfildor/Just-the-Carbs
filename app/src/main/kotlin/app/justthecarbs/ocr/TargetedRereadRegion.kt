package app.justthecarbs.ocr

/**
 * Computes a bounded, targeted crop for one extra native-resolution recognition, when the ordinary
 * evidence set could not settle a reading — a corrupted unit glyph, a scale-ambiguous pair, or a
 * digit fused with its unit — before the app gives up and asks the user to type the number (spec §2).
 *
 * ## Why this exists, and what it does NOT do
 *
 * `SelectedRegionRecognizer` (Strategy B) already re-reads the user's whole confirmed rectangle at
 * native resolution. This is a **second, narrower** re-read, targeted at the row(s) that actually
 * matter, tried only after Strategy B has already run and left the reading inconclusive. It exists
 * because a tighter, higher-effective-resolution crop of just the failing row can recover a glyph
 * that a wider crop's competing content (surrounding prose, other columns) drowns out — the same
 * mechanism [SelectedRegionRecognizer] was built for, applied one level narrower.
 *
 * It never repairs, guesses, or substitutes a digit. It produces a **new observation** — a different
 * crop, a fresh ML Kit recognition — and hands that back as ordinary [RecognitionEvidence] to
 * [EvidenceResolver], exactly like every other pass. If the reread repeats the same reading, nothing
 * changes; if it disagrees, the disagreement is visible exactly like any other conflict.
 *
 * ## Why the region must never be narrower than "header through value" (the sondey/kinder lesson)
 *
 * [ScanRegionMapper]'s own KDoc documents a measured regression: cropping *before* recognition, even
 * generously, can remove the basis-header band above a tall label's total-carbohydrate row, and
 * [ColumnClassifier] then reclassifies the per-100 column as [NutritionColumnKind.REFERENCE_PERCENT]
 * — turning a correct `Confident` reading into a `NotFound`. A crop tight around only the *value*
 * cell reproduces that exact failure at a smaller scale, because the header the interpreter needs to
 * place the value is no longer in the recognised image at all.
 *
 * So the region computed here always spans from the **top of the table's resolved header band**
 * (when one exists) through the bottom of the target row, never from the value row alone. When no
 * header row is resolved (a linear/serving-declared panel), the region spans from the top of the
 * document's already-recognised panel bounds, for the same reason.
 */
internal object TargetedRereadRegion {

    /**
     * The bounded native-resolution rereading region for [targetRow], within [document]'s panel
     * bounds — or null when a safe, header-preserving region cannot be computed.
     *
     * [headerBox] is the resolved header band's box, when [ColumnClassifier] found one; null for a
     * linear/serving-declared panel, in which case [panelTop] (the panel's own established top edge)
     * is used instead — never the target row's own top, which would risk exactly the sondey/kinder
     * failure.
     *
     * The result is expressed as fractions of the *document* (the space [document.width]/
     * [document.height] describe), which may itself already be a crop — the caller is responsible
     * for composing this with any existing crop origin before recognising against the full-resolution
     * source bitmap. See [SelectedRegionCrop.toSourceSpace] for the equivalent composition on the
     * read-back side.
     */
    fun of(
        document: OcrDocument,
        targetRow: LogicalRow,
        headerBox: OcrBox?,
        panelTop: Int,
    ): NormalizedRegion? {
        if (document.width <= 0 || document.height <= 0) return null

        val top = (headerBox?.top ?: panelTop).coerceAtMost(targetRow.box.top)
        val bottom = targetRow.box.bottom

        // A margin proportional to the row's own height, so short and tall labels both get a
        // comparable amount of surrounding context rather than a fixed pixel band that is generous
        // on one capture resolution and negligible on another.
        val rowHeight = (targetRow.box.bottom - targetRow.box.top).coerceAtLeast(1)
        val verticalMargin = (rowHeight * VERTICAL_MARGIN_FRACTION).toInt()
        val horizontalMargin = (document.width * HORIZONTAL_MARGIN_FRACTION).toInt()

        val left = (0).coerceAtLeast(minOf(headerBox?.left ?: targetRow.box.left, targetRow.box.left) - horizontalMargin)
        val right = document.width.coerceAtMost(maxOf(headerBox?.right ?: targetRow.box.right, targetRow.box.right) + horizontalMargin)
        val expandedTop = (top - verticalMargin).coerceAtLeast(0)
        val expandedBottom = (bottom + verticalMargin).coerceAtMost(document.height)

        if (right <= left || expandedBottom <= expandedTop) return null

        val region = NormalizedRegion(
            left = left.toDouble() / document.width,
            top = expandedTop.toDouble() / document.height,
            right = right.toDouble() / document.width,
            bottom = expandedBottom.toDouble() / document.height,
        )

        // A region covering essentially the whole document reproduces the wider pass with no new
        // information — see SelectedRegionCrop.toPixels's identical guard, which this mirrors so the
        // two never disagree about what counts as "the whole frame".
        if (region.width >= WHOLE_FRAME_THRESHOLD && region.height >= WHOLE_FRAME_THRESHOLD) return null

        return region
    }

    /**
     * Vertical margin above/below the header-through-value span, as a fraction of the target row's
     * own height. A third of a row's height is enough to include a partially-clipped glyph's
     * descender or a following row's ascender without pulling in a whole extra printed row.
     */
    private const val VERTICAL_MARGIN_FRACTION = 0.35

    /**
     * Horizontal margin, as a fraction of the document width. Wider than the vertical margin because
     * a nutrition table's columns are the thing being re-read, and a resolved header can sit
     * horizontally offset from the value row beneath it — see [ColumnClassifier]'s own basis-anchor
     * handling, which the same header/value misalignment already motivates.
     */
    private const val HORIZONTAL_MARGIN_FRACTION = 0.05

    /** See [SelectedRegionCrop.toPixels]'s identical constant; kept in step deliberately. */
    private const val WHOLE_FRAME_THRESHOLD = 0.97
}
