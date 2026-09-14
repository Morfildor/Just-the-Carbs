# 1.0.7 UI/UX Interaction Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing warm-editorial Compose UI feel more premium and purpose-built through
split number/unit result typography, a shared success-feedback grammar, contextual Home/scanner/
search behavior, and short navigation motion — with zero change to arithmetic, provenance,
verification, OCR safety, or scanner lifecycle.

**Architecture:** Two new small shared components (`ResultValue`, a success-pulse state holder)
absorb the currently-duplicated result rendering and the currently-ad-hoc copy-confirmation pattern.
Every consuming screen (Product, Meal, Search, Home, both scanners, Navigation) is then updated in
its own task, each independently testable and independently committable. Scanner recognition,
OCR/ambiguity handling, and calculation logic are never touched — only the composables that already
sit around existing state (`CaptureState`, `addingToMeal`, `mealItems`, `verificationStatus`) gain
animated transitions or new-but-narrow conditional UI.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation-Compose 2.9.8, existing
`ui/theme/Theme.kt` (`Motion`, `NumberType`, `Space`, `extendedColors`) design tokens.

**Spec:** `docs/superpowers/specs/2026-09-14-interaction-polish-design.md`

## Global Constraints

- No change to arithmetic, rounding, `ResultFormatter` output, `NutritionBasis`, provenance,
  `VerificationStatus` semantics, OCR ambiguity/conflict/recovery logic, scale handling,
  `PhysicalObservationId`, session immutability, Room schema, `MealStore`/`PortionUsageStore`
  contracts, Search request budget/debounce/cancellation, OFF lookup behavior, scanner lifecycle,
  camera permissions, navigation destinations, or versioning/release state
  (`1.0.7` / `versionCode 8`, branch `main`; `release/1.0.6` is never touched).
- Motion vocabulary is fixed: `Motion.QUICK_MS = 120`, `Motion.STANDARD_MS = 220`,
  `Motion.COPIED_STATE_MS = 2500L` (`ui/theme/Theme.kt`). No new timing constants unless a task
  explicitly justifies one against this same object.
- No decorative entrance animation, no spring/bounce, no animation gating a result's appearance, no
  snackbar/toast for ordinary success (existing error text and existing Toast-on-copy are the only
  standing exceptions), no screenshot/pixel-golden tests for animation frames.
- 48dp minimum touch targets (`Space.minTouchTarget`); large fonts must not clip; color is never the
  only state signal; every new interactive element needs a real accessible label/description.
- Result-red (`extendedColors.result`) is reserved for **confirmed** calculated results only — never
  for search-row previews, pending scanner proposals, or the empty-meal placeholder.
- Item 12 (direct portion-drag manipulation) is **out of scope**. Do not implement it in this plan.
- Follow existing code conventions in each touched file (KDoc explaining *why*, not *what*; existing
  naming patterns like `MEAL_ADD_TAG`, `PRODUCT_RESULT_TAG`; existing test-tag constant style).
- Baseline verification per task, from repo root in PowerShell:
  ```powershell
  $env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
  $env:ANDROID_HOME = 'C:\atools\sdk'
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  ```
  Full `:app:assembleDebug` / `:app:assembleDebugAndroidTest` and targeted instrumented runs happen
  at the end of each task that touches Compose UI (see each task's Step list) and again as a whole
  suite in the final task.

---

### Task 1: `ResultValue` shared composable + `NumberType.resultUnit` token

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt` (add `NumberType.resultUnit`)
- Create: `app/src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt`
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/components/ResultValueTest.kt`

**Interfaces:**
- Consumes: `NumberType.result` (72sp Bold Space Grotesk), `NumberType.resultAutoSize`
  (`TextAutoSize.StepBased(36sp..72sp)`), `Motion.QUICK_MS` — all existing, from
  `ui/theme/Theme.kt`.
- Produces (for Tasks 2 and 3 to consume):
  ```kotlin
  // app/src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt
  const val RESULT_VALUE_NUMERAL_TAG_SUFFIX = "_numeral"

  @Composable
  fun ResultValue(
      dominant: String,
      unit: String,
      accessibleLabel: String,
      modifier: Modifier = Modifier,
      color: Color = MaterialTheme.extendedColors.result,
      testTag: String? = null,
  )
  ```
  `dominant` is the numeral only (e.g. `"31.3"`), `unit` is e.g. `"g"` — callers stop concatenating
  them into one string. `accessibleLabel` is the full spoken form the caller already builds today
  (e.g. `"31.3 grams"`) and is applied via `semantics(mergeDescendants = true) { contentDescription
  = accessibleLabel }` on the outer `Row` so TalkBack reads one node, not two. `testTag`, when
  non-null, is applied to the outer `Row` (callers keep using their existing tag constants,
  e.g. `PRODUCT_RESULT_TAG`, `MEAL_TOTAL_TAG` — unchanged from today).

**Step 1: Add the `resultUnit` token to `NumberType`**

Read `app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt` around line 323-372 first (the
`NumberType` object). Add a new token immediately after `result` (after line 332, before
`resultAutoSize`):

```kotlin
    /**
     * The unit beside the dominant result, e.g. the `g` in `31.2 g`.
     *
     * Deliberately smaller than [result] rather than a plain trailing string in the same style —
     * the number is the answer; the unit is a label on it. Same family and weight as [result] so
     * the pairing still reads as one object, not two different typefaces glued together.
     */
    val resultUnit = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )
```

**Step 2: Check whether Space Grotesk exposes tabular figures**

Run:
```powershell
Get-ChildItem app/src/main/res/font/ | Select-String -Pattern "grotesk" -SimpleMatch
```
Then inspect the actual font file(s) found (e.g. with a font-feature inspector, or by checking any
accompanying license/metadata `.txt`/`.json` shipped alongside the font files in that directory, or
the upstream Space Grotesk OpenType feature list if no local metadata exists — Space Grotesk is a
known open-source family and its public specimen documents which OpenType features it ships).
Record the finding as a one-line KDoc comment above `resultUnit` and above `result`:

- If `tnum` (tabular figures) is present: add `fontFeatureSettings = "tnum"` to **both** `result` and
  the new `resultUnit` `TextStyle`s in `NumberType`, with a comment: `// Tabular numerals: digits of
  a changing result don't visually reflow against each other while animating.`
- If not present or you cannot confirm it: leave both styles without `fontFeatureSettings`, and add
  a comment on `resultUnit`: `// Space Grotesk's bundled instance does not expose tabular figures
  (tnum) — verified 2026-09-14, not applied. Digits may shift width slightly during the
  AnimatedContent cross-fade; this is a font limitation, not a missed feature.`

Do not guess; this must be based on an actual check of the shipped font file. If the check is
genuinely inconclusive, take the "not present" branch (fail safe — no unverified claim in a comment).

**Step 3: Write `ResultValue`**

Create `app/src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/**
 * The single rendering of a calculated carbohydrate result: a dominant numeral with a smaller,
 * baseline-paired unit — `31.2` `g` rather than one string `"31.2 g"` in one typographic weight.
 *
 * A pure rendering component: callers already hold `ResultFormatter` output and pass the numeral
 * and unit as separate strings. No calculation, formatting or rounding happens here.
 *
 * Previously duplicated independently in `ResultPanel` (Product) and `MealTotalPanel` (Meal),
 * which is how the two drifted (only Product cross-faded its digits on change). This is now the
 * only place either lives.
 */
@Composable
fun ResultValue(
    dominant: String,
    unit: String,
    accessibleLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.extendedColors.result,
    testTag: String? = null,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            // One coherent node for TalkBack: without this the numeral and the unit are two
            // separately-focusable fragments a screen-reader user has to reassemble themselves.
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text = dominant,
            style = NumberType.result,
            color = color,
            maxLines = 1,
            // Shrinks rather than clips — see NumberType.resultAutoSize's own KDoc.
            autoSize = NumberType.resultAutoSize,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = unit,
            style = NumberType.resultUnit,
            color = color,
            maxLines = 1,
        )
    }
}
```

Note: the outer `Row`'s `semantics(mergeDescendants = true)` sets the `liveRegion` itself, so
callers must **not** also apply `liveRegion` to an inner `Text` (Product's existing `ResultPanel`
does this today on its single `Text` — Task 3 removes that inner semantics block since the outer one
now supersedes it).

**Step 4: Write the instrumented test**

Create `app/src/androidTest/kotlin/app/justthecarbs/ui/components/ResultValueTest.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test

class ResultValueTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theNumeralAndUnitAreExposedAsOneCoherentAccessibleResult() {
        composeRule.setContent {
            JustTheCarbsTheme {
                ResultValue(
                    dominant = "31.2",
                    unit = "g",
                    accessibleLabel = "31.2 grams",
                    testTag = "result_under_test",
                )
            }
        }

        // The merged node carries the full spoken label...
        composeRule.onNodeWithTag("result_under_test")
            .assertContentDescriptionEquals("31.2 grams")
        // ...and the two constituent strings are still findable as text (rendering, not hidden).
        composeRule.onNodeWithText("31.2").assertExists()
        composeRule.onNodeWithText("g").assertExists()
    }

    @Test
    fun aLongValueDoesNotClipAndTheUnitStaysBesideIt() {
        composeRule.setContent {
            JustTheCarbsTheme {
                ResultValue(
                    dominant = "1234.5",
                    unit = "g",
                    accessibleLabel = "1234.5 grams",
                    testTag = "long_result",
                )
            }
        }

        composeRule.onNodeWithTag("long_result").assertExists()
        composeRule.onNodeWithText("g").assertExists()
    }
}
```

**Step 5: Run the test**

```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:assembleDebugAndroidTest
```
Then, with an emulator/device attached (`adb devices`):
```powershell
adb shell am instrument -w -r -e class app.justthecarbs.ui.components.ResultValueTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: both cases pass. If no device is attached at this point in the session, note it and defer
running this specific instrumented class to the final verification task — do not skip writing it.

**Step 6: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```
Expected: no new failures (this task adds no `domain/` code).

**Step 7: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt app/src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt app/src/androidTest/kotlin/app/justthecarbs/ui/components/ResultValueTest.kt
git commit -m "Add shared ResultValue component with split number/unit typography"
```

---

### Task 2: `rememberSuccessPulse` shared success-feedback holder

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ui/components/SuccessPulse.kt`
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/components/SuccessPulseTest.kt`

**Interfaces:**
- Consumes: `Motion.COPIED_STATE_MS` (existing, `Theme.kt`).
- Produces (for Tasks 3–4 to consume):
  ```kotlin
  // app/src/main/kotlin/app/justthecarbs/ui/components/SuccessPulse.kt
  @Composable
  fun rememberSuccessPulse(trigger: Any?, holdMs: Long = Motion.COPIED_STATE_MS): Boolean
  ```
  Returns `true` for `holdMs` milliseconds after `trigger` changes to a non-null value, then `false`
  again. A new non-null `trigger` value while already showing restarts the hold from the new value
  (rapid re-triggers converge on the latest, same rule as the existing Copy check-mark's
  `copiedAt == copiedValue` comparison). Passing `null` for `trigger` clears the pulse immediately.

**Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/app/justthecarbs/ui/components/SuccessPulseTest.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SuccessPulseTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aNonNullTriggerShowsSuccessAndHoldsForTheGivenWindow() {
        var trigger by mutableStateOf<String?>(null)
        composeRule.setContent {
            val showing = rememberSuccessPulse(trigger, holdMs = 500L)
            Text(if (showing) "SHOWING" else "IDLE")
        }

        composeRule.onNodeWithText("IDLE").assertExists()
        composeRule.runOnIdle { trigger = "value-a" }
        composeRule.onNodeWithText("SHOWING").assertExists()

        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.onNodeWithText("IDLE").assertExists()
    }

    @Test
    fun aNewTriggerWhileShowingRestartsTheHoldFromTheLatestValue() {
        var trigger by mutableStateOf<String?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val showing = rememberSuccessPulse(trigger, holdMs = 500L)
            Text(if (showing) "SHOWING" else "IDLE")
        }

        composeRule.runOnIdle { trigger = "value-a" }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithText("SHOWING").assertExists()

        // Retrigger with a different value before the first hold expires.
        composeRule.runOnIdle { trigger = "value-b" }
        composeRule.mainClock.advanceTimeBy(400L)
        // 400ms after the SECOND trigger — still well inside its own 500ms hold.
        composeRule.onNodeWithText("SHOWING").assertExists()

        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.onNodeWithText("IDLE").assertExists()
    }
}
```

**Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```
Expected: compile failure — `rememberSuccessPulse` is unresolved. (If a device is attached and you
run the class, expect a build/reference error, not a runtime assertion failure — the function does
not exist yet.)

**Step 3: Write the implementation**

Create `app/src/main/kotlin/app/justthecarbs/ui/components/SuccessPulse.kt`:

```kotlin
package app.justthecarbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.justthecarbs.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Generalizes the "copied" check-mark pattern (originally inline in `ProductScreen`'s result copy
 * button) into one shared holder, so every success confirmation in the app — Add to meal, Copy,
 * Favorite, Save — holds its state the same way and for the same reasoning: long enough to survive
 * glancing away and back, short enough it cannot be mistaken for the resting state.
 *
 * Keyed on [trigger] rather than a plain boolean flag: a *new* trigger value (e.g. a newly copied
 * string, or a fresh add-to-meal attempt) restarts the hold from that value, so rapid repeats
 * converge on the latest action instead of the first one's timer silently finishing mid-flight.
 */
@Composable
fun rememberSuccessPulse(trigger: Any?, holdMs: Long = Motion.COPIED_STATE_MS): Boolean {
    var shownFor by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            shownFor = trigger
            delay(holdMs)
            if (shownFor == trigger) {
                shownFor = null
            }
        } else {
            shownFor = null
        }
    }
    return trigger != null && shownFor == trigger
}
```

**Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.components.SuccessPulseTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: both cases pass. If no device attached, defer running to final verification task.

**Step 5: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 6: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/components/SuccessPulse.kt app/src/androidTest/kotlin/app/justthecarbs/ui/components/SuccessPulseTest.kt
git commit -m "Add rememberSuccessPulse, generalizing the copy check-mark pattern"
```

