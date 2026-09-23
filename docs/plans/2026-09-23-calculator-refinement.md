# Calculator refinement pass (2026-09-23): what changed, how to tune it, how to revert it

A conservative, presentation-only refinement of the Product / Calculator screen, on branch
`calculator-refinement-2026-09-23` off `main` at `077efde`. It follows the hierarchy pass recorded
in `2026-09-23-calculator-hierarchy-review-and-plan.md` and does not redo it. The goal was a
clearer reading order, PRODUCT then PORTION then CARBS, through spacing, type, geometry and quieter
surfaces, and six defects measured on the current build before anything was changed.

**No behaviour changed.** Calculations, parsing, portion maths, package-size and countable-unit
logic, saved state, usage history, product and image loading, search, scanners, persistence,
favourites, the meal, navigation and every ViewModel are untouched. One UI-state adaptation was
needed (a `ResultPanel` parameter, commit 6). No new dependency, no new screen, no data-model or
repository change.

## Commits, and where to tune or revert each

Each commit stands alone; `git revert <sha>` undoes exactly one row. They were written in this
order, and later ones touch the same file as earlier ones, so reverting an early one alone may need
a trivial conflict resolution in `ProductScreen.kt`.

| Commit | Change | Tune here | Revert effect |
|---|---|---|---|
| `d2b0239` | The answer is coral `#BD492F` (was `#C13C2D`, which read as red); Dark keeps `#FF8A75` | `Theme.kt` `Coral` / `CoralDark`; mirrored by name in `ContrastTest` and `AccentRecessionTest`, which must move with it | The answer reads red again. The token is shared: the meal total, Home's recent figures, the welcome slide and the tutorial preview follow it, deliberately |
| `80c9c6c` | Value buttons (Usual, pack) draw their Dark edge when the **app** theme is Dark, not only when the phone is | `JtcControls.kt`, `JtcValueButton`, `needsBorder` | Dark chosen in Settings on a light phone loses the buttons' only edge again (`ValueButtonThemeEdgeTest` fails) |
| `c3a527b` | Top bar loses the decorative bar-chart marker; the filled favourite star is ink, not blue; the no-photo monogram plate is neutral grey, not lavender | `ProductScreen.kt` `ProductTopBar`; `Common.kt` `FavoriteButton`; `ProductIdentityRow.kt` plate background | The header regains three coloured accents competing with the answer |
| `d08c9cf` | Portion group: mode chips flow under the `PORTION` label instead of squeezing it; the typed number cannot be squeezed out by a long unit (custom `NumberBesideUnit` layout); count unit follows the typed count ("1 slice"); `USUAL` caption in caps; each Usual button is one third of the row | `ProductScreen.kt`: `NUMBER_WIDTH_SHARE` (0.6), `USUAL_SLOTS` (3); `strings.xml` `product_usual_label` | At 1.8x a long custom unit squeezes the count to zero width and chip labels break mid-word again |
| `135367b` | Dock: both meal buttons 56dp and always equal height; the filled one is `primaryTile` (deep cobalt in Dark instead of the pale blue that outshone the answer); provenance note fits one line | `MealComponents.kt` `MealActions`, `MEAL_ACTION_PADDING`; `strings.xml` `product_result_unverified` | 48dp/56dp pair returns; Dark's filled button outshines the answer |
| `0ef71a6` | On short windows (height at most 700dp) while typing, the meal bar and the dock's meal buttons step aside so the field stays visible; the field is brought into view on arrival and when the keyboard closes, only if the zone overflows | `ProductScreen.kt` `SHORT_WINDOW_HEIGHT_DP` (700), `CalculatorBody` `keyboardSqueeze`, `inputInView`; `ResultPanel(actionsStepAside)` | On a 360x600dp phone the portion field is zero height while typing again |
| `bec19c2` | Whitespace only: `PortionField` indented inside the `Box` that `0ef71a6` wrapped it in | none | none |
| `bc833e0` | "Short" is measured in lines of text: window height divided by the font scale. Found in the regression matrix: at 1.8x on the 411x914 phone the count field was a sliver under the dock while typing, because the 700dp rule never engaged there | `ProductScreen.kt` `shortWindow` | At 1.8x on a tall phone the field is covered while typing again; 1.0x behaviour is identical either way. Side effect of keeping it: at 1.3x on a tall phone the meal buttons also step aside while typing, although the field fits there |

## Defects found on the unchanged build, and their status

1. **360x600dp, keyboard open: the portion field had zero height** (typing into an invisible
   field) and *Add & scan next* was clipped. Fixed by `0ef71a6`.
2. **1.8x text: the mode chips shared the `PORTION` line** and broke mid-word
   ("Generous tablespoo / n heaped"). Fixed by `d08c9cf`.
