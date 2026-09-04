package app.justthecarbs.ocr

import kotlin.math.abs

/** A table-local source-element subset and the semantic structure derived from it. */
internal data class NutritionPanel(
    val id: Int,
    val elements: List<OcrElement>,
    val bounds: OcrBox,
    val rows: List<LogicalRow>,
    val columns: List<NutritionColumn>,
    val declarations: List<NutrientDeclaration>,
    /** False means localization was uncertain and this is the unchanged full-document fallback. */
    val localized: Boolean,
)

/** One value-shaped source element together with its physical row and local column ownership. */
internal data class NutrientCell(
    val element: OcrElement,
    val sourceRow: LogicalRow,
    val column: NutritionColumn?,
)

/** One semantic nutrient declaration. It may retain more than one strict physical row. */
internal data class NutrientDeclaration(
    val kind: NutritionRowKind,
    val labelElements: List<OcrElement>,
    val valueCells: List<NutrientCell>,
    val sourceRows: List<LogicalRow>,
    val bounds: OcrBox,
    val panelId: Int = -1,
    val parentKind: NutritionRowKind? = null,
) {
    val text: String = sourceRows.joinToString(" / ") { it.text }
}

internal data class SemanticNutritionDocument(val panels: List<NutritionPanel>)

/** Builds the shared panel/row/declaration model used by every OCR interpretation surface. */
internal object NutritionDocumentModel {
    fun build(document: OcrDocument): SemanticNutritionDocument {
        if (document.elements.isEmpty()) return SemanticNutritionDocument(emptyList())
        val located = NutritionPanelLocator2D.locate(document)
        // Localization deliberately sees raw elements first. The strict full-document rows are lazy:
        // they are only needed by the conservative prose veto or the unchanged fallback path.
        val fullRows by lazy { LogicalRowBuilder.build(document) }
        val fullColumns by lazy { ColumnClassifier.classify(fullRows, document.width) }
        val fullIsProse by lazy {
            ProseNutritionReader.isProseLabel(fullRows, fullColumns, document.width)
        }
        // A near-full-width guess must never displace the established prose path. A strongly bounded
        // side-by-side table may still be isolated: that is the contamination case localization is
        // for, and its raw 2D structure is independent of the globally damaged row interpretation.
        val localized = if (
            located.isNotEmpty() && fullIsProse && located.none { elements ->
                horizontalCoverage(elements, document.width) <= MAX_PROSE_OVERRIDE_WIDTH_FRACTION
            }
        ) {
            emptyList()
        } else {
            located
        }
        val sources = if (localized.isEmpty()) listOf(document.elements to false) else {
            localized.map { it to true }
        }
        return SemanticNutritionDocument(
            sources.mapIndexed { index, (elements, isLocalized) ->
                val localDocument = document.copy(elements = elements)
                val rows = if (isLocalized) LogicalRowBuilder.build(localDocument) else fullRows
                val columns = if (isLocalized) {
                    ColumnClassifier.classify(rows, document.width)
                } else {
                    fullColumns
                }
                var hasTotal = false
                val declarations = NutrientDeclarationBuilder.build(rows, columns, document.width)
                    .map { declaration ->
                        val parent = if (
                            declaration.kind == NutritionRowKind.CARBOHYDRATE_CHILD && hasTotal
                        ) {
                            NutritionRowKind.TOTAL_CARBOHYDRATE
                        } else {
                            null
                        }
                        if (declaration.kind == NutritionRowKind.TOTAL_CARBOHYDRATE) hasTotal = true
                        declaration.copy(panelId = index, parentKind = parent)
                    }
                NutritionPanel(
                    id = index,
                    elements = elements,
                    bounds = unionOf(elements.map { it.box }),
                    rows = rows,
                    columns = columns,
                    declarations = declarations,
                    localized = isLocalized,
                )
            },
        )
    }

    private fun unionOf(boxes: List<OcrBox>): OcrBox =
        boxes.drop(1).fold(boxes.first()) { result, box -> result.union(box) }

    private fun horizontalCoverage(elements: List<OcrElement>, documentWidth: Int): Double {
        if (elements.isEmpty() || documentWidth <= 0) return 1.0
        return (elements.maxOf { it.box.right } - elements.minOf { it.box.left }).toDouble() / documentWidth
    }

    private const val MAX_PROSE_OVERRIDE_WIDTH_FRACTION = 0.72
}

