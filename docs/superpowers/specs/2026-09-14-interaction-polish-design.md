# 1.0.7 Professional UI/UX interaction pass — design

Status: approved for implementation planning. Not a visual redesign — the warm-editorial system in
`DESIGN.md` is preserved (palette, typography family, surfaces, scanner chrome, product hierarchy).
This pass adds numeric identity, contextual behavior, state choreography and action feedback.

Scope boundary carried from every existing safety document (`CLAUDE.md`, `AGENTS.md`): no change to
arithmetic, rounding, nutrition basis, provenance, verification semantics, OCR ambiguity/conflict
handling, scale handling, `PhysicalObservationId`, session immutability, Room schema, search
budget/debounce/cancellation, scanner lifecycle, camera permissions, navigation destinations, or
versioning/release state (`1.0.7` / `versionCode 8`, `main`; `release/1.0.6` untouched).

## Current-state findings (grounding the plan)

Surveyed directly against `app/src/main/kotlin/app/justthecarbs/` on `main` at `ed00d2a`:

- **No shared result component exists.** `ResultPanel` (`ui/product/ProductScreen.kt:1914-2005`) and
  `MealTotalPanel` (`ui/meal/MealScreen.kt:316-341`) each independently render `NumberType.result`
  (72sp) + `NumberType.supporting`. Product wraps its value in `AnimatedContent` (`Motion.QUICK_MS`
  cross-fade); Meal does not — an existing small drift between the two duplicates.
- **Empty meal renders a real `0.0 g` result today.** `MealTotalPanel` is always shown
  (`MealScreen.kt:283`); its `dominant` calc falls back to `BigDecimal.ZERO` when `total` is null and
  renders at full result styling with no action beneath it (`Add & scan next` is gated on
  `items.isNotEmpty()`). This is the defect item 2 exists to fix.
- **Add-to-meal has zero visible success feedback.** `ProductViewModel.addToMeal` (`ProductViewModel.kt:806-844`)
  guards re-entrancy, then silently flips `addingToMeal` back to `false` on success — no flag, no
  snackbar, no animation. The only visible sign is the meal-bar count ticking up.
- **The reference success pattern already exists**: the Copy check-mark
  (`ProductScreen.kt:1957-2004`) — icon swap keyed on the copied value, held `Motion.COPIED_STATE_MS`
  (2500ms), gated haptic, Toast for TalkBack. This is the template to generalize.
- **`SearchResultRow`** (`ui/components/Common.kt:293-353`) already right-aligns a
  `carbsText` (value + basis together, never defaulting a missing unit) in a `labelLarge` trailing
  `Text`. This is refinement (fixed width, tabular numerals, quiet "No value"), not a rebuild.
- **Barcode scanner** (`ui/scan/ScannerScreen.kt`) has one existing animation: `ScanFrame`'s fill
  alpha (`Motion.QUICK_MS`, keyed on `acquired`). **Label scanner** (`ui/scan/LabelScannerScreen.kt`,
  2480 lines) has **zero** `AnimatedVisibility`/`AnimatedContent`/`animate*AsState` anywhere (grep
  confirmed) — all state transitions are instant recomposition swaps between `CaptureState`
  (`IDLE`/`CAPTURING`/`PROCESSING`) and the separate `pendingCrop`-driven frozen/review branch. This
  is the largest single lift in the pass.
- **Home's empty-state step strip fallback is an unbounded `horizontalScroll`**
  (`HomeScreen.kt:301-343`, `EmptyStateStepStrip`) — the code comment admits the third step is cut in
  half at 1.8x font scale on a narrow display. Needs a real adaptive layout, not a scroll escape hatch.
- **Hero image IME-compaction already exists and works**: `ProductHeroImage.kt` animates height via
  `animateDpAsState(Motion.STANDARD_MS)` keyed on a `compact: Boolean` derived from `WindowInsets.ime`.
  This pass coordinates/extends it rather than building new infrastructure.
- **"Verify" exists only in the `ProductTopBar` overflow menu** (`ProductScreen.kt:421-439`) — no
  affordance near `SourceBadge` (`ui/components/Common.kt:53-110`) today.
- **No navigation transitions, no predictive back.** `JustTheCarbsNavHost.kt` sets no
  `enterTransition`/`exitTransition` anywhere (grep: zero matches); the manifest has no
  `android:enableOnBackInvokedCallback`; no `PredictiveBackHandler` exists. `targetSdk = 36`,
  `compileSdk = 37`, `minSdk = 26` — eligible, unused.
- **Motion vocabulary already matches the brief exactly**: `Motion.QUICK_MS = 120`,
  `Motion.STANDARD_MS = 220`, `Motion.COPIED_STATE_MS = 2500L` (`ui/theme/Theme.kt:301-317`). No new
  timing constants are needed.
