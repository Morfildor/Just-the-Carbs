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

## Scanner shutter haptic feedback (2026-09-08) — READ FIRST

`1.0.6` / `versionCode 7` — see "Version and track state" further down this file for the single
current-version authority; that section is kept up to date and is where to check version status,
not here.

Both scanners now give tactile shutter acknowledgement, gated by the single existing app-level
*Haptic feedback* setting (`AppSettings.hapticsEnabled` — no new preference was added):

- **Barcode scanner** (`ScannerScreen.kt`) — unchanged behaviour, already established: one
  `HapticFeedbackType.LongPress` when `BarcodeAcceptance.Accepted` is consumed, nowhere else.
- **Nutrition-label scanner** (`LabelScannerScreen.kt`) — one
  `HapticFeedbackType.LongPress` at a committed shutter capture, fired **immediately after**
  `coordinator.freezeAtShutter(...)` inside `captureLabel()` and before `coordinator.beginNewWork()`.
  This is shutter acknowledgement only — "the shutter press was accepted and the scan is being
  captured" — **never** an OCR-success or value-confirmed signal.
  `hapticsEnabled` is threaded from `AppSettings` through `JustTheCarbsNavHost` into
  `LabelScannerScreen`/`LabelCamera`, mirroring the barcode scanner's existing wiring exactly.
  **No default value** on either `hapticsEnabled` parameter (`LabelScannerScreen` or the private
  `LabelCamera`) — every real caller must supply it explicitly, so a caller that forgets to wire it
  fails to compile rather than silently shipping with haptics off.

### Outcome haptics (2026-09-08, later same day) — CORRECTS THE "no second haptic" CLAIM ABOVE

A scan now produces **at most two** haptics, distinguishable by effect. The shutter `LongPress` is
unchanged and still fires immediately after `freezeAtShutter(...)`. The second is fired once from
`readSelectedTable`, only on the **automatic** pass, and is decided by `ScanHapticCue`
(`ocr/ScanHapticCue.kt`, pure, 9 JVM cases) keyed on `ScanPresentationDecision.Action`:
`Confirm` when a question is put on the frozen photograph, `SegmentTick` on `AUTO_ADVANCE`,
`Reject` when work is handed back, and **silence** for every outcome of a user-confirmed crop.

**The safety property is an inversion and it is pinned by test:** `AUTO_ADVANCE` — the most
corroborated outcome — gets the *softest* cue, so the habit built is "firm buzz means read the
screen", never "strong buzz means trustworthy number". Do not "improve" this by making success feel
more emphatic; `the most confident outcome gets the softest cue` fails first if you do. Negative
controls: inverting it fails 3 tests, removing the `!automatic` restraint fails 1.

Compose UI 1.12.0's `HapticFeedbackType` routes through `HapticFeedbackConstantsCompat`, whose
`getFeedbackConstantOrFallback` guarantees a real effect on minSdk 26 (`Confirm`→`VIRTUAL_KEY`
below API 30, `Reject`→`LONG_PRESS`, `SegmentTick`→`CONTEXT_CLICK` below 34). `ScannerScreen`'s
comment about `CONFIRM` needing API 30 is therefore obsolete, and was left alone as out of scope.

**Unverified on hardware, and this is the whole design:** whether the three effects are actually
distinguishable by touch. Vibration motors differ enormously between devices; on a poor one the
vocabulary collapses into three identical buzzes, which is exactly what this design exists to avoid.
`docs/manual-qa.md` §39.7 is the gate.

## Two introductions, two flags (2026-09-08, later same day) — READ BEFORE TOUCHING ONBOARDING

**Supersedes the "the carousel is replaced" and "there is still exactly one flag" claims that stood
earlier the same day.** Owner instruction: keep **both**. The app now has two introductions.

| | welcome carousel | coach-mark tutorial |
|---|---|---|
| screen | `WelcomeCarouselScreen` | `OnboardingScreen` |
| route | `Routes.WELCOME` | `Routes.ONBOARDING` |
| opens itself? | **yes**, first launch only | **no**, offered on Home |
| flag written | `hasSeenOnboarding` | `hasSeenTutorial` |
| decides | `startDestination` | Home's reminder card + the launch counter |

**One flag cannot serve both, and this is the whole design.** `hasSeenOnboarding` means "the
carousel has been through"; `hasSeenTutorial` means "done with the coach marks". Were they one
field, finishing the carousel would retire the Home card before it had ever been shown, so a
first-run user would get the carousel *or* the tutorial and never both — which is precisely the
arrangement the pair exists to avoid. Pinned by `TutorialReminderTest.the welcome carousel does not
retire the tutorial reminder` and `OnboardingViewModelTest.finishing the tutorial never marks the
welcome carousel seen`; negative control (making the tutorial write the carousel's flag) fails 6.

**`recordLaunch` keys on `hasSeenTutorial`, not the carousel's flag** — the counter exists solely to
decide whether Home still offers the coach marks, so were it keyed on the carousel a user who read
the carousel on launch 1 would freeze the window there and lose the reminder for the five launches
it was meant to cover.

**The onboarding-flash defect class is guarded again, not structurally impossible.**
`startDestination` reads `settings.hasSeenOnboarding` once more, reversing the "no longer reads a
DataStore value at all" property claimed earlier the same day. The guard is `MainActivity`'s splash
hold: it keeps the splash on screen until `StartupState` carries a real settings value, so this
branch never evaluates a default-shaped one. **That hold is load-bearing for onboarding again — do
not remove it as merely a theme concern.**

**No migration.** DataStore has no `has_seen_tutorial` key on an existing install, so it reads false
and an existing tester sees the carousel once more on the next update. Accepted deliberately: one
screen with a Skip on it, against the alternative of deriving carousel-completion from `launchCount`,
which is cleverer and less honest.

**Skip on the carousel does not exit** — it jumps to the last slide, where *Get started* is the one
place the flag is written. A Skip that left directly would need its own copy of the write, the
failure reporting and the navigation.

**The reminder window** is `domain/TutorialReminder` (pure): Home offers the tutorial on
the first launch and the **five after it** — launches 1..6 inclusive, which is `REMINDER_LAUNCHES +
1` and the off-by-one worth not rediscovering. `AppSettings.launchCount` is incremented once per real
launch from `MainActivity.onCreate` **guarded on `savedInstanceState == null`**, so a rotation does
not burn a launch; the increment happens inside a single DataStore `edit` and re-checks its own stop
condition against stored values, so two launches racing cannot both write the same number.

**Finishing, skipping and dismissing the Home card all set `hasSeenTutorial`**, and all three mean
"I am done with this". Dismissal is deliberately permanent rather than a snooze — an experienced user
reinstalling taps *No thanks* once and is never asked again — which is only safe because
**Settings → Replay tutorial** keeps it reachable.

### A DataStore test trap on Windows — do not read it as a fixture bug

**Two writes to one `DataStore` inside a single `runTest` fail on Windows**, with:

```
java.io.IOException: Unable to rename ...settings.preferences_pb.tmp to ...settings.preferences_pb.
This likely means that there are multiple instances of DataStore for this file.
```

**That message is misleading.** There is exactly one instance; the atomic `.tmp` → file rename is
refused while the previous write still holds the handle. It is a platform/fixture limitation, not app
behaviour — `recordLaunch` is a single `edit` and the app calls it once per launch.

Measured rather than guessed: a probe test doing two plain setter writes fails, while a single
`recordLaunch` passes. **An explicit `CoroutineScope`, `Dispatchers.IO`, and unique temp directories
were all tried and none of them fix it.** Every pre-existing test in `SettingsRepositoryTest` wrote at
most once, which is why nothing had exercised it.

The workaround is to keep each test to **one** write against the store under test — seed prior state
through a *separate, discarded* DataStore instance (`storeSeededWith`) — and to assert rules that
need two writes where the rule actually lives, i.e. in `TutorialReminder`, which takes the flag as a
parameter and touches no file. `SettingsRepositoryTest` says so at the one assertion this cost.

**Replay mode writes nothing.** `OnboardingViewModel.finish()` returns `Saved` immediately for
`TutorialMode.REPLAY` without touching the repository, so watching the tutorial from Settings cannot
stand in for having completed it and cannot fail to close on a broken store. Both modes exit by
`popBackStack()`, because the tutorial is always opened *from* somewhere still on the stack.

**The previews are drawings, and that is structural.** `TutorialPreview.kt` renders constants — no
ViewModel, no repository, no camera, no network, nothing that can write Room or the real meal. There
is no state there to mutate, so "the tutorial cannot affect real data" is a property of the code
rather than a rule to remember. The whole backdrop is `clearAndSetSemantics {}`, so TalkBack cannot
reach a preview control that would do nothing.

### The overlay's presentation (2026-09-08) — owner: "it dims the screen, too boxy, too much dimming"

Presentation only; no step, wording, navigation or flag behaviour changed.

**The dim is graded, not flat.** `BASE_SCRIM` is **0.42**, down from a flat 0.78 everywhere. A single
wash strong enough to make the far corners recede also flattens the area around the target, so a
tutorial about the app's own buttons was drawn over an app you could barely see. `TutorialScrim` now
lays a light base wash and adds the rest of the weight through a **radial gradient centred on the
spotlight**, reaching `EDGE_WEIGHT` at the furthest corner. So 0.42 is not the strength the user sees
at the edges — it is the strength near the control, which is what has to stay readable.

**The hole is feathered.** The clear-blended hole is followed by `FEATHER_STEPS` (12) thin `DstOut`
strokes of decreasing strength across `FEATHER` (28dp). **One thick stroke was tried first and is
wrong** — a band of uniform alpha reads on the device as a pale ring drawn around the spotlight, i.e.
a second edge, which is exactly the boxiness the feather exists to remove. Only a screenshot showed
it.

**Only the halo pulses, never the ring.** `TutorialSpotlightDecoration(pulse=)` scales three
concentric halo strokes; the ring itself stays exactly on the control's bounds. A border that grows
and shrinks around a button reads as the button changing size, and on this screen the ring is a claim
about *which* control the words describe.

**The spotlight travels between steps, but only between two known targets.** `lerpRect` interpolates
per edge (not centre-plus-size, so a target changing shape stays a rectangle). Animating into or out
of null is deliberately excluded: it would slide the hole from the screen's origin, or leave it
briefly over a control the current step is not talking about.

**`IntrinsicSize.Min` on the callout Row is load-bearing.** The accent spine asks to
`fillMaxHeight()`, and in a parent offering unbounded height that takes all of it and drags the card
with it — **measured on the device, the card filled the screen top to bottom** and the tutorial
stopped being a callout at all. The suite was green throughout; nothing in it looks at how tall a
card is.

**The progress dots use `hideFromAccessibility()`, not `clearAndSetSemantics {}`.** The latter
removes the whole subtree *including the test tag*, so `TUTORIAL_PROGRESS_TAG` became unfindable and
`progressIsRenderedForEveryStep` failed. The node must stay findable; it just must not be spoken —
the card's "Step 2 of 6" eyebrow now carries that in words.

**Geometry, not coordinates.** `TutorialAnchors` records `boundsInRoot()` via `onGloballyPositioned`;
an absent or zero-size rect returns null and the overlay renders a **centred callout with no arrow**
rather than pointing at the origin. Anchors are cleared on backdrop change so a stale rectangle from
the previous preview is never pointed at. `CalloutPlacement` (pure, 9 JVM cases) decides which side
the card sits on and returns `CENTERED` when neither side fits — an overlapping card would hide the
control the step is describing.

### Verified (the two-introductions pass)

JVM **1944/1944** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 204 JUnit XML
files — up from 1927). Lint **exit 0, 22 findings, 0 errors** — unchanged baseline, none in a changed
file. Debug APK builds.

Instrumented on the `carbscan` emulator: **38/38, 0 skipped, on three consecutive runs** —
`WelcomeCarouselScreenTest` (new), `TutorialScreenTest`, `TutorialNavigationTest`,
`HomeTutorialReminderTest`. Three runs per this file's standing warning about the soft-keyboard
artefact.

**Negative control:** making `OnboardingViewModel.finish()` write `hasSeenOnboarding` instead of
`hasSeenTutorial` fails **6** tests, including `finishing the tutorial never marks the welcome
carousel seen` by name. Restored and re-verified green.

Driven by hand on the emulator through the whole first run: carousel slides 1→2→3, *Get started* →
Home **with the *New here?* card present**, tutorial steps 1→2 with the spotlight on *Scan barcode*,
exit, force-stop, relaunch → straight to Home with no carousel.

**Three defects only a device screenshot found**, none of which any assertion looks at: the callout
card stretched to fill the entire screen (`fillMaxHeight` spine in an unbounded parent); the feather
rendered as a visible pale ring rather than a fade; and — caught by a test, not the eye — the
progress dots vanishing from the semantics tree along with their test tag.

**NOT verified on physical hardware.** Everything above is JVM plus the emulator, including every
screenshot. `docs/manual-qa.md` §41 is the gate — in particular the new **41.30–41.36**, which are
the only check on whether the dimming and boxiness complaints are actually answered, and 41.1/41.1b,
which are the first-launch gating the emulator can only approximate.

**Do not move this haptic before `freezeAtShutter()` or after OCR/`onUseValue`.** The freeze must
stay the first thing a committed shutter press does — see the "FREEZE FIRST" comment at its call
site in `captureLabel()`, which this pass did not touch, only inserted after.

**Also this pass:** `ScannerScreen.kt`'s `remember { BarcodeAnalyzer { ... } }` closure is long-lived
and unkeyed (created once for the composable's lifetime), so it previously captured
`hapticsEnabled`, the haptic feedback host and `onBarcode` from whichever composition was current
when first created. It now reads all three through `rememberUpdatedState`, so a later change to any
of them is picked up without recreating the analyzer. This is a latent-hazard hardening fix, not a
reproduced defect — nothing in the app currently re-enters this screen with a live analyzer while
`hapticsEnabled` changes underneath it.

**Not verified on physical hardware in this pass** — see `docs/manual-qa.md` §38. Emulator/JVM
execution does not establish vibration quality or timing feel.

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

## Review + dead-code pass (2026-08-30, later same day) — 1.0.3 COMMITTED, READ FIRST

Still `versionCode 4` / `1.0.3`, still open, nothing built as a release and nothing uploaded. Two
defects found reviewing the uncommitted 1.0.3 tree, then a dead-code sweep. **Nothing about the
calculation, schema, migrations, the §10 lookup priority, barcode detection or any OCR recognition
rule changed**, and the nine-photograph corpus is unchanged.

**The 1.0.3 work is now COMMITTED**, on branch `1.0.3-quick-calculation` (three commits off
`8ce1817`). It had accumulated across five passes entirely in the working tree, which meant no
restore point and no way to satisfy the "release builds come from a committed tree" rule. `main` is
untouched.

### Two defects, both in wiring that no test reaches

Both are the pattern this file keeps recording: **the pure unit is pinned, the caller's state
lifecycle is not.** `CropChange` has 9 JVM cases and is correct; the defect was in `LabelScannerScreen`,
and there is **no instrumented test that drives that composable's state at all** — only the pipeline
beneath it. That gap is why a review found these and a green suite did not.

1. **A new capture could be dismissed as an unchanged crop of the previous one.** `captureLabel`
   cleared `pendingCrop`, `cropSelection`, `readingTable` and `autoAttempted` — but **not**
   `lastRecognisedRegion`, which only `resumeLive` cleared. `captureLabel` is reachable without
   `resumeLive` from the ambiguous, not-found and searching cards (all render after `releaseCapture`
   drops `pendingCrop`). Since both captures propose the same `ScanRegionMapper.expand(scanRegion)`
   rectangle, the new photograph's first *Read table* compared equal to the old one's and was
   skipped — telling the user a picture that had **never been read** would "read the same as before".
   Fixed by clearing it in `captureLabel` too. Note the blast radius was limited because capture #2's
   own automatic pass overwrites the stale value before any user crop; the defect surfaces when that
   pass does not reach the assignment.
2. **`crop_body_after_attempt` was byte-identical to `crop_body`**, so the conditional selecting
   between them was dead and the P4 wording lived entirely in the title. Rewritten to say the thing
   the generic copy cannot — that the box on screen *is* the one already tried.

### The pre-recognition crop was dead for 13 days and still compiled

The 2026-08-17 capture-first pass stopped cropping to the scan overlay before OCR (it cut the basis
header off tall labels and cost **both canaries**), but left the machinery in place with
`region = null` at every call site. Removed: the `region` parameter and crop branch from
`StillImageLoader.loadWithRotation`, `StillImageLoader.load` (no production caller at all), and
`ScanRegionMapper.toPixels` with `PixelRegion`.

**`ScanRegionMapper.expand` is untouched and load-bearing** — it is the rectangle the fast path reads
and the crop screen opens on. Do not confuse the two when reading that file. Both KDocs still argued
*for* cropping before recognition, a position this codebase measured and reversed, and now record why.

`ProductionStillPathBaselineTest` deleted: 2 tests, **zero assertions**, written to measure a baseline
"before production code is touched" for a change that shipped, with a KDoc describing a path that no
longer exists. Not part of the 37-test corpus.

**21 unused strings deleted**, each verified with zero Kotlin references *independently of lint*.
Four `crop_handle_*` labels among them looked like a pending a11y fix and were not: the handles are
drawn on a **Canvas**, so there are no per-handle nodes to label and the selection already carries one
`contentDescription`.

**`ServingSizeParser.parse` has no production caller and was deliberately KEPT** (reason now in its
KDoc). Deleting it deletes a *rule*, not an unused function: "a count with no printed weight is not a
weight mapping" (`1 slice` → null) has no other home, `parseDescriptor` is *required* to accept that
case, and countable-portions §5/§20 makes a false positive there the failure that matters. Do not
"use" it by wiring it in, and do not delete it in the next sweep.

### Verified

JVM **1069/1069** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 87 JUnit XML files
— down from 1075 by exactly the 6 `toPixels` cases removed, so nothing vanished silently). Lint
**exit 0**, advisories **40 → 19**, unused resources **21 → 0**. OCR corpus **39/39** on the emulator
(`RealImageOcrTest` 15, `ProductionStillPipelineTest` 8, `SelectedTableProductionTest` 6,
`EvidencePipelineProductionTest` 10 — that class gained 2 cases this cycle), counted from
`INSTRUMENTATION_STATUS_CODE`, 0 ignored. Those tests **assert** the canary values, so 39/39 is what
clears the `StillImageLoader` change.

**Not done:** no release or AAB build, so **the R8 barriers were not re-checked** — nothing removed
was a barrier-relevant class, but that is an argument, not a measurement. No full instrumented sweep;
the standing whole-suite figure is still the 259/259 that predates several passes. Nothing here has
been seen on physical hardware.

### A git trap worth not repeating

A `git rm` staged early, before the commits were split, was swept into the **first** commit rather
than the dead-code one. Attempting to correct that with `git rebase --onto` **dropped that commit
entirely** and left a detached HEAD — recovered because the branch ref and a stash still held
everything. The misplacement is cosmetic and was left alone with a note in the commit message.
**Stage deletions with the commit they belong to, and do not rewrite history to fix a tidy-ness
problem.**

## Startup hardening: onboarding flash, OCR basis defaults, permission recovery, evidence concurrency (2026-09-04/05) — 1.0.4, READ FIRST

Four release-blocking safety items, scoped down at the owner's direction from a larger 15-section
review to the highest-severity items only: startup state, OCR basis defaults, camera-permission
recovery on both scanners, and `LiveEvidenceBuffer` concurrency. The usage-semantics rewrite
(recent-usage/"Usual portion" model), UI-backdrop fixes and Settings accessibility work were
explicitly deferred and are **not** addressed here. Nothing about the calculation, schema,
migrations, the §10 lookup priority, barcode detection or any OCR *recognition* rule changed.

### Onboarding could flash before Home on a returning user's cold start

`JustTheCarbsNavHost`'s `startDestination` was decided from whatever `AppSettings` value
`setContent {}` first composed with, and `collectAsStateWithLifecycle` needs an `initialValue` —
which was `AppSettings()`, whose `hasSeenOnboarding` defaults to `false`. A returning user could
therefore see Onboarding rendered for a frame or two before the real DataStore value replaced it.

`MainActivity` now holds the splash screen on screen (`SplashScreen.setKeepOnScreenCondition`)
until the first real settings value arrives, via a new sealed `StartupState`
(`Loading`/`Ready(settings)`) and `Flow<AppSettings>.asStartupState()`. While `Loading`, the
composition renders nothing but a neutral black background — under the splash, so it is never
actually seen — rather than either destination, which is the one thing a default-shaped value must
never imply. `startupState` is mirrored into a plain `var` because
`setKeepOnScreenCondition`'s lambda runs outside composition and cannot itself collect a `Flow`.

**A caught lint defect, worth recording because it is a general trap.** The first implementation
called `container.settingsRepository.settings.asStartupState()` directly inside the composable
body — `asStartupState()` applies a `Flow.map`, and lint's `FlowOperatorInvokedInComposition` rule
correctly refuses this: called there, it builds a *new* mapped `Flow` on every recomposition
instead of once. Fixed by wrapping it in `remember(container)`. Caught by a lint run, not by
reading the code — the pattern is easy to write correctly by accident when the operator is hidden
inside a named extension function rather than a visible `.map {}`.

`OnboardingViewModel.complete()` is now `suspend`, guarded by a `Mutex` plus a `completed` flag, so
it returns only once `hasSeenOnboarding = true` is durable — the *NavHost*'s `onGetStarted` handler
now `launch`es a coroutine that calls `complete()` before navigating, with a separate UI-layer
`completing` boolean guarding against a rapid double tap starting two navigation attempts (the
ViewModel's own mutex already makes the DataStore write idempotent; this is the separate guarantee
that "completed once, navigated once" holds even when a second tap lands before the first
coroutine resumes).

