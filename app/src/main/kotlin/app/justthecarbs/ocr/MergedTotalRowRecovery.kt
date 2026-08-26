package app.justthecarbs.ocr

/**
 * Recovers the total-carbohydrate declaration from a row that [RowClassifier] typed
 * [NutritionRowKind.CARBOHYDRATE_CHILD] because a child term was printed later on the same
 * reconstructed row.
 *
 * ### The failure this exists for
 *
 * ML Kit merges two printed rows into one reconstructed row routinely — it is the same wrapping
 * behaviour [CarbohydrateTermAnchor] was built for, seen from the other side. On a real device a
 * trilingual label produced:
 *
 * ```
 * Kohlenhydrate: waarvan suikers/dont   86g   |22 g (8 %)
 * ```
 *
 * The printed table has `Kohlenhydrate … 86 g` on one line and `waarvan suikers … 54 g` on the next;
 * reconstruction welded the two nutrient names together. [RowClassifier] then applied its
 * unconditional child-exclusion rule and typed the whole row `CARBOHYDRATE_CHILD`, so the row was
 * discarded, no total row remained, and the correct `86` was lost. On the same package a second
 * photograph merged the *fat* row into the carbohydrate row instead and the parser confidently
 * reported the fat figure, `12`.
 *
 * ### Why weakening [RowClassifier] is not the fix
 *
 * That unconditional rule is the correctness claim of the whole geometry-first rewrite: a row naming
 * a child nutrient can never supply the total, which is what stopped a sugars figure being reported
 * as total carbohydrate on real packaging. It stays exactly as it is. This class does not reclassify
 * anything — it asks a narrower question about rows the classifier has already excluded, and answers
 * it with the row's own reading order.
 *
 * ### The rule
 *
 * A child-typed row carries a recoverable total declaration when a **total-carbohydrate term is
 * printed before the first child term**. The recovered row exposes only the elements **between the
 * total term and that child term** — bounded on both sides:
 *
 * - the right bound makes the sugars value that caused the exclusion unreachable;
 * - the left bound discards the preceding nutrient's clause, whose value would otherwise be the only
 *   number left in the span on a running-text label, where a value follows its term.
 *
 * Both bounds were required in practice: an early version cut only on the right and reported a
 * bread label's **fat** figure as its carbohydrate total.
 *
 * This is the brief's "classify by local anchor span, not whole-row classification", scoped to the
 * one case where the whole-row verdict demonstrably discards a correct printed declaration.
 *
 * ### Why this cannot manufacture a wrong value
 *
 * - It only ever *adds back* a span that a total-carbohydrate term introduces. A row with no total
 *   term before its child term is left excluded, exactly as today.
 * - The span ends at the first child anchor, so it is structurally impossible to reach a child's
 *   value — the property that made the exclusion rule worth having.
 * - The surviving elements go through the unchanged pipeline: [CarbohydrateTermAnchor] still strips
 *   values claimed by other nutrients (which is what refuses the merged *fat* row above), the column
 *   classifier still has to place the cell in a real per-100 column, and the validator still has to
 *   accept it. Nothing here bypasses a single existing guard.
 */
internal object MergedTotalRowRecovery {

    /**
     * The leading total-carbohydrate span of [row], or null when the row carries no total declaration
     * ahead of its child term.
     *
     * Returned as a [LogicalRow] so callers treat it exactly like any other total row; its box is
     * recomputed from the surviving elements rather than inherited, so downstream geometry describes
     * the span that was actually read.
     */
    fun recover(row: LogicalRow): LogicalRow? {
        val anchors = CarbohydrateTermAnchor.nutrientAnchors(row)
        val firstChild = anchors.firstOrNull { !it.isCarbohydrate && it.isChild } ?: return null
        // The LAST total term before the child, so a multilingual run of synonyms
        // ("Koolhydraten/Glucides/Kohlenhydrate") starts the span at the name nearest its value.
        val leadingTotal = anchors.lastOrNull { it.isCarbohydrate && it.left < firstChild.left }
            ?: return null

        // Bounded on BOTH sides: at or right of the total term, strictly left of the child term.
        //
        // The left bound is not optional and its absence produced a confident-wrong on this repo's
        // stokbrood fixture. That label prints "…onverzadigde vetzuren 6,4 g, koolhydraten 46g,
        // waarvan suikers 1,0 g" on one reconstructed row. Cutting only at the child term kept the
        // preceding clause's **fat** figure `6,4` and — because the value follows its term in running
        // text — dropped the carbohydrate's own `46`. The parser then confidently reported 6.4.
        //
        // Anchoring the left edge at the total term is what makes this span-local in the sense the
        // rule claims: the span contains one nutrient's declaration and no part of its neighbours'.
        val kept = row.elements.filter { it.box.left >= leadingTotal.left && it.box.left < firstChild.left }
        if (kept.isEmpty()) return null

        // A span with no number in it is not a recovered declaration, and returning one is actively
        // harmful: it presents as a total-carbohydrate row, so the interpreter stops looking and the
        // prose stage — which may well be able to read the sentence whole — is never reached.
        //
        // This is what a running-text label does when the recognizer fuses the value to the following
        // clause ("koolhydraten" + "46g,waarvan"): the value's element begins at the child anchor, so
        // the bounded span legitimately contains no cell. Yielding nothing hands the row back to the
        // stage equipped for it rather than answering from a clause that states no figure.
        if (kept.none { it.text.any(Char::isDigit) }) return null
        return LogicalRow(
            elements = kept,
            box = kept.map { it.box }.reduce { acc, box -> acc.union(box) },
            sourceLines = kept.map { LineKey(it.blockId, it.lineId) }.toSet(),
        )
    }
}
