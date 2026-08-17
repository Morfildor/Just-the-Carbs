# Deriving the real-image OCR fixtures

Records how the nine committed fixtures under `app/src/androidTest/assets/ocr_real/` were produced
from the seven full-frame source photographs the owner placed at `Test labels/` (git-ignored, never
committed) plus the two pre-existing fixtures (`sondey_multilingual_100g.jpg`,
`kinder_multicolumn_piece.jpg`) that were already cropped-ish and are left untouched — only the
`.gitignore` policy around them changed.

Tooling: Python 3 + Pillow 12.3.0 (`python -m pip install Pillow`). ImageMagick is not installed;
`convert` on PATH is the unrelated Windows filesystem tool. All processing was crop-only (plus the
mandatory EXIF-bake described below) — no deskew, no rotation correction beyond the EXIF tag, no
sharpening, no contrast/brightness normalization, no denoise, no upscaling, no colour change.

## Source-mapping correction (read this before trusting file names elsewhere)

The controller context handed off for this task mapped `Test labels/7.jpg` to the yoghurt photo and
`Test labels/20260816_193636.jpg` to the stokbrood photo. That mapping was **wrong** — the two rows
were swapped. Verified empirically (direct visual inspection, repeated across independent reads,
cross-checked with MD5 hashes and raw pixel sampling to rule out a display-caching artifact):

- `Test labels/7.jpg` (raw 1999x1125) is the **AH stokbrood** photo (dense multi-paragraph prose
  label), not yoghurt.
- `Test labels/20260816_193636.jpg` (raw 2418x1362) is the **AH yoghurt** photo (serving-column +
  %RI table), not stokbrood.

The table below uses the corrected mapping. This correction is also recorded in the SDD progress
ledger at `.superpowers/sdd/2026-08-16-real-image-ocr-generalization/progress.md`.

## Source originals (never modified)

All seven are Samsung Galaxy S24 Ultra JPEGs. `PIL.Image.getexif()` reports EXIF orientation tag
**6** (rotate 90° clockwise to view upright) on every one of them, confirmed by direct inspection —
not assumed from the controller note. Files were only ever opened read-only; nothing was written
back to `Test labels/`.

| File | Raw pixel dimensions (as stored, sensor orientation) | EXIF orientation | Byte size | Package |
|---|---|---|---|---|
| `Test labels/1.jpg` | 2670x1503 | 6 | 1,071,901 | Orange juice, NL/DE, per 100 ml |
| `Test labels/2.jpg` | 2139x1204 | 6 | 1,080,746 | Lidl grated cheese, 2 columns + %RI |
| `Test labels/3.jpg` | 2239x1261 | 6 | 1,086,294 | Ozener 500 g jar, multilingual prose |
| `Test labels/4.jpg` | 2450x1379 | 6 | 1,167,046 | Bel cheese lid 125 g, curved prose ring |
| `Test labels/6.jpg` | 2187x1231 | 6 | 928,778 | Melkan Witte Kaas 45+ 200 g, single column |
| `Test labels/7.jpg` | 1999x1125 | 6 | 795,521 | AH stokbrood, dense multi-paragraph prose |
| `Test labels/20260816_193636.jpg` | 2418x1362 | 6 | 1,165,349 | AH yoghurt, serving column + %RI |

## Orientation handling (binding ruling, applied)

Production honours EXIF (`StillImageLoader.applyExifRotation`, applied before cropping), but the
androidTest harness's `BitmapFactory.decodeStream` does **not** apply EXIF. To make the committed
fixture match what production actually feeds ML Kit, the EXIF rotation was baked into the pixels
before cropping, and the saved fixture carries **no** EXIF orientation tag (verified: `getexif()`
returns no orientation key on every committed file). Pipeline per file:

1. `PIL.Image.open(source)`
2. `PIL.ImageOps.exif_transpose(image)` — physically rotates pixels per the orientation tag,
   producing an upright portrait image with no residual orientation metadata
3. `.crop((left, top, right, bottom))` in the now-upright pixel space
4. `.save(path, quality=95, subsampling=0)`

Each fixture was re-opened after saving and visually confirmed upright with text running
left-to-right horizontally (direct inspection of the rendered crop, not inferred).