---

### Task 3: Wire `ResultValue` into `ResultPanel` (Product) and add Verify affordance

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt` (`ResultPanel` around
  lines 1914-2005; `ProductSummary` around lines 908-942; `CalculatorBody` signature around
  line 563-590 and its call to `ProductSummary` around line 628)
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/product/ProductScreenTest.kt` (existing file
  — add cases, do not create a new file)

**Interfaces:**
- Consumes: `ResultValue` from Task 1 (`app.justthecarbs.ui.components.ResultValue`); existing
  `ResultFormatter.decimal/whole/wholeGrams`; existing `PRODUCT_RESULT_TAG` constant (do not rename
  — instrumented tests already reference it); existing `product.isRemoteRefreshable: Boolean`
  (`domain/Product.kt:130`, already means "not user-authored AND unverified" — the exact condition
  for offering Verify).
- Produces: no new public API — this task only changes composable internals and adds two new
  optional callback parameters threaded one level down.

**Step 1: Split `ResultPanel`'s `dominant` string into numeral + unit and adopt `ResultValue`**

Read `app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt` lines 1914-1953 first (the
existing `AnimatedContent`-wrapped `Text`). Replace the `dominant` construction and the
`AnimatedContent`/`Text` block:

Before (lines 1917-1953, abbreviated to the parts that change):
```kotlin
            val wholeGrams = ResultFormatter.wholeGrams(exact)
            val dominant = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(exact)} g"
                ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(wholeGrams)} g"
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = dominant,
                    transitionSpec = { ... },
                    label = "result",
                    modifier = Modifier.weight(1f),
                ) { value ->
                    Text(
                        text = value,
                        style = NumberType.result,
                        color = MaterialTheme.extendedColors.result,
                        maxLines = 1,
                        autoSize = NumberType.resultAutoSize,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PRODUCT_RESULT_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                Spacer(Modifier.width(Space.s))
                // ... copy button unchanged below
```

After:
```kotlin
            val wholeGrams = ResultFormatter.wholeGrams(exact)
            val dominantNumeral = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> ResultFormatter.decimal(exact)
                ResultStyle.WHOLE_DOMINANT -> ResultFormatter.whole(wholeGrams)
            }
            val resultUnit = stringResource(R.string.result_unit_grams)
            val accessibleResult = stringResource(R.string.result_accessible_grams, dominantNumeral)

            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Animated only on the digits changing, not on every recomposition, and only for
                // 120ms — long enough to notice the number moved, short enough that nobody waits.
                AnimatedContent(
                    targetState = dominantNumeral,
                    transitionSpec = {
                        (fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)))
                    },
                    label = "result",
                    modifier = Modifier.weight(1f),
                ) { value ->
                    ResultValue(
                        dominant = value,
                        unit = resultUnit,
                        accessibleLabel = accessibleResult,
                        testTag = PRODUCT_RESULT_TAG,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.width(Space.s))
                // ... copy button unchanged below
```

