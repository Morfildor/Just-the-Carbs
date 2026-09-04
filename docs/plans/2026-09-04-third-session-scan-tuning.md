# Third-session scan tuning (2026-09-04, `docs/Scan Evidence 3rd testr`) — IMPLEMENTED

29 captures, ~22 distinct products, Samsung SM-S928B, app `1.0.3-debug`. This is the corpus the
owner described as *"much better, still not amazing"*.

**Nothing here changes a recognition rule, a threshold, the calculation, the schema, migrations, the
§10 lookup priority or barcode detection.** No parser rule was relaxed, no confidence bar lowered,
and no digit is ever manufactured. The whole diff is two files: `CrossColumnRatioCheck.kt` and
`FocusedAmountEntry.kt`. HEAD is `47ad5d1`, unchanged — no commit, no version bump, no release build.

---

## 1. What the session measured

**Latency is not a problem.** 477–936 ms end to end, ML Kit 244–453 ms, on the real device with the
debug evidence writer still attached. No latency work was done or is needed.

**Zero wrong values reached the user across 29 captures**, and six bundles carry the
physical-observation rejection text, so that P0 is confirmed on hardware.

**Zero captures advanced automatically.** Every one cost at least one interaction: 14
`CONFIRM_ON_CAPTURE`, 5 `RECOVERY`, 5 `FOCUSED_AMOUNT_ENTRY`, 5 `CROP_FALLBACK`.

That 0/29 was **arithmetically forced**, not a matter of tuning. `automatic-verification` was `NONE`
on all 29, and its cross-column half reported **`only 0 coherent row pairs; 3 needed`** on 19 of the
20 captures that reached it — zero pairs, not two. With `DISTINCT_OCR_AGREEMENT` correctly closed by
the physical-observation rule, both routes to an automatic advance were shut.

## 2. Root cause: two header-typesetting artifacts

`CrossColumnRatioCheck` required `singleOrNull` on the per-100 column and at least one `PER_SERVING`
column. Both fail routinely on real packaging.

**A. One printed column, read once per language.** The Turkish rice-flour box emits six `PER_100_G`
columns (`100 g.`, `For 100 g.`, `Für 100 g.`, `Pour 100 g.`, `Voor 100g.`, `Pr 100 g.`) spanning
**91 px of a 1684 px frame — 5.4%**. `singleOrNull` returned null and the check refused before
starting. This is the *identical* defect already fixed for `PER_SERVING` fifteen lines below, with
the justification already written; it had simply never been applied to the per-100 side.

**B. The second value column emitted `UNKNOWN`.** The Lidl yoghurt prints `Ø/100 g` and `Ø/125 g`;
the Baltic crispbread prints `Ø/100 g` and `Ø/9 g`. There is no `NutritionBasis` member meaning "per
125 g", so those columns are `UNKNOWN` — **correct for reading a value, and the rule that closed the
2.6× error.** It is not an objection to using the column as a *ratio denominator*: that quantity is
dimensionless, never leaves the class, infers no basis, reads no value and is displayed nowhere.

## 3. The changes

### P1 — collapse duplicate same-kind per-100 columns

Per-100 columns within `SAME_COLUMN_FRACTION` (3% of frame width) are one printed column, by the
same rule already used for `PER_SERVING`.

**The bound is the safety argument, and it has a measured counter-example.**
`20260904-134552-198` photographs an Indomie packet printing **two separate per-100 g tables** — the
noodles at 27 g and the bouillon at 2,7 g. Those columns sit **443 px apart (26%)**, so they are not
collapsed and the check declines rather than inventing a table that is not printed. Pinned by
`two different products' per-hundred tables are never merged into one column`.

### P2 — an `UNKNOWN` column may be the ratio denominator, and nowhere else

Bounds, all pinned: consulted **only** when no `PER_SERVING` column resolved (established meaning
wins); `REFERENCE_PERCENT` is never eligible (a `%RI` cell is a fraction of a reference intake, not
the nutrient restated, and would compare a mass against a percentage); it must be a single printed
column by the same distance rule; and it must not be the per-100 column itself.

### P3 — a multilingual name-wrap is one declaration

`20260904-134420-616` prints its carbohydrate row as `Karbonhidrat / Kohlenhydrate glucides 80 g /
carbohydrate`, reconstructed as four rows that each type `TOTAL_CARBOHYDRATE`. `FocusedAmountEntry`
required `singleOrNull` over them, so the guard fired on a label **agreeing with itself in five
languages** and sent a capture whose row and basis were both established to the crop screen.

