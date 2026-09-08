# Just the Carbs — engineering review, 2026-09-08

> Historical review snapshot. The follow-up fixes are documented in [remediation.md](remediation.md)
> and the 1.0.6 section of [CHANGELOG.md](../../../CHANGELOG.md). Findings below describe the reviewed commit.

## A. Repository state

- Branch: `main`.
- Starting HEAD: `3468c713a8b1fe7030c4e430a36fc96516e4a956`.
- Ending reviewed HEAD: `749ef98ccaee994d65fa1df7b5e5172c39ab8689` (`Add scanner haptic feedback and open 1.0.6`). Claude committed this while the review was running.
- At entry, navigation, both scanner screens, branding, changelog, CLAUDE.md, manual QA and version history were modified. Nine design-handoff files were already deleted. The implementation/documentation edits became `749ef98`; the nine deletions remain. This review did not change application code or those deletions.
- Review artifacts are confined to this directory. Four temporary regression tests were executed, archived here as `RepositoryAuditProbeTest.kt.txt`, and removed from the test source set. Their failing JUnit output is preserved in `probe-results.xml`.
- Relevant recent changes: `ebe6fe2` changed portion-history SQL and meal/navigation writes; `cb00b24` tightened OCR declaration/column handling; `f890e9a` added crop targeting; `6f8ff63` opened corrective release 1.0.5. The findings below are not attributed to the haptic commit.

Architecture reviewed: a single Activity and Compose navigation graph, destination-owned ViewModels, an application container, Room v7, DataStore preferences, Retrofit/OkHttp, shared search governors and a primary/fallback search chain. Barcode scanning uses CameraX and ML Kit. Label capture uses live evidence, a shutter snapshot, retained Pass A, filtered/re-recognized crops, evidence resolution, verification and explicit recovery before calculator handoff.

## B. Executive verdict

**Important reliability issues and release blockers found.** The highest-value repairs are the Android 8–10 SQL regression and calculator/meal consistency defects. Four focused JVM probes reproduced four defects despite all 1,883 existing tests passing.

The OCR safety gates are substantial and should be preserved. The review found integration gaps around those gates, not grounds to relax their rules. There is also a small amount of proven unused code and test-only infrastructure whose documentation overstates its production role.

Evidence levels below distinguish executed reproductions from source-traced paths. No emulator or physical-device execution was performed in this review. An inspection of a branch establishes what it does when reached; it does not establish how frequently a real device reaches it.

## C. Confirmed findings

### 1. [P1] Portion-history writes use SQL unsupported by supported Android versions

- **Location:** [PortionUsageDao.kt:70](C:/Users/tuncb/Desktop/CarbTracker/app/src/main/kotlin/app/justthecarbs/data/local/PortionUsageDao.kt:70), `JustTheCarbsDatabase.kt:362`, `ProductViewModel.kt:247,976`, `app/build.gradle.kts:61`.
- **Evidence:** `recordUse` executes literal `INSERT ... ON CONFLICT ... DO UPDATE`. Generated `PortionUsageDao_Impl` preserves that statement. The database uses the platform implementation, with no bundled SQLite driver. `minSdk` is 26. SQLite added this UPSERT syntax in 3.24; Android's documented platform versions are 3.18 at API 26, 3.19 at 27, and 3.22 at 28/29. [SQLite release notes](https://www.sqlite.org/releaselog/3_24_0.html), [Android SQLite version table](https://developer.android.com/reference/android/database/sqlite/package-summary).
- **Failure mode:** On affected Android 8–10 devices, enter a valid portion and wait for the 600 ms usage debounce. Preparing the statement fails. `rememberUsage()` launches the write without catching it, so ordinary calculator use can crash. Back navigation also awaits the same write without recovery.
- **Impact:** A common workflow is broken on an explicitly supported OS range. OEM SQLite versions can differ; no affected device was executed here.
- **Recommended fix:** Keep the v7 unique-key/sentinel repair, but implement the increment with an API-26-compatible DAO transaction, such as insert-ignore followed by an update on conflict. Preserve atomic increments; do not revert to an unprotected read/write pair.
- **Regression-test recommendation:** Run actual Room `recordUse` tests on API 26 or 28, including a new variant and repeated/concurrent increments. Existing API-36-only CI and in-memory JVM fakes miss this compatibility boundary.
- **Regression risk / origin:** Low to moderate, confined to DAO write semantics. Introduced by `ebe6fe2` on September 7.

