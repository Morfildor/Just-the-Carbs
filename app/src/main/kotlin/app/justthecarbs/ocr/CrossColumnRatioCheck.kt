package app.justthecarbs.ocr

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Verifies a carbohydrate reading against the rest of its own table, using the two value columns the
 * label prints.
 *
 * ## The failure this exists for
 *
 * On `docs/Scan Evidence 02-09/20260902-085542-213` the package prints `72,0 g` and ML Kit returned
 * `12,0.g`. That is a well-formed number, on the correctly classified total-carbohydrate row, under
 * a correctly resolved `PER_100_G` column, with a plausible magnitude. **Every content-based guard
 * in this app passes it**, and on the device it advanced automatically to Quick Calculation.
 *
 * No rule about the token can catch that, and none should try — see rule 6 of the brief and the
 * standing prohibition on digit-substitution heuristics. But the *table* contradicts it. A nutrition
 * table states every nutrient twice, per 100 and per serving, so the ratio between the two columns
 * is a property of the serving size and is therefore **the same on every row**:
 *
 * ```
 * Energie    135 / 432 = 0.313
 * Fat        3.4 / 11  = 0.309
 * Saturates  0.3 / 1.1 = 0.273
 * Sugars     0.7 / 2.3 = 0.304
 * Fibre      0.9 / 2.8 = 0.321      <- median 0.31, five independent rows
 * Carb      22.5 / 12  = 1.875      <- the misread
 * Carb      22.5 / 72  = 0.3125     <- what the label actually prints
 * ```
 *
 * The check needs no knowledge of what a carbohydrate value should look like. It asks only whether
 * this row behaves like every other row on the same label.
 *
 * ## What it must never do
 *
 * **It validates or vetoes. It never calculates, replaces, corrects or ranks.** Given the 1.875 row
 * it reports a conflict; it does not divide 22.5 by 0.31 to "recover" 72, and it does not prefer a
 * candidate whose ratio is closer to the median. Deriving the value from the ratio would be exactly
 * the scoring the geometry-first architecture removed, and it would silently manufacture a
 * carbohydrate figure that no OCR pass ever read.
 *
 * Nor is an absent verdict a failure. A one-column label, or a table too damaged to yield three
 * coherent rows, returns [Verdict.NotEnoughEvidence] — which blocks *automatic* advancement without
 * claiming anything is wrong. That is the honest answer, and it is what the correct-but-unverifiable
 * capture needs.
 *
 * ## Why the median is taken in log space
 *
 * A ratio is multiplicative: `0.5x` and `2x` are equally wrong, but on a linear scale the second is
 * three times further from 1. Taking the median of `ln(ratio)` and comparing distances there makes
 * the tolerance symmetric in the way the quantity actually behaves. It also keeps one wildly wrong
 * row — a misread that lands two orders of magnitude out — from dragging the centre, which a mean
 * would and which is the whole reason a median is used at all.
 */
internal object CrossColumnRatioCheck {

    /** What the table's other rows say about the candidate row. */
    sealed interface Verdict {
        /**
         * The candidate row's ratio matches the table's own, established by [supportingRows] other
         * rows.
         */
        data class Consistent(
            val medianRatio: Double,
            val candidateRatio: Double,
            val supportingRows: Int,
        ) : Verdict

        /**
         * The candidate row's ratio is not the table's. Automatic advancement must not happen.
         *
         * Carries both figures so a diagnostic can state the contradiction rather than merely
         * announcing a refusal.
         */
        data class Conflicting(
            val medianRatio: Double,
            val candidateRatio: Double,
            val supportingRows: Int,
        ) : Verdict

        /**
         * Fewer than [MIN_SUPPORTING_ROWS] coherent pairs, or no candidate pair. **Not a failure and
         * not a conflict** — the table simply cannot answer the question.
         */
        data class NotEnoughEvidence(val coherentRows: Int) : Verdict
    }

    /** One row's pair of figures from the two established columns. */
    private data class RowPair(val perHundred: BigDecimal, val perServing: BigDecimal) {
        /** Serving over per-100. Null when either side is zero or negative. */
        val ratio: Double?
            get() {
                if (perHundred.signum() <= 0 || perServing.signum() <= 0) return null
                val value = perServing.toDouble() / perHundred.toDouble()
                return value.takeIf { it.isFinite() && it > 0.0 }
            }
    }

