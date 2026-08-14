# Security review — Just the Carbs

Written against the actual code and build configuration, not a generic checklist. Where a claim
depends on something only observable at runtime (the merged manifest, a minified build), the
command used to check it is included so it can be re-verified after any change.

**Status: internal review. Not an independent/third-party audit** (see [Third-party dependencies
and independent review](#third-party-dependencies-and-independent-review) below).

---

## Permissions

| Permission | Declared by | Purpose |
|---|---|---|
| `CAMERA` | Just the Carbs | Barcode and nutrition-label scanning |
| `INTERNET` | Just the Carbs | Open Food Facts product-data and product-image requests |
| `ACCESS_NETWORK_STATE` | Transitive, `com.google.android.datatransport` via ML Kit | Not used by Just the Carbs code; see [google-play-data-safety.md](google-play-data-safety.md) |

No storage, photo-library, contacts, location, Bluetooth, phone-state, or SMS permission exists
anywhere in the merged manifest (checked via
`app/build/intermediates/merged_manifest/debug/AndroidManifest.xml`). The app cannot see the
device's photo gallery, contacts, or location even if it wanted to — the capability is absent, not
merely unused.

## Exported components

The manifest declares exactly one component: `MainActivity`, `exported="true"` with a
`LAUNCHER`/`MAIN` intent filter — required for any activity the user can open from the home
screen, and it accepts no external data (no deep-link `<data>` scheme, no custom intent action
beyond `MAIN`). No service, broadcast receiver, or content provider is declared by the app itself.

Libraries merge their own manifest entries (ML Kit, AndroidX, CameraX); none of these introduce an
app-declared exported component. `android:exported` was not required to be set explicitly on any
component beyond `MainActivity` because none of the others exist.

## Cleartext networking

`android:usesCleartextTraffic="false"` is set at the application level in the manifest. Both
network destinations (`world.openfoodfacts.org` for product data, `images.openfoodfacts.org` /
`static.openfoodfacts.org` for product photos) are accessed over HTTPS only — enforced at the
platform level, not just by convention in the request-building code. No `network_security_config.xml`
exists because none is needed: there is no cleartext exception to declare for any domain.

## Remote image URL validation and gallery (updated 2026-08-14)

A corrupt or malicious Open Food Facts product record could in principle carry an `image_url`
pointing anywhere. Before this pass, that URL went straight to Coil with only a blank-string check.
`ProductImageUrlValidator` (`domain/ProductImageUrlValidator.kt`) now gates every image URL:

- Scheme must be exactly `https` (case-insensitive) — plain `http` is rejected even on an
  otherwise-approved host.
- Host must be exactly `images.openfoodfacts.org` or `static.openfoodfacts.org` — checked against
  the parsed `URI.host`, not a substring/prefix match, so `images.openfoodfacts.org.evil.com` is
  rejected (pinned by a test: `rejects a host that merely contains the approved host as a
  substring`).
- Anything that fails to parse as a URI, or is null/blank, is rejected the same way a missing image
  already was — falling back to the monogram tile, never a broken state.

The v3 `selected_images` display URLs for Front, Nutrition, Ingredients, and Packaging are validated
at the remote mapping boundary. They are validated again when decoded from Room and again by
`ProductImageSelector` before the hero or gallery reaches Coil. Language selection keeps one image
per role and duplicate URLs are removed; adding a gallery therefore does not widen the host or
scheme allowlist.

**What this does not cover:** the validator only constrains which *host* an image request can
reach — it does not, and cannot, verify that the image content itself is safe or non-malicious.
That risk is the same one accepted by displaying any remote image and is bounded by Coil/the
platform's own image decoding, not by this validator.

## Shared network stack

Retrofit (product-data requests) and Coil (product-image requests) now build on **one**
`OkHttpClient` instance — `AppContainer.okHttpClient`, constructed once (`by lazy`) and passed to
both `NetworkModule.openFoodFactsApi(client)` and Coil's `OkHttpNetworkFetcherFactory`. Before this
pass they were two separately-constructed clients with matching configuration, which is not the
same guarantee: two clients don't share a connection pool, and a config change applied to one could
silently miss the other. A single shared instance means the identifying User-Agent, connect/read/
call timeouts, and connection-pool behaviour apply uniformly to every request the app makes,
by construction rather than by convention.

The User-Agent interceptor (`NetworkModule.userAgent`) is applied to this one shared client, so it
reaches both the product-data host and the image host. Both are Open Food Facts–operated hosts, so
this is not a case of leaking an app-identifying header to an unrelated third party.

Gallery requests use the same singleton Coil loader and shared OkHttp client as the existing hero
and thumbnails. Pager composition keeps the adjacent image warm when there is more than one image.
Loading and failure stay inside the modal; neither blocks or mutates the carbohydrate calculation.
Coil's normal disk/memory cache can serve an already-fetched image offline.

## Nutrition-label still images and diagnostics

`ImageCapture` writes a still only to a unique file in the app-private cache directory. It is not
inserted into MediaStore, never needs storage/photo-library permission, is processed on-device by
ML Kit, and is deleted on success, OCR failure, setup failure, capture failure, or scanner disposal.
No label image is persisted in Room or exposed to another component.

OCR diagnostics log actual live-analysis and still dimensions, latency, recognized elements,
anchors, headers, candidate geometry/scores, and selection/rejection reasons only behind the static
`BuildConfig.DEBUG` branch. Release is non-debuggable and minified, so R8 removes that branch. The
diagnostics contain recognized label text and must therefore remain debug-only; HTTP logging is
still not enabled.

## WebView

**Absent.** No `WebView`, `android.webkit`, or in-app browser component exists anywhere in the
source tree (`grep -rln "WebView" app/src/main` returns nothing). There is no HTML-rendering
surface to harden, sandbox, or restrict JavaScript on, because there is none.

## Backup and data extraction

`android:allowBackup="false"`, with both legacy (`backup_rules.xml`) and Android 12+
(`data_extraction_rules.xml`) rules additionally excluding `database`, `sharedpref`, and `file`
domains from cloud backup and device-to-device transfer, as a defence in depth beyond the single
`allowBackup` flag. The countable-portions feature adds a second Room table (`portion_units`) and
three columns to `products` — no new backup surface, since the existing rule excludes the whole
`database` domain, not named tables.

## Local database

- Every carbohydrate/weight figure (`carbsPer100`, `amountPerUnit`, `packageAmount`, `lastPortion`,
  `lastCount`, and their `original`/`latest` remote counterparts) is stored as SQLite `TEXT`
  holding a plain decimal string, never `REAL` — a deliberate, tested choice (not a security
  control) to avoid binary floating-point round-trip corruption; see `CLAUDE.md`.
- The database lives in the app's private storage (Room's default location under
  `/data/data/app.justthecarbs/databases/`), inaccessible to other apps on a non-rooted device without
  the `READ_EXTERNAL_STORAGE`-style access this app never requests or grants a path to.
- No secrets, tokens, or credentials are stored in the database — there is no account, so there is
  nothing of that kind to store.
- `portion_units.productBarcode` has a foreign key to `products.barcode` with `ON DELETE CASCADE`
  (verified structurally in the exported v3 schema JSON) — deleting a product cannot leave orphaned
  portion-unit rows referencing it.

## Release build debuggability

`buildTypes.release` sets `isDebuggable = false` explicitly (`app/build.gradle.kts`) — not merely
relying on AGP's default, which is the same value but not guaranteed to stay that way across a
Gradle/AGP upgrade. `isMinifyEnabled` and `isShrinkResources` are both `true` for release. The
signing config is attached **only** when all four signing inputs are present and the keystore file
exists (`hasSigningMaterial` check). It never falls back to the debug key. Packaging, assembling,
bundling, or installing a release now fails before task execution when an input is absent, so
Gradle no longer leaves an unsigned release artifact that could be mistaken for uploadable output.

## Logs

Only `OcrDiagnosticsLogger` writes application debug logs, for the diagnostic fields described
above, and every entry is guarded by `BuildConfig.DEBUG`. No barcode or HTTP body/header logger is
wired into the app. `okhttp-logging` remains declared in the version catalog but unused.

## Clipboard

*Copy* on the calculator result places **only the numeric value** on the clipboard
(`ProductScreen.kt`, `ResultFormatter.clipboardValue`) — never a label like "31 g carbs", and never
a barcode, product name, or any other field. This isn't a security boundary (Android's clipboard is
shared system state any app with clipboard access can read), but it does mean the app's own copy
action cannot itself carry more information than the single number the user asked to copy.

