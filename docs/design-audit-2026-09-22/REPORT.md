# Just the Carbs — visual direction and polish report (2026-09-22)

Review plus design decisions. Nothing in the code was changed. Reviewed at HEAD `71ca1af`
(1.0.8 / versionCode 9 open), debug build, `carbscan` emulator (API 36, 1080×2400 @420dpi =
411×914dp), light and dark, plus a 360×720dp override and a 1.3× font scale. Every finding was
seen on the running app; the screenshot that shows it is named in brackets and lives in
`screenshots/` next to this file.

Calculation, scanning, search, persistence and navigation logic are out of scope and must not
change. Everything below is presentation.

---

## 1. Overall assessment

The app is already above "good indie": the warm paper/ink palette, Space Grotesk numerals, the
cobalt/tomato/orange role split, the `JtcTopBar` marker, the result dock, the search row's
price-column figure, the copy tone and the provenance wording read as one authored product. Light
Home, Search results, Not-found and Manual entry are close to finished.

Five systemic things keep it from reading as a polished commercial product:

1. **The calculator no longer fits its own hierarchy.** A 245dp hero photo, a three-line
   provenance block and a dock that has grown (compact meal bar + provenance sentence + two
   buttons) push the portion field, the one control the screen exists for, under the dock at the
   default size on a 411×914dp phone, and completely off screen at 360×720dp or 1.3× text.
   [20, 28, S02, F01]
2. **The result numeral's unit is set as a subscript.** `23.5` with the `g` hanging below the
   baseline appears on every result surface. It is the app's most important glyph pair and it is
   optically wrong. [crop_result_panel, 29, 09]
3. **Too many boxes, too many accents.** Home stacks a bordered search field, a tinted meal bar,
   a filled tile, a teal-bordered tile, bordered recent cards with a coloured spine, pill
   thumbnails and a segmented pill control; Product adds a bordered photo plate, an orange badge,
   a blue link, an outlined field, seven outlined value buttons and an elevated dock. Every level
   has an edge. The 22dp corner radius on 80dp-tall containers makes it bubbly rather than precise.
4. **Component vocabulary is defined in the theme but not applied.** Two of seventeen text fields
   use the app's field colours; five of eight chips are Material-default rectangles; outlined
   buttons inherit Material 1.4's grey label so `Add to meal`, `−50/+50`, `Retake`, `Type it in`
   look disabled beside blue text buttons; one dropdown menu is styled, two are default.
   [20, 24, 31, 37, 42, 44]
5. **The tutorial teaches an app that does not exist** (gradient pill tiles, circular plates,
   outlined `Enter manually`, boxed meal rows) and **decoration collides with controls** (the
   nutrition-bar backdrop sits under Product's star/overflow, Meal's `Clear meal` and first ×, and
   the open overflow menu). The two scanners wear different chrome. [05–10 vs 11; 18, 29, 44; 33 vs 31]

Two outright defects: the Verify dialog renders the raw placeholder `Carbs per 100 %1$s` [45], and
the rate card says `Rate JustTheCarbs` [13].

---

## 2. Proposed visual direction

The system the app should converge on. Everything in sections 3–7 derives from this.

**Hierarchy philosophy.** One dominant object per screen, one interactive accent, everything else
in ink on paper. Home: the primary scan tile. Product: the result numeral, then the portion field.
Meal: the total. Search: the product name. Decoration never sits at the same level as a control.
The backdrop motif lives on Home only; every other screen identifies its destination with the
three-rule `DestinationMarker` in the top bar and nothing else.

**Typography.** Two families, unchanged: Space Grotesk for wordmark, headings, labels, actions and
every number; the platform sans for body. Weight carries hierarchy more than size: Bold only for
the wordmark and the two big numerals; SemiBold for titles, labels, actions and inline figures;
Regular for body. One title size on every secondary screen (`titleLarge` 24/30). Eyebrow labels
(`CARBS`, `MEAL TOTAL`, section labels) stay `labelSmall` uppercase with 1.6sp tracking, and are
the only uppercase text in the app. Body text is never centred except in recovery panels.

