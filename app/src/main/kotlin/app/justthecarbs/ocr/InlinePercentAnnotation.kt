package app.justthecarbs.ocr

/**
 * Tells a reference-percent **column header** apart from an inline `% DV` **annotation**.
 *
 * ## The two things that look alike
 *
 * A European table prints its reference-intake column the way it prints any column — one header
 * token standing over a stack of cells:
 *
 * ```
 * Voedingswaarde      100 g    portie    %RI
 * Koolhydraten        72,0 g   22,5 g     9%
 * ```
 *
 * A US Nutrition Facts panel prints the same information with no columns at all, as an annotation
 * attached to each nutrient's own value, inside a running clause:
 *
 * ```
 * Total Fat 0.5 g (1 % DV), Sat. Fat 0 g (0 % D), Trans Fat 0 g,
 * Cholest. 0 mg (0 % DV), Sodium 500 mg (22 % DV), Total Carb. 6 g (2 % DV), Fiber 1 g (4 % DV)
 * ```
 *
 * Both contain the reference-intake vocabulary, so a rule keyed on that vocabulary alone treats the
 * second as eight column headers. Measured on `docs/Scan Evidence 02-09/20260902-085611-201`: eight
 * `REFERENCE_PERCENT` columns at x=351, 524, 738, 744, 817, 1078, 1232 and 1452, on a panel whose
 * printed layout has one column. `Total Carb. 6g` bound to the nearest of them and was refused.
 *
 * ## What separates them, and why it is not position
 *
 * A column header **heads** something: it names a kind of figure and nothing else, and the figures
 * live below it. An annotation sits *beside* the value it annotates, on the same printed line, in a
 * clause that also names the nutrient.
 *
 * So the question asked here is structural: **does this span share its row with a nutrient name and
 * that nutrient's own value?** If it does, it is an annotation — whatever its x position, whatever
 * the rest of the label looks like. That keeps the rule independent of how the panel is framed, how
 * ML Kit happened to break the lines, and how many clauses landed on one recognised row.
 *
 * The European case is unaffected because its `%RI` header row contains no nutrient name and no
 * value: it is a header row, which is exactly what makes it a header.
 */
internal object InlinePercentAnnotation {

    /**
     * Whether [span] is an annotation inside a nutrient clause rather than a column header.
     *
     * Requires **both** signals, because either alone has a false positive:
     *
     * - a nutrient name on the same row — but a multilingual header row can carry a stray word that
     *   normalizes into the nutrient vocabulary;
     * - a value-shaped token on the same row — but a header row may print `100 g`, which is
     *   value-shaped.
     *
     * Together they describe a clause: *this nutrient, this much of it, this percentage of the
     * reference intake*. That is not a header under any layout.
     */
    fun isInlineAnnotation(row: LogicalRow, span: List<OcrElement>): Boolean {
        // The whole row, not the elements outside the span.
        //
        // The greedy span walk routinely absorbs the nutrient name into the span itself — on the
        // Korean sauce it produced spans reading `Iron (2 % DV),` and `Calcium (0% DV).`, each of
        // which *is* the clause. Asking only about the elements outside such a span finds nothing
        // and concludes it is a header, which is the opposite of the truth. The question is about
        // the printed line the percentage sits on, so the line is what is examined.
        val text = row.elements.joinToString(" ") { it.text }
        val normalized = NutritionTerminology.normalize(text)

        val namesANutrient = NUTRIENT_WORDS.any {
            NutritionTerminology.containsTerm(normalized, it)
        }
        if (!namesANutrient) return false

        // A quantity printed with its unit, somewhere on the row. This is what distinguishes a
        // nutrient clause ("Sodium 500 mg (22 % DV)") from a header row that happens to name a
        // nutrient — a header states no amounts.
        //
        // The percentage's own digits cannot satisfy this, because a bare percentage carries no
        // mass or volume unit; without that the rule would be circular and every percent header
        // would suppress itself.
        if (QUANTITY_WITH_UNIT.containsMatchIn(text)) return true

        // A clause whose only amounts are percentages: `Iron (2 % DV), Potas. (0 % DV)` — the row
        // that survived the mass-unit test on both sauce captures and emitted two more phantom
        // columns. Micronutrients are legitimately printed this way, with no gram figure at all.
        //
        // Requiring **two** such clauses on one row keeps this from firing on a genuine percent
        // header. A header names the column once ("%RI", "% DV"); a clause enumerates nutrient after
        // nutrient. Two nutrient-and-percentage pairs on one recognised line is a sentence, not a
        // heading — and a European table's `%RI` header row names no nutrient at all, so it never
        // reaches this test.
        return NUTRIENT_WITH_PERCENT.findAll(text).count() >= 2
    }

    /**
     * Words that name a nutrient on a Nutrition Facts panel.
     *
     * Deliberately the panel's own vocabulary rather than [NutritionTerminology]'s full carbohydrate
     * lists: the question here is "is this row a nutrient clause", which any nutrient answers, and a
     * carbohydrate-specific list would miss `Total Fat 0.5 g (1 % DV)` — the clause that produced
     * the first phantom column.
     */
    private val NUTRIENT_WORDS = listOf(
        "fat", "sat fat", "trans fat", "cholest", "cholesterol", "sodium", "salt",
        "carb", "total carb", "carbohydrate", "fiber", "fibre", "sugars", "protein",
        "calcium", "iron", "potas", "potassium", "vit", "vitamin", "calories", "energy",
    )

    /**
     * A printed quantity: a number fused to or followed by a mass or volume unit.
     *
     * `0.5 g`, `500 mg`, `6g` and `2 g` all match. A bare percentage does not, which matters: the
     * annotation's own `(1 % DV)` must not count as the value that proves it is an annotation, or
     * the rule would be circular.
     */
    private val QUANTITY_WITH_UNIT =
        Regex("""\d+(?:[.,]\d+)?\s*(?:mg|g|kg|ml|l|mcg)\b""", RegexOption.IGNORE_CASE)

    /**
     * A nutrient name immediately followed by a bracketed percentage — `Iron (2 % DV)`.
     *
     * Used only in the two-or-more form, to recognise a micronutrient clause that prints no mass at
     * all. The bracket is required: it is what makes the percentage an annotation *of that
     * nutrient*, and without it the pattern would match a nutrient name that merely happens to sit
     * on the same row as a percent header.
     */
    private val NUTRIENT_WITH_PERCENT = Regex(
        """[A-Za-z][A-Za-z.]*\s*\(\s*\d+(?:[.,]\d+)?\s*%""",
        RegexOption.IGNORE_CASE,
    )
}
