# Countable portions — design spec

Status: approved by owner (spec supplied in full by owner; this document
records it against the current codebase for implementation). Architectural
scope: new domain component, Room v3 migration, OFF DTO expansion, UI change
across the calculator and Recents, plus a documentation/security pass.

## 1. Problem

Today the calculator only accepts grams/ml:

`SCAN → grams/ml → CARBS`

Many foods are eaten by count (slices of bread, biscuits, dumplings). The user
should be able to enter `2 slices` and get the same authoritative carb result
without doing gram math in their head:

`SCAN → slices/pieces/etc. → CARBS`

## 2. Non-negotiable architectural rule

There is exactly one carbohydrate formula, in `CarbCalculator`:

```
exact = carbsPer100.multiply(portion).movePointLeft(2)
```

Countable portions are a **conversion layer in front of** `CarbCalculator`,
never a second calculation path. A new pure-Kotlin object,
`PortionResolver`, turns `(count, amountPerUnit)` into the `portion` BigDecimal
that `CarbCalculator` already accepts:

```kotlin
object PortionResolver {
    fun resolve(count: BigDecimal, amountPerUnit: BigDecimal): BigDecimal {
        require(count >= BigDecimal.ZERO) { "count must not be negative" }
        require(amountPerUnit >= BigDecimal.ZERO) { "amountPerUnit must not be negative" }
        return count.multiply(amountPerUnit)
    }
}
```

No rounding inside `resolve` — the multiply result flows straight into
`CarbCalculator.calculate`, which is the only place `movePointLeft`/rounding
happens. Basis (g vs ml) is carried by the `PortionUnit`/`Product`, not by
`PortionResolver` — `resolve` is a pure BigDecimal multiply, agnostic to unit.

## 3. Domain model: `PortionUnit`