- **Haptics convention**: a plain `Boolean` (or `settings.hapticsEnabled` where `AppSettings` is
  already in scope) gates `performHapticFeedback` inline at the call site — no new preference key.
  New haptic feedback in this pass follows the same idiom.

## Scope decision (owner-approved)

- Items 1–11 and 13–19 of the brief: **in scope**, implemented as specified.
- Item 12 (direct portion-drag manipulation): **explicitly out of scope for this pass.** Not
  prototyped. Documented here as a deferred idea so it isn't silently lost, and is not part of the
  implementation plan.

## Components introduced

### `ResultValue` (new shared composable, `ui/components/`)

Single source of truth for every large calculated-carbohydrate display. Replaces the duplicated
rendering in `ResultPanel` and `MealTotalPanel`.

- API: `ResultValue(dominant: String, unit: String, modifier: Modifier = Modifier, color: Color = extendedColors.result, autoSize: TextAutoSize = NumberType.resultAutoSize, testTag: String? = null)`.
  Takes pre-formatted strings (already produced by `ResultFormatter`) — it is a rendering component,
  not a formatter. No calculation logic lives here.
- Renders the numeral and unit as two `Text` nodes in a `Row` with `Alignment.Bottom` (or an
  `inlineContent` span if that proves simpler against auto-sizing — decided during implementation),
  numeral at `NumberType.result` scale, unit at a new smaller companion style
  (~24–28sp, same weight/family, `NumberType` gets one new token e.g. `resultUnit`).
  Baseline-aligned, left-aligned as a whole, existing auto-size behavior preserved for the numeral.
- Tabular numerals: apply `TextStyle(fontFeatureSettings = "tnum")` if Space Grotesk (bundled) exposes
  the `tnum` OpenType feature; verified during implementation (font inspection), documented either way
  — this is a "if supported" requirement per the brief, not an assumed guarantee.
- Accessibility: `Modifier.semantics(mergeDescendants = true)` on the outer `Row` with a single
  `contentDescription` built from the already-existing accessible string (e.g. `"31.2 grams"`), so
  TalkBack reads one coherent result rather than two unrelated nodes for the numeral and the unit.
- Used by: `ResultPanel` (Product), `MealTotalPanel` (Meal). `SearchResultRow`'s trailing value is
  visually related but stays a separate, smaller composable (item 5) — it is not a "confirmed result"
  in the same sense and must not adopt result-red per `DESIGN.md`'s explicit rule.

### `SuccessFeedback` (new shared pattern, `ui/components/` — likely a small state holder + one or two composable helpers, not a single monolithic composable, since call sites differ: an icon-swap button vs. a full action button changing label)

Generalizes the Copy check-mark:

- `rememberSuccessPulse(key: Any?, holdMs: Long = Motion.COPIED_STATE_MS): Boolean` — a small
  remember+LaunchedEffect holder mirroring the existing `copiedAt`/`showCopied` pattern, parameterized
  so callers can trigger it and get back a boolean "currently showing success" state, auto-clearing
  after `holdMs`, keyed so a new trigger supersedes an old one (rapid taps converge on latest).
- A convention (documented, not necessarily a shared composable given differing button shapes) for:
  icon/label swap while `success == true`, existing haptic-gating idiom, existing Toast-for-TalkBack
  idiom reused only where no on-screen text already changes meaningfully (Add-to-meal's own button
  label change is sufficient; it does not also need a Toast, since unlike Copy nothing left the
  screen for the user to lose track of).
