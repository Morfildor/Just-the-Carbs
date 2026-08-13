# CarbQuick — Master Requirements Brief

This is the owner's original development brief, preserved verbatim in structure as the
authoritative requirements document. Sections are referenced elsewhere as §N.

> Status note: §44 (regulatory release gate) is **unresolved** and blocks publication.
> The owner has chosen to build to the stricter standard until the assessment is complete.

---

## §1 Product purpose

Calculate the amount of carbohydrate in the portion of packaged food the user is about to consume.

1. Scan food barcode
2. Retrieve carbohydrate content from product data
3. User enters how many grams or millilitres they will consume
4. App calculates carbohydrate grams in that portion
5. Display the result extremely clearly

Example: 48.2 g carb/100 g, portion 65 g → 48.2 × 65 / 100 = 31.33 g.
Main output `31 g`; supporting `31.3 g calculated`.

Useful to people who need an accurate carbohydrate value **before** using a separate bolus
calculator. **This application itself must NOT calculate medication or insulin.**

## §2 Absolute product scope

**NOT a diet app.** No calorie tracking, calorie goals, weight loss, body weight, macros dashboard,
fat/protein/sugar tracking, exercise, food diary, meal plans, daily totals, weekly charts, streaks,
achievements, social features, recipes, wellness scores, or AI dietary advice.

**NOT an insulin calculator.** No insulin dose, insulin-to-carb ratio, correction factor, glucose
entry, glucose targets, insulin-on-board, active insulin, pump communication, CGM communication,
CamAPS API integration, treatment recommendations, medication recommendations, or automatic bolus
calculations.

Core interaction: **SCAN → PORTION → CARBS**. For a known product: **RECENT → PORTION → CARBS**.
Every feature must make one of those workflows faster, safer, clearer or more reliable.

## §3 Design quality

First-class requirement. Must not look like a tutorial project, generic Material sample, CRUD app,
MyFitnessPal, Yazio, or a diabetes dashboard. Premium, purpose-built utility: extremely clean, calm,
modern, minimal, fast; excellent typography, generous spacing, clear hierarchy, one-handed
usability, minimal cognitive load, no clutter, no unnecessary screens.

The two numbers that matter are **portion** and **calculated carbohydrates** — they dominate.
Critically review every screen and iterate rather than accepting the first functional layout.

## §4 Platform

Native Android only: Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit Barcode Scanning, ML Kit
Text Recognition, Room, Retrofit/OkHttp, Coroutines, Flow/StateFlow, ViewModel, Gradle Kotlin DSL.

Stable production dependencies only — no alpha/experimental without strong reason. Verify current
official documentation before implementing. Use the Play-required target API (expect Android 16 /
API 36; verify). Modern minSdk with reasonable coverage. Optimize for modern phones. Explicitly
consider Samsung Galaxy behaviour.

## §5 Branding architecture

Working name **CarbQuick**. Centralize branding so app name, application ID, namespace, icon, accent
identity and store listing name can be changed easily. Do not scatter branding strings through
source. Do not use CamAPS, Ypsomed, Libre, Abbott or other trademarks in the name. Do not imply
official affiliation.

## §6 Application startup

Extremely fast. No decorative splash delay; use the required system splash appropriately. Reach
useful functionality immediately.

Home: header **CarbQuick**; primary large **Scan barcode**; below, **Recent**; secondary **Enter
manually**. Favourites appear at the start of Recent rather than a separate tab. Avoid bottom
navigation unless testing proves it helps. Prefer one primary surface.

## §7 Home screen UX

Compact product cards containing only: product image if available, product name, last portion, last
calculated carbohydrate value, favourite icon. No irrelevant nutrition information. Tapping a recent
product opens the calculator immediately. No network call before showing locally cached data.

## §8 Barcode scanner

CameraX + ML Kit. Support at minimum EAN-13/GTIN-13, EAN-8/GTIN-8, UPC-A, UPC-E. Optimize for
European supermarket products.

UX: full camera preview, subtle scan frame, flashlight toggle, close/back, optional manual barcode
entry, minimal text. On detection: debounce duplicates, validate, subtle haptic, stop further scan
callbacks, begin resolution immediately. Continuous detection — never require photographing.

## §9 Camera permissions

Only **CAMERA** and **INTERNET**. Do NOT request contacts, location, phone state, SMS, Health
Connect, Bluetooth, storage, or broad photo/video access. If image import is added later, use the
Photo Picker. Provide a concise contextual explanation before requesting. If denied:
*"Camera access is needed to scan a barcode."* with **Allow camera** and **Enter manually**.
Manual functionality must always remain available.

