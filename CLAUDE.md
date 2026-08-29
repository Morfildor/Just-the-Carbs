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

GitHub: **https://github.com/Morfildor/Just-the-Carbs** — **public** (owner, 2026-08-16). It was
private until GitHub Pages was needed to host the privacy policy, which Pages will not serve from a
private repo on a free account. This reverses the earlier "stays private" decision; treat everything
in the repo as publicly readable. Nothing signed and no keystore is committed, and
`keystore.properties` is git-ignored — re-check that before any release work.

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
- ✅ **434 JVM unit tests passing; lint clean; debug builds** (2026-08-16 T1D UX pass — was 429
  before it). The minified release was **not** rebuilt in this pass; the last verified release build
  is the 2026-08-15 one.
- ✅ **140 instrumented tests, all passing** (2026-08-16 T1D UX pass). The two `quickAdjust*` cases
  had been failing on HEAD as well — a **test** bug, not an app bug: the ± row sits below the fold,
  where a node has empty bounds and `performClick()` presses nothing. Both now `performScrollTo()`
  first; see the dedicated section below. The previously documented
  failure, `SettingsScreenTest.tappingPrivacyPolicyDoesNotCrashTheScreen`, **no longer exists**: it
  was replaced by `tappingPrivacyPolicyOpensTheCommittedUrlAndKeepsTheScreen`, which stubs Compose's
  `LocalUriHandler` instead of letting a real browser launch, so the browser-backgrounding problem
  is structurally gone rather than merely tolerated. Do not re-list it as a known failure.
  Note: a full-suite run occasionally aborts with a UTP `TEST_EXECUTION_FAILED` driver error part
  way through (it recorded 123/123 green, then failed the build). It does not reproduce and the
  affected classes pass in isolation — emulator/instrumentation flakiness, not a code failure.
- ✅ The previously flaky instrumented test is **fixed** — it was a test bug (a keyboard-covered
  control that `performClick()` silently no-ops on), not app behaviour. Full suite is green.
- ✅ **Dependency vulnerability scan run** — `tools/dependency-scan.sh`, 226 shipped artifacts,
  0 known vulnerabilities (2026-08-14). Point-in-time; re-run before release.
- ✅ **Release signing done 2026-08-26** — real owner upload key; signed AAB built. See the
  2026-08-26 section below. ❌ Still not done: keystore backup, systematic multi-device testing

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

## Default theme is Light (2026-08-15)

On a fresh install the app opens in **Light regardless of the Android system theme**. Only the
*default* moved; the Settings selector still offers System / Light / Dark and each behaves as before
(an explicit `SYSTEM` still follows the OS in both directions).

The default lives in **three** places that must agree, because `MainActivity` renders
`AppSettings()` for the frame or two before DataStore answers and the repository value afterwards —
if only one moved, a fresh install would visibly flip theme during launch:

1. `AppSettings.theme` (`domain/Settings.kt`)
2. the missing/unknown-value fallback in `SettingsRepository` — an unrecognised stored value means a
   corrupt or downgraded preference file, not a request for the system theme
3. `JustTheCarbsTheme`'s default argument (`ui/theme/Theme.kt`)

`ThemeDefaultTest` (instrumented) asserts the **rendered** `colorScheme.background` with
`LocalConfiguration` forced to system-dark and system-light, not which enum was passed in — the enum
round-trip is already covered by the JVM tests, and the rendered colour is where a wrong default is
actually visible. It carries a self-check that light and dark backgrounds differ and that the forced
configuration really reaches `isSystemInDarkTheme()`, without which every other case in the class
would pass vacuously. Verified on the minified release build on the emulator, not just in tests.

## Home entry points (2026-08-15)

Home now presents all three ways in as first-class actions. Design:
`docs/superpowers/specs/2026-08-15-home-entry-points-design.md`.

**The bug was structural, not cosmetic.** "Scan nutrition label" was rendered only inside
`EmptyState`, which lives in the `recents.isEmpty()` branch — so the app's third entry point
**disappeared permanently after the user's first scan**. Every returning user had two ways in, not
three. A green 133-test suite never caught it because nothing asserted the action existed in the
non-empty state.

**Direct Home → label scanning already worked and needed no navigation change.** `JustTheCarbsNavHost`
already passed `onScanLabel = { navController.navigate(Routes.labelScan()) }`, and the no-context path
is complete: empty barcode → `productExists == false` → `onSavePortionUnit = null` and
`onCarryPendingPortionUnit` non-null → an accepted reading routes to `Routes.manual("", carbs, basis)`,
carrying any detected countable portion as typed arguments into the same creation flow used when a
barcode lookup misses. Verified on the emulator by tapping the card with no product context: the
scanner opens directly. Do not "add" this flow again — only its presentation was ever missing.

**Layout.** One `LazyColumn` (`HomeBody`) holds, in order: filled *Scan barcode* card, outlined
*Scan nutrition label* card, *Enter manually*, then either recents or the branded starter hero. The
barcode action moved out of its pinned bottom slot into this scroll region — the only arrangement
where the two scanners read as a matched pair *and* precede history. `HomeActionCard` is one
composable parameterised by `filled`; the outlined variant reuses `RecentCard`'s exact surface and
border so the column is visibly one system, and spends colour only on its `tertiaryContainer` icon
roundel. No new palette, no new drawables.

**Two defects only running the app caught** (the suite was green for both):

1. Pinning *Enter manually* to the bottom edge left ~900px of dead space between the last card and
   the screen edge — the screen read unfinished. It is now the last content item instead.
2. Ordering *Enter manually* **after** the starter hero put it beyond the composed window at 1.8×
   font scale, where `LazyColumn` never composes it at all — `performScrollTo` failed with "could not
   find any node". The action did not merely sit below the fold, it did not exist. It now precedes
   the hero: the hero is reassurance, manual entry is a function.

`search_hint` ("Product or brand name") is now a placeholder under a real `label` of *Search
products* — previously the word "search" appeared nowhere on Home and a magnifier glyph carried the
entire discovery burden. Explicit-search behaviour is unchanged: typing never calls Open Food Facts.

## UX polish pass (2026-08-16)

An app-wide friction and visual-coherence audit. No behaviour, calculation, schema or parser change
— every finding below was presentation. `PRODUCT.md` and `DESIGN.md` were added at the repo root,
transcribed from the design handoff, `docs/MASTER-PROMPT.md` and `Theme.kt`; **`Theme.kt` stays
authoritative** if DESIGN.md drifts.

**Raw enum constants were reaching the user.** The "Add portion unit" type picker rendered
`kind.name`, so it listed `SLICE`, `PIECE`, `BISCUIT`, `SACHET`, `CUSTOM` in screaming caps, and two
other sites used `kind.name.lowercase()`. The plurals already existed for every kind; they were only
reachable from a `PortionUnit`, not a bare `PortionUnitKind`. Added
`PortionUnitKind.kindLabel(count)` in `PortionUnitLabels.kt` and routed all three sites through it.
`CUSTOM` gets its own string ("Something else") because it has no built-in word — its label is text
the user has not typed yet.