### The last unsafe OCR basis default is gone, and manual entry's basis is now genuinely optional

One `NutritionBasis.valueOf(...)` call remained in the codebase: the saved-state label-comparison
handoff in `JustTheCarbsNavHost`, which would have crashed the whole screen on a corrupted or
unrecognised basis string. Replaced with a new pure top-level function,
`parseDetectedLabelReading(carbsText, basisText): Pair<BigDecimal, NutritionBasis>?`, using
`entries.firstOrNull` — the same safe-parsing idiom already used elsewhere in this file (e.g.
`SettingsRepository`'s unrecognised-theme fallback). A parse failure now calls
`ProductViewModel.reportLabelHandoffFailure()`, which renders a dismissible `AlertDialog`
(`label_handoff_failed`) saying the scan result could not be read back — stated, not swallowed,
the same "say it out loud" rule `quickSaveFailed` already follows.

**The more consequential gap was in `LabelScannerScreen`'s *Correct* action.** `CandidateChoice`'s
unknown-basis branch — reached when the parser found a value but could not establish what it was
measured per — hard-coded `onCorrect(candidate.value, NutritionBasis.PER_100_G)`. A pre-selected
grams chip on the following manual-entry screen is indistinguishable from a basis the app actually
read; it is exactly the "grams of what?" guess this app must never make on the user's behalf.

Fixed by threading `NutritionBasis?` (nullable) through the entire callback chain —
`LabelScannerScreen.onCorrectValue` → `LabelCamera` → `ProposalCard`/`AmbiguousCard` →
`CandidateChoice` — down to the one click site, which now calls `onCorrect(candidate.value, null)`.
`ManualEntryUiState.basis` is now `NutritionBasis?` (previously defaulted to `PER_100_G`
unconditionally); `canSave` requires it non-null. `ManualEntryViewModel.start()` distinguishes two
cases by whether `ocrCarbs` is non-blank: **blank** (ordinary Home entry, or an *Edit* action with
no basis) still defaults to grams exactly as before; **non-blank with an unparsable `ocrBasis`**
(a scanned figure genuinely being carried in, basis unresolved) leaves `basis` as `null` rather
than defaulting. `ManualEntryScreen` shows neither g/ml chip selected and an explanatory line
(`manual_basis_unresolved`) in that state, with Save disabled until the user picks one with the
package in hand.

The route-level disambiguation this relies on already existed independently: `Routes.QUICK`'s own
malformed-basis fallback (`navController.navigate(Routes.manual(null, carbsArg, basisArg))`) was
already producing the same "carbs non-blank, basis blank" shape before this pass, so the rule in
`start()` is general rather than specific to the *Correct* action — confirmed by a JVM test using
that exact input shape.

### Camera-permission recovery: both scanners had the identical dead end

Neither `ScannerScreen` nor `LabelScannerScreen` had any way back to the camera once a permission
request had been answered. Both tracked only `hasPermission` (bool) plus `permissionRequested`
(bool), and once `permissionRequested` was true the rationale screen showed only *Enter manually* —
whether the denial was "not this time" (Android would still show its own dialog again) or "never
ask me again" (Android has stopped offering it, and the only remaining path is the app's own
Settings page). *Enter manually* was always present, so the app itself was never lost — but the
*camera* was a dead end from the first "Deny" onward, on both scanners, independently duplicated.

New shared `CameraPermissionState` (`ui/scan/CameraPermissionGate.kt`): `Granted` / `NotRequested`
/ `DeniedCanAskAgain` / `PermanentlyDenied`, derived by a pure `currentPermissionState(granted,
requestedThisVisit, canAskAgain)` — deliberately Android-framework-free (no `Activity` parameter)
so it is plain-JVM-testable without Robolectric, which this codebase does not use. The caller reads
`Activity.shouldShowRequestPermissionRationale(CAMERA)` and passes the boolean in.

`rememberCameraPermissionController()` requests once automatically on first composition (§9's
"ask in context" rule, unchanged) and adds an `ON_RESUME` recheck via `LifecycleEventObserver` —
new to this codebase — which is what recognises a permission granted in the app's own Settings
page after the user returns: `ContextCompat.checkSelfPermission` only changes because the OS
changed it while the screen was backgrounded, and `ON_RESUME` is exactly the signal that a
backgrounding-and-return just happened. Neither
`ActivityResultContracts.RequestPermission()`'s callback nor an ordinary recomposition would
observe this transition on their own.

`CameraPermissionRationale` (shared, replacing `ScannerScreen`'s `PermissionRationale` and
`LabelScannerScreen`'s near-identical `LabelPermissionRationale`) shows *Allow camera* for
`NotRequested`/`DeniedCanAskAgain` and *Open Settings* (`Intent(Settings
.ACTION_APPLICATION_DETAILS_SETTINGS)`, this app's own package URI) for `PermanentlyDenied`, with
different body text (`permission_settings_body`) explaining why in the latter case. *Enter
manually* is present in every state, unchanged.

### `LiveEvidenceBuffer` had a real, not hypothetical, concurrent-access hazard

`record()` (called from the analyzer's frame callback, marshalled onto the main thread) and
`clear()` (called from Compose on retake/screen exit) mutate a plain `ArrayDeque`; `stableConsensus()`
/`asEvidence()` are read from `LabelScannerScreen`'s `saveScope.launch { withContext(Dispatchers.IO)
{ ... SelectedTableResolution.resolve(..., liveEvidence = liveEvidence.asEvidence(...)) } }` — a
background-dispatcher read racing main-thread writes on a data structure `ArrayDeque` explicitly
documents as not thread-safe. This needed no unusual timing to reach: a live frame lands roughly
every 30-100 ms during normal aiming, and the still-recognition coroutine reads the buffer on every
capture.

Every method touching `observations` is now wrapped in `synchronized(lock)`. That alone stops
corruption but does nothing about a *different* hazard: a live frame from an abandoned attempt (a
Retake mid-recognition) or a different package the camera swept past could still silently
corroborate the capture being evaluated now, simply by being recent enough. `Observation` now
carries a `sessionId` (default `0L`, so every pre-existing caller and test is unaffected);
`record()` is stamped with `LabelScannerScreen`'s existing `captureSession.get()` — already bumped
on dispose, retake and every new capture — and `stableConsensus(nowMs, sessionId)`/`asEvidence(nowMs,
sessionId)` only ever consider observations from the session being asked about. The still-recognition
coroutine captures its `session` value on the main thread *before* switching to `Dispatchers.IO`,
so it always asks about the session that started it, never whichever session happens to be current
by the time the background read runs.

### Verified

JVM full suite, `--rerun-tasks`: **1715/1715** (0 failures, 0 errors, 0 skipped, counted from 170
JUnit XML files). New coverage: `StartupStateTest` (2), `CameraPermissionStateTest` (6),
`LabelHandoffParsingTest` (9, the extracted `parseDetectedLabelReading`), 8 new
`ManualEntryViewModelTest` cases (unresolved-basis blocks save, chip selection unblocks it, a known
OCR basis preselects, ordinary Home entry is unaffected, a blank-carbs blank-basis case still
defaults, an invalid basis string never becomes grams, idempotent re-`start()` does not resurrect a
default, `save()` itself refuses with a null basis even called directly), 4 new
`OnboardingViewModelTest` cases (idempotent `complete()`, a counting-`DataStore`-decorator proving
exactly one write across three calls, two concurrent callers both returning only after the write
lands), and 10 new `LiveEvidenceBufferTest` cases (session-scoped consensus, session isolation
against a stale-session pool, `asEvidence` inheriting the same scoping, `clear()` forgetting every
session, two concurrency-stress tests hammering `record`/`stableConsensus`/`snapshot`/`clear` from
multiple threads with a 10 s deadline, and a same-thread "queued callback right after a snapshot"
case proving the earlier snapshot is unaffected by a later write).

Lint: **0 errors, 23 warnings** — unchanged baseline. **One `lintDebug` run hit a transient internal
crash** (`ExperimentalDetector`, `FirExpressionStub` cast failure analyzing `LiveEvidenceBuffer.kt`)
that reproduces the general pattern this file already records under "A lint crash that is a lint
bug, not a code defect" — a `--rerun-tasks` retry with **zero code changes** came back clean. Do not
chase this as a code error if it recurs; it is a lint-internal issue, not a defect in the analyzed
file. `git diff --check` clean (one CRLF-normalization notice only). `assembleDebug` and
`compileDebugAndroidTestKotlin` both succeed.

**Connected OCR corpus** (`RealImageOcrTest` + `ProductionStillPipelineTest` +
`SelectedTableProductionTest` + `EvidencePipelineProductionTest`, 39 tests) on the `carbscan`
emulator: **10 failures**. **A `git worktree` control at clean `266338b`, same emulator, same
session, measured the identical 10 by name** — compared with a sorted-list diff, zero differences.
None of this pass touches OCR recognition or parsing code (`LiveEvidenceBuffer`'s change is a
concurrency wrapper, functionally inert for the single-threaded default-session usage every
existing fixture and caller exercises), so this is the expected result, not a regression.

**Two of the ten failures show a wrong value, and that is stated plainly rather than smoothed
over**: `stokbroodStillReadsFortySixThroughTheProductionStillPath` /
`stokbroodStillResolvesThroughTheEvidencePipeline` / `noFixtureGainsAConfidentWrongValueThrough…`
show the emulator's ML Kit reading `6.4` where the fixture prints `46`, and
`kinderStillReadsItsPerPieceRelationship` shows `3` where it states `6.7`. The parser is not at
fault — these tests run the real ML Kit recognizer against the fixture bitmap on *this* emulator,
and the wrong value originates entirely in what the recognizer reports, which the parser then
correctly interprets. Same class of degradation this file already records elsewhere (`Koolhydraten`
→ `nlhioonorate`, `Glucides` → `Gucides`), and this exact corpus is recorded as **39/39 on real
hardware** — so this reads as further evidence the emulator's camera pipeline is worse than real
optics on these specific photographs, not evidence the app's OCR safety logic is unsound. It has
not been re-confirmed against this diff on real hardware, which is the open gate.

### NOT verified, and this is the gate

**No physical device was attached.** Everything above is JVM plus the `carbscan` emulator
(`ro.kernel.qemu=1`, `ro.hardware=ranchu`, `ro.build.characteristics=emulator`), whose virtual
camera cannot exercise the real capture/permission/OCR flow end to end. Specifically unverified:
the onboarding-flash fix on a real cold start, both camera-permission recovery flows (temporary
denial → re-request; permanent denial → Settings → return with grant) on both scanners, the
`LiveEvidenceBuffer` session-boundary behaviour under a real capture/retake sequence, and whether
the standing 39/39 real-hardware OCR figure still holds against this diff (it should — no
recognition or parsing code changed — but that is an argument, not a measurement).
`docs/manual-qa.md` §35 is the gate.

## Pair-symmetry fix + the connected gate attributed (2026-09-04, eighteenth session) — READ FIRST

A review pass over the fourth-test evidence (`docs/Scan Evıdence 4th test/`, thirteen captures
`160320`–`160740` on a Samsung SM-S928B) and Codex's audit
(`.audits/architectural-analysis-2026-09-04.md`). One real defect fixed, and the audit's headline P0
**measured and materially corrected**. Nothing about the calculation, the schema, migrations, the §10
lookup priority, barcode detection or any OCR *recognition* rule changed — no threshold moved and no
parser rule was relaxed. Still `versionCode 4`, nothing built as a release, nothing uploaded.

### The audit's P0 "12 of 33 connected tests fail" is real, and 9 of the 12 are NOT this tree

Codex ran the connected OCR suite on the `carbscan` emulator and reported 33 tests / 12 failures,
recommending it be treated as a release blocker. The count reproduces exactly. **What the audit did
not do is run the control**, and the control is what decides whether those failures are regressions:

| run | tests | failures |
|---|---|---|
| working tree, before this pass | 33 | **12** |
| `git worktree` at clean `47ad5d1`, same emulator, same session | 33 | **9** |
| working tree, after this pass | 33 | **9** — all 9 identical by name to the control |

So **9 of the 12 were pre-existing** and are the emulator ML Kit degradation this file already
records (`Koolhydraten` → `nlhioonorate`, `Glucides` → `Gucides`; the corpus is recorded as 39/39 on
real hardware). Only **3** were new, and all three failed with `expected row provenance` — a stale
test contract, never a wrong value.

**Do not read the remaining 9 as a regression from this tree, and do not "fix" the fixtures against
emulator output.** Equally, do not read 9 as green: the corpus is still unverified against this diff
on hardware, and saying so is an argument rather than a measurement until a device is attached.

### The stale contract was genuinely unpassable, not merely outdated

`RealImageOcrTest.assertRowNamesTheTotalNotSugars` required `CandidateProvenance.FromRow`. The
tabular path now emits `FromDeclaration`, and **no production code constructs `FromRow` at all** —
verified by search, the only remaining reference being one diagnostics branch. So four cases could
not pass whatever the parser read.

Migrated rather than deleted, and **without weakening what it proves**. Both types carry the same
evidence (the source rows' text), so both substantive claims are asserted on either — and the second
is **new**, because it can no longer be inherited:

1. the source rows name a carbohydrate term;
2. **no source row names a child term.** `FromRow` proved "the total and its child occupy different
   rows" implicitly, by carrying one row. A declaration may span several, so the child exclusion has
   to be *stated*. Checked against `NutritionTerminology.exclusionTerms` — the parser's own child
   vocabulary — so the assertion cannot drift from what the parser treats as a child.

`theProseReaderIsNeverConsultedForAReadableTable` had the same defect and is now stated as a positive
check against both tabular types, deliberately not as `!is FromProseSpan`: a future third provenance
type must be considered rather than silently satisfying a negative test.

### The real defect: a separatorless pair refused one member and offered the other

`ScaleAmbiguity` searched for the paired value only among elements **to the right of** the candidate.
So on a two-column row the left cell saw its pair and was refused, and the right cell saw nothing,
returned `Unsupported`, and was then admitted by its declared serving basis. The bundles record the
asymmetry in the app's own words — `20260904-160639-565`:

```
suppressed '46' @x=1161: a common rescaling of '46' and '12' is equally consistent …
offered    '12' -> 12 g / serving | selectable (awaiting a tap)
```

One number refused **because of** the other, and the other offered. Three captures in this one
session (`160639-565` `46`/`12`, `160501-961` `159`/`18`, `160532-812` `15`/`38g`), so it is a
property of the rule rather than of one photograph. A common rescaling is a property of the **pair**
and is symmetric by construction — this file's own stated rule says "a **pair** of values that move
together" — and the implementation disagreed with the rule it documents.

**The rightward bound's stated reason was misattributed.** It was added so "the nutrient's own name"
could not pair with its own value, making a single-column `41g` read as ambiguous. That consequence
is real; the cause is not the direction. What excludes a *name* is `looksLikeAValueCell`, which
requires a **leading digit** — so `Koolhydraten`, `Vetten`, `E471` and `Omega-3` are all excluded from
either side. The direction only removed the rightmost cell's ability to see its own pair. Both
controls are pinned (`a lone value beside a nutrient name is unsupported, not ambiguous`, `a nutrient
name containing digits is not a pair`).

The paired sibling is now chosen by **nearest horizontal gap** rather than element order, since with
both sides admitted "first" can name the far column. That decides only how the refusal *reads* in a
bundle, never whether it happens.

**No value is repaired.** `46` never becomes `4.6` and `12` never becomes `1.2`; a withheld figure
routes to focused entry with the frozen photograph, the highlighted row and the basis preserved.

**A declared serving basis does not rescue a demonstrated ambiguity**, and that is the one behaviour
change worth stating plainly. A serving sentence the label printed says which quantity a figure is
measured per; it says nothing about where the figure's decimal point is. The Korean sauce control is
untouched — its `6 g / 18 g serving` is a *lone* separatorless value (`Unsupported`), never a
demonstrated pair — and the whole JVM suite confirms it.

### Verified

JVM **1688/1688** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 168 JUnit XML files
— up from 1678). Lint **exit 0, 0 errors, 23 warnings** — unchanged baseline. Connected OCR suite
**33 tests, 9 failures, all 9 pre-existing at clean `47ad5d1`** on the same emulator in the same
session, compared by name.

`EighteenthSessionPairFixtures` is generated by `tools/derive-session-fixtures.py` from each bundle's
own `diagnostics.txt`, and the tool asserts the parsed element count against the count the device
declared (160, 179, 167 — all matching). The geometry is the phone's; no box was invented.

**Negative control**, restored and re-verified green: restoring the `it.box.left >
candidateElement.box.left` bound fails **6** — three unit cases in `ScalePairSymmetryTest` and all
three real captures in `EighteenthSessionPairRegressionTest`.

### What was reviewed and deliberately NOT changed

- **`160359-876`'s cross-column "contradiction" is correct, not a false veto.** The Fanta reads
  `Koolhydraten: 0.5g 13g` where `13g` is the 250 ml column's printed `1,3`. The ratio check reports
  `row 26.0 vs table 2.6` and blocks automatic advancement — which is right. The value shown (`0.5`,
  correct) still reaches the user, because its own token carries a separator. Working as designed.
- **The audit's other P0 — the Hellmann's `1,3` → `13` proposal — is a documented, measured
  trade-off**, not an oversight: suppressing it also suppresses two correct separatorless integers
  (`57 g`, `35 g`). The route that actually closes it is a second physical observation, which is the
  audit's own P1 and is a feature, not a fix. Left for that work rather than traded away here.
- **P1 (`SECOND_OBSERVATION_PASS` unwired), P2s (semantic model as single source of truth, `Resolved`
  evidence grades), the replay-harness consolidation and the held-out optical benchmark** are all
  correctly identified and are architecture/validation work, not a release-blocker fix. Attempting
  them in this pass would have been a large refactor of the trust core with no device to measure it
  against.

### NOT verified, and this is the gate

**No physical device was attached** — everything above is JVM plus the `carbscan` emulator, whose
virtual camera cannot produce a nutrition table, so the automatic accept path was not exercised end
to end and **no physical-QA checkbox was ticked**. Nothing in this pass has been seen on hardware.

The three captures the fix is about were **already routing to recovery or focused entry** on the
device; what changed is that the offered figure is now withheld there too. Confirming that on
hardware — that `12`, `18` and `38` no longer appear as one-tap choices, and that focused entry opens
with the basis preserved — is the open gate, alongside §32 and §§26–31.

**A leftover control worktree** may remain at `%TEMP%\jtc-control`; it is deregistered from git
(`git worktree list` shows only the main tree) and Windows path-length refused its deletion. It is
inert — delete it with an explorer or `rmdir /s` if it is in the way.

## UI refresh: colourful chrome, opaque bars (2026-09-04) — still 1.0.3 / versionCode 4, READ FIRST

A UI/UX pass, plus a window-configuration fix the owner asked for. **Nothing about the
calculation, the schema, migrations, the §10 lookup priority, barcode detection or any OCR
recognition rule changed** — no threshold moved and no parser rule was relaxed. Still
`versionCode 4`, nothing built as a release, nothing uploaded.

Spec: `docs/superpowers/specs/2026-09-03-ui-refresh-design.md`.
Plan: `docs/superpowers/plans/2026-09-03-ui-refresh.md`.
Branch: **`ui-refresh-2026-09-03`**, off `main` — `main` is untouched.

### The system bars: why the obvious fix would have shipped broken

The app drew edge-to-edge, so the cream background showed behind both system bars. The
instinctive fix — set `android:statusBarColor`/`navigationBarColor` to black and drop
`enableEdgeToEdge()` — **would have looked correct on the emulator and failed on a current
phone.** `targetSdk` is 36, and from Android 15 (API 35) the platform enforces edge-to-edge and
treats both attributes as **deprecated no-ops**.

`SystemBarScrim` therefore paints opaque black bands in Compose, drawn once at the root of
`JustTheCarbsTheme` *after* `content()` — before it, every screen's own background would cover it
and the fix would silently do nothing. Bar icons are now pinned **light unconditionally**: the
ground behind them is a known constant, so the old theme-tracking inversion would render them
black-on-black in Light mode. That is a real behaviour change — in Light mode the bars were
previously cream with dark icons.

**`OnboardingScreen` was the one screen in the app with no inset padding at all**, so its Skip
button clipped under the now-opaque bar. My own spec asserted "every screen already applies its
own inset padding"; that was wrong, and this is the correction. Padding goes on the content
`Column`, not the root `Box`, so the coloured background still bleeds to the edges. All 12
`*Screen.kt` files were then audited programmatically — onboarding was the only gap.

### The luminance rule is what lets the app be colourful

Six accents were added (teal, violet, green, magenta, indigo, amber). The safety argument is not
"we were careful", it is arithmetic:

> Result red `#D42F2F` has relative luminance **0.162**. Every accent is *darker* — teal 0.142,
> green 0.159, magenta 0.124, violet 0.098, amber 0.098, indigo 0.083 — so it **recedes behind**
> the carbohydrate figure instead of competing with it.

`AccentRecessionTest` pins that over the whole palette in both schemes; `ContrastTest` pins every
accent at ≥4.5:1 on every surface it is drawn on, computed from the live tokens.

**The rule earned its place before it shipped.** The dark accents were first drafted as ordinary
bright tints (`#5EEAD4`, `#C4B5FD`, `#86EFAC` …) — the values any dark theme reaches for.
Computed, **all six failed**: `#5EEAD4` measures 0.660 against the dark result red's 0.366,
nearly twice as bright as the number it must not out-shout. Nothing about those swatches looked
wrong. The shipped dark values are the brightest of each hue that still recedes, found by search.

**`DESIGN.md`'s colour rationale was rewritten**, not patched. It said the palette was
near-monochrome *because* colour competes with the result; that is no longer what the app does,
and leaving it would have the next reader treat a measured guarantee as a style preference. Its
core-role table was also **already stale independently of this pass** — it listed the original
handoff `#2F8FE0`/`#FF5C5C` rather than the measured `#1B6FBF`/`#D42F2F` that ship.

### Eleven hand-rolled top bars became one

Measured before the change: **zero** uses of `TopAppBar`. Eleven screens each built their own
title `Row`, and they had drifted — `titleLarge` on Manual Entry and Settings, `titleMedium` on
Meal and Search, `headlineMedium` on Home. `JtcTopBar` replaces four of them (Home keeps its own:
its title is a brand wordmark and it has no back affordance, so folding it in would need a
`destination == HOME` special case inside a component whose purpose is having none).

### Home already knew the answer and was whispering it

`rememberedCarbs()` already resolved the exact carbohydrate figure for every recent product. It
was rendered as `bodyMedium` in `onSurfaceVariant` inside a single grey line — supporting text at
the same weight as the portion label. For a returning user the number they came for was on
screen, correct, and styled as metadata. It now has its own right-aligned column in the result
red under a `CARBS` label, still following `settings.resultStyle` so Recents and the calculator
cannot disagree.

### The scan wait: the staged progress list was measured and NOT built

The plan asked for staged progress during recognition. **Step 1 was a discovery step and it came
back negative**, which is a successful outcome of that step rather than a shortfall:
`LabelAnalyzer.analyzeStillRetaining` takes a **single `onComplete` callback**, and the scanner
has exactly three `captureState` transitions. Between shutter and result there is **one**
observable transition and no honest way to report thirds of it. Inventing stages on a timer would
claim knowledge the app does not have, on the screen whose output someone doses insulin from.

What *was* built is free: the **frozen capture now replaces the live preview** while recognition
runs. Previously the user watched a live feed of wherever the phone had drifted for the
323–1974 ms ML Kit takes, while the app read a photo already taken. Loaded through Coil so the
8 MP JPEG decodes off-thread and does not compete for CPU with the pass being waited on.

**That change is NOT committed.** `LabelScannerScreen.kt` carries ~325 lines of a prior session's
uncommitted OCR work, against ~45 of mine; committing it would bury substantial safety-critical
code under a UI commit and misattribute it. It builds, it is verified on the device, and it
travels with whoever commits that session. Do not `git checkout` that file without reading this.

### Evidence retention 12 to 35 (owner request)

`ScanEvidenceRecorder.MAX_RETAINED`. A §32-style session runs three captures across several
packages — nine for that gate alone — and at 12 the earliest bundles were pruned before the
session ended. The cost is disk: each capture keeps an untouched 8 MP `capture.jpg` plus a decoded
`passA.png`, so 35 is ~350 MB in `cacheDir`. Acceptable **only** because this is debug-only.

### Verified

JVM **1525/1525** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up
from 1519 by the six new palette tests). Lint **exit 0**, 0 errors, 23 findings, **0 unused
resources**; every finding is in a file this pass never touched.

Instrumented, counted from Gradle's progress lines: `HomeScreenTest` **23/23** (up from 21),
`MealScreenTest` 19/19, `SearchScreenTest` 29/29, `SettingsScreenTest` 4/4, `JtcTopBarTest` 3/3 —
**75/75 in one combined run**, 0 skipped, 0 failed. `MealScreenTest` and `SearchScreenTest` were
each run three times, per this file's standing warning about the soft-keyboard artefact.

Release **builds**, and its R8 barriers were re-checked on that build: `ScanEvidenceRecorder` and
`OcrDiagnosticsLogger` map to `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`
and `ScanTrace` are absent entirely; `ScaleAmbiguity`, `RecoveryCandidates`, `CarbCandidate` and
`UnitMarkerFilter` are retained as real classes. **The retention bump cannot reach a shipped
build.**

**Negative control:** setting both `AccentPalette.kt`'s `LightTeal` and its test mirror to a
plausible bright teal (`#5EEAD4`) fails `AccentRecessionTest` with *"teal luminance 0.660 is not
below the result red's 0.162"*. Restored byte-identically.

### Six defects that only a device screenshot found

Every one passed the whole suite. Nothing in it looks at where a control sits relative to a
screen edge, a corner radius, or a system bar.

| Defect | Where |
|---|---|
| Skip clipped under the status bar | `OnboardingScreen` — the only screen with no insets |
| Accent spine 8dp from the screen edge, read as a clipped artefact | `JtcTopBar` |
| Spine filled the row height, ends landing arbitrarily | `JtcTopBar` |
| Back arrow tinted with the destination accent — on Settings a muted neutral, making the only way off the screen its faintest element | `JtcTopBar` |
| No space beneath the bar; "Appearance" touched it | `JtcTopBar` |
| Card spine flush against the 18dp corner radius | `RecentCard` |

Plus a real ordering bug: `SettingsScreen` applied `navigationBarsPadding()` **after**
`verticalScroll`, so it padded the scrolling *content* rather than the viewport and the last row
came to rest under the navigation bar. Same trap this file already records for the portion zone's
fade modifier. Audited every screen — Settings was the only instance.

**Four of those six are defects in the spec I wrote**, not in the implementation. The
implementers built what was specified.

### NOT verified

**Nothing in this pass has been seen on physical hardware.** Everything above is JVM plus the
`carbscan` emulator (API 36), including all the screenshots.

**The nine-photograph OCR corpus was not re-run.** No OCR rule changed, and the corpus fails
17/39 on this emulator at clean HEAD anyway — an emulator run would measure the emulator. That
leaves it unverified against this diff, and saying so is an argument rather than a measurement.

**Colour appearance is verified by eye; only contrast and recession are verified by test.** The
opaque bars were checked in Light and Dark, gesture and three-button navigation, on **one**
device at **one** API level. Behaviour on API 29–34 — where the XML attributes still apply and
the scrim is belt-and-braces — has not been compared on real hardware.

## Surgical OCR correction pass (2026-09-03, eleventh pass) — still 1.0.3 / versionCode 4, READ FIRST

Three defects found by auditing the tenth pass's own work, each reproduced by a test that **fails on
the pre-fix tree** before the fix. Nothing about the calculation, the schema, migrations, the §10
lookup priority, barcode detection or any OCR *recognition* rule changed — no threshold moved and no
parser rule was relaxed. Every fix adds a refusal, moves a translation to a boundary, or deletes a
duplicated policy. Still `versionCode 4`, nothing built as a release, nothing uploaded, **no commit**.

HEAD is `c57aee0`, `main`, unchanged: no commits, no resets, no history rewrites. The pass was made
on the inherited dirty worktree and **five files carrying pre-existing changes were deliberately not
touched** — `RecoveryCandidates.kt`, `ScaleAmbiguity.kt`, `strings.xml`, `docs/manual-qa.md` and
`CLAUDE.md`. (That property was verified by mtime at the time. It no longer holds for the two doc
files: a later session edited them, and this section is that transcription.)

### P1a — the scale rule could be bypassed by corroboration

`ReadingEligibility` tested corroboration **first** and returned eligible on it outright, on the
stated ground that agreement between two distinct recognition runs settles scale "by a route that is
not scale-invariant". **That ground is false for both routes this app has, and the falsity is
arithmetic, not a judgement call.** `CrossColumnRatioCheck` compares a *ratio*, which is unchanged
when both of its terms are scaled together; `DISTINCT_OCR_AGREEMENT` compares two recognitions of the
same pixels, which can lose the same separator twice. Neither observes absolute scale, so neither can
vouch for it.

A demonstrated `ScaleAmbiguity.Verdict.Ambiguous` is therefore checked **before** corroboration. That
is a refusal added, never one removed. Second half of the same defect: `mayAdvanceVerified` never
consulted eligibility at all, so the gate that decides *terminal* advancement was not asking the
question — and its `document` is now a required parameter rather than a defaulted one, so a caller
cannot silently omit the evidence the rule needs.

### The `41` conflict resolves on evidence already in the codebase — nothing is superseded

The obvious worry about the reordering is that it breaks the proven `41 g / 100 ml` integer case,
which is admitted precisely *because* two distinct runs agreed. It does not, and the reason is a
distinction `ScaleAmbiguity` already draws:

```
FORTYONE scale=Unsupported(41g, "no paired value…")
FORTYONE verification=DISTINCT_OCR_AGREEMENT
FORTYONE action=AUTO_ADVANCE
```

A **lone** separatorless integer is `Unsupported`; only a separatorless **pair** is `Ambiguous`. The
new ordering refuses *demonstrated* ambiguity, so corroboration still admits 41. What no longer
passes is a separatorless pair that two runs happen to agree on — which is the truffle label, and is
the point. **QA rows 29.12 and 30.18 stand unchanged**; no superseding note was written and **no
heuristic was invented** to separate the two cases.

### P1b — Strategy B's geometry travelled into a full-frame world unchanged

`SELECTED_REGION_OCR` recognises a **crop** of the source bitmap, so its element boxes and its
`width`/`height` are crop-local. Every consumer treated them as full-frame. The measured signature is
unambiguous: the highlighted box was short by **exactly the crop origin**.

`SelectedRegionCrop.toSourceSpace` had existed with tests since the crop pass was written — the
translation was never missing, it simply was not called. `RecognitionEvidence` now carries `crop`
alongside a `sourceSpaceGeometry` accessor, and that is **the single translation boundary**: the
`document` stays crop-local, every consumer states which space it wants, and `VerificationScreen`
translates once, at presentation. For every pass but Strategy B `crop` is null and the geometry
returns unchanged, so nothing else moves.

### P2 — the evidence document was the unfiltered one

The filtered report was paired with the **whole-frame** document, so a neighbouring panel's `62 g`
was visible to stages reasoning about "this table". `SelectedTableReader.Result` now exposes the
filtered `document` and `SelectedTableResolution` pairs the two — one functional line each.

### One `when`, replacing a policy that was written twice

`LabelScannerScreen` restated the presentation policy imperatively alongside `ScanPresentationDecision`.
It is now a single `when (decision)`, with `CROP_FALLBACK` added as an explicit `Action` and
`releasesCapture` made exhaustive. This is the same structural argument the eighth and ninth sessions
made twice over: **a rule no test can reach is a rule that can be silently reverted**, and a policy
duplicated between a pure object and a composable that binds a camera is reachable in only one of the
two places.

### The null-basis `PER_100_G` was recommended for removal and is KEPT — measured unreachable

An audit recommended deleting the guard. Measured instead: `LabelReading.Confident` carries
`require(candidate.basis != null)`, so the state is **unconstructible** and the guard is already
unreachable. No production change was made, and the invariant is pinned by `NullBasisProposalTest`
(4 cases) rather than the guard being deleted. **Deleting a guard on the strength of an invariant
held in another file is how that invariant's absence becomes invisible** — the same reasoning that
keeps `ServingSizeParser.parse` alive with no production caller.

### Verified

JVM **1481/1481** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 137 JUnit XML files
— up from 1441). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0** — baseline unchanged.
`assembleDebug` successful; `androidTest` Kotlin compiles. `git diff --check` clean (LF/CRLF notices
only).

