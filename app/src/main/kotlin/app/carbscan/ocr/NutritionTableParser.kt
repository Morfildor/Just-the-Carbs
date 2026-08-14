package app.carbscan.ocr

import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.NutritionValueValidator
import java.math.BigDecimal
import kotlin.math.abs

/** Centralized, reviewable weights and thresholds for the deterministic scoring model. */
object NutritionParserThresholds {
    const val CARBOHYDRATE_ANCHOR = 40
    const val SAME_RECOGNIZED_LINE = 25
    const val VERTICAL_OVERLAP = 20
    const val NEAR_ROW = 14
    const val PER_100_HEADER = 20
    const val STRONG_COLUMN_ALIGNMENT = 25
    const val WEAK_COLUMN_ALIGNMENT = 10
    const val GRAM_UNIT = 5

    const val TRUSTWORTHY_ROW_SCORE = 54
    const val CONFIDENT_SCORE = 105
    const val SELECTION_MARGIN = 15

    const val MIN_VERTICAL_OVERLAP = 0.35
    const val MAX_ROW_DISTANCE_IN_HEIGHT = 1.10
    const val STRICT_COLUMN_FRACTION = 0.12
    const val LOOSE_COLUMN_FRACTION = 0.22
    const val MIN_STRICT_COLUMN_PIXELS = 60.0
}

data class CandidateEvidence(val reason: String, val points: Int)

/** One total-carbohydrate interpretation with its complete explanation. */
data class CarbCandidate(
    val sourceLine: String,
    val label: String,
    val value: BigDecimal,
    /** Null means the row/value is credible but the printed per-100 basis was not established. */
    val basis: NutritionBasis?,
    val score: Int,
    val geometry: OcrBox,
    val evidence: List<CandidateEvidence>,
)

/** The three explicit OCR outcomes. Every value still requires user confirmation. */
sealed interface LabelReading {
    data class Confident(val candidate: CarbCandidate) : LabelReading {
        init {
            require(candidate.basis != null) { "A confident OCR result must have a per-100 basis" }
        }
    }
    data class Ambiguous(val candidates: List<CarbCandidate>) : LabelReading
    data object NotFound : LabelReading
}

data class OcrDiagnostic(val stage: String, val message: String)

data class NutritionParseReport(
    val reading: LabelReading,
    val diagnostics: List<OcrDiagnostic>,
)

/** Spatial nutrition-table parser. Contains no Android or ML Kit types. */
object NutritionTableParser {
    private val NUMBER = Regex("(?<!\\d)(\\d{1,3}(?:[.,]\\d{1,3})?)(?!\\d)")
    private val PER_100 = Regex("(?:^|\\s)100\\s*(g|ml)(?:$|\\s)")

    fun parse(document: OcrDocument): LabelReading = parseWithDiagnostics(document).reading

    fun parseWithDiagnostics(document: OcrDocument): NutritionParseReport {
        val diagnostics = mutableListOf<OcrDiagnostic>()
        document.elements.forEach {
            diagnostics += OcrDiagnostic("element", "${it.text} @ ${it.box}")
        }

        val lines = document.elements
            .groupBy { LineKey(it.blockId, it.lineId) }
            .map { (key, elements) -> OcrLine(key, elements.sortedBy { it.box.left }) }

        val anchors = lines.mapNotNull { line -> findAnchor(line, diagnostics) }
        if (anchors.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "No total-carbohydrate anchor")
            return NutritionParseReport(LabelReading.NotFound, diagnostics)
        }

        val headers = findHeaders(lines, diagnostics)
        val per100Headers = headers.filter { it.basis != null }
        val numbers = findNumbers(document.elements)
        if (per100Headers.isEmpty()) {
            return unresolvedBasisOrNotFound(anchors, numbers, diagnostics)
        }
        val candidates = mutableListOf<ScoredCandidate>()

