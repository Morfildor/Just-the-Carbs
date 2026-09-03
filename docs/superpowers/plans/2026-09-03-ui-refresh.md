# UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the app a colourful, modern visual language on every surface except the carbohydrate result — which keeps its exclusive red — and make the system bars opaque black instead of showing app content behind them.

**Architecture:** Six new accent colours are added to `ExtendedColors` and enforced by two JVM tests: one for WCAG contrast, one asserting every accent is *darker* than the result red so it recedes behind it. A `SystemBarScrim` composable draws opaque black bands behind the system bars (setting `statusBarColor` would be a no-op at `targetSdk 36`). A shared `JtcTopBar` replaces eleven hand-rolled title rows. Home surfaces the carbohydrate figure it already computes, and the scanner shows the frozen photo during recognition.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit4 (JVM), Compose UI Test (instrumented), Gradle.

**Spec:** `docs/superpowers/specs/2026-09-03-ui-refresh-design.md`

## Global Constraints

- **Nothing may change** the calculation, the schema, migrations, the §10 lookup priority, barcode detection, or any OCR recognition rule, threshold or parser rule.
- **The result keeps its monopoly.** `NumberType.result`, its auto-size floor of 36.sp, and `ExtendedColors.result` are untouched. No new colour may be used on or behind the result number.
- **Every accent must clear 4.5:1** on cream `#FFF6EE`, white `#FFFFFF` and surfaceContainerLow `#FDFBF8`.
- **Every accent must have relative luminance strictly below the result red** (light `#D42F2F` = 0.162, dark `#FF7A7A`).
- **Never colour alone for meaning** — every coloured signal carries a word or an icon too.
- **UI strings are English only.** No `values-nl/`. `androidResources { localeFilters += "en" }` stays.
- **Build commands** (PowerShell):
  ```
  $env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"
  .\gradlew.bat :app:testDebugUnitTest --rerun-tasks
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug
  ```
- **Count test results from JUnit XML**, never from the exit code — `app/build/test-results/testDebugUnitTest/*.xml`. A wrapper has reported exit 0 while Gradle printed BUILD FAILED.
- **Every task ends with a negative control**: revert the change, watch the intended test fail, restore byte-identically.
- **Baseline before starting:** JVM 1519/1519, lint exit 0 with 22 warnings and 0 errors.

---

## File Structure

**Create:**
- `app/src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt` — the six accents, light and dark, plus the per-destination accent mapping.
- `app/src/main/kotlin/app/justthecarbs/ui/theme/SystemBarScrim.kt` — opaque black bands behind the system bars.
- `app/src/main/kotlin/app/justthecarbs/ui/components/JtcTopBar.kt` — the one top bar.
- `app/src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt` — the decorative circle, extracted.
- `app/src/test/kotlin/app/justthecarbs/ui/theme/AccentRecessionTest.kt` — the luminance rule.

**Modify:**
- `Theme.kt` — extend `ExtendedColors`, wire `SystemBarScrim`.
- `MainActivity.kt:27,46-63` — bar configuration.
- `HomeScreen.kt` — carb figure, gradient action cards, accent spines, backdrop.
- `LabelScannerScreen.kt:1508-1552` — frozen photo + staged progress.
- `MealScreen.kt`, `SearchScreen.kt`, `SettingsScreen.kt`, `ManualEntryScreen.kt` — adopt `JtcTopBar`.
- `ContrastTest.kt` — cover the new accents.
- `strings.xml` — new labels.
- `DESIGN.md` — rewrite the stale colour rationale.

**Dropped from the spec:** Finding 5 (splitting `ProductScreen`). Reason recorded in the spec's own review: refactoring the most safety-critical screen in the same pass that changes its colours makes a regression hard to attribute. It is a stable, long-standing risk and does not need to share this pass.

---

### Task 1: The accent palette and its two safety tests

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt`
- Create: `app/src/test/kotlin/app/justthecarbs/ui/theme/AccentRecessionTest.kt`
- Modify: `app/src/test/kotlin/app/justthecarbs/ui/theme/ContrastTest.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt:84-106`

**Interfaces:**
- Produces: `AccentPalette` data class with `val teal, violet, green, magenta, indigo, amber: Color`; `LightAccents` / `DarkAccents`; `ExtendedColors.accents: AccentPalette`; `enum class Destination { HOME, PRODUCT, MEAL, SEARCH, SETTINGS, MANUAL, SCAN_BARCODE, SCAN_LABEL }` and `@Composable fun Destination.accent(): Color`.

- [ ] **Step 1: Write the failing luminance test**

Create `app/src/test/kotlin/app/justthecarbs/ui/theme/AccentRecessionTest.kt`:

```kotlin
package app.justthecarbs.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The rule that lets this app be colourful without endangering the one number that matters.
 *
 * `DESIGN.md` used to justify a near-monochrome palette on the grounds that "a colourful interface
 * competes with [the result]". The 2026-09-03 refresh spends colour freely on chrome instead, and
 * this test is what replaces that argument with a measurement: every accent is strictly darker than
 * the result red, so it recedes behind it rather than competing.
 *
 * Luminance, not saturation. A vivid mid-tone can be more eye-catching than a saturated dark one,
 * and WCAG relative luminance is the same quantity `ContrastTest` already reasons about — using two
 * different notions of "prominent" in one codebase is how the guarantee quietly stops meaning
 * anything.
 *
 * Without this the rule is a paragraph in a spec, which one plausible-looking hex edit can
 * contradict with nothing failing.
 */
class AccentRecessionTest {

    private val lightResult = 0xD42F2F
    private val darkResult = 0xFF7A7A

    private val lightAccents = mapOf(
        "teal" to 0x0F766E,
        "violet" to 0x6D28D9,
        "green" to 0x15803D,
        "magenta" to 0xBE185D,
        "indigo" to 0x4338CA,
        "amber" to 0x92400E,
    )

