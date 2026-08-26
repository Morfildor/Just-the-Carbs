package app.justthecarbs.domain

/**
 * Decides whether a remote product is measured in **grams or millilitres** — or admits it cannot
 * tell (release pass §3).
 *
 * ## Why this is not a labelling detail
 *
 * The basis is not a conversion factor and never becomes one (§17): the arithmetic
 * `portion / 100 × carbsPer100` is identical whichever it is. What it decides is the **unit the
 * portion field asks the user for**. Get it wrong on a liquid and the app asks for grams; a user who
 * complies — by weighing 250 ml of a syrup that weighs 330 g — types a number a third too large into
 * a field the app then treats as authoritative, and the carbohydrate figure they dose from is a
 * third too high. Nothing downstream can detect this, because every stage did its job correctly on
 * the input it was given.
 *
 * The previous behaviour was `PackageQuantityParser.inferBasis`, which returned `PER_100_G` whenever
 * the free-text quantity was unreadable. Its own comment argued the default was safe because the app
 * never converts between units — true of the arithmetic, and beside the point once a human is asked
 * to measure something.
 *
 * ## Resolution order
 *
 * 1. **Open Food Facts' structured unit** (`product_quantity_unit`). OFF normalises the free text
 *    itself, so this is the one field that survives "390 gram", "1,5 liter" and multipack notation
 *    without this app guessing at grammar.
 * 2. **An unambiguous free-text quantity** — [PackageQuantityParser] for a single quantity, and
 *    otherwise the consistency rule below.
 * 3. **[Unresolved]**. The caller routes the user to the existing safe path, where the basis is a
 *    visible, changeable choice rather than something the app decided quietly.
 *
 * ## The consistency rule
 *
 * A multipack like `6 x 33 cl` states no single package size — whether "the package" is one bottle
 * or the crate is exactly the ambiguity [PackageQuantityParser] refuses to resolve, and this class
 * does not resolve it either. But it is not ambiguous about *centilitres*: every measurement unit in
 * that string maps to millilitres, so the basis follows without a guess.
 *
 * So: a basis is read from free text only when **every** unit token attached to a number agrees.
 * `250 g / 300 ml` disagrees and stays [Unresolved]. Text with no unit token at all is
 * [Unresolved] — that is the case the old default silently answered "grams".
 *
 * Only the unit spellings [PackageQuantityParser] already accepts are recognised here. Adding
 * "gram", "liter" and friends is a separate decision with its own false-positive surface, and step 1
 * already covers the products that need it.
 */
object PackageBasisResolver {

    sealed interface Resolution {
        /** The basis is known. [PackageQuantity] is present only when a *single* size was stated. */
        data class Resolved(val basis: NutritionBasis, val quantity: PackageQuantity?) : Resolution

        /** Not established. Never substitute a default for this — ask the user (§13). */
        data object Unresolved : Resolution
    }

    /**
     * A number immediately followed by a unit. Anchored on the digit so a stray "l" inside a word
     * cannot be read as litres, and `\b` at the end so "gram" is not matched as "g".
     */
    private val UNIT_TOKEN = Regex(
        """\d\s*(mg|kg|g|ml|cl|dl|l)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param structuredUnit OFF's `product_quantity_unit`, e.g. `"g"` or `"ml"`.
     * @param freeTextQuantity OFF's `quantity`, e.g. `"500 ml"` or `"6 x 33 cl"`.
     */
    fun resolve(structuredUnit: String?, freeTextQuantity: String?): Resolution {
        // The single package size, when one was stated plainly. Reused by both resolved branches so
        // that adding the structured unit never changes which products get pack shortcuts (§14) —
        // this pass is about the basis, and quietly starting to treat a multipack's total as "the
        // package" would be a different change with a different risk.
        val parsed = PackageQuantityParser.parse(freeTextQuantity)

        // What the printed text says about units, when it says one thing consistently.
        val fromText = consistentBasisIn(freeTextQuantity)

        basisOfUnit(structuredUnit)?.let { structured ->
            // A structured unit contradicting the printed text means the two sources disagree about
            // what the product even is. Refusing is the only honest outcome: there is no evidence
            // for preferring either, and picking one is the guess this class exists to stop.
            //
            // Both signals are checked, not just the parsed quantity. OFF derives its structured
            // unit from this same free text, so a `product_quantity_unit` of "g" against a printed
            // "6 x 33 cl" — which parses to no quantity at all, and so would slip past a
            // parsed-only check — is a corrupt record, not a normalisation.
            if (parsed != null && parsed.basis != structured) return Resolution.Unresolved
            if (fromText != null && fromText != structured) return Resolution.Unresolved
            return Resolution.Resolved(structured, parsed)
        }

        if (parsed != null) return Resolution.Resolved(parsed.basis, parsed)

        return if (fromText == null) {
            Resolution.Unresolved
        } else {
            Resolution.Resolved(fromText, quantity = null)
        }
    }

    private fun basisOfUnit(unit: String?): NutritionBasis? =
        unit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(UNITS::get)

    private fun consistentBasisIn(text: String?): NutritionBasis? {
        if (text.isNullOrBlank()) return null
        val bases = UNIT_TOKEN.findAll(text)
            .mapNotNull { UNITS[it.groupValues[1].lowercase()] }
            .toSet()
        return bases.singleOrNull()
    }

    /** Deliberately the same spellings [PackageQuantityParser] accepts, and no more. */
    private val UNITS: Map<String, NutritionBasis> = mapOf(
        "mg" to NutritionBasis.PER_100_G,
        "g" to NutritionBasis.PER_100_G,
        "kg" to NutritionBasis.PER_100_G,
        "ml" to NutritionBasis.PER_100_ML,
        "cl" to NutritionBasis.PER_100_ML,
        "dl" to NutritionBasis.PER_100_ML,
        "l" to NutritionBasis.PER_100_ML,
    )
}