## §10 Product lookup order

1. **Locally user-verified data** — use immediately, never silently overwrite.
2. **Locally cached online data** — display immediately; optional background refresh must not delay
   calculation.
3. **Remote provider** — Open Food Facts initially.
4. **Fallback** — scan nutrition label, or enter manually.

The user must never hit a dead end.

## §11 Data provider architecture

Do not couple UI to Open Food Facts. Create a `ProductDataSource` abstraction with
`LocalProductDataSource` and `OpenFoodFactsDataSource`, designed so future providers (GS1,
Netherlands product data, retailer-authorized) can be added without a rewrite.

**Do NOT scrape supermarket websites** (Albert Heijn, Jumbo, Lidl, Aldi, PLUS, Dirk, Picnic, or
others). Legitimate APIs/data sources only.

## §12 Open Food Facts

Before implementing: read current official API docs, determine the recommended product-read API,
follow User-Agent requirements and rate limits, review licensing/attribution, request only necessary
fields.

Useful data: barcode, product name, localized name, brand, quantity, serving quantity, nutriments,
carbohydrates per 100, product image, nutrition image, countries, nutrition measurement basis.

The critical data point is **total carbohydrate** (Dutch: *Koolhydraten*). Do NOT substitute sugars,
fibre, net carbs, energy or protein. Prefer Dutch/localized naming when sensible, falling back to
the general product name.

## §13 Remote data validation

Never trust an API value blindly. Reject negative values, NaN, infinity, malformed numbers,
impossible unit combinations, empty nutrition basis, and anything not safely interpretable. Do not
invent missing data. If confidence is insufficient: **"Carbohydrate value unavailable"** with
**Scan label** / **Enter manually**. Correct failure beats incorrect calculation.

## §14 Product screen

The central screen; majority of UI/UX attention.

Structure: back · product name · favourite star; small thumbnail; **48.2 g carbs / 100 g**; source
status (*Online value* or *✓ Verified by you*); **How much are you eating?**; large **[ 65 ] g**;
quick adjustment −10 −5 +5 +10; if reliable package size exists, ½ pack / Full pack; then a
persistent result area: CARBOHYDRATES, **31 g**, *31.3 g calculated*.

The final result must visually dominate.

## §15 Terminology

Short wording: **Carbohydrates** or **Carbs**. Avoid medical jargon. Supports a workflow where the
result is later entered into a separate bolus calculator, but the software does not calculate
insulin and must not imply the result is an insulin recommendation.

## §16 Portion entry

Exceptionally fast. Large numeric input, decimal support, numeric keyboard, automatic live
calculation, easy replacement of the current value, select-existing-number where appropriate,
correct IME behaviour, keyboard must not cover the result, result stays visible where possible.
Quick buttons −10 −5 +5 +10. Light haptics if beneficial. **No Save or Calculate button** — the
result updates immediately.

## §17 Calculation engine

Separate all calculation logic from UI.

- per-100-g: `carbohydrates = carbsPer100 × portionGrams / 100`
- per-100-ml: `carbohydrates = carbsPer100ml × portionMl / 100`

**Never assume 1 ml = 1 g** unless a product-specific density is explicitly available and
intentionally supported. Use appropriate decimal arithmetic; do not accumulate floating-point
display errors.

Test: 48.2 × 65 / 100 = 31.33 → display 31.3 g; 52 × 30 / 100 = 15.6 → rounded 16 g;
4.8 × 250 / 100 = 12 → 12.0 g or 12 g per UX rules. Never round internally before completion.

## §18 Result rounding

Show the accurate decimal result plus a visually prominent whole-gram convenience value
(**31 g** / *31.3 g calculated*). Mathematically correct nearest-whole rounding. Settings may allow
*whole gram + decimal detail* or *decimal only*. Avoid floor/ceiling modes without demonstrated need.

## §19 Copy result

Allow explicit copying of the calculated value. Copy **only the number** (`31`, not `31 g carbs`).
Subtle confirmation: **31 copied**. Never automatically open or control another application.

## §20 Last portion memory

Remember the last portion per product; pre-fill it on next use; make replacing it extremely easy.
Store locally only.

## §21 Recents

Local recently used products sorted by `lastUsedAt` descending, limited sensibly on home.
Item shows name, `65 g → 31 g`. Tap opens the calculator immediately. **No meal logging** — recents
exist strictly for speed.

