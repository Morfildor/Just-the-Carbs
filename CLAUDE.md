# CarbQuick — session context

Read this first. It records what the previous session verified so you don't re-derive it.

## What this project is

Native Android app: scan a food barcode → enter portion → read carbohydrate grams.
Full requirements are in **`docs/MASTER-PROMPT.md`** (the owner's original brief — treat it as the
requirements document; sections are referenced throughout as §N).
Design decisions are in **`docs/superpowers/specs/2026-08-13-carbquick-design.md`**.

**It does NOT calculate insulin.** Not a diet tracker. Scope discipline is a hard requirement (§2).

## Status

- ✅ Toolchain installed and verified
- ✅ Requirements verified against official docs
- ✅ Gradle build config resolves; wrapper generated
- ❌ **No application code written yet** — implementation was deliberately stopped here

## Toolchain (already installed — do NOT reinstall)

```powershell
$env:JAVA_HOME="C:\Users\tuncb\AppData\Local\Temp\claude\c--Users-tuncb-Desktop-CarbTracker\77ae85ec-8ae9-49fd-b76e-b6653755945e\scratchpad\tools\jdk\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat <task>
```

JDK 21.0.12 (Temurin, portable) · Android SDK at `C:\atools\sdk` (platform 36, build-tools 36.0.0)
· Gradle 9.7.0.

**If the scratchpad JDK is gone** (temp dirs get cleaned), re-download Temurin 21 portable zip and
update `JAVA_HOME`. Do not use `winget` — see traps below.

### Traps that cost the last session real time

1. **`winget install` hangs forever** — triggers a UAC prompt nobody can answer in a
   non-interactive session. Use portable archives only. No admin rights are available.
2. **Windows MAX_PATH** breaks SDK extraction into the deep scratchpad path. That's why the SDK
   lives at the short path `C:\atools`. `Expand-Archive` misreports this as a *missing file* error.
3. **`sdkmanager --licenses` ignores piped stdin** — write hash files into `$ANDROID_HOME\licenses`.
4. **AGP 9.x has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is now a hard
   error. Kotlin options go in the `android { kotlin { } }` block. This is already handled.
5. **KSP has its own version line** (2.3.11) that does *not* track Kotlin's (2.3.21). Don't "fix"
   the mismatch — it's intentional. Kotlin 2.4.x has no KSP build yet, so 2.4.x is not usable.

## Verified facts (checked 2026-08-13 — do not trust training data over these)

| Fact | Value |
|---|---|
| Play target API requirement | **API 36**, deadline **2026-08-31** (~2.5 weeks out) |
| AGP / Gradle / JDK | 9.3.1 / 9.7.0 / 17+ (using 21) |
| OFF read rate limit | **15 req/min/IP** — makes cache-first mandatory, not optional |
| OFF User-Agent | Mandatory, must identify the app |
| OFF data licence | **ODbL** — attribution *and* share-alike |

## Owner's four confirmed decisions

1. **ml vs g** — portion field locked to the product's own basis unit. Never assume 1 ml = 1 g.
   Offer manual per-100-g entry instead. Never dead-end.
2. **Rounding** — whole gram dominant (`31 g`), decimal legible beneath (`31.3 g calculated`).
3. **Regulatory** — build to the **stricter** standard (as if an accessory to a medical device):
   OCR never auto-accepted, verified data never silently overwritten, no value shown when
   confidence is insufficient.
4. **Backup** — `android:allowBackup="false"`. Resolves both the privacy question and the ODbL
   share-alike question.

## Working agreements

- Verify library versions against Google Maven / Maven Central before use. **Stable only** — several
  `<release>` tags currently point at alpha/RC.
- `domain/` stays pure Kotlin with zero Android dependencies — it's the safety-critical calculation
  layer and must be JVM-unit-testable without an emulator.
- Never claim something builds or passes without having run it.
- No emulator is available, so instrumented tests can be written but not executed here. Say so
  plainly rather than implying they passed.
