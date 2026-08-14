# Design — product development pass (2026-08-14)

Extends the existing CarbScan implementation. **Not a rewrite.** Everything in the current baseline
listed in `CLAUDE.md` is preserved; this document records only what changes and why.

The fundamental architecture is unchanged:

```
count → resolved g/ml → CarbCalculator
```

There remains exactly one carbohydrate formula, `carbsPer100 × resolvedAmount / 100`, evaluated in
`BigDecimal` in `CarbCalculator` and nowhere else.

---

## 1. Scope

| Layer | Change |
|---|---|
| P0 | count-field replace, IME layout, inline portion-unit correction, flaky test isolation, stale docs |
| P1 | product hero image, 400px OFF image selection, design-system tightening, responsive calculator |
| P2 | Temporary Meal Total, Add & scan next, integrated OCR verification, usual portions |
| P3 | ¼ pack fraction, OFF product search, Android launcher shortcuts |
| P4 | CC BY-SA attribution, dependency review, documentation sync, full QA |

Out of scope, permanently: insulin, I:C ratios, correction factors, glucose, CGM/pump integration,
calorie or macro dashboards, daily totals, dated meal history, weight, goals, accounts, cloud sync,
ads, first-party analytics, AI meal-photo estimation.

## 2. Verified external facts

Checked against the live API on 2026-08-14, not recalled from training data.

| Fact | Value | How checked |
|---|---|---|
| `image_front_url` | **400 px** front image (`front_en.879.400.jpg`) | live GET, barcode 3017620422003 |
| `image_front_small_url` | 200 px | same response |
| `image_front_thumb_url` | 100 px | same response |
| Image host | `images.openfoodfacts.org` — already on the allowlist | same response |
| Free-text search | `GET /cgi/search.pl?search_terms=…&json=1` — returns code, name, brands, quantity, 400px image and nutriments in one call | live GET, `search_terms=nutella`, HTTP 200, 1024 hits |
| `api/v2/search` | Does **not** support `search_terms` — returns an error page. It is a facet/filter endpoint, not free-text search | live GET returned HTML error, not JSON |

400 px is the display-sized variant the brief asks for: right for a 120–170 dp hero (≈360–510 px at
3×) without requesting an enormous original. No allowlist change is needed or permitted.

## 3. Data layer — Room v4

Two new tables. `MIGRATION_3_4` is **additive only**: two `CREATE TABLE IF NOT EXISTS` statements
and no `ALTER` against an existing table, so every v1/v2/v3 record — verified products, favourites,
recents, portion units, remembered input mode, remote-change metadata — is untouched by
construction. `fallbackToDestructiveMigration()` stays absent.

### 3.1 `current_meal_items`

```
id                 INTEGER PK AUTOINCREMENT
productBarcode     TEXT NULL     -- quick calculations have no barcode
displayName        TEXT NOT NULL
portionDescription TEXT NOT NULL -- "2 slices", "½ pack", "200 ml"
resolvedAmount     TEXT NOT NULL -- "72"
basis              TEXT NOT NULL -- G | ML
carbsPer100        TEXT NOT NULL -- "48.2"
exactCarbs         TEXT NOT NULL -- "34.704", never rounded
addedAt            INTEGER NOT NULL -- ordering only, never displayed
```

**Snapshot, not reference.** There is deliberately no foreign key to `products`. §9 requires a meal
item to survive the product being reformulated, re-verified, or deleted; a cascading FK would delete
the item, and a restricting FK would block the product delete. Copying the facts needed to re-display
and re-total is the only shape that satisfies "an existing meal item must NOT silently change".

`exactCarbs` holds the unrounded exact value so the total is `sum(exactCarbs)` — never a sum of
rounded whole grams or of one-decimal display strings.

### 3.2 `portion_usage`

