# Nutrition scanner reliability pass — measurements

**Status: measurement log, updated as the pass proceeds. Nothing here is committed.**
Date started: 2026-08-18. Emulator `carbscan` (API 36), bundled ML Kit Latin recognizer
(`com.google.mlkit:text-recognition:16.0.1`).

This file records what was *measured*, including results that contradict the pass brief's
assumptions. It exists so that no later session re-derives these numbers or repeats a rejected
experiment.

---

## Baseline before any change

| Suite | Result |
|---|---|
| JVM (`--rerun-tasks`, counted from JUnit XML) | **653/653**, 0 failures, 0 errors, 0 skipped |
| `ProductionStillPipelineTest` (emulator, real fixtures) | **8/8** |

---

## §0 Findings A–D — all four verified directly in source

| Finding | Verdict | Evidence |
|---|---|---|
| **A** — crop architecture cannot recover recognition failures | **Confirmed** | `SelectedTableReader.read` calls `ElementRegionFilter.filter` then `NutritionTableParser.parseWithDiagnostics(filtered)`. No recognizer reference. `ElementRegionFilter` only ever `.filter{}`s `document.elements`. |
| **B** — useful live evidence is discarded | **Confirmed, with mechanism** | `LabelAnalyzer(onReading = { liveReadiness = it })`. `liveReadiness` is read in exactly one place: choosing the `ocr_ready_to_capture` string. `captureLabel()` then sets `reading = null`; `resumeLive()` sets `liveReadiness = null`. The `LabelReading.Confident` that produced "Table in view" is never read for its value. |
| **C** — initial crop is very broad | **Confirmed** | Initial selection is `ScanRegionMapper.expand(scanRegion ?: DEFAULT_CROP)` — the whole scan guide plus a safety margin. |
| **D** — ML Kit itself is part of the problem | **Confirmed** | `MlKitOcrMapper.toDocument` builds `OcrElement(text, box, blockId, lineId)`. `getConfidence()`, `getAngle()`, `getSymbols()`, `getRecognizedLanguage()` all discarded. |

---

## §7 — Is confidence metadata actually populated? **YES**

API verified with `javap` against `play-services-mlkit-text-recognition-common:19.1.0`:
`Text.Element` exposes `getConfidence()`, `getAngle()`, `getSymbols()`; `Text.Symbol` exposes
`getConfidence()`. Existing in the API is not the same as being populated, so it was measured.

**Zero NaN across all nine fixtures**, at both element and symbol level:

```
fixture                                elements  elemNaN  symbols  symNaN  angleNaN  lang
sondey_multilingual_100g.jpg              506       0      3193      0        0       506
kinder_multicolumn_piece.jpg              367       0      2204      0        0       367
real_yoghurt_serving_column_07.jpg        173       0       704      0        0       173
real_stokbrood_prose_dense_06.jpg         138       0       859      0        0       138
real_witte_kaas_single_column_05.jpg       89       0       469      0        0        89
real_grated_cheese_multicolumn_02.jpg      80       0       361      0        0        80
real_juice_bilingual_per100ml_01.jpg       32       0       248      0        0        32
real_jar_prose_multilingual_03.jpg         93       0       534      0        0        93
real_lid_prose_curved_04.jpg              326       0      2081      0        0       326
```

**The signal is diagnostic, not decorative.** On the Kinder fixture the mangled nutrient token
`'Uokohidiat/0gjikovi'` carries confidence **0.452** while clean numeric tokens like `'270'` carry
**0.882**. That mangled token is the very "Kohlenhydrate → severe nonsense" hazard named in the brief
(§16), and confidence separates it from good text.

Confidence is therefore a **usable** signal. Per §7 it must still never decide *which nutrient a
number belongs to* — it answers "how sure was OCR about these characters", not "what does this number
mean". Semantics stay with geometry and the parser.

Harness: `RecognizerCapabilityProbeTest`.

---

## §3 — Native-resolution crop re-OCR: the brief's premise is **half right**, and the half that is
## wrong would have broken three canaries

§3 requires cropping upright source pixels with **no resize** and recognising them. This was measured
directly (`Bitmap.createBitmap` over a sub-rectangle copies source pixels verbatim — no scaling, no
re-encode).

### First measurement, at the production overlay rectangle (0.08–0.92 × 0.20–0.80)