Remove the now-unused imports if this was their only use in the file (`LiveRegionMode`, `liveRegion`
semantics import) — check with a grep before removing:
```powershell
Select-String -Path app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt -Pattern "LiveRegionMode|liveRegion ="
```
If those symbols are used elsewhere in the same file, leave the imports; if this was the only use,
remove the now-unused import lines (do not leave orphaned imports — `AGENTS.md`'s surgical-changes
rule).

Add two new string resources to `app/src/main/res/values/strings.xml`, near the existing
`product_result_*` strings:
```xml
    <!-- The unit beside a split-typography result (see ResultValue), e.g. the "g" in "31.2 g". -->
    <string name="result_unit_grams">g</string>
    <!-- Spoken form for TalkBack: reads the numeral and unit as one phrase, e.g. "31.2 grams". -->
    <string name="result_accessible_grams">%1$s grams</string>
```

**Step 2: Run unit tests, lint, and a targeted instrumented run**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.product.ProductScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: existing `ProductScreenTest` cases referencing `PRODUCT_RESULT_TAG` and its text content
still pass unchanged (the tag is preserved; the rendered text for the numeral is unchanged; only the
unit is now a separate sibling node under the same merged-semantics parent — any existing assertion
that reads the *merged* accessible text, e.g. via `onNodeWithTag(PRODUCT_RESULT_TAG)`, should still
see `"31.2 grams"` equivalent content through the new `contentDescription`). If any existing
assertion specifically matched the old concatenated `"31.2 g"` node text directly (not via
content description), it will now fail because that exact string is split across two child `Text`
nodes — fix that assertion to check `assertContentDescriptionEquals` instead, per this task's own
new pattern in `ResultValueTest`. Record which (if any) existing assertions needed this adjustment.

**Step 3: Add the Verify affordance near `SourceBadge`**

Read `ProductSummary` (`ProductScreen.kt:908-942`) and `CalculatorBody`'s signature
(`ProductScreen.kt:563-590`) first.

Add two new optional parameters to `CalculatorBody` (after `onVerifyPortionUnit` at line 577, to
group the verify-related callbacks together):
```kotlin
    onVerify: () -> Unit = {},
    onVerifyByTyping: () -> Unit = {},
```
Thread them from `ProductScreen`'s top-level `when` branch (around line 312, the
`state.product != null -> CalculatorBody(...)` call) — that scope already has `onVerify` and
`onVerifyByTyping` as `ProductScreen`'s own top-level parameters (lines 160-161), so add:
```kotlin
                    onVerify = onVerify,
                    onVerifyByTyping = onVerifyByTyping,
```
to that `CalculatorBody(...)` call.

Find where `CalculatorBody` calls `ProductSummary` (around line 628) and pass the two callbacks
through; update `ProductSummary`'s signature to accept them:

```kotlin
@Composable
private fun ProductSummary(
    product: Product,
    compact: Boolean = false,
    onVerify: () -> Unit = {},
    onVerifyByTyping: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Text(
            text = stringResource(
                R.string.product_per_100,
                ResultFormatter.quantity(product.carbsPer100),
                product.portionUnit,
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        SourceBadge(product, showHint = !compact)
        // Discoverable verification, not just buried in the overflow menu. Only when it is
        // actually relevant: a value the app itself never checked against the package, and not
        // user-authored (isRemoteRefreshable is exactly "not user-authored AND unverified" —
        // the same condition the app already uses to decide whether a background refresh may
        // touch this product, so this reuses an existing fact rather than inventing a new one).
        //
        // Deliberately worded and styled as a neutral action, not a warning: SourceBadge's own
        // orange-soft badge already carries the "not verified" signal, so this must not repeat
        // or escalate it.
        if (!compact && product.isRemoteRefreshable) {
            Spacer(Modifier.height(Space.xs))
            TextButton(
                onClick = onVerify,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = Space.xs),
                modifier = Modifier.heightIn(min = Space.minTouchTarget).testTag(PRODUCT_VERIFY_INLINE_TAG),
            ) {
                Text(
                    text = stringResource(R.string.product_verify_inline),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
```

`onVerifyByTyping` is threaded through as a parameter for symmetry with the overflow menu's two
verify entries, but is not wired to a second visible control in this task — the brief asks for "a
restrained `Verify` action", singular, near the badge; the typed-entry path remains reachable only
via the overflow menu, unchanged. (If this asymmetry looks wrong once seen on screen, note it in the
task's own findings rather than silently adding a second inline button — that is a design judgment
call for the final report, not a silent scope change.)

Add the new test tag near the file's other tag constants (search for `const val PRODUCT_RESULT_TAG`
and add alongside it):
```kotlin
const val PRODUCT_VERIFY_INLINE_TAG = "product_verify_inline"
```

Add the new string resource to `strings.xml`, near `product_verify`:
```xml
    <!-- Inline verify action beside the per-100 figure, for an online value never checked against
         the package. Shorter than the overflow menu's "Verify against package" — this sits right
         next to the number it is about. -->
    <string name="product_verify_inline">Verify</string>
```

Update the necessary imports in `ProductScreen.kt` if `TextButton`, `PaddingValues`, or `heightIn`
are not already imported in that file (check first — most are almost certainly already present
given the file's size and existing button usage elsewhere).

**Step 4: Write instrumented tests**

Add to the existing `app/src/androidTest/kotlin/app/justthecarbs/ui/product/ProductScreenTest.kt`
(read its existing structure first — a representative existing test and its `setContent`/state-
building helper — to match conventions):

```kotlin
    @Test
    fun verifyIsOfferedForAnUnverifiedOnlineValue() {
        // Build/reuse the existing helper that renders ProductScreen with a ProductUiState whose
        // product has dataSource = OPEN_FOOD_FACTS, verificationStatus = UNVERIFIED (match the
        // existing test file's own state-construction helper/pattern here).
        // ...
        composeRule.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertExists()
    }

    @Test
    fun verifyIsAbsentForAVerifiedValue() {
        // Same, but verificationStatus = USER_VERIFIED.
        composeRule.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertDoesNotExist()
    }

    @Test
    fun verifyIsAbsentForAManualOrOcrValue() {
        // dataSource = MANUAL (or OCR), any verificationStatus — isRemoteRefreshable is false
        // because it is user-authored, so the condition must not fire regardless of verification
        // status.
        composeRule.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertDoesNotExist()
    }
```
Fill in the actual state-construction calls by reading the existing test file's own conventions for
building a `ProductUiState`/`Product` fixture with a given `dataSource`/`verificationStatus` — do not
invent a different fixture-building style than what the file already uses.

**Step 5: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.product.ProductScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: all `ProductScreenTest` cases, including the three new ones, pass.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt app/src/main/res/values/strings.xml app/src/androidTest/kotlin/app/justthecarbs/ui/product/ProductScreenTest.kt
git commit -m "Adopt ResultValue in the calculator and add an inline Verify action"
```

---

### Task 4: Add-to-meal success feedback

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/product/ProductViewModel.kt` (`ProductUiState`
  around lines 125-142; `addCurrentToMeal` around lines 805-845)
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/meal/MealComponents.kt` (`MealActions` around
  lines 51-88)
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt` (`MealActions` call
  site around lines 2064-2071)
- Test: `app/src/test/kotlin/app/justthecarbs/ui/product/ProductViewModelTest.kt` (existing file —
  add cases)
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/product/ProductScreenTest.kt` (existing
  file — add cases)

**Interfaces:**
- Consumes: `rememberSuccessPulse` from Task 2.
- Produces: `ProductUiState.lastMealAddSucceededAt: Long?` (nullable epoch-ish counter, see below),
  consumed by `ProductScreen`'s `MealActions` call site.

**Step 1: Write the failing ViewModel test**

Read the existing `ProductViewModelTest.kt`'s conventions for testing `addCurrentToMeal` first
(search for `addCurrentToMeal` or `addingToMeal` in that file to find the existing success/failure
test pair and match its fixture/fake-repository style). Add:

```kotlin
    @Test
    fun `a successful add-to-meal records a success signal the screen can consume`() = runTest {
        // Reuse this file's existing fixture/fake setup for a loaded product ready to add to the
        // meal (the same setup the existing "addCurrentToMeal succeeds" test already uses).
        val viewModel = /* existing fixture construction */

        assertEquals(null, viewModel.state.value.lastMealAddSucceeded)
        viewModel.addCurrentToMeal(portionDescription = "50 g")
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.lastMealAddSucceeded)
    }

    @Test
    fun `a failed add-to-meal does not record a success signal`() = runTest {
        // Reuse this file's existing fixture for a repository whose addMealItem throws.
        val viewModel = /* existing failing-repository fixture construction */

        viewModel.addCurrentToMeal(portionDescription = "50 g")
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.lastMealAddSucceeded)
        assertTrue(viewModel.state.value.mealAddFailed)
    }
