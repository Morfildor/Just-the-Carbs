# Version history — Just the Carbs

The permanent record of every artifact that has reached a track. One section per `versionCode`,
newest first.

This is the archive; [`../CHANGELOG.md`](../CHANGELOG.md) is the working document. The split is
deliberate:

- **`CHANGELOG.md`** answers *"what changed lately?"* — the version being worked on (open, still
  accumulating changes) plus the one currently on a track. It stays short enough to read at a
  glance, and an **open version lives only there** until Play accepts its build.
- **`docs/version-history.md`** (this file) answers *"what exactly was in the build a tester is
  reporting against?"* — it keeps the artifact identity (hash, size, signer, dates) that a change
  list has no room for and that must never be edited afterwards.

## Rules

1. A version is added here **when it is uploaded**, not when it is built. A build that never left
   the machine is not history.
2. Entries are **append-only**. Correct a later entry rather than rewriting a shipped one; if a
   recorded fact turns out wrong, add a dated note under it saying so.
3. Every entry records the SHA-256 and byte size of the uploaded artifact. That is what makes
   "which build is this tester on?" answerable months later, when several AABs with the same
   version name exist on disk.
4. `versionCode` is unique and never reused, including for a build that was rejected or rolled
   back — Play refuses a duplicate, and a reused number makes this file ambiguous.
5. Copy the version's **change list** over from `CHANGELOG.md` verbatim — the Fixed / Changed /
   Added / Internal entries and the Play notes. Do not improve the wording on the way across; the
   value of this file is that it says what was believed at the time. The artifact table, the
   verification figures and the known-gaps list are **added here**, not copied, because they are
   facts about the upload rather than about the changes.
6. Every version carries a **Play Store release notes** block — the text pasted into Play Console's
   *What's new*. See the rules below; it is the one part of an entry written for users rather than
   for us.

## Writing the Play Store release notes

This block is **not** a summary of the change list. It is a different document for a different
reader, and the granularity gap is deliberate.

- **Hard limit: 500 characters**, including whitespace, per language. Play truncates silently.
- **Three or four lines at most.** Anything a tester would not notice in normal use does not belong
  — no internal refactors, no test counts, no file names, no version-control detail.
- **Say what changed for the person using the app**, in their vocabulary: "the scanner no longer
  freezes", not "cancelled a pending capture on dispose".
- **Group the small stuff.** A pass of six defects is one line: "Various stability and reliability
  fixes." Enumerating them reads as instability rather than diligence.
- **Never restate the app's purpose or make a health claim.** §44 §7.1 forbids marketing the app
  for diabetes and forbids the owner's personal circumstances appearing in published material, and
  this field is published material. Keep it to what changed.
- **Never promise a fix you cannot verify on a device.** Wording a scanner improvement as "faster"
  is a claim; "reduces the wait after capture" describes what was done.
- Keep the block even when a version ships with nothing user-visible — write "Behind-the-scenes
  improvements." rather than deleting it, so every version has notes on record.

---

## 1.0.5 (versionCode 6)

Corrective release for the withdrawn `1.0.4` / `versionCode 5` below. Opened and built
2026-09-07. **No production code change relative to `f890e9a`** — the version bump is the entire
functional diff. Full detail (why no OCR/scale-safety port was needed, what was verified, why) is
in `CHANGELOG.md`'s `1.0.5` section; this entry records the build/artifact facts.