**The product photo is now sized against the screen, not a flat 150 dp** (owner request: "much
bigger, but appropriately"). `PHOTO_HEIGHT_FRACTION = 0.28f` clamped to 150–280 dp, which is ~245 dp
on a typical phone. Verified against a live Open Food Facts product: the jar's label and its "400G"
badge are legible, and the per-100 figure, portion field, adjusters and pinned result all still fit
without scrolling.

**Three heights, and the ordering between them is load-bearing:**

- a real photo gets the proportional height;
- **no photo gets 84 dp**, not the photo height — the monogram is derived from the name printed
  directly above it, so it identifies nothing and a 150 dp slab spent a third of the screen
  restating two letters;
- `compact` (IME open) gets 64 dp, reduced from 92 dp because the enlarged photo pushed the portion
  field under the pinned panel.

`compact` is checked **before** `!hasImage`. With the checks the other way round the monogram plate
was the one element that never gave height back while typing, which is how the result and the
equation ended up below the fold — caught by the instrumented suite, not by reading the code.

**Other fixes:** the meal-total panel gained the `resultElevation` shadow the calculator's identical
panel already had (without it, `surfaceContainerLowest` on cream is a ~1% difference and the
screen's most important number had no edge); the portion field gained a greyed `0` placeholder,
cleared from semantics so text searches cannot match the input instead of the result; `manual_carbs`
now names its unit ("Carbs per 100 g/ml") tracking the basis chips, since that is the one field
where the ambiguity has a numeric consequence; the unverified-source hint hides while the keyboard
is open.

**Microcopy:** `product_result_label` `CARBOHYDRATES` → `CARBS` and `home_empty_body` now says
"carbs", per the one-vocabulary rule. Dutch `KOOLHYDRATEN` is left alone — it has no shorter
idiomatic form.

**Home's empty state no longer draws `ic_launcher_foreground`.** It is a 108 dp adaptive-icon vector
whose two paths are *white* shapes meant to read against the launcher's own coloured background,
inside a 72 dp safe zone. Tinted dark on a light tile the figure and ground invert and it renders as
an indistinct blob. Only visible by looking at the screen. The headline now leads and the existing
3-step strip does the visual work; the mark still renders correctly on onboarding, where it is white
on blue as designed. **Do not put the launcher icon on a light surface again.**

**Two test-quality fixes made while chasing real failures:** `MealScreenTest` read the literal
`"CARBOHYDRATES"` (now resolved from resources, so a position assertion cannot fail over wording),
and two `ProductScreenTest` assertions searched the whole screen for text like `"0.0 g"` — which the
*portion field* also matches once it holds a value and a unit suffix. Both are now scoped to
`PRODUCT_RESULT_TAG`, so they genuinely assert about the result.

**Deliberately rejected:** `weight(1f, fill = false)` on the portion zone removes the remaining gap
above the result panel but unpins the panel from the bottom edge, leaving a strip of page beneath
it. Tried both ways on the emulator; the gap is the lesser problem and the panel must stay welded to
the bottom. Also rejected: a −/+ stepper for countable portions (the count field already pre-fills
`1` and selects-all on focus, so it is one tap plus one keystroke) and any haptics beyond the
existing copy feedback.

## T1D consumer UX pass (2026-08-16)

A UI/UX review from the perspective of someone with type 1 diabetes counting carbs to dose insulin,
then the pre-release half of it implemented. Review:
`docs/plans/2026-08-16-t1d-ux-review.md` (11 findings, ranked, tagged pre/post-release).
**Nothing in this pass changed calculation, schema, navigation or parsing** — all presentation.

**The eight implemented (pre-release) items:**

- **The result slot no longer says "Enter a portion" into a void.** It shows the per-100 figure the
  result will be scaled from, in `NumberType.supporting` and the ordinary variant colour —
  deliberately **not** `NumberType.result` or the result hue, because a per-100 figure that looked
  like an answer is the worst available confusion on that screen. The 96 dp reservation is unchanged.
  The countable path now says *Enter a count*, since "portion" named the wrong input there.
- **Provenance now appears at the result, not only at the top of the screen.** `SourceBadge` drops
  its advisory line while the IME is open, so at the moment the user reads the number and decides
  whether to act, nothing on that half of the screen said whether the figure was ever checked
  against the package. One plain line, `bodySmall`, variant colour, **no icon and no alarm hue**.
  Only for `OPEN_FOOD_FACTS` — a MANUAL or OCR value was by definition read off the package.
  Wording is a fact about the *data* ("Not checked against the package"), never about a consequence
  ("may affect your dose"): the first describes a crowd-sourced database, the second is medical
  advice this app never gives.
- **Quick-adjust steps scale with the package** — `quickAdjustStep(packageAmount)`, a pure
  `internal` function with its own JVM test (`QuickAdjustStepTest`, 5 cases). A fixed ±5 g was
  one-hundredth of a 500 g pack and a quarter of a 20 g biscuit. Ladder is 5/10/25/50, keyed on the
  **same confidently-parsed package size `PackShortcuts` already gates on**, so it introduces no new
  guess; **no package size keeps the original ±5/±10**. Steps stay round numbers — a computed "+37"
  is defensible and unusable. `adjust_plus_five`/`_ten` etc. replaced by parameterised
  `adjust_plus`/`adjust_minus` in both locales.
- **The per-100 line is now `SemiBold`.** It is what every result derives from and what an
  experienced counter sanity-checks first; it was rendering lighter than a product name the photo
  and top bar already establish.
- **Copy holds a visible confirmation** — the icon swaps to a check for `Motion.COPIED_STATE_MS`
  (2500 ms). The Toast was the *only* confirmation and is transient, easily missed one-handed, and
  gone by the time the user looks back from the app they are pasting into. The Toast stays: it is
  what announces the copy to TalkBack. Keyed on the copied value, so copying a different number
  after changing the portion restarts the confirmation rather than reusing a running timer.
- **`verify_label_basis_mismatch` had a lowercase `t` after a full stop** — in the one dialog
  specifically about the app refusing to do something risky.
- **Onboarding promised a control that does not exist.** `onboarding_body_2` said "Drag, type, or
  tap a preset"; there is no drag/slider/pointer API anywhere in `ui/` (verified by search, not
  assumed). Now "Type it, or tap a preset."
- **Minor:** `meal_bar_summary` now says "g carbs" not bare "g" (one-vocabulary rule); dead
  `settings_results_whole` / `settings_results_decimal` removed from both locales — `ResultStyle`
  has two entries and only the `_first` variants were ever referenced.

## Production-hardening pass (2026-08-25) — READ FIRST

Not committed. Builds on the same uncommitted working tree as the sections below; nothing about the
calculation, schema, migrations, the §10 lookup priority or **barcode detection** changed.

### The basis coin-flip is gone, and column provenance is now a field

`CandidateChoice` had a branch, for a candidate whose basis was never established, offering two
buttons — *Use / 100 g* and *Use / 100 ml* — each of which **committed the value immediately**. That
is the one question this app must never ask. Bare grams on a nutrition table can equally be per
100 g, per 100 ml or **per serving**, so the card offered two answers to a three-way question and
whichever the user picked became indistinguishable from a value the parser had actually placed. It
now shows the number, says plainly that what it is measured *per* was not read, and routes to manual
entry pre-filled — where the basis is a visible, changeable chip rather than a one-tap commitment.

The branch was **unreachable from the automatic path** when found (both candidate factories take a
non-null basis) and is kept as a latent-hazard closure, not a live bug fix. Do not restore the two
buttons. `ocr_use_per_100_g` / `_ml` are still used by `AssistedReadingScreen`, where the user has
*tapped the row themselves* and is being asked deliberately — that is a different question.

`CarbCandidate.column: NutritionColumnKind?` makes the provenance checkable. Null means "no column
was involved" (an inline declaration or prose sentence), **not** "unknown column" — that is
`UNKNOWN`, and a cell in one never becomes a candidate. The `init` block makes `PER_SERVING`,
`REFERENCE_PERCENT` and `UNKNOWN` candidates *unconstructible*, so the guarantee is type-level rather
than a rule someone must remember. `CandidateColumnProvenanceTest` (8 JVM cases) pins it, and states
the safety invariant in prose: **a safe non-result is better than a confidently wrong carbohydrate
value.**

### Scan latency: the evidence recorder was the bottleneck, and it is now off the path

`ScanEvidenceRecorder.recordCapture` copied a ~3.5 MB JPEG and `recordPassABitmap` PNG-encoded a
~24 MB bitmap, **synchronously, between the parse finishing and the user seeing anything**. That is
most of the ~3.27 s of a 3.55 s device scan that `ScanTrace` measured outside the recognizer.

- `consumeCapture` **moves** the file (`renameTo` within `cacheDir`) instead of copying it, and
  returns whether the caller must still delete it. In release it returns false without touching
  anything and the delete runs exactly as before.
- `recordPassAImageAsync` encodes on a background daemon thread, **after** the result handover, and
  re-decodes from the moved `capture.jpg` rather than touching the live bitmap. That second part is
  a correctness fix, not just a scheduling one: the bitmap's ownership transfers to the crop screen,
  which recycles it on Retake, so encoding it in the background races a recycle into a native crash.

**A `by lazy` on an `object` breaks the release privacy check.** The background writer started as
`private val writer by lazy { … }` and that alone moved `ScanEvidenceRecorder` from absent to
**present** in release `mapping.txt` — R8 correctly stripped every method but had to keep `<clinit>`
for the `Lazy` field, and with it the class and its `ThreadFactory` lambda. A plain null-initialised
`@Volatile var` plus a `writer()` accessor folds away instead. Now `R8$$REMOVED$$CLASS$$`.
**When checking these barriers, `R8$$REMOVED$$CLASS$$` on the right-hand side means removed** — a
mapping to a real short name like `ab3` is what "retained" looks like.

### Other latency work

- `SelectedRegionRecognizer` built and closed a **new ML Kit client per call**, charging the user for
  native detector setup on the one tap where they are already waiting, and guaranteeing the next tap
  pays it again. One process-lifetime client now, never closed — unlike `LabelAnalyzer`'s, which
  belongs to a camera session and is correctly closed with it.
- Its timeout went 8 s → **5 s**. Deliberately *not* down to the ~2 s the measurements suggest: this
  is a hang guard whose only effect in the normal case is nothing at all, and setting it near the
  expected duration converts "slow phone" into "second opinion silently unavailable" — a quality
  regression bought with a latency win the user never experiences. Unverified on low-end hardware.
- `readSelectedTable` moved from `Dispatchers.Default` to **`Dispatchers.IO`**. Strategy B parks a
  thread on a `CountDownLatch`; Default is CPU-count-sized and meant for work that never blocks.
- Shutter-to-file is now logged (`acquisition …ms`). It happens entirely outside `analyzeStill`, so
  `ScanTrace` could not see it and the evidence bundles had a hole exactly where sensor readout,
  JPEG encode and file write live.

### Lookup single-flight, and a stale-result overwrite

`ProductViewModel.load`'s guard tested `product != null` — precisely the field a lookup that has
*started but not finished* has not written. Two calls close together both saw null and both went to
the network, against a 15 reads/min/IP budget. Worse, both completion branches write state
unconditionally, so a **slow abandoned lookup landing after a fast current one put the previous
product on screen under the new scan's barcode**. Now a `lookupJob`: same barcode in flight → join;
different barcode → cancel the old one.

`ProductLookupSingleFlightTest` pins both. **Verified non-vacuous by negative control** — with the
guard and the cancel commented out, both cases fail. The overwrite case needs a **per-barcode** delay
in the fake: with one shared delay the two lookups complete in start order and the test passes
without any cancellation whatever.

### A lint crash that is a lint bug, not a code defect

`lintAnalyzeDebug` died with `Unexpected failure … (this is a bug in lint)` —
`resolveSyntheticJavaPropertyAccessorCall` on `JustTheCarbsNavHost.kt`. Isolated by swapping in
HEAD's copy of that one file, which lints clean: the trigger was the previous session's 10-line
`onCorrectValue` addition, a second nested lambda containing the same `basis.name` navigation
expression as its sibling. Hoisting both into one local `openManualEntryWith` function fixes it.
Behaviour is identical; only lint could tell the two forms apart. **Do not chase this as a code
error, and do not suppress it** — deduplicating the expression is the fix.

### Verified this pass

JVM **726/726** (0 failures, 0 errors, **0 skipped**, counted from JUnit XML), lint **exit 0**, debug
APK, minified release APK (65 MB) and **release AAB** (35 MB) all build from `clean`.

Release R8 barriers re-checked. `ScanEvidenceExport` and `OcrDiagnosticsReport` have no mapping entry
at all; `ScanEvidenceRecorder` maps to `R8$$REMOVED$$CLASS$$`. `UnitMarkerFilter` and
`CandidateProvenance` are retained as real classes. `MergedTotalRowRecovery` also reads
`R8$$REMOVED$$CLASS$$` — that is the **inlined-not-dropped** case this file already warns about for
`ElementRegionFilter` and `SelectedTableReader`, not a stripped safety rule: it is a single-function
object on the answer path, inlined into the interpreter. Do not read that marker as "the feature
shipped disabled" without checking behaviour.

**Still NOT verified on a physical device — and the whole point of the latency work is a device
number.** The before figure (3.55 s median / 8.1 s worst) is measured; the after figure is not. Also
unverified on hardware: the 5 s Strategy B bound on a low-end phone, and the async evidence writer
under repeated rapid captures.

## Dutch label recognition (2026-08-26) — READ FIRST

Owner instruction: the **UI stays English**, and **scanning Dutch packaging must work superbly** —
"the app can't detect carbs text in Dutch very well, especially nutritional table scan." It could
not, and the reasons were vocabulary, not architecture. No parser rule was relaxed and no threshold
was tuned; the real-image corpus is unchanged.

**Do not conflate the two halves.** English UI strings and Dutch *input recognition* are separate
decisions (owner decision 10). `values-nl/strings.xml` is gone; `ServingSizeParser`'s Dutch words,
`product_name_nl` preference and everything below are input recognition and are being **extended**.

### Measure first — and check the harness before believing it

`DutchLabelDiagnosticTest` (JVM, prints, asserts almost nothing) runs printed Dutch label forms
through the real interpreter and reports the outcome per form. `DutchNutritionTableTest` asserts what
it found. Shared geometry lives in `DutchLabelFixtures`.

**The first version of that harness produced six false failures.** It laid every header word
left-to-right from one origin, so `Voedingswaarde per 100 g` pushed its own `per 100 g` hundreds of
pixels right of the values it heads, and every long Dutch header "failed". Acting on that would have
meant tuning the parser against a picture no package resembles — the same class of error as the
geometric regression test that turned out to be measuring the soft keyboard. **A header phrase is
centred over the column it describes; a leading noun sits left, in the label column.**

The same applies to the merged-row fixture: laying the child to the *right* of the total's value puts
its number in no column at all, so every case passes because the value was unplaceable. Modelling the
safe version of a hazard proves nothing. The child keeps the **same value column**, with boxes
overlapping 25 of 40 px — the geometry the 2026-08-16 chaining bug produced.

### Three findings, all measured before and after

1. **`per 100 gram` resolved no column, so the scan returned `NotFound`** with a correct value on a
   correct total row. `per 100 milliliter` likewise. **Five** places each held a private `g|ml`
   literal, and fixing `ColumnClassifier` alone did nothing: `RowClassifier` must type the row
   `HEADER` before the column vocabulary is ever consulted. One shared
   `domain/BasisUnitSpellings` now feeds `ColumnClassifier`, `RowClassifier`, `InlineBasisSpans`,
   `ProseNutritionReader`, `ServingWeightAssociator`, `NutritionTableInterpreter` and
   `ServingSizeParser`. It lives in `domain/` because `ServingSizeParser` is there and `domain/` may
   not depend on `ocr/`. Only spellings of the two bases the app *has* — an ounce still resolves
   nothing, pinned by a test.
2. **Dutch child-nutrient names were missing**, and the measured consequence on a merged row was
   `Ambiguous [62.0, 35.0]` — the app asking someone about to dose insulin to choose between the
   total and the sugars figure with nothing on screen to say which is which. Not a confident-wrong
   (the architecture held), but not acceptable either. Dutch prints **`sacharose`** where English
   prints `sucrose`, and uses transparent compounds — **`melksuiker`** (lactose), **`druivensuiker`**
   (dextrose), **`vruchtensuiker`** (fructose) — that share no stem with their Latin equivalents, so
   the shared English list could never have covered them. Also added: `suikeralcoholen`,
   `meervoudige alcoholen`, `voedingsvezel` (singular), `vezelstoffen`, `zetmelen`, `glucosestroop`.
   All now type `CARBOHYDRATE_CHILD`, and the merged row refuses.
3. **`Koolhydraat`, `Koolhydr.` and `Kool-hydraten` were not the word for carbohydrate**, so those
   labels read nothing at all. Dutch hyphenates long compounds across a line break and normalization
   turns the hyphen into a space, so the printed `Kool-hydraten` arrives as two words — hence
   `"kool hydraten"` as a term, with a test that a bare `Rode kool` still reads nothing. German
   `Kohlen-hydrate` gets the same treatment, plus `Milchzucker`/`Traubenzucker`/`Fruchtzucker`.

Dutch countable words were also extended (`plak`, `plakje`, `snee`, `wafel`, `blokje`, `bol`,
`beker`, `glas`, `eetlepel`, `theelepel`). Those only ever create a `PER_SERVING` column, which by
construction can never supply the per-100 figure — the cost of missing one was a lost feature, not a
wrong number, which is why nobody noticed.

### Verified

JVM **771/771** (0 skipped, `--rerun-tasks`). The nine-photograph corpus is **unchanged**:
`RealImageOcrTest` 15, `ProductionStillPipelineTest` 8, `SelectedTableProductionTest` 6 and
`EvidencePipelineProductionTest` 8 — **37/37 on device, run after these changes**. Negative control
is the diagnostic's own before/after output, on the same fixtures: the unlisted child terms measured
`Ambiguous [62.0, 35.0]` and now measure `NotFound`; `per 100 gram` measured `NotFound` and now
measures `Confident 62.0`.

**The full 218-test instrumented suite was NOT completed after these changes.** The last clean
whole-suite run is **218/218 (0 skipped, 14m19s)**, taken earlier the same day — after the
release-blocker fixes but **before** the Dutch vocabulary work. The ~180 tests not covered by the
37/37 OCR run are Compose UI and Room tests that this vocabulary work does not touch, but that is an
argument, not a measurement. Do not record 218/218 as evidence for the Dutch changes.

### The AVD went into a crash loop — recognise this before blaming a test

Four consecutive attempts aborted, presenting as three different problems and sharing one cause:
**the emulator process was dying and restarting under the run.**

| Symptom | What it actually was |
|---|---|
| `Adb connection Error: Connection reset` → `Connection refused` | adb losing a device that had gone away, not adb misbehaving |
| `Unable to find instrumentation target package` + `DELETE_FAILED_INTERNAL_ERROR` | a package operation issued while the device was going down |
| `JustTheCarbsDatabaseMigrationTest > migratingFromV4…` **FAILED** | not a migration defect — see below |

That last one is the trap: it names a real test and reads like a genuine regression. The per-test
logcat says otherwise:

```
Failed to open APK '/data/app/…/app.justthecarbs.debug-…/base.apk': I/O error
java.io.IOException: Failed to load asset path …/base.apk
PackageManager$NameNotFoundException: app.justthecarbs.debug
```

The app under test was **physically unreadable on the emulator's virtual disk**. No migration ran.
Confirmed independently: `uptime` reported `up 0 min` three separate times without anyone rebooting
it, and the qemu process reappeared at 436 MB where it had been 2.8 GB.

**Diagnosis order that works**: read the per-test logcat under
`app/build/outputs/androidTest-results/connected/<avd>/logcat-<class>-<method>.txt` **before**
reading the assertion. An I/O error on `base.apk`, or `NameNotFoundException` on the app's own
package, means the device is broken and the named test is a bystander. Host RAM was 9 GB free of
32 GB throughout, so this was not host pressure.

**The repair is `emulator -avd carbscan -wipe-data`** — a corrupt AVD disk image does not recover on
its own, and the self-reboots only clear the orphaned package directory, not the corruption.

**Still unverified:** no Dutch package has been scanned on physical hardware since this change. The
forms above come from Dutch and Belgian packaging conventions, not from photographs in this repo —
which is exactly why the diagnostic prints rather than asserts, and why the next real Dutch failure
should be added to it before anything is changed.

## Final release-blocker pass (2026-08-26) — READ FIRST

Six concrete defects, all confirmed by reading the code and then reproduced by a test that fails on
HEAD. Nothing about the calculation, the OCR architecture, barcode detection, the §10 lookup
priority or the signing key changed.

### "Clear recent history" and "Clear saved products" both under-delivered

Two privacy controls that did less than their labels said, in ways nothing surfaced.

`clearRecentHistory` was `UPDATE products SET lastUsedAt = NULL, lastPortion = NULL WHERE favorite
= 0`. Three things wrong with one statement:

1. **`lastInputMode`, `lastSelectedPortionUnitId` and `lastCount` were never cleared.** Together
   those three *are* a remembered portion — they are exactly "2 slices" — so a cleared product still
   pre-filled the count the user last ate.
2. **`WHERE favorite = 0` exempted every favourite**, which kept its entire usage history through an
   action whose label says nothing about exempting rows.
3. **`portion_usage` was untouched**, so the *Usual* shortcuts survived and reappeared on the next
   visit to the same product.

`deleteAllProducts` was `DELETE FROM products`. `portion_units` cascades and went with it;
**`portion_usage` has no foreign key at all** — deliberately, so per-product usage is not coupled to
the product row's lifetime — so every usage aggregate was orphaned in place. Re-scanning the same
barcode recreated the product row, the orphans matched it by string, and portions from a product the
user had deleted came back as shortcuts. `rescanningAClearedBarcodeResurrectsNoUsageHistory` drives
the whole round trip, because a `SELECT` straight after the delete does not show the defect.

Both are now `@Transaction` methods on `ProductDao`, which is why that DAO issues statements against
three tables — one atomic user action whose entire claim is that nothing survives it should not be
split across DAOs. `current_meal_items` is deliberately **not** cleared by either: a meal item is an
immutable snapshot designed to outlive its product (that is why `MIGRATION_3_4` gave it no FK), and
the meal is the plate being assembled right now, not saved product data.

**The privacy policy said "Delete everything the app has stored".** It does not — settings and the
in-progress meal survive. Both `docs/privacy-policy.md` and the **live** `docs/privacy-policy.html`
now spell out exactly what each action clears, and the in-app confirmation strings were corrected to
match. Policy and behaviour have to move together; the HTML is what is actually published.

### An unproven Open Food Facts basis no longer becomes grams

`PackageQuantityParser.inferBasis` returned `PER_100_G` for any `quantity` it could not parse, and
its own comment argued this was safe because the app never converts between units.

**That argument is true of the arithmetic and beside the point.** The basis decides *the unit the
portion field asks a human to measure in*. A drink whose quantity reads "1,5 liter" produced a
product asking for grams; a user who complies — weighing 250 ml of a syrup that weighs 330 g — types
a number a third too large, and every downstream stage then behaves perfectly on it. Nothing can
detect it afterwards.

`inferBasis` is deleted. `PackageBasisResolver` (pure, 16 JVM cases) resolves in order:

1. **`product_quantity_unit`** — OFF's own normalized unit, now requested and deserialised. This is
   what rescues "390 gram", "1,5 liter" and multipack notation without teaching this app's parser
   grammar it should not have.
2. **An unambiguous free-text quantity** — `PackageQuantityParser` for a single size, and otherwise
   the consistency rule: a basis is read from free text only when **every** unit token attached to a
   number agrees. `6 x 33 cl` is ambiguous about the pack *size* and not about *centilitres*, so the
   basis resolves and `packageAmount` stays null. `250 g / 300 ml` disagrees and resolves nothing.
3. **`Unresolved`** → `ProductFetchResult.Unusable(barcode, UnusableReason.UNKNOWN_BASIS)`.

A structured unit that contradicts an unambiguous printed quantity refuses rather than ranking the
two — there is no evidence for preferring either.

**Ordering matters and is pinned:** "is there a number at all?" is answered *before* "what is it
measured per?", using the permissive millilitre ceiling, so a record missing both facts reports *no
value* rather than asking someone to choose a unit for a number that does not exist.

The new `Failure.UnknownBasis` leads with **Enter manually**, not the label scanner: the figure was
never in doubt, only its denominator, and manual entry is the one screen where the basis is a visible
changeable chip. Search hits with no established basis keep their name, brand and photo and show
**no number** — `ProductSearchHit.basis` is now nullable and `SearchResultRow` reads value and unit
together, so a hit built inconsistently degrades to "no value" instead of printing an assumed unit.

`product_quantity` is deserialised through a `LooseNumericText` serializer because OFF sends it as a
bare number in some records and a quoted string in others; a strictly typed property throws on
whichever form it was not declared for, and this app reports that as *malformed response*. Scoped to
that one field rather than switching the parser to `isLenient`, which would relax quoting for the
carbohydrate values too.

**Fixture note:** eleven existing tests had to gain an explicit `"quantity"`. They were silently
relying on the grams default, which is the clearest possible evidence that the default was doing
real work nobody had noticed.

### Dutch localization removed — the app ships in English only

`values-nl/strings.xml` carried **206 of 298 strings and none of the 10 plurals**. A Dutch device got
about two thirds of the interface in Dutch and every countable-portion plural in English mid-sentence.

It was a leftover from an earlier draft of the brief; the owner's 2026-08-14 decision is that
displayed UI strings are English-only. Finishing it would have meant shipping ~100 unreviewed strings
— including the safety and provenance copy — with no native speaker to check them before release.
The file is deleted and `androidResources { localeFilters += "en" }` makes it structural, which also
stops AndroidX and Material supplying a Dutch "Cancel" inside an English dialog. Verified: the
release APK contains **no language configurations at all**.

**Parsing is untouched and must stay so.** `ServingSizeParser` still recognises Dutch `serving_size`
text and `product_name_nl` is still preferred. Recognising Dutch input and displaying Dutch are
separate facts — do not "fix" one by changing the other.

### The CI release gate did not gate on instrumented tests

`ci.yml`'s `instrumented` job carries `continue-on-error: true`, and its comment claimed the
`release` job was "the actual release gate" — but that job ran only JVM tests and `assembleRelease`.
**No workflow anywhere required the instrumented suite to pass**, which is the suite that covers the
Room migrations, the committed real-image OCR corpus and every Compose behaviour test.

New `.github/workflows/release-gate.yml`: `workflow_dispatch` plus `v*` tags, three sequential
blocking jobs, **no `continue-on-error` anywhere** and none may be added. It runs JVM with
`--rerun-tasks` (a plain run restores FROM-CACHE and proves nothing), asserts **0 skipped** from the
JUnit XML on both suites, and checks the R8 privacy barriers and the absence of a release
`FileProvider` as build steps rather than as something a human remembers to look at. `ci.yml`'s
tolerance is unchanged and now honestly labelled as branch-only.

### Verified this pass

JVM **754/754** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up from
726). Instrumented **218/218** (up from 214). Lint exit 0, 34 advisories, 0 errors. Debug APK
(89.4 MB), minified release APK (66.8 MB) and release AAB (35.6 MB) all built from `clean`. OSV scan
re-run: 226 resolved release artifacts, 0 known vulnerabilities, control query passing.

R8 barriers re-checked: `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`;
`ScanEvidenceExport`, `OcrDiagnosticsReport` and `ScanTrace` absent entirely; `UnitMarkerFilter`,
`CandidateProvenance`, `CarbCandidate` and the new `PackageBasisResolver` retained as real classes.
Release manifest: CAMERA, INTERNET, ACCESS_NETWORK_STATE (transitive, disclosed); one exported
component of ours (`MainActivity`); no `FileProvider`. Both release artifacts signed with the real
upload key (`1E:21:23:F3:…:C4:F5`), not the disposable one.

### Two things found and deliberately NOT changed

- **`uses-feature android:name="android.hardware.camera"` ships as *required*** — implied by the
  CAMERA permission. The app genuinely works without a camera (manual entry is a first-class path and
  the privacy policy says so), so `android:required="false"` would be correct. It is left alone
  because it changes which devices Play offers the app to, and that is a distribution decision for
  the owner rather than something to alter inside a release-blocker pass.
- **The §44 regulatory assessment is still in reachable public Git history** — `7a3b43a` (the .md)
  and `7212efb` (the .pdf), removed in `5675c45`, both ancestors of `main`. Untracked today, exposed
  historically. No history was rewritten; the procedure, the ordering question that decides whether
  this is a cleanup or an incident, and the reasons a force-push is not a full remedy are in
  `docs/git-history-remediation.md`. **Owner action.**

## Real upload key exists (2026-08-26) — SUPERSEDES THE "NOT FOR PLAY" SECTION BELOW

The owner generated a production upload keystore in Android Studio and built a signed AAB with it.
**No code changed** — `app/build.gradle.kts` is untouched in the signing region and the fail-closed
guard is exactly as committed; only `keystore.properties` (gitignored) now points at the real key.

