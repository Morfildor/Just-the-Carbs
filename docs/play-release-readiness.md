# Google Play release readiness — Just the Carbs

**Status as of 2026-08-28**

| Area | Status |
|---|---|
| Engineering | **READY** |
| Release artifact | **READY** |
| Store listing | **READY** |
| Internal testing | **DEPLOYED** — `1.0.0` / `versionCode 1` |
| Closed testing | **ACTIVE — running, 12+ testers opted in** — same artifact, promoted |
| Version in development | `1.0.1` / **`versionCode 2` — OPEN**, never built for release, never uploaded |
| Play-delivered smoke test | **PENDING** — §8a |
| Production submission | **BLOCKED** — see §1b |

**Closed testing is running.** `1.0.0` (`versionCode 1`) went to internal testers on 2026-08-26 and
**the same artifact was then promoted to the closed track**, where 12 or more testers are opted in
and the testing period is under way. It is **one artifact and one hash progressing through two
tracks**, not two releases — `docs/version-history.md` records it once, by hash, with its track
progression.

Play accepted the bundle and its signature, so **bundle format, upload-key signing and Play App
Signing acceptance are proven facts, not open items** — do not re-list them.

**The closed track no longer needs creating and its clock is already running.** Earlier revisions of
this document treated closed testing as a future step and asked whether it needed to be set up; that
is stale and has been corrected. What remains is elapsed time plus the production gates in §1b.

Play currently displays the temporary name `app.justthecarbs (unreviewed)`. That is expected until
app setup and review complete; it is **not** a defect and needs no rebuild.

**`versionCode 2` / `1.0.1` is open and is the current development target.** It was opened during the
closed beta and is set in `branding.gradle.kts`; nothing has been built or uploaded against it. Add
work to its section in `CHANGELOG.md`, do not bump the number again, and do not build a release AAB
except as a deliberate, instructed release step (§2c). Earlier revisions of this document said "do
not create a `versionCode 2`" — that was written before 2 existed and is stale.

**Engineering evidence** (unchanged, and still corresponding to this artifact): JVM **771/771**,
instrumented **218/218**, both 0 skipped; lint **0 errors**. `app-release.aab` built from `clean` on
`68c85a3` (recorded in `0b2312f`): 35,624,186 bytes, SHA-256
`37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b`, signed with upload key SHA-256
`1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5`.
R8 privacy barriers and the absent release `FileProvider` re-checked on this build's `mapping.txt`
(§7).

The remaining path is Play Console form completion (§5a) plus two owner decisions — the **Health Apps
declaration category** (§4a) and the **§44 signature**. Nothing that remains is engineering work.

**Immediate next action: install the Play-delivered build on the Samsung device via the tester link
and run the ten-step smoke test (§8a).**

**The 14-day clock is running.** If this account is subject to Play's **12-testers / 14-days
closed-testing requirement**, the closed track now satisfies both halves of it in progress: 12+
testers are opted in and the period is elapsing. Internal testing never counted toward it, which is
why the closed track matters; that track now exists and is active, so this is a matter of waiting
and of keeping testers opted in, not of setting anything up. Confirm the remaining days in Console
on the Production track — that is the authority, not this file.

## 1. Release gates

Every gate is classified by **the stage it actually blocks**, with the authority that makes it
mandatory. A gate that no external party requires is not a blocker; it is listed under
§1c as optional. Do not reintroduce a blocker because an earlier revision of this document called it
one — reclassify it against the authority column or delete it.

### 1a. Closed — testing tracks deployed

**INTERNAL TESTING: DEPLOYED (2026-08-26). CLOSED TESTING: ACTIVE**, running the same artifact with
12+ testers opted in. Everything here is settled by evidence, and several rows are now settled by
Play itself rather than by local verification. Do not reopen them.

