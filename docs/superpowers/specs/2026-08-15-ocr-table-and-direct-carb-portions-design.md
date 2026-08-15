# OCR table interpretation + direct-carb countable portions — design

Date: 2026-08-15
Status: approved, implementation pending

## Problem

Two real physical-device failures drive this pass:

1. The OCR nutrition-table parser can choose a child nutrient (sugars, dextrose, a
   `%RI` figure) as "total carbohydrate" on multi-column or hierarchical real labels,
   because it relies on ML Kit `blockId`/`lineId` and proximity scoring rather than an
   actual reconstructed table.
2. Countable portions (slice/piece/sachet) currently require a known per-unit weight.
   When OFF or a label only gives per-serving carbs (no weight), or only a bare count
   ("1 slice" with no bracketed weight), the user is forced to weigh food that a
   trustworthy carbohydrate relationship already describes.

A small, unrelated regression is also fixed: `SearchViewModel` can let an in-flight
request for an edited-away query populate state under the new query text, and the
"15 requests/minute" comment for OFF Search is wrong (the actual limit is 10).

## Non-goals

No LLM/cloud OCR, no photo upload, no Robotoff/Nutri-Sight, no average-slice-weight
estimation, no scraping, no category/name-based unit inference, no calorie/insulin
tracking, no accounts/analytics, no unrelated refactor or historical-doc rewrite.

---

## 0. Search invalidation fix

**File:** `ui/search/SearchViewModel.kt`

Current gap: `onQueryChanged` never touches `requestId`, so an in-flight request for
an earlier submitted query can still win the "am I still the latest request" check in
`runSearch` if the user only edited the text field without submitting a new search —
because that check only compares against the last *bumped* `requestId`, which nothing
in `onQueryChanged` invalidates. Concretely: submit `bread` (requestId=1) → edit text
to `bread wholegrain` (requestId still 1, no new request started) → old `bread`
request completes → `thisRequestId(1) == requestId(1)` → stale results are written
under the new editor text.

Fix: `onQueryChanged` must invalidate the previous request whenever the edited text
diverges from the query the currently-displayed results/error/no-match state belongs
to (`displayedQuery`, a new field distinct from `lastSubmittedQuery`):

- bump `requestId` (so any in-flight `runSearch` for the old query becomes stale and
  its late completion no-ops on arrival);
- cancel the current `searchJob` if one is running;
- clear `hits`/`noMatches`/`error` from state (they belonged to a different query);
- do **not** start a new job — editing never searches.

`lastSubmittedQuery` keeps its existing job: deduplicating a second explicit `search()`
call for a query whose results are already the ones on screen. It must NOT prevent a
changed-away-and-back resubmission — since `onQueryChanged` already cleared
`displayedQuery`/results when the text diverged, by the time the user retypes the
original text and taps search, `displayedQuery` no longer matches, so the dedup guard
(rephrased to compare against `displayedQuery`, not just the raw last-submitted
string) correctly allows it.

Net state shape:

```kotlin
data class SearchUiState(
    val query: String = "",       // editor text, always live
    val searching: Boolean = false,
    val hits: List<ProductSearchHit> = emptyList(),
    val noMatches: Boolean = false,
    val error: LookupError? = null,
)

private var displayedQuery: String? = null   // query the current hits/error/noMatches belong to
private var searchJob: Job? = null
private var requestId = 0L
```

`search()` dedups when `terms == displayedQuery` (a result is already showing for this
exact text) rather than `lastSubmittedQuery` — this is the change that makes A→B→A
work, since `displayedQuery` was cleared during the edit to B.

Also fix the "15 requests/minute" comment in the 5 files the explore pass found
(`SearchViewModel.kt:35`, `OpenFoodFactsApi.kt:39`, `OpenFoodFactsDataSource.kt:115`,
`ProductRepository.kt:47`, `ProductRepositoryTest.kt:46`) — OFF Search's limit is
**10 requests/minute/IP**, distinct from the 15/min read limit those comments were
conflating it with. Only the Search-specific comments change; the product-read-path
15/min comments are correct as-is and stay.

