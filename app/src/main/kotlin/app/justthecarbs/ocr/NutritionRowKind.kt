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

    fun classify(row: LogicalRow): NutritionRowKind {
        val normalized = NutritionTerminology.normalize(row.text)

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

    private val PER_100 = Regex("(?:^|\\s)100\\s*(?:g|ml)(?:$|\\s)")

    /** "%RI", "%DV", "reference intake", "RI*" — the percentage column's vocabulary. */
    private val REFERENCE_INTAKE = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")
}
