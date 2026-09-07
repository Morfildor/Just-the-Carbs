package app.justthecarbs.ocr

/**
 * Requires a carbohydrate value to be accompanied by a recognised unit, and declines it otherwise.
 *
 * ### The failure this exists for
 *
 * ML Kit folds a printed unit glyph into the numeric token next to it. Measured on a Samsung
 * SM-S928B across five captures of three real packages (`docs/Scan evidence 31-08-26/`):
 *
 * ```
 * printed        returned      folder            package
 * 0,5 g          0.59          140132-710        Fanta Zero     <- reached CONFIDENT
 * 3,4 g          3,49          135943-434        Sondey
 * 0 g            09            140132-710        Fanta Zero
 * 2,5 g          2,50          140033-054        Boursin
 * 3 g            3q.           140033-054        Boursin
 * 28 g           28q,          140033-054        Boursin
 * 6.5 g          6.5q.         140056-376        Boursin
 * 2.5 g          2.5c          140056-376        Boursin
 * ```
 *
 * The first row is the one that matters: the parser reached `CONFIDENT 0.59 PER_100_ML` on a can
 * printing 0,5 g, from a correct `TOTAL_CARBOHYDRATE` row with a correctly resolved column. Every
 * stage behaved correctly on a corrupted token. On the *same* label in the *same* pass, the sugars
 * row read `0.5` correctly — so this is not a property of the label, the lighting or the framing,
 * and no amount of re-aiming avoids it.
 *
 * ### Why plausibility cannot catch this
 *
 * `0.59 g/100 ml` is an entirely ordinary carbohydrate quantity. It clears every validator, it is on
 * the right row, and it is introduced by the right nutrient term. There is nothing suspicious about
 * the number. The only observable fact that distinguishes it from a real reading is that **the row
 * states no unit anywhere** — the `g` that should follow it became the digit `9`.
 *
 * ### The rule
 *
 * > A carbohydrate value must be accompanied by a recognised unit (`g` or `ml`), either as a valid
 * > suffix on the token itself or as a separate immediately-adjacent element. Otherwise, decline.
 *
 * It covers two distinct corruption classes with one question:
 *
 * - **unit -> letter** (`3q.`, `2.5c`, `6.5q.`, `28q,`) — the suffix is present but is not a unit,
 *   which is directly detectable.
 * - **unit -> digit** (`0.59`, `2,50`, `09`, `3,49`) — the unit is gone entirely and the token still
 *   looks like a clean number. Here the *absence* of a unit is the only signal that exists.
 *
 * ### Deliberately unconditional — no sibling comparison, no table scope
 *
 * An earlier form of this rule was sibling-conditional: decline when neighbouring cells carry units
 * and this one does not. **That is too weak and was rejected by the owner**, because the Boursin
 * declaration carries well-formed `19g` and `5,5g.` in the same table as the corrupted `3q.` — so a
 * sibling comparison finds units nearby and lets the corruption through.
 *
 * A column-scoped variant was also considered and rejected on evidence, not taste: inheriting the
 * unit from the column header would read the Fanta's `100 ml 250 m` header, accept `0.59` as
 * `0.59 ml`, and the confident-wrong survives. **A rule that does not catch scan 4 is not worth
 * having.** Scope is the token and its immediate right-hand neighbour, nothing wider.
 *
 * ### It refuses; it never repairs
 *
 * There is no correction path here and none may be added. `0.59` must not become `0.5`, `3q.` must
 * not become `3`. The decimal point is what OCR is least reliable about, so repositioning one guesses
 * at exactly the wrong thing — and unlike a refusal, a wrong repair is invisible: the user sees a
 * plausible number and has no reason to check it. A refusal routes to the assisted path, where a
 * person reads the value off the package.
 *
 * ### The known cost
 *
 * A label printing its unit only in the column header, with bare values down the column, is declined
 * by this rule even though it is not corrupted. That is a coverage regression to `NotFound`, which
 * routes to the assisted path — a UX cost, not an accuracy cost. It was accepted deliberately in
 * exchange for closing a confident-wrong on a dosing input.
 */
