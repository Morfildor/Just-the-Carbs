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