## Crops produced

Crop rectangles are `(left, top, right, bottom)` in the **upright** (post-`exif_transpose`) pixel
space, i.e. after step 2 above, before any further rotation.

| Source | Upright size (post-transpose) | Crop rectangle | Cropped size | Committed fixture |
|---|---|---|---|---|
| `1.jpg` | 1503x2670 | (0, 420, 1503, 1230) | 1503x810 | `real_juice_bilingual_per100ml_01.jpg` |
| `2.jpg` | 1204x2139 | (0, 600, 1204, 1650) | 1204x1050 | `real_grated_cheese_multicolumn_02.jpg` |
| `3.jpg` | 1261x2239 | (0, 880, 1261, 1900) | 1261x1020 | `real_jar_prose_multilingual_03.jpg` |
| `4.jpg` | 1379x2450 | (0, 280, 1379, 1650) | 1379x1370 | `real_lid_prose_curved_04.jpg` |
| `6.jpg` | 1231x2187 | (0, 520, 1231, 1780) | 1231x1260 | `real_witte_kaas_single_column_05.jpg` |
| `7.jpg` | 1125x1999 | (0, 280, 1125, 1600) | 1125x1320 | `real_stokbrood_prose_dense_06.jpg` |
| `20260816_193636.jpg` | 1362x2418 | (0, 380, 1362, 1660) | 1362x1280 | `real_yoghurt_serving_column_07.jpg` |

What each crop keeps, and why:

- **Photo 1 (juice)**: full "Voedingswaarde / Nährwerte" header through the per-100ml table,
  including the statiegeldfles logo for realistic clutter. Hands/background/most of the bottle body
  removed.
- **Photo 2 (cheese)**: LIDL logo through the full two-column (`ø/100 g` / `ø/portie 50 g`) + %RI
  table, **and** the "Deze verpakking bevat 5 porties van 50 g." line below the table (serving-count
  context required by the controller ruling).
- **Photo 3 (jar prose)**: the full multilingual sentence block (7 languages) from "Voedingswaarde
  per..." through the barcode/500 g footer. Jar rim/lid and background fingers removed.
  Hand/thumb visible at photo edges is unavoidable given the source composition; no attempt made to
  paint it out (would violate crop-only).
- **Photo 4 (lid, curved)**: the **entire printed ring** of text kept (per controller ruling — the
  curvature is the variable under test). Cropped to the label circle plus a small margin, background
  table surface removed.
  Serving/nutrition figures appear once, in the trailing "Pour/Per/Pro 100g: ... Glucides/
  Koolhydraten/Kohlenhydrate/Carbohydrate/Kolhydrat/Kulhydrat: 3g. dont sucres/... : 2,5g" sentence —
  this photo has no separate tabular per-100 block, it is prose throughout the ring.
- **Photo 6 (Witte Kaas)**: product name "WITTE KAAS 45+" through the full ingredient+table block,
  down to "200 g e". Barcode/date line excluded (not needed to interpret the nutrition figures).
- **Photo 7 (stokbrood)**: "AH L&P STKBR / DES ROAST S" header through the dense ingredient list and
  "Voedingswaarde per 100g: ..." prose sentence, down to "Consumeren op de dag van aankoop."
  (renamed to fixture `..._06.jpg` per the corrected package↔fixture-number mapping).
- **Photo 20260816_193636 (yoghurt)**: kept the **left-edge fragment** ("...HURT", "10% VET",
  "...ortie (150 g)") together with the full `Voedingswaarde per 100g / schaaltje (150g)` table and
  the full `%RI` block through "8400 kJ / 2000 kcal per dag." (controller ruling: keep the `%RI`
  block and the `schaaltje (150 g)` header). Renamed to fixture `..._07.jpg`.

## Existing fixtures left untouched

`sondey_multilingual_100g.jpg` (900x1600) and `kinder_multicolumn_piece.jpg` (900x1600) were **not**
re-cropped, re-encoded, or otherwise modified — copied byte-for-byte from their prior location. Only
the `.gitignore` policy around the `ocr_real/` directory changed, so these two become tracked instead
of git-ignored.
