# Security review — CarbScan

Written against the actual code and build configuration, not a generic checklist. Where a claim
depends on something only observable at runtime (the merged manifest, a minified build), the
command used to check it is included so it can be re-verified after any change.

**Status: internal review. Not an independent/third-party audit** (see [Third-party dependencies
and independent review](#third-party-dependencies-and-independent-review) below).

---

## Permissions

| Permission | Declared by | Purpose |
|---|---|---|
| `CAMERA` | CarbScan | Barcode and nutrition-label scanning |
| `INTERNET` | CarbScan | Open Food Facts product-data and product-image requests |
| `ACCESS_NETWORK_STATE` | Transitive, `com.google.android.datatransport` via ML Kit | Not used by CarbScan code; see [google-play-data-safety.md](google-play-data-safety.md) |

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

## Remote image URL validation (countable-portions work, 2026-08-14)

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

`ProductThumbnail.kt` is the single call site that reads `product.imageUrl` for display, and it now
routes through this validator before ever reaching Coil's `AsyncImage`. There is no second path
that bypasses it.

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
  `/data/data/app.carbscan/databases/`), inaccessible to other apps on a non-rooted device without
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
signing config is attached **only** when real signing material is present (`hasSigningMaterial`
check) and **never** falls back to the debug key — a release APK either has a real signature or
none at all, never a debug-signed one masquerading as release.

## Logs

No `Log.d`/`Log.e`/`println` call exists anywhere in `app/src/main` (`grep -rn "Log\.|println("
app/src/main/kotlin` returns nothing). There is nothing that could leak a barcode, a carbohydrate
value, or a URL into logcat, because no logging statement was ever added. `okhttp-logging` (HTTP
request/response logging) is declared in the version catalog but is **not** wired into
`NetworkModule` or any `OkHttpClient` — declared, unused, and therefore not shipping any request
body or header to logcat in either build type.

## Clipboard

*Copy* on the calculator result places **only the numeric value** on the clipboard
(`ProductScreen.kt`, `ResultFormatter.clipboardValue`) — never a label like "31 g carbs", and never
a barcode, product name, or any other field. This isn't a security boundary (Android's clipboard is
shared system state any app with clipboard access can read), but it does mean the app's own copy
action cannot itself carry more information than the single number the user asked to copy.

## Secrets

No API key, client secret, or credential exists anywhere in the checked-in source (Open Food Facts'
read API requires none). Release signing material is read exclusively from `keystore.properties`
(gitignored) or environment variables (`CARBSCAN_STORE_FILE`, `CARBSCAN_STORE_PASSWORD`,
`CARBSCAN_KEY_ALIAS`, `CARBSCAN_KEY_PASSWORD`) — never committed, and the build fails closed
(unsigned release, not a fallback debug-signed one) when that material is absent.

## ML Kit behaviour

Covered in depth in [google-play-data-safety.md](google-play-data-safety.md) — summarized here for
completeness: `com.google.android.datatransport` (Google's CCT telemetry transport) ships
transitively via `com.google.mlkit:common` and **cannot be excluded** without a fatal
`NoClassDefFoundError` crashing the scanner, verified empirically on the emulator (2026-08-14). No
opt-out constant exists in the shipped artifacts. This is disclosed in the privacy policy and Data
Safety draft as third-party (Google) telemetry, explicitly not CarbScan's own analytics — the app
neither invokes nor configures it.

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
| Image URL validation | Was unvalidated before this pass — any `image_url` in a remote record would reach Coil | **Fixed**: host-allowlist validator, tested |
| Shared OkHttp client | Was two separately-constructed clients, not one shared instance | **Fixed**: single `AppContainer.okHttpClient` |
| Cleartext traffic | Disabled at platform level | Already correct, unchanged |
| Exported components | Only the required launcher activity | Already correct, unchanged |
| WebView | Absent | Already correct, unchanged |
| Backup | Disabled, plus explicit domain exclusions | Already correct, unchanged |
| Logging | No logging statements exist | Already correct, unchanged |
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
