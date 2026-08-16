# Home entry points — design (2026-08-15)

Scope: the Home screen's entry-point presentation only. No calculation, OCR, Room, OFF, scanner or
regulatory change.

## The problem, as actually found in the code

The brief assumed direct Home → nutrition-label scanning did not exist. It does.
`JustTheCarbsNavHost` already passes `onScanLabel = { navController.navigate(Routes.labelScan()) }`
to `HomeScreen`, and the no-context path is complete: an empty barcode makes `productExistsCheck`
false, which selects `onCarryPendingPortionUnit` over `onSavePortionUnit`, so an accepted reading
routes to `Routes.manual("", carbs, basis)` and a detected countable portion travels as typed
navigation arguments into the same creation flow used when a barcode lookup misses.

The real defect is narrower. The label action is rendered **only inside `EmptyState`**, which sits in
the `else if (recents.isEmpty())` branch. After the user's first scan, `recents` is non-empty and the
third entry point disappears permanently. It was also an 18dp-icon `TextButton` — the visual weight
of a footnote, next to a 64dp filled primary button.

Three secondary problems:

1. The search field's only label is the placeholder `search_hint` ("Product or brand name"). The word
   "Search" appears nowhere on Home; discovery rests on recognising a magnifier glyph.
2. The barcode action and the label action share no visual language, so they do not read as two ways
   of doing one thing.
3. `EmptyStateStepStrip` is a fixed-width `Row`; it clips below roughly 340dp of usable width at
   1.8x font scale.

## Decision: where the primary action lives

The brief asks for the primary scan action to be in comfortable thumb reach **and** for core actions
to sit above recent products. On a phone these conflict, because recents scroll and anything above
them scrolls away.

Resolution: **both scanner cards move into the scrollable region, above recents; only *Enter
manually* stays pinned at the bottom.** This changes today's arrangement, where the barcode button is
pinned.

Reasoning: at the top of the scroll region the cards sit roughly 220-300dp from the top edge — within
thumb reach and above the fold on a short display — and this is the only arrangement in which the two
scanner actions can read as a matched pair while still preceding history. Pinning both scanners
instead would park about 180dp of permanent chrome over the recents list, which is the dashboard
feeling the app must not have (§2, §22).

## Composition

```
Just the Carbs                    [gear]
[search icon  Search products            ]
--------------- meal bar, if any ---------
| scrollable                             |
|  +-----------------------------------+ |
|  | [barcode]  Scan barcode        -> | |   filled primary, ~88dp
|  |            Fastest way to find a  | |
|  |            packaged product       | |
|  +-----------------------------------+ |
|  +- - - - - - - - - - - - - - - - - -+ |
|  | [table]    Scan nutrition label -> | |   outlined secondary
|  |            Read carbs from the     | |
|  |            package                 | |
|  +- - - - - - - - - - - - - - - - - -+ |
|                                        |
|  RECENT   (or brand hero + step strip) |
|  ...                                   |
------------------------------------------
[            Enter manually             ]   pinned tertiary
```

Hierarchy: SEARCH -> PRIMARY BARCODE -> NUTRITION LABEL -> history -> manual entry.

## Components

One new private `HomeActionCard` in `HomeScreen.kt`, two instances differing by a `filled: Boolean`.

- Filled: `primary` / `onPrimary`, keeping the existing 12dp blue-tinted shadow from today's scan
  button, so the primary action loses none of its weight in the move.
- Outlined: `surfaceContainerLowest` with a 1dp `outlineVariant` border — identical to `RecentCard`,
  so the two scanner cards and the history below them are visibly one system. Its icon roundel uses
  `tertiaryContainer`/`onTertiaryContainer` (the existing orange), which is how the two camera
  actions stay distinguishable at a glance without introducing a colour.

No new palette. No new drawable assets. `Space` tokens throughout. `extendedColors.result` (red)
stays reserved for carbohydrate results and is not used here.

Icons, both already imported by this file: `Icons.Filled.QrCodeScanner` for barcode,
`Icons.Filled.DocumentScanner` for the label — a document/table scan rather than a second camera
glyph.

## Empty state

Keep the hero mark, the "Scan. Portion. Carbs." headline and the Scan -> Portion -> Carbs strip.
Two changes:

- Drop its trailing "Scan nutrition label" `TextButton`. That action is now a first-class card above,
  and leaving both would offer the same route twice on one screen.
- Wrap the step strip in `horizontalScroll` so it degrades instead of clipping on a narrow display at
  large font scale.

The hero now renders **below** the action cards: with the cards present, they are the answer to "what
can I do here", and the hero is reassurance rather than instruction.

## Behaviour preserved exactly

- Search stays explicit: typing calls `onSearchQueryChanged` only; `ImeAction.Search` or the leading
  icon calls `onSearchSubmit`. No search-as-you-type.
- A non-blank query still replaces the middle region with results, and still hides the meal bar.
- All navigation reuses the existing `onScan`, `onScanLabel`, `onManualEntry`, `onSearch*` callbacks
  and the existing routes. No new route, no parallel navigation.

## Accessibility

Each card carries one merged semantics node: `role = Role.Button` and a `contentDescription` of
"<title>. <subtitle>". Icons and chevrons are `contentDescription = null`. This yields one TalkBack
announcement per card rather than three, and keeps both cards discoverable by role.

## Tests

Added to the instrumented `HomeScreenTest`:

- all three entry points visible with recents present — the actual regression;
- all three visible in the empty state;
- each card invokes the correct callback exactly once;
- typing still does not submit; the IME action still submits exactly once;
- button semantics on both cards;
- 1.8x font scale keeps every entry point reachable.

A JVM test is not possible for any of this: it is Compose UI, and the project does not use
Robolectric (owner decision 7).