| | |
|---|---|
| Track | **Closed testing — uploaded 2026-09-07; pending Play's review** |
| Built from | `6f8ff63d11ce88bcf601d3ae298d96799555bc36` on `main`, `clean` build |
| AAB | `app-release.aab`, 35,925,241 bytes |
| AAB SHA-256 | `40854973366ece208f42f8a1bd715a81249ab14d2b988a6cbc0a0b30c0e937f2` |
| APK (used for the physical safety retest below) | `app-release.apk`, 66,937,306 bytes |
| APK SHA-256 | `c5f9c284b9732bebd436693d21ff6122984e171ce3d813be06488719d4fc42f3` |
| Signer | `CN=Tunc Bilen, O=JustTheCarbs, OU=Release, C=NL, L=Haarlem`, SHA-256 `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — the same key as `versionCode` 1, 2, 3, 4 and the withdrawn 5, which is what lets Play accept this as an update |

Verified directly from the built artifacts, not from configuration: `aapt2 dump xmltree` on the
extracted manifest shows `package="app.justthecarbs"`, `versionCode=6`, `versionName="1.0.5"`,
`minSdkVersion=26`, `targetSdkVersion=36`; no `android:debuggable` attribute (absent means false);
`allowBackup="false"`; permissions unchanged (`CAMERA`, `INTERNET`, plus the transitive
`ACCESS_NETWORK_STATE` already disclosed). R8 barriers unchanged from 1.0.4:
`ScanEvidenceRecorder`/`OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`/
`OcrDiagnosticsReport`/`ScanTrace` absent entirely; `UnitMarkerFilter`/`CandidateProvenance`/
`CarbCandidate` retained as real classes. No leaked local filesystem paths in the compiled dex
(scanned programmatically); no scan-evidence or secret files in the bundle beyond the same two
routine third-party-library inclusions already documented for 1.0.4.

JVM **1883/1883** (0 failures, 0 errors, 0 skipped, `--rerun-tasks`, 198 XML files) — identical to
the count before this version, since no production or test code changed. Lint **0 errors, 23
warnings** (unchanged baseline). Debug APK, minified release APK and signed release AAB all build
clean from `clean`.

### Physical safety retest — PASSED (owner, 2026-09-07)

Run against the release APK above on physical hardware, before upload:

1. **Red `7.2 → 12` label, 3–5 captures.** `12` did not auto-advance and did not reach ordinary
   `CONFIRM_ON_CAPTURE`. Where `CONFIRM_UNVERIFIED` appeared (frozen photo + row close-up, the
   suspected value shown for comparison), rejecting it opened the known-basis correction /
   focused-entry screen with `/100 g` preserved, and `7.2` could be entered there.
2. **Two known-good decimal labels.** Normal fast automatic/confirmation behavior, unchanged.
3. **`6 g / 18 g serving`-style declared-serving label.** Confirmation and normalization both
   correct, as before.
4. **One weak/off-angle capture.** The automatic narrow crop-targeting rectangle
   (`AutoCropTargeting`) still started sensibly positioned, not the old full-frame fallback.

All four passed. Owner proceeded to upload this build to Google Play's closed testing track.

---

## 1.0.4 (versionCode 5) — SUBMITTED, THEN WITHDRAWN — NOT RELEASED

**Correction, 2026-09-07 (same day as upload).** This `versionCode` was uploaded and entered Google
Play's closed-testing review, then the owner **stopped that review before Play completed it**,
after discovering that the documentation commit recording this upload (`d7c594f`, since amended)
accidentally contained unrelated private correspondence. **This version was never accepted or
released by Play, and must not be described as such anywhere this entry is read.** Per Google's
versioning rules, an update artifact must use a `versionCode` higher than any Play has seen —
including a withdrawn one — so `versionCode 5` is retired outright; the corrective build is
`1.0.5` / `versionCode 6`. Everything below this notice is the factual record of what was true
**at the moment of upload**, kept per this file's append-only rule rather than deleted, and must be
read together with this correction rather than in place of it.

| | |
|---|---|
| Track | **Closed testing — submitted, review withdrawn before completion. Not released.** |
| Uploaded | 2026-09-07 |
| Built from | `f890e9a4bc373c3f87e382233d05fd204bc217cc` on `main`, `clean` build |
| Artifact | `app-release.aab`, 35,925,236 bytes |
| SHA-256 | `89448d7d00aff4ddca78ed11c272ab1d763d5a2a8aaabc8a7148fc3b4b682269` |
| Upload key | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — the same key as `versionCode` 1, 2, 3 and 4, which is what would have let Play accept this as an update had the review completed |

Opened 2026-08-29 by a trust + feedback polish patch (Send Feedback, Rate the app in Settings, a
crowd-sourced-data note beside the OCR provenance line), then carried the 2026-09-04/05 startup
hardening pass (onboarding-flash fix, safe basis parsing replacing the last unguarded
`NutritionBasis.valueOf`, a shared camera-permission recovery gate for both scanners,
`LiveEvidenceBuffer` synchronization) and the 2026-09-07 scanner optimization pass — three OCR
evidence-adjudication fixes (a unit-box overlap tolerance, a nine-language declaration fragmented
across five rows, a recovery candidate reaching `ConfirmationEligibility`'s full-document
cross-column re-check) plus a targeted, non-generic starting rectangle for the crop-fallback
screen (`AutoCropTargeting`). No change to the calculation, the schema, migrations, the §10 lookup
priority or barcode detection in any of it.

**A separate, later fix measured against a 23-capture device corpus (`DISTINCT_OCR_AGREEMENT` no
longer settling absolute decimal scale on its own) is recorded in `CHANGELOG.md`'s open `1.0.4`
section but was confirmed, by commit history, to be on a review branch and NOT part of this
artifact.** It ships in whichever version actually carries it; do not read it into this entry.

The signer certificate was read from the built bundle with `keytool -printcert -jarfile` before
upload, and the `versionCode`/`versionName` were decoded from the bundle's own extracted manifest
(via `aapt2 dump xmltree`) rather than trusted from the Gradle configuration — the signing guard
cannot tell a real upload key from a disposable one, so a green `bundleRelease` is not evidence
that an uploadable artifact exists.

### Verification at upload

| Check | Result |
|---|---|
| JVM tests | 1883/1883, 0 failures, 0 errors, 0 skipped (`--rerun-tasks`, counted from 198 JUnit XML files) |
| Lint | 0 errors, 23 warnings (unchanged baseline) |
| Debug APK / androidTest compile | Both build clean from `clean` |
| R8 privacy barriers | `ScanEvidenceRecorder`, `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`, `ScanTrace` absent entirely. `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate` retained as real classes. `AutoCropTargeting` shows the established inlined-not-dropped pattern (line-mapped fragments present, no standalone class entry), consistent with a small pure function folded into its caller rather than a stripped feature |
| Release manifest (extracted and dumped, not assumed) | `versionCode=5`, `versionName="1.0.4"`, `package=app.justthecarbs`, `targetSdk=36`; permissions exactly CAMERA, INTERNET, ACCESS_NETWORK_STATE (transitive, disclosed) plus one framework-generated signature-level receiver permission; one exported component (`MainActivity`); no `FileProvider`; `allowBackup="false"`; not debuggable |
| Packaged-content check | No leaked local filesystem paths/usernames anywhere in the compiled dex (scanned programmatically); no scan-evidence, test-asset, or secret files in the bundle beyond two routine third-party-library inclusions (`abc_vector_test.xml` from AndroidX, `DebugProbesKt.bin` from kotlinx-coroutines) |

No OSV dependency scan was re-run for this specific artifact in this pass (the last recorded run,
226 resolved release-runtime artifacts / 0 known vulnerabilities, is the 1.0.3-era figure; nothing
in this version's dependency graph changed).

### Device verification at upload — what was and was not checked

The `f890e9a` code state was physically exercised on an SM-S928B before upload, including the
nutrition-label scanner and the final crop-targeting behavior. **The final signed/minified AAB
itself was not separately installed as a physical-device smoke test during this release
preparation pass** — the build/verify/sign work in this pass was JVM plus config/manifest
inspection of the built artifact itself, and the emulator's virtual camera was not exercised
either. Artifact-specific device verification therefore remains distinct from the earlier
source-state hardware test: the code that shipped was tested on hardware, but the signed bundle
that was actually uploaded was not separately re-installed and re-tested after signing.

The connected 39-test real-image OCR corpus was last measured on the emulator during the
2026-09-07 scanner optimization pass at 33/39 passing with 6 pre-existing failures (confirmed
identical by name against a `git worktree` control at clean `ebe6fe2` — zero regressions from that
pass's own changes) — not re-run specifically for this upload.

`docs/manual-qa.md` §37 (the scanner-optimization-pass gate) and §35 (the startup-hardening gate)
are both still open. Recorded here so a tester report against this build can be read against what
was actually known about it at upload time.

### Play Store release notes (drafted for this upload — review was withdrawn before Play published
### anything, so this text never reached end users)

336 characters against the 500 limit. See `CHANGELOG.md`'s `1.0.4` section for the full draft
context and why the 23-capture-corpus fix is deliberately not mentioned here.

```
Nutrition label scanning is more reliable: fixed cases where a good reading was missed due to overlapping text or a label split across many lines. Fixed an issue where the app could briefly show the wrong screen on launch, and where camera permission had no way back after being denied. Added Send Feedback and Rate the app to Settings.
```

---

## 1.0.3 (versionCode 4)

| | |
|---|---|
| Track | **Closed testing** |
| Released | 2026-09-04 |
| Built from | `7cbf78d` on branch `ui-refresh-2026-09-03`, `clean` build |
| Artifact | `app-release.aab`, 35,850,832 bytes |
| SHA-256 | `d94632a56519ae8074e6030be933bf98725e4ffaeea37ffc0ede79b30338ec83` |
| Upload key | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — the same key as `versionCode` 1, 2 and 3, which is what lets Play accept this as an update |

The third update of the closed beta. It carries the OCR quick-calculation feature opened on
2026-08-29 and a long run of nutrition-label scanning work, of which the last pass is the one that
gives this version its shape: **a separatorless pair of values now withholds both of its members,
not only the left one.** No change to the calculation, the schema, migrations, the §10 lookup
priority or barcode detection.

The signer certificate was read from the built bundle with `keytool -printcert -jarfile` before
upload, and the `versionCode` and `versionName` were decoded from the bundle's own protobuf manifest
rather than trusted from the Gradle configuration — the signing guard cannot tell a real upload key
from a disposable one, so a green `bundleRelease` is not evidence that an uploadable artifact exists.

### Verification at upload

| Check | Result |
|---|---|
| JVM tests | 1688/1688, 0 failures, 0 errors, 0 skipped (`--rerun-tasks`, counted from 168 JUnit XML files) |
| Lint | 0 errors, 23 warnings (unchanged baseline) |
| OSV dependency scan | 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query positive |
| R8 privacy barriers | `ScanEvidenceRecorder`, `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`, `ScanTrace`, `ZipIntegrity` absent entirely. Nine safety rules retained as real classes |
| Release manifest | CAMERA, INTERNET, ACCESS_NETWORK_STATE; no `FileProvider`; `allowBackup="false"`; not debuggable; no locale configs |

### Device verification at upload — what was and was not checked

**Nothing in this version was verified on physical hardware before upload.** Every figure above is
JVM plus the `carbscan` emulator, whose virtual camera cannot produce a nutrition table — so the
*automatic* OCR accept path was never exercised end to end against a real package.

The connected OCR suite measured **33 tests / 9 failures** on that emulator. All nine are
**pre-existing**: a `git worktree` control at clean `47ad5d1`, same emulator and same session, fails
the identical nine by name. They are the emulator's ML Kit degradation (`Koolhydraten` →
`nlhioonorate`) this repo already records; the same corpus is 39/39 on real hardware, and it was
**not** re-run on hardware against this build.

The open gates at upload were `docs/manual-qa.md` **§34** (rows 34.1–34.5 decide whether the
separatorless-pair leak is closed on a phone; 34.6–34.8 are the controls that must keep working) and
**§§26–33**, all unticked. Recorded here so a tester report against this build can be read against
what was actually known about it.

### Play Store release notes (as published)

**This is the text that went into Play Console's *What's new*** — the owner's wording, recorded
verbatim rather than the draft that preceded it. 384 characters against the 500 limit. Checked
against §44 §7.1: no health claim, no mention of diabetes, no medical wording.

```
Nutrition label scanning gets a major upgrade: faster, safer readings with better support for multi-column, serving-based and American-style labels. Good scans now go straight to a quick calculation, while uncertain values ask only for what’s missing. You can also save a quick calculation as a product for later. Plus smoother recovery, better row tapping and many reliability fixes.
```

### Fixed — the eighteenth session (2026-09-04)

A separatorless **pair** of values demonstrates that a common rescaling is equally consistent with
the recognised text — and that is true of both members, not only the left one. The app refused one
and offered the other.

- **Neither member of a separatorless pair is offered.** `ScaleAmbiguity` searched for a candidate's
  paired value only among elements to the *right* of it, so on a two-column carbohydrate row the
  left cell saw the pair and was withheld while the right cell saw nothing, reported "no paired
  value", and was then admitted by its declared serving basis. Measured on three captures of the
  2026-09-04 session: a protein bar printing `46 g / 100 g` and `12 g / 25 g reep` suppressed `46`
  and **offered `12 g / serving`** one tap from the calculator. The pair relation is symmetric, so
  both are now withheld.

  The rightward bound existed so a nutrient *name* could not pair with its own value. That job is
  actually done by the leading-digit rule — `Koolhydraten`, `Vetten` and `E471` are excluded from
  either side — and both controls are pinned by tests. A single-column label reads exactly as before.

  **No value is repaired**: `46` never becomes `4.6`. A withheld figure routes to focused entry with
  the frozen photograph, the highlighted row and the label's own basis preserved, where the user
  types the digits printed in front of them.

- A declared serving basis no longer rescues a **demonstrated** ambiguity. A serving sentence the
  label printed says which quantity a figure is measured per; it says nothing about where its decimal
  point is. Lone separatorless values under a declared serving — the Korean sauce's
  `6 g / 18 g serving` — are unaffected and still offered.

### Fixed — test contracts, not behaviour (2026-09-04)

- Four real-image OCR cases asserted `CandidateProvenance.FromRow`, which **no production code
  constructs any more** — the tabular path emits `FromDeclaration`. They were failing on a type name
  while the carbohydrate values they exist to protect were still correct. Migrated to accept either,
  and **strengthened**: the source rows must now also name no child nutrient, checked against the
  parser's own child vocabulary. `FromRow` proved that implicitly by carrying one row; a declaration
  may span several, so it has to be stated.

### Fixed — the seventh phone session (2026-09-02)

The sixth session's scale safety held on the device: the truffle label never displayed or offered
`89`. It was, however, **unrecoverable**. Tapping the carbohydrate value answered *"This looks like
sugars or fibre. Tap the total carbohydrate row instead."*, and returning to recovery left *Type it
in* disabled — a dead end on a label the app had otherwise read correctly.

- **A tap inside a merged row's total-carbohydrate clause is no longer answered with a fact about
  its sugars clause.** On this package ML Kit puts the whole declaration on one reconstructed row
  (`Kohlenhydrate 8,9 a, 1.3q(<19; waarvan suikers/dant sucres/tavon Žucker`), so the row classifies
  `CARBOHYDRATE_CHILD` — correctly — and every element on it, including the word `Kohlenhydrate` and
  the value `8,9` half a screen from the sugars word, was refused as sugars. Hit-testing now asks
  which printed *clause* the finger landed on, using the horizontal position a tap carries and the
  automatic path does not have. A tap inside the sugars clause is still refused.
- **Focused entry is reachable after an unsuccessful tap.** It was gated on a tap that produced
  nothing *and was not a child row*, so a merged row could never reach it; `FocusedAmountEntry`
  itself also required a `TOTAL_CARBOHYDRATE` row and returned null here. Both are fixed, and a
  second refused child-clause tap now offers focused entry rather than inviting a third identical
  attempt. It asks for the amount printed under **100 ml** and shows no basis picker.
- **Nothing was relaxed to achieve this.** Automatic classification is unchanged — both captures
  still classify `CARBOHYDRATE_CHILD` and neither produces a confident reading. `89` is still
  suppressed, `8.9` is never manufactured from the separatorless capture, `ScaleAmbiguity`,
  cross-run dispute suppression, unit accompaniment and child-nutrient protection are untouched, and
  no OCR pass was added.

### Performance — a real parser defect the session exposed

`141642-529` took **2142 ms** with **1432 ms in parse**, against 501–864 ms total and 55–199 ms
parse for every other capture in the same run. Measured in work counts rather than wall clock, that
289-element document did **51,792** `normalize` calls against 2,295 for its 262-element sibling —
22.5x the work for a 1.10x larger document.

The cause was the 2026-09-01 quadratic shape surviving in two functions: `ProseNutritionReader`
walked **every vocabulary term at every token position** and normalized the term inside that loop,
though the vocabulary is a compile-time constant. Both now use a cached `NutritionTerminology
.termWords`, mirroring the existing term cache. **51,792 → 2,060**, so the slow capture now does
slightly *less* work than its sibling, consistent with its size. Pinned by invocation-count
assertions, not by wall-clock time.

### Fixed — the sixth phone session (2026-09-02)

A truffle sauce printing `8,9 g / 100 ml` and `1,3 g / 15 ml` was recognised as `89` and `13` — the
decimal separator did not survive — and the app displayed **`89 g / 100 ml`**, a ten-fold error, on
two consecutive captures that reached the screen by two different routes.

- **A value another recognition run contradicted is no longer offered by recovery.** On
  `20260902-131511-970` two independent runs read `89` and `8`, the resolver correctly returned
  `Conflicted` — and the recovery screen then offered `89 g / 100 ml` as its first choice, because it
  was rebuilt from the winning document alone and had no way to know a dispute had happened. The
  refusal now travels with the hand-off. **Both** sides of a disagreement are suppressed, not just
  the loser: nothing available to the app says which reading is the printed one. Undisputed values,
  row tapping and manual entry are unaffected, so a conflicted capture degrades to *read the
  package*, never to a dead end.
- **A single unverified recognition no longer gets an ordinary confirmation when the evidence cannot
  establish the decimal scale.** On `20260902-131545-452` one run read `89`, the second found
  nothing, and the app showed a confirmation card. A confirmation is a question — *is this right?* —
  and it is a fair question only when the user can check the answer; here the separator was missing
  from *every* value on the label, so nothing on screen distinguished `89` from `8.9`. Such a reading
  now goes to focused entry with the stated basis preserved.
- **Why the existing cross-column check could not catch this.** The collapse is uniform: the printed
  `2,7 g` fat read `27 g`, `1,6 g` salt read `16 g`, `4,6 g` sugars read `46g`. Multiplying both
  sides of a ratio by ten leaves the ratio unchanged, so the table corroborated `89` exactly as
  strongly as it would have corroborated `8.9`. **Relational consistency establishes proportion,
  never absolute scale** — a separate question, so it gets a separate answer rather than a tightened
  threshold.
- **Nothing is repaired.** `89` never becomes `8.9`; no value is divided, shifted or inserted
  anywhere. The decimal point is what OCR is least reliable about, and unlike a refusal a wrong
  repair is invisible — the user sees a plausible number and has no reason to check it.
- **The rule keys on punctuation, not magnitude,** and is only ever asked of an *unverified* reading
  paired with a second value that also lost its separator. That is what keeps `41g` on
  `20260902-131357-353` — integer-like, no separator, and correct — advancing exactly as before, on
  the strength of three runs agreeing. There is no "large values are suspicious" threshold, and none
  may be added: such a rule would refuse flour and sugar while still admitting a collapsed `46`.
- **Edit no longer discards a basis the label stated.** Rejecting `89 g / 100 ml` and tapping *Edit*
  opened manual entry blank with **`100 g`** selected, because the action carried no basis at all and
  the screen's own default stood. The basis decides the unit the portion field asks a human to
  measure in, so typing the right figure there stored it against the wrong denominator with nothing
  downstream able to detect it. *Edit* from `/100 ml` now opens on `100 ml`, and from `/100 g` on
  `100 g`; the amount stays blank deliberately, since the action is reached precisely when the app's
  number was wrong or withheld.
- **The evidence bundle can now reconstruct this class without a screen recording.** `selection.txt`
  gained the cross-run dispute (which run read what), the surviving punctuation evidence behind a
  scale refusal, and the basis handed to Edit or focused entry with whether the amount was
  intentionally blank.

### Fixed — the fifth phone session (2026-09-02)

- **A carbohydrate figure can no longer be shown under a basis it never had.** When OCR damaged a
  `per 100 g` column header, the per-100 value bound to the neighbouring *per portion* column — the
  only one left within binding distance — and the recovery screen offered `72 g / serving` for a
  value the packet prints per 100 g. A column may now claim a cell only when no other value on that
  row sits closer to it, so a number whose own column was destroyed is left unresolved and
  suppressed instead of relabelled. The genuine per-serving figure is unaffected.
- **A verified reading now reaches you.** On the same capture the second recognition read the
  printed `72 g / 100 g` and the label's own other rows corroborated it — and the app showed generic
  recovery anyway. A reading a single recognition found is still only *proposed* when nothing
  corroborates it; when the table itself agrees, it is now the result.
- **A contradicted value is refused everywhere, not only automatically.** A figure the table's own
  rows refute — the `12` where the packet prints `72` — is no longer offered as a recovery choice
  either. Typing a value by hand is unchanged.
- **Verification is now tied to the exact reading being offered**, rather than to whichever
  recognition happened to be first in the list.
- **The camera permission notice no longer names only barcodes.** Opening the label scanner on a
  fresh install explained the camera was needed "to scan a barcode". No permission changed.

### Diagnostics

- Evidence bundles now record the recovery proposal: each offered choice with its displayed value,
  basis, provenance and derivation, and each *suppressed* number with the rule that removed it.
- The `serving:` line is relabelled `header-…` and points at that block. It reports the column
  header's own declaration, which is legitimately absent on a US linear panel; the serving size such
  a panel states in a sentence is what the screen uses and is now printed where it can be read.

### Fixed — the fourth phone session (2026-09-02)

- **A misread number can no longer become an answer without you confirming it.** The scanner
  read one cracker packet's `72,0 g` as `12,0 g` and showed 12 g of carbs per 100 g straight
  away, with nothing to press. It now checks a figure against the rest of the same table — every
  other row on that label agreed the reading was six times too small — and a figure it cannot
  check is shown for you to confirm rather than used on its own. Correct scans that the label
  itself corroborates still go straight through in two taps.
- **US-style nutrition panels are read properly.** A panel that prints everything in sentences
  ("Total Carb. 6 g (2% DV), Fiber 1 g") was being read as if the percentages were columns, and
  the carbohydrate figure was discarded. Both jars of sauce now offer 6 g per 18 g serving.
- **An ingredient list is no longer treated as nutrition information.** One sauce lists "brown
  sugar" among its ingredients, and that line was being counted as part of the nutrition table.
- **Tapping the carbohydrate row now selects that row.** Where two rows sat close together the
  app could attribute the tap to the sugars row below, refuse it, and leave the screen unchanged
  — so the tap looked like it had missed and people tapped again.
- **A row it found but could not read now asks for just that number.** Instead of repeating
  "tap the carbohydrate row", it says the row was found and asks for the value printed under
  100 ml. It does not ask which unit — it already knows, from the label.
- **A converted figure is shown rounded.** Reading 6 g per an 18 g serving showed 33.3 g per
  100 g on one screen and 33.33333333 on the next.

### Play Store release notes (fifth-session draft, re-check before upload)

```
Nutrition label scanning is more careful. A figure the app cannot double-check against the rest
of the label is shown for you to confirm instead of being used straight away, and a value is
never shown under the wrong "per 100 g" or "per portion" heading. American-style nutrition
panels read properly, tapping a row picks the row you meant, and a row the app finds but cannot
read asks for just that number.
```

### Fixed

- **A figure printed per 250 ml can no longer end up labelled "per 100 ml".** When the scanner
  could not read a drink's table by itself and asked the user to point at the number, it would then
  offer to use it "per 100 ml" — because the label says that somewhere, even though the number the
  user pointed at was printed under a different column. On one drink that turned 1,3 g per 250 ml
  into 1,3 g per 100 ml: right number, wrong measure, and nothing on screen to say so. Every figure
  the scanner offers now shows what it is measured per — *0.5 g / 100 ml*, *1.3 g / 250 ml* — and
  picking the second one converts it rather than relabelling it. A number the app cannot place on
  the label is not offered at all.
- **Nutrition panels that give a serving size instead of a per-100 column now work.** A label
  reading *Serv. size: 1 Tbsp (18 g)* and *Total Carb. 6 g* used to be a dead end: the scanner
  refused it, then asked whether the 6 g was per 100 g or per 100 ml, and neither answer was right.
  It now shows *6 g per 18 g serving* and works out the per-100 figure from the serving size the
  label printed, showing both.
- **Two-column drink labels are read as two columns.** Several photographs of the same drink had
  their *per 100 ml* and *per 250 ml* headings merged into one, so a value from either column could
  be reported under the other. They are now kept apart even where the second heading was damaged in
  the photo.
- **A value with a stray mark between the number and its unit is no longer thrown away.** One
  capture of a cracker packet read *72,0.g* rather than *72,0 g* and was rejected; the next
  photograph of the same packet was read correctly. That figure is now accepted, while a number
  whose unit is genuinely missing or wrong is still refused.
- **Percentages are never offered as carbohydrate figures.** Reference-intake values such as *9%*
  or *2% DV* could appear among the numbers the scanner suggested.
- **Tapping the sugars or fibre line says so.** It used to offer that line's numbers; it now
  explains that this looks like sugars and asks for the total carbohydrate line instead.
- **Exported scan diagnostics are checked before they are shared.** One export was cut short and
  produced a file that could not be opened, with nothing to indicate anything had gone wrong. An
  incomplete archive is now reported as an export failure instead.

### Changed

- **The first scan after opening the scanner is faster.** Text recognition now loads while the
  camera is being aimed rather than when the shutter is pressed.
- **A scanned nutrition label goes straight to the calculator.** Scanning a label with no product
  open used to hand the reading to the *Enter product* form, which would not let go of it until a
  product name had been typed and a record saved — so getting one carbohydrate figure out of one
  photograph meant creating a database entry the user never asked for. The reading now opens the
  ordinary calculator directly: type the portion, read the total, leave. Nothing is written to the
  device unless the user taps **Save product**, which is where the name is asked for and the only
  place it is needed.
- **The calculator names itself when there is no product to name.** A quick calculation is titled
  *Quick calculation* and drops the placeholder image, instead of showing an empty title over a
  blank tile — which read as a product record that had failed to load rather than as the reading
  just taken. Its controls are centred in the space the image gave back.
- **A scanned label lands ready to type.** The portion field takes focus and the keyboard opens as
  soon as the reading reaches the calculator, so the sequence is scan, type, read the total — with
  no tap in between on the one field the screen exists for. A saved product is deliberately
  unchanged: it opens with the portion you last used and its one-tap shortcuts visible, which a
  keyboard would cover.
- **At larger text sizes the calculator no longer looks broken.** From the 1.3× text setting
  onwards the portion controls need more room than the screen has, and the last one — usually
  *+ Add portion unit* — came to rest sliced horizontally through the middle of its letters at the
  edge of the result panel. It read as a rendering fault rather than as a hint that more was below.
  That edge now fades, and only when there is genuinely more to scroll to; at the default text size
  nothing changes at all.

### Added

- **Save product**, on a quick calculation only. Secondary to the result by design; asks for a name
  and nothing else, keeps the calculation on screen, and puts the product into Recents with the
  portion already remembered. Cancelling leaves the calculation exactly as it was.

### Changed — scanning

- **A good scan no longer asks you to approve a crop.** Capture already chose a rectangle for
  itself — the scan guide you were aiming with — and the next screen existed to have that rectangle
  confirmed before it could be read. When it was already right, which is the ordinary case, that was
  a tap that changed nothing. The reading now happens straight away, and the crop screen appears
  only when the result was not safe enough to show. Confirming the *value* is unchanged: the
  proposal card and its **Use 48 g / 100 g** button are exactly as they were.
- **The crop screen says when it is a fallback.** Reached after an automatic attempt it now reads
  *Couldn't read it automatically*, and while a pass is running it says *Reading table…* instead of
  telling you to drag corners the app is not waiting on. Previously it looked identical whether it
  was the first step after a capture or the hand-off from an attempt you had just waited through.

### Fixed — found in review of the above, each reproduced by a test that failed before the fix

- **A failed save looked like a tap that missed.** If the write failed, the naming dialog stayed
  open exactly as it was — and the message explaining what had happened renders on the screen
  *behind* it, so nothing the user could see said the product had not been kept. The dialog now
  closes on failure, which is what puts the message in front of them, with the action still there
  to try again.
- **A failure message outlived the attempt it described.** Having failed once, the warning stayed
  on screen through the next attempt and through a *successful* save, so a product that had just
  been saved still showed a message saying it had not been. Reopening the form now clears it.
- **A quick calculation added to a meal produced a blank line.** The meal takes its label from the
  product's name, and a quick calculation has none by design — so the item appeared on the plate as
  an empty row, with only its portion and carbohydrate figure to identify it, and the *Remove* label
  a screen reader announces had nothing after the word. It is now listed as *Quick calculation*.
- **A new capture could be dismissed as an unchanged crop of the previous one.** `captureLabel`
  cleared every other piece of per-capture state — the frozen photo, the proposed box, the reading
  flag, the "already tried automatically" flag — but not the region the last recognition ran over.
  That region is only cleared by *Retake*, and *Capture label* is reachable without it from the
  ambiguous, not-found and searching cards. Since both captures propose the same rectangle (both
  derive it from the scan guide), the new photograph's first *Read table* compared equal to the old
  photograph's and was skipped, telling the user a picture that had never been read would "read the
  same as before". The skip is now cleared on capture as well as on retake.
- **The post-attempt crop instructions were the ordinary ones.** `crop_body_after_attempt` was
  byte-identical to `crop_body`, so the conditional selecting between them did nothing and the new
  wording lived entirely in the title. The body now says the thing the generic copy cannot — that
  the box on screen *is* the one the app already tried, so moving it is what changes the answer.

### Fixed — from the physical-device recording of 2026-08-30

- **An impossible carbohydrate figure was offered exactly like a real one.** A red label printing
  about `7,9 g` produced `790` and `794` through the assisted path, and both were presented with the
  same two full-emphasis *Use / 100 g* and *Use / 100 ml* buttons an ordinary value gets — one tap
  from a figure that cannot exist, with nothing on screen saying so. 790 g of carbohydrate cannot be
  in 100 g of food, nor in 100 ml. Such a figure now gets **no ordinary accept action at all**: the
  number is still shown, said plainly to be wrong, and the field stays open to correct.
  **Nothing is repaired and nothing is clamped** — `790` is never quietly offered as `79.0` or
  `7.9`, because the decimal point is the one thing OCR is least reliable about, and a wrong repair
  is invisible where a refusal is not.
- **A basis the label had already stated was thrown away and then asked for again.** A coconut-milk
  table printed `per 100 ml` clearly enough that the column classifier read it, but because the
  *value* needed assistance the app asked *"2.5 g carbs — per what?"* and offered `/100 g` beside
  `/100 ml` — re-asking a question it had answered, with the wrong answer one tap from the right
  one. Confidence in the value and confidence in the basis are separate facts, and are no longer
  collapsed into one. A basis the label stated unambiguously is now carried through and named. A
  label stating nothing, or stating **both**, still asks — that ambiguity is exactly what the user
  is there to resolve.

### Changed — the fallback does less redundant work

- **Confirming an unchanged crop no longer repeats the same recognition.** When the automatic
  attempt declines, the crop screen opens on the very rectangle that attempt used, so tapping
  *Read table* without moving a corner re-ran the identical passes over identical input — a
  measured ~400 ms wait to reach the refusal already given, recognition being deterministic. It now
  goes straight to the assisted path, which is where a repeat of that outcome led anyway, and says
  *"Same box as before, so it would read the same."* A crop the user genuinely moved is always
  recognised: this is a shortcut through a known result, never a skipped check.
- **Framing guidance names the goal.** *Move closer to the nutrition table* became *Move closer —
  fill the frame with the nutrition table*. Measured cause: the region the automatic pass reads
  spans the **full width of the frame**, because the 12% safety margin around an already
  near-full-width scan guide clamps to both edges. Surrounding package text therefore cannot be
  excluded by aiming more carefully — only by getting closer — and the old wording left the user
  adjusting something that could not help. No new signal, no threshold change, and the shutter is
  still never blocked.

### Internal

- `ProductViewModel.startQuickCalculation` loses its mandatory `name` parameter — the coupling that
  forced the OCR path through product creation — and gains `saveQuickCalculation`. The calculation
  itself is untouched: both acquisition paths reach the same `CarbCalculator` through the same
  state, so there is no second formula and no second rounding.
- Provenance is carried, not flattened: a quick calculation is `OCR` / `UNVERIFIED`, and saving
  preserves the origin rather than relabelling it `MANUAL`.
- `CarbPlausibility` (domain) is a two-question phrasing of the ceilings `NutritionValueValidator`
  already owns — **one rule, not a second copy**, pinned by a test asserting the two agree across
  the range. `StatedBasis` (ocr) asks the existing `ColumnClassifier` what the table is measured
  per and reports it only when unambiguous. `CropChange` (ocr) decides whether a confirmed
  rectangle differs enough to be worth recognising, with a tolerance far below any deliberate drag.
- **No OCR rule was weakened.** No threshold moved, no confidence bar lowered, no second engine or
  parser added, and `EvidenceResolver` is untouched. The real-image corpus is unchanged.

### Internal — dead code removed

Nothing here changes behaviour; every item was already unreachable in production.

- **The pre-recognition crop is gone from the code, not just from the call sites.** The 2026-08-17
  capture-first pass stopped cropping the capture to the scan overlay before OCR — cropping cut the
  basis header off tall labels and cost both canaries — but it left the machinery in place with
  `region = null` passed at every call site. `StillImageLoader.loadWithRotation` loses the parameter
  and the crop branch, `StillImageLoader.load` (no production caller at all) is deleted, and
  `ScanRegionMapper.toPixels` with its `PixelRegion` type goes with them. `ScanRegionMapper.expand`
  is untouched and still load-bearing — it is the rectangle the fast path reads. Both KDocs, which
  still argued *for* cropping before recognition, now record why that was measured and reversed.
- **`ProductionStillPathBaselineTest` deleted.** Two tests, zero assertions, written to measure a
  baseline "before production code is touched" for a change that shipped; its KDoc described a
  production path (`StillImageLoader`'s ROI crop) that no longer exists. Not part of the
  nine-photograph corpus. `ScanRegionMapperTest` loses the 6 cases that tested only `toPixels`,
  keeping its 3 `expand` cases — which is the whole of the 1075 → 1069 JVM change.
- **21 unused strings deleted**, each verified to have zero Kotlin references independently of
  lint. Includes four `crop_handle_*` accessibility labels that described per-handle nodes that do
  not exist — the crop handles are drawn on a Canvas and the selection carries one
  `contentDescription`. Unused-resource advisories 21 → 0; total lint advisories 40 → 19.
- **Kept deliberately, with the reason recorded in its KDoc:** `ServingSizeParser.parse` has no
  production caller (everything moved to `parseDescriptor`, whose weight is optional), but deleting
  it would delete a *rule* rather than an unused function — "a count with no printed weight is not a
  weight mapping" has no other home, and countable-portions §5/§20 makes a false positive there the
  failure that matters. Also kept: `NutritionTableLocator` (retained-and-unwired by an earlier
  decision, with its measured failure in its own KDoc) and the two live-service diagnostics.

**Play Store release notes (draft — re-check before building):**

> Scan a nutrition label and get your carbs straight away. No product name, no saving, no setup —
> just the value, your portion, and the total. If you want to keep a product for next time, saving
> it is now a single optional step.

---

## 1.0.2 (versionCode 3)

| | |
|---|---|
| Track | **Closed testing** |
| Released | 2026-08-29 |
| Built from | `29a4f3d`, `clean` build with `--rerun-tasks --no-build-cache` (all 54 tasks executed, nothing from cache) |
| Artifact | `app-release.aab`, 35,671,928 bytes |
| SHA-256 | `7c2ae0618fda7fdfcaa8e5be24172ccfe54b1639177efc978a2b88c3c2a42828` |
| Upload key | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — the same key as `versionCode 1` and `2`, which is what lets Play accept this as an update |

The second update of the closed beta, and the first to carry feature work: text search moved to a
different provider and became live-as-you-type, and the light/dark theme was reconciled with the
Android system bars. No change to the calculation, the schema, migrations, the §10 lookup priority,
barcode detection, or any OCR safety rule.

The signer certificate was read from the built bundle with `keytool -printcert -jarfile` before
upload, and the `versionCode` and `versionName` were decoded from the bundle's own protobuf manifest
rather than trusted from the Gradle configuration — the signing guard cannot tell a real upload key
from a disposable one, so a green `bundleRelease` is not evidence that an uploadable artifact exists.

### Device verification at upload — what was and was not checked

Recorded at the time rather than reconstructed later, and split by what was actually observed.

**Verified on a physical device (owner, 2026-08-29):**

- **Live search works.** The first hardware confirmation of this version's largest change — the
  provider migration and search-as-you-type. Until this point the whole search stack had been JVM
  and emulator only.

**NOT verified on a physical device at upload:**

- **The theme and system-bar fixes.** This is the notable gap: those defects were *reported from a
  device*, so the fixes address symptoms no emulator run had reproduced, and the four
  device-theme × app-selection combinations are exactly what automated tests cannot observe on real
  system bars. Worth checking against the Play-delivered build via the tester link — in particular
  app-forced-Light on a dark phone and app-forced-Dark on a light phone, the two combinations the
  previous code got wrong.
- **The barcode and OCR label-scan regression pass**, and a full calculation from a search result.
  Unchanged by this version's diff, but unexercised on hardware against this build.
- **The 63-row manual-QA sheet for the search work** (`docs/manual-qa.md` §19c–§19e) remains
  unchecked. Deliberately not treated as a blocker for a beta patch; it is the standing record of
  what the search migration has never been exercised against on hardware. The provider fallback is
  invisible by design — only the `JtcSearch` debug log can say which provider answered a query.

**Unchanged and still open:** `uses-feature android:hardware.camera` ships as required, as it did in
1.0.0 and 1.0.1 — an open distribution decision for the owner, not a defect.

#### Note added 2026-08-29 — theme and barcode confirmed on the device after upload

The section above is left as written at upload time. Since then the owner checked the
Play-delivered build on the Samsung device:

- **The theme fixes work.** Light and Dark were both exercised and both render correctly — the
  reported defects are gone: status-bar icons are readable in Light, and the Settings title, back
  arrow, gear and *Haptic feedback* row are readable in Dark. That is direct confirmation of the
  `LocalContentColor` half of the fix, which is what the black-on-black symptoms came from.
- **Barcode scanning works**, so the core path is regression-free on this build.

**Still not observed, and worth being exact about:** the two *override* combinations — app forced
Light while the phone is Dark, and app forced Dark while the phone is Light — were not tested
separately. Those are the cases that exercise the **other** root cause, the system bars resolving
their light/dark from the device configuration instead of the app's selection. `resolveDarkTheme`
makes the two share one authority and is pinned by tests in both directions, so the behaviour is
argued rather than unknown — but it is inference, not observation, and this file does not promote
the second to the first. Also still unexercised on hardware: an OCR label scan, and a full
calculation from a search result.

- **Live search asked Open Food Facts far more often than it is allowed to, and the refusals looked
  like an outage.** Search-as-you-type sent a request after every half-second pause in typing, with
  nothing capping how many that added up to. Open Food Facts allows about 10 searches per minute per
  address — shared by everyone on the same connection — so a minute of ordinary typing could exceed
  it several times over, and the server started refusing. Those refusals were shown as *"The product
  database is unavailable"*, which is what produced the results → loading → error → loading → results
  flicker on a real phone: the app had earned the failure and then blamed the database for it.
  Typing is now answered instantly from results already on screen, while remote searches are paced
  by one shared budget across the whole app. **Later in this version** the primary provider changed
  (see *Product search now uses a dedicated search service* below), and that budget of at most 9 per
  minute now applies to the Open Food Facts fallback, which is the endpoint that imposes it. Typing
  and Search still coalesce to one request per settled query, and repeatedly pressing Search or Try
  again still adds none at all.
- **A momentary search failure wiped the results you were reading.** While live search refreshed a
  list, a single failed request replaced that whole list with the full error screen — losing results
  that were still perfectly good and still tappable, for a request nobody had asked for. Open Food
  Facts' search endpoint answers 503 while otherwise healthy often enough that this happened
  mid-word. A refresh that fails now leaves the results in place and says so in a thin line above
  them, with *Try again*; the full error screen is still shown when a search fails with nothing on
  screen to keep, which is the case where those recovery options are the whole point.

### Changed

- **Product search now uses a dedicated search service, with the previous one kept as a backup.**
  Text search runs against Open Food Facts' purpose-built search service; the older search endpoint
  remains in place and answers automatically if the new one cannot. The change is about whether
  search works at all: measured on 2026-08-28, at a deliberately polite pace well inside its own
  limits, the old endpoint refused **five of seven** ordinary queries — *chocolate*, *gouda*,
  *nutella*, *bread* and *hagelslag* all came back unavailable. The new service answered every one,
  on the phone, in about a tenth of a second. Switching between the two is invisible: a search that
  falls back looks like one search, not a failure followed by a retry, and an error is shown only
  once both have failed.
  - **One known trade, and it costs no accuracy.** The new service does not publish the field the
    app uses to tell grams from millilitres, so more results now show their name, brand, size and
    photo without a carbohydrate figure beside them. Nothing is guessed — the app has never shown a
    figure whose unit it could not establish, and tapping a result still fetches the full product
    and its real values exactly as before. Search only helps you find the product; the numbers you
    count with come from the product itself.

- **Product search now updates as you type.** Previously a query sat in the field doing nothing
  until Search or the keyboard's Search key was pressed — the results were there to be had and the
  app waited to be asked. Typing now runs the search by itself, about half a second after typing
  stops. Pressing Search still works and skips that wait.
- **Home's search shows when it is refreshing.** Home had no refresh indication at all: its only
  loading state was the centred spinner shown when there were no results, so a refresh over an
  existing list was completely silent. It now shows the same thin progress line and the same
  inline refresh notice as the search screen, so the two cannot drift on what a refresh looks like.
- **Typing now updates the visible results immediately, without waiting for the network.** When the
  new query extends the one the results came from — typing `chocolate` after `choc` — the list
  narrows on the spot while the remote refresh is still pending. A query that is *not* an extension
  (`chocolate` → `gouda`, or shortening back to `choc`) shows a neutral waiting state instead: those
  results are not a partial answer to the new query, so presenting them would be wrong rather than
  merely stale.
- **Waiting for the app's own request budget no longer looks like a failure.** It shows a quiet
  *Updating…* and resolves by itself; only the newest query is kept while it waits, so typing five
  more characters during a wait costs one request rather than five.
- **Being rate-limited is now handled as a wait rather than reported as a broken database**, with no
  *Try again* button while a server-imposed backoff is in force — offering one there invites exactly
  the repeated requests the backoff exists to stop. The queued search resumes automatically, once;
  a second refusal is then reported plainly instead of retrying forever.
- **The wait before a typed query is searched is 0.5 s.** It went 0.6 s → 1 s earlier in this
  version's development, while every search still went to Open Food Facts' rate-limited endpoint and
  a conservative delay avoided spending a scarce slot on a mid-word hesitation. It returned to 0.5 s
  when the dedicated search service became the primary provider (below): that service answers in
  about a tenth of a second and imposes no such budget, so the extra half-second bought nothing and
  the user paid it on every query.
- **Results no longer blank out between queries.** Editing a query keeps the previous results on
  screen, under a thin progress line, until the newer ones replace them in one step. The old
  behaviour cleared the list on every keystroke, so the screen flashed empty-then-spinner-then-
  results for each character typed. Results are still dropped at once when the query is cleared or
  shortened below the three-character minimum, where nothing is coming to replace them.
- **Typing a query that is still too short is no longer treated as a mistake.** The "type at least
  3 characters" notice now appears only when Search is actually pressed, not while someone is on
  their way to a longer word — where, as a screen-reader live region, it announced on every
  keystroke.

### Hardening

- **Search requests now use the provider's POST search API.** What someone types is sent in the
  request body instead of the web address. A web address is the part of a request that proxies and
  server logs routinely keep in plain text, and a search here is a food someone is about to eat.
- **A search reply that arrives damaged no longer looks like "no such product".** If the search
  service answers that it has matches but none of them can be read, the app now treats that as a
  failed search and asks the backup provider — instead of telling the user their product does not
  exist. Both look like an empty list on screen, which is why this could not be noticed in use.
- **Punctuation in a product name is now searched for literally.** The search service reads its
  input as a query language, so brackets, `+` and `:` were being treated as commands rather than as
  part of the name: *Kinder Bueno (White)* found nothing at all, and *milk -chocolate* quietly
  searched for milk **without** chocolate. Names are now searched exactly as typed. Nothing about
  what the search field shows or accepts changed.

### Improved

- **Going back to a search you just ran is now instant.** Looking at *chocolate*, then *gouda*, then
  *chocolate* again reuses the results the app already has instead of asking the network a second
  time — so the list appears immediately, with no spinner and nothing to wait for. Results are kept
  for a few minutes and only while the app is open; nothing is written to the device. A search that
  failed is never reused, so a momentary problem cannot get stuck on screen, and tapping a product
  still loads its full, current details exactly as before.
- **Search results are fetched slightly leaner.** One piece of data the app requested and never
  displayed is no longer asked for. Nothing shown on a result changes.
- **Seven controls were too small to tap reliably, and two of them are the grams/slices switch.**
  Measured on a device, not guessed: the *Grams* and *Slices* buttons on the calculator were 32dp
  tall, *Add portion unit* 40dp, the search and clear buttons inside both search boxes 40dp, Home's
  meal bar 40dp and Home's *Enter manually* 43dp — against the 48dp minimum the rest of the app
  already uses. The mode switch is the one that matters: it decides whether the number you type
  means grams or a count, so missing it changes what the answer is *of*, not just its size. All are
  now full-size. Nothing moved, nothing was restyled, and no spacing changed — the tappable area
  grew to meet the text already there.

### Internal

- New `TouchTargetSizeTest` (instrumented, 5 cases) asserts every clickable node on Home, Search and
  the calculator is at least `Space.minTouchTarget` on its short side, in **dp**, so the result does
  not depend on device density. Text fields are excluded by `IsEditable`, and zero-sized (scrolled
  out of view) nodes are skipped so an off-screen node cannot fail for the wrong reason.
  **Why a test rather than a review habit:** the convention was already established and applied
  everywhere else, and seven controls still shipped under it, because every place that misses it is
  a place where something *else* silently overrides the intent — an `IconButton` in a text field's
  decoration slot is measured by the field, not by its own 48dp default; Material 3's `FilterChip`
  is 32dp by default; and on Home's *Enter manually*, `.height(48.dp)` was written **before**
  `.padding(top = 4.dp)`, so the padding was applied *inside* the 48dp box and the button measured
  44. That last one is the case worth remembering: the line that looks like the fix **was** the
  defect, so reading the code argued the opposite of the truth. Fixed by ordering padding first;
  modifier order is load-bearing and silent when wrong.
  Negative control, run against the unfixed code: every case fails, reporting
  `Search = 40x40dp`, `Clear search = 40x40dp`, `Grams = 75x32dp`, `Slices = 73x32dp`,
  `+ Add portion unit = 137x40dp`, `Enter manually = 371x43dp`. At 1.8× font scale the chips
  measured 35dp, so a large font does **not** rescue them — which is why every fix is
  `heightIn(min = …)` rather than a fixed `height`, so the control still grows with its text.
- Live and explicit search share **one** request pipeline (`MutableStateFlow<SearchRequest>` →
  `flatMapLatest`), so a debounce and a keypress cannot issue two requests for the same query.
  Typing a nine-character word costs one request, not nine.
- Stale-response protection is a request-generation check at the single point where a result is
  written into state, deliberately **not** left to coroutine cancellation. A response for an
  abandoned query cannot reach the screen even if its transport ignores cancellation and completes
  anyway.
- Searches run in their own child coroutine rather than in the collector. `collectLatest` waits for
  the previous block to unwind before starting the next, so a transport slow to cancel stalled the
  pipeline and the *next* query was never sent — found by negative control, not by reading the code.
- `SearchUiState` gained one field, `refreshFailed`. It distinguishes the two failures by what the
  user stands to lose rather than by what went wrong — both carry the same `LookupError` — and is
  always false when there are no hits, so a renderer can treat the two as exclusive. Both screens
  now test `hits.isNotEmpty()` **before** `error != null`; the old ordering is why a refresh failure
  could take the region away from results that were still good.
- New `RemoteSearchGovernor` (pure Kotlin, `domain/`, injectable clock) owns the Open Food Facts
  search budget: `MIN_INTERVAL_MS = 7000`, giving `MAX_REQUESTS_PER_MINUTE = 9`. Held by
  `AppContainer` as a single instance because Home's inline search and the search screen are two
  separate `SearchViewModel`s — a per-ViewModel cooldown would let the two most-likely-consecutive
  screens spend the same budget twice.
- Coalescing is structural rather than a queue: `requests` is a `MutableStateFlow` holding at most
  one desired query, and `flatMapLatest` discards the settle wait *and* the governor wait whenever a
  newer query arrives. There is no data structure in which a backlog of old prefixes could form.
- An attempt is recorded when a request **starts**, for every outcome, and cancellation does not
  refund it — a request that has left the device has spent the quota whatever the app does with the
  answer.
- 429 was already classified as `RATE_LIMITED`; `Retry-After` was being discarded. New
  `RetryAfterHeader` parses both RFC 9110 forms and returns null (never zero) for anything
  unusable, so a malformed header falls back to a conservative 60 s rather than reading as "retry
  now". The governor takes the stricter of its own interval and the server's backoff, caps any
  server-named backoff at 5 minutes, and never lets a later laxer refusal shorten an earlier one.
- The automatic resume after a rate limit is bounded to **one** attempt. Written unbounded first,
  which produced request → 429 → re-arm → request forever; the test suite hung on it.
- `SearchUiState` gained `awaitingRemotePermit`, `rateLimited` and `narrowedLocally`. The remote
  result set is kept separately from the displayed list so narrowing is non-destructive and can
  widen again.
- Two defects found by writing the tests: `waitUntilPermittedMs` computed `Long.MIN_VALUE - now`
  for "no rule applies", which **underflows** to a ~292-million-year wait and parked every first
  search (invisible at a clock of 0, which is why the first governor test passed); and the
  explicit-search dedupe guard swallowed **Retry** entirely, because a finished search leaves the
  requested query set. Both are now pinned by their own tests.
- `LIVE_SEARCH_DEBOUNCE_MS` (600 → 500 earlier in 1.0.2) is replaced by `REMOTE_SEARCH_SETTLE_MS`
  (1000, then **500** once the primary provider changed — see below). The request rate is bounded by
  the governors, not by this constant.

#### Search-a-licious as the primary provider (later in 1.0.2)

- **The feasibility gate was measured before anything was wired.** Legacy `cgi/search.pl`, at 7 s
  spacing: **503 on 5 of 7** representative queries. `search.openfoodfacts.org/search`: twelve
  back-to-back requests, all 200, 136–202 ms, no throttling, no auth. Verified again end-to-end on
  the emulator through the production wiring: **7/7 queries, 20 hits each, 78–106 ms** after the
  first. Bench: `SearchALiciousLiveDiagnosticTest` (prints, asserts almost nothing — a network test
  that fails the build on a flaky connection teaches the team to ignore it).
- **`product_quantity_unit` is not in the Search-a-licious index** — 0 of 140 hits across seven
  queries, and requesting it by name returns nothing rather than erroring. It is
  `PackageBasisResolver`'s primary evidence, so the basis now resolves from free-text `quantity`
  alone on that path and resolves less often (`pasta`: 3/20 vs the legacy 18/20). **No resolver rule
  was weakened to compensate.** A hit with no basis shows no number, which is the existing §13 rule;
  the authoritative figure still comes from the canonical barcode lookup after selection.
- **`brands` is a JSON array here and a comma string on the legacy path** (137 of 140 hits).
  `FirstOfStringOrArray` reads either, scoped to that one field for the same reason
  `LooseNumericText` is — the carbohydrate values keep strict typing.
- `langs=nl,en` is load-bearing, not decoration: without it `product_name_nl` is absent from every
  hit, and Dutch recall collapses (`hagelslag` 449 matches with it, 26 without). This is **input
  recognition**, not localization — the UI stays English (owner decision 10).
- `FallbackProductSearch` (pure, `domain/`) is itself a `ProductSearchSource`, so no ViewModel or
  screen knows there are two providers — which is what makes the migration reversible: pointing
  `AppContainer.searchSource` at the legacy source alone restores the previous behaviour exactly.
  Fallback-eligible: `OFFLINE`, `TIMEOUT`, `SERVER`, `MALFORMED`. **Not** eligible: `RATE_LIMITED`
  (answering "you are asking too often" by asking elsewhere is the behaviour the limit exists to
  stop) and, crucially, a legitimate `NoMatches` — that is an answer, and falling back on it would
  double the cost of every search for something genuinely absent.
- **A cancelled query cannot spend a fallback request, and that needed an explicit guard.** Found by
  test, not by reading: a primary whose transport ignores cancellation returns an ordinary `Failed`,
  and `fallback.search` may then run to completion without ever suspending — so nothing would have
  thrown. `currentCoroutineContext().ensureActive()` before the fallback call is what closes it.
  `CancellationException` is never caught anywhere in the chain.
- **The governor moved out of the ViewModel and down to the provider it protects.** It sat above the
  provider boundary, so leaving it there would have made every primary query wait out an interval
  sized for a different service. `GovernedProductSearch` wraps the legacy source only, keeps
  `MIN_INTERVAL_MS = 7000` and the shared cross-screen budget, and **refuses immediately rather than
  waiting** — a 7 s delay behind an already-failed primary is the stacked wait this migration must
  not create. The primary has its own instance at `PRIMARY_MIN_INTERVAL_MS = 300`.
  `RemoteSearchGovernor`'s clock parameter had to stay **last**: existing callers use a trailing
  lambda, and adding the interval after it silently rebound every one (caught by the compiler).
- Debug-only `SearchProviderLog` / `LogcatSearchProviderLog` (`adb logcat -s JtcSearch`) answers the
  one question device testing cannot answer by looking: which provider served this query. **No query
  text is ever logged** — stage, provider and result count only.
- **A pre-existing ViewModel test was measuring the wrong budget** and is re-aimed rather than
  relaxed: it asserted OFF's 9/min ceiling against traffic that now goes to a provider without one.
  The legacy budget is asserted where it is now enforced, in `GovernedProductSearchTest`.
- **One integration test was found vacuous by negative control and fixed.** The stale-fallback case
  passed with the generation guard deleted — it was measuring `flatMapLatest`, not staleness. It now
  uses a `NonCancellable` fallback, the only fake that reproduces the hazard, and fails without the
  guard. Same trap as the Dutch header fixture and the soft-keyboard geometry test.
- **Search-a-licious moved to `POST /search`.** Identical `q` semantics to the GET form (verified
  against the service's own OpenAPI document), so this is a transport change only. `langs` and
  `fields` are JSON **arrays** in the POST schema where the query string took comma-joined strings.
- **`@EncodeDefault` on the request body is load-bearing.** kotlinx.serialization omits a property
  equal to its default, and the shared `Json` does not set `encodeDefaults` — so every request would
  have serialised to `{"q":"…"}` alone and the *server's* defaults would have applied: `page_size` 10
  instead of 20, `langs` `["en"]` instead of `["nl","en"]` (which is what makes `product_name_nl`
  appear at all, so Dutch recall would have collapsed), and no field filter, pulling ~13 KB per hit.
  Every request still succeeded, so the failure was invisible; caught by asserting the request body
  rather than the outcome.
- **`NoMatches` vs `MALFORMED` is now decided by whether the provider *claimed* matches**, never by
  the mapped list being empty — which is true in both cases and is what made the original bug
  invisible. `hits` non-empty **or** `count > 0` with nothing usable ⇒ `MALFORMED` (fallback-
  eligible); empty `hits` with no positive `count` ⇒ `NoMatches` (an answer, no fallback). A mix of
  valid and malformed records still returns the valid ones — one bad record never discards good ones.
- **`SearchALiciousQuery.escape` prefixes Lucene's reserved characters**, in the provider only. The
  wider rule was chosen over a narrower one on measurement: whether a character acts as an operator
  depends on **position**, not identity (`(` is inert inside a word, an operator around one), and
  escaping the full set changed **no** query that already worked — `M&M's`, `Ben & Jerry's`,
  `70% chocolate`, `Haagen-Dazs`, `7-Up`, `Lay's`, `Côte d'Or` all returned identical counts and
  identical top hits. Apostrophes, `%`, `.`, `,`, spaces and all non-ASCII are untouched. The legacy
  fallback receives the user's text verbatim — it has no query language, so the same escaping there
  would send literal backslashes into a search that would match nothing.
- Nine negative controls run and restored byte-for-byte (hash-verified): GET restored / query in the
  URL (3 fail), unusable-hits→`NoMatches` (4), `MALFORMED` made ineligible (2), `NoMatches` made
  eligible (3), `ensureActive` removed (1), escaping removed (15), `@EncodeDefault` removed (1),
  generation guard removed (7). None vacuous.
- No change to the search request pipeline's generation check, the dedupe rules, query
  normalization, local narrowing, the minimum query length, the calculation, the schema, migrations,
  the §10 lookup priority, barcode detection, any OCR rule, or the 30 s product-refresh window. The
  canonical product path still reads Open Food Facts' product API directly, not the search chain.
- `CachedProductSearch` is a `ProductSearchSource` decorator wrapping the **primary only**, inside
  the chain. Wrapping the primary rather than the whole chain is what keeps a cached result's
  provenance answerable — a legacy answer is never filed under the primary's name — and it makes
  "a cache hit does not reach the fallback" structural rather than a rule. `AppContainer` holds one
  instance, so Home's inline search and the search screen share it for the same reason they already
  share one governor. 20 entries, 5-minute TTL, access-ordered LRU, memory only.
- Only `Found` is cached. Every `Failed` and `NoMatches` is passed through untouched: a cached 503
  would outlive the outage it described, and a cached "no matches" would tell someone a product does
  not exist because it did not five minutes ago, in a database strangers edit continuously. A
  cancelled search writes nothing, because the delegate never returns.
- A future `storedAtMs` counts as expired rather than fresh — a backwards clock change would
  otherwise pin an entry until real time caught up. Same rule and same reasoning as the 30 s
  product-refresh window.
- **Phrase boosting was evaluated and is not available on this deployment.** `boost_phrase` does not
  exist in the service's OpenAPI document (zero occurrences of "boost" or "phrase") and sending it
  is accepted with HTTP 200 and changes nothing at all — the most misleading of the three possible
  answers, since it would have looked enabled. Free-text Lucene phrase syntax does not work either:
  `"nutella"` returns **0**, `(coca cola)` returns 0, `coca^2 cola` returns 0 and `coca OR cola`
  returns **HTTP 500**, while `brands:"coca-cola"` returns 3283 and the service's own documented
  example works — so quoting is honoured only as a field-filter value. Recorded as a re-runnable
  diagnostic in `SearchALiciousLiveDiagnosticTest`, and it independently re-confirms the escaping.
- `SEARCH_FIELDS` dropped `lang`, which was requested and read nowhere. Measured before removing:
  240 bytes per response (12 bytes × 20 hits, 2.3%) across five queries with the **mapped products
  identical** for every one. The request's `langs` is untouched — that is what makes
  `product_name_nl` arrive, and it is a different thing from the per-hit `lang` echo.
- The field-list assertion is now an exact-list comparison. The previous `contains` checks could not
  see a field being **added**, which is precisely how `lang` went unnoticed; proven by negative
  control, which the old assertions did not catch.
- `page_size` stays at **20** on evidence: across the 48-query benchmark, Top20 (37/39) exceeds
  Top10 (36/39) by exactly one query, so a larger page would enlarge every response to buy at most
  one position.

#### Light/dark theme and Android system UI (later in 1.0.2)

Reported from a physical device: status-bar icons disappeared in Light mode, and several Settings
and Home elements were unreadable in Dark mode. Presentation only — no calculation, schema,
migration, §10 lookup priority, barcode, OCR, search or navigation behaviour changed.

- **Status-bar icons were unreadable in Light mode.** `enableEdgeToEdge()` was called with no
  arguments, so the bars resolved their own light/dark from the **device** configuration while the
  app's colours followed the user's selection — two authorities for one question. Choosing Light on
  a dark phone produced light icons on the cream background, which is invisible. Bar appearance now
  follows the **selected app theme** in every combination: Light theme → dark icons, Dark theme →
  light icons.
- **Dark-mode text and icons were black on near-black.** The Settings back arrow, the Settings
  title, the *Haptic feedback* row and Home's gear icon all rendered black in Dark mode. One cause,
  not four: the screens paint themselves with `Modifier.background(colorScheme.background)`, which
  fills a colour but provides no `LocalContentColor` — Material3's `Surface` is what normally does
  both, and the app has one, inside a dialog. Everything that did not name a colour therefore
  inherited `LocalContentColor`'s default of `Color.Black`. `JustTheCarbsTheme` now provides
  `onBackground`, rather than wrapping every screen in a `Surface` that would double-paint
  backgrounds the screens already draw (and would fight the camera screens, which are deliberately
  black in both themes).
- **Navigation-bar treatment now matches the theme.** Icon appearance follows the effective theme,
  and API 29+ contrast enforcement is disabled — its translucent scrim read as a grey band matching
  neither theme with three-button navigation. Safe only because icon contrast comes from the
  appearance flag against a solid, known theme colour. Gesture and three-button navigation both
  remain usable; API 26–28 is unchanged, guarded by a version check.
- **Theme switching is unchanged and still immediate.** The manifest declares
  `configChanges="…|uiMode"`, so this Activity is never recreated — which is also why the one-shot
  `onCreate` call could never re-run. The bar flags are applied from inside the composition, so a
  preference change reapplies them without an Activity restart, a flash or a navigation reset.

### Internal — theme

- New `resolveDarkTheme(themeChoice, systemInDarkTheme)` is the single authority both the Material
  colour scheme and the system bars read, so the two cannot drift again. Pure and system-free — the
  device state is a parameter, which is what makes it JVM-testable without an emulator.
- New `ThemeResolutionTest` (JVM, 10 cases) pins the six device-theme × app-selection combinations
  plus the two properties the consumers rely on: an explicit choice must ignore the device entirely,
  and `SYSTEM` must follow it in both directions. Verified non-vacuous by negative control —
  reintroducing the device read into the `LIGHT` branch fails 2 cases, including the one that
  mirrors the reported defect. The instrumented `ThemeDefaultTest` is unchanged and still asserts
  the **rendered** background, which is the seam where a wrong default is visible.
- Only `Theme.kt`, `MainActivity.kt` and the new test changed. `Color.Black`/`Color.White` elsewhere
  was audited and deliberately left: the camera screens are intentionally black in both themes with
  explicitly tinted foregrounds, and onboarding's white sits on saturated brand backgrounds, with
  its one theme-background slide already using semantic tokens.

### Verification — 1.0.2 release candidate

- **JVM 1015/1015** (up from 1004), 0 failures, 0 errors, **0 skipped**, `--rerun-tasks`, counted
  from JUnit XML rather than a wrapper exit code.
- **Instrumented 248/248** in **one whole-suite run**, 0 failures, **0 ignored**, counted from
  instrumentation status codes. Taken **after** every change in this version, so the figure covers
  the release rather than predating part of it.
- **Real-image OCR corpus 37/37**, unchanged — `RealImageOcrTest` 15, `ProductionStillPipelineTest`
  8, `SelectedTableProductionTest` 6, `EvidencePipelineProductionTest` 8.
- **Room migrations 10/10** (`JustTheCarbsDatabaseMigrationTest`).
- **Lint exit 0 on both debug and release**, 0 errors, **41 advisories** — unchanged baseline, so
  this version's work added none.
- **Debug APK and release AAB both build from `clean`.**
- **OSV dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities**, control
  query positive.
- **R8 privacy barriers re-checked** on the release build's `mapping.txt`: `ScanEvidenceRecorder`
  and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`
  and `ScanTrace` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate` and
  `PackageBasisResolver` retained as real classes. Release manifest: three disclosed permissions,
  one exported component of ours (`MainActivity`), **no `FileProvider`**.
- **Not verified on physical hardware.** Everything above is JVM and emulator. The artifact hash,
  size and signer are recorded in [`docs/version-history.md`](docs/version-history.md) only once
  Play accepts the upload.

### Play Store release notes

```
Product search now updates as you type, with faster and more reliable results. Returning to a
search you just ran is instant, and results stay on screen while the next ones load instead of
blanking out. Product names containing brackets, symbols or punctuation now find the right
products. Also fixes status bar icons being invisible in light mode, and text and icons being
hard to read in dark mode, with better light and dark theme integration throughout.
```

457 characters, within the 500 limit.

Internal notes, not for Play: the provider names, the request budgets, the settle delay, the
fallback architecture and the move to POST are engineering details with no user-facing wording —
the privacy improvement is real but describing it would need the reader to know the search text was
previously in the address, which is not something the app ever showed them. The fewer preview
figures on search cards are also deliberately unmentioned — tapping a result still fetches the full
product, so there is no user-visible loss to describe. The theme entries are worded as the two
things a tester can see and check — light-mode status bar, dark-mode readability — rather than by
mechanism; `LocalContentColor` and edge-to-edge mean nothing to a reader of this field.

---

## 1.0.1 (versionCode 2)

| | |
|---|---|
| Track | **Closed testing** |
| Released | 2026-08-28 |
| Built from | `45f3dd9`, `clean` build (evidence recorded in `cb2d549`) |
| Artifact | `app-release.aab`, 35,626,125 bytes |
| SHA-256 | `8c4e6da7998b81a38fbb23234b008a8088ab57149d0d0f6a8b3e146c4d7bfd30` |
| Upload key | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` — the same key as `versionCode 1`, which is what lets Play accept this as an update |

The first update of the closed beta. A small, conservative quality pass: no feature work, no schema
change, no migration, no new permission or dependency, no change to barcode scanning, and no change
to the calculation or to any OCR safety rule.

The signer certificate was read from the built bundle with `keytool -printcert -jarfile` before
upload, rather than inferred from the build succeeding — the Gradle signing guard cannot tell a real
upload key from a disposable one, so a green `bundleRelease` is not evidence that an uploadable
artifact exists.

### Play Store release notes

Pasted into Play Console *What's new*. 411 characters, within the 500 limit.

The label-scan change is worded as *reducing the wait after capture* rather than as "faster": the
work removed is real and measured in the code, but no before/after figure was taken on a physical
device, and this file's rules forbid promising a fix that has not been verified on one.

```
Fixes a problem where dragging the crop box while scanning a nutrition label moved it far less than
expected. Searching for a very short product name now explains why it needs more letters instead of
appearing to do nothing, and a product that fails to save now says so instead of looking like the
button was missed. Uses less mobile data when looking up a product, and reduces the wait after
capturing a label.
```

### Fixed — reproduced by a test that failed before the fix

- **Every new product was fetched from Open Food Facts twice.** Not an edge case and not a race:
  *every* first-time scan cost two requests. On a cache miss the lookup downloaded the product and
  saved it, and the background refresh that runs immediately afterwards then found that fresh row,
  decided it had something to refresh, and re-downloaded the same barcode microseconds later — for
  a budget of 15 reads per minute shared by everyone on the same connection. A freshly downloaded
  product is now stamped as synced, and a refresh is skipped for any product synced within the last
  30 seconds — which suppresses that immediate duplicate, and also means reopening the same product
  within half a minute does not re-check it. Anything cached longer ago than that still refreshes
  normally, which is the only way a reformulated product can be noticed. Measured before and after:
  a first-time lookup went from two requests to one.
- **A dragged crop rectangle moved a fraction of the distance your finger did.** Drag events arrive
  as small increments, and the handler recomputed each one from the rectangle as it stood when the
  gesture *began* rather than as it stood after the previous increment — so the increments replaced
  each other instead of adding up. Measured: a drag delivered as ten 10-pixel steps moved the
  rectangle 10 pixels instead of 100. Resizing from every corner was affected the same way, and a
  second drag restarted from the original rectangle, discarding the first.
- **Saving a manually entered product could fail with nothing on screen to say so.** If the write
  failed, the Save button simply became tappable again and the screen was otherwise unchanged — no
  message, no error, nothing. The only available reading was that the tap had not registered, so the
  natural response was to tap again and fail again. A failed save now says the product was not
  saved; the message clears as soon as you edit a field or the next save succeeds. A related case
  was also corrected: when a product carrying a scanned portion failed to save, the app moved on to
  the calculator for a product that did not exist. It now only does that when the product genuinely
  landed and it was the portion alone that failed — which is what the existing message about the
  portion has always described. The two writes are made as two separate steps so which of them
  failed is known from where the failure happened; an earlier draft of this fix inferred it
  afterwards by checking whether a product row existed, which gave the wrong answer whenever that
  barcode was already stored from an earlier scan — a failed product write was then reported as a
  portion failure and the calculator opened on the old record.

### Improved

- **Searching for a product name shorter than three characters did nothing at all.** The search is
  deliberately refused below three characters — two letters match thousands of products — but the
  refusal was silent: no request, no spinner, and the same "type a product name" prompt the screen
  already showed. The screen now says a longer query is needed, and announces it for screen readers.
  Nothing about when a search is actually sent changed.

### Changed

- **The scanner starts reading a captured label sooner.** Live camera frames stop being analysed
  the moment you tap capture, instead of finishing a full parse whose result was then thrown away.
  That parse sat directly between the shutter and the reading of the captured photo, so discarding
  it earlier shortens the wait. Nothing about what the scanner accepts, refuses or reports changed.
  The framing hint can also no longer appear over the capture screen after you have tapped capture.

### Hardening — no defect reproduced, kept as protection

- **The label scanner's autofocus fallback no longer runs after the screen closes.** Capture waits
  up to 1.2 s for autofocus and fires the shutter anyway if it never reports back; nothing cancelled
  that fallback, so closing the scanner within that window left it queued to run against a camera
  already torn down. That state is reachable by reading the code, and the fallback now does nothing
  once the screen is gone. **The resulting failure was never reproduced on a device**, so no
  specific crash is claimed — this is defensive, not a fixed crash report. The previous draft of
  this entry described it as a crash; that was not established and has been corrected.
- **The crop screen no longer shares its grabbed-corner state across the whole app.** Which corner a
  drag had hold of was kept in state shared by every scan in the process rather than per screen, and
  a drag interrupted by *Retake* could leave it set. It is now per screen, so a new capture cannot
  inherit it. **No misbehaviour was ever reproduced from this** — an earlier draft of this entry
  claimed the next scan's first drag would resize instead of move, which was not established and has
  been corrected. This is state isolation, not a fixed defect.

### Internal

- The lookup test's fake cache previously discarded everything written to it, which is precisely
  what hid the double fetch: with nothing stored, the refresh returned early and its request never
  appeared. It now persists, and four cases cover the fresh lookup, the cached refresh, overlapping
  lookups and cancellation. All four fail on the old code.
- Crop gesture state extracted to a pure class with 8 JVM tests covering accumulation, per-corner
  resizing, consecutive gestures, interrupted gestures and the bounds rules. Verified non-vacuous:
  reintroducing the old captured-value read fails exactly the accumulation cases.
- **The instrumented flake is root-caused and fixed.** It was the soft keyboard, not timing and not
  a layout defect: Gboard is a real 641-pixel window over the bottom of the screen, and the
  assertions that failed were on elements pinned there. Measured — the meal bar sits at a stable,
  fully on-screen position when its test runs alone, and with the keyboard disabled the class passed
  19/19 three times, while with it enabled exactly one arbitrary test failed per run. The tests now
  dismiss the keyboard after typing, as a user does. No retries, no `@Ignore`, no sleeps and no
  weakened assertions were used.
- Five repository tests pin the new refresh-freshness rule, including the two cases that decide
  whether it is safe: a never-synced product still refreshes, and a sync timestamp in the future
  (a device clock change) is not read as freshness.
- A lookup that fetches from the network now hands back the same record it caches. It saved a copy
  stamped with the sync time but returned the unstamped original, so the two disagreed about when
  the product was last synced. Nothing read that field off the returned value, so no user-visible
  behaviour changed; two tests now pin that the returned and cached records match, and that a cache
  hit is still returned untouched.
- Ten tests pin the two failure notices above: six on the manual-entry save (reported, cleared by an
  edit, cleared by a later success, plus the three failure-classification cases — product failure
  over an existing local row, portion failure after a successful product write, and full success
  still navigating) and four on the search refusal (reported, cleared by an edit, cleared by a
  longer submission, and **not** raised for a blank field — an empty box is not a refused search).
  Verified non-vacuous by negative control: removing each notice, and reinstating the discarded
  after-the-fact failure inference, each fails exactly its own tests and nothing else.
- `ProductRepository.saveProductWithPortionUnit` now has no production caller — manual entry makes
  the same two writes itself so it can tell which one failed. It is left in place with its existing
  repository tests rather than removed as part of this change.

### Verification recorded at upload

- JVM 801/801 and instrumented 218/218, both 0 failures, 0 errors, 0 skipped — counted from JUnit
  XML and instrumentation status codes, not from a wrapper exit code. The instrumented figure is
  **one whole-suite run** (15m25s, 0 ignored) taken after the last code change in this version.
- Real-image OCR corpus 37/37, unchanged.
- Lint exit 0, 0 errors, 41 advisories.
- OSV scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query positive.
- R8 privacy barriers: `ScanEvidenceRecorder` and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`;
  `ScanEvidenceExport` and `OcrDiagnosticsReport` absent from `mapping.txt`. Safety classes retained
  as real classes: `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate`, `PackageBasisResolver`.
- Release manifest: CAMERA, INTERNET, ACCESS_NETWORK_STATE (transitive, disclosed); one exported
  component of ours (`MainActivity`); no `FileProvider`; not debuggable; `allowBackup="false"`.
- Bundle carries no language configurations — English-only, so the locale restriction still holds.

### Known gaps at release

- **Nothing in this version was verified on physical hardware.** The crop-drag fix, the scanner
  disposal guard and the latency work are emulator-and-JVM-only. The crop fix in particular is the
  one change a tester is most likely to notice, and it has only been proven by measurement in a pure
  gesture test.
- The §8a Play-delivered smoke test has not been run against this build.
- Unchanged from 1.0.0: no Dutch package scanned on physical hardware; keystore backup not
  restore-tested; countable portions against a real Open Food Facts `serving_size` remain
  fixture-only.

---

## 1.0.0 (versionCode 1)

| | |
|---|---|
| Track | Internal testing → **Closed testing** (promoted unchanged) |
| Released | 2026-08-26 (internal); promoted to closed testing thereafter |
| Built from | `68c85a3` (recorded in `0b2312f`), `clean` build |
| Artifact | `app-release.aab`, 35,624,186 bytes |
| SHA-256 | `37be02324dec011c74edd876d346077a03dc611096eae4d98374ab791c7e604b` |
| Upload key | `1E:21:23:F3:10:4C:C4:C1:87:EC:C2:F1:16:2A:A1:98:57:E2:7C:98:71:77:FA:A0:15:BD:B8:62:88:F8:C4:F5` |
| Play review | *Not reviewed* at upload; listing name shown as `app.justthecarbs (unreviewed)` |

First build delivered to testers via Google Play. Hash and certificate were verified against the
file on disk, and Play accepted the artifact — so bundle format, upload signing and Play App
Signing enrollment are proven for this version.

**Promoted from internal to closed testing unchanged**, and recorded here as **one artifact moving
between tracks** rather than as two releases: the bytes, the hash and the `versionCode` are
identical, and a promotion produces no new artifact to archive. 12+ testers are opted in on the
closed track and the testing period is running. A second section here would imply a second build
exists and would break this file's one-section-per-`versionCode` rule.

### Play Store release notes

Pasted into Play Console *What's new*. 197 characters, within the 500 limit.

```
First release.

Scan a barcode or a nutrition label, enter a portion, and read the carbohydrate grams. Add several
portions together for a meal. Works offline for products you have already scanned.
```

### Added

- Scan a food barcode, enter a portion, read the carbohydrate grams. Open Food Facts lookup with a
  local cache, manual barcode entry, and search by product name as a fallback from a failed lookup.
- **Nutrition-label scanning.** Capture a label, confirm the table with a draggable rectangle, and
  read the per-100 figure. Where automatic reading is not confident the frozen photo stays on
  screen and you point at the value yourself, rather than the scan dead-ending.
- **Countable portions** — "2 slices" as well as "65 g", when a trustworthy per-item weight or a
  per-serving carbohydrate figure exists.
- **Temporary meal.** Add several calculated portions and read one total. One meal only; no history.
- **Usual portions.** Portions repeated for a specific product become one-tap shortcuts.
- Light/dark/system theme, onboarding, and a settings screen with the privacy policy and
  Open Food Facts attribution.

### Notes

- The app calculates carbohydrate amounts only. It does **not** calculate insulin, and it is not a
  diet tracker.
- English-only interface. Dutch, German and other label text is still recognised when scanning.
- No analytics, no accounts, no advertising. Data stays on the device apart from Open Food Facts
  lookups.

### Verification recorded at upload

- JVM 754/754, instrumented 218/218, both 0 skipped.
- Lint exit 0, 0 errors.
- OSV scan: 226 resolved release artifacts, 0 known vulnerabilities.
- Release manifest: CAMERA, INTERNET, ACCESS_NETWORK_STATE (transitive, disclosed); one exported
  component (`MainActivity`); no `FileProvider`.

### Known gaps at release

- Not verified across a range of physical devices; Samsung Galaxy specifics untested.
- No Dutch package scanned on physical hardware.
- Keystore backup not restore-tested.
