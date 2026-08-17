# Real-image OCR baseline (2026-08-16), before any production change

Measured, not tuned. `RealImageBaselineTest.dumpEveryFixture` (androidTest) runs the production
pipeline — ML Kit `TextRecognition` → `MlKitOcrMapper.toDocument` →
`NutritionTableParser.parseWithDiagnostics` → `OcrDiagnosticsReport.render` — against all nine
committed fixtures and logs the full diagnostic dump via `Log.i(TAG, ...)`. No production file under
`app/src/main/kotlin/app/justthecarbs/ocr/` was touched to produce this document. Raw logcat output
captured to `.superpowers/sdd/2026-08-16-real-image-ocr-generalization/baseline_raw.txt` and split
per-fixture under `.../chunks/`.

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.RealImageBaselineTest`
on `emulator-5554` (AVD `carbscan`, API 36), 2026-08-16. Build succeeded, 1/1 test ran (it asserts
nothing, so "1 test, 0 failed" is expected and uninformative about the parser — the value is in the
log).

## Correction carried over from Task 1

The controller's source-photo mapping had `Test labels/7.jpg` and `Test labels/20260816_193636.jpg`
swapped (verified by direct visual inspection, repeated). `7.jpg` is the stokbrood photo,
`20260816_193636.jpg` is the yoghurt photo. Fixture filenames below use the corrected mapping — see
`tools/derive-ocr-fixtures.md`.

---

## Fixture 1 — `real_juice_bilingual_per100ml_01.jpg` (1503x810)

**Raw ML Kit text (concatenated):** `Voedingswaarde / Nährwerte | gemiddeldper 100mi/
100mienthaltendurchschnittlich | Energie/Brennwert | Vetten/Fett | waarvan verzadigde vetzuren/ |
davon gesättigte Fettsäuren | Koolhydraten / Kohlenhydrate | -waarvan suikers¹ /davon Zucker | 175 kJ
(41 kcal) | 0,2g | 0,04g | 9,0 g | 9,0g | ...`

ML Kit recognized the carbohydrate term (`Koolhydraten / Kohlenhydrate`) and the value (`9,0 g`)
correctly as separate elements. It **mangled the basis header**: `"gemiddeldper 100mi/
100mienthaltendurchschnittlich"` — "100 ml" lost its space and OCR'd the "l" as "i" twice
(`100mi`), and the whole Dutch/German sentence ran together with no recoverable "ml" token. ML Kit
also invented two garbage tokens from background label art: `EOELOF`, `KATIEGE` (mirror-reversed
fragments of "STATIEGELDFLES", the deposit-bottle logo text, picked up because the logo text runs in
a circular/reversed layout).

**Logical rows (8):** clean, one row per printed line. `TOTAL_CARBOHYDRATE` row =
`'Koolhydraten / Kohlenhydrate 9,0 g'`; `CARBOHYDRATE_CHILD` row = `'-waarvan suikers' /davon Zucker
9,0g'`. Row reconstruction and row classification both worked correctly.

**Columns (0):** none resolved — the header row's basis phrase is unreadable as "100 ml" by the
terminology matcher because of the OCR mangling above, so `ColumnClassifier` never emits a
`PER_100_ML` (or `PER_100_G`) column.

**Outcome:** `NotFound`. Diagnostic reason: *"total row found but no per-100 column was resolved"*.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 9.0 g per 100 ml | NotFound | NotFound | `ML_KIT_RECOGNITION` (basis phrase) |

**Recognized but lost, or never recognized?** The *value* and the *carbohydrate term* were
recognized correctly. The *basis phrase* was **not recognized** — ML Kit output `100mi` /
`100mienthaltendurchschnittlich`, not `100 ml`. This is a recognition failure on the basis text, not
an interpretation failure: no downstream stage lost information that was actually present in the
`Text` object. Record as a Task 8 preprocessing candidate (the basis line is small, cramped, and two
languages run together with no space) — do not patch parser semantics to compensate.

---

## Fixture 2 — `real_grated_cheese_multicolumn_02.jpg` (1204x1050)

**Raw text:** `... Koolhydraten Waarvan suikerS Vezels ... o/100 g ... 1421 k1/ 342 kcal 26,0 q 2,09
0.59 09 25,0 g 1,50 g o/portie 50 g %RI 711 ki/ 171 kcal 13,0 g 8,7g 9% 19 % 44 % 1,0 q <1% 0,3g <1%
09 ...`

