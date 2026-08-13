# Manual QA checklist — CarbScan

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
| 6.1 | Whole gram dominates; decimal legible beneath | ☐ |
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

## 9. Nutrition-label OCR (§29) — *unverified against real packaging*

| # | Check | Pass |
|---|---|---|
| 9.1 | Dutch label: *Koolhydraten* row detected | ☐ |
| 9.2 | English label: *Carbohydrate* row detected | ☐ |
| 9.3 | **"waarvan suikers" is never taken as the total** | ☐ |
| 9.4 | Nothing is auto-accepted — always *Use* / *Edit* | ☐ |
| 9.5 | Two plausible columns → candidates shown, app does not choose | ☐ |
| 9.6 | Unreadable label → honest failure plus manual entry | ☐ |
| 9.7 | No image is saved to the gallery | ☐ |

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
| 11.3 | Card shows name and `65 g → 31 g`, nothing irrelevant | ☐ |
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
