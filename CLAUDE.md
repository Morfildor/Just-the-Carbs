# CarbScan — session context

Read this first. It records what previous sessions verified so you don't re-derive it.

## Build an APK right now

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk` (~87 MB).
A copy is kept on the Desktop as **`CarbScan-debug.apk`** — install that on a phone.

Other useful tasks:

```powershell
.\gradlew.bat :app:testDebugUnitTest         # 110 JVM tests
.\gradlew.bat :app:lintDebug                 # lint (currently clean)
.\gradlew.bat :app:assembleRelease           # minified, UNSIGNED (64 MB)
.\gradlew.bat :app:connectedDebugAndroidTest # 10 instrumented tests, needs a device
```

## What this project is

Native Android app: scan a food barcode → enter portion → read carbohydrate grams.
Requirements are in **`docs/MASTER-PROMPT.md`** (referenced throughout as §N).
Design decisions are in `docs/superpowers/specs/2026-08-13-carbquick-design.md`.

**It does NOT calculate insulin.** Not a diet tracker. Scope discipline is a hard requirement (§2).

**Working name is CarbScan** (`app.carbscan`). Docs under `docs/` still say *CarbQuick* — the owner
has NOT decided the public name, so do not sweep the docs. Branding is genuinely centralised in
`branding.gradle.kts` (Gradle `extra` properties feeding namespace, applicationId, versionName,
`app_name` and the OFF User-Agent).

GitHub: **https://github.com/Morfildor/CarbScan** — private, and staying private for now.

## Status (2026-08-14)

- ✅ Domain calculation engine, TDD — `CarbCalculator`, `NutritionBasis`, `PortionParser`,
  `NutritionValueValidator`, `PackageQuantityParser`, `BarcodeValidator`, `ResultFormatter`
- ✅ Room + `ProductRepository` owning the §10 lookup priority
- ✅ Open Food Facts data source behind the `ProductDataSource` abstraction
- ✅ Full UI: home, scanner, calculator, manual entry, verify dialog, label OCR, settings
- ✅ **110 JVM unit tests + 10 instrumented tests passing, lint clean**
- ✅ Debug APK and minified release APK both build
- ✅ §73 documentation set complete in `docs/`
- ✅ CI workflow (`.github/workflows/ci.yml`)
- ❌ Not done: §60 Compose UI tests, §69 UX critique pass, release signing, AAB

### Verified by actually running it (API 36 emulator)

- §70 new product: 48.2 g/100 g × 65 g → **31 g** / *31.3 g calculated*
- §70 known product: tap recent → portion pre-filled → instant result
- ml basis: 9.4 g/100 ml × 250 ml → **24 g**, portion locked to ml
- Dutch comma decimal, dark mode, 1.8× font scale
- Minified release build runs; Room, enums and ML Kit all survive R8

### NOT verified — do not claim otherwise

- **Decoding a real barcode.** CameraX binds and ML Kit's `libbarhopper_v3.so` loads and analyses
  frames, but no physical barcode has ever been decoded.
- **OCR against real packaging.** Only the pure text parser is tested.
- Live Open Food Facts responses (tested against MockWebServer, not the real API).
- Any physical device, incl. Samsung Galaxy.

## Toolchain (installed — do NOT reinstall)

| Thing | Path |
|---|---|
| JDK 21.0.12 (Temurin) | `C:\atools\jdk-21.0.12+8` |
| Android SDK | `C:\atools\sdk` (platforms 36 **and 37**, build-tools 36.0.0 and 37.0.0) |
| Emulator + API 36 image | installed; AVD named **`carbscan`** |

Launch the emulator headless:

```powershell
C:\atools\sdk\emulator\emulator.exe -avd carbscan -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
```

Boot takes ~90 s. Then `C:\atools\sdk\platform-tools\adb.exe devices`.
Note: `connectedAndroidTest` **uninstalls the app afterwards** — reinstall before driving the UI.

### Traps that cost real time

1. **`winget install` hangs forever** — UAC prompt nobody can answer. Portable archives only. No
   admin rights available.
2. **Windows MAX_PATH** breaks SDK extraction into deep scratchpad paths. That's why everything
   lives under the short `C:\atools`. `Expand-Archive` misreports it as a *missing file* error.
3. **`sdkmanager --licenses` ignores piped stdin** — write hash files into `$ANDROID_HOME\licenses`.
4. **AGP 9.x has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is a hard error.
   Kotlin options go in `android { kotlin { } }`.
5. **KSP has its own version line** (2.3.11), which does *not* track Kotlin's (2.3.21). Not a bug.
6. **`compileSdk` must be 37, not 36.** AndroidX (core 1.19.0, Compose 1.12.0, lifecycle 2.11.0)
   refuses to compile against 36. `targetSdk` stays **36** — the Play requirement. Independent.
7. **`resValue` needs `buildFeatures { resValues = true }`** in AGP 9.
8. **Don't put the JDK in the session scratchpad.** Temp dirs get cleaned; that is why it now lives
   at `C:\atools\jdk-21.0.12+8`.

## Verified facts (checked 2026-08-13 — do not trust training data over these)

| Fact | Value |
|---|---|
| Play target API requirement | **API 36**, deadline **2026-08-31** |
| AGP / Gradle / JDK | 9.3.1 / 9.7.0 / 21 |
| OFF read rate limit | **15 req/min/IP** — makes cache-first mandatory, not optional |
| OFF User-Agent | Mandatory, must identify the app |
| OFF data licence | **ODbL** — attribution *and* share-alike |

## Owner's confirmed decisions

1. **ml vs g** — portion locked to the product's basis unit. Never assume 1 ml = 1 g.
2. **Rounding** — whole gram dominant (`31 g`), decimal legible beneath (`31.3 g calculated`).
3. **Regulatory** — build to the **stricter** standard (as if an accessory to a medical device).
4. **Backup** — `allowBackup="false"`.
5. **Provenance ≠ verification** (owner correction, 2026-08-13) — `dataSource`
   (`OPEN_FOOD_FACTS`/`MANUAL`/`OCR`) and `verificationStatus` (`UNVERIFIED`/`USER_VERIFIED`) are
   **separate fields and must stay separate**. A product can come from OFF *and* be verified; that
   provenance must be preserved. Do not "simplify" these back into one enum.
6. Repo stays **private**; docs keep saying CarbQuick until the public name is decided.
7. **No Robolectric** — DAO tests stay instrumented.

## Architecture

```
domain/    Pure Kotlin, ZERO Android imports — the safety-critical layer. Keep it that way.
data/
  local/   Room: ProductEntity, ProductDao, RoomProductDataSource
  remote/  Retrofit + OpenFoodFactsDataSource
  settings/DataStore
  ProductRepository   ← owns the §10 lookup priority
