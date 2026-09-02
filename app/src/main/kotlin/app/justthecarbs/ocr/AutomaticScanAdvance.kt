package app.justthecarbs.ocr

/**
 * Decides whether a capture may skip the crop-confirmation step (1.0.3 P3).
 *
 * ## The step this removes, and the one it never removes
 *
 * Capture already computes a rectangle on its own — [ScanRegionMapper.expand] of the scan guide the
 * user was aiming with — and the crop screen's job was to have that rectangle *approved* before it
 * could be read. When the rectangle was already right, which is the ordinary case for someone who
 * pointed the phone at a nutrition table, that approval is a tap that changes nothing.
 *
 * So the resolution now runs against that same automatic rectangle first, and the crop screen opens
 * only when the answer was not safe enough to present. **The user still confirms the value** — the
 * proposal card and its *Use 48 g / 100 g* button are untouched. What is skipped is confirming a
 * *crop*, which is a statement about pixels, not about carbohydrate.
 *
 * ## Why this needs no new confidence rule
 *
 * Every judgement here is [EvidenceResolver]'s, already made, unchanged: this function reads an
 * outcome, it does not compute one. Nothing about recognition, thresholds, corroboration or the
 * parser moves. The same `readSelectedTable` path runs with the same region it would have used had
 * the user tapped *Read table* without dragging anything — the fast path is that tap, made
 * automatically, with a stricter rule about what may follow it.
 *
 * ## The gate requires a resolved outcome AND a confident reading
 *
 * An ambiguity now has its own outcome, [EvidenceResolver.Outcome.Unresolved], so "the parser could
 * not decide" is no longer spelled `Resolved`. Both halves of the gate are still checked: the
 * `Confident` test is retained rather than being made redundant by the renaming, because
 * [EvidenceResolver.Outcome.Resolved] is the resolver's own vocabulary and a future change to what
 * it may carry must not silently widen what advances.
 *
 * Ambiguity goes to the recovery path, where the user can act on it — the most likely reason a
 * parser cannot choose between candidates is that the frame contains more than the table, and
 * adjusting the rectangle is precisely the lever for that. It never goes to a card asking the user
 * to choose between bare numbers.
 *
 * Advancing therefore requires **both** a `Resolved` outcome **and** a `Confident` reading. Every
 * other combination — unresolved, needs-verification, conflicted, nothing — falls back.
 */
internal object AutomaticScanAdvance {

    /**
     * Whether [outcome] is safe to present without the user first confirming the crop.
     *
     * Pure and total: every outcome maps to a decision, and adding a new [EvidenceResolver.Outcome]
     * makes this `when` fail to compile rather than silently defaulting a new case to "advance".
     */
    fun mayAdvance(outcome: EvidenceResolver.Outcome): Boolean = when (outcome) {
        is EvidenceResolver.Outcome.Resolved -> outcome.reading is LabelReading.Confident
        // A value from one uncorroborated recognition. The existing flow holds this on the frozen
        // photograph so the user can check it against the package they are holding, which is the
        // only place it can be checked — skipping the crop would not change that, and arriving at
        // this state without having been asked for a crop is more confusing, not less.
        is EvidenceResolver.Outcome.NeedsVerification -> false
        // The parser offered competing candidates and nothing narrowed them. Repeating the same
        // ambiguity across passes does not resolve it, so there is nothing here to advance on.
        is EvidenceResolver.Outcome.Unresolved -> false
        // Two passes disagree. At least one is wrong and nothing can say which.
        is EvidenceResolver.Outcome.Conflicted -> false
        // No usable reading. The rectangle is the user's most direct lever over this.
        EvidenceResolver.Outcome.Nothing -> false
    }

