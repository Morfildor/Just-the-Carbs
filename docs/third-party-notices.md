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

### Why share-alike is not engaged by this build

ODbL's share-alike obligation attaches to distributing a derivative *database*. CarbScan stores
fetched products only in an app-private, on-device cache. That cache is never exported, shared,
uploaded, or redistributed — and Android backup is disabled, so it is not copied to the user's Google
account either.

A purely on-device cache is not a distributed derivative database, so share-alike is not triggered.
Attribution is provided regardless.

> **If this ever changes** — if products are exported, synced, shared between users, or backed up —
> the share-alike obligation must be reassessed *before* that feature ships. This is the single
> licence condition most likely to be broken by an innocuous-looking feature.

### Product images

Image display is currently **not implemented**. If it is added, the licence attached to each image
must be checked and honoured; OFF image licensing is not uniform and is not covered by the ODbL
attribution above.

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

- `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp` — declared for future product-image
  loading; **not currently used by any code path.** Remove if image support is not implemented.

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
