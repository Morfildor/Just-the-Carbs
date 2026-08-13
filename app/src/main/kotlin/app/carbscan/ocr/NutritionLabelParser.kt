package app.carbscan.ocr

import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.NutritionValueValidator
import java.math.BigDecimal

/** One carbohydrate row the parser believes it found on a label. */
data class CarbCandidate(
    /** The label text that produced it, shown to the user so they can check the parser's work. */
    val sourceLine: String,
    val label: String,
    val value: BigDecimal,
    val basis: NutritionBasis,
)

/** The outcome of reading a nutrition label (§29). */
sealed interface LabelReading {
    /** Exactly one plausible row. Still requires confirmation — never auto-accepted. */
    data class Single(val candidate: CarbCandidate) : LabelReading

    /** Several plausible rows. The user picks; the app does not. */
    data class Ambiguous(val candidates: List<CarbCandidate>) : LabelReading

    /** Nothing readable. The user is sent to manual entry (§29 "Never guess"). */
    data object NotFound : LabelReading
}

/**
 * Parses a nutrition table out of OCR text (§29).
 *
 * Pure Kotlin and free of ML Kit, so every case below is a JVM unit test rather than something that
 * needs a camera pointed at a packet of biscuits.
 *
 * Two rules shape the whole design:
 *
 * 1. **Total carbohydrate only.** `waarvan suikers` / `of which sugars` is a sub-line of the total,
 *    and reading it as the total understates carbohydrate — sometimes by a lot. Those lines are
 *    actively excluded rather than merely not matched (§12).
 * 2. **Never guess.** Two plausible rows produce [LabelReading.Ambiguous], not the first match.
 *    A label with a "per 100 g" and a "per serving" column is the common case, and silently
 *    choosing one would be inventing an answer (§29, §71).
 */
object NutritionLabelParser {

    /** The total-carbohydrate label in the supported languages (§29). Extend this list to add more. */
    private val CARB_LABELS = listOf(
        "koolhydraten",
        "carbohydrate",
        "carbohydrates",
        "kohlenhydrate",
        "glucides",
    )

    /**
     * Sub-lines that sit underneath the total and must never be mistaken for it.
     * "waarvan suikers" is Dutch for "of which sugars".
     */
    private val EXCLUDED = listOf(
        "waarvan",
        "of which",
        "davon",
        "dont",
        "suikers",
        "sugars",
        "zucker",
        "sucres",
        "vezels",
        "fibre",
        "fiber",
        "ballaststoffe",
        "polyol",
        "zetmeel",
        "starch",
    )

    /** e.g. "47,3 g" or "47.3g". The unit is required — a bare number on a label is not a value. */
    private val VALUE = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)

    private val PER_100_ML = Regex("""per\s*100\s*ml|/\s*100\s*ml|100\s*ml""", RegexOption.IGNORE_CASE)
    private val PER_100_G = Regex("""per\s*100\s*g|/\s*100\s*g|100\s*g""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): LabelReading {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // The basis is usually a column heading, not part of the carbohydrate row, so it is read
        // from the whole block rather than from the matched line alone.
        val basis = detectBasis(rawText)

        val candidates = lines.mapNotNull { line -> toCandidate(line, basis) }

        // Identical readings on several lines are one finding, not an ambiguity: a label repeating
        // "Koolhydraten 47,3 g" in two columns of the same units is not a question for the user.
        val distinct = candidates.distinctBy { it.value to it.basis }

        return when {
            distinct.isEmpty() -> LabelReading.NotFound
            distinct.size == 1 -> LabelReading.Single(distinct.single())
            else -> LabelReading.Ambiguous(distinct)
        }
    }

    private fun toCandidate(line: String, basis: NutritionBasis): CarbCandidate? {
        val lower = line.lowercase()

        if (EXCLUDED.any { it in lower }) return null
        val label = CARB_LABELS.firstOrNull { it in lower } ?: return null

        val raw = VALUE.find(line)?.groupValues?.get(1) ?: return null
        val value = BigDecimal(raw.replace(',', '.'))

        // An OCR misread that produces an impossible figure is discarded here, not shown to the
        // user as something to confirm (§13).
        val validated = NutritionValueValidator.validateCarbsPer100(value.toDouble(), basis)
            ?: return null

        return CarbCandidate(
            sourceLine = line,
            label = label.replaceFirstChar(Char::uppercase),
            value = validated,
            basis = basis,
        )
    }

    /**
     * Millilitres win a tie. If a label mentions both, it is a drink whose table also prints a
     * per-serving weight, and treating a beverage as grams is the error with real consequences.
     */
    private fun detectBasis(text: String): NutritionBasis = when {
        PER_100_ML.containsMatchIn(text) -> NutritionBasis.PER_100_ML
        PER_100_G.containsMatchIn(text) -> NutritionBasis.PER_100_G
        else -> NutritionBasis.PER_100_G
    }
}
