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

## 21. OCR quick calculation (1.0.3, 2026-08-29) — PHYSICAL DEVICE ONLY

The emulator's virtual camera cannot render a nutrition table, so the *automatic* accept path
(a confident reading → **Confirm**) has never run on a real label. Everything below was verified on
the emulator through the assisted-reading path, which shares the same callback — that establishes the
destination, **not** the recognition. 21.1 is the one row that cannot be replaced by a test.

| # | Check | Pass |
|---|---|---|
| 21.1 | Scan a real package's nutrition label from Home. A confident reading's **Confirm** lands on the calculator — **not** on *Enter product* | ☐ |
| 21.2 | The screen is titled *Quick calculation*, shows the detected value **with its basis** ("48 g carbs / 100 g") and *Read from label by you*. No blank title, no empty image tile | ☐ |
| 21.3 | Typing a portion produces a total immediately. No name was requested at any point | ☐ |
| 21.4 | Leave the app (back to Home). **Recents shows no new entry** — the calculation persisted nothing | ☐ |
| 21.5 | Repeat, then tap **Save product**. It asks for a name and nothing else; the result stays visible behind the dialog | ☐ |
| 21.6 | Cancelling the save returns to the calculation with the portion and total unchanged | ☐ |
| 21.7 | Saving keeps the number on screen, and Home then lists the product with its portion ("Name — 35 g → 16.8 g") | ☐ |
| 21.8 | A millilitre label asks for a portion **in ml**, not grams | ☐ |
| 21.9 | Scanning a label while a product is already open still opens the **comparison** (both figures side by side), not the quick calculator — this path is unchanged and must stay so | ☐ |
| 21.10 | A reading the parser cannot place still offers *Correct* / manual entry, and never a basis guess | ☐ |
| 21.11 | The keyboard is **already open** on arrival and the portion can be typed without tapping the field first | ☐ |
| 21.12 | Opening a **saved** product from Recents does **not** open the keyboard — the remembered portion and the *Usual* shortcuts are visible and tappable | ☐ |
| 21.13 | A quick calculation added to the meal appears as *Quick calculation*, never as a blank line | ☐ |
| 21.14 | If a save fails (hard to force; skip if you cannot), the dialog closes and the failure is visible on the screen behind it | ☐ |
| 21.15 | Set the phone's text size to **large** (Settings → Display → Font size, ~1.3× or more). On the calculator the last control above the result panel **fades out** rather than being cut through the middle of its letters, and scrolling reveals it | ☐ |
| 21.16 | Scan a real nutrition table that reads well. **The crop screen does not appear** — capture goes straight to the value proposal | ☐ |
| 21.17 | Scan a hard/cluttered label. The crop screen **does** appear and says *Couldn't read it automatically*, not *Tighten the box* | ☐ |
| 21.18 | From that fallback, dragging the corners and tapping **Read table** still works exactly as before | ☐ |
| 21.19 | **Retake** during the automatic attempt returns to the live camera; the next capture's crop screen does **not** claim a failed attempt | ☐ |
| 21.20 | Back during the automatic attempt leaves the scanner cleanly, with no stale spinner and no Recent entry | ☐ |
| 21.21 | Ten consecutive scans show no progressive slowdown and no leaked camera | ☐ |
| 21.22 | Record for ~10 scans: time from **Capture** to a usable value, and whether the crop screen appeared. This is the P3 measurement that could not be taken without hardware | ☐ |

## 22. Device-recording corrections (1.0.3, 2026-08-30) — PHYSICAL DEVICE ONLY

The four cases from the 2026-08-30 screen recording, retested against the fixes. **Do not tick any
row that was not actually observed on hardware** — every claim below is emulator/JVM-only today.

### 22a. The red label — the P0 case

Use the same red package that produced `790` / `794`.

| # | Check | Pass |
|---|---|---|
| 22.1 | Scan it and reach the assisted path (tap the row, tap the number, or type it in) | ☐ |
| 22.2 | If a figure like `790` or `794` is produced, **neither** *Use / 100 g* nor *Use / 100 ml* is offered for it | ☐ |
| 22.3 | The screen says *"That can't be right — check the figure."* rather than showing a dead button | ☐ |
| 22.4 | **No corrected number is offered anywhere** — `79.0`, `7.90` and `7.9` must not appear as an app suggestion. The app refuses; it never repositions the decimal point | ☐ |
| 22.5 | Typing the printed `7.9` by hand is accepted normally and both actions appear | ☐ |
| 22.6 | The resulting calculation is correct for the portion entered | ☐ |

### 22b. Coconut milk — the P1 case, at two framings

| # | Check | Pass |
|---|---|---|
| 22.7 | **Close framing** (table fills the frame): still fast-paths, skipping the crop screen | ☐ |
| 22.8 | The value and `/100 ml` are both correct | ☐ |
| 22.9 | **Wide framing** (lots of surrounding package): note whether it declines — expected, and the reason for 22.13 | ☐ |
| 22.10 | If the value needs assistance, the app does **not** ask "per what?" — it says *Read from the label as per 100 ml* and offers that one action | ☐ |
| 22.11 | The portion field then asks for **ml**, not grams | ☐ |
| 22.12 | On a label printing **both** per 100 g and per 100 ml, the app still asks — this ambiguity must not be auto-resolved | ☐ |

### 22c. Framing guidance — the P3 wording

| # | Check | Pass |
|---|---|---|
| 22.13 | Held far from a label, the guidance reads *"Move closer — fill the frame with the nutrition table"* | ☐ |
| 22.14 | It never blocks the shutter — **Capture** is always tappable | ☐ |
| 22.15 | It does not appear when framing is genuinely fine (a false "move closer" is how advice gets ignored) | ☐ |

### 22d. Reflective / curved label — the P2 case

| # | Check | Pass |
|---|---|---|
| 22.16 | Automatic attempt declines and the crop screen says *"Couldn't read it automatically"* | ☐ |
| 22.17 | Tap **Read table** *without moving any corner*: it goes to the assisted path **immediately**, with no second wait | ☐ |
| 22.18 | It says *"Same box as before, so it would read the same."* — **not** the "your box kept nearly the whole photo" message, which would be a different and wrong claim | ☐ |
| 22.19 | Now **do** move a corner meaningfully and tap **Read table**: recognition genuinely runs again (a visible *Reading table…* pause) | ☐ |
| 22.20 | **Retake**, then capture again: the first *Read table* on the new photo runs a real pass — a new capture must never be treated as an unchanged crop | ☐ |

### 22e. Regression sweep while the phone is in hand

| # | Check | Pass |
|---|---|---|
| 22.21 | A clean table still fast-paths to a correct value at the same or better speed | ☐ |
| 22.22 | Barcode scanning is unaffected | ☐ |
| 22.23 | Quick calculation: no name asked, keyboard already open, no Recents entry on exit | ☐ |
| 22.24 | Optional **Save product** still works and then appears in Recents | ☐ |
| 22.25 | A quick calculation added to the meal reads *Quick calculation*, not a blank row | ☐ |
| 22.26 | **Back** during processing leaves cleanly; no stuck spinner | ☐ |

## 22f. How to run §22 — the execution appendix (2026-08-30)

Everything in §22 is **PHYSICAL DEVICE ONLY** and none of it has been run. This appendix exists so
the session that finally has a phone in hand does not have to re-derive the build, the log filter or
the recording format. It adds no checks of its own; it is how to produce evidence for the ones above.

### The build under test

Do not build a fresh APK and assume it matches. Verify the artifact:

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat :app:assembleDebug
(Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256).Hash
& 'C:\atools\sdk\build-tools\36.0.0\aapt2.exe' dump badging app\build\outputs\apk\debug\app-debug.apk |
  Select-String '^package:'
