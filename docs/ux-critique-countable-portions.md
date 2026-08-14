# UX critique — countable portions (2026-08-14)

A §69-style critique, run against the actual implementation, not the mockup. The scenario from the
brief: **the user has a bag of sliced bread in one hand and their phone in the other.**

This document records what was checked, what was found, and what changed as a result — including
one thing found only by actually driving the screen through instrumented tests, not by reading the
code.

---

## Can they scan and enter "2 slices" without thinking about grams?

**Yes, for the golden path.** Scan → product opens → tap **Slices** → type `2` → result. At no
point does the user type, see, or need to understand the number 72 to get an answer — the
`72 g` intermediate is displayed as supporting information, not requested as input.

**One rough edge, found by testing the actual screen rather than reading the code:** the count
field pre-fills `1` the first time a countable unit is selected (mirroring the existing app's
convention of pre-filling the last-used amount). A user who wants `2` and taps straight into the
field and types `2` — without first clearing the `1` — gets `12`, because Android text fields don't
select-all on focus by default and neither does this one. This is not hypothetical: it is exactly
the mistake the first version of this feature's own automated test made, which is a reasonable
proxy for what an unfamiliar user's thumb will do too.

*Verdict: acceptable but worth watching.* The existing grams field has the same property in
principle, but starts **empty**, so there is nothing to accidentally append to. The countable field
starting at `1` (a plausible real value, not a placeholder) makes the mistake more likely than the
grams field's blank start does. Not fixed in this pass — matching the existing field's established
convention was judged more valuable than a one-off inconsistency — but flagged here rather than
silently accepted, and a candidate for a follow-up: either start the count field blank with `1` as
placeholder text, or select-all on focus.

## Is switching units obvious?

**Yes.** Two chips, `Grams` / `Slices`, directly under "How much are you eating?" — the same visual
language (`FilterChip`) used nowhere else on this screen, so it doesn't compete with existing
buttons. Selection state is a filled chip, matching Material's standard affordance.

Switching **does not** clear or reset anything: grams → slices → grams round-trips through the same
resolved gram amount, verified by test (`switchingFromSlicesBackToGramsKeepsTheEquivalentGramAmount`,
`switchingBackToSlicesRestoresTheSameCount`). A user who taps the wrong chip loses nothing.

## Is the per-slice weight visible enough to understand where the calculation came from?

**Yes.** `2 slices × 36 g = 72 g` sits directly beneath the count field, in body-small text —
legible, not a tooltip or a hidden "details" affordance the user has to find. This is the single
piece of UI that answers "why did I get this number," and it's on-screen by default whenever a
countable unit is selected, not gated behind a tap.

## Is the result still visually dominant?

**Yes, unchanged.** `ResultPanel` — the decimal-dominant `30.2 g` — was not touched by this feature.
It remains pinned to the bottom of the screen, above the keyboard, exactly as before. Nothing about
countable portions competes with it for visual weight; the equation text is deliberately
`bodySmall`/`onSurfaceVariant`, the same treatment already used for supporting information
elsewhere on this screen (e.g. the whole-gram line beneath the decimal).

## Is the screen becoming cluttered?

**Conditionally yes, and this is the critique's main finding.**

For a product with **no** countable units — most products, since `ServingSizeParser` is
deliberately conservative — the screen is **pixel-identical** to before. No mode row, no extra
spacing, nothing rendered. This was a hard design constraint (§11) and it holds: verified by test
(`aProductWithNoPortionUnitsKeepsTheOriginalSingleFieldLayout`).

For a product **with** a countable unit, the screen now stacks: product summary → (notice, if any)
→ portion question → mode row → count field → equation text → status/verify row → (remote-changed
notice, if any) → **+ Add portion unit** → result panel. That is up to four more elements than the
original grams-only layout (mode row, equation, status row, add-unit action) on top of the
same-height result panel.

**This was not hypothetical either — it showed up as a real test failure.** While testing on a
1080×2400 emulator with the on-screen keyboard open (typing into the count field), the equation
text and the add-portion-unit action were pushed low enough in the scrollable region that they
required a scroll to reach, rather than being visible without one. The result panel itself stayed
visible throughout — it's pinned outside the scrollable area — so the number the user actually
needs never disappeared. But the supporting equation text, the one piece of UI that explains *why*
that number is what it is, can go off-screen at the exact moment (keyboard open, mid-typing) a user
might most want to glance at it.

*This is a real, shipped trade-off, not a bug fixed in this pass.* The screen already scrolls by
design (`verticalScroll`), so nothing is unreachable — but "reachable by scrolling" and "visible
while typing" are different guarantees, and the countable-portion additions moved this screen from
usually satisfying both to sometimes only satisfying the first. Recorded here as a known limitation
rather than silently shipped; see `docs/known-limitations.md`.

## Can the user correct a bad slice weight quickly?

**Partially.** If the unit came from Open Food Facts, tapping **Online portion** marks it
`✓ Verified by you` — but the *current* implementation does not let the user edit the weight
inline while doing so; it verifies the value as-is (`ProductRepository.verifyPortionUnit` does
accept an optional corrected amount, but no UI path currently calls it with one). A user who
notices "36 g" is wrong for their actual loaf has no faster fix than: add a **new** custom unit
with the correct weight, which works, but is not obviously "the way to correct a wrong one" — it
reads as adding a second, competing unit.

*Verdict: functional, not smooth.* A follow-up UI affordance — an edit action on the verify tap,
pre-filling the amount field with the current value — would close this gap. Not built in this pass;
the repository-level support for it already exists (`verifyPortionUnit(unitId,
confirmedAmountPerUnit)`), so this is a UI-only follow-up, not a data-model change.

## Does the app remember the user's normal mode next time?

**Yes**, and this is one of the more solidly verified parts of the feature. `ProductRepository`
persists `lastInputMode`/`lastSelectedPortionUnitId`/`lastCount` alongside the existing
`lastPortion`, and `ProductViewModel.onProductLoaded` restores whichever mode was last used —
covered by a dedicated repository test (`the last input mode, portion unit and count are
remembered`) and reflected in Recents (`2 slices → 30.2 g`, not `72 g → 30.2 g`). Switching back to
grams explicitly clears the remembered selection (`switching back to grams clears the remembered
portion unit selection`) — a deliberate choice, not an oversight, so "I used grams last time" is
just as sticky as "I used slices last time."

## Can it all be done one-handed?

**Yes for the golden path**, with the same caveat as the clutter question: one-handed reach is
about thumb distance, and every new control (mode chips, count field, add-unit action) sits in the
same lower-half zone the original design already reserved for one-handed use — nothing was added
near the top of the screen. The scrolling-while-keyboard-open issue above is the one place this
answer is qualified rather than unconditional: reaching a control that has scrolled out of the
visible area is still one-handed, but it is an extra gesture the golden path (select unit, type
count, read result) does not require.

---

## What changed as a result of this critique

Nothing in the shipped UI was altered because of this pass — the findings above are two flagged,
disclosed trade-offs (count-field pre-fill, keyboard-open scroll) and one identified follow-up
(inline weight correction on verify), not defects that block the golden path. They are recorded in
`docs/known-limitations.md` and as follow-up candidates rather than fixed silently or left
undocumented.
