package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.ServingDescriptor
import app.justthecarbs.domain.ServingSizeParser
import java.math.BigDecimal
import kotlin.math.abs

/**
 * A per-serving carbohydrate figure read from a serving column (spec §5).
 *
 * [descriptor] is typed at parse time so no later stage re-parses [rawHeaderText]: the save flow
 * reads `descriptor.count` directly. Null when the column is a valid per-serving column that names
 * no countable unit ("per serving"), which is a real and common shape — the figure is still useful,
 * there is just nothing countable to attach it to.
 */
data class ServingCarbCandidate(
    val carbsPerServing: BigDecimal,
    val descriptor: ServingDescriptor?,
    val rawHeaderText: String,
)

/**
 * Reads a nutrition table by reconstructing it, rather than by scoring proximity.
 *
 * The pipeline is: geometry-first rows ([LogicalRowBuilder]) -> row types ([RowClassifier]) ->
 * columns ([ColumnClassifier]) -> cells associated to columns. A child nutrient is eliminated at the
 * row-typing stage, before any number is looked at, so it cannot appear even in an ambiguity set.
 * A cell in a percent or unknown column is refused outright.
 */
object NutritionTableInterpreter {

    private val NUMBER = Regex("(?<!\\d)(\\d{1,3}(?:[.,]\\d{1,3})?)(?!\\d)")

    fun interpret(document: OcrDocument): NutritionParseReport {
        val diagnostics = mutableListOf<OcrDiagnostic>()
        val rows = LogicalRowBuilder.build(document)
        rows.forEach { diagnostics += OcrDiagnostic("row", "${it.text} @ ${it.box}") }

        val typed = rows.map { it to RowClassifier.classify(it) }
        typed.forEach { (row, kind) -> diagnostics += OcrDiagnostic("row-kind", "${kind.name}: ${row.text}") }

        val totalRows = typed.filter { it.second == NutritionRowKind.TOTAL_CARBOHYDRATE }.map { it.first }
        if (totalRows.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "No total-carbohydrate row")
            return NutritionParseReport(LabelReading.NotFound, diagnostics, null)
        }

        val columns = ColumnClassifier.classify(rows, document.width)
        columns.forEach {
            diagnostics += OcrDiagnostic("column", "${it.kind.name} '${it.headerText}' @ x=${it.centerX}")
        }

        if (totalRows.size > 1) {
            diagnostics += OcrDiagnostic("info", "${totalRows.size} total-carbohydrate rows; interpreting all")
        }

        // EVERY total row is interpreted, never just the first (correction pass §3).
        //
        // A table can genuinely print the carbohydrate line more than once — a bilingual package, a
        // repeated header block, or OCR splitting one printed row in two. Taking `first()` answered
        // that by list order, which is arbitrary: the same photograph could yield either value
        // depending on element ordering, and the user was shown the winner as a confident reading.
        // Identical interpretations collapse below; genuinely different ones become an ambiguity the
        // user resolves.
        val perHundred = mutableListOf<Pair<BigDecimal, NutritionBasis>>()
        // The first serving cell found across the total rows, kept with the row it came from so the
        // per-serving figure and its column header stay together.
        var serving: BigDecimal? = null
        var servingColumn: NutritionColumn? = null
        // Rows that contributed a per-100 reading, so a candidate quotes the line it came from.
        val contributingRows = mutableMapOf<Pair<BigDecimal, NutritionBasis>, LogicalRow>()

        totalRows.forEach { totalRow ->
            // A basis printed inside the value row itself, e.g. "Carbohydrate per 100 g 45 g", on a
            // label with no separate header row (correction pass §6).
            val inlineBases = InlineBasisSpans.find(totalRow)
            inlineBases.forEach {
                diagnostics += OcrDiagnostic("inline-basis", "${it.basis.name} in '${totalRow.text}'")
            }

            numbersIn(totalRow, document.width, inlineBases).forEach { cell ->
                val column = columnFor(cell, columns, document.width)
                // An inline basis only applies when no classified column claims the cell, so a real
                // header row always wins and this cannot quietly override a resolved table.
                val inlineBasis = inlineBases.singleOrNull()?.basis
                if (column == null && inlineBasis == null) {
                    diagnostics += OcrDiagnostic("rejected", "${cell.value.toPlainString()}: no column")
                    return@forEach
                }
                if (column == null) {
                    val validated =
                        NutritionValueValidator.validateCarbsPer100(cell.value.toDouble(), inlineBasis!!)
                    if (validated == null) {
                        diagnostics += OcrDiagnostic(
                            "rejected",
                            "${cell.value.toPlainString()}: outside the possible per-100 range",
                        )
                    } else {
                        val key = validated.stripTrailingZeros() to inlineBasis
                        perHundred += validated to inlineBasis
                        contributingRows.putIfAbsent(key, totalRow)
                    }
                    return@forEach
                }
                when (column.kind) {
                    NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_100_ML -> {
                        val basis = if (column.kind == NutritionColumnKind.PER_100_ML) {
                            NutritionBasis.PER_100_ML
                        } else {
                            NutritionBasis.PER_100_G
                        }
                        val validated =
                            NutritionValueValidator.validateCarbsPer100(cell.value.toDouble(), basis)
                        if (validated == null) {
                            diagnostics += OcrDiagnostic(
                                "rejected",
                                "${cell.value.toPlainString()}: outside the possible per-100 range",
                            )
                        } else {
                            val key = validated.stripTrailingZeros() to basis
                            perHundred += validated to basis
                            contributingRows.putIfAbsent(key, totalRow)
                        }
                    }
                    NutritionColumnKind.PER_SERVING -> {
                        if (serving == null) {
                            serving = cell.value
                            servingColumn = column
                        }
                    }
                    // Never carbohydrate grams. This is the whole reason UNKNOWN exists as a kind
                    // rather than being treated as "probably per 100 g".
                    NutritionColumnKind.REFERENCE_PERCENT, NutritionColumnKind.UNKNOWN ->
                        diagnostics += OcrDiagnostic(
                            "rejected",
                            "${cell.value.toPlainString()}: ${column.kind.name} column",
                        )
                }
            }
        }