| Status | Item | Evidence |
|---|---|---|
| **DEPLOYED 2026-08-26** | Internal testing track | `1.0.0` / `versionCode 1` live to internal testers. Track Active; Play review status *Not reviewed*. The temporary listing name `app.justthecarbs (unreviewed)` is expected pre-review. |
| **ACTIVE** | Closed testing track | The **same artifact** promoted from internal — one `versionCode 1`, one hash, two tracks; **not** a second release. 12+ testers opted in, testing period running. |
| **PROVEN BY PLAY** | Bundle accepted | Play ingested the AAB. Bundle format, `versionCode`/`versionName` and manifest identity are accepted — no longer a local claim. |
| **PROVEN BY PLAY** | Upload signature accepted | Play accepted the upload-key signature and Play App Signing enrollment. Upload key SHA-256 `1E:21:23:F3:…:C4:F5`; Google holds the separate app-signing key (§2). |
| **DONE 2026-08-26** | The uploaded artifact | `app-release.aab` from `clean` on `68c85a3` (recorded in `0b2312f`). 35,624,186 bytes, SHA-256 `37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b`. Hash and certificate independently re-verified against the file on disk. |
| **DONE 2026-08-26** | Tests/lint correspond to this artifact | JVM 771/771, instrumented 218/218 (both 0 skipped), lint 0 errors. No file under `app/` has changed since `68c85a3`, so the uploaded bundle still matches HEAD's code. |
| **DONE 2026-08-26** | Privacy policy live and linked | `https://morfildor.github.io/Just-the-Carbs/privacy-policy.html`, GitHub Pages from `docs/` on `main`. `SettingsScreen` opens the same `BuildConfig.PRIVACY_POLICY_URL`, pinned by `SettingsScreenTest`. Entering it in Console is a §5a row. |
| **DONE 2026-08-26** | Store assets | *(owner)* 512×512 icon, feature graphic and real-device screenshots complete. Specs and shot list in [store-assets.md](store-assets.md). |
| **DONE 2026-08-26** | One shipping language | English only; `values-nl/strings.xml` deleted, `androidResources { localeFilters += "en" }` set. Release APK carries no language configurations. Dutch *input parsing* is retained and separate. |
| **DONE 2026-08-26** | Release CI gates | `.github/workflows/release-gate.yml` blocks on JVM (`--rerun-tasks`), instrumented, lint and the release build, with 0-skipped assertions from JUnit XML and the R8 privacy barriers as build steps. |
| **DONE 2026-08-14** | Target/compile SDK | `targetSdk = 36`, `compileSdk = 37`. Google requires API 36 for new apps from 2026-08-31. |
| **DONE 2026-08-14** | Version source, fail-closed signing, dependency inventory | `branding.gradle.kts` is the sole version source; `:app:bundleRelease` refuses to produce an unsigned artifact; 226 release-runtime artifacts, 0 known vulnerabilities ([resolved-release-dependencies.md](resolved-release-dependencies.md)). |

### 1b. Must complete before production

Each item names the external authority that makes it mandatory. Nothing else blocks production.
Console form status row-by-row is §5a.

| # | Gate | Authority — why it is mandatory | Owner action |
|---|---|---|---|
| 1 | **App content forms completed** | Play requires Data Safety, Health Apps declaration, content rating, target audience, app access and ads before a production release. Internal testing does not require them all; production does. | Work §5a top to bottom. Answers are pre-written: §4a (health), §4b (Data Safety), §4c (rating/audience/access/ads). |
| 2 | **Signed §44 assessment** | MDR Article 2(1) — the manufacturer must be able to produce its qualification record. The assessment is **written and its conclusion is complete**; only the signature block (§9) and its name/date fields are blank. | Sign and date `docs/regulatory-qualification-assessment.md` §9. A signature, not a review project. |
| 3 | **Store listing wording matches §44 §7.1** | The §44 conclusion is **conditional** on the marketing constraints in its §7.1; publishing copy that breaches them invalidates the conclusion the release relies on. | Read the final listing against §7.1 before publishing. [play-store-listing.md](play-store-listing.md) was already claim-audited against it (checklist row E1). |
| 4 | **Countries/regions set to EU** | Not a Play requirement in itself — it is what keeps the signed §44 assessment coextensive with the markets served. | Set distribution to EU countries only. |
| 5 | **Play-delivered smoke test** | Not a Play requirement. Retained as the one genuine defect gate: it is the first exercise of the *Play-delivered* install rather than a locally built APK. | Run §8a's ten steps from the tester link. |
| — | **Organization account** | Conditional on the health-declaration outcome (§4a, **owner decision**), never on §44. | Applies only if the declaration lands in a health category — an external blocker from that moment, not before. Distinguish required-now from required-after-a-stated-date from not-applicable. |

**EU-only scope for v1.** The assessment covers the European Union (Annex A.1). Restricting v1
distribution to EU countries in Play Console keeps the signed assessment coextensive with the
markets served, and removes UK/US assessment from the release path entirely. Widening distribution
later reopens that question — that is a v2 decision, not a v1 blocker.

### 1c. Optional after launch

None of these is required by Google Play or by law for an EU-scoped v1. They were previously carried
as blockers; each is demoted here with the reason.

