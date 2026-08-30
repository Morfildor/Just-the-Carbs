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
 * ## The gate is deliberately stricter than "Resolved"
 *
 * [EvidenceResolver.Outcome.Resolved] can legitimately carry an **[LabelReading.Ambiguous]** reading:
 * when no pass is confident, the resolver keeps the richest ambiguous report rather than flattening
 * it to `NotFound`. That outcome is safe — the scanner shows `AmbiguousCard` and never auto-accepts
 * — but it means the parser could not decide between candidates, and the most likely reason is that
 * the frame contains more than the table. That is precisely what adjusting the rectangle fixes, so
 * ambiguity goes to the crop screen where the user can act on it, not to a card asking them to
 * choose between numbers a tighter box might have disambiguated.
 *
 * Advancing therefore requires **both** a `Resolved` outcome **and** a `Confident` reading. Every
 * other combination — ambiguous, needs-verification, conflicted, nothing — falls back.
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
        // Two passes disagree. At least one is wrong and nothing can say which.
        is EvidenceResolver.Outcome.Conflicted -> false
        // No usable reading. The rectangle is the user's most direct lever over this.
        EvidenceResolver.Outcome.Nothing -> false
    }
}
