# Just the Carbs — design system

> This describes the implemented UI. `Theme.kt` and `AccentPalette.kt` remain authoritative when
> code and prose differ.

## Product scene and visual direction

The app is for someone holding food in one hand under imperfect kitchen or shop lighting who needs
a trustworthy carbohydrate answer in seconds. The interface is a **warm editorial utility**: paper
and ink surfaces, decisive cobalt interaction, a tomato-red answer, solid task tiles, and a small
barcode/nutrition-rule motif. It should feel authored and energetic without becoming a food diary.

Three hierarchy rules govern every screen:

1. The current task and next action are obvious at a glance.
2. Provenance, verification, and ambiguity stay explicit but visually secondary.
3. A calculated carbohydrate result is the dominant object through scale, placement, whitespace,
   contrast, and reserved colour — not through colour luminance alone.

Visual variance is intentionally high (7/10), information density is compact but breathable
(6/10), and motion is restrained (3/10). Decorative gradients, generic hero circles, glass effects,
dashboard grids, excessive pills, and stacks of interchangeable rounded cards are outside the
system.

## Colour roles

Dynamic colour is not used; wallpaper colour must not change the hierarchy.

| Role | Light | Dark | Use |
|---|---:|---:|---|
| Primary cobalt | `#2856C5` | `#82A2FF` | Primary action, links, selected state |
| Primary container | `#E6ECFF` | `#263454` | Selected and supporting interaction surfaces |
| Result tomato | `#C13C2D` | `#FF8A75` | Confirmed carbohydrate figures only |
| Tertiary orange | `#F4A261` | `#FFC078` | Warm information and scanner context |
| Page | `#F7F2E8` | `#111318` | Root ground |
| Primary ink | `#191B23` | `#F3F0E8` | Main text |
| Muted ink | `#61616C` | `#B7B2A8` | Supporting text |
| Divider | `#DED8CB` | `#353943` | Quiet structure |

Destination accents identify areas but never carry meaning alone: teal for label/manual work,
indigo for search, amber for meal, violet for favourites, and neutral ink for settings. The light
green is `#147C3B`; the other exact accent values live in `AccentPalette.kt`.

Result red is not the error colour. Unverified data uses a worded warm badge. Scanner guidance uses
paired light and dark edges because no single colour survives every package image. Loaded product
photos use a near-white media plate in both schemes so white-background photography never appears
inside a black frame.

`ContrastTest` pins text/background pairs at the normal-text floor, including prominent large text.

## Typography and numerical hierarchy

Space Grotesk Medium/SemiBold/Bold is bundled for headings, actions, labels, and numbers; body copy
uses the platform sans. The compact scale is deliberate:

- Page heading: 32sp/36sp.
- Section/card title: 18–24sp with tight line height.
- Body: 17sp/24sp; supporting body: 15sp/22sp.
- Editable portion: 52sp/58sp bold.
- Confirmed result: 72sp/76sp bold, tightened tracking, auto-sizing down instead of clipping.

The label and unit remain readable when a result auto-sizes. A scanner proposal awaiting user
confirmation must not use confirmed-result styling.

A calculated result renders its dominant number and its unit as two related but distinct text
styles rather than one string — the number is the answer, the unit is a label on it — sharing one
coherent accessible node so a screen reader still reports them together. **They share a baseline**:
both are aligned with `alignByBaseline()` and both trim their line height, because two text boxes
can only sit on one baseline when neither reserves leading the other does not. Aligning their boxes
instead dropped the unit below the numeral as a subscript, which is the app’s most-looked-at pair
of glyphs rendered wrong. The same shape scales down
for smaller previews, such as a search row's trailing figure, provided the styling never borrows
confirmed-result colour for a value nothing has confirmed yet. Space Grotesk's bundled instance does
not currently expose tabular figures, so digits in this shape may shift width slightly during a
cross-fade; that is a font limitation to revisit if a tabular-figure build becomes available, not a
missed design requirement.

