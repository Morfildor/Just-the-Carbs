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

    // `inferBasis(quantity)` used to live here, returning PER_100_G for anything it could not read.
    // It was removed in the 2026-08-26 release pass; see PackageBasisResolver, which replaces it and
    // records why a grams default is not the harmless labelling choice the old comment claimed.
    // Do not reintroduce a total function from quantity text to a basis — the whole point is that
    // the function is partial.
}