```

Adjust the exact fixture-construction calls to match whatever this file's existing
success/failure `addCurrentToMeal` tests already do — do not invent a different fake-repository
shape than what is already established there.

**Step 2: Run to verify failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.product.ProductViewModelTest"
```
Expected: compile failure — `lastMealAddSucceeded` is unresolved on `ProductUiState`.

**Step 3: Add the state field and set it on success**

In `ProductViewModel.kt`, add a new field to `ProductUiState` immediately after `mealAddFailed`
(around line 142):
```kotlin
    /**
     * Non-null immediately after a meal-add write actually lands — never set before persistence
     * returns, per the same rule [mealAddFailed] follows for the failure side. A plain `Long`
     * (`System.currentTimeMillis()` or a simple counter) rather than a `Boolean`, because a
     * *second* add straight after the first must be its own distinct signal — the success feedback
     * (see `rememberSuccessPulse`) restarts on a new value, not on a value going from true to true.
     */
    val lastMealAddSucceeded: Long? = null,
```

In `addCurrentToMeal` (around line 837, the success branch), change:
```kotlin
                _state.update { it.copy(addingToMeal = false) }
```
to:
```kotlin
                _state.update {
                    it.copy(addingToMeal = false, lastMealAddSucceeded = System.currentTimeMillis())
                }
```

**Step 4: Run the ViewModel test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.product.ProductViewModelTest"
```
Expected: both new cases pass; no existing `ProductViewModelTest` case regresses (search the file
for any assertion enumerating `ProductUiState`'s full field set — if one exists using positional
`copy()`/`equals()` comparison rather than named-field assertions, it may need updating for the new
field; check before assuming none exist).

**Step 5: Wire the success pulse into `MealActions`**

Read `MealComponents.kt:51-88` (`MealActions`) first. Add a new optional parameter and swap the
"Add to meal" button's label while the pulse is showing:

```kotlin
@Composable
fun MealActions(
    onAdd: () -> Unit,
    onAddAndScanNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * A distinct value each time a meal-add write has just succeeded (see
     * `ProductUiState.lastMealAddSucceeded`). Null means no recent success to show. Drives a brief
     * "✓ Added" label on *Add to meal* via `rememberSuccessPulse` — the same confirmation grammar
     * as the result's copy button, generalized rather than reinvented.
     */
    justAdded: Any? = null,
) {
    val showAdded = rememberSuccessPulse(justAdded)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        OutlinedButton(
            onClick = onAdd,
            enabled = enabled,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).testTag(MEAL_ADD_TAG),
        ) {
            if (showAdded) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.xs))
            }
            Text(
                if (showAdded) stringResource(R.string.meal_added) else stringResource(R.string.meal_add),
            )
        }
        Button(
            onClick = onAddAndScanNext,
            enabled = enabled,
            shape = RoundedCornerShape(Space.buttonRadius),
            modifier = Modifier.weight(1f).testTag(MEAL_ADD_AND_SCAN_TAG),
        ) {
            Text(stringResource(R.string.meal_add_and_scan))
        }
    }
}
```

Add the new imports this needs (`Icon`, `Icons.Filled.Check`, `size`, `androidx.compose.ui.Modifier`
size extension — check which are already imported in `MealComponents.kt` before re-adding) and the
import for `app.justthecarbs.ui.components.rememberSuccessPulse`.

Add the new string resource near `meal_add` in `strings.xml`:
```xml
    <!-- Brief success label on *Add to meal*, shown for a few seconds right after a successful
         add — see rememberSuccessPulse. -->
    <string name="meal_added">Added</string>
```
(The "✓" is the `Check` icon, not baked into the string — so TalkBack, which does not read the icon
twice, announces "Added" cleanly via the button's own text.)

**Step 6: Wire the ViewModel state into the call site**

In `ProductScreen.kt` around line 2067, update the `MealActions(...)` call:
```kotlin
            MealActions(
                onAdd = onAddToMeal,
                onAddAndScanNext = onAddToMealAndScanNext,
                enabled = !state.addingToMeal,
                justAdded = state.lastMealAddSucceeded,
            )
```
(`state` here is the `ProductUiState` already in scope inside `ResultPanel` — confirm by reading the
surrounding function signature; if `ResultPanel` does not currently receive the full `state` object
but only destructured fields, thread `lastMealAddSucceeded` through as its own parameter instead,
matching whatever pattern `addingToMeal`/`mealAddFailed` already use at that call site.)

**Step 7: Write the instrumented test**

Add to `ProductScreenTest.kt`:
```kotlin
    @Test
    fun addToMealShowsABriefSuccessLabelAfterAConfirmedWrite() {
        // Reuse this file's existing fixture for a loaded, calculated product with a fake
        // repository whose addMealItem succeeds.
        // ... render ProductScreen, tap MEAL_ADD_TAG ...
        composeRule.waitForIdle()
        composeRule.onNodeWithText(/* R.string.meal_added resolved, e.g. via composeRule's own
            string-resource helper or a hardcoded "Added" if that's this file's existing
            convention */).assertExists()
    }

    @Test
    fun addToMealNeverShowsSuccessBeforeTheWriteCompletes() {
        // Reuse a fixture whose repository's addMealItem suspends indefinitely (or use a
        // CompletableDeferred-backed fake, matching whatever async-control pattern this file's
        // existing addToMeal-in-flight test already uses for `addingToMeal`).
        // ... tap MEAL_ADD_TAG, then immediately assert the success label is NOT shown yet ...
        composeRule.onNodeWithText(/* "Added" */).assertDoesNotExist()
    }
```
Fill in the exact fixture/state-construction calls by reading this file's existing conventions —
in particular its existing test for `addingToMeal` guarding a second tap, which already needs the
same kind of controllable-completion fake this second new test needs.

**Step 8: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.product.ProductScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```

**Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/product/ProductViewModel.kt app/src/main/kotlin/app/justthecarbs/ui/meal/MealComponents.kt app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/app/justthecarbs/ui/product/ProductViewModelTest.kt app/src/androidTest/kotlin/app/justthecarbs/ui/product/ProductScreenTest.kt
git commit -m "Add success confirmation to Add to meal"
```

---

### Task 5: Empty meal placeholder + `ResultValue` in `MealTotalPanel`

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt` (`MealTotalPanel` around
  lines 282-359)
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/meal/MealScreenTest.kt` (existing file —
  add cases)

**Interfaces:**
- Consumes: `ResultValue` from Task 1.
- Produces: no new public API.

**Step 1: Split `MealTotalPanel`'s dominant string and branch on emptiness**

Read `MealScreen.kt:282-359` first. Replace the `dominant` construction and its rendering:

Before (lines 309-326):
```kotlin
        val dominant = when (settings.resultStyle) {
            ResultStyle.DECIMAL_DOMINANT -> "${ResultFormatter.decimal(total?.exact ?: java.math.BigDecimal.ZERO)} g"
            ResultStyle.WHOLE_DOMINANT -> "${ResultFormatter.whole(total?.wholeGrams ?: 0)} g"
        }

        Text(
            text = dominant,
            style = NumberType.result,
            color = MaterialTheme.extendedColors.result,
            maxLines = 1,
            autoSize = NumberType.resultAutoSize,
            modifier = Modifier.testTag(MEAL_TOTAL_TAG),
        )
```

After:
```kotlin
        if (state.items.isEmpty()) {
            // No calculation has happened — this must not look like one. A dominant tomato-red
            // "0.0 g" here previously claimed a result the app had not computed; an em dash makes
            // no such claim.
            Text(
                text = stringResource(R.string.meal_total_empty_placeholder),
                style = NumberType.result,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(MEAL_TOTAL_TAG),
            )
        } else {
            val dominantNumeral = when (settings.resultStyle) {
                ResultStyle.DECIMAL_DOMINANT -> ResultFormatter.decimal(total?.exact ?: java.math.BigDecimal.ZERO)
                ResultStyle.WHOLE_DOMINANT -> ResultFormatter.whole(total?.wholeGrams ?: 0)
            }
            val resultUnit = stringResource(R.string.result_unit_grams)
            val accessibleResult = stringResource(R.string.result_accessible_grams, dominantNumeral)

            ResultValue(
                dominant = dominantNumeral,
                unit = resultUnit,
                accessibleLabel = accessibleResult,
                testTag = MEAL_TOTAL_TAG,
            )
        }
