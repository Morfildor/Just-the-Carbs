package app.justthecarbs.domain

/**
 * The reference quantity a product's carbohydrate figure is declared against.
 *
 * A product is declared per 100 g **or** per 100 ml, never both. The portion the user enters is
 * always expressed in this same unit — the app never converts between them, because that would
 * require a density it does not have (brief §17, design decision 3.1).
 */
enum class NutritionBasis(val unitLabel: String) {
    PER_100_G("g"),
    PER_100_ML("ml"),
}

/**
 * How the two basis units are **spelled on packaging and in Open Food Facts text**, as opposed to
 * how this app displays them ([NutritionBasis.unitLabel] is always the short form).
 *
 * It lives in `domain/` rather than beside the OCR terminology because both layers need it and
 * `domain/` is the one that may not depend on the other: `ServingSizeParser` reads OFF's free-text
 * `serving_size` ("1 plak (20 gram)"), and the OCR stages read a printed column header
 * ("per 100 gram"). Those were separate literals until 2026-08-26, in five places, all spelling the
 * unit `g|ml` and nothing else — so a Dutch or German label that wrote the word out resolved no
 * basis at all and the scan returned `NotFound` with a correct value sitting on a correct row.
 *
 * Deliberately **not** extended to units the app cannot represent. There are exactly two bases; an
 * ounce or a calorie must keep resolving nothing rather than being quietly mapped onto one of them,
 * which is the same rule that governs every other refusal in this codebase.
 */
object BasisUnitSpellings {

    val gram = setOf("g", "gr", "gram", "grams", "gramm", "gramme", "grammes")
    val millilitre = setOf("ml", "milliliter", "millilitre", "milliliters", "millilitres")

    /**
     * Regex alternation, **longest first**, so `gram` is preferred over the `g` that prefixes it.
     * Every pattern using it must still demand a boundary afterwards, or `g` matches inside `gram`.
     */
    val alternation: String = (gram + millilitre).sortedByDescending { it.length }.joinToString("|")

    fun basisFor(word: String): NutritionBasis? = when (word.trim().lowercase()) {
        in gram -> NutritionBasis.PER_100_G
        in millilitre -> NutritionBasis.PER_100_ML
        else -> null
    }
}
