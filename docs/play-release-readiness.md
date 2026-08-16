# Google Play release readiness — Just the Carbs

**Decision: NO-GO as of 2026-08-14.** The app is technically close to release, but public
publication is blocked by the unresolved §44 qualification assessment. Production signing,
privacy-policy hosting, final Play declarations, and real-device release testing also remain owner
actions. A disposable test signature is not a production signature.

This is the owner's release order. Do not skip a blocked item and do not turn an unverified item
into a claim.

## 1. Go/no-go gates

| Status | Gate | Evidence or owner action |
|---|---|---|
| **BLOCKED** | §44 qualification and intended-purpose assessment | A manufacturer's assessment is **drafted but unsigned**, held locally and outside version control at `docs/regulatory-qualification-assessment.md` (conclusion: not a medical device; EU only; conditional on its §7 constraints). Sign it, then close the remaining rows in [regulatory-release-checklist.md](regulatory-release-checklist.md) — independent review (A2) and market scope (A5) are still open. Do not publish or select a health/medical status until this is resolved. |
| **OPEN** | Correct Play developer account type | Google says developers providing health apps, including medical apps, must register as an Organization. The §44/Play health classification determines whether this applies. Record the account and verification result in the regulatory checklist. |
| **OPEN** | Production upload key | No owner keystore or signing secrets existed on 2026-08-14. Follow §2; do not upload the disposable test-signed bundle. |
| **OPEN** | Public privacy-policy URL and in-app link | Host `docs/privacy-policy.html` on a stable, public, non-geofenced, non-editable HTML URL. Then add that URL to Play Console and Settings → About before the health-app release path is used. |
| **OPEN** | Data Safety answers confirmed | Use §4. Search text and ML Kit collection must not be omitted. Confirm Open Food Facts' handling of search text/IP at submission. |
| **OPEN** | Health Apps declaration | Use the §44-dependent branches in [play-health-declaration.md](play-health-declaration.md). Save as draft while unresolved. |
| **OPEN** | Store assets | Produce a 512×512 store icon, 1024×500 feature graphic, and at least two real-device screenshots. Current repository contains none of these upload assets. |
| **OPEN** | Physical-device release QA | Only barcode scanning and label OCR have been confirmed on physical hardware, and not with this release artifact. Run the release checklist in §8. |
| **OPEN** | Project licence | The repository has no project licence. The owner must choose one or explicitly keep all rights reserved; this pass does not choose for them. |
| **DONE 2026-08-14** | Target/compile SDK | `app/build.gradle.kts`: `targetSdk = 36`, `compileSdk = 37`. Google requires API 36 for new apps and updates from 2026-08-31. These values are intentionally independent. |
| **DONE 2026-08-14** | Version source | `branding.gradle.kts` is the only source for version code/name. First release is `1` / `1.0.0`; incrementing requires one edit in that file. Play requires a higher version code for every update. |
| **DONE 2026-08-14** | Release fails closed | `:app:bundleRelease` without all signing inputs exits with `Release signing is incomplete; refusing to create an unsigned release artifact.` |
| **DONE 2026-08-14** | AAB build path exercised | `app-release.aab` built and signed with an ignored, 30-day **DISPOSABLE TEST KEY**. Size: 34,961,985 bytes (33.34 MiB). It proves the path only and must not be uploaded. |
| **DONE 2026-08-14** | Resolved dependency inventory | 226 release-runtime artifacts recorded in [resolved-release-dependencies.md](resolved-release-dependencies.md), resolved only from Google Maven and Maven Central. |

## 2. Production upload key and signed AAB

Google Play requires Android App Bundles for new apps and requires Play App Signing for new apps.
With the default Play App Signing setup, Google holds the app-signing key and this local keystore
holds a separate upload key. Google documents an upload-key reset process if that upload key is lost
or compromised. By contrast, losing a self-managed app-signing key outside Play App Signing can
make updates impossible. Keep encrypted, access-controlled backups either way.

### Generate the owner-controlled upload key

Run from PowerShell. `keytool` prompts for secrets, so passwords do not enter shell history:

```powershell
New-Item -ItemType Directory -Path 'C:\secure' -Force
& 'C:\atools\jdk-21.0.12+8\bin\keytool.exe' `
  -genkeypair -v `
  -keystore 'C:\secure\JustTheCarbs-upload.jks' `
  -alias 'justthecarbs-upload' `
  -keyalg RSA -keysize 3072 -validity 10000
Copy-Item 'keystore.properties.example' 'keystore.properties'
notepad 'keystore.properties'
```

Replace every placeholder in `keystore.properties`. The file should point to the absolute keystore
path. Both files are gitignored. Back up the keystore and passwords in two independent secure
locations. Do not reuse the disposable test password or key.

### Build and verify the production bundle

