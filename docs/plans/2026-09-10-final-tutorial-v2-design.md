# Final Tutorial V2 design

## Goal

Polish the existing contextual tutorial so each real feature reads immediately while the app remains spatially recognizable. Preserve the measured spotlight, central teaching stage, tap-anywhere behavior, deterministic previews, and accessibility contract.

## Visual system

The overlay uses three independent focus layers:

1. A theme-tuned translucent scrim quiets surrounding content without hiding its color or structure.
2. A one-dp tonal edge separates the measured target from adjacent content, especially for green-on-green and neutral Search targets.
3. A broad elliptical semantic bloom supplies atmosphere without tracing another rounded card.

`TutorialFocusEmphasis` controls only the edge and bloom strength. Search and Nutrition Label use `Strong`; the remaining steps use `Standard`. START keeps its broad orientation emphasis without a cleared aperture.

## Pointer

`TutorialPointer` is step metadata. START uses `None`; target-specific steps use `Feature`. A pure geometry function chooses the shortest safe route from a narration edge to a point outside the measured target, builds a shallow cubic Bezier, and returns no pointer when the path would intersect narration, enter the target, cross the Skip exclusion, or become too short or long. Large-font and narrow layouts therefore degrade by omitting the pointer.

The pointer is a thin, rounded, semantic-color Canvas stroke with an open arrowhead. It reveals once from the teaching block toward the feature and then remains still. It has no semantics.

## Motion and composition

The existing `Animatable<Rect>` remains the sole rendered spotlight geometry. A single finite acquisition phase drives the focus edge, pointer reveal, bloom settlement, and active progress segment over the existing 220 ms standard token. Accent, radius, and spotlight retarget on the same timing; copy crossfades in the stable measured slot. Backdrop crossfades occur only at Home to Product and Product to Meal.

The teaching group stays centered near 60% of usable portrait height. It uses Space Grotesk SemiBold at 30/34 sp for headlines and Medium at 16.5/22 sp for body copy. Progress stays compact and secondary; no card, accent bar, or local outlined background is introduced.

## Accessibility and testing

The pane title, polite merged announcement, `Step N of 6`, Next/Finish semantic action, independent 48 dp Skip target, and preview semantics exclusion remain unchanged. Scrim, bloom, rim, pointer, and rail remain decorative.

Unit tests cover step metadata and pure pointer safety geometry. Compose tests retain interaction, measured-target, stable-stage, 320 dp, 2x-font, navigation, and persistence coverage. Visual review covers all six steps in light and dark themes, light at 2x font, and a representative 320 dp layout, plus normal and rapid walkthroughs where the available emulator permits them.