internal object CarbUnitAccompaniment {

    /**
     * A number carrying a valid unit suffix, with optional punctuation before and after it.
     *
     * Trailing `[.,;:]` is allowed because real recognitions routinely append the sentence
     * punctuation that follows the figure on a prose label — `19g,` and `5,5g.` are both correct
     * readings from the Boursin declaration. The unit itself must still be one this app has.
     *
     * ### The separator before the unit (2026-09-01, third phone session)
     *
     * Measured on `docs/Scan evidence 01-09-26 3rd testr/20260901-225720-700/`, a cracker bag whose
     * printed `72,0 g` came back as **`72,0.g`** — a full stop where the space is. This rule declined
     * it, the label read `NotFound`, and the recovery screen then offered the *portion* figure
     * `22,5g` and even `9%` as things the user might mean. The very next capture of the same package
     * (`225738-513`) returned a clean `72,0g` and read `Confident 72.0 PER_100_G`, so the decline was
     * ML Kit punctuation noise, not a property of the label.
     *
     * ### Why this does not reopen the `g` -> `9` defect
     *
     * The whole rule turns on whether **a unit glyph is present**. The corruption class it exists for
     * is the unit having been replaced — by a digit (`0.59`, `09`, `22,59`) or by another letter
     * (`3q.`, `2.5c`). Neither becomes acceptable here: the token must still end in a unit spelling
     * this app knows, and one stray separator between the number and that unit changes nothing about
     * whether the unit was read.
     *
     * A single separator, and only between the number and the unit. `72,0..g`, `72,0 x g` and
     * `72,0mg` are all still declined — the last because `mg` is not in the alternation, which is
     * deliberate: milligrams on a carbohydrate row is a misread, not a value.
     */
    private val UNIT_SUFFIXED = Regex(
        "^\\d{1,3}(?:[.,]\\d{1,3})?\\s*[.,]?\\s*(?:g|gr|gram|grammes?|ml)[.,;:]?$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A number with no trailing letters at all.
     *
     * Anchored, and deliberately *not* tolerant of a trailing alphabetic run: that is what separates
     * a bare value awaiting an adjacent unit from a token whose unit glyph was misrecognised as some
     * other letter. `0.59` matches here; `3q.` matches neither this nor [UNIT_SUFFIXED], which is
     * exactly the intent.
     */
    private val BARE_NUMBER = Regex("^\\d{1,3}(?:[.,]\\d{1,3})?[.,;:]?$")

    /** An element that is a unit and nothing else, as ML Kit emits it beside a value. */
    private val UNIT_ELEMENT = Regex(
        "^(?:g|gr|gram|grammes?|ml)[.,;:]?$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * How far to the right of the value a unit element may sit and still be its unit, as a multiple
     * of the value's own height.
     *
     * Expressed in text height rather than pixels so it survives every capture resolution, the same
     * convention the row builder uses. Measured against the Sondey fixture, where `72,0`
     * `[1066..1150]` is followed by `g` `[1163..1187]` — a 13 px gap against a 42 px glyph height,
     * about 0.31. The bound is set well above that and still far below a column gap, so a unit
     * belonging to a different column cannot reach.
     */
    private const val MAX_UNIT_GAP_IN_HEIGHTS = 1.2

    /**
     * How far a unit element's LEFT edge may sit inside the value's own box and still count as
     * adjacency rather than overlap noise, as a fraction of the value's height.
     *
     * ML Kit's own bounding boxes are not exact — two neighbouring glyphs' boxes can overlap by a
     * few pixels even when the characters themselves do not touch, purely from how the recognizer
     * pads a box around a detected shape. A real capture pairing a value with its own trailing unit
     * measured this at up to ~2 px against glyph heights in the 30-50 px range (about 0.05), well
     * below any gap that could plausibly belong to a genuinely different token.
     *
     * This is deliberately small and is not a relaxation of the adjacency rule itself — see
     * [isImmediatelyRightOf]'s center-ordering gate, which is what actually distinguishes "this
     * unit's box merely touches the value's box" from "this is some other token entirely". Overlap
     * tolerance alone, without that gate, would accept a unit sitting mostly or wholly to the
     * value's left whenever the boxes happened to touch — exactly the corruption this rule exists to
     * refuse.
     */
    private const val MAX_UNIT_OVERLAP_IN_HEIGHTS = 0.15

    /** Fraction of the value's height that must overlap vertically for a unit to be on its row. */
    private const val MIN_VERTICAL_OVERLAP = 0.5

    /**
     * Whether [value] is accompanied by a recognised unit.
     *
     * [rowElements] are the elements of the printed row [value] sits on, in any order; only those
     * immediately right of [value] and vertically overlapping it are considered.
     *
     * Returns false for anything that is not a number at all — this is asked only about tokens the
     * parser already treats as candidate values, and a non-numeric token reaching it is not a value
     * to accept.
     */
    fun isAccompanied(value: OcrElement, rowElements: List<OcrElement>): Boolean {
        val text = value.text.trim()

        // The unit rode in on the token itself.
        if (UNIT_SUFFIXED.matches(text)) return true

        // A token with a trailing letter run that is not a unit is a corrupted unit, never a value.
        // Returning here rather than falling through to the adjacency check is deliberate: `3q.`
        // followed by a stray `g` elsewhere on the row must still be declined, because the token
        // itself already carries a mangled unit and a second one cannot repair it.
        if (!BARE_NUMBER.matches(text)) return false

        // A bare number. Its unit must be the very next thing printed on the same row.
        return rowElements.any { candidate ->
            candidate !== value &&
                UNIT_ELEMENT.matches(candidate.text.trim()) &&
                isImmediatelyRightOf(value, candidate)
        }
    }

    /**
     * Whether [unit] sits immediately right of [value] on the same printed row.
     *
     * Two independent conditions are required, and neither alone is sufficient:
     *
     * 1. **Center ordering** — [unit]'s horizontal center must sit strictly to the right of
     *    [value]'s. This is the categorical claim that carries reading order: a nutrition table
     *    prints `61,9 g`, never `g 61,9`, so a unit whose center is not to the value's right is
     *    either the previous column's trailing unit or an unrelated neighbouring token, and
     *    accepting it would let one column's unit vouch for another column's number. Center
     *    ordering, rather than edge ordering, is what makes a small overlap tolerable at all: two
     *    boxes can overlap by a few pixels of OCR noise while their centers still agree on which
     *    token is which.
     * 2. **Bounded gap** — the horizontal distance between the boxes, which may be a small negative
     *    number (a bounded overlap, [MAX_UNIT_OVERLAP_IN_HEIGHTS]) up to a generous positive one
     *    ([MAX_UNIT_GAP_IN_HEIGHTS]). A gap more negative than the overlap bound means the boxes
     *    overlap by more than adjacency noise can explain, which is what a unit that actually
     *    belongs to a different, closely-set token looks like.
     *
     * The vertical overlap requirement keeps a unit from the row above or below out.
     */
    private fun isImmediatelyRightOf(value: OcrElement, unit: OcrElement): Boolean {
        val height = (value.box.bottom - value.box.top).toDouble()
        if (height <= 0.0) return false

        val valueCenter = (value.box.left + value.box.right) / 2.0
        val unitCenter = (unit.box.left + unit.box.right) / 2.0
        if (unitCenter <= valueCenter) return false

        val gap = unit.box.left - value.box.right
        if (gap < -height * MAX_UNIT_OVERLAP_IN_HEIGHTS || gap > height * MAX_UNIT_GAP_IN_HEIGHTS) {
            return false
        }

        val overlap = minOf(value.box.bottom, unit.box.bottom) -
            maxOf(value.box.top, unit.box.top)
        return overlap >= height * MIN_VERTICAL_OVERLAP
    }
}