### 2. [P1] Product verification replaces a direct-carb result with a stale gram calculation

- **Location:** `ProductViewModel.kt:182,637,885,1125,1135`.
- **Evidence:** Switching to `DirectCarbs` clears `result` but retains `portionText`. Verification/reset/remote-apply call the gram-only `recalculate()`. `exactCarbs` then prefers the newly populated `result` over `directCarbResult`.
- **Failure mode:** Enter 50 g; switch to two slices at 14 g carbs each; verify the product's per-100 value as 50. The executed probe records `exact=25.00, direct=28, weighed=25.00`. The selected portion still describes two slices, but the result becomes 25 instead of 28. The meal builder still chooses the direct result, so display and saved meal can disagree.
- **Impact:** Incorrect carbohydrate output after an ordinary verification action.
- **Recommended fix:** Route all recalculation through the active input mode/conversion, and clear the inactive result. Cover product verification, label verification, reset, remote apply, and empty count input. Do not merely reverse the `exactCarbs` fallback order; that leaves inconsistent state in place.
- **Regression-test recommendation:** Archived `directPortionSurvivesProductVerification` fails on current code. Extend it to the other product-update entry points and compare screen/meal results.
- **Regression risk / origin:** Low to moderate, localized calculator repair. The direct-carb branch dates to `4eb99890` in August; not a haptic regression.

### 3. [P1] Changing product basis silently reinterprets a saved weight portion as volume

- **Location:** `ProductViewModel.kt:481,622,1135`; `ui/home/RememberedCarbs.kt:69`; `VerifyDialog.kt:103`.
- **Evidence:** Weight conversions carry their own basis, but count calculation uses only `amountPerUnit` and the product's current basis. The verification dialog allows changing that basis. Loading/selecting stored units does not reject a mismatch; Home repeats the same calculation.
- **Failure mode:** Select two slices at 35 g each, then correct the product to 10 g carbs per 100 ml. The executed probe produces `7.00` g carbs from 70 **grams** as though they were 70 **millilitres**.
- **Impact:** An unsupported conversion reaches both calculator and remembered results and can be saved to a meal. No density has been supplied.
- **Recommended fix:** Require matching bases before using a weight conversion, including restored selections and Home summaries. On a basis change, invalidate incompatible selection/results and basis-dependent remembered quantities; ask for a compatible quantity rather than relabeling the old number. Keep the stored unit available for correction.
- **Regression-test recommendation:** Archived `weightPortionCannotBeUsedAfterBasisChanges` fails. Add reopen, Home-summary and gram↔ml tests, including package shortcuts and remembered portions.
- **Regression risk / origin:** Moderate because changing basis affects several dependent quantities. Existing August calculation behavior, not introduced by the current haptic changes.

### 4. [P1] Online-value history stores numbers without their nutrition basis

