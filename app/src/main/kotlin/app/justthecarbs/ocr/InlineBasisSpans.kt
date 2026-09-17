package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis

/**
 * Finds a measurement basis printed *inside* a nutrient row (correction pass §6).
 *
 * The geometry-first architecture reads column meaning from [NutritionRowKind.HEADER] rows, which is
 * right for the tabular labels it was built for. Some real packaging has no header row at all and
 * prints the basis on the nutrient line itself:
 *
 * ```
 * Carbohydrate   per 100 g   45 g
 * ```
 *
 * [RowClassifier] already keeps such a row as `TOTAL_CARBOHYDRATE` rather than demoting it to a
 * header — it checks nutrient terms before header shape — so the row survives, but nothing then
 * establishes what its number measures and the reading was refused as `NotFound`.
 *
 * This finds the basis phrase as a *subspan* and reports which element indices it occupies. Two jobs,
 * both narrow:
 *
 * 1. Supply the basis when no classified column claims a cell.
 * 2. Mark the phrase's own elements so its "100" can never be read as a carbohydrate value — the
 *    safety requirement, since 100 clears the per-100 validator's ceiling and would be handed back
 *    as a confident answer.
 *
 * This deliberately does **not** reintroduce proximity scoring. It recognises one fixed vocabulary in
 * one fixed order, and a row containing two different bases yields no single answer at all.
 */
object InlineBasisSpans {

    /** One recognised basis phrase and the element indices it covers. */
    data class Span(val basis: NutritionBasis, val elementIndices: Set<Int>)

    /** How many consecutive elements a basis phrase may span, e.g. "per" "100" "ml". */
    private const val MAX_SPAN = 3

    /** Words that introduce the phrase. The one shared list — see [NutritionTerminology]. */
    private val CONNECTIVES = NutritionTerminology.connectives

    /**
     * Every basis phrase on the row, left to right.
     *
     * A row printing two different bases returns both, and the caller refuses to pick between them —
     * guessing there would be exactly the confident wrong answer the column stage already refuses.
     */
    fun find(row: LogicalRow): List<Span> {
        if (ParserWorkCounters.enabled) ParserWorkCounters.inlineBasisCalls++
        val spans = mutableListOf<Span>()
        var index = 0

        while (index < row.elements.size) {
            val span = spanAt(row, index)
            if (span == null) {
                index++
                continue
            }
            spans += span
            index += span.elementIndices.size
        }

        return spans
    }

    /**
     * A basis phrase starting exactly at [start], or null.
     *
     * Requires the literal "100" as its own element followed by the unit, so "per 100 g" matches
     * while a bare "100 g" of package weight elsewhere does not — the connective is what marks the
     * phrase as a measurement basis rather than a quantity.
     */
    private fun spanAt(row: LogicalRow, start: Int): Span? {
        val first = row.elements.getOrNull(start) ?: return null
        if (NutritionTerminology.normalize(first.text) !in CONNECTIVES) return markedAfterSpanAt(row, start)

        val hundred = row.elements.getOrNull(start + 1) ?: return null
        if (NutritionTerminology.normalize(hundred.text) != "100") return null

        val unitElement = row.elements.getOrNull(start + 2) ?: return null
        // Every spelling a package uses, not just the two-letter abbreviations — "per 100 gram" is an
        // ordinary Dutch and German form. See NutritionTerminology.gramUnits.
        val basis = NutritionTerminology.basisUnitFor(NutritionTerminology.normalize(unitElement.text))
            ?: return null

        return Span(basis, (start until start + MAX_SPAN).toSet())
    }

    /**
     * `100 g kohta`, `100 g için`, `100 g'da`, `100 g-ban`, `100 g:ssa`: the languages that mark a
     * basis after the quantity, with a postposition or a case suffix on the unit, rather than with a
     * connective before it. Either marker does the connective's job — it says `100 g` is a basis, not
     * an amount.
     *
     * Added 2026-09-17. Without it the Estonian row `Süsivesikud 100 g kohta 62,5 g` read
     * `Confident 100.0` and the Turkish and Finnish rows offered `100` beside the real figure
     * (`EuropeanLabelDiagnosticTest`). A bare `100 g` with no marker is still not a basis, which is
     * why a Latvian or Lithuanian row printing only `100 g` still offers `100` as a choice.
     */
    private fun markedAfterSpanAt(row: LogicalRow, start: Int): Span? {
        if (NutritionTerminology.normalize(row.elements[start].text) != "100") return null
        val unitElement = row.elements.getOrNull(start + 1) ?: return null
        val basis = NutritionTerminology.basisUnitFor(NutritionTerminology.normalize(unitElement.text))
            ?: return null
        if (NutritionTerminology.carriesCaseSuffix(unitElement.text)) return Span(basis, setOf(start, start + 1))

        val marker = row.elements.getOrNull(start + 2) ?: return null
        if (NutritionTerminology.normalize(marker.text) !in NutritionTerminology.postpositions) return null
        return Span(basis, (start until start + MAX_SPAN).toSet())
    }
}
