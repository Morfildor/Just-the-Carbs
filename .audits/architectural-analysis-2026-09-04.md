# OCR Accuracy, Efficiency, and Trustworthiness Audit

**Date**: 2026-09-04  
**Scope**: Current working tree, including uncommitted and untracked OCR changes  
**Files Analyzed**: 238 OCR-relevant artifacts (63 production OCR files, 149 JVM test files, 17 instrumentation test files, and 9 direct UI/domain/build/evidence files)  
**Production OCR Size**: 14,672 Kotlin lines  
**Dead Code Files**: 0 safe immediate deletions; 2 production-inactive paths  
**Duplication Groups**: 3

---

## Executive Summary

### Verdict

**The current OCR working tree is not release-ready.** Its trust architecture is unusually defensive and contains several excellent safeguards, but those safeguards are not backed by a green end-to-end recognition gate today.

- **Accuracy**: The current connected OCR run failed **12 of 33 tests**. Genuine regressions include `46 -> 6.4`, a printed per-piece `6.7 -> 3`, and a formerly unique `53.5` reading becoming ambiguous. Four additional failures are stale assertions expecting the retired `FromRow` provenance rather than the new `FromDeclaration` model.
- **Trustworthiness**: Automatic advancement is conservative, conflict-preserving, and now distinguishes repeated processing of one photograph from an independent observation. However, the code deliberately still offers one known tenfold error (`1.3 -> 13`) for confirmation, and the independent-observation route is not wired in production.
- **Efficiency**: The latest physical session measured **477–936 ms end to end** and **244–453 ms in ML Kit** on one Samsung SM-S928B, which is good. The result is not yet a performance envelope: there are no cold/warm P50/P95/P99 benchmarks across device tiers, no peak-memory measurements, and the fallback OCR pass can block a worker for up to 5 seconds.
- **Validation quality**: All **1,678 JVM tests pass**, but they mostly validate parser behavior over saved OCR tokens. The physical ML Kit boundary is where the current failures occur. The newest 29-capture replay is exact-value aware, but it is a tuned replay corpus, not a held-out optical benchmark.

### Current measured outcome, with the correct caveat

The newest 29-capture token replay contains 23 captures with known ground truth and reports:

| Outcome | Count | Share of known-ground-truth captures |
|---|---:|---:|
| Correct automatic advance | 2 | 8.7% |
| Correct confirmation proposal | 13 | 56.5% |
| Correct focused-entry routing | 3 | 13.0% |
| Correct value existed but was withheld | 3 | 13.0% |
| OCR contained no correct value | 2 | 8.7% |
| Ground truth unavailable | 6 | excluded |

That replay has 0 wrong values shown and 2/2 correct automatic advances, but two automatic cases are far too few to infer dependable field precision. The approximate 95% Wilson lower bound for 2 successes out of 2 is only 34%. More importantly, the replay starts after recognition, so it cannot detect the regressions now visible in the instrumentation suite.

### Structural Findings

- **Dead Code**: No file can be deleted safely without a product decision. `NutritionTableLocator` is intentionally tested but production-unwired; the second-observation enum/type route exists only in tests.
- **Duplicated Functionality**: 3 groups, including near-identical session replay harnesses and multiple numeric-cell lexers/column binders.
- **Architectural Anti-Patterns**: 7 material issues, led by validation-boundary fragmentation, an unwired trust route, cross-context mutable state, and oversized policy objects.
- **Type Issues**: 2 semantic type-model issues; no `Any`, `@ts-ignore`-style bypass, or unchecked generic type abuse was found.
- **Code Smells**: 6 files exceed 500 lines; the most important algorithm functions span roughly 130–370 lines.

**Estimated Cleanup**: No immediate production deletion is justified. Consolidating the replay harnesses can remove roughly 350–450 test lines. Retiring the experimental locator would remove about 288 production lines, but only after an explicit roadmap decision.

---

## Architecture and Data Flow

