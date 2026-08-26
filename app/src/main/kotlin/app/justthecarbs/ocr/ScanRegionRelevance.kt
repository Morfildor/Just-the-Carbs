package app.justthecarbs.ocr

/**
 * Applies what the user framed as **relevance**, after recognition, never as a crop before it.
 *
 * ## Why this exists
 *
 * The scan rectangle used to be a destructive OCR boundary: the captured image was cropped to it
 * before recognition. That was measured to remove the basis header band on tall labels and cost both
 * canaries — sondey reported `rejected: 61.9: REFERENCE_PERCENT column`, i.e. the right value on the
 * right row, made unplaceable because its `o/100 g` header had been cropped away.
 *
 * The framing is still real information — the user did point at the table they care about. It is
 * simply applied where it cannot destroy evidence: as a tie-breaker among candidates the parser has
 * *already accepted*.
 *
 * ## The safety contract
 *
 * This class can only ever **narrow** an existing reading. Specifically it may:
 *
 * - drop candidates from an [LabelReading.Ambiguous] list, and
 * - collapse an `Ambiguous` to `Confident` when exactly one candidate survives.
 *
 * It may **never**:
 *
 * - turn [LabelReading.NotFound] into a reading — a refusal stays a refusal, so no framing gesture
 *   can conjure a value the parser declined to place;
 * - alter a [LabelReading.Confident] candidate's value, basis or provenance;
 * - introduce a candidate that was not already in the list;
 * - supply or change a basis.
 *
 * Those are the properties that keep it outside the safety architecture rather than part of it. A
 * filter that could promote a refusal would be a second, weaker interpretation path — exactly what
 * the geometry-first rewrite removed.
 *
 * ## Why dropping every candidate is a no-op rather than a refusal
 *
 * If no candidate overlaps the frame, the user's aim and the parser's evidence disagree. The frame is
 * a soft guide drawn over a preview, and a table whose rows sit slightly outside it is ordinary; the
 * parser's evidence is the harder signal. Returning the reading unchanged is the conservative choice —
 * it neither invents nor discards. Turning it into `NotFound` would let a mis-drawn overlay suppress a
 * correct reading.
 */
internal object ScanRegionRelevance {

    /**
     * How much of a candidate's own box must fall inside the framed region for it to count as framed.
     *
     * Deliberately low. A nutrition row is wide and the frame is a guide, not a target the user is
     * expected to hit precisely, so a row hanging out of one side is still the row they meant. This
     * only has to separate "the user was looking at this" from "this is a different part of the
     * package", which on a real capture are far apart.
     */
    private const val MIN_OVERLAP = 0.30

    fun apply(
        report: NutritionParseReport,
        region: NormalizedRegion?,
        imageWidth: Int,
        imageHeight: Int,
    ): NutritionParseReport {
        if (region == null || imageWidth <= 0 || imageHeight <= 0) return report

        // Only an ambiguity has anything to choose between. A Confident reading is already the
        // parser's single answer and must not be second-guessed by framing; a NotFound is a refusal.
        val ambiguous = report.reading as? LabelReading.Ambiguous ?: return report

        // The frame is expanded by the same safety margin the crop used, for the same reason: the
        // overlay is a guide and the table may legitimately sit a little outside it. Here the margin
        // is harmless — at worst it keeps a candidate, it can never delete a header.
        val framed = ScanRegionMapper.expand(region)
        val survivors = ambiguous.candidates.filter { candidate ->
            overlapFraction(candidate.geometry, framed, imageWidth, imageHeight) >= MIN_OVERLAP
        }

        return when {
            // Nothing framed, or no discrimination achieved: leave the parser's answer alone.
            survivors.isEmpty() || survivors.size == ambiguous.candidates.size -> report
            // Exactly one candidate is where the user was pointing. This is the case the filter
            // exists for: the parser could not choose on geometry alone, and the user already did.
            //
            // A basis is required to promote. `LabelReading.Confident` requires one by invariant, and
            // a candidate without a basis is one whose printed per-100 declaration was never
            // established — a genuine unknown. Framing says which value the user meant; it says
            // nothing about whether the label read per 100 g or per 100 ml, and inventing that is
            // exactly the substitution this app must never make. Such a reading stays Ambiguous so
            // the UI keeps asking.
            survivors.size == 1 && survivors.single().basis != null ->
                report.copy(reading = LabelReading.Confident(survivors.single()))
            else -> report.copy(reading = LabelReading.Ambiguous(survivors))
        }
    }

    /** The fraction of [box]'s own area that lies inside [region]. */
    private fun overlapFraction(
        box: OcrBox,
        region: NormalizedRegion,
        imageWidth: Int,
        imageHeight: Int,
    ): Double {
        val boxArea = (box.right - box.left).toDouble() * (box.bottom - box.top).toDouble()
        if (boxArea <= 0.0) return 0.0

        val regionLeft = region.left * imageWidth
        val regionTop = region.top * imageHeight
        val regionRight = region.right * imageWidth
        val regionBottom = region.bottom * imageHeight

        val overlapWidth = minOf(box.right.toDouble(), regionRight) - maxOf(box.left.toDouble(), regionLeft)
        val overlapHeight = minOf(box.bottom.toDouble(), regionBottom) - maxOf(box.top.toDouble(), regionTop)
        if (overlapWidth <= 0.0 || overlapHeight <= 0.0) return 0.0

        return (overlapWidth * overlapHeight) / boxArea
    }
}
