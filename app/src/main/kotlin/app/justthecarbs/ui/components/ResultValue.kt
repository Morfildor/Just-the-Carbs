package app.justthecarbs.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.justthecarbs.ui.theme.NumberType
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

const val RESULT_VALUE_NUMERAL_TAG_SUFFIX = "_numeral"

/**
 * The single rendering of a calculated carbohydrate result: a dominant numeral with a smaller,
 * baseline-paired unit — `31.2` `g` rather than one string `"31.2 g"` in one typographic weight.
 *
 * A pure rendering component: callers already hold `ResultFormatter` output and pass the numeral
 * and unit as separate strings. No calculation, formatting or rounding happens here.
 *
 * Previously duplicated independently in `ResultPanel` (Product) and `MealTotalPanel` (Meal),
 * which is how the two drifted (only Product cross-faded its digits on change). This is now the
 * only place either lives.
 */
@Composable
fun ResultValue(
    dominant: String,
    unit: String,
    accessibleLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.extendedColors.result,
    testTag: String? = null,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            // One coherent node for TalkBack: without this the numeral and the unit are two
            // separately-focusable fragments a screen-reader user has to reassemble themselves.
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text = dominant,
            style = NumberType.result,
            color = color,
            maxLines = 1,
            // Shrinks rather than clips — see NumberType.resultAutoSize's own KDoc.
            autoSize = NumberType.resultAutoSize,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = unit,
            style = NumberType.resultUnit,
            color = color,
            maxLines = 1,
        )
    }
}
