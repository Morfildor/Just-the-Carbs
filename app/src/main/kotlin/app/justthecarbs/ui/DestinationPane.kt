package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics

/**
 * Names a navigation destination for accessibility services, so TalkBack announces the screen it
 * has arrived on instead of moving on in silence.
 *
 * Applied once per destination in the nav host rather than inside each screen, so a new destination
 * cannot forget it and no screen's own layout changes. `propagateMinConstraints` hands the screen
 * exactly the constraints it had before this wrapper existed, so nothing visible moves.
 *
 * A null or empty [title] sets no pane title — the product screen while its product is still
 * loading has no name to announce, and announcing a blank would be worse than waiting for one.
 */
@Composable
internal fun DestinationPane(title: String?, content: @Composable () -> Unit) {
    Box(
        modifier = if (title.isNullOrEmpty()) Modifier else Modifier.semantics { paneTitle = title },
        propagateMinConstraints = true,
    ) {
        content()
    }
}
