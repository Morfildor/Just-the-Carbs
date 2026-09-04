package app.justthecarbs.ocr

/**
 * Where the nutrition declaration stops and the rest of the package begins.
 *
 * ## The failure this exists for
 *
 * On both Korean sauce captures the ingredient list classified as nutrition. The row
 *
 * ```
 * pepper powder, water, salt, garlic, onion), wheat, alcohol, brown sugar, minced garlic
 * ```
 *
 * names **brown sugar**, which is a carbohydrate child term, so [RowClassifier] typed it
 * `CARBOHYDRATE_CHILD` and it took part in the document's structure — it is a row the interpreter
 * counted, the recovery screen could reach, and the merged-row guard had to reason about.
 *
 * That is not a vocabulary problem and must not be fixed as one. "Brown sugar" genuinely is sugar;
 * the ingredient list is genuinely not a nutrition table. The two facts are separated by *where the
 * text is*, not by what it says, and every attempt to separate them by wording would either drop a
 * real sugars row or admit a real ingredient list.
 *
 * ## The rule
 *
 * A nutrition declaration ends at the first structural boundary the package prints — the ingredient
 * list, an allergen or "contains" statement, storage instructions, or the manufacturer's address.
 * Everything from that row onward is package text, whatever nutrient words it happens to contain.
 *
 * ## Why this is conservative rather than clever
 *
 * The boundary must be **found**, not assumed: a label with no ingredient list has no boundary, and
 * then nothing changes at all. And it is only ever consulted *after* the nutrition rows, so a
 * declaration printed below an ingredient list — which does happen on multi-panel packaging — is
 * protected by requiring the boundary to be the *last* candidate row, not the first.
 *
 * That last point is load-bearing and was the difference between a safe rule and a destructive one:
 * keying on the first match would cut the declaration off on any package printing its ingredients
 * above the table.
 */
internal object DeclarationBoundary {

    /**
     * The index of the first row that is package text rather than nutrition, or null when the
     * document prints no boundary.
     *
     * Null is the ordinary answer for a European table photographed on its own, and it means the
     * pre-existing behaviour: every row is eligible, exactly as before.
     */
    fun indexOf(rows: List<LogicalRow>): Int? {
        val candidates = rows.indices.filter { index ->
            val normalized = NutritionTerminology.normalize(rows[index].text)
            BOUNDARY_TERMS.any { NutritionTerminology.containsTerm(normalized, it) }
        }
        if (candidates.isEmpty()) return null

        // The **earliest** boundary that still leaves a nutrition declaration above it.
        //
        // Neither "first" nor "last" alone is right, and both were tried:
        //
        // - *First* cuts the declaration off on a package printing its ingredients above the table,
        //   which loses the table entirely — far worse than the failure being fixed.
        // - *Last* is what shipped in the first attempt here, and it does not work: the Korean sauce
        //   prints `ingredients:` at row 8 and `BEST BEFORE` at row 17, so the boundary landed at
        //   17 and the ingredient row at 9 — the one naming "brown sugar" — stayed inside the
        //   declaration. Measured, not predicted.
        //
        // Requiring a nutrient row above the boundary is what makes "earliest" safe: a boundary with
        // no declaration above it is not the end of a declaration, so it is skipped and a later one
        // is tried. That preserves the ingredients-above-table case for exactly the reason "last"
        // was chosen for it, without letting a late boundary swallow the ingredient list.
        val boundary = candidates.firstOrNull { candidate ->
            candidate > 0 &&
                rows.take(candidate).count { namesANutrient(it) } >= MIN_DECLARATION_ROWS
        }

        return boundary
    }

    /**
     * How many nutrient rows must stand above a boundary for it to be ending a *declaration*.
     *
     * ## Why one is not enough
     *
     * A nutrition declaration prints several nutrients — energy, fat, carbohydrate, sugars, protein,
     * salt. A single row naming one nutrient is as likely to be a sentence as a table, and on the
     * Baltic tortilla (`20260903-212828-161`) it was: the storage advice
     *
     * ```
     * soga Fendes inden for 3 dage. aanwezige Bewaaradvies: suikers. Kamertemperatuur. Verpakt o
     * ```
     *
     * names `suikers` and carries the number `3` — from *3 dage*, three days — so it passed the
     * value test as a one-row "declaration". That let the boundary fire at the ingredient list
     * printed above the table, and the table's own header 5 rows below was then past the boundary.
     *
     * Two is the smallest number that means "a table", and it is deliberately not larger: a
     * short declaration is real, and requiring more would start losing boundaries on labels that
     * print only a few nutrients.
     */
    private const val MIN_DECLARATION_ROWS = 2