ocr/       NutritionLabelParser (pure) + LabelAnalyzer (ML Kit)
ui/        Compose screens + ViewModels, immutable state via StateFlow
```

Key invariants, each pinned by a test:

- `movePointLeft(2)` for ÷100 — exact scale shift, cannot round or throw.
- Whole gram and displayed decimal are derived from `exact` **independently** — never round twice.
- `NutritionBasis` is a label, **never** a conversion factor.
- `isRemoteRefreshable` = not user-authored **AND** unverified. Both conditions matter.
- Carbohydrate values are stored as **TEXT** in SQLite, never REAL.

## Working agreements

- Verify library versions against Google Maven / Maven Central. **Stable only.**
- `domain/` stays pure Kotlin — it must be JVM-testable with no emulator.
- **Never claim something builds or passes without having run it.**
- Don't fabricate regulatory or policy wording (§44, §50). If it can't be verified, mark it as an
  owner action with a place to record the source and date.

## Open findings needing the owner

1. **§44 regulatory assessment is unresolved and blocks publication.** See
   `docs/regulatory-release-checklist.md`.
2. **ML Kit ships Google telemetry.** `com.google.android.datatransport` arrives transitively and
   merges `ACCESS_NETWORK_STATE`, against §9's two-permission rule and §34's "no telemetry SDK".
   No opt-out constant exists in the shipped artifacts — **none was invented**. Owner must decide.
3. Licence for the project not yet chosen.
4. Contact email is still `REPLACE_ME@example.com` throughout.
