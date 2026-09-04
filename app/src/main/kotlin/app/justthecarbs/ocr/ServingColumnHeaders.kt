package app.justthecarbs.ocr

import app.justthecarbs.domain.BasisUnitSpellings
import kotlin.math.abs

/**
 * Recovers a serving column from the **serving size the label printed in its header**, including
 * when that size is printed on the line below the header it belongs to.
 *
 * ## The measured failure
 *
 * Two captures from the twelfth phone session, on two different packages, print a two-column table
 * and resolve **one** column:
 *
 * ```
 * 20260903-212442-653   HEADER  'Voedingswaarde per 100 g 4 crackers'   y=1198..1277
 *                       OTHER   '(25 g)'                                y=1280..1350
 *                       -> column PER_100_G @ x=1067          (the serving column is missing)
 *
 * 20260903-212700-478   HEADER  'gamcht 360 g/ per 100 g part'          y=1258..1414
 *                       OTHER   '2parten (30g) (30 g)'                  y=1350..1500
 *                       -> column PER_100_G @ x=1092          (the serving column is missing)
 * ```
 *
 * The reason is the same on both: the header's semantic unit is **split across two recognised
 * rows**. `4 crackers` and `part` sit on the header row; the size that gives each its meaning —
 * `(25 g)`, `(30 g)` — is printed underneath and reconstructs as a row of its own.
 * [ColumnClassifier]'s span walk only ever looks along one row, so it sees a noun it has no
 * vocabulary for and moves on.
 *
 * What that cost is not cosmetic:
 *
 * - On the cracker, the only carbohydrate figure OCR recovered was the serving cell `18 g`. With no
 *   column to claim it, it stated no basis, recovery suppressed it, and the capture was a dead end
 *   — for a label whose printed `72 g / 100 g` is exactly `18 / 25 × 100`.
 * - On the pickle, the serving cell `1,6` had no column of its own and reached across 243 px to the
 *   per-100 column, which reported it as `Confident 1.6/PER_100_G`. [ColumnOwnership] now refuses
 *   that independently; giving the cell **its own** column is the other half, and the half that
 *   turns a refusal into an answer the user can use.
 *
 * ## The rule, and why it is not a vocabulary
 *
 * > A **parenthesised quantity-and-unit** printed in the table's header band, with value cells
 * > aligned beneath it, is that column's serving size.
 *
 * A bracketed `(25 g)` is not a noun this parser has to know. It is a printed quantity, in the one
 * place a printed quantity means "this column is per *this much*". Reading it needs no list of
 * words, so it works on `4 crackers (25 g)`, on `part (30 g)`, on `schaaltje (150 g)` and on the
 * multilingual equivalents alike — none of which share a vocabulary.
 *
 * Deliberately **not** done here: recognising `4 crackers` or `part` as serving nouns. Adding those
 * would be the per-label heuristic this repo keeps refusing, and it is unnecessary — the quantity
 * is what the basis is made of, and the noun contributes nothing the conversion needs.
 *
 * ## Why this cannot produce a wrong carbohydrate value
 *
 * A [NutritionColumnKind.PER_SERVING] column can never supply the per-100 figure the calculator
 * scales from: `CarbCandidate`'s own `init` makes such a candidate unconstructible. So the only
 * things this can do are **remove** a per-100 claim from a cell that was never per-100, and **add**
 * a serving reading that carries its own printed quantity and is normalized rather than relabelled.
 * Both are safety-positive. Where no bracketed quantity is printed, or where nothing is aligned
 * beneath it, nothing is emitted and behaviour is exactly as before.
 *
 * The three guards that make "printed in the header band" mean something:
 *
 * 1. **Position** — the token is on a `HEADER` row, or on a row directly continuing one (see
 *    [isContinuationOf]). A quantity in the middle of an ingredient list is not a column header.
 * 2. **Aligned cells** — at least [MIN_ALIGNED_VALUE_CELLS] value cells on non-header rows sit
 *    within the strict column tolerance of it. This is the guard that matters most in practice: the
 *    pickle prints **two** `(30 g)` tokens on the continuation row, one at x≈339 belonging to the
 *    drained-weight sentence `uitgelekt gewicht 360 g / 2 parten (30g)` and one at x≈1337 heading
 *    the table's serving column. Only the second has a column of values under it.
 * 3. **Not already a column** — a per-100, percent or serving column already covering that x
 *    position keeps it. A quantity of exactly 100 is skipped outright: that is a per-100 header, and
 *    claiming it here would be a second, worse way of resolving one.
 */
internal object ServingColumnHeaders {

    /**
     * Serving columns recovered from printed sizes in [rows]'s header band.
     *
     * [existing] is what [ColumnClassifier] already resolved, so this can decline to duplicate or
     * override a column the ordinary header vocabulary already read.
     */
    fun recover(
        rows: List<LogicalRow>,
        kinds: List<NutritionRowKind>,
        existing: List<NutritionColumn>,
        documentWidth: Int,
    ): List<NutritionColumn> {
        if (rows.isEmpty()) return emptyList()

        val headerIndices = kinds.indices.filter { kinds[it] == NutritionRowKind.HEADER }
        if (headerIndices.isEmpty()) return emptyList()

        val medianHeight = rows
            .flatMap { row -> row.elements.map { it.box.height } }
            .sorted()
            .let { it[it.size / 2] }
            .coerceAtLeast(1)

        // The rows a header's own meaning may be spread across: the header itself plus any row
        // directly continuing it. A continuation row must name no nutrient — a nutrient row printed
        // tight under a header is a value row, not more header.
        val bandIndices = headerIndices.flatMap { header ->
            val continuations = rows.indices.filter { candidate ->
                candidate != header &&
                    kinds[candidate] != NutritionRowKind.HEADER &&
                    isContinuationOf(rows[header], rows[candidate], medianHeight) &&
                    !namesANutrient(rows[candidate])
            }
            listOf(header) + continuations
        }.distinct()

        // Value cells the recovered column would head. Taken from non-header rows only, for the same
        // reason the percent fallback does: a header's own tokens are not cells.
        val valueCells = rows
            .filterIndexed { index, _ -> kinds[index] != NutritionRowKind.HEADER }
            .flatMap { ColumnOwnership.competingCells(it) }

        val strict = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.STRICT_COLUMN_FRACTION,
        )
        val near = documentWidth * NEAR_COLUMN_FRACTION

