# Manual QA checklist — Just the Carbs

For a physical device (§61). The automated suites cover logic; this covers everything that only
happens when a real person holds a real phone in front of real food.

**Device:** ____________________ **Android:** ______ **Build:** ______ **Tester/date:** ______

---

## 0. The kitchen test (do this first)

Stand up. Hold something in one hand. Phone in the other. Scan a real product and get its carb value
one-handed, without putting anything down.

| # | Check | Pass |
|---|---|---|
| 0.1 | Reached the result one-handed | ☐ |
| 0.2 | Never had to reach the top of the screen for anything essential | ☐ |
| 0.3 | Result readable at arm's length, at a glance | ☐ |
| 0.4 | Total taps and keystrokes from launch to result: ______ | ☐ |

## 1. Fresh install

| # | Check | Pass |
|---|---|---|
| 1.1 | Cold start is fast; no artificial splash delay | ☐ |
| 1.2 | Home shows the empty state, not a spinner | ☐ |
| 1.3 | No permission is requested at launch | ☐ |

## 2. Camera permission (§9)

| # | Check | Pass |
|---|---|---|
| 2.1 | Permission requested only on opening the scanner | ☐ |
| 2.2 | Denying shows the explanation plus **Allow camera** and **Enter manually** | ☐ |
| 2.3 | Manual entry works fully with camera permanently denied | ☐ |
| 2.4 | Granting later works without reinstalling | ☐ |
| 2.5 | "Don't allow" twice → app still usable, no dead end | ☐ |

## 3. Scanner (§8) — *the least-verified area; test hardest here*

| # | Check | Pass |
|---|---|---|
| 3.1 | Preview appears quickly | ☐ |
| 3.2 | **EAN-13 decodes** (typical European product) | ☐ |
| 3.3 | **EAN-8 decodes** | ☐ |
| 3.4 | Detection is continuous — no shutter button needed | ☐ |
| 3.5 | Single haptic on success | ☐ |
| 3.6 | Rapid re-scan of the same product does not double-navigate | ☐ |
| 3.7 | Torch toggles; absent gracefully if unsupported | ☐ |
| 3.8 | Crumpled / curved / shiny packaging still scans | ☐ |
| 3.9 | Dim kitchen lighting | ☐ |
| 3.10 | Back from scanner returns Home, does not reopen the camera | ☐ |
| 3.11 | Reopening the scanner works repeatedly (no leaked camera) | ☐ |

## 4. Primary acceptance (§70)

| # | Check | Pass |
|---|---|---|
| 4.1 | **New product:** scan → loads → type `65` → **31 g** appears immediately | ☐ |
| 4.2 | **Known product:** tap recent → previous portion pre-filled → new portion → instant result | ☐ |
| 4.3 | **Unknown product:** scan → *Product not found* → label scan or manual → portion → result | ☐ |
| 4.4 | No workflow felt like a diet tracker | ☐ |

## 5. Portion entry (§16)

| # | Check | Pass |
|---|---|---|
| 5.1 | Decimal keypad appears, not a full keyboard | ☐ |
| 5.2 | Result updates live; no Calculate button anywhere | ☐ |
| 5.3 | **Keyboard never covers the result** | ☐ |
| 5.4 | `.` and `,` both accepted (**Dutch decimal separator**) | ☐ |
| 5.5 | −10 / −5 / +5 / +10 work and never go below zero | ☐ |
| 5.6 | Replacing the existing number is easy | ☐ |
| 5.7 | Empty field shows no result, not `0 g` | ☐ |
| 5.8 | ½ pack / Full pack appear only with a known package size, and are correct | ☐ |

## 6. Result and copy (§18, §19)

| # | Check | Pass |
|---|---|---|
| 6.1 | Decimal dominates (`31.3 g`); whole gram legible beneath (`≈ 31 g`) | ☐ |
| 6.2 | Copy puts **only the number** on the clipboard (paste elsewhere: `31`, not `31 g carbs`) | ☐ |
| 6.3 | Confirmation appears | ☐ |
| 6.4 | Decimal-only setting changes the display | ☐ |
| 6.5 | No other app is opened or controlled | ☐ |

## 7. Verification (§23)

| # | Check | Pass |
|---|---|---|
| 7.1 | Remote product shows *Online value* | ☐ |
| 7.2 | Verify pre-fills current values; correcting is easy | ☐ |
| 7.3 | After saving: *✓ Verified by you* | ☐ |
| 7.4 | Reopening later still shows the verified value | ☐ |
| 7.5 | **Verified value survives a refresh — never silently replaced** | ☐ |
| 7.6 | *Reset to online value* restores the original and clears verification | ☐ |
| 7.7 | Reset is not offered for a hand-typed product (no online value to return to) | ☐ |

## 8. ml vs g (design decision 3.1)

| # | Check | Pass |
|---|---|---|
| 8.1 | Per-100-ml product locks the portion field to **ml** | ☐ |
| 8.2 | No gram/ml conversion is offered or implied anywhere | ☐ |
| 8.3 | Correcting a wrongly-inferred basis via Verify works | ☐ |

## 9. Nutrition-label OCR (§29) — *implemented; real-world reliability under validation*