```text
CameraX 1280x720 live frames
  -> ML Kit Latin OCR
  -> readiness + recent live evidence only

CameraX quality still (requested 3264x2448)
  -> full bitmap decode + EXIF rotation
  -> ML Kit Pass A over the whole photograph
  -> OcrDocument (text, boxes, confidence, language)
  -> semantic panel/row/column/declaration interpretation
  -> Strategy A: filter existing Pass A elements to selected region
  -> Strategy B: native-resolution crop + a new ML Kit recognition when needed
  -> EvidenceResolver (preserve agreement/disagreement)
  -> AutomaticVerification (cross-column or distinct physical observation)
  -> scale/unit/basis eligibility
  -> AUTO_ADVANCE | CONFIRM | RECOVERY | CROP | FOCUSED_ENTRY
```

The Android/ML Kit boundary is clean: ML Kit objects are converted once by `MlKitOcrMapper`, and the parser and trust policies are pure Kotlin. That separation is a major strength and explains the large deterministic JVM suite.

---

## What Is Done Well

1. **Capture-first authority** — live 720p analysis guides the user; the answer comes from a deliberate high-resolution still (`LabelScannerScreen.kt:394-414`, `1280-1298`).
2. **No silent digit repair** — the parser refuses or asks instead of manufacturing a decimal point or substituting a plausible digit. This is the correct default for carbohydrate data.
3. **Conflicts remain conflicts** — `EvidenceResolver` groups exact numeric value plus basis and refuses disagreement instead of allowing source order or confidence to choose a winner (`EvidenceResolver.kt:168-249`).
4. **Correlated observations are recognized as correlated** — filtered Pass A, full Pass A, and crop OCR are distinguished from separate physical photographs (`RecognitionEvidence.kt:117-141`, `AutomaticVerification.kt:202-210`).
5. **Recognizer confidence only subtracts trust** — low confidence can withhold a lone proposal, but high confidence cannot win a conflict (`EvidenceResolver.kt:35-53`, `252-254`).
6. **Scale ambiguity is explicit** — separatorless values are not silently treated as established, and uniform decimal collapse is correctly identified as invisible to ratio checks (`ScaleAmbiguity.kt:1-109`, `160-272`).
7. **Structural verification is relational, not plausibility scoring** — the cross-column check validates or vetoes but never derives a replacement value (`CrossColumnRatioCheck.kt:1-66`).
8. **Numeric equality is scale-insensitive** — `BigDecimal.compareTo` prevents `5`, `5.0`, and `5.00` from becoming artificial disagreements.
9. **Bounded live work** — a single in-flight analysis and CameraX `KEEP_ONLY_LATEST` avoid a backlog (`LabelAnalyzer.kt:129-221`, `LabelScannerScreen.kt:1275-1279`).
10. **Debug evidence is mostly off the user-visible path** — expensive capture copying and diagnostics are queued after handoff (`LabelAnalyzer.kt:412-465`, `512-568`).

---

## Prioritized Accuracy and Trust Findings

### P0 — The current optical/production-path gate is red

**Evidence**: Running

```text
./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=
    app.justthecarbs.ocr.RealImageOcrTest,
    app.justthecarbs.ocr.ProductionStillPipelineTest,
    app.justthecarbs.ocr.EvidencePipelineProductionTest
```

on the attached `carbscan` API 36 emulator executed 33 tests and failed 12.

Material behavior failures include:

| Failure | Observed current behavior | Expected |
|---|---|---|
| `ProductionStillPipelineTest.kt:136` | `6.4` | `46` |
| `ProductionStillPipelineTest.kt:164` | `3` | `6.7` per piece |
| `ProductionStillPipelineTest.kt:125` | ambiguous between `53.5` and `6.7` | confident `53.5` |
| `EvidencePipelineProductionTest.kt:128` | `6.4` | `46` |
| `RealImageOcrTest.kt:493` | no piece relationship | `PIECE` relationship |
| `RealImageOcrTest.kt:253-266` | no longer confident | test currently expects the recognizer's known-wrong `2.09` baseline |

Four failures (`RealImageOcrTest.kt:184-193`, `519-525`) are stale test contracts: the current parser emits `CandidateProvenance.FromDeclaration`, while these tests require `FromRow`. Those should be updated only after asserting the replacement provenance carries the same nutrient-name and child-exclusion evidence.

**Impact**: The green JVM suite creates false confidence because the live recognition boundary and the new semantic model disagree with the optical fixtures.

**Recommendation**: Treat the connected suite as a release blocker. First classify each failure as (a) intended contract migration, (b) parser regression, or (c) ML Kit nondeterminism. Then require the entire optical production-path suite to pass exact value, basis, and final UI-action assertions.

