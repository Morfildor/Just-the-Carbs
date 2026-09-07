# Patch notes — Just the Carbs

Change list per version. **Newest first.** This file carries the version being worked on and the
most recent released one; every uploaded version is copied into
[`docs/version-history.md`](docs/version-history.md), the append-only archive that records each
artifact's hash, size and signer.

**Latest release: `1.0.3` / `versionCode 4`**, on closed testing since 2026-09-04. `1.0.4` /
`versionCode 5` is now open below.

## Versioning rule — one version per uploaded artifact (owner, resolved 2026-08-30)

**The first development change after an uploaded release opens the next `versionCode`. Multiple
coherent changes may accumulate under that development version until it is uploaded. Once uploaded,
that version is frozen.**

A version number identifies an **artifact**, not a commit. An earlier revision of this section said
"every code change gets its own version number", which contradicted its own next bullet and did not
describe what 1.0.0, 1.0.1 or 1.0.2 actually did; that phrasing is withdrawn.

- The first code change after a release **opens a new version**: bump `brandVersionCode` and
  `brandVersionName` in `branding.gradle.kts`, and rename the **Unreleased** heading to that
  version. Later changes in the same cycle land under that same heading.
- Do **not** bump again until the open version has been uploaded and accepted.
- `versionCode` increments by one each time and is **never reused** — Play refuses a duplicate,
  including for a build that was rejected or never uploaded.
- Documentation-only changes do not open a version. A version number exists to identify an
  artifact, and prose that changes no code produces none.
- Test figures and the Play *What's new* text inside an unreleased section describe the work so far
  and must be re-checked before the build is made.
- A version moves to [`docs/version-history.md`](docs/version-history.md) only once **Play accepts
  the upload**. A build that never left the machine is not a release.

### How 1.0.0, 1.0.1 and 1.0.2 were produced

All three accumulated several changes under one version number before being uploaded — which is
exactly what the rule above describes, so they are consistent with it rather than exceptions to it.
1.0.2 alone carried five separate passes. They are left exactly as recorded; the rule applies to
work after
1.0.1.

## Conventions

- **Unreleased** holds the heading waiting for the next code change. Under the versioning rule
  above it is renamed to a real version as soon as that change lands, so it is normally empty
  between releases rather than a place work accumulates.
- A version heading is `## <versionName> (versionCode N) — <date> — <track>`, so a tester report
  quoting "1.0.1" matches exactly one artifact.
- Entries group by **Fixed / Changed / Added / Internal**, written for whoever reads them next: a
  defect line says what the user would have seen, not which function moved.
- `versionCode` is unique per upload and **never reused** — Play rejects a duplicate. It is bumped
  when a version section is opened, then left alone until that build ships.
- **`versionCode 1`, `2` and `3` are spent.** All three are on the closed track and none is to be
  rebuilt or re-uploaded; the next number is `4`, which `1.0.3` has open below.
- When a version is uploaded, copy its section verbatim into `docs/version-history.md`. Nothing is
  rewritten on the way across, so the record of what shipped stays what it said at the time.
- Every version also carries a **Play Store release notes** block — the *What's new* text, written
  for users and far less granular than the list above it: ≤500 characters, small fixes grouped into
  one line, no health claim and no mention of diabetes (§44 §7.1 binds this field). Rules are at the
  top of [`docs/version-history.md`](docs/version-history.md).

---

## Unreleased

Nothing yet. `1.0.4` / `versionCode 5` is open below; a documentation-only change opens nothing
further and lands directly under that heading.

## 1.0.4 (versionCode 5) — 2026-09-04, open

Opened by a small trust + feedback polish patch, requested after reviewing closed-beta tester
feedback (`docs/Closed_beta_tester_feedback.pdf`). Deliberately surgical — no OCR, scanning,
calculation, database or networking file was touched.

### Added

- **Send feedback / Report a problem** (Settings → About). Opens the device's email app via
  `mailto:`, prefilled with a subject and a body containing app version, Android API level and
  device manufacturer/model, addressed to the app's existing support address
  (`BuildConfig.CONTACT_EMAIL`). Shows an inline fallback message if no email app can handle it.
  Nothing is attached or exported automatically.
- **Rate JustTheCarbs** (Settings → About). Opens the Play Store app directly to the app's own
  listing (`market://details?id=...`), falling back to the HTTPS listing if the Play Store app
  cannot handle the intent, with an inline fallback message if neither succeeds. User-initiated
  only — no in-app review API, no automatic prompts, no scan-count gating.

### Changed

- The existing "Not checked against the package" line shown beside the carbohydrate result (for
  Open Food Facts-sourced values only) now also notes that the data is crowd-sourced and may differ
  from the user's own product — reusing the existing conditional slot and height budget rather than
  adding a new line to the result panel.

### Fixed — startup hardening (2026-09-04/05)

Four release-blocking safety items, scoped down from a larger review at the owner's direction (the
usage-semantics rewrite, UI-backdrop fixes and Settings accessibility items were explicitly
deferred). Nothing about the calculation, schema, migrations, the §10 lookup priority, barcode
detection or any OCR *recognition* rule changed.

- **A returning user could briefly see Onboarding before Home**, because the start destination was
  decided from `AppSettings()`'s synthetic default while the real DataStore value was still loading.
  `MainActivity` now holds the splash screen (`StartupState.Loading`/`Ready`) until the first real
  settings value arrives, so the start destination is never chosen from a default. Onboarding
  completion is now durable-before-navigation (`OnboardingViewModel.complete()` is `suspend`,
  mutex-guarded), with a UI-layer double-tap guard on top.
- **An OCR reading with no established basis could still become `PER_100_G`.** The one remaining
  unsafe `NutritionBasis.valueOf(...)` call (the saved-state label-comparison handoff) is replaced
  with safe parsing that reports a dismissible failure instead of guessing or crashing.
  `LabelScannerScreen`'s *Correct* action threads a nullable basis end to end from the one button
  that used to hard-code grams for an unresolved reading. Manual entry's basis field is now
  genuinely nullable: neither chip is pre-selected and Save stays disabled until the user picks one,
  when reached from a scanned value with no established basis. Ordinary manual entry from Home is
  unaffected and still defaults to grams.
- **Denying the camera permission was a dead end on both scanners.** Neither screen had a way back
  to the system permission dialog after a "not this time" denial, nor a way to the app's Settings
  page after a "never ask me again" one. Both scanners now share one five-state permission gate
  (`CameraPermissionState`) with an `ON_RESUME` recheck that recognises a grant made in Settings.
- **`LiveEvidenceBuffer` had an unsynchronized concurrent-access hazard.** It is written from the
  main thread and read from a background dispatcher inside the still-recognition coroutine — a real
  race on a plain `ArrayDeque`, not a hypothetical. Every access is now synchronized, and
  observations are stamped with the capture session they belong to, so a live frame from an
  abandoned attempt or a different package can never corroborate a later capture.

Verified: JVM full suite **1715/1715** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, 170 XML
files). Lint 0 errors, 23 warnings (unchanged baseline). The connected OCR corpus (39 tests) on the
`carbscan` emulator shows 10 pre-existing failures, confirmed identical by name against a clean-HEAD
worktree control — zero regressions from this pass; none of the changes touch OCR recognition or
parsing code. `docs/manual-qa.md` §35 is the gate — nothing here has been seen on physical hardware.

### Fixed — scanner optimization pass: interaction count, unit-box overlap, declaration fragments (2026-09-07)

A 16-capture device session (`docs/scan-evidence (6).zip`, Samsung SM-S928B) was replayed
deterministically against the real production decision path — every capture faithfully derived from
its own `diagnostics.txt` (`SeptemberSeventhSessionFixtures`, element counts matching each bundle's
own declared count exactly). Three defects fixed, one presentation gap closed, one routing question
traced and left alone because the evidence behind it genuinely differs. No OCR recognition rule, row
classification, column classification or calculation rule changed; every fix is either a bounded
geometry tolerance, a structural row-join that reuses existing per-value gates unchanged, or a
display-layer change over an unmodified `BigDecimal`.

- **`CarbUnitAccompaniment` rejected a unit box that merely touched its value box.** `isImmediatelyRightOf`
  treated any `gap < 0` between a value and its trailing unit as disqualifying, so ordinary ML Kit
  box-edge noise (a unit's box overlapping the value's by a couple of pixels) declined an otherwise
  clean reading. Replaced with two independent conditions: the unit's horizontal **center** must sit
  strictly right of the value's (the categorical claim that carries reading order — a unit whose
  center is not to the right is the previous column's trailing glyph or an unrelated token, whatever
  the overlap), and the gap may be a small bounded negative number
  (`MAX_UNIT_OVERLAP_IN_HEIGHTS = 0.15`, well below any deliberate token spacing) up to the existing
  generous positive bound. A large overlap, or an overlap whose center still sits left of the value,
  is still refused — pinned by dedicated fixtures alongside the existing real-device corpus in
  `CarbUnitAccompanimentTest`, none of which changed verdict.
- **A nine-language declaration fragmented across five physical rows read `NotFound` on the very
  first attempt of the session**, despite every fact needed to read it (row, basis, value) being
  present and legible in the recognized text: `Carbohydrate/ Kolhydrat/`, `Hilihydraatit/
  Kohlenhydrate/`, `Koolhydraten/ Hidratos Glucides/` and `Weglowodany: de carbono/` — four separate
  printed rows stating the carbohydrate name before the printed `58,9 g` even appears, on a fifth row
  (`Of which 58,9 g des`, itself carrying reconstruction debris from the adjacent sugars clause).
  `NutrientDeclarationBuilder`'s existing row-join capped a declaration at `MAX_DECLARATION_ROWS = 3`
  rows regardless of whether any of them carried a value, so the walk stopped one row short of even
  reaching the fifth. A separate, LOOSER cap (`MAX_NAME_ONLY_DECLARATION_ROWS = 6`) now applies only
  while the declaration is still accumulating name-only rows — the instant any row carries a value,
  the ordinary tighter cap governs every subsequent row exactly as before, so this can never let a
  declaration absorb an unbounded run of genuinely separate, value-bearing declarations. A new
  `isNutrientlessValueContinuation` predicate then admits the trailing value row itself, requiring
  **all** of: the row names no nutrient of any kind — carbohydrate, child, or unrelated (fat,
  protein, salt) — via the same `CarbohydrateTermAnchor.nutrientAnchors` check
  `isValueOnlyContinuation` already trusts, which is what makes this safe: a row this rule can reach
  could not have been `CARBOHYDRATE_CHILD` or named a different nutrient, because either would
  already have classified it as something else; and exactly one aligned value cell, so a row with a
  genuine column-ownership ambiguity (two ML Kit columns both claiming a number) is refused rather
  than guessed. The recovered value still passes through every existing gate unchanged —
  `CarbUnitAccompaniment`, column ownership, `CrossColumnRatioCheck`, plausibility and scale evidence
  all run exactly as they would on any other declaration's value cell; this only decides which
  physical row supplies the cell, never reads or accepts the number itself.
