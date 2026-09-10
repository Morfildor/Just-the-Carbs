package app.justthecarbs.ui.components

import androidx.compose.foundation.border
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

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

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
