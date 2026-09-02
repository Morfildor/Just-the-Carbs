package app.justthecarbs.ocr

import app.justthecarbs.domain.ServingSizeParser

/** What one reconstructed table row is, as far as a carbohydrate reading is concerned. */
enum class NutritionRowKind {
    /** The row carrying the product's TOTAL carbohydrate figure. At most one per table. */
    TOTAL_CARBOHYDRATE,

    /** Sugars, polyols, starch, fibre, a named sugar — never a source of the total. */
    CARBOHYDRATE_CHILD,

    /** A column-header row: "per 100 g", "per serving", "%RI". Carries no nutrient value. */
    HEADER,

    OTHER,
}

/**
 * Classifies a [LogicalRow] by what it is, before any value is read from it.
 *
 * The child-nutrient rule is the whole point of this stage: a row naming any child nutrient is
 * [NutritionRowKind.CARBOHYDRATE_CHILD] **unconditionally**, so it cannot become the total no matter
 * what else it contains or how close its number sits to the word "Carbohydrate". The previous parser
 * expressed this as a scoring penalty, which geometry could outvote — that is exactly how a sugars
 * value got reported as total carbohydrate on a real package.
 */
object RowClassifier {

    /**
     * Memoized classifications, keyed by row **identity**.
     *
     * ### Why a cache at all
     *
     * Classification is consulted from five independent places — the interpreter, both column
     * stages, [UnitAccompanimentPolicy] and [ProseNutritionReader] — none of which can pass its
     * answer to the others without threading a classification map through every signature in the
     * parser. Profiling the 2026-09-01 device captures counted **95 classifications for 19 rows**,
     * each one running a full [NutrientRowSegments] pass over every element position at four span
     * lengths, plus a whole-vocabulary sweep.
     *
     * A single-entry cache was tried first and does not work: the callers interleave (one stage
     * filters every row, then the next maps every row), so consecutive lookups are for different
     * rows and each evicts the last. The access pattern needs a map, not a most-recent slot.
     *
     * ### Why identity, and why this cannot go stale
     *
     * [classify] is a pure function of the row's text and element geometry, and [LogicalRow] is an
     * immutable data class — so a given instance's answer is fixed for its lifetime. Keying on
     * identity rather than equality is deliberate: two equal-but-distinct rows are simply
     * re-classified, which is correct, whereas an equality-keyed map would quietly depend on
     * [LogicalRow.equals] covering everything [classify] reads.
     *
     * ### Why the bound, and why eviction is safe
     *
     * A parse builds its rows once and discards them, so entries are garbage after it returns. The
     * bound exists so a long-lived process cannot accumulate them without limit; clearing wholesale
     * on overflow costs at most one re-classification per row and needs no LRU bookkeeping. Sized
     * well above any real label's row count (the largest in this repo's corpus is 24).
     *
     * Not thread-safe, deliberately: one interpretation runs on one thread, and a lock on the
     * parser's hottest lookup would cost more than the work it protects. A racing writer can only
     * cause a redundant re-classification, never a wrong answer, because the value depends solely on
     * the key.
     */
    private val classifications = java.util.IdentityHashMap<LogicalRow, NutritionRowKind>()

    /** See [classifications]. Far above the largest real label's row count. */
    private const val MAX_CACHED_ROWS = 512

    /**
     * Classifies every row of a document, stopping nutrient classification at the package's own
     * structural boundary (§P0-2).
     *
     * ## Why this overload exists
     *
     * [classify] answers about one row from that row alone, which is what makes it pure and
     * cacheable — and which is exactly why it cannot see that a row sits inside an ingredient list.
     * On the Korean sauce the ingredients row names "brown sugar" and therefore classified as
     * `CARBOHYDRATE_CHILD`: correct about the words, wrong about the document.
     *
     * The boundary is a property of the *document*, so it is applied here, over the whole list, and
     * never inside the single-row function. A row past the boundary is `OTHER` — not reclassified by
     * some other rule, simply not nutrition.
     *
     * Callers holding a whole document should prefer this. [classify] remains correct for the
     * single-row question and is still what this delegates to.
     */
    fun classifyAll(rows: List<LogicalRow>): List<NutritionRowKind> {
        val boundary = DeclarationBoundary.indexOf(rows)
        return rows.mapIndexed { index, row ->
            if (boundary != null && index >= boundary) NutritionRowKind.OTHER else classify(row)
        }
    }

    fun classify(row: LogicalRow): NutritionRowKind {
        classifications[row]?.let { return it }
        val kind = classifyUncached(row)
        if (classifications.size >= MAX_CACHED_ROWS) classifications.clear()
        classifications[row] = kind
        return kind
    }