    /**
     * Whether [candidate]'s row is consistent with the rest of [document]'s table.
     *
     * [candidate] is the accepted reading being checked. Its own row is excluded from the supporting
     * evidence — a row cannot corroborate itself, the same rule that keeps two parses of one
     * recognition from counting as two opinions.
     */
    fun check(document: OcrDocument, candidate: CarbCandidate): Verdict {
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        // The per-100 column, collapsed the same way the serving column below it already was.
        //
        // A multilingual package prints one column header once per language, and each recognised
        // phrase emits its own column: the Turkish rice-flour box states `100 g.`, `For 100 g.`,
        // `Für 100 g.`, `Pour 100 g.`, `Voor 100g.` and `Pr 100 g.`, giving six `PER_100_G` columns
        // spanning 91 px of a 1684 px frame. `singleOrNull` refused the check on precisely the
        // labels it is needed for.
        //
        // The bound is what keeps this safe, and it has a measured counter-example:
        // `docs/Scan Evidence 3rd testr/20260904-134552-198` photographs an Indomie packet printing
        // **two separate per-100 g tables**, one for the noodles (27 g) and one for the bouillon
        // (2,7 g). Those columns sit 443 px apart — 26% of the width — so they are not collapsed,
        // and the check declines rather than inventing a table that is not printed.
        val perHundredColumn = singlePrintedColumn(
            columns.filter {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            },
            document,
        ) ?: return Verdict.NotEnoughEvidence(0)

        // A multilingual header states "portion" and "portie/" on two recognised rows, so the same
        // printed column is emitted twice a few pixels apart. Requiring exactly one would refuse the
        // check on precisely the labels it is needed for. They are collapsed when they agree on
        // position — which is what makes them one printed column — and a genuine disagreement (two
        // serving columns far apart) still yields no verification.
        //
        // When the label states no serving column at all, a position whose meaning was never
        // established may stand in — see [offBasisDenominator] for why that is sound here and
        // nowhere else.
        val servingColumn = singlePrintedColumn(
            columns.filter { it.kind == NutritionColumnKind.PER_SERVING },
            document,
        ) ?: offBasisDenominator(columns, perHundredColumn, document)
        ?: return Verdict.NotEnoughEvidence(0)

        val candidateRow = rows.firstOrNull { row ->
            row.box.verticalOverlapRatio(candidate.geometry) > ROW_MATCH_OVERLAP
        }

        val supporting = rows
            .filter { it !== candidateRow }
            .filter { RowClassifier.classify(it) != NutritionRowKind.HEADER }
            .mapNotNull { pairOn(it, perHundredColumn, servingColumn, document) }
            .mapNotNull { it.ratio }

        if (supporting.size < MIN_SUPPORTING_ROWS) return Verdict.NotEnoughEvidence(supporting.size)

        // Log space: a ratio is multiplicative, so `half` and `double` must be equally far out.
        val logs = supporting.map { ln(it) }.sorted()
        val medianLog = logs[logs.size / 2]
        val coherent = logs.count { abs(it - medianLog) <= ln(1.0 + SUPPORT_TOLERANCE) }
        if (coherent < MIN_SUPPORTING_ROWS) return Verdict.NotEnoughEvidence(coherent)

        val median = exp(medianLog)
        val candidatePair = candidateRow
            ?.let { pairOn(it, perHundredColumn, servingColumn, document) }
            ?: return Verdict.NotEnoughEvidence(coherent)
        val candidateRatio = candidatePair.ratio ?: return Verdict.NotEnoughEvidence(coherent)

        // The candidate is compared against the per-100 value the *reading* accepted, not against
        // whatever the row's per-100 cell happens to hold — those are the same on an ordinary table
        // and can differ on a damaged one, and it is the accepted value that is being verified.
        val ratioOfAcceptedValue = candidatePair.perServing.toDouble()
            .takeIf { candidate.value.signum() > 0 }
            ?.div(candidate.value.toDouble())
            ?: return Verdict.NotEnoughEvidence(coherent)

        val within = abs(ln(ratioOfAcceptedValue) - medianLog) <= ln(1.0 + CANDIDATE_TOLERANCE)
        return if (within) {
            Verdict.Consistent(median, ratioOfAcceptedValue, coherent)
        } else {
            Verdict.Conflicting(median, ratioOfAcceptedValue, coherent)
        }
    }