- Applied to: Add to meal (button label → "✓ Added" → reverts or strengthens next action), Favorite
  toggle (icon state, if not already adequate), Save product / quick calculation (existing dialog
  flow gets a brief confirmation before it closes, per item 4's "successful verification where
  appropriate" — scoped narrowly to avoid inventing new flows).

## Screen-by-screen plan

### Meal (item 2)

`MealTotalPanel` gains an `items.isEmpty()` branch: render `—` via `ResultValue`-adjacent (or the same
component with a literal em-dash + no unit) styling that is visibly **not** claiming a calculated
result — smaller weight or muted color, not `extendedColors.result`. Once `items` becomes non-empty,
normal `ResultValue` rendering takes over. `EmptyMeal`'s existing body text may gain one clause if
needed for the placeholder to read sensibly, but no new illustration/icon per the brief ("no
confetti… restrained").

### Add to meal (item 3)

`ProductViewModel` gains a transient success signal (e.g. `mealAddSucceeded: Boolean` or a one-shot
event, mirroring the existing `mealAddFailed` field shape) set immediately after persistence succeeds,
cleared after the hold window or on next interaction. `MealActions`'s "Add to meal" button consumes it
via the `SuccessFeedback` convention: label → "✓ Added" → either reverts to "Add to meal" or, where
the brief allows ("strengthen the next useful action"), the row re-labels toward `Scan next`/`View
meal` without forcing navigation. Never shown before `addToMeal`'s persistence call actually returns
success — the existing `addingToMeal` guard ordering is preserved.

### Search (item 5)

`SearchResultRow`'s trailing column becomes a small dedicated sub-composable (e.g.
`SearchNutritionColumn`) with a reserved width (~90–105dp, `Modifier.widthIn` or fixed `width`
depending on how it behaves at large font), value on top in a slightly stronger weight/tabular style,
basis beneath in a quieter secondary line — same two-line shape the brief mocks up. Missing value:
quiet "No value" text in the same region, same color treatment as today's `onSurfaceVariant` fallback
(no fake zero, no basis rendered without a value). Narrow/large-font degrade: column keeps its
reserved width as a floor but the row's name column absorbs any remaining squeeze exactly as today
(`weight(1f)` on the name `Column` already does this) — verified, not re-architected.

### Scanner choreography (item 6)

**Barcode**: extend the existing `ScanFrame` alpha animation with a brief, explicit acquisition
confirmation — e.g. a short scale/stroke pulse on the frame or a check-glyph fade-in over
`Motion.QUICK_MS`–`Motion.STANDARD_MS`, then transition into the lookup screen exactly as today.
Additive to existing animation infrastructure; no change to `BarcodeStabilityTracker`,
`BarcodeAnalyzer`, CameraX binding, or acceptance thresholds.

**Label scanner**: introduce state-driven `AnimatedContent`/`Crossfade` around the
`CaptureState`(`IDLE`/`CAPTURING`/`PROCESSING`) → frozen/`pendingCrop` transition so the shutter
visibly acknowledges capture (e.g. shutter control morphs into a compact progress treatment)
immediately, before OCR completes — the existing `FOCUS_TIMEOUT_MS`-bounded capture and the
`pendingCrop` early-return branch are not restructured, only wrapped with a transition so the swap
from live chrome to frozen/review chrome reads as connected rather than an instant cut. No change to
`LabelAnalyzer`, `EvidenceResolver`, ambiguity/conflict/recovery logic, haptic cues, or
`ScanHapticCue` — purely the container transition around existing branches.

### Home (items 7, 8)

**Context-aware primary action (item 7)**: `HomeScreen` reads whether a meal is in progress (the same
signal `MealBarIfPresent` already uses) and swaps `home_scan_button`'s string resource /
`home_action_barcode_subtitle` to a "Scan next item" framing when `mealItems` is non-empty — copy and
string-resource change plus a conditional in `HomeActionCard`'s params, not a new component. Icon
unchanged. Ordering/visibility of the barcode/label/manual tiles unchanged.

**Responsive step strip (item 8)**: replace `EmptyStateStepStrip`'s `horizontalScroll` fallback with
a `Layout`/`FlowRow`-style (or a simple width-measured branch between a `Row` and a compact vertical
`Column` arrangement) composition that wraps or switches to a vertical arrangement under a measured
width/font-scale threshold, preserving reading order and the existing roundel + connector visual
vocabulary at normal width. No horizontal scroll remains as the sole affordance for reading three
steps.

### Product (items 1, 3, 9, 10)

- `ResultPanel` adopts `ResultValue` (item 1), preserving its existing `AnimatedContent` cross-fade
  wrapper, autoSize, decimal/whole-dominant `ResultStyle` behavior, and `PRODUCT_RESULT_TAG`.
- Add-to-meal success state wired per above (item 3).
- IME identity transition (item 9): coordinate `ProductHeroImage`'s existing compact transition with
  the portion/result section's visual weight — verify (and adjust only if needed) that the existing
  `imeVisible`-gated hides (prompt text, unsaved hint, provenance line) plus the hero's
  `animateDpAsState` already produce a coherent "identity yields, portion/result dominates" read; this
  is confirmation/refinement of existing architecture per the brief's explicit instruction not to
  build a new collapsing-toolbar framework, not new state.
- Verify action (item 10): add a small inline text/icon action near `SourceBadge` (only rendered when
  `product.dataSource == OPEN_FOOD_FACTS && verificationStatus == UNVERIFIED` — reusing existing
  fields, not inventing a new condition), calling the same `onVerify`/`onVerifyByTyping` callbacks
  already wired from the nav host. Overflow menu entries remain as secondary access, unchanged.
  Deliberately worded and styled as a neutral action, not a warning (`SourceBadge`'s existing
  orange-soft/worded-badge treatment already carries the "not verified" signal — the new action must
  not duplicate or escalate that into alarm styling).

### Navigation (item 11)

- Add short, uniform `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` to
  `NavHost` (a single shared spec, fade or short shared-axis within `Motion.QUICK_MS`/`STANDARD_MS`
  — no slide/scale choreography per the brief), applied once at the `NavHost` level rather than
  per-`composable` where Navigation-Compose 2.9.8 supports that.
  before OCR/scanner results appear — verified explicitly since scanner screens are navigation
  destinations too.
- Predictive back: add `android:enableOnBackInvokedCallback="true"` to the manifest; audit existing
  `BackHandler` usages (`ProductScreen.kt:211`, `ScannerScreen.kt:192`, others) for predictive-back
  compatibility, replacing with `PredictiveBackHandler` only where it is safe and low-risk to do so
  within this pass — back *behavior* (what screen you land on) must not change, only whether the
  gesture participates in the system preview. If any `BackHandler` site's migration looks risky
  (e.g. scanner cleanup ordering), leave it as ordinary `BackHandler` and document it rather than
  forcing the migration, per the brief's own escape hatch in item 11.

## Explicitly deferred (not built this pass)

- **Item 12, direct portion-drag manipulation.** Owner decision: skip prototyping entirely for this
  pass. If revisited later, the brief's hard rules (must not replace existing controls, must not
  intercept ordinary scrolling, must not complicate tap/keyboard entry, must remain TalkBack-clear)
  still apply and should gate any future attempt.