    // Not the obvious Tailwind-style 300/400 tints. Those were tried first and ALL SIX failed the
    // recession rule: #5EEAD4 teal measures 0.660 against the dark result red's 0.366, i.e. nearly
    // twice as bright as the number it must not out-shout. The values below are the brightest of
    // each hue that still recedes, found by search rather than by eye, and each still clears 4.5:1
    // on all three dark surfaces (night, raised, surfaceContainerLow).
    private val darkAccents = mapOf(
        "teal" to 0x43B1A6,
        "violet" to 0xA997D3,
        "green" to 0x45B56E,
        "magenta" to 0xE481B3,
        "indigo" to 0x9496FF,
        "amber" to 0xD49425,
    )

    private fun luminance(rgb: Int): Double {
        val channels = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            .map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    @Test
    fun `every light accent recedes behind the result red`() {
        val result = luminance(lightResult)
        lightAccents.forEach { (name, rgb) ->
            val accent = luminance(rgb)
            assertTrue(
                "$name luminance ${"%.3f".format(accent)} is not below the result red's " +
                    "${"%.3f".format(result)} — it would compete with the carbohydrate figure",
                accent < result,
            )
        }
    }

    @Test
    fun `dark scheme accents recede behind the dark result red`() {
        // The dark scheme inverts: the result red is a *bright* tint on near-black, so the accents
        // must be brighter still to be visible and yet must not out-shout it. Stated as the same
        // rule with the comparison reversed rather than as a second, differently-argued rule.
        val result = luminance(darkResult)
        darkAccents.forEach { (name, rgb) ->
            val accent = luminance(rgb)
            assertTrue(
                "$name (dark) luminance ${"%.3f".format(accent)} must stay below the dark result " +
                    "red's ${"%.3f".format(result)}",
                accent < result,
            )
        }
    }

    @Test
    fun `the accent literals here match the values AccentPalette actually ships`() {
        // Same guard as ContrastTest's `light tokens match the values Theme kt actually ships`:
        // without it these literals could drift from the palette and every assertion above would
        // keep passing while the real app regressed.
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt",
        ).readText()

        lightAccents.forEach { (name, expected) ->
            val token = "Light" + name.replaceFirstChar { it.uppercase() }
            val declared = Regex("""private val $token = Color\(0xFF([0-9A-Fa-f]{6})\)""")
                .find(source)?.groupValues?.get(1)?.toInt(16)
            assertTrue(
                "could not find `$token` in AccentPalette.kt — rename the mirror in this test too",
                declared != null,
            )
            assertTrue(
                "$token is ${declared?.toString(16)} in AccentPalette.kt but " +
                    "${expected.toString(16)} here",
                declared == expected,
            )
        }
    }
}
```

- [ ] **Step 2: Run it and verify it fails for the right reason**

```
.\gradlew.bat :app:testDebugUnitTest --tests "*AccentRecessionTest*" --rerun-tasks
```

Expected: `the accent literals here match…` FAILS with "could not find `LightTeal` in AccentPalette.kt" (the file does not exist yet, so `readText()` throws `FileNotFoundException` — that is also an acceptable failure). The two luminance tests PASS already, because they test literals. That asymmetry is expected and is why step 1 includes the drift test: it is the only one of the three that can fail before the source exists.

**A worked example of this test earning its place, before it has even run.** The first draft of this plan used Tailwind-style dark tints — `#5EEAD4`, `#C4B5FD`, `#86EFAC`, `#F9A8D4`, `#A5B4FC`, `#FCD34D`. They look like exactly the right colours for a dark scheme. Computed, **all six failed**: `#5EEAD4` has luminance 0.660 against the dark result red's 0.366, so every accent would have been nearly twice as bright as the carbohydrate figure. Nothing about the swatches suggested that. If the values had gone in unmeasured, `AccentRecessionTest` would have failed on its first run — which is the good outcome, and the reason the rule is a test rather than a paragraph.

- [ ] **Step 3: Create the palette**

Create `app/src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt`:

```kotlin
package app.justthecarbs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The six destination accents added by the 2026-09-03 UI refresh.
 *
 * ## Why these exact values
 *
 * Each was chosen against two measurements taken before it was written down, not by eye:
 *
 * 1. **Contrast** — every one clears WCAG AA normal text (4.5:1) on all three surfaces it is ever
 *    drawn on: cream, white and `surfaceContainerLow`. Pinned by `ContrastTest`.
 * 2. **Recession** — every one has relative luminance *below* the result red, so it recedes behind
 *    the carbohydrate figure instead of competing with it. Pinned by `AccentRecessionTest`.
 *
 * The second is the load-bearing one. This app's palette was previously near-monochrome *because*
 * "a colourful interface competes with [the result]" — the refresh spends colour freely on chrome
 * and keeps that guarantee by arithmetic instead. Do not add an accent here without running both
 * tests; a vivid mid-tone that "looks fine" is exactly what the luminance rule exists to catch.
 *
 * ## What these are NOT for
 *
 * Never the result number, and never a surface behind it. Never meaning on their own — every
 * coloured signal in this app carries a word or an icon as well.
 */
private val LightTeal = Color(0xFF0F766E)
private val LightViolet = Color(0xFF6D28D9)
private val LightGreen = Color(0xFF15803D)
private val LightMagenta = Color(0xFFBE185D)
private val LightIndigo = Color(0xFF4338CA)
private val LightAmber = Color(0xFF92400E)

// Dark-scheme accents are lifted, not the same hues on a dark ground: #0F766E teal on near-black
// fails contrast badly. But they are NOT the usual bright pastel tints either, and that is the
// non-obvious part.
//
// The first attempt used Tailwind-style 300/400 tints (#5EEAD4, #C4B5FD, #86EFAC, …) and **all six
// failed the recession rule** — #5EEAD4 measures luminance 0.660 against the dark result red's
// 0.366, so every accent would have been nearly twice as bright as the carbohydrate figure. That
// is the exact failure the rule exists to catch, and it was caught by computing the numbers rather
// than by looking at the swatches, which is the whole argument for having the test.
//
// These are the brightest colour of each hue that still recedes, found by searching the
// lightness/saturation space. Each clears 4.5:1 on night, surfaceVariant and surfaceContainerLow.
private val DarkTeal = Color(0xFF43B1A6)
private val DarkViolet = Color(0xFFA997D3)
private val DarkGreen = Color(0xFF45B56E)
private val DarkMagenta = Color(0xFFE481B3)
private val DarkIndigo = Color(0xFF9496FF)
private val DarkAmber = Color(0xFFD49425)

/** The accents, resolved for one scheme. Read through [MaterialTheme.extendedColors]. */
data class AccentPalette(
    val teal: Color,
    val violet: Color,
    val green: Color,
    val magenta: Color,
    val indigo: Color,
    val amber: Color,
)

val LightAccents = AccentPalette(
    teal = LightTeal,
    violet = LightViolet,
    green = LightGreen,
    magenta = LightMagenta,
    indigo = LightIndigo,
    amber = LightAmber,
)

val DarkAccents = AccentPalette(
    teal = DarkTeal,
    violet = DarkViolet,
    green = DarkGreen,
    magenta = DarkMagenta,
    indigo = DarkIndigo,
    amber = DarkAmber,
)

/**
 * Where the user is, for the purpose of picking an accent.
 *
 * An enum rather than each screen naming a colour directly, so the mapping lives in one place and a
 * screen cannot quietly pick a hue that belongs to a different destination. This is the same reason
 * `Space.primaryButtonHeight` was extracted from sixteen hand-typed `56.dp` literals.
 */
enum class Destination {
    HOME,
    PRODUCT,
    MEAL,
    SEARCH,
    SETTINGS,
    MANUAL,
    SCAN_BARCODE,
    SCAN_LABEL,
}

/**
 * This destination's accent.
 *
 * `SETTINGS` deliberately takes the neutral `onSurfaceVariant` rather than an accent: it is the one
 * destination that is pure configuration, and giving it a hue would imply it belongs to the
 * scan → portion → carbs workflow the other colours mark out.
 */
@Composable
fun Destination.accent(): Color {
    val accents = MaterialTheme.extendedColors.accents
    return when (this) {
        Destination.HOME -> MaterialTheme.colorScheme.primary
        Destination.PRODUCT -> MaterialTheme.colorScheme.primary
        Destination.MEAL -> accents.amber
        Destination.SEARCH -> accents.indigo
        Destination.SETTINGS -> MaterialTheme.colorScheme.onSurfaceVariant
        Destination.MANUAL -> accents.teal
        Destination.SCAN_BARCODE -> MaterialTheme.colorScheme.primary
        Destination.SCAN_LABEL -> accents.teal
    }
}
```