    /**
     * Whether [row] states a nutrient **declaration**, i.e. could be the table this boundary ends.
     *
     * ## Why a nutrient word is not enough, and why that circularity mattered
     *
     * This test used to be `RowClassifier.classify(row)` naming a nutrient. That is circular: the
     * classifier types a row `CARBOHYDRATE_CHILD` for containing a child term anywhere along it,
     * which is exactly the over-reach the boundary exists to undo. So a paragraph of package prose
     * could satisfy "there is a declaration above this boundary", and the boundary would then fire
     * on that paragraph and cut off the real table printed below it.
     *
     * Measured on `20260903-212828-161`, the Baltic tortilla. Its marketing paragraph reconstructs
     * as one row at y≈700:
     *
     * ```
     * IStorbritannien. edetortila med fuldkom. Ingredienser: Contains stablser naturally (EA15),
     * occurring Room sugars. termperature.dced Packaged ina protective d package
     * ```
     *
     * It carries the boundary word `Contains` **and** the child term `sugars.`, so it was both the
     * boundary and its own justification. The declaration was therefore declared to end at y≈700 —
     * and the package's actual nutrition header,
     * `Toitevaärtus toote kohta/ Uzturvērtība/ Maistinqumo o deklaracija: 100g`, sits at **y=1481**,
     * 780 px below it. `RowClassifier.classify` types that row `HEADER`; `classifyAll` demoted it to
     * `OTHER` for being past the boundary, so **no per-100 column resolved anywhere on the label**.
     * The printed `47 g / 100 g` was then unreachable by every route at once: no automatic reading,
     * no recovery candidate (a cell with no column states no basis) and no focused entry (which
     * requires an established basis).
     *
     * ## The fix
     *
     * A declaration states quantities. A nutrient row above the boundary must therefore carry a
     * value cell, or be a basis header — which is a structural claim in its own right and is what
     * opens a declaration on a label whose values are printed further down. Prose that merely
     * mentions sugar satisfies neither.
     *
     * This only ever makes the boundary **later** or absent, never earlier, so it cannot newly cut a
     * declaration off. The Korean sauce control is unaffected: the US panel rows printed above its
     * `ingredients:` line carry their own figures, so the boundary still lands there and the
     * brown-sugar row is still excluded.
     */
    private fun namesANutrient(row: LogicalRow): Boolean =
        when (RowClassifier.classify(row)) {
            NutritionRowKind.HEADER -> true
            NutritionRowKind.TOTAL_CARBOHYDRATE, NutritionRowKind.CARBOHYDRATE_CHILD ->
                ColumnOwnership.competingCells(row).isNotEmpty()
            NutritionRowKind.OTHER -> false
        }

    /**
     * Whether the row at [index] lies past the declaration boundary.
     *
     * Callers use this to skip a row entirely rather than to reclassify it, so a row past the
     * boundary contributes nothing to columns, to totals, or to recovery candidates.
     */
    fun isPastBoundary(rows: List<LogicalRow>, index: Int): Boolean {
        val boundary = indexOf(rows) ?: return false
        return index >= boundary
    }

    /**
     * Words that open a non-nutrition section.
     *
     * Every one of these introduces a section by name — they are the package's own structural
     * headings, not incidental words. `ingredient` covers the English and Dutch (`ingrediënten`,
     * which normalizes to `ingredienten`) forms through [NutritionTerminology.containsTerm]'s
     * prefix-free term matching.
     *
     * Deliberately short. A long list of things that "look like" package text would start excluding
     * rows for resembling prose, which is a judgement about wording — the thing this object exists
     * to avoid making.
     */
    private val BOUNDARY_TERMS = listOf(
        "ingredients",
        "ingredient",
        "ingredienten",
        "contains",
        "allergy advice",
        "allergene",
        "store in",
        "keep refrigerated",
        "refrigerate after opening",
        "best before",
        "produced by",
        "product of",
        "manufactured by",
        "distributed by",
    )
}
