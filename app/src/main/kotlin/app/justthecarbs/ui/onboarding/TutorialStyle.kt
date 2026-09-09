package app.justthecarbs.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/** The feature's existing colour echoes through focus, rail and narration. */
@Composable
internal fun TutorialAccent.color(): Color = when (this) {
    TutorialAccent.PRIMARY, TutorialAccent.BARCODE, TutorialAccent.SEARCH -> MaterialTheme.colorScheme.primary
    TutorialAccent.LABEL -> MaterialTheme.extendedColors.accents.green
    TutorialAccent.PORTION -> MaterialTheme.colorScheme.tertiary
    TutorialAccent.RESULT -> MaterialTheme.extendedColors.result
}

/** Radius includes the aperture's padding; bounds always come from measured anchors. */
internal fun TutorialFocusStyle.radius(padding: Dp): Dp = when (this) {
    TutorialFocusStyle.GROUP -> Space.sheetTopRadius
    TutorialFocusStyle.CARD -> Space.cardRadius + padding
    TutorialFocusStyle.CONTROL -> Space.buttonRadius + padding
    TutorialFocusStyle.RESULT -> Space.sheetTopRadius + padding
}
