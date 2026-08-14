# Just the Carbs — visual redesign & rebrand

Design doc for implementing `docs/design_handoff_just_the_carbs/` in the native Android app.

## Goal

Replace the app's current near-monochrome Material 3 look with the "Just the Carbs" visual
identity (bright pastel trio over cream, Space Grotesk + Roboto, bolder shape language), and
rebrand the app's public identity to "Just the Carbs". This is a **visual and branding** change.
No feature is added or removed except onboarding, which the design doc specifies as a new screen
that does not exist in the app today.

Source of truth for the visual spec: `docs/design_handoff_just_the_carbs/README.md` and the HTML
mockups in `docs/design_handoff_just_the_carbs/screens/`. The mockups are simplified prototypes
(hardcoded fake data, only the happy path) — the app's actual behavior (countable portions, usual
portions, label verification, remote-changed notices, provenance badges, error/failure states,
search) is unchanged and gets the new visual language extended to it consistently, even though the
mockups don't depict those states.

## Rebrand scope

- `branding.gradle.kts`: `brandAppName` → "Just the Carbs", `brandApplicationId` and
  `brandNamespace` → a new id (e.g. `app.justthecarbs`). This changes the Play Store / install
  identity. No production installs exist yet, so orphaned local data on upgrade is not a concern.
- The **Kotlin package** (`app.carbscan.*`, ~105 files) is explicitly NOT renamed. `brandNamespace`
  only affects the generated `BuildConfig`/manifest namespace at build time; Kotlin source keeps
  its existing `package app.carbscan...` declarations. This keeps the large mechanical rename out
  of this visual-work branch entirely, per `branding.gradle.kts`'s own documented rebrand path
  (change the extras, nothing else needs editing).
- `app_name` string resource, wordmark text, and the OFF User-Agent (which reads `brandAppName`)
  follow automatically once the extra is changed.
- GitHub repo name, docs prose (which CLAUDE.md says stays "CarbQuick"-labeled until the owner
  decides) are **not** touched by this pass — CLAUDE.md's existing note about the undecided public
  name is superseded by this session's explicit instruction to rename, but repo/doc renaming is a
  separate, unrelated cleanup not requested here.

## Design tokens

New tokens added to `ui/theme/Theme.kt`, replacing the current teal-based scheme:

- **Colors**: blue `#2F8FE0` (primary interactive), blue-soft `#E4F1FC`, red `#FF5C5C` (reserved
  for the carbohydrate result number only), orange `#FFA94D` / orange-soft `#FFEEDC`
  (informational/badges), cream `#FFF6EE` (background), ink `#181A1E` / ink-muted `#6B6A72`,
  neutral line `#E4DFD3`, disabled blue `#DCE8F5`.
- Mapped onto `ColorScheme`: `primary`/`onPrimary` = blue/white, `primaryContainer` = blue-soft,
  `background`/`surface` = cream, `onBackground`/`onSurface` = ink, `onSurfaceVariant` = ink-muted,
  `outline`/`outlineVariant` = neutral line, `tertiary`/`tertiaryContainer` = orange/orange-soft
  (badges, safety card).
- The result red is **not** mapped to Material's `error` role (semantically wrong — it isn't an
  error state). It's added as a standalone token, e.g. `CarbScanColors.result`, read directly by
  the result/meal-total composables the same way `NumberType` is read today.
- Dark mode: no dark spec exists in the handoff. Built by extrapolation using the same token
  roles — cream inverts to a near-black background, ink inverts to an off-white foreground, the
  blue/red/orange accents are kept close to their light values (brightened only as needed to hold
  contrast on a dark ground), following the same method the current `DarkColors` uses relative to
  `LightColors`.
- **Typography**: Space Grotesk (500/600/700) for display/UI — headlines, numbers, buttons,
  labels; Roboto (400/500/600) for body/supporting text and form input. Bundled as font files
  under `app/src/main/res/font/` (Google Fonts is a CDN dependency in the mockups; the app needs
  them offline-bundled, same as it already bundles ML Kit models). `NumberType.result`/`.portion`
  sizes and letter-spacing updated to the doc's values (64sp/-2sp result, 48sp/-1.5sp portion,
  keeping the existing `resultAutoSize` shrink-to-fit safety mechanism unchanged since it's an
  overflow-safety property, not a visual one).
- **Shape**: card radius 18–22dp, button radius 14–18dp, chip/pill radius 999dp (full), bottom
  sheet top corners 32dp — replacing the current tightened 14/12dp scale. `Space` object's radius
  fields updated in place; call sites are unaffected since they reference the named constants.