### Tests

In `SearchViewModelTest.kt`, add cases proving:

- typing alone never calls the fake search source;
- editing after a submitted search invalidates the in-flight job (assert the fake
  source's deferred is never awaited into state — i.e. resolving it after the edit
  does not change `hits`);
- changing text A → submit → edit to B (no submit) → edit back to A → submit again
  performs a **new** search for A (not deduped away);
- stale `hits`/`noMatches`/`error` are cleared immediately on edit, before any new
  submission;
- two explicit `search()` calls for the same unchanged, currently-displayed query
  remain deduplicated (existing behavior preserved).

---

## 1-4. OCR: geometry-first logical table layer

**New files under `ocr/`, pure Kotlin, zero Android imports:**

```text
LogicalRow.kt          -- LogicalRow, LogicalRowBuilder
NutritionRowKind.kt     -- TOTAL_CARBOHYDRATE | CARBOHYDRATE_CHILD | HEADER | OTHER
NutritionColumnKind.kt  -- PER_100_G | PER_100_ML | PER_SERVING | REFERENCE_PERCENT | UNKNOWN
NutritionTableInterpreter.kt  -- orchestrates row build -> classify -> column classify -> cell association
```

`NutritionTableParser.kt` becomes a thin adapter: build `OcrDocument` (unchanged) →
`NutritionTableInterpreter` → `NutritionParseReport(reading, servingCandidate)`. The
existing `LabelReading` sealed type (`Confident`/`Ambiguous`/`NotFound`),
`CarbCandidate`, `CandidateEvidence`, `OcrDiagnostic` stay as the canonical per-100
contract — nothing in `LabelAnalyzer`, `AmbiguityStabilityTracker`, or the live-scan
UI needs to change to keep working against the canonical reading.

### 1.1 `LogicalRowBuilder`

Row membership is a new, independent, stricter geometry rule — **not** a reuse of the
existing scoring-pass constants (`MIN_VERTICAL_OVERLAP`/`MAX_ROW_DISTANCE_IN_HEIGHT`),
because those were tuned as one signal among many in a scoring model, and row
reconstruction now has to be an authoritative hard boundary other stages build on.

```kotlin
data class LogicalRow(
    val elements: List<OcrElement>,   // left-to-right
    val box: OcrBox,                  // union of all element boxes
    val sourceLines: Set<LineKey>,    // original blockId/lineId pairs, diagnostics only
)

object LogicalRowBuilder {
    fun build(document: OcrDocument): List<LogicalRow>
}
```

Algorithm: sort all elements by `centerY`, then `left`. Walk in order, growing a
current row while each next element has `verticalOverlapRatio(rowBox) >= 0.5` against
the row's *running* union box (tighter than the existing 0.35 scoring threshold,
since this is now a hard split not a soft signal); if overlap is inconclusive (some
overlap but below 0.5) fall back to `centerY` distance `<= medianElementHeight * 0.6`
as a narrow tiebreaker (tighter than the existing 1.10) before starting a new row.
`blockId`/`lineId` are never consulted for row membership — only retained per-element
for diagnostics. Sort each finished row's elements left-to-right by `left`.

This directly satisfies the required regression cases: two elements from different
ML Kit lines but genuinely on the same printed row merge (overlap/closeness says so);
two elements ML Kit merged onto one line but that are geometrically on different
printed rows (e.g. "Carbohydrate 45g" baseline vs. "sugars 8g" baseline one line
below) split, because the *geometry* — not the shared `lineId` — decides.

### 2. `RowClassifier`

```kotlin
enum class NutritionRowKind { TOTAL_CARBOHYDRATE, CARBOHYDRATE_CHILD, HEADER, OTHER }

object RowClassifier {
    fun classify(row: LogicalRow): NutritionRowKind
}
```