| | |
|---|---|
| Keystore | `C:\secure\JustTheCarbs-upload.jks`, alias `justthecarbs-upload` |
| Signer DN | `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen` |
| Key | 2048-bit RSA, SHA256withRSA, valid 2026-08-26 → 2051-08-20 |
| Cert SHA-256 | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` |
| AAB | 35,689,027 bytes, SHA-256 `00876FA9…BBB4A2`, `versionCode 1` / `versionName 1.0.0` |

**`apksigner` cannot read an AAB, and this was a bundle-only build** — there is no
`app/build/outputs/apk/release/` at all, so the verification command in the section below cannot be
run on this artifact. Use `keytool -printcert -jarfile <aab>` or `jarsigner -verify -certs <aab>`
instead. jarsigner's "self-signed certificate" and "no timestamp" warnings are **expected and
correct** for an Android upload key; they are not defects and do not need fixing.

Barriers re-checked on *this* build's `mapping.txt`: `ScanEvidenceRecorder` and
`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport` and
`ScanTrace` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate` retained as
real classes. Release manifest carries only the ML Kit init provider and `androidx.startup` — zero
`FileProvider`/evidence matches.

**What this bundle did NOT close** *(historical — superseded by the release-candidate section below)*.
It was built from the **uncommitted working tree**, so it was not a release candidate, and no test,
lint or OSV run was repeated against it. That bundle (`00876FA9…BBB4A2`) is **superseded**; do not
upload it.

## Closed-beta quality pass (2026-08-28) — READ FIRST

Conservative pass taken **while `versionCode 1` is live to internal testers**. Nothing about the
calculation, the schema, migrations, the §10 lookup priority, barcode detection or any OCR safety
rule changed.

**`versionCode` was bumped to 2 / `versionName` 1.0.1 in this pass, and that version is OPEN.**
Several builds go out over the beta as findings come in; 1.0.1 is the first of them. The working
rules for the open cycle live in **"Version and track state"** below — that section is authoritative,
this one only records that the bump happened here.

Two defects reproduced by a test that fails on HEAD before the fix, one hardening change, plus one
latency change.

1. **The label scanner's focus timeout outlived the screen.** `focusThenCapture` posts a
   `postDelayed(FOCUS_TIMEOUT_MS)` fallback that fires the shutter if autofocus never reports back.
   **Nothing cancelled it.** Closing the scanner within 1.2 s of tapping capture left it queued; it
   then ran after `onDispose` had called `executor.shutdown()` and unbound the camera, and
   `takePicture` hands its callback to that executor — `RejectedExecutionException` on the main
   thread from an ordinary "tap capture, change your mind" gesture. The existing session guard
   cannot cover it: that guard is read *inside* the callback which never gets to run. Fixed by
   checking the existing `disposed` flag in `fireOnce`, the single chokepoint both paths go
   through, and by delivering the focus listener on `mainExecutor` — a listener registered on a
   shut-down `ExecutorService` is rejected at dispatch, before `fireOnce`'s own guard is reached.
2. **The lookup single-flight guard had a gap after the fetch.** See the superseded "Not a defect"
   section below, which this pass corrects.
3. **Hardening, not a fixed defect — `activeHandle` in `CropConfirmationScreen` was a private
   top-level `var`**, process-wide mutable state shared by every crop screen, on the argument that
   `onDragStart` always sets it first. `detectDragGestures` runs `onDragEnd`/`onDragCancel` only
   while its pointer input is alive, so a drag interrupted by *Retake* could leave it set. Now a
   per-instance `remember`. **An earlier revision of this file stated that the next capture's first
   drag then resized from a stale corner instead of translating. That was never reproduced and is
   withdrawn** — reachable state, unproven consequence, the same distinction the focus-timeout entry
   is now careful about. The proven crop defect is the accumulation one in the stabilization section
   below. Keep the per-instance state; it costs nothing and isolates the state by construction.

**Latency:** `LabelAnalyzer.analyze` ran the full geometry-first parse on live frames whose result
the two `!paused` checks then discarded. `paused` is set the instant the user taps capture, so that
discarded parse sat directly between the shutter and `startPendingStillIfPossible()` — the call that
begins recognising the 8 MP still. It now returns before the parse. Behaviour-neutral by
construction: `resume()` calls `stability.reset()`, so tracker state from skipped frames could never
have surfaced anyway (already pinned by `explicit reset clears tracking`).

**Patch notes are now kept, and split in two** (owner instruction): `CHANGELOG.md` at the repo root
carries Unreleased plus the version currently on a track, and `docs/version-history.md` is the
append-only archive holding each uploaded artifact's hash, size, signer and dates. A version's
section is copied across **verbatim** when superseded — the point of the archive is that it records
what was believed at the time. Do not rewrite shipped entries; add a dated note instead.

Every version also carries a **Play Store release notes** block (owner instruction) — the text for
Play Console's *What's new*, deliberately far less granular than the engineering change list. Rules
are in `docs/version-history.md`; the ones easy to get wrong: **500 characters max**, group small
fixes into one line rather than enumerating them, describe what the user sees rather than what
moved, and **never make a health claim or mention diabetes** — this field is published material and
§44 §7.1 binds it exactly as it binds the store listing.

**Larger ideas found and deliberately deferred** are in
`docs/plans/2026-08-28-post-beta-backlog.md` — including the two most honest gaps in this pass:
`LabelAnalyzer` has no JVM coverage at all (the pause-ordering change is argued from
`AmbiguityStabilityTracker.reset()`, not demonstrated), and neither scanner fix has an instrumented
test, because both need a composable disposed mid-gesture against a faked camera.

**Verified:** JVM **773/773** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit
XML — up from 771). Lint exit 0, 0 errors, 41 advisories. Debug APK builds.

**Instrumented: 216/218, and the 2 failures are a PRE-EXISTING FLAKE, proven by worktree control.**
**→ SUPERSEDED 2026-08-28: root-caused to the soft keyboard and FIXED. The suite is now 218/218 in
one whole-suite run. See "The instrumented flake is fixed" below — the analysis in this section was
right that it was pre-existing and not a code regression, and wrong that it was unfixable harness
noise.**
The whole-suite run reported `MealScreenTest.addingTwoPortionsTotalsThemInTheBar` and
`addAndScanNextRecordsTheItemAndLeavesForTheScanner` failing with "is not displayed". Do not read
that as a regression from this pass, and do not "fix" `MealScreenTest`:

- **`MealScreenTest` never constructs `ProductViewModel`.** `showCalculatorWithMeal` builds a
  `ProductUiState` literal and renders `ProductScreen` directly, so the `load`/`onProductLoaded`
  change cannot reach it. Checked, not assumed.
- **Clean HEAD flakes identically.** A `git worktree` at `cc01789` — none of this pass's changes —
  run three times gave 19/19, **18/19**, 19/19, failing
  `addingToTheMealKeepsThePortionFieldAndResultVisible`: a *third* test name, same "is not
  displayed" mode. Three runs, three different victims, on unmodified code.
- `ProductScreenTest` gave **1 then 2** failures across two identical runs, the top one being
  `quickAdjustNeverProducesANegativePortion` — the below-the-fold harness issue this file already
  documents.

The failures are visibility/settling artefacts of the Compose harness (see the two existing
sections on keyboard-covered and scrolled-out-of-view controls), not app behaviour. **`exit code 0`
from `connectedDebugAndroidTest` did not mean the suite passed** — the wrapper reported 0 while
Gradle printed `BUILD FAILED`. Always count from the JUnit XML.

**The classes covering what this pass actually changed are green**, run individually on the final
build: **`RealImageOcrTest` 15/15, `ProductionStillPipelineTest` 8/8, `SelectedTableProductionTest`
6/6, `EvidencePipelineProductionTest` 8/8 — the full 37/37 nine-photograph corpus**, which is the
suite that would catch an OCR regression from the `LabelAnalyzer` change.

**Not** re-verified in this pass: the release/AAB build and the R8 privacy barriers — no release
build was made. `versionCode 2` is claimed but nothing has been built or uploaded against it.

## Stabilization pass (2026-08-28, later same day) — READ FIRST

Still `versionCode 2` / `1.0.1`, still **OPEN**; nothing built or uploaded. Nothing about the
calculation, the schema, migrations, the §10 lookup priority, barcode detection or any OCR safety
rule changed. Two real defects fixed, one unproven claim corrected, and the instrumented flake
root-caused and closed.

### Every fresh product lookup cost TWO Open Food Facts requests

Not a race and not an edge case — **every** first-time scan. `lookup` misses the cache, fetches,
and saves; `onProductLoaded` then calls `refreshFromRemote`, which reads that just-written row,
sees a product worth refreshing, and re-fetches the same barcode microseconds later. Against
15 reads/min/IP shared by everyone behind one address.

**The existing test could not see it, and the reason is the important part.** `EmptyLocal.save` in
`ProductLookupSingleFlightTest` was a no-op, so nothing was ever stored, so `refreshFromRemote`
returned at its first `local.fetch` and its request never happened. *A fake that cannot store is not
a cache*, and the single-flight guarantees it asserted were being measured over half the path. With
a persisting fake, **four** tests fail on HEAD, all reporting `[barcode, barcode]` — including the
two that were previously green.

Fixed at the one place that owns the lookup priority, not at the call site: `lookup` stamps
`remoteUpdatedAt` when it saves a freshly fetched product, and `refreshFromRemote` skips a product
synced inside `REMOTE_FRESHNESS_WINDOW` (30 s). The window is sized to cover one load and nothing
more — it must never become a cache policy, because the background refresh is the only thing that
can notice a reformulation for a product served from cache (§24, correction #10).

Two properties decide whether the guard is safe, and both are pinned: **a null `remoteUpdatedAt` is
not fresh** (it means never-refreshed — a pre-existing row or a user-authored one, which must still
be checked), and **a future timestamp is not fresh** (a backwards clock change would otherwise
freeze every refresh until real time caught up). Verified non-vacuous by negative control: removing
the stamp fails all four cases.

**Say what it does, not "nothing else changed".** The window is a time rule, not a rule about which
call site asked, so it suppresses more than the one duplicate it was written for: a product synced
within the last 30 s is not refreshed *whoever* asks, which includes reopening the same product
inside half a minute. Anything synced longer ago refreshes exactly as before. Do not write "cached
products still refresh exactly as before" — that was in an earlier draft and is not true of the
30 s window. Accepted for 1.0.1: the cost is one skipped re-check within half a minute, against a
duplicate request on every first-time scan.

**Corrected 2026-08-28 (documentation pass):** `lookup` saved the stamped copy and returned the
unstamped one, so the record handed to the caller and the record in the cache disagreed about
`remoteUpdatedAt` — the returned product read as never-synced. Nothing read that field off the
returned value, so this was latent rather than an observed defect. `lookup` now returns
`fetched.copy(product = stamped)`; a cache hit still returns the cached row untouched, because
stamping a read would make every product look freshly synced and silently suppress the refresh.
Pinned by `a fresh lookup returns the same product it cached` and `a cached lookup returns the
cached product unstamped`.

### The crop rectangle moved a tenth as far as the finger did

`detectDragGestures` suspends inside one `pointerInput` block for the whole gesture, so the lambda
reads the `selection` captured when that block last started — and the block's key is `displayed`,
which cannot change while the user drags inside the image. `dragAmount` is an **increment**, not a
total, so every event computed `rectangleAsAtGestureStart + thisDelta` and the increments replaced
each other instead of accumulating.

**Measured, not argued:** ten 10 px events moved the rectangle to x=210 instead of x=300. Every
corner resize was affected identically, and a second gesture restarted from the original rectangle,
silently discarding the first — which is the ordinary way anyone adjusts a crop.

Gesture state now lives in `ui/scan/CropGestureState.kt`, a pure class with no Compose or Android
types, so the transition sequence is JVM-testable (8 cases). Reintroducing the captured-value read
fails exactly the four accumulation cases. Recomposition was never a fix for this and depending on
one landing between two pointer events would be the same bug with better luck.

### A crash mechanism that was documented as fact and was not proven

The previous pass's comment stated that the uncancelled focus timeout firing after `onDispose`
throws `RejectedExecutionException` on the main thread. **The reachable state is provable by reading
the code; the specific exception is not** — it was never reproduced on a device, and CameraX may
catch it, surface an error callback, or fail differently. Comment rewritten to separate what is
established from what is not, and the changelog entry moved out of *Fixed* into a *Hardening* group.
The guard itself is kept: it costs nothing and the state it guards is real.

**The general rule this is an instance of:** a reachability argument establishes that code *can* run
in a given state. It does not establish what that run *does*. Do not promote the second to fact
without a reproduction.

**The same correction was applied to the crop handle on 2026-08-28** (documentation pass). The
process-global `activeHandle` really could survive a Retake mid-drag — reachable — but the claim
that the next capture's first drag then *resized instead of moved* was never reproduced and is
withdrawn everywhere it appeared: `CropGestureState.kt`, `CropConfirmationScreen.kt`,
`CropGestureStateTest.kt`, `CHANGELOG.md` and the pass entry above. The per-instance `remember`
stays, described as state isolation. **The proven crop defect is the accumulation one above** — do
not let the two merge back into one story.

### The instrumented flake is fixed — it was the soft keyboard

Previously recorded here as unfixable Compose-harness noise with an arbitrary victim per run. It is
neither arbitrary nor noise.

**Measured in three steps.** (1) Run alone, the meal bar sits at `Rect(53, 954, 1027, 1039)` in a
1080x2400 root, `placed=true`, stable across six samples and three runs — so there is no layout
defect and nothing that needs longer to settle. (2) Logcat shows Gboard as
`SoftKeyboardView{0,0-1080,641}`: a real 641 px window over the bottom of the screen, and every
failing assertion was on an element pinned there. `assertIsDisplayed` tests visibility against the
window, so it was reporting the truth. (3) **The control that settles it:** with the IME disabled
via `adb shell ime disable`, `MealScreenTest` passed **19/19 three times consecutively**; with it
enabled, exactly one arbitrary test failed per run. The variable is the keyboard.

The victim looked random because it was whichever test ran while a previous test's IME was still up
— which is also why a worktree control at clean HEAD reproduced it on a *third* test name and was
misread as proof of irreducible flakiness.

Fixed in the tests, where the defect is: `typePortion` types, dismisses the keyboard and waits for
idle, which is what a real user does before reading the total. **No retries, no `@FlakyTest`, no
`@Ignore`, no sleeps, no weakened assertions.** `ProductScreenTest` had the same defect and needed
the same fix — note it passed **32/32 on three consecutive runs and then failed on runs 4 and 5**,
which is the reason this file now insists on repeated runs rather than two.

**Result: 8 consecutive clean runs of each class (19/19 and 32/32), then the full suite 218/218 in
one whole-suite run, 0 failures and 0 ignored.**

### Verified

JVM **789/789** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up from
773). Instrumented **218/218** in one complete run, 0 ignored, counted from instrumentation status
codes. Real-image OCR corpus **37/37**, unchanged, which is what clears the `LabelAnalyzer` pause
change. Lint exit 0, 0 errors, 41 advisories. Debug APK builds (89.6 MB).

**Not** done in this pass, deliberately: no release/AAB build, no R8 barrier re-check, no
`versionCode` change. Still unverified on physical hardware — the crop drag fix, the scanner
disposal guard and the latency work all remain emulator-and-JVM-only.

## Documentation-consistency pass (2026-08-28, third pass same day)

Still `versionCode 2` / `1.0.1`, still **OPEN**; nothing built or uploaded. No feature work, no
schema, migration, calculation, parser or UI change. Three documentation corrections plus one small
production change:

1. **The version/track story was contradictory across five files** — some passages still read as if
   `versionCode 1` were the development target and creating `versionCode 2` were the thing to avoid.
   Replaced with one authoritative section: **"Version and track state"** below. Fix that section and
   let the others defer to it; do not restate the rules in a third place.
2. **The crop-handle claim was demoted from defect to hardening** (see the reachability rule above).
3. **The 30 s freshness window is now described by what it does**, not as "cached products refresh
   exactly as before" — see the correction in the stabilization section above.
4. **`ProductRepository.lookup` returns the record it caches**, stamp included. Latent inconsistency,
   not an observed defect; details in the stabilization section above.

**Verified:** JVM **791/791** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit
XML — up from 789, the two new repository tests). Negative control run: reverting the return value to
the unstamped product fails `a fresh lookup returns the same product it cached`. Lint exit 0. **The
instrumented suite was not re-run** — the only production change is a repository return value with
JVM coverage and no UI surface, and the standing figure is the stabilization pass's 218/218.

## Live debounced search (2026-08-28) — opens 1.0.2 / versionCode 3

Search runs as you type. Nothing about the calculation, the schema, migrations, the §10 lookup
priority, barcode detection, any OCR rule or the 30 s product-refresh window changed — the diff is
`SearchViewModel`, `SearchScreen`, one comment in `HomeScreen`, and tests.

**One pipeline, not two paths.** `MutableStateFlow<SearchRequest?>` → `flatMapLatest` → the search,
collected once in `init` on `viewModelScope`. Live edits emit `immediate = false` (debounced
`LIVE_SEARCH_DEBOUNCE_MS = 600`); the IME action and the search button emit `immediate = true`. The
reason both go through one flow is the duplicate they would otherwise produce: a debounce pending
for "hagelslag" plus a keypress for "hagelslag" is two requests for one query, against a
10 reads/min/IP budget.

**The null emission is load-bearing.** `requests.value = null` is what cancels a pending debounce,
so nulls must reach `flatMapLatest` — an upstream `filterNotNull()` leaves the queued `delay`
running and fires a request for a query the user has already deleted. The inner flow returns early
on null instead.

### Three findings that only running the tests produced

1. **`collectLatest` stalls the pipeline against a transport slow to cancel.** It waits for the
   previous block to finish unwinding before starting the next, so with a search that does not
   return promptly on cancellation the **next query is never sent at all** — measured: three
   stale-protection tests failed with the second query missing from the call list entirely. Each
   search now runs in its own `launch`ed child, cancelled by the collector when a newer request
   arrives. Do not "simplify" this back to `collectLatest`.
2. **The mandatory latest-query-wins test was passing vacuously.** The original fake honoured
   cancellation, so a superseded search never returned and the test was measuring `flatMapLatest`,
   not the staleness guard — proven by deleting the generation check and watching that test stay
   green. `UncancellableSearchSource` (a `withContext(NonCancellable)` fake) is the only fake that
   reproduces the hazard. **Modelling only the safe version of a hazard proves nothing**, the same
   lesson as the Dutch header fixture and the soft-keyboard geometry test.
