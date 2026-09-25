package app.justthecarbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long typed input must stay unchanged before a live region speaks it. */
const val SETTLE_MS = 600L

/**
 * Text for a polite live region that should speak what the user settled on, not every keystroke
 * (2026-09-25 review).
 *
 * While [settling] (the keyboard is open) the returned text follows [text] only once it has been
 * unchanged for [settleMs]; each change restarts the wait. When [settling] is false it follows at
 * once, so closing the keyboard speaks the figure on screen without a delay.
 */
@Composable
fun rememberSettledText(text: String?, settling: Boolean, settleMs: Long = SETTLE_MS): String? {
    var settled by remember { mutableStateOf(text) }
    LaunchedEffect(text, settling) {
        if (settling) delay(settleMs)
        settled = text
    }
    return if (settling) settled else text
}
