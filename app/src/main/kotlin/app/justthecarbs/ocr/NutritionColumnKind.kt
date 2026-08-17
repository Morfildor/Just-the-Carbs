package app.justthecarbs.ocr

import app.justthecarbs.domain.ServingSizeParser
import kotlin.math.abs

/** What one table column measures. */
enum class NutritionColumnKind {
    PER_100_G,
    PER_100_ML,
    PER_SERVING,

    /** A "%RI"/"%DV" column. Never a carbohydrate quantity, only a percentage of a daily reference. */
    REFERENCE_PERCENT,

    /** Position known, meaning not established. A cell here is never used for any figure. */
    UNKNOWN,
}

/** One classified column, positioned by the x-centre later cells are aligned against. */
data class NutritionColumn(
    val kind: NutritionColumnKind,
    /** Null when the column was recovered from its cells rather than from a header. */
    val headerBox: OcrBox?,
    val centerX: Double,
    /** The header text this column was read from, or "" when recovered from cell shape. */
    val headerText: String,
    /**
     * The band of rows this column is evidence about, or null when it applies document-wide.
     *
     * Non-null only for a column recovered from cell shape rather than read from a header. A stated
     * header is a claim about the table it heads, and a table's rows run below its header for an
     * unbounded distance — on the real packaging in this repo's fixtures a correct per-100 header
     * sits seven reconstructed rows above the carbohydrate row it governs. A cluster of
     * percent-shaped cells, by contrast, is evidence only about the rows it was found on: it says
     * "these particular rows have a percentage in this x-band", and nothing whatever about a table
     * printed elsewhere in the frame that it never touched.
     *
     * That distinction matters because a crop of a real package routinely retains a second printed
     * block — a reference-intake summary, a marketing paragraph, a neighbouring package. Its
     * percentages cluster on an x position of their own, the fallback recovers a column from them,
     * and that column then vetoes the real table's grams cells as "percentages". The reading is lost
     * to a panel it has no relationship with.
     *
     * Bounding a recovered column to its own rows is a narrowing of what it may *claim*. It is not a
     * preference for whichever column sits nearer the values — that would be a proximity score, and
     * proximity scoring is the mechanism the geometry-first rewrite removed.
     */
    val verticalExtent: IntRange? = null,
)

/**
 * Finds the table's columns and says what each one measures.
 *
 * Header text is the primary signal. The cell-shape fallback exists for exactly one job: a column
 * whose numbers all carry a percent sign is [NutritionColumnKind.REFERENCE_PERCENT] even when OCR
 * lost its header, so a "17%" can never be handed back as 17 g of carbohydrate. It deliberately does
 * NOT try to tell per-100 from per-serving by shape — both look like bare grams, and a wrong guess
 * there is a confident wrong answer rather than a refusal. Those need a header, or they stay
 * [NutritionColumnKind.UNKNOWN] and are refused downstream.
 */
object ColumnClassifier {

    fun classify(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        val headerRows = rows.filter { RowClassifier.classify(it) == NutritionRowKind.HEADER }
        val fromHeaders = headerRows.flatMap { columnsIn(it) }

        val percentColumns = percentColumnsFromCells(rows, documentWidth).filter { candidate ->
            // A header already covering this x position wins; do not add a duplicate.
            fromHeaders.none { abs(it.centerX - candidate.centerX) <= documentWidth * NEAR_COLUMN_FRACTION }
        }

        return (fromHeaders + percentColumns).sortedBy { it.centerX }
    }