| # | Check | Pass |
|---|---|---|
| 9.1 | Dutch label: *Koolhydraten* row detected | ☐ |
| 9.2 | English label: *Carbohydrate* row detected | ☐ |
| 9.3 | **"waarvan suikers" is never taken as the total** | ☐ |
| 9.4 | Nothing is auto-accepted — always *Use* / *Edit* | ☐ |
| 9.5 | Two plausible columns → candidates shown, app does not choose | ☐ |
| 9.6 | Carb row visible but per-100 basis unclear → candidate plus explicit g/ml choice; never a guessed basis | ☐ |
| 9.7 | Unreadable label → honest failure plus *Capture label* and manual entry | ☐ |
| 9.8 | *Capture label* reads a sharper still through the same confirmation flow | ☐ |
| 9.9 | Torch toggles when the device has a flash; scanner remains usable without one | ☐ |
| 9.10 | Repeated capture/live attempts stay responsive; no OCR backlog develops | ☐ |
| 9.11 | No captured label image appears in Photos and no temporary file remains after completion/exit | ☐ |

## 9a. Product image gallery

| # | Check | Pass |
|---|---|---|
| 9a.1 | Product with no safe selected images keeps the monogram/hero and has no gallery action | ☐ |
| 9a.2 | One image opens a modal over the calculator with label/close/Back and no arrows | ☐ |
| 9a.3 | Multiple images swipe and page with explicit arrows; indicator and type/language update | ☐ |
| 9a.4 | Tall and wide images fit without cropping important package text | ☐ |
| 9a.5 | Slow image shows progress without blocking portion entry or changing the result | ☐ |
| 9a.6 | Broken image shows an unavailable/retry state and closes back to the unchanged calculator | ☐ |
| 9a.7 | Cached gallery image remains visible offline; an uncached one fails cleanly | ☐ |
| 9a.8 | Dark mode, largest font, TalkBack labels, swipe, arrows, close, and Android Back are usable | ☐ |

## 10. Offline and errors (§32, §36)

| # | Check | Pass |
|---|---|---|
| 10.1 | Airplane mode: cached and verified products work fully | ☐ |
| 10.2 | Airplane mode + unknown barcode → *No internet connection* with both escape routes | ☐ |
| 10.3 | Very slow connection: lookup is cancellable; no infinite loader | ☐ |
| 10.4 | Unknown barcode online → *Product not found* with both escape routes | ☐ |
| 10.5 | Product with no carb value → *Carbohydrate value unavailable*, no invented number | ☐ |
| 10.6 | No stack trace or raw error is ever shown | ☐ |

## 11. Recents and favourites (§21, §22)

| # | Check | Pass |
|---|---|---|
| 11.1 | Most recently used first | ☐ |
| 11.2 | Favourites float above other recents | ☐ |
| 11.3 | Card shows name and `65 g → 31 g` (or, for a countable-unit product, `2 slices → 30 g`), nothing irrelevant | ☐ |
| 11.4 | Tapping opens the calculator immediately, no network wait | ☐ |
| 11.5 | Last portion is remembered — **including after leaving via the system back gesture** | ☐ |
| 11.6 | …and after Home, and after the app is killed | ☐ |

## 12. Appearance and accessibility (§38, §39)

| # | Check | Pass |
|---|---|---|
| 12.1 | Light mode | ☐ |
| 12.2 | Dark mode | ☐ |
| 12.3 | Theme setting overrides the system | ☐ |
| 12.4 | **Largest system font: nothing clips — especially `+10` and the pack buttons** | ☐ |
| 12.5 | Largest font: a 4-digit result is fully readable | ☐ |
| 12.6 | TalkBack: result is announced when the portion changes | ☐ |
| 12.7 | TalkBack: every icon button is labelled | ☐ |
| 12.8 | Status is never conveyed by colour alone | ☐ |
| 12.9 | Touch targets comfortable one-handed | ☐ |

## 13. Lifecycle (§63)

| # | Check | Pass |
|---|---|---|
| 13.1 | Rotate mid-edit: portion and result survive | ☐ |
| 13.2 | Background and resume: state intact | ☐ |
| 13.3 | "Don't keep activities" enabled → no crash, sensible restore | ☐ |
| 13.4 | Process killed mid-edit → sensible restore, no duplicate network request | ☐ |
| 13.5 | Rapid back-and-forth navigation does not duplicate lookups | ☐ |

## 14. Samsung Galaxy specifically (§4)

| # | Check | Pass |
|---|---|---|
| 14.1 | Samsung keyboard: decimal separator behaves | ☐ |
| 14.2 | One UI large-font / display-size settings do not clip the result | ☐ |
| 14.3 | Samsung camera behaviour: scanner starts reliably | ☐ |
| 14.4 | Gesture navigation and edge-to-edge layout correct | ☐ |
| 14.5 | Samsung battery optimisation does not break resume | ☐ |

## 15a. Countable portions (2026-08-14) — *not yet verified on hardware*

Do not hardcode a live OFF product into an automated test; this section exists precisely because
real Open Food Facts data (a real Dutch sliced-bread barcode) needs a human to check it.

