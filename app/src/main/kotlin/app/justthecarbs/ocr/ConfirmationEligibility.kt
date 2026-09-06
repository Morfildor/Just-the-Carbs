package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis

/**
 * Whether an automatically-produced reading may be shown for **explicit human visual confirmation**
 * — a distinct, strictly narrower question from [ReadingEligibility], which governs the automatic
 * one-tap [VerificationScreen][app.justthecarbs.ui.scan.VerificationScreen] state
 * ([AutomaticScanAdvance.mayConfirm]) and automatic advancement
 * ([AutomaticScanAdvance.mayAdvanceVerified]).
 *
 * ## Why this is a separate object rather than a change to [ReadingEligibility]
 *
 * [ReadingEligibility] answers *"may this figure be put in front of the user at all, on any
 * surface?"* and every one of its callers trusts that a refusal there means the figure is unsafe to
 * show even for comparison. Weakening it to admit `ScaleAmbiguity.Verdict.Unsupported` would widen
 * every caller at once, including [AutomaticScanAdvance.mayConfirm] — the gate that lets a reading
 * skip the crop screen and land directly on the frozen-photo proposal with nothing yet compared.
 *
 * This object asks a different, weaker question, reachable only from the presentation layer that
 * already keeps the frozen photograph and the enlarged row close-up on screen next to the figure:
 * *"is there enough here that showing it beside its own printed row, for the user to look at and
 * either accept or reject, is worth doing — never worth accepting sight-unseen?"* Nothing here ever
 * skips a confirmation tap. [ReadingEligibility] is untouched: [ScanPresentationDecision] still asks
 * it first, in the same order, for the same reasons, and this object is consulted only for a reading
 * it already refused.
 *
 * ## What is newly permitted, and what is not
 *
 * The **only** thing this relaxes is [ScaleAmbiguity.Verdict.Unsupported] and
 * [ScaleAmbiguity.Verdict.Ambiguous] — insufficient or ambiguous evidence about the *decimal scale*
 * of an otherwise-structurally-sound reading. Every other exclusion [ReadingEligibility] and
 * [RecoveryCandidates] already apply is asked again here, unchanged:
 *
 * - the row must classify as [NutritionRowKind.TOTAL_CARBOHYDRATE], never a child nutrient
 *   (sugars, polyols, fibre, …) — checked via [RecoveryCandidates.of], which applies the same
 *   unconditional child-row exclusion the automatic path uses;
 * - the value must sit in the total-carbohydrate clause of its row (never a neighbouring nutrient's
 *   figure claimed by reading order — [CarbohydrateTermAnchor]);
 * - the value must carry its own unit when the label prints units on its cells
 *   ([UnitAccompanimentPolicy]/[CarbUnitAccompaniment]);
 * - the candidate's basis must be owned by the column it actually sits in, not merely the nearest
 *   surviving one ([RecoveryCandidates]'s column-ownership rule);
 * - a value the label's own other rows structurally contradict is excluded
 *   ([CrossColumnRatioCheck.Verdict.Conflicting]);
 * - a value a **distinct recognition run** read differently is excluded ([DisputedCandidates]) —
 *   this is the "explicitly contradicted" case the task calls out by name, and it is refused here
 *   exactly as it is refused everywhere else;
 * - a header quantity, a percentage, or any cell in an [NutritionColumnKind.UNKNOWN] or
 *   [NutritionColumnKind.REFERENCE_PERCENT] column is never a candidate at all — enforced by
 *   [CarbCandidate]'s own `init` block and by [RecoveryCandidates]'s column resolution, neither of
 *   which this object bypasses;
 * - the value must be **physically plausible** as a per-100 carbohydrate figure under its own basis
 *   ([CarbPlausibility]) — a check [ReadingEligibility] does not perform at all (it is not asked to;
 *   the automatic path's [NutritionValueValidator] ceiling is applied further upstream, but an
 *   explicit confirmation screen showing a structurally-sound-looking `790` needs its own barrier,
 *   exactly as [AssistedReadingScreen][app.justthecarbs.ui.scan.AssistedReadingScreen]'s typed-value
 *   path already has one).
 *
 * ## What this never does
 *
 * It does not repair, round, rescale or divide a value. It does not skip a confirmation tap — every
 * admission here routes to an explicit "does this match the label?" screen with one primary action
 * the user must press, never to automatic advance and never to the ordinary one-tap
 * [AutomaticScanAdvance.Presentation.ConfirmOnCapture] state. It does not change what
 * [ReadingEligibility] or [AutomaticScanAdvance] decide for the automatic path — a reading refused
 * here is still refused there, and a reading admitted there never reaches this object at all (see
 * [ScanPresentationDecision], which asks [ReadingEligibility] first and this object only on refusal).
 */
internal object ConfirmationEligibility {

    /** Why a reading may be offered for explicit visual confirmation, or why it may not. */
    sealed interface Verdict {
        val reason: String
        val isEligible: Boolean get() = this is Eligible

        /** May be shown, with [candidate] carrying everything the confirmation screen needs. */
        data class Eligible(val candidate: RecoveryCandidates.Candidate, override val reason: String) : Verdict

        data class Refused(override val reason: String) : Verdict
    }