Instrumented on the `carbscan` AVD: **134 green** — `UnverifiedProposalLifecycleTest` 11,
`AssistedReadingScreenTest` 27, `ProductScreenTest` 32, `QuickCalculationScreenTest` 14,
`HomeScreenTest` 21, `MealScreenTest` 19, migrations 10.

Debug APK **89,848,583 bytes**, SHA-256
`f39656454686aed3c35c2f34d2348847d2decde9c0444304aac3520d4a45d72a`, `versionCode=4` / `1.0.3-debug`;
permissions unchanged (CAMERA, INTERNET, ACCESS_NETWORK_STATE). No AAB, no release build, no upload.

**Pre-fix failures, which is what makes the coverage non-vacuous:** P1a `ScaleInvarianceTest` 3/8
failed (now 9/9) plus 2 further failures once the advance gate was reached; P1b
`StrategyBProvenanceTest` 2/8 failed with the box short by exactly the crop origin (now 8/8); P2
`FilteredEvidenceDocumentTest` 2/4 failed with the other panel's `62 g` visible (now 4/4).
`ScanTransitionTest` (6) and `ScanEvidenceDiagnosticsTest` (7) are new and green.

**Three negative controls**, each restored byte-identically and re-verified green: corroboration-first
ordering restored fails **4**; the advance gate left unguarded fails **3**; the filtered document
mis-paired fails **2**.

### NOT verified, and this is the gate

**Everything above is JVM plus the `carbscan` emulator** (`ro.kernel.qemu=1`, `ro.hardware=ranchu`),
whose virtual camera cannot produce a nutrition table — so the *automatic* accept path was not
exercised end to end and **no physical-QA checkbox was ticked**.

**The nine-photograph OCR corpus was NOT re-run against this diff**, deliberately: it fails 17/39 on
this emulator at clean `c57aee0` too (CLAUDE.md records **39/39 on real hardware**), so an emulator
run would measure the emulator rather than these changes. That leaves the corpus unverified against
this diff, and saying so is an argument rather than a measurement until a device is attached.

`docs/manual-qa.md` **§32** is the gate — three green-drink, three white-table and three red-label
captures on hardware, cold and warm timings reported separately, automatic-correct /
confirmed-correct / focused-entry / wrong-value counted separately, and **zero** wrong values through
any route. **Pin the APK hash above to that device run.** §§26–31 remain open alongside it.

## One eligibility decision (2026-09-03, tenth pass) — still 1.0.3 / versionCode 4, READ FIRST

Analysis pass over the **correct** ninth-session archive (`scan-evidence (10).zip`, SHA-256
`3391e49c…f382c43`, verified). The nine bundles already committed under `docs/Scan Evidence 03-09/`
were checked file-by-file against it and are **byte-identical**, so no fixture rested on the stale
archive. Retention and export were not investigated or changed.

Nothing about the calculation, the schema, migrations, the §10 lookup priority, barcode detection or
any OCR *recognition* rule changed. No threshold moved and no parser rule was relaxed. Still
`versionCode 4`, nothing built as a release, nothing uploaded.

### The green drink's `0.5`: it was the HEADER, not the value or the confidence

`084951-833` records `resolver.verdict: Nothing` where the white table records `NeedsVerification`,
with both showing `RECOVERY`. The bundles predate `strategyB.txt`, so this was traced through the
real resolver rather than read off the status text — and the cause is not the one the text suggests.

| capture | header as recognised | columns | statedBasis | Pass A resolver |
|---|---|---|---|---|
| green `084951-833` | `PER: 100 m \| 25d6` — the `l` lost | **0** | null | `Nothing` |
| white `085019-213` | `… per 100g` | 1 (`PER_100_G`) | `PER_100_G` | `NeedsVerification` |

**The green drink's value cell was never the problem.** Pass A reads `0.5g` — unit and separator
intact — so `UnitAccompanimentPolicy` never declined it. What Pass A could not do is *place* it: ML
Kit read `100 ml` as `100` + `m`, and `m` is not a unit spelling (**and must never become one — a
bare `m` is metres, and that list is shared with `ServingSizeParser`**). So `ColumnClassifier`
resolved zero columns, `0.5` was rejected with `no column`, no pass was confident, and
`EvidenceResolver` returned `Nothing` from its `confident.isEmpty()` branch.

Measured on the real classifier: with `m`, `columns=0`; with `ml`, `columns=1`
(`PER_100_ML @ x=1163.5`) and the interpreter reads `Confident 0.5/PER_100_ML` — the device's
Strategy B verdict exactly. So Strategy B genuinely established the value, its unit, the
carbohydrate row and the `/100 ml` basis, and the outcome is **deterministic**:
`NeedsVerification` / `CONFIRM_ON_CAPTURE`, pinned by an exact assertion. "Proposal or honest
refusal" is not an acceptable expected result and is not what the test allows.

### A tap says which row, never which decimal scale

Recovery offered `12 g / 100 g` for a package printing `7,2 g`. The old rule was an explicit
asymmetry — the automatic path refused `ScaleAmbiguity.Verdict.Unsupported`, recovery refused only
`Ambiguous` — justified by "a human is pointing at a number they can see". That is half right, and
the wrong half is the release blocker: **a tap establishes which row the user meant and nothing
about whether the recognizer read the digits correctly.** A recovery choice is still a value
proposed by the app.

**The naive symmetry was measured and is wrong.** Refusing `Unsupported` in recovery deletes the
Korean sauce's legitimate `6 g / 18 g serving` — the two are indistinguishable to `ScaleAmbiguity`,
both bare separatorless integers with nothing to pair against.

What separates them is already a type in this codebase, `CarbBasis`:

| | value | basis | how established |
|---|---|---|---|
| Korean sauce | `6` | `PerQuantity(18 g serving)` | the label **printed** `Serv. size: 1 Tbsp (18 g)` |
| red Lidl | `12` | `PerHundred` | **inferred** from a column the app resolved |

New `ReadingEligibility` (pure) is the single decision both surfaces consult, in evidence-strength
order: **corroboration → the token's own separator → a declared serving basis**, else refuse.

**The corroboration-first ordering is load-bearing and cost one wrong attempt.** Putting `Ambiguous`
first broke `a verified reading is never made ambiguous` — `20260902-131357-353` reads `41g`,
integer-like, and is **correct**, agreed by distinct runs. A reading corroborated by a route that is
not scale-invariant has its scale settled before this rule is asked.

Measured blast radius across **all 45 committed fixtures**: exactly **one** candidate is now
refused — the eighth session's `redLabelTwelve`, `'12g' -> 12 g / 100 g`. Every Korean-sauce offer
and every `Established` offer is untouched.

### The scanner veto is now JVM-testable — the blind spot is closed

Reverting the ninth session's veto previously failed **zero** JVM tests, and the reason was
structural: it was a local `val` inside a composable that binds a camera, unreachable from the JVM —
the same shape as the eighth session's P0 (an anonymous `else` in the same file). **A rule no test
can reach is a rule that can be silently reverted.**

`ScanPresentationDecision` (pure) is that rule as a value, and the scanner asks it rather than
restating it. Its `Action` is recorded in the bundle beside the branch that ran, so a divergence
between the pure decision and the imperative UI prints in evidence instead of being argued about.

### Three negative controls, each restored byte-identically and re-verified green

| control disabled | failures | what it proves |
|---|---|---|
| recovery reverted to `Ambiguous`-only | **2** — incl. `the opening recovery list must be empty, was [12 g / 100 g]` | the leak is real and the fix closes it |
| veto reverted to `mayAdvance` | **1** — `expected:<CONFIRM_ON_CAPTURE> but was:<RECOVERY>` | the blind spot is genuinely closed |
| declared-serving admission removed | **10** across 6 classes, 4 sessions | the Korean sauce rests on exactly that distinction |

### Verified

JVM **1441/1441** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 131 JUnit XML
files — up from 1417). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0** — unchanged
from the ninth-session baseline. Instrumented sources compile; **136 green** on the `carbscan` AVD:
`UnverifiedProposalLifecycleTest` 11, `AssistedReadingScreenTest` 27, `ProductScreenTest` 32,
`QuickCalculationScreenTest` 14, `HomeScreenTest` 21, `MealScreenTest` 19, `ScanPolishScreenTest` 2,
migrations 10.

Debug APK **89,846,131 bytes**, SHA-256
`48adb3a71614a10dd7d477e7ebdc167ec74aca2482c7bd3f794aad6be237224f`, `versionCode=4` /
`1.0.3-debug` read from the APK with `aapt2 dump badging`; permissions unchanged.

Minified release **builds** (66,885,330 bytes) and its R8 barriers were re-checked on that build:
`ScanEvidenceRecorder`/`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`;
`ScanEvidenceExport`/`OcrDiagnosticsReport`/`ScanTrace`/`ZipIntegrity` absent entirely;
`ScaleAmbiguity`, `RecoveryCandidates`, `DisputedCandidates`, `CrossColumnRatioCheck`,
`NutrientRowSegments`, `CarbCandidate`, `UnitMarkerFilter`, `CandidateProvenance` retained as real
classes. Release manifest: CAMERA, INTERNET, ACCESS_NETWORK_STATE, **no FileProvider**.

**`ReadingEligibility` and `ScanPresentationDecision` read as removed/absent, and both are the
inlined-not-dropped case — checked behaviourally, not assumed.** `ReadingEligibility$Verdict`,
`$Verdict$Eligible`, `$Verdict$Refused` and `ScanPresentationDecision$Action` all survive as real
classes, and **both eligibility reason strings are present in the shipped release DEX**. Do not read
those markers as a safety rule shipping disabled.

### NOT verified, and this is the gate

**No physical device was attached** (`ro.kernel.qemu=1`, `ro.hardware=ranchu`,
`ro.build.characteristics=emulator`) — everything above is JVM plus the `carbscan` emulator, whose
virtual camera cannot produce a nutrition table, so the *automatic* accept path was not exercised
end to end. **No device timing was measured**, and the ninth session's unexplained 323–1974 ms ML Kit
spread is still unexplained.

**The verdict is NOT release-ready.** The supplied bundles were produced by the previous APK, so they
establish what was wrong and never that it is fixed. `docs/manual-qa.md` **§32** is the gate: three
green-drink, three white-table and three red-label captures, cold and warm timings reported
separately, with automatic-correct / confirmed-correct / focused-entry / wrong-value counted
**separately** — and **zero** wrong values shown or offered through any route. §§26–31 remain open
alongside it.