- **A `RecoveryCandidates`-suppressed candidate could never reach `ConfirmationEligibility`'s own,
  deliberately more authoritative, full-document cross-column re-check.** Traced from a routing
  question (two captures of the same product, same demonstrated `46`/basis contradiction, one routed
  to `RECOVERY` and the other to `CONFIRM_UNVERIFIED`) that turned out to be genuinely different
  evidence per capture — different OCR reads of the sibling value (`129` vs `12`) satisfied two
  different suppression rules (`contradicted()`'s localized-panel cross-column check vs.
  `ScaleAmbiguity`'s pairing rule) — and is correctly left unforced: the two captures are not the
  same evidence routed inconsistently, and no fix was made to make them agree. What the trace did
  surface is a real, independent structural gap: `RecoveryCandidates.candidatesOn`'s localized-panel
  cross-column veto (`contradicted()`) ran unconditionally, including inside
  `ofIncludingScaleRefusals()` — the population `ConfirmationEligibility` searches specifically
  because it re-asks the cross-column question against the FULL document, which can hold more
  supporting rows than the localized panel (the existing Hellmann's-mayonnaise measurement already
  documented in this codebase: a 188-element panel-scoped document reports `NotEnoughEvidence` while
  the full 325-element document correctly reports `Conflicting`). A candidate the *narrower* panel
  happened to flag never survived long enough to reach that richer, more authoritative re-check at
  all. `ofIncludingScaleRefusals()` now also exempts the localized cross-column veto
  (`requireLocalCrossColumn = false`); `RecoveryCandidates.of()` itself is unaffected — its own
  cross-column check is unchanged, because the plain recovery screen has no richer check standing
  behind it to fall back on. `ConfirmationEligibility`'s own full-document check still runs
  unconditionally on everything that reaches it, so a genuinely contradicted candidate is still
  refused — proven by a synthetic fixture (`RecoveryCandidatesLocalCrossColumnTest`) where the same
  candidate is `Conflicting` against a 3-row document and `Consistent` against the same document plus
  4 correcting rows, and `ConfirmationEligibility` correctly refuses the first and admits the second.
- **A declared-serving confirmation showed only the normalized-for-storage figure, never the printed
  pair.** A label stating `6 g carbohydrate / 18 g serving` normalizes internally to `33.3 g/100 g`
  for storage and calculation — correct arithmetic, but `VerificationScreen`'s
  `ScaleUnresolved` state showed only that normalized number, which is not printed anywhere on the
  package and cannot be visually compared against it, on the one screen whose entire purpose is that
  comparison. `CarbReading.derivedFrom` already existed specifically for this ("kept so the UI can
  say '33.3 g carbs/100 g — from 6 g per 18 g serving'"), but nothing rendered it. `VerificationScreen`
  gained two optional parameters (`printedAmount`, `printedBasisLabel`, both null by default, so
  every other caller and the ordinary per-100 scale-unresolved case are unaffected) that, when a
  declared-serving basis is present (`ConfirmationEligibility.isDeclaredServing`), render the printed
  pair as the primary confirmation line and the normalized figure as clearly-labelled secondary
  context beneath it. The internal `BigDecimal` this stores and calculates from is unchanged either
  way — this is a display-only reordering of numbers already computed, never a rounding or repair.

Interaction-count effect: the fragment-join and cross-column-recheck fixes each turn what was
previously a `CROP_FALLBACK`/`RECOVERY` dead end (crop or generic recovery menu) into a route that
reaches a proposal or confirmation directly from the automatic attempt, for the specific evidence
shapes each fix targets — no interaction was added anywhere, and the ordinary per-100 and
already-correct declared-serving-confirmation paths are unchanged.

Verified: JVM full suite **1874/1874** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, 197 XML
files — up from 1869 immediately before this pass's new test files). Lint exit 0, 23 warnings
(unchanged baseline; 0 findings in any changed file). Debug APK and minified release APK both build
from `clean`; R8 barriers re-checked on the release build — `CarbUnitAccompaniment` and
`RecoveryCandidates` retained as real classes (answer-path logic, correctly not stripped),
`ScanEvidenceRecorder`/`OcrDiagnosticsLogger` still correctly read `R8$$REMOVED$$CLASS$$` (unchanged
by this pass). New instrumented test `VerificationScreenDeclaredServingTest` (4 cases) run on the
`carbscan` emulator, 4/4 passing, counted from the JUnit XML rather than the console exit code.

**Non-vacuous by construction, not merely asserted:** the fragment-join fixture
(`20260907-164816-836`) was verified to read `NotFound` on the unmodified production code before this
change (temporarily reverted via `git stash`, re-run, restored) and `Confident 58.9/PER_100_G` after
— the exact printed value. The remaining 15 of 16 captures were replayed through the real parser
before and after this pass and produce byte-identical Pass-A-level outcomes, confirming zero
collateral change outside the one targeted geometry.

The connected 39-test real-image OCR corpus (`RealImageOcrTest` + `ProductionStillPipelineTest` +
`SelectedTableProductionTest` + `EvidencePipelineProductionTest`) on the `carbscan` emulator shows
**33/39 passing, 6 pre-existing failures — confirmed identical by exact name** against a
`git worktree` control at clean `ebe6fe2`: `RealImageOcrTest.kinderReadsItsPerPieceRelationshipWhenRecognitionSupportsIt`,
`RealImageOcrTest.theProseReaderIsNeverConsultedForAReadableTable`,
`RealImageOcrTest.gratedCheeseReadsItsPerServingFigureButNotItsDescriptor`,
`RealImageOcrTest.gratedCheeseReportsTheDigitRecognitionActuallyProduced`,
`ProductionStillPipelineTest.kinderIsReadCorrectlyThroughTheProductionStillPath`,
`ProductionStillPipelineTest.kinderStillReadsItsPerPieceRelationship` — same six on both sides, zero
difference either direction. All six are the emulator's own ML Kit misreading the kinder/grated-cheese
photographs (`Koolhydraten` → `nlhioonorate`, the digit-level grated-cheese non-determinism), a
degradation CLAUDE.md already records as recurring on this emulator independent of any parser or OCR
change — this pass touches neither. **Zero regressions**, measured, not assumed.

No P2 latency instrumentation or evidence-diagnostics enrichment was attempted, per instruction.
`docs/manual-qa.md` §37 is the remaining gate — nothing in this pass has been seen on physical
hardware.

### Fixed — OCR scan evidence review, 23-capture corpus (2026-09-06, NOT YET committed to main)

A device session (`docs/Scan evidence 06-09/`, a Samsung phone, 23 captures across 13 products) found
one release-blocking safety defect and one unnecessary-friction defect, both fixed and verified by
replaying the real evidence through the real production decision path. No OCR recognition, parsing,
row/column classification, or calculation rule changed — every fix is in the evidence-adjudication
layer (`AutomaticVerification`, `ScaleAmbiguity`, `ReadingEligibility`, `ScanPresentationDecision`).

- **`DISTINCT_OCR_AGREEMENT` could no longer settle absolute decimal scale on its own.** A red Lidl
  label printing `7,2 g/100g` was read as `12` by both a live pre-shutter frame and a native-resolution
  re-crop — two genuinely distinct physical observations, agreeing on the same systematic misread of
  the same damaged glyph. The app treated that agreement as sufficient to skip the value confirmation
  entirely (`CONFIRM_ON_CAPTURE`). Measured: two independent *observations* of the same optical defect
  are not two independent pieces of evidence about the *digits*. Only
  `AutomaticVerification.Route.CROSS_COLUMN` (the label's own other nutrient rows — evidence of a
  genuinely different kind) may now settle an `Unsupported` scale verdict; `DISTINCT_OCR_AGREEMENT`
  remains real verification (it still blocks a *conflicting* reading and still earns a proposal) but
  no longer settles scale alone. The cost, stated plainly: a small number of genuinely correct
  integers (previously confirmed via distinct-run agreement alone, e.g. `41g`) now cost one confirmed
  keystroke via focused entry rather than a bare tap — never discarded, never delayed past the
  frozen-photograph screen.
- **A US-style linear Nutrition Facts panel with no per-100 column fell to the crop-confirmation
  screen even when the recovery screen already had a fully-resolved answer.** `RecoveryCandidates`
  (via `ServingDeclaration`) already recognises `Serv. size: 1Tbsp (18 g)` + `Total Carb. 6 g` as
  `6 g / 18 g serving`, normalizing to `33.3 g/100g` — but `ScanPresentationDecision`'s automatic-veto
  fallback checked only `FocusedAmountEntry` (per-100-column shapes) before giving up to the crop
  screen, where that already-resolved answer was never shown. It now also checks
  `RecoveryCandidates.of(document)` before falling back, routing to the recovery screen instead.
- **Added a bounded, targeted native-resolution reread** (`TargetedRereadRegion`,
  `TargetedRereadTrigger`, wired into `SelectedTableResolution.resolve` via a new
  `EvidenceSource.TARGETED_REREAD`), tried at most once per capture attempt, only when a
  total-carbohydrate row and basis are already located but the digits are still in doubt (a
  scale-ambiguous/unsupported value, or a value declined for a corrupted unit glyph). The region
  always includes the resolved per-100 header band — never narrower than "header through value" — per
  the documented sondey/kinder lesson that a tighter crop can remove the very header a value needs to
  be placed under. Reuses `SelectedRegionRecognizer`'s exact bitmap-lifecycle/timeout/ownership
  mechanics (new `evidenceSource` parameter, defaulted so every existing caller is unaffected).
  **Deliberately shares the still capture's own `PhysicalObservationId` and `RecognitionRun`** — it is
  one more parse of the photograph already in hand, never a claim of independent physical
  corroboration, so it cannot itself manufacture a `DISTINCT_OCR_AGREEMENT`.

Verified: JVM full suite **1802/1802** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, 187 XML
files — up from 1783 at the start of this pass). Lint exit 0, 23 warnings (unchanged baseline). Debug
APK and instrumented sources both build/compile. All 23 captures replayed through the real
`SelectedTableResolution`/`ScanPresentationDecision` path with faithfully-derived geometry fixtures
(`tools/derive-session-fixtures.py`, element counts matching every bundle's own declared count
exactly): 2 of 23 actions changed (the two fixes above), 21 unchanged — zero collateral regressions
across the corpus. **Not done in this pass**: the connected OCR corpus and the changed screens were
not re-run on hardware or the emulator's virtual camera (which cannot exercise real recognition);
`docs/manual-qa.md` needs a new dated gate for this pass. Not yet committed to `main` — on a review
branch pending merge.

## 1.0.3 (versionCode 4) — RELEASED 2026-09-04, closed testing

Opened 2026-08-29, built from committed `7cbf78d` and **accepted by Play onto the closed track on
2026-09-04**. Archived with its artifact hash, size and signer in
[`docs/version-history.md`](docs/version-history.md); this section is kept here as the current
release and must not be edited — correct it there with a dated note instead.

**`versionCode 4` is now spent.** It is never rebuilt or re-uploaded; Play refuses a duplicate code.

### Play Store release notes (as published)

**This is the text that went into Play Console's *What's new*** — the owner's wording, recorded
verbatim rather than the draft that preceded it. 384 characters against the 500 limit. Checked
against §44 §7.1: no health claim, no mention of diabetes, no medical wording.

```
Nutrition label scanning gets a major upgrade: faster, safer readings with better support for multi-column, serving-based and American-style labels. Good scans now go straight to a quick calculation, while uncertain values ask only for what’s missing. You can also save a quick calculation as a product for later. Plus smoother recovery, better row tapping and many reliability fixes.
```

### Fixed — OCR evidence lifecycle (2026-09-05)

Live pre-shutter camera evidence was being recorded under one identity and queried under another,
so it was almost never actually available to corroborate a still capture -- not because of a wiring
slip between two call sites, but because a single counter (`captureSession`) was used both to
identify "which pre-shutter aim does this frame belong to" and "is this async work still current",
and those two questions need different bump timing. `CaptureEvidenceCoordinator` separates them.
Live evidence is now frozen atomically at the moment the shutter fires, before any other
shutter-handling side effect runs, so OCR latency (measured 477-2458ms on real hardware) can no
longer expire evidence that was genuinely present when the user pressed the shutter.

Every camera-derived recognition (`FULL_FRAME_PASS_A`, `FILTERED_PASS_A`, `SELECTED_REGION_OCR`) now
carries a real `PhysicalObservationId` identifying which physical photograph it came from, instead
of defaulting to `UNKNOWN` -- closing the gap that made the independent-observation verification
route (`DISTINCT_OCR_AGREEMENT`) structurally unreachable in production.

Closed the one path by which same-photograph recognition-run agreement (never a second physical
observation) could make an `Unsupported`-scale value confirmable with one tap
(`AutomaticScanAdvance.eligibility` was unconditionally telling `ReadingEligibility` that
corroboration settled decimal scale, regardless of what kind of corroboration was actually present).
The value is not blocked -- it still reaches the user through focused entry on the frozen
photograph with the stated basis preserved, exactly as before for the already-documented `41g`/`11g`
integer cases.

`EvidenceResolver` can now resolve a disagreement between two confident candidates when one is
explicitly, structurally contradicted by its own document's cross-column ratios and the other is
positively verified by the same check -- never by confidence, magnitude, or source order. A
disagreement where neither or both candidates are uncheckable still refuses exactly as before.

Fixed onboarding: a `SettingsRepository.setHasSeenOnboarding` write failure previously left the
*Get started* button permanently disabled with no error shown and no way to retry, because the
UI-layer double-tap guard was set `true` before the write and never reset on failure.
`OnboardingViewModel` now exposes an observable `Idle`/`Saving`/`Failed`/`Saved` state; navigation
happens only on `Saved`, and `Failed` shows a retryable message.

Nothing about the calculation, schema, migrations, the §10 lookup priority, barcode detection, or
any OCR *recognition* rule changed. No confidence threshold moved and no parser rule was relaxed.

### Fixed — the eighteenth session (2026-09-04)

A separatorless **pair** of values demonstrates that a common rescaling is equally consistent with
the recognised text — and that is true of both members, not only the left one. The app refused one
and offered the other.

- **Neither member of a separatorless pair is offered.** `ScaleAmbiguity` searched for a candidate's
  paired value only among elements to the *right* of it, so on a two-column carbohydrate row the
  left cell saw the pair and was withheld while the right cell saw nothing, reported "no paired
  value", and was then admitted by its declared serving basis. Measured on three captures of the
  2026-09-04 session: a protein bar printing `46 g / 100 g` and `12 g / 25 g reep` suppressed `46`
  and **offered `12 g / serving`** one tap from the calculator. The pair relation is symmetric, so
  both are now withheld.

  The rightward bound existed so a nutrient *name* could not pair with its own value. That job is
  actually done by the leading-digit rule — `Koolhydraten`, `Vetten` and `E471` are excluded from
  either side — and both controls are pinned by tests. A single-column label reads exactly as before.

  **No value is repaired**: `46` never becomes `4.6`. A withheld figure routes to focused entry with
  the frozen photograph, the highlighted row and the label's own basis preserved, where the user
  types the digits printed in front of them.

- A declared serving basis no longer rescues a **demonstrated** ambiguity. A serving sentence the
  label printed says which quantity a figure is measured per; it says nothing about where its decimal
  point is. Lone separatorless values under a declared serving — the Korean sauce's
  `6 g / 18 g serving` — are unaffected and still offered.

### Fixed — test contracts, not behaviour (2026-09-04)

- Four real-image OCR cases asserted `CandidateProvenance.FromRow`, which **no production code
  constructs any more** — the tabular path emits `FromDeclaration`. They were failing on a type name
  while the carbohydrate values they exist to protect were still correct. Migrated to accept either,
  and **strengthened**: the source rows must now also name no child nutrient, checked against the
  parser's own child vocabulary. `FromRow` proved that implicitly by carrying one row; a declaration
  may span several, so it has to be stated.

### Fixed — the seventh phone session (2026-09-02)

The sixth session's scale safety held on the device: the truffle label never displayed or offered
`89`. It was, however, **unrecoverable**. Tapping the carbohydrate value answered *"This looks like
sugars or fibre. Tap the total carbohydrate row instead."*, and returning to recovery left *Type it
in* disabled — a dead end on a label the app had otherwise read correctly.

- **A tap inside a merged row's total-carbohydrate clause is no longer answered with a fact about
  its sugars clause.** On this package ML Kit puts the whole declaration on one reconstructed row
  (`Kohlenhydrate 8,9 a, 1.3q(<19; waarvan suikers/dant sucres/tavon Žucker`), so the row classifies
  `CARBOHYDRATE_CHILD` — correctly — and every element on it, including the word `Kohlenhydrate` and
  the value `8,9` half a screen from the sugars word, was refused as sugars. Hit-testing now asks
  which printed *clause* the finger landed on, using the horizontal position a tap carries and the
  automatic path does not have. A tap inside the sugars clause is still refused.
- **Focused entry is reachable after an unsuccessful tap.** It was gated on a tap that produced
  nothing *and was not a child row*, so a merged row could never reach it; `FocusedAmountEntry`
  itself also required a `TOTAL_CARBOHYDRATE` row and returned null here. Both are fixed, and a
  second refused child-clause tap now offers focused entry rather than inviting a third identical
  attempt. It asks for the amount printed under **100 ml** and shows no basis picker.
- **Nothing was relaxed to achieve this.** Automatic classification is unchanged — both captures
  still classify `CARBOHYDRATE_CHILD` and neither produces a confident reading. `89` is still
  suppressed, `8.9` is never manufactured from the separatorless capture, `ScaleAmbiguity`,
  cross-run dispute suppression, unit accompaniment and child-nutrient protection are untouched, and
  no OCR pass was added.

### Performance — a real parser defect the session exposed

`141642-529` took **2142 ms** with **1432 ms in parse**, against 501–864 ms total and 55–199 ms
parse for every other capture in the same run. Measured in work counts rather than wall clock, that
289-element document did **51,792** `normalize` calls against 2,295 for its 262-element sibling —
22.5x the work for a 1.10x larger document.

The cause was the 2026-09-01 quadratic shape surviving in two functions: `ProseNutritionReader`
walked **every vocabulary term at every token position** and normalized the term inside that loop,
though the vocabulary is a compile-time constant. Both now use a cached `NutritionTerminology
.termWords`, mirroring the existing term cache. **51,792 → 2,060**, so the slow capture now does
slightly *less* work than its sibling, consistent with its size. Pinned by invocation-count
assertions, not by wall-clock time.

### Fixed — the sixth phone session (2026-09-02)

A truffle sauce printing `8,9 g / 100 ml` and `1,3 g / 15 ml` was recognised as `89` and `13` — the
decimal separator did not survive — and the app displayed **`89 g / 100 ml`**, a ten-fold error, on
two consecutive captures that reached the screen by two different routes.

- **A value another recognition run contradicted is no longer offered by recovery.** On
  `20260902-131511-970` two independent runs read `89` and `8`, the resolver correctly returned
  `Conflicted` — and the recovery screen then offered `89 g / 100 ml` as its first choice, because it
  was rebuilt from the winning document alone and had no way to know a dispute had happened. The
  refusal now travels with the hand-off. **Both** sides of a disagreement are suppressed, not just
  the loser: nothing available to the app says which reading is the printed one. Undisputed values,
  row tapping and manual entry are unaffected, so a conflicted capture degrades to *read the
  package*, never to a dead end.
- **A single unverified recognition no longer gets an ordinary confirmation when the evidence cannot
  establish the decimal scale.** On `20260902-131545-452` one run read `89`, the second found
  nothing, and the app showed a confirmation card. A confirmation is a question — *is this right?* —
  and it is a fair question only when the user can check the answer; here the separator was missing
  from *every* value on the label, so nothing on screen distinguished `89` from `8.9`. Such a reading
  now goes to focused entry with the stated basis preserved.
- **Why the existing cross-column check could not catch this.** The collapse is uniform: the printed
  `2,7 g` fat read `27 g`, `1,6 g` salt read `16 g`, `4,6 g` sugars read `46g`. Multiplying both
  sides of a ratio by ten leaves the ratio unchanged, so the table corroborated `89` exactly as
  strongly as it would have corroborated `8.9`. **Relational consistency establishes proportion,
  never absolute scale** — a separate question, so it gets a separate answer rather than a tightened
  threshold.
- **Nothing is repaired.** `89` never becomes `8.9`; no value is divided, shifted or inserted
  anywhere. The decimal point is what OCR is least reliable about, and unlike a refusal a wrong
  repair is invisible — the user sees a plausible number and has no reason to check it.
- **The rule keys on punctuation, not magnitude,** and is only ever asked of an *unverified* reading
  paired with a second value that also lost its separator. That is what keeps `41g` on
  `20260902-131357-353` — integer-like, no separator, and correct — advancing exactly as before, on
  the strength of three runs agreeing. There is no "large values are suspicious" threshold, and none
  may be added: such a rule would refuse flour and sugar while still admitting a collapsed `46`.
- **Edit no longer discards a basis the label stated.** Rejecting `89 g / 100 ml` and tapping *Edit*
  opened manual entry blank with **`100 g`** selected, because the action carried no basis at all and
  the screen's own default stood. The basis decides the unit the portion field asks a human to
  measure in, so typing the right figure there stored it against the wrong denominator with nothing
  downstream able to detect it. *Edit* from `/100 ml` now opens on `100 ml`, and from `/100 g` on
  `100 g`; the amount stays blank deliberately, since the action is reached precisely when the app's
  number was wrong or withheld.
- **The evidence bundle can now reconstruct this class without a screen recording.** `selection.txt`
  gained the cross-run dispute (which run read what), the surviving punctuation evidence behind a
  scale refusal, and the basis handed to Edit or focused entry with whether the amount was
  intentionally blank.

### Fixed — the fifth phone session (2026-09-02)

- **A carbohydrate figure can no longer be shown under a basis it never had.** When OCR damaged a
  `per 100 g` column header, the per-100 value bound to the neighbouring *per portion* column — the
  only one left within binding distance — and the recovery screen offered `72 g / serving` for a
  value the packet prints per 100 g. A column may now claim a cell only when no other value on that
  row sits closer to it, so a number whose own column was destroyed is left unresolved and
  suppressed instead of relabelled. The genuine per-serving figure is unaffected.
- **A verified reading now reaches you.** On the same capture the second recognition read the
  printed `72 g / 100 g` and the label's own other rows corroborated it — and the app showed generic
  recovery anyway. A reading a single recognition found is still only *proposed* when nothing
  corroborates it; when the table itself agrees, it is now the result.
- **A contradicted value is refused everywhere, not only automatically.** A figure the table's own
  rows refute — the `12` where the packet prints `72` — is no longer offered as a recovery choice
  either. Typing a value by hand is unchanged.
- **Verification is now tied to the exact reading being offered**, rather than to whichever
  recognition happened to be first in the list.
- **The camera permission notice no longer names only barcodes.** Opening the label scanner on a
  fresh install explained the camera was needed "to scan a barcode". No permission changed.

### Diagnostics

- Evidence bundles now record the recovery proposal: each offered choice with its displayed value,
  basis, provenance and derivation, and each *suppressed* number with the rule that removed it.
- The `serving:` line is relabelled `header-…` and points at that block. It reports the column
  header's own declaration, which is legitimately absent on a US linear panel; the serving size such
  a panel states in a sentence is what the screen uses and is now printed where it can be read.

### Fixed — the fourth phone session (2026-09-02)

- **A misread number can no longer become an answer without you confirming it.** The scanner
  read one cracker packet's `72,0 g` as `12,0 g` and showed 12 g of carbs per 100 g straight
  away, with nothing to press. It now checks a figure against the rest of the same table — every
  other row on that label agreed the reading was six times too small — and a figure it cannot
  check is shown for you to confirm rather than used on its own. Correct scans that the label
  itself corroborates still go straight through in two taps.
- **US-style nutrition panels are read properly.** A panel that prints everything in sentences
  ("Total Carb. 6 g (2% DV), Fiber 1 g") was being read as if the percentages were columns, and
  the carbohydrate figure was discarded. Both jars of sauce now offer 6 g per 18 g serving.
- **An ingredient list is no longer treated as nutrition information.** One sauce lists "brown
  sugar" among its ingredients, and that line was being counted as part of the nutrition table.
- **Tapping the carbohydrate row now selects that row.** Where two rows sat close together the
  app could attribute the tap to the sugars row below, refuse it, and leave the screen unchanged
  — so the tap looked like it had missed and people tapped again.
- **A row it found but could not read now asks for just that number.** Instead of repeating
  "tap the carbohydrate row", it says the row was found and asks for the value printed under
  100 ml. It does not ask which unit — it already knows, from the label.
- **A converted figure is shown rounded.** Reading 6 g per an 18 g serving showed 33.3 g per
  100 g on one screen and 33.33333333 on the next.

### Play Store release notes (fifth-session draft, re-check before upload)

```
Nutrition label scanning is more careful. A figure the app cannot double-check against the rest
of the label is shown for you to confirm instead of being used straight away, and a value is
never shown under the wrong "per 100 g" or "per portion" heading. American-style nutrition
panels read properly, tapping a row picks the row you meant, and a row the app finds but cannot
read asks for just that number.
```

### Fixed

- **A figure printed per 250 ml can no longer end up labelled "per 100 ml".** When the scanner
  could not read a drink's table by itself and asked the user to point at the number, it would then
  offer to use it "per 100 ml" — because the label says that somewhere, even though the number the
  user pointed at was printed under a different column. On one drink that turned 1,3 g per 250 ml
  into 1,3 g per 100 ml: right number, wrong measure, and nothing on screen to say so. Every figure
  the scanner offers now shows what it is measured per — *0.5 g / 100 ml*, *1.3 g / 250 ml* — and
  picking the second one converts it rather than relabelling it. A number the app cannot place on
  the label is not offered at all.
- **Nutrition panels that give a serving size instead of a per-100 column now work.** A label
  reading *Serv. size: 1 Tbsp (18 g)* and *Total Carb. 6 g* used to be a dead end: the scanner
  refused it, then asked whether the 6 g was per 100 g or per 100 ml, and neither answer was right.
  It now shows *6 g per 18 g serving* and works out the per-100 figure from the serving size the
  label printed, showing both.
- **Two-column drink labels are read as two columns.** Several photographs of the same drink had
  their *per 100 ml* and *per 250 ml* headings merged into one, so a value from either column could
  be reported under the other. They are now kept apart even where the second heading was damaged in
  the photo.
- **A value with a stray mark between the number and its unit is no longer thrown away.** One
  capture of a cracker packet read *72,0.g* rather than *72,0 g* and was rejected; the next
  photograph of the same packet was read correctly. That figure is now accepted, while a number
  whose unit is genuinely missing or wrong is still refused.
- **Percentages are never offered as carbohydrate figures.** Reference-intake values such as *9%*
  or *2% DV* could appear among the numbers the scanner suggested.
- **Tapping the sugars or fibre line says so.** It used to offer that line's numbers; it now
  explains that this looks like sugars and asks for the total carbohydrate line instead.
- **Exported scan diagnostics are checked before they are shared.** One export was cut short and
  produced a file that could not be opened, with nothing to indicate anything had gone wrong. An
  incomplete archive is now reported as an export failure instead.

### Changed

- **The first scan after opening the scanner is faster.** Text recognition now loads while the
  camera is being aimed rather than when the shutter is pressed.
- **A scanned nutrition label goes straight to the calculator.** Scanning a label with no product
  open used to hand the reading to the *Enter product* form, which would not let go of it until a
  product name had been typed and a record saved — so getting one carbohydrate figure out of one
  photograph meant creating a database entry the user never asked for. The reading now opens the
  ordinary calculator directly: type the portion, read the total, leave. Nothing is written to the
  device unless the user taps **Save product**, which is where the name is asked for and the only
  place it is needed.
- **The calculator names itself when there is no product to name.** A quick calculation is titled
  *Quick calculation* and drops the placeholder image, instead of showing an empty title over a
  blank tile — which read as a product record that had failed to load rather than as the reading
  just taken. Its controls are centred in the space the image gave back.
- **A scanned label lands ready to type.** The portion field takes focus and the keyboard opens as
  soon as the reading reaches the calculator, so the sequence is scan, type, read the total — with
  no tap in between on the one field the screen exists for. A saved product is deliberately
  unchanged: it opens with the portion you last used and its one-tap shortcuts visible, which a
  keyboard would cover.
- **At larger text sizes the calculator no longer looks broken.** From the 1.3× text setting
  onwards the portion controls need more room than the screen has, and the last one — usually
  *+ Add portion unit* — came to rest sliced horizontally through the middle of its letters at the
  edge of the result panel. It read as a rendering fault rather than as a hint that more was below.
  That edge now fades, and only when there is genuinely more to scroll to; at the default text size
  nothing changes at all.

### Added

- **Save product**, on a quick calculation only. Secondary to the result by design; asks for a name
  and nothing else, keeps the calculation on screen, and puts the product into Recents with the
  portion already remembered. Cancelling leaves the calculation exactly as it was.

### Changed — scanning

- **A good scan no longer asks you to approve a crop.** Capture already chose a rectangle for
  itself — the scan guide you were aiming with — and the next screen existed to have that rectangle
  confirmed before it could be read. When it was already right, which is the ordinary case, that was
  a tap that changed nothing. The reading now happens straight away, and the crop screen appears
  only when the result was not safe enough to show. Confirming the *value* is unchanged: the
  proposal card and its **Use 48 g / 100 g** button are exactly as they were.
- **The crop screen says when it is a fallback.** Reached after an automatic attempt it now reads
  *Couldn't read it automatically*, and while a pass is running it says *Reading table…* instead of
  telling you to drag corners the app is not waiting on. Previously it looked identical whether it
  was the first step after a capture or the hand-off from an attempt you had just waited through.

### Fixed — found in review of the above, each reproduced by a test that failed before the fix

- **A failed save looked like a tap that missed.** If the write failed, the naming dialog stayed
  open exactly as it was — and the message explaining what had happened renders on the screen
  *behind* it, so nothing the user could see said the product had not been kept. The dialog now
  closes on failure, which is what puts the message in front of them, with the action still there
  to try again.
- **A failure message outlived the attempt it described.** Having failed once, the warning stayed
  on screen through the next attempt and through a *successful* save, so a product that had just
  been saved still showed a message saying it had not been. Reopening the form now clears it.
- **A quick calculation added to a meal produced a blank line.** The meal takes its label from the
  product's name, and a quick calculation has none by design — so the item appeared on the plate as
  an empty row, with only its portion and carbohydrate figure to identify it, and the *Remove* label
  a screen reader announces had nothing after the word. It is now listed as *Quick calculation*.
- **A new capture could be dismissed as an unchanged crop of the previous one.** `captureLabel`
  cleared every other piece of per-capture state — the frozen photo, the proposed box, the reading
  flag, the "already tried automatically" flag — but not the region the last recognition ran over.
  That region is only cleared by *Retake*, and *Capture label* is reachable without it from the
  ambiguous, not-found and searching cards. Since both captures propose the same rectangle (both
  derive it from the scan guide), the new photograph's first *Read table* compared equal to the old
  photograph's and was skipped, telling the user a picture that had never been read would "read the
  same as before". The skip is now cleared on capture as well as on retake.
- **The post-attempt crop instructions were the ordinary ones.** `crop_body_after_attempt` was
  byte-identical to `crop_body`, so the conditional selecting between them did nothing and the new
  wording lived entirely in the title. The body now says the thing the generic copy cannot — that
  the box on screen *is* the one the app already tried, so moving it is what changes the answer.

### Fixed — from the physical-device recording of 2026-08-30

- **An impossible carbohydrate figure was offered exactly like a real one.** A red label printing
  about `7,9 g` produced `790` and `794` through the assisted path, and both were presented with the
  same two full-emphasis *Use / 100 g* and *Use / 100 ml* buttons an ordinary value gets — one tap
  from a figure that cannot exist, with nothing on screen saying so. 790 g of carbohydrate cannot be
  in 100 g of food, nor in 100 ml. Such a figure now gets **no ordinary accept action at all**: the
  number is still shown, said plainly to be wrong, and the field stays open to correct.
  **Nothing is repaired and nothing is clamped** — `790` is never quietly offered as `79.0` or
  `7.9`, because the decimal point is the one thing OCR is least reliable about, and a wrong repair
  is invisible where a refusal is not.
- **A basis the label had already stated was thrown away and then asked for again.** A coconut-milk
  table printed `per 100 ml` clearly enough that the column classifier read it, but because the
  *value* needed assistance the app asked *"2.5 g carbs — per what?"* and offered `/100 g` beside
  `/100 ml` — re-asking a question it had answered, with the wrong answer one tap from the right
  one. Confidence in the value and confidence in the basis are separate facts, and are no longer
  collapsed into one. A basis the label stated unambiguously is now carried through and named. A
  label stating nothing, or stating **both**, still asks — that ambiguity is exactly what the user
  is there to resolve.

### Changed — the fallback does less redundant work

- **Confirming an unchanged crop no longer repeats the same recognition.** When the automatic
  attempt declines, the crop screen opens on the very rectangle that attempt used, so tapping
  *Read table* without moving a corner re-ran the identical passes over identical input — a
  measured ~400 ms wait to reach the refusal already given, recognition being deterministic. It now
  goes straight to the assisted path, which is where a repeat of that outcome led anyway, and says
  *"Same box as before, so it would read the same."* A crop the user genuinely moved is always
  recognised: this is a shortcut through a known result, never a skipped check.
- **Framing guidance names the goal.** *Move closer to the nutrition table* became *Move closer —
  fill the frame with the nutrition table*. Measured cause: the region the automatic pass reads
  spans the **full width of the frame**, because the 12% safety margin around an already
  near-full-width scan guide clamps to both edges. Surrounding package text therefore cannot be
  excluded by aiming more carefully — only by getting closer — and the old wording left the user
  adjusting something that could not help. No new signal, no threshold change, and the shutter is
  still never blocked.

### Internal

- `ProductViewModel.startQuickCalculation` loses its mandatory `name` parameter — the coupling that
  forced the OCR path through product creation — and gains `saveQuickCalculation`. The calculation
  itself is untouched: both acquisition paths reach the same `CarbCalculator` through the same
  state, so there is no second formula and no second rounding.
- Provenance is carried, not flattened: a quick calculation is `OCR` / `UNVERIFIED`, and saving
  preserves the origin rather than relabelling it `MANUAL`.
- `CarbPlausibility` (domain) is a two-question phrasing of the ceilings `NutritionValueValidator`
  already owns — **one rule, not a second copy**, pinned by a test asserting the two agree across
  the range. `StatedBasis` (ocr) asks the existing `ColumnClassifier` what the table is measured
  per and reports it only when unambiguous. `CropChange` (ocr) decides whether a confirmed
  rectangle differs enough to be worth recognising, with a tolerance far below any deliberate drag.
- **No OCR rule was weakened.** No threshold moved, no confidence bar lowered, no second engine or
  parser added, and `EvidenceResolver` is untouched. The real-image corpus is unchanged.

### Internal — dead code removed

Nothing here changes behaviour; every item was already unreachable in production.

- **The pre-recognition crop is gone from the code, not just from the call sites.** The 2026-08-17
  capture-first pass stopped cropping the capture to the scan overlay before OCR — cropping cut the
  basis header off tall labels and cost both canaries — but it left the machinery in place with
  `region = null` passed at every call site. `StillImageLoader.loadWithRotation` loses the parameter
  and the crop branch, `StillImageLoader.load` (no production caller at all) is deleted, and
  `ScanRegionMapper.toPixels` with its `PixelRegion` type goes with them. `ScanRegionMapper.expand`
  is untouched and still load-bearing — it is the rectangle the fast path reads. Both KDocs, which
  still argued *for* cropping before recognition, now record why that was measured and reversed.
- **`ProductionStillPathBaselineTest` deleted.** Two tests, zero assertions, written to measure a
  baseline "before production code is touched" for a change that shipped; its KDoc described a
  production path (`StillImageLoader`'s ROI crop) that no longer exists. Not part of the
  nine-photograph corpus. `ScanRegionMapperTest` loses the 6 cases that tested only `toPixels`,
  keeping its 3 `expand` cases — which is the whole of the 1075 → 1069 JVM change.
- **21 unused strings deleted**, each verified to have zero Kotlin references independently of
  lint. Includes four `crop_handle_*` accessibility labels that described per-handle nodes that do
  not exist — the crop handles are drawn on a Canvas and the selection carries one
  `contentDescription`. Unused-resource advisories 21 → 0; total lint advisories 40 → 19.
- **Kept deliberately, with the reason recorded in its KDoc:** `ServingSizeParser.parse` has no
  production caller (everything moved to `parseDescriptor`, whose weight is optional), but deleting
  it would delete a *rule* rather than an unused function — "a count with no printed weight is not a
  weight mapping" has no other home, and countable-portions §5/§20 makes a false positive there the
  failure that matters. Also kept: `NutritionTableLocator` (retained-and-unwired by an earlier
  decision, with its measured failure in its own KDoc) and the two live-service diagnostics.

**Play Store release notes (draft — re-check before building):**

> Scan a nutrition label and get your carbs straight away. No product name, no saving, no setup —
> just the value, your portion, and the total. If you want to keep a product for next time, saving
> it is now a single optional step.

## 1.0.2 (versionCode 3) — 2026-08-29 — Closed testing

Opened 2026-08-28 by the live-search change below, under the one-version-per-code-change rule, and
extended the same day by the Search-a-licious migration, the search-hardening and accuracy passes,
and on 2026-08-29 by the light/dark theme and system-UI fixes.

**Uploaded and accepted by Play on 2026-08-29**, built from `29a4f3d`. The artifact's hash, size and
signer are in [`docs/version-history.md`](docs/version-history.md), which is the authority for what
a tester is reporting against.

**Device verification (owner, 2026-08-29, against the Play-delivered build): live search, Light and
Dark theme rendering, and barcode scanning all confirmed on hardware.** The reported status-bar and
dark-mode contrast defects are gone. Still unobserved: the two theme *override* combinations (app
forced opposite to the phone), an OCR label scan, and a calculation from a search result. See the
archive entry for the exact split.

### Fixed — reproduced by a test that failed before the fix

- **Live search asked Open Food Facts far more often than it is allowed to, and the refusals looked
  like an outage.** Search-as-you-type sent a request after every half-second pause in typing, with
  nothing capping how many that added up to. Open Food Facts allows about 10 searches per minute per
  address — shared by everyone on the same connection — so a minute of ordinary typing could exceed
  it several times over, and the server started refusing. Those refusals were shown as *"The product
  database is unavailable"*, which is what produced the results → loading → error → loading → results
  flicker on a real phone: the app had earned the failure and then blamed the database for it.
  Typing is now answered instantly from results already on screen, while remote searches are paced
  by one shared budget across the whole app. **Later in this version** the primary provider changed
  (see *Product search now uses a dedicated search service* below), and that budget of at most 9 per
  minute now applies to the Open Food Facts fallback, which is the endpoint that imposes it. Typing
  and Search still coalesce to one request per settled query, and repeatedly pressing Search or Try
  again still adds none at all.
- **A momentary search failure wiped the results you were reading.** While live search refreshed a
  list, a single failed request replaced that whole list with the full error screen — losing results
  that were still perfectly good and still tappable, for a request nobody had asked for. Open Food
  Facts' search endpoint answers 503 while otherwise healthy often enough that this happened
  mid-word. A refresh that fails now leaves the results in place and says so in a thin line above
  them, with *Try again*; the full error screen is still shown when a search fails with nothing on
  screen to keep, which is the case where those recovery options are the whole point.

### Changed

- **Product search now uses a dedicated search service, with the previous one kept as a backup.**
  Text search runs against Open Food Facts' purpose-built search service; the older search endpoint
  remains in place and answers automatically if the new one cannot. The change is about whether
  search works at all: measured on 2026-08-28, at a deliberately polite pace well inside its own
  limits, the old endpoint refused **five of seven** ordinary queries — *chocolate*, *gouda*,
  *nutella*, *bread* and *hagelslag* all came back unavailable. The new service answered every one,
  on the phone, in about a tenth of a second. Switching between the two is invisible: a search that
  falls back looks like one search, not a failure followed by a retry, and an error is shown only
  once both have failed.
  - **One known trade, and it costs no accuracy.** The new service does not publish the field the
    app uses to tell grams from millilitres, so more results now show their name, brand, size and
    photo without a carbohydrate figure beside them. Nothing is guessed — the app has never shown a
    figure whose unit it could not establish, and tapping a result still fetches the full product
    and its real values exactly as before. Search only helps you find the product; the numbers you
    count with come from the product itself.

- **Product search now updates as you type.** Previously a query sat in the field doing nothing
  until Search or the keyboard's Search key was pressed — the results were there to be had and the
  app waited to be asked. Typing now runs the search by itself, about half a second after typing
  stops. Pressing Search still works and skips that wait.
- **Home's search shows when it is refreshing.** Home had no refresh indication at all: its only
  loading state was the centred spinner shown when there were no results, so a refresh over an
  existing list was completely silent. It now shows the same thin progress line and the same
  inline refresh notice as the search screen, so the two cannot drift on what a refresh looks like.
- **Typing now updates the visible results immediately, without waiting for the network.** When the
  new query extends the one the results came from — typing `chocolate` after `choc` — the list
  narrows on the spot while the remote refresh is still pending. A query that is *not* an extension
  (`chocolate` → `gouda`, or shortening back to `choc`) shows a neutral waiting state instead: those
  results are not a partial answer to the new query, so presenting them would be wrong rather than
  merely stale.
- **Waiting for the app's own request budget no longer looks like a failure.** It shows a quiet
  *Updating…* and resolves by itself; only the newest query is kept while it waits, so typing five
  more characters during a wait costs one request rather than five.
- **Being rate-limited is now handled as a wait rather than reported as a broken database**, with no
  *Try again* button while a server-imposed backoff is in force — offering one there invites exactly
  the repeated requests the backoff exists to stop. The queued search resumes automatically, once;
  a second refusal is then reported plainly instead of retrying forever.
- **The wait before a typed query is searched is 0.5 s.** It went 0.6 s → 1 s earlier in this
  version's development, while every search still went to Open Food Facts' rate-limited endpoint and
  a conservative delay avoided spending a scarce slot on a mid-word hesitation. It returned to 0.5 s
  when the dedicated search service became the primary provider (below): that service answers in
  about a tenth of a second and imposes no such budget, so the extra half-second bought nothing and
  the user paid it on every query.
- **Results no longer blank out between queries.** Editing a query keeps the previous results on
  screen, under a thin progress line, until the newer ones replace them in one step. The old
  behaviour cleared the list on every keystroke, so the screen flashed empty-then-spinner-then-
  results for each character typed. Results are still dropped at once when the query is cleared or
  shortened below the three-character minimum, where nothing is coming to replace them.
- **Typing a query that is still too short is no longer treated as a mistake.** The "type at least
  3 characters" notice now appears only when Search is actually pressed, not while someone is on
  their way to a longer word — where, as a screen-reader live region, it announced on every
  keystroke.

### Hardening

- **Search requests now use the provider's POST search API.** What someone types is sent in the
  request body instead of the web address. A web address is the part of a request that proxies and
  server logs routinely keep in plain text, and a search here is a food someone is about to eat.
- **A search reply that arrives damaged no longer looks like "no such product".** If the search
  service answers that it has matches but none of them can be read, the app now treats that as a
  failed search and asks the backup provider — instead of telling the user their product does not
  exist. Both look like an empty list on screen, which is why this could not be noticed in use.
- **Punctuation in a product name is now searched for literally.** The search service reads its
  input as a query language, so brackets, `+` and `:` were being treated as commands rather than as
  part of the name: *Kinder Bueno (White)* found nothing at all, and *milk -chocolate* quietly
  searched for milk **without** chocolate. Names are now searched exactly as typed. Nothing about
  what the search field shows or accepts changed.

### Improved

- **Going back to a search you just ran is now instant.** Looking at *chocolate*, then *gouda*, then
  *chocolate* again reuses the results the app already has instead of asking the network a second
  time — so the list appears immediately, with no spinner and nothing to wait for. Results are kept
  for a few minutes and only while the app is open; nothing is written to the device. A search that
  failed is never reused, so a momentary problem cannot get stuck on screen, and tapping a product
  still loads its full, current details exactly as before.
- **Search results are fetched slightly leaner.** One piece of data the app requested and never
  displayed is no longer asked for. Nothing shown on a result changes.
- **Seven controls were too small to tap reliably, and two of them are the grams/slices switch.**
  Measured on a device, not guessed: the *Grams* and *Slices* buttons on the calculator were 32dp
  tall, *Add portion unit* 40dp, the search and clear buttons inside both search boxes 40dp, Home's
  meal bar 40dp and Home's *Enter manually* 43dp — against the 48dp minimum the rest of the app
  already uses. The mode switch is the one that matters: it decides whether the number you type
  means grams or a count, so missing it changes what the answer is *of*, not just its size. All are
  now full-size. Nothing moved, nothing was restyled, and no spacing changed — the tappable area
  grew to meet the text already there.

### Internal

- New `TouchTargetSizeTest` (instrumented, 5 cases) asserts every clickable node on Home, Search and
  the calculator is at least `Space.minTouchTarget` on its short side, in **dp**, so the result does
  not depend on device density. Text fields are excluded by `IsEditable`, and zero-sized (scrolled
  out of view) nodes are skipped so an off-screen node cannot fail for the wrong reason.
  **Why a test rather than a review habit:** the convention was already established and applied
  everywhere else, and seven controls still shipped under it, because every place that misses it is
  a place where something *else* silently overrides the intent — an `IconButton` in a text field's
  decoration slot is measured by the field, not by its own 48dp default; Material 3's `FilterChip`
  is 32dp by default; and on Home's *Enter manually*, `.height(48.dp)` was written **before**
  `.padding(top = 4.dp)`, so the padding was applied *inside* the 48dp box and the button measured
  44. That last one is the case worth remembering: the line that looks like the fix **was** the
  defect, so reading the code argued the opposite of the truth. Fixed by ordering padding first;
  modifier order is load-bearing and silent when wrong.
  Negative control, run against the unfixed code: every case fails, reporting
  `Search = 40x40dp`, `Clear search = 40x40dp`, `Grams = 75x32dp`, `Slices = 73x32dp`,
  `+ Add portion unit = 137x40dp`, `Enter manually = 371x43dp`. At 1.8× font scale the chips
  measured 35dp, so a large font does **not** rescue them — which is why every fix is
  `heightIn(min = …)` rather than a fixed `height`, so the control still grows with its text.
- Live and explicit search share **one** request pipeline (`MutableStateFlow<SearchRequest>` →
  `flatMapLatest`), so a debounce and a keypress cannot issue two requests for the same query.
  Typing a nine-character word costs one request, not nine.
- Stale-response protection is a request-generation check at the single point where a result is
  written into state, deliberately **not** left to coroutine cancellation. A response for an
  abandoned query cannot reach the screen even if its transport ignores cancellation and completes
  anyway.
- Searches run in their own child coroutine rather than in the collector. `collectLatest` waits for
  the previous block to unwind before starting the next, so a transport slow to cancel stalled the
  pipeline and the *next* query was never sent — found by negative control, not by reading the code.
- `SearchUiState` gained one field, `refreshFailed`. It distinguishes the two failures by what the
  user stands to lose rather than by what went wrong — both carry the same `LookupError` — and is
  always false when there are no hits, so a renderer can treat the two as exclusive. Both screens
  now test `hits.isNotEmpty()` **before** `error != null`; the old ordering is why a refresh failure
  could take the region away from results that were still good.
- New `RemoteSearchGovernor` (pure Kotlin, `domain/`, injectable clock) owns the Open Food Facts
  search budget: `MIN_INTERVAL_MS = 7000`, giving `MAX_REQUESTS_PER_MINUTE = 9`. Held by
  `AppContainer` as a single instance because Home's inline search and the search screen are two
  separate `SearchViewModel`s — a per-ViewModel cooldown would let the two most-likely-consecutive
  screens spend the same budget twice.
- Coalescing is structural rather than a queue: `requests` is a `MutableStateFlow` holding at most
  one desired query, and `flatMapLatest` discards the settle wait *and* the governor wait whenever a
  newer query arrives. There is no data structure in which a backlog of old prefixes could form.
- An attempt is recorded when a request **starts**, for every outcome, and cancellation does not
  refund it — a request that has left the device has spent the quota whatever the app does with the
  answer.
- 429 was already classified as `RATE_LIMITED`; `Retry-After` was being discarded. New
  `RetryAfterHeader` parses both RFC 9110 forms and returns null (never zero) for anything
  unusable, so a malformed header falls back to a conservative 60 s rather than reading as "retry
  now". The governor takes the stricter of its own interval and the server's backoff, caps any
  server-named backoff at 5 minutes, and never lets a later laxer refusal shorten an earlier one.
- The automatic resume after a rate limit is bounded to **one** attempt. Written unbounded first,
  which produced request → 429 → re-arm → request forever; the test suite hung on it.
- `SearchUiState` gained `awaitingRemotePermit`, `rateLimited` and `narrowedLocally`. The remote
  result set is kept separately from the displayed list so narrowing is non-destructive and can
  widen again.
- Two defects found by writing the tests: `waitUntilPermittedMs` computed `Long.MIN_VALUE - now`
  for "no rule applies", which **underflows** to a ~292-million-year wait and parked every first
  search (invisible at a clock of 0, which is why the first governor test passed); and the
  explicit-search dedupe guard swallowed **Retry** entirely, because a finished search leaves the
  requested query set. Both are now pinned by their own tests.
- `LIVE_SEARCH_DEBOUNCE_MS` (600 → 500 earlier in 1.0.2) is replaced by `REMOTE_SEARCH_SETTLE_MS`
  (1000, then **500** once the primary provider changed — see below). The request rate is bounded by
  the governors, not by this constant.

#### Search-a-licious as the primary provider (later in 1.0.2)

- **The feasibility gate was measured before anything was wired.** Legacy `cgi/search.pl`, at 7 s
  spacing: **503 on 5 of 7** representative queries. `search.openfoodfacts.org/search`: twelve
  back-to-back requests, all 200, 136–202 ms, no throttling, no auth. Verified again end-to-end on
  the emulator through the production wiring: **7/7 queries, 20 hits each, 78–106 ms** after the
  first. Bench: `SearchALiciousLiveDiagnosticTest` (prints, asserts almost nothing — a network test
  that fails the build on a flaky connection teaches the team to ignore it).
- **`product_quantity_unit` is not in the Search-a-licious index** — 0 of 140 hits across seven
  queries, and requesting it by name returns nothing rather than erroring. It is
  `PackageBasisResolver`'s primary evidence, so the basis now resolves from free-text `quantity`
  alone on that path and resolves less often (`pasta`: 3/20 vs the legacy 18/20). **No resolver rule
  was weakened to compensate.** A hit with no basis shows no number, which is the existing §13 rule;
  the authoritative figure still comes from the canonical barcode lookup after selection.
- **`brands` is a JSON array here and a comma string on the legacy path** (137 of 140 hits).
  `FirstOfStringOrArray` reads either, scoped to that one field for the same reason
  `LooseNumericText` is — the carbohydrate values keep strict typing.
- `langs=nl,en` is load-bearing, not decoration: without it `product_name_nl` is absent from every
  hit, and Dutch recall collapses (`hagelslag` 449 matches with it, 26 without). This is **input
  recognition**, not localization — the UI stays English (owner decision 10).
- `FallbackProductSearch` (pure, `domain/`) is itself a `ProductSearchSource`, so no ViewModel or
  screen knows there are two providers — which is what makes the migration reversible: pointing
  `AppContainer.searchSource` at the legacy source alone restores the previous behaviour exactly.
  Fallback-eligible: `OFFLINE`, `TIMEOUT`, `SERVER`, `MALFORMED`. **Not** eligible: `RATE_LIMITED`
  (answering "you are asking too often" by asking elsewhere is the behaviour the limit exists to
  stop) and, crucially, a legitimate `NoMatches` — that is an answer, and falling back on it would
  double the cost of every search for something genuinely absent.
- **A cancelled query cannot spend a fallback request, and that needed an explicit guard.** Found by
  test, not by reading: a primary whose transport ignores cancellation returns an ordinary `Failed`,
  and `fallback.search` may then run to completion without ever suspending — so nothing would have
  thrown. `currentCoroutineContext().ensureActive()` before the fallback call is what closes it.
  `CancellationException` is never caught anywhere in the chain.
- **The governor moved out of the ViewModel and down to the provider it protects.** It sat above the
  provider boundary, so leaving it there would have made every primary query wait out an interval
  sized for a different service. `GovernedProductSearch` wraps the legacy source only, keeps
  `MIN_INTERVAL_MS = 7000` and the shared cross-screen budget, and **refuses immediately rather than
  waiting** — a 7 s delay behind an already-failed primary is the stacked wait this migration must
  not create. The primary has its own instance at `PRIMARY_MIN_INTERVAL_MS = 300`.
  `RemoteSearchGovernor`'s clock parameter had to stay **last**: existing callers use a trailing
  lambda, and adding the interval after it silently rebound every one (caught by the compiler).
- Debug-only `SearchProviderLog` / `LogcatSearchProviderLog` (`adb logcat -s JtcSearch`) answers the
  one question device testing cannot answer by looking: which provider served this query. **No query
  text is ever logged** — stage, provider and result count only.
- **A pre-existing ViewModel test was measuring the wrong budget** and is re-aimed rather than
  relaxed: it asserted OFF's 9/min ceiling against traffic that now goes to a provider without one.
  The legacy budget is asserted where it is now enforced, in `GovernedProductSearchTest`.
- **One integration test was found vacuous by negative control and fixed.** The stale-fallback case
  passed with the generation guard deleted — it was measuring `flatMapLatest`, not staleness. It now
  uses a `NonCancellable` fallback, the only fake that reproduces the hazard, and fails without the
  guard. Same trap as the Dutch header fixture and the soft-keyboard geometry test.
- **Search-a-licious moved to `POST /search`.** Identical `q` semantics to the GET form (verified
  against the service's own OpenAPI document), so this is a transport change only. `langs` and
  `fields` are JSON **arrays** in the POST schema where the query string took comma-joined strings.
- **`@EncodeDefault` on the request body is load-bearing.** kotlinx.serialization omits a property
  equal to its default, and the shared `Json` does not set `encodeDefaults` — so every request would
  have serialised to `{"q":"…"}` alone and the *server's* defaults would have applied: `page_size` 10
  instead of 20, `langs` `["en"]` instead of `["nl","en"]` (which is what makes `product_name_nl`
  appear at all, so Dutch recall would have collapsed), and no field filter, pulling ~13 KB per hit.
  Every request still succeeded, so the failure was invisible; caught by asserting the request body
  rather than the outcome.
- **`NoMatches` vs `MALFORMED` is now decided by whether the provider *claimed* matches**, never by
  the mapped list being empty — which is true in both cases and is what made the original bug
  invisible. `hits` non-empty **or** `count > 0` with nothing usable ⇒ `MALFORMED` (fallback-
  eligible); empty `hits` with no positive `count` ⇒ `NoMatches` (an answer, no fallback). A mix of
  valid and malformed records still returns the valid ones — one bad record never discards good ones.
- **`SearchALiciousQuery.escape` prefixes Lucene's reserved characters**, in the provider only. The
  wider rule was chosen over a narrower one on measurement: whether a character acts as an operator
  depends on **position**, not identity (`(` is inert inside a word, an operator around one), and
  escaping the full set changed **no** query that already worked — `M&M's`, `Ben & Jerry's`,
  `70% chocolate`, `Haagen-Dazs`, `7-Up`, `Lay's`, `Côte d'Or` all returned identical counts and
  identical top hits. Apostrophes, `%`, `.`, `,`, spaces and all non-ASCII are untouched. The legacy
  fallback receives the user's text verbatim — it has no query language, so the same escaping there
  would send literal backslashes into a search that would match nothing.
- Nine negative controls run and restored byte-for-byte (hash-verified): GET restored / query in the
  URL (3 fail), unusable-hits→`NoMatches` (4), `MALFORMED` made ineligible (2), `NoMatches` made
  eligible (3), `ensureActive` removed (1), escaping removed (15), `@EncodeDefault` removed (1),
  generation guard removed (7). None vacuous.
- No change to the search request pipeline's generation check, the dedupe rules, query
  normalization, local narrowing, the minimum query length, the calculation, the schema, migrations,
  the §10 lookup priority, barcode detection, any OCR rule, or the 30 s product-refresh window. The
  canonical product path still reads Open Food Facts' product API directly, not the search chain.
- `CachedProductSearch` is a `ProductSearchSource` decorator wrapping the **primary only**, inside
  the chain. Wrapping the primary rather than the whole chain is what keeps a cached result's
  provenance answerable — a legacy answer is never filed under the primary's name — and it makes
  "a cache hit does not reach the fallback" structural rather than a rule. `AppContainer` holds one
  instance, so Home's inline search and the search screen share it for the same reason they already
  share one governor. 20 entries, 5-minute TTL, access-ordered LRU, memory only.
- Only `Found` is cached. Every `Failed` and `NoMatches` is passed through untouched: a cached 503
  would outlive the outage it described, and a cached "no matches" would tell someone a product does
  not exist because it did not five minutes ago, in a database strangers edit continuously. A
  cancelled search writes nothing, because the delegate never returns.
- A future `storedAtMs` counts as expired rather than fresh — a backwards clock change would
  otherwise pin an entry until real time caught up. Same rule and same reasoning as the 30 s
  product-refresh window.
- **Phrase boosting was evaluated and is not available on this deployment.** `boost_phrase` does not
  exist in the service's OpenAPI document (zero occurrences of "boost" or "phrase") and sending it
  is accepted with HTTP 200 and changes nothing at all — the most misleading of the three possible
  answers, since it would have looked enabled. Free-text Lucene phrase syntax does not work either:
  `"nutella"` returns **0**, `(coca cola)` returns 0, `coca^2 cola` returns 0 and `coca OR cola`
  returns **HTTP 500**, while `brands:"coca-cola"` returns 3283 and the service's own documented
  example works — so quoting is honoured only as a field-filter value. Recorded as a re-runnable
  diagnostic in `SearchALiciousLiveDiagnosticTest`, and it independently re-confirms the escaping.
- `SEARCH_FIELDS` dropped `lang`, which was requested and read nowhere. Measured before removing:
  240 bytes per response (12 bytes × 20 hits, 2.3%) across five queries with the **mapped products
  identical** for every one. The request's `langs` is untouched — that is what makes
  `product_name_nl` arrive, and it is a different thing from the per-hit `lang` echo.
- The field-list assertion is now an exact-list comparison. The previous `contains` checks could not
  see a field being **added**, which is precisely how `lang` went unnoticed; proven by negative
  control, which the old assertions did not catch.
- `page_size` stays at **20** on evidence: across the 48-query benchmark, Top20 (37/39) exceeds
  Top10 (36/39) by exactly one query, so a larger page would enlarge every response to buy at most
  one position.

#### Light/dark theme and Android system UI (later in 1.0.2)

Reported from a physical device: status-bar icons disappeared in Light mode, and several Settings
and Home elements were unreadable in Dark mode. Presentation only — no calculation, schema,
migration, §10 lookup priority, barcode, OCR, search or navigation behaviour changed.

- **Status-bar icons were unreadable in Light mode.** `enableEdgeToEdge()` was called with no
  arguments, so the bars resolved their own light/dark from the **device** configuration while the
  app's colours followed the user's selection — two authorities for one question. Choosing Light on
  a dark phone produced light icons on the cream background, which is invisible. Bar appearance now
  follows the **selected app theme** in every combination: Light theme → dark icons, Dark theme →
  light icons.
- **Dark-mode text and icons were black on near-black.** The Settings back arrow, the Settings
  title, the *Haptic feedback* row and Home's gear icon all rendered black in Dark mode. One cause,
  not four: the screens paint themselves with `Modifier.background(colorScheme.background)`, which
  fills a colour but provides no `LocalContentColor` — Material3's `Surface` is what normally does
  both, and the app has one, inside a dialog. Everything that did not name a colour therefore
  inherited `LocalContentColor`'s default of `Color.Black`. `JustTheCarbsTheme` now provides
  `onBackground`, rather than wrapping every screen in a `Surface` that would double-paint
  backgrounds the screens already draw (and would fight the camera screens, which are deliberately
  black in both themes).
- **Navigation-bar treatment now matches the theme.** Icon appearance follows the effective theme,
  and API 29+ contrast enforcement is disabled — its translucent scrim read as a grey band matching
  neither theme with three-button navigation. Safe only because icon contrast comes from the
  appearance flag against a solid, known theme colour. Gesture and three-button navigation both
  remain usable; API 26–28 is unchanged, guarded by a version check.
- **Theme switching is unchanged and still immediate.** The manifest declares
  `configChanges="…|uiMode"`, so this Activity is never recreated — which is also why the one-shot
  `onCreate` call could never re-run. The bar flags are applied from inside the composition, so a
  preference change reapplies them without an Activity restart, a flash or a navigation reset.

### Internal — theme

- New `resolveDarkTheme(themeChoice, systemInDarkTheme)` is the single authority both the Material
  colour scheme and the system bars read, so the two cannot drift again. Pure and system-free — the
  device state is a parameter, which is what makes it JVM-testable without an emulator.
- New `ThemeResolutionTest` (JVM, 10 cases) pins the six device-theme × app-selection combinations
  plus the two properties the consumers rely on: an explicit choice must ignore the device entirely,
  and `SYSTEM` must follow it in both directions. Verified non-vacuous by negative control —
  reintroducing the device read into the `LIGHT` branch fails 2 cases, including the one that
  mirrors the reported defect. The instrumented `ThemeDefaultTest` is unchanged and still asserts
  the **rendered** background, which is the seam where a wrong default is visible.
- Only `Theme.kt`, `MainActivity.kt` and the new test changed. `Color.Black`/`Color.White` elsewhere
  was audited and deliberately left: the camera screens are intentionally black in both themes with
  explicitly tinted foregrounds, and onboarding's white sits on saturated brand backgrounds, with
  its one theme-background slide already using semantic tokens.

### Verification — 1.0.2 release candidate

- **JVM 1015/1015** (up from 1004), 0 failures, 0 errors, **0 skipped**, `--rerun-tasks`, counted
  from JUnit XML rather than a wrapper exit code.
- **Instrumented 248/248** in **one whole-suite run**, 0 failures, **0 ignored**, counted from
  instrumentation status codes. Taken **after** every change in this version, so the figure covers
  the release rather than predating part of it.
- **Real-image OCR corpus 37/37**, unchanged — `RealImageOcrTest` 15, `ProductionStillPipelineTest`
  8, `SelectedTableProductionTest` 6, `EvidencePipelineProductionTest` 8.
- **Room migrations 10/10** (`JustTheCarbsDatabaseMigrationTest`).
- **Lint exit 0 on both debug and release**, 0 errors, **41 advisories** — unchanged baseline, so
  this version's work added none.
- **Debug APK and release AAB both build from `clean`.**
- **OSV dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities**, control
  query positive.
- **R8 privacy barriers re-checked** on the release build's `mapping.txt`: `ScanEvidenceRecorder`
  and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`
  and `ScanTrace` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate` and
  `PackageBasisResolver` retained as real classes. Release manifest: three disclosed permissions,
  one exported component of ours (`MainActivity`), **no `FileProvider`**.
- **Figures above are JVM and emulator.** Physical-device confirmation was taken separately, after
  upload — live search, Light/Dark theme rendering and barcode scanning all verified on the
  Play-delivered build (owner, 2026-08-29). The artifact hash, size and signer are in
  [`docs/version-history.md`](docs/version-history.md).

### Play Store release notes

```
Product search now updates as you type, with faster and more reliable results. Returning to a
search you just ran is instant, and results stay on screen while the next ones load instead of
blanking out. Product names containing brackets, symbols or punctuation now find the right
products. Also fixes status bar icons being invisible in light mode, and text and icons being
hard to read in dark mode, with better light and dark theme integration throughout.
```

457 characters, within the 500 limit.

Internal notes, not for Play: the provider names, the request budgets, the settle delay, the
fallback architecture and the move to POST are engineering details with no user-facing wording —
the privacy improvement is real but describing it would need the reader to know the search text was
previously in the address, which is not something the app ever showed them. The fewer preview
figures on search cards are also deliberately unmentioned — tapping a result still fetches the full
product, so there is no user-visible loss to describe. The theme entries are worded as the two
things a tester can see and check — light-mode status bar, dark-mode readability — rather than by
mechanism; `LocalContentColor` and edge-to-edge mean nothing to a reader of this field.

---

## 1.0.1 (versionCode 2) — 2026-08-28 — Closed testing

The **first update of the closed beta**, uploaded and accepted by Play on 2026-08-28. Full entry,
including the artifact hash, size and signer, in
[`docs/version-history.md`](docs/version-history.md).

A small, conservative quality pass taken during the closed beta. No feature work, no schema change,
no migration, no new permission or dependency, no change to barcode scanning, and no change to the
calculation or to any OCR safety rule.

### Fixed — reproduced by a test that failed before the fix

- **Every new product was fetched from Open Food Facts twice.** Not an edge case and not a race:
  *every* first-time scan cost two requests. On a cache miss the lookup downloaded the product and
  saved it, and the background refresh that runs immediately afterwards then found that fresh row,
  decided it had something to refresh, and re-downloaded the same barcode microseconds later — for
  a budget of 15 reads per minute shared by everyone on the same connection. A freshly downloaded
  product is now stamped as synced, and a refresh is skipped for any product synced within the last
  30 seconds — which suppresses that immediate duplicate, and also means reopening the same product
  within half a minute does not re-check it. Anything cached longer ago than that still refreshes
  normally, which is the only way a reformulated product can be noticed. Measured before and after:
  a first-time lookup went from two requests to one.
- **A dragged crop rectangle moved a fraction of the distance your finger did.** Drag events arrive
  as small increments, and the handler recomputed each one from the rectangle as it stood when the
  gesture *began* rather than as it stood after the previous increment — so the increments replaced
  each other instead of adding up. Measured: a drag delivered as ten 10-pixel steps moved the
  rectangle 10 pixels instead of 100. Resizing from every corner was affected the same way, and a
  second drag restarted from the original rectangle, discarding the first.

- **Saving a manually entered product could fail with nothing on screen to say so.** If the write
  failed, the Save button simply became tappable again and the screen was otherwise unchanged — no
  message, no error, nothing. The only available reading was that the tap had not registered, so the
  natural response was to tap again and fail again. A failed save now says the product was not
  saved; the message clears as soon as you edit a field or the next save succeeds. A related case
  was also corrected: when a product carrying a scanned portion failed to save, the app moved on to
  the calculator for a product that did not exist. It now only does that when the product genuinely
  landed and it was the portion alone that failed — which is what the existing message about the
  portion has always described. The two writes are made as two separate steps so which of them
  failed is known from where the failure happened; an earlier draft of this fix inferred it
  afterwards by checking whether a product row existed, which gave the wrong answer whenever that
  barcode was already stored from an earlier scan — a failed product write was then reported as a
  portion failure and the calculator opened on the old record.

### Improved

- **Searching for a product name shorter than three characters did nothing at all.** The search is
  deliberately refused below three characters — two letters match thousands of products — but the
  refusal was silent: no request, no spinner, and the same "type a product name" prompt the screen
  already showed. The screen now says a longer query is needed, and announces it for screen readers.
  Nothing about when a search is actually sent changed.

### Changed

- **The scanner starts reading a captured label sooner.** Live camera frames stop being analysed
  the moment you tap capture, instead of finishing a full parse whose result was then thrown away.
  That parse sat directly between the shutter and the reading of the captured photo, so discarding
  it earlier shortens the wait. Nothing about what the scanner accepts, refuses or reports changed.
  The framing hint can also no longer appear over the capture screen after you have tapped capture.

### Hardening — no defect reproduced, kept as protection

- **The label scanner's autofocus fallback no longer runs after the screen closes.** Capture waits
  up to 1.2 s for autofocus and fires the shutter anyway if it never reports back; nothing cancelled
  that fallback, so closing the scanner within that window left it queued to run against a camera
  already torn down. That state is reachable by reading the code, and the fallback now does nothing
  once the screen is gone. **The resulting failure was never reproduced on a device**, so no
  specific crash is claimed — this is defensive, not a fixed crash report. The previous draft of
  this entry described it as a crash; that was not established and has been corrected.
- **The crop screen no longer shares its grabbed-corner state across the whole app.** Which corner a
  drag had hold of was kept in state shared by every scan in the process rather than per screen, and
  a drag interrupted by *Retake* could leave it set. It is now per screen, so a new capture cannot
  inherit it. **No misbehaviour was ever reproduced from this** — an earlier draft of this entry
  claimed the next scan's first drag would resize instead of move, which was not established and has
  been corrected. This is state isolation, not a fixed defect.

### Internal

- The lookup test's fake cache previously discarded everything written to it, which is precisely
  what hid the double fetch: with nothing stored, the refresh returned early and its request never
  appeared. It now persists, and four cases cover the fresh lookup, the cached refresh, overlapping
  lookups and cancellation. All four fail on the old code.
- Crop gesture state extracted to a pure class with 8 JVM tests covering accumulation, per-corner
  resizing, consecutive gestures, interrupted gestures and the bounds rules. Verified non-vacuous:
  reintroducing the old captured-value read fails exactly the accumulation cases.
- **The instrumented flake is root-caused and fixed.** It was the soft keyboard, not timing and not
  a layout defect: Gboard is a real 641-pixel window over the bottom of the screen, and the
  assertions that failed were on elements pinned there. Measured — the meal bar sits at a stable,
  fully on-screen position when its test runs alone, and with the keyboard disabled the class passed
  19/19 three times, while with it enabled exactly one arbitrary test failed per run. The tests now
  dismiss the keyboard after typing, as a user does. No retries, no `@Ignore`, no sleeps and no
  weakened assertions were used.
- Five repository tests pin the new refresh-freshness rule, including the two cases that decide
  whether it is safe: a never-synced product still refreshes, and a sync timestamp in the future
  (a device clock change) is not read as freshness.
- A lookup that fetches from the network now hands back the same record it caches. It saved a copy
  stamped with the sync time but returned the unstamped original, so the two disagreed about when
  the product was last synced. Nothing read that field off the returned value, so no user-visible
  behaviour changed; two tests now pin that the returned and cached records match, and that a cache
  hit is still returned untouched.
- Ten tests pin the two failure notices above: six on the manual-entry save (reported, cleared by an
  edit, cleared by a later success, plus the three failure-classification cases — product failure
  over an existing local row, portion failure after a successful product write, and full success
  still navigating) and four on the search refusal (reported, cleared by an edit, cleared by a
  longer submission, and **not** raised for a blank field — an empty box is not a refused search).
  Verified non-vacuous by negative control: removing each notice, and reinstating the discarded
  after-the-fact failure inference, each fails exactly its own tests and nothing else.
- `ProductRepository.saveProductWithPortionUnit` now has no production caller — manual entry makes
  the same two writes itself so it can tell which one failed. It is left in place with its existing
  repository tests rather than removed as part of this change.
- **JVM 801/801** (up from 791) and **instrumented 218/218**, both 0 failures, 0 errors and 0 skipped,
  counted from JUnit XML / instrumentation status codes rather than from a wrapper exit code. The
  37-test real-image OCR corpus is 37/37, unchanged. Lint exit 0, 0 errors, 41 advisories. OSV
  dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query
  positive. The instrumented suite completed as **one whole-suite run** (218/218, 0 ignored,
  15m25s) taken **after** every change in this version, including the two that add a line to a
  rendered screen — so the figure covers this release rather than predating part of it.

### Play Store release notes

**As pasted into Play Console's *What's new* on upload.** 411 characters, within the 500 limit.

The label-scan change is worded as *reducing the wait after capture* rather than as "faster": the
work removed is real and measured in the code, but no before/after figure was taken on a physical
device, and the archive's rules forbid promising a fix that has not been verified on one.

```
Fixes a problem where dragging the crop box while scanning a nutrition label moved it far less than
expected. Searching for a very short product name now explains why it needs more letters instead of
appearing to do nothing, and a product that fails to save now says so instead of looking like the
button was missed. Uses less mobile data when looking up a product, and reduces the wait after
capturing a label.
```

---

## 1.0.0 (versionCode 1) — 2026-08-26 — Internal testing → Closed testing

First build delivered to testers via Google Play, and the version currently on a track. The **same
artifact** was promoted from internal to closed testing — one bundle, one hash, two tracks, not two
releases — and the closed-testing period is running with 12+ testers opted in. Full entry, including
the artifact hash, in [`docs/version-history.md`](docs/version-history.md).

### Added

- Scan a food barcode, enter a portion, read the carbohydrate grams. Open Food Facts lookup with a
  local cache, manual barcode entry, and search by product name as a fallback from a failed lookup.
- **Nutrition-label scanning.** Capture a label, confirm the table with a draggable rectangle, and
  read the per-100 figure. Where automatic reading is not confident the frozen photo stays on
  screen and you point at the value yourself, rather than the scan dead-ending.
- **Countable portions** — "2 slices" as well as "65 g", when a trustworthy per-item weight or a
  per-serving carbohydrate figure exists.
- **Temporary meal.** Add several calculated portions and read one total. One meal only; no history.
- **Usual portions.** Portions repeated for a specific product become one-tap shortcuts.
- Light/dark/system theme, onboarding, and a settings screen with the privacy policy and
  Open Food Facts attribution.

### Notes

- The app calculates carbohydrate amounts only. It does **not** calculate insulin, and it is not a
  diet tracker.
- English-only interface. Dutch, German and other label text is still recognised when scanning.
- No analytics, no accounts, no advertising. Data stays on the device apart from Open Food Facts
  lookups.