Reuses `NutritionTerminology.normalize`/`containsTerm`. Order of checks, first match
wins:

1. Any child term present (existing `exclusionTerms`, extended per Section 2 of the
   task: sugar(s), of which sugars, added sugar(s), polyol(s), starch, fibre/fiber,
   dextrose, glucose, fructose, sucrose, lactose, maltose, plus existing multilingual
   equivalents) → `CARBOHYDRATE_CHILD`, **unconditionally** — this row can never
   become `TOTAL_CARBOHYDRATE` regardless of any other text or score on it. This is
   the hard rule from the task: no scoring penalty, an outright type-level exclusion.
2. Else a carbohydrate term present (existing `carbohydrateTerms`) → `TOTAL_CARBOHYDRATE`.
3. Else a header-like term present (per-100/per-serving/%RI vocabulary, reusing/
   extending existing header terminology) → `HEADER`.
4. Else → `OTHER`.

The interpreter requires **at most one** `TOTAL_CARBOHYDRATE` row; if more than one
distinct row classifies as `TOTAL_CARBOHYDRATE` with conflicting values that cannot be
resolved as the same figure, that is genuine row-level ambiguity (see §4 below).

### 3. `ColumnClassifier`

```kotlin
enum class NutritionColumnKind { PER_100_G, PER_100_ML, PER_SERVING, REFERENCE_PERCENT, UNKNOWN }

data class NutritionColumn(val kind: NutritionColumnKind, val headerBox: OcrBox?, val centerX: Double)

object ColumnClassifier {
    fun classify(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn>
}
```

Primary signal: classify each `HEADER`-kind row's elements/spans against per-100(g/ml),
per-serving/portion, and %RI/%DV terminology (extending the existing header
vocabulary), producing one `NutritionColumn` per recognized header span, positioned at
that header's `centerX`.

Fallback (per your answer): when no `HEADER` row is found, or a candidate value
column has no header confidently classified, inspect that column's own cell values —
a column whose numeric cells consistently carry a trailing `%` (or a `%` element
adjacent on the same row) classifies as `REFERENCE_PERCENT` even without a matching
header. This exists specifically so a percent column can never be *mistaken* for
carbohydrate grams even when OCR drops or mangles its header; it does not attempt to
guess PER_100_G vs PER_SERVING from shape alone — those two still require a header
match, else `UNKNOWN`. A cell in an `UNKNOWN` column is never used as the canonical or
serving carbohydrate value.

### 4. Cell association + interpretation

For the row classified `TOTAL_CARBOHYDRATE`:

1. Extract its numeric cells (existing `findNumbers` regex + gram-unit detection,
   reused as-is).
2. Associate each numeric cell to the `NutritionColumn` whose `centerX` it aligns with
   (reusing the existing strict/loose horizontal-alignment fractions from
   `columnEvidence`, now measured against classified columns instead of a raw header
   element).
3. A cell aligned to `REFERENCE_PERCENT` or `UNKNOWN` is discarded — never eligible as
   carbohydrate grams.
4. A cell aligned to `PER_100_G`/`PER_100_ML` is the canonical candidate; its column's
   kind also fixes the result's `NutritionBasis`.
5. A cell aligned to `PER_SERVING` becomes the `ServingCarbCandidate` value.

Confidence: unchanged principle ("confidently correct or explicitly ask"). A unique,
column-resolved `PER_100_G`/`PER_100_ML` cell on the one `TOTAL_CARBOHYDRATE` row is
`Confident`. If the total-carb row's canonical cell cannot be uniquely resolved to one
column (e.g. two candidate cells both plausibly align to `PER_100_G` within the loose
fraction, with no strong winner) → `Ambiguous`, carrying those candidates through the
existing `AmbiguityStabilityTracker` path unchanged. A child row's value is never a
candidate in that ambiguity set — it was excluded at the row-classification stage,
before cell association ever runs. No column resolved at all → `NotFound`.

