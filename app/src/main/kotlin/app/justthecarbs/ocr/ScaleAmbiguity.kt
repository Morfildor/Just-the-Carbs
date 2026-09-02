package app.justthecarbs.ocr

/**
 * Whether the **absolute decimal scale** of a reading is established by the evidence, or only its
 * relationship to the other values on the label.
 *
 * ## The failure this exists for
 *
 * A truffle sauce prints `8,9 g / 100 ml` and `1,3 g / 15 ml`. Both captures of it
 * (`20260902-131511-970`, `20260902-131545-452`) recognised the carbohydrate row as
 *
 * ```
 * Kohlenhydrate 89 g, 13gk19; W3arvan
 * ```
 *
 * and the app displayed **`89 g / 100 ml`** — ten times the printed figure.
 *
 * ## Why every existing guard passed it, and why they were all right to
 *
 * `89` is a well-formed number, on a correctly classified `TOTAL_CARBOHYDRATE` row, under a
 * correctly resolved `PER_100_ML` column, carrying its unit. It is inside
 * [app.justthecarbs.domain.NutritionValueValidator]'s ceiling, because 89 g of carbohydrate per
 * 100 g is an ordinary figure for flour, sugar or dried pasta. **A parser that refused it would
 * refuse every legitimate high-carbohydrate label**, which is a far worse trade than the one this
 * object makes.
 *
 * ## Why the cross-column ratio check cannot see it
 *
 * [CrossColumnRatioCheck] asks whether this row behaves like the rest of the table — the
 * serving-to-per-100 ratio is a property of the serving size, so it is the same on every row. That
 * is exactly the right question for a *single* misread digit, which is what it was built for and
 * what it still catches.
 *
 * It cannot catch this one, and the reason is arithmetic rather than a gap in the implementation.
 * The decimal collapse on this label is **uniform**: the recognizer dropped the separator from
 * every value in the column, so the printed `2,7 g` fat reads `27 g`, `1,6 g` salt reads `16 g` and
 * `4,6 g` sugars reads `46g`. Multiplying both sides of a ratio by ten leaves the ratio unchanged:
 *
 * ```
 * 13 / 89  = 0.146        <- as recognised
 * 1.3 / 8.9 = 0.146       <- as printed
 * 15 / 100  = 0.15        <- the declared serving
 * ```
 *
 * So the table corroborates `89` exactly as strongly as it corroborates `8.9`. **Relational
 * consistency establishes proportion, never absolute scale.** No amount of cross-column evidence
 * can distinguish the two, which is why this is a separate question with a separate answer rather
 * than a tightening of that check.
 *
 * ## The rule
 *
 * > When the recognizer did not preserve a decimal separator anywhere in a **pair** of values that
 * > move together, and dividing the whole pair by a common power of ten yields an equally
 * > self-consistent reading, the absolute scale is **not established by this evidence**.
 *
 * Three properties keep that general rather than package-specific:
 *
 * 1. **It asks about punctuation, not magnitude.** There is no "values above 50 are suspicious"
 *    threshold anywhere here, and none may be added — such a rule would refuse flour and sugar
 *    while still admitting a collapsed `4,6` -> `46`.
 * 2. **It requires a pair.** A single-value label states one number with nothing to share a scale
 *    with, so those labels are untouched. The pair is what makes a *uniform* rescaling expressible.
 * 3. **It is only ever asked of an unverified reading.** See [EvidenceResolver] and
 *    [AutomaticScanAdvance] — a reading two distinct recognition runs agree on, or one the label's
 *    own structure supports through a route that is not scale-invariant, has its scale settled by
 *    that evidence and never reaches this question. That is what keeps
 *    `20260902-131357-353` (`41g`, integer-like, no separator, and **correct**) advancing exactly as
 *    it did.
 *
 * ## What it never does
 *
 * It does not divide, shift, repair, or propose `8.9`. **No divided value is manufactured anywhere
 * in this file.** The decimal point is what OCR is least reliable about, so repositioning it guesses
 * at precisely the least reliable thing — and unlike a refusal, a wrong repair is invisible: the
 * user sees a plausible number and has no reason to check it. An ambiguous scale produces a
 * *refusal to propose either scale*, and the user types the number they can see.
 */
object ScaleAmbiguity {

    /** Whether the evidence establishes the reading's absolute scale. */
    sealed interface Verdict {
        /** The scale is established, or there is nothing here that could put it in doubt. */
        data class Established(val reason: String) : Verdict

        /**
         * A common rescaling of the whole pair is equally consistent with the recognised text.
         *
         * [pairedText] and [candidateText] are the surviving tokens, so a bundle can say what the
         * decision was made on rather than merely that it was made.
         */
        data class Ambiguous(
            val candidateText: String,
            val pairedText: String,
            val reason: String,
        ) : Verdict
    }