3. **A blocking `CountDownLatch` in an instrumented test deadlocks rather than fails.** The
   ViewModel's searches run on `Dispatchers.Main`, which is the thread Compose's test
   synchronization drives; the run hung for 10 minutes at 12/18. `CompletableDeferred` suspends
   instead and the test passes in 35 s.

**Cancellation is not the guarantee.** `request.generation != requestGeneration` at the single point
where a result becomes state is what stops an old response landing, and it holds whether or not the
transport honoured the cancellation. Cancellation is the optimisation; the generation check is the
invariant.

**Deliberate behaviour changes, both about flicker:** editing keeps the previous results on screen
under a hairline `LinearProgressIndicator` until the newer ones replace them in one state write
(they are dropped at once when the query is cleared or falls below `MIN_QUERY_LENGTH`, where nothing
is coming); and `queryTooShort` is now set **only** by an explicit `search()`, never by typing — as
a live region it had announced on every keystroke. The progress line carries
`clearAndSetSemantics {}` for the same reason.

**Verified:** JVM **827/827** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit
XML — up from 791; 45 in `SearchViewModelTest`). `SearchScreenTest` **18/18 on five consecutive
runs**. `HomeScreenTest` 16/16, `ProductScreenTest` 32/32, `MealScreenTest` 19/19. Lint exit 0,
41 advisories, 0 errors — unchanged from the pre-pass baseline. Debug APK builds (89.4 MB).

**Negative controls, all three re-run against the final implementation:** removing the debounce
fails 6 tests (incl. the one-request-per-word count); removing the generation check fails 4 (incl.
the mandatory A→B→A-completes-late case); removing the explicit/automatic dedupe guard fails 2.

**Not done in this pass, deliberately:** no release or AAB build, no R8 barrier re-check, no OCR
corpus run (no OCR or scanner file was touched), and no full 218-test instrumented sweep — the four
UI classes above cover the changed surface, and the whole suite runs at the release gate. Nothing
here has been seen on physical hardware.

## Search-a-licious is the primary search provider (2026-08-28) — still 1.0.2 / versionCode 3

Text search runs against **`https://search.openfoodfacts.org/search`**, with the legacy
`cgi/search.pl` retained as a governed fallback. Still `versionCode 3`, **not bumped** — 1.0.2 was
already open. Nothing about the calculation, the schema, migrations, the §10 lookup priority,
barcode detection, OCR, or the 30 s product-refresh window changed.

### The feasibility gate was measured before anything was wired, and it is why this happened

| | legacy `cgi/search.pl` | Search-a-licious |
|---|---|---|
| 7 representative queries, 7 s spacing | **503 on 5 of 7** | 200 on 7 of 7 |
| 12 back-to-back requests | not attempted (budget) | 12× 200, 136–202 ms, no throttling |
| auth | none | none |

Re-verified **end to end on the emulator through the production wiring**: 7/7 queries, 20 hits each,
78–106 ms after the first (the first carries TLS setup). Bench:
`SearchALiciousLiveDiagnosticTest` — it prints and asserts almost nothing on purpose, because a
network test that fails the build on a flaky connection is a test people learn to ignore.

### Three schema facts that had to be measured, not assumed

1. **`product_quantity_unit` is not in the index** — 0 of 140 hits across seven queries, and asking
   for it by name returns *nothing* rather than an error. It is `PackageBasisResolver`'s primary
   evidence, so on this path the basis comes from free-text `quantity` alone and resolves less often
   (`pasta`: 3/20 vs legacy 18/20; overall 51/140).
   **No resolver rule was weakened to compensate, and none may be.** A hit with no basis shows no
   number — the existing §13 rule — and still carries name, brand, package text and photo. This is a
   *display* regression, never a nutrition one: the figure the user doses from comes from the
   canonical barcode lookup after they tap, which is unchanged.
2. **`brands` is a JSON array here and a comma-joined string on the legacy path** (137 of 140).
   `FirstOfStringOrArray` reads either. Scoped to that one field for the same reason
   `LooseNumericText` is — the carbohydrate values keep strict typing.
3. **`langs=nl,en` is load-bearing.** Without it `product_name_nl` is absent from *every* hit and
   Dutch recall collapses: `hagelslag` returns 449 matches with it and 26 without. Input
   recognition, not localization — the UI stays English (owner decision 10).

### The boundary, and why the migration is reversible

`FallbackProductSearch` is itself a `ProductSearchSource`, so no ViewModel and no screen knows there
are two providers. Pointing `AppContainer.searchSource` at `legacySearchSource` alone restores the
previous behaviour exactly, with no other edit.

**Fallback-eligible:** `OFFLINE`, `TIMEOUT`, `SERVER`, `MALFORMED` — the failures where a *different
host* might plausibly answer. **Not eligible:** `RATE_LIMITED` (answering "you ask too often" by
asking elsewhere is the behaviour the limit exists to stop) and — the rule the design rests on — a
legitimate `NoMatches`, which is an **answer**. Falling back on empty results would double the cost
of every deliberate search for something genuinely absent. When both fail, the **primary's** error
surfaces: the legacy endpoint's habitual 503 would otherwise mask a real offline state.

### Two findings that only running the tests produced

1. **A cancelled query could still spend a fallback request.** A primary whose transport ignores
   cancellation returns an ordinary `Failed`, and `fallback.search` may then run to completion
   without ever suspending — so nothing on that path would have thrown.
   `currentCoroutineContext().ensureActive()` before the fallback call is what closes it.
   `CancellationException` is caught nowhere in the chain.
2. **One integration test was vacuous and was caught by negative control.** The stale-fallback case
   passed with the ViewModel's generation guard deleted, i.e. it was measuring `flatMapLatest`, not
   staleness. It now uses a `NonCancellable` fallback — the only fake that reproduces the hazard —
   and fails without the guard. **Same trap as the Dutch header fixture and the soft-keyboard
   geometry test: modelling only the safe version of a hazard proves nothing.**

### The governor moved down to the provider it protects

It sat in `SearchViewModel`, *above* the provider boundary, so leaving it there would have made
every primary query wait out an interval sized for a different service. `GovernedProductSearch`
now wraps the legacy source only, keeps `MIN_INTERVAL_MS = 7000` and the shared cross-screen budget,
and **refuses immediately rather than waiting** — a 7 s delay behind an already-failed primary is
the stacked wait this migration must not create. The primary has its own instance at
`PRIMARY_MIN_INTERVAL_MS = 300`; `REMOTE_SEARCH_SETTLE_MS` returned 1000 → **500**.

**`RemoteSearchGovernor`'s clock parameter must stay last.** Callers construct it as
`RemoteSearchGovernor { clock }`, and adding the interval after it silently rebinds the trailing
lambda to the wrong parameter — caught by the compiler, and a real hazard for the next person.

**A pre-existing ViewModel test was measuring the wrong budget** once the primary changed. It is
**re-aimed, not relaxed**: the legacy 9/min ceiling is now asserted where it is actually enforced,
in `GovernedProductSearchTest`.

Debug-only diagnostics: `adb logcat -s JtcSearch` says which provider answered. **No query text is
ever logged** — stage, provider and result count only.

**Verified:** JVM **939/939** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit
XML — up from 827). `SearchScreenTest` **29/29 on five consecutive runs**; `HomeScreenTest` **21/21
on three**. Lint exit 0, 41 advisories, 0 errors — unchanged baseline. Debug APK builds.
**Seven negative controls**, each restored afterwards: primary success falling back (3 fail),
no-results falling back (1), cancellation not blocking fallback (1), `RATE_LIMITED` made eligible
(2), dedupe removed (1), governor bypassed (5), generation guard removed (1).

**Not done, deliberately:** no release or AAB build, no R8 barrier re-check, no OCR corpus run (no
OCR or scanner file was touched). **Nothing here has been seen on physical hardware** — and that is
the gate: `docs/manual-qa.md` §19c exists precisely because the fallback is invisible by design, so
only the debug log can say which provider answered.

## Search hardening: POST, unusable replies, Lucene input (2026-08-28) — still 1.0.2 / versionCode 3

Three fixes on top of the migration above. Still `versionCode 3`, **not bumped** — 1.0.2 was already
open. Nothing about the calculation, schema, migrations, the §10 lookup priority, barcode detection,
OCR or the 30 s refresh window changed; the diff is `data/remote/SearchALicious*`, one interface
method on `SearchProviderLog`, and tests.

### `GET` → `POST`, and one invisible serialization trap

Both verbs exist on `/search` with **identical `q` semantics** (read from the service's own
OpenAPI document, not assumed), so this is transport only: the user's search text moves out of the
URL — the part of a request proxies and access logs retain in plain text — and into the body. In
this app a search term is a food someone is about to eat.

`langs` and `fields` are **arrays** in the POST schema where the query string took comma-joined
strings.

**The trap, and it would have shipped silently:** kotlinx.serialization omits a property equal to
its default, and the shared `NetworkModule` `Json` does not set `encodeDefaults`. Every request
would have gone out as `{"q":"…"}` alone, and the **server's** defaults would have applied —
`page_size` 10 instead of 20, `langs` `["en"]` instead of `["nl","en"]` (which is the only reason
`product_name_nl` appears at all, so Dutch recall would have collapsed), and no field filter, so
~13 KB per hit. Every request still succeeds and still returns products, so nothing surfaces.
`@EncodeDefault` on the three properties fixes it. **Do not remove those annotations, and do not
"simplify" by setting `encodeDefaults = true` on the shared `Json`** — that changes how every other
DTO serialises to fix one body. Caught only because the test asserts the request body rather than
the outcome.

### "No matches" and "nothing usable" were the same statement, and one of them suppressed the fallback

`toSearchResult` ended `if (hits.isEmpty()) NoMatches else Found(hits)`. Since
`FallbackProductSearch` deliberately does **not** fall back on `NoMatches` — a zero-result answer is
an answer — a response carrying matches whose every record failed to map reported "nothing matches",
**suppressed the legacy fallback, and told the user their product does not exist**. Both render as
an empty list, so it is invisible from the screen.

The classification now turns on whether the provider *claimed* matches, never on the mapped list
being empty — that is true in both cases and is exactly what hid the bug:

- `hits` empty **and** no positive `count` → `NoMatches` (an answer; no fallback, unchanged).
- `hits` non-empty **or** `count > 0`, nothing usable → `MALFORMED`, which **is** fallback-eligible.
- Any usable hit → `Found`, carrying only the good ones. One malformed record never discards the
  rest — missing fields are an ordinary state of a crowd-sourced database.

`count` is used only in the direction that is safe: a positive `count` escalates to a failure, but a
missing or zero `count` never *downgrades* a non-empty-but-unusable `hits` array back to an answer.

### The search box is not a query editor — and the worst case was not an empty list

`q` is parsed as **Lucene**, so ordinary punctuation in an ordinary product name became operators.
Measured live, six inputs returned **zero results** as typed and the correct products once escaped:
`Kinder Bueno (White)`, `milk + chocolate`, `product:name`, `"chocolate milk"`, `chocolate^2`,
`chocolate~2`.

**And one case worse than a zero:** `milk -chocolate` returned a full list either way — but the
leading `-` is NOT, so unescaped it *excluded* chocolate and led with "Lait De Coco Nature". A
silently wrong result set is harder to notice than an empty one, because there is nothing to notice.

`SearchALiciousQuery.escape` prefixes Lucene's reserved set. **The wider rule was chosen over a
narrower one on evidence, not caution:** whether a character acts as an operator depends on
**position**, not identity — `(` is inert inside `chocolate(milk` and an operator around
`(White)`; `-` is inert inside `Haagen-Dazs` and an operator in `milk -chocolate`. A rule escaping
only the characters seen to break in one position is one product name from being wrong. The cost was
measured: escaping the full set changed **no** query that already worked — `M&M's`, `Ben & Jerry's`,
`70% chocolate`, `Coca-Cola Zero`, `Haagen-Dazs`, `7-Up`, `Lay's`, `Uncle Ben's`, `Côte d'Or`,
`Dr. Oetker`, `Milka Oreo`, `hagelslag` all returned identical counts **and identical top hits**.

Apostrophes, `%`, `.`, `,`, spaces and all non-ASCII are untouched — none is a metacharacter and all
are everywhere in real names. **Scoped to this provider only:** the legacy `cgi/search.pl` takes
plain text with no query language, so the same escaping there would send literal backslashes into a
search matching nothing — this bug inverted. Pinned by a test asserting the fallback receives the
text verbatim.

### Verified

JVM **965/965** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up from
939). `SearchScreenTest` **29/29 on five consecutive runs**; `HomeScreenTest` **21/21 on three**,
counted from instrumentation status codes, 0 ignored. Lint exit 0, 41 advisories, 0 errors —
unchanged baseline. Debug APK builds (89.6 MB). Live on-device POST bench: 7/7 queries, 20 hits
each, 80–110 ms after the first.

**Nine negative controls**, each restored byte-for-byte and hash-verified, none vacuous: GET restored
/ query in URL (3 fail), unusable-hits→`NoMatches` (4), `MALFORMED` made ineligible (2), `NoMatches`
made eligible (3), `ensureActive` removed (1), escaping removed (15), `@EncodeDefault` removed (1),
generation guard removed (7).

**A harness trap that wasted a run:** the instrumentation runner is
`app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner` — note the **`.test`**. Using
the app's own package gives `Unable to find instrumentation info`, which the status-code parser reads
as 0 passed / 1 failed and looks exactly like a real suite failure. Confirm with
`adb shell pm list instrumentation`.

**Not done, deliberately:** no release or AAB build, no R8 barrier re-check, no OCR corpus run (no
OCR or scanner file was touched). **Nothing here has been seen on physical hardware** —
`docs/manual-qa.md` **§19d** is the gate, and its punctuation rows are the specific inputs that were
measured broken.

## Search accuracy + efficiency (2026-08-28) — still 1.0.2 / versionCode 3

An accuracy-and-efficiency pass over the search stack. Still `versionCode 3`, **not bumped** — 1.0.2
was already open. Nothing about the calculation, schema, migrations, the §10 lookup priority,
barcode detection, OCR, the 30 s product-refresh window, the escaping, the POST transport or the
fallback rules changed. The diff is one new `domain/` class, one field dropped from the request, and
tests.

### Phrase boosting does not exist on this deployment — do not implement it

The brief asked for a `boost_phrase` A/B. **There is nothing to A/B**, and the measurements are
worth keeping because the failure mode is the misleading one:

- **`boost_phrase` is not a parameter here.** The service's OpenAPI document contains **zero**
  occurrences of "boost" or "phrase". Sending it anyway returns **HTTP 200** with byte-identical
  results — silently ignored. Of the three possible answers (accept / reject / ignore) this is the
  dangerous one: a naive A/B would have "enabled" it and reported no regression, which is true and
  means nothing.
- **Free-text Lucene phrase syntax does not work either.** Measured against the **raw HTTP
  endpoint with unescaped queries**: `"nutella"` → **0 hits** (a one-word phrase cannot legitimately
  fail), `(coca cola)` → 0, `coca^2 cola` → 0, `coca OR cola` → **HTTP 500**. Meanwhile
  `brands:"coca-cola"` → 3283 and the service's **own documented example** → 5 hits. So quoting is
  honoured **only** as a field-filter value, never as a free-text phrase.

That second result also independently re-confirms `SearchALiciousQuery`: `(`, `^` and `"` genuinely
destroy free-text queries here. Recorded as a re-runnable diagnostic
(`SearchALiciousLiveDiagnosticTest.phraseSyntaxSupportOnTheLiveService`) rather than only as prose.

**Do not misread that diagnostic's output.** It runs through the data source, so the escaper applies
and every phrase form comes back **Found** — the metacharacters arrive as literal text and the query
degrades to an ordinary word search, which is the escaping working. Only `explicit OR` still fails
(SERVER), because `OR` is a bare word that nothing escapes. The zeros above required bypassing the
app entirely. **A `Found` line there is not evidence that phrase syntax works**; it is evidence that
the app cannot send a phrase query at all, which is the actual conclusion.

**A measurement trap that cost two runs:** the first attempt escaped the query and *then* wrapped it
in quotes, sending `"\"coca cola\""` — a phrase whose content is a literal quote character. Every
variant returned 0 or 500 and it looked like a service result. It was measuring my own string
construction. The corrected run sends structurally-unescaped delimiters around escaped inner text,
and only *then* is the 0-hit result attributable to the service. **A negative result from a
hand-built query string is not evidence until the string itself has been printed and read.**

### Baseline relevance, and why no ranker was built

48 queries, live, `page_size=20`, over the categories the brief lists:
**Top1 34/39 · Top3 34/39 · Top10 36/39 · Top20 37/39** (39 scored; 9 generic queries scored
separately, all returned usable results).

**Top1 equals Top3, and that is the finding.** When this service finds the expected product it ranks
it *first* — there is no population of near-misses at rank 2–3 for a re-ranker to lift. The two
misses are not ranking failures either: `pindak`, `pindaka` and `nutel` all return **zero hits**
(the index does no prefix matching), and `nutt` returns 7 unrelated hits. **No client-side ranking
can fix an empty result set**, which is the evidence behind not building one.

`SearchRelevanceBenchmarkTest` (JVM, MockWebServer) pins the pipeline's half of this — that captured
responses survive deserialization, mapping, validation and barcode dedupe **with the provider's
order intact**. It deliberately does *not* re-assert the remote ranking: that would be a test whose
colour depends on someone else's server, i.e. the flaky-live-test shape the brief forbids in the
standard suite.

### The cache: a decorator on the primary, not on the chain

`CachedProductSearch` is a `ProductSearchSource` wrapping **the primary only**, inside
`FallbackProductSearch`. That placement is load-bearing twice over: a cache hit is an ordinary
primary `Found`, so the fallback is **structurally** unreachable on a hit; and a legacy answer is
never filed under the primary's name, so a cached result's provenance stays answerable. Wrapping the
whole chain would lose both properties. `AppContainer` holds one instance, shared by Home's inline
search and the search screen for the same reason they already share one governor.

20 entries, 5-minute TTL, access-ordered `LinkedHashMap` (LRU, so the query being flipped back and
forth survives). Memory only, process lifetime, no schema change, no new dependency.

