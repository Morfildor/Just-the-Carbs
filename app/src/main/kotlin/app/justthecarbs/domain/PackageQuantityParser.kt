package app.justthecarbs.domain

import java.math.BigDecimal

/** A package size the app is confident about, e.g. `500 ml` (§14 ½ pack / Full pack). */
data class PackageQuantity(
    val amount: BigDecimal,
    val basis: NutritionBasis,
)

/**
 * Reads Open Food Facts' free-text `quantity` field ("500 ml", "1,5 L", "250g").
 *
 * This one field answers two questions at once: how big the package is, and whether the product is
 * measured in grams or millilitres. That second answer matters more — it decides the unit the
 * portion field is locked to, and the app must never convert between the two (§17).
 *
 * Anything it cannot read confidently returns `null`. A multipack like "6 x 33 cl" is deliberately
 * not guessed at: whether "the package" means one bottle or the crate is exactly the sort of
 * ambiguity that should reach the user rather than be resolved by a coin flip (§13).
 */
object PackageQuantityParser {

    private val QUANTITY = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s*(mg|g|kg|ml|cl|dl|l)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Multipliers to the basis unit: grams for solids, millilitres for liquids. */
    private val UNITS: Map<String, Pair<NutritionBasis, BigDecimal>> = mapOf(
        "mg" to (NutritionBasis.PER_100_G to BigDecimal("0.001")),
        "g" to (NutritionBasis.PER_100_G to BigDecimal.ONE),
        "kg" to (NutritionBasis.PER_100_G to BigDecimal("1000")),
        "ml" to (NutritionBasis.PER_100_ML to BigDecimal.ONE),
        "cl" to (NutritionBasis.PER_100_ML to BigDecimal("10")),
        "dl" to (NutritionBasis.PER_100_ML to BigDecimal("100")),
        "l" to (NutritionBasis.PER_100_ML to BigDecimal("1000")),
    )

    fun parse(text: String?): PackageQuantity? {
        val match = QUANTITY.find(text?.trim().orEmpty()) ?: return null
        val (number, unit) = match.destructured

        val amount = PortionParser.parse(number) ?: return null
        if (amount.signum() <= 0) return null

        val (basis, multiplier) = UNITS[unit.lowercase()] ?: return null
        return PackageQuantity(amount = amount.multiply(multiplier).stripTrailingZeros(), basis = basis)
    }

    /**
     * The measurement basis for a product, inferred from its declared quantity.
     *
     * Falls back to grams when the quantity is unreadable. That default is a labelling choice, not
     * a calculation one — the arithmetic is identical either way, because the app never converts
     * between units. A mislabelled basis is corrected by the user in the Verify flow (§23), which
     * is why an unreadable quantity does not make the product unusable.
     */
    fun inferBasis(quantity: String?): NutritionBasis =
        parse(quantity)?.basis ?: NutritionBasis.PER_100_G
}
