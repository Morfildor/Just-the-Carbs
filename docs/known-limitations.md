# Known limitations — CarbScan

Written to be read by someone deciding whether to trust a number this app produced. Nothing here is
softened (§67).

---

## Data

**Public food databases can be wrong.** Open Food Facts is crowd-sourced. Entries can be incomplete,
mistyped, or contributed in good faith from a package that differs from the one in your hand. The
app shows *Online value · Check package if needed* for exactly this reason, and lets you override
any value with one you have read yourself.

**Products change while keeping the same barcode.** A manufacturer can reformulate a product without
issuing a new GTIN. A value that was correct last year may be wrong today, and neither the database
nor the app can detect this. **A verified value is a snapshot of the package you checked, not a
permanent fact.** The app records the verification date so staleness can be surfaced later, and
never blocks a calculation because a verification is old.

**The app validates plausibility, not truth.** A carbohydrate figure is rejected if it is negative,
NaN, infinite, or impossible for its basis (>100 g per 100 g). A value that is merely *wrong* — 30
where the package says 40 — will be accepted and used. Only you can catch that, by checking the
package.

**Only total carbohydrate is used.** Sugars, fibre, starch, polyols and net carbs are never
substituted for a missing total. If the total is absent, the app reports the value as unavailable
rather than deriving one.

## Measurement basis

**Grams and millilitres are never interconverted.** A per-100-ml product's portion is locked to ml.
The app has no density data and will not invent any, so it cannot tell you the gram weight of
250 ml of juice. Enter a per-100-g value from the package if you need grams.

**The basis may be inferred.** When Open Food Facts does not state a readable package quantity, the
app assumes grams. This affects the *unit label*, not the arithmetic — the calculation is identical
either way — but a drink mislabelled as grams could lead you to weigh instead of measure. The Verify
flow lets you correct the basis.

**Multipacks are not interpreted.** A quantity like "6 x 33 cl" is not parsed, so no ½ pack / Full
pack shortcut appears. Whether "the package" means one bottle or the crate is your decision, not a
regex's.

## OCR

**OCR can misread a label.** Poor light, curved packaging, glare, and unusual table layouts all
degrade recognition. Every reading is presented for confirmation and none is ever auto-accepted.
When more than one plausible carbohydrate row is found, the app asks rather than choosing.

**Only Dutch and English labels are recognised.** German and French keywords are present but
untested. Other languages will typically produce no reading, which routes you to manual entry.

**OCR reads what is printed; it cannot know if the print is wrong.**

## Connectivity

**The first lookup of an unknown barcode requires internet.** After that the product is cached and
works offline permanently. Offline with an unknown barcode, the app offers label scanning and manual
entry rather than failing.

**Open Food Facts limits reads to 15 per minute per IP.** Heavy scanning can hit this. The app
serves anything cached without a network call, so this is rare in normal use, and the resulting
message is distinct from a generic failure.

## Countable portions (2026-08-14)

**A countable unit only appears when Open Food Facts' `serving_size` text is unambiguous, or when
you add one yourself.** `serving_size` is free text with no guaranteed format — the parser only
accepts strings that state an explicit count-to-quantity relationship ("1 slice (36 g)"). A bare
weight ("35 g"), a unit word with no count ("portion 25 g"), or anything else ambiguous is rejected
rather than guessed at. This means **most products will not get an automatic countable unit**, even
ones a person would obviously describe by count — that is a deliberate false-negative bias (§5): a
wrong per-item weight silently applied to every future calculation would be worse than asking the
user to type grams, or to add their own unit.

**A remote-suggested unit is not verified by the app.** Like the carbohydrate value itself, a unit
parsed from `serving_size` is shown as *Online portion* until the user checks it against the
package — the parser being cautious about *whether* to produce a unit says nothing about whether
the specific number it produced is correct for the package in hand.

**Not yet verified against a real Open Food Facts `serving_size` response.** Automated tests use
fixtures; no live product with a countable-portion-shaped `serving_size` has been scanned and
checked against real packaging. See `docs/manual-qa.md` §15a, currently unchecked.

