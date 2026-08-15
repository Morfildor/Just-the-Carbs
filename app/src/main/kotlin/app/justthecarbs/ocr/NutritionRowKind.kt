package app.justthecarbs.ocr

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
        return REFERENCE_INTAKE.containsMatchIn(normalizedText)
    }

    private val PER_100 = Regex("(?:^|\\s)100\\s*(?:g|ml)(?:$|\\s)")

    /** "%RI", "%DV", "reference intake", "RI*" — the percentage column's vocabulary. */
    private val REFERENCE_INTAKE = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")
}
