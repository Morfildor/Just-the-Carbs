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

## Spacing, shape, and surfaces

Spacing uses the 4/8/16/24/32/48dp scale. Screens use a 20dp horizontal edge. Touch targets are at
least 48dp and primary text actions use a 56dp minimum, never a fixed text-bearing height.

- Cards: 22dp radius, normally tonal or bordered rather than elevated.
- Buttons and fields: 14dp radius.
- Media: 20dp radius.
- Sheets/result docks: 28dp top radius.
- Chips: pill-shaped only when the affordance is genuinely a compact chip.
- Result dock: 6dp elevation; ordinary cards: flat.

Warm surface steps separate page, field, card, and modal layers. The result dock is the one ordinary
content surface allowed a meaningful shadow because its separation is functional. Root screens
paint an opaque page before the decorative motif; the motif is a compact 92×96dp set of unequal
nutrition bars, not a full-page tint.

## Component vocabulary

- `JtcTopBar`: flexible 64dp minimum, ordinary-ink navigation, one-line title, and compact
  nutrition-bar destination marker. Product keeps a two-line intrinsic-height variant.
- Home task tile: one solid cobalt primary tile for barcode scanning and one quieter outlined teal
  tile for label scanning. No decorative gradients.
- `SearchResultRow`: a 76dp minimum row with product identity on the left and carb summary aligned
  on the right; the full row is the touch target.
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
and recent items. It does not imitate a dashboard and does not reserve a large empty hero area.

Product/calculator is the hero experience: product identity and provenance lead into a prominent
portion input and a left-aligned answer dock. The answer remains stable and legible while changing;
verification and recovery never silently replace its inputs.

Search prioritises typing and scanning. Loading, empty, offline, retry, and fallback routes remain
close to the query. Rows are dense enough to compare quickly without shrinking touch targets.

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

All text-bearing controls grow rather than clip. Long product names ellipsise or wrap according to
context. The result is a polite live region, composite rows expose one coherent accessibility node,
and colour is always accompanied by text, iconography, geometry, or position.

## Change discipline

UI changes must preserve exact arithmetic, nutrition basis, provenance, verification, observation
identity, cancellation, scanner/OCR safety, and session immutability. Visual tests should protect
the current hierarchy and semantics rather than freeze obsolete pixels. Verify representative light,
dark, loading, empty, error, permission, keyboard, and large-font states in proportion to the change.
