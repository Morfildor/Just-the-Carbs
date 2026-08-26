package app.justthecarbs.ocr

/**
 * Restricts a recognised document to the elements inside a user-confirmed rectangle.
 *
 * ## The hypothesis this implements
 *
 * On a real package the dominant failure is not misreading the table — it is that the recognised
 * document also contains the ingredient list, marketing copy, a best-before date and often a second
 * package's panel. [LogicalRowBuilder] groups by geometry, so a total-carbohydrate row and a prose
 * sentence at the same height become one row, and once merged no downstream rule can separate them.
 * Three automatic approaches to isolating the table were built and measured against the corpus, and
 * all three were rejected: vertical banding dropped the basis header band, connected-component
 * clustering had no threshold that worked across fixtures, and re-recognising an isolated crop
 * **manufactured a confident-wrong** by re-tokenising `(g)` as `(9)`.
 *
 * A human pointing at the table supplies what none of those could infer. This filter is how that
 * gesture reaches the parser.
 *
 * ## Why this is not a safety-relevant stage
 *
 * It operates on **elements**, before reconstruction, and it has no concept of a nutrient, a value,
 * a column or an answer. It cannot prefer a number because it cannot recognise one. Everything that
 * decides what a figure *means* — [CarbohydrateTermAnchor], [UnitMarkerFilter], the child-nutrient
 * exclusion, the usable-basis-column rule — runs afterwards, unchanged, on whatever survives.
 *
 * The user's rectangle asserts *"the nutrition table is in here"*. It does not assert *"a number in
 * here is the carbohydrate value"*, and nothing in this file lets it mean the second thing. In
 * particular a selection that excludes the basis header does not gain a basis: the parser simply
 * fails to resolve a column and refuses, exactly as it would on a cropped photograph.
 *
 * ## Two properties worth stating explicitly
 *
 * - **It can only remove.** Every surviving element is one Pass A produced, with its original text
 *   and its original box. No re-recognition, no rescaling, no re-tokenisation — which is what makes
 *   it structurally incapable of the `(g)` -> `(9)` failure.
 * - **Coordinates are not rebased.** The returned document keeps the source image's dimensions and
 *   every box keeps its absolute position, because row pitch, slope estimation and column alignment
 *   are all expressed in source-image pixels and text heights. Rebasing to the selection would
 *   silently change the meaning of every one of those thresholds.
 */
internal object ElementRegionFilter {

    /**
     * How much of an element's own area must fall inside the selection for it to be kept.
     *
     * Set at a half rather than requiring containment because ML Kit's boxes are word-like and a
     * selection edge is a dragged gesture, so a word at the table's margin routinely straddles it.
     * Set well above zero because the whole purpose is to exclude the adjacent panel, and a rule that
     * admitted anything merely *touching* the rectangle would let a prose line back into a table row —
     * the exact interference being removed.
     *
     * Normalising by the element's own area, rather than by absolute overlap, is what makes the rule
     * independent of tokenisation: a long prose line and a short numeric cell can share an identical
     * overlap area while meaning entirely different things.
     */
    private const val MIN_OVERLAP = 0.50

    /**
     * A selection this close to the whole frame is treated as no selection at all.
     *
     * Filtering there would cost an allocation and remove nothing, and — more importantly — it keeps
     * "the user did not meaningfully narrow anything" a single, explicit state rather than a filtered
     * document that happens to be identical.
     */
    private const val NO_OP_THRESHOLD = 0.98

    /**
     * [document] restricted to [region], or null when the selection retains nothing usable.
     *
     * Null is a distinct outcome from an empty document on purpose. An empty [OcrDocument] parses to
     * [LabelReading.NotFound], which the UI states as "no carbohydrate value on this label" — a claim
     * about the package. "Your selection enclosed no recognised text" is a claim about the gesture,
     * and the caller must be able to tell them apart to decide whether to keep the whole-frame
     * reading instead.
     */
    fun filter(document: OcrDocument, region: NormalizedRegion?): OcrDocument? {
        if (region == null) return document
        if (region.width >= NO_OP_THRESHOLD && region.height >= NO_OP_THRESHOLD) return document

        val left = region.left * document.width
        val top = region.top * document.height
        val right = region.right * document.width
        val bottom = region.bottom * document.height

        val retained = document.elements.filter { element ->
            overlapFraction(element.box, left, top, right, bottom) >= MIN_OVERLAP
        }
        if (retained.isEmpty()) return null
        if (retained.size == document.elements.size) return document

        return document.copy(elements = retained)
    }

    /**
     * The elements [filter] would discard, for the debug evidence bundle.
     *
     * Deliberately derived from the same predicate rather than recomputed, so the recorded rejects
     * cannot disagree with what actually happened.
     */
    fun rejected(document: OcrDocument, region: NormalizedRegion?): List<OcrElement> {
        val kept = filter(document, region)?.elements?.toSet() ?: return emptyList()
        return document.elements.filterNot { it in kept }
    }

    /** The fraction of [box]'s own area lying inside the selection. */
    private fun overlapFraction(
        box: OcrBox,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): Double {
        val area = box.width.toDouble() * box.height.toDouble()
        // A zero-area box carries no evidence either way; excluding it cannot lose a value, because
        // no value can be read from a box with no extent.
        if (area <= 0.0) return 0.0

        val overlapWidth = minOf(box.right.toDouble(), right) - maxOf(box.left.toDouble(), left)
        val overlapHeight = minOf(box.bottom.toDouble(), bottom) - maxOf(box.top.toDouble(), top)
        if (overlapWidth <= 0.0 || overlapHeight <= 0.0) return 0.0

        return (overlapWidth * overlapHeight) / area
    }
}
