# Repository-review remediation — 2026-09-08

The owner authorized fixes and a push after the review. All nine findings have implementation
changes in 1.0.6; the existing shutter-haptic patch remains included. The detailed before/after
record is in [CHANGELOG.md](../../../CHANGELOG.md#106-versioncode-7--in-development-not-uploaded).

| Review finding | Repair |
| --- | --- |
| Platform SQLite UPSERT compatibility | Insert-or-ignore plus increment in a Room transaction; API 26/29 CI database jobs |
| Direct-carb recalculation | Active-mode calculation clears the inactive result |
| Changed nutrition basis reuses old quantities | Clear dependent history/amounts; reject incompatible units and pending history snapshots |
| Remote values lose their basis | Room v8 stores original/latest bases; unknown legacy pairs cannot be restored |
| Committed meal reported as failed | Separate optional history failure and capture usage before suspension |
| Missing retained bitmap bypasses gates | Recovery without forwarding the old parser proposal |
| Barcode manual-entry and exit races | Pause/generation/closed guards plus owned camera cleanup |
| Late crop target ignored | Selection keyed by bitmap and initial target; connected regression |
| Evidence work blocks UI | Background export and queued crop diagnostics; guarded taps and explicit failures |

Validation: 1890 JVM tests passed; lint has 0 errors and 22 warnings; debug APK assembled.
20 targeted API 36 connected tests passed (migration, DAO and crop target). API 26/29 jobs are
configured but were not run locally. Phone testing is pending; see manual QA §§38–39.

The archived probe source and failing XML in this directory are **pre-fix evidence**, retained with
the original review. Permanent regression tests are under app/src/test. The original review's
line numbers refer to its recorded commit and are not current source locations.

The nine pre-existing design-handoff deletions were left outside this fix commit. No release build
or Play upload was performed.
