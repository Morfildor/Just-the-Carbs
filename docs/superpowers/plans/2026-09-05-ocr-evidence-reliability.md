# OCR Evidence Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the pre-shutter live-evidence lifecycle bug, make physical-observation provenance real, remove the one known wrong-prefill path, consolidate the scanner's final trust decision into one pure typed engine, and fix the onboarding-completion failure mode — all without weakening any existing safety invariant.

**Architecture:** A new pure `CaptureEvidenceCoordinator` separates two concepts `LabelScannerScreen.kt`'s single `AtomicLong captureSession` currently conflates: an **aim epoch** (which pre-shutter live-camera stream a frame belongs to) and a **work generation** (a cancellation token for in-flight async recognition work). `LiveEvidenceBuffer` gains an atomic `freezeAtShutter` snapshot taken before any state mutation, so live evidence survives the shutter regardless of how long the still pipeline subsequently takes. `RecognitionEvidence` construction sites are audited so every camera-derived instance carries a non-`UNKNOWN` `PhysicalObservationId`. `ReadingEligibility`'s corroboration branch stops treating same-photograph agreement as sufficient to *show* an `Unsupported`-scale value, closing the one documented wrong-prefill path. A new pure `ScanDecisionEngine` wraps the existing (correct) `EvidenceResolver` / `AutomaticVerification` / `ScanPresentationDecision` chain in one exhaustive sealed result so `LabelScannerScreen` executes rather than decides. Finally `OnboardingViewModel`/`JustTheCarbsNavHost` gain an observable completion state so a DataStore write failure cannot permanently disable the onboarding button.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition, JUnit4/JVM tests (no Robolectric — this repo's `domain`/`ocr` packages are pure Kotlin and tested on the JVM only; UI is tested with instrumented Compose tests), Gradle/AGP 9.3.1.

**Spec:** `just-the-carbs-ocr-architecture-review-2026-09-05.md` (the external architectural review this plan implements), corrected throughout by this plan's own verification pass (see "What the review got wrong" below). `.audits/architectural-analysis-2026-09-04.md` is corroborating ground truth already in the repo.

## What the review got wrong — read this before Task 1

The review's literal description of the P0 bug does **not** match the code, and Task 1 below fixes the *actual* defect, not the literal one. Do not re-derive this; it was verified by reading `LabelScannerScreen.kt` in full.

**The claimed mechanism:** "Live frames recorded under session N → shutter increments to N+1 → resolver asks for N+1 → all pre-shutter frames rejected."

**What the code actually does** (`LabelScannerScreen.kt`):
- `captureSession` is declared at line 366: `val captureSession = remember { java.util.concurrent.atomic.AtomicLong(0L) }`.
- `captureLabel()` increments it at **line 1130**, as the very first side effect of the shutter handler — before `focusThenCapture` (line 1155) is even called, i.e. before any capture I/O begins.
- The live analyzer callback (`onReading`, line 412) stamps `liveEvidence.record(result, System.currentTimeMillis(), captureSession.get())` — reading whatever the counter is *at the moment each frame arrives*.
- `readSelectedTable` (line 570) reads `val session = captureSession.get()` fresh, and this happens to be called only *after* `captureLabel()`'s increment in the normal flow, so it reads the *same* post-increment value that any frame arriving after the tap would also read.
- So there is no "N vs N+1" mismatch in the sense of two different call sites disagreeing about which counter value to use — every read after the shutter tap reads the same, already-incremented number.

**The actual defect:** the counter conflates two different questions, and it is the *conflation* that is the bug, not a wiring slip between two call sites:

1. **Aim epoch** — "which pre-shutter live-camera stream does this frame belong to?" This should stay constant while the user is aiming, so that a frame recorded at T-minus-800ms (while framing the shot) is still recognized as belonging to *this* capture attempt when the shutter fires at T-0.
2. **Work generation** — "is stale async work from a previous attempt still trying to write a result?" This correctly *should* bump on every new capture, exactly as it does today, so an in-flight recognition from an abandoned attempt cannot land.

Because one `AtomicLong` serves both roles, and it bumps at the *start* of `captureLabel()` (correct for role 2, wrong for role 1), every live frame recorded **before** the shutter tap — i.e. essentially all of them, since aiming necessarily happens before the tap — is stamped with the pre-tap value. The instant the tap increments the counter, `readSelectedTable`'s query (`asEvidence(now, session)` at line 590, using the post-increment `session`) filters `LiveEvidenceBuffer.stableConsensus` on `it.sessionId == sessionId` (`LiveEvidenceBuffer.kt:135`) — which **excludes every one of those pre-shutter frames**, because they carry the *old* sessionId. Only a frame that happens to land in the razor-thin window between the increment (`captureLabel.kt:1130`) and `analyzer.pause()` (`captureLabel.kt:1151`) — both on the main thread, microseconds apart — could ever be stamped with the new value. In practice this window retains functionally zero pre-shutter frames.

The class's own KDoc (`LiveEvidenceBuffer.kt:53-57`) documents this as intentional ("a live frame from session 3 can never corroborate session 4's still capture"), which means this is a **design conflation baked into the current architecture**, not an oversight in one call site. The fix (Task 1) is exactly the review's prescribed remedy — split `aimEpoch` from `workGeneration` — but the bug reproduction test in Task 1 Step 2 must assert the real mechanism (frames recorded *before* the shutter tap are excluded from the post-tap query, because the aim epoch and the work generation are the same counter and the counter bumps at tap time) rather than the review's literal "N used to record, N+1 used to query" framing, which a test written against the literal claim would fail to even construct (there is no code path where a stale N is queried — the bug is that the *fresh* value used to query excludes evidence stamped with the *previous* fresh value).

**The wrong-prefill mechanism is also narrower than the review implies.** `AutomaticScanAdvance.presentation()` (verified, `AutomaticScanAdvance.kt:415-460`) already gates auto-advance strictly: `mayAdvanceVerified` requires `ReadingEligibility.mayAdvanceWithoutConfirmation(scale)`, which requires `scale is ScaleAmbiguity.Verdict.Established` (`ReadingEligibility.kt:257-258`) — so an `Unsupported`-scale value **cannot** auto-advance today; that part of the review's fear is already false. The real gap is one level down: `ReadingEligibility.evaluate()`'s corroboration branch (`ReadingEligibility.kt:199-201`) still returns `Eligible` for an `Unsupported`-scale value when `corroborated` is true (same-photograph agreement across `FULL_FRAME_PASS_A`/`FILTERED_PASS_A`/`SELECTED_REGION_OCR` — none of which currently carries a distinct `PhysicalObservationId`, so "corroboration" here can only ever mean "views of one photograph agreed with each other"). `Eligible` feeds `mayConfirm` → `AutomaticScanAdvance.presentation` → `Presentation.ConfirmOnCapture` → `ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE`, which **does** prefill the digit for one-tap acceptance. This is the actual, narrow site of the `1.3→13` acceptance the review is worried about, and it is exactly what the file's own KDoc (`ReadingEligibility.kt:79-83`) documents as a deliberate, *accepted* trade-off: "So corroboration now settles neither. `41g` and the peanut butter's `11 g` are still shown... through the focused-entry route." Task 4 below reverses that acceptance for the single-physical-observation case specifically, while preserving it for genuine cross-observation corroboration once Task 3 makes that distinction real.

## Global Constraints

- Preserve `versionName 1.0.4` / `versionCode 5` (already open per `CLAUDE.md`'s "Version and track state" section). Do not bump, tag, merge, publish, or upload.
- Target branch `ui-refresh-2026-09-03`, starting commit `2c0f0257a091655cfe9371125689fbe6fac70a21`. `main` is not the target. If HEAD or branch differs from this at task-start time, stop and report — do not proceed.
- `domain/` and `ocr/` stay pure Kotlin — every new type introduced by this plan (`CaptureEvidenceCoordinator`, `LiveEvidenceSnapshot`, `ScanDecision`, `ScanDecisionEngine`) must be JVM-testable with zero Android imports, consistent with the rest of `ocr/`.
- Never invent, repair, shift, or infer a digit or decimal. Never default an OCR-derived basis. Never promote sugars/fibre/polyols/other child nutrients to total carbohydrate. Never compose label/amount/unit/basis across unrelated rows or recognition runs. Never settle a conflict using confidence, magnitude, frequency, source order, or plausibility. Compare numeric values with `BigDecimal.compareTo`, never `.equals()`.
- All derivatives of one JPEG (full frame, filtered, crop, rotation, resize, contrast adjustment, repeated OCR) are one physical observation and must share one `PhysicalObservationId`. Only a second, separately-acquired photograph gets a new one.
- Unsupported decimal scale from a single physical observation must never prefill a value for one-tap acceptance. Blank focused entry is the correct outcome; digit repair or a prefilled guess is not.
- Every non-terminal scan decision must retain the frozen capture image — this is `ScanPresentationDecision.releasesCapture`'s existing invariant (`ScanPresentationDecision.kt:161-172`) and must not regress.
- No product-specific correction rules (no per-package heuristics).
- Per this repo's stated working agreement (`CLAUDE.md`): never claim a test suite passed without having run it; verify library versions before adding dependencies (none are added by this plan); `domain`/`ocr` stay pure Kotlin (restated above because it is the constraint most at risk from Phase 9's ViewModel/reducer work, which must live in `ui/` while the reducer's pure decision logic stays in `ocr/`).
- Follow this repo's established test-naming and fixture conventions: JVM tests live under `app/src/test/kotlin/app/justthecarbs/...` mirroring the production package; instrumented tests live under `app/src/androidTest/kotlin/app/justthecarbs/...`. Do not introduce Robolectric (owner decision, `CLAUDE.md` §7 of "Owner's confirmed decisions").
- Every task must build (`./gradlew :app:compileDebugKotlin` at minimum, full JVM test run where the task changes `ocr`/`domain` code) before being marked complete.

---

## File Structure

New files this plan creates:

- `app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt` — pure class separating `aimEpoch` from `workGeneration`; owns `LiveEvidenceSnapshot` freezing.
- `app/src/test/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinatorTest.kt` — unit tests for the coordinator in isolation.
- `app/src/test/kotlin/app/justthecarbs/ocr/CaptureLifecycleIntegrationTest.kt` — the integration test that fails on unmodified `2c0f025` and passes after Task 1/2, driving the coordinator + `LiveEvidenceBuffer` + a fake resolver through the full record→shutter→freeze→resolve sequence.
- `app/src/main/kotlin/app/justthecarbs/ocr/ScanDecision.kt` — the sealed `ScanDecision` result type.
- `app/src/main/kotlin/app/justthecarbs/ocr/ScanDecisionEngine.kt` — the pure engine wrapping `EvidenceResolver`/`AutomaticVerification`/`ScanPresentationDecision`.
- `app/src/test/kotlin/app/justthecarbs/ocr/ScanDecisionEngineTest.kt` — exhaustive tests for the engine.
- `app/src/main/kotlin/app/justthecarbs/ocr/ConflictAdjudication.kt` — the grouped-evidence adjudication rule (Task 7).
- `app/src/test/kotlin/app/justthecarbs/ocr/ConflictAdjudicationTest.kt`.
- `app/src/test/kotlin/app/justthecarbs/onboarding/OnboardingCompletionStateTest.kt` — for Task 8.
- `docs/ocr-evidence-provenance-design.md` — the short design note Task 10 asks for (aimEpoch vs workGeneration, shutter snapshot semantics, physical-observation IDs).
- `app/src/test/kotlin/app/justthecarbs/ocr/fixtures/GroundTruthManifest.kt` — machine-readable ground truth for optical fixtures (Task 0).

Modified files:

- `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt` — replace `captureSession: AtomicLong` usage with `CaptureEvidenceCoordinator`; wire `ScanDecisionEngine` in place of direct `ScanPresentationDecision.decide` calls; assign real `PhysicalObservationId`s at every `RecognitionEvidence` construction site this file owns.
- `app/src/main/kotlin/app/justthecarbs/ocr/LiveEvidenceBuffer.kt` — add `freezeAtShutter`, record `Ambiguous`/`NotFound` outcomes (not just silently drop `NotFound`), change consensus to a contiguous-suffix rule.
- `app/src/main/kotlin/app/justthecarbs/ocr/SelectedTableResolution.kt` — thread a `PhysicalObservationId` (the still capture's) through the `FULL_FRAME_PASS_A`/`FILTERED_PASS_A` construction sites (lines 104-109, 115-124) instead of leaving them at the `UNKNOWN` default.
- `app/src/main/kotlin/app/justthecarbs/ocr/SelectedRegionRecognizer.kt` — accept and stamp the still's `PhysicalObservationId`; later (Phase 9) convert from `CountDownLatch` blocking to a suspending, cancellable implementation.
- `app/src/main/kotlin/app/justthecarbs/ocr/ReadingEligibility.kt` — narrow the corroboration branch so `Unsupported` scale is only rescued by corroboration when the corroborating evidence includes a distinct `PhysicalObservationId`, never by same-photograph agreement alone.
- `app/src/main/kotlin/app/justthecarbs/ocr/EvidenceResolver.kt` — attach an explicit evidence-grade marker to `Outcome.Resolved` distinguishing "Pass A alone" from "corroborated by ≥2 recognition runs" from "structurally verified", per Task 6.
- `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt` — replace the bare `completed: Boolean` with an observable `StateFlow<CompletionState>`.
- `app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt` — replace the local `completing` boolean with observation of the ViewModel's state; navigate only on `Saved`; show a retryable error on `Failed`.
- `app/src/test/kotlin/app/justthecarbs/ocr/LiveEvidenceBufferTest.kt` — extend with the new suffix-consensus and freeze tests.
- `app/src/androidTest/kotlin/app/justthecarbs/ocr/EvidencePipelineProductionTest.kt` — migrate its safety assertion off the deprecated `AutomaticScanAdvance.mayAdvance(outcome)` call (per `.audits/architectural-analysis-2026-09-04.md` P1 finding, `EvidencePipelineProductionTest.kt:147-176`) onto `ScanDecisionEngine`.
- `CHANGELOG.md` — add entries under the existing open `## 1.0.4` heading only.
- `docs/manual-qa.md` — add the new physical-verification gate rows this plan's Phase 10/Release Gates section requires.

---

## Task 0: Baseline verification and ground-truth manifest

**Files:**
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/fixtures/GroundTruthManifest.kt`
- Test: (this task's deliverable *is* test infrastructure; verified by a smoke test in the same file)

**Interfaces:**
- Produces: `GroundTruthManifest`, a `data class GroundTruthCase(val captureId: String, val printedCarbValue: BigDecimal, val printedBasis: NutritionBasis, val allowedFinalActions: Set<String>, val forbiddenDisplayedValues: Set<BigDecimal>, val hasServingFacts: Boolean)` and a `val CASES: List<GroundTruthCase>` — consumed by later Phase 5/10 tests that assert against real optical fixtures.

- [ ] **Step 1: Verify branch and HEAD exactly match the plan's target**

Run:
```bash
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
```
Expected: `ui-refresh-2026-09-03` and `2c0f0257a091655cfe9371125689fbe6fac70a21`. If either differs, STOP and report the mismatch — do not proceed with any other step in this plan.

- [ ] **Step 2: Record the existing JVM suite result**

Run:
```bash
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"
cd C:\Users\tuncb\Desktop\CarbTracker
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
```
Expected: matches CLAUDE.md's recorded figure for this commit (JVM 1715/1715, 0 failures/errors/skipped — the "Startup hardening" section's figure). Record the actual JUnit XML count (`app/build/test-results/testDebugUnitTest/*.xml`, sum `tests` attribute across files) rather than trusting the console summary. If it does not match, note the discrepancy in the final report but do not attempt to fix pre-existing failures as part of this plan unless they block a later task.

- [ ] **Step 3: Record lint and debug assembly baselines**

Run:
```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```
Expected: lint exit 0 (warnings-only is fine; record the count), both compile/assemble tasks succeed. Record exact output.

- [ ] **Step 4: Attempt the connected OCR suite if a device/emulator is available**

Run:
```powershell
C:\atools\sdk\platform-tools\adb.exe devices
```
If a device is listed, run:
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.RealImageOcrTest,app.justthecarbs.ocr.ProductionStillPipelineTest,app.justthecarbs.ocr.EvidencePipelineProductionTest
```
and record the pass/fail counts from the JUnit XML under `app/build/outputs/androidTest-results/connected/`. If no device is attached, record explicitly "connected OCR suite not run — no device attached" and leave the optical release gate (see Release Gates section) open. **Do not claim a suite ran if it did not.**

- [ ] **Step 5: Write the ground-truth manifest**

```kotlin
package app.justthecarbs.ocr.fixtures

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * Machine-readable ground truth for every optical fixture this plan's Phase 5/10 tests assert
 * against. Each case names the printed value/basis from the physical package (not from what any
 * pass of ML Kit returned), the final UI actions that are acceptable outcomes, and the values that
 * must never be displayed regardless of which path is taken.
 *
 * A capture with no established ground truth (nobody has read the physical package and recorded
 * the printed figure) must not appear here — an unverified guess about "what the label probably
 * says" would let a wrong recognizer output silently pass as correct.
 */
object GroundTruthManifest {

    data class GroundTruthCase(
        val captureId: String,
        val printedCarbValue: BigDecimal,
        val printedBasis: NutritionBasis,
        /** Final actions acceptable for this capture — e.g. AUTO_ADVANCE, CONFIRM_ON_CAPTURE, FOCUSED_AMOUNT_ENTRY. */
        val allowedFinalActions: Set<String>,
        /** Values that must never be shown or prefilled for this capture, under any path. */
        val forbiddenDisplayedValues: Set<BigDecimal>,
        val hasServingFacts: Boolean = false,
    )

    /**
     * Seeded from CLAUDE.md's documented session fixtures with an established printed ground
     * truth. Extend this list as new physical captures are verified against their package — do not
     * add a case whose printed value has not actually been read off the physical label.
     */
    val CASES: List<GroundTruthCase> = listOf(
        // Hellmann's bottle, docs/Scan Evidence new structure/20260904-113950-065: prints
        // "1,3 g / 100 ml"; every recognition view reads "13g". This is the case Task 4 exists to
        // close: 13 must never be prefilled for one-tap acceptance.
        GroundTruthCase(
            captureId = "20260904-113950-065",
            printedCarbValue = BigDecimal("1.3"),
            printedBasis = NutritionBasis.PER_100_ML,
            allowedFinalActions = setOf("RECOVERY", "FOCUSED_AMOUNT_ENTRY"),
            forbiddenDisplayedValues = setOf(BigDecimal("13"), BigDecimal("13.0")),
        ),
    )
}
```

- [ ] **Step 6: Run a smoke test confirming the manifest compiles and is non-empty**

```kotlin
package app.justthecarbs.ocr.fixtures

import org.junit.Assert.assertTrue
import org.junit.Test

class GroundTruthManifestTest {
    @Test fun `manifest is non-empty and every forbidden value differs from the printed value`() {
        assertTrue(GroundTruthManifest.CASES.isNotEmpty())
        GroundTruthManifest.CASES.forEach { case ->
            case.forbiddenDisplayedValues.forEach { forbidden ->
                assertTrue(
                    "case ${case.captureId}: forbidden value $forbidden must differ from printed ${case.printedCarbValue}",
                    forbidden.compareTo(case.printedCarbValue) != 0,
                )
            }
        }
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.fixtures.GroundTruthManifestTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/kotlin/app/justthecarbs/ocr/fixtures/GroundTruthManifest.kt
git commit -m "$(cat <<'EOF'
Add machine-readable ground-truth manifest for OCR optical fixtures

Establishes exact printed values, basis, and forbidden displayed values for
optical test cases, so later phases can assert against verified package
contents rather than against whatever a recognizer happened to return.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 1: `CaptureEvidenceCoordinator` — split aim epoch from work generation

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinatorTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (this is the foundational type).
- Produces:
  - `class CaptureEvidenceCoordinator(private val clock: () -> Long = System::currentTimeMillis)`.
  - `val aimEpoch: Long` (read-only property, current aim epoch).
  - `val workGeneration: Long` (read-only property, current work generation).
  - `fun beginNewAim(): Long` — call on retake/resume/dispose/genuinely new aim; bumps `aimEpoch`, returns the new value.
  - `fun beginNewWork(): Long` — call whenever stale async work must be invalidated (every new capture attempt); bumps `workGeneration`, returns the new value. Does **not** touch `aimEpoch`.
  - `fun isCurrentWork(generation: Long): Boolean` — true iff `generation == workGeneration` at call time.
  - `data class LiveEvidenceSnapshot(val aimEpoch: Long, val candidate: CarbCandidate?, val newestFrameAgeMs: Long?, val observationCount: Int, val rejectionReason: String?)`.
  - `fun freezeAtShutter(buffer: LiveEvidenceBuffer, nowElapsed: Long): LiveEvidenceSnapshot` — reads `buffer`'s current aim-epoch-scoped consensus **before** any other shutter-handling side effect runs; must be called before `beginNewWork()`.

This task's `CaptureEvidenceCoordinator` is deliberately a plain class with two `AtomicLong` fields and no stored `CoroutineScope` — per the `kotlin-concurrency-and-flow` skill's structured-concurrency guidance, this is state/bookkeeping, not an owner of asynchronous work, so it must expose no `launch`/`scope` at all. Callers (the Compose screen) own their own coroutines and call this coordinator's plain synchronous methods.

- [ ] **Step 1: Write the failing test for aim-epoch/work-generation independence**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureEvidenceCoordinatorTest {

    @Test fun `beginNewWork bumps workGeneration but not aimEpoch`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aimBefore = coordinator.aimEpoch
        val genBefore = coordinator.workGeneration

        coordinator.beginNewWork()

        assertEquals("aimEpoch must not change on a work-generation bump", aimBefore, coordinator.aimEpoch)
        assertNotEquals("workGeneration must change", genBefore, coordinator.workGeneration)
    }

    @Test fun `beginNewAim bumps aimEpoch but not workGeneration`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aimBefore = coordinator.aimEpoch
        val genBefore = coordinator.workGeneration

        coordinator.beginNewAim()

        assertNotEquals("aimEpoch must change", aimBefore, coordinator.aimEpoch)
        assertEquals("workGeneration must not change on an aim-epoch bump", genBefore, coordinator.workGeneration)
    }

    @Test fun `isCurrentWork is false for a stale generation after beginNewWork`() {
        val coordinator = CaptureEvidenceCoordinator()
        val staleGeneration = coordinator.workGeneration

        coordinator.beginNewWork()

        assertEquals(false, coordinator.isCurrentWork(staleGeneration))
        assertEquals(true, coordinator.isCurrentWork(coordinator.workGeneration))
    }

    @Test fun `repeated captures under one aim each get a fresh work generation without moving the aim epoch`() {
        val coordinator = CaptureEvidenceCoordinator()
        val aim = coordinator.aimEpoch

        val gen1 = coordinator.beginNewWork()
        val gen2 = coordinator.beginNewWork()

        assertNotEquals(gen1, gen2)
        assertEquals("two shutter presses under the same aim must not move the aim epoch", aim, coordinator.aimEpoch)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CaptureEvidenceCoordinatorTest"`
Expected: FAIL with "unresolved reference: CaptureEvidenceCoordinator" (compile failure — the class does not exist yet).

- [ ] **Step 3: Write the minimal implementation (aim/work split only, no snapshot yet)**

```kotlin
package app.justthecarbs.ocr

import java.util.concurrent.atomic.AtomicLong

/**
 * Separates two identities [LabelScannerScreen] previously conflated into one `AtomicLong`
 * (`captureSession`):
 *
 * - **Work generation** answers "is this in-flight async callback still current?" It must bump on
 *   every new capture attempt, so a still-recognition result from an abandoned attempt cannot land.
 * - **Aim epoch** answers "which pre-shutter live-camera stream does this frame belong to?" It must
 *   NOT bump when a shutter is pressed — a frame recorded while the user was aiming, before the tap,
 *   belongs to the same aim as the still that tap produces. It bumps only when the user starts a
 *   genuinely new aim: retake, resume-after-dispose, or leaving and re-entering the screen.
 *
 * ## Why one counter was wrong
 *
 * `LabelScannerScreen.captureSession` bumped at the top of `captureLabel()`, before the still image
 * was even captured. Live frames recorded while framing the shot (necessarily before the tap) were
 * stamped with the pre-tap value. The moment the tap incremented the counter,
 * `LiveEvidenceBuffer.asEvidence(now, session)` filtered on `sessionId == session` using the
 * post-tap value — excluding every one of those pre-tap frames. The buffer's own KDoc documented
 * this exclusion as intentional, which means the conflation was structural, not a wiring slip.
 */
class CaptureEvidenceCoordinator {
    private val aimEpochCounter = AtomicLong(0L)
    private val workGenerationCounter = AtomicLong(0L)

    val aimEpoch: Long get() = aimEpochCounter.get()
    val workGeneration: Long get() = workGenerationCounter.get()

    /** Call on retake, resume-after-dispose, or leaving/re-entering the screen. */
    fun beginNewAim(): Long = aimEpochCounter.incrementAndGet()

    /** Call at the start of every new capture attempt, to invalidate stale in-flight work. */
    fun beginNewWork(): Long = workGenerationCounter.incrementAndGet()

    /** True iff [generation] is still the current work generation. */
    fun isCurrentWork(generation: Long): Boolean = generation == workGenerationCounter.get()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CaptureEvidenceCoordinatorTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt app/src/test/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinatorTest.kt
git commit -m "$(cat <<'EOF'
Add CaptureEvidenceCoordinator separating aim epoch from work generation

LabelScannerScreen's single captureSession AtomicLong conflated two
different questions: "is this async callback still current" (must bump on
every shutter press) and "which pre-shutter live stream does this frame
belong to" (must NOT bump on a shutter press, since aiming necessarily
happens before the tap). This coordinator makes them two counters.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `LiveEvidenceBuffer` — session-scoped by aim epoch, atomic shutter snapshot, contiguous-suffix consensus

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/LiveEvidenceBuffer.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/LiveEvidenceBufferTest.kt` (extend existing file)
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinatorTest.kt` (extend)

**Interfaces:**
- Consumes: `CaptureEvidenceCoordinator` from Task 1.
- Produces:
  - `LiveEvidenceBuffer.record(reading: LabelReading, timestampMs: Long, aimEpoch: Long = 0L)` — the `sessionId` parameter is renamed `aimEpoch` (the rename makes the call sites self-documenting; every existing caller passes it positionally or by the old name and must be updated — grep confirms `LabelScannerScreen.kt:412` is the only production caller).
  - `LiveEvidenceBuffer.stableConsensus(nowMs: Long, aimEpoch: Long = 0L): CarbCandidate?` — rewritten to a contiguous-suffix rule (see Step 3).
  - `LiveEvidenceBuffer.asEvidence(nowMs: Long, aimEpoch: Long = 0L, documentFor: (CarbCandidate) -> OcrDocument? = { null }): RecognitionEvidence?`.
  - `CaptureEvidenceCoordinator.freezeAtShutter(buffer: LiveEvidenceBuffer, nowElapsed: Long): CaptureEvidenceCoordinator.LiveEvidenceSnapshot` — new method, added to the coordinator from Task 1.
  - `data class LiveEvidenceSnapshot(val aimEpoch: Long, val candidate: CarbCandidate?, val newestFrameAgeMs: Long?, val observationCount: Int, val rejectionReason: String?)` on `CaptureEvidenceCoordinator`.

**Design decision — the contiguous-suffix consensus rule**, per the review's Phase 1 §3 and this plan's own re-verification of `LiveEvidenceBuffer.kt`: the current `stableConsensus` (lines 133-146) filters the *whole* window to confident observations and requires 3 of them to agree — meaning three old agreeing frames survive even if the two most recent frames were `Ambiguous` or disagreed, because non-confident observations are invisible to the `mapNotNull` filter and `NotFound` observations are never even stored (`record`, line 108: `if (reading is LabelReading.NotFound) return`). The fix: store `NotFound` too (as a `null`-candidate marker), and require the **most recent** observations in the window to be the agreeing ones — a recent disagreement or non-confident reading invalidates consensus even if an older run of 3 agreed.

- [ ] **Step 1: Write the failing tests for suffix-consensus behavior**

Add to `app/src/test/kotlin/app/justthecarbs/ocr/LiveEvidenceBufferTest.kt` (this file already exists per the repo's 10 existing `LiveEvidenceBufferTest` cases from the startup-hardening pass — these are additive):

```kotlin
    @Test fun `a recent NotFound after three agreeing confident frames invalidates consensus`() {
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        buffer.record(LabelReading.Confident(candidate), 1000L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(candidate), 1100L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(candidate), 1200L, aimEpoch = 7L)
        buffer.record(LabelReading.NotFound, 1300L, aimEpoch = 7L)

        assertEquals(null, buffer.stableConsensus(nowMs = 1300L, aimEpoch = 7L))
    }

    @Test fun `a recent conflicting confident frame invalidates consensus even with an older agreeing run`() {
        val buffer = LiveEvidenceBuffer()
        val agreed = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        val different = confidentCandidate(BigDecimal("12"), NutritionBasis.PER_100_G)
        buffer.record(LabelReading.Confident(agreed), 1000L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(agreed), 1100L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(agreed), 1200L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(different), 1250L, aimEpoch = 7L)

        assertEquals(null, buffer.stableConsensus(nowMs = 1250L, aimEpoch = 7L))
    }

    @Test fun `an ambiguous frame containing a competing candidate invalidates consensus`() {
        val buffer = LiveEvidenceBuffer()
        val agreed = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        val competing = confidentCandidate(BigDecimal("64"), NutritionBasis.PER_100_G)
        buffer.record(LabelReading.Confident(agreed), 1000L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(agreed), 1100L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(agreed), 1200L, aimEpoch = 7L)
        buffer.record(LabelReading.Ambiguous(listOf(agreed, competing)), 1250L, aimEpoch = 7L)

        assertEquals(null, buffer.stableConsensus(nowMs = 1250L, aimEpoch = 7L))
    }

    @Test fun `only the final five observations from the aim epoch are considered`() {
        val buffer = LiveEvidenceBuffer()
        val old = confidentCandidate(BigDecimal("99"), NutritionBasis.PER_100_G)
        val recent = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        // Six old disagreeing observations, then three recent agreeing ones — the old ones must
        // not be visible to the suffix rule at all, so their disagreement cannot suppress the
        // recent agreement.
        repeat(6) { i -> buffer.record(LabelReading.Confident(old.copy(value = old.value + BigDecimal(i))), 1000L + i, aimEpoch = 7L) }
        buffer.record(LabelReading.Confident(recent), 1900L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(recent), 1950L, aimEpoch = 7L)
        buffer.record(LabelReading.Confident(recent), 2000L, aimEpoch = 7L)

        val consensus = buffer.stableConsensus(nowMs = 2000L, aimEpoch = 7L)
        assertEquals(0, consensus?.value?.compareTo(BigDecimal("46")))
    }

    @Test fun `the sessionId parameter is now named aimEpoch and an epoch-6 frame cannot corroborate epoch 7`() {
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        buffer.record(LabelReading.Confident(candidate), 1000L, aimEpoch = 6L)
        buffer.record(LabelReading.Confident(candidate), 1100L, aimEpoch = 6L)
        buffer.record(LabelReading.Confident(candidate), 1200L, aimEpoch = 6L)

        assertEquals(null, buffer.stableConsensus(nowMs = 1200L, aimEpoch = 7L))
    }
```

Also add a helper at the bottom of the test file if one does not already exist (check for an existing `confidentCandidate` helper in the file first — reuse it if present rather than duplicating):

```kotlin
private fun confidentCandidate(value: BigDecimal, basis: NutritionBasis): CarbCandidate = CarbCandidate(
    sourceLine = "test",
    label = "test",
    value = value,
    basis = basis,
    score = 100,
    geometry = OcrBox(0f, 0f, 10f, 10f),
    evidence = emptyList(),
    column = null,
)
```

(Verify the exact `CarbCandidate` constructor shape against `RecognitionEvidence.kt`/`ScaleAmbiguity.kt`'s existing test fixtures before writing this helper — the fields `sourceLine`, `label`, `value`, `basis`, `score`, `geometry`, `evidence`, `column` were confirmed via `ReadingEligibility.kt:281-290`'s `probeFor` construction in this plan's research phase; reuse that exact shape.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.LiveEvidenceBufferTest"`
Expected: FAIL — compile error on `aimEpoch` parameter name (current signature uses `sessionId`), and the new suffix-rule tests fail logically once renamed (the "NotFound invalidates" and "conflicting frame invalidates" cases fail against the current all-window-confident-only logic).

- [ ] **Step 3: Rewrite `LiveEvidenceBuffer` with the suffix-consensus rule**

```kotlin
package app.justthecarbs.ocr

import java.math.BigDecimal

/**
 * A bounded history of pre-shutter live interpretations (spec §5, §9), scoped by aim epoch rather
 * than by a single conflated session counter.
 *
 * ## Aim epoch, not "session"
 *
 * The parameter previously named `sessionId` is renamed `aimEpoch` to make the call site
 * self-documenting: it identifies which pre-shutter live-camera stream an observation belongs to,
 * and it must be supplied by [CaptureEvidenceCoordinator.aimEpoch] — which does NOT change on a
 * shutter press — never by a per-capture work-generation counter. See
 * [CaptureEvidenceCoordinator]'s KDoc for why conflating the two excluded almost all pre-shutter
 * evidence in the previous design.
 *
 * ## Contiguous-suffix consensus, not "any 3 agreeing in the window"
 *
 * The previous rule filtered the whole time window to confident observations and required 3 to
 * agree, which let three *old* agreeing frames remain "stable" even if the camera had since moved
 * to an ambiguous or conflicting reading. Consensus must reflect what the camera was seeing *right
 * before the shutter*, so a recent disagreement or non-confident reading must invalidate an older
 * agreeing run: see [stableConsensus].
 */
class LiveEvidenceBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    /**
     * One live interpretation, when it happened, and which aim epoch it belongs to.
     *
     * [reading] may now be [LabelReading.NotFound] or [LabelReading.Ambiguous] — both are stored
     * (previously `NotFound` was dropped at [record]) because the suffix-consensus rule in
     * [stableConsensus] needs to see a *recent* non-confident reading to invalidate an older
     * agreeing run, which it cannot do if that reading was never recorded at all.
     *
     * [aimEpoch] defaults to 0 so every pre-existing caller and test keeps behaving exactly as
     * before when it does not care about epoch scoping.
     */
    data class Observation(
        val reading: LabelReading,
        val timestampMs: Long,
        val aimEpoch: Long = 0L,
    )

    private val lock = Any()
    private val observations = ArrayDeque<Observation>()

    /** Records a live-frame interpretation, including NotFound and Ambiguous. Cheap; called on every analysed frame. */
    fun record(reading: LabelReading, timestampMs: Long, aimEpoch: Long = 0L) {
        synchronized(lock) {
            observations.addLast(Observation(reading, timestampMs, aimEpoch))
            while (observations.size > capacity) observations.removeFirst()
        }
    }

    /** Forgets everything. Called on retake and on leaving the screen. */
    fun clear() = synchronized(lock) { observations.clear() }

    /** Snapshot for the evidence bundle; ordering is oldest-first. */
    fun snapshot(): List<Observation> = synchronized(lock) { observations.toList() }

    /**
     * The value the most recent contiguous run of observations from [aimEpoch] agreed on, or null.
     *
     * Examines at most the final [MAX_SUFFIX_LENGTH] observations recorded under [aimEpoch] (older
     * ones, however they read, cannot suppress or supply consensus). Within that suffix:
     *
     * - the newest observation must be inside [windowMs] of [nowMs];
     * - at least [MIN_AGREEING_FRAMES] observations must be [LabelReading.Confident] and agree
     *   exactly on value and basis (numeric `compareTo`, since `BigDecimal.equals` is
     *   scale-sensitive);
     * - any [LabelReading.Confident] observation in the suffix that disagrees with that value
     *   invalidates consensus outright;
     * - any [LabelReading.Ambiguous] observation whose candidates include a value other than the
     *   agreed one invalidates consensus;
     * - at most one non-confident observation (Ambiguous with no competing candidate, or NotFound)
     *   is tolerated, and only if it is not newer than the newest agreeing confident observation —
     *   a `NotFound` or ambiguity that arrived *after* the last agreeing frame means the camera's
     *   most recent view is not confident, and that must not be papered over by older agreement.
     */
    fun stableConsensus(nowMs: Long, aimEpoch: Long = 0L): CarbCandidate? {
        val suffix = synchronized(lock) { observations.toList() }
            .filter { it.aimEpoch == aimEpoch }
            .takeLast(MAX_SUFFIX_LENGTH)
        if (suffix.isEmpty()) return null
        if (nowMs - suffix.last().timestampMs > windowMs) return null

        val confident = suffix.mapNotNull { obs -> (obs.reading as? LabelReading.Confident)?.let { obs to it.candidate } }
        if (confident.size < MIN_AGREEING_FRAMES) return null

        val agreedValue = confident.last().second
        val allConfidentAgree = confident.all { (_, candidate) ->
            candidate.basis != null &&
                candidate.basis == agreedValue.basis &&
                candidate.value.compareTo(agreedValue.value) == 0
        }
        if (!allConfidentAgree) return null

        val newestAgreeingTimestamp = confident.last().first.timestampMs
        val nonConfident = suffix.filterNot { it.reading is LabelReading.Confident }
        if (nonConfident.size > 1) return null
        nonConfident.forEach { obs ->
            if (obs.timestampMs > newestAgreeingTimestamp) return null
            val ambiguous = obs.reading as? LabelReading.Ambiguous ?: return@forEach
            val competes = ambiguous.candidates.any { it.value.compareTo(agreedValue.value) != 0 }
            if (competes) return null
        }

        return agreedValue
    }

    /** Consensus wrapped as resolver evidence, or null. */
    fun asEvidence(
        nowMs: Long,
        aimEpoch: Long = 0L,
        documentFor: (CarbCandidate) -> OcrDocument? = { null },
    ): RecognitionEvidence? {
        val candidate = stableConsensus(nowMs, aimEpoch) ?: return null
        return RecognitionEvidence(
            source = EvidenceSource.LIVE_STABLE_FRAME,
            report = NutritionParseReport(LabelReading.Confident(candidate), emptyList()),
            document = documentFor(candidate),
        )
    }

    private companion object {
        const val DEFAULT_CAPACITY = 12
        const val DEFAULT_WINDOW_MS = 1_500L
        const val MIN_AGREEING_FRAMES = 3

        /** At most the final 5 observations from one aim epoch are examined for consensus. */
        const val MAX_SUFFIX_LENGTH = 5
    }
}
```

**Note on `LabelReading.Ambiguous.candidates`:** confirm the exact field name on `LabelReading.Ambiguous` before compiling — the review's prompt calls it `candidates` and this plan follows that; if the actual sealed class field has a different name, use that name and update the KDoc/code above accordingly rather than silently renaming the domain type.

- [ ] **Step 4: Update `LabelScannerScreen.kt`'s single production call site to the renamed parameter**

At line 412 (analyzer callback) and line 590 (`asEvidence` call inside `readSelectedTable`), rename the argument label from implicit/`sessionId` to `aimEpoch`, and — critically — change what value is passed. This is the heart of the fix: line 412 must pass `coordinator.aimEpoch` (not the old `captureSession.get()`), and line 590 must also pass `coordinator.aimEpoch`, so that both recording and querying key on the epoch that does not move at shutter time. The `workGeneration`-based cancellation checks (currently at lines 596, 954, 1012, all comparing against `captureSession.get()`) are migrated to `coordinator.isCurrentWork(...)` in Task 3 alongside the `LabelScannerScreen` rewiring — this task only touches the `LiveEvidenceBuffer` call sites, since `LabelScannerScreen` still holds the old `AtomicLong` field until Task 3 replaces it wholesale (partial migration here would leave the file in an inconsistent, harder-to-review state).

Because Task 3 is where `LabelScannerScreen` is rewired end-to-end, this task's Step 4 is: **do not modify `LabelScannerScreen.kt` yet.** Leave it passing `captureSession.get()` to the (now-renamed) `aimEpoch` parameter for now — it will compile and behave exactly as before (still wrong, but not more wrong) until Task 3 replaces `captureSession` with the coordinator. Confirm this by running the existing `LabelScannerScreen`-adjacent instrumented tests are unaffected by the rename (they call the buffer directly with named/positional `Long` args, not `sessionId=`, so a rename is source-compatible for positional callers — check the two 2026-09-04 startup-hardening tests in `LiveEvidenceBufferTest.kt` that reference session scoping by name, and update any that use the `sessionId =` named-argument form to `aimEpoch =`).

Run:
```powershell
grep -rn "sessionId" app/src/main/kotlin/app/justthecarbs/ app/src/test/kotlin/app/justthecarbs/
```
Update every remaining named-argument use of `sessionId =` to `aimEpoch =` across production and test code (this is a mechanical rename now that the parameter name changed).

- [ ] **Step 5: Add `freezeAtShutter` to `CaptureEvidenceCoordinator`**

```kotlin
    /**
     * One frozen answer to "what did the live camera see, right before the shutter fired?" — taken
     * atomically, before any other shutter-handling side effect (work-generation bump, analyzer
     * pause, autofocus, image capture) runs.
     *
     * Freezing here rather than reading the buffer later is what stops OCR latency from silently
     * expiring valid evidence: the review measured 477-2458ms for the still pipeline on real
     * hardware, comfortably longer than [LiveEvidenceBuffer]'s 1500ms window, so a query issued
     * after the still pipeline completes can find nothing left even when the camera saw a stable
     * reading seconds ago relative to when it actually mattered — the shutter press.
     */
    data class LiveEvidenceSnapshot(
        val aimEpoch: Long,
        val candidate: CarbCandidate?,
        val newestFrameAgeMs: Long?,
        val observationCount: Int,
        val rejectionReason: String?,
    )

    fun freezeAtShutter(buffer: LiveEvidenceBuffer, nowElapsed: Long): LiveEvidenceSnapshot {
        val epoch = aimEpoch
        val snapshotList = buffer.snapshot().filter { it.aimEpoch == epoch }
        val candidate = buffer.stableConsensus(nowElapsed, epoch)
        val newestAge = snapshotList.maxOfOrNull { it.timestampMs }?.let { nowElapsed - it }
        val rejection = when {
            snapshotList.isEmpty() -> "no observations recorded for this aim epoch"
            candidate == null -> "recent observations did not reach stable agreement"
            else -> null
        }
        return LiveEvidenceSnapshot(
            aimEpoch = epoch,
            candidate = candidate,
            newestFrameAgeMs = newestAge,
            observationCount = snapshotList.size,
            rejectionReason = rejection,
        )
    }
```

Note: `buffer.snapshot()`'s `Observation.timestampMs` values are wall-clock (`System.currentTimeMillis()`, per `LabelScannerScreen.kt:412`'s existing call) while `nowElapsed` here is documented as monotonic elapsed time (`SystemClock.elapsedRealtime()`) per the review's Task 2 instruction. **This is a real inconsistency to resolve, not paper over**: either (a) `LiveEvidenceBuffer` must be changed to record and compare using the same clock source throughout, or (b) `freezeAtShutter` must accept and use a wall-clock `nowMs` to match what the buffer already stores. Resolve this in Step 6 below rather than leaving mixed clock sources in the same computation — a wall-clock jump (NTP sync, timezone change) would corrupt monotonic-clock assumptions if the two are mixed.

- [ ] **Step 6: Resolve the clock-source inconsistency — standardize on monotonic elapsed time everywhere**

Change `LiveEvidenceBuffer.record`'s call sites and `Observation.timestampMs`'s semantic contract (via KDoc, not a field rename, to avoid an unnecessary API break) to require **monotonic elapsed time** (`SystemClock.elapsedRealtime()` on Android, or an injected `() -> Long` in tests), not wall-clock time, for every `timestampMs`/`nowMs` argument. Update the KDoc on `LiveEvidenceBuffer.record` and `stableConsensus`:

```kotlin
    /**
     * Records a live-frame interpretation. Cheap; called on every analysed frame.
     *
     * [timestampMs] MUST be monotonic elapsed time (`SystemClock.elapsedRealtime()` on Android),
     * never wall-clock time — a wall-clock adjustment (NTP sync, timezone/DST change) must not be
     * able to make an old observation appear fresh or a fresh one appear stale. [CaptureEvidence­
     * Coordinator.freezeAtShutter] and every production caller must use the same clock source.
     */
```

Update `LabelScannerScreen.kt`'s two call sites (line 412, line 590) — this is folded into Task 3's rewiring, since both lines move from `System.currentTimeMillis()` to an injected monotonic clock as part of that task. For this task, only the `LiveEvidenceBuffer`/`CaptureEvidenceCoordinator` contract and KDoc change; production wiring happens in Task 3. Add a JVM test confirming the contract is honored by the coordinator:

```kotlin
    @Test fun `freezeAtShutter uses the same clock value passed to it, not a fresh wall-clock read`() {
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"), NutritionBasis.PER_100_G)
        val coordinator = CaptureEvidenceCoordinator()
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1000L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1100L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(candidate), timestampMs = 1200L, aimEpoch = coordinator.aimEpoch)

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 1250L)

        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
        assertEquals(3, snapshot.observationCount)
    }

    @Test fun `freezeAtShutter reports a rejection reason when nothing is available`() {
        val buffer = LiveEvidenceBuffer()
        val coordinator = CaptureEvidenceCoordinator()

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 1000L)

        assertEquals(null, snapshot.candidate)
        assertEquals("no observations recorded for this aim epoch", snapshot.rejectionReason)
    }
```

- [ ] **Step 7: Run all `LiveEvidenceBufferTest` and `CaptureEvidenceCoordinatorTest` cases**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.LiveEvidenceBufferTest" --tests "app.justthecarbs.ocr.CaptureEvidenceCoordinatorTest"`
Expected: PASS, including every pre-existing case from the startup-hardening pass (10 original + new additions).

- [ ] **Step 8: Run the full JVM suite to confirm no other file broke from the `sessionId`→`aimEpoch` rename**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS, same count as Task 0's baseline plus this task's additions, minus zero (no regressions).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/LiveEvidenceBuffer.kt app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt app/src/test/kotlin/app/justthecarbs/ocr/LiveEvidenceBufferTest.kt app/src/test/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinatorTest.kt
git commit -m "$(cat <<'EOF'
Rename LiveEvidenceBuffer sessionId to aimEpoch; contiguous-suffix consensus

record()/stableConsensus()/asEvidence() now take aimEpoch, matching
CaptureEvidenceCoordinator's separated identity, instead of a session
counter that also served as a work-generation cancellation token.

stableConsensus() now examines only the most recent contiguous run of
observations, so a recent NotFound, ambiguity, or conflicting confident
frame invalidates an older agreeing run instead of being invisible to it.
Both NotFound and Ambiguous observations are now stored (previously
NotFound was dropped at record time), which is what makes the suffix rule
able to see them at all.

Added CaptureEvidenceCoordinator.freezeAtShutter, which takes an atomic
snapshot of live consensus before any other shutter-handling side effect
runs -- closing the gap where OCR latency (measured 477-2458ms on real
hardware) could expire evidence before a later, lazy query for it.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Wire `LabelScannerScreen` to the coordinator; the integration test that fails on unmodified HEAD

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/CaptureLifecycleIntegrationTest.kt` (new)

**Interfaces:**
- Consumes: `CaptureEvidenceCoordinator` (Task 1/2), `LiveEvidenceBuffer` with `aimEpoch` (Task 2).
- Produces: `LabelScannerScreen` no longer declares `val captureSession = remember { AtomicLong(0L) }`; it declares `val coordinator = remember { CaptureEvidenceCoordinator() }` and calls `coordinator.beginNewAim()` where `captureSession.incrementAndGet()` was previously called for dispose/resumeLive (lines 430, 450), and `coordinator.beginNewWork()` where it was called for the shutter (line 1130). All `captureSession.get()` reads for cancellation purposes (lines 923, 954, 1012, 596) become `coordinator.isCurrentWork(capturedGeneration)`, where `capturedGeneration` is the value returned by the `beginNewWork()` call for that attempt. All `captureSession.get()` reads for evidence-recording/querying purposes (lines 412, 570/590) become `coordinator.aimEpoch`.

**This is the task whose test must fail on unmodified `2c0f025` and pass afterward**, per the review's explicit requirement. Because `LabelScannerScreen.kt` is a Compose file with Android/CameraX dependencies, the integration test cannot drive the actual composable from a JVM test — instead, it drives the **exact sequence of coordinator/buffer calls** `LabelScannerScreen` performs, reproduced faithfully enough that the test fails against the *old* single-counter design and passes against the new split design. This is done by writing the test against a small pure `CaptureLifecycleSimulator` that mirrors the production call sequence, so the test is meaningful without needing Robolectric or a real Compose test harness (which this repo does not use for `ocr`/`domain` logic).

- [ ] **Step 1: Write the failing integration test, driven through a faithful lifecycle simulator**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Reproduces LabelScannerScreen's actual capture lifecycle against CaptureEvidenceCoordinator and
 * LiveEvidenceBuffer, without needing Android/Compose. This is the test the review asked for: it
 * must fail against the OLD single-AtomicLong design (which this test does not use -- it uses the
 * NEW coordinator, so a regression back to one shared counter would fail this suite, not merely
 * fail to compile).
 */
class CaptureLifecycleIntegrationTest {

    private fun confidentCandidate(value: BigDecimal) = CarbCandidate(
        sourceLine = "test", label = "test", value = value,
        basis = app.justthecarbs.domain.NutritionBasis.PER_100_G,
        score = 100, geometry = OcrBox(0f, 0f, 10f, 10f), evidence = emptyList(), column = null,
    )

    @Test fun `three frames recorded while aiming survive a shutter press and a slow still pipeline`() {
        val coordinator = CaptureEvidenceCoordinator()
        val buffer = LiveEvidenceBuffer()
        val candidate = confidentCandidate(BigDecimal("46"))

        // User aims the camera; three agreeing live frames land, exactly as the analyzer callback
        // does in production -- recorded against the CURRENT aim epoch (which does not move yet).
        var clock = 0L
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 100
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 100
        buffer.record(LabelReading.Confident(candidate), clock, aimEpoch = coordinator.aimEpoch)
        clock += 50 // user taps the shutter shortly after the third frame

        // Shutter handler: freeze BEFORE bumping work generation (this ordering is the fix).
        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = clock)
        val workGeneration = coordinator.beginNewWork()

        // Simulate a slow still pipeline -- 2500ms, well past LiveEvidenceBuffer's 1500ms window.
        clock += 2500

        // The still pipeline is done; it queries the buffer the way readSelectedTable does today.
        // A query issued AFTER 2500ms would find nothing (window has passed) if it re-queried the
        // buffer live -- which is exactly why the frozen snapshot, not a live re-query, must be
        // what the resolver receives.
        val liveQueryNow = buffer.asEvidence(nowMs = clock, aimEpoch = coordinator.aimEpoch)

        assertEquals("a live re-query after the window has passed must find nothing", null, liveQueryNow)
        assertNotNull("but the frozen snapshot taken AT shutter time must still carry the candidate", snapshot.candidate)
        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
        assertEquals(true, coordinator.isCurrentWork(workGeneration))
    }

    @Test fun `an aim epoch from a previous attempt cannot corroborate the current one`() {
        val coordinator = CaptureEvidenceCoordinator()
        val buffer = LiveEvidenceBuffer()
        val staleCandidate = confidentCandidate(BigDecimal("99"))

        // Frames recorded under a previous aim (before a retake).
        buffer.record(LabelReading.Confident(staleCandidate), 0L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(staleCandidate), 100L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(staleCandidate), 200L, aimEpoch = coordinator.aimEpoch)

        // User retakes -- a genuinely new aim begins.
        coordinator.beginNewAim()

        // New frames under the new aim disagree with the stale ones (a different package, say).
        val freshCandidate = confidentCandidate(BigDecimal("46"))
        buffer.record(LabelReading.Confident(freshCandidate), 300L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(freshCandidate), 400L, aimEpoch = coordinator.aimEpoch)
        buffer.record(LabelReading.Confident(freshCandidate), 500L, aimEpoch = coordinator.aimEpoch)

        val snapshot = coordinator.freezeAtShutter(buffer, nowElapsed = 550L)

        assertEquals(0, snapshot.candidate?.value?.compareTo(BigDecimal("46")))
    }

    @Test fun `retake cancels stale work -- a late result from before the retake is not current`() {
        val coordinator = CaptureEvidenceCoordinator()
        val staleGeneration = coordinator.beginNewWork() // first capture attempt

        coordinator.beginNewAim() // user retakes
        coordinator.beginNewWork() // the retake's own capture attempt

        // A result from the FIRST attempt finally arrives, late.
        assertEquals(false, coordinator.isCurrentWork(staleGeneration))
    }

    @Test fun `two captures under one aim epoch (no retake between them) each get their own work generation`() {
        val coordinator = CaptureEvidenceCoordinator()
        val gen1 = coordinator.beginNewWork()
        val gen2 = coordinator.beginNewWork()

        assertEquals(false, coordinator.isCurrentWork(gen1))
        assertEquals(true, coordinator.isCurrentWork(gen2))
    }
}
```

**Verify this test fails on unmodified `2c0f025`:** since this test is written against the *new* `CaptureEvidenceCoordinator`/`aimEpoch`-based API that does not exist at `2c0f025`, it fails to compile at that commit — which is the correct and expected failure mode for a test proving a bug that required an architectural fix, not a behavioral one (the review's own framing acknowledges the bug is structural). Confirm this explicitly:

```bash
git stash
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CaptureLifecycleIntegrationTest"
git stash pop
```
Expected: the stashed-tree run fails to compile (no `CaptureEvidenceCoordinator` class exists at that commit), confirming the test is meaningful against the true baseline.

- [ ] **Step 2: Run the test against the current (Task 1/2-modified) tree to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CaptureLifecycleIntegrationTest"`
Expected: PASS (4 tests).

- [ ] **Step 3: Rewire `LabelScannerScreen.kt` to use the coordinator**

Read the current file in full around each of the eleven call sites verified in this plan's research (lines 366, 412, 430, 450, 570, 590, 596, 923, 954, 1012, 1130) before editing — do not edit blind from line numbers alone, since earlier edits in this task shift subsequent line numbers.

Replace:
```kotlin
val captureSession = remember { java.util.concurrent.atomic.AtomicLong(0L) }
```
with:
```kotlin
val coordinator = remember { CaptureEvidenceCoordinator() }
```

At the analyzer callback (was line 412):
```kotlin
liveEvidence.record(result, System.currentTimeMillis(), captureSession.get())
```
becomes:
```kotlin
liveEvidence.record(result, SystemClock.elapsedRealtime(), coordinator.aimEpoch)
```
(add `import android.os.SystemClock` if not already present).

At `onDispose` (was line 430) and `resumeLive()` (was line 450):
```kotlin
captureSession.incrementAndGet()
```
becomes:
```kotlin
coordinator.beginNewAim()
```

At `captureLabel()` (was line 1130), the shutter handler — this is the site where the frozen snapshot must be taken **before** the work-generation bump:
```kotlin
// A new attempt invalidates anything still in flight from the previous one.
captureSession.incrementAndGet()
```
becomes:
```kotlin
// Freeze live evidence BEFORE bumping work generation or touching anything else -- this
// ordering is the fix for the pre-shutter evidence loss (see CaptureEvidenceCoordinator KDoc).
val liveSnapshot = coordinator.freezeAtShutter(liveEvidence, SystemClock.elapsedRealtime())
frozenLiveSnapshot = liveSnapshot
// A new attempt invalidates anything still in flight from the previous one.
val workGeneration = coordinator.beginNewWork()
```

This requires a new `remember { mutableStateOf<CaptureEvidenceCoordinator.LiveEvidenceSnapshot?>(null) }` field, `frozenLiveSnapshot`, declared near `coordinator`'s declaration, so the frozen snapshot survives from the shutter tap through to `readSelectedTable`'s later resolution call.

At `takePictureNow` (was line 923):
```kotlin
val session = captureSession.get()
```
becomes: remove this line — the work generation is already captured as `workGeneration` from `captureLabel()`'s scope and threaded through to `takePictureNow` as a parameter (adjust the function signature to accept `workGeneration: Long`, threading it from the `captureLabel()` call site at what was line 1155's `focusThenCapture(capture, file)` call — add `workGeneration` as a parameter there too).

At the stale-result guards (was lines 596, 954, 1012):
```kotlin
if (session != captureSession.get()) return@launch
```
becomes:
```kotlin
if (!coordinator.isCurrentWork(workGeneration)) return@launch
```
(with the equivalent transformation for the `result.sessionId != captureSession.get()` and `session != captureSession.get()` forms at the other two sites — each needs `workGeneration` threaded into its scope the same way).

At `readSelectedTable` (was lines 570, 590):
```kotlin
val session = captureSession.get()
...
liveEvidence = liveEvidence.asEvidence(System.currentTimeMillis(), session),
```
becomes:
```kotlin
val workGeneration = coordinator.workGeneration
...
// Use the FROZEN snapshot taken at shutter time, never a fresh query -- a fresh query here
// would be exactly the "queried after the window passed" bug this task's integration test
// (CaptureLifecycleIntegrationTest) exists to prevent regressing.
liveEvidence = frozenLiveSnapshot?.candidate?.let { candidate ->
    RecognitionEvidence(
        source = EvidenceSource.LIVE_STABLE_FRAME,
        report = NutritionParseReport(LabelReading.Confident(candidate), emptyList()),
        document = null,
    )
},
```

**Important:** this replaces the live *query* (`asEvidence`, which re-reads the buffer) with a read of the already-frozen snapshot taken at shutter time. This is the literal fix for the latency-expiry defect the review's Phase 1 §2 asks for ("never read the mutable live buffer from the background OCR coroutine... carry the frozen snapshot with the capture request/result").

Because `readSelectedTable` can be invoked from more than one call site (the automatic post-capture path and the user's manual "Read table" tap after a confirmed crop, per this file's existing structure), verify both call sites route through the same `frozenLiveSnapshot` field rather than one of them re-querying the buffer live. If the manual crop-confirmation path is invoked long after the shutter (the user dragging a crop rectangle can take many seconds), using the frozen snapshot there is *still correct*, not stale — the snapshot represents "what the camera saw as the shutter was pressed", which is exactly the evidence that should corroborate a still recognized from that same shutter press, however long the user takes to confirm a crop afterward.

- [ ] **Step 4: Compile and run existing Compose-adjacent instrumented tests to confirm nothing else broke**

Run:
```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```
Expected: both succeed.

If a device is available, run the label-scanner instrumented tests:
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ui.scan.LabelScannerScreenTest
```
(confirm this exact test class name exists first with `find app/src/androidTest -iname "*LabelScanner*"`; if the class is named differently, use the actual name). Record pass/fail; if no device is attached, record that explicitly.

- [ ] **Step 5: Run the full JVM suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS, baseline count plus this task's additions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt app/src/test/kotlin/app/justthecarbs/ocr/CaptureLifecycleIntegrationTest.kt
git commit -m "$(cat <<'EOF'
Wire LabelScannerScreen to CaptureEvidenceCoordinator; freeze live evidence at shutter

Replaces the single captureSession AtomicLong (which conflated aim epoch
and work generation) with the coordinator from the previous two commits.
The shutter handler now freezes live-evidence consensus BEFORE bumping
work generation or performing any other side effect, and readSelectedTable
consumes that frozen snapshot instead of re-querying the live buffer --
closing the gap where a slow still pipeline (measured 477-2458ms on real
hardware) could let valid pre-shutter evidence expire before it was read.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Real physical-observation provenance

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/SelectedTableResolution.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/SelectedRegionRecognizer.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/SelectedTableResolutionTest.kt` (extend existing, or create if none exists — check first)
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/PhysicalObservationProvenanceTest.kt` (new)

**Interfaces:**
- Consumes: `PhysicalObservationId` (existing, `RecognitionEvidence.kt:131-143`), `CaptureEvidenceCoordinator` (Task 1).
- Produces:
  - `SelectedTableResolution.resolve(...)` gains a required `stillObservationId: PhysicalObservationId` parameter (no default — forcing every caller to supply one explicitly is the point; per the audit's finding that `PhysicalObservationId.UNKNOWN` "lets production compile while the intended provenance route is entirely unwired," Task 4 removes that silent compilability for the two construction sites this file owns).
  - `SelectedRegionRecognizer.recognise(...)` gains a required `observationId: PhysicalObservationId` parameter, stamped onto the returned `RecognitionEvidence`.
  - A helper `PhysicalObservationId.forStill(captureId: String): PhysicalObservationId = PhysicalObservationId("still:$captureId")` and `PhysicalObservationId.forLiveSnapshot(aimEpoch: Long, snapshotId: String): PhysicalObservationId = PhysicalObservationId("live:$aimEpoch:$snapshotId")`.

- [ ] **Step 1: Write the failing test asserting Pass A, filtered Pass A, and Strategy B share one still ID**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhysicalObservationProvenanceTest {

    @Test fun `forStill and forLiveSnapshot produce distinct, non-UNKNOWN ids`() {
        val still = PhysicalObservationId.forStill("capture-123")
        val live = PhysicalObservationId.forLiveSnapshot(aimEpoch = 7L, snapshotId = "abc")

        assertNotEquals(PhysicalObservationId.UNKNOWN, still)
        assertNotEquals(PhysicalObservationId.UNKNOWN, live)
        assertNotEquals(still, live)
    }

    @Test fun `two forStill calls with the same captureId produce the same id`() {
        assertEquals(PhysicalObservationId.forStill("capture-123"), PhysicalObservationId.forStill("capture-123"))
    }

    @Test fun `full-frame and filtered Pass A evidence from one resolve call share the still id`() {
        val stillId = PhysicalObservationId.forStill("capture-1")
        val passA = fakePassAResult()
        val result = SelectedTableResolution.resolve(
            passA = passA,
            region = fakeRegion(),
            bitmap = null,
            stillObservationId = stillId,
        )
        // Every RecognitionEvidence this call produced that isn't LIVE_STABLE_FRAME must carry stillId.
        result.evidence
            .filter { it.source != EvidenceSource.LIVE_STABLE_FRAME }
            .forEach { assertEquals("source ${it.source} must carry the still id", stillId, it.physicalObservation) }
    }
}
```

**Fixture helpers required by this test** (`fakePassAResult()`, `fakeRegion()`): check `app/src/test/kotlin/app/justthecarbs/ocr/` for an existing `SelectedTableResolutionTest.kt` and reuse its exact `PassAResult`/`NormalizedRegion` construction helpers verbatim rather than inventing new ones — this plan's research pass did not transcribe that specific file's fixtures (it transcribed `EvidenceResolverTest.kt`'s instead, reused below in Tasks 6-7), so read that file directly before writing this step's final code. If no such file exists, build `PassAResult`/`NormalizedRegion` fixtures using the same verified `OcrDocument`/`OcrBox`/`NutritionParseReport` construction pattern documented in Task 6 Step 1 below (`OcrDocument(width, height, elements)`, `OcrBox(left, top, right, bottom)` positional, `NutritionParseReport(reading, diagnostics = emptyList())`).

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.PhysicalObservationProvenanceTest"`
Expected: FAIL — compile error, `forStill`/`forLiveSnapshot`/`stillObservationId` parameter do not exist yet.

- [ ] **Step 3: Add the factory functions to `PhysicalObservationId`**

Edit `RecognitionEvidence.kt`'s `PhysicalObservationId` companion object:

```kotlin
@JvmInline
value class PhysicalObservationId(val value: String) {
    companion object {
        val UNKNOWN = PhysicalObservationId("UNKNOWN")

        /**
         * The id shared by every recognition derived from one quality still capture: the whole
         * frame, the filtered/cropped re-parse, and a Strategy B re-recognition of the same JPEG.
         * All of these read the same ink, so they are one physical observation whatever transform
         * or recognizer call produced them.
         */
        fun forStill(captureId: String): PhysicalObservationId = PhysicalObservationId("still:$captureId")

        /**
         * The id for a frozen pre-shutter live-evidence snapshot. Distinct from the still it may
         * corroborate, because it represents genuinely different sensor frames captured before the
         * shutter fired -- not a re-processing of the same pixels.
         */
        fun forLiveSnapshot(aimEpoch: Long, snapshotId: String): PhysicalObservationId =
            PhysicalObservationId("live:$aimEpoch:$snapshotId")
    }
}
```

- [ ] **Step 4: Run test to verify the factory-function tests pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.PhysicalObservationProvenanceTest"`
Expected: first two tests PASS; the third still fails (no `stillObservationId` parameter on `resolve` yet).

- [ ] **Step 5: Thread `stillObservationId` through `SelectedTableResolution.resolve`**

Modify the signature (verified current shape: `fun resolve(passA: PassAResult, region: NormalizedRegion, bitmap: Bitmap?, liveEvidence: RecognitionEvidence? = null, recogniseRegion: (Bitmap?, NormalizedRegion) -> RecognitionEvidence? = { bmp, rgn -> SelectedRegionRecognizer.recognise(bmp, rgn) })`):

```kotlin
fun resolve(
    passA: PassAResult,
    region: NormalizedRegion,
    bitmap: Bitmap?,
    stillObservationId: PhysicalObservationId,
    liveEvidence: RecognitionEvidence? = null,
    recogniseRegion: (Bitmap?, NormalizedRegion) -> RecognitionEvidence? = { bmp, rgn ->
        SelectedRegionRecognizer.recognise(bmp, rgn, stillObservationId)
    },
): Result
```

At the `FULL_FRAME_PASS_A` construction site (verified lines 104-109):
```kotlin
RecognitionEvidence(
    source = EvidenceSource.FULL_FRAME_PASS_A,
    report = passA.report,
    document = passA.document,
    physicalObservation = stillObservationId,
)
```

At the `FILTERED_PASS_A` construction site (verified lines 115-124):
```kotlin
RecognitionEvidence(
    source = EvidenceSource.FILTERED_PASS_A,
    report = filtered.report,
    document = filtered.document ?: passA.document,
    physicalObservation = stillObservationId,
)
```

Note the default `recogniseRegion` lambda now passes `stillObservationId` through to `SelectedRegionRecognizer.recognise`, which Step 6 updates to accept and stamp it.

- [ ] **Step 6: Add `observationId` to `SelectedRegionRecognizer.recognise`**

```kotlin
fun recognise(
    source: Bitmap?,
    region: NormalizedRegion?,
    observationId: PhysicalObservationId,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): RecognitionEvidence? {
    // ... existing body unchanged until the RecognitionEvidence construction ...
    return RecognitionEvidence(
        source = EvidenceSource.SELECTED_REGION_OCR,
        report = report,
        document = document,
        elapsedMs = (System.nanoTime() - started) / 1_000_000,
        crop = crop,
        physicalObservation = observationId,
    )
}
```

- [ ] **Step 7: Update `LabelScannerScreen.kt`'s call site to generate and pass a real still ID**

At the site in `readSelectedTable` (or wherever `SelectedTableResolution.resolve` is invoked, per Task 3's rewiring), generate a stable per-capture id — the temp file name already created in `captureLabel()` (`File.createTempFile("justthecarbs-label-", ".jpg", ...)`, verified at line 1123) is a natural, already-unique per-attempt identifier:

```kotlin
val stillObservationId = PhysicalObservationId.forStill(file.name)
```//

Thread `stillObservationId` from `captureLabel()` through to wherever `readSelectedTable`/`SelectedTableResolution.resolve` is eventually called (likely via the same parameter-threading pattern used for `workGeneration` in Task 3), and pass it as the new required argument.

Also update the frozen-live-snapshot `RecognitionEvidence` construction from Task 3 (the `frozenLiveSnapshot?.candidate?.let { ... }` block) to stamp `physicalObservation = PhysicalObservationId.forLiveSnapshot(coordinator.aimEpoch, file.name)` — using the still's own capture id as the snapshot id is sufficient uniqueness here since one shutter press produces exactly one frozen snapshot.

- [ ] **Step 8: Run all tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS, including the now-passing third case of `PhysicalObservationProvenanceTest`. Any other test in the repo that calls `SelectedTableResolution.resolve` or `SelectedRegionRecognizer.recognise` positionally/without the new required parameter will fail to compile — fix each call site by supplying an explicit `PhysicalObservationId` (production callers use `forStill`, test callers may use a literal `PhysicalObservationId("test-fixture")` or similar, never `UNKNOWN`, per the review's "do not use UNKNOWN in production camera factories" instruction — test fixtures are not production, but using a real non-UNKNOWN id in tests too keeps the invariant visible and catches accidental omission).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/RecognitionEvidence.kt app/src/main/kotlin/app/justthecarbs/ocr/SelectedTableResolution.kt app/src/main/kotlin/app/justthecarbs/ocr/SelectedRegionRecognizer.kt app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt app/src/test/kotlin/app/justthecarbs/ocr/PhysicalObservationProvenanceTest.kt
git commit -m "$(cat <<'EOF'
Require a real PhysicalObservationId at every camera-derived evidence site

SelectedTableResolution.resolve and SelectedRegionRecognizer.recognise now
require an explicit PhysicalObservationId rather than silently defaulting
to UNKNOWN. Full-frame Pass A, filtered Pass A, and Strategy B recognition
of one still all share PhysicalObservationId.forStill(captureId), so they
can no longer accidentally satisfy DISTINCT_OCR_AGREEMENT against each
other -- closing the gap the 2026-09-04 audit flagged as making the
independent-observation verification route production-unreachable.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Close the wrong-prefill path — `Unsupported` scale requires a distinct physical observation to be shown

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/ReadingEligibility.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/AutomaticVerification.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/ReadingEligibilityTest.kt` (extend existing)

**Interfaces:**
- Consumes: `PhysicalObservationId` now-real provenance (Task 4).
- Produces: `ReadingEligibility.evaluate(...)` gains a new required distinction — the `corroborationSettlesScale: Boolean` parameter (already present, default `true`) is joined by making the *caller* (`AutomaticScanAdvance.eligibility`, verified `AutomaticScanAdvance.kt:207-281`) compute it correctly from real evidence instead of the hardcoded `true` literal verified at that call site. No new public API is strictly required if the existing `corroborationSettlesScale` parameter is wired correctly — this task's core work is **removing the hardcoded `corroborationSettlesScale = true`** at the one call site that currently ignores whether corroboration actually came from a distinct physical observation.

**This is the precise, narrow fix** identified by this plan's research (see "What the review got wrong" above): `AutomaticScanAdvance.kt`'s `eligibility()` function calls `ReadingEligibility.evaluate(..., corroborationSettlesScale = true)` unconditionally. Now that Task 4 makes `PhysicalObservationId` real, this can be computed correctly: `corroborationSettlesScale` should be `true` only when the verification's corroboration came from `AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT` with genuinely distinct observation ids (which, before Task 4, could never actually happen in production — everything shared `UNKNOWN`) or from `Route.CROSS_COLUMN` (which is legitimately scale-invariant-immune per the existing KDoc reasoning at `ReadingEligibility.kt:156-159`, since it compares structural ratios from the label's *own* other rows, not from a second view of the same digits). It must be `false` when corroboration is merely `agreesAcrossViews` (same-photograph agreement across recognition runs, `AutomaticVerification.kt:280-296`, verified to allow `confident.map { it.source.recognitionRun }.distinct().size >= 2` — which is satisfied by e.g. `FULL_FRAME_PASS_A` + `SELECTED_REGION_OCR` of one photograph, exactly the Hellmann's `13` case).

- [ ] **Step 1: Write the failing test reproducing the Hellmann's `1.3→13` case**

Add to `app/src/test/kotlin/app/justthecarbs/ocr/ReadingEligibilityTest.kt` (verify this file's existing structure first — it will already have tests for the `Established`/`Ambiguous` branches; add alongside them):

```kotlin
    @Test fun `Unsupported scale corroborated only by same-photograph view agreement is refused, not shown`() {
        // Reproduces the documented Hellmann's case: 1.3 g/100ml printed, every view of one
        // photograph reads 13g. Same-photograph agreement (agreesAcrossViews) must NOT be
        // sufficient to make this Eligible -- only a genuinely distinct physical observation
        // (a second photograph) or the label's own cross-column structure may.
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "13", reason = "no separator present"),
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(app.justthecarbs.domain.NutritionBasis.PER_100_ML),
            corroborated = true,
            corroborationSettlesScale = false, // same-photograph agreement, not a distinct observation
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Refused)
    }

    @Test fun `Unsupported scale corroborated by a genuinely distinct physical observation IS eligible`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "41", reason = "no separator present"),
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(app.justthecarbs.domain.NutritionBasis.PER_100_ML),
            corroborated = true,
            corroborationSettlesScale = true, // a second photograph, or cross-column structure
        )
        assertEquals(true, verdict is ReadingEligibility.Verdict.Eligible)
    }
```

(Note: `ReadingEligibility.evaluate`'s `corroborationSettlesScale` parameter already exists and this exact behavior is already implemented correctly *inside* `evaluate` itself, per the verified source — lines 146, 199. **These two tests should already pass against the current `ReadingEligibility.kt` unmodified.** The actual defect is not inside `evaluate`; it's at the caller. This step's tests exist to pin `evaluate`'s existing, correct behavior so Step 3's caller fix cannot accidentally regress it. Run them first to confirm they pass before touching the caller.)

- [ ] **Step 2: Confirm the `evaluate`-level tests pass unmodified (they should — this establishes the baseline before fixing the caller)**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ReadingEligibilityTest"`
Expected: PASS for both new cases — `evaluate` itself already implements the correct rule; it just isn't being asked the right question by its one caller.

- [ ] **Step 3: Write the failing test proving the caller currently ignores the distinction**

Add a new test to `app/src/test/kotlin/app/justthecarbs/ocr/AutomaticScanAdvanceTest.kt`. `EvidenceResolver.Outcome.NeedsVerification` has no direct-construction precedent anywhere in this repo's tests — every existing test obtains it by calling the real `EvidenceResolver.resolve(...)` and downcasting (confirmed by exhaustive search), so this test follows that same idiom rather than constructing `NeedsVerification` directly. A single confident pass with a separatorless value (`"13"`, no decimal point) is what makes `ScaleAmbiguity.check(document, candidate)` return `Unsupported` — reproducing the documented Hellmann's case:

```kotlin
    @Test fun `Unsupported-scale confident reading corroborated only by same-photograph agreement is NOT eligible for confirmation`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("Koolhydraten", OcrBox(0, 0, 100, 30), blockId = 0, lineId = 0),
                OcrElement("13g", OcrBox(200, 0, 250, 30), blockId = 0, lineId = 0, confidence = 0.9f),
            ),
        )
        val candidate = CarbCandidate(
            sourceLine = "Koolhydraten 13g",
            label = "Koolhydraten",
            value = BigDecimal("13"),
            basis = NutritionBasis.PER_100_ML,
            score = 120,
            geometry = OcrBox(200, 0, 250, 30),
            evidence = emptyList(),
        )
        val outcome = EvidenceResolver.resolve(
            listOf(
                RecognitionEvidence(
                    source = EvidenceSource.FULL_FRAME_PASS_A,
                    report = NutritionParseReport(LabelReading.Confident(candidate), emptyList()),
                    document = document,
                ),
            ),
        ) as? EvidenceResolver.Outcome.NeedsVerification
            ?: throw AssertionError("fixture must produce NeedsVerification — a single uncorroborated pass")

        // agreesAcrossViews-style corroboration: viewsAgree = true, route = NONE -- two recognition
        // RUNS of one photograph agreeing, never a structural or distinct-observation verification.
        val verification = AutomaticVerification.Verdict(route = AutomaticVerification.Route.NONE, viewsAgree = true)

        val isEligible = AutomaticScanAdvance.mayConfirm(outcome, verification, document)

        assertEquals(
            "same-photograph view agreement must not make an Unsupported-scale value confirmable",
            false,
            isEligible,
        )
    }
```

- [ ] **Step 4: Run test to verify it fails against the current, unfixed caller**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.AutomaticScanAdvanceTest"`
Expected: FAIL — `isEligible` is currently `true`, because `eligibility()` hardcodes `corroborationSettlesScale = true` regardless of what kind of corroboration `verification` actually represents.

- [ ] **Step 5: Fix `AutomaticScanAdvance.eligibility` to compute `corroborationSettlesScale` from the real verdict**

Modify the verified current implementation (`AutomaticScanAdvance.kt:207-281`):

```kotlin
fun eligibility(
    outcome: EvidenceResolver.Outcome,
    verification: AutomaticVerification.Verdict,
    document: OcrDocument?,
): ReadingEligibility.Verdict? {
    val confident = confidentReading(outcome) ?: return null
    return ReadingEligibility.evaluate(
        scale = scaleVerdict(outcome, document),
        basis = confident.candidate.basis?.let { app.justthecarbs.domain.CarbBasis.PerHundred(it) },
        corroborated = verification.mayBeProposed,
        // Only CROSS_COLUMN (the label's own structure) and DISTINCT_OCR_AGREEMENT (a genuinely
        // separate photograph, per PhysicalObservationId) can see absolute decimal scale.
        // `viewsAgree` alone -- agreement between recognition runs of ONE photograph -- inherits
        // the same pixels and cannot: it is what the Hellmann's 1.3 -> 13 case measured.
        corroborationSettlesScale = verification.route != AutomaticVerification.Route.NONE,
    )
}
```

This single-line change (`corroborationSettlesScale = true` → `corroborationSettlesScale = verification.route != AutomaticVerification.Route.NONE`) is the entire fix: `Route.NONE` is what `AutomaticVerification.verify` returns when only `agreesAcrossViews`-style same-photograph agreement holds (verified: `Verdict.mayBeProposed = mayAdvanceAutomatically || viewsAgree`, where `mayAdvanceAutomatically = route != Route.NONE` — so `mayBeProposed` can be `true` via `viewsAgree` alone while `route` stays `NONE`). `Route.CROSS_COLUMN` and `Route.DISTINCT_OCR_AGREEMENT` are the two routes that can see absolute scale, exactly matching `ReadingEligibility`'s own documented reasoning.

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.AutomaticScanAdvanceTest"`
Expected: PASS.

- [ ] **Step 7: Run the full JVM suite to confirm no regression — especially the documented `41g`/peanut-butter `11g` cases that must still be shown via `Recover`**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS, full baseline count plus additions. Specifically check `ScaleInvarianceTest`, `AutomaticScanAdvanceTest`, and `ReadingEligibilityTest` for zero regressions — per `ReadingEligibility.kt`'s own KDoc (lines 249-255), the `41g` and `11g` cases are expected to still reach the user via `Presentation.Recover`/focused entry (one step later, never as a prefilled proposal) — that path is untouched by this fix since it doesn't go through `mayConfirm`'s `Eligible` branch at all.

- [ ] **Step 8: Add a ground-truth-manifest-driven test asserting the Hellmann's case's forbidden value never appears via `ScanPresentationDecision`**

```kotlin
    @Test fun `the ground-truth Hellmann's case never reaches CONFIRM_ON_CAPTURE with 13 prefilled`() {
        val case = GroundTruthManifest.CASES.first { it.captureId == "20260904-113950-065" }
        // Build the outcome/verification/document that reproduces same-photograph agreement on
        // "13" with Unsupported scale, using this file's existing fixture helpers.
        val action = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)

        assertEquals(false, action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE)
        assertEquals(true, case.allowedFinalActions.contains(action.name))
    }
