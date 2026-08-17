# Real-image OCR generalization pass — design

Date: 2026-08-16
Status: approved for implementation (owner, 2026-08-16)

## Why this pass exists

Seven new photographs of real packages, taken on a physical phone, are the primary evidence for this
pass. They join the two existing real fixtures (Sondey, Kinder) that closed the previous real-device
failures.

The purpose is **generalization**, not nine green snapshots. Every change below must be expressible as
a rule about a *class* of nutrition labels. Package-specific coordinates, brand checks and value
literals in production code are forbidden.

The safety rule is unchanged and overrides recognition rate:

> A false confident value is substantially worse than `NotFound`.

## What the seven photographs are

Transcribed by reading the labels, not by running the parser. Where a value could not be determined
visually it is flagged rather than invented.

**The golden values for photos 1–7 were independently visually confirmed by the owner on 2026-08-16.**
This table is the one artifact in the pass that no amount of running code can validate — every
assertion in the real-image suite encodes it — so it is settled evidence from here on. A future
session finding a disagreement between this table and the parser must re-read the photograph before
concluding the parser is wrong.

| # | Fixture | Package | Layout class | Total carbohydrate | Basis | Child value(s) | Languages |
|---|---|---|---|---|---|---|---|
| 1 | `real_juice_bilingual_per100ml_01.jpg` | Orange juice bottle | Single column, rotated | **9,0 g** | **per 100 ml** | sugars **9,0 g** | NL, DE |
| 2 | `real_grated_cheese_multicolumn_02.jpg` | Lidl grated cheese | 2 columns + %RI | **2,0 g** | per 100 g | sugars 0,5 g | NL |
| 3 | `real_jar_prose_multilingual_03.jpg` | Ozener 500 g jar | Run-on prose | **1,6 g** | per 100 g | sugars 1,6 g | NL, TR, DA, FR, DE |
| 4 | `real_lid_prose_curved_04.jpg` | Bel cheese lid 125 g | Prose on a curved lid | **3 g** | per 100 g | sugars 2,5 g | FR, NL, EN, DE, DA, SV |
| 5 | `real_witte_kaas_single_column_05.jpg` | Melkan Witte Kaas 200 g | Single column | **2,3 g** | per 100 g | sugars **2,3 g** | NL |
| 6 | `real_stokbrood_prose_dense_06.jpg` | AH stokbrood | Dense receipt prose | **46 g** | per 100 g | sugars **1,0 g**, fibre **4,7 g** | NL |
| 7 | `real_yoghurt_serving_column_07.jpg` | AH yoghurt | 2 columns + %RI block | **5,0 g** | per 100 g | sugars **5,0 g** | NL |

Photo 2 additionally prints a genuine per-serving column: header `ø/portie 50 g`, carbohydrate
**1,0 g**, and the text `Deze verpakking bevat 5 porties van 50 g`.

Photo 7 additionally prints a per-serving column: `schaaltje (150 g)` with carbohydrate **7,5 g**, and
a separate `%RI` block in which carbohydrate carries **3,0 %**. Its serving arithmetic is exact:
`5,0 × 150 / 100 = 7,5`.

Photo 1 is printed rotated 90° on the bottle. Photo 4's text follows the curve of a circular lid.

### Correction to an earlier transcription

Photo 6 was first transcribed here with `4,7 g` as its sugars figure. That is wrong: `4,7 g` is
**fibre** (`vezels`), and the sugars figure is `1,0 g`. Corrected above by re-reading the source
photograph. The error mattered — it would have produced a negative assertion guarding a value that
was never the risk while leaving the actual sugars figure unguarded.

### Two properties that make these different from the existing fixtures

**Identical total and child values (photos 1, 3, 5, 7).** The total and the "of which sugars"
figure are the *same printed number* — `9,0`, `1,6`, `2,3` and `5,0` respectively. A test asserting
only the value passes even if the parser read the sugars row. These fixtures therefore assert
provenance as well as value.