**Only `Found` is stored.** Every `Failed` and `NoMatches` passes through untouched — a cached 503
would outlive the outage it described, and a cached "no matches" would tell someone a product does
not exist because it did not five minutes ago, in a database strangers edit continuously. A
cancelled search writes nothing because the delegate never returns.

**A future `storedAtMs` counts as expired, not fresh** — same rule and same reasoning as the 30 s
product-refresh window; the naive reading (`now - stored` negative, so "younger than the TTL") would
pin an entry until real time caught up.

Nothing above the decorator knows it exists: `SearchViewModel` is unmodified, so the generation
guard, the settle wait and local narrowing behave exactly as they do for a fast network answer. That
matters most for the case where a hit completes **without ever suspending** — cancellation cannot
help there, and the generation check is what stops it landing on a newer query.

### `lang` was requested and read nowhere

Dropped from `SEARCH_FIELDS` on measurement, not principle: **240 bytes per response** (12 bytes ×
20 hits, 2.3%) across `chocolate`, `pasta`, `hagelslag`, `milk`, `nutella`, with the **mapped
products identical** for all five. Do not confuse it with the request's `langs`, which is what makes
`product_name_nl` arrive and is untouched.

**The test that should have caught it could not**, and that is the transferable part: the field
assertions were `contains` checks, which are blind to a field being *added*. Proven by negative
control — restoring `lang` failed **nothing**. Now an exact-list comparison, which catches it.
**A `contains` assertion over a request's field list pins only half the contract.**

`page_size` stays at **20** on evidence: Top20 (37/39) exceeds Top10 (36/39) by exactly one query,
so a larger page enlarges every response to buy at most one position.

### Verified

JVM **1004/1004** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up
from 965; +24 cache, +7 benchmark/payload, +8 chain integration). Lint exit 0, 0 errors. Debug APK
builds.

**Thirteen negative controls**, each restored byte-for-byte and hash-verified: cache bypassed (14
fail) · expired entry reused (4) · bound removed (2) · failures cached (4) · LRU→FIFO (1) ·
normalization dropped (1) · `lang` restored (2, **after** the assertion was tightened — it caught 0
before) · `page_size` raised (2) · GET restored (2) · unusable→`NoMatches` (4) · `MALFORMED` made
ineligible (2) · escaping removed (15) · generation guard removed (7).

**Not done, deliberately:** no release or AAB build, no R8 barrier re-check, no OCR corpus run (no
OCR or scanner file was touched), and no `versionCode` change. **Nothing here has been seen on
physical hardware** — `docs/manual-qa.md` **§19e** is the gate, and its 19e.2 row (two `primary
start` lines, not three) is the only check that can distinguish a cache hit from a fast search.

## Prefix/partial-query recall: MEASURED AND NOT FEASIBLE (2026-08-28) — do not implement it

A feasibility pass on whether provider-generated prefix search could rescue the partial queries that
return nothing (`nutel`, `pindak`, `stroopw`). **It cannot. No production code was changed** — the
only edit is one added diagnostic, `SearchALiciousLiveDiagnosticTest.prefixWildcardSupportOnTheLive‑
Service`. Still 1.0.2 / `versionCode 3`, unbumped, because nothing shipped.

**The trailing `*` is silently discarded** — the same signature as `boost_phrase` before it, and the
reason both had to be measured rather than reasoned about. Over **28 partial queries**, baseline vs
final-token wildcard: **0 differed**. Identical counts, identical ranks, identical top hits, every
one. Multi-word arms (`kinder bu*` vs `kinder* bu*` vs plain) are identical too, so §6's
"wildcard only the final token" question never arises.

Two rows settle it, and **the first is a trap**:

| sent | count | top hit |
|---|---|---|
| `nutel` / `nutel*` | 0 / **0** | – |
| `nut` / `nut*` | 10000 / **10000** | Mixed Nuts (identical top 3) |
| `choc*late` | **3118** | **Late** |
| `nutella` | 631 | Nutella & go! |

- **`nut*` looks like a working wildcard and is not.** It equals bare `nut` exactly, because `nut` is
  simply a real word. Read alone that row would have "confirmed" wildcard support — the same
  mistake shape as reading a `Found` line in the phrase diagnostic as evidence phrase syntax works.
- **`choc*late` returns *Late*.** The `*` is a **token separator**, splitting the query into
  `choc` + `late` — the opposite of a prefix expansion. A working wildcard could not do that.

**Not just one syntax:** `~`, `~1`, `~2` are 0 on every canary, and `product_name:…` returns
**HTTP 500** (that field is language-subfielded — `product_name_nl` etc. — so it is not directly
queryable). The index holds analyzed whole tokens with no edge-ngram expansion: `pindakaa` → 60 hits,
`pindaka` → 0. That is a token boundary, not a ranking cliff.

**Why nothing was built.** §13's decision rule is never reached — there is no candidate to weigh,
because the candidate has no measurable effect. Implementing the NoMatches→prefix retry would add a
second request to every zero-result query, guaranteed to return the same zero. And **no client-side
ranking can reorder an empty result set**, which is why the same conclusion also blocks a fuzzy
matcher or a custom ranker here. The `/autocomplete` endpoint does not help either: it requires
`taxonomy_names` and searches taxonomies (categories/brands), not product names.

Reproduced **on device** through the app's own networking stack, matching the raw-HTTP numbers. The
diagnostic deliberately bypasses `SearchALiciousDataSource` — the escaper escapes `*`, so a wildcard
can only be built by appending it *after* escaping, and going through the data source would measure
the escaper instead of the service.

## Version and track state (2026-08-28) — THE AUTHORITATIVE ANSWER, READ BEFORE ANY RELEASE CLAIM

Everything else in this file and in `docs/` is subordinate to this section. Where an older passage
disagrees, this one is right — and fix the older passage rather than working around it.

| Question | Answer |
|---|---|
| What is the latest release? | `1.0.1` / **`versionCode 2`**, uploaded and **accepted by Play 2026-08-28** |
| Which track? | **Closed testing.** `versionCode 1` preceded it on internal → closed |
| Closed-testing period | **Running.** 12+ testers opted in |
| What is in development? | **`1.0.2` / `versionCode 3`** — opened 2026-08-28 by the live-search pass, extended the same day by the Search-a-licious provider migration, the search-hardening pass and the accuracy/efficiency pass. Nothing built or uploaded against it |
| Is 1.0.1 released? | **Yes.** Built from `45f3dd9`, uploaded 2026-08-28, in `docs/version-history.md` |
| What do I develop against? | **`versionCode 3`**, already set in `branding.gradle.kts`. Do **not** bump again during 1.0.2 work |
| Production | Not submitted. Gated by the Play forms + the §44 signature — see below |

**VERSIONING RULE CHANGED 2026-08-28 (owner): a new version number per code change.** The old
policy — accumulate safe fixes into one open version until the owner decides to push — produced
1.0.0 and 1.0.1 and **no longer applies**. From now on the first code change after a release bumps
`brandVersionCode`/`brandVersionName` in `branding.gradle.kts` and opens a new `CHANGELOG.md`
section. Documentation-only changes open nothing: a version number identifies an artifact, and prose
that changes no code produces none. Full rule at the top of `CHANGELOG.md`.

**`versionCode 1` and `2` are both spent.** Neither is to be rebuilt or re-uploaded — Play refuses a
duplicate code, and both are on an active track. The next number is **3**.

The current artifact is `app-release.aab` from `clean` on **`45f3dd9`** (evidence in `cb2d549`):
35,626,125 bytes, SHA-256 `8c4e6da7998b81a38fbb23234b008a8088ab57149d0d0f6a8b3e146c4d7bfd30`,
signed with the real upload key `1E:21:23:F3:…:C4:F5` — **the same key as `versionCode 1`**, which
is what lets Play accept it as an update. The signer DN was read from the built bundle with
`keytool -printcert -jarfile` before upload, not inferred from a green build: the Gradle guard
cannot tell a real upload key from a disposable one.

`versionCode 1` (`1.0.0`, `68c85a3`, SHA-256 `37be0232…c7e604b`) reached the closed track by
**promotion of the same bundle** — same bytes, same hash, same version code. `docs/version-history.md`
records it **once**, with the track progression noted; a promotion is not a release and does not get
a second entry. Play still shows the temporary name `app.justthecarbs (unreviewed)`; that is expected
pre-review and is not a defect.

### Working rules after 1.0.1

- **`1.0.2` / `versionCode 3` is OPEN and already set** in `branding.gradle.kts` (2026-08-28, the
  live-search pass). Further 1.0.2 work adds entries to that `CHANGELOG.md` section and **does not
  bump the version again** — one number per code change means the number is opened once and then
  identifies the artifact that eventually ships. The next bump is `1.0.3` / `versionCode 4`, after
  1.0.2 has shipped.
- **A documentation-only change opens nothing.** No version, no bump, no `CHANGELOG.md` heading.
- **Do not rebuild or upload `versionCode 1` or `2`.** Both are on an active track and Play refuses
  a duplicate code. Superseded artifacts stay superseded — in particular the earlier bundle
  `00876FA9…BBB4A2`, built from an uncommitted tree.
- **Nothing goes into `docs/version-history.md` until Play accepts a build.** That file is the
  append-only record of artifacts that actually shipped. A built-but-unuploaded version is not
  history.
- The test figures and the Play *What's new* text in an unreleased section describe the work **so
  far** and must be re-checked and rewritten before the build is made.
- **A release build is a deliberate, instructed act.** Building or uploading an AAB is never part of
  an ordinary development pass. When one is asked for, follow `docs/play-release-readiness.md`
  §2c/§2d — build from a committed tree, verify the R8 privacy barriers and **read the signer DN off
  the artifact**, then copy the section into `docs/version-history.md` with the hash once Play
  accepts it.

**Historical note.** Earlier revisions of this file and of `docs/play-release-readiness.md` said
"DO NOT REBUILD … any replacement needs `versionCode 2`", then later that `versionCode 2` "exists
and is the development target". Both are stale: **2 shipped on 2026-08-28**. What survives from them
is only the general rule — a version code that has reached a track is never rebuilt or re-uploaded,
which now covers 1 and 2 alike.

**Next technical action:** install the Play-delivered **1.0.1** build on the Samsung device via the
**tester link** — not a local APK — and run the ten-step smoke test
(`docs/play-release-readiness.md` §8a). This is now more valuable than it was for 1.0.0: nothing in
1.0.1 was verified on physical hardware, and the crop-drag fix is the change a tester is most likely
to notice.

**The 14-day clock is RUNNING.** If this account is subject to Play's **12-testers / 14-days
closed-testing requirement** (some personal accounts created from Nov 2023 onward are; organization
accounts are not), the closed track is now satisfying it in progress: 12+ testers opted in, period
elapsing. **Internal testing never counted toward it** — a separate track, no credit — which is
exactly why the closed track was needed; it now exists, so nothing remains to create or enrol. What
is left is elapsed time and keeping testers opted in. Console's Production track is the authority on
days remaining. Still the longest pole, and still the one item outside this repo's evidence.

Production gates (full detail §1b and the §5a Console matrix): complete the app-content forms
(Data Safety, content rating, target audience, app access, ads); sign the §44 assessment; read the
final listing against §44 §7.1; set countries to EU only.

**Health Apps declaration is an OWNER DECISION — do not answer it in documentation.** The app
calculates carbohydrate amounts for portions and meals, which may fall close to Google's **Nutrition
and Weight Management** category. An earlier revision of these docs recommended "My app doesn't
provide any health features"; that recommendation is **withdrawn** — it was not this document's call
to make. `docs/play-release-readiness.md` §4a now states the facts on both sides without choosing.
**Never equate "not a medical device" (§44/MDR) with "not a Google Play health app" (Play policy)**;
they are separate classifications by separate authorities, and the Organization-account requirement
attaches to the Play one only.

**Two distinctions that were being conflated and must stay separate:**

- **§44 medical-device qualification ≠ Google Play health-app classification.** Different authorities,
  different questions; neither answer follows from the other. The Organization-account requirement
  attaches to the *Play* classification, never to §44.
- **Upload key ≠ app-signing key.** The developer holds the upload key; Google generates and holds
  the separate app-signing key under Play App Signing. The upload key never becomes the app-signing
  key, and after enrollment a lost upload key is recoverable via Google's upload-key reset — so the
  untested keystore backup is a strong recommendation, not a release blocker.

Demoted from blocker to optional, with reasons in `docs/play-release-readiness.md` §1c: independent
regulatory review (the MDR makes the manufacturer the responsible party, so a self-assessment is the
expected record), non-EU market assessment (moot while EU-only), restore-tested keystore backup, the
20-item device sweep (replaced by a 10-minute smoke test, §8a), git-history remediation, and the
project licence.

## Release-closure pass (2026-08-25, later same day) — READ FIRST

Verification pass over the production-hardening section above. Nothing about the calculation, schema,
migrations, the §10 lookup priority, barcode detection or any parser rule changed. Not committed.

### The release AAB is signed with a key that says NOT FOR PLAY — RESOLVED 2026-08-26, see above

*(Historical. The keystore was replaced with a real upload key on 2026-08-26. The reasoning below is
still why the DN must be read on every release build, so it is kept rather than deleted.)*

`assembleRelease` and `bundleRelease` both succeed, and the artifact they produce is signed
`CN=DISPOSABLE TEST KEY, OU=NOT FOR PLAY, O=JustTheCarbs Test, C=NL`. The Gradle guard
(`gradle.taskGraph.whenReady`) refuses to package a release without all four secrets, which is
correct and worth keeping — but four valid properties pointing at a real keystore is exactly what a
test key also looks like, so **the guard cannot tell an upload key from a disposable one and a green
`bundleRelease` is not evidence that an uploadable artifact exists.** Read the DN:

```powershell
& 'C:\atools\sdk\build-tools\36.0.0\apksigner.bat' verify --print-certs `
  'app\build\outputs\apk\release\app-release.apk'
```

Recorded as a gate in `docs/play-release-readiness.md` §2. Generating the real upload key is an owner
action; do not "fix" this in code.

### Debug-only evidence work was still on the user-visible path, in two places

The production-hardening pass moved the *heavy* writes off the critical path and that part holds
(`consumeCapture` moves rather than copies; `recordPassAImageAsync` re-decodes from the moved file on
a background thread, so it cannot race the crop screen's recycle). Two smaller sites survived:

1. **`recordSelection` on the "Read table" tap.** It wrote every retained and rejected element with
   geometry *between* `SelectedTableResolution.resolve` returning and the outcome reaching the screen
   — i.e. on the critical path of the second half of the very scan these bundles exist to time. Now
   written after the outcome is applied. Safe because `releaseCapture` recycles only the bitmap; the
   evidence folder handle and the document outlive it.
2. **`recordMeta` ran before the handover**, so the diagnostics that exist to measure the scan were
   part of what they measured. Moved after it.

### `ScanTrace` now separates on-path from off-path, and that distinction is load-bearing

**Only a debug build records evidence, so every device latency measurement is taken on a build
carrying work a user will never pay for.** A single total therefore overstates the shipped experience
with no way to subtract the difference — which is how "3.55 s" gets quoted about a release build that
never wrote a PNG.

`markOffPath`/`timeOffPath` flag debug-only and post-handover stages; `userVisibleMs()` is the total
without them; `summary()` prints both and marks off-path stages with a trailing `*`:

```
scan 1180ms (user-visible 640ms) | evidence-text 280* · jpeg-decode 210 · mlkit 190 · … · handoff 3
```

**The ≤2 s acceptance target is about `user-visible`, never `scan`.** Also added the missing `handoff`
mark. `evidence-capture` was previously marked *after* `summary()` had already been taken, so it
appeared in no log at all — dead instrumentation, now live. In release the whole trace folds away:
its only consumers are `OcrDiagnosticsLogger.timing` and `recordMeta`, both of which R8 removes.

The device stage map and the 20-item release sweep are `docs/release-closure-device-verification.md`.

### Two comments that were wrong about the artifact

- `app/build.gradle.kts` claimed excluding `camera-video` keeps `ACCESS_NETWORK_STATE` out of the
  manifest. **It does not — that permission ships.** The release manifest-merger report attributes it
  to `com.google.android.datatransport:transport-backend-cct`, which arrives via `com.google.mlkit:common`
  and cannot be excluded. It is correctly disclosed in `docs/privacy-policy.md`,
  `docs/google-play-data-safety.md` and `docs/security-review.md`; only the build comment was wrong.
  The exclusion is still worth keeping — it drops media3 and the muxer.
- The same file said the `ocr_real` assets are git-ignored and the test skips itself when they are
  absent. Both halves have been false since 2026-08-16.

### Verified this pass

JVM **726/726** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — a plain
`clean test` restores from the Gradle build cache and proves nothing). Instrumented **214/214**
(0 failures, 0 errors, 0 skipped) in **one complete run** after an AVD reboot, including
`RealImageOcrTest` 15, `ProductionStillPipelineTest` 8, `SelectedTableProductionTest` 6,
`EvidencePipelineProductionTest` 8, `JustTheCarbsDatabaseMigrationTest` 10 and `ProductScreenTest`
32/32. There is no `@Ignore` and no `assumeTrue` anywhere in either test source set, so "0 skipped"
cannot be a silent skip. Lint exit 0. Debug APK (90 MB), minified release APK (67 MB) and release AAB
(35 MB) all built from `clean`.

Release R8 re-checked on this build: `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` map to
`R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport` and `OcrDiagnosticsReport` have no mapping entry at all;
`UnitMarkerFilter`, `CandidateProvenance` and `CarbCandidate` are retained as real classes; the
evidence `FileProvider` is absent from the release manifest and the only providers in it are ML Kit's
init provider and `androidx.startup`. `TextResolutionGuidance` reads `R8$$REMOVED$$CLASS$$` — that is
the **inlined-not-dropped** case this file already warns about, not a stripped feature.

Dependency scan re-run 2026-08-25 (`tools/dependency-scan.sh`, OSV.dev querybatch): **226 resolved
release artifacts, 0 known vulnerabilities**, control query passing.

The privacy policy is **live** at the URL in `branding.gradle.kts`
(`https://morfildor.github.io/Just-the-Carbs/privacy-policy.html`, last updated 15 August 2026),
and `SettingsScreen` opens that same `BuildConfig.PRIVACY_POLICY_URL`, pinned by `SettingsScreenTest`.
Recording that as evidence against checklist row C7 is still the owner's.