```

Note `total` can be null while `state.items` is non-empty only transiently (matching existing
`?: BigDecimal.ZERO`/`?: 0` fallbacks) — this task does not change that existing null-handling, only
which branch (`empty` vs `has items`) decides which visual treatment to use, keyed on
`state.items.isEmpty()` rather than on `total` being null, since `total` is a `CarbResult?` sourced
independently of the item list per the existing code (leave that relationship exactly as-is; do not
try to unify the two nullability sources — that would be exactly the "unrelated refactor" `AGENTS.md`
warns against).

Add the new string resource to `strings.xml`, near `meal_total_label`:
```xml
    <!-- Shown in place of a calculated total when the meal has no items yet — an em dash, never a
         claimed "0.0 g" result (see the empty-meal placeholder work, 2026-09-14 interaction pass). -->
    <string name="meal_total_empty_placeholder">—</string>
```

**Step 2: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 3: Write instrumented tests**

Add to `MealScreenTest.kt` (read its existing empty-state test, if one already exists near
`EmptyMeal`/`meal_empty_title`, to match its `MealUiState` construction pattern):

```kotlin
    @Test
    fun anEmptyMealShowsThePlaceholderNotACalculatedZero() {
        // Render MealScreen with a MealUiState whose items is emptyList().
        composeRule.onNodeWithTag(MEAL_TOTAL_TAG)
            .assert(hasText("—"))
    }

    @Test
    fun aPopulatedMealShowsARealResultViaResultValue() {
        // Render MealScreen with a MealUiState carrying at least one item and a non-null total.
        composeRule.onNodeWithTag(MEAL_TOTAL_TAG).assertExists()
        // Assert the merged content description carries the actual total (adjust the expected
        // string to whatever fixture total this test's MealUiState construction uses).
    }
```
Fill in the exact `MealUiState`/item fixtures using this test file's existing conventions (search
for an existing populated-meal test to copy its state-construction shape).

**Step 4: Run tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.meal.MealScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Run **three times consecutively** per this repo's own standing warning in `CLAUDE.md` about a
soft-keyboard artifact affecting `MealScreenTest` specifically — if any run shows a different test
failing each time with "is not displayed", that is the documented pre-existing harness flake, not a
regression from this task; only investigate further if the *same* new test fails on all three runs.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt app/src/main/res/values/strings.xml app/src/androidTest/kotlin/app/justthecarbs/ui/meal/MealScreenTest.kt
git commit -m "Give the empty meal a neutral placeholder instead of a calculated-looking zero"
```

---

### Task 6: `SearchResultRow` nutrition column refinement

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/components/Common.kt` (`SearchResultRow` around
  lines 293-353)
- Test: find and modify the existing test file covering `SearchResultRow` (likely
  `app/src/androidTest/kotlin/app/justthecarbs/ui/search/SearchScreenTest.kt` or a
  `CommonComponentsTest.kt` — locate with a grep for `SearchResultRow` under `androidTest` before
  writing new cases, and add to whichever file already covers it rather than creating a new one)

**Interfaces:**
- Consumes: nothing new (still `ProductSearchHit`, `search_carbs`/`search_no_carbs` string
  resources, unchanged data shape).
- Produces: no new public API — `SearchResultRow`'s own signature
  (`SearchResultRow(hit: ProductSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier)`) is
  unchanged, so `SearchScreen.kt` and Home's inline search results need no call-site changes.

**Step 1: Read the current implementation and the `search_carbs` string format**

Read `Common.kt:293-353` and confirm the exact current trailing-`Text` block and the
`search_carbs`/`search_no_carbs` string definitions in `strings.xml` before changing anything.

**Step 2: Extract the trailing column into its own sub-composable with a reserved width**

Replace the current trailing `Text(carbsText, ...)` with a new private composable
`SearchNutritionColumn`, splitting the existing single string into a value line and a basis line so
they can be styled at two different weights (mirroring `ResultValue`'s number/unit split
philosophy, but staying a separate, smaller, non-`ResultValue` composable per the design spec — this
is not a "confirmed result" and must not use `extendedColors.result`):

```kotlin
/**
 * The trailing carbohydrate summary in a search result row — a small value/basis pair in a
 * reserved-width column so rows compare cleanly down a list, the way a price column would.
 *
 * Deliberately its own composable rather than [ResultValue]: this is a *preview* figure attached
 * to an unselected search hit, never a confirmed calculated result, so it must never borrow
 * result-red (design system rule) even though the two-line value/basis shape looks similar.
 */
