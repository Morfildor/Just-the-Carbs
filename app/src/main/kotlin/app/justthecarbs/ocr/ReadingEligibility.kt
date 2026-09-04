package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis

/**
 * The one decision about whether the app may **put a carbohydrate figure in front of the user**.
 *
 * ## Why this exists as its own object
 *
 * Two surfaces offer a value: the automatic path proposes one ([AutomaticScanAdvance.presentation]),
 * and the recovery screen lists the ones a tap can select ([RecoveryCandidates]). Until this pass
 * they asked *different questions with different rules*, and the asymmetry was deliberate and
 * documented:
 *
 * ```
 * automatic : refuse Ambiguous AND Unsupported   (the app is proposing; the user cannot check it)
 * recovery  : refuse Ambiguous only              (the user is pointing at a number they can see)
 * ```
 *
 * That reasoning is half right, and the half that is wrong cost a release blocker. "The user tapped
 * it" establishes **which row they meant**. It establishes nothing whatever about the *decimal scale*
 * of the digits the recognizer returned, because the user is looking at the package and the app is
 * looking at its own recognition of it — and when those disagree, the tap does not reconcile them.
 *
 * Measured on `docs/Scan Evidence 03-09/20260903-085128-913` and its eighth-session sibling: a red
 * Lidl label prints `7,2 g / 100 g`, the recognizer returned `12g`, and recovery offered
 * **`12 g / 100 g`** — one tap from the calculator, with nothing on screen to distinguish it from the
 * printed figure. A recovery choice is still a value proposed by the app.
 *
 * ## Why the naive fix was measured and rejected
 *
 * Making recovery simply refuse [ScaleAmbiguity.Verdict.Unsupported] — the obvious symmetry — was
 * measured across every committed session fixture and **deletes the Korean sauce's legitimate
 * `6 g / 18 g serving`** (third, fourth and fifth sessions). That is a control that must keep
 * working, and the eighth session recorded exactly this trade-off as the reason the two surfaces were
 * left inconsistent.
 *
 * The two numbers really are indistinguishable to [ScaleAmbiguity]: both are bare separatorless
 * integers with nothing on their row to pair against, so both are `Unsupported`.
 *
 * ## The distinction that does separate them
 *
 * They differ in **where their basis came from**, which is already a type in this codebase:
 *
 * | | value | basis | how the basis was established |
 * |---|---|---|---|
 * | Korean sauce | `6` | [CarbBasis.PerQuantity] `18 g serving` | the label *printed* `Serv. size: 1 Tbsp (18 g)` |
 * | red Lidl | `12` | [CarbBasis.PerHundred] | *inferred* from a column the app resolved |
 *
 * A serving declaration is a sentence a human wrote on the package and the app read back. The figure
 * beside it is the figure that sentence governs, and the user tapping it is confirming a pairing the
 * *label* asserts. An inferred per-hundred basis asserts only that a cell sat under a header — true,
 * and silent about whether the digits in that cell are the digits on the package.
 *
 * So the rule is about **evidence for the digits**, never about magnitude, product or wording:
 *
 * > A figure may be offered when something establishes its scale: a decimal separator it or a
 * > sibling carries, corroboration from outside this recognition run, or a serving basis the label
 * > itself declared. A separatorless value under a basis the app *inferred*, corroborated by
 * > nothing, is refused — by every surface, identically.
 *
 * ## The ordering correction (tenth pass)
 *
 * An earlier revision tested corroboration **first** and returned eligible on it outright, on the
 * stated ground that agreement between distinct runs settles scale "by a route that is not
 * scale-invariant". That ground is false for both routes this app has, and the falsity is
 * arithmetic — see [evaluate] and `ScaleInvarianceTest`. A demonstrated
 * [ScaleAmbiguity.Verdict.Ambiguous] is therefore checked before corroboration.
 *
 * ## The completion of that correction (thirteenth pass)
 *
 * The tenth pass stopped one step short: it refused a demonstrated [ScaleAmbiguity.Verdict.Ambiguous]
 * before corroboration but left [ScaleAmbiguity.Verdict.Unsupported] *behind* it, so a lone
 * separatorless value two runs agreed on was still admitted. The arithmetic does not support that
 * split — a scale-invariant route cannot see a missing separator whether or not a sibling happened
 * to be recognised beside it — and `20260904-081421-421` is the path taken on hardware, advancing
 * automatically with `scale evidence: UNSUPPORTED` recorded in the same bundle.
 *
 * So corroboration now settles neither. **`41g` and the peanut butter's `11 g` are still shown**, one
 * step later, through the focused-entry route [AutomaticScanAdvance.Presentation.Recover] opens: the
 * photograph is kept, the basis the label stated is preserved, and the user supplies only the digits
 * they can read. What is withdrawn is permission to skip *both* confirmations on evidence that
 * cannot see a decimal point — not the reading itself.
 *
 * ## What this never does
 *
 * It does not divide, shift, repair or propose a rescaled value; `12` never becomes `1.2`. It does
 * not rank, score, or prefer one candidate over another. It only ever **withholds**, and a withheld
 * figure routes to focused entry with the basis preserved, where the user types what they can read.
 */