## Spacing, shape, and surfaces

Spacing uses the 4/8/16/24/32/48dp scale. Screens use a 20dp horizontal edge. Touch targets are at
least 48dp and primary text actions use a 56dp minimum, never a fixed text-bearing height.

Rounding is restrained: precise rather than bubbly, and one step down across the board from the
original handoff figures.

- Cards: 16dp radius, normally tonal or bordered rather than elevated.
- Buttons and fields: 12dp radius.
- Media: 12dp radius.
- Icon plates: 10dp radius.
- Sheets/result docks: 24dp top radius.
- Chips: pill-shaped only when the affordance is genuinely a compact chip.
- Fully round (`CircleShape`) is reserved for the scanner scrim buttons, which float over a camera
  preview and are the only place it appears.
- Result dock: 6dp elevation; ordinary cards: flat.

Warm surface steps separate page, field, card, and modal layers. The result dock is the one ordinary
content surface allowed a meaningful shadow because its separation is functional. **Fewer edges** is
the governing rule: no box inside a box, no border around every control, no decorative outline, and
no second elevated surface competing with the dock. A field at rest is a quiet fill with no border
at all; the border appears on focus. **One exception, deliberate:** the calculator's number-entry
frame (the portion and count fields) carries a 1dp `outline` hairline at rest, because its value is
shown at headline size directly above another headline number and the fill alone measured 1.05:1
against the page: a remembered portion read as a readout, not an input. The hairline is the "this
is a field" cue the 17sp fields never needed; focus still promotes it to the 2dp primary stroke.

A raised surface moves *away* from the page’s own luminance, which means `surfaceContainerLowest`
in Light and `surfaceContainerHigh` in Dark. Modals follow that rule rather than one fixed token,
and so do the two answer docks (the calculator's result and the meal's total) through the single
`resultDock` token: in Dark the dock is a raised graphite slab, never a surface darker than the page.

The calculator reads as one column: `PORTION` over the input, `CARBS` over the answer, the same
`labelSmall` eyebrow on both so the two figures present as a labelled pair. The input is 48sp
SemiBold ink, left-aligned with its unit on its baseline, in the hairlined frame; the answer is 72sp
Bold in the result colour on the elevated dock. They differ by kind, weight, scale and hue at once,
so the pairing survives dark mode, monochrome and large font scales. The portion group rests
directly on the dock, and the slack of a tall screen collects under the identity header.

The decorative motif — a compact 92×96dp set of unequal nutrition bars — is drawn on **Home only**,
over an opaque page. It was previously painted on every root screen, where it collided with the
trailing controls at the bottom of the content; the other screens keep their `DestinationMarker`,
which is the same motif at top-bar scale and is identity rather than decoration.

## Component vocabulary

- `JtcTopBar`: flexible 64dp minimum, ordinary-ink navigation, one-line title, and compact
  nutrition-bar destination marker. Product keeps a two-line intrinsic-height variant.
- Home task tile: one solid `primaryTile` card for barcode scanning and one quieter hairline-outlined
  card for label scanning, each with a square 40dp icon plate. No decorative gradients, no accent
  shadow, and no accent-tinted border — the destination accent appears only inside the icon plate.
  `primaryTile` is a separate token from `colorScheme.primary` because in Dark the latter is pale
  enough that an 88dp tile of it would outrank the carbohydrate result.
- `JtcOutlinedButton` (48dp, primary label and border), `JtcValueButton` (40dp, quiet fill, no
  border) and `PrimaryAction` (56dp) are the three button roles; controls migrate by role rather
  than uniformly.
- Scanner chrome is shared between both cameras: circular scrim buttons, no title band over the
  preview, and one dark bottom dock carrying the task, the guidance line and the actions.
