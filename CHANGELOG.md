# Patch notes — Just the Carbs

Change list per version. **Newest first.** This file carries the version being worked on and the
one currently on a track; uploaded versions are copied into
[`docs/version-history.md`](docs/version-history.md), the append-only archive that records each
artifact's hash, size and signer.

## How this works during the closed beta

Several builds go out over the testing period, each driven by tester findings and feedback. So at
any moment there is normally **one open version section at the top** — the next build — which keeps
accumulating changes until it is actually pushed.

- **Add new work to the open version's section.** Do not start a new version heading for each
  change; a version number is claimed once and is worth one upload.
- A version stays open until its AAB is built and accepted by Play. Only then is it a closed record
  and copied into [`docs/version-history.md`](docs/version-history.md).
- The test figures and the Play *What's new* text inside an open section describe the work **so
  far**, and must be re-checked and rewritten before upload.
- `versionCode` is bumped **once**, when the section is opened — not per change. Bumping it again
  mid-version would strand the notes against a number that never shipped.

## Conventions

- **Unreleased** is for work not yet assigned to a version. During the beta most work goes straight
  into the open version section instead; use Unreleased when the next build's number is not decided.
- A version heading is `## <versionName> (versionCode N) — <date> — <track>`, so a tester report
  quoting "1.0.0" matches exactly one artifact.
- Entries group by **Fixed / Changed / Added / Internal**, written for whoever reads them next: a
  defect line says what the user would have seen, not which function moved.
- `versionCode` is unique per upload and **never reused** — Play rejects a duplicate. It is bumped
  when a version section is opened, then left alone until that build ships.
- When a version is uploaded, copy its section verbatim into `docs/version-history.md`. Nothing is
  rewritten on the way across, so the record of what shipped stays what it said at the time.
- Every version also carries a **Play Store release notes** block — the *What's new* text, written
  for users and far less granular than the list above it: ≤500 characters, small fixes grouped into
  one line, no health claim and no mention of diabetes (§44 §7.1 binds this field). Rules are at the
  top of [`docs/version-history.md`](docs/version-history.md).

---

## 1.0.1 (versionCode 2) — BUILT FOR CLOSED TESTING, awaiting upload

The **first update of the closed beta**. The change list below is complete, the test figures were
taken after the last change in it, and a signed release bundle has been built from this committed
tree for upload to the closed track.

**It is not history yet.** This section moves to
[`docs/version-history.md`](docs/version-history.md) — verbatim, with the artifact's hash, size and
signer — **only once Play has accepted the upload**. A build that never left the machine is not a
release. Until then `versionCode 2` stays claimed and unshipped, and any further change goes into
this same section rather than opening a new version.

A small, conservative quality pass taken during the closed beta. No feature work, no schema change,
no migration, no new permission or dependency, no change to barcode scanning, and no change to the
calculation or to any OCR safety rule.

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
- **JVM 801/801** (up from 791) and **instrumented 218/218**, both 0 failures, 0 errors and 0 skipped,
  counted from JUnit XML / instrumentation status codes rather than from a wrapper exit code. The
  37-test real-image OCR corpus is 37/37, unchanged. Lint exit 0, 0 errors, 41 advisories. OSV
  dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query
  positive. The instrumented suite completed as **one whole-suite run** (218/218, 0 ignored,
  15m25s) taken **after** every change in this version, including the two that add a line to a
  rendered screen — so the figure covers this release rather than predating part of it.

### Play Store release notes

The text to paste into Play Console's *What's new*. It covers the change list above and nothing
else; if more work lands in this version before upload, rewrite it and re-count the characters.
Deliberately much less granular than the list above — see the rules in
[`docs/version-history.md`](docs/version-history.md).

Currently 411 characters, within the 500 limit.

Note on wording: the label-scan change is described as *reducing the wait after capture* rather than
as "faster". The work removed is real and measured in the code, but no before/after figure has been
taken on a physical device, and the archive's rules forbid promising a fix that has not been
verified on one.

```
Fixes a problem where dragging the crop box while scanning a nutrition label moved it far less than
expected. Searching for a very short product name now explains why it needs more letters instead of
appearing to do nothing, and a product that fails to save now says so instead of looking like the
button was missed. Uses less mobile data when looking up a product, and reduces the wait after
capturing a label.
```

---

## 1.0.0 (versionCode 1) — 2026-08-26 — Internal testing → Closed testing

First build delivered to testers via Google Play, and the version currently on a track. The **same
artifact** was promoted from internal to closed testing — one bundle, one hash, two tracks, not two
releases — and the closed-testing period is running with 12+ testers opted in. Full entry, including
the artifact hash, in [`docs/version-history.md`](docs/version-history.md).

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
