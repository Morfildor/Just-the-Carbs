# Just the Carbs Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the "Just the Carbs" visual redesign (design tokens, typography, shape language) to
every existing screen, add a new first-launch onboarding flow, and rebrand the app's public
identity — without changing any ViewModel logic, domain code, Room schema, or existing test tags.

**Architecture:** Retheme in place. `Theme.kt` gets new color/typography/shape tokens; every screen
composable keeps its existing structure/state/callbacks and only has its Material3 component
styling (colors, shapes, text styles) updated to read the new tokens. One genuinely new screen
(Onboarding) is added with a trivial ViewModel and one new `AppSettings` field. Rebrand is a
`branding.gradle.kts` value change plus a handful of literal "CarbScan" strings in prose copy.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, DataStore Preferences, Navigation
Compose — all already in use; no new dependencies except one bundled font file.

**Spec:** `docs/superpowers/specs/2026-08-14-just-the-carbs-redesign.md` (this plan implements it
in full); source mockups in `docs/design_handoff_just_the_carbs/`.

## Global Constraints

- Kotlin package stays `app.carbscan.*` everywhere — do not rename any `package` declaration, file
  path under `kotlin/app/carbscan/`, or class name (`CarbScanTheme`, `CarbScanNavHost`,
  `CarbScanApplication`, `CarbScanDatabase`, `Theme.CarbScan` style names, `CARBSCAN_*` env vars).
  These are internal symbols, not user-visible branding.
- Only `branding.gradle.kts`'s `brandAppName`, `brandApplicationId`, `brandNamespace` change, plus
  literal "CarbScan" occurrences inside **string resource values** (user-visible prose).
- Every existing test tag stays the same string value: `PRODUCT_RESULT_TAG`,
  `PORTION_CORRECTION_FIELD_TAG`, `ADD_PORTION_UNIT_FIELD_TAG`, `USUAL_PORTION_ROW_TAG`,
  `PRODUCT_HERO_TAG`, `MEAL_ADD_TAG`, `MEAL_ADD_AND_SCAN_TAG`, `MEAL_BAR_TAG`, `MEAL_TOTAL_TAG`,
  `MEAL_CLEAR_TAG`, `PRODUCT_GALLERY_*_TAG`.
- No ViewModel, domain, or repository logic changes. No Room schema/migration changes.
- Colors: blue `#2F8FE0`, blue-soft `#E4F1FC`, red `#FF5C5C` (result only), orange `#FFA94D`,
  orange-soft `#FFEEDC`, cream `#FFF6EE`, ink `#181A1E`, ink-muted `#6B6A72`, line `#E4DFD3`,
  disabled blue `#DCE8F5`.
- Typography: Space Grotesk 500/600/700 for display/UI (headlines, numbers, buttons, labels);
  system default (Roboto on stock Android — already the Compose default, no change needed) for
  body/supporting text.
- Shape: card radius 18–22dp, button radius 14–18dp, pill/chip radius 999dp (full), bottom-sheet
  top corners 32dp.
- `testDebugUnitTest` (237 tests) and `lintDebug` must stay green throughout. Run after every task
  that touches `app/src/main` or `app/src/test`.
- Build command:
  ```powershell
  $env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
  $env:ANDROID_HOME="C:\atools\sdk"
  cd C:\Users\tuncb\Desktop\CarbTracker
  .\gradlew.bat :app:testDebugUnitTest
  ```

---

## File Structure

**New files:**
- `app/src/main/res/font/space_grotesk.ttf` — bundled variable font (weights 500/600/700 via
  `FontVariation.weight`).
- `app/src/main/kotlin/app/carbscan/ui/onboarding/OnboardingScreen.kt` — 3-slide carousel.
- `app/src/main/kotlin/app/carbscan/ui/onboarding/OnboardingViewModel.kt` — slide index + persist.
- `app/src/test/kotlin/app/carbscan/data/settings/SettingsRepositoryTest.kt` — new test file
  covering the new `hasSeenOnboarding` persistence (no existing test file for this repo).

**Modified files (visual-only unless noted):**
- `branding.gradle.kts` — rebrand values.
- `app/src/main/kotlin/app/carbscan/ui/theme/Theme.kt` — new tokens (colors, typography, shape).
- `app/src/main/kotlin/app/carbscan/domain/Settings.kt` — add `hasSeenOnboarding: Boolean` field.
- `app/src/main/kotlin/app/carbscan/data/settings/SettingsRepository.kt` — persist the new field.
- `app/src/main/kotlin/app/carbscan/ui/CarbScanNavHost.kt` — add onboarding route + conditional
  start destination.
- `app/src/main/kotlin/app/carbscan/ui/home/HomeScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/scan/ScannerScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/product/ProductScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/manual/ManualEntryScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/settings/SettingsScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/meal/MealScreen.kt`
- `app/src/main/kotlin/app/carbscan/ui/meal/MealComponents.kt`
- `app/src/main/kotlin/app/carbscan/ui/components/Common.kt`
- `app/src/main/kotlin/app/carbscan/ui/components/ProductThumbnail.kt`
- `app/src/main/kotlin/app/carbscan/ui/components/ProductHeroImage.kt`
- `app/src/main/res/values/strings.xml` + `values-nl/strings.xml` — replace literal "CarbScan" in
  prose (`permission_body`, `basis_mismatch_body`, `settings_safety_body`) with the app's own
  generic wording (no BuildConfig string-substitution — those strings don't currently take a name
  argument and adding one is an unnecessary structural change); add onboarding strings.
- `app/src/main/res/values/colors.xml` + `values-night/colors.xml` — cream/near-black
  window/splash background.
- `app/src/main/res/values/themes.xml` — starting-theme background color reference only (style
  names unchanged, per Global Constraints).

---

### Task 1: Rebrand via branding.gradle.kts and clean up literal "CarbScan" prose

**Files:**
- Modify: `branding.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml:41` (`permission_body`)
- Modify: `app/src/main/res/values/strings.xml:268` (`basis_mismatch_body`)
- Modify: `app/src/main/res/values/strings.xml:339` (`settings_safety_body`)
- Modify: `app/src/main/res/values-nl/strings.xml:32,111,172` (matching Dutch strings)
- Test: manual — build and check `BuildConfig.APP_NAME` / launcher label

**Interfaces:**
- Produces: `BuildConfig.APP_NAME == "Just the Carbs"`, `applicationId` starts with
  `app.justthecarbs`, `R.string.app_name` (generated) == "Just the Carbs".

- [ ] **Step 1: Update branding.gradle.kts**

```kotlin
/** Working name. Must not contain another company's trademark (brief §5, §51). */
extra["brandAppName"] = "Just the Carbs"

/** Play Store application id. Immutable once published — choose carefully. */
extra["brandApplicationId"] = "app.justthecarbs"

/** Kotlin/Java namespace. */
extra["brandNamespace"] = "app.carbscan"
```

Leave `brandNamespace` as `app.carbscan` — this only affects the generated manifest package
attribute, not Kotlin source, and the Global Constraints keep the Kotlin package unchanged.
Leave `brandContactEmail`, `brandVersionCode`, `brandVersionName` untouched.

- [ ] **Step 2: Replace literal "CarbScan" in English prose strings**

In `app/src/main/res/values/strings.xml`, change:

```xml
<string name="permission_body">This app uses the camera only to read barcodes and nutrition labels. Images are never uploaded or saved.</string>
```

```xml
<string name="basis_mismatch_body">Grams and millilitres are not interchangeable without knowing the product\'s density, so this app won\'t convert between them. To use the other unit, enter the value from the package.</string>
```

```xml
<string name="settings_safety_body">This app calculates the carbohydrate content of a portion. It does not calculate insulin or any other medication, and it does not replace the information printed on the package. Always check the package if a value looks wrong.</string>
```

- [ ] **Step 3: Replace the matching Dutch strings**

In `app/src/main/res/values-nl/strings.xml`:

```xml
<string name="permission_body">Deze app gebruikt de camera alleen om streepjescodes en voedingswaardelabels te lezen. Afbeeldingen worden nooit geüpload of opgeslagen.</string>
```

```xml
<string name="basis_mismatch_body">Grammen en milliliters zijn niet uitwisselbaar zonder de dichtheid van het product, dus deze app rekent er niet tussen om. Voer de waarde van de verpakking in om de andere eenheid te gebruiken.</string>
```

```xml
<string name="settings_safety_body">Deze app berekent het koolhydraatgehalte van een portie. De app berekent geen insuline of andere medicatie en vervangt niet de informatie op de verpakking. Controleer altijd de verpakking als een waarde niet klopt.</string>
```

- [ ] **Step 4: Build and verify the rebrand took effect**

Run:
```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:assembleDebug
```
Expected: build succeeds. Confirm the applicationId by checking
`app\build\outputs\apk\debug\output-metadata.json` or `aapt dump badging` contains
`app.justthecarbs.debug` and `application-label:'Just the Carbs'`.

- [ ] **Step 5: Commit**

```bash
git add branding.gradle.kts app/src/main/res/values/strings.xml app/src/main/res/values-nl/strings.xml
git commit -m "Rebrand app to Just the Carbs (app name, applicationId, prose strings)"
```

---

### Task 2: Bundle Space Grotesk and add design tokens to Theme.kt

**Files:**
- Create: `app/src/main/res/font/space_grotesk.ttf` (already downloaded to scratchpad at
  `C:\Users\tuncb\AppData\Local\Temp\claude\c--Users-tuncb-Desktop-CarbTracker\ea9ad5ea-6787-4f31-abcc-28eb3be35d02\scratchpad\SpaceGrotesk.ttf`
  — Space Grotesk, SIL Open Font License, from the `google/fonts` repository)
- Modify: `app/src/main/kotlin/app/carbscan/ui/theme/Theme.kt`
- Test: `app/src/test/kotlin` — no existing Theme test; verified by unit test suite staying green
  (Theme.kt has no testable logic, only declarations) and by the emulator walkthrough in Task 11.

**Interfaces:**
- Produces (read by every screen task below):
  - `JustTheCarbsColors` object: `blue`, `blueSoft`, `red`, `orange`, `orangeSoft`, `cream`, `ink`,
    `inkMuted`, `line`, `disabledBlue` — plus dark-mode equivalents, all `androidx.compose.ui.graphics.Color`.
  - `LocalResultColor: androidx.compose.runtime.staticCompositionLocalOf<Color>` — or simpler, a
    top-level `@Composable fun resultColor(): Color` that returns the correct red/dark-red for the
    current theme. (Chosen: a `@Composable` accessor `JustTheCarbsColors.result` computed from
    `isSystemInDarkTheme()`-independent logic is wrong since it must follow `themeChoice`, not the
    system — so it is exposed via `MaterialTheme`-adjacent `LocalCarbScanExtendedColors` composition
    local, set once in `CarbScanTheme`.)
  - `SpaceGrotesk: FontFamily` — the bundled font, weighted via `FontVariation.Settings`.
  - `Space.cardRadius = 18.dp`, `Space.buttonRadius = 16.dp`, `Space.chipRadius = 999.dp` (already
    this value, unchanged), `Space.mediaRadius = 16.dp`, `Space.sheetTopRadius = 32.dp` (new).
  - `NumberType.result` = 64sp/-2sp/SpaceGrotesk/700, `NumberType.portion` = 48sp/-1.5sp/SpaceGrotesk/700
    (both keep their existing field names and the existing `resultAutoSize` shrink mechanism
    untouched — only the `TextStyle` values inside change).
  - `CarbScanTheme(themeChoice, content)` — same signature, unchanged call sites.

- [ ] **Step 1: Copy the font file into the resource directory**

```powershell
New-Item -ItemType Directory -Force "app\src\main\res\font" | Out-Null
Copy-Item "C:\Users\tuncb\AppData\Local\Temp\claude\c--Users-tuncb-Desktop-CarbTracker\ea9ad5ea-6787-4f31-abcc-28eb3be35d02\scratchpad\SpaceGrotesk.ttf" "app\src\main\res\font\space_grotesk.ttf"
```

Android resource filenames must be lowercase with underscores — `space_grotesk.ttf` is correct.

- [ ] **Step 2: Add the color tokens and font family to Theme.kt**

Replace the existing color declarations (`Ink`, `Paper`, `PaperRaised`, `InkMuted`, `LineLight`,
`Night`, `NightRaised`, `Chalk`, `ChalkMuted`, `LineDark`, `TealDeep`, `TealSoft`, `TealBright`,
`TealShade`, `WarnLight`, `WarnDark`) with:

```kotlin
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import app.carbscan.R

/** Space Grotesk, bundled as a variable font (brief: design tokens, Typography). */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Light palette (design doc "Design tokens" section).
private val Blue = Color(0xFF2F8FE0)
private val BlueSoft = Color(0xFFE4F1FC)
private val Red = Color(0xFFFF5C5C)
private val Orange = Color(0xFFFFA94D)
private val OrangeSoft = Color(0xFFFFEEDC)
private val Cream = Color(0xFFFFF6EE)
private val Ink = Color(0xFF181A1E)
private val InkMuted = Color(0xFF6B6A72)
private val LineLight = Color(0xFFE4DFD3)
private val DisabledBlue = Color(0xFFDCE8F5)

// Dark palette — extrapolated from the light tokens (no dark spec exists in the handoff).
// Cream inverts to near-black, ink inverts to off-white; accent hues held close to their light
// values, brightened only enough to hold contrast on a dark ground.
private val Night = Color(0xFF15140F)
private val NightRaised = Color(0xFF1E1D18)
private val Chalk = Color(0xFFF2EFE8)
private val ChalkMuted = Color(0xFFAFAEA8)
private val LineDark = Color(0xFF39372F)
private val BlueDark = Color(0xFF5CA6E8)
private val BlueSoftDark = Color(0xFF16324A)
private val RedDark = Color(0xFFFF7A7A)
private val OrangeDark = Color(0xFFFFB868)
private val OrangeSoftDark = Color(0xFF4A3418)
private val DisabledBlueDark = Color(0xFF2A3A47)

/**
 * The result red and a few tokens Material's ColorScheme has no matching role for (brief: "Why
 * red here and blue elsewhere"). `result` is spent on exactly one thing per screen — the
 * carbohydrate number — never on `error`, which is semantically a fault state this app doesn't have.
 */
data class ExtendedColors(
    val result: Color,
    val orangeSoft: Color,
    val onOrangeSoft: Color,
    val disabledButton: Color,
)

private val LightExtendedColors = ExtendedColors(
    result = Red,
    orangeSoft = OrangeSoft,
    onOrangeSoft = Color(0xFFB5710B),
    disabledButton = DisabledBlue,
)

private val DarkExtendedColors = ExtendedColors(
    result = RedDark,
    orangeSoft = OrangeSoftDark,
    onOrangeSoft = OrangeDark,
    disabledButton = DisabledBlueDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
```

