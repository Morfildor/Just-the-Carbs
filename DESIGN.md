# Just the Carbs — design system

> Transcribed from the implemented source of truth: `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt`
> and `docs/design_handoff_just_the_carbs/README.md`. **Theme.kt is authoritative** — if this file
> and the code disagree, the code is right and this file is stale.

## Colour strategy

**Restrained**, deliberately. The palette is close to monochrome over a warm cream base with a
single blue accent, because the calculator's result must be the loudest thing on screen and a
colourful interface competes with it.

Roles are separated so nothing competes with the result:

| Role | Light | Dark | Spent on |
|---|---|---|---|
| Blue (primary) | `#2F8FE0` | `#5CA6E8` | Every interactive control, active state, favourite |
| Blue soft | `#E4F1FC` | `#16324A` | Accent tint, selected chips, verified badge |
| **Red (result)** | `#FF5C5C` | `#FF7A7A` | **Exactly one thing per screen: the carb number** |
| Orange (tertiary) | `#FFA94D` | `#FFB868` | Soft informational surfaces, label-scan accent |
| Orange soft | `#FFEEDC` | `#4A3418` | Safety card, unverified-source badge |
| Cream (background) | `#FFF6EE` | `#15140F` | Page ground |
| Ink | `#181A1E` | `#F2EFE8` | Primary text |
| Ink muted | `#6B6A72` | `#AFAEA8` | Secondary text |
| Line | `#E4DFD3` | `#39372F` | Borders, dividers |

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

- 48dp minimum touch target, enforced by `Space.minTouchTarget`.
- The result is a polite live region so TalkBack reads the new value as the portion changes.
- Composite tappable rows merge their descendants into one node with one description, rather than
  leaving a screen-reader user to reassemble fragments.
- Never colour alone for meaning.
- Text wraps and controls grow (`heightIn`) rather than clipping at large font scale.
