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
| What leaves the device? | `OpenFoodFactsDataSource`, `NetworkModule`, `OpenFoodFactsApi` — one endpoint, one host |
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
| App activity | **No** *(see uncertainty below)* | — | CarbScan sends no analytics or usage events |
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
- Favourites and settings

## The one network request

| Field | Value |
|---|---|
| Endpoint | `GET https://world.openfoodfacts.org/api/v2/product/{barcode}` |
| Trigger | Only for a barcode not already cached on the device |
| Payload | Barcode number; User-Agent identifying app and version |
| User identifiers sent | None. No account, device ID, or advertising ID exists to send |
| Encryption in transit | Yes — HTTPS only; cleartext disabled |
| Third-party recipient | Open Food Facts (independent organisation) |

A cached product is served with **no** network request.

## ⚠️ Uncertainty requiring owner confirmation

**ML Kit's data-transport component.** `com.google.android.datatransport:transport-backend-cct`
arrives transitively with ML Kit and merges `ACCESS_NETWORK_STATE`. It is Google's logging/telemetry
transport. CarbScan neither invokes nor configures it.

This means the app cannot truthfully claim that the Open Food Facts request is the *only* traffic
the process can generate — only that it is the only request CarbScan's own code makes.

The owner must, before submission:

1. Confirm against **current official ML Kit documentation** whether usage logging occurs and
   whether a supported opt-out exists. *(No opt-out constant was found in the shipped artifacts, so
   none has been invented here.)*
2. Decide whether any resulting diagnostic data must be declared under **App activity** or
   **Device or other IDs**.
3. Record the decision and its basis.

☐ Confirmed — *(owner completes, with date and source)*

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
