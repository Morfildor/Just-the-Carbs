# Nutrition-table scanner — Phase 1 audit (2026-08-17)

Baseline commit: `aaedf0c`. Traced by reading the runtime path, not by inferring from class names.
Every claim below is cited to a file and line, or to a measurement.

The feature works 30–50% of the time on real packages. This document establishes **why**, before any
production change, so the fixes are aimed at measured causes rather than plausible ones.

---

## The actual runtime flow

### Live path

```
CameraX ImageAnalysis (1280x720, STRATEGY_KEEP_ONLY_LATEST)
  -> LabelAnalyzer.analyze()                       LabelAnalyzer.kt:46
  -> InputImage.fromMediaImage(rotationDegrees)    LabelAnalyzer.kt:60
  -> ML Kit TextRecognizer (DEFAULT_OPTIONS)
  -> MlKitOcrMapper.toDocument()
  -> NutritionTableParser.parseWithDiagnostics()
  -> AmbiguityStabilityTracker.onFrame()           LabelAnalyzer.kt:64
  -> onReading() -> `reading` state                LabelScannerScreen.kt:233
```

### Still path

```
ImageCapture (3264x2448, CAPTURE_MODE_MAXIMIZE_QUALITY)  LabelScannerScreen.kt:357-369
  -> takePicture(OutputFileOptions -> cacheDir JPEG)     LabelScannerScreen.kt:276
  -> LabelAnalyzer.analyzeStill(context, file, region)   LabelScannerScreen.kt:282
  -> BitmapFactory.decodeFile(ARGB_8888)                 StillImageLoader.kt:34
  -> applyExifRotation()                                 StillImageLoader.kt:51
  -> ScanRegionMapper.expand(region) -> toPixels()       ScanRegion.kt:57,76
  -> Bitmap.createBitmap(crop)                           StillImageLoader.kt:45
  -> InputImage.fromBitmap(cropped, 0)                   LabelAnalyzer.kt:130
  -> same parser as above, with `full = true` diagnostics
```

All three use cases bind through one `ViewPort` matched to the `PreviewView`
(`LabelScannerScreen.kt:380-385`), inside `doOnLayout` because `viewPort` is null before measurement.
That binding is what makes `ScanRegionMapper`'s fraction arithmetic correct, and it is sound.

---

## Finding 1 — the live frame is authoritative; the user often never reaches capture

**Severity: highest. This is the most likely single cause of the field failure rate.**

```kotlin
LaunchedEffect(reading) { if (reading != null) analyzer.pause() }   // LabelScannerScreen.kt:246
```

`reading` is set by **any** live-frame result, including `NotFound`
(`AmbiguityStabilityTracker` passes `Confident`/`NotFound` straight through; only `Ambiguous` is
debounced). The moment it becomes non-null the UI leaves `SearchingCard` — and `SearchingCard` is the
**only** composable that renders the primary `CaptureButton` (`LabelScannerScreen.kt:606`).

Consequences, in the order the user experiences them:

1. Scanner opens. Within a few hundred ms a 720p frame produces a reading.
2. The screen commits to that reading and renders a result/`NotFound` card.
3. The 8 MP capture path — the ROI crop, the EXIF handling, `CAPTURE_MODE_MAXIMIZE_QUALITY` — is
   **never exercised** unless the user notices the secondary "Capture label" action and taps it.

So the quality work done in the 2026-08-16 pass is largely dormant in the field. The authoritative
result is a 1280x720 analysis frame of a table the user may still have been aiming.

`analyzer.pause()` only sets a `@Volatile` flag (`LabelAnalyzer.kt:36`); `ImageAnalysis` keeps
delivering frames, which are decoded and discarded. Pausing is not stopping.

## Finding 2 — the nine-fixture corpus does not exercise the production still path

`RealImageOcrTest.documentFor` calls `InputImage.fromBitmap(bitmap, 0)` on the **whole asset**
(`RealImageOcrTest.kt:78`). It never calls `LabelAnalyzer.analyzeStill`, `StillImageLoader`, or
`ScanRegionMapper`. Confirmed by search: `analyzeStill` has exactly **one** caller in the repo, the
UI, and **zero** test callers.

The corpus therefore measures *parser-given-recognition*. It does not measure the camera pipeline,
the crop, the EXIF rotation, or the resolution the device actually feeds ML Kit.

Measured fixture dimensions:

| Fixture | Pixels |
|---|---|
| kinder_multicolumn_piece | 900x1600 |
| sondey_multilingual_100g | 900x1600 |
| real_grated_cheese_multicolumn_02 | 1204x1050 |
| real_jar_prose_multilingual_03 | 1261x1020 |
| real_juice_bilingual_per100ml_01 | 1503x810 |
| real_lid_prose_curved_04 | 1379x1370 |
| real_stokbrood_prose_dense_06 | 1125x1320 |
| real_witte_kaas_single_column_05 | 1231x1260 |
| real_yoghurt_serving_column_07 | 1362x1280 |

These are pre-cropped, WhatsApp-recompressed images of roughly one analysis frame's size. Production
sends ML Kit an 8 MP capture cropped to roughly a third of frame. **The corpus and production differ
in scale by close to an order of magnitude**, and in *both* directions depending on fixture.

Neither the four Confident results nor the four NotFound results can be assumed to transfer to a
device. This is the same class of defect as the `assumeTrue` skips the last pass removed: the suite
is green about something other than the thing that is broken.

## Finding 3 — three of four NotFound fixtures fail at one recoverable stage

The value is already in hand in each case; only the **basis column** is missing.

| Fixture | Total row reconstructed | Blocking stage |
|---|---|---|
| juice (#1) | `Koolhydraten / Kohlenhydrate 9,0 g` | header fused to `100mienthaltendurchschnittlich` -> `rejected: 9.0: no column` |
| witte kaas (#5) | `Koolhydraten 2,3 g` | header lost -> `rejected: 2.3: no column` |
| jar (#3) | value present | `Naringsindhold (100g):` opens no declaration (no connective) |
| lid (#4) | value present | `PourPerlPro 100g:` fused -> no connective |

Refusing is currently **correct** — an unplaceable per-100-ml figure shown as per-100-g is a wrong
number someone doses from, and the app has no density data. The safety rule should not be weakened.

But this is where the recall is being lost, and it is lost at the *header*, not at the carbohydrate
row. A targeted high-resolution re-OCR of the header region (brief Phase 4, Pass B) attacks exactly
this stage and requires **no** change to any safety rule. It is the highest-value recall work
available.

## Finding 4 — no focus or exposure control exists

Searched for `FocusMeteringAction`, `startFocusAndMetering`, `MeteringPoint`, `afMode`,
`Camera2Interop`. **Zero hits across the whole source tree.**

Capture fires on whatever autofocus state happened to exist when the shutter was tapped. For text a
few millimetres high this is a large and cheap source of variance, and it is invisible in fixtures —
every fixture is a photograph someone already focused by hand.

## Finding 5 — the still decode path is an OOM risk that surfaces as NotFound

`StillImageLoader.load` (`StillImageLoader.kt:33-49`):

1. decodes 3264x2448 at `ARGB_8888` — ~32 MB;
2. `applyExifRotation` allocates a **second** full-size bitmap before recycling the first — ~64 MB peak;
3. crops, allocating a third.

There is no `inSampleSize`, no `inBitmap` reuse, and no `Bitmap.Config.RGB_565` fallback. On a
mid-range device this can throw `OutOfMemoryError`, which is caught broadly at `LabelAnalyzer.kt:153`
and reported to the user as… `NotFound`. Some field failures are plausibly this rather than
recognition, and nothing currently distinguishes them.

Note the rotation is also unnecessary in the common case: CameraX writes EXIF orientation, and the
crop rectangle is expressed in *upright preview* fractions, so the rotate-then-crop order is required
— but it should be done with a single `Matrix`-aware decode rather than two full copies.

---

## Finding 6 — MEASURED: the production ROI crop destroys both canaries

**This is the decisive measurement of the audit.** Findings 1–5 were read from code; this one was run.

`ProductionStillPathBaselineTest` (androidTest) runs the fixtures through `StillImageLoader` +
`ScanRegionMapper` — the real still path — instead of `InputImage.fromBitmap(whole_asset, 0)`.

| Fixture | Golden suite (whole asset) | **Production crop path** | Delta |
|---|---|---|---|
| juice | NotFound | NotFound | — |
| grated cheese | Confident 2.09 | Confident 2.09 | — |
| jar | NotFound | NotFound | — |
| lid | NotFound | NotFound | — |
| witte kaas | NotFound | **Confident 2.3** | **gained** |
| stokbrood | Confident 46 | Confident 46 | — |
| yoghurt | Confident 5 | **NotFound** | **lost** |
| sondey | Confident 61.9 | **NotFound** | **lost (canary)** |
| kinder | Confident 53.5 | **NotFound** | **lost (canary)** |

**4 correct on the measured suite becomes 2 correct on the path the device runs.**

### The mechanism, from `CropTruncationDiagnosticTest`

For both canaries the crop rect is `left=0 top=204 w=900 h=1191` on a 900x1600 image — it removes the
top 12.75% and the bottom. Diagnostics after the crop:

```
sondey  CROPPED: rejected: 61.9: REFERENCE_PERCENT column
        result: Total-carbohydrate row found but no usable per-100 cell
kinder  CROPPED: result: Total-carbohydrate row found but no usable per-100 cell
```

The carbohydrate row is still found and the value 61.9 is still read correctly. What the crop removes
is the **basis header band** (`o/100 g`), so `ColumnClassifier` no longer has a PER_100_G header and
reclassifies that x-position as `REFERENCE_PERCENT`. `NutritionTableInterpreter` then correctly
refuses a value it cannot place.

**The safety architecture behaved exactly as designed. It was handed a truncated image.** No parser
rule is at fault, and no parser rule should be relaxed to compensate.

Why witte kaas *gains* from the same crop: cropping removes competing prose that was defeating row
reconstruction. So the crop is not simply harmful — it is **unconditionally applied where it should be
evidence-driven**. A crop that removes the header must not be used; a crop that removes only prose
should be.

### Consequence for the brief's premise

Capture-first gating alone would make things **worse**, not better: it would route every user through
the crop path that currently breaks both canaries. Fixing the crop is a prerequisite for, not a
follow-on to, making capture authoritative.

## Finding 7 — the "unrecoverable" 2.09 is scale-dependent, not absolute

From the same baseline run, grated cheese:

```
full scale 1204x1050 -> Confident 2.09   (the accepted-wrong value)
scaled x0.5  602x525 -> Confident 2      (the PRINTED value)
scaled x0.75 903x787 -> Confident 2
```

CLAUDE.md records 2.09 as an unrecoverable recognition-stage failure. It is unrecoverable *at that
scale*. This is direct evidence that the brief's Pass D — independent re-recognition of a tighter or
differently-scaled crop — can recover the correct digit **without any numeric-repair rule**, which is
exactly the distinction the brief draws between legitimate re-recognition and forbidden invention.

Note this does **not** license a global downscale: the same run shows sondey and kinder going
Confident -> NotFound at x0.5 and x0.75. Scale sensitivity runs in both directions per label, which is
why the answer is targeted multi-pass recognition with evidence fusion rather than a single global
transform.

---

## What the audit implies for the plan

The brief's Phase 2 (capture-first) is correct but under-specified in one respect: the problem is not
only that the live frame is *low resolution*, it is that the live frame **pre-empts the capture UI
entirely**. Gating on capture is therefore both the reliability fix and the UX fix.

Priority order, revised after the measurements in Findings 6 and 7:

1. **Finding 6** — the crop must stop truncating headers. This is now the top item: it is the largest
   measured regression on the production path, and it blocks capture-first from being an improvement.
   The fix is evidence-driven cropping (recognise the whole capture, and use the crop only when it
   does not remove the basis header band), not a smaller margin — a margin is a guess, the header's
   position is observable.
2. **Finding 1** — capture-first gating. Depends on 1 above being done first.
3. **Finding 4** — focus/metering before capture.
4. **Finding 2** — the corpus must run the real still path. No production change; it changes what
   every later number *means*, so it gates all measurement.
5. **Finding 3 + 7** — targeted multi-pass recognition (header band, carbohydrate row, numeric cell)
   with evidence fusion. Attacks the stage losing three of four NotFounds, and Finding 7 shows it can
   also recover the one confident-wrong.
6. **Finding 5** — bounded-memory decode.

**A global preprocessing transform is ruled out by measurement, not by precedent.** Finding 7 shows
downscaling helps one fixture and breaks two others; the 2026-08-16 pass found upscaling does the
mirror image. Any scale change must therefore be per-region and evidence-fused.

Phases 4 (full evidence fusion), 5 (basis grammar), 9 (corpus expansion) and 10 (device QA) are
scoped **after** the above, against a baseline that is actually measured on the production path.

---

## Explicitly not changed by this audit

Barcode scanning (`BarcodeAnalyzer`, `BarcodeStabilityTracker`, `BarcodeFrameReader`) is out of
scope and frozen. It binds its own camera in `ScannerScreen.kt`, separate from
`LabelScannerScreen.kt`; the two share no CameraX configuration, so label-side changes cannot reach
it. Verified by reading both bindings.
