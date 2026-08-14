# Google Play Data Safety — proposed answers

**Status: draft for owner review. Nothing here has been submitted (§49).**

Answers below were derived by **reading the actual code and the merged manifest**, not from a
template. Where the honest answer is "uncertain", it says so rather than guessing.

> Confirm each answer against the Data Safety form as it reads at submission time. The form's
> wording and categories change.

---

## Evidence this was based on

| Question | How it was checked |
|---|---|
| What permissions ship? | `app/build/intermediates/merged_manifest/.../AndroidManifest.xml` |
| What leaves the device? | `OpenFoodFactsDataSource`, `NetworkModule`, `OpenFoodFactsApi` — product-data requests to `world.openfoodfacts.org`, product-image requests to Open Food Facts' image host when a product has a photo (`ProductImageUrlValidator` restricts this to Open Food Facts hosts only), and **search text** when the user uses Search by name (added 2026-08-14) |
| What is stored? | `ProductEntity`, `CarbScanDatabase`, `SettingsRepository` |
| Are images kept? | `BarcodeAnalyzer`, `LabelAnalyzer` — every `ImageProxy` is closed; nothing is written |
| Third-party SDKs | Gradle dependency tree and manifest-merger blame report |

## Permissions actually declared in the merged manifest

| Permission | Origin | Purpose |
|---|---|---|
| `CAMERA` | CarbScan | Barcode and nutrition-label scanning |
| `INTERNET` | CarbScan | Open Food Facts product lookup |
| `ACCESS_NETWORK_STATE` | **Transitive**, `com.google.android.datatransport` via ML Kit | Not used by CarbScan code |
| `<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Transitive, `androidx.core` | Internal AndroidX signature permission; not a user-facing capability |

No location, contacts, storage, photo-library, Bluetooth, phone state, SMS, or Health Connect
permission is requested.

## Data collection — proposed answers

"Collection" in Play's sense means data transmitted off the device.

| Data type | Collected? | Shared? | Answer rationale |
|---|---|---|---|
| Name, email, user IDs | **No** | No | No account exists anywhere in the app |
| Location | **No** | No | No location permission or API used |
| Health and fitness | **No** | No | No health data is transmitted. Carbohydrate values are product facts, stored on-device only |
| Photos and videos | **No** | No | Camera frames are analysed in memory and discarded; nothing is stored or sent |
| Files and docs | **No** | No | No storage access |
| App activity — **in-app search** | **Owner decision required** | Not shared for any secondary purpose | Added 2026-08-14. Play's "App activity" category explicitly names in-app search history. CarbScan stores no search history, and the text is sent to Open Food Facts **only** to fetch the results the user asked for — which is Play's definition of processing that may be declarable as collection even when it is transient. Flagged rather than answered: a "No" here is a claim about Play's category boundaries, not about the code, and the code alone cannot settle it |
| App activity — other | **No** | — | CarbScan sends no analytics or usage events |
| Device or other IDs | **No** *(see uncertainty below)* | — | CarbScan reads no advertising ID or device identifier |
| Purchases, financial info | **No** | No | No monetisation |
| Contacts, calendar, SMS | **No** | No | Not requested |

## Data stored on-device but not collected

Play's form does not treat these as collection, but they should be described accurately in the
listing and privacy policy:

- Scanned barcodes and product names
- Carbohydrate values, basis, package size
- User-verified values, verification timestamps, and the original online value
- Last portion per product and last-used timestamp
- Repeated portions per product, used to offer one-tap shortcuts (per-barcode only)
- The items in the single current meal (no name, no date shown, no past meals possible)
- Favourites and settings

Search text is deliberately **absent** from this list: it is never written to storage. See the
in-app search row above, which is a question about Play's categories rather than about the code.

## The network requests

| Field | Value |
|---|---|
| Endpoint | `GET https://world.openfoodfacts.org/api/v3/product/{barcode}` (product data) |
| Trigger | Only for a barcode not already cached on the device |
| Payload | Barcode number; User-Agent identifying app and version |
| Endpoint | `GET https://images.openfoodfacts.org/...` (product photo) |
| Trigger | Only when the product-data response includes an image, and only once per image (Coil caches it after) |
| Payload | Standard image request; no additional data attached |
| Endpoint | `GET https://world.openfoodfacts.org/cgi/search.pl` (search by name) |
| Trigger | Only on the search screen, which is reachable only from a failed barcode lookup. Debounced ~400 ms and ignored below three characters, so keystrokes are not each a request |
| Payload | The words the user typed; User-Agent identifying app and version. Never stored on the device |
| User identifiers sent | None, on either request. No account, device ID, or advertising ID exists to send |
| Encryption in transit | Yes — HTTPS only; cleartext disabled |
| Third-party recipient | Open Food Facts (independent organisation) for both |