```kotlin
data class ServingCarbCandidate(
    val carbsPerServing: BigDecimal,
    val descriptor: String?,   // e.g. "per slice", "per portion" header text, diagnostics/display only
)

data class NutritionParseReport(
    val reading: LabelReading,
    val servingCandidate: ServingCarbCandidate?,
)
```

The live-scan confidence/stability behavior in `LabelAnalyzer`/`AmbiguityStabilityTracker`
continues to key off `reading` only; `servingCandidate` is additional metadata a
verification screen can offer to save (§17), never a replacement for the canonical
per-100 result and never itself gating live-scan stability.

A serving-per-N-slices column (e.g. "per 2 slices") only yields a per-unit figure
(`carbsPerServing / count`) when the count is explicitly and confidently read from the
header text itself (a leading digit before the unit word) — never inferred.

---

## 6-10. Countable-portion domain model

### `PortionConversion`

```kotlin
sealed interface PortionConversion {
    data class WeightBased(val amountPerUnit: BigDecimal, val basis: NutritionBasis) : PortionConversion
    data class DirectCarbs(val carbsPerUnit: BigDecimal) : PortionConversion
}
```

One conversion per `PortionUnit`, no diagnostic sidecar field (per your answer) — in
Case C (both a weight and a serving-carbs figure exist), `WeightBased` is the value
that gets stored; the serving-carbs figure is used only as a transient rounding
sanity-check at the point the candidate is constructed (tolerate normal label
rounding, never surfaced as a conflict, never persisted as a second number).

`PortionUnit` changes from `amountPerUnit: BigDecimal, basis: NutritionBasis` to
`conversion: PortionConversion`. Provenance/freeze fields generalize in place:

```kotlin
data class PortionUnit(
    val id: Long = 0,
    val productBarcode: String,
    val kind: PortionUnitKind,
    val customLabel: String?,
    val conversion: PortionConversion,
    val dataSource: ProductDataOrigin,
    val verificationStatus: VerificationStatus,
    val verifiedAt: Instant?,
    val originalRemoteConversion: PortionConversion?,
    val latestRemoteConversion: PortionConversion?,
    val rawRemoteServingText: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isRemoteRefreshable: Boolean get() = !dataSource.isUserAuthored && verificationStatus == VerificationStatus.UNVERIFIED
    val remoteConversionDiffers: Boolean get() = latestRemoteConversion?.let { it != conversion } == true
}
```

`isRemoteRefreshable` and the freeze/apply flow in `ProductRepository`
(`refreshPortionUnitFromCandidate`, `applyLatestRemotePortionUnit`) keep their exact
current shape and reasoning — they now branch on `PortionConversion` type instead of
reading a raw decimal, but the freeze rule itself (never silently overwrite a
user-verified unit) is unchanged and applies identically to both conversion kinds.

### `ServingSizeParser` → descriptor/weight split

```kotlin
data class ServingDescriptor(
    val kind: PortionUnitKind,
    val count: BigDecimal,
    val weightOrVolume: AmountWithBasis?,   // null when no bracketed weight present
    val rawText: String,
)

data class AmountWithBasis(val amount: BigDecimal, val basis: NutritionBasis)
```

`ServingSizeParser.parse` is refactored into: a descriptor pattern (`count + unit
word`, weight now optional) that succeeds for "2 slices", "1 sachet", "1 portion" as
well as the existing "2 slices (70 g)" form; when a bracketed weight is present it's
extracted into `weightOrVolume` exactly as today. Singular/plural normalization,
existing English/Dutch term list, and generic serving/portion (only when explicitly
present in source text) are preserved unchanged — no speculative new vocabulary.

### OFF: `carbohydrates_serving`