## Discarded correct readings (2026-09-03, ninth phone session) — still 1.0.3 / versionCode 4, READ FIRST

The ninth session (`docs/Scan Evidence 03-09/`, nine bundles `084935`–`085128`, with
`Screen_Recording_20260903_085136`) produced **no wrong value at all** — the eighth session's `12`
proposal did not recur, which is the P0 fix holding on the device. It exposed the opposite failure:
on three captures the app **held a correct reading and showed the user nothing**.

Nothing about the calculation, the schema, migrations, the §10 lookup priority, barcode detection or
any OCR *recognition* rule changed. No threshold moved and no parser rule was relaxed. Still
`versionCode 4`, nothing built as a release, nothing uploaded.

### A correct reading was discarded by the automatic veto — and it is NOT a regression

| bundle | prints | Strategy B read | app showed |
|---|---|---|---|
| `084951-833` | `0,5 g / 100 ml` | `Confident 0.5/PER_100_ML` | recovery, `0.5g` suppressed |
| `085019-213` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | recovery, `2.8` suppressed |
| `085032-269` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | recovery, `2.8` suppressed |

**No stage was individually wrong.** Pass A reconstructed the white table's row correctly
(`[TOTAL_CARBOHYDRATE] 'Koolhydraten, waarvan 2.8 9'`) and resolved its `per 100g` column — the
printed `g` came back as a `9`, so `UnitAccompanimentPolicy` declined a unit-less value. **That
refusal is correct and is unchanged**: the identical misread hit the *fat* row of the same capture
(`Vetten, waarvan 4,8 9`), so it is a property of the recognition, not something a
carbohydrate-specific rule could or should repair. Strategy B then read the row cleanly and the
resolver correctly returned `NeedsVerification` — *one pass read this, please check it*.

The scanner's automatic veto read `automatic && !AutomaticScanAdvance.mayAdvance(outcome)`, and
everything it declined **skipped the entire outcome `when`** (`if (!declined) when …`). `mayAdvance`
answers `false` for `NeedsVerification` — the right answer to "may this skip the confirmation", the
wrong answer to "may this be shown at all". So the correct reading was thrown away before any branch
could render it, and recovery then re-derived candidates from *Pass A*, where the value had lost its
unit, and suppressed it.

**Proven pre-existing**: `git show c57aee0` has the identical `val declined` line, the identical
`if (!declined)` veto and a byte-identical `mayAdvance`. Do not record this as caused by the
eighth-session patch.

### The fix: widen what may be *proposed*, never what may be *accepted*

`AutomaticScanAdvance.confidentReading(outcome)` returns the confident reading from **either**
outcome type that can carry one, so one scale rule governs both. `mayPresentAutomatically` replaces
the veto's `mayAdvance` call and asks *"is there anything here worth showing on the photograph?"*.

**The safety invariant holds by construction**: `mayAdvanceVerified` still delegates to `mayAdvance`,
which refuses `NeedsVerification`, so that outcome can never reach `Presentation.Advance`. Pinned by
`an uncorroborated reading may be proposed but never advanced`.

**The `NeedsVerification` branch now applies the scale rule too.** It became reachable from the
*automatic* path in this pass, and without that check the red label's `Confident 12.0` — unverified,
no decimal separator, nothing on its row to pair with — would be proposed automatically. It is not:
the verdict is `Unsupported`, so it routes to focused entry with the digits withheld.

Ambiguity, conflict and a failed read **still decline to the crop screen**, because for those the
rectangle genuinely is the user's lever. A confident reading is not improved by cropping.

### The evidence bundle could not answer the question it was collected for

`diagnostics.txt` records **Pass A's** document. Strategy B's verdict was recorded and **its document
never was** — so a session where the two passes disagreed could not be replayed, which is exactly
this session's shape. Diagnosis required reconstructing Strategy B's document from Pass A's plus the
one difference the verdict implied (`NinthSessionStrategyBDocuments`, asserted against the real
interpreter so it cannot pass for the wrong reason). `strategyB.txt` is now written in the same
format as `diagnostics.txt`, so the next session replays with the existing tooling.

### The stale ZIP was a supply mistake, not a defect — do not "fix" the recorder for it

`scan-evidence (9)(2).zip` was byte-identical to `(9)(1)` (SHA-256 `5d352537…33a4f7`), holding the
**eighth** session's bundles. `prune()` keeps `MAX_RETAINED - 1` = 11 captures, and the fresh export
contained exactly the nine new bundles and none of the eight old ones — so retention and export both
worked. The first archive was simply exported before the new captures existed.

### Verified

JVM **1417/1417** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 129 JUnit XML files
— up from 1395). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0**. Instrumented on the
`carbscan` AVD: **136 green** — `UnverifiedProposalLifecycleTest` 11 (3 new),
`AssistedReadingScreenTest` 27, `ProductScreenTest` 32, `HomeScreenTest` 21, `MealScreenTest` 19,
`QuickCalculationScreenTest` 14, migrations 10, `ScanPolishScreenTest` 2.

**Two negative controls**, both restored byte-identically and re-verified green: making
`confidentReading` return null for `NeedsVerification` fails exactly the 2 tests that assert the fix
and nothing else; reverting the scanner veto to `mayAdvance` fails **nothing in the entire JVM
suite**, which is the same blind spot that let the eighth session's P0 ship and is why the
instrumented lifecycle test exists.

### The OCR corpus fails 17/39 on this emulator — measured, not assumed

A clean-`c57aee0` control build (APK SHA-256 `73cd91f0…d5c`, forced with `--rerun-tasks` because
Gradle reported `compileDebugKotlin UP-TO-DATE` after the revert) fails the **identical 17 by name**,
compared programmatically with zero differences. The emulator's ML Kit reads the committed
photographs far worse than a device does. CLAUDE.md records this corpus as **39/39 on real
hardware**. Not a regression, and not device evidence either.

### NOT verified, and this is the gate

**No physical device was attached** (`ro.kernel.qemu=1`, `ro.hardware=ranchu`), so the §31c
reliability gate — three captures per clear label — has **not been run**, and nothing in this pass
has been seen on hardware.

**Device timing is unexplained and was not re-measured.** This session's bundles record ML Kit at
**323–1974 ms** and `scan` at **487–2458 ms**, against the eighth session's 379–638 ms on the same
device and app version. Two captures exceeded 2 s. Recorded rather than assumed away; §31e is where
it is measured.

`docs/manual-qa.md` **§31** is the gate. Rows **31.1–31.6** decide whether the discarded readings
actually reach the user, **31.9/31.10** whether the red label's `12` stays out, and **31c** is the
reliability table that decides release status. §§26–30 remain open alongside it.

## The blind confirmation (2026-09-02, eighth phone session) — still 1.0.3 / versionCode 4, READ FIRST

The eighth session (`docs/Scan Evidence 5th test/`, eight captures `212902`–`213026`, with
`Screen_Recording_20260902_213037`) produced the first **wrong value offered for one-tap
confirmation with nothing on screen to check it against**. Two captures correct, five
recovery/conflict/NotFound, one wrong proposal.

Nothing about the calculation, the schema, migrations, the §10 lookup priority, barcode detection or
any OCR *recognition* rule changed. No threshold moved and no parser rule was relaxed — both fixes
add a refusal or move a screen. Still `versionCode 4`, nothing built as a release, nothing uploaded.

### `7,2` read as `12`, and two independent defects let it reach the user

A red Lidl label prints `7,2 g / 100 g`. `20260902-213005-691` records the whole failure in the
app's own words:

```
automatic-verification: NONE — only one recognition run (PASS_A)
strategy B      : RAN_NO_READING
scale evidence  : established (no paired value in this clause to share a scale with)
final UI action : CONFIRM
```

**Defect 1 — absence of evidence recorded as establishment.** `ScaleAmbiguity.check` could only
*demonstrate* ambiguity from a **pair** of separatorless values, and returned
`Established("no paired value…")` for a lone one — a sentence that says *no evidence* while the type
says *evidence*. `AutomaticScanAdvance.mayConfirm` then read `!is Ambiguous` as permission. The
recognizer had fused the `7,` into the Spanish nutrient word (`carbono2g` on two other captures of
the same package), leaving one bare number on the row, **so the worse the recognition, the more
confident the gate became.**

The verdict is now three-valued — `Established` / `Ambiguous` / **`Unsupported`** — and `mayConfirm`
requires *positive* evidence (`is Established`) rather than absence of ambiguity. `Unsupported` is
**not a refusal on its own**: a second recognition run agreeing still confirms, which is what keeps
the proven `41 g / 100 ml` integer case working.

**The asymmetry with `RecoveryCandidates` is deliberate and measured.** Recovery still suppresses
only `Ambiguous`, because there a human is pointing at a number they can see. Extending it to
`Unsupported` was measured across every committed session fixture and **deletes the Korean sauce's
`6 g / 18 g serving`** (third, fourth *and* fifth sessions) — a control that must keep working. Do
not "make the two consistent" without re-running that measurement.

**Defect 2 — the proposal was drawn over the live camera.** `readSelectedTable`'s `Resolved` branch
called `releaseCapture(captured)` as its **first statement**, before choosing between advancing,
confirming and recovering. So the confirmation branch inherited a recycled bitmap and fell back to
the ordinary `ProposalCard` over the live preview. The recording (≈00:01:12) shows `12 g / 100 g`
with a blue *Confirm* button over an **empty wooden table** — the package already moved away. "The
user still had to confirm" is no defence when there is nothing to confirm against.

The capture is now released **only on terminal transitions** (automatic advance, explicit
acceptance, retake, close). An unverified `Resolved` reading routes to the existing
`VerificationScreen` — the same question `NeedsVerification` already asked — which gained an
**enlarged close-up of the candidate's own row**, a highlight on the full photograph, and the row's
recognised text (`From: Hidratos de carbono 12g`). A 1684x3648 capture fitted to a phone viewport
renders an 80 px row at a few pixels; without the close-up the photograph is present but not useful.

`AutomaticScanAdvance.Presentation` (`Advance` / `ConfirmOnCapture` / `Recover` / `NotApplicable`)
makes the branch a value, so the invariant **only `Advance` is terminal** is unit-testable. The P0
was an anonymous `else` inside a composable that binds a real camera, which is exactly why nothing
reached it.

### P1: preprocessing was measured and is NOT shipped — do not retry it from the source

`7.2` survives in **none** of the four red-label recognitions (`12g`, `724`, twice `carbono2g`); the
only `7` tokens anywhere are the postcode `DE-74167` and a batch code. So no parser rule can derive
it honestly, and the only legitimate route was a better image.

Seven variants x four captures = **28 measurements** on device through the production
`StillImageLoader` and real ML Kit (`RedLabelAcquisitionExperimentTest`). **`7.2` was recovered zero
times, and every variant that changed an outcome made it worse:**

| variant | capture | outcome |
|---|---|---|
| baseline | all four | `NotFound` (correct — the value is not there) |
| upscale-1.5x | 213014-298 | **`Confident 72.0`** — 10x the printed figure |
| upscale-2x | 213026-546 | **`Confident 72.0`** — 10x |
| grayscale | 213014-298 | **`Confident 12.0`** |
| grayscale-upscale-2x | 213014-298 / 213026-546 | **`Confident 29.0`** / **`Confident 72.0`** |
| grayscale-contrast, contrast-1.4x | all four | `NotFound` |

Upscaling resamples the decimal separator away — the same `(g)` -> `(9)` mechanism
`SelectedRegionRecognizer` already refuses to rely on. Three of seven variants turn an honest
`NotFound` into a confident tenfold error, which is the worst outcome this app can produce. **The
shipped answer for this label is the P0 safety fix plus focused entry.** Re-run that class before
proposing preprocessing again.

### Verified

JVM **1395/1395** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 126 JUnit XML
files — up from 1363). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0**. Debug APK
**89,826,028 bytes**, SHA-256 `C8FC97D953A58375B58802A35ECF77519D0F79A2BBA291656A510A42A51808B1`,
`versionCode=4` / `1.0.3-debug` read from the APK with `aapt2 dump badging`; permissions unchanged.

Instrumented on the `carbscan` AVD: **133 green** — `AssistedReadingScreenTest`,
`UnverifiedProposalLifecycleTest` (8, new), `ScanPolishScreenTest`, `QuickCalculationScreenTest`,
`ProductScreenTest` (83 together), plus migrations, `MealScreenTest` and `HomeScreenTest` (50).

Minified release **builds** (66,885,330 bytes) and its R8 barriers were re-checked on that build:
`ScanEvidenceRecorder`/`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`;
`ScanEvidenceExport`/`OcrDiagnosticsReport`/`ScanTrace`/`ZipIntegrity` absent entirely;
`ScaleAmbiguity`, `DisputedCandidates`, `RecoveryCandidates`, `CrossColumnRatioCheck`,
`NutrientRowSegments`, `CarbCandidate`, `UnitMarkerFilter`, `CandidateProvenance` retained as real
classes. `AutomaticScanAdvance` reads as absent and is again the **inlined-not-dropped** case —
checked behaviourally, not assumed: `AutomaticScanAdvance$Presentation` survives as a real class and
the four new verification strings are present in the shipped release APK.

**Two negative controls**, both restored green afterwards: reverting `mayConfirm`'s polarity fails
exactly the P0 test; making the unpaired case report `Established` again fails 3.

### The OCR corpus fails 17/39 on this emulator, and that is NOT this pass

Run on the `carbscan` AVD, `RealImageOcrTest` + `ProductionStillPipelineTest` +
`SelectedTableProductionTest` + `EvidencePipelineProductionTest` give **39 tests, 17 failures**.

**A `git worktree` control at clean `c57aee0` fails the identical 17 by name** — compared
programmatically, the sets are identical with zero differences. The emulator's ML Kit reads the
committed photographs far worse than a device does (`Koolhydraten` → `nlhioonorate`, `Glucides` →
`Gucides`), so the corpus measures the emulator here, not the parser. CLAUDE.md records this corpus
as **39/39 on real hardware**. Do not read these failures as a regression, and do not "fix" the
fixtures against emulator output.

### NOT verified, and this is the gate

**Nothing in this pass has been seen on physical hardware.** No device was attached; everything above
is JVM plus the `carbscan` emulator, and the emulator's virtual camera cannot produce a nutrition
table, so the *automatic* accept path was not exercised end to end.

**No device timing was measured.** The 379–638 ms figures quoted from the eighth session's bundles
are capture-time only and predate this pass.

`docs/manual-qa.md` **§30** is the gate. Row **30.1** (no capture may ever display `12 g / 100 g`),
**30.4** (every proposal on the frozen photograph) and **30.6** (8 of 10 captures reaching a correct
`7.2` within one confirmation, *or* an honest statement that reliability remains inadequate) are the
rows that decide whether this is closed. §§26–29 remain open alongside it.

## The merged carbohydrate clause (2026-09-02, seventh phone session) — still 1.0.3 / versionCode 4, READ FIRST

The seventh phone session (`docs/Scan Evidence 02-09 4th test/`, ten captures `141440`–`141703`
plus the older retained `140819`, with `Screen_Recording_20260902_141716`) proved the sixth
session's scale-safety work holds on the device — **the truffle label never displayed or offered
`89`** — and exposed the opposite failure: the label was safe and **unrecoverable**.

Nothing about the calculation, the schema, migrations, the §10 lookup priority, barcode detection,
`ScaleAmbiguity`, cross-run dispute suppression, unit accompaniment or child-nutrient protection
changed. No OCR pass was added. Still `versionCode 4`, nothing built as a release, nothing uploaded.

### One mistake, in four places: classifying a merged row when the user tapped inside a clause

On this package ML Kit puts the whole declaration on **one** reconstructed row:

```
Zig lkg2%; Nolhydraten/Glucides Kohlenhydrate 8,9 a, 1.3q(<19; waarvan suikers/dant sucres/tavon Žucker
                                ^^^ total clause ^^^^^^^^^^^^^  ^^^^^^^^^^ child clause ^^^^^^^^^^^^
```

The row therefore classifies `CARBOHYDRATE_CHILD` — **correctly, and that is unchanged**. Four
separate places then read the whole row as one unit, each measured rather than argued:

| layer | behaviour | consequence |
|---|---|---|
| `NutrientRowSegments:116` | the merged-row guard discards the row because a **trailing** language-variant clause (`sucres/tavon Žucker`, no number) carries no value | a valid total clause is thrown away |
| `RecoveryCandidates.isChildRowAt` / `candidatesOn` | classify the whole row | every tap answered "This looks like sugars or fibre" — including on `Kohlenhydrate` itself |
| `AssistedReadingScreen:530` | `fruitlessTap` set only when the tap was **not** a child row | the flag could never become true |
| `FocusedAmountEntry:93` | requires a `TOTAL_CARBOHYDRATE` row | returned null, so *Type it in* was disabled |

Measured before the fix on both captures: **every** element on the row returned `isChildRow=true`,
`recovery of()` empty, `focused entry NULL`. That is the whole dead end, reproduced in the JVM.

### The fix is confined to the tap path, and that boundary is the safety argument

A tap carries a **horizontal position**. The automatic path has no such thing, and the two printed
clauses are separated by exactly that. `NutrientRowSegments.totalCarbohydrateClause` answers a
strictly weaker question than `totalCarbohydrateSegment` — *which printed clause is the finger on?*
rather than *may this row be read as a total row?* — establishes no reading, promotes no row and
produces no value. Being wrong about it costs a tap; being wrong about the other costs a
carbohydrate figure.

**Automatic classification is untouched**: both captures still classify `CARBOHYDRATE_CHILD`,
neither produces a confident reading, `89` stays suppressed and `8.9` is never manufactured from the
separatorless capture. Every suppression rule still runs on whatever the tap reaches — which is why
both captures still offer **nothing** and route to focused entry instead.

**Two subtleties that cost a wrong first attempt each, both worth not rediscovering:**

1. **The clause boundary must be the printed nutrient word, not the matched span's start.** The
   greedy span walk matched the child term over a span beginning at the damaged unit glyph `a,`
   (x=900) — which is the printed `g` belonging to the total's own value `8,9` at x=851. Using the
   span's start put the value's own unit outside its own clause. The naming element (`suikers/dant`,
   x=1251) is the boundary a person can actually see.
2. **The greedy walk cannot answer this question at all.** On
   `Koolhydraten 12 g waarvan suikers 3 g` it matches the four-element span
   `koolhydraten 12 g waarvan suikers` as one **child** term, consuming the carbohydrate word — right
   for its own question, useless for this one. The clause locator asks which **element** names which
   nutrient, independently of how spans group. It takes the **first** total-naming element, not
   `singleOrNull`, because `141703` names the nutrient twice in two languages
   (`Nolhydraten/Glucides` and `Kohlenhydrate`) — one declaration, not two clauses — and refuses
   outright when a child is named *before* the total (the merged Croatian/German shape).

### The 1432 ms parse was a real defect, and the counters found it

`141642-529` took **2142 ms** with **1432 ms in parse**, against 501–864 ms total and 55–199 ms
parse for every other capture. Measured as work rather than wall clock, that 289-element document
did **51,792** `normalize` calls against **2,295** for its 262-element sibling — **22.5x the work
for a 1.10x larger document**.

The 2026-09-01 quadratic shape had survived in two functions: `ProseNutritionReader.longestTermAt`
and `termLengthAt` walk **every vocabulary term at every token position** and normalized the term
inside that loop, though the vocabulary is a compile-time constant. Both now use a cached
`NutritionTerminology.termWords`, the sibling of the existing term cache.

**51,792 → 2,060**, so the slow capture now does slightly *less* work than its sibling, consistent
with document size. The prose path is the only one affected, which is why `141703` (no prose
fallback) was always fast. Pinned by **invocation-count** assertions — a wall-clock assertion in the
standard suite is either too loose to catch anything or flaky.

**A memoization of `flatten` was tried first and is NOT the fix** — it moved 51,792 to 51,160. It is
kept because it is correct and cheap, but do not mistake it for the cause.

### An honest negative-control result

The clause bound in `candidatesOn` — which stops a merged-row tap offering the *sugars* value —
**fails no test when removed**, and that is recorded rather than dressed up. On every merged row
reachable in practice something else refuses the value first: with clean units and a header the
prose reader reads the row correctly and recovery is never reached; without units accompaniment
declines it; with units and no separator `ScaleAmbiguity` withholds it. The bound stays as defence
in depth because the rules in front of it are not there to enforce clause separation.
`MergedRowTapBoundTest` pins the boundary itself — where it falls, which elements are inside it, and
that a child-clause tap is still refused.

**`ScaleAmbiguity` pairs across the clause boundary on a merged row** (it uses
`totalCarbohydrateSegment`, null there, so it falls back to the whole row and pairs the carbohydrate
value with the sugars value). Left alone deliberately: it errs towards **withholding**, which is the
safe direction, and it is on the automatic path this pass must not touch.

### Measured outcomes

| capture | before | after |
|---|---|---|
| 141528-720 / 141558-254 / 141626-947 | cracker `72/100 g`, drink `41/100 ml` auto | unchanged |
| **141642-529** | RECOVERY, no proposal, **every tap refused as sugars**, *Type it in* disabled | taps on `8`, `13g` honoured; **focused entry under `PER_100_ML`**; still no `89`, still no `8.9` |
| **141703-456** | as above, including a tap on `Kohlenhydrate` itself | taps on `8,9`, its damaged unit and `Kohlenhydrate` honoured; **focused entry under `PER_100_ML`** |

### Verified

