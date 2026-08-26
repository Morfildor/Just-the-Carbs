package app.justthecarbs.ocr

/**
 * Finds where the nutrition table sits in a recognized full-package capture.
 *
 * ## STATUS: NOT WIRED INTO PRODUCTION. Retained as research, with its failure recorded.
 *
 * This was built as the locating half of a two-pass still read (Pass A locates, Pass B re-recognises
 * the isolated table and answers). **That was measured, failed, and was reverted.** It is kept, with
 * its 11 tests, because the next attempt at localisation should start from what was learned, not from
 * scratch. Do not re-wire it into [LabelAnalyzer] without addressing both findings below.
 *
 * ### Finding 1 — re-recognising a crop can manufacture a confident-wrong
 *
 * Rescaling changes how ML Kit tokenises. On the Kinder canary the printed unit marker `(g)` came back
 * from the isolated crop as `(9)` — a well-formed single-digit carbohydrate value, on the correct row,
 * introduced by the correct nutrient term. It beat the real `53,5` and produced `Confident 9.0`.
 *
 * That specific hazard is now guarded structurally by [UnitMarkerFilter], so it is no longer a blocker
 * on its own. But the general lesson stands and is the reason multi-scale work must be an evidence
 * ensemble rather than a replacement: **an independent recognition pass is a new opportunity to be
 * wrong, not merely a cleaner look at the same thing.**
 *
 * ### Finding 2 — a vertical band cannot isolate horizontally adjacent panels, and it drops headers
 *
 * Measured on the Kinder fixture: the located region was `[98,419,900,914]` and **12 of 24 rows fell
 * outside it, including the basis header band**. Pass B on that crop reported `rejected: 53.5: no
 * column` — precisely the failure that removing the overlay crop had fixed, reintroduced in a new
 * guise. `MAX_HEADER_GAP_IN_PITCHES` is not the answer: a multilingual header spans many reconstructed
 * rows and sits further from the body than any pitch multiple safely covers.
 *
 * Worse, that fixture contains a **second package's ingredient panel horizontally adjacent** to the
 * table, so reconstructed rows already span both panels before this class sees them. A vertical band
 * cannot separate two things that share the same vertical extent. Any future locator must work on raw
 * elements *before* [LogicalRowBuilder] merges them, and must cluster in two dimensions.
 *
 * ## Why this exists
 *
 * Measured, not reasoned about. Every committed fixture is a pre-cropped nutrition panel: the table
 * fills the frame. A phone capture is a whole package, where the table occupies roughly a third to a
 * half of the frame and is surrounded by an ingredient list, marketing copy, a barcode and a date.
 *
 * A ratio sweep compositing the fixtures into realistic frames showed what that costs:
 *
 * ```
 * kinder     ratio=1.00 -> Confident 53.5     ratio=0.30..0.80 -> NotFound (all four)
 * stokbrood  ratio=1.00 -> Confident 46.0     ratio=0.80       -> NotFound
 * yoghurt    ratio=1.00 -> Confident 5.0      ratio=0.80       -> NotFound
 * ```
 *
 * Note stokbrood and yoghurt getting *worse* as the table gets bigger: larger surrounding prose is
 * recognised better, so it produces more competing rows. This is interference, not resolution.
 *
 * The previous fix — cropping to the scan overlay before recognition — removed that interference but
 * also removed the basis header on tall labels, costing both canaries. Neither cropping to a fixed
 * rectangle nor reading the whole frame is right, because **the table's position is a property of the
 * package, not of the app**. It is unknowable before recognition and observable after it.
 *
 * The intended shape was: recognise everything (Pass A), locate the table from that recognized
 * geometry, isolate it, and recognise it again (Pass B). This class is the locating step of that
 * design — see the status note above for why the design did not survive measurement.
 *
 * ## What it locates from
 *
 * Structural signals only, all already computed by the existing pipeline:
 *
 * - a **basis or serving header** ([NutritionRowKind.HEADER]) — the strongest single anchor, because
 *   the header is what makes a value placeable at all;
 * - **carbohydrate anchors** — total-carbohydrate and child rows;
 * - **nutrient rows generally** — rows naming any nutrient term;
 * - **aligned value columns** — the vertical structure that distinguishes a table from prose.
 *
 * Expansion is expressed in **text heights and row pitches measured from the located rows themselves**,
 * never in pixels and never as a fraction of the frame. A pixel margin is a guess about resolution; a
 * fraction of the frame is a guess about how the user framed it. A row pitch is a measurement of the
 * thing being isolated.
 *
 * ## What it must never do
 *
 * Return a region it is not confident about. A wrong crop that happens to contain a number is exactly
 * how a confident-wrong answer gets produced, and a confident-wrong carbohydrate figure is the worst
 * output this app can produce. When the evidence does not describe a coherent table, this returns
 * null, the caller keeps Pass A's own verdict, and the user is asked for a better photograph.
 */
