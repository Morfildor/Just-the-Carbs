# Nutrition scanner reliability pass — FINAL CHECKPOINT

**Nothing is committed.** Working tree only, awaiting physical-device verification.
Date: 2026-08-18. Full measurement log: `2026-08-18-scanner-reliability-measurements.md`.

---

## 1. Root causes — what was actually making physical recognition fail

All four findings in the brief were verified directly in source before any change.

### A. The crop architecture could not recover recognition failures — CONFIRMED

`SelectedTableReader.read` calls `ElementRegionFilter.filter(document, region)` and then
`NutritionTableParser.parseWithDiagnostics(filtered)`. No recognizer is referenced anywhere in that
path, and `ElementRegionFilter` only ever `.filter{}`s an existing element list. Moving the rectangle
could never make an unrecognised character appear.

### B. Usable live evidence was being discarded — CONFIRMED, with the exact mechanism

`LabelAnalyzer(onReading = { liveReadiness = it })`. `liveReadiness` was read in **exactly one
place**: choosing between the `ocr_ready_to_capture` / `ocr_move_closer` / `ocr_looking` strings.
`captureLabel()` then set `reading = null` and `resumeLive()` set `liveReadiness = null`. The
`LabelReading.Confident` object that produced *"Table in view — tap to capture"* in the recording was
never read for its value by any code. That is the Stroopwafel failure precisely: live succeeded, the
user captured, the still path failed, and the evidence had already been thrown away.

### C. The initial crop rectangle was very broad — CONFIRMED

The proposal is `ScanRegionMapper.expand(scanRegion ?: DEFAULT_CROP)` — the whole scan guide, widened
by a safety margin.

### D. ML Kit is itself part of the problem — CONFIRMED

`MlKitOcrMapper` built `OcrElement(text, box, blockId, lineId)` and discarded `getConfidence()`,
`getAngle()`, `getSymbols()` and `getRecognizedLanguage()` entirely.

### The additional root cause found by measurement, which the brief did not anticipate

**Re-recognition damage does not require rescaling.** The historic `(g)` → `(9)` confident-wrong was
attributed to rescaling changing ML Kit's tokenisation. Measured here: with **no resize whatsoever**,
a native-resolution crop at the production overlay rectangle still turned three canaries
(kinder, yoghurt, stokbrood) from `Confident` into `NotFound`, and produced a brand-new wrong value on
grated cheese (`2.09` → `2.04`). Losing context the parser needs — basis headers, prose declaration
spans — is sufficient on its own.

---

## 2. OCR engine bake-off

**The two candidates cannot coexist in one APK.** Established by inspecting the artifacts:
`com.google.mlkit:text-recognition:16.0.1` and
`com.google.android.gms:play-services-mlkit-text-recognition:19.0.1` both define
`com.google.mlkit.vision.text.latin.TextRecognizerOptions`. They are interchangeable implementations
of one API, so a side-by-side comparison in a single test APK is not buildable. A sequential
dependency swap was used instead, with the harness identifying the linked engine at runtime from an
artifact-specific marker class (never from a build flag).

| Run | Engine | Result |
|---|---|---|
| 1 | Bundled (as shipped) | **4/9 correct**, 1.0–6.7 s per fixture |
| 2 | Play Services | **UNAVAILABLE on every fixture**, 66–154 ms |

Run 2 is **not a quality result**. The `carbscan` AVD is a plain `sdk_gphone64` image with no Play
Services, so the on-demand model can never be delivered; the ~100 ms failures are delivery failures,
not recognition (a real recognition of these images takes 1–7 seconds).

**What it does establish:** adopting the Play Services model would make the scanner completely
non-functional on any device without Play Services, and would require explicit model-availability
handling plus a first-run download path before it could ship.

**Verdict: the bundled model stays** — not because it measured better, but because the alternative is
unevaluated on available hardware and carries a hard availability dependency the bundled model does
not. Re-running run 2 on a Play-Store-enabled AVD or a physical phone is an open item; the harness is
in place and the swap is a one-line change documented in `OcrEngineBakeOffTest`'s KDoc.

---

## 3. Recognition architecture

```
                        ┌→ Pass A: ML Kit over the WHOLE capture ──────┐
capture bitmap ─────────┤                                              │
  (retained, upright)   └→ user confirms the table rectangle           │
                                    │                                  │
                        ┌───────────┴───────────┐                      │
                   Strategy A              Strategy B                  │
              filter Pass A's          fresh ML Kit pass over          │
              elements, re-parse       the NATIVE-RESOLUTION crop      │
              (no recognition)         (no resize, no re-encode)       │
                        │                       │                      │
    pre-shutter live ───┼───────────────────────┼──────────────────────┘
    stable consensus    │                       │
                        └──────→ EvidenceResolver ←──────┘
                                      │
              ┌───────────┬───────────┴───────┬─────────────┐
          Resolved   NeedsVerification    Conflicted     Nothing
              │              │                 │             │
           result      "check this"      "two readings"   assisted
                         + photo          + no value       tap-row/
                                                           tap-value/
                                                           type-in
```