Four of the seven new photographs have this property. That is high enough to be a characteristic of
the category rather than a quirk of this selection: on dairy and juice, where essentially all
carbohydrate is sugar, the two figures are routinely printed identically. The corpus is not
unrepresentative for containing them — a parser that reads the sugars row would look correct on most
dairy labels and fail on bread.

**Correction (2026-08-16, after cropping):** photo 4 was first transcribed here with `2,5 g` as its
total. Reading the crop directly, the label prints
`Glucides / Koolhydraten / Kohlenhydrate / Carbohydrate / Kolhydrat / Kulhydrat: 3g. dont sucres /
… : 2,5g` — the total is **3 g** and `2,5 g` is the sugars figure. Photo 4 is therefore **not** an
identical-value fixture, and `2,5` joins its forbidden list. This is the second transcription error
found in this corpus; both were caught by re-reading the image rather than by any test.

**Prose labels (photos 3, 4, 6).** Three of the seven are not tables. The carbohydrate figure appears
inline inside a flowing multilingual sentence that also contains the sugars figure. The
geometry-first architecture reconstructs *rows*, and a sentence is one row containing both nutrients
— which `RowClassifier` correctly types `CARBOHYDRATE_CHILD`, yielding `NotFound`.

## Change 1 — inline prose reader (per-100 only)

A new pure stage for labels with no tabular structure. It is a **recognizer of one printed form**,
not a scoring model. Its contract, in full:

1. Activates **only** when table reconstruction returns `NotFound` — never after `Confident` or
   `Ambiguous` — and only when the prose-eligibility predicate below holds.
2. Requires an explicit total-carbohydrate term. No inference from position.
3. Binds the value by close token/phrase adjacency to that term.
4. Requires a real numeric value carrying a gram unit.
5. Requires an explicit per-100 basis established by the declaration rule below.
6. Hard-excludes any span containing sugar/child terminology.
7. Never infers serving or per-piece values from prose in this pass.
8. Conflicting eligible total-carbohydrate spans → `Ambiguous`.
9. Insufficient or competing basis → `NotFound`.
10. No score-based "closest number" fallback.

Rule 10 is structural: the stage has no notion of a best candidate. It either finds a span matching
the required shape or yields nothing. There is nothing to tune later, which is what keeps it from
becoming a second scoring path of the kind removed in the 2026-08-15 rewrite.

### Activation is a gate, not a preference (rule 1)

The trigger is tabular `NotFound` specifically. A tabular `Ambiguous` result must **not** fall
through to prose: ambiguity means the table stage found competing legitimate interpretations, and
resolving that competition with a different stage's answer is exactly the confident-wrong-answer
path this architecture refuses. `Ambiguous` is surfaced to the user unchanged.

The stage additionally requires an explicit **prose-eligibility predicate** — a positive structural
statement that this label *is* prose, not merely that the table stage failed. A table the parser
failed to read is not a prose label, and running a second reader over it would convert a safe refusal
into a guess.

**The predicate is NOT "one row contains a total term and a child term."** That formulation is
rejected: a *failed table* produces exactly that shape through row merging — which is precisely the
2026-08-16 chaining bug, where a carbohydrate row absorbed the sugars row beneath it. A predicate
satisfied by the very failure mode the geometry work exists to prevent would fire the prose reader on
broken tables. The nine-image baseline also measured it firing on only 1 of 9 fixtures, so it is both
unsound and too narrow.

The predicate requires **both** of:

1. **Positive sentence-like structure** — a nutrient term followed by its value, followed by a child
   term followed by *its* value, in reading order: the `nutrient → value → child → value` shape that
   a sentence produces and a table column does not.
2. **Absence of usable tabular column evidence** — no classified per-100 or per-serving column that
   could have supplied the reading. A label with a working column structure is a table, whatever else
   is true of its rows.

Final details are informed by the nine-image baseline, which measured what real ML Kit segmentation
actually produces. Pinned by tests in both directions: eligible for the prose fixtures, ineligible
for every tabular fixture **and** for a deliberately merged-row table fixture representing the
chaining failure.