internal object NutritionTableLocator {

    /**
     * The located table, in the coordinates of the image [rows] were built from.
     *
     * [confidence] is not a score to rank by — nothing ranks these. It records which structural
     * evidence was present, so the caller can require the strong form and diagnostics can say what was
     * missing. There is exactly one accept/reject decision and it is made here.
     */
    data class Located(
        val region: OcrBox,
        val anchorRowCount: Int,
        val hasHeader: Boolean,
        val hasCarbohydrateAnchor: Boolean,
    )

    /**
     * Where the nutrition table is, or null when the recognized text does not describe one.
     *
     * Null is a legitimate and common answer: a capture of the front of a package contains no table.
     */
    fun locate(document: OcrDocument): Located? {
        if (document.elements.isEmpty()) return null
        val rows = LogicalRowBuilder.build(document)
        if (rows.isEmpty()) return null
        return locate(rows, document.width, document.height)
    }

    fun locate(rows: List<LogicalRow>, imageWidth: Int, imageHeight: Int): Located? {
        if (rows.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null

        val classified = rows.map { it to RowClassifier.classify(it) }

        // A TOTAL row is required to anchor. A child row alone is explicitly not enough, and that is
        // a measured correction rather than caution: an ingredient list names carbohydrate children
        // constantly — "glucosestroop", "cacaopoeder", "dextrose", "maltodextrine" — so
        // `RowClassifier` correctly types those prose rows CARBOHYDRATE_CHILD. Seeding on children
        // located a "table" inside a pure ingredient paragraph in this class's own test, which is
        // precisely the crop that would feed a second recognition pass a page of prose.
        //
        // Children still join the band once a total has anchored it (they are ordinary table rows
        // there); they simply cannot start one.
        val totalRows = classified.filter { it.second == NutritionRowKind.TOTAL_CARBOHYDRATE }.map { it.first }
        if (totalRows.isEmpty()) return null
        val carbohydrateRows = totalRows

        val headerRows = classified.filter { it.second == NutritionRowKind.HEADER }.map { it.first }

        // The carbohydrate rows are the seed, and deliberately the only seed. A broader "any nutrient
        // word" seed was considered and rejected: nutrient words appear in ingredient lists and
        // marketing copy as readily as in tables ("bevat suikers", "bron van eiwitten"), so seeding on
        // them would anchor the band to prose the isolation exists to exclude. The band grows outward
        // by row pitch from here, which is what picks up the rest of the table's rows without needing
        // to name them.
        val seed = carbohydrateRows
        val pitch = medianRowPitch(rows)
        val textHeight = medianTextHeight(rows)
        if (pitch <= 0.0 || textHeight <= 0.0) return null

        // Grow a band through rows separated by no more than a few pitches. A table's rows are
        // regularly spaced; the gap between the table and the ingredient paragraph is larger than the
        // gap between two of its rows. Using the table's OWN measured pitch is what makes this
        // resolution-independent and package-independent.
        val band = growBand(rows, seed, pitch)
        if (band.isEmpty()) return null

        // The header must be included when one exists, even if it sits outside the grown band — this
        // is the exact failure the pre-Pass-A crop caused. A header above the band is pulled in by
        // extending the top edge to cover it, never by relaxing the band rule for everything else.
        val governingHeader = headerRows
            .filter { it.box.bottom <= band.maxOf { row -> row.box.bottom } }
            .filter { header ->
                // Within a plausible distance above the band: a header governs the rows beneath it,
                // but a "per 100 g" printed in a different panel entirely does not.
                val bandTop = band.minOf { it.box.top }
                header.box.bottom >= bandTop - pitch * MAX_HEADER_GAP_IN_PITCHES
            }
            .minByOrNull { it.box.top }

        val included = band + listOfNotNull(governingHeader)
        val union = included.drop(1).fold(included.first().box) { acc, row -> acc.union(row.box) }

        // Expansion in text heights, measured from the rows being isolated. Horizontal is more
        // generous than vertical because a value column can sit well right of the widest recognized
        // nutrient name, and clipping a value column is how a table becomes unreadable; vertical
        // over-reach costs interference, which is what this exists to remove.
        val padX = textHeight * HORIZONTAL_PAD_IN_HEIGHTS
        val padY = textHeight * VERTICAL_PAD_IN_HEIGHTS
        val region = OcrBox(
            left = (union.left - padX).toInt().coerceIn(0, imageWidth - 1),
            top = (union.top - padY).toInt().coerceIn(0, imageHeight - 1),
            right = (union.right + padX).toInt().coerceIn(1, imageWidth),
            bottom = (union.bottom + padY).toInt().coerceIn(1, imageHeight),
        )
        if (region.width < MIN_REGION_PIXELS || region.height < MIN_REGION_PIXELS) return null

        // A region covering nearly the whole frame isolates nothing — re-recognising it would cost a
        // second full-resolution pass to obtain the same interference Pass A already has.
        //
        // Measured against how much of the frame's TEXT is being excluded, not against the region's
        // own dimensions. A region clamped to the image edges reports a width smaller than the frame
        // while still containing every recognized element, and a dimension test reads that as a
        // meaningful crop. This asks the question that actually matters: is there anything outside it?
        val excludedRows = rows.count { row ->
            row.box.right < region.left || row.box.left > region.right ||
                row.box.bottom < region.top || row.box.top > region.bottom
        }
        if (excludedRows == 0) return null

        return Located(
            region = region,
            anchorRowCount = included.size,
            hasHeader = governingHeader != null,
            hasCarbohydrateAnchor = true,
        )
    }

    /**
     * Rows reachable from [seed] through gaps of at most [pitch] x [MAX_ROW_GAP_IN_PITCHES].
     *
     * Walks outward from the seed rows in printed order rather than clustering everything: a package
     * often prints a second aligned block (a reference-intake summary, a neighbouring language's
     * table), and a cluster over the whole frame would merge them. Reachability from a carbohydrate
     * anchor keeps the band anchored to the table that matters.
     */
    private fun growBand(
        rows: List<LogicalRow>,
        seed: List<LogicalRow>,
        pitch: Double,
    ): List<LogicalRow> {
        if (seed.isEmpty()) return emptyList()
        val ordered = rows.sortedBy { it.box.top }
        val seedIndices = seed.mapNotNull { row -> ordered.indexOf(row).takeIf { it >= 0 } }.toSortedSet()
        if (seedIndices.isEmpty()) return emptyList()

        val maxGap = pitch * MAX_ROW_GAP_IN_PITCHES
        val included = seedIndices.toMutableSet()

        // Downward from the lowest seed row, upward from the highest, stopping at the first gap too
        // large to be a row boundary. A single stop is correct: once the printed block has ended,
        // anything past it belongs to something else.
        var index = seedIndices.last()
        while (index + 1 < ordered.size) {
            val gap = ordered[index + 1].box.top - ordered[index].box.bottom
            if (gap > maxGap) break
            included += index + 1
            index++
        }
        index = seedIndices.first()
        while (index - 1 >= 0) {
            val gap = ordered[index].box.top - ordered[index - 1].box.bottom
            if (gap > maxGap) break
            included += index - 1
            index--
        }

        // Seed rows between the extremes are included regardless, so a row the walk skipped over
        // cannot leave a hole in the band.
        val lowest = included.min()
        val highest = included.max()
        return (lowest..highest).mapNotNull { ordered.getOrNull(it) }
    }

    private fun medianRowPitch(rows: List<LogicalRow>): Double {
        if (rows.size < 2) return 0.0
        val ordered = rows.sortedBy { it.box.centerY }
        val gaps = ordered.zipWithNext { a, b -> (b.box.centerY - a.box.centerY).toDouble() }
            .filter { it > 0 }
            .sorted()
        if (gaps.isEmpty()) return 0.0
        return gaps[gaps.size / 2]
    }

    private fun medianTextHeight(rows: List<LogicalRow>): Double {
        val heights = rows.flatMap { row -> row.elements.map { it.box.height } }.sorted()
        if (heights.isEmpty()) return 0.0
        return heights[heights.size / 2].toDouble()
    }

    /**
     * How large a vertical gap still counts as the next row of the same block, in row pitches.
     *
     * 2.5 tolerates a table with a blank separator line or a sub-heading between sections, while
     * staying well under the gap between a table and a separate paragraph. Measured in pitches so it
     * describes the printed layout rather than a resolution.
     */
    private const val MAX_ROW_GAP_IN_PITCHES = 2.5

    /**
     * How far above the band a header may sit and still govern it, in row pitches.
     *
     * More generous than the row gap because a multilingual header block legitimately sits several
     * lines above the first nutrient row — on this repo's own Sondey fixture a correct per-100 header
     * is seven reconstructed rows above the carbohydrate row it governs.
     */
    private const val MAX_HEADER_GAP_IN_PITCHES = 8.0

    private const val HORIZONTAL_PAD_IN_HEIGHTS = 1.5
    private const val VERTICAL_PAD_IN_HEIGHTS = 1.0

    /** Below this there is not enough left to recognise. */
    private const val MIN_REGION_PIXELS = 64
}