**The resolver never votes.** Agreement across *independent recognition runs* resolves; disagreement
always refuses; a lone Pass A keeps exactly the standing it had before this pass existed; an
uncorroborated re-recognition or live reading is *proposed for verification*, never accepted silently.

### The consensus bug that measurement caught — the single most important fix in this pass

The first wired implementation **resolved grated cheese confidently to the known-wrong `2.09`**.

Consensus was counted over evidence *sources*. But `FULL_FRAME_PASS_A` and `FILTERED_PASS_A` are two
*parses of one recognition* — the filtered document is a strict subset of the same elements with
identical characters. When both produced `2.09`:

1. the resolver saw "two sources agree" and marked it corroborated, **and**
2. the staged-execution rule saw the same agreement and **skipped the independent recognition
   entirely** — the one pass that would have contradicted it.

A single wrong opinion promoted itself to a confirmed answer *and* suppressed its own contradiction.

**Fixed structurally**, not by a threshold: `EvidenceSource.recognitionRun` maps sources onto the
recognition that produced them (`PASS_A` / `SELECTED_REGION` / `LIVE`), and both the resolver and the
staging rule now count **distinct runs**. Two parses of one recognition can never corroborate each
other, because only a separate run can independently be wrong.

Pinned by `EvidenceResolverTest.the two pass A views cannot corroborate each other` and by
`EvidencePipelineProductionTest.gratedCheeseNeverSilentlyResolvesTheDisputedValue`.

---

## 4. Real-image corpus — measured through the production evidence pipeline

`EvidencePipelineProductionTest`: **8/8 passing** on real photographs with the real recognizer.

| Fixture | Printed | Pipeline outcome | Notes |
|---|---|---|---|
| sondey | 61.9 /100 g | **Resolved 61.9** | canary intact |
| kinder | 53.5 /100 g | **Resolved 53.5** | canary intact; `9` never offered |
| yoghurt | 5.0 /100 g | **Resolved 5** | canary intact |
| stokbrood | 46 /100 g | **Resolved 46** | canary intact, prose path |
| witte kaas | 2.3 /100 g | **surfaced 2.3** | **RECOVERED** — was `NotFound` before this pass |
| grated cheese | 2,0 /100 g | **refused** | was silently `2.09`; now conflicts rather than lying |
| jar / lid | — | refused | fat/sugar figures never offered |

**Confident-wrong count: 0.** One pre-existing confident-wrong (`2.09`) was *eliminated* by this pass
— not by a repair rule, but because an independent recognition disagrees with it and the resolver
refuses rather than choosing.

### Crop tightness sweep — why re-recognition can never be the oracle

```
fixture         0.00 (full)   0.05        0.10        0.15
sondey          Conf 61.9     Conf 61.9   NotFound    NotFound
kinder          Conf 53.5     NotFound    NotFound    NotFound
yoghurt         Conf 5        Conf 5      NotFound    NotFound
stokbrood       Conf 46       NotFound    Conf 46     Conf 46     <- non-monotonic
witte kaas      NotFound      NotFound    Conf 2.3    Conf 2.3    <- recovery
grated cheese   Conf 2.09     Conf 2      Conf 2      NotFound    <- correction
```

No tightness is safe across the corpus, and stokbrood's non-monotonic behaviour shows the failure is
not "too tight" or "too loose" — it is per-label. **Do not tune this constant.**

---

## 5. Confidence metadata (§7)

Measured as **fully populated — zero NaN** across all nine fixtures at both element and symbol level
(506/367/173/… elements). It is diagnostic: the mangled Kinder token `Uokohidiat/0gjikovi` scores
**0.452** while clean numerics score **0.88+**.

Retained on `OcrElement` as nullable fields with defaults, so no existing parser fixture changed.
Used only to **withhold** a proposal (`MIN_PROPOSAL_CONFIDENCE = 0.60`), never to decide which
nutrient a number belongs to — pinned by
`EvidenceResolverTest.high confidence does not win a conflict`.

---

## 6. Assisted fallback — the dead end is gone (§17–§19)

When automatic recognition cannot resolve, the **frozen photograph stays on screen** and the user is
offered three routes, none of which navigate away:

1. **Tap the carbohydrate row** — candidates are restricted to the tapped row, so the sugars figure on
   the next row is not in the returned list at all.
2. **Tap the number** — every value-shaped token is outlined; embedded digits in words
   (`0gjikovi`) are never offered.
3. **Type it in** — with the table still visible, accepting `,` or `.` as the decimal separator.

The basis is always asked for, never assumed. **This weakens no safety rule**: every parser refusal
exists because the app could not determine *which nutrient a number belongs to*, and the tap supplies
exactly that association from a human reading the package. The app does not re-guess afterwards.

Covered by `AssistedSelectionTest` (12 JVM cases, incl. "tapping the carbohydrate row cannot return
the sugars value") and `AssistedReadingScreenTest` (10 instrumented cases).

---

## 7. Camera resolution and ViewPort (§23–§24)

`ImageCapture.resolutionInfo` is now read **after binding** (the only time it is populated) and
recorded to both logcat and the exportable evidence bundle's `meta.txt`:

```
capture config  : requested=3264x2448 selected=<actual> viewportCrop=<rect> rotation=<deg>
```

This closes the specific trap named in the brief — a `900x1600` fixture-replay dimension was once
mistaken for a physical camera result. `meta.txt` now prints
`not recorded (no camera bind)` for replays rather than a resolution no camera produced.

**The negotiated resolution on physical hardware remains unmeasured.** The emulator's virtual camera
cannot answer it; your device run will.

---

## 8. Crop UX (§10–§11)

- Title changed to **"Tighten the box around the table"**; body explicitly says to leave out
  ingredients and other text.
- Scrim raised 0.55 → **0.78**, so excluded text reads as *dismissed* rather than merely tinted.
- Handles redrawn as amber corner brackets with a ringed centre — previously flat white dots on a
  white outline, which read as part of the frame rather than as controls.
- **Ineffective-selection detection**: `SelectedTableReader.Result.isIneffective` flags a rectangle
  that removed <2% of elements, and the assisted screen then says so specifically. Deliberately
  **not** a rule about rectangle size — a large table can legitimately fill the frame, and measured
  evidence shows rectangle size predicts nothing.

---

## 9. Barcode lock (§27) — PROVEN UNCHANGED

```
IDENTICAL  ui/scan/ScannerScreen.kt
IDENTICAL  ui/scan/BarcodeAnalyzer.kt
IDENTICAL  domain/BarcodeStabilityTracker.kt
IDENTICAL  domain/BarcodeFrameReader.kt
```

Hash-identical to `HEAD` (`git hash-object` vs `git rev-parse HEAD:<path>`). No crop, evidence,
resolver or assist type is referenced anywhere in the barcode path. **43 barcode JVM tests green.**

---

## 10. Tests

All counted from JUnit XML after `--rerun-tasks`, as required.

| Suite | Before | After |
|---|---|---|
| **JVM** (0 skipped) | 653 | **707 / 707** |
| **Instrumented, complete single run** (0 skipped) | 189 | **212 / 212** |
| `ProductionStillPipelineTest` | 8/8 | **8/8** |
| `EvidencePipelineProductionTest` | — | **8/8** (new) |
| `AssistedReadingScreenTest` | — | **10/10** (new) |
| Barcode JVM | 43 | **43** |
| Lint | clean | **clean (exit 0)** |

The instrumented run is a **complete single-run suite** (`Finished 212 tests`, 24m47s), not
per-class aggregation. The AVD was rebooted first, per the memory note in `CLAUDE.md` — 1.6 GB
available after reboot, and the run completed without the `TEST_EXECUTION_FAILED` driver abort that
memory pressure previously caused.

New JVM suites: `EvidenceResolverTest` (18), `LiveEvidenceBufferTest` (13),
`SelectedRegionCropTest` (9), `AssistedSelectionTest` (12), `IneffectiveSelectionTest` (6).
New instrumented suites: `EvidencePipelineProductionTest` (8), `AssistedReadingScreenTest` (10),
plus the measurement harnesses `RecognizerCapabilityProbeTest`, `CropTightnessSweepTest` and
`OcrEngineBakeOffTest`.

### Debug APK (§36)

Built with `--rerun-tasks`; **`JustTheCarbs-debug.apk` (90.1 MB) is on the Desktop.**
All new classes verified present in DEX by `dexdump`, not assumed from a successful build:

```
classes12.dex : EvidenceResolver, RecognitionEvidence, SelectedRegionRecognizer,
                SelectedRegionCrop, LiveEvidenceBuffer, AssistedSelection,
                SelectedTableResolution
classes7.dex  : AssistedReadingScreenKt, VerificationScreenKt
```

### Diff scope

Production: `OcrDocument.kt`, `MlKitOcrMapper.kt`, `LabelScannerScreen.kt`,
`CropConfirmationScreen.kt`, `SelectedTableReader.kt`, `OcrDiagnosticsLogger.kt`,
`ScanEvidenceRecorder.kt`, both `strings.xml`; new `RecognitionEvidence.kt`, `EvidenceResolver.kt`,
`SelectedRegionCrop.kt`, `SelectedRegionRecognizer.kt`, `SelectedTableResolution.kt`,
`LiveEvidenceBuffer.kt`, `AssistedSelection.kt`, `AssistedReadingScreen.kt`, `VerificationScreen.kt`.
Tests and docs as listed above. `libs.versions.toml` gains an unused `mlkit-text-gms` alias for the
bake-off swap. **Nothing committed — 0 commits since `aaedf0c`.**

---

## 10a. Latency and memory (§28, §29)

Measured on the **emulator**, which is materially slower than a phone for ML Kit — treat these as
upper bounds and relative costs, not as device figures.

| Stage | Cost | Notes |
|---|---|---|
| Pass A (whole capture, ML Kit) | **1.4–5.4 s** | starts at shutter, overlaps the user's crop gesture |
| Strategy A (filter + re-parse) | **sub-millisecond** | no recognition; re-parses elements already in memory |
| Strategy B (native-resolution crop OCR) | **~1–7 s** | bounded at 8 s; **skipped entirely** when independent runs already agree |
| Resolver | negligible | pure comparison over a handful of evidence objects |
| Assisted mode | instant | reuses Pass A's document; no recognition at all |

**Staged execution means the common case pays nothing new.** When Pass A and a live consensus already
agree, Strategy B never runs. It is spent only when the cheap evidence is inconclusive — which is
exactly when a second opinion is worth a wait.

**Memory (§29):** peak is bounded to *source bitmap + one crop*. `SelectedRegionRecognizer` allocates
the crop with `Bitmap.createBitmap` over a sub-rectangle and recycles it in a `finally` block before
returning, so two 8 MP bitmaps are never held beyond one pass. The source bitmap's lifetime is
unchanged (owned by `PassAResult`, released by `recycle()` on retake or completion). No transformed
or rescaled copies are retained, because none are produced.

---

## 11. Rejected experiments — do not repeat

- **A single "best" crop tightness.** Swept 4 insets × 6 fixtures: none is safe, and behaviour is
  non-monotonic per label.
- **Native-resolution crop as inherently safer than a rescaled one.** Measured false — three canaries
  fall with no resize at all.
- **Counting evidence *sources* for consensus.** Produced a real confident-wrong; must be
  recognition *runs*.
- **Both ML Kit artifacts in one APK.** Duplicate class; not buildable.

---

## 12. Remaining limitations — stated plainly

1. **Nothing in this pass has been seen on a physical device.** The camera path, the crop gesture
   against a real 8 MP capture, focus/exposure behaviour and whether the assisted path is usable
   one-handed in a shop are all unverified.
2. **The Stroopwafel/Lidl label is not in the corpus.** The failing package from your recording has no
   fixture, so its specific failure is addressed only architecturally (live evidence retention +
   assisted fallback), not measured. Adding a sanitized crop of it is the highest-value next fixture.
3. **No exported evidence bundles from your device were available**, so the §21 replay of the actual
   recorded failures could not be performed. The recorder and replay tooling are in place for it.
4. **The Play Services engine is unevaluated** — see §2 above.
5. **Automatic accuracy is 4/9 at parser level on the whole-asset corpus.** The ≥90% automatic target
   in §33 is **not met by automatic recognition alone**; it is met end-to-end only in the sense that
   the assisted path makes every legible label completable. That distinction is deliberate and should
   not be blurred in any release note.
6. **Fixtures 3, 4 and the juice label remain `NotFound`** for the reasons already documented
   (declaration-opener grammar) — untouched by this pass.

---

## 13. Physical acceptance — what to try

1. **Kinder** — capture, confirm the crop, expect `53.5 /100 g` automatically; if not, tap the
   carbohydrate row. Repeat 3×.
2. **Stroopwafel/Lidl** — same, expecting `61.9 /100 g`. This is the label where live evidence
   retention should now matter: if live reaches "Table in view" and the still path fails, you should
   get a **"Check this against the label — 61.9 g per 100 g"** proposal rather than a dead end.
3. **Barcode** — scan any known product; behaviour must be identical to before.

Please also **export an evidence bundle** from a failing scan if you hit one — `meta.txt` now
contains the negotiated capture resolution, which answers the last open camera question.