- **Location:** `ProductRepository.kt:138,201,236,253`; `domain/Product.kt:89`; `data/local/ProductEntity.kt`.
- **Evidence:** `originalRemoteCarbs` and `latestRemoteCarbs` retain only amounts. Verification may change basis. Reset/apply restore the saved amount while keeping the effective basis; refresh's `differs` comparison also ignores basis.
- **Failure mode:** Start with an online 40 g/100 g value, verify 10 g/100 ml, then reset online. The executed probe restores 40 but leaves `PER_100_ML`. The number/basis pair never existed in the remote record.
- **Impact:** Wrong denominator, persistently stored. Basis-only remote changes are also not represented faithfully in the change notice.
- **Recommended fix:** Preserve and compare amount+basis together for remote originals/latest values. Existing rows cannot reliably reveal a lost original basis: recover from fresh remote data or withhold reset until the basis is known. Any schema extension needs an explicit migration and tests; do not infer the missing historical basis from the current one.
- **Regression-test recommendation:** Archived `resetOnlineRestoresTheOriginalBasisTogetherWithTheValue` fails. Extend to latest-remote apply, basis-only changes, and migration of rows without historical basis.
- **Regression risk / origin:** Moderate; requires deliberate persistence compatibility. Reset's amount-only behavior dates to `75e526cd` in August.

### 5. [P1] A usage-history failure turns a committed meal addition into a retryable failure

- **Location:** `ProductViewModel.kt:834–873`, especially line 864; `RoomMealDataSource.kt:15`.
- **Evidence:** The meal insert commits first; usage recording is then awaited in the same `try`. Either failure sets `mealAddFailed=true`. There is no shared transaction or insertion identity protecting a subsequent retry.
- **Failure mode:** The probe permits the meal insert, fails the history write, and retries the add. Recorded output: `mealRows=2, reportedFailure=true`. “Add & scan next” also suppresses its navigation event although the meal item exists.
- **Impact:** Duplicate meal carbohydrates and misleading save status. Finding 1 provides one concrete cause of a usage-write failure; storage errors provide another.
- **Recommended fix:** Treat successful meal insertion as the meal operation's success and report history failure separately, or transact both if both are genuinely required. Capture usage from the same tap-time snapshot as the meal, rather than rebuilding it after suspension. Preserve the existing duplicate-tap guard.
- **Regression-test recommendation:** Archived `successfulMealInsertMustNotBeReportedAsFailedWhenHistoryFails` fails. Existing `AddToMealTransactionTest` covers insert failure, delayed inserts and double taps, but not failure after insertion commits.
- **Regression risk / origin:** Low to moderate. The current awaited usage write/shared failure boundary was introduced in `ebe6fe2`.

### 6. [P2] Bitmap-decode fallback bypasses the newer OCR confirmation safeguards

- **Location:** `LabelAnalyzer.kt:365–380,418`; `LabelScannerScreen.kt:1251–1255,1852,2239–2263`.
- **Evidence:** When bitmap decoding returns null, the analyzer can still recognize the file through `InputImage.fromFilePath`. A returned `PassAResult` with no bitmap is assigned straight to `reading`; the confident/ambiguous cards pass candidates to `onUseValue` when they have a basis. This path does not run selected-table evidence resolution, automatic verification or confirmation eligibility.
- **Failure mode:** If the file-based recognizer succeeds after the app's bitmap decode fails, a scale-unsupported or contradictory Pass-A candidate can receive the old one-tap confirmation rather than the normal guarded confirmation/recovery route. The retained photo required by the newer visual confirmation is absent.
- **Impact:** An exceptional image path has weaker acceptance conditions than normal capture. This is source-traced, not a reproduced physical-device decode failure; it does not imply all fallback results are wrong or automatically accepted.
- **Recommended fix:** Fail closed into Retake/manual correction when no usable capture can be retained, or make this path satisfy the same evidence and visual-confirmation requirements. Do not treat `LabelReading.Confident` alone as authorization to hand off a value.
- **Regression-test recommendation:** Inject a no-bitmap Pass-A result containing a basis-known but scale-unsupported candidate. Assert there is no direct Confirm→calculator route. Current pure gate tests do not cover this UI branch.
- **Regression risk / origin:** Low if confined to failed image preparation; pre-existing behavior in the retaining pipeline. Preserve any established basis for manual correction where safely available.

### 7. [P2] Barcode analysis continues while the user enters a barcode manually