@Composable
private fun SearchNutritionColumn(hit: ProductSearchHit, modifier: Modifier = Modifier) {
    val value = hit.carbsPer100
    val basis = hit.basis
    Column(
        modifier = modifier.widthIn(min = 90.dp, max = 105.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (value != null && basis != null) {
            Text(
                text = ResultFormatter.quantity(value),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
            Text(
                text = stringResource(R.string.search_carbs_basis, basis.displayUnit()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        } else {
            Text(
                text = stringResource(R.string.search_no_carbs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.End,
            )
        }
    }
}
```

Read the exact existing `ProductSearchHit`/`NutritionBasis` shape first (the fields' real names may
differ from `carbsPer100`/`basis`/`displayUnit()` above — check `domain/Product.kt` or wherever
`ProductSearchHit` and `NutritionBasis` are declared, and the existing `search_carbs` string's
current format-argument order, before finalizing this composable's field accesses). Match the
existing `carbsText`-building logic's null-handling exactly (per the spec: "unknown basis must still
suppress unsupported numbers" — i.e. a value with a null basis must still render as "no value", not
as a bare unlabeled number; check the existing code's current null-combination logic to confirm this
is already the rule before replicating it).

Add the new string resource (basis-only half, for the two-line split) to `strings.xml` near
`search_carbs`:
```xml
    <!-- Second line of the search-result nutrition column, e.g. "/100 g" under the value line. -->
    <string name="search_carbs_basis">/ %1$s</string>
```
Do not remove the existing `search_carbs` (combined) string if anything else in the codebase still
references it — grep first:
```powershell
Select-String -Path app/src/main -Pattern "search_carbs\b" -Recurse
```

Replace the call site inside `SearchResultRow` (the existing trailing `Text(...)`) with
`SearchNutritionColumn(hit, modifier = Modifier.padding(start = Space.s))`, keeping the row's
existing `Spacer`/`weight(1f)` name-column structure exactly as-is.

**Step 3: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 4: Write/extend the instrumented test**

Locate the existing test coverage first:
```powershell
Select-String -Path app/src/androidTest -Pattern "SearchResultRow" -Recurse
```
Add cases to whatever file already covers it:
```kotlin
    @Test
    fun searchRowsPairValueAndBasisAndSuppressAnUnknownBasis() {
        // A hit with a non-null carbsPer100 but null basis must render "No value" (or this file's
        // existing string-resource equivalent), never a bare number with no unit — same rule the
        // basis-suppression logic already enforces elsewhere in the app.
    }

    @Test
    fun searchRowsShowAQuietNoValueStateForAMissingCarbFigure() {
        // A hit with carbsPer100 == null renders the "No value" text, not a fake zero.
    }
```
Fill in the exact `ProductSearchHit` fixture construction using this test file's existing
conventions.

**Step 5: Run tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.search.SearchScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
(Substitute the actual class name found in Step 4.) Run **three times consecutively** — `SearchScreenTest`
is separately documented in this repo as needing repeated runs to catch the soft-keyboard artifact.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/components/Common.kt app/src/main/res/values/strings.xml
git add <the modified androidTest file>
git commit -m "Refine SearchResultRow's trailing nutrition column"
```

---

### Task 7: Home context-aware primary action + responsive empty-state step strip

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt` (`HomeScreen` params around
  line 121-131; `HomeBody`'s call to `HomeActionCard` around lines 599-609; `EmptyStateStepStrip`
  around lines 301-343)
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/home/HomeScreenTest.kt` (existing file —
  add cases)

**Interfaces:**
- Consumes: nothing new — `mealItems`/`mealTotal` are already `HomeScreen` parameters
  (`HomeScreen.kt:130-131`).
- Produces: no new public API — `HomeBody` gains one new internal boolean parameter
  (`mealInProgress: Boolean`), not exposed beyond this file.

**Step 1: Thread `mealInProgress` into `HomeBody` and swap the barcode action's copy**

Read `HomeScreen.kt` lines 121-135 (top-level params) and 563-609 (`HomeBody` + its `HomeActionCard`
call) first.

`HomeScreen`'s top-level composable already receives `mealItems: List<MealItem>` (line 130). At its
`HomeBody(...)` call (around line 236), add:
```kotlin
                    mealInProgress = mealItems.isNotEmpty(),
```

Update `HomeBody`'s signature (around line 564) to accept it:
```kotlin
private fun HomeBody(
    recents: List<RecentEntry>,
    settings: AppSettings,
    onScan: () -> Unit,
    onScanLabel: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    showTutorialReminder: Boolean,
    onStartTutorial: () -> Unit,
    onDismissTutorialReminder: () -> Unit,
    mealInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
```

Update the barcode `HomeActionCard` call (around line 599-609):
```kotlin
        item(key = "action_barcode") {
            HomeActionCard(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(
                    if (mealInProgress) R.string.home_scan_next_button else R.string.home_scan_button,
                ),
                subtitle = stringResource(
                    if (mealInProgress) {
                        R.string.home_action_barcode_subtitle_mid_meal
                    } else {
                        R.string.home_action_barcode_subtitle
                    },
                ),
                accent = MaterialTheme.colorScheme.primary,
                filled = true,
                onClick = onScan,
                modifier = Modifier.testTag(HOME_SCAN_BARCODE_TAG),
            )
        }
```

Add the two new string resources to `strings.xml`, near `home_scan_button`/`home_action_barcode_subtitle`:
```xml
    <!-- Primary barcode action's label while a meal is already in progress — same tile, same icon,
         contextual copy only (2026-09-14 interaction pass, item 7). -->
    <string name="home_scan_next_button">Scan next item</string>
    <string name="home_action_barcode_subtitle_mid_meal">Add another item to your meal</string>
```

**Step 2: Replace the step strip's horizontal-scroll fallback with a responsive layout**

Read `EmptyStateStepStrip` (`HomeScreen.kt:301-343`) fully first. Replace its `horizontalScroll` `Row`
with a composable that measures available width and switches between the existing horizontal roundel
row (unchanged visual vocabulary at normal width) and a compact vertical arrangement:

```kotlin
@Composable
private fun EmptyStateStepStrip(modifier: Modifier = Modifier) {
    val steps = listOf(
        Triple(Icons.Filled.QrCodeScanner, R.string.home_empty_step_scan, MaterialTheme.colorScheme.primary),
        Triple(Icons.Filled.Scale, R.string.home_empty_step_portion, MaterialTheme.colorScheme.tertiary),
        Triple(Icons.Filled.Calculate, R.string.home_empty_step_carbs, MaterialTheme.extendedColors.result),
    )

    // Replaces the previous horizontalScroll fallback, which the code's own prior comment admitted
    // ran the third step past the screen edge at 1.8x font scale on a narrow display. BoxWithConstraints
    // measures the actual available width and switches to a compact vertical arrangement below the
    // threshold, so all three steps are always fully readable without scrolling to see them.
    BoxWithConstraints(modifier = modifier) {
        // 320dp is this repo's own documented historical minimum Android width (see
        // CLAUDE.md's tap-anywhere-tutorial completion pass, which uses the same figure for its
        // own narrow-viewport test) — a plain, already-established threshold rather than a new
        // guess.
        val compact = maxWidth < 320.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                steps.forEach { (icon, labelRes, tint) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepRoundel(icon, tint)
                        Spacer(Modifier.width(Space.s))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.forEachIndexed { index, (icon, labelRes, tint) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepRoundel(icon, tint)
                        Spacer(Modifier.width(Space.s))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Space.xs)
                                .width(16.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRoundel(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
```

Add the `BoxWithConstraints`, `ImageVector`, and `Color` imports if not already present in
`HomeScreen.kt` (check first). Remove the now-unused `horizontalScroll`/`rememberScrollState` imports
if this was their only use in the file (grep to confirm before removing).

Note: reading order is preserved in both branches (Scan → Portion → Carbs, top-to-bottom in the
vertical case, left-to-right in the horizontal case — matches the `steps` list order in both, so no
separate accessibility-traversal override is needed).

**Step 3: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 4: Write instrumented tests**

Add to `HomeScreenTest.kt` (read its existing conventions for rendering `HomeScreen` with a given
`mealItems`/`mealTotal` first):

```kotlin
    @Test
    fun theBarcodeActionOffersToScanNextWhenAMealIsInProgress() {
        // Render HomeScreen with a non-empty mealItems list (matching this file's existing
        // meal-bar-present test's fixture, if one exists).
        composeRule.onNodeWithText(/* resolved home_scan_next_button, e.g. "Scan next item" */)
            .assertExists()
    }

    @Test
    fun theBarcodeActionShowsOrdinaryScanCopyWithNoMealInProgress() {
        // Render HomeScreen with mealItems = emptyList().
        composeRule.onNodeWithText(/* resolved home_scan_button, e.g. "Scan barcode" */)
            .assertExists()
    }

    @Test
    fun theEmptyStateStepStripHasNoHorizontalScrollAtANarrowWidth() {
        // Render just the empty-state Home body (recents = emptyList()) inside a narrow
        // (e.g. 300.dp) width Box, matching this repo's own documented pattern for narrow-viewport
        // tests (see CLAUDE.md's tap-anywhere-tutorial completion pass, "theCalloutFitsOnANarrowViewport",
        // for the established 320dp Box-wrapping convention used elsewhere in this codebase).
        // Assert all three step labels are present and NOT inside any node with a horizontalScroll
        // semantics action (check via the semantics tree, e.g. asserting no ScrollAxisRange
        // horizontal action is present, or simply that all three step texts are simultaneously
        // displayed without needing performScrollTo).
    }
```
Match the exact `HomeUiState`/`HomeScreen` invocation this test file's existing tests already use.

**Step 5: Run tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb shell am instrument -w -r -e class app.justthecarbs.ui.home.HomeScreenTest app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Run **three times consecutively** per `HomeScreenTest`'s own precedent elsewhere in this repo
(soft-keyboard artifact).

**Step 6: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt app/src/main/res/values/strings.xml app/src/androidTest/kotlin/app/justthecarbs/ui/home/HomeScreenTest.kt
git commit -m "Make Home context-aware during an active meal and fix the step strip's narrow-width fallback"
```

---

### Task 8: Barcode scanner acquisition confirmation

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/ScannerScreen.kt` (`ScanFrame` around
  lines 391-422)
- Test: existing barcode scanner test file (locate via grep for `ScanFrame` or `acquired` under
  `androidTest` before adding cases)

**Interfaces:**
- Consumes: nothing new.
- Produces: no new public API — `ScanFrame`'s signature is unchanged
  (`ScanFrame(acquired: Boolean, modifier: Modifier = Modifier)`).

**Step 1: Read the existing `ScanFrame` and extend its animation**

Read `ScannerScreen.kt:391-422` fully. The existing fill-alpha `animateFloatAsState` and check-icon
appearance already provide most of item 6's "brief visual acquisition confirmation." Add a short
scale pulse on the frame's border/box to make the acknowledgment slightly more explicit, still
within `Motion.QUICK_MS`/`STANDARD_MS`, still no new overlay:

```kotlin
@Composable
private fun ScanFrame(acquired: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val fillAlpha by animateFloatAsState(
        targetValue = if (acquired) 0.24f else 0.04f,
        animationSpec = tween(Motion.QUICK_MS),
        label = "scanFrameFill",
    )
    // A brief outward pulse on acceptance only — never on the resting/searching state, and never
    // repeating. Existing infra (the fill/check-icon above) already says "got it"; this adds a
    // small sense of the frame actually reacting to the moment of acceptance rather than merely
    // switching state.
    val scale by animateFloatAsState(
        targetValue = if (acquired) 1.03f else 1f,
        animationSpec = tween(Motion.STANDARD_MS),
        label = "scanFrameScale",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .height(176.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(accent.copy(alpha = fillAlpha), RoundedCornerShape(Space.cardRadius))
            .border(2.dp, accent, RoundedCornerShape(Space.cardRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (acquired) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
```

Add the `graphicsLayer` import if not already present in the file (check first —
`androidx.compose.ui.graphics.graphicsLayer` or the `Modifier.graphicsLayer` extension).

**Step 2: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 3: Locate and extend the existing test coverage**

```powershell
Select-String -Path app/src/androidTest -Pattern "ScanFrame|acquired" -Recurse
```
If an existing test already asserts on the `acquired`-state check icon's presence, this task needs
no new test — the pulse is a presentation detail on top of an already-tested state transition, and
per the plan's own no-pixel-tests rule, the animation's visual frames are not independently
asserted. Confirm the existing assertion still passes; if none exists at all for this composable,
add one minimal case asserting the check icon appears when `acquired = true` and is absent when
`false` (a state-presence assertion, not a timing/animation one).

**Step 4: Run tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```
Run the relevant class found in Step 3.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/scan/ScannerScreen.kt
git commit -m "Add a brief acquisition pulse to the barcode scan frame"
```

---

### Task 9: Label scanner shutter → reading state choreography

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt` (`SearchingCard`
  around lines 2047-2099; `CaptureButton` around lines 2409-2417)
- Test: existing label scanner test file (locate via grep for `CaptureState` or `SearchingCard`
  under `androidTest`)

**Interfaces:**
- Consumes: nothing new — `CaptureState` enum (`IDLE`/`CAPTURING`/`PROCESSING`) is unchanged.
- Produces: no new public API — `SearchingCard`'s and `CaptureButton`'s signatures are unchanged.

**Note on scope**: the live-camera-to-frozen-photo transition is a **function-level early return**
(`LabelScannerScreen.kt:1549-1557`, `if (frozen != null && frozenBitmap != null) { ...; return }`),
not a single composable swap — the existing code comment there explicitly explains why (continuing
to render the live camera underneath a frozen review would waste power and confuse the user). Do
**not** restructure that into a `Crossfade` across the live/frozen boundary; that is a materially
larger and riskier change than this task's scope, and risks reintroducing the exact problem that
comment warns against. Instead, this task makes the **shutter's own acknowledgment** (which happens
entirely within `SearchingCard`, before the frozen branch is ever reached) visibly connected and
immediate, which is what the brief's "do not wait for OCR completion before acknowledging capture"
and "shutter briefly morphs into a compact reading/progress treatment" describe.

**Step 1: Read `SearchingCard` and `CaptureButton` fully**

Read `LabelScannerScreen.kt:2047-2099` and `2409-2417` before changing anything — confirm the exact
current `when (captureState)` text swap and the plain `Button` shape.

**Step 2: Wrap the guidance text swap in `AnimatedContent`**

Replace the current plain `Text(text = stringResource(when (captureState) {...}), ...)` inside
`SearchingCard`'s `Row` (around lines 2064-2093) with an `AnimatedContent` keyed on the resolved
string resource id, so the text itself cross-fades on state change rather than swapping instantly:

```kotlin
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (captureState != CaptureState.IDLE) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            val guidanceRes = when (captureState) {
                CaptureState.CAPTURING -> R.string.ocr_capturing
                CaptureState.PROCESSING -> R.string.ocr_processing
                CaptureState.IDLE -> when {
                    liveReadiness is LabelReading.Confident ||
                        liveReadiness is LabelReading.Ambiguous -> R.string.ocr_ready_to_capture
                    framing?.readiness == TextResolutionGuidance.Readiness.SIDEWAYS ->
                        R.string.ocr_turn_upright
                    framing?.readiness == TextResolutionGuidance.Readiness.TOO_SMALL ->
                        R.string.ocr_move_closer
                    else -> R.string.ocr_looking
                }
            }
            AnimatedContent(
                targetState = guidanceRes,
                transitionSpec = {
                    (fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)))
                },
                label = "scanGuidance",
            ) { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CaptureButton(onCapture, captureState = captureState)
```

Note `CaptureButton`'s call signature changes here (from `enabled = captureState == CaptureState.IDLE`
to passing the whole `captureState`) — implemented next.

**Step 3: Make `CaptureButton` morph into a compact progress treatment**

Replace `CaptureButton` (lines 2409-2417):

```kotlin
/**
 * The shutter. Morphs into a compact progress treatment the instant a capture is accepted — before
 * OCR has produced anything — so the tap is visibly acknowledged rather than the screen appearing to
 * ignore it for however long recognition takes (design pass item 6: "do not wait for OCR completion
 * before acknowledging capture").
 */
@Composable
private fun CaptureButton(onClick: () -> Unit, captureState: CaptureState) {
    Button(
        onClick = onClick,
        enabled = captureState == CaptureState.IDLE,
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.primaryButtonHeight),
    ) {
        AnimatedContent(
            targetState = captureState,
            transitionSpec = {
                (fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)))
            },
            label = "captureButtonContent",
        ) { state ->
            if (state == CaptureState.IDLE) {
                Text(stringResource(R.string.ocr_capture_label))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        stringResource(
                            if (state == CaptureState.CAPTURING) R.string.ocr_capturing else R.string.ocr_processing,
                        ),
                    )
                }
            }
        }
    }
}
```

This removes the now-redundant standalone `CircularProgressIndicator` above the guidance text in
`SearchingCard` (Step 2's version already keeps it — leave it as-is; the two indicators serve
different reads: one on the button itself for "the shutter accepted your tap," one beside the
guidance text for "here is what's happening." If, once seen on an actual device, this reads as
duplicated rather than reinforcing, note it in the final report as a finding rather than silently
removing one — this is a judgment call to surface, not to decide unilaterally mid-implementation).

Add `LocalContentColor` import if not already present (`androidx.compose.material3.LocalContentColor`).

**Step 4: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

**Step 5: Locate and extend existing test coverage**

```powershell
Select-String -Path app/src/androidTest -Pattern "CaptureState|SearchingCard|ocr_capture_label" -Recurse
```
If existing tests already assert `CaptureButton`'s `enabled` state per `CaptureState`, confirm they
still pass unchanged (the `enabled` logic is unchanged — only its content composition changed). Add
one case if none exists asserting the capture label text is shown when `IDLE` and progress text is
shown when `CAPTURING`/`PROCESSING` (a state-presence assertion, not a timing one, per this plan's
no-animation-frame-testing rule).

**Step 6: Run tests**

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```
Run whatever class covers `LabelScannerScreen` (locate via the Step 5 grep).

