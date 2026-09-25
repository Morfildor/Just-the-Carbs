# Review fixes (2026-09-25) Implementation Plan

> **For agentic workers:** executed natively in the session that wrote it (owner: "make a plan and fix
> all of them, in steps, make sure this release is bug free"). Steps use checkbox syntax.

**Goal:** fix every finding of the 2026-09-25 deep review of the 1.0.8 branch (`ux-polish-2026-09-24`,
commits `4ebde9c`..`ead38ec` plus the uncommitted protein reading), test-first, without changing any
carbohydrate calculation.

**Architecture:** each fix lands in the layer that owns the rule (repository for data integrity,
ViewModel for state lifetimes, a pure helper for layout decisions so they are JVM-testable, the
composable only for presentation). Every behavioural fix gets a test that fails before it.

**Tech stack:** Kotlin, Jetpack Compose, Room, JUnit4 JVM tests, Compose instrumented tests on the
`carbscan` AVD at 1080x2400/420 and CI's 320x640/160.

**Spec:** the review report in this session; `docs/superpowers/specs/2026-09-24-protein-design.md`
for the protein items; `CLAUDE.md`, `DESIGN.md`.

## Global constraints

- No change to `CarbCalculator`, `DirectCarbCalculator`, `PortionResolver`, `ResultFormatter`'s
  rounding, any OCR recognition rule, or the §10 lookup priority.
- UI stays English; no em dashes in user-visible strings; plain wording.
- Room schema stays at v9 (unreleased, but no column changes are needed by this plan).
- Every JVM run counted from JUnit XML with `--rerun-tasks`; instrumented counted from
  instrumentation status codes; geometry-sensitive classes also at `wm size 320x640` /
  `wm density 160`; reset `font_scale` to 1.0 before instrumented runs.
- Local commits per wave; no push, no release build, no upload.

## Review focus (inputs the tests below must pin)

1. A portion changed after *Add to meal* (by typing, shortcut, count, mode, rotation, keyboard
   open/close) is never shown as "Added".
2. A lookup that finishes after the user saved the same barcode by hand never overwrites the saved row.
3. The protein row never oscillates, never takes the field, and never makes TalkBack repeat an
   unchanged carb figure.
4. A verified Open Food Facts product shows the protein the online record lists.
5. A second "Don't allow" leads straight to *Open Settings*.

---

## Wave 0: baseline

- [ ] Commit the uncommitted protein reading exactly as reviewed ("Protein reading (as reviewed)"),
      excluding `.playwright-mcp/`. Restore point for every fix below.

## Wave A: the release blockers (P1)

### Task A1: "Added" never describes a portion that was not added

**Files:** `ui/product/ProductViewModel.kt`, `ui/meal/MealComponents.kt`,
`ui/components/SuccessPulse.kt`; tests `ui/product/MealAddedHoldTest.kt` (JVM, new),
`androidTest/.../MealAddedHoldScreenTest.kt` (new).

- ViewModel: every user input that changes what *Add* would write (`onPortionChanged`,
  `adjustPortion`, `setPortion`, `switchToGrams`, `switchToPortionUnit`, `onCountChanged`,
  `applyUsualPortion`) sets `lastMealAddSucceeded = null`.
- UI: the hold is measured from the add time, not from when `MealActions` was composed:
  `rememberSuccessPulseSince(at: Long?, holdMs)` shows only while `now - at < holdMs` and schedules
  its own end for the remaining time. Re-entering composition after the hold shows nothing.
- Tests first: JVM (add, then each input path, `lastMealAddSucceeded` is null); instrumented
  (MealActions composed with `justAdded = now - 10 s` shows *Add to meal*, not *Added*; composed with
  `now` shows *Added*; removed and re-added inside the hold shows only the remainder).

### Task A2: a late lookup never overwrites a row the user wrote

**Files:** `domain/ProductDataSource.kt`, `data/local/ProductDao.kt`,
`data/local/RoomProductDataSource.kt`, `data/ProductRepository.kt`, `ui/JustTheCarbsNavHost.kt`;
tests `data/ProductRepositoryTest.kt` (JVM), `androidTest/.../ProductDaoTest.kt`.

- `LocalProductDataSource.saveIfAbsent(product): Boolean` with a default (fetch, then save) for
  fakes; Room overrides with `@Insert(onConflict = IGNORE)` (atomic).
- `lookup()` uses `saveIfAbsent`; when a row already exists it returns that row (and saves no
  portion-unit candidate), so the network result can never replace it.
- NavHost: *Enter manually* and *Scan nutrition label* from the product route while no product is
  loaded pop the product entry (`popUpTo(Routes.PRODUCT) { inclusive = true }`), cancelling the
  lookup and removing the stale duplicate. The compare path (product loaded) is unchanged.
- Tests first: JVM, a gated remote that completes after `saveUserAuthoredProduct` leaves the manual
  row intact and returns it; DAO instrumented, `insertIfAbsent` on an existing barcode changes
  nothing.

### Task A3: the protein row gate is stable and cheap

**Files:** `ui/product/ProductScreen.kt`, new `ui/product/ProteinRowGate.kt` (pure); tests
`ui/product/ProteinRowGateTest.kt` (JVM), `androidTest/.../ProteinReadingScreenTest.kt`.

- Pure `proteinRowFits(zoneRoomPx, occupyingPx, lastMeasuredPx, estimatePx, floorPx)`:
  room as if absent = `zoneRoomPx + occupyingPx`, cost = `max(estimatePx, lastMeasuredPx)`.
  `occupyingPx` is 0 when the row is not composed; `lastMeasuredPx` is retained after it leaves.
  JVM test simulates show -> measure -> hide -> re-measure for rows taller than the estimate and
  proves no oscillation.
- The composable reads the gate through `derivedStateOf`, so a room change recomposes nothing
  unless the verdict flips (the IME slide no longer recomposes the calculator every frame).
- Arrival: the dock's size animation is off until the first room measurement has landed, so a
  starved window corrects without a 220 ms shrink.
- Speech follows the data, not the gates: the result's accessible sentence is built from the
  ungated protein data, so the keyboard or the room gate showing/hiding the row never changes
  what TalkBack hears. (Deliberate change from the spec's "only while the row is shown"; recorded.)

## Wave B: protein data and presentation (P2/P3)

### Task B1: verified products carry the online protein

**Files:** `data/ProductRepository.kt`; test `data/ProteinRepositoryTest.kt`.

- `refreshFromRemote`'s non-refreshable branch also stores the fetched protein pair when the
  product is Open Food Facts data and `fetched.basis == latest.basis`; otherwise it leaves protein
  as it is. Protein is never verified by the user (the verify dialog checks carbs), so it follows
  the online record. `applyLatestRemoteValue` then pairs the new carbs with the protein of the same
  fetch. User-authored products still never carry protein.
- Tests first: pre-v9 verified product gains protein on refresh; manual product does not;
  basis-different fetch leaves protein unchanged; apply-newer after a reformulation shows the new
  record's protein; a protein-only difference is still `Unchanged`.

### Task B2: TalkBack hears the settled figure, not every keystroke

**Files:** new `ui/components/SettledAnnouncement.kt`, `ui/product/ProductScreen.kt`; test
`androidTest/.../SettledAnnouncementTest.kt`.

- `rememberSettledText(text, settling, settleMs = 600)`: while `settling` (keyboard open) the
  returned text follows `text` only after it has been unchanged for `settleMs`; when `settling`
  turns false it follows at once. The result Box's live description uses it with
  `settling = imeVisible`. Tests never see an IME inset, so existing literals are unaffected.

### Task B3: small protein fixes

- Plurals: `result_accessible_grams`, `_and_protein`, `product_protein_accessible` become
  `<plurals>` so a whole-gram "1" reads "1 gram".
- Toggle double tap: `ProteinToggle` and the Settings row keep an optimistic local value, so two
  quick taps land on the state the user sees.
- Home footer at 320dp and 1.0x: trim the toggle's horizontal padding so *Enter manually* and the
  chip share one line (missed by 2dp); larger text still wraps.
- Stale KDoc `[clearUsageForBasisChange]` -> `[dropBasisBoundFacts]`; listing sentence reworded.
- Tests: large-figure test asserts character completeness (`getLineEnd`); Home protein-off test
  also checks content descriptions.

## Wave C: earlier commits

- C1 Meal total: figure `Bold` inside the SemiBold sentence; test compares against the base weight;
  a missing marker renders the plain sentence instead of throwing.
- C2 Figures: `VerificationScreen` and the assisted screen show `candidateFigure` (locale separator,
  no rounding) for figures the user commits.
- C3 Permission: rationale true before and false after the request is a permanent denial.
  Settings body worded for "not offered again" rather than "turned off".
- C4 Search: scroll-to-top keyed on the result set, not the typed text.
- C5 `ThemeRoleOwnershipTest`: require the content column to be found.
- C6 Close: ignores a second tap (`dropUnlessResumed`) on proposal, conflict, crop and assisted
  screens; on `VerificationScreen` the header is pinned outside the scroll.
- C7 Shortcuts: spoken names start with the visible label; `selected` is omitted while the field is
  empty (pack and Usual).
- C8 Crop: minimum side in dp; a touch inside a small box moves it.
- C9 Pack hint: its line is reserved while a pack size is known, so the field does not jump.
- C10 Portion field: an external change while focused selects all, so the next digit replaces it.
- C11 Assisted typing: unparseable text gets the same explanatory sentence style.
- C12 Settings: the cleared confirmation is consumed when shown; a failed clear is reported, not a
  crash.
- C13 Torch turns off when the photo freezes.
- C14 Welcome and onboarding get pane titles.
- C15 Copy: conflict body no longer blames the label.
- C16 Real-tap select-all instrumented test.

## Wave D: verification and docs

- Full JVM `--rerun-tasks`, lint, `assembleDebug`, `assembleDebugAndroidTest`.
- Instrumented: every class touched plus the calculator/Home/search/scanner classes at both
  geometries; then the release-gate set (`notAnnotation=ExploratoryExperiment`).
- Negative controls for A1, A2, A3, B1, C3.
- CHANGELOG 1.0.8, CLAUDE.md section, manual-qa rows.

## Kept as designed (not bugs; reasons recorded)

- Protein "No online value" after the user changes the basis in the verify dialog (spec row 359).
- First visit after the upgrade for an unverified cached product shows "No online value" until
  the refresh lands (spec, documented).
- Protein validator's Kotlin number grammar (`"6.3f"`): shared with the carb path, harmless.