| # | Check | Pass |
|---|---|---|
| 15a.1 | Scan a real Dutch packaged sliced bread; product loads with name, image, carbs | ☐ |
| 15a.2 | If OFF has a parseable `serving_size` for it, a **Slices** (or matching kind) chip appears automatically — if not, this product is not a countable-portion case; try another | ☐ |
| 15a.3 | Selecting the chip shows a count field defaulting to `1`, and the equation text (`1 slice × NN g = NN g`) | ☐ |
| 15a.4 | Typing `2` updates the equation and the result together, live | ☐ |
| 15a.5 | The equation's per-slice weight is visible enough to sanity-check against the package | ☐ |
| 15a.6 | Switching **Slices → Grams** keeps the equivalent gram amount, no re-typing | ☐ |
| 15a.7 | Switching **Grams → Slices** restores the last count | ☐ |
| 15a.8 | **+ Add portion unit**: create a custom unit (e.g. "Dumpling", a weight from your own scale); it becomes usable immediately, no extra tap | ☐ |
| 15a.9 | A decimal count (`1.5`) is accepted and calculates correctly | ☐ |
| 15a.10 | A negative or garbage count shows no result, not a crash or a wrong number | ☐ |
| 15a.11 | Favourite the product, back out, reopen from Recents: the countable mode/unit/count you last used is restored — **not** grams | ☐ |
| 15a.12 | Recents card for this product reads `2 slices → 30 g`, not `72 g → 30 g` | ☐ |
| 15a.13 | Turn on airplane mode, reopen the same product: countable entry still works fully offline | ☐ |
| 15a.14 | Dark mode: mode chips, count field and equation text all legible | ☐ |
| 15a.15 | Largest system font: chips and equation text do not clip or overlap | ☐ |
| 15a.16 | Keyboard open (typing a count): the result at the bottom of the screen stays visible | ☐ |
| 15a.17 | A long product name combined with the mode row does not push the result off-screen | ☐ |

## 15b. Direct-carb portions (2026-08-15) — *not yet verified on hardware*

The path for a product whose label gives carbohydrate **per serving** but prints no per-item weight.
The whole point is that the user is never asked to weigh anything, so **any gram figure appearing on
this path is a defect**, not a nicety.

| # | Check | Pass |
|---|---|---|
| 15b.1 | Find an OFF product whose `serving_size` names a unit with no bracketed weight (e.g. `"2 slices"`) and whose `carbohydrates_serving` is present; scan it | ☐ |
| 15b.2 | A countable chip appears for that unit even though no weight is known | ☐ |
| 15b.3 | Entering `4` gives a result equal to 4 × (serving carbs ÷ serving count), checked by hand | ☐ |
| 15b.4 | The equation reads `4 slices × NN g carbs` — **no gram weight and no "= NN g" weight term anywhere** | ☐ |
| 15b.5 | The portion/grams field stays empty; nothing fills in a derived weight | ☐ |
| 15b.6 | Case D: a product with `"1 slice"` and **no** `carbohydrates_serving` offers **no** automatic chip — the app must not invent a relationship | ☐ |
| 15b.7 | **+ Add portion unit** → **Carbs per unit** lets you define one by hand; only one field is shown, labelled "1 slice contains … g carbs" | ☐ |
| 15b.8 | Correcting a direct-carb unit (Edit) pre-fills the carbs figure, not a weight, and saving keeps its Open Food Facts provenance with ✓ Verified | ☐ |
| 15b.9 | Switching a direct-carb unit → Grams and back does not produce a fabricated weight or a stale result | ☐ |

## 15c. Direct-carb portions in the meal (2026-08-15) — *not yet verified on hardware*

| # | Check | Pass |
|---|---|---|
| 15c.1 | Add a direct-carb portion to the meal; the line reads "4 slices", not a gram amount | ☐ |
| 15c.2 | The meal total equals the sum of its lines, including a mix of weighed and counted items | ☐ |
| 15c.3 | Force-stop and relaunch: the direct-carb line survives with its count and total intact | ☐ |
| 15c.4 | Upgrading from a build with existing meal items and portion units (v5 → v6) loses nothing: verified units stay verified, favourites and recents survive, usual portions still resolve | ☐ |

## 15d. OCR total vs child nutrients (2026-08-15) — *not yet verified on hardware*

This is the failure that motivated the rewrite. Re-photograph the **original packages** that
previously misread, not just any label.

| # | Check | Pass |
|---|---|---|
| 15d.1 | A multi-column label (per 100 g **and** per serving) reports the per-100-g total, not the serving figure | ☐ |
| 15d.2 | A hierarchical label ("Carbohydrate … of which sugars …") reports the **total**, never the sugars value | ☐ |
| 15d.3 | A label with a `%RI`/`%DV` column never reports the percentage as grams | ☐ |
| 15d.4 | A label naming a specific sugar (dextrose, glucose syrup) still reports the total | ☐ |
| 15d.5 | A genuinely unreadable/ambiguous label offers an explicit choice rather than a confident wrong number | ☐ |
| 15d.6 | A label with no per-100 header at all reports "not found" and offers a retake — it does not offer a value with an unknown basis | ☐ |
| 15d.7 | Live scanning still settles rather than flickering; a single bad frame does not strand an ambiguity card | ☐ |

