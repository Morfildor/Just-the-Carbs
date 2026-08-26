package app.justthecarbs.ocr

/**
 * Re-reads an already-recognised capture through the rectangle the user confirmed.
 *
 * ## The architecture this implements (Strategy A)
 *
 * ```
 * full-resolution capture
 *   -> ML Kit, once
 *   -> raw OCR elements + geometry          <- retained in PassAResult
 *   -> user-confirmed table rectangle
 *   -> ElementRegionFilter                  <- element-level, geometric, removal-only
 *   -> LogicalRowBuilder / RowClassifier / ColumnClassifier / interpreter (unchanged)
 * ```
 *
 * There is deliberately **no second recognition pass**. That is not a performance choice, it is a
 * correctness one: an independent recognition of a rescaled crop produced `(9)` from a printed `(g)`
 * on the Kinder canary, a well-formed single-digit carbohydrate value on the correct row that beat
 * the real 53,5. Reusing Pass A's elements makes that class of failure structurally impossible —
 * every character the parser sees after cropping is a character it saw before cropping.
 *
 * ## Why it may keep the whole-frame reading
 *
 * If the selection retains nothing recognisable, this returns the **unfiltered** report rather than a
 * refusal. The user's gesture is then evidence about nothing: a rectangle enclosing no text says the
 * crop went wrong, not that the label has no carbohydrate row, and discarding a good whole-frame
 * reading on that basis would make the feature strictly worse than not having it.
 */
internal object SelectedTableReader {

    /**
     * Below this fraction of elements removed, the selection has not isolated anything (spec §11).
     *
     * 2% is deliberately near-zero: this flags "the rectangle did essentially nothing", not "the
     * rectangle could be tighter". Judging tightness would be judging the table's size, which is
     * measured to prove nothing.
     */
    const val MIN_USEFUL_REMOVAL = 0.02

    /** What was done, for the evidence bundle and for deciding whether a fallback is worth trying. */
    enum class Outcome {
        /** The selection filtered the document and the parser ran on the survivors. */
        FILTERED,

        /** The selection retained nothing; the whole-frame reading was kept unchanged. */
        RETAINED_WHOLE_FRAME,

        /** There was no document to filter — recognition itself failed. */
        NO_DOCUMENT,
    }

    data class Result(
        val report: NutritionParseReport,
        val outcome: Outcome,
        val elementsBefore: Int,
        val elementsAfter: Int,
    ) {
        /**
         * How much of the recognised document the selection actually removed (spec §11).
         *
         * 0.0 means the rectangle removed nothing — the user pressed "Read table" on a selection that
         * is, as far as recognition is concerned, the whole frame. That is worth telling them, because
         * the pipeline they just ran is identical to the one that already failed.
         *
         * Expressed as a fraction of elements rather than of area on purpose: a rectangle's size says
         * nothing about whether it isolated the table (measured — a 77%-of-frame proposal once
         * contained the answer and still lost the reading, while a 13% one cut it in half). What
         * matters is whether interfering *text* was excluded.
         */
        val fractionRemoved: Double
            get() = if (elementsBefore <= 0) 0.0
            else (elementsBefore - elementsAfter).toDouble() / elementsBefore

        /**
         * True when the selection is not doing the job it exists to do.
         *
         * Deliberately not a rule about rectangle size (§11 forbids that): a large nutrition table
         * can legitimately fill the frame. This asks only whether narrowing changed the input at all.
         */
        val isIneffective: Boolean
            get() = outcome == Outcome.FILTERED && fractionRemoved < MIN_USEFUL_REMOVAL
    }

    /**
     * Applies [region] to [document] and re-parses.
     *
     * [wholeFrameReport] is what Pass A produced without any crop, used verbatim when the selection
     * turns out to be unusable.
     */
    fun read(
        document: OcrDocument?,
        wholeFrameReport: NutritionParseReport,
        region: NormalizedRegion?,
    ): Result {
        if (document == null) {
            return Result(wholeFrameReport, Outcome.NO_DOCUMENT, elementsBefore = 0, elementsAfter = 0)
        }

        val filtered = ElementRegionFilter.filter(document, region)
            ?: return Result(
                wholeFrameReport,
                Outcome.RETAINED_WHOLE_FRAME,
                elementsBefore = document.elements.size,
                elementsAfter = document.elements.size,
            )

        // The full production parser, unchanged. Every safety rule — the child-nutrient exclusion,
        // CarbohydrateTermAnchor, UnitMarkerFilter, the usable-basis-column requirement — runs here
        // exactly as it does on a whole frame. The crop removed interference; it granted no licence.
        val report = NutritionTableParser.parseWithDiagnostics(filtered)

        return Result(
            report = report,
            outcome = Outcome.FILTERED,
            elementsBefore = document.elements.size,
            elementsAfter = filtered.elements.size,
        )
    }
}