object ReadingEligibility {

    /** Why a figure may be shown, or why it may not. Every branch names its evidence. */
    sealed interface Verdict {

        /** The sentence a bundle prints, whichever way the decision went. */
        val reason: String

        val isEligible: Boolean get() = this is Eligible

        /** The figure may be offered. [reason] is what made it offerable, for the evidence bundle. */
        data class Eligible(override val reason: String) : Verdict

        /** The figure may not be offered. [reason] is what is missing. */
        data class Refused(override val reason: String) : Verdict
    }

    /**
     * Whether a figure whose scale evidence is [scale] and whose basis is [basis] may be shown.
     *
     * [corroborated] is evidence from **outside** this recognition run — another view reading the
     * same digits, or the label's own other rows agreeing through [CrossColumnRatioCheck]. It is
     * passed in rather than recomputed because the two callers establish it differently and both
     * already hold the answer: the automatic path from [AutomaticVerification.Verdict.mayBeProposed],
     * recovery from the cross-run dispute set.
     *
     * ## It is proposal-grade evidence, and that is weaker than it sounds
     *
     * Note this parameter governs whether a figure may be **shown**, never whether it may skip the
     * user's confirmation. It is therefore satisfied by agreement between two views of *one*
     * photograph, which cannot settle absolute decimal scale or an optical corruption — both views
     * inherit the same pixels. That is why the [ScaleAmbiguity.Verdict.Ambiguous] refusal below is
     * checked **before** this flag rather than after it, and why
     * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] now requires a second
     * [PhysicalObservationId] that this flag does not.
     *
     * The order of the checks is the order of the evidence's strength, and each returns its own
     * sentence so a bundle can say which one applied.
     */
    fun evaluate(
        scale: ScaleAmbiguity.Verdict?,
        basis: CarbBasis?,
        corroborated: Boolean,
        /**
         * Whether [corroborated] came from evidence that can see decimal scale.
         *
         * True for a second [PhysicalObservationId] or the label's own structure; **false** for
         * agreement between views of one photograph, which cannot see a separator that is missing
         * from the ink.
         *
         * Measured on `20260904-113950-065`: a Hellmann's bottle prints `1,3 g / 100 ml` and every
         * view of the one capture read `13g`, so agreement was unanimous and unanimously wrong by a
         * factor of ten. Defaults to true so existing callers — recovery, where a human is pointing at
         * a number they can see — keep their behaviour unchanged.
         */
        corroborationSettlesScale: Boolean = true,
    ): Verdict {
        // ## Corroboration is NOT checked first, and the reason is arithmetic
        //
        // An earlier revision returned `Eligible` on `corroborated` outright, documenting the order
        // as load-bearing because a reading distinct runs agree on "has had its scale settled by a
        // route that is not scale-invariant". **Both routes the app actually has are scale-invariant**,
        // and that is measurable rather than arguable — see [ScaleInvarianceTest], which asserts each
        // route reports the identical verdict on a label and on its ×10 twin:
        //
        // * [AutomaticVerification.Route.CROSS_COLUMN] compares this row's serving-to-per-100 ratio
        //   against the table's median. Multiplying every value by ten leaves every ratio unchanged,
        //   which is exactly what a uniform separator loss does — the sixth session's truffle label
        //   read `89`/`13` for a printed `8,9`/`1,3` and every other row collapsed with it.
        // * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] compares two runs' digits. When the
        //   separator is absent from the *pixels* rather than lost by one pass, both runs read the
        //   same wrong scale and agree perfectly. The eighth session's red label repeated its error
        //   across four recognitions of the same package.
        //
        // > Evidence unchanged by multiplying all recognized values by ten cannot establish absolute
        // > decimal scale.
        //
        // So a demonstrated ambiguity is asked **before** corroboration. That is a refusal added, not
        // a permission widened: nothing that was refused becomes eligible here.
        if (scale is ScaleAmbiguity.Verdict.Ambiguous) {
            return Verdict.Refused(
                "a common rescaling of '${scale.candidateText}' and '${scale.pairedText}' is " +
                    "equally consistent with the recognised text, and corroboration by a " +
                    "scale-invariant route cannot distinguish the two",
            )
        }

        // A separator that survived in the candidate's own recognised token. Not scale-invariant: a
        // ×10 rescale of the document changes this text, so it is genuine evidence about scale.
        if (scale is ScaleAmbiguity.Verdict.Established) {
            return Verdict.Eligible("the recognised text states the scale (${scale.reason})")
        }

        // Corroboration, now that no ambiguity has been demonstrated. It is kept — and kept ahead of
        // the basis test — because it remains the strongest evidence available against a *single*
        // misread digit, which is what both routes were built for and still catch. What it no longer
        // does is overrule the one thing it cannot see.
        // Corroboration may only answer the scale question when it can *see* scale.
        //
        // `Unsupported` means the recognised text says nothing about the separator either way. Filling
        // that silence with agreement between views of one photograph is precisely the mistake the
        // 2026-09-04 pass removed from automatic advancement: every view inherits the same ink, so a
        // separator missing from the print is missing from all of them and their agreement is
        // unanimous and uninformative. The Hellmann's `1,3 g` -> `13g` is the measured instance.
        //
        // Agreement is still admitted for a scale that is *not* in question — the `Established` branch
        // above already returned, and a declared serving basis is handled below — so this narrows the
        // rule to the one question correlated views cannot answer.
        if (corroborated && (corroborationSettlesScale || scale !is ScaleAmbiguity.Verdict.Unsupported)) {
            return Verdict.Eligible("corroborated by evidence outside this recognition run")
        }

        // Unsupported, or never asked. The basis's provenance is what is left to distinguish a
        // figure the label vouched for from one the app placed itself.
        return when (basis) {
            // The label printed a serving sentence and this figure is what that sentence governs.
            // Tapping it confirms a pairing the package asserts, not one the app inferred.
            is CarbBasis.PerQuantity, is CarbBasis.PerUnknownServing ->
                Verdict.Eligible("the label declared the serving this figure is measured per")

            // Inferred from a resolved column, with no separator and nothing corroborating it. This
            // is the red label's `12`.
            is CarbBasis.PerHundred, null -> Verdict.Refused(
                "no decimal separator, nothing outside this recognition agreed, and the basis was " +
                    "inferred rather than declared — the scale is not established",
            )
        }
    }

