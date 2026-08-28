# Play Health Apps declaration — owner draft

**Status: OWNER DECISION REQUIRED. Nothing has been submitted. Source check: 2026-08-14.**

**This is a Google Play platform classification, not a medical-device question.** It is decided
against Google's own Health Apps categories and is **not** derived from the §44 MDR assessment —
neither answer follows from the other. "Not a medical device" and "not a Google Play health app" are
separate conclusions reached by separate authorities.

The app calculates carbohydrate amounts for food portions and for a meal total, which may fall close
to Google's **Nutrition and Weight Management** category. The facts on both sides are in
[play-release-readiness.md](play-release-readiness.md) §4a; this document holds the supporting
capability facts and the branch logic. **The category choice is the owner's.**

Google requires every published app to complete the Health Apps declaration, even an app that
offers no health feature. The relevant current categories and follow-up fields are documented in
[Provide information for the Health Apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en)
and the requirements are in [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

## Facts that do not depend on §44

Just the Carbs multiplies a product's carbohydrate value per 100 g or 100 ml by a user-entered portion
and divides by 100. A count is first resolved to a gram/ml portion and then uses the same formula.

| Capability | Build fact |
|---|---|
| Insulin/medication dose, I:C ratio, correction, IOB | Absent |
| Glucose entry, storage, interpretation | Absent |
| Pump/CGM communication | Absent |
| Diagnosis, symptom assessment, treatment recommendation | Absent |
| Health Connect or health permission | Absent |
| Account, cloud sync, ads | Absent |
| Camera use | Barcode and nutrition-label recognition; images processed on-device and discarded |
| Network use | Open Food Facts product/search/image requests; ML Kit diagnostics/usage collection described in the Data Safety draft |
| Local storage | Products, portions, verification state, usual portions, one current-meal scratchpad, settings |

The intended-use context immediately before a separate bolus calculator is part of the owner’s
assessment and must not be hidden or minimized.

## Form answer decision tree

**Health Apps declaration: OWNER DECISION REQUIRED.** This document does not select the category.

The app calculates carbohydrate amounts for food portions and for a meal total, which may fall close
to Google's **Nutrition and Weight Management** category. The facts on both sides are set out in
`play-release-readiness.md` §4a; the choice is the owner's. Answer against Google's **current** form
wording at submission — the form text governs over any summary here.

**Do not under-declare to avoid account requirements, and do not over-classify functionality that is
not present.** Both are real failure modes.

| Play conclusion | Declaration answer | Follow-up |
|---|---|---|
| **Nutrition and Weight Management** — the closest category, and the owner's decision | Select it if the owner concludes Play treats carbohydrate calculation for portions and meals as dietary-intake functionality | Google's category describes dietary-intake tracking, meal planning, diet/weight management or dietary goals. Against that: this app keeps no diary, no daily totals, no longitudinal history, no goals and no weight, and holds one unnamed current meal with no history. Record the reasoning either way. If selected, the health-app requirements below apply. |
| No health feature under Play's categories | Select **My app doesn't provide any health features** | Only on a recorded analysis. Absence of Health Connect data alone is **not** a sufficient basis; §4a's category walk is. No disclaimer and no account change follow. |
| Play classifies the intended purpose as condition management | Assess/select **Diseases and Conditions Management** | Record why that category matches. Apply the non-regulated health-app requirements below, including Google's exact disclaimer wording; the Organization-account requirement then attaches. |
| Regulated in a market where offered | Select **Medical Device Apps** | Only if a regulatory conclusion says so. Complete every requested legal, compliance, operating, manufacturer, intended-purpose, warning, eIFU, UDI and certificate field that applies. Do not invent or leave required fields unsupported. |

Do not select **Clinical Decision Support** based on adjacency to another calculator. Google's
examples include professional decision support and drug dosage calculators; Just the Carbs contains
neither. If a qualified assessor reaches a different classification, record their reasoning rather
than altering the code description.

## Policy-dependent listing and in-app work

Google’s current policy says:

- regulated apps must be declared and provide regulatory proof on request;
- other health/medical apps must include this clear description in the app description:
  **“not a medical device and does not diagnose, treat, cure, or prevent any medical condition.”**
- a health app must link its privacy policy in Play Console and provide a privacy-policy link or
  text in the app; the URL must be active, public, non-geofenced, non-editable, and not a PDF.

This section does not choose which branch applies. If the signed §44/Play assessment selects the
non-regulated health-app branch, insert Google's wording exactly in the full description and any
in-app location the then-current policy requires. If it selects the regulated branch, do not use
the non-regulated disclaimer as a substitute for regulatory evidence.

## Organization account dependency

[Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en)
states that developers providing health apps, such as medical apps, must register as an
Organization.

**This is conditional on the declaration's outcome, not on §44.** If the declaration lands in a
health category, the Organization requirement attaches at that point and is a genuine external
blocker; if it does not, no account change is required. Resolve the category first, then record the
account requirement it implies — and distinguish **required now** from **required only after a
stated policy effective date** from **not applicable**, noting the date the policy was read.

Record the final category and account result:

| Field | Owner record |
|---|---|
| §44 assessor / date | *(owner)* |
| Markets assessed | *(owner)* |
| Regulatory conclusion | *(owner)* |
| Play category selected | *(owner)* |
| Developer account type and verification | *(owner)* |
| Required proof/disclaimer branch | *(owner)* |
| Privacy-policy URL verified in Console and app | *(owner)* |
| Declaration submission/export retained at | *(owner)* |
