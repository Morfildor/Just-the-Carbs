# Manual QA — one checklist

A single-pass consolidation of `docs/manual-qa.md`, which has grown to 882 lines across 40+ sections
as features accumulated. **That file stays authoritative** for the reasoning behind each row, the
measured values, and the history of why a check exists. This one is the sheet you carry while
holding the phone.

Rows are merged where several sections asked the same question of different features. Anything that
can only be answered on real hardware is marked **HW**. Anything the automated suites already cover
is not repeated here.

**Device:** ____________________ **Android:** ______ **Build:** ______ **Tester/date:** ______

---

## A. Install, permission, lifecycle

| # | Check | Pass |
|---|---|---|
| A1 | Cold start is fast; Home shows the empty state, not a spinner; no permission asked at launch | ☐ |
| A2 | Camera permission requested only on opening a scanner | ☐ |
| A3 | Denying leaves the app fully usable via manual entry; no dead end after "Don't allow" twice | ☐ |
| A4 | Granting later works without reinstalling | ☐ |
| A5 | Rotation, backgrounding and returning preserve the in-progress calculation and meal | ☐ |
| A6 | Reopening either scanner repeatedly works — no leaked camera | ☐ |

## B. Barcode scanning

| # | Check | Pass |
|---|---|---|
| B1 | EAN-13 and EAN-8 both decode, continuously, with no shutter button | ☐ |
| B2 | Single haptic on success; rapid re-scan does not double-navigate | ☐ |
| B3 | Crumpled, curved, shiny packaging and dim lighting still scan | ☐ |
| B4 | Torch toggles, or is absent gracefully | ☐ |
| B5 | A neighbouring product on a shelf is not committed to — the code must hold steady before it counts | ☐ |
| B6 | *Product not found* offers **Scan barcode again** and is not a dead end | ☐ |
| B7 | Back from the scanner returns Home and does not reopen the camera | ☐ |

## C. Calculation and portions

| # | Check | Pass |
|---|---|---|
| C1 | New product: scan → type `65` → result appears immediately, decimal dominant, whole grams beneath | ☐ |
| C2 | Known product: tapping a recent pre-fills the portion and the result is instant | ☐ |
| C3 | A millilitre product locks the portion to ml and never asks for grams | ☐ |
| C4 | A decimal comma is accepted as typed | ☐ |
| C5 | Quick-adjust steps scale with the pack size and never produce a negative portion | ☐ |
| C6 | Countable portions: "2 slices" resolves, and the count field pre-fills `1` and selects all | ☐ |
| C7 | A direct-carb portion (no printed weight) shows "N × X g carbs" and invents no gram figure | ☐ |
| C8 | *Usual* shortcuts appear only for the same product and are one tap | ☐ |
| C9 | Copy gives a visible confirmation that persists ~2.5 s, not only a Toast | ☐ |

## D. Nutrition-label OCR — the core of this patch

**HW.** The emulator's virtual camera cannot render a nutrition table, so every row here is
hardware-only. See §E for the four specific products this patch was built against.

| # | Check | Pass |
|---|---|---|
| D1 | A clean, well-lit table reaches a result **without a crop screen and without a confirm tap** | ☐ |
| D2 | A table the app cannot read keeps the frozen photograph and offers a way forward — never a dead end | ☐ |
| D3 | Tapping the carbohydrate row restricts candidates to that row; sugars is unreachable | ☐ |
| D4 | The basis is always asked or stated, never assumed | ☐ |
| D5 | An impossible value (e.g. `790`) is refused with an explanation, and no accept action is offered | ☐ |
| D6 | A per-serving-only label never presents its figure as per 100 g or per 100 ml | ☐ |
| D7 | *Retake* and *Enter manually* are reachable at every step | ☐ |
| D8 | A basis mismatch when verifying a stored value offers **no apply path** | ☐ |
| D9 | Quick calculation shows the value, its basis, and *Read from label by you* | ☐ |
| D10 | A quick calculation leaves no Recents entry until it is explicitly saved | ☐ |