### The exclusion rule changes scope, and only here

Today exclusion is **row**-scoped: a row naming a child nutrient cannot be the total. That is right
for a table row and wrong for a sentence, where both nutrients share one row.

Inside this stage only, exclusion becomes **span**-scoped: a value binds to its nearest preceding
nutrient term, and a child term claims every value up to the next nutrient term. So in photo 6's
actual printed text:

```
koolhydraten 46 g, waarvan suikers 1,0 g, vezels 4,7 g
```

`46` binds to `koolhydraten`; `1,0` is claimed by `waarvan suikers` and `4,7` by `vezels`. Both child
terms claim their own value and neither reaches back to the total.

This example is the real stokbrood relationship, not an illustration. It is worth noting that the
three values are `46`, `1,0` and `4,7` — a proximity rule reaching forward from `koolhydraten` for
"the nearest number" would still land on `46` here and look correct, which is precisely why the rule
is defined by binding rather than by distance. The row-scoped rule is untouched on the tabular path.

> General rule: *on a label with no tabular structure, a nutrient value binds to its nearest
> preceding nutrient term within the same line; a child term claims every value up to the next
> nutrient term.*

### Risk, and how it is contained

This is the only change in the pass that touches the child-exclusion rule protecting Sondey and
Kinder. Containment is structural and doubled: the stage runs only on tabular `NotFound` (both of
those return `Confident`), **and** only when the prose-eligibility predicate holds (neither is prose).
Either condition alone excludes them.

That "cannot happen" is pinned by an explicit test asserting the prose reader is **not** invoked for
Sondey or Kinder. An unasserted impossibility is how the previous child-exclusion regression reached
a real package.

### Basis binding is structural, not a distance (rule 5)

Photos 3 and 4 print the basis **once**, at the head of a long sentence, with every nutrient listed
after it. The binding rule is therefore about structure, not proximity:

> **One unique explicit per-100 basis governing the same prose declaration applies to that
> declaration's nutrient spans. Competing or absent bases yield `NotFound`.**

A *declaration* is the printed unit that opens with a basis and enumerates nutrients under it —
`Voedingswaarde per 100 g: energie …, koolhydraten 46 g, …`. The basis governs the nutrient spans
belonging to that declaration. Uniqueness is required: a declaration under which two different bases
appear yields `NotFound` rather than a choice between them, matching how the tabular path already
refuses a row carrying two bases.

**A declaration spans as many `LogicalRow`s as it is printed across.** The nine-image baseline
measured this directly and decisively: across all three prose fixtures, the basis phrase reconstructs
as its own standalone row **0 times out of 3** on the same row as the carbohydrate term and its
value. On photo 4 the basis `Pour/Per/Pro 100g:` opens the block and the carbohydrate figure sits
three printed lines below it. A same-row requirement would therefore never fire on any real prose
label — it would neither fix nor regress anything, which makes it not a safety rule but dead code.

A declaration consequently opens at a basis phrase and extends over subsequent rows until the next
basis phrase or the end of the document. This keeps the rule structural — the governing basis is
still established by the declaration a value belongs to, never by a distance threshold.

### Aggregation is document-wide, never first-match

`ProseNutritionReader.read` must gather **every** eligible total-carbohydrate interpretation across
the whole document and then decide once:

- **zero** unique results → `NotFound`
- **one** unique result → `Confident`
- **multiple differing** results → `Ambiguous`

Returning the first accepted row is forbidden. A first-match return is a positional preference
masquerading as a rule — it would silently pick one of two disagreeing declarations and report it
confidently, which is the exact failure class this architecture refuses. Aggregation is also what
makes the governing-basis rule meaningful: a value can only be checked against the declaration that
governs it once all declarations are known.

This is deliberately the same principle as the geometry-first rewrite — establish the structural
relationship, do not score proximity. A distance threshold would be a tunable number, and a tunable
number is what a future pass loosens until a sugars figure fits under it.

## Change 2 — tabular weight-based serving column