**Spacing and density.** Keep the 4/8/16/24/32 scale and the 20dp edge. Tighten vertical rhythm:
8dp between siblings inside a group, 16dp between groups, 24dp between sections. Rows are 48dp
(settings, menus) or 56dp (search rows with thumbnail, 76dp only when a second line exists).
Controls share three heights: 56dp primary, 48dp secondary/field, 40dp compact value buttons.
Nothing is taller than it needs to be to be tappable.

**Surface philosophy.** Three layers, flat: page (`background`), quiet fill (`surfaceContainerLow`,
no border) for inputs and grouped controls, and card (`surfaceContainerLowest` + 1dp
`outlineVariant`) only for things that are genuinely a unit the user picks up: a recent product, a
search result thumbnail, a dialog. Elevation exists for exactly one surface, the result dock. No
tinted fills as decoration; tinted fills mean state (selected, meal in progress) or role (the orange
informational box). No side-stripe accents.

**Shape.** Reduce rounding one step across the board: cards 22 → 16dp, buttons/fields 14 → 12dp,
media/thumbnails 12dp, icon plates 10dp, sheet/dock 28 → 24dp, chips stay pill. Circles only for
scrim buttons over a live camera. The wordmark, the numerals and the tomato red carry the
personality; the corners do not have to.

**Accent usage.** Cobalt is spent on: the one primary tile, primary buttons, links/text buttons,
the focused field border, the selected segment. It is not spent on list figures, borders of
secondary tiles, or a spine on every card. Tomato is spent only on confirmed carbohydrate results
(dock, meal rows, recent-card figure). Orange marks the "online, unverified" badge and the single
informational box in Settings. The six destination accents appear only in 20dp icon plates and the
top-bar marker; never as a border, never as a fill larger than a plate.

**Primary / secondary actions.** Primary: filled `primary`, 56dp, 12dp radius, `labelLarge`
SemiBold, full width or weighted half. Secondary: outlined, 48dp, `primary` label, 1dp
`primary` at 45% alpha border. Tertiary: text button, `primary`, no container. Value buttons
(`−25`, `½ pack`, usual portions): 40dp, `surfaceContainerLow` fill, no border, `onSurface`
SemiBold label. The same four treatments on every screen, no bespoke clickable rows.

**Numerical results.** One `ResultValue`: 72sp Bold tomato numeral and 26sp Bold unit on a shared
baseline, left-aligned on every dock (Product, Quick calculation and Meal alike), copy button
vertically centred on the numeral's cap height, one `supporting` line beneath (`≈ 23 g whole
grams`), one `bodySmall` provenance line only when the source is online. Preview figures (search
rows, recent cards) use the same number/unit pairing at `titleMedium` in ink, tomato only when the
figure is a real remembered result (recent card), never when it is a per-100 preview (search row).

**Light theme.** Paper `#F7F2E8` stays. Fewer edges: inputs and value buttons become quiet fills,
tiles lose coloured borders, dialogs move to warm white. The only saturated fills on a light screen
are the primary tile/button and the tomato numeral.

**Dark theme.** Graphite, not inverted cream, stays. Make it sophisticated by contrast discipline
rather than brightness: large fills get a deep cobalt (`primaryTile` ≈ `#2E4DB5` with chalk text),
pale cobalt `#82A2FF` is reserved for text, icons, borders and small buttons; the media plate
shrinks to a 64dp thumbnail so the light slab disappears; cards are `surfaceContainerLow` with a
1dp `outlineVariant` hairline; the coral result remains the brightest thing on any screen.

**Motion.** Unchanged in spirit: 120/220ms, no choreography. Keep result cross-fade, the copy
check, the `✓ Added` pulse, the quick-add pop, the nav fade. Add only: the dock never animates
height (the field must not jump), and the scanner dock cross-fades its guidance text (already does).

