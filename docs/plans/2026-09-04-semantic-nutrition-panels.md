# Semantic Nutrition Panels and Declarations

## Goal

Move the OCR interpretation path to the following bounded architecture while preserving the existing refusal and provenance guarantees:

```text
panel-local raw elements
  -> strict physical rows
  -> semantic nutrient declarations
  -> cells and columns
  -> evidence resolution
  -> verification
  -> presentation
```

This is primarily an architecture, safety, and generalization pass. The latest 17-capture corpus indicates that most remaining recall failures are optical; the semantic model must not claim or manufacture recognition gains that the replay does not demonstrate.

## Evidence

- The latest corpus contains 17 physical sessions. The recorded presentation routes are four automatic advances, three confirmations, five focused amount entries, three recovery routes, and two crop fallbacks.
- Session `20260904-095122-233` prints the total-carbohydrate label and value on separate physical rows. Its OCR also corrupts `75g` to `759`, so it demonstrates the need to represent a multi-row declaration without proving that semantic grouping alone can recover the correct amount.
- Older adjacent-panel fixtures demonstrate that globally reconstructed physical rows can combine a nutrition table with horizontally adjacent package text before downstream filtering can separate them.
- Three latest sessions contain a correct recognition candidate that is withheld or disputed downstream and require individual review: `20260904-094627-485`, `20260904-094841-512`, and `20260904-095034-270`.

The requested recording was not supplied. Evidence files establish the intended presentation route but cannot prove visible tap behavior or the exact interaction timeline.

## Design

### `NutritionPanel`

A bounded raw-element grouping stage will identify coherent nutrition panels before `LogicalRowBuilder` runs. It will use raw element geometry and existing terminology to combine these signals:

- total-carbohydrate and other nutrient anchors;
- a nutrition basis or serving header;
- repeated numeric X clusters;
- coherent row pitch and vertical extent;
- label-column continuity;
- compatible local text orientation.

The grouping result owns a source-element subset and source-space bounds. It is not an automatic crop request and does not perform a second OCR run. The grouping implementation must remain bounded and near-linear after sorting; it is not a general layout solver.

A panel is accepted only when several independent structural signals agree. Multiple accepted panels are interpreted independently. Identical readings may collapse; incompatible readings become a structural conflict. When no panel is strong enough, the existing full-document path remains the fallback and receives no confidence upgrade from the failed localization attempt.

### Strict physical rows

`LogicalRowBuilder` remains unchanged. It runs on each accepted panel's source elements, so physical printed rows remain conservative and child rows cannot be joined through increased Y tolerance.

### `NutrientDeclaration`

A semantic declaration retains:

- nutrient kind;
- contributing physical rows and label elements;
- value cells and their original elements;
- source-space bounds;
- resolved column ownership;
- parent/child relationship;
- provenance back to the panel, rows, and elements.

A declaration may span immediately adjacent physical rows only when multiple signals agree: normalized vertical gap, compatible label region or continuation indentation, established value-column alignment, the same panel and column schema, and absence of an intervening nutrient boundary.

A child nutrient or a new unrelated nutrient closes the preceding total declaration. A value following a child label belongs to the child. Mere proximity or the presence of a number never establishes continuation.

Rows that contain multiple nutrient clauses remain supported. Clause segmentation is retained within each physical row, while declaration grouping handles continuation between rows.

### Shared consumers

Automatic parsing, recovery candidate generation, focused amount entry, and manual tap handling will use the same semantic model. A tap first resolves to the tightest source element or cell, then to its owning declaration. A safe total-carbohydrate cell can complete on the first tap; a child-owned cell remains ineligible; an unreadable amount with a safe declaration and basis moves directly to focused amount entry.

### Diagnostics

Parser and presentation diagnostics will expose typed failure categories instead of collapsing every miss into `NotFound`:

- carbohydrate term missing;
- carbohydrate value missing;
- basis missing;
- declaration fragmented;
- structural conflict;
- scale unresolved;
- OCR conflict.

The evidence recorder will report the final cross-layer diagnosis and preserve the lower-level reasons that produced it.

## Test-first slices