- [ ] **Step 3: Update LightColors / DarkColors to the new palette**

Replace the body of `LightColors`:

```kotlin
private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF0B3E63),
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF0B3E63),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFBF8),
    surfaceContainer = Color(0xFFF3EFE6),
    surfaceContainerHigh = Color(0xFFEBE6DA),
    surfaceContainerHighest = Color(0xFFE2DCCC),
    outline = LineLight,
    outlineVariant = LineLight,
    error = Color(0xFF9B2C2C),
    onError = Color.White,
    tertiary = Orange,
    tertiaryContainer = OrangeSoft,
    onTertiaryContainer = Color(0xFF7A4B0A),
)
```

Replace the body of `DarkColors`:

```kotlin
private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF00243D),
    primaryContainer = BlueSoftDark,
    onPrimaryContainer = BlueDark,
    secondary = ChalkMuted,
    onSecondary = Night,
    secondaryContainer = BlueSoftDark,
    onSecondaryContainer = BlueDark,
    background = Night,
    onBackground = Chalk,
    surface = Night,
    onSurface = Chalk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = ChalkMuted,
    surfaceContainerLowest = Color(0xFF0C0B08),
    surfaceContainerLow = Color(0xFF19180F),
    surfaceContainer = Color(0xFF201E17),
    surfaceContainerHigh = Color(0xFF2A2820),
    surfaceContainerHighest = Color(0xFF34322A),
    outline = LineDark,
    outlineVariant = LineDark,
    error = Color(0xFFF2999A),
    onError = Color(0xFF3A0A0B),
    tertiary = OrangeDark,
    tertiaryContainer = OrangeSoftDark,
    onTertiaryContainer = OrangeDark,
)
```

- [ ] **Step 4: Update Space radii**

```kotlin
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Card radius (design tokens: 16–22px, 18 most common). */
    val cardRadius = 18.dp

    /** Buttons and inputs (design tokens: 14–18px). */
    val buttonRadius = 16.dp

    /** Product imagery. */
    val mediaRadius = 16.dp

    /** Chips stay pill-shaped. */
    val chipRadius = 999.dp

    val minTouchTarget = 48.dp
    val screenEdge = 20.dp
    val thumbnail = 52.dp
    val resultElevation = 3.dp
    val cardElevation = 0.dp

    /** Pinned bottom sheet top corners (design tokens: 32px). */
    val sheetTopRadius = 32.dp
}
```

- [ ] **Step 5: Update NumberType and CarbScanTypography to use Space Grotesk**

```kotlin
object NumberType {
    val result = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
    )

    val resultAutoSize = TextAutoSize.StepBased(
        minFontSize = 36.sp,
        maxFontSize = 64.sp,
        stepSize = 1.sp,
    )

    val portion = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.5).sp,
        textAlign = TextAlign.Center,
    )

    val supporting = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
}

private val CarbScanTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = labelMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
    )
}
```

Body styles (`bodyLarge`/`bodyMedium`/`bodySmall`) are left as `Typography()`'s defaults —
Compose's platform default font is Roboto on stock Android, matching the doc's body-font spec with
no bundled file needed.

- [ ] **Step 6: Wire the extended colors into CarbScanTheme**

```kotlin
@Composable
fun CarbScanTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = CarbScanTypography,
            content = content,
        )
    }
}
```

- [ ] **Step 7: Add a convenience accessor**

```kotlin
/** The extended tokens Material's ColorScheme has no role for — see [ExtendedColors]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current
```

Place this as a top-level extension property in `Theme.kt`, so call sites write
`MaterialTheme.extendedColors.result` alongside their existing `MaterialTheme.colorScheme.*` reads.

- [ ] **Step 8: Build and run unit tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, 237 tests passing (Theme.kt has no unit-testable logic; this confirms
nothing else broke from the import/signature changes).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/font/space_grotesk.ttf app/src/main/kotlin/app/carbscan/ui/theme/Theme.kt
git commit -m "Add Just the Carbs design tokens: pastel palette, Space Grotesk, updated shape scale"
```

---

### Task 3: Restyle shared components (Common.kt, ProductThumbnail.kt, ProductHeroImage.kt)

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/components/Common.kt`
- Modify: `app/src/main/kotlin/app/carbscan/ui/components/ProductThumbnail.kt`
- Modify: `app/src/main/kotlin/app/carbscan/ui/components/ProductHeroImage.kt`
- Test: existing instrumented tests referencing `PRODUCT_HERO_TAG` (in `ProductScreenTest.kt`) —
  run after Task 4, since Product screen is the composable that hosts `ProductHeroImage`.

**Interfaces:**
- Consumes: `MaterialTheme.extendedColors` from Task 2, updated `Space.cardRadius`/`mediaRadius`.
- Produces: no signature changes — `SourceBadge`, `FavoriteButton`, `RecoveryPanel`,
  `PrimaryAction`, `SecondaryAction`, `SectionLabel`, `ProductThumbnail`, `SearchThumbnail`,
  `ProductHeroImage` all keep their exact existing signatures.

- [ ] **Step 1: Restyle SourceBadge's "ONLINE"-style pill in Common.kt**

In `SourceBadge`, change the unverified-badge background/text colors from
`MaterialTheme.colorScheme.surfaceVariant` / `onSurfaceVariant` to the orange badge treatment the
doc specifies for the "ONLINE" pill:

```kotlin
Box(
    modifier = Modifier
        .background(
            color = if (verified) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.extendedColors.orangeSoft
            },
            shape = RoundedCornerShape(50),
        )
        .padding(horizontal = 12.dp, vertical = 6.dp),
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (verified) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.extendedColors.onOrangeSoft
        },
    )
}
```