## E. The four regression products — this patch's release gate

**HW, and the reason this patch exists.** Each row is a product photographed on 2026-09-01; the
evidence is in `docs/Scan Evidence 01-09-26/`. A failure here blocks the patch.

| # | Product | Required outcome | Pass |
|---|---|---|---|
| E1 | Green drink (`0,5 g/100 ml`, `1,3 g/250 ml`) | Reads `0.5 g/100 ml`, **or** refuses safely with labelled choices. **Never `1.3 g/100 ml`** | ☐ |
| E2 | Cracker bag (`72,0 g/100 g`) | Reads `72 g/100 g` and reaches Quick Calculation **automatically, with no confirm tap** | ☐ |
| E3 | Korean sauce (`6 g` per `18 g` serving) | Never labels `6 g` as per 100 g or per 100 ml. Refusal or an explicit basis question only | ☐ |
| E4 | Multilingual table (`59,2 g/100 g`, `5,4 g/9 g`) | Reads `59.2 g/100 g`. Never reports `54 g` | ☐ |
| E5 | Across all four | **No confident wrong value anywhere**, at any framing | ☐ |
| E6 | Across all four | Clean scans need **two taps** total: *Scan nutrition label* → *Capture* | ☐ |
| E7 | Across all four | Recovery never requires cropping as a mandatory step | ☐ |

## F. Meal, search and data

| # | Check | Pass |
|---|---|---|
| F1 | Several portions add to one meal total; the meal survives a force-stop | ☐ |
| F2 | A quick-calculation meal line is legible — never a blank row | ☐ |
| F3 | Search runs as you type, keeps previous results under a progress line, and never flickers | ☐ |
| F4 | Punctuation in a product name (`Kinder Bueno (White)`, `milk -chocolate`) returns sensible results | ☐ |
| F5 | A search failure never renders as "no matches" | ☐ |
| F6 | A failed barcode lookup offers search — but not when the failure was a network error | ☐ |
| F7 | Offline: clear message, retry works, nothing is silently wrong | ☐ |
| F8 | *Clear recent history* removes remembered portions, counts and Usual shortcuts, favourites included | ☐ |
| F9 | *Clear saved products* leaves no usage that reappears when the same barcode is re-scanned | ☐ |

## G. Appearance, accessibility, attribution

| # | Check | Pass |
|---|---|---|
| G1 | Light and Dark both render correctly, including the status bar | ☐ |
| G2 | App forced Light on a dark phone, and forced Dark on a light phone, both follow the app | ☐ |
| G3 | At 1.3× and 1.8× font scale nothing is cut mid-glyph; the portion zone fades when more is below | ☐ |
| G4 | Every touch target is ≥48 dp at 1.0× and 1.8× | ☐ |
| G5 | TalkBack announces every control with a meaningful label | ☐ |
| G6 | Open Food Facts attribution appears in Settings → About | ☐ |
| G7 | The privacy policy link opens the committed URL | ☐ |

## H. The kitchen test — do this last, and honestly

| # | Check | Pass |
|---|---|---|
| H1 | Standing, one hand holding food, reached a carb value one-handed without putting anything down | ☐ |
| H2 | Never had to reach the top of the screen for anything essential | ☐ |
| H3 | The result is readable at arm's length, at a glance | ☐ |
| H4 | Taps and keystrokes from launch to result: ______ | ☐ |

---

## What this checklist deliberately does not cover

- **Anything the JVM or instrumented suites already assert.** Re-checking by hand what a test pins
  is how a checklist becomes long enough that nobody completes it.
- **The release/R8 build.** That has its own gate in `docs/play-release-readiness.md`, including the
  privacy-barrier check and reading the signer DN off the artifact.
- **Regulatory and store-listing wording.** Owner work, governed by §44 §7.1.
