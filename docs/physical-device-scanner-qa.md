# Physical-device QA protocol — nutrition-table scanner

For the 2026-08-17 capture-first pass. Closes `docs/manual-qa.md` §15f/§15g when completed.

**Why this exists.** Every result in the automated corpus is a *stored photograph* run through the
recognizer. That proves the parser works on real optics. It proves nothing about the camera path —
focus, exposure, the 8 MP capture, memory, or whether the capture-first gating actually engages. Those
are the parts this pass changed most.

Install `JustTheCarbs-debug.apk`. Record results in the tables below and hand them back.

> **START HERE for the 2026-08-17 crop pass.** The scanner now asks you to confirm the table
> rectangle after capture. **Part 0 below is the only part that has never been seen on a device
> and is the reason this build exists.** Do it first; the older parts still apply unchanged.

---

## Part 0 — The user-confirmed table crop (NEW, do this first)

The flow is now: *Capture label* → the photo freezes → a white rectangle appears → drag/resize it
around the whole nutrition table → **Read table**.

**The one thing that must be enclosed is the basis header.** A rectangle that excludes `per 100 g`
leaves the value unplaceable and the app will refuse — deliberately, because guessing the basis is the
one substitution it must never make. If a scan fails, check the rectangle included the heading before
reporting it as a parser failure.

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 0.1 | Capture any nutrition table. | The photo freezes. A rectangle with four corner handles appears over it, plus *Read table* and *Retake*. | |
| 0.2 | Without moving anything, tap *Read table*. | A result or a clear refusal within ~1 s. **It should feel instant** — no second recognition runs. | |
| 0.3 | Capture again. Drag each of the four corners. | The rectangle resizes smoothly; corners cannot cross; it never leaves the photo. | |
| 0.4 | Drag from the middle of the rectangle. | The whole rectangle moves without resizing. | |
| 0.5 | Deliberately drag the top edge **below** the `per 100 g` heading, then *Read table*. | The app **refuses** (no confident value). This is correct behaviour, not a bug — record it as a pass. | |
| 0.6 | Tap *Retake* mid-way through adjusting. | Returns to the live camera immediately. No stale photo, no stale result. | |
| 0.7 | Capture, then immediately tap *Retake* **while it still says "Reading captured label…"**. | Returns to live camera. When the earlier recognition finishes it must **not** re-freeze the screen or show a result. | |
| 0.8 | Rotate the phone to landscape on the crop screen, if it rotates. | The rectangle stays over the same part of the photo. It must not jump or invert. | |

### The measurement that matters: Kinder and Stroopwafel

This is the reason for the whole pass. Both packages fail today on the device with the table plainly
readable.

| Package | Attempt | Rectangle drawn around | Result | Correct? |
|---|---|---|---|---|
| Kinder | 1 | whole table incl. `per 100 g` | | expect **53.5 / 100 g** |
| Kinder | 2 | tighter, just the table | | |
| Kinder | 3 | generous, table + margin | | |
| Stroopwafel | 1 | whole table incl. heading | | |
| Stroopwafel | 2 | tighter | | |
| Stroopwafel | 3 | generous | | |

**If a scan still fails with the heading clearly inside the rectangle, export the evidence** (Part 7)
and hand back the folder. `selection.txt` is new and answers the question directly: it lists the
rectangle, the elements retained, and the elements rejected as outside it. Three causes are then
distinguishable at a glance — the crop removed nothing (rectangle too generous), the crop removed the
header or value column (they appear in the rejected list), or the crop was right and the parser
refused on its own terms (`diagnostics.txt` names the stage that ran out of evidence).

---

## Part 1 — Capture-first gating actually engages (5 minutes, do this first)

The single most important behavioural change. Before this pass, a 720p live frame could answer before
you ever pressed capture.

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 1.1 | Open *Scan nutrition label*, point at any nutrition table, **do not tap anything**. Wait 15 s. | The card NEVER shows a carbohydrate value or a result. It shows only "Point at the nutrition table" or "Table in view — tap to capture". | |
| 1.2 | Same, holding steady on a clearly readable table. | Text changes to "Table in view — tap to capture". The capture button stays visible the whole time. | |
| 1.3 | Tap the capture button. | Brief "Capturing label…", then "Reading label…", then a result or a clear failure. | |
| 1.4 | On the result card, tap *Scan again*. | Returns to the live view; guidance text reappears; no stale value. | |

**If any value appears before you tap capture in 1.1, stop and report it — the gating has regressed.**