- [ ] **Step 4: Wire the palette into `ExtendedColors`**

In `Theme.kt`, add `val accents: AccentPalette` as the last property of the `ExtendedColors` data class, then set it in both instances:

```kotlin
private val LightExtendedColors = ExtendedColors(
    result = Red,
    orangeSoft = OrangeSoft,
    onOrangeSoft = Color(0xFF965D08),
    disabledButton = DisabledBlue,
    accents = LightAccents,
)

private val DarkExtendedColors = ExtendedColors(
    result = RedDark,
    orangeSoft = OrangeSoftDark,
    onOrangeSoft = OrangeDark,
    disabledButton = DisabledBlueDark,
    accents = DarkAccents,
)
```

- [ ] **Step 5: Extend `ContrastTest` to cover the accents**

Add to `ContrastTest`, after the existing `container foregrounds on their containers` test:

```kotlin
    @Test
    fun `every destination accent on every surface it is drawn on`() {
        // The refresh spends these on top-bar spines, section headings and icon roundels, all of
        // which land on one of these three grounds. Asserted as a loop over the whole palette so a
        // seventh accent cannot be added without either passing or failing here — the alternative,
        // one test per colour, is what lets a new one be added with no test at all.
        mapOf(
            "teal" to 0x0F766E,
            "violet" to 0x6D28D9,
            "green" to 0x15803D,
            "magenta" to 0xBE185D,
            "indigo" to 0x4338CA,
            "amber" to 0x92400E,
        ).forEach { (name, accent) ->
            assertContrast("$name/cream", accent, cream)
            assertContrast("$name/white", accent, white)
            assertContrast("$name/surfaceContainerLow", accent, surfaceContainerLow)
        }
    }

    @Test
    fun `every dark accent on every dark surface it is drawn on`() {
        // The dark scheme was previously untested here — ContrastTest covered light only, on the
        // stated grounds that Light is the fresh-install default. That reasoning holds for which
        // palette matters *most*, not for which one may be unreadable, and the accents ship in
        // both.
        val night = 0x15140F
        val nightRaised = 0x1E1D18
        val darkContainerLow = 0x19180F

        mapOf(
            "teal" to 0x43B1A6,
            "violet" to 0xA997D3,
            "green" to 0x45B56E,
            "magenta" to 0xE481B3,
            "indigo" to 0x9496FF,
            "amber" to 0xD49425,
        ).forEach { (name, accent) ->
            assertContrast("dark $name/night", accent, night)
            assertContrast("dark $name/nightRaised", accent, nightRaised)
            assertContrast("dark $name/containerLow", accent, darkContainerLow)
        }
    }

    @Test
    fun `white text on a filled accent card`() {
        // The Home action cards render white title and subtitle over an accent gradient. The
        // gradient's *darkest* stop is not the risk — its lightest is, so each end is checked.
        listOf(
            "blue→indigo start" to 0x1B6FBF,
            "blue→indigo end" to 0x4338CA,
            "teal→green start" to 0x0F766E,
            "teal→green end" to 0x15803D,
        ).forEach { (name, stop) ->
            assertContrast("white/$name", 0xFFFFFF, stop)
        }
    }
```

- [ ] **Step 6: Run all three tests and verify they pass**

```
.\gradlew.bat :app:testDebugUnitTest --tests "*AccentRecessionTest*" --tests "*ContrastTest*" --rerun-tasks
```

Expected: PASS. If `white/teal→green end` fails, `#15803D` at 5.02:1 is the tightest pair in the set — do not darken it below `#146B34` without re-running `AccentRecessionTest`, since darkening also moves luminance.

- [ ] **Step 7: Negative control**