---

## 3. Target compositions for the important screens

Top to bottom. Sizes in dp at 1.0×. These are the visual targets Opus implements.

### 3a. Home (light) [reference: 27]

1. Top row, 64dp: wordmark `headlineMedium` Bold at 20dp; gear 48dp at end. Backdrop motif behind
   the wordmark at the right, inset so it clears the gear (already does).
2. Search bar, 56dp, 8dp below: `surfaceContainerLow` fill, no border, 12dp radius, leading 24dp
   search icon, placeholder `Search products` (no floating label; drop `label`), 1dp `primary`
   border only while focused, trailing × while non-empty. This is the first-class search entry.
3. Meal strip (only mid-meal), 44dp, 12dp below: `secondaryContainer`, 12dp radius, `Meal · 1
   item · 23.5 g` `titleSmall` + chevron. Unchanged colour; radius follows the new token.
4. Primary tile, 88dp, 16dp below: filled `primaryTile`, 16dp radius, 40dp icon plate (white
   16%), title `titleMedium`, subtitle `bodySmall` 88%, chevron. Only one shadow in the app besides
   the dock: none here; make it flat (drop the 5dp accent shadow).
5. Label-scan tile, 72dp, 8dp below: `surfaceContainerLowest` + 1dp `outlineVariant` (no teal
   border), teal only in the 40dp icon plate (teal 10% fill, teal icon). Same anatomy as the
   primary tile so they read as a pair with one loud, one quiet.
6. `Enter manually` text button, 48dp, 8dp below, centred, unchanged.
7. Section `Recent` (`titleSmall`) 24dp below the last action; recent cards 8dp apart:
   `surfaceContainerLowest` + hairline, 16dp radius, 72dp tall. Remove the 4dp accent spine.
   Thumbnail 48dp, 12dp radius. Name `titleMedium` (2 lines max), portion `bodyMedium` variant.
   Right column: figure `23.5 g` in tomato `titleMedium` over `CARBS` eyebrow; then the existing
   quick-add + star segment (36dp tall, pill, hairline). Favourite state is the filled star alone.
8. Empty state (no recents): unchanged copy; step strip plates become 36dp squares with 10dp
   radius (already are) at 10% tint.

### 3b. Product / Quick calculation [reference: 18, 20, 28]

Fixed frame, no hero photo. The photo moves into a thumbnail.

1. Top bar, 64dp: back, marker, product name `titleMedium` (2 lines), star, overflow.
2. Meal strip (only mid-meal), 40dp: identical to Home's strip, full width inside the 20dp edge.
   This replaces the compact meal bar that currently lives inside the dock.
3. Identity row, 72dp, 8dp below: 56dp thumbnail (12dp radius, opens the gallery; monogram
   plate when no photo) at left; at right, `67 g carbs / 100 g` `titleLarge` SemiBold, and on
   the next line the `Online value` badge (unchanged pill) followed by `Verify` as a text link on
   the same baseline. The `Check package if needed` hint is deleted; the dock carries provenance.
   Quick calculation (no name) shows no thumbnail and the title `Quick calculation`.
4. Portion group, 16dp below, in a scroll zone that fits without scrolling on 360×720:
   - Label row: `Portion` `labelLarge` left; on the right, when portion units exist, the
     grams/slices chips (pill, 32dp, `JtcFilterChip`).
   - Field, 88dp: `surfaceContainerLow` fill, no border at rest, 1dp `primary` on focus, 12dp
     radius, numeral `NumberType.portion` centred, unit `titleMedium` at the end. The old
     centred question `How much are you eating?` is dropped; the label plus the empty field's `0`
     placeholder say the same thing.
   - `Usual` row (when present), 8dp below: value buttons.
   - Adjust row, 8dp below: four value buttons 40dp, `surfaceContainerLow`, `onSurface`
     SemiBold, 8dp gaps. Pack row 8dp below, same treatment, only when a package size exists.
   - `+ Add portion unit` text button, 8dp below, left aligned (no leading `+` in the label;
     use a 18dp `Add` icon).
