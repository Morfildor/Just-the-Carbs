# Real package photographs for OCR integration tests

`RealImageOcrTest` (and the measurement-only `RealImageBaselineTest`) run the **complete** pipeline —
photo → ML Kit text recognition → line/element segmentation → `MlKitOcrMapper` →
`NutritionTableParser` — against actual photographs of actual packages.

This is the gap every other OCR test in this repo leaves open. `OcrDocument` fixtures begin *after*
recognition has already succeeded, so they can only test the parser's reaction to text somebody
typed into a test file. They cannot test what ML Kit actually returns for a photograph: how it
splits a multilingual row, whether it reads `61,9` as one token or three, which boxes it merges. A
suite of 400+ green fixtures coexisted with a nutrition scanner that failed on real packaging,
which is exactly the failure mode this directory exists to close.

## The nine fixtures — committed and mandatory

These images are **committed** to the repository (2026-08-16) and are **mandatory**: a missing
fixture makes the corresponding test **fail**, not skip. That is a deliberate reversal of the
directory's earlier policy — a green run on an incomplete fixture set must never be mistaken for
evidence that the real labels work, and a silently-skipped high-value regression test is exactly the
failure mode a public CI run must not have.

| File | Package | Expected total carbohydrate |
|---|---|---|
| `real_juice_bilingual_per100ml_01.jpg` | Orange juice, NL/DE label, per 100 ml | `9,0 g` per 100 ml |
| `real_grated_cheese_multicolumn_02.jpg` | Lidl grated cheese, 2 columns (per 100g / per portie 50g) + %RI | `2,0 g` per 100 g / `1,0 g` per 50 g portion |
| `real_jar_prose_multilingual_03.jpg` | Ozener 500 g jar, 8-language prose label | `1,6 g` per 100 g (Kulhydrat/Koolhydrate/Carbohydrate) |
| `real_lid_prose_curved_04.jpg` | Bel cheese lid, 125 g, curved prose ring (6 languages) | `3 g` per 100 g (Glucides/Koolhydraten/Kohlenhydrate/Carbohydrate/Kolhydrat/Kulhydrat), of which sugars `2,5 g` |
| `real_witte_kaas_single_column_05.jpg` | Melkan Witte Kaas 45+, 200 g, single Dutch column | `2,3 g` per 100 g (waarvan suikers `2,3 g`) |
| `real_stokbrood_prose_dense_06.jpg` | Albert Heijn stokbrood, dense Dutch ingredient+nutrition prose | `46 g` per 100 g (koolhydraten; waarvan suikers `1,0 g`) |
| `real_yoghurt_serving_column_07.jpg` | Albert Heijn yoghurt, per-100g + per-schaaltje(150g) columns + %RI | `5,0 g` per 100 g / `7,5 g` per 150 g schaaltje |
| `sondey_multilingual_100g.jpg` | Sondey / Lidl biscuits, NL/FR/DE table | `61.9 g` per 100 g — never `47.6` (sugars) |
| `kinder_multicolumn_piece.jpg` | Kinder / Ferrero chocolate, per 100 g + per piece + %RI | `53.5 g` per 100 g — never `3`, `7` or `53.3`; per piece `6.7 g` |

For Kinder the test also checks the per-piece relationship where recognition supports it:
`6.7 g` per piece, `1 piece = 12.5 g`.

## Sanitization: cropping only

Every one of the seven newer fixtures is a crop of a full-frame photograph the owner took, cropped
to the nutrition panel plus whatever header/serving text is needed to interpret it (see
`tools/derive-ocr-fixtures.md` for the exact source rectangles). Sanitization was **cropping only**:
no deskew, no rotation correction beyond baking in the original EXIF orientation, no sharpening, no
contrast/brightness normalization, no denoise, no upscaling, no colour change. The tilt, glare,
curvature and JPEG compression artifacts visible in these images are the variables under test — a
laboratory-perfect scan would pass while telling us nothing. The full-frame originals
(hands, background, barcodes, unrelated packaging) never leave local disk; see
`tools/derive-ocr-fixtures.md`.

## Wanted: the Stroopwafel/Lidl label (2026-08-18)

The highest-value missing fixture. A physical-device recording shows this package reaching
*"Table in view — tap to capture"* — meaning a live frame produced a usable interpretation — and the
deliberate still capture that follows returning *"Couldn't confidently find carbohydrates."* Printed
total carbohydrate is **61.9 g / 100 g**.

The 2026-08-18 reliability pass addressed that failure **architecturally** (pre-shutter live evidence
is now retained and can be offered for verification; a failed automatic read falls through to
assisted tap-the-row rather than a dead end) but **could not measure it**, because no fixture and no
exported evidence bundle for this package exist. Until one is added, the Stroopwafel case is
reasoned about, not verified.

Adding it needs only the usual sanitization: crop to the nutrition panel plus whatever header text is
needed to interpret it, no other processing. An exported evidence bundle (`capture.jpg` + `meta.txt`)
from a failing device scan is worth even more, since `meta.txt` now records the camera's negotiated
capture resolution.

## Running

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat :app:connectedDebugAndroidTest --tests "app.justthecarbs.ocr.RealImageOcrTest"
```

## If a real image fails

Do not tune a threshold until it passes. Capture the diagnostics first — the test prints
`OcrDiagnosticsReport.render(...)` on failure, which names the stage that ran out of evidence
(recognition, row reconstruction, terminology, column resolution, numeric parsing). Fix that stage,
with a synthetic regression test pinning the specific geometry that was wrong.
