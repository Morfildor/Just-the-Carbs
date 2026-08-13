# Store assets — CarbScan

Asset plan for the Play listing (§55).

> ⚠️ **Play's asset requirements change.** Every dimension below must be re-verified against the
> current Play Console requirements before upload. They are recorded here as the plan, not as
> authority.

---

## Launcher icon — done

Already implemented as vector XML, no raster export needed:

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

| Asset | Spec (verify) | Status |
|---|---|---|
| Store icon | 512 × 512 PNG, 32-bit | ☐ |
| Feature graphic | 1024 × 500 PNG/JPEG, no alpha | ☐ |
| Phone screenshots | 2–8 required; 16:9 or 9:16; min 320px, max 3840px on any side | ☐ |
| Tablet screenshots | Only if tablet support is declared | ☐ |
| Promo video | Optional — recommended to skip | ☐ |

## Screenshot sequence

Five screenshots that tell the workflow story in order. Capture on a real device, in **light mode**,
at default font size, with realistic Dutch supermarket products.

| # | Screen | Shows | Caption |
|---|---|---|---|
| 1 | Scanner | Live preview, subtle frame | *Scan the barcode* |
| 2 | Calculator, portion typed | `48.2 g carbs / 100 g`, portion `65 g` | *Enter your portion* |
| 3 | Calculator, result dominant | **31 g** with *31.3 g calculated* | *Read the carbs* |
| 4 | Verify dialog | Checking a value against the package | *Correct the data yourself* |
| 5 | Home with recents | `65 g → 31 g` rows, one starred | *Repeat products in one tap* |

**Screenshot 3 is the important one.** It is the whole product in a single image and should be the
first frame a browsing user sees.

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