5. Result dock, pinned, 24dp top radius, `surfaceContainerLowest`, 6dp elevation, 20dp side
   padding, 16dp top, 20dp bottom:
   - `CARBS` eyebrow.
   - Numeral row 80dp: `ResultValue` baseline-aligned left; copy icon button 48dp at the end,
     centred on the numeral's cap height.
   - `≈ 23 g whole grams` `supporting`, 4dp below.
   - Provenance, one line, `bodySmall` variant, only for online products: `Online value · not
     checked against the package`.
   - 16dp, then the button row: `Add to meal` secondary (48dp outlined, primary label) and
     `Add & scan next` primary (56dp), weighted equally. The `✓ Added` state stays.
   - Pending state (no portion yet) keeps the 96dp reserved height with `67 g per 100 g` and
     `Enter a portion` exactly as now.
   Height budget: 16 + 16 + 80 + 20 + 18 + 16 + 56 + 20 ≈ 242dp with provenance; 224 without.

   Check: 64 + 40 + 72 + 16 + 24 + 88 + 8 + 40 + 8 + 40 + 8 + 40 = 448dp above the dock; with a
   242dp dock that is 690dp, inside 720dp at 1.0× and inside 914dp at 1.3× on the reference phone
   with the scroll zone absorbing the rest.

### 3c. Meal [reference: 29]

1. Top bar as today; `Clear meal` stays a text button.
2. Rows 64dp: name `bodyLarge`, `35 g · 23.5 g` `bodyMedium` variant with the carbohydrate
   figure in tomato `titleSmall` at the row's end instead of inside the grey line (the row's
   right column becomes `23.5 g`, and the left subline is just `35 g`). × 48dp after it. Hairline
   dividers, no cards.
3. Dock: identical anatomy to Product's dock, **left-aligned** (`MEAL TOTAL` eyebrow, numeral,
   copy, whole-gram line), then `Scan next item` primary. Today Meal centres; centring is the only
   place the two docks disagree.
4. Empty: `No items yet` / body centred in the list area, unchanged.

### 3d. Search rows (Home inline and Search screen) [reference: 16]

56dp minimum row, 76dp when the name wraps. Thumbnail 48dp, 12dp radius. Name `titleMedium`
SemiBold ink, brand · quantity `bodySmall` variant. Right column, 88dp reserved width: `67 g` in
`titleMedium` SemiBold **ink** over `/ 100 g` `bodySmall` variant. Blue leaves the list; a column
of eight blue numbers competed with the only tappable blue element on the screen and read as
links. Hairline dividers between rows, unchanged. `No carbohydrate value` sentence stays in the
text column.

### 3e. Scanner chrome (both scanners, both themes) [reference: 33, 31, 39]

- Top: two 40dp black-45% circle scrim buttons, close at start, torch at end. No title band.
- Guide: barcode keeps the 2dp cobalt rounded frame; label keeps the light/dark corner brackets.
- Bottom dock, 16dp inset, black 66%, 16dp radius, 16dp padding: title `titleMedium` white
  (`Scan barcode` / `Align the nutrition table`), guidance `bodyMedium` white 80% (one line,
  cross-faded), then the action row: barcode → ghost outlined `Enter barcode` (white 32% border);
  label → primary 56dp `Capture label` plus a white text button `Enter manually`. While capturing,
  the spinner appears once, inside the button; guidance says `Hold still`.
- Frozen review screens (crop, assist, verify, conflict): keep light chrome but the header sits
  on `surface` with a hairline below it, and the control area on `surfaceContainerLow`. The header
  copy is two lines at most.

### 3f. Settings [reference: 12, 13]

