package app.justthecarbs.ocr

/**
 * Decides, on a [NutritionRowKind.TOTAL_CARBOHYDRATE] row, which numbers were introduced by the
 * carbohydrate term and which were introduced by some *other* nutrient printed on the same row.
 *
 * ### The class of label this exists for
 *
 * A **running nutrition sentence** rather than a table: "…of which saturates: 19 g, Carbohydrate: 3
 * g, of which sugars: 2,5 g…". Printed line breaks fall wherever the text happens to wrap, so one
 * reconstructed row routinely carries the tail of the previous nutrient's clause and the head of the
 * carbohydrate clause. The row types `TOTAL_CARBOHYDRATE` correctly — it really does name
 * carbohydrate — but its leftmost number is the fat figure from the clause above.
 *
 * The existing column machinery has nothing to separate the two numbers with. It only knows which
 * x-band to read from, and on the real packaging in this repo's fixtures the preceding nutrient's
 * figure sat 21 px from the basis column's centre while the true carbohydrate figure sat 336 px
 * away, outside it. **No distance rule can separate those; reading order can.**
 *
 * ### The rule
 *
 * Printed nutrition text — table row or sentence — names a nutrient and then states its value. So a
 * number belongs to the **nearest nutrient name to its left** on the same row. When that name is not
 * a carbohydrate term, the number is not this row's carbohydrate figure.
 *
 * Anchoring on "the nearest nutrient name to the left" rather than "left of the carbohydrate term"
 * is deliberate, and the difference is load-bearing. A table may legitimately print its label column
 * to the **right** of its value columns, and there the carbohydrate figure is left of the term while
 * no other nutrient name precedes it at all — so it survives. A number is only ever excluded because
 * something else on the row actually claimed it.
 *
 * ### Why this is conservative
 *
 * It can only ever remove candidates, never invent one. A row whose every number is claimed by
 * another nutrient contributes nothing and the label falls through to `NotFound`, which the owner
 * ranks far above a confident wrong value. And it is inert on the ordinary table row, where the only
 * nutrient named is the carbohydrate itself.
 */
internal object CarbohydrateTermAnchor {

    /**
     * The x positions on [row] at which a nutrient name begins, paired with whether that name is a
     * carbohydrate term. Sorted left to right, empty when the row names no nutrient at all.
     */
    fun nutrientAnchors(row: LogicalRow): List<Anchor> {
        val words = mutableListOf<String>()
        val ownerOfWord = mutableListOf<Int>()
        row.elements.forEachIndexed { index, element ->
            NutritionTerminology.normalize(element.text)
                .split(' ')
                .filter { it.isNotBlank() }
                .forEach {
                    words += it
                    ownerOfWord += index
                }
        }
        if (words.isEmpty()) return emptyList()

        // Word-level rather than element-level because a term can span elements ("ogljikovi
        // hidrati") and one element can carry several words ("Koolhydraten/Glucides/Kohlenhydrate"
        // normalizes to three). The match's START word decides the anchor — matching anywhere in a
        // window of following elements would report the element *before* a term as naming it, which
        // on the wrapped-sentence row this exists for puts the anchor after the very number it must
        // exclude.
        val anchors = mutableListOf<Anchor>()
        fun record(terms: Collection<String>, isCarbohydrate: Boolean, isChild: Boolean = false) {
            terms.forEach { term ->
                val termWords = NutritionTerminology.normalize(term).split(' ').filter { it.isNotBlank() }
                if (termWords.isEmpty()) return@forEach
                for (start in 0..words.size - termWords.size) {
                    if (termWords.indices.all { words[start + it] == termWords[it] }) {
                        anchors += Anchor(
                            left = row.elements[ownerOfWord[start]].box.left,
                            isCarbohydrate = isCarbohydrate,
                            isChild = isChild,
                        )
                    }
                }
            }
        }
        record(NutritionTerminology.carbohydrateTerms, isCarbohydrate = true)
        record(NutritionTerminology.exclusionTerms, isCarbohydrate = false, isChild = true)
        record(OTHER_NUTRIENT_TERMS, isCarbohydrate = false)

        return anchors.sortedBy { it.left }
    }

    /**
     * Whether a number whose box ends at [right] was introduced by a carbohydrate term.
     *
     * True when the row names no nutrient before it at all, which is the label-column-on-the-right
     * table and the ordinary case where nothing disputes the number.
     */
    fun isCarbohydrateValue(anchors: List<Anchor>, right: Int): Boolean =
        anchors.lastOrNull { it.left < right }?.isCarbohydrate ?: true

    /**
     * A nutrient name's left edge, and what kind of nutrient it names.
     *
     * [isChild] separates a *carbohydrate child* (sugars, polyols, fibre, starch) from an unrelated
     * nutrient (fat, protein, salt). Both are `isCarbohydrate = false` and both equally disqualify a
     * number they introduce, so value binding does not care which it is — but
     * [MergedTotalRowRecovery] does: only a child term explains why [RowClassifier] excluded a row,
     * and so only a child term may bound a recovered total span.
     */
    data class Anchor(val left: Int, val isCarbohydrate: Boolean, val isChild: Boolean = false)

    /**
     * Nutrient names that are neither carbohydrate nor one of its children, so that a figure they
     * introduce is never mistaken for the carbohydrate figure on a wrapped prose row.
     *
     * **Every form here was read out of this repo's nine real fixtures' actual ML Kit output**, not
     * added speculatively for language coverage: Dutch/German/French/English/Danish/Swedish/Turkish
     * fat, saturates, protein, salt and energy words all appear verbatim in those recognitions
     * because the packages are multilingual. Diacritics are written out for reviewability and
     * normalized away on both sides before matching, exactly as in [NutritionTerminology].
     *
     * Deliberately *not* exhaustive and deliberately not a new public vocabulary: an unlisted
     * nutrient simply leaves the number unclaimed, which is the pre-existing behaviour. Missing a
     * term costs nothing that was not already lost; inventing one that collides with a carbohydrate
     * word would cost a correct reading, so nothing unobserved is guessed at.
     */
    internal val OTHER_NUTRIENT_TERMS: Set<String> = setOf(
        // fat
        "vetten", "vet", "fett", "fedt", "fat", "matieres grasses", "matières grasses",
        "lipides", "yag", "yağ", "grasse", "grasses",
        // saturates
        "verzadigde vetzuren", "vetzuren", "gesattigte fettsauren", "gesättigte fettsäuren",
        "fettsauren", "fettsäuren", "acides gras satures", "acides gras saturés", "gras satures",
        "gras saturés", "saturates", "mattede fedtsyrer", "maettede fedtsyrer", "fedtsyrer",
        "mattat fett", "onverzadigde vetzuren",
        // protein
        "eiwitten", "eiwit", "eiweiss", "eiweiß", "protein", "proteine", "protéines", "proteines",
        "proteini", "protéine",
        // salt
        "zout", "salz", "sel", "salt", "sol",
        // energy
        "energie", "energy", "energi", "brennwert", "energy value", "valeur energetique",
        "valeur énergétique",
    )
}
