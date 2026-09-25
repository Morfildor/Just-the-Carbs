package app.justthecarbs.ui.components

/**
 * The plural count for a spoken "N grams" phrase built from a figure as displayed.
 *
 * English uses the singular only for a bare 1 ("1 gram of carbs"); a figure shown with a decimal
 * stays plural ("1.0 grams"), as does every other figure. Taking the displayed text rather than
 * the number keeps speech and screen in agreement (2026-09-25 review).
 */
fun spokenGramsQuantity(figure: String): Int = if (figure == "1") 1 else 2
