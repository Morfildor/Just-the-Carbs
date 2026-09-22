package app.justthecarbs.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Puts the keyboard away when the user reaches for a scrolling list, without spending their touch.
 *
 * Live search means results arrive while the field still has focus and the IME is still up — the
 * user never pressed Enter, so nothing has dismissed it. The region stops at the keyboard, so the
 * list is not covered, but the viewport is roughly halved: the moment someone stops typing and
 * starts *reading*, the list they are reading is the smallest thing on screen.
 *
 * **The pass and the consumption rule are the whole contract.** `Initial` rather than `Main`, and
 * `requireUnconsumed = false`, so this handler only ever OBSERVES the gesture. It runs before the
 * row's own `clickable` sees the event and consumes nothing, so a single tap both dismisses the
 * keyboard and selects the product rather than being spent on the dismissal — and a drag scrolls
 * the list normally instead of being swallowed.
 *
 * That is the reason for the choice, NOT a reproduced defect: a control run on `Main` still passes
 * `tappingAResultStillSelectsItOnTheFirstTap`, because Compose's synthetic `performClick` does not
 * model the consumption ordering a real finger produces. The test pins the property on the shipped
 * code; it is not a discriminator between the two passes. Hardware is the discriminating check.
 *
 * Extracted from `SearchScreen` in the 2026-09-22 refinement pass so Home's inline results get the
 * identical behaviour rather than a second copy that can drift from it.
 */
fun Modifier.dismissKeyboardOnTouch(onTouched: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouched()
    }
}
