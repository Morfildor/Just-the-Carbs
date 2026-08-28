# Regulatory release gate — Just the Carbs

**Scope of this gate: public production publication.** It does **not** gate internal or closed
testing, which distribute to named testers the owner controls rather than to the public.

**Internal testing is DEPLOYED (2026-08-26) and CLOSED TESTING IS ACTIVE** — `1.0.0` /
`versionCode 1`, the same artifact progressing through both tracks, with 12+ testers opted in and
the closed-testing period running.
That was correct: no row here applied to it, and none is applied retroactively.

**Production status: GATED.** This document records engineering evidence and owner actions; it is not
a regulatory determination. Last engineering review: 2026-08-14; scope and classification review
2026-08-26.

**Launch scope is EU-only for v1** (assessment Annex A.1), set in Play Console under
countries/regions. Rows conditional on other markets are not blockers for this release.

**Which open rows actually block production.** Only two:

- **A1/A3 signature** — the assessment is written and its conclusion complete; it needs the
  manufacturer's signature and date (§9 of that document).
- **A6 / E4 listing wording** — the §44 conclusion is conditional on its §7.1 marketing constraints,
  so published copy must be read against them.

Every other open row below is either **conditional on a market the owner is not releasing to**
(A5 — v1 is EU-only), **the owner's own conservative recommendation rather than a legal
precondition** (A2 — under the MDR the manufacturer is the responsible party, so a manufacturer
self-assessment is the expected record), or **a Play Console form completed at submission**
(C3–C8). Those are tracked in the readiness document's §1b/§1c and are not restated as blockers here.

Status vocabulary is strict:

- **DONE** includes dated evidence.
- **OPEN — Owner** names the decision or evidence the owner must obtain.
- **DRAFTED — Owner signature required** means the written record exists but is unsigned. It is
  **not** a completed row, and it blocks *production publication* as OPEN does; it exists so the
  owner can see which rows need a signature rather than new work.
- **NOT REACHED** means the row is conditional on a row above that has not concluded.
- There is no “in progress,” implied completion, or disclaimer-based bypass. A drafted record
  confers no permission to publish publicly.

## A. Qualification decision — all rows block release

A manufacturer's assessment has been **drafted** and is held **locally, outside version control**,
at `docs/regulatory-qualification-assessment.md` (exported to
`docs/regulatory-qualification-assessment.pdf` for signature). It is deliberately not published: it
records the manufacturer's own health information to explain two design decisions, and §7.1 of that
same document forbids that fact appearing in published material. Its conclusion is **not a medical
device**.
Rows below record what that document does and does not settle. A drafted assessment is not a signed
one, and a self-assessment is not an independent one.

| ID | Status | Required record | Evidence / date |
|---|---|---|---|
| A1 | **DRAFTED — Owner signature required** | Define the exact intended purpose, intended users, use environment, markets, and public claims | Assessment §1 (intended purpose), §7.1 (marketing constraints), Annex A.1 (EU only). Becomes DONE when §9 is signed and dated |
| A2 | **OPTIONAL — Owner discretion** | Obtain a competent qualification assessment against Regulation (EU) 2017/745 Article 2 and the actual intended purpose | Assessment §3 is a **manufacturer self-assessment**. Under the MDR the manufacturer is the party responsible for qualification, so this is the legitimate and expected record; independent review is the assessment's own conservative recommendation (Annex A.6), not a legal precondition for a product concluded not to be a device. Closes when either an independent reviewer records an opinion **or** the owner records a reasoned decision to proceed on the self-assessment, with competence basis stated — see note below |
| A3 | **DRAFTED — Owner signature required** | Assess current software guidance: MDCG 2019-11 rev.1 (June 2025) | Assessment §4 analyses the current revision, confirmed live 2026-08-14. Annex A.2 requires reconfirming the revision at signature |
| A4 | **NOT REACHED** | If qualification applies, determine classification using current guidance, including MDCG 2021-24 rev.1 (April 2026), and determine conformity route | Conditional on A2. The drafted assessment concludes qualification does not apply, so no class, rule or conformity route is determined. If A2 overturns that conclusion, this row reopens as OPEN — Owner |
| A5 | **NOT BLOCKING for v1 — scoped out** | Assess every distribution market separately; EU work does not answer UK/US/other markets | Assessment covers the **EU only** (Annex A.1). **v1 distribution is set to EU countries only in Play Console**, which makes the signed assessment coextensive with the markets served and removes UK (MHRA) / US (FDA) assessment from this release. This row reopens as **OPEN — Owner** the moment distribution is widened beyond the EU |
| A6 | **OPEN — Owner** | Record the final conclusion and approve matching app/listing wording | Assessment §8 states a conclusion but is unsigned; listing wording review against §7.1 is Annex A.5 and is not done. See B |