| Item | Why it is not a blocker |
|---|---|
| **Native debug symbols not uploaded** | Play accepted the bundle with this warning. It affects only the readability of native crash stack traces in Play Console — ML Kit's native libraries, not this app's Kotlin code, which is symbolicated via `mapping.txt` already. **Do not rebuild v1 to clear it**; adding the symbol file changes the artifact, which means a new version code. Fold it into the next build that ships for another reason — `versionCode 2` / `1.0.1` is open, so it can be picked up there. |
| Temporary listing name `app.justthecarbs (unreviewed)` | Expected until app setup and review complete. Not a defect and not fixable by rebuilding. |
| Independent regulatory review (checklist A2) | The MDR makes the **manufacturer** responsible for qualification, so a manufacturer self-assessment is the legitimate and expected record. Independent review is the assessment's own conservative recommendation (Annex A.6), not a legal precondition for a non-device. Worth obtaining; not worth holding the release for. |
| Non-EU market assessment (checklist A5) | Moot while distribution is EU-only. Becomes mandatory only if the owner adds UK/US markets. |
| Restore-tested keystore backup | Copying the keystore takes minutes (§2a) and is strongly recommended. It is not a Play requirement, and after Play App Signing enrollment — **now complete** — a lost upload key is recoverable through Google's upload-key reset. A restore *drill* is not a production blocker. |
| Full 20-item device sweep | Superseded for this release by §8a. Run the full sweep only if the smoke test fails or code changes. |
| Regulatory assessment in public Git history | Both files are untracked today; the exposure is historical. Remediation: [git-history-remediation.md](git-history-remediation.md). Owner action on its own timeline. |
| Project licence | No Play requirement. "All rights reserved" is the default and is a valid disposition. |
| Dutch store listing | Optional localisation; the app ships English only. Listing localisation does not require translating the app. |

## 2. Production upload key and signed AAB

**Two different keys — do not conflate them.**

| Key | Who holds it | Role |
|---|---|---|
| **Upload key** | The developer, here `C:\secure\JustTheCarbs-upload.jks` | Signs the bundle that is *uploaded* to Play. |
| **App-signing key** | **Google**, generated and held under Play App Signing | Signs the APKs Play *delivers* to devices. |

The upload key **never becomes** the app-signing key. Google generates the app-signing key on top of
it at enrollment. Play requires Android App Bundles and Play App Signing for new apps, so this is the
arrangement for Just the Carbs.

Consequence for key loss: once Play App Signing enrollment has happened, a lost or compromised
**upload** key is recoverable through Google's documented upload-key reset process. A self-managed
**app-signing** key outside Play App Signing is the one whose loss makes updates impossible — which
is not this app's arrangement. Backup remains strongly recommended regardless.

### 2a. Keystore backup — one instruction, not a project

The keystore exists at `C:\secure\JustTheCarbs-upload.jks`. Do this once:

1. Copy `JustTheCarbs-upload.jks` to **two** independent secure locations (for example an encrypted
   external drive and encrypted cloud storage). Never commit it; `keystore.properties` is gitignored.
2. Store the keystore and key passwords in a password manager, alongside the alias
   `justthecarbs-upload`.

Optional verification that a copy is intact and openable:

```powershell
& 'C:\atools\jdk-21.0.12+8\bin\keytool.exe' -list -v `
  -keystore '<path-to-the-copy>' -alias 'justthecarbs-upload'
```

The printed SHA-256 must equal `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5`.

**This does not block internal or closed testing.**

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

`NOT FOR PLAY` in the DN would mean the keystore in use is the old disposable one. It is not: as of
2026-08-26 `keystore.properties` points at the owner's real upload keystore and the check reads
`C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`. Keep reading the DN on every release
build — that is the check that caught the disposable key.

One recorded deviation from the recipe above:

- The key is **2048-bit RSA**, not the 3072 bits the `keytool` command specifies. 2048 meets
  Google's documented minimum, so this is acceptable and is **not** worth regenerating — but the
  upload key is fixed once published, so decide now if a larger key is wanted.
- `app/build.gradle.kts` is unchanged: the fail-closed guard and `signingConfigs` are exactly as
  committed, and Android Studio picked up the real key through the existing `keystore.properties`
  path. No signing logic was edited to make this build succeed.

### 2b. The shipped artifact

**This bundle is uploaded and live on internal testing.** Every value below was re-verified against
the file on disk on 2026-08-26 (`sha256sum` and `keytool -printcert -jarfile`), not copied forward
from an earlier pass — and Play has since accepted the same artifact.

| Field | Value |
|---|---|
| Source commit | `68c85a3` (build recorded in `0b2312f`); no file under `app/` has changed since |
| Build | `:app:bundleRelease` from `clean` |
| Version code/name | `1` / `1.0.0`, package `app.justthecarbs` |
| Path | `app/build/outputs/bundle/release/app-release.aab` |
| AAB byte size | 35,624,186 bytes (33.97 MiB) |
| AAB SHA-256 | `37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b` |
| Upload certificate SHA-256 | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` |
| Signer DN | `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`, valid 2026-08-26 → 2051-08-20 |
| Keystore backup | Recommended, not blocking — §2a |

**Do not rebuild or re-upload this artifact** — see §2c. It corresponds to the committed code and is
on an active Play track; a replacement ships as `versionCode 2`, which is already open.

An earlier bundle (35,689,027 bytes, SHA-256 `00876FA9…BBB4A2`) was built from an uncommitted tree
on the same day and is **superseded**. Do not upload it.

`jarsigner`'s "self-signed certificate" and "no timestamp" warnings are expected and correct for an
Android upload key. They are not defects.

### 2c. `versionCode 1` is final; `versionCode 2` is open

`versionCode 1` is **published to testers and is not to be rebuilt or re-uploaded.** Play refuses a
duplicate version code, and that artifact has already proven bundle format, upload signing and Play
App Signing acceptance. Leave it alone.