## Secrets

No API key, client secret, or credential exists anywhere in the checked-in source (Open Food Facts'
read API requires none). Release signing material is read exclusively from `keystore.properties`
(gitignored) or environment variables (`JUSTTHECARBS_STORE_FILE`, `JUSTTHECARBS_STORE_PASSWORD`,
`JUSTTHECARBS_KEY_ALIAS`, `JUSTTHECARBS_KEY_PASSWORD`) — never committed. The build fails closed with no
release artifact when any signing input is absent (verified 2026-08-14). A disposable test key can
exercise the path but must never be uploaded.

## ML Kit behaviour

Covered in depth in [google-play-data-safety.md](google-play-data-safety.md) — summarized here for
completeness: `com.google.android.datatransport` (Google's CCT telemetry transport) ships
transitively via `com.google.mlkit:common` and **cannot be excluded** without a fatal
`NoClassDefFoundError` crashing the scanner, verified empirically on the emulator (2026-08-14). No
opt-out constant exists in the shipped artifacts. Google's current
[ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure), fetched
2026-08-14, says the SDK collects device/app information, per-installation identifiers,
performance/configuration metrics, feature events, and errors for diagnostics and usage analytics.
Those types are now explicitly drafted in Data Safety rather than left as an owner guess. The app
does not configure a separate analytics product, but SDK collection still counts under Play.

## Third-party dependencies and independent review

Dependency list and licences are maintained in
[third-party-notices.md](third-party-notices.md). All are established, actively-maintained
libraries (AndroidX/Jetpack, Retrofit, OkHttp, Coil, kotlinx.serialization, ML Kit) with no
known-vulnerable version pinned.

### Dependency vulnerability scan — performed 2026-08-14

A real scan has now been run: `tools/dependency-scan.sh` resolves `releaseRuntimeClasspath` and
queries every artifact that actually ships against **OSV.dev**, the advisory database behind
GitHub's and Google's own alerts.

**Result: 226 resolved artifacts, 0 with known vulnerabilities.**

Three things about this scan are worth stating, because each is a way the same exercise commonly
produces a false all-clear:

1. **It scans resolved versions, not requested ones.** The first run reported two vulnerable
   artifacts — `kotlin-stdlib 1.3.71` and `play-services-basement 18.0.0`, both pulled in by ML
   Kit. Both were false positives: Gradle's output prints the *requested* version before the
   `-> resolved` arrow, and those versions are upgraded to 2.4.0 and 18.4.0 respectively before
   anything ships. Scanning the left-hand side reports vulnerabilities in code that is not in the
   APK. The script takes the right-hand side.
2. **It fails loudly rather than silently.** An all-empty response is indistinguishable from a
   malformed query, so the script issues a control query for a coordinate with a known advisory
   (`okhttp 4.9.1` → `GHSA-3cqm-mf7h-prrj`) and exits non-zero if that comes back clean. Without
   the control, a future change to OSV's request format would quietly turn every run into a pass.
3. **OWASP Dependency-Check was not used**, because it cannot be installed on this machine (no
   admin rights; `winget install` blocks on an unanswerable UAC prompt). OSV covers the same
   Maven advisory data over an API needing no install or key. A `./gradlew :app:dependencies`
   listing on its own would *not* have been a scan and is not presented as one.

A clean scan is a point-in-time result, not a durable property: it says nothing about advisories
published tomorrow, and re-running it stays a release-checklist item.

## Findings summary

| Area | Finding | Status |
|---|---|---|
| Image URL validation | Hero, thumbnail, selected-image DTO, Room metadata, and gallery paths | **Fixed**: repeated HTTPS/OFF-host validation, tested |
| Shared OkHttp client | Was two separately-constructed clients, not one shared instance | **Fixed**: single `AppContainer.okHttpClient` |
| Cleartext traffic | Disabled at platform level | Already correct, unchanged |
| Exported components | Only the required launcher activity | Already correct, unchanged |
| WebView | Absent | Already correct, unchanged |
| Backup | Disabled, plus explicit domain exclusions | Already correct, unchanged |
| Logging | OCR diagnostics could expose recognized label text | **Debug-only**: statically guarded; absent from non-debuggable minified release |
| Clipboard | Copies only the numeric value | Already correct, unchanged |
| Secrets | None committed; release signing fails closed | Already correct, unchanged |
| Dependency vulnerability scanning | Was never performed | **Done 2026-08-14**: `tools/dependency-scan.sh`, 226 artifacts, 0 known vulnerabilities — re-run before each release |
| Independent/third-party security audit | Not performed | **Open gap** — owner decision, not a code fix |

## Release-checklist additions

- ☑ Run a dependency-vulnerability scan — *done 2026-08-14, `bash tools/dependency-scan.sh`, clean
  across 226 artifacts.*
- ☐ **Re-run** `tools/dependency-scan.sh` immediately before publication and on a recurring cadence
  afterward. The 2026-08-14 result expires the moment a new advisory lands; a clean scan from
  months earlier is not evidence about the build being shipped.
- ☐ Decide whether an independent security review is warranted before publication, given the
  unresolved §44 regulatory question — a decision for the owner, not something this review can
  make on its own.