```

Expect `versionCode='4' versionName='1.0.3-debug'` and package `app.justthecarbs.debug` — note the
**`.debug` suffix**, which is what `adb shell am start` and `pm clear` must name.

The build prepared for the §22 run, from a clean tree at commit `73df0e1`, is also copied to
`C:\Users\tuncb\Desktop\JustTheCarbs-debug.apk`:

| | |
|---|---|
| Size | 89,436,837 bytes |
| SHA-256 | `8F1BCB6D759560598E0D66195C09AF4B77A45E9A779E10CA58E744BD4792B873` |
| Read from the APK | `versionCode='4'`, `versionName='1.0.3-debug'`, `app.justthecarbs.debug` |
| Permissions | CAMERA, INTERNET, ACCESS_NETWORK_STATE (+ AGP's debug-only receiver permission) |

**A debug APK is not reproducible byte-for-byte** — rebuilding the same commit yields a different
hash, because build IDs and timestamps vary. A hash mismatch against the table above is therefore
*not* evidence of a different tree; check `git rev-parse HEAD` and a clean `git status` for that.
Record whichever hash you actually installed.

**The build must be a debug build**, and not for convenience: `OcrDiagnosticsLogger` is
`BuildConfig.DEBUG`-gated and R8 strips it entirely from release, so a release APK produces **no
timing evidence at all**. Latency measured on debug is also *pessimistic* — it includes the evidence
writer a user never pays for. See `ScanTrace.markOffPath`: read `user-visible` from the summary
line, not `scan`.

### Confirm it is really hardware

The single check that settles it:

```powershell
& 'C:\atools\sdk\platform-tools\adb.exe' shell getprop ro.kernel.qemu    # must be EMPTY, not 1
& 'C:\atools\sdk\platform-tools\adb.exe' shell getprop ro.build.characteristics  # must NOT be 'emulator'
& 'C:\atools\sdk\platform-tools\adb.exe' shell getprop ro.product.model
```

An emulator answers `1` / `emulator` / `sdk_gphone64_x86_64`. **No §22 row may be ticked from an
emulator**, and the emulator's virtual camera cannot render a nutrition table at all, so the
automatic accept path is unreachable there by construction.

### Watching the scan

Clear the log immediately before each scan so one capture's lines stand alone:

```powershell
$adb='C:\atools\sdk\platform-tools\adb.exe'
& $adb logcat -c
& $adb logcat -s JustTheCarbsOCR
```

The lines that answer §22, in the order one scan emits them:

| Line | What it tells you |
|---|---|
| `ImageCapture requested=… selected=… viewportCrop=… rotation=…` | what CameraX **negotiated on this device** — the open §23 question; `selected` is the fact, `requested` only a hint |
| `acquisition <n>ms (shutter to file)` | sensor readout + JPEG encode + write, invisible to `ScanTrace` |
| `pass A (uncropped) recognised=WxH relevance=[…]` | recognition ran on the **whole** capture; the region is relevance, never a crop |
| `scan <n>ms (user-visible <n>ms) \| stage · stage …` | the per-stage breakdown. **Stages marked `*` are off-path** (debug-only or post-handover) and are excluded from `user-visible` |
| `selected table: <OUTCOME> elements=A->B reparse=<n>ms outcome=<Reading>` | the resolution. `elements=A->B` identical means the crop removed no interference |
| `strategy-B <trace>` | the independent ML Kit pass ran (~400 ms). **Absence is NOT proof it did not run** — it can return null without logging; read `strategy B` in `selection.txt` instead |
| `fast-path declined (<Outcome>)` | the automatic attempt handed over to the crop screen |
| `selected-table skipped (crop unchanged since last pass)` | the shortcut through an already-refused region. **Not D1 evidence on its own** — see the 2026-08-31 findings below |

**A successful fast-path advance logs no line of its own.** It is identified by
`selected table: … outcome=Confident` *without* a following `fast-path declined`. Do not hunt for an
"advanced" message; there isn't one, and reading its absence as a failure would be a misdiagnosis.

### The latency record

Ten scans, and keep the two populations apart — a median mixing them describes nothing:

| # | Label | Framing | Fast path? | Crop shown? | acquisition | mlkit | A | B | reparse | Capture→useful UI |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 |  |  |  |  |  |  |  |  |  |  |

Report separately: **median and range for fast-path scans**, **median and range for fallback scans**,
and the **advance/decline counts**. The expected trade is one screen and one tap saved on a good
scan against ~400 ms added before a declining one; both halves must be stated, not just the win.

**State explicitly whether any wrong value advanced automatically.** That is the one result that
overrides every latency figure in this table — a silence there is not the same as a "none".

### Findings from the 2026-08-31 device session (five captures, SM-S928B)

Corrections to the rows above, made from the evidence bundle in `docs/Scan evidence 31-08-26/`.
Read these before recording anything in this appendix.

**A wrong value DID reach `Confident` on hardware.** The Fanta Zero capture
(`20260831-140132-710`) printed `0,5 g` per 100 ml and pass A reported
`CONFIDENT 0.59 PER_100_ML` from a correct `TOTAL_CARBOHYDRATE` row with a correctly resolved
column. It did not reach the user, but §22f's "did any wrong value advance" question must not be
answered "none observed" for this session.

**Two bundle fields were misread, and both are now fixed in the recorder.** They are recorded here
because a bundle taken before the fix still carries the old shape:

- `meta.txt` `outcome` was **pass A's reading**, not the resolver's verdict — the fast-path gate
  reads `AutomaticScanAdvance.mayAdvance(EvidenceResolver.Outcome)` over *every* pass. It is now
  written as two separate lines, `passA.reading` and `resolver.verdict`.
- `selection.txt`'s `second OCR pass : no (Pass A elements reused)` was a **hardcoded string
  literal** that measured nothing. Strategy B *is* invoked on the automatic attempt whenever
  independent runs do not already agree, and its result was dropped silently when it returned null.

**D1 is therefore UNEVIDENCED in that bundle, and the row above overstates what the log proves.**
A `strategy-B` line's absence is not evidence that no second recognition ran — in a debug build
`SelectedRegionRecognizer` can return null (recycled bitmap, degenerate crop, caught exception)
without logging. `selection.txt` now records `strategy B`, `resolver.verdict` and the passes that
contributed evidence, so the next bundle can answer this; until then, do not tick D1.

Consequence for latency: `recognition: <n>ms (ML Kit alone)` covers **pass A only**, so any
Strategy B cost is unaccounted for in the five folders recorded on 2026-08-31.

**§22.13 and §22.15 are unverified, not passed.** No framing guidance appeared in 5:45 of testing,
including a 20-second stretch of visible framing difficulty, so the true positive never fired and
§22.15 could not be reached.

**§21.1 and §21.16 remain unticked.** The crop screen appeared on all five captures.

### P2-1, noted and NOT implemented

The parser distinguishes failures the user never sees. Three distinct causes currently share one
sentence, and the third was missed by the original brief:

| Diagnostic | Seen in | What it actually means |
|---|---|---|
| `No total-carbohydrate row` | 140208-173 | the carbohydrate row was not found |
| `Total-carbohydrate row found but no usable per-100 cell` | 135943-434, 140033-054, 140056-376 | the row was found; the "per 100 g" heading was not |
| **two readings disagreed** (`EvidenceResolver.Outcome.Conflicted`) | — | passes contradicted each other; at least one is wrong and nothing can say which |

The third is a different statement from either of the others and must not be folded into "couldn't
read it". Keep the existing title — §22.33 depends on it — and change the subtitle only.

### Physical UX observations to make while scanning

Not new features — behaviours only a hand on a phone can check. Record what happened, not a tick.

| # | Check | Pass |
|---|---|---|
| 22.27 | **Retake during automatic processing** leaves cleanly; the abandoned pass's result never appears | ☐ |
| 22.28 | **Back during automatic processing** exits with no stuck spinner | ☐ |
| 22.29 | The scan after a Retake shows **no stale failure wording** — it must not open saying the automatic attempt failed before one has run | ☐ |
| 22.30 | Ten scans in a row do not get progressively slower (an ML Kit client or bitmap leak would show here) | ☐ |
| 22.31 | Fast repeated taps on **Capture** / **Read table** cause no duplicate processing | ☐ |
| 22.32 | The processing text matches the actual state — *Reading table…* only while a pass is genuinely running | ☐ |
| 22.33 | The crop screen says **why** it appeared (*Couldn't read it automatically* after a declined attempt, not the generic tighten-the-box wording) | ☐ |
| 22.34 | Quick calculation is readable in **both** Light and Dark | ☐ |
| 22.35 | A **saved** product arrives with the keyboard **closed**; a **quick calculation** arrives with it **open** | ☐ |
| 22.36 | At the largest system font, the portion zone fades/scrolls rather than clipping a control mid-glyph | ☐ |
| 22.37 | The meal row for a quick calculation reads *Quick calculation*, never blank | ☐ |

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

## 23. Parser correctness patch (1.0.3, 2026-09-01) — PHYSICAL DEVICE ONLY, RELEASE GATE

The four labels from the 2026-09-01 device session, after the accompaniment, segmentation, column
anchoring, cross-column and resolver changes. Evidence: `docs/Scan Evidence 01-09-26/`.

**The patch is not release-ready until every row here is ticked on hardware.** These four products
are the ones whose failures motivated the work, so a green JVM suite says nothing about them: three
of the four failures were geometric, and the fourth was a vocabulary gap.

Build under test: `app-debug.apk`, 89,469,605 bytes,
SHA-256 `E2490516D2AFF47CF1C822EDD01560F6ACE7CB9175F90C0D05798FB12B0498E4`,
`versionCode=4` / `versionName=1.0.3-debug` (read from the APK with `aapt2 dump badging`).

### 23a. The green drink — the P0 case

Prints `0,5 g / 100 ml` and `1,3 g / 250 ml`. Before this patch the device produced
**`1.3 g / 100 ml`** — the 250 ml figure wearing the 100 ml basis, wrong by a factor of 2.6.

| # | Check | Pass |
|---|---|---|
| 23.1 | **`1.3 g/100 ml` never appears**, at any framing, however many times it is scanned | ☐ |
| 23.2 | Either `0.5 g/100 ml` is read, or the app refuses and offers a labelled recovery | ☐ |
| 23.3 | No bare number is ever offered without its basis (`0.59`, `13g` alone) | ☐ |
| 23.4 | If candidates are shown, each carries its own basis in the label | ☐ |
| 23.5 | `adb logcat -s JustTheCarbsOCR` shows the 250 ml column as a separate, non-per-100 column | ☐ |

### 23b. The cracker bag — the clean-path canary

Prints `72,0 g / 100 g` and `22,5 g` per portion. This already worked and must be untouched.

| # | Check | Pass |
|---|---|---|
| 23.6 | Reads **`72 g / 100 g`** | ☐ |
| 23.7 | Reaches Quick Calculation **automatically** — no crop screen, no *Confirm* tap | ☐ |
| 23.8 | Exactly **two taps** from Home: *Scan nutrition label*, then *Capture* | ☐ |
| 23.9 | Quick Calculation shows the figure, its basis, and *Read from label by you* | ☐ |
| 23.10 | Capture-to-result median over 10 scans: ______ ms (target ≤1200) | ☐ |

### 23c. The Korean sauce — the fabricated-basis case

A linear US panel: `Serv. size: 1 Tbsp (18 g)`, `Total Carb. 6g`, `Fiber 1 g`. Before this patch the
row typed as a carbohydrate *child* (because of `Fiber`) and the `6 g` was unreachable; manual
selection then offered only "/100 g" and "/100 ml" for a per-serving figure.

| # | Check | Pass |
|---|---|---|
| 23.11 | `6 g` is **never** presented as per 100 g or per 100 ml | ☐ |
| 23.12 | The fibre figure `1 g` is never offered as total carbohydrate | ☐ |
| 23.13 | No `%DV` number (`2`, `4`, `22`) is ever offered as carbohydrate grams | ☐ |
| 23.14 | The outcome is a refusal or an explicit basis question — never a silent per-100 label | ☐ |

### 23d. The multilingual table — the anchoring case

Prints `59,2 g / 100 g` and `5,4 g` per 9 g portion, with an RI column. Before this patch the
per-100 column anchored at x=1439.5, over the *portion* values, and nothing read.

| # | Check | Pass |
|---|---|---|
| 23.15 | Reads **`59.2 g / 100 g`**, preferably automatically | ☐ |
| 23.16 | `54 g` never appears as a value (it is `5,4 g` with the decimal point lost) | ☐ |
| 23.17 | The RI percentages are never offered as carbohydrate | ☐ |
| 23.18 | Fibre (`20,0 g`) and sugars (`1,5 g`) are never offered as the total | ☐ |

### 23e. Latency and diagnostics

Measure on the **debug** build — release strips the diagnostics logger entirely. Read
**`user-visible`** from the trace summary, not `scan`.

| # | Check | Pass |
|---|---|---|
| 23.19 | Strong clean scan: capture → Quick Calculation median ≤1.2 s over 10 scans | ☐ |
| 23.20 | Recovery screen visible ≤1.5 s when the scan cannot be read | ☐ |
| 23.21 | The UI never waits for evidence writing — `evidence-diagnostics` no longer precedes the result | ☐ |
| 23.22 | `selection.txt` lists each pass with what it contributed, not just its name | ☐ |
| 23.23 | An ambiguous reading with no corroboration records `Unresolved`, never `Resolved` | ☐ |
| 23.24 | `meta.txt` no longer claims "not reached" is what the advance gate read | ☐ |

### 23f. Regression sweep while the phone is in hand

| # | Check | Pass |
|---|---|---|
| 23.25 | Barcode scanning is unchanged (§3, §15f) | ☐ |
| 23.26 | A label printing units only in its header still reads — the accompaniment rule did not over-reach | ☐ |
| 23.27 | A Dutch label still reads (`Koolhydraten`, `per 100 g`) | ☐ |
| 23.28 | The nine committed corpus photographs still read as before, via the instrumented suite | ☐ |

---

**Blocking issues found:**

**Sign-off:** ____________________ **Date:** ____________

---

## §24 — P0 regression retest: the green drink only (2026-09-01, second phone session)

**Do this before §23 and before any other product.** It is deliberately short: the P0 repair is
about latency and about one label, and a long sheet would delay the one measurement that matters.

Build under test: `app-debug.apk`, 89,734,290 bytes,
SHA-256 `58516719 84141396 EE086509 4B470743 5C07A912 E0BC9A5A 35A4E774 D00D7FED`,
`versionCode=4` / `versionName=1.0.3-debug` (read from the APK, not from Gradle).

The product is the **green Fanta-style drink** whose label prints `0,5 g/100 ml` in the left column
and `1,3 g/250 ml` in the right. It is the label behind bundles `20260901-222212-563` and
`20260901-222300-297`.

### 24a — Latency, which is the point of this pass

Scan it **five times**, reframing between attempts. Record the wall clock from tapping *Capture* to
either a result or a recovery screen — a stopwatch is fine; this is a seconds-scale question.

| # | Framing | Seconds to result **or** recovery | Outcome shown |
|---|---|---|---|
| 24.1 | Close, table fills the frame | ______ | ______ |
| 24.2 | Close, slight tilt | ______ | ______ |
| 24.3 | Normal arm's length | ______ | ______ |
| 24.4 | Wide, surrounding package visible | ______ | ______ |
| 24.5 | Dim room light | ______ | ______ |

**Pass condition: every row is ≤ 2 seconds.** The before figures were 20.2 s and 11.2 s. A row above
2 s is a failure of this pass regardless of what it eventually displayed.

**Never acceptable at any framing:** the spinner persisting with no result and no recovery screen.
That is the exact behaviour that made the user abandon both recorded scans.

### 24b — Correctness on this label

| # | Check | Pass |
|---|---|---|
| 24.6 | The reading is **`0.5 g/100 ml`**, or a recovery screen — never a silent failure | ☐ |
| 24.7 | **`1.3 g/100 ml` never appears.** This is the forbidden outcome; it is the 250 ml figure | ☐ |
| 24.8 | `13 g/100 ml` likewise never appears | ☐ |
| 24.9 | If recovery appears, it offers the carbohydrate row for tapping and reaches `0.5` | ☐ |
| 24.10 | No unlabelled value (`0.59`, bare `13g`) is offered as a choice | ☐ |

### 24c — Read the log while the phone is in hand

`adb logcat -s JustTheCarbsOCR`. The line to find is the trace summary:

```
scan <total>ms (user-visible <n>ms) | parse <n> · mlkit <n> · evidence-capture <n>* · …
```

| # | Check | Value | Pass |
|---|---|---|---|
| 24.11 | `parse` is **under 700 ms** (was 9278 and 5331) | ______ | ☐ |
| 24.12 | `user-visible` is under 2000 ms | ______ | ☐ |
| 24.13 | `evidence-capture` still carries `*` **and** `scan` minus `user-visible` accounts for it | ______ | ☐ |
| 24.14 | The result appears on screen **before** the bundle finishes writing | | ☐ |

24.14 is the P0-2 check and the one a number cannot answer: watch the screen, not the log. The
result must not wait for the evidence bundle. Reading `scan` instead of `user-visible` is how the
previous session's figures were misread — **the `*` marks arithmetic, not scheduling**, which is
precisely the defect this pass fixed.

### 24d — Only if 24a–24c pass

Then, and only then, continue to §23 for the other three products. If any row above fails, stop and
export the bundle (`scan-evidence`), because a second regression measured on the same label is worth
more than partial coverage of four.

**Result:** ____________________ **Date:** ____________

---

## §25 — Basis-complete recovery retest (2026-09-02, debug APK `7F7EF256…03CF4`)

**Do not tick §22, §23 or §24 from this section.** Those gates cover earlier builds and earlier
questions; this one covers the third phone session's findings and is additive.

**Artifact:** `app/build/outputs/apk/debug/app-debug.apk`, 89,970,536 bytes, SHA-256
`7f7ef25611a96e2bcdc3de436ac9325c742fd5aa944ed1eb5d9849c1c6903cf4`, `versionCode=4` /
`versionName=1.0.3-debug` read from the APK with `aapt2 dump badging`. Desktop copy
`JustTheCarbs-debug.apk` verified byte-identical.

**Why this exists.** The 2026-09-01 third phone session proved the performance repair worked
(parser 66–579 ms, most captures 0.89–1.92 s) **and** that automatic refusal alone is not enough:
the user selected the printed 250 ml value through the recovery screen and Quick Calculation showed
**`1.3 g carbs / 100 ml`** — the original 2.6x error, arriving through the manual path after the
automatic path had been fixed.

### 25a — The green drink, four framings (the P0)

Scan the same drink four times, deliberately varying framing (wide, close, angled, straight on).
For **each** scan, whatever the app does automatically, then open the recovery screen and read the
choices it offers.

| # | Check | Pass |
|---|---|---|
| 25.1 | No screen at any point shows `1.3 g / 100 ml` | ☐ |
| 25.2 | No screen at any point shows `13 g / 100 ml` | ☐ |
| 25.3 | **Every** choice reads `<number> g / <something>` — never a bare number | ☐ |
| 25.4 | Any right-column value that is offered displays `/ 250 ml`, not `/ 100 ml` | ☐ |
| 25.5 | Choosing a `/ 250 ml` figure produces roughly `0.5 g / 100 ml`, never `1.3` | ☐ |
| 25.6 | At least one of the four framings reaches `0.5 g / 100 ml` automatically | ☐ |
| 25.7 | Tapping the **Waarvan suikers** row says it looks like sugars and offers nothing | ☐ |

25.4 is the row that decides this pass. A `/250 ml` label is the app telling the truth about a
column it cannot store; a `/100 ml` label on that same figure is the release-blocking defect.

### 25b — The cracker, both captures

| # | Check | Pass |
|---|---|---|
| 25.8 | Both captures reach `72 g / 100 g` **automatically**, with no crop screen | ☐ |
| 25.9 | No candidate list anywhere contains `9%`, or `9` offered as a value | ☐ |
| 25.10 | The corrupted portion value `22,59` is not offered | ☐ |

### 25c — The Korean sauce

| # | Check | Pass |
|---|---|---|
| 25.11 | The app shows `6 g / 18 g serving`, or its derived `33.3 g / 100 g` | ☐ |
| 25.12 | It **never** asks "per 100 g or per 100 ml?" for this label | ☐ |
| 25.13 | The fibre `1 g` on the same printed row is not offered | ☐ |
| 25.14 | No `%DV` figure (`2`, `4`, `22`) is offered | ☐ |
| 25.15 | When the derived figure is shown, the printed `6 g per 18 g serving` is shown too | ☐ |

25.12 is the sauce's equivalent of 25.4. The label states its basis; being asked to choose between
two bases it does not print means the reading arrived without one.

### 25d — The multilingual (Baltic) table

Read `adb logcat -s JustTheCarbsOCR` while scanning.

| # | Check | Value | Pass |
|---|---|---|---|
| 25.16 | Returns `59.2 g / 100 g` | ______ | ☐ |
| 25.17 | The log lists **three** separate anchors near x≈1330, x≈1505 and x≈1589 | ______ | ☐ |
| 25.18 | `5,4` is never reported as a per-100 figure | | ☐ |

25.17 is the one that distinguishes a correct answer from a lucky one. Before this pass the table
emitted a single column spanning both positions and `59,2` won only because it happened to sit
nearer the midpoint than `5,4`.

### 25e — Two taps, and timing reported cold and warm separately

| # | Check | Value | Pass |
|---|---|---|---|
| 25.19 | A strong scan is **two taps** from Home to Quick Calculation | ______ | ☐ |
| 25.20 | **First** scan after opening the app — record it separately | ______ ms | ☐ |
| 25.21 | Warm scans (2nd onward), median | ______ ms | ☐ |
| 25.22 | Warm median ≤ **1.2 s** | | ☐ |
| 25.23 | Recovery screen visible within **1.5 s** on a scan that declines | ______ ms | ☐ |
| 25.24 | Every warm scan ≤ **2 s** where ML Kit permits | | ☐ |

**Report 25.20 and 25.21 separately and do not average them.** The cold figure was 3401 ms with
2789 ms of it inside ML Kit; a warm-up now runs when the scanner opens, so the cold number should
fall — but if it does not, that is a finding to record, not a threshold to move.

### 25f — Evidence export integrity

| # | Check | Pass |
|---|---|---|
| 25.25 | The result appears on screen **before** the bundle finishes writing | ☐ |
| 25.26 | Exported ZIP opens and every entry extracts without error | ☐ |
| 25.27 | The exported size is plausible for the number of captures (~8–10 MB each) | ☐ |

25.26 is the check the first upload of the third session failed: 58 MB, truncated mid-entry, no
central directory, shared without complaint. The archive is now written under a temporary name,
read back in full, and renamed only if every entry decompresses — so a failure should now present
as an export error rather than as a corrupt file.

**Result:** ____________________ **Date:** ____________

---

## §26 — Verified automatic advancement (2026-09-02, fourth phone session, debug APK `83226166…D3FC9`)

Appended, not a rewrite: §§22–25 stand as the record of what earlier builds were asked to prove.
This section covers only what changed after the fourth phone session, whose nine captures are in
`docs/Scan Evidence 02-09/` and are committed as `FourthSessionFixtures`.

**What that session found.** Automatic accuracy was 4 of 5, and the one miss is the reason this
section exists: `085542-213` displayed **`12 g/100 g`** where the cracker prints `72,0 g`, with no
confirmation step at all. ML Kit read `12,0.g`. Nothing about the token can detect that, and nothing
should try — but the table's own other rows disagree with it by a factor of six, and now that
disagreement blocks the automatic path.

The rule this section tests: **a value one OCR run parsed cleanly is not thereby verified.** It
reaches the user either way; what it no longer does is reach them unconfirmed.

### 26a — The cracker, ten captures

The release blocker. Ten separate captures, re-framing between each, at least three of them
deliberately awkward (angled, close, partly shadowed).

| # | Check | Pass |
|---|---|---|
| 26.1 | No capture ever displays `12 g / 100 g`, at any step, automatic or after a tap | ☐ |
| 26.2 | No capture displays any per-100 figure other than `72` (or refuses) | ☐ |
| 26.3 | A correct capture reaches Quick Calculation in **two taps** — capture, then use | ☐ |
| 26.4 | A capture the app cannot verify shows the value with **one** confirmation tap, never a dead end | ☐ |
| 26.5 | `adb logcat -s JustTheCarbsOCR` shows `automatic-verification: CROSS_COLUMN` on advancing captures | ☐ |
| 26.6 | Count of ten: ___ advanced automatically, ___ asked for confirmation, ___ went to recovery | ☐ |

**If any capture displays a per-100 figure other than 72, stop and record the bundle.** That is the
defect this whole pass exists to close and no other row matters until it is understood.

### 26b — The green drink, five framings

| # | Check | Pass |
|---|---|---|
| 26.7 | No framing ever displays `1.3` or `13` as a per-100-ml figure | ☐ |
| 26.8 | A clean framing reaches `0.5 g / 100 ml` | ☐ |
| 26.9 | On a refused capture, tapping the carbohydrate row selects **that** row, not sugars | ☐ |
| 26.10 | A tap that yields nothing offers *"Enter the value printed under 100 ml"* — it does not simply repeat | ☐ |
| 26.11 | That focused screen offers **no** "100 g or 100 ml?" choice — the basis is already fixed | ☐ |
| 26.12 | Typing `0.5` there gives `0.5 g / 100 ml` | ☐ |
| 26.13 | Tapping the sugars row is still refused, with the message naming sugars | ☐ |
| 26.14 | No repeated-tap loop: every tap changes something on screen | ☐ |

### 26c — The Korean sauce, five captures

| # | Check | Pass |
|---|---|---|
| 26.15 | Recovery offers **`6 g / 18 g serving`** | ☐ |
| 26.16 | Choosing it shows **`33.3`**, never `33.33333333` or any longer decimal | ☐ |
| 26.17 | The provenance line reads *From 6 g per 18 g serving* | ☐ |
| 26.18 | `/100 g` and `/100 ml` are never offered as guesses on this label | ☐ |
| 26.19 | `2`, `4` and `22` (the `% DV` figures) are never offered | ☐ |
| 26.20 | `1` (the `Fiber 1 g` on the same recognised row) is never offered | ☐ |
| 26.21 | Nothing from the ingredient list — which names "brown sugar" — is ever offered | ☐ |

### 26d — Timing, reported cold and warm separately

The eight-of-nine ≤579 ms baseline was measured **before** this pass. Verification adds a cross-column
re-parse (free — rows already built) and, on a label that cannot corroborate itself, a Strategy B
recognition that previously did not run. Both numbers are wanted; do not average them together.

| # | Measurement | Value |
|---|---|---|
| 26.22 | First capture after opening the scanner (cold), `user-visible` from the trace | ______ ms |
| 26.23 | Median of the next nine (warm) | ______ ms |
| 26.24 | p95 of the warm population — target ≤2 s | ______ ms |
| 26.25 | Slowest single warm capture, and which route it took | ______ ms, route ______ |
| 26.26 | A cross-column-verified capture — Strategy B is skipped, so this should stay near baseline | ______ ms |

Read `user-visible`, not `scan`: the debug build carries the evidence writer a user never pays for.

### 26e — The evidence bundle says what happened

Each bundle's `selection.txt` now carries two lines that did not exist before. They are what makes a
wrong reading explainable without a screen recording beside the files.

| # | Check | Pass |
|---|---|---|
| 26.27 | `automatic-verification:` names `CROSS_COLUMN`, `DISTINCT_OCR_AGREEMENT` or `NONE` | ☐ |
| 26.28 | When it is `NONE`, the line says why — a contradiction, or too few coherent rows | ☐ |
| 26.29 | `final UI action :` names `AUTO_ADVANCE`, `CONFIRM` or `RECOVERY` and matches what you saw | ☐ |
| 26.30 | On the misread cracker, the verification line shows the two ratios (~1.875 against ~0.31) | ☐ |
| 26.31 | Every exported archive opens, and every attempt inside it is complete | ☐ |

**Result:** ____________________ **Date:** ____________

---

## §27 — Recovery basis integrity (2026-09-02, fifth phone session, debug APK `4F7C6B67…7ED5E`)

Additive. **§§22–26 are unchanged and still stand**; this section covers only what the fifth session
changed. The gate for the previous pass is §26 and it remains open.

### What this pass fixed, so you know what you are looking for

`20260902-103936-423`. The packet prints `72,0 g / 100 g` and `22,5 g / portion`. ML Kit read the
`per 100 g` header as **`1009`**, so no per-100 column resolved. Two things then went wrong:

1. Recovery offered **`72 g / serving`** — the printed per-100 figure wearing the serving column's
   basis, because that column was the only one left within binding distance (329 px, inside a 370 px
   tolerance). A fabricated basis, one tap from the calculator.
2. The bundle recorded `SELECTED_REGION_OCR → Confident 72.0/PER_100_G` and
   `automatic-verification: CROSS_COLUMN (support=5)` — a verified, correct reading — and then
   `final UI action : RECOVERY`. The app held the right answer and showed a wrong one.

Both are fixed. On the JVM fixtures that capture now resolves to `72.0/PER_100_G` and advances
automatically, and its recovery list contains only the genuine `22.5 g / serving`.

### 27a — The damaged-header cracker (the release blocker)

Photograph the same cracker packet, deliberately including framings that damage the `per 100 g`
header — slight angle, glare across the header band, or the header near the frame edge. **At least
10 captures.**

| # | Check | ✓ |
|---|---|---|
| 27.1 | **No capture ever displays `72 g / serving`, or any per-100 figure labelled `/serving`** | ☐ |
| 27.2 | No capture displays `12 g / 100 g` anywhere — automatic, confirmation or recovery | ☐ |
| 27.3 | A capture whose header reads cleanly reaches `72 g / 100 g` | ☐ |
| 27.4 | A capture whose header is damaged either reaches `72 g / 100 g` or refuses; never a wrong basis | ☐ |
| 27.5 | Where recovery appears, `22.5 g / serving` is offered and is the *only* serving-labelled choice | ☐ |
| 27.6 | Tapping `22.5 g / serving` through to the calculator shows a serving figure, not a per-100 one | ☐ |
| 27.7 | At least one capture reproduces the `1009` header (check `diagnostics.txt`) | ☐ |
| 27.8 | On that capture, `final UI action` is **not** `RECOVERY` when the bundle also says `CROSS_COLUMN` | ☐ |

### 27b — The green drink

**At least 5 captures.**

| # | Check | ✓ |
|---|---|---|
| 27.9 | Every reading is `0.5 g / 100 ml`; never `1.3`, never `13` | ☐ |
| 27.10 | The app never asks "per 100 g or per 100 ml?" for this label | ☐ |
| 27.11 | Every recovery choice offered states `/100 ml` | ☐ |
| 27.12 | A clean capture needs at most one confirmation tap | ☐ |

### 27c — The Korean sauce

**At least 5 captures.** This is a regression check — it worked before this pass and must still.

| # | Check | ✓ |
|---|---|---|
| 27.13 | Recovery offers `6 g / 18 g serving` | ☐ |
| 27.14 | Tapping it through shows **`33.3 g / 100 g`** — not `33.33333333` | ☐ |
| 27.15 | The screen says where it came from (`From 6 g per 18 g serving`) | ☐ |
| 27.16 | No generic "per 100 g or per 100 ml?" picker ever appears for this label | ☐ |
| 27.17 | `1`, `2`, `4` and `22` are never offered as carbohydrate values | ☐ |

### 27d — Interaction

| # | Check | ✓ |
|---|---|---|
| 27.18 | A verified automatic result reaches the calculator in **two taps** total | ☐ |
| 27.19 | An unverified but plausible result costs **at most one** confirmation tap | ☐ |
| 27.20 | Tapping the carbohydrate row once and being refused offers focused entry — no repeat loop | ☐ |
| 27.21 | Focused entry names the basis the label stated and shows **no** basis picker | ☐ |
| 27.22 | Tapping a sugars row is still refused, with a message saying why | ☐ |

### 27e — The permission rationale

| # | Check | ✓ |
|---|---|---|
| 27.23 | From Home → **Scan nutrition label** on a fresh install, the rationale reads "Camera access is needed to scan barcodes and nutrition labels." — it must **not** say "to scan a barcode" | ☐ |
| 27.24 | The same wording appears from the barcode scanner, and is correct there too | ☐ |

### 27f — Timing, reported cold and warm separately

Measure on the **debug** build and read `user-visible` from the trace summary, not `scan`. A stage
marked `*` is off the delivered-result path.

| | cold (first capture after opening the scanner) | warm (subsequent) |
|---|---|---|
| median | __________ ms | __________ ms |
| p95 | __________ ms | __________ ms |
| worst | __________ ms | __________ ms |

| # | Check | ✓ |
|---|---|---|
| 27.25 | Warm user-visible p95 ≤ **2 s** | ☐ |
| 27.26 | A cross-column-verified capture logs `SKIPPED_CROSS_COLUMN_VERIFIED` and runs **no** second ML Kit pass | ☐ |

### 27g — What the bundle must now say

The `selection.txt` of every capture gained a `recovery proposal` block.

| # | Check | ✓ |
|---|---|---|
| 27.27 | `=== recovery proposal ===` is present and lists the serving declaration | ☐ |
| 27.28 | Each offered choice shows its displayed value, basis, provenance and (where derived) what it came from | ☐ |
| 27.29 | Each **suppressed** number names the rule that removed it — e.g. "no column claims it, so it states no basis" | ☐ |
| 27.30 | On the damaged-header cracker, `72,0` appears as **suppressed**, not as an offer | ☐ |
| 27.31 | `serving:` in `diagnostics.txt` now says `header-weight=` and points at the recovery block — the sauce's `18 g` is reported there, not as `weight=none` | ☐ |
| 27.32 | Every exported archive opens, and every attempt inside it is complete | ☐ |

**Result:** ____________________ **Date:** ____________

## §28 — The truffle sauce, the decimal collapse, and Edit (sixth phone session, 1.0.3)

**This is the gate for the sixth-session patch. Nothing in it has been seen on a phone.**

The package prints `8,9 g / 100 ml` and `1,3 g / 15 ml portion`. Two captures on 2026-09-02 both
recognised those as `89` and `13` — the decimal separator did not survive on *any* value on the
label — and the app displayed **`89 g / 100 ml`**, ten times the printed figure. The two reached the
screen by different routes and each needs its own row below.

Every fix here is a refusal or a preserved fact. **No value is divided, shifted or repaired**, so a
capture that used to show `89` must now show *nothing* rather than `8.9`. A row showing `8.9` is a
**failure**, not a pass — it would mean something invented a value.

### 28a — The two failing captures (P0)

| # | Check | ✓ |
|---|---|---|
| 28.1 | Scan the truffle sauce repeatedly. **No capture, at any framing, ever displays `89 g / 100 ml`** — not automatically, not on a confirmation card, not as a recovery choice | ☐ |
| 28.2 | **No capture ever displays `8.9` that the app produced itself.** The figure may only appear because the user typed it | ☐ |
| 28.3 | A capture whose runs disagree shows the conflict screen, and continuing from it offers **neither** disputed value | ☐ |
| 28.4 | A capture where only one run reads anything does **not** offer a one-tap confirmation of it | ☐ |
| 28.5 | The screen reached instead is focused entry or row tapping — not a dead end, and not the camera | ☐ |
| 28.6 | Focused entry asks only for the number and states **`100 ml`**; it offers no `100 g`/`100 ml` picker | ☐ |
| 28.7 | Typing `8.9` there gives `8.9 g carbs / 100 ml`, and a portion calculates from it correctly | ☐ |

### 28b — Edit preserves the basis (P0)

| # | Check | ✓ |
|---|---|---|
| 28.8 | From a `/100 ml` reading, tap **Edit** → manual entry opens with **`100 ml`** selected | ☐ |
| 28.9 | From a `/100 g` reading, tap **Edit** → manual entry opens with **`100 g`** selected | ☐ |
| 28.10 | Edit opens with the amount **blank** — the rejected figure is not pre-filled | ☐ |
| 28.11 | Rotating the phone on that screen keeps the basis; it does not revert to `100 g` | ☐ |
| 28.12 | *Correct* (not *Edit*) still pre-fills the detected value, unchanged from before | ☐ |
| 28.13 | Manual entry reached from Home still opens on `100 g` — the default is unchanged where no label is involved | ☐ |

### 28c — Must not regress (rerun the fifth- and fourth-session captures)

| # | Check | ✓ |
|---|---|---|
| 28.14 | The green drink still reaches `0.5 / 100 ml` by distinct-run agreement | ☐ |
| 28.15 | The clean cracker still auto-advances `72 / 100 g` with cross-column support | ☐ |
| 28.16 | The damaged-header cracker still **never** displays `72 g / serving` | ☐ |
| 28.17 | The previously contradicted `12 / 100 g` is still absent everywhere | ☐ |
| 28.18 | The US linear panel still offers `6 g per 18 g serving` → `33.3 / 100 g` | ☐ |
| 28.19 | A legitimate high-carbohydrate label (flour, pasta, sugar — genuinely ~70–90 g/100 g) still reads and advances normally. **This is the row that proves the fix is not a magnitude threshold** | ☐ |
| 28.20 | A label printing clean decimals (`13,2 g`, `6,6 g`) still auto-advances as before | ☐ |
| 28.21 | Barcode scanning is unaffected | ☐ |

### 28d — Timing, reported cold and warm separately

No new OCR pass was added, so the expectation is *no change*. Record it rather than assuming it.

| # | Check | ✓ |
|---|---|---|
| 28.22 | Cold first scan after launch: ______ ms | ☐ |
| 28.23 | Warm subsequent scans, five samples: ______ ms | ☐ |
| 28.24 | The truffle sauce's refusal appears in the same time a reading used to — the refusal costs no extra recognition | ☐ |

### 28e — What the bundle must now say

| # | Check | ✓ |
|---|---|---|
| 28.25 | `selection.txt` carries a `cross-run dispute:` line naming the value **and** the run that read it differently | ☐ |
| 28.26 | It carries a `scale evidence :` line naming the candidate token, the paired token and the reason | ☐ |
| 28.27 | It carries a `correction hand-off:` line stating the basis and whether the amount was blank **on purpose** | ☐ |
| 28.28 | The `=== recovery proposal ===` block lists `89` as **suppressed**, with the rule that removed it — never as an offer | ☐ |
| 28.29 | Every exported archive opens and every attempt inside it is complete | ☐ |

**Result:** ____________________ **Date:** ____________

---

## §29 — The merged carbohydrate clause and focused entry (seventh phone session, 1.0.3)

Gate for the tap-path fix made after `Screen_Recording_20260902_141716` and `scan-evidence (8).zip`
(ten captures `141440`–`141703`; `140819` is an older retained capture and is **not** part of that
session). Debug APK `73CD91F0…93D5C`, `versionCode 4` / `1.0.3-debug`.

**What the seventh session established, and what it did not.** The sixth session's scale-safety work
**held on the device**: neither truffle capture displayed or offered `89`, the genuine
high-carbohydrate label still auto-advanced at `41 g / 100 ml`, and the cracker, drink and linear
sauce were all unchanged. The truffle label was nonetheless **unrecoverable** — the user tapped the
carbohydrate value, was told *"This looks like sugars or fibre"*, and returning left *Type it in*
disabled. §29 is the gate for that repair; §§26–28 remain open alongside it.

**Nothing in this section may be ticked from an emulator.** The changed surface is a tap on a frozen
photograph and the screen that follows it.

### 29a — The two truffle captures (the ones that failed)

Photograph the truffle-sauce label twice, framing as in the recording.

| # | Check | ✓ |
|---|---|---|
| 29.1 | **No capture displays or offers `89`, in any screen, at any point** | ☐ |
| 29.2 | No capture displays `8.9` either — the app must never manufacture the printed answer | ☐ |
| 29.3 | Tapping the carbohydrate **value** is **not** answered with "This looks like sugars or fibre" | ☐ |
| 29.4 | Tapping the damaged unit glyph immediately right of the value behaves the same way | ☐ |
| 29.5 | Tapping the word `Koolhydraten` / `Kohlenhydrate` behaves the same way | ☐ |
| 29.6 | Tapping inside `waarvan suikers` / `dont sucres` / `davon Zucker` **is** still refused as a child clause | ☐ |
| 29.7 | After an unsuccessful total-clause tap, **focused entry is offered** — *Type it in* is reachable, not disabled | ☐ |
| 29.8 | Focused entry asks for the value printed under **100 ml**, and shows **no basis picker** | ☐ |
| 29.9 | Going back to recovery and returning still leaves focused entry reachable (never permanently disabled) | ☐ |
| 29.10 | Typing `8.9` there reaches Quick Calculation as **`8.9 g carbs / 100 ml`** | ☐ |
| 29.11 | The `/100 ml` basis survives into the calculator and into Edit | ☐ |

### 29b — Nothing device-proven regressed

| # | Check | ✓ |
|---|---|---|
| 29.12 | The genuine high-carbohydrate drink still reaches **`41 g / 100 ml`** — as a one-tap confirmation on the photograph since §33; it no longer auto-advances, because corroboration is scale-invariant and cannot establish that `41` is not a collapsed `4,1`. The figure must still be **shown immediately**, never sent to recovery and never retyped | ☐ |
| 29.13 | Its bundle still records `automatic-verification: DISTINCT_OCR_AGREEMENT` | ☐ |
| 29.14 | The cracker still advances as **`72 g / 100 g`**, and never as `72 g / serving` | ☐ |
| 29.15 | A cracker capture whose header reads `1009` still refuses rather than fabricating a serving basis | ☐ |
| 29.16 | The US linear sauce still offers **`6 g / 18 g serving`** → `33.3 g / 100 g`, shown as *From 6 g per 18 g serving* | ☐ |
| 29.17 | The sauce never offers a fibre figure or a `% DV` number as a carbohydrate candidate | ☐ |
| 29.18 | The previously contradicted **`12 g / 100 g`** capture still never appears — automatically, as a proposal, or in recovery | ☐ |
| 29.19 | The drink still preserves `/100 ml` through manual correction | ☐ |
| 29.20 | A cross-column-verified capture still logs `strategy B : SKIPPED_CROSS_COLUMN_VERIFIED` — **no extra OCR pass** | ☐ |

### 29c — Timing, recorded cold and warm **separately**

The recording does not label cold and warm runs, so the sixth/seventh-session device figures cannot
settle this. `141642-529` measured **2142 ms** total with **1432 ms in parse**, against 501–864 ms
total and 55–199 ms parse for every other capture in the same session. The JVM work counters showed
that gap was a real defect — the prose reader normalized the whole vocabulary at every token
position — now fixed and measured at **51,792 → 2,060** normalize calls on that document.

**That is a JVM measurement. It is not a device measurement.** Record both below.

| # | Check | ✓ |
|---|---|---|
| 29.21 | Force-stop the app, scan the truffle label: record **cold** `scan` and `parse` from `meta.txt` | ☐ |
| 29.22 | Scan it again without leaving: record **warm** `scan` and `parse` | ☐ |
| 29.23 | The truffle capture's `parse` is now in the same range as its siblings (tens to low hundreds of ms), not ~1400 ms | ☐ |
| 29.24 | No capture in the run exceeds ~2 s user-visible on the second and later scans | ☐ |

Cold: `scan ______ms / parse ______ms`  ·  Warm: `scan ______ms / parse ______ms`

### 29d — Evidence

| # | Check | ✓ |
|---|---|---|
| 29.25 | Every exported archive opens and every attempt inside it is complete | ☐ |
| 29.26 | The truffle bundles carry a `correction hand-off:` line stating `basis=PER_100_ML` | ☐ |
| 29.27 | Their `=== recovery proposal ===` block lists every suppressed number with the rule that removed it | ☐ |
| 29.28 | No bundle from this run shows a `72 g / serving` or `89 g / 100 ml` proposal | ☐ |

**Result:** ____________________ **Date:** ____________

## §30 — The blind confirmation and the single-run scale hole (eighth phone session, 1.0.3)

Gate for the work done after `Screen_Recording_20260902_213037` and `scan-evidence (9).zip` (eight
captures `212902`–`213026`). Debug APK `C8FC97D9…808B1`, `versionCode 4` / `1.0.3-debug`.
**§§26–29 remain open alongside this section.**

### What the eighth session found

Two captures were correct, five recovered or refused, and **one was wrong and offered for one-tap
confirmation**. A red Lidl label printing **`7,2 g / 100 g`** was recognised as `12g`, and
`20260902-213005-691` shows the app's own account of why that reached the user:

```
automatic-verification: NONE — only one recognition run (PASS_A)
strategy B      : RAN_NO_READING
scale evidence  : established (no paired value in this clause to share a scale with)
final UI action : CONFIRM
```

The scale rule reported *absence of evidence* as *establishment*, so the reading was proposed. Worse,
the proposal was drawn over the **live camera preview** — the frozen photograph had already been
recycled — and the recording shows the package moved away by then. There was nothing on screen to
check the number against, so the tap could only ever mean "yes, there is a number there".

**`7,2` is recoverable from none of the four red-label recognitions** (`12g`, `724`, and twice
`carbono2g` with the `7,` fused into the nutrient word). Seven acquisition variants were measured on
all four captures — upscaling, grayscale, contrast, and their combinations. **None recovered `7.2`,
and three produced a confident *wrong* value instead**, including `72 g / 100 g`, ten times the
printed figure. No preprocessing was shipped; see `RedLabelAcquisitionExperimentTest`, whose KDoc
carries the full table. **Focused entry is the honest fallback for this label, and 30.6 is where that
is judged.**

**Nothing in this section may be ticked from an emulator.** The changed surface is which screen a
proposal is drawn on and which photograph is behind it.

### 30a — The red Lidl label: at least 10 varied captures

Vary distance, angle and lighting. Record what each capture did.

| # | Check | ✓ |
|---|---|---|
| 30.1 | **No capture ever displays `12 g / 100 g`** — automatically, on a proposal, or in recovery | ☐ |
| 30.2 | `724`, a bare `2`, and `6.1` are likewise never offered as the total | ☐ |
| 30.3 | Nothing ever displays `7.2` that OCR did not read — the app must not manufacture the right answer | ☐ |
| 30.4 | Every proposal that does appear is drawn on the **frozen photograph**, never over the live camera | ☐ |
| 30.5 | Each proposal shows an enlarged close-up of the row, and names the row it read (`From: …`) | ☐ |
| 30.6 | **At least 8 of 10 captures reach a correct `7.2 / 100 g`** within at most one confirmation — by automatic reading, by proposal, **or by focused entry**. If not, state plainly that recognition reliability for this label remains inadequate | ☐ |
| 30.7 | Focused entry preserves `/100 g` and shows **no basis picker** | ☐ |
| 30.8 | Rejecting a proposal keeps the photograph and does **not** prefill the rejected number | ☐ |
| 30.9 | The sugars `6,1`, fibre `0,8`, protein `1,8` and salt `0,25` never appear as a carbohydrate total | ☐ |

Captures reaching a correct `7.2`: ____ / 10.

### 30b — The green drink: at least 5 captures

| # | Check | ✓ |
|---|---|---|
| 30.10 | A clean capture still reaches **`0.5 g / 100 ml`** | ☐ |
| 30.11 | Its bundle still records `scale evidence : established (the candidate's own token carries a decimal separator)` | ☐ |
| 30.12 | A capture whose runs disagree still refuses **both** `0.5` and `5`, and says the readings conflicted | ☐ |
| 30.13 | `0.59` and `1.3` remain suppressed where unit accompaniment rejects them | ☐ |
| 30.14 | The `/100 ml` basis survives manual correction and reaches the calculator | ☐ |

### 30c — The cracker: at least 5 captures

| # | Check | ✓ |
|---|---|---|
| 30.15 | Still auto-advances as **`72 g / 100 g`**, and never as `72 g / serving` | ☐ |
| 30.16 | Its bundle still records a `CROSS_COLUMN` or `DISTINCT_OCR_AGREEMENT` verification | ☐ |
| 30.17 | A verified capture still logs `strategy B : SKIPPED_CROSS_COLUMN_VERIFIED` — no extra OCR pass | ☐ |

### 30d — Nothing device-proven regressed

| # | Check | ✓ |
|---|---|---|
| 30.18 | The genuine high-carbohydrate drink still reaches **`41 g / 100 ml`** — the integer control that must not be refused | ☐ |
| 30.19 | The US linear sauce still offers **`6 g / 18 g serving`** → `33.3 g / 100 g` | ☐ |
| 30.20 | The truffle label still never displays or offers `89`, and never manufactures `8.9` | ☐ |
| 30.21 | A clean decimal label reads correctly and advances as it did before | ☐ |

### 30e — Capture lifecycle (the P0-A surface)

| # | Check | ✓ |
|---|---|---|
| 30.22 | *Retake* from a proposal returns to the live camera and the next capture works | ☐ |
| 30.23 | Closing the scanner from a proposal leaks nothing and does not crash | ☐ |
| 30.24 | Rotating the device on a proposal keeps the photograph and the figure | ☐ |
| 30.25 | Rapid capture → retake → capture, five times, shows no stale result and no crash | ☐ |
| 30.26 | No `RejectedExecutionException`, `IllegalStateException: bitmap is recycled`, or OCR backlog in logcat | ☐ |

### 30f — Timing, recorded cold and warm **separately**

The eighth session's captures measured **379–638 ms** total (`user-visible` 379–630 ms) at capture
time, which excludes Strategy B. Nothing in this pass adds an OCR pass to the common path; the P0-B
change only alters which screen an *already computed* outcome is shown on. That is an argument, not a
device measurement — record both figures.

| # | Check | ✓ |
|---|---|---|
| 30.27 | Force-stop, scan: record **cold** `scan` and `parse` from `meta.txt` | ☐ |
| 30.28 | Scan again without leaving: record **warm** `scan` and `parse` | ☐ |
| 30.29 | **Warm user-visible p95 ≤ 2 s** across the whole run | ☐ |

Cold: `scan ______ms / parse ______ms`  ·  Warm p95: `______ms`

### 30g — Evidence

| # | Check | ✓ |
|---|---|---|
| 30.30 | A refused single-run reading records `scale evidence : UNSUPPORTED — …`, never `established (no paired value …)` | ☐ |
| 30.31 | A proposal held on the capture records `final UI action : CONFIRM_ON_CAPTURE` | ☐ |
| 30.32 | Every exported archive opens and every attempt inside it is complete | ☐ |
| 30.33 | No bundle from this run shows a `12 g / 100 g` proposal | ☐ |

**Result:** ____________________ **Date:** ____________

---

## §31 — Discarded correct readings (ninth phone session, 1.0.3)

**Status: OPEN.** §§26–30 remain open alongside it and nothing in this section closes any of them.

The 2026-09-03 session (`docs/Scan Evidence 03-09/`, nine bundles `084935`–`085128`, with
`Screen_Recording_20260903_085136`) produced **no wrong value at all** — the eighth session's `12`
proposal did not recur, which is the P0 fix holding. It exposed the opposite failure: on three
captures the app **held a correct reading and showed the user nothing**.

| bundle | package prints | Strategy B read | app showed |
|---|---|---|---|
| `084951-833` | `0,5 g / 100 ml` | `Confident 0.5/PER_100_ML` | recovery, `0.5g` suppressed |
| `085019-213` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | recovery, `2.8` suppressed |
| `085032-269` | `2,8 g / 100 g` | `Confident 2.8/PER_100_G` | recovery, `2.8` suppressed |

The cause was the automatic veto asking `mayAdvance`, which answers *false* for
`NeedsVerification` — the right answer to "may this skip confirmation" and the wrong answer to "may
this be shown at all". **Present at `c57aee0`; not a regression from the eighth-session patch.**

### 31a — The three captures that lost a correct reading

Each must now reach a confirmation **on the frozen photograph**, not the crop screen.

| # | Check | ✓ |
|---|---|---|
| 31.1 | White Dutch table (`Koolhydraten, waarvan 2,8 g`): a proposal reading **2.8 g per 100 g** appears | ☐ |
| 31.2 | That proposal is drawn over the **frozen photograph**, never the live preview | ☐ |
| 31.3 | The enlarged close-up shows the carbohydrate row it was read from | ☐ |
| 31.4 | Confirming reaches Quick calculation at `2.8 g carbs / 100 g` | ☐ |
| 31.5 | Green drink: a proposal reading **0.5 g per 100 ml** appears, or an honest refusal — never a wrong number | ☐ |
| 31.6 | No capture requires more than **one** unsuccessful step before focused entry | ☐ |

### 31b — Controls that must not move

| # | Check | ✓ |
|---|---|---|
| 31.7 | Cracker still advances automatically to `72 g / 100 g` | ☐ |
| 31.8 | Blue tub still advances automatically to `3.2 g / 100 g` | ☐ |
| 31.9 | Red Lidl label (`7,2 g`) **never** displays or proposes `12` | ☐ |
| 31.10 | Red Lidl label routes to focused entry with the basis preserved and the field **empty** | ☐ |
| 31.11 | Ingredient-only underside still refuses; the crop screen is still offered there | ☐ |

### 31c — Reliability gate (three captures per clear label)

Record every attempt. **A safe refusal is not a success.**

| Product | correct automatically | correct after 1 interaction | manual entry needed | wrong shown |
|---|---|---|---|---|
| White Dutch table `2,8` | ___ /3 | ___ /3 | ___ /3 | ___ /3 |
| Cracker `72` | ___ /3 | ___ /3 | ___ /3 | ___ /3 |
| Blue tub `3,2` | ___ /3 | ___ /3 | ___ /3 | ___ /3 |
| Green drink `0,5` | ___ /3 | ___ /3 | ___ /3 | ___ /3 |
| Red Lidl `7,2` | ___ /3 | ___ /3 | ___ /3 | ___ /3 |

**Release minimum:** zero wrong values shown or proposed · ≥80% correct automatically across the
clear supported set · no clear supported product failing automatically on all three attempts ·
focused entry reachable after at most one unsuccessful row tap. **If unmet, report NOT release-ready.**

### 31d — Evidence completeness

The Strategy B document was previously **never recorded**, so a session where Pass A and Strategy B
disagreed could not be replayed — which is exactly this session's shape, and why its diagnosis
required reconstruction. `strategyB.txt` is new.

| # | Check | ✓ |
|---|---|---|
| 31.12 | A capture where Strategy B ran writes `strategyB.txt` with its own element dump | ☐ |
| 31.13 | Its `elements=` header matches the number of element lines it prints | ☐ |
| 31.14 | A capture where Strategy B did not run writes no `strategyB.txt` (not an empty one) | ☐ |
| 31.15 | `final UI action` reads `CONFIRM_ON_CAPTURE` for a held proposal and `RECOVERY` for a withheld one | ☐ |
| 31.16 | Export the archive **after** the run and confirm it holds this run's bundles, not an earlier session's | ☐ |

### 31e — Timing, recorded cold and warm **separately**

This session measured ML Kit at **323–1974 ms** and total `scan` at **487–2458 ms** — materially
slower than the eighth session's 379–638 ms, on the same device and app version. That is unexplained
and is **not** attributed to this pass; record it rather than assuming it away.

| # | Check | ✓ |
|---|---|---|
| 31.17 | Force-stop, scan: record **cold** `scan`, `mlkit` and `parse` from `meta.txt` | ☐ |
| 31.18 | Scan again without leaving: record **warm** `scan`, `mlkit` and `parse` | ☐ |
| 31.19 | Warm `user-visible` p95 ≤ 2 s across the run | ☐ |

Cold: `scan ______ms / mlkit ______ms / parse ______ms` · Warm p95: `______ms`

**Result:** ____________________ **Date:** ____________

---

## §32 — Deterministic proposals and the recovery leak (tenth pass, 1.0.3)

**Status: OPEN — this is the release gate.** §§26–31 remain open alongside it. Nothing in this
section closes any of them, and **the verdict stays NOT release-ready until every row below is
ticked on physical hardware.**

Evidence for the analysis: `scan-evidence (10).zip`, SHA-256
`3391e49c41d387090b4638aee3010240f0a3d0fe6026aed46c0e41f54f382c43` — the nine correct
`20260903-084935-802`..`085128-913` bundles. Those bundles were produced by the **previous** APK, so
they establish what was wrong, never that it is fixed.

**Artifact to test — UPDATED by the eleventh pass (2026-09-03). Pin THIS hash to the device run:**
`app/build/outputs/apk/debug/app-debug.apk` (also on the Desktop as `JustTheCarbs-debug.apk`),
**89,848,583 bytes**, SHA-256
`f39656454686aed3c35c2f34d2348847d2decde9c0444304aac3520d4a45d72a`, `versionCode=4` /
`1.0.3-debug`; permissions unchanged (CAMERA, INTERNET, ACCESS_NETWORK_STATE).

*(Superseded: the tenth pass's APK was 89,846,131 bytes, SHA-256 `48adb3a7…37224f`. Do not run §32
against it — it predates the three corrections below.)*

### What the eleventh pass changed beneath these rows (2026-09-03)

The rows in this section are **unchanged and still the gate**. Three defects were fixed underneath
them, each reproduced by a test that failed before the fix:

- **P1a — the scale rule could be bypassed by corroboration.** `ReadingEligibility` tested
  corroboration first and returned eligible on it outright. Neither corroboration route this app has
  observes absolute scale (`CrossColumnRatioCheck` compares a ratio, which is scale-invariant;
  `DISTINCT_OCR_AGREEMENT` compares two recognitions of the same pixels, which can lose the same
  separator twice), so a demonstrated ambiguity is now checked **before** corroboration.
  `mayAdvanceVerified` also never consulted eligibility at all, and now does.
- **P1b — Strategy B's highlight was drawn in the wrong space.** Its element boxes are crop-local and
  were consumed as full-frame, so the box was short by **exactly the crop origin**. Translation now
  happens once, at presentation.
- **P2 — the evidence document was the unfiltered one**, so a neighbouring panel's `62 g` was visible
  to stages reasoning about "this table".

**Rows 29.12 and 30.18 stood unchanged in the eleventh pass, and 29.12 was NARROWED in the
thirteenth — see §33.** The reasoning recorded here was that a lone separatorless integer is
`Unsupported` rather than `Ambiguous`, so corroboration could still admit it and `41 g / 100 ml`
would auto-advance.

The thirteenth session measured what that permits: `20260904-081421-421` skipped both confirmations
with `scale evidence: UNSUPPORTED` in its own bundle. Both verification routes are scale-invariant,
so the same reasoning admits a collapsed `1,1` identically. Corroboration therefore no longer
authorises *skipping* the confirmation.

**It still authorises showing the figure**, which is the half these rows exist to protect: `41` is
proposed immediately on the frozen photograph, one tap from the calculator, never sent to recovery
and never retyped. Row 30.18's wording ("still reaches") already describes that; row 29.12 said
"auto-advances" and now says the same thing as 30.18. Both rows remain **unticked** — no
hardware-verified behaviour was contradicted.

**The OCR corpus was NOT re-run against this build.** It fails 17/39 on the `carbscan` emulator at
clean `c57aee0` as well, so an emulator run measures the emulator; CLAUDE.md records **39/39 on real
hardware**. Row 32.18 below is where that is settled.

### What changed, and what each row is testing

1. **The green drink now has one exact expected outcome.** Its Pass A resolves **no column** — the
   printed `PER: 100 ml` came back as `100` + `m`, and `m` is not a unit spelling — so `0.5` was
   rejected with `no column` and the resolver returned `Nothing`. That is why its bundle differs
   from the white table's `NeedsVerification` despite both showing `RECOVERY`. Strategy B read the
   header cleanly and established all four facts, so the required outcome is a **proposal**, not a
   refusal.
2. **A tap is no longer treated as evidence about decimal scale.** Recovery and the automatic path
   now share one eligibility decision, so they cannot contradict each other about the same
   candidate.

### 32a — The green drink: one outcome, no disjunction

| # | Check | ✓ |
|---|---|---|
| 32.1 | Green drink (`0,5 g / 100 ml`): a proposal reading **0.5 g per 100 ml** appears | ☐ |
| 32.2 | It is drawn over the **frozen photograph**, never the live preview | ☐ |
| 32.3 | Confirming reaches Quick calculation at `0.5 g carbs / 100 ml` | ☐ |
| 32.4 | It is **never** advanced automatically — a confirmation step is always shown | ☐ |

### 32b — The red label: no wrong value by ANY route

The package prints `7,2 g / 100 g`. No recognition of it contains `7.2`, so nothing may display it.

| # | Check | ✓ |
|---|---|---|
| 32.5 | No capture ever displays or proposes `12` | ☐ |
| 32.6 | **Tapping the carbohydrate row** offers neither `12` nor `72` — the list is empty or refuses | ☐ |
| 32.7 | Tapping *every* element of that row (word, value, unit) offers no figure at all | ☐ |
| 32.8 | Focused entry is reachable, `/100 g` preserved, and the field is **empty** | ☐ |
| 32.9 | Nothing anywhere displays `7.2` — it is in no recognition and may not be invented | ☐ |

### 32c — Controls that must not move

| # | Check | ✓ |
|---|---|---|
| 32.10 | White Dutch table proposes **2.8 g per 100 g** on the frozen photograph | ☐ |
| 32.11 | Cracker still advances automatically to `72 g / 100 g` | ☐ |
| 32.12 | Blue tub still advances automatically to `3.2 g / 100 g` | ☐ |
| 32.13 | A US linear panel (Korean sauce shape) still offers `6 g / 18 g serving` when tapped | ☐ |
| 32.14 | Ingredient-only underside still refuses and still offers the crop screen | ☐ |

### 32d — Reliability gate: three captures per label

Record **every** attempt, including retries. **A safe refusal is not a success**, and a wrong value
shown or offered by any route fails the gate outright.

| label | capture | outcome (auto-correct / confirmed-correct / focused-entry / **wrong**) |
|---|---|---|
| green drink `0,5 / 100 ml` | 1 | ____________ |
| green drink | 2 | ____________ |
| green drink | 3 | ____________ |
| white table `2,8 / 100 g` | 1 | ____________ |
| white table | 2 | ____________ |
| white table | 3 | ____________ |
| red Lidl `7,2 / 100 g` | 1 | ____________ |
| red Lidl | 2 | ____________ |
| red Lidl | 3 | ____________ |

**Totals — report these four separately, never as one pass rate:**

- automatic-correct: ______
- confirmed-correct: ______
- focused-entry (honest refusal): ______
- **wrong value shown or offered: ______  ← must be 0**

### 32e — Timing, cold and warm reported separately

Cold = first scan after a force-stop. Warm = subsequent scans in the same session. Read
**`user-visible`** from the trace summary, not `scan`: stages marked `*` are off-path, and the debug
build carries an evidence writer a user never pays for.

| # | Check | ✓ |
|---|---|---|
| 32.15 | Cold `user-visible` recorded for the first capture | ☐ |
| 32.16 | Warm `user-visible` recorded for at least 8 further captures | ☐ |
| 32.17 | Warm p95 ≤ 2 s | ☐ |

Cold: `scan ______ms / mlkit ______ms / parse ______ms` · Warm p95: `______ms`

The ninth session measured ML Kit at **323–1974 ms** against the eighth session's 379–638 ms on the
same device and app version, with two captures over 2 s. That is unexplained and was not re-measured
in this pass; 32.15–32.17 are where it is settled.

### 32f — The eleventh pass's three corrections, on hardware

Added 2026-09-03. Each row tests a defect fixed in JVM and never seen on a device.

| # | Check | ✓ |
|---|---|---|
| 32.18 | **OCR corpus 39/39 on hardware** against this APK — the emulator's 17/39 measures the emulator, not the diff | ☐ |
| 32.19 | **P1b:** on a capture Strategy B answers, the highlight sits on the **correct row** in the photograph — not offset upward by the crop origin | ☐ |
| 32.20 | The row close-up in the verification screen shows the **carbohydrate row**, not a neighbouring one | ☐ |
| 32.21 | **P2:** on a label with a second panel in frame, no figure from that panel is proposed or offered | ☐ |
| 32.22 | **P1a:** the truffle label (`89`/`13` separatorless pair) is refused **even when both routes verify it** | ☐ |
| 32.23 | The `41 g / 100 ml` integer case still **auto-advances** — the reordering must not have cost it | ☐ |
| 32.24 | A uniform 3-row decimal collapse never advances | ☐ |

Rows 32.22–32.24 are the behaviour matrix's three scale cases. 32.23 is the control: if it fails, the
ambiguity/corroboration ordering has been made stricter than intended and `Unsupported` is being
treated as `Ambiguous`.

**Result:** ____________________ **Date:** ____________

---

## §33 — Recall, scale and the tap (thirteenth pass, 1.0.3, debug APK `F618E982…01740`)

Built from the thirteenth phone session, `docs/Scan evidence 04-09 1st test/` — nineteen captures
across twelve packages, with `Screen_Recording_20260904_081200`. Ground truth for every row below was
read **off the photograph**, not off the parser: three captures in that session recognise a
confidently wrong number, and two of them would look like successes if the app's own answer were
taken as truth.

Debug APK `app-debug.apk`, 89,569,105 bytes, SHA-256
`f618e98208c0fd16d7331c132ba69eb2050097ab8ae1acd9ba887b77f3701740`, `versionCode=4` /
`1.0.3-debug` read from the APK with `aapt2 dump badging`. Permissions unchanged: CAMERA, INTERNET,
ACCESS_NETWORK_STATE.

Pin **this** hash to the device run. It is the artefact built after the negative controls were
restored **and** after the still-path executor-rejection guard was added (see row 33.19), so it is
the tree the rows below describe.

### What changed, and what each row is testing

1. **A corroborated reading may no longer skip both confirmations on an unestablished scale.**
   `20260904-081421-421` advanced automatically with `scale evidence: UNSUPPORTED` and
   `automatic-verification: DISTINCT_OCR_AGREEMENT` in the same bundle. The jar genuinely prints
   `11 g`, so the value was right — and both verification routes are scale-invariant, so the same
   reasoning would have admitted a collapsed `1,1`. **The figure is still shown**, immediately, on
   the frozen photograph; only the permission to bypass that tap is withdrawn.
2. **A capture whose row and basis are known asks for the digits, not for a crop.**
   `20260904-081307-240` prints `Koolhydraten 6,2 g` in large flat type; the `g` came back as a `0`
   (`6,20`), the row and the `Ø/100 ml` column were both resolved, and the app opened the **crop**
   screen — a rectangle the user cannot usefully change, over a row already located.
3. **A tap inside an element's box belongs to that element.** On the yoghurt tub the child label
   `waarvan` `[311,1948,486,2015]` sits inside the total's `Koolhydraten/Glucides`
   `[297,1884,802,1988]` both vertically and horizontally. A tap on the sugars word resolved to the
   **total** row, so a user deliberately tapping sugars was handed the total's candidates.
4. **The assisted screen is theme-correct.** It forced `Color.Black` with Material controls left on
   their defaults, so in Light theme the focused-entry field's label, outline, cursor and digits
   rendered dark-on-black.

### Rows

| # | Check | ✔ |
|---|---|---|
| 33.1 | **No capture, on any package, displays or offers a figure the package does not print** | ☐ |
| 33.2 | The red Lidl carton (`7,2 g / 100 g`) never displays or offers **`12`** through any route | ☐ |
| 33.3 | The mayonnaise (`1,3 g / 100 ml`) never displays or offers **`13`** through any route | ☐ |
| 33.4 | The peanut butter (`11 g / 100 g`) reaches the user — as a **one-tap confirmation on the photograph**, not recovery, not retyped | ☐ |
| 33.5 | A label printing a decimal (yoghurt `3,2`, fritessaus `13,2`) still **auto-advances** | ☐ |
| 33.6 | The green Lidl drink (`6,2 g / 100 ml`) opens **focused amount entry** — "type the number printed under 100 ml" — and **not** the crop screen | ☐ |
| 33.7 | That focused-entry screen offers **no basis picker**; the basis reads per 100 ml and cannot be changed | ☐ |
| 33.8 | Typing `6.2` there reaches Quick Calculation as `6.2 g carbs / 100 ml` | ☐ |
| 33.9 | Tapping the **carbohydrate number** on a frozen clear table selects the total row on the **first** tap | ☐ |
| 33.10 | Tapping the **sugars number** is refused, and the screen says why | ☐ |
| 33.11 | Tapping the **sugars word** on a label whose child clause is indented under its parent is refused — it must not be answered with the total's candidates | ☐ |
| 33.12 | No tap loop: one deliberate tap either completes, or leads to focused entry / manual completion. Never the same screen twice | ☐ |
| 33.13 | **Light theme** — focused `/100 g` and `/100 ml` fields: label, outline, cursor, typed digits, buttons and helper copy all legible | ☐ |
| 33.14 | **Dark theme** — the same six, all legible | ☐ |
| 33.15 | In both themes the photograph is still clearly readable behind/above the controls | ☐ |
| 33.16 | Warm scans reach the first actionable screen in **≤ 2 s** (report cold and warm separately) | ☐ |
| 33.17 | The progress indicator does not visibly stutter while a large label is parsed (the parse now runs off the main thread) | ☐ |
| 33.18 | The nine-photograph OCR corpus on **real hardware** — record pass/fail per fixture | ☐ |
| 33.19 | **Lifecycle race** — capture, then immediately Retake, then capture again: no crash, no stale photograph, the second result is the one shown | ☐ |
| 33.20 | **Lifecycle race** — capture, then immediately leave the scanner: no crash, and re-entering the scanner works normally (the still path releases its bitmap even when its listener is rejected at dispatch) | ☐ |

### Cold and warm timing

Report separately. The thirteenth session's own bundles measured `user-visible` at 298–1113 ms
(median ~500 ms), so any figure materially above that is a regression rather than the expected cost.

| run | package | cold/warm | user-visible ms | action reached |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

### What this pass could NOT verify

**No physical device was attached.** Everything measured for §33 is JVM plus the `carbscan`
emulator (`ro.kernel.qemu=1`, `ro.hardware=ranchu`), whose virtual camera cannot produce a nutrition
table — so the *automatic* accept path was not exercised end to end and **no row above is ticked**.

The nine-photograph OCR corpus was run on that emulator and measured **29/39, against a clean-`7617da5`
control of 22/39 on the same emulator in the same session** — zero new failures, seven fixed,
compared programmatically by test name. That is a same-emulator comparison, not device evidence:
CLAUDE.md records this corpus as 39/39 on real hardware, and row 33.18 is where that is settled.

**Result:** ____________________ **Date:** ____________

---

## §34 — The separatorless pair, from both sides (eighteenth session, 1.0.3)

**Status: OPEN.** §§26–33 remain open alongside it; nothing here closes any of them.

Evidence for the analysis: `docs/Scan Evıdence 4th test/`, thirteen bundles
`20260904-160320-756`..`160740-278`, Samsung SM-S928B, API 36, app `1.0.3-debug`. **Those bundles
were produced by the previous APK**, so they establish what was wrong and never that it is fixed.

**Artifact to test — pin THIS hash to the device run:**
`app/build/outputs/apk/debug/app-debug.apk`, **89,601,873 bytes**, SHA-256
`f9c96c81542dc74d99a9dd6ac74432e1a1faa65f73356b716bedea9c101488d8`, `versionCode=4` /
`1.0.3-debug` read from the APK with `aapt2 dump badging`. Permissions unchanged (CAMERA, INTERNET,
ACCESS_NETWORK_STATE).

*(A debug build is not byte-reproducible, so **rebuild and re-hash before the device run** if the
tree has moved since. What matters is that the APK on the phone is the one the rows below are
ticked against.)*

### What changed beneath these rows

`ScaleAmbiguity` looked for a candidate's paired value only among elements **to the right of** it, so
on a two-column row the left cell saw the pair and was refused while the right cell saw nothing and
was offered. Three captures in this session recorded it:

| capture | recognised carbohydrate row | suppressed | was offered |
|---|---|---|---|
| `160639-565` | `Koolhydraten/Glucides 46 g 12 g` | `46` | **`12` → 12 g / serving** |
| `160501-961` | `koolhydraten, waarvan 159 18` | `159` | **`18` → 18 g / serving** |
| `160532-812` | `koolhydraten, waarvan 15 g 38g` | `15` | **`38g` → 38 g / 25 g** |

A common rescaling is a property of the **pair** and is symmetric, so both members are now refused.
**No value is repaired** — `46` never becomes `4.6` — and each routes to focused entry with the
photograph, the highlighted row and the basis preserved.

### The rows

The three products above are the ones whose failures motivated this. Rows 34.1–34.3 are what decide
whether the leak is actually closed on a phone.

| # | Check | ✅ |
|---|---|---|
| 34.1 | Scan the **protein bar** (`46 g / 100 g`, `12 g / 25 g reep`). No screen — proposal card or recovery list — ever shows **`12`** as a selectable or pre-filled carbohydrate figure | ☐ |
| 34.2 | Same package: **`46`** likewise never appears as a selectable or pre-filled figure (the control — it was already suppressed and must stay so) | ☐ |
| 34.3 | Same package: the app routes to **focused entry**, the frozen photograph is on screen, the carbohydrate row is highlighted, and the basis the label stated is preserved (no "per 100 g or per 100 ml?" question) | ☐ |
| 34.4 | Typing the printed `46` into focused entry reaches the calculator with `46 g / 100 g` | ☐ |
| 34.5 | Repeat 34.1–34.4 for the **crisps/serving package** (`159`/`18` and `15`/`38g` captures): neither member of either pair is ever offered | ☐ |
| 34.6 | **The Korean sauce control still works** — a label printing `Serv. size: 1 Tbsp (18 g)` still offers `6 g / 18 g serving`. A lone separatorless value under a *declared* serving is unaffected; only a demonstrated pair is refused | ☐ |
| 34.7 | **A single-column label still reads** — a package printing one carbohydrate value with no second column (e.g. the `41 g / 100 ml` drink) still reaches a proposal, not a refusal | ☐ |
| 34.8 | **A separated value is unaffected** — any label whose carbohydrate token keeps its decimal separator (`0,5 g`, `2,5 g`) still proposes exactly as before | ☐ |
| 34.9 | Scan the **Fanta** (`0,5 g / 100 ml`, `1,3 g / 250 ml`). The figure shown is `0.5 / 100 ml`; **`13` and `1.3` are never offered as the per-100 figure** | ☐ |
| 34.10 | Across every capture in the session: **zero wrong carbohydrate values shown or offered through any route** | ☐ |
| 34.11 | Export the evidence bundle and confirm the recovery list no longer prints an `offered` line for a member of a pair whose sibling it `suppressed` | ☐ |

### Cold and warm timing

Report separately. This session's own bundles measured `user-visible` at 477–1443 ms, so any figure
materially above that is a regression rather than the expected cost.

| run | package | cold/warm | user-visible ms | action reached |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

### What this pass could NOT verify

**No physical device was attached.** Everything is JVM plus the `carbscan` emulator
(`ro.kernel.qemu=1`, `ro.hardware=ranchu`), whose virtual camera cannot produce a nutrition table —
so the automatic accept path was not exercised end to end and **no row above is ticked**.

The connected OCR suite was run on that emulator and measured **33 tests / 9 failures, against a
clean-`47ad5d1` control of 33 / 9 on the same emulator in the same session** — identical by name,
zero new failures. Before this pass the same run measured 12 failures; the three that went were
stale `FromRow` provenance contracts, not values. That is a same-emulator comparison, **not device
evidence**: CLAUDE.md records this corpus as 39/39 on real hardware, and row 33.18 is where that is
settled.

**Result:** ____________________ **Date:** ____________

## §35 — Startup hardening: onboarding flash, OCR basis defaults, permission recovery, evidence concurrency (2026-09-04/05, 1.0.4)

**Status: OPEN.** §§26–34 remain open alongside it; nothing here closes any of them. Scope for this
pass was narrowed by the owner to the release-blocking safety items only: startup state, OCR basis
defaults, camera-permission recovery and `LiveEvidenceBuffer` concurrency. Usage-semantics rewrite,
UI-backdrop fixes and Settings accessibility work were explicitly deferred and are not addressed
here.

### What changed

- **Startup**: `MainActivity` now holds the splash screen (`setKeepOnScreenCondition`) until the
  first real DataStore value arrives, via a new `StartupState` sealed interface (`Loading`/`Ready`).
  A returning user can no longer see Onboarding flash before the real `hasSeenOnboarding` value
  loads, because nothing is rendered from `AppSettings()`'s synthetic default — the screen paints a
  neutral black background instead, under the splash, until `Ready`. `OnboardingViewModel.complete()`
  is now `suspend`, mutex-guarded for idempotency, and navigation to Home only happens after the
  write lands (UI-layer double-tap guard on top).
- **OCR basis defaults removed**: the one remaining unsafe `NutritionBasis.valueOf(...)` call (the
  saved-state label-comparison handoff in `JustTheCarbsNavHost`) is replaced with safe
  `entries.firstOrNull` parsing, extracted into a pure `parseDetectedLabelReading` function; a
  malformed/missing basis now reports `ProductViewModel.reportLabelHandoffFailure()` (a dismissible
  dialog) rather than crashing or silently defaulting to grams. `LabelScannerScreen`'s *Correct*
  action (`onCorrectValue`) now threads `NutritionBasis?` end to end from the one button click that
  used to hard-code `PER_100_G` for an unresolved basis. `ManualEntryUiState.basis` is now
  `NutritionBasis?`; `canSave` requires it non-null; the basis chip row shows neither chip selected
  and an explanatory line when reached from an OCR value with no established basis, and Save stays
  disabled until the user picks one. Ordinary Home-initiated manual entry (`ocrCarbs` blank) is
  **unaffected** — it still opens on grams, exactly as before; the rule only refuses to default when
  a scanned figure is genuinely being carried in with no resolvable basis.
- **Camera-permission recovery**: a new shared `CameraPermissionState` (five values: `Granted`,
  `NotRequested`, `DeniedCanAskAgain`, `PermanentlyDenied`, plus the `ON_RESUME`-triggered recheck
  that moves `PermanentlyDenied` back to `Granted`) and `rememberCameraPermissionController()`,
  used identically by `ScannerScreen` and `LabelScannerScreen` — previously each screen had its own
  copy of granted/requested tracking, and **both** went dead the moment a request was answered: no
  way back to the system dialog for a "not this time" denial, no way to the app's Settings page for
  a "never ask me again" one. A shared `CameraPermissionRationale` composable now shows *Allow
  camera* for the first two states and *Open Settings* (via
  `ACTION_APPLICATION_DETAILS_SETTINGS`) for the permanent-denial state; *Enter manually* is present
  in every state.
- **`LiveEvidenceBuffer` concurrency**: the buffer is read (`stableConsensus`/`asEvidence`) from a
  background dispatcher inside `LabelScannerScreen`'s still-recognition coroutine
  (`withContext(Dispatchers.IO)`) while written (`record`/`clear`) from the main thread — a genuine
  concurrent-access hazard on a plain unsynchronized `ArrayDeque`, not a hypothetical. Every method
  touching the deque is now `synchronized`. Observations also carry a `sessionId`, stamped from the
  existing `captureSession` generation counter (already bumped on dispose/retake/every new capture);
  `stableConsensus`/`asEvidence` only ever consider observations from the requested session, so a
  live frame from an abandoned attempt or a different package swept past can never corroborate a
  later capture.

### Verified this pass

JVM full suite, `--rerun-tasks`: **1715/1715** (0 failures, 0 errors, 0 skipped, 170 XML files) —
up from the pre-pass baseline by the new `StartupStateTest`, `CameraPermissionStateTest`,
`LabelHandoffParsingTest`, plus the new `ManualEntryViewModelTest`/`OnboardingViewModelTest`/
`LiveEvidenceBufferTest` cases described above. Lint: **0 errors, 23 warnings** (unchanged baseline;
one transient `ExperimentalDetector` internal crash on the first `lintDebug` run reproduced the
documented lint-bug pattern from an earlier pass and cleared on retry with no code change — see
CLAUDE.md's "A lint crash that is a lint bug, not a code defect"). `git diff --check` clean.
`assembleDebug` and `compileDebugAndroidTestKotlin` both succeed.

**Connected OCR corpus** (`RealImageOcrTest` + `ProductionStillPipelineTest` +
`SelectedTableProductionTest` + `EvidencePipelineProductionTest`, 39 tests) on the `carbscan`
emulator: **10 failures**, and **a clean-HEAD (`266338b`) worktree control on the same emulator in
the same session measured the identical 10 by name — zero differences**, confirmed programmatically
(sorted-list diff, not eyeballed). None of this pass's changes touch OCR recognition or parsing code
— `LiveEvidenceBuffer`'s change is a concurrency wrapper, functionally inert for the single-threaded
default-session usage every existing caller and fixture exercises — so this result is expected and
is not evidence of a regression.

**Read honestly, not just compared.** Two of the ten failures (`stokbroodStillReadsFortySix…` /
`stokbroodStillResolvesThroughTheEvidencePipeline`, and `noFixtureGainsAConfidentWrongValueThrough…`)
show the emulator's ML Kit recognizing `6.4` where the fixture prints `46`, and
`kinderStillReadsItsPerPieceRelationship` shows `3` where the fixture states `6.7` — a **wrong value
confidently produced**, not merely a safe refusal or a stale test contract. The parser is not at
fault: `analyse(...)` in these tests runs the real ML Kit recognizer against the fixture bitmap on
*this* emulator, and the wrongness originates entirely in what the recognizer reports, which the
parser then correctly interprets. This is the same class of degradation this file already records
for other fixtures (`Koolhydraten` → `nlhioonorate`, `Glucides` → `Gucides`) and CLAUDE.md records
this exact corpus as **39/39 on real hardware** — so it is read as further evidence that the
emulator's virtual camera pipeline reads these particular photographs worse than real optics, **not**
as evidence the app's OCR safety logic is unsound. It remains true that this has not been
re-confirmed against *this* diff on real hardware, and that is the gate below.

### NOT verified, and this is the gate

**No physical device was attached.** Everything above is JVM plus the `carbscan` emulator
(`ro.kernel.qemu=1`, `ro.hardware=ranchu`, `ro.build.characteristics=emulator`), whose virtual camera
cannot exercise the real capture/permission/OCR flow end to end. In particular:

- The onboarding-flash fix, the camera-permission recovery flows (temporary denial → re-request,
  permanent denial → Settings → return with grant), and the `LiveEvidenceBuffer` session-boundary
  behaviour under a real capture/retake sequence have **not** been seen on hardware.
- The OCR corpus's 10 emulator failures have not been re-run on real hardware against this diff to
  confirm the standing 39/39 figure still holds — it should, since no recognition or parsing code
  changed, but that is an argument, not a measurement.

| # | Check | ✅ |
|---|---|---|
| 35.1 | Fresh install (or Settings → clear data), no prior onboarding: app opens directly to Onboarding, no flash of Home first | ☐ |
| 35.2 | Returning user (onboarding already completed): app opens directly to Home, no flash of Onboarding first | ☐ |
| 35.3 | Tap *Get started* rapidly twice: navigates to Home exactly once, no crash, no double-write artefact | ☐ |
| 35.4 | Scan a label whose basis the app cannot establish, tap *Correct*: manual entry opens with **neither** g/ml chip selected, Save disabled, explanatory text visible | ☐ |
| 35.5 | From 35.4, tap the g chip: Save becomes enabled; saving stores the product at the chosen basis | ☐ |
| 35.6 | Ordinary manual entry from Home (no scan): opens on grams as before, Save enabled once name+carbs are filled | ☐ |
| 35.7 | Deny camera permission once (still eligible for the system dialog): the screen offers *Allow camera* again, not only *Enter manually* | ☐ |
| 35.8 | Deny camera permission a second time (system stops offering its dialog): the screen now offers *Open Settings*, not a dead *Allow camera* button | ☐ |
| 35.9 | From 35.8, tap *Open Settings*, grant the permission there, press back: the scanner recognises the grant and shows the camera without needing to leave and re-enter the screen again | ☐ |
| 35.10 | Both `ScannerScreen` (barcode) and `LabelScannerScreen` (label) behave identically for 35.7–35.9 | ☐ |
| 35.11 | Scan a label, retake mid-recognition several times in quick succession: no crash, no `ConcurrentModificationException` in logcat, no reading from an abandoned attempt appearing on the new capture | ☐ |
| 35.12 | Cold and warm timing for a normal label scan is unchanged from the standing figures (no regression from the `synchronized` guards) | ☐ |

## §36 — OCR evidence lifecycle gate (2026-09-05 pass)

Everything below requires a physical device; none of it can be verified on the `carbscan` emulator,
whose virtual camera cannot exercise the pre-shutter live-evidence path meaningfully.

- [ ] 36.1 Scan a label with the camera held steady on the table for ~1 second before tapping
      capture. Confirm (via `adb logcat` diagnostics, if evidence export is enabled in debug) that
      the frozen live snapshot at shutter time reports a non-zero observation count.
- [ ] 36.2 Repeat 36.1 but deliberately move the phone away from the table in the instant before
      tapping the shutter (camera sees a different scene at the moment of the tap). Confirm the
      frozen snapshot reflects what was seen immediately before the tap, not an even earlier stable
      reading — i.e. confirm the suffix-consensus rule actually invalidates on a recent
      disagreement rather than reporting a stale earlier agreement.
- [ ] 36.3 Retake (tap Retake/Retry) after a first capture, then scan a *different* product. Confirm
      the second scan's result is never influenced by the first product's live evidence (aim-epoch
      isolation).
- [ ] 36.4 Reproduce the documented Hellmann's `1,3 g / 100 ml` case (or an equivalent
      separatorless-integer misread) on a physical device. Confirm the app never offers `13` (or
      the equivalent misread digit run) for one-tap confirmation. Confirm it instead reaches
      focused entry with the frozen photograph, the row highlighted, and the basis preserved.
- [ ] 36.5 Confirm the four documented canary labels (sondey, kinder, yoghurt, stokbrood) still
      auto-advance correctly on a physical device after this pass, and that grated cheese, witte
      kaas, the jar, and the lid all still correctly refuse (per the existing
      `EvidencePipelineProductionTest` table in CLAUDE.md's "OCR quick calculation" section).
- [ ] 36.6 Deny camera permission temporarily, retry, grant it, and confirm the scanner recovers.
      Deny permanently, open Settings, grant it there, return to the app, and confirm the
      `ON_RESUME` recheck picks up the grant without requiring the screen to be closed and reopened.
- [ ] 36.7 Force a `SettingsRepository` write failure during onboarding (if a debug hook exists for
      this; otherwise this row stays open pending a way to simulate it on-device) and confirm the
      *Get started* button re-enables with a visible error, and that tapping it again after fixing
      the underlying condition successfully navigates to Home.

**Result:** ____________________ **Date:** ____________
