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
