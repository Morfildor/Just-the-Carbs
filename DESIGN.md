# Just the Carbs — design system

> Transcribed from the implemented source of truth: `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt`
> and `docs/design_handoff_just_the_carbs/README.md`. **Theme.kt is authoritative** — if this file
> and the code disagree, the code is right and this file is stale.

## Colour strategy

**Colourful chrome, protected result** (2026-09-04). Colour is spent freely on navigation,
actions, sections and empty states. The carbohydrate result keeps its exclusive red and its
prominence on the calculator.

This section previously said the palette was near-monochrome **because** "a colourful interface
competes with [the result]". That rationale is withdrawn: the app is now colourful and the result
is still the loudest thing on the calculator, because the guarantee moved from *the absence of
colour elsewhere* to **arithmetic**.

### The luminance rule — what protects the result now

Result red `#D42F2F` has WCAG relative luminance **0.162**. Every destination accent is *darker*,
so it recedes behind the carbohydrate figure rather than competing with it:

```
teal 0.142 · green 0.159 · magenta 0.124 · violet 0.098 · amber 0.098 · indigo 0.083
```

`AccentRecessionTest` asserts this over the whole palette in both schemes, so a new accent that
breaks it **fails the build**. `ContrastTest` separately pins every accent at ≥4.5:1 on every
surface it is drawn on. Neither is a style preference; both are computed from the live tokens.

**A worked example of why the rule is a test and not a paragraph.** The dark accents were first
drafted as ordinary bright tints (`#5EEAD4`, `#C4B5FD`, `#86EFAC` …) — the values any dark theme
reaches for. Computed, **all six failed**: `#5EEAD4` measures 0.660 against the dark result red's
0.366, nearly twice as bright as the number it must not out-shout. Nothing about those swatches
looked wrong.

### Core roles

| Role | Light | Dark | Spent on |
|---|---|---|---|
| Blue (primary) | `#1B6FBF` | `#5CA6E8` | Every interactive control, active state, favourite |
| Blue soft | `#E4F1FC` | `#16324A` | Accent tint, selected chips, verified badge |
| **Red (result)** | `#D42F2F` | `#FF7A7A` | **Exactly one thing per screen: the carb number** |
| Orange (tertiary) | `#FFA94D` | `#FFB868` | Soft informational surfaces, label-scan accent |
| Orange soft | `#FFEEDC` | `#4A3418` | Safety card, unverified-source badge |
| Cream (background) | `#FFF6EE` | `#15140F` | Page ground |
| Ink | `#181A1E` | `#F2EFE8` | Primary text |
| Ink muted | `#6B6A72` | `#AFAEA8` | Secondary text |
| Line | `#E4DFD3` | `#39372F` | Borders, dividers |

Blue and red are the *measured* values `Theme.kt` ships, not the original handoff tokens
(`#2F8FE0` / `#FF5C5C`), which scored 3.43:1 and 2.84:1 on the surfaces they are actually drawn on
— the result failing even the 3:1 large-text floor. This table listed the handoff values until
2026-09-04; it was stale, and `ContrastTest`'s `light tokens match the values Theme kt actually
ships` exists to stop the *code* drifting the same way.

### Destination accents (`AccentPalette.kt`)

| Accent | Light | Dark | Spent on |
|---|---|---|---|
| Teal | `#0F766E` | `#43B1A6` | Label scanner, manual entry |
| Violet | `#6D28D9` | `#A997D3` | Favourites |
| Green | `#15803D` | `#45B56E` | Label-scan gradient tail |
| Magenta | `#BE185D` | `#E481B3` | Reserved for list variety |
| Indigo | `#4338CA` | `#9496FF` | Search, barcode-scan gradient tail |
| Amber | `#92400E` | `#D49425` | Meal |

Settings deliberately takes a **neutral**, not an accent: it is pure configuration and giving it a
hue would imply it belongs to the scan → portion → carbs workflow the other colours mark out.

### Where colour is never spent

- **On or behind the result number.** The result panel stays `surfaceContainerLowest`.
- **On card grounds.** Colour goes on the 4dp accent spine and the icon roundel, so product names
  keep full contrast against white.
