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
        document: OcrDocument?,
    ): Boolean = mayAdvance(outcome) &&
        verification.mayAdvanceAutomatically &&
        // ## The scale rule applies to advancing too, and it did not used to
        //
        // This function read `mayAdvance && verification.mayAdvanceAutomatically` and consulted
        // [ReadingEligibility] nowhere, so the strictest transition in the app — the one that skips
        // *both* confirmations — was the only one no scale rule guarded. [mayConfirm] gained that
        // guard in the eighth session; advancing never did, because advancing additionally required
        // verification and verification was assumed to settle scale.
        //
        // Both verification routes are scale-invariant (see [ReadingEligibility.evaluate]), so on a
        // uniformly decimal-collapsed label they report agreement and this gate admitted the reading
        // outright. Measured: the truffle's `89`/`13` verifies through `CROSS_COLUMN` with every
        // ratio intact, and through `DISTINCT_OCR_AGREEMENT` when two runs read the same collapse.
        //
        // Asking the same eligibility object the proposal path asks is what keeps one decision
        // across both surfaces. A null document cannot establish a scale, so it refuses — the safe
        // direction for a missing input to a safety gate, and unreachable in production where the
        // scanner always passes the capture's own document.
        eligibility(outcome, verification, document)?.isEligible == true &&
        // ## And advancing additionally requires POSITIVE scale evidence (thirteenth session)
        //
        // The line above asks *"may this figure be shown?"*. Advancing asks the strictly stronger
        // *"may the user never be asked about it?"*, and until this pass the two shared one answer.
        // `20260904-081421-421` is that gap taken on hardware: `scale evidence: UNSUPPORTED`,
        // `automatic-verification: DISTINCT_OCR_AGREEMENT`, `final UI action: AUTO_ADVANCE`.
        //
        // The value there was right — the jar prints `11 g` — but the reasoning would have admitted
        // a collapsed `1,1` identically, because both verification routes are scale-invariant. The
        // brief names this a release blocker, and `Unsupported` says only that this rule found
        // nothing to say, which is not permission to skip the one screen where a human could catch
        // it.
        //
        // The reading is **not** discarded: `presentation` falls through to `ConfirmOnCapture`,
        // which shows it on the frozen photograph with its row highlighted. The cost is one tap on
        // a genuine integer label; the benefit is that no decimal collapse can reach the calculator
        // unseen.
        ReadingEligibility.mayAdvanceWithoutConfirmation(scaleVerdict(outcome, document))

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
     *
     * ## The correction (eighth session, `20260902-213005-691`)
     *
     * The test below used to read `scaleVerdict(...) !is Ambiguous`, i.e. *confirm unless ambiguity
     * was demonstrated*. Since [ScaleAmbiguity] could only demonstrate ambiguity from a **pair** of
     * separatorless values, a lone separatorless value returned `Established` and confirmed.
     *
     * That is what put `12 g / 100 g` in front of a user holding a package printing `7,2 g`, with
     * `automatic-verification: NONE` recorded in the same bundle. The recognizer had fused the `7,`
     * into the nutrient word, leaving one bare number on the row and therefore nothing to pair with
     * — so the *worse* the recognition, the more confident this gate became.
     *
     * The polarity is now the other way round: an unverified reading is confirmed only on
     * **positive** scale evidence. [ScaleAmbiguity.Verdict.Unsupported] — the new "this rule has
     * nothing to say" — is not permission, and neither is a null verdict from a missing document.
     *
     * ### What this deliberately does not do
     *
     * It does not reject integers. A legitimate whole-number label still confirms whenever anything
     * outside the single run supports it, which is what [AutomaticVerification] answers one line
     * above: a second recognition run reading the same digits, or the label's own other rows. Only
     * the combination *unverified **and** no scale evidence* is refused, and that combination is
     * precisely "one recognition said so and nothing else did".
     */
    fun mayConfirm(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument? = null,
    ): Boolean = eligibility(outcome, verification, document)?.isEligible == true

    /**
     * The centralized eligibility verdict for [outcome]'s reading, or null when there is no reading.
     *
     * Delegates to [ReadingEligibility], which is the **single** place that decides whether a figure
     * may be put in front of the user. [RecoveryCandidates] asks the same object about the same
     * question, so the two surfaces cannot contradict each other about the same candidate — which
     * they previously did, by design, and which cost the red label's `12` reaching the user through
     * a tap after the automatic path had correctly refused it.
     *
     * Exposed rather than kept private so the evidence bundle can record *why* a figure was shown or
     * withheld, in the same words the decision was made in.
     */
    fun eligibility(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument?,
    ): ReadingEligibility.Verdict? {
        val confident = confidentReading(outcome) ?: return null
        return ReadingEligibility.evaluate(
            scale = scaleVerdict(outcome, document),
            // ## Always `PerHundred`, and that is faithful rather than lossy
            //
            // [ReadingEligibility] branches on the basis's *provenance*: a declared serving
            // ([app.justthecarbs.domain.CarbBasis.PerQuantity] /
            // [app.justthecarbs.domain.CarbBasis.PerUnknownServing]) is a sentence the package
            // printed, and is the escape hatch that keeps the Korean sauce's `6 g / 18 g serving`
            // offerable. So wrapping everything as `PerHundred` looks like it discards that
            // distinction.
            //
            // It does not, because **the automatic path structurally cannot produce a declared
            // serving basis.** [CarbCandidate]'s `init` requires `column` to be `PER_100_G`,
            // `PER_100_ML` or null, so a serving column can never supply a candidate at all, and
            // `candidate.basis` is a [app.justthecarbs.domain.NutritionBasis] — an enum of the two
            // per-hundred bases and nothing else. There is no serving declaration to lose here.
            //
            // The consequence, stated plainly so it is a decision rather than an accident: the
            // "the label declared the serving" branch is reachable **only** through recovery, where
            // [RecoveryCandidates] builds a [app.justthecarbs.domain.CarbReading] that can carry
            // `PerQuantity`. Do not "fix" this by widening the wrapping — that would assert a
            // declaration the automatic path never read.
            basis = confident.candidate.basis?.let { app.justthecarbs.domain.CarbBasis.PerHundred(it) },
            // ## The *proposal* question, not the advancement one
            //
            // This asks whether the figure may be put on screen behind a confirmation tap, which is
            // strictly weaker than whether it may skip that tap. [AutomaticVerification.mayBeProposed]
            // therefore also accepts agreement between two views of one photograph.
            //
            // Passing `mayAdvanceAutomatically` here conflated the two, and the cost was measured: once
            // same-frame agreement stopped verifying advancement (2026-09-04), `20260904-113818-873`
            // (`57 g per 100 gram`) and `20260904-114311-968` (`koolhydraten 35 g`) fell from
            // `CONFIRM_ON_CAPTURE` to `RECOVERY` — the app still held the correct value and stopped
            // showing it. Removing a wrong automatic route must not remove a correct proposal.
            //
            // Every scale rule still applies on top of this: [ReadingEligibility] refuses a
            // demonstrated [ScaleAmbiguity.Verdict.Ambiguous] *before* it consults corroboration at
            // all, precisely because agreement is scale-invariant.
            corroborated = verification.mayBeProposed,
            // ## Why same-frame corroboration is still allowed to answer the scale question here
            //
            // It was briefly restricted to advancement-grade evidence, to suppress
            // `20260904-113950-065` — a Hellmann's bottle printing `1,3 g / 100 ml` that every view of
            // the one capture read as `13g`, and which is therefore offered for confirmation at ten
            // times the printed figure.
            //
            // **Measured, that restriction costs more than it saves.** All three separatorless
            // integers in the corpus are `Unsupported` for the identical reason — no paired value in
            // the clause — so nothing distinguishes them:
            //
            // | capture | printed | read | correct |
            // |---|---|---|---|
            // | `113818-873` | `57 g`  | `57`   | yes |
            // | `114311-968` | `35 g`  | `35g.` | yes |
            // | `113950-065` | `1,3 g` | `13g`  | no  |
            //
            // Restricting the flag hid `57` and `35` — two correct readings pushed into recovery — to
            // suppress one wrong proposal that **no rule could have fixed anyway**: `1,3` appears
            // nowhere in any recognition of that capture, so the correct value is simply absent and
            // only a different photograph can supply it.
            //
            // ## Reversed (2026-09-05): same-frame agreement no longer settles this question
            //
            // The paragraph above recorded that restricting this flag "costs more than it saves",
            // trading `57`/`35` (pushed to recovery) against `13` (a wrong one-tap proposal). That
            // measurement is unchanged and is kept above for the record, but the trade itself is no
            // longer accepted: a wrong figure prefilled for one-tap acceptance on a dosing input is
            // not an acceptable cost for keeping two correct figures one screen closer, and the
            // 2026-09-05 evidence-reliability plan states the rule directly — "Unsupported decimal
            // scale from a single physical observation must never prefill a value for one-tap
            // acceptance. Blank focused entry is the correct outcome; digit repair or a prefilled
            // guess is not."
            //
            // `57` and `35` are not lost, only slowed: [presentation] still routes an ineligible
            // confident reading with an established basis to [Presentation.Recover], which keeps the
            // photograph and opens focused entry with the basis preserved — exactly the path this
            // file already uses for the peanut butter's `11 g` and the truffle's `89`. What changes is
            // that a same-observation agreement, which cannot see a missing decimal separator any
            // more than a single run can, no longer buys the one-tap shortcut past that screen.
            //
            // `Route.CROSS_COLUMN` and `Route.DISTINCT_OCR_AGREEMENT` are unaffected: both are
            // evidence from outside this physical observation — the label's own other rows, or a
            // genuinely separate photograph (Task 4's real `PhysicalObservationId`) — and neither
            // shares the pixels that lost the separator in the first place.
            corroborationSettlesScale = verification.route != AutomaticVerification.Route.NONE,
        )
    }

    /**
     * The confident reading [outcome] carries, from either outcome type that can carry one.
     *
     * ## Why `NeedsVerification` belongs here (ninth session, 2026-09-03)
     *
     * [mayConfirm] used to begin `if (!mayAdvance(outcome)) return false`, and [mayAdvance] answers
     * `false` for [EvidenceResolver.Outcome.NeedsVerification] — correctly, because that outcome must
     * not *advance*. But "may not advance" was then also read as "may not be **proposed**", and those
     * are different questions: `NeedsVerification` is precisely the resolver saying *one pass read
     * this, nothing corroborated it, please check it against the package*. That is the definition of
     * a proposal.
     *
     * Measured on `docs/Scan Evidence 03-09/20260903-085019-213` and `-085032-269`. A clear, flat,
     * high-contrast Dutch table prints `Koolhydraten, waarvan 2,8 g`. Pass A reconstructed the row and
     * resolved the `per 100g` column correctly, but the printed `g` came back as a `9` — on the fat
     * row too, so it is a property of the recognition — and [UnitAccompanimentPolicy] rightly declined
     * a unit-less value. Strategy B, recognising the user's crop, read `Confident 2.8/PER_100_G`. The
     * resolver returned `NeedsVerification`, which is the right verdict. The bundles then record
     * `final UI action : RECOVERY`, whose only offer was the same `2.8` **suppressed**. The app held
     * the correct printed value twice and showed the user nothing both times.
     *
     * Returning the reading from both outcome types lets one scale rule govern both, rather than
     * having a second, weaker path grow beside it.
     */
    fun confidentReading(outcome: EvidenceResolver.Outcome): LabelReading.Confident? = when (outcome) {
        is EvidenceResolver.Outcome.Resolved -> outcome.reading as? LabelReading.Confident
        is EvidenceResolver.Outcome.NeedsVerification -> outcome.reading
        // Competing candidates, a disagreement between passes, or nothing at all. None of these is a
        // reading, and none becomes one by being asked about differently.
        is EvidenceResolver.Outcome.Unresolved -> null
        is EvidenceResolver.Outcome.Conflicted -> null
        EvidenceResolver.Outcome.Nothing -> null
    }

    /**
     * Whether an **automatic** attempt may present its own outcome instead of falling back to the
     * crop screen.
     *
     * ## The failure this closes (ninth session, 2026-09-03)
     *
     * The scanner's automatic veto read `automatic && !mayAdvance(outcome)`, and everything it
     * declined skipped the entire outcome `when` and went to the crop screen. [mayAdvance] answers
     * `false` for [EvidenceResolver.Outcome.NeedsVerification], so a **correct** reading that the
     * resolver had explicitly marked *"please check this against the package"* was discarded before
     * anything could show it.
     *
     * Measured on three captures of that session — the white Dutch table twice
     * (`Confident 2.8/PER_100_G`, the printed value) and the green drink once
     * (`Confident 0.5/PER_100_ML`, the printed value). Every one reached
     * `final UI action : RECOVERY`, and recovery then suppressed the same number for having lost its
     * unit in Pass A. The recording shows the user retrying the white table repeatedly and never
     * being offered `2,8`.
     *
     * ## Why the crop screen is not the right fallback for this outcome
     *
     * The veto exists so an *unresolved or contested* capture reaches the user only after they have
     * had the chance to tighten the rectangle — the one lever they hold over ambiguity, conflict and
     * a failed read. That reasoning is sound and is unchanged for
     * [EvidenceResolver.Outcome.Unresolved], [EvidenceResolver.Outcome.Conflicted] and
     * [EvidenceResolver.Outcome.Nothing], all of which still decline.
     *
     * It does not apply to a confident reading whose only weakness is that one pass produced it.
     * Cropping cannot corroborate a reading; only another pass or the label's own structure can, and
     * both have already been asked by the time this is called. So the crop step costs the user an
     * interaction and returns nothing.
     *
     * ## What still declines
     *
     * Everything [presentation] does not classify as presentable. In particular a reading whose scale
     * the evidence cannot establish yields [Presentation.Recover], which keeps the photograph and
     * routes to focused entry — it does **not** silently confirm. That is what keeps the eighth
     * session's red-label `12` out of the user's way: it is unverified, its token carries no decimal
     * separator, and it has nothing to pair with, so [ScaleAmbiguity] reports
     * [ScaleAmbiguity.Verdict.Unsupported] and no confirmation is offered for it.
     */
    fun mayPresentAutomatically(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument?,
    ): Boolean = when (presentation(outcome, verification, document, automatic = true)) {
        Presentation.Advance -> true
        Presentation.ConfirmOnCapture -> true
        Presentation.Recover -> true
        // No confident reading to present. The rectangle is the user's most direct lever here, which
        // is exactly what the automatic veto was written for.
        Presentation.NotApplicable -> false
    }

    /**
     * How a resolved capture should be presented, as one value rather than a chain of `if`s.
     *
     * ## Why this is a type and not three booleans in the scanner
     *
     * The eighth session's P0 was a **branch**, not a rule: `readSelectedTable` released the frozen
     * photograph at the top of its `Resolved` case and then chose between advancing, confirming and
     * recovering — so the confirmation branch inherited a recycled bitmap and fell back to drawing
     * its card over the live camera preview. Nothing tested that branch, because it was an anonymous
     * `else` inside a composable that binds a real camera.
     *
     * Naming the outcomes makes the decision unit-testable in the JVM, and makes the invariant
     * checkable in one place: **only [Advance] is terminal.** [ConfirmOnCapture] and [Recover] both
     * keep the photograph, because both still have a question for the user.
     */
    enum class Presentation {
        /** Verified. Skip both confirmations; the capture may be released. */
        Advance,

        /**
         * Confident and confirmable, but nothing outside this recognition run agreed.
         *
         * Shown on the frozen photograph with the row highlighted, never on the live preview.
         */
        ConfirmOnCapture,

        /**
         * Confident, and the digits themselves are not trustworthy enough to propose.
         *
         * Routes to focused entry on the photograph, with the stated basis preserved.
         */
        Recover,

        /** Not a confident reading at all; the caller's existing handling applies. */
        NotApplicable,
    }

    /**
     * Which of [Presentation] applies to [outcome].
     *
     * [automatic] mirrors the scanner's own gate: a reading reached after the user confirmed a crop
     * keeps its confirmation step, because there the user has already been asked a question and an
     * answer appearing without acknowledgement reads as the app having ignored them.
     */
    fun presentation(
        outcome: EvidenceResolver.Outcome,
        verification: AutomaticVerification.Verdict,
        document: OcrDocument?,
        automatic: Boolean,
    ): Presentation {
        // Both outcome types that can carry a confident reading are handled. `NeedsVerification`
        // can never reach [Presentation.Advance] — [mayAdvanceVerified] delegates to [mayAdvance],
        // which refuses it — so this widens what may be *proposed* without widening what may be
        // accepted without asking. See [confidentReading].
        val confident = confidentReading(outcome) ?: return Presentation.NotApplicable
        if (confident.candidate.basis == null) {
            // "Grams of what?" is the one question this app never answers for the user.
            return if (mayConfirm(outcome, verification, document)) {
                Presentation.ConfirmOnCapture
            } else {
                Presentation.Recover
            }
        }
        if (automatic && mayAdvanceVerified(outcome, verification, document)) return Presentation.Advance
        if (!mayConfirm(outcome, verification, document)) return Presentation.Recover
        return if (verification.mayAdvanceAutomatically && !automatic) {
            // Verified, reached through a **confirmed crop**. The user has already been asked a
            // question there, so the ordinary card applies and the caller selects it from the
            // outcome type.
            //
            // ## The `!automatic` bound (thirteenth session)
            //
            // This branch used to test verification alone, on the reasoning that "verified but not
            // advancing" could only mean a confirmed crop — true while verification was the *only*
            // thing standing between a confident reading and `Advance`.
            //
            // Since advancing additionally requires positive scale evidence (see
            // [mayAdvanceVerified]), an **automatic** capture can now be verified, eligible, and
            // still not advance: the peanut butter's corroborated `11 g`, whose scale is
            // `Unsupported`. Without this bound such a capture returned `NotApplicable`, which
            // [ScanPresentationDecision] maps to a non-proposal screen — so a correct, corroborated
            // reading was withheld from the very screen that exists to show it.
            //
            // On the automatic path the answer is the same as for any other uncorroborated-scale
            // reading: propose it on the frozen photograph, one tap from the calculator.
            Presentation.NotApplicable
        } else {
            Presentation.ConfirmOnCapture
        }
    }

    /**
     * The scale verdict for [outcome], for the gate above and for the evidence bundle.
     *
     * Null means the question was never asked: the outcome carries no confident candidate, or no
     * document was supplied.
     *
     * **Null is not permission.** An earlier revision of this KDoc said "an absent document must
     * never *create* a refusal", which was true while [mayConfirm] refused only on a demonstrated
     * ambiguity. Now that confirmation requires positive scale evidence, a null verdict means an
     * unverified reading is not offered for one-tap confirmation — it goes to focused entry with its
     * basis preserved, which is the safe direction for a missing input to a safety gate. In
     * production `document` is the capture's own `OcrDocument` and is never null; this is about which
     * way the code fails, not about a path a user reaches.
     */
    fun scaleVerdict(
        outcome: EvidenceResolver.Outcome,
        document: OcrDocument?,
    ): ScaleAmbiguity.Verdict? {
        val candidate = confidentReading(outcome)?.candidate ?: return null
        if (document == null) return null
        return ScaleAmbiguity.check(document, candidate)
    }
}
