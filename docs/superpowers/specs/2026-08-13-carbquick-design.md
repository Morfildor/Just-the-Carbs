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

Checked against official sources on 2026-08-13, not assumed from training data:

| Fact | Value | Source |
|---|---|---|
| Play target API requirement | **API 36** for new apps/updates, deadline **2026-08-31** | developer.android.com/google/play/requirements/target-sdk |
| Latest stable AGP | **9.3.0** (July 2026) | developer.android.com/build/releases/gradle-plugin |
| Min Gradle for AGP 9.3.0 | **9.5.0** | same |
| Min JDK for AGP 9.3.0 | **17** (using 21 LTS) | same |
| OFF read rate limit | **15 req/min/IP** | support.openfoodfacts.org |
| OFF User-Agent | **Mandatory**, must identify the app | same |
| OFF data licence | **ODbL** — attribution *and* share-alike | same |

The **2026-08-31 deadline is ~18 days out**. `targetSdk = 36` is non-negotiable for this release.

The **15 req/min limit is a design constraint, not a footnote**: it makes the cache-first lookup
order (§10 of the brief) a correctness requirement rather than an optimisation.

## 3. Confirmed design decisions

These four were escalated to the owner because they change the product, not just the code.

### 3.1 Millilitre / gram basis — *explicit unit, no conversion*

A per-100-ml product's portion field is locked to **ml**. The app never assumes `1 ml = 1 g`.
If the user needs the other unit, the app says plainly that the units are not interchangeable and
offers manual entry of a per-100-g value. This never invents a density and never dead-ends.

### 3.2 Rounding — *whole gram dominant*

Per brief §18: large `31 g`, supporting `31.3 g calculated`. The decimal is rendered **legibly**,
not as a whisper.

> Noted for the owner: the dominant number being the *rounded* one gives up precision at the moment
> precision matters. This was raised, and the owner confirmed the brief's specified emphasis. It is
> a deliberate decision, not an oversight, and is easy to invert later (single value in the
> result composable).

### 3.3 Regulatory posture — *build to the stricter standard*

Until the owner completes the MDR qualification assessment, safety-critical paths are built as if
the app were an **accessory to a medical device**:

- OCR output is **never** auto-accepted.
- User-verified data is **never** silently overwritten by remote data.
- When confidence is insufficient, the app shows **no value** rather than a guess.
- The release checklist **blocks** publication until the assessment is signed.

### 3.4 Android backup — *disabled for product data*

`android:allowBackup="false"`. This resolves two questions at once:

- **Privacy**: local usage history (which foods, when) never reaches the user's Google account.
- **ODbL share-alike**: with no redistribution of the cached OFF database, the share-alike clause
  is not engaged. A purely on-device cache is not a distributed derivative database.

Cost: saved products do not survive a device migration. Documented in known-limitations.

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
2. **Cached remote local** — shown immediately; background refresh may not block calculation.
3. **Open Food Facts** — network.
4. **Fallback** — scan label / manual entry. The user never hits a dead end.

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

No account, no ads, no analytics SDK, no telemetry, no tracking. Permissions: `CAMERA`, `INTERNET`
only. OCR is on-device; images are not persisted or uploaded. The only network egress is a barcode
lookup to Open Food Facts — the privacy policy must say this explicitly and must **not** claim
"no data leaves the device".

## 9. Known constraints

- Public food data can be wrong or stale; barcodes are reused across reformulations.
- OCR can misread labels; it is always confirmed by the user.
- First lookup of an unknown barcode requires network.
- Regulatory status is **unresolved** and gates publication.
