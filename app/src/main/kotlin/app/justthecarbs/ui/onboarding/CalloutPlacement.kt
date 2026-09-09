package app.justthecarbs.ui.onboarding

/**
 * Which side of the spotlight the callout card sits on.
 *
 * [CENTERED] is the no-anchor case, decided by the caller before [calloutSideFor] is ever invoked:
 * when there is no spotlight at all (the orientation step, or a target briefly unavailable), the
 * card sits in the middle of the screen.
 *
 * [CLAMPED] is [calloutSideFor]'s own emergency case, for when the card fits neither the bottom
 * zone nor the top zone at all -- the card itself, at its real measured height, is taller than the
 * larger of the two. This is not the ordinary bottom/top decision going the "wrong" way; it means
 * both zones were measured and rejected. It exists for large accessibility font sizes on small
 * screens, where a six-word body can measure taller than either clear zone once line-wrapped and
 * scaled. The caller places a [CLAMPED] card flush against whichever edge leaves it fully on
 * screen, coordinate-clamped rather than picked by a second comparison -- see [OnboardingScreen].
 */
enum class CalloutSide { ABOVE, BELOW, CENTERED, CLAMPED }

/**
 * Where to put the callout for a spotlight occupying [spotlightTop]..[spotlightBottom] on a screen
 * of height [screenHeight]. All values are pixels in the same coordinate space.
 *
 * Pure arithmetic in its own file so the rule is JVM-testable: the placement decision is exactly the
 * kind of thing that looks obviously right while being wrong on one screen size, and an instrumented
 * test can only check the sizes the emulator happens to have.
 *
 * **The rule is bottom-default, not a comparison.** A previous version of this function compared
 * the space above and below the spotlight and picked whichever was roomier -- that is what made the
 * callout's reading position jump between tutorial steps depending on where each step's target
 * happened to sit. There is now exactly one default (bottom) and exactly one reason to leave it: the
 * bottom zone, after subtracting [safeAreaBottomInset], is smaller than [cardHeight]. In that case
 * -- and only that case -- the card moves to the top zone instead. This keeps the decision coarse
 * and deterministic on purpose: the same spotlight geometry always produces the same side, on every
 * call, with no second candidate to weigh it against. **This normal bottom-default/top-fallback
 * behaviour is unchanged** — [CLAMPED] is a third, narrower check layered after it, not a
 * replacement for it: top is tried and rejected before [CLAMPED] can ever be returned.
 *
 * [cardHeight] must be the callout card's real measured height (see [OnboardingScreen]'s
 * `SubcomposeLayout` usage), not an estimate -- an estimate is exactly the kind of guess that is
 * wrong at the extremes (a long translated string, a large accessibility font size), which is
 * precisely where getting this decision wrong means the card overlaps the very control it describes.
 */
fun calloutSideFor(
    spotlightTop: Float,
    spotlightBottom: Float,
    screenHeight: Float,
    cardHeight: Float,
    safeAreaBottomInset: Float = 0f,
): CalloutSide {
    val availableBelow = (screenHeight - spotlightBottom) - safeAreaBottomInset
    if (availableBelow >= cardHeight) return CalloutSide.BELOW

    val availableAbove = spotlightTop
    if (availableAbove >= cardHeight) return CalloutSide.ABOVE

    // Neither zone has room. Rather than choosing between two options that are both known to
    // overlap the spotlight -- which is exactly the "roomier side" comparison this function exists
    // to not make -- the card is placed by the caller wherever it fits fully on screen, clamped
    // rather than chosen.
    return CalloutSide.CLAMPED
}

