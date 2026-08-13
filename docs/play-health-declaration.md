# Play health declaration — preparation

**Draft for owner completion. Nothing has been submitted (§46).**

> ⚠️ **This document does not quote the Google Play Health Content and Services policy.** That
> policy changes, and reproducing remembered wording would create a false impression of compliance.
> The owner must read the policy as it currently stands and complete the answers below against it.

---

## Blocking dependency

**Every answer here depends on the §44 regulatory assessment**, which is unresolved. See
[regulatory-release-checklist.md](regulatory-release-checklist.md).

> **Do NOT declare CarbScan non-medical while that assessment is incomplete (§46).** A declaration
> is a factual statement to Google, not a preference.

## Facts about the app, for whoever completes the form

These are verified against the code and can be relied on.

### What it does

- Multiplies a declared carbohydrate value by a user-entered portion and divides by 100.
- Displays the result as whole grams with a decimal beneath.
- Stores products, portions, favourites and user-verified values on the device.

### What it does not do

| Capability | Present? |
|---|---|
| Insulin dose calculation | **No** |
| Insulin-to-carb ratio, correction factor, IOB | **No** |
| Glucose entry, storage, or interpretation | **No** |
| Pump or CGM communication | **No** |
| Treatment or medication recommendation | **No** |
| Diagnosis, triage, or symptom assessment | **No** |
| Health Connect integration | **No** |
| Telehealth, prescriptions, pharmacy | **No** |
| User health-data collection or transmission | **No** |

There is no dose, ratio, correction, glucose, or medication concept anywhere in the codebase.

### Intended-purpose context — the part that matters

CarbScan is positioned for use **immediately before** a separate bolus calculator. That adjacency is
the whole reason §44 exists and must be disclosed accurately in any assessment. It should not be
minimised on the form to obtain a simpler category.

## Policy categories to assess

Read each against the current policy text and record the outcome.

| # | Question | Answer | Basis |
|---|---|---|---|
| 1 | Does the app fall under Health Content and Services at all? | ☐ | |
| 2 | Is it a "health app" in the policy's terms? | ☐ | |
| 3 | Does it require the non-medical-device disclaimer? | ☐ | |
| 4 | Does it require a professional-advice statement? | ☐ | |
| 5 | Any medical-device declaration required in Play Console? | ☐ | |
| 6 | Any additional documentation or attestation required? | ☐ | |
| 7 | Is an Organization Play Console account required (§47)? | ☐ | |

## Permissions and data — for the form

| Item | Answer |
|---|---|
| Permissions requested by CarbScan | `CAMERA`, `INTERNET` |
| Permission merged transitively | `ACCESS_NETWORK_STATE` (via ML Kit's `datatransport`) |
| Sensitive permissions | None |
| Health data accessed | None |
| Health data transmitted | None |
| Data transmitted off-device | A scanned barcode, to Open Food Facts, only when uncached |
| User identifiers transmitted | None — no account or device identifier exists |
| Analytics / advertising SDK added by us | None |
| Third-party telemetry present | ML Kit's `com.google.android.datatransport` — see [google-play-data-safety.md](google-play-data-safety.md) |
| Backup | Disabled |

## Regulatory status field — owner must confirm

| Field | Value |
|---|---|
| Regulatory status | ☐ Not a medical device ☐ Medical device ☐ Accessory to a medical device ☐ **Not yet assessed** |
| Assessed by | *(owner)* |
| Date | *(owner)* |
| Evidence retained at | *(owner)* |

**Current value: Not yet assessed.**

## Required wording (§50)

If the assessment concludes a disclaimer is required, insert **Google's exact required language**,
retrieved from the current policy, into:

1. The store listing full description
2. Any in-app location the policy specifies

Do not paraphrase it. Do not write your own version.

☐ Wording retrieved from current policy and inserted — *(owner completes, with source URL and date)*

## Submission rules observed

- Nothing is submitted automatically by this project.
- No declaration is pre-filled on the owner's behalf.
- No claim of medical approval or certification appears anywhere in the app or listing.