(Only the two `color = ...` branches for the non-verified case change; the verified/primary branch
is unchanged since verified badges use the blue primary container per the doc's "blue owns every
interactive/active state" rule.)

- [ ] **Step 2: Restyle PrimaryAction/SecondaryAction button radius**

`PrimaryAction` and `SecondaryAction` already read `Space.buttonRadius`, which Task 2 already
updated to 16dp — no code change needed here, only verify by reading the file that both still use
`RoundedCornerShape(Space.buttonRadius)` (they do, per the current source). No step needed beyond
confirming — skip to Step 3.

- [ ] **Step 3: Restyle ProductThumbnail's monogram tile background**

In `ProductThumbnail`, change the tile background from `MaterialTheme.colorScheme.surfaceContainerHigh`
to the blue-soft monogram tile the doc specifies for both Home's recent cards and this thumbnail:

```kotlin
.background(MaterialTheme.colorScheme.primaryContainer)
```

(`primaryContainer` now resolves to `BlueSoft`/`BlueSoftDark` per Task 2, and the monogram text
already reads `MaterialTheme.colorScheme.onSurfaceVariant` — change that to
`MaterialTheme.colorScheme.onPrimaryContainer` so the initials render in blue, matching
`result.html`'s "VB" monogram in blue-on-blue-soft.)

Apply the identical two-line change to `SearchThumbnail` in the same file (it currently duplicates
the same background/text color choices).

- [ ] **Step 4: Restyle ProductHeroImage's monogram fallback text color**

In `ProductHeroImageContent`, change the monogram text color from
`MaterialTheme.colorScheme.onSurfaceVariant` to `MaterialTheme.colorScheme.onPrimaryContainer`, and
change the container background from `MaterialTheme.colorScheme.surfaceContainerLowest` to
`MaterialTheme.colorScheme.primaryContainer` for the *fallback* (no-image) state only — when an
image successfully loads, the container background should stay neutral (white/near-black) so the
photo (usually shot on white) doesn't fight a colored backdrop, matching the existing code comment
about "White, not a tinted surface" for loaded images. Implement this as a conditional:

```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .height(height)
        .clip(shape)
        .background(
            if (loaded) MaterialTheme.colorScheme.surfaceContainerLowest
            else MaterialTheme.colorScheme.primaryContainer,
        )
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .testTag(PRODUCT_HERO_TAG)
        .clearAndSetSemantics {
            if (onClick != null) {
                role = Role.Button
                contentDescription = viewImagesDescription
            }
        },
    contentAlignment = Alignment.Center,
) {
    if (!loaded) {
        Text(
            text = product.monogram(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 44.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    // ... AsyncImage unchanged
}
```

(Font size 44sp matches the doc's `result.html` monogram spec exactly, up from the current 40sp.)

- [ ] **Step 5: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/components/Common.kt app/src/main/kotlin/app/carbscan/ui/components/ProductThumbnail.kt app/src/main/kotlin/app/carbscan/ui/components/ProductHeroImage.kt
git commit -m "Restyle shared components (badges, thumbnails, hero image) to Just the Carbs tokens"
```

---

### Task 4: Restyle the Product/Result screen

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/product/ProductScreen.kt`
- Test: `app/src/androidTest/kotlin/app/carbscan/ui/ProductScreenTest.kt` (existing, run on
  emulator — see Task 11 for the full instrumented run; this task only requires the unit suite to
  stay green since Compose UI tests need a device/emulator, not `testDebugUnitTest`)

**Interfaces:**
- Consumes: `MaterialTheme.extendedColors.result` (Task 2), `NumberType.result`/`.portion` (Task 2),
  `Space.cardRadius`/`.buttonRadius`/`.sheetTopRadius` (Task 2).
- Produces: no signature changes to `ProductScreen` or any of its private composables — same
  parameters, same test tags (`PORTION_CORRECTION_FIELD_TAG`, `ADD_PORTION_UNIT_FIELD_TAG`,
  `USUAL_PORTION_ROW_TAG`, `PRODUCT_RESULT_TAG`).

- [ ] **Step 1: Restyle the result panel's dominant number to the doc's red**

In `ResultPanel`, the `Text` showing `dominant` currently reads
`color = MaterialTheme.colorScheme.onSurface`. Change it:

```kotlin
Text(
    text = value,
    style = NumberType.result,
    color = MaterialTheme.extendedColors.result,
    maxLines = 1,
    autoSize = NumberType.resultAutoSize,
    modifier = Modifier
        .testTag(PRODUCT_RESULT_TAG)
        .semantics { liveRegion = LiveRegionMode.Polite },
)
```

- [ ] **Step 2: Restyle the pinned result panel's shape to the doc's 32dp top corners**

`panelShape` already reads `RoundedCornerShape(topStart = Space.xl, topEnd = Space.xl)` where
`Space.xl = 32.dp` — this already matches the doc's spec numerically, but rename the reference to
the new named constant for clarity and to decouple it from the general spacing scale:

```kotlin
val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)
```

- [ ] **Step 3: Restyle "Scan next" button to filled blue, "Add to meal" to outlined**

`MealActions` (in `MealComponents.kt`, shared with Meal screen) already uses `Button` (filled,
which reads `MaterialTheme.colorScheme.primary` = Blue after Task 2) for "Add & scan next" and
`OutlinedButton` for "Add to meal" — this already matches the doc's "outlined Add to meal / filled
blue Scan next" spec with no code change needed. Confirm by reading the current source (already
verified during planning) — no step needed, move to Step 4.

- [ ] **Step 4: Restyle the decorative background circle**

Add the doc's blue-soft decorative circle bleeding off the top-right corner, matching
`result.html`'s `<div style="position:absolute;top:-90px;right:-70px;...background:BLUE_SOFT">`.
In `ProductScreen`'s outer `Column`, wrap the existing content in a `Box` so the circle can be
absolutely positioned behind it:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 70.dp, y = (-90).dp)
            .size(220.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ... existing ProductTopBar / loading / failure / CalculatorBody content, unchanged
    }
}
```

Add imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.offset`,
`androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.Alignment` (already imported).

- [ ] **Step 5: Restyle the source badge / per-100 summary text style**

`ProductSummary`'s per-100 text already reads `MaterialTheme.typography.titleLarge`, which now
carries Space Grotesk Bold after Task 2 — no change needed.

- [ ] **Step 6: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/product/ProductScreen.kt
git commit -m "Restyle Product/Result screen to Just the Carbs tokens"
```

---

### Task 5: Restyle the Home screen

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `MaterialTheme.extendedColors`, `Space` tokens from Task 2.
- Produces: no signature changes to `HomeScreen`, `RecentCard`, `EmptyState`.

- [ ] **Step 1: Add the decorative top-right circle**

Same pattern as Task 4 Step 4, wrapping `HomeScreen`'s content `Column` in a `Box`:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 80.dp, y = (-90).dp)
            .size(220.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ... existing header / MealBarIfPresent / RecentList-or-EmptyState / bottom CTA column, unchanged
    }
}
```

- [ ] **Step 2: Restyle the primary CTA button with the doc's blue glow shadow**

The `Button` for "Scan barcode" currently has no explicit shadow. Add one matching the doc's
`0 8px 20px rgba(47,143,224,0.32)`:

```kotlin
Button(
    onClick = onScan,
    shape = RoundedCornerShape(Space.buttonRadius),
    modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(Space.buttonRadius),
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        ),
) {
    // unchanged Icon + Spacer + Text
}
```

Add import: `androidx.compose.ui.draw.shadow`.

- [ ] **Step 3: Restyle the empty state's icon tile to blue-soft**

In `EmptyState`, change the tile background from `MaterialTheme.colorScheme.surfaceContainer` to
`MaterialTheme.colorScheme.primaryContainer`, and the icon tint from
`MaterialTheme.colorScheme.onSurfaceVariant` to `MaterialTheme.colorScheme.onPrimaryContainer`.

- [ ] **Step 4: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/home/HomeScreen.kt
git commit -m "Restyle Home screen to Just the Carbs tokens"
```

---

### Task 6: Restyle the Scanner screen

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/scan/ScannerScreen.kt`

**Interfaces:**
- Consumes: `MaterialTheme.colorScheme.primary` (now Blue) for the scan frame accent.
- Produces: no signature changes.

- [ ] **Step 1: Restyle ScanFrame's border to the doc's violet/blue stroke**