```
fixture                              full frame              native crop
sondey_multilingual_100g.jpg         Confident 61.9/100g     Confident 61.9/100g
kinder_multicolumn_piece.jpg         Confident 53.5/100g     NotFound          <- canary lost
real_yoghurt_serving_column_07.jpg   Confident 5/100g        NotFound          <- canary lost
real_stokbrood_prose_dense_06.jpg    Confident 46/100g       NotFound          <- canary lost
real_witte_kaas_single_column_05.jpg NotFound                NotFound
real_grated_cheese_multicolumn_02.jpg Confident 2.09/100g    Confident 2.04/100g <- NEW wrong value
real_juice_bilingual_per100ml_01.jpg NotFound                NotFound
real_jar_prose_multilingual_03.jpg   NotFound                NotFound
real_lid_prose_curved_04.jpg         NotFound                NotFound
```

Two things this establishes immediately:

1. **Re-recognition damage does not require rescaling.** The historic `(g)` → `(9)` failure was
   attributed to rescaling changing tokenisation. Here there is *no* rescaling and three canaries
   still fall over. The cause is loss of context the parser needs — basis header bands and prose
   declaration spans that sit outside a generic rectangle.
2. **A brand-new confident-wrong appeared spontaneously**: grated cheese `2.09` → `2.04`. Neither is
   the printed `2,0`. This is exactly the §8 conflict case, produced by the first measurement taken.

### Tightness sweep — the result that redirects the pass

The rectangle above is a *generic* box, not a table crop, so the obvious confound was excluded by
sweeping crop tightness (inset = fraction removed from each edge; 0.00 = whole frame):

```
fixture                                0.00 (full)      0.05           0.10           0.15
sondey_multilingual_100g.jpg           Conf 61.9        Conf 61.9      NotFound       NotFound
kinder_multicolumn_piece.jpg           Conf 53.5        NotFound       NotFound       NotFound
real_yoghurt_serving_column_07.jpg     Conf 5           Conf 5         NotFound       NotFound
real_stokbrood_prose_dense_06.jpg      Conf 46          NotFound       Conf 46        Conf 46
real_witte_kaas_single_column_05.jpg   NotFound         NotFound       Conf 2.3       Conf 2.3
real_grated_cheese_multicolumn_02.jpg  Conf 2.09        Conf 2         Conf 2         NotFound
```

**Re-recognition genuinely recovers labels the full frame cannot read.** Two of them:

- **witte kaas**: `NotFound` → **Confident 2.3**, the correct printed value, at insets 0.10 and 0.15.
  This is the "honest regression" recorded in `CLAUDE.md` (the fixture the old pre-recognition crop
  used to help). It is recoverable **without** reinstating a global crop.
- **grated cheese**: `2.09` (a known confident-**wrong**) → **`2`, the actually printed value**, at
  insets 0.05 and 0.10. `CLAUDE.md` records this as "recognition-originated ... unrecoverable at the
  parser. Do not add [a repair rule]." That remains correct advice about *parser repair rules* — and
  it turns out the value is recoverable by **re-recognising different pixels**, which invents nothing.

**And it is categorically unsafe as a replacement result.** In the same table kinder dies at 0.05,
sondey and yoghurt at 0.10, and stokbrood is **non-monotonic** — readable at 0.00, lost at 0.05,
readable again at 0.10 and 0.15. There is no tightness that is safe across the corpus, and no
measurable property of a rectangle predicts which fixture is about to break (this reproduces the
2026-08-17 "crop proposal size proves nothing" finding by a different route).

### Consequences for the design

- §3 is implemented as specified (native resolution, no resize) but **only as an additional evidence
  source**, never as an oracle and never as a replacement — which is what §3 and §6 already require.
- The resolver (§8) must treat *disagreement* between passes as a refusal trigger, because the
  measurements above contain two independent disagreement cases (2.09 vs 2.04 vs 2) where silently
  choosing either would be a confident-wrong.
- Agreement across independently-cropped recognitions is meaningful evidence: sondey agrees at 0.00
  and 0.05, yoghurt agrees at 0.00 and 0.05, stokbrood at 0.00/0.10/0.15, grated cheese at 0.05/0.10.

Harnesses: `RecognizerCapabilityProbeTest`, `CropTightnessSweepTest`.

---

---

## The consensus bug that measurement caught — READ THIS BEFORE CHANGING THE RESOLVER

The first wired implementation of the evidence pipeline **resolved grated cheese confidently to the
known-wrong `2.09`**, and the reason is a subtle and repeatable design error.

Consensus was counted over evidence **sources**. But `FULL_FRAME_PASS_A` and `FILTERED_PASS_A` are two
*parses* of a single *recognition* — the filtered document is a strict subset of the same elements,
carrying identical characters. When both produced `2.09`:

1. the resolver saw "two sources agree" and marked the value corroborated; and
2. the staged-execution rule saw the same agreement and **skipped the independent recognition
   entirely** — the one pass that would have contradicted it.

A single wrong opinion promoted itself to a confirmed answer *and* suppressed its own contradiction.

**The fix is structural, not a threshold.** `EvidenceSource.recognitionRun` maps sources onto the
recognition that produced them (`PASS_A`, `SELECTED_REGION`, `LIVE`), and both the resolver and the
staging rule now count **distinct runs**. Two parses of one recognition can never corroborate each
other; only a separate run can, because only a separate run can independently be wrong.

Pinned by `EvidenceResolverTest.the two pass A views cannot corroborate each other` and by
`EvidencePipelineProductionTest.gratedCheeseNeverSilentlyResolvesTheDisputedValue`, which asserts the
refusal against the real photograph.

After the fix: **`EvidencePipelineProductionTest` 8/8 on the real corpus**, all four canaries intact,
witte kaas recovered, and the disputed value refused rather than resolved.

---

---

## §12 OCR engine bake-off — run, and INCONCLUSIVE on this hardware

### The two candidates cannot coexist in one APK — established from the artifacts, not guessed

```
com.google.mlkit:text-recognition:16.0.1                              (bundled model)
com.google.android.gms:play-services-mlkit-text-recognition:19.0.1    (Play Services model)
```

Both ship `com.google.mlkit.vision.text.latin.TextRecognizerOptions`. They are two *implementations
of one API*, deliberately interchangeable and mutually exclusive — so a side-by-side, both-engines-in-
one-test-APK comparison is not buildable. This is why the brief authorises a sequential dependency
swap, and that is what was done.

`OcrEngineBakeOffTest` identifies the linked engine at runtime from an artifact-specific marker class
rather than from a build flag, so a printed table can never be mis-attributed to the wrong engine.

### Run 1 — bundled (baseline, as shipped)

```
engine: BUNDLED (com.google.mlkit:text-recognition)
fixture                                  outcome            ms     elements
sondey_multilingual_100g.jpg             Conf 61.9/100_G    6659   506   OK
kinder_multicolumn_piece.jpg             Conf 53.5/100_G    5343   367   OK
real_yoghurt_serving_column_07.jpg       Conf 5/100_G       2166   173   OK
real_stokbrood_prose_dense_06.jpg        Conf 46/100_G      1822   138   OK
real_witte_kaas_single_column_05.jpg     NotFound           1354    89
real_grated_cheese_multicolumn_02.jpg    Conf 2.09/100_G    1248    80
real_juice_bilingual_per100ml_01.jpg     NotFound           1069    32
real_jar_prose_multilingual_03.jpg       NotFound           1468    93
real_lid_prose_curved_04.jpg             NotFound           3046   326
correct (parser-level, whole asset): 4/9
```

### Run 2 — Play Services model, same corpus, same emulator

```
engine: PLAY-SERVICES (com.google.android.gms:play-services-mlkit-text-recognition)
every fixture: UNAVAILABLE(MlKitException), 66–154 ms, 0 elements
correct: 0/9
```

**This is not a quality result and must not be reported as one.** The Play Services model is
delivered on demand and the `carbscan` AVD is a plain `sdk_gphone64` image without Play Services, so
the model can never arrive. The ~100 ms failures confirm delivery failure rather than recognition —
a real recognition of these images takes 1–7 seconds.

**What it does establish**, which is operationally useful: adopting the Play Services model would
make the scanner **completely non-functional on any device without Play Services**, and would need
explicit model-availability handling and a first-run download path before it could ship. The brief
(§12) anticipated exactly this.

**Verdict: the bundled model stays.** Not because it was measured better — it was not measured
against a working alternative at all — but because the alternative is unevaluated on available
hardware and carries a hard availability dependency the bundled model does not. Re-running run 2 on a
Play-Store-enabled AVD or a physical phone is the open item; the harness is in place and needs only
the dependency swap documented in `OcrEngineBakeOffTest`'s KDoc.

The `mlkit-text-gms` alias is left in `libs.versions.toml`, referenced by nothing, so that re-running
the swap is a one-line change. It does not affect what ships.

---

## Rejected / do not repeat

- **Choosing a single "best" crop tightness.** Measured across 4 insets × 6 fixtures: none is safe.
  Non-monotonic behaviour (stokbrood) means the failure is not "too tight" or "too loose" — it is
  per-label. Do not tune this constant.
- **Treating a native-resolution crop as inherently safer than a rescaled one.** Measured false: with
  no resize whatsoever, three canaries still fall to `NotFound` at the production rectangle.