```

- [ ] **Step 9: Run and confirm this final test passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.AutomaticScanAdvanceTest"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/AutomaticScanAdvance.kt app/src/test/kotlin/app/justthecarbs/ocr/ReadingEligibilityTest.kt app/src/test/kotlin/app/justthecarbs/ocr/AutomaticScanAdvanceTest.kt
git commit -m "$(cat <<'EOF'
Stop same-photograph agreement from making an Unsupported-scale value confirmable

AutomaticScanAdvance.eligibility hardcoded corroborationSettlesScale=true
regardless of what kind of corroboration was actually present.
ReadingEligibility.evaluate already correctly distinguishes distinct-
observation/cross-column corroboration (which can see absolute decimal
scale) from same-photograph view agreement (which cannot, since every view
inherits the same pixels) -- but its one production caller was not asking
the right question.

Now corroborationSettlesScale is computed from the real verification
route: only CROSS_COLUMN and DISTINCT_OCR_AGREEMENT settle scale; NONE
(same-photograph agreement alone) does not. This closes the one path by
which the documented 1.3 g -> "13g" misread could still reach a one-tap
CONFIRM_ON_CAPTURE card. The value is not blocked outright -- it still
reaches the user through focused entry (Presentation.Recover), with the
photograph and stated basis preserved, exactly as ReadingEligibility's own
KDoc already specifies for the 41g/11g integer cases.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `ScanDecision` sealed type and `ScanDecisionEngine`

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/ScanDecision.kt`
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/ScanDecisionEngine.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/ScanDecisionEngineTest.kt`
- Modify: `app/src/androidTest/kotlin/app/justthecarbs/ocr/EvidencePipelineProductionTest.kt`

**Interfaces:**
- Consumes: `EvidenceResolver.Outcome`, `AutomaticVerification.Verdict`, `ScanPresentationDecision.Action`, `ScaleAmbiguity.Verdict` — all existing, verified.
- Produces:
  - `sealed interface ScanDecision` with variants `AutoAccept`, `Confirm`, `FocusedEntry`, `Conflict`, `Crop`.
  - `object ScanDecisionEngine { fun decide(evidence: List<RecognitionEvidence>, automatic: Boolean): ScanDecision }`.

**Design note:** this task does **not** replace `ScanPresentationDecision` — per this plan's verification, `ScanPresentationDecision.decide` is already a correct, pure, well-tested function (it is *exactly* what the review's "P1 — final policy is fragmented" complaint asks to be created, except it already exists and already routes `Recover`/`FOCUSED_AMOUNT_ENTRY` correctly). `ScanDecisionEngine` therefore **wraps** `ScanPresentationDecision.decide` and translates its `Action` enum into the richer `ScanDecision` sealed type the review asks for, adding the one thing `Action` doesn't carry: a typed, non-null `VerifiedReading` for `AutoAccept` that is *impossible to construct* except when every automatic-advance gate already verified in `AutomaticScanAdvance`/`ReadingEligibility` held. This gives the "AutoAccept must require positive verification" guarantee at the type level without duplicating the policy that already correctly lives in `AutomaticScanAdvance`.

- [ ] **Step 1: Write the failing test for the sealed type's construction constraints**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ScanDecisionEngineTest {

    // Verified fixture helpers, reused verbatim from EvidenceResolverTest.kt (this repo's own
    // existing pattern for constructing CarbCandidate / OcrDocument / RecognitionEvidence for
    // resolver-level tests) -- do not invent a different construction style.

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(100, 100, 200, 130),
        evidence = emptyList(),
    )

    private fun documentWithConfidence(confidence: Float?) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement(
                text = "53,5",
                box = OcrBox(100, 100, 200, 130),
                blockId = 0,
                lineId = 0,
                confidence = confidence,
            ),
        ),
    )

    private fun evidence(
        source: EvidenceSource,
        value: String?,
        basis: NutritionBasis? = NutritionBasis.PER_100_G,
        confidence: Float? = 0.9f,
    ): RecognitionEvidence {
        val reading = if (value != null) LabelReading.Confident(candidate(value, basis)) else LabelReading.NotFound
        return RecognitionEvidence(
            source = source,
            report = NutritionParseReport(reading, emptyList()),
            document = documentWithConfidence(confidence),
        )
    }

    @Test fun `two independent recognition runs agreeing on an Established-scale value becomes AutoAccept`() {
        // FULL_FRAME_PASS_A and SELECTED_REGION_OCR are two different recognitionRuns (PASS_A vs
        // SELECTED_REGION per EvidenceSource.recognitionRun), so their agreement is corroboration
        // AutomaticVerification recognizes -- and the candidate's own recognised text (a decimal
        // separator, "53,5") gives ScaleAmbiguity.Verdict.Established, which is what
        // mayAdvanceWithoutConfirmation requires. This combination is what ScanPresentationDecision
        // already resolves to AUTO_ADVANCE; ScanDecisionEngine must turn that into AutoAccept.
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "53.5"),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected AutoAccept, got $decision", decision is ScanDecision.AutoAccept)
    }

    @Test fun `a value only one pass found is not verified -- ScanDecision is Confirm, not AutoAccept`() {
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, null),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "2.3"),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected Confirm or FocusedEntry, got $decision", decision is ScanDecision.Confirm || decision is ScanDecision.FocusedEntry)
        assertTrue("must never silently AutoAccept an uncorroborated single-pass reading", decision !is ScanDecision.AutoAccept)
    }

    @Test fun `no evidence at all resolves to Crop, never AutoAccept`() {
        val decision = ScanDecisionEngine.decide(emptyList(), automatic = true)

        assertTrue(decision is ScanDecision.Crop || decision is ScanDecision.FocusedEntry)
        assertTrue(decision !is ScanDecision.AutoAccept)
    }

    @Test fun `passes disagreeing on the value become ScanDecision Conflict, never AutoAccept`() {
        // The exact fixture from EvidenceResolverTest's "passes disagreeing on the value are
        // conflicted and never pick one" -- 53.5 vs 9, from two different recognition runs, with no
        // structural corroboration for either.
        val evidenceList = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, "53.5"),
            evidence(EvidenceSource.SELECTED_REGION_OCR, "9"),
        )

        val decision = ScanDecisionEngine.decide(evidenceList, automatic = true)

        assertTrue("expected Conflict, got $decision", decision is ScanDecision.Conflict)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ScanDecisionEngineTest"`