### ~~Not a defect, so do not "fix" it~~ — SUPERSEDED 2026-08-28, it was reachable

*(Kept because the reasoning is instructive: it was careful, and still wrong.)*

The original note said `onProductLoaded` launching a coroutine outside `lookupJob` was harmless,
because the route is `product/{barcode}`, the ViewModel is scoped to the `NavBackStackEntry`, and
`LaunchedEffect(barcode)` fires once — so one ViewModel only ever serves one barcode.

All of that is true and **it does not close the hole**, because it reasons about how many *distinct
barcodes* reach one ViewModel and the guard's failure is about how many *calls* reach it for the
same one. `load` returns early on `lookupJob?.isActive == true` or `product != null`; between the
fetch completing and `onProductLoaded`'s detached coroutine writing the product, **neither holds** —
the job is finished and the product is still null. Any second `load` in that window re-fetches.
`LaunchedEffect(barcode)` re-running after a configuration change is enough to reach it.

Measured, not argued: `a second load after the fetch resolves but before the product lands costs no
extra fetch` fails on the old code with `[barcode, barcode]` and passes now. `onProductLoaded` is a
`suspend fun` awaited inside `lookupJob`, so the job spans the whole load and both guards cover the
delivery phase too. A failed lookup still leaves no product, so *Try again* still re-fetches —
pinned by `retrying after a failed lookup fetches again`.

**The general lesson:** a reachability argument about the *navigation graph* cannot establish a
claim about *call timing*. Only the timing test settled it.

## Multi-source evidence scanner (2026-08-18) — READ FIRST, SUPERSEDES THE CROP SECTIONS BELOW

The nutrition scanner no longer has *one* recognition whose result becomes the answer. It gathers up
to four opinions and resolves them conservatively. **Not committed.** Full measurements:
`docs/plans/2026-08-18-scanner-reliability-measurements.md`; checkpoint report:
`docs/plans/2026-08-18-scanner-reliability-checkpoint.md`.

Barcode is untouched and proven so: all four barcode files hash-identical to HEAD, 43 tests green.

### The architecture

```
capture ─┬→ Pass A (whole frame, ML Kit)          ─┐
         └→ user rectangle ─┬→ Strategy A (filter Pass A's elements, re-parse; no OCR)
                            └→ Strategy B (fresh ML Kit over the NATIVE-RESOLUTION crop)
   pre-shutter live stable consensus              ─┘
                                    ↓
                            EvidenceResolver
             Resolved | NeedsVerification | Conflicted | Nothing→assisted
```

### Three findings that must not be re-derived

1. **Re-recognition damage does not require rescaling.** The `(g)`→`(9)` failure was blamed on
   rescaling changing tokenisation. Measured with **no resize at all**: a native-resolution crop at
   the production overlay turned kinder, yoghurt and stokbrood from `Confident` to `NotFound` and
   invented a new wrong value on grated cheese (`2.09`→`2.04`). Losing basis headers and prose
   declaration spans is sufficient on its own.

2. **No crop tightness is safe** (swept 0.00/0.05/0.10/0.15 × 6 fixtures). kinder dies at 0.05,
   sondey and yoghurt at 0.10, and **stokbrood is non-monotonic** — readable at 0.00, lost at 0.05,
   readable again at 0.10. Do not tune this constant. But re-recognition genuinely **recovers**
   labels the full frame cannot read: witte kaas `NotFound`→`2.3` (correct) and grated cheese's
   known-wrong `2.09`→`2` (the printed value). Hence: evidence, never an oracle.

3. **Consensus must be counted over recognition RUNS, not evidence sources.** `FULL_FRAME_PASS_A` and
   `FILTERED_PASS_A` are two *parses of one recognition*, so their agreeing proves nothing. The first
   wired implementation counted sources and therefore **resolved grated cheese confidently to the
   known-wrong `2.09`** *and* skipped the independent recognition that would have contradicted it.
   `EvidenceSource.recognitionRun` fixes this structurally. Pinned by
   `EvidenceResolverTest.the two pass A views cannot corroborate each other`.

### The dead end is gone (§17–§19)

A failed automatic read keeps the **frozen photograph on screen** and offers: tap the carbohydrate
row (candidates restricted to that row, so sugars is unreachable), tap the number, or type it in.
The basis is always asked, never assumed. This weakens no safety rule — every parser refusal exists
because the app could not tell *which nutrient a number belongs to*, and the tap supplies exactly
that from a human reading the package.

### `2.09` is no longer "unrecoverable" — but do not add a repair rule

The note further down this file saying grated cheese's `2.09` is unrecoverable at the parser remains
correct **about parser repair rules**. It is recoverable by re-recognising *different pixels*, which
invents nothing. The pipeline now refuses it as a conflict rather than resolving it.

### ML Kit confidence is populated and is now retained

Measured: **zero NaN** across all nine fixtures, element and symbol level. Diagnostic — the mangled
Kinder token `Uokohidiat/0gjikovi` scores 0.452 while clean numerics score 0.88+. Retained on
`OcrElement` as nullable fields with defaults (no existing fixture changed). Used only to *withhold*
a proposal, **never** to decide which nutrient a number is.

### §12 bake-off: the two ML Kit artifacts CANNOT coexist

`com.google.mlkit:text-recognition` and `com.google.android.gms:play-services-mlkit-text-recognition`
both define `com.google.mlkit.vision.text.latin.TextRecognizerOptions` — interchangeable
implementations of one API, duplicate-class if both are added. Use the sequential dependency swap
documented in `OcrEngineBakeOffTest`. Bundled scored 4/9; the Play Services model reported
`UNAVAILABLE` on every fixture because the `carbscan` AVD has no Play Services, so it is
**unevaluated**, not worse. Adopting it would break the scanner on any device without Play Services.

## User-confirmed table crop (2026-08-17) — superseded above, kept for its rejected experiments

The nutrition scanner is now **Capture → freeze → confirm the table rectangle → Read table → result**.
Nothing about the calculation, schema, migrations, the §10 lookup priority or **barcode scanning**
changed. Not committed.

### Why the architecture changed

A physical device kept returning *Couldn't confidently find carbohydrates* on a Kinder table that was
large, sharp, well lit and square-on, with the `per 100 g` header and the `53,5` both plainly visible.
Three automatic localisation attempts had already been measured and rejected (vertical banding dropped
the basis header; connected-component clustering had no cross-fixture threshold; re-recognising an
isolated crop **manufactured a confident-wrong** by re-tokenising `(g)` as `(9)`). The dominant
remaining cause is surrounding package text merging into table rows during reconstruction — something a
person separates in a second and no algorithm in this repo has managed.

### Strategy A: filter Pass A's elements, never recognise twice

```
capture -> ML Kit ONCE -> raw elements + geometry -> user rectangle
        -> ElementRegionFilter -> unchanged parser
```

`LabelAnalyzer.analyzeStillRetaining` keeps the recognised `OcrDocument` and the decoded bitmap in a
`PassAResult`; `SelectedTableReader` re-filters and re-parses **in memory**. There is deliberately no
second recognition pass: reusing Pass A's elements makes the `(g)` → `(9)` class of failure
*structurally unreachable*, because no character can differ between the whole-frame read and the
cropped one. Recognition starts the moment the shutter fires, so it overlaps the user's crop gesture
and "Read table" costs only a re-parse.