### P0 — A known tenfold wrong value is intentionally still user-visible

`AutomaticScanAdvance.kt:252-279` documents a Hellmann's label that prints `1.3 g / 100 ml`; every view of one photograph reads `13g`, and the app offers `13` for confirmation. The code sets `corroborationSettlesScale = true` because suppressing this proposal also suppresses two correct separatorless integers.

The older 21-capture replay confirms the trade-off remains present: its current output contains **1 `WRONG_PROPOSAL`**, while `FifteenthSessionReplayTest.kt:37-48` gates only `WRONG_AUTO`. The newer 29-capture test correctly gates both wrong automatic results and wrong proposals, but only for that newer corpus (`SeventeenthSessionReplayTest.kt:28-48`).

**Impact**: A confirmation tap is not a correctness mechanism; it can anchor the user on a plausible-looking wrong number. The implementation therefore does not meet a strict “no wrong carbohydrate figure shown” trust policy across all recorded corpora.

**Recommendation**: Apply one policy to every corpus: unsupported-scale evidence from a single physical observation must not prefill a number. Route it to focused entry, or acquire a genuinely independent second still and require exact agreement. Preserve the frozen photo and basis so the user can enter what is printed without losing context.

### P1 — The independent physical-observation trust route is production-unreachable

The model is sound but incomplete:

- `EvidenceSource.SECOND_OBSERVATION_PASS` is declared at `RecognitionEvidence.kt:48`.
- `AutomaticVerification` requires two distinct `PhysicalObservationId` values at `AutomaticVerification.kt:208-210`.
- Production constructs Pass A and filtered evidence without IDs (`SelectedTableResolution.kt:103-123`).
- Every omitted ID becomes the safe sentinel `UNKNOWN` (`RecognitionEvidence.kt:202`).
- A production-only search finds no construction of `PhysicalObservationId` and no use of `SECOND_OBSERVATION_PASS` outside its declaration/mapping.

**Impact**: `DISTINCT_OCR_AGREEMENT` exists in types and tests but cannot occur in the shipped pipeline. Automatic recall therefore depends almost entirely on cross-column tables; single-column labels can never earn optical verification.

**Recommendation**: Give each shutter acquisition a capture ID, propagate that ID to every derivative, and add a second-still acquisition route for cases where it can replace an unsafe proposal or recovery step. Never infer physical independence from crop, source enum, or recognition-run count.

### P1 — Test architecture measures three different things as though they were interchangeable

There are three useful but distinct test boundaries:

1. Parser unit tests over synthetic/saved `OcrDocument` values.
2. Replay tests over OCR elements previously captured from a device.
3. Instrumentation tests that actually decode images and run ML Kit.

The strongest current replay test validates exact value, basis, and final action, but cannot detect recognition changes. Conversely, `EvidencePipelineProductionTest.theFastPathAdvancesOnlyOnConfidentlyResolvedFixtures` calls the legacy `AutomaticScanAdvance.mayAdvance(result.outcome)` at lines 147-176 rather than `mayAdvanceVerified` or `ScanPresentationDecision`. Its “safety” check verifies only that an advancing outcome is structurally confident and has a basis. The forbidden-value test at lines 208-227 checks a curated blacklist, not exact ground truth, and omits the grated-cheese and witte-kaas fixtures from that map.

`RealImageOcrTest.kt:237-266` also deliberately freezes a known wrong recognizer output (`2.09` where the label prints `2.0`). That is valuable as a recognition baseline, but it must not be counted as an accuracy pass.

**Impact**: Aggregate claims such as “all tests pass,” “8/8,” or “zero wrong” can refer to parser safety, preserved historical behavior, or one tuned replay rather than current end-to-end exact accuracy.

**Recommendation**: Give each suite an explicit metric label and make only the optical suite authoritative for end-to-end accuracy. Every authoritative case needs exact printed value, exact basis, allowed final actions, and whether any wrong value was displayed.

### P1 — Live evidence can be stale and is read across threads without ownership

`LiveEvidenceBuffer` stores observations in an unsynchronized `ArrayDeque` (`LiveEvidenceBuffer.kt:62`). `record` is invoked on the main executor (`LabelScannerScreen.kt:409-415`), while `asEvidence` is evaluated inside `withContext(Dispatchers.IO)` (`LabelScannerScreen.kt:581-587`). Pausing the analyzer does not cancel a callback already queued to the main executor.