3. **1.8x, count mode: the typed count was squeezed to zero width** by a long unit name. Fixed by
   `d08c9cf` (`NumberBesideUnit`).
4. **1.8x: "100 g" can wrap between the number and the unit** (per-100 line, equation). **Not
   fixed**, see below.
5. **App Dark on a light phone: value buttons had no edge.** Fixed by `80c9c6c`, with a test that
   fails on the old code.
6. **Dark: the pale filled button outshone the answer.** Fixed by `135367b`.

## Considered and deliberately not done

- **Primary cobalt to coral app-wide.** A different, much larger pass; it changes every screen.
  The answer is coral, the interaction colour stays cobalt.
- **System bars.** The camera screens would need their own icon handling, and the brief said to
  leave correct window/inset code alone.
- **Non-breaking space in "100 g".** About fifteen instrumented assertions match those strings with
  an ordinary space, and changing them is a test rewrite, not a visual expectation update.
- **Shrinking the monogram plate or a larger "roomy" photo tier.** `aPhotoAndAMonogramOccupyTheSamePlate`
  and the `>= 72dp` hero test pin the plate size, and a size that changes when a late photo arrives
  would move the layout under the finger.
- **Moving the product name out of the top bar into the content.** The identity row hides while the
  keyboard is open, so the top bar is the only place the name survives while typing.
- **The empty band under the identity row on sparse products** (about 170dp on a product with no
  photo, no pack size and no Usual row at 1.0x). It is the slack the bottom-anchored portion group
  leaves by design; filling it means adding content or re-anchoring the group, which the hierarchy
  pass measured and rejected.
- **Segmented mode control, loading skeleton, Home/meal/search/settings items.** Out of scope.

## Verification (final tree `bc833e0`)

- JVM **2151/2151**, 0 skipped, `--rerun-tasks`, counted from 220 JUnit XML files.
- Lint **0 errors, 29 warnings**. The one new warning is `ConfigurationScreenWidthHeight` on the
  short-window check, the same advisory already reported on `ProductIdentityRow`'s compact rule;
  both read `Configuration.screenHeightDp` so they measure the window the same way.
- Instrumented, 14 classes (`ProductScreenTest`, `QuickCalculationScreenTest`,
  `CountablePortionScreenTest`, `TouchTargetSizeTest`, `MealScreenTest`, `UsualPortionScreenTest`,
  `DirectCarbResultScreenTest`, `CorrectingKnownAmountScreenTest`, `LabelVerificationScreenTest`,
  `PackShortcutsResponsiveTest`, `ThemeRefinementVisualTest`, `ThemeDefaultTest`, `ResultValueTest`,
  new `ValueButtonThemeEdgeTest`): **146/146 at 1080x2400/420 and 146/146 at CI's
  `wm size 320x640` / `wm density 160`**, 0 ignored, counted from instrumentation status codes.
- Test changes: `UsualPortionScreenTest` reads the Usual caption from `R.string` instead of the
  literal "Usual"; `ContrastTest` and `AccentRecessionTest` follow the token rename. No test was
  weakened or deleted.
- **The keyboard-open branches have no instrumented coverage**: `createComposeRule` never receives
  an IME inset. They were verified on the emulator only, through the regression matrix below.

### Regression matrix (carbscan emulator, API 36)

| Where | States |
|---|---|
| 411x914dp, 1.0x, Light and Dark | photo with three Usuals (Nutella); ml, known pack size and one Usual (Coca-Cola); monogram, long name, 125.3 g, countable custom unit (manual product); pending; editing after a result with the keyboard open; favourite and not |
| 411x914dp, 1.3x, Light and Dark | manual product, grams |
| 411x914dp, 1.8x, Light and Dark | grams and count mode at rest; keyboard open and after Done (Dark) |
| 360x600dp, 1.0x, Dark | arrival, typing, after Done (Coca-Cola and the manual product) |

**Not seen on physical hardware.**

## Remaining visual issues

- At 1.8x the portion zone overflows. Whatever sits at its top edge (the `PORTION` caption or a
  Usual button) and the field's lower edge fall under the zone's 20dp fades. Bringing the field
  into view with the fade height as a margin would fix the second; not done, since it touches the
  scrolling contract every calculator test leans on.
- With the keyboard open at 1.8x the result autosizes smaller than the typed number (pre-existing,
  recorded in the hierarchy pass); hierarchy still holds by colour, weight and caption.
- "100 g" can wrap between the number and the unit at 1.8x (see above).
- A long custom unit name puts the mode chips on their own line under `PORTION` even at 1.0x: the
  caption and the chips need about 979px of a 974px line on 411dp. Short unit names keep one line.
- The empty band under the identity row on sparse products.