## §22 Favorites

Star frequently used products; favourites float above other recents. No complicated management
screen. Long press / overflow may offer favourite-unfavourite, remove from recents, edit product.
Keep interactions predictable.

## §23 User verification

Critical reliability feature — remote databases may be wrong or outdated.

Remote products display **Online value** with **Verify label**. The user can verify product name,
carbohydrates per 100, g/ml basis, package quantity, and serving information. After confirmation:
**✓ Verified by you**. Store the verified value, timestamp, source, and the original online value.

**User-verified data ALWAYS takes priority over remote data; sync must NEVER silently replace it.**
Provide **Reset to online value** behind an overflow/edit menu.

## §24 Age of verification

Store the verification date. Architecture should allow a subtle future reminder that formulations
change. No annoying recurring prompts. If a staleness indicator is shown, keep it unobtrusive
(e.g. *Verified 1 year ago · Recheck label*). **Never block calculation because verification is old.**

## §25 Unverified online data UX

Make the source visible without alarming the user, e.g. `48.2 g / 100 g` ·
**Online value · Check package if needed**. Avoid implying community data has been medically
validated.

## §26 Unknown barcode

If no usable remote product is found: **Product not found**, immediately offering
**[ Scan nutrition label ]** and **[ Enter manually ]**. Retain the barcode; any manually created
product becomes associated with it, so the next scan resolves locally.

## §27 Manual entry

Keep extremely small. Required: product name, carbohydrates per 100, basis (100 g / 100 ml).
Optional: package quantity. Pre-fill the barcode when arriving from an unknown scan. Do NOT ask for
irrelevant nutrition data.

## §28 Quick manual calculator

Calculate without saving a product: carbs/100 g, portion, result; optional **Save product**. Useful
for damaged/missing barcodes. Secondary to barcode scanning.

## §29 Nutrition label OCR

ML Kit Text Recognition, on device where possible. Recognize at minimum Dutch (*Koolhydraten*,
*per 100 g*, *per 100 ml*) and English (*Carbohydrate(s)*, *per 100 g/ml*). Architect parsing so
more languages can be added.

**OCR output must NEVER be silently accepted.** Show e.g. *Detected — Koolhydraten 47.3 g / 100 g*
with **[ Use 47.3 ]** and **[ Edit ]**. If multiple plausible rows exist, show candidates or request
confirmation. If ambiguous: *"Couldn't confidently identify the carbohydrate value."* plus manual
entry. **Never guess.**

## §30 OCR image privacy

Do not upload label images to a custom server. Do not persist images unless necessary. Prefer
temporary in-memory/local processing, discarded afterwards. No broad storage permission.

## §31 Product images

If OFF provides an image: small useful thumbnail, cached, loaded asynchronously. Image availability
must NEVER delay the calculator, calculation or barcode workflow. Fully usable without images.

## §32 Offline support

Degrade gracefully. Fully functional offline for barcode recognition, cached products, verified
products, manually created products, recents, favourites, calculations, and OCR where ML Kit config
permits. Unknown barcode offline: **No internet connection** with **[ Scan label ]** /
**[ Enter manually ]**. **No infinite loaders.**

## §33 Room database

Robust local schema. A product may contain: barcode, productName, brand, carbsPer100,
nutritionBasis, packageAmount, packageUnit, servingAmount, imageUrl, source, originalRemoteCarbs,
userVerified, verifiedAt, remoteUpdatedAt, lastUsedAt, lastPortion, favorite. Improve the model if
architecture warrants. Implement migrations properly. **Never use destructive migration in a
production release for convenience.**

## §34 Local privacy

No login, account, cloud profile, advertising ID, analytics SDK, advertising SDK, telemetry SDK,
Health Connect, or third-party tracking. Use Play Android Vitals rather than adding crash SDKs
initially. Keep products, favourites, portions, verification status and recents on device. Review
Android backup behaviour; prefer preventing local usage history from being uploaded, or configure
backup/extraction rules explicitly. **Document the decision.**

## §35 Network security

HTTPS only; no cleartext traffic. Sensible timeouts. Do not log remote responses containing
unnecessary user-related information in release builds. No secrets in source. OFF requires no secret
key for ordinary reads. Never commit future credentials.

## §36 Error handling

Explicitly handle: no camera permission, camera unavailable, flashlight unavailable, duplicate
scans, malformed barcode, unsupported barcode, internet unavailable, DNS/network failure, timeout,
HTTP error, rate limiting, malformed JSON, product missing, carbohydrate missing, invalid
carbohydrate value, ambiguous basis, OCR failure, OCR ambiguity, database error, invalid manual
input. Concise, actionable errors. **Never expose stack traces.**

