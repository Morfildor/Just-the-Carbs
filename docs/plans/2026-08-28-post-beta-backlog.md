# Post-closed-testing backlog — found 2026-08-28

Ideas noticed during the 2026-08-28 conservative quality pass and **deliberately not implemented**,
because each is larger than "would I push this today to people already testing the app?" allows.

Nothing here is a known defect on a tester's device. These are improvements and open questions.
Revisit after the closed-testing period, one at a time, each with its own measurement.

---

## 1. `LabelAnalyzer` has no JVM test coverage at all

**Why it was not done now:** it needs fakes for `ImageProxy`, `InputImage` and the ML Kit
`Task` API, which is a test-infrastructure project rather than a change to the class.

**Why it matters:** `LabelAnalyzer` is the boundary every scan crosses, and it holds real
concurrency — `inFlight`, `pendingStill`, `paused`, `closed`, and the still-request replacement path
that deletes a superseded capture file. The pause-ordering change made in this pass was argued from
reading `AmbiguityStabilityTracker.reset()` rather than demonstrated by a test of the analyzer
itself. That argument is sound, but it is an argument.

**Shape of the work:** extract the recognition callback into a pure function over
`(OcrDocument, paused: Boolean)` so the decision logic is JVM-testable without ML Kit, leaving only
the ML Kit plumbing untested. Do not restructure the still path to achieve this.

## 2. The focus-timeout fallback is a `postDelayed` with no cancellation handle

This pass fixed the **symptom** — `fireOnce` now checks `disposed`, so a fallback that outlives the
screen does nothing. The queued message still runs, it just returns immediately.

A cleaner design holds the `Handler` and calls `removeCallbacks` in `onDispose`, or moves the bound
into a coroutine on `rememberCoroutineScope` where cancellation is structural. Both are correct;
neither was done during the beta because the guard is sufficient and the alternatives touch the
capture path, which is the single most latency-sensitive code in the app.

**Do not treat this as unfinished work unless the capture path is being changed anyway.**

## 3. Duplicated per-error-message `when` blocks across three screens

`LookupError` is mapped to title/body strings in `HomeScreen`, `ProductScreen` and `SearchScreen`,
with the same five branches in each. A shared mapping would remove the risk of the three drifting.

**Explicitly rejected for now** as refactoring for cleanliness: the blocks are currently identical
and correct, and consolidating them touches three user-facing screens for no behaviour change. If a
fourth caller appears, or the three ever disagree, that is the moment to unify them.

## 4. Instrumented coverage for the two scanner defects fixed in this pass

The dispose-during-focus crash and the stale crop handle are both **lifecycle and gesture**
behaviours. Neither is covered by a test, because both need a Compose test that disposes a
composable mid-gesture against a real camera or a faked `ImageCapture`.

Both fixes are small, local, and argued from the code paths in their own comments — but "argued"
is weaker than this repo's usual standard, and that gap is recorded here rather than hidden.

## 5. Open question: is the live analyzer worth running at all before the first capture?

The capture-first design already established that a live frame can never produce the answer. Live
frames now serve only two purposes: the "Table in view" readiness line and the "Move closer"
framing hint. Both are advisory.

That is a continuous ML Kit recognition on every preview frame, for two strings. It may well be
worth it — the readiness line is the main thing telling a user their framing is good — but nobody
has measured what it costs in battery or in thermal throttling on a mid-range phone, or whether
dropping to (say) every third frame is indistinguishable to the user.

**This is a measurement task before it is a change task.** Do not reduce the frame rate on
intuition; the reason live analysis exists is that users could not tell whether the app could see
the table.

## 6. `MealScreenTest` is flaky, and it hides real failures

**Measured 2026-08-28 on a `git worktree` at clean `cc01789`:** three runs gave 19/19, **18/19**,
19/19, and the failing case was a different one each time
(`addingTwoPortionsTotalsThemInTheBar`, `addAndScanNextRecordsTheItemAndLeavesForTheScanner`,
`addingToTheMealKeepsThePortionFieldAndResultVisible`). All fail the same way: the meal bar "is not
displayed". `ProductScreenTest` behaves the same, giving 1 then 2 failures across identical runs.

This is almost certainly the same class of harness artefact this repo has hit twice already — a
control that is covered by the IME or scrolled out of view, where a node is `isPlaced` with empty
bounds. Every failing assertion runs **after** `performTextInput`, which opens the keyboard.

**Why it matters more than a nuisance:** a suite that fails ~1 test at random cannot be used as a
release gate, and `release-gate.yml` requires the instrumented suite to pass with 0 skipped. It
also cost real time this pass — the failures had to be disproved against a worktree control before
the changes could be trusted.

**Do not fix by adding retries or `@FlakyTest`.** Diagnose with
`fetchSemanticsNode().boundsInRoot` + `.layoutInfo.isPlaced` as documented in CLAUDE.md, and if it
is the known cause, `performScrollTo()` before the assertion — the fix already applied to the
`quickAdjust*` cases.

## 7. The release sequence for 1.0.1

Not a backlog item so much as a standing reminder. `versionCode` is now **2** / `versionName`
**1.0.1**, and that version is **open** — more work is going into it before it is pushed, so add
changes to its section in `CHANGELOG.md` rather than bumping again.

When it is ready, follow `docs/play-release-readiness.md` §2c and §2d: build from a committed tree,
verify the signer DN is the real upload key, re-check the R8 privacy barriers, re-run the tests and
rewrite the Play *What's new* to cover everything that ended up in the version, then record the
hash and copy the entry into `docs/version-history.md` **after** Play accepts it.
