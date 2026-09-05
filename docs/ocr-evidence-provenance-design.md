# Aim epoch, work generation, and physical-observation IDs

Written 2026-09-05, as part of the OCR evidence-reliability pass (Tasks 1-8 of
`docs/superpowers/plans/2026-09-05-ocr-evidence-reliability.md`).

## The problem in one sentence

`LabelScannerScreen`'s single `captureSession: AtomicLong` answered two different questions with
one number, and the number bumped at the time that was right for one question and wrong for the
other.

## Two questions, two counters

**Work generation** — "is this in-flight asynchronous callback still current, or has the user moved
on to a different attempt?" This must bump on every new shutter press, so a still-recognition
result from an abandoned capture cannot land on top of a newer one.

**Aim epoch** — "which pre-shutter live-camera stream does this frame belong to?" This must NOT
bump on a shutter press. A live frame recorded while the user is framing the shot is, by
definition, recorded *before* the tap that produces the still it should be able to corroborate.
Bumping this counter at the moment of the tap retroactively excludes every frame that led up to
it.

`CaptureEvidenceCoordinator` (`app/src/main/kotlin/app/justthecarbs/ocr/CaptureEvidenceCoordinator.kt`)
is two `AtomicLong`s, `beginNewAim()` and `beginNewWork()`, called at the right two different
moments: `beginNewAim()` on retake/dispose/leaving-and-re-entering the screen; `beginNewWork()` at
the start of every shutter press.

## Shutter snapshot semantics

`freezeAtShutter(buffer, nowElapsed)` is called at the very start of the shutter handler, before
`beginNewWork()`, before the analyzer is paused, before autofocus, before the image is captured.
It reads `LiveEvidenceBuffer`'s current consensus for the current aim epoch and returns an
immutable `LiveEvidenceSnapshot`. That snapshot — not a later live query of the buffer — is what
flows into the still-recognition pipeline.

This matters because the still pipeline can take anywhere from ~480ms to ~2.5s on real hardware
(recorded in `.audits/architectural-analysis-2026-09-04.md` and CLAUDE.md's various device-session
sections), comfortably longer than `LiveEvidenceBuffer`'s 1500ms consensus window. A query issued
*after* the still pipeline finishes can find the window has already closed on genuinely good
evidence that was present at the moment that actually mattered — the shutter press.

## Physical-observation IDs

`PhysicalObservationId` (`RecognitionEvidence.kt`) already existed as a type before this pass, but
every production construction site left it at the default `UNKNOWN` sentinel, which made
`AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT` structurally unreachable — two pieces of
evidence both defaulting to `UNKNOWN` can never be "distinct".

`PhysicalObservationId.forStill(captureId)` and `.forLiveSnapshot(aimEpoch, snapshotId)` are the two
factories production code now uses. Every recognition derived from one quality-still JPEG — the
whole-frame Pass A, the filtered re-parse, a Strategy B re-recognition of a crop of the same
bitmap — shares one `forStill(...)` id, because they all read the same ink. Only a frame from the
frozen pre-shutter live snapshot, which genuinely came from different sensor frames, gets a
different id and can therefore corroborate the still.

## What this does not change

No confidence threshold moved. No parser rule was relaxed. `ReadingEligibility`'s existing
`Established`/`Ambiguous`/`Unsupported` scale logic, `ScaleAmbiguity`'s existing checks, and
`ScanPresentationDecision`'s existing routing are all unchanged in their own rules — this pass fixed
the *evidence* those rules see (which frames survive to be considered, and whether "corroboration"
can mean the same photograph agreeing with itself), and fixed one caller
(`AutomaticScanAdvance.eligibility`) that was answering `ReadingEligibility.evaluate`'s
`corroborationSettlesScale` question incorrectly by always saying `true`.
