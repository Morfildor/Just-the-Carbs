package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

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
    /** A per-serving figure read alongside the canonical per-100 result (spec §5). Never gates live scanning. */
    val servingCandidate: ServingCarbCandidate? = null,
)

/**
 * Spatial nutrition-table parser. Contains no Android or ML Kit types.
 *
 * Now a thin adapter over [NutritionTableInterpreter], which reconstructs the printed table from
 * geometry instead of scoring proximity against ML Kit's own line grouping. The public contract
 * ([LabelReading], [CarbCandidate], [NutritionParseReport]) is unchanged, so [LabelAnalyzer] and
 * [AmbiguityStabilityTracker] keep working untouched.
 */
object NutritionTableParser {

    fun parse(document: OcrDocument): LabelReading = parseWithDiagnostics(document).reading

    fun parseWithDiagnostics(document: OcrDocument): NutritionParseReport =
        NutritionTableInterpreter.interpret(document)
}