        val recovered = mutableListOf<NutritionColumn>()
        bandIndices.forEach { index ->
            bracketedQuantities(rows[index].elements).forEach { printed ->
                val centerX = printed.box.centerX
                val aligned = valueCells.count { abs(it.box.centerX - centerX) <= strict }
                if (aligned < MIN_ALIGNED_VALUE_CELLS) return@forEach
                if (existing.any { abs(it.centerX - centerX) <= near }) return@forEach
                if (recovered.any { abs(it.centerX - centerX) <= near }) return@forEach
                recovered += NutritionColumn(
                    kind = NutritionColumnKind.PER_SERVING,
                    headerBox = printed.box,
                    centerX = centerX,
                    // The quantity alone, in the shape a serving column's header is already read in
                    // ("per portie 50 g"), so the existing reader picks it up unchanged.
                    headerText = printed.headerText,
                )
            }
        }
        return recovered
    }

    /** A printed serving size, with the box the glyphs occupy. */
    private data class PrintedSize(val box: OcrBox, val headerText: String)

    /**
     * Whether [candidate] continues [header] — printed close enough beneath it to be the rest of the
     * same header.
     *
     * The gap is measured from the header's bottom and may be **negative**: on the pickle the
     * continuation row's box overlaps the header's, because the header row itself spans four
     * recognised lines of a multi-column heading. What is excluded is a row printed a whole row
     * pitch or more further down, which is a value row.
     */
    private fun isContinuationOf(header: LogicalRow, candidate: LogicalRow, medianHeight: Int): Boolean {
        if (candidate.box.top < header.box.top) return false
        return candidate.box.top - header.box.bottom <= medianHeight * MAX_CONTINUATION_GAP_IN_HEIGHTS
    }

    private fun namesANutrient(row: LogicalRow): Boolean {
        val normalized = NutritionTerminology.normalize(row.text)
        return NutritionTerminology.carbohydrateTerms.any {
            NutritionTerminology.containsTerm(normalized, it)
        } || NutritionTerminology.exclusionTerms.any {
            NutritionTerminology.containsTerm(normalized, it)
        }
    }

    /**
     * Bracketed `(<quantity> <unit>)` spans in [elements], in either tokenization ML Kit produces:
     * one element (`(30g)`) or two (`(25` + `g)`).
     *
     * At least one bracket must be present. An unbracketed `25 g` in a header is already
     * [ColumnClassifier]'s business — it is what `offBasisQuantitySpan` emits as `UNKNOWN`, and
     * claiming it here would turn a deliberately meaning-free position into a basis on the strength
     * of nothing.
     */
    private fun bracketedQuantities(elements: List<OcrElement>): List<PrintedSize> {
        val found = mutableListOf<PrintedSize>()
        var index = 0
        while (index < elements.size) {
            val single = SINGLE_ELEMENT.find(elements[index].text.trim())
            if (single != null && !isPerHundred(single.groupValues[1])) {
                found += PrintedSize(
                    elements[index].box,
                    "${single.groupValues[1]} ${single.groupValues[2]}",
                )
                index++
                continue
            }
            val next = elements.getOrNull(index + 1)
            if (next != null) {
                val pair = PAIR_HEAD.find(elements[index].text.trim())
                val tail = PAIR_TAIL.find(next.text.trim())
                if (pair != null && tail != null && !isPerHundred(pair.groupValues[1])) {
                    found += PrintedSize(
                        elements[index].box.union(next.box),
                        "${pair.groupValues[1]} ${tail.groupValues[1]}",
                    )
                    index += 2
                    continue
                }
            }
            index++
        }
        return found
    }

    /** `100` heads a per-100 column, which is [ColumnClassifier]'s job and not this one's. */
    private fun isPerHundred(quantity: String): Boolean =
        quantity.replace(',', '.').toBigDecimalOrNull()?.compareTo(java.math.BigDecimal(100)) == 0

    /**
     * `(30g)`, `(150 ml)` — quantity and unit inside one element.
     *
     * The **opening** bracket is required and the closing one is optional, because that is the half
     * OCR keeps: a size is recognised as `(30g)` or `(30g` far more often than as `30g)`. Requiring
     * the opener is what stops an ordinary value cell (`30g` in the table body) matching.
     */
    private val SINGLE_ELEMENT = Regex(
        "^\\((\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(${BasisUnitSpellings.alternation})\\)?$",
        RegexOption.IGNORE_CASE,
    )

    /** `(25` — the opening half when ML Kit split the size across two elements. */
    private val PAIR_HEAD = Regex("^\\((\\d{1,4}(?:[.,]\\d{1,2})?)$")

    /** `g)` — the closing half. The bracket is required, or an ordinary unit cell would match. */
    private val PAIR_TAIL = Regex("^(${BasisUnitSpellings.alternation})\\)$", RegexOption.IGNORE_CASE)

    private const val MIN_ALIGNED_VALUE_CELLS = 3
    private const val MAX_CONTINUATION_GAP_IN_HEIGHTS = 1.0
    private const val NEAR_COLUMN_FRACTION = 0.08
}