## §37 Loading states

Polished brief loading state for remote lookup. No full-screen spinner when cached information
exists. Provide cancellation/back navigation if lookup is slow. Prioritize perceived speed.

## §38 Design system

Small internal design system: spacing, typography, card style, corner radius, icon sizes, touch
targets, result typography, motion durations. Use Material 3 intelligently rather than accepting
defaults. Support system/light/dark themes. Dynamic color only if hierarchy stays excellent.

## §39 Accessibility

TalkBack semantics, accessible labels, adequate contrast, minimum touch targets, font scaling,
keyboard navigation where applicable. Test with enlarged system font — large carbohydrate values
must not clip. Do not rely on colour alone for status.

## §40 One-handed use

Assume the user is standing in a kitchen holding food in the other hand. Primary controls reachable
one-handed, placed toward the middle/lower screen. Avoid tiny top-right actions for essential
workflow. Keep the result visible near the bottom during portion editing where possible.

## §41 Haptics

Restrained: successful barcode scan, optionally quick portion controls. No vibration on ordinary
navigation. Settings toggle: **Haptic feedback**.

## §42 Internationalization

No hardcoded visible strings; use string resources. Initial UI languages English and Dutch (English
default). Numeric parsing/display must respect locale while keeping internal decimal handling
unambiguous. Architect cleanly for more languages.

## §43 Settings

Intentionally small. **Appearance**: System/Light/Dark. **Results**: whole gram + decimal detail, or
decimal. **Interaction**: haptic feedback on/off. **Data**: clear recent history, clear locally saved
products (confirmation required). **About**: version, data source attribution, privacy policy,
safety/regulatory information, open-source notices. No feature creep.

## §44 Regulatory release gate

**Extremely important.** Do NOT assume the app is or is not a medical device — status depends on
final intended purpose, claims, functionality, markets and applicable law.

Before production publication create `docs/regulatory-release-checklist.md`, flagging that the owner
must perform and document a regulatory qualification assessment. Evaluate the intended purpose under
current EU MDR 2017/745, current MDCG software qualification/classification guidance, applicable
Google Play health-app requirements, and other markets if distributed outside the EU.

The app performs carbohydrate calculations and does NOT calculate insulin. However, because it may
be positioned for use before a separate bolus calculator, **do not use a disclaimer to evade a
qualification assessment**. If assessment concludes it qualifies as a medical device or accessory,
STOP treating it as a wellness app and identify the additional compliance work required.

**Do not fabricate regulatory certification. Do not add a CE mark. Do not state medical approval
without evidence.**

## §45 Medical claim boundary

Do not claim the app improves glucose control, prevents hypo/hyperglycaemia, improves HbA1c, makes
insulin dosing safer, provides clinically accurate doses, replaces medical advice, replaces a food
label, or has medical-device approval. Do not invent clinical claims. Copy must accurately describe
what the software does.

## §46 Google Play health policy

Review the CURRENT Google Play Health Content and Services policy. Determine applicable declarations
and prepare `docs/play-health-declaration.md` containing relevant policy categories, proposed
declaration answers, explanation for each, permissions used, data accessed, data transmitted, and a
regulatory status field requiring owner confirmation. **Do NOT falsely declare the app non-medical
if qualification is incomplete. Do NOT submit anything automatically.**

## §47 Google Play account requirements

Note in the release checklist that the owner must verify whether an **Organization** Play Console
account is required for the final health-app category. Do not assume a personal account suffices.

## §48 Privacy policy

Create `docs/privacy-policy.html` and `docs/privacy-policy.md`, accurately describing the
implementation: no account, no advertising, no analytics SDK, no sale of data, local product
history/favourites/verified values, camera usage, barcode processing, OCR processing, OFF product
requests, what is transmitted during lookup, storage and deletion, third-party data-provider
relationship, and a contact placeholder for the owner.

**Do not falsely say "no data leaves the device"** — barcode lookup requires a network request.
Clearly distinguish **local user data** from **remote product lookup requests**. The HTML must suit
static hosting; the Play privacy-policy URL must eventually be publicly accessible.

## §49 Data safety preparation

Create `docs/google-play-data-safety.md`. **Review actual code first.** Document proposed answers to
the Data Safety form based on the real app and its dependencies — no generic answers. Identify
uncertainty requiring developer confirmation.

