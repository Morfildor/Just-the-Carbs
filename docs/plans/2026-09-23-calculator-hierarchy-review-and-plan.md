# Calculator visual hierarchy: review and redesign plan (2026-09-23)

Stage 1 (review) and Stage 2 (plan) of the visual/UX pass on the Product / Calculator screen.
Everything below was measured on the `carbscan` emulator (API 36) running the debug build of
`main` at `71d9f57`, not inferred from source. Screenshots referenced as `NN-before-*.png` were
taken at 411x914dp (1080x2400 @420dpi) unless the name says otherwise; the set lives in the
session scratchpad and the important ones are reproduced after implementation for comparison.

Fixtures used: **Nutella 400 g** (Open Food Facts, real photo, remembered 65 g, one custom
countable unit "spoon = 15 g", used twice so a *Usual* row exists), **Coca-Cola 330 ml** (real
photo, ml basis, package size known so the pack row renders, an OFF serving unit), a **manual
product with a 68-character Dutch name** (monogram, no package size), and a **Quick calculation**
reached through the label scanner's *Type it in* path (48 g / 100 g).

A note on the emulator: Gboard was showing a floating stylus-handwriting toolbar for the decimal
field, which takes no window inset, so the app's keyboard-open branches never ran. Fixed with
`settings put secure stylus_handwriting_enabled 0`; every keyboard-open capture below is taken with
a docked keypad and a real inset.

---

## Stage 1: review

### 1. Current hierarchy map (remembered saved product, light, 411x914)

Reading order the eye actually takes, from screenshot `04`:

1. **37.4 g** in the dock (72sp bold, red, bottom of screen). Biggest, only colour on the page.
2. **65** in the field (52sp bold, ink, centred in a pale box in the upper third).
3. **57.5 g carbs / 100 g** in the identity row (24sp semibold, ink), beside the photo.
4. The photo (96dp), the name in the top bar (18sp), the badge and *Verify*.
5. Labels last: `Portion` (14sp) and `CARBS` (11sp caps, muted).

So the screen presents three "number + g" figures in the same family, two of them bold ink and
one bold red, with the labels that would separate them as the quietest elements on the page. The
1-second test in the brief fails at step 2: the eye sees 65 g and 37.4 g as two headline figures
and has to read the small labels to learn which one it controls.

### 2. Problems found

**Hierarchy / input-output confusion (P0)**

- **The portion looks like a display, not a field.** At rest the field is `surfaceContainerLow`
  on the cream page: measured contrast **1.05:1** in light, **1.07:1** in dark (`Theme.kt` tokens).
  There is no border until focus. The numeral is centred with the unit pushed 350px to the right
  edge (`65` at x=436..598, `g` at x=954). A centred 52sp bold number in a barely visible box is
  the visual grammar of a readout. Only the caret says otherwise, and only while focused.
- **Portion and result share every typographic property but size.** Both Space Grotesk Bold,
  both dark (ink L=0.011, red L=0.148, only **3.23:1** between them; in monochrome they are two
  heavy numerals), 52sp vs 72sp. Your own note confirms it: in light mode the two are hard to tell
  apart at first glance. Colour is doing all the work and it is not enough.
- **Keyboard open makes them equal.** The dock's numeral slot compacts to 60dp and `resultAutoSize`
  shrinks the result to roughly 50sp (screenshot `13`, `25`): the input at 52sp and the answer at
  ~50sp, 280dp apart, both bold. This is the state in which the user is actually producing the
  number.
- **A third competing figure.** The per-100 line is 24sp semibold ink, the second-heaviest text on
  screen, and it is a number with a unit. It is legitimately the sanity-check figure, but at this
  weight it joins the contest rather than supporting it.
- **The two labels are not a pair.** `Portion` is `labelLarge` 14sp ink (reads as a section
  heading); `CARBS` is `labelSmall` 11sp muted caps 32dp below the dock's edge. Home's Recent card
  solves the same problem well (`CARBS 37.4 g` beside `65 g`, screenshot `00`); the calculator does
  not use the same device.

**Composition (P1)**

- **A dead band of 216 to 335dp.** Content ends at y=1070px and the dock starts at y=1638px in the
  result state (568px = **216dp** of empty cream); in the empty state the dock is shorter and the
  band is 878px = **335dp** (screenshot `01`). The portion controls sit in the upper third, the
  answer at the bottom, and a void separates the input from the output it produces. It reads as an
  unfinished page, and the input is nowhere near thumb reach, which the screen's own KDoc names as
  its design constraint.
- **The identity row is headed by a number, not the product.** Name in the top bar, then
  `[photo] 57.5 g carbs / 100 g`. The photo at 96dp is recognisable (the jar, the can), so identity
  is not broken, but the row's headline is the figure. Given the slack above, the photo can grow.

