package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis

/**
 * The per-100 basis a label *stated*, independently of whether any value could be read (1.0.3 P1).
 *
 * ## The defect this exists for
 *
 * A coconut-milk table printed `per 100 ml` clearly enough that [ColumnClassifier] resolved the
 * column, but the carbohydrate value still needed assistance. The assisted screen then asked
 * *"2.5 g carbs — per what?"* and offered `/100 g` beside `/100 ml`.
 *
 * The app had already established the basis and threw it away. Worse, it then re-asked with the
 * wrong answer one tap from the right one, on a screen the user reaches precisely when things have
 * already gone imperfectly.
 *
 * ## Value confidence and basis confidence are separate facts
 *
 * They are produced by different stages: [RowClassifier] and the interpreter decide which number is
 * the total carbohydrate, while [ColumnClassifier] decides what the table is measured per. Either
 * can succeed while the other fails. The pipeline's outcome types collapse them —
 * [EvidenceResolver.Outcome.Nothing] carries nothing at all — so a basis established by a stage
 * that *did* succeed was discarded because a later stage did not.
 *
 * This recovers that one fact and nothing else.
 *
 * ## Why this weakens no safety rule
 *
 * It reads no value, ranks no candidate, and moves no threshold. It asks the existing classifier
 * the question it already answers, and reports the answer **only when it is unambiguous**:
 *
 * - two per-100 columns disagreeing → null (that is the ambiguity the user must resolve)
 * - no per-100 column → null
 * - a serving column only → null ([NutritionBasis] has no member meaning "per serving", and
 *   mapping one onto a per-100 unit would attach a serving figure to a per-100 basis)
 *
 * Every null is the pre-existing behaviour: the user is asked. The change is confined to the one
 * case where the label said so plainly and the app already knew.
 */
object StatedBasis {

    /**
     * The basis [document] states, or null when it does not state exactly one.
     *
     * Null is not "unknown, pick something" — it is "ask the user", which is what the caller
     * already did in every case before this existed.
     */
    fun of(document: OcrDocument?): NutritionBasis? {
        if (document == null || document.elements.isEmpty()) return null

        val stated = ColumnClassifier
            .classify(LogicalRowBuilder.build(document), document.width)
            .mapNotNull { column ->
                when (column.kind) {
                    NutritionColumnKind.PER_100_G -> NutritionBasis.PER_100_G
                    NutritionColumnKind.PER_100_ML -> NutritionBasis.PER_100_ML
                    // A serving column is a real column about a different question, and a
                    // REFERENCE_PERCENT or UNKNOWN column asserts no basis at all. None of them
                    // contradicts a per-100 header, so none of them takes part in this decision.
                    NutritionColumnKind.PER_SERVING,
                    NutritionColumnKind.REFERENCE_PERCENT,
                    NutritionColumnKind.UNKNOWN,
                    -> null
                }
            }
            .distinct()

        // Exactly one basis, however many columns stated it. Multilingual packaging prints the same
        // basis twice routinely ("per 100 g / pro 100 g") and that is agreement; two *different*
        // bases is the ambiguity this must refuse.
        return stated.singleOrNull()
    }
}