## Part 2 — Focus and exposure

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 2.1 | From ~30 cm, tap capture on a matte label. | Shutter fires within ~1.5 s; it never hangs. | |
| 2.2 | Point at a **glossy** package under a ceiling light, capture. | Exposure settles on the table rather than blowing out; shutter still fires. | |
| 2.3 | Move from far to close and immediately capture (no pause). | Still fires within ~1.5 s even though AF was mid-sweep. | |
| 2.4 | Point at a blank white surface (nothing to focus on) and capture. | **Shutter still fires** — bounded by the 1200 ms focus timeout. Must not hang. | |

## Part 3 — Repeated scans per package (the reliability number)

Use **at least 6 packages**, ideally including the ones in the corpus. For each, do **5 separate
scans**: exit to the live view between each, re-aim, and capture again. Do not keep the phone still
between attempts — the point is to measure ordinary use.

Record per package:

| Package | Printed carbs (per 100 g/ml) | Scans | Correct 1st attempt | Correct within 2 attempts | Confident-WRONG | NotFound | Notes |
|---|---|---|---|---|---|---|---|
| | | 5 | | | | | |
| | | 5 | | | | | |
| | | 5 | | | | | |
| | | 5 | | | | | |
| | | 5 | | | | | |
| | | 5 | | | | | |

**"Confident-WRONG" is the critical column.** It means the app showed a number confidently and that
number is not what the package prints. Note the wrong value AND the printed value — a wrong value that
matches the sugars/fat/salt figure is a different (worse) defect than one that is merely misread.

Totals:

- first-attempt success rate: ____ / ____
- success within two attempts: ____ / ____
- **confident-wrong: ____ (target: 0)**
- NotFound: ____

## Part 4 — Orientation and distance

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 4.1 | Capture a table held in **landscape**. | Correct value, correct basis. | |
| 4.2 | Rotate the device mid-scan, then capture. | No crash; result matches the printed value. | |
| 4.3 | Capture from ~50 cm (table small in frame). | Either correct or an honest failure — never a wrong confident number. | |
| 4.4 | Capture a **curved** container (jar/tub). | Same. | |

## Part 5 — Memory at 8 MP

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 5.1 | Capture 10 times in a row without leaving the scanner. | No crash, no progressive slowdown. | |
| 5.2 | On a mid-range/older device if available, repeat 5.1. | No `OutOfMemoryError`. A crash here is the decode path, not recognition. | |

## Part 6 — BARCODE NON-REGRESSION (frozen behaviour)

The barcode flow is explicitly frozen this pass. It binds its own camera and shares no CameraX
configuration with the label scanner, so it *should* be untouched — verify, don't assume.

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 6.1 | Scan 5 ordinary product barcodes. | All decode; timing feels unchanged. | |
| 6.2 | Raise the phone across a shelf of several products. | Does not commit to a neighbouring product; still requires a held, framed barcode. | |
| 6.3 | Scan a barcode that is not in Open Food Facts. | *Product not found* with *Scan barcode again* as the primary action. | |
| 6.4 | Compare decode speed against your memory of the previous build. | No perceptible slowdown. | |

**Any regression in Part 6 is a release blocker for this pass.**

---

## Part 7 — Capturing evidence from a failed scan (debug builds only)

**Use this whenever a scan fails on the phone but the same package works in the test suite.** That
combination means the device and the harness disagree, and only the phone's own bytes can settle it.

The debug build records the last 12 captures. For each one it keeps:

| File | What it answers |
|---|---|
| `capture.jpg` | The untouched JPEG. Was the photo sharp? Was it the resolution we asked for? |
| `passA.png` | The decoded, EXIF-corrected bitmap **actually handed to ML Kit**. Rotation or decode damage shows up here and nowhere else. |
| `recognized.txt` | ML Kit's complete output with per-element geometry. Did the recognizer see the number at all? |
| `diagnostics.txt` | Every parser stage and the refusal reason. Did the parser reject good text? |
| `meta.txt` | Dimensions, EXIF rotation, decode fallback, timing, device. |

### To collect

1. Reproduce the failure in the app.
2. **Settings → Export scan evidence** (at the bottom, debug builds only).
3. Share the zip to yourself — email, Drive, cable, whatever is convenient.
4. Hand over the zip.

Nothing is uploaded automatically and nothing leaves the phone without that explicit share.

### To replay a capture offline

```powershell
# after unzipping
adb push <unzipped>\20260817-143022-118\capture.jpg `
  /sdcard/Android/data/app.justthecarbs.debug/files/replay/kinder-device.jpg

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.DeviceCaptureReplayTest