        anchors.forEach { anchor ->
            numbers.forEach { number ->
                val rowEvidence = rowEvidence(anchor, number) ?: run {
                    diagnostics += OcrDiagnostic(
                        "rejected",
                        "${number.value.toPlainString()} rejected: not aligned with ${anchor.sourceLine}",
                    )
                    return@forEach
                }

                val validatedForAnyBasis = per100Headers.any { header ->
                    NutritionValueValidator.validateCarbsPer100(number.value.toDouble(), header.basis!!) != null
                }
                if (!validatedForAnyBasis) {
                    diagnostics += OcrDiagnostic(
                        "rejected",
                        "${number.value.toPlainString()} rejected: outside the possible per-100 range",
                    )
                    return@forEach
                }

                val matchingHeaders = per100Headers.mapNotNull { header ->
                    columnEvidence(document, anchor, number, header)
                }
                if (matchingHeaders.isEmpty()) {
                    diagnostics += OcrDiagnostic(
                        "rejected",
                        "${number.value.toPlainString()} rejected: not aligned to any per-100 header",
                    )
                    return@forEach
                }

                matchingHeaders.forEach { column ->
                    val basis = column.header.basis!!
                    val validated = NutritionValueValidator.validateCarbsPer100(number.value.toDouble(), basis)
                        ?: return@forEach
                    val evidence = buildList {
                        add(CandidateEvidence("total-carbohydrate anchor", NutritionParserThresholds.CARBOHYDRATE_ANCHOR))
                        add(rowEvidence)
                        add(CandidateEvidence("${basis.name} header", NutritionParserThresholds.PER_100_HEADER))
                        add(column.evidence)
                        if (number.hasGramUnit) {
                            add(CandidateEvidence("printed gram unit", NutritionParserThresholds.GRAM_UNIT))
                        }
                    }
                    val candidate = CarbCandidate(
                        sourceLine = anchor.sourceLine,
                        label = anchor.label,
                        value = validated,
                        basis = basis,
                        score = evidence.sumOf { it.points },
                        geometry = number.box,
                        evidence = evidence,
                    )
                    candidates += ScoredCandidate(candidate, column.strong, rowEvidence.points)
                    diagnostics += OcrDiagnostic(
                        "candidate",
                        "${validated.toPlainString()} ${basis.name} score=${candidate.score} " +
                            "geometry=${number.box} evidence=${evidence.joinToString { "${it.reason}:${it.points}" }}",
                    )
                }
            }
        }

        val distinct = candidates
            .distinctBy { Triple(it.candidate.value.stripTrailingZeros(), it.candidate.basis, it.candidate.geometry) }
            .sortedByDescending { it.candidate.score }

        if (distinct.isEmpty()) {
            return unresolvedBasisOrNotFound(anchors, numbers, diagnostics)
        }

        val confident = distinct.filter {
            it.strongColumn &&
                it.rowPoints + NutritionParserThresholds.CARBOHYDRATE_ANCHOR >=
                NutritionParserThresholds.TRUSTWORTHY_ROW_SCORE &&
                it.candidate.score >= NutritionParserThresholds.CONFIDENT_SCORE
        }
        val winner = confident.firstOrNull()
        val runnerUp = distinct.drop(1).firstOrNull()
        val hasSelectionMargin = winner != null && (
            runnerUp == null ||
                winner.candidate.score - runnerUp.candidate.score >= NutritionParserThresholds.SELECTION_MARGIN
            )

        val reading = if (winner != null && hasSelectionMargin) {
            diagnostics += OcrDiagnostic(
                "selected",
                "${winner.candidate.value.toPlainString()} ${winner.candidate.basis?.name} won with score ${winner.candidate.score}",
            )
            distinct.drop(1).forEach {
                diagnostics += OcrDiagnostic(
                    "rejected",
                    "${it.candidate.value.toPlainString()} rejected: lower score ${it.candidate.score}",
                )
            }
            LabelReading.Confident(winner.candidate)
        } else {
            val plausible = distinct.filter {
                it.rowPoints + NutritionParserThresholds.CARBOHYDRATE_ANCHOR >=
                    NutritionParserThresholds.TRUSTWORTHY_ROW_SCORE
            }.map { it.candidate }
            if (plausible.isEmpty()) {
                return unresolvedBasisOrNotFound(anchors, numbers, diagnostics)
            } else {
                diagnostics += OcrDiagnostic(
                    "ambiguous",
                    "${plausible.size} plausible interpretation(s); selection margin not met",
                )
                LabelReading.Ambiguous(plausible)
            }
        }