Photo 2's `ø/portie 50 g` is a per-serving column carrying its own serving weight. Tabular only; this
logic is **not** reused for prose labels.

1. Recognize `per portion 50 g` and localized equivalents as `PER_SERVING`.
2. Parse the explicit serving weight from the column header.
3. Cross-check `carbsPer100 × servingWeight / 100` against the printed serving carbs, with rounding
   tolerance.
4. Emit a weight-based serving **only** when header semantics and arithmetic agree.
5. On material conflict, keep the canonical per-100 result and **drop** the serving candidate.

> General rule: *a serving column stating its own weight is trustworthy only when the table's own
> arithmetic reproduces the printed per-serving figure.*

This is the same corroboration argument `ServingWeightAssociator` already makes for Kinder's
separately-printed `(12,5 g)`. The two differ only in **where the weight was acquired** — a separate
line there, inside the column header here — so both route through **one** corroboration function.
Writing a second one is how the tolerance drifts between them.

Photo 7 is a second instance of the same shape: header `schaaltje (150 g)`, serving carbohydrate
`7,5 g`, arithmetic `5,0 × 150 / 100 = 7,5`. Two independent real fixtures exercising this rule is
what makes it a general capability rather than one package's quirk — and they differ usefully, since
photo 2 states its serving weight in the header text while photo 7 parenthesises it.

Note: both real serving fixtures are arithmetically **exact** (`2,0 × 50 / 100 = 1,0` and
`5,0 × 150 / 100 = 7,5`), so neither exercises the rounding tolerance. Kinder's
`53,5 × 12,5 / 100 = 6,6875` vs printed `6,7` does. The tolerance is therefore pinned by a synthetic
test rather than implied by exact-match fixtures — an exact match cannot demonstrate that a tolerance
exists, only that it was not needed.

Photo 7 also carries a `%RI` figure of `3,0` on the carbohydrate row itself. `3,0` is numerically
plausible as a gram value, so this fixture is the corpus's strongest test that a reference-percentage
cell can never be handed back as carbohydrate grams.

## Change 3 — candidate provenance, at the granularity that proves the claim

`NutritionParseReport` exposes where an accepted candidate came from, so a test can distinguish "read
the total" from "read the sugars figure that prints the same number".

**Row-level provenance is insufficient for prose.** On photos 3 and 4 the total and the sugars figure
share one reconstructed row *and* print the same value, so a row-level assertion proves nothing at
all there — it would be satisfied by a sugars read. Provenance granularity must therefore match the
stage that produced the result:

- **Tabular results** carry the reconstructed row the value was read from. Sufficient, because the
  total and its child occupy different rows.
- **Prose results** carry the **span**: the bound nutrient term and the element indices of the value
  bound to it. Tests assert the selected span is bound to a `TOTAL_CARBOHYDRATE` term, not merely
  that the number matches.

> General rule: *a correct value read from the wrong label is a latent defect; provenance must be
> exposed at a granularity fine enough that a test can tell the two apart — which for a label
> printing its total and its child in one row with one value means span level, not row level.*

Debug-gated exactly as the existing diagnostics are; R8 must still strip the renderer from release.

## Change 4 — curvature (conditional)

Photo 4's text follows a circular lid. `RowSlopeEstimator` produces one global skew scalar, which
cannot describe an arc.

No fix is designed in advance. If the baseline shows curvature breaking row reconstruction, it is
measured and treated as its own class with the smallest general rule that covers it. Global
tolerances are **not** loosened — that is what produced the single-linkage chaining bug.

## Fixtures and repository policy

The seven originals are source evidence: never edited, recompressed, rotated or renamed. Dimensions
and EXIF orientation are recorded for each.

**Nine** sanitized fixtures are committed — the seven new plus cropped Sondey and Kinder — so the
real-image suite is mandatory in CI rather than skippable. Sanitization is **cropping only**, to the
nutrition panel plus the headers and serving text needed to interpret it. Explicitly not applied:
deskew, sharpening, contrast normalization, rotation correction, or any enhancement production does
not perform. The tilt, glare, curvature and compression are the variables under test.

