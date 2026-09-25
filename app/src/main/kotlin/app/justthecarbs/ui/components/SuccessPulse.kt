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

/**
 * A success hold measured from when the success happened, not from when this composable first saw
 * it (2026-09-25 review).
 *
 * [rememberSuccessPulse] starts its hold when the trigger first reaches composition, so a control
 * that leaves composition and returns (the calculator's meal buttons step aside while the keyboard
 * is open) started a fresh hold for a success that was long over. Here [at] is a wall-clock time
 * from [now]; the hold shows only for what is left of [holdMs] after it, and a time in the future
 * (the clock moved) shows nothing rather than holding indefinitely.
 */
@Composable
fun rememberSuccessPulseSince(
    at: Long?,
    holdMs: Long = Motion.COPIED_STATE_MS,
    now: () -> Long = System::currentTimeMillis,
): Boolean {
    fun remaining(): Long = if (at == null) 0L else (holdMs - (now() - at)).takeIf { it in 1..holdMs } ?: 0L
    var showing by remember(at) { mutableStateOf(remaining() > 0L) }
    LaunchedEffect(at) {
        val left = remaining()
        showing = left > 0L
        if (left > 0L) {
            delay(left)
            showing = false
        }
    }
    return showing
}