## 15e. OCR → save as portion unit (2026-08-15) — *not yet verified on hardware*

| # | Check | Pass |
|---|---|---|
| 15e.1 | From a product screen, scan a label whose serving column names a unit ("per slice"); after the still capture, **Save as a slice portion** appears | ☐ |
| 15e.2 | It does **not** appear during live scanning — only after an explicit capture | ☐ |
| 15e.3 | It does **not** appear when the serving column is generic ("per serving") with no countable unit named | ☐ |
| 15e.4 | Tapping it creates a usable countable unit on that product, shown as verified | ☐ |
| 15e.5 | A "per 2 slices" column halves correctly — the saved unit is per *one* slice | ☐ |

## 15f. The two real packages (2026-08-16) — *the release gate for OCR*

The nutrition scanner failed on real packaging while 459 automated tests passed. The cause was
row reconstruction collapsing on ordinary camera tilt (see CLAUDE.md, "Real-device scanner pass").
It is fixed and covered by geometry regressions at 4 row pitches x 7 tilts — but **a synthetic
reconstruction of a label is not the label**. These two packages are the acceptance criteria.

The standard is not "it worked once, held perfectly square". Repeat each row with a slight angle,
ordinary hand shake, moderate glare, the table off-centre inside the frame, and the package
slightly rotated. Robust normal use, not laboratory photographs.

**This section stays OPEN, and the nine-fixture instrumented suite does not close it.** Be precise
about what those passing JPEGs do and do not establish (2026-08-17):

> **Proven:** a real photographed JPEG → ML Kit → `MlKitOcrMapper` → the parser, on nine real
> packages, including tilt, curvature, multilingual rows and a second package in frame.
>
> **Still requires hardware:** live CameraX capture → `ViewPort` → shutter → `ImageCapture` → EXIF
> orientation → ROI crop → OCR. Every stage before `MlKitOcrMapper` is emulator-only, and the crop's
> correctness lives in the camera *binding*, which no fixture exercises.

### Sondey / Lidl biscuits — trilingual NL/FR/DE, decimal comma

| # | Check | Pass |
|---|---|---|
| 15f.1 | Reports **61.9 g per 100 g** | ☐ |
| 15f.2 | Never reports **47.6** (that is the sugars row) | ☐ |
| 15f.3 | Reads `61,9` as 61.9 — never 619, never a choice between 61 and 9 | ☐ |
| 15f.4 | Still correct with the table tilted a few degrees | ☐ |
| 15f.5 | Still correct with the table off-centre inside the frame | ☐ |

### Kinder / Ferrero chocolate — per 100 g + per piece + %RI

| # | Check | Pass |
|---|---|---|
| 15f.6 | Reports **53.5 g per 100 g** | ☐ |
| 15f.7 | Never reports **3** or **7** (reference percentages) | ☐ |
| 15f.8 | Never reports **53.3** (sugars per 100 g) | ☐ |
| 15f.9 | The per-piece figure read is **6.7**, from the carbohydrate row and not the sugars row | ☐ |
| 15f.10 | Where recognition permits, the piece weight is **12.5 g** and `1 piece` — or no weight at all, never a different one | ☐ |

### The crop, which is the highest-risk new behaviour

The still capture is cropped to the on-screen frame before recognition, and that mapping depends on
CameraX cropping the saved JPEG to the `ViewPort`. It is emulator-checked only.

| # | Check | Pass |
|---|---|---|
| 15f.11 | With the table filling the frame, the reading is correct (crop is not cutting the table) | ☐ |
| 15f.12 | With the table near an edge of the frame, the reading still succeeds (the 12% margin is enough) | ☐ |
| 15f.13 | `adb logcat -s JustTheCarbsOCR` shows `still cropped to=` roughly the frame's share of `still resolution=` | ☐ |
| 15f.14 | Capturing repeatedly does not slow down or run out of memory at 8 MP | ☐ |

### Barcode acceptance (§1–§4)

| # | Check | Pass |
|---|---|---|
| 15f.15 | Raising the phone past a shelf does **not** fire a lookup for a product not aimed at | ☐ |
| 15f.16 | Deliberately aiming at a barcode still scans quickly — a brief steadying moment, not a wait | ☐ |
| 15f.17 | A distant barcode does not fire while walking toward it | ☐ |
| 15f.18 | The hint reads "Point the barcode inside the frame", then "Hold steady" | ☐ |
| 15f.19 | From *Product not found*, **Scan barcode again** returns straight to the camera in one tap | ☐ |
| 15f.20 | After that, a new barcode scans normally (the latch really reset) | ☐ |

## 15g. The seven new real packages (2026-08-17) — *still open*

Seven more package photographs were added as committed fixtures and are pinned by
`RealImageOcrTest` (now **mandatory** — a missing fixture fails the suite rather than skipping it).
Those tests run the real ML Kit recognizer over the committed crops, so the **parser** is proven on
real optics. The **camera path is not**: nothing below has been done on a phone.

Two of these labels are prose (running sentences, not tables) and are read by `ProseNutritionReader`
behind its eligibility gate. Two more currently return `NotFound` by design — see the table.