/**
 * Conservative raw-element table localization.
 *
 * This is deliberately not a layout solver. A panel is emitted only when a total-carbohydrate
 * anchor, a per-100 header, several nutrient anchors, and a repeated numeric X cluster describe the
 * same bounded area. Failure to establish that structure returns no panel and preserves the full
 * document path.
 */
private object NutritionPanelLocator2D {
    private data class RawSpan(val elements: List<OcrElement>) {
        val text = NutritionTerminology.normalize(elements.joinToString(" ") { it.text })
        val box = elements.drop(1).fold(elements.first().box) { result, element ->
            result.union(element.box)
        }
    }

    private data class NumericCluster(val elements: List<OcrElement>) {
        val centerX = elements.map { it.box.centerX }.average()
        val top = elements.minOf { it.box.top }
        val bottom = elements.maxOf { it.box.bottom }
    }

    fun locate(document: OcrDocument): List<List<OcrElement>> {
        if (document.elements.size < MIN_PANEL_ELEMENTS) return emptyList()
        val medianHeight = document.elements.map { it.box.height }.sorted()
            .let { it[it.size / 2].coerceAtLeast(1) }
        val spans = rawSpans(document.elements)
        val totals = spans.filter { span ->
            NutritionTerminology.carbohydrateTerms.any {
                NutritionTerminology.containsTerm(span.text, it)
            }
        }.distinctBy { it.box }
        if (totals.isEmpty()) return emptyList()

        val bases = spans.filter { BASIS.containsMatchIn(it.text) }
        if (bases.isEmpty()) return emptyList()
        val servingHeaders = spans.filter { span ->
            NutritionTerminology.servingTerms.any {
                NutritionTerminology.containsTerm(span.text, it)
            }
        }
        val nutrientAnchors = spans.filter(::namesNutrient).distinctBy { it.box }
        val numericClusters = numericClusters(document.elements, document.width, medianHeight)
        if (numericClusters.isEmpty()) return emptyList()

        val candidates = totals.mapNotNull { total ->
            val cluster = numericClusters
                .filter { total.box.centerY >= it.top - medianHeight * MAX_VERTICAL_REACH_IN_HEIGHTS }
                .filter { total.box.centerY <= it.bottom + medianHeight * MAX_VERTICAL_REACH_IN_HEIGHTS }
                .minByOrNull { abs(it.centerX - total.box.centerX) }
                ?: return@mapNotNull null
            if (total.box.left > cluster.centerX) return@mapNotNull null
            val nearbyBases = bases.filter { basis ->
                basis.box.bottom <= cluster.bottom + medianHeight * 2 &&
                    basis.box.bottom >= cluster.top - medianHeight * MAX_HEADER_REACH_IN_HEIGHTS &&
                    abs(basis.box.centerX - cluster.centerX) <=
                    document.width * MAX_HEADER_COLUMN_SPREAD
            }
            if (nearbyBases.isEmpty()) return@mapNotNull null

            val nearbyNutrients = nutrientAnchors.filter { anchor ->
                    anchor.box.centerY >= cluster.top - medianHeight * 2 &&
                    anchor.box.centerY <= cluster.bottom + medianHeight * 2 &&
                    anchor.box.left <= cluster.centerX &&
                    abs(anchor.box.left - total.box.left) <=
                    document.width * MAX_LABEL_COLUMN_SPREAD
            }
            if (nearbyNutrients.size < MIN_NUTRIENT_ANCHORS) return@mapNotNull null

            val companionClusters = numericClusters.filter { other ->
                rangesOverlap(cluster.top, cluster.bottom, other.top, other.bottom) &&
                    abs(other.centerX - cluster.centerX) <= document.width * MAX_COLUMN_SPREAD
            }
            val nearbyServingHeaders = servingHeaders.filter { header ->
                nearbyBases.any { basis ->
                    abs(header.box.centerY - basis.box.centerY) <=
                        medianHeight * MAX_HEADER_ROW_GAP_IN_HEIGHTS
                }
            }
            val structural = total.elements +
                nearbyNutrients.flatMap { it.elements } +
                nearbyBases.flatMap { it.elements } +
                companionClusters.flatMap { it.elements } +
                nearbyServingHeaders.flatMap { it.elements }
            val left = structural.minOf { it.box.left } - medianHeight * HORIZONTAL_PAD_IN_HEIGHTS
            val right = structural.maxOf { it.box.right } + medianHeight * HORIZONTAL_PAD_IN_HEIGHTS
            val top = structural.minOf { it.box.top } - medianHeight * VERTICAL_PAD_IN_HEIGHTS
            val bottom = structural.maxOf { it.box.bottom } + medianHeight * VERTICAL_PAD_IN_HEIGHTS

            document.elements.filter { element ->
                element.box.centerX >= left && element.box.centerX <= right &&
                    element.box.centerY >= top && element.box.centerY <= bottom
            }.takeIf { it.size >= MIN_PANEL_ELEMENTS }
        }

        // Overlapping anchors in one multilingual table describe one panel, not several candidates.
        return candidates
            .sortedByDescending { it.size }
            .fold(mutableListOf()) { accepted, candidate ->
                if (accepted.none { existing -> overlapRatio(existing, candidate) >= SAME_PANEL_OVERLAP }) {
                    accepted += candidate
                }
                accepted
            }
    }

