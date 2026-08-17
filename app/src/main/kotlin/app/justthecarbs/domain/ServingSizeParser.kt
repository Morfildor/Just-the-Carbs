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

    /** count + unit word, with an optional bracketed weight. The weight group is now optional. */
    private val DESCRIPTOR = Regex(
        """^\s*(?:(\d+(?:[.,]\d+)?)\s*)?(\p{L}+)\s*(?:[(,=]?\s*(\d+(?:[.,]\d+)?)\s*(g|ml)\)?\s*)?$""",
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
        // "schaaltje" is a small dish or bowl — a serving vessel, not a countable item with a shape
        // of its own, so it maps to SERVING exactly as "portie" does. Added 2026-08-17 after a real
        // Dutch yoghurt package printed its per-serving column header as "schaaltje (150 g)"; the
        // word is ordinary Dutch for a single-serve pot, not specific to that brand.
        "schaaltje" to PortionUnitKind.SERVING, "schaaltjes" to PortionUnitKind.SERVING,
    )

    /** Recognises a unit word in isolation — used by the OCR column-header path (spec §4). */
    internal fun kindForWord(word: String): PortionUnitKind? = UNIT_WORDS[word.lowercase().trim()]

    /**
     * What the string describes, weight optional.
     *
     * A bare weight ("30 g") is still rejected: it names no countable unit, so there is nothing to
     * count. An unrecognised word is rejected rather than guessed — a false unit mapping is worse
     * than no mapping.
     */
    fun parseDescriptor(rawServingSize: String?): ServingDescriptor? {
        val raw = rawServingSize?.trim().orEmpty()
        val match = DESCRIPTOR.find(raw) ?: return null
        val countText = match.groupValues[1]
        val word = match.groupValues[2]
        val weightText = match.groupValues[3]
        val unit = match.groupValues[4]

        val kind = kindForWord(word) ?: return null
        // A weight with no leading count states no count-to-quantity relationship: "portion 25 g"
        // does not say how many portions 25 g is, so it stays a rejection exactly as before. A bare
        // "slice" is different — it names one unit and claims no weight at all.
        if (countText.isEmpty() && weightText.isNotEmpty()) return null
        val count = if (countText.isEmpty()) BigDecimal.ONE else PortionParser.parse(countText) ?: return null
        if (count.signum() <= 0) return null

        val weightOrVolume = if (weightText.isEmpty()) {
            null
        } else {
            val weight = PortionParser.parse(weightText) ?: return null
            if (weight.signum() <= 0) return null
            AmountWithBasis(
                amount = weight,
                basis = if (unit.equals("ml", ignoreCase = true)) {
                    NutritionBasis.PER_100_ML
                } else {
                    NutritionBasis.PER_100_G
                },
            )
        }

        return ServingDescriptor(kind = kind, count = count, weightOrVolume = weightOrVolume, rawText = raw)
    }

    /**
     * The weight-backed mapping only. Unchanged contract: a string with no printed weight still
     * returns null here, because this function's whole promise is a weight relationship. Callers
     * that can work without one use [parseDescriptor].
     */
    fun parse(rawServingSize: String?): ParsedServingSize? {
        val descriptor = parseDescriptor(rawServingSize) ?: return null
        val perUnit = descriptor.amountPerUnit ?: return null
        return ParsedServingSize(kind = descriptor.kind, amountPerUnit = perUnit.amount, basis = perUnit.basis)
    }
}
