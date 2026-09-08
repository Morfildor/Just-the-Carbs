package app.justthecarbs.ui.onboarding

/**
 * Which side of the spotlight the callout card sits on.
 *
 * [CENTERED] is the no-anchor case: the card sits in the middle of the screen and no arrow is drawn.
 * It is a deliberate layout for an orientation step, and also the safe fallback whenever a target is
 * unavailable — a callout with no arrow is a complete, readable thing, whereas an arrow to a
 * rectangle that does not exist is not.
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
 * The rule is "put the card in the roomier half, provided it actually fits there". Preferring the
 * larger gap keeps the card off the target it is describing; requiring [requiredHeight] to fit stops
 * it being placed into a gap too small to render in, where it would be clipped by the screen edge.
 * When neither side fits, [CENTERED] is returned rather than picking the least-bad side: an
 * overlapping card hides the very control the step is pointing at.
 */
fun calloutSideFor(
    spotlightTop: Float,
    spotlightBottom: Float,
    screenHeight: Float,
    requiredHeight: Float,
): CalloutSide {
    val above = spotlightTop
    val below = screenHeight - spotlightBottom

    val fitsAbove = above >= requiredHeight
    val fitsBelow = below >= requiredHeight

    return when {
        fitsBelow && below >= above -> CalloutSide.BELOW
        fitsAbove -> CalloutSide.ABOVE
        fitsBelow -> CalloutSide.BELOW
        else -> CalloutSide.CENTERED
    }
}

/**
 * Clamp [desired] so a [width]-wide box stays within `0..screenWidth`, inset by [margin].
 *
 * Used to keep the callout and its arrow on screen when the target sits near an edge. When the
 * available width is smaller than the box itself the box is pinned to the left margin rather than
 * being given a negative position: overflowing one edge is recoverable, and starting off-screen is
 * not.
 */
fun clampHorizontally(desired: Float, width: Float, screenWidth: Float, margin: Float): Float {
    val max = screenWidth - margin - width
    if (max <= margin) return margin
    return desired.coerceIn(margin, max)
}