    /** ML Kit line segments used only to locate anchors; they never become physical rows. */
    private fun rawSpans(elements: List<OcrElement>): List<RawSpan> = buildList {
        elements.groupBy { LineKey(it.blockId, it.lineId) }.values.forEach { line ->
            val ordered = line.sortedBy { it.box.left }
            val segments = mutableListOf<MutableList<OcrElement>>()
            ordered.forEach { element ->
                val current = segments.lastOrNull()
                val previous = current?.lastOrNull()
                if (previous == null || element.box.left - previous.box.right >
                    maxOf(previous.box.height, element.box.height) * MAX_WORD_GAP_IN_HEIGHTS
                ) {
                    segments += mutableListOf(element)
                } else {
                    current += element
                }
            }
            // Singles preserve precise label anchors; the whole gap-bounded segment recovers terms
            // ML Kit split across elements. Avoiding every sliding subspan keeps localization linear.
            segments.forEach { segment ->
                segment.forEach { add(RawSpan(listOf(it))) }
                if (segment.size > 1) add(RawSpan(segment))
            }
        }
    }

    private fun namesNutrient(span: RawSpan): Boolean =
        NutritionTerminology.carbohydrateTerms.any {
            NutritionTerminology.containsTerm(span.text, it)
        } || NutritionTerminology.exclusionTerms.any {
            NutritionTerminology.containsTerm(span.text, it)
        } || CarbohydrateTermAnchor.OTHER_NUTRIENT_TERMS.any {
            NutritionTerminology.containsTerm(span.text, it)
        }

    private fun numericClusters(
        elements: List<OcrElement>,
        documentWidth: Int,
        medianHeight: Int,
    ): List<NumericCluster> {
        val numeric = elements.filter { ColumnOwnership.valueIn(it.text) != null }
            .sortedBy { it.box.centerX }
        if (numeric.isEmpty()) return emptyList()
        val maxXGap = maxOf(
            medianHeight.toDouble() * MAX_X_GAP_IN_HEIGHTS,
            documentWidth.toDouble() * MAX_X_GAP_FRACTION,
        )
        val clusters = mutableListOf<MutableList<OcrElement>>()
        numeric.forEach { element ->
            val current = clusters.lastOrNull()
            if (current == null || abs(current.map { it.box.centerX }.average() - element.box.centerX) > maxXGap) {
                clusters += mutableListOf(element)
            } else {
                current += element
            }
        }
        return clusters.flatMap { splitVertical(it, medianHeight) }
            .filter { it.elements.map { element -> element.box.centerY.toInt() }.distinct().size >= MIN_COLUMN_CELLS }
    }

    private fun splitVertical(elements: List<OcrElement>, medianHeight: Int): List<NumericCluster> {
        val ordered = elements.sortedBy { it.box.centerY }
        val result = mutableListOf<MutableList<OcrElement>>()
        ordered.forEach { element ->
            val current = result.lastOrNull()
            if (current == null || element.box.centerY - current.last().box.centerY >
                medianHeight * MAX_NUMERIC_ROW_GAP_IN_HEIGHTS
            ) {
                result += mutableListOf(element)
            } else {
                current += element
            }
        }
        return result.map(::NumericCluster)
    }

    private fun rangesOverlap(aTop: Int, aBottom: Int, bTop: Int, bBottom: Int): Boolean =
        minOf(aBottom, bBottom) >= maxOf(aTop, bTop)

    private fun overlapRatio(first: List<OcrElement>, second: List<OcrElement>): Double {
        val shared = first.toSet().intersect(second.toSet()).size
        return shared.toDouble() / minOf(first.size, second.size).coerceAtLeast(1)
    }

    private val BASIS = Regex(
        "(?:^|\\s)${NutritionTerminology.PER_100_QUANTITY_PATTERN}" +
            "\\s*(?:${NutritionTerminology.basisUnitAlternation})(?:$|\\s)",
    )

