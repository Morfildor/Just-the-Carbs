package app.justthecarbs.ui.onboarding

/**
 * Which side of the spotlight the callout card sits on.
 *
 * [CENTERED] is the no-anchor case, decided by the caller before [calloutSideFor] is ever invoked:
 * when there is no spotlight at all (the orientation step, or a target briefly unavailable), the
 * card sits in the middle of the screen. [calloutSideFor] itself only ever returns [ABOVE] or
 * [BELOW] — it is not consulted when there is no spotlight to place the card relative to.
 */
enum class CalloutSide { ABOVE, BELOW, CENTERED }

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
 * call, with no second candidate to weigh it against.
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
    return if (availableBelow >= cardHeight) CalloutSide.BELOW else CalloutSide.ABOVE
}

