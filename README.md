# CarbScan

Scan a food barcode, enter your portion, read the carbohydrate grams. That is the whole app.

**CarbScan does not calculate insulin.** It is not a diet tracker, not a food diary, and it does not
communicate with pumps or CGMs. It produces one number, clearly, so you can use it wherever you need
it next.

> **Working name.** "CarbScan" is a working name; the public name is undecided. Branding is
> centralised in [`branding.gradle.kts`](branding.gradle.kts) — app name, application id, namespace
> and the Open Food Facts User-Agent all derive from that one file. Project documentation under
> `docs/` still refers to the original name *CarbQuick*.

---

## The workflow

```
SCAN → PORTION → CARBS
RECENT → PORTION → CARBS
```

Everything in the app exists to make one of those two paths faster, safer or clearer. A feature that
does not is left out.

**Countable portions** (2026-08-14) let the portion step be a count instead of a weight, when a
per-item weight is available and trustworthy:

```
SCAN → 2 slices → CARBS
```

`2 slices × 36 g = 72 g` is resolved by `PortionResolver`, a pure conversion layer — the app still
has exactly one carbohydrate formula, `carbsPer100 × portion / 100`; a countable unit only ever
supplies the `portion` value. Per-item weights come from Open Food Facts' `serving_size` text
(parsed cautiously — a false negative is fine, a false positive mapping is not) or from the user
directly. See [docs/superpowers/specs/2026-08-14-countable-portions-design.md](docs/superpowers/specs/2026-08-14-countable-portions-design.md).

**Added 2026-08-14**, all serving the same two paths rather than extending the app's purpose:

- **Temporary meal** — add several calculated portions, read one total. One unnamed, undated list
  that stays until you clear it, so a half-built plate survives switching apps. It is a scratchpad
  for one plate of food, not a diary: there is no meal id anywhere in the code, so meal *history*
  is not merely absent but unbuildable without adding the concept first.
- **Label verification** — scan a package to check a value already stored. Differences are shown
  as both numbers side by side and nothing is applied without a tap. A basis mismatch (per 100 ml
  against per 100 g) refuses to compare rather than converting.
- **Usual portions** — a portion you repeat for a product becomes a one-tap shortcut. Per product
  only; no dates or usage counts are shown, and no cross-product pattern can be assembled.
- **Search by name** — when a barcode is not in the database. A fallback from a failure, never the
  way in, and not offered when the lookup failed because the network was down. Nothing is ever
  auto-selected, even when a single result comes back.

**Screenshots:** *(placeholders — capture from a physical device)*
`docs/screenshots/01-home.png` · `02-scanner.png` · `03-calculator.png` · `04-verify.png` ·
`05-manual-entry.png`

## Architecture

Deliberately flat. A small app does not need a clean-architecture framework.

```
domain/    Pure Kotlin, zero Android imports — the safety-critical layer
           PortionResolver, ServingSizeParser, PortionUnit, ProductImageUrlValidator
data/
  local/   Room (v3): ProductEntity/Dao, PortionUnitEntity/Dao, RoomProductDataSource,
           RoomPortionUnitDataSource
  remote/  Open Food Facts v3: Retrofit API, DTOs, OpenFoodFactsDataSource
  settings/DataStore preferences
  ProductRepository   ← owns the §10 lookup priority and portion-unit persistence/verification
ocr/       NutritionLabelParser (pure) + LabelAnalyzer (ML Kit)
ui/        Compose screens + ViewModels, immutable state via StateFlow
```

**`domain/` has no Android dependencies.** That is the most important boundary in the project: the
calculation layer is exhaustively unit-testable on the JVM with no emulator, and it stays that way.

### The calculation

```
carbohydrates = carbsPer100 × portion / 100
```

Default display is **decimal-dominant** (`31.3 g`, with `≈ 31 g whole grams` beneath).

Evaluated in `BigDecimal`, and divided using `movePointLeft(2)` — an exact scale shift that cannot
round, cannot throw on a non-terminating quotient, and cannot lose a digit.

The exact value is **never rounded internally**. The displayed decimal and the whole gram are each
derived from it independently, so nothing is rounded twice. `48.2 × 65 / 100 = 31.33` → displayed
**31.3 g** with *≈ 31 g whole grams* beneath.

`ResultFormatter` sets `RoundingMode.HALF_UP` explicitly, because `DecimalFormat` defaults to
HALF_EVEN — without it the app displayed a different decimal from the one it calculated.

