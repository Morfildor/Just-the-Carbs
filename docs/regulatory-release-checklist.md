# Regulatory release checklist — CarbScan

**Status: UNRESOLVED. This document blocks production publication (brief §44).**

Nothing in this file is a regulatory determination. It is a list of questions the owner must
answer, with evidence, before CarbScan is published to any public store.

---

## 1. The gate

CarbScan calculates the carbohydrate content of a portion of packaged food. It does **not**
calculate insulin, interpret glucose, or communicate with a pump or CGM.

That does not by itself settle its regulatory status. Under EU MDR 2017/745, qualification turns on
the **intended purpose** the manufacturer assigns — including marketing claims and the context of
use — not solely on what the code computes. CarbScan's intended purpose positions it for use
immediately *before* a separate bolus calculator, and that adjacency is precisely what a
qualification assessment has to examine.

> **A disclaimer must not be used to avoid this assessment (§44).** If the assessment concludes
> that CarbScan qualifies as a medical device or as an accessory to one, it must stop being treated
> as a wellness app and the additional compliance work must be identified and completed.

**Owner action required. This cannot be delegated to the development process.**

## 2. What the owner must perform and document

| # | Action | Done | Evidence / date |
|---|---|---|---|
| 2.1 | Write the formal **intended purpose** statement, in the words that will appear in the store listing and in-app | ☐ | |
| 2.2 | Assess qualification under **MDR 2017/745 Article 2(1)** against that intended purpose | ☐ | |
| 2.3 | Assess against **current MDCG software qualification and classification guidance** (MDCG 2019-11 and any successor — confirm the current version before relying on it) | ☐ | |
| 2.4 | If qualified as a device: determine **class**, conformity assessment route, and whether a notified body is required | ☐ | |
| 2.5 | Assess the **Google Play Health Content and Services** policy as it currently reads | ☐ | |
| 2.6 | Determine whether markets outside the EU are in scope, and assess each separately (UK MHRA, US FDA, etc.) | ☐ | |
| 2.7 | Record the conclusion, the reasoning, and the date, and retain it | ☐ | |

## 3. Decision record

| Field | Value |
|---|---|
| Assessment performed by | *(name — owner completes)* |
| Date | *(owner completes)* |
| Intended purpose statement | *(owner completes)* |
| Conclusion | ☐ Not a medical device ☐ Medical device ☐ Accessory to a medical device |
| Basis for conclusion | *(owner completes)* |
| If a device: class and route | *(owner completes)* |

**Until this table is completed and signed, CarbScan must not be published.**

## 4. What the build already does to reduce risk

The owner chose to build to the **stricter** standard while the assessment is outstanding — as if
CarbScan were an accessory to a medical device. That decision is implemented, not merely asserted:

| Safeguard | Where it lives | Enforced by |
|---|---|---|
| OCR output is never auto-accepted | `LabelScannerScreen` — values are proposals with *Use* / *Edit* | `NutritionLabelParserTest` |
| Ambiguous label readings are never resolved by the app | `NutritionLabelParser` returns `Ambiguous` | `reports ambiguity when two different carbohydrate values appear` |
| The sugars sub-line is never read as total carbohydrate | `NutritionLabelParser` excludes `waarvan suikers` / `of which sugars` | `never mistakes the sugars sub-line for the total` |
| Verified data is never silently overwritten | `Product.isRemoteRefreshable` | 7 provenance regression tests |
| No value is shown when confidence is insufficient | `NutritionValueValidator` returns null; UI shows *Carbohydrate value unavailable* | `NutritionValueValidatorTest` |
| Never assumes 1 ml = 1 g | `NutritionBasis` is a label, never a factor | `the basis never applies a density conversion` |
| No double rounding of the displayed gram | `CarbResult` derives both values from `exact` | `the whole gram is rounded from the exact value...` |
| No insulin calculation anywhere | — | Absence of any dose, ratio, correction or glucose concept in the codebase |

These reduce risk. **They do not substitute for the assessment.**

## 5. Explicit prohibitions observed

- No CE mark is applied anywhere in the app, the icon, the store listing, or this repository.
- No claim of medical approval, clinical validation, or regulatory clearance is made.
- No clinical efficacy claim is made (§45): the copy states only what the software computes.
- No affiliation with CamDiab, Ypsomed, CamAPS FX, Abbott or Libre is stated or implied (§51).

## 6. Play Console account (§47)

The owner must verify whether an **Organization** Play Console account is required for the final
health-app category. Do not assume a personal developer account is sufficient. Confirm against the
current Play Console requirements at the time of submission.

☐ Verified account type requirement — *(owner completes, with date)*

## 7. Open finding requiring a decision

**ML Kit ships a Google telemetry transport.** `com.google.android.datatransport` arrives
transitively with ML Kit and merges `ACCESS_NETWORK_STATE` into the manifest. §34 states the app
uses no telemetry SDK; that is true of code written for CarbScan, but not of this transitive
dependency, whose behaviour the app does not control.

No opt-out constant was found in the shipped ML Kit artifacts, so **none has been invented**. The
owner must decide between: accepting and disclosing it, confirming a supported opt-out against
current official ML Kit documentation, or replacing the dependency. It must be disclosed accurately
in the Data Safety form either way (§49).

☐ Decision recorded — *(owner completes)*