Section eyebrow, 24dp above, 8dp below. Segments unchanged. Rows 48dp, 8dp apart: `bodyLarge`
ink label (destructive rows in ink too, red only on the confirm button), supporting `bodySmall`
where present. `Rate on Google Play` becomes a plain row with a 20dp amber star leading icon.
Version line moves to the bottom of About in `bodySmall` variant. The orange `Important` box
stays as the one informational surface (16dp radius). Confirm dialogs get a title and a verb that
matches the row (`Clear`).

### 3g. Tutorial

Previews render the real components (3a, 3b, 3c) with fake data. Narration block, rail, pointer,
scrim unchanged.

---

## 4. Ranked recommendations

### P0 — quality defects and inconsistencies

**P0-1 · Portion input hidden under the result dock; hero photo too large** — implement 3b.
Files: `product/ProductScreen.kt` (`CalculatorBody`, `ProductSummary`, `ResultPanel`,
`PortionField`, `QuickAdjustRow`, `PackShortcuts`), `components/ProductHeroImage.kt` (becomes a
56dp thumbnail; keep gallery tap and monogram), `product/ProductTopBar`. Scope: medium.
Evidence: [20, 28, S02, F01].

**P0-2 · Result unit rendered as a subscript** — `components/ResultValue.kt`: both `Text`s
`Modifier.alignByBaseline()`; set `NumberType.result.lineHeight = 72.sp` with
`LineHeightStyle(Center, Trim.Both)`; verify at `resultAutoSize` minimum (36sp) and on Meal.
Scope: small. Evidence: [crop_result_panel, 29, 09].

**P0-3 · Raw `%1$s` in the Verify dialog** — `product/VerifyDialog.kt:96`:
`stringResource(R.string.manual_carbs, basis.unitLabel)`, label follows the chip. Scope: small.
Evidence: [45].

**P0-4 · Tutorial previews are a different design** — `onboarding/TutorialPreview.kt`: reuse
the real composables, delete gradients, circles, outlined `Enter manually`, boxed meal rows.
Scope: medium. Evidence: [05–10 vs 11, 29].

**P0-5 · Two scanners, two chromes; duplicated capturing text** — implement 3e in a shared
`scan/ScannerChrome.kt`; delete `ScrimIconButton`/`LabelScrimIconButton` duplication. Scope:
medium. Evidence: [33, 31, 39].

### P1 — meaningful polish

**P1-6 · Backdrop motif collides with trailing controls** — remove `AccentBackdrop` from every
screen except Home (decision in section 2). Product, Meal, Search, Settings, Manual keep the
`DestinationMarker`. Scope: small. Evidence: [18, 29, 44].

**P1-7 · Outlined buttons look disabled; value buttons undefined** — add `JtcOutlinedButton`
(primary label, primary-45% border, 48dp) and `JtcValueButton` (40dp, `surfaceContainerLow`, no
border, `onSurface` SemiBold). Replace the 15 `OutlinedButton` sites. Scope: small. Evidence:
[20, 23, 42, 40].

**P1-8 · Fields, chips, menus have two looks** — `jtcTextFieldColors()` (`surfaceContainerLow`
rest, `surfaceContainerLowest` focused, `primary`/transparent border), `JtcFilterChip` (pill),
`JtcDropdownMenu` (12dp, lowest, hairline). Apply to all 17 fields, 8 chips, 3 menus. Scope:
small–medium. Evidence: [37, 43, 45, 24, 44].

**P1-9 · Global radius step down** — `Space.cardRadius` 22 → 16, `buttonRadius` 14 → 12,
`mediaRadius` 20 → 12, `sheetTopRadius` 28 → 24; add `plateRadius = 10.dp`; replace
`RoundedCornerShape(50)` ×3 with `CircleShape` (scanner scrim buttons only). Scope: small, wide
blast radius; re-screenshot everything.

**P1-10 · Home surface simplification** — implement 3a: search bar as quiet fill without floating
label; flat primary tile (no accent shadow); label tile without teal border; recent cards without
the spine; thumbnails 12dp. Scope: small–medium. Evidence: [27, 16].

