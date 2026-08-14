# Regulatory release gate — Just the Carbs

**NO-GO. Every OPEN row blocks public publication.** This document records engineering evidence
and owner actions; it is not a regulatory determination. Last engineering review: 2026-08-14.

Status vocabulary is strict:

- **DONE** includes dated evidence.
- **OPEN — Owner** names the decision or evidence the owner must obtain.
- **DRAFTED — Owner signature required** means the written record exists but is unsigned. It is
  **not** a completed row and blocks release exactly as OPEN does; it exists only so the owner can
  see which rows need a signature rather than new work.
- **NOT REACHED** means the row is conditional on a row above that has not concluded.
- There is no “in progress,” implied completion, or disclaimer-based bypass. A drafted record
  confers no permission to publish.

## A. Qualification decision — all rows block release

A manufacturer's assessment has been **drafted** and is held at
[regulatory-qualification-assessment.md](regulatory-qualification-assessment.md) (exported to
`regulatory-qualification-assessment.pdf` for signature). Its conclusion is **not a medical device**.
Rows below record what that document does and does not settle. A drafted assessment is not a signed
one, and a self-assessment is not an independent one.

| ID | Status | Required record | Evidence / date |
|---|---|---|---|
| A1 | **DRAFTED — Owner signature required** | Define the exact intended purpose, intended users, use environment, markets, and public claims | Assessment §1 (intended purpose), §7.1 (marketing constraints), Annex A.1 (EU only). Becomes DONE when §9 is signed and dated |
| A2 | **OPEN — Owner** | Obtain a competent qualification assessment against Regulation (EU) 2017/745 Article 2 and the actual intended purpose | Assessment §3 is a **manufacturer self-assessment**; no assessor competence is recorded. Independent review is Annex A.6 and remains outstanding — see note below |
| A3 | **DRAFTED — Owner signature required** | Assess current software guidance: MDCG 2019-11 rev.1 (June 2025) | Assessment §4 analyses the current revision, confirmed live 2026-08-14. Annex A.2 requires reconfirming the revision at signature |
| A4 | **NOT REACHED** | If qualification applies, determine classification using current guidance, including MDCG 2021-24 rev.1 (April 2026), and determine conformity route | Conditional on A2. The drafted assessment concludes qualification does not apply, so no class, rule or conformity route is determined. If A2 overturns that conclusion, this row reopens as OPEN — Owner |
| A5 | **OPEN — Owner** | Assess every distribution market separately; EU work does not answer UK/US/other markets | Assessment covers the **EU only** (Annex A.1). UK (MHRA) and US (FDA) are unassessed. Distribution must be restricted to the EU until this is done |
| A6 | **OPEN — Owner** | Record the final conclusion and approve matching app/listing wording | Assessment §8 states a conclusion but is unsigned; listing wording review against §7.1 is Annex A.5 and is not done. See B |

Primary sources retrieved 2026-08-14:

- [Regulation (EU) 2017/745](https://eur-lex.europa.eu/eli/reg/2017/745/oj?locale=en)
- [MDCG 2019-11 rev.1 — software qualification and classification](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en)
- [MDCG 2021-24 rev.1 — classification of medical devices](https://health.ec.europa.eu/latest-updates/update-mdcg-2021-24-rev1-guidance-classification-medical-devices-april-2026-2026-04-20_en)
- [MDCG 2025-4 — medical-device software apps on online platforms](https://health.ec.europa.eu/latest-updates/mdcg-2025-4-guidance-safe-making-available-medical-device-software-mdsw-apps-online-platforms-june-2025-06-16_en)

A disclaimer cannot complete A1–A6. The software’s limited feature set also cannot decide them by
itself.

**Why A2 remains OPEN even though an assessment exists.** The drafted assessment is the
manufacturer's own. Under the MDR the manufacturer is the party responsible for qualification, so a
self-assessment is a legitimate and expected record — but it is not evidence of assessor competence,
which is what A2 asks for. The assessment itself identifies its adjacency to insulin dosing (§5.4)
as the point on which a competent authority could most plausibly disagree, and recommends
independent review (Annex A.6). Given the app's proximity to the owner's own insulin dosing, that
review is the conservative course. A2 closes when either an independent reviewer records an opinion,
or the owner records a reasoned decision to proceed on the self-assessment alone, with competence
basis stated.

## B. Owner decision record — drafted, unsigned

Source: [regulatory-qualification-assessment.md](regulatory-qualification-assessment.md). This table
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
| C3 | **OPEN — Owner** | Select the accurate Health Apps branch | Depends on A1–A6; save form as draft meanwhile |
| C4 | **OPEN — Owner** | Provide regulatory proof/fields if the regulated branch applies | Use only documents from the completed legal route |
| C5 | **OPEN — Owner** | Insert Google's current disclaimer if the non-regulated health-app branch applies | Use exact sourced text; do not use it to influence A1–A6 |
| C6 | **OPEN — Owner** | Confirm Organization developer account | Google says health-app providers must register as an Organization; final category/account evidence required |
| C7 | **OPEN — Owner** | Host privacy policy and add Play Console + in-app links | Public URL and release-build evidence required |
| C8 | **OPEN — Owner** | Submit/export the final Health Apps declaration | Retain submitted answers and date |

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

The owner may change the decision below to GO only when every OPEN row above has evidence.

| Field | Current value |
|---|---|
| Decision | **NO-GO** |
| Open blocking rows | A1 and A3 (drafted, awaiting signature); A2, A5, A6; B signature; C3–C8; E4–E6 |
| Owner approval | *(OPEN — Owner)* |
| Date | *(OPEN — Owner)* |