Change `LightTeal` to `Color(0xFF7DD3C0)` (a light teal). Run both tests.
Expected: `every light accent recedes behind the result red` FAILS, and `every destination accent on every surface it is drawn on` FAILS on contrast. Restore `0xFF0F766E` exactly and re-run to confirm green.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt \
        app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt \
        app/src/test/kotlin/app/justthecarbs/ui/theme/AccentRecessionTest.kt \
        app/src/test/kotlin/app/justthecarbs/ui/theme/ContrastTest.kt
git commit -m "Add the destination accent palette, pinned by contrast and recession

Six accents, each measured against the three surfaces it is drawn on before
being written down. AccentRecessionTest is the load-bearing one: every accent
has luminance below the result red, so colour can be spent freely on chrome
while the carbohydrate figure keeps its prominence by arithmetic rather than by
the absence of colour elsewhere.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Opaque system bars

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ui/theme/SystemBarScrim.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/MainActivity.kt:27,46-63`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt:334-361`
- Modify: `app/src/main/res/values/themes.xml:8-9`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `@Composable fun SystemBarScrim()`, drawn inside `JustTheCarbsTheme` around `content`.

**Why not the obvious fix — read before starting.** Setting `android:statusBarColor` / `android:navigationBarColor` to black looks like the answer and is not. `targetSdk` is 36, and from Android 15 (API 35) the platform enforces edge-to-edge and **ignores both attributes** — they are deprecated no-ops. That fix would be correct on an older emulator image and wrong on a current phone, with nothing on screen to say which. The window therefore stays edge-to-edge and the app paints the bands itself.

- [ ] **Step 1: Create the scrim**

Create `app/src/main/kotlin/app/justthecarbs/ui/theme/SystemBarScrim.kt`:

```kotlin
package app.justthecarbs.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Opaque black bands behind the status and navigation bars.
 *
 * ## Why this is drawn rather than configured
 *
 * The owner's requirement is ordinary, non-immersive system bars: solid black, with no app content
 * showing through. The instinctive implementation — `android:statusBarColor` and
 * `android:navigationBarColor` in `themes.xml`, plus dropping `enableEdgeToEdge()` — **does not
 * work at this app's target.**
 *
 * `targetSdk` is 36. From Android 15 (API 35) the platform enforces edge-to-edge and treats both of
 * those attributes as deprecated no-ops. A fix resting on them would be correct on an older
 * emulator image and silently wrong on a current device, which is precisely the class of failure
 * this repo keeps recording ("a green suite and a broken screen"). So the window stays
 * edge-to-edge — which is not optional — and the app paints the bands itself.
 *
 * ## Why black rather than a theme colour
 *
 * Black in both themes, deliberately. The requirement is that the bars look like the system's, and
 * a cream status bar is exactly what made the app look full-screen. Because the ground is now a
 * known constant rather than arbitrary app content, [MainActivity] can pin the bar icons to light
 * unconditionally instead of inverting them with the theme.
 *
 * ## Ordering
 *
 * Drawn *over* the content, as the last child of the theme's root Box. Under it, a screen that
 * paints its own background — every screen here does — would cover the bands.
 */
@Composable
fun SystemBarScrim() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.Black),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(Color.Black),
        )
    }
}
```

- [ ] **Step 2: Draw it inside the theme**

In `Theme.kt`, replace the body of the inner `CompositionLocalProvider` so the scrim is drawn after `content`:

```kotlin
            CompositionLocalProvider(
                LocalContentColor provides (if (dark) DarkColors else LightColors).onBackground,
            ) {
                // The scrim is drawn here, once, rather than by each screen — eleven screens each
                // remembering to paint two bands is eleven chances to forget one, and the one that
                // forgets is invisible until someone looks at that screen on a device.
                androidx.compose.foundation.layout.Box {
                    content()
                    SystemBarScrim()
                }
            }
```

- [ ] **Step 3: Fix the bar appearance in `MainActivity`**

Replace lines 46–63 of `MainActivity.kt`:

```kotlin
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                // Light icons, unconditionally.
                //
                // This used to track the app theme (`!dark`), which was correct while the bars took
                // the app's own background — a cream status bar needs dark icons. The bars are now
                // painted opaque black by `SystemBarScrim` in both themes, so the ground behind
                // these icons is a known constant and inverting them with the theme would make them
                // black-on-black in Light mode.
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false

                // Re-enabled. The previous comment argued for `false` because the framework's
                // translucent scrim "reads as a grey band that matches neither theme" over this
                // app's flat backgrounds. That reasoning does not survive the change above: the
                // band is now deliberately black, so the framework enforcing contrast against it
                // agrees with the design instead of fighting it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
            }
```

Then delete the now-unused `val dark = resolveDarkTheme(...)` line **only if** nothing else in the file reads it — `JustTheCarbsTheme(themeChoice = settings.theme)` takes the choice, not the boolean, so it can go. Remove the `resolveDarkTheme` and `isSystemInDarkTheme` imports with it.

- [ ] **Step 4: Update `enableEdgeToEdge` to declare dark bars**

Replace line 27:

```kotlin
        // Explicitly dark on both bars. Left to resolve itself, `enableEdgeToEdge()` picks a
        // light or dark scrim from the *device* configuration, which on a light-themed phone
        // produced a pale scrim under bands this app now paints black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
```

Add `import androidx.activity.SystemBarStyle`.

- [ ] **Step 5: Make the XML honest**

In `themes.xml`, replace the two transparent bar colours:

```xml
        <!--
          Black rather than transparent, for the pre-Compose window only: this is what paints
          behind the very first frame. On API 35+ the platform ignores both attributes entirely
          (they are deprecated no-ops under enforced edge-to-edge) and `SystemBarScrim` is what
          actually holds — these remain correct and load-bearing on API 29–34.
        -->
        <item name="android:statusBarColor">@android:color/black</item>
        <item name="android:navigationBarColor">@android:color/black</item>
```

- [ ] **Step 6: Build and verify on a device or emulator**

```
.\gradlew.bat :app:assembleDebug
```

Then install and check, in this order:

1. **Light theme, gesture nav** — status bar black, icons visible, Home's cream background starts *below* it.
2. **Dark theme** — same.
3. **Three-button nav** — the navigation bar is black, not grey-scrimmed.
4. **The label scanner** — the camera preview must not lose framing area. This is the one screen where a black band could take real space: the scan region is measured from the overlay's laid-out bounds, so compare the corner brackets' position against the pre-change build.
5. **Rotate** — bands follow the insets.