**One instrumented UI test is order-dependent flaky, not app-broken.**
`CountablePortionScreenTest.addingAPortionUnitThroughTheInlineFormMakesItImmediatelyUsable` passes
reliably run in isolation, and the same select-unit/read-equation behaviour it checks is covered
reliably by five other tests in the same file — but it intermittently fails when run as part of the
full 42-test instrumented suite. Investigated 2026-08-14: an explicit `waitUntil` poll times out
rather than eventually succeeding, which rules out a simple recomposition-timing race and points at
emulator-level IME/focus state carrying over between test-activity transitions. Left documented in
the test itself rather than deleted, retried silently, or weakened.

**The count field can be mistyped on first use.** It pre-fills `1`; typing without first clearing
it appends rather than replaces (e.g. typing `2` over a pre-filled `1` gives `12`), because the
field does not select-all on focus. The result panel itself is never affected by this — a wrong
count just shows a wrong-but-visible result the user can see and correct. See the full write-up in
[ux-critique-countable-portions.md](ux-critique-countable-portions.md).

**Supporting content can scroll out of view while the keyboard is open.** The `2 slices × 36 g =
72 g` equation text and the *+ Add portion unit* action live in the same scrollable region as the
count field; with the on-screen keyboard open, they are not guaranteed to be visible without
scrolling. The result panel itself is pinned outside this region and stays visible throughout — see
[ux-critique-countable-portions.md](ux-critique-countable-portions.md).

## Scope — things this app deliberately does not do

- **It does not calculate insulin**, an insulin-to-carb ratio, a correction factor, or any dose.
- It does not interpret glucose, and does not communicate with pumps or CGMs.
- It is not a food diary: no daily totals, history beyond recents, calories, or macros.
- It does not replace the information printed on the package.

## Data durability

**Saved products do not survive a device migration.** Android backup is disabled
(`allowBackup="false"`) *and* explicit backup/data-extraction rules exclude the Room database, shared
preferences and files (which is where DataStore lives) from **both** cloud backup and
device-to-device transfer. This is a deliberate trade (§34): your food history - recents, favourites,
verified values and remembered portions - never leaves the device by that route. The cost is that a
new phone starts empty, and verified values need re-entering.

**Product images are displayed under a confirmed licence, but in-app attribution wording is not yet
updated for it.** Open Food Facts images are CC BY-SA — distinct from the ODbL licence covering the
database itself, and confirmed against OFF's current terms of use (2026-08-14). The in-app
attribution string still covers only the database licence. See `docs/third-party-notices.md`; this
is an open pre-publication action.

**There is no browse-all-products screen.** Products are reached through Recents and favourites. A
product cleared from recent history remains in the database but is only re-reachable by scanning its
barcode again.

## Verification status of this build

| Area | State |
|---|---|
| Calculation, parsing, validation, repository, OCR parsing | 116 JVM unit tests, passing |
| Room DAO ordering and decimal round-trip, plus calculator UI behaviour | 26 instrumented tests, passing on an API 36 emulator |
| Scan → portion → carbs, recents, manual entry, ml basis, dark mode, large font | Exercised by hand on an API 36 emulator |
| Barcode decoding from a real barcode | **Verified on a physical device** (2026-08-14) |
| Nutrition-label OCR against real packaging | **Verified on a physical device** (2026-08-14) |
| Real Open Food Facts responses | **Verified against the live API** (2026-08-14): barcode 3017620422003 returned Nutella, 57.5 g/100 g, with its product image. Also covered by 17 tests over a local HTTP server |
| Breadth of physical hardware, incl. Samsung Galaxy specifics | Only spot-checked; not systematically tested |
| Release (R8/minified) build | Builds and runs on an emulator; Room, enums and ML Kit verified to survive minification. Not yet run on physical hardware, and not signed with production material |

## Regulatory

**CarbScan's regulatory status is unresolved and gates publication.** See
[regulatory-release-checklist.md](regulatory-release-checklist.md). Do not publish until that
assessment is complete and recorded.