        val servingCandidate = serving?.let { value ->
            ServingCarbCandidate(
                carbsPerServing = value,
                descriptor = servingColumn?.headerText?.let(::descriptorFromHeader),
                rawHeaderText = servingColumn?.headerText.orEmpty(),
            )
        }

        // Normalized before deduplication so "45" and "45.0" printed on two rows are one
        // interpretation rather than a fabricated disagreement.
        val distinct = perHundred.distinctBy { it.first.stripTrailingZeros() to it.second }
        val reading = when {
            distinct.isEmpty() -> {
                diagnostics += OcrDiagnostic("result", "Total-carbohydrate row found but no usable per-100 cell")
                LabelReading.NotFound
            }
            distinct.size == 1 -> {
                val (value, basis) = distinct.single()
                diagnostics += OcrDiagnostic("selected", "${value.toPlainString()} ${basis.name}")
                LabelReading.Confident(candidate(rowFor(value, basis, contributingRows, totalRows), value, basis))
            }
            else -> {
                diagnostics += OcrDiagnostic("ambiguous", "${distinct.size} distinct total-carbohydrate readings")
                LabelReading.Ambiguous(
                    distinct.map { (value, basis) ->
                        candidate(rowFor(value, basis, contributingRows, totalRows), value, basis)
                    },
                )
            }
        }

        return NutritionParseReport(reading, diagnostics, servingCandidate)
    }

    private fun rowFor(
        value: BigDecimal,
        basis: NutritionBasis,
        contributingRows: Map<Pair<BigDecimal, NutritionBasis>, LogicalRow>,
        totalRows: List<LogicalRow>,
    ): LogicalRow = contributingRows[value.stripTrailingZeros() to basis] ?: totalRows.first()

    /**
     * Turns a serving column's header into a typed descriptor.
     *
     * A count above 1 is only ever taken from an explicit leading digit in the header ("per 2
     * slices"). It is never inferred — guessing that a serving covers more than one unit would
     * silently halve or double every result computed from it.
     */
    private fun descriptorFromHeader(headerText: String): ServingDescriptor? {
        val withoutPer = headerText.trim().removePrefix("per").removePrefix("Per").trim()
        return ServingSizeParser.parseDescriptor(withoutPer)
            // "per serving"/"per portion" parse to a SERVING descriptor, but they name no countable
            // thing the user would recognise as an item, so they carry no descriptor.
            ?.takeIf { !isGenericServingWord(withoutPer) }
    }

    private fun isGenericServingWord(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text)
        return normalized == "serving" || normalized == "portion" || normalized == "portie"
    }

    private fun candidate(row: LogicalRow, value: BigDecimal, basis: NutritionBasis) = CarbCandidate(
        sourceLine = row.text,
        label = row.elements.firstOrNull()?.text.orEmpty().replaceFirstChar { it.uppercase() },
        value = value,
        basis = basis,
        score = NutritionParserThresholds.CONFIDENT_SCORE,
        geometry = row.box,
        evidence = listOf(
            CandidateEvidence("total-carbohydrate row", NutritionParserThresholds.CARBOHYDRATE_ANCHOR),
            CandidateEvidence("${basis.name} column", NutritionParserThresholds.PER_100_HEADER),
            CandidateEvidence("column-resolved cell", NutritionParserThresholds.STRONG_COLUMN_ALIGNMENT),
        ),
    )

    /** Nearest column within the loose fraction; null when the cell aligns to nothing. */
    private fun columnFor(cell: NumberCell, columns: List<NutritionColumn>, documentWidth: Int): NutritionColumn? {
        if (columns.isEmpty()) return null
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )
        return columns
            .map { it to abs(it.centerX - cell.box.centerX) }
            .filter { it.second <= loose }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * The numeric cells on [row] that are eligible to be a carbohydrate quantity.
     *
     * Two kinds of number are excluded before anything else looks at them:
     *
     * - **Percentages**, in either tokenization. [PercentAssociation] decides this at row-geometry
     *   level rather than from the element's own text, because ML Kit emits "17%" as one element on
     *   some frames and as "17" + "%" on others (correction pass §5).
     * - **Numbers belonging to an inline basis phrase**, so the "100" in "per 100 g" is never a
     *   candidate value. It would otherwise clear the per-100 validator's ceiling and be offered as
     *   a confident answer (correction pass §6).
     */
    private fun numbersIn(
        row: LogicalRow,
        documentWidth: Int,
        inlineBases: List<InlineBasisSpans.Span>,
    ): List<NumberCell> = buildList {
        val percentIndices = PercentAssociation.percentElementIndices(row, documentWidth)
        val basisIndices = inlineBases.flatMap { it.elementIndices }.toSet()

        row.elements.forEachIndexed { index, element ->
            if (index in percentIndices) return@forEachIndexed
            if (index in basisIndices) return@forEachIndexed
            NUMBER.findAll(element.text).forEach { match ->
                val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
                    ?: return@forEach
                add(NumberCell(value, element.box))
            }
        }
    }

    private data class NumberCell(val value: BigDecimal, val box: OcrBox)
}