Record which device and API level. **This step cannot be satisfied by a JVM test** — that is the point of it.

- [ ] **Step 7: Negative control**

Comment out the `SystemBarScrim()` call in `Theme.kt`, rebuild, and confirm on a device that the cream background returns behind the status bar. Restore and rebuild.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/theme/SystemBarScrim.kt \
        app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt \
        app/src/main/kotlin/app/justthecarbs/MainActivity.kt \
        app/src/main/res/values/themes.xml
git commit -m "Paint the system bars opaque black instead of showing content behind them

Drawn in Compose rather than configured. targetSdk is 36 and from Android 15 the
platform ignores statusBarColor/navigationBarColor entirely, so the obvious fix
would have been right on an old emulator image and wrong on a current phone.

Bar icons are now pinned light rather than tracking the theme: the ground behind
them is a known constant, so the old inversion would render them black-on-black
in Light mode.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: The shared top bar

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ui/components/JtcTopBar.kt`
- Modify: `MealScreen.kt:144-174`, `SearchScreen.kt`, `SettingsScreen.kt:~90-100`, `ManualEntryScreen.kt`

**Interfaces:**
- Consumes: `Destination`, `Destination.accent()` from Task 1.
- Produces: `@Composable fun JtcTopBar(title: String, destination: Destination, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {})`.

- [ ] **Step 1: Write the failing instrumented test**

Create `app/src/androidTest/kotlin/app/justthecarbs/ui/JtcTopBarTest.kt`:

```kotlin
package app.justthecarbs.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.ui.components.JtcTopBar
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JtcTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theTitleAndBackActionAreShown() {
        var backs = 0
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Meal", destination = Destination.MEAL, onBack = { backs++ })
            }
        }
        rule.onNodeWithText("Meal").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aBarWithNoBackActionRendersNoBackButton() {
        // Home has no back affordance. Passing null must remove the control, not disable it — a
        // dead button is worse than an absent one.
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Just the Carbs", destination = Destination.HOME)
            }
        }
        rule.onNodeWithText("Just the Carbs").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }

    @Test
    fun theTrailingSlotRenders() {
        rule.setContent {
            JustTheCarbsTheme {
                JtcTopBar(title = "Meal", destination = Destination.MEAL) {
                    Text("Clear")
                }
            }
        }
        rule.onNodeWithText("Clear").assertIsDisplayed()
    }
}
```

Add the imports `androidx.compose.ui.test.onAllNodesWithContentDescription` and `androidx.compose.ui.test.assertCountEquals`.

- [ ] **Step 2: Run and verify it fails**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*JtcTopBarTest*"
```

Expected: compilation failure — `JtcTopBar` is unresolved.

- [ ] **Step 3: Create the component**

Create `app/src/main/kotlin/app/justthecarbs/ui/components/JtcTopBar.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Destination
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.accent

/**
 * The one top bar.
 *
 * ## What this replaces
 *
 * Eleven screens each built their own title `Row`, and they had drifted: measured on 2026-09-03 the
 * title was `titleLarge` on Manual Entry and Settings, `titleMedium` on Meal and Search, and
 * `headlineMedium` on Home. One structural element, three sizes, with nothing holding them
 * together. This is the same failure `Space.primaryButtonHeight` was extracted to stop, where
 * `56.dp` had been typed by hand at sixteen call sites.
 *
 * ## The accent spine
 *
 * The 4dp coloured edge is how a destination gets identity now that titles are one size. It is
 * deliberately the *same* device as the accent spine on Home's recent cards, so the app reads as
 * one system rather than as a screen that happens to have a stripe.
 *
 * It never carries meaning on its own — the title says where you are, and the spine only
 * reinforces it. That is this app's existing accessibility rule, and it binds the new palette
 * exactly as it bound the old one.
 *
 * ## Home is not a caller
 *
 * Home keeps `headlineMedium` and its own row. Its title is a brand wordmark, not a navigation
 * label, and it is the one screen with no back affordance — folding it in here would mean a
 * `destination == HOME` special case inside a component whose whole purpose is that there are none.
 */
@Composable
fun JtcTopBar(
    title: String,
    destination: Destination,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val accent = destination.accent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = Space.m)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.minTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.product_back),
                    tint = accent,
                )
            }
        } else {
            Box(Modifier.width(Space.s))
        }

        Text(
            text = title,
            // One size, everywhere. This is the whole point of the component.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.xs)
                .semantics { heading() },
        )

        trailing()
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*JtcTopBarTest*"
```

Expected: 3/3 PASS. Count from `INSTRUMENTATION_STATUS_CODE`, not the exit code.

- [ ] **Step 5: Adopt it in Meal**

Replace the `Row` at `MealScreen.kt:144-174` with:

```kotlin
            JtcTopBar(
                title = stringResource(R.string.meal_title),
                destination = Destination.MEAL,
                onBack = onBack,
            ) {
                // Only offered when there is something to clear — a permanently-present destructive
                // action on an empty screen is noise the user has to learn to ignore.
                if (state.items.isNotEmpty()) {
                    TextButton(
                        onClick = { onShowClearConfirmation(true) },
                        modifier = Modifier.testTag(MEAL_CLEAR_TAG),
                    ) {
                        Text(stringResource(R.string.meal_clear))
                    }
                }
            }
```

Remove the now-unused `Icons.AutoMirrored.Filled.ArrowBack`, `IconButton` and `Icon` imports **only if** nothing else in the file uses them — `MealItemRow` has a remove button, so check before deleting.

- [ ] **Step 6: Adopt it in Search, Settings and Manual Entry**

Same substitution in each, with `Destination.SEARCH`, `Destination.SETTINGS`, `Destination.MANUAL`. Preserve each screen's existing trailing content and its `testTag`s exactly — a changed tag breaks tests for a reason unrelated to this work.

