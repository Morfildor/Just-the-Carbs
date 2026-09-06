package app.justthecarbs.ocr

/**
 * Decides whether one bounded, targeted native-resolution reread is worth attempting, and computes
 * where — the policy half of the targeted-reread feature; [TargetedRereadRegion] is the pure
 * geometry, [SelectedRegionRecognizer] the actual recognition mechanics.
 *
 * ## When a reread is worth trying
 *
 * Only when the ordinary evidence already found *something structural* — a total-carbohydrate row —
 * but could not turn it into a value the user can act on without typing:
 *
 * - a confident reading whose [ScaleAmbiguity] verdict is [ScaleAmbiguity.Verdict.Ambiguous] or
 *   [ScaleAmbiguity.Verdict.Unsupported] (the row and the value are both known; only the scale is in
 *   doubt), or
 * - no confident reading at all, but a total-carbohydrate row was located (a corrupted unit glyph or
 *   a fused digit defeated [CarbUnitAccompaniment]/[UnitMarkerFilter], the row/basis are otherwise
 *   fine).
 *
 * A reread is never attempted for an ambiguity between multiple *different* rows/candidates
 * ([LabelReading.Ambiguous] with more than one distinct value), a genuine cross-run conflict, or
 * when nothing resembling a carbohydrate declaration exists at all — none of those is a "the row is
 * known, only the digits are in doubt" situation, and re-reading a wider or unrelated area is
 * exactly what [ScanRegionMapper]'s own history warns against.
 *
 * ## At most one reread per capture attempt
 *
 * [SelectedTableResolution.resolve] calls [regionFor] at most once, after Strategy B has already run
 * (or been skipped as unnecessary) — never before, and never a second time if the first reread is
 * itself inconclusive. Repeatedly re-cropping and re-reading the same clause cannot manufacture
 * evidence a first tighter look did not find, and every extra pass costs real wall-clock time on a
 * dosing input.
 */
internal object TargetedRereadTrigger {

    /**
     * The row to target, and the header box (if any) to preserve — or null when no reread is
     * warranted for [document]'s current resolution state.
     */
    data class Target(val row: LogicalRow, val headerBox: OcrBox?, val panelTop: Int)

    /**
     * Whether [report]'s reading justifies a reread, and where.
     *
     * [document] must be the same document [report] was produced from — the row and column geometry
     * only means anything measured against the recognition that produced it.
     */
    fun targetFor(document: OcrDocument?, report: NutritionParseReport): Target? {
        if (document == null || document.elements.isEmpty()) return null

        val semantic = NutritionDocumentModel.build(document)
        val panel = semantic.panels.singleOrNull() ?: return null

        val totals = panel.declarations.filter { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }
        // Mirrors FocusedAmountEntry.of's own fallback: a nutrient name printed in several languages
        // reconstructs as several declarations naming no figure, which is agreement, not ambiguity —
        // only the declaration(s) actually carrying a value can disagree about what the figure is.
        val declaration = totals.singleOrNull()
            ?: totals.filter { it.valueCells.isNotEmpty() }.singleOrNull()
            ?: return null

        val worthRereading = when (val reading = report.reading) {
            is LabelReading.Confident -> {
                val verdict = ScaleAmbiguity.check(document, reading.candidate)
                verdict is ScaleAmbiguity.Verdict.Ambiguous || verdict is ScaleAmbiguity.Verdict.Unsupported
            }
            // A structurally-located row with no confident value at all — the unit-rejection shape.
            LabelReading.NotFound -> report.failureReason == CarbFailureReason.CARB_VALUE_MISSING
            // A genuine ambiguity between different candidates is not "digits in doubt on one row" —
            // a reread of one row cannot resolve a disagreement about which row is the real one.
            is LabelReading.Ambiguous -> false
        }
        if (!worthRereading) return null

        // The row actually carrying a value cell, not merely the widest source row -- a declaration
        // spanning several physical rows may have its label on one row and its figures on another,
        // and re-reading the label alone would target the wrong pixels.
        val row = declaration.valueCells.map { it.sourceRow }.distinct().singleOrNull()
            ?: declaration.sourceRows.maxByOrNull { it.box.width() }
            ?: return null
        val headerBox = panel.columns
            .filter { it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML }
            .mapNotNull { it.headerBox }
            .reduceOrNull { a, b -> a.union(b) }

        return Target(row = row, headerBox = headerBox, panelTop = panel.bounds.top)
    }

    /** The rereading region for [target], within [document]'s own coordinate space. */
    fun regionFor(document: OcrDocument, target: Target): NormalizedRegion? =
        TargetedRereadRegion.of(document, target.row, target.headerBox, target.panelTop)

    private fun OcrBox.width(): Int = right - left
}