**Grams and millilitres are never interconverted.** `NutritionBasis` is a label, not a factor. A
per-100-ml product's portion field is locked to ml, because converting would require a density the
app does not have. A test pins this: identical numbers must produce identical results on both bases.

### Lookup priority

1. **User-verified local** — used immediately, never silently overwritten
2. **Cached remote local** — shown immediately; refresh never blocks a calculation
3. **Open Food Facts** — network
4. **Fallback** — scan the label, or enter it by hand. The user never hits a dead end.

Open Food Facts permits 15 reads/min/IP. Cache-first is required for performance, offline
capability, resilience, user experience and responsible API usage — a calculation is not invalid
merely because the value arrived over the network.

### Provenance vs. verification

Two independent fields, deliberately not collapsed into one:

- `dataSource` — `OPEN_FOOD_FACTS` | `MANUAL` | `OCR`. Permanent.
- `verificationStatus` — `UNVERIFIED` | `USER_VERIFIED`. Changes freely.

A product downloaded from Open Food Facts and later checked against the package is *both* things.
`isRemoteRefreshable` requires **both** not-user-authored **and** unverified, because the two
exclude different cases: user-typed data is protected even when unverified, and verified data is
protected even though its provenance is OFF.

### Calculation-session immutability

Once the calculator is open, a background refresh **never** changes the value being calculated
with. It records the newer figure and shows an *Online value changed* notice the user can accept.
Otherwise the screen could open on 48.2, the user types a portion, and the answer moves under their
hand while they are reading it. The same rule covers a selected countable portion unit: a refresh
can update `PortionUnit.latestRemoteAmountPerUnit` for a notice, never the `amountPerUnit` the open
session is calculating with.

## Privacy

No account, no advertising, no analytics we added, no tracker. Permissions: `CAMERA` and `INTERNET`.
Products, portions, countable portion units, favourites and verified values stay on-device; Android
backup is disabled.

CarbScan's own code makes two kinds of request: a barcode lookup to Open Food Facts, and — when a
product has one — a request for its photo, restricted to Open Food Facts' own image hosts
(`ProductImageUrlValidator`). The privacy policy says so explicitly rather than claiming "no data
leaves your device", which would be false. ML Kit ships a Google telemetry transport we do not
control; that is disclosed, not hidden. See [docs/privacy-policy.md](docs/privacy-policy.md).

Retrofit and Coil (images) share **one** `OkHttpClient` instance (`AppContainer.okHttpClient`), not
two separately-constructed clients with matching config — both genuinely inherit the same
connection pool, timeouts and identifying User-Agent.

## Build

```powershell
$env:JAVA_HOME="<path to JDK 21>"
$env:ANDROID_HOME="C:\atools\sdk"

.\gradlew.bat :app:testDebugUnitTest        # 225 JVM unit tests
.\gradlew.bat :app:lintDebug                # Android lint
.\gradlew.bat :app:assembleDebug            # debug APK
.\gradlew.bat :app:connectedDebugAndroidTest # instrumented tests (needs a device)
```

Requires JDK 21, Android SDK platform **37** and build-tools 37. `compileSdk` is 37 because AndroidX
requires it; `targetSdk` stays **36**, the Play requirement. The two are independent.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release build and signing

The release build type enables R8, resource shrinking, and no debug flags. It attaches a signing
config **only** when real signing material is present, and never falls back to the debug key.

Signing material is read from `keystore.properties` (gitignored) or environment variables
(`CARBSCAN_STORE_FILE`, `CARBSCAN_STORE_PASSWORD`, `CARBSCAN_KEY_ALIAS`, `CARBSCAN_KEY_PASSWORD`).

**No keystore, password or credential is committed, and none may be.** To create an upload key:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 4096 \
        -validity 10000 -alias upload
