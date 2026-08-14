# CarbScan — session context

Read this first. It records what previous sessions verified so you don't re-derive it.

## Build an APK right now

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk` (~88 MB).
A copy is kept on the Desktop as **`CarbScan-debug.apk`** — install that on a phone.

Other useful tasks:

```powershell
.\gradlew.bat :app:testDebugUnitTest         # 225 JVM tests
.\gradlew.bat :app:lintDebug                 # lint (clean)
.\gradlew.bat :app:assembleRelease           # minified, UNSIGNED (~64 MB)
.\gradlew.bat :app:connectedDebugAndroidTest # 89 instrumented tests, needs a device
bash tools/dependency-scan.sh                # CVE scan of the shipped dependency graph
```

## What this project is

Native Android app: scan a food barcode → enter portion → read carbohydrate grams. As of
2026-08-14, the portion step can also be a **count** ("2 slices") when a trustworthy per-item
weight exists — see [Countable portions](#countable-portions-2026-08-14) below.
Requirements are in **`docs/MASTER-PROMPT.md`** (referenced throughout as §N).
Design decisions are in `docs/superpowers/specs/2026-08-13-carbquick-design.md` (original) and
`docs/superpowers/specs/2026-08-14-countable-portions-design.md` (countable portions).

**It does NOT calculate insulin.** Not a diet tracker. Scope discipline is a hard requirement (§2).

**Working name is CarbScan** (`app.carbscan`). Docs under `docs/` still say *CarbQuick* — the owner
has NOT decided the public name, so do not sweep the docs. Branding is genuinely centralised in
`branding.gradle.kts` (Gradle `extra` properties feeding namespace, applicationId, versionName,
`app_name` and the OFF User-Agent).

GitHub: **https://github.com/Morfildor/CarbScan** — private, and staying private for now.

## Status (2026-08-14)

- ✅ Domain calculation engine, TDD — `CarbCalculator`, `NutritionBasis`, `PortionParser`,
  `NutritionValueValidator`, `PackageQuantityParser`, `BarcodeValidator`, `ResultFormatter`
- ✅ Room + `ProductRepository` owning the §10 lookup priority
- ✅ Open Food Facts data source behind the `ProductDataSource` abstraction
- ✅ Full UI: home, scanner, calculator, manual entry, verify dialog, label OCR, settings
- ✅ Debug APK and minified release APK both build; release smoke-tested (launches, no crash) on
  the emulator with a debug-signed copy — the committed release artifact stays unsigned
- ✅ §73 documentation set complete in `docs/`, incl. new `security-review.md` and
  `ux-critique-countable-portions.md`
- ✅ CI workflow (`.github/workflows/ci.yml`)
- ✅ §60 Compose UI tests (16 behaviour tests on the calculator)
- ✅ Manual barcode entry (§8); live Open Food Facts verified end to end incl. product images
- ✅ **Countable portions** (2026-08-14) — see dedicated section below
- ✅ **Product development pass** (2026-08-14) — see dedicated section below
- ✅ **225 JVM unit tests, 89 instrumented tests, all passing; lint clean**
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
`CarbScanDatabase`, not just the migration-test harness — see below) plus
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

**Two genuine findings from actually running the tests, not just reading the code:**
1. Room's `MigrationTestHelper` connection does not enforce the `portion_units` FK's
   `ON DELETE CASCADE` the same way a normally-opened `CarbScanDatabase` does — confirmed by adding
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
- **Nutrition-label OCR works.**

These were the two largest unknowns and are now closed. Do not re-list them as unverified.

### NOT verified — do not claim otherwise

- Behaviour across a range of physical devices, incl. Samsung Galaxy specifics (§61 §14).
- **Anything at all on a physical device beyond barcode scanning and label OCR.** Everything in
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
3. **Regulatory** — build to the **stricter** standard (as if an accessory to a medical device).
4. **Backup** — `allowBackup="false"`.
5. **Provenance ≠ verification** (owner correction, 2026-08-13) — `dataSource`
   (`OPEN_FOOD_FACTS`/`MANUAL`/`OCR`) and `verificationStatus` (`UNVERIFIED`/`USER_VERIFIED`) are
   **separate fields and must stay separate**. A product can come from OFF *and* be verified; that
   provenance must be preserved. Do not "simplify" these back into one enum.
6. Repo stays **private**; docs keep saying CarbQuick until the public name is decided.
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
           PortionResolver, ServingSizeParser, PortionUnit(Kind), ProductImageUrlValidator,
           MealStore, PortionUsageStore, LabelComparison, ProductSearch
data/
  local/   Room (v4): ProductEntity/Dao, PortionUnitEntity/Dao, RoomProductDataSource,
           RoomPortionUnitDataSource, RoomMealDataSource, RoomPortionUsageDataSource
  remote/  Retrofit (OFF v3 read + cgi/search.pl) + OpenFoodFactsDataSource
  settings/DataStore
  ProductRepository   ← owns the §10 lookup priority, portion units, meal, usage, search
ocr/       NutritionLabelParser (pure) + LabelAnalyzer (ML Kit)
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
- Room schema is at **v4**; `MIGRATION_1_2` adds `latestRemoteCarbs`, `MIGRATION_2_3` adds
  `portion_units` + three `products` columns for remembered countable-portion mode, `MIGRATION_3_4`
  adds `current_meal_items` (the name is the scope guarantee: there is only ever a *current* meal)
  and `portion_usage`. Never destructive. `MIGRATION_2_3`'s `ALTER TABLE ADD
  COLUMN` calls are guarded by a `PRAGMA table_info` check — see "Countable portions" above for why.
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

1. **§44 regulatory assessment is unresolved and blocks publication.** See
   `docs/regulatory-release-checklist.md`.
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
5. **Physical-device verification of everything built in the 2026-08-14 development pass.** Only
   barcode scanning and label OCR have ever been confirmed on real hardware. Meal, label
   verification, usual portions and search are emulator-only.
6. **Countable portions against a real OFF `serving_size`.** Still fixture-only; no live product
   with a countable-unit-shaped `serving_size` has been checked against real packaging.
   `docs/manual-qa.md` §15a.

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
