package app.justthecarbs.ocr

import java.math.BigDecimal

/**
 * Turning a user's tap on the frozen photograph into a carbohydrate value (spec §17, §18).
 *
 * ## Why this is not a weakening of the safety rules
 *
 * Every refusal in this parser exists because the app could not tell *which nutrient a number belongs
 * to*. Sugars can equal the total, a saturated-fat figure can sit nearer the basis column than the
 * real value, `(g)` can be recognised as `(9)`. The parser refuses because the association is
 * genuinely unknowable from the evidence it has.
 *
 * When the user taps the carbohydrate row, that association stops being inferred. **The tap IS the
 * row identity** — it is supplied by a human reading the printed label, which is a better source than
 * any geometric rule in this repo. So this is not the parser guessing with a lower bar; it is the
 * parser being told the one thing it could not work out.
 *
 * What it still must not do is re-guess after the tap. Nothing here re-ranks, re-scores or "corrects"
 * the user's choice, and the resulting value is always shown for explicit confirmation before use.
 *
 * ## Scope
 *
 * Pure Kotlin, no Android, no ML Kit — it works on the [OcrDocument] that already exists, so it needs
 * no extra recognition and can run instantly when automatic recognition has failed.
 */
object AssistedSelection {

    /** A number the user could plausibly be pointing at, with where it sits on the image. */
    data class NumericCandidate(
        val text: String,
        val value: BigDecimal,
        val box: OcrBox,
        /** Recognizer confidence for the element this came from, when known. Diagnostic only. */
        val confidence: Float? = null,
    )

    /**
     * Matches a number, optionally with a unit suffix, as a whole token.
     *
     * Anchored at both ends so `0gjikovi` — the Slovenian word ML Kit read with a leading zero, which
     * once became a carbohydrate value — cannot be offered as the number `0`. That hazard is already
     * closed in the automatic path; it must not reopen in the assisted one.
     */
    private val NUMERIC_TOKEN = Regex("^(\\d{1,3}(?:[.,]\\d{1,3})?)\\s*(?:g|gr|gram|ml|kcal|kj|%)?$",
        RegexOption.IGNORE_CASE)

    /**
     * Every value-shaped token in the document, for the "tap the number" interaction (§17).
     *
     * Deliberately *not* filtered to plausible carbohydrate values: the user is choosing, and hiding
     * a number because the app thinks it unlikely would reintroduce exactly the judgement this
     * interaction exists to avoid. Energy figures and percentages are included because the user can
     * see they are not what they want, whereas a missing number is invisible.
     */
    fun numericCandidates(document: OcrDocument?): List<NumericCandidate> {
        if (document == null) return emptyList()
        return document.elements.mapNotNull { element ->
            val match = NUMERIC_TOKEN.find(element.text.trim()) ?: return@mapNotNull null
            val raw = match.groupValues[1].replace(',', '.')
            val value = raw.toBigDecimalOrNull() ?: return@mapNotNull null
            NumericCandidate(
                text = element.text.trim(),
                value = value,
                box = element.box,
                confidence = element.confidence,
            )
        }
    }

    /**
     * The numbers on the row the user tapped (§18).
     *
     * "The row" is defined by vertical overlap with [tappedY], using the document's own reconstructed
     * rows so the notion of a row is the same one the parser uses. Restricting to the tapped row is
     * the whole safety property: a number from the sugars row two lines below cannot appear in this
     * list, so confirming a value from it cannot silently import an adjacent nutrient's figure.
     */
    fun candidatesOnRowAt(document: OcrDocument?, tappedY: Int): List<NumericCandidate> {
        if (document == null) return emptyList()
        val row = LogicalRowBuilder.build(document)
            .firstOrNull { tappedY >= it.box.top && tappedY <= it.box.bottom }
            ?: return emptyList()

        // Intersect the row's own elements with the numeric filter, rather than re-scanning the
        // document by y-coordinate: row membership is decided once, by the same geometry the parser
        // trusts, so the assisted path and the automatic path cannot disagree about what a row is.
        val rowElements = row.elements.toSet()
        return numericCandidates(document).filter { candidate ->
            rowElements.any { it.box == candidate.box }
        }
    }

    /**
     * The row the user tapped, for showing them what they selected.
     *
     * Returned so the UI can echo the row's text back ("Koolhydraten 53,5 g") and the user can see
     * whether they hit the row they meant before confirming a number from it.
     */
    fun rowTextAt(document: OcrDocument?, tappedY: Int): String? {
        if (document == null) return null
        return LogicalRowBuilder.build(document)
            .firstOrNull { tappedY >= it.box.top && tappedY <= it.box.bottom }
            ?.text
    }
}
