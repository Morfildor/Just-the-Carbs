# Third-party notices — CarbScan

Dependencies and data sources, with the licence each is used under (§57).

---

## Data source — attribution required

### Open Food Facts

CarbScan retrieves product data from Open Food Facts.

- **Database licence:** Open Database License (ODbL) v1.0
- **Individual contents licence:** Database Contents License (DbCL) v1.0
- **Product images:** Creative Commons licences, varying by image

**Attribution shown in-app** (Settings → About):

> Product data from Open Food Facts, used under the Open Database License (ODbL).

### Share-alike — requires review, not assumed resolved

ODbL's share-alike obligation attaches to distributing a derivative *database*. As built, CarbScan
stores fetched products only in an app-private, on-device cache that is never exported, shared,
uploaded or redistributed, and Android backup is disabled.

**That is a description of the implementation, not a legal conclusion.** This project does not make
a licensing determination (correction #3). Open Food Facts data and images remain subject to their
applicable current licences, and a licence review is a release-checklist gate.

> **Anything that changes the distribution picture reopens the question** - export, sync, sharing
> between users, server-side caching, or backup. This is the single licence condition most likely to
> be broken by an innocuous-looking feature, so reassess *before* such a feature ships.

☐ Licence review completed before publication - *(owner completes, with date)*

### Product images — now displayed, licensing NOT yet reviewed

Product images from Open Food Facts **are displayed in the app** (product thumbnails on the home
list and the calculator header).

> **Open action.** Image licensing on Open Food Facts is **not uniform** and is **distinct from the
> ODbL licence covering the structured database**. Individual images carry their own licences
> (various Creative Commons terms) and may impose their own attribution requirements.
>
> The in-app attribution currently covers the *database*. Before publication the owner must review
> Open Food Facts' current image attribution and licensing requirements and add whatever additional
> attribution they require.

☐ Current OFF image attribution/licensing requirements reviewed and satisfied - *(owner completes,
with date and source URL)*

### API terms

Verify Open Food Facts' current API terms and rate limits before release. As checked on 2026-08-13:
15 reads/min/IP, and an identifying `User-Agent` is mandatory. CarbScan sends
`CarbScan/<version> (Android; <contact>)`, generated from `branding.gradle.kts`.

---

## Software dependencies

All Apache License 2.0 unless noted.

### AndroidX / Jetpack — Apache 2.0, The Android Open Source Project

`androidx.core:core-ktx` · `androidx.core:core-splashscreen` · `androidx.activity:activity-compose` ·
`androidx.compose:compose-bom` and Compose UI, Foundation, Material3, Material Icons ·
`androidx.lifecycle:*` · `androidx.navigation:navigation-compose` · `androidx.room:*` ·
`androidx.datastore:datastore-preferences` · `androidx.camera:*` (camera-core, camera2,
camera-lifecycle, camera-view)

### Google — Apache 2.0

- `com.google.mlkit:barcode-scanning` — on-device barcode recognition
- `com.google.mlkit:text-recognition` — on-device text recognition

> ML Kit transitively includes Google Play services components and
> `com.google.android.datatransport`, a Google logging/telemetry transport that CarbScan does not
> invoke or configure. It is disclosed in [google-play-data-safety.md](google-play-data-safety.md)
> and the privacy policy rather than left implicit. ML Kit is additionally subject to Google's own
> terms of service, which the owner should review before release.

### Square — Apache 2.0

- `com.squareup.okhttp3:okhttp` — HTTP client
- `com.squareup.retrofit2:retrofit` and `converter-kotlinx-serialization`

### JetBrains — Apache 2.0

- `org.jetbrains.kotlin:*` — Kotlin standard library and compiler
- `org.jetbrains.kotlinx:kotlinx-serialization-json`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`

### Coil — Apache 2.0

- `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp` — loads Open Food Facts product
  thumbnails. Shares the app's single OkHttp client, so images inherit the same timeouts and
  identifying User-Agent.

### Test-only dependencies (not shipped in the APK)

- JUnit 4 — Eclipse Public License 1.0
- `androidx.test:*`, Espresso, Compose UI test — Apache 2.0
- `com.squareup.okhttp3:mockwebserver` — Apache 2.0
- `app.cash.turbine:turbine` — Apache 2.0
- `androidx.room:room-testing` — Apache 2.0

---

## Generating the definitive notice list

The table above is maintained by hand and can drift. Before release, generate the authoritative list
from the resolved dependency graph:

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```

ML Kit and Play services artifacts also ship machine-readable notices inside their AARs
(`third_party_licenses.txt`), which must be surfaced if Play services libraries remain in the final
build.

☐ Definitive notice list regenerated and reconciled before release — *(owner completes)*

---

## Trademarks

CarbScan is not affiliated with, endorsed by, or connected to CamDiab, Ypsomed, CamAPS FX, Abbott,
Libre, or any other medical device manufacturer. No third-party trademark is used in the app name,
icon, or promotional material (§51).