```powershell
$env:JAVA_HOME='C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME='C:\atools\sdk'
.\gradlew.bat :app:bundleRelease
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs `
  'app\build\outputs\bundle\release\app-release.aab'
Get-FileHash 'app\build\outputs\bundle\release\app-release.aab' -Algorithm SHA256
```

Owner records before upload:

| Field | Value |
|---|---|
| Build date | *(owner)* |
| Version code/name | *(owner; must be `1` / `1.0.0` for the first upload unless intentionally changed)* |
| AAB byte size | *(owner)* |
| SHA-256 | *(owner)* |
| Upload certificate SHA-256 | *(owner; export/read with `keytool -list -v`)* |
| Offline backup locations tested | *(owner)* |

## 3. Official sources checked on 2026-08-14

Only primary publisher/regulator sources are used for compliance claims.

| Topic | Source and result |
|---|---|
| Target API | [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-GB_ALL): API 36 for new apps/updates from 2026-08-31. |
| AAB | [Android App Bundles](https://developer.android.com/guide/app-bundle): new Play apps must publish with AAB. |
| Signing | [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en): upload key vs app-signing key, reset process, and new-app setup. |
| Versioning | [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en): version code must increase; maximum 2,100,000,000. |
| Data Safety | [Provide information for the Data Safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en): collection, ephemeral processing, sharing exceptions, and data types. |
| ML Kit data | [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms): collected diagnostics/usage data and on-device input processing. |
| Health policy | [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en): declaration, privacy policy, and conditional regulatory proof/disclaimer. |
| Health form | [Health Apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en): all published apps complete it; current categories and medical-device fields. |
| Account type | [Play Console requirements](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en): health-app providers must register as an Organization. |
| Privacy policy | [Developer Program Policy](https://support.google.com/googleplay/android-developer/answer/17105854?hl=en): active public non-geofenced non-editable URL, no PDF, retention/deletion and contact disclosures. |
| Listing limits | [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en): title 30, short description 80, full description 4,000 characters. |
| Preview assets | [Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en): icon, feature graphic, and screenshot formats/dimensions. |
| Target audience | [Manage target audience](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en-GB): select only groups genuinely targeted; child groups trigger Families requirements. |
| Content rating | [Content Ratings](https://support.google.com/googleplay/android-developer/answer/9898843?hl=en): IARC questionnaire is mandatory and must be updated when relevant content changes. |
| Private Pages hosting | [GitHub Pages setup](https://docs.github.com/en/pages/getting-started-with-github-pages) and [publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site): private-repository Pages requires a paid plan that supports it; a published site is public unless access control is deliberately enabled. |
| EU MDR | [Regulation (EU) 2017/745](https://eur-lex.europa.eu/eli/reg/2017/745/oj?locale=en), [MDCG 2019-11 rev.1](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en), and [MDCG 2021-24 rev.1](https://health.ec.europa.eu/latest-updates/update-mdcg-2021-24-rev1-guidance-classification-medical-devices-april-2026-2026-04-20_en). These are inputs to the owner assessment, not a determination here. |

## 4. Play Console answers — draft

These answers describe the 2026-08-14 build. Reconfirm them against the artifact uploaded.

### App details and access

| Play field | Draft answer | Basis |
|---|---|---|
| App name | `Just the Carbs` | 14 characters; centralized branding. |
| Default language | English | App also contains Dutch resources, but listing localization is optional. |
| App or game | App | Utility/calculator behavior. |
| Free or paid | Owner decision; no paid features exist | Price is an owner commercial decision. |
| Contains ads | **No** | No ad SDK or ad surface. |
| App access | **All functionality is available without login or membership** | No account or authentication exists. Camera can be denied; manual entry remains available. |
| Government app | **No** | No government affiliation. |
| Financial features | **No** | No payments, trading, lending, or financial data. |

### Data Safety

Use the detailed evidence in [google-play-data-safety.md](google-play-data-safety.md). Proposed
high-level answers:

| Question | Draft answer |
|---|---|
| Does the app collect or share required user data types? | **Yes — collects.** Search terms are transmitted to Open Food Facts; ML Kit collects SDK diagnostics/usage data and per-installation identifiers. |
| Is all collected user data encrypted in transit? | **Yes.** App traffic is HTTPS-only; ML Kit states its collected data is encrypted in transit using HTTPS. |
| Account creation | **No account creation.** Account-deletion URL is not applicable. |
| Data deletion request mechanism | **No server-side deletion mechanism is provided by Just the Carbs.** The app can delete all local data in Settings or by uninstalling. Confirm how the current form treats ephemeral/SDK data before submitting. |
| Independent security review | **No.** No qualifying independent review was performed. |

Do not answer “No data collected.” Do not omit ML Kit because its APIs process camera frames
on-device; its official disclosure separately says it collects metrics and identifiers.

### Health Apps declaration

Every published app must complete the form. Current answer: **save as draft; §44 unresolved**.

- Do not select **My app doesn’t provide any health features** merely because Just the Carbs stores no
  Health Connect data. Its intended-use context must be assessed.
- If the owner assessment says it is regulated, select **Medical Device Apps** and provide every
  regulatory field requested by Google; do not upload until the legal route is complete.
- If it is not regulated but is treated by Play as supporting condition management, assess
  **Diseases and Conditions Management**. Do not select **Nutrition and Weight Management** merely
  because it handles carbohydrate values: the app does not track dietary intake, plan meals,
  manage weight, or maintain a food diary.
- If the assessment concludes it has no health feature under Play's definitions, retain the written
  basis before selecting the no-health-feature answer.

### Target audience

Draft selection: **18 and over only**. The app is not designed for children, has no child-oriented
characters, education, rewards, or marketing, and the listing must not introduce any. Select no
child age group. Revisit only if the owner intentionally redesigns the product for minors; that
would trigger a separate Families-policy assessment.

### Content rating questionnaire

Choose the **Utility / Productivity / Communication / Other** questionnaire category (calculator is
an official Productivity example), then answer from these facts:

| Questionnaire topic | Draft answer and reason |
|---|---|
| Violence, fear, sexuality/nudity, profanity, controlled substances, gambling | **No** — none is displayed or referenced. |
| User communication or user-to-user content | **No** — no accounts, messaging, posting, or shared content. |
| Location sharing | **No** — no user-facing location feature. Data Safety's treatment of network IP is separate. |
| Purchases or paid digital goods | **No** — no billing library or purchase UI. |
| Ads | **No** — no ads. |
| Unrestricted web access | **No** — users cannot browse arbitrary websites; network calls target allowlisted product services. |
| User-generated content | **No public UGC** — user-entered product values stay app-private and are never uploaded. |

Do not pre-claim an “Everyone” result. Submit the truthful questionnaire and record the IARC result
and certificate after Play calculates it.

## 5. Store listing and assets

The claim-reviewed copy is in [play-store-listing.md](play-store-listing.md). Current limits are:

| Field | Limit | Draft status |
|---|---:|---|
| App name | 30 characters | `Just the Carbs` — within limit. |
| Short description | 80 characters | Draft is within limit; recount after any policy wording change. |
| Full description | 4,000 characters | Draft is within limit before any conditional health disclaimer; recount after insertion. |

Required upload assets currently missing:

| Asset | Current Play requirement | Status |
|---|---|---|
| Store icon | 512×512, 32-bit PNG with alpha, ≤1,024 KB | **MISSING.** Adaptive launcher XML is not the store upload. |
| Feature graphic | 1024×500 JPEG or 24-bit PNG, no alpha | **MISSING.** |
| Screenshots | At least 2 total; JPEG/24-bit PNG, no alpha; each side 320–3,840 px and long side no more than twice short side | **MISSING.** Capture the five planned phone screens on a real device; do not fabricate or mock them. |
| Tablet screenshots | Not a universal publication minimum; add real tablet captures if tablet distribution/quality is claimed | **OPEN owner scope decision.** |
| Promo video | Optional | **Skip for first release.** |

## 6. Privacy-policy hosting

The repository is private. On GitHub Free, Pages requires a public source repository; paid plans
can publish Pages from a private repository, but the resulting policy page must remain publicly
accessible for Play. Do not make this application repository public just to host one file.

Recommended owner path:

1. Create a separate public repository containing only `privacy-policy.html` (and optionally a
   custom domain), or use a paid private-repository Pages plan with a public site.
2. Publish it over HTTPS and verify it in a logged-out/private browser with JavaScript disabled.
3. Record the final URL and date below.
4. Add the URL to Play Console and add a visible **Privacy policy** link in Settings → About.
5. Rebuild and repeat release QA because the in-app build changed.

| Field | Value |
|---|---|
| Public URL | *(owner)* |
| Verified logged out / non-geofenced / HTML | *(owner, date)* |
| Added in app | *(owner/developer, commit and date)* |
| Added in Play Console | *(owner, date)* |

## 7. Technical evidence from this pass

Final results are recorded only after fresh commands complete:

### 2026-08-15 re-verification (commit `6de524e`, Light-theme default)

Re-run on the exact final commit, per step 10 of the owner release sequence. The artifacts below
are **test-signed and not uploadable**; the go/no-go gates in §1 are unchanged by this pass.

| Check | Result |
|---|---|
| JVM tests | **PASS** — 429 tests, 0 failures, 0 errors, 0 skipped. |
| Instrumented tests | **PASS** — 133 tests, 0 failures, 0 errors, 0 skipped (API 36 emulator). |
| Lint | **PASS** — `:app:lintDebug` clean, `abortOnError = true`. |
| OSV dependency scan | **PASS** — 226 resolved release-runtime artifacts, 0 known vulnerabilities; control query positive, so the clean result is not a silent no-match. |
| Release APK | Built from clean tree at `6de524e`. 67,053,774 bytes. SHA-256 `C5B9F870A4F10195EA0388652AC02671BF8C2FAB21F3AF1726D936003B935B20`. |
| Release AAB | Built from clean tree at `6de524e`. 35,471,570 bytes. SHA-256 `5DB05C294269A565E28858EB1BFC0B919F8222FD4B4FCD353D3BBB5733C04C99`. |
| Signature | **NOT UPLOADABLE** — `CN=DISPOSABLE TEST KEY, OU=NOT FOR PLAY, O=JustTheCarbs Test, C=NL`, valid 2026-08-15 to **2026-09-14** (30 days). Play upload keys must remain valid well beyond that, and the upload key is fixed once published. |
| Minified release smoke test | **PASS (emulator)** — installs and launches; logcat clean of `ClassNotFoundException` / `NoClassDefFoundError` / serialization failures, so R8 keep rules still hold. |
| Light-theme default on the release build | **PASS (emulator)** — fresh install with Android night mode **on** renders the light (Cream) palette. All three selector choices re-checked on the same artifact: System follows the OS, Dark stays dark, Light stays light. |
| Physical-device QA of this artifact | **NOT DONE** — unchanged standing gap. |

| Check | Result |
|---|---|
| Fail-closed unsigned bundle | **PASS 2026-08-14** — re-verified; `:app:bundleRelease` fails with `Release signing is incomplete; refusing to create an unsigned release artifact.` naming all four missing inputs. |
| Debug path unaffected by the guard | **PASS 2026-08-14** — `:app:testDebugUnitTest` and `:app:lintDebug` both succeed with no signing material present. |
| Disposable signed AAB/APK build | **PASS 2026-08-14** — 34,961,985-byte AAB; 66,801,394-byte minified APK. |
| Signature verification | **PASS 2026-08-14** — certificate reads `CN=DISPOSABLE TEST KEY, OU=NOT FOR PLAY, O=CarbScan Test, C=NL`, valid 2026-08-14 to 2026-09-13. Confirms the artifacts on disk are **not** uploadable. |
| JVM tests | **PASS 2026-08-14** — 225 tests, 0 failures, 0 errors, 0 skipped. |
| Lint | **PASS 2026-08-14** — `:app:lintDebug` clean. |
| OSV dependency scan | **PASS 2026-08-14** — 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query positive. Point-in-time; re-run on the final commit. |
| Instrumented tests | *Not re-run in this pass* — last full green run 2026-08-14 (89 tests). Requires a running emulator/device. |
| Minified release exercised | *Emulator only, earlier in 2026-08-14* — search → selection → calculation verified on the minified build. Not re-run against this artifact. |
| Release logcat CNF/NCDF check | *Emulator only, earlier in 2026-08-14* — clean of `ClassNotFound`/`NoClassDefFound`. Not re-run against this artifact. |

## 8. Final owner release sequence

1. Complete and sign §44; select the matching Play health branch and account type.
2. Choose the public name/application ID before the first upload; both become costly or impossible
   to change after publication.
3. Choose the project licence disposition.
4. Host the privacy policy, add the in-app link, and verify public access.
5. Generate and back up the production upload key; build and hash the production AAB.
6. Capture real-device store screenshots and produce the icon/feature graphic.
7. Complete Data Safety, Health Apps, target audience, content rating, app access, ads, and store
   listing forms from this checklist. Save screenshots/PDF exports of submitted answers as evidence.
8. Upload to internal testing first. Test what Play delivers on at least one physical target device,
   including camera denial/manual entry, barcode scan, OCR, live lookup/search, Room persistence,
   countable portions, meal, verification, clipboard, dark mode, and large font.
9. Inspect Play pre-launch reports and App Bundle Explorer; resolve every blocking issue.
10. Re-run tests, lint, OSV scan, signed bundle verification, and physical-device smoke test on the
    exact final commit/AAB. Record SHA-256 and evidence, then decide go/no-go.

## 9. Explicitly unverified

- No production-signed AAB exists; only a disposable test-signed artifact exists.
- Nothing has been uploaded to Play Console and no Play form has been submitted.
- No privacy-policy URL is live or linked in the app.
- Only barcode scanning and label OCR have ever been confirmed on physical hardware. Everything
  else, including the minified release, remains unverified on physical hardware.
- Countable portions against a real Open Food Facts `serving_size` response remain fixture-only.
- Multi-device/Samsung-specific behavior remains unverified.
- Open Food Facts retention/use of search terms and request IP addresses was not established by a
  primary source fetched in this pass; the Data Safety draft is conservative and flags confirmation.
- No legal/regulatory or Open Food Facts licence conclusion was made by this engineering pass.
