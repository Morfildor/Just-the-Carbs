package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.PortionParser

/**
 * The serving size a **linear** nutrition panel declares in words, e.g. `Serv. size: 1 Tbsp (18 g)`.
 *
 * ## Why a linear panel needs this and a table does not
 *
 * A European table prints its basis as a column header, and [ColumnClassifier] reads it there. A US
 * Nutrition Facts panel has no columns at all: it prints one serving declaration at the top and then
 * states every figure against it in running text. Measured on a Sempio Korean sauce, two captures
 * (`docs/Scan evidence 01-09-26 3rd testr/20260901-225752-375` and `-225813-635`):
 *
 * ```
 * Nutrition Facts Servings: 13, Serv. size: 1Tbsp
 * (18 g), Amount per serving: Calories 35, Total
 * ...
 * DV), Total Carb. 6 g (2% DV), Fiber 1 g (4% D),
 * ```
 *
 * The printed `6 g` is per 18 g. Before this existed the app had no way to say so: `6` was refused
 * because no per-100 column resolved, the user was sent to recovery, and recovery asked
 * *"6 g carbs — per what?"* offering `/100 g` and `/100 ml`. Both answers are wrong — the figure is
 * neither — and one of them was one tap away from a stored product 5.5x too low.
 *
 * ## What makes this a reading rather than a guess
 *
 * Three facts must all be printed, in this order, for a declaration to be recognised:
 *
 * 1. a **serving phrase** (`serv. size`, `serving size`, `per serving`, `amount per serving`);
 * 2. a **mass or volume** in a unit this app has, at most [MAX_LOOKAHEAD_ROWS] rows later;
 * 3. nothing contradicting it — two different masses found means [of] returns null.
 *
 * The unit is required. A serving declared as `1 Tbsp` with no mass is a real shape and yields
 * [CarbBasis.PerUnknownServing], never a mass invented from a name — this app has no table of what a
 * tablespoon of anything weighs and must not acquire one.
 *
 * ## Where it may be used
 *
 * [RecoveryCandidates] consults it **only** when the document resolved no per-100 column anywhere. A
 * label that states a per-100 basis is a table, and its own headers decide what its cells mean; a
 * serving sentence printed elsewhere on the package must never override them.
 */
internal object ServingDeclaration {

    /**
     * The serving basis this document declares, or null when it declares none unambiguously.
     *
     * Null is the safe answer and the common one. It means the caller offers no serving-based
     * candidate, which is exactly the behaviour that existed before this object.
     */
    fun of(rows: List<LogicalRow>): CarbBasis? {
        val declarations = rows.indices.mapNotNull { index -> declarationAt(rows, index) }
        if (declarations.isEmpty()) return null

        // Several rows can name a serving on a dense panel — "Serv. size:" and "Amount per serving:"
        // are both present on the Korean sauce, and both find the same `(18 g)`. Agreement is
        // ordinary; disagreement is not, and the app must not choose between two stated sizes.
        val masses = declarations.mapNotNull { it as? CarbBasis.PerQuantity }
            .distinctBy { it.quantity.stripTrailingZeros() to it.unit }
        return when {
            masses.size > 1 -> null
            masses.size == 1 -> masses.single()
            // Every declaration named a serving and none stated a mass.
            else -> declarations.first()
        }
    }

    /**
     * The declaration beginning at row [index], or null when that row starts none.
     *
     * The mass may be on the same row or on one of the next [MAX_LOOKAHEAD_ROWS]: ML Kit wraps a
     * printed line wherever the frame ends, and on the Korean sauce the `(18 g)` lands at the start
     * of the *following* recognised row from the `Serv. size:` that introduces it.
     */
    private fun declarationAt(rows: List<LogicalRow>, index: Int): CarbBasis? {
        val row = rows[index]
        val normalized = NutritionTerminology.normalize(row.text)
        val phrase = SERVING_PHRASES.find { normalized.contains(it) } ?: return null

        // The mass must come *after* the phrase that introduces it, on this row or on the next.
        // Searching the whole row from its start would let a mass printed to the left of the phrase
        // — another nutrient's figure on a linear panel — be adopted as the serving size.
        //
        // Bounded to [MAX_NUTRIENT_FREE_LOOKAHEAD] characters past the phrase, and stopped at the
        // first nutrient name, for the same reason. Measured on the Korean sauce: `Amount per
        // serving:` is followed on its own row by `Calories 35, Total`, and on the next by
        // `Fat 0.5 g (1 % DV)` — so an unbounded forward search adopts **0.5 g** as the serving mass
        // and every figure on the panel becomes twelve times too large. `Calories` and `Fat` are
        // both in [NutrientRowSegments]' boundary vocabulary, which is what stops the walk.
        massBefore(afterPhrase(normalized, phrase))?.let { return it }

        for (offset in 1..MAX_LOOKAHEAD_ROWS) {
            val next = rows.getOrNull(index + offset) ?: break
            val nextText = NutritionTerminology.normalize(next.text)
            // The mass may be printed at the *start* of the following row, before that row's own
            // declaration phrase — which is exactly the Korean sauce's shape: row 0 ends
            // `Serv. size: 1Tbsp` and row 1 begins `(18 g), Amount per serving:`. Searching only up
            // to the next phrase is what finds the 18 g while still refusing to reach past it into
            // the following declaration's figures.
            val beforeNextPhrase = SERVING_PHRASES
                .filter { nextText.contains(it) }
                .minOfOrNull { nextText.indexOf(it) }
                ?.let { nextText.take(it) }
                ?: nextText
            massBefore(beforeNextPhrase)?.let { return it }
            if (SERVING_PHRASES.any { nextText.contains(it) }) break
        }

        // A serving was named and no mass was found with it. That is a real shape, and saying so is
        // what lets the UI show "6 g per serving" rather than inventing a quantity.
        return CarbBasis.PerUnknownServing(null)
    }