**Responsive (P1)**

- 360x720dp with a meal in progress: the field's lower half is under the dock (screenshot `21`).
- 320x640dp: the field is entirely under the dock at 1.0x (screenshot `22`).
- 1.8x on 411x914: the field is under the dock at rest (screenshot `24`); with the keyboard open it
  fits (screenshot `25`). The scroll fade handles all three honestly, but the driver is the dock's
  height (label + 80dp slot + supporting line + a two-to-three-line provenance sentence + two meal
  buttons that wrap at 1.3x).

**Dark mode (P1)**

- The field at rest is invisible as a box (1.07:1).
- The dock uses `surfaceContainerLowest` in both themes. In dark that is *darker* than the page
  (1.04:1) and the 6dp shadow is invisible, so the answer surface reads as a recessed slab with no
  edge (screenshots `18`, `19`). `DESIGN.md` already states the rule this breaks: a raised surface
  moves away from the page's luminance, which is `surfaceContainerHigh` in dark.

**Countable mode (P2)**

- The equation `1 spoon × 15 g = 15 g` is centred `bodySmall` at the top of the *carbs* dock
  (screenshot `09`): a portion fact, centred, inside the answer surface, in a screen where
  everything else is left-aligned.

**Polish (P2)**

- *Add portion unit* is a bare blue text link whose label sits 12dp right of the column edge
  (TextButton padding), so it is the one thing in the column that does not align.
- The mode chip reads `Grams` on a millilitre product (screenshot `12`).
- Nutella's quantity is `400 g e` (the ℮ sign) and the package parser declines it, so no pack row
  appears on a very common label form. Domain, out of scope, recorded.
- `Usual` is `labelSmall` with 1.6sp tracking in title case, which renders as spaced lowercase.

### 3. Evidence (measurements)

| Item | Measured |
|---|---|
| Field at rest vs page, light / dark | 1.05:1 / 1.07:1 |
| Dock vs page, light / dark | 1.11:1 (plus 6dp shadow) / 1.04:1 (shadow invisible) |
| Portion ink vs result red | 3.23:1 (L 0.011 vs 0.148) |
| Field numeral / unit x-positions | numeral 436..598px, unit 954..985px |
| Dead band, result state / empty state | 216dp / 335dp |
| Result numeral size, keyboard open | ~50sp in a 60dp slot vs input 52sp |
| Per-100 line | titleLarge 24sp SemiBold |
| Labels | `Portion` labelLarge 14sp ink; `CARBS` labelSmall 11sp muted |
| Field under dock | 360x720 with meal bar, 320x640 always, 1.8x always |

Code references: `ProductScreen.kt` `PortionField` (OutlinedTextField, centred `NumberType.portion`,
`suffix` unit, `heightIn(88dp)`), `CalculatorBody` (`verticalArrangement = Arrangement.Top` in the
weighted scroll zone), `ResultPanel` (`surfaceContainerLowest`, `RESULT_SLOT_HEIGHT` 80/60dp),
`ProductSummary` (per-100 `titleLarge`), `ProductIdentityRow.kt` (`THUMBNAIL_SIZE` 96dp),
`Theme.kt` `NumberType`, `JtcControls.kt` `jtcTextFieldColors` (transparent unfocused border).

### 4. What already works

- The result dock itself: elevation, 24dp top radius, the 72/26sp baseline-paired numeral and unit,
  red reserved for exactly one thing, the whole-grams line, the copy action, the meal actions. Keep.
- The dock's fixed numeral slot so it never jumps on the first keystroke. Keep and extend.
- Hiding the identity row while typing, and the compact field/dock while the keyboard is open.
- The pack row, the Usual row and the mode chips: correct components, correct vocabulary.
- The top bar (two-line intrinsic-height title, ordinary-ink back arrow, destination marker).
- Provenance as words (badge + sentence), never colour alone.
- Home's Recent card is the reference for label/value pairing.

### 5. Highest-value opportunities

**P0 (comprehension)**
1. Make the portion unmistakably an input: bordered field, left-aligned numeral with the unit
   beside it, lighter weight and smaller than the answer, `PORTION` eyebrow matching `CARBS`.
2. Keep the answer dominant by contrast of *kind*, not just size: box vs surface, semibold vs bold,
   ink vs red, and a matched label pair so the two are read as question and answer.

**P1 (visual quality)**
3. Close the dead band by anchoring the calculator group directly above the dock (input over
   answer, thumb reach), with a height-stable dock so nothing moves under the finger.
4. Give identity presence and demote the per-100 figure: 112dp photo, per-100 at `titleMedium`.
5. Dark mode: the dock takes `surfaceContainerHigh`, the field takes a hairline.