Separately, `record` ignores `NotFound` frames (`LiveEvidenceBuffer.kt:65-69`). A confident reading can therefore remain eligible for 1.5 seconds while the camera moves to another package or loses the table; there is no scene/package identity binding. Three agreeing frames are required, but disagreement and `NotFound` handling do not prove that those frames belong to the captured still.

**Impact**: At minimum this permits inconsistent snapshots or a concurrent-modification failure. More importantly, stale live evidence can corroborate a still, make a wrong value proposable, or cause Strategy B to be skipped (`SelectedTableResolution.kt:128-180`).

**Recommendation**: Confine the buffer to one dispatcher or synchronize snapshot/record. At shutter time, atomically snapshot it on its owning thread, version it with the capture session, and invalidate it on scene discontinuity/NotFound streaks. Bind retained evidence to a spatial/text fingerprint or do not let it affect Strategy B execution.

### P1 — There is no independent, sufficiently diverse optical benchmark

The committed optical corpus has only 9 cropped images. Historical measurement reported 4/9 correct at the parser level for the bundled ML Kit model; that is a historical baseline, not a current score. The newer session corpora are much larger (19, 17, 21, 16, and 29 captures), but they store recognized elements for deterministic replay and were also used to design the current heuristics.

Coverage gaps include:

- full CameraX acquisition in an automated release gate;
- held-out products not used to tune thresholds/regexes;
- multiple manufacturers, cameras, and low/mid/high device tiers;
- systematic blur, glare, curvature, rotation, distance, and crop variation;
- repeated captures of the same label to estimate recognition variance;
- explicit country/language/layout stratification;
- an executable manifest for every physical capture's exact printed value and basis.

**Impact**: Zero observed errors on a small, tuned subset is not evidence of a low field error rate. Regressions at the recognizer boundary can coexist with 1,678 passing JVM tests, as the current run demonstrates.

**Recommendation**: Freeze a held-out optical release set and report selective metrics separately: automatic exact precision/coverage, all-visible-proposal exact precision/coverage, basis accuracy, child-nutrient false selection, abstention rate, and recovery burden. Keep tuning and release sets disjoint.

### P2 — A package-derived corrupted-unit regex is not broadly calibrated

`UnitAccompanimentPolicy.kt:197-207` treats any decimal token ending in `9` (or a leading-zero integer) as evidence that a unit glyph was corrupted. Three distinct rows can establish a unit-bearing convention even when no clean unit survives.

This protects measured `g -> 9` failures, but a legitimate header-unit-only table with three ordinary values ending in 9 can be misclassified as a value-unit table. Existing controls cover examples such as `2.1`, `4.8`, `3.6`, `47`, and `72`, not a broad distribution of legitimate values ending in 9.

**Impact**: Primarily false rejection/extra recovery, not a silent wrong value. It is still an overfit risk and likely contributes to the current grated-cheese confidence regression.

**Recommendation**: Move this rule behind a calibrated feature with explicit false-positive tests, or require geometric suffix evidence rather than only numeric spelling. Report its precision/recall on held-out unit-bearing and header-only tables.

### P2 — The new semantic model is not the single source of truth

`NutritionDocumentModel` now localizes panels and builds semantic nutrient declarations, but `CrossColumnRatioCheck` rebuilds raw logical rows and columns and extracts the first numeric token nearest each column (`CrossColumnRatioCheck.kt:109-155`, `257-290`). `RecoveryCandidates` and `ScaleAmbiguity` perform additional independent numeric/row ownership work.

**Impact**: A parser can accept a declaration under one ownership model while verification evaluates it under another. Fixes to panel boundaries, declaration grouping, or numeric tokenization require shotgun changes and can create verifier/parser disagreement.

**Recommendation**: Make verification and recovery consume the accepted semantic declaration plus typed sibling cells. Keep raw-document adapters only at the boundary, and include provenance for every supporting row used by a verification verdict.

### P2 — “Resolved” is not a trustworthy state name

`EvidenceResolver.Outcome.Resolved` can mean:

- multiple recognition runs agree (`EvidenceResolver.kt:220-230`);
- Pass A alone produced a result (`243-249`); or
- a lone crop result is structurally corroborated (`287-297`).