## §50 Required health disclaimer

Do not invent wording. Inspect Google's current Health Content and Services policy. If the app falls
into the category requiring the non-medical-device disclaimer, include Google's required language in
the store description plus any required professional-advice statement. If determined to be a
regulated medical device, use the regulated pathway instead. **Conditional on the §44 decision.**

## §51 No false affiliation

Public copy must not imply development or endorsement by CamDiab, Ypsomed, CamAPS FX, Abbott or
Libre, or integration with those products, without actual authorization. Avoid third-party
trademarks in the title and promotional graphics.

## §52 Google Play technical readiness

Correct target SDK, release build variant, versionCode, versionName, Android App Bundle, adaptive
launcher icon, monochrome icon where appropriate, edge-to-edge layout, proper app label, no debug
flags in release, R8/minification review, resource shrinking where safe, secure release config.
Generate a debug APK, and a release AAB if proper signing material is available. **Do not use a
debug signing key for a production release.**

## §53 Signing security

**Do NOT commit keystores, passwords, signing secrets or credentials.** Configure production signing
via local/environment variables. If no upload key exists: explain how to create one securely,
configure the project to reference it outside source control, and build everything possible without
pretending a production-signed bundle exists. Add signing files to `.gitignore`. Document the Play
App Signing / upload-key workflow.

## §54 Store listing package

Create `docs/play-store-listing.md` with app title, short description and full description within
current Play limits. Tone: precise, simple, factual; no exaggerated or medical efficacy claims; no
keyword stuffing. Describe barcode scanning, portion calculation, local product memory, label
verification, offline saved products. **Not a diet tracker. No implied medical approval.** Keep
wording consistent with the §44 assessment.

## §55 Store asset plan

Create `docs/store-assets.md` specifying current required Play assets and dimensions after checking
official documentation: launcher icon, feature graphic, phone screenshots, promotional copy,
screenshot sequence. Screenshot story: scan a barcode → enter portion → see result → verify package
value → reuse recent products. Do not clutter screenshots with marketing claims.

## §56 App icon

Professional adaptive icon using vector/XML assets. Concept should communicate food/product,
calculation and speed. **Avoid** syringe, blood drop, glucose meter, insulin pump, medical cross,
calorie flame, bathroom scale. Recognizable at small sizes, simple geometric concept, easily
replaceable branding.

## §57 Open-source and data attribution

Review licensing of every dependency and data provider. Create `docs/third-party-notices.md`
including required Open Food Facts attribution per their current terms. Comply with any additional
product-image licensing. **Do not guess licensing requirements — read the current official terms.**

## §58 Security review

Before completion check exported components, manifest permissions, deep links, network config,
WebView usage, file providers, secrets, logs, backups, dependency vulnerabilities where tooling
allows, release debuggability, clipboard behaviour, and database handling. Do not introduce WebView
unless genuinely needed.

## §59 Quality testing

Comprehensive unit tests, at minimum:

- **Calculation** — normal, decimal, zero, large portion, grams, millilitres, rounding, locale-safe
  input, negative rejection, malformed values.
- **Repository** — verified local value wins; cached value used offline; remote fetched when
  necessary; remote cannot overwrite verified value; unknown product; missing carbohydrate value;
  malformed API response.
- **Product history** — recent sorting, favourite, last portion, manual products, verified timestamp.
- **OCR parsing** — Dutch *Koolhydraten*, English *Carbohydrate*, 100 g, 100 ml, ambiguous multiple
  values, parsing failure.

## §60 UI testing

Meaningful Compose/UI tests for critical workflows where practical:

1. Home → Scan result → Product → Portion → Result
2. Recent → Product → Change portion → Result
3. Unknown product → Manual entry → Calculator
4. Remote value → Verify → Save → reopen
5. Offline cached product
6. Camera permission denied → Manual entry

## §61 Manual QA checklist

Create `docs/manual-qa.md` covering fresh install, denied camera, permission later granted, no
internet, poor internet, dark mode, light mode, large font, rotation, background/resume, process
killed, scanner reopening, rapid repeat scan, flashlight, decimal input, Dutch decimal separator,
screen reader, and Samsung device behaviour.

## §62 Performance

Quick cold startup, quick scanner start, immediate cached-product opening, no network work blocking
UI, smooth portion updates, no visible calculation latency, stable recomposition, appropriately
sized images. Avoid premature architecture complexity.

## §63 State restoration