`OffNutriments` gains `@SerialName("carbohydrates_serving") val carbohydratesServing: Double? = null`,
validated through the existing safe-numeric path (`NutritionValueValidator`-style
check) with no upper-bound assumption — a serving can legitimately exceed 100g/100ml
so the existing `<=100` check used for `carbohydrates_100g` is not reused here as-is;
a serving-carbs-specific validation (positive, finite, sane magnitude ceiling far
above 100, e.g. reject only clearly corrupt data) is added instead.

`OpenFoodFactsDataSource` mapping produces a `PortionUnitCandidate` carrying a
`PortionConversion` per Section 9's precedence:

- weight present in the parsed descriptor → `WeightBased(weight/count, basis)`
  (Cases A & C — weight-backed wins whenever a valid weight relationship exists);
- no weight, `carbohydrates_serving` present → `DirectCarbs(carbsPerServing / count)`
  (Case B);
- neither → no `PortionUnitCandidate` is produced for that descriptor at all (Case D)
  — the app may still know the product describes "1 slice" from `serving_size` text
  alone, but does not fabricate a conversion; the countable-unit UI path for that
  product falls to the "how much does 1 slice weigh?" one-time prompt.

```kotlin
data class PortionUnitCandidate(
    val kind: PortionUnitKind,
    val conversion: PortionConversion,
    val rawServingText: String,
)
```

### Meal item persistence generalization

```kotlin
enum class MealItemKind { WEIGHT_BASED, DIRECT_CARBS }

data class MealItem(
    val id: Long = 0,
    val productBarcode: String?,
    val displayName: String,
    val portionDescription: String,
    val kind: MealItemKind,
    val resolvedAmount: BigDecimal?,   // WEIGHT_BASED only
    val basis: NutritionBasis?,        // WEIGHT_BASED only
    val carbsPer100: BigDecimal?,      // WEIGHT_BASED only
    val count: BigDecimal?,            // DIRECT_CARBS only
    val carbsPerUnit: BigDecimal?,     // DIRECT_CARBS only
    val exactCarbs: BigDecimal,        // always present, both kinds
    val addedAt: Instant,
)
```

`exactCarbs` and `portionDescription` are always populated (both kinds need an
auditable total and a human-readable line); the kind-specific fields are nullable in
a way the `kind` discriminant makes unambiguous to read — not a general-purpose
nullable soup, exactly two legitimate shapes gated by one enum, mirroring the
`PortionConversion` split at the persistence layer since Room can't store a sealed
type directly. `MealTotal.exact`/`asResult` are unaffected (they only ever read
`exactCarbs`/`basis`, and `basis` handling for the total's display already tolerates
`items.firstOrNull()`).

### Centralized calculation (Section 13)

`domain/PortionResolver.kt` (or an adjacent new pure object, given `PortionResolver`
today is weight-only multiplication) gains the direct-carb entry point; both live
beside/extend the domain calculator layer, `BigDecimal` throughout, calculation
precision kept separate from display rounding (`ResultFormatter` unchanged):

```kotlin
// weight-based (existing PortionResolver.resolve, unchanged) feeds CarbCalculator as today
// direct-carb, new:
object DirectCarbCalculator {
    fun exactCarbs(count: BigDecimal, carbsPerUnit: BigDecimal): BigDecimal
}
```

---

## 11. Room v6