`.gitignore` changes from ignoring all of `ocr_real/` to ignoring full-frame originals while tracking
the sanitized fixtures. `assumeTrue` becomes a hard failure: a missing fixture fails CI rather than
silently skipping, which is the gap that let a green suite coexist with a broken scanner.

## Testing

Two layers, both required. Neither replaces the other.

**Pure JVM.** For every defect a real image reveals, the smallest synthetic case representing the
underlying condition — a geometry fixture for a row-reconstruction failure, a token fixture for a
numeric-parsing failure. Fast, and they pin the rule rather than the photograph.

**Real-image instrumentation.** All **nine** fixtures through the real recognizer, `MlKitOcrMapper`
and `NutritionTableParser`. No mocked recognizer. Assertions cover the canonical value and basis,
plus explicit negative assertions for the dangerous nearby values on that specific label:

| Fixture | Must never be returned as carbohydrate grams |
|---|---|
| 1 juice | — (sugars equals the total; provenance carries the proof) |
| 2 grated cheese | `0,5` sugars, `50` serving weight |
| 3 jar | `500` pack weight, `20` saturated fat |
| 4 lid | `2,5` sugars, `125` pack weight, `19` saturated fat |
| 5 witte kaas | `200` pack weight |
| 6 stokbrood | `1,0` sugars, `4,7` fibre, `12` protein |
| 7 yoghurt | `3,0` %RI, `150` serving weight |
| Sondey | `47,6` sugars |
| Kinder | `3`, `7`, `53,3` |

Photos 1, 3, 4, 5 and 7 additionally assert provenance — at the granularity their stage requires:
**row** provenance for the tabular fixtures (1, 5, 7), and **span** provenance for the prose fixtures
(3, 4). On photos 1, 5 and 7 the sugars figure equals the total, so without provenance a sugars read
would pass the value assertion unnoticed. Photo 4's total (`3 g`) and sugars (`2,5 g`) differ, so its
span provenance proves something narrower but still necessary: which *stage* answered. A `3` reached
by the tabular path would mean the fix landed somewhere other than where the design claims.

The `20` and `19` entries for photos 3 and 4 are the saturated-fat figures the 2026-08-16 baseline
measured this pipeline **actually returning today** as confident carbohydrate values. They are
regression guards against a live defect, not hypothetical near-misses.

Photos 2 and 7 assert the weight-based serving candidate (`1,0 g` per 50 g and `7,5 g` per 150 g
respectively). Photos 3, 4 and 6 assert the **absence** of a serving candidate.

The prose reader's activation gate is pinned in both directions: it is not invoked for Sondey,
Kinder or any tabular fixture, and the prose-eligibility predicate is asserted true for 3/4/6 and
false for the tabular fixtures.

Photos 3, 4 and 6 assert the **absence** of a serving candidate — per-100-only is the deliberate
scope of the prose reader, and asserting the absence is what keeps it that way.

## Explicitly out of scope

- Barcode stability work: untouched.
- Serving inference from prose, in any form.
- Image preprocessing, unless the baseline demonstrates a *recognition-stage* failure — and then
  benchmarked across all nine fixtures, never improving one while breaking another.
- The camera path. Nine JPEGs passing proves the parser on real optics. It does not prove
  CameraX → ViewPort → shutter → ImageCapture → EXIF → ROI crop → OCR. `docs/manual-qa.md` §15f
  stays open until performed on hardware.

## Order of work

The baseline runs on all nine fixtures through the production pipeline **before** any production
code changes, capturing raw ML Kit text, logical rows, row and column classifications, candidates,
final reading and failure stage. Each failure is classified as recognition versus interpretation:
recognition failures are never patched with downstream parser heuristics, and interpretation failures
are fixed at the stage that lost the information.

Only then: TDD per defect — pure regression test, production fix, real-image test — followed by the
full suite, lint, debug build, minified release, and R8 verification that diagnostics remain stripped.