    private const val MIN_PANEL_ELEMENTS = 6
    private const val MIN_COLUMN_CELLS = 3
    private const val MIN_NUTRIENT_ANCHORS = 3
    private const val MAX_WORD_GAP_IN_HEIGHTS = 2
    private const val MAX_X_GAP_IN_HEIGHTS = 2
    private const val MAX_X_GAP_FRACTION = 0.035
    private const val MAX_NUMERIC_ROW_GAP_IN_HEIGHTS = 6
    private const val MAX_VERTICAL_REACH_IN_HEIGHTS = 12
    private const val MAX_HEADER_REACH_IN_HEIGHTS = 12
    private const val MAX_HEADER_ROW_GAP_IN_HEIGHTS = 3
    private const val MAX_HEADER_COLUMN_SPREAD = 0.35
    private const val MAX_LABEL_COLUMN_SPREAD = 0.35
    private const val MAX_COLUMN_SPREAD = 0.30
    private const val HORIZONTAL_PAD_IN_HEIGHTS = 2
    private const val VERTICAL_PAD_IN_HEIGHTS = 2
    private const val SAME_PANEL_OVERLAP = 0.60
}

/** Associates strict rows into semantic declarations without changing row membership. */
private object NutrientDeclarationBuilder {
    fun build(
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): List<NutrientDeclaration> {
        if (rows.isEmpty()) return emptyList()
        val originalKinds = RowClassifier.classifyAll(rows)
        val kinds = originalKinds.toMutableList()
        recoverMissingTotal(rows, kinds, columns)
        val declarations = mutableListOf<NutrientDeclaration>()
        val consumed = mutableSetOf<Int>()

        rows.indices.forEach { index ->
            if (index in consumed) return@forEach
            when (kinds[index]) {
                NutritionRowKind.TOTAL_CARBOHYDRATE -> {
                    val sourceRows = mutableListOf(rows[index])
                    var cursor = index
                    var hasValue = alignedCells(rows[index], columns, documentWidth).isNotEmpty()
                    while (sourceRows.size < MAX_DECLARATION_ROWS && cursor + 1 < rows.size) {
                        val nextIndex = cursor + 1
                        val next = rows[nextIndex]
                        if (!isAdjacent(rows[cursor], next)) break
                        when (kinds[nextIndex]) {
                            NutritionRowKind.CARBOHYDRATE_CHILD, NutritionRowKind.HEADER -> break
                            NutritionRowKind.TOTAL_CARBOHYDRATE -> {
                                val nextHasValue = alignedCells(next, columns, documentWidth).isNotEmpty()
                                if (hasValue && nextHasValue) break
                                sourceRows += next
                                consumed += nextIndex
                            }
                            NutritionRowKind.OTHER -> {
                                if (hasValue) break
                                if (!isValueOnlyContinuation(next, columns, documentWidth)) break
                                sourceRows += next
                            }
                        }
                        cursor = nextIndex
                        hasValue = sourceRows.any { alignedCells(it, columns, documentWidth).isNotEmpty() }
                    }
                    declarations += declaration(NutritionRowKind.TOTAL_CARBOHYDRATE, sourceRows, columns, documentWidth)
                }
                NutritionRowKind.CARBOHYDRATE_CHILD -> declarations += declaration(
                    NutritionRowKind.CARBOHYDRATE_CHILD,
                    listOf(rows[index]),
                    columns,
                    documentWidth,
                )
                NutritionRowKind.HEADER, NutritionRowKind.OTHER -> Unit
            }
        }
        rows.indices.filter { index ->
            originalKinds[index] == NutritionRowKind.CARBOHYDRATE_CHILD &&
                kinds[index] == NutritionRowKind.TOTAL_CARBOHYDRATE
        }.forEach { index ->
            declarations += declaration(
                NutritionRowKind.CARBOHYDRATE_CHILD,
                listOf(rows[index]),
                columns,
                documentWidth,
            )
        }
        if (declarations.none { it.kind == NutritionRowKind.TOTAL_CARBOHYDRATE }) {
            rows.firstOrNull { NutrientRowSegments.totalCarbohydrateClause(it) != null }?.let { row ->
                val clause = NutrientRowSegments.totalCarbohydrateClause(row)!!
                declarations += declaration(
                    NutritionRowKind.TOTAL_CARBOHYDRATE,
                    listOf(row),
                    columns,
                    documentWidth,
                ).copy(
                    labelElements = row.elements.filter { clause.contains(it.box) },
                    // The clause establishes a focused-entry target, not permission to read values
                    // from a physical row that also contains a child declaration.
                    valueCells = emptyList(),
                )
            }
        }
        return declarations
    }

