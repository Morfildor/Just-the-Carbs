# Architecture and behavior safeguards

Paths below are relative to `app/src/main/kotlin/app/justthecarbs/`.

## Boundaries

- `domain/`: pure Kotlin calculations, validation, portion conversion, and store interfaces. No Android imports.
- `data/local/`: Room entities, DAOs, migrations, and local persistence. `data/remote/`: provider APIs and mapping. `data/settings/`: DataStore preferences.
- `ProductRepository` owns barcode lookup priority and persistence behavior. Preserve verified local data first, cached remote data next, network lookup next, then useful manual/label fallbacks. Text search has a separate provider chain; do not apply barcode lookup rules to search.
- `ocr/`: keep interpretation and evidence rules testable independently from ML Kit and camera integration.
- `ui/`: Compose screens and ViewModels exposing immutable state through StateFlow. Reuse existing shared components and application wiring.
- `AppContainer` in `JustTheCarbsApplication.kt` manually wires shared dependencies. Extend that composition root rather than introducing a dependency-injection framework or extra architecture layers for routine features.
- Dependency versions live in `gradle/libs.versions.toml`. AGP uses built-in Kotlin; do not add `org.jetbrains.kotlin.android`.

## Numbers and user data

- Use `BigDecimal`; divide per-100 calculations with `movePointLeft(2)`. Round only for display, deriving decimal and whole-gram displays independently from the exact result with the established HALF_UP rule.
- Never interconvert grams and millilitres: the app has no density model. A nutrition basis is a label, not a conversion factor; keep portions locked to the product's basis.
- Resolve weight-based counts through `PortionResolver` into the existing calculator. Preserve the explicit direct-carb conversion path without fabricating a weight or per-100 value.
- Keep `dataSource` separate from `verificationStatus`. Protect user-authored and user-verified values from remote replacement.
- An open calculation uses a stable snapshot. Background refresh may offer an update; applying it requires user acceptance, including updates to the selected portion unit.
- Store exact nutrition and portion values as SQLite TEXT, not REAL. Schema changes require a preserving migration, exported schemas in `app/schemas/`, and instrumented migration tests. Do not use destructive migration fallback.
- The meal is a current scratchpad, not a dated history. Preserve that product boundary.

## Scanner changes

- Read [OCR evidence provenance](../ocr-evidence-provenance-design.md) and the current scanner-specific sections in `CLAUDE.md` before changing capture sequencing or automatic acceptance.
- Freeze live evidence at the start of a committed shutter capture, before starting new work, pausing analysis, autofocus, or still capture. Keep aim epoch and work generation distinct.
- Multiple parses/crops of one photograph are one physical observation, not independent corroboration. Preserve observation IDs and conservative eligibility rules.
- Do not pick the first ambiguous value, silently substitute sugars for total carbs, or relax confidence rules to make one fixture pass. Preserve explicit confirmation and recovery paths.
- Test real-photo recognition as well as pure parser behavior. Use the committed sanitized fixtures; keep full-frame originals and debug evidence local. See [fixture policy](../../app/src/androidTest/assets/ocr_real/README.md).
- Keep diagnostic capture/export debug-only and preserve the release stripping checks.

## Networking and UI

- Text search uses `ProductSearchSource`: cached Search-a-licious primary, then governed legacy search only for eligible failures. Cache the primary provider, not the whole fallback chain, to preserve provenance. Barcode lookup stays on the canonical Open Food Facts product API; screens and ViewModels must not select providers themselves.
- Retrofit and Coil share the configured OkHttp client. Preserve identifying requests, provider rate limiting, cache behavior, and the HTTPS image-host allowlist.
- Do not remove ML Kit's transitive telemetry transport on the assumption that it is optional; the project records scanner crashes from that exclusion. Keep privacy disclosures consistent with shipped dependencies.
- Use theme tokens and existing contrast/accent tests. The carbohydrate result remains visually dominant; the default theme is Light, with System and Dark available.
- Before modifying onboarding or scan haptics, read the current owner decisions in `CLAUDE.md`: the tutorial is offered rather than opened automatically, and haptic cues must not imply that a nutrition value is trustworthy.