JVM **1363/1363** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 122 JUnit XML
files — up from 1352). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0**. Debug APK
**89,519,401 bytes**, SHA-256 `73cd91f095312827239a499bc9df463ab34b75f6e45a7efa9ef078dad0193d5c`,
`versionCode=4` / `1.0.3-debug` read from the APK with `aapt2 dump badging`; permissions unchanged.
Instrumented sources **compile**. Minified release **builds** and its R8 barriers re-checked on that
build — `ScanEvidenceRecorder`/`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`,
`ScanEvidenceExport`/`OcrDiagnosticsReport`/`ScanTrace`/`ZipIntegrity` absent, and
`ScaleAmbiguity`, `DisputedCandidates`, `NutrientRowSegments`, `RecoveryCandidates`,
`CrossColumnRatioCheck`, `CarbCandidate` all retained as real classes. `FocusedAmountEntry` again
reads as `R8$$REMOVED$$CLASS$$` and is again the **inlined-not-dropped** case — checked
behaviourally: `FocusedAmountEntry$Target` survives as a real class and the focused-entry strings
are present in the shipped APK.

**Eight negative controls**, seven failing for their intended reason and each restored green
afterwards; the eighth is the defence-in-depth bound described above.

### NOT verified, and this is the gate

**No instrumented run** — `adb devices` was empty, so the nine-photograph OCR corpus has not been run
against these changes. The sources compile; that is not the same thing.

**No device timing was measured this pass.** The 51,792 → 2,060 figure is a JVM work count, which is
the right instrument for the defect but is not a phone measurement. **The recording does not label
cold and warm runs separately**, so nothing here settles that question either.

`docs/manual-qa.md` **§29** is the gate. Rows **29.3–29.9** decide whether the dead end is actually
closed, **29.1/29.2** whether `89` and an invented `8.9` stay out, and **29.21–29.24** are the
cold/warm timings that must be recorded separately. §§26–28 remain open alongside it.

## Recovery basis integrity (2026-09-02, fifth phone session) — still 1.0.3 / versionCode 4

The fifth phone session (`docs/Scan Evidence 02-09 2nd test/`, six complete bundles) proved the
fourth session's automatic-verification work holds — the misread never reappeared — and exposed the
**opposite** failure: a reading that *was* independently verified and still did not reach the user,
while a fabricated one did.

Nothing about the calculation, the schema, migrations, the §10 lookup priority, barcode detection or
any confidence threshold changed. No parser rule was relaxed; both fixes add a refusal or route an
existing verdict correctly. Still `versionCode 4`, nothing built as a release candidate, nothing
uploaded.

### The defect: a damaged header let one cell wear another column's basis

`20260902-103936-423`. The packet prints `72,0 g / 100 g` and `22,5 g / portion`. ML Kit read the
per-100 header as **`1009`**, so `ColumnClassifier` resolved no per-100 column — only the serving
column at x=1411 and a reference-percent column at x=1584.

`RecoveryCandidates` binds a cell to its nearest column within `LOOSE_COLUMN_FRACTION` (0.22) of the
document width — **370 px** on this 1684-wide capture. `72,0` sits at x≈1082, so the serving column
was 329 px away and *inside* the tolerance. The printed per-100 figure bound to it and recovery
offered **`72 g / serving`**, one tap from the calculator.

**No stage was individually wrong.** The tolerance is generous on purpose so photographic skew does
not detach a cell from its own column, and the serving column genuinely was the nearest one. The
defect is that *nearest surviving column* was treated as *this cell's column* — the same thing only
while every column is intact.

The rule added is structural and names nothing product-specific:

> A column may claim a cell only if no **other value cell on the same row** sits closer to that
> column's centre.

A table column is the set of cells printed under it; when two cells on one row both reach the same
column, at most one is in it. On a healthy table each cell's own column is nearest and nothing
changes. Here `22,5` is 20 px from the serving column and `72,0` is 329 px, so `22,5` claims it and
`72,0` — whose column OCR destroyed — is left honestly unresolved and suppressed rather than
relabelled. Recovery was more permissive than the parser it is the fallback for; it no longer is.

**A wrong first fix, worth recording.** I initially blamed the document-level serving-declaration
fallback in `basisFor` and narrowed it to "no value columns at all". That broke both sauce captures,
and measuring showed why it was wrong twice over: the damaged cracker prints no `Serv. size`
sentence, so `ServingDeclaration.of` returns **null** there and that branch never ran. It was never
the cause. **The synthetic fixture that convinced me otherwise was my own construction** — I wrote
`per portion` as its header, which `ServingDeclaration` reads as a declaration, so the fixture
modelled a different failure from the measured one. The fixture now uses the device's own
`Nutritional value portion` and carries a precondition asserting `ServingDeclaration.of` is null,
without which it would pass for the wrong reason.

### The contradiction: a verified reading that reached nobody

The same bundle records `SELECTED_REGION_OCR → Confident 72.0/PER_100_G`,
`automatic-verification: CROSS_COLUMN (support=5, median=0.309, candidate=0.313)` — and
`final UI action : RECOVERY`.

`EvidenceResolver` rule 4 says a value only the re-recognition found "may propose but not decide",
which produces `NeedsVerification`; `AutomaticScanAdvance.mayAdvance` refuses that, so `declined`
was set *before* the `when` and the outcome fell to recovery without its own branch ever running.

Rule 4's reasoning is about **the absence of corroboration**, not about which pass produced the
reading. When corroboration exists the premise is gone. New **rule 5**: a lone re-recognition
resolves when `CrossColumnRatioCheck` supports it — the *other nutrient rows of the same table*,
whose serving-to-per-100 ratio is a property of the serving size and therefore identical on every
row. A misread digit cannot also have misread four other rows consistently in the same direction.

`NotEnoughEvidence` (any single-value-column label) keeps rule 4 unchanged and is still proposed for
confirmation; a **contradiction** is not rescued by this at all.

### Verification now belongs to the candidate being promoted

`verify(evidence)` judged `confident.first()` — an artefact of list-construction order — while the
scanner promotes whatever the resolver resolved. Those coincide today only because a disagreement
makes the resolver return `Conflicted`, a guarantee held in a different file. It now refuses
outright when confident passes disagree, and asks the structural route against each confident pass's
own document richest-first, so the pass that *can* answer is consulted rather than the first one.

### Two diagnostics corrections

- **`weight=none` was true and misleading.** It reports the *column header's* serving descriptor,
  legitimately absent on a US linear panel, while the `18 g` the user sees comes from
  `ServingDeclaration` reading `Serv. size: 1 Tbsp (18 g)` off the panel. Two different objects, one
  printed. Now `header-weight=` and it points at the new block.
- **`selection.txt` gained `=== recovery proposal ===`** — every offered choice with its displayed
  value, basis, provenance and derivation, and every *suppressed* number with the rule that removed
  it. A suppressed number previously left no trace, which is what made the fabricated
  `72 g / serving` hard to attribute.

### The inline-DV `HEADER` rows are terminology, not a defect — do not rewrite the parser for them

Several US-panel nutrient sentences log as `HEADER` because `RowClassifier` types a row `HEADER`
when it names reference-intake vocabulary, and a US panel prints `% DV` *inside* each clause. The
name follows the vocabulary and is imprecise.

**The production consequence is nil, and that is pinned rather than argued.** What a `HEADER` row is
used for is `ColumnClassifier`, and `InlinePercentAnnotation` already stops a clause becoming a
column — measured at **1** legitimate reference-percent column on both sauce captures, against the
eight phantoms that defect once produced. Renaming the classification would change a value read by
`CrossColumnRatioCheck`, `DeclarationBoundary`, `UnitAccompanimentPolicy` and `ColumnClassifier`,
all of which behave correctly here. `InlineDvHeaderTerminologyTest` pins the behaviour.

### The permission rationale named one scanner

`permission_title` is shared by `ScannerScreen` and `LabelScannerScreen` and said "Camera access is
needed to scan a barcode" — shown verbatim to someone who had tapped *Scan nutrition label*. Now
"Camera access is needed to scan barcodes and nutrition labels." No permission changed.

### Measured outcomes across the six captures

| capture | before | after |
|---|---|---|
| 103854-549 | drink `0.5/100 ml`, one tap | unchanged |
| 103906-452 | drink `0.5/100 ml`, one tap | unchanged |
| 103926-226 | cracker `72/100 g`, `SKIPPED_CROSS_COLUMN_VERIFIED`, auto | unchanged — 0 extra OCR passes |
| **103936-423** | **RECOVERY offering `72 g / serving`** | **AUTO_ADVANCE `72 g / 100 g`**; recovery offers only `22.5 g / serving` |
| 103949-880 | sauce → `6 g / 18 g serving` | unchanged |
| 104006-838 | sauce → `6 g / 18 g serving` | unchanged |

### Verified

JVM **1303/1303** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 114 JUnit XML
files — up from 1277). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0**. Instrumented
sources **compile**. Debug APK **89,798,001 bytes**, SHA-256
`4f7c6b679d247e12d97d44b0d803ba74720da1dd9b35a194fa6d0aea64e7ed5e`, `versionCode=4` /
`1.0.3-debug` read from the APK with `aapt2 dump badging`. Permissions unchanged.

Minified release **builds** (66,868,638 bytes) and its R8 barriers were re-checked on that build:
`ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`,
`OcrDiagnosticsReport`, `ScanTrace` and `ZipIntegrity` absent entirely; `UnitMarkerFilter`,
`CandidateProvenance`, `CarbCandidate`, `RecoveryCandidates`, `ServingDeclaration`,
`CrossColumnRatioCheck`, `InlinePercentAnnotation`, `DeclarationBoundary` and `CarbReading` retained
as real classes. `AutomaticVerification` and `FocusedAmountEntry` again read as absent /
`R8$$REMOVED$$CLASS$$` and are again the **inlined-not-dropped** case — checked behaviourally, not
assumed: `AutomaticVerification$Route` and `$Verdict` survive as real classes, `CROSS_COLUMN` and
`DISTINCT_OCR_AGREEMENT` are present as string constants in the shipped DEX, and the focused-entry
and new permission strings are both in the release APK.

**Three negative controls**, each restored and the suite re-verified green afterwards:

| control disabled | failures |
|---|---|
| verified selected-region promotion | 2 (`FifthSessionRegressionTest`, both P0 guards) |
| candidate-specific basis provenance | 1 (`the per-hundred value is never offered as a serving figure`) |
| contradiction finality | 1 (`the contradicted misread is never offered by recovery`) |

*(One control's restore reported a hash mismatch. It is line endings only — Python's read/write
cycle wrote LF where the untracked file had none tracked; content was verified identical by grep and
the file matches its siblings. `core.autocrlf=true` normalizes on commit.)*

### NOT verified, and this is the gate

**No instrumented run** — no device or emulator was attached (`adb devices` empty), so the
nine-photograph OCR corpus has not been run against these changes. The instrumented sources compile;
that is not the same thing.

**Nothing here has been seen on physical hardware, and no timing was measured this pass.**
`docs/manual-qa.md` **§27** is the gate. Row **27.1** (no capture may ever display `72 g / serving`)
is what decides whether the fabrication is actually closed, and **27.8** decides the contradiction.
§26 remains open alongside it.

## Verified automatic advancement (2026-09-02, fourth phone session) — still 1.0.3 / versionCode 4

The fourth phone session (`docs/Scan Evidence 02-09/`, nine complete bundles) produced the first
**confident-wrong that reached the user with no confirmation step at all**. The cracker prints
`72,0 g`; ML Kit read `12,0.g`; Quick Calculation displayed `12 g carbs / 100 g`.

Automatic accuracy was 4 of 5. Performance was already good and is preserved — median 516 ms, eight
of nine ≤579 ms, parser 46–90 ms.

Nothing about the calculation, the schema, migrations, the §10 lookup priority or barcode detection
changed. Still `versionCode 4`, nothing built as a release candidate, nothing uploaded.

### The defect was not the misread. It was that one clean parse counted as verification

`12,0.g` is a well-formed number on the correctly classified total-carbohydrate row under a correctly
resolved `PER_100_G` column. **Every content-based guard in the app passes it**, and every one of
them is right to: a parser that refused `12` would refuse every legitimate 12 g label too.

The mistake was upstream of all of them. `SKIPPED_PASS_A_STRONG` skipped the second recognition when
both views of Pass A read confidently and agreed — and both views of Pass A are two *parses* of one
*recognition* over the same characters. That condition describes a **clean parse**, and a clean parse
of a misread character is exactly as clean as a clean parse of a correct one. It removed the last
opportunity to disagree, and the app then advanced with no confirmation.

This is the same error `EvidenceSource.recognitionRun` was introduced to prevent (grated cheese,
`2.09`) arriving through a different door: that one *claimed* corroboration and was caught; this one
claimed only that a second look was not worth the wait, which sounds weaker and had the same effect.

**Three questions are now three separate things**, and conflating any two is what produced this:

| question | answered by | vocabulary |
|---|---|---|
| is it structurally plausible? | the parser | `Confident` / `Ambiguous` / `NotFound` |
| is it independently verified? | `AutomaticVerification` | `CROSS_COLUMN` / `DISTINCT_OCR_AGREEMENT` / `NONE` |
| what does the UI do? | the scanner | `AUTO_ADVANCE` / `CONFIRM` / `RECOVERY` |

**An unverified reading is not an error and is not discarded.** It reaches the user through the
proposal card — one tap, which is what the app did before the fast path existed. Only a verified
reading skips that tap. Refusing unverified readings outright would trade a rare wrong answer for a
constant one.

### The table contradicts the misread, and that is free evidence

A nutrition table states every nutrient twice, so the serving-to-per-100 ratio is a property of the
serving size and is **the same on every row**. On `085542-213`:

```
Energie    135 / 432 = 0.313
Fat        3.4 / 11  = 0.309
Saturates  0.3 / 1.1 = 0.273
Sugars     0.7 / 2.3 = 0.304
Fibre      0.9 / 2.8 = 0.321     median 0.309, four independent rows
Carb      22.5 / 12  = 1.875     <- the misread, six times out
Carb      22.5 / 72  = 0.3125    <- what the label prints
```

`CrossColumnRatioCheck` needs no idea what a carbohydrate value should look like. It asks only
whether this row behaves like every other row on the same label. Thresholds are named and tested at
their boundaries: `MIN_SUPPORTING_ROWS = 3`, `SUPPORT_TOLERANCE = 0.20`, `CANDIDATE_TOLERANCE = 0.25`.
The median is taken in **log space**, because a ratio is multiplicative and `0.5x` and `2x` must be
equally far out.

**It validates or vetoes. It never calculates, replaces, corrects or ranks.** Given the 1.875 row it
reports a conflict; it does not divide 22.5 by 0.31 to "recover" 72. Deriving the value from the
ratio would manufacture a figure no OCR pass ever read — worse than the bug, because it would be
invisible.

**Three measurement traps found while building it**, each of which produced zero coherent pairs and
looked like the check simply not working:

1. **Two `PER_SERVING` columns.** A multilingual header prints `portion` and `portie/` on two
   recognised rows, so the same printed column is emitted twice a few pixels apart. `singleOrNull`
   refused the check on exactly the labels needing it. They are collapsed when they agree on
   position; a genuine disagreement still yields no verification.
2. **Punctuation between number and unit.** `72,0.g` and `2,8` + separate `g` are both ordinary
   printed forms. The ratio check needs the *magnitude*, not a verdict on how cleanly the unit
   printed — that verdict is `CarbUnitAccompaniment`'s job on the answer path, and duplicating it
   here would let a damaged glyph silently remove a supporting row.
3. **`0,38g67`** — the salt cell fused with the next column's percentage. It matches nothing and
   contributes no pair, which is correct; a permissive pattern would read `0.38` and move the median
   every other judgement rests on.

`SKIPPED_PASS_A_STRONG` is renamed **`SKIPPED_CROSS_COLUMN_VERIFIED`** and now requires the check to
pass. The rename records a real change: a bundle printing the old name is a build that could skip
unverified. A label that cannot corroborate itself now runs Strategy B — the honest cost of not
having a second opinion for free, and pinned by its own test.

### The US linear panel: nine phantom columns from inline `% DV`

Both sauce captures bound `Total Carb. 6g` to a `REFERENCE_PERCENT` column and refused it. The panel
has no columns at all.

A European table prints its reference-intake column as a header standing over a stack of cells. A US
panel prints the same information as an annotation *inside* each nutrient clause — `Total Fat 0.5 g
(1 % DV), Sat. Fat 0 g (0 % D)`. Both contain the vocabulary, so a rule keyed on vocabulary alone
read every recognised row as a column header: **eight** of them, at x=351, 524, 738, 744, 817, 1078,
1232 and 1452.

`InlinePercentAnnotation` separates them structurally: *does this span share its row with a nutrient
name and that nutrient's own printed amount?* If so it is a clause, whatever its x position. Two
findings, both measured rather than predicted:

- **The span usually swallows the nutrient name** (`Iron (2 % DV),` *is* the clause), so asking about
  the elements *outside* the span finds nothing and concludes it is a header — the opposite of the
  truth. The whole row is examined.
- **`Iron (2 % DV), Potas. (0 % DV)` prints no mass at all.** Micronutrients legitimately do. Two or
  more nutrient-and-percentage pairs on one recognised row is a sentence, not a heading; one is not
  enough, or a genuine `%RI` header would suppress itself.

Eight phantom columns became one (a legitimate cell-shape recovery), and both captures now offer
`6 g / 18 g serving`.

### `Ingredients` ends the declaration

The sauce's ingredient list names **brown sugar**, so the row classified as `CARBOHYDRATE_CHILD` and
joined the table's structure. That is not a vocabulary problem and must not be fixed as one: brown
sugar genuinely is sugar, and the ingredient list genuinely is not a nutrition table. They are
separated by *where the text is*.

`RowClassifier.classifyAll(rows)` is the new document-aware entry point; `classify(row)` stays pure
and is what it delegates to. The interpreter, the column classifier and `RecoveryCandidates` all use
it.

**Neither "first boundary" nor "last boundary" works, and both were tried on the device's own
recognition.** First cuts the declaration off on a package printing ingredients above the table.
Last is what I shipped in the first attempt and it silently failed: the sauce prints `ingredients:`
at row 8 and `BEST BEFORE` at row 17, so the boundary landed at 17 and the brown-sugar row at 9
stayed inside. The rule is the **earliest boundary that still leaves a nutrient row above it**.

### The recovery dead end, and the loop it caused

`085453-023`: ML Kit returned `Kolhydraten:` (one letter lost) and `0.59` (the unit glyph read as a
digit). The row typed `OTHER`, so there was no total row, so recovery offered nothing — and the
screen asked for the same tap again. **A rejected tap that leaves the screen unchanged is
indistinguishable from a missed tap**, and the recording shows the user repeating it.

Two fixes:

1. **Element-first hit-testing.** The reconstructed rows overlap by 83 px (`1772..1948` against
   `1865..1980`), and taking the first union-box match makes the answer depend on reconstruction
   order. Elements are tighter than unions, so the tap is attributed to the element it landed on.
   **A nearest-centre tiebreak was tried and gets the real case wrong**: at y=1881 both
   `Kolhydraten:` and `Waarvan` are under the finger, and the sugars word is *nearer*. Distance
   measures which box is closer; it says nothing about which word the finger is on. The
   nutrient-naming element wins — including a head-damaged one, via
   `DamagedCarbohydrateLabel.statesADamagedCarbohydrateWord`, reused rather than restated so a row
   the app recovers cannot be a row the user is unable to select.
2. **`FocusedAmountEntry`.** The row and the basis *were* established; only the number was
   unreadable. So the app asks for the number: *"Carbohydrate row found, but the number wasn't
   clear. Enter the value printed under 100 ml."*

**That screen offers no basis picker, and that is the whole safety argument.** It is reachable only
when the label stated the basis and the classifier read it, so a picker would invite a guess to
overwrite a fact the app got right — the composition that produced `1.3 g / 100 ml` on an earlier
build. Full manual entry, where the user supplies both halves knowingly, is still offered and is a
different screen. `0.59` remains unusable throughout; the plausibility barrier still applies.

### One formatter for derived quantities

The recovery screen showed `33.3 g / 100 g` and the calculator that followed showed
**`33.33333333`** — the same value, formatted by two sites that each decided for themselves.
`stripTrailingZeros().toPlainString()` is right for a figure a human typed and wrong for one the app
*derived*, because a derived figure carries the full precision of its division.

`ResultFormatter.quantity` is the one place a stored or derived quantity becomes text: one decimal
place, HALF_UP, trailing zeros trimmed, locale-aware. **The stored value is untouched** — the exact
figure stays in `BigDecimal` all the way to the calculation.

### Measured outcomes across the nine captures

| capture | before | after |
|---|---|---|
| 085442-819 | auto `0.5/100 ml` | `0.5/100 ml`, verified by distinct-run agreement |
| 085453-023 | refusal, recovery dead end | refusal + focused entry under `100 ml` |
| 085513-478 | refusal, recovery dead end | as above |
| 085534-551 | auto `72/100 g` | auto `72/100 g`, `CROSS_COLUMN` (4 rows) |
| **085542-213** | **auto `12/100 g` — wrong, unconfirmed** | **`12` vetoed; one confirmation tap** |
| 085554-517 | auto `72/100 g` | auto `72/100 g`, `CROSS_COLUMN` (5 rows) |
| 085602-075 | auto `72/100 g` | auto `72/100 g`, `CROSS_COLUMN` (4 rows) |
| 085611-201 | parser fails; 8 phantom % columns | 1 column; recovery offers `6 g / 18 g serving` |
| 085631-444 | as above | as above |

### Verified