    /**
     * The one printed column [candidates] describe, or null when they describe none or several.
     *
     * Columns of one kind that sit within [SAME_COLUMN_FRACTION] of the frame width are one printed
     * column read once per language; columns further apart are different columns, and a check that
     * merged them would be reasoning about a table the package does not print.
     */
    private fun singlePrintedColumn(
        candidates: List<NutritionColumn>,
        document: OcrDocument,
    ): NutritionColumn? {
        if (candidates.isEmpty()) return null
        val centres = candidates.map { it.centerX }
        if (centres.max() - centres.min() > document.width * SAME_COLUMN_FRACTION) return null
        return candidates.first()
    }

    /**
     * A second value column whose *meaning* was never established, usable here and nowhere else.
     *
     * ## What `UNKNOWN` means, and why it is not an objection to a ratio
     *
     * [NutritionColumnKind.UNKNOWN] marks a position the label prints values under whose meaning the
     * app could not establish — `Ø/125 g`, `Ø/9 g`, `250 ml`. There is no
     * [app.justthecarbs.domain.NutritionBasis] member for any of those, so **reading a value from
     * such a column is refused everywhere in this app, and that refusal is unchanged.** It is the
     * rule that closed the 2.6x error.
     *
     * It is not an objection to using the column as a *denominator*. This class computes
     * `second / perHundred` for each row, compares those ratios against each other, and discards
     * them. The quantity is dimensionless, it never leaves this file, no basis is inferred from it,
     * no value is read out of the column, and nothing derived from it is ever displayed. What is
     * being asked is only *does this row behave like every other row of the same table?* — and the
     * answer to that does not depend on knowing what the second column means.
     *
     * ## The labels this exists for
     *
     * `docs/Scan Evidence 3rd testr/20260904-134233-470` prints `Ø/100 g` and `Ø/125 g`, and states
     * the serving ratio on three independent rows — energie 1.2505, vetten 1.2500, koolhydraten
     * 1.2500. `-134917-744` prints `Ø/100 g` and `Ø/9 g` and states it on three more — 0.0887,
     * 0.0899, 0.0912. Both were read correctly and both were made to ask the user, because their
     * second column has no name this app recognises.
     *
     * ## The bounds
     *
     * - Consulted **only** when the label resolved no `PER_SERVING` column, so an established
     *   meaning always wins over an unestablished position.
     * - [NutritionColumnKind.REFERENCE_PERCENT] is never eligible. A `%RI` cell is a fraction of a
     *   reference intake, not the nutrient restated, so it would compare a mass against a
     *   percentage and move the median every other judgement rests on. Only `UNKNOWN` qualifies.
     * - It must be a single printed column, by the same distance rule as every other column here.
     * - It must not be the per-100 column itself.
     */
    private fun offBasisDenominator(
        columns: List<NutritionColumn>,
        perHundred: NutritionColumn,
        document: OcrDocument,
    ): NutritionColumn? = singlePrintedColumn(
        columns.filter { it.kind == NutritionColumnKind.UNKNOWN && it !== perHundred },
        document,
    )

    /**
     * The two figures [row] prints under the two columns, or null when it does not print both.
     *
     * Percentages, reference-intake cells and unit-less debris are excluded here rather than by the
     * caller, because a row contributing a junk pair is worse than a row contributing nothing: it
     * moves the median that every other judgement rests on.
     */
    private fun pairOn(
        row: LogicalRow,
        perHundred: NutritionColumn,
        serving: NutritionColumn,
        document: OcrDocument,
    ): RowPair? {
        val percentIndices = PercentAssociation.percentElementIndices(row, document.width)
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            document.width * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )

        var hundredValue: BigDecimal? = null
        var servingValue: BigDecimal? = null

        row.elements.forEachIndexed { index, element ->
            if (index in percentIndices) return@forEachIndexed
            val value = numericValue(element.text) ?: return@forEachIndexed
            val centre = element.box.centerX
            val toHundred = abs(perHundred.centerX - centre)
            val toServing = abs(serving.centerX - centre)

            // Nearest wins, and only within the ordinary column tolerance. A cell aligned to neither
            // column belongs to neither and is dropped rather than being forced onto the closer one.
            if (toHundred <= toServing && toHundred <= loose) {
                if (hundredValue == null) hundredValue = value
            } else if (toServing < toHundred && toServing <= loose) {
                if (servingValue == null) servingValue = value
            }
        }