## Testing approach

- Unit/domain: none of this pass touches `domain/` calculation logic, so no new JVM tests are
  expected there beyond incidental coverage of any new pure helper (e.g. a step-strip width-threshold
  function, if extracted as pure logic).
- Instrumented Compose tests, one behavioral contract per item where practical:
  - `ResultValue` renders one coherent accessible node (number + unit merged/paired), decimal- and
    whole-dominant styles both still correct.
  - Empty Meal shows the placeholder, never a red/result-styled zero; populated Meal shows a real
    result via the same component.
  - Add-to-meal shows a success state only after (not before) a stubbed successful persistence; a
    failure path still shows the existing failure text and no success state.
  - `SearchResultRow`'s nutrition column: value+basis paired, missing-value quiet state, no fake zero.
  - Home: barcode action copy differs with a non-empty vs. empty meal.
  - Step strip: renders without a horizontal-scroll node at a narrow/large-font configuration
    (mirroring the existing below-the-fold/keyboard-covered test lessons already documented in
    `CLAUDE.md` — assert on real measured bounds, not just presence).
  - Verify action visible only for `OPEN_FOOD_FACTS`/`UNVERIFIED`, absent otherwise.
  - Scanner: state-transition presence is checked via existing `CaptureState`/`acquired`-flag
    assertions already in each screen's test suite, extended to assert the new transition doesn't
    delay the underlying state change (no timing-based flaky assertions, per `AGENTS.md`/`CLAUDE.md`
    "no brittle screenshot/pixel tests for minor animation frames").
- No new screenshot/pixel-golden tests for animation frames, per the brief.
- Baseline commands: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`,
  `:app:assembleDebugAndroidTest`, plus targeted instrumented runs for every touched screen class.
- Manual/visual QA pass across the light/dark/large-font/narrow-width matrix listed in the brief's
  §16, performed by driving the debug build (emulator, per this repo's standing toolchain) — reported
  honestly as emulator-only, consistent with every other unverified-on-hardware note in `CLAUDE.md`.

## Documentation

`DESIGN.md` gains entries only for principles that ship: split number/unit result typography +
tabular numerals, the success-feedback grammar (`SuccessFeedback`/`rememberSuccessPulse`), contextual
active-meal Home copy, the `SearchResultRow` nutrition column shape, and scanner state choreography.
Implementation-detail specifics (exact dp widths, exact enum names) stay in code/comments, not
permanent design doctrine, per the brief's explicit instruction.

## Risks / open questions carried into planning

- Tabular numerals depend on Space Grotesk actually exposing `tnum` — needs a quick check against the
  bundled font file before committing to that requirement; if absent, ship without it and say so in
  the final report rather than silently dropping the requirement.
- `NavHost`-level transitions in Navigation-Compose 2.9.8: confirm the shared-spec-at-NavHost-level
  API shape during implementation (per-`composable` transitions are the more universally-documented
  path; a single shared default may need a small helper rather than a single constructor arg).
- Predictive-back migration is scoped conservatively (manifest flag + opportunistic `BackHandler`
  replacement) rather than guaranteed end-to-end, per the brief's own "leave it documented rather than
  forcing it" clause — the implementation plan should treat each `BackHandler` site as its own
  go/no-go decision rather than a blanket migration.
