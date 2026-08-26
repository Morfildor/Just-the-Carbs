# Release-closure device verification — Samsung SM-S928B

Written 2026-08-25 for the final release-closure pass. Everything here is **owner action on physical
hardware**; none of it can be closed from this machine.

Two existing protocols already cover the scanner in depth and are **not** repeated here:

- `docs/physical-device-scanner-qa.md` — capture-first gating, focus/exposure, crop gesture, memory
  at 8 MP, barcode non-regression, evidence collection.
- `docs/manual-qa.md` — the full functional sweep (§0–§20).

This sheet covers only what the closure pass added: **latency stage capture** and a **20-item
release sweep** whose items are release blockers rather than feature checks.

---

## Part A — OCR latency capture

### Build to use

A **debug** build. Release records no evidence and logs nothing, by design — that is the privacy
guarantee, not an oversight. The consequence is that every device measurement is taken on a build
carrying work a user will never pay for, so the trace now reports **two** totals and you must quote
the right one.

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat :app:assembleDebug
C:\atools\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\atools\sdk\platform-tools\adb.exe logcat -c
C:\atools\sdk\platform-tools\adb.exe logcat -s JustTheCarbsOCR:D > scan-latency.txt
```

### What one scan prints

```
acquisition 412ms (shutter to file)
scan 1180ms (user-visible 640ms) | evidence-text 280* · jpeg-decode 210 · mlkit 190 · evidence-diagnostics 160* · rotate 120 · relevance 60 · parse 24 · handoff 3 · evidence-meta 2*
strategy-B scan 730ms | mlkit 610 · crop 90 · parse 18
selected table outcome=FILTERED elements 240 -> 96 elapsed=760ms reading=Confident
```

**A trailing `*` marks an off-path stage** — debug-only work, or work that runs after the result was
already handed to the UI. `user-visible` is `scan` minus every starred stage, and **it is the only
figure the acceptance target below is about**. Quoting `scan` describes a build nobody ships.

### The stage map

| §3 stage | Where to read it |
|---|---|
| shutter → file available | `acquisition NNNms (shutter to file)` |
| decode / orientation | `jpeg-decode`, `exif`, `rotate` in the `scan` line |
| initial OCR (Pass A) | `mlkit-input` + `mlkit` in the `scan` line |
| crop / selection | user think time (not instrumented — it is a gesture, not a computation) |
| selected-region OCR (Strategy B) | `strategy-B scan …` line; `mlkit` within it |
| parsing | `parse`, in whichever line you are reading |
| result handoff | `handoff` in the `scan` line |
| post-handoff evidence export | every starred stage, plus the async `passA.png` encode, which is off-thread and appears in no total at all |

### Acceptance

For an **ordinary successful label scan** on this flagship:

- `acquisition` + `user-visible` + `strategy-B` ≤ **2000 ms** to result display.
- No unexplained gap: the starred and unstarred stages must roughly account for `scan`. A large
  residual means time is being spent somewhere with no mark on it, which is itself the finding.
- **No 5 s timeout hit.** `Selected-region OCR timed out after 5000ms` must not appear on any
  successful ordinary scan. If it does, record the package and the lighting — do not raise the
  timeout; it is a hang guard and moving it up hides the cause.

Record the median and worst of **11 scans**, matching the 2026-08-25 baseline (median 3.55 s, worst
8.1 s) so the two are comparable. Nothing on this machine can produce the "after" figure.

---

## Part B — the 20-item release sweep

Mark each `PASS` / `FAIL` / `N/A` with a note. Any FAIL is a release blocker.

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Scan a barcode of a product in Open Food Facts. | Product screen with a name and a per-100 figure. | |
| 2 | Scan the **same** barcode again immediately. | Loads from cache instantly. No second network request (check with the device offline after the first scan — it must still work). | |
| 3 | Scan barcode A, and while it is still loading (throttle the network or use a slow connection) navigate back and scan barcode B. | The screen shows **B**. A must never appear under B's barcode, and A's slow answer must not replace B's. | |
| 4 | Turn on airplane mode, scan a barcode not previously cached. | A clear network failure with a retry. **Never** "no matches" and never a search suggestion — the same host is down. | |
| 5 | Scan a product OFF holds with no carbohydrate value. | An explicit "no usable value" state offering manual entry. Never `0 g`. | |
| 6 | Scan a clear nutrition label (Sondey or Kinder). | The printed per-100 value, correct basis. See `manual-qa.md` §15f. | |
| 7 | Scan a label the app cannot place confidently. | The ambiguity card, up to 3 candidates, **or** the assisted-reading path. Never a silently chosen value. | |
| 8 | On any accepted reading, tap *Correct*. | Manual entry opens **pre-filled** with the detected number and the basis chip visible and changeable. | |
| 9 | The `869 / 22 / 12` pattern (a per-portion column surviving when the per-100 column is corrupted). | The app must **refuse** or ask, never report the per-portion figure as per-100. This is the regression the typed column provenance exists for. | |
| 10 | A label printing `Koolhydraten` and `waarvan suikers` merged onto one recognised row. | The sugars figure must **never** be returned as total carbohydrate. Refusal is a pass. | |
| 11 | A genuine zero-carb label (e.g. still water, some cheeses). | `0 g` **is** reported. False-zero protection must not suppress a true zero. | |
| 12 | Capture 10 labels in rapid succession without leaving the scanner. | No crash, no progressive slowdown, no `OutOfMemoryError`. | |
| 13 | Capture, then tap **Retake** immediately while the debug evidence is still being written. | Returns to live camera. **No native crash.** This is the recycled-bitmap race the async writer exists to close — a crash here is a hard blocker. | |
| 14 | A product with a very long name (>60 chars). | Name truncates or wraps; it never pushes the result or the portion field off screen. | |
| 15 | Open the portion field so the keyboard covers the lower screen. | The portion field, the result and the ± row all remain reachable by scrolling. | |
| 16 | Settings → 1.8× font scale, then repeat 14 and 15. | Every action still composes and is reachable. See the LazyColumn note in `CLAUDE.md`. | |
| 17 | Deny the camera permission; then deny it **permanently**. | Manual entry stays a first-class path. The permanently-denied state explains how to re-enable it and does not loop. | |
| 18 | Start a label scan, background the app mid-OCR, return. | No crash. Either the result or a clean re-arm — never a stale value from the abandoned session. | |
| 19 | Press Back rapidly through scanner → product → home. | No crash, no orphaned camera session, no black preview on re-entry. | |
| 20 | Use a recent product, force-stop the app, relaunch. | The recent product is present; its remembered portion pre-fills; a persisted meal survives. | |

### Do not weaken safety to hit the latency target

Items 9, 10 and 11 are the safety triad. A refusal is a pass. If the ≤2 s target and any of those
three ever conflict, the target loses — a confidently wrong carbohydrate value is the worst outcome
this app can produce, and the user doses insulin from the number on that screen.

---

## Part C — the 2026-08-26 release-blocker pass

Added for the fixes in that pass. Items 21–24 have automated coverage that passes; what the device
adds is the **whole user-visible loop** — the Settings dialog, the coroutine, Room's real connection
with foreign keys on, and a genuine re-scan. Item 25 has no automated coverage at all and 26–28 are
long-standing gaps.

Use the **release** build for this part, not the debug one: it is the artifact being shipped, and
these are behaviour checks rather than latency measurements.

| # | Test | Expected | Result |
|---|---|---|---|
| 21 | Use a product, star it as a favourite, use it again with a countable portion ("2 slices"). Settings → **Clear recent history** → confirm. | The product, its value, its verification and its star all survive. It no longer appears under recents by usage, its portion field is empty, and the count no longer pre-fills. **A favourite must not be exempt.** | |
| 22 | Repeat 21, then reopen that product. | The *Usual* shortcuts are gone. This is the half that used to survive the clear entirely. | |
| 23 | Build up two products with portion units and repeated portions. Settings → **Clear saved products** → confirm. Then **scan one of the same barcodes again**. | The product comes back from Open Food Facts as if new: no usual portions, no saved portion units, no remembered portion. Nothing from before the clear reappears. | |
| 24 | Before and after 23, check Settings and the meal. | Theme, result style and haptics are unchanged, and an in-progress meal is still there. Neither is saved product data, and the privacy policy now says so. | |
| 25 | Scan a **multipack beverage** — a 6 × 33 cl or 6 × 250 ml pack with a real barcode. | The portion field is locked to **ml**, never g. No pack-size shortcut is offered (whether "the pack" is one bottle or six is still undecided, deliberately). | |
| 26 | Scan a product whose OFF record has no readable quantity (or temporarily one you know lacks it). | The **"Grams or millilitres?"** screen, leading with *Enter manually*. **Never** a product screen silently asking for grams. | |
| 27 | Search by name for a product with an unreadable quantity. | The hit still appears with its name, brand and photo, and shows **no carbohydrate figure**. Selecting it runs a normal lookup and lands on item 26's screen. | |
| 28 | Open the barcode scanner, then leave it — Back, Home, and by switching apps. Return and reopen it. | The camera indicator clears when you leave, the preview comes back live on re-entry, and no other app reports the camera as busy. | |
| 29 | Set the device language to **Dutch** and relaunch. | The whole interface is in English, consistently — including system-supplied dialog buttons. No mixed-language screen anywhere. | |
| 30 | With the network throttled hard, scan a barcode, then background the app and return before it finishes. | The result arrives or fails cleanly. No stale product under the wrong barcode, no spinner that never ends. | |

Items 21–24 are the ones to run first: they are privacy controls, and a privacy control that
under-delivers is a defect the user cannot see and has no way to check.

---

## Part D — Dutch label recognition (2026-08-26)

The vocabulary fixes in this pass were measured against *synthetic* Dutch tables built from Dutch and
Belgian packaging conventions. **No Dutch package has been photographed since**, so this part is the
gate on the whole Dutch-recognition claim, and it is the highest-value hour of testing left.

Use real packaging from a Dutch supermarket. For each, record the printed carbohydrate figure first,
then what the app reports.

| # | Package to find | Why this one | Result |
|---|---|---|---|
| 31 | Anything whose table reads **`per 100 gram`** rather than `per 100 g` | The single worst bug found: it resolved no column, so the scan returned "couldn't find carbohydrates" with a correct value plainly on screen. | |
| 32 | A drink whose table reads **`per 100 ml`** | The basis must come back millilitres and the portion field must ask for ml. | |
| 33 | A label printing **`Koolhydraten`** and **`waarvan suikers`** on separate lines | The ordinary case. The total must win; the sugars figure must never appear. | |
| 34 | A **dense or curved** label where those two lines nearly touch, photographed slightly tilted | The merged-row case. **A refusal is a pass.** What must never happen is the sugars figure being offered — before this pass it was offered as an equal choice. | |
| 35 | A label using a Dutch sugar compound: **`melksuiker`**, **`druivensuiker`**, **`vruchtensuiker`** or **`sacharose`** | These are the terms that were missing. Common on dairy, juice and confectionery. | |
| 36 | A label printing **`Koolhydraat`** (singular) or an abbreviated **`Koolhydr.`** | Small packs abbreviate. Both read nothing at all before this pass. | |
| 37 | A label where **`Kool-`/`hydraten`** is hyphenated across a line | Dutch splits long compounds; the hyphen normalizes to a space. | |
| 38 | A two-column label: **`per 100 g`** beside **`per portie`** or **`per stuk`** | The per-100 figure must win, and the serving column must not displace it. | |
| 39 | A label with a **`per plak`** or **`per plakje`** column (cheese, cold cuts, cake) | Should now offer to save the slice as a countable portion. Not offering it is a minor failure; reporting the *per-slice* figure as per-100 is a blocker. | |
| 40 | Any Dutch product in Open Food Facts whose `serving_size` reads **`1 plak (20 gram)`** | The remote path, not the camera: the spelled-out unit used to drop the weight silently. | |

Item 34 is the one to spend time on. It is the only case here where the failure mode was
*plausible-looking* rather than obviously broken.