    /**
     * Walks a header row's elements, growing a span while it stays unrecognized and emitting a
     * column as soon as the span matches a known header vocabulary. This is what separates
     * "per 100 g" from "per serving" when both sit on one row.
     */
    private fun columnsIn(row: LogicalRow): List<NutritionColumn> {
        val columns = mutableListOf<NutritionColumn>()
        var spanStart = 0

        while (spanStart < row.elements.size) {
            // A lone "%" heading its own column, which is how a reference-intake column is printed
            // on a dense multilingual label. Handled before the span walk so it can never be
            // absorbed into a neighbouring header: on a reconstruction of the Kinder table the
            // greedy pass matched "per stuk %" as one PER_SERVING span, which both dragged that
            // column's centre 38 px toward the percentages and left no percent column at all. It
            // also destroyed the serving descriptor, since "stuk %" parses as no unit word.
            if (isBarePercent(row.elements[spanStart].text)) {
                val box = row.elements[spanStart].box
                columns += NutritionColumn(
                    kind = NutritionColumnKind.REFERENCE_PERCENT,
                    headerBox = box,
                    centerX = box.centerX,
                    headerText = row.elements[spanStart].text,
                )
                spanStart++
                continue
            }

            var matched = false
            // Longest span first: "per 100 ml" must beat a bare "100" prefix.
            for (length in minOf(MAX_HEADER_SPAN, row.elements.size - spanStart) downTo 1) {
                val span = row.elements.subList(spanStart, spanStart + length)
                // A span ending on a connective has over-reached into the next column: "per 100 g
                // per" is the grams column plus the start of another one, and consuming that
                // trailing word both widens this column's centre and hides the column it belongs
                // to. A bare "%" is over-reach of the same kind — normalization strips the sign, so
                // "per stuk %" would otherwise still match the serving vocabulary. Shorter spans
                // are tried instead.
                if (isConnective(span.last().text) || isBarePercent(span.last().text)) continue
                val text = span.joinToString(" ") { it.text }
                val kind = kindOf(NutritionTerminology.normalize(text)) ?: continue
                val box = span.drop(1).fold(span.first().box) { acc, element -> acc.union(element.box) }
                columns += NutritionColumn(kind, box, box.centerX, text)
                spanStart += length
                matched = true
                break
            }
            if (!matched) spanStart++
        }

        return columns
    }

    /** A word that only ever introduces a header, never ends one. */
    private fun isConnective(text: String): Boolean =
        NutritionTerminology.normalize(text) in CONNECTIVES

    /**
     * An element that is a percent sign and nothing else, in the tokenizations ML Kit produces for a
     * percent-column header: "%", "% RI", "%RI*". A token carrying a digit is a cell, not a header.
     */
    private fun isBarePercent(text: String): Boolean = BARE_PERCENT_HEADER.matches(text.trim())

    /**
     * The kind this span names, or null if it names none — or more than one.
     *
     * Ambiguity must be a rejection, not a first-match win. A greedy longest-span pass offers
     * "per 100 g %RI" as one span, which contains both vocabularies; picking whichever was checked
     * first silently merged two real columns into one and lost the grams column entirely. Returning
     * null makes the caller try shorter spans, which is what separates them.
     */
    private fun kindOf(normalizedSpan: String): NutritionColumnKind? {
        val matches = buildList {
            if (REFERENCE_PERCENT.containsMatchIn(normalizedSpan)) add(NutritionColumnKind.REFERENCE_PERCENT)
            // Every per-100 match, not just the first: a span reading "per 100 g per 100 ml" covers
            // two real columns, and taking the first match would emit one column spanning both —
            // losing the ml column entirely and putting its centre between the two.
            PER_100.findAll(normalizedSpan).forEach { match ->
                add(
                    if (match.groupValues[1] == "ml") {
                        NutritionColumnKind.PER_100_ML
                    } else {
                        NutritionColumnKind.PER_100_G
                    },
                )
            }
            if (isServingSpan(normalizedSpan)) add(NutritionColumnKind.PER_SERVING)
        }
        return matches.singleOrNull()
    }

