# Protein as an optional second reading: design specification

Status: **implemented 2026-09-25** on branch `ux-polish-2026-09-24` after the owner asked for it
("implement it"); the §2 amendment is recorded in `docs/MASTER-PROMPT.md`. Two decisions were taken
during implementation: the glyph is `EggAlt` (the section 3 gate failed on the emulator screenshot:
the plain egg read as a water drop at 20dp), and at 320dp the footer wraps at 1.0x (measured on the
device: *Enter manually* 131dp and the chip 151dp in a 280dp row). The design text below is kept as
written.

Original status: design complete and reviewed, awaiting owner review. Not implemented. Written 2026-09-24 against
branch `ux-polish-2026-09-24` at `4b24328` and re-checked against `ead38ec` (the two commits between
are a CHANGELOG entry and one test). The shipped app was captured on the `carbscan` emulator in Light
and Dark, at 1.0x and 1.8x, on 411x914 and 320x640; the protein layout itself exists only as the
companion mockups and the rules below, and section 10 names the geometry checks that must pass before
the layout is trusted. Companion mockups:
`docs/superpowers/specs/2026-09-24-protein-design-mockups.html` (open it in a browser; every token in
it is the app's own). Section 15 records the eight-lens review this revision comes from.

This patch adds protein from Open Food Facts data only. Protein from nutrition-label OCR is the next
patch; section 9 defines the seam it plugs into, and nothing in the presentation depends on the source.

**Prerequisite for implementation:** the owner's amendment to `docs/MASTER-PROMPT.md` §2 (section 12,
item 1). Until it is approved, the scope document forbids what this design builds.

## 0. Understanding and assumptions

What the owner asked for, in one sentence: an optional protein figure for the same portion as the
carbohydrate answer, off by default, switched on from Home before a scan or from Settings, shown as
clearly secondary information, never estimated, never shown as 0 g when missing, never a second card,
and designed so the OCR patch can reuse the presentation without a redesign.

Assumptions made because the session is unattended. Each is a place the owner can overrule.

1. **This is not "protein tracking".** `docs/MASTER-PROMPT.md` §2 forbids "fat/protein/sugar
   tracking" and a "macros dashboard". The line, made explicit so it can be tested: a nutrient figure
   is tracking when it is stored per use, summed across portions, shown outside the open calculator,
   or compared with a target. A per-portion protein reading on the calculator's result surface is none
   of those. The design keeps it that way structurally (section 11) and asks the owner to amend §2 by
   a dated block rather than by editing the verbatim brief (section 12).
2. **Protein follows the carbohydrate result style.** Decimal-first shows `4.1 g`; whole-first shows
   `4 g`. One setting, one rounding rule, derived independently from the exact figure, never from the
   displayed carb figure.
3. **In this patch protein exists only on Open Food Facts products.** A manually entered product, a
   product created from a label scan and the quick-calculation scratch product carry no protein, and a
   background refresh never attaches an online protein figure to them (section 9). So in this patch the
   protein's source is always the product's source, and the only mixed case is a product whose carbs
   the user verified against the package while the protein stays an online value; the dock says so in
   words.
4. **The meal does not carry protein.** Adding a portion to the meal stores carbs only, as today. A
   meal protein total is the first step towards the tracker §2 forbids and needs a schema change to the
   meal snapshot; it is deferred, with the seam named in section 9.
5. **Recents and search stay carbs-only.** The figure a returning user comes back for is the carb
   figure; protein is one tap away on the product.
6. **Protein is the only second reading.** A third nutrient (fat, energy, sugars) is a scope change
   that needs an owner decision under §2 and a review against the regulatory assessment's §7.2 before
   any design (section 12). Nothing here is built to make one cheap.

## 1. Design concept

**Protein is a second reading, never a second answer.** The app has one answer surface, the result
dock, and one answer colour, coral. Protein joins the dock as a labelled pair in ink, one line, four
times smaller than the carb numeral, placed after the carb block (numeral, whole-gram restatement,
provenance sentence) so the answer and its caption stay welded together and the reading follows them.
It uses the app's existing "eyebrow + figure" grammar (`CARBS 37.4 g` on a recent card, `PORTION` over
the field), so it looks like it was always there, and it uses none of the answer's properties: not its
size, not its weight, not its colour, not its stacked layout.

**The mode lives with the ways in.** Home's entry cluster is two scan tiles and *Enter manually*. The
control is a toggle chip on that last row, at the trailing edge, closing the cluster: "here are the
ways in, and here is the one thing you can ask them to read as well". Off, it is a quiet pill in ink
with a hairline edge and an outlined egg. On, it takes the paired container every selected control in
the app already uses, the egg fills, and the primary tile's subtitle says what the next scan will read.
At the default text size it costs Home no vertical space and adds no card.

**One state, two controls.** Home's chip and the Settings row read the same persisted boolean from the
one settings flow and write it through the one repository method. There is no session mode. Both are
labelled `Show protein`, so the user meets one name for one thing.

**Missing is a state, not a hole.** When protein is on and the record lists no value, the same row
says `PROTEIN  No online value` in supporting ink. Never 0 g, never an estimate, never a warning, and
never a claim about the package: an EU package always prints protein, so the wording names the source
that lacks it.

**The source is invisible to the row and visible in the sentence.** The dock reads `exactProtein`
from UI state; UI state reads `proteinPer100` from the product; the row does not know where the figure
came from. The provenance sentence is the only place a source is named, and it already is today.

Four separations keep the hierarchy legible in every configuration: scale (72sp against 18sp), weight
(Bold against SemiBold), hue (coral against ink) and layout (stacked against inline). Any one survives
alone, which is what holds in monochrome, at 1.8x and in Dark.

## 2. Home: the Protein chip

### Placement

The footer of the entry cluster, which today is its own `LazyColumn` item (`key = "manual"`) holding
a full-width *Enter manually* text button. That item becomes a wrapping row: *Enter manually* at the
start, the chip at the end. The item keeps its key (`HomeScreenTest` scrolls to it by index), the
button keeps `HOME_MANUAL_TAG` and drops `fillMaxWidth()` (inside a wrapping row a full-width child
takes the whole first line and pushes the chip under it every time); its padding-then-`heightIn`
modifier order, and the comment that explains it, stay. Reasons for the placement, in order of weight:

- It is a modifier of what the ways in will read, so it belongs with them, after them, as the
  cluster's closing element. Above the tiles it would be a third strip between search and the primary
  action; in the wordmark row it would collide with the backdrop motif and the wordmark at 1.8x; beside
  the search field it would read as a search filter.
- The row exists already, so at 1.0x the chip costs no height on the 411dp phone, where the two items
  share one line with ample room. At 320dp it is at the limit: measured in the mockup, *Enter manually*
  is about 127dp and the chip about 150dp, 277dp of the 280dp row. `SpaceBetween` adds no minimum
  gap, so the pair fits with about 3dp between the button's padding and the chip, but a different
  font rasteriser can tip it onto two lines. Both states are correct and both are verified (section
  10); on a 320dp window the chip may therefore cost one chip row even at 1.0x. At 1.8x
  the pair no longer fits on either width, the chip wraps to a second line under *Enter manually*, and
  the Recent heading moves down by one chip row plus the 8dp item gap. That is the honest cost, and it
  is paid only by large-text users who have switched the feature on or off; the chip exists in both
  states, so the footer's height does not change with the state.
