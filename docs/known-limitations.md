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

**Implemented, but real-world reliability is still under validation.** The parser now uses OCR
element geometry to establish a total-carbohydrate row and a per-100 g/ml column. Poor light,
curved packaging, glare, small print, damaged labels, and unusual tables can still defeat either
text recognition or spatial alignment. A confident proposal still requires a user tap; ambiguous
interpretations are shown as candidates, and insufficient evidence routes to still capture or
manual entry.

**The centralized terminology set covers 15 Latin-script label languages:** English, Dutch,
German, French, Spanish, Italian, Portuguese, Turkish, Polish, Danish, Swedish, Norwegian,
Finnish, Czech, and Romanian. Six have realistic spatial fixtures; the remaining nine have
terminology/exclusion fixtures. Automated coverage is not evidence of reliable recognition on all
fonts, packages, and camera conditions.

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

**The order-dependent flaky instrumented test is fixed (2026-08-14), and it was a test bug, not an
app bug.** The earlier entry here recorded it as unexplained. The actual cause, found by reading
the full-suite failure rather than re-reading the code: a control covered by the soft keyboard is
not clickable, but `performClick()` on it does not throw — it clicks nothing, the expected state
change never happens, and a later assertion fails for a reason unrelated to what it was checking.
That also explains the odd symptom, a `waitUntil` timing out instead of eventually succeeding —
nothing was ever going to change. The fix is per-interaction: controls that can sit below the fold
with the keyboard open get `performScrollTo()` first. No retry, no sleep, no weakened assertion.

**Fixed 2026-08-14 — the count field mistyping and the scrolled-away equation.** Both are listed
here as resolved rather than deleted, because a reader who saw an earlier build should be able to
tell whether what they hit is still true. The count field now selects its contents on focus, so
typing `2` over a pre-filled `1` gives `2`; and the equation is now pinned with the result panel
instead of living in the scrollable region, so it stays visible with the keyboard open. Both are
regression-tested.

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

**Image attribution is now in the app (2026-08-14).** Open Food Facts licenses its photographs
under CC BY-SA 3.0, separately from the ODbL/DbCL covering the database, and Settings → About now
carries a distinct credit line for each. What remains open is the *licence review* — whether the
overall use of OFF data and images is compliant — which is a different question from whether the
required credit is displayed. See `docs/third-party-notices.md`.

**There is no browse-all-products screen.** Products are reached through Recents and favourites. A
product cleared from recent history remains in the database but is only re-reachable by scanning its
barcode again.

## Verification status of this build

Counts below are from the run of 2026-08-14, not from memory.

| Area | State |
|---|---|
| Calculation, parsing, validation, repository, OCR parsing, meal totals, label comparison, search | **237 JVM unit tests, all passing** |
| Room DAO/migrations plus calculator, gallery, meal, label-verification, usual-portion and search UI behaviour | **95 instrumented tests, all passing** on an API 36 emulator |
| Instrumented suite stability | The one previously order-dependent test now passes in the full suite; the cause was a keyboard-covered control, fixed per-interaction rather than retried |
| Dependency vulnerabilities | **226 shipped artifacts scanned against OSV.dev, 0 known vulnerabilities** (2026-08-14, `tools/dependency-scan.sh`) — a point-in-time result, not a standing property |
| Scan → portion → carbs, recents, manual entry, ml basis, dark mode, large font | Exercised by hand on an API 36 emulator |
| Barcode decoding from a real barcode | **Verified on a physical device** (2026-08-14) |
| Rebuilt spatial nutrition-label OCR against real packaging | **Implemented, but real-world reliability is still under validation.** The previous parser was spot-checked on one physical device; that does not validate this rewrite. |
| Real Open Food Facts responses | **Verified against the live API** (2026-08-14): barcode 3017620422003 returned Nutella, 57.5 g/100 g, with its product image. Also covered by 17 tests over a local HTTP server |
| Breadth of physical hardware, incl. Samsung Galaxy specifics | Only spot-checked; not systematically tested |
| Release (R8/minified) build | Builds and runs on an emulator; Room, enums and ML Kit verified to survive minification. Not yet run on physical hardware, and not signed with production material |

## Regulatory

**CarbScan's regulatory status is unresolved and gates publication.** See
[regulatory-release-checklist.md](regulatory-release-checklist.md). Do not publish until that
assessment is complete and recorded.