Automatic verification currently prevents this overloaded state from automatically advancing by itself, but the old `mayAdvance(outcome)` test demonstrates how easy it is for a caller to interpret “Resolved” as “verified.”

**Recommendation**: Encode evidence grade in the type, e.g. `Parsed`, `Corroborated`, `Verified`, and `Conflicted`, or require a `VerificationVerdict` in every API that asks whether the UI may show/advance a value. Deprecate the outcome-only `mayAdvance` path.

---

## Efficiency Review

### Measured strengths

- Latest hardware session: 477–936 ms end to end, 244–453 ms ML Kit, with debug evidence enabled.
- The still parser was previously measured at roughly 26–198 ms for 36–341 elements after the normalization-cache fix.
- The work-counter regression test passes: for 289 vs 262 elements, normalization work was 5,693 vs 4,638 calls (1.23x for a 1.10x input-size ratio), with no warm vocabulary cache misses.
- Live analysis is bounded to one in-flight ML Kit task and the latest CameraX frame.
- Pass A is reused for a zero-recognition-cost filtered parse; Strategy B is skipped when structural verification is already available.
- Debug evidence copying/rendering was moved after result handoff.

### Remaining efficiency risks

1. **No distributional latency data** — a min/max range on one flagship phone is not a P95/P99 guarantee.
2. **Cold-start behavior is under-measured** — recognizer warm-up exists, but cold model initialization and first-capture latency are not a release metric.
3. **Fallback can occupy an IO worker for 5 seconds** — `SelectedRegionRecognizer.kt:70-100` blocks on `CountDownLatch.await`; cancellation is not coroutine-native.
4. **Peak bitmap memory is unmeasured** — 3264x2448 ARGB is about 30.5 MiB before crop/native OCR overhead. Rotation recycling is careful, but the full bitmap and a native-resolution crop coexist during Strategy B.
5. **Static recognizer lifetime** — `SelectedRegionRecognizer` owns a process-lifetime lazy recognizer and never closes it (`SelectedRegionRecognizer.kt:142-157`). This may be acceptable, but ownership should be explicit and profiled.
6. **Parser performance coverage is narrow** — the deterministic cost guard compares only two large documents and catches the previous complexity shape; it does not benchmark all major table layouts.

### Recommended performance gate

- Measure cold and warm P50/P95/P99 for acquisition, decode/rotate, Pass A, parser, Strategy B, resolution, and UI handoff.
- Run on at least low-, mid-, and high-tier devices, plus the emulator only as a reproducibility check.
- Record peak Java/native memory and allocation pressure for rotated and non-rotated 8 MP captures.
- Add cancellation and timeout tests for retake/navigation during Pass A and Strategy B.
- Keep the deterministic work-counter test; add a benchmark suite rather than wall-clock assertions to the ordinary unit suite.

---

## Dead Code

### Completely Dead Files (DELETE)

None found with sufficient confidence. No production file is recommended for immediate deletion.

### Dead Exports (REMOVE)

None that are both unused and clearly outside an intended public/test contract.

### Possibly Dead or Production-Inactive (VERIFY)

| File | Export/path | Reason | Verification needed |
|---|---|---|---|
| `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableLocator.kt` | `NutritionTableLocator` | About 288 lines; explicitly retained, fully tested, and unwired. Production references it only in explanatory KDoc. | Decide whether 2D `NutritionDocumentModel` supersedes it; delete or put it behind a measured experiment. |
| `app/src/main/kotlin/app/justthecarbs/ocr/RecognitionEvidence.kt` | `SECOND_OBSERVATION_PASS`, `RecognitionRun.SECOND_OBSERVATION` | Used in tests and types, but no production acquisition creates this evidence. | Implement the feature or mark it as planned/unavailable so diagnostics do not imply it exists. |
| `gradle/libs.versions.toml:74` | `mlkit-text-gms` | Intentionally retained after a bake-off; not referenced by the app build. | Remove if the bake-off is closed, or move experimental dependencies to documented tooling. |

### Internal Dead Code

- `UnitMarkerFilter.kt:66` suppresses an unused-parameter warning for future context that is not consumed. This is low severity but indicates an API designed for a signal that does not exist yet.
- No private method was found that was conclusively both unreachable and safe to remove; compiler/lint output also did not identify one.