```
id             INTEGER PK AUTOINCREMENT
productBarcode TEXT NOT NULL
inputMode      TEXT NOT NULL     -- GRAMS | PORTION_UNIT
portionUnitId  INTEGER NULL
amount         TEXT NOT NULL     -- count for countable, base amount for grams/ml
usageCount     INTEGER NOT NULL
lastUsedAt     INTEGER NOT NULL
UNIQUE(productBarcode, inputMode, portionUnitId, amount)
```

Aggregate rows only. There is no per-use event row, so **no timeline exists to reconstruct** (§22).
Variants are pruned to the top few per product. No date is ever shown to the user.

## 4. Domain layer

Three new pure objects. `domain/` keeps zero Android imports and stays JVM-testable.

**`MealTotal`** — `sum(item.exactCarbs)` in `BigDecimal`, formatted only after summation. This is
not a second carbohydrate formula: each `exactCarbs` was already produced by `CarbCalculator`, and
adding existing results is not a new calculation path.

**`UsualPortionSelector`** — pure ranking over `portion_usage`: `usageCount` descending, `lastUsedAt`
breaking ties, maximum 3 suggestions, minimum 2 uses to qualify so a single isolated use never
creates one (§13). Gram, millilitre and countable variants stay distinct — a "2" that meant two
slices must never be offered as two grams.

**`ProductImageSelector`** — `image_front_url` → `image_front_small_url` → none, each candidate
passed through the existing `ProductImageUrlValidator`. Pure, so §29's selection tests need no
emulator.

## 5. Calculator screen

Restructured as three zones rather than a longer vertical stack.

```
zone 1  identity   name · hero image ~150dp · per-100 · verification
zone 2  portion    mode chips · usual · count field · equation · pack · verify label
zone 3  result     carbohydrates · meal actions      [pinned]
```

**The keyboard fix is structural, not cosmetic.** The conversion equation moves out of the
scrollable region and pins directly above the result panel, so it is visible whenever the result is.
Zone 2's scroll can then only ever hide genuinely secondary controls. This closes the known
limitation that the equation — the user's sanity check — could scroll away at the exact moment they
were typing.

The hero image compacts from ~150 dp to ~90 dp when the IME opens, via one short
`animateDpAsState` transition. `ContentScale.Fit` on a neutral surface preserves the packaging
silhouette; brand and product-name areas are never cropped to fill the box. Loading is asynchronous
and never blocks calculation — the monogram occupies the same box from the first frame.

Recents stay compact. The large image is a calculator/identification feature, not a list style.

### 5.1 Count-field replace (§3.1)

The field moves to `TextFieldValue` and selects its whole contents on first focus, so typing `2`
over a pre-filled `1` yields `2`, not `12`. Subsequent cursor editing behaves normally. Pinned by a
regression UI test.

### 5.2 Inline portion-unit correction (§3.3)

Tapping *Online portion* expands an inline row — `1 slice = [36] g` → **Save as verified** — calling
the already-existing `ProductRepository.verifyPortionUnit(unitId, confirmedAmountPerUnit)`. No
competing custom unit is created. Preserved: remote provenance, `originalRemoteAmountPerUnit`, the
corrected effective amount, verification status and `verifiedAt`.

## 6. Temporary Meal Total

Calculator memory, not meal logging.

Allowed: add to meal, add & scan next, edit item portion, remove item, clear meal, read total.
Absent: meal names, dates as user-facing history, daily totals, analytics, calories, macros, charts.

Golden multi-product path, with no forced return to Home:

```
SCAN → PORTION → ADD & SCAN NEXT → PORTION → ADD → TOTAL
```

When a meal has items, a compact bar — `Meal · 2 items · 38.0 g` — appears on Home and the
calculator; tapping it opens the temporary total. Countable and package portions keep their
human-readable description (`2 slices`, `½ pack`), not merely the resolved grams.

## 7. Integrated label verification

*Verify label* on the calculator opens the camera directly into nutrition-label OCR. The detected
value is shown beside the current one:

```
ONLINE   48.2 g / 100 g
PACKAGE  48.2 g / 100 g
         ✓ Values match      → [ Confirm ]
```

**Even an exact match requires the explicit Confirm tap.** OCR is never self-accepting (§12), and
the open calculator's value never changes before confirmation (session immutability). On a mismatch
the two figures are shown plainly with *Use package value* / *Edit detected value* / *Cancel* — the
app never chooses between conflicting values on the user's behalf.

The parser is unchanged and stays conservative: total carbohydrate only (`Koolhydraten`,
`Carbohydrate`, `Carbohydrates`), never sugars, `waarvan suikers`, fibre, protein or energy. Ambiguity
asks rather than guesses.

## 8. Package fractions

Extended from `½ · Full` to `¼ · ½ · Full`. `¾` is not added — §14 requires demonstrated value, and a
fourth button truncates at large font scale. The existing reliability gate is unchanged: shortcuts
appear only when the package quantity parsed confidently, the basis is compatible, and the
interpretation is unambiguous. Multipacks are still never inferred. All shortcuts resolve through the
existing base-unit pathway.

## 9. Product search

A fallback, never the primary workflow. Failure hierarchy:

```
Barcode lookup unavailable / not found
    → Search product      → Scan nutrition label      → Enter manually
```

Uses `GET /cgi/search.pl?search_terms=…&json=1` (verified in §2). Text input is debounced. Result
cards carry enough to verify a choice before relying on it: a large-enough image, product name,
brand, package quantity and carbohydrate per 100 where available. **The user always chooses
explicitly — no fuzzy match is ever auto-selected.** A selected result is cached normally afterwards,
and becomes visually obvious on the calculator hero screen before its value is relied on.

## 10. Android shortcuts

Two static launcher shortcuts: **Scan food** → scanner directly, **Quick calculator** → the manual
quick-calculation flow. No home-screen widget in this pass. The goal is fewer taps, not more
surfaces.

## 11. Safety boundaries

Nothing here calculates an insulin dose, correction insulin, insulin-on-board, glucose response or
any treatment suggestion. The Temporary Meal adds carbohydrate to carbohydrate. Usual portions only
select quantities. Search only finds food records.

No wording anywhere may claim safer dosing, fewer hypos, better glucose control or better HbA1c.

**Regulatory qualification remains unresolved and continues to block publication.** It is not to be
"solved" through wording (§44, §28).

## 12. Testing

JVM, pure and deterministic: meal single/multiple items, exact decimal summation, the
no-sum-of-rounded-values regression, remove/edit/clear, countable and package-fraction items; usual
portions one-use-does-not-trigger, repeat-use qualifies, countable distinct from grams, g distinct
from ml, frequency order, recency tie-break, max 3, pruning; package fractions ¼×400=100, ½×400=200,
Full=400, decimal packages, ml stays ml, ambiguous/multipack rejected; verification match, mismatch,
no automatic acceptance, session value unchanged until confirmation; image selection prefers the
valid high-quality URL, falls back to small, rejects an unsafe URL, and falls back to monogram when
none exists.

Instrumented: hero image substantially larger than a recent thumbnail and non-blocking, layout intact
with no image, usable at large font; count field `1` → typing `2` gives `2`; equation and result both
reachable with the IME open; online 36 g corrected to 38 g is used as 38 g with provenance retained;
meal add / add-and-scan-next / edit / delete / clear / restoration; OCR match confirm and mismatch
requiring explicit choice; usual portion appears after repeat use and calculates on explicit tap;
search requires explicit selection.

## 13. Known constraint

Dependency-vulnerability scanning (§26) may not be runnable in this environment: there is no admin
right and `winget install` hangs on an unanswerable UAC prompt (a documented trap in `CLAUDE.md`).
A Gradle-resolved scanner will be attempted. If it cannot run reliably, this stays an explicit
release action — a dependency tree is not a vulnerability scan and will not be presented as one.