JVM **1277/1277** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 110 JUnit XML
files — up from 1250). Lint **exit 0, 0 errors, 22 warnings**, unused resources **0**. Instrumented
sources **compile**. Debug APK **89,502,997 bytes**, SHA-256
`83226166d50c6eb37f600f88e3fa032ec244e6ef5d546bf36e7ced458d6d3fc9`, `versionCode=4` /
`1.0.3-debug` read from the APK with `aapt2 dump badging`. Permissions unchanged.

Minified release **builds** and its R8 barriers were re-checked on that build: `ScanEvidenceRecorder`
and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`,
`ScanTrace` and `ZipIntegrity` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`,
`CarbCandidate`, `RecoveryCandidates`, `ServingDeclaration`, `CrossColumnRatioCheck`,
`InlinePercentAnnotation`, `DeclarationBoundary` and `CarbReading` retained as real classes.

**`AutomaticVerification` and `FocusedAmountEntry` read as absent / `R8$$REMOVED$$CLASS$$`, and both
are the inlined-not-dropped case** this file already warns about — checked behaviourally rather than
assumed: `AutomaticVerification$Route` survives as a real enum with all three constants and
`getRoute()` inlined into the scanner, `CrossColumnRatioCheck` (the actual veto logic) is retained,
and the focused-entry strings are present in the release APK. Do not read those markers as a feature
shipping disabled without checking behaviour.

**Seven negative controls**, each restored and the suite re-verified green afterwards:

| control disabled | failures |
|---|---|
| recognition-run uniqueness | 2 (`AutomaticVerificationTest`) |
| cross-column veto | 4 across 2 classes, incl. both P0 guards |
| linear-panel serving declaration | 6 across 3 classes |
| inline-DV suppression | 1 (the eight phantom columns return) |
| ingredients boundary | 1 (brown sugar becomes a nutrient row) |
| element-first hit-testing | 1 (`RecoveryTapTest`) |
| central numeric formatting | 4 (`QuantityFormattingTest`) |

### NOT verified, and this is the gate

**No instrumented run** — no device or emulator was attached, so the nine-photograph OCR corpus has
not been run against these changes. The instrumented sources compile; that is not the same thing.

**Nothing here has been seen on physical hardware, and the timing claim is the weakest part.** The
516 ms median was measured before this pass. Cross-column verification is free (a re-parse of rows
already built), but a label that cannot corroborate itself now runs a Strategy B recognition that
previously did not run — measured at ~400 ms on other hardware. `docs/manual-qa.md` **§26** is the
gate; §26d requires cold and warm reported separately, and **26.1** (no capture may ever display
`12 g / 100 g`) is the row that decides whether the release blocker is actually closed.

## Basis-complete readings (2026-09-02, third phone session) — still 1.0.3 / versionCode 4

The third phone session (`docs/Scan evidence 01-09-26 3rd testr/`, nine complete bundles) proved two
things at once. **The performance repair worked** — device parser 66–579 ms, most captures 0.89–1.92 s,
evidence persistence no longer a synchronous stage. **And automatic refusal alone is not enough**: the
user selected the printed 250 ml value through the *recovery* screen and Quick Calculation showed

```
1.3 g carbs / 100 ml
```

the original 2.6x error, arriving through the manual path after the automatic path had been fixed.

Nothing about the calculation, the schema, migrations, the §10 lookup priority or barcode detection
changed. Still `versionCode 4`, nothing built as a release candidate, nothing uploaded.

### The nine captures are committed as geometry-preserving fixtures

`ThirdSessionFixtures` is **generated** from each bundle's own `diagnostics.txt`, not transcribed:
973 elements across nine documents, counts matching each bundle's `elements=` line exactly (97, 82,
92, 76, 92, 48, 157, 150, 179). Ingredient prose and address blocks are **kept**, unlike
`HardwareLabelFixtures`, and that turned out to matter — they are what make
`UnitAccompanimentPolicy`'s document-level question answerable, they supply the percent clusters the
column fallback recovers from, and on the Korean sauce an ingredients row classifies as
`CARBOHYDRATE_CHILD` (it names "brown sugar") and takes part in the document's structure.

`ThirdSessionDiagnosticTest` prints and asserts almost nothing; `ThirdSessionRegressionTest` asserts.
The JVM baseline reproduced all nine device outcomes exactly before anything was changed.

### The defect was a composition, and no stage was individually wrong

Recovery worked in two steps: pick a number, then pick a basis. `StatedBasis.of(document)` correctly
reported *the label states per 100 ml*; the tap correctly reported *the user means this cell*. Both
true. But **the label's basis is not the tapped cell's basis**, and a two-step flow has nowhere to
notice that.

`RecoveryCandidates` replaces it. A number becomes selectable **only together with the basis of the
column it sits in**, so the choices read `0.5 g / 100 ml`, `1.3 g / 250 ml`, `6 g / 18 g serving`.
Picking the second yields `0.52 g / 100 ml` by conversion, never by relabelling. A cell in an
`UNKNOWN` column is **not offered at all** — there is no honest label for it.

`CarbReading` (domain) is the type that makes this structural: amount plus `CarbBasis`
(`PerHundred` / `PerQuantity(quantity, unit)` / `PerUnknownServing`) plus provenance. Identity
includes the basis, so `1.3 g/250 ml` and `1.3 g/100 ml` are different readings and any stage looking
for agreement gets that for free. `normalizedToPerHundred()` returns a **new** reading carrying
`derivedFrom`, so the printed figure and the derived one both survive onto the screen.

**`BasisActions` — the last "per 100 g or per 100 ml?" question — is now reachable only after the
user has TYPED the figure**, where they are the source of the data. That distinction is the whole
fix and must not erode: when the app read the number, the app must already know the basis.

### Four parser fixes, each measured on a device recognition

1. **A truncated unit terminates the per-100 span.** `225654-501` prints `PER: 100 ml 250 m` — the
   `l` simply gone, with no second recognition to recover it, so the 2026-09-01 fix (which required
   a *repeated* full unit) never fired and one column at x=1254 covered both printed columns.
   `isUnitFragment` asks a narrower question than the shared spelling list — *is this a proper prefix
   of a unit?* — in the one place whose answer only ever produces a refusal. **`m` is still not a
   unit spelling and must never become one**: a bare `m` is metres and that list is shared with
   `ServingSizeParser`.
2. **A duplicated quantity is skipped.** `225530-249` reconstructs as `100 ml 250 250 m ml9` — ML Kit
   read `250` twice, overlapping. Without skipping the repeat the element after the quantity is
   another quantity, every check fails, and both `250`s are swallowed.
3. **A basis phrase may not span a column gap.** `225617-066`'s header is `PER: 100 ml 250 ml` with
   `PER:` at x=263 and `100` at x=984 — a 619 px gap. `per` is a connective, so the anchor walk took
   it and landed at **x=707**, midway between the label column and the values; the printed `0.5g` at
   x=1076.5 was then 369 px from its own column and only 222 px from the 250 ml one, so the
   interpreter bound the right value to the **wrong** column. The reading survived only because the
   prose fallback happened to catch it.
4. **A basis span may not end on non-basis debris.** The Baltic `of9g` (a `/` read as `f`) split into
   `of` + `9g`, and the greedy walk matched `o/100g| of` — the trailing `of` dragging the anchor
   48 px toward the 9 g portion column. Applied **after** `kindOf`, and only to per-100 and serving
   spans: a `REFERENCE_PERCENT` span legitimately ends on `%Rí`, and checking before the kind was
   known deleted every percent column on the label.

**Punctuation between a number and its unit is accompaniment.** `72,0 g` came back as `72,0.g` on
`225720-700` and was declined for stating no unit; the next capture of the same package returned a
clean `72,0g` and read confidently, so the decline was punctuation noise. This does **not** reopen
the `g`→`9` defect: the token must still end in a unit spelling, and `0.59`, `22,59`, `72,0mg` and
`3q.` are all still refused.

### The linear panel finally has a basis

`ServingDeclaration` reads `Serv. size: 1 Tbsp (18 g)` from a US Nutrition Facts panel, which has no
columns at all. Consulted **only** when the document resolved no per-100 column anywhere, so a real
table's own headers always win. It stops at the first nutrient name — without that bound the forward
search adopts the `Fat 0.5 g` printed two rows later as the serving mass, and every figure on the
panel becomes twelve times too large (measured; that was the first implementation).

The sauce now offers `6 g / 18 g serving`, normalizing to `33.33333333 g / 100 g` with the printed
reading preserved in `derivedFrom`. It never offers `/100 g` as a guess, never offers the `Fiber 1 g`
on the same recognised row, and never offers a `%DV` figure.

### Percentages are excluded at one boundary

Every recovery route passes through `RecoveryCandidates`, so the exclusion lives there rather than at
each call site: a `%` in the token, a leading `<` (the Baltic `<1%`), a `ri`/`dv`/`gda` word, a
`REFERENCE_PERCENT` column, or `PercentAssociation`'s split-token verdict. Pinned by tests asserting
the cracker can never display `9%` and the sauce can never display `2`, `4` or `22`.

**Only the total-carbohydrate row contributes candidates.** A probe over the nine captures found
`400 g` from a kilojoule footnote, `8 g` from a batch code and `13 g` from an energy row all being
offered. That is not merely untidy: the list is a claim that each entry is a plausible reading of the
carbohydrate figure.

### Strategy B, warm-up and export integrity

`SKIPPED_PASS_A_STRONG` skips the ~400 ms second recognition when **both** views of Pass A read
confidently, agree completely, state a basis, and nothing else on the table contradicts them. It is a
different question from `SKIPPED_RUNS_ALREADY_AGREE` — that one claims corroboration, this one claims
a second look is not worth the wait — and the two are logged separately so a bundle says which claim
was made. Grated cheese does not reach it.

`LabelAnalyzer.warmUp()` runs one 1x1 recognition when the scanner opens. The first capture of the
third session spent **2789 ms** in ML Kit against 447–1838 ms for the eight that followed.

**Evidence export is written under a temporary name, read back in full, and renamed only if every
entry decompresses.** The first bundle uploaded from the third session was 58 MB, truncated
mid-entry, with no central directory — and was shared without complaint. `ZipIntegrity` reads every
entry to its end (which is what verifies the CRC) and compares against the declared size.
`ScanEvidenceRecorder.drain()` is the **only** place in the app that waits for the writer; scanning
is unchanged.

### Measured outcomes across the nine captures

| capture | before | after |
|---|---|---|
| 225530-249 | NotFound, one fused column | NotFound (correct — `oolhvdraten` unrecoverable, row chained), two columns |
| 225617-066 | Confident 0.5 (via prose fallback) | Confident 0.5 PER_100_ML, correct anchor |
| 225632-622 | NotFound | NotFound (correct — both cells lost their unit); `0.59` not offered |
| 225654-501 | **Ambiguous [0.5, 13.0]** | **Confident 0.5 PER_100_ML**; `13g` in an UNKNOWN column |
| 225720-700 | NotFound | **Confident 72.0 PER_100_G** |
| 225738-513 | Confident 72.0 | unchanged; `22,59` still refused |
| 225752-375 | NotFound, no basis offered | recovery offers `6 g / 18 g serving` → `33.3 g/100 g` |
| 225813-635 | as above | as above |
| 225829-154 | Confident 59.2 **by luck** (one column) | Confident 59.2 with three separate anchors |

### Verified

JVM **1236/1236** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 104 JUnit XML
files — up from 1175). Lint **exit 0, 0 errors, 21 warnings**, unused resources **0** (seven strings
orphaned by the removed two-step flow were deleted, each verified with zero Kotlin references
independently of lint). Debug APK **89,970,536 bytes**, SHA-256
`7f7ef25611a96e2bcdc3de436ac9325c742fd5aa944ed1eb5d9849c1c6903cf4`, `versionCode=4` / `1.0.3-debug`
read from the APK.

Minified release **builds** and its R8 barriers were re-checked on that build: `ScanEvidenceRecorder`
and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`,
`ScanTrace` and the new `ZipIntegrity` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`,
`CarbCandidate` and the new `RecoveryCandidates`, `ServingDeclaration` and `CarbReading` retained as
real classes. Release manifest: CAMERA, INTERNET, ACCESS_NETWORK_STATE, **zero** FileProvider matches.
**That release APK is a minification check from an uncommitted tree. It is not a release candidate.**

**Eight negative controls**, each restored and re-verified green: truncated-unit split disabled fails
6 across 3 classes · basis-phrase gap check disabled fails 2 · punctuation accompaniment reverted
fails 1 · serving declaration disabled fails 4 · an UNKNOWN column given a basis fails **5**
(including the P0 guard) · row restriction removed fails 4 across 3 classes · ZIP size check disabled
fails 1 · strong-path gate weakened fails 1.

### A latent break this pass found

`EvidencePipelineProductionTest` did not compile: `EvidenceResolver.Outcome.Unresolved` was added in
the 2026-09-01 correctness patch and that `when` was never made exhaustive, so **the whole
instrumented suite could not build**. Fixed by enumerating the branch rather than adding an `else`,
so a future outcome type has to be considered rather than silently defaulting to "offers nothing".

### NOT verified, and this is the gate

**No instrumented run at all** — no device or emulator was attached, so the nine-photograph OCR
corpus has not been run against these changes. That is the largest gap.

**Nothing here has been seen on physical hardware.** `docs/manual-qa.md` **§25** is the gate. Its
25.4 row (a right-column value must display `/250 ml`) and 25.12 (the sauce must never be asked "per
100 g or per 100 ml?") are the two rows that decide whether the P0 is actually closed on a phone.

## P0 regression repair (2026-09-01, second phone session) — still 1.0.3 / versionCode 4, READ FIRST

The correctness patch below shipped a **parse regression of 10–27x** that only a phone could show.
Two captures of the same drink (`docs/Scan Evidence 01-09-26 2nd test/`) spent **20195 ms** and
**11189 ms** on the spinner and returned `NotFound`. Nothing about the calculation, the schema,
migrations, the §10 lookup priority, barcode detection or any confidence threshold changed here —
every fix is either a cache or a refusal.

### The regression was 98.5% vocabulary re-normalization, and it was measured, not guessed

`NutritionTerminology.containsTerm(text, term)` called `normalize(term)` on **every call**, and
`term` is always a compile-time constant. `normalize` runs an NFD decomposition plus five regex
replacements and allocates six intermediate strings. Profiling the 79-element capture:

| | before | after | factor |
|---|---|---|---|
| `normalize()` calls | **225,360** | **682** | **330x** |
| of which vocabulary terms | 222,076 (98.5%) | **0** | — |
| `RowClassifier.classify` (19 rows) | **95** | **19** | 5x |
| JVM parse wall clock | 105.5 ms | **6.9 ms** | 15x |

Three multipliers compounded: the term normalization above; `classify` being consulted from five
independent stages, each re-running a full `NutrientRowSegments` pass; and
`UnitAccompanimentPolicy` recomputing `InlineBasisSpans.find(row)` **inside its per-element loop**.

**Why the JVM never showed it and the phone did.** 105 ms on a warm desktop JIT is invisible in a
test suite. The same allocation-heavy regex work on mobile ART, cold, was 9278 ms. **A parser change
that looks free in the JVM suite is not evidence about the device** — that is the transferable
lesson, and it is why `ParserStageProfileTest` now asserts **invocation counts** rather than
wall-clock time. Counts are deterministic across machines; a timing assertion is either too loose to
catch anything or flaky.

Two initialization traps found while fixing it, both worth knowing: a `val` in a Kotlin `object`
that calls `normalize` runs **before** the regexes declared below it, giving
`ExceptionInInitializerError` which surfaces at every call site as an unrelated-looking
`NoClassDefFoundError`; and a **one-entry** cache does nothing here, because the stages interleave
(one filters every row, then the next maps every row) so consecutive lookups always evict each other.

### The evidence work was never off the path — `markOffPath` only changes a printed number

`meta.txt` reported `evidence-capture 9487*`, with `*` meaning off-path, inside a `scan 20195ms`.
Both were true. `ScanEvidenceRecorder.consumeCapture` was still being called **synchronously at the
top of `LabelAnalyzer.finish`, before the result was delivered** — and its `renameTo` falls back to
`source.copyTo` whenever the rename is refused, which is routine across filesystems. That fallback is
~3 MB of unbounded synchronous I/O.

**`markOffPath` excludes a stage from the `user-visible` arithmetic. It does not move the work.**
Work is off the path when it happens *after delivery*; nothing else makes it so. `consumeCaptureAsync`
now defers the move, the copy fallback and the temporary-file delete to the writer thread, queued
after the handover; `recordMeta` renders on the calling thread and writes on the writer. The writer is
single-threaded and FIFO, which is what guarantees `capture.jpg` is in place before `passA.png` and
`meta.txt` are written.

### The fused header was still fused, in a shape the first fix could not see

`222300-297`'s header row is `100 ml 250 m ml (79`. ML Kit recognised the printed `ml` **twice**,
once truncated — `m` at `[1023,1418]` and `ml` at `[1023,1426]`, the same left edge. `m` is not a
unit spelling and **must never become one** (a bare `m` is metres, and the spelling list is shared
with every other stage), so `offBasisQuantitySpan` returned null, the guard never fired, and the
per-100 span swallowed `250 m`:

```
before:  PER_100_ML '100 ml 250 m' @ x=836.0                    (one column, both cells bound to it)
after :  PER_100_ML '100 ml' @ x=709.0  +  UNKNOWN '250 m ml' @ x=994.5
```

The fix keys on **horizontal overlap**: two columns are horizontally separated — that is what makes
them columns — so an element overlapping its predecessor is the same glyphs read again, not a new
column. The fragment must also be a prefix of the unit that follows, so `250 x ml` stays unmatched.

### `DamagedCarbohydrateLabel` — structural recovery, not a looser vocabulary

`222212-563` is a clean capture whose table ML Kit read correctly except for one word:
`Koolhydraten:` → `laolhydraten:`. The row keeps both its values and precedes the sugars row, and the
table returned `NotFound`.

Recovery requires **all six** conditions, each a fact about the table rather than about the string:
a resolved per-100 column exists (so prose and ingredient lists are unreachable); the row is
immediately followed by a `CARBOHYDRATE_CHILD` row (positional, and the strongest single signal); the
word ends in a carbohydrate suffix (`hydraten`, `hidrati`, …) on a word of ≥8 characters; the row
carries a value under an established column; the row names no child term itself; and no undamaged
total row exists. Fuzzy matching is **not** used — a fuzzy match on `koolhydraten` reaches `koolzaad`.

`bydraten.` in `222300-297` correctly does **not** recover: `koolhy` is gone, no suffix survives, and
that capture stays `NotFound` by design.

### Measured outcomes

| capture | before | after |
|---|---|---|
| 222212-563 | `NotFound` after 20195 ms | **`Confident 0.5 PER_100_ML`** — the printed value |
| 222300-297 | `NotFound` after 11189 ms | `NotFound` (correct; label unrecoverable) |
| A/B/C/D (correctness patch) | — | unchanged: NotFound / 72.0 / NotFound / 59.2 |

**Four negative controls, each restored and re-verified green:** disabling the split-unit recovery
fails exactly 1; disabling the damaged-label recovery fails exactly 2; bypassing the term cache fails
exactly 1; bypassing the classification cache fails exactly 1.

**Verified:** JVM **1175/1175** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 99
JUnit XML files — up from 1159). Lint **exit 0, 0 errors, 20 warnings**. Debug APK 89,734,290 bytes,
SHA-256 `58516719…D00D7FED`, `versionCode=4` / `1.0.3-debug` read from the APK.

**NOT verified on hardware, and this is the whole point of the pass.** The JVM figures above are a
proxy: the regression they measure was invisible on the JVM in the first place. **No instrumented run
at all** — no device or emulator was attached, so the nine-photograph corpus has not been run against
these changes. The next checkpoint is a phone recording showing one capture reaching a value or a
recovery in ~1–2 s.

## Parser correctness patch (2026-09-01) — still 1.0.3 / versionCode 4, READ FIRST

Four labels photographed on a Samsung SM-S928B (`docs/Scan Evidence 01-09-26/`), one of which
produced a **2.6x-wrong carbohydrate value in Quick Calculation**. Five parser/resolver defects
fixed, plus the contained UX and latency wins. Still `versionCode 4`, nothing built as a release,
nothing uploaded. **No threshold was lowered and no confidence bar was relaxed** — every change either
adds a refusal or separates two things that were being conflated.

### The 2.6x error: three defects stacked, and the row layer was innocent

The drink prints `0,5 g/100 ml` and `1,3 g/250 ml`. It produced `1.3 g/100 ml`.

Row reconstruction and classification were **correct throughout** — `Koolhydraten: 0.59 13g` typed
`TOTAL_CARBOHYDRATE`, sugars typed `CARBOHYDRATE_CHILD`. Do not go looking for a row bug here.

1. **`ColumnClassifier` emitted ONE column for two printed columns.** ML Kit fused the header into
   `100` `ml250` `ml`, and there is no `per 250 ml` vocabulary — `PER_100` only matches the literal
   quantity `100` — so the span walk matched `100 ml`, found exactly one kind, and **silently absorbed
   the 250 ml header into it**. One column at x=1199.5, over the 250 ml values. Both cells bound to it.
2. **`CarbUnitAccompaniment` had no production call site.** `0.59` is the printed `0,5 g` with the `g`
   read as a `9`. The module was written, tested and never wired in.
3. **`EvidenceResolver` wrapped an ambiguity in `Outcome.Resolved`.** The bundle records
   `Ambiguous` + `RAN_NO_READING` → `resolver.verdict: Resolved`.

