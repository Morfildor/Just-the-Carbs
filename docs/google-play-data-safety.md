# Google Play Data Safety — proposed answers

**Draft for owner review; nothing submitted. Evidence date: 2026-08-14.**

Google defines collection as transmitting user data off-device, including transmission by an SDK.
It explicitly says ephemeral processing must still be included in the form response. Its data-type
table defines in-app search history as information about what a user searched for in the app.

Primary sources checked:

- [Google Play Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
- [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms)

## Form-level answers

| Question | Proposed answer | Evidence |
|---|---|---|
| Does the app collect or share any required user data types? | **Yes — collects** | Search text is sent to Open Food Facts. ML Kit's official disclosure says its Android SDK collects diagnostics/usage data and identifiers. |
| Is all collected data encrypted in transit? | **Yes** | App traffic is HTTPS-only and `usesCleartextTraffic="false"`; ML Kit states its collected data uses HTTPS. |
| Does the app provide account creation? | **No** | No authentication or account model exists. |
| Can users request server-side deletion? | **No mechanism provided by CarbScan** | Settings deletes all local records; the app controls no Open Food Facts or Google server record. Re-check the exact form wording for ephemeral/SDK data. |
| Independent security review? | **No** | No qualifying independent audit was performed. |

Do **not** submit the former “nothing collected” position. Google’s current ML Kit disclosure closes
that uncertainty.

## Data-type answers

The table is intentionally conservative. The owner must compare the current form labels at
submission; Google notes that final categorization can depend on the developer's interpretation.

| Play data type | Collected? | Shared? | Required/optional | Purpose | Basis |
|---|---|---|---|---|---|
| App activity → In-app search history | **Yes** | **No, proposed under user-initiated-action exception** | Optional; the app works without name search | App functionality | A user types a product name and the app sends it to Open Food Facts after a debounce. The UI names Open Food Facts, so the transfer is expected. Google says a transfer caused by a specific user action that the user reasonably expects need not be marked shared. |
| App activity → App interactions | **Yes** | No | Required when ML Kit features are used | Analytics | ML Kit lists feature initialization, detection, model download, resource release, and other event types as collected for diagnostics and usage analytics. |
| App info and performance → Diagnostics | **Yes** | No | Required when ML Kit features are used | Analytics | ML Kit lists device/app information, performance metrics, API configuration, input/output size, feature version, and error codes. |
| Device or other IDs | **Yes** | No | Required when ML Kit features are used | Analytics | For bundled features, ML Kit lists per-installation identifiers not intended to uniquely identify a user or physical device. |
| Approximate location | **Owner confirmation required; declare Yes conservatively if request IP is retained/used to infer location** | Proposed No under user action/service handling | Network features optional | App functionality / security | Open Food Facts and Google necessarily receive a network address, but no fetched primary Open Food Facts source established retention or location inference. Google says location inferred from IP belongs here. Record the service evidence used for the final answer. |
| Health info / fitness info | **No** | No | — | — | No health data is transmitted. Product carbohydrate facts and local portion calculations are not sent as a user health record. |
| Photos and videos | **No** | No | — | — | Camera frames are processed in memory. ML Kit states feature input and result data remain on-device; the app saves/uploads no camera image. Product images are downloaded, not uploaded user photos. |
| Name, email, user IDs | **No** | No | — | — | No account. The identifying User-Agent contains the developer support email, not a user's email. |
| Financial info, purchases | **No** | No | — | — | No billing or monetization. |
| Messages, contacts, calendar, files | **No** | No | — | — | No such APIs or permissions. |
| Other app activity | **No beyond ML Kit events above** | No | — | — | No developer-added analytics or usage-event pipeline. |

### Search text recommendation

Answer **Yes, collected, In-app search history, optional, app functionality**. Do not silently answer
No because the app keeps no local history: Google’s definition is transmission off-device, not local
storage. Marking it ephemeral is only supportable if Open Food Facts confirms that the terms are
held in memory no longer than necessary for the real-time response. That service-side fact was not
verified here, so the conservative draft does not claim ephemeral processing.

For “shared,” the proposed **No** relies on Google's documented user-initiated-action exception:
the user opens Search by name, sees that it searches Open Food Facts, and types the query to request
those results. The owner should preserve screenshots of that disclosure and the form wording.

## Network inventory

| Recipient / endpoint | Trigger | Data |
|---|---|---|
| `https://world.openfoodfacts.org/api/v3/product/{barcode}` | Uncached barcode lookup | Barcode; app/version/developer-contact User-Agent; normal network metadata |
| `https://world.openfoodfacts.org/cgi/search.pl` | User performs name search; ≥3 characters, ~400 ms debounce | Search words; User-Agent; normal network metadata |
| `https://images.openfoodfacts.org/...` or `https://static.openfoodfacts.org/...` | A returned product has an allowlisted HTTPS image URL | Standard image request; User-Agent; normal network metadata |
| Google ML Kit endpoints | SDK diagnostics/maintenance behavior | Data types listed by Google's ML Kit disclosure; not camera input or recognized result |

`ProductImageUrlValidator` permits only HTTPS and the two exact Open Food Facts image hosts. Retrofit
and Coil use the same `OkHttpClient`. No developer endpoint, account server, analytics service, ad
network, or cloud sync exists.

## Permissions in the merged release manifest

| Permission | Origin | Purpose |
|---|---|---|
| `CAMERA` | CarbScan | Barcode and nutrition-label scanning |
| `INTERNET` | CarbScan | Open Food Facts and SDK networking |
| `ACCESS_NETWORK_STATE` | ML Kit → `com.google.android.datatransport` | Transitive SDK network/transport support |
| `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Internal signature permission |

No location, contacts, storage/photo-library, Bluetooth, phone, SMS, advertising ID, or Health
Connect permission is requested.

## Data stored only on-device

- Barcodes, product names, carbohydrate values, basis, package size, source and verification state
- Portion units, remembered portions, favourites, and settings
- Per-product usual portions
- Items in the single current meal; no past meal or diary exists

Android backup is disabled and the backup/data-transfer rules exclude app data. Settings can clear
recent usage or all saved products; uninstalling removes the app-private data.

## ML Kit resolution

The shipped `com.google.mlkit:barcode-scanning` and `text-recognition` APIs process images and
recognized outputs on-device. Separately, Google's disclosure says ML Kit collects:

- device manufacturer/model, OS/build, and ML accelerators;
- package name and app version;
- per-installation identifiers for bundled features;
- latency/performance, API configuration, input/output size, feature version, events, and errors.

Google states these are used for diagnostics and usage analytics, encrypted with HTTPS, and not
transferred to third parties. CarbScan does not enable barcode auto-zoom, so the additional
auto-zoom session ID, zoom events, and predicted bounding-box collection is not applicable.

The transport cannot be excluded: a prior emulator experiment produced
`NoClassDefFoundError: com.google.android.datatransport.cct.CCTDestination` when opening the scanner.
Disclosure is therefore required; “we did not add analytics” is not a basis for answering No.

## Owner submission record

| Decision | Value / source / date |
|---|---|
| Open Food Facts search retention; ephemeral eligible? | *(owner obtains primary service statement)* |
| Request IP retained or used for approximate location? | *(owner obtains primary service statement; otherwise use conservative Yes)* |
| Current Play form data-type mapping confirmed | *(owner)* |
| Submitted answers exported and retained at | *(owner)* |