| # | Check | Pass |
|---|---|---|
| 15g.1 | Juice (per 100 **ml**): either a correct 9.0 per 100 ml or a clean "couldn't read" — never a per-100-**g** answer | ☐ |
| 15g.2 | Grated cheese: reports 2.0 per 100 g. **Known:** the committed crop yields 2.09 because ML Kit misreads the digit; a real capture may do better or worse | ☐ |
| 15g.3 | Jar (multilingual prose): reports **1.6**, or `NotFound`. Never 20 (that is the saturated-fat figure) | ☐ |
| 15g.4 | Lid (curved prose): reports **3**, or `NotFound`. Never **2.5** (sugars) and never **19** (saturated fat) | ☐ |
| 15g.5 | Witte kaas: reports 2.3 per 100 g, or a clean "couldn't read" | ☐ |
| 15g.6 | Stokbrood (dense prose): reports **46**. Never 1.0 (sugars), 4.7 (fibre) or 12 (protein) | ☐ |
| 15g.7 | Yoghurt: reports **5.0** per 100 g. Never 3.0 (that is a %RI figure) | ☐ |
| 15g.8 | On any label above, a wrong-looking number is **never** presented confidently — an unreadable label must say so | ☐ |

**Known-open at the time of writing, all safe (`NotFound`, never a wrong confident value):** fixtures
1 and 5 fail at recognition (ML Kit fuses or loses the basis header); fixture 2 returns 2.09 because
recognition itself produces `2,09`; fixtures 3 and 4 open no prose *declaration*, because their basis
headers print as `Næringsindhold (100g)` and a fused `PourPerlPro 100g:` with no connective. Widening
the declaration grammar to accept those is deliberately **deferred to its own task with its own
safety envelope** (owner, 2026-08-17) — see `CLAUDE.md`.

## 16. Temporary meal (2026-08-14) — *not yet verified on hardware*

The meal is deliberately one unnamed, undated list. If any check below reveals a date, a name, a
*past* meal, or a second meal, that is a scope breach (§2), not a missing feature.

| # | Check | Pass |
|---|---|---|
| 16.1 | Calculate a portion, tap **Add to meal**: the running total appears with that item | ☐ |
| 16.2 | **Add & scan next** adds the item and opens the scanner in one tap | ☐ |
| 16.3 | Add three products; the total equals the sum of the three individual results | ☐ |
| 16.4 | Each meal row names the product *and* the portion used (`2 slices`, `65 g`) | ☐ |
| 16.5 | Removing one item updates the total immediately and correctly | ☐ |
| 16.6 | **Clear** empties the meal and asks first — no silent discard | ☐ |
| 16.7 | The meal bar does not cover the portion field or the *+ Add portion unit* action, keyboard open or closed | ☐ |
| 16.8 | Force-close and reopen the app: the meal is **still there**, so a half-built plate is not lost to an app switch | ☐ |
| 16.9 | There is still no way to see a *past* meal — only the current one exists | ☐ |
| 16.9 | Nowhere does a date, a time, a meal name, or a history of past meals appear | ☐ |

## 17. Label verification against a saved value (2026-08-14) — *not yet verified on hardware*

| # | Check | Pass |
|---|---|---|
| 17.1 | Open a saved product, choose **Scan label to verify**, scan the real package | ☐ |
| 17.2 | Matching value: it says so plainly and offers to mark the product verified | ☐ |
| 17.3 | Differing value: **both numbers are shown side by side**, and neither is applied until you choose | ☐ |
| 17.4 | Choosing the package value updates the product; declining leaves it untouched | ☐ |
| 17.5 | Scanning a label whose basis differs (per 100 ml vs per 100 g) refuses to compare rather than converting | ☐ |
| 17.6 | An open calculation is never silently changed by a verification result | ☐ |

## 18. Usual portions (2026-08-14) — *not yet verified on hardware*

| # | Check | Pass |
|---|---|---|
| 18.1 | Use the same portion for one product several times; a **Usual** shortcut eventually appears | ☐ |
| 18.2 | Tapping it fills that portion and produces the same result as typing it by hand | ☐ |
| 18.3 | A portion used only once does **not** appear — restraint is the intended behaviour | ☐ |
| 18.4 | Shortcuts are per product; another product does not inherit them | ☐ |
| 18.5 | No dates, counts of use, or history are shown to the user anywhere | ☐ |

## 19. Search by name (2026-08-14) — *partly verified on the emulator against live OFF*

19.1–19.4 were verified on an API 36 emulator against the live API (unknown barcode
`2777777777777` → search `hagelslag` → real Dutch results → selection loaded De Ruijter at
67 g/100 g). They still need confirming on real hardware.

| # | Check | Pass |
|---|---|---|
| 19.1 | Scan/enter a barcode OFF does not have: **Search by name** is offered | ☐ |
| 19.2 | A real query returns recognisable products with brand and package quantity | ☐ |
| 19.3 | Nothing is ever auto-selected, even when exactly one result comes back | ☐ |
| 19.4 | Selecting a result loads it into the calculator like any scanned product | ☐ |
| 19.5 | A result with no carbohydrate value says so in words — never `0 g` | ☐ |
| 19.6 | In airplane mode, search is **not** offered on the failure screen (the same host is unreachable) | ☐ |
| 19.7 | A search server error offers **Try again** and never reads as "no such product" | ☐ |

