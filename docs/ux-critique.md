# UX critique — the 2026-08-14 development pass

A §69-style critique of everything added in this pass: the temporary meal, OCR label verification,
usual portions, search by name, and the design/hero-image work.

Written after driving each screen on an API 36 emulator, including against the live Open Food Facts
API and against the minified (R8) release build. Where a judgement comes from reading code rather
than using the screen, it says so — that distinction turned out to matter more than any other in
this pass.

**The standing caveat:** none of this has been used on a physical device. The two things ever
confirmed on real hardware are barcode scanning and label OCR. Everything below is emulator
evidence, which is real evidence but not the same thing as a person holding a bag of bread.

---

## The thing this pass should be judged on

Four layout defects reached a running app, and **not one was caught by a test**. The suite went
from 42 to 89 instrumented tests during this pass and stayed green through every one of them.

They were all variations on the same mistake: the meal bar, then the usual-portions row, competing
for vertical space with the portion field and the on-screen keyboard.

| # | What broke | Why no test caught it |
|---|---|---|
| 1 | Meal bar as a fixed header clipped the portion question and pushed *+ Add portion unit* off-screen | Every element still existed in the semantic tree and every assertion passed |
| 2 | Meal bar in the scrolling zone became invisible whenever the keyboard opened | Tests do not have the keyboard open at assertion time unless made to |
| 3 | Meal bar at full size inside the pinned panel grew the panel *upward* over the portion field | The panel and field both existed, at sizes nothing asserted on |
| 4 | The Usual row made the portion zone taller, leaving *+ Add portion unit* half under the panel | Same: presence, not position |

The lesson is narrow and worth keeping: **Compose semantics assert existence, and these were all
failures of position.** A node that is present, clickable in the tree, and covered by the keyboard
passes every ordinary assertion while being unusable by a human.

The same root cause explains the instrumented test that had been called "flaky" since the previous
session. It was never flakiness: `performClick()` on a keyboard-covered control does not throw, it
clicks nothing, so a later assertion fails for an unrelated-looking reason. Fixed by scrolling
before clicking, and the full suite has now run green three consecutive times.

**One of my own tests was also wrong in the same direction.** I wrote a geometric regression test
that compared element positions before and after typing — but `performTextInput` opens the IME, so
it measured about 268 dp of keyboard and called it layout movement. It would have "passed" while
proving nothing. Replaced with a single-layout assertion that the panel top never overlaps the
field bottom.

---

## Temporary meal

**Scenario: a plate with three things on it.**

Add to meal → Add & scan next → repeat → read one total. The flow works and the total is correct.
*Add & scan next* is the right primary action; the alternative (add, back out, find the scanner
again) is three taps for something people do repeatedly in one sitting.

**What is good:** each row names the product *and* the portion (`2 slices`, `65 g`), not just a
number. A meal listing `31 g, 24 g, 10 g` would be unauditable — the user could not tell which
line was wrong.

**Honest weakness: editing a meal item means removing and re-adding it.** There is no way to
change `65 g` to `70 g` in place. For a scratchpad this is defensible, and remove-then-re-add is
two taps, but it is a real gap rather than a deliberate simplification, and it should be named as
one.

**A correction to my own earlier claim.** I documented in three places — including the privacy
policy — that the meal does not survive a restart. That was false: `MealStore` is Room-backed. I
found it by force-stopping the app and re-reading the database, and the home screen still showed
`Meal · 1 item · 34.7 g`. The behaviour is right (losing a half-built plate to an app switch would
be a bug); the documentation was wrong, and it was wrong in a user-facing document about data
retention. Fixed everywhere.

**Scope holds, structurally.** There is no meal id anywhere in the code, so "meal history" is not
merely absent but unbuildable without first adding the concept. No date is shown anywhere.

---

## OCR label verification

**Scenario: the saved value looks wrong.**

Scan the package, compare, decide. The critical property — **both numbers side by side, nothing
applied without a tap** — holds.