1. Import all 17 latest Pass A and Strategy B documents as immutable JVM fixtures. Record printed truth, current parser result, current route, and current failure diagnosis before production changes.
2. Add declaration-model tests for wrapped total labels, multilingual continuations, two columns, multi-clause rows, child boundaries, unrelated numbers, missing cells, and broken decimal scale.
3. Add raw-element panel tests for horizontally adjacent prose, multiple panels, weak localization fallback, headers above a multilingual body, and panel-local column ownership.
4. Implement the panel and declaration models and move automatic interpretation onto them.
5. Move recovery, focused entry, and tap ownership onto the same model.
6. Add typed cross-layer diagnostics and replay the latest and accumulated corpora.
7. Trace the three correctly recognized but withheld/disputed latest sessions. Relax presentation only where the existing scale, basis, cross-run, and child-nutrient guarantees establish a confirmation-grade reading.
8. Measure optical-recognition variants against the physical corpus. Adopt only independently fallible views that improve corpus outcomes, retain native provenance, and pass through normal evidence resolution.

## Guardrails

- No `LogicalRowBuilder` tolerance changes.
- No package-specific coordinates or product-specific rules.
- No automatic crop re-OCR authority.
- No value/basis composition across unrelated elements or runs.
- No child-nutrient promotion.
- No decimal invention or scale inference from scale-invariant agreement.
- No parser work moved back to the main thread.
- No version bump, release AAB, commit, reset, history rewrite, or unrelated cleanup.

## Validation

Run the complete relevant JVM suite with `--rerun-tasks`, lint, debug assembly, instrumented-test compilation, the latest 17-session replay, and the accumulated physical corpus. Report exact totals, before/after classifications, actual latency measurements available from the evidence, and every remaining miss by failure layer.

## Measured outcome

### Semantic pass

All 17 Pass A documents and all 17 Strategy B documents were imported from the physical evidence before the production path changed. The pre-change replay and the semantic replay are identical:

| outcome | before | after |
|---|---:|---:|
| correct automatic | 4 | 4 |
| correct confirmation | 2 | 2 |
| correct focused entry | 5 | 5 |
| unnecessary recovery | 1 | 1 |
| OCR has no correct value | 3 | 3 |
| wrong proposal | 1 | 1 |
| wrong automatic | 0 | 0 |
| ground truth unavailable | 1 | 1 |

Therefore **0 of the latest 17 captures improved and 0 regressed**. This is an architecture and safety result, not a recognition-recall result. The older synthetic Kinder adjacent-panel regression does improve: full-frame interpretation now isolates the nutrition panel and reads `53.5/PER_100_G` before strict rows are built.

The three latest captures with correct but withheld/disputed evidence remain conservative after individual traces:

- `094627`: Pass A `0.59` conflicts with Strategy B `0.5`; final diagnosis `OCR_CONFLICT`, focused entry, no proposed digits.
- `094841`: Pass A reads the printed integer `57`, but neither punctuation nor an independent run establishes its scale; final diagnosis `SCALE_UNRESOLVED`, recovery, no proposed digits.
- `095034`: Pass A `1` conflicts with Strategy B `11`; final diagnosis `OCR_CONFLICT`, focused entry, no proposed digits.

There is no safe winner in either conflict and no new scale evidence for `094841`, so none was relaxed merely because the withheld candidate happens to match photographed truth.

### Optical phase

A fresh ML Kit run on an Android emulator measured five separately identified views of each of the seven physical OCR-corruption JPEGs. Counts are `correct / confident-wrong` at the parser boundary:

| OCR view | correct | confident-wrong |
|---|---:|---:|
| original | 0 | 1 |
| grayscale | 0 | 2 |
| contrast 1.4× | 1 | 2 |
| grayscale + contrast 1.6× | 0 | 1 |
| upscale 1.5× | 2 | 1 |

Upscaling recovered `094800` (`1.3/PER_100_ML`) and `095122` (`75/PER_100_G`); contrast recovered `094800`. But upscaling and contrast also repeated the correlated `12/PER_100_G` error on `094650`, whose package prints `7.2`. Grayscale introduced other confident-wrong readings. The experiment retains the variant name and full parser provenance for every result and never enters production evidence resolution.

No additional OCR view is adopted in this pass: the measured precision/latency trade is unsafe, and treating correlated transforms of one ML Kit engine as automatic corroboration could strengthen exactly the wrong `12` reading. Production scale, basis, and child-nutrient safeguards remain unchanged.

The supplied Samsung evidence measured existing parser latency at p50 `62 ms` and p95 `266 ms`. No post-change capture was made on that physical device, so an apples-to-apples latency improvement or regression is not claimed. The recognition and presentation threading path was not changed; deterministic parser-work regression guards remain part of the full suite.

