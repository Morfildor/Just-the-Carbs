# Tutorial Tap-Anywhere Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the coach-mark tutorial's precision-tap interaction (aim for a small Next/Back/Finish button, chase a callout that jumps between screen halves, watch a pulsing arrowed spotlight) with a full-screen tap-anywhere overlay: a deterministic bottom-default callout, one static spotlight ring, and a dedicated tap-catching surface that Skip always wins against structurally.

**Architecture:** `OnboardingScreen` gains a full-screen tap-catching `Box` layered strictly between the decorative backdrop/scrim and the callout card + Skip button, so pointer-input dispatch order — not manual hit-test logic — guarantees Skip always intercepts its own taps first. `CalloutPlacement.kt` is rewritten from a two-sided room-comparison into a one-directional bottom-default/top-fallback decision driven by the callout card's real measured height via `SubcomposeLayout`. `TutorialOverlay.kt` drops its halo/pulse/arrow drawing code, keeping one static ring. `OnboardingViewModel` drops `previous()`/back support. No navigation, persistence, or backdrop-architecture changes.

**Tech Stack:** Kotlin, Jetpack Compose (`SubcomposeLayout`, `pointerInput`/`detectTapGestures`, `Canvas`/`drawScope`), JUnit4 (pure JVM tests for `CalloutPlacement.kt`, `TutorialStep.kt`), Compose UI test (`createComposeRule`, instrumented tests for `TutorialScreenTest.kt`/`TutorialNavigationTest.kt`).

**Spec:** `docs/superpowers/specs/2026-09-08-tutorial-tap-anywhere-design.md`

## Global Constraints

