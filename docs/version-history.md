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