- A lone centred link was the weakest line on Home. Two purposeful items at the row's edges give it a
  job: the tertiary way in on the left, the mode on the right.
- It is in thumb reach, directly under the tiles, above the Recent heading, so it is seen before the
  first scan without demanding attention.

The chip leaves with the body while a search query is typed, exactly as the tiles do.

### The control

A custom chip, `ProteinToggle` (see section 8), built from the app's chip anatomy with switch
semantics. It is the app's chip height, not a smaller cousin of it: `Theme.kt` allows three control
heights and no more, and the calculator's mode chips are `heightIn(min = Space.minTouchTarget)`.

| | Off | On |
|---|---|---|
| Container | `surfaceContainerLow` | `secondaryContainer` |
| Label | `onSurface` | `onSecondaryContainer` |
| Glyph | `Icons.Outlined.Egg`, `onSurfaceVariant` | `Icons.Filled.Egg`, `onSecondaryContainer` |
| Hairline, Light | 1dp `outlineVariant` (the recent cards' own edge) | none |
| Hairline, Dark | 1dp `outline` | 1dp `outline` |
| Tile subtitle | unchanged | `Carbs, and protein when listed` (not mid-meal) |

Geometry: pill (`Space.chipRadius`), `heightIn(min = Space.minTouchTarget)` (48dp), 12dp leading
inset, 20dp glyph, `Space.s` (8dp) gap, `labelLarge` SemiBold label `Show protein`, `Space.m` (16dp)
trailing inset. The label does not change with state. The state cue that meets the 3:1 non-text
threshold is the glyph's fill (outlined against filled); the container tint and the label colour are
reinforcement, not the signal, so the glyph's size is load-bearing and is not left to a default.

Why the hairline when off: `surfaceContainerLow` on the page is 1.05:1 in Light and 1.07:1 in Dark. A
mode chip in the calculator gets away with an edgeless quiet fill because a selected sibling sits
beside it; a toggle that stands alone with no edge and a muted label is, token for token, the app's
disabled treatment. The Light hairline is the recent cards' `outlineVariant`; the Dark hairline is
`JtcValueButton`'s page-luminance rule (`background.luminance() < 0.5f`), which keys on the page and
not on the state, so Dark keeps it when on as well (`outline` on `secondaryContainer` measures 3.5:1;
without it the on fill sits at 1.5:1 against the page and the label is byte-identical to the
*Enter manually* link's `primary`). DESIGN.md records this as the second instance of the number-entry
frame's exception: a fill at 1.05:1 cannot bound a control that stands alone.

Semantics: `Modifier.toggleable(value, role = Role.Switch, onValueChange)` on the whole 48dp chip, one
node, merged text exactly `Show protein`, the glyph's `Icon` with `contentDescription = null`. The
platform supplies the spoken On / Off for a `Role.Switch` toggleable (the haptics row in Settings
already relies on that); no `stateDescription` strings are added, so the two controls cannot drift.
Tests assert node properties (`isToggleable()`, `hasRole(Role.Switch)`, `ToggleableState` follows the
setting), never a TalkBack sentence.

Interaction: tap toggles and persists. Container, content and glyph cross in one beat,
`Motion.STANDARD_MS` with the app's ease-out curve (`animateColorAsState` for the colours, a
`Crossfade` for the glyph, same duration); the favourite star, the app's other outlined/filled
control, swaps in one beat too. No scale, no bounce, no ripple colour change beyond Material's
default. **No haptic:** the app's haptics belong to scan outcomes (shutter, confirm, advance, reject)
and CLAUDE.md records that on a poor motor the vocabulary already blurs; a buzz for changing what the
dock shows would dilute "a buzz means read the screen". The Settings row has none either, so the two
controls for one state do not differ in feedback. No snackbar, no dialog, no first-run explanation:
the tile subtitle is the explanation.

### Relationship to Scan

The primary tile stays the only filled surface and the only 88dp object. When protein is on and no
meal is in progress, its subtitle changes from `Fastest way to find a packaged product` to
`Carbs, and protein when listed` (30 characters, one line at 412dp; the shipped subtitle is 38). The
wording claims only what this tile delivers: carbs always, protein when the record lists it. The
merged description the tile speaks (`home_action_description`, "Scan barcode. Carbs, and protein when
listed") carries the mode to TalkBack without a live region on Home. Mid-meal the meal copy wins
(`Add another item to your meal`); the chip alone states the mode. The label tile's subtitle never
changes in this patch, because label scanning does not read protein yet; that asymmetry is honest and
disappears with the OCR patch.

### Light and Dark

Light: off is `#FBF8F1` fill with a `#DED8CB` hairline, `#191B23` label and `#61616C` glyph on the
cream page; on is `#E6ECFF` with `#17336F`, the meal bar's own pairing (10.2:1). Dark: off is `#171A20`
fill with a 1dp `#848997` hairline, `#F3F0E8` label and `#B7B2A8` glyph; on is `#263454` with
`#82A2FF` (5.0:1) and the same hairline. The on tint is a pale container, not an accent fill, so the
recession rule (nothing brighter than the coral figure) is untouched; Home's coral figures are the
recent cards'.

### What it is not

Not a Material `FilterChip`: its role is checkbox, and this control is a switch. Not a bare `Switch` on
Home (a switch on a home screen reads as a settings panel). Not in the tutorial preview, which is a
drawing of Home and teaches Scan, Portion, Carbs only. Not a noun tag: `Protein` alone, sitting above
a list, reads as a filter on the recents beneath it; `Show protein` names the control.

## 3. The mark

`Icons.Outlined.Egg` off, `Icons.Filled.Egg` on, from `material-icons-extended`. The alias is
unversioned under the Compose BOM; it resolves to 1.7.8 in the Gradle cache, whose `classes.jar`
carries both glyphs (verified). This is the same on/off grammar as the app's favourite star, and every
icon the app draws is a Material glyph, so the egg sits beside them without a new style.

Why an egg: it is the one protein symbol that is neither a meat cut nor a gym weight, it is a single
closed shape that survives small sizes in both fills, and it is read as "protein" across cultures. A
letter P was rejected (a monogram, not a symbol, and the app already uses monograms for missing
photos). A bean reads as coffee at small sizes. A dumbbell makes the app a fitness tracker. A custom
glyph derived from the nutrition-rule motif was considered and rejected: the rules already mean
"destination" in the top bar, and abstract marks need a label to say what they are.

Size and the fallback: the chip draws the glyph at 20dp (Material's 2dp stroke at 24dp is 1.67dp at
20dp, 1.5dp at 18dp, and the inner highlight curl that separates an egg from a water drop is the first
thing to go). Implementation is gated on a 1.0x / 411dp device screenshot read at arm's length: if the
outlined egg reads as a drop, the glyph becomes `Icons.Outlined.EggAlt` / `Icons.Filled.EggAlt` from
the same artifact (verified present; the yolk makes it unambiguous) and the more illustrative form is
accepted. The mockups show both at 20dp.

Two rules: the glyph is never tinted with a nutrient colour (it takes the control's content colour),
and it never appears without the word `protein` beside it. An egg alone is also the EU allergen
pictogram; beside the label it is unambiguous, and the OCR patch's proposal screen inherits the rule.

## 4. Calculator: the protein row in the dock

### Composition

The result dock keeps its anatomy and gains one row after the carb block. Top to bottom, with the
answer at rest, on an unverified Open Food Facts product:

```
CARBS                              labelSmall, onSurfaceVariant            (unchanged)
37.4 g                    [copy]   80dp slot, 72sp Bold coral + 26sp unit  (unchanged)
≈ 37 g whole grams                 NumberType.supporting, onSurfaceVariant (unchanged)
Online value, not checked …        bodySmall, onSurfaceVariant             (unchanged)
PROTEIN  4.1 g                     NEW: Space.s (8dp) above; eyebrow + titleMedium ink
[Add to meal] [Add & scan next]    56dp, Space.m above                      (unchanged)
```

The carb block is byte-identical to today: numeral, restatement and provenance keep their spacing
(`Space.xs` before the provenance line). The provenance sentence is the caption of the answer, the one
line a doser reads before acting, and it stays welded to the numeral; the protein row follows it after
an 8dp gap, one step larger, so carbs read as one block and protein as a second one. A first draft put
the row between the restatement and the provenance line; there the sentence re-parented itself to the
protein, and `PROTEIN  No online value` above `Online value, not checked …` read as an explanation of
why protein was missing.

The protein row: a `Row` with baseline alignment; `PROTEIN` in `labelSmall` `onSurfaceVariant`, an
8dp gap, the figure in `titleMedium` (18sp SemiBold Space Grotesk) `onSurface`, formatted with the carb
result style, unit `g` in the same string (`product_protein_value`, `%1$s g`). It is left-aligned in
the dock's column like everything else on the surface, and it is the recent card's carb pair
(`RecentCarbCaption` + `RecentCarbFigure`) recoloured to ink, which is why it reads as the app's own.

Fitting: eyebrow and figure are `maxLines = 1` and the figure is never ellipsised (a figure that
silently loses digits is the failure the numeral's autosize exists to prevent). Portions are unbounded
(`PortionParser` has no ceiling; a test already asserts a 1205 g result), so the figure may reach five
digits; at `titleMedium` that still fits beside the eyebrow on a 280dp column. If a width ever cannot
hold both, the pair is laid out with `WrappingRow` so the figure drops under the eyebrow, start-aligned,
rather than shrinking. `No online value` may take two lines.

No online value: the same row, the figure replaced by `No online value` in `bodyMedium`
`onSurfaceVariant`. Quieter than a figure so it cannot be read as one, and it names the source: the
Open Food Facts record has no value, which says nothing about the package.

### Why this and not the alternatives measured

- **Not a second card or surface.** The dock is the one elevated surface. A second card is the "two
  macro cards" the brief forbids.
- **Not beside the numeral.** The numeral row auto-sizes the carb figure to fit its width; a trailing
  protein column would squeeze it, which is the one thing protein must not do.
- **Not folded into the supporting line** (`≈ 37 g whole grams · 4.1 g protein`). Zero height at
  1.0x, but it buries the figure the user opted into inside a grey restatement of the carbs, equals
  two figures in one style on one line, wraps mid-sentence at large text, and on the one window where
  height matters (section "Height and the keyboard") it wraps too, so it saves nothing there.
- **Not in the identity row as a per-100 figure.** The 2026-09-23 hierarchy pass made that row one
  fact; a second figure there is a second number above the field the user is about to type into.
- **Not a hairline-separated ledge.** DESIGN.md's "fewer edges" rule; spacing separates it well
  enough and the mockup confirms it.
- **Not in the supporting style.** A reviewer asked for the figure at 15sp SemiBold muted so that it
  never outweighs the whole-gram restatement. Rejected: the restatement is a caption of the numeral,
  not a competing figure, the provenance line now sits between them, and two grey lines of one size
  would make protein a second caption of the carbs, which is the "buried" failure above. The row's
  weight is bounded instead: never larger than `titleMedium`, never the numeral's colour.

### Height and the keyboard

The dock's height is a budget (four defects in this app came from growing it). The row costs
`titleMedium.lineHeight × fontScale + 8dp`: 32dp at 1.0x, 39dp at 1.3x, 51dp at 1.8x. It costs 0dp
while typing: it steps aside with the provenance line and the meal actions the moment the IME opens,
so the dock keeps one height for the whole time the keyboard is up, and it returns the instant the
keyboard closes. That is the existing rule for everything below the numeral, applied without
exception.

At rest the cost is paid by `CalculatorFrame` the way every dock change is: the portion zone takes
its height first, the identity takes what is left, so on the 411x914 phone the hero photo may step
down one size (240 to 200dp, or 144 to 128dp) and nothing else moves.

**The field wins over the row.** On the 320x640dp window at 1.3x with a result the portion zone is
already a measured 23dp band (`ProductScreenTest`, the case CLAUDE.md records as a known corner), and
`CalculatorFrame` clamps the zone at 0. A 39dp row there would push the field, the Usual row, the pack
shortcuts and the adjust row off screen, and because the IME is the only thing that compacts the dock,
nothing left on screen could open it: the calculator would be stuck at its answer until Back. So the
row is withheld at rest whenever the zone would fall below `Space.minTouchTarget` (48dp) with the row
present. The decision is arithmetic on one measurement, never a feedback loop: the frame reports the
zone's height, the screen adds the row's known cost back if the row is currently shown, and hides the
row iff `zoneWithoutRow - rowCost < 48dp`. Hiding the row therefore cannot re-enable it, and the
layout cannot oscillate. It is not keyed on the existing short-window heuristic
(`screenHeightDp / fontScale <= 700`), which would take protein away from every large-text user on the
reference phone. On the starved window the user sees carbs and no protein at rest; that is a documented
limitation of that window, the same class as the sliver it already has. The dock animates every change
with its existing `animateContentSize`.

A frame-level alternative exists and is not taken here: reserve the field's height for the zone before
the identity floor, letting the identity clip first. It would also cure today's 23dp sliver for carbs,
but it changes the shipped geometry of the most defect-prone layout in the app and belongs to a
calculator pass, not to this one. Section 14 offers it to the owner.

### Motion

The row has no animation of its own. It is composed when it has something to show and removed when it
does not; the dock's `animateContentSize` (220ms, ease-out-quart) carries the size, and its digits
change in place on later keystrokes, exactly as the carb numeral does. No cross-fade per digit, no
second container inside a dock that already animates its size. Reduced motion is honoured through the
platform's animator-duration scale, which Compose's animation APIs read; no app code reads the setting.

### The copy button

Copies the carb figure only, unchanged. The clipboard exists for transcribing the answer into a dose
calculator; a second number in the clipboard would be the wrong number half the time.

### Accessibility

- The result Box's polite live region description (the stable Box outside the numeral's
  `AnimatedContent`) becomes `"37.4 grams of carbs, 4.1 grams of protein"` when a protein value
  exists and `"37.4 grams of carbs, no online value for protein"` when the record has none. One
  announcement per portion change, carbs first, both facts. The three descriptions are one-to-one with
  the three presentations: the existing `"37.4 grams of carbs"` stays for protein off and for every
  state in which the row is hidden, so its 43 test literals are untouched.
- The protein row is its own node for explore-by-touch: `semantics(mergeDescendants = true)` with
  `contentDescription` `"4.1 grams of protein"` or `"Protein, no online value"`, so `PROTEIN 4.1 g`
  is never spoken as "protein four point one gee". No live region of its own (the dock's announces).
  Linear navigation therefore hears the protein figure twice, once in the announcement and once on
  the row; that is intended, and the traversal order is pinned: `CARBS`, result Box, whole-grams line,
  provenance line, protein row, meal actions.
- Nothing in the row is a tap target; the row is never clickable.

## 5. Complete state behaviour

| State | Home | Dock | Notes |
|---|---|---|---|
| Protein off (default) | Chip off, quiet | Unchanged, no row, no reserved height | Every existing screenshot and test holds |
| On, product loading | Chip on | No dock (loading body) | Nothing to reserve |
| On, no portion yet | | `CARBS` + `Enter a portion`, no row | Protein appears with the answer, not before it |
| On, typing (IME open) | | Numeral slot compact; row hidden | Same rule as provenance and meal actions |
| On, answer, Open Food Facts product, value listed | | `PROTEIN  4.1 g` after the provenance line | Same portion, same basis, same exact arithmetic |
| On, answer, Open Food Facts record without protein | | `PROTEIN  No online value` | A fact about the record; supporting ink |
| On, answer, verified Open Food Facts product with protein | | Row as above; provenance `Carbs checked against the package. Protein is an online value.` | The user verified carbs only |
| On, answer, verified Open Food Facts product without protein, or whose verification changed the basis | | `PROTEIN  No online value`; provenance stays `Checked against the package` | A basis change drops the stored protein (section 9) |
| On, answer, product entered manually or read from a label | | No row | No protein is stored for them in this patch, and a "no value" claim would be untrue |
| On, quick calculation (unsaved, from a label or typed) | | No row | Protein data cannot exist in this patch |
| On, counted portion with a printed weight (`WeightBased`) | | Row shown; grams resolve, protein scales | Same path as carbs |
| On, counted portion with carbs per unit only (`DirectCarbs`) | | No row | No gram figure exists, so no protein figure can; nothing is invented |
| On, portion changed | | Carbs and protein update in the same state write | Every exit of `recalculate()` writes both |
| On, online value changed notice (carbs) | | Notice unchanged; protein follows the snapshot | See session immutability below |
| On, starved window (zone with the row under 48dp) | | Row withheld at rest | The field wins; section 4 |
| Toggled in Settings while a product is open | | Row appears or leaves, animated | State flows through the one settings flow |
| Mid-meal on Home | Chip states the mode; the tile keeps its meal copy | | TalkBack learns the mode from the chip only |

Session immutability, extended to protein: the calculator works from the product snapshot loaded
with the screen. A background refresh may write a newer record to the cache for the next visit; it
never changes the open session, and it raises no notice for protein (a secondary figure does not
interrupt). Protein is never refreshed on its own: it is written when the nutrient record is written,
so the stored carbs and protein always come from one fetched record (section 9). The existing actions
that reload the product wholesale (apply the newer online value, verify, reset to online value) bring
the record's protein with them, as they bring everything else.

Product loading: no layout jumping is possible because the dock is not composed during the loading
body, and the row is never reserved empty.

## 6. Settings

Section **Results**, after the decimal/whole segment, before the divider. The haptics row's anatomy
plus a supporting line, written out so there is one candidate layout:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouchTarget)
        .toggleable(value = settings.proteinEnabled, role = Role.Switch, onValueChange = onProteinChanged)
        .padding(vertical = Space.s),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.protein_toggle), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.settings_protein_body), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = settings.proteinEnabled, onCheckedChange = null)
}
```

- Label: `Show protein` (`bodyLarge`), the chip's own string.
- Supporting: `Shows the protein in your portion under the carbs when Open Food Facts lists it. Carbs
  are unchanged.` (`bodySmall`, `onSurfaceVariant`; the `SettingsAction` supporting-line style). Two
  short sentences: the source the user is consenting to, named as the app's 1.0.8 copy rule requires,
  and the promise the brief asks for.
- Control: Material `Switch`, trailing, `onCheckedChange = null`; the row is one node because of that
  line (a non-null callback makes the switch a second focusable node inside the toggleable row).
  Merged reading: "Show protein, Shows the protein … unchanged, [state], switch".

Why Results: the setting changes what the result shows, not how the app is used (Interaction) or what
it stores (Data). The `Results` heading carries `heading()` semantics, so TalkBack heading navigation
lands on it.

The Settings row and the Home chip are the same state. Toggling either updates the other on the next
frame through the settings flow. No confirmation, no snackbar, no haptic.

## 7. Responsive and accessibility behaviour

- **Narrow (320dp).** Footer row: `Enter manually` (about 127dp) and the chip (about 150dp) fill 277dp
  of 280dp at 1.0x. They share one line when the device's text metrics match the mockup's and wrap
  otherwise; neither is a defect, and the verification pass records which one the 320x640/160
  emulator shows. Dock: the protein pair fits beside its eyebrow up to five digits at `titleMedium`; the
  provenance sentence wraps as it does today.
- **Large text (1.3x, 1.8x).** The footer is a `FlowRow(horizontalArrangement = SpaceBetween,
  verticalArrangement = spacedBy(Space.s))`; when the two items do not fit, the chip moves to its own
  line under *Enter manually*, start-aligned by `SpaceBetween`, and grows with its label
  (`heightIn(min = 48dp)`, never `height`). That wrap happens on the 411dp phone at 1.8x as well as on
  320dp. In the dock the row scales with text (section 4) and yields to the field on a starved window.
- **Long product names, large values, small values.** Irrelevant to the chip. In the dock a
  three-digit carb figure auto-sizes as today and the protein row is unaffected by it; `0.3 g` and
  `0.0 g` (a record that lists zero) render as figures; a missing value renders as `No online value`;
  a very wide figure drops under its eyebrow rather than shrinking or losing digits.
- **Colour-blind and monochrome.** Chip state is carried by the glyph's fill (the one cue at 3:1 or
  better), reinforced by container, label colour, the spoken state and the tile subtitle; dock hierarchy
  by scale, weight and layout. Nothing is colour-only. A greyscale screenshot row is in the manual QA
  list, because no test measures glyph legibility.
- **TalkBack.** Chip: one switch named `Show protein` with a state. Settings: one switch named
  `Show protein`. Dock: one polite announcement carrying both facts; the row itself is readable on
  explore.
- **Contrast.** Computed from the live tokens: chip label off 15.4:1 / 16.3:1 (Light / Dark), glyph
  off 5.8:1 / 8.3:1, on pair 10.2:1 / 5.0:1, Dark hairline on the on fill 3.5:1 and on the page 5.3:1,
  dock eyebrow and `No online value` 6.1:1 / 6.6:1, figure 17.0:1 / 12.3:1, Settings supporting 5.5:1 /
  8.8:1. The Light on pair is pinned by value in `ContrastTest` under the primaryContainer name; the
  others are new assertions (section 10).
- **Reduced motion.** The only new motion is the chip's 220ms cross; the row rides the dock's existing
  size animation. Both are state feedback, not decoration, and both degrade to an instant swap through
  the platform animator scale.
- **Touch targets.** Chip 48dp in both states through `heightIn`, never inside another clickable (the
  footer is a plain `LazyColumn` item); the row in Settings 48dp minimum; nothing else is tappable.

## 8. Reusable design rules and components

### Rules

1. **A second reading is an inline labelled pair in ink, after the answer's own block, never a second
   card.** Eyebrow `labelSmall` + figure `titleMedium` on one baseline; never larger than
   `titleMedium`, never the answer's colour. Protein is the only second reading. A third nutrient is a
   scope change requiring an owner decision under §2 and a review against the regulatory assessment's
   §7.2 before design; this rule is repeated in the row's KDoc.
2. **The answer colour is for the answer.** Coral is never used for protein, its label or its glyph.
3. **Everything below the numeral steps aside while typing.** Provenance, meal actions, protein.
4. **A missing secondary figure is a worded state in supporting ink, in its own slot, naming the source
   that lacks it.** Never 0, never a dash, never hidden when the source could have carried it, never a
   warning, never a claim about the package.
5. **A mode toggle is a 48dp chip with switch semantics and a filled/outlined glyph.** Selected takes
   the paired container. A toggle that stands alone carries a hairline: `outlineVariant` in Light,
   `outline` in Dark in both states.
6. **When the secondary figure's source differs from the answer's, the provenance line says so.** The
   implicit-subject sentence (`Online value, not checked …`) is used only when both figures come from
   the same record.
7. **The presentation reads a nutrient value from the product and never from its source.**
8. **The field wins over a secondary row.** A reading is withheld before the portion field loses its
   touch target.

### Components (names for the implementation plan)

- `ProteinToggle(checked, onCheckedChange, modifier)` in `ui/components/`. Chip anatomy from
  `JtcFilterChip`'s colours (that chip has no icon slot, so a sibling rather than a variant) with
  `toggleable(Role.Switch)`; leading glyph outlined or filled egg at 20dp; label from
  `R.string.protein_toggle`. `modifier` applied at the root. Used by Home only in this patch.
- A private `SecondaryReadingRow(label, value: SecondaryReading)` in `ProductScreen.kt`, where
  `SecondaryReading` is `Value(text)` or `Unavailable(text)`. Renders eyebrow + figure, or eyebrow +
  supporting text, with the fitting rules of section 4. Private until a second caller exists; the OCR
  patch reuses it in place. Named for the reading so it needs no rename then.
- `ProteinPresentation` (pure Kotlin, `domain/`): `of(enabled, product, exactProtein, hasAnswer) ->
  Hidden | Value(exact) | NoOnlineValue`. The data half of section 5's table lives in this one function
  and its tests. The two layout gates, `imeVisible` and the starved-window rule, are applied by the
  screen, which is the layer that knows them.
- No `ProteinCalculator`. The protein figure is `CarbCalculator.calculate(proteinPer100, portion,
  basis).exact`: one `movePointLeft(2)` shift in the app, applied to a second input, so agreement is
  structural rather than pinned by a sampled test.
- Settings row: the inline haptics-row pattern with a supporting `Text` (section 6); if a third boolean
  row ever appears, extract `SettingsSwitchRow(label, supporting?, checked, onCheckedChange)`.

### Strings (English only, the app's rule)

| Key | Text |
|---|---|
| `protein_toggle` | `Show protein` (chip label and Settings row label) |
| `home_action_barcode_subtitle_protein` | `Carbs, and protein when listed` |
| `settings_protein_body` | `Shows the protein in your portion under the carbs when Open Food Facts lists it. Carbs are unchanged.` |
| `product_protein_label` | `PROTEIN` (upper case from the string, like `CARBS`) |
| `product_protein_value` | `%1$s g` |
| `product_protein_no_online_value` | `No online value` |
| `product_protein_accessible` | `%1$s grams of protein` |
| `product_protein_no_online_value_accessible` | `Protein, no online value` |
| `result_accessible_grams_and_protein` | `%1$s grams of carbs, %2$s grams of protein` |
| `result_accessible_grams_protein_unavailable` | `%1$s grams of carbs, no online value for protein` |
| `product_result_verified_protein_online` | `Carbs checked against the package. Protein is an online value.` |
| `settings_safety_body` (changed, owner approval: gate row E3) | `This app calculates the carbohydrate content of a portion and, if you turn it on, shows the protein. It does not calculate insulin or any other medication, and it does not replace the information printed on the package. Always check the package if a value looks wrong.` |

Vocabulary: `Protein` joins the one-vocabulary list in PRODUCT.md. Never "macros", never "macro",
never "nutrients" in consumer copy. `No online value` is a fact about the record; `Not read from the
label` is reserved for the OCR patch if it needs one. No `stateDescription` strings: the platform
speaks the switch state.

## 9. Data model and the seam for OCR

Facts established from the code at `ead38ec` (file and line references are in the review record):

- Three parsers share `OffNutriments`: the product read, the legacy search (`OffSearchResponse`
  reuses `OffProduct`) and Search-a-licious (`nutriments: OffNutriments?`). All request the whole
  `nutriments` object, so `proteins_100g` arrives on the wire today and is dropped under
  `ignoreUnknownKeys = true`. **No field-list change**, so the pinned field-list tests stay. The shared
  `Json` sets `coerceInputValues = true`, which coerces nulls only, not type mismatches; any decode
  failure is reported as a malformed reply for the whole response.
- `Product` has no nutrient field beyond carbohydrate, and its provenance (`dataSource`) and
  verification are separate fields by owner decision. Room is at version 8; `MIGRATION_7_8` is the one
  unguarded additive ALTER; `MIGRATION_3_4` shows the `hasColumn` guard.
- `refreshFromRemote`: the refreshable branch (unverified, not user-authored) saves the fetched product
  wholesale; the not-refreshable branch copies `latestRemoteCarbs`, `latestRemoteBasis`,
  `remoteUpdatedAt` and image metadata only. `saveVerification` can change the product's basis
  (`VerifyDialog` offers the chips) and clears usage when it does; `resetToOnlineValue` moves the basis
  back to the remote one. The four wholesale reloads go through `repository.lookup`.
- `recalculate()` has four state-writing exits (no product, invalid count, `DirectCarbs`, result).

The additions, all nullable, all source-agnostic in the presentation:

1. `OffNutriments.proteins100g` read **tolerantly**: `@Serializable(with = LooseNumericText::class)
   val proteins100g: String?` (the serializer the DTO already uses for `product_quantity`, because Open
   Food Facts types numeric fields inconsistently). A string, an object, a null or garbage in that field
   parses to a value the validator refuses, and the product response never fails because of protein,
   on any of the three parsers. A strict `Double?` would make one malformed protein value a malformed
   lookup and a malformed search page, which contradicts the next item.
2. `NutritionValueValidator.validateProteinPer100(raw: String?, basis): BigDecimal?` with the
   carbohydrate function's rules (null, unparsable, NaN, infinite, negative, above the per-100 ceiling:
   100 g per 100 g, 200 per 100 ml, the same mass-fraction and density bounds), sharing its
   implementation; a test pins that the two agree. An invalid protein value becomes null, never an
   unusable product: protein can never block the carb answer.
3. `Product.proteinPer100: BigDecimal? = null` and `Product.proteinOrigin: ProductDataOrigin? =
   null`, with an `init` check that both are null or both set, so a figure without a source is
   unconstructible. KDoc: valid only under the product's own `basis`. Set by
   `OpenFoodFactsDataSource.toResult` with `OPEN_FOOD_FACTS`; left null by manual entry and the
   quick-calculation scratch product. The origin is written now, and only ever `OPEN_FOOD_FACTS` in
   this patch, because the OCR patch needs it on the day it writes `OCR`, and because owner decision 5
   (provenance is its own field) should hold for protein from the first version that stores it.
4. Room `products.proteinPer100 TEXT` and `products.proteinOrigin TEXT` (nullable, plain strings like
   `carbsPer100` and `dataSource`), version 9, `internal val MIGRATION_8_9` additive and guarded with
   `hasColumn` like `MIGRATION_3_4`; `9.json` appears on the first KSP build after the bump and is
   committed; a migration case in the existing class.
5. **Protein travels with the nutrient record and is never written on its own.** `lookup` stores it
   with the fetched product. `refreshFromRemote` is unchanged: the refreshable branch already saves the
   fetched product wholesale, so its protein arrives with its carbs under its basis; the not-refreshable
   branch (verified or user-authored) copies remote metadata only and does not touch protein, so a
   verified product keeps the protein of the record it was verified from and a manual product never
   receives an online figure. `RefreshOutcome` is unchanged: a protein-only difference reports
   `Unchanged` and raises no notice. `saveVerification` with a basis change also nulls `proteinPer100`
   and `proteinOrigin` (the usage-clearing helper is renamed to say so), because a per-100 g figure has
   no meaning under a millilitre portion; the dock then shows `No online value` until a wholesale reload
   brings the record back.
6. `AppSettings.proteinEnabled: Boolean = false`; DataStore key `protein_enabled`;
   `SettingsRepository.setProteinEnabled`; `SettingsViewModel.setProtein`. Home receives
   `onProteinChanged: (Boolean) -> Unit = {}` from the NavHost, which launches the same repository
   method the Settings screen uses (the precedent is `setHasSeenTutorial` on the Home scope). The
   Settings screen's new callback is defaulted `{}` as well, so the six instrumented classes that
   construct these screens still compile. `HomeViewModel` keeps not receiving settings. The "four
   values" KDocs in `Settings.kt` and `SettingsRepository.kt` are already stale at six and are
   corrected in passing.
7. `ProductUiState.exactProtein: BigDecimal? = null`, written on **every** exit of `recalculate()`:
   `CarbCalculator.calculate(proteinPer100, portion, basis).exact` when the product has a protein value
   and a gram portion resolved; null on the no-product, invalid-count and `DirectCarbs` exits. One state
   update per portion change carries both figures.
8. `ProductScreen` reads `settings.proteinEnabled`, the state, the IME and the frame's zone measurement
   to build the presentation and its two gates; `ResultPanel` renders the row.

Untouched: `MealItem`, `MealItemEntity`, `ProductSearchHit`, `ProductDao.observeSearchable` (an
explicit projection), `PortionUnit`, every calculator, formatter and OCR file, every field-list
constant, the manual-entry screen, the label scanner, the tutorial.

### The seam the OCR patch plugs into

- The only value the presentation reads is `Product.proteinPer100`. An OCR reading that establishes
  protein per 100 g/ml writes `proteinPer100` with `proteinOrigin = OCR` on the scratch product (and on
  the saved product when the user saves it), and every state in section 5 applies unchanged. The
  `No online value` wording is for `OPEN_FOOD_FACTS` origin; the OCR patch adds `Not read from the
  label` for its own, keyed on the origin the row already stores.
- The provenance sentence keys on `proteinOrigin` against the product's `dataSource` and
  `verificationStatus`; the verified case in section 5 is the first of its variants, and an OCR-read
  protein on an OCR-read carb value is the "same source" case that needs no new sentence.
- The refresh rule already protects the OCR patch: an OCR-origin product is user-authored and an
  OFF product with a label-verified figure is verified, so neither is refreshable and online protein
  can never overwrite a label-read one.
- The quick-calculation route gains an optional `protein` argument beside `carbs` and `basis`, carried
  only when the user confirmed it in the same tap as the carb value. The route exists; the argument does
  not yet.
- **The seam does not cover acceptance, and the OCR patch must decide it** (section 14). Three rules
  bind that decision: a protein the parser cannot read never blocks, delays or downgrades the carb
  proposal; protein is never offered through the recovery list or the assisted taps in its first patch;
  a protein row is never a carbohydrate candidate (the parser's exclusion terms already treat protein as
  an anchor to refuse, and stay so). Reading a protein row is new parser work, correctly out of scope
  here.
- The row is deliberately not named after protein or Open Food Facts.
- Nothing in this patch may add a protein field to the meal snapshot, the search hit, the recent card
  or the manual-entry form. Each of those is a separate decision with its own scope question.

## 10. Verification the implementation plan must include

JVM: the tolerant DTO (`OpenFoodFactsDataSourceTest`, `SearchALiciousDataSourceTest`,
`OpenFoodFactsSearchTest`): a numeric, a string, an object and a null `proteins_100g` beside valid
carbs all leave the lookup and the search page usable, and a reply without the field gives null;
`validateProteinPer100` mirrors the carb validator (null, NaN, negative, both ceilings) and agrees
with it; `Product` refuses a figure without an origin; `ProductRepository` stores protein and origin on
lookup, leaves them untouched in the not-refreshable branch, replaces them with the record in the
refreshable branch, nulls them on a verification that changes the basis and keeps them on one that does
not, returns `Unchanged` for a protein-only change, and never replaces the open session's product on a
refresh; `SettingsRepository` round-trips `protein_enabled` with a false default (one write per test,
the Windows DataStore trap); `ProteinPresentation` has one case per data row of section 5 with
negative controls for `DirectCarbs`, manual origin and a null value; `recalculate()` writes
`exactProtein` on every exit and nulls it on the `DirectCarbs` exit; the quick calculation's scratch
product has none; `ContrastTest` adds `onSecondaryContainer/secondaryContainer` in both schemes (4.5),
`onSurfaceVariant/surfaceContainerLow` in Light (4.5) and `outline/secondaryContainer` in Dark (3.0),
named for the chip; a schema assertion that `current_meal_items` at version 9 has no protein column.

Instrumented (run at 1080x2400/420 and at `wm size 320x640` / `wm density 160`, font scale reset to
1.0 first): the migration case v8 to v9 and a DAO round-trip; Home chip toggles the persisted setting
and reflects it; Settings row reflects Home; the tile subtitle and its merged description follow the
state; the chip meets 48dp in both states at 1.0x and 1.8x (`TouchTargetSizeTest`); the footer's
one-line and wrapped states; the dock shows the row with the value for the same portion, the
`No online value` state, no row when off, no row while the IME is open, the merged content description
and the three live-region descriptions; the traversal order of section 4; a five-digit protein figure
laid out without ellipsis at 320dp / 2.0x; a protein-on variant of
`theLargerProductImageLeavesThePortionFieldAndResultOnScreen` at 320x640/160 at 1.3x and 1.8x asserting
the field wholly reachable (clipped bounds equal size) with the row withheld, plus the negative control
with the gate removed, which must fail it; `DirectCarbResultScreenTest` gains "no row for `DirectCarbs`
with protein on"; Home, Search and Meal render no node carrying `product_protein_label` or
`product_protein_accessible`; no test literal for the existing `"N grams of carbs"` description changes
when protein is off (23 in `ProductScreenTest`, 43 across five classes).

Manual QA rows to add to `docs/manual-qa.md`: the chip on a physical device in Light and Dark, read at
arm's length (the egg-or-drop gate of section 3) and in greyscale; a product with protein, one without,
one verified; the 320x640 / 1.3x result state with protein on; the two overrides (app Light on a dark
phone and the reverse) for the chip's hairline.

## 11. Where protein deliberately does not appear

- **Search results** (Home inline and the Search screen): a hit is a candidate; nothing is calculated.
  The lookup after the tap carries protein to the product.
- **Recent and favourite cards**: the card's job is what, how much, carbs. A second figure on every
  card is the dashboard the app refuses to be; protein is one tap away. This is protected by test, not
  by schema: `proteinPer100` plus `lastPortion` would make a "last protein" derivable, so section 10
  pins that Home renders no protein node.
- **Meal bar, meal rows, meal total**: the meal is a carb scratchpad. No protein is stored per item, so
  no total can be shown, which is the structural guarantee (the same shape as "no meal id"). Deferred,
  not forgotten; see section 9.
- **Manual entry**: no protein field.
- **Label scanner, verification flows, the online-value-changed notice**: carbs only.
- **Identity row**: one per-100 fact.
- **Copy button, clipboard**: the carb figure only.
- **Tutorial and welcome carousel**: unchanged; they teach Scan, Portion, Carbs.
- **Splash, launcher, shortcuts, store screenshots**: unchanged; screenshots are taken with protein
  off, the default.

## 12. Adjacent changes and documents

Code comments that become untrue and must move with the change: `OpenFoodFactsDto.kt` ("the only
nutrient the app reads"), `ManualEntryScreen.kt` ("nothing about protein is asked for", still true but
worth restating why), `HomeScreen.kt` RecentCard KDoc ("no macros", still true), `Settings.kt` and
`SettingsRepository.kt` ("four values"), `SearchALiciousDataSourceTest.kt` field-purpose map
(`"nutriments" to "the carbohydrate figure"`).

Owner decisions this design needs. The first is a prerequisite for the implementation pass; the
others ship with the same release.

1. **`docs/MASTER-PROMPT.md` §2** stays verbatim and gains a dated block beneath it:
   > **Amendment (owner, date).** The exclusion above stands. "Tracking" means any nutrient figure
   > that is stored per use, summed across portions, shown outside the open calculator, or compared
   > with a target. An optional protein reading for the portion on screen, off by default, is permitted
   > on the calculator's result surface only; it is never totalled, remembered, listed on Home, in
   > search, in the meal or in the clipboard, and is never a substitute for or combined with the
   > carbohydrate figure. Any further nutrient is excluded.
2. **The regulatory assessment** (`docs/regulatory-qualification-assessment.md`, untracked, unsigned)
   describes a carbohydrate-only product. Before signature: a dated addendum to its §1 and §2 stating
   that the app can additionally show, off by default, the protein of the same portion, computed by the
   same arithmetic from the same product record, never totalled, stored per use or compared with a
   target; and a new §7.2 row, "adding any further nutrient (fat, energy, sugars) or presenting
   nutrients as a set: direct input to fat–protein-unit dosing methods", as a reassessment trigger.
   Protein alone breaches no §7.2 row; the addendum keeps the signed document true.
3. **The Health Apps declaration** (`docs/play-release-readiness.md` §4a,
   `docs/play-health-declaration.md`): the fact table gains "since 1.0.8 the app can also show, as an
   option that is off by default, the protein for the same portion; protein is never totalled, stored
   per use, or shown outside the open calculator", and the "toward the category" cell gains "shows a
   second nutrient (protein) per portion when enabled". C3 is re-decided with protein stated, both
   readings recorded; the category remains the owner's call.
4. **Store and product copy**, all in the same Play Console submission as the build, all bound by the
   assessment's §7.1 (the owner adopts the wording; the implementer does not publish it):
   - `docs/play-store-listing.md` short description: `Scan a barcode, enter your portion, read the
     carbohydrate grams.` Bullet: `It is not a diet tracker: no calories, no food diary, no daily
     totals, no goals. Protein for your portion can be shown as an option; it is never added up or
     recorded.` Closing line: `Just the Carbs gives you the carbohydrate number for your portion, and,
     only if you switch it on, the protein, and nothing more.`
   - `docs/known-limitations.md`: `It is not a food diary: no daily totals, no history beyond
     recents, no calories. Protein for the portion on screen can be shown as an option; it is never
     totalled, stored per use, or listed anywhere else.` and, under "Only total carbohydrate is used":
     `Protein, when shown, is a separate figure from the same source and is never combined with or
     substituted for the carbohydrate figure.`
   - `README.md` line 6: `It produces the carbohydrate figure for a portion, clearly, and, as an
     option, the protein for the same portion.`
   - `PRODUCT.md` purpose line: `read the carbohydrate grams (and, if switched on, the protein for the
     same portion). That is the entire product.` Vocabulary gains `Protein`.
   - The name tension is accepted and recorded: the name states the purpose, protein is an opt-in
     reading, and the "does not do" bullet says so.
5. **DESIGN.md** gains the rules of section 8 (a paragraph under "Component vocabulary" for the
   standing-alone hairline and one under "Typography and numerical hierarchy" for the second reading).
6. **Privacy policy and Data Safety**: the Data Safety form does not change (same request, same host,
   no new data type, health-info row stays "No"). The policy's stored-data line (`.md` and the live
   `.html`, which changes only when pushed, the owner's call) and the draft inventory in
   `docs/google-play-data-safety.md` each gain one clause: "carbohydrate values and, for Open Food
   Facts products, protein values".
7. **In-app safety text** (`settings_safety_body`, gate row E3): the wording in section 8, re-read
   against the gate.
8. **CHANGELOG 1.0.8, Added:** `**Protein, as an option.** Settings → Results → Show protein, or the
   Show protein chip on Home, shows the protein in your portion under the carbs when Open Food Facts
   lists a value for the product. Off unless you switch it on. It appears only on the calculator:
   nothing is added to the meal, Recents, search results or the clipboard, and no total exists. Carbs
   are calculated exactly as before.` **Play notes line:** `Optional protein: switch it on from Home or
   Settings to see the protein in your portion under the carbs. Off unless you turn it on; never added
   up or kept.` No purpose statement, no health claim.

## 13. Alternatives considered

- **Header mode chip** (wordmark row, beside the gear). Honest about being a global mode, zero
  height; rejected because it collides with the backdrop motif and the wordmark at 1.8x, and a control
  in the header reads as chrome, not as part of scanning.
- **Split primary tile** (a protein segment inside the scan tile). The most literal "scan modifier";
  rejected because it puts two touch targets inside the one merged button the tutorial and TalkBack
  rely on, and it attaches the mode to barcode scanning alone when search reads protein too.
- **A strip above the tiles.** Rejected: a third strip between search and the primary action on a
  screen that already carries a meal bar, and it pushes the primary tile down on every window.
- **Settings only, no Home control.** Every user who never wants protein would see no mode control on
  the one primary surface, and a thumb reaching *Enter manually* could not toggle a persistent mode by
  accident. It lost to the brief, which asks for a Home control, and to the consequence analysis: an
  accidental enable costs one self-labelled line in the dock and a subtitle change, undone by one tap,
  and a first-enable dialog would make a low-stakes toggle feel like a permission. Section 14 keeps the
  question open for the first physical-device review.
- **Protein in the supporting line, in the identity row, beside the numeral, as a ledge, in the
  supporting style**: see section 4.
- **Folding the pair into the whole-gram line on short windows** to save the row's height. On the
  one window that starves, the pair does not fit on that line at 1.3x either, so it wraps and saves
  nothing; on the reference phone at 1.8x it would change the row's shape for every large-text user.
  The starvation gate of section 4 is a single presentation with one rule.
- **A `latestRemoteProtein` field and a notice for protein-only changes.** Rejected: a secondary
  figure does not interrupt, and protein travelling with the record keeps the stored carbs and protein
  from one snapshot without a second mechanism.
- **Deferring `proteinOrigin` to the OCR patch.** One fewer column now, one more migration then, and a
  stored figure without provenance in between. Rejected for the reason owner decision 5 exists.
- **Reading `proteins_serving` for direct-carb units.** Possible later; would give counted portions
  without a weight a protein figure from the record's own serving value. Not now: it adds a second
  source of truth for the portion.

## 14. Open questions for the owner

1. Approve the §2 amendment (section 12, item 1); nothing is implemented before it.
2. Sign off the assessment addendum and the §7.2 row before signing the assessment (item 2), and
   re-decide the Health Apps declaration with protein stated (item 3).
3. Adopt the listing, README, known-limitations and PRODUCT.md wording (item 4), the safety text (item
   7), and the CHANGELOG and Play notes (item 8), or reword them within §7.1.
4. The starved window (section 4): keep the "field wins, row withheld" rule for this patch
   (recommended), or open a calculator pass to reserve the field's height before the identity floor,
   which would also cure today's 23dp sliver for carbs.
5. Keep the Home chip after the first physical-device review, or move the control to Settings only?
   Recommended: keep.
6. For the OCR patch: one Confirm accepting both figures when they are shown together, protein never
   blocking the carb proposal and never offered through recovery. Recommended: decide now and write it
   into the OCR patch's brief.
7. The glyph: egg at 20dp, with `EggAlt` as the gated fallback (section 3). Recommended: egg, decided
   on the device screenshot.

## 15. Review record

Eight independent reviews of the first draft, 2026-09-24: five lenses on this model (a person with
type 1 diabetes as the user, accessibility with computed contrast, implementation feasibility against
the code with file and line references, visual and brand against DESIGN.md, and a scope and regulatory
devil's advocate) and three on the opposite model through the Codex CLI (skeptic, architect,
minimalist; the synthesized verdict is in the session scratchpad). Findings adopted, with the section
they changed:

- Tolerant parsing of `proteins_100g` (three reviewers): section 9. A strict field would have let one
  malformed value fail a lookup and a search page.
- Protein only on Open Food Facts products, never attached to a user-authored product by a refresh,
  and never refreshed on its own; `proteinOrigin` stored from the first version (four reviewers):
  sections 0, 5, 9. The "manual product later received online protein" state and its string are gone.
- A verification that changes the basis drops the stored protein (feasibility): section 9.
- The starved window: "the field wins" (user advocate, accessibility, feasibility): section 4.
- The row after the provenance line, not before it (visual): section 4.
- Chip at 48dp with a hairline in both themes when off and in Dark when on, ink label, 20dp glyph,
  `Show protein` label, one 220ms beat, no haptic (visual, accessibility, user advocate, minimalist):
  sections 2, 3, 8.
- `No online value` with its accessible forms and a not-listed live-region description
  (accessibility, scope, user advocate): sections 4, 8.
- Tile subtitle `Carbs, and protein when listed`; Settings copy naming Open Food Facts (scope, user
  advocate, visual): sections 2, 6.
- The footer's real cost at 1.8x and the `LazyColumn` item change (accessibility, feasibility,
  minimalist): sections 2, 7.
- One formula: no `ProteinCalculator` (minimalist, feasibility); the row private until a second caller
  (minimalist): section 8.
- The §2 amendment as a prerequisite and as a dated block, the assessment addendum, the Health Apps
  re-decision, the listing and doc wording, the privacy one-liners, the safety text, the CHANGELOG and
  Play notes, screenshots with protein off, the OCR acceptance rules (scope, architect): sections 9,
  12, 14.
- Fitting rules for wide figures, the scaled row cost, the corrected spacing measurements, the BOM
  icon fact, `ContrastTest` pins, the Settings row written out, defaulted callbacks, every exit of
  `recalculate()`, the `FilterChip` reason (skeptic, feasibility, accessibility): sections 2, 4, 6, 7,
  9, 10.

Findings considered and not adopted, with the reason: the protein figure in the supporting style
(section 4); the inline fold on short windows (section 13); a plural provenance sentence ("Online
values …") when both figures are online (no new string; the sentence captions the answer and the row is
self-labelled after it); the minimalist's cut of `ProteinPresentation` (its value is a testable state
table, not reuse); changing the frame's reserve order in this patch (section 14).