## 19a. Live search continuity (2026-08-28) — *not yet verified on hardware*

This pass exists because live search was functionally correct and visibly rough on a physical
device. Every check below is about **continuity** — what stays on screen — so run them by watching,
not by reading state. The emulator is not the target: it does not reproduce the network timing that
made the original build flicker.

Do these on the search screen **and** on Home's inline search; the two render the same three cases
and must not disagree.

| # | Check | Pass |
|---|---|---|
| 19a.1 | Type a product name at a normal pace: the results appear without pressing Search | ☐ |
| 19a.2 | Add a word to a query that already has results: the old list **stays** and a thin line appears under the field — no full-screen spinner, no blank | ☐ |
| 19a.3 | The results below do not jump vertically as that line appears and disappears | ☐ |
| 19a.4 | The old results remain **tappable** during the refresh, and tapping one opens that product | ☐ |
| 19a.5 | When the newer results arrive they replace the old ones in one step — no empty frame in between | ☐ |
| 19a.6 | "No products found" never flashes while typing or while a search is running | ☐ |
| 19a.7 | Turn airplane mode on **while results are showing**, then edit the query: the results stay, with *Couldn't refresh results* and *Try again* above them — **not** the full error screen | ☐ |
| 19a.8 | Turn airplane mode back on with an **empty** result area and search: the full error screen with *Try again* / *Scan nutrition label* / *Enter manually* is still what appears | ☐ |
| 19a.9 | *Try again* on that inline notice keeps the results visible while it retries | ☐ |
| 19a.10 | Backspace below three characters: the results disappear at once rather than lingering | ☐ |
| 19a.11 | Clear the field: the screen returns to its resting state immediately | ☐ |
| 19a.12 | Press the keyboard's Search key mid-typing: results arrive without a visible second load | ☐ |
| 19a.13 | With TalkBack on, typing does not announce anything per keystroke; a failed refresh is announced once | ☐ |

## 19b. Search request pacing (2026-08-28) — *not yet verified on hardware*

The app caps itself at **9 Open Food Facts searches per minute, shared across Home and the search
screen**. These checks exist because the failure they guard against — the app earning a 503 and
reporting it as *"The product database is unavailable"* — was only ever visible on a real device
under real network timing.

**Scope correction (later in 1.0.2):** that 9/min cap now applies to the Open Food Facts **fallback
only**. The primary provider is the dedicated search service (§19c), which imposes no such budget,
so ordinary successful searches no longer wait for it. Every check below still stands — the observable
requirement is unchanged and is the same one throughout: that message must not appear unless the
database is genuinely unreachable. Checks 19b.6 and 19b.10 now exercise the *fallback* path, so
expect them to be reachable only while the primary is also failing.

The thing to watch for throughout: **that message must not appear at all** unless the database is
genuinely unreachable.

| # | Check | Pass |
|---|---|---|
| 19b.1 | Type continuously for 20–30 seconds: the visible list keeps up with typing, and no error appears at any point | ☐ |
| 19b.2 | Pause repeatedly between characters for a minute: still no error, and results still arrive | ☐ |
| 19b.3 | Change the query completely mid-wait: the result that eventually arrives belongs to the **last** thing typed, never an earlier prefix | ☐ |
| 19b.4 | Press Enter ten times quickly: no burst of requests, no error, one result | ☐ |
| 19b.5 | Cause a real failure (airplane mode), then tap *Try again* ten times: no hammering, and recovery works once connectivity returns | ☐ |
| 19b.6 | Search on Home, then immediately open the search screen and search again: the second one waits its turn rather than both going out | ☐ |
| 19b.7 | Toggle Wi-Fi/mobile mid-search: no *database unavailable* flash for what is really a pacing wait | ☐ |
| 19b.8 | Clear the field while *Updating…* is showing: everything resets at once and nothing arrives afterwards | ☐ |
| 19b.9 | *Updating…* never persists indefinitely — a result, a no-results verdict or an error always follows | ☐ |
| 19b.10 | If a rate-limit notice appears, it offers **no** *Try again* button and clears by itself | ☐ |

## 19c. Search provider migration (2026-08-28) — *not yet verified on hardware*

Text search now runs against Open Food Facts' dedicated search service, with the previous endpoint
kept as an automatic fallback. **This migration is not complete until it is driven on the same
physical phone that exposed the original search problem** — everything below has only been seen on
the emulator and in tests.

Two things make this section different from §19a/§19b. First, the fallback is **deliberately
invisible**, so the only way to tell which provider answered is the debug log:

```
adb logcat -s JtcSearch
```

A line reading `primary OK hits=20` means the new service answered; `fallback start` means the old
one was used. Second, **fewer results now show a carbohydrate figure** — that is expected and not a
defect (the new index does not publish the field the app uses to tell grams from millilitres). What
must never happen is a figure shown with the *wrong* unit.

