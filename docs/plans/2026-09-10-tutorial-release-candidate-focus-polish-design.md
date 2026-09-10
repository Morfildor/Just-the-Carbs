# Tutorial release-candidate focus polish

## Scope

Keep the current tutorial architecture, copy, teaching position, deterministic previews,
tap-anywhere behavior, Skip, persistence, and accessibility. Change only focus hierarchy,
pointer legibility/geometry, and transition cohesion.

## Design

The existing three-layer system remains. Context receives a modestly stronger theme-aware veil and
a broad local falloff immediately outside the clear measured aperture. The target gains a brighter
semantic definition; colored targets use a lighter relative of their accent while Search uses a
clean primary-blue edge. Strong emphasis raises definition, bloom, and the finite arrival peak
without changing target size.

BARCODE, SEARCH, LABEL, and MEAL keep feature pointers. Their main stroke becomes a high-opacity
2.2dp semantic line over a restrained 3.6dp theme-ground casing, with a small open 8.5dp head.
Routes begin and end nearer the facing edges of teaching and target, prefer negative space, and use
a 12dp viewport safety inset. Normal phone geometry must resolve all four pointers; genuinely
constrained layouts may still return no route. START and TOTAL remain pointer-free.

One internal presented-step state coordinates content. On a requested step change, old progress and
copy fade out together, the presented step switches while invisible, then new progress/copy fade in
as target, accent, bloom, and pointer acquire. Focus arrival peaks once and settles within the
existing 220ms motion window. A newer request cancels the old transition and converges on the latest
step; interaction completion continues to follow the requested index so rapid Finish taps are not
lost.

## Evidence

Add pure tests for focus-arrival bounds and representative BARCODE, SEARCH, LABEL, and MEAL pointer
routes. Retain constrained-geometry omission tests. Run the existing tutorial behavior/navigation/
persistence/visual suites, capture all settled variants, and compare slow and rapid recordings.
