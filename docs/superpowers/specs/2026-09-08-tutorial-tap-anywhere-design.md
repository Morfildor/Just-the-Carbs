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

**Tap-anywhere-advances, as a dedicated sibling hit surface.** The tap target is its own
full-screen `Box` in the overlay's `Box` stack — not a `pointerInput` modifier on a parent
`Column` that also contains Skip. Composition/z-order, top to bottom: decorative backdrop and
scrim (bottom) → the tap-catching `Box` (`Modifier.fillMaxSize().pointerInput { detectTapGestures
{ onNext() } }`) → the callout card and Skip (top). Because both Skip and the callout card
render in a layer above the tap surface, Compose's pointer-input dispatch lets their own
`clickable`/pointer-input regions consume taps before those taps ever reach the full-screen box
beneath — Skip unambiguously wins hit-testing on its own bounds and never also advances the
tutorial. This is a structural guarantee from layering, not a manual "if the tap hit Skip's
bounds, don't also call onNext" check. The callout card is a passive text container with no
`clickable` of its own, so a tap landing on its background (not on Skip) passes through to the
tap surface beneath exactly like a tap on open scrim does — the card does not need to
re-implement "tap advances," it simply has nothing that intercepts the tap ahead of it. On the
final step, the same tap surface calls `onFinish` instead of `onNext`.

**Back is removed.** Forward-only tour: tap anywhere = next, Skip = exit early, system Back =
exit (unchanged). `OnboardingViewModel.previous()`, the `TUTORIAL_BACK_TAG` button, and
`canGoBack` are deleted. This removes the only other reason a tap's location used to matter.

**Progress becomes passive, and single-treatment.** The dot row (`TUTORIAL_PROGRESS_TAG`, the
`repeat(stepCount) { ... animateDpAsState ... }` width animation, and its
`hideFromAccessibility()`) is deleted outright rather than kept alongside the text. Two
progress indicators (dots visually, "Step N of M" in words) is more chrome than a
tap-anywhere screen needs, and the dot row was already redundant with the eyebrow line — it
carried no information the text didn't already state, only a second thing to render and
animate. Only the accessible "Step N of M" text line remains, unwrapped in anything clickable.
Final-step copy changes to invite a tap ("Tap anywhere to finish") rather than showing a
Finish button.

**Spotlight simplified.** Drop the halo/pulse animation and the curved arrow-with-arrowhead
entirely. Keep one soft, static rounded-rect outline around the (still generously padded)
spotlight — enough to say "I'm talking about this," not enough to imply "press exactly here."
`TutorialSpotlightDecoration`'s halo loop and `drawConnector`/`drawArrowHead` are deleted;
`arrowStart` and `pulse` parameters go with them.

**Callout placement: bottom-safe-area is the deterministic default.** The current
"whichever side has more room wins" comparison is what makes the reading location jump
between steps — that is being removed, not preserved. `CalloutPlacement.kt` is simplified,
not kept: the callout renders in the bottom safe area on every step unless doing so would
materially overlap or obscure the highlighted target, or would be unusable at the current
viewport/font scale (the card's estimated height, at the current font scale, would not fit
in the bottom zone without covering the spotlight). Only then does it fall back to the top
safe area. This fallback is deliberately coarse — a simple "does the spotlight's bottom edge
sit low enough, and does the card's estimated height at this font scale fit below it without
touching the spotlight" check, not a room-comparison between two candidate zones. There is no
third position: a target so large neither zone is clear still resolves to one deterministic
side (bottom bias unless the check above trips), never a centred/covering placement. Because
the default is fixed, most steps will render the callout in the same place; only a step whose
target sits low on screen predictably flips to the top, and the same step flips the same way
on every run — that predictability is the point, not an incidental property.
`calloutSideFor`'s contract changes: it no longer compares available space on both sides, and
`CalloutPlacementTest` is rewritten to match the new deterministic-default-with-coarse-fallback
behavior rather than the old "roomier side wins" cases.

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
exactly one step; a tap on Skip's own bounds advances zero steps and exits, never both;
final-step tap finishes once. Back-button and dot-row-progress tests are deleted; the
"Step N of M" text assertion stays. `TutorialNavigationTest` (routing/persistence) needs only
its two `TUTORIAL_PRIMARY_TAG`-repeated-click walks changed to a generic tap on the full-screen
hit surface — its routing/persistence assertions are unaffected. `TutorialStepTest` (pure
step-list ordering) is unaffected. `CalloutPlacementTest` is rewritten for the new
deterministic-bottom-default-with-coarse-top-fallback contract: same step index renders the
callout in the same position on repeat calls (determinism), an ordinary target uses the bottom
zone, a target low enough to make the bottom zone unusable at default font scale falls back to
top, and the same low target continues to fall back to top at a larger font scale (where the
card's estimated height grows). `TutorialPreview.kt` previews are updated to drop arrow/pulse
parameters used in any manual preview compositions, and to drop any dot-row preview state.
