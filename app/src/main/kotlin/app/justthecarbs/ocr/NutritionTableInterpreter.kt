package app.justthecarbs.ocr

import app.justthecarbs.domain.AmountWithBasis
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.PortionParser
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

        // Classified before the no-total-row exit, not after: the prose fallback's eligibility
        // predicate needs to see whether the document resolved any basis column at all, and that exit
        // is one of the two places it can be reached from. One call site, hoisted — not duplicated.
        val columns = ColumnClassifier.classify(rows, document.width)
        columns.forEach {
            diagnostics += OcrDiagnostic("column", "${it.kind.name} '${it.headerText}' @ x=${it.centerX}")
        }

        val totalRows = typed.filter { it.second == NutritionRowKind.TOTAL_CARBOHYDRATE }.map { it.first }
        if (totalRows.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "No total-carbohydrate row")
            proseFallback(rows, columns, document.width, diagnostics)?.let { return it }
            return NutritionParseReport(LabelReading.NotFound, diagnostics, null)
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

            val anchors = CarbohydrateTermAnchor.nutrientAnchors(totalRow)
            if (anchors.any { !it.isCarbohydrate }) {
                diagnostics += OcrDiagnostic(
                    "nutrient-anchors",
                    anchors.joinToString(", ") { "${if (it.isCarbohydrate) "carb" else "other"}@${it.left}" },
                )
            }

            numbersIn(totalRow, document.width, inlineBases, anchors).forEach { cell ->
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

        // Normalized before deduplication so "45" and "45.0" printed on two rows are one
        // interpretation rather than a fabricated disagreement.
        val distinct = perHundred.distinctBy { it.first.stripTrailingZeros() to it.second }

        val servingCandidate = serving?.let { value ->
            val descriptor = servingColumn?.headerText?.let(::descriptorFromHeader)
            val resolved = descriptor?.let {
                // A serving weight printed on its own line under the column header ("per piece" /
                // "(12.5 g)") is adopted only when the table's own arithmetic reproduces the printed
                // per-serving figure from it — see ServingWeightAssociator.agreesWithTable. Without
                // that corroboration the weight is left off entirely and the direct-carbs path is
                // used, which is the existing, honest fallback (spec §17).
                withPrintedWeight(it, servingColumn, rows, document, distinct, value, diagnostics)
            }
            // Rejected means the header's own stated weight contradicts the table. The per-serving
            // figure came out of that same column, so it is not salvaged.
            if (resolved is PrintedWeightResult.Rejected) return@let null
            ServingCarbCandidate(
                carbsPerServing = value,
                descriptor = (resolved as? PrintedWeightResult.Accepted)?.descriptor,
                rawHeaderText = servingColumn?.headerText.orEmpty(),
            )
        }

        var provenance: CandidateProvenance? = null

        val reading = when {
            distinct.isEmpty() -> {
                diagnostics += OcrDiagnostic("result", "Total-carbohydrate row found but no usable per-100 cell")
                proseFallback(rows, columns, document.width, diagnostics)?.let { return it }
                LabelReading.NotFound
            }
            distinct.size == 1 -> {
                val (value, basis) = distinct.single()
                diagnostics += OcrDiagnostic("selected", "${value.toPlainString()} ${basis.name}")
                val row = rowFor(value, basis, contributingRows, totalRows)
                provenance = CandidateProvenance.FromRow(row.text, row.box)
                LabelReading.Confident(candidate(row, value, basis))
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

        return NutritionParseReport(reading, diagnostics, servingCandidate, provenance)
    }

    /**
     * The prose fallback, behind its two independent gates.
     *
     * Reached only from a tabular `NotFound` — never from `Confident` and never from `Ambiguous`. An
     * ambiguity means the table stage found competing legitimate interpretations, and resolving that
     * competition with a different stage's answer would be exactly the confident-wrong-answer the
     * architecture refuses; it is surfaced unchanged.
     */
    private fun proseFallback(
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        documentWidth: Int,
        diagnostics: MutableList<OcrDiagnostic>,
    ): NutritionParseReport? {
        // The predicate needs the columns the table stage already resolved, and the width its own
        // cell-alignment check is scaled by: a document with a real, structurally participating basis
        // column is not a prose label however badly the rest of the table read.
        if (!ProseNutritionReader.isProseLabel(rows, columns, documentWidth)) {
            diagnostics += OcrDiagnostic("prose", "not a prose label; no fallback")
            return null
        }
        val result = ProseNutritionReader.read(rows)
        if (result.reading == LabelReading.NotFound) {
            diagnostics += OcrDiagnostic("prose", "prose label, but no bindable total with a governing basis")
            return null
        }
        diagnostics += OcrDiagnostic("prose", "read from a prose declaration")
        // Per-100 only: a prose label never yields a serving candidate (spec, rule 7).
        return NutritionParseReport(result.reading, diagnostics, null, result.provenance)
    }

    /**
     * The outcome of attaching a printed serving weight to [ServingDescriptor].
     *
     * [Rejected] exists because a header that states its own weight is making a claim about what the
     * column IS. When the table's arithmetic refuses that claim, the header and the column disagree,
     * and no part of the serving reading survives — including the per-serving carbohydrate figure,
     * which was read out of that same column. A geometric weight (found on its own line) failing the
     * same check only invalidates the parser's own inference, so it degrades to [Accepted] with the
     * weight left off, which is the pre-existing direct-carbs fallback.
     */
    private sealed interface PrintedWeightResult {
        data class Accepted(val descriptor: ServingDescriptor) : PrintedWeightResult
        data object Rejected : PrintedWeightResult
    }

    /**
     * [descriptor] with the label's printed serving weight attached, when one is both found and
     * corroborated. Returns the descriptor unchanged when no weight is found — never a guessed weight.
     *
     * A descriptor that already carries a weight (the header itself said "per 2 slices (70 g)") is a
     * different kind of claim: the header is the text asserting what this column IS, so its stated
     * weight is still checked against the table's own arithmetic rather than trusted outright — see
     * [PrintedWeightResult.Rejected].
     */
    private fun withPrintedWeight(
        descriptor: ServingDescriptor,
        servingColumn: NutritionColumn?,
        rows: List<LogicalRow>,
        document: OcrDocument,
        perHundred: List<Pair<BigDecimal, NutritionBasis>>,
        carbsPerServing: BigDecimal,
        diagnostics: MutableList<OcrDiagnostic>,
    ): PrintedWeightResult {
        // A weight the header itself states is still checked against the table's own arithmetic —
        // the same rule, and the same function, used for a weight printed on its own line. The
        // acquisition site differs; the evidence standard must not. Writing a second tolerance here
        // is how the two drift apart. What differs is the CONSEQUENCE: a refused header weight
        // discredits the column the header names, so the whole candidate goes.
        descriptor.weightOrVolume?.let { stated ->
            val reference = perHundred.singleOrNull() ?: return PrintedWeightResult.Accepted(descriptor)
            if (!ServingWeightAssociator.agreesWithTable(reference.first, stated.amount, carbsPerServing)) {
                diagnostics += OcrDiagnostic(
                    "serving-weight",
                    "header weight ${stated.amount.toPlainString()} g rejected: " +
                        "${reference.first.toPlainString()}/100 does not give ${carbsPerServing.toPlainString()}" +
                        " — dropping the serving candidate",
                )
                return PrintedWeightResult.Rejected
            }
            return PrintedWeightResult.Accepted(descriptor)
        }
        val column = servingColumn ?: return PrintedWeightResult.Accepted(descriptor)

        val medianHeight = document.elements.map { it.box.height }.sorted()
            .let { if (it.isEmpty()) 1 else it[it.size / 2] }
        val weight = ServingWeightAssociator.find(rows, column, document.width, medianHeight)
            ?: return PrintedWeightResult.Accepted(descriptor)

        // One unambiguous per-100 figure is required to check against. With none, or with the table
        // still ambiguous, there is nothing to corroborate the weight with and it is not adopted.
        val reference = perHundred.singleOrNull()
        if (reference == null) {
            diagnostics += OcrDiagnostic(
                "serving-weight",
                "${weight.amount.toPlainString()} ${weight.basis.name} not adopted: no single per-100 figure",
            )
            return PrintedWeightResult.Accepted(descriptor)
        }

        // The printed weight is the weight of the WHOLE serving, which is exactly what
        // ServingDescriptor.weightOrVolume means — "per 2 pieces (25 g)" is 25 g of serving, and
        // ServingDescriptor.amountPerUnit divides by the count to reach 12.5 g per piece. Scaling it
        // by the count here would double it, and the error is invisible on the common count-of-one
        // label, which is the only kind the Kinder fixture has.
        if (!ServingWeightAssociator.agreesWithTable(reference.first, weight.amount, carbsPerServing)) {
            diagnostics += OcrDiagnostic(
                "serving-weight",
                "${weight.amount.toPlainString()} g rejected: " +
                    "${reference.first.toPlainString()}/100 does not give ${carbsPerServing.toPlainString()}",
            )
            return PrintedWeightResult.Accepted(descriptor)
        }

        diagnostics += OcrDiagnostic(
            "serving-weight",
            "${weight.amount.toPlainString()} ${weight.basis.name} corroborated by the per-100 column",
        )
        return PrintedWeightResult.Accepted(descriptor.copy(weightOrVolume = AmountWithBasis(weight.amount, weight.basis)))
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
        // Normalized first, which the previous `removePrefix("per")` could not substitute for.
        // On the real Kinder package the matched header span is "/ Par pièce": a leading slash from
        // the language separator, a connective that is not the English "per", and an accent that
        // ServingSizeParser's unit table does not carry. Each of those alone defeated the parse, and
        // the per-piece portion was silently dropped from a label that prints it plainly.
        val normalized = NutritionTerminology.normalize(headerText)
        val words = normalized.split(' ').filter { it.isNotBlank() }
        val phrase = words.drop(if (words.firstOrNull() in NutritionTerminology.connectives) 1 else 0)
            .joinToString(" ")
        if (phrase.isEmpty()) return null

        val descriptor = ServingSizeParser.parseDescriptor(phrase) ?: headerWeightDescriptor(phrase)
        return descriptor
            // "per serving"/"per portion" parse to a SERVING descriptor, but they name no countable
            // thing the user would recognise as an item, so they carry no descriptor.
            ?.takeIf { !isGenericServingWord(phrase) }
    }

    /**
     * A serving header naming its own weight with no leading count — "portie 50 g", "schaaltje
     * (150 g)" — falls outside [ServingSizeParser.parseDescriptor]'s contract: that function treats a
     * weight with no leading count as ambiguous ("portion 25 g" could mean anything when read as a
     * standalone `serving_size` string with no fixed relationship to the column it came from).
     *
     * A table column header carries no such ambiguity. The header IS the definition of what one cell
     * of this column means, so a stated weight with no count prefix names the weight of that one
     * serving — the general rule being that a serving header may state its own weight, and when it
     * does, that weight belongs to the serving it names.
     */
    private fun headerWeightDescriptor(phrase: String): ServingDescriptor? {
        val match = HEADER_WEIGHT.find(phrase) ?: return null
        val word = match.groupValues[1]
        val weightText = match.groupValues[2]
        val unit = match.groupValues[3]
        val kind = ServingSizeParser.kindForWord(word) ?: return null
        val weight = PortionParser.parse(weightText) ?: return null
        if (weight.signum() <= 0) return null
        val basis = if (unit.equals("ml", ignoreCase = true)) NutritionBasis.PER_100_ML else NutritionBasis.PER_100_G
        return ServingDescriptor(
            kind = kind,
            count = BigDecimal.ONE,
            weightOrVolume = AmountWithBasis(weight, basis),
            rawText = phrase,
        )
    }

    /** A unit word directly followed by a weight, with no leading count: "portie 50 g", "stuk (25 g)". */
    private val HEADER_WEIGHT =
        Regex("""^(\p{L}+)\s*[(,=]?\s*(\d+(?:[.,]\d+)?)\s*(g|ml)\)?\s*$""", RegexOption.IGNORE_CASE)

    private fun isGenericServingWord(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text)
        return normalized in GENERIC_SERVING_WORDS
    }

    /**
     * Serving words naming no countable thing a user would recognise as an item.
     *
     * "schaaltje" (a small dish or bowl) belongs here for the same reason "portie" does: it names the
     * vessel a serving is presented in, so "3 schaaltjes" is not a portion the user counts off a
     * package the way "3 slices" is.
     */
    private val GENERIC_SERVING_WORDS = setOf("serving", "portion", "portie", "schaaltje")

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

    /**
     * Nearest column within the loose fraction; null when the cell aligns to nothing.
     *
     * A column carrying a [NutritionColumn.verticalExtent] is only considered for cells inside that
     * band — see the field's own documentation for why a column recovered from cell shape is
     * evidence about its own rows and not about the whole frame.
     */
    private fun columnFor(cell: NumberCell, columns: List<NutritionColumn>, documentWidth: Int): NutritionColumn? {
        if (columns.isEmpty()) return null
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )
        return columns
            .filter { it.verticalExtent?.let { band -> cell.box.centerY.toInt() in band } ?: true }
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
     * - **Numbers a different nutrient named on the same row already claimed**, per [anchors]. See
     *   [CarbohydrateTermAnchor] for why reading order settles this and a distance cannot.
     */
    private fun numbersIn(
        row: LogicalRow,
        documentWidth: Int,
        inlineBases: List<InlineBasisSpans.Span>,
        anchors: List<CarbohydrateTermAnchor.Anchor> = emptyList(),
    ): List<NumberCell> = buildList {
        val percentIndices = PercentAssociation.percentElementIndices(row, documentWidth)
        val basisIndices = inlineBases.flatMap { it.elementIndices }.toSet()
        val fragmentPartners = decimalFragmentPartners(row)

        row.elements.forEachIndexed { index, element ->
            if (index in percentIndices) return@forEachIndexed
            if (index in basisIndices) return@forEachIndexed
            // Consumed as the tail of "61," + "9" by the element before it.
            if (index in fragmentPartners.values) return@forEachIndexed
            // Claimed by a different nutrient named to its left on this same row.
            if (!CarbohydrateTermAnchor.isCarbohydrateValue(anchors, element.box.right)) {
                return@forEachIndexed
            }

            val text = fragmentPartners[index]
                ?.let { tail -> element.text + row.elements[tail].text }
                ?: element.text
            val box = fragmentPartners[index]
                ?.let { tail -> element.box.union(row.elements[tail].box) }
                ?: element.box

            NUMBER.findAll(text).forEach { match ->
                if (!isStandaloneNumber(text, match)) return@forEach
                val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
                    ?: return@forEach
                add(NumberCell(value, box))
            }
        }
    }

    /**
     * Whether a matched number is a table cell rather than digits embedded in a word.
     *
     * Found by running the real Kinder package through ML Kit. It read Slovenian "**O**gljikovi" as
     * "**0**gjikovi", and that leading zero was a perfectly well-formed number: it passed the
     * per-100 validator (0 g of carbohydrate is a legitimate figure), became a second distinct
     * reading, and turned a correct confident 53.5 into an ambiguity between 53.5 and 0. A user
     * would have been asked to choose between the right answer and a misread letter.
     *
     * Letter-to-digit confusion is ordinary OCR behaviour on packaging — O/0, l/1, S/5 — so this is
     * not specific to one label. A value cell is a number standing on its own, optionally carrying a
     * unit: "53,5", "53,5 g", "0,313". It is never digits welded between letters.
     *
     * The trailing unit must match **exactly**, which is the whole difficulty: testing that the
     * suffix merely *starts* with "g" would accept "0gjikovi" and change nothing.
     */
    private fun isStandaloneNumber(text: String, match: MatchResult): Boolean {
        text.getOrNull(match.range.first - 1)?.let { if (it.isLetter()) return false }
        val after = text.substring(match.range.last + 1)
        if (after.isEmpty() || !after.first().isLetter()) return true
        return after.trimEnd { !it.isLetter() }.lowercase() in TRAILING_UNITS
    }

    /** Units a printed figure may be fused to, e.g. "53,5g". */
    private val TRAILING_UNITS = setOf("g", "mg", "ml", "l", "kj", "kcal", "mcg")

    /**
     * Element pairs that are one printed number split across a decimal separator, as
     * `index of head -> index of tail`.
     *
     * European labels print `61,9`, and OCR does not always keep it whole: a comma sits low and
     * thin, and a fragmented recognition can end one element on the separator and start the next
     * with the fractional digits. Rejoining them matters because the alternative is not a harmless
     * miss — `61` and `9` both clear the per-100 validator, so the label would be reported as
     * *ambiguous between 61 and 9* and the user asked to choose between a right answer and a
     * meaningless one (§15).
     *
     * Deliberately narrow. The head must **end** on the separator, which is what marks its number as
     * incomplete; the tail must be nothing but digits; and they must be horizontally adjacent, so
     * two numbers in different columns are never joined.
     */
    private fun decimalFragmentPartners(row: LogicalRow): Map<Int, Int> = buildMap {
        row.elements.forEachIndexed { index, element ->
            if (!TRAILING_SEPARATOR.containsMatchIn(element.text)) return@forEachIndexed
            val next = row.elements.getOrNull(index + 1) ?: return@forEachIndexed
            if (!FRACTIONAL_TAIL.matches(next.text)) return@forEachIndexed
            val gap = next.box.left - element.box.right
            if (gap < 0 || gap > element.box.height * MAX_FRAGMENT_GAP_IN_HEIGHTS) return@forEachIndexed
            put(index, index + 1)
        }
    }

    /** An element whose number was cut off mid-decimal: "61," or "0.". */
    private val TRAILING_SEPARATOR = Regex("\\d[.,]$")

    /** The other half: fractional digits and nothing else. */
    private val FRACTIONAL_TAIL = Regex("^\\d{1,3}$")

    /** Two halves of one number sit within about half a character of each other. */
    private const val MAX_FRAGMENT_GAP_IN_HEIGHTS = 0.5

    private data class NumberCell(val value: BigDecimal, val box: OcrBox)
}