- `SearchResultRow`: a 76dp minimum row with product identity on the left and carb summary aligned
  on the right; the full row is the touch target. Its figure is ordinary ink, not the result colour
  — a search hit is a candidate, and nothing about it has been calculated yet.
- `SourceBadge`: compact worded provenance, never colour-only.
- Settings segment: full-width radio semantics, equal options, clear selected container; it replaces
  a loose collection of oversized filter pills.
- `RecoveryPanel`: every failure state provides plain language and at least one next action.
- Scanner chrome: black translucent top/bottom docks and light controls over live photography;
  themed raised chrome around frozen review states.
- Dialogs: shared tonal surface, subtle border, standard title/body hierarchy, and visible context
  behind the scrim.

States must be visibly distinct: pressed/selected uses the paired container, disabled uses dedicated
foreground and background roles, progress stays attached to the action that caused it, empty states
are compact and instructive, and errors include recovery. Text fields own their background and
focused/unfocused borders instead of falling back to unrelated Material defaults.

## Screen composition

Home opens with search, then the two highest-value scan actions, then a compact three-step rhythm
and recent items. It does not imitate a dashboard and does not reserve a large empty hero area. Its
primary scan action's copy acknowledges an in-progress meal rather than always reading as a first
scan, so returning to Home mid-meal does not misstate what the next tap continues.

Search prioritises typing and scanning. Loading, empty, offline, retry, and fallback routes remain
close to the query. Rows are dense enough to compare quickly without shrinking touch targets. Each
row's trailing nutrition figure sits in a reserved-width column, value over basis, so a list of
results compares down the page the way a price column would; a hit with no established basis shows
a quiet no-value state in the same column rather than a blank space or an invented unit.

Product/calculator is the hero experience: product identity and provenance lead into a prominent
portion input and a left-aligned answer dock. The answer remains stable and legible while changing;
verification and recovery never silently replace its inputs.

Barcode and nutrition-label scanning deliberately share image-relative chrome but communicate
different tasks. Live capture, frozen review, ambiguity, conflict, no-result, and permission states
must never blur into one another.

Onboarding teaches Scan → Portion → Carbs with one focused idea per page. Its richer illustration
and gradient treatments are an intentional contained exception, not a pattern for utility screens.

## Dark mode, motion, and accessibility

Dark mode uses graphite layers rather than inverted cream. Primary and result colours are retuned
for dark surfaces; dividers remain visible, disabled controls remain recognisable, and camera chrome
continues to work over unpredictable imagery.

Motion uses 120ms quick and 220ms standard timings. It clarifies state change without delaying the
answer: result digits cross-fade, page teaching transitions remain short, and no utility screen has
decorative entrance choreography. Existing haptics stay reserved for meaningful capture,
confirmation, or failure events.

A successful action confirms itself by changing its own control's state — an icon, a label, a brief
held glow — rather than by requiring a separate toast or dialog the user might not be looking at.
The confirmation holds long enough to survive a glance away and back, and a fresh attempt of the
same action restarts the hold rather than leaving a stale one to finish on its own; a failed attempt
never shows this state. The two scanners narrate their own state changes — the moment a shutter
commits, the moment a reading result replaces live guidance — as brief, purely visual transitions
that never delay the underlying capture or recognition they describe. Navigation between screens
uses a short, uniform fade rather than directional slides or scale choreography, so moving through
the app never competes with the content the user navigated to see.

All text-bearing controls grow rather than clip. Long product names ellipsise or wrap according to
context. The result is a polite live region, composite rows expose one coherent accessibility node,
and colour is always accompanied by text, iconography, geometry, or position.

## Change discipline

UI changes must preserve exact arithmetic, nutrition basis, provenance, verification, observation
identity, cancellation, scanner/OCR safety, and session immutability. Visual tests should protect
the current hierarchy and semantics rather than freeze obsolete pixels. Verify representative light,
dark, loading, empty, error, permission, keyboard, and large-font states in proportion to the change.