        return NutritionParseReport(reading, diagnostics)
    }

    /**
     * Keeps the middle outcome explicit: a credible row/value is useful evidence, but without a
     * spatially established per-100 header the parser must not manufacture a basis.
     */
    private fun unresolvedBasisOrNotFound(
        anchors: List<Anchor>,
        numbers: List<NumberElement>,
        diagnostics: MutableList<OcrDiagnostic>,
    ): NutritionParseReport {
        val candidates = anchors.flatMap { anchor ->
            numbers.mapNotNull { number ->
                val row = rowEvidence(anchor, number) ?: return@mapNotNull null
                if (row.points + NutritionParserThresholds.CARBOHYDRATE_ANCHOR <
                    NutritionParserThresholds.TRUSTWORTHY_ROW_SCORE
                ) {
                    return@mapNotNull null
                }
                val validated = NutritionValueValidator.validateCarbsPer100(
                    number.value.toDouble(),
                    NutritionBasis.PER_100_G,
                ) ?: NutritionValueValidator.validateCarbsPer100(
                    number.value.toDouble(),
                    NutritionBasis.PER_100_ML,
                ) ?: return@mapNotNull null
                val evidence = buildList {
                    add(CandidateEvidence("total-carbohydrate anchor", NutritionParserThresholds.CARBOHYDRATE_ANCHOR))
                    add(row)
                    if (number.hasGramUnit) {
                        add(CandidateEvidence("printed gram unit", NutritionParserThresholds.GRAM_UNIT))
                    }
                }
                CarbCandidate(
                    sourceLine = anchor.sourceLine,
                    label = anchor.label,
                    value = validated,
                    basis = null,
                    score = evidence.sumOf { it.points },
                    geometry = number.box,
                    evidence = evidence,
                )
            }
        }.distinctBy { it.value.stripTrailingZeros() to it.geometry }
            .sortedByDescending { it.score }

        if (candidates.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "Carbohydrate row found but no trustworthy value/basis pairing")
            return NutritionParseReport(LabelReading.NotFound, diagnostics)
        }

        candidates.forEach { candidate ->
            diagnostics += OcrDiagnostic(
                "candidate",
                "${candidate.value.toPlainString()} basis=UNRESOLVED score=${candidate.score} " +
                    "geometry=${candidate.geometry} evidence=${candidate.evidence.joinToString { "${it.reason}:${it.points}" }}",
            )
        }
        diagnostics += OcrDiagnostic(
            "ambiguous",
            "${candidates.size} trustworthy row value(s), but no per-100 g/ml basis was established",
        )
        return NutritionParseReport(LabelReading.Ambiguous(candidates), diagnostics)
    }

    private fun findAnchor(line: OcrLine, diagnostics: MutableList<OcrDiagnostic>): Anchor? {
        val normalizedLine = NutritionTerminology.normalize(line.text)
        val exclusion = NutritionTerminology.exclusionTerms.firstOrNull {
            NutritionTerminology.containsTerm(normalizedLine, it)
        }
        if (exclusion != null) {
            diagnostics += OcrDiagnostic("excluded", "${line.text} contains exclusion '$exclusion'")
            return null
        }

        val spans = line.spans(maxElements = 5)
        val match = spans.mapNotNull { span ->
            val normalized = NutritionTerminology.normalize(span.text)
            val term = NutritionTerminology.carbohydrateTerms
                .filter { NutritionTerminology.containsTerm(normalized, it) }
                .maxByOrNull { NutritionTerminology.normalize(it).length }
            term?.let { span to it }
        }.minByOrNull { (span, _) -> span.box.width }
            ?: return null

        val anchor = Anchor(
            key = line.key,
            box = match.first.box,
            label = match.second.replaceFirstChar { it.uppercase() },
            sourceLine = line.text,
        )
        diagnostics += OcrDiagnostic("anchor", "${line.text} -> ${anchor.label} @ ${anchor.box}")
        return anchor
    }

    private fun findHeaders(
        lines: List<OcrLine>,
        diagnostics: MutableList<OcrDiagnostic>,
    ): List<ColumnHeader> {
        val found = mutableListOf<ColumnHeader>()
        lines.forEach { line ->
            line.spans(maxElements = 4).forEach { span ->
                val normalized = NutritionTerminology.normalize(span.text)
                val serving = NutritionTerminology.servingTerms.any {
                    NutritionTerminology.containsTerm(normalized, it)
                }
                if (serving) {
                    found += ColumnHeader(line.key, span.box, null)
                    diagnostics += OcrDiagnostic("header", "SERVING '${span.text}' @ ${span.box}")
                }

                if (NutritionTerminology.carbohydrateTerms.none {
                        NutritionTerminology.containsTerm(normalized, it)
                    }
                ) {
                    PER_100.find(normalized)?.groupValues?.get(1)?.let { unit ->
                        val basis = if (unit == "ml") NutritionBasis.PER_100_ML else NutritionBasis.PER_100_G
                        found += ColumnHeader(line.key, span.box, basis)
                        diagnostics += OcrDiagnostic("header", "${basis.name} '${span.text}' @ ${span.box}")
                    }
                }
            }
        }

        return found
            .groupBy { it.key to it.basis }
            .map { (_, duplicates) -> duplicates.minBy { it.box.width } }
    }

    private fun findNumbers(elements: List<OcrElement>): List<NumberElement> = buildList {
        elements.forEach { element ->
            NUMBER.findAll(element.text).forEach { match ->
                val raw = match.groupValues[1].replace(',', '.')
                val value = raw.toBigDecimalOrNull() ?: return@forEach
                val projected = projectBox(element, match.range.first, match.range.last + 1)
                add(
                    NumberElement(
                        value = value,
                        box = projected,
                        key = LineKey(element.blockId, element.lineId),
                        hasGramUnit = hasGramUnit(element.text, match.range.last + 1) ||
                            hasAdjacentGramUnit(element, elements),
                    ),
                )
            }
        }
    }

    private fun projectBox(element: OcrElement, start: Int, endExclusive: Int): OcrBox {
        if (element.text.isEmpty() || element.box.width == 0) return element.box
        val left = element.box.left + (element.box.width * start / element.text.length)
        val right = element.box.left + (element.box.width * endExclusive / element.text.length)
        return OcrBox(left, element.box.top, maxOf(left + 1, right), element.box.bottom)
    }

    private fun hasGramUnit(text: String, after: Int): Boolean =
        Regex("^\\s*(?:g|gr|gram|gramme|grammes|gramm)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(text.substring(after))

    private fun hasAdjacentGramUnit(element: OcrElement, elements: List<OcrElement>): Boolean =
        elements.any { other ->
            other !== element &&
                other.blockId == element.blockId &&
                other.lineId == element.lineId &&
                NutritionTerminology.normalize(other.text) in setOf("g", "gr", "gram") &&
                other.box.left >= element.box.right &&
                other.box.left - element.box.right <= maxOf(element.box.height * 2, 24)
        }

    private fun rowEvidence(anchor: Anchor, number: NumberElement): CandidateEvidence? = when {
        anchor.key == number.key -> CandidateEvidence(
            "same recognized line",
            NutritionParserThresholds.SAME_RECOGNIZED_LINE,
        )
        anchor.box.verticalOverlapRatio(number.box) >= NutritionParserThresholds.MIN_VERTICAL_OVERLAP ->
            CandidateEvidence("vertical row overlap", NutritionParserThresholds.VERTICAL_OVERLAP)
        abs(anchor.box.centerY - number.box.centerY) <=
            maxOf(anchor.box.height, number.box.height).coerceAtLeast(1) *
            NutritionParserThresholds.MAX_ROW_DISTANCE_IN_HEIGHT ->
            CandidateEvidence("near-row alignment", NutritionParserThresholds.NEAR_ROW)
        else -> null
    }

    private fun columnEvidence(
        document: OcrDocument,
        anchor: Anchor,
        number: NumberElement,
        header: ColumnHeader,
    ): ColumnMatch? {
        if (header.box.centerY >= maxOf(anchor.box.centerY, number.box.centerY)) return null
        val distance = abs(number.box.centerX - header.box.centerX)
        val strict = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            document.width * NutritionParserThresholds.STRICT_COLUMN_FRACTION,
            header.box.width * 0.75,
        )
        val loose = maxOf(strict, document.width * NutritionParserThresholds.LOOSE_COLUMN_FRACTION)
        return when {
            distance <= strict -> ColumnMatch(
                header,
                CandidateEvidence("strong per-100 column alignment", NutritionParserThresholds.STRONG_COLUMN_ALIGNMENT),
                strong = true,
            )
            distance <= loose -> ColumnMatch(
                header,
                CandidateEvidence("weak per-100 column alignment", NutritionParserThresholds.WEAK_COLUMN_ALIGNMENT),
                strong = false,
            )
            else -> null
        }
    }

    private data class LineKey(val block: Int, val line: Int)

    private data class OcrLine(val key: LineKey, val elements: List<OcrElement>) {
        val text: String = elements.joinToString(" ") { it.text }

        fun spans(maxElements: Int): List<TextSpan> = buildList {
            elements.indices.forEach { start ->
                for (length in 1..minOf(maxElements, elements.size - start)) {
                    val selected = elements.subList(start, start + length)
                    add(
                        TextSpan(
                            text = selected.joinToString(" ") { it.text },
                            box = selected.drop(1).fold(selected.first().box) { box, element ->
                                box.union(element.box)
                            },
                        ),
                    )
                }
            }
        }
    }

    private data class TextSpan(val text: String, val box: OcrBox)
    private data class Anchor(val key: LineKey, val box: OcrBox, val label: String, val sourceLine: String)
    private data class ColumnHeader(val key: LineKey, val box: OcrBox, val basis: NutritionBasis?)
    private data class NumberElement(
        val value: BigDecimal,
        val box: OcrBox,
        val key: LineKey,
        val hasGramUnit: Boolean,
    )
    private data class ColumnMatch(
        val header: ColumnHeader,
        val evidence: CandidateEvidence,
        val strong: Boolean,
    )
    private data class ScoredCandidate(val candidate: CarbCandidate, val strongColumn: Boolean, val rowPoints: Int)
}
