# Development workflow

## Pick up a task

1. Inspect the working tree and read the affected implementation and tests before editing. Do not assume existing changes are yours to commit or revert.
2. Search `CLAUDE.md` by feature or symbol for owner corrections, rejected experiments, and known device limitations. Read the relevant plan/spec in `docs/plans/` or `docs/superpowers/` when it applies.
3. State the concrete behavior to change and the evidence needed to verify it. Proceed with routine implementation choices within the request; ask only when a consequential ambiguity cannot be resolved from current instructions and project evidence.
4. Implement within the existing architecture. Keep incidental refactors and dependency changes out of a focused fix.
5. Run the applicable checks in [verification.md](verification.md), inspect the diff, and report any remaining limitations.

## Source and documentation discipline

- Later explicit owner corrections override dated assumptions. `CLAUDE.md` contains marked superseded sections; read the correction before applying an old rule.
- Read actual build configuration for toolchain, schema, and signing behavior. For example, release packaging now rejects incomplete signing even though older notes describe unsigned output.
- For UI, use `PRODUCT.md`, `DESIGN.md`, and the existing Compose components. `Theme.kt`, `AccentPalette.kt`, and palette tests govern implemented color behavior; old handoff documents are historical and may no longer exist.
- Preserve English-only displayed UI while retaining Dutch input recognition. Keep implementation terms such as OCR confidence, basis enums, and provider names out of consumer copy.
- Do not introduce medical-device qualification claims, including claims that the app is not one. Follow existing owner-approved wording; verify proposed policy or regulatory statements from authoritative sources and record source/date.
- Add user-visible changes under the existing open version in `CHANGELOG.md`. Documentation-only work does not open a version.
- Record substantial decisions, measured results, and unresolved device findings in the relevant existing documents. Keep session evidence dated and distinguish measured facts from hypotheses; avoid appending another copy of global rules.
- Preserve historical CarbScan/CarbQuick names in dated specs and plans. Current branding is centralized in `branding.gradle.kts`.

## Handoff

Describe the resulting behavior, tests actually executed, and any remaining device or release checks. Identify pre-existing failures separately from regressions introduced by the change. Stage only task-owned changes when a commit is part of the request.