Work continues on **`versionCode 2` / `1.0.1`, which is already open** in `branding.gradle.kts`.
That number is claimed once: do not bump it again while 1.0.1 is unshipped, and do not open a
`versionCode 3` section. Changes go into 1.0.1's section in `CHANGELOG.md`.

**Opening `versionCode 2` was not itself a decision to ship.** Building a release AAB and uploading
it is a separate, deliberate act the owner asks for — never a step in an ordinary development pass,
and not warranted by documentation, the native debug-symbols warning (§1c), the temporary
`(unreviewed)` listing name, dependency upgrades, cosmetics or refactors. 1.0.1 ships when the owner
decides its accumulated changes are worth an upload.

When that upload does happen: rebuild from a **committed** tree, verify the signer DN (§2a) and the
R8 privacy barriers (§7), record the new artifact's hash and size in §2b, and copy 1.0.1's
`CHANGELOG.md` section verbatim into `docs/version-history.md` — **only after Play accepts it**.

### 2d. Patch notes are part of the upload, not an afterthought

Two documents, and they are not interchangeable:

- **`CHANGELOG.md`** (repo root) — Unreleased work plus the version currently on a track.
- **`docs/version-history.md`** — the append-only archive: one section per uploaded `versionCode`
  with its hash, size, signer and dates, so a tester report can be matched to one exact artifact.

At upload time, in order:

1. Rename `CHANGELOG.md`'s **Unreleased** heading to the version being shipped, and start a fresh
   empty Unreleased above it.
2. Write the **Play Store release notes** block for that version — the *What's new* text. It is
   deliberately much less granular than the change list: ≤500 characters, small fixes grouped into
   one line, described as the user experiences them. **No health claim, no mention of diabetes** —
   §44 §7.1 binds this field exactly as it binds the store listing. Full rules are at the top of
   `docs/version-history.md`.
3. After the upload is accepted, copy the version's change list and Play notes verbatim into
   `docs/version-history.md` and add the artifact table (hash, byte size, signer, track, date).