**The fix for (1) is the load-bearing one and is not an anchoring tweak.** A `<quantity><unit>` header
whose quantity is not 100 is now emitted as **`UNKNOWN`** — a position whose meaning is not
established, so its cells are refused. `NutritionBasis` has two members and neither means "per
250 ml"; claiming the position without claiming a meaning is what keeps the two cells apart.
Negative control: disabling it fails 6 cases, including "never reports 1.3 g/100 ml".

### `CarbUnitAccompaniment` is wired in behind a document-level policy

Applied to every candidate, the rule declines a whole legitimate layout — units in the header, bare
values down the column — which broke **30 existing tests**. Its own tests deliberately assert that a
bare `72,0` is *not* accompanied, so the rule itself must not change.

`UnitAccompanimentPolicy.mayDeclineBareValues` asks a different, weaker question, once per document:
*does this label demonstrate that it prints units on its value cells?* Two or more unit-bearing value
cells is the threshold. **This is not the sibling rule the owner rejected** — that objection is about
a token whose unit became another letter (`3q.`, `2.5c`), which `isAccompanied` refuses on its own
text before neighbours are consulted. The policy gates only the *bare-number* branch.

Header rows and inline basis phrases are excluded from the count, and the check runs on the
**rejoined** cell, not the raw element — a `61,9 g` fragmented into `61,` + `9` + `g` has its unit
adjacent to the tail. Both were real regressions caught by existing tests.

An accompaniment-declined cell in a per-100 column now sets `perHundredCellRejected`, so the "the
table's own answer was found and is unusable" rule still blocks the prose fallback.

### Nutrient-boundary segmentation, and the guard that makes it safe

`NutrientRowSegments` splits a row at the nutrient names printed on it, so
`DV), Total Carb. 6g (2% DV), Fiber 1 g (4% DV),` yields a total segment bounded at `Fiber`.

**The merged-row guard is the whole safety argument.** Requiring **every** segment to carry its own
numeric value distinguishes a linear US panel from a merged multilingual table row. Without it,
ML Kit's merge of Croatian `od kojih šećeri` with German `Kohlenhydrate` typed as
`TOTAL_CARBOHYDRATE` — the sugars-as-total failure, reintroduced through a side door. It was caught
by `RealMlKitFindingsTest`, not by review.

`Total Carb.` needed adding to the vocabulary; it is matched as the two-word `total carb`, anchored
by a word that only introduces a nutrient total.

### The multilingual table needed vocabulary, not geometry

`o/100 g| o/9g RE` splits correctly once a damaged `per` (reduced to `o/`) is recognised, and the
9 g column becomes `UNKNOWN`. But the reading still failed, and the cause was **Estonian, Latvian and
Lithuanian missing from `NutritionTerminology`**: the declaration wraps across two rows, and the row
carrying the *numbers* named carbohydrate only in Latvian and Lithuanian. Same shape as the
documented Dutch gap — a lost reading, not a wrong one.

### `Outcome.Unresolved`, and why the gate did not actually change

An ambiguity with nothing to corroborate it is now `Unresolved`, not `Resolved`.
`AutomaticScanAdvance` already required `Resolved` **and** `Confident`, so **this closed an honesty
defect, not a live auto-advance hole** — say so rather than claiming a crash was averted. It matters
because the bundles a person reads while debugging said the opposite of what happened, and because a
future caller trusting the name would open the hole for real. The `Confident` half of the gate is
kept rather than made redundant.

`selection.txt` now lists each pass with what it contributed and its recognition run, so
`SELECTED_REGION_OCR` returning nothing no longer reads as a third opinion. `meta.txt` no longer
appends "this is what AutomaticScanAdvance reads" to a placeholder.

### Cross-column consistency reports; it never repairs

`CrossColumnConsistency` compares a per-100 figure against the portion column using the printed
portion size. Tolerance is derived from the **printed value's own precision**, not a percentage:
`59,2 × 9/100 = 5,328` vs printed `5,4` is consistent; `54` is not. `decimalShiftHypothesis` reports
a lost decimal point **only when exactly one placement works**, and is recorded as a hypothesis in
diagnostics — never applied, never substituted for the recognised token.

### UX and latency

- **The confirmation card is gone for strong readings only.** A capture that passed `mayAdvance` with
  a non-null basis goes straight to Quick Calculation. Gated on `automatic`: a reading reached after
  the user confirmed a crop keeps its card, because there they have already been asked a question.
  A null basis never advances.
- **Evidence writing moved off the UI-decision path.** `evidence-diagnostics` cost **582–1106 ms in
  every recorded bundle**, running between the parse and the result reaching the screen. `ScanTrace`
  marked it off-path, so the printed `user-visible` figure *excluded* work the user was still waiting
  for. Both writes now render an immutable snapshot on the calling thread and write after `finish()`.

### Verified

JVM **1159/1159** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up from
1112). Lint **exit 0, 0 errors, 20 warnings**. Debug APK builds (89,469,605 bytes, SHA-256
`E2490516…2B0498E4`, `versionCode=4` / `1.0.3-debug` read from the APK).

**Three negative controls, each restored and re-verified green:** disabling the accompaniment call
fails exactly the 2 `0.59` cases; disabling segmentation fails exactly the sauce case; disabling the
off-basis column fails 6 across both the drink and the multilingual table.

**Not done:** no release or AAB build, **so the R8 barriers were not re-checked** — `CrossColumnConsistency`,
`NutrientRowSegments` and `UnitAccompanimentPolicy` are new answer-path classes and should be
*retained*, but that is an argument, not a measurement. **No instrumented run at all** — no device or
emulator was attached, so the nine-photograph corpus has not been run against these changes. That is
the largest gap in this pass.

**Nothing here has been seen on physical hardware.** `docs/manual-qa.md` **§23** is the gate, and its
four products are the exact ones whose failures motivated the work. `docs/manual-qa-checklist.md` is
a single-sheet consolidation of the whole QA document, added this pass.

## Device-recording corrections (2026-08-30) — still 1.0.3 / versionCode 4, READ FIRST

A physical-device screen recording of the 1.0.3 scanner. The direction held — the fast path skipped
the crop screen on clean labels and reached `32.7 g/100 g` and `2.5 g/100 ml` in ~3–4 s — and it
exposed four concrete problems. **No OCR rule was weakened**: no threshold moved, no confidence bar
lowered, `EvidenceResolver` is untouched, no second engine or parser exists, and the nine-photograph
corpus is unchanged. Still `versionCode 4`, nothing built as a release, nothing uploaded.

### P0 — an impossible value was offered exactly like a real one

A red label printing about `7,9 g` produced **`790`** and **`794`** through the assisted path, and
both appeared behind the same two full-emphasis *Use / 100 g* / *Use / 100 ml* buttons an ordinary
value gets.

**Root cause, and note that no single stage was misbehaving.** `AssistedSelection.numericCandidates`
is *deliberately* unfiltered — the user is choosing, and hiding a number because the app thinks it
unlikely reintroduces the judgement that interaction exists to avoid. That is correct and unchanged.
The defect was that **nothing between the tap and the accept action re-checked plausibility**, while
`ManualEntryViewModel` validates only at the *destination* — i.e. after the user has already
committed with a confident-looking tap. `NUMERIC_TOKEN` matches `\d{1,3}`, so `790` is a well-formed
token all the way through.

`CarbPlausibility` (domain) now gates the accept actions. It holds **no rule of its own** — it asks
`NutritionValueValidator.validateCarbsPer100`, the same ceilings every remote value passes, and
reports a boolean. The indirection exists because the *shape* of the question differs (offer an
action, versus accept a stored value), and a caller forced to write
`validate(x.toDouble(), b) != null` is one refactor from "simplifying" it into a local `> 100`
check — which is how a second, drifting copy of a safety rule gets born. Pinned by a test asserting
the two agree across the range.

**Asked per basis, not once**, and that matters: `150` is impossible per 100 g and legitimate per
100 ml (the per-ml ceiling is a density bound, not a mass bound), so a single verdict would either
block a correct reading or admit an impossible one. `790` fails both, so **no accept action is
rendered at all**.

**Deliberately not a disabled button** — a control that does nothing and says nothing is the dead
end the assisted screen exists to remove. It shows the number, says *"That can't be right — check
the figure."*, and leaves the field editable. **And deliberately not a repair**: `790` never becomes
`79.0` or `7.9`. The decimal point is what OCR is least reliable about, so repositioning it guesses
at exactly the wrong thing, and unlike a refusal a wrong repair is invisible — the user sees a
plausible number and has no reason to check it.

### P1 — a basis the label stated was discarded, then asked for again

A coconut-milk table printed `per 100 ml` clearly enough that `ColumnClassifier` resolved the column,
but because the **value** needed assistance the app asked *"2.5 g carbs — per what?"* with `/100 g`
beside `/100 ml`.

**Root cause:** `EvidenceResolver.Outcome.Nothing` is a bare `data object` carrying no report, and
the scanner builds `AssistState(document = …)` — raw elements, no parsed structure. So a fact
established by a stage that *succeeded* (the column classifier) was thrown away because a *later*
stage failed. Value confidence and basis confidence are separate facts produced by separate stages;
flattening them is what produced the question.

`StatedBasis.of(document)` recovers that one fact, from the document the screen already holds — no
new pipeline state, no recognition, nothing invented. It reports a basis **only when unambiguous**:
two per-100 columns disagreeing → null, no per-100 column → null, a serving column only → null
(`NutritionBasis` has no member meaning "per serving", so mapping one onto a per-100 unit would
attach a serving figure to a per-100 basis). Two columns stating the *same* basis is agreement, not
conflict — multilingual packaging prints "per 100 g / pro 100 g" routinely. Every null is the
pre-existing behaviour: the user is asked.

**The two rules compose.** A preserved basis does not exempt a value from the plausibility barrier,
so an impossible value under a known basis leaves no accept action at all — pinned by its own test.

### P2 — confirming an unchanged crop repeated the identical recognition

**This was structural, not a guess.** The capture handler computes
`ScanRegionMapper.expand(scanRegion)`, assigns it to `cropSelection`, and *then* calls
`readSelectedTable(proposed, automatic = true)`. So when the automatic attempt declines, the crop
screen opens on **the same rectangle** — and *Read table* without moving a corner re-ran Strategy A
over the same retained elements and Strategy B (measured ~400 ms) over the same pixels of the same
bitmap. Recognition is deterministic over identical input, so the outcome was necessarily the
refusal already given.

`CropChange.isMaterial` compares the confirmed region against `lastRecognisedRegion` — keyed on the
**region**, not on which button was pressed, because the question is a property of the input. A
tolerance rather than equality, because a corner touched and returned does not produce the
bit-identical double; `TOLERANCE = 0.002` is a couple of pixels, above float round-trip noise and
far below any deliberate drag (both bounds pinned). A null previous region is **always** material,
which is what keeps a fresh capture behaving exactly as before, and `resumeLive` clears it so a new
capture can never be mistaken for an unchanged crop of the last one.

**It is a shortcut through a known result, never a skipped check** — the rules already ran on that
exact region and declined. A crop the user genuinely moved is always recognised.

`AssistState.cropUnchanged` is a **separate flag from `ineffectiveSelection`**, not a reuse: that
one says "your box kept nearly the whole photo", a claim about *size*, and an unchanged box may be
perfectly tight. Telling a user to tighten an already-tight crop sends them to fix something that is
not wrong.

### P3 — the ROI is correct; the framing advice was incomplete

Traced the whole chain before touching anything, and **found no mapping defect**. The region is
measured from the overlay's own laid-out bounds (not recomputed from the constants that position
it), the camera binds preview, analysis and capture through one `ViewPort`, and normalized fractions
survive every resolution change. All correct.

**What the measurement did show** (`ScanRegionMapperTest.the expanded scan guide spans the full
frame width on a typical phone`): the guide is `fillMaxWidth().padding(Space.l).aspectRatio(0.8f)`,
so on a 1080x2400 phone it lands at roughly `L0.061 T0.253 R0.939 B0.747` — already near full width,
the only horizontal inset being one padding step. Expanding by `SAFETY_MARGIN = 0.12` then
**saturates horizontally**: 12% of the guide's own width far exceeds that padding, so both sides
clamp to the frame edge and the automatic pass reads the **entire width of the photograph**.
Vertically there is room, so it does not clamp — the asymmetry is the point.

That is the measured explanation for the recording's pattern (wide framing declines, close framing
succeeds), and it is the margin behaving exactly as documented on a guide that is already nearly
full-width — **not a bug**. Per the brief, geometry left alone. **Do not "fix" the margin without
re-running the corpus**: the same class of change has been measured and rejected before.

Guidance improved instead, minimally: `ocr_move_closer` became *"Move closer — fill the frame with
the nutrition table"*. Surrounding text cannot be excluded by aiming, only by getting closer, and
the old wording left the user adjusting something that could not help. It reuses the existing
calibrated `TextResolutionGuidance` signal — no new signal, no threshold change, and the shutter is
still never gated.

### Verified

JVM **1075/1075** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up
from 1043; +12 `CarbPlausibilityTest`, +10 `StatedBasisTest`, +9 `CropChangeTest`, +1
`ScanRegionMapperTest`). `AssistedReadingScreenTest` **24/24** on device (up from 10).

*(Per-class counts corrected 2026-08-30 from the JUnit XML: an earlier revision said 13 and 8. The
total was right; only the split between those two classes was wrong.)*

**Three negative controls, each restored and re-verified green:** replacing `CropChange`'s tolerance
with exact equality fails 2; `StatedBasis` picking the first basis instead of requiring exactly one
fails the both-bases refusal; removing the plausibility filter fails exactly the 5 P0 cases and
nothing else.

**A fixture trap worth remembering.** The `StatedBasis` tests carry an explicit precondition
asserting the **real** `ColumnClassifier` resolves each fixture. Without it a fixture whose header
the classifier never recognises would make every positive case pass for the wrong reason — the same
trap as the Dutch header fixture and the soft-keyboard geometry test. Header phrases are laid
**centred over the column they head**, not left-to-right from the label margin.

**Not verified on physical hardware.** Everything above is emulator and JVM. The four device cases
that motivated it — the red label, the coconut milk at two framings, a reflective/curved label, and
a clean table — are the open gates.

### Device-validation attempt (2026-08-30, later same day) — NO HARDWARE WAS AVAILABLE

A pass was started to run the §22 gate on a phone. **It could not be run**, and the reason is
recorded so the next session does not repeat the setup work or, worse, quote emulator behaviour as
device evidence.

The only attached target was `emulator-5554`, confirmed synthetic on four independent properties:
`ro.kernel.qemu=1`, `ro.boot.qemu=1`, `ro.hardware=ranchu`, `ro.build.characteristics=emulator`.
**No §22 row was ticked and no OCR change was made** — the brief's own instruction for this case,
and the right one: the emulator's virtual camera cannot render a nutrition table, so the automatic
accept path is *unreachable* there. An emulator run would not have been weak evidence; it would have
been evidence about a different thing.

What the pass did instead, all of it re-measured rather than taken from the section above:

- **JVM 1075/1075** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 87 JUnit XML
  files). Lint **exit 0, 0 errors, 40 warnings**. Debug APK builds.
