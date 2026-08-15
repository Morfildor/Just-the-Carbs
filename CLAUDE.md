# Just the Carbs — session context

Read this first. It records what previous sessions verified so you don't re-derive it.

## Build an APK right now

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk` (~88 MB).
A copy is kept on the Desktop as **`JustTheCarbs-debug.apk`** — install that on a phone.

Other useful tasks:

```powershell
.\gradlew.bat :app:testDebugUnitTest         # 392 JVM tests
.\gradlew.bat :app:lintDebug                 # lint (clean)
.\gradlew.bat :app:assembleRelease           # minified, UNSIGNED unless keystore.properties exists (~64 MB)
.\gradlew.bat :app:connectedDebugAndroidTest # 119 instrumented tests, needs a device
bash tools/dependency-scan.sh                # CVE scan of the shipped dependency graph
```

## What this project is

Native Android app: scan a food barcode → enter portion → read carbohydrate grams. As of
2026-08-14, the portion step can also be a **count** ("2 slices") when a trustworthy per-item
weight exists — see [Countable portions](#countable-portions-2026-08-14) below.
Requirements are in **`docs/MASTER-PROMPT.md`** (referenced throughout as §N).
Design decisions are in `docs/superpowers/specs/2026-08-13-carbquick-design.md` (original — kept
under its original filename/prose as a historical record from when the working name was CarbQuick)
and `docs/superpowers/specs/2026-08-14-countable-portions-design.md` (countable portions), extended
by `docs/superpowers/specs/2026-08-15-ocr-table-and-direct-carb-portions-design.md` (geometry-first
OCR + direct-carb portions), whose implementation plan is
`docs/superpowers/plans/2026-08-15-ocr-table-and-direct-carb-portions.md`.

**It does NOT calculate insulin.** Not a diet tracker. Scope discipline is a hard requirement (§2).

**Public name is Just the Carbs** (`app.justthecarbs`), decided 2026-08-14. Branding is genuinely
centralised in `branding.gradle.kts` (Gradle `extra` properties feeding namespace, applicationId,
versionName, `app_name` and the OFF User-Agent). Historical docs under `docs/superpowers/specs/`
and `docs/superpowers/plans/` keep their original CarbScan/CarbQuick prose as a dated record of
decisions made under the earlier working names — do not sweep those.

GitHub: **https://github.com/Morfildor/Just-the-Carbs** — private, and staying private for now.

## Post-rebrand hardening pass (2026-08-15)

The 2026-08-14 rebrand commit renamed Kotlin identifiers and docs but **missed
`app/proguard-rules.pro`**, which still referenced `app.carbscan.**` throughout — every R8 keep
rule for kotlinx.serialization, Retrofit, Room and domain enums was silently matching nothing.
Confirmed by building a minified release **before** the fix: it still ran, because none of those
reflection paths happened to get stripped by R8's own analysis this time — but the rules were
dead weight and the same gap would eventually break the OFF response parser, Room, or the enum
`valueOf()` calls in a future release build. Fixed alongside `keystore.properties.example`
(pointed at `CarbScan-upload.jks`/`carbscan-upload`). Repo-wide search confirmed `app/src/**`,
build files, CI and resources were otherwise already clean; only prose in historical
`docs/superpowers/**`, `docs/design_handoff_just_the_carbs/README.md` and
`.claude/docs/ai/carbscan/` remains, deliberately unswept.

Also in this pass: OCR ambiguity restored (the UI no longer collapses `LabelReading.Ambiguous` to
`candidates.first()` — see `AmbiguousCard` in `LabelScannerScreen.kt`, up to 3 shown for explicit
choice); a live-frame `AmbiguityStabilityTracker` (pure Kotlin, `ocr/`) so a single incomplete
frame doesn't pause live scanning — ambiguity must repeat for 3 frames or persist ~800ms before it
surfaces, `Confident`/`NotFound` are unaffected, still captures bypass the tracker entirely; a
restrained corner-bracket `ScanRegionOverlay` on the label scanner (visual guide only, OCR still
processes the full frame); `ProductImageSelector.galleryImages()` now synthesizes a `FRONT` entry
from `largeImageUrl`/`imageUrl` when no structured `selected_images` exist, so the gallery opens
whenever the hero photo shows (legacy/cached and search-result products); `OpenFoodFactsApi` split
into `PRODUCT_FIELDS` (unchanged, includes gallery/serving metadata) and `SEARCH_FIELDS` (lean —
search results never needed `selected_images`/`serving_size`); the empty Home state redesigned
from two lines of text into a branded "Scan. Portion. Carbs." composition with a compact 3-step
icon strip and a "Scan nutrition label" tertiary action, still no dashboard content.

Verified by installing the minified release (fresh disposable local test key, never committed)
and driving it on the emulator: launch, live OFF search, live product lookup + gallery, Room
persistence, the redesigned empty Home — zero `ClassNotFoundException` /
`NoClassDefFoundError` / serialization failures in logcat. 259 JVM tests (up from 237), 106
instrumented tests (up from 95), lint clean.

## Status (2026-08-14)

- ✅ Domain calculation engine, TDD — `CarbCalculator`, `NutritionBasis`, `PortionParser`,
  `NutritionValueValidator`, `PackageQuantityParser`, `BarcodeValidator`, `ResultFormatter`
- ✅ Room + `ProductRepository` owning the §10 lookup priority
- ✅ Open Food Facts data source behind the `ProductDataSource` abstraction
- ✅ Full UI: home, scanner, calculator, manual entry, verify dialog, spatial label OCR, product gallery, settings
- ✅ Debug APK and minified release APK both build; release smoke-tested on the emulator with a
  fresh disposable local test key (2026-08-15) — no crash, live OFF search/lookup, gallery, Room
  persistence all verified. The committed release artifact stays unsigned; nothing signed is committed
- ✅ §73 documentation set complete in `docs/`, incl. new `security-review.md` and
  `ux-critique-countable-portions.md`
- ✅ CI workflow (`.github/workflows/ci.yml`)
- ✅ §60 Compose UI tests (16 behaviour tests on the calculator)
- ✅ Manual barcode entry (§8); live Open Food Facts verified end to end incl. product images
- ✅ **Countable portions** (2026-08-14) — see dedicated section below
- ✅ **Product development pass** (2026-08-14) — see dedicated section below
- ✅ **392 JVM unit tests passing; lint clean; debug + minified release both build** (2026-08-15
  OCR/direct-carb pass — was 259 before it)
- ⚠️ **119 instrumented tests, 118 passing.** The one failure is
  `SettingsScreenTest.tappingPrivacyPolicyDoesNotCrashTheScreen`, and it is **pre-existing and
  environmental, not a regression**: verified by stashing the entire pass and running that test
  against clean HEAD, where it fails identically. The API 36 emulator image ships Chrome, so tapping
  the privacy-policy row really does launch a browser, backgrounding the test activity and leaving
  Compose with no hierarchy to assert against ("No compose hierarchies found in the app"). The test's
  own KDoc anticipates having no browser to intercept. It needs an Intents stub or `@Ignore` on
  browser-equipped images — deliberately **not** changed in this pass, being unrelated scope.
- ✅ The previously flaky instrumented test is **fixed** — it was a test bug (a keyboard-covered
  control that `performClick()` silently no-ops on), not app behaviour. Full suite is green.
- ✅ **Dependency vulnerability scan run** — `tools/dependency-scan.sh`, 226 shipped artifacts,
  0 known vulnerabilities (2026-08-14). Point-in-time; re-run before release.
- ❌ Not done: release signing, AAB, systematic multi-device testing

## Countable portions (2026-08-14)

Major feature: portion entry can be a count ("2 slices") instead of a weight, when a trustworthy
per-item weight exists. Full design: `docs/superpowers/specs/2026-08-14-countable-portions-design.md`.
UX critique: `docs/ux-critique-countable-portions.md`. Security: `docs/security-review.md`.

**Architecture** — one formula, unchanged: `PortionResolver` (pure, `count × amountPerUnit`) is a
conversion layer in front of `CarbCalculator`, never a second calculation path.
`ServingSizeParser` cautiously turns OFF's free-text `serving_size` into a `PortionUnitCandidate`
(English + Dutch input recognition — the app's own UI strings stay English-only, an owner
correction mid-session). `PortionUnit` mirrors `Product`'s provenance/verification split exactly.

**Room v3**: new `portion_units` table (FK cascade to `products`, verified against a normally-opened
`JustTheCarbsDatabase`, not just the migration-test harness — see below) plus
`lastInputMode`/`lastSelectedPortionUnitId`/`lastCount` on `products`. `MIGRATION_2_3` guards every
`ALTER TABLE ADD COLUMN` with a `PRAGMA table_info` check — not defensive theatre: Room's own
`MigrationTestHelper` was observed re-invoking the migration during its validation pass, which made
a naive (non-idempotent) `ALTER` fail with "duplicate column" as a pure testing artefact.

**OFF migrated v2 → v3** (checked 2026-08-14: v3 current, v2 deprecated-but-supported, fields this
app reads unchanged between the two). `serving_quantity` is deliberately NOT used to derive a
countable unit — OFF documents it as its own normalized extraction from `serving_size`, not an
independently trustworthy per-unit weight.

**Networking hardened**: `ProductImageUrlValidator` (HTTPS + OFF-image-host allowlist) gates every
remote image URL — previously unvalidated. Retrofit and Coil now share **one** `OkHttpClient`
instance (`AppContainer.okHttpClient`) — previously two separately-constructed clients with
matching config, not a real shared instance.

### Direct-carb conversions (2026-08-15)

`PortionUnit` no longer stores `amountPerUnit`/`basis`. It stores a sealed **`PortionConversion`**:

- `WeightBased(amountPerUnit, basis)` — "1 slice = 35 g", resolved via `PortionResolver` then
  `CarbCalculator`, exactly as before.
- `DirectCarbs(carbsPerUnit)` — "1 slice = 14.2 g carbs", used when OFF gives
  `carbohydrates_serving` but `serving_size` prints no weight. `DirectCarbCalculator` is the only
  place `count × carbsPerUnit` happens. **No gram figure exists on this path and none is invented** —
  `portionText` stays empty, the UI shows "4 slices × 14.2 g carbs", and a direct-carb `MealItem` has
  `resolvedAmount == null`.

A sealed interface rather than nullable fields, so "weight-based with no weight" is unconstructible.

**OFF precedence** (`OpenFoodFactsDataSource.portionUnitCandidate`): a printed weight always wins
(Cases A and C); no weight plus `carbohydrates_serving` gives `DirectCarbs` (Case B); neither gives
**no candidate at all** (Case D) and the UI asks the user once. `carbohydrates_serving` is validated
by `NutritionValueValidator.validateCarbsPerServing`, which deliberately does **not** reuse the
per-100 ceiling — a 500 g meal can legitimately exceed 100 g, so only clearly corrupt data (>1000) is
refused.

The freeze rule is unchanged and applies identically to both kinds: `isRemoteRefreshable` keys on
provenance and verification, never on which conversion the unit holds. `remoteConversionDiffers`
compares **numerically** — `BigDecimal.equals` would report `36` vs `36.0` as a change and show the
user a "portion changed" notice about nothing.

`ServingSizeParser` is split: `parseDescriptor` returns a typed `ServingDescriptor(kind, count,
weightOrVolume?)` where the weight is now **optional**, and `parse` keeps its original
weight-required contract by delegating to it. A weight with no leading count ("portion 25 g") is
still rejected — it states no count-to-quantity relationship.

**Two genuine findings from actually running the tests, not just reading the code:**
1. Room's `MigrationTestHelper` connection does not enforce the `portion_units` FK's
   `ON DELETE CASCADE` the same way a normally-opened `JustTheCarbsDatabase` does — confirmed by adding
   `PortionUnitDaoTest.deletingAProductCascadesToItsPortionUnits`, which uses
   `Room.inMemoryDatabaseBuilder` (the real production path) and passes. Trust the production-path
   test over the migration-harness one for this specific question.
2. ~~The flaky `CountablePortionScreenTest` case~~ — **root-caused and fixed** in the 2026-08-14
   development pass; see that section below. It was never emulator flakiness: a control covered by
   the soft keyboard is not clickable, and `performClick()` on it does not throw, it clicks
   nothing. `performScrollTo()` before clicking is the fix.

### Verified by actually running it (API 36 emulator)

- §70 new product: 48.2 g/100 g × 65 g → **31 g** / *31.3 g calculated*
- §70 known product: tap recent → portion pre-filled → instant result
- ml basis: 9.4 g/100 ml × 250 ml → **24 g**, portion locked to ml
- Dutch comma decimal, dark mode, 1.8× font scale
- Minified release build runs; Room, enums and ML Kit all survive R8

### Verified by the owner on a physical device (2026-08-14)

- **Barcode scanning works.**
- The previous nutrition-label parser was spot-checked. The rebuilt spatial OCR is **implemented,
  but real-world reliability is still under validation** and needs new physical-package coverage.

These were the two largest unknowns and are now closed. Do not re-list them as unverified.

### NOT verified — do not claim otherwise

- Behaviour across a range of physical devices, incl. Samsung Galaxy specifics (§61 §14).
- **Anything in this pass on a physical device beyond barcode scanning.** Everything in
  this pass — meal, label verification, usual portions, search, attribution — was verified on the
  **emulator** only. That is the single biggest standing gap.
- The release (R8) build on physical hardware — it runs on the emulator.
- **Countable portions against a real OFF `serving_size` response.** All automated coverage uses
  fixtures; no live product with a countable-unit-shaped `serving_size` has been scanned and
  checked against real packaging. See `docs/manual-qa.md` §15a, currently unchecked.
- Countable portions on a physical device at all — built and instrumented-tested on the emulator
  only, same caveat as the rest of this build.

## Product development pass (2026-08-14)

Four features plus a design-system pass. Full brief priorities P0→P4; **P3.3 (launcher shortcuts)
was explicitly skipped by the owner.**

**Temporary meal.** Add several calculated portions, read one total. The scope guarantee is
structural, not a rule someone must remember: `MealStore` holds **one** meal and there is **no meal
id anywhere in the codebase**, so "meal history" cannot be built without first adding the concept.
No name, no date. It **does** persist across a restart (Room-backed, verified on the emulator by
force-stopping and relaunching), which is deliberate — losing a half-built plate to an app switch
would be a bug, not scope discipline. What makes it a scratchpad is that there is only ever *one*
and no past meal can exist.

**OCR label verification** (`LabelComparison`, pure domain). Scan a package to check a stored
value. Differences show **both numbers side by side**; nothing is applied without a tap. A basis
mismatch (per 100 ml vs per 100 g) offers **no apply path at all** rather than converting — the app
has no density data and inventing one here would corrupt a stored value.

**Usual portions.** Portions repeated for a specific product become one-tap shortcuts. Per-barcode
only; `PortionUsageStore` deliberately has **no "all usage" accessor**, so a cross-product eating
pattern cannot be assembled from it. No dates, no counts, nothing shown to the user but the
portion. Amounts are normalized with `stripTrailingZeros()` before storage — the column is TEXT, so
`65` and `65.0` would otherwise be different portions.

**Search by name** (`cgi/search.pl`). A fallback from a failed barcode lookup, never the way in,
and **not offered when the lookup failed for network reasons** — the same host is down. A
`ProductSearchHit` is not a `Product` and cannot become one: no provenance, no verification status,
no id. Selecting one runs an ordinary barcode lookup, so "no fuzzy match is auto-selected" holds
because no code path could do it. A search failure is **never** rendered as "no matches" — the
endpoint answered 503 three times during live verification while product reads stayed healthy.

### What only running the app caught

Four layout defects, none caught by any assertion — worth remembering before trusting a green
suite as evidence that a screen is usable:

- The meal bar broke the calculator in **three** different placements before the fourth worked
  (fixed header clipped the portion question; scrolling zone made it invisible with the keyboard
  open; full-size in the pinned panel grew upward over the portion field).
- The Usual row made the portion zone taller and pushed *+ Add portion unit* half under the panel.

Also: a geometric regression test I wrote was itself invalid — it compared before/after positions
while `performTextInput` opened the IME, so it measured ~268 dp of keyboard, not layout movement.
A single-layout `panelTop >= fieldBottom` assertion replaced it.

## Geometry-first nutrition table parsing (2026-08-15)

The OCR parser previously grouped text into rows using ML Kit's `blockId`/`lineId` and then scored
candidates by proximity. On real multi-column and hierarchical labels that could return a **child
nutrient's** value as total carbohydrate — ML Kit both splits one printed row across several lines
and merges two printed rows into one, and a proximity score could be outvoted by geometry.

Four pure-Kotlin stages under `ocr/`, each independently tested:

1. **`LogicalRowBuilder`** — rows from box geometry alone: vertical overlap ≥ 0.5 against the
   *running* row box, with a centre-distance tiebreaker at 0.6 median heights. `blockId`/`lineId` are
   retained for diagnostics and **never** consulted for row membership. Thresholds live in
   `LogicalRowThresholds`, deliberately separate from and stricter than `NutritionParserThresholds` —
   a row boundary is now a hard structural claim, not one soft signal among many.
2. **`RowClassifier`** — `TOTAL_CARBOHYDRATE` / `CARBOHYDRATE_CHILD` / `HEADER` / `OTHER`. A row
   naming any child nutrient (sugars, polyols, starch, fibre, dextrose, glucose, fructose, sucrose,
   lactose, maltose, maltodextrin, glucose syrup, …) is `CARBOHYDRATE_CHILD` **unconditionally** — a
   type-level exclusion checked *before* the carbohydrate check, so "Carbohydrate of which sugars"
   is a child row. This is the correctness claim of the whole rewrite; it is not a score penalty.
3. **`ColumnClassifier`** — `PER_100_G` / `PER_100_ML` / `PER_SERVING` / `REFERENCE_PERCENT` /
   `UNKNOWN`. Headers are the primary signal; a cell-shape fallback recovers a percent column whose
   header OCR lost (≥2 percent-shaped cells sharing an x position). It never guesses per-100 vs
   per-serving from shape — those stay `UNKNOWN`, and an `UNKNOWN` cell is never used for any figure.
   A span matching two vocabularies at once returns null so shorter spans are tried, which is what
   keeps "per 100 g per 100 ml" as two columns rather than one.
4. **`NutritionTableInterpreter`** — associates the total row's cells to columns, producing the
   unchanged `LabelReading` plus a new `servingCandidate: ServingCarbCandidate?` carrying a typed
   `ServingDescriptor`, so the OCR→save flow reads `descriptor.count` without re-parsing header text.

`NutritionTableParser` shrank from 458 to ~76 lines and is now just an adapter. `LabelReading`,
`CarbCandidate`, `MlKitOcrMapper` and `AmbiguityStabilityTracker` are untouched, so live-scan
stability behaviour is unchanged.

**Deliberate behaviour change:** a carbohydrate value whose column was never resolved is now
`NotFound` rather than `Ambiguous` with a null basis. A value the parser cannot place on the label is
not a reading; the app asks for a better photo instead of asking the user to supply the basis.

**OCR → "Save as a slice portion"**: a still capture whose serving column named a countable unit
offers to save it as a `PortionUnit` (`ProductDataOrigin.OCR`, `USER_VERIFIED` — the user was reading
the package). Only from a still capture that passed the explicit accept step, never from a live
frame; `LabelAnalyzer.analyzeStill` now reports the whole `NutritionParseReport`, while the live path
still deals only in `LabelReading` so a camera frame cannot persist anything.

## Toolchain (installed — do NOT reinstall)

| Thing | Path |
|---|---|
| JDK 21.0.12 (Temurin) | `C:\atools\jdk-21.0.12+8` |
| Android SDK | `C:\atools\sdk` (platforms 36 **and 37**, build-tools 36.0.0 and 37.0.0) |
| Emulator + API 36 image | installed; AVD named **`carbscan`** |

Launch the emulator headless:

```powershell
C:\atools\sdk\emulator\emulator.exe -avd carbscan -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
```

Boot takes ~90 s. Then `C:\atools\sdk\platform-tools\adb.exe devices`.
Note: `connectedAndroidTest` **uninstalls the app afterwards** — reinstall before driving the UI.

### Traps that cost real time

1. **`winget install` hangs forever** — UAC prompt nobody can answer. Portable archives only. No
   admin rights available.
2. **Windows MAX_PATH** breaks SDK extraction into deep scratchpad paths. That's why everything
   lives under the short `C:\atools`. `Expand-Archive` misreports it as a *missing file* error.
3. **`sdkmanager --licenses` ignores piped stdin** — write hash files into `$ANDROID_HOME\licenses`.
4. **AGP 9.x has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is a hard error.
   Kotlin options go in `android { kotlin { } }`.
5. **KSP has its own version line** (2.3.11), which does *not* track Kotlin's (2.3.21). Not a bug.
6. **`compileSdk` must be 37, not 36.** AndroidX (core 1.19.0, Compose 1.12.0, lifecycle 2.11.0)
   refuses to compile against 36. `targetSdk` stays **36** — the Play requirement. Independent.
7. **`resValue` needs `buildFeatures { resValues = true }`** in AGP 9.
8. **Don't put the JDK in the session scratchpad.** Temp dirs get cleaned; that is why it now lives
   at `C:\atools\jdk-21.0.12+8`.

## Verified facts (checked 2026-08-13 — do not trust training data over these)

| Fact | Value |
|---|---|
| Play target API requirement | **API 36**, deadline **2026-08-31** |
| AGP / Gradle / JDK | 9.3.1 / 9.7.0 / 21 |
| OFF read rate limit | **15 req/min/IP** — makes cache-first mandatory, not optional |
| OFF User-Agent | Mandatory, must identify the app; documented format `AppName/Version (ContactEmail)` |
| OFF data licence | **ODbL**+DbCL — attribution *and* share-alike |
| OFF image licence (confirmed 2026-08-14) | **CC BY-SA** — separate from the data licence |
| OFF product-read endpoint (confirmed 2026-08-14) | **v3** (`api/v3/product/{barcode}`) — v2 deprecated-but-supported |
| OFF image hosts (confirmed 2026-08-14) | `images.openfoodfacts.org` (live-verified), `static.openfoodfacts.org` (doc-sourced) |

## Owner's confirmed decisions

1. **ml vs g** — portion locked to the product's basis unit. Never assume 1 ml = 1 g.
2. **Rounding** — **decimal dominant** (`31.3 g`), whole gram beneath (`≈ 31 g whole grams`).
   Revised 2026-08-14 (correction #6): the result is transcribed into another calculator, so
   leading with the rounded figure loses precision where it matters. `ResultStyle.WHOLE_DOMINANT`
   restores the old hierarchy if a whole-gram-only destination is ever confirmed.
3. **Regulatory** — build to the **stricter** standard while qualification is unresolved. Never
   state the app is, is not, or is "as if" an MDR accessory/medical device (see decision 9).
4. **Backup** — `allowBackup="false"`.
5. **Provenance ≠ verification** (owner correction, 2026-08-13) — `dataSource`
   (`OPEN_FOOD_FACTS`/`MANUAL`/`OCR`) and `verificationStatus` (`UNVERIFIED`/`USER_VERIFIED`) are
   **separate fields and must stay separate**. A product can come from OFF *and* be verified; that
   provenance must be preserved. Do not "simplify" these back into one enum.
6. Repo stays **private**. **Just the Carbs** (`app.justthecarbs`) is the current, decided public
   name and namespace (2026-08-14) — see the header above. Historical docs under
   `docs/superpowers/**` intentionally keep their original CarbScan/CarbQuick prose as a dated
   record; that is not an open decision, just an unswept historical record.
7. **No Robolectric** — DAO tests stay instrumented.
8. **Calculation-session immutability** (correction #5) — once the calculator is open, a background
   refresh must NEVER change the value being calculated with. It records the newer figure and shows
   an *Online value changed* notice the user can accept. Regression-tested; do not "simplify".
9. **Regulatory wording** — never describe the app as an accessory to a medical device, or as a
   medical device, or as not one. Qualification is unresolved; the docs say only that the controls
   are conservative while it is.
10. **Countable-portion app language is English-only** (2026-08-14, owner correction mid-session —
    the original brief's Dutch requirement was leftover from an earlier draft). This is about
    *displayed UI strings* only: `ServingSizeParser` still recognizes Dutch remote `serving_size`
    text (the owner is in the Netherlands and OFF data for their products is legitimately Dutch) —
    parsing input in Dutch and displaying the app in English are separate facts, don't conflate
    them if this is revisited.

## Architecture

```
domain/    Pure Kotlin, ZERO Android imports — the safety-critical layer. Keep it that way.
           PortionResolver, ServingSizeParser, PortionUnit(Kind), ProductImage, ProductImageUrlValidator,
           MealStore, PortionUsageStore, LabelComparison, ProductSearch
data/
  local/   Room (v5): ProductEntity/Dao, PortionUnitEntity/Dao, RoomProductDataSource,
           RoomPortionUnitDataSource, RoomMealDataSource, RoomPortionUsageDataSource
  remote/  Retrofit (OFF v3 read + cgi/search.pl) + OpenFoodFactsDataSource
  settings/DataStore
  ProductRepository   ← owns the §10 lookup priority, portion units, meal, usage, search
ocr/       OcrDocument + geometry-first table layer (LogicalRowBuilder → RowClassifier →
           ColumnClassifier → NutritionTableInterpreter, all pure) + ML Kit mapper/LabelAnalyzer
           boundary. NutritionTableParser is now a thin adapter over the interpreter.
ui/        Compose screens + ViewModels, immutable state via StateFlow
           product/, meal/, search/, components/ (shared design system)
```

Key invariants, each pinned by a test:

- `movePointLeft(2)` for ÷100 — exact scale shift, cannot round or throw.
- Whole gram and displayed decimal are derived from `exact` **independently** — never round twice.
- `NutritionBasis` is a label, **never** a conversion factor.
- `isRemoteRefreshable` = not user-authored **AND** unverified. Both conditions matter. Same rule,
  same reasoning, applies per-unit to `PortionUnit.isRemoteRefreshable`.
- Carbohydrate values (and countable-portion weights) are stored as **TEXT** in SQLite, never REAL.
- `ResultFormatter` sets `RoundingMode.HALF_UP` explicitly — `DecimalFormat` defaults to HALF_EVEN,
  which made the app display a different decimal from the one it calculated (15.4 vs 15.5).
- Room schema is at **v6**; `MIGRATION_1_2` adds `latestRemoteCarbs`, `MIGRATION_2_3` adds
  `portion_units` + three `products` columns for remembered countable-portion mode, `MIGRATION_3_4`
  adds `current_meal_items` (the name is the scope guarantee: there is only ever a *current* meal)
  and `portion_usage`; `MIGRATION_4_5` adds nullable selected-image gallery metadata;
  `MIGRATION_5_6` **rebuilds and copies** both `portion_units` (weight columns →
  `conversionKind`/`conversionValue`/`conversionBasis` plus six remote-variant columns) and
  `current_meal_items` (adds `itemKind`, makes `resolvedAmount`/`basis`/`carbsPer100` nullable).
  Rebuild-and-copy because SQLite cannot drop `NOT NULL` in place. **Row ids are preserved by the
  copy**, which is why `portion_usage.portionUnitId` still resolves — there is a migration test
  asserting exactly that join. Every migrated row is explicitly labelled (`'WEIGHT'` /
  `'WEIGHT_BASED'`), never left NULL for a mapper to infer. Never destructive. `MIGRATION_2_3`'s
  `ALTER TABLE ADD COLUMN` calls are guarded by a `PRAGMA table_info` check — see "Countable
  portions" above for why.
- `MealStore` has **no meal id** and `PortionUsageStore` has **no all-usage accessor**. Both
  absences are the scope guarantee (§2) expressed structurally — adding either would make a food
  diary buildable. Do not add them "for symmetry".
- Countable-portion amounts and usage amounts are normalized with `stripTrailingZeros()` before
  storage, because the columns are TEXT and `65` vs `65.0` would otherwise be distinct portions.
- `PortionResolver` is the only place `count × amountPerUnit` happens; it never itself computes a
  carbohydrate value — that stays `CarbCalculator`'s job alone, keeping one formula in the app.

## Working agreements

- Verify library versions against Google Maven / Maven Central. **Stable only.**
- `domain/` stays pure Kotlin — it must be JVM-testable with no emulator.
- **Never claim something builds or passes without having run it.**
- Don't fabricate regulatory or policy wording (§44, §50). If it can't be verified, mark it as an
  owner action with a place to record the source and date.

## Open findings needing the owner

1. **§44 regulatory assessment is drafted but unsigned, and still blocks publication.** The
   manufacturer's assessment is `docs/regulatory-qualification-assessment.md` (conclusion: **not a
   medical device**, EU only, conditional on its §7 marketing constraints); a PDF export exists for
   signature. Signing it closes checklist rows A1/A3 — **A2 (independent review), A5 (non-EU
   markets) and A6 (listing wording) stay open**, so publication remains NO-GO. Gate rows are in
   `docs/regulatory-release-checklist.md`; the release order is `docs/play-release-readiness.md`.
   §7.1 forbids marketing the app for diabetes and forbids the owner's personal pump use appearing
   in any published material — that constraint is binding on store copy and review replies.
2. **ML Kit telemetry: investigated and settled as far as code can settle it (2026-08-14).**
   `com.google.android.datatransport` comes from `com.google.mlkit:common` and **cannot be
   excluded** — doing so fatally crashes the scanner (`NoClassDefFoundError: CCTDestination`),
   verified on the emulator. No opt-out constant exists in the shipped artifacts; none was
   invented. Disclosed in the privacy policy and Data Safety draft. Owner still owes a review of
   Google's ML Kit disclosures and the Data Safety category choice.
3. **Open Food Facts licence review** — the *attribution* is now done (see below), but whether the
   overall use of OFF data and images complies is a separate question and still the owner's. The
   ODbL share-alike condition is the one most easily broken by an innocuous feature (export, sync,
   sharing, server-side caching), so reassess before any such feature ships.
   See `docs/third-party-notices.md`.
4. Licence for the project not yet chosen.
5. **Physical-device verification of the rebuilt spatial OCR and gallery.** Barcode scanning and
   the previous OCR implementation were spot-checked on real hardware. The new parser, still
   capture path, and product gallery are emulator-only.
6. **Countable portions against a real OFF `serving_size`.** Still fixture-only; no live product
   with a countable-unit-shaped `serving_size` has been checked against real packaging.
   `docs/manual-qa.md` §15a.
7. **Direct-carb portions (Case B) against real data.** The "no printed weight, but
   `carbohydrates_serving` is present" path is covered by fixtures and emulator runs only. No live
   OFF product with that exact shape has been scanned and checked against its package.
   `docs/manual-qa.md` §15b–§15c.
8. **The rebuilt geometry-first OCR interpreter on physical hardware.** All seven adversarial
   fixtures pass as unit tests, but the two real-device failures that motivated the rewrite — a
   multi-column label and a hierarchical one — have not been re-photographed on the original
   packages. `docs/manual-qa.md` §15d–§15e.

### Closed in the 2026-08-15 OCR/direct-carb pass

- ~~OCR could return a child nutrient (sugars, dextrose, a %RI figure) as total carbohydrate~~ —
  child rows are now excluded by row *type* before any number is read; see "Geometry-first nutrition
  table parsing" above.
- ~~Countable portions required a per-item weight, so a label giving only per-serving carbs sent the
  user to fetch a kitchen scale~~ — `PortionConversion.DirectCarbs`; see "Direct-carb conversions".
- ~~`SearchViewModel` could write a stale in-flight response under newly-edited query text~~ —
  `onQueryChanged` now bumps `requestId`, cancels the running job, and clears displayed results;
  dedupe keys on `displayedQuery` so an A→B→A retype genuinely re-searches.
- ~~Comments claimed OFF Search allows 15 req/min~~ — corrected to 10 for the search endpoint only;
  the product-read path's 15/min comments were already right and were left alone.

### Closed in the 2026-08-14 development pass

- ~~CC BY-SA attribution line missing~~ — added to Settings → About, verified rendering on device.
- ~~Dependency vulnerability scanning never run~~ — `tools/dependency-scan.sh`, 226 artifacts,
  0 known vulnerabilities. Re-run before release; a clean scan expires.
- ~~No UI path to correct a wrong remote-suggested per-unit weight~~ — inline correction now calls
  `verifyPortionUnit(unitId, confirmedAmountPerUnit)`.
- ~~Contact email was the `REPLACE_ME@example.com` placeholder~~ — owner supplied
  `albinogorillassupport@gmail.com` (2026-08-14). It now feeds the mandatory OFF User-Agent from
  `branding.gradle.kts`, closing a real compliance gap: the previous value identified nobody.
- ~~One order-dependent flaky instrumented test~~ — root-caused to a keyboard-covered control that
  `performClick()` silently no-ops on. Fixed per-interaction; full suite green.

### Closed in the 2026-08-15 post-rebrand hardening pass

- ~~`proguard-rules.pro` still referenced `app.carbscan.**`~~ — every R8 keep rule updated to
  `app.justthecarbs.**`; see that section above.
- ~~`LabelScannerScreen` silently promoted `LabelReading.Ambiguous.candidates.first()` to a
  confident-looking answer~~ — now shows up to 3 distinct candidates for explicit choice.
- ~~Live OCR could pause scanning on the very first ambiguous frame~~ — `AmbiguityStabilityTracker`
  requires the interpretation to repeat for 3 frames or ~800ms before surfacing it.
- ~~Legacy/cached products with a hero photo but no structured gallery had no way to open it~~ —
  `ProductImageSelector.galleryImages()` synthesizes the missing `FRONT` entry.
- ~~OFF search requested the same gallery/serving fields as a full product lookup~~ — split into
  `PRODUCT_FIELDS`/`SEARCH_FIELDS`.
- ~~Empty Home was two lines of text in a large void~~ — redesigned into a branded
  "Scan. Portion. Carbs." composition.