    /**
     * Whether [scale] is strong enough to skip the confirmation step **entirely**.
     *
     * ## Two questions, not one (thirteenth session)
     *
     * [evaluate] answers *"may this figure be put in front of the user?"*. Auto-advance asks a
     * strictly stronger question — *"may the user never be asked about it at all?"* — and until this
     * pass the two shared one answer. That is what let `20260904-081421-421` advance automatically
     * with `scale evidence: UNSUPPORTED` recorded in its own bundle.
     *
     * The distinction matters because the two failures are not comparable. Showing a wrong figure on
     * the frozen photograph costs the user a glance at the package they are holding, next to the row
     * the app has highlighted. Advancing on one puts it straight into the calculator with nothing to
     * check it against — and the brief lists exactly that as a release blocker: *"0 cross-run
     * agreement bypassing unresolved absolute scale"*.
     *
     * ## Why corroboration cannot answer this one
     *
     * Both of this app's verification routes are unchanged by multiplying every recognised value by
     * ten — [AutomaticVerification.Route.CROSS_COLUMN] compares ratios, and
     * [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] compares two runs that can lose the same
     * separator twice (the eighth session's red label did so across four recognitions of one
     * package). See `ScaleInvarianceTest`. So corroboration is real evidence against a *single*
     * misread digit and no evidence at all about absolute scale.
     *
     * Only the recognised text itself can settle that, which is [ScaleAmbiguity.Verdict.Established].
     *
     * ## What this costs, stated plainly
     *
     * A genuine printed integer — `41 g`, the peanut butter's `11 g` — no longer skips the
     * confirmation. It is still **shown**, immediately, on the frozen photograph with its row
     * highlighted, one tap from the calculator: [evaluate] still returns `Eligible` for it on
     * corroboration, which is the whole reason the two questions had to be separated rather than the
     * refusal simply moved. Manual-QA rows 29.12 and 30.18 are affected in exactly that way and no
     * other — the figure still reaches the user, one tap later — and both rows are unticked, so no
     * verified behaviour is being contradicted.
     */
    fun mayAdvanceWithoutConfirmation(scale: ScaleAmbiguity.Verdict?): Boolean =
        scale is ScaleAmbiguity.Verdict.Established

    /**
     * The same decision for a [RecoveryCandidates.Candidate], which already carries its own basis.
     *
     * Recovery has no [AutomaticVerification] verdict of its own; a value a distinct run contradicted
     * is removed earlier by [DisputedCandidates], so nothing reaching here is corroborated *or*
     * disputed by another run. Passing `corroborated = false` is therefore the honest input, not a
     * conservative one.
     */
    fun evaluate(document: OcrDocument, candidate: RecoveryCandidates.Candidate): Verdict = evaluate(
        scale = ScaleAmbiguity.check(document, probeFor(candidate)),
        basis = candidate.reading.basis,
        corroborated = false,
    )

    /**
     * A [CarbCandidate] standing for a recovery choice, so [ScaleAmbiguity] can be asked about it.
     *
     * The parser has no candidate for this cell — that is why the user is on the recovery screen —
     * and this construction mirrors [RecoveryCandidates]'s own probe exactly, so the two cannot end
     * up asking about subtly different candidates.
     */
    private fun probeFor(candidate: RecoveryCandidates.Candidate) = CarbCandidate(
        sourceLine = candidate.rowText,
        label = candidate.rowText,
        value = candidate.reading.amount,
        basis = null,
        score = 0,
        geometry = candidate.box,
        evidence = emptyList(),
        column = null,
    )
}