`ElementRegionFilter` keeps an element when ≥50% of **its own area** is inside the rectangle
(normalising by element area makes the rule independent of ML Kit's tokenisation). It returns **null**
rather than an empty document when the selection retains nothing — "your crop enclosed no text" is a
different statement from "this label has no carbohydrate row", and `SelectedTableReader` keeps the
whole-frame reading in that case.

### The automatic initial proposal was BUILT, MEASURED and REMOVED

Do not rebuild it without reading this. An `InitialCropProposal` that located the table from nutrient
terms, clustered the label column and padded outward **damaged two of four canaries, in two different
ways**:

- **kinder** — proposed `[0.115,0.346,0.473,0.475]`, 13% of the frame. The winning candidate's own box
  spans x=137..896 and the crop ended at x=426, so the answer was physically cut off. `Confident 53.5`
  → `NotFound`.
- **yoghurt** — proposed `[0.000,0.094,0.929,0.923]`, **77% of the frame**, and the winning candidate's
  box was fully **inside** it. Still `NotFound`, because the 21 removed elements included the basis
  header. **Size and position said nothing at all.**

Four successive fixes (largest-cluster selection, a minimum-area guard, explicit header inclusion, a
wider column-gap threshold) each moved the failure rather than removing it. That is the architectural
signal, not a tuning opportunity: *deciding where a table ends* is the same problem three earlier
localisation attempts failed at. The starting rectangle is now the **scan guide the user was already
aiming with**, expanded by `ScanRegionMapper.SAFETY_MARGIN` — better precisely because it is not a
guess about the table.

A parser-verified gate (keep the narrowing only if it still reads) was also tried. It works, and it is
still not enough: it can only preserve a reading the whole frame already had, so it cannot help the
labels the crop exists for. Removed with the proposal.

### Safety is unchanged, and that is asserted rather than assumed

The rectangle asserts *"the nutrition table is in here"*, **never** *"a number in here is the
carbohydrate value"*. `SelectedTableSafetyTest` (9 JVM cases) runs selections that **include** each
hazard and asserts the parser still refuses it: `(9)` cannot win, sugars/saturated-fat/protein cannot
supply the total, a merged total+child row is still refused, and — the most important case — **a
selection excluding the basis header does not manufacture a basis**. A legitimate single-digit
carbohydrate value still reads, so the guard is positional and not magnitude-based.

### Measured

- **Real corpus through the shipped starting crop** (`SelectedTableProductionTest`, real photographs,
  real ML Kit): sondey 61.9, kinder 53.5, yoghurt 5.0, stokbrood 46.0 — **no canary regression, zero
  confident-wrong**, and filtering never increases the element count.
- **Synthetic interference sweep** (`SelectedTableInterferenceTest`, report separately from real
  fixtures): a table with an adjacent prose panel that the full frame **cannot** read is read
  correctly once filtered, on all four sides and on two structurally different tables, with provenance
  asserted against `RowClassifier` rather than a literal.
- JVM **653/653** (0 skipped, `--rerun-tasks`, counted from JUnit XML). Instrumented **189/189**
  (0 skipped) — a **complete single-run suite**, not per-class aggregation; the AVD was rebooted
  first, which is what the 2.5 GB emulator needs after the 8 MP still work. Lint exit 0.
- Confirming a crop costs a **re-parse only**, pinned by `SelectedTableLatencyTest` against a real
  recognition on the same image — the guard against someone reintroducing OCR behind the crop.
- Release R8: `ScanEvidenceRecorder`/`ScanEvidenceExport`/`OcrDiagnosticsReport` all absent from
  `mapping.txt`; the crop strings and classes are present in the release APK, so the feature ships
  while the evidence writer does not. Note `ElementRegionFilter`, `SelectedTableReader` and
  `CropSelectionGeometry` show as `R8$$REMOVED$$CLASS$$` — they are **inlined, not dropped**;
  verified by finding the crop strings and behaviour in the release APK itself. Grep the
  class-definition line (`^app\.justthecarbs\.ocr\.X ->`), never a bare substring: a line-number
  mapping entry mentions a stripped class and reads as a false positive.
- **Barcode freeze verified**: `ScannerScreen.kt`, `BarcodeAnalyzer.kt`, `BarcodeStabilityTracker.kt`
  and `BarcodeFrameReader.kt` all hash-identical to their pre-pass values and git-clean; no crop
  type is referenced anywhere in the barcode flow.

### Not verified

**Nothing in this pass has been seen on a physical device.** The crop gesture, the frozen-photo
layout, the coordinate mapping against a real 8 MP capture and whether the crop actually rescues the
Kinder and Stroopwafel failures are all open. `CropSelectionGeometry` is pinned by 12 JVM cases
(letterboxing, orientation, EXIF-upright dimensions, degenerate and inverted selections, round-trip)
because a visually correct box mapping to the wrong bitmap coordinates would reproduce the original
crop bug invisibly — but that is arithmetic, not the device.

## Autonomous scanner reliability pass (2026-08-17) — READ FIRST

Supersedes nothing below; it corrects two things and adds one guard. Nothing was committed.

### The Pass B two-pass isolation experiment was REVERTED. Do not rebuild it as it was.

A previous attempt made a second recognition of a geometrically isolated table the primary result
source (Pass A locates, Pass B answers). **It produced a confident-wrong and was reverted.**
Measured on the Kinder canary at table-to-frame ratio 0.80: `Confident 9.0` where the package prints
`53,5`. Two independent defects, both recorded so they are not rediscovered:

1. **Re-recognising a crop can manufacture a wrong value.** Rescaling changes ML Kit's tokenisation.
   The printed unit marker `(g)` came back as `(9)` — a well-formed single-digit carbohydrate value,
   on the correct total-carbohydrate row, introduced by the correct nutrient term. Every existing
   guard passed it. An independent recognition pass is **a new opportunity to be wrong**, not merely
   a cleaner look at the same pixels. This is why any multi-scale work must be an evidence ensemble
   that can only corroborate, never a replacement result.
2. **A vertical band cannot isolate horizontally adjacent panels, and it dropped the header.**
   Isolation chose `[98,419,900,914]` and discarded **12 of 24 rows including the basis header band**,
   giving `rejected: 53.5: no column` — precisely the defect that removing the overlay crop had fixed.
   `MAX_HEADER_GAP_IN_PITCHES` is not the fix; a multilingual header spans many reconstructed rows.
   Worse, the Kinder fixture contains a **second package's ingredient panel horizontally adjacent**, so
   reconstructed rows already span both panels before any locator sees them. Any future locator must
   work on **raw elements before `LogicalRowBuilder`** and cluster in two dimensions.

`NutritionTableLocator` and its 11 JVM tests are **retained but unwired**, with the failure recorded
in its own KDoc. `RawElementClusteringDiagnosticTest` is the measurement harness for the next attempt.

### `UnitMarkerFilter` — new, and the reason the above is no longer a live hazard

A number occupying a **unit-marker position** can no longer become a carbohydrate value. The rule is
positional and structural: **a bracketed numeric token that is the first number after a nutrient name
on the row is the unit annotation, not the value.** It only ever removes candidates.

**A cross-row version of this rule was tried and is WRONG — do not reinstate it.** Treating "shares an
x position with unit markers on two or more other rows" as a marker column regressed the **sondey
canary** to `NotFound`, because real labels overwhelmingly print the unit to the **right** of the value
(`61,9` `g`). Those trailing `g` elements cluster beautifully — right next to the value column — so the
rule identified the value column and deleted the answer. Unit repetition says nothing about which side
of the value the unit sits on. Reading order does.

Bracketing is **required** and is a deliberate limit: an unbracketed leading number on a nutrient row
is genuinely ambiguous (it may be the value on a table whose columns did not resolve), so excluding it
would cost correct readings. `UnitMarkerFilterTest` pins this, and carries a **verified negative
control**: with the filter disabled its main fixture yields `Confident 9.0`, so the test cannot pass
vacuously. Note the geometry in that fixture is load-bearing — the resolved basis column must sit over
the *marker* column, which is the only arrangement in which `(9)` is placeable at all.

### `TextResolutionGuidance` — advisory framing signal, calibrated not guessed

Median recognized text height as a fraction of frame height, surfaced as *Move closer* on the label
scanner. **It never gates the shutter and never touches a value.** It reaches the UI on a separate
`onFraming` callback from `onReading`, so camera advice and a value-bearing reading cannot be confused.

Calibrated by `TextResolutionCalibrationTest` against the real corpus at five ratios. **There is no
clean separating value** — two successes sit at 0.0094 and 0.0098 while failures continue well above
any candidate cutoff — so the threshold is placed *below every observed success* (0.0090) rather than
mid-overlap. A false "move closer" contradicts a user whose framing was fine, which is how advisory
guidance gets ignored; a false READY costs only the retry they were making anyway.

**Text size explains only part of the failures and the docs say so.** Kinder and yoghurt fail at their
*largest* rendering, where surrounding prose is best recognised and competes hardest. That cause is
invisible to any size metric.

### Measured state after this pass

Framing sweep (synthetic composites — **report separately from real-fixture results**):

```
                 1.00        0.30        0.45        0.60        0.80
kinder      Conf 53.5    NotFound    NotFound    NotFound    NotFound
sondey      Conf 61.9    NotFound   Conf 61.9   Conf 61.9   Conf 61.9
yoghurt     Conf 5.0     Ambig(2)    Ambig(2)    Ambig(2)   NotFound
stokbrood   Conf 46.0    NotFound   Conf 46.0   Conf 46.0   NotFound
CONFIDENT-WRONG: none
```

Against the failed Pass B checkpoint: same number correct, **one confident-wrong eliminated**, and the
Kinder@1.00 regression (Confident → NotFound) undone.

### Research measured and REJECTED this pass (do not repeat)

- **Connected-component clustering of raw elements** as the successor to vertical banding. Fails three
  independent ways: no gap threshold works across the corpus (sondey needs 1.0h, kinder 2.0h, and at
  2.0h stokbrood and yoghurt collapse to the whole document); kinder's "usable" cluster still spans
  x 0.13..1.00, i.e. it never separated the adjacent panel it existed to separate; and package text is
  spatially connected — prose sits closer to a table than a table's own column spacing. **A locator
  must key on table STRUCTURE (repeated aligned value columns, consistent row pitch), not whitespace.**
  Harness: `RawElementClusteringDiagnosticTest`, with the result table in its KDoc.
- **Widening the declaration opener** for fixtures 3 and 4 (`Naringsindhold (100g)`, fused
  `PourPerlPro 100g`). Measured: **it would fix neither.** Both already resolve a `PER_100_G` column
  from that very phrase — `ColumnClassifier` matches `100g` without a connective — and both then fail
  at `Total-carbohydrate row found but no usable per-100 cell`. The prose reader is correctly refused
  because a document with a resolved basis column belongs to the tabular path. **The real blocker is
  cell-to-column association on curved/prose labels**, not the declaration grammar. Harness:
  `BlockedDeclarationDiagnosticTest`.

### Verified this pass

JVM **609/609** (0 skipped, counted from JUnit XML after `--rerun-tasks`), mandatory real-image
**15/15**, production-path **8/8**, lint clean (exit 0; the 29 advisories are all pre-existing — 17
unused strings, 7 newer-version notices, and 5 assorted).

**Instrumented, verified per class on the final build:** `RealImageOcrTest` 15/15,
`ProductionStillPipelineTest` 8/8, `MealScreenTest` 19/19, `SettingsScreenTest` 4/4,
`LabelVerificationScreenTest` 8/8, `CountablePortionScreenTest` 12/12, `ThemeDefaultTest` 7/7, DAO and
migration classes all green. `ProductScreenTest` is **31/32**: `quickAdjustNeverProducesANegativePortion`
fails on the known below-the-fold harness issue documented further down this file. It is **not** from
this pass — `ProductScreenTest.kt` is unmodified, and this pass touched only `ui/scan/` and
`ui/settings/`, never the product calculator. (`RealMlKitFindingsTest` is a **JVM** test, not
instrumented; trying to run it via `am instrument` gives a misleading `ClassNotFoundException`.)

**181/181 with 0 skipped was recorded from JUnit XML on the whole-suite run that completed.** Later
attempts to reproduce that whole-suite number kept dying part way through, and the cause is the
**emulator, not the code**: `carbscan` has only 2.5 GB and `/proc/meminfo` showed 237 MB free after the
8 MP still-path work, so the instrumentation process is killed mid-run. Per-class and per-package runs
pass. When re-verifying, reboot the AVD first, or give it more RAM.

Both privacy barriers re-verified independently on the release build: `ScanEvidenceRecorder`,
`ScanEvidenceExport` and `OcrDiagnosticsReport` are **absent** from release `mapping.txt` while
`UnitMarkerFilter` and `TextResolutionGuidance` are correctly retained, and the evidence `FileProvider`
is present in the debug merged manifest and **absent** from release.

**Barcode freeze verified structurally:** `ScannerScreen.kt`, `BarcodeAnalyzer.kt`,
`BarcodeStabilityTracker.kt` and `BarcodeFrameReader.kt` are untouched in the working tree,
`ScannerScreen` contains no `ImageCapture` reference at all, and 49 barcode/scanner tests pass.

**A Windows trap that wastes a run:** `connectedDebugAndroidTest` can fail with
`FileSystemException: ...logcat-<test>.txt: The process cannot access the file` — a stale lock on the
per-test logcat file, **not** a test failure, and the wrapper may still report exit 0. Delete
`app/build/outputs/androidTest-results`, restart the adb server, or drive the suite with
`adb shell am instrument -w -r` and count `INSTRUMENTATION_STATUS_CODE` directly.

### A measurement method worth reusing

**900x1600 is the Kinder fixture's own size, not a phone capture.** An evidence line reading
`capture.jpg = 900x1600` was read as proof that CameraX negotiated a 1.4 MP still; it was a replay of a
committed fixture. `ImageCapture` is configured with `ResolutionStrategy(Size(3264, 2448),
FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)` and `CAPTURE_MODE_MAXIMIZE_QUALITY`, bound through one
`ViewPort` with preview and analysis. **The negotiated resolution on physical hardware is still
unmeasured** — the emulator's virtual camera does not answer that question, and `meta.txt` on a real
device will.

## Capture-first scanner pass (2026-08-17) — READ BEFORE TRUSTING ANY OCR NUMBER BELOW

**The single most important correction in this file: the nine-fixture results recorded further down
are PARSER results, not production results.** `RealImageOcrTest` recognises each whole asset with
`InputImage.fromBitmap(asset, 0)`, bypassing `StillImageLoader`, `ScanRegionMapper` and
`LabelAnalyzer.analyzeStill` entirely. It measures the parser's reaction to real recognizer output.
It is **not** evidence about what a user gets from the scanner, and it never was.

Full audit: `docs/plans/2026-08-17-nutrition-scanner-audit.md`. Design:
`docs/plans/2026-08-17-capture-first-scanner-design.md`.

### What the gap was hiding: the ROI crop destroyed both canaries

Measured, not inferred. Running the corpus through the **production** still path:

| | golden suite (whole asset) | production path (before) | production path (after) |
|---|---|---|---|
| sondey | Confident 61.9 | **NotFound** | Confident 61.9 |
| kinder | Confident 53.5 | **NotFound** | Confident 53.5 |
| yoghurt | Confident 5 | **NotFound** | Confident 5 |
| witte kaas | NotFound | Confident 2.3 | **NotFound** (regression, see below) |
| **totals** | 4 correct | **2 correct** | **5 correct, 0 confident-wrong** |

The shipped still path cropped the capture to the scan overlay **before** recognition. On a tall
label that removes the basis header band, so `ColumnClassifier` reclassified the per-100 column as
`REFERENCE_PERCENT` and the interpreter correctly refused an unplaceable value. Sondey's diagnostics
said it exactly: `rejected: 61.9: REFERENCE_PERCENT column` — the right value, on the right
total-carbohydrate row, thrown away for want of a header the app had itself cropped off.

**No parser rule was at fault and none was relaxed.** The safety architecture behaved as designed on
a truncated input. This is the clearest evidence yet that these guards are worth keeping.

### The fix: recognise first, crop later (never a bigger margin)

`analyzeStill` now runs **Pass A on the whole uncropped capture**. A wider fixed margin was rejected
on evidence: the header's offset varies per package, so any margin is a guess that is wrong on some
label with nothing on screen to show it. The header's position is *observable after recognition* and
only guessable before it.

The scan rectangle is now **relevance, not a boundary** — `ScanRegionRelevance` (pure, 10 JVM tests).
Its safety contract is the load-bearing part and is pinned by negative tests: it may only ever
**narrow** an existing reading. It can never turn `NotFound` into a reading, never alter a `Confident`
candidate, never introduce a candidate the parser did not produce, and never supply a basis — a
single surviving candidate with a null basis stays `Ambiguous` rather than being promoted.

### Capture-first gating

`LaunchedEffect(reading) { if (reading != null) analyzer.pause() }` latched the UI on the **first
live 1280x720 frame** that produced any interpretation. Because the primary capture button renders
only inside `SearchingCard` (shown only while `reading == null`), **the user frequently never reached
the 8 MP path at all** — the app answered from an analysis frame of a table they were still aiming.

Live frames now write `liveReadiness`, which drives framing guidance only and can never become the
result. `reading` is set exclusively from a still capture. `ocr_looking` was reworded from "Looking
for carbohydrates…" to "Point at the nutrition table" because under capture-first the old string
promised something that state cannot deliver.

### Focus and metering

There was **no `FocusMeteringAction` anywhere in the app**; capture fired on whatever AF state existed.
Now AF+AE+AWB are requested on the framed region before capture, bounded by `FOCUS_TIMEOUT_MS = 1200`.
The bound is the contract: `startFocusAndMetering` can legitimately never complete on a low-contrast
surface, so the capture fires on focus completion **or** timeout, exactly once, guarded by an
`AtomicBoolean`. A missed focus costs a softer photo; a hung shutter costs the feature.

### The honest regression

**Witte kaas went Confident 2.3 -> NotFound.** It was the one fixture the crop *helped*: cropping
removed prose that was defeating row reconstruction. This is a real trade — 3 gained (2 of them
canaries), 1 lost — and it is recorded rather than hidden. It is the first target for the targeted
re-read layer (priority 5), which can recover it without reinstating a crop that costs two canaries.

### Known-scale finding, for the targeted re-read work

Grated cheese reads `2.09` at full scale and **`2` (the printed value) at 0.5x and 0.75x**. The
"unrecoverable recognition failure" recorded below is unrecoverable *at that scale only*. This is
evidence that independent re-recognition of a tighter/rescaled crop can recover it **without any
numeric-repair rule**. It does **not** license a global downscale: the same run breaks sondey and
kinder at those scales. Scale sensitivity runs both ways per label.

### Verified

JVM **578/578** (0 skipped, counted from JUnit XML after `--rerun-tasks`), instrumented **178/178**
(0 skipped), lint clean. `ProductionStillPipelineTest` is the new suite that measures the **shipped**
feature — put production-path regressions there, not in `RealImageOcrTest`.

**Still NOT verified on a physical device.** The camera path — focus, exposure, 8 MP memory, and
whether capture-first gating engages in the hand — is emulator-only. Protocol written for the owner:
`docs/physical-device-scanner-qa.md`.

## Real-image OCR generalization (2026-08-17) — nine real packages, prose labels, provenance

Seven more real package photographs were added to the two existing ones and put through the
**production** pipeline. Design:
`docs/superpowers/specs/2026-08-16-real-image-ocr-generalization-design.md`; plan and its three
owner amendments: `docs/superpowers/plans/2026-08-16-real-image-ocr-generalization.md`.

**What the baseline found, and why it mattered.** Before any change, three of the nine fixtures
returned a **confident wrong** carbohydrate value, and two of those returned the **saturated-fat
figure** as total carbohydrate. That is the single worst outcome this app can produce. 537 JVM tests
were green throughout, because every synthetic fixture placed a printed row's elements at identical
y and none reproduced a wrapped prose sentence. **A green suite is not evidence that the scanner
reads packaging.**

Interpretation-level confident-wrong went **2 → 0**. One confident-wrong remains (fixture 2, 2.09
where the package prints 2.0) and is **recognition-originated** — ML Kit genuinely returns `2,09`,
so no honest downstream rule recovers it. Do not add one.

### The four changes

1. **`CandidateProvenance`** (`FromRow` / `FromProseSpan`) on `NutritionParseReport`, populated on
   every parse and **not** `BuildConfig.DEBUG`-gated. It exists because on several labels the sugars
   figure **equals** the total — fixture 3 prints `1,6 g` for both — so a value assertion cannot
   detect a sugars misread. Provenance is part of the golden assertion for prose fixtures, not
   decoration. R8 correctly retains it while stripping the diagnostics renderer.
2. **`CarbohydrateTermAnchor`** — on a total-carbohydrate row, a number belongs to the **nearest
   nutrient name to its left**. This is what killed the two fat-figure results: a wrapped sentence
   puts the previous nutrient's clause tail and the carbohydrate clause head on one reconstructed
   row, and no *distance* rule can separate them (the fat figure sat 21 px from the basis column's
   centre, the true carbohydrate figure 336 px away). Reading order can. It only ever **removes**
   candidates, never invents one, and is inert on an ordinary table row. Anchoring on "nearest
   nutrient name to the left" rather than "left of the carbohydrate term" is load-bearing: a table
   may print its label column to the *right* of its values, and there the figure survives because
   nothing else claimed it.
3. **`ProseNutritionReader`** — reads a total from a running sentence
   (`Voedingswaarde per 100 g: … koolhydraten 46 g, waarvan suikers 1,0 g`). Non-scoring by
   construction: no ranking, no "closest number", no tunable distance. Runs **only** after a tabular
   `NotFound` and **only** when a positive two-part eligibility predicate holds.
4. **Serving column carrying its own weight**, with `PrintedWeightResult`. See below.

### The prose eligibility predicate — three rules that each cost a measurement to find

**Condition 1 — ordered structure, evaluated over the DECLARATION, not one row.** The sequence is
`TOTAL_CARBOHYDRATE → total value → CARBOHYDRATE_CHILD → child value` in reading order. **Bare
co-occurrence of a total term and a child term on one row is forbidden as a predicate** — that is
exactly what a *failed table* produces through row merging, i.e. the 2026-08-16 chaining bug, and
using it would hand the prose reader the tables it must never touch. Tokens may cross row boundaries
only **within one declaration span**, because ML Kit wraps a printed sentence wherever the line ends
and the child clause routinely lands on the next row.

**Condition 2 — absence of a USABLE basis column, not of any resolved one.** On a prose label the
basis phrase is embedded in a sentence, so `ColumnClassifier` resolves a column from it regardless —
fixture 6 resolved three, two of them from the **ingredients** prose. Measured: the original
"no resolved column" predicate could **never** fire on any real prose label. A column is *usable*
only when aligned numeric nutrient-value cells sit at its x-position (`MIN_ALIGNED_VALUE_CELLS = 3`;
prose tops out at 2, every genuine table reaches ≥3). **Never infer usability from the existence of
a `NutritionColumn` object** — that is precisely what the old predicate wrongly trusted.

This is what keeps the merged-table guard intact: a merged table's basis column has real values
aligned under it, so it *is* usable, so eligibility refuses. `aMergedTableRowIsNotProse` asserts that
**reason** (`hasUsableBasisColumn == true`), not just the verdict, so the guard cannot silently
erode.

**A rule that looks right and is dangerous:** counting cells aligned to the column's *header*
x-centre. Sondey's header centre sits 6 text heights from its value column, so that rule marks the
canary table unusable and hands it to the prose reader. It was tried and discarded.

### Serving weights: two acquisition sites, deliberately different failure modes

`withPrintedWeight` returns a sealed `PrintedWeightResult`. A weight found on its **own line** is the
parser's own geometric inference — if the arithmetic refuses it, drop the weight and keep the
per-serving figure (the label really did print it). A weight stated **inside the header** is a claim
about what the column *is* — if the arithmetic refuses it, header and column contradict each other
and the **entire `ServingCarbCandidate` is dropped**, because `carbsPerServing` came out of that same
column. Both sites route through the one existing `ServingWeightAssociator.agreesWithTable`; a second
tolerance is how the two drift apart.

### Deferred deliberately (owner, 2026-08-17) — do not "fix" these casually

- **Fixtures 3 and 4 return `NotFound` because they open zero prose declarations.** `basisPhraseAt`
  requires a connective; these print `Næringsindhold (100g)` (a Danish noun) and a fused
  `PourPerlPro 100g:`. Widening it is **its own task with its own safety envelope**: a constrained
  declaration grammar, *never* arbitrary "noun + 100 g" or generic fused-token matching, strong
  carbohydrate-term anchoring and declaration boundaries established independently of the returned
  value, and adversarial negatives for ingredients, sugars, fat, serving prose and unrelated "100 g"
  text **before** enabling it. Fixture 3 must keep asserting provenance/anchor identity rather than
  the bare number; fixture 4 must keep `2.5` and `19` forbidden.
- **`Ø/portie` is left unresolved.** The package prints `Ø/portie` (the European "average per"
  symbol) and ML Kit reads `Ø` as `o` — it is *not* a `per` misread. Adding bare `o` to the
  connectives is too permissive. Fixture 2's primary result is unrecoverable anyway.
- **Preprocessing: tried, NOT retained.** A 2× upscale rescued fixture 5 but regressed **two**
  working fixtures (kinder and stokbrood, Confident → `NotFound`). It failed "improves at least one,
  degrades none" and was deleted. Record kept so it is not retried blindly.

### Verified

JVM **568/568**, instrumented **167/167**, both 0 skipped. `RealImageOcrTest` is now **mandatory** —
every `assumeTrue` removed, so a missing fixture fails the suite instead of silently skipping (a
skip is how a green suite coexisted with a scanner that did not work). Lint clean; debug and minified
release both build; the diagnostics renderer is absent from release `mapping.txt` while
`CandidateProvenance` is correctly retained.

**Nine-fixture production state:** 1 `NotFound`(recognition) · 2 **2.09 confident-wrong**(recognition,
accepted) · 3 `NotFound`(deferred) · 4 `NotFound`(deferred) · 5 `NotFound`(recognition) ·
6 **46.0 `FromProseSpan`** · 7 5.0 `FromRow` · 8 61.9 `FromRow` · 9 53.5 + 6.7/PIECE `FromRow`.

**Still NOT verified on a physical device.** The parser is proven on real optics (the fixtures are
hand-held phone photographs run through the real recognizer), but the **camera path is not**.
`docs/manual-qa.md` §15f and the new §15g are the open gates.

**A JVM trap this repo has now hit twice:** `BigDecimal("50").stripTrailingZeros()` is `5E+1` at
**scale −1**, and `BigDecimal.equals` compares scale. Any assertion on a `stripTrailingZeros()`
result whose value is a multiple of ten must use `compareTo`.

## Real-device scanner pass (2026-08-16) — READ THIS BEFORE TOUCHING OCR GEOMETRY

Driven by two physical-device failures, both treated as release blockers. Nothing about the
calculation, the schema, migrations or the §10 lookup priority changed.

### The nutrition scanner failed on real packaging because rows chained on tilt

**Root cause, measured, not guessed.** `LogicalRowBuilder` decided row membership by comparing an
element's vertical overlap against the row's *running union box*. That box grows as members are
added, so on a photograph — where the same printed row drifts steadily down across the table's
width — it inflated well past the text height, and any element of the **next** row falling inside it
scored a full overlap ratio and joined. Classic single-linkage chaining. The carbohydrate row
swallowed the sugars row, `RowClassifier` typed the merged row `CARBOHYDRATE_CHILD` by the
(correct, unconditional) exclusion rule, and there was then **no total-carbohydrate row at all** →
`NotFound`. The safety rule turned a geometry bug into a total wipeout.

Measured on a reconstruction of the Kinder table before the fix:

```
pitch=30 slope=2%  -> Confident
pitch=30 slope=4%  -> NotFound  (carbohydrate and sugars rows merged)
pitch=40 slope=5%  -> NotFound  (merged)
pitch=50 slope=8%  -> NotFound  (fragmented; all values landed on the child row)
```

**4% slope is about 2.3 degrees of hand tilt.** Every pre-existing OCR fixture places a printed
row's elements at *identical* y — slope exactly 0 — which is why 459 tests were green while the
feature did not work. **Do not add an axis-aligned fixture and believe it proves anything about a
photograph.**

**The fix** is `RowSlopeEstimator` + de-skewed banding, not a loosened threshold:

- one global skew scalar (dy/dx) estimated from the image itself, then rows grouped on de-skewed
  centres against the row's **median**, never against a growing union or the previously added
  element — both of those are single-linkage rules that walk;
- ML Kit's `blockId`/`lineId` contribute **only** that scalar. Row membership is still geometry-only,
  so the architecture's core claim is intact. The estimator refuses a line spanning >2.5 text heights
  (the documented "two printed rows merged into one line" case), refuses a line under 4 text heights
  wide (that measures box jitter, not slope), and takes the median so one survivor cannot move it.

`TiltedTableRowReconstructionTest` sweeps 4 row pitches x 7 slopes and asserts `Confident` in all 28.

### Three more real defects the same fixtures exposed

1. **`ColumnClassifier` swallowed a bare `%` header.** The greedy longest-span pass matched
   `"per stuk %"` as one PER_SERVING span: it dragged that column's centre 38 px toward the
   percentages, emitted **no** REFERENCE_PERCENT column, and destroyed the serving descriptor
   (`"stuk %"` parses as no unit word). A bare `%` is now its own column and can never end another
   span.
2. **Split percent tokens never recovered a column.** The cell fallback tested `\d\s*%` against raw
   element text, so `"3"` + `"%"` — the other tokenization ML Kit produces — was invisible. It now
   goes through `PercentAssociation`, which already handled both.
3. **A serving weight printed on its own line was ignored.** `"per stuk"` / `"(12,5 g)"` gave a
   descriptor with no weight. `ServingWeightAssociator` now adopts it **only** when the table's own
   arithmetic reproduces the printed per-serving figure (53.5 x 12.5 / 100 = 6.6875 vs printed 6.7).
   That corroboration is the whole safety argument — proximity to the right column is not evidence.

**A bug I introduced and caught in review:** the printed weight is the weight of the *whole serving*,
which is exactly what `ServingDescriptor.weightOrVolume` means; I initially multiplied it by
`descriptor.count`. Invisible on a count-of-one label — i.e. on every fixture — and it would have
doubled every portion from a "per 2 stuks (25 g)" label. There is now a test for count > 1.

### The scan region is no longer decorative

Its own KDoc used to say cropping "has no demonstrated recognition benefit". That was written
against rendered fixtures, where the frame contains a table and nothing else. On a real package the
rest of the frame is the ingredient list, marketing copy, a barcode and a date — more rows to
survive, and words like "suikers" and stray "100 g" appear in prose as readily as in a table.

A still capture is now cropped to the overlay + 12% margin before recognition. **The mapping is
trivial only because the camera binds all three use cases through one `ViewPort` matched to the
`PreviewView`** — that makes the capture cover the preview's field of view, so a fraction of the
preview is the same fraction of the JPEG. Without it the crop would need the preview crop, both
aspect ratios and the rotation, and would be wrong per-device in a way nobody could see.
**Correctness lives in the binding, not in `ScanRegionMapper`'s arithmetic.** Every refusal in that
mapper falls back to reading the whole image, which is the pre-pass behaviour.

Binding happens inside `doOnLayout` because `PreviewView.viewPort` is null before measurement.
Still capture went 1920x1440 -> 3264x2448 (§12); analysis stays 1280x720, since live frames are
guidance only.

### Barcode: it fired on one decoded frame

`BarcodeAnalyzer` accepted the first frame that decoded anything, latched by an `AtomicBoolean`.
Raising the phone toward a shelf decodes a neighbouring product for one frame, and the app committed
to a lookup — landing on *Product not found*, which offered **no way back to the camera at all**.

Policy now lives in `domain/` (`BarcodeStabilityTracker`, `BarcodeFrameReader`), pure and
JVM-testable with no camera: supported format -> valid check digit -> centre inside a generous
central region -> longest side >= 20% of the frame -> held for 3 frames (or 250 ms at low frame
rates, never fewer than 2) -> one-shot latch. Validation happens *before* the tracker sees anything,
so a misread digit cannot accumulate stability. The largest qualifying barcode wins when two are in
shot. `Scan barcode again` is now the primary action on *Product not found* and pops that dead end.

### Verified against the real photographs, and what that caught

The owner supplied the two packages mid-session. `RealImageOcrTest` (androidTest) runs the actual
photographs through the production ML Kit recognizer, `MlKitOcrMapper` and the real parser.
**All 5 cases pass**: Sondey **61.9 g/100 g** (never 47.6), Kinder **53.5 g/100 g** (never 3, 7 or
53.3), plus **6.7 g per piece** with a `PIECE` descriptor. Both photographs contain a *second*
package's ingredient panel in the frame and are only 900x1600 (WhatsApp-compressed), and the reading
is still correct — before the ROI crop and the 8 MP capture, which the test does not exercise.

**Three defects only the real ML Kit output could reveal.** Every one of them was invisible to 537
JVM tests, and each is now pinned by `RealMlKitFindingsTest`:

1. **A letter misread as a digit became a carbohydrate value.** ML Kit read Slovenian "**O**gljikovi"
   as "**0**gjikovi". That `0` is a well-formed number and 0 g of carbohydrate is legitimate, so it
   cleared the validator, became a second interpretation, and turned a correct confident **53.5 into
   an ambiguity between 53.5 and 0** — asking the user to choose between the right answer and a
   misread letter. A value cell must now stand alone, or carry only a real unit: `53,5g` is a cell,
   `0gjikovi` is a word. Testing that the suffix merely *starts* with "g" would accept both and fix
   nothing.
2. **`RowClassifier` and `ColumnClassifier` disagreed about what a serving header looks like.** The
   per-piece header spans several printed lines of eight languages; the line carrying "Par pièce"
   names no per-100 basis, no generic serving word and no reference intake, so it was typed `OTHER`
   — and `ColumnClassifier`, which only ever looks at `HEADER` rows, never got to apply the
   countable-unit vocabulary it already had. The per-piece column did not exist and 6.7 was
   discarded with `no column`. Both stages now route through `ServingSizeParser`.
3. **The serving header arrives as `"/ Par pièce"`** — a slash from the language separator, a
   connective that is not the English "per", and an accent `ServingSizeParser`'s unit table does not
   carry. Each alone defeated `descriptorFromHeader`'s `removePrefix("per")`. It normalizes first now.

The Croatian terminology added earlier in the pass turned out to be load-bearing rather than
decorative: ML Kit merged "od kojih šećeri" with "Kohlenhydrate" onto one recognized row, which
without the exclusion types as `TOTAL_CARBOHYDRATE` on the strength of the German word.
Serbian/Macedonian/Albanian were added from the same observed text.

**Read `OcrDiagnosticsReport` output before touching any threshold.** Every finding above came from
the diagnostics dump naming the stage that ran out of evidence, not from reading code. It is
`BuildConfig.DEBUG`-gated and R8 strips the whole renderer from the release build — verified absent
from `mapping.txt`.

### Still not verified on a physical device

The photographs are hand-held phone shots, so the parser is now proven on real optics — but the
**camera path is not**. Emulator-only, and in risk order: the `ViewPort`-cropped 8 MP capture
(CameraX must crop the saved JPEG to the viewport for `ScanRegionMapper`'s fractions to mean
anything), memory at 8 MP, and the barcode acceptance thresholds against real hand movement.
`docs/manual-qa.md` §15f is the gate. The images live in `app/src/androidTest/assets/ocr_real/` and
are **committed** as of 2026-08-16 — the earlier "git-ignored" note is obsolete; see "Nine-fixture
real-image corpus" below for the policy reversal and what is still ignored.

## Nine-fixture real-image corpus + the prose reader (2026-08-16/17)

**READ THIS BEFORE ADDING ANY OCR RULE TO MAKE A LABEL "WORK".**

### The corpus is committed and mandatory — the gitignore policy reversed

Nine sanitized crops live in `app/src/androidTest/assets/ocr_real/` and are **tracked**. The
previous arrangement (images local-only, `Assume`-skipped) is exactly how a green suite coexists
with a broken scanner: with no assets every case *skipped* and CI reported success for a run that
measured nothing. `RealImageOcrTest` now **fails** on a missing fixture, and there is no
`assumeTrue` anywhere in it.

What stays ignored is unchanged and non-negotiable: `Test labels/` and
`ocr_real/originals/` hold full-frame originals (surroundings, other packages, people) and the repo
is public. Only the cropped nutrition panels are tracked.

### Prose reader: two independent gates, both required

Some labels print nutrition as a run-on multilingual sentence with no table at all.
`ProseNutritionReader` (pure Kotlin) reads those, but only on **tabular NotFound** and only when
`isProseLabel` holds. It is a recognizer of one printed form, **not a second scoring model** — it
has no notion of a best candidate, which is what keeps it from reintroducing the scoring path the
geometry-first rewrite removed.

1. **Condition 1 — positive sentence structure**: `TOTAL_CARBOHYDRATE → value → CARBOHYDRATE_CHILD
   → value`, in reading order. Bare co-occurrence of a total term and a child term on one row is
   **forbidden** as a predicate: that is the signature of a *merged table row* (the 2026-08-16
   chaining bug), so keying on it would hand the prose reader precisely the tables it must never
   touch.
2. **Condition 2 — no *usable* basis column.** Amended 2026-08-17 from "no *resolved* column",
   which could never be satisfied: on a prose label the basis phrase sits inside running text, so
   `ColumnClassifier` resolves a column from it regardless — all three prose fixtures resolve one,
   two of fixture 6's come from the *ingredients* prose. A column is **usable** only when
   ≥3 value cells the interpreter would bind to it are also **mutually aligned with each other**
   (`MIN_ALIGNED_VALUE_CELLS`). Binding alone is not evidence — that tolerance is deliberately
   generous to survive photographic skew. **Never infer usability from a `NutritionColumn` object
   existing**; that is what the old predicate wrongly trusted.

**The merged-table guard is intact and pinned by its reason, not its verdict.**
`aMergedTableRowIsNotProse` asserts `hasUsableBasisColumn == true` — a merged table still prints its
values one under another, so the column stays usable and condition 2 refuses. The reconstruction
defect moved the *rows*, not the *columns*. If a future change makes that column "unusable", the
guard has silently eroded and the assertion catches it.

### The window is the declaration, not the row (owner amendment, 2026-08-17)

Condition 1 originally evaluated within a single reconstructed row. ML Kit wraps a printed sentence
wherever the line ends, so the child clause routinely lands on the *next* row. **The predicate did
not change**; only the window did, and it moved to the declaration span `read` already assembles —
the same assembly, not a second token stream, so the gate and the reader cannot disagree about where
a declaration ends.

That boundary is what stops it becoming document-wide chaining: a declaration runs from one basis
phrase to the next, so the walk cannot reach into a *neighbouring* declaration to borrow the child
clause it is missing. Pinned by `aSequenceCompletedAcrossTwoDeclarationsIsNotProse`, whose
preconditions assert the fixture really produces **two** declarations and that the four tokens
*would* satisfy a document-wide window — without those it would pass vacuously and pin nothing.

### Span- vs row-level provenance, and why row level is insufficient

`CandidateProvenance` is `FromRow(rowText, rowBox)` or `FromProseSpan(nutrientTerm, …)`. Row
granularity suffices for a table — total and sugars occupy different rows. It is **not** sufficient
for prose, where both share one reconstructed row and, on the real corpus, *the same printed number*:
fixture 3 prints **1,6 g for its total and 1,6 g for its sugars**. A numeric assertion there proves
nothing — a sugars misread passes it — so the golden tests assert the **bound nutrient term** is a
carbohydrate term and is *not* a child term, checked against `NutritionTerminology`'s own
vocabularies so the test cannot drift from what the parser treats as a child.

### Measured state of all nine, 2026-08-17 (see the task-9-10 report for the full table)

Four Confident and correct (stokbrood 46 via prose; yoghurt 5.0, sondey 61.9, kinder 53.5 + 6.7/piece
via row), four NotFound, one Confident-and-wrong.

**Fixtures 3 and 4 are blocked upstream of the prose reader, not by it.** Neither opens a
declaration at all, because a declaration must begin at a recognized basis phrase and neither label
prints one: fixture 3's is `Naringsindhold (100g):` (a Danish noun, no connective) and fixture 4's
arrives as the fused token `PourPerlPro 100g:` (ML Kit welded the trilingual "Pour / Per / Pro"
together). Verified by control — substituting a literal `per` into the same recognized text yields
one declaration and prose eligibility, so the amendment works and the blocker is elsewhere.
**Widening basis phrases to bare nouns is not authorized**, and fixture 3 is precisely the label
where a wrongly-bound term would be undetectable by value.

**Fixture 2 is Confident 2.09 where the package prints 2,0 — a recognition-stage failure that is
unrecoverable at the parser.** ML Kit genuinely returns the token `2,09`; every downstream stage
then behaves correctly, and 2.09 is a legitimate carbohydrate quantity so nothing can refuse it.
It is asserted **as measured**, with a comment, rather than papered over. **Do not write a rule that
trims a digit from a value adjacent to another column** — that repair silently corrupts correct
readings elsewhere. A false confident value is substantially worse than `NotFound`, because the user
doses insulin from it.

### Preprocessing: tried, NOT retained

A plain 2x bilinear upscale before recognition was measured against the corpus. It rescued fixture 5
(NotFound → Confident) but **regressed two canaries** — kinder and stokbrood both fell Confident →
NotFound. The acceptance condition was "improves at least one and degrades none", so it was
discarded and the experiment deleted. Do not re-try upscaling without re-running all nine.

## The quick-adjust test failure — fixed, and worth reading before trusting a click

**Resolved 2026-08-16. It was a test bug, not an app bug**, and it predates this pass (it fails on
HEAD too, verified in a clean `git worktree`). Both `quickAdjust*` cases now call `performScrollTo()`
before `performClick()`.

**The actual cause, measured:** in the test harness the quick-adjust row starts outside the visible
bounds of the portion zone's scroll container. A node scrolled out of view is still
`isPlaced == true` and still reports a size, but its `boundsInRoot` is an **empty rect at the
origin** — so it has no clickable area. `performClick()` on it does not throw. It clicks nothing,
`onAdjust` never fires, and the portion silently stays put.

```
before scroll:  bounds=Rect(0,0,0,0)          size=228x126   placed=true
after scroll:   bounds=Rect(799,861,1027,987)                portion 65 -> 75
```

**This is a test-harness artifact, not a user-facing layout defect — do not "fix" the layout.**
After the scroll the row occupies y=861–987 on a 1080x2400 @420dpi window (411x914 dp, an ordinary
modern phone), comfortably on screen and well clear of the result panel at y=1050. I initially wrote
this up as a real overlap that left the ± buttons dead on small devices; that was wrong, and the
arithmetic above is what disproves it. `createComposeRule` composes into a harness-sized container
rather than the full activity window, which is what puts the row out of view there but not in the app.

**Why the assertion only started failing recently:** the uncommitted UX-polish work tightened it from
a whole-screen `onNodeWithText("36.2 g")` to one scoped to `PRODUCT_RESULT_TAG`. The loose version
had been passing for the wrong reason — it could match the portion field, which also contains the
text. The tightening did not break anything; it revealed that the click had never been working.

**Diagnostic method worth reusing.** `fetchSemanticsNode().boundsInRoot` + `.layoutInfo.isPlaced` is
what settles this class of question in one run. Note specifically that:

- `printToLog`'s per-node offsets are **not** absolute screen positions — reading them as such sent
  me down a wrong path (I concluded the pinned result panel was overlapping the row; it is not).
- "displayed" in an assertion failure does not distinguish *covered* from *scrolled out of view*.
- Two layout changes were tried against the wrong diagnosis and **reverted**: shrinking the result
  panel's padding, and enlarging the zone's trailing `Spacer`. Neither is needed. The panel padding
  keeps only the give-back rule for the new provenance line, which is correct on its own terms.

**The general rule, now with two instances in this repo:** if `performClick()` appears to do nothing,
the control is probably unreachable — covered by the keyboard (the 2026-08-14 case) or below the fold
(this one). Scroll to it first; do not start moving layout.

**Do not "fix" the decorative corner circle.** Blue on Home/Product and orange on Meal is not an
inconsistency — `ManualEntryScreen` is orange too. Blue marks lookup-driven screens, orange
user-authored ones. I flagged it as a possible defect in the review and was wrong.

**Deliberately NOT done** (the three post-release findings, all needing an owner decision):
surfacing a favourite's remembered result on Home so a repeated product needs no scan (finding 1 —
fastest path is currently the slowest, and the data is already computed then discarded); making the
meal startable rather than only appendable (finding 2 — `MealActions` renders only inside
`ResultPanel` when `exact != null`, and Home's meal bar hides whenever the search field is
non-blank, i.e. exactly while the user is finding the next item); and per-item provenance on meal
lines (finding 3 — needs a `MealItem` field, hence a migration).

