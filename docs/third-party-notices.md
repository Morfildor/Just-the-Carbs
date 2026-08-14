# Third-party notices — CarbScan

Dependencies and data sources, with the licence each is used under (§57).

---

## Data source — attribution required

### Open Food Facts

CarbScan retrieves product data from Open Food Facts.

- **Database licence:** Open Database License (ODbL) v1.0
- **Individual contents licence:** Database Contents License (DbCL) v1.0
- **Product images:** Creative Commons Attribution-ShareAlike **3.0** (CC BY-SA 3.0) — the version
  matters and is easy to lose: OFF's terms link specifically to
  `creativecommons.org/licenses/by-sa/3.0/`, not the 4.0 most people assume. Per Open Food Facts'
  own terms of use (`world.openfoodfacts.org/terms-of-use`, checked 2026-08-14), a separate
  licence from the database's ODbL/DbCL. OFF's terms also note images "may contain graphical
  elements subject to copyright or other rights" belonging to the photographed product's own
  packaging — a real nuance OFF does not claim to have cleared, not boilerplate.

**Attribution shown in-app** (Settings → About) — two lines, because the data and the photographs
are licensed separately and one ODbL sentence leaves the images uncredited:

> Product data from Open Food Facts (openfoodfacts.org), used under the Open Database License
> (ODbL). Individual records are under the Database Contents License (DbCL).

> Product photos from Open Food Facts (openfoodfacts.org), used under the Creative Commons
> Attribution-ShareAlike 3.0 licence (CC BY-SA 3.0). A photo may also show packaging artwork owned
> by its manufacturer.

Both name the licence and credit Open Food Facts with its address, which is what OFF's terms
actually ask of re-users ("mention the licence and … attribute the authorship to Open Food Facts
with a link to https://openfoodfacts.org", re-checked live 2026-08-14). The image line's closing
sentence is not boilerplate: OFF's terms state plainly that a photo may contain graphical elements
owned by someone else, and that the CC licence covers the photograph, not the packaging design in
it.

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

### Product images — displayed and licence-hardened, attribution wording still an owner action

Product images from Open Food Facts **are displayed in the app** (product thumbnails on the home
list and the calculator header). As of the countable-portions work (2026-08-14):

- The licence is now confirmed as **CC BY-SA** (see above), distinct from the database's ODbL/DbCL
  — this was previously described as "varying by image" pending review; that review is done.
- Requests for images are restricted to an approved-host allowlist
  (`ProductImageUrlValidator`, HTTPS + `images.openfoodfacts.org`/`static.openfoodfacts.org` only)
  — a corrupt or malicious product record cannot make the app fetch an image from an arbitrary
  third-party host.
- Coil (image loading) and Retrofit (product data) now genuinely **share one `OkHttpClient`
  instance** — previously two separately-constructed clients with matching configuration, not one
  shared object; both now inherit the same connection pool, timeouts, and identifying User-Agent.

**Done (2026-08-14).** The in-app About screen now carries a distinct CC BY-SA 3.0 credit line for
photographs alongside the ODbL/DbCL line for data — see the wording above. Adding the required
credit is a factual step, not a licensing determination, so it is closed here; whether the overall
use of OFF data and images is compliant remains the licence review below, which is still open.

☑ CC BY-SA attribution wording added to in-app About - *(done 2026-08-14; source:
world.openfoodfacts.org/terms-of-use, re-checked live the same day)*

☐ Store-listing attribution - *(owner completes when the listing is written)*

### API terms

Verify Open Food Facts' current API terms and rate limits before release. As checked 2026-08-14
against `openfoodfacts.github.io/openfoodfacts-server/api/`: product reads are on the **v3** read
endpoint (`api/v3/product/{barcode}`) — CarbScan migrated off v2 as part of the countable-portions
work, since v2 is documented as deprecated-but-supported and the fields this app reads are
unchanged between the two. Rate limit remains 15 reads/min/IP, and an identifying `User-Agent` is
mandatory (documented format: `AppName/Version (ContactEmail)`). CarbScan sends
`CarbScan/<version> (Android; <contact>)`, generated from `branding.gradle.kts`. The contact address
is `albinogorillassupport@gmail.com`, supplied by the owner on 2026-08-14 — this closes what was a
real compliance gap against OFF's documented User-Agent format, since the previous value was a
placeholder that identified nobody.

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
