package app.justthecarbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.justthecarbs.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Generalizes the "copied" check-mark pattern (originally inline in `ProductScreen`'s result copy
 * button) into one shared holder, so every success confirmation in the app — Add to meal, Copy,
 * Favorite, Save — holds its state the same way and for the same reasoning: long enough to survive
 * glancing away and back, short enough it cannot be mistaken for the resting state.
 *
 * Keyed on [trigger] rather than a plain boolean flag: a *new* trigger value (e.g. a newly copied
 * string, or a fresh add-to-meal attempt) restarts the hold from that value, so rapid repeats
 * converge on the latest action instead of the first one's timer silently finishing mid-flight.
 */
@Composable
fun rememberSuccessPulse(trigger: Any?, holdMs: Long = Motion.COPIED_STATE_MS): Boolean {
    var shownFor by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            shownFor = trigger
            delay(holdMs)
            if (shownFor == trigger) {
                shownFor = null
            }
        } else {
            shownFor = null
        }
    }
    return trigger != null && shownFor == trigger
}