**Total Safe Deletion**: 0 lines immediately.  
**Conditional Cleanup**: roughly 288 production lines if `NutritionTableLocator` is formally retired.

---

## Duplicated Functionality

### HIGH: Session replay harnesses

**Instances**: 5  
**Files**:

- `ThirteenthSessionReplay.kt` — 144 lines
- `FourteenthSessionReplay.kt` — 122 lines
- `FifteenthSessionReplay.kt` — 140 lines
- `SixteenthSessionReplay.kt` — 135 lines
- `SeventeenthSessionReplay.kt` — 135 lines

`SixteenthSessionReplay` and `SeventeenthSessionReplay` differ by only 10 inserted/10 removed lines, primarily corpus/type names and comments. Fifteenth is nearly identical as well.

**Impact**: A trust-policy change can be applied to the newest corpus and omitted from an older one. That has already happened: the newest replay gates wrong proposals while the 21-capture suite gates only wrong automatic advances.

**Recommendation**: Create one generic `PhysicalSessionReplay<Capture>` with a shared classification and policy assertion layer. Corpora should be data only.

**Estimated removable duplication**: 350–450 test lines.

### HIGH: Numeric token extraction and parsing

**Instances**: at least 5  
**Files**:

- `NutritionTableInterpreter.kt:36`, `762-855`
- `CrossColumnRatioCheck.kt:301-328`
- `RecoveryCandidates.kt:797-811`
- `ScaleAmbiguity.kt:307-309`
- `ProseNutritionReader.kt:693-710`

Each accepts a slightly different grammar for numbers, suffix units, fused debris, and punctuation. Some differences are intentional by context, but the lexical primitive and policy are mixed together.

**Impact**: One OCR token can become one number in the parser, another in verification, and nothing in recovery.

**Recommendation**: Centralize lossless token lexing into a typed result (`number`, separator evidence, suffix, percent/energy flags). Let each consumer apply an explicit acceptance policy to that shared parse.

### MEDIUM: Row/column ownership outside the semantic model

**Instances**: parser, ratio verifier, recovery, and scale checking.  
**Analysis**: The new semantic document establishes panels/declarations, while downstream safety stages return to raw rows, nearest-column distance, and local regex parsing.

**Recommendation**: Pass typed declarations and cell provenance through the pipeline so all later stages reason about the exact cells the parser accepted.

---

## Architectural Anti-Patterns

### God Objects / Oversized Policy Modules

| File | Lines | Responsibilities |
|---|---:|---|
| `NutritionColumnKind.kt` | 1,041 | column discovery, header splitting, unit/quantity reconstruction, percent inference, geometry thresholds |
| `NutritionTableInterpreter.kt` | 942 | orchestration, declaration interpretation, serving logic, prose fallback, candidate selection, numeric lexing |
| `RecoveryCandidates.kt` | 820 | hit-testing, row selection, child rejection, candidate extraction, basis/column inference, contradiction checks |
| `ProseNutritionReader.kt` | 727 | prose/table discrimination, tokenization, declarations, terminology search, numeric interpretation |
| `LabelAnalyzer.kt` | 700 | live analysis, still queueing, decoding, ML Kit lifecycle, parsing, tracing, evidence handoff |
| `ScanEvidenceRecorder.kt` | 741 | debug-only evidence formats and asynchronous persistence |

The first four are correctness-critical policy objects. Their size and overlapping concepts increase the probability that a safety rule is fixed in one path but not another.

### Cross-context mutable singleton-like state

`LiveEvidenceBuffer` has no ownership or synchronization contract despite main-thread writes and IO-thread reads. `SelectedRegionRecognizer` owns a static recognizer whose lifetime is the process.

### Sentinel default hides incomplete provenance

`PhysicalObservationId.UNKNOWN` is a safe failure default—it prevents accidental verification—but it also lets production compile while the intended provenance route is entirely unwired. A required capture ID at the production construction boundary would make omission visible.

### Validation layer violation

The release-relevant ML Kit boundary is validated only by a small instrumentation suite, while most policy gates operate on replay data. As a result, “green parser” and “green OCR” are easy to conflate.

### Circular Dependencies

None found in executable OCR code. KDoc cross-references are extensive but do not create runtime cycles.

### Layer Violations