Expected: FAIL — `ScanDecision`/`ScanDecisionEngine` do not exist yet.

- [ ] **Step 3: Write `ScanDecision.kt`**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The scanner's one final answer: what to show the user, given a bundle of recognition evidence.
 *
 * Every existing policy object ([EvidenceResolver], [AutomaticVerification], [ScaleAmbiguity],
 * [ReadingEligibility], [AutomaticScanAdvance], [ScanPresentationDecision]) already makes its own
 * correct judgement. This type does not re-decide any of them -- it is the terminal shape their
 * combined judgement is expressed as, so a caller (the UI) executes an exhaustive `when` over five
 * variants instead of asking six separately-named objects six separately-named questions.
 *
 * [AutoAccept] is the only variant that may navigate without a user tap. Its [VerifiedReading] is
 * only ever constructed by [ScanDecisionEngine] from a bundle that already passed every existing
 * automatic-advance gate -- see [ScanDecisionEngine.decide].
 */
sealed interface ScanDecision {

    /** A reading verified strongly enough to skip both confirmations entirely. */
    data class AutoAccept(val reading: VerifiedReading) : ScanDecision

    /** A confident, scale-established reading, not yet independently verified -- one tap confirms it. */
    data class Confirm(val value: BigDecimal, val basis: NutritionBasis, val rowText: String) : ScanDecision