- **Location:** `ScannerScreen.kt:159–177,205–209,324–328`; `BarcodeAnalyzer.kt:83–107`.
- **Evidence:** Opening `ManualBarcodeDialog` only changes `showBarcodeDialog`. The analyzer remains active, and accepted results navigate unconditionally. `close()` does not introduce a closed-result guard, and the screen's provider listener also has no disposal guard.
- **Failure mode:** Open manual entry while a recognizable package remains in view. Camera frames can reach acceptance and navigate away while the user is typing, selecting the camera's barcode instead of the intended manual one.
- **Impact:** Manual recovery can be interrupted by its own scanner. The same missing ownership gate leaves pending callbacks insufficiently guarded at navigation boundaries. The manual-dialog path is directly reachable without assuming an unusual transport failure.
- **Recommended fix:** Suspend acceptance during manual input, reset stability on dismissal, and close the acceptance gate immediately on any committed navigation/Close. Guard queued results/provider completion and release owned camera use cases/executor deterministically.
- **Regression-test recommendation:** Inject acceptance while the dialog is open, after manual confirmation, and after Close; assert only the intended navigation occurs. Current barcode tracker tests do not exercise this UI ownership boundary.
- **Regression risk / origin:** Low to moderate, scanner lifecycle scope. The new `rememberUpdatedState` haptic wiring does not cause or fix this existing behavior.

### 8. [P2] Automatic crop targeting does not update an already-visible crop screen

- **Location:** `CropConfirmationScreen.kt:105`; `LabelScannerScreen.kt:925–927,1277–1293,1627–1637`.
- **Evidence:** The crop screen is composed with the initial generic region while the automatic read is running. Its local selection uses unkeyed `remember { mutableStateOf(initialSelection) }`. The later CROP_FALLBACK branch updates the parent's region, which does not replace that remembered selection.
- **Failure mode:** With an automatic recognition slow enough for the processing crop UI to compose, its fallback target arrives afterward. The visible rectangle and the subsequent Read callback retain the generic region. `AutoCropTargeting.regionFor()` can be correct while the shipped feature has no effect.
- **Impact:** Extra manual adjustment and potentially repeated recognition of the unchanged region. The user-facing behavior claimed by `f890e9a` is not reliably applied.
- **Recommended fix:** Give the selection one owner, or synchronize a new automatic target at the explicit capture/automatic-attempt boundary while preserving subsequent user edits. Avoid a broad key that resets the crop on unrelated recompositions.
- **Regression-test recommendation:** Compose with region A and `reading=true`; update to region B and `reading=false`; press Read and assert B is submitted. Also verify a later user adjustment survives ordinary recomposition. Existing nine targeting tests cover geometry only.
- **Regression risk / origin:** Low. Integration regression from `f890e9a`; the local remembered state predates that change.

### 9. [P2] Debug evidence export and selection diagnostics block the UI thread

- **Location:** `SettingsScreen.kt:334`; `ScanEvidenceExport.kt:26–83`; `ScanEvidenceRecorder.kt:166–182,604–724`; `LabelScannerScreen.kt:1106`.
- **Evidence:** The export click synchronously calls `share()`, including a writer drain of up to 30 seconds and ZIP construction/validation over retained captures. Selection diagnostics are also synchronously rendered/written after the coroutine returns to Main; this includes another table interpretation and recovery analysis.
- **Failure mode:** Export a substantial retained capture set while the writer is busy. Input is blocked for the drain and archive work. Slow storage can make this long enough for an ANR. Scanner transitions can also stall behind debug diagnostic work even though the decision has already been made.
- **Impact:** Meaningful debug-build freezes and distorted scanner latency measurements; not a release privacy/export exposure. No device timing benchmark was run here.
- **Recommended fix:** Perform export/drain/validation off Main, disable duplicate export while running, and launch only the resulting share intent on Main. Queue immutable selection diagnostics with the existing writer. Distinguish empty captures from export failure in UI feedback.
- **Regression-test recommendation:** Use a deliberately blocked writer/slow exporter to verify UI responsiveness and single-flight export; retain ZIP integrity tests. Existing archive tests establish file integrity, not caller-thread safety.
- **Regression risk / origin:** Low, debug-only. Pre-existing code, not haptic work.