    /**
     * Whether the capture may skip **both** the crop confirmation and the value confirmation.
     *
     * ## Why [mayAdvance] alone is no longer enough
     *
     * `mayAdvance` asks whether the parser produced a placeable, uncontested reading. That is a
     * question about *structure*, and this pass measured what happens when it is treated as a
     * question about *digits*: on `docs/Scan Evidence 02-09/20260902-085542-213` the printed
     * `72,0 g` came back as `12,0.g`, satisfied every structural rule, and reached Quick Calculation
     * with no user confirmation at all.
     *
     * So advancing now requires structure **and** independent verification — see
     * [AutomaticVerification] for what counts as independent and why two parses of one recognition
     * do not.
     *
     * ## What happens when verification is unavailable
     *
     * The reading is still shown. It goes to the proposal card, which is one tap and was the app's
     * behaviour before the fast path existed. **An unverified reading is not an error and is not
     * discarded** — most single-column labels can never satisfy the cross-column route, and refusing
     * them would trade a rare wrong answer for a constant one.
     */
    fun mayAdvanceVerified(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
    ): Boolean = mayAdvance(outcome) && verification.mayAdvanceAutomatically

    /**
     * Whether [outcome] may be offered as an ordinary **one-tap confirmation**.
     *
     * ## The gap between "not safe to advance" and "safe to propose"
     *
     * Until this pass those were the same question asked once: anything `mayAdvance` accepted but
     * `mayAdvanceVerified` refused went to the proposal card. That is the right default and it stays
     * the default — an unverified reading is not an error, most single-column labels can never
     * satisfy the cross-column route, and refusing them all would trade a rare wrong answer for a
     * constant one.
     *
     * It is wrong in exactly one situation, measured on `20260902-131545-452`. There a **single**
     * recognition run read a package printing `8,9 g / 100 ml` as `89 g`, the resolver returned
     * `Resolved` under its rule 3 ("Pass A alone answers"), verification reported
     * `NONE — only one recognition run`, and the scanner showed an ordinary confirmation card
     * reading `89 g / 100 ml`.
     *
     * A confirmation card is a question — *is this right?* — and it is a fair question only when the
     * app can show the user something they can check. Here the number on the card and the number on
     * the package differ by a decimal point the recognizer dropped from *every value on the label*,
     * so nothing on screen distinguishes the two, and the confirmation collects a tap that means
     * "yes, I can see a number there" rather than "yes, that is the figure".
     *
     * ## The rule
     *
     * > A reading that nothing independently verified, and whose absolute decimal scale the evidence
     * > cannot establish, is not offered for confirmation. It goes to focused entry, where the basis
     * > is preserved and the user supplies the digits.
     *
     * **Both conditions are required.** Verification alone is not enough to refuse (that would
     * refuse the ordinary unverified label), and ambiguity alone is not enough either — a reading
     * two distinct runs agree on has had its scale settled by evidence that is not scale-invariant,
     * which is what keeps `20260902-131357-353` (`41g`, integer-like, verified, and correct)
     * behaving exactly as it did.
     */
    fun mayConfirm(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument? = null,
    ): Boolean {
        if (!mayAdvance(outcome)) return false
        // Verified by a route that is not scale-invariant — a second recognition run reading the
        // same digits, or the label's own structure. The scale question is already answered.
        if (verification.mayAdvanceAutomatically) return true

        return scaleVerdict(outcome, document) !is ScaleAmbiguity.Verdict.Ambiguous
    }

    /**
     * The scale verdict for [outcome], for the gate above and for the evidence bundle.
     *
     * Null-safe by construction: with no document there is nothing to ask, and the reading keeps the
     * standing it had before this pass existed — an absent document must never *create* a refusal.
     */
    fun scaleVerdict(
        outcome: EvidenceResolver.Outcome,
        document: OcrDocument?,
    ): ScaleAmbiguity.Verdict? {
        val resolved = outcome as? EvidenceResolver.Outcome.Resolved ?: return null
        val candidate = (resolved.reading as? LabelReading.Confident)?.candidate ?: return null
        if (document == null) return null
        return ScaleAmbiguity.check(document, candidate)
    }
}