A cached product — and an already-loaded photo — is served with **no** new network request.

## ML Kit's data-transport component — investigated 2026-08-14

`com.google.android.datatransport:transport-backend-cct` (Google's CCT logging/telemetry transport)
ships inside the APK and is what merges `ACCESS_NETWORK_STATE` into the manifest. CarbScan neither
invokes nor configures it.

**This was investigated empirically rather than assumed. Findings:**

| Question | Finding | How it was established |
|---|---|---|
| What pulls it in? | **`com.google.mlkit:common`** — the core module that *both* barcode scanning and text recognition depend on. Not an optional analytics add-on | `gradlew :app:dependencyInsight --dependency transport-backend-cct` |
| Is it actually in the shipped APK? | **Yes** — 223 classes incl. `CctTransportBackend`, `CCTDestination`, `CctBackendFactory` | dex scan of the built APK |
| Is there a documented opt-out? | **None found.** The ML Kit AAR manifests contain only `ComponentRegistrar` meta-data — no logging flag. No `firebase_ml*` or ML-Kit logging constant exists anywhere in the shipped artifacts | Read every ML Kit `AndroidManifest.xml` and scanned the dex for logging flags |
| Can it be excluded? | **No.** Excluding it builds successfully but **fatally crashes the scanner** at runtime: `NoClassDefFoundError: com.google.android.datatransport.cct.CCTDestination`, escalating to `AndroidRuntime` the moment the scanner opens | Built with the exclusion, installed on an API 36 emulator, opened the scanner, read logcat |
| Is a telemetry endpoint visible? | **No URL is greppable in the APK.** CCT ships a `StringMerger` class, which assembles its endpoint from split strings — the absence of a plain URL is by design, not evidence of absence | dex string + base64 scan |
| Does the app configure Firebase? | **No.** There is no `google-services.json` and no Firebase SDK is declared, so no Firebase project identifies this app | Repository inspection |

**Conclusion: the telemetry transport cannot be disabled without destroying the app's two headline
features.** It is a hard dependency, not optional analytics. The honest position is disclosure, and
the exclusion has been documented in `app/build.gradle.kts` so nobody re-attempts it.

**Proposed Data Safety treatment.** CarbScan itself collects nothing. However, the app cannot
truthfully claim the Open Food Facts request is the *only* traffic the process can generate — only
that it is the only request CarbScan's own code makes. The owner should therefore:

1. Review Google's own ML Kit terms and privacy documentation for what Google states it collects
   through this component, and declare accordingly.
2. Decide whether resulting diagnostic data belongs under **App activity** or **Device or other IDs**.
3. Record the decision, its basis, and the date.

☐ Declaration decided — *(owner completes, with date and source)*

## Security practices — proposed answers

| Question | Answer | Basis |
|---|---|---|
| Is data encrypted in transit? | **Yes** | HTTPS only; `usesCleartextTraffic="false"` |
| Can users request data deletion? | **Yes** | Settings → Clear saved products; uninstalling removes everything. No server-side data exists |
| Is there a data deletion mechanism? | **In-app** | No account, so no server-side deletion request applies |
| Committed to Play Families policy? | N/A | Not directed at children |
| Independent security review? | **No** | None performed |

## Backup

`android:allowBackup="false"`, with explicit `backup_rules.xml` and `data_extraction_rules.xml`
excluding database, shared preferences and files from both cloud backup and device transfer. Local
usage history therefore never reaches the user's Google account.

## Health declaration cross-reference

Whether CarbScan falls into a Play health-app category depends on the unresolved §44 assessment. See
[regulatory-release-checklist.md](regulatory-release-checklist.md) and
[play-health-declaration.md](play-health-declaration.md).

**Do not declare the app non-medical while that assessment is incomplete.**
