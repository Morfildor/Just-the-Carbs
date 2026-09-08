# Just the Carbs

Native Android utility: find or scan food, resolve a portion, and calculate carbohydrate grams. Preserve this product boundary. Supporting navigation, settings, diagnostics, persistence, and infrastructure may change when needed, but do not introduce insulin dosing, diet tracking, or food-diary behavior.

## Start here

- Check `git status --short` before editing. This repository often contains ongoing work; preserve unrelated edits, deletions, and untracked files.
- Inspect the current branch, HEAD, recent relevant commits, and affected call sites before changing behavior. Distinguish existing defects from regressions introduced by recent work.
- Read [development workflow](docs/agent-instructions/workflow.md) for every implementation task, and [architecture safeguards](docs/agent-instructions/architecture.md) before changing behavior.
- Use [verification and release](docs/agent-instructions/verification.md) to select checks and handle artifacts.
- Read [PRODUCT.md](PRODUCT.md) and [DESIGN.md](DESIGN.md) for UI work. Current theme tokens and their tests define the implemented palette.
- Search [CLAUDE.md](CLAUDE.md) for the affected feature and explicit owner decisions. It is a large dated session record, not a requirement to reread every historical pass.
- [docs/MASTER-PROMPT.md](docs/MASTER-PROMPT.md) is the original requirements brief. Later explicit owner decisions supersede older requirements; keep historical specs as history.
- For current implementation facts, inspect source, Gradle configuration, and CI rather than copying old test counts, schema versions, or build claims. For release status, read CLAUDE.md's **Version and track state** section and [version history](docs/version-history.md).

## Quick commands

Run from the repository root in PowerShell, using the checked-in wrapper:

```powershell
$env:JAVA_HOME = 'C:\atools\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\atools\sdk'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

These are this workstation's existing bootstrap paths; check them before replacing or installing tools. Gradle daemon JVM selection is separately controlled by `gradle/gradle-daemon-jvm.properties`; see the verification guide.

These commands are baseline checks, not proof that every change is verified. Select additional migration, instrumentation, release/minification, or physical-device checks when the affected behavior requires them.

## Working principles

- Keep `domain/` pure Kotlin and independently JVM-testable.
- Preserve exact arithmetic, nutrition basis, provenance, and explicit user verification. Never invent a missing nutrition value or silently replace the active calculation's inputs.
- For scanner/OCR behavior, prefer withholding a result or requesting explicit confirmation over confidently presenting an insufficiently supported carbohydrate value.
- Treat OCR verification, ambiguity handling, provenance, physical-observation identity, conflict handling, and recovery logic as safety-sensitive. Do not simplify or merge apparently duplicated paths until behavioral equivalence is demonstrated.
- In asynchronous code, explicitly consider whether older work can complete after newer work and incorrectly modify current state. Preserve cancellation, lifecycle ownership, observation identity, and single-source-of-truth semantics across coroutines, Flows, CameraX, ML Kit callbacks, and network requests.
- Before deleting apparently unused Android code, verify reachability through call sites, navigation, manifests, resources, Room, serialization, framework callbacks, reflection, and tests. Separate proven-dead code from merely suspicious or historical code.
- Prefer surgical fixes over architecture rewrites. Fix adjacent defects when they are demonstrated, relevant, and low-risk; do not expand scope for stylistic cleanup alone.
- When fixing a reproducible defect, add focused regression coverage where practical. Test behavior and failure modes rather than implementation details.
- Fix the demonstrated cause and verify the affected behavior. A green parser test is not evidence that a camera scan works on a phone.
- Do not treat a passing baseline command as sufficient when the change affects Room migrations, instrumentation-only behavior, release/minified builds, CameraX, device lifecycle, or physical scanning.
- Do not change versioning, signing, Play-track/release state, or release metadata unless the task explicitly requires it.
- Use stable dependencies only; verify proposed versions against official repositories.
- Report what changed, which checks actually ran, what remains unverified, and any known residual risks. Never reuse historical passing results as evidence for today's change.
- Keep this file a short entry point. Put task-specific detail in the linked guides and dated evidence in the existing project records.