**P1-11 · Settings reads loud and marketing-heavy** — implement 3f; fix `settings_rate` →
`Rate Just the Carbs` (or `Rate on Google Play`); titled confirm dialogs with `Clear`. Scope: small.
Evidence: [12, 13, 14].

**P1-12 · Dialog surface muddy in light** — `JtcDialogDefaults`: light `surfaceContainerLowest`,
dark `surfaceContainerHigh`. Scope: small. Evidence: [14, 30, 45].

**P1-13 · Dark mode hierarchy inversion** — add `primaryTile`/`onPrimaryTile` (light =
primary/onPrimary; dark ≈ `#2E4DB5`/`#F3F0E8`, must pass `ContrastTest` 4.5:1 and
`AccentRecessionTest`), use for the Home tile and the scanner capture button; thumbnail replaces
the media plate. Scope: small. Evidence: [D06, D08].

**P1-14 · Search figure colour** — `SearchNutritionColumn`: value in `onSurface` (was `primary`),
`titleMedium` SemiBold; update `DESIGN.md`. Scope: small. Evidence: [16].

**P1-15 · Meal dock alignment and row figure** — implement 3c. Scope: small. Evidence: [29].

### P2 — finishing touches

- Add-portion-unit form should `bringIntoView` when it expands [24].
- Carousel `Skip` at `labelLarge` / 0.72 alpha on the coloured slides [01, 03].
- Crop-screen header copy to two lines [40].
- `Product not found` title on a name search → `No matches` [47].

---

## 5. Design-system cleanup

| Area | Today | Standardise to |
|---|---|---|
| Radii | 22 / 14 / 20 / 999 / 28 plus literal 12, 10, 4, 2, `RoundedCornerShape(50)` | 16 card / 12 button+field / 12 media / 10 plate / pill chip / 24 sheet; `CircleShape` for camera scrim buttons only |
| Buttons | `Button`, grey-label `OutlinedButton`, `TextButton`, ad-hoc clickable rows | `PrimaryAction` 56, `JtcOutlinedButton` 48, `TextButton`, `JtcValueButton` 40 |
| Fields | 2/17 styled | `jtcTextFieldColors()` everywhere; no floating labels on search bars |
| Chips | 3 pill, 5 default | `JtcFilterChip` pill |
| Menus | 1 styled, 2 default | `JtcDropdownMenu` |
| Dialogs | `surfaceContainerHigh` both themes, sometimes untitled | light lowest / dark high, always titled, verb matches the row |
| Cards | lowest + hairline, plus spines, plus teal border, plus accent shadow | lowest + hairline only; no spines, no accent borders, no shadow |
| Icon plates | 48/12, 36/10, 36 circle, 40 circle | 40dp and 36dp squares at 10dp radius, 10% accent fill, 20–22dp icon |
| Backdrop | every non-camera screen | Home only |
| Result | bottom-aligned Row, centred on Meal | baseline-aligned, left on every dock |
| Preview figures | blue in search, tomato in recents | ink in search (per-100 preview), tomato in recents (remembered result) |
| Hero | 150–280dp plate | 56dp thumbnail in an identity row |
| Rows | 12dp pad + 16 gap in Settings; 76dp search rows | 48dp settings rows, 56/76dp search rows |
| Dark fills | `primary` pastel as tile fill | `primaryTile` deep cobalt |

---

## 6. Light vs dark

**Light — good; two structural faults and one surface habit.** Fix the calculator (P0-1), the
dialog surface (P1-12), and the box-on-box habit (P1-9, P1-10). The grey backdrop on Settings is
the one place the motif reads as noise; it goes with P1-6.

**Dark — acceptable; one hierarchy inversion.** Graphite layering, dividers and the coral result
all work; the navy meal strip is a quiet, good surface. The pale-cobalt tile and buttons invert
the hierarchy (P1-13) and the light media plate brackets the numeral (solved by the thumbnail).
Keep the dark dialog surface as is. Scanner chrome is theme-independent and correct.

