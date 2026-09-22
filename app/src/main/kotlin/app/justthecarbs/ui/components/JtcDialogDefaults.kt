package app.justthecarbs.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.theme.Space

/** Shared visual contract for app modals; behavior and content remain owned by each dialog. */
object JtcDialogDefaults {
    val shape: Shape = RoundedCornerShape(Space.cardRadius)
    val tonalElevation: Dp = 0.dp

    /**
     * The modal's own surface, and it differs by theme on purpose.
     *
     * `surfaceContainerHigh` is right in Dark: a raised panel needs to be *lighter* than the page
     * behind it to read as being in front of it. In Light it is `#E8E2D7`, a warm grey noticeably
     * DARKER than the cream page -- so a dialog looked like a muddy patch pressed into the page
     * rather than a card lifted off it, which is the opposite of what elevation is for.
     *
     * Light therefore takes `surfaceContainerLowest`, the same warm white the result dock and every
     * card already use. One rule, stated once: a raised surface moves *away* from the page's own
     * luminance, which happens to mean up in Light and up in Dark from opposite directions.
     */
    val containerColor: Color
        @Composable get() = if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }

    val iconContentColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val titleContentColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val textContentColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

/** A quiet edge keeps the raised dark container legible without turning every modal into a card. */
@Composable
fun Modifier.jtcDialogOutline(): Modifier =
    border(1.dp, MaterialTheme.colorScheme.outlineVariant, JtcDialogDefaults.shape)