Do not edit a version's entry once it is in the archive. If something recorded there turns out to
be wrong, add a dated note beneath it saying so.

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
| Pages hosting | [GitHub Pages setup](https://docs.github.com/en/pages/getting-started-with-github-pages) and [publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site). Resolved: the repository is **public** and Pages serves the policy from `docs/` on `main`. The earlier private-repo constraint no longer applies. |
| EU MDR | [Regulation (EU) 2017/745](https://eur-lex.europa.eu/eli/reg/2017/745/oj?locale=en), [MDCG 2019-11 rev.1](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en), and [MDCG 2021-24 rev.1](https://health.ec.europa.eu/latest-updates/update-mdcg-2021-24-rev1-guidance-classification-medical-devices-april-2026-2026-04-20_en). These are inputs to the owner assessment, not a determination here. |

## 4. Play Console answers

Copy-paste ready. Every answer is derived from the shipped implementation, not from intent.

### App details and access

| Play field | Answer | Basis |
|---|---|---|
| App name | `Just the Carbs` | 14 characters; centralized branding. |
| Default language | English | The app ships English only (`localeFilters += "en"`). |
| App or game | App | Utility/calculator behavior. |
| Free or paid | Owner decision; no paid features exist | Price is an owner commercial decision. |
| Contains ads | **No** | No ad SDK or ad surface. |
| App access | **All functionality is available without login or membership** | No account or authentication exists. Camera can be denied; manual entry remains available. |
| Government app | **No** | No government affiliation. |
| Financial features | **No** | No payments, trading, lending, or financial data. |

### 4a. Health Apps declaration: **OWNER DECISION REQUIRED**

**This document does not select the category.** Just the Carbs calculates carbohydrate amounts for
food portions and for a meal total, which may fall close to Google Play's **Nutrition and Weight
Management** category. That is a judgement about Google's platform policy applied to this app's
actual behaviour, and it is the owner's to make.

**Do not equate "not a medical device" with "not a Google Play health app."** §44 is an EU
medical-device qualification under MDR Article 2(1); the Health Apps declaration is a Google Play
platform classification against Google's own categories. Different authorities, different questions —
neither answer follows from the other. Every published app completes the declaration, including apps
that declare no health feature.

**The facts to decide from — retain whichever answer is chosen, with this basis, alongside the
submitted form.** The app multiplies a carbohydrate-per-100 g/ml figure from food packaging by a
portion the user types, and can total several such portions into one current meal.

| Category | Assessment | Basis |
|---|---|---|
| Medical Device Apps | Does not appear to apply | §44 assessment concludes: not a medical device under MDR Article 2(1). Note this settles the *MDR* question, not the Play one. |
| Clinical Decision Support | Does not appear to apply | No professional decision support, no drug-dosage calculation. Adjacency to a separate dosing app the user may own is not a feature of this app. |
| Diseases and Conditions Management | Does not appear to apply | No feature specific to any disease or condition; no condition-management workflow. |
| **Nutrition and Weight Management** | **OWNER DECISION — the category this app sits closest to** | *Toward the category:* the app computes carbohydrate amounts for food portions and produces a meal carbohydrate total. *Away from it:* Google's category describes dietary-intake tracking, meal planning, diet/weight management and dietary goals; this app has no diary, no daily totals, no longitudinal intake history, no goals, no weight, and holds exactly **one** current meal with no name or date (`MealStore` carries no meal id, so past meals are structurally impossible). Both readings are stated because the decision is genuinely the owner's. |

Answer against Google's **current** form wording at submission — the categories and their
descriptions change, and the form text governs over any summary here.

**Do not under-declare to avoid account requirements, and do not over-classify functionality that is
not present.** Both are real failure modes.

**Consequences that follow from the answer, not from §44:**

| If the declaration lands… | Then |
|---|---|
| Outside any health category | No Organization-account requirement and no Play disclaimer requirement follow. |
| In a health category | Google requires health-app providers to register as an **Organization** — an external blocker from that moment. The non-regulated health-app branch also requires Google's exact wording ("not a medical device and does not diagnose, treat, cure, or prevent any medical condition") in the full description. That text is compatible with §44 §7.1, which independently forbids describing the app as being *for* diabetes. |

Where an account-type requirement applies, distinguish **required now** from **required only after a
stated policy effective date** from **not applicable**, and record which it is with the date read.

Privacy-policy requirement is satisfied either way: the URL is live, public, and linked in both the
app and (at submission) Play Console.

### 4b. Data Safety — answer sheet

Full evidence in [google-play-data-safety.md](google-play-data-safety.md). This is the sheet to copy.

**Form-level:**

| Question | Answer |
|---|---|
| Does the app collect or share any required user data types? | **Yes — collects** |
| Is all collected data encrypted in transit? | **Yes** |
| Do you provide a way for users to request data deletion? | **No server-side mechanism.** Settings clears local data; uninstalling removes it. |
| Account creation | **No** |
| Independent security review | **No** |

**Data types — declare exactly these four as collected, and nothing else:**

| Data type | Collected | Shared | Optional? | Purpose | Why |
|---|---|---|---|---|---|
| App activity → **In-app search history** | Yes | No¹ | Optional | App functionality | Product-name search text is sent to Open Food Facts. Collection means *transmitted off-device*, so local non-retention is irrelevant. |
| App activity → **App interactions** | Yes | No | Required with ML Kit use | Analytics | ML Kit's disclosure lists feature initialization, detection, model download and resource-release events. |
| App info and performance → **Diagnostics** | Yes | No | Required with ML Kit use | Analytics | ML Kit collects device/app info, performance metrics, API configuration, I/O size, feature version, error codes. |
| **Device or other IDs** | Yes | No | Required with ML Kit use | Analytics | ML Kit collects per-installation identifiers for bundled features. |

¹ *Shared = No* relies on Google's user-initiated-action exception: the user opens Search by name,
sees that it searches Open Food Facts, and types the query to request those results. Keep a
screenshot of that disclosure.

**Declare NO for everything else**, specifically: Personal info (name, email, user IDs, address,
phone); Financial info; Health and fitness; **Photos and videos**; Audio; Files and docs; Calendar;
Contacts; Messages; **Location** (precise or approximate); Web browsing history; Purchase history.

Three that invite a wrong answer:

- **Photos and videos → No.** A label capture writes one JPEG to the app's *private cache* for
  on-device OCR and deletes it after processing. It is never transmitted, so nothing is *collected*.
  The form asks about collection and sharing, not local temporary files.
- **Health and fitness → No.** Carbohydrate figures are food-label facts about a product, not a
  user health record, and none is transmitted.
- **Location → No.** No location permission, API or feature exists. Open Food Facts and Google
  necessarily receive a network address, as every network app does; declare Approximate location only
  if a primary source establishes that request IP is retained and used to infer location. None was
  found. Record whatever source is relied on at submission.

Do **not** answer "No data collected" — ML Kit's own disclosure closes that option.

### 4c. Target audience

Select **18 and over only**. The app is not designed for children, has no child-oriented characters,
education, rewards, or marketing, and the listing must not introduce any. Select no child age group.
Revisit only if the owner intentionally redesigns the product for minors; that would trigger a
separate Families-policy assessment.

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

## 5. Play Console — remaining completion items

### 5a. Status matrix

Work this top to bottom. Every answer is pre-written in §4; nothing here needs new analysis.
**REQUIRED BEFORE PRODUCTION** means Play blocks a production release without it — not an internal
preference.

| Console item | Classification | Action / evidence |
|---|---|---|
| App bundle uploaded and accepted | **DONE** | `versionCode 1` live on internal testing; Play accepted bundle and signature. |
| Play App Signing enrollment | **DONE** | Completed at upload; Google holds the app-signing key. |
| Internal testing track | **DONE** | Active, available to internal testers, released 2026-08-26. |
| Main store listing (name, short/full description, icon, feature graphic, screenshots) | **REQUIRED BEFORE PRODUCTION** | Copy from [play-store-listing.md](play-store-listing.md); assets complete (§5b). Read against §44 §7.1 before saving. |
| Privacy policy URL | **REQUIRED BEFORE PRODUCTION** | Paste `https://morfildor.github.io/Just-the-Carbs/privacy-policy.html` into App content → Privacy policy. Page is live and linked in-app (§6). |
| App access | **REQUIRED BEFORE PRODUCTION** | Select **All functionality available without special access**. No account, login or gated feature exists. |
| Ads declaration | **REQUIRED BEFORE PRODUCTION** | **No ads.** No ad SDK or ad surface ships. |
| Content rating questionnaire | **REQUIRED BEFORE PRODUCTION** | Category **Utility/Productivity/Other**; answers in §4c. Record the IARC result Play returns — do not pre-claim a rating. |
| Target audience and content | **REQUIRED BEFORE PRODUCTION** | **18 and over only**; no child age group (§4c). |
| Data Safety | **REQUIRED BEFORE PRODUCTION** | Enter §4b verbatim. |
| Health Apps declaration | **REQUIRED BEFORE PRODUCTION — OWNER DECISION** | §4a sets out the facts on both sides; it does **not** choose. Closest category is **Nutrition and Weight Management**. Decide against Google's current form wording and retain the written basis with the submission. |
| Government apps declaration | **NOT APPLICABLE** | No government affiliation. |
| Financial features declaration | **NOT APPLICABLE** | No payments, lending, trading or financial data. |
| News app declaration | **NOT APPLICABLE** | Not a news app; no editorial content. |
| Health/medical *content* policy extras (Google's disclaimer text, regulatory proof) | **CONDITIONAL** on the §4a declaration outcome | Required only if the declaration lands in a health category; not applicable otherwise. |
| Countries/regions | **REQUIRED BEFORE PRODUCTION** | Set **EU countries only** — keeps the signed §44 assessment coextensive with markets served. |
| Pricing (free/paid) | **REQUIRED BEFORE PRODUCTION** | **Free.** No billing library or purchase UI ships. Free→paid is irreversible; paid→free is not. |
| App category and contact details | **REQUIRED BEFORE PRODUCTION** | Category *Health & Fitness* or *Food & Drink* is an owner choice; a Play **store category** is not the Health Apps *declaration* and does not by itself trigger health-app requirements. Support email `albinogorillassupport@gmail.com` is already the app's OFF contact. |
| **Closed-testing precondition (12 testers / 14 days)** | **IN PROGRESS — clock running** | Play requires some **personal** developer accounts created from Nov 2023 onward to run a **closed test with at least 12 testers for 14 continuous days** before applying for production access. **The closed track is active and 12+ testers are opted in**, so if the requirement applies to this account it is being satisfied now — there is nothing left to create or enrol. Internal testing never counted toward it, which is exactly why the closed track was needed. Remaining work is elapsed time and keeping testers opted in; **Console's Production track is the authority on the days remaining**, not this file. Still the longest pole. |
| Native debug symbols | **OPTIONAL** | See §1c. Do not rebuild v1 for it. |

### 5b. Store listing and assets

The claim-reviewed copy is in [play-store-listing.md](play-store-listing.md). Current limits are:

| Field | Limit | Draft status |
|---|---:|---|
| App name | 30 characters | `Just the Carbs` — within limit. |
| Short description | 80 characters | Draft is within limit; recount after any policy wording change. |
| Full description | 4,000 characters | Draft is within limit before any conditional health disclaimer; recount after insertion. |

Upload assets — **complete**, held by the owner:

| Asset | Current Play requirement | Status |
|---|---|---|
| Store icon | 512×512, 32-bit PNG with alpha, ≤1,024 KB | **DONE 2026-08-26** *(owner)* |
| Feature graphic | 1024×500 JPEG or 24-bit PNG, no alpha | **DONE 2026-08-26** *(owner)* |
| Screenshots | At least 2 total; JPEG/24-bit PNG, no alpha; each side 320–3,840 px and long side no more than twice short side | **DONE 2026-08-26** *(owner)* — real-device captures. |
| Tablet screenshots | Not a universal publication minimum | Optional; add real captures only if tablet quality is claimed. |
| Promo video | Optional | Skip for first release. |

Before publishing, read the final listing text against §7.1 of the §44 assessment (gate 5 in §1b).
[play-store-listing.md](play-store-listing.md) was already claim-audited against it.

## 6. Privacy policy — live

| Field | Value |
|---|---|
| Public URL | `https://morfildor.github.io/Just-the-Carbs/privacy-policy.html` |
| Hosting | GitHub Pages from `docs/` on `main`; the repository is **public**, so no paid plan or separate repository is needed. |
| In-app link | Settings → About opens `BuildConfig.PRIVACY_POLICY_URL` — the same URL, pinned by `SettingsScreenTest`. |
| Content | Corrected 2026-08-26 so "Your control" describes what each clear action really does; committed and published. |
| Play Console | *(owner — enter the URL at submission)* |

Re-verify in a logged-out browser at submission. No rebuild is needed: the URL in the app has not
changed.

## 7. Technical evidence — release candidate

### 2026-08-28 — `versionCode 2` / `1.0.1`, built for closed testing (commit `45f3dd9`)

**Built, verified, and awaiting upload.** This is the candidate for the closed track. It becomes
history in [`version-history.md`](version-history.md) only once Play accepts it (§2c).

| Check | Result |
|---|---|
| Release AAB | **Built from `clean` on the committed tree at `45f3dd9`.** 35,626,125 bytes, SHA-256 `8c4e6da7998b81a38fbb23234b008a8088ab57149d0d0f6a8b3e146c4d7bfd30`. |
| Signature | **UPLOADABLE KEY** — signer `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`, SHA256withRSA, 2048-bit, valid 2026-08-26 → 2051-08-20, cert SHA-256 `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — **identical to the key that signed `versionCode 1`**, which is what lets Play accept it as an update. Read with `keytool -printcert -jarfile`; `apksigner` cannot read an AAB. |
| Identity in the merged release manifest | `package="app.justthecarbs"`, `versionCode="2"`, `versionName="1.0.1"`. |
| JVM tests | **PASS — 801/801**, 0 failures, 0 errors, 0 skipped (`--rerun-tasks`, counted from JUnit XML). |
| Instrumented tests | **PASS — 218/218**, 0 failures, 0 ignored, in **one whole-suite run** (15m25s), counted from instrumentation status codes. Taken after the last code change in this version. |
| Lint | **PASS — 0 errors**, 41 advisories (unchanged baseline). |
| R8 privacy barriers | **PASS** — `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport` and `OcrDiagnosticsReport` absent from `mapping.txt`. Safety classes retained as real classes: `UnitMarkerFilter`→`z34`, `CandidateProvenance`→`v00`, `CarbCandidate`→`f30`, `PackageBasisResolver`→`cl2`. |
| Release manifest providers | **PASS** — zero `FileProvider` matches, so the debug evidence provider does not ship. |
| Exported components | **PASS** — one of ours (`MainActivity`, the launcher) plus AndroidX's permission-guarded `ProfileInstallReceiver`. Same as `versionCode 1`. |
| Permissions | CAMERA, INTERNET, ACCESS_NETWORK_STATE (transitive via ML Kit, disclosed) — unchanged from `versionCode 1`. |
| `debuggable` / `allowBackup` | **PASS** — not debuggable; `allowBackup="false"`. |
| Locale configs in the bundle | **PASS** — none; English-only, so the `localeFilters` restriction still holds. |
| OSV dependency scan | **PASS 2026-08-28** — 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query positive. |
| Release APK | **NOT BUILT** — bundle-only, as for `versionCode 1`. |
| Play acceptance | **PENDING** — not yet uploaded. |
| Play-delivered smoke test on this build | **PENDING** *(owner — §8a)* |
| Physical-device verification | **NOT DONE** — unchanged standing gap. The crop-drag fix, the scanner disposal guard and the latency work remain emulator-and-JVM-only. |

### 2026-08-26 — the shipped artifact (commit `68c85a3`, recorded in `0b2312f`)

| Check | Result |
|---|---|
| Release AAB | **Built from `clean` on the committed tree.** 35,624,186 bytes, SHA-256 `37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b` — re-verified against the file on disk. |
| Signature | **UPLOADABLE KEY** — signer `C=NL, L=Haarlem, O=JustTheCarbs, OU=Release, CN=Tunc Bilen`, SHA256withRSA, 2048-bit, valid 2026-08-26 → 2051-08-20, cert SHA-256 `1E:21:23:F3:…:C4:F5`. jarsigner's "self-signed" and "no timestamp" warnings are expected for an Android upload key and are not defects. |
| Identity in the packaged manifest | `package="app.justthecarbs"`, `versionCode="1"`, `versionName="1.0.0"`. |
| JVM tests | **PASS — 771/771**, 0 failures, 0 errors, 0 skipped (`--rerun-tasks`, counted from JUnit XML). |
| Instrumented tests | **PASS — 218/218**, 0 failures, 0 errors, 0 skipped. |
| Lint | **PASS — 0 errors.** |
| Code corresponds to this artifact | **PASS** — no file under `app/` has changed since `68c85a3`; only documentation has. Documentation changes do not require a rebuild. |
| R8 privacy barriers | **PASS** — `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`, `ScanTrace` absent from `mapping.txt`; `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate`, `PackageBasisResolver` retained as real classes. |
| Release manifest providers | **PASS** — only the ML Kit init provider and `androidx.startup`; zero `FileProvider`/`evidence` matches, so the debug evidence provider does not ship. |
| OSV dependency scan | **PASS** — 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query positive. |
| Release APK | **NOT BUILT** — bundle-only build. Use `keytool -printcert -jarfile` on the AAB, not `apksigner`. |
| Play acceptance | **PASS 2026-08-26** — uploaded and accepted; live on internal testing. Bundle format and upload signature validated by Play itself. |
| Native debug symbols | **NOT UPLOADED** — Play warned; accepted anyway. Optional, non-blocking (§1c). |
| Play-delivered smoke test | **PENDING** *(owner — §8a)* |
| Keystore backup | *(owner — §2a; not blocking)* |

### Historical — superseded passes

*The two tables below record earlier builds. They are kept as a dated record and describe artifacts
that are **not** the release candidate. The disposable-key signatures they mention were replaced by
the real upload key on 2026-08-26.*

#### 2026-08-15 re-verification (commit `6de524e`, Light-theme default)

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

## 8. Deployment sequence

### DONE — internal, then closed testing *(historical)*

1. ~~Create the app in Play Console; confirm Play App Signing enrollment.~~ **Done 2026-08-26.**
2. ~~Upload `app-release.aab` to the Internal testing track.~~ **Done** — Play accepted bundle and
   signature.
3. ~~Add testers and roll out.~~ **Done** — Active, available to internal testers.
4. ~~Promote to the Closed testing track and enrol testers.~~ **Done** — the **same artifact**
   (`versionCode 1`, SHA-256 `37be0232…c7e604b`) progressed from internal to closed; **12+ testers
   opted in** and the testing period is running. One artifact, two tracks — not two releases.

### NOW — the two things in flight

5. **Install from the tester link on the Samsung device and run §8a.** This is the next technical
   action, and it tests the **Play-delivered** install — not a locally built APK. That distinction is
   the point: it exercises the artifact as Google repackages and delivers it.
6. **Let the closed-testing period run.** Nothing to create or enrol; the clock is elapsing. Keep
   testers opted in — dropping below 12 is the one thing that can cost days. Console's Production
   track shows the remaining time and is the authority on it.

### THEN — production submission

7. Complete every **REQUIRED BEFORE PRODUCTION** row in §5a. Export the submitted answers as evidence.
8. Sign and date the §44 assessment (§9 of that document).
9. Read the final listing against §44 §7.1.
10. Set countries/regions to **EU only**.
11. Review the Play pre-launch report, then promote to production.

### 8a. Play-delivered device smoke test

**Install through the tester link** (internal or closed — both deliver the same `versionCode 1`
artifact) — do not sideload a local APK; the point is to exercise what Play delivers. Engineering
suites are green and no code changed in the artifact under test, so this is a defect gate, not a QA
campaign.

| # | Step | Pass condition |
|---|---|---|
| 1 | Install via the Play tester link | Installs and appears in the launcher |
| 2 | Launch | Opens to Home, no crash |
| 3 | Barcode scan | Camera opens; a real product barcode is detected |
| 4 | Product lookup | Open Food Facts returns the product; name and per-100 figure shown |
| 5 | Portion calculation | Typing a portion yields the expected grams |
| 6 | Nutrition-label scan | Returns a value **or an honest refusal** — never a confident wrong number |
| 7 | Confirm / Correct | The verify path applies or rejects a value as shown |
| 8 | Search by name | A name search returns hits; selecting one loads the product |
| 9 | Cached / recent product | A previously used product reappears on Home and recalculates; still works with networking off |
| 10 | Restart | Force-stop and relaunch; saved products and the current meal survive |

All ten pass → record **PLAY-DELIVERED DEVICE SMOKE TEST: PASS** with the date and device, and treat
hardware verification as complete.

A failure blocks the **production promotion**, not the tester tracks. It would also be a reason to
push the open `versionCode 2` sooner rather than later (§2c) — the fix would land there, since
`versionCode 1` is not rebuilt. Diagnose with the relevant part of
[manual-qa.md](manual-qa.md); the historical 20-item sweep in
[release-closure-device-verification.md](release-closure-device-verification.md) is not required
unless a failure needs it.

## 9. Explicitly unverified

Facts, not blockers.

- **The §8a Play-delivered smoke test has not been run.** The app has been exercised on the Samsung
  device with real-device screenshots taken; what is unconfirmed is the Play-delivered install of
  this exact build.
- Play review has not happened — status *Not reviewed*, listing name temporary.
- No production Play form has been submitted yet (§5a).
- The keystore has no restore-tested backup (§2a). Play App Signing enrollment is complete, so a lost
  upload key is recoverable by reset.
- Countable portions against a real Open Food Facts `serving_size` response remain fixture-only.
- Open Food Facts' retention/use of search terms and request IP addresses was not established from a
  primary source; §4b answers conservatively and flags confirmation at submission.
- No legal or Open Food Facts licence conclusion is made by this engineering document. The §44
  assessment is the owner's own record and is unsigned until the owner signs it.
