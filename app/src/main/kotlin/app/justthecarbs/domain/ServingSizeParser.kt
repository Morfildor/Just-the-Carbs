package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** A countable unit read from a remote serving-size string, normalized to a per-single-unit weight. */
data class ParsedServingSize(
    val kind: PortionUnitKind,
    val amountPerUnit: BigDecimal,
    val basis: NutritionBasis,
)

/**
 * Cautiously turns Open Food Facts' free-text `serving_size` into a countable-unit mapping
 * (countable-portions brief §5).
 *
 * Only accepts strings that state an explicit count-to-quantity relationship — `serving_quantity`
 * alone is never enough (brief §7: it is OFF's normalized extraction from `serving_size`, not a
 * documented "grams per countable unit"). A multi-count serving like "2 slices (70 g)" is divided
 * down to the per-unit weight; the app never stores "1 slice = 70 g".
 *
 * False negatives are acceptable; false positive mappings are not, so anything ambiguous — a bare
 * weight, a unit word with no leading count, a count with no weight — returns `null` rather than
 * guessing.
 *
 * Unit-word recognition covers English and Dutch (owner confirmed 2026-08-14: OFF `serving_size`
 * text for products in the Netherlands is legitimately Dutch). This is input recognition only —
 * every recognized word maps to the same canonical [PortionUnitKind], which the app then displays
 * through its English-only UI strings.
 */
object ServingSizeParser {

    private val PATTERN = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s+(\p{L}+)\s*[(,=]?\s*(\d+(?:[.,]\d+)?)\s*(g|ml)\)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val UNIT_WORDS: Map<String, PortionUnitKind> = mapOf(
        // English
        "slice" to PortionUnitKind.SLICE, "slices" to PortionUnitKind.SLICE,
        "piece" to PortionUnitKind.PIECE, "pieces" to PortionUnitKind.PIECE,
        "biscuit" to PortionUnitKind.BISCUIT, "biscuits" to PortionUnitKind.BISCUIT,
        "cookie" to PortionUnitKind.COOKIE, "cookies" to PortionUnitKind.COOKIE,
        "bar" to PortionUnitKind.BAR, "bars" to PortionUnitKind.BAR,
        "roll" to PortionUnitKind.ROLL, "rolls" to PortionUnitKind.ROLL,
        "scoop" to PortionUnitKind.SCOOP, "scoops" to PortionUnitKind.SCOOP,
        "sachet" to PortionUnitKind.SACHET, "sachets" to PortionUnitKind.SACHET,
        "serving" to PortionUnitKind.SERVING, "servings" to PortionUnitKind.SERVING,
        "portion" to PortionUnitKind.SERVING, "portions" to PortionUnitKind.SERVING,
        // Dutch — input recognition only, see class doc.
        "sneetje" to PortionUnitKind.SLICE, "sneetjes" to PortionUnitKind.SLICE,
        "stuk" to PortionUnitKind.PIECE, "stuks" to PortionUnitKind.PIECE,
        "koekje" to PortionUnitKind.COOKIE, "koekjes" to PortionUnitKind.COOKIE,
        "reep" to PortionUnitKind.BAR, "repen" to PortionUnitKind.BAR,
        "bolletje" to PortionUnitKind.ROLL, "bolletjes" to PortionUnitKind.ROLL,
        "broodje" to PortionUnitKind.ROLL, "broodjes" to PortionUnitKind.ROLL,
        "schepje" to PortionUnitKind.SCOOP, "schepjes" to PortionUnitKind.SCOOP,
        "zakje" to PortionUnitKind.SACHET, "zakjes" to PortionUnitKind.SACHET,
        "portie" to PortionUnitKind.SERVING, "porties" to PortionUnitKind.SERVING,
    )

    fun parse(rawServingSize: String?): ParsedServingSize? {
        val match = PATTERN.find(rawServingSize?.trim().orEmpty()) ?: return null
        val (countText, word, weightText, unit) = match.destructured

        val kind = UNIT_WORDS[word.lowercase()] ?: return null
        val count = PortionParser.parse(countText) ?: return null
        val weight = PortionParser.parse(weightText) ?: return null
        if (count.signum() <= 0 || weight.signum() <= 0) return null

        val amountPerUnit = weight.divide(count, 4, RoundingMode.HALF_UP).stripTrailingZeros()
        val basis = if (unit.equals("ml", ignoreCase = true)) {
            NutritionBasis.PER_100_ML
        } else {
            NutritionBasis.PER_100_G
        }

        return ParsedServingSize(kind = kind, amountPerUnit = amountPerUnit, basis = basis)
    }
}