---

## 7. Screen notes not covered above

- **Welcome carousel [01–03].** Strong; keep. Only the `Skip` weight (P2).
- **Search states [47].** Keep the recovery panel; rename the no-match title.
- **Not found [36].** Correct hierarchy; the barcode line at the bottom is the right quiet place.
- **Manual entry [37, 38].** Clean; only field/chip consistency. Errors on Save only is fine.
- **Crop / assist / verify [40, 42, 43].** Three stacked full-width buttons are right for the
  situation; only the header band tone and copy length change.
- **Gallery [46].** Fine.

---

## 8. Preserve list

Do not disturb:

- Palette roles and the tests that pin them (`ContrastTest`, `AccentRecessionTest`); the six
  destination accents (now confined to plates and the marker).
- `NumberType` scale (72/52/26) and `resultAutoSize`.
- `JtcTopBar` with the three-rule `DestinationMarker`, one title size.
- The result dock as the one elevated surface; `surfaceContainerLowest`; eyebrow + numeral +
  whole-gram + provenance anatomy.
- `SourceBadge` wording; the no-alarm provenance copy; `Not checked against the package`.
- Home order (search → meal → two tiles → manual → recents/hero); mid-meal `Scan next item`
  copy; the `secondaryContainer` meal strip.
- Recent card content (name, portion, tomato figure over `CARBS`, quick-add + star segment) and
  the quick-add pop / `✓ Added` / copy-check confirmations.
- `SearchResultRow` anatomy and its accessibility description.
- `SettingsChoiceSegment`.
- Carousel type and colour drench; tutorial spotlight, scrim, pointer, rail and copy.
- Scanner guide geometry, haptics, live regions, `clearAndSetSemantics` decisions.
- Motion values and the nav fade.

---

## 9. Opus implementation handoff

Order matters: tokens and shared components first, then screens. Do not touch calculation, OCR,
search, navigation or persistence. Run `:app:testDebugUnitTest`, `:app:lintDebug` and the UI test
classes named per step. Re-screenshot the listed states after each step and compare against
section 3.

**Step 1 — tokens and shared components (P0-2, P1-7, P1-8, P1-9, P1-12, P1-13)**
- `theme/Theme.kt`: `cardRadius 16`, `buttonRadius 12`, `mediaRadius 12`, `sheetTopRadius 24`,
  add `plateRadius 10`; add `ExtendedColors.primaryTile/onPrimaryTile`; `NumberType.result`
  line height 72sp + `LineHeightStyle(Center, Trim.Both)`. Extend `ContrastTest` /
  `AccentRecessionTest` to the new pair.
- `components/ResultValue.kt`: `alignByBaseline()` on numeral and unit.
- `components/`: `JtcOutlinedButton`, `JtcValueButton`, `jtcTextFieldColors()`, `JtcFilterChip`,
  `JtcDropdownMenu`; `JtcDialogDefaults` theme-dependent container.
- Replace call sites: 15 `OutlinedButton`, 17 `OutlinedTextField` colours, 8 `FilterChip`,
  3 `DropdownMenu`, 3 `RoundedCornerShape(50)`.
- Tests: `QuantityFormattingTest`, `ProductScreenTest`, `MealScreenTest`, `SettingsScreenTest`.

**Step 2 — Product / Quick calculation (P0-1, 3b)**
- `ProductHeroImage.kt` → `ProductIdentityRow` (56dp thumbnail + per-100 + badge/Verify).
- `ProductScreen.kt`: remove `MealBarIfPresent` from `ResultPanel`; meal strip under the top
  bar; `SourceBadge(showHint=false)` here; portion label row replaces the centred question;
  field as quiet fill; value buttons; `bringIntoView` on the add-unit form; provenance to one
  line; dock paddings per 3b.