None material found. Android/CameraX/ML Kit types are kept at the boundary; parser and decision logic remain pure Kotlin.

### Tight Coupling

Final presentation requires coordinating `EvidenceResolver`, `AutomaticVerification`, `ReadingEligibility`, `ScaleAmbiguity`, `AutomaticScanAdvance`, and `ScanPresentationDecision`. The policy is defensible but distributed. The existence of the obsolete outcome-only `mayAdvance` call demonstrates that callers can select only part of the trust protocol.

---

## Type Issues

### Unsafe Generic / Dynamic Types

None found. This is Kotlin and the OCR model uses explicit sealed outcomes, value classes, nullable fields, and `BigDecimal`.

### Non-null Assertions and Casts

Six `!!` occurrences and one forced `LabelReading.Confident` cast exist in production OCR. Inspection found them locally guarded by prior null/type checks; none is currently a demonstrated crash. They should still be reduced when the large functions are split because their safety is positional rather than encoded in types.

### Semantic Type Issue 1: Resolution vs verification

`EvidenceResolver.Outcome.Resolved` does not encode whether the result was parsed, cross-run corroborated, structurally verified, or merely inherited from Pass A. This is the highest-value type refactor.

### Semantic Type Issue 2: Optional physical provenance

`RecognitionEvidence.physicalObservation` defaults to `UNKNOWN`. The default is fail-safe, but a required ID for all camera-derived evidence would prevent the current production/test divergence.

### Precision Boundary

OCR values are parsed as `BigDecimal`, converted to `Double` for `NutritionValueValidator`, then converted back with `BigDecimal.valueOf` (`NutritionTableInterpreter.kt:262`, `284`; `NutritionValueValidator.kt:25-39`). Current OCR precision is small enough that this is unlikely to explain observed regressions, but accepting `BigDecimal` directly would preserve the type contract and remove an unnecessary numeric boundary.

---

## Code Smells

### Long Functions (>50 lines)

A structural scan flagged 50 candidate function spans over 50 lines; comments inflate that number, so the most material executable/policy cases were reviewed manually:

| File | Function | Approximate span | Issue |
|---|---|---:|---|
| `NutritionTableInterpreter.kt:94` | `interpretPanel` | ~370 lines | candidate extraction, rejection, selection, serving and fallback policy in one method |
| `NutritionColumnKind.kt:95` | `columnsIn` | ~210 lines | several header grammars and geometry repairs combined |
| `NutritionColumnKind.kt:311` | `splitRunTogetherBases` | ~185 lines | lexical repair plus synthetic geometry construction |
| `EvidenceResolver.kt:168` | `resolve` | ~139 lines | six evidence rules and outcome construction |
| `RecoveryCandidates.kt:501` | `candidatesOn` | ~137 lines | numeric extraction, ownership, eligibility, and contradiction policy |
| `AutomaticVerification.kt:147` | `verify` | ~133 lines | conflict, structural, physical-observation, and diagnostic policy |
| `SelectedRegionRecognizer.kt:51` | `recognise` | ~129-line span | bitmap ownership, asynchronous ML Kit, timeout, parse, translation, and cleanup |

### Complex Conditionals

- The final trust decision is distributed across several objects and relies on callers choosing the complete API.
- Column recognition and recovery have deep, exception-driven conditional trees tuned from individual captures.
- Cross-column verification has a second, narrower interpretation of the table rather than consuming the semantic result.

### Magic Numbers

Most thresholds are named and documented. The notable exception in spirit is not an unnamed number but a narrowly encoded glyph assumption: trailing `9` means a likely corrupted `g` in `UnitAccompanimentPolicy.kt:207`.

### Commented-Out Code

None found. Historical implementations are generally described rather than left commented out.

### Documentation in executable files

Many correctness-critical files contain long incident histories and rejected-experiment narratives. The reasoning is valuable, but placing all history inline makes current invariants hard to locate and contributes heavily to 700–1,000-line modules. Keep concise invariant KDoc next to code and move measurement narratives to versioned design records with fixture/test links.

---

## Statistics

**Code and tests**:

- Production OCR: 63 files, 14,672 lines
- JVM OCR tests/fixtures: 149 files, approximately 56,057 lines
- Android OCR tests: 17 files, approximately 3,445 lines
- JVM run: 1,678 tests, 0 failures, 0 errors, 0 skipped
- Selected connected OCR run: 33 tests, 12 failures
- Lint: successful, no lint errors