---

# Physical-observation provenance (2026-09-04, later same day)

A later hardware session of **21 captures** arrived after the pass above
(`docs/Scan Evidence new structure`, 11:36–11:43). It is a different session from the 17 replayed
above — no bundle id overlaps — and it contains the app's first **confident-wrong automatic advance**
measured on a device.

## The defect

`20260904-113653-044` photographs a Fanta bottle printing `0,5 g / 100 ml`. The device recorded:

```
FULL_FRAME_PASS_A   [run=PASS_A]          Confident 0.59/PER_100_ML
FILTERED_PASS_A     [run=PASS_A]          Confident 0.59/PER_100_ML
SELECTED_REGION_OCR [run=SELECTED_REGION] Confident 0.59/PER_100_ML
automatic-verification: DISTINCT_OCR_AGREEMENT
final UI action : AUTO_ADVANCE
```

The printed `g` was recognised as a `9`, and the app advanced to Quick Calculation with **no
confirmation step** on a figure ten times the printed one.

Two independent root causes, each reproduced by a test that fails on the pre-fix tree:

1. **`DISTINCT_OCR_AGREEMENT` counted `RecognitionRun`, not photographs.** `SELECTED_REGION_OCR`
   re-recognises a *crop of the same JPEG*, so it inherits that capture's focus, blur and glyph
   damage. Two correlated observations agreeing is one observation counted twice.
2. **The unit-accompaniment policy declared the label "units in headers only".** That bottle prints
   `g` on **all twelve** of its value cells; ML Kit corrupted five of the six in the 100 ml column,
   leaving one survivor against a threshold of two. **The corruption suppressed the evidence of its
   own convention** — the more thoroughly the glyphs are damaged, the more ordinary a bare value
   looks, so the threshold is unreachable precisely on the labels that need it.

## The fix

`PhysicalObservationId` makes the photograph the unit of independence. Everything derived from one
capture — full frame, crop, rotation, upscale, contrast — shares its id; only a genuinely separate
photograph can corroborate for **automatic advancement**. The default is a shared `UNKNOWN` constant,
so un-annotated evidence can never look independent by omission.

Same-frame agreement is **demoted, not deleted**. `AutomaticVerification.agreesAcrossViews` answers
the weaker *may this be shown for confirmation* question, which same-frame views can legitimately
answer: they can still disagree about tokenisation and row association, just not about optics or
decimal scale. Without that distinction, removing the wrong automatic route also removed two correct
proposals (`57 g`, `35 g`) — measured, then fixed. Two parses of one *recognition run* still
corroborate nothing, which keeps the eighth session's red-label `12` out.

`UnitAccompanimentPolicy` now counts a corrupted unit glyph (`09`, `0.59`, `14.59`) as evidence that
the label prints *something* after its numbers, while still requiring **at least one clean unit**
somewhere. A corrupted token is never accepted as a unit — `CarbUnitAccompaniment` refuses it exactly
as before — it merely stops the damage reading as proof of a bare-value convention.

## Measured outcome, 21 captures

| classification | device | after |
|---|---:|---:|
| correct automatic | 6 | 2 |
| correct confirmation | 4 | 7 |
| correct focused entry | 3 | 3 |
| unnecessary recovery | 1 | 1 |
| OCR has no correct value | 1 | 1 |
| wrong proposal | 2 | 1 |
| **wrong automatic** | **1** | **0** |
| ground truth unavailable | 6 | 6 |

**Correct readings put in front of the user: 10 → 9 … and one of the 10 was wrong.** Counting only
correct ones, 9 → 9 with the wrong automatic removed. Five same-frame agreements were invalidated;
four became confirmations and one (`113653-044`) became focused entry because its digits are
corrupted. One capture *gained* automation: `113914-060` now advances on `CROSS_COLUMN`.

The structural route is untouched — `114241-317` still advances automatically on four supporting
rows, which is why this is the removal of a bad route rather than of automation.

## Older corpora

The 17-capture corpus **gained** a reading: `094627-485` now offers the correct `0.5` for
confirmation instead of withholding it, and `094946-883`'s wrong proposal is gone. Correct readings
shown went 6 → 7, wrong proposals 1 → 0. The 19-capture thirteenth-session corpus moved 3 automatic
advances to confirmations with **no reading lost** (8 shown, before and after) and `WRONG_AUTO`
still 0.

## Remaining wrong proposal