- Tests: `ProductScreenTest` (32), `QuickCalculationScreenTest` (14), `MealScreenTest` (19),
  `CountablePortionScreenTest` (12), `LabelVerificationScreenTest` (8). Screenshots: 411×914 at
  1.0/1.3/1.8×, 360×720 at 1.0×, each with and without a meal, keyboard open and closed, grams and
  countable modes, pending and result states, light and dark.

**Step 3 — Home and Search (P1-10, P1-14, 3a, 3d)**
- `HomeScreen.kt`: search bar without label, quiet fill; `HomeActionCard` flat, outlined variant
  without accent border; `RecentCard` without spine; `ProductThumbnail`/`SearchThumbnail` 12dp;
  `AccentBackdrop` stays here only.
- `components/Common.kt`: `SearchNutritionColumn` value in `onSurface`.
- Tests: `HomeScreenTest` (23), `HomeQuickAddScreenTest`, `SearchScreenTest` (29),
  `HomeTutorialReminderTest` (7), `WelcomeCarouselScreenTest` (8).

**Step 4 — Meal, Settings, dialogs (P1-15, P1-11, P0-3, 3c, 3f)**
- `MealScreen.kt`: left-aligned `MealTotalPanel` sharing the Product dock anatomy; row figure at
  the end in tomato; drop `AccentBackdrop`.
- `SettingsScreen.kt`: 48dp rows, ink destructive labels, rate row, titled `ConfirmDialog` with
  `Clear`; `strings.xml` `settings_rate`.
- `VerifyDialog.kt:96` unit argument.
- Tests: `MealScreenTest`, `SettingsScreenTest`, `ThemeDefaultTest`.

**Step 5 — Scanner chrome (P0-5, 3e)**
- `scan/ScannerChrome.kt` shared; `ScannerScreen.kt` drops the title band; `LabelScannerScreen`
  `ScannerCard(review=false)` becomes the dark dock; single spinner; frozen-state headers on
  `surface`. No change to `ScanRegionOverlay` geometry, `captureLabel`, or any OCR path.
- Tests: `ScanPolishScreenTest`, `AssistedReadingScreenTest`, `UnverifiedProposalLifecycleTest`,
  plus the OCR corpus classes to prove nothing under the chrome moved.

**Step 6 — Tutorial (P0-4, 3g)**
- `TutorialPreview.kt` reuses real components (`internal` visibility), keeps
  `clearAndSetSemantics {}` at the preview root.
- Tests: `TutorialScreenTest`, `TutorialNavigationTest`, `TutorialVisualTest` (six configs).

**Step 7 — remove secondary-screen backdrops (P1-6)** and the P2 finishes; update `DESIGN.md`
to match section 2.

**Regression risks**
- `resultAutoSize` must still shrink without clipping (`125.3 g` at 1.8×) after the line-height
  change.
- Radius token change touches every screen; `TutorialVisualTest` and any pixel-adjacent
  assertions on spotlight radii (`TutorialFocusStyle.radius`) follow the tokens automatically but
  must be re-inspected.
- `TutorialAnchors` must still resolve when previews use real components.
- The meal strip on Product must not collide with a two-line product title; it sits below the
  bar, not in it.
- `primaryTile` dark must pass recession (luminance < 0.366) and 4.5:1 with its text.
- Removing hero height frees the layout; check `fadeOutWhenMoreBelow` still draws nothing when
  the zone fits.

**States to recheck after implementation (both themes)**
Home empty / with recents / mid-meal; search typing, results, no matches, offline; Product
loading, pending, result, keyboard open, countable mode, add-portion form open, newer-remote
notice; Quick calculation; Meal empty / one / many; Manual entry with errors; barcode scanner live
and dialog; label scanner live, capturing, crop, assist, verify; Settings and both confirm
dialogs; tutorial 1–6; welcome 1–3; 360×720dp and 1.3×/1.8× on Home and Product.