    /** [text] from just past [phrase] onward. */
    private fun afterPhrase(text: String, phrase: String): String =
        text.substring((text.indexOf(phrase) + phrase.length).coerceAtMost(text.length))

    /**
     * [massIn] applied to the part of [text] before the first nutrient name.
     *
     * A serving size is printed with the serving declaration, never after the first nutrient has
     * been named. Truncating there is what keeps `Fat 0.5 g` and `Calories 35` — which follow the
     * phrase on the very same recognised row of a US panel — from being read as the serving mass.
     */
    private fun massBefore(text: String): CarbBasis.PerQuantity? {
        val cut = NUTRIENT_BOUNDARY.find(text)?.range?.first ?: text.length
        return massIn(text.take(cut))
    }

    /**
     * The first nutrient name in a normalized string.
     *
     * Deliberately a small, explicit list rather than the whole terminology vocabulary: this is a
     * *stopping* rule, so a word that appears here can only ever make a serving declaration narrower
     * — never let a wrong mass in. Every entry is a heading a nutrition panel prints immediately
     * after its serving declaration.
     */
    private val NUTRIENT_BOUNDARY = Regex(
        "(?:^|\\s)(?:calories|calorieen|energie|energy|fat|vetten|total fat|sat|cholest|sodium|" +
            "salt|zout|protein|carb|carbohydrate|koolhydraten|sugars|fiber|fibre)(?:$|[\\s.,:;])",
    )

    /**
     * The first `<number> <unit>` in [text] as a serving mass, or null.
     *
     * Excludes a leading count: `1 Tbsp (18 g)` states a count of one and a mass of 18 g, and only
     * the mass is a quantity this app can divide by. That falls out of requiring a unit — `Tbsp` is
     * not one — rather than from a rule about counts.
     */
    private fun massIn(text: String): CarbBasis.PerQuantity? {
        val match = MASS.find(text) ?: return null
        val amount = PortionParser.parse(match.groupValues[1]) ?: return null
        if (amount.signum() <= 0) return null
        val unit = NutritionTerminology.basisUnitFor(match.groupValues[2]) ?: return null
        return CarbBasis.PerQuantity(amount, unit, servingWord = SERVING_LABEL)
    }

    /**
     * A number followed by a basis unit.
     *
     * The trailing boundary is required so `g` cannot match inside `gram`, the same rule every other
     * pattern built on [app.justthecarbs.domain.BasisUnitSpellings] follows.
     */
    private val MASS = Regex(
        "(?<!\\d)(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(${NutritionTerminology.basisUnitAlternation})(?:$|\\s)",
    )

    /**
     * Phrases that introduce a serving size, normalized.
     *
     * Deliberately phrases and not single words. A bare "serving" appears in marketing copy and in
     * the reference-intake footnote on European packaging; "serv size" and "amount per serving" are
     * headings a nutrition panel prints and prose does not.
     */
    private val SERVING_PHRASES = listOf(
        "amount per serving",
        "serving size",
        "serv size",
        "serv. size",
        "per serving",
        "per portion",
    ).map { NutritionTerminology.normalize(it) }

    /** How a serving basis names itself to the user. */
    private const val SERVING_LABEL = "serving"

    /**
     * How far past the introducing phrase the mass may be printed, in reconstructed rows.
     *
     * One row covers the Korean sauce's wrap. Two is the bound rather than one because a dense panel
     * can put the `Servings: 13,` count between them; beyond that the search would start reaching
     * into a different part of the label.
     */
    private const val MAX_LOOKAHEAD_ROWS = 2
}