`113950-065` — a Hellmann's bottle printing `1,3 g / 100 ml` read as `13g` by every view. It is
offered for confirmation at `13.0`, exactly as the device did.

**It is not repairable from this evidence, and that was measured rather than assumed**: `1,3` appears
in *no* recognition of that capture, so the correct value is absent entirely. A `corroborationSettlesScale`
restriction was implemented and measured; it suppresses this proposal but also hides the correct `57`
and `35`, because all three are separatorless integers that `ScaleAmbiguity` reports as `Unsupported`
for the identical reason. **Trading two correct readings for one wrong one is the wrong direction**,
so the parameter is retained and pinned but the automatic path passes `true`. The route that closes
this is a second physical observation.

## Verified

JVM **1645/1645** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 160 JUnit XML files
— up from 1609). Lint **exit 0**, 0 errors, 10 warnings. Debug APK builds (89,935,051 bytes,
`versionCode 4` / `1.0.3-debug` read from the APK; permissions unchanged). Instrumented sources
compile.

**Two negative controls**, each restored byte-identically and re-verified green:

| control disabled | failures |
|---|---|
| observation counting reverted to `recognitionRun` | **12**, incl. the P0 and both corpus gates |
| corrupted-glyph accompaniment evidence removed | **4**, incl. the correct `0.5` on `094627` |

The second control shows the two fixes are complementary rather than redundant: without the
accompaniment change, `094627`'s correct reading is lost.

## NOT verified

**No second physical observation was implemented.** Restoring the automatic rate through a genuinely
independent frame B is designed for (`EvidenceSource.SECOND_OBSERVATION_PASS` and the provenance
model exist and are pinned) and **not built** — no CameraX change was made, no latency was measured,
and no lifecycle race was exercised.

**Nothing in this pass has been seen on physical hardware.** Everything above is JVM replay of device
evidence. No release build, no AAB, no R8 barrier re-check, no commit, no version bump.

## Thorough unit-glyph corruption (2026-09-04, sixteenth session) — READ FIRST

The **first physical session run against the physical-observation build**
(`docs/Scan Evidence 04-09 2nd test`, 16 captures, Samsung SM-S928B, 12:45–12:49). Still
`versionCode 4`, nothing built as a release, nothing uploaded, no commit. Nothing about the
calculation, the schema, migrations, the §10 lookup priority, barcode detection or the semantic
`NutritionPanel` / `NutrientDeclaration` architecture changed.

### The device confirms the P0 fix, on hardware

Three bundles (`124711`, `124724`, `124924`) carry the new rejection text verbatim — *"all 2
recognition runs read one physical observation (UNKNOWN); views of the same photograph share its
optical defects and cannot corroborate each other"* — and **no capture in the session
auto-advanced**. Under the previous build each of those three would have been
`DISTINCT_OCR_AGREEMENT` and advanced with no confirmation step.

**The JVM replay reproduces all 16 device actions exactly**, which is what makes the corpus
trustworthy as a harness rather than merely as a record.

### The defect it exposed: corruption thorough enough to hide its own evidence

`20260904-124935-320` photographs a Lidl drink printing **`6,2 g / 100 ml`**. The app offered
**`6.29`** for one-tap confirmation. Unlike the Fanta's `0.59`, that token *carries a decimal
separator*, so `ScaleAmbiguity` returns `Established` and nothing downstream questions it.

**The controlled comparison is inside the same session, on the same physical package seconds
earlier**, which is what makes the cause unambiguous rather than argued:

| capture | value cells as recognised | policy verdict | read |
|---|---|---|---|
| `124924-679` | `0g`, `0g`, `6,2g`, `6,09`, `0g`, `0,01g` | prints units on its values | **`6.2`** ✅ |
| `124935-320` | `09`, `09`, `6,29`, `609`, `09`, `0,019` | **units in headers only** | `6.29` ❌ |

Same label, same typesetting, opposite verdicts — decided only by *how thoroughly* ML Kit destroyed
the `g` glyphs. This is the 2026-09-04 Fanta finding one step further on: that fix counted corrupted
cells as witnesses to the convention but still required **one clean unit to survive somewhere**, and
here none did.

**The evidence is destroyed by the very corruption it exists to catch**, so the threshold becomes
unreachable exactly on the labels that need it. 15 of the 16 captures report "prints units on its
value cells"; the single exception is the only capture that produced a wrong offer.

### The fix, and why the legitimate layout is still safe