adb logcat -d -s JustTheCarbsReplay
```

Each capture folder also contains **`selection.txt`** (added 2026-08-17) whenever a crop was
confirmed. Read it before anything else when a manually cropped table still fails — it records the
rectangle, the elements retained, the elements rejected as outside it, and the parser diagnostics
afterwards, which separates the three causes that otherwise look identical:

- **elements `N -> N`** — the crop removed nothing, so the rectangle was too generous to help.
- **the header or the value column appears in the rejected list** — the crop removed the answer.
- **the retained list looks like a clean table and the parser still refused** — a genuine parser
  limit, and `diagnostics.txt` names the stage that ran out of evidence.

The replay runs the real capture through the same `analyzeStill` the scanner calls. It splits the
question cleanly:

- **Fails in replay too** → fully reproducible offline. The camera is exonerated; the fault is in
  recognition or parsing, and it can now be fixed against a real image.
- **Passes in replay but fails in the app** → the fault is in the camera path (acquisition, focus,
  timing), *not* the parser.

### Release builds record nothing

Both guards are structural, not conventions: the recorder is removed by R8 (verified absent from
release `mapping.txt`) and the `FileProvider` is declared only in `src/debug/AndroidManifest.xml`
(verified absent from the release manifest). The shipped app still deletes each capture the moment
recognition finishes and never writes it anywhere else.

---

## Reporting

Return: the six tables, the Part 3 totals, and for every confident-wrong result the printed value, the
shown value, and a photo of the package if possible. Confident-wrong cases are the ones worth the most
— they are the only failure mode that can harm someone.

For any failure that does not reproduce in the suite, attach the Part 7 evidence zip.


---

## Part 8 — Protocol after the 2026-08-17 autonomous pass

Two things changed that affect what to test, and one earlier conclusion was **wrong**.

### Correction: the "900x1600 capture" was not a phone capture

An evidence line reading `capture.jpg = 900x1600` was taken as proof that CameraX negotiated a 1.4 MP
still. It is the **Kinder fixture's own dimensions** — that meta came from replaying a committed
fixture, not from hardware. `ImageCapture` requests `Size(3264, 2448)` with
`FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` and `CAPTURE_MODE_MAXIMIZE_QUALITY`.

**The negotiated resolution on real hardware is still unmeasured.** The emulator's virtual camera
cannot answer it. This is now the single highest-value line in a real `meta.txt`.

### What to capture, in priority order

The most informative exports are **2-3 failed Kinder and 2-3 failed Stroopwafel scans at different
distances**. Vary only the distance; keep focus, lighting and angle good. The distance series is what
discriminates between the two known failure causes, which behave oppositely:

- **too small** — fails at long range, recovers as you approach;
- **surrounding-text interference** — fails at *close* range, where the package prose around the table
  is best recognised and competes hardest. Measured: kinder and yoghurt both fail at their largest
  rendering.

A series that fails at every distance means neither, and points at the camera path.

### The three questions each export answers

Read `meta.txt` first:

1. `captured JPEG` — if this is not close to 3264x2448 on a modern phone, CameraX is not honouring the
   request and that is the top defect, ahead of anything in the parser.
2. `pass A bitmap` vs `captured JPEG` — a large discrepancy means decode or rotation is damaging it.
3. `pass A (locate)` and `total` — latency, and whether recognition ran at all.

Then look at `passA.png`. If it is soft, the parser was never the problem.

### Replaying them

Batch replay now exists and takes a whole unzipped export:

```
adb push <unzipped>/. /sdcard/Android/data/app.justthecarbs.debug/files/replay/
```

Name a file or its folder with `~<printed value>` — `kinder-close~53.5.jpg` — and the report marks each
result CORRECT or WRONG and prints a confident-wrong summary at the end. Then:

```
adb shell am instrument -w -e class app.justthecarbs.ocr.DeviceCaptureReplayTest   app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s JustTheCarbsReplay
```

**The split remains the point.** A capture that fails in replay is fully reproducible offline and the
camera is exonerated. A capture that *passes* in replay while failing in the app puts the fault in the
camera path — acquisition, focus, or timing — and not in the parser.

### New: the framing hint

The scanner now says **"Move closer to the nutrition table"** when recognized text is below the
calibrated size. It is advisory, never gates the shutter, and cannot produce a value. Worth noting on
a physical device whether it appears at sensible distances — it was calibrated on synthetic composites,
not on real optics, and that is exactly the kind of threshold that can be wrong in the hand.
