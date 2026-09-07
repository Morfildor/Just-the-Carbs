package app.justthecarbs.ocr

/**
 * Where the crop screen's box should start when the automatic fast path declined and the user must
 * confirm a rectangle by hand — narrower than [ScanRegionMapper]'s generic scan-guide expansion,
 * without guessing where a table is that recognition has not already located.
 *
 * ## What this is not
 *
 * This is **not** a table-localization architecture. [ScanRegionMapper]'s own KDoc records a
 * measured history of exactly that idea — vertical banding, connected-component clustering, an
 * automatic table-detecting proposal — being tried and rejected: each one damaged real canaries by
 * cutting off a basis header or a value column the interpreter needed, because *guessing* where a
 * table's edges are before recognition has already run cannot see what recognition sees.
 *
 * This object never guesses. It only ever asks: **what has [NutritionDocumentModel] already
 * established about this exact document?** A declaration and a resolved column exist or they do
 * not; there is no scoring, no clustering, no new geometric heuristic. When nothing has been
 * established, this returns null and the caller falls back to [ScanRegionMapper.expand] exactly as
 * before — the crop screen still opens on a rectangle, just not a narrower one.
 *
 * ## Priority, and why each tier is safe
 *
 * 1. **The carbohydrate declaration's own bounds, unioned with the resolved per-100 header band.**
 *    This is the same "header through value" span [TargetedRereadRegion] already establishes is
 *    safe to crop to for a native-resolution reread — the header is what keeps
 *    [ColumnClassifier] from reclassifying the per-100 column as [NutritionColumnKind.REFERENCE_PERCENT]
 *    on a tall label, which is precisely the sondey/kinder regression [ScanRegionMapper] documents.
 *    Reused here at crop-screen scale rather than reread scale: generous padding, because the user
 *    can still drag the corners if the box is imperfect, whereas a reread crop has no such recourse.
 * 2. **The owning [NutritionPanel]'s bounds**, when a panel exists but no single declaration could
 *    be targeted — a genuine cross-candidate conflict or an ambiguity between different rows, where
 *    the panel as a whole is still known but which declaration is "the" one is exactly the question
 *    being asked of the user.
 * 3. **Null.** No panel, no declaration, nothing resembling a nutrition table was structurally
 *    located at all. The generic scan-guide rectangle is the only honest starting point.
 *
 * ## Never a second acceptance rule
 *
 * This never decides whether a reading is safe to accept — that is [ScanPresentationDecision]'s job
 * entirely, and this object is only ever consulted *after* that decision has already routed to
 * [ScanPresentationDecision.Action.CROP_FALLBACK]. It positions a rectangle for the user to look at
 * and correct; it does not read a value, does not resolve a scale, and does not touch
 * [ColumnClassifier], [RowClassifier], [ScaleAmbiguity], [CrossColumnRatioCheck] or any child-row
 * exclusion. Those all still run, unchanged, on whatever the user ultimately confirms.
 */
internal object AutoCropTargeting {

    /**
     * The tightened starting rectangle for [document], or null when no safe declaration or panel
     * bound was established and the caller should keep today's generic fallback.
     *
     * [document] is whichever document the crop screen is about to show — Pass A's whole-frame
     * document in the ordinary case. Coordinates in the result are fractions of that same document,
     * matching every other [NormalizedRegion] producer in this package.
     */
    fun regionFor(document: OcrDocument?): NormalizedRegion? {
        if (document == null || document.elements.isEmpty()) return null
        if (document.width <= 0 || document.height <= 0) return null

        val panels = NutritionDocumentModel.build(document).panels
        if (panels.isEmpty()) return null

        val declarationRegion = panels.firstNotNullOfOrNull { panel -> declarationRegion(document, panel) }
        if (declarationRegion != null) return declarationRegion

        // No single declaration could be targeted (an ambiguity between distinct rows, or a
        // structural conflict). [NutritionDocumentModel.build] always returns at least one panel for
        // a non-empty document — when localization found nothing it hands back the WHOLE document as
        // one unlocalized fallback panel (`localized == false`), which carries no positive evidence
        // of a table at all and is not a narrowing of anything. Only a `localized == true` panel —
        // [NutritionPanelLocator2D]'s own bar: a carbohydrate anchor, a per-100 header, several
        // nutrient anchors and a repeated numeric column all describing the same bounded area — is
        // established structure worth cropping to. Preferring the panel with the most recognised
        // elements is the least presumptuous choice among several localized candidates, mirroring
        // [TargetedRereadTrigger]'s own single-panel requirement.
        val panel = panels.filter { it.localized }.maxByOrNull { it.elements.size } ?: return null
        return paddedAndClamped(document, panel.bounds)
    }

