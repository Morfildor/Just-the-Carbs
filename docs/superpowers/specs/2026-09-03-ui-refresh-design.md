# UI refresh: colourful chrome, protected result, opaque system bars

**Date:** 2026-09-03
**Status:** Approved for implementation
**Version:** lands under `1.0.3` / `versionCode 4`, which is open

## What this is

A UI/UX pass with two parts:

1. **Five findings** from a review of every screen in `ui/`, ranked by severity.
2. **A visual direction change** requested by the owner: the app should be
   "professional, trendy and colorful", and the system bars must be opaque black
   rather than showing app content behind them.

Nothing in this spec changes the calculation, the schema, migrations, the §10
lookup priority, barcode detection, or any OCR recognition rule. Every change is
presentation, plus one window-configuration fix.

## The tension this resolves, stated plainly

`Theme.kt` and `DESIGN.md` both argue the palette is near-monochrome **because**
the carbohydrate result must be the loudest thing on screen — "a colourful
interface competes with it". That is not decoration; it is the reasoning behind
the app's one safety-critical display.

The owner's decision is **colourful chrome with a protected result**: colour is
spent freely on navigation, actions, sections and empty states, and the result
number keeps its exclusive red and its visual monopoly on the calculator.

This is not a compromise phrasing. It is enforced by measurement — see
"The luminance rule" below — so the guarantee survives someone editing a token
later without reading this document.

## Part 1 — The palette

### Six new accents

| Token | Hex | vs cream | vs white | vs surfaceContainerLow |
|---|---|---|---|---|
| Teal | `#0F766E` | 5.13 | 5.47 | 5.30 |
| Violet | `#6D28D9` | 6.65 | 7.10 | 6.88 |
| Green | `#15803D` | 4.70 | 5.02 | 4.86 |
| Magenta | `#BE185D` | 5.65 | 6.04 | 5.85 |
| Indigo | `#4338CA` | 7.40 | 7.90 | 7.65 |
| Amber | `#92400E` | 6.64 | 7.09 | 6.86 |

All clear WCAG AA normal-text (4.5:1) on every surface they are drawn on, and
white text on each clears 4.5:1 for use in filled cards and roundels.

These are computed values, not aspirations. `ContrastTest` derives ratios from
the live tokens rather than asserting remembered constants, so extending it to
cover these six means a later edit that breaks one **fails the build**.

### The luminance rule — what makes "colourful but safe" hold

Result red `#D42F2F` has relative luminance **0.162**. Every new accent is
darker:

```
teal 0.142 · green 0.159 · magenta 0.124 · violet 0.098 · amber 0.098 · indigo 0.083
```

A darker accent **recedes behind** the result rather than competing with it. So
the result keeps its prominence by arithmetic, not by intention.

**This must become a test.** `AccentRecessionTest` asserts every accent in the
extended palette has luminance strictly below the result red, in both light and
dark schemes. Without it, the guarantee is a paragraph someone can contradict
with one plausible-looking hex edit.

### Where colour is spent, and where it is not

**Spent on:** action-card gradients, per-destination top-bar accent spines,
section headings, icon roundels, empty-state illustration, chips, the scanner's
staged progress.

**Never spent on:**
- The result number's surroundings. The result panel keeps `surfaceContainerLowest`.
- Card grounds. Colour goes on the *edge* (4dp spine) and the *roundel*, so
  product names keep full contrast against white.
- Meaning on its own. `DESIGN.md`'s existing rule — "never colour alone for
  meaning" — is unchanged and binds the new palette identically. The CARBS label
  is text; the favourite marker is a star; provenance stays worded.

## Part 2 — System bars must be opaque

### The requirement

The status bar and navigation bar currently show app content behind them. They
must be solid black, as in an ordinary non-immersive app.

### Why the obvious fix does not work

Three places currently make the bars transparent:

- `MainActivity.onCreate` calls `enableEdgeToEdge()`
- `themes.xml` sets `android:statusBarColor` and `android:navigationBarColor` to transparent
- `MainActivity` sets `isNavigationBarContrastEnforced = false`

The instinctive fix is to set those XML colours to black and drop
`enableEdgeToEdge()`. **That would appear to work and then fail on modern
devices.** `targetSdk` is 36, and from Android 15 (API 35) the platform enforces
edge-to-edge and **ignores `statusBarColor` and `navigationBarColor` entirely** —
they are deprecated no-ops. A fix resting on them would be correct on the
emulator's older images and wrong on a current phone, with nothing on screen to
say which.

### The approach that actually works

Keep edge-to-edge (it is not optional at this target) and **draw opaque black
bands behind the system bars in Compose**, so the window is edge-to-edge while
the bars read as solid black.

Concretely, in `MainActivity`:

- Keep `enableEdgeToEdge()`, but pass explicit dark `SystemBarStyle` values so
  the scrim the framework applies is black rather than auto-resolved.
- Set `isNavigationBarContrastEnforced = true` — the existing comment argues for
  `false` on the grounds that the scrim "reads as a grey band that matches
  neither theme". Under an intentionally black bar that reasoning no longer
  applies, and the comment must be rewritten rather than left contradicting the
  code.
- Force `isAppearanceLightStatusBars = false` and
  `isAppearanceLightNavigationBars = false` **unconditionally**. Icons must be
  light because the bar behind them is now always black — this no longer tracks
  the app theme. The existing light/dark inversion logic is deleted, and that is
  a genuine behaviour change worth stating: in Light mode the bars were
  previously cream with dark icons.