Do not overload `Product.servingAmount`. That field already exists on
`Product`/`ProductEntity` but is dead — never populated, never read anywhere
in the codebase. It predates this feature and is unrelated scaffolding; it is
left untouched (per project convention: don't remove pre-existing dead code
that isn't part of this change — flagged in the final report instead).

```kotlin
data class PortionUnit(
    val id: Long = 0,
    val productBarcode: String,
    val kind: PortionUnitKind,          // enum, see §4
    val customLabel: String?,           // required iff kind == CUSTOM
    val amountPerUnit: BigDecimal,      // the *verified/effective* value used in calculations
    val basis: NutritionBasis,          // GRAMS or MILLILITRES — must match the product's basis
    val dataSource: ProductDataOrigin,  // OPEN_FOOD_FACTS | MANUAL | OCR — reuses the existing enum
    val verificationStatus: VerificationStatus,   // UNVERIFIED | USER_VERIFIED — reuses the existing enum
    val verifiedAt: Instant?,
    val originalRemoteAmountPerUnit: BigDecimal?, // first remote value ever seen, immutable once set
    val latestRemoteAmountPerUnit: BigDecimal?,   // most recent remote value seen; not applied automatically
    val rawRemoteServingText: String?,            // e.g. "2 slices (70 g)", for diagnostics/re-parsing
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

This directly mirrors the existing `Product` provenance/verification split
(`ProductDataOrigin` + `VerificationStatus`, kept as two orthogonal fields per
the owner's standing correction — never collapsed into one enum) and the
existing `latestRemoteCarbs`/session-immutability pattern on `Product`.

A product has zero, one, or many `PortionUnit`s. Storage: new Room table
`portion_units`, not new columns on `products` (one-to-many, and the existing
`products` table is already wide).

## 4. `PortionUnitKind`

```kotlin
enum class PortionUnitKind {
    SLICE, PIECE, BISCUIT, COOKIE, BAR, ROLL, SCOOP, SACHET, SERVING, CUSTOM
}
```

Each non-`CUSTOM` kind has a localized singular/plural label pair in
`strings.xml` (e.g. `portion_kind_slice_one` / `portion_kind_slice_other`,
using Android plural resources). **UI display strings are English only for
this feature** (owner correction, 2026-08-14): no `values-nl` entries are
added for new countable-portion UI strings, and the existing
`values-nl/strings.xml` file is untouched. This is purely about *displayed
app language* — it is separate from **`ServingSizeParser`'s input
recognition**, which the owner confirmed must still handle Dutch remote text
(they live in the Netherlands and OFF products they scan will legitimately
carry Dutch `serving_size` strings like `"1 sneetje (35 g)"`); see §5. In
other words: the parser *reads* Dutch, the app *speaks* English — a Dutch
`serving_size` still maps to the same canonical `PortionUnitKind` enum, which
is what drives the (English-only) displayed label. `CUSTOM` uses
`PortionUnit.customLabel` verbatim — user text, never translated, never run
through the kind-label lookup.

## 5. `ServingSizeParser`

Pure Kotlin, cautious, false-negative-biased. Signature:

```kotlin
object ServingSizeParser {
    data class Parsed(val kind: PortionUnitKind, val amountPerUnit: BigDecimal, val basis: NutritionBasis)
    fun parse(rawServingSize: String?): Parsed?
}
```

Accepts only strings that state **both** a count and a base quantity
unambiguously, and normalizes to a **per-single-unit** amount:

- `"1 slice (36 g)"` → `SLICE, 36`
- `"2 slices (70 g)"` → `SLICE, 35` (divide by the stated count — never store
  the multi-count amount as if it were per-unit)
- `"1 slice, 35 g"`, `"1 sneetje (35 g)"`, `"1 biscuit (12.5 g)"`,
  `"1 cookie = 15 g"`, `"1 bar (40 g)"`, `"1 piece 22 g"` → analogous

Rejects (returns `null`) anything that only states a bare quantity with no
explicit count relationship:

- `"30 g"`, `"serving size 40 g"`, `"approx. 35 g"`, `"portion 25 g"`,
  `"1 slice"` (no weight), `"slice 35"` (no unit), malformed/garbage strings

`OFF serving_quantity` alone is never treated as "grams per unit" — see §7.
**English and Dutch unit-name recognition** (owner confirmed 2026-08-14: they
are in the Netherlands and OFF `serving_size` text for their products will
legitimately be Dutch). This is input-side recognition only — it maps
recognized words in either language to the same canonical
`PortionUnitKind` enum (§4), which the UI then displays via its English-only
string resources. Recognized Dutch unit words for the initial kind set:
`sneetje/sneetjes` (SLICE), `stuk/stuks` (PIECE), `koekje/koekjes` (BISCUIT/
COOKIE — OFF Dutch text doesn't reliably distinguish these two English kinds,
so both map from the same Dutch tokens; ambiguous only in which enum value is
chosen, never in the parsed amount), `reep/repen` (BAR), `bolletje/bolletjes`
or `broodje/broodjes` (ROLL), `schepje/schepjes` (SCOOP), `zakje/zakjes`
(SACHET), `portie/porties` (SERVING). Decimal comma (`12,5 g`) is accepted
via the same tokenizer as `PortionParser`, consistent regardless of which
language's unit word surrounds it.
Division to get the per-unit amount uses exact `BigDecimal` division; if the
division does not terminate cleanly the parser still returns a value scaled
to a fixed, generous precision (matches `PortionResolver`/`CarbCalculator`'s
existing "never throw on non-terminating quotient" discipline) — never
`Double`.

## 6. Room v3 migration

Non-destructive, `MIGRATION_2_3`, following the exact pattern of the existing
`MIGRATION_1_2` in `CarbScanDatabase.kt`:

```sql
CREATE TABLE portion_units (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    productBarcode TEXT NOT NULL,
    kind TEXT NOT NULL,
    customLabel TEXT,
    amountPerUnit TEXT NOT NULL,
    basis TEXT NOT NULL,
    dataSource TEXT NOT NULL,
    verificationStatus TEXT NOT NULL,
    verifiedAt INTEGER,
    originalRemoteAmountPerUnit TEXT,
    latestRemoteAmountPerUnit TEXT,
    rawRemoteServingText TEXT,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    FOREIGN KEY(productBarcode) REFERENCES products(barcode) ON DELETE CASCADE
);
CREATE INDEX index_portion_units_productBarcode ON portion_units(productBarcode);

ALTER TABLE products ADD COLUMN lastInputMode TEXT;        -- "GRAMS" | portion_unit id marker
ALTER TABLE products ADD COLUMN lastSelectedPortionUnitId INTEGER;
ALTER TABLE products ADD COLUMN lastCount TEXT;             -- BigDecimal as TEXT, same convention as carbs
```

`lastInputMode`/`lastSelectedPortionUnitId`/`lastCount` live on `products`
(single values per product, same cardinality as `lastPortion` today) rather
than a separate table — mirrors the existing `lastUsedAt`/`lastPortion`
fields exactly. All new decimal columns are `TEXT`, per the standing
BigDecimal-as-TEXT rule. `exportSchema` stays on; a new schema JSON is
committed. Migration test: seed a v2 in-memory DB with representative rows
(favorite, verified, with `latestRemoteCarbs`), run `MIGRATION_2_3`, assert
every pre-existing column/row survives unchanged and the new columns are
present with `NULL` defaults — modeled on the existing `ProductDaoTest`
conventions (real Room, no Robolectric, per owner's standing "no Robolectric"
rule).

## 7. Open Food Facts integration

`OpenFoodFactsApi`/`OpenFoodFactsDto`/`OpenFoodFactsDataSource` are extended
to request and parse `serving_size` (free text) in addition to the existing
`code, product_name, product_name_nl, brands, quantity, nutriments,
image_front_small_url` field list. The raw OFF response never reaches
domain/UI: `OpenFoodFactsDataSource.toResult()` remains the single seam where
DTO → domain mapping happens, and that is where `ServingSizeParser.parse(dto.servingSize)`
is called to (maybe) produce a `PortionUnit` candidate with
`dataSource = OPEN_FOOD_FACTS`, `verificationStatus = UNVERIFIED`.

`serving_quantity`/`serving_quantity_unit` are **not** used to derive a
countable unit — only the free-text `serving_size` field, run through
`ServingSizeParser`, which requires an explicit count-to-quantity relationship
in the text itself (§5). This sidesteps needing to trust `serving_quantity`'s
exact documented semantics for correctness, while still recording what OFF
provided in `rawRemoteServingText` for diagnostics.

`carbohydrates_serving` (if present) is never read — the single authoritative
formula stays `carbohydrates_100g × resolved portion`.

### 7a. OFF API research findings (confirmed 2026-08-14, primary sources)

- **`serving_size`**: free-text, e.g. `"1 portion (330 ml)"` — *"We expect a
  quantity + unit but the user is free to input any string."* This is exactly
  what `ServingSizeParser` (§5) consumes.
- **`serving_quantity`**: OFF's own *normalized numeric extraction* from
  `serving_size` — explicitly **not** "servings per package." Confirms the
  design decision in §7: it is not a reliable per-unit weight on its own
  (e.g. `serving_size: "1 portion (330 ml)"` → `serving_quantity: 330`, which
  is a single-serving quantity, not necessarily a countable per-item weight),
  so `ServingSizeParser` still parses `serving_size` text directly rather than
  trusting `serving_quantity`. Note for the DTO: the OpenAPI schema types this
  field as `string` but live responses return a JSON **number** — parse
  defensively (accept either).
- **`serving_quantity_unit`**: a real, documented field (`"g"` or `"ml"`).
  Not required for `ServingSizeParser` to function (the parser extracts its
  own unit from the free text), but worth adding to the DTO for potential
  future cross-checking; not used to gate acceptance in this pass (YAGNI —
  the parser's own unit token is authoritative).
- **API version**: **v3 is current/recommended**
  (`https://world.openfoodfacts.org/api/v3/product/{barcode}.json`); v2 is
  deprecated-but-supported. Field names used by this app are unchanged
  between v2/v3. Envelope differs: v2 has `status: 1` (int) +
  `status_verbose`; v3 has `status: "success"` (string) + a `result.id`
  object + `errors`/`warnings` arrays. **Decision: migrate the DTO to v3** —
  field-name compatibility makes this low-risk, and it avoids building new
  serving-size parsing against a channel already marked deprecated. The
  envelope change (`status` int→string, new `result` wrapper) is the one
  breaking shape change and needs its own DTO fields + a fixture update in
  `OpenFoodFactsDataSourceTest`.
- **Rate limit**: confirmed 15 req/min/IP for product reads (matches
  CLAUDE.md already) — no change to the existing cache-first discipline.
- **User-Agent**: documented format is `AppName/Version (ContactEmail)`. The
  app's contact email is still the placeholder `REPLACE_ME@example.com`
  (tracked as open finding #5 in CLAUDE.md, owner-owed, out of scope for this
  feature) — flagged again here because it now has a concrete compliance
  implication, not just a docs placeholder.
- **Image hosts**: `images.openfoodfacts.org` confirmed live (this is what
  `image_front_small_url` resolves to today). `static.openfoodfacts.org`
  appears in one schema example but was not independently live-verified.
  **Allowlist decision**: accept HTTPS URLs on `images.openfoodfacts.org` OR
  `static.openfoodfacts.org` — the second is included defensively since it's
  doc-sourced even though unconfirmed live, and rejecting it costs nothing
  (falls back to the monogram tile) while over-trusting an unlisted host
  would be the actual risk.
- **Licensing**: structured data is ODbL (confirmed, matches CLAUDE.md);
  individual field values additionally carry a Database Contents License
  (DbCL); **images are licensed separately, under CC BY-SA** — not ODbL. Both
  require attribution to Open Food Facts with a link to openfoodfacts.org.
  OFF's own terms note images may still carry third-party rights (e.g.
  photographed packaging) that OFF doesn't independently clear — this nuance
  goes into the third-party notices doc as a stated caveat, not a legal
  conclusion we invent.