- **Motion**: existing `Motion.QUICK_MS`/`STANDARD_MS` and the result "pop" animation stay
  structurally as-is (they already implement the doc's animation shape); only visual parameters
  that need to change (easing curve, if Compose's default doesn't already match
  `cubic-bezier(.2,0,0,1)`) are adjusted.

## Component strategy

Material 3 components stay in place (`OutlinedTextField`, `FilterChip`, `Switch`, `AlertDialog`,
`DropdownMenu`, `Button`/`OutlinedButton`/`TextButton`) and are rethemed via the color scheme,
shapes, and typography above — not replaced with hand-built equivalents. The mockup's custom pill
toggles and switch are achievable via M3 `FilterChip`/`Switch` theming closely enough that a bespoke
rebuild isn't warranted, and keeps existing accessibility/IME/RTL behavior intact for free.

## Screens

All 6 existing screens are restyled in place — same composable structure, same ViewModels, same
navigation, same test tags — only colors/type/shape/spacing change:

1. **Home** (`HomeScreen.kt`) — wordmark in Space Grotesk 700, blue-soft decorative circle, meal
   banner restyled to blue-soft pill, recent cards restyled (18dp radius, blue-soft monogram tile),
   primary CTA becomes the full blue button with glow shadow, secondary becomes text-only blue.
2. **Scanner** (`ScannerScreen.kt`) — frame/corner-bracket color and scan-line become violet/blue
   per the doc, frosted circular icon chips, manual-entry pill button restyled. Camera preview,
   permission flow, torch logic, manual barcode dialog unchanged.
3. **Product/Result** (`ProductScreen.kt`) — the big one. Hero image area, per-100 summary +
   source badge (orange "ONLINE" pill), portion input restyled (Space Grotesk 48sp), quick-adjust
   pills, pack shortcuts, portion-mode row (grams/countable-unit chips), usual-portions row,
   add-portion-unit form, remote-changed notices, verify/correct-portion-unit UI, meal bar,
   and the pinned result panel (red 64sp result, supporting line, Add to meal / Scan next buttons)
   — all restyled to the doc's language. Every existing state (loading, failure/recovery panel,
   label verification dialog, product gallery dialog) gets the same token treatment even though
   only the happy path appears in `result.html`.
4. **Manual entry** (`ManualEntryScreen.kt`) — field styling (56dp white rows, soft shadow),
   measured-per pill toggle, save button (blue enabled / `#DCE8F5` disabled).
5. **Settings** (`SettingsScreen.kt`) — pill selectors for theme, switch for haptics, orange-tinted
   safety card per the doc's verbatim copy (already matches current string resources).
6. **Meal** (`MealScreen.kt`) — item rows, pinned total sheet with red total figure, "Clear"/"Done"
   restyled.

## New: Onboarding

Net-new screen and flow, since none exists today.

- `ui/onboarding/OnboardingScreen.kt`: 3-slide carousel matching `onboarding.html` — full-screen
  slide with decorative circles, icon mark, eyebrow/headline/body, dot pagination, CTA button
  ("Next" → "Get started" on the last slide), "Skip" jumps to the last slide. Background/text
  cross-fade between slides (280ms).
- `ui/onboarding/OnboardingViewModel.kt`: trivial — current slide index, advance/skip, and a
  `completeOnboarding()` that persists the seen flag.
- `AppSettings` domain model gets one new field: `hasSeenOnboarding: Boolean` (default false),
  persisted through the existing DataStore-backed `SettingsRepository` (new key, same pattern as
  `hapticsEnabled`/`resultStyle`/`theme`).
- `CarbScanNavHost`: new `Routes.ONBOARDING` as the conditional start destination — shown when
  `!settings.hasSeenOnboarding`, otherwise `Routes.HOME`. Completing or skipping onboarding calls
  the persist function and navigates to Home with `popUpTo(Routes.ONBOARDING) { inclusive = true }`
  so back doesn't return to it.
- Icon mark (the "sliced rounded square revealing a core circle" glyph): implemented as a Compose
  vector (`ImageVector` built with `Icons` builder DSL, or a static vector drawable), tinted per
  slide via `tint`. New to this redesign per the doc — treated as a placeholder mark, not wired up
  as the launcher icon (that stays out of scope; the doc calls it "pending sign-off").

## What is explicitly unchanged

- All ViewModels' logic, state classes, and public function signatures.
- Navigation graph shape (routes/args) apart from the added Onboarding entry.
- Domain layer, Room schema, repository logic.
- Existing test tags (`PRODUCT_RESULT_TAG`, `PORTION_CORRECTION_FIELD_TAG`,
  `ADD_PORTION_UNIT_FIELD_TAG`, `USUAL_PORTION_ROW_TAG`, etc.) — instrumented and unit tests keep
  passing against the same handles.
- String resource *keys* — only values change where the doc specifies new copy (e.g. onboarding
  strings, which are new keys since the screen is new). Existing screens' copy is unchanged; the
  doc's copy for those screens matches or near-matches what's already there.

## Testing

- `testDebugUnitTest` (237 tests) must stay green — no domain/ViewModel logic changes.
- `connectedDebugAndroidTest` (95 instrumented tests) run against the emulator after the restyle,
  since Compose UI tests can be sensitive to structural changes even when only visuals change
  (e.g. a color-only change shouldn't break `performClick`/`assertExists`, but layout/shape changes
  that shift hit-test regions could).
- `lintDebug` stays clean.
- Manual walkthrough on the emulator against all 7 screens (6 restyled + onboarding), light and
  dark, comparing against the mockups.
- Debug APK built and copied to Desktop per the project's existing workflow.

## Open items for the owner (not blocking this implementation)

- The icon mark is explicitly a placeholder per the design doc ("pending sign-off, not a final
  logo") — not treated as final branding.
- Dark mode palette is this implementation's extrapolation, not an owner-approved spec.
- The applicationId change means this build is no longer upgrade-compatible with any prior
  `app.carbscan`-id installs on a test device; a fresh install replaces rather than upgrades.