Handle process recreation correctly. Restore sensible state if the process is killed mid-edit. Do
not duplicate remote requests. Use proper ViewModel/SavedState handling.

## §64 CI

Simple GitHub Actions workflow: compile, unit tests, lint on PRs/pushes. No signing secrets in the
repository. Release signing only when explicitly configured with repository secrets later. Keep CI
maintainable.

## §65 Code quality

Clear naming, small composables, reasonable ViewModels, repository boundaries, immutable UI state,
StateFlow, structured concurrency, DI only if beneficial. **Do not overengineer** — avoid a complex
clean-architecture framework for a small app. Another senior Android developer should understand it
quickly.

## §66 README

Professional README covering purpose, screenshot placeholders, primary workflow, architecture, tech
stack, project structure, calculation logic, OFF integration, local verification, barcode scanner,
OCR, offline behaviour, privacy architecture, build instructions, testing, debug APK, release AAB
instructions, signing, Play preparation, regulatory release gate, and known limitations.

## §67 Known limitations

Create `docs/known-limitations.md` documenting: public food databases can be incorrect; products may
change composition while retaining a barcode; OCR can misread labels; packaging may use unusual
serving/basis conventions; internet is required for first lookup; the app does not calculate insulin;
it does not communicate with pumps/CGMs; regulatory status must be finalized before public release.
**Do not hide limitations.**

## §68 Development process

1. Inspect environment · 2. Check existing project · 3. Verify Android/Play technical requirements ·
4. Verify ML Kit barcode docs · 5. Verify ML Kit OCR docs · 6. Verify OFF API and licensing ·
7. Design data model · 8. Design screen flow · 9. Calculation engine · 10. Room · 11. Repository ·
12. OFF provider · 13. Scanner · 14. Home/recent UI · 15. Calculator UI · 16. Verification ·
17. Manual entry · 18. OCR · 19. Offline/error handling · 20. Settings/About · 21. Localization ·
22. Polish UI · 23. Accessibility review · 24. Privacy/security review · 25. Write tests ·
26. Run tests · 27. Run lint · 28. Build debug APK · 29. Configure release build · 30. Build release
AAB where signing permits · 31. Play publication documents · 32. Regulatory checklist review ·
33. Final UX critique · 34. Fix remaining issues · 35. Repeat tests/build.

**Do not stop when code merely compiles.**

## §69 UX critique pass

Simulate: user standing in a kitchen, food in one hand, phone in the other, wanting the carbohydrate
amount as fast as possible. Ask critically — how many taps? how much typing? did the keyboard behave?
is the scanner fast? can they read the result instantly? is irrelevant information competing? can
they easily correct bad product data? is an error recoverable? can a frequent product be calculated
faster? is it usable one-handed? Fix any obvious friction before completion.

## §70 Primary acceptance test

- **New product** — open app → scan → product loads → type `65` → immediately see **31 g**
- **Known product** — open app → tap recent → previous portion appears → type new portion →
  immediate result
- **Unknown product** — scan → not found → scan label or enter carbs/100 → type portion → result

No workflow should feel like a diet tracker.

## §71 Safety acceptance test

The app must never calculate or recommend insulin, modify a pump, interpret glucose, imply food
database values are guaranteed correct, automatically accept uncertain OCR, silently replace
verified data, invent missing values, present itself as officially affiliated with another medical
product, or make unsupported medical claims.

## §72 Final build requirements

Run unit tests, feasible UI tests, Android lint, debug build, and release bundle build where
possible. Fix actual errors. **Do not suppress problems to produce a green build** unless the
suppression is technically justified and documented.

## §73 Final deliverables

**Application** — complete project, production-quality source, debug APK, release AAB if signing
permits, icon/resources. **Tests** — unit tests, relevant UI tests, manual QA checklist.
**Documentation** — README.md, privacy-policy.md, privacy-policy.html, google-play-data-safety.md,
play-health-declaration.md, play-store-listing.md, store-assets.md, regulatory-release-checklist.md,
third-party-notices.md, known-limitations.md, manual-qa.md.

**Final report** — what was implemented; test results; lint result; APK path; AAB path if generated;
dependency/API decisions; remaining limitations; steps requiring human action before publication;
regulatory questions requiring owner decision.

**Do not claim publication readiness if a required release gate remains unresolved.**

## §74 Final product principle

Protect the simplicity of the product. The user should never need to think about the application.
They should simply **scan → weigh → read the carbs**. Everything else exists only to make those
three actions faster, safer and more pleasant. If a proposed feature does not directly improve that
workflow, leave it out.
