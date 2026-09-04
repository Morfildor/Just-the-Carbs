package app.justthecarbs.ocr

/**
 * A complete, pasteable trace of one nutrition-label read (§9).
 *
 * **Why this exists.** The real-device OCR failure this was written for was invisible from the app:
 * the screen said "couldn't find carbohydrates" and nothing said whether ML Kit had failed to read
 * the words, whether the rows had been reconstructed wrongly, or whether a column had gone
 * unresolved. Diagnosing it required reconstructing the geometry by hand. Every stage now reports
 * what it saw and what it refused, in one block a person can copy out of logcat and hand to whoever
 * is debugging it.
 *
 * Pure — no Android types, no I/O, no logging of its own — so the format is unit-testable and so
 * nothing here can accidentally become a shipped side effect. [OcrDiagnosticsLogger] is the only
 * caller and it is `BuildConfig.DEBUG`-gated, which keeps this out of release builds entirely.
 *
 * **What it deliberately does not contain:** the image, any crop of it, and anything identifying a
 * person or a device. A nutrition label photographed in someone's kitchen is their data; the
 * geometry of the words on it is what is needed to fix a parser.
 */
object OcrDiagnosticsReport {

    fun render(document: OcrDocument, report: NutritionParseReport): String = buildString {
        appendLine("=== JustTheCarbs OCR diagnostics ===")
        appendLine("image: ${document.width}x${document.height}  elements=${document.elements.size}")
        appendLine("skew: ${"%.4f".format(RowSlopeEstimator.estimate(document.elements))} (dy/dx)")
        appendLine()

        appendLine("--- raw text ---")
        appendLine(document.elements.joinToString(" ") { it.text })
        appendLine()

        appendLine("--- elements (text @ block/line box) ---")
        document.elements.forEach { element ->
            appendLine(
                "  '${element.text}' @ b${element.blockId}/l${element.lineId} " +
                    "[${element.box.left},${element.box.top},${element.box.right},${element.box.bottom}]",
            )
        }
        appendLine()

        val rows = LogicalRowBuilder.build(document)
        appendLine("--- reconstructed rows (${rows.size}) ---")
        rows.forEach { row ->
            appendLine(
                "  [${RowClassifier.classify(row).name}] '${row.text}' " +
                    "@ y=${row.box.top}..${row.box.bottom} lines=${row.sourceLines.size}",
            )
        }
        appendLine()

        val columns = ColumnClassifier.classify(rows, document.width)
        appendLine("--- columns (${columns.size}) ---")
        if (columns.isEmpty()) appendLine("  (none resolved)")
        columns.forEach { column ->
            val band = column.verticalExtent?.let { " y=${it.first}..${it.last}" }.orEmpty()
            appendLine(
                "  ${column.kind.name} x=${"%.1f".format(column.centerX)} header='${column.headerText}'$band",
            )
        }
        appendLine()

        appendLine("--- excluded percentage cells ---")
        val excluded = rows.flatMap { row ->
            PercentAssociation.percentElementIndices(row, document.width)
                .mapNotNull { row.elements.getOrNull(it)?.text }
        }
        appendLine(if (excluded.isEmpty()) "  (none)" else "  " + excluded.joinToString(", "))
        appendLine()

        appendLine("--- stage log ---")
        report.diagnostics.forEach { appendLine("  ${it.stage}: ${it.message}") }
        appendLine()

        appendLine("--- outcome ---")
        when (val reading = report.reading) {
            is LabelReading.Confident -> appendLine(
                "  CONFIDENT ${reading.candidate.value.toPlainString()} ${reading.candidate.basis?.name} " +
                    "from '${reading.candidate.sourceLine}'",
            )
            is LabelReading.Ambiguous -> {
                appendLine("  AMBIGUOUS (${reading.candidates.size} interpretations)")
                reading.candidates.forEach {
                    appendLine("    ${it.value.toPlainString()} ${it.basis?.name} from '${it.sourceLine}'")
                }
            }
            LabelReading.NotFound -> {
                appendLine("  NOT FOUND")
                appendLine(
                    "  reason: ${report.failureReason?.name ?: notFoundReason(report, rows, columns)}",
                )
            }
        }
        report.servingCandidate?.let { serving ->
            // `descriptor`/`weight` here describe the **column header's** own serving declaration —
            // "per portie 50 g". They are legitimately absent on a US linear Nutrition Facts panel,
            // which has no column headers at all and states its serving size in a sentence instead.
            //
            // That absence is NOT the same as the app not knowing the serving size: `ServingDeclaration`
            // reads `Serv. size: 1 Tbsp (18 g)` off the panel and is what the recovery screen shows.
            // A bundle reading `weight=none` beside a screen reading "From 6 g per 18 g serving" is
            // two different objects being reported, not a contradiction — the `recovery proposal`
            // block in `selection.txt` prints the one the user actually saw.
            appendLine(
                "  serving: ${serving.carbsPerServing.toPlainString()} per '${serving.rawHeaderText}' " +
                    "header-descriptor=${serving.descriptor?.kind?.name ?: "none"} " +
                    "header-weight=${serving.descriptor?.weightOrVolume?.amount?.toPlainString() ?: "none"} " +
                    "(header only; see 'recovery proposal' for the declared serving size)",
            )
        } ?: appendLine("  serving: none")
        report.provenance?.let { provenance ->
            append("  provenance: ")
            when (provenance) {
                is CandidateProvenance.FromRow -> appendLine("row '${provenance.rowText}'")
                is CandidateProvenance.FromDeclaration -> appendLine(
                    "declaration rows ${provenance.rowTexts.joinToString(prefix = "'", postfix = "'", separator = "' / '")}",
                )
                is CandidateProvenance.FromProseSpan ->
                    appendLine("prose span '${provenance.nutrientTerm}' -> elements ${provenance.valueElementIndices}")
            }
        }
        appendLine("=== end ===")
    }

    /**
     * Which stage ran out of evidence, named in the terms the pipeline is built from, so a failure
     * report points at a stage rather than at "OCR".
     */
    private fun notFoundReason(
        report: NutritionParseReport,
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
    ): String {
        val totals = rows.count { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }
        val children = rows.count { RowClassifier.classify(it) == NutritionRowKind.CARBOHYDRATE_CHILD }
        return when {
            rows.isEmpty() -> "ML Kit recognized no text"
            totals == 0 && children > 0 ->
                "no total-carbohydrate row; $children child row(s) found — a merged row would show here"
            totals == 0 -> "no row named a carbohydrate term (check terminology and recognition)"
            columns.none {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            } -> "total row found but no per-100 column was resolved"
            else -> report.diagnostics.lastOrNull { it.stage == "result" }?.message ?: "unspecified"
        }
    }
}