The guard exists to stop the app arbitrating between declarations stating **different figures**. A
row printing only a nutrient name states no figure, so it cannot be what the user confirms and
cannot disagree with anything. The rows that *carry a value* are what must be unambiguous — two of
those still return null, pinned by `two total rows stating different values still yield no target`.

## 4. Measured outcome

| | device | after |
|---|---|---|
| `AUTO_ADVANCE` | 0 | **2** (`134917-744` = 59.2, `134719-477` = 3.2 — both correct) |
| wrong figures shown | 0 | **0** |
| correct readings shown | 14 | 14 |
| `CROP_FALLBACK` | 5 | 4 |

Across the **earlier** corpora the same changes moved three more captures `CONFIRM → AUTO`, every one
correct and every one corroborated by its own table: the thirteenth session's yoghurt (1.25 ratio,
4 rows), and the fourteenth's Fanta (2.6 ratio, 3 rows) and yoghurt (1.25, 4 rows). `CORRECT_AUTO +
CORRECT_CONFIRM` is unchanged in both, so **no correct reading was lost anywhere**.

## 5. Two things built, measured, and NOT kept

**Corroborating views.** `20260904-134233-470` looks like it should verify — it prints `Ø/100 g`
beside `Ø/125 g` and every printed row states the 1.25 ratio — but only the crop pass read it
confidently and that crop cut the header. Letting a *non-confident* view of the same photograph
supply the table was implemented, including translating the crop-local candidate through
`sourceSpaceGeometry`. **The translation is exact** (the candidate lands on the right row at 1.000
overlap) and the table still cannot answer:

```
Energie      503 | 152    <- pairs with the neighbouring kcal, not with 629
Vetten      10,0 | -      <- reconstruction put 12,5 on a different row
Koolhydraten 3,2 | 4,0    = 1.25, the only coherent pair
Sugars       3,2 | 5,8    <- a genuine misread
Protein     4,69 | 0,13   <- picks up the salt value
```

One pair against three. It changed **no capture in any corpus**, so it was removed rather than kept
as machinery that might pay off later. `CorroboratingViewTest` keeps the measurement executable.
*The transferable point: a document holding the right columns is not a document holding the right
pairs — column resolution and row reconstruction fail independently.*

**Threshold changes.** None. `MIN_SUPPORTING_ROWS`, `SUPPORT_TOLERANCE` and `CANDIDATE_TOLERANCE`
are untouched. The failures were structural (0 pairs), and `134520-722` — the only capture reaching
support=2 — genuinely printed no second cell on its rows. Loosening a margin to buy an advance is
the wrong trade on this screen.

Also unchanged: `ScaleAmbiguity` (the five recoveries are all bare-integer `Unsupported`, and the
column-separator rescue was already measured and rejected), the physical-observation rule, and the
right-of-candidate sibling rule.

## 6. Verified

JVM **1678/1678** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 166 JUnit XML
files — up from 1654). Lint **exit 0, 0 errors, 23 warnings** — unchanged baseline. Debug APK
**89,935,487 bytes**, SHA-256 `AD9D3A95A58D03EF06ECC0A63CB2E8CFFD680E2ED6B469E72336CADE83984AEF`.
Instrumented sources compile.

**Three negative controls**, each restored and the suite re-verified green afterwards:

| control disabled | failures |
|---|---|
| per-100 collapse reverted to `singleOrNull` | 1 (`a table headed in three languages…`) |
| off-basis denominator removed | **6** across 5 classes and 4 corpora |
| `FocusedAmountEntry` relaxation reverted | 2 |

The first control initially failed **nothing** — the collapse is reachable (the Hellmann's mayonnaise
emits three `PER_100_ML` columns spanning 1% of the frame, the Fanta three under 1%) but those
captures fail for unrelated reasons. Rather than delete a correct symmetry fix or keep unexercised
code, a direct unit test now pins it, and the control then fails exactly that test.

## 7. NOT verified

**Nothing in this pass has been seen on physical hardware.** Everything above is JVM. The two new
automatic advances are the whole point of the change and have not been observed on a phone.

The nine-photograph OCR corpus was not re-run on a device; no emulator or device was attached. No
release build, no AAB, no R8 barrier re-check. The device gate is the next session: three captures
each of the Baltic crispbread and the coconut water, confirming they reach Quick Calculation with no
tap and with the printed value.
