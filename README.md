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

**Screenshots:** *(placeholders — capture from a physical device)*
`docs/screenshots/01-home.png` · `02-scanner.png` · `03-calculator.png` · `04-verify.png` ·
`05-manual-entry.png`

## Architecture

Deliberately flat. A small app does not need a clean-architecture framework.

```
domain/    Pure Kotlin, zero Android imports — the safety-critical layer
data/
  local/   Room: ProductEntity, ProductDao, RoomProductDataSource
  remote/  Open Food Facts: Retrofit API, DTOs, OpenFoodFactsDataSource
  settings/DataStore preferences
  ProductRepository   ← owns the §10 lookup priority
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
hand while they are reading it.

## Privacy

No account, no advertising, no analytics we added, no tracker. Permissions: `CAMERA` and `INTERNET`.
Products, portions, favourites and verified values stay on-device; Android backup is disabled.

The only request CarbScan's own code makes is a barcode lookup to Open Food Facts — the privacy
policy says so explicitly rather than claiming "no data leaves your device", which would be false.
ML Kit ships a Google telemetry transport we do not control; that is disclosed, not hidden. See
[docs/privacy-policy.md](docs/privacy-policy.md).

## Build

```powershell
$env:JAVA_HOME="<path to JDK 21>"
$env:ANDROID_HOME="C:\atools\sdk"

.\gradlew.bat :app:testDebugUnitTest        # 116 JVM unit tests
.\gradlew.bat :app:lintDebug                # Android lint
.\gradlew.bat :app:assembleDebug            # debug APK
.\gradlew.bat :app:connectedDebugAndroidTest # 26 instrumented tests (needs a device)
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
| Repository | §10 priority, 7 provenance regressions, refresh rules | 22 tests, passing |
| Open Food Facts | real HTTP via MockWebServer: malformed JSON, 429, 500, dropped connection | 17 tests, passing |
| OCR parsing | Dutch/English, 100 g / 100 ml, sugars sub-line, ambiguity, failure | 14 tests, passing |
| Room DAO | ordering, favourites float, TEXT decimal round-trip | 10 instrumented, passing |
| Calculator UI (§60) | result on typing, no Calculate button, quick adjust, ml lock, provenance badges, pack shortcuts, double-rounding guard, long names, zero-carb, large and decimal portions | 16 instrumented, passing |

**Verified on hardware:** barcode decoding and label OCR. **Verified against the live API:**
product lookup and images. **Not verified:** breadth of physical devices, and the release build on
hardware. See
[docs/known-limitations.md](docs/known-limitations.md).

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
| [third-party-notices.md](docs/third-party-notices.md) | Licences and ODbL attribution |
| [manual-qa.md](docs/manual-qa.md) | Manual QA checklist |
| [MASTER-PROMPT.md](docs/MASTER-PROMPT.md) | The original requirements brief (§ references) |

## Licence

Not yet chosen — the owner must select one before publication.