- UI strings stay English-only; do not add non-English tutorial copy (per CLAUDE.md's countable-portion-language decision, which applies project-wide to displayed UI text).
- `domain/` stays pure Kotlin and JVM-testable — this task does not touch `domain/`, but `CalloutPlacement.kt`'s pure arithmetic (in `ui/onboarding/`) must remain independently JVM-testable with no Compose runtime dependency in its core decision function, exactly as it is today.
- Do not merge or touch `hasSeenOnboarding` (welcome carousel) and `hasSeenTutorial` (this coach-mark tutorial) — they are deliberately separate flags per CLAUDE.md's "Two introductions, two flags" section. This plan touches only `hasSeenTutorial`'s screen; do not add any welcome-carousel code.
- `TutorialPreview.kt` backdrops must stay structurally non-interactive (no ViewModel, no clickables, no semantics reachable by TalkBack) — this is what makes tap-anywhere safe with no click-through risk. Do not add any interactive element to the backdrop previews.
- No new external dependency, no new gesture-detection library, no debounce/time-based tap guard — a fast double-tap simply advances twice.
- Every touch target (Skip, and the tap-catching surface itself where relevant) must clear `Space.minTouchTarget` (48.dp) — pinned today by `everyTutorialControlMeetsTheTouchTargetFloor`, being renamed/adjusted in this plan, not deleted.
- Never claim a test passed without running it. Every task's steps below must actually be executed, not assumed.

---

## File Structure

| File | Responsibility after this change |
|---|---|
| `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt` | Step index, completion state, `finish()`. Loses `previous()`. |
| `app/src/main/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacement.kt` | Pure bottom-default/top-fallback decision (`calloutSideFor`), now taking the card's real measured height instead of an estimate. Loses the room-comparison logic. `clampHorizontally` is unused by the new placement (no arrow anymore) — checked for other call sites and removed if genuinely dead. |
| `app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialOverlay.kt` | `TutorialScrim` (unchanged) + `TutorialSpotlightDecoration` (loses halo loop, `drawConnector`, `drawArrowHead`, `arrowStart`/`pulse` params — keeps one static ring). |
| `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingScreen.kt` | Adds the `SubcomposeLayout`-based measured placement and the sibling tap-catching `Box`; removes Back button, dot-row progress, arrow/pulse wiring, `CALLOUT_HEIGHT_ESTIMATE` estimate path (unless the `SubcomposeLayout` step is abandoned, in which case it is kept as an explicitly isolated fallback per the spec). |
| `app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialStep.kt` | Copy-only edits (title/body string *content* in `strings.xml`, not this file's structure). |
| `app/src/main/res/values/strings.xml` | Tutorial copy tightened to one sentence per step where currently two; `tutorial_back` string usage removed from code (string itself can stay unreferenced-but-harmless or be removed — see Task 6). |
| `app/src/test/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacementTest.kt` | Rewritten for the new bottom-default/measured-fallback contract. |
| `app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialScreenTest.kt` | Rewritten around tap-anywhere: tap advances, Skip wins hit-testing, no Back, no dot row. |
| `app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt` | Two `TUTORIAL_PRIMARY_TAG` click walks become generic taps on the tap-catching surface; everything else unchanged. |

---

### Task 1: Remove Back from `OnboardingViewModel`

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt`
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt` (uses `viewModel::previous` — will be fixed in Task 8, but this task must not break compilation, so update the one reference here too)

**Interfaces:**
- Consumes: nothing new.
- Produces: `OnboardingViewModel` no longer exposes `fun previous()`. `OnboardingScreen` (Task 5) will drop its `onPrevious: () -> Unit` parameter entirely — callers of `OnboardingScreen` must stop passing it.

This task is small and mechanical, so it is folded together rather than split further — removing a single public function and its one call site is not independently reviewable at finer grain.

- [ ] **Step 1: Remove `previous()` from `OnboardingViewModel.kt`**

Delete this method (lines 68-77 in the current file):

```kotlin
    /**
     * Step back one moment.
     *
     * Coerced at zero rather than wrapping to the end: the first step is the beginning of a
     * sequence, and wrapping would hide that. Back *from* the first step is the screen's business
     * (it leaves the tutorial), not this function's.
     */
    fun previous() {
        _stepIndex.value = (_stepIndex.value - 1).coerceAtLeast(0)
    }
```

- [ ] **Step 2: Fix the one call site in `TutorialNavigationTest.kt` that references `viewModel::previous`**

In `TestGraph`, find:

```kotlin
                OnboardingScreen(
                    stepIndex = stepIndex,
                    mode = mode,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onExit = { exitScope.launch { viewModel.finish() } },
                    busy = completion is OnboardingViewModel.CompletionState.Saving,
                )
```

Remove the `onPrevious = viewModel::previous,` line. (`OnboardingScreen` still requires this parameter until Task 5 removes it from the signature — leaving this line in would fail to compile once `previous()` is deleted, but removing the parameter from the call site now will itself fail to compile against the *current* `OnboardingScreen` signature, which still requires `onPrevious`. Resolve this by temporarily passing `onPrevious = {}` here, to be cleaned up for real in Task 8 once `OnboardingScreen`'s signature changes in Task 5.)

```kotlin
                OnboardingScreen(
                    stepIndex = stepIndex,
                    mode = mode,
                    onNext = viewModel::next,
                    onPrevious = {},
                    onExit = { exitScope.launch { viewModel.finish() } },
                    busy = completion is OnboardingViewModel.CompletionState.Saving,
                )
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (there is no unit test file for `OnboardingViewModel` today; this is a compile-only checkpoint, confirmed by searching `app/src/test` for `OnboardingViewModelTest` — none exists as of this plan, so there is nothing to run at unit level for this specific method removal).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt
git commit -m "Remove Back support from OnboardingViewModel

Forward-only tour: OnboardingViewModel.previous() is deleted. The one
androidTest call site is stubbed to an empty lambda pending
OnboardingScreen's signature change in a later task."
```

---

### Task 2: Rewrite `CalloutPlacement.kt` for bottom-default/measured-fallback

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacement.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacementTest.kt`

**Interfaces:**
- Consumes: nothing (pure function, no dependency on other tasks).
- Produces: `calloutSideFor(spotlightTop: Float, spotlightBottom: Float, screenHeight: Float, cardHeight: Float, safeAreaBottomInset: Float = 0f): CalloutSide` — **signature changes**: `requiredHeight` is renamed `cardHeight` (it is now the card's *actual* measured height, not an estimate — the rename makes that fact visible at every call site) and a new `safeAreaBottomInset` parameter (default 0f) accounts for system-bar padding already applied by the caller, so the "does it fit below" check is against the true usable bottom zone. `CalloutSide` enum keeps its three values (`ABOVE`, `BELOW`, `CENTERED`) — `CENTERED` is retained only for the no-spotlight (orientation step / anchor unavailable) case, not as a placement outcome when both zones are too small (see rule below). `clampHorizontally` is unused after this task (no arrow to position) — Task 3 confirms this and removes it if so.

The new rule, exactly as approved: bottom is default. Move to top only if bottom would materially overlap the spotlight (the available gap below the spotlight, minus the bottom safe-area inset, is smaller than the card's real height) OR bottom is otherwise unusable at the current size. There is no "roomier side" comparison and no scored fallback — if top *also* doesn't fit, the design still resolves to top (not centered) for a spotlit step, because bottom is disqualified and top is the only other on-target position; `CENTERED` remains reserved for `spotlight == null` (decided by the caller, as today — `calloutSideFor` is only ever invoked when there is a real spotlight, exactly as now).

- [ ] **Step 1: Write the failing tests — replace `CalloutPlacementTest.kt` entirely**

```kotlin
package app.justthecarbs.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Callout placement, tested at the sizes an emulator does not happen to have.
 *
 * Bottom is the deterministic default. The only decision this function makes is whether bottom
 * placement, given the card's REAL measured height (not an estimate), would overlap the spotlight
 * or fail to fit in the usable safe area below it — in which case it falls back to top. There is no
 * "roomier side" comparison: a side is chosen once by this one rule, not by comparing two candidate
 * zones' available space.
 */
class CalloutPlacementTest {

    private val screen = 2400f
    private val card = 600f

    @Test
    fun `an ordinary target near the top uses the bottom default`() {
        val side = calloutSideFor(
            spotlightTop = 200f,
            spotlightBottom = 400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `an ordinary target near vertical centre still uses the bottom default`() {
        // The old "roomier side" rule would have picked ABOVE here (1400 above vs 1000 below).
        // The new rule has no such comparison: bottom fits (1000 >= 600), so bottom is used.
        val side = calloutSideFor(
            spotlightTop = 1400f,
            spotlightBottom = 1400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `a target low enough to make the bottom zone too small falls back to top`() {
        // Only 300px below the spotlight, card needs 600 - bottom would overlap. Top has 2000px.
        val side = calloutSideFor(
            spotlightTop = 2000f,
            spotlightBottom = 2100f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `a target flush against the bottom edge falls back to top`() {
        val side = calloutSideFor(
            spotlightTop = 2100f,
            spotlightBottom = 2400f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }

    @Test
    fun `a target flush against the top edge still uses the bottom default`() {
        val side = calloutSideFor(
            spotlightTop = 0f,
            spotlightBottom = 300f,
            screenHeight = screen,
            cardHeight = card,
        )
        assertEquals(CalloutSide.BELOW, side)
    }

    @Test
    fun `the bottom safe-area inset is subtracted from the available gap`() {
        // 700px between spotlight and screen bottom, card needs 600 - fits with no inset.
        // But a 150px system-bar inset shrinks the usable gap to 550, which no longer fits.
        val withoutInset = calloutSideFor(
            spotlightTop = 1600f,
            spotlightBottom = 1700f,
            screenHeight = screen,
            cardHeight = card,
            safeAreaBottomInset = 0f,
        )
        assertEquals(CalloutSide.BELOW, withoutInset)

        val withInset = calloutSideFor(
            spotlightTop = 1600f,
            spotlightBottom = 1700f,
            screenHeight = screen,
            cardHeight = card,
            safeAreaBottomInset = 150f,
        )
        assertEquals(CalloutSide.ABOVE, withInset)
    }

    @Test
    fun `repeated calls with identical inputs return the identical side`() {
        // Determinism is the whole point of removing the room-comparison: the same step must always
        // render in the same place, never flip between runs on the same geometry.
        val first = calloutSideFor(1000f, 1200f, screen, card)
        val second = calloutSideFor(1000f, 1200f, screen, card)
        val third = calloutSideFor(1000f, 1200f, screen, card)
        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `a very tall card that fits neither zone still resolves to top rather than centering`() {
        // Bottom disqualified (does not fit); top is the only remaining on-target position, so it
        // is used even though it is also tight -- there is no third "give up and center" outcome
        // once a real spotlight exists. CENTERED is reserved for the no-spotlight case, decided by
        // the caller, not by this function choosing it as a fallback.
        val side = calloutSideFor(
            spotlightTop = 1100f,
            spotlightBottom = 1300f,
            screenHeight = screen,
            cardHeight = 1150f,
        )
        assertEquals(CalloutSide.ABOVE, side)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (compile error, since `calloutSideFor`'s signature does not yet match)**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.onboarding.CalloutPlacementTest"`
Expected: FAIL — compile error, `calloutSideFor` does not have a `cardHeight` or `safeAreaBottomInset` parameter yet.

- [ ] **Step 3: Rewrite `CalloutPlacement.kt`**

```kotlin
package app.justthecarbs.ui.onboarding

/**
 * Which side of the spotlight the callout card sits on.
 *
 * [CENTERED] is the no-anchor case, decided by the caller before [calloutSideFor] is ever invoked:
 * when there is no spotlight at all (the orientation step, or a target briefly unavailable), the
 * card sits in the middle of the screen. [calloutSideFor] itself only ever returns [ABOVE] or
 * [BELOW] — it is not consulted when there is no spotlight to place the card relative to.
 */
enum class CalloutSide { ABOVE, BELOW, CENTERED }

/**
 * Where to put the callout for a spotlight occupying [spotlightTop]..[spotlightBottom] on a screen
 * of height [screenHeight]. All values are pixels in the same coordinate space.
 *
 * Pure arithmetic in its own file so the rule is JVM-testable: the placement decision is exactly the
 * kind of thing that looks obviously right while being wrong on one screen size, and an instrumented
 * test can only check the sizes the emulator happens to have.
 *
 * **The rule is bottom-default, not a comparison.** A previous version of this function compared
 * the space above and below the spotlight and picked whichever was roomier -- that is what made the
 * callout's reading position jump between tutorial steps depending on where each step's target
 * happened to sit. There is now exactly one default (bottom) and exactly one reason to leave it: the
 * bottom zone, after subtracting [safeAreaBottomInset], is smaller than [cardHeight]. In that case
 * -- and only that case -- the card moves to the top zone instead. This keeps the decision coarse
 * and deterministic on purpose: the same spotlight geometry always produces the same side, on every
 * call, with no second candidate to weigh it against.
 *
 * [cardHeight] must be the callout card's real measured height (see [OnboardingScreen]'s
 * `SubcomposeLayout` usage), not an estimate -- an estimate is exactly the kind of guess that is
 * wrong at the extremes (a long translated string, a large accessibility font size), which is
 * precisely where getting this decision wrong means the card overlaps the very control it describes.
 */
fun calloutSideFor(
    spotlightTop: Float,
    spotlightBottom: Float,
    screenHeight: Float,
    cardHeight: Float,
    safeAreaBottomInset: Float = 0f,
): CalloutSide {
    val availableBelow = (screenHeight - spotlightBottom) - safeAreaBottomInset
    return if (availableBelow >= cardHeight) CalloutSide.BELOW else CalloutSide.ABOVE
}
```

Note: `clampHorizontally` is deliberately left in place for this step — Task 3 checks whether it has any remaining caller before removing it, since the new spotlight has no arrow to position but the function's usage needs to be confirmed empirically rather than assumed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.onboarding.CalloutPlacementTest"`
Expected: PASS, all 8 cases.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacement.kt app/src/test/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacementTest.kt
git commit -m "Replace roomier-side callout placement with deterministic bottom default

calloutSideFor no longer compares available space on both sides of the
spotlight. Bottom is the default; it only falls back to top when the
card's real measured height would not fit (or would overlap the
spotlight) in the bottom safe area. cardHeight replaces the estimate-
named requiredHeight parameter, and a new safeAreaBottomInset accounts
for system-bar padding in the fit check."
```

---

### Task 3: Remove halo/pulse/arrow from `TutorialOverlay.kt`

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialOverlay.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `TutorialSpotlightDecoration(spotlight: Rect?, accent: Color, cornerRadius: Dp, strokeWidth: Dp, modifier: Modifier = Modifier)` — **signature changes**: `arrowStart: Offset?` and `pulse: Float = 1f` parameters are removed. `inflateWithin` is unchanged (still used by `OnboardingScreen` to pad the spotlight rect). Task 5 (`OnboardingScreen.kt`) depends on this new, shorter signature and must stop passing `arrowStart`/`pulse`.

There is no test file for `TutorialOverlay.kt` today (it is drawing code with no pure logic to unit-test; `inflateWithin`'s geometry has historically been covered indirectly through screen-level instrumented tests, not a dedicated JVM test file — confirmed by searching `app/src/test` for `TutorialOverlayTest`, none exists). This task is verified by compilation and by the instrumented suite in Task 8, not a dedicated new test.

- [ ] **Step 1: Remove the halo loop, `drawConnector` call, and their supporting private functions/constants**

Replace `TutorialSpotlightDecoration` and delete `drawConnector`/`drawArrowHead`/`HALO_LAYERS`/`HALO_STEP`/`HALO_ALPHA`:

```kotlin
/**
 * The ring drawn around the spotlight.
 *
 * Restrained by design: one static rounded outline, nothing else. No pulse, no connector, no
 * arrowhead — the tutorial is read once and should not be the loudest thing the user ever sees the
 * app do, and a tap-anywhere overlay does not need an arrow telling the user where to press, because
 * pressing anywhere works.
 *
 * The border is not the only cue that a control is the target: the spotlight is a hole in an
 * otherwise uniform dim, and the callout names the control in words. So the design does not rely on
 * colour alone.
 */
@Composable
fun TutorialSpotlightDecoration(
    spotlight: Rect?,
    accent: Color,
    cornerRadius: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (spotlight == null) return@Canvas

        val radiusPx = cornerRadius.toPx()
        val strokePx = strokeWidth.toPx()

        drawRoundRect(
            color = accent,
            topLeft = spotlight.topLeft,
            size = spotlight.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
        )
    }
}
```

Delete these blocks entirely (they no longer have any caller once the above replaces the old `TutorialSpotlightDecoration`):
- `private const val HALO_LAYERS = 3`
- `private const val HALO_STEP = 2.2f`
- `private const val HALO_ALPHA = 0.28f`
- `private fun DrawScope.drawConnector(...)` (the whole function, including its doc comment)
- `private fun DrawScope.drawArrowHead(...)` (the whole function, including its doc comment)

- [ ] **Step 2: Remove now-unused imports**

After deleting the code above, `Path`, `PathEffect`, and `kotlin.math.abs` are no longer referenced anywhere in this file (they were only used by `drawConnector`/`drawArrowHead`). Remove:

```kotlin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import kotlin.math.abs
```

Verify `Offset` is still used elsewhere in the file before removing its import — it is (in `TutorialScrim`'s `Offset.Zero` and gradient-centre calculations), so keep `import androidx.compose.ui.geometry.Offset`.

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: FAIL at this point — `OnboardingScreen.kt` still calls `TutorialSpotlightDecoration` with `arrowStart`/`pulse` arguments (fixed in Task 5). This is expected; do not attempt to fix `OnboardingScreen.kt` in this task.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialOverlay.kt
git commit -m "Simplify TutorialSpotlightDecoration to a single static ring

Removes the three-layer breathing halo and the curved connector/
arrowhead entirely. A tap-anywhere overlay has nothing for an arrow to
direct the user's finger toward. Leaves OnboardingScreen.kt temporarily
non-compiling against the new shorter signature; fixed in a later task."
```

---

### Task 4: Confirm `clampHorizontally` is dead code and remove it if so

**Files:**
- Modify (conditionally): `app/src/main/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacement.kt`
- Modify (conditionally): `app/src/test/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacementTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: either `clampHorizontally` remains available, or it is removed. Task 5 must not call it either way, since the new placement has no horizontally-offset arrow anchor to clamp.

`clampHorizontally` existed to keep the arrow's horizontal start position on-screen near a target close to a screen edge. With the arrow removed (Task 3) and the callout card already `fillMaxWidth()`-ed within `Space.screenEdge` padding (unaffected by this redesign), there should be no remaining caller. This must be verified by search, per AGENTS.md's reachability rule, not assumed from the design intent alone.

- [ ] **Step 1: Search for all callers of `clampHorizontally` across the whole app module**

Run: `grep -rn "clampHorizontally" app/src/main app/src/test app/src/androidTest`

Expected: only the definition in `CalloutPlacement.kt` and its own tests in `CalloutPlacementTest.kt` — confirming no production call site exists (the arrow was its only consumer, and Task 3 already removed the arrow).

- [ ] **Step 2a: If step 1 confirms no other caller — remove `clampHorizontally` and its tests**

Delete from `CalloutPlacement.kt`:

```kotlin
/**
 * Clamp [desired] so a [width]-wide box stays within `0..screenWidth`, inset by [margin].
 *
 * Used to keep the callout and its arrow on screen when the target sits near an edge. When the
 * available width is smaller than the box itself the box is pinned to the left margin rather than
 * being given a negative position: overflowing one edge is recoverable, and starting off-screen is
 * not.
 */
fun clampHorizontally(desired: Float, width: Float, screenWidth: Float, margin: Float): Float {
    val max = screenWidth - margin - width
    if (max <= margin) return margin
    return desired.coerceIn(margin, max)
}
```

Delete from `CalloutPlacementTest.kt` (added back in Task 2's rewrite must NOT have included these — if Task 2 was followed exactly, these two tests do not exist in the file at this point, since the Task 2 rewrite already omitted them; this step is a no-op confirmation in that case). If for any reason they are still present, delete:

```kotlin
    @Test
    fun `horizontal clamping keeps a box on screen at either edge`() { ... }

    @Test
    fun `a box wider than the screen is pinned to the margin rather than given a negative position`() { ... }
```

- [ ] **Step 2b: If step 1 finds a real caller — stop, do not delete, and note it in the commit message instead**

If any production code outside `CalloutPlacement.kt`/`CalloutPlacementTest.kt` calls `clampHorizontally`, leave the function in place untouched and record in the commit message which call site still needs it and why. (This is not expected given the design, but AGENTS.md requires verifying reachability rather than assuming.)

- [ ] **Step 3: Run the JVM test suite for this package**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.onboarding.CalloutPlacementTest"`
Expected: PASS, 8 cases (unchanged from Task 2's step 4, since Task 2's rewrite did not include the clamp tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/CalloutPlacement.kt
git commit -m "Remove clampHorizontally: dead code after the arrow was removed

Confirmed by search — its only caller was the now-deleted arrow
positioning code in TutorialOverlay.kt."
```

(If step 2b applied instead, skip this commit and note the finding for the next task's author.)

---

### Task 5: Rebuild `OnboardingScreen.kt` — measured placement, tap-anywhere surface, no Back, no dot row

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Consumes: `calloutSideFor(spotlightTop, spotlightBottom, screenHeight, cardHeight, safeAreaBottomInset = 0f): CalloutSide` (Task 2), `TutorialSpotlightDecoration(spotlight, accent, cornerRadius, strokeWidth, modifier)` (Task 3), `OnboardingViewModel` with no `previous()` (Task 1).
- Produces: `OnboardingScreen(stepIndex: Int, mode: TutorialMode, onNext: () -> Unit, onExit: () -> Unit, completionError: String? = null, busy: Boolean = false)` — **signature changes**: `onPrevious: () -> Unit` parameter is **removed**. `CalloutCard` is now `internal`-visible-in-file only, drops `canGoBack`/`onPrevious`/the dot row, and is measured via `SubcomposeLayout` before being placed. `TUTORIAL_BACK_TAG` and `TUTORIAL_PROGRESS_TAG` constants are deleted. Task 6 (strings) and Task 8 (tests) depend on this new signature and these deleted tags.

This is the largest task in the plan; it is kept as one task because the tap surface, the measured placement, and the Back/dot-row removal are not independently testable slices of this one composable — an instrumented test can only exercise the whole rendered screen, not a partial rewrite of it, so splitting further would leave intermediate steps in a non-compiling or untestable state.

- [ ] **Step 1: Replace `OnboardingScreen.kt` in full**

```kotlin
package app.justthecarbs.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space

/** Stable handles for instrumented tests. */
const val TUTORIAL_OVERLAY_TAG = "tutorial_overlay"
const val TUTORIAL_TITLE_TAG = "tutorial_title"
const val TUTORIAL_BODY_TAG = "tutorial_body"
const val TUTORIAL_PRIMARY_TAG = "tutorial_primary"
const val TUTORIAL_SKIP_TAG = "tutorial_skip"
const val TUTORIAL_TAP_SURFACE_TAG = "tutorial_tap_surface"

/** How much larger than its control the spotlight is drawn. */
private val SPOTLIGHT_PADDING = 10.dp

/**
 * Generous on purpose. A tight radius makes the hole read as a rectangle cut out of the dim; at this
 * size it reads as light falling on the control. Paired with the feathered edge and graded dim in
 * [TutorialScrim] — all three exist to stop the overlay looking like a box on top of the app.
 */
private val SPOTLIGHT_RADIUS = 28.dp
private val SPOTLIGHT_STROKE = 2.dp

/**
 * The first-launch tutorial: a full-screen walkthrough over a deterministic preview of the app.
 *
 * Tapping almost anywhere on screen advances to the next step — the highlighted control is visual
 * context, never a precision target the user must aim for. Skip is the one deliberate exception: it
 * sits above the tap-catching surface in composition order, so Compose's own pointer-input dispatch
 * gives it first refusal on any tap that lands on it, structurally, before that tap could ever reach
 * the "advance" surface underneath.
 *
 * Replaces the previous three-slide carousel. The carousel described the app in the abstract; this
 * points at the controls the user is about to use, using their real labels, over a rendering of the
 * screens they appear on.
 *
 * Nothing behind the scrim is real. [TutorialBackdropContent] draws constants — see its own
 * documentation for why that is a structural guarantee rather than a convention.
 *
 * @param onExit leaves the tutorial. What that means is the caller's business and differs by mode:
 *   Home on first run, Settings on replay. This screen never decides its own destination.
 */
@Composable
fun OnboardingScreen(
    stepIndex: Int,
    mode: TutorialMode,
    onNext: () -> Unit,
    onExit: () -> Unit,
    completionError: String? = null,
    busy: Boolean = false,
) {
    val step = TUTORIAL_STEPS[stepIndex.coerceIn(0, TUTORIAL_LAST_STEP)]
    val last = stepIndex >= TUTORIAL_LAST_STEP
    val anchors = rememberTutorialAnchors()

    // System Back is an explicit exit, exactly like Skip.
    BackHandler(enabled = !busy) { onExit() }

    // A stale rectangle from the previous preview must not be pointed at while the new backdrop is
    // still being laid out — that is the one situation where an anchor exists but describes the
    // wrong screen. Cleared on backdrop change, so the overlay falls back to a centred callout for
    // the frame or two before the new controls report their bounds.
    LaunchedEffect(step.backdrop) { anchors.clear() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { SPOTLIGHT_PADDING.toPx() }
        val safeAreaBottomPx = with(density) {
            WindowInsets.systemBars.asPaddingValues(density).calculateBottomPadding().toPx()
        }

        val rawBounds = if (step.anchor == TutorialAnchor.NONE) null else anchors.boundsOf(step.anchor)
        val target: Rect? = rawBounds?.let { inflateWithin(it, paddingPx, widthPx, heightPx) }

        // The spotlight travels between steps instead of jumping.
        //
        // Only between two *known* targets, and that restriction is the whole of the safety
        // argument: animating out of or into null would slide the hole from the screen's origin, or
        // leave it briefly over a control the current step is not talking about. So an appearing or
        // disappearing target snaps, and only a move from one real rectangle to another is animated.
        //
        // Interpolated per edge rather than as a centre plus a size, so a target that changes shape
        // as well as position (a wide card to a small icon) stays a rectangle throughout.
        val previous = remember { mutableStateOf<Rect?>(null) }
        val progress = remember { Animatable(1f) }
        val from = previous.value

        LaunchedEffect(target) {
            val start = previous.value
            if (target != null && start != null && start != target) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(Motion.STANDARD_MS))
            } else {
                progress.snapTo(1f)
            }
            previous.value = target
        }

        val spotlight: Rect? = when {
            target == null -> null
            from == null || progress.value >= 1f -> target
            else -> lerpRect(from, target, progress.value)
        }

        // The preview, then the scrim over it, then the ring, then the controls. The whole backdrop
        // is marked decorative: while the tutorial is up, its controls must not be separately
        // reachable by TalkBack, or the user could tab to a "Scan barcode" card that does nothing.
        TutorialBackdropContent(
            backdrop = step.backdrop,
            anchors = anchors,
            modifier = Modifier.clearAndSetSemantics { },
        )

        // Fades in once, on arrival.
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scrimAlpha.animateTo(1f, tween(Motion.STANDARD_MS))
        }

        TutorialScrim(
            spotlight = spotlight,
            cornerRadius = SPOTLIGHT_RADIUS,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = BASE_SCRIM * scrimAlpha.value),
            modifier = Modifier.fillMaxSize(),
        )

        val accent = MaterialTheme.colorScheme.primary

        TutorialSpotlightDecoration(
            spotlight = spotlight,
            accent = accent,
            cornerRadius = SPOTLIGHT_RADIUS,
            strokeWidth = SPOTLIGHT_STROKE,
            modifier = Modifier.fillMaxSize(),
        )

        // The dedicated tap-anywhere surface. It sits ABOVE the backdrop/scrim/spotlight and BELOW
        // the callout card and Skip in composition order — that ordering is the entire correctness
        // argument. Because Skip and the card render afterward (i.e. on top), Compose gives their
        // own pointer-input regions first refusal on a tap that lands on them; only a tap that
        // reaches neither falls through to this box and calls onNext/onFinish. There is no manual
        // "did this tap hit Skip's bounds" check anywhere in this file.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TUTORIAL_TAP_SURFACE_TAG)
                .pointerInput(last, busy) {
                    detectTapGestures {
                        if (!busy) {
                            if (last) onExit() else onNext()
                        }
                    }
                }
                // Decorative: the tap surface has no meaning of its own to announce. TalkBack
                // reaches "advance" through Skip and the primary button inside the card instead,
                // exactly as it did before this redesign.
                .clearAndSetSemantics { },
        )

        val tutorialLabel = stringResource(R.string.tutorial_pane_title)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .testTag(TUTORIAL_OVERLAY_TAG)
                .semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                    paneTitle = tutorialLabel
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screenEdge),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onExit,
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(TUTORIAL_SKIP_TAG),
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // AnimatedContent cross-fades the words while the card itself stays put.
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    (fadeIn(tween(Motion.STANDARD_MS)) +
                        slideInVertically(tween(Motion.STANDARD_MS)) { it / 8 })
                        .togetherWith(fadeOut(tween(Motion.QUICK_MS)))
                },
                label = "tutorialCallout",
            ) { index ->
                val animated = TUTORIAL_STEPS[index.coerceIn(0, TUTORIAL_LAST_STEP)]
                val cardLast = index >= TUTORIAL_LAST_STEP
                val title = stringResource(animated.titleRes)
                val body = stringResource(animated.bodyRes)
                val progressLabel = stringResource(R.string.tutorial_progress, index + 1, TUTORIAL_STEPS.size)

                // Measures the real CalloutCard once (with real strings, real font scale) to decide
                // bottom vs. top, then places it — a single extra measure pass around content that
                // already exists, not a second placement engine. See CalloutPlacement.kt.
                SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
                    val cardConstraints = constraints.copy(minHeight = 0)
                    val measured = subcompose("card") {
                        CalloutCard(
                            title = title,
                            body = body,
                            progressLabel = progressLabel,
                            last = cardLast,
                            busy = busy,
                            completionError = completionError,
                            onNext = onNext,
                            onFinish = onExit,
                            modifier = Modifier.padding(horizontal = Space.screenEdge),
                        )
                    }.first().measure(cardConstraints)

                    val side = if (spotlight == null) {
                        CalloutSide.CENTERED
                    } else {
                        calloutSideFor(
                            spotlightTop = spotlight.top,
                            spotlightBottom = spotlight.bottom,
                            screenHeight = heightPx,
                            cardHeight = measured.height.toFloat(),
                            safeAreaBottomInset = safeAreaBottomPx,
                        )
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val y = when (side) {
                            CalloutSide.BELOW -> constraints.maxHeight - measured.height
                            CalloutSide.ABOVE -> 0
                            CalloutSide.CENTERED -> (constraints.maxHeight - measured.height) / 2
                        }
                        measured.place(0, y)
                    }
                }
            }
        }
    }
}

/**
 * The instructional card: title, one sentence, step count, and the primary action.
 *
 * The title and body are one polite live region, so a step change is announced as a single sentence
 * rather than as two separate interruptions — and politely, so it waits for whatever the user is
 * already hearing rather than cutting across it.
 *
 * Deliberately has no `clickable` of its own on its background: a tap on the card's background (but
 * not on the primary button) falls through to the tap-anywhere surface beneath it in exactly the
 * same way a tap on open scrim does. Only the primary button intercepts a tap ahead of that surface.
 */
@Composable
private fun CalloutCard(
    title: String,
    body: String,
    progressLabel: String,
    last: Boolean,
    busy: Boolean,
    completionError: String?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CALLOUT_RADIUS)
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(CALLOUT_ELEVATION, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(SPINE_WIDTH)
                .fillMaxHeight()
                .background(accent),
        )

        Column(
            modifier = Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$title. $body"
                },
            ) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(TUTORIAL_TITLE_TAG),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.testTag(TUTORIAL_BODY_TAG),
                )
            }

            if (completionError != null) {
                Text(
                    text = stringResource(R.string.tutorial_completion_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = if (last) onFinish else onNext,
                    enabled = !busy,
                    shape = RoundedCornerShape(Space.buttonRadius),
                    modifier = Modifier
                        .heightIn(min = Space.minTouchTarget)
                        .testTag(TUTORIAL_PRIMARY_TAG),
                ) {
                    Text(
                        text = stringResource(
                            if (last) R.string.tutorial_finish else R.string.tutorial_next,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * The card's own radius, larger than [Space.cardRadius].
 *
 * The callout is the one surface floating over a dimmed app rather than sitting in a list with
 * other cards, so it can afford a softer corner than the app's ordinary card idiom.
 */
private val CALLOUT_RADIUS = 26.dp
private val CALLOUT_ELEVATION = 12.dp
private val SPINE_WIDTH = 4.dp

/**
 * The dim immediately around the target.
 *
 * Well below the 0.78 this screen used to apply everywhere: at that strength the app underneath was
 * effectively gone, which is the opposite of what a tutorial pointing at real controls wants. The
 * far edges still reach a comparable weight through [TutorialScrim]'s radial gradient.
 */
private const val BASE_SCRIM = 0.42f

/**
 * Interpolate between two rectangles, edge by edge.
 *
 * Per-edge rather than centre-plus-size so a target that changes shape as well as position stays a
 * rectangle for every frame in between, instead of scaling through an intermediate aspect ratio.
 */
private fun lerpRect(from: Rect, to: Rect, fraction: Float): Rect = Rect(
    left = from.left + (to.left - from.left) * fraction,
    top = from.top + (to.top - from.top) * fraction,
    right = from.right + (to.right - from.right) * fraction,
    bottom = from.bottom + (to.bottom - from.bottom) * fraction,
)
```

Notes on deliberate choices baked into this rewrite, so the next reader does not "fix" them:
- The tap surface's `pointerInput` key is `(last, busy)` — recreating the gesture detector when either changes is correct and cheap (there is no in-flight gesture state worth preserving across a step change, since a tap either completes within one step or doesn't count).
- `Modifier.clearAndSetSemantics { }` on the tap surface matches the existing idiom already used on the backdrop and scrim in this same file — it must not become independently reachable by TalkBack, since its entire purpose is to be a silent catch-all behind the real controls.
- The primary button inside `CalloutCard` still exists and still works after this change — a sighted user's habitual tap on it still advances, and a TalkBack user reaches "advance" *only* through it and Skip, since the full-screen tap surface is marked decorative. This preserves accessibility parity: TalkBack users are not asked to tap an unlabeled arbitrary point on screen.
- `Row` around the primary button now uses `Arrangement.End` instead of a `Spacer(Modifier.weight(1f))` before a (now nonexistent) Back button — this is the direct, minimal consequence of removing Back, not a broader layout change.

- [ ] **Step 2: Compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Lint check**

Run: `.\gradlew.bat :app:lintDebug`
Expected: exit 0. Read the report for any new finding in `OnboardingScreen.kt` specifically (an unused-parameter or unused-import warning would indicate a leftover from the rewrite); pre-existing findings in other files are out of scope for this task.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingScreen.kt
git commit -m "Rebuild OnboardingScreen around tap-anywhere and measured placement

- Full-screen tap-catching Box added below the callout card and Skip
  in composition order, so Skip structurally always wins hit-testing.
- Callout placement now uses SubcomposeLayout to measure the real
  CalloutCard before choosing bottom (default) or top (fallback).
- Back button, canGoBack, onPrevious parameter, and the dot-row
  progress indicator are all removed. Only the accessible
  'Step N of M' text line remains.
- Halo/pulse/arrow wiring removed to match TutorialSpotlightDecoration's
  simplified signature."
```

---

### Task 6: Tighten tutorial copy and update final-step wording

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: no code-level interface change — string *content* only. `tutorial_next`/`tutorial_finish` keys are unchanged (still referenced by `OnboardingScreen.kt`'s `CalloutCard`); `tutorial_back` becomes unreferenced by any Kotlin code after Task 5 but is left in `strings.xml` (an orphaned string is a lint advisory, not a functional issue, and this plan does not expand into a lint-cleanup pass beyond what this redesign itself causes — Task 9's lint run will surface it and it can be removed there if flagged).

Per the spec: each of the 6 steps keeps one short title + one sentence; tighten any step currently running two sentences to one. Reviewing the current strings (read in Task 0 exploration): `tutorial_body_label` ("Use Scan nutrition label to read the Total carbohydrate row from the package. If anything is unclear, you stay in control.") and `tutorial_body_total` ("Keep adding foods. Meal Total combines the current meal. Remove an item or clear it when you're done.") are both two-or-more sentences and are the ones to tighten. `tutorial_finish`'s current text ("Start using Just the Carbs") does not read as inviting a tap on an already-full-screen tap target — per the spec's "Tap anywhere to finish" final-step copy change, this needs new wording that reads correctly both as the button label (still shown, for the sighted/TalkBack path) and implicitly for the tap-anywhere behavior.

- [ ] **Step 1: Edit `strings.xml`**

Change line 693 (`tutorial_finish`):

```xml
<string name="tutorial_finish" tools:ignore="MissingTranslation">Start using Just the Carbs</string>
```
to:
```xml
<string name="tutorial_finish" tools:ignore="MissingTranslation">Got it — start using Just the Carbs</string>
```

Change line 705 (`tutorial_body_label`):

```xml
<string name="tutorial_body_label" tools:ignore="MissingTranslation">Use Scan nutrition label to read the Total carbohydrate row from the package. If anything is unclear, you stay in control.</string>
```
to:
```xml
<string name="tutorial_body_label" tools:ignore="MissingTranslation">Use Scan nutrition label to read the Total carbohydrate row straight from the package.</string>
```

Change line 709 (`tutorial_body_total`):

```xml
<string name="tutorial_body_total" tools:ignore="MissingTranslation">Keep adding foods. Meal Total combines the current meal. Remove an item or clear it when you\'re done.</string>
```
to:
```xml
<string name="tutorial_body_total" tools:ignore="MissingTranslation">Meal Total combines everything you\'ve added so far.</string>
```

Leave every other `tutorial_*` string (lines 690-722) unchanged — they are already one short sentence or a title fragment, matching the spec's "no new steps, no removed steps" and "tighten any step running two sentences where one suffices" (only these three did).

- [ ] **Step 2: Build check (string resources are validated at compile time via `aapt`)**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (a malformed string resource — e.g. an unescaped apostrophe — fails this step; note the existing file already correctly escapes apostrophes with `\'`, matching the pattern used in `tutorial_body_total`'s edit above).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "Tighten tutorial copy to one sentence per step

tutorial_body_label and tutorial_body_total each dropped a trailing
second sentence. tutorial_finish now reads correctly as an action to
tap on a tap-anywhere screen."
```

---

### Task 7: Update `TutorialPreview.kt` if it references removed parameters

**Files:**
- Modify (conditionally): `app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks directly (the backdrop previews render independently of `OnboardingScreen`'s own signature) — but if this file contains `@Preview`-annotated composables that call `OnboardingScreen`, `TutorialSpotlightDecoration`, or `CalloutCard` directly with the old parameter lists (`onPrevious`, `arrowStart`, `pulse`, `canGoBack`), those call sites will fail to compile after Tasks 1, 3, and 5.
- Produces: nothing new; this task only removes references to deleted parameters if present.

This task's existence is conditional because the file's exact preview contents were summarized but not fully re-read line-by-line in this planning session as a large file. The step below has the implementer check first, per AGENTS.md's instruction not to assume.

- [ ] **Step 1: Search for stale references**

Run: `grep -n "onPrevious\|arrowStart\|pulse\|canGoBack\|TUTORIAL_BACK_TAG\|TUTORIAL_PROGRESS_TAG" app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt`

- [ ] **Step 2a: If step 1 finds matches — remove them**

For any `@Preview` composable found calling `OnboardingScreen(...)` with an `onPrevious = ...` argument, delete that named argument (the parameter no longer exists on `OnboardingScreen` after Task 5). For any manual preview composition of `TutorialSpotlightDecoration(...)` passing `arrowStart`/`pulse`, delete those named arguments (removed in Task 3). If any preview references `TUTORIAL_BACK_TAG` or `TUTORIAL_PROGRESS_TAG` for a semantics/test-tag assertion inside a preview (unusual but possible), delete that reference — both constants no longer exist after Task 5.

- [ ] **Step 2b: If step 1 finds no matches — no code change needed, note it and move on**

Record in the task's completion notes that `TutorialPreview.kt` required no changes, confirmed by search rather than assumed.

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (only if Step 2a made changes)**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt
git commit -m "Update TutorialPreview.kt for the removed Back/arrow/pulse/dot-row parameters

Confirmed by search which preview call sites referenced OnboardingScreen's
removed onPrevious parameter or TutorialSpotlightDecoration's removed
arrowStart/pulse parameters, and removed those arguments."
```

If Step 2b applied, skip the commit — there is nothing to commit for this task.

---

### Task 8: Rewrite `TutorialScreenTest.kt` around the tap-anywhere contract

**Files:**
- Modify: `app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialScreenTest.kt`

**Interfaces:**
- Consumes: `OnboardingScreen(stepIndex, mode, onNext, onExit, completionError, busy)` (Task 5's new signature — no `onPrevious`), `TUTORIAL_TAP_SURFACE_TAG` (Task 5), and the deletion of `TUTORIAL_BACK_TAG`/`TUTORIAL_PROGRESS_TAG` (Task 5).
- Produces: nothing consumed by later tasks — this is the last file in the dependency chain for the screen itself.

**Instrumented — needs a device or emulator.** Per AGENTS.md: do not claim these pass without actually running them on the `carbscan` AVD (or equivalent) and reading the JUnit XML / instrumentation status codes, not just the wrapper's exit code (CLAUDE.md documents this codebase's history of a misleading `exit 0` on a build that Gradle itself reported `BUILD FAILED` for the whole-suite case — confirm this per-class run to avoid that trap).

- [ ] **Step 1: Replace `TutorialScreenTest.kt` in full**

```kotlin
package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.ui.onboarding.OnboardingScreen
import app.justthecarbs.ui.onboarding.TUTORIAL_BODY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_LAST_STEP
import app.justthecarbs.ui.onboarding.TUTORIAL_OVERLAY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_PRIMARY_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_SKIP_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_STEPS
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_SURFACE_TAG
import app.justthecarbs.ui.onboarding.TUTORIAL_TITLE_TAG
import app.justthecarbs.ui.onboarding.TutorialMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The tutorial's rendered behaviour under the tap-anywhere contract: a tap almost anywhere advances
 * one step; Skip is the one deliberate exception and always wins hit-testing on its own bounds; the
 * final step's tap finishes instead of advancing.
 *
 * **Instrumented: needs a device or emulator.** The step machine is covered by pure JVM tests
 * ([app.justthecarbs.ui.onboarding.TutorialStepTest]); this class covers what only a real
 * composition can answer.
 *
 * Copy is read from resources rather than hardcoded, so a wording change does not fail these tests
 * for the wrong reason.
 */
class TutorialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Drives the real screen with real state, so a tap actually moves it.
     */
    private fun showTutorial(
        mode: TutorialMode = TutorialMode.FIRST_RUN,
        theme: ThemeChoice = ThemeChoice.LIGHT,
        startStep: Int = 0,
        onExit: () -> Unit = {},
    ) {
        compose.setContent {
            var step by remember { mutableStateOf(startStep) }
            JustTheCarbsTheme(themeChoice = theme) {
                Box(Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = mode,
                        onNext = { step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onExit = onExit,
                    )
                }
            }
        }
    }

    private fun tapTapSurface() {
        compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click() }
        compose.waitForIdle()
    }

    @Test
    fun theTutorialOpensOnItsFirstStep() {
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
    }

    @Test
    fun tappingTheFullScreenSurfaceAdvancesExactlyOneStep() {
        // The core contract: a tap on open scrim -- not on any specific control -- advances.
        showTutorial()

        tapTapSurface()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[1].titleRes))
    }

    @Test
    fun repeatedTapsWalkTheWholeSequenceAndAllSixStepsCopyResolves() {
        showTutorial()

        TUTORIAL_STEPS.forEachIndexed { index, step ->
            compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertTextEquals(string(step.titleRes))
            compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true).assertTextEquals(string(step.bodyRes))
            if (index != TUTORIAL_LAST_STEP) {
                tapTapSurface()
            }
        }
    }

    @Test
    fun theTeachingStepsNameTheAppsOwnControls() {
        showTutorial(startStep = 2)
        compose.onNodeWithTag(TUTORIAL_BODY_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[2].bodyRes))
        compose.onNodeWithText(string(app.justthecarbs.R.string.home_search_label), substring = true)
            .assertExists()
    }

    @Test
    fun tappingTheCalloutCardBackgroundAlsoAdvances() {
        // The card has no clickable of its own on its background, so a tap there falls through to
        // the tap surface beneath it exactly like a tap on open scrim -- pinned here because it is
        // the specific case the spec calls out: the card must not need to reimplement "tap advances".
        showTutorial()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[1].titleRes))
    }

    @Test
    fun tappingSkipExitsWithoutAdvancingAndNeverBothFire() {
        // The load-bearing guarantee from the design: a tap landing on Skip's own bounds must exit,
        // and must NOT also advance the step -- the two must never both fire from one tap.
        var exits = 0
        var nextCalls = 0
        compose.setContent {
            var step by remember { mutableStateOf(0) }
            JustTheCarbsTheme {
                Box(Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        stepIndex = step,
                        mode = TutorialMode.FIRST_RUN,
                        onNext = { nextCalls++; step = (step + 1).coerceAtMost(TUTORIAL_LAST_STEP) },
                        onExit = { exits++ },
                    )
                }
            }
        }

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
        assertEquals(0, nextCalls)
    }

    @Test
    fun theFinalStepsTapFinishesInsteadOfAdvancing() {
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_finish)).assertIsDisplayed()
        tapTapSurface()

        assertEquals(1, exits)
    }

    @Test
    fun theFinalStepsPrimaryButtonAlsoFinishes() {
        // The button remains a working, labelled alternative to the tap-anywhere surface -- the
        // path a TalkBack user relies on, since the full-screen surface is decorative to them.
        var exits = 0
        showTutorial(startStep = TUTORIAL_LAST_STEP, onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun nextIsNotOfferedOnTheFinalStep() {
        showTutorial(startStep = TUTORIAL_LAST_STEP)

        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_next)).assertDoesNotExist()
    }

    @Test
    fun skipIsOfferedOnEveryStepAndExits() {
        var exits = 0
        showTutorial(onExit = { exits++ })

        TUTORIAL_STEPS.indices.forEach { index ->
            compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
            if (index != TUTORIAL_LAST_STEP) {
                tapTapSurface()
            }
        }

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
    }

    @Test
    fun skipExitsFromTheVeryFirstStep() {
        var exits = 0
        showTutorial(onExit = { exits++ })

        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
    }

    @Test
    fun everyTutorialControlMeetsTheTouchTargetFloor() {
        showTutorial(startStep = 1)

        listOf(TUTORIAL_PRIMARY_TAG, TUTORIAL_SKIP_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun backButtonNoLongerExists() {
        // Forward-only tour: there is no Back control anywhere in the tutorial now.
        showTutorial(startStep = 2)
        compose.onNodeWithText(string(app.justthecarbs.R.string.tutorial_back)).assertDoesNotExist()
    }

    @Test
    fun theStepCountTextIsExactlyStepOneOfSix() {
        // "Step 1 of 6" is the one remaining progress indicator, asserted as the literal rendered
        // string rather than merely "a text node exists" -- tutorial_progress is "Step %1$d of %2$d"
        // and TUTORIAL_STEPS.size is 6 (both confirmed in strings.xml / TutorialStep.kt), so this
        // proves the words-only replacement for the dot row actually renders, not just that some
        // node is present.
        showTutorial(startStep = 0)
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
    }

    @Test
    fun theTutorialRendersInDarkThemeToo() {
        showTutorial(theme = ThemeChoice.DARK)

        compose.onNodeWithTag(TUTORIAL_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).assertIsDisplayed()
    }

    @Test
    fun replayModeRendersTheSameStepsAsFirstRun() {
        showTutorial(mode = TutorialMode.REPLAY)

        compose.onNodeWithTag(TUTORIAL_TITLE_TAG, useUnmergedTree = true)
            .assertTextEquals(string(TUTORIAL_STEPS[0].titleRes))
        compose.onNodeWithTag(TUTORIAL_SKIP_TAG).assertIsDisplayed()
    }
}
```

`theStepCountTextIsExactlyStepOneOfSix` hardcodes the expected English string deliberately:
`tutorial_progress` is `"Step %1$d of %2$d"` (confirmed in `strings.xml`) and `TUTORIAL_STEPS.size` is
6 (confirmed in `TutorialStep.kt`) — both are stable, checked facts, not guesses, and asserting the
literal rendered text is what proves the dot row's replacement (words alone) actually renders, rather
than merely that *a* text node exists.

- [ ] **Step 2: Run the test class on a connected device/emulator**

Run:
```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:connectedDebugAndroidTest --tests "app.justthecarbs.ui.TutorialScreenTest"
```

Expected: all cases pass. Confirm by reading `app/build/outputs/androidTest-results/connected/<device>/TEST-app.justthecarbs.ui.TutorialScreenTest.xml` for `failures="0" errors="0"`, per this codebase's documented practice of not trusting the wrapper's bare exit code alone.

If the AVD is not already running, start it first per AGENTS.md/CLAUDE.md's documented emulator launch command before running the test.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialScreenTest.kt
git commit -m "Rewrite TutorialScreenTest around the tap-anywhere contract

Replaces button-click-driven step walks with taps on the full-screen
surface. Adds explicit coverage that Skip exits without also advancing
(the load-bearing hit-testing guarantee), that the card background
falls through to the tap surface, and that Back/dot-row are gone."
```

---

### Task 9: Update `TutorialNavigationTest.kt` for the new tap surface and signature

**Files:**
- Modify: `app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt`

**Interfaces:**
- Consumes: `OnboardingScreen(stepIndex, mode, onNext, onExit, completionError, busy)` (Task 5), `TUTORIAL_TAP_SURFACE_TAG` (Task 5).
- Produces: nothing consumed elsewhere; this is a routing/persistence test only.

Per the spec: only the two `TUTORIAL_PRIMARY_TAG`-repeated-click walks change to a generic tap; routing/persistence assertions are otherwise unaffected. This also requires removing the `onPrevious = {}` stub added in Task 1 (now that `OnboardingScreen` no longer has that parameter at all) and the now-unused `TUTORIAL_PRIMARY_TAG` import if nothing else in the file references it after the rewrite (checked below).

- [ ] **Step 1: Update the `TestGraph`'s `OnboardingScreen` call site**

Find (as left by Task 1, Step 2):

```kotlin
                OnboardingScreen(
                    stepIndex = stepIndex,
                    mode = mode,
                    onNext = viewModel::next,
                    onPrevious = {},
                    onExit = { exitScope.launch { viewModel.finish() } },
                    busy = completion is OnboardingViewModel.CompletionState.Saving,
                )
```

Replace with:

```kotlin
                OnboardingScreen(
                    stepIndex = stepIndex,
                    mode = mode,
                    onNext = viewModel::next,
                    onExit = { exitScope.launch { viewModel.finish() } },
                    busy = completion is OnboardingViewModel.CompletionState.Saving,
                )
```

- [ ] **Step 2: Update `finishingTheTutorialPersistsItAndReturnsToHome` to tap instead of clicking the primary button repeatedly**

Find:

```kotlin
    @Test
    fun finishingTheTutorialPersistsItAndReturnsToHome() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)
        compose.onNodeWithText("Show me").performClick()
        compose.waitForIdle()

        repeat(TUTORIAL_LAST_STEP + 1) {
            compose.onNodeWithTag(TUTORIAL_PRIMARY_TAG).performClick()
            compose.waitForIdle()
        }

        assertEquals("home", route())
        assertTrue(runBlocking { repository.settings.first().hasSeenTutorial })
    }
```

Replace with:

```kotlin
    @Test
    fun finishingTheTutorialPersistsItAndReturnsToHome() {
        val repository = freshRepository()
        runBlocking { repository.recordLaunch() }
        start(repository)
        compose.onNodeWithText("Show me").performClick()
        compose.waitForIdle()

        repeat(TUTORIAL_LAST_STEP + 1) {
            compose.onNodeWithTag(TUTORIAL_TAP_SURFACE_TAG).performTouchInput { click() }
            compose.waitForIdle()
        }

        assertEquals("home", route())
        assertTrue(runBlocking { repository.settings.first().hasSeenTutorial })
    }
```

- [ ] **Step 3: Confirm whether `skippingTheTutorialPersistsItAndReturnsToHome` needs a change**

Re-read that test — it only ever uses `TUTORIAL_SKIP_TAG`, never `TUTORIAL_PRIMARY_TAG`, so per the spec ("only its two `TUTORIAL_PRIMARY_TAG`-repeated-click walks changed") it needs no edit. Confirm by search that `everyStepShowsItsOwnTitleAndBody`-style walks do not also exist in this file — they do not; that pattern lives in `TutorialScreenTest.kt`, already handled in Task 8. Leave `skippingTheTutorialPersistsItAndReturnsToHome` untouched.

- [ ] **Step 4: Update imports**

Add:
```kotlin
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import app.justthecarbs.ui.onboarding.TUTORIAL_TAP_SURFACE_TAG
```

Remove `import app.justthecarbs.ui.onboarding.TUTORIAL_PRIMARY_TAG` only if, after Step 2's edit, no test in this file references `TUTORIAL_PRIMARY_TAG` anymore — confirm by search:

Run: `grep -n "TUTORIAL_PRIMARY_TAG" app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt`

If the only remaining match is the import line itself, remove that import line.

- [ ] **Step 5: Run the test class on a connected device/emulator**

Run:
```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:connectedDebugAndroidTest --tests "app.justthecarbs.ui.TutorialNavigationTest"
```

Expected: all cases pass. Confirm via the JUnit XML at `app/build/outputs/androidTest-results/connected/<device>/TEST-app.justthecarbs.ui.TutorialNavigationTest.xml`.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/kotlin/app/justthecarbs/ui/TutorialNavigationTest.kt
git commit -m "Update TutorialNavigationTest for the removed onPrevious parameter and tap surface

The finish-walk test now taps the full-screen tap surface instead of
repeatedly clicking the removed primary-button-click pattern. Routing
and persistence assertions are unchanged."
```

---

### Task 10: Full verification pass

**Files:** none modified — this task only runs checks and records results.

**Interfaces:** N/A.

Per `docs/agent-instructions/verification.md` and AGENTS.md's explicit requirement not to reuse historical passing results as evidence for this change, and per CLAUDE.md's own repeated warnings about misleading `exit 0` results on this codebase's Windows/Gradle setup.

- [ ] **Step 1: Full JVM unit test suite**

```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL. Count failures/errors/skipped from the JUnit XML under `app/build/test-results/testDebugUnitTest/`, not merely the console summary, per this codebase's documented practice.

- [ ] **Step 2: Lint**

```powershell
.\gradlew.bat :app:lintDebug
```

Expected: exit 0. Read `app/build/reports/lint-results-debug.html` for any new finding introduced by this change specifically (an orphaned `tutorial_back` string resource is the one plausible new advisory from Task 6's note — if lint flags it, remove the unused string in this task rather than leaving it, since Task 6 deliberately deferred that decision to this verification step).

- [ ] **Step 3: Debug APK build**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Full instrumented suite for the touched classes, run three times per this codebase's documented soft-keyboard/IME flake precaution**

CLAUDE.md documents that `MealScreenTest`/`ProductScreenTest` needed repeated runs to catch IME-related flakiness; the tutorial screens do not open a soft keyboard (no text input anywhere in the tutorial), so a single clean run is the primary bar, but run twice to catch any ordering-dependent flake introduced by the new `SubcomposeLayout`/tap-surface interaction, which is new machinery this codebase has not exercised before:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "app.justthecarbs.ui.TutorialScreenTest" --tests "app.justthecarbs.ui.TutorialNavigationTest"
```

Run this command twice. Expected: all cases pass both times, confirmed via JUnit XML each time (not the wrapper exit code alone).

- [ ] **Step 5: Confirm no other test file references now-deleted symbols**

```
grep -rn "TUTORIAL_BACK_TAG\|TUTORIAL_PROGRESS_TAG\|onPrevious\b" app/src/main app/src/test app/src/androidTest
```

Expected: no matches anywhere in the app module (the `OnboardingViewModel.previous()` method and all its call sites, the `TUTORIAL_BACK_TAG`/`TUTORIAL_PROGRESS_TAG` constants, and every `onPrevious` parameter/argument, must be fully gone — a leftover reference anywhere would either fail to compile already, or (worse) compile against a stale, unrelated `onPrevious` if such a name were accidentally reintroduced elsewhere; this is a final sweep, not a first check).

- [ ] **Step 6: Record results and remaining physical-QA gap**

Per the original brief's requested report format and AGENTS.md's "report what changed, which checks actually ran, what remains unverified" rule — this step produces the summary to hand back to the user, not a code change. State explicitly: the `SubcomposeLayout` measured-placement path and the tap-anywhere hit-testing guarantee have not been observed on physical hardware; the emulator/JVM checks above confirm behavior and layering order, not real-finger touch-target feel or any device-specific IME/keyboard interaction (there is none in this screen, but this should still be stated rather than assumed). This matches CLAUDE.md's established convention of separating "verified by test" from "verified on a device" for every UI change in this codebase.

No commit for this task — it is a verification-and-reporting step.

---

## Self-Review Notes

**Spec coverage check:** Tap-anywhere-as-sibling-Box (Task 5), Skip wins hit-testing structurally (Task 5, tested in Task 8), Back removed (Tasks 1, 5, 8), dot-row removed / text-only progress kept (Task 5, tested in Task 8), spotlight simplified to one static ring (Task 3), callout placement bottom-default with measured-geometry fallback via `SubcomposeLayout` (Tasks 2, 5, tested in Task 2), narrow-width/large-font consideration (addressed by using real measured height rather than an estimate at all — the measured path makes a dedicated narrow-width/large-font *test* less critical than in the estimate-fallback case, but Task 2's test suite does cover the geometry boundary condition the fallback would also need), copy audit (Task 6), non-goals preserved (verified by not touching `TutorialAnchors.kt`, `TutorialReminder.kt`, navigation graph, or persistence semantics in any task), testing section fully covered (Tasks 2, 8, 9), preview updates (Task 7).

**Placeholder scan:** Task 8's first draft contained a placeholder-shaped assertion (`""` substring) inside the plan text itself — caught and replaced with a concrete hardcoded assertion before finalizing the task; the final task instructs the implementer to use only the corrected version.

**Type consistency check:** `calloutSideFor`'s signature (`spotlightTop, spotlightBottom, screenHeight, cardHeight, safeAreaBottomInset`) is introduced once in Task 2 and used identically in Task 5's `SubcomposeLayout` call — no drift. `OnboardingScreen`'s new signature (no `onPrevious`) is introduced in Task 5 and consumed identically in Tasks 8 and 9. `TUTORIAL_TAP_SURFACE_TAG` is defined once in Task 5 and consumed identically in Tasks 8 and 9. `TutorialSpotlightDecoration`'s shortened signature is defined in Task 3 and consumed identically in Task 5.

**Gap acknowledged, not hidden:** Task 5's `SubcomposeLayout` approach is the spec's primary path; the spec's own fallback (isolated estimate-based heuristic, only if `SubcomposeLayout` proves impractical) is not written out as an alternate task here, since the plan's job is to implement the approved primary design, and the spec's fallback trigger condition ("incompatible with `AnimatedContent`'s cross-fade without disproportionate restructuring") is something the implementer of Task 5 can only discover while actually attempting it. If Task 5's implementer hits that wall, the correct response is to stop, report the specific incompatibility found, and get a decision before improvising a fallback that was not itself reviewed — not to silently substitute the old estimate-based heuristic.