```

Store it outside the repository. Enrol in Play App Signing so Google holds the app signing key and
your upload key can be rotated if lost.

> The release AAB has **not** been built: no signing material exists in this environment.

## Testing

| Layer | Coverage | Status |
|---|---|---|
| Calculation | anchor cases, zero, large, double-rounding guard, half-up, ml-vs-g, negatives | 11 tests, passing |
| Portion parsing | `.` / `,` separators, mid-typing states, malformed, scientific notation | 14 tests, passing |
| Value validation | null, NaN, infinity, negative, impossible magnitudes | 10 tests, passing |
| Package quantity | g/ml/l/cl/kg, comma decimals, multipack refusal | 11 tests, passing |
| Barcode | EAN-13/8, UPC-A check digits, normalisation | 10 tests, passing |
| Repository | §10 priority, 7 provenance regressions, refresh rules, portion-unit persistence/verification/immutability | 40 tests, passing |
| Open Food Facts | real HTTP via MockWebServer: malformed JSON, 429, 500, dropped connection, v3 endpoint, serving_size candidates | 21 tests, passing |
| OCR parsing | Dutch/English, 100 g / 100 ml, sugars sub-line, ambiguity, failure | 14 tests, passing |
| Portion resolution | count × amount-per-unit, zero, decimal, large, negative rejection | 7 tests, passing |
| Serving size parsing | English + Dutch recognition, multi-count normalization, ambiguity rejection | 20 tests, passing |
| Image URL validation | HTTPS + host allowlist, rejects unapproved/malformed URLs | 6 tests, passing |
| Label comparison | match, mismatch, basis mismatch refuses to convert | 6 tests, passing |
| Product search | result mapping, missing carbs, 503 is not "no matches", offline | 9 tests, passing |
| Room DAO | ordering, favourites float, TEXT decimal round-trip | 10 instrumented |
| Room migrations | non-destructive, new columns/tables, cascade delete | instrumented |
| Calculator UI (§60) | result on typing, no Calculate button, quick adjust, ml lock, provenance badges, pack shortcuts, double-rounding guard, long names, zero-carb, large and decimal portions | 16 instrumented |
| Countable-portion UI (§22) | mode switching, derived-amount equation, user-defined units, session immutability | 10 instrumented |
| Meal UI | add, add & scan next, remove, clear, running total, no dates anywhere | 13 instrumented |
| Label verification UI | both values shown, nothing auto-applied, basis mismatch offers no apply path | 8 instrumented |
| Usual portions UI | appears only on repetition, per-product, no history shown | 7 instrumented |
| Search UI | never auto-selects, missing value stated in words, failure ≠ no matches | 9 instrumented |

**Totals: 225 JVM unit tests and 89 instrumented tests, all passing; lint clean** (2026-08-14).

**Dependency scan:** `bash tools/dependency-scan.sh` — 226 shipped artifacts checked against
OSV.dev, 0 known vulnerabilities (2026-08-14). Point-in-time; re-run before release.

**Verified on hardware:** barcode decoding and label OCR — nothing else. **Verified against the
live API:** product lookup, images, and search by name. **Not verified:** everything added in the
2026-08-14 pass on real hardware, breadth of physical devices, and the release build on hardware.
See [docs/known-limitations.md](docs/known-limitations.md).

## Regulatory status

**Unresolved, and it blocks publication.** CarbScan performs carbohydrate arithmetic and calculates
no medication — but qualification under EU MDR turns on intended purpose, not only on what the code
computes, and a disclaimer must not be used to avoid the assessment.

The controls are deliberately conservative in the meantime: OCR is never auto-accepted, verified
data is never silently overwritten, and no value is shown when confidence is insufficient. The docs
deliberately make no claim either way about the app's regulatory classification.

**Read [docs/regulatory-release-checklist.md](docs/regulatory-release-checklist.md) before doing
anything with this app publicly.**

## Documentation

| Document | Purpose |
|---|---|
| [regulatory-release-checklist.md](docs/regulatory-release-checklist.md) | The §44 gate. Blocks publication |
| [known-limitations.md](docs/known-limitations.md) | What this app cannot do, unsoftened |
| [privacy-policy.md](docs/privacy-policy.md) / [.html](docs/privacy-policy.html) | Privacy policy |
| [google-play-data-safety.md](docs/google-play-data-safety.md) | Data Safety answers, from real code |
| [play-health-declaration.md](docs/play-health-declaration.md) | Health policy declaration prep |
| [play-store-listing.md](docs/play-store-listing.md) | Store copy |
| [store-assets.md](docs/store-assets.md) | Required asset specs |
| [third-party-notices.md](docs/third-party-notices.md) | Licences and ODbL/CC BY-SA attribution |
| [manual-qa.md](docs/manual-qa.md) | Manual QA checklist |
| [security-review.md](docs/security-review.md) | Permissions, network, backup, dependencies |
| [ux-critique-countable-portions.md](docs/ux-critique-countable-portions.md) | §69-style critique of the countable-portions feature |
| [superpowers/specs/2026-08-14-countable-portions-design.md](docs/superpowers/specs/2026-08-14-countable-portions-design.md) | Countable-portions design spec |
| [MASTER-PROMPT.md](docs/MASTER-PROMPT.md) | The original requirements brief (§ references) |

## Licence

Not yet chosen — the owner must select one before publication.
