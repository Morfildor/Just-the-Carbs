# Capture-first nutrition scanning — design (2026-08-17)

Evidence base: `docs/plans/2026-08-17-nutrition-scanner-audit.md`. Every decision below traces to a
measurement in that document, not to a plausible story about recognition quality.

**The problem is camera and cropping architecture, not a weak carbohydrate parser.** The parser
correctly refused incomplete evidence; it was handed truncated images.

---

## The core change

The visible scan rectangle stops being a destructive OCR boundary and becomes framing guidance plus
relevance information.

```
BEFORE  capture -> decode -> rotate -> CROP(overlay+12%) -> ML Kit -> parse
                                       ^ deletes the basis header band on tall labels,
                                         unconditionally, before recognition ever runs

AFTER   capture -> decode -> rotate -> ML Kit(FULL image) -> parse
                                          ^ Pass A sees everything
                             scan rect -> ranks relevance AFTER recognition
```

Measured justification (audit Finding 6): the crop removes the header band, `ColumnClassifier` then
reclassifies the per-100 column as `REFERENCE_PERCENT`, and the interpreter correctly refuses a value
it cannot place. Sondey's diagnostics say it literally: `rejected: 61.9: REFERENCE_PERCENT column`.

## Why not simply widen the margin

Rejected on evidence. The header's offset varies per package — sondey prints it at the top of a tall
multilingual panel, witte kaas has no per-100 header in frame at all. A fixed margin is a guess that
is wrong on some package in a way nobody can see on screen, which is the same class of defect as the
pre-`ViewPort` crop arithmetic. **The header's position is observable after recognition; before
recognition it is only guessable.** So recognise first.

## The honest trade-off

Cropping genuinely *helped* one fixture: witte kaas went NotFound -> Confident 2.3 because the crop
removed prose that was defeating row reconstruction. Full-image Pass A gives that benefit up.

This is recorded, not hidden. The recovery mechanism is the relevance filter: the scan rect carries
the same "which part of the package matters" information the crop did, applied **after** recognition
where it cannot destroy evidence. If witte kaas does not recover, it is reported as a measured
regression and becomes a priority-5 target for targeted re-reading — not a reason to reinstate a
destructive crop that costs two canaries.

## Scan rect as relevance, precisely

`NormalizedRegion` is retained and still measured from the overlay's real laid-out bounds. Its use
changes:

- **Not** used to crop the bitmap before recognition.
- Used to compute, per reconstructed row, whether it overlaps what the user framed.
- Applied only to break ties among otherwise equally-supported candidates, and to suppress rows
  wholly outside the frame when a framed candidate exists.

It may never promote a candidate the parser refused, and never supplies a basis. It is a filter over
candidates the existing rules already accepted, so no safety rule is weakened by it.

## Capture-first gating

Audit Finding 1: `LaunchedEffect(reading) { if (reading != null) analyzer.pause() }` latches the UI on
the first live 720p frame that yields any reading, and `SearchingCard` — the only composable holding
the primary capture button — is only rendered while `reading == null`. The user frequently never
reaches the 8 MP path.

After: live analysis drives **framing guidance only** and can never produce the authoritative reading.
A result card is reachable only from a still capture. Live `LabelReading` values are used to indicate
readiness, not to answer.

## Focus and metering

Audit Finding 4: no `FocusMeteringAction` anywhere in the tree. Capture fires on whatever AF state
existed. Added: focus/metering on the scan region before capture, AF+AE+AWB, with a bounded timeout so
the shutter never hangs — capture proceeds on timeout rather than blocking.

## Harness

Audit Finding 2: the golden suite recognises whole assets via `InputImage.fromBitmap(asset, 0)` and
never touches `StillImageLoader`, `ScanRegionMapper`, or `analyzeStill`. It measures parser-given-
recognition and cannot see the camera pipeline.

The real-image harness moves onto the production still path so that every number it reports is a
number about the shipped feature.

## Sequencing

Priorities 1-4 (crop, gating, focus, harness) are implemented and **measured** before the
evidence-fusion layer (priority 5) is designed. Building fusion on an unmeasured baseline is how a
large unvalidated surface area gets added to a safety-critical path.

## Safety invariants — unchanged by this design

No parser safety rule is loosened. Specifically preserved: `CandidateProvenance`; the unconditional
child-nutrient exclusion; the usable-basis-column guard; declaration boundaries; the merged-table
prose rejection; forbidden-value assertions. The canaries failed because of a **truncated input**,
which is evidence the safety rules are working and must not be relaxed to compensate.

Barcode scanning is untouched. `ScannerScreen` binds its own `ImageAnalysis` with no `ImageCapture`
and no `UseCaseGroup`, sharing no CameraX configuration with the label scanner.