The basis-mismatch case is the part I would defend hardest: when the stored value is per 100 ml and
the scanned label is per 100 g, the dialog offers **no apply path at all**. Not a warning, not a
confirm — the action simply does not exist. The app has no density data, and a per-100-ml value
silently accepted as per-100-g would corrupt a stored figure the user later trusts. Refusing to
compare is the honest answer.

**Weakness: discoverability.** *Enter label value* sits in the overflow menu. Someone who has not
gone looking will not know verification exists. That is arguably correct for a rarely-used
action — the alternative is a permanent button competing with the portion field, which the four
layout defects above suggest is a bad trade — but it is a real cost, not a neutral choice.

---

## Usual portions

**Scenario: the same two slices of bread every morning.**

A portion repeated for a product becomes a one-tap row.

**The design decision worth defending: it is deliberately reluctant.** A portion used once does not
appear. I confirmed this directly in SQLite — after using three different portions once each, the
table held three variants at count 1 and none qualified. That restraint is the feature: a shortcut
offered too eagerly is a wrong number one tap away, and this app's whole failure mode is confident
wrong numbers about food.

**Weakness: the reluctance is invisible.** The user is never told a shortcut is coming, so its
first appearance looks arbitrary. I would rather have that than a chatty explanation, but a person
who never notices the row simply never benefits from it.

Per-product only, and `PortionUsageStore` has no all-usage accessor, so no cross-product picture of
someone's eating can be assembled from it.

---

## Search by name

**Scenario: the barcode is not in the database.**

Verified end to end against the **live** API, and again on the **minified release build**: unknown
barcode `2777777777777` → *Search by name* → `hagelslag` → real Dutch results with brands and
package sizes → selected one → calculator with the real product photo at 67 g/100 g. A 15 g portion
gave `10.1 g` with `≈ 10 g whole grams` beneath, which is correct HALF_UP rounding and the owner's
decimal-dominant hierarchy.

Three decisions worth recording:

**A search failure is never shown as "no matches".** The endpoint answered 503 three times during
verification while product reads stayed healthy. Telling someone their product does not exist
because a host was busy would send them off to transcribe a label the database already has.

**Search is not offered when the lookup failed for network reasons.** Confirmed by putting the
emulator into airplane mode: the offline screen still offers only label-scan and manual entry.
Offering search against the same dead host would be a dead end dressed up as a way forward.

**Nothing is auto-selected, and this is structural.** A `ProductSearchHit` is not a `Product` and
cannot become one — no provenance, no verification status, no id. Selecting one runs an ordinary
barcode lookup, so a searched product is validated and cached by exactly the same path as a
scanned one. There is no code that *could* auto-select, even for a single result.

**A card never implies a value it does not have.** A record with no carbohydrate figure says so in
words; a `0 g carbs` card would be a confident wrong answer.

**Weakness: results are ranked by Open Food Facts, not by us.** The right product is usually near
the top, but "usually" is doing real work in that sentence, and a user in a shop may take the first
plausible-looking row. The mitigation is that each card shows brand and package size — a 390 g pack
and a 600 g pack are visibly different — but nothing prevents a hasty wrong pick.

---

## Design and hero image

The product photo makes the calculator recognisably *about the thing in your hand*, which matters
when a wrong product is otherwise just a name. Images pass the host allowlist, so a corrupt record
cannot make the app fetch from an arbitrary host.

The spacing/typography pass is the least verifiable work here. It looks right at 1.0× and 1.8× font
scale on one emulator screen size. I would not claim more than that.

---

## What I would fix next, in order

1. **Get this onto a physical device.** Every layout defect in this pass came from real rendering,
   and a phone is a different renderer from an emulator. This is the highest-value next step by a
   wide margin.
2. **In-place editing of a meal item**, replacing remove-and-re-add.
3. **Make label verification discoverable** without giving it permanent screen space.
4. Add a golden-path instrumented test that asserts *positions* with the keyboard open, since the
   existing 89 assert presence and that is exactly the gap these four defects walked through.
