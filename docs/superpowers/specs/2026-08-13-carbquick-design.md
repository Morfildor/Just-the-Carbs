# CarbQuick — Design Specification

Date: 2026-08-13
Status: Approved (design decisions confirmed by owner)

## 1. Purpose

Calculate the carbohydrate grams in the portion of packaged food a user is about to eat.

```
SCAN → PORTION → CARBS
RECENT → PORTION → CARBS
```

The app does **not** calculate insulin, interpret glucose, or communicate with pumps or CGMs. Every
feature must make one of the two workflows above faster, safer, or clearer.

## 2. Verified platform facts

Verified against official Android documentation on **2026-08-13**. These are the versions the
project is *pinned to*, not a claim that they remain the latest available - re-verify before release
(correction #7).

**`compileSdk` may be higher than `targetSdk`, and currently is.** `targetSdk = 36` is what the Play
requirement addresses; `compileSdk = 37` is what current AndroidX requires in order to compile at
all. They are independent settings and the difference is deliberate.

Re-verify before each release: target SDK requirement, compileSdk compatibility, AGP, Gradle, JDK,
Compose and ML Kit.

| Fact | Value | Source |
|---|---|---|
| Play target API requirement | **API 36** for new apps/updates, deadline **2026-08-31** | developer.android.com/google/play/requirements/target-sdk |
| Project-pinned AGP | **9.3.1** | developer.android.com/build/releases/gradle-plugin |
| Project-pinned Gradle | **9.7.0** | same |
| Project-pinned JDK | **21 LTS** (AGP minimum 17) | same |
| Project-pinned compileSdk | **37** - required by current AndroidX; may exceed targetSdk | AndroidX AAR metadata |
| OFF read rate limit | **15 req/min/IP** | support.openfoodfacts.org |
| OFF User-Agent | **Mandatory**, must identify the app | same |
| OFF data licence | **ODbL** — attribution *and* share-alike | same |

The **2026-08-31 deadline is ~18 days out**. `targetSdk = 36` is non-negotiable for this release.

The **15 req/min limit shapes the design**, but does not make cache-first a correctness
requirement: a calculation is not invalid merely because the value arrived over the network.
Cache-first is required for **performance, offline capability, resilience, user experience and
responsible API usage** (correction #8). Current Open Food Facts request limits and the mandatory
identifying User-Agent are respected regardless.

## 3. Confirmed design decisions

These four were escalated to the owner because they change the product, not just the code.

### 3.1 Millilitre / gram basis — *explicit unit, no conversion*

A per-100-ml product's portion field is locked to **ml**. The app never assumes `1 ml = 1 g`.
If the user needs the other unit, the app says plainly that the units are not interchangeable and
offers manual entry of a per-100-g value. This never invents a density and never dead-ends.

### 3.2 Rounding — *decimal dominant* (revised, correction #6)

Default hierarchy: large **`31.3 g`**, supporting `≈ 31 g whole grams`.

The brief (§18) originally specified the opposite emphasis. It was revisited because the result
exists to be transcribed into another calculator, and leading with the rounded figure discards
precision at the one moment precision matters. **No downstream whole-gram-only constraint has been
confirmed**; if one is, `ResultStyle.WHOLE_DOMINANT` restores the original hierarchy and the
constraint should be recorded here.

The arithmetic is unchanged and non-negotiable: calculate exactly, derive the decimal display from
the exact result, derive the whole gram from the exact result, **never round twice**. Regression case
retained - `51.5 × 30 / 100 = 15.45` displays `15.5` and rounds to `15`; neither is derived from
the other.

> Noted for the owner: the dominant number being the *rounded* one gives up precision at the moment
> precision matters. This was raised, and the owner confirmed the brief's specified emphasis. It is
> a deliberate decision, not an oversight, and is easy to invert later (single value in the
> result composable).

### 3.3 Conservative safety controls pending regulatory qualification

MDR qualification is **unresolved**. This document therefore makes no statement about whether
CarbScan is, or is not, a medical device or an accessory to one - that determination belongs to the
assessment recorded in `docs/regulatory-release-checklist.md`, not to an architecture document
(correction #4).

While it remains unresolved, the safety-critical paths are deliberately conservative:

- OCR output is **never** auto-accepted.
- User-verified data is **never** silently overwritten by remote data.
- When confidence is insufficient, the app shows **no value** rather than a guess.
- Publication remains **blocked** until the qualification assessment is completed and recorded.

### 3.4 Android backup — *disabled for product data*

`android:allowBackup="false"`, **plus** explicit `backup_rules.xml` and `data_extraction_rules.xml`
covering **both** cloud backup and device-to-device transfer (correction #2). The attribute alone is
not relied on. Excluded domains: `database` (Room), `sharedpref`, and `file` - which is where
DataStore lives. That covers local product history, recents, favourites, user-verified values and
cached portion data.

Rationale: local usage history (which foods, when) never reaches the user's Google account.

Cost: saved products do not survive a device migration. Documented in known-limitations.

**No licensing conclusion is drawn from this.** Open Food Facts data and images remain subject to
their applicable current licences regardless of backup configuration, and this document does not
make a legal determination (correction #3). Attribution and licence review are release-checklist
items. Note specifically that **product images may carry licensing requirements distinct from the
structured database**, and images are now displayed in the app - see `docs/third-party-notices.md`.

## 4. Architecture

Deliberately flat. A small app does not need a clean-architecture framework (brief §65).

```
ui/          Compose screens + ViewModels (immutable state, StateFlow)
domain/      CarbCalculator, NutritionBasis, validation — pure Kotlin, zero Android deps
data/
  local/     Room: ProductEntity, ProductDao, migrations
  remote/    OpenFoodFactsDataSource (Retrofit), DTOs, validation
  ProductRepository   ← owns the lookup priority
ocr/         ML Kit text recognition + NutritionLabelParser (pure, testable)
scan/        CameraX + ML Kit barcode
```

**`domain/` is pure Kotlin with no Android dependencies.** This is the single most important
boundary: the calculation engine is the safety-critical part, and keeping it Android-free means it
is exhaustively unit-testable on the JVM with no emulator.

### Lookup priority (owned by `ProductRepository`)

1. **User-verified local** — used immediately, never overwritten.
2. **Cached remote local** — shown immediately; background refresh may not block calculation, and
   may not alter an open calculation session (see *Calculation-session immutability*).
3. **Open Food Facts** — network.
4. **Fallback** — scan label / manual entry. The user never hits a dead end.

### Provenance and verification are separate concepts (correction #9)

They must never be collapsed into one field. A product can be sourced from Open Food Facts *and*
later verified by the user; both facts have to survive.

| Field | Values |
|---|---|
| `dataSource` | `OPEN_FOOD_FACTS`, `MANUAL`, `OCR`, future providers. Permanent |
| `verificationStatus` | `UNVERIFIED`, `USER_VERIFIED`. Changes freely |

Retained alongside: `verifiedAt`, `originalRemoteCarbs` (the value the user overrode),
`latestRemoteCarbs` (the newest figure the provider reports), `remoteUpdatedAt`, and `barcode`.

**User verification must never destroy source provenance.** Regression-tested.

Architecturally, remote-source data is kept distinct from user-created and user-verified overrides
rather than mutating the source record in a way that obscures where a number came from.

### Calculation-session immutability (correction #5)

> **A calculator session uses an immutable product-data snapshot.**

Once the calculator is open and the user is entering a portion, the nutritional value backing that
session does not change because of a background refresh. The failure this forbids:

1. screen opens on cached 48.2 g / 100 g
2. user types 65 g
3. a background refresh returns 51.0 g / 100 g
4. the result changes silently while the user is reading it

Instead, when remote data differs during an open session the app preserves the session, records the
newer remote value locally, surfaces an unobtrusive *Online value changed* notice, and lets the user
apply it explicitly - or picks it up the next time the product is opened. Applying is reversible and
returns the record to unverified, because the user has not checked *that* number against a package.

This applies especially to user-facing carbohydrate values. Regression-tested.

### Reformulation handling (correction #10)

Products get reformulated while keeping the same barcode. The model stores enough to tell three
things apart - the original remote value, the user's verified local value, and the latest remote
value - so a meaningful difference can be surfaced (*"Online value changed since you verified this
product"*) without ever overwriting automatically, and without blocking normal calculation.

### Calculation engine

`carbs = carbsPer100 × portion / 100`, evaluated with `BigDecimal` to avoid binary
floating-point display drift. Internal value is never rounded before the final display step.

Anchor cases (from brief §17):

| carbsPer100 | portion | exact | displayed | rounded |
|---|---|---|---|---|
| 48.2 | 65 | 31.33 | 31.3 g | 31 g |
| 52 | 30 | 15.6 | 15.6 g | 16 g |
| 4.8 | 250 | 12 | 12.0 g | 12 g |

## 5. Screens

- **Home** — title, large *Scan barcode*, Recent list (favourites float to top), *Enter manually*.
  One primary surface. No bottom navigation.
- **Scanner** — full preview, subtle frame, torch, close. Continuous detection, debounced, haptic
  on success.
- **Product / Calculator** — *the* screen. Carbs-per-100 with source status, large portion field,
  −10/−5/+5/+10, persistent dominant result kept visible above the keyboard.
- **Verify** — confirm name, carbs per 100, basis; then `✓ Verified by you`.
- **Manual entry** / **Quick calculator** / **Settings + About**.

## 6. Data integrity rules

Remote values are validated before use. Rejected: negative, NaN, infinity, malformed, missing
basis, uninterpretable units. A rejected value produces *"Carbohydrate value unavailable"* plus
*Scan label* / *Enter manually* — never a fabricated number.

Total carbohydrate only. Never substituted with sugars, fibre, net carbs, energy, or protein.

## 7. Testing

- **Unit (JVM)**: calculator (normal/decimal/zero/large/rounding/locale/negative/malformed),
  repository priority (verified wins, cache offline, remote never overwrites verified),
  history (sorting, favourite, last portion), OCR parsing (NL `Koolhydraten`, EN `Carbohydrate`,
  100 g / 100 ml, ambiguity, failure).
- **UI**: the six workflows in brief §60, as far as the environment permits.

## 8. Privacy

**No first-party analytics, advertising, behavioural tracking, or telemetry is implemented by
CarbScan.** Third-party SDKs - specifically Google ML Kit - may collect limited diagnostic,
configuration, performance, error or usage information according to their own current disclosures.
This must be reflected accurately in the privacy policy and the Google Play Data Safety declaration
(correction #1).

Verified against the shipped dependency versions on 2026-08-14: ML Kit brings
`com.google.android.datatransport` (Google's CCT transport) transitively via
`com.google.mlkit:common`, and it **cannot be excluded** - removing it fatally crashes the scanner.
Evidence and method are recorded in `docs/google-play-data-safety.md`. Review current official ML Kit
Android data-disclosure documentation before finalising the declaration.

Permissions: `CAMERA` and `INTERNET`, plus `ACCESS_NETWORK_STATE` merged in transitively by that
component. OCR is on-device; images are not persisted or uploaded. The only network egress
CarbScan's *own* code performs is a barcode lookup to Open Food Facts - the privacy policy says so
explicitly and must **not** claim "no data leaves the device".

## 8b. Release gate contents (correction #11)

`docs/regulatory-release-checklist.md` gates publication on all of:

- Google Play Health Apps declaration
- Data Safety form, matching the shipped implementation
- publicly reachable privacy policy URL
- confirmation of the required developer account type
- final medical-device qualification decision
- final store wording review
- third-party SDK disclosures, including the ML Kit transport described above
- Open Food Facts database **and image** attribution and licensing

Nothing is auto-submitted and no declaration answer is fabricated.

## 9. Known constraints

- Public food data can be wrong or stale; barcodes are reused across reformulations.
- OCR can misread labels; it is always confirmed by the user.
- First lookup of an unknown barcode requires network.
- Regulatory status is **unresolved** and gates publication.