| # | Check | Pass |
|---|---|---|
| 19c.1 | Type rapidly across several different queries: results keep up, and the log shows one `primary` line per settled query — not one per keystroke | ☐ |
| 19c.2 | Pause frequently while typing: still one request per pause at most, no error at any point | ☐ |
| 19c.3 | Replace the query completely: the result that lands belongs to the **last** thing typed | ☐ |
| 19c.4 | Search from Home, then immediately open the search screen and continue: no duplicated requests, both screens behave identically | ☐ |
| 19c.5 | Press Enter repeatedly: one request, no burst | ☐ |
| 19c.6 | Clear the field mid-request: everything resets and nothing arrives afterwards | ☐ |
| 19c.7 | Turn on airplane mode and search: **one** error, shown once — never a primary error followed by a second failure | ☐ |
| 19c.8 | Restore connectivity and tap *Try again*: results arrive | ☐ |
| 19c.9 | Search feels noticeably faster than it did in 1.0.1 — no multi-second wait before results | ☐ |
| 19c.10 | A result **without** a carbohydrate figure still shows name, brand, size and photo, and is tappable | ☐ |
| 19c.11 | Tapping any result — with or without a figure — opens the product with its **correct** carbohydrate value and unit | ☐ |
| 19c.12 | No result card ever shows a figure whose unit looks wrong for the product (a drink asking for grams, etc.) | ☐ |
| 19c.13 | Scan a barcode and calculate a portion: unchanged by all of the above | ☐ |
| 19c.14 | In the log, confirm at least one `fallback start` … `fallback OK` sequence (force it by searching while the primary is blocked, if it does not occur naturally) and confirm the screen showed no error during it | ☐ |
| 19c.15 | Search a Dutch term (`hagelslag`, `kaas`): Dutch products are found and Dutch names are shown | ☐ |

## 19d. Search hardening — POST, unusable replies, punctuation (2026-08-28) — *not yet verified on hardware*

Three changes on top of §19c, all on the search path only. Same debug log as §19c
(`adb logcat -s JtcSearch`); the primary's start line now reads `primary start (POST /search)`.

**Two of these are invisible by construction**, which is why they need driving rather than reading:
a damaged reply and a genuine no-match both render as an empty list, and the escaping change alters
only what leaves the app — the search field still shows exactly what was typed.

The punctuation cases below are not invented. Each returned **zero results** against the live
service before this change, except `milk -chocolate`, which returned a full list of the *wrong*
products — it searched for milk **without** chocolate.

| # | Check | Pass |
|---|---|---|
| 19d.1 | Search `Kinder Bueno (White)`: finds Kinder Bueno products. Before the fix this returned nothing at all | ☐ |
| 19d.2 | Search `milk + chocolate`: finds milk chocolate products | ☐ |
| 19d.3 | Search `milk -chocolate`: results **do** include milk chocolate — the `-` is part of the text, not an instruction to exclude | ☐ |
| 19d.4 | Search `M&M's`, `Ben & Jerry's`, `Coca-Cola Zero`, `70% chocolate`, `Lay's`: each finds the expected brand, i.e. escaping did not break ordinary names | ☐ |
| 19d.5 | Search `Côte d'Or` and a non-Latin term if available: accented and non-ASCII text is unchanged and still matches | ☐ |
| 19d.6 | Search a term with a quote or colon (`chocolate: dark`, `"milk"`): returns products rather than nothing | ☐ |
| 19d.7 | The search field itself still shows exactly what was typed — no backslashes, no rewriting, no cursor jumps | ☐ |
| 19d.8 | Dutch recall is unchanged from §19c.15 (`hagelslag` still finds Dutch products with Dutch names) — this confirms the language setting still reaches the server in the new request format | ☐ |
| 19d.9 | Results still arrive in one to two seconds; the switch to POST added no perceptible delay | ☐ |
| 19d.10 | Tapping a result still opens the product with the correct carbohydrate value and unit | ☐ |
| 19d.11 | Scan a barcode and calculate a portion: unchanged | ☐ |

**Optional, needs a proxy or a captured request** — the privacy claim of the POST change. With a
debugging proxy (or Charles/mitmproxy) on the same network, confirm the request to
`search.openfoodfacts.org` shows **no search text in the URL**; the term appears only in the request
body. This is the one check that directly verifies what the change was made for.

## 19e. Repeat-search speed and relevance (2026-08-28) — *not yet verified on hardware*

The efficiency pass. A short-lived memory of recent successful searches means a query you already
ran comes back without another request. **Nothing about ranking changed** — phrase boosting was
evaluated and does not exist on this service — so the relevance rows below are a regression check,
not a check of something new.

The saving is only observable as *speed*, since a cache hit is deliberately indistinguishable from a
fast search: no badge, no "cached" label, no different spinner. `adb logcat -s JtcSearch` is what
distinguishes them — a cache hit produces **no** `primary start` line at all.

The memory lasts about five minutes and only while the app is running. Force-stopping the app or
leaving it long enough clears it, which is what rows 19e.4 and 19e.5 check.