Primary sources retrieved 2026-08-14:

- [Regulation (EU) 2017/745](https://eur-lex.europa.eu/eli/reg/2017/745/oj?locale=en)
- [MDCG 2019-11 rev.1 — software qualification and classification](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en)
- [MDCG 2021-24 rev.1 — classification of medical devices](https://health.ec.europa.eu/latest-updates/update-mdcg-2021-24-rev1-guidance-classification-medical-devices-april-2026-2026-04-20_en)
- [MDCG 2025-4 — medical-device software apps on online platforms](https://health.ec.europa.eu/latest-updates/mdcg-2025-4-guidance-safe-making-available-medical-device-software-mdsw-apps-online-platforms-june-2025-06-16_en)

A disclaimer cannot complete A1–A6. The software’s limited feature set also cannot decide them by
itself.

**Why A2 is optional rather than blocking.** The drafted assessment is the manufacturer's own. Under
the MDR the manufacturer is the party responsible for qualification, so a self-assessment is a
legitimate and expected record — it is simply not evidence of assessor *competence*, which is the
extra assurance A2 describes. Nothing in the MDR requires a non-device to obtain an independent
opinion before distribution, so holding the release for one is the owner's choice, not an external
requirement.

It remains the conservative course, and the reason is recorded rather than dismissed: the assessment
itself identifies adjacency to insulin dosing (§5.4) as the point on which a competent authority
could most plausibly disagree, and recommends independent review (Annex A.6). The owner may
reasonably obtain that review after launch, or record a reasoned decision to proceed on the
self-assessment with the competence basis stated. Either closes the row.

## B. Owner decision record — drafted, unsigned

Source: `docs/regulatory-qualification-assessment.md` (held locally, not in version control). This table
summarises; the assessment governs.

| Field | Owner record |
|---|---|
| Assessor name and role | *(OPEN — Owner)* — assessment §9 is unsigned; role stated as Manufacturer (self-assessment) |
| Assessment date/version | *(OPEN — Owner)* — document covers version 1.0.0 (version code 1); date field blank pending signature |
| Intended purpose | **Drafted** — general-purpose calculator for the carbohydrate content of a portion of packaged food; assessment §1 |
| Intended users/use environment | **Drafted** — anyone wanting a portion's carbohydrate content (cooking, sport, weight, low-carb); no condition-specific feature; assessment §1 |
| Markets | **European Union only.** UK and US unassessed; assessment Annex A.1 |
| Conclusion and legal basis | **Drafted** — not a medical device and not an accessory, under MDR Article 2(1) and MDCG 2019-11 rev.1; assessment §8. **Unsigned, and conditional on §7** |
| If applicable: class/rule/conformity route | Not reached — conclusion is that qualification does not apply |
| Approved public wording | *(OPEN — Owner)* — §7.1 sets binding constraints; the listing has not been reviewed against them (Annex A.5) |
| Owner signature/date | *(OPEN — Owner)* — **this is the gate; nothing above is in force until signed** |

## C. Google Play regulatory consequences

| ID | Status | Gate | Evidence / action |
|---|---|---|---|
| C1 | **DONE** | Current Health Content and Services policy fetched | Google primary source, 2026-08-14; linked in `play-release-readiness.md` |
| C2 | **DONE** | Current Health Apps declaration categories fetched | Google primary source, 2026-08-14; conditional branches in `play-health-declaration.md` |
| C3 | **OPEN — Owner decision** | Select the accurate Health Apps branch | **Play classification is a separate question from A1–A6** and is not derived from them — "not a medical device" does not mean "not a Google Play health app". The app calculates carbohydrate amounts for portions and meals, which may fall close to **Nutrition and Weight Management**. Facts on both sides: `play-release-readiness.md` §4a. Decided by the owner against Google's current form wording |
| C4 | **NOT REACHED** | Provide regulatory proof/fields if the regulated branch applies | Conditional on C3 selecting the regulated branch |
| C5 | **NOT REACHED** | Insert Google's current disclaimer if the non-regulated health-app branch applies | Conditional on C3 selecting a health category. If it does, use Google's exact sourced text; it does not influence A1–A6 |
| C6 | **CONDITIONAL on C3** | Confirm Organization developer account | Google requires an Organization account of developers providing **health apps**. Attaches only if C3 lands in a health category, and never as a consequence of §44. Record whether it is required now, required after a stated policy effective date, or not applicable |
| C7 | **DONE 2026-08-26** | Host privacy policy and add Play Console + in-app links | Live at `https://morfildor.github.io/Just-the-Carbs/privacy-policy.html` (GitHub Pages, public repo, `docs/` on `main`); `SettingsScreen` opens the same `BuildConfig.PRIVACY_POLICY_URL`, pinned by `SettingsScreenTest`. Entering the URL in Play Console remains part of submission |
| C8 | **OPEN — Owner (submission step)** | Submit/export the final Health Apps declaration | Retain submitted answers and date |

## D. Implemented scope and safety controls

These facts reduce misuse risk but do not answer qualification.

| ID | Status | Build fact | Evidence / date |
|---|---|---|---|
| D1 | **DONE** | One carbohydrate formula; countable portions resolve to g/ml before that formula | `PortionResolver`, `CarbCalculator`; code inspection 2026-08-14 |
| D2 | **DONE** | No insulin/dose/ratio/correction/glucose calculation or pump/CGM communication | Source search and app architecture; 2026-08-14 |
| D3 | **DONE** | OCR proposals require explicit user acceptance; ambiguity yields no value | `NutritionLabelParser`, label UI; code inspection 2026-08-14 |
| D4 | **DONE** | Sugars sub-lines are not substituted for total carbohydrate | Parser implementation/test inventory; 2026-08-14 |
| D5 | **DONE** | User-verified values are not silently replaced and source provenance remains separate | `Product.isRemoteRefreshable`, repository; code inspection 2026-08-14 |
| D6 | **DONE** | g and ml are never interconverted | `NutritionBasis`, `CarbCalculator`; code inspection 2026-08-14 |
| D7 | **DONE** | Invalid/negative/non-finite remote values are rejected | `NutritionValueValidator`; code inspection 2026-08-14 |
| D8 | **DONE** | Rounding is explicit and derived from the exact result, never double-rounded | `CarbResult`, `ResultFormatter`; code inspection 2026-08-14 |
| D9 | **DONE** | Camera may be denied; manual entry is a first-class path | Manifest camera optional; home/manual navigation; code inspection 2026-08-14 |
| D10 | **DONE** | Backup and cleartext traffic disabled; product images restricted to HTTPS allowlisted hosts | Manifest, backup rules, `ProductImageUrlValidator`; code inspection 2026-08-14 |

## E. Public-copy controls

| ID | Status | Gate | Evidence / action |
|---|---|---|---|
| E1 | **DONE** | Draft copy contains no efficacy, clinical validation, regulatory approval, or accuracy claim | `play-store-listing.md` claim audit, 2026-08-14 |
| E2 | **DONE** | Draft assets forbid device-manufacturer trademarks and medical/certification imagery | `store-assets.md`, 2026-08-14 |
| E3 | **DONE** | In-app safety text states the calculation boundary without a qualification claim | `settings_safety_body`, reviewed 2026-08-14 |
| E4 | **OPEN — Owner** | Final name, intended-purpose wording, listing, screenshots, and disclaimer branch approved together | Signed copy review after A1–A6 |
| E5 | **OPEN — Owner** | Open Food Facts data/image licence review | Record reviewer, conclusion, source versions, date; engineering attribution is not a legal conclusion |
| E6 | **OPEN — Owner** | Project licence disposition chosen | Record licence or all-rights-reserved decision |

## F. Final gate sign-off

This gate governs **public production publication only**. Internal and closed testing are not gated
by it.

Every outstanding row, classified strictly:

| Classification | Rows | Note |
|---|---|---|
| **LEGALLY REQUIRED BEFORE EU PRODUCTION** | **A1, A3, B** — sign and date the assessment (§9 of that document). **A6 / E4** — read the final listing against §7.1. | The assessment's conclusion is already written; A1/A3/B need the owner's signature, which only the owner can give. A6/E4 is a read-through of copy already claim-audited (E1). |
| **INTERNAL / OPTIONAL** | **A2** (independent review — the MDR makes the manufacturer the responsible party, so the self-assessment is the expected record); **E5** (OFF licence review); **E6** (project licence) | Worth doing; no external party requires any of them for this launch. |
| **NOT APPLICABLE TO EU-ONLY V1** | **A4** (conclusion is that qualification does not apply); **A5** (UK/US markets not in scope) | A5 reopens only if distribution is widened. |
| **CONDITIONAL — depends on the C3 outcome** | **C4, C5, C6** | Apply only if the Health Apps declaration lands in a health category. Not resolvable until C3 is decided. |
| **OWNER DECISION / PLAY SUBMISSION STEP** | **C3** (category choice — owner's), **C8** (submit and retain) | C3 is a genuine decision, not a form-filling step; see `play-release-readiness.md` §4a. |

| Field | Current value |
|---|---|
| Internal testing | **DEPLOYED 2026-08-26** — not gated by this document |
| Closed testing | **ACTIVE** — same artifact, 12+ testers, period running; not gated by this document |
| Production publication | **GATED** — by the LEGALLY REQUIRED row above and the Play forms in `play-release-readiness.md` §5a |
| Owner approval | *(OPEN — Owner)* |
| Date | *(OPEN — Owner)* |