    /**
     * The declaration-plus-header region for [panel], or null when [panel] has no single
     * total-carbohydrate declaration to target.
     *
     * Mirrors [TargetedRereadTrigger.targetFor]'s own declaration selection exactly — a nutrient
     * name printed in several languages reconstructs as several value-less declarations agreeing
     * with each other, which is not the ambiguity this guard exists to refuse. Only declarations
     * that actually carry a value can disagree about what the figure is, so those are what must be
     * unambiguous.
     */
    private fun declarationRegion(document: OcrDocument, panel: NutritionPanel): NormalizedRegion? {
        val totals = panel.declarations.filter { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }
        val declaration = totals.singleOrNull()
            ?: totals.filter { it.valueCells.isNotEmpty() }.singleOrNull()
            ?: return null

        val headerBox = panel.columns
            .filter { it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML }
            .mapNotNull { it.headerBox }
            .reduceOrNull { a, b -> a.union(b) }

        val bounds = headerBox?.union(declaration.bounds) ?: declaration.bounds
        return paddedAndClamped(document, bounds)
    }

    /**
     * [bounds], grown by [PADDING_FRACTION] per side and clamped to [document]'s own extent.
     *
     * The padding is deliberately more generous than [TargetedRereadRegion]'s reread margins: this
     * rectangle is the *starting* point for a screen whose entire purpose is letting the user drag
     * it, so erring toward "a little wide but obviously over the right content" is the safer
     * failure than "so tight a slightly-misjudged declaration box clips the very row being asked
     * about." A height-relative fraction keeps the margin proportional on both a short two-row
     * declaration and a tall multi-language one, rather than a fixed pixel band that is generous on
     * one capture resolution and negligible on another.
     */
    private fun paddedAndClamped(document: OcrDocument, bounds: OcrBox): NormalizedRegion? {
        val height = (bounds.bottom - bounds.top).coerceAtLeast(1)
        val verticalPad = (height * PADDING_FRACTION).toInt()
        val horizontalPad = (height * PADDING_FRACTION).toInt()

        val left = (bounds.left - horizontalPad).coerceIn(0, document.width)
        val top = (bounds.top - verticalPad).coerceIn(0, document.height)
        val right = (bounds.right + horizontalPad).coerceIn(0, document.width)
        val bottom = (bounds.bottom + verticalPad).coerceIn(0, document.height)

        if (right <= left || bottom <= top) return null

        val region = NormalizedRegion(
            left = left.toDouble() / document.width,
            top = top.toDouble() / document.height,
            right = right.toDouble() / document.width,
            bottom = bottom.toDouble() / document.height,
        )

        // A region already covering essentially the whole document is not a narrowing at all — see
        // [TargetedRereadRegion]'s identical guard, kept in step so the two never disagree about
        // what counts as "the whole frame".
        if (region.width >= WHOLE_FRAME_THRESHOLD && region.height >= WHOLE_FRAME_THRESHOLD) return null

        return region
    }

    /**
     * Padding per side, as a fraction of the target content's own height. Applied to both axes so
     * a wide, short declaration and a narrow, tall one both get a comparable amount of breathing
     * room relative to their own scale rather than to the document's.
     */
    private const val PADDING_FRACTION = 0.6

    /** See [TargetedRereadRegion]'s identical private constant; kept in step deliberately. */
    private const val WHOLE_FRAME_THRESHOLD = 0.97
}