    /**
     * A per-serving column header: the generic "per serving"/"per portion", or a countable unit
     * ("per slice", "per 2 slices").
     *
     * The countable case reuses [ServingSizeParser]'s own unit-word table rather than a second list,
     * so the vocabulary a `serving_size` string is parsed with and the vocabulary a column header is
     * recognised with cannot drift apart.
     */
    private fun isServingSpan(normalizedSpan: String): Boolean {
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalizedSpan, it) }) {
            return true
        }
        val words = normalizedSpan.split(' ').filter { it.isNotBlank() }
        // Only a "per <unit>" shape counts. A bare unit word elsewhere in the table is not a header.
        if (words.size < 2 || words.first() !in CONNECTIVES) return false
        return ServingSizeParser.kindForWord(words.last()) != null
    }

    /**
     * Recovers a percent column from the cells themselves when its header is missing or unreadable.
     * Requires at least two percent-shaped cells sharing an x position, so a single stray "17%" in
     * running text cannot invent a column.
     */
    private fun percentColumnsFromCells(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        val percentCells = rows
            .filter { RowClassifier.classify(it) != NutritionRowKind.HEADER }
            .flatMap { row ->
                // Both tokenizations, decided once in PercentAssociation rather than re-tested here
                // against the element's own text. A split "3" + "%" is the shape a bare-text filter
                // misses, and it is the shape that leaves a percentage column unrecovered — exactly
                // when its header was also lost, which is the only case this fallback exists for.
                val indices = PercentAssociation.percentElementIndices(row, documentWidth)
                indices.mapNotNull { index ->
                    row.elements.getOrNull(index)?.takeIf { cell -> cell.text.any { it.isDigit() } }
                }
            }

        if (percentCells.size < MIN_PERCENT_CELLS) return emptyList()

        val tolerance = documentWidth * NEAR_COLUMN_FRACTION
        val clusters = mutableListOf<MutableList<OcrElement>>()
        percentCells.sortedBy { it.box.centerX }.forEach { cell ->
            val cluster = clusters.lastOrNull()
            if (cluster != null && abs(cluster.last().box.centerX - cell.box.centerX) <= tolerance) {
                cluster += cell
            } else {
                clusters += mutableListOf(cell)
            }
        }

        return clusters.filter { it.size >= MIN_PERCENT_CELLS }.map { cluster ->
            // The band is grown by one row pitch either side of the cells themselves, so a table
            // whose percentages OCR'd on only some of its rows still protects the rows between and
            // immediately around them. A pitch is measured from the cells' own text height, so it
            // scales with the image rather than assuming a resolution.
            val margin = cluster.map { it.box.height }.average() * PERCENT_BAND_MARGIN_IN_HEIGHTS
            val top = cluster.minOf { it.box.top } - margin
            val bottom = cluster.maxOf { it.box.bottom } + margin
            NutritionColumn(
                kind = NutritionColumnKind.REFERENCE_PERCENT,
                headerBox = null,
                centerX = cluster.map { it.box.centerX }.average(),
                headerText = "",
                verticalExtent = top.toInt()..bottom.toInt(),
            )
        }
    }

    /** Longest header phrase worth trying, e.g. "per 100 ml". */
    private const val MAX_HEADER_SPAN = 4

    /** Two cells make a column; one is a stray. */
    private const val MIN_PERCENT_CELLS = 2

    /** How close two x-centres must be to count as the same column. */
    private const val NEAR_COLUMN_FRACTION = 0.08

    /**
     * How far beyond its own cells a recovered percent column still claims, in text heights.
     *
     * One and a half heights is about one row pitch, which is what lets a column recovered from the
     * rows above and below a gap keep covering the row in between when its own percentage failed to
     * recognise. It is not enough to reach a separate printed block, which is the whole point.
     */
    private const val PERCENT_BAND_MARGIN_IN_HEIGHTS = 1.5

    /** Words that introduce a column header. One shared list — see NutritionTerminology. */
    private val CONNECTIVES = NutritionTerminology.connectives

    private val PER_100 = Regex("(?:^|\\s)100\\s*(g|ml)(?:$|\\s)")
    private val REFERENCE_PERCENT = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")

    /** Runs against RAW element text — normalization would strip the "%" these depend on. */
    private val BARE_PERCENT_HEADER = Regex("^%\\s*(?:ri|dv|gda)?\\*?$", RegexOption.IGNORE_CASE)
}