- [ ] **Step 7: Run the affected suites**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*MealScreenTest*" --tests "*SearchScreenTest*" --tests "*SettingsScreenTest*"
```

Expected: `MealScreenTest` 19/19, `SearchScreenTest` 29/29, `SettingsScreenTest` 4/4 — the counts recorded in CLAUDE.md. **Titles must be unchanged in content**, so any failure here is a real regression, not an expected update.

Per CLAUDE.md, run `MealScreenTest` and `SearchScreenTest` **three times each**: both have a documented history of passing on runs 1–3 and failing on 4–5 when the soft keyboard is involved.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/components/JtcTopBar.kt \
        app/src/androidTest/kotlin/app/justthecarbs/ui/JtcTopBarTest.kt \
        app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/search/SearchScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/settings/SettingsScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/manual/ManualEntryScreen.kt
git commit -m "Replace four hand-rolled top bars with one JtcTopBar

Measured before the change: titleLarge on Manual Entry and Settings,
titleMedium on Meal and Search. One structural element, three sizes, nothing
holding them together.

Home keeps its own row deliberately — its title is a brand wordmark, not a
navigation label, and it is the one screen with no back affordance.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Home surfaces the carbohydrate figure it already computes

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt:796-880` (`RecentCard`)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/kotlin/app/justthecarbs/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `Destination.accent()` from Task 1; the existing `rememberedCarbs(product, entry.lastUnit)`.
- Produces: no new public API.

- [ ] **Step 1: Add the strings**

In `strings.xml`, beside the existing `recent_summary`:

```xml
    <!--
      Recents previously rendered "150 g → 5.6 g" as one grey line. The carbohydrate figure was
      already computed and was styled as metadata, so the number a returning user came for was on
      screen and looked like a caption. It is now its own labelled column; this is the label.
    -->
    <string name="recent_carbs_label" tools:ignore="MissingTranslation">CARBS</string>
    <!-- The portion alone, now that the carbs figure has its own column. -->
    <string name="recent_portion_only" tools:ignore="MissingTranslation">%1$s</string>
```

- [ ] **Step 2: Write the failing test**

Add to `HomeScreenTest`:

```kotlin
    @Test
    fun aRecentProductShowsItsCarbFigureAsAFigure() {
        // The figure was always computed by `rememberedCarbs`; it was rendered as grey supporting
        // text inside "150 g → 5.6 g". This asserts it is present as its own labelled value, which
        // is what makes Home answer the question a returning user actually has.
        val product = Product(
            barcode = "1",
            name = "Griekse yoghurt",
            carbsPer100 = BigDecimal("3.7"),
            basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            lastPortion = BigDecimal("150"),
        )
        rule.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = listOf(RecentEntry(product = product, lastUnit = null)),
                    settings = AppSettings(),
                    onScan = {}, onManualEntry = {}, onOpenProduct = {},
                    onToggleFavorite = {}, onOpenSettings = {},
                )
            }
        }
        rule.onNodeWithText("5.6 g").assertIsDisplayed()
        rule.onNodeWithText("CARBS").assertIsDisplayed()
    }

    @Test
    fun theRecentCarbFigureFollowsTheConfiguredResultStyle() {
        // Recents and the calculator disagreeing about a number is a defect this repo has already
        // fixed once — the whole-gram/decimal split. Promoting the figure to a prominent position
        // makes any future disagreement more visible, not less, so it is pinned here.
        val product = Product(
            barcode = "1",
            name = "Griekse yoghurt",
            carbsPer100 = BigDecimal("3.7"),
            basis = NutritionBasis.PER_100_G,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            lastPortion = BigDecimal("150"),
        )
        rule.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = listOf(RecentEntry(product = product, lastUnit = null)),
                    settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT),
                    onScan = {}, onManualEntry = {}, onOpenProduct = {},
                    onToggleFavorite = {}, onOpenSettings = {},
                )
            }
        }
        rule.onNodeWithText("6 g").assertIsDisplayed()
    }
```

Add the `ResultStyle` import.

- [ ] **Step 3: Run and verify it fails**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*HomeScreenTest*"
```

Expected: both new tests FAIL — "5.6 g" is currently only inside the combined string "150 g → 5.6 g", and "CARBS" does not exist on Home.

- [ ] **Step 4: Rewrite `RecentCard`'s content**

Replace the `summary` computation and the `Row` body in `RecentCard`. Keep the existing `rememberedCarbs` call and both label branches exactly — only the *presentation* splits in two:

```kotlin
    val remembered = rememberedCarbs(product, entry.lastUnit)

    val portionLabel = remembered?.let {
        when (it) {
            is RememberedCarbs.Countable ->
                "${it.count.stripTrailingZeros().toPlainString()} " +
                    entry.lastUnit!!.unitLabel(
                        count = if (it.count.compareTo(BigDecimal.ONE) == 0) 1 else 2,
                    )
            is RememberedCarbs.Weight ->
                "${it.portion.stripTrailingZeros().toPlainString()} ${product.portionUnit}"
        }
    } ?: stringResource(R.string.recent_never_used)

    // Unchanged rule: follows the user's configured result style, so Recents and the calculator
    // cannot print different numbers for the same portion of the same product.
    val carbsLabel = remembered?.let {
        when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(it.exactCarbs)} g"
            ResultStyle.WHOLE_DOMINANT ->
                "${ResultFormatter.whole(ResultFormatter.wholeGrams(it.exactCarbs))} g"
        }
    }

    // One accent per card, derived from the barcode so a given product keeps the same colour
    // between launches. Decoration only — it encodes nothing, and the card is fully legible in
    // greyscale.
    val accents = MaterialTheme.extendedColors.accents
    val spine = listOf(
        accents.teal, accents.violet, accents.green,
        accents.magenta, accents.indigo, accents.amber,
    )[(product.barcode.hashCode().absoluteValue) % 6]
