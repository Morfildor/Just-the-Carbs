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
