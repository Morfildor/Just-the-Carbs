# Google Play release readiness — Just the Carbs

**Decision: NO-GO as of 2026-08-26.** The app is technically close to release, but public
publication is blocked by the unresolved §44 qualification assessment. Final Play declarations and
real-device release testing also remain owner actions.

Production signing is **no longer a blocker**: an owner-controlled upload key exists and has signed
a real bundle (§2, §7). That closes the signature gate and nothing else — §44 still governs
go/no-go.

**2026-08-26 (later same day):** the working tree behind this build is now committed (`68c85a3` on
`main`) rather than uncommitted, closing that specific caveat. JVM 771/771 (0 skipped,
`--rerun-tasks`), instrumented 218/218 (0 skipped, fresh AVD after a `-wipe-data` repair — the
instance had a corrupted disk image, see CLAUDE.md's "AVD went into a crash loop" note), lint clean.
`app-release.aab` rebuilt from `clean` on this commit: 35,624,186 bytes, SHA-256
`37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b`, signed with the real upload key
(fingerprint `1E:21:23:F3:...:C4:F5`, matching §2). R8 barriers re-checked on this build's
`mapping.txt`: `ScanEvidenceRecorder`/`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`;
`ScanEvidenceExport`/`OcrDiagnosticsReport` absent entirely; `UnitMarkerFilter`,
`CandidateProvenance`, `PackageBasisResolver` retained as real classes. Release manifest carries only
the ML Kit init provider and `androidx.startup` — no `FileProvider`. The keystore still has no
tested backup (owner action, unchanged).

This is the owner's release order. Do not skip a blocked item and do not turn an unverified item
into a claim.

## 1. Go/no-go gates

| Status | Gate | Evidence or owner action |
|---|---|---|
| **BLOCKED** | §44 qualification and intended-purpose assessment | A manufacturer's assessment is **drafted but unsigned**, held locally and outside version control at `docs/regulatory-qualification-assessment.md` (conclusion: not a medical device; EU only; conditional on its §7 constraints). Sign it, then close the remaining rows in [regulatory-release-checklist.md](regulatory-release-checklist.md) — independent review (A2) and market scope (A5) are still open. Do not publish or select a health/medical status until this is resolved. |
| **OPEN** | Correct Play developer account type | Google says developers providing health apps, including medical apps, must register as an Organization. The §44/Play health classification determines whether this applies. Record the account and verification result in the regulatory checklist. |
| **DONE 2026-08-26** | Production upload key | Owner-controlled keystore generated at `C:\secure\JustTheCarbs-upload.jks` (alias `justthecarbs-upload`) and used to sign `app-release.aab`. Signer DN `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`, 2048-bit RSA, valid 2026-08-26 → 2051-08-20. Evidence in §2. **Backup of the keystore and its passwords is still unverified** and remains an owner action. |
| **ACTION** | Republish the privacy policy | The page is live at `https://morfildor.github.io/Just-the-Carbs/privacy-policy.html`, served by GitHub Pages from `docs/` on `main`, and `SettingsScreen` opens that same `BuildConfig.PRIVACY_POLICY_URL` (pinned by `SettingsScreenTest`). **Both `privacy-policy.md` and `privacy-policy.html` were edited on 2026-08-26** — the "Your control" section now describes what the two clear actions really do, because the old wording ("delete everything the app has stored") overstated Clear saved products. The live page still shows the old text until the commit is pushed. Push, reload the URL, confirm the new text and the 26 August date, then record the URL in Play Console. |
| **OPEN** | Regulatory assessment in public Git history | `docs/regulatory-qualification-assessment.md` (`7a3b43a`) and its PDF (`7212efb`) are untracked today but remain in reachable history on a **public** repository. Procedure, the ordering question that decides severity, and why a force-push is not a full remedy: [git-history-remediation.md](git-history-remediation.md). Owner action; no history was rewritten. |
| **OPEN** | Data Safety answers confirmed | Use §4. Search text and ML Kit collection must not be omitted. Confirm Open Food Facts' handling of search text/IP at submission. |
| **OPEN** | Health Apps declaration | Use the §44-dependent branches in [play-health-declaration.md](play-health-declaration.md). Save as draft while unresolved. |
| **OPEN** | Store assets | `Logo.png` at the repository root **is** a 512×512 mark and is the traced source of `ic_launcher_foreground.xml`, so the store icon exists but has never been checked against Play's icon rules (no transparency, no rounded-corner masking of its own). Still missing entirely: the 1024×500 feature graphic and at least two real-device screenshots. |
| **DONE 2026-08-26** | One shipping language | The app ships English only. `values-nl/strings.xml` held 206 of 298 strings and none of the 10 plurals, so a Dutch device saw a mixed interface; it is deleted and `androidResources { localeFilters += "en" }` also stops AndroidX/Material supplying their own translations. Verified: the release APK carries no language configurations. Open Food Facts input is still parsed in Dutch — that is separate. |
| **DONE 2026-08-26** | Release CI actually gates | `.github/workflows/release-gate.yml`: JVM (`--rerun-tasks`), instrumented, lint and the release build all block, with 0-skipped assertions read from JUnit XML and the R8 privacy barriers checked as build steps. `ci.yml`'s instrumented job keeps `continue-on-error` for branch work and is now labelled as such. Before this, **no workflow required the instrumented suite to pass.** |
| **OPEN** | Physical-device release QA | Only barcode scanning and label OCR have been confirmed on physical hardware, and not with this release artifact. Run the release checklist in §8. |
| **OPEN** | Project licence | The repository has no project licence. The owner must choose one or explicitly keep all rights reserved; this pass does not choose for them. |
| **DONE 2026-08-14** | Target/compile SDK | `app/build.gradle.kts`: `targetSdk = 36`, `compileSdk = 37`. Google requires API 36 for new apps and updates from 2026-08-31. These values are intentionally independent. |
| **DONE 2026-08-14** | Version source | `branding.gradle.kts` is the only source for version code/name. First release is `1` / `1.0.0`; incrementing requires one edit in that file. Play requires a higher version code for every update. |
| **DONE 2026-08-14** | Release fails closed | `:app:bundleRelease` without all signing inputs exits with `Release signing is incomplete; refusing to create an unsigned release artifact.` |
| **DONE 2026-08-26** | AAB build path exercised | `app-release.aab` built from `clean` on committed commit `68c85a3`, signed with the real owner upload key (§2). Size: 35,624,186 bytes (34.0 MiB), SHA-256 `37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b`. This is upload-eligible signing-wise; §44 still blocks publication. |
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

**Check the certificate name, not just that signing succeeded.** The Gradle guard refuses to package
a release without all four secrets, but it cannot tell an upload key from a test key — both are
four valid properties pointing at a real keystore. On 2026-08-25 a full clean release build produced
a signed APK and AAB whose signer was:

```
Signer #1 certificate DN: CN=DISPOSABLE TEST KEY, OU=NOT FOR PLAY, O=JustTheCarbs Test, C=NL
```

A green `bundleRelease` is therefore **not** evidence that an uploadable artifact exists. Read the DN
before recording anything below. `apksigner` cannot read an AAB, and a bundle-only build (what
Android Studio's *Build → Generate Signed App Bundle* produces) leaves no release APK on disk at
all — so verify the bundle directly:

```powershell
& 'C:\atools\jdk-21.0.12+8\bin\keytool.exe' -printcert `
  -jarfile 'app\build\outputs\bundle\release\app-release.aab'
```

Use `apksigner verify --print-certs` only when an APK from the *same* build actually exists.

`NOT FOR PLAY` in the DN means the keystore on this machine is still the disposable one and the
upload key has not been generated yet.

**Resolved 2026-08-26.** `keystore.properties` now points at the owner's real upload keystore and
the same check reads `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`. Two deviations
from the recipe above, both recorded rather than corrected:

- The key is **2048-bit RSA**, not the 3072 bits the `keytool` command above specifies. 2048 meets
  Google's documented minimum, so this is acceptable and is **not** worth regenerating — but the
  upload key is fixed once published, so decide now if a larger key is wanted.
- `app/build.gradle.kts` is unchanged: the fail-closed guard and `signingConfigs` are exactly as
  committed, and Android Studio picked up the real key through the existing `keystore.properties`
  path. No signing logic was edited to make this build succeed.

Owner records before upload:

| Field | Value |
|---|---|
| Build date | 2026-08-26 (Android Studio, `bundleRelease`) |
| Version code/name | `1` / `1.0.0` — read from the packaged release manifest, package `app.justthecarbs` |
| AAB byte size | 35,689,027 bytes (34.03 MiB) |
| SHA-256 | `00876FA9B2A73B44F585A0D792948921F7BC6F2DE181D76DC22914237FBBB4A2` |
| Upload certificate SHA-256 | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` |
| Offline backup locations tested | **NOT DONE** *(owner)* — the keystore exists only at `C:\secure\JustTheCarbs-upload.jks`. Losing it before Play App Signing enrollment means no updates are possible; after enrollment Google documents an upload-key reset. Back it up and the passwords in two independent secure locations, then restore-test one. |

**This artifact is not the release candidate.** It was built from the current *uncommitted* working
tree, not from a clean tagged commit, so it does not satisfy step 10 of §8. It proves the upload key
works end to end. Rebuild and re-hash from the final commit before uploading anything but an
internal-testing throwaway.

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

### 2026-08-26 — first production-key signed bundle

Built in Android Studio from the uncommitted working tree. Verified afterwards from the command
line against the artifact on disk; no test was re-run in this pass.

| Check | Result |
|---|---|
| Release AAB | Built. 35,689,027 bytes. SHA-256 `00876FA9B2A73B44F585A0D792948921F7BC6F2DE181D76DC22914237FBBB4A2`. |
| Signature | **UPLOADABLE KEY** — `jarsigner -verify` reports `jar verified`, signer `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`, SHA256withRSA, 2048-bit, valid 2026-08-26 → 2051-08-20. The "self-signed" and "no timestamp" warnings jarsigner prints are expected and correct for an Android upload key; do not treat them as defects. |
| Identity in the packaged manifest | `package="app.justthecarbs"`, `versionCode="1"`, `versionName="1.0.0"`. |
| Release APK | **NOT BUILT** — this was a bundle-only build, so `app/build/outputs/apk/release/` does not exist. Any check written against that APK cannot be run on this artifact. |
| R8 privacy barriers | **PASS** — re-checked on this build's `mapping.txt`. `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` map to `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport` and `ScanTrace` have no mapping entry at all; `UnitMarkerFilter`, `CandidateProvenance` and `CarbCandidate` are retained as real classes. |
| Release manifest providers | **PASS** — only the ML Kit init provider and `androidx.startup`. Zero matches for `FileProvider` or `evidence`, so the debug evidence provider does not ship. |
| Signing guard intact | **PASS** — `app/build.gradle.kts` is byte-unchanged in the signing region; the real key was picked up through the existing `keystore.properties` path. |
| Tests / lint on this artifact | **NOT RE-RUN.** Last full green run is the 2026-08-25 release-closure pass (JVM 726, instrumented 214, lint exit 0) against a different tree. |
| Physical-device QA of this artifact | **NOT DONE** — unchanged standing gap. |
| Keystore backup | **NOT DONE** — see §2. |

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
   *(Key generated and a bundle signed and hashed 2026-08-26 — see §2. **Backup still outstanding**,
   and the hashed bundle must be rebuilt from the final commit.)*
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

- A production-key-signed AAB now exists (2026-08-26, §2 and §7), but it was built from an
  uncommitted tree and is **not** a release candidate. The keystore has no tested backup, and Play
  App Signing enrollment has not happened.
- Nothing has been uploaded to Play Console and no Play form has been submitted.
- No privacy-policy URL is live or linked in the app.
- Only barcode scanning and label OCR have ever been confirmed on physical hardware. Everything
  else, including the minified release, remains unverified on physical hardware.
- Countable portions against a real Open Food Facts `serving_size` response remain fixture-only.
- Multi-device/Samsung-specific behavior remains unverified.
- Open Food Facts retention/use of search terms and request IP addresses was not established by a
  primary source fetched in this pass; the Data Safety draft is conservative and flags confirmation.
- No legal/regulatory or Open Food Facts licence conclusion was made by this engineering pass.