```

Then in the `Row`, add the accent spine as a left border and the carbs column before `FavoriteButton`:

```kotlin
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(Space.cardRadius),
            )
            .clickable(onClick = onClick)
            .padding(start = 0.dp, top = Space.s, bottom = Space.s, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = Space.xs)
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(spine),
        )
        Spacer(Modifier.width(Space.s))

        ProductThumbnail(product = product)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.s + Space.xs, end = Space.xs),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = portionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The answer, as an answer.
        //
        // The SAME red as the calculator, because it is the same fact — not a new hue. A second red
        // would make the app appear to have two kinds of carbohydrate figure.
        if (carbsLabel != null) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = Space.xs),
            ) {
                Text(
                    text = carbsLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.extendedColors.result,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.recent_carbs_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FavoriteButton(favorite = product.favorite, onToggle = onToggleFavorite)
    }
```

Add imports: `kotlin.math.absoluteValue`, `androidx.compose.foundation.layout.width`.

- [ ] **Step 5: Run and verify it passes**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*HomeScreenTest*"
```

Expected: 23/23 (21 existing + 2 new). If an existing test asserted the combined `"150 g → 5.6 g"` string, it must be updated — but check first that it is asserting *presentation* and not a calculation, because only the former legitimately changed.

- [ ] **Step 6: Negative control**

Change `color = MaterialTheme.extendedColors.result` to `onSurfaceVariant` and confirm the two new tests still pass (they assert text, not colour) — then **add** a colour assertion if that bothers you, or accept that colour is verified by eye here and say so. Restore.

More usefully: delete the `carbsLabel` block entirely and confirm `aRecentProductShowsItsCarbFigureAsAFigure` fails. Restore byte-identically.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/androidTest/kotlin/app/justthecarbs/ui/HomeScreenTest.kt
git commit -m "Home: show the carbohydrate figure as a figure

rememberedCarbs() already resolved the exact value for every recent product and
it was rendered as grey bodyMedium inside '150 g -> 5.6 g'. For a returning user
the number they came for was on screen, correct, and styled as metadata.

Same red as the calculator, because it is the same fact, and it still follows
settings.resultStyle so the two screens cannot disagree.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Gradient action cards and the accent backdrop

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt`
- Modify: `HomeScreen.kt:132-141` (backdrop), `665-775` (`HomeActionCard`)
- Modify: `MealScreen.kt:130-136`, `SearchScreen.kt`, `SettingsScreen.kt`, `ManualEntryScreen.kt`

**Interfaces:**
- Consumes: `Destination`, `Destination.accent()`.
- Produces: `@Composable fun AccentBackdrop(accent: Color, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Extract the backdrop**

Create `app/src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The decorative circle bleeding off the top-right corner.
 *
 * Extracted because it was on exactly two of eleven screens — Home in blue and Meal in orange — and
 * nowhere else, which read as an unfinished rollout rather than as a deliberate accent. Every
 * non-camera screen now gets one in its own destination colour.
 *
 * Purely decorative: it sits behind all content and never intercepts touches. The alpha is low
 * enough that text drawn over it keeps the contrast `ContrastTest` asserts against the flat
 * background — do not raise it without re-checking that, because the assertions are computed
 * against the *ground colour*, not against this.
 *
 * Camera screens deliberately do not use it. They are black by design and a coloured wash over a
 * live preview is noise on the one screen where the user is trying to see through the glass.
 */
@Composable
fun AccentBackdrop(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 80.dp, y = (-90).dp)
            .size(220.dp)
            .background(accent.copy(alpha = 0.14f), CircleShape),
    )
}
```

- [ ] **Step 2: Use it on Home**

Replace `HomeScreen.kt:135-141` with:

```kotlin
        AccentBackdrop(
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd),
        )
```

- [ ] **Step 3: Use it on Meal, Search, Settings and Manual Entry**

Meal replaces its existing hand-rolled circle with `AccentBackdrop(accent = Destination.MEAL.accent(), …)`. The other three add one inside their root `Box` — if a screen's root is a `Column`, wrap it in a `Box` first, with the backdrop as the first child so it sits behind.

- [ ] **Step 4: Make the action cards gradients**

In `HomeActionCard`, replace the flat `container` background. The signature gains a `gradient: List<Color>` parameter replacing `filled: Boolean`:

```kotlin
@Composable
private fun HomeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val description = stringResource(R.string.home_action_description, title, subtitle)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = gradient.last().copy(alpha = 0.30f),
                spotColor = gradient.last().copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = Space.m)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                // White at 0.82 rather than a theme colour: the ground here is an accent gradient,
                // not a surface, so onSurfaceVariant would be tuned for the wrong background. Both
                // gradient stops are checked against white in ContrastTest.
                color = Color.White.copy(alpha = 0.82f),
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
        )
    }
}
```

Update both call sites:

```kotlin
        item(key = "action_barcode") {
            HomeActionCard(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(R.string.home_scan_button),
                subtitle = stringResource(R.string.home_action_barcode_subtitle),
                gradient = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.extendedColors.accents.indigo,
                ),
                onClick = onScan,
                modifier = Modifier.testTag(HOME_SCAN_BARCODE_TAG),
            )
        }
        item(key = "action_label") {
            HomeActionCard(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.home_empty_scan_label),
                subtitle = stringResource(R.string.home_action_label_subtitle),
                gradient = listOf(
                    MaterialTheme.extendedColors.accents.teal,
                    MaterialTheme.extendedColors.accents.green,
                ),
                onClick = onScanLabel,
                modifier = Modifier.testTag(HOME_SCAN_LABEL_TAG),
            )
        }
```

Add `androidx.compose.ui.graphics.Brush` and `androidx.compose.ui.graphics.Color` imports.

- [ ] **Step 5: Run the suites**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*HomeScreenTest*" --tests "*MealScreenTest*"
.\gradlew.bat :app:lintDebug
```

Expected: `HomeScreenTest` 23/23, `MealScreenTest` 19/19, lint exit 0 with **22 warnings and 0 errors** — the recorded baseline. A new warning means a genuinely new advisory; investigate rather than accept it.

- [ ] **Step 6: Look at it on a device**

Both action cards, light and dark, at font scale 1.0 and 1.8. Specifically check the subtitle on the teal→green card: green is the lowest-contrast stop in the set at 5.02:1 against white, so it is the pair most likely to look marginal even though it passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt \
        app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/search/SearchScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/settings/SettingsScreen.kt \
        app/src/main/kotlin/app/justthecarbs/ui/manual/ManualEntryScreen.kt
git commit -m "Gradient action cards, and the accent backdrop on every non-camera screen

The decorative circle was on two of eleven screens, which read as an unfinished
rollout rather than a deliberate accent. Both scanners now carry gradients so
they read as a matched pair instead of one filled card and one outlined
afterthought.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: The scan wait shows the photo and real progress

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt:1497-1552`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: the existing `CaptureState { IDLE, CAPTURING, PROCESSING }` and `pendingCrop`.
- Produces: no new public API.

**The hard constraint.** Stages must map to **milestones the code already emits**. Never a timed animation. A fake progress bar on an operation measured at 1974 ms is worse than the spinner, because it implies knowledge the app does not have. If a milestone cannot be observed without changing the OCR path, **that stage is not shown** — reduce the list rather than inventing one.

- [ ] **Step 1: Establish which milestones actually exist**

Before writing any UI, read `LabelAnalyzer.analyzeStill` and `SelectedTableResolution.resolve` and list the points where state is genuinely observable from the composable. Write that list into the task's commit message.

If exactly one observable transition exists between shutter and result, then **the staged list is not built** — show the frozen photo with the existing single line instead, and record why in the code. That is a legitimate outcome of this step, not a failure of it.

- [ ] **Step 2: Add the strings**

```xml
    <!-- Shown over the frozen capture while recognition runs. -->
    <string name="ocr_stage_captured" tools:ignore="MissingTranslation">Photo captured</string>
    <string name="ocr_stage_recognised" tools:ignore="MissingTranslation">Text recognised</string>
    <string name="ocr_stage_parsing" tools:ignore="MissingTranslation">Finding the carbohydrate row</string>
```

- [ ] **Step 3: Show the frozen photo during `PROCESSING`**

The app already holds the captured bitmap in `pendingCrop`. In the `SearchingCard` branch, when `captureState == CaptureState.PROCESSING` and a bitmap exists, draw it behind the card instead of the live preview. This costs nothing — no new decode, no new allocation.

- [ ] **Step 4: Run the OCR corpus**

```
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*RealImageOcrTest*" --tests "*ProductionStillPipelineTest*" --tests "*SelectedTableProductionTest*" --tests "*EvidencePipelineProductionTest*"
```

**Read this before interpreting the result.** CLAUDE.md records this corpus as **39/39 on real hardware** and **17 failures on the `carbscan` emulator at clean HEAD** — the emulator's ML Kit reads the committed photographs far worse than a device does. On the emulator, compare the failing set *by name* against a clean-HEAD control run; identical sets mean no regression. Only a device run gives 39/39.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "Scanner: show the frozen capture while recognition runs

Device evidence records ML Kit at 323-1974 ms, two captures over 2 s, behind an
indeterminate spinner that cannot distinguish normal from stuck. The app already
holds the captured bitmap, so showing it costs nothing.

Stages map to milestones the pipeline already emits. A timed animation was
considered and rejected: a fake progress bar on a 1974 ms operation is worse
than a spinner, because it implies knowledge the app does not have.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Rewrite the stale colour rationale and record the pass

**Files:**
- Modify: `DESIGN.md:7-30`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Rewrite `DESIGN.md`'s colour strategy**

The current text says the palette is restrained **because** "a colourful interface competes with [the result]". That is no longer what the app does, and leaving it is worse than the drift it describes — the next reader would treat a measured guarantee as a style preference.

Replace with a section stating: colour is spent freely on chrome; the result is protected by the luminance rule; both properties are enforced by `ContrastTest` and `AccentRecessionTest`; and the accent table with its measured ratios.

- [ ] **Step 2: Add a CLAUDE.md section**

Follow the file's existing convention exactly: what changed, what was measured, negative controls, and an explicit **NOT verified** gate. The gate for this pass is:

- The opaque bars are verified on **one** device at **one** API level — name both. Behaviour on API 29–34 (where the XML attributes still apply) versus 35+ (where only the scrim does) has not been compared on real hardware.
- The OCR corpus was **not** re-run on a device, only on the emulator against a name-matched control.
- Colour appearance is verified by eye; only contrast and recession are verified by test.

- [ ] **Step 3: Full verification run**

```
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Expected: JVM ≥1519 + the new tests, 0 failures, 0 errors, **0 skipped** counted from JUnit XML. Lint exit 0, 0 errors. Record the APK size and SHA-256, per this repo's convention.

- [ ] **Step 4: Commit**

```bash
git add DESIGN.md CLAUDE.md
git commit -m "Record the UI refresh pass, and correct DESIGN.md's colour rationale

DESIGN.md justified a near-monochrome palette on the grounds that colour
competes with the result. After this pass that is no longer what the app does,
and leaving it would have the next reader treat a measured guarantee as a style
preference. The guarantee is now AccentRecessionTest.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:** Palette → Task 1. Opaque bars → Task 2. Finding 1 (Home figure) → Task 4. Finding 2 (top bar) → Task 3. Finding 3 (scan wait) → Task 6. Finding 4 (backdrop) → Task 5. Documentation debt → Task 7. Finding 5 is dropped, with the reason recorded in the File Structure section. **No gaps.**

**Placeholder scan:** Task 6 Step 1 deliberately makes discovery a step rather than asserting milestone names I have not verified — and states what to do if the discovery comes back negative. That is a real instruction, not a TODO. No other step defers work.

**Type consistency:** `AccentPalette` fields (`teal`, `violet`, `green`, `magenta`, `indigo`, `amber`) are used identically in Tasks 1, 4 and 5. `Destination` and `Destination.accent()` are defined in Task 1 and consumed in Tasks 3 and 5. `JtcTopBar`'s signature is defined in Task 3 Step 3 and matched by its call sites in Steps 5–6. `AccentBackdrop(accent, modifier)` is defined in Task 5 Step 1 and matched in Steps 2–3. `rememberedCarbs` / `RememberedCarbs.Countable|Weight` in Task 4 match the existing source.

**Ordering:** Task 1 must be first (Tasks 3, 4, 5 all consume it). Task 2 is independent and can run in parallel. Tasks 3–6 are independent of each other. Task 7 must be last.
