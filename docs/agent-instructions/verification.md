# Verification and release

## Toolchain

Use the checked-in Gradle wrapper. The workstation bootstrap JDK is under `C:\atools\jdk-21.0.12+8` and Android SDK under `C:\atools\sdk`; reuse installed tools after checking paths.

Read `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`, `gradle/libs.versions.toml`, and `app/build.gradle.kts` before diagnosing toolchain issues. At authoring, CI selects bootstrap JDK 21 but the checked-in daemon criteria request JVM 25. Do not assume `JAVA_HOME` alone determines the daemon JVM or downgrade the criteria to match historical notes. Use `.\gradlew.bat --version` to inspect the effective runtime when needed.

## Choose checks by change

| Change | Verification |
|---|---|
| Domain, parser, repository, or ViewModel behavior | Relevant JVM regression tests, then `:app:testDebugUnitTest` for implementation completion |
| Android/Compose implementation | `:app:lintDebug` and `:app:assembleDebug`; instrumented behavior tests for affected interactions |
| Room schema or DAO behavior | Instrumented DAO/migration tests; exported schema review; no Robolectric |
| OCR/camera pipeline | Pure evidence/parser tests plus real-image instrumented regressions; physical-device checks for camera timing, focus, glare, and haptics |
| Shrinking, manifest, dependencies, or release work | Applicable minified release and privacy gates in `.github/workflows/ci.yml` and `.github/workflows/release-gate.yml` |
| Documentation only | Check facts, referenced paths, and diff; no Android build needed unless documenting a newly measured build result |

Commands run from the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'app.justthecarbs.<package>.<TestClass>'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Replace the test selector with an existing class. Instrumentation requires a booted emulator or connected device; check `C:\atools\sdk\platform-tools\adb.exe devices` first. The recorded local AVD is `carbscan`. Instrumentation may uninstall the app afterwards; reinstall before manual UI checks. Avoid destructive test/install actions on a phone containing user data without authorization.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. A Desktop copy is a convenience, not proof of freshness: if delivering one, copy the artifact from the successful build and verify it matches.

For device QA use [manual QA](../manual-qa.md) and [physical-device scanner QA](../physical-device-scanner-qa.md). Emulator success does not establish physical-camera reliability or distinguishable vibration effects. Scroll off-screen Compose controls into view before interacting in tests.

Report command outcomes from the current run, including skipped or blocked checks. Missing mandatory OCR fixtures must fail rather than silently skip. Keep sanitized real-photo fixtures intact; use `tools/derive-ocr-fixtures.md` for provenance.

## Versioning and release

- Read `CLAUDE.md`'s **Version and track state** section, `branding.gradle.kts`, and [version history](../version-history.md) before changing versions or describing track status.
- Multiple changes accumulate under one open development version. Once a versionCode reaches Play it is permanently spent, even if withdrawn or never approved. Documentation-only changes do not bump versions.
- Release packaging fails without complete signing material. Never bypass this gate or substitute debug signing for a production artifact. CI uses a disposable test key; its artifacts are not uploadable production builds.
- For an authorized release, follow [release readiness](../play-release-readiness.md), the current CI gates, and the release record. Verify signer identity and artifact hash, not just the filename. Inspect R8 removal of evidence/diagnostic classes and absence of the evidence FileProvider in the release manifest.
- Run `bash tools/dependency-scan.sh` in a Bash environment for the shipped dependency scan when applicable. Treat previous scan results as dated evidence.
- Keep signing material, local configuration, full-frame photos, and private correspondence out of this public repository. Do not print credentials while checking signing readiness.
- A local build is not an upload, approval, or release. Record each state only when evidence establishes it; publishing requires authorization covering that action.