`portion_units` rebuild-and-copy (SQLite can't drop a NOT NULL constraint in place):

```sql
CREATE TABLE portion_units_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  productBarcode TEXT NOT NULL,
  kind TEXT NOT NULL,
  customLabel TEXT,
  conversionKind TEXT NOT NULL,        -- 'WEIGHT' | 'DIRECT_CARBS'
  conversionValue TEXT NOT NULL,       -- decimal-as-string (amountPerUnit or carbsPerUnit)
  conversionBasis TEXT,                -- 'PER_100_G' | 'PER_100_ML', NULL iff DIRECT_CARBS
  dataSource TEXT NOT NULL,
  verificationStatus TEXT NOT NULL,
  verifiedAt INTEGER,
  originalRemoteConversionKind TEXT,
  originalRemoteConversionValue TEXT,
  originalRemoteConversionBasis TEXT,
  latestRemoteConversionKind TEXT,
  latestRemoteConversionValue TEXT,
  latestRemoteConversionBasis TEXT,
  rawRemoteServingText TEXT,
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL,
  FOREIGN KEY(productBarcode) REFERENCES products(barcode) ON DELETE CASCADE
);
-- copy: conversionKind='WEIGHT', conversionValue=amountPerUnit, conversionBasis=basis,
--       originalRemoteConversionKind = CASE WHEN originalRemoteAmountPerUnit IS NOT NULL THEN 'WEIGHT' END,
--       ... same pattern for latestRemote*
INSERT INTO portion_units_new SELECT ... FROM portion_units;
DROP TABLE portion_units;
ALTER TABLE portion_units_new RENAME TO portion_units;
CREATE INDEX index_portion_units_productBarcode ON portion_units (productBarcode);
```

IDs, FKs, `customLabel`, `kind`, `dataSource`, `verificationStatus`, `verifiedAt`,
`rawRemoteServingText`, `createdAt`/`updatedAt` all copy straight across unchanged, so
`portion_usage` (FK's target `portion_units.id`) needs no changes at all — IDs are
preserved by the copy. No `fallbackToDestructiveMigration`.

`current_meal_items` — additive guarded `ALTER TABLE ADD COLUMN` (same
`hasColumn`-guarded pattern as `MIGRATION_2_3`/`MIGRATION_4_5`), all nullable:

```sql
ALTER TABLE current_meal_items ADD COLUMN itemKind TEXT;      -- NULL = legacy row = WEIGHT_BASED
ALTER TABLE current_meal_items ADD COLUMN count TEXT;
ALTER TABLE current_meal_items ADD COLUMN carbsPerUnit TEXT;
```

Existing `resolvedAmount`/`basis`/`carbsPer100` stay `NOT NULL` — still correct for
every pre-migration row, which are all `WEIGHT_BASED` by construction. The
entity-to-domain mapper treats `itemKind IS NULL` as `WEIGHT_BASED` for backward
compatibility with rows written before this migration.

Both table changes ship in one `MIGRATION_5_6`, registered alongside the existing
migrations. Schema JSON exported to `app/schemas/.../6.json`.

### Migration test

New test(s) in `JustTheCarbsDatabaseMigrationTest.kt`: seed a v5 database with a
representative mix (an unverified OFF-sourced weight unit, a user-verified weight
unit with `originalRemoteAmountPerUnit` set and diverging `latestRemoteAmountPerUnit`,
a manually-entered weight unit, plus an existing `current_meal_items` row and a
`portion_usage` row referencing one of the portion unit IDs) → run `MIGRATION_5_6` →
assert every row survives with the same id, the weight fields losslessly reflected as
`conversionKind='WEIGHT'` with matching value/basis, remote-diff fields correctly
carried over, and the `portion_usage` FK still resolves to the same portion unit id.

---

## 14-15. Product UI

Wherever the current weight-backed portion-count screen lives (count input → result),
branch display on `PortionUnit.conversion`:

- `WeightBased`: unchanged today's line, `"4 slices × 35 g = 140 g"`, feeding
  `CarbCalculator` via `PortionResolver.resolve` as today.
- `DirectCarbs`: `"4 slices × 14.2 g carbs = 56.8 g"` via `DirectCarbCalculator`, no
  grams shown or implied anywhere on that path.

Add/edit portion-unit UI gets a compact two-mode toggle — "Weight" (`1 slice = [ ] g`)
or "Carbs per unit" (`1 slice = [ ] g carbs`) — instead of only accepting weight.
Neither is required to be filled if the other is chosen; validation is per-mode
(positive decimal, matching the project's existing input-validation conventions).
Existing correction/verification affordances (the inline "this weight/value is wrong,
here's the right one" flow) work for both, since both are just a `PortionConversion`
being replaced/verified through the same freeze-aware repository methods.

---

## 16. OFF countable-portion integration — worked examples

| `serving_size` | `carbohydrates_serving` | Result |
|---|---|---|
| `"2 slices (70 g)"` | any/absent | `WeightBased(35, PER_100_G)` |
| `"2 slices"` | `25.2` | `DirectCarbs(12.6)` |
| `"1 slice"` | absent | no `PortionUnitCandidate` — Case D, user prompted once, persisted after |

---

## 17. OCR → save flow (full scope, per owner decision)

The label-scanner/verification screen (wherever `LabelReading`/`NutritionParseReport`
currently surfaces its result for accept/correct) gains a "Save as portion unit"
affordance, shown only when `servingCandidate` is present on a `Confident` report and
the descriptor's unit count was explicitly read (not inferred). Tapping it is the
explicit-acceptance step — same pattern as existing OCR label-verification
accept/correct flows — that constructs a `PortionUnit` with
`dataSource = ProductDataOrigin.OCR`:

- if the same OCR pass also confidently read a countable descriptor with a weight
  (e.g. a "per slice" column alongside a legible "2 slices (70 g)"-shaped label
  elsewhere on the same capture) and the weight/serving figures agree within normal
  rounding, save `WeightBased`;
- otherwise, from `servingCandidate.carbsPerServing` and the explicitly-read count,
  save `DirectCarbs(carbsPerServing / count)`.

Never auto-saved from a live camera frame — only from a still capture that has passed
through the existing explicit accept step, matching the "never silently persist a
live camera prediction" requirement. This is additive UI on top of the existing
label-scanner verification screen, not a new screen.

---

## 18. OCR regression test fixtures

New `OcrDocument` fixtures in the parser test file (or a new
`LogicalRowBuilderTest.kt`/`NutritionTableInterpreterTest.kt` alongside it) covering,
each built with deliberately adversarial `blockId`/`lineId` vs. geometry:

1. Same physical row, different ML Kit line IDs → still one `LogicalRow`, correct
   total extracted.
2. Different physical rows (carb total + sugars beneath it), same ML Kit line ID →
   two `LogicalRow`s; sugars value never wins.
3. Sugars value placed geometrically *closer* to the carbohydrate label text than the
   correct total cell → total still wins (proves column/row-kind resolution beats raw
   proximity).
4. Dextrose child row → excluded, total wins.
5. Two-column (100g/serving) table → both `per100` and `serving` extracted, not
   flagged ambiguous.
6. `%RI`/`%DV` column present alongside grams → grams wins, percent never selected.
7. Genuinely unresolvable two-candidate total-carb layout → `Ambiguous`, never
   `.first()`-resolved.

## 19. Portion/domain test list

Per Section 19 of the task, covering weight-backed, direct-carb, descriptor parsing
(including bare/no-weight forms and existing Dutch terms), OFF mapping for all three
worked-example rows above, no-conversion Case D, user-defined direct-carb units,
remote direct-carb refresh/freeze, verified-unit-not-silently-overwritten, v5→v6
migration, `portion_usage` FK preservation, direct-carb meal addition, and "no fake
resolved grams" (a `DirectCarbs` `MealItem` has `resolvedAmount == null`).

## 24. Documentation

Update (do not rewrite historical docs): the "Countable portions" section of
`CLAUDE.md` to describe `PortionConversion`, the geometry-first OCR architecture
summary, Room v6, and the closed OCR-total-vs-child-nutrient gap. Add a short
"geometry-first nutrition table parsing" note to whichever doc currently describes
the OCR approach (if one exists) or to `CLAUDE.md` directly if not.