**Dead Code**:

- Safe immediate file deletion: 0
- Production-inactive paths: 2
- Conditional production cleanup: ~288 lines

**Duplication**:

- Groups: 3
- Near-duplicate session replay files: 5
- Estimated replay duplication: 350–450 lines

**Architectural Issues**:

- God/oversized modules: 6
- Circular executable dependencies: 0 found
- Layer violations: 0 material
- Other material architectural issues: 7

**Type Issues**:

- Dynamic/unsafe `Any` usage: 0
- Suppressed type errors: 0
- Guarded non-null assertions: 6
- Semantic type-model issues: 2

**Code Smells**:

- Candidate long-function spans: 50; 7 high-value cases listed
- Broadly risky package-tuned heuristics: 1 confirmed
- Commented-out implementation blocks: 0

---

## Impact Assessment

### Accuracy

The dominant current risk is not numeric arithmetic; it is recognition and ownership. ML Kit can drop separators, turn `g` into `9`, merge neighboring columns, or reorder rows. The parser is designed to abstain under uncertainty, but current semantic changes also regress previously correct optical cases. Accuracy cannot be summarized by the passing JVM count.

### Efficiency

The common path appears responsive on the one measured flagship device. Optimization should not be the immediate priority. The next performance work should establish distributions and memory bounds, not add recognition shortcuts that weaken trust.

### Trust

Automatic trust is much stronger than earlier versions: same-photo reprocessing no longer masquerades as independent evidence, and cross-column contradiction blocks automatic advancement. The remaining known wrong confirmation proposal and incomplete second-observation path prevent a trustworthy-result claim across the whole recorded corpus.

### Maintainability

- **Immediate code reduction**: none recommended before correctness is restored.
- **Low-risk test consolidation**: about 350–450 lines.
- **Possible experimental cleanup**: about 288 lines.
- **Highest leverage refactor**: one typed semantic declaration/cell model shared by interpretation, verification, scale checking, and recovery.

---

## Recommended Remediation Order

### Release blockers

1. **Restore the optical gate**: fix or intentionally migrate all 12 connected-test failures; require exact value, basis, provenance meaning, and final action.
2. **Remove the known wrong proposal**: never prefill unsupported-scale digits from one physical observation.
3. **Unify safety assertions across every session corpus**: `WRONG_AUTO == 0` and `WRONG_PROPOSAL == 0`, not just on the newest set.

### Trust architecture

4. **Wire physical observation IDs and a real second-still route**.
5. **Make live evidence atomic, capture-versioned, and scene-bound**.
6. **Replace `Resolved` with evidence-grade states and remove outcome-only advancement APIs**.

### Accuracy validation

7. **Create a held-out optical benchmark manifest** with exact printed ground truth and final-action expectations.
8. **Report selective precision and coverage separately** for automatic advances and all visible proposals.
9. **Grow the optical set before claiming reliability**. As an illustration, observing zero errors in 300 independent automatic cases only bounds the error rate to roughly below 1% at 95% confidence; two cases cannot establish production trust.

### Maintainability and efficiency

10. **Make the semantic document the single source of truth** for parser, verifier, scale, and recovery.
11. **Centralize numeric lexing** while keeping context-specific acceptance policies explicit.
12. **Consolidate session replay infrastructure and split oversized policy methods**.
13. **Add cold/warm latency and peak-memory benchmarks across device tiers**; keep the current deterministic complexity guard.

---

## Release Acceptance Criteria

A trustworthy OCR release should satisfy all of the following:

- Full connected OCR suite green on the target bundled ML Kit version.
- Every ground-truthed optical fixture asserts exact total-carbohydrate value and exact basis.
- Zero wrong automatic values and zero wrong prefilled confirmation values across all retained corpora.
- Any capture with unknown ground truth is reported separately and cannot contribute to a safety claim.
- All evidence derived from one photograph shares one required physical observation ID.
- The distinct-observation route is exercised by a real production-flow instrumentation test.
- Live evidence cannot cross a capture/session/scene boundary or be read concurrently without synchronization.
- Held-out optical results are reported separately from tuning/replay results.
- Cold/warm P95 latency and peak memory meet defined limits on low-, mid-, and high-tier devices.