    /** The row and basis are known; the digits are not safe to prefill. Amount starts blank. */
    data class FocusedEntry(val basis: NutritionBasis, val rowText: String) : ScanDecision

    /** Confident evidence disagreed and nothing resolves it. No candidate is preselected. */
    data class Conflict(val values: List<String>) : ScanDecision

    /** Nothing could be read or placed; the crop rectangle is the user's remaining lever. */
    data object Crop : ScanDecision
}

/**
 * A reading that has been verified strongly enough to reach the user with no confirmation step.
 *
 * The constructor is private: the only way to obtain one is [ScanDecisionEngine.decide] deciding
 * every gate already held. This is what makes "AutoAccept requires verification" a type-level fact
 * rather than a rule a caller must remember to check.
 */
data class VerifiedReading private constructor(
    val value: BigDecimal,
    val basis: NutritionBasis,
    val rowText: String,
) {
    companion object {
        internal fun of(value: BigDecimal, basis: NutritionBasis, rowText: String) =
            VerifiedReading(value, basis, rowText)
    }
}
```

- [ ] **Step 4: Write `ScanDecisionEngine.kt`**

```kotlin
package app.justthecarbs.ocr

/**
 * Wraps the existing, already-correct decision chain
 * ([EvidenceResolver] -> [AutomaticVerification] -> [ScanPresentationDecision]) into one
 * exhaustive [ScanDecision]. Adds no new policy: every threshold and rule this delegates to is
 * unchanged. What it adds is a type-level guarantee that only a bundle [ScanPresentationDecision]
 * itself resolved to [ScanPresentationDecision.Action.AUTO_ADVANCE] can ever produce a
 * [ScanDecision.AutoAccept] carrying a [VerifiedReading] -- constructible only inside this file.
 */