- **As the sole carrier of meaning.** The `CARBS` label is text, the favourite marker is a star,
  provenance stays worded. Unchanged rule, and it binds the new palette identically.

`error` is a genuine fault state and is **not** the result red — the result is not an error.

Dynamic colour is deliberately unused: it would hand the accent, and therefore the visual weight of
the result, to the user's wallpaper.

## Typography

- **Space Grotesk** (variable, weights 500/600/700) — headlines, numbers, buttons, labels.
- **Roboto** (Material default) — body copy, supporting text, form input.

Two numbers get sizes nothing else competes with (`NumberType`):

- `result` — 64sp Bold, letter-spacing −2, auto-sizing down to 36sp so a long figure **shrinks
  rather than clips**. A result that silently loses digits while still looking finished is this
  screen's worst failure.
- `portion` — 48sp Bold, letter-spacing −1.5.
- `supporting` — 15sp Normal. Legible, not a whisper.

## Spacing, radius, targets (`Space`)

`xs 4` · `s 8` · `m 16` · `l 24` · `xl 32` · `xxl 48` (dp)

- `screenEdge` 20dp — standard horizontal margin, every screen.
- `cardRadius` 18dp · `buttonRadius` 16dp · `mediaRadius` 16dp · `chipRadius` 999dp (pills stay pills).
- `sheetTopRadius` 32dp — the pinned result/total surface.
- `minTouchTarget` 48dp — never below, §39.
- `thumbnail` 52dp.
- `resultElevation` 3dp; `cardElevation` 0dp — cards are defined by border + surface tone, not shadow.

## Surfaces

Cards are outlined (`surfaceContainerLowest` + 1dp `outlineVariant`), not elevated. The **only**
elevated surface is the pinned result panel, whose shadow is load-bearing: without it the panel was
~1% different from the page and the most important element on screen had no edge at all.

The same pinned surface treatment carries the calculator result and the meal total, so the same kind
of number appears in the same place and the app reads as one thing.

## Motion (`Motion`)

`QUICK_MS 120` · `STANDARD_MS 220`. Short and unshowy — this app is used standing in a kitchen, and
animation that delays a number makes the app worse. The result cross-fades on digit change only.
No decorative entrance animation anywhere.

## Component vocabulary

- Primary action: filled `Button`, 56dp, `buttonRadius`.
- Secondary action: `TextButton`, full width, 48dp.
- Recovery from any failure: `RecoveryPanel` (title + body + at least one action). A message with no
  way forward is a dead end and is forbidden.
- Selection: `FilterChip`, pill-shaped.
- Provenance: `SourceBadge` — always carries meaning in words, never colour alone.

## Accessibility floor

### Tutorial presentation exception (owner, 2026-09-10)

The optional tutorial uses a stable centered teaching stage, a maximum 320dp column, and no card or
identifiable text background. Headlines use Space Grotesk SemiBold at 30sp/34sp (24sp/28sp at large
font scale); tutorial body copy uses Space Grotesk Medium at 16.5sp/22sp. Theme foreground colours
retain readable contrast. A light contextual scrim, one-dp tonal target edge, broad semantic bloom,
and optional collision-aware pointer establish context, focus, and explanation. These choices apply
only to the tutorial. Its measured synthetic previews reframe around the stage; real app screens
retain their typography and layout. Pointer, bloom, scrim, edge, and visual progress remain excluded
from accessibility.

START is the sole orientation treatment: one broad, borderless blue lift relates Search, Barcode,
and Nutrition Label while leaving the Scan / Portion / Carbs rhythm as context. Targeted chapters use
either a safe direct retarget or a short release/acquire selected from measured overlap and aspect
ratio. The displayed chapter is atomic across preview, copy, progress, accent, pointer metadata, and
advance semantics; only focus visibility and the short fade-through animate around that switch.

- 48dp minimum touch target, enforced by `Space.minTouchTarget`.
- The result is a polite live region so TalkBack reads the new value as the portion changes.
- Composite tappable rows merge their descendants into one node with one description, rather than
  leaving a screen-reader user to reassemble fragments.
- Never colour alone for meaning.
- Text wraps and controls grow (`heightIn`) rather than clipping at large font scale.
