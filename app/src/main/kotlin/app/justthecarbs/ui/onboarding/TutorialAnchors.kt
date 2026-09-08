package app.justthecarbs.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where each preview control actually landed, in root coordinates.
 *
 * The spotlight and arrow are drawn from measured geometry rather than from hardcoded coordinates or
 * a screenshot, which is what lets them stay correct across phone sizes, rotation, system-bar insets
 * and font scale: whatever the layout does, the bounds reported here describe it.
 *
 * Backed by a snapshot state map, so a control reporting its bounds recomposes the overlay that
 * reads them.
 */
class TutorialAnchors {
    private val bounds = mutableStateMapOf<TutorialAnchor, Rect>()

    /** Record where an anchor was laid out. Called from [Modifier.tutorialAnchor]. */
    fun report(anchor: TutorialAnchor, rect: Rect) {
        bounds[anchor] = rect
    }

    /**
     * The measured bounds for [anchor], or null when it has not been positioned.
     *
     * Null is a real and expected answer, not a failure: on the frame before layout completes, and
     * on any step whose backdrop does not contain that control, there is genuinely no rectangle. The
     * caller renders a centred callout with no arrow rather than pointing at the origin.
     *
     * An empty rectangle is treated as absent too. A composable that is measured but not placed
     * reports a zero-size rect at the origin, and drawing a spotlight there would put a circle in
     * the screen's top-left corner and an arrow to nothing — the exact failure this returns null to
     * avoid.
     */
    fun boundsOf(anchor: TutorialAnchor): Rect? =
        bounds[anchor]?.takeIf { it.width > 0f && it.height > 0f }

    /** Forget every measurement. Used when the backdrop changes, so a stale rectangle from the
     *  previous preview cannot be pointed at while the new one is still being laid out. */
    fun clear() {
        bounds.clear()
    }
}

@Composable
fun rememberTutorialAnchors(): TutorialAnchors = remember { TutorialAnchors() }

/**
 * Report this composable's position to [anchors] as [anchor].
 *
 * `boundsInRoot()` rather than `positionInWindow()` because the overlay draws in the same root, so
 * both agree about the origin without either having to know about insets. Purely observational — it
 * adds no padding, size, semantics or input handling to the control it is applied to.
 */
fun Modifier.tutorialAnchor(anchors: TutorialAnchors, anchor: TutorialAnchor): Modifier =
    this.onGloballyPositioned { anchors.report(anchor, it.boundsInRoot()) }