## D. Dead / obsolete code

### Safe to remove

These have declaration-only matches after call-site searches, excluding generated code. They are not manifest callbacks, serializers or reflective entry points in this repository:

- `ScanEvidenceRecorder.recordRecognizedText` (line 293) and `recordDiagnostics` (line 327): synchronous public wrappers unused by production and tests; asynchronous counterparts are used. Update the stale KDoc reference to the synchronous method if removing it.
- `BarcodeAnalyzer.reset` (line 62): no caller. Keep `BarcodeStabilityTracker.reset`, which is a distinct method with real tests and may be needed by finding 7's repair.
- `PortionUsageDao.deleteForProduct` (line 93): no caller, including the Room adapter/tests. Room generates an implementation, but nothing invokes it.
- `R.string.recent_summary` (`strings.xml:33`): unused by source/resources, consistent with lint. No dynamic resource lookup was found in app code.

Remove these only after the scanner repair has established whether it needs the reset entry point. No runtime source deletion was performed during this review.

### Requires confirmation / intentionally retained

- `LabelAnalyzer.analyzeStill` is **test-used**, not dead: `ProductionStillPipelineTest`, `DeviceCaptureReplayTest` and `FramedCaptureDiagnosticTest` call it. Production uses `analyzeStillRetaining`. The former test's description of being the screen's exact entry point is stale; preserve its useful decode/EXIF coverage while correcting that claim.
- `ScanDecisionEngine`, `ScanDecision` and `VerifiedReading` are used by JVM/instrumentation tests but not by `LabelScannerScreen`. In particular, `EvidencePipelineProductionTest` uses the wrapper. Keep or relocate this test adapter only after documenting its intentional differences; do not present its type-level guarantees as guarantees enforced at the UI boundary.
- `ProductRepository.saveProductWithPortionUnit` has repository tests but no production caller; ManualEntry owns a different two-write flow. Decide whether it is a supported helper or test-only surface before removal.
- `Product.servingAmount` has no observed calculation consumer, but is persisted by Room and appears in migration fixtures. It is a schema compatibility field, not a safe opportunistic deletion.
- Exploratory OCR experiments are deliberately retained and excluded through `ExploratoryExperiment` in CI/release gating. They are evidence, not automatically obsolete code.

## E. Simplification opportunities

1. **One active calculator result.** A small mode-aware recalculation entry point can enforce the mutual exclusion of gram/direct results and unit compatibility. Findings 2–3 show the concrete failure caused by maintaining both results independently. A full ViewModel rewrite is unnecessary.
2. **Separate committed meal state from optional history state.** Finding 5 is caused by conflating two writes under one success/failure flag. Make the commit boundary explicit and use a shared tap-time snapshot.
3. **Make OCR test and UI decision contracts agree.** The UI already shares core policy functions, but the test wrapper is not identical: its serving-confirmation conversion and conflict/crop presentation differ. Verify parity on real evidence before removing either representation or routing the UI through it. Preserve all current scale, dispute, row and column protections.

## F. Performance/resource findings and coverage boundaries

