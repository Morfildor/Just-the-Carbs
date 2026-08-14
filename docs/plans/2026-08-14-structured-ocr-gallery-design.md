# Structured nutrition OCR and product gallery design

Date: 2026-08-14

## Scope

This pass replaces flattened nutrition-label parsing with deterministic spatial parsing and adds a
modal product-image gallery. It preserves the current calculation, persistence, lookup, and
calculation-session behavior. It adds no health interpretation, meal-photo estimation, or release
work.

## OCR architecture

ML Kit remains an Android boundary. `LabelAnalyzer` converts recognized blocks, lines, and elements
into a pure Kotlin `OcrDocument`. Each `OcrElement` retains its text, bounding box, block id, and line
id. `NutritionTableParser` consumes only these pure objects, so its behavior stays JVM-testable.

The parser runs explicit stages:

1. Normalize element text without discarding geometry.
2. Find total-carbohydrate anchors using one centralized multilingual dictionary.
3. Reject sugar, fibre, starch, and polyol anchors before numeric scoring.
4. Find per-100 g/ml and serving headers, then derive their horizontal column regions.
5. Find numeric elements aligned with each carbohydrate row.
6. Score row overlap, vertical distance, column alignment, unit evidence, and basis evidence using
   centralized weights and thresholds.
7. Return one of three outcomes:
   - a confident per-100 g/ml result when both row and basis evidence pass;
   - explicit candidates when the carbohydrate row is trustworthy but the basis or value remains
     ambiguous;
   - `NotFound` when the evidence is insufficient.

Every candidate records its evidence, score, geometry, and rejection reason. Equal or near-equal
plausible interpretations never resolve automatically.

## Language data

The terminology table contains total-carbohydrate anchors and exclusions for English, Dutch,
German, French, Spanish, Italian, Portuguese, Turkish, Polish, Danish, Swedish, Norwegian, Finnish,
Czech, and Romanian. Language data is independent of the scoring engine and covered by table-driven
tests. Exclusion rows can never earn total-carbohydrate anchor points.

## Camera and still capture

CameraX keeps `STRATEGY_KEEP_ONLY_LATEST` and one analyzer thread. The analyzer also enforces a
single in-flight ML Kit task. A `ResolutionSelector` prefers analysis around 1280x720 and accepts the
closest supported resolution without excluding compatible devices. Debug diagnostics record the
actual `ImageProxy` dimensions and OCR latency.

The scanner binds `ImageCapture` beside preview and analysis. **Capture label** writes only to a
temporary cache file, runs the same ML Kit-to-`OcrDocument` conversion and parser, and deletes the
file in `finally`. No capture enters MediaStore or leaves the device. Torch state uses the bound
camera's `CameraControl` and reports unavailable hardware without a fake control.

## OCR diagnostics

Debug builds log analyzer dimensions, latency, recognized elements, carbohydrate anchors, headers,
candidate values and geometry, scores, selections, rejections, and reasons. Release builds omit the
diagnostic calls behind `BuildConfig.DEBUG` checks.

## Product images and persistence

The OFF DTO reads `selected_images` for `front`, `nutrition`, `ingredients`, and `packaging`, using
each category's 400 px `display` map. Mapping selects the device/app language first, then the product
language, English, and finally a deterministic available language. It emits at most one image per
category, avoiding duplicate language variants.

Every URL passes through `ProductImageUrlValidator`. Raw DTOs remain in `data.remote`; the domain
uses `ProductImage(type, language, displayUrl)`. Gallery metadata is cached through an additive Room
migration. No image field participates in carbohydrate calculations or session refresh decisions.

## Gallery interaction

Job: inspect available package imagery without leaving or blocking the calculator.

Scene: a user holds a package near the phone, often under uneven kitchen lighting, and checks fine
print before trusting a carbohydrate value.

Register and dials: Material product UI; visual variance 3/10, motion 3/10, information density
4/10. Existing color, spacing, radius, typography, and motion tokens remain authoritative.

The existing hero opens a modal dialog only when safe images exist. The dialog provides horizontal
swipe, explicit previous/next controls, close, Android Back, page count, type label, optional
language, and `ContentScale.Fit`. One image still opens without arrows. A failed or slow image leaves
the calculator untouched and shows a stable in-dialog loading/error state. Adjacent pages may be
preloaded through the existing shared Coil/OkHttp path. No gallery request blocks calculation.

Gallery states: closed, loading, one image, multiple images, page transition, cached offline,
load failure, Android Back/close, dark mode, large text, and no images. The no-image state keeps the
current hero fallback and exposes no gallery action.

## Test boundaries

Parser invariants belong to the pure JVM OCR suite: row identification, language terminology,
geometry, column basis, scoring thresholds, ambiguity, exclusions, and invalid values. DTO/domain
image mapping and URL rejection also belong to JVM tests.

Instrumented tests cover behavior that pure tests cannot: the hero's modal affordance, one/multiple
image controls, Back/close, failure state, calculator availability under image load, scanner state
transitions, torch availability, and the temporary still-capture flow where practical. The final
gate remains a real emulator pass for both scanner and gallery surfaces.