Replace the current transparent/white-tinted frame with a stroked rounded rectangle matching the
doc's `scanner.html` (`stroke={BLUE} strokeWidth="3"` with `rgba(47,143,224,0.08)` fill):

```kotlin
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .height(180.dp)
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .border(3.dp, accent, RoundedCornerShape(26.dp)),
    )
}
```

Add import: `androidx.compose.foundation.border`.

- [ ] **Step 2: Restyle the manual-entry pill button border/fill**

The `TextButton` for "Enter barcode manually" currently has no border. Wrap it to match the doc's
pill: `52dp` tall, `16dp` radius, `1px rgba(255,255,255,0.25)` border,
`rgba(255,255,255,0.08)` fill:

```kotlin
TextButton(
    onClick = { showBarcodeDialog = true },
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
        .height(52.dp)
        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
) {
    Text(
        text = stringResource(R.string.scanner_enter_manually),
        color = Color.White,
    )
}
```

- [ ] **Step 3: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/scan/ScannerScreen.kt
git commit -m "Restyle Scanner screen to Just the Carbs tokens"
```

---

### Task 7: Restyle Manual Entry and Settings screens

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/manual/ManualEntryScreen.kt`
- Modify: `app/src/main/kotlin/app/carbscan/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `MaterialTheme.extendedColors`, `Space` tokens from Task 2.
- Produces: no signature changes.

- [ ] **Step 1: Restyle Manual Entry's decorative circle and Save button disabled state**

Add the top-left decorative circle (orange-soft per the doc) using the same `Box`-wrapping pattern
as Task 4/5:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = (-80).dp, y = (-90).dp)
            .size(200.dp)
            .background(MaterialTheme.extendedColors.orangeSoft.copy(alpha = 0.9f), CircleShape),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ... existing header / form / save button, unchanged
    }
}
```

The `Save product` `Button`'s disabled state should render as the doc's flat pale blue-gray. Since
`Button`'s `enabled = state.canSave` already drives Material3's built-in disabled container color,
override it explicitly to match the doc's exact `#DCE8F5`:

```kotlin
Button(
    onClick = onSave,
    enabled = state.canSave,
    shape = RoundedCornerShape(Space.buttonRadius),
    colors = ButtonDefaults.buttonColors(
        disabledContainerColor = MaterialTheme.extendedColors.disabledButton,
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
    ),
    modifier = Modifier.fillMaxWidth().height(58.dp),
) { Text(stringResource(R.string.manual_save)) }
```

Add imports: `androidx.compose.material3.ButtonDefaults`, `androidx.compose.foundation.shape.CircleShape`.

- [ ] **Step 2: Restyle Settings' safety card to the doc's orange-tinted card**