- **The artifact to test is pinned**: `app/build/outputs/apk/debug/app-debug.apk`, 89,438,285 bytes,
  SHA-256 `35F4387F17AEDB2D0B8E78C20BE1E2AA9685521E22EEA8C6BE052B7851322EBB`. `versionCode=4` /
  `versionName=1.0.3-debug` were read **from the APK** with `aapt2 dump badging`, not from Gradle
  config — the same discipline the release path uses for the signer DN. Permissions unchanged:
  CAMERA, INTERNET, ACCESS_NETWORK_STATE (plus AGP's debug-only receiver permission).
- **`CarbPlausibility` re-checked against the brief's consistency requirement**: it holds no rule of
  its own, delegates to `NutritionValueValidator.validateCarbsPer100`, exposes no `correct()` or
  `clamp()`, and `the barrier agrees with the remote value validator across the range` still pins
  the agreement. **No drift into a second definition.**
- **`docs/manual-qa.md` §22f** added — the execution appendix: how to prove the device is real, the
  exact `JustTheCarbsOCR` log lines a scan emits and what each one answers, the ten-scan latency
  table, and 11 physical UX rows (22.27–22.37). Purely additive, 191 lines, no existing row touched.

**One log-reading trap, found by reading the call sites and worth knowing before the phone session.**
A *declined* fast path logs `fast-path declined (<Outcome>)`; a **successful** one logs **nothing of
its own**. Success is `selected table: … outcome=Confident` with no decline line following. Do not
hunt for an "advanced" message and read its absence as a failure.

Also: measure latency on the **debug** build (release strips `OcrDiagnosticsLogger` entirely, so it
yields no timing at all), and read **`user-visible`** from the trace summary rather than `scan` —
the debug build carries the evidence writer a user never pays for, and stages marked `*` are
off-path.

## OCR quick calculation (2026-08-29) — opens 1.0.3 / versionCode 4, NOT UPLOADED

A scanned nutrition label now reaches a carbohydrate total without creating a product. Nothing about
the calculation, the schema, migrations, the §10 lookup priority, barcode detection, any OCR
recognition rule, the search stack or the 30 s refresh window changed. **Built and driven on the
emulator; no release artifact was made and nothing was uploaded.**

### The feature was already written and had never been wired up

`ProductViewModel.startQuickCalculation` existed with **zero call sites and zero tests**, and the
§28 strings `quick_title`/`quick_subtitle` were likewise unreferenced. Every downstream guard was
already in place too — `rememberUsage`, `toggleFavorite` and `addPortionUnit` all return early on an
empty barcode, `addCurrentToMeal` passes a null barcode when unsaved, and `ProductScreen` already
hid the favourite, the overflow menu and *Add portion unit* on an empty barcode. So this pass wired
an existing path rather than building one; the diff is a route, a save action and tests.

**The coupling was one parameter.** `startQuickCalculation(name, …)` required a name, so the OCR
result had nowhere to go but `ManualEntryScreen`, whose `canSave` requires a non-blank name and
whose `save()` writes a Room row before `LaunchedEffect(savedBarcode)` will navigate. Reading one
number off one photograph therefore cost a named, saved record. The name parameter is gone and
`saveQuickCalculation(name)` asks for it at the only moment it is needed.

### What the route is, and why it is not `product/{barcode}`

`Routes.QUICK = "quick?carbs={carbs}&basis={basis}"` reaching the **same** `ProductScreen` and the
**same** `ProductViewModel`. Separate from `PRODUCT` because the two differ in what they do on
arrival: that one begins with a database and possibly a network lookup, this one begins with
nothing. Folding them together would mean teaching the lookup path to recognise a sentinel barcode
and skip itself, which is how a sentinel ends up written to disk.

Both arguments are **required** — a figure whose basis was lost in transit is the "grams of what?"
question the app must never answer for the user, so the route falls back to manual entry rather
than defaulting the basis.

### Two presentation defects a nameless product creates

Both were invisible to the tests and only showed up on the device:

1. **An empty title over an empty monogram plate** reads as a product record that failed to load.
   The hero is suppressed when `name.isEmpty()` and the title falls back to *Quick calculation*. A
   saved product with no photo still gets its monogram, unchanged.
2. **883 px of dead page** between the last control and the pinned result panel — the quick screen
   has no hero, no *Usual* row, no portion units and no *Add portion unit*, so it fills far less of
   the `weight(1f)` zone. Fixed with `verticalArrangement = Center` **only** when `unsaved`.
   Note two non-fixes: `weight(1f, fill = false)` on the zone removes the gap but unpins the result
   panel (already tried and rejected in 2026-08-16), and a `weight` spacer *inside* the
   `verticalScroll` Column is meaningless — the scroll gives it an infinite height constraint. I
   wrote the second one before catching it.

### Provenance is carried, and the two facts stay separate

A quick calculation is `OCR` / **`UNVERIFIED`** — the user confirmed a number the *parser* proposed,
which is not the same as transcribing the package. Saving preserves the origin rather than
flattening it to `MANUAL`, and `saveUserAuthoredProduct` then stamps `USER_VERIFIED`. That is a
stronger claim than the unsaved state makes, deliberately: keeping a product for future meals is an
act of vouching in a way that confirming a proposal to get one number is not. It is the repository's
existing rule for user-authored products, not a decision made here — if revisited, it must move for
manual entry and this path together.

### Three test-fixture traps caught while writing the tests

1. **A write count is not a row count.** Saving legitimately touches the row twice — once to create
   it, once for `recordUse` to stamp `lastUsedAt`, which is what puts it in Recents at all. The
   assertion counts **distinct barcodes**, which is what a duplicate would actually look like.
2. **A `ForbiddenRemote` that throws on any fetch cannot be used for the barcode control test** — a
   normal load correctly fetches *and* refreshes. Asserting otherwise would pin the opposite of the
   intended behaviour.
3. **Two controls labelled "Save product"** (the screen action and the dialog's confirm button) are
   unresolvable for a test and ambiguous for a person or a screen reader. The dialog's button is
   now *Save*.

**Negative control:** removing the `unsaved || barcode.isEmpty()` guard in `rememberUsage` fails
**11 of 17** cases in `QuickCalculationTest`, so the persistence assertions are not vacuous.

### Automatic fast path: the crop confirmation is now conditional (2026-08-30, P3/P4)

`Capture → crop confirmation → Read table → result` became `Capture → result`, with the crop screen
retained in full as the fallback. **No recognition rule, threshold, parser stage or resolver rule
changed**, and the OCR corpus is unchanged at 37/37.

**Why this was cheap and safe, which is not obvious.** The capture handler *already* computed the
rectangle by itself — `ScanRegionMapper.expand(scanRegion)`, the scan guide the user aimed with — and
the crop screen's job was to have that same rectangle approved. So the fast path is the user's own
*Read table* tap on the app's own rectangle, made automatically. It costs nothing extra: Strategy A
is a re-parse of elements already in memory, and `SelectedTableResolution` already skips Strategy B
when A is corroborated. **Do not read this as a new automatic table-detector** — that was built,
measured against the corpus, and rejected (it damaged two of four canaries); the rectangle here is
still not a guess about where the table is.

**The gate is `AutomaticScanAdvance.mayAdvance`, and it is deliberately stricter than `Resolved`.**
`EvidenceResolver.Outcome.Resolved` can legitimately carry an **`Ambiguous`** reading — when no pass
is confident the resolver keeps the richest ambiguous report rather than flattening it to
`NotFound`. Gating on the outcome type alone would therefore send a multi-candidate reading past the
crop step. Not unsafe (the scanner shows `AmbiguousCard` and never auto-accepts), but the parser
could not decide, and a frame containing more than the table is the usual reason — which is exactly
what the rectangle fixes. **Advancing requires `Resolved` AND `Confident`.** Everything else —
ambiguous, needs-verification, conflicted, nothing — falls back. `AutomaticScanAdvanceTest` (7 JVM
cases) drives the **real** resolver rather than hand-built outcomes, so the gate cannot drift from
the classification it depends on, and each case asserts its precondition.

The mechanism is a **veto, not an acceptance**: `readSelectedTable(region, automatic = true)` runs
the identical resolution and then declines to present anything `mayAdvance` rejects. A confirmed
crop still reaches the same four branches with the same rules.

**P4 wording.** The crop screen took an `afterAutomaticAttempt` flag: reached as a fallback it reads
*Couldn't read it automatically* rather than *Tighten the box around the table*, which otherwise
appeared identical whether it was the first step after a capture or a hand-off from an attempt the
user had just waited through. Its title also shows *Reading table…* while a pass is running — the
ordering in that `when` is load-bearing, because instructing someone to drag corners while the app is
already reading asks for work about to be thrown away. `autoAttempted` is reset in `resumeLive`,
without which a Retake would open claiming a failure that had not happened yet.

**Measured on the real nine-photograph corpus**, which is the only place the *advance* half can be
observed without a phone in hand (`EvidencePipelineProductionTest.theFastPathAdvancesOnlyOn‑
ConfidentlyResolvedFixtures`, which runs the production resolution at the shipped starting rectangle
and then asks the gate the same question the scanner asks):

```
sondey        Resolved  61.9   advance=true      witte kaas   Nothing     advance=false
kinder        Resolved  53.5   advance=true      grated chz   Conflicted  advance=false
yoghurt       Resolved  5      advance=true      jar          Nothing     advance=false
stokbrood     Resolved  46     advance=true      lid          Nothing     advance=false
                                                 4 of 8 skip the crop step
```

**All four canaries advance, every one carrying the correct printed value, and nothing wrong
advances.** Grated cheese — the corpus's live hazard, where three recognitions of one photograph give
three different numbers — correctly refuses and is pinned by its own named test, because letting that
one through would put a known-wrong value in front of the user with one tap *fewer* than before.

The test prints its table and asserts the **safety** property per fixture (advancing implies
`Confident` **and** a non-null basis) rather than a pass rate: how many of eight labels advance is a
property of eight particular photographs and would make it a brittle scoreboard.

**Emulator walkthrough of the decline half:** capture logged `fast-path declined (Nothing)` and
landed on the crop screen with the new wording; *Read table* from there still reached the assisted
path; the full chain still ended in Quick calculation at `48 g carbs / 100 g` with the keyboard open
and `35` typed without a tap.

**The measured cost of declining is ~400 ms**, and it is worth stating plainly rather than hiding:
on a scan that will end up at the crop screen anyway, the user now waits for Strategy B's ML Kit pass
(measured `strategy-B 403ms | mlkit 398 · crop 3` on the emulator) before that screen appears.
Strategy B is skipped only when *independent* runs already agree, which needs live evidence to have
corroborated Pass A, so on the common path it runs. The trade is one screen plus one tap saved on a
good scan against ~0.4 s added to a bad one. **Unmeasured on physical hardware**, where both figures
will differ.

**A disposal race, checked and already contained — do not "fix" it.** The automatic attempt now runs
on *every* capture rather than only on a tap, so `onDispose` recycling the bitmap mid-pass is far
more reachable than before. It is harmless: Strategy A never touches the bitmap at all (it re-parses
retained elements), and `SelectedRegionRecognizer` wraps its whole body in `catch (Exception)` and
returns null, which degrades Strategy B to "no second opinion" — its documented contract. The
`isRecycled` check at its head is a check-then-use race, and the catch is what actually makes it
safe.

### Ease pass: the quick screen now opens the keyboard, and only that screen

**Measured on the device before changing anything**: landing on the calculator gave
`dumpsys input_method → mInputShown=false`, so a user who had just scanned a label, cropped it and
confirmed the figure still had to tap the one field on a screen that exists to take one number.
There was **no `FocusRequester` anywhere in `ui/`** — verified by search, not assumed.

`PortionField` gained an `autoFocus` parameter, passed as `state.unsaved && state.portionText
.isEmpty()`. Both halves are load-bearing:

- **`unsaved`** — a saved product must NOT grab the keyboard. It arrives pre-filled with the
  remembered portion, and its *Usual* shortcuts and pack buttons are alternatives to typing at all,
  so opening the IME would cover the very controls that make a repeat visit fast in order to offer
  an edit the user may not want. Confirmed still `mInputShown=false` on device after the change.
- **`portionText.isEmpty()`** — so returning to a quick calculation that already has a portion (a
  rotation, coming back from the meal) does not re-claim focus.

The request is keyed on `Unit`, not on the value, so it fires once for the life of the screen; keyed
on anything recomposition-sensitive it would drag focus back on every keystroke, which is worse than
the tap it saves because it fights the user. Pinned by three instrumented cases including
`focusIsNotStolenBackAfterTyping`.

**Verified end to end on the emulator through the real flow** (Home → *Scan nutrition label* →
capture → crop → *Read table* → *Type it in* → `48` → *Use / 100 g*): `mInputShown=true` on arrival,
then `input text "35"` **with no tap** produced `16.8 g / ≈ 17 g whole grams`. Negative control:
`autoFocus = false` fails exactly `thePortionFieldIsReadyToTypeIntoOnArrival` and nothing else.

### The portion zone was cut mid-glyph from 1.3× text, and the fix has one load-bearing detail

**Measured, on the device, at four font scales.** The portion zone fits without scrolling at 1.0×
(no scrollable node in the hierarchy at all) and overflows from **1.3×** — an ordinary accessibility
setting, not an extreme. At 1.3× *+ Add portion unit* occupied `[85,1561][489,1608]` against a zone
ending at exactly `1608`: rendered, readable, and severed through the middle of its letters. At 1.8×
the whole quick-adjust row went the same way. Screenshots, not inference — the semantics dump alone
was misleading here (see below).

`Modifier.fadeOutWhenMoreBelow(scroll)` fades the bottom ~20 dp of the viewport, gated on
`scroll.canScrollForward`, so at the default scale it draws **nothing**. `DstIn` against an alpha
ramp rather than a solid-to-transparent gradient painted over the top: the latter needs to know the
background colour and would smear the wrong one in one of the two themes.

**The ordering is the whole thing, and I got it wrong first.** A draw modifier placed *after*
`verticalScroll` decorates the scrolling **content**, whose height is the full scrollable extent —
so the fade landed far below the screen and nothing appeared at the visible edge. It must come
**before** `verticalScroll`, where it decorates the viewport. The first build compiled, ran, and
changed nothing visible; only a device screenshot showed it, which is the same lesson as the
keyboard-geometry and Dutch-header fixtures.

**Two things the semantics dump said that were false.** At 1.8× the ± row reported `h=40` and
*+ Add portion unit* was absent from the dump entirely — both read as "the controls have collapsed
and one is gone". Neither was true: the **clickable** targets stayed 126×126 px (48 dp) throughout,
and the missing action was simply below the fold, appearing after one swipe. This is the third
instance in this file of a below-the-fold node being misread as a layout defect. Check
`clickable="true"` bounds and scroll before concluding anything.

**Checked and left alone:** the meal screen has the same pinned-panel shape but does not overflow
(4 items at 1.3× leave the total panel clear at y=1747, no scrollable node), so no fade was added
there. The monogram plate is already `MONOGRAM_HEIGHT` (84 dp) and is not the 150 dp slab an earlier
pass removed. Every touch target on Home and the calculator measures ≥48 dp at 1.0× and 1.8×, and
every clickable node carries a labelled child for TalkBack.

### A third defect: the meal line was blank

`addCurrentToMeal` passed `displayName = product.name`, and a quick calculation's name is empty **by
design** — so an item added to the meal rendered as an empty row in the one list whose entire job is
saying what is on the plate, and `meal_remove_item` announced "Remove" with nothing after it.

**The existing test asserted the barcode and both write-stores and never the name**, which is exactly
how it got through: the fixture proved the item was *unattached to a product* and said nothing about
whether it was *legible*. The fallback is supplied by the screen (`R.string.quick_title`), not the
ViewModel, following the rule already established for `portionDescription` — the ViewModel supplies
the numbers, the screen supplies the wording, because wording lives in resources. `addCurrentToMeal`
therefore takes a second `fallbackName` parameter that a named product ignores. Negative control:
restoring `product.name` fails with `expected:<[Quick calculation]> but was:<[]>`.

### Two defects the review found, both on the save-failure path

Neither was reachable in any test in the pass above, and the reason is the transferable part: **every
fake in that fixture is an in-memory map that cannot fail**, so the whole `onFailure` branch was
unexecuted code that happened to compile. A `FailingLocal` that throws on demand is what made the
path measurable, and both defects appeared immediately.

1. **A failed save left the naming dialog open**, and `quickSaveFailed` renders on the *Save
   product* action — which is on the screen **behind** that dialog. So the only account of what had
   gone wrong was under the scrim: the user tapped *Save*, the dialog did not move, and nothing said
   the product had not been kept. Indistinguishable from a missed tap. `onFailure` now closes the
   form as part of reporting, which is what makes the message visible.
2. **`quickSaveFailed` was never cleared on reopen**, so a message about a failed attempt stayed on
   screen through the next one — including through a *successful* save, right up until the screen
   changed. `showSaveQuickCalculation` clears it alongside the name error.

Both are pinned, and the pair is **verified non-vacuous by negative control**: reverting the two
one-line state changes fails exactly the two new cases and nothing else.

Also checked and **not** defects, so do not re-investigate: a configuration change re-runs
`LaunchedEffect(carbsArg, basisArg)` against the surviving ViewModel, but `startQuickCalculation`
does not touch `portionText` and `recalculate()` re-derives from it, so the typed portion survives
(measured). The blank-name guard in `saveQuickCalculation` is unreachable from the dialog, whose
confirm button is disabled while the field is blank — it is kept deliberately as the layer that owns
the rule, and pinned, because a guard that depends on a button staying disabled is one refactor from
not existing.

### Verified on the emulator, end to end

Driven by hand through Home → *Scan nutrition label* → capture → crop → *Read table* → assisted
*Type it in* → `48` → *Use / 100 g*: the screen shows **Quick calculation**, `48 g carbs / 100 g`,
*Read from label by you*, and typing `35` gives **16.8 g / ≈ 17 g whole grams** — the exact figures
the JVM tests assert. **Home showed no Recents entry afterwards**, which is the no-persistence claim
measured rather than argued. Tapping *Save product* → naming it *Hagelslag* → the screen keeps 16.8 g
and the 35 portion, gains *+ Add portion unit*, drops *Save product*, and Home then lists
"Hagelslag — 35 g → 16.8 g".

### Verified

JVM **1036/1036** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from JUnit XML — up
from 1015; +21 in `QuickCalculationTest`). Instrumented **259/259** in one complete run, 0 failures,
0 ignored, 0 assumption failures, 16m19s, counted from instrumentation status codes — and there is
no `@Ignore` or `assumeTrue` anywhere in `androidTest`, so the zero cannot be a silent skip. The
nine-photograph OCR corpus is **37/37** (`RealImageOcrTest` 15, `ProductionStillPipelineTest` 8,
`SelectedTableProductionTest` 6, `EvidencePipelineProductionTest` 8) and the Room migrations
**10/10**. Lint exit 0, **40** advisories (one *fewer* than the 41 baseline — `quick_title` is now
referenced; `quick_subtitle` remains unused and is pre-existing).

**The 259/259 whole-suite figure was taken BEFORE the review fixes and the ease pass, and has not
been repeated.** The JVM figure above *is* current (1036). What has been re-run on the emulator
afterwards is the changed surface: **`QuickCalculationScreenTest` 14/14, `ProductScreenTest` 32/32,
`MealScreenTest` 19/19, `CountablePortionScreenTest` 12/12, `LabelVerificationScreenTest` 8/8 — 85
tests, all green**. Those are the classes that matter here: the meal-line fix changes
`onAddToMeal`/`onAddToMealAndScanNext` from `(String) -> Unit` to `(String, String) -> Unit`, a
signature every meal test drives through (`MealScreenTest`'s two call sites became
`{ description, _ -> }`), and the ease pass touches `PortionField`, which every one of those screens
renders. The remaining ~174 instrumented tests — OCR corpus, Room, search, settings, theme — touch
none of the changed files, but that is an argument, not a measurement: **do not quote 259/259 as
evidence for the current code.**

Minified release **builds** (66.8 MB APK) and its R8 barriers were re-checked on that build:
`ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`,
`OcrDiagnosticsReport` and `ScanTrace` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`,
`CarbCandidate` and `PackageBasisResolver` retained as real classes. Release manifest: CAMERA,
INTERNET, ACCESS_NETWORK_STATE — unchanged — and **zero** providers, so no `FileProvider`.

**That release APK was a minification check from an uncommitted tree. It is not a release candidate,
it is not signed for upload, and no AAB was built.** Building one is a deliberate instructed act
(`docs/play-release-readiness.md` §2c/§2d), and 1.0.3 goes into `docs/version-history.md` only once
Play accepts it.

**Not verified on physical hardware.** Everything above is emulator and JVM. In particular the
emulator's virtual camera cannot produce a real nutrition table, so the *automatic* OCR accept path
(`ConfidentCard` → *Confirm*) was exercised only through its assisted-reading sibling, which shares
the same `onUseValue` callback. A real Dutch or English package scanned end to end into the quick
calculator is the open gate.

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

## Version and track state (updated 2026-09-08) — THE AUTHORITATIVE ANSWER, READ BEFORE ANY RELEASE CLAIM

Everything else in this file and in `docs/` is subordinate to this section. Where an older passage
disagrees, this one is right — and fix the older passage rather than working around it.

| Question | Answer |
|---|---|
| What is the latest release? | `1.0.5` / **`versionCode 6`**, released to Play's closed-testing track 2026-09-07, **available to selected testers** (per Play Console) |
| Which track? | **Closed testing.** `versionCode 1` (internal → closed), `2`, `3`, `4` preceded it and were each replaced in turn; `5` (`1.0.4`) was uploaded, entered review, then withdrawn by the owner before review completed — Play still consumed the code, so `5` never reached the track as a release |
| What is in development? | **`1.0.6` / `versionCode 7`, OPEN.** Opened 2026-09-08 by a scanner shutter-haptic patch. `branding.gradle.kts` names `7` / `"1.0.6"`. Debug build verified (`assembleDebug`); no release artifact built or uploaded |
| Is 1.0.5 released? | **Yes**, to closed testing. In `docs/version-history.md` with hash, size and signer. Do not describe it as "pending Play review" — Play Console shows it live and available to selected testers |
| Is 1.0.4 released? | **No.** Uploaded, entered review, withdrawn by the owner before completion (private-correspondence text found in repo history). `versionCode 5` is spent and never reused; the corrective build used `6`, not a rebuilt `5` |
| What do I develop against? | **`1.0.6` / `versionCode 7`** — already open; further changes this cycle land under `CHANGELOG.md`'s existing `## 1.0.6` heading, not a new bump, until it is built and uploaded |
| Production | Not submitted. Gated by the Play forms + the §44 signature — see below |

**THE VERSIONING RULE.** A version number identifies an **artifact that reached Play**, not a
commit and not merely a local build:

> The first development change after a release **that reached Play** opens the next `versionCode`.
> Multiple coherent changes may accumulate under that open version until it is uploaded. Once
> **uploaded** — i.e. received by Play, whether or not review subsequently completes, and whether or
> not the release is later withdrawn — that `versionCode` is spent and frozen forever.

Play acceptance/review completion is **not** the gate for opening the next number: uploading is.
`1.0.4` / `versionCode 5` is the concrete proof — submitted, entered review, then withdrawn before
Play finished reviewing it, and `5` was still permanently consumed. The corrective release that
followed opened the *next* number (`6`), never a rebuilt `5`. Do not read an older passage's "do not
bump until the open version has been uploaded **and accepted**" as still correct; acceptance is not
required, only upload.

This is what 1.0.0 through 1.0.5 actually did — 1.0.2 alone accumulated five separate passes (live
search, the Search-a-licious migration, search hardening, search accuracy, the theme fixes) under
one number before its single upload.

Documentation-only changes open nothing: prose that changes no code produces no artifact.

**`versionCode 1` through `6` are all spent** (`5` by upload-then-withdrawal, not by a completed
release — see above). None is to be rebuilt or re-uploaded — Play refuses a duplicate code
regardless of what happened to that code's review. The next number is **7**, already open as `1.0.6`
above.

**A version belongs in `docs/version-history.md` once it has reached Play** — uploaded to a track —
whether or not review has since completed and whether or not it was later withdrawn. `1.0.4`'s
entry there is correct to keep despite never shipping cleanly: the archive records what a
`versionCode` actually was. A build that never left the machine is the only case that stays out.

### HISTORICAL — 1.0.3 artifact facts and post-1.0.2 rules, superseded by the table above

*(Kept for the signer/promotion provenance and the general rules, which are still true. The
version-specific claims — "current artifact", "no version is open", "next technical action" — describe
`1.0.2`→`1.0.3`, not the current `1.0.5`→`1.0.6` state. Do not read anything below this line as
naming the current version.)*

The `1.0.3` artifact was `app-release.aab` from `clean` on **`29a4f3d`**: 35,671,928 bytes, SHA-256
`7c2ae0618fda7fdfcaa8e5be24172ccfe54b1639177efc978a2b88c3c2a42828`, signed with the real upload key
`1E:21:23:F3:…:C4:F5` — the same key used by every version through `1.0.5`, which is what lets Play
accept each as an update. The signer DN was read from the built bundle with
`keytool -printcert -jarfile` before upload, not inferred from a green build: the Gradle guard
cannot tell a real upload key from a disposable one. **This DN-reading discipline is still current
practice** — apply it to any future release build regardless of version.

`versionCode 1` (`1.0.0`, `68c85a3`, SHA-256 `37be0232…c7e604b`) reached the closed track by
**promotion of the same bundle** — same bytes, same hash, same version code. `docs/version-history.md`
records it **once**, with the track progression noted; a promotion is not a release and does not get
a second entry.

**General rules that are still current** (the version numbers in the surrounding prose are not):

- **A documentation-only change opens nothing.** No version, no bump, no `CHANGELOG.md` heading.
- **Never rebuild or re-upload a spent `versionCode`.** Play refuses a duplicate regardless of
  what happened to that code's review — see the corrected versioning rule above.
- The test figures and the Play *What's new* text in an unreleased section describe the work **so
  far** and must be re-checked and rewritten before the build is made.
- **A release build is a deliberate, instructed act.** Building or uploading an AAB is never part of
  an ordinary development pass. When one is asked for, follow `docs/play-release-readiness.md`
  §2c/§2d — build from a committed tree, verify the R8 privacy barriers and **read the signer DN off
  the artifact**, then copy the section into `docs/version-history.md` with the hash once the upload
  reaches a track.

**1.0.2 is verified on physical hardware for everything it changed** (owner, 2026-08-29, against the
Play-delivered build): **live search works**, **Light and Dark themes both render correctly** — the
reported status-bar and dark-mode-contrast defects are gone — and **barcode scanning is
regression-free**. Do not re-list those as unverified.

**1.0.3 shipped on JVM and emulator evidence alone — no row of §34 or §§26–33 was ticked before
upload.** That is a decision the owner made with the gaps stated, not an oversight. `1.0.4` (never
released) and `1.0.5` (released 2026-09-07, see its own CHANGELOG/version-history sections) are the
versions that actually followed; `1.0.5`'s physical safety retest is recorded there, not here.

The two highest-value things that were being watched for on `1.0.3`, kept for reference against
future scanner-safety changes:

- **§34.1–34.5 — no wrong figure offered.** A separatorless pair now withholds *both* members, so a
  label like `46 g / 100 g` + `12 g / 25 g` should offer neither and route to focused entry with the
  photograph and basis kept. A tester seeing `12` presented as a carbohydrate figure is a live
  defect, not a preference.
- **§34.6–34.8 — the controls.** The refusal must not have spread: a declared serving
  (`6 g / 18 g serving`), a single-column label, and any value carrying its own decimal separator
  must all still reach a proposal. A tester reporting that scanning got *worse* — more typing, fewer
  answers — is most likely one of these, and is the failure mode this change risks.

Fold in the three checks still outstanding from 1.0.2 while a phone is in hand: the two theme
*override* combinations (app forced Light on a dark phone, and the reverse), an OCR label scan, and
a calculation from a search result. The overrides are the only part of the theme work still argued
rather than observed — a test cannot watch a real status bar.

Beyond that, the remaining path to production is Play Console forms plus the §44 signature, which is
owner work, not engineering.

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