- Finding 9 is the confirmed performance issue. Retention is capped at 35 capture folders, but archive size/work scales with high-resolution JPEG/PNG files, so a count cap does not make synchronous export safe.
- Barcode and label analyzers/executors, CameraX binding, ML Kit completion paths, coroutine cancellation, shutter epochs/work generations, crop ownership, evidence retention, search generation checks and governor pacing were inspected. Label disposal has explicit epoch/disposed checks; the barcode screen lacks equivalent acceptance ownership (finding 7).
- Search correctly increments generations on edits, rejects stale responses, cancels prior work and bounds automatic rate-limit retry. The container shares primary/legacy budgets; Home and dedicated search have destination-owned ViewModels. No additional confirmed search defect was established.
- Remote lookup remains local-first; freshness avoids the immediate duplicate fetch after a cache miss. Responses distinguish network/timeout/rate-limit failures, and cancellation is not broadly caught as an ordinary exception in the remote sources. Persistent remote basis is the concrete reconciliation gap (finding 4).
- Room migrations 1→7, entities, generated SQL, DAO transaction boundaries and migration tests were inspected. There is no destructive fallback configured. API-36-only runtime coverage is the material migration/SQL compatibility gap.
- Settings use one DataStore source and lifecycle-aware collection. The new label shutter haptic is after evidence freeze and reads the app setting; barcode callbacks use updated state. No additional haptic defect was established; hardware timing/feel was not assessed.
- Manifests, backup exclusions, debug-only FileProvider, R8 rules, dependencies and both CI workflows were inspected. Release signing/R8 execution, live remote API requests, full device instrumentation, process-death tests and camera hardware tests were not run. No exhaustive per-file proof of absence or broad dependency security claim is made.

## G. Test/build results

Environment: JDK `C:\atools\jdk-21.0.12+8`, Android SDK `C:\atools\sdk`.

```powershell
$env:JAVA_HOME='C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME='C:\atools\sdk'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain
```

- Initial sandbox attempt failed fetching Gradle (`Permission denied: getsockopt`); an approved execution completed successfully.
- Baseline combined invocation: `BUILD SUCCESSFUL in 55s`; 67 tasks, 15 executed and 52 up-to-date. JUnit XML: **1,883 tests, 0 failures, 0 skipped**. Lint: **0 errors, 23 warnings**. Debug APK and Android test Kotlin compilation succeeded. This does not mean instrumentation ran.
- Temporary probes: `:app:testDebugUnitTest --tests '*RepositoryAuditProbeTest' --console=plain` compiled and ran **4 tests, all 4 failed at the intended behavioral assertions**. Repeated after adding exact-result/duplicate-row output, with the same four failures. See [probe-results.xml](probe-results.xml) and [probe source](RepositoryAuditProbeTest.kt.txt).
- After removing the probes from the source set, the combined command again succeeded: `BUILD SUCCESSFUL in 3s`, 1 task executed, 3 from cache and 63 up-to-date. This restoration check reused cached baseline outputs; the earlier baseline invocation actually executed the test task.
- Probe reuse: copy the archived `.kt.txt` file into the product test package as `RepositoryAuditProbeTest.kt`, run the targeted command, implement fixes, then retain suitably named regression tests in the regular suite. Do not mistake the archived red tests for a currently failing normal build.

## H. Recommended implementation batch

1. **Compatibility hotfix:** replace unsupported portion-history UPSERT with an atomic API-26-compatible transaction; add an old-API Room test to CI. Keep the v7 uniqueness fix.
2. **Calculator/persistence correctness:** fix mode-aware recalculation and basis compatibility; preserve remote amount+basis as a pair with an explicit legacy-row strategy. Start with the three archived calculation/basis probes; cover Home, reopen and meal snapshots.
3. **Meal commit semantics:** separate history failure from meal success and freeze both inputs at tap time. Retain the fourth probe and add a navigation assertion.
4. **Scanner integration:** fail closed on missing retained bitmap, gate barcode acceptance during manual/exit states, and propagate automatic crop targets into the visible selection. Add UI-boundary tests; keep OCR acceptance rules unchanged.
5. **Small debug/cleanup follow-up:** move evidence export/selection diagnostics off Main; remove confirmed unused wrappers/resources and correct test-only production claims. No architecture rewrite or unrelated UI work.
