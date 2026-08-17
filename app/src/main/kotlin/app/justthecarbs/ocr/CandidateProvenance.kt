package app.justthecarbs.ocr

/**
 * Where an accepted carbohydrate value came from, at a granularity fine enough to prove it was the
 * total rather than a child nutrient printing the same number.
 *
 * Row granularity is sufficient for a table — the total and its "of which sugars" occupy different
 * rows. It is NOT sufficient for a prose label, where both share one reconstructed row and, on four
 * of this repo's real fixtures, the same printed value. There, only the bound nutrient term
 * distinguishes them, so the prose variant carries the span.
 */
sealed interface CandidateProvenance {

    /** A value read from a reconstructed table row. */
    data class FromRow(val rowText: String, val rowBox: OcrBox) : CandidateProvenance

    /**
     * A value bound to a nutrient term inside a prose declaration.
     *
     * [nutrientTerm] is the term the value bound to — the assertion target. [valueElementIndices]
     * locates the value within [rowText]'s row for diagnostics.
     */
    data class FromProseSpan(
        val nutrientTerm: String,
        val valueElementIndices: Set<Int>,
        val rowText: String,
    ) : CandidateProvenance
}
