# Play Health Apps declaration — owner draft

**Status: BLOCKED by §44. Nothing has been submitted. Source check: 2026-08-14.**

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

Current form action: **open the declaration and save it as a draft. Do not submit a category until
the signed §44 assessment supplies the branch.**

| §44 / Play conclusion | Draft declaration answer | Follow-up |
|---|---|---|
| Regulated in a market where offered | Select **Medical Device Apps** | Complete every requested legal, compliance, operating, manufacturer, intended-purpose, warning, eIFU, UDI and certificate field that applies. Do not invent or leave required fields unsupported. |
| Not regulated, but Play classifies the intended purpose as condition management | Assess/select **Diseases and Conditions Management** | Record why that category matches. Apply the non-regulated health-app policy requirements below. |
| Not regulated and Play classifies it as no health feature | Select **My app doesn’t provide any health features** | Retain the written analysis supporting that answer. Absence of Health Connect data alone is not a sufficient basis. |

Do not select **Nutrition and Weight Management** merely because the calculation uses carbohydrate
facts. Google's category covers dietary-intake tracking, meal planning, diet/weight management, or
specific dietary goals. Just the Carbs performs none of those and has no diary or daily totals.

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
Organization. Record the final category and account result:

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