**P2 (polish)**
6. Left-align the countable equation; align *Add portion unit* with the column; name the unit on
   the mode chip (`Millilitres` for ml products).

---

## Stage 2: the plan

One direction: **a labelled input over a labelled answer, stacked as one calculator.** Identity is
a header; the calculation is a column pinned to the bottom of the screen; the portion is a boxed,
left-aligned, labelled field and the carbs are a label on an elevated surface. Nothing else on the
screen is a heavy number.

### Change 1: the portion field becomes a field

*Problem:* borderless, centred, bold, unit far away.
*Design:* `NumberEntryField` (new, shared by `PortionField` and `CountField`): `BasicTextField`
inside a 12dp-radius box, 1dp `outline` hairline at rest (3.3:1 light, 5.3:1 dark), 2dp `primary`
on focus, fill `surfaceContainerLow` at rest and `surfaceContainerLowest` focused; numeral
left-aligned in a new `NumberType.portion` (48sp, SemiBold 600, -1sp tracking) with the unit as
`titleMedium` in `onSurfaceVariant` on the same baseline 6dp after it; placeholder `0` in the same
style at 40% alpha; min height 80dp (64dp compact). The label above the field becomes the same
eyebrow as the dock's `CARBS`: `PORTION`, `labelSmall`, `onSurfaceVariant`, with the mode chips
still on its line.
*Files:* `ProductScreen.kt` (`PortionField`, `CountField`, group label), `Theme.kt`
(`NumberType.portion`), `strings.xml` (`product_portion_group_label` → `PORTION`).
*States:* empty, remembered, focused, keyboard open, countable, ml.
*Checks:* content description unchanged (`Portion in g`, `Number of slices`), `hasSetTextAction`
still finds it, `TouchTargetSizeTest` chips, `CountablePortionScreenTest` select-all on focus.

### Change 2: the answer stays dominant by kind

*Problem:* same family, weight and darkness as the input.
*Design:* no change to the 72sp bold red numeral. The dock's `CARBS` label and the field's
`PORTION` label are now identical eyebrows, so the pair reads as question and answer. With the
input at 48sp SemiBold the scale ratio becomes 1.5x and the weight contrast is real; with the
keyboard open the result shrinks to ~50sp at most, still bold and red on its own surface against a
48sp semibold ink figure in a box. The pending→result switch cross-fades (120ms) instead of
cutting. The countable equation is left-aligned and stays inside the dock (its visibility with the
keyboard open is a structural guarantee the tests rely on).
*Files:* `ProductScreen.kt` (`ResultPanel`, `PortionEquationText`).

### Change 3: the calculator sits above the answer

*Problem:* 216 to 335dp of dead page between the input and its answer.
*Design:* the weighted scroll zone uses `Arrangement.Bottom`, so the portion group (label, field,
usual, pack, add unit) rests directly on the dock and the slack collects under the identity
header. To keep the field from moving under the finger on the first keystroke, the dock reserves
the meal-actions row's height while the keyboard is open and no result exists yet (an invisible,
non-interactive `MealActions`), so the dock's silhouette is the same before and after the first
digit. Trailing spacer 32dp → 16dp (the group no longer needs to scroll clear of the dock at
the default scale; the fade still handles overflow).
*Files:* `ProductScreen.kt` (`CalculatorBody`, `ResultPanel`).
*Test:* `ProductScreenTest.thePortionControlsFollowTheProductHeaderWithoutALargeDeadBand` pins
the old anchoring (header→label gap < 140dp). It is re-aimed at the invariant it protects: no dead
band between the input and the answer (last control → dock gap small, field wholly visible).

### Change 4: identity has presence, the per-100 supports

*Problem:* the row is headed by a 24sp number; the photo is at the small end of useful.
*Design:* `THUMBNAIL_SIZE` 96 → 112dp (compact 72dp rule unchanged), and the per-100 line drops
from `titleLarge` to `titleMedium` (18sp SemiBold), the same weight as the product name, so the
row reads photo → figure → provenance rather than figure first.
*Files:* `ProductIdentityRow.kt`, `ProductScreen.kt` (`ProductSummary`).
*Test:* `theProductHeroImageIsSubstantiallyLargerThanARecentThumbnail` still holds (112 > 52).

### Change 5: dark mode is designed, not inverted

*Design:* new `ExtendedColors.resultDock` (light `surfaceContainerLowest`, dark
`surfaceContainerHigh`), used by the calculator dock and the meal total panel so the two stay one
component. The field's hairline uses `outline` in both themes.
*Files:* `Theme.kt`, `ProductScreen.kt`, `MealScreen.kt`, `DESIGN.md` (two sentences).

### Change 6: polish