## 8. User-defined portion units

`ProductRepository.saveUserPortionUnit(barcode, kind, customLabel?, amountPerUnit, basis)`
creates a `PortionUnit` with `dataSource = MANUAL`, `verificationStatus = USER_VERIFIED`,
`verifiedAt = now`. User-defined units are never touched by a remote refresh
(mirrors `isRemoteRefreshable` gating already on `Product`: a `PortionUnit`
is only remote-refreshable when `dataSource == OPEN_FOOD_FACTS && verificationStatus == UNVERIFIED`).

## 9. Verification & remote-refresh immutability

Same trust philosophy as carbs today, extended per-unit:

- A background refresh may update `PortionUnit.latestRemoteAmountPerUnit` and
  `rawRemoteServingText`, but never `amountPerUnit` (the effective/verified
  value) — mirrors `RefreshOutcome.RemoteDiffers`/`newerRemoteCarbs` exactly,
  extended to `newerRemoteAmountPerUnit` in `ProductUiState`.
- User confirmation (`✓ Verified by you`) sets `verificationStatus = USER_VERIFIED`,
  `verifiedAt = now`, and does not change `amountPerUnit` unless the user
  edits the value as part of verifying.
- Session immutability (§10) means even the *currently open* calculator never
  reacts to a `latestRemoteAmountPerUnit` change mid-session — same rule as
  carbs (owner correction #5), now covering `amountPerUnit` and the resolved
  gram amount too.

## 10. Session immutability (extended)

Once a calculator session opens, frozen for the life of that session:
`carbsPer100`, `basis`, selected `PortionUnit` (by id) and its `amountPerUnit`
snapshot. A background refresh during the session can only populate the
"Online portion changed" notice; applying it requires an explicit user tap
(or happens naturally on the next session open). Directly extends the
existing `ProductViewModel`/`ProductUiState`/`RemoteChangedNotice` mechanism.

## 11. Calculator UX

If a product has no `PortionUnit`s: unchanged single grams/ml field.

If it has one or more: a compact mode row above the amount field —

```
How much are you eating?

[ Grams ] [ Slices ]

[      2      ] slices

2 slices × 36 g = 72 g
```

— then the existing result panel, decimal-dominant, unchanged in hierarchy.
Count input accepts decimals (`BigDecimal`, rejects negative/invalid), reuses
`PortionParser`-style tokenizing. Quick controls: `−1`/`+1` steppers,
consistent with the existing `adjustPortion(delta)` ±5/±10 pattern in
`ProductViewModel`. No extra screen for mode selection — inline chips, same
screen, no layout jump (reserve the row's height whether or not multiple
units exist? No — only render the row when `portionUnits.isNotEmpty()`;
single-unit products keep today's exact layout, so there's nothing to
reserve space for).

`+ Add portion unit` is a subtle text action below the amount field, opening
a small inline form (portion type dropdown defaulting sensibly, or "Custom"
+ name field; amount-per-unit field), not a new screen/route.

## 12. Remembered input mode

Extends `ProductRepository.recordUse`: also persist `lastInputMode`
(`GRAMS` or a portion-unit id), `lastSelectedPortionUnitId`, `lastCount`.
`ProductViewModel.load(barcode)` restores these on open — defaulting to the
remembered mode, not always grams. Switching back to grams is one tap on the
`[ Grams ]` chip, immediate, no confirmation.

## 13. Recents

`RecentCard` (in `HomeScreen.kt`) recomputes its summary live from the
product's *last recorded* input, same as today, but now displays it in
whichever unit was last used:

- countable: `2 slices → 30.2 g`
- base-unit: `72 g → 30.2 g`

Still flows through `CarbCalculator` for the result — no cached/derived
display path, consistent with the existing `RecentCard` design.

## 14. Networking hardening

**Image URL validation**: new pure/testable `ProductImageUrlValidator`
(domain or a small `data`-layer utility, no Android imports needed) requiring
HTTPS and an approved OFF image host allowlist (hosts confirmed in §7a).
`ProductThumbnail`/wherever `product.imageUrl` is read for Coil is changed to
pass the URL through the validator first; a rejected URL is treated the same
as a blank one (falls back to the monogram tile).

**Shared OkHttpClient**: the survey confirmed Coil's `ImageLoader` and
Retrofit's `OpenFoodFactsApi` currently build two *separately-constructed*
`OkHttpClient` instances with identical config, not one shared instance.
`AppContainer` is changed to build a single `OkHttpClient` once (already
`lazy`) and pass that same instance to both `NetworkModule.openFoodFactsApi()`
and the Coil `ImageLoader.Builder`'s `OkHttpNetworkFetcherFactory`. The
User-Agent interceptor stays on that shared client — OFF's User-Agent
requirement is about identifying the app to OFF's own servers, and OFF's
image CDN is also an OFF-operated host, so sending the same header there is
appropriate (not "technically inappropriate" cross-origin leakage per the
brief's caution in §15 — confirmed once §7a pins the image host as
OFF-operated).

## 15. Testing plan

See owner's brief §20–22 verbatim for the required test matrix
(`PortionResolver`, `ServingSizeParser` positive/negative/localized cases,
calculation integration, repository persistence/verification/immutability,
UI mode-switching/session-immutability/reopen). Fixtures only for automated
OFF-shaped tests — no live product hardcoded into CI, per the brief.

## 16. Documentation & compliance follow-through

Owner's brief §16–19, §25 apply as written: whole-gram-dominant language
removed wherever still present, ODbL/image-licensing facts corrected instead
of asserted, privacy documentation corrected to disclose image network
requests, and a new `docs/security-review.md` written. These are documentation
and review deliverables, not architecture, and don't gate the schema/domain
design above — tracked separately in the implementation plan.

## 17. Out of scope (explicit)

No insulin/glucose/I:C/pump/CGM/calorie/meal-logging/macro/account/cloud-sync
/analytics/ads. Countable portions are a faster portion-entry method only.
