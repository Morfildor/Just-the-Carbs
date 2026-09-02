package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis

/**
 * The escape from a recovery dead end: the app knows *which row* and *which column*, and asks the
 * user only for the number it could not read.
 *
 * ## The dead end this replaces
 *
 * On `docs/Scan Evidence 02-09/20260902-085453-023` the drink's carbohydrate row printed
 * `Koolhydraten: 0,5 g` and ML Kit returned **`Kolhydraten: 0.59`** — one letter dropped from the
 * nutrient name, and the unit glyph read as a digit. Two consequences compounded:
 *
 * - `Kolhydraten` was not a carbohydrate word, so the row typed `OTHER`, there was no
 *   total-carbohydrate row, and [RecoveryCandidates] had nothing to contribute.
 * - `0.59` states no unit on a label that prints units, so even with the row recovered the cell is
 *   correctly refused.
 *
 * The screen therefore showed an empty list and an instruction to tap the carbohydrate row — which
 * the user did, repeatedly, because the tap was landing on a row the app had no candidates for. The
 * recording shows the loop. **A rejected tap that leaves the screen unchanged is indistinguishable
 * from a missed tap.**
 *
 * ## What is established, and what is asked
 *
 * The two facts are separable and only one was missing. The column classifier resolved `PER_100_ML`
 * from the printed header with no difficulty; it is the *cell* that was unreadable. So the app knows
 * the basis and asks for the amount:
 *
 * ```
 * Carbohydrate row found, but the number wasn't clear.
 * Enter the value printed under 100 ml.
 * ```
 *
 * **The basis is fixed and the user cannot change it here.** That is the whole safety argument: this
 * screen is reachable only when the label itself stated the basis, so offering a `100 g or 100 ml?`
 * picker would invite the user to overwrite a fact the app already read correctly with a guess — the
 * exact composition that produced `1.3 g / 100 ml` through the old two-step recovery. Someone who
 * genuinely wants to supply both halves uses full manual entry, which is a different screen reached
 * by a different action.
 *
 * When no row or no unambiguous basis is established this returns null, and the screen offers retake
 * or manual entry honestly rather than inventing a target.
 */
internal object FocusedAmountEntry {

    /**
     * What to ask for.
     *
     * [rowText] is echoed back so the user can confirm the app found the right row before typing a
     * number into it — the one check available to them, since they cannot see the app's row
     * reconstruction.
     */
    data class Target(
        val basis: NutritionBasis,
        val rowText: String,
        /** The row's box, so the frozen photograph can highlight what the app believes it found. */
        val rowBox: OcrBox,
    )

    /**
     * The focused-entry target for [document], or null when one is not safely established.
     *
     * Requires **both**:
     * - a total-carbohydrate row, either classified directly or recovered by
     *   [DamagedCarbohydrateLabel] — the recovery that exists for precisely this damaged-word case;
     * - exactly one per-100 column, so the basis is unambiguous. Two per-100 columns disagreeing, or
     *   only a serving column, yields null rather than a choice — the app does not ask the user to
     *   arbitrate between two things it read.
     *
     * Two per-100 columns stating the *same* basis is agreement, not conflict: multilingual
     * packaging prints `per 100 ml / pro 100 ml` routinely.
     */
    fun of(document: OcrDocument?): Target? {
        if (document == null || document.elements.isEmpty()) return null
        val rows = LogicalRowBuilder.build(document)
        val kinds = rows.map { RowClassifier.classify(it) }
        val columns = ColumnClassifier.classify(rows, document.width)

        val basis = columns
            .mapNotNull {
                when (it.kind) {
                    NutritionColumnKind.PER_100_G -> NutritionBasis.PER_100_G
                    NutritionColumnKind.PER_100_ML -> NutritionBasis.PER_100_ML
                    else -> null
                }
            }
            .distinct()
            .singleOrNull()
            ?: return null

        val rowIndex = kinds.indexOfFirst { it == NutritionRowKind.TOTAL_CARBOHYDRATE }
            .takeIf { it >= 0 }
            ?: DamagedCarbohydrateLabel.recoverTotalRowIndex(rows, kinds, columns)
            // A row that merged the carbohydrate declaration with the `waarvan suikers` clause that
            // follows it classifies as a child, so neither branch above finds it — yet the label
            // plainly printed a carbohydrate row and the user can see it. Measured on both truffle
            // captures of the seventh session, where this returned null and the screen offered no
            // way forward at all after a tap.
            //
            // Asking for [NutrientRowSegments.totalCarbohydrateClause] rather than relaxing the
            // classification keeps the distinction that matters: this establishes *which row to ask
            // about*, and the user then types the number. No value is read from the row, so the
            // merged-row guard's concern — reading a figure out of a clause that may not be what it
            // looks like — does not arise.
            ?: rows.indexOfFirst { NutrientRowSegments.totalCarbohydrateClause(it) != null }
                .takeIf { it >= 0 }
            ?: return null

        val row = rows[rowIndex]
        return Target(basis = basis, rowText = row.text, rowBox = row.box)
    }
}