        val h = hundredValue ?: return null
        val s = servingValue ?: return null
        return RowPair(h, s)
    }

    /**
     * The number [text] states, or null when it states none.
     *
     * A trailing unit is allowed and ignored — `3,4g` and `11,0 g` are the ordinary printed forms —
     * but a token carrying a `%`, or one whose unit is an energy unit, is refused: the energy row is
     * usable only when both cells are the same unit, and a row printing `1820 kJ432 kcal` beside
     * `569kJ/135 kcal` states four numbers in two units, which is not a pair.
     */
    private fun numericValue(text: String): BigDecimal? {
        val normalized = text.trim()
        if (normalized.contains('%')) return null
        val match = NUMBER_WITH_OPTIONAL_UNIT.matchEntire(normalized) ?: return null
        val unit = match.groupValues[2].lowercase().trimEnd('.', ',')
        if (unit in ENERGY_UNITS) return null
        return runCatching { BigDecimal(match.groupValues[1].replace(',', '.')) }
            .getOrNull()
            ?.takeIf { it.signum() >= 0 }
    }

    /**
     * A number, optionally followed by a short unit, with the punctuation OCR sprays between them.
     *
     * `72,0.g`, `11,0`, `3,4g` and `2,8` are all cells on the real cracker captures — the unit is
     * fused to some and a separate element beside others, and `72,0.g` carries a stray full stop
     * where the space was. All of them must yield their number, because the ratio check needs the
     * *magnitude*, not a verdict on how cleanly the unit was printed. That verdict is
     * [CarbUnitAccompaniment]'s job on the answer path, and duplicating it here would make a damaged
     * unit glyph silently remove a supporting row.
     *
     * Still deliberately strict about what it will not read: `0,38g67` (the misread cracker's salt
     * cell fused with the next column's percentage) and `1120.g` match nothing and contribute no
     * pair. A permissive pattern would read the first as `0.38` and add a junk ratio to the median
     * that every other judgement rests on.
     */
    private val NUMBER_WITH_OPTIONAL_UNIT =
        Regex("""(\d{1,4}(?:[.,]\d{1,3})?)[.,]?\s*([a-zA-Z]{0,4}\.?)""")

    private val ENERGY_UNITS = setOf("kj", "kcal", "cal", "j")

    /**
     * How many other rows must agree before the table is considered to state a ratio at all.
     *
     * Three. Two rows agreeing is a coincidence available on almost any table — including one where
     * both were misread the same way — and requiring four costs the check on short panels that
     * legitimately print only energy, fat, carbohydrate and protein.
     */
    internal const val MIN_SUPPORTING_ROWS = 3

    /**
     * How far a supporting row may sit from the median and still be counted as agreeing.
     *
     * ±20%. Wide enough to absorb per-cell rounding on small figures — `0,3` against `1,1` is 0.273
     * where the true serving fraction is 0.3125, a 13% deviation produced entirely by rounding two
     * cells to one decimal place — and narrow enough that a row misread by a factor of ten or a
     * digit substitution cannot be counted as support.
     */
    internal const val SUPPORT_TOLERANCE = 0.20

    /**
     * How far the candidate's own ratio may sit from the established median.
     *
     * ±25%, deliberately looser than [SUPPORT_TOLERANCE]. The support threshold decides what the
     * table's ratio *is*, and there a tight bound is protective; the candidate threshold decides
     * whether a correct reading is *rejected*, and there a tight bound costs correct scans. The
     * failure it must catch is not marginal: 1.875 against 0.31 is six times out.
     */
    internal const val CANDIDATE_TOLERANCE = 0.25

    /** Vertical overlap at which a reconstructed row is taken to be the candidate's own row. */
    private const val ROW_MATCH_OVERLAP = 0.5

    /**
     * How close two columns of the same kind must sit to be the same printed column.
     *
     * A multilingual header prints its serving word on several recognised rows, each emitting a
     * column a few pixels apart. 3% of the frame width is far tighter than the ordinary column
     * tolerance and comfortably wider than that jitter.
     */
    private const val SAME_COLUMN_FRACTION = 0.03
}