| # | Check | Pass |
|---|---|---|
| 19e.1 | Search `chocolate`, then `gouda`, then `chocolate` again. The third search shows results **immediately** — no spinner, no visible delay | ☐ |
| 19e.2 | In `adb logcat -s JtcSearch` during 19e.1: two `primary start` lines, not three. The repeated query produced no request | ☐ |
| 19e.3 | The re-shown `chocolate` results are chocolate products, in the same order as the first time — not gouda, not a mixture | ☐ |
| 19e.4 | Force-stop the app, reopen it and search `chocolate`: it searches normally again (the memory does not survive a restart, and nothing was written to the device) | ☐ |
| 19e.5 | Leave the app open for over five minutes, then repeat a search done before that: it fetches again rather than showing stale results | ☐ |
| 19e.6 | Turn off the network, search something new (it fails), turn the network back on and search the same thing: it retries and succeeds — a failure is never remembered | ☐ |
| 19e.7 | Search a term with no results (e.g. `zzzznotaproduct`), then search it again: it searches again rather than replaying "no results" from memory | ☐ |
| 19e.8 | Type `chocolate`, then quickly change to `gouda` before results settle. The screen ends on **gouda's** results — an instant cache answer must not land on a newer query | ☐ |
| 19e.9 | Repeat a search on Home's inline search that was first run on the search screen (or vice versa): it is instant, because both screens share one memory | ☐ |
| 19e.10 | Exact product searches still rank sensibly: `Nutella`, `Kinder Bueno`, `Coca Cola Zero`, `Oreo`, `Snickers` each put the expected product first | ☐ |
| 19e.11 | Dutch searches are still strong: `hagelslag`, `pindakaas`, `stroopwafel`, `speculaas`, `karnemelk` each find the expected Dutch products | ☐ |
| 19e.12 | Multi-word searches are still good: `dark chocolate`, `peanut butter`, `chocolate milk`, `tomato pasta sauce` | ☐ |
| 19e.13 | Generic searches still return something useful: `pasta`, `milk`, `bread`, `cheese` | ☐ |
| 19e.14 | The punctuation rows of §19d still pass — the escaping is unchanged and must stay that way | ☐ |
| 19e.15 | Result cards show the same information as before: name, brand, package size, photo, and a carbohydrate figure where one is known | ☐ |
| 19e.16 | Tapping any result — cached or freshly searched — opens the product with the correct carbohydrate value and unit. **This is the one that matters**: search data is only for choosing, and the number always comes from the product lookup afterwards | ☐ |
| 19e.17 | No new flicker, no spinner flash on a repeat search, and no new error messages anywhere in search | ☐ |
| 19e.18 | With TalkBack on, a repeated search announces exactly as a normal search does — nothing extra about caching | ☐ |

## 19f. Touch-target sizes (2026-08-28) — *not yet verified on hardware*

Seven controls were smaller than the app's own 48dp minimum and are now full size. **No layout
moved and no styling changed** — only the tappable area grew to match the text already drawn — so
these rows are as much a check that nothing *shifted* as that the targets improved.

Sizes are asserted automatically by `TouchTargetSizeTest`, which is what makes this section short:
the measurement is already covered. What a device adds is the thing a test cannot have — a thumb.
Do these one-handed, standing up, the way the app is actually used.

The grams/slices pair is the row that matters. A mis-tap there does not make the number wrong by a
little; it changes whether the number means grams or a count.

| # | Check | Pass |
|---|---|---|
| 19f.1 | On the calculator with a countable portion available, tap **Grams** then **Slices** repeatedly with a thumb, one-handed. Every tap registers; none is missed or lands on the neighbouring chip | ☐ |
| 19f.2 | The chips look **unchanged** — same size text, same colours, same spacing between them and the fields above and below | ☐ |
| 19f.3 | *+ Add portion unit* responds to an ordinary thumb tap anywhere on its text | ☐ |
| 19f.4 | The search and clear (✕) buttons inside Home's search box both respond first time; neither requires aiming | ☐ |
| 19f.5 | The same two buttons on the Search screen behave identically | ☐ |
| 19f.6 | With a meal in progress, Home's meal bar opens the meal on a thumb tap anywhere along it, and looks the same as before | ☐ |
| 19f.7 | Home's *Enter manually* responds first time, and the gap between it and the card above it is unchanged | ☐ |
| 19f.8 | Set the system font to its **largest** setting. All of the above still work, nothing is clipped, and no control has grown so tall that it pushes something important off screen | ☐ |
| 19f.9 | Nothing on Home, Search or the calculator overlaps, jumps, or re-flows compared with the previous build | ☐ |

## 20. Attribution (2026-08-14)

| # | Check | Pass |
|---|---|---|
| 20.1 | Settings → About shows the ODbL/DbCL line for data **and** a separate CC BY-SA 3.0 line for photos | ☐ |
| 20.2 | Neither line is clipped at the largest system font size | ☐ |

## 15. Safety acceptance (§71) — all must be true

| # | Check | Pass |
|---|---|---|
| 15.1 | Nothing anywhere calculates or suggests insulin or any medication | ☐ |
| 15.2 | No glucose is requested or interpreted | ☐ |
| 15.3 | No claim that database values are guaranteed correct | ☐ |
| 15.4 | No uncertain OCR value is ever auto-accepted | ☐ |
| 15.5 | Verified data is never silently replaced | ☐ |
| 15.6 | No missing value is ever invented | ☐ |
| 15.7 | No implied affiliation with any medical product or manufacturer | ☐ |
| 15.8 | No unsupported medical claim in any copy | ☐ |

---

**Blocking issues found:**

**Sign-off:** ____________________ **Date:** ____________