The safety card currently renders as plain `Text` composables with no card background. Wrap the
title+body pair in a card matching the doc's `#FFEEDC` background, `18dp` radius:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Space.cardRadius))
        .background(MaterialTheme.extendedColors.orangeSoft)
        .padding(Space.m),
    verticalArrangement = Arrangement.spacedBy(Space.s),
) {
    Text(
        text = stringResource(R.string.settings_safety_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.extendedColors.onOrangeSoft,
    )
    Text(
        text = stringResource(R.string.settings_safety_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.extendedColors.onOrangeSoft,
    )
}
```

This replaces the two standalone `Text` calls for `settings_safety_title`/`settings_safety_body`
in the existing `Column`. The attribution `Text` calls immediately after stay outside this card,
unchanged, matching the doc's layout (attribution text sits below the card, not inside it).

Add import: `androidx.compose.foundation.shape.RoundedCornerShape` (likely already imported —
verify) and `androidx.compose.foundation.background`/`androidx.compose.ui.draw.clip` (already
imported in this file).

- [ ] **Step 3: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/manual/ManualEntryScreen.kt app/src/main/kotlin/app/carbscan/ui/settings/SettingsScreen.kt
git commit -m "Restyle Manual Entry and Settings screens to Just the Carbs tokens"
```

---

### Task 8: Restyle the Meal screen and MealComponents

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/ui/meal/MealScreen.kt`
- Modify: `app/src/main/kotlin/app/carbscan/ui/meal/MealComponents.kt`

**Interfaces:**
- Consumes: `MaterialTheme.extendedColors.result`, `Space.sheetTopRadius` from Task 2.
- Produces: no signature changes.

- [ ] **Step 1: Restyle the meal total's dominant figure to red**

In `MealTotalPanel`, the `Text` showing `dominant` currently reads
`color = MaterialTheme.colorScheme.onSurface`. Change to:

```kotlin
Text(
    text = dominant,
    style = NumberType.result,
    color = MaterialTheme.extendedColors.result,
    maxLines = 1,
    autoSize = NumberType.resultAutoSize,
    modifier = Modifier.testTag(MEAL_TOTAL_TAG),
)
```

- [ ] **Step 2: Restyle MealTotalPanel's shape to the doc's 32dp sheet radius**

Same as Task 4 Step 2:

```kotlin
val panelShape = RoundedCornerShape(topStart = Space.sheetTopRadius, topEnd = Space.sheetTopRadius)
```

- [ ] **Step 3: Add the decorative top-right circle to MealScreen**

Same pattern as prior screens, orange-soft per the doc's `meal.html`:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 80.dp, y = (-90).dp)
            .size(200.dp)
            .background(MaterialTheme.extendedColors.orangeSoft.copy(alpha = 0.9f), CircleShape),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ... existing header / item list-or-empty / MealTotalPanel, unchanged
    }
}
```

- [ ] **Step 4: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 237 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/meal/MealScreen.kt app/src/main/kotlin/app/carbscan/ui/meal/MealComponents.kt
git commit -m "Restyle Meal screen to Just the Carbs tokens"
```

---

### Task 9: Add hasSeenOnboarding to AppSettings and SettingsRepository

**Files:**
- Modify: `app/src/main/kotlin/app/carbscan/domain/Settings.kt`
- Modify: `app/src/main/kotlin/app/carbscan/data/settings/SettingsRepository.kt`
- Create: `app/src/test/kotlin/app/carbscan/data/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces: `AppSettings.hasSeenOnboarding: Boolean` (default `false`),
  `SettingsRepository.setHasSeenOnboarding(seen: Boolean): suspend () -> Unit`.
- Consumes (by Task 10): the above two.

- [ ] **Step 1: Write the failing test**

Check whether a DataStore-backed repository test needs a fake `Context` — inspect how other
DataStore-based tests in this codebase are structured. Since no `SettingsRepositoryTest` exists
yet, use Robolectric-free instrumentation is not available per Global Constraints ("No
Robolectric — DAO tests stay instrumented" is documented for Room, but `SettingsRepository` uses
DataStore Preferences, not Room, and DataStore's `PreferenceDataStoreFactory.create` can run against
a JVM temp file without Android). Write the test using an in-memory/temp-file DataStore:

```kotlin
package app.carbscan.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRepositoryTest {

    private fun tempDataStore(): DataStore<Preferences> {
        val dir = File.createTempFile("settings-test", "").apply { delete(); mkdirs() }
        return PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "settings.preferences_pb") },
        )
    }

    @Test
    fun `hasSeenOnboarding defaults to false`() = runTest {
        val store = tempDataStore()
        val repo = SettingsRepository.forTesting(store)
        assertEquals(false, repo.settings.first().hasSeenOnboarding)
    }

    @Test
    fun `setHasSeenOnboarding persists true`() = runTest {
        val store = tempDataStore()
        val repo = SettingsRepository.forTesting(store)
        repo.setHasSeenOnboarding(true)
        assertTrue(repo.settings.first().hasSeenOnboarding)
    }
}
```

This requires `SettingsRepository` to expose a way to inject a `DataStore<Preferences>` for
testing, since the production constructor only takes a `Context`. Add a package-private secondary
constructor / factory rather than changing the public API.

- [ ] **Step 2: Run the test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.carbscan.data.settings.SettingsRepositoryTest"
```
Expected: FAIL — compilation error, `hasSeenOnboarding` and `SettingsRepository.forTesting` do not
exist yet.

- [ ] **Step 3: Add hasSeenOnboarding to AppSettings**

```kotlin
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val resultStyle: ResultStyle = ResultStyle.DECIMAL_DOMINANT,
    val hapticsEnabled: Boolean = true,
    val hasSeenOnboarding: Boolean = false,
)
```

- [ ] **Step 4: Add the DataStore key, mapping, setter, and test constructor to SettingsRepository**

```kotlin
class SettingsRepository private constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsStore)

    val settings: Flow<AppSettings> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            AppSettings(
                theme = prefs[THEME]?.toEnum(ThemeChoice.entries, ThemeChoice.SYSTEM)
                    ?: ThemeChoice.SYSTEM,
                resultStyle = prefs[RESULT_STYLE]?.toEnum(ResultStyle.entries, ResultStyle.DECIMAL_DOMINANT)
                    ?: ResultStyle.DECIMAL_DOMINANT,
                hapticsEnabled = prefs[HAPTICS] ?: true,
                hasSeenOnboarding = prefs[HAS_SEEN_ONBOARDING] ?: false,
            )
        }

    suspend fun setTheme(choice: ThemeChoice) =
        store.edit { it[THEME] = choice.name }.let {}

    suspend fun setResultStyle(style: ResultStyle) =
        store.edit { it[RESULT_STYLE] = style.name }.let {}

    suspend fun setHapticsEnabled(enabled: Boolean) =
        store.edit { it[HAPTICS] = enabled }.let {}

    suspend fun setHasSeenOnboarding(seen: Boolean) =
        store.edit { it[HAS_SEEN_ONBOARDING] = seen }.let {}

    private fun <T : Enum<T>> String.toEnum(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        /** Test-only entry point so tests can inject a temp-file DataStore instead of a Context. */
        internal fun forTesting(store: DataStore<Preferences>) = SettingsRepository(store)

        private val THEME = stringPreferencesKey("theme")
        private val RESULT_STYLE = stringPreferencesKey("result_style")
        private val HAPTICS = booleanPreferencesKey("haptics")
        private val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.carbscan.data.settings.SettingsRepositoryTest"
```
Expected: PASS, 2 tests green.

- [ ] **Step 6: Run the full unit suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, 239 tests passing (237 existing + 2 new).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/domain/Settings.kt app/src/main/kotlin/app/carbscan/data/settings/SettingsRepository.kt app/src/test/kotlin/app/carbscan/data/settings/SettingsRepositoryTest.kt
git commit -m "Add hasSeenOnboarding to AppSettings and SettingsRepository"
```

---

### Task 10: Build the Onboarding screen, ViewModel, icon mark, and wire into navigation

**Files:**
- Create: `app/src/main/kotlin/app/carbscan/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/kotlin/app/carbscan/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/kotlin/app/carbscan/ui/CarbScanNavHost.kt`
- Modify: `app/src/main/res/values/strings.xml` (add onboarding strings)
- Modify: `app/src/main/kotlin/app/carbscan/MainActivity.kt` (pass settings-derived start
  destination — see Step 5)

**Interfaces:**
- Consumes: `AppSettings.hasSeenOnboarding` and `SettingsRepository.setHasSeenOnboarding` (Task 9),
  `MaterialTheme.extendedColors`/`SpaceGrotesk` (Task 2).
- Produces: `OnboardingScreen(slideIndex: Int, onNext: () -> Unit, onSkip: () -> Unit,
  onGetStarted: () -> Unit)`, `OnboardingViewModel(settingsRepository: SettingsRepository)` with
  `val slideIndex: StateFlow<Int>`, `fun next()`, `fun skip()`, `fun complete()`.

- [ ] **Step 1: Add onboarding string resources**

In `app/src/main/res/values/strings.xml`, add a new section:

```xml
<!-- Onboarding (first-launch carousel) -->
<string name="onboarding_skip" tools:ignore="MissingTranslation">Skip</string>
<string name="onboarding_step_1" tools:ignore="MissingTranslation">STEP 1</string>
<string name="onboarding_step_2" tools:ignore="MissingTranslation">STEP 2</string>
<string name="onboarding_step_3" tools:ignore="MissingTranslation">STEP 3</string>
<string name="onboarding_title_1" tools:ignore="MissingTranslation">Scan it.</string>
<string name="onboarding_body_1" tools:ignore="MissingTranslation">Point your camera at any barcode. No searching, no typing a product name.</string>
<string name="onboarding_title_2" tools:ignore="MissingTranslation">Size the portion.</string>
<string name="onboarding_body_2" tools:ignore="MissingTranslation">Drag, type, or tap a preset. Grams, slices, whatever makes sense for the food.</string>
<string name="onboarding_title_3" tools:ignore="MissingTranslation">Get the number.</string>
<string name="onboarding_body_3" tools:ignore="MissingTranslation">Just the carbs. Nothing else on screen competes with it.</string>
<string name="onboarding_next" tools:ignore="MissingTranslation">Next</string>
<string name="onboarding_get_started" tools:ignore="MissingTranslation">Get started</string>
```

(English-only, `tools:ignore="MissingTranslation"`, matching the pattern every other post-launch
feature in this file already follows.)

- [ ] **Step 2: Write OnboardingViewModel**

```kotlin
package app.carbscan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carbscan.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** First-launch carousel state: which of the 3 slides is showing, and marking it seen on exit. */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _slideIndex = MutableStateFlow(0)
    val slideIndex: StateFlow<Int> = _slideIndex.asStateFlow()

    fun next() {
        _slideIndex.value = (_slideIndex.value + 1).coerceAtMost(LAST_SLIDE)
    }

    fun skip() {
        _slideIndex.value = LAST_SLIDE
    }

    fun complete() {
        viewModelScope.launch { settingsRepository.setHasSeenOnboarding(true) }
    }

    private companion object {
        const val LAST_SLIDE = 2
    }
}
```

- [ ] **Step 3: Write OnboardingScreen**

```kotlin
package app.carbscan.ui.onboarding

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import app.carbscan.R
import app.carbscan.ui.theme.NumberType
import app.carbscan.ui.theme.Space
import app.carbscan.ui.theme.SpaceGrotesk
import app.carbscan.ui.theme.extendedColors

private data class Slide(
    val eyebrowRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

private val SLIDES = listOf(
    Slide(R.string.onboarding_step_1, R.string.onboarding_title_1, R.string.onboarding_body_1),
    Slide(R.string.onboarding_step_2, R.string.onboarding_title_2, R.string.onboarding_body_2),
    Slide(R.string.onboarding_step_3, R.string.onboarding_title_3, R.string.onboarding_body_3),
)

/**
 * First-launch, 3-slide carousel (design doc "Onboarding"). Slide 1: blue bg, slide 2: cream bg,
 * slide 3: red bg — colors read from the theme's own tokens rather than hardcoded, so dark mode
 * gets a sensible extrapolation automatically.
 */
@Composable
fun OnboardingScreen(
    slideIndex: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val slide = SLIDES[slideIndex]
    val last = slideIndex == SLIDES.lastIndex

    val background by animateColorAsState(
        targetValue = when (slideIndex) {
            0 -> MaterialTheme.colorScheme.primary
            1 -> MaterialTheme.colorScheme.background
            else -> MaterialTheme.extendedColors.result
        },
        animationSpec = tween(280),
        label = "onboardingBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (slideIndex == 1) MaterialTheme.colorScheme.onBackground else Color.White,
        animationSpec = tween(280),
        label = "onboardingForeground",
    )
    val markColor = if (slideIndex == 1) MaterialTheme.colorScheme.tertiary else Color.White

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .background(foreground.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(180.dp)
                .background(foreground.copy(alpha = 0.14f), CircleShape),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Space.screenEdge),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                if (!last) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = foreground.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                OnboardingMark(color = markColor, size = 64.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(slide.eyebrowRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(slide.titleRes),
                    fontFamily = SpaceGrotesk,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp,
                    lineHeight = 46.sp,
                    color = foreground,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(slide.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground.copy(alpha = 0.85f),
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
            ) {
                Dots(count = SLIDES.size, active = slideIndex, color = foreground)

                val ctaContainer = if (last) Color.White else if (slideIndex == 1) MaterialTheme.colorScheme.primary else Color.White
                val ctaContent = if (last) MaterialTheme.extendedColors.result else if (slideIndex == 1) Color.White else background

                Button(
                    onClick = if (last) onGetStarted else onNext,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ctaContainer,
                        contentColor = ctaContent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Text(
                        text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, active: Int, color: Color) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (index == active) 22.dp else 7.dp,
                animationSpec = tween(220),
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = if (index == active) 1f else 0.35f)),
            )
        }
    }
}

/**
 * The app's icon mark (design doc "Assets"): a rounded square "sliced open" at the top, revealing
 * a smaller solid circle with a light center dot. New to this redesign — a placeholder pending
 * sign-off, not a final logo (per the design handoff), so it is drawn inline here rather than
 * wired up as the launcher icon.
 *
 * Approximated with rects/circles only (no bezier `Path.arcTo`), since the mark is explicitly a
 * placeholder and a pixel-exact reproduction of the mockup's SVG path is not worth the
 * geometry-debugging cost here — the silhouette (square tile, a band cut across its top revealing
 * the circle beneath, circle with a light center dot) is what needs to read, not the exact curve.
 */
@Composable
private fun OnboardingMark(color: Color, size: androidx.compose.ui.unit.Dp) {
    val centerDot = MaterialTheme.colorScheme.background
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width / 64f
        val tileTopLeft = Offset(4f * s, 4f * s)
        val tileSize = androidx.compose.ui.geometry.Size(56f * s, 56f * s)
        val tileCorner = androidx.compose.ui.geometry.CornerRadius(18f * s, 18f * s)

        // The tile itself, at low opacity.
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = tileTopLeft,
            size = tileSize,
            cornerRadius = tileCorner,
        )

        // The "sliced open" band across the top third, clipped to the tile's rounded corners so
        // it reads as part of the same shape rather than a separate rectangle overlapping it.
        val tileRoundRect = androidx.compose.ui.geometry.RoundRect(
            rect = androidx.compose.ui.geometry.Rect(offset = tileTopLeft, size = tileSize),
            cornerRadius = tileCorner,
        )
        clipPath(Path().apply { addRoundRect(tileRoundRect) }) {
            drawRect(color = color, topLeft = tileTopLeft, size = androidx.compose.ui.geometry.Size(56f * s, 22f * s))
        }

        // The core circle with a light center dot.
        drawCircle(color = color, radius = 13f * s, center = Offset(32f * s, 38f * s))
        drawCircle(color = centerDot, radius = 6f * s, center = Offset(32f * s, 38f * s))
    }
}
```

Add imports: `androidx.compose.ui.graphics.drawscope.clipPath`, `androidx.compose.ui.graphics.Path`,
`androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.sp` (alongside the imports
already listed at the top of Step 3).

- [ ] **Step 4: Add the ONBOARDING route to CarbScanNavHost**

```kotlin
private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    // ... existing routes unchanged
}
```

```kotlin
@Composable
fun CarbScanNavHost(
    container: AppContainer,
    settings: AppSettings,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (settings.hasSeenOnboarding) Routes.HOME else Routes.ONBOARDING,
    ) {

        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(
                factory = factory { OnboardingViewModel(container.settingsRepository) },
            )
            val slideIndex by viewModel.slideIndex.collectAsStateWithLifecycle()

            OnboardingScreen(
                slideIndex = slideIndex,
                onNext = viewModel::next,
                onSkip = viewModel::skip,
                onGetStarted = {
                    viewModel.complete()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            // ... existing, unchanged
        }
        // ... rest of the NavHost unchanged
    }
}
```

Add import: `app.carbscan.ui.onboarding.OnboardingScreen`, `app.carbscan.ui.onboarding.OnboardingViewModel`.

Note: `startDestination` is evaluated once when `NavHost` is first composed. Since `settings` is
collected as state in `MainActivity` with `initialValue = AppSettings()` (which has
`hasSeenOnboarding = false`), the very first composition — before the real DataStore value loads —
will briefly resolve to `Routes.ONBOARDING` if the true value is `true`, then Compose won't
re-evaluate `startDestination` on a `settings` change. This is fine for this app's actual DataStore
read latency (near-instant, backed by a local file) but worth a one-line comment in the code
acknowledging it does not re-navigate mid-session if `hasSeenOnboarding` changes after first
composition — it doesn't need to, since `complete()` navigates explicitly.

- [ ] **Step 5: Verify MainActivity needs no change**

`MainActivity` already collects `settings` before calling `CarbScanNavHost(container, settings)` —
re-read the current file to confirm the `settings` value (with real `hasSeenOnboarding`) is
available by the time `CarbScanNavHost` first composes. Since `collectAsStateWithLifecycle` with an
`initialValue` synchronously provides that initial value on first composition and then recomposes
once the real Flow value arrives, and `startDestination` is fixed at `NavHost`'s first composition,
there is a real (if narrow) race: a returning user whose true `hasSeenOnboarding` is `true` could
flash onboarding for one frame if DataStore hasn't emitted yet. Accept this — it matches how
`settings.theme` already behaves for the same reason (the app already has this class of startup
race for every setting), and fixing it would require restructuring `MainActivity`'s composition
gating, which is out of scope for a visual redesign. No code change needed in `MainActivity.kt`.

- [ ] **Step 6: Build and run unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all 239 tests passing (Onboarding has no unit-testable logic beyond
what Task 9 already covers via `SettingsRepositoryTest`; `OnboardingViewModel` is simple enough
that its behavior is exercised by the emulator walkthrough in Task 11 — add a lightweight unit test
for it here since it is pure state, not Android-dependent):

Add `app/src/test/kotlin/app/carbscan/ui/onboarding/OnboardingViewModelTest.kt`:

```kotlin
package app.carbscan.ui.onboarding

import app.carbscan.data.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class OnboardingViewModelTest {

    @Test
    fun `next advances slide index up to the last slide`() {
        val viewModel = OnboardingViewModel(mock())
        assertEquals(0, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(1, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(2, viewModel.slideIndex.value)
        viewModel.next()
        assertEquals(2, viewModel.slideIndex.value)
    }

    @Test
    fun `skip jumps straight to the last slide`() {
        val viewModel = OnboardingViewModel(mock())
        viewModel.skip()
        assertEquals(2, viewModel.slideIndex.value)
    }
}
```

Check whether `org.mockito.kotlin` (`mockito-kotlin`) is already a test dependency in
`app/build.gradle.kts` / `gradle/libs.versions.toml` before using `mock()` — if it is not present,
replace `mock()` with a minimal fake:

```kotlin
private fun fakeRepository(): SettingsRepository =
    SettingsRepository.forTesting(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = {
                java.io.File.createTempFile("onboarding-vm-test", "").apply { delete() }
            },
        ),
    )
```

and construct with `OnboardingViewModel(fakeRepository())` instead of `mock()`, dropping the
mockito-kotlin import entirely if it's not already a dependency (avoids adding a new test
dependency for two tests that don't need mocking — `next()`/`skip()` never call the repository).

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, 241 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/carbscan/ui/onboarding/ app/src/main/kotlin/app/carbscan/ui/CarbScanNavHost.kt app/src/main/res/values/strings.xml app/src/test/kotlin/app/carbscan/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "Add Onboarding screen, ViewModel, and wire into first-launch navigation"
```

---

### Task 11: Update window/splash background colors, build, and full verification pass

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`

**Interfaces:**
- Consumes: nothing new — this task closes out the visual pass and verifies the whole app.

- [ ] **Step 1: Update the light window/splash background to cream**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#FFF6EE</color>
    <color name="splash_background">#FFF6EE</color>
    <color name="ic_launcher_background">#0B6E5F</color>
</resources>
```

(`ic_launcher_background` is left unchanged — the launcher icon itself is explicitly out of scope
per the design doc, which calls the new icon mark "pending sign-off, not a final logo".)

- [ ] **Step 2: Check/update values-night/colors.xml to the new dark background**

Read the current file first, then set `window_background`/`splash_background` to match the new
`Night` token from Task 2 (`#15140F`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="window_background">#15140F</color>
    <color name="splash_background">#15140F</color>
</resources>
```

- [ ] **Step 3: Full unit test suite**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, 241 tests passing.

- [ ] **Step 4: Lint**

```powershell
.\gradlew.bat :app:lintDebug
```
Expected: BUILD SUCCESSFUL, no new warnings/errors introduced (the project's CLAUDE.md records
lint as clean prior to this work — any new finding must be fixed, not suppressed).

- [ ] **Step 5: Build the debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. Output at `app\build\outputs\apk\debug\app-debug.apk`.

- [ ] **Step 6: Start the emulator and install**

```powershell
C:\atools\sdk\emulator\emulator.exe -avd carbscan -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
```

Wait ~90s for boot, then:

```powershell
C:\atools\sdk\platform-tools\adb.exe devices
C:\atools\sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\debug\app-debug.apk"
```

- [ ] **Step 7: Run the instrumented test suite**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
Expected: BUILD SUCCESSFUL, all 95 instrumented tests passing. Pay particular attention to
`ProductScreenTest` (uses `PRODUCT_RESULT_TAG`, `PRODUCT_HERO_TAG`, gallery tags) and any test
asserting on text color, node bounds, or hit-testing near the new decorative circles — a decorative
`Box` added behind content must not intercept clicks meant for the content in front of it (Compose
z-order by declaration order already guarantees this since the circle is declared first/behind, but
verify by watching for `performClick()` failures specifically in these files: `ProductScreenTest`,
and any `HomeScreenTest`/`MealScreenTest`/`ManualEntryScreenTest`/`SettingsScreenTest` if they
exist — check via `Glob app/src/androidTest/kotlin/**/*.kt` first).

Note: `connectedAndroidTest` uninstalls the app afterward — reinstall before the manual walkthrough
in Step 8.

- [ ] **Step 8: Reinstall and manually walk through all 7 screens**

```powershell
C:\atools\sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Launch the app (fresh install → should land on Onboarding). Walk through:
1. Onboarding — all 3 slides, Skip, Next, Get started.
2. Home — empty state, then with a scanned/manual product in Recent.
3. Scanner — frame/colors (camera permission may block full verification in the emulator; at
   minimum confirm the permission-rationale screen and manual-entry fallback render correctly).
4. Product/Result — the calculator with a real product: hero image, portion input, result panel,
   Add to meal.
5. Manual Entry — form styling, disabled/enabled Save button.
6. Settings — theme pills, haptics switch, safety card.
7. Meal — add 2+ items via Product screen, open Meal, check total panel and item rows.

Repeat with Settings → Appearance → Dark, confirming the extrapolated dark palette from Task 2 is
legible and the result-red stays visually distinct from body text in both modes.

- [ ] **Step 9: Copy the debug APK to the Desktop per the project's existing workflow**

```powershell
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "$env:USERPROFILE\Desktop\CarbScan-debug.apk" -Force
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml
git commit -m "Update window/splash background colors to Just the Carbs palette"
```

---

## Self-Review Notes

- **Spec coverage:** every section of the spec (rebrand, design tokens, component strategy, all 6
  screens, onboarding, "what stays unchanged", testing) maps to a task above. The spec's "open
  items for the owner" section is informational only and needs no task.
- **Placeholder scan:** several code samples originally contained non-compiling filler that has
  since been rewritten in place: Task 10 Step 3's onboarding title `Text` (was a
  `NumberType.portion.copy(fontSize = TextUnit.Unspecified)` chained with a stray `fontSize =
  44.sp()` call — now explicit `Text` parameters), the `OnboardingMark` glyph (was a `Path.arcTo`
  approach with an invalid inline `RoundRect` expression — now a rect/circle-only `Canvas`
  approximation, with a documented reason for not chasing bezier-exact fidelity on an admitted
  placeholder asset), and `SettingsRepository`'s two illegal `companion object` blocks in Task 9
  (merged into one). The `Space.sheetTopRadius / 2` button-radius derivation in the onboarding CTA
  button was also replaced with a direct `Space.buttonRadius` reference — the division happened to
  equal the same value but was a fragile, non-obvious way to express it. No placeholders remain.
- **Type consistency:** `MaterialTheme.extendedColors.result` (Task 2) is the name used
  consistently in Tasks 4, 8, and 10. `SettingsRepository.setHasSeenOnboarding` (Task 9) matches
  the call site in `OnboardingViewModel.complete()` (Task 10). `Space.sheetTopRadius` (Task 2) is
  used consistently in Tasks 4 and 8. `AppSettings.hasSeenOnboarding` (Task 9) matches
  `settings.hasSeenOnboarding` read in `CarbScanNavHost` (Task 10).
