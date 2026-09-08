# Tutorial tap-anywhere redesign

## Problem

The coach-mark tutorial (`OnboardingScreen` + `TutorialOverlay`) works but feels like a
precision test on a real phone: a double-ring pulsing spotlight, a curved arrow, a callout
that floats above or below the target depending on geometry, and small Back/Next/Skip
buttons the user must aim for. Confirmed against
`docs/Screen_Recording_20260908_174711_Just the Carbs.mp4` (frames extracted with ffmpeg
and reviewed directly, since no video-watching tool was available on this machine).

## What the recording and code already establish

The whole tutorial — both `TutorialMode.FIRST_RUN` and `TutorialMode.REPLAY` — renders over
`TutorialBackdropContent` (`TutorialPreview.kt`), a deterministic, non-interactive drawing
built from constants. There is no real Home/Product/Meal screen, ViewModel, camera, network,
or Room access behind the scrim, and nothing in it carries semantics or click handlers. This
means several concerns the brief raises as open risks are **already closed structurally**:

- No click-through to production controls is possible today, because there are no production
  controls under the overlay to click through to.
- Replay is already deterministic — it renders the identical screen and step sequence as
  first-run, with no dependency on Home state, Recents, search text, or a selected product.
- There is no coupling between tutorial progression and any live app subsystem (scanner,
  network, OCR) to remove.

So this redesign is scoped to `OnboardingScreen`'s interaction model and the overlay's visual
weight — not a rewiring of navigation or backdrop architecture.

## Change

**Tap-anywhere-advances.** The full-screen `Column` gains a transparent tap-catching layer
(`Modifier.pointerInput { detectTapGestures { onNext() } }`) sized to the whole overlay,
placed beneath Skip and above the (non-interactive) backdrop in composition order. Compose's
normal top-most-consumes-first hit testing means Skip's own `clickable` intercepts its own
taps without any manual dispatch logic — there is nothing else clickable to conflict with,
since the backdrop is decorative. On the final step, the same tap calls `onFinish` instead of
`onNext`.

**Back is removed.** Forward-only tour: tap anywhere = next, Skip = exit early, system Back =
exit (unchanged). `OnboardingViewModel.previous()`, the `TUTORIAL_BACK_TAG` button, and
`canGoBack` are deleted. This removes the only other reason a tap's location used to matter.

**Progress becomes passive.** The step-count line and dot row stay (already unobtrusive,
already `hideFromAccessibility` on the dots with the eyebrow line carrying "Step N of M" in
words) but stop being wrapped in anything clickable. Final-step copy changes to invite a tap
("Tap anywhere to finish") rather than showing a Finish button.

**Spotlight simplified.** Drop the halo/pulse animation and the curved arrow-with-arrowhead
entirely. Keep one soft, static rounded-rect outline around the (still generously padded)
spotlight — enough to say "I'm talking about this," not enough to imply "press exactly here."
`TutorialSpotlightDecoration`'s halo loop and `drawConnector`/`drawArrowHead` are deleted;
`arrowStart` and `pulse` parameters go with them.

**Callout placement stays adaptive, biased to a stable default.** Keep
`CalloutPlacement.kt`'s above/below logic (it already prefers the roomier side and falls back
to centred when neither fits) rather than hard-pinning position, since a hard-pinned bottom
card can still be obscured by a target that sits low on some phone/font-scale combinations.
No change to `calloutSideFor`'s contract; `CalloutPlacementTest` stays as-is.

**Copy audit.** Each of the 6 steps gets one short title + one sentence; tighten any step
currently running two sentences where one suffices. No new steps, no removed steps — the
existing 6-step sequence (orientation, barcode, search, label, meal, total) already matches
current product surface.

## Non-goals / explicitly unchanged

- Navigation graph, `Routes.onboarding()`, `TutorialMode`, `OnboardingViewModel.finish()`
  persistence semantics, `TutorialReminder` window/launch-count rule, Home's reminder card —
  all preserved per the brief's scope barriers and confirmed correct by inspection.
- `TutorialAnchors` (measured geometry lookup) is kept as-is; still needed to find the
  spotlight rectangle.
- `TutorialPreview.kt` backdrops are kept; only referenced, not restructured.
- No new dependency, no gesture framework, no debounce/time-based tap guard (a fast
  double-tap simply advances twice, matching the old double-click-Next behavior).

## Testing

`TutorialScreenTest` is rewritten around the tap-anywhere contract: tap the highlighted
region, tap empty scrim, tap the callout card body, tap near screen edges — each advances
exactly one step; Skip advances zero steps and exits; final-step tap finishes once. Back-button
tests are deleted. `TutorialNavigationTest` (routing/persistence) needs only its two
`TUTORIAL_PRIMARY_TAG`-repeated-click walks changed to a generic tap on the overlay — its
routing/persistence assertions are unaffected. `TutorialStepTest` (pure step-list ordering) is
unaffected. `CalloutPlacementTest` is unaffected. `TutorialPreview.kt` previews are updated to
drop arrow/pulse parameters used in any manual preview compositions.