    private fun recoverMissingTotal(
        rows: List<LogicalRow>,
        kinds: MutableList<NutritionRowKind>,
        columns: List<NutritionColumn>,
    ) {
        if (kinds.any { it == NutritionRowKind.TOTAL_CARBOHYDRATE }) return
        val merged = rows.indexOfFirst { MergedTotalRowRecovery.recover(it) != null }
        if (merged >= 0) {
            kinds[merged] = NutritionRowKind.TOTAL_CARBOHYDRATE
            return
        }
        DamagedCarbohydrateLabel.recoverTotalRowIndex(rows, kinds, columns)?.let {
            kinds[it] = NutritionRowKind.TOTAL_CARBOHYDRATE
        }
    }

    private fun declaration(
        kind: NutritionRowKind,
        sourceRows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): NutrientDeclaration {
        val cells = sourceRows.flatMap { alignedCells(it, columns, documentWidth) }
        val elements = sourceRows.flatMap { it.elements }
        return NutrientDeclaration(
            kind = kind,
            labelElements = elements.filter { ColumnOwnership.valueIn(it.text) == null },
            valueCells = cells,
            sourceRows = sourceRows,
            bounds = sourceRows.drop(1).fold(sourceRows.first().box) { result, row -> result.union(row.box) },
        )
    }

    private fun alignedCells(
        row: LogicalRow,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): List<NutrientCell> {
        val inlineBoxes = InlineBasisSpans.find(row).flatMap { span ->
            span.elementIndices.mapNotNull(row.elements::getOrNull).map { it.box }
        }.toSet()
        val competing = ColumnOwnership.competingCells(row)
        // ML Kit sometimes emits `Carbohydrate 52.4 g` as one element. It is not a competing
        // value-only cell, but it still makes this physical row value-bearing; the interpreter's
        // existing clause parser remains responsible for extracting and validating the number.
        val valueBearing = (competing + row.elements.filter(::containsInlineCarbohydrateValue)).distinct()
        return valueBearing.filterNot { it.box in inlineBoxes }.map { element ->
            val column = columns
                .filter { it.verticalExtent?.contains(element.box.centerY.toInt()) ?: true }
                .minByOrNull { abs(it.centerX - element.box.centerX) }
                ?.takeIf { ColumnOwnership.claims(it, element.box, competing, documentWidth) }
            NutrientCell(element, row, column)
        }.filter { it.column != null || columns.isEmpty() }
    }

    private fun containsInlineCarbohydrateValue(element: OcrElement): Boolean {
        val normalized = NutritionTerminology.normalize(element.text)
        val namesCarbohydrate = NutritionTerminology.carbohydrateTerms.any {
            NutritionTerminology.containsTerm(normalized, it)
        }
        return namesCarbohydrate && INLINE_VALUE.containsMatchIn(normalized)
    }

    private fun isValueOnlyContinuation(
        row: LogicalRow,
        columns: List<NutritionColumn>,
        documentWidth: Int,
    ): Boolean {
        if (CarbohydrateTermAnchor.nutrientAnchors(row).any { !it.isCarbohydrate }) return false
        val cells = alignedCells(row, columns, documentWidth)
        if (cells.isEmpty()) return false
        val cellElements = cells.map { it.element }.toSet()
        return row.elements.all { element ->
            element in cellElements || UNIT_OR_PUNCTUATION.matches(element.text.trim())
        }
    }

    private fun isAdjacent(first: LogicalRow, second: LogicalRow): Boolean {
        val medianHeight = (first.elements + second.elements).map { it.box.height }.sorted()
            .let { it[it.size / 2].coerceAtLeast(1) }
        val gap = second.box.top - first.box.bottom
        return gap <= medianHeight * MAX_CONTINUATION_GAP_IN_HEIGHTS
    }

    private val UNIT_OR_PUNCTUATION = Regex("^(?:g|gr|gram|ml|[,.;:/()%-]+)$", RegexOption.IGNORE_CASE)
    private val INLINE_VALUE = Regex("(?:^|\\s)\\d{1,3}(?:[.,]\\d{1,3})?\\s*(?:g|gr|gram|ml)(?:$|\\s|[,.;:])")
    private const val MAX_DECLARATION_ROWS = 3
    private const val MAX_CONTINUATION_GAP_IN_HEIGHTS = 1
}