ML Kit recognized `Koolhydraten` and its row correctly, and recognized the header row
(`o/100 g`, `o/portie 50 g`, `%RI`) well enough that **4 columns resolved** (`PER_100_G`,
`PER_SERVING`, two `REFERENCE_PERCENT`). Row reconstruction, row classification and column
classification all worked. But the printed `2,0 g` was OCR'd as **`2,09`** — the decimal comma read
correctly, but the trailing `g` unit was misread as digit `9`, producing a well-formed but wrong
number.

**Outcome:** `CONFIDENT 2.09 PER_100_G` — a **confident, wrong** answer. The true value is `2,0 g`.
`2.09` is off by roughly 4.5%, small enough that a naive smoke test comparing "is it a number" would
pass; a test asserting the exact expected value catches it immediately.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 2,0 g per 100 g | 2.09 g per 100 g | **Wrong** | `ML_KIT_RECOGNITION` (unit merged into digit) |

**Recognized but lost, or never recognized?** Never recognized correctly — ML Kit's own output for
this cell is `2,09`, a single token; nothing downstream had the correct digits to work with. This is
a recognition failure specific to a value cell whose printed unit sits close enough to the last
digit to be read as part of the number. Also note: the serving-column cell was OCR'd as `1,0 q`
(correct value `1,0`, `q` for `g` — harmless since the parser only needs the numeric prefix), while
the per-100 cell's `g`→`9` substitution corrupted the actual digits. Not the same failure, coincidence
of a similar substitution pattern.

---

## Fixture 3 — `real_jar_prose_multilingual_03.jpg` (1261x1020)

**Raw text (relevant excerpt):** `... valeur énergétique/ Energí:349 kcal/ 1460 kJ. Veten / Yağ /
Fett/ Fedt / ipides: 29 g (Verzadigde vetten doymuş yağ gesättigte Fet / gras saturé/ heraf maettede
fedtsyrer: 20 g). Koolhydraten / karbonhidrat Kohlenhydrate glucies Kulhydrat 1,6 gSTker Seker Zucker
/ Sucre / heraf sukkerarter: 1,6 g ...`

