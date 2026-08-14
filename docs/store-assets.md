# Store assets — Just the Carbs

Asset plan for the Play listing (§55).

Requirements re-checked on 2026-08-14 against Google's
[preview asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

---

## Launcher icon — done

The adaptive launcher icon is implemented as vector XML, but Google Play still requires a separate
512×512 PNG upload:

| File | Role |
|---|---|
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon |
| `res/drawable/ic_launcher_foreground.xml` | Foreground (package + scan beam) |
| `res/drawable/ic_launcher_background.xml` | Background (solid teal) |
| `<monochrome>` layer | Themed icons on Android 13+ |

**Concept (§56):** a package crossed by a scan beam — product, reading it, speed. Two shapes only,
so it stays legible at 48dp in a launcher folder.

Deliberately **not** a syringe, blood drop, glucose meter, pump, medical cross, calorie flame, or
bathroom scale. The app is a calculator for packaged food and the icon must not suggest otherwise.

☐ Export the 512×512 32-bit PNG store icon from the same vectors

## Store assets to produce

| Asset | Current specification | Status |
|---|---|---|
| Store icon | 512 × 512, 32-bit PNG with alpha, ≤1,024 KB | **MISSING** |
| Feature graphic | 1024 × 500 JPEG or 24-bit PNG, no alpha | **MISSING** |
| Phone screenshots | Produce at least 2; JPEG/24-bit PNG, no alpha; each side 320–3,840 px; long side ≤2× short side | **MISSING** |
| Tablet screenshots | Not a universal publication minimum; use real tablet captures if tablet quality/distribution is claimed | **OPEN owner scope decision** |
| Promo video | Optional — recommended to skip | **SKIP for first release** |

## Screenshot sequence

Five screenshots that tell the workflow story in order. Capture on a real device, in **light mode**,
at default font size, with realistic Dutch supermarket products.

| # | Screen | Shows | Caption |
|---|---|---|---|
| 1 | Scanner | Live preview, subtle frame | *Scan the barcode* |
| 2 | Calculator, portion typed | `48.2 g carbs / 100 g`, portion `65 g` | *Enter your portion* |
| 3 | Calculator, result dominant | **31.3 g** with *≈ 31 g whole grams* beneath | *Read the carbs* |
| 4 | Verify dialog | Checking a value against the package | *Correct the data yourself* |
| 5 | Home with recents | `65 g → 31 g` rows, one starred | *Repeat products in one tap* |

**Screenshot 3 is the important one.** It is the whole product in a single image and should be the
first frame a browsing user sees. Decimal (`31.3 g`) is the dominant figure, not the whole gram —
match whatever `ResultStyle` the capture device is actually set to (default: decimal-dominant).

**Optional 6th screenshot (countable portions, 2026-08-14):** the calculator with **Slices**
selected, a count of `2`, and the `2 slices × 36 g = 72 g` supporting line visible above the
result — *No scale needed for a slice of bread*. Not required for launch; a natural addition once
real screenshots are captured, since it is the newest and most visually distinct capability.

### Screenshot rules

- **No marketing text overlays on the UI.** Let the interface speak; §55 warns against clutter.
- Captions belong in the caption field, not painted onto the image.
- No claim of accuracy, safety, or medical benefit in any frame (§45).
- No third-party product branding prominent enough to imply endorsement (§51) — prefer
  own-brand/generic packaging.
- Use real values that are arithmetically correct. A screenshot showing a wrong calculation would be
  both embarrassing and, for this product, a credibility problem.
- Dark-mode variants optional; if included, keep the same sequence.

## Feature graphic

Plain: the wordmark on the teal background, with the icon mark. No screenshot collage, no
superlatives, no medical imagery, no numbers that could read as a health claim.

## Assets that must NOT appear anywhere

- CE mark, or any regulatory or certification symbol (§44)
- Any suggestion of clinical validation or medical approval
- CamAPS, Ypsomed, Abbott, Libre, or any medical device trademark (§51)
- Insulin pens, pumps, syringes, glucose meters, or blood imagery

## Localisation

English is the primary listing. A Dutch listing is worthwhile given the target market — the app
already ships Dutch strings. Translate title, short and full descriptions, and screenshot captions;
screenshots can be re-captured with the device set to Dutch.

☐ Dutch listing — *(owner decides)*