**Step 7: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt
git commit -m "Animate the label scanner's shutter and guidance text state transitions"
```

---

### Task 10: Navigation transitions + predictive back

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt` (`NavHost(...)` call)
- Modify: `app/src/main/AndroidManifest.xml` (add
  `android:enableOnBackInvokedCallback="true"` to `<application>`)
- Test: no new instrumented test file — existing navigation-flow tests (if any) must still pass;
  this task's verification is primarily build + manual navigation smoke-check (see Step 5)

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this task changes only presentation-layer transition specs and a manifest
  flag; no navigation destination, route, or back-stack behavior changes.

**Step 1: Read the current `NavHost` call and confirm the Navigation-Compose 2.9.8 API shape**

Read `JustTheCarbsNavHost.kt`'s `NavHost(...)` call in full. Confirm (via the actual installed
library source, e.g. under the Gradle cache, or the official Navigation-Compose 2.9.8 release notes/
KDoc if locally inspectable) whether `NavHost` in this version accepts
`enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` as direct constructor
parameters (applied to every destination by default) — this has been available as a `NavHost`-level
default since Navigation-Compose 2.7, so 2.9.8 should support it, but verify against the actual
resolved artifact rather than assuming.

**Step 2: Add a shared fade transition spec**

Add the four transition lambdas to the existing `NavHost(...)` call:

```kotlin
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Short, uniform fade across every destination — clarifies "you moved to a new screen"
        // without a slide/scale choreography and without delaying the destination's own content
        // (design pass item 11; the app's motion vocabulary is Motion.QUICK_MS/STANDARD_MS
        // everywhere else, so navigation uses the same two numbers rather than inventing a third).
        enterTransition = { fadeIn(tween(Motion.STANDARD_MS)) },
        exitTransition = { fadeOut(tween(Motion.QUICK_MS)) },
        popEnterTransition = { fadeIn(tween(Motion.STANDARD_MS)) },
        popExitTransition = { fadeOut(tween(Motion.QUICK_MS)) },
    ) {
        // ... existing composable(...) entries unchanged
    }
```

Add the necessary imports (`androidx.compose.animation.fadeIn`, `androidx.compose.animation.fadeOut`,
`androidx.compose.animation.core.tween`, `app.justthecarbs.ui.theme.Motion`) if not already present
in the file (check first).

**Explicitly verify this does not delay scanner results**: the barcode and label scanner screens are
themselves navigation destinations, so their *entry* transition is this same fade — confirm (by
reading, and later by manual check in Step 5) that the fade only affects the container's opacity
during the ~220ms entry, not anything inside the destination's own state machine (`CaptureState`,
`BarcodeAnalyzer` callbacks) — those are unrelated to `NavHost`'s transition and start running
immediately regardless of the fade, so no scanner behavior is actually delayed; only the container's
visual entrance is faded in.

**Step 3: Add the predictive-back manifest flag**

Read `app/src/main/AndroidManifest.xml`'s `<application>` tag. Add:
```xml
    <application
        android:enableOnBackInvokedCallback="true"
        ...>
```
(Preserve every existing attribute on that tag exactly — this is a single attribute addition, not a
reformat.)

**Step 4: Audit existing `BackHandler` sites — replace only where safe**