    /**
     * Whether [reading]'s candidate — refused by [ReadingEligibility] for insufficient scale
     * evidence — may still be offered for explicit visual confirmation against [document].
     *
     * Locates the reading's own cell inside [RecoveryCandidates.of]'s output for [document], which is
     * what applies every structural exclusion above identically to how the recovery screen already
     * applies them. A candidate absent from that list was excluded by one of those rules — child row,
     * wrong clause, missing unit, column ownership, cross-column contradiction, or a distinct-run
     * dispute — and is refused here for the same reason, never re-admitted.
     *
     * [disputed] must be the same [DisputedCandidates] the resolver produced for this evidence set, so
     * a value one recognition run contradicted cannot be shown for confirmation just because it also
     * happens to be the automatic path's own candidate.
     */
    fun evaluate(
        document: OcrDocument?,
        reading: LabelReading.Confident,
        scale: ScaleAmbiguity.Verdict?,
        disputed: DisputedCandidates,
    ): Verdict {
        if (document == null) return Verdict.Refused("no document to compare the reading against")

        // Only insufficient/ambiguous SCALE evidence is newly admitted. Everything else that
        // ReadingEligibility already checked once — a demonstrated cross-run dispute would have
        // already produced Outcome.Conflicted upstream, never reaching this function at all, but the
        // scale verdict itself is re-stated here so a caller cannot accidentally offer confirmation
        // for a reading whose refusal came from something other than scale.
        when (scale) {
            is ScaleAmbiguity.Verdict.Established -> Unit // Already eligible upstream; unreachable here.
            is ScaleAmbiguity.Verdict.Unsupported -> Unit
            is ScaleAmbiguity.Verdict.Ambiguous -> Unit
            null -> return Verdict.Refused("no scale question was ever asked — no candidate or no document")
        }

        val candidate = reading.candidate

        // Physical plausibility: a check ReadingEligibility never performs, and the one the task
        // requires be added for this newly-opened surface. A structurally sound-looking but
        // physically impossible figure (a fused digit reading `790`) gets no confirmation screen at
        // all — the same refusal AssistedReadingScreen.BasisActions already applies to a typed value.
        val basis = candidate.basis
        if (basis == null || !CarbPlausibility.isPlausiblePer100(candidate.value, basis)) {
            return Verdict.Refused("the value is not physically plausible under its own basis")
        }

        // Every structural exclusion RecoveryCandidates applies — child row, wrong clause, missing
        // unit, column ownership, cross-column contradiction, distinct-run dispute — re-asked
        // identically via [RecoveryCandidates.ofIncludingScaleRefusals], which is exactly [of]'s own
        // candidate construction MINUS the final scale-eligibility gate this object exists to
        // re-decide. Using plain [of] here would be self-defeating: it already excludes every
        // candidate refused for scale, which is precisely the population this function is asked
        // about.
        //
        // Matched by value and basis rather than by exact box equality: [CarbCandidate.geometry] can
        // be a recovered clause's *span* over several elements (see [MergedTotalRowRecovery] and
        // [ScaleAmbiguity]'s own `candidateElement` for the identical reason it does not use box
        // equality either), while [RecoveryCandidates.Candidate.box] is always one row element's own
        // box. A span and the single element carrying its accepted number legitimately differ.
        val located = RecoveryCandidates.ofIncludingScaleRefusals(document, disputed)
            .firstOrNull {
                it.reading.amount.compareTo(candidate.value) == 0 &&
                    perHundredBasis(it.reading.basis) == candidate.basis
            }

        if (located == null) {
            return Verdict.Refused(
                "the candidate does not appear among the label's own structurally-admissible " +
                    "readings — excluded as a child row, wrong clause, missing unit, unowned column, " +
                    "a cross-column contradiction, or a distinct recognition run's dispute",
            )
        }

        // ## A second, full-document cross-column check — not redundant with the one above
        //
        // [RecoveryCandidates] scopes its cross-column contradiction check to the LOCALIZED PANEL
        // (`document.copy(elements = panel.elements)`), which can hold measurably fewer rows than
        // the full document — measured directly on a Hellmann's mayonnaise capture
        // (`docs/Scan evidence 2/20260906-135616-088`): the full 325-element document gives
        // [CrossColumnRatioCheck] three coherent supporting rows and reports `Conflicting` (row
        // ratio 0.069 vs the table's own 0.150 — exactly what [AutomaticVerification] independently
        // computes and refuses), while the 188-element panel-scoped document falls to
        // `NotEnoughEvidence` for want of one of those rows, so [RecoveryCandidates] itself never
        // catches the contradiction. [confident.candidate] carries the reading's own full-document
        // geometry — the same object [AutomaticVerification.verify] already checked this against —
        // so re-asking here cannot introduce a new geometry to disagree about; it only closes the
        // gap between two call sites that were, until now, allowed to see different amounts of the
        // same table.
        if (CrossColumnRatioCheck.check(document, candidate) is CrossColumnRatioCheck.Verdict.Conflicting) {
            return Verdict.Refused(
                "the label's own other rows contradict this reading, checked against the full " +
                    "document rather than the localized recovery panel",
            )
        }

        return Verdict.Eligible(
            candidate = located,
            reason = "the total-carbohydrate row and its basis are established; only the decimal " +
                "scale is unresolved, and the user is shown the printed row to check it against",
        )
    }

    /** Whether [basis] came from something the label declared, for callers that need to distinguish. */
    fun isDeclaredServing(basis: CarbBasis): Boolean =
        basis is CarbBasis.PerQuantity || basis is CarbBasis.PerUnknownServing

    /** The [NutritionBasis] behind a [CarbBasis.PerHundred], or null for a declared-serving basis. */
    fun perHundredBasis(basis: CarbBasis): NutritionBasis? = (basis as? CarbBasis.PerHundred)?.basis
}