- `AddPortionUnitAction`: zero horizontal content padding so the label aligns with the column.
- Mode chip: `Grams` / `Millilitres` by basis (`product_mode_millilitres` added).
- `Usual` unchanged (tests match it; separate decision).

### What stays untouched

Calculations, `ResultFormatter`, persistence, meal semantics, navigation, scanners, OCR, the
lookup priority, `ResultValue` (its API and the meal total), `PackShortcuts`, the top bar, the
provenance copy, the pending-slot behaviour, `resultAutoSize`, the compact-on-IME rules.

### Responsive and accessibility

- 360x720 and 320x640: the group is bottom-anchored, so when it overflows the zone the layout is
  identical to today's (scroll + fade); nothing new can be hidden.
- Font scale: `PORTION` and `CARBS` are `labelSmall` (11sp) and scale together; the field grows via
  `heightIn`; the 1dp hairline does not scale and stays a hairline.
- TalkBack: field content descriptions unchanged; the reserved actions row is
  `clearAndSetSemantics {}` so nothing invisible is announced; the equation stays a plain text node.
- Contrast: hairline `outline` 3.3:1 (light) / 5.3:1 (dark) against the field fill; every text
  pair already pinned by `ContrastTest` is unchanged.

### Risk / regression areas

- Replacing `OutlinedTextField` with `BasicTextField` in `PortionField`/`CountField`: focus,
  IME action Done, select-all-on-focus, autofocus on quick calculations. Covered by
  `QuickCalculationScreenTest`, `CountablePortionScreenTest`, `ProductScreenTest`.
- Bottom anchoring + the reserved actions row: `MealScreenTest` asserts the field sits above the
  dock; `ProductScreenTest` asserts the field is wholly visible on arrival at 320x640/1.3x.
- The label string change (`Portion` → `PORTION`) touches the tutorial preview, which reads the
  same resource; visually consistent, and no tutorial test matches that text.

---

## Stage 3: outcome (measured on the same emulator, same fixtures)

Implemented as planned, with three corrections found only by looking at the built screen:

1. **The unit was still at the far edge on the first build.** A `BasicTextField` inside a `Row`
   takes every pixel the row offers, so `g` landed at x=923 beside a two-digit portion. The text
   box is now `width(IntrinsicSize.Min)` capped by the row weight: `65` ends at x≈240 and `g`
   starts at x=269.
2. **The zone's top edge needed the same fade as its bottom.** With the group resting on the dock,
   a focused field that overflows the zone is scrolled into view from below the top edge, and the
   mode chips were cut mid-glyph under the meal bar with the keyboard open. The fade is now
   symmetric and inert when nothing overflows.
3. **The pending dock's preview is centred in the slot,** not bottom-aligned: bottom-aligned read as
   a label, a hole and two lines.
4. **A 640dp-tall window must count as short.** Found by re-running the geometry classes at CI's
   320x640 @160dpi: the compact-thumbnail rule was `< 640dp`, so that window got the 112dp plate and
   the result-state dock left the field no room at 1.3x. Now `<= 640dp`; the hero-size test admits
   the documented 72dp compact plate (`>=` rather than `>`).

| Measurement (411x914, light, remembered 65 g) | Before | After |
|---|---|---|
| Field at rest: edge | none (fill 1.05:1) | 1dp `outline` hairline, 3.3:1 (dark 5.3:1) |
| Numeral / unit | 52sp Bold centred; unit 350px away | 48sp SemiBold left; unit 29px after the numeral |
| Labels | `Portion` 14sp ink / `CARBS` 11sp caps | `PORTION` / `CARBS`, identical eyebrows |
| Gap last control → dock label | 216dp (empty state 335dp) | 45dp |
| Photo | 96dp | 112dp |
| Per-100 line | 24sp titleLarge | 18sp titleMedium |
| Dock in dark vs page | 1.04:1, darker than the page | `surfaceContainerHigh`, 1.33:1, raised |
| Result numeral | 72sp Bold red, unchanged | unchanged |

States re-captured after: remembered (light, dark), empty (light, dark), keyboard open (411 at
1.0x and 1.8x), countable, millilitre with pack row and OFF serving unit, Quick Calculation
(arrival with keyboard, pending, result), 360x720dp, 320x640dp, 1.3x and 1.8x.

Responsive findings: at 360x720 with a meal bar the label, field and dock all fit at 1.0x; at
320x640 and at 1.8x the shortcut rows sit under the dock and scroll with the fade, as before, but
the field itself now stays above the dock at 1.8x where it was under it. With the keyboard open at
1.8x the result autosizes to fit its 60dp compact slot and ends up visually smaller than the
scaled input; the hierarchy holds by colour, weight and label there, and it is recorded as visual
debt rather than fixed, because the only fix is a smaller compact input numeral, which the field's
own contract (the typed numeral never changes size) rules out for now.