**Test changes made in this pass, all because behaviour or copy genuinely moved:** three
`MealScreenTest` assertions updated for `meal_bar_summary`'s new "g carbs" suffix, and one
`LabelVerificationScreenTest` assertion for the corrected capital in `verify_label_basis_mismatch`
(it was asserting on the typo). New `QuickAdjustStepTest` (5 JVM cases) pins the step ladder,
including that a missing package size keeps the original ±5 and that the ladder never decreases as
the package grows.

**Still emulator/unverified:** none of this pass has been seen by a human on a device. Two items are
visual claims that instrumented tests do not settle — the result slot's new two-line pending state
at 1.8× font scale, and whether the provenance line crowds `MealActions` on a short display.

**Instrumented suite is green (140/140).** The two `quickAdjust*` failures seen during this pass
predated it and were a test bug — see the dedicated section above.

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
6. Repo is **public** since 2026-08-16 (superseding the earlier "stays private" decision) — GitHub
   Pages could not serve the privacy policy from a private repo. **Just the Carbs**
   (`app.justthecarbs`) is the current, decided public
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

1. **§44 regulatory assessment is written but unsigned — it blocks public production, not upload.**
   The manufacturer's assessment is `docs/regulatory-qualification-assessment.md` (conclusion: **not
   a medical device**, EU only, conditional on its §7 marketing constraints); a PDF export exists for
   signature. **Both files are deliberately untracked** (see `.gitignore`) — they contain the
   owner's personal information and the repo is public. They are on disk; read them there.
   The conclusion is complete; what is missing is the signature and date in §9, which closes A1/A3
   and B. **A2 is owner discretion, not a legal precondition** (the MDR makes the manufacturer the
   responsible party), and **A5 is moot while v1 is EU-only** — so the remaining production gates are
   the signature and A6 listing-wording review. Gate rows are in
   `docs/regulatory-release-checklist.md`; the deployment sequence is `docs/play-release-readiness.md`.
   §7.1 forbids marketing the app for diabetes, and forbids the owner's personal circumstances
   appearing in any published material — binding on store copy and review replies. Do not restate
   those circumstances in tracked files, including this one.
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