```powershell
Select-String -Path app/src/main/kotlin -Pattern "BackHandler\(" -Recurse
```
For each site found (expected: at least `ProductScreen.kt:211`, `ScannerScreen.kt:192`, likely
others), read its surrounding ~15 lines. For each site, decide independently:

- **Safe to migrate to `PredictiveBackHandler`**: the back action is a pure navigation
  pop/state-reset with no cleanup ordering dependency (e.g. simply closing a dialog, or a screen
  whose `BackHandler` just calls the same `onBack` callback `NavHost`'s own back button would use).
  For these, replace `BackHandler(enabled = X) { onBack() }` with the `PredictiveBackHandler` API's
  equivalent (a `LaunchedEffect`-driven collection of the predictive-back `Flow` that, on gesture
  completion, invokes the same `onBack()` — read `androidx.activity.compose.PredictiveBackHandler`'s
  actual signature in the resolved `androidx.activity:activity-compose` version before writing the
  replacement, since its exact shape (a suspend `Flow<BackEventCompat>`-based API, not a simple
  callback) differs materially from `BackHandler`'s).
- **Leave as ordinary `BackHandler`, documented**: any site with cleanup ordering concerns — in
  particular `ScannerScreen.kt`'s and `LabelScannerScreen.kt`'s, given this repo's own extensive
  documented history of camera/executor disposal races (see `CLAUDE.md`'s "Closed-beta quality pass"
  section on `focusThenCapture`'s uncancelled timeout and `executor.shutdown()` ordering). Add a
  one-line comment at each such site: `// Deliberately not migrated to PredictiveBackHandler in the
  2026-09-14 interaction pass — camera/executor disposal ordering here needs its own dedicated
  audit; see docs/superpowers/specs/2026-09-14-interaction-polish-design.md.`

Do not force a migration you are not confident is behaviorally identical. This audit's outcome
(how many sites migrated, how many deliberately left) is reported in the final report, not decided
in advance by this plan.

**Step 5: Manual smoke-check (no automated test can assert predictive-back gesture behavior)**

Build and install the debug APK on the emulator; drive: Home → Product → back (confirm lands back on
Home, same as before); Home → Scanner → back mid-scan (confirm camera releases cleanly, no crash —
this is the highest-risk path given Step 4's caution); Home → Search → a result → Product → back →
back (confirm the stack unwinds identically to pre-change behavior). Record this as manual/emulator
evidence in the final report, consistent with this repo's standing practice of reporting exactly
what was and was not verified on real hardware vs. emulator.

**Step 6: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```
Run the full existing instrumented suite for any screen touched by a `BackHandler` migration in
Step 4 (at minimum `ProductScreenTest`, and the barcode/label scanner test classes if either was
touched).

**Step 7: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt app/src/main/AndroidManifest.xml
git add <any files touched in Step 4's BackHandler audit>
git commit -m "Add short navigation fade transitions and enable predictive back"
```

---

### Task 11: `DESIGN.md` updates + full verification + final report

**Files:**
- Modify: `docs/DESIGN.md` (append principles that shipped)
- No code changes in this task — verification and documentation only

**Step 1: Update `DESIGN.md`**

Read the current `DESIGN.md` in full (already read once during brainstorming — re-read for any
change from other concurrent work before editing). Add new entries to the appropriate existing
sections rather than a new top-level section, matching the file's existing register (declarative,
one paragraph per principle, no implementation minutiae):

- Under "Typography and numerical hierarchy": one paragraph on the split number/unit result shape
  (`ResultValue`) and tabular-numeral status (whichever Task 1 Step 2 concluded).
- Under "Dark mode, motion, and accessibility" (or a new adjacent short paragraph): the
  success-feedback grammar (generalized from the copy check-mark), scanner state choreography, and
  the short navigation fade — each one sentence to a short paragraph, principle-level only (e.g. "A
  successful action changes its own control's state rather than requiring a separate toast" — not
  "MealActions accepts a `justAdded: Any?` parameter").
- Under "Screen composition": one line on Home's contextual primary-action copy during an active
  meal, and the `SearchResultRow` nutrition column's reserved-width shape.

Do not restate exact dp values, enum names, or file paths in `DESIGN.md` — those stay in code
comments per the spec's own instruction ("Do not turn implementation details into permanent design
doctrine").

**Step 2: Run the full verification suite**

```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```
Expected: all green, 0 new lint errors (compare warning count before/after this pass began, at
commit `ed00d2a`, to confirm no regression).

Run every touched instrumented test class at least once more together, with an emulator/device
attached:
```powershell
adb devices
```
Then, for each of: `ResultValueTest`, `SuccessPulseTest`, `ProductScreenTest`, `MealScreenTest`
(x3 runs), the `SearchResultRow`-covering class (x3 runs), `HomeScreenTest` (x3 runs), and whatever
classes cover the two scanner screens —
```powershell
adb shell am instrument -w -r -e class <FullyQualifiedClassName> app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
```

**Step 3: `git diff --check`**

```powershell
git diff --check
```
Expected: clean (or only pre-existing CRLF notices this repo's own standing practice already treats
as benign).

**Step 4: Visual QA pass**

Install the debug APK on the emulator and drive through the states listed in the design spec's
Testing Approach section — Light + Dark for: Home resting, Home active meal, Home empty, Search with
mixed result quality, Product no-result/pending, Product calculated result, Product after Add to
meal, Product IME open, Meal empty, Meal populated, label scanner live, label scanner
capture/reading, barcode accepted/lookup — plus large-font checks on result typography, the search
row, Home's empty state, active-meal Home, Meal Total, and Product result/actions, and a narrow-width
check. Take note of anything that reads wrong; fix trivial issues inline (matching this repo's own
documented practice of catching layout defects only a rendered screen reveals) and record anything
non-trivial as a residual risk in the final report rather than silently expanding scope.

**Step 5: Commit the documentation update (and any trivial visual fixes from Step 4)**

```bash
git add docs/DESIGN.md
git commit -m "Document the 2026-09-07 interaction-pass design principles"
```
(If Step 4 produced trivial fixes, include them in this commit or a preceding small one, whichever
better isolates the change per this repo's own commit-discipline conventions.)

**Step 6: Write the final report**

Per the original brief's §FINAL REPORT section, produce a concise report (not a chronological
diary) covering: starting/final HEAD; changes implemented; the drag-gesture item's explicit
deferral and why; UI/UX defects found during implementation; accessibility/responsive findings;
visual states actually inspected; tests/checks run and their results; files substantially changed;
remaining physical-device QA; residual risks (including the `BackHandler` sites deliberately left
unmigrated from Task 10, and the tabular-numerals finding from Task 1).

---

## Plan self-review notes

- **Spec coverage**: items 1 (Task 1, 3, 5), 2 (Task 5), 3 (Task 4), 4 (Tasks 2, 4), 5 (Task 6),
  6 (Tasks 8, 9), 7 (Task 7), 8 (Task 7), 9 (confirmed as already-working existing architecture,
  Task 3's Step 1 change is purely the `ResultValue` swap — no separate task needed since the spec
  itself says this is refinement/confirmation, not new state), 10 (Task 3), 11 (Task 10),
  12 (explicitly deferred, documented in Global Constraints and Task 11's report), 13 (motion
  vocabulary constraint applied throughout, no dedicated task), 14 (Global Constraints), 15
  (accessibility notes folded into each task's Step, e.g. Task 1's merged semantics, Task 7's reading
  order), 16 (Task 11 Step 4), 17 (every task's own Steps + Task 11), 18 (Task 11 Step 1),
  19 (Task 11 Step 5-6, commit + report).
- **Placeholder scan**: no TBD/TODO left; every "read the existing file first" instruction is paired
  with an exact line range and an exact code diff to apply, not a description of what to do.
  Fixture-construction calls inside new tests are deliberately left as "match this file's existing
  convention" rather than invented from nothing, since the actual existing helper/fixture shape in
  each test file was not read line-by-line during this survey — this is flagged explicitly in each
  such Step rather than silently guessed at, which is the honest alternative to fabricating a fixture
  API that might not match the real file.
- **Type/name consistency check**: `ResultValue(dominant, unit, accessibleLabel, modifier, color,
  testTag)` — used identically in Task 3 (Product) and Task 5 (Meal). `rememberSuccessPulse(trigger,
  holdMs)` — used identically in Task 4. `ProductUiState.lastMealAddSucceeded: Long?` — declared in
  Task 4 Step 3, consumed in Task 4 Steps 5-6, no renaming across steps. `PRODUCT_VERIFY_INLINE_TAG`
  — declared and consumed within Task 3 only. `mealInProgress: Boolean` — declared and consumed
  within Task 7 only, not exposed elsewhere. Confirmed no drift between declaration and usage.