A `SystemBarScrim` composable in `ui/theme/` draws the two black bands using
`WindowInsets.statusBars` / `WindowInsets.navigationBars` heights, placed once at
the root of `JustTheCarbsTheme` so no screen can forget it.

### The camera screens are the exception to check, not to assume

`LabelScannerScreen`, `ScannerScreen`, `CropConfirmationScreen` and
`AssistedReadingScreen` draw a black background already and their content is
inset-padded, so black bars are consistent there by construction. This must be
**verified on a device**, not argued: a camera preview that renders under a black
band would lose framing area, which matters because the scan region is measured
from the overlay's laid-out bounds.

### What this does not change

Every screen already applies `statusBarsPadding()` / `navigationBarsPadding()`,
so no content is currently *under* the bars — only the background shows through.
No screen's inset handling needs to change, which is what keeps this fix small.

## Part 3 — The five findings

### Finding 1 (HIGH): Home computes the answer, then hides it

`rememberedCarbs()` resolves the exact carbohydrate figure for every recent
product on Home. It is rendered as `bodyMedium` in `onSurfaceVariant` inside
`"%1$s → %2$s"` — grey supporting text at the same weight as the portion label.

For a returning user the number they came for is already on screen, already
correct, styled as metadata. They tap through to a screen that recalculates the
identical figure and shows it at 64sp.

**Change:** the figure moves to a right-aligned column on `RecentCard`, in the
result red, under a small `CARBS` label. The portion stays as supporting text on
the left.

**Constraint:** it is the *same* red as the calculator, because it is the same
fact. It must not become a new hue, and it must follow
`settings.resultStyle` exactly as it does today — Recents and the calculator
disagreeing about a number is a defect this repo has already fixed once.

### Finding 2 (HIGH): eleven hand-rolled top bars, three title sizes

Measured: zero uses of `TopAppBar`. Eleven screens each build a `Row`. Title
style is `titleLarge` on Manual Entry and Settings, `titleMedium` on Meal and
Search, `headlineMedium` on Home.

**Change:** one `JtcTopBar` in `ui/components/`, taking a title, an optional
back action, an optional trailing slot, and an accent colour. One title style
everywhere. The accent renders as the 4dp spine, matching the Home card
language.

Home keeps `headlineMedium` deliberately — it is a brand wordmark, not a
navigation title, and is the one screen with no back affordance.

### Finding 3 (MED): the scan wait is a spinner and a sentence

Device evidence records ML Kit at 323–1974 ms, two captures over 2 s. An
indeterminate spinner cannot distinguish "normal" from "stuck", and the frozen
photo the app already holds is not shown.

**Change:** during `PROCESSING`, show the captured bitmap with a staged progress
list.

**The hard constraint:** stages must map to **real pipeline milestones the code
already emits** — capture complete, ML Kit returned, parse complete. Never a
timed animation. A fake progress bar on a 1974 ms operation is worse than the
spinner, because it implies knowledge the app does not have. If a milestone
cannot be observed without changing the OCR path, that stage is not shown.

### Finding 4 (MED): the decorative circle is on 2 of 11 screens

Home (blue) and Meal (orange) carry an offset circle; no other screen does.

**Change:** extract `AccentBackdrop` and apply it to every non-camera screen,
each with its destination accent. Camera screens keep flat black.

### Finding 5 (LOW): ProductScreen is 2016 lines, 20 composables

Not user-facing. `CalculatorBody` takes 24 callback parameters, and four past
layout defects clustered in the result-panel area.

**Change:** extract the portion-entry composables into
`ui/product/PortionControls.kt` and the result panel into
`ui/product/ResultPanel.kt`. **Pure file moves, no behaviour change**, verified
by the instrumented suite passing unchanged.

Deliberately last, and droppable: it competes for review effort with the rest,
and the safest version of this refactor is the one nobody rushes.

## Testing

| Change | How it is verified |
|---|---|
| New accents | `ContrastTest` extended — computes from live tokens |
| Luminance rule | New `AccentRecessionTest`, both schemes |
| Opaque bars | Device screenshot, light and dark, gesture and 3-button nav |
| Home carb figure | `HomeScreenTest` — figure present, follows `resultStyle` |
| `JtcTopBar` | Existing per-screen tests; titles unchanged in content |
| Scan progress | Instrumented against real milestones, not timings |
| ProductScreen split | Existing 32 `ProductScreenTest` cases pass unmodified |

Every change gets a negative control per this repo's convention: revert it, watch
the intended test fail, restore byte-identically.

## What must NOT change

- The three-zone calculator layout, the pinned result panel and its fixed height
  budget. Four defects came from that area; it is hard-won.
- The result's exclusive red, `NumberType.result`, and its auto-size floor.
- Any OCR threshold, parser rule, or recognition path.
- `ServingSizeParser`'s Dutch input recognition, and the English-only UI rule.
- Error-recovery routing — every failure keeps its tailored way out.

## Documentation debt this creates

`DESIGN.md` states the palette is near-monochrome *because* colour competes with
the result. After this pass that rationale is stale. It must be rewritten to
state what is actually true: colour is spent freely on chrome, and the result is
protected by the luminance rule and by `AccentRecessionTest` — not by the absence
of colour elsewhere.

Leaving the old wording in place would be worse than the drift it describes,
because the next reader would treat a measured guarantee as a style preference.