Corruption seen on **three separate nutrient rows** now establishes the convention on its own
(`MIN_CORRUPTED_UNIT_ROWS`). Counted per *row* rather than per cell, because several corrupted cells
on one reconstructed row can be one damaged printed row rather than independent witnesses — the same
reasoning that makes the policy count over the document rather than over the carbohydrate row. Three
rather than two because this evidence is strictly weaker: a clean `2.1g` states what was printed,
whereas `0.59` states only that *something* followed the number.

**The header-only layout is protected by the pattern, not by the clean-unit requirement**, and that
is measurable rather than asserted: a label printing bare `2.1`, `4.8`, `3.6`, or bare integers `21`,
`36`, `47`, `72`, matches the corrupted-cell pattern **zero** times, because a corrupted cell must
show a trailing digit where a unit belongs. Both layouts stay pinned by `UnitConventionSemanticsTest`.

Nothing is autocorrected. `6.29` never becomes `6.2`; the capture routes to focused entry with the
basis correctly established as `/100 ml`.

### Column-wide separator evidence: BUILT, MEASURED, REJECTED — do not rebuild it

Two captures (`124822-392`, `124835-611`) read a crisps tube's **`72 g / 100 g` correctly**, unit
intact, and withheld it: `72` has no decimal separator and its single-column row has no sibling to
pair against, so `ScaleAmbiguity` returns `Unsupported`. The label appears to answer the question one
row away — its other cells read `1,1g`, `9,9 g`, `9,8 g`, `2,20 g`.

Implemented as "two separated cells elsewhere in the candidate's column establish the scale".
**It rescued both `72` captures and simultaneously re-admitted the red Lidl `12`** — a package
printing `7,2 g` whose column *also* kept its separators (`<0,1g`, `6.1g`, `0,8 q`, `0,25 9`) while
the carbohydrate cell arrived as a bare `12g`, the `7,` having been fused into the multilingual
nutrient text (`Hidratos de carbono/ Hidratos de carbono 12g`).

So the premise is false: **a column preserving separators on other rows says nothing about whether
this cell's separator survived.** Glyph loss is local — a fused nutrient word, a reflection, one
blurred character — not a property of the column. Reverted, and recorded in `ScaleAmbiguity`'s own
source plus `ColumnSeparatorScaleEvidenceTest`, which asserts the property the two labels *share* —
which is precisely why no rule keyed on it can separate them.

`72` therefore stays withheld. That is a real UX cost on a correct reading, and the honest route to
admitting it is a second physical observation, not weaker scale evidence.

### Measured outcomes across the 16 captures

| | device | after |
|---|---|---|
| correct, confirmed | 5 | 5 |
| correct, focused entry | 3 | **4** |
| **wrong figure shown** | **1** | **0** |
| unnecessary recovery | 2 | 2 |
| no correct value in evidence | 3 | 3 |
| ground truth unreadable | 2 | 2 |
| **automatic advance** | **0** | **0** |

Ground truth was read from each `capture.jpg` by cropping the carbohydrate row from the recorded
element geometry, never from parser output. Two captures whose row is not legible are scored
`GROUND_TRUTH_UNKNOWN` rather than guessed.

### Verified

JVM **1654/1654** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, counted from 162 JUnit XML files
— up from 1645). Lint **exit 0, 0 errors, 23 warnings**, none in a file this pass touched. Debug APK
**89,935,164 bytes**, SHA-256 `31a1eed8b0d063718795b799af2ce139bf82da8bec70af2f12eb56c6edd15625`.

The two older corpora are **unchanged**: the seventeenth-session replay still shows 7 correct
confirmations and 0 wrong figures; the 21-capture session still shows 2 automatic + 7 confirmed and
0 wrong automatic readings.

**Negative control**: removing the corrupted-row convention fails exactly **2** tests — the two that
assert the defect — and nothing else. Restored byte-identically and re-verified green.

### NOT verified

**Nothing in this pass has been seen on physical hardware.** The 16 captures were taken on the
*previous* build, so they establish what was wrong and never that it is fixed; the JVM replay is a
measurement of recorded evidence, not of a phone.

**No second physical observation was implemented**, so the two correct `72` readings and the
`UNNECESSARY_RECOVERY` pair remain withheld. That is still the largest open item, and it is now the
only remaining route to raising the automatic rate without weakening a safety rule.

No release build, no AAB, no R8 barrier re-check, no commit, no version bump.