    private fun classifyUncached(row: LogicalRow): NutritionRowKind {
        if (ParserWorkCounters.enabled) ParserWorkCounters.rowClassifyCalls++
        val normalized = NutritionTerminology.normalize(row.text)

        // A linear Nutrition Facts panel prints several nutrients in sequence on one row, each
        // introduced by its own name. Such a row genuinely *contains* a total-carbohydrate
        // declaration bounded by the next nutrient name, so it is a total row — the child term
        // further along belongs to a different clause and is excluded by position, not by luck.
        //
        // Checked before the unconditional child rule below because that rule reads the row as one
        // unit, which is right for a merged table row and wrong for a linear panel. The distinction
        // is made by [NutrientRowSegments], which returns nothing at all unless the row states a
        // total term *and* a second, different nutrient term — a shape a merged table row does not
        // have. See that object for why this does not weaken the sugars-as-total guarantee.
        if (NutrientRowSegments.totalCarbohydrateSegment(row) != null) {
            return NutritionRowKind.TOTAL_CARBOHYDRATE
        }

        // First and unconditional. Order matters: "Carbohydrate of which sugars" hits this before
        // the carbohydrate check below, which is the entire correctness claim of this class.
        if (NutritionTerminology.exclusionTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return NutritionRowKind.CARBOHYDRATE_CHILD
        }

        if (NutritionTerminology.carbohydrateTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return NutritionRowKind.TOTAL_CARBOHYDRATE
        }

        if (isHeaderLike(normalized)) return NutritionRowKind.HEADER

        return NutritionRowKind.OTHER
    }

    /**
     * A row that names no nutrient but does name a measurement basis. Checked only after the
     * nutrient checks, so "Carbohydrate per 100 g" — a value row with an inline basis — stays the
     * total row rather than being demoted to a header that carries no value.
     */
    private fun isHeaderLike(normalizedText: String): Boolean {
        if (PER_100.containsMatchIn(normalizedText)) return true
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalizedText, it) }) {
            return true
        }
        if (REFERENCE_INTAKE.containsMatchIn(normalizedText)) return true
        return namesACountableServing(normalizedText)
    }

    /**
     * A "per <countable unit>" header — "per stuk", "par pièce" — using the **same** unit vocabulary
     * [ColumnClassifier] recognises columns with.
     *
     * Found by running the real Kinder package through ML Kit. Its per-piece header spans several
     * printed lines of eight languages, and the line carrying "Par pièce" names no per-100 basis, no
     * generic serving word and no reference intake — so this returned false, the row was typed
     * `OTHER`, and [ColumnClassifier] (which only ever looks at `HEADER` rows) never got to apply the
     * countable-unit vocabulary it already had. The per-piece column did not exist, and the printed
     * 6.7 g per piece was discarded with `no column`.
     *
     * Two stages disagreeing about what a serving header looks like is the actual defect; routing
     * both through [app.justthecarbs.domain.ServingSizeParser] is what stops them drifting again.
     *
     * Safe by construction: this is checked only after the nutrient terms, so a real value row is
     * never demoted — and `HEADER` and `OTHER` are equally value-less downstream, so the only thing
     * this can change is whether a column gets recognised.
     */
    private fun namesACountableServing(normalizedText: String): Boolean {
        val words = normalizedText.split(' ').filter { it.isNotBlank() }
        return words.zipWithNext().any { (first, second) ->
            first in CONNECTIVES && ServingSizeParser.kindForWord(second) != null
        }
    }

    /** The one shared list — see [NutritionTerminology]. */
    private val CONNECTIVES = NutritionTerminology.connectives

    /**
     * Every spelling of the basis unit, from the one shared list.
     *
     * This was a private `(?:g|ml)` literal until 2026-08-26 — a fourth independent copy of the same
     * pattern — and it is why extending [ColumnClassifier] alone did not make `per 100 gram` work: a
     * row that names no nutrient and no recognised basis is typed `OTHER`, and `ColumnClassifier`
     * only ever looks at `HEADER` rows, so the column vocabulary never got a chance to run. Two
     * stages have to agree that a row is a header before either can act on it.
     */
    private val PER_100 = Regex("(?:^|\\s)100\\s*(?:${NutritionTerminology.basisUnitAlternation})(?:$|\\s)")

    /** "%RI", "%DV", "reference intake", "RI*" — the percentage column's vocabulary. */
    private val REFERENCE_INTAKE = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")
}