ML Kit recognized both the fat figure (`20 g`) and the true carbohydrate figure (`1,6`, garbled as
part of `1,6 gSTker` — "g Suiker" merged) correctly as text, and recognized `Koolhydraten` /
`Kohlenhydrate` / `Kulhydrat` (three languages' words for carbohydrate) as text. The recognition
itself is usable; what fails is row reconstruction interacting with row classification:

**Logical rows:** the 8-language sentence wraps across many printed lines, each becoming its own
logical row (17 rows total, clean 1:1 with printed lines — no chaining/merging defect). But the
*sentence content* crosses row boundaries arbitrarily: the row ending in `"...20 g). Koolhydraten /
karbonhidrat"` contains **both** the saturated-fat number (20 g) and the word "Koolhydraten" — purely
because that is where the printed line break happened to fall, not because of any table structure.

**Row classification:** that row is typed `TOTAL_CARBOHYDRATE` (correctly, by the unconditional
carbohydrate-word rule) — but its only number is `20`, the fat figure. The *next* row,
`'Kohlenhydrate glucies Kulhydrat 1,6 gSTker'`, is also typed `TOTAL_CARBOHYDRATE` (again correctly —
it names carbohydrate terms). The diagnostic log shows: *"info: 2 total-carbohydrate rows;
interpreting all"*, then `rejected: 1.6: no column` and `selected: 20.0 PER_100_G`.

**Outcome:** `CONFIDENT 20.0 PER_100_G` — a **confident, badly wrong** answer (the saturated-fat
value, 12.5x the true carbohydrate figure). This is exactly the kind of silent substitution the
`RowClassifier` unconditional-exclusion rule was built to prevent for *sugars vs. carbohydrate*, but
the rule has no defense against a fat figure landing on a row that also happens to carry the word
"carbohydrate" one sentence-wrap later.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 1,6 g per 100 g | 20.0 g per 100 g | **Wrong** | `ROW_RECONSTRUCTION` / `ROW_CLASSIFICATION` (prose sentence boundaries don't align with printed-line boundaries; two rows both type as `TOTAL_CARBOHYDRATE` and the wrong one wins) |

**Recognized but lost, or never recognized?** Recognized but lost — this is squarely an
interpretation failure. Every digit and word needed for a correct reading (`1,6`, `Koolhydraten`,
`Kohlenhydrate`, `Kulhydrat`) is present verbatim in ML Kit's output. The failure is entirely in how
logical rows get built from a prose paragraph and how a row-level classification (correct for a
table) behaves when applied to a sentence that spans several physical lines. This is fixture 3, one
of the three the plan explicitly expected to need interpretation-stage work (Task 3-6 territory).

**Basis-declaration measurement (a):** the basis phrase "Naringsindhold (100g)" is on its own
logical row (`[HEADER] 'Naringsindhold (100g): Energiel eneri / Brenrwert'`), several rows away from
either carbohydrate-typed row. The declaration and the carbohydrate term/value are **not** on the
same logical row. One `PER_100_G` column *did* get resolved from that header row (x=405.0), and it
did successfully attach to the (wrong) row that had the "20" figure — so the structural distance
between basis and value did not, in this specific case, prevent a column from resolving; it just
attached to the wrong row's cell because both `TOTAL_CARBOHYDRATE` rows sit under the same single
column.

---

## Fixture 4 — `real_lid_prose_curved_04.jpg` (1379x1370)

**Raw text (relevant excerpt):** `... PourPerlPro 100g: Energie / Energy value / Energi: 198 kJ - 290
kçal. Matières gasses / Vetten /Fett / Fat/ Fedt: 28g, dont acides gras saturés / Waarvan verzadigde
vetzuren / davon gesättigte Fettsäuren / of which saturates / varav mattat Tett! heraf fmaætede
fedtsyrer: 19g, Glucides | Kolhydraten / Köhlenhjdrate | Carbohydrate /Kolydrat/ Kulhydrat: 3g. dont
sures /waarvan suikers Idavon Zucker I of which sugars/varav sockerarter7 heraf sukkerarter: 2,5g ...`

ML Kit recognized the true carbohydrate figure (`3g`), the sugars figure (`2,5g`), and all six
languages' carbohydrate words correctly as text. It also correctly read the saturated-fat figure
(`19g`) and its six-language label. The recognition is fully usable.

**Logical rows (27):** printed-line-based reconstruction is clean — the curved ring did **not**
fragment (see curvature measurement (c) below). But exactly the same prose-wrap defect as fixture 3
recurs: the row `'heraf fmaætede fedtsyrer: 19g, Glucides | Kolhydraten / Köhlenhjdrate | Carbohydrate
/Kolydrat/ Kulhydrat: 3g. dont'` carries **both** numbers (the fat figure `19g` at the start of the
line, the true carb figure `3g` at the end) because the printed line break landed between "heraf...
fedtsyrer:" and "Glucides...". This single row is typed `TOTAL_CARBOHYDRATE` (correct, by the
carbohydrate-word rule), but it has two numbers and the wrong one is selected.

**Columns (2):** `PER_100_G` at x=231.5 (from the `'PourPerlPro 100g: Energie /'` header row) and a
`REFERENCE_PERCENT` at x=1067.5 with an empty header (likely a stray `%` association, there is no
%RI table on this label — this column is probably spurious, picking up the `28%` fat-percentage
figure's x-position from `KRYDDERURTER,FETT/FEDT 28%` in the marketing copy above). The true `3g`
carbohydrate figure sits geometrically close to the `REFERENCE_PERCENT` x-position (it is the last
number on a long wrapped line, pushed rightward) and gets `rejected: 3: REFERENCE_PERCENT column` —
excluded as a percentage-column cell even though it is not a percentage. Meanwhile `19.0` sits closer
to the `PER_100_G` x-position and is selected.

**Outcome:** `CONFIDENT 19.0 PER_100_G` — **confident, wrong** (the saturated-fat figure, 6.3x the
true carbohydrate value).

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 3 g per 100 g | 19.0 g per 100 g | **Wrong** | `ROW_RECONSTRUCTION` / `ROW_CLASSIFICATION` (same prose-wrap defect as fixture 3) compounded by `COLUMN_CLASSIFICATION` / `PERCENT_FILTERING` (the correct value's x-position fell inside a spurious percent column and was excluded) |

**Recognized but lost, or never recognized?** Recognized but lost — every digit and word needed is
present in ML Kit's output verbatim. Purely an interpretation failure, and this fixture shows a
**second, distinct** mechanism from fixture 3's: not just "two numbers on one row, wrong one wins",
but the correct number being actively excluded by a percent-column false positive. Expected —
fixture 4 was flagged in the brief as one of the three prose fixtures likely needing interpretation
work.

**Curvature measurement (c):** the printed ring did **not** fragment into an unusual number of rows.
27 logical rows for a label with roughly that many printed lines of text is proportionate — no sign
of the curvature itself causing row-merging or row-splitting defects distinct from the ordinary
photograph-tilt slope handling already in `RowSlopeEstimator`. The image-level `skew` value reported
was -0.0139 (dy/dx), a small, uniform, whole-image tilt — not a per-row curvature signal, and
`LogicalRowBuilder` evidently coped with it fine (compare: the Kinder/Sondey fixtures, which are
flat labels, show similarly clean row counts). **Curvature was not the failure mode for this
fixture; the prose-sentence/row-boundary mismatch was**, identical in kind to fixture 3.

**Basis-declaration measurement (a):** the basis phrase ("PourPerlPro 100g") is on a different
logical row (`[HEADER] 'PourPerlPro 100g: Energie / Energy value / Energi: 198 kJ - 290 kçal.
Matières gasses / Vetten /Fett / Fat/ Fedt: 28g, dont'`) than the row carrying the carbohydrate term
and its true value (`'heraf fmaætede fedtsyrer: 19g, Glucides | Kolhydraten ... Kulhydrat: 3g.
dont'`). **Not the same row.** Column resolution still bridged the distance (one `PER_100_G` column
did form from the header row and attach to the far-away carb-typed row), which is enough to explain
why this fixture reaches `Confident` at all rather than `NotFound` — but it also explains *why the
wrong number was selected*: the column only tells the interpreter which x-band to read from on
whatever row is classified `TOTAL_CARBOHYDRATE`; it says nothing about *which number within that
row* is the carbohydrate figure when the row holds two unrelated numbers from two different
sentences.

---

## Fixture 5 — `real_witte_kaas_single_column_05.jpg` (1231x1260)

**Raw text:** `... 100 9 Voedingswaarde Energie 1083 k] /261 kcal 19,9 g Vetten waarvan: verzadigd vet
14,0 g Koolhydraten waarvan: suikers Vezels Eiwitten Zout 2,3 g 2,3 g ... 0,0 g 18,19 2,00 9 Bevat
alleen van nature aanwezige suikers. ...`

ML Kit recognized `Koolhydraten` and its value `2,3 g` correctly as separate elements (two-column
layout: labels in one block, values in another, common for this label style). It also correctly read
the header `100 9` (should be "100 g" — the "g" misread as digit "9", same substitution pattern as
fixture 2's unit-merge, but here it corrupts the header token rather than a value).

**Logical rows (23):** clean, matching printed lines 1:1 — no chaining or fragmentation defect. Row
classification correctly typed `Koolhydraten 2,3 g` as `TOTAL_CARBOHYDRATE` and `waarvan: suikers 2,3
g` as `CARBOHYDRATE_CHILD`.

**Columns (0):** none resolved. The header row `'Voedingswaarde 100 9'` never gets read as a
`PER_100_G` basis declaration because "100 9" (digit-for-letter substitution) does not match the
terminology matcher's "100 g" pattern, and no other row supplies a usable header. `rejected: 2.3: no
column`.

**Outcome:** `NotFound`. Reason: *"total row found but no per-100 column was resolved"*.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 2,3 g per 100 g | NotFound | NotFound | `ML_KIT_RECOGNITION` (header "100 g" misread as "100 9") |

**Recognized but lost, or never recognized?** Not recognized — the header token ML Kit actually
produced is `100 9`, not `100 g`. Recognition failure, same class as fixture 1 (a basis-declaration
token corrupted by OCR), different specific corruption (digit-for-letter vs. concatenation). Record
as a Task 8 preprocessing candidate; do not patch terminology matching to accept `9` as `g` — that
would create new false positives elsewhere (a real `9` could legitimately appear next to units).

**Bonus finding, not part of the required measurements:** the row `'Bevat alleen van nature
aanwezige suikers.'` (a prose sentence, not a table row, meaning "Contains only naturally occurring
sugars") is typed `CARBOHYDRATE_CHILD` purely because it contains the word "suikers". Harmless here
because the fixture is already `NotFound`, but it demonstrates the child-exclusion rule can
false-positive on ordinary prose sentences that are not part of the nutrition table at all — worth
keeping in mind if a prose-eligibility predicate starts trusting `CARBOHYDRATE_CHILD` rows as strong
evidence of "there is a real nutrition sentence here."

---

## Fixture 6 — `real_stokbrood_prose_dense_06.jpg` (1125x1320)

**Raw text (relevant excerpt):** `... Allergie-infobevat gerstgluten,roggegluten, tarwe gluten en
sesam. Kan andere glutenbevattende gra nen, mosterd, walnoot, soja (fabriek) en andere allergenen
(winkel) bevatten. Voedingswaarde per 100g: energie 1312kJ /312kcal,vetten 7,8 g, waar van
verzadigde vetzuren 1,1 g, onverzadigde vetzu ren 6,4 g, koolhydraten 46g,waarvan suikers 1,0 g.
vezels 4,7g, eiwitten 12g, zout 1,03 g. ...`

ML Kit recognized every figure correctly: `koolhydraten`, `46g`, `waarvan suikers`, `1,0 g` all
appear verbatim, on one physical printed line (`'ren 6,4 g, koolhydraten 46g,waarvan suikers 1,0
g.'`).

**Logical rows (24):** clean 1:1 with printed lines. Row classification: the row containing
`koolhydraten...46g,waarvan suikers 1,0 g.` is typed `CARBOHYDRATE_CHILD`, **not**
`TOTAL_CARBOHYDRATE` — because the row's text contains the child term "suikers" as well as the parent
term "koolhydraten", and `RowClassifier`'s unconditional exclusion rule (child term present -> child
row, checked before the carbohydrate check) fires first. No other row in the whole document contains
"koolhydraten" without also containing a child term, so **no row is ever typed
`TOTAL_CARBOHYDRATE`** for this fixture. Diagnostic: `result: No total-carbohydrate row`.

**Outcome:** `NotFound`. Reason string is unusually informative: *"no total-carbohydrate row; 3 child
row(s) found — a merged row would show here"* — the diagnostics renderer already anticipated this
exact case.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 46 g per 100 g | NotFound | NotFound | `ROW_CLASSIFICATION` (the correct, unconditional child-exclusion rule denies this row `TOTAL_CARBOHYDRATE` status because "koolhydraten" and "waarvan suikers" print on the same physical line — this is prose, not a table, so there is no separate child row to carry the exclusion) |

**Recognized but lost, or never recognized?** Recognized but lost. Every character needed is present
in ML Kit's output. This is the textbook case the plan predicted for fixture 6: a dense-prose label
where the parent-and-child nutrients are stated in one sentence rather than one table row each, so
the table-oriented row classifier's (correct, for tables) exclusion rule has no way to tell "child
value repeated on the total row" (a table pattern that must stay excluded) apart from "total and
child stated together in one prose clause" (a prose pattern where the total figure is the number
that appears *before* "waarvan").

**Prose-eligibility predicate measurement (b):** **YES** — this fixture has a logical row containing
both a carbohydrate term (`koolhydraten`) and a child term (`suikers`) on the same row:
`'ren 6,4 g, koolhydraten 46g,waarvan suikers 1,0 g.'`. This is the single clearest, most
representative example of the predicate's target case in the whole nine-fixture set.

---

## Fixture 7 — `real_yoghurt_serving_column_07.jpg` (1362x1280)

**Raw text (relevant excerpt):** `... Voedingswaarde per ... koolhydraten, waarvan ... - suikers ...
100 g schaaltje (150 g) ... 5,0 g ... 7,5 g ... 5,0 g ... 7,5 g ...`

ML Kit recognized the two-column table reasonably well: `koolhydraten, waarvan` and its per-100g/
per-150g values (`5,0 g` / `7,5 g`) are all present as text, and the header `100 g schaaltje (150 g)`
is present too — but this fixture's crop deliberately retains a fragment of a *different*, unrelated
block of body-copy text at the extreme left edge (`"HURT"`, `"10% VET"`, `"-g e"`, `"ortie (150
g)"`, plus several lines of a "Heijn zuivel is voor koeien die..." animal-welfare marketing
paragraph) per the controller's instruction to keep the `schaaltje (150 g)` header context. That
marketing paragraph's incidental short numeric-shaped fragments and the real table's numbers end up
close enough in x-position that column resolution gets confused.

**Logical rows (25):** the row `'koolhydraten, waarvan 5,0 g 7,5 g'` is correctly typed
`TOTAL_CARBOHYDRATE`, and `'ortie (150 g) - suikers 5,0 g 7,5 g'` is correctly typed
`CARBOHYDRATE_CHILD`. Row reconstruction and classification both worked as intended here.

**Columns (3):** two separate `PER_100_G` columns resolve (`x=136.0` from `'t0g per 100 g.'` — a
fragment of the unrelated marketing paragraph reading "...waarvan toegevoegd suikers 0 g per 100
g...", **not** the nutrition table — and `x=702.0` from the real `'Voedingswaarde per 100 g'`
header), plus one `REFERENCE_PERCENT` at `x=863.5` with an empty header. The real values (`5.0`,
`7.5`) sit closer in x to the spurious `REFERENCE_PERCENT` column than to either `PER_100_G` column,
so both get `rejected: ...: REFERENCE_PERCENT column`.

**Outcome:** `NotFound`. Reason: *"Total-carbohydrate row found but no usable per-100 cell"*.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 5,0 g per 100 g / 7,5 g per 150 g | NotFound | NotFound | `COLUMN_CLASSIFICATION` (a phrase inside an unrelated marketing paragraph elsewhere on the crop was read as a second, spurious `PER_100_G` basis declaration, which combined with a stray `REFERENCE_PERCENT` column to out-compete the real column for the value cells' x-position) |

**Recognized but lost, or never recognized?** Recognized but lost. All needed text and numbers are
present in ML Kit's output. This is a genuinely different failure shape from the other NotFound
cases: it is not that no basis column was found, but that *too many* candidate basis-shaped phrases
existed on the crop (because the crop necessarily keeps some non-table body copy per the controller's
"keep the header context" instruction), and the wrong one geometrically dominates. This argues for
caution before trusting "a PER_100_G column resolved" as strong evidence in a future prose-eligibility
or fallback path — a resolved column is not proof it is *the* column.

---

## Fixture 8 — `sondey_multilingual_100g.jpg` (900x1600, pre-existing fixture)

**Raw text (relevant excerpt):** `... Koolhydraten/Glucides/Kohlenhydrate 61,9 g ... WOaTVan
suikers/dont sucres/davon Zucker 47,6g ...`

ML Kit recognized the true total-carbohydrate figure and label correctly. Row reconstruction,
classification (`TOTAL_CARBOHYDRATE` for the 61,9 g row, `CARBOHYDRATE_CHILD` for the 47,6 g sugars
row) and column resolution (`PER_100_G` at x=310.0) all worked.

**Outcome:** `CONFIDENT 61.9 PER_100_G` — matches the app's documented known-good result exactly
(never 47.6, the sugars figure).

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 61.9 g per 100 g | 61.9 g per 100 g | **Confident, correct** | — |

**Recognized but lost, or never recognized?** N/A — this fixture passes end to end.

---

## Fixture 9 — `kinder_multicolumn_piece.jpg` (900x1600, pre-existing fixture)

**Raw text (relevant excerpt):** `... Uokohidiat/0gjikovi hidrati / Ugljeni hidrati / Batia,
Koolhydraten /adle/Gucides 53,5 6.7 3 ... od kojih šećeri /od Kohlenhydrate tega siadkorji / ...
nga tể clat sheqerna / dont sucres/ waarvan suikers/ 53,3 6.7 7 ...`

ML Kit recognized the correct per-100g figure (`53,5`), correctly typed `TOTAL_CARBOHYDRATE` for its
row and `CARBOHYDRATE_CHILD` for the sugars row (which also, notably, contains the misread "0" from
Slovenian "Ogljikovi" documented elsewhere in this codebase's history — visible here in the raw text
as `Uokohidiat/0gjikovi hidrati`, but it does not corrupt this run's outcome). Columns resolved:
`PER_100_G` (x=341.0), `PER_SERVING` (x=704.5, header `'/ Par pièce'`), `REFERENCE_PERCENT`
(x=817.8). Two `TOTAL_CARBOHYDRATE` rows exist (info log: *"2 total-carbohydrate rows; interpreting
all"*) — both plausibly the same printed row read at slightly different geometry/language groupings
— and the correct 53.5 is selected.

**Outcome:** `CONFIDENT 53.5 PER_100_G`, serving `6.7 per '/ Par pièce'` with
`descriptor=PIECE`, matching the app's documented known-good result (never 3, 7, or 53.3; per-piece
6.7 g). The per-piece weight (12.5 g) was **not** recognized on this run (`weight=none`) — the test
class `RealImageOcrTest.kotlin` already treats this as an acceptable, non-asserted outcome (see its
comment: *"If recognition lost the '(12.5 g)' line the descriptor legitimately carries no weight"*),
consistent with real-world OCR variance run to run.

| Expected | Actual | Outcome | Failure stage |
|---|---|---|---|
| 53.5 g per 100 g; never 3, 7, 53.3; 6.7 g per piece | 53.5 g per 100 g; serving 6.7 g PIECE | **Confident, correct** | — |

**Recognized but lost, or never recognized?** N/A — this fixture passes end to end (piece weight
recognition is a known, accepted run-to-run variance, not a defect).

---

## Comparison table

| Fixture | Expected | Actual | Outcome | Failure stage |
|---|---|---|---|---|
| `real_juice_bilingual_per100ml_01.jpg` | 9.0 g / 100 ml | NotFound | NotFound | `ML_KIT_RECOGNITION` |
| `real_grated_cheese_multicolumn_02.jpg` | 2.0 g / 100 g | 2.09 g / 100 g | **Wrong** | `ML_KIT_RECOGNITION` |
| `real_jar_prose_multilingual_03.jpg` | 1.6 g / 100 g | 20.0 g / 100 g | **Wrong** | `ROW_RECONSTRUCTION` / `ROW_CLASSIFICATION` |
| `real_lid_prose_curved_04.jpg` | 3 g / 100 g | 19.0 g / 100 g | **Wrong** | `ROW_RECONSTRUCTION` / `ROW_CLASSIFICATION` + `COLUMN_CLASSIFICATION` |
| `real_witte_kaas_single_column_05.jpg` | 2.3 g / 100 g | NotFound | NotFound | `ML_KIT_RECOGNITION` |
| `real_stokbrood_prose_dense_06.jpg` | 46 g / 100 g | NotFound | NotFound | `ROW_CLASSIFICATION` |
| `real_yoghurt_serving_column_07.jpg` | 5.0 g / 100 g | NotFound | NotFound | `COLUMN_CLASSIFICATION` |
| `sondey_multilingual_100g.jpg` | 61.9 g / 100 g | 61.9 g / 100 g | **Confident, correct** | — |
| `kinder_multicolumn_piece.jpg` | 53.5 g / 100 g; 6.7 g/piece | 53.5 g / 100 g; 6.7 g/piece | **Confident, correct** | — |

**Current pass rate: 2 of 9** (both pre-existing fixtures). **3 of 9 are `Wrong`** (a confident,
incorrect answer — the most dangerous outcome, worse than `NotFound`). **4 of 9 are `NotFound`**
(the safe, expected-when-uncertain outcome).

## Key-goal measurements, answered explicitly

**(a) For prose fixtures 3, 4, 6: does the basis phrase end up on the SAME logical row as the
carbohydrate term and its value?**

- Fixture 3 (jar): **No.** Basis phrase (`'Naringsindhold (100g): ...'`) is its own `HEADER` row;
  the carbohydrate term/value sit on a different row several rows later.
- Fixture 4 (lid): **No.** Basis phrase (`'PourPerlPro 100g: ...'`) is its own `HEADER` row; the
  carbohydrate term/value sit on a different row.
- Fixture 6 (stokbrood): **N/A in the row-declaration sense, but structurally different and worth
  flagging.** The basis phrase (`'100g: energie 1312kJ ...'`) is on its own `HEADER` row, separate
  from the row carrying `koolhydraten...46g,waarvan suikers 1,0 g.` — so by the same measure, no.
  But this fixture's actual failure has nothing to do with basis distance: the carbohydrate row never
  gets typed `TOTAL_CARBOHYDRATE` at all (see (b)), so no basis-binding rule, however implemented,
  gets a chance to run on it.

**Conclusion for (a): 0 of 3 prose fixtures put the basis declaration and the carbohydrate
term+value on the same logical row.** In every case the basis phrase is a separate, standalone
sentence/clause that becomes its own logical row (typed `HEADER`), while the carbohydrate figure sits
on a different row. **A structural "declaration" rule that requires the basis and the carbohydrate
value to co-occur on one logical row will not fire on any of these three real fixtures as currently
segmented.** The two fixtures that did reach `Confident` (3 and 4) did so via the *existing*
column-classification mechanism — a `PER_100_G` column resolved from the standalone header row and
was then applied by x-position to whatever row was typed `TOTAL_CARBOHYDRATE`, regardless of row
adjacency or sentence-boundary relationship to that header. That is column-based binding, not
same-row declaration binding, and it is what's currently producing (wrong) answers.

**(b) For every fixture: does any single logical row contain BOTH a carbohydrate term and a child
term?**

| Fixture | Any row with both a carb term and a child term? | Row text |
|---|---|---|
| 1 juice | No | — |
| 2 cheese | No | — |
| 3 jar | No | — |
| 4 lid | No | — |
| 5 witte kaas | No | — |
| 6 stokbrood | **Yes** | `'ren 6,4 g, koolhydraten 46g,waarvan suikers 1,0 g.'` |
| 7 yoghurt | No | — |
| 8 sondey | No | — |
| 9 kinder | No | — |

**Conclusion for (b): 1 of 9 fixtures (fixture 6, stokbrood) has a row satisfying the predicate**,
and it is exactly the row that needs prose handling — the predicate would fire correctly and
precisely on the one fixture built to need it. It does **not** false-fire on fixtures 3 or 4, which
are also prose labels but happen to keep the carbohydrate term and its child term on separate
printed lines (so their `TOTAL_CARBOHYDRATE`-typed rows never also contain a child term). This means
the predicate, as defined, is **narrower** than "this is a prose label" — fixtures 3 and 4 are prose
labels that currently fail for entirely different reasons (wrong-number-on-shared-row, not
parent+child-on-shared-row) and the predicate would not flag them as prose-eligible at all under its
current definition.

**(c) Photo 4 (round lid): how many logical rows, and does curvature fragment it?**

**27 logical rows**, one substantially per printed line of the label (visually confirmed: the label
has roughly two dozen distinct printed lines across 6 languages plus a footer block). **No
fragmentation attributable to curvature was observed.** The image-level skew estimate was a small,
uniform -0.0139 (dy/dx) — consistent with ordinary photograph tilt, not a curvature signal that
varies across the row. `LogicalRowBuilder`'s box-geometry row reconstruction produced clean,
one-line-per-row output across the entire ring, including the portion of text that visually curves
most (top and bottom of the circular label in the original photograph). The lid's actual failure
(described above) is a same-defect recurrence of the prose-sentence/row-boundary mismatch seen in
fixture 3, not a curvature-specific defect. **Curvature was not the variable that broke this
fixture.**

## Assessment: is the approved structural basis-binding rule viable as specified?

**No, not as a same-logical-row requirement — it needs amendment.** All three prose fixtures put the
basis declaration on its own standalone logical row, never sharing a row with the carbohydrate term
and value. A rule requiring same-row co-occurrence would find zero eligible declarations across this
entire real-world sample and would leave fixtures 3, 4, and 6 exactly where they are today (Wrong,
Wrong, NotFound) — it would not be a regression, but it would also not be the fix, because it could
never fire.

What *does* currently reach a basis figure (fixtures 3 and 4, both to the wrong number) is the
existing column-classification mechanism: a `PER_100_G` column resolves from a standalone header row
by text content alone, then gets applied by x-coordinate to whichever row is typed
`TOTAL_CARBOHYDRATE`, with no requirement that the two rows be nearby, adjacent, or related beyond
sharing an x-band. That mechanism is already doing cross-row binding — it's just doing it too
loosely (any `TOTAL_CARBOHYDRATE`-typed row in the whole document, regardless of distance, picks up
the same column) and it's operating on rows that can carry two unrelated numbers (a table-column
model applied to a prose paragraph).

A workable structural rule, if the design intends to keep the "declaration" framing, likely needs to
be relaxed from *same logical row* to something like *nearest preceding basis declaration within the
prose block*, or a rule that operates at the level of the reconstructed sentence/clause (splitting a
row's text at its own internal number boundaries) rather than the level of the printed-line-derived
logical row. Either direction is a real design change, not a threshold tweak — flagging for the
owner rather than deciding unilaterally, per the task's scope limits.

## Anything that surprised me

- **`Wrong` outcomes (3 of 9) outnumber the pass rate on new fixtures (0 of 7 new fixtures pass)** —
  every one of the seven newly-cropped real photographs either fails safely (`NotFound`, 4 cases) or
  fails dangerously (`Wrong`, confidently returning a fat or sugar figure as the carbohydrate value,
  3 cases). Zero of the seven new fixtures reach a correct `Confident` reading. This is a starker
  result than "the design needs prose handling" — it says the *existing* column-to-row binding
  mechanism is actively unsafe on prose labels, producing wrong answers with the same confidence
  score (105) as a correct table read. Any prose-eligibility gate needs to also consider suppressing
  or downgrading the *existing* column-based `Confident` path on rows it should not trust, not only
  adding a new prose path.
- **The "wrong number selected" failure mode (fixtures 3, 4) is a genuinely different bug shape than
  the "no row survives classification" failure mode (fixture 6)**, even though both are "prose
  labels going wrong." The plan's prose-eligibility predicate (b) targets fixture 6's shape exactly
  and correctly does not fire on fixtures 3/4's shape — which means fixing fixture 6 via the planned
  predicate will do nothing for fixtures 3 and 4. Those need the basis-binding / row-boundary fix
  discussed above, a separate mechanism.
- **Two distinct OCR corruption patterns turn a unit into a digit**: `"g"` → `"9"` (fixtures 2, 5)
  and a merged `"100 ml"` → `"100mi"` (fixture 1). Both are recognition-stage, not
  interpretation-stage, and both are exactly the kind of finding the task instructed to record for
  Task 8 rather than patch here.
- **Fixture 4's curvature turned out to be a non-issue for row reconstruction** — the geometry-first
  row builder handled the curved ring cleanly. The controller's instinct to test it was reasonable
  (curvature was untested before), but the measured result is a clean negative: nothing here
  motivates curvature-specific work.
- **A prose sentence describing "no added sugars" false-positives the child-term exclusion** (fixture
  5's `'Bevat alleen van nature aanwezige suikers.'`, harmless in this run only because the fixture
  was already `NotFound` for an unrelated reason). Worth remembering once any future stage starts
  trusting `CARBOHYDRATE_CHILD` rows as strong evidence of "this document is a real nutrition
  sentence" — ordinary marketing prose can trigger the same row-kind.