object ScanDecisionEngine {

    fun decide(evidence: List<RecognitionEvidence>, automatic: Boolean): ScanDecision {
        val outcome = EvidenceResolver.resolve(evidence)
        val document = evidence.firstOrNull { it.document != null }?.document
        val verification = AutomaticVerification.verify(evidence)
        val action = ScanPresentationDecision.decide(outcome, verification, document, automatic)

        return when (action) {
            ScanPresentationDecision.Action.AUTO_ADVANCE -> {
                val confident = AutomaticScanAdvance.confidentReading(outcome)
                val basis = confident?.candidate?.basis
                if (confident == null || basis == null) {
                    // Defensive: ScanPresentationDecision only returns AUTO_ADVANCE when
                    // AutomaticScanAdvance.mayAdvanceVerified held, which itself requires a
                    // non-null basis. Reaching here would mean the two disagree -- refuse rather
                    // than silently downgrade to a confirmation on a value we cannot describe.
                    ScanDecision.Conflict(values = listOfNotNull(confident?.candidate?.value?.toPlainString()))
                } else {
                    ScanDecision.AutoAccept(
                        VerifiedReading.of(confident.candidate.value, basis, confident.candidate.sourceLine),
                    )
                }
            }
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE, ScanPresentationDecision.Action.CONFIRM -> {
                val confident = AutomaticScanAdvance.confidentReading(outcome)
                val basis = confident?.candidate?.basis
                if (confident == null || basis == null) {
                    ScanDecision.Crop
                } else {
                    ScanDecision.Confirm(confident.candidate.value, basis, confident.candidate.sourceLine)
                }
            }
            ScanPresentationDecision.Action.RECOVERY -> {
                val target = FocusedAmountEntry.of(document)
                if (target != null) {
                    ScanDecision.FocusedEntry(target.basis, target.rowText)
                } else {
                    ScanDecision.Crop
                }
            }
            ScanPresentationDecision.Action.CROP_FALLBACK -> ScanDecision.Crop
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY -> {
                val target = FocusedAmountEntry.of(document)
                if (target != null) {
                    ScanDecision.FocusedEntry(target.basis, target.rowText)
                } else {
                    ScanDecision.Crop
                }
            }
        }
    }
}
```

`CarbCandidate.sourceLine` is confirmed as the correct field for "the printed row text" — verified directly against `CarbCandidate`'s real constructor (`data class CarbCandidate(val sourceLine: String, val label: String, val value: BigDecimal, val basis: NutritionBasis?, val score: Int, val geometry: OcrBox, val evidence: List<CandidateEvidence>, val column: NutritionColumnKind? = null)`, `NutritionTableParser.kt:31-58`; note its `init` block requires `column` to be `null`, `PER_100_G`, or `PER_100_ML` — never omit that constraint when constructing test fixtures with a non-null `column`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ScanDecisionEngineTest"`
Expected: PASS (4 tests, all using the verified fixture helpers written directly into the test file above — no unresolved placeholders remain in this task's test code).

- [ ] **Step 6: Migrate `EvidencePipelineProductionTest`'s safety assertion off the deprecated `mayAdvance(outcome)` call**

Per `.audits/architectural-analysis-2026-09-04.md`'s P1 finding (`EvidencePipelineProductionTest.kt:147-176`): `theFastPathAdvancesOnlyOnConfidentlyResolvedFixtures` currently calls `AutomaticScanAdvance.mayAdvance(result.outcome)` directly, which checks only that the outcome is structurally `Resolved`+`Confident` — not that it passed verification. Read the current test body in full first, then replace the direct `mayAdvance` call with `ScanDecisionEngine.decide(evidence, automatic = true) is ScanDecision.AutoAccept` and re-assert the same safety property ("advancing implies Confident and non-null basis") through the new engine instead:

```kotlin
// Before (per the audit): AutomaticScanAdvance.mayAdvance(result.outcome) -- checks structural
// confidence only, not verification.
// After: the full ScanDecisionEngine path, which requires ScanPresentationDecision to have
// actually resolved AUTO_ADVANCE (i.e. mayAdvanceVerified, not just mayAdvance).
val decision = ScanDecisionEngine.decide(evidence, automatic = true)
val advances = decision is ScanDecision.AutoAccept
```

- [ ] **Step 7: Run the connected test if a device is available; otherwise leave this gate explicitly open**

Run (if `adb devices` lists a device):
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.EvidencePipelineProductionTest
```
Expected: PASS, with the same four canaries (sondey, kinder, yoghurt, stokbrood) advancing and grated-cheese/witte-kaas/jar/lid correctly refusing — this is the exact table recorded in CLAUDE.md's "OCR quick calculation" section under "Automatic fast path". If no device is available, record explicitly that this connected suite was not run and this specific regression check remains open.

- [ ] **Step 8: Run the full JVM suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/ScanDecision.kt app/src/main/kotlin/app/justthecarbs/ocr/ScanDecisionEngine.kt app/src/test/kotlin/app/justthecarbs/ocr/ScanDecisionEngineTest.kt app/src/androidTest/kotlin/app/justthecarbs/ocr/EvidencePipelineProductionTest.kt
git commit -m "$(cat <<'EOF'
Add ScanDecisionEngine wrapping the existing decision chain in one sealed type

ScanPresentationDecision.decide() was already the correct, pure, tested
final-action decision (contrary to the review's assumption that no such
consolidation existed) -- but its Action enum carries no VerifiedReading
guarantee, so a caller could in principle treat any Action as navigable.

ScanDecisionEngine wraps it: AutoAccept can only be constructed from a
bundle ScanPresentationDecision itself resolved to AUTO_ADVANCE, and
VerifiedReading's constructor is private to this file. No new policy is
added; every threshold this delegates to is unchanged.

Also migrates EvidencePipelineProductionTest off the deprecated
AutomaticScanAdvance.mayAdvance(outcome) call the 2026-09-04 audit flagged
as checking structural confidence only, not verification.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Safe conflict adjudication

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/ConflictAdjudication.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/ConflictAdjudicationTest.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/EvidenceResolver.kt`

**Interfaces:**
- Consumes: `RecognitionEvidence` (existing), `CrossColumnRatioCheck` (existing, verified `internal object` at `CrossColumnRatioCheck.kt:58-89` with `Verdict.Consistent`/`Verdict.Conflicting`/`Verdict.NotEnoughEvidence` — see Step 1 below).
- Produces: `object ConflictAdjudication { fun adjudicate(groups: Map<Pair<BigDecimal, NutritionBasis?>, List<RecognitionEvidence>>): AdjudicationResult }` where `AdjudicationResult` is a sealed type with `SingleSupported(evidence: List<RecognitionEvidence>)` and `StillConflicted(groups: ...)`.

- [ ] **Step 1: `CrossColumnRatioCheck.kt`'s real `Verdict` shape (already verified for this plan — do not re-guess)**

Verified directly from `app/src/main/kotlin/app/justthecarbs/ocr/CrossColumnRatioCheck.kt:58-89`. The review's Task 7 language ("structurally Contradicted"/"positively Verified") does **not** match the real type — use these exact names:

```kotlin
internal object CrossColumnRatioCheck {
    sealed interface Verdict {
        data class Consistent(val medianRatio: Double, val candidateRatio: Double, val supportingRows: Int) : Verdict
        data class Conflicting(val medianRatio: Double, val candidateRatio: Double, val supportingRows: Int) : Verdict
        data class NotEnoughEvidence(val coherentRows: Int) : Verdict
    }

    fun check(document: OcrDocument, candidate: CarbCandidate): Verdict
}
```

Note `CrossColumnRatioCheck` is `internal object` (package-internal), so `ConflictAdjudication` (same `app.justthecarbs.ocr` package) can call `check` directly — no visibility change needed. Map the review's vocabulary onto the real one: "structurally contradicted" = `Verdict.Conflicting`; "positively verified" = `Verdict.Consistent`; "uncheckable" = `Verdict.NotEnoughEvidence`.

- [ ] **Step 2: Write the failing tests for grouping and adjudication rules**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ConflictAdjudicationTest {

    // Reused verbatim from EvidenceResolverTest.kt's verified fixture pattern (see Task 6's
    // ScanDecisionEngineTest for the same helpers) -- CarbCandidate/OcrDocument/RecognitionEvidence
    // construction must match this repo's existing style exactly.

    private fun candidate(value: String, basis: NutritionBasis?) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = basis,
        score = 120,
        geometry = OcrBox(100, 100, 200, 130),
        evidence = emptyList(),
    )

    /**
     * A document whose OTHER rows (energie/vet/eiwit at fixed ratios to a 100g/serving pair) make
     * CrossColumnRatioCheck.check(document, candidate) return Consistent for a candidate whose row
     * matches that ratio, and Conflicting for one that doesn't -- mirroring the real four-row
     * fixture documented in CLAUDE.md's "Verified automatic advancement" section (the cracker
     * canary: energie 135/432=0.313, fat 3.4/11=0.309, etc., median ~0.31).
     *
     * For this fixture, a candidate row ratio near 0.31 is Consistent; a candidate row ratio far
     * from it (e.g. the documented 1.875 misread) is Conflicting.
     */
    private fun tableWithRatio(rowValue: String, otherColumnValue: String) = OcrDocument(
        width = 1000,
        height = 1000,
        elements = listOf(
            OcrElement("Energie", OcrBox(0, 0, 100, 30), blockId = 0, lineId = 0),
            OcrElement("135", OcrBox(200, 0, 250, 30), blockId = 0, lineId = 0),
            OcrElement("432", OcrBox(300, 0, 350, 30), blockId = 0, lineId = 0),
            OcrElement("Vet", OcrBox(0, 40, 100, 70), blockId = 1, lineId = 0),
            OcrElement("3.4", OcrBox(200, 40, 250, 70), blockId = 1, lineId = 0),
            OcrElement("11", OcrBox(300, 40, 350, 70), blockId = 1, lineId = 0),
            OcrElement("Koolhydraten", OcrBox(0, 80, 100, 110), blockId = 2, lineId = 0),
            OcrElement(otherColumnValue, OcrBox(200, 80, 250, 110), blockId = 2, lineId = 0),
            OcrElement(rowValue, OcrBox(300, 80, 350, 110), blockId = 2, lineId = 0),
        ),
    )

    private fun evidenceGroup(value: String, document: OcrDocument): List<RecognitionEvidence> = listOf(
        RecognitionEvidence(
            source = EvidenceSource.FULL_FRAME_PASS_A,
            report = NutritionParseReport(LabelReading.Confident(candidate(value, NutritionBasis.PER_100_G)), emptyList()),
            document = document,
        ),
    )

    @Test fun `a group explicitly contradicted by its own table loses to a group the same table supports`() {
        // 12/100g against a serving column of ~37.5 (ratio 3.1, wildly inconsistent with the
        // table's other rows at ~0.31) vs 72/100g against ~22.5 (ratio 0.3125, consistent).
        val contradicted = evidenceGroup("12", tableWithRatio(rowValue = "12", otherColumnValue = "37.5"))
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val groups = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue("expected SingleSupported, got $result", result is ConflictAdjudication.AdjudicationResult.SingleSupported)
    }

    @Test fun `two groups both uncheckable (not enough table rows) remains a conflict`() {
        val sparseDocument = OcrDocument(
            width = 100, height = 100,
            elements = listOf(OcrElement("Koolhydraten", OcrBox(0, 0, 50, 20), blockId = 0, lineId = 0)),
        )
        val groupA = evidenceGroup("12", sparseDocument)
        val groupB = evidenceGroup("72", sparseDocument)
        val groups = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to groupA,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to groupB,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue(result is ConflictAdjudication.AdjudicationResult.StillConflicted)
    }

    @Test fun `a supported group versus an uncheckable group remains a conflict -- uncheckable is not the same as contradicted`() {
        val sparseDocument = OcrDocument(
            width = 100, height = 100,
            elements = listOf(OcrElement("Koolhydraten", OcrBox(0, 0, 50, 20), blockId = 0, lineId = 0)),
        )
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val uncheckable = evidenceGroup("12", sparseDocument)
        val groups = mapOf(
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to uncheckable,
        )

        val result = ConflictAdjudication.adjudicate(groups)

        assertTrue(
            "a supported candidate does not automatically defeat one that simply cannot be checked",
            result is ConflictAdjudication.AdjudicationResult.StillConflicted,
        )
    }

    @Test fun `adjudication is order-independent -- swapping map insertion order gives the same result`() {
        val contradicted = evidenceGroup("12", tableWithRatio(rowValue = "12", otherColumnValue = "37.5"))
        val supported = evidenceGroup("72", tableWithRatio(rowValue = "72", otherColumnValue = "22.5"))
        val forward = mapOf(
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
        )
        val reversed = mapOf(
            (BigDecimal("72") to NutritionBasis.PER_100_G as NutritionBasis?) to supported,
            (BigDecimal("12") to NutritionBasis.PER_100_G as NutritionBasis?) to contradicted,
        )

        val resultForward = ConflictAdjudication.adjudicate(forward) as ConflictAdjudication.AdjudicationResult.SingleSupported
        val resultReversed = ConflictAdjudication.adjudicate(reversed) as ConflictAdjudication.AdjudicationResult.SingleSupported

        assertEquals(resultForward.evidence, resultReversed.evidence)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ConflictAdjudicationTest"`
Expected: FAIL — `ConflictAdjudication` does not exist.

- [ ] **Step 4: Write `ConflictAdjudication.kt`**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * Adjudicates disagreement between distinct (value, basis) groups of confident evidence using each
 * group's OWN document structure -- never by confidence, magnitude, frequency, source order, or
 * plausibility.
 *
 * A group is eliminated only when [CrossColumnRatioCheck] explicitly returns [CrossColumnRatioCheck.Verdict.Conflicting]
 * for it AND a competing group gets [CrossColumnRatioCheck.Verdict.Consistent] from the same check.
 * A group whose check returns [CrossColumnRatioCheck.Verdict.NotEnoughEvidence] is never eliminated
 * merely because a competing group happens to be [CrossColumnRatioCheck.Verdict.Consistent] --
 * "supported vs uncheckable" stays a conflict, because absence of contradiction is not the same
 * claim as presence of support.
 */
object ConflictAdjudication {

    sealed interface AdjudicationResult {
        data class SingleSupported(val evidence: List<RecognitionEvidence>) : AdjudicationResult
        data class StillConflicted(val groups: Map<Pair<BigDecimal, NutritionBasis?>, List<RecognitionEvidence>>) : AdjudicationResult
    }

    fun adjudicate(
        groups: Map<Pair<BigDecimal, NutritionBasis?>, List<RecognitionEvidence>>,
    ): AdjudicationResult {
        data class Judged(
            val key: Pair<BigDecimal, NutritionBasis?>,
            val evidence: List<RecognitionEvidence>,
            val verdict: CrossColumnRatioCheck.Verdict?,
        )

        val judged = groups.map { (key, groupEvidence) ->
            val primary = groupEvidence.first()
            val document = primary.document
            val candidate = (primary.reading as? LabelReading.Confident)?.candidate
            val verdict = if (document != null && candidate != null) {
                CrossColumnRatioCheck.check(document, candidate)
            } else {
                null
            }
            Judged(key = key, evidence = groupEvidence, verdict = verdict)
        }

        val anySupported = judged.any { it.verdict is CrossColumnRatioCheck.Verdict.Consistent }
        val remaining = judged.filterNot { it.verdict is CrossColumnRatioCheck.Verdict.Conflicting && anySupported }
        val supportedRemaining = remaining.filter { it.verdict is CrossColumnRatioCheck.Verdict.Consistent }

        return if (supportedRemaining.size == 1 && remaining.size == 1) {
            AdjudicationResult.SingleSupported(supportedRemaining.single().evidence)
        } else {
            AdjudicationResult.StillConflicted(groups)
        }
    }
}
```

- [ ] **Step 5: Run tests, iterating on the implementation until all pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ConflictAdjudicationTest"`
Expected: PASS, all cases including order-independence and the uncheckable-vs-supported distinction.

- [ ] **Step 6: Wire `ConflictAdjudication` into `EvidenceResolver` for the `Conflicted` outcome path**

Read `EvidenceResolver.kt`'s `resolve()` function (verified 139-line span, `EvidenceResolver.kt:168`) in full, find where it currently constructs `Outcome.Conflicted` on confident-value disagreement, and insert a call to `ConflictAdjudication.adjudicate` before giving up — if it returns `SingleSupported`, resolve to `Outcome.Resolved` (or `NeedsVerification`, depending on whether the surviving group's own corroboration already satisfies verification — follow the existing pattern for how `Resolved` vs `NeedsVerification` is chosen elsewhere in this function) using the surviving group's evidence; if `StillConflicted`, proceed to construct `Outcome.Conflicted` exactly as today.

- [ ] **Step 7: Add a regression test proving a previously-conflicted case now resolves when one group is structurally supported and the other contradicted**

```kotlin
    @Test fun `EvidenceResolver resolves a conflict when one group is structurally contradicted by its own table`() {
        // Reuse the same fixture shapes as ConflictAdjudicationTest's first case, wrapped as a
        // call to EvidenceResolver.resolve(evidence) instead of ConflictAdjudication.adjudicate
        // directly, proving the wiring in Step 6 works end-to-end.
    }
```

- [ ] **Step 8: Run the full JVM suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS, no regression to any existing `EvidenceResolverTest` case (especially the ones proving genuine, unresolvable conflicts like grated cheese still refuse).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ocr/ConflictAdjudication.kt app/src/main/kotlin/app/justthecarbs/ocr/EvidenceResolver.kt app/src/test/kotlin/app/justthecarbs/ocr/ConflictAdjudicationTest.kt
git commit -m "$(cat <<'EOF'
Add ConflictAdjudication: a supported candidate can defeat a contradicted one

EvidenceResolver previously refused ALL confident disagreement uniformly,
even when one candidate's own table structure explicitly contradicted it
and a competing candidate was independently, structurally supported.
ConflictAdjudication groups confident evidence by exact (value, basis),
evaluates each group against its own document via CrossColumnRatioCheck,
and eliminates a group only when it is both explicitly contradicted AND a
competing group is positively verified -- never by confidence, magnitude,
frequency, source order, or plausibility. Supported-vs-uncheckable remains
a conflict, since absence of contradiction is not the same as presence of
support.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Fix onboarding-completion failure handling

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/onboarding/OnboardingCompletionStateTest.kt` (new)
- Modify: `app/src/test/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModelTest.kt` (extend existing — verified 4 cases already exist per CLAUDE.md's startup-hardening section)

**Interfaces:**
- Consumes: `SettingsRepository.setHasSeenOnboarding` (existing).
- Produces: `OnboardingViewModel.completionState: StateFlow<CompletionState>` where `sealed interface CompletionState { data object Idle : CompletionState; data object Saving : CompletionState; data class Failed(val message: String) : CompletionState; data object Saved : CompletionState }`. `OnboardingViewModel.complete()` remains `suspend fun` (per the existing correct mutex-guarded design) but now updates `completionState` through the sequence and does not swallow the repository exception silently — it catches, sets `Failed`, and does **not** rethrow (the caller observes state rather than catching an exception), per this repo's own established pattern for surfacing failures as state (`ManualEntryViewModel`'s `quickSaveFailed` pattern, referenced in CLAUDE.md's "Startup hardening" section).

- [ ] **Step 1: Write the failing test for the new state machine**

**`SettingsRepository` is a concrete `class`, not an interface** — verified directly (`app/src/main/kotlin/app/justthecarbs/data/settings/SettingsRepository.kt:21-23`): its only usable constructor for tests is `SettingsRepository.forTesting(store: DataStore<Preferences>)`, which the existing `OnboardingViewModelTest.kt` already uses. A failure is injected by wrapping a real temp-file `DataStore<Preferences>` in a Kotlin delegation object that overrides `updateData` to throw — the exact pattern the existing `` `a second call to complete after the first returns writes to the store only once` `` test already uses for counting (verified at `OnboardingViewModelTest.kt:88-111`), adapted here to throw instead of count.

```kotlin
package app.justthecarbs.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.justthecarbs.data.settings.SettingsRepository
import app.justthecarbs.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class OnboardingCompletionStateTest {

    private fun tempStore(): DataStore<Preferences> {
        val dir = File.createTempFile("onboarding-completion-test", "").apply { delete(); mkdirs() }
        return PreferenceDataStoreFactory.create(produceFile = { File(dir, "settings.preferences_pb") })
    }

    private fun succeedingRepository(): SettingsRepository = SettingsRepository.forTesting(tempStore())

    /** Fails every write. Mirrors OnboardingViewModelTest's countingStore decorator, but throws. */
    private fun alwaysThrowingRepository(): SettingsRepository {
        val real = tempStore()
        val throwing = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("simulated write failure")
            }
        }
        return SettingsRepository.forTesting(throwing)
    }

    /** Fails the first write, succeeds on every write after that. */
    private fun failOnceThenSucceedRepository(): SettingsRepository {
        val real = tempStore()
        var attempts = 0
        val flaky = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                attempts++
                if (attempts == 1) throw IOException("simulated write failure")
                return real.updateData(transform)
            }
        }
        return SettingsRepository.forTesting(flaky)
    }

    @Test fun `complete moves to Saved on a successful write`() = runTest {
        val viewModel = OnboardingViewModel(succeedingRepository())

        assertEquals(OnboardingViewModel.CompletionState.Idle, viewModel.completionState.value)
        viewModel.complete()
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test fun `a repository failure sets Failed and never Saved -- the button must be re-enabled`() = runTest {
        val viewModel = OnboardingViewModel(alwaysThrowingRepository())

        viewModel.complete()

        assertTrue(viewModel.completionState.value is OnboardingViewModel.CompletionState.Failed)
    }

    @Test fun `retrying after a failure and succeeding reaches Saved`() = runTest {
        val viewModel = OnboardingViewModel(failOnceThenSucceedRepository())

        viewModel.complete() // fails
        assertTrue(viewModel.completionState.value is OnboardingViewModel.CompletionState.Failed)

        viewModel.complete() // retries, succeeds
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }

    @Test fun `calling complete again after Saved does not re-run the write`() = runTest {
        val real = tempStore()
        var writeCount = 0
        val counting = object : DataStore<Preferences> by real {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                writeCount++
                return real.updateData(transform)
            }
        }
        val viewModel = OnboardingViewModel(SettingsRepository.forTesting(counting))

        viewModel.complete()
        viewModel.complete()

        assertEquals(1, writeCount)
        assertEquals(OnboardingViewModel.CompletionState.Saved, viewModel.completionState.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.onboarding.OnboardingCompletionStateTest"`
Expected: FAIL — `CompletionState`/`completionState` do not exist yet.

- [ ] **Step 3: Rewrite `OnboardingViewModel`**

```kotlin
package app.justthecarbs.ui.onboarding

import androidx.lifecycle.ViewModel
import app.justthecarbs.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** First-launch carousel state: which of the 3 slides is showing, and marking it seen on exit. */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    sealed interface CompletionState {
        data object Idle : CompletionState
        data object Saving : CompletionState
        data class Failed(val message: String) : CompletionState
        data object Saved : CompletionState
    }

    private val _slideIndex = MutableStateFlow(0)
    val slideIndex: StateFlow<Int> = _slideIndex.asStateFlow()

    private val _completionState = MutableStateFlow<CompletionState>(CompletionState.Idle)
    val completionState: StateFlow<CompletionState> = _completionState.asStateFlow()

    private val completeMutex = Mutex()

    fun next() {
        _slideIndex.value = (_slideIndex.value + 1).coerceAtMost(LAST_SLIDE)
    }

    fun showSlide(index: Int) {
        _slideIndex.value = index.coerceIn(0, LAST_SLIDE)
    }

    fun skip() {
        _slideIndex.value = LAST_SLIDE
    }

    /**
     * Persists `hasSeenOnboarding = true` and updates [completionState] to reflect the outcome.
     *
     * A [Mutex] guards against two callers racing (a rapid double tap on *Get started*) starting
     * two DataStore edits. Unlike the previous design, a repository failure now sets
     * [CompletionState.Failed] rather than leaving the caller's own local "in progress" flag stuck
     * true forever -- the caller observes this state and re-enables its own UI on Failed. A retry
     * (calling [complete] again after a Failed state) is a normal, supported second attempt: the
     * mutex does not remember the previous failure, only whether a write is currently in flight or
     * has already durably succeeded.
     */
    suspend fun complete() {
        completeMutex.withLock {
            if (_completionState.value is CompletionState.Saved) return
            _completionState.value = CompletionState.Saving
            try {
                settingsRepository.setHasSeenOnboarding(true)
                _completionState.value = CompletionState.Saved
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _completionState.value = CompletionState.Failed(e.message ?: "Could not save")
            }
        }
    }

    private companion object {
        const val LAST_SLIDE = 2
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.onboarding.OnboardingCompletionStateTest"`
Expected: PASS.

- [ ] **Step 5: Run the existing `OnboardingViewModelTest` to confirm no regression to the 4 pre-existing cases**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS — the pre-existing "idempotent complete()", "exactly one write across three calls", and "two concurrent callers" cases must all still pass unmodified against the new state-based implementation (they assert on write counts and repeated-call behavior, not on the removed bare `completed: Boolean`, so they should be source-compatible; if any directly referenced the old private field, fix the test to observe `completionState` instead).

- [ ] **Step 6: Update `JustTheCarbsNavHost.kt` to observe `completionState` instead of the local `completing` boolean**

Read the current onboarding composable block in full (verified lines ~218-247) before editing. Replace:

```kotlin
var completing by remember { mutableStateOf(false) }

OnboardingScreen(
    slideIndex = slideIndex,
    onNext = viewModel::next,
    onSkip = viewModel::skip,
    onSlideChanged = viewModel::showSlide,
    onGetStarted = {
        if (!completing) {
            completing = true
            coroutineScope.launch {
                viewModel.complete()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
        }
    },
)
```

with:

```kotlin
val completionState by viewModel.completionState.collectAsStateWithLifecycle()

LaunchedEffect(completionState) {
    if (completionState is OnboardingViewModel.CompletionState.Saved) {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.ONBOARDING) { inclusive = true }
        }
    }
}

OnboardingScreen(
    slideIndex = slideIndex,
    onNext = viewModel::next,
    onSkip = viewModel::skip,
    onSlideChanged = viewModel::showSlide,
    // Disabled only while a write is genuinely in flight -- Idle and Failed both allow a tap
    // (Failed is a retry, not a re-disable), so a DataStore failure can no longer leave this
    // button permanently unusable.
    onGetStarted = {
        if (completionState !is OnboardingViewModel.CompletionState.Saving) {
            coroutineScope.launch { viewModel.complete() }
        }
    },
    completionError = (completionState as? OnboardingViewModel.CompletionState.Failed)?.message,
)
```

This requires `OnboardingScreen` to accept a new optional `completionError: String?` parameter to render a retryable error message — check `OnboardingScreen.kt`'s current signature and add this parameter, rendering it as a small dismissible/persistent text near the *Get started* button (follow this repo's existing pattern for inline failure messages, e.g. `quickSaveFailed` in `ManualEntryViewModel`/`ManualEntryScreen`, per CLAUDE.md's documented convention of "say it out loud" rather than swallowing failures).

- [ ] **Step 7: Compile and run any existing `OnboardingScreen`/`JustTheCarbsNavHost` instrumented tests**

Run:
```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```
If a device is available, run any existing onboarding instrumented test (locate with `find app/src/androidTest -iname "*Onboarding*"`) and confirm it still passes; update it if it references the removed `completing` local directly.

- [ ] **Step 8: Run the full JVM suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/app/justthecarbs/ui/onboarding/OnboardingViewModel.kt app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt app/src/test/kotlin/app/justthecarbs/onboarding/OnboardingCompletionStateTest.kt
git commit -m "$(cat <<'EOF'
Fix onboarding: a DataStore write failure no longer permanently disables Get started

JustTheCarbsNavHost's local `completing` boolean was set true before
calling viewModel.complete() and never reset on failure, so an exception
from SettingsRepository.setHasSeenOnboarding left the button disabled
forever with no way to retry and no error shown.

OnboardingViewModel now exposes an observable CompletionState
(Idle/Saving/Failed/Saved) instead of a bare completed boolean. The
NavHost navigates only on Saved, shows a retryable error on Failed, and
re-enables the button (Idle and Failed both permit a tap; only Saving
disables it).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Correct the CameraPermissionState documentation error

**Files:**
- Modify: `CLAUDE.md` (the one line the review flagged as incorrect)

**Interfaces:** none — documentation-only.

The review's own prompt asks to "correct the statement that CameraPermissionState has five states: it has four states; ON_RESUME is an event/transition." This plan's research did not re-verify `CameraPermissionGate.kt` directly (it was out of scope for the P0/P1 investigation), so this task starts with verification, not a blind edit.

- [ ] **Step 1: Read `CameraPermissionGate.kt` and confirm the actual state count**

Read the file at `app/src/main/kotlin/app/justthecarbs/ui/scan/CameraPermissionGate.kt` in full. Confirm the `CameraPermissionState` sealed type's exact variant list. Per CLAUDE.md's own "Startup hardening" section, the states are documented there as "`Granted` / `NotRequested` / `DeniedCanAskAgain` / `PermanentlyDenied`" — four states — with `ON_RESUME` described as a lifecycle recheck trigger, not a state. Confirm this matches the actual sealed type definition.

- [ ] **Step 2: Search for any place claiming "five states" or similar**

Run:
```powershell
grep -rn "five state\|5 state" CLAUDE.md docs/
```
If a match is found (the review claims this exists somewhere in the docs, though it was not found in the `CLAUDE.md` excerpt reviewed for this plan), correct it to state four states with `ON_RESUME` described as an event/transition, matching the existing accurate description already present elsewhere in CLAUDE.md's startup-hardening section. If no such claim exists anywhere in the tracked docs, record in the final report that this specific review claim could not be located and no doc change was needed.

- [ ] **Step 3: Commit if a change was made**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
Correct CameraPermissionState state count in documentation

CameraPermissionState has four states (Granted/NotRequested/
DeniedCanAskAgain/PermanentlyDenied); ON_RESUME is a lifecycle recheck
event/transition, not a fifth state.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

(Skip the commit if Step 2 found nothing to correct.)

---

## Task 10: Design note, CHANGELOG entry, and manual-QA gate rows

**Files:**
- Create: `docs/ocr-evidence-provenance-design.md`
- Modify: `CHANGELOG.md` (append to the existing open `## 1.0.4` heading only)
- Modify: `docs/manual-qa.md` (append new gate rows)

**Interfaces:** none — documentation.

- [ ] **Step 1: Write the design note**

```markdown
# Aim epoch, work generation, and physical-observation IDs

Written 2026-09-05, as part of the OCR evidence-reliability pass (Tasks 1-8 of
`docs/superpowers/plans/2026-09-05-ocr-evidence-reliability.md`).

## The problem in one sentence

`LabelScannerScreen`'s single `captureSession: AtomicLong` answered two different questions with
one number, and the number bumped at the time that was right for one question and wrong for the
other.

## Two questions, two counters

**Work generation** — "is this in-flight asynchronous callback still current, or has the user moved
on to a different attempt?" This must bump on every new shutter press, so a still-recognition
result from an abandoned capture cannot land on top of a newer one.

**Aim epoch** — "which pre-shutter live-camera stream does this frame belong to?" This must NOT
bump on a shutter press. A live frame recorded while the user is framing the shot is, by
definition, recorded *before* the tap that produces the still it should be able to corroborate.
Bumping this counter at the moment of the tap retroactively excludes every frame that led up to
it.

`CaptureEvidenceCoordinator` (`app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt`)
is two `AtomicLong`s, `beginNewAim()` and `beginNewWork()`, called at the right two different
moments: `beginNewAim()` on retake/dispose/leaving-and-re-entering the screen; `beginNewWork()` at
the start of every shutter press.

## Shutter snapshot semantics

`freezeAtShutter(buffer, nowElapsed)` is called at the very start of the shutter handler, before
`beginNewWork()`, before the analyzer is paused, before autofocus, before the image is captured.
It reads `LiveEvidenceBuffer`'s current consensus for the current aim epoch and returns an
immutable `LiveEvidenceSnapshot`. That snapshot — not a later live query of the buffer — is what
flows into the still-recognition pipeline.

This matters because the still pipeline can take anywhere from ~480ms to ~2.5s on real hardware
(recorded in `.audits/architectural-analysis-2026-09-04.md` and CLAUDE.md's various device-session
sections), comfortably longer than `LiveEvidenceBuffer`'s 1500ms consensus window. A query issued
*after* the still pipeline finishes can find the window has already closed on genuinely good
evidence that was present at the moment that actually mattered — the shutter press.

## Physical-observation IDs

`PhysicalObservationId` (`RecognitionEvidence.kt`) already existed as a type before this pass, but
every production construction site left it at the default `UNKNOWN` sentinel, which made
`AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT` structurally unreachable — two pieces of
evidence both defaulting to `UNKNOWN` can never be "distinct".

`PhysicalObservationId.forStill(captureId)` and `.forLiveSnapshot(aimEpoch, snapshotId)` are the two
factories production code now uses. Every recognition derived from one quality-still JPEG — the
whole-frame Pass A, the filtered re-parse, a Strategy B re-recognition of a crop of the same
bitmap — shares one `forStill(...)` id, because they all read the same ink. Only a frame from the
frozen pre-shutter live snapshot, which genuinely came from different sensor frames, gets a
different id and can therefore corroborate the still.

## What this does not change

No confidence threshold moved. No parser rule was relaxed. `ReadingEligibility`'s existing
`Established`/`Ambiguous`/`Unsupported` scale logic, `ScaleAmbiguity`'s existing checks, and
`ScanPresentationDecision`'s existing routing are all unchanged in their own rules — this pass fixed
the *evidence* those rules see (which frames survive to be considered, and whether "corroboration"
can mean the same photograph agreeing with itself), and fixed one caller
(`AutomaticScanAdvance.eligibility`) that was answering `ReadingEligibility.evaluate`'s
`corroborationSettlesScale` question incorrectly by always saying `true`.
```

- [ ] **Step 2: Add the CHANGELOG entry under the existing open `## 1.0.4` heading**

Read the current `## 1.0.4` section in full (verified to start at line 69, with an existing `### Fixed — startup hardening (2026-09-04/05)` subsection) before editing, and append a new subsection immediately after it:

```markdown
### Fixed — OCR evidence lifecycle (2026-09-05)

Live pre-shutter camera evidence was being recorded under one identity and queried under another,
so it was almost never actually available to corroborate a still capture -- not because of a wiring
slip between two call sites, but because a single counter (`captureSession`) was used both to
identify "which pre-shutter aim does this frame belong to" and "is this async work still current",
and those two questions need different bump timing. `CaptureEvidenceCoordinator` separates them.
Live evidence is now frozen atomically at the moment the shutter fires, before any other
shutter-handling side effect runs, so OCR latency (measured 477-2458ms on real hardware) can no
longer expire evidence that was genuinely present when the user pressed the shutter.

Every camera-derived recognition (`FULL_FRAME_PASS_A`, `FILTERED_PASS_A`, `SELECTED_REGION_OCR`) now
carries a real `PhysicalObservationId` identifying which physical photograph it came from, instead
of defaulting to `UNKNOWN` -- closing the gap that made the independent-observation verification
route (`DISTINCT_OCR_AGREEMENT`) structurally unreachable in production.

Closed the one path by which same-photograph recognition-run agreement (never a second physical
observation) could make an `Unsupported`-scale value confirmable with one tap
(`AutomaticScanAdvance.eligibility` was unconditionally telling `ReadingEligibility` that
corroboration settled decimal scale, regardless of what kind of corroboration was actually present).
The value is not blocked -- it still reaches the user through focused entry on the frozen
photograph with the stated basis preserved, exactly as before for the already-documented `41g`/`11g`
integer cases.

`EvidenceResolver` can now resolve a disagreement between two confident candidates when one is
explicitly, structurally contradicted by its own document's cross-column ratios and the other is
positively verified by the same check -- never by confidence, magnitude, or source order. A
disagreement where neither or both candidates are uncheckable still refuses exactly as before.

Fixed onboarding: a `SettingsRepository.setHasSeenOnboarding` write failure previously left the
*Get started* button permanently disabled with no error shown and no way to retry, because the
UI-layer double-tap guard was set `true` before the write and never reset on failure.
`OnboardingViewModel` now exposes an observable `Idle`/`Saving`/`Failed`/`Saved` state; navigation
happens only on `Saved`, and `Failed` shows a retryable message.

Nothing about the calculation, schema, migrations, the §10 lookup priority, barcode detection, or
any OCR *recognition* rule changed. No confidence threshold moved and no parser rule was relaxed.
```

- [ ] **Step 3: Add gate rows to `docs/manual-qa.md`**

Read the file's existing numbering scheme (the highest-numbered section referenced elsewhere in this repo's CLAUDE.md is §35, per the startup-hardening pass) and append a new §36 following the established format of prior sections (a short intro paragraph plus numbered checkbox rows):

```markdown
## §36 — OCR evidence lifecycle gate (2026-09-05 pass)

Everything below requires a physical device; none of it can be verified on the `carbscan` emulator,
whose virtual camera cannot exercise the pre-shutter live-evidence path meaningfully.

- [ ] 36.1 Scan a label with the camera held steady on the table for ~1 second before tapping
      capture. Confirm (via `adb logcat` diagnostics, if evidence export is enabled in debug) that
      the frozen live snapshot at shutter time reports a non-zero observation count.
- [ ] 36.2 Repeat 36.1 but deliberately move the phone away from the table in the instant before
      tapping the shutter (camera sees a different scene at the moment of the tap). Confirm the
      frozen snapshot reflects what was seen immediately before the tap, not an even earlier stable
      reading — i.e. confirm the suffix-consensus rule actually invalidates on a recent
      disagreement rather than reporting a stale earlier agreement.
- [ ] 36.3 Retake (tap Retake/Retry) after a first capture, then scan a *different* product. Confirm
      the second scan's result is never influenced by the first product's live evidence (aim-epoch
      isolation).
- [ ] 36.4 Reproduce the documented Hellmann's `1,3 g / 100 ml` case (or an equivalent
      separatorless-integer misread) on a physical device. Confirm the app never offers `13` (or
      the equivalent misread digit run) for one-tap confirmation. Confirm it instead reaches
      focused entry with the frozen photograph, the row highlighted, and the basis preserved.
- [ ] 36.5 Confirm the four documented canary labels (sondey, kinder, yoghurt, stokbrood) still
      auto-advance correctly on a physical device after this pass, and that grated cheese, witte
      kaas, the jar, and the lid all still correctly refuse (per the existing
      `EvidencePipelineProductionTest` table in CLAUDE.md's "OCR quick calculation" section).
- [ ] 36.6 Deny camera permission temporarily, retry, grant it, and confirm the scanner recovers.
      Deny permanently, open Settings, grant it there, return to the app, and confirm the
      `ON_RESUME` recheck picks up the grant without requiring the screen to be closed and reopened.
- [ ] 36.7 Force a `SettingsRepository` write failure during onboarding (if a debug hook exists for
      this; otherwise this row stays open pending a way to simulate it on-device) and confirm the
      *Get started* button re-enables with a visible error, and that tapping it again after fixing
      the underlying condition successfully navigates to Home.
```

- [ ] **Step 4: Commit**

```bash
git add docs/ocr-evidence-provenance-design.md CHANGELOG.md docs/manual-qa.md
git commit -m "$(cat <<'EOF'
Add design note, changelog entry, and manual-QA gate rows for the OCR evidence-lifecycle pass

Documents aimEpoch vs workGeneration, shutter-snapshot semantics, and
physical-observation IDs. Adds §36 to manual-qa.md covering the physical
device checks this pass cannot verify on the emulator.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Explicitly deferred (not in this plan's scope)

Per the review's own Phase 9/10 framing and this plan's Global Constraints, the following are **not** attempted here and should become a follow-up plan once Tasks 1-10 land and their real shape (especially `ScanDecisionEngine`'s and `ConflictAdjudication`'s actual usage patterns) is proven by real code rather than by this plan's necessarily-provisional sketches:

- **`ParsedNutritionView`/semantic-model consolidation** (review Phase 3 / Phase 9 item 1) — migrating `CrossColumnRatioCheck`, `ScaleAmbiguity`, `StatedBasis`, `RecoveryCandidates`, `FocusedAmountEntry`, unit accompaniment, and confidence lookup onto one shared semantic declaration/cell model. This is a large, cross-cutting refactor of correctness-critical code (per `.audits/architectural-analysis-2026-09-04.md`'s own listing of `NutritionColumnKind.kt` at 1,041 lines and `NutritionTableInterpreter.kt` at 942 lines) that deserves its own plan with its own parity-test strategy, not a phase bolted onto a P0 safety fix.
- **Moving all camera/OCR orchestration out of `LabelScannerScreen.kt` into a lifecycle-aware ViewModel/reducer** (review Phase 3 item 2) — Task 3 above touches this file surgically (replacing `captureSession` and wiring the coordinator/engine) but does not attempt the full extraction into a separate state-holder class. That extraction is real, valuable work per the `compose-state-and-effects`/`kotlin-concurrency-and-flow` skills' guidance on state-holder boundaries, but doing it simultaneously with the P0 evidence fix would make it much harder to isolate which change caused which test result.
- **Converting `SelectedRegionRecognizer` from `CountDownLatch` blocking to a suspending, cancellable implementation** (review Phase 3 item 3) — verified real (`SelectedRegionRecognizer.kt:70-101`, `DEFAULT_TIMEOUT_MS = 5_000L`). Genuinely worth fixing per the `kotlin-concurrency-and-flow` skill's guidance on blocking-thread bridges, but it is a self-contained, independently landable change once Task 4's `observationId` threading is in place, and should not be mixed into the same review cycle as the P0 evidence fix.
- **The held-out optical benchmark and selective second-still acquisition** (review Phase 4/Phase 10 item 9) — requires physical devices and a measurement protocol this plan cannot execute from a development session. `docs/manual-qa.md` §36 (Task 10) records the specific gates that remain open; growing the optical corpus and building a genuinely separate second-acquisition route is follow-up work gated on physical access, not something to build speculatively now.
- **Session-replay-harness consolidation** (`.audits/architectural-analysis-2026-09-04.md`'s "HIGH: Session replay harnesses" finding, ~350-450 duplicated test lines across `ThirteenthSessionReplay.kt` through `SeventeenthSessionReplay.kt`) — a real maintainability finding, but it is test-infrastructure cleanup unrelated to the safety-critical fixes in this plan, and mixing it in would obscure the diff for a security-sensitive review.

---

## Release Gates

Do not consider this plan's work release-ready until:

1. Every task's JVM tests pass (`./gradlew.bat :app:testDebugUnitTest`), full suite, `--rerun-tasks`, 0 failures/errors/skipped, counted from JUnit XML.
2. `lintDebug` shows no new warnings or errors versus Task 0's recorded baseline.
3. `assembleDebug` and `compileDebugAndroidTestKotlin` both succeed.
4. Every case in `GroundTruthManifest.CASES` (Task 0) passes with zero forbidden displayed values, through both the automatic and confirmation paths.
5. `EvidencePipelineProductionTest` (migrated in Task 6) passes on a connected device, reproducing the same canary/refusal table CLAUDE.md already documents for the fast-path advancement feature.
6. The core label set (sondey, kinder, yoghurt, stokbrood, grated cheese, witte kaas, jar, lid, plus the Hellmann's-shaped case) is run at least 3 times on a physical target device with §36's rows checked.
7. Camera-permission denial/retry/Settings-grant/resume and onboarding failure/retry are validated on hardware (§36.6, §36.7).
8. `docs/manual-qa.md` §36's rows are checked with evidence (screenshots, logcat excerpts, or recorded observation counts), not assumed.
9. This plan's "Explicitly deferred" section's items remain explicitly open in the final report — do not let the presence of `ScanDecisionEngine`/`ConflictAdjudication` be read as "the architectural cleanup is done."

If the connected/hardware suites cannot be run in a given implementation session, **leave the optical release gate explicitly open in the final report** — do not mark it passed on the strength of the JVM suite alone, per this plan's own Task 0 verification discipline.