    /**
     * Whether [candidate]'s scale is established by [document] alone.
     *
     * The paired value is looked for on the candidate's **own row**, which is where a nutrition
     * table prints the same nutrient under a second column. Restricting to that row is what keeps
     * the question about one nutrient measured two ways, rather than about two unrelated numbers
     * that happen to sit near each other.
     */
    fun check(document: OcrDocument, candidate: CarbCandidate): Verdict {
        val row = rowContaining(document, candidate) ?: return Verdict.Established("no row to pair against")
        val candidateElement = candidateElement(row, candidate)
            ?: return Verdict.Established("the candidate's own element was not identifiable")

        // A separator that survived on the candidate itself settles it outright: the recognizer
        // reported where the point is, so the scale is stated rather than inferred.
        if (hasDecimalSeparator(candidateElement.text)) {
            return Verdict.Established("the candidate's own token carries a decimal separator")
        }

        // The sibling **value** cells: tokens to the right of the candidate, inside the same
        // carbohydrate clause, that read as a value rather than as a name.
        //
        // Three restrictions, each load-bearing and each measured:
        //
        // * **Inside the carbohydrate clause.** A US linear panel prints every nutrient on one
        //   recognised row — `DV), Total Carb. 6g (2% DV), Fiber 1 g (4% DV),` — so without this
        //   bound the fibre figure becomes the carbohydrate's "paired column value" and the panel's
        //   legitimate `6 g` is suppressed. Measured: it broke seven existing tests across four
        //   sessions' fixtures. [NutrientRowSegments] is the same bound [RecoveryCandidates] and the
        //   automatic path already use, so the three cannot disagree about where the clause ends.
        //   Null on an ordinary table row, where the whole row is the clause and nothing changes.
        // * **A value cell, not any token containing a digit.** A nutrient name may legitimately
        //   contain digits (`E471`, `Omega-3`), and treating one as a paired value would make every
        //   such row ambiguous.
        // * **To the right of the candidate.** A nutrition table prints the label column first and
        //   its value columns after it, so a second measurement of the same nutrient is to the
        //   right. Without this the nutrient's own name was pairing with its own value, which made a
        //   legitimate single-column `41g` label read as ambiguous.
        val clause = NutrientRowSegments.totalCarbohydrateSegment(row)
        val siblings = row.elements.filter {
            it !== candidateElement &&
                it.box.left > candidateElement.box.left &&
                (clause == null || clause.contains(it.box)) &&
                looksLikeAValueCell(it.text)
        }
        if (siblings.isEmpty()) {
            return Verdict.Established("no paired value in this clause to share a scale with")
        }

        // If any sibling kept a separator, the recognizer demonstrably preserved decimal points on
        // this row, so the candidate's lack of one is information rather than damage.
        val separated = siblings.firstOrNull { hasDecimalSeparator(it.text) }
        if (separated != null) {
            return Verdict.Established(
                "a paired value on this row kept its separator ('${separated.text.trim()}')",
            )
        }

        val paired = siblings.first()
        return Verdict.Ambiguous(
            candidateText = candidateElement.text.trim(),
            pairedText = paired.text.trim(),
            reason = "neither the candidate nor its paired column value carries a decimal " +
                "separator, so a common rescaling of the pair is equally consistent",
        )
    }

    private fun rowContaining(document: OcrDocument, candidate: CarbCandidate): LogicalRow? =
        LogicalRowBuilder.build(document)
            .firstOrNull { row -> candidateElement(row, candidate) != null }

    /**
     * The element the candidate's value was actually read from.
     *
     * ## Why this is not an exact-box match
     *
     * The candidate's `geometry` is not always one element. When
     * [MergedTotalRowRecovery] recovers a total from a row ML Kit merged with its child, the
     * geometry it records is the **span** of that recovered clause. Measured on
     * `20260902-131545-452`: the candidate box is `[530,1758,973,1819]`, covering
     * `Kohlenhydrate 89 g, 13gk19; W3arvan` — four elements, not one.
     *
     * So the value element is located *inside* the candidate's span, by matching the number the
     * parser accepted. Matching the number rather than taking the leftmost value cell matters on a
     * span containing two of them: `89` is the candidate and `13gk19;` is its pair, and picking the
     * wrong one would invert the comparison.
     */
    private fun candidateElement(row: LogicalRow, candidate: CarbCandidate): OcrElement? {
        val span = candidate.geometry
        val within = row.elements.filter {
            it.box.left >= span.left && it.box.right <= span.right &&
                it.box.verticalOverlapRatio(span) > 0.5
        }
        // The element whose leading number is the value the parser accepted.
        return within.firstOrNull { leadingNumber(it.text)?.compareTo(candidate.value) == 0 }
        // A single element carrying the whole value (`61,9`), when the span is that element.
            ?: row.elements.firstOrNull { it.box == span }
    }

    /** The number a token starts with, ignoring anything fused after it. */
    private fun leadingNumber(text: String): java.math.BigDecimal? =
        LEADING_NUMBER.find(text.trim())?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimalOrNull()

    private val LEADING_NUMBER = Regex("^(\\d{1,4}(?:[.,]\\d{1,3})?)")

    /**
     * Whether the token reads as a value cell — a number, possibly with a unit or other debris fused
     * to it.
     *
     * Deliberately permissive **after** the leading number, because a collapsed serving cell arrives
     * as `13gk19;` (figure, unit and reference percentage welded together) and requiring a clean
     * `1,3 g` would find no pair on exactly the labels this rule exists for. Deliberately strict
     * **before** it: the token must start with a digit, so a nutrient name that happens to contain
     * one is not mistaken for the second measurement of the same nutrient.
     */
    private fun looksLikeAValueCell(text: String): Boolean = VALUE_CELL.containsMatchIn(text.trim())

    private val VALUE_CELL = Regex("^\\d")

    /**
     * Whether the token carries a decimal separator **between two digits**.
     *
     * Deliberately narrow. A trailing `89g,` ends in a comma that separates it from the next cell,
     * not a decimal point — that token is exactly the collapsed form this rule must catch, and a
     * naive `contains(',')` would call it separated and pass the failure straight through.
     */
    private fun hasDecimalSeparator(text: String): Boolean = DECIMAL_BETWEEN_DIGITS.containsMatchIn(text)

    private val DECIMAL_BETWEEN_DIGITS = Regex("\\d[.,]\\d")
}
